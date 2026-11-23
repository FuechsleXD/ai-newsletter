package de.fuechsle.ainewsletter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Round trip of all entities through Hibernate and PostgreSQL: every mapped field is written, read
 * back from the database (after clearing the persistence context) and compared.
 */
@SpringBootTest
@Transactional
class EntityMappingTest extends PostgresTestSupport {

  private static final Instant STARTED = Instant.parse("2026-11-23T06:00:00Z");

  private final EntityManager em;

  @Autowired
  EntityMappingTest(EntityManager em) {
    this.em = em;
  }

  @Test
  void configurationEntitiesRoundTrip() {
    Schedule schedule = new Schedule("Täglich", "0 0 6 * * *", "UTC");
    schedule.setName("Täglich 06:00");
    schedule.setCronExpression("0 0 6 * * MON-FRI");
    schedule.setTimezone("Europe/Berlin");
    em.persist(schedule);

    AiProvider provider = new AiProvider("OPENAI", "OpenAI", "newsdigest/prod/ai/openai");
    provider.setDisplayName("OpenAI (GPT)");
    provider.setSecretName("newsdigest/prod/ai/openai-2");
    provider.setMaxConcurrentCalls(4);
    provider.setActive(false);
    em.persist(provider);

    AiModel model =
        new AiModel(
            provider, "model-x", "X", false, false, new BigDecimal("1.5"), new BigDecimal("6"));
    model.setDisplayName("Model X");
    model.setSupportsWebSearch(true);
    model.setSupportsStructuredOutput(true);
    model.setMaxOutputTokens(8000);
    model.setSupportsTemperature(true);
    model.setTemperature(new BigDecimal("0.30"));
    model.setInputPricePerMtok(new BigDecimal("2.5"));
    model.setOutputPricePerMtok(new BigDecimal("10"));
    model.setPricePerSearchCall(new BigDecimal("0.01"));
    model.setActive(false);
    em.persist(model);

    Recipient recipient = new Recipient("  Someone@Example.TEST ");
    recipient.setDisplayName("Someone");
    recipient.setActive(false);
    em.persist(recipient);

    Topic topic = new Topic("KI", schedule, model);
    topic.setName("Künstliche Intelligenz");
    topic.setDescription("Neuigkeiten zu Sprachmodellen");
    topic.setInitialLookbackDays(14);
    topic.setAggregatorModel(model);
    topic.setSchedule(schedule);
    topic.setActive(false);
    topic.getModels().add(model);
    topic.getRecipients().add(recipient);
    em.persist(topic);

    TopicState state = new TopicState(topic);
    state.setLastSuccessfulRunAt(LocalDate.of(2026, 11, 22));
    em.persist(state);

    Budget budget = new Budget(new BigDecimal("12.5"), 75);
    budget.setActive(false);
    em.persist(budget);

    flushAndClear();

    Topic loadedTopic = em.find(Topic.class, topic.getId());
    assertThat(loadedTopic.getName()).isEqualTo("Künstliche Intelligenz");
    assertThat(loadedTopic.getDescription()).isEqualTo("Neuigkeiten zu Sprachmodellen");
    assertThat(loadedTopic.getInitialLookbackDays()).isEqualTo(14);
    assertThat(loadedTopic.isActive()).isFalse();
    assertThat(loadedTopic.getVersion()).isZero();
    assertThat(loadedTopic.getCreatedAt()).isNotNull();
    assertThat(loadedTopic.getUpdatedAt()).isEqualTo(loadedTopic.getCreatedAt());
    assertThat(loadedTopic.getSchedule().getId()).isEqualTo(schedule.getId());
    assertThat(loadedTopic.getAggregatorModel().getId()).isEqualTo(model.getId());
    assertThat(loadedTopic.getModels()).extracting(AiModel::getId).containsExactly(model.getId());
    assertThat(loadedTopic.getRecipients())
        .extracting(Recipient::getId)
        .containsExactly(recipient.getId());

    Schedule loadedSchedule = em.find(Schedule.class, schedule.getId());
    assertThat(loadedSchedule.getName()).isEqualTo("Täglich 06:00");
    assertThat(loadedSchedule.getCronExpression()).isEqualTo("0 0 6 * * MON-FRI");
    assertThat(loadedSchedule.getTimezone()).isEqualTo("Europe/Berlin");

    AiProvider loadedProvider = em.find(AiProvider.class, provider.getId());
    assertThat(loadedProvider.getCode()).isEqualTo("OPENAI");
    assertThat(loadedProvider.getDisplayName()).isEqualTo("OpenAI (GPT)");
    assertThat(loadedProvider.getSecretName()).isEqualTo("newsdigest/prod/ai/openai-2");
    assertThat(loadedProvider.getMaxConcurrentCalls()).isEqualTo(4);
    assertThat(loadedProvider.isActive()).isFalse();

    AiModel loadedModel = em.find(AiModel.class, model.getId());
    assertThat(loadedModel.getProvider().getId()).isEqualTo(provider.getId());
    assertThat(loadedModel.getModelName()).isEqualTo("model-x");
    assertThat(loadedModel.getDisplayName()).isEqualTo("Model X");
    assertThat(loadedModel.isSupportsWebSearch()).isTrue();
    assertThat(loadedModel.isSupportsStructuredOutput()).isTrue();
    assertThat(loadedModel.getMaxOutputTokens()).isEqualTo(8000);
    assertThat(loadedModel.isSupportsTemperature()).isTrue();
    assertThat(loadedModel.getTemperature()).isEqualByComparingTo("0.3");
    assertThat(loadedModel.getInputPricePerMtok()).isEqualByComparingTo("2.5");
    assertThat(loadedModel.getOutputPricePerMtok()).isEqualByComparingTo("10");
    assertThat(loadedModel.getPricePerSearchCall()).isEqualByComparingTo("0.01");
    assertThat(loadedModel.isActive()).isFalse();

    Recipient loadedRecipient = em.find(Recipient.class, recipient.getId());
    assertThat(loadedRecipient.getEmail()).isEqualTo("someone@example.test");
    assertThat(loadedRecipient.getDisplayName()).isEqualTo("Someone");
    assertThat(loadedRecipient.isActive()).isFalse();

    TopicState loadedState = em.find(TopicState.class, topic.getId());
    assertThat(loadedState.getTopicId()).isEqualTo(topic.getId());
    assertThat(loadedState.getTopic().getId()).isEqualTo(topic.getId());
    assertThat(loadedState.getLastSuccessfulRunAt()).isEqualTo(LocalDate.of(2026, 11, 22));

    Budget loadedBudget = em.find(Budget.class, budget.getId());
    assertThat(loadedBudget.getMonthLimitUsd()).isEqualByComparingTo("12.5");
    assertThat(loadedBudget.getWarnPercent()).isEqualTo(75);
    assertThat(loadedBudget.isActive()).isFalse();
  }

