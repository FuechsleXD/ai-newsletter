package de.fuechsle.ainewsletter.provider;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Maps {@code ai_provider.code} to the {@link NewsProvider} implementation. Every provider it
 * returns runs its calls through the {@link ProviderCallLimiter}, so no call bypasses the limit.
 */
@Component
public class NewsProviderRegistry {

  private final Map<String, NewsProvider> providers;

  @Autowired
  public NewsProviderRegistry(List<NewsProvider> providers, ProviderCallLimiter limiter) {
    this.providers =
        providers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    NewsProvider::code, provider -> new LimitedNewsProvider(provider, limiter)));
  }

  /** Without a call limit, for tests that build services by hand. */
  public NewsProviderRegistry(List<NewsProvider> providers) {
    this(providers, ProviderCallLimiter.unlimited());
  }

  /**
   * Returns the implementation for a provider code.
   *
   * @throws ProviderException if no implementation exists for the code
   */
  public NewsProvider get(String providerCode) {
    NewsProvider provider = providers.get(providerCode);
    if (provider == null) {
      throw new ProviderException("No implementation for provider " + providerCode);
    }
    return provider;
  }

  /** A provider whose calls take a slot of the limiter first. */
  private record LimitedNewsProvider(NewsProvider provider, ProviderCallLimiter limiter)
      implements NewsProvider {

    @Override
    public String code() {
      return provider.code();
    }

    @Override
    public ModelResponse call(ModelInvocation invocation) {
      return limiter.call(provider.code(), () -> provider.call(invocation));
    }
  }
}
