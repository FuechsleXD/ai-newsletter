package de.fuechsle.ainewsletter.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Runs official, unmodified CIB seven SQL scripts in the schema {@code bpm} (ADR-006).
 *
 * <p>The official scripts create their tables without a schema prefix, while Flyway runs with the
 * default schema {@code app}. This base class therefore sets {@code search_path} to {@code bpm} for
 * the duration of the migration transaction ({@code SET LOCAL}) and executes the scripts in the
 * given order. The Flyway checksum is computed from the script contents, so any change to a copied
 * script is detected by {@code validate-on-migrate}.
 */
public abstract class BpmScriptMigration extends BaseJavaMigration {

  static final String SCHEMA = "bpm";

  private static final Pattern SQL_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

  private final String resourceFolder;
  private final List<String> scripts;

  /**
   * @param resourceFolder classpath folder of the copied scripts, without leading slash
   * @param scripts script file names in execution order
   */
  protected BpmScriptMigration(String resourceFolder, List<String> scripts) {
    this.resourceFolder = resourceFolder;
    this.scripts = List.copyOf(scripts);
  }

  @Override
  public void migrate(Context context) throws SQLException {
    String appUser = appUser(context);
    try (Statement statement = context.getConnection().createStatement()) {
      // Same privilege model as the schema "app": the app user gets data access only.
      statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
      statement.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + appUser);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA "
              + SCHEMA
              + " GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "
              + appUser);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA "
              + SCHEMA
              + " GRANT USAGE, SELECT ON SEQUENCES TO "
              + appUser);

      // Only for this transaction; Flyway's own search_path is restored on commit.
      statement.execute("SET LOCAL search_path TO " + SCHEMA);
      for (String script : scripts) {
        // pgjdbc executes a script with several statements in one call
        statement.execute(read(script));
      }
    }
  }

  @Override
  public Integer getChecksum() {
    CRC32 crc = new CRC32();
    for (String script : scripts) {
      crc.update(script.getBytes(StandardCharsets.UTF_8));
      crc.update(read(script).getBytes(StandardCharsets.UTF_8));
    }
    return (int) crc.getValue();
  }

  /** Classpath location of a script, used in error messages and tests. */
  String location(String script) {
    return resourceFolder + "/" + script;
  }

  List<String> scripts() {
    return scripts;
  }

  private String read(String script) {
    String location = location(script);
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(location)) {
      if (in == null) {
        throw new IllegalStateException("Missing CIB seven script on the classpath: " + location);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read CIB seven script " + location, e);
    }
  }

  private static String appUser(Context context) {
    String appUser =
        Objects.requireNonNull(
            context.getConfiguration().getPlaceholders().get("appUser"),
            "Flyway placeholder appUser is not set");
    // The name is concatenated into DDL, so only plain identifiers are accepted.
    if (!SQL_IDENTIFIER.matcher(appUser).matches()) {
      throw new IllegalStateException("Invalid database user name in placeholder appUser");
    }
    return appUser;
  }
}
