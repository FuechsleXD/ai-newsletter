package de.fuechsle.ainewsletter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StorageKeysTest {

  @Test
  void keyOfARunIsSafe() {
    assertThat(StorageKeys.requireSafe(DigestStorage.pdfKey(42))).isEqualTo("runs/42/digest.pdf");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../x.pdf",
        "/etc/passwd.txt",
        "runs/../../x.pdf",
        "a\\b.pdf",
        "runs//1.pdf",
        "Runs/1.pdf",
        "runs/1",
        ""
      })
  void unsafeKeysAreRejected(String key) {
    assertThatThrownBy(() -> StorageKeys.requireSafe(key))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid storage key");
  }

  @Test
  void nullIsRejected() {
    assertThatThrownBy(() -> StorageKeys.requireSafe(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
