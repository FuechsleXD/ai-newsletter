package de.fuechsle.ainewsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Run state of a topic (1:1). */
@Entity
@Table(name = "topic_state")
public class TopicState {

  @Id
  @Column(name = "topic_id")
  private Long topicId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "topic_id")
  private Topic topic;

  /** {@code period_to} of the last run with status COMPLETED or NO_NEWS. */
  @Column(name = "last_successful_run_at")
  private LocalDate lastSuccessfulRunAt;

  protected TopicState() {}

  public TopicState(Topic topic) {
    this.topic = topic;
  }

  public Long getTopicId() {
    return topicId;
  }

  public Topic getTopic() {
    return topic;
  }

  public LocalDate getLastSuccessfulRunAt() {
    return lastSuccessfulRunAt;
  }

  public void setLastSuccessfulRunAt(LocalDate lastSuccessfulRunAt) {
    this.lastSuccessfulRunAt = lastSuccessfulRunAt;
  }
}
