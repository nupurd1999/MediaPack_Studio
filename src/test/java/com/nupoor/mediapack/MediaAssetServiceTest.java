package com.nupoor.mediapack;

import com.nupoor.mediapack.model.MediaAsset;
import com.nupoor.mediapack.repository.MediaAssetRepository;
import com.nupoor.mediapack.service.AuditLogService;
import com.nupoor.mediapack.service.MediaAssetService;
import com.nupoor.mediapack.service.SolrSearchService;
import com.nupoor.mediapack.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MediaAssetServiceTest {

    private MediaAssetRepository repository;
    private SolrSearchService solrSearchService;
    private StorageService storageService;
    private MediaAssetService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(MediaAssetRepository.class);
        solrSearchService = Mockito.mock(SolrSearchService.class);
        storageService = Mockito.mock(StorageService.class);

        service = new MediaAssetService(repository, solrSearchService, storageService);
    }

    @Test
    @DisplayName("saveAsset — persists asset in repository and indexes in Solr")
    void testSaveAsset() {
        MediaAsset asset = new MediaAsset("SKU-100", "test.png", "assets/test.png", "IMAGE_PNG", 1024, 800, 600, List.of("bolt"));
        when(repository.save(any(MediaAsset.class))).thenReturn(asset);

        MediaAsset saved = service.saveAsset(asset);
        assertNotNull(saved);
        assertEquals("SKU-100", saved.getSku());
        verify(solrSearchService, times(1)).indexAsset(saved);
    }

    @Test
    @DisplayName("uploadAsset — stores binary via storageService and indexes in Solr")
    void testUploadAsset_NewFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "screw.png", "image/png", "binary-image-content".getBytes());
        when(repository.findByChecksumSha256(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaAsset asset = service.uploadAsset(file, "SKU-SCREW-1", "IMAGE_PNG", List.of("hardware"), "DE");

        assertNotNull(asset);
        assertEquals("screw.png", asset.getFileName());
        assertEquals("DE", asset.getTenantRegion());
        verify(storageService, times(1)).store(anyString(), any(InputStream.class), eq((long) "binary-image-content".length()), eq("image/png"));
        verify(solrSearchService, times(1)).indexAsset(any(MediaAsset.class));
    }

    @Test
    @DisplayName("uploadAsset — deduplicates file if SHA-256 already exists in repository")
    void testUploadAsset_Deduplication() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "duplicate.png", "image/png", "same-bytes".getBytes());
        MediaAsset existing = new MediaAsset("SKU-ORIGINAL", "orig.png", "assets/orig.png", "IMAGE_PNG", 10, 0, 0, List.of("original"));
        existing.setId("existing-id-1");

        when(repository.findByChecksumSha256(anyString())).thenReturn(Optional.of(existing));

        MediaAsset result = service.uploadAsset(file, "SKU-DUPE", "IMAGE_PNG", List.of("new-tag"), "DE");

        assertEquals("existing-id-1", result.getId());
        verify(storageService, never()).store(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("uploadAsset — throws IllegalArgumentException on empty file")
    void testUploadAsset_EmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> service.uploadAsset(emptyFile, "SKU-1", null, null, "DE"));
    }

    @Test
    @DisplayName("getPresignedDownloadUrl — calls storageService.getDownloadUrl")
    void testGetPresignedDownloadUrl() {
        MediaAsset asset = new MediaAsset("SKU-1", "doc.pdf", "assets/doc.pdf", "DOCUMENT_PDF", 10, 0, 0, List.of());
        asset.setId("asset-1");
        when(repository.findById("asset-1")).thenReturn(Optional.of(asset));
        when(storageService.getDownloadUrl("assets/doc.pdf")).thenReturn("http://localhost:8080/download/doc.pdf");

        String url = service.getPresignedDownloadUrl("asset-1");
        assertEquals("http://localhost:8080/download/doc.pdf", url);
    }
}
