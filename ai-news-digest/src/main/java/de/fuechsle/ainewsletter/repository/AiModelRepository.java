package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.AiModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {

  /** Model with its provider in one query. */
  @Query("select m from AiModel m join fetch m.provider where m.id = :id")
  Optional<AiModel> findWithProvider(@Param("id") Long id);
}
