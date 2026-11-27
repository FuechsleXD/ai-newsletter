package de.fuechsle.ainewsletter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import de.fuechsle.ainewsletter.secret.SecretNotFoundException;
import de.fuechsle.ainewsletter.secret.SecretProvider;
import de.fuechsle.ainewsletter.support.TestProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to a local stub of the Anthropic Messages API (JDK HTTP server), never to the real API
 * (golden rule 10). Checks what is sent and how the answer is mapped.
 */
class AnthropicNewsProviderTest {

  private static final String API_KEY = "test-key-not-real";

  // Shape of a Messages API answer with server-side web search
  private static final String WEB_SEARCH_ANSWER =
      """
      {
        "id": "msg_test",
        "type": "message",
        "role": "assistant",
        "model": "test-model-1",
        "content": [
          {"type": "text", "text": "Ich suche nach Meldungen. "},
          {"type": "server_tool_use", "id": "srvtoolu_1", "name": "web_search",
           "input": {"query": "Java Release November 2026"}},
          {"type": "web_search_tool_result", "tool_use_id": "srvtoolu_1",
           "content": [{"type": "web_search_result", "url": "https://news.example.test/a",
                        "title": "A", "encrypted_content": "abc", "page_age": null}]},
          {"type": "text", "text": "{\\"items\\": []}"}
        ],
        "stop_reason": "end_turn",
        "stop_sequence": null,
        "usage": {
          "input_tokens": 12000,
          "output_tokens": 800,
          "server_tool_use": {"web_search_requests": 2}
        }
      }
      """;

  private final JsonMapper json = new JsonMapper();
  private final List<Recorded> requests = new CopyOnWriteArrayList<>();
  private HttpServer server;
  private volatile int status = 200;
  private volatile String answer = WEB_SEARCH_ANSWER;

  @BeforeEach
  void startStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          requests.add(new Recorded(exchange.getRequestHeaders().getFirst("x-api-key"), body));
          byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopStub() {
    server.stop(0);
  }

  @Test
  void researchCallSendsModelLimitsWebSearchAndKeyAndMapsUsage() {
    ModelResponse response = provider().call(invocation(null, true));

    assertThat(requests).hasSize(1);
    Recorded request = requests.getFirst();
    assertThat(request.apiKey()).isEqualTo(API_KEY);
    JsonNode body = json.readTree(request.body());
    assertThat(body.get("model").asString()).isEqualTo("test-model-1");
    assertThat(body.get("max_tokens").asInt()).isEqualTo(4_000);
    assertThat(body.has("temperature")).isFalse();
    assertThat(body.get("messages").get(0).toString()).contains("Finde Meldungen zu Java");
    JsonNode tool = body.get("tools").get(0);
    assertThat(tool.get("type").asString()).startsWith("web_search_");
    assertThat(tool.get("max_uses").asInt()).isEqualTo(3);

    assertThat(response.text()).contains("{\"items\": []}");
    assertThat(response.inputTokens()).isEqualTo(12_000);
    assertThat(response.outputTokens()).isEqualTo(800);
    assertThat(response.webSearches()).isEqualTo(2);
    assertThat(response.stopReason()).isEqualToIgnoringCase("end_turn");
    assertThat(response.refused()).isFalse();
    // Query and hits come from the complete answer in the metadata
    assertThat(response.searchTrace().searches())
        .containsExactly(
            new SearchTrace.Search(
                "Java Release November 2026",
                List.of(new SearchTrace.Hit("A", "https://news.example.test/a", null)),
                null));
    assertThat(response.duration().isNegative()).isFalse();
  }

  @Test
  void temperatureIsSentOnlyWhenMaintained() {
    provider().call(invocation(new BigDecimal("0.3"), true));

    JsonNode body = json.readTree(requests.getFirst().body());
    assertThat(body.get("temperature").asDouble()).isEqualTo(0.3);
  }

