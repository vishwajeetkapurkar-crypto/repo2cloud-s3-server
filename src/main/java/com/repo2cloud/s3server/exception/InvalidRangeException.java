package com.repo2cloud.s3server.exception;

public class InvalidRangeException extends RuntimeException {

    public InvalidRangeException(String message) {
        super(message);
    }
}