package de.fuechsle.ainewsletter.migration;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Creates the CIB seven 3.0.0 tables in the schema {@code bpm} from the official create scripts.
 *
 * <p>Registered as a Spring bean; Spring Boot hands all {@code JavaMigration} beans to Flyway. The
 * class name defines the Flyway version and description.
 */
@Component
public class V3__bpm_create_engine_3_0 extends BpmScriptMigration {

  public V3__bpm_create_engine_3_0() {
    super(
        "db/migration/bpm/cibseven-3.0.0",
        List.of(
            "activiti.postgres.create.engine.sql",
            "activiti.postgres.create.history.sql",
            "activiti.postgres.create.identity.sql",
            "activiti.postgres.create.case.engine.sql",
            "activiti.postgres.create.case.history.sql",
            "activiti.postgres.create.decision.engine.sql",
            "activiti.postgres.create.decision.history.sql",
            "activiti.postgres.create.extensions.sql"));
  }
}
