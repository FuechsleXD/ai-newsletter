package de.fuechsle.ainewsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Result of one research model within a run (one row per run and model). */
@Entity
@Table(name = "provider_result")
public class ProviderResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "digest_run_id", nullable = false)
  private DigestRun digestRun;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "model_id", nullable = false)
  private AiModel model;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProviderResultStatus status;

  /** Untrusted model output; cleared after the retention period. */
  @Column(name = "raw_response", columnDefinition = "text")
  private String rawResponse;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  @Column(name = "input_tokens")
  private Integer inputTokens;

  @Column(name = "output_tokens")
  private Integer outputTokens;

  @Column(name = "search_calls")
  private Integer searchCalls;

  /** Cost snapshot at run time. */
  @Column(name = "cost_usd", precision = 12, scale = 6)
  private BigDecimal costUsd;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  /** Searches and their results (diagnosis, provider.SearchTrace as JSON), null without search. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "search_trace", columnDefinition = "jsonb")
  private String searchTrace;

  protected ProviderResult() {}

  public ProviderResult(
      DigestRun digestRun, AiModel model, ProviderResultStatus status, long durationMs) {
    this.digestRun = digestRun;
    this.model = model;
    this.status = status;
    this.durationMs = durationMs;
  }

  public Long getId() {
    return id;
  }

  public DigestRun getDigestRun() {
    return digestRun;
  }

  public AiModel getModel() {
    return model;
  }

  public ProviderResultStatus getStatus() {
    return status;
  }

  public void setStatus(ProviderResultStatus status) {
    this.status = status;
  }

  public String getRawResponse() {
    return rawResponse;
  }

  public void setRawResponse(String rawResponse) {
    this.rawResponse = rawResponse;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(long durationMs) {
    this.durationMs = durationMs;
  }

  public Integer getInputTokens() {
    return inputTokens;
  }

  public void setInputTokens(Integer inputTokens) {
    this.inputTokens = inputTokens;
  }

  public Integer getOutputTokens() {
    return outputTokens;
  }

  public void setOutputTokens(Integer outputTokens) {
    this.outputTokens = outputTokens;
  }

  public Integer getSearchCalls() {
    return searchCalls;
  }

  public void setSearchCalls(Integer searchCalls) {
    this.searchCalls = searchCalls;
  }

  public BigDecimal getCostUsd() {
    return costUsd;
  }

  public void setCostUsd(BigDecimal costUsd) {
    this.costUsd = costUsd;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getSearchTrace() {
    return searchTrace;
  }

  public void setSearchTrace(String searchTrace) {
    this.searchTrace = searchTrace;
  }
}
