package com.nupoor.mediapack.service.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Pluggable Storage Service interface.
 * Decouples asset and export packaging from specific storage implementations.
 *
 * Implementations:
 * 1. LocalStorageService (Default / local profile): Stores directly to local disk with zero cloud overhead.
 * 2. S3StorageService (Cloud / demo profile): Stores to AWS S3 or LocalStack.
 */
public interface StorageService {

    /**
     * Stores a file stream under the given storage key.
     */
    void store(String key, InputStream inputStream, long contentLength, String contentType);

    /**
     * Retrieves an input stream for reading the stored file.
     */
    InputStream getInputStream(String key);

    /**
     * Generates a download URL accessible by clients (local endpoint or pre-signed URL).
     */
    String getDownloadUrl(String key);

    /**
     * Checks if a file exists under the given key.
     */
    boolean exists(String key);

    /**
     * Deletes a file under the given key if it exists.
     */
    void delete(String key);

    /**
     * Returns the local file path if stored on local disk, or empty if cloud-hosted.
     */
    Optional<Path> getLocalFilePath(String key);

    /**
     * Returns the identifier of this storage provider ("LOCAL" or "S3").
     */
    String getStorageType();
}
