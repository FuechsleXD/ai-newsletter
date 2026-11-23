package de.fuechsle.ainewsletter.prompt;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Loads prompt templates from {@code classpath:prompts/<name>/<version>.txt}. The active version of
 * each prompt comes from {@link NewsDigestProperties.Prompts}; existing versions are never changed
 * (golden rule 6), so templates are cached after the first load.
 */
@Component
public class PromptTemplates {

  public static final String RESEARCH = "research";
  public static final String AGGREGATE = "aggregate";
  public static final String REPAIR_JSON = "repair-json";

  private static final Pattern SAFE_SEGMENT = Pattern.compile("[a-z0-9-]+");

  private final NewsDigestProperties.Prompts activeVersions;
  private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

  public PromptTemplates(NewsDigestProperties properties) {
    this.activeVersions = properties.prompts();
  }

  /** The active version of the research prompt. */
  public PromptTemplate research() {
    return load(RESEARCH, activeVersions.research());
  }

  /** The active version of the aggregate prompt. */
  public PromptTemplate aggregate() {
    return load(AGGREGATE, activeVersions.aggregate());
  }

  /** The active version of the JSON repair prompt. */
  public PromptTemplate repairJson() {
    return load(REPAIR_JSON, activeVersions.repairJson());
  }

  /** Loads a specific version, e.g. for evaluating prompt versions against each other. */
  public PromptTemplate load(String name, String version) {
    if (!SAFE_SEGMENT.matcher(name).matches() || !SAFE_SEGMENT.matcher(version).matches()) {
      throw new PromptException("Invalid prompt name or version: " + name + "/" + version);
    }
    return cache.computeIfAbsent(name + "/" + version, key -> read(name, version));
  }

  private PromptTemplate read(String name, String version) {
    String location = "prompts/" + name + "/" + version + ".txt";
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(location)) {
      if (in == null) {
        throw new PromptException("Prompt template not found: " + location);
      }
      return new PromptTemplate(
          name, version, new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new PromptException("Cannot read prompt template " + location, e);
    }
  }
}
