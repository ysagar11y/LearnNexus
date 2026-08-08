package com.learnnexus.media;

import com.learnnexus.common.ApiException;
import com.learnnexus.config.AppProperties;
import com.learnnexus.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URLConnection;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Object storage access.
 *
 * <p>Course media never streams through the API: browsers upload to and download
 * from storage directly using short-lived presigned URLs. That keeps large video
 * off the application's heap and lets a CDN sit in front of the bucket, while
 * still requiring an authenticated, tenant-checked call to obtain each URL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final AppProperties properties;

    public record UploadTicket(
            UUID assetId,
            String uploadUrl,
            String storageKey,
            Duration expiresIn
    ) {}

    /**
     * Keys are prefixed with the tenant id so that bucket-level policies, lifecycle
     * rules and any future per-tenant bucket split all remain possible.
     */
    public String buildKey(String folder, String filename) {
        UUID tenantId = TenantContext.requireTenantId();
        String safeName = filename == null ? "file" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.length() > 120) {
            safeName = safeName.substring(safeName.length() - 120);
        }
        return "tenants/%s/%s/%s-%s".formatted(tenantId, folder, UUID.randomUUID(), safeName);
    }

    public String presignUpload(String storageKey, String contentType) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.storage().bucket())
                .key(storageKey)
                .contentType(contentType)
                .build();

        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(properties.storage().presignTtl())
                        .putObjectRequest(put)
                        .build())
                .url()
                .toString();
    }

    public String presignDownload(String storageKey, String downloadFilename) {
        GetObjectRequest.Builder get = GetObjectRequest.builder()
                .bucket(properties.storage().bucket())
                .key(storageKey);

        if (downloadFilename != null) {
            get.responseContentDisposition("attachment; filename=\"" + downloadFilename.replace('"', '\'') + "\"");
        }

        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(properties.storage().presignTtl())
                        .getObjectRequest(get.build())
                        .build())
                .url()
                .toString();
    }

    /** Server-side upload, used for generated artefacts such as certificate PDFs. */
    public void put(String storageKey, byte[] content, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(properties.storage().bucket())
                        .key(storageKey)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
    }

    /**
     * Confirms an object exists and reports its real size — the client's claimed
     * byte count is not trusted for quota accounting.
     */
    public Optional<HeadObjectResponse> head(String storageKey) {
        try {
            return Optional.of(s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.storage().bucket())
                    .key(storageKey)
                    .build()));
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.storage().bucket())
                    .key(storageKey)
                    .build());
        } catch (S3Exception ex) {
            // A missing object is the desired end state, so this is not worth failing on.
            log.warn("Could not delete {}: {}", storageKey, ex.getMessage());
        }
    }

    public void assertBucketReachable() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(properties.storage().bucket())
                    .build());
        } catch (NoSuchBucketException ex) {
            throw ApiException.unprocessable("storage_unavailable",
                    "The media bucket does not exist. Run the storage bootstrap step.");
        }
    }

    public static String guessContentType(String filename) {
        String guessed = URLConnection.guessContentTypeFromName(filename);
        return guessed == null ? "application/octet-stream" : guessed;
    }
}
