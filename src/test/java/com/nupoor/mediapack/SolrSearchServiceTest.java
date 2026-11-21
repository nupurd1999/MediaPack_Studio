package com.nupoor.mediapack;

import com.nupoor.mediapack.model.MediaAsset;
import com.nupoor.mediapack.service.SolrSearchService;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SolrSearchServiceTest {

    private SolrClient solrClient;
    private SolrSearchService service;

    @BeforeEach
    void setUp() {
        solrClient = Mockito.mock(SolrClient.class);
        service = new SolrSearchService(solrClient);
    }

    @Test
    @DisplayName("searchAssets — returns hits and facets successfully from Solr")
    void testSearchAssets_Success() throws Exception {
        QueryResponse response = Mockito.mock(QueryResponse.class);
        SolrDocumentList docList = new SolrDocumentList();
        SolrDocument doc = new SolrDocument();
        doc.setField("id", "asset-1");
        doc.setField("sku_s", "SKU-001");
        docList.add(doc);
        docList.setNumFound(1);

        when(response.getResults()).thenReturn(docList);

        FacetField facetField = new FacetField("file_type_s");
        facetField.add("IMAGE_PNG", 1);
        when(response.getFacetFields()).thenReturn(List.of(facetField));

        when(solrClient.query(any(SolrParams.class))).thenReturn(response);

        Map<String, Object> result = service.searchAssets("bolt", "IMAGE_PNG", "fastener", 20);

        assertNotNull(result);
        assertEquals(1L, result.get("total"));
        List<?> hits = (List<?>) result.get("hits");
        assertEquals(1, hits.size());

        Map<?, ?> facets = (Map<?, ?>) result.get("facets");
        assertTrue(facets.containsKey("file_type_s"));
    }

    @Test
    @DisplayName("searchAssets — handles offline Solr gracefully returning empty results")
    void testSearchAssets_OfflineGracefulFallback() throws Exception {
        when(solrClient.query(any(SolrParams.class))).thenThrow(new RuntimeException("Connection refused to Solr"));

        Map<String, Object> result = service.searchAssets("bolt", null, null, 10);
        assertNotNull(result);
        assertEquals(0L, result.get("total"));
        assertTrue(((List<?>) result.get("hits")).isEmpty());
    }

    @Test
    @DisplayName("indexAsset — does not throw exception when Solr is offline")
    void testIndexAsset_OfflineGraceful() throws Exception {
        when(solrClient.add(any(org.apache.solr.common.SolrInputDocument.class))).thenThrow(new RuntimeException("Solr connection error"));

        MediaAsset asset = new MediaAsset("SKU-1", "file.png", "assets/file.png", "IMAGE_PNG", 100, 10, 10, List.of("tag"));
        asset.setId("id-1");

        assertDoesNotThrow(() -> service.indexAsset(asset));
    }
}
