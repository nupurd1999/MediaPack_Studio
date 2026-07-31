package com.nupoor.mediapack.service;

import com.nupoor.mediapack.model.CollaborativeLightbox;
import com.nupoor.mediapack.repository.CollaborativeLightboxRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CollaborativeLightboxService {

    private final CollaborativeLightboxRepository lightboxRepository;

    public CollaborativeLightboxService(CollaborativeLightboxRepository lightboxRepository) {
        this.lightboxRepository = lightboxRepository;
    }

    public CollaborativeLightbox createLightbox(String title, String ownerUserId) {
        CollaborativeLightbox lightbox = new CollaborativeLightbox(title, ownerUserId);
        return lightboxRepository.save(lightbox);
    }

    public Optional<CollaborativeLightbox> getLightboxById(String id) {
        return lightboxRepository.findById(id);
    }

    public List<CollaborativeLightbox> getLightboxesByOwner(String ownerUserId) {
        return lightboxRepository.findByOwnerUserId(ownerUserId);
    }

    public CollaborativeLightbox addAssetToLightbox(String lightboxId, String assetId) {
        CollaborativeLightbox lightbox = lightboxRepository.findById(lightboxId)
                .orElseThrow(() -> new IllegalArgumentException("Lightbox not found: " + lightboxId));

        if (!lightbox.getAssetIds().contains(assetId)) {
            lightbox.getAssetIds().add(assetId);
            lightbox.setLastModifiedAt(Instant.now());
        }

        // Saving will increment @Version and check for optimistic locking failure
        return lightboxRepository.save(lightbox);
    }

    public CollaborativeLightbox removeAssetFromLightbox(String lightboxId, String assetId) {
        CollaborativeLightbox lightbox = lightboxRepository.findById(lightboxId)
                .orElseThrow(() -> new IllegalArgumentException("Lightbox not found: " + lightboxId));

        lightbox.getAssetIds().remove(assetId);
        lightbox.setLastModifiedAt(Instant.now());
        return lightboxRepository.save(lightbox);
    }

    public CollaborativeLightbox shareWithUser(String lightboxId, String userId) {
        CollaborativeLightbox lightbox = lightboxRepository.findById(lightboxId)
                .orElseThrow(() -> new IllegalArgumentException("Lightbox not found: " + lightboxId));

        if (!lightbox.getSharedWithUserIds().contains(userId)) {
            lightbox.getSharedWithUserIds().add(userId);
            lightbox.setLastModifiedAt(Instant.now());
        }
        return lightboxRepository.save(lightbox);
    }

    public CollaborativeLightbox updateLightbox(CollaborativeLightbox lightbox) {
        lightbox.setLastModifiedAt(Instant.now());
        return lightboxRepository.save(lightbox);
    }
}
