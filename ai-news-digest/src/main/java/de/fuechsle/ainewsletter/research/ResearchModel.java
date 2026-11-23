package de.fuechsle.ainewsletter.research;

import de.fuechsle.ainewsletter.cost.ModelPrices;
import de.fuechsle.ainewsletter.domain.AiModel;
import java.math.BigDecimal;

/**
 * Snapshot of a research model and its provider as needed for one call, taken from the database
 * rows so that no entity leaves the service layer.
 */
public record ResearchModel(
    Long modelId,
    String providerCode,
    String secretName,
    String modelName,
    Integer maxOutputTokens,
    BigDecimal temperature,
    boolean supportsWebSearch,
    ModelPrices prices) {

  /** Copies the relevant values of a model and its provider. */
  public static ResearchModel of(AiModel model) {
    return new ResearchModel(
        model.getId(),
        model.getProvider().getCode(),
        model.getProvider().getSecretName(),
        model.getModelName(),
        model.getMaxOutputTokens(),
        model.getTemperature(),
        model.isSupportsWebSearch(),
        new ModelPrices(
            model.getInputPricePerMtok(),
            model.getOutputPricePerMtok(),
            model.getPricePerSearchCall()));
  }
}
