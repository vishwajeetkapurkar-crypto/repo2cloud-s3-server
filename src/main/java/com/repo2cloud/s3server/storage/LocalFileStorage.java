package com.repo2cloud.s3server.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.repo2cloud.s3server.exception.InvalidBucketNameException;
import com.repo2cloud.s3server.exception.InvalidObjectKeyException;
import com.repo2cloud.s3server.util.ETagUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

@Component
public class LocalFileStorage {

    private final Path rootLocation;

    public InputStream getObjectStream(Path objectPath) {

    try {

        if (!Files.exists(objectPath)) {
            throw new RuntimeException(
                    "Object file does not exist"
            );
        }

        return Files.newInputStream(objectPath);

    } catch (IOException e) {

        throw new RuntimeException(
                "Could not open object stream",
                e
        );
    }
}

    public LocalFileStorage(
            @Value("${s3.storage.root}") String storageRoot) {

        this.rootLocation = Paths.get(storageRoot)
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not initialize storage directory",
                    e
            );
        }
    }

    public Path saveObject(
        String bucketName,
        String objectKey,
        byte[] data) {

    try {
        Path bucketPath = rootLocation
                .resolve(bucketName)
                .normalize();

        if (!bucketPath.startsWith(rootLocation)) {
    throw new InvalidBucketNameException(bucketName);
}

        Path objectPath = getObjectPath(
                bucketName,
                objectKey
        );

        Files.createDirectories(objectPath.getParent());

        Files.write(objectPath, data);

        return objectPath;

    } catch (IOException e) {
        throw new RuntimeException(
                "Could not save object",
                e
        );
    }
}

public byte[] readObject(Path objectPath) {

    try {
        if (!Files.exists(objectPath)) {
            throw new RuntimeException(
                    "Object file does not exist"
            );
        }

        return Files.readAllBytes(objectPath);

    } catch (IOException e) {
        throw new RuntimeException(
                "Could not read object",
                e
        );
    }
}

public Path backupObject(Path objectPath) {

    try {
        if (!Files.exists(objectPath)) {
            return null;
        }

        Path backupPath = Files.createTempFile(
                objectPath.getParent(),
                objectPath.getFileName().toString(),
                ".bak"
        );

        Files.copy(
                objectPath,
                backupPath,
                StandardCopyOption.REPLACE_EXISTING
        );

        return backupPath;

    } catch (IOException e) {
        throw new RuntimeException(
                "Could not backup existing object",
                e
        );
    }
}
public void restoreBackup(
        Path backupPath,
        Path objectPath) {

    if (backupPath == null) {
        return;
    }

    try {
        Files.move(
                backupPath,
                objectPath,
                StandardCopyOption.REPLACE_EXISTING
        );

    } catch (IOException e) {
        throw new RuntimeException(
                "Could not restore object backup",
                e
        );
    }
}
public void deleteBackup(Path backupPath) {

    if (backupPath == null) {
        return;
    }

    try {
        Files.deleteIfExists(backupPath);

    } catch (IOException e) {
        throw new RuntimeException(
                "Could not delete object backup",
                e
        );
    }
}

public StreamingUploadResult saveObject(
        String bucketName,
        String objectKey,
        InputStream inputStream) {

    Path tempPath = null;

    try {

        Path bucketPath =
                rootLocation
                        .resolve(bucketName)
                        .normalize();

       if (!bucketPath.startsWith(rootLocation)) {
    throw new InvalidBucketNameException(bucketName);
}

        Path objectPath =
                getObjectPath(
                        bucketName,
                        objectKey
                );

        Files.createDirectories(
                objectPath.getParent()
        );

        /*
         * Create temporary file in the same directory.
         * Keeping it in the same filesystem allows
         * an atomic move to the final path.
         */
        tempPath = Files.createTempFile(
                objectPath.getParent(),
                objectPath.getFileName().toString(),
                ".tmp"
        );

        MessageDigest digest =
                ETagUtil.createMD5Digest();

        long totalBytes = 0;

        byte[] buffer =
                new byte[8192];

        try (
                OutputStream outputStream =
                        Files.newOutputStream(tempPath)
        ) {

            int bytesRead;

            while (
                    (bytesRead =
                            inputStream.read(buffer))
                            != -1
            ) {

                outputStream.write(
                        buffer,
                        0,
                        bytesRead
                );

                digest.update(
                        buffer,
                        0,
                        bytesRead
                );

                totalBytes += bytesRead;
            }
        }

        /*
         * Upload completed successfully.
         * Now replace the final object.
         */
        Files.move(
                tempPath,
                objectPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
        );

        tempPath = null;

        String etag =
                ETagUtil.toETag(
                        digest.digest()
                );

        return new StreamingUploadResult(
                objectPath,
                totalBytes,
                etag
        );

    } catch (IOException e) {

        /*
         * Delete temporary file if upload failed.
         */
        if (tempPath != null) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
                // Ignore cleanup failure
            }
        }

        throw new RuntimeException(
                "Could not save object",
                e
        );
    }
}

public InputStream getObjectRangeStream(
        Path objectPath,
        long start,
        long length) {

    try {

        if (!Files.exists(objectPath)) {
            throw new RuntimeException(
                    "Object file does not exist"
            );
        }

        RandomAccessFile randomAccessFile =
                new RandomAccessFile(
                        objectPath.toFile(),
                        "r"
                );

        randomAccessFile.seek(start);

        return new InputStream() {

            private long remaining = length;

            @Override
            public int read() throws IOException {

                if (remaining <= 0) {
                    return -1;
                }

                int value =
                        randomAccessFile.read();

                if (value == -1) {
                    remaining = 0;
                    return -1;
                }

                remaining--;

                return value;
            }

            @Override
            public int read(
                    byte[] buffer,
                    int offset,
                    int len) throws IOException {

                if (remaining <= 0) {
                    return -1;
                }

                int bytesToRead =
                        (int) Math.min(
                                len,
                                remaining
                        );

                int bytesRead =
                        randomAccessFile.read(
                                buffer,
                                offset,
                                bytesToRead
                        );

                if (bytesRead == -1) {
                    remaining = 0;
                    return -1;
                }

                remaining -= bytesRead;

                return bytesRead;
            }

            @Override
            public void close() throws IOException {
                randomAccessFile.close();
            }
        };

    } catch (IOException e) {

        throw new RuntimeException(
                "Could not open object range stream",
                e
        );
    }
}

public void deleteObject(Path objectPath) {
    try {
        if (Files.exists(objectPath)) {
            Files.delete(objectPath);
        }
    } catch (IOException e) {
        throw new RuntimeException("Could not delete object", e);
    }
}

    public Path getObjectPath(
            String bucketName,
            String objectKey) {

        Path bucketPath = rootLocation.resolve(bucketName);

        Path objectPath = bucketPath
                .resolve(objectKey)
                .normalize();

        if (!objectPath.startsWith(bucketPath)) {
             throw new InvalidObjectKeyException(
            bucketName,
            objectKey
    );
        }

        return objectPath;
    }
}