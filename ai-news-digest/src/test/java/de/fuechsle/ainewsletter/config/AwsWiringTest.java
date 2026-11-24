package de.fuechsle.ainewsletter.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.secret.AwsSecretsManagerSecretProvider;
import de.fuechsle.ainewsletter.secret.EnvironmentSecretProvider;
import de.fuechsle.ainewsletter.secret.SecretProvider;
import de.fuechsle.ainewsletter.storage.DigestStorage;
import de.fuechsle.ainewsletter.storage.LocalDigestStorage;
import de.fuechsle.ainewsletter.storage.S3DigestStorage;
import de.fuechsle.ainewsletter.support.PostgresTestSupport;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Which secret source and storage the app uses (ADR-015). Building the SDK clients does not call
 * AWS; nothing here talks to a real service.
 */
class AwsWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              Properties.class,
              ClockConfig.class,
              AwsClientConfig.class,
              EnvironmentSecretProvider.class,
              AwsSecretsManagerSecretProvider.class,
              LocalDigestStorage.class,
              S3DigestStorage.class)
          .withPropertyValues(
              "newsdigest.mail.from=" + PostgresTestSupport.MAIL_FROM,
              "newsdigest.mail.operator=" + PostgresTestSupport.MAIL_OPERATOR);

  @Test
  void defaultUsesTheEnvironmentAndTheLocalDirectoryWithoutAwsClients() {
    runner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context.getBean(SecretProvider.class))
          .isInstanceOf(EnvironmentSecretProvider.class);
      assertThat(context.getBean(DigestStorage.class)).isInstanceOf(LocalDigestStorage.class);
      assertThat(context).doesNotHaveBean(SecretsManagerClient.class);
      assertThat(context).doesNotHaveBean(S3Client.class);
    });
  }

  @Test
  void awsSettingsUseSecretsManagerAndS3() {
    runner
        .withPropertyValues(
            "newsdigest.secrets.source=aws",
            "newsdigest.storage.type=s3",
            "newsdigest.storage.s3-bucket=newsdigest-test-pdfs",
            "newsdigest.aws.region=eu-central-1")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(SecretProvider.class))
              .isInstanceOf(AwsSecretsManagerSecretProvider.class);
          assertThat(context.getBean(DigestStorage.class)).isInstanceOf(S3DigestStorage.class);
          assertThat(context.getBean(S3Client.class).serviceClientConfiguration().region())
              .hasToString("eu-central-1");
        });
  }

  @Test
  void s3WithoutBucketDoesNotStart() {
    runner
        .withPropertyValues("newsdigest.storage.type=s3")
        .run(context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("s3-bucket must be set"));
  }

  @Test
  void profileProdSwitchesToAws() throws IOException {
    PropertySource<?> prod =
        new YamlPropertySourceLoader()
            .load("prod", new ClassPathResource("application-prod.yml"))
            .getFirst();

    assertThat(prod.getProperty("newsdigest.secrets.source")).isEqualTo("aws");
    assertThat(prod.getProperty("newsdigest.storage.type")).isEqualTo("s3");
    assertThat(prod.getProperty("spring.mail.properties.mail.smtp.starttls.required"))
        .isEqualTo(true);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(NewsDigestProperties.class)
  static class Properties {}
}
