package de.fuechsle.ainewsletter.research;

import static org.assertj.core.api.Assertions.assertThat;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import de.fuechsle.ainewsletter.cost.CostCalculator;
import de.fuechsle.ainewsletter.cost.ModelPrices;
import de.fuechsle.ainewsletter.domain.ProviderResultStatus;
import de.fuechsle.ainewsletter.prompt.PromptRenderer;
import de.fuechsle.ainewsletter.prompt.PromptTemplates;
import de.fuechsle.ainewsletter.provider.JsonModelCall;
import de.fuechsle.ainewsletter.provider.ModelInvocation;
import de.fuechsle.ainewsletter.provider.ModelResponse;
import de.fuechsle.ainewsletter.provider.ModelSnapshot;
import de.fuechsle.ainewsletter.provider.NewsProviderRegistry;
import de.fuechsle.ainewsletter.provider.ProviderException;
import de.fuechsle.ainewsletter.secret.SecretNotFoundException;
import de.fuechsle.ainewsletter.support.Fixtures;
import de.fuechsle.ainewsletter.support.ScriptedProvider;
import de.fuechsle.ainewsletter.support.TestProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ResearchServiceTest {

  // Test prices only, not real provider prices
  private static final ModelPrices PRICES =
      new ModelPrices(new BigDecimal("3"), new BigDecimal("15"), new BigDecimal("0.01"));

  private final ScriptedProvider provider = new ScriptedProvider();

  @Test
  void successfulResearchIsParsedAndCosted() {
    provider.answer(response(Fixtures.read("research/valid.json"), 10_000, 2_000, 3));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.SUCCEEDED);
    assertThat(result.items()).hasSize(2);
    assertThat(result.errorMessage()).isNull();
    assertThat(result.inputTokens()).isEqualTo(10_000);
    assertThat(result.outputTokens()).isEqualTo(2_000);
    assertThat(result.webSearches()).isEqualTo(3);
    // 0.03 + 0.03 + 0.03
    assertThat(result.costUsd()).isEqualTo(new BigDecimal("0.090000"));
    assertThat(result.duration()).isEqualTo(Duration.ofSeconds(2));
    assertThat(result.rawResponse()).contains("Spring Boot 4.1.3");
  }

  @Test
  void itemsThatBreakTheContractAreDroppedButTheResultSucceeds() {
    provider.answer(response(Fixtures.read("research/mixed-invalid-items.json"), 1, 1, 0));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.SUCCEEDED);
    assertThat(result.items()).hasSize(2);
  }

  @Test
  void invocationComesFromTheModelRowAndTheRenderedPrompt() {
    provider.answer(response("{\"items\": []}", 1, 1, 0));

    service().research(request(model(4_000, new BigDecimal("0.2"))));

    ModelInvocation invocation = provider.invocations().getFirst();
    assertThat(invocation.providerCode()).isEqualTo("TEST");
    assertThat(invocation.secretName()).isEqualTo("newsdigest/test/ai/test");
    assertThat(invocation.modelName()).isEqualTo("test-model-1");
    assertThat(invocation.maxOutputTokens()).isEqualTo(4_000);
    assertThat(invocation.temperature()).isEqualByComparingTo("0.2");
    assertThat(invocation.webSearch()).isTrue();
    assertThat(invocation.maxWebSearches()).isEqualTo(10);
    assertThat(invocation.timeout()).isEqualTo(Duration.ofSeconds(120));
    assertThat(invocation.prompt())
        .contains("TOPIC: Java")
        .contains("FOCUS: Releases und JEPs")
        .contains("PERIOD: 2026-11-16 to 2026-11-22 (time zone Europe/Berlin)")
        .contains("5 to 10 items")
        .contains("at most 60 words in English")
        .doesNotContain("{{");
  }

  @Test
  void defaultsApplyWhenTheModelRowLeavesValuesOpen() {
    provider.answer(response("{\"items\": []}", 1, 1, 0));
    ResearchRequest request =
        new ResearchRequest(
            "Java",
            "  ",
            LocalDate.of(2026, 11, 16),
            LocalDate.of(2026, 11, 22),
            ZoneId.of("UTC"),
            model(null, null));

    service().research(request);

    ModelInvocation invocation = provider.invocations().getFirst();
    assertThat(invocation.maxOutputTokens()).isEqualTo(16_000);
    assertThat(invocation.temperature()).isNull();
    assertThat(invocation.prompt()).contains("FOCUS: keiner");
  }

  @Test
  void invalidJsonIsRepairedOnceWithoutWebSearch() {
    provider.answer(response(Fixtures.read("research/invalid-json.txt"), 10_000, 2_000, 2));
    provider.answer(response(Fixtures.read("research/valid.json"), 3_000, 1_000, 0));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.SUCCEEDED);
    assertThat(result.items()).hasSize(2);
    assertThat(provider.invocations()).hasSize(2);
    ModelInvocation repair = provider.invocations().get(1);
    assertThat(repair.webSearch()).isFalse();
    assertThat(repair.modelName()).isEqualTo("test-model-1");
    assertThat(repair.prompt())
        .contains("ERROR: Invalid JSON")
        .contains("EXPECTED FORMAT:\nOUTPUT")
        .contains("Abgeschnittene Antwort");
    // Both calls are costed: (13,000 * 3 + 3,000 * 15) / 1M + 2 * 0.01
    assertThat(result.inputTokens()).isEqualTo(13_000);
    assertThat(result.outputTokens()).isEqualTo(3_000);
    assertThat(result.webSearches()).isEqualTo(2);
    assertThat(result.costUsd()).isEqualTo(new BigDecimal("0.104000"));
    assertThat(result.duration()).isEqualTo(Duration.ofSeconds(4));
  }

  @Test
  void failedRepairIsAFailedResultWithCost() {
    provider.answer(response("kein JSON", 1_000_000, 0, 0));
    provider.answer(response("immer noch kein JSON", 0, 1_000_000, 0));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(result.items()).isEmpty();
    assertThat(result.errorMessage()).startsWith("Antwort ist kein gültiges JSON");
    assertThat(result.rawResponse()).isEqualTo("immer noch kein JSON");
    assertThat(result.costUsd()).isEqualTo(new BigDecimal("18.000000"));
  }

  @Test
  void repairAttemptsCanBeSwitchedOff() {
    provider.answer(response("kein JSON", 1, 1, 0));
    NewsDigestProperties noRepair =
        TestProperties.with(Map.of("newsdigest.ai.json-repair-attempts", "0"));

    ResearchResult result = service(noRepair).research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(provider.invocations()).hasSize(1);
  }

  @Test
  void providerErrorIsAFailedResultWithoutCost() {
    provider.fail(new ProviderException("HTTP 529 overloaded"));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(result.errorMessage()).isEqualTo("HTTP 529 overloaded");
    assertThat(result.costUsd()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.rawResponse()).isNull();
  }

  @Test
  void missingApiKeyIsAFailedResult() {
    provider.fail(new SecretNotFoundException("Secret 'newsdigest/test/ai/test' is not set"));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(result.errorMessage()).contains("is not set");
  }

  @Test
  void errorDuringRepairKeepsTheCostOfTheFirstCall() {
    provider.answer(response("kein JSON", 1_000_000, 0, 1));
    provider.fail(new ProviderException("timeout"));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(result.errorMessage()).isEqualTo("timeout");
    assertThat(result.costUsd()).isEqualTo(new BigDecimal("3.010000"));
  }

  @Test
  void refusalIsAFailedResult() {
    provider.answer(new ModelResponse("", 500, 10, 0, "REFUSAL", Duration.ofSeconds(1)));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(result.errorMessage()).isEqualTo("Modell hat die Anfrage abgelehnt");
    assertThat(result.inputTokens()).isEqualTo(500);
    assertThat(provider.invocations()).hasSize(1);
  }

  @Test
  void answerWithoutContentIsAFailedResultWithoutRepair() {
    provider.answer(
        new ModelResponse("", 0, 0, 0, ModelResponse.NO_CONTENT, Duration.ofSeconds(1)));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(result.errorMessage()).contains("keine Antwort");
    assertThat(provider.invocations()).hasSize(1);
  }

  @Test
  void estimatedUsageIsCostedAndMarkedInTheError() {
    provider.answer(
        new ModelResponse(
            "", 100_000, 0, 0, ModelResponse.NO_CONTENT, Duration.ofSeconds(1), true));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.errorMessage()).contains("keine Antwort", "Kosten geschätzt");
    // 100,000 * 3 / 1M
    assertThat(result.costUsd()).isEqualByComparingTo("0.3");
  }

  @Test
  void pausedSearchIsAFailedResultWithCostAndWithoutRepair() {
    provider.answer(new ModelResponse("{\"items\": [", 1_000, 100, 5, "pause_turn", Duration.ZERO));

    ResearchResult result = service().research(request(model(null, null)));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(result.errorMessage()).contains("pause_turn");
    assertThat(result.webSearches()).isEqualTo(5);
    assertThat(provider.invocations()).hasSize(1);
  }

  @Test
  void modelWithoutWebSearchIsRejectedWithoutCall() {
    ModelSnapshot noSearch =
        new ModelSnapshot(
            1L, "TEST", "newsdigest/test/ai/test", "test-model-1", null, null, false, PRICES);

    ResearchResult result = service().research(request(noSearch));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(result.errorMessage()).contains("keine Websuche");
    assertThat(provider.invocations()).isEmpty();
  }

  @Test
  void modelWithoutSearchPriceIsRejectedWithoutCall() {
    ModelSnapshot noPrice =
        new ModelSnapshot(
            1L,
            "TEST",
            "newsdigest/test/ai/test",
            "test-model-1",
            null,
            null,
            true,
            new ModelPrices(BigDecimal.ONE, BigDecimal.ONE, null));

    ResearchResult result = service().research(request(noPrice));

    assertThat(result.status()).isEqualTo(ProviderResultStatus.FAILED);
    assertThat(result.errorMessage()).contains("kein Preis pro Websuche");
    assertThat(result.costUsd()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(provider.invocations()).isEmpty();
  }

  @Test
  void promptVersionIsTheActiveResearchVersion() {
    assertThat(service().promptVersion()).isEqualTo("v6");
  }

  @Test
  void requestRejectsReversedPeriod() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new ResearchRequest(
                "Java",
                null,
                LocalDate.of(2026, 11, 22),
                LocalDate.of(2026, 11, 16),
                ZoneId.of("UTC"),
                model(null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ResearchService service() {
    return service(TestProperties.defaults());
  }

  private ResearchService service(NewsDigestProperties properties) {
    PromptTemplates templates = new PromptTemplates(properties);
    PromptRenderer renderer = new PromptRenderer();
    return new ResearchService(
        new NewsProviderRegistry(List.of(provider)),
        templates,
        renderer,
        new JsonModelCall(templates, renderer, properties),
        new ResearchResponseParser(new JsonMapper()),
        new CostCalculator(),
        properties);
  }

  private static ModelSnapshot model(Integer maxTokens, BigDecimal temperature) {
    return new ModelSnapshot(
        1L,
        "TEST",
        "newsdigest/test/ai/test",
        "test-model-1",
        maxTokens,
        temperature,
        true,
        PRICES);
  }

  private static ResearchRequest request(ModelSnapshot model) {
    return new ResearchRequest(
        "Java",
        "Releases und JEPs",
        LocalDate.of(2026, 11, 16),
        LocalDate.of(2026, 11, 22),
        ZoneId.of("Europe/Berlin"),
        model);
  }

  private static ModelResponse response(String text, long in, long out, long searches) {
    return new ModelResponse(text, in, out, searches, "END_TURN", Duration.ofSeconds(2));
  }
}
