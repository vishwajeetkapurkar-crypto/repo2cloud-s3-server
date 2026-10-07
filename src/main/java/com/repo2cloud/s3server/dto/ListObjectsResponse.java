package com.repo2cloud.s3server.dto;

import java.util.List;

public class ListObjectsResponse {

    private String name;
    private List<ObjectSummary> contents;
    private List<CommonPrefix> commonPrefixes;
    private String nextContinuationToken;
    private boolean truncated;

    public ListObjectsResponse(
            String name,
            List<ObjectSummary> contents,
            List<CommonPrefix> commonPrefixes,
            String nextContinuationToken,
            boolean truncated) {

        this.name = name;
        this.contents = contents;
        this.commonPrefixes = commonPrefixes;
        this.nextContinuationToken = nextContinuationToken;
        this.truncated = truncated;
    }

    public String getName() {
        return name;
    }

    public List<ObjectSummary> getContents() {
        return contents;
    }

    public List<CommonPrefix> getCommonPrefixes() {
        return commonPrefixes;
    }

    public String getNextContinuationToken() {
        return nextContinuationToken;
    }

    public boolean isTruncated() {
        return truncated;
    }
}