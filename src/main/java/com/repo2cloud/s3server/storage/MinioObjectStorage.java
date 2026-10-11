package com.repo2cloud.s3server.storage;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class MinioObjectStorage {

    private static final long PART_SIZE = 10L * 1024 * 1024;

    private final MinioClient minioClient;
    private final String bucketPrefix;

    public MinioObjectStorage(
            MinioClient minioClient,
            @Value("${repo2cloud.storage.bucket-prefix:repo2cloud-bucket-}")
            String bucketPrefix) {
        this.minioClient = minioClient;
        this.bucketPrefix = bucketPrefix;
    }

    /*
     * Each Repo2Cloud logical bucket receives its own
     * internal MinIO bucket.
     *
     * Example:
     * Database bucket ID 15 -> repo2cloud-bucket-15
     */
    public String getPhysicalBucketName(Long bucketId) {
        if (bucketId == null || bucketId <= 0) {
            throw new IllegalArgumentException(
                    "A valid bucket ID is required"
            );
        }

        return bucketPrefix + bucketId;
    }

    /*
     * Create a physical bucket in MinIO.
     */
    public void createBucket(Long bucketId) {

        String bucketName = getPhysicalBucketName(bucketId);

        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not create MinIO bucket: " + bucketName,
                    e
            );
        }
    }

    /*
     * Upload an object to MinIO.
     *
     * The object key can contain folders, for example:
     * images/products/frame.jpg
     */
    public UploadResult uploadObject(
            Long bucketId,
            String objectKey,
            InputStream inputStream,
            String contentType) {

        String bucketName = getPhysicalBucketName(bucketId);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(
                                    inputStream,
                                    -1,
                                    PART_SIZE
                            )
                            .contentType(
                                    contentType != null
                                            ? contentType
                                            : "application/octet-stream"
                            )
                            .build()
            );

            StatObjectResponse metadata =
                    minioClient.statObject(
                            StatObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(objectKey)
                                    .build()
                    );

            return new UploadResult(
                    metadata.size(),
                    normalizeETag(metadata.etag())
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not upload object: " + objectKey,
                    e
            );
        }
    }

    /*
 * Copy an object within the same physical MinIO bucket.
 *
 * This supports creating a backup before overwriting
 * an existing object.
 */
public void copyObject(
        Long bucketId,
        String sourceObjectKey,
        String destinationObjectKey) {

    String bucketName = getPhysicalBucketName(bucketId);

    try {
        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .bucket(bucketName)
                        .object(destinationObjectKey)
                        .source(
                                CopySource.builder()
                                        .bucket(bucketName)
                                        .object(sourceObjectKey)
                                        .build()
                        )
                        .build()
        );

    } catch (Exception e) {
        throw new RuntimeException(
                "Could not copy object from "
                        + sourceObjectKey
                        + " to "
                        + destinationObjectKey,
                e
        );
    }
}
    /*
     * Download the complete object.
     *
     * The caller is responsible for closing the returned stream.
     */
    public InputStream getObject(
            Long bucketId,
            String objectKey) {

        String bucketName = getPhysicalBucketName(bucketId);

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not download object: " + objectKey,
                    e
            );
        }
    }

    /*
     * Download a specific byte range.
     *
     * Example:
     * offset = 100
     * length = 500
     *
     * Returns up to 500 bytes starting at byte 100.
     */
    public InputStream getObjectRange(
            Long bucketId,
            String objectKey,
            long offset,
            long length) {

        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException(
                    "Offset must be non-negative and length must be positive"
            );
        }

        String bucketName = getPhysicalBucketName(bucketId);

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .offset(offset)
                            .length(length)
                            .build()
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not download object range: " + objectKey,
                    e
            );
        }
    }

    /*
     * Delete an object from MinIO.
     */
    public void deleteObject(
            Long bucketId,
            String objectKey) {

        String bucketName = getPhysicalBucketName(bucketId);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not delete object: " + objectKey,
                    e
            );
        }
    }

    /*
     * Read metadata from MinIO.
     */
    public StatObjectResponse getObjectMetadata(
            Long bucketId,
            String objectKey) {

        String bucketName = getPhysicalBucketName(bucketId);

        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not read object metadata: " + objectKey,
                    e
            );
        }
    }

    /*
     * Delete an empty physical MinIO bucket.
     */
    public void deleteBucket(Long bucketId) {

        String bucketName = getPhysicalBucketName(bucketId);

        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (exists) {
                minioClient.removeBucket(
                        RemoveBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not delete MinIO bucket: " + bucketName,
                    e
            );
        }
    }

    /*
     * MinIO normally returns an ETag without quotation marks.
     * Keep the quoted representation used by the existing API.
     */
    private String normalizeETag(String etag) {

        if (etag == null || etag.isBlank()) {
            return "\"\"";
        }

        String normalized = etag.replace("\"", "");

        return "\"" + normalized + "\"";
    }

    /*
     * Result returned after uploading an object.
     */
    public static class UploadResult {

        private final long size;
        private final String etag;

        public UploadResult(long size, String etag) {
            this.size = size;
            this.etag = etag;
        }

        public long getSize() {
            return size;
        }

        public String getEtag() {
            return etag;
        }
    }
}