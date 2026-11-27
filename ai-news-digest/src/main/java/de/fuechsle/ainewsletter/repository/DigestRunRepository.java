package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.RunStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DigestRunRepository extends JpaRepository<DigestRun, Long> {

  boolean existsByTopicIdAndStatus(Long topicId, RunStatus status);

  /** Whether a run of the topic started at or after the given time (scheduler catch-up). */
  boolean existsByTopicIdAndStartedAtGreaterThanEqual(Long topicId, Instant startedAt);

  /** Runs with the status that started before the given time, oldest first. */
  List<DigestRun> findByStatusAndStartedAtBeforeOrderByStartedAt(RunStatus status, Instant before);

  Optional<DigestRun> findByProcessInstanceId(String processInstanceId);

  /** Run locked for update, so a cancellation and the end of the process do not overlap. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from DigestRun r where r.id = :id")
  Optional<DigestRun> findForUpdate(@Param("id") Long id);

  /** Run with topic and schedule in one query. */
  @Query("select r from DigestRun r join fetch r.topic t join fetch t.schedule where r.id = :id")
  Optional<DigestRun> findWithTopic(@Param("id") Long id);

  /** Latest run of the topic with the given status, without the given run. */
  Optional<DigestRun> findFirstByTopicIdAndStatusAndIdNotOrderByStartedAtDesc(
      Long topicId, RunStatus status, Long id);
}
