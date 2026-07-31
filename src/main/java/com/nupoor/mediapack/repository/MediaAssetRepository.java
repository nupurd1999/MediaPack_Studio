package com.nupoor.mediapack.repository;

import com.nupoor.mediapack.model.MediaAsset;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaAssetRepository extends MongoRepository<MediaAsset, String> {
    List<MediaAsset> findBySku(String sku);
    List<MediaAsset> findByFileType(String fileType);
    List<MediaAsset> findByTagsContaining(String tag);
}
