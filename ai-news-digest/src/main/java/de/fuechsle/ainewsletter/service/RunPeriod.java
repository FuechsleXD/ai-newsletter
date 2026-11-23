package de.fuechsle.ainewsletter.service;

import java.time.LocalDate;

/** Period of a run as dates in the time zone of the topic schedule, both inclusive. */
public record RunPeriod(LocalDate from, LocalDate to) {}
