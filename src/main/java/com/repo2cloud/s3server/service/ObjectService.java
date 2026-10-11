package com.repo2cloud.s3server.service;
import java.util.UUID;

import com.repo2cloud.s3server.dto.CommonPrefix;
import com.repo2cloud.s3server.dto.ListObjectsResponse;
import com.repo2cloud.s3server.dto.ListingEntry;
import com.repo2cloud.s3server.dto.ObjectDownload;
import com.repo2cloud.s3server.dto.ObjectSummary;
import com.repo2cloud.s3server.entity.Bucket;
import com.repo2cloud.s3server.entity.StoredObject;
import com.repo2cloud.s3server.exception.BucketNotFoundException;
import com.repo2cloud.s3server.exception.InvalidObjectKeyException;
import com.repo2cloud.s3server.exception.ObjectNotFoundException;
import com.repo2cloud.s3server.repository.BucketRepository;
import com.repo2cloud.s3server.repository.StoredObjectRepository;
import com.repo2cloud.s3server.storage.MinioObjectStorage;
import com.repo2cloud.s3server.validation.ObjectKeyValidator;

import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ObjectService {

        private static final Logger log =
        LoggerFactory.getLogger(ObjectService.class);

    private final BucketRepository bucketRepository;
private final StoredObjectRepository storedObjectRepository;
private final MinioObjectStorage minioObjectStorage;

   public ObjectService(
        BucketRepository bucketRepository,
        StoredObjectRepository storedObjectRepository,
        MinioObjectStorage minioObjectStorage) {

    this.bucketRepository = bucketRepository;
    this.storedObjectRepository = storedObjectRepository;
    this.minioObjectStorage = minioObjectStorage;
}
    // ============================================================
    // GET OBJECT
    // ============================================================

    public ObjectDownload downloadObject(
            Long ownerId,
            String bucketName,
            String objectKey) {

        ownerId = validateOwnerId(ownerId);
        Bucket bucket = bucketRepository.findByNameAndOwnerId(bucketName, ownerId)
                .orElseThrow(() ->
                        new BucketNotFoundException(
                                "Bucket not found: " + bucketName
                        )
                );

        StoredObject storedObject =
                storedObjectRepository
                        .findByBucketIdAndObjectKey(
                                bucket.getId(),
                                objectKey
                        )
                        .orElseThrow(() ->
                                new ObjectNotFoundException(
                                        "Object not found: " + objectKey
                                )
                        );

        InputStream inputStream =
        minioObjectStorage.getObject(
                bucket.getId(),
                storedObject.getObjectKey()
        );

        InputStreamResource resource =
                new InputStreamResource(inputStream);

        return new ObjectDownload(
                resource,
                storedObject.getContentType(),
                storedObject.getSize(),
                storedObject.getEtag()
        );
    }

    // ============================================================
    // HEAD OBJECT
    // ============================================================

    public StoredObject headObject(
            Long ownerId,
            String bucketName,
            String objectKey) {

        ownerId = validateOwnerId(ownerId);
        Bucket bucket = bucketRepository.findByNameAndOwnerId(bucketName, ownerId)
                .orElseThrow(() ->
                        new BucketNotFoundException(
                                "Bucket not found: " + bucketName
                        )
                );

        return storedObjectRepository
                .findByBucketIdAndObjectKey(
                        bucket.getId(),
                        objectKey
                )
                .orElseThrow(() ->
                        new ObjectNotFoundException(
                                "Object not found: " + objectKey
                        )
                );
    }

    // ============================================================
    // DELETE OBJECT
    // ============================================================

    // ============================================================
// DELETE OBJECT
// ============================================================

public void deleteObject(
        Long ownerId,
        String bucketName,
        String objectKey) {

    // 1. Validate the owner ID.
    ownerId = validateOwnerId(ownerId);

    // 2. Find the bucket belonging to this owner.
    Bucket bucket = bucketRepository
            .findByNameAndOwnerId(bucketName, ownerId)
            .orElseThrow(() ->
                    new BucketNotFoundException(
                            "Bucket not found: " + bucketName
                    )
            );

    // 3. Look for the object's metadata in MySQL.
    Optional<StoredObject> storedObject =
            storedObjectRepository.findByBucketIdAndObjectKey(
                    bucket.getId(),
                    objectKey
            );

    // 4. If the object doesn't exist, do nothing.
    // S3-compatible DeleteObject should still succeed.
    if (storedObject.isEmpty()) {
        return;
    }

    // 5. Delete the actual object from MinIO.
    minioObjectStorage.deleteObject(
            bucket.getId(),
            storedObject.get().getObjectKey()
    );

    // 6. Remove its metadata from MySQL.
    storedObjectRepository.delete(storedObject.get());
}

    // ============================================================
    // LIST OBJECTS
    // ============================================================

    public ListObjectsResponse listObjects(
            Long ownerId,
            String bucketName,
            String prefix,
            String delimiter,
            int maxKeys,
            String continuationToken) {

        ownerId = validateOwnerId(ownerId);
        Bucket bucket = bucketRepository.findByNameAndOwnerId(bucketName, ownerId)
                .orElseThrow(() ->
                        new BucketNotFoundException(
                                "Bucket not found: " + bucketName
                        )
                );

        List<StoredObject> objects;

        if (prefix != null && !prefix.isEmpty()) {

            objects = storedObjectRepository
                    .findByBucketIdAndObjectKeyStartingWith(
                            bucket.getId(),
                            prefix
                    );

        } else {

            objects = storedObjectRepository
                    .findByBucketId(bucket.getId());
        }

        /*
         * Deterministic object ordering.
         */
        objects.sort(
                Comparator.comparing(
                        StoredObject::getObjectKey
                )
        );

        /*
         * Build one unified listing.
         *
         * An entry can be:
         *
         *   object
         *
         * or:
         *
         *   common prefix
         */
        List<ListingEntry> entries =
                new ArrayList<>();

        if (delimiter == null || delimiter.isEmpty()) {

            /*
             * No delimiter:
             * every object becomes an entry.
             */
            for (StoredObject object : objects) {

                ObjectSummary summary =
                        new ObjectSummary(
                                object.getObjectKey(),
                                object.getSize(),
                                object.getEtag(),
                                object.getCreatedAt()
                        );

                entries.add(
                        ListingEntry.object(summary)
                );
            }

        } else {

            /*
             * Delimiter mode.
             *
             * Keep only one CommonPrefix for each
             * logical directory.
             */
            Set<String> prefixSet =
                    new LinkedHashSet<>();

            for (StoredObject object : objects) {

                String key =
                        object.getObjectKey();

                int startIndex =
                        prefix == null
                                ? 0
                                : prefix.length();

                int delimiterIndex =
                        key.indexOf(
                                delimiter,
                                startIndex
                        );

                if (delimiterIndex != -1) {

                    String commonPrefix =
                            key.substring(
                                    0,
                                    delimiterIndex
                                            + delimiter.length()
                            );

                    prefixSet.add(
                            commonPrefix
                    );

                } else {

                    ObjectSummary summary =
                            new ObjectSummary(
                                    object.getObjectKey(),
                                    object.getSize(),
                                    object.getEtag(),
                                    object.getCreatedAt()
                            );

                    entries.add(
                            ListingEntry.object(
                                    summary
                            )
                    );
                }
            }

            /*
             * Add CommonPrefixes to the same listing.
             */
            for (String commonPrefix : prefixSet) {

                entries.add(
                        ListingEntry.prefix(
                                new CommonPrefix(
                                        commonPrefix
                                )
                        )
                );
            }
        }

        /*
         * Sort objects and CommonPrefixes together.
         */
        entries.sort(
                Comparator.comparing(
                        ListingEntry::getKey
                )
        );

        /*
         * Apply continuation token AFTER the unified
         * listing has been created.
         */
        if (continuationToken != null
                && !continuationToken.isEmpty()) {

            entries =
                    entries.stream()
                            .filter(entry ->
                                    entry.getKey()
                                            .compareTo(
                                                    continuationToken
                                            ) > 0
                            )
                            .collect(
                                    Collectors.toList()
                            );
        }

        /*
         * max-keys applies to both objects and
         * CommonPrefixes.
         */
        boolean truncated =
                entries.size() > maxKeys;

        List<ListingEntry> limitedEntries =
                entries.stream()
                        .limit(maxKeys)
                        .collect(
                                Collectors.toList()
                        );

        List<ObjectSummary> summaries =
                new ArrayList<>();

        List<CommonPrefix> commonPrefixes =
                new ArrayList<>();

        for (ListingEntry entry : limitedEntries) {

            if (entry.isObject()) {

                summaries.add(
                        entry.getObjectSummary()
                );

            } else {

                commonPrefixes.add(
                        entry.getCommonPrefix()
                );
            }
        }

        String nextContinuationToken = null;

        if (truncated && !limitedEntries.isEmpty()) {

            ListingEntry lastEntry =
                    limitedEntries.get(
                            limitedEntries.size() - 1
                    );

            nextContinuationToken =
                    lastEntry.getKey();
        }

        return new ListObjectsResponse(
                bucket.getName(),
                summaries,
                commonPrefixes,
                nextContinuationToken,
                truncated
        );
    }

    // ============================================================
    // RANGE GET
    // ============================================================

    public ObjectDownload downloadObjectRange(
            Long ownerId,
            String bucketName,
            String objectKey,
            long start,
            long length) {

        ownerId = validateOwnerId(ownerId);
        Bucket bucket = bucketRepository.findByNameAndOwnerId(bucketName, ownerId)
                .orElseThrow(() ->
                        new BucketNotFoundException(
                                "Bucket not found: " + bucketName
                        )
                );

        StoredObject storedObject =
                storedObjectRepository
                        .findByBucketIdAndObjectKey(
                                bucket.getId(),
                                objectKey
                        )
                        .orElseThrow(() ->
                                new ObjectNotFoundException(
                                        "Object not found: " + objectKey
                                )
                        );

        InputStream inputStream =
        minioObjectStorage.getObjectRange(
                bucket.getId(),
                storedObject.getObjectKey(),
                start,
                length
        );

        InputStreamResource resource =
                new InputStreamResource(inputStream);

        return new ObjectDownload(
                resource,
                storedObject.getContentType(),
                length,
                storedObject.getEtag()
        );
    }

    // ============================================================
    // UPLOAD OBJECT
    // ============================================================

public StoredObject uploadObject(
        Long ownerId,
        String bucketName,
        String objectKey,
        InputStream inputStream,
        String contentType) {

    ownerId = validateOwnerId(ownerId);

    // 1. Validate the object key.
    if (!ObjectKeyValidator.isValid(objectKey)) {
        throw new InvalidObjectKeyException(
                bucketName,
                objectKey
        );
    }

    // 2. Find the bucket belonging to this owner.
    Bucket bucket = bucketRepository
            .findByNameAndOwnerId(bucketName, ownerId)
            .orElseThrow(() ->
                    new BucketNotFoundException(
                            "Bucket not found: " + bucketName
                    )
            );

    // 3. Find existing metadata.
    Optional<StoredObject> existingObject =
            storedObjectRepository.findByBucketIdAndObjectKey(
                    bucket.getId(),
                    objectKey
            );

    String effectiveContentType =
            contentType != null && !contentType.isBlank()
                    ? contentType
                    : "application/octet-stream";

    // 4. Back up the original object before overwriting it.
    String backupKey = null;
    boolean backupCreated = false;

    if (existingObject.isPresent()) {
        backupKey = ".repo2cloud-internal-backup/"
                + UUID.randomUUID();

        minioObjectStorage.copyObject(
                bucket.getId(),
                objectKey,
                backupKey
        );

        backupCreated = true;
    }

    StoredObject savedObject;

    try {
        // 5. Upload the new content.
        MinioObjectStorage.UploadResult uploadResult =
                minioObjectStorage.uploadObject(
                        bucket.getId(),
                        objectKey,
                        inputStream,
                        effectiveContentType
                );

        // 6. Prepare the metadata.
        StoredObject storedObject =
                existingObject.orElseGet(StoredObject::new);

        if (existingObject.isEmpty()) {
            storedObject.setBucket(bucket);
            storedObject.setObjectKey(objectKey);
        }

        storedObject.setFileName(
                objectKey.substring(
                        objectKey.lastIndexOf("/") + 1
                )
        );

        storedObject.setContentType(effectiveContentType);
        storedObject.setSize(uploadResult.getSize());
        storedObject.setStoragePath("minio");
        storedObject.setEtag(uploadResult.getEtag());
        storedObject.setCreatedAt(LocalDateTime.now());

        // 7. Save metadata to MySQL.
        savedObject = storedObjectRepository.saveAndFlush(
                storedObject
        );

    } catch (Exception uploadOrDatabaseFailure) {

        // 8. Restore the original object after any failed overwrite.
        // Do not depend on uploadObject() having returned successfully.
        if (backupCreated) {
            try {
                minioObjectStorage.copyObject(
                        bucket.getId(),
                        backupKey,
                        objectKey
                );

            } catch (Exception restoreFailure) {
                uploadOrDatabaseFailure.addSuppressed(
                        restoreFailure
                );

                // Keep the backup for manual recovery.
                throw new RuntimeException(
                        "The upload failed and the original object "
                                + "could not be restored. Preserve and "
                                + "inspect backup: " + backupKey,
                        uploadOrDatabaseFailure
                );
            }

            // Restoration succeeded. Backup cleanup is best-effort.
            try {
                minioObjectStorage.deleteObject(
                        bucket.getId(),
                        backupKey
                );

            } catch (Exception cleanupFailure) {
                log.warn(
                        "Original object was restored, but backup "
                                + "cleanup failed. Backup key: {}",
                        backupKey,
                        cleanupFailure
                );
            }

        } else if (existingObject.isEmpty()) {

            // For a new object, remove any object that may have been
            // uploaded before the failure occurred.
            try {
                minioObjectStorage.deleteObject(
                        bucket.getId(),
                        objectKey
                );

            } catch (Exception cleanupFailure) {
                uploadOrDatabaseFailure.addSuppressed(
                        cleanupFailure
                );

                log.error(
                        "Failed to clean up object after upload failure: {}",
                        objectKey,
                        cleanupFailure
                );
            }
        }

        throw new RuntimeException(
                "Object upload or metadata persistence failed: "
                        + objectKey,
                uploadOrDatabaseFailure
        );
    }

    // 9. The upload and database update succeeded.
    // Clean up the backup separately so cleanup failure cannot
    // trigger an unnecessary rollback.
    if (backupCreated) {
        try {
            minioObjectStorage.deleteObject(
                    bucket.getId(),
                    backupKey
            );

        } catch (Exception cleanupFailure) {
            log.warn(
                    "Upload succeeded, but backup cleanup failed. "
                            + "Backup key: {}",
                    backupKey,
                    cleanupFailure
            );
        }
    }

    return savedObject;
}
    private Long validateOwnerId(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A positive Repo2Cloud user ID is required in X-Repo2Cloud-User-Id."
            );
        }
        return ownerId;
    }
}
