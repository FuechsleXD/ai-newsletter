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
import java.time.LocalDate;

/** News item reported by a research model (untrusted content). */
@Entity
@Table(name = "news_item")
public class NewsItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "provider_result_id", nullable = false)
  private ProviderResult providerResult;

  @Column(name = "headline", nullable = false, columnDefinition = "text")
  private String headline;

  @Column(name = "summary", nullable = false, columnDefinition = "text")
  private String summary;

  @Column(name = "url", nullable = false, length = 2048)
  private String url;

  @Column(name = "normalized_url", nullable = false, length = 2048)
  private String normalizedUrl;

  /** Publication date as reported by the model; NULL = unknown. */
  @Column(name = "published_at")
  private LocalDate publishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private NewsItemStatus status;

  protected NewsItem() {}

  public NewsItem(
      ProviderResult providerResult,
      String headline,
      String summary,
      String url,
      String normalizedUrl,
      LocalDate publishedAt,
      NewsItemStatus status) {
    this.providerResult = providerResult;
    this.headline = headline;
    this.summary = summary;
    this.url = url;
    this.normalizedUrl = normalizedUrl;
    this.publishedAt = publishedAt;
    this.status = status;
  }

  public Long getId() {
    return id;
  }

  public ProviderResult getProviderResult() {
    return providerResult;
  }

  public String getHeadline() {
    return headline;
  }

  public String getSummary() {
    return summary;
  }

  public String getUrl() {
    return url;
  }

  public String getNormalizedUrl() {
    return normalizedUrl;
  }

  public LocalDate getPublishedAt() {
    return publishedAt;
  }

  public NewsItemStatus getStatus() {
    return status;
  }

  public void setStatus(NewsItemStatus status) {
    this.status = status;
  }
}
