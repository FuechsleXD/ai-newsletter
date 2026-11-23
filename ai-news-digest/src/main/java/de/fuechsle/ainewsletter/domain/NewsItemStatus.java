package de.fuechsle.ainewsletter.domain;

/** Result of checking a news item (docs/02-architektur.md, "Prüfung der Meldungen"). */
public enum NewsItemStatus {
  VALID,
  OUT_OF_PERIOD,
  DUPLICATE_PREVIOUS,
  DUPLICATE_RUN,
  UNREACHABLE
}
