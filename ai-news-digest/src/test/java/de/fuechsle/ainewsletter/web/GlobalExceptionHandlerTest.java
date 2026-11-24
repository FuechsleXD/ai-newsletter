package de.fuechsle.ainewsletter.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.service.RunAlreadyActiveException;
import de.fuechsle.ainewsletter.service.TopicNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void businessExceptionsBecomeProblemsWithTheirMessage() {
    ProblemDetail notFound = handler.notFound(new TopicNotFoundException(7));
    ProblemDetail conflict = handler.conflict(new RunAlreadyActiveException(7));

    assertThat(notFound.getStatus()).isEqualTo(404);
    assertThat(notFound.getDetail()).isEqualTo("Topic 7 existiert nicht");
    assertThat(conflict.getStatus()).isEqualTo(409);
    assertThat(conflict.getTitle()).isEqualTo("Konflikt");
  }

  @Test
  void unexpectedErrorsHideTheirDetails() {
    ProblemDetail problem =
        handler.unexpected(new IllegalStateException("SQL state 42P01 at table secret_stuff"));

    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo("Die Anfrage ist fehlgeschlagen.");
    assertThat(problem.toString()).doesNotContain("secret_stuff");
  }
}
