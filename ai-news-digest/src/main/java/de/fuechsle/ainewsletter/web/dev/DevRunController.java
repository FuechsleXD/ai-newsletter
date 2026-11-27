package de.fuechsle.ainewsletter.web.dev;

import de.fuechsle.ainewsletter.process.DigestProcessStarter;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.service.RunCancellationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual start and cancellation of a run for local development (roadmap steps 9 and 16), only in
 * profile {@code dev} (docs/06-security.md). The real endpoints with roles, rate limit and budget
 * check follow in roadmap steps 19 and 20.
 */
@Profile("dev")
@RestController
@RequestMapping("/dev")
public class DevRunController {

  private final DigestProcessStarter starter;
  private final DigestRunRepository runs;
  private final RunCancellationService cancellation;

  public DevRunController(
      DigestProcessStarter starter, DigestRunRepository runs, RunCancellationService cancellation) {
    this.starter = starter;
    this.runs = runs;
    this.cancellation = cancellation;
  }

  @PostMapping("/topics/{topicId}/runs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DevRunResponse start(@PathVariable long topicId) {
    String processInstanceId = starter.start(topicId);
    long runId = runs.findByProcessInstanceId(processInstanceId).orElseThrow().getId();
    return new DevRunResponse(runId, processInstanceId);
  }

  @PostMapping("/runs/{runId}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable long runId) {
    cancellation.cancel(runId, "Manuell abgebrochen (dev)");
  }
}
