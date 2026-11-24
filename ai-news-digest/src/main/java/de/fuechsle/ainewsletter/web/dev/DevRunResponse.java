package de.fuechsle.ainewsletter.web.dev;

/** Started run: ID of the {@code digest_run} row and of the process instance. */
public record DevRunResponse(long runId, String processInstanceId) {}
