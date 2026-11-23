package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.Recipient;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

  /** Active recipients of a topic, ordered by ID. */
  @Query(
      "select r from Topic t join t.recipients r where t.id = :topicId and r.active = true"
          + " order by r.id")
  List<Recipient> findActiveByTopic(@Param("topicId") Long topicId);
}
