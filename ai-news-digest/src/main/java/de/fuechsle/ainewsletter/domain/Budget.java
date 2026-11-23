package de.fuechsle.ainewsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Monthly budget in USD; at most one row is active, without one the default applies. */
@Entity
@Table(name = "budget")
public class Budget {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "month_limit_usd", nullable = false, precision = 12, scale = 6)
  private BigDecimal monthLimitUsd;

  @Column(name = "warn_percent", nullable = false)
  private int warnPercent;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  protected Budget() {}

  public Budget(BigDecimal monthLimitUsd, int warnPercent) {
    this.monthLimitUsd = monthLimitUsd;
    this.warnPercent = warnPercent;
  }

  public Long getId() {
    return id;
  }

  public BigDecimal getMonthLimitUsd() {
    return monthLimitUsd;
  }

  public int getWarnPercent() {
    return warnPercent;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
