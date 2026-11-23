package de.fuechsle.ainewsletter.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reads files from {@code src/test/resources/fixtures/}. */
public final class Fixtures {

  private Fixtures() {}

  public static String read(String path) {
    String location = "fixtures/" + path;
    try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream(location)) {
      if (in == null) {
        throw new IllegalArgumentException("Fixture not found: " + location);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
