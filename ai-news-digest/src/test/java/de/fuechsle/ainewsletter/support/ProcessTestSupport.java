package de.fuechsle.ainewsletter.support;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.cibseven.bpm.engine.HistoryService;
import org.cibseven.bpm.engine.history.HistoricProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base class for process tests: embedded engine with running job executor, real PostgreSQL and the
 * {@link FakeNewsProvider}. Test data is written with JDBC (committed), so the asynchronous jobs of
 * the engine see it. Every test uses its own topic and models (unique names).
 */
@SpringBootTest
@Import(ProcessTestSupport.FakeProviderConfig.class)
public abstract class ProcessTestSupport extends PostgresTestSupport {

  // Test prices only, not real provider prices
  private static final String MODEL_INSERT =
      "INSERT INTO ai_model (provider_id, model_name, display_name, supports_web_search,"
          + " supports_structured_output, input_price_per_mtok, output_price_per_mtok,"
          + " price_per_search_call)"
          + " SELECT id, ?, ?, true, true, 3, 15, 0.01 FROM ai_provider WHERE code = 'FAKE'"
          + " RETURNING id";

  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected HistoryService historyService;
  @Autowired protected FakeNewsProvider fakeProvider;

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeProviderConfig {

    @Bean
    FakeNewsProvider fakeNewsProvider() {
      return new FakeNewsProvider();
    }
  }

  /** Unique suffix for names in one test. */
  protected static String unique() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  protected void ensureFakeProvider() {
    jdbc.update(
        "INSERT INTO ai_provider (code, display_name, secret_name)"
            + " VALUES ('FAKE', 'Fake', 'newsdigest/test/ai/fake') ON CONFLICT (code) DO NOTHING");
  }

  /** Creates a model of the fake provider; the name prefix selects its behaviour. */
  protected long model(String name) {
    ensureFakeProvider();
    return jdbc.queryForObject(MODEL_INSERT, Long.class, name, name);
  }

  /** Creates an active topic with the default schedule, aggregator and research models. */
  protected long topic(String name, long aggregatorModelId, long... researchModelIds) {
    long topicId =
        jdbc.queryForObject(
            "INSERT INTO topic (name, schedule_id, aggregator_model_id)"
                + " SELECT ?, min(id), ? FROM schedule RETURNING id",
            Long.class,
            name,
            aggregatorModelId);
    for (long modelId : researchModelIds) {
      jdbc.update("INSERT INTO topic_model (topic_id, model_id) VALUES (?, ?)", topicId, modelId);
    }
    return topicId;
  }

  protected HistoricProcessInstance awaitEnd(String processInstanceId) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
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
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    throw new AssertionError("Process instance " + processInstanceId + " did not end in time");
  }

  protected String runStatus(String processInstanceId) {
    return jdbc.queryForObject(
        "SELECT status FROM digest_run WHERE process_instance_id = ?",
        String.class,
        processInstanceId);
  }

  protected long runId(String processInstanceId) {
    return jdbc.queryForObject(
        "SELECT id FROM digest_run WHERE process_instance_id = ?", Long.class, processInstanceId);
  }
}
