package de.fuechsle.ainewsletter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * AWS SDK clients, only created when the app uses AWS (profile {@code prod}, ADR-015). Credentials
 * come from the SDK default chain, on the EC2 instance from the instance role; there are no keys in
 * the configuration.
 */
@Configuration(proxyBeanMethods = false)
public class AwsClientConfig {

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "newsdigest.secrets.source", havingValue = "aws")
  SecretsManagerClient secretsManagerClient(NewsDigestProperties properties) {
    return SecretsManagerClient.builder()
        .region(Region.of(properties.aws().region()))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .build();
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "newsdigest.storage.type", havingValue = "s3")
  S3Client s3Client(NewsDigestProperties properties) {
    return S3Client.builder()
        .region(Region.of(properties.aws().region()))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .build();
  }
}
