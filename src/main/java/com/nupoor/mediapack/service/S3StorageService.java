package com.nupoor.mediapack.service;

import com.nupoor.mediapack.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

@Service
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket:mediapack-assets}")
    private String bucketName = "mediapack-assets";

    private volatile boolean bucketVerified = false;

    @Autowired
    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public void ensureBucketExists() {
        if (s3Client == null || bucketVerified) return;
        synchronized (this) {
            if (bucketVerified) return;
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                bucketVerified = true;
            } catch (Exception e) {
                log.info("Bucket {} does not exist yet. Creating bucket...", bucketName);
                try {
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                    bucketVerified = true;
                } catch (Exception ex) {
                    log.warn("Could not auto-create S3 bucket: {}", ex.getMessage());
                }
            }
        }
    }

    public void uploadFile(String s3Key, InputStream inputStream, long contentLength, String contentType) {
        ensureBucketExists();
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
            log.info("Successfully uploaded asset to S3 key: {}", s3Key);
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", s3Key, e);
            throw new RuntimeException("S3 upload failed for key: " + s3Key, e);
        }
    }

    public String generatePresignedUrl(String s3Key) {
        if (s3Presigner == null) {
            return "http://localhost:4566/" + bucketName + "/" + s3Key;
        }
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(24))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Failed to generate pre-signed URL for key: {}", s3Key, e);
            return "http://localhost:4566/" + bucketName + "/" + s3Key; // Fallback URL
        }
    }

    // StorageService interface implementations
    @Override
    public void store(String key, InputStream inputStream, long contentLength, String contentType) {
        uploadFile(key, inputStream, contentLength, contentType);
    }

    @Override
    public InputStream getInputStream(String key) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(key).build();
        return s3Client.getObject(request);
    }

    @Override
    public String getDownloadUrl(String key) {
        return generatePresignedUrl(key);
    }

    @Override
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("Error checking S3 key existence {}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", key, e.getMessage());
        }
    }

    @Override
    public Optional<Path> getLocalFilePath(String key) {
        return Optional.empty();
    }

    @Override
    public String getStorageType() {
        return "S3";
    }
}