  @Test
  void callWithoutWebSearchSendsNoTool() {
    answer =
        """
        {"id": "msg_2", "type": "message", "role": "assistant", "model": "test-model-1",
         "content": [{"type": "text", "text": "{\\"items\\": []}"}],
         "stop_reason": "end_turn", "stop_sequence": null,
         "usage": {"input_tokens": 10, "output_tokens": 5}}
        """;

    ModelResponse response = provider().call(invocation(null, false));

    JsonNode body = json.readTree(requests.getFirst().body());
    assertThat(body.has("tools")).isFalse();
    assertThat(response.webSearches()).isZero();
  }

  @Test
  void answerWithoutContentIsReportedAsNoContent() {
    answer =
        """
        {"id": "msg_3", "type": "message", "role": "assistant", "model": "test-model-1",
         "content": [], "stop_reason": "refusal", "stop_sequence": null,
         "usage": {"input_tokens": 10, "output_tokens": 0}}
        """;

    ModelResponse response = provider().call(invocation(null, true));

    // Spring AI 2.0.3 drops answers without content, including stop reason and usage: the input
    // tokens are estimated from the prompt (24 characters / 3), the estimate is marked
    assertThat(response.noContent()).isTrue();
    assertThat(response.text()).isEmpty();
    assertThat(response.inputTokens()).isEqualTo(8);
    assertThat(response.outputTokens()).isZero();
    assertThat(response.usageEstimated()).isTrue();
  }

  @Test
  void pausedTurnKeepsItsStopReasonAndUsage() {
    answer =
        """
        {"id": "msg_4", "type": "message", "role": "assistant", "model": "test-model-1",
         "content": [{"type": "text", "text": "Ich suche weiter."}],
         "stop_reason": "pause_turn", "stop_sequence": null,
         "usage": {"input_tokens": 900, "output_tokens": 50,
                   "server_tool_use": {"web_search_requests": 5}}}
        """;

    ModelResponse response = provider().call(invocation(null, true));

    assertThat(response.paused()).isTrue();
    assertThat(response.usageEstimated()).isFalse();
    assertThat(response.inputTokens()).isEqualTo(900);
    assertThat(response.webSearches()).isEqualTo(5);
  }

  @Test
  void searchErrorsAndSearchesWithoutResultAreTraced() {
    answer =
        """
        {"id": "msg_5", "type": "message", "role": "assistant", "model": "test-model-1",
         "content": [
           {"type": "server_tool_use", "id": "srvtoolu_1", "name": "web_search",
            "input": {"query": "Spring AI November 2026"}},
           {"type": "web_search_tool_result", "tool_use_id": "srvtoolu_1",
            "content": [{"type": "web_search_result", "url": "https://spring.example.test/b",
                         "title": "B", "encrypted_content": "x", "page_age": "2 days ago"}]},
           {"type": "server_tool_use", "id": "srvtoolu_2", "name": "web_search",
            "input": {"query": "Copilot November 2026"}},
           {"type": "web_search_tool_result", "tool_use_id": "srvtoolu_2",
            "content": {"type": "web_search_tool_result_error", "error_code": "max_uses_exceeded"}},
           {"type": "server_tool_use", "id": "srvtoolu_3", "name": "web_search",
            "input": {"query": "ohne Ergebnis"}},
           {"type": "server_tool_use", "id": "srvtoolu_4", "name": "code_execution",
            "input": {"code": "print(1)"}},
           {"type": "text", "text": "{\\"items\\": []}"}
         ],
         "stop_reason": "end_turn", "stop_sequence": null,
         "usage": {"input_tokens": 100, "output_tokens": 10,
                   "server_tool_use": {"web_search_requests": 3}}}
        """;

    SearchTrace trace = provider().call(invocation(null, true)).searchTrace();

    assertThat(trace.searches())
        .containsExactly(
            new SearchTrace.Search(
                "Spring AI November 2026",
                List.of(new SearchTrace.Hit("B", "https://spring.example.test/b", "2 days ago")),
                null),
            new SearchTrace.Search("Copilot November 2026", List.of(), "max_uses_exceeded"),
            new SearchTrace.Search("ohne Ergebnis", List.of(), null));
    assertThat(trace.hitCount()).isEqualTo(1);
    // The code execution of the search tool is no search, it is only counted
    assertThat(trace.otherToolCalls()).isEqualTo(1);
  }

