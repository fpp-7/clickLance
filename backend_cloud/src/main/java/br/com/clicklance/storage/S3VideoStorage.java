package br.com.clicklance.storage;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class S3VideoStorage implements VideoStorage {
    private final StorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;
    private final Clock clock;

    public S3VideoStorage(StorageProperties properties, S3Client client, S3Presigner presigner, Clock clock) {
        properties.validate();
        this.properties = properties;
        this.client = client;
        this.presigner = presigner;
        this.clock = clock;
    }

    @Override
    public StoredVideo verify(String key) {
        StorageKeys.requireSafe(key);
        try {
            HeadObjectResponse object = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket()).key(key).build());
            String contentType = object.contentType();
            Long size = object.contentLength();
            if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("video/mp4")
                    || size == null || size <= 0 || size > properties.getMaxBytes()) {
                throw new StorageException(StorageException.Reason.INVALID_VIDEO, "Tipo ou tamanho do vídeo inválido");
            }
            return new StoredVideo(size);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) throw new StorageException(StorageException.Reason.NOT_FOUND, "Vídeo não encontrado");
            throw unavailable(e);
        } catch (software.amazon.awssdk.core.exception.SdkException e) {
            throw unavailable(e);
        }
    }

    @Override
    public String playbackUrl(String key, Instant notAfter) {
        StorageKeys.requireSafe(key);
        Instant expiry = StorageKeys.expiry(clock, properties.getPlaybackTtlSeconds(), notAfter);
        // Leave one second for local signing so the URL does not outlive the authorization deadline.
        long seconds = Duration.between(clock.instant(), expiry).getSeconds() - 1;
        if (seconds < 1) throw new StorageException(StorageException.Reason.EXPIRED, "Prazo de reprodução expirado");
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(seconds))
                    .getObjectRequest(GetObjectRequest.builder().bucket(properties.getBucket()).key(key)
                            .responseCacheControl("private, no-store").build())
                    .build()).url().toExternalForm();
        } catch (software.amazon.awssdk.core.exception.SdkException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void delete(String key) {
        StorageKeys.requireSafe(key);
        try {
            BucketVersioningStatus status = client.getBucketVersioning(GetBucketVersioningRequest.builder()
                    .bucket(properties.getBucket()).build()).status();
            if (status != null) {
                throw new StorageException(StorageException.Reason.UNSAFE_CONFIGURATION,
                        "Purga exige bucket sem histórico de versionamento; versões precisam de política própria");
            }
            var result = client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket()).key(key).build());
            if (Boolean.TRUE.equals(result.deleteMarker())) {
                throw new StorageException(StorageException.Reason.UNSAFE_CONFIGURATION,
                        "Versionamento alterado durante a purga; exclusão física não confirmada");
            }
        } catch (software.amazon.awssdk.core.exception.SdkException e) {
            throw unavailable(e);
        }
    }

    private StorageException unavailable(Throwable cause) {
        return new StorageException(StorageException.Reason.UNAVAILABLE, "Storage indisponível", cause);
    }
}
