package de.fuechsle.ainewsletter.eval;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.nio.file.Path;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * <p>The evaluation runs in its own thread: the CIB seven job executor is only started once the
 * application is ready, which is after all {@link ApplicationRunner}s returned. Waiting for the run
 * inside {@link #run} would keep the job executor from ever picking up the research jobs.
 */
@Profile("eval")
@Component
public class EvalRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

  static final String THREAD_NAME = "eval";

  private final EvalService evalService;
  private final NewsDigestProperties.Eval eval;
  private final ConfigurableApplicationContext context;
  private final IntConsumer exit;

  @Autowired
  public EvalRunner(
      EvalService evalService,
      NewsDigestProperties properties,
      ConfigurableApplicationContext context) {
    this(evalService, properties, context, System::exit);
  }

  EvalRunner(
      EvalService evalService,
      NewsDigestProperties properties,
      ConfigurableApplicationContext context,
      IntConsumer exit) {
    this.evalService = evalService;
    this.eval = properties.eval();
    this.context = context;
    this.exit = exit;
  }

  @Override
  public void run(ApplicationArguments args) {
    Thread.ofPlatform().name(THREAD_NAME).start(this::evaluateAndExit);
  }

  void evaluateAndExit() {
    int exitCode = 0;
    try {
      evalService.run(eval.topics(), Path.of(eval.outputDirectory()), eval.runTimeout());
    } catch (RuntimeException e) {
      log.error("Eval failed", e);
      exitCode = 1;
    }
    int code = exitCode;
    exit.accept(SpringApplication.exit(context, () -> code));
  }
}
