package de.fuechsle.ainewsletter.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for tests against a real PostgreSQL (Testcontainers). Uses the same image and the same
 * init script as docker-compose.yml, so the app runs as {@code newsdigest_app} and Flyway as {@code
 * newsdigest_migration}, exactly like locally and in production.
 *
 * <p>The container is started once per JVM and shared by all subclasses.
 */
public abstract class PostgresTestSupport {

  public static final String APP_USER = "newsdigest_app";
  public static final String APP_PASSWORD = "app-test-password";
  public static final String MIGRATION_USER = "newsdigest_migration";
  public static final String MIGRATION_PASSWORD = "migration-test-password";

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
  }
}
