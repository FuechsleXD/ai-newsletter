package de.fuechsle.ainewsletter.storage;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * {@link DigestStorage} in a local directory ({@code newsdigest.storage.local-directory}) until S3
 * follows in roadmap step 11. Files are written to a temporary file first and then moved, so a
 * reader never sees a half-written PDF.
 */
@Component
public class LocalDigestStorage implements DigestStorage {

  private static final Pattern SAFE_KEY = Pattern.compile("[a-z0-9-]+(/[a-z0-9-]+)*\\.[a-z]+");

  private final Path baseDirectory;

  public LocalDigestStorage(NewsDigestProperties properties) {
    this.baseDirectory =
        Path.of(properties.storage().localDirectory()).toAbsolutePath().normalize();
  }

  @Override
  public void store(String key, byte[] content) {
    Path target = resolve(key);
    try {
      Files.createDirectories(target.getParent());
      Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
      Files.write(temporary, content);
      Files.move(
          temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new StorageException("Cannot store " + key, e);
    }
  }

  @Override
  public byte[] load(String key) {
    try {
      return Files.readAllBytes(resolve(key));
    } catch (IOException e) {
      throw new StorageException("Cannot load " + key, e);
    }
  }

  /** Only simple keys like {@code runs/12/digest.pdf}; no path traversal. */
  private Path resolve(String key) {
    if (key == null || !SAFE_KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("Invalid storage key: " + key);
    }
    return baseDirectory.resolve(key);
  }
}
