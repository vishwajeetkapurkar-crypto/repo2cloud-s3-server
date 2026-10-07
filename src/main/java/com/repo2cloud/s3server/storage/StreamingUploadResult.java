package com.repo2cloud.s3server.storage;

import java.nio.file.Path;

public class StreamingUploadResult {

    private final Path storagePath;

    private final long size;

    private final String etag;

    public StreamingUploadResult(
            Path storagePath,
            long size,
            String etag) {

        this.storagePath = storagePath;
        this.size = size;
        this.etag = etag;
    }

    public Path getStoragePath() {
        return storagePath;
    }

    public long getSize() {
        return size;
    }

    public String getEtag() {
        return etag;
    }
}