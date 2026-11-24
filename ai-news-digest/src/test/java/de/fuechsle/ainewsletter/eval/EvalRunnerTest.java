package de.fuechsle.ainewsletter.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

class EvalRunnerTest {

  private final EvalService evalService = mock(EvalService.class);
  private final NewsDigestProperties properties = mock(NewsDigestProperties.class);
  private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
  private final CompletableFuture<Integer> exitCode = new CompletableFuture<>();

  private EvalRunner runner;

  @BeforeEach
  void setUp() {
    when(properties.eval())
        .thenReturn(
            new NewsDigestProperties.Eval(List.of("Java"), Duration.ofMinutes(1), "data/eval"));
    runner = new EvalRunner(evalService, properties, context, exitCode::complete);
  }

  @Test
  void doesNotBlockTheStartupSoTheJobExecutorCanStart() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<String> evalThread = new CompletableFuture<>();
    doAnswer(
            invocation -> {
              evalThread.complete(Thread.currentThread().getName());
              release.await(10, TimeUnit.SECONDS);
              return List.of();
            })
        .when(evalService)
        .run(any(), any(), any());

    // Returns while the evaluation is still waiting for its run
    runner.run(new DefaultApplicationArguments());

    assertThat(evalThread.get(10, TimeUnit.SECONDS)).isEqualTo(EvalRunner.THREAD_NAME);
    assertThat(exitCode).isNotDone();
    release.countDown();
    assertThat(exitCode.get(10, TimeUnit.SECONDS)).isZero();
  }

  @Test
  void failedEvaluationStopsTheAppWithErrorCode() throws Exception {
    doThrow(new IllegalStateException("boom")).when(evalService).run(any(), any(), any());

    runner.run(new DefaultApplicationArguments());

    assertThat(exitCode.get(10, TimeUnit.SECONDS)).isEqualTo(1);
  }
}
