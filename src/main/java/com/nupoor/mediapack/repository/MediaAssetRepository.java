package com.nupoor.mediapack.repository;

import com.nupoor.mediapack.model.MediaAsset;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaAssetRepository extends MongoRepository<MediaAsset, String> {
    List<MediaAsset> findBySku(String sku);
    Optional<MediaAsset> findByChecksumSha256(String checksumSha256);
    List<MediaAsset> findByTenantRegion(String tenantRegion);
}
