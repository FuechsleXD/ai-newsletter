package de.fuechsle.ainewsletter.eval;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.nio.file.Path;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Starts the evaluation when the app runs with profile {@code eval} (manually only, never in the
 * CI) and stops the app afterwards: {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=eval}.
 * Topics come from {@code newsdigest.eval.topics}.
 */
@Profile("eval")
@Component
public class EvalRunner implements ApplicationRunner {

  private final EvalService evalService;
  private final NewsDigestProperties.Eval eval;
  private final ConfigurableApplicationContext context;

  public EvalRunner(
      EvalService evalService,
      NewsDigestProperties properties,
      ConfigurableApplicationContext context) {
    this.evalService = evalService;
    this.eval = properties.eval();
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    evalService.run(eval.topics(), Path.of(eval.outputDirectory()), eval.runTimeout());
    System.exit(SpringApplication.exit(context, () -> 0));
  }
}
