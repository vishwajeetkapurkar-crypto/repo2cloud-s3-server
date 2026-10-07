package com.repo2cloud.s3server.validation;

public final class ObjectKeyValidator {

    private ObjectKeyValidator() {
    }

    public static boolean isValid(String objectKey) {

        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }

        // Object keys must not start with /
        if (objectKey.startsWith("/")) {
            return false;
        }

        // Prevent path traversal
        String[] parts = objectKey.split("/");

        for (String part : parts) {

            if ("..".equals(part)) {
                return false;
            }
        }

        return true;
    }
}