  @Test
  void updatingAConfigurationEntityIncrementsVersionAndUpdatedAt() {
    Schedule schedule = new Schedule("Stündlich", "0 0 * * * *", "UTC");
    em.persist(schedule);
    flushAndClear();
    Instant createdAt = em.find(Schedule.class, schedule.getId()).getCreatedAt();

    Schedule loaded = em.find(Schedule.class, schedule.getId());
    loaded.setCronExpression("0 30 * * * *");
    flushAndClear();

    Schedule updated = em.find(Schedule.class, schedule.getId());
    assertThat(updated.getVersion()).isOne();
    assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
    assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
  }

  @Test
  void historyEntitiesRoundTrip() {
    Schedule schedule = new Schedule("Wöchentlich", "0 0 7 * * MON", "Europe/Berlin");
    em.persist(schedule);
    AiProvider provider = new AiProvider("GEMINI", "Gemini", "newsdigest/prod/ai/gemini");
    em.persist(provider);
    AiModel model =
        new AiModel(provider, "model-y", "Y", true, true, BigDecimal.ONE, BigDecimal.TEN);
    em.persist(model);
    Topic topic = new Topic("Robotik", schedule, model);
    em.persist(topic);
    Recipient recipient = new Recipient("reader@example.test");
    em.persist(recipient);

    DigestRun run =
        new DigestRun(
            topic, model, "v1", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22), STARTED);
    run.setProcessInstanceId("proc-123");
    run.setStatus(RunStatus.COMPLETED);
    run.setFinishedAt(STARTED.plus(5, ChronoUnit.MINUTES));
    run.setErrorMessage("none");
    run.setAggregateJson("{\"sections\": []}");
    run.setPdfS3Key("digests/2026/11/run-1.pdf");
    em.persist(run);

