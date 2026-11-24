package de.fuechsle.ainewsletter.web;

import de.fuechsle.ainewsletter.service.ConflictException;
import de.fuechsle.ainewsletter.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central error handling of the REST API (golden rule 8): every error is an RFC 9457 {@link
 * ProblemDetail}. Business exceptions are thrown in services and mapped here by their base class;
 * everything unexpected becomes a 500 with a generic message, details only in the log. Validation
 * errors, {@code traceId} and the further cases of docs/05-api.md follow with the API in roadmap
 * step 19.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "Nicht gefunden", e.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  ProblemDetail conflict(ConflictException e) {
    return problem(HttpStatus.CONFLICT, "Konflikt", e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception e) {
    log.error("Unexpected error in a REST request", e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "Interner Fehler", "Die Anfrage ist fehlgeschlagen.");
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
