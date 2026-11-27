package de.fuechsle.ainewsletter.process;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real parallelism of the research (docs/04-prozess-bpmn.md, "Parallele Recherche"): the instances
 * of the multi-instance run at the same time on the job executor, at most {@code
 * max-concurrent-calls-per-provider} (default 2) per provider, and the join collects all results.
 * Only the fake provider is called.
 */
class ParallelResearchProcessTest extends ProcessTestSupport {

  @Autowired private DigestProcessStarter starter;

  @BeforeEach
  void closeGate() {
    fakeProvider.resetParallel();
  }

  @AfterEach
  void openGate() {
    fakeProvider.openParallelGate();
  }

  @Test
  void twoResearchModelsCallAtTheSameTimeAndBothResultsAreJoined() {
    String suffix = unique();
    long first = model("parallel-a-" + suffix);
    long second = model("parallel-b-" + suffix);
    long topic = topic("Parallel " + suffix, model("ok-aggregator-" + suffix), first, second);

    String instance = starter.start(topic);

    // Both calls are inside the provider before either may finish: no sequential execution
    fakeProvider.awaitParallelCalls(2);
    fakeProvider.openParallelGate();

    awaitEnd(instance);
    assertThat(runStatus(instance)).isEqualTo("COMPLETED");
    assertThat(fakeProvider.maxParallelCalls()).isEqualTo(2);
    // Instances finishing together meet at the join (OptimisticLockingException, retried by the
    // engine); both results are there exactly once
    assertThat(
            jdbc.queryForList(
                "SELECT status FROM provider_result WHERE digest_run_id = ? ORDER BY model_id",
                String.class,
                runId(instance)))
        .containsExactly("SUCCEEDED", "SUCCEEDED");
    assertThat(fakeProvider.calls("parallel-a-" + suffix)).isEqualTo(1);
    assertThat(fakeProvider.calls("parallel-b-" + suffix)).isEqualTo(1);
  }

  @Test
  void moreModelsThanSlotsWaitForAFreeSlot() throws InterruptedException {
    String suffix = unique();
    long a = model("parallel-a-" + suffix);
    long b = model("parallel-b-" + suffix);
    long c = model("parallel-c-" + suffix);
    long topic = topic("Parallel drei " + suffix, model("ok-aggregator-" + suffix), a, b, c);

    String instance = starter.start(topic);

    fakeProvider.awaitParallelCalls(2);
    // The third instance waits for a slot of the provider and does not enter the call
    Thread.sleep(500);
    assertThat(fakeProvider.maxParallelCalls()).isEqualTo(2);
    fakeProvider.openParallelGate();

    awaitEnd(instance);
    assertThat(runStatus(instance)).isEqualTo("COMPLETED");
    assertThat(fakeProvider.maxParallelCalls()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM provider_result WHERE digest_run_id = ?"
                    + " AND status = 'SUCCEEDED'",
                Integer.class,
                runId(instance)))
        .isEqualTo(3);
  }
}
