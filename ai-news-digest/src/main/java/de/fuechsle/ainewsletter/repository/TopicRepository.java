package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.Topic;
import de.fuechsle.ainewsletter.scheduling.ScheduledTopic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

  /** Topic with schedule, aggregator model and research models (with providers) in one query. */
  @Query(
      """
      select distinct t from Topic t
        join fetch t.schedule
        join fetch t.aggregatorModel am
        join fetch am.provider
        left join fetch t.models m
        left join fetch m.provider
      where t.id = :id
      """)
  Optional<Topic> findForRun(@Param("id") Long id);

  Optional<Topic> findByName(String name);

  /** Active topics with the cron expression and time zone of their schedule. */
  @Query(
      """
      select new de.fuechsle.ainewsletter.scheduling.ScheduledTopic(
          t.id, t.name, s.cronExpression, s.timezone)
      from Topic t join t.schedule s
      where t.active = true
      order by t.id
      """)
  List<ScheduledTopic> findScheduled();
}
