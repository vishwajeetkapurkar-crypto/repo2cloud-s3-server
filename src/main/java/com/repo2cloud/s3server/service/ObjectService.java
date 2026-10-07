package com.repo2cloud.s3server.service;

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
import com.repo2cloud.s3server.storage.LocalFileStorage;
import com.repo2cloud.s3server.storage.StreamingUploadResult;
import com.repo2cloud.s3server.validation.ObjectKeyValidator;

import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ObjectService {

    private final BucketRepository bucketRepository;
    private final StoredObjectRepository storedObjectRepository;
    private final LocalFileStorage localFileStorage;

    public ObjectService(
            BucketRepository bucketRepository,
            StoredObjectRepository storedObjectRepository,
            LocalFileStorage localFileStorage) {

        this.bucketRepository = bucketRepository;
        this.storedObjectRepository = storedObjectRepository;
        this.localFileStorage = localFileStorage;
    }

    // ============================================================
    // GET OBJECT
    // ============================================================

    public ObjectDownload downloadObject(
            String bucketName,
            String objectKey) {

        Bucket bucket = bucketRepository.findByName(bucketName)
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

        Path objectPath = Path.of(
                storedObject.getStoragePath()
        );

        InputStream inputStream =
                localFileStorage.getObjectStream(
                        objectPath
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
            String bucketName,
            String objectKey) {

        Bucket bucket = bucketRepository.findByName(bucketName)
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

    public void deleteObject(
            String bucketName,
            String objectKey) {

        Bucket bucket = bucketRepository.findByName(bucketName)
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

        Path objectPath = Path.of(
                storedObject.getStoragePath()
        );

        // Delete actual file
        localFileStorage.deleteObject(objectPath);

        // Delete metadata
        storedObjectRepository.delete(storedObject);
    }

    // ============================================================
    // LIST OBJECTS
    // ============================================================

    public ListObjectsResponse listObjects(
            String bucketName,
            String prefix,
            String delimiter,
            int maxKeys,
            String continuationToken) {

        Bucket bucket = bucketRepository.findByName(bucketName)
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
            String bucketName,
            String objectKey,
            long start,
            long length) {

        Bucket bucket = bucketRepository.findByName(bucketName)
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

        Path objectPath =
                Path.of(
                        storedObject.getStoragePath()
                );

        InputStream inputStream =
                localFileStorage.getObjectRangeStream(
                        objectPath,
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
            String bucketName,
            String objectKey,
            InputStream inputStream,
            String contentType) {

        /*
         * Validate object key before doing any filesystem
         * or database operation.
         */
        if (!ObjectKeyValidator.isValid(objectKey)) {

            throw new InvalidObjectKeyException(
                    bucketName,
                    objectKey
            );
        }

        /*
         * Make sure bucket exists.
         */
        Bucket bucket =
                bucketRepository.findByName(bucketName)
                        .orElseThrow(() ->
                                new BucketNotFoundException(
                                        "Bucket not found: "
                                                + bucketName
                                )
                        );

        /*
         * Check whether this is a new object or an overwrite.
         */
        Optional<StoredObject> existingObject =
                storedObjectRepository
                        .findByBucketIdAndObjectKey(
                                bucket.getId(),
                                objectKey
                        );

        // ========================================================
        // EXISTING OBJECT -> OVERWRITE
        // ========================================================

        if (existingObject.isPresent()) {

            StoredObject storedObject =
                    existingObject.get();

            /*
             * Keep a backup of the old physical file.
             *
             * If the database update fails after the new file
             * has been written, we can restore the old file.
             */
            Path oldObjectPath =
                    Path.of(
                            storedObject.getStoragePath()
                    );

            Path backupPath =
                    localFileStorage.backupObject(
                            oldObjectPath
                    );

            StreamingUploadResult uploadResult;

            try {

                /*
                 * Save the new file.
                 *
                 * LocalFileStorage already writes to a temporary
                 * file and atomically replaces the target.
                 */
                uploadResult =
        localFileStorage.saveObject(
                bucketName,
                objectKey,
                inputStream
        );

                /*
                 * Update metadata.
                 */
                storedObject.setFileName(
                        objectKey.substring(
                                objectKey.lastIndexOf("/") + 1
                        )
                );

                storedObject.setContentType(
                        contentType != null
                                ? contentType
                                : "application/octet-stream"
                );

                storedObject.setSize(
                        uploadResult.getSize()
                );

                storedObject.setStoragePath(
                        uploadResult
                                .getStoragePath()
                                .toString()
                );

                storedObject.setEtag(
                        uploadResult.getEtag()
                );

                storedObject.setCreatedAt(
                        LocalDateTime.now()
                );

                /*
                 * Save metadata.
                 */
                StoredObject savedObject =
        storedObjectRepository.saveAndFlush(
                storedObject
        );

                /*
                 * Database update succeeded.
                 * Old backup is no longer required.
                 */
                localFileStorage.deleteBackup(
                        backupPath
                );

                return savedObject;

            } catch (Exception e) {

                /*
                 * Something failed while replacing the object.
                 *
                 * Restore the previous physical file so that
                 * filesystem and metadata remain consistent.
                 */
                try {

                    localFileStorage.restoreBackup(
                            backupPath,
                            oldObjectPath
                    );

                } catch (Exception restoreException) {

                    /*
                     * Preserve the original exception while
                     * keeping the restore failure as context.
                     */
                    e.addSuppressed(
                            restoreException
                    );
                }

                throw e;
            }
        }

        // ========================================================
        // NEW OBJECT
        // ========================================================

        StreamingUploadResult uploadResult =
                localFileStorage.saveObject(
                        bucketName,
                        objectKey,
                        inputStream
                );

        StoredObject storedObject =
                new StoredObject();

        storedObject.setBucket(bucket);

        storedObject.setObjectKey(
                objectKey
        );

        storedObject.setFileName(
                objectKey.substring(
                        objectKey.lastIndexOf("/") + 1
                )
        );

        storedObject.setContentType(
                contentType != null
                        ? contentType
                        : "application/octet-stream"
        );

        storedObject.setSize(
                uploadResult.getSize()
        );

        storedObject.setStoragePath(
                uploadResult
                        .getStoragePath()
                        .toString()
        );

        storedObject.setEtag(
                uploadResult.getEtag()
        );

        storedObject.setCreatedAt(
                LocalDateTime.now()
        );

        try {

            /*
             * Save metadata.
             */
            return storedObjectRepository.save(
                    storedObject
            );

        } catch (Exception e) {

            /*
             * Database save failed.
             *
             * Remove the physical file because there is no
             * valid metadata record for it.
             */
            try {

                localFileStorage.deleteObject(
                        uploadResult.getStoragePath()
                );

            } catch (Exception cleanupException) {

                e.addSuppressed(
                        cleanupException
                );
            }

            throw e;
        }
    }
}