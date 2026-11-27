package de.fuechsle.ainewsletter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.support.PostgresTestSupport;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the Flyway migrations against a real PostgreSQL: all migrations applied, seed data, the
 * constraints of docs/03-datenmodell.md, privileges of the app user and the monthly cost view.
 *
 * <p>Every test runs in a transaction that is rolled back. The JdbcTemplate uses the app user.
 */
@SpringBootTest
@Transactional
class SchemaMigrationTest extends PostgresTestSupport {

  private final JdbcTemplate jdbc;
  private final Flyway flyway;

  @Autowired
  SchemaMigrationTest(JdbcTemplate jdbc, Flyway flyway) {
    this.jdbc = jdbc;
    this.flyway = flyway;
  }

  // ---------------------------------------------------------------------------------------------
  // Migrations and seed data
  // ---------------------------------------------------------------------------------------------

  @Test
  void allMigrationsAreAppliedSuccessfully() {
    // Without the "schema creation" marker Flyway writes before the first migration
    MigrationInfo[] applied =
        Arrays.stream(flyway.info().applied())
            .filter(info -> !info.getScript().startsWith("\""))
            .toArray(MigrationInfo[]::new);

    assertThat(applied)
        .extracting(MigrationInfo::getScript)
        .containsExactly(
            "V1__app_initial_schema.sql",
            "V2__app_seed.sql",
            "de.fuechsle.ainewsletter.migration.V3__bpm_create_engine_3_0",
            "V4__app_topic_aggregator_required.sql",
            "V5__app_model_supports_temperature.sql",
            "V6__app_provider_result_search_trace.sql",
            "V7__app_price_tier_and_budget_warning.sql",
            "R__monthly_cost.sql");
    assertThat(Arrays.stream(applied).map(MigrationInfo::getState))
        .allMatch(MigrationState::isApplied)
        .noneMatch(MigrationState::isFailed);
    assertThat(flyway.info().pending()).isEmpty();
  }

  @Test
  void seedContainsDefaultScheduleAndAnthropicProviderOnly() {
    assertThat(
            jdbc.queryForMap(
                "SELECT cron_expression, timezone FROM schedule WHERE name = 'Wöchentlich montags 07:00'"))
        .containsEntry("cron_expression", "0 0 7 * * MON")
        .containsEntry("timezone", "Europe/Berlin");
    assertThat(
            jdbc.queryForMap(
                "SELECT secret_name, active FROM ai_provider WHERE code = 'ANTHROPIC'"))
        .containsEntry("secret_name", "newsdigest/prod/ai/anthropic")
        .containsEntry("active", true);
    // Other test classes share the database, so the seed itself is checked instead of row counts:
    // no models, prices, recipients or budgets (ADR-013, no real mail addresses).
    assertThat(seedStatements())
        .allSatisfy(statement ->
            assertThat(statement).containsAnyOf("INSERT INTO schedule", "INSERT INTO ai_provider"));
  }

