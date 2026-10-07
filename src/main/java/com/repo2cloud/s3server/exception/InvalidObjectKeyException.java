package com.repo2cloud.s3server.exception;

public class InvalidObjectKeyException extends RuntimeException {

    private final String bucketName;
    private final String objectKey;

    public InvalidObjectKeyException(
            String bucketName,
            String objectKey) {

        super("Invalid object key: " + objectKey);

        this.bucketName = bucketName;
        this.objectKey = objectKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getObjectKey() {
        return objectKey;
    }
}