  @Test
  void callWithoutSearchHasAnEmptyTrace() {
    answer =
        """
        {"id": "msg_6", "type": "message", "role": "assistant", "model": "test-model-1",
         "content": [{"type": "text", "text": "{}"}],
         "stop_reason": "end_turn", "stop_sequence": null,
         "usage": {"input_tokens": 10, "output_tokens": 2}}
        """;

    assertThat(provider().call(invocation(null, false)).searchTrace().isEmpty()).isTrue();
  }

  @Test
  void estimateRoundsUp() {
    ModelResponse response =
        AnthropicNewsProvider.withoutContent(
            invocation(null, false).withPrompt("1234567"), Duration.ofSeconds(1));

    assertThat(response.inputTokens()).isEqualTo(3);
  }

  @Test
  void httpErrorBecomesProviderExceptionWithoutKey() {
    status = 400;
    answer =
        """
        {"type": "error", "error": {"type": "invalid_request_error",
         "message": "temperature is not supported for this model"}}
        """;

    assertThatThrownBy(() -> provider().call(invocation(new BigDecimal("0.3"), true)))
        .isInstanceOf(ProviderException.class)
        .hasMessageContaining("test-model-1")
        .hasMessageNotContaining(API_KEY);
  }

  @Test
  void retryableErrorIsNotRetriedBySdk() {
    // 529 overloaded would be retried twice by the SDK default; every attempt can be billed
    status = 529;
    answer =
        """
        {"type": "error", "error": {"type": "overloaded_error", "message": "Overloaded"}}
        """;

    assertThatThrownBy(() -> provider().call(invocation(null, true)))
        .isInstanceOf(ProviderException.class);
    assertThat(requests).hasSize(1);
    assertThat(provider().options(invocation(null, true), API_KEY).getMaxRetries()).isZero();
  }

  @Test
  void missingKeyFailsBeforeAnyRequest() {
    SecretProvider noSecrets =
        name -> {
          throw new SecretNotFoundException("Secret '" + name + "' is not set");
        };
    AnthropicNewsProvider provider =
        new AnthropicNewsProvider(noSecrets, properties(), Clock.systemUTC());

    assertThatThrownBy(() -> provider.call(invocation(null, true)))
        .isInstanceOf(SecretNotFoundException.class);
    assertThat(requests).isEmpty();
  }

  @Test
  void codeMatchesTheProviderRow() {
    assertThat(provider().code()).isEqualTo("ANTHROPIC");
  }

  private AnthropicNewsProvider provider() {
    SecretProvider secrets =
        name -> {
          assertThat(name).isEqualTo("newsdigest/test/ai/anthropic");
          return API_KEY;
        };
    return new AnthropicNewsProvider(secrets, properties(), Clock.systemUTC());
  }

  private de.fuechsle.ainewsletter.config.NewsDigestProperties properties() {
    String baseUrl = "http://localhost:" + server.getAddress().getPort();
    return TestProperties.with(Map.of("newsdigest.providers.anthropic.base-url", baseUrl));
  }

  private static ModelInvocation invocation(BigDecimal temperature, boolean webSearch) {
    return new ModelInvocation(
        "ANTHROPIC",
        "newsdigest/test/ai/anthropic",
        "test-model-1",
        4_000,
        temperature,
        webSearch,
        3,
        "Finde Meldungen zu Java.",
        Duration.ofSeconds(10));
  }

  private record Recorded(String apiKey, String body) {}
}
