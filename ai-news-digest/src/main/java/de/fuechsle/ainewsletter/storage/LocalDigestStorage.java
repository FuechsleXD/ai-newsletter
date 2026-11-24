package de.fuechsle.ainewsletter.storage;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link DigestStorage} in a local directory ({@code newsdigest.storage.local-directory}); the
 * default locally and in tests, in AWS {@link S3DigestStorage} is used (ADR-015). Files are written
 * to a temporary file first and then moved, so a reader never sees a half-written PDF.
 */
@Component
@ConditionalOnProperty(
    name = "newsdigest.storage.type",
    havingValue = "local",
    matchIfMissing = true)
public class LocalDigestStorage implements DigestStorage {

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

  private Path resolve(String key) {
    return baseDirectory.resolve(StorageKeys.requireSafe(key));
  }
}
