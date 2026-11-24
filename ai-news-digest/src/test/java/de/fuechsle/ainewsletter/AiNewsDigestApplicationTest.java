package de.fuechsle.ainewsletter;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.support.PostgresTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiNewsDigestApplicationTest extends PostgresTestSupport {

  private final int port;
  private final NewsDigestProperties properties;

  @Autowired
  AiNewsDigestApplicationTest(
      @Value("${local.server.port}") int port, NewsDigestProperties properties) {
    this.port = port;
    this.properties = properties;
  }

  @Test
  void contextStartsAndBindsDefaults() {
    assertThat(properties.ai().maxItemsPerModel()).isEqualTo(10);
  }

  @Test
  void healthEndpointReportsUp() throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("\"status\":\"UP\"");
    }
  }

  @Test
  void devEndpointsDoNotExistWithoutProfileDev() throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/dev/topics/1/runs"))
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isIn(404, 405);
    }
  }
}
