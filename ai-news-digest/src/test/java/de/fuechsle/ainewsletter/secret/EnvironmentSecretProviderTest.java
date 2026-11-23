package de.fuechsle.ainewsletter.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EnvironmentSecretProviderTest {

  @Test
  void variableNameIsDerivedFromTheSecretName() {
    assertThat(EnvironmentSecretProvider.variableName("newsdigest/prod/ai/anthropic"))
        .isEqualTo("NEWSDIGEST_PROD_AI_ANTHROPIC");
    assertThat(EnvironmentSecretProvider.variableName("newsdigest/prod/mail.smtp-2"))
        .isEqualTo("NEWSDIGEST_PROD_MAIL_SMTP_2");
  }

  @Test
  void returnsTheTrimmedValue() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("NEWSDIGEST_PROD_AI_ANTHROPIC", " test-key \n");

    assertThat(new EnvironmentSecretProvider(environment).getSecret("newsdigest/prod/ai/anthropic"))
        .isEqualTo("test-key");
  }

  @Test
  void missingOrBlankSecretIsReportedWithoutValue() {
    MockEnvironment environment = new MockEnvironment().withProperty("NEWSDIGEST_BLANK", "  ");
    EnvironmentSecretProvider provider = new EnvironmentSecretProvider(environment);

    assertThatThrownBy(() -> provider.getSecret("newsdigest/missing"))
        .isInstanceOf(SecretNotFoundException.class)
        .hasMessageContaining("newsdigest/missing")
        .hasMessageContaining("NEWSDIGEST_MISSING");
    assertThatThrownBy(() -> provider.getSecret("newsdigest/blank"))
        .isInstanceOf(SecretNotFoundException.class);
  }
}
