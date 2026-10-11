package com.repo2cloud.s3server.service;

import com.repo2cloud.s3server.entity.Bucket;
import com.repo2cloud.s3server.entity.StoredObject;
import com.repo2cloud.s3server.exception.BucketAlreadyExistsException;
import com.repo2cloud.s3server.exception.BucketNotEmptyException;
import com.repo2cloud.s3server.exception.BucketNotFoundException;
import com.repo2cloud.s3server.exception.InvalidBucketNameException;
import com.repo2cloud.s3server.repository.BucketRepository;
import com.repo2cloud.s3server.repository.StoredObjectRepository;
import com.repo2cloud.s3server.storage.LocalFileStorage;
import com.repo2cloud.s3server.validation.BucketNameValidator;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BucketService {
    private final StoredObjectRepository storedObjectRepository;
    private final LocalFileStorage localFileStorage;
    private final BucketRepository bucketRepository;

    public BucketService(BucketRepository bucketRepository, StoredObjectRepository storedObjectRepository, LocalFileStorage localFileStorage) {
        this.bucketRepository = bucketRepository;
        this.storedObjectRepository = storedObjectRepository;
        this.localFileStorage = localFileStorage;
    }

    public Bucket createBucket(String name, Long ownerId) {
        ownerId = validateOwnerId(ownerId);
        if (!BucketNameValidator.isValid(name)) throw new InvalidBucketNameException(name);
        if (bucketRepository.existsByNameAndOwnerId(name, ownerId)) {
            throw new BucketAlreadyExistsException("Bucket already exists: " + name);
        }
        return bucketRepository.save(new Bucket(name, ownerId));
    }

    public List<Bucket> getAllBuckets(Long ownerId) {
        ownerId = validateOwnerId(ownerId);
        return bucketRepository.findAllByOwnerIdOrderByNameAsc(ownerId);
    }

    public Bucket getBucket(String name, Long ownerId) {
        ownerId = validateOwnerId(ownerId);
        return bucketRepository.findByNameAndOwnerId(name, ownerId)
                .orElseThrow(() -> new BucketNotFoundException("Bucket not found: " + name));
    }

    public void deleteBucket(String name, Long ownerId) {
        Bucket bucket = getBucket(name, ownerId);
        List<StoredObject> objects = storedObjectRepository.findByBucketId(bucket.getId());
        if (!objects.isEmpty()) throw new BucketNotEmptyException(name);
        bucketRepository.delete(bucket);
        // Physical storage uses the database bucket ID as an isolated namespace.
        localFileStorage.deleteBucket(bucket.getId().toString());
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
