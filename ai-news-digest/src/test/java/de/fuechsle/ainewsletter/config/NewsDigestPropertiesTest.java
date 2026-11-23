package de.fuechsle.ainewsletter.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Period;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class NewsDigestPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(PropertiesConfig.class);

  @Test
  void bindsDocumentedDefaultsWithoutAnyConfiguration() {
    runner.run(context -> {
      assertThat(context).hasNotFailed();
      NewsDigestProperties p = context.getBean(NewsDigestProperties.class);

      assertThat(p.run().initialLookbackDays()).isEqualTo(7);
      assertThat(p.run().configReloadInterval()).isEqualTo(Duration.ofSeconds(60));
      assertThat(p.run().maxManualRunsPerTopicPerHour()).isEqualTo(3);

      assertThat(p.ai().defaultProviderCode()).isEqualTo("ANTHROPIC");
      assertThat(p.ai().aggregatorModelId()).isNull();
      assertThat(p.ai().callTimeout()).isEqualTo(Duration.ofSeconds(120));
      assertThat(p.ai().maxConcurrentCallsPerProvider()).isEqualTo(2);
      assertThat(p.ai().maxItemsPerModel()).isEqualTo(10);
      assertThat(p.ai().jsonRepairAttempts()).isEqualTo(1);
      assertThat(p.ai().duplicateLookbackDays()).isEqualTo(90);
      assertThat(p.ai().passPreviousHeadlinesToAggregator()).isTrue();

      assertThat(p.auth().clockSkew()).isEqualTo(Duration.ofSeconds(30));
      assertThat(p.secrets().cacheTtl()).isEqualTo(Duration.ofMinutes(15));

      assertThat(p.urlCheck().timeout()).isEqualTo(Duration.ofSeconds(5));
      assertThat(p.urlCheck().maxRedirects()).isEqualTo(3);
      assertThat(p.urlCheck().maxConcurrentChecks()).isEqualTo(5);

      assertThat(p.budget().monthlyLimitUsd()).isEqualByComparingTo(BigDecimal.TEN);
      assertThat(p.budget().warnPercent()).isEqualTo(80);

      assertThat(p.process().externalTaskRetryCycle()).isEqualTo("R3/PT5M");
      assertThat(p.process().historyTimeToLive()).isEqualTo(Duration.ofDays(30));
      assertThat(p.process().jobLockTime()).isEqualTo(Duration.ofMinutes(10));
      assertThat(p.process().shutdownTimeout()).isEqualTo(Duration.ofMinutes(3));

      assertThat(p.render().gotenbergTimeout()).isEqualTo(Duration.ofSeconds(30));
      assertThat(p.render().shortenAttempts()).isEqualTo(1);

      assertThat(p.output().language()).isEqualTo(Locale.GERMAN);
      assertThat(p.output().minTopicCards()).isEqualTo(4);
      assertThat(p.output().maxTopicCards()).isEqualTo(6);
      assertThat(p.output().mailSubjectPattern()).isEqualTo("[News Digest] {topic} – {date}");

      assertThat(p.retention().rawResponse()).isEqualTo(Period.ofDays(90));
      assertThat(p.retention().runs()).isEqualTo(Period.ofYears(2));

      assertThat(p.api().defaultPageSize()).isEqualTo(20);
      assertThat(p.api().maxPageSize()).isEqualTo(100);
    });
  }

  @Test
  void valuesCanBeOverriddenByProperties() {
    runner
        .withPropertyValues(
            "newsdigest.budget.monthly-limit-usd=25.50",
            "newsdigest.ai.call-timeout=90s",
            "newsdigest.ai.aggregator-model-id=some-model",
            "newsdigest.run.initial-lookback-days=14")
        .run(context -> {
          NewsDigestProperties p = context.getBean(NewsDigestProperties.class);
          assertThat(p.budget().monthlyLimitUsd()).isEqualByComparingTo("25.50");
          assertThat(p.ai().callTimeout()).isEqualTo(Duration.ofSeconds(90));
          assertThat(p.ai().aggregatorModelId()).isEqualTo("some-model");
          assertThat(p.run().initialLookbackDays()).isEqualTo(14);
          // untouched values keep their defaults
          assertThat(p.budget().warnPercent()).isEqualTo(80);
        });
  }

  @Test
  void invalidValuesFailStartup() {
    runner
        .withPropertyValues("newsdigest.budget.warn-percent=101")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("newsdigest.ai.max-concurrent-calls-per-provider=0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(NewsDigestProperties.class)
  static class PropertiesConfig {}
}
