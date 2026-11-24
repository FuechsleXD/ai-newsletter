package de.fuechsle.ainewsletter.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fuechsle.ainewsletter.support.TestProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/** No real AWS calls: the SDK client is mocked. */
class AwsSecretsManagerSecretProviderTest {

  private static final String NAME = "newsdigest/test/ai/test";

  private final SecretsManagerClient client = mock(SecretsManagerClient.class);
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T10:00:00Z"));
  private final AwsSecretsManagerSecretProvider provider =
      new AwsSecretsManagerSecretProvider(
          client, clock, TestProperties.with(Map.of("newsdigest.secrets.cache-ttl", "15m")));

  @Test
  void readsTheSecretByItsNameAndTrimsIt() {
    answer(" test-key-1 \n");

    assertThat(provider.getSecret(NAME)).isEqualTo("test-key-1");

    ArgumentCaptor<GetSecretValueRequest> request =
        ArgumentCaptor.forClass(GetSecretValueRequest.class);
    verify(client).getSecretValue(request.capture());
    assertThat(request.getValue().secretId()).isEqualTo(NAME);
  }

  @Test
  void keepsTheValueForTheCacheTtlAndReadsItAgainAfterwards() {
    answer("old-key");
    assertThat(provider.getSecret(NAME)).isEqualTo("old-key");

    // A rotated key is only picked up after the TTL
    answer("rotated-key");
    clock.advance(Duration.ofMinutes(14));
    assertThat(provider.getSecret(NAME)).isEqualTo("old-key");

    clock.advance(Duration.ofMinutes(1));
    assertThat(provider.getSecret(NAME)).isEqualTo("rotated-key");
    verify(client, times(2)).getSecretValue(any(GetSecretValueRequest.class));
  }

  @Test
  void missingSecretFailsWithItsName() {
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(ResourceNotFoundException.builder().message("not found").build());

    assertThatThrownBy(() -> provider.getSecret(NAME))
        .isInstanceOf(SecretNotFoundException.class)
        .hasMessageContaining(NAME)
        .hasMessageContaining("does not exist");
  }

  @Test
  void emptySecretFailsWithoutCachingIt() {
    answer("  ");

    assertThatThrownBy(() -> provider.getSecret(NAME))
        .isInstanceOf(SecretNotFoundException.class)
        .hasMessageContaining("has no value");

    answer("now-set");
    assertThat(provider.getSecret(NAME)).isEqualTo("now-set");
  }

  @Test
  void sdkErrorFailsWithTheNameButNeverAValue() {
    answer("cached-value-must-not-leak");
    provider.getSecret(NAME);
    clock.advance(Duration.ofHours(1));
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(
            SecretsManagerException.builder()
                .message("AccessDeniedException")
                .statusCode(400)
                .build());

    assertThatThrownBy(() -> provider.getSecret(NAME))
        .isInstanceOf(SecretNotFoundException.class)
        .hasMessageContaining(NAME)
        .hasMessageContaining("AccessDeniedException")
        .hasMessageNotContaining("cached-value-must-not-leak");
  }

  private void answer(String value) {
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().name(NAME).secretString(value).build());
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
