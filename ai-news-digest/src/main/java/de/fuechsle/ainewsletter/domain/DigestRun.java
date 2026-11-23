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
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One run of the digest process for a topic. */
@Entity
@Table(name = "digest_run")
public class DigestRun {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "topic_id", nullable = false)
  private Topic topic;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "aggregator_model_id", nullable = false)
  private AiModel aggregatorModel;

  @Column(name = "process_instance_id", length = 64)
  private String processInstanceId;

  @Column(name = "prompt_version", nullable = false, length = 50)
  private String promptVersion;

  /** First day of the run period (inclusive) in the schedule's time zone. */
  @Column(name = "period_from", nullable = false)
  private LocalDate periodFrom;

  /** Last day of the run period (inclusive) in the schedule's time zone. */
  @Column(name = "period_to", nullable = false)
  private LocalDate periodTo;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private RunStatus status;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  /** Aggregator result document (deliberate denormalisation). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "aggregate_json", columnDefinition = "jsonb")
  private String aggregateJson;

  @Column(name = "pdf_s3_key", length = 1024)
  private String pdfS3Key;

  protected DigestRun() {}

  public DigestRun(
      Topic topic,
      AiModel aggregatorModel,
      String promptVersion,
      LocalDate periodFrom,
      LocalDate periodTo,
      Instant startedAt) {
    this.topic = topic;
    this.aggregatorModel = aggregatorModel;
    this.promptVersion = promptVersion;
    this.periodFrom = periodFrom;
    this.periodTo = periodTo;
    this.startedAt = startedAt;
    this.status = RunStatus.RUNNING;
  }

  public Long getId() {
    return id;
  }

  public Topic getTopic() {
    return topic;
  }

  public AiModel getAggregatorModel() {
    return aggregatorModel;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public LocalDate getPeriodFrom() {
    return periodFrom;
  }

  public LocalDate getPeriodTo() {
    return periodTo;
  }

  public RunStatus getStatus() {
    return status;
  }

  public void setStatus(RunStatus status) {
    this.status = status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getAggregateJson() {
    return aggregateJson;
  }

  public void setAggregateJson(String aggregateJson) {
    this.aggregateJson = aggregateJson;
  }

  public String getPdfS3Key() {
    return pdfS3Key;
  }

  public void setPdfS3Key(String pdfS3Key) {
    this.pdfS3Key = pdfS3Key;
  }
}
