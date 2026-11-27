package de.fuechsle.ainewsletter.research;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.domain.DigestRun;
import de.fuechsle.ainewsletter.domain.NewsItem;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import de.fuechsle.ainewsletter.domain.RunStatus;
import de.fuechsle.ainewsletter.repository.DigestRunRepository;
import de.fuechsle.ainewsletter.repository.NewsItemRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
 *   <li>{@code DUPLICATE_PREVIOUS}: normalized URL already {@code VALID} in an earlier completed
 *       run of the same topic within {@code ai.duplicate-lookback-days}
 *   <li>{@code DUPLICATE_RUN}: same normalized URL as an earlier item of the run; the first item
 *       (lowest ID) stays a candidate, the models of all copies count for the consensus
 *   <li>{@code UNREACHABLE}: the URL check of the remaining candidates fails ({@link UrlChecker})
 *   <li>otherwise {@code VALID}
 * </ol>
 *
 * <p>The check starts from scratch every time, so a repeated job leads to the same result (the URL
 * check may differ if a site was down). Each URL is requested once per run.
 */
@Service
public class NewsItemChecker {

  private static final Logger log = LoggerFactory.getLogger(NewsItemChecker.class);

  private final DigestRunRepository runs;
  private final NewsItemRepository newsItems;
  private final UrlChecker urls;
  private final int lookbackDays;

  public NewsItemChecker(
      DigestRunRepository runs,
      NewsItemRepository newsItems,
      UrlChecker urls,
      NewsDigestProperties properties) {
    this.runs = runs;
    this.newsItems = newsItems;
    this.urls = urls;
    this.lookbackDays = properties.ai().duplicateLookbackDays();
  }

  @Transactional
  public NewsCheckSummary check(long runId) {
    DigestRun run = runs.findById(runId).orElseThrow();
    List<NewsItem> items = newsItems.findByRun(runId);
    Set<String> reportedBefore = reportedBefore(run, items);

    classify(items, run.getPeriodFrom(), run.getPeriodTo(), reportedBefore);
    List<String> candidates =
        items.stream()
            .filter(item -> item.getStatus() == NewsItemStatus.VALID)
            .map(NewsItem::getNormalizedUrl)
            .toList();
    Map<String, UrlChecker.Result> results = urls.checkAll(candidates);
    results.forEach((url, result) -> {
      if (!result.reachable()) {
        log.info("Source of run {} unreachable ({})", runId, result.reason());
      }
    });
    Set<String> unreachable =
        results.entrySet().stream()
            .filter(entry -> !entry.getValue().reachable())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    markUnreachable(items, unreachable);

    NewsCheckSummary summary = NewsCheckSummary.of(items);
    log.info("News items of run {} checked: {}", runId, summary);
    return summary;
  }

  /** URLs of this run that an earlier completed run of the topic already reported. */
  private Set<String> reportedBefore(DigestRun run, List<NewsItem> items) {
    Set<String> runUrls =
        items.stream().map(NewsItem::getNormalizedUrl).collect(Collectors.toSet());
    if (runUrls.isEmpty()) {
      return Set.of();
    }
    Instant since = run.getStartedAt().minus(Duration.ofDays(lookbackDays));
    return newsItems.findReportedUrls(
        run.getTopic().getId(),
        run.getId(),
        RunStatus.COMPLETED,
        NewsItemStatus.VALID,
        since,
        runUrls);
  }

  /**
   * Sets period and duplicate status of each item; the items must be in the order they were stored.
   * Every item that passes is {@code VALID} until the URL check.
   *
   * @param from first day of the period (inclusive)
   * @param to last day of the period (inclusive)
   * @param reportedBefore normalized URLs of earlier runs of the topic
   */
  static void classify(
      List<NewsItem> items, LocalDate from, LocalDate to, Set<String> reportedBefore) {
    Set<String> seenUrls = new HashSet<>();
    for (NewsItem item : items) {
      LocalDate published = item.getPublishedAt();
      if (published != null && (published.isBefore(from) || published.isAfter(to))) {
        item.setStatus(NewsItemStatus.OUT_OF_PERIOD);
      } else if (reportedBefore.contains(item.getNormalizedUrl())) {
        item.setStatus(NewsItemStatus.DUPLICATE_PREVIOUS);
      } else if (!seenUrls.add(item.getNormalizedUrl())) {
        item.setStatus(NewsItemStatus.DUPLICATE_RUN);
      } else {
        item.setStatus(NewsItemStatus.VALID);
      }
    }
  }

  /** Marks the remaining candidates whose URL did not pass the check. */
  static void markUnreachable(List<NewsItem> items, Set<String> unreachableUrls) {
    for (NewsItem item : items) {
      if (item.getStatus() == NewsItemStatus.VALID
          && unreachableUrls.contains(item.getNormalizedUrl())) {
        item.setStatus(NewsItemStatus.UNREACHABLE);
      }
    }
  }

  /** Number of items per status after the check. */
  public record NewsCheckSummary(
      int valid, int outOfPeriod, int duplicatePrevious, int duplicateRun, int unreachable) {

    static NewsCheckSummary of(List<NewsItem> items) {
      Map<NewsItemStatus, Long> counts =
          items.stream().collect(Collectors.groupingBy(NewsItem::getStatus, Collectors.counting()));
      return new NewsCheckSummary(
          count(counts, NewsItemStatus.VALID),
          count(counts, NewsItemStatus.OUT_OF_PERIOD),
          count(counts, NewsItemStatus.DUPLICATE_PREVIOUS),
          count(counts, NewsItemStatus.DUPLICATE_RUN),
          count(counts, NewsItemStatus.UNREACHABLE));
    }

    private static int count(Map<NewsItemStatus, Long> counts, NewsItemStatus status) {
      return counts.getOrDefault(status, 0L).intValue();
    }
  }
}
