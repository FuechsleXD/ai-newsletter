package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.AiProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {

  Optional<AiProvider> findByCode(String code);
}