    ProviderResult result = new ProviderResult(run, model, ProviderResultStatus.SUCCEEDED, 4200);
    result.setRawResponse("{\"items\": []}");
    result.setInputTokens(1000);
    result.setOutputTokens(500);
    result.setSearchCalls(3);
    result.setCostUsd(new BigDecimal("0.012345"));
    result.setErrorMessage("retry once");
    em.persist(result);

    NewsItem item =
        new NewsItem(
            result,
            "Neues Modell",
            "Zusammenfassung.",
            "https://News.example.test/a?utm_source=x",
            "https://news.example.test/a",
            LocalDate.of(2026, 11, 20),
            NewsItemStatus.DUPLICATE_RUN);
    item.setStatus(NewsItemStatus.VALID);
    em.persist(item);

    AggregationResult aggregation =
        new AggregationResult(run, model, 3000, 800, new BigDecimal("0.05"), 9000);
    em.persist(aggregation);

    MailDelivery sent = new MailDelivery(run, recipient);
    sent.markFailed("timeout");
    sent.markSent(STARTED.plus(6, ChronoUnit.MINUTES));
    em.persist(sent);

    flushAndClear();

    DigestRun loadedRun = em.find(DigestRun.class, run.getId());
    assertThat(loadedRun.getTopic().getId()).isEqualTo(topic.getId());
    assertThat(loadedRun.getAggregatorModel().getId()).isEqualTo(model.getId());
    assertThat(loadedRun.getProcessInstanceId()).isEqualTo("proc-123");
    assertThat(loadedRun.getPromptVersion()).isEqualTo("v1");
    assertThat(loadedRun.getPeriodFrom()).isEqualTo(LocalDate.of(2026, 11, 16));
    assertThat(loadedRun.getPeriodTo()).isEqualTo(LocalDate.of(2026, 11, 22));
    assertThat(loadedRun.getStatus()).isEqualTo(RunStatus.COMPLETED);
    assertThat(loadedRun.getStartedAt()).isEqualTo(STARTED);
    assertThat(loadedRun.getFinishedAt()).isEqualTo(STARTED.plus(5, ChronoUnit.MINUTES));
    assertThat(loadedRun.getErrorMessage()).isEqualTo("none");
    assertThat(loadedRun.getAggregateJson()).contains("\"sections\"");
    assertThat(loadedRun.getPdfS3Key()).isEqualTo("digests/2026/11/run-1.pdf");

    ProviderResult loadedResult = em.find(ProviderResult.class, result.getId());
    assertThat(loadedResult.getDigestRun().getId()).isEqualTo(run.getId());
    assertThat(loadedResult.getModel().getId()).isEqualTo(model.getId());
    assertThat(loadedResult.getStatus()).isEqualTo(ProviderResultStatus.SUCCEEDED);
    assertThat(loadedResult.getRawResponse()).isEqualTo("{\"items\": []}");
    assertThat(loadedResult.getDurationMs()).isEqualTo(4200);
    assertThat(loadedResult.getInputTokens()).isEqualTo(1000);
    assertThat(loadedResult.getOutputTokens()).isEqualTo(500);
    assertThat(loadedResult.getSearchCalls()).isEqualTo(3);
    assertThat(loadedResult.getCostUsd()).isEqualByComparingTo("0.012345");
    assertThat(loadedResult.getErrorMessage()).isEqualTo("retry once");

