package de.fuechsle.ainewsletter.provider;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.prompt.PromptRenderer;
import de.fuechsle.ainewsletter.prompt.PromptTemplate;
import de.fuechsle.ainewsletter.prompt.PromptTemplates;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A model call whose answer must follow a JSON contract (research and aggregation): call the model,
 * parse the answer and, if it breaks the contract, make the configured number of repair attempts
 * with the same model (prompt {@code repair-json}, without web search). Usage and cost of all calls
 * are collected in the given {@link ModelCalls}.
 */
@Component
public class JsonModelCall {

  private static final Logger log = LoggerFactory.getLogger(JsonModelCall.class);

  static final String REFUSED = "Modell hat die Anfrage abgelehnt";
  static final String NO_CONTENT = "Modell hat keine Antwort geliefert (möglicherweise abgelehnt)";
  static final String INVALID_JSON = "Antwort ist kein gültiges JSON: ";

  private final PromptTemplates templates;
  private final PromptRenderer renderer;
  private final int repairAttempts;

  public JsonModelCall(
      PromptTemplates templates, PromptRenderer renderer, NewsDigestProperties properties) {
    this.templates = templates;
    this.renderer = renderer;
    this.repairAttempts = properties.ai().jsonRepairAttempts();
  }

  /**
   * @param template template the prompt of {@code invocation} was rendered from; its output section
   *     is shown again in the repair prompt
   * @param parser turns the answer text into the result; throws {@link AnswerParseException} if the
   *     answer breaks the contract
   */
  public <T> Outcome<T> call(
      ModelCalls calls,
      ModelInvocation invocation,
      PromptTemplate template,
      Function<String, T> parser) {
    ModelResponse response = calls.call(invocation);
    if (response == null) {
      return Outcome.failed(calls.lastError());
    }
    if (response.refused()) {
      return Outcome.failed(REFUSED);
    }
    if (response.noContent()) {
      return Outcome.failed(NO_CONTENT);
    }

    String parseError;
    try {
      return Outcome.succeeded(parser.apply(response.text()));
    } catch (AnswerParseException e) {
      parseError = e.getMessage();
    }

    for (int attempt = 1; attempt <= repairAttempts; attempt++) {
      log.info(
          "Answer of model {} is not valid ({}), repair attempt {}",
          calls.model().modelName(),
          parseError,
          attempt);
      String repairPrompt =
          renderer.render(
              templates.repairJson(),
              Map.of(
                  "parseError", parseError,
                  "expectedFormat", template.outputSection(),
                  "previousResponse", calls.lastText()));
      response = calls.call(invocation.withPrompt(repairPrompt).withoutWebSearch());
      if (response == null) {
        return Outcome.failed(calls.lastError());
      }
      try {
        return Outcome.succeeded(parser.apply(response.text()));
      } catch (AnswerParseException e) {
        parseError = e.getMessage();
      }
    }
    return Outcome.failed(INVALID_JSON + parseError);
  }

  /**
   * Result of the call: the parsed value or the reason it failed (German, shown to admins).
   *
   * @param value parsed answer, {@code null} if the call failed
   * @param errorMessage reason of the failure, {@code null} on success
   */
  public record Outcome<T>(T value, String errorMessage) {

    static <T> Outcome<T> succeeded(T value) {
      return new Outcome<>(value, null);
    }

    static <T> Outcome<T> failed(String errorMessage) {
      return new Outcome<>(null, errorMessage);
    }

    public boolean succeeded() {
      return value != null;
    }
  }
}
