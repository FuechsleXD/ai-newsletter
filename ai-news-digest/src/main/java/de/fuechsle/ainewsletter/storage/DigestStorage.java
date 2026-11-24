package de.fuechsle.ainewsletter.storage;

/**
 * Storage of the rendered PDFs. The key is deterministic per run ({@link #pdfKey(long)}), so a
 * repeated render job overwrites the same object (docs/04-prozess-bpmn.md, "Idempotenz").
 * Implementations: {@link LocalDigestStorage} (default) and {@link S3DigestStorage} (AWS).
 */
public interface DigestStorage {

  /** Key of the PDF of a run, stored in {@code digest_run.pdf_s3_key}. */
  static String pdfKey(long runId) {
    return "runs/" + runId + "/digest.pdf";
  }

  /** Stores the content under the key, replacing an existing object. */
  void store(String key, byte[] content);

  /**
   * @throws StorageException if there is no object with this key or it cannot be read
   */
  byte[] load(String key);
}
