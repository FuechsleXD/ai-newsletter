package de.fuechsle.ainewsletter.support;

import de.fuechsle.ainewsletter.provider.ModelInvocation;
import de.fuechsle.ainewsletter.provider.ModelResponse;
import de.fuechsle.ainewsletter.provider.NewsProvider;
import de.fuechsle.ainewsletter.provider.ProviderException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provider {@code FAKE} for process tests, never calls a real API (golden rule 10). The behaviour
 * is chosen by the prefix of the model name.
 *
 * <p>Research calls (all calls except the aggregation):
 *
 * <ul>
 *   <li>{@code ok-}: two valid news items (dated today in Berlin and without date); every {@code
 *       ok-} model reports the same URLs
 *   <li>{@code old-}: one item outside every run period and one valid item
 *   <li>{@code empty-}: an empty list
 *   <li>{@code fail-}: provider error
 *   <li>{@code slow-}: waits until {@link #release()} is called, then two valid items
 * </ul>
 *
 * <p>Aggregation calls (prompt with the data block of the aggregation): {@code fail-} is a provider
 * error, {@code garbage-} answers without JSON (also in the repair attempt), every other model
 * answers a valid aggregate with one section per news item ID found in the prompt.
 */
public class FakeNewsProvider implements NewsProvider {

  public static final String CODE = "FAKE";

  /**
   * Data block of the aggregation prompt: {@code <NEWS>} from version v3, before {@code
   * <MELDUNGEN>}.
   */
  private static final List<String> AGGREGATE_MARKERS = List.of("<NEWS>", "<MELDUNGEN>");

  private static final Pattern ITEM_ID = Pattern.compile("\"id\":\"(\\d+)\"");

  private final Map<String, AtomicInteger> researchCalls = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> aggregationCalls = new ConcurrentHashMap<>();
  private volatile CountDownLatch slowEntered = new CountDownLatch(1);
  private volatile CountDownLatch slowRelease = new CountDownLatch(1);

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public ModelResponse call(ModelInvocation invocation) {
    String model = invocation.modelName();
    if (model.startsWith("fail-")) {
      count(isAggregation(invocation) ? aggregationCalls : researchCalls, model);
      throw new ProviderException("Fake provider error for " + model);
    }
    if (isAggregation(invocation) || model.startsWith("garbage-")) {
      count(aggregationCalls, model);
      return aggregate(model, invocation.prompt());
    }
    count(researchCalls, model);
    return research(model);
  }

  /** Number of research calls of a model. */
  public int calls(String modelName) {
    return value(researchCalls, modelName);
  }

  /** Number of aggregation calls of a model, including repair attempts. */
  public int aggregations(String modelName) {
    return value(aggregationCalls, modelName);
  }

  /** Waits until a {@code slow-} model has been called. */
  public void awaitSlowCall() {
    await(slowEntered);
  }

  /** Lets waiting {@code slow-} models continue and arms the latches again. */
  public void release() {
    slowRelease.countDown();
    slowEntered = new CountDownLatch(1);
    slowRelease = new CountDownLatch(1);
  }

  private ModelResponse research(String model) {
    if (model.startsWith("empty-")) {
      return response("{\"items\": []}", 1);
    }
    if (model.startsWith("slow-")) {
      slowEntered.countDown();
      await(slowRelease);
    }
    String today = LocalDate.now(ZoneId.of("Europe/Berlin")).toString();
    String first =
        model.startsWith("old-")
            ? item("Alte Meldung", "https://old.example.test/archive/2000", "\"2000-01-01\"")
            : item(
                "Spring Boot veröffentlicht", "https://spring.example.test/blog/release", q(today));
    String second = item("Neue Java-Preview", "https://jdk.example.test/ea/", "null");
    return response("Hier sind die Meldungen:\n{\"items\": [" + first + "," + second + "]}", 1);
  }

  private static ModelResponse aggregate(String model, String prompt) {
    if (model.startsWith("garbage-")) {
      return response("Leider kann ich das nicht als JSON liefern.", 0);
    }
    List<String> ids = new ArrayList<>();
    Matcher matcher = ITEM_ID.matcher(prompt);
    while (matcher.find()) {
      ids.add(matcher.group(1));
    }
    String sections =
        ids.stream()
            .map(id ->
                "{\"heading\":\"Thema "
                    + id
                    + "\",\"iconKey\":\"unknown-icon\",\"text\":\"Text zu "
                    + id
                    + ".\",\"flowSteps\":[],\"highlightNumber\":null,\"sourceIds\":[\""
                    + id
                    + "\"],\"models\":[\"erfunden\"]}")
            .collect(Collectors.joining(","));
    return response(
        "{\"title\":\"Fake-Digest\",\"subtitle\":\"Test\",\"summary\":\"Kurzfazit.\","
            + "\"keyPoints\":[{\"iconKey\":\"ai\",\"text\":\"Punkt\"}],"
            + "\"sections\":["
            + sections
            + "],\"takeaways\":[\"Merken\"],\"watchOut\":\"Beobachten\"}",
        0);
  }

  private static boolean isAggregation(ModelInvocation invocation) {
    return AGGREGATE_MARKERS.stream().anyMatch(invocation.prompt()::contains);
  }

  private static String item(String headline, String url, String publishedAt) {
    return "{\"headline\":"
        + q(headline)
        + ",\"summary\":\"Zusammenfassung.\",\"url\":"
        + q(url)
        + ",\"publishedAt\":"
        + publishedAt
        + "}";
  }

  private static String q(String value) {
    return "\"" + value + "\"";
  }

  private static ModelResponse response(String text, int webSearches) {
    return new ModelResponse(text, 1_000, 500, webSearches, "END_TURN", Duration.ofMillis(10));
  }

  private static void count(Map<String, AtomicInteger> counts, String model) {
    counts.computeIfAbsent(model, key -> new AtomicInteger()).incrementAndGet();
  }

  private static int value(Map<String, AtomicInteger> counts, String model) {
    AtomicInteger count = counts.get(model);
    return count == null ? 0 : count.get();
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Fake provider waited too long");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
