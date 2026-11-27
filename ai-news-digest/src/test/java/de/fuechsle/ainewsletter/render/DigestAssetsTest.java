package de.fuechsle.ainewsletter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.aggregate.IconKeys;
import java.util.List;
import org.junit.jupiter.api.Test;

class DigestAssetsTest {

  private final IconKeys iconKeys = new IconKeys();
  private final DigestAssets assets = new DigestAssets(iconKeys);

  @Test
  void everyAllowedIconKeyHasItsOwnSvg() {
    for (String key : iconKeys.keys()) {
      assertThat(assets.icon(key)).startsWith("<svg aria-hidden=\"true\"").endsWith("</svg>");
    }
    assertThat(iconKeys.keys().stream().map(assets::icon).distinct())
        .hasSameSizeAs(iconKeys.keys());
  }

  @Test
  void unknownIconKeyGetsTheDefaultIcon() {
    assertThat(assets.icon("does-not-exist")).isEqualTo(assets.icon(IconKeys.DEFAULT));
    assertThat(assets.icon(null)).isEqualTo(assets.icon(IconKeys.DEFAULT));
  }

  @Test
  void svgsAreSizedByTheLayout() {
    assertThat(assets.icon("ai")).doesNotContain("width=\"24\"", "height=\"24\"", "\n");
    assertThat(assets.ui("check")).contains("viewBox=\"0 0 24 24\"");
    assertThatThrownBy(() -> assets.ui("unknown")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fontsAreEmbeddedAsBase64InAllWeights() {
    assertThat(assets.fontFaces())
        .contains("font-weight:400;", "font-weight:600;", "font-weight:700;", "font-weight:800;")
        .contains("url(data:font/woff2;base64,d09GMg");
  }

  @Test
  void missingIconStopsTheStart() {
    IconKeys withUnknownKey = new IconKeys(List.of(IconKeys.DEFAULT, "no-such-icon"));

    assertThatThrownBy(() -> new DigestAssets(withUnknownKey))
        .hasMessageContaining("pdf/icons/no-such-icon.svg");
  }
}
