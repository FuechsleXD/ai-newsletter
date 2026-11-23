package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.NewsItem;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

  @Modifying
  @Query("delete from NewsItem n where n.providerResult.id = :providerResultId")
  int deleteByProviderResultId(@Param("providerResultId") Long providerResultId);

  long countByProviderResultId(Long providerResultId);

  /** All news items of a run with their result and model, in the order they were stored. */
  @Query(
      "select n from NewsItem n join fetch n.providerResult p join fetch p.model"
          + " where p.digestRun.id = :runId order by n.id")
  List<NewsItem> findByRun(@Param("runId") Long runId);

  boolean existsByProviderResultDigestRunIdAndStatus(Long digestRunId, NewsItemStatus status);
}
