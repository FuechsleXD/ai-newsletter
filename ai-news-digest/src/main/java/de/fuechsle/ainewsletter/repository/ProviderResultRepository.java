package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.ProviderResult;
import de.fuechsle.ainewsletter.domain.ProviderResultStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderResultRepository extends JpaRepository<ProviderResult, Long> {

  Optional<ProviderResult> findByDigestRunIdAndModelId(Long digestRunId, Long modelId);

  boolean existsByDigestRunIdAndStatus(Long digestRunId, ProviderResultStatus status);
}
