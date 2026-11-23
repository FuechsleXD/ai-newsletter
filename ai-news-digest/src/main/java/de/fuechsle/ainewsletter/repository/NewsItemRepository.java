package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.NewsItem;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

  @Modifying
  @Query("delete from NewsItem n where n.providerResult.id = :providerResultId")
  int deleteByProviderResultId(@Param("providerResultId") Long providerResultId);

  long countByProviderResultId(Long providerResultId);

  boolean existsByProviderResultDigestRunIdAndStatus(Long digestRunId, NewsItemStatus status);
}
