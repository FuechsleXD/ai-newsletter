package de.fuechsle.ainewsletter.prompt;

/**
 * A versioned prompt template, e.g. {@code research/v1}.
 *
 * @param name prompt name (folder under {@code prompts/})
 * @param version version (file name without {@code .txt}), stored in {@code digest_run}
 * @param text template text with {@code {{placeholder}}} markers
 */
public record PromptTemplate(String name, String version, String text) {}
