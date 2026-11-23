package de.fuechsle.ainewsletter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class NewsProviderRegistryTest {

  @Test
  void findsTheImplementationByProviderCode() {
    NewsProvider anthropic = provider("ANTHROPIC");
    NewsProviderRegistry registry = new NewsProviderRegistry(List.of(anthropic, provider("OTHER")));

    assertThat(registry.get("ANTHROPIC")).isSameAs(anthropic);
  }

  @Test
  void unknownProviderIsAProviderError() {
    NewsProviderRegistry registry = new NewsProviderRegistry(List.of(provider("ANTHROPIC")));

    assertThatThrownBy(() -> registry.get("GEMINI"))
        .isInstanceOf(ProviderException.class)
        .hasMessageContaining("GEMINI");
  }

  private static NewsProvider provider(String code) {
    return new NewsProvider() {
      @Override
      public String code() {
        return code;
      }

      @Override
      public ModelResponse call(ModelInvocation invocation) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
