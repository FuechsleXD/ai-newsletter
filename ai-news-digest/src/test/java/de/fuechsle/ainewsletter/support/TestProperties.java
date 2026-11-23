package de.fuechsle.ainewsletter.support;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** {@link NewsDigestProperties} for unit tests, bound exactly like in the application. */
public final class TestProperties {

  private TestProperties() {}

  public static NewsDigestProperties defaults() {
    return with(Map.of());
  }

  /** Defaults with single values overridden, e.g. {@code "newsdigest.ai.max-items-per-model"}. */
  public static NewsDigestProperties with(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate("newsdigest", NewsDigestProperties.class);
  }
}
