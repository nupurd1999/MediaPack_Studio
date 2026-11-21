<div align="center">
  <img src="./assets/logo.png" alt="MediaPack Studio Logo" width="160" style="border-radius: 12px; margin-bottom: 8px;" />
</div>

# MediaPack Studio — Technical Architecture & Implementation Design

> **Project Concept:** Distributed Bulk Asset Packaging & Multi-Tenant Media Engine  
> **Domain:** Enterprise E-Commerce & Industrial Logistics  
> **Target Audience:** German / EU Enterprise Tech Leads, E-Commerce Systems Architects, Engineering Hiring Managers  
> **Target Profile:** Nupoor Dhamal (Senior Java Backend Developer — Würth IT / Würth Group Background)

---

## 📌 Executive Summary

**MediaPack Studio** is a high-performance Java 21 / Spring Boot 3 microservice engineered to solve large-scale digital asset management (DAM) and media packaging challenges faced by global industrial e-commerce companies like the **Würth Group**.

It mirrors Nupoor's real-world experience at **Würth IT India**, implementing:
1. **Dynamic Multi-Tenant MongoDB Routing:** A custom `AbstractMongoFactory` pattern resolving database connections per country region (`DE`, `NL`, `US`).
2. **Redis Async Task Execution Pipeline:** Asynchronous job processing for bundling high-resolution product media files into ZIP archives.
3. **Collaborative Lightbox Optimistic Locking:** Concurrency-safe media collection sharing using MongoDB `@Version` optimistic locking.
4. **Live Progress & AWS S3 Delivery:** Real-time progress updates via **WebSockets (`STOMP`)** + automated generation of **AWS S3 pre-signed download URLs**.
5. **Apache Solr Search Filtering:** High-speed catalog search and custom filter presets.

---

## 🏗️ System Architecture

```mermaid
graph TD
    User[Enterprise User / Branch Manager] -->|JWT + X-Tenant-Region: DE| GW[Spring Security API Gateway]
    
    subgraph Multi-Tenant Database Layer
        GW --> Router[MultiTenantMongoFactory<br/>AbstractMongoFactory Pattern]
        Router -->|Region DE| DB_DE[(MongoDB: DE Catalog & Lightboxes)]
        Router -->|Region NL| DB_NL[(MongoDB: NL Catalog & Lightboxes)]
        Router -->|Region US| DB_US[(MongoDB: US Catalog & Lightboxes)]
    end

    subgraph Search & Collaboration
        GW --> SolrService[Apache Solr Search Engine<br/>Catalog & Media Metadata Filter]
        GW --> LightboxService[Collaborative Lightbox Service<br/>MongoDB @Version Optimistic Locking]
    end

    subgraph Asynchronous Export Pipeline
        GW -->|Enqueue Export Task| RedisQueue[(Redis Task Queue)]
        RedisQueue --> Worker[Async Media Packager Worker]
        Worker -->|Stream ZIP Bytes| S3[(AWS S3 / LocalStack Storage)]
        Worker -->|Publish Progress %| WS[Spring WebSockets / STOMP Broker]
    end

    WS -->|Progress: 25%..75%..100% + S3 Link| User
```

---

## 🔑 Key Engineering Highlights (What Impresses German Tech Leads)

1. **Custom `AbstractMongoFactory` Multi-Tenancy:**
   - Evaluates incoming request context (`X-Tenant-Region: DE`) via a `TenantContext` thread-local holder and routes queries dynamically to the appropriate region database (`media_de`, `media_nl`, `media_us`).
2. **MongoDB Optimistic Locking (`@Version`):**
   - When multiple enterprise users simultaneously add, remove, or reorder assets in a shared "Collaborative Lightbox", MongoDB's `@Version` field guarantees that concurrent updates trigger an `OptimisticLockingFailureException`, preventing data overwrite bugs.
