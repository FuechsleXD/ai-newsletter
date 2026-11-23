package de.fuechsle.ainewsletter.secret;

/**
 * Resolves secrets (API keys, credentials) by their name, e.g. {@code
 * newsdigest/prod/ai/anthropic}. The database only stores secret names, never values
 * (docs/06-security.md).
 *
 * <p>Implementations must never log secret values.
 */
public interface SecretProvider {

  /**
   * Returns the value of the secret.
   *
   * @throws SecretNotFoundException if the secret does not exist or is empty
   */
  String getSecret(String name);
}
