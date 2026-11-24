package de.fuechsle.ainewsletter.secret;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * Reads secrets from AWS Secrets Manager with the instance role (profile {@code prod}, ADR-015).
 *
 * <p>Values are kept in memory for {@code newsdigest.secrets.cache-ttl}; after that they are read
 * again, so a rotated API key takes effect without a restart (docs/06-security.md). Values are
 * never logged or put into exception messages.
 */
@Component
@ConditionalOnProperty(name = "newsdigest.secrets.source", havingValue = "aws")
public class AwsSecretsManagerSecretProvider implements SecretProvider {

  private final SecretsManagerClient client;
  private final Clock clock;
  private final Duration cacheTtl;
  private final Map<String, CachedSecret> cache = new ConcurrentHashMap<>();

  public AwsSecretsManagerSecretProvider(
      SecretsManagerClient client, Clock clock, NewsDigestProperties properties) {
    this.client = client;
    this.clock = clock;
    this.cacheTtl = properties.secrets().cacheTtl();
  }

  @Override
  public String getSecret(String name) {
    Instant now = clock.instant();
    CachedSecret cached = cache.get(name);
    if (cached != null && now.isBefore(cached.expiresAt())) {
      return cached.value();
    }
    String value = read(name);
    cache.put(name, new CachedSecret(value, now.plus(cacheTtl)));
    return value;
  }

  private String read(String name) {
    String value;
    try {
      value =
          client
              .getSecretValue(GetSecretValueRequest.builder().secretId(name).build())
              .secretString();
    } catch (ResourceNotFoundException e) {
      throw new SecretNotFoundException("Secret '" + name + "' does not exist in Secrets Manager");
    } catch (SdkException e) {
      // SDK messages contain the error code and request ID, never the secret value
      throw new SecretNotFoundException(
          "Secret '" + name + "' cannot be read from Secrets Manager: " + e.getMessage(), e);
    }
    if (value == null || value.isBlank()) {
      throw new SecretNotFoundException("Secret '" + name + "' has no value in Secrets Manager");
    }
    return value.trim();
  }

  private record CachedSecret(String value, Instant expiresAt) {

    @Override
    public String toString() {
      return "CachedSecret[expiresAt=" + expiresAt + "]";
    }
  }
}