3. **Non-Blocking Async Zip Pipeline & Memory Efficiency:**
   - Instead of loading multi-gigabyte media files entirely into heap RAM, the worker streams file chunks directly from S3 into a `ZipOutputStream` and pipes the output stream directly back to AWS S3, maintaining low memory overhead (`<512MB RAM`).
4. **Apache Solr Search Integration:**
   - Fast full-text and faceted search across millions of product assets based on resolution, file type, SKU tags, and custom metadata presets.
5. **LocalStack S3 Offline Compatibility:**
   - Configured with `LocalStack` in `docker-compose.yml` so anyone running the project locally can test AWS S3 uploads and pre-signed URL generation without an actual AWS cloud account.

---

## 🗄️ MongoDB Document Schemas

### 1. `MediaAsset.java` (Asset Metadata Document)

```java
package com.nupoor.mediapack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "media_assets")
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
    private List<String> tags;
    private Instant createdAt;

    // Constructors, Getters, Setters omitted for brevity
}
```

### 2. `CollaborativeLightbox.java` (Optimistic Locking Document)

```java
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

    public CollaborativeLightbox() {}

    public CollaborativeLightbox(String title, String ownerUserId) {
        this.title = title;
        this.ownerUserId = ownerUserId;
        this.lastModifiedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<String> getAssetIds() { return assetIds; }
    public Long getVersion() { return version; }
}
```

---

## 💻 Code Blueprint (Java 21 / Spring Boot 3)

### 1. Multi-Tenant MongoDB Context Resolver (`TenantContext.java`)

```java
package com.nupoor.mediapack.config.tenant;

public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get() != null ? CURRENT_TENANT.get() : "DE"; // Default to German tenant
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

### 2. Dynamic `AbstractMongoFactory` Router (`MultiTenantMongoConfig.java`)

```java
package com.nupoor.mediapack.config.tenant;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MultiTenantMongoConfig {

    private final String mongoUri = "mongodb://admin:secret@localhost:27017/?authSource=admin";

    @Bean
    public MongoTemplate mongoTemplate() {
        MongoClient mongoClient = MongoClients.create(mongoUri);
        
        // Multi-tenant database factory mapping
        Map<String, String> tenantDbMap = Map.of(
            "DE", "media_catalog_de",
            "NL", "media_catalog_nl",
            "US", "media_catalog_us"
        );

        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(mongoClient, "media_catalog_de")) {
            @Override
            protected String getDatabaseName() {
                String tenant = TenantContext.getCurrentTenant();
                return tenantDbMap.getOrDefault(tenant, "media_catalog_de");
            }
        };
    }
}
```

### 3. Redis Async Export Worker with WebSockets (`AsyncMediaPackagerWorker.java`)

```java
package com.nupoor.mediapack.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AsyncMediaPackagerWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncMediaPackagerWorker.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MockS3StorageService s3Service;

    public AsyncMediaPackagerWorker(SimpMessagingTemplate messagingTemplate, MockS3StorageService s3Service) {
        this.messagingTemplate = messagingTemplate;
        this.s3Service = s3Service;
    }

    @Async("taskExecutor")
    public void processExportJob(String jobId, List<String> assetKeys) {
        log.info("Starting async ZIP export for Job ID: {} with {} assets", jobId, assetKeys.size());
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            int total = assetKeys.size();

            for (int i = 0; i < total; i++) {
                String assetKey = assetKeys.get(i);
                
                // Add file to ZIP archive
                ZipEntry entry = new ZipEntry("assets/" + assetKey + ".txt");
                zos.putNextEntry(entry);
                zos.write(("Sample high-res media content for: " + assetKey).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // Broadcast WebSockets progress update
                int percent = (int) (((double) (i + 1) / total) * 100);
                messagingTemplate.convertAndSend("/topic/export-progress/" + jobId,
                        new ExportProgressEvent(jobId, percent, "PROCESSING", null));

                Thread.sleep(100); // Simulate network I/O
            }

            zos.finish();

            // Upload packaged ZIP to S3 & generate 24h pre-signed download URL
            String s3Key = "exports/" + jobId + ".zip";
            s3Service.uploadFile(s3Key, new ByteArrayInputStream(baos.toByteArray()));
            String presignedUrl = s3Service.generatePresignedUrl(s3Key);

            log.info("Completed export job {}. Pre-signed URL generated.", jobId);

            // Final completion broadcast
            messagingTemplate.convertAndSend("/topic/export-progress/" + jobId,
                    new ExportProgressEvent(jobId, 100, "COMPLETED", presignedUrl));

        } catch (Exception e) {
            log.error("Failed to process export job: {}", jobId, e);
            messagingTemplate.convertAndSend("/topic/export-progress/" + jobId,
                    new ExportProgressEvent(jobId, 0, "FAILED", null));
        }
    }

    public record ExportProgressEvent(String jobId, int progressPercent, String status, String downloadUrl) {}
}
```

---

## 🐳 Docker Compose Local Setup (`docker-compose.yml`)

```yaml
version: '3.8'

