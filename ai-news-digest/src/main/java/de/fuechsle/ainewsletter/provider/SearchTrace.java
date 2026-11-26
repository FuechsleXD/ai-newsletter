package de.fuechsle.ainewsletter.provider;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The web searches of a model call: what the model searched for and which pages came back
 * (diagnosis of the research, stored in {@code provider_result.search_trace}, exported by the
 * eval). Independent of the provider; empty if the model did not search.
 *
 * @param searches searches in the order the model ran them
 * @param otherToolCalls other server tool calls of the model that are no searches, e.g. the code
 *     execution with which the search tool filters its results; they carry no query
 */
public record SearchTrace(List<Search> searches, int otherToolCalls) {

  public static final SearchTrace EMPTY = new SearchTrace(List.of());

  // Traces stored before otherToolCalls existed have no such field
  private static final JsonMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

  public SearchTrace {
    searches = List.copyOf(searches);
  }

  public SearchTrace(List<Search> searches) {
    this(searches, 0);
  }

  /**
   * One web search.
   *
   * @param query search terms as sent by the model
   * @param hits pages returned by the search, in the order of the provider
   * @param errorCode error of the search tool (e.g. {@code max_uses_exceeded}), {@code null} if it
   *     returned results
   */
  public record Search(String query, List<Hit> hits, String errorCode) {

    public Search {
      hits = List.copyOf(hits);
    }
  }

  /**
   * A page returned by a search.
   *
   * @param pageAge age of the page as reported by the provider (e.g. "2 days ago"), may be {@code
   *     null}
   */
  public record Hit(String title, String url, String pageAge) {}

  public boolean isEmpty() {
    return searches.isEmpty() && otherToolCalls == 0;
  }

  public int hitCount() {
    return searches.stream().mapToInt(search -> search.hits().size()).sum();
  }

  /** Both traces, this one first (e.g. a research call and its repair attempt). */
  public SearchTrace plus(SearchTrace other) {
    if (other.isEmpty()) {
      return this;
    }
    List<Search> all = new ArrayList<>(searches);
    all.addAll(other.searches());
    return new SearchTrace(all, otherToolCalls + other.otherToolCalls());
  }

  /** JSON for the database column; {@code null} if there were no tool calls. */
  public String toJson() {
    return isEmpty() ? null : JSON.writeValueAsString(this);
  }

  /** Reads {@link #toJson()}; {@link #EMPTY} for {@code null} or blank. */
  public static SearchTrace fromJson(String json) {
    return json == null || json.isBlank() ? EMPTY : JSON.readValue(json, SearchTrace.class);
  }
}
