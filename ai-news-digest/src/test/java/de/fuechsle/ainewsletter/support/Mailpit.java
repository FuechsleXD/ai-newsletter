package de.fuechsle.ainewsletter.support;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads the mails Mailpit received, over its HTTP API (only the test container). */
public final class Mailpit {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final JsonMapper JSON = new JsonMapper();

  private Mailpit() {}

  /** A received mail with its HTML, text and attachments. */
  public record Mail(
      String id,
      String subject,
      String from,
      String html,
      String text,
      List<Attachment> attachments) {}

  public record Attachment(String fileName, String contentType, byte[] content) {}

  /** All mails to the address, oldest first. */
  public static List<Mail> mailsTo(String address) {
    JsonNode result =
        get(
            "/api/v1/search?query="
                + URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8));
    List<Mail> mails = new ArrayList<>();
    for (JsonNode summary : result.get("messages")) {
      mails.add(0, mail(summary.get("ID").asString()));
    }
    return mails;
  }

  /** Waits up to 10 s until the address has at least {@code count} mails. */
  public static List<Mail> awaitMailsTo(String address, int count) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    List<Mail> mails = mailsTo(address);
    while (mails.size() < count && Instant.now().isBefore(deadline)) {
      sleep();
      mails = mailsTo(address);
    }
    return mails;
  }

  private static Mail mail(String id) {
    JsonNode message = get("/api/v1/message/" + id);
    List<Attachment> attachments = new ArrayList<>();
    for (JsonNode attachment : message.get("Attachments")) {
      attachments.add(
          new Attachment(
              attachment.get("FileName").asString(),
              attachment.get("ContentType").asString(),
              bytes("/api/v1/message/" + id + "/part/" + attachment.get("PartID").asString())));
    }
    return new Mail(
        id,
        message.get("Subject").asString(),
        message.get("From").get("Address").asString(),
        message.get("HTML").asString(),
        message.get("Text").asString(),
        attachments);
  }

  private static JsonNode get(String path) {
    return JSON.readTree(new String(bytes(path), StandardCharsets.UTF_8));
  }

  private static byte[] bytes(String path) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(RenderAndMailContainers.mailpitApiUrl() + path)).build();
    try {
      HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Mailpit answered " + response.statusCode() + " for " + path);
      }
      return response.body();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void sleep() {
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
