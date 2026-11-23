package de.fuechsle.ainewsletter.service;

import java.util.List;

/**
 * What the process needs to know about a newly created run.
 *
 * @param modelIds research models (active models of active providers), in a stable order
 */
public record PreparedRun(
    long runId, RunPeriod period, List<Long> modelIds, long aggregatorModelId) {

  public PreparedRun {
    modelIds = List.copyOf(modelIds);
  }
}
