package de.fuechsle.ainewsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Usage and cost of the aggregator call of a run (1:1 with the run). */
@Entity
@Table(name = "aggregation_result")
public class AggregationResult {

  @Id
  @Column(name = "digest_run_id")
  private Long digestRunId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "digest_run_id")
  private DigestRun digestRun;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "model_id", nullable = false)
  private AiModel model;

  @Column(name = "input_tokens", nullable = false)
  private int inputTokens;

  @Column(name = "output_tokens", nullable = false)
  private int outputTokens;

  /** Cost snapshot at run time. */
  @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6)
  private BigDecimal costUsd;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  protected AggregationResult() {}

  public AggregationResult(
      DigestRun digestRun,
      AiModel model,
      int inputTokens,
      int outputTokens,
      BigDecimal costUsd,
      long durationMs) {
    this.digestRun = digestRun;
    this.model = model;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.costUsd = costUsd;
    this.durationMs = durationMs;
  }

  public Long getDigestRunId() {
    return digestRunId;
  }

  public DigestRun getDigestRun() {
    return digestRun;
  }

  public AiModel getModel() {
    return model;
  }

  public int getInputTokens() {
    return inputTokens;
  }

  public int getOutputTokens() {
    return outputTokens;
  }

  public BigDecimal getCostUsd() {
    return costUsd;
  }

  public long getDurationMs() {
    return durationMs;
  }
}
