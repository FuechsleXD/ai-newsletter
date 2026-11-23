package de.fuechsle.ainewsletter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Period;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * All application defaults in one place (see docs/12-defaults.md). Every value can be overridden in
 * application.yml or via environment variables; where a database column exists, it overrides the
 * default at runtime.
 *
 * <p>Providers, models, prices, recipients and schedules are intentionally not part of this class:
 * they live in the database only (ADR-013).
 */
@Validated
@ConfigurationProperties(prefix = "newsdigest")
public record NewsDigestProperties(
    @Valid @NotNull @DefaultValue Run run,
    @Valid @NotNull @DefaultValue Ai ai,
    @Valid @NotNull @DefaultValue Prompts prompts,
    @Valid @NotNull @DefaultValue Providers providers,
    @Valid @NotNull @DefaultValue Auth auth,
    @Valid @NotNull @DefaultValue Secrets secrets,
    @Valid @NotNull @DefaultValue UrlCheck urlCheck,
    @Valid @NotNull @DefaultValue Budget budget,
    @Valid @NotNull @DefaultValue Process process,
    @Valid @NotNull @DefaultValue Render render,
    @Valid @NotNull @DefaultValue Output output,
    @Valid @NotNull @DefaultValue Mail mail,
    @Valid @NotNull @DefaultValue Storage storage,
    @Valid @NotNull @DefaultValue Retention retention,
    @Valid @NotNull @DefaultValue Api api) {

  /** Run defaults. */
  public record Run(
      @Min(1) @DefaultValue("7") int initialLookbackDays,
      @NotNull @DefaultValue("60s") Duration configReloadInterval,
      @Min(1) @DefaultValue("3") int maxManualRunsPerTopicPerHour) {}

  /** Model and research defaults. */
  public record Ai(
      @NotNull @DefaultValue("120s") Duration callTimeout,
      /**
       * Used when {@code ai_model.max_output_tokens} is NULL; the Anthropic API requires a value.
       */
      @Min(1) @DefaultValue("16000") int defaultMaxOutputTokens,
      /** Upper limit of web searches per research call (cost guard). */
      @Min(1) @DefaultValue("5") int maxWebSearchesPerCall,
      @Min(1) @DefaultValue("2") int maxConcurrentCallsPerProvider,
      @Min(1) @DefaultValue("10") int maxItemsPerModel,
      @Min(0) @DefaultValue("1") int jsonRepairAttempts,
      @Min(1) @DefaultValue("90") int duplicateLookbackDays,
      @DefaultValue("true") boolean passPreviousHeadlinesToAggregator) {}

  /** Active prompt version per prompt (src/main/resources/prompts/<name>/<version>.txt). */
  public record Prompts(
      @NotBlank @DefaultValue("v1") String research,
      @NotBlank @DefaultValue("v1") String aggregate,
      @NotBlank @DefaultValue("v1") String repairJson) {}

  /** Technical endpoints of the provider APIs (not secrets, not model choices). */
  public record Providers(@Valid @NotNull @DefaultValue Anthropic anthropic) {

    /** Anthropic Messages API. */
    public record Anthropic(@NotBlank @DefaultValue("https://api.anthropic.com") String baseUrl) {}
  }

  /** Token validation defaults (Keycloak). */
  public record Auth(@NotNull @DefaultValue("30s") Duration clockSkew) {}

  /** Secrets Manager defaults. */
  public record Secrets(@NotNull @DefaultValue("15m") Duration cacheTtl) {}

  /** URL check defaults. */
  public record UrlCheck(
      @NotNull @DefaultValue("5s") Duration timeout,
      @Min(0) @DefaultValue("3") int maxRedirects,
      @Min(1) @DefaultValue("5") int maxConcurrentChecks) {}

  /** Budget defaults in USD. */
  public record Budget(
      @NotNull @DecimalMin("0") @DefaultValue("10") BigDecimal monthlyLimitUsd,
      @Min(1) @Max(100) @DefaultValue("80") int warnPercent) {}

  /** Process engine defaults. */
  public record Process(
      @NotBlank @DefaultValue("R3/PT5M") String externalTaskRetryCycle,
      @NotNull @DefaultValue("30d") Duration historyTimeToLive,
      @NotNull @DefaultValue("10m") Duration jobLockTime,
      @NotNull @DefaultValue("3m") Duration shutdownTimeout) {}

  /** PDF rendering defaults. Gotenberg is only reachable in the internal Docker network. */
  public record Render(
      @NotBlank @DefaultValue("http://localhost:3000") String gotenbergUrl,
      @NotNull @DefaultValue("30s") Duration gotenbergTimeout,
      @Min(0) @DefaultValue("1") int shortenAttempts) {}

  /** Output defaults. Sender and operator addresses come only from environment variables. */
  public record Output(
      @NotNull @DefaultValue("de") Locale language,
      @Min(1) @DefaultValue("4") int minTopicCards,
      @Min(1) @DefaultValue("6") int maxTopicCards,
      /** Upper limit for headings in the digest (prompt and shortening before rendering). */
      @Min(10) @DefaultValue("60") int maxHeadingChars,
      /** Upper limit for texts in the digest (prompt and shortening before rendering). */
      @Min(20) @DefaultValue("320") int maxTextChars,
      @NotBlank @DefaultValue("[News Digest] {topic} – {date}") String mailSubjectPattern) {}

  /**
   * Mail addresses of the app; only from environment variables ({@code NEWSDIGEST_MAIL_FROM},
   * {@code NEWSDIGEST_MAIL_OPERATOR}), never in code or migrations (golden rule 9). The app does
   * not start without them.
   */
  public record Mail(@NotBlank @Email String from, @NotBlank @Email String operator) {}

  /**
   * Storage of the rendered PDFs until S3 follows in roadmap step 11: a local directory.
   *
   * @param localDirectory base directory; the key {@code runs/<runId>/digest.pdf} is resolved in it
   */
  public record Storage(@NotBlank @DefaultValue("data/pdfs") String localDirectory) {}

  /** Retention periods. */
  public record Retention(
      @NotNull @DefaultValue("90d") Period rawResponse, @NotNull @DefaultValue("2y") Period runs) {}

  /** REST API defaults. */
  public record Api(
      @Min(1) @DefaultValue("20") int defaultPageSize,
      @Min(1) @DefaultValue("100") int maxPageSize) {}
}
