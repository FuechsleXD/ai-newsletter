package de.fuechsle.ainewsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Model of a provider with capabilities and prices (USD per 1 million tokens). */
@Entity
@Table(name = "ai_model")
public class AiModel extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "provider_id", nullable = false)
  private AiProvider provider;

  @Column(name = "model_name", nullable = false, length = 200)
  private String modelName;

  @Column(name = "display_name", nullable = false, length = 200)
  private String displayName;

  @Column(name = "supports_web_search", nullable = false)
  private boolean supportsWebSearch;

  @Column(name = "supports_structured_output", nullable = false)
  private boolean supportsStructuredOutput;

  /** NULL = provider default. */
  @Column(name = "max_output_tokens")
  private Integer maxOutputTokens;

  /** NULL = provider default. */
  @Column(name = "temperature", precision = 3, scale = 2)
  private BigDecimal temperature;

  @Column(name = "input_price_per_mtok", nullable = false, precision = 12, scale = 6)
  private BigDecimal inputPricePerMtok;

  @Column(name = "output_price_per_mtok", nullable = false, precision = 12, scale = 6)
  private BigDecimal outputPricePerMtok;

  @Column(name = "price_per_search_call", precision = 12, scale = 6)
  private BigDecimal pricePerSearchCall;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  protected AiModel() {}

  public AiModel(
      AiProvider provider,
      String modelName,
      String displayName,
      boolean supportsWebSearch,
      boolean supportsStructuredOutput,
      BigDecimal inputPricePerMtok,
      BigDecimal outputPricePerMtok) {
    this.provider = provider;
    this.modelName = modelName;
    this.displayName = displayName;
    this.supportsWebSearch = supportsWebSearch;
    this.supportsStructuredOutput = supportsStructuredOutput;
    this.inputPricePerMtok = inputPricePerMtok;
    this.outputPricePerMtok = outputPricePerMtok;
  }

  public Long getId() {
    return id;
  }

  public AiProvider getProvider() {
    return provider;
  }

  public String getModelName() {
    return modelName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public boolean isSupportsWebSearch() {
    return supportsWebSearch;
  }

  public void setSupportsWebSearch(boolean supportsWebSearch) {
    this.supportsWebSearch = supportsWebSearch;
  }

  public boolean isSupportsStructuredOutput() {
    return supportsStructuredOutput;
  }

  public void setSupportsStructuredOutput(boolean supportsStructuredOutput) {
    this.supportsStructuredOutput = supportsStructuredOutput;
  }

  public Integer getMaxOutputTokens() {
    return maxOutputTokens;
  }

  public void setMaxOutputTokens(Integer maxOutputTokens) {
    this.maxOutputTokens = maxOutputTokens;
  }

  public BigDecimal getTemperature() {
    return temperature;
  }

  public void setTemperature(BigDecimal temperature) {
    this.temperature = temperature;
  }

  public BigDecimal getInputPricePerMtok() {
    return inputPricePerMtok;
  }

  public void setInputPricePerMtok(BigDecimal inputPricePerMtok) {
    this.inputPricePerMtok = inputPricePerMtok;
  }

  public BigDecimal getOutputPricePerMtok() {
    return outputPricePerMtok;
  }

  public void setOutputPricePerMtok(BigDecimal outputPricePerMtok) {
    this.outputPricePerMtok = outputPricePerMtok;
  }

  public BigDecimal getPricePerSearchCall() {
    return pricePerSearchCall;
  }

  public void setPricePerSearchCall(BigDecimal pricePerSearchCall) {
    this.pricePerSearchCall = pricePerSearchCall;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
