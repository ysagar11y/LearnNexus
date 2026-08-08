package com.learnnexus.media;

import com.learnnexus.common.ApiException;
import com.learnnexus.common.PageResponse;
import com.learnnexus.config.AppProperties;
import com.learnnexus.security.CurrentUser;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenant.Tenant;
import com.learnnexus.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Media", description = "Direct-to-storage uploads for course content.")
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    public record UploadRequest(
            @NotBlank String filename,
            @NotBlank String contentType,
            @Positive long sizeBytes,
            String folder
    ) {}

    public record AssetResponse(
            UUID id,
            String filename,
            String contentType,
            MediaAsset.Kind kind,
            MediaAsset.Status status,
            long sizeBytes,
            Integer durationSeconds,
            String url,
            Instant createdAt
    ) {}

    @Operation(summary = "Request a presigned URL and upload the file straight to storage")
    @PostMapping("/upload-url")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','AUTHOR','INSTRUCTOR')")
    public StorageService.UploadTicket requestUpload(@Valid @RequestBody UploadRequest request) {
        return mediaService.createUploadTicket(request);
    }

    @Operation(summary = "Confirm an upload finished; the asset becomes usable in lessons")
    @PostMapping("/{assetId}/complete")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','AUTHOR','INSTRUCTOR')")
    public AssetResponse completeUpload(@PathVariable UUID assetId,
                                        @RequestParam(required = false) Integer durationSeconds) {
        return mediaService.completeUpload(assetId, durationSeconds);
    }

    @Operation(summary = "The tenant's media library")
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','AUTHOR','INSTRUCTOR')")
    public PageResponse<AssetResponse> list(@RequestParam(required = false) MediaAsset.Kind kind,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        return mediaService.list(kind, page, size);
    }

    @Operation(summary = "A short-lived URL for viewing or downloading an asset")
    @GetMapping("/{assetId}/url")
    public AssetResponse url(@PathVariable UUID assetId,
                             @RequestParam(defaultValue = "false") boolean download) {
        return mediaService.presign(assetId, download);
    }

    @Operation(summary = "Delete an asset from storage and the library")
    @DeleteMapping("/{assetId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','AUTHOR')")
    public ResponseEntity<Void> delete(@PathVariable UUID assetId) {
        mediaService.delete(assetId);
        return ResponseEntity.noContent().build();
    }

    @Service
    @Slf4j
    @RequiredArgsConstructor
    public static class MediaService {

        private final MediaAssetRepository repository;
        private final StorageService storageService;
        private final TenantRepository tenantRepository;
        private final AppProperties properties;

        @Transactional
        public StorageService.UploadTicket createUploadTicket(UploadRequest request) {
            if (request.sizeBytes() > properties.storage().maxUploadBytes()) {
                throw ApiException.badRequest("file_too_large",
                        "Files are limited to " + (properties.storage().maxUploadBytes() / (1024 * 1024)) + " MB.");
            }
            assertStorageQuota(request.sizeBytes());

            String folder = switch (request.folder() == null ? "" : request.folder()) {
                case "branding", "avatars", "thumbnails" -> request.folder();
                default -> "content";
            };
            String key = storageService.buildKey(folder, request.filename());

            MediaAsset asset = new MediaAsset();
            asset.setStorageKey(key);
            asset.setFilename(request.filename());
            asset.setContentType(request.contentType());
            asset.setSizeBytes(request.sizeBytes());
            asset.setKind(MediaAsset.Kind.fromContentType(request.contentType(), request.filename()));
            asset.setStatus(MediaAsset.Status.PENDING);
            asset.setUploadedBy(CurrentUser.requireId());
            repository.save(asset);

            return new StorageService.UploadTicket(
                    asset.getId(),
                    storageService.presignUpload(key, request.contentType()),
                    key,
                    properties.storage().presignTtl());
        }

        @Transactional
        public AssetResponse completeUpload(UUID assetId, Integer durationSeconds) {
            MediaAsset asset = require(assetId);

            // Trust the object store, not the client: an upload that never landed
            // must not leave a lesson pointing at a missing file.
            var head = storageService.head(asset.getStorageKey())
                    .orElseThrow(() -> ApiException.badRequest("upload_missing",
                            "The upload did not complete. Please try again."));

            asset.setSizeBytes(head.contentLength());
            asset.setStatus(MediaAsset.Status.READY);
            asset.setDurationSeconds(durationSeconds);
            repository.save(asset);

            return toResponse(asset, storageService.presignDownload(asset.getStorageKey(), null));
        }

        @Transactional(readOnly = true)
        public PageResponse<AssetResponse> list(MediaAsset.Kind kind, int page, int size) {
            Page<MediaAsset> assets = repository.findLibrary(kind,
                    PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
            return PageResponse.of(assets, asset ->
                    toResponse(asset, storageService.presignDownload(asset.getStorageKey(), null)));
        }

        @Transactional(readOnly = true)
        public AssetResponse presign(UUID assetId, boolean download) {
            MediaAsset asset = require(assetId);
            String url = storageService.presignDownload(
                    asset.getStorageKey(), download ? asset.getFilename() : null);
            return toResponse(asset, url);
        }

        @Transactional
        public void delete(UUID assetId) {
            MediaAsset asset = require(assetId);
            storageService.delete(asset.getStorageKey());
            repository.delete(asset);
        }

        private void assertStorageQuota(long incomingBytes) {
            Tenant tenant = tenantRepository.findActiveById(TenantContext.requireTenantId())
                    .orElseThrow(() -> ApiException.notFound("Tenant", TenantContext.requireTenantId()));
            long used = repository.totalStorageBytes();
            if (used + incomingBytes > tenant.getMaxStorageBytes()) {
                throw ApiException.conflict("storage_limit_reached",
                        "This workspace has reached its storage limit. Remove unused media or upgrade the plan.");
            }
        }

        private MediaAsset require(UUID assetId) {
            return repository.findById(assetId)
                    .orElseThrow(() -> ApiException.notFound("Media asset", assetId));
        }

        private AssetResponse toResponse(MediaAsset asset, String url) {
            return new AssetResponse(asset.getId(), asset.getFilename(), asset.getContentType(),
                    asset.getKind(), asset.getStatus(), asset.getSizeBytes(), asset.getDurationSeconds(),
                    url, asset.getCreatedAt());
        }
    }
}
