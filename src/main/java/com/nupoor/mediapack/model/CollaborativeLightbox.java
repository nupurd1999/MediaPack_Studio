package com.nupoor.mediapack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "collaborative_lightboxes")
public class CollaborativeLightbox {

    @Id
    private String id;

    private String title;
    private String ownerUserId;
    private List<String> sharedWithUserIds = new ArrayList<>();
    private List<String> assetIds = new ArrayList<>();

    @Version
    private Long version; // Optimistic locking field

    private Instant lastModifiedAt;

    public CollaborativeLightbox() {
        this.lastModifiedAt = Instant.now();
    }

    public CollaborativeLightbox(String title, String ownerUserId) {
        this.title = title;
        this.ownerUserId = ownerUserId;
        this.lastModifiedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public List<String> getSharedWithUserIds() { return sharedWithUserIds; }
    public void setSharedWithUserIds(List<String> sharedWithUserIds) { this.sharedWithUserIds = sharedWithUserIds; }

    public List<String> getAssetIds() { return assetIds; }
    public void setAssetIds(List<String> assetIds) { this.assetIds = assetIds; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getLastModifiedAt() { return lastModifiedAt; }
    public void setLastModifiedAt(Instant lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }
}
