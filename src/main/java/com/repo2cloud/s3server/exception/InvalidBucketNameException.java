package com.repo2cloud.s3server.exception;

public class InvalidBucketNameException extends RuntimeException {

    private final String bucketName;

    public InvalidBucketNameException(String bucketName) {
        super("Invalid bucket name: " + bucketName);
        this.bucketName = bucketName;
    }

    public String getBucketName() {
        return bucketName;
    }
}