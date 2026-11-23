package de.fuechsle.ainewsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/** News topic with its schedule, research models and recipients. */
@Entity
@Table(name = "topic")
public class Topic extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  /** NULL = default (newsdigest.run.initial-lookback-days). */
  @Column(name = "initial_lookback_days")
  private Integer initialLookbackDays;

  /** Model that condenses the research results; required, there is no default (ADR-013). */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "aggregator_model_id", nullable = false)
  private AiModel aggregatorModel;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "schedule_id", nullable = false)
  private Schedule schedule;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  /** Research models (parallel, no order). */
  @ManyToMany
  @JoinTable(
      name = "topic_model",
      joinColumns = @JoinColumn(name = "topic_id"),
      inverseJoinColumns = @JoinColumn(name = "model_id"))
  private Set<AiModel> models = new HashSet<>();

  @ManyToMany
  @JoinTable(
      name = "topic_recipient",
      joinColumns = @JoinColumn(name = "topic_id"),
      inverseJoinColumns = @JoinColumn(name = "recipient_id"))
  private Set<Recipient> recipients = new HashSet<>();

  protected Topic() {}

  public Topic(String name, Schedule schedule, AiModel aggregatorModel) {
    this.name = name;
    this.schedule = schedule;
    this.aggregatorModel = aggregatorModel;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getInitialLookbackDays() {
    return initialLookbackDays;
  }

  public void setInitialLookbackDays(Integer initialLookbackDays) {
    this.initialLookbackDays = initialLookbackDays;
  }

  public AiModel getAggregatorModel() {
    return aggregatorModel;
  }

  public void setAggregatorModel(AiModel aggregatorModel) {
    this.aggregatorModel = aggregatorModel;
  }

  public Schedule getSchedule() {
    return schedule;
  }

  public void setSchedule(Schedule schedule) {
    this.schedule = schedule;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Set<AiModel> getModels() {
    return models;
  }

  public Set<Recipient> getRecipients() {
    return recipients;
  }
}
