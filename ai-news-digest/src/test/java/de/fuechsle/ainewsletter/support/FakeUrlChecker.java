package de.fuechsle.ainewsletter.support;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.research.UrlChecker;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * URL check for process tests without any HTTP request (golden rule 10): a URL whose host starts
 * with {@code unreachable.} answers 404, every other URL is reachable.
 */
public class FakeUrlChecker extends UrlChecker {

  private final AtomicInteger checkedUrls = new AtomicInteger();

  public FakeUrlChecker(NewsDigestProperties properties) {
    super(properties);
  }

  @Override
  public Map<String, Result> checkAll(Collection<String> urls) {
    Map<String, Result> results = new LinkedHashMap<>();
    urls.stream().distinct().forEach(url -> results.put(url, check(url)));
    return results;
  }

  @Override
  public Result check(String url) {
    checkedUrls.incrementAndGet();
    return url.contains("://unreachable.")
        ? new Result(false, "http 404")
        : new Result(true, "http 200");
  }

  /** Number of URLs checked since the start of the test context. */
  public int checkedUrls() {
    return checkedUrls.get();
  }
}
