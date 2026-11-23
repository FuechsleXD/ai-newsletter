package de.fuechsle.ainewsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * AI provider; {@code secretName} is the name of the secret in AWS Secrets Manager, never a key.
 */
@Entity
@Table(name = "ai_provider")
public class AiProvider extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "code", nullable = false, length = 32)
  private String code;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "secret_name", nullable = false)
  private String secretName;

  /** NULL = default (newsdigest.ai.max-concurrent-calls-per-provider). */
  @Column(name = "max_concurrent_calls")
  private Integer maxConcurrentCalls;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  protected AiProvider() {}

  public AiProvider(String code, String displayName, String secretName) {
    this.code = code;
    this.displayName = displayName;
    this.secretName = secretName;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getSecretName() {
    return secretName;
  }

  public void setSecretName(String secretName) {
    this.secretName = secretName;
  }

  public Integer getMaxConcurrentCalls() {
    return maxConcurrentCalls;
  }

  public void setMaxConcurrentCalls(Integer maxConcurrentCalls) {
    this.maxConcurrentCalls = maxConcurrentCalls;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
