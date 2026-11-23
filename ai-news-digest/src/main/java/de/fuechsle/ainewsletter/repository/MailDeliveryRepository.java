package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.MailDelivery;
import de.fuechsle.ainewsletter.domain.MailDeliveryStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailDeliveryRepository extends JpaRepository<MailDelivery, Long> {

  Optional<MailDelivery> findByDigestRunIdAndRecipientId(Long digestRunId, Long recipientId);

  long countByDigestRunIdAndStatus(Long digestRunId, MailDeliveryStatus status);
}
