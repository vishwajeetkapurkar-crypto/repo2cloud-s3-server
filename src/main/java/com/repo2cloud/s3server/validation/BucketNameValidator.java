package com.repo2cloud.s3server.validation;

import java.util.regex.Pattern;

public final class BucketNameValidator {

    private static final Pattern BUCKET_NAME_PATTERN =
            Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");

    private BucketNameValidator() {
    }

    public static boolean isValid(String bucketName) {

        if (bucketName == null) {
            return false;
        }

        return BUCKET_NAME_PATTERN
                .matcher(bucketName)
                .matches();
    }
}