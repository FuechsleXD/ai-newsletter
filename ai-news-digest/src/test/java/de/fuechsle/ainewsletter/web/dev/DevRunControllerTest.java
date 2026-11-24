package de.fuechsle.ainewsletter.web.dev;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.support.ProcessTestSupport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Manual start over HTTP in profile {@code dev}, with real process, Gotenberg and Mailpit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DevRunControllerTest extends ProcessTestSupport {

  private final JsonMapper json = new JsonMapper();

  @Value("${local.server.port}")
  private int port;

  @Test
  void startsARunAndAnswersWithItsIds() throws Exception {
    String suffix = unique();
    long model = model("ok-" + suffix);
    long topic = topic("Dev " + suffix, model, model);

    HttpResponse<String> response = post(topic);

    assertThat(response.statusCode()).isEqualTo(202);
    JsonNode body = json.readTree(response.body());
    String instance = body.get("processInstanceId").asString();
    assertThat(body.get("runId").asLong()).isEqualTo(runId(instance));
    awaitEnd(instance);
    assertThat(runStatus(instance)).isEqualTo("COMPLETED");
  }

  @Test
  void unknownTopicIsANotFoundProblem() throws Exception {
    HttpResponse<String> response = post(Long.MAX_VALUE);

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    JsonNode problem = json.readTree(response.body());
    assertThat(problem.get("title").asString()).isEqualTo("Nicht gefunden");
    assertThat(problem.get("detail").asString()).contains("existiert nicht");
    assertThat(problem.get("status").asInt()).isEqualTo(404);
  }

  @Test
  void secondStartOfARunningTopicIsAConflictProblem() throws Exception {
    String suffix = unique();
    long model = model("slow-" + suffix);
    long topic = topic("Dev slow " + suffix, model, model);

    HttpResponse<String> first = post(topic);
    try {
      fakeProvider.awaitSlowCall();

      HttpResponse<String> second = post(topic);

      assertThat(second.statusCode()).isEqualTo(409);
      assertThat(json.readTree(second.body()).get("title").asString()).isEqualTo("Konflikt");
    } finally {
      fakeProvider.release();
    }
    awaitEnd(json.readTree(first.body()).get("processInstanceId").asString());
  }

  private HttpResponse<String> post(long topicId) throws IOException, InterruptedException {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(
                  URI.create("http://localhost:" + port + "/dev/topics/" + topicId + "/runs"))
              .POST(HttpRequest.BodyPublishers.noBody())
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
