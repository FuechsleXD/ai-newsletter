package de.fuechsle.ainewsletter.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Maps {@code ai_provider.code} to the {@link NewsProvider} implementation. */
@Component
public class NewsProviderRegistry {

  private final Map<String, NewsProvider> providers;

  public NewsProviderRegistry(List<NewsProvider> providers) {
    this.providers =
        providers.stream()
            .collect(Collectors.toUnmodifiableMap(NewsProvider::code, Function.identity()));
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
}
