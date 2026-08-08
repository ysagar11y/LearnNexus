package com.learnnexus.media;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    @Query("select m from MediaAsset m where (:kind is null or m.kind = :kind)")
    Page<MediaAsset> findLibrary(@Param("kind") MediaAsset.Kind kind, Pageable pageable);

    @Query("select coalesce(sum(m.sizeBytes), 0) from MediaAsset m")
    long totalStorageBytes();
}
