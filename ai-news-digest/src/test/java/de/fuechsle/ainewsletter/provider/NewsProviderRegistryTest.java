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

    // The registry wraps the implementation with the call limit, so the code is compared
    assertThat(registry.get("ANTHROPIC").code()).isEqualTo("ANTHROPIC");
    assertThat(registry.get("OTHER").code()).isEqualTo("OTHER");
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
