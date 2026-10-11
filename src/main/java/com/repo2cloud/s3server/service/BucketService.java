package com.repo2cloud.s3server.service;

import com.repo2cloud.s3server.entity.Bucket;
import com.repo2cloud.s3server.entity.StoredObject;
import com.repo2cloud.s3server.exception.BucketAlreadyExistsException;
import com.repo2cloud.s3server.exception.BucketNotEmptyException;
import com.repo2cloud.s3server.exception.BucketNotFoundException;
import com.repo2cloud.s3server.exception.InvalidBucketNameException;
import com.repo2cloud.s3server.repository.BucketRepository;
import com.repo2cloud.s3server.repository.StoredObjectRepository;
import com.repo2cloud.s3server.storage.MinioObjectStorage;
import com.repo2cloud.s3server.validation.BucketNameValidator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BucketService {

    private final StoredObjectRepository storedObjectRepository;
    private final BucketRepository bucketRepository;
    private final MinioObjectStorage minioObjectStorage;

    public BucketService(
            BucketRepository bucketRepository,
            StoredObjectRepository storedObjectRepository,
            MinioObjectStorage minioObjectStorage) {

        this.bucketRepository = bucketRepository;
        this.storedObjectRepository = storedObjectRepository;
        this.minioObjectStorage = minioObjectStorage;
    }

    /*
     * Create a logical Repo2Cloud bucket in MySQL
     * and its corresponding physical bucket in MinIO.
     */
    public Bucket createBucket(String name, Long ownerId) {

        ownerId = validateOwnerId(ownerId);

        if (!BucketNameValidator.isValid(name)) {
            throw new InvalidBucketNameException(name);
        }

        if (bucketRepository.existsByNameAndOwnerId(name, ownerId)) {
            throw new BucketAlreadyExistsException(
                    "Bucket already exists: " + name
            );
        }

        // Step 1: Save the logical bucket in MySQL.
        Bucket bucket = bucketRepository.save(
                new Bucket(name, ownerId)
        );

        try {
            // Step 2: Create its physical bucket in MinIO.
            minioObjectStorage.createBucket(bucket.getId());

            return bucket;

        } catch (RuntimeException exception) {

            // Compensate for a failed MinIO operation.
            bucketRepository.delete(bucket);

            throw exception;
        }
    }

    /*
     * Return buckets belonging to the requested owner.
     */
    public List<Bucket> getAllBuckets(Long ownerId) {

        ownerId = validateOwnerId(ownerId);

        return bucketRepository
                .findAllByOwnerIdOrderByNameAsc(ownerId);
    }

    /*
     * Find a bucket belonging to the requested owner.
     */
    public Bucket getBucket(String name, Long ownerId) {

        ownerId = validateOwnerId(ownerId);

        return bucketRepository
                .findByNameAndOwnerId(name, ownerId)
                .orElseThrow(() ->
                        new BucketNotFoundException(
                                "Bucket not found: " + name
                        )
                );
    }

    /*
     * Delete an empty logical bucket and its physical
     * MinIO bucket.
     */
    public void deleteBucket(String name, Long ownerId) {

        Bucket bucket = getBucket(name, ownerId);

        List<StoredObject> objects =
                storedObjectRepository.findByBucketId(
                        bucket.getId()
                );

        if (!objects.isEmpty()) {
            throw new BucketNotEmptyException(name);
        }

        // Delete the physical bucket first.
        minioObjectStorage.deleteBucket(bucket.getId());

        // Delete the logical bucket from MySQL.
        bucketRepository.delete(bucket);
    }

    /*
     * Validate the Repo2Cloud user ID.
     */
    private Long validateOwnerId(Long ownerId) {

        if (ownerId == null || ownerId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A positive Repo2Cloud user ID is required " +
                            "in X-Repo2Cloud-User-Id."
            );
        }

        return ownerId;
    }
}