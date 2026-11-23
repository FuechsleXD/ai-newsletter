package de.fuechsle.ainewsletter.process;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Names and encoding of the process variables of {@code digestRun} (docs/04-prozess-bpmn.md).
 *
 * <p>All variables are primitive values or strings, so no Java serialization is needed in the
 * engine: dates are ISO strings ({@code YYYY-MM-DD}) and the research models are a comma separated
 * list of model IDs. The bean is used in the BPMN as {@code ${digestRunVariables.modelIds(models)}}
 * to turn that list into the multi-instance collection.
 */
@Component("digestRunVariables")
public class DigestRunVariables {

  public static final String PROCESS_KEY = "digestRun";

  public static final String TOPIC_ID = "topicId";
  public static final String RUN_ID = "runId";
  public static final String PERIOD_FROM = "periodFrom";
  public static final String PERIOD_TO = "periodTo";
  public static final String MODELS = "models";
  public static final String MODEL = "model";
  public static final String AGGREGATOR_MODEL_ID = "aggregatorModelId";
  public static final String BUDGET_OK = "budgetOk";
  public static final String RESEARCH_OUTCOME = "researchOutcome";
  public static final String RUN_STATUS = "runStatus";
  public static final String NOTIFICATION_REASON = "notificationReason";

  /** Encodes model IDs for the variable {@code models}. */
  public static String encodeModelIds(List<Long> modelIds) {
    return modelIds.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  /** Decodes the variable {@code models}; an empty string means no research model. */
  public List<Long> modelIds(String models) {
    if (models == null || models.isBlank()) {
      return List.of();
    }
    return Arrays.stream(models.split(",")).map(String::strip).map(Long::valueOf).toList();
  }
}
