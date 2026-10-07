package com.repo2cloud.s3server.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ETagUtil {

    public static MessageDigest createMD5Digest() {

        try {
            return MessageDigest.getInstance("MD5");

        } catch (NoSuchAlgorithmException e) {

            throw new RuntimeException(
                    "MD5 algorithm not available",
                    e
            );
        }
    }

    public static String toETag(byte[] hash) {

        StringBuilder hexString =
                new StringBuilder();

        for (byte b : hash) {

            hexString.append(
                    String.format("%02x", b)
            );
        }

        return "\"" + hexString + "\"";
    }

    /*
     * Keep this method because your existing code/tests
     * may still use it.
     */
    public static String generateETag(byte[] data) {

        MessageDigest digest =
                createMD5Digest();

        byte[] hash =
                digest.digest(data);

        return toETag(hash);
    }
}