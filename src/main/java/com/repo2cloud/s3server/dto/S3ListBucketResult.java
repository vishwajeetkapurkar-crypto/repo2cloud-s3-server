package com.repo2cloud.s3server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "ListBucketResult")
public class S3ListBucketResult {

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "KeyCount")
    private int keyCount;

    @JacksonXmlProperty(localName = "MaxKeys")
    private int maxKeys;

    @JacksonXmlProperty(localName = "IsTruncated")
    private boolean isTruncated;

    @JsonInclude(JsonInclude.Include.NON_NULL)
@JacksonXmlProperty(localName = "NextContinuationToken")
private String nextContinuationToken;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Contents")
    private List<ObjectSummary> contents;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "CommonPrefixes")
    private List<CommonPrefix> commonPrefixes;

    public S3ListBucketResult(
            String name,
            List<ObjectSummary> contents,
            List<CommonPrefix> commonPrefixes,
            int keyCount,
            int maxKeys,
            boolean isTruncated,
            String nextContinuationToken) {

        this.name = name;
        this.contents = contents;
        this.commonPrefixes = commonPrefixes;
        this.keyCount = keyCount;
        this.maxKeys = maxKeys;
        this.isTruncated = isTruncated;
        this.nextContinuationToken = nextContinuationToken;
    }

    public String getName() {
        return name;
    }

    public int getKeyCount() {
        return keyCount;
    }

    public int getMaxKeys() {
        return maxKeys;
    }

    public boolean getIsTruncated() {
        return isTruncated;
    }

    public String getNextContinuationToken() {
        return nextContinuationToken;
    }

    public List<ObjectSummary> getContents() {
        return contents;
    }

    public List<CommonPrefix> getCommonPrefixes() {
        return commonPrefixes;
    }
}