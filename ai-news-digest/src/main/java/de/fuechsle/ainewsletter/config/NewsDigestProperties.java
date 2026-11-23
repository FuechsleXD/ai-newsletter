package de.fuechsle.ainewsletter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
 * <p>Model names, recipients and schedules are intentionally not part of this class: they live in
 * the database. The only exception is the default aggregator model ID, which has no default value
 * and must be configured explicitly.
 */
@Validated
@ConfigurationProperties(prefix = "newsdigest")
public record NewsDigestProperties(
    @Valid @NotNull @DefaultValue Run run,
    @Valid @NotNull @DefaultValue Ai ai,
    @Valid @NotNull @DefaultValue Auth auth,
    @Valid @NotNull @DefaultValue Secrets secrets,
    @Valid @NotNull @DefaultValue UrlCheck urlCheck,
    @Valid @NotNull @DefaultValue Budget budget,
    @Valid @NotNull @DefaultValue Process process,
    @Valid @NotNull @DefaultValue Render render,
    @Valid @NotNull @DefaultValue Output output,
    @Valid @NotNull @DefaultValue Retention retention,
    @Valid @NotNull @DefaultValue Api api) {

  /** Run defaults. */
  public record Run(
      @Min(1) @DefaultValue("7") int initialLookbackDays,
      @NotNull @DefaultValue("60s") Duration configReloadInterval,
      @Min(1) @DefaultValue("3") int maxManualRunsPerTopicPerHour) {}

  /** Model and research defaults. */
  public record Ai(
      @NotBlank @DefaultValue("ANTHROPIC") String defaultProviderCode,
      /** Default aggregator model ID; no default value, checked against the DB at startup. */
      String aggregatorModelId,
      @NotNull @DefaultValue("120s") Duration callTimeout,
      @Min(1) @DefaultValue("2") int maxConcurrentCallsPerProvider,
      @Min(1) @DefaultValue("10") int maxItemsPerModel,
      @Min(0) @DefaultValue("1") int jsonRepairAttempts,
      @Min(1) @DefaultValue("90") int duplicateLookbackDays,
      @DefaultValue("true") boolean passPreviousHeadlinesToAggregator) {}

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

  /** PDF rendering defaults. */
  public record Render(
      @NotNull @DefaultValue("30s") Duration gotenbergTimeout,
      @Min(0) @DefaultValue("1") int shortenAttempts) {}

  /** Output defaults. Sender and operator addresses come only from environment variables. */
  public record Output(
      @NotNull @DefaultValue("de") Locale language,
      @Min(1) @DefaultValue("4") int minTopicCards,
      @Min(1) @DefaultValue("6") int maxTopicCards,
      @NotBlank @DefaultValue("[News Digest] {topic} – {date}") String mailSubjectPattern) {}

  /** Retention periods. */
  public record Retention(
      @NotNull @DefaultValue("90d") Period rawResponse, @NotNull @DefaultValue("2y") Period runs) {}

  /** REST API defaults. */
  public record Api(
      @Min(1) @DefaultValue("20") int defaultPageSize,
      @Min(1) @DefaultValue("100") int maxPageSize) {}
}
