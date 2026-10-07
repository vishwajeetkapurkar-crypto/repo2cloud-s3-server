package com.repo2cloud.s3server.dto;

public class ListingEntry {

    private final String key;

    private final ObjectSummary objectSummary;

    private final CommonPrefix commonPrefix;

    private ListingEntry(
            String key,
            ObjectSummary objectSummary,
            CommonPrefix commonPrefix) {

        this.key = key;
        this.objectSummary = objectSummary;
        this.commonPrefix = commonPrefix;
    }

    public static ListingEntry object(
            ObjectSummary objectSummary) {

        return new ListingEntry(
                objectSummary.getKey(),
                objectSummary,
                null
        );
    }

    public static ListingEntry prefix(
            CommonPrefix commonPrefix) {

        return new ListingEntry(
                commonPrefix.getPrefix(),
                null,
                commonPrefix
        );
    }

    public String getKey() {
        return key;
    }

    public ObjectSummary getObjectSummary() {
        return objectSummary;
    }

    public CommonPrefix getCommonPrefix() {
        return commonPrefix;
    }

    public boolean isObject() {
        return objectSummary != null;
    }

    public boolean isPrefix() {
        return commonPrefix != null;
    }
}