package com.repo2cloud.s3server.exception;

public class BucketAlreadyExistsException extends RuntimeException {

    public BucketAlreadyExistsException(String message) {
        super(message);
    }
}