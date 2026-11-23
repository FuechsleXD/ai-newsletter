package de.fuechsle.ainewsletter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fuechsle.ainewsletter.support.TestProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDigestStorageTest {

  @TempDir Path directory;

  @Test
  void storedContentCanBeLoadedAndIsReplacedByTheNextStore() {
    LocalDigestStorage storage = storage();
    String key = DigestStorage.pdfKey(42);

    storage.store(key, new byte[] {1, 2, 3});
    storage.store(key, new byte[] {4, 5});

    assertThat(key).isEqualTo("runs/42/digest.pdf");
    assertThat(storage.load(key)).containsExactly(4, 5);
    assertThat(directory.resolve("runs/42/digest.pdf")).exists();
    try (var files = Files.list(directory.resolve("runs/42"))) {
      // no temporary files are left behind
      assertThat(files).hasSize(1);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  @Test
  void missingObjectCannotBeLoaded() {
    assertThatThrownBy(() -> storage().load("runs/1/digest.pdf"))
        .isInstanceOf(StorageException.class);
  }

  @Test
  void keysOutsideTheDirectoryAreRejected() {
    LocalDigestStorage storage = storage();

    for (String key :
        new String[] {"../x.pdf", "/etc/passwd.txt", "runs/../../x.pdf", "a\\b.pdf"}) {
      assertThatThrownBy(() -> storage.store(key, new byte[] {1}))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> storage.load(null)).isInstanceOf(IllegalArgumentException.class);
  }

  private LocalDigestStorage storage() {
    return new LocalDigestStorage(
        TestProperties.with(Map.of("newsdigest.storage.local-directory", directory.toString())));
  }
}
