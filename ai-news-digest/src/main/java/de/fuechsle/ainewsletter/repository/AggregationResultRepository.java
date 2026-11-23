package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.AggregationResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** Usage and cost of the aggregator call, keyed by the run ID. */
public interface AggregationResultRepository extends JpaRepository<AggregationResult, Long> {}
