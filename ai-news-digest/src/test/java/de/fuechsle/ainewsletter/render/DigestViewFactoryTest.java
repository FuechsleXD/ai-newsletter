package de.fuechsle.ainewsletter.render;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.render.DigestView.SectionView;
import de.fuechsle.ainewsletter.render.DigestView.SourceView;
import org.junit.jupiter.api.Test;

class DigestViewFactoryTest {

  @Test
  void numbersSectionsAndSourcesAndCountsModels() {
    DigestView view = RenderTestData.view("normal");

    assertThat(view.topicName()).isEqualTo("Java");
    assertThat(view.period()).isEqualTo("16.11.2026 – 22.11.2026");
    assertThat(view.createdOn()).isEqualTo("23.11.2026");
    assertThat(view.sections()).extracting(SectionView::number).containsExactly(1, 2, 3, 4, 5);
    assertThat(view.sections())
        .extracting(SectionView::color)
        .containsExactly("blue", "green", "purple", "orange", "red");
    assertThat(view.sections().getFirst().colorHex()).isEqualTo("#3b82f6");
    assertThat(view.sources()).extracting(SourceView::number).containsExactly(1, 2, 3, 4, 5, 6);
    assertThat(view.sources().getFirst().domain()).isEqualTo("news1.example.test");
    // Section 1 refers to the news items 1 and 2
    assertThat(view.sections().getFirst().sourceNumbers()).containsExactly(1, 2);
    assertThat(view.modelCount()).isEqualTo(2);
  }

  @Test
  void sectionOfOnlyOneModelIsUnconfirmed() {
    DigestView view = RenderTestData.view("normal");

    assertThat(view.sections().get(0).models()).containsExactly("Claude Test A");
    assertThat(view.sections().get(0).unconfirmed()).isTrue();
    assertThat(view.sections().get(1).unconfirmed()).isFalse();
  }

  @Test
  void textsAreShortenedToTheLimits() {
    DigestView view = RenderTestData.view("long");

    assertThat(view.title()).hasSizeLessThanOrEqualTo(60).endsWith("…");
    assertThat(view.subtitle()).hasSizeLessThanOrEqualTo(60);
    assertThat(view.summary()).hasSizeLessThanOrEqualTo(320).endsWith("…");
    assertThat(view.takeaways()).allSatisfy(text -> assertThat(text).hasSizeLessThanOrEqualTo(320));
    assertThat(view.watchOut()).hasSizeLessThanOrEqualTo(320);
    assertThat(view.keyPoints())
        .allSatisfy(point -> assertThat(point.text()).hasSizeLessThanOrEqualTo(60));
    assertThat(view.sections())
        .allSatisfy(section -> {
          assertThat(section.heading()).hasSizeLessThanOrEqualTo(60);
          assertThat(section.text()).hasSizeLessThanOrEqualTo(320);
        });
  }

  @Test
  void domainWithoutWwwAndInvalidUrlStaysAsItIs() {
    assertThat(DigestViewFactory.domain("https://www.example.test/a")).isEqualTo("example.test");
    assertThat(DigestViewFactory.domain("kein url")).isEqualTo("kein url");
    assertThat(DigestViewFactory.domain("mailto:x")).isEqualTo("mailto:x");
  }
}
