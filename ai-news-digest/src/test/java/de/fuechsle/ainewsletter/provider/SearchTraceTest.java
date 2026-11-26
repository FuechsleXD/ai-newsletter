package de.fuechsle.ainewsletter.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTraceTest {

  private static final SearchTrace TRACE =
      new SearchTrace(
          List.of(
              new SearchTrace.Search(
                  "Spring AI November 2026",
                  List.of(
                      new SearchTrace.Hit("A", "https://a.example.test/1", "2 days ago"),
                      new SearchTrace.Hit("B", "https://b.example.test/2", null)),
                  null),
              new SearchTrace.Search("Copilot", List.of(), "max_uses_exceeded")),
          3);

  @Test
  void jsonRoundTripKeepsQueriesHitsAndErrors() {
    String json = TRACE.toJson();

    assertThat(json).contains("Spring AI November 2026", "max_uses_exceeded");
    assertThat(SearchTrace.fromJson(json)).isEqualTo(TRACE);
  }

  @Test
  void noSearchesMeansNoJsonAndBack() {
    assertThat(SearchTrace.EMPTY.toJson()).isNull();
    assertThat(SearchTrace.fromJson(null)).isEqualTo(SearchTrace.EMPTY);
    assertThat(SearchTrace.fromJson(" ")).isEqualTo(SearchTrace.EMPTY);
  }

  @Test
  void onlyOtherToolCallsAreStoredAndOlderJsonWithoutThemIsRead() {
    SearchTrace filtering = new SearchTrace(List.of(), 2);

    assertThat(filtering.isEmpty()).isFalse();
    assertThat(SearchTrace.fromJson(filtering.toJson())).isEqualTo(filtering);
    assertThat(SearchTrace.fromJson("{\"searches\":[]}")).isEqualTo(SearchTrace.EMPTY);
  }

  @Test
  void tracesOfSeveralCallsAreJoinedInOrder() {
    SearchTrace repair = SearchTrace.EMPTY;
    SearchTrace second =
        new SearchTrace(List.of(new SearchTrace.Search("second", List.of(), null)));

    SearchTrace joined = TRACE.plus(repair).plus(second);

    assertThat(joined.searches())
        .extracting(SearchTrace.Search::query)
        .containsExactly("Spring AI November 2026", "Copilot", "second");
    assertThat(joined.hitCount()).isEqualTo(2);
    assertThat(joined.otherToolCalls()).isEqualTo(3);
  }

  @Test
  void responseWithoutTraceHasAnEmptyOne() {
    ModelResponse response = new ModelResponse("{}", 1, 1, 0, "END_TURN", java.time.Duration.ZERO);

    assertThat(response.searchTrace()).isEqualTo(SearchTrace.EMPTY);
  }
}
