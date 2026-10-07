package com.repo2cloud.s3server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.repo2cloud.s3server.dto.S3ErrorResponse;
import org.springframework.http.MediaType;


@RestControllerAdvice
public class GlobalExceptionHandler {

        private String extractBucketName(String message) {

    String prefix = "Bucket not found: ";

    if (message != null && message.startsWith(prefix)) {
        return message.substring(prefix.length());
    }

    return null;
}

@ExceptionHandler(InvalidBucketNameException.class)
public ResponseEntity<S3ErrorResponse> handleInvalidBucketName(
        InvalidBucketNameException ex) {

    S3ErrorResponse error = new S3ErrorResponse(
            "InvalidBucketName",
            ex.getMessage(),
            ex.getBucketName(),
            null
    );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_XML)
            .body(error);
}


@ExceptionHandler(InvalidRangeException.class)
public ResponseEntity<S3ErrorResponse> handleInvalidRange(
        InvalidRangeException ex) {

    S3ErrorResponse error = new S3ErrorResponse(
            "InvalidRange",
            ex.getMessage(),
            null,
            null
    );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_XML)
            .body(error);
}


@ExceptionHandler(InvalidObjectKeyException.class)
public ResponseEntity<S3ErrorResponse> handleInvalidObjectKey(
        InvalidObjectKeyException ex) {

    S3ErrorResponse error =
            new S3ErrorResponse(
                    "InvalidObjectKey",
                    "The specified object key is invalid.",
                    ex.getBucketName(),
                    ex.getObjectKey()
            );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_XML)
            .body(error);
}

@ExceptionHandler(InvalidListParameterException.class)
public ResponseEntity<S3ErrorResponse> handleInvalidListParameter(
        InvalidListParameterException ex) {

    S3ErrorResponse error =
            new S3ErrorResponse(
                    "InvalidArgument",
                    ex.getMessage(),
                    null,
                    null
            );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_XML)
            .body(error);
}

@ExceptionHandler(BucketNotEmptyException.class)
public ResponseEntity<S3ErrorResponse> handleBucketNotEmpty(
        BucketNotEmptyException ex) {

    S3ErrorResponse error =
            new S3ErrorResponse(
                    "BucketNotEmpty",
                    "The bucket you tried to delete is not empty.",
                    ex.getBucketName(),
                    null
            );

   return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_XML)
        .body(error);
}


private String extractObjectKey(String message) {

    String prefix = "Object not found: ";

    if (message != null && message.startsWith(prefix)) {
        return message.substring(prefix.length());
    }

    return null;
}

   @ExceptionHandler(ObjectNotFoundException.class)
public ResponseEntity<S3ErrorResponse> handleObjectNotFound(
        ObjectNotFoundException ex) {

    String objectKey =
            extractObjectKey(ex.getMessage());

    S3ErrorResponse error =
            new S3ErrorResponse(
                    "NoSuchKey",
                    "The specified key does not exist.",
                    null,
                    objectKey
            );

    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_XML)
            .body(error);
}

@ExceptionHandler(BucketNotFoundException.class)
public ResponseEntity<S3ErrorResponse> handleBucketNotFound(
        BucketNotFoundException ex) {

    S3ErrorResponse error =
            new S3ErrorResponse(
                    "NoSuchBucket",
                    "The specified bucket does not exist.",
                    extractBucketName(ex.getMessage()),
                    null
            );

    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_XML)
            .body(error);
}

   @ExceptionHandler(BucketAlreadyExistsException.class)
public ResponseEntity<S3ErrorResponse> handleBucketAlreadyExists(
        BucketAlreadyExistsException ex) {

    S3ErrorResponse error = new S3ErrorResponse(
            "BucketAlreadyExists",
            ex.getMessage(),
            null,
            null
    );

    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_XML)
            .body(error);
}
}