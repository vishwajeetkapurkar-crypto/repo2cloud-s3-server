package com.repo2cloud.s3server.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.annotation.JsonInclude;

@JacksonXmlRootElement(localName = "Error")
public class S3ErrorResponse {

    @JacksonXmlProperty(localName = "Code")
private String code;

@JacksonXmlProperty(localName = "Message")
private String message;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JacksonXmlProperty(localName = "BucketName")
private String bucketName;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JacksonXmlProperty(localName = "Key")
private String key;

    public S3ErrorResponse(
            String code,
            String message,
            String bucketName,
            String key) {

        this.code = code;
        this.message = message;
        this.bucketName = bucketName;
        this.key = key;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getKey() {
        return key;
    }
}