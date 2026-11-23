package de.fuechsle.ainewsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cron schedule with time zone, usable by several topics. */
@Entity
@Table(name = "schedule")
public class Schedule extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "cron_expression", nullable = false, length = 100)
  private String cronExpression;

  @Column(name = "timezone", nullable = false, length = 64)
  private String timezone;

  protected Schedule() {}

  public Schedule(String name, String cronExpression, String timezone) {
    this.name = name;
    this.cronExpression = cronExpression;
    this.timezone = timezone;
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

  public String getCronExpression() {
    return cronExpression;
  }

  public void setCronExpression(String cronExpression) {
    this.cronExpression = cronExpression;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }
}
