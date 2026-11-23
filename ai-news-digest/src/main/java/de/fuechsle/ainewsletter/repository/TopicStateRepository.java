package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.TopicState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicStateRepository extends JpaRepository<TopicState, Long> {}
