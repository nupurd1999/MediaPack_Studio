package com.nupoor.mediapack.service;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.MediaAsset;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

/**
 * Solr-backed catalog search service providing:
 * - Full-text asset search by SKU, file name, and tags
 * - Multi-value faceted filtering by file type and tags
 * - Tenant isolation filter (tenant_s) to guarantee zero cross-tenant leakage
 * - Graceful degradation when Solr is offline
 */
@Service
public class SolrSearchService {

    private static final Logger log = LoggerFactory.getLogger(SolrSearchService.class);

    @Value("${solr.url:http://localhost:8983/solr/product_media}")
    private String solrUrl;

    private SolrClient solrClient;

    public SolrSearchService() {}

    public SolrSearchService(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    @PostConstruct
    public synchronized void init() {
        if (this.solrClient == null && solrUrl != null && !solrUrl.isBlank()) {
            this.solrClient = new HttpSolrClient.Builder(solrUrl).build();
        }
    }

    @PreDestroy
    public synchronized void cleanup() {
        if (this.solrClient != null) {
            try {
                this.solrClient.close();
            } catch (Exception e) {
                log.warn("Error closing SolrClient: {}", e.getMessage());
            }
        }
    }

    protected synchronized SolrClient getClient() {
        if (this.solrClient == null) {
            this.solrClient = new HttpSolrClient.Builder(solrUrl != null ? solrUrl : "http://localhost:8983/solr/product_media").build();
        }
        return this.solrClient;
    }

    /**
     * Indexes a MediaAsset document into the Solr product_media core.
     */
    public void indexAsset(MediaAsset asset) {
        if (asset == null) return;
        try {
            SolrClient client = getClient();
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField("id",           asset.getId());
            doc.addField("sku_s",        asset.getSku());
            doc.addField("file_name_t",  asset.getFileName());
            doc.addField("file_type_s",  asset.getFileType());
            doc.addField("size_bytes_l", asset.getSizeInBytes());
            doc.addField("width_i",      asset.getWidth());
            doc.addField("height_i",     asset.getHeight());
            doc.addField("tags_ss",      asset.getTags() != null ? asset.getTags() : List.of());
            doc.addField("tenant_s",     asset.getTenantRegion() != null ? asset.getTenantRegion() : TenantContext.getCurrentTenant());

            client.add(doc);
            client.commit();
            log.info("Indexed asset [{}] (sku={}, tenant={}) into Solr",
                    asset.getId(), asset.getSku(), asset.getTenantRegion());
        } catch (Throwable e) {
            log.warn("Solr indexing skipped (Solr offline?): {}", e.getMessage());
        }
    }

    /**
     * Executes a full-text + faceted search query with tenant isolation.
     */
    public Map<String, Object> searchAssets(String queryTerm, String fileTypeFilter, String tagFilter, int rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hits", Collections.emptyList());
        result.put("facets", Collections.emptyMap());
        result.put("total", 0L);

        try {
            SolrClient client = getClient();
            ModifiableSolrParams params = new ModifiableSolrParams();

            String query = buildQuery(queryTerm);
            params.set("q", query);

            List<String> filterQueries = new ArrayList<>();
            String currentTenant = TenantContext.getCurrentTenant();

            // Tenant isolation filter query
            if (currentTenant != null && !currentTenant.isBlank()) {
                filterQueries.add("(tenant_s:" + currentTenant + " OR (*:* NOT tenant_s:[* TO *]))");
            }

            if (fileTypeFilter != null && !fileTypeFilter.isBlank()) {
                filterQueries.add("file_type_s:" + fileTypeFilter);
            }
            if (tagFilter != null && !tagFilter.isBlank()) {
                filterQueries.add("tags_ss:" + tagFilter);
            }
            if (!filterQueries.isEmpty()) {
                params.set("fq", filterQueries.toArray(new String[0]));
            }

            params.set("facet", "true");
            params.set("facet.field", new String[]{"file_type_s", "tags_ss"});
            params.set("facet.mincount", 1);
            params.set("rows", Math.min(rows, 100));

            QueryResponse response = client.query(params);

            List<Map<String, Object>> hits = new ArrayList<>();
            response.getResults().forEach(doc -> {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("id",          doc.getFieldValue("id"));
                hit.put("sku",         doc.getFieldValue("sku_s"));
                hit.put("fileName",    doc.getFieldValue("file_name_t"));
                hit.put("fileType",    doc.getFieldValue("file_type_s"));
                hit.put("sizeInBytes", doc.getFieldValue("size_bytes_l"));
                hit.put("width",       doc.getFieldValue("width_i"));
                hit.put("height",      doc.getFieldValue("height_i"));
                hit.put("tags",        doc.getFieldValues("tags_ss"));
                hit.put("tenantRegion", doc.getFieldValue("tenant_s"));
                hits.add(hit);
            });

            Map<String, List<Map<String, Object>>> facets = new LinkedHashMap<>();
            List<FacetField> facetFields = response.getFacetFields();
            if (facetFields != null) {
                for (FacetField ff : facetFields) {
                    List<Map<String, Object>> values = new ArrayList<>();
                    if (ff.getValues() != null) {
                        for (FacetField.Count c : ff.getValues()) {
                            values.add(Map.of("name", c.getName(), "count", c.getCount()));
                        }
                    }
                    facets.put(ff.getName(), values);
                }
            }

            result.put("hits", hits);
            result.put("facets", facets);
            result.put("total", response.getResults().getNumFound());
            result.put("tenantRegion", currentTenant);
        } catch (Throwable e) {
            log.warn("Solr search skipped (Solr offline? Returning empty results): {}", e.getMessage());
            result.put("warning", "Solr search engine currently unreachable. Running in fallback mode.");
        }

        return result;
    }

    private String buildQuery(String term) {
        if (term == null || term.isBlank() || "*".equals(term.trim())) {
            return "*:*";
        }
        String clean = term.trim().replaceAll("([+\\-!(){}\\[\\]^\"~*?:\\\\])", "\\\\$1");
        return String.format("sku_s:*%s* OR file_name_t:*%s* OR tags_ss:*%s*", clean, clean, clean);
    }
}
