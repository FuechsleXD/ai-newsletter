package de.fuechsle.ainewsletter.research;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.domain.NewsItem;
import de.fuechsle.ainewsletter.domain.NewsItemStatus;
import de.fuechsle.ainewsletter.research.NewsItemChecker.NewsCheckSummary;
import java.time.LocalDate;
import java.util.List;
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

    NewsCheckSummary summary =
        NewsItemChecker.apply(List.of(before, first, last, after, unknown), FROM, TO);

    assertThat(List.of(before, first, last, after, unknown))
        .extracting(NewsItem::getStatus)
        .containsExactly(
            NewsItemStatus.OUT_OF_PERIOD,
            NewsItemStatus.VALID,
            NewsItemStatus.VALID,
            NewsItemStatus.OUT_OF_PERIOD,
            NewsItemStatus.VALID);
    assertThat(summary).isEqualTo(new NewsCheckSummary(3, 2, 0));
  }

  @Test
  void firstItemOfAUrlStaysValidAndLaterCopiesAreDuplicates() {
    NewsItem first = item("https://a.test/x", null);
    NewsItem other = item("https://a.test/y", null);
    NewsItem copy = item("https://a.test/x", TO);

    NewsCheckSummary summary = NewsItemChecker.apply(List.of(first, other, copy), FROM, TO);

    assertThat(copy.getStatus()).isEqualTo(NewsItemStatus.DUPLICATE_RUN);
    assertThat(first.getStatus()).isEqualTo(NewsItemStatus.VALID);
    assertThat(summary).isEqualTo(new NewsCheckSummary(2, 0, 1));
  }

  @Test
  void itemOutOfPeriodDoesNotBlockAValidCopyOfTheSameUrl() {
    NewsItem old = item("https://a.test/x", FROM.minusYears(1));
    NewsItem current = item("https://a.test/x", TO);

    NewsItemChecker.apply(List.of(old, current), FROM, TO);

    assertThat(old.getStatus()).isEqualTo(NewsItemStatus.OUT_OF_PERIOD);
    assertThat(current.getStatus()).isEqualTo(NewsItemStatus.VALID);
  }

  @Test
  void repeatedCheckStartsFromScratch() {
    NewsItem item = item("https://a.test/x", TO);
    item.setStatus(NewsItemStatus.DUPLICATE_RUN);

    NewsItemChecker.apply(List.of(item), FROM, TO);

    assertThat(item.getStatus()).isEqualTo(NewsItemStatus.VALID);
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
