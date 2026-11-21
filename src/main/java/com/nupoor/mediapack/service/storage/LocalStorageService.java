package com.nupoor.mediapack.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Optional;

/**
 * Local Disk Storage Service.
 * Default storage implementation for local standalone environments.
 * Stores assets and ZIP export bundles in the local file system (default: ./storage).
 *
 * Benefits:
 * - Zero AWS S3 or LocalStack dependencies.
 * - Zero memory leaks — streams directly to local disk.
 * - Works 100% offline with zero cloud charges or Docker container overhead.
 */
@Service
@Primary
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path rootLocation;

    public LocalStorageService(@Value("${storage.local.base-dir:./storage}") String baseDir) {
        this.rootLocation = Paths.get(baseDir).toAbsolutePath().normalize();
        initStorage();
    }

    private void initStorage() {
        try {
            Files.createDirectories(rootLocation.resolve("exports"));
            Files.createDirectories(rootLocation.resolve("assets"));
            log.info("[LocalStorage] Initialized local storage directory at: {}", rootLocation);
        } catch (IOException e) {
            log.error("[LocalStorage] Failed to initialize local storage directory: {}", rootLocation, e);
        }
    }

    @Override
    public void store(String key, InputStream inputStream, long contentLength, String contentType) {
        try {
            Path destinationFile = rootLocation.resolve(key).normalize();

            // Prevent path traversal attacks
            if (!destinationFile.startsWith(rootLocation)) {
                throw new SecurityException("Cannot store file outside current storage directory: " + key);
            }

            Files.createDirectories(destinationFile.getParent());
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("[LocalStorage] Successfully saved: {} ({} bytes)", key, Files.size(destinationFile));
        } catch (IOException e) {
            log.error("[LocalStorage] Failed to store file for key: {}", key, e);
            throw new RuntimeException("Failed to store file locally: " + key, e);
        }
    }

    @Override
    public InputStream getInputStream(String key) {
        try {
            Path file = rootLocation.resolve(key).normalize();
            if (!Files.exists(file)) {
                throw new FileNotFoundException("Local file not found: " + key);
            }
            return Files.newInputStream(file);
        } catch (IOException e) {
            log.error("[LocalStorage] Failed to read file for key: {}", key, e);
            throw new RuntimeException("Could not read local file: " + key, e);
        }
    }

    @Override
    public String getDownloadUrl(String key) {
        // Return relative API download endpoint
        if (key.startsWith("exports/")) {
            String jobId = key.substring("exports/".length());
            if (jobId.endsWith(".zip")) jobId = jobId.substring(0, jobId.length() - 4);
            return "/api/exports/download/" + jobId;
        }
        if (key.startsWith("assets/")) {
            return "/api/assets/download?key=" + key;
        }
        return "/api/storage/download?key=" + key;
    }

    @Override
    public boolean exists(String key) {
        Path file = rootLocation.resolve(key).normalize();
        return Files.exists(file);
    }

    @Override
    public void delete(String key) {
        try {
            Path file = rootLocation.resolve(key).normalize();
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("[LocalStorage] Failed to delete file {}: {}", key, e.getMessage());
        }
    }

    @Override
    public Optional<Path> getLocalFilePath(String key) {
        Path file = rootLocation.resolve(key).normalize();
        return Files.exists(file) ? Optional.of(file) : Optional.empty();
    }

    @Override
    public String getStorageType() {
        return "LOCAL";
    }

    public Path getRootLocation() {
        return rootLocation;
    }
}
