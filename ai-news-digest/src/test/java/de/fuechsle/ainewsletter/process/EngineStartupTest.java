package de.fuechsle.ainewsletter.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.support.PostgresTestSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.cibseven.bpm.engine.HistoryService;
import org.cibseven.bpm.engine.RepositoryService;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.history.HistoricProcessInstance;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.cibseven.bpm.engine.repository.Deployment;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The embedded CIB seven engine starts against the tables created by Flyway in the schema {@code
 * bpm} (ADR-006) and executes a process including an asynchronous job.
 */
@SpringBootTest
// Its job executor must not stay alive and take the jobs of the process tests
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EngineStartupTest extends PostgresTestSupport {

  @DynamicPropertySource
  static void jobExecutor(DynamicPropertyRegistry registry) {
    registry.add("camunda.bpm.job-execution.enabled", () -> "true");
  }

  private final RepositoryService repositoryService;
  private final RuntimeService runtimeService;
  private final HistoryService historyService;
  private final ProcessEngineConfigurationImpl engineConfiguration;
  private final JdbcTemplate jdbc;

  @Autowired
  EngineStartupTest(
      RepositoryService repositoryService,
      RuntimeService runtimeService,
      HistoryService historyService,
      ProcessEngineConfigurationImpl engineConfiguration,
      JdbcTemplate jdbc) {
    this.repositoryService = repositoryService;
    this.runtimeService = runtimeService;
    this.historyService = historyService;
    this.engineConfiguration = engineConfiguration;
    this.jdbc = jdbc;
  }

  @Test
  void engineRunsAProcessWithAnAsyncJobOnTheFlywaySchema() throws InterruptedException {
    Deployment deployment =
        repositoryService
            .createDeployment()
            .name("engine-smoke-test")
            .addClasspathResource("bpmn/engine-smoke-test.bpmn")
            .deploy();
    try {
      ProcessInstance instance = runtimeService.startProcessInstanceByKey("engineSmokeTest");

      HistoricProcessInstance finished = awaitFinished(instance.getId(), Duration.ofSeconds(30));

      assertThat(finished.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
      assertThat(
              historyService
                  .createHistoricVariableInstanceQuery()
                  .processInstanceId(instance.getId())
                  .variableName("visited")
                  .singleResult()
                  .getValue())
          .isEqualTo(true);
    } finally {
      repositoryService.deleteDeployment(deployment.getId(), true);
    }
  }

  @Test
  void engineUsesTheBpmSchemaAndDoesNotManageIt() {
    assertThat(engineConfiguration.getDatabaseSchemaUpdate()).isEqualTo("false");
    assertThat(engineConfiguration.getDatabaseSchema()).isEqualTo("bpm");
    assertThat(engineConfiguration.getDatabaseTablePrefix()).isEqualTo("bpm.");

    List<String> engineTablesInBpm = engineTables("bpm");
    assertThat(engineTablesInBpm)
        .contains("act_ge_property", "act_ru_job", "act_hi_procinst", "act_re_decision_def");
    assertThat(engineTables("app")).isEmpty();
  }

  @Test
  void processDefaultsComeFromNewsDigestProperties() {
    assertThat(engineConfiguration.getHistoryTimeToLive()).isEqualTo("P30D");
    assertThat(engineConfiguration.getFailedJobRetryTimeCycle()).isEqualTo("R3/PT5M");
    assertThat(engineConfiguration.getJobExecutor().getLockTimeInMillis())
        .isEqualTo((int) Duration.ofMinutes(20).toMillis());
  }

  @Test
  void appUserHasDataAccessButNoDdlRightsOnTheEngineSchema() throws SQLException {
    // Data access works through the app datasource (the engine itself relies on it)
    assertThat(jdbc.queryForObject("SELECT count(*) FROM bpm.act_ge_property", Long.class))
        .isPositive();

    try (Connection connection =
            DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PASSWORD);
        Statement statement = connection.createStatement()) {
      assertThatThrownBy(() -> statement.execute("CREATE TABLE bpm.not_allowed (id int)"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("permission denied");
      assertThatThrownBy(() -> statement.execute("DROP TABLE bpm.act_ge_property"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("must be owner");
    }
  }

  private List<String> engineTables(String schema) {
    return jdbc.queryForList(
        "SELECT lower(table_name) FROM information_schema.tables"
            + " WHERE table_schema = ? AND lower(table_name) LIKE 'act\\_%'",
        String.class, schema);
  }

  private HistoricProcessInstance awaitFinished(String processInstanceId, Duration timeout)
      throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      HistoricProcessInstance instance =
          historyService
              .createHistoricProcessInstanceQuery()
              .processInstanceId(processInstanceId)
              .finished()
              .singleResult();
      if (instance != null) {
        return instance;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Process instance did not finish within " + timeout);
  }
}
