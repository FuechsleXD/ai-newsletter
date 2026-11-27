package de.fuechsle.ainewsletter.research;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Checks whether the source URLs of news items are reachable (docs/02-architektur.md, "Prüfung der
 * Meldungen", status {@code UNREACHABLE}) with the SSRF protection of docs/06-security.md:
 *
 * <ul>
 *   <li>only targets allowed by the {@link UrlCheckPolicy}; every address the host resolves to must
 *       be public, also after every redirect
 *   <li>redirects are followed by hand, at most {@code url-check.max-redirects}
 *   <li>{@code HEAD} first, {@code GET} with {@code Range: bytes=0-0} if the server does not allow
 *       {@code HEAD}; the body is never read, no cookies, own User-Agent
 * </ul>
 *
 * <p>Unreachable is a URL whose host does not resolve, that cannot be connected to, times out, is
 * blocked by the policy, has too many redirects or answers 404/410. Every other status counts as
 * reachable: many news sites block bots with 401/403/429 although the article exists.
 *
 * <p>Remaining risk: the HTTP client resolves the host again when it connects (DNS rebinding
 * between check and request). Only the status is read and the content never used, and the instance
 * metadata requires a token (IMDSv2) that a plain request cannot get.
 */
@Component
public class UrlChecker {

  static final String USER_AGENT = "ai-news-digest-urlcheck/1.0";
  private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
  private static final Set<Integer> GONE = Set.of(404, 410);

  /** Result of one check; {@code reason} is short and never contains the URL. */
  public record Result(boolean reachable, String reason) {

    static Result reachable(int status) {
      return new Result(true, "http " + status);
    }

    static Result unreachable(String reason) {
      return new Result(false, reason);
    }
  }

  /** Resolves a host name; replaceable in tests so that no real DNS query is made. */
  @FunctionalInterface
  interface Resolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  private final NewsDigestProperties.UrlCheck settings;
  private final UrlCheckPolicy policy;
  private final Resolver resolver;
  private final HttpClient client;

  @Autowired
  public UrlChecker(NewsDigestProperties properties) {
    this(properties.urlCheck(), UrlCheckPolicy.PUBLIC_ONLY, InetAddress::getAllByName);
  }

  UrlChecker(NewsDigestProperties.UrlCheck settings, UrlCheckPolicy policy, Resolver resolver) {
    this.settings = settings;
    this.policy = policy;
    this.resolver = resolver;
    this.client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(settings.timeout())
            .build();
  }

  /**
   * Checks every URL once, at most {@code url-check.max-concurrent-checks} at the same time.
   *
   * @return result per URL, in the order of the input
   */
  public Map<String, Result> checkAll(Collection<String> urls) {
    List<String> distinct = urls.stream().distinct().toList();
    Semaphore slots = new Semaphore(settings.maxConcurrentChecks());
    Map<String, Future<Result>> futures = new LinkedHashMap<>();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (String url : distinct) {
        futures.put(url, executor.submit(() -> inSlot(slots, url)));
      }
      Map<String, Result> results = new LinkedHashMap<>();
      for (Map.Entry<String, Future<Result>> entry : futures.entrySet()) {
        results.put(entry.getKey(), get(entry.getValue()));
      }
      return results;
    }
  }

  private Result inSlot(Semaphore slots, String url) throws InterruptedException {
    slots.acquire();
    try {
      return check(url);
    } finally {
      slots.release();
    }
  }

  private static Result get(Future<Result> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.unreachable("interrupted");
    } catch (ExecutionException e) {
      return Result.unreachable("error");
    }
  }

  /** Checks one URL. */
  public Result check(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      return Result.unreachable("invalid url");
    }
    for (int redirect = 0; redirect <= settings.maxRedirects(); redirect++) {
      Result blocked = blocked(uri);
      if (blocked != null) {
        return blocked;
      }
      HttpResponse<InputStream> response;
      try {
        response = request(uri);
      } catch (HttpTimeoutException e) {
        return Result.unreachable("timeout");
      } catch (ConnectException e) {
        return Result.unreachable("connection");
      } catch (IOException e) {
        return Result.unreachable("io");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Result.unreachable("interrupted");
      } catch (IllegalArgumentException e) {
        return Result.unreachable("invalid url");
      }
      int status = response.statusCode();
      if (!REDIRECTS.contains(status)) {
        return GONE.contains(status)
            ? Result.unreachable("http " + status)
            : Result.reachable(status);
      }
      String location = response.headers().firstValue("Location").orElse(null);
      if (location == null) {
        return Result.reachable(status);
      }
      try {
        uri = uri.resolve(location);
      } catch (IllegalArgumentException e) {
        return Result.unreachable("invalid redirect");
      }
    }
    return Result.unreachable("too many redirects");
  }

  /** Reason why the target must not be requested, {@code null} if it is allowed. */
  private Result blocked(URI uri) {
    String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost();
    if (host == null || !policy.allowsTarget(scheme, uri.getPort())) {
      return Result.unreachable("blocked target");
    }
    InetAddress[] addresses;
    try {
      addresses = resolver.resolve(host);
    } catch (UnknownHostException e) {
      return Result.unreachable("dns");
    }
    for (InetAddress address : addresses) {
      if (!policy.allowsAddress(address)) {
        return Result.unreachable("blocked address");
      }
    }
    return null;
  }

  private HttpResponse<InputStream> request(URI uri) throws IOException, InterruptedException {
    HttpResponse<InputStream> head = send(uri, "HEAD", HttpRequest.BodyPublishers.noBody());
    if (head.statusCode() != 405 && head.statusCode() != 501) {
      return head;
    }
    return send(uri, "GET", HttpRequest.BodyPublishers.noBody());
  }

  private HttpResponse<InputStream> send(URI uri, String method, HttpRequest.BodyPublisher body)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri)
            .timeout(settings.timeout())
            .header("User-Agent", USER_AGENT)
            .method(method, body);
    if ("GET".equals(method)) {
      request.header("Range", "bytes=0-0");
    }
    HttpResponse<InputStream> response =
        client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    // Only the status matters: close the body without reading it
    response.body().close();
    return response;
  }
}
