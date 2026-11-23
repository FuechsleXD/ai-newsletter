package de.fuechsle.ainewsletter.render;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Renders HTML to PDF with Gotenberg (ADR-008), route {@code /forms/chromium/convert/html}. Only
 * the HTML page is sent; it contains everything it needs, so Chromium loads nothing from the
 * network (Gotenberg also denies it, see docker-compose.yml). Paper size, margins and backgrounds
 * come from the CSS ({@code @page}).
 */
@Component
public class GotenbergClient {

  static final String CONVERT_HTML = "/forms/chromium/convert/html";

  private final RestClient restClient;

  public GotenbergClient(NewsDigestProperties properties) {
    NewsDigestProperties.Render render = properties.render();
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(render.gotenbergTimeout()).build());
    requestFactory.setReadTimeout(render.gotenbergTimeout());
    this.restClient =
        RestClient.builder().baseUrl(render.gotenbergUrl()).requestFactory(requestFactory).build();
  }

  public byte[] htmlToPdf(String html) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("files", new NamedResource(html.getBytes(StandardCharsets.UTF_8), "index.html"));
    form.add("preferCssPageSize", "true");
    form.add("printBackground", "true");
    try {
      byte[] pdf =
          restClient
              .post()
              .uri(CONVERT_HTML)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(form)
              .retrieve()
              .body(byte[].class);
      if (pdf == null || pdf.length == 0) {
        throw new GotenbergException("Gotenberg returned no PDF", null);
      }
      return pdf;
    } catch (RestClientException e) {
      throw new GotenbergException("PDF rendering with Gotenberg failed: " + e.getMessage(), e);
    }
  }

  /** A multipart file part needs a file name; Gotenberg expects {@code index.html}. */
  private static final class NamedResource extends ByteArrayResource {

    private final String filename;

    private NamedResource(byte[] content, String filename) {
      super(content);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }
}
