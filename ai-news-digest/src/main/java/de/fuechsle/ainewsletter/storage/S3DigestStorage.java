package de.fuechsle.ainewsletter.storage;

import de.fuechsle.ainewsletter.config.NewsDigestProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * {@link DigestStorage} in a private S3 bucket ({@code newsdigest.storage.s3-bucket}, profile
 * {@code prod}, ADR-015). Access with the instance role; encryption, versioning and the retention
 * period are set on the bucket (Terraform). S3 replaces an object atomically, so a reader never
 * sees a half-written PDF.
 */
@Component
@ConditionalOnProperty(name = "newsdigest.storage.type", havingValue = "s3")
public class S3DigestStorage implements DigestStorage {

  static final String PDF_CONTENT_TYPE = "application/pdf";

  private final S3Client client;
  private final String bucket;

  public S3DigestStorage(S3Client client, NewsDigestProperties properties) {
    this.client = client;
    this.bucket = properties.storage().s3Bucket();
  }

  @Override
  public void store(String key, byte[] content) {
    String safeKey = StorageKeys.requireSafe(key);
    try {
      client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(safeKey)
              .contentType(PDF_CONTENT_TYPE)
              .build(),
          RequestBody.fromBytes(content));
    } catch (SdkException e) {
      throw new StorageException("Cannot store " + key + " in S3", e);
    }
  }

  @Override
  public byte[] load(String key) {
    String safeKey = StorageKeys.requireSafe(key);
    try {
      return client
          .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(safeKey).build())
          .asByteArray();
    } catch (SdkException e) {
      // Includes NoSuchKey
      throw new StorageException("Cannot load " + key + " from S3", e);
    }
  }
}
