package de.fuechsle.ainewsletter.process;

import de.fuechsle.ainewsletter.domain.RunStatus;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.TopicRepository;
import de.fuechsle.ainewsletter.service.RunAlreadyActiveException;
import de.fuechsle.ainewsletter.service.TopicNotFoundException;
import java.util.Map;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way to start a digest run (scheduler and manual run). At most one run per topic may be
 * active: checked here and additionally enforced by the partial unique index on {@code digest_run},
 * because "Lauf vorbereiten" runs in this transaction.
 */
@Service
public class DigestProcessStarter {

  private static final Logger log = LoggerFactory.getLogger(DigestProcessStarter.class);

  private final RuntimeService runtime;
  private final TopicRepository topics;
  private final DigestRunRepository runs;

  public DigestProcessStarter(
      RuntimeService runtime, TopicRepository topics, DigestRunRepository runs) {
    this.runtime = runtime;
    this.topics = topics;
    this.runs = runs;
  }

  /**
   * Starts a run of the topic.
   *
   * @return ID of the process instance
   * @throws TopicNotFoundException if the topic does not exist
   * @throws RunAlreadyActiveException if a run of the topic is still running
   */
  @Transactional
  public String start(long topicId) {
    if (!topics.existsById(topicId)) {
      throw new TopicNotFoundException(topicId);
    }
    if (runs.existsByTopicIdAndStatus(topicId, RunStatus.RUNNING)) {
      throw new RunAlreadyActiveException(topicId);
    }
    try {
      ProcessInstance instance =
          runtime.startProcessInstanceByKey(
              DigestRunVariables.PROCESS_KEY,
              String.valueOf(topicId),
              Map.of(DigestRunVariables.TOPIC_ID, topicId));
      log.info(
          "Started process instance {} of {} for topic {}",
          instance.getId(),
          DigestRunVariables.PROCESS_KEY,
          topicId);
      return instance.getId();
    } catch (DataIntegrityViolationException e) {
      // A concurrent start created a running run between the check and the insert.
      throw new RunAlreadyActiveException(topicId);
    }
  }
}
