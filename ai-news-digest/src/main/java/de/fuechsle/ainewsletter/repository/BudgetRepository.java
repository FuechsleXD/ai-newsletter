package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.Budget;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

  /** The active budget; at most one exists (partial unique index). */
  Optional<Budget> findByActiveTrue();
}
