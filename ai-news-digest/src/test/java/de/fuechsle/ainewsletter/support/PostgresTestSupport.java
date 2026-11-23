package de.fuechsle.ainewsletter.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for tests against a real PostgreSQL (Testcontainers). Uses the same image and the same
 * init script as docker-compose.yml, so the app runs as {@code newsdigest_app} and Flyway as {@code
 * newsdigest_migration}, exactly like locally and in production.
 *
 * <p>The container is started once per JVM and shared by all subclasses.
 *
 * <p>The job executor of the engine is off by default: Spring keeps test contexts open, and every
 * running job executor would compete for the jobs of the process tests on the shared database. Only
 * {@link ProcessTestSupport} and the engine smoke test switch it on.
 */
// Lower precedence than @DynamicPropertySource, so subclasses can switch the job executor on
@TestPropertySource(properties = "camunda.bpm.job-execution.enabled=false")
public abstract class PostgresTestSupport {

  public static final String APP_USER = "newsdigest_app";
  public static final String APP_PASSWORD = "app-test-password";
  public static final String MIGRATION_USER = "newsdigest_migration";
  public static final String MIGRATION_PASSWORD = "migration-test-password";

  /** Test addresses only (example.test), never real ones (golden rule 9). */
  public static final String MAIL_FROM = "digest@example.test";

  public static final String MAIL_OPERATOR = "ok-operator@example.test";

  protected static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.7-alpine")
          .withDatabaseName("newsdigest")
          .withEnv("DB_MIGRATION_PASSWORD", MIGRATION_PASSWORD)
          .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
          .withCopyFileToContainer(
              MountableFile.forHostPath("docker/postgres/init/01-create-users.sh"),
              "/docker-entrypoint-initdb.d/01-create-users.sh");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> APP_USER);
    registry.add("spring.datasource.password", () -> APP_PASSWORD);
    registry.add("spring.flyway.user", () -> MIGRATION_USER);
    registry.add("spring.flyway.password", () -> MIGRATION_PASSWORD);
    registry.add("newsdigest.mail.from", () -> MAIL_FROM);
    registry.add("newsdigest.mail.operator", () -> MAIL_OPERATOR);
  }
}
