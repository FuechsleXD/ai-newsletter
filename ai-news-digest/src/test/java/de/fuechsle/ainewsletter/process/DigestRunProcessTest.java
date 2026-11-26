package de.fuechsle.ainewsletter.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.service.RunAlreadyActiveException;
import de.fuechsle.ainewsletter.service.TopicNotFoundException;
import de.fuechsle.ainewsletter.support.Mailpit;
import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.history.HistoricActivityInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The process {@code digestRun} with the embedded engine, a real database, the fake provider and
 * Gotenberg and Mailpit as local containers (mandatory cases from docs/10-tests.md that apply with
 * one model; parallelism follows in step 13).
 */
class DigestRunProcessTest extends ProcessTestSupport {

  @Autowired private DigestProcessStarter starter;
  @Autowired private RuntimeService runtimeService;

  @Test
  void happyPathCompletesAndMovesTheLastSuccessfulRun() throws IOException {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Happy " + suffix, model, model);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("COMPLETED");
    Map<String, Object> run =
        jdbc.queryForMap(
            "SELECT id, period_from, period_to, prompt_version, finished_at FROM digest_run"
                + " WHERE process_instance_id = ?",
            instance);
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
    assertThat(run.get("period_to").toString()).isEqualTo(today.toString());
    assertThat(run.get("period_from").toString()).isEqualTo(today.minusDays(7).toString());
    assertThat(run.get("prompt_version")).isEqualTo("v6");
    assertThat(run.get("finished_at")).isNotNull();

    Map<String, Object> result =
        jdbc.queryForMap(
            "SELECT status, input_tokens, search_calls, cost_usd FROM provider_result"
                + " WHERE digest_run_id = ?",
            run.get("id"));
    assertThat(result.get("status")).isEqualTo("SUCCEEDED");
    assertThat(result.get("search_calls")).isEqualTo(1);
    // 1,000 * 3 / 1M + 500 * 15 / 1M + 1 * 0.01
    assertThat((BigDecimal) result.get("cost_usd")).isEqualByComparingTo("0.0205");
    assertThat(newsItems((Long) run.get("id"))).isEqualTo(2);

    Map<String, Object> aggregation =
        jdbc.queryForMap(
            "SELECT model_id, cost_usd FROM aggregation_result WHERE digest_run_id = ?",
            run.get("id"));
    assertThat(aggregation.get("model_id")).isEqualTo(model);
    // 1,000 * 3 / 1M + 500 * 15 / 1M, no web search
    assertThat((BigDecimal) aggregation.get("cost_usd")).isEqualByComparingTo("0.0105");
    assertThat(
            jdbc.queryForObject(
                "SELECT aggregate_json ->> 'title' FROM digest_run WHERE id = ?",
                String.class,
                run.get("id")))
        .isEqualTo("Fake-Digest");

    assertThat(lastSuccessfulRun(topic)).isEqualTo(today);

    long runId = (Long) run.get("id");
    assertThat(
            jdbc.queryForObject(
                "SELECT pdf_s3_key FROM digest_run WHERE id = ?", String.class, runId))
        .isEqualTo("runs/" + runId + "/digest.pdf");
    List<Mailpit.Mail> mails = Mailpit.awaitMailsTo(recipientOf(topic), 1);
    assertThat(mails).hasSize(1);
    Mailpit.Mail mail = mails.getFirst();
    assertThat(mail.subject())
        .isEqualTo(
            "[News Digest] Happy "
                + suffix
                + " – "
                + today.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(Locale.ENGLISH)));
    assertThat(mail.from()).isEqualTo(MAIL_FROM);
    assertThat(mail.html()).contains("Fake-Digest", "Summary");
    assertThat(mail.text()).contains("SUMMARY", "Fake-Digest");
    assertThat(mail.attachments()).hasSize(1);
    Mailpit.Attachment attachment = mail.attachments().getFirst();
    assertThat(attachment.fileName()).isEqualTo("news-digest.pdf");
    assertThat(attachment.contentType()).isEqualTo("application/pdf");
    try (PDDocument pdf = Loader.loadPDF(attachment.content())) {
      assertThat(pdf.getNumberOfPages()).isEqualTo(1);
      assertThat(new PDFTextStripper().getText(pdf)).contains("Fake-Digest");
    }
    assertThat(deliveryStatuses(runId)).containsExactly("SENT");
    assertThat(visited(instance))
        .doesNotContain("notifyOperatorMail")
        .contains(
            "prepareRun",
            "checkBudget",
            "researchFork",
            "research",
            "researchJoin",
            "checkNews",
            "aggregate",
            "render",
            "sendMails",
            "mailGateway",
            "finishCompleted",
            "endCompleted");
  }

  @Test
  void failingModelEndsTheRunAsFailedWithoutMovingTheLastSuccessfulRun() {
    String suffix = unique();
    long model = model("fail-" + suffix);
    long topic = topic("Fail " + suffix, model, model);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT error_message FROM provider_result WHERE digest_run_id = ?",
                String.class,
                runId(instance)))
        .contains("Fake provider error");
    assertThat(lastSuccessfulRun(topic)).isNull();
    assertThat(visited(instance)).contains("notifyOperatorFailed", "endFailed");
    assertThat(operatorMailAbout("Fail " + suffix).text())
        .contains("Kein Recherche-Modell", "Lauf: " + runId(instance));
    assertThat(Mailpit.mailsTo(recipientOf(topic))).isEmpty();
  }

  @Test
  void noValidNewsEndsAsNoNewsAndMovesTheLastSuccessfulRun() {
    String suffix = unique();
    long model = model("empty-" + suffix);
    long topic = topic("Empty " + suffix, model, model);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("NO_NEWS");
    assertThat(lastSuccessfulRun(topic)).isEqualTo(LocalDate.now(ZoneId.of("Europe/Berlin")));
    assertThat(visited(instance)).doesNotContain("aggregate", "sendMails");
  }

  @Test
  void failingAggregatorEndsTheRunAsFailedWithoutRetriesAndInformsTheOperator() {
    String suffix = unique();
    long researcher = model("ok-" + suffix);
    long aggregator = model("fail-agg-" + suffix);
    long topic = topic("Aggregator " + suffix, aggregator, researcher);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("FAILED");
    assertThat(fakeProvider.aggregations("fail-agg-" + suffix)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT error_message FROM digest_run WHERE id = ?", String.class, runId(instance)))
        .isEqualTo("Aggregation fehlgeschlagen: Fake provider error for fail-agg-" + suffix);
    assertThat(lastSuccessfulRun(topic)).isNull();
    assertThat(visited(instance))
        .contains("aggregationFailed", "notifyOperatorAggregation", "finishFailed", "endFailed")
        .doesNotContain("render", "sendMails");
    assertThat(operatorMailAbout("Aggregator " + suffix).text())
        .contains("Aggregation ist fehlgeschlagen", "Fehler: Aggregation fehlgeschlagen");
  }

  @Test
  void failedRecipientIsRetriedThenLoggedAsFailedAndTheOthersGetTheMailOnlyOnce() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Teilweise " + suffix, model, model);
    long rejected = recipient(topic, "rejected-" + suffix + "@example.test");

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("COMPLETED");
    long runId = runId(instance);
    // Three attempts of the job, but the accepted recipient got the mail exactly once
    assertThat(Mailpit.mailsTo(recipientOf(topic))).hasSize(1);
    Map<String, Object> failed =
        jdbc.queryForMap(
            "SELECT status, sent_at, error_message FROM mail_delivery"
                + " WHERE digest_run_id = ? AND recipient_id = ?",
            runId,
            rejected);
    assertThat(failed.get("status")).isEqualTo("FAILED");
    assertThat(failed.get("sent_at")).isNull();
    assertThat((String) failed.get("error_message")).isNotBlank();
    assertThat(deliveryStatuses(runId)).containsExactlyInAnyOrder("SENT", "FAILED");
    assertThat(visited(instance)).contains("notifyOperatorMail", "finishCompleted");
    assertThat(operatorMailAbout("Teilweise " + suffix).text())
        .contains("nicht alle Empfänger", "Zugestellt: 1, fehlgeschlagen: 1");
    assertThat(lastSuccessfulRun(topic)).isEqualTo(LocalDate.now(ZoneId.of("Europe/Berlin")));
  }

  @Test
  void noRecipientReachedLeadsToAnIncident() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Niemand " + suffix, model, model);
    jdbc.update("UPDATE recipient SET active = false WHERE email = ?", recipientOf(topic));
    recipient(topic, "rejected-" + suffix + "@example.test");

    String instance = starter.start(topic);
    try {
      awaitIncident(instance);

      assertThat(runStatus(instance)).isEqualTo("RUNNING");
      assertThat(deliveryStatuses(runId(instance))).containsExactly("FAILED");
    } finally {
      runtimeService.deleteProcessInstance(instance, "test cleanup");
    }
  }

  @Test
  void topicWithoutActiveRecipientsEndsAsFailedWithoutPdf() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Ohne Empfänger " + suffix, model, model);
    jdbc.update("UPDATE recipient SET active = false WHERE email = ?", recipientOf(topic));

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT pdf_s3_key FROM digest_run WHERE id = ?", String.class, runId(instance)))
        .isNull();
    assertThat(visited(instance))
        .contains("noRecipients", "notifyOperatorNoRecipients", "finishFailed")
        .doesNotContain("sendMails");
    assertThat(operatorMailAbout("Ohne Empfänger " + suffix).text())
        .contains("keine aktiven Empfänger");
    assertThat(lastSuccessfulRun(topic)).isNull();
  }

  @Test
  void topicWithoutResearchModelsEndsAsFailed() {
    String suffix = unique();
    long aggregator = model("ok-" + suffix);
    long topic = topic("Ohne Modelle " + suffix, aggregator);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(runStatus(instance)).isEqualTo("FAILED");
  }

  @Test
  void exhaustedBudgetSkipsTheRunWithoutCallingAModel() {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Budget " + suffix, model, model);
    jdbc.update("INSERT INTO budget (month_limit_usd, warn_percent, active) VALUES (0, 80, true)");
    try {
      String instance = starter.start(topic);
      awaitEnd(instance);

      assertThat(runStatus(instance)).isEqualTo("SKIPPED_BUDGET");
      assertThat(fakeProvider.calls("ok-" + suffix)).isZero();
      assertThat(lastSuccessfulRun(topic)).isNull();
      assertThat(visited(instance)).contains("notifyOperatorBudget", "endSkipped");
      assertThat(operatorMailAbout("Budget " + suffix).text())
          .contains("Monatsbudget ist erreicht", "Ausgaben im Monat: ", " USD von 0");
    } finally {
      jdbc.update("UPDATE budget SET active = false WHERE active");
    }
  }

  @Test
  void secondStartOfARunningTopicIsRejected() {
    String suffix = unique();
    long model = model("slow-" + suffix);
    long topic = topic("Slow " + suffix, model, model);

    String instance = starter.start(topic);
    try {
      fakeProvider.awaitSlowCall();

      assertThatThrownBy(() -> starter.start(topic))
          .isInstanceOf(RunAlreadyActiveException.class)
          .hasMessageContaining(String.valueOf(topic));
    } finally {
      fakeProvider.release();
    }
    awaitEnd(instance);
    assertThat(runStatus(instance)).isEqualTo("COMPLETED");

    // After the run has ended, the topic can run again
    long fast = model("ok-" + suffix);
    jdbc.update("UPDATE topic_model SET model_id = ? WHERE topic_id = ?", fast, topic);
    String next = starter.start(topic);
    awaitEnd(next);
    assertThat(runStatus(next)).isEqualTo("COMPLETED");
  }

  @Test
  void changedModelChoiceAppliesToTheNextRunWithoutNewProcessVersion() {
    String suffix = unique();
    long first = model("ok-a-" + suffix);
    long second = model("ok-b-" + suffix);
    long topic = topic("Wechsel " + suffix, first, first);
    String firstRun = starter.start(topic);
    awaitEnd(firstRun);

    jdbc.update("UPDATE topic_model SET model_id = ? WHERE topic_id = ?", second, topic);
    String secondRun = starter.start(topic);
    awaitEnd(secondRun);

    assertThat(fakeProvider.calls("ok-a-" + suffix)).isEqualTo(1);
    assertThat(fakeProvider.calls("ok-b-" + suffix)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT model_id FROM provider_result WHERE digest_run_id = ?",
                Long.class,
                runId(secondRun)))
        .isEqualTo(second);
    // Both runs use the same deployed process definition
    assertThat(processDefinition(firstRun)).isEqualTo(processDefinition(secondRun));
    // The second run continues where the first one ended (same day: today only)
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
    assertThat(
            jdbc.queryForObject(
                "SELECT period_from FROM digest_run WHERE id = ?",
                LocalDate.class,
                runId(secondRun)))
        .isEqualTo(today);
  }

  @Test
  void inactiveModelsAreNotResearched() {
    String suffix = unique();
    long active = model("ok-active-" + suffix);
    long inactive = model("ok-inactive-" + suffix);
    jdbc.update("UPDATE ai_model SET active = false WHERE id = ?", inactive);
    long topic = topic("Inaktiv " + suffix, active, active, inactive);

    String instance = starter.start(topic);
    awaitEnd(instance);

    assertThat(fakeProvider.calls("ok-inactive-" + suffix)).isZero();
    assertThat(fakeProvider.calls("ok-active-" + suffix)).isEqualTo(1);
  }

  @Test
  void unknownTopicCannotBeStarted() {
    assertThatThrownBy(() -> starter.start(Long.MAX_VALUE))
        .isInstanceOf(TopicNotFoundException.class);
  }

  private LocalDate lastSuccessfulRun(long topicId) {
    List<LocalDate> values =
        jdbc.queryForList(
            "SELECT last_successful_run_at FROM topic_state WHERE topic_id = ?",
            LocalDate.class,
            topicId);
    return values.isEmpty() ? null : values.getFirst();
  }

  private List<String> deliveryStatuses(long runId) {
    return jdbc.queryForList(
        "SELECT status FROM mail_delivery WHERE digest_run_id = ? ORDER BY id",
        String.class,
        runId);
  }

  /** The operator mail about a topic (the operator address is shared by all tests). */
  private Mailpit.Mail operatorMailAbout(String topicName) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    while (Instant.now().isBefore(deadline)) {
      Optional<Mailpit.Mail> mail =
          Mailpit.mailsTo(MAIL_OPERATOR).stream()
              .filter(candidate -> candidate.subject().endsWith("– " + topicName))
              .findFirst();
      if (mail.isPresent()) {
        return mail.get();
      }
      sleep();
    }
    throw new AssertionError("No operator mail about " + topicName);
  }

  private void awaitIncident(String instance) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
    while (Instant.now().isBefore(deadline)) {
      if (runtimeService.createIncidentQuery().processInstanceId(instance).count() > 0) {
        return;
      }
      sleep();
    }
    throw new AssertionError("No incident for " + instance);
  }

  private static void sleep() {
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private long newsItems(long runId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM news_item n JOIN provider_result p ON p.id = n.provider_result_id"
            + " WHERE p.digest_run_id = ?",
        Long.class,
        runId);
  }

  private List<String> visited(String instance) {
    return historyService
        .createHistoricActivityInstanceQuery()
        .processInstanceId(instance)
        .list()
        .stream()
        .map(HistoricActivityInstance::getActivityId)
        .toList();
  }

  private String processDefinition(String instance) {
    return historyService
        .createHistoricProcessInstanceQuery()
        .processInstanceId(instance)
        .singleResult()
        .getProcessDefinitionId();
  }
}
