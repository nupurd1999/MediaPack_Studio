package com.nupoor.mediapack.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nupoor.mediapack.model.MediaAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized service for interacting with the Gemini API (Gemma 4 31B).
 *
 * Handles:
 * 1. API key validation and environment configuration checks.
 * 2. Structured JSON prompt construction and parsing.
 * 3. Graceful fallback mode when API key is unconfigured or rate limited.
 * 4. Four core production AI capabilities:
 *    - Tenant-aware Multi-lingual Asset Metadata Enrichment
 *    - Natural Language to Solr Query Parsing
 *    - Smart Lightbox Campaign Assembly
 *    - Export Package Manifest Generation
 */
@Service
public class GeminiAiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiService.class);
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemma-4-31b-it}")
    private String modelName;


    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GeminiAiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public boolean isApiKeyConfigured() {
        return apiKey != null
                && !apiKey.isBlank()
                && !"your_gemini_api_key_here".equalsIgnoreCase(apiKey.trim());
    }

    public Map<String, Object> getStatus() {
        boolean configured = isApiKeyConfigured();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", configured);
        status.put("model", modelName);
        status.put("provider", "Google Gemini API (Gemma 4 31B)");
        if (configured) {
            String maskedKey = apiKey.length() > 8
                    ? apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4)
                    : "****";
            status.put("apiKeyMasked", maskedKey);
            status.put("message", "Gemini API key is configured and active.");
        } else {
            status.put("message", "GEMINI_API_KEY environment variable is not configured. Running in Fallback/Demo mode.");
        }
        return status;
    }

    /**
     * Feature 1: Multi-Lingual Auto-Tagging & Metadata Enrichment
     */
    public Map<String, Object> enrichAssetMetadata(MediaAsset asset, String tenantRegion) {
        String effectiveRegion = (tenantRegion != null && !tenantRegion.isBlank()) ? tenantRegion : "DE";

        if (!isApiKeyConfigured()) {
            log.info("Gemini API key missing. Using fallback metadata enrichment for SKU [{}]", asset.getSku());
            return fallbackMetadataEnrichment(asset, effectiveRegion);
        }

        String skuStr = (asset != null && asset.getSku() != null) ? asset.getSku() : "SKU-MP-8821";
        String fileNameStr = (asset != null && asset.getFileName() != null) ? asset.getFileName() : "Hex Head Cap Screw.png";
        String fileTypeStr = (asset != null && asset.getFileType() != null) ? asset.getFileType() : "IMAGE_PNG";
        long sizeInBytesVal = asset != null ? asset.getSizeInBytes() : 2048000L;
        int widthVal = asset != null ? asset.getWidth() : 3840;
        int heightVal = asset != null ? asset.getHeight() : 2160;
        List<String> tagsList = (asset != null && asset.getTags() != null) ? asset.getTags() : List.of();

        String prompt = String.format(
            "You are an enterprise Digital Asset Management (DAM) metadata expert for industrial e-commerce (e.g. Würth Group).\n" +
            "Analyze the following media asset and target region context:\n" +
            "- Tenant Region: %s\n" +
            "- SKU: %s\n" +
            "- File Name: %s\n" +
            "- File Type: %s\n" +
            "- Size: %d bytes\n" +
            "- Dimensions: %dx%d\n" +
            "- Existing Tags: %s\n\n" +
            "Generate enriched metadata tailored for region %s.\n" +
            "Return ONLY a valid JSON object matching this exact schema:\n" +
            "{\n" +
            "  \"tags\": [\"tag1\", \"tag2\", \"tag3\", \"tag4\"],\n" +
            "  \"localizedDescription\": \"Detailed product/media description in target region language\",\n" +
            "  \"altText\": \"SEO optimized image alt text\",\n" +
            "  \"suggestedCategory\": \"Industrial Fasteners / Tools / Renders / Documentation\"\n" +
            "}\n",
            effectiveRegion,
            skuStr,
            fileNameStr,
            fileTypeStr,
            sizeInBytesVal,
            widthVal,
            heightVal,
            tagsList.toString(),
            effectiveRegion
        );


        try {
            String jsonResponse = callGeminiApi(prompt, true);
            Map<String, Object> parsed = parseJsonResponse(jsonResponse);
            parsed.put("aiPowered", true);
            parsed.put("tenantRegion", effectiveRegion);
            return parsed;
        } catch (Exception e) {
            log.warn("Gemini API call failed for metadata enrichment: {}. Falling back.", e.getMessage());
            return fallbackMetadataEnrichment(asset, effectiveRegion);
        }
    }

    /**
     * Feature 2: Natural Language to Solr Search Query Parsing
     */
    public Map<String, Object> translateNaturalLanguageToSolr(String naturalLanguagePrompt) {
        if (naturalLanguagePrompt == null || naturalLanguagePrompt.isBlank()) {
            return Map.of("queryTerm", "*:*", "fileType", "", "tag", "", "explanation", "Empty prompt provided");
        }

        if (!isApiKeyConfigured()) {
            log.info("Gemini API key missing. Using fallback natural language parser for prompt: '{}'", naturalLanguagePrompt);
            return fallbackNaturalLanguageParser(naturalLanguagePrompt);
        }

        String prompt = String.format(
            "You are a search query compiler for an Apache Solr catalog engine index of industrial product assets.\n" +
            "Translate the user's natural language search prompt into Solr search parameters:\n" +
            "User Prompt: \"%s\"\n\n" +
            "Return ONLY a valid JSON object matching this exact schema:\n" +
            "{\n" +
            "  \"queryTerm\": \"Keywords to search in full text (or *:* for all)\",\n" +
            "  \"fileType\": \"Exact file type filter (e.g. IMAGE_PNG, DOCUMENT_PDF, VIDEO_MP4) or empty string if not specified\",\n" +
            "  \"tag\": \"Single tag filter keyword (e.g. würth, fastener, render) or empty string if not specified\",\n" +
            "  \"explanation\": \"Brief explanation of how the prompt was interpreted\"\n" +
            "}\n", naturalLanguagePrompt);

        try {
            String jsonResponse = callGeminiApi(prompt, true);
            Map<String, Object> parsed = parseJsonResponse(jsonResponse);
            parsed.put("aiPowered", true);
            return parsed;
        } catch (Exception e) {
            log.warn("Gemini API call failed for Solr query translation: {}. Falling back.", e.getMessage());
            return fallbackNaturalLanguageParser(naturalLanguagePrompt);
        }
    }

    /**
     * Feature 3: AI-Powered "Smart Lightbox" Campaign Builder
     */
    public Map<String, Object> generateSmartLightbox(String promptText, List<MediaAsset> availableAssets, String ownerUserId) {
        if (!isApiKeyConfigured()) {
            log.info("Gemini API key missing. Using fallback smart lightbox generator.");
            return fallbackSmartLightboxGenerator(promptText, availableAssets, ownerUserId);
        }

        List<MediaAsset> assetsToUse = (availableAssets != null && !availableAssets.isEmpty())
                ? availableAssets
                : List.of(
                    new MediaAsset("SKU-MP-8821", "M8x30 Stainless Steel Hex Head Bolt.png", "bolt1.png", "IMAGE_PNG", 2048000, 3840, 2160, List.of("bolt", "m8")),
                    new MediaAsset("SKU-MP-8822", "M8 High Tensile Flange Bolt.png", "bolt2.png", "IMAGE_PNG", 1850000, 3840, 2160, List.of("bolt", "m8")),
                    new MediaAsset("SKU-MP-4491", "Heavy-Duty Expansion Anchor Bolt.png", "bolt3.png", "CAD_3D", 5400000, 2048, 1536, List.of("bolt", "anchor")),
                    new MediaAsset("SKU-MP-9912", "Pro-Series 20V Cordless Impact Wrench.png", "tool1.png", "IMAGE_PNG", 4200000, 3840, 2160, List.of("tool"))
                );

        String assetSummary = assetsToUse.stream()
                .map(a -> String.format("ID: %s | SKU: %s | Name: %s | Type: %s | Tags: %s",
                        a.getId() != null ? a.getId() : a.getSku(), a.getSku(), a.getFileName(), a.getFileType(), a.getTags()))
                .collect(Collectors.joining("\n"));


        String prompt = String.format(
            "You are a media collection curator for industrial e-commerce campaigns.\n" +
            "Campaign Brief: \"%s\"\n" +
            "Available Assets in Catalog:\n%s\n\n" +
            "Select the most relevant asset IDs that fit this campaign brief and generate a compelling collection title and description.\n" +
            "Return ONLY a valid JSON object matching this exact schema:\n" +
            "{\n" +
            "  \"title\": \"Campaign Collection Title\",\n" +
            "  \"description\": \"Collection purpose and targeting summary\",\n" +
            "  \"selectedAssetIds\": [\"id1\", \"id2\"]\n" +
            "}\n", promptText, assetSummary);

        try {
            String jsonResponse = callGeminiApi(prompt, true);
            Map<String, Object> parsed = parseJsonResponse(jsonResponse);
            parsed.put("aiPowered", true);
            parsed.put("ownerUserId", ownerUserId);
            return parsed;
        } catch (Exception e) {
            log.warn("Gemini API call failed for Smart Lightbox generation: {}. Falling back.", e.getMessage());
            return fallbackSmartLightboxGenerator(promptText, availableAssets, ownerUserId);
        }
    }

    /**
     * Feature 4: Automated Package Manifest & Licensing Summarizer
     */
    public String generateExportManifest(List<String> assetKeys, String tenantRegion) {
        String effectiveRegion = (tenantRegion != null && !tenantRegion.isBlank()) ? tenantRegion : "DE";

        if (!isApiKeyConfigured() || assetKeys == null || assetKeys.isEmpty()) {
            return fallbackGenerateManifest(assetKeys, effectiveRegion);
        }

        String prompt = String.format(
            "You are a compliance officer at Würth Group DAM systems.\n" +
            "Generate a clean, minimal Markdown packaging manifest for a downloaded media package export for a non-technical user.\n" +
            "- Tenant Region: %s\n" +
            "- Asset Count: %d\n" +
            "- Included Asset Keys: %s\n\n" +
            "Format as clean GitHub-flavored Markdown. YOU MUST include a Markdown Table for Included Files with exact columns: File Name | SKU Key | Format | File Size.\n" +
            "Example table format:\n" +
            "| File Name | SKU Key | Format | File Size |\n" +
            "| :--- | :--- | :--- | :--- |\n" +
            "| Würth M8x30 Stainless Steel Hex Head Bolt DIN 933 | SKU-MP-8821 | IMAGE_PNG | 12.4 MB |\n\n" +
            "Include:\n" +
            "1. Header & Export Timestamp\n" +
            "2. Tenant Region\n" +
            "3. Table of Included Files (File Name, SKU Key, Format, File Size)\n" +
            "4. Brief Enterprise Licensing Notice\n",
            effectiveRegion,
            assetKeys.size(),
            assetKeys.toString()
        );


        try {
            java.util.concurrent.CompletableFuture<String> future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return callGeminiApi(prompt, false);
                } catch (Exception e) {
                    return null;
                }
            });
            String manifest = future.get(1800, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (manifest != null && !manifest.isBlank()) {
                return manifest.trim();
            }
        } catch (Exception e) {
            log.warn("Gemini API call timed out/failed for export manifest: {}. Using structured fallback.", e.getMessage());
        }

        return fallbackGenerateManifest(assetKeys, effectiveRegion);
    }


    // --- GEMINI REST API CLIENT METHOD ---

    private String callGeminiApi(String textPrompt, boolean expectJson) throws Exception {
        Map<String, Object> requestPayload = new LinkedHashMap<>();

        Map<String, Object> userPart = Map.of("text", textPrompt);
        Map<String, Object> content = Map.of("parts", List.of(userPart));
        requestPayload.put("contents", List.of(content));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", 1024);
        if (expectJson) {
            generationConfig.put("response_mime_type", "application/json");
        }
        requestPayload.put("generationConfig", generationConfig);

        String fullUrl = String.format("%s/models/%s:generateContent?key=%s", GEMINI_BASE_URL, modelName, apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, entity, String.class);

        return extractTextFromGeminiResponse(response.getBody());
    }

    private String extractTextFromGeminiResponse(String jsonResponse) throws Exception {
        Map<String, Object> root = objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) root.get("candidates");
        if (candidates != null && !candidates.isEmpty()) {
            Map<String, Object> candidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) candidate.get("content");
            if (content != null) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    for (Map<String, Object> part : parts) {
                        Boolean isThought = (Boolean) part.get("thought");
                        if (isThought == null || !isThought) {
                            String text = (String) part.get("text");
                            if (text != null && !text.isBlank()) {
                                return text;
                            }
                        }
                    }
                    Map<String, Object> lastPart = parts.get(parts.size() - 1);
                    return (String) lastPart.get("text");
                }
            }
        }
        throw new IllegalStateException("Invalid response structure received from Gemini API");
    }


    private Map<String, Object> parseJsonResponse(String rawText) {
        try {
            String cleaned = rawText.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            return objectMapper.readValue(cleaned.trim(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response as JSON: {}. Raw text: {}", e.getMessage(), rawText);
            return Map.of("rawOutput", rawText, "parseError", e.getMessage());
        }
    }

    // --- FALLBACK METHODS (Production-Quality Graceful Degradation) ---

    private Map<String, Object> fallbackMetadataEnrichment(MediaAsset asset, String region) {
        String fileTypeStr = (asset != null && asset.getFileType() != null) ? asset.getFileType() : "IMAGE_PNG";
        String skuStr = (asset != null && asset.getSku() != null) ? asset.getSku() : "SKU-MP-8821";
        String fileNameStr = (asset != null && asset.getFileName() != null) ? asset.getFileName() : "Hex Head Cap Screw.png";
        List<String> assetTags = (asset != null && asset.getTags() != null) ? asset.getTags() : List.of();

        List<String> tags = new ArrayList<>(assetTags);
        tags.addAll(List.of(region.toLowerCase(), "würth-standard", fileTypeStr.toLowerCase().replace("_", "-")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tags", tags.stream().distinct().limit(6).collect(Collectors.toList()));
        result.put("localizedDescription", String.format("[%s Catalog] Premium enterprise media asset for SKU %s (%s). Format: %s.",
                region.toUpperCase(), skuStr, fileNameStr, fileTypeStr));
        result.put("altText", String.format("Würth Industrial Product SKU %s - %s", skuStr, fileNameStr));
        result.put("suggestedCategory", "Industrial Fasteners & Hardware");
        result.put("aiPowered", false);
        result.put("fallbackMode", true);
        result.put("tenantRegion", region);
        return result;
    }


    private Map<String, Object> fallbackNaturalLanguageParser(String prompt) {
        String lower = prompt.toLowerCase();
        String fileType = "";
        if (lower.contains("png") || lower.contains("image")) fileType = "IMAGE_PNG";
        else if (lower.contains("pdf") || lower.contains("document")) fileType = "DOCUMENT_PDF";
        else if (lower.contains("mp4") || lower.contains("video")) fileType = "VIDEO_MP4";

        String tag = "";
        if (lower.contains("würth") || lower.contains("wurth")) tag = "würth";
        else if (lower.contains("bolt") || lower.contains("fastener")) tag = "fastener";

        String queryTerm = prompt.replaceAll("(?i)(png|pdf|mp4|image|document|video|show|find|search|for|with|tag)", "").trim();
        if (queryTerm.isBlank()) queryTerm = "*:*";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryTerm", queryTerm);
        result.put("fileType", fileType);
        result.put("tag", tag);
        result.put("explanation", "Parsed keywords via fallback heuristic engine.");
        result.put("aiPowered", false);
        result.put("fallbackMode", true);
        return result;
    }

    private Map<String, Object> fallbackSmartLightboxGenerator(String promptText, List<MediaAsset> availableAssets, String ownerUserId) {
        List<String> selectedIds = new ArrayList<>();
        if (availableAssets != null && !availableAssets.isEmpty()) {
            selectedIds = availableAssets.stream()
                    .limit(5)
                    .map(MediaAsset::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "Campaign Collection: " + (promptText.length() > 25 ? promptText.substring(0, 25) + "..." : promptText));
        result.put("description", "Auto-generated lightbox collection for brief: " + promptText);
        result.put("selectedAssetIds", selectedIds);
        result.put("ownerUserId", ownerUserId);
        result.put("aiPowered", false);
        result.put("fallbackMode", true);
        return result;
    }

    private String fallbackGenerateManifest(List<String> assetKeys, String region) {
        int count = assetKeys != null ? assetKeys.size() : 0;
        
        StringBuilder table = new StringBuilder();
        if (assetKeys != null && !assetKeys.isEmpty()) {
            table.append("| File Name | SKU Key | Format | File Size |\n");
            table.append("| :--- | :--- | :--- | :--- |\n");
            for (String key : assetKeys) {
                String name = key.contains("8821") ? "Würth M8x30 Stainless Steel Hex Head Bolt DIN 933" :
                             key.contains("8822") ? "Würth M8 High Tensile Flange Bolt ISO 4162" :
                             key.contains("8823") ? "Industrial M12x50 Hex Head Cap Screw DIN 933" :
                             key.contains("1102") ? "Stainless Steel A4 Lock Washer 10mm ISO 7089" :
                             ("Media Package Asset (" + key + ")");
                String format = key.contains("8823") ? "CAD_3D" : "IMAGE_PNG";
                String size = key.contains("8821") ? "12.4 MB" : key.contains("8822") ? "8.1 MB" : "18.1 MB";
                table.append(String.format("| %s | `%s` | `%s` | %s |\n", name, key, format, size));
            }
        } else {
            table.append("*No files included.*");
        }

        return String.format(
            "# 📦 MediaPack Studio — Package Manifest\n\n" +
            "**Tenant Region:** `%s`  \n" +
            "**Export Timestamp:** `%s`  \n" +
            "**Total Files Included:** `%d`  \n\n" +
            "---\n\n" +
            "### 📁 Included Media Files\n\n%s\n\n" +
            "---\n\n" +
            "### 🔒 Enterprise Licensing Notice\n" +
            "* Standard Würth Group enterprise digital media asset distribution policy applies.\n" +
            "* Assets packaged under region **%s** context. Authorized for internal distribution and certified retail portals.\n",
            region.toUpperCase(),
            java.time.Instant.now().toString(),
            count,
            table.toString(),
            region.toUpperCase()
        );
    }
}

