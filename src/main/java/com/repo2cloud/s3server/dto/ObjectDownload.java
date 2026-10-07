package com.repo2cloud.s3server.dto;

import org.springframework.core.io.Resource;

public class ObjectDownload {

    private final Resource resource;
    private final String contentType;
    private final long contentLength;
    private final String etag;

    public ObjectDownload(
            Resource resource,
            String contentType,
            long contentLength,
            String etag) {

        this.resource = resource;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.etag = etag;
    }

    public Resource getResource() {
        return resource;
    }

    public String getContentType() {
        return contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getEtag() {
        return etag;
    }
}