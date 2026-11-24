package de.fuechsle.ainewsletter.eval;

import de.fuechsle.ainewsletter.render.DigestRenderer;
import de.fuechsle.ainewsletter.render.RenderDigestService;
import de.fuechsle.ainewsletter.storage.DigestStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes everything a person needs to judge the prompts of one run into a directory: {@code
 * run.json} (status, period, prompt version, per model the usage, cost, error and the news items
 * with their check status, aggregation cost, delivery statuses), the raw answer of every research
 * model, {@code aggregate.json}, {@code digest.pdf}, {@code mail.html} and a short {@code
 * summary.md}. Mail addresses are never exported.
 */
@Component
public class EvalExporter {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final RenderDigestService renderService;
  private final DigestRenderer renderer;
  private final DigestStorage storage;

  public EvalExporter(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      RenderDigestService renderService,
      DigestRenderer renderer,
      DigestStorage storage) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.renderService = renderService;
    this.renderer = renderer;
    this.storage = storage;
  }

  /** Exports the run into {@code directory} (created if needed). */
  public void export(long runId, Path directory) {
    Map<String, Object> run =
        jdbc.queryForMap(
            "SELECT r.id, t.name AS topic, r.status, r.period_from, r.period_to, r.prompt_version,"
                + " r.started_at, r.finished_at, r.error_message, r.aggregate_json::text AS aggregate,"
                + " r.pdf_s3_key FROM digest_run r JOIN topic t ON t.id = r.topic_id WHERE r.id = ?",
            runId);
    List<Map<String, Object>> results = providerResults(runId);
    Map<String, Object> aggregation = aggregation(runId);
    List<String> deliveries =
        jdbc.queryForList(
            "SELECT status FROM mail_delivery WHERE digest_run_id = ? ORDER BY id",
            String.class,
            runId);

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("runId", runId);
    report.put("topic", run.get("topic"));
    report.put("status", run.get("status"));
    report.put("periodFrom", String.valueOf(run.get("period_from")));
    report.put("periodTo", String.valueOf(run.get("period_to")));
    report.put("promptVersion", run.get("prompt_version"));
    report.put("startedAt", String.valueOf(run.get("started_at")));
    report.put(
        "finishedAt", run.get("finished_at") == null ? null : run.get("finished_at").toString());
    report.put("errorMessage", run.get("error_message"));
    report.put("totalCostUsd", totalCost(results, aggregation));
    report.put("research", results);
    report.put("aggregation", aggregation);
    report.put("mailDeliveries", deliveries);

    // Raw answers go to their own files, not into run.json
    Map<String, String> rawAnswers = new LinkedHashMap<>();
    for (Map<String, Object> result : results) {
      Object raw = result.remove("rawResponse");
      if (raw != null) {
        rawAnswers.put("research-" + slug((String) result.get("model")) + ".txt", (String) raw);
      }
    }

    try {
      Files.createDirectories(directory);
      write(
          directory.resolve("run.json"),
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
      for (Map.Entry<String, String> raw : rawAnswers.entrySet()) {
        write(directory.resolve(raw.getKey()), raw.getValue());
      }
      String aggregate = (String) run.get("aggregate");
      if (aggregate != null) {
        write(
            directory.resolve("aggregate.json"),
            objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(objectMapper.readTree(aggregate)));
        write(directory.resolve("mail.html"), renderer.mailHtml(renderService.view(runId)));
      }
      String pdfKey = (String) run.get("pdf_s3_key");
      if (pdfKey != null) {
        Files.write(directory.resolve("digest.pdf"), storage.load(pdfKey));
      }
      write(directory.resolve("summary.md"), summary(report, results, aggregation));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot export run " + runId + " to " + directory, e);
    }
  }

  private List<Map<String, Object>> providerResults(long runId) {
    List<Map<String, Object>> results = new ArrayList<>();
    for (Map<String, Object> row :
        jdbc.queryForList(
            "SELECT p.id, m.display_name, m.model_name, p.status, p.input_tokens, p.output_tokens,"
                + " p.search_calls, p.cost_usd, p.duration_ms, p.error_message, p.raw_response"
                + " FROM provider_result p JOIN ai_model m ON m.id = p.model_id"
                + " WHERE p.digest_run_id = ? ORDER BY m.display_name",
            runId)) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("model", row.get("display_name"));
      result.put("modelName", row.get("model_name"));
      result.put("status", row.get("status"));
      result.put("inputTokens", row.get("input_tokens"));
      result.put("outputTokens", row.get("output_tokens"));
      result.put("webSearches", row.get("search_calls"));
      result.put("costUsd", row.get("cost_usd"));
      result.put("durationMs", row.get("duration_ms"));
      result.put("errorMessage", row.get("error_message"));
      result.put(
          "items",
          jdbc.queryForList(
              "SELECT id, headline, url, published_at::text AS published_at, status FROM news_item"
                  + " WHERE provider_result_id = ? ORDER BY id",
              row.get("id")));
      result.put("rawResponse", row.get("raw_response"));
      results.add(result);
    }
    return results;
  }

  private Map<String, Object> aggregation(long runId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT m.display_name AS model, a.input_tokens AS \"inputTokens\","
                + " a.output_tokens AS \"outputTokens\", a.cost_usd AS \"costUsd\","
                + " a.duration_ms AS \"durationMs\" FROM aggregation_result a"
                + " JOIN ai_model m ON m.id = a.model_id WHERE a.digest_run_id = ?",
            runId);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private static BigDecimal totalCost(
      List<Map<String, Object>> results, Map<String, Object> aggregation) {
    BigDecimal total =
        results.stream()
            .map(result -> (BigDecimal) result.get("costUsd"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return aggregation == null ? total : total.add((BigDecimal) aggregation.get("costUsd"));
  }

  private static String summary(
      Map<String, Object> report,
      List<Map<String, Object>> results,
      Map<String, Object> aggregation) {
    StringBuilder text = new StringBuilder();
    text.append("# Eval-Lauf ")
        .append(report.get("runId"))
        .append(" – ")
        .append(report.get("topic"));
    text.append("\n\n- Status: ").append(report.get("status"));
    text.append("\n- Zeitraum: ")
        .append(report.get("periodFrom"))
        .append(" bis ")
        .append(report.get("periodTo"));
    text.append("\n- Prompt-Version: ").append(report.get("promptVersion"));
    text.append("\n- Kosten gesamt: ").append(report.get("totalCostUsd")).append(" USD");
    if (report.get("errorMessage") != null) {
      text.append("\n- Fehler: ").append(report.get("errorMessage"));
    }
    text.append(
        "\n\n| Modell | Status | Input-Token | Output-Token | Websuchen | Kosten USD | Meldungen |");
    text.append("\n|---|---|---|---|---|---|---|");
    for (Map<String, Object> result : results) {
      text.append("\n| ")
          .append(result.get("model"))
          .append(" | ")
          .append(result.get("status"))
          .append(" | ")
          .append(result.get("inputTokens"))
          .append(" | ")
          .append(result.get("outputTokens"))
          .append(" | ")
          .append(result.get("webSearches"))
          .append(" | ")
          .append(result.get("costUsd"))
          .append(" | ")
          .append(itemCounts(result))
          .append(" |");
    }
    if (aggregation != null) {
      text.append("\n| Aggregator ")
          .append(aggregation.get("model"))
          .append(" | – | ")
          .append(aggregation.get("inputTokens"))
          .append(" | ")
          .append(aggregation.get("outputTokens"))
          .append(" | 0 | ")
          .append(aggregation.get("costUsd"))
          .append(" | – |");
    }
    return text.append('\n').toString();
  }

  /** E.g. {@code 4 (VALID 3, OUT_OF_PERIOD 1)}. */
  @SuppressWarnings("unchecked")
  private static String itemCounts(Map<String, Object> result) {
    List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Map<String, Object> item : items) {
      counts.merge((String) item.get("status"), 1, Integer::sum);
    }
    if (counts.isEmpty()) {
      return "0";
    }
    StringBuilder text = new StringBuilder().append(items.size()).append(" (");
    counts.forEach((status, count) -> text.append(status).append(' ').append(count).append(", "));
    text.setLength(text.length() - 2);
    return text.append(')').toString();
  }

  /** File and directory name part: lower case letters, digits and dashes only. */
  static String slug(String value) {
    String slug =
        value
            .toLowerCase(Locale.ROOT)
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
    return slug.isEmpty() ? "x" : slug;
  }

  private static void write(Path file, String content) throws IOException {
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }
}
