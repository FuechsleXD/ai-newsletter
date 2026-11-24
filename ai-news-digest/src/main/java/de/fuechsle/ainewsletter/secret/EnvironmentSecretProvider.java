package de.fuechsle.ainewsletter.secret;

import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Reads secrets from the environment (default, {@code newsdigest.secrets.source=environment}); in
 * AWS {@link AwsSecretsManagerSecretProvider} is used instead (ADR-015).
 *
 * <p>The variable name is derived from the secret name: upper case, every character that is not a
 * letter or digit becomes {@code _}. Example: {@code newsdigest/prod/ai/anthropic} is read from
 * {@code NEWSDIGEST_PROD_AI_ANTHROPIC}. Locally the value comes from the git-ignored {@code .env}.
 */
@Component
@ConditionalOnProperty(
    name = "newsdigest.secrets.source",
    havingValue = "environment",
    matchIfMissing = true)
public class EnvironmentSecretProvider implements SecretProvider {

  private final Environment environment;

  public EnvironmentSecretProvider(Environment environment) {
    this.environment = environment;
  }

  @Override
  public String getSecret(String name) {
    String variable = variableName(name);
    String value = environment.getProperty(variable);
    if (value == null || value.isBlank()) {
      throw new SecretNotFoundException(
          "Secret '" + name + "' is not set (environment variable " + variable + ")");
    }
    return value.trim();
  }

  static String variableName(String secretName) {
    return secretName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
  }
}
