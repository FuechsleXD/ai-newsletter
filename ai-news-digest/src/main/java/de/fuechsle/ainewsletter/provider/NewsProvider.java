package de.fuechsle.ainewsletter.provider;

/**
 * Connection to one AI provider. A new provider is a new implementation plus a row in {@code
 * ai_provider} with the same code; process and API stay unchanged (docs/02-architektur.md).
 */
public interface NewsProvider {

  /** Provider code as stored in {@code ai_provider.code}, e.g. {@code ANTHROPIC}. */
  String code();

  /**
   * Calls the model described by the invocation.
   *
   * @throws ProviderException if the call fails
   */
  ModelResponse call(ModelInvocation invocation);
}
