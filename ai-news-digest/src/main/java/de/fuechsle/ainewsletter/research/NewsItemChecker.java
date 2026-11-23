package de.fuechsle.ainewsletter.research;

import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.NewsItem;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.NewsItemRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the news items of a run after all research has ended (docs/02-architektur.md, "Prüfung der
 * Meldungen"), in this order:
 *
 * <ol>
 *   <li>{@code OUT_OF_PERIOD}: publication date outside the run period (unknown date is allowed)
 *   <li>{@code DUPLICATE_RUN}: same normalized URL as an earlier item of the run; the first item
 *       (lowest ID) stays {@code VALID}, the models of all copies count for the consensus
 *   <li>otherwise {@code VALID}
 * </ol>
 *
 * <p>Duplicates of earlier runs and the URL check follow in roadmap step 14. The check starts from
 * scratch every time, so a repeated job leads to the same result.
 */
@Service
public class NewsItemChecker {

  private static final Logger log = LoggerFactory.getLogger(NewsItemChecker.class);

  private final DigestRunRepository runs;
  private final NewsItemRepository newsItems;

  public NewsItemChecker(DigestRunRepository runs, NewsItemRepository newsItems) {
    this.runs = runs;
    this.newsItems = newsItems;
  }

  @Transactional
  public NewsCheckSummary check(long runId) {
    DigestRun run = runs.findById(runId).orElseThrow();
    NewsCheckSummary summary =
        apply(newsItems.findByRun(runId), run.getPeriodFrom(), run.getPeriodTo());
    log.info("News items of run {} checked: {}", runId, summary);
    return summary;
  }

  /**
   * Sets the status of each item; the items must be in the order they were stored.
   *
   * @param from first day of the period (inclusive)
   * @param to last day of the period (inclusive)
   */
  static NewsCheckSummary apply(List<NewsItem> items, LocalDate from, LocalDate to) {
    Set<String> seenUrls = new HashSet<>();
    int valid = 0;
    int outOfPeriod = 0;
    int duplicateRun = 0;
    for (NewsItem item : items) {
      LocalDate published = item.getPublishedAt();
      if (published != null && (published.isBefore(from) || published.isAfter(to))) {
        item.setStatus(NewsItemStatus.OUT_OF_PERIOD);
        outOfPeriod++;
      } else if (!seenUrls.add(item.getNormalizedUrl())) {
        item.setStatus(NewsItemStatus.DUPLICATE_RUN);
        duplicateRun++;
      } else {
        item.setStatus(NewsItemStatus.VALID);
        valid++;
      }
    }
    return new NewsCheckSummary(valid, outOfPeriod, duplicateRun);
  }

  /** Number of items per status after the check. */
  public record NewsCheckSummary(int valid, int outOfPeriod, int duplicateRun) {}
}
