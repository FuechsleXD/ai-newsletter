package de.fuechsle.ainewsletter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecipientTest {

  @Test
  void emailIsTrimmedAndLowerCased() {
    assertThat(new Recipient(" Someone@Example.TEST ").getEmail())
        .isEqualTo("someone@example.test");
  }

  @Test
  void missingEmailStaysMissingSoValidationCanReportIt() {
    Recipient recipient = new Recipient("someone@example.test");
    recipient.setEmail(null);

    assertThat(recipient.getEmail()).isNull();
  }
}
