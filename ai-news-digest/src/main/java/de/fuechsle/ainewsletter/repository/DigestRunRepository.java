package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.RunStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DigestRunRepository extends JpaRepository<DigestRun, Long> {

  boolean existsByTopicIdAndStatus(Long topicId, RunStatus status);

  /** Run with topic and schedule in one query. */
  @Query("select r from DigestRun r join fetch r.topic t join fetch t.schedule where r.id = :id")
  Optional<DigestRun> findWithTopic(@Param("id") Long id);
}
