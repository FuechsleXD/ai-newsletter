package de.fuechsle.ainewsletter.render;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate;
import de.fuechsle.ainewsletter.render.DigestView.SectionView;
import de.fuechsle.ainewsletter.render.DigestView.SourceView;
import org.junit.jupiter.api.Test;

class DigestViewFactoryTest {

  @Test
  void numbersSectionsAndSourcesAndCountsModels() {
    DigestView view = RenderTestData.view("normal");

    assertThat(view.topicName()).isEqualTo("Java");
    assertThat(view.period()).isEqualTo("Nov 16, 2026 – Nov 22, 2026");
    assertThat(view.createdOn()).isEqualTo("Nov 23, 2026");
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
  void everyModelKeepsItsDotColorAcrossTheCards() {
    DigestView view = RenderTestData.view("normal");

    assertThat(view.models())
        .containsExactly(
            new DigestView.ModelBadge("Claude Test A", "#2563eb"),
            new DigestView.ModelBadge("Claude Test B", "#16a34a"));
    assertThat(view.sections().get(1).modelBadges()).isEqualTo(view.models());
    assertThat(view.sections().getFirst().lightHex()).isEqualTo("#eff6ff");
    assertThat(view.sections().getFirst().darkHex()).isEqualTo("#1d4ed8");
  }

  @Test
  void lengthFactorShortensTextsForTheSecondAttemptButNotBelowTheMinimum() {
    DigestAggregate aggregate = RenderTestData.aggregate("long");
    DigestLanguage language = RenderTestData.language("en");

    DigestView full = RenderTestData.view(aggregate, language, 1.0);
    DigestView shorter = RenderTestData.view(aggregate, language, 0.7);
    DigestView tiny = RenderTestData.view(aggregate, language, 0.01);

    assertThat(full.summary()).hasSize(320);
    assertThat(shorter.summary()).hasSizeLessThanOrEqualTo(224).endsWith(TextShortener.ELLIPSIS);
    assertThat(shorter.sections().getFirst().heading()).hasSizeLessThanOrEqualTo(42);
    assertThat(tiny.summary()).hasSizeLessThanOrEqualTo(DigestViewFactory.MIN_TEXT_CHARS);
    assertThat(tiny.title()).hasSizeLessThanOrEqualTo(DigestViewFactory.MIN_HEADING_CHARS);
    assertThat(tiny.title()).hasSizeGreaterThan(DigestViewFactory.MIN_HEADING_CHARS / 2);
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
