package com.nupoor.mediapack.service;

import com.nupoor.mediapack.model.MediaAsset;
import com.nupoor.mediapack.repository.MediaAssetRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MediaAssetService {

    private final MediaAssetRepository assetRepository;
    private final SolrSearchService solrSearchService;
    private final S3StorageService s3StorageService;

    public MediaAssetService(MediaAssetRepository assetRepository, SolrSearchService solrSearchService, S3StorageService s3StorageService) {
        this.assetRepository = assetRepository;
        this.solrSearchService = solrSearchService;
        this.s3StorageService = s3StorageService;
    }

    public MediaAsset saveAsset(MediaAsset asset) {
        MediaAsset saved = assetRepository.save(asset);
        solrSearchService.indexAsset(saved);
        return saved;
    }

    public Optional<MediaAsset> getAssetById(String id) {
        return assetRepository.findById(id);
    }

    public List<MediaAsset> getAllAssets() {
        return assetRepository.findAll();
    }

    public List<MediaAsset> getAssetsBySku(String sku) {
        return assetRepository.findBySku(sku);
    }

    public String getPresignedDownloadUrl(String assetId) {
        MediaAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with ID: " + assetId));
        return s3StorageService.generatePresignedUrl(asset.getS3Key());
    }
}
