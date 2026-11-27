package de.fuechsle.ainewsletter.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.support.TestProperties;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Against a local stub server on the loopback address only: no real DNS query and no external
 * request (golden rule 10). Host names resolve through a test resolver.
 */
class UrlCheckerTest {

  private static final UrlCheckPolicy STUB = new UrlCheckPolicy(true, false);

  private final List<String> requests = new CopyOnWriteArrayList<>();
  private final AtomicInteger active = new AtomicInteger();
  private final AtomicInteger maxActive = new AtomicInteger();
  private HttpServer server;
  private String base;

  @BeforeEach
  void startStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
    server.createContext("/", this::handle);
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopStub() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    requests.add(
        exchange.getRequestMethod()
            + " "
            + path
            + " "
            + exchange.getRequestHeaders().getFirst("User-Agent")
            + " "
            + exchange.getRequestHeaders().getFirst("Range"));
    maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
    try {
      switch (path) {
        case "/ok" -> respond(exchange, 200);
        case "/forbidden" -> respond(exchange, 403);
        case "/gone" -> respond(exchange, 410);
        case "/missing" -> respond(exchange, 404);
        case "/no-head" ->
            respond(exchange, "HEAD".equals(exchange.getRequestMethod()) ? 405 : 206);
        case "/hop1" -> redirect(exchange, "/hop2");
        case "/hop2" -> redirect(exchange, base + "/ok");
        case "/loop" -> redirect(exchange, "/loop");
        case "/to-internal" -> redirect(exchange, "http://10.0.0.5/admin");
        case "/to-metadata" -> redirect(exchange, "http://169.254.169.254/latest/meta-data/");
        case "/slow" -> {
          sleep(1_500);
          respond(exchange, 200);
        }
        case "/busy" -> {
          sleep(200);
          respond(exchange, 200);
        }
        default -> respond(exchange, 500);
      }
    } finally {
      active.decrementAndGet();
    }
  }

  private static void respond(HttpExchange exchange, int status) throws IOException {
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }

  private static void redirect(HttpExchange exchange, String location) throws IOException {
    exchange.getResponseHeaders().add("Location", location);
    respond(exchange, 302);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private UrlChecker checker(UrlCheckPolicy policy) {
    NewsDigestProperties properties =
        TestProperties.with(
            Map.of(
                "newsdigest.url-check.timeout", "500ms",
                "newsdigest.url-check.max-concurrent-checks", "2"));
    return new UrlChecker(properties.urlCheck(), policy, UrlCheckerTest::resolve);
  }

  /** Test DNS: literals as they are, a few names of the test, everything else does not exist. */
  private static InetAddress[] resolve(String host) throws UnknownHostException {
    return switch (host) {
      case "127.0.0.1", "10.0.0.5", "169.254.169.254" ->
          new InetAddress[] {InetAddress.getByName(host)};
      case "intranet.example.test" ->
          new InetAddress[] {InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.1.2.3")};
      default -> throw new UnknownHostException(host);
    };
  }

  @Test
  void reachableSourceIsCheckedWithHeadAndOwnUserAgent() {
    UrlChecker.Result result = checker(STUB).check(base + "/ok");

    assertThat(result.reachable()).isTrue();
    assertThat(requests).containsExactly("HEAD /ok " + UrlChecker.USER_AGENT + " null");
  }

  @Test
  void missingAndGoneAreUnreachableButBlockedBotsAndServerErrorsAreNot() {
    UrlChecker checker = checker(STUB);

    assertThat(checker.check(base + "/missing"))
        .isEqualTo(new UrlChecker.Result(false, "http 404"));
    assertThat(checker.check(base + "/gone")).isEqualTo(new UrlChecker.Result(false, "http 410"));
    assertThat(checker.check(base + "/forbidden").reachable()).isTrue();
    assertThat(checker.check(base + "/error").reachable()).isTrue();
  }

  @Test
  void serverWithoutHeadIsAskedWithARangeGet() {
    UrlChecker.Result result = checker(STUB).check(base + "/no-head");

    assertThat(result).isEqualTo(new UrlChecker.Result(true, "http 206"));
    assertThat(requests)
        .containsExactly(
            "HEAD /no-head " + UrlChecker.USER_AGENT + " null",
            "GET /no-head " + UrlChecker.USER_AGENT + " bytes=0-0");
  }

  @Test
  void redirectsAreFollowedUpToTheLimit() {
    UrlChecker checker = checker(STUB);

    assertThat(checker.check(base + "/hop1").reachable()).isTrue();
    assertThat(requests).extracting(r -> r.split(" ")[1]).containsExactly("/hop1", "/hop2", "/ok");

    requests.clear();
    assertThat(checker.check(base + "/loop"))
        .isEqualTo(new UrlChecker.Result(false, "too many redirects"));
    // First request plus 3 redirects
    assertThat(requests).hasSize(4);
  }

  @Test
  void redirectToAnInternalAddressIsBlockedBeforeTheRequest() {
    UrlChecker checker = checker(STUB);

    assertThat(checker.check(base + "/to-internal"))
        .isEqualTo(new UrlChecker.Result(false, "blocked address"));
    assertThat(checker.check(base + "/to-metadata"))
        .isEqualTo(new UrlChecker.Result(false, "blocked address"));
    assertThat(requests)
        .extracting(r -> r.split(" ")[1])
        .containsExactly("/to-internal", "/to-metadata");
  }

  @Test
  void blockedTargetsAreNeverRequested() {
    UrlChecker production = checker(UrlCheckPolicy.PUBLIC_ONLY);

    // Loopback and a host with one private address among public ones
    assertThat(production.check(base + "/ok").reason()).isEqualTo("blocked target");
    assertThat(production.check("http://127.0.0.1/ok").reason()).isEqualTo("blocked address");
    assertThat(production.check("https://intranet.example.test/").reason())
        .isEqualTo("blocked address");
    assertThat(production.check("ftp://127.0.0.1/file").reason()).isEqualTo("blocked target");
    assertThat(production.check("file:///etc/passwd").reason()).isEqualTo("blocked target");
    assertThat(production.check("not a url").reason()).isEqualTo("invalid url");
    assertThat(production.check("https://nowhere.example.test/a").reason()).isEqualTo("dns");
    assertThat(requests).isEmpty();
  }

  @Test
  void slowSourceTimesOut() {
    assertThat(checker(STUB).check(base + "/slow"))
        .isEqualTo(new UrlChecker.Result(false, "timeout"));
  }

  @Test
  void closedPortIsUnreachable() throws IOException {
    int port;
    try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
      port = socket.getLocalPort();
    }

    assertThat(checker(STUB).check("http://127.0.0.1:" + port + "/").reachable()).isFalse();
  }

  @Test
  void everyUrlIsCheckedOnceWithLimitedConcurrency() {
    List<String> urls =
        List.of(
            base + "/busy?1",
            base + "/busy?2",
            base + "/busy?3",
            base + "/busy?4",
            base + "/busy?1",
            base + "/missing");

    Map<String, UrlChecker.Result> results = checker(STUB).checkAll(urls);

    assertThat(results).hasSize(5);
    assertThat(results.get(base + "/missing").reachable()).isFalse();
    assertThat(requests).hasSize(5);
    assertThat(maxActive.get()).isLessThanOrEqualTo(2);
  }
}
