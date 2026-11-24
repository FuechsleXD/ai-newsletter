package de.fuechsle.ainewsletter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.fuechsle.ainewsletter.support.TestProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** No real AWS calls: the SDK client is mocked. */
class S3DigestStorageTest {

  private static final String BUCKET = "newsdigest-test-pdfs";
  private static final String KEY = DigestStorage.pdfKey(12);

  private final S3Client client = mock(S3Client.class);
  private final S3DigestStorage storage =
      new S3DigestStorage(
          client,
          TestProperties.with(
              Map.of("newsdigest.storage.type", "s3", "newsdigest.storage.s3-bucket", BUCKET)));

  @Test
  void storesThePdfInTheBucketUnderItsKey() throws Exception {
    storage.store(KEY, new byte[] {1, 2, 3});

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
    verify(client).putObject(request.capture(), body.capture());
    assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(request.getValue().key()).isEqualTo("runs/12/digest.pdf");
    assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
    try (var stream = body.getValue().contentStreamProvider().newStream()) {
      assertThat(stream.readAllBytes()).containsExactly(1, 2, 3);
    }
  }

  @Test
  void loadsThePdfFromTheBucket() {
    when(client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenReturn(
            ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {4, 5}));

    assertThat(storage.load(KEY)).containsExactly(4, 5);

    ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(client).getObjectAsBytes(request.capture());
    assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(request.getValue().key()).isEqualTo(KEY);
  }

  @Test
  void missingObjectOrS3ErrorBecomesStorageException() {
    when(client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("missing").build());
    assertThatThrownBy(() -> storage.load(KEY))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining(KEY);

    when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(S3Exception.builder().message("AccessDenied").statusCode(403).build());
    assertThatThrownBy(() -> storage.store(KEY, new byte[] {1}))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining(KEY);
  }

  @Test
  void unsafeKeysNeverReachS3() {
    assertThatThrownBy(() -> storage.store("../x.pdf", new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.load(null)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(client);
  }
}
