package com.repo2cloud.s3server.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.time.LocalDateTime;

public class ObjectSummary {

    @JacksonXmlProperty(localName = "Key")
    private String key;

    @JacksonXmlProperty(localName = "Size")
    private long size;

    @JacksonXmlProperty(localName = "ETag")
    private String etag;

    @JacksonXmlProperty(localName = "LastModified")
    private LocalDateTime lastModified;

    public ObjectSummary(
            String key,
            long size,
            String etag,
            LocalDateTime lastModified) {

        this.key = key;
        this.size = size;
        this.etag = etag;
        this.lastModified = lastModified;
    }

    public String getKey() {
        return key;
    }

    public long getSize() {
        return size;
    }

    public String getEtag() {
        return etag;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }
}