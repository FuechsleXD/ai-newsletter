package de.fuechsle.ainewsletter.provider;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * One call to a model. Everything that selects or tunes the model comes from the database row of
 * the model ({@code ai_model}) and its provider ({@code ai_provider}), never from code.
 *
 * @param providerCode provider code, e.g. {@code ANTHROPIC} (selects the {@link NewsProvider})
 * @param secretName name of the API key secret, never the key itself
 * @param modelName exact model ID of the provider
 * @param maxOutputTokens upper limit for the answer
 * @param temperature sampling temperature or {@code null} for the provider default; not every model
 *     accepts it
 * @param webSearch whether the provider's web search may be used
 * @param maxWebSearches upper limit of web searches in this call (cost guard)
 * @param prompt rendered prompt
 * @param timeout timeout of this call
 */
public record ModelInvocation(
    String providerCode,
    String secretName,
    String modelName,
    int maxOutputTokens,
    BigDecimal temperature,
    boolean webSearch,
    int maxWebSearches,
    String prompt,
    Duration timeout) {

  public ModelInvocation {
    Objects.requireNonNull(providerCode, "providerCode");
    Objects.requireNonNull(secretName, "secretName");
    Objects.requireNonNull(modelName, "modelName");
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(timeout, "timeout");
  }

  /** Same call with another prompt, e.g. for the JSON repair attempt. */
  public ModelInvocation withPrompt(String newPrompt) {
    return new ModelInvocation(
        providerCode,
        secretName,
        modelName,
        maxOutputTokens,
        temperature,
        webSearch,
        maxWebSearches,
        newPrompt,
        timeout);
  }

  /** Same call without web search, e.g. for the JSON repair attempt. */
  public ModelInvocation withoutWebSearch() {
    return new ModelInvocation(
        providerCode,
        secretName,
        modelName,
        maxOutputTokens,
        temperature,
        false,
        0,
        prompt,
        timeout);
  }
}
