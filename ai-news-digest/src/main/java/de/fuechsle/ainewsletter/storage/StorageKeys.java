package de.fuechsle.ainewsletter.storage;

import java.util.regex.Pattern;

/** Validation of storage keys, shared by all {@link DigestStorage} implementations. */
final class StorageKeys {

  /** Only simple keys like {@code runs/12/digest.pdf}: no path traversal, no special characters. */
  private static final Pattern SAFE_KEY = Pattern.compile("[a-z0-9-]+(/[a-z0-9-]+)*\\.[a-z]+");

  private StorageKeys() {}

  /**
   * @return the key if it is safe
   * @throws IllegalArgumentException otherwise
   */
  static String requireSafe(String key) {
    if (key == null || !SAFE_KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("Invalid storage key: " + key);
    }
    return key;
  }
}
