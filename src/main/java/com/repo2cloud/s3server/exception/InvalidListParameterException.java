package com.repo2cloud.s3server.exception;

public class InvalidListParameterException extends RuntimeException {

    public InvalidListParameterException(String message) {
        super(message);
    }
}