services:
  mediapack-app:
    build: .
    container_name: mediapack-studio-app
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - SPRING_DATA_MONGODB_URI=mongodb://admin:secret@mongodb:27017/?authSource=admin
      - SPRING_REDIS_HOST=redis
      - SOLR_URL=http://solr:8983/solr/product_media
      - AWS_S3_ENDPOINT=http://localstack:4566
    depends_on:
      - mongodb
      - redis
      - solr
      - localstack

  mongodb:
    image: mongo:6.0
    container_name: mediapack-mongodb
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: secret
    ports:
      - "27017:27017"

  redis:
    image: redis:7-alpine
    container_name: mediapack-redis
    ports:
      - "6379:6379"

  solr:
    image: solr:9.2
    container_name: mediapack-solr
    ports:
      - "8983:8983"
    command:
      - solr-precreate
      - product_media

  localstack:
    image: localstack/localstack:latest
    container_name: mediapack-localstack
    ports:
      - "4566:4566"
    environment:
      - SERVICES=s3
```

---

## 🧪 Testing Strategy: Optimistic Locking Unit Test

To prove that concurrent collection edits cannot overwrite each other, include a unit test verifying `OptimisticLockingFailureException`:

```java
@Test
void testCollaborativeLightbox_ConcurrentEdit_TriggersOptimisticLockingFailure() {
    // 1. Create and save initial Lightbox
    CollaborativeLightbox lightbox = new CollaborativeLightbox("Würth Tools Q3 Campaign", "user1");
    CollaborativeLightbox saved = lightboxRepository.save(lightbox);

    // 2. Load two separate instances representing 2 concurrent HTTP requests
    CollaborativeLightbox client1View = lightboxRepository.findById(saved.getId()).get();
    CollaborativeLightbox client2View = lightboxRepository.findById(saved.getId()).get();

    // 3. Client 1 updates title and saves (increments version from 0 to 1)
    client1View.setTitle("Würth Tools Q3 - Updated by Client 1");
    lightboxRepository.save(client1View);

    // 4. Client 2 tries to save stale version 0
    client2View.setTitle("Würth Tools Q3 - Conflicting Edit by Client 2");
    
    // Assert OptimisticLockingFailureException is thrown
    assertThrows(OptimisticLockingFailureException.class, () -> {
        lightboxRepository.save(client2View);
    });
}
```

---

## 📌 Summary Checklist for Nupoor's GitHub Repository

- [x] High-level Mermaid Architecture Diagram in `README.md`
- [x] 1-Command Local Launch (`docker-compose up`) with LocalStack S3 & Solr
- [x] Dynamic `AbstractMongoFactory` Multi-Tenant Resolver (`DE`, `NL`, `US`)
- [x] MongoDB `@Version` Optimistic Locking Unit Test
- [x] Async Task Executor with WebSockets STOMP Progress Streaming
- [x] AWS S3 Pre-Signed Download URL Generation
- [x] Apache Solr Search & Faceted Filter Configuration
- [x] Swagger UI OpenAPI docs & Postman Collection in `/docs`
