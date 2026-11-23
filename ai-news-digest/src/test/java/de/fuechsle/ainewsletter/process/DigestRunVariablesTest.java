package de.fuechsle.ainewsletter.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DigestRunVariablesTest {

  private final DigestRunVariables variables = new DigestRunVariables();

  @Test
  void modelIdsSurviveTheRoundTrip() {
    String encoded = DigestRunVariables.encodeModelIds(List.of(3L, 17L, 42L));

    assertThat(encoded).isEqualTo("3,17,42");
    assertThat(variables.modelIds(encoded)).containsExactly(3L, 17L, 42L);
  }

  @Test
  void noModelsIsAnEmptyCollection() {
    assertThat(DigestRunVariables.encodeModelIds(List.of())).isEmpty();
    assertThat(variables.modelIds("")).isEmpty();
    assertThat(variables.modelIds(null)).isEmpty();
    assertThat(variables.modelIds(" 1 , 2 ")).containsExactly(1L, 2L);
  }
}
