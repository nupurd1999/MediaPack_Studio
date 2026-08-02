package com.nupoor.mediapack.service;

import com.nupoor.mediapack.model.MediaAsset;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Solr-backed catalog search service providing:
 * - Full-text asset search by SKU, file name, and tags
 * - Multi-value faceted filtering by file type and tags
 * - Graceful degradation when Solr is offline (returns empty results + warning)
 */
@Service
public class SolrSearchService {

    private static final Logger log = LoggerFactory.getLogger(SolrSearchService.class);

    @Value("${solr.url:http://localhost:8983/solr/product_media}")
    private String solrUrl;

    private SolrClient buildClient() {
        return new Http2SolrClient.Builder(solrUrl).build();
    }

    /**
     * Indexes a MediaAsset document into the Solr product_media core.
     * Called automatically after every asset save via MediaAssetService.
     */
    public void indexAsset(MediaAsset asset) {
        try (SolrClient client = buildClient()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField("id",           asset.getId());
            doc.addField("sku_s",        asset.getSku());
            doc.addField("file_name_t",  asset.getFileName());
            doc.addField("file_type_s",  asset.getFileType());
            doc.addField("size_bytes_l", asset.getSizeInBytes());
            doc.addField("width_i",      asset.getWidth());
            doc.addField("height_i",     asset.getHeight());
            doc.addField("tags_ss",      asset.getTags()); // multiValued field

            client.add(doc);
            client.commit();
            log.info("Indexed asset [{}] (sku={}) into Solr", asset.getId(), asset.getSku());
        } catch (Throwable e) {
            log.warn("Solr indexing skipped (Solr offline?): {}", e.getMessage());
        }

    }

    /**
     * Executes a full-text + faceted search query.
     *
     * Returns a result map containing:
     *   "hits"   → List<Map<String, Object>> of matching documents
     *   "facets" → Map<String, List<FacetCount>> keyed by field name
     *   "total"  → Long count of matching documents
     *
     * @param queryTerm     free-text search term (matched against sku, file name, tags)
     * @param fileTypeFilter optional file_type_s filter value (e.g. "IMAGE_PNG")
     * @param tagFilter     optional tag filter value (e.g. "würth")
     * @param rows          max results to return (default 50)
     */
    public Map<String, Object> searchAssets(String queryTerm, String fileTypeFilter, String tagFilter, int rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hits", Collections.emptyList());
        result.put("facets", Collections.emptyMap());
        result.put("total", 0L);

        try (SolrClient client = buildClient()) {
            ModifiableSolrParams params = new ModifiableSolrParams();

            // Build query string — default to match-all if empty
            String query = buildQuery(queryTerm);
            params.set("q", query);

            // Apply filter queries (additive narrowing)
            List<String> filterQueries = new ArrayList<>();
            if (fileTypeFilter != null && !fileTypeFilter.isBlank()) {
                filterQueries.add("file_type_s:" + fileTypeFilter);
            }
            if (tagFilter != null && !tagFilter.isBlank()) {
                filterQueries.add("tags_ss:" + tagFilter);
            }
            if (!filterQueries.isEmpty()) {
                params.set("fq", filterQueries.toArray(new String[0]));
            }

            // Faceted aggregations
            params.set("facet",               "true");
            params.set("facet.field",         new String[]{"file_type_s", "tags_ss"});
            params.set("facet.mincount",      "1");
            params.set("facet.limit",         "20");

            params.set("rows", Math.min(rows, 200));  // Enforce max rows guard
            params.set("sort", "score desc");

            QueryResponse response = client.query(params);

            // Build hits list
            List<Map<String, Object>> hits = new ArrayList<>();
            response.getResults().forEach(doc -> hits.add(doc.getFieldValueMap()));
            result.put("hits",  hits);
            result.put("total", response.getResults().getNumFound());

            // Build facets map
            Map<String, List<Map<String, Object>>> facets = new LinkedHashMap<>();
            if (response.getFacetFields() != null) {
                for (FacetField ff : response.getFacetFields()) {
                    List<Map<String, Object>> counts = new ArrayList<>();
                    for (FacetField.Count count : ff.getValues()) {
                        counts.add(Map.of("value", count.getName(), "count", count.getCount()));
                    }
                    facets.put(ff.getName(), counts);
                }
            }
            result.put("facets", facets);

            log.info("Solr search [q={}, fq={}] returned {} hits", queryTerm, filterQueries, hits.size());

        } catch (Throwable e) {
            log.warn("Solr search failed (Solr offline?): {}", e.getMessage());
        }


        return result;
    }

    // Overload for backward compatibility
    public Map<String, Object> searchAssets(String queryTerm, String fileTypeFilter) {
        return searchAssets(queryTerm, fileTypeFilter, null, 50);
    }

    private String buildQuery(String queryTerm) {
        if (queryTerm == null || queryTerm.isBlank()) {
            return "*:*";
        }
        String escaped = queryTerm.replace(":", "\\:");
        return String.format("sku_s:%s OR file_name_t:%s OR tags_ss:%s",
                escaped, escaped, escaped);
    }
}