    NewsItem loadedItem = em.find(NewsItem.class, item.getId());
    assertThat(loadedItem.getProviderResult().getId()).isEqualTo(result.getId());
    assertThat(loadedItem.getHeadline()).isEqualTo("Neues Modell");
    assertThat(loadedItem.getSummary()).isEqualTo("Zusammenfassung.");
    assertThat(loadedItem.getUrl()).isEqualTo("https://News.example.test/a?utm_source=x");
    assertThat(loadedItem.getNormalizedUrl()).isEqualTo("https://news.example.test/a");
    assertThat(loadedItem.getPublishedAt()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(loadedItem.getStatus()).isEqualTo(NewsItemStatus.VALID);

    AggregationResult loadedAggregation = em.find(AggregationResult.class, run.getId());
    assertThat(loadedAggregation.getDigestRunId()).isEqualTo(run.getId());
    assertThat(loadedAggregation.getDigestRun().getId()).isEqualTo(run.getId());
    assertThat(loadedAggregation.getModel().getId()).isEqualTo(model.getId());
    assertThat(loadedAggregation.getInputTokens()).isEqualTo(3000);
    assertThat(loadedAggregation.getOutputTokens()).isEqualTo(800);
    assertThat(loadedAggregation.getCostUsd()).isEqualByComparingTo("0.05");
    assertThat(loadedAggregation.getDurationMs()).isEqualTo(9000);

    MailDelivery loadedDelivery = em.find(MailDelivery.class, sent.getId());
    assertThat(loadedDelivery.getDigestRun().getId()).isEqualTo(run.getId());
    assertThat(loadedDelivery.getRecipient().getId()).isEqualTo(recipient.getId());
    assertThat(loadedDelivery.getStatus()).isEqualTo(MailDeliveryStatus.SENT);
    assertThat(loadedDelivery.getSentAt()).isEqualTo(STARTED.plus(6, ChronoUnit.MINUTES));
    assertThat(loadedDelivery.getErrorMessage()).isNull();
  }

  @Test
  void newRunStartsAsRunningAndNewDeliveryAsPending() {
    Schedule schedule = new Schedule("Monatlich", "0 0 7 1 * *", "UTC");
    em.persist(schedule);
    AiProvider provider = new AiProvider("MISTRAL", "Mistral", "newsdigest/prod/ai/mistral");
    em.persist(provider);
    AiModel model = new AiModel(provider, "m", "M", true, true, BigDecimal.ONE, BigDecimal.ONE);
    em.persist(model);
    Topic topic = new Topic("Energie", schedule, model);
    em.persist(topic);
    DigestRun run =
        new DigestRun(
            topic, model, "v1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), STARTED);
    em.persist(run);
    MailDelivery delivery = new MailDelivery(run, new Recipient("x@example.test"));
    em.persist(delivery.getRecipient());
    em.persist(delivery);
    delivery.markFailed("mailbox full");
    flushAndClear();

    assertThat(em.find(DigestRun.class, run.getId()).getStatus()).isEqualTo(RunStatus.RUNNING);
    MailDelivery loaded = em.find(MailDelivery.class, delivery.getId());
    assertThat(loaded.getStatus()).isEqualTo(MailDeliveryStatus.FAILED);
    assertThat(loaded.getErrorMessage()).isEqualTo("mailbox full");
    assertThat(loaded.getSentAt()).isNull();
    assertThat(new MailDelivery(run, loaded.getRecipient()).getStatus())
        .isEqualTo(MailDeliveryStatus.PENDING);
  }

  private void flushAndClear() {
    em.flush();
    em.clear();
  }
}
