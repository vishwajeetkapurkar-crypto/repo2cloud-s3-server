package com.repo2cloud.s3server.exception;

public class BucketNotFoundException extends RuntimeException {

    public BucketNotFoundException(String message) {
        super(message);
    }
}