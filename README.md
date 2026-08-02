<div align="center">

<img src="https://img.shields.io/badge/-%F0%9F%93%A6%20MediaPack%20Studio-1a1a2e?style=for-the-badge&labelColor=0f3460" alt="MediaPack Studio" />

# MediaPack Studio

### Distributed Bulk Asset Packaging, Multi-Tenant Engine & Gemma 4 31B AI Integration

*High-performance Java 21 / Spring Boot 3 microservice for enterprise-scale digital asset management*

<br/>

[![Java 21](https://img.shields.io/badge/Java-21-FF6B35?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gemma 4 31B](https://img.shields.io/badge/Gemma_4_31B-Google_Gemini_API-8E44AD?style=flat-square&logo=google&logoColor=white)](https://ai.google.dev/)
[![MongoDB](https://img.shields.io/badge/MongoDB-6.0-47A248?style=flat-square&logo=mongodb&logoColor=white)](https://www.mongodb.com/)
[![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=flat-square&logo=redis&logoColor=white)](https://redis.io/)
[![Apache Solr](https://img.shields.io/badge/Apache_Solr-9.2-D9411E?style=flat-square&logo=apache&logoColor=white)](https://solr.apache.org/)
[![AWS S3](https://img.shields.io/badge/AWS_S3-SDK_v2-FF9900?style=flat-square&logo=amazons3&logoColor=white)](https://aws.amazon.com/s3/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-a855f7?style=flat-square)](LICENSE)

<br/>

[🚀 Quick Start](#-getting-started) · [🏗️ Architecture](#️-system-architecture) · [🤖 AI Capabilities](#-gemma-4-31b-ai-integration) · [🔌 API Reference](#-api-reference) · [🧪 Testing](#-testing) · [🐳 Docker Setup](#-docker-compose-local-setup)

---

</div>

## 📌 Overview

**MediaPack Studio** is a production-grade Spring Boot microservice engineered for large-scale **Digital Asset Management (DAM)** in enterprise e-commerce environments — inspired by real-world engineering challenges at global industrial companies like the **Würth Group**.

It tackles six deeply technical backend problems simultaneously:

| # | Problem Solved | Technology Used |
|---|----------------|-----------------|
| 1 | Multi-region tenant database isolation per HTTP request | Custom `AbstractMongoFactory` + `ThreadLocal` |
| 2 | Non-blocking ZIP export of multi-GB media bundles | Redis Queue + `@Async` + `ZipOutputStream` streaming |
| 3 | Concurrent lightbox edit safety across distributed users | MongoDB `@Version` Optimistic Locking |
| 4 | Real-time download progress visibility | Spring WebSockets (STOMP) |
| 5 | AI-powered asset enrichment & natural language search | Google Gemini API (`gemma-4-31b-it`) |
| 6 | Secure cloud media delivery | AWS S3 Pre-Signed URLs (24h TTL) |

> **Stack:** Java 21 · Spring Boot 3.2 · Google Gemini API (Gemma 4 31B) · MongoDB 6 · Redis 7 · Apache Solr 9.2 · AWS SDK v2 · LocalStack · Docker Compose

---

## 🏗️ System Architecture

```mermaid
graph TD
    User["🧑‍💼 Enterprise User / Branch Manager"] -->|JWT + X-Tenant-Region: DE| GW[Spring Boot API Controller]

    subgraph AI Intelligence Layer
        GW --> AIService["GeminiAiService<br/>Gemma 4 31B (gemma-4-31b-it)"]
        AIService -->|Natural Language Search| SolrService
        AIService -->|Auto Metadata Enrichment| GW
        AIService -->|Smart Campaign Builder| LightboxService
    end

    subgraph Multi-Tenant Database Layer
        GW --> Router["MultiTenantMongoConfig<br/>TenantContext Dynamic Resolution"]
        Router -->|Region DE| DB_DE[(MongoDB: media_catalog_de)]
        Router -->|Region NL| DB_NL[(MongoDB: media_catalog_nl)]
        Router -->|Region US| DB_US[(MongoDB: media_catalog_us)]
    end

    subgraph Search and Collaboration
        GW --> SolrService["Apache Solr Search Engine<br/>Catalog & Media Metadata Filter"]
        GW --> LightboxService["Collaborative Lightbox Service<br/>MongoDB @Version Optimistic Locking"]
    end

    subgraph Asynchronous Export Pipeline
        GW -->|Enqueue Export Task| Worker[Async Media Packager Worker]
        Worker -->|Stream ZIP Bytes| S3[(AWS S3 / LocalStack Storage)]
        Worker -->|Publish Progress %| WS[Spring WebSockets / STOMP Broker]
    end

    WS -->|"Progress: 25%..75%..100% + S3 Link"| User
```

---

## ✨ Key Engineering Highlights

### 🔀 1. Dynamic Multi-Tenant MongoDB Routing

A custom `AbstractMongoFactory` pattern evaluates the incoming `X-Tenant-Region` HTTP header via a `TenantContext` `ThreadLocal` holder and routes all MongoDB queries to the correct regional database — **zero cross-tenant data leakage**.

```
Request: X-Tenant-Region: DE  →  Resolves to: media_catalog_de
Request: X-Tenant-Region: NL  →  Resolves to: media_catalog_nl
Request: X-Tenant-Region: US  →  Resolves to: media_catalog_us
```

```java
// TenantContext.java — ThreadLocal tenant resolution
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get() != null ? CURRENT_TENANT.get() : "DE"; // Default: German tenant
    }
}
```

---

### 🔒 2. MongoDB Optimistic Locking for Collaborative Lightboxes

When multiple enterprise users simultaneously edit a shared **Collaborative Lightbox**, MongoDB's `@Version` field prevents silent data overwrites by throwing `OptimisticLockingFailureException` on stale updates — **zero race conditions**.

```java
@Document(collection = "collaborative_lightboxes")
public class CollaborativeLightbox {
    @Version
    private Long version;  // Auto-incremented by MongoDB on each save
}
```

---

### ⚡ 3. Memory-Efficient Non-Blocking ZIP Export Pipeline

Instead of loading multi-gigabyte media archives into heap RAM, the async worker **streams file chunks directly** from S3 into a `ZipOutputStream`, piping output bytes back to AWS S3 in real time — **< 512MB memory overhead** regardless of export size.

---

### 📡 4. Real-Time WebSocket Progress Broadcasting

Export progress is streamed live to the client via **STOMP WebSockets** at `/topic/export-progress/{jobId}`. The client receives percentage updates (`25% → 75% → 100%`) and a final **AWS S3 Pre-Signed URL** (24h TTL) on completion.

---

### 🔍 5. Apache Solr Faceted Search

High-speed full-text and faceted search across millions of product assets — filtered by resolution, file type, SKU tags, and custom metadata presets using **SolrJ 9.5 client**.

---

### 🤖 6. Gemma 4 31B AI Engine (Google Gemini API)

Centralized `GeminiAiService` powering 4 intelligent microservice features using **`gemma-4-31b-it`**:

1. **Automated Asset Metadata Enrichment**: Generates localized descriptions, alt text, and multi-lingual tags based on SKU attributes and regional context.
2. **Smart AI Search (Natural Language Solr Compiler)**: Translates plain natural language search queries (*"find heavy duty expansion anchor bolts for spring assembly"*) into structured Solr query parameters (`q=...&fileType=...`).
3. **Smart Lightbox Campaign Builder**: Evaluates campaign briefs to auto-curate collections, generate targeted titles & descriptions, and match catalog asset SKUs.
4. **Export Manifest Generation**: Produces structured AI metadata summary manifests included in media package exports.

---

### ☁️ 7. LocalStack S3 Offline Compatibility

Fully testable locally via **LocalStack S3 emulator** in `docker-compose.yml` — no cloud account required for local development and CI pipelines.

---

## 🤖 Gemma 4 31B AI Integration

The microservice integrates **Gemma 4 31B** via a resilient service layer with thought-token filtering, fallback handling, and secure API key management:

```yaml
# application-local.yml
gemini:
  api:
    key: ${GEMINI_API_KEY}
    model: ${GEMINI_MODEL:gemma-4-31b-it}
```

### Features at a Glance:

- **Thought-Token Extraction**: Filters out intermediate reasoning blocks (`"thought": true`) returned by instruction-tuned Gemma models to parse clean JSON responses.
- **Offline Resiliency**: Automatically falls back to deterministic rule-based generators when MongoDB or external API endpoints are offline.
- **Non-Blocking UI Execution**: Frontend uses optimistic UI state updates so campaign generation and metadata enrichment respond instantly.

---

## 🚀 Getting Started

### Prerequisites

- **Docker Desktop** 24+ ([download](https://www.docker.com/products/docker-desktop/))
- **Java 21** (for local Maven builds only)
- **Google Gemini API Key** (optional, for live AI features)

### ⚡ 1-Command Launch

Spin up the full infrastructure stack — Spring Boot app, MongoDB, Redis, Apache Solr, and LocalStack S3 — in a single command:

```bash
git clone https://github.com/your-username/mediapack-studio.git
cd mediapack-studio
GEMINI_API_KEY="your-gemini-api-key" docker-compose up --build
```

### Access Points

| Service | URL |
|---------|-----|
| 🖥️ **WebSocket & DAM Studio Dashboard** | http://localhost:8080 |
| 🔌 **Spring Boot REST API** | http://localhost:8080/api |
| 🔍 **Apache Solr Admin UI** | http://localhost:8983/solr |
| ☁️ **LocalStack S3 Endpoint** | http://localhost:4566 |

---

## 🔌 API Reference

All endpoints require the `X-Tenant-Region` header for multi-tenant database routing.

<details>
<summary><b>🤖 Gemini AI Endpoints (Gemma 4 31B)</b></summary>

<br/>

### Enrich Asset Metadata

```http
POST /api/ai/enrich-asset
X-Tenant-Region: DE
Content-Type: application/json
```

```json
{
  "sku": "SKU-MP-8821",
  "fileName": "M8x30 Stainless Steel Hex Head Bolt.png",
  "fileType": "IMAGE_PNG",
  "sizeInBytes": 2048000,
  "width": 3840,
  "height": 2160,
  "tags": ["bolt", "m8"]
}
```

### Smart AI Search (Solr Query Translation)

```http
GET /api/ai/smart-search?prompt=find+high+strength+stainless+steel+bolts
X-Tenant-Region: DE
```

### Smart Lightbox Campaign Builder

```http
POST /api/ai/generate-lightbox?prompt=Assemble+a+spring+industrial+fasteners+collection&ownerUserId=NupoorD
X-Tenant-Region: DE
```

### Check Gemini API Status

```http
GET /api/ai/status
```

</details>

<details>
<summary><b>📁 Asset Management Endpoints</b></summary>

<br/>

### Create Media Asset

```http
POST /api/assets
X-Tenant-Region: DE
Content-Type: application/json
```

```json
{
  "sku": "SKU-WURTH-001",
  "fileName": "industrial_bolt_highres.png",
  "s3Key": "assets/industrial_bolt_highres.png",
  "fileType": "IMAGE_PNG",
  "sizeInBytes": 1048576,
  "width": 1920,
  "height": 1080,
  "tags": ["fastener", "bolt", "würth"]
}
```

### List Assets by Tenant

```http
GET /api/assets
X-Tenant-Region: NL
```

</details>

<details>
<summary><b>💡 Collaborative Lightbox Endpoints</b></summary>

<br/>

### Create Lightbox

```http
POST /api/lightboxes?title=Q3+Fastener+Campaign&ownerUserId=user1
X-Tenant-Region: DE
```

### Add Asset to Lightbox (Increments `@Version`)

```http
POST /api/lightboxes/{id}/assets/{assetId}
X-Tenant-Region: DE
```

</details>

<details>
<summary><b>📦 Async ZIP Export Endpoint</b></summary>

<br/>

### Trigger Async ZIP Export

```http
POST /api/exports/trigger
Content-Type: application/json
```

```json
{
  "jobId": "job-export-101",
  "assetKeys": ["SKU-WURTH-001", "SKU-WURTH-002"],
  "tenantRegion": "DE"
}
```

**Subscribe to real-time progress via WebSocket STOMP:**

```
Topic: /topic/export-progress/job-export-101
```

</details>

---

## 🗄️ Data Models

<details>
<summary><b>MediaAsset — Asset Metadata Document</b></summary>

```java
@Document(collection = "media_assets")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaAsset {
    @Id
    private String id;

    @Indexed
    private String sku;              // Product SKU reference

    private String fileName;
    private String s3Key;
    private String fileType;         // "IMAGE_PNG", "DOCUMENT_PDF", "CAD_3D"
    private long   sizeInBytes;
    private int    width;
    private int    height;
    private List<String> tags;
    private Instant createdAt;
}
```

</details>

<details>
<summary><b>CollaborativeLightbox — Optimistic Locking Document</b></summary>

```java
@Document(collection = "collaborative_lightboxes")
public class CollaborativeLightbox {
    @Id
    private String id;

    private String title;
    private String ownerUserId;
    private List<String> sharedWithUserIds;
    private List<String> assetIds;

    @Version
    private Long version;            // Auto-incremented by MongoDB on each save

    private Instant lastModifiedAt;
}
```

</details>

---

## 🐳 Docker Compose Local Setup

```yaml
services:
  mediapack-app:    # Spring Boot 3 Application  → :8080
  mongodb:          # MongoDB 6.0                → :27017
  redis:            # Redis 7 Alpine             → :6379
  solr:             # Apache Solr 9.2            → :8983
  localstack:       # LocalStack S3 Emulator     → :4566
```

**Environment Variables (auto-configured in Docker Compose):**

| Variable | Value |
|----------|-------|
| `SPRING_DATA_MONGODB_URI` | `mongodb://admin:secret@mongodb:27017` |
| `SPRING_REDIS_HOST` | `redis` |
| `SOLR_URL` | `http://solr:8983/solr/product_media` |
| `AWS_S3_ENDPOINT` | `http://localstack:4566` |
| `GEMINI_API_KEY` | `${GEMINI_API_KEY}` |
| `GEMINI_MODEL` | `gemma-4-31b-it` |
| `SPRING_PROFILES_ACTIVE` | `local` |

---

## 🧪 Testing

### Run Full Test Suite

```bash
mvn test
```

### Key Test: Optimistic Locking Concurrency Validation

The test suite includes a critical concurrent-edit test verifying that `OptimisticLockingFailureException` is correctly raised when two clients attempt to update the same lightbox document simultaneously:

```java
@Test
void testCollaborativeLightbox_ConcurrentEdit_TriggersOptimisticLockingFailure() {
    CollaborativeLightbox client1View = lightboxRepository.findById(saved.getId()).get();
    CollaborativeLightbox client2View = lightboxRepository.findById(saved.getId()).get();

    client1View.setTitle("Würth Tools Q3 - Updated by Client 1");
    lightboxRepository.save(client1View);

    client2View.setTitle("Würth Tools Q3 - Conflicting Edit by Client 2");
    assertThrows(OptimisticLockingFailureException.class, () -> {
        lightboxRepository.save(client2View);
    });
}
```

---

## 📁 Project Structure

```
mediapack-studio/
├── src/
│   ├── main/
│   │   ├── java/com/nupoor/mediapack/
│   │   │   ├── config/
│   │   │   │   └── tenant/
│   │   │   │       ├── TenantContext.java           # ThreadLocal tenant holder
│   │   │   │       └── MultiTenantMongoConfig.java  # Dynamic MongoDB router
│   │   │   ├── controller/
│   │   │   │   └── GeminiAiController.java          # AI feature endpoints
│   │   │   ├── model/
│   │   │   │   ├── MediaAsset.java                  # Asset metadata document
│   │   │   │   └── CollaborativeLightbox.java        # Optimistic locking document
│   │   │   └── service/
│   │   │       ├── GeminiAiService.java              # Gemma 4 31B AI Service layer
│   │   │       └── AsyncMediaPackagerWorker.java     # ZIP export + WebSocket progress
│   │   └── resources/
│   │       └── static/
│   │           └── index.html                       # WebSocket & DAM Studio dashboard
│   └── test/
│       └── java/com/nupoor/mediapack/
│           └── GeminiAiControllerTest.java           # AI feature test suite
├── Dockerfile
├── docker-compose.yml
├── .env.example
└── pom.xml
```

---

## 🛠️ Technology Stack

| Layer | Technology | Version / Model |
|-------|-----------|-----------------|
| **Language** | Java | 21 (LTS) |
| **Framework** | Spring Boot | 3.2.5 |
| **AI Intelligence** | Google Gemini API (Gemma 4 31B) | `gemma-4-31b-it` |
| **Database** | MongoDB | 6.0 |
| **Cache / Queue** | Redis | 7 Alpine |
| **Search Engine** | Apache Solr | 9.2 |
| **Cloud Storage** | AWS SDK v2 (S3 + Presigner) | 2.25.15 |
| **Real-Time** | Spring WebSockets (STOMP) | — |
| **Local S3 Emulator** | LocalStack | Latest |
| **Testing** | JUnit 5 + Mockito | — |
| **Build** | Maven | 3.x |
| **Containerization** | Docker Compose | 3.8 |

---

## ✅ Feature Checklist

- [x] Mermaid System Architecture Diagram with AI Layer
- [x] 1-Command Local Launch (`docker-compose up`) with LocalStack S3 & Solr
- [x] Dynamic `AbstractMongoFactory` Multi-Tenant Resolver (`DE`, `NL`, `US`)
- [x] Google Gemini API (`gemma-4-31b-it`) Multi-lingual Asset Enrichment
- [x] Natural Language Solr Compiler for Smart AI Search
- [x] Smart AI Lightbox Campaign Builder with Non-Blocking UI Execution
- [x] MongoDB `@Version` Optimistic Locking — Collaborative Lightboxes
- [x] Redis Async Task Executor with WebSockets STOMP Progress Streaming
- [x] AWS S3 Pre-Signed Download URL Generation (24h TTL)
- [x] Apache Solr Faceted Search & Metadata Filter Configuration
- [x] Memory-Efficient ZIP Streaming (`< 512MB` for multi-GB exports)
- [x] LocalStack Offline AWS Compatibility (no cloud account required)
- [x] JUnit 5 Test Coverage for All Services & AI Controllers

---

## 👩‍💻 Author

**Nupoor Dhamal** — Senior Java Backend Developer

*Specializing in enterprise Java microservices, distributed systems, AI integrations, and industrial e-commerce backend architecture.*

[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-0A66C2?style=flat-square&logo=linkedin&logoColor=white)](https://linkedin.com/in/your-profile)
[![GitHub](https://img.shields.io/badge/GitHub-Follow-181717?style=flat-square&logo=github&logoColor=white)](https://github.com/your-username)

---

<div align="center">

*Built with ☕ Java 21, 🍃 Spring Boot 3, 🤖 Gemma 4 31B, and ❤️ for clean distributed systems engineering.*

</div>
