package de.fuechsle.ainewsletter.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

class BpmScriptMigrationTest {

  private final V3__bpm_create_engine_3_0 v3 = new V3__bpm_create_engine_3_0();

  @Test
  void allOfficialScriptsOfV3ArePresentAndCreateTablesWithoutSchemaPrefix() throws Exception {
    assertThat(v3.scripts()).hasSize(8);
    for (String script : v3.scripts()) {
      try (InputStream in = getClass().getClassLoader().getResourceAsStream(v3.location(script))) {
        assertThat(in).as(script).isNotNull();
        String sql = new String(in.readAllBytes());
        // The migration relies on search_path; a qualified name would bypass the schema "bpm"
        assertThat(sql).as(script).doesNotContainIgnoringCase("create table bpm.");
      }
    }
  }

  @Test
  void checksumIsStableAndDependsOnTheScripts() {
    assertThat(v3.getChecksum()).isEqualTo(new V3__bpm_create_engine_3_0().getChecksum());
    assertThat(v3.getChecksum()).isNotEqualTo(new V902__only_engine_script().getChecksum());
  }

  @Test
  void versionAndDescriptionComeFromTheClassName() {
    assertThat(v3.getVersion().getVersion()).isEqualTo("3");
    assertThat(v3.getDescription()).isEqualTo("bpm create engine 3 0");
  }

  @Test
  void missingScriptFailsWithAClearMessage() {
    BpmScriptMigration broken = new V901__missing_script();

    assertThatThrownBy(broken::getChecksum)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("db/migration/bpm/does-not-exist/x.sql");
  }

  @Test
  void appUserPlaceholderMustBeAPlainIdentifier() {
    assertThatThrownBy(() -> v3.migrate(contextWithAppUser("app; DROP SCHEMA app")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid database user name");
    assertThatThrownBy(() -> v3.migrate(contextWithAppUser(null)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("appUser");
  }

  // Flyway derives version and description from the class name, so test migrations need one.
  static class V901__missing_script extends BpmScriptMigration {
    V901__missing_script() {
      super("db/migration/bpm/does-not-exist", List.of("x.sql"));
    }
  }

  static class V902__only_engine_script extends BpmScriptMigration {
    V902__only_engine_script() {
      super("db/migration/bpm/cibseven-3.0.0", List.of("activiti.postgres.create.engine.sql"));
    }
  }

  private static Context contextWithAppUser(String appUser) {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getPlaceholders())
        .thenReturn(appUser == null ? Map.of() : Map.of("appUser", appUser));
    Context context = mock(Context.class);
    when(context.getConfiguration()).thenReturn(configuration);
    return context;
  }
}
