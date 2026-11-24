package de.fuechsle.ainewsletter.web.dev;

import de.fuechsle.ainewsletter.process.DigestProcessStarter;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual start of a run for local development (roadmap step 9), only in profile {@code dev}
 * (docs/06-security.md). The real endpoint {@code POST /api/v1/topics/{id}/runs} with roles, rate
 * limit and budget check follows in roadmap steps 19 and 20.
 */
@Profile("dev")
@RestController
@RequestMapping("/dev")
public class DevRunController {

  private final DigestProcessStarter starter;
  private final DigestRunRepository runs;

  public DevRunController(DigestProcessStarter starter, DigestRunRepository runs) {
    this.starter = starter;
    this.runs = runs;
  }

  @PostMapping("/topics/{topicId}/runs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DevRunResponse start(@PathVariable long topicId) {
    String processInstanceId = starter.start(topicId);
    long runId = runs.findByProcessInstanceId(processInstanceId).orElseThrow().getId();
    return new DevRunResponse(runId, processInstanceId);
  }
}
