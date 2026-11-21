package com.nupoor.mediapack;

import com.nupoor.mediapack.service.storage.LocalStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("LocalStorageService — stores, checks existence, reads, and generates URL")
    void testLocalStorageLifecycle() throws Exception {
        LocalStorageService storage = new LocalStorageService(tempDir.toString());
        assertEquals("LOCAL", storage.getStorageType());

        String key = "exports/job-abc.zip";
        byte[] content = "test-zip-bytes".getBytes(StandardCharsets.UTF_8);

        // Store
        storage.store(key, new ByteArrayInputStream(content), content.length, "application/zip");

        // Exists
        assertTrue(storage.exists(key));

        // Read
        try (InputStream is = storage.getInputStream(key)) {
            byte[] readBytes = is.readAllBytes();
            assertArrayEquals(content, readBytes);
        }

        // Download URL
        String downloadUrl = storage.getDownloadUrl(key);
        assertEquals("/api/exports/download/job-abc", downloadUrl);

        // Delete
        storage.delete(key);
        assertFalse(storage.exists(key));
    }
}
