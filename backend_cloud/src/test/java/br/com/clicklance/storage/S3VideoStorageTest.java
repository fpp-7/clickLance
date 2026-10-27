package br.com.clicklance.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3VideoStorageTest {
    private static final String KEY = "replays/quadra_001/evento_001.mp4";
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private final S3Client client = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);

    private S3VideoStorage storage() {
        StorageProperties p = new StorageProperties();
        p.setMode("s3");
        p.setBucket("bucket-privado");
        p.setMaxBytes(100);
        return new S3VideoStorage(p, client, presigner, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void verifiesVideoMetadataAndRejectsMissingOrInvalidObjects() {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("video/mp4").contentLength(80L).build());
        assertEquals(80, storage().verify(KEY).sizeBytes());
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("text/html").contentLength(80L).build());
        assertEquals(StorageException.Reason.INVALID_VIDEO,
                assertThrows(StorageException.class, () -> storage().verify(KEY)).reason());
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertEquals(StorageException.Reason.NOT_FOUND,
                assertThrows(StorageException.class, () -> storage().verify(KEY)).reason());
    }

    @Test void refusesPhysicalPurgeWhenVersioningIsEnabledOrSuspended() {
        for (BucketVersioningStatus status : new BucketVersioningStatus[]{
                BucketVersioningStatus.ENABLED, BucketVersioningStatus.SUSPENDED}) {
            when(client.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                    .thenReturn(GetBucketVersioningResponse.builder().status(status).build());
            assertEquals(StorageException.Reason.UNSAFE_CONFIGURATION,
                    assertThrows(StorageException.class, () -> storage().delete(KEY)).reason());
        }
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test void deletionRequiresAnUnversionedBucketAndAnAuthorizedKeyShape() {
        when(client.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenReturn(GetBucketVersioningResponse.builder().build());
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());
        storage().delete(KEY);
        ArgumentCaptor<DeleteObjectRequest> deletion = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(deletion.capture());
        assertEquals("bucket-privado", deletion.getValue().bucket());
        assertEquals(KEY, deletion.getValue().key());
        assertThrows(StorageException.class, () -> storage().delete("../elsewhere.mp4"));
    }

    @Test void detectsVersioningEnabledBetweenTheCheckAndDeletion() {
        when(client.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenReturn(GetBucketVersioningResponse.builder().build());
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().deleteMarker(true).build());
        assertEquals(StorageException.Reason.UNSAFE_CONFIGURATION,
                assertThrows(StorageException.class, () -> storage().delete(KEY)).reason());
    }

    @Test void privateSignedUrlIsLimitedByTheResourceDeadline() throws Exception {
        PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://private.example/video?signature=test").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
        assertTrue(storage().playbackUrl(KEY, NOW.plusSeconds(20)).contains("signature="));
        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertEquals(Duration.ofSeconds(19), request.getValue().signatureDuration());
        assertEquals(KEY, request.getValue().getObjectRequest().key());
        assertThrows(StorageException.class, () -> storage().playbackUrl(KEY, NOW));
    }
}
