package de.fuechsle.ainewsletter.research;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.domain.NewsItem;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import de.fuechsle.ainewsletter.research.NewsItemChecker.NewsCheckSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NewsItemCheckerTest {

  private static final LocalDate FROM = LocalDate.of(2026, 11, 16);
  private static final LocalDate TO = LocalDate.of(2026, 11, 22);

  @Test
  void periodIsInclusiveAndUnknownDateIsAllowed() {
    NewsItem before = item("https://a.test/1", FROM.minusDays(1));
    NewsItem first = item("https://a.test/2", FROM);
    NewsItem last = item("https://a.test/3", TO);
    NewsItem after = item("https://a.test/4", TO.plusDays(1));
    NewsItem unknown = item("https://a.test/5", null);

    List<NewsItem> items = List.of(before, first, last, after, unknown);
    NewsItemChecker.classify(items, FROM, TO, Set.of());
    NewsCheckSummary summary = NewsCheckSummary.of(items);

    assertThat(List.of(before, first, last, after, unknown))
        .extracting(NewsItem::getStatus)
        .containsExactly(
            NewsItemStatus.OUT_OF_PERIOD,
            NewsItemStatus.VALID,
            NewsItemStatus.VALID,
            NewsItemStatus.OUT_OF_PERIOD,
            NewsItemStatus.VALID);
    assertThat(summary).isEqualTo(new NewsCheckSummary(3, 2, 0, 0, 0));
  }

  @Test
  void firstItemOfAUrlStaysValidAndLaterCopiesAreDuplicates() {
    NewsItem first = item("https://a.test/x", null);
    NewsItem other = item("https://a.test/y", null);
    NewsItem copy = item("https://a.test/x", TO);

    List<NewsItem> items = List.of(first, other, copy);
    NewsItemChecker.classify(items, FROM, TO, Set.of());
    NewsCheckSummary summary = NewsCheckSummary.of(items);

    assertThat(copy.getStatus()).isEqualTo(NewsItemStatus.DUPLICATE_RUN);
    assertThat(first.getStatus()).isEqualTo(NewsItemStatus.VALID);
    assertThat(summary).isEqualTo(new NewsCheckSummary(2, 0, 0, 1, 0));
  }

  @Test
  void itemOutOfPeriodDoesNotBlockAValidCopyOfTheSameUrl() {
    NewsItem old = item("https://a.test/x", FROM.minusYears(1));
    NewsItem current = item("https://a.test/x", TO);

    NewsItemChecker.classify(List.of(old, current), FROM, TO, Set.of());

    assertThat(old.getStatus()).isEqualTo(NewsItemStatus.OUT_OF_PERIOD);
    assertThat(current.getStatus()).isEqualTo(NewsItemStatus.VALID);
  }

  @Test
  void repeatedCheckStartsFromScratch() {
    NewsItem item = item("https://a.test/x", TO);
    item.setStatus(NewsItemStatus.DUPLICATE_RUN);

    NewsItemChecker.classify(List.of(item), FROM, TO, Set.of());

    assertThat(item.getStatus()).isEqualTo(NewsItemStatus.VALID);
  }

  @Test
  void earlierRunsComeBeforeDuplicatesInTheRunAndUnreachableComesLast() {
    NewsItem old = item("https://a.test/old", FROM.minusDays(3));
    NewsItem reported = item("https://a.test/reported", TO);
    NewsItem first = item("https://a.test/gone", null);
    NewsItem copy = item("https://a.test/gone", TO);
    NewsItem fine = item("https://a.test/fine", TO);
    List<NewsItem> items = List.of(old, reported, first, copy, fine);

    // An out-of-period item is not a duplicate of an earlier run, even if the URL was reported
    NewsItemChecker.classify(
        items, FROM, TO, Set.of("https://a.test/reported", "https://a.test/old"));
    NewsItemChecker.markUnreachable(items, Set.of("https://a.test/gone"));

    assertThat(items)
        .extracting(NewsItem::getStatus)
        .containsExactly(
            NewsItemStatus.OUT_OF_PERIOD,
            NewsItemStatus.DUPLICATE_PREVIOUS,
            NewsItemStatus.UNREACHABLE,
            NewsItemStatus.DUPLICATE_RUN,
            NewsItemStatus.VALID);
    assertThat(NewsCheckSummary.of(items)).isEqualTo(new NewsCheckSummary(1, 1, 1, 1, 1));
  }

  private static NewsItem item(String normalizedUrl, LocalDate publishedAt) {
    return new NewsItem(
        null,
        "Überschrift",
        "Text",
        normalizedUrl,
        normalizedUrl,
        publishedAt,
        NewsItemStatus.VALID);
  }
}
