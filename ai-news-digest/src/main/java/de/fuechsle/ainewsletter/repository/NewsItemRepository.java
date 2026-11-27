package de.fuechsle.ainewsletter.repository;

import de.fuechsle.ainewsletter.domain.NewsItem;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import de.fuechsle.ainewsletter.domain.RunStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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

  /**
   * Which of the given normalized URLs an earlier run of the topic already reported: items with the
   * given status in runs with the given status that started since the given time, without the run
   * itself.
   */
  @Query(
      "select distinct n.normalizedUrl from NewsItem n join n.providerResult p join p.digestRun r"
          + " where r.topic.id = :topicId and r.id <> :runId and r.status = :runStatus"
          + " and r.startedAt >= :since and n.status = :itemStatus and n.normalizedUrl in :urls")
  Set<String> findReportedUrls(
      @Param("topicId") Long topicId,
      @Param("runId") Long runId,
      @Param("runStatus") RunStatus runStatus,
      @Param("itemStatus") NewsItemStatus itemStatus,
      @Param("since") Instant since,
      @Param("urls") Collection<String> urls);
}
