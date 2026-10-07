package com.repo2cloud.s3server.exception;

public class BucketNotEmptyException extends RuntimeException {

    private final String bucketName;

    public BucketNotEmptyException(String bucketName) {
        super("Bucket is not empty: " + bucketName);
        this.bucketName = bucketName;
    }

    public String getBucketName() {
        return bucketName;
    }
}