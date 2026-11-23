package de.fuechsle.ainewsletter.research;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UrlNormalizerTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          HTTPS://Example.TEST/Blog/Post                         | https://example.test/Blog/Post
          https://example.test/blog/post/                        | https://example.test/blog/post
          https://example.test/                                  | https://example.test
          https://example.test/a#section-2                       | https://example.test/a
          https://example.test:443/a                             | https://example.test/a
          http://example.test:80/a                               | http://example.test/a
          http://example.test:8080/a                             | http://example.test:8080/a
          https://example.test/a?utm_source=x&id=7&UTM_Medium=y   | https://example.test/a?id=7
          https://example.test/a?fbclid=abc&gclid=def            | https://example.test/a
          https://example.test/a?b=2&a=1                         | https://example.test/a?b=2&a=1
          https://example.test/a?&q=java&                        | https://example.test/a?q=java
          https://user@example.test/a                            | https://user@example.test/a
          '  https://example.test/a  '                           | https://example.test/a
          """)
  void normalizesForDuplicateChecks(String url, String expected) {
    assertThat(UrlNormalizer.normalize(url)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"not a url", "mailto:someone", "/relative/path"})
  void invalidOrRelativeUrlsStayUnchanged(String url) {
    assertThat(UrlNormalizer.normalize(url)).isEqualTo(url);
  }
}
