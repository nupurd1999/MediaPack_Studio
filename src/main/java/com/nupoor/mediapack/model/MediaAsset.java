package com.nupoor.mediapack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Document(collection = "media_assets")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaAsset {


    @Id
    private String id;

    @Indexed
    private String sku;

    private String fileName;
    private String s3Key;
    private String fileType; // e.g., "IMAGE_PNG", "DOCUMENT_PDF", "VIDEO_MP4"
    private long sizeInBytes;
    private int width;
    private int height;
    private List<String> tags = new ArrayList<>();
    private Instant createdAt;

    public MediaAsset() {
        this.createdAt = Instant.now();
    }

    public MediaAsset(String sku, String fileName, String s3Key, String fileType, long sizeInBytes, int width, int height, List<String> tags) {
        this.sku = sku;
        this.fileName = fileName;
        this.s3Key = s3Key;
        this.fileType = fileType;
        this.sizeInBytes = sizeInBytes;
        this.width = width;
        this.height = height;
        this.tags = tags != null ? tags : new ArrayList<>();
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public long getSizeInBytes() { return sizeInBytes; }
    public void setSizeInBytes(long sizeInBytes) { this.sizeInBytes = sizeInBytes; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