  private static java.util.List<String> seedStatements() {
    try (java.io.InputStream in =
        SchemaMigrationTest.class
            .getClassLoader()
            .getResourceAsStream("db/migration/app/V2__app_seed.sql")) {
      String sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      return java.util.Arrays.stream(sql.split(";"))
          .map(part -> part.lines().filter(line -> !line.strip().startsWith("--")))
          .map(lines -> lines.collect(java.util.stream.Collectors.joining(" ")).strip())
          .filter(statement -> !statement.isEmpty())
          .toList();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Constraints
  // ---------------------------------------------------------------------------------------------

  @Test
  void scheduleNameIsUnique() {
    assertViolates(
        "uq_schedule_name",
        "INSERT INTO schedule (name, cron_expression, timezone)"
            + " VALUES ('Wöchentlich montags 07:00', '0 0 8 * * MON', 'UTC')");
  }

  @Test
  void providerCodeMustBeUpperCase() {
    assertViolates(
        "ck_ai_provider_code",
        "INSERT INTO ai_provider (code, display_name, secret_name) VALUES ('openai', 'x', 'y')");
  }

  @Test
  void modelNameIsUniquePerProvider() {
    long providerId = anthropicId();
    insertModel(providerId, "model-a");

    assertViolates(
        "uq_ai_model_provider_model_name",
        "INSERT INTO ai_model (provider_id, model_name, display_name, supports_web_search,"
            + " supports_structured_output, input_price_per_mtok, output_price_per_mtok)"
            + " VALUES (?, 'model-a', 'A', true, true, 1, 2)",
        providerId);
  }

  @Test
  void modelPricesMustNotBeNegative() {
    assertViolates(
        "ck_ai_model_prices",
        "INSERT INTO ai_model (provider_id, model_name, display_name, supports_web_search,"
            + " supports_structured_output, input_price_per_mtok, output_price_per_mtok)"
            + " VALUES (?, 'model-b', 'B', true, true, -1, 2)",
        anthropicId());
  }

  @Test
  void temperatureRequiresModelSupport() {
    long modelId = insertModel(anthropicId(), "no-temperature");

    assertViolates(
        "ck_ai_model_temperature_supported",
        "UPDATE ai_model SET temperature = 0.5 WHERE id = ?",
        modelId);
  }

  @Test
  void temperatureIsAllowedForModelsThatSupportIt() {
    long modelId = insertModel(anthropicId(), "with-temperature");

    jdbc.update(
        "UPDATE ai_model SET supports_temperature = true, temperature = 0.5 WHERE id = ?", modelId);

    assertThat(
            jdbc.queryForObject(
                "SELECT temperature FROM ai_model WHERE id = ?", BigDecimal.class, modelId))
        .isEqualByComparingTo("0.5");
  }

  @Test
  void topicRequiresExistingSchedule() {
    assertViolates(
        "fk_topic_schedule",
        "INSERT INTO topic (name, schedule_id, aggregator_model_id) VALUES ('KI', 999999, ?)",
        insertModel(anthropicId(), "aggregator"));
  }

  @Test
  void topicRequiresAnAggregatorModel() {
    assertViolates(
        "aggregator_model_id",
        "INSERT INTO topic (name, schedule_id) VALUES ('KI', ?)",
        defaultScheduleId());
  }

  @Test
  void topicModelPairIsUnique() {
    long topicId = insertTopic("KI");
    long modelId = insertModel(anthropicId(), "model-c");
    jdbc.update("INSERT INTO topic_model (topic_id, model_id) VALUES (?, ?)", topicId, modelId);

    assertViolates(
        "pk_topic_model",
        "INSERT INTO topic_model (topic_id, model_id) VALUES (?, ?)",
        topicId,
        modelId);
  }

  @Test
  void modelInUseByTopicCannotBeDeleted() {
    long topicId = insertTopic("KI");
    long modelId = insertModel(anthropicId(), "model-d");
    jdbc.update("INSERT INTO topic_model (topic_id, model_id) VALUES (?, ?)", topicId, modelId);

    assertViolates("fk_topic_model_model", "DELETE FROM ai_model WHERE id = ?", modelId);
  }

  @Test
  void recipientEmailMustBeLowerCase() {
    assertViolates(
        "ck_recipient_email_lower_case",
        "INSERT INTO recipient (email) VALUES ('Someone@Example.test')");
  }

  @Test
  void recipientEmailIsUnique() {
    jdbc.update("INSERT INTO recipient (email) VALUES ('someone@example.test')");

    assertViolates(
        "uq_recipient_email", "INSERT INTO recipient (email) VALUES ('someone@example.test')");
  }

  @Test
  void atMostOneBudgetIsActive() {
    jdbc.update("INSERT INTO budget (month_limit_usd, warn_percent, active) VALUES (5, 80, false)");
    jdbc.update("INSERT INTO budget (month_limit_usd, warn_percent, active) VALUES (5, 80, false)");
    jdbc.update("INSERT INTO budget (month_limit_usd, warn_percent, active) VALUES (10, 80, true)");

    assertViolates(
        "uq_budget_single_active",
        "INSERT INTO budget (month_limit_usd, warn_percent, active) VALUES (20, 90, true)");
  }

  @Test
  void runStatusMustBeKnown() {
    long topicId = insertTopic("KI");
    long modelId = insertModel(anthropicId(), "aggregator");

    assertViolates(
        "ck_digest_run_status",
        "INSERT INTO digest_run (topic_id, aggregator_model_id, prompt_version, period_from,"
            + " period_to, status) VALUES (?, ?, 'v1', DATE '2026-11-16', DATE '2026-11-22', 'DONE')",
        topicId,
        modelId);
  }

  @Test
  void runPeriodMustNotBeReversed() {
    long topicId = insertTopic("KI");
    long modelId = insertModel(anthropicId(), "aggregator");

    assertViolates(
        "ck_digest_run_period",
        "INSERT INTO digest_run (topic_id, aggregator_model_id, prompt_version, period_from,"
            + " period_to, status) VALUES (?, ?, 'v1', DATE '2026-11-22', DATE '2026-11-16', 'RUNNING')",
        topicId,
        modelId);
  }

  @Test
  void onlyOneRunningRunPerTopic() {
    long topicId = insertTopic("KI");
    long modelId = insertModel(anthropicId(), "aggregator");
    insertRun(topicId, modelId, "COMPLETED");
    insertRun(topicId, modelId, "FAILED");
    insertRun(topicId, modelId, "RUNNING");
    // A running run of another topic is fine
    insertRun(insertTopic("Robotik"), modelId, "RUNNING");

    assertThatThrownBy(() -> insertRun(topicId, modelId, "RUNNING"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_digest_run_single_running");
  }

  @Test
  void providerResultIsUniquePerRunAndModel() {
    long modelId = insertModel(anthropicId(), "research");
    long runId = insertRun(insertTopic("KI"), modelId, "RUNNING");
    insertProviderResult(runId, modelId, null);

    assertViolates(
        "uq_provider_result_run_model",
        "INSERT INTO provider_result (digest_run_id, model_id, status, duration_ms)"
            + " VALUES (?, ?, 'FAILED', 1)",
        runId,
        modelId);
  }

  @Test
  void newsItemStatusMustBeKnown() {
    long modelId = insertModel(anthropicId(), "research");
    long resultId =
        insertProviderResult(insertRun(insertTopic("KI"), modelId, "RUNNING"), modelId, null);

    assertViolates(
        "ck_news_item_status",
        "INSERT INTO news_item (provider_result_id, headline, summary, url, normalized_url, status)"
            + " VALUES (?, 'h', 's', 'https://a.test', 'https://a.test', 'MAYBE')",
        resultId);
  }

  @Test
  void sentMailRequiresSentAt() {
    long modelId = insertModel(anthropicId(), "aggregator");
    long runId = insertRun(insertTopic("KI"), modelId, "RUNNING");
    long recipientId = insertRecipient("someone@example.test");

    assertViolates(
        "ck_mail_delivery_sent_at",
        "INSERT INTO mail_delivery (digest_run_id, recipient_id, status) VALUES (?, ?, 'SENT')",
        runId,
        recipientId);
  }

  @Test
  void deletingARunDeletesItsHistory() {
    long modelId = insertModel(anthropicId(), "research");
    long runId = insertRun(insertTopic("KI"), modelId, "COMPLETED");
    long resultId = insertProviderResult(runId, modelId, new BigDecimal("0.10"));
    jdbc.update(
        "INSERT INTO news_item (provider_result_id, headline, summary, url, normalized_url, status)"
            + " VALUES (?, 'h', 's', 'https://a.test', 'https://a.test', 'VALID')",
        resultId);
    jdbc.update(
        "INSERT INTO aggregation_result (digest_run_id, model_id, input_tokens, output_tokens,"
            + " cost_usd, duration_ms) VALUES (?, ?, 1, 1, 0.01, 1)",
        runId,
        modelId);
    long recipientId = insertRecipient("someone@example.test");
    jdbc.update(
        "INSERT INTO mail_delivery (digest_run_id, recipient_id, status) VALUES (?, ?, 'PENDING')",
        runId,
        recipientId);

    jdbc.update("DELETE FROM digest_run WHERE id = ?", runId);

    // Counted per run: other test classes leave committed rows in the shared database
    assertThat(countWhere("provider_result", "digest_run_id", runId)).isZero();
    assertThat(countWhere("news_item", "provider_result_id", resultId)).isZero();
    assertThat(countWhere("aggregation_result", "digest_run_id", runId)).isZero();
    assertThat(countWhere("mail_delivery", "digest_run_id", runId)).isZero();
    assertThat(countWhere("recipient", "id", recipientId)).isOne();
  }

  private long countWhere(String table, String column, long id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Long.class, id);
  }

  // ---------------------------------------------------------------------------------------------
  // Privileges
  // ---------------------------------------------------------------------------------------------

  @Test
  void appUserCannotChangeTheSchema() throws SQLException {
    try (Connection connection =
            DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PASSWORD);
        Statement statement = connection.createStatement()) {

      assertThatThrownBy(() -> statement.execute("CREATE TABLE app.not_allowed (id int)"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("permission denied");
      assertThatThrownBy(() -> statement.execute("ALTER TABLE app.topic ADD COLUMN x int"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("must be owner");
      assertThatThrownBy(() -> statement.execute("DROP TABLE app.topic"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("must be owner");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Monthly cost view
  // ---------------------------------------------------------------------------------------------

  @Test
  void monthlyCostSumsResearchAndAggregationPerBerlinMonth() {
    long topicId = insertTopic("KI");
    long modelId = insertModel(anthropicId(), "model");

    // 2026-01-31 23:30 UTC is already 2026-02-01 in Berlin
    long february = insertRunStartedAt(topicId, modelId, "2026-01-31T23:30:00Z");
    insertProviderResult(february, modelId, new BigDecimal("0.25"));
    jdbc.update(
        "INSERT INTO aggregation_result (digest_run_id, model_id, input_tokens, output_tokens,"
            + " cost_usd, duration_ms) VALUES (?, ?, 1, 1, 0.05, 1)",
        february,
        modelId);

    // 2026-01-31 22:30 UTC is still January in Berlin; FAILED call without cost is ignored
    long january = insertRunStartedAt(insertTopic("Robotik"), modelId, "2026-01-31T22:30:00Z");
    insertProviderResult(january, modelId, new BigDecimal("1.00"));
    long secondModel = insertModel(anthropicId(), "model-2");
    insertProviderResult(january, secondModel, null);

    assertThat(costOf(LocalDate.of(2026, 2, 1))).isEqualByComparingTo("0.30");
    assertThat(costOf(LocalDate.of(2026, 1, 1))).isEqualByComparingTo("1.00");
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private void assertViolates(String constraint, String sql, Object... args) {
    assertThatThrownBy(() -> jdbc.update(sql, args))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }

  private long anthropicId() {
    return jdbc.queryForObject("SELECT id FROM ai_provider WHERE code = 'ANTHROPIC'", Long.class);
  }

  private long defaultScheduleId() {
    return jdbc.queryForObject("SELECT min(id) FROM schedule", Long.class);
  }

  private long insertModel(long providerId, String name) {
    return jdbc.queryForObject(
        "INSERT INTO ai_model (provider_id, model_name, display_name, supports_web_search,"
            + " supports_structured_output, input_price_per_mtok, output_price_per_mtok)"
            + " VALUES (?, ?, ?, true, true, 3, 15) RETURNING id",
        Long.class,
        providerId,
        name,
        name);
  }

  private long insertTopic(String name) {
    long aggregatorId = insertModel(anthropicId(), "topic-aggregator-" + name);
    return jdbc.queryForObject(
        "INSERT INTO topic (name, schedule_id, aggregator_model_id) VALUES (?, ?, ?) RETURNING id",
        Long.class,
        name,
        defaultScheduleId(),
        aggregatorId);
  }

  private long insertRecipient(String email) {
    return jdbc.queryForObject(
        "INSERT INTO recipient (email) VALUES (?) RETURNING id", Long.class, email);
  }

  private long insertRun(long topicId, long modelId, String status) {
    return jdbc.queryForObject(
        "INSERT INTO digest_run (topic_id, aggregator_model_id, prompt_version, period_from,"
            + " period_to, status) VALUES (?, ?, 'v1', DATE '2026-11-16', DATE '2026-11-22', ?)"
            + " RETURNING id",
        Long.class,
        topicId,
        modelId,
        status);
  }

  private long insertRunStartedAt(long topicId, long modelId, String startedAt) {
    return jdbc.queryForObject(
        "INSERT INTO digest_run (topic_id, aggregator_model_id, prompt_version, period_from,"
            + " period_to, status, started_at)"
            + " VALUES (?, ?, 'v1', DATE '2026-01-25', DATE '2026-01-31', 'COMPLETED', ?::timestamptz)"
            + " RETURNING id",
        Long.class,
        topicId,
        modelId,
        startedAt);
  }

  private long insertProviderResult(long runId, long modelId, BigDecimal cost) {
    return jdbc.queryForObject(
        "INSERT INTO provider_result (digest_run_id, model_id, status, duration_ms, cost_usd)"
            + " VALUES (?, ?, ?, 1000, ?) RETURNING id",
        Long.class,
        runId,
        modelId,
        cost == null ? "FAILED" : "SUCCEEDED",
        cost);
  }

  private BigDecimal costOf(LocalDate month) {
    return jdbc.queryForObject(
        "SELECT cost_usd FROM monthly_cost WHERE month = ?", BigDecimal.class, month);
  }
}
