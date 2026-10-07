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

import org.springframework.stereotype.Service;
import com.repo2cloud.s3server.validation.BucketNameValidator;


import java.util.List;

@Service
public class BucketService {
    private final StoredObjectRepository storedObjectRepository;
    private final LocalFileStorage localFileStorage;
    private final BucketRepository bucketRepository;

   public BucketService(
        BucketRepository bucketRepository,
        StoredObjectRepository storedObjectRepository,
        LocalFileStorage localFileStorage) {

    this.bucketRepository = bucketRepository;
    this.storedObjectRepository = storedObjectRepository;
    this.localFileStorage = localFileStorage;
}

    public Bucket createBucket(String name) {

        if (!BucketNameValidator.isValid(name)) {
    throw new InvalidBucketNameException(name);
}

    if (bucketRepository.existsByName(name)) {
        throw new BucketAlreadyExistsException(
                "Bucket already exists: " + name
        );
    }

    Bucket bucket = new Bucket(name);

    return bucketRepository.save(bucket);
}

    public List<Bucket> getAllBuckets() {
        return bucketRepository.findAll();
    }

    public Bucket getBucket(String name) {

    return bucketRepository.findByName(name)
            .orElseThrow(() ->
                    new BucketNotFoundException(
                            "Bucket not found: " + name
                    ));
}

    public void deleteBucket(String name) {

    Bucket bucket = bucketRepository.findByName(name)
            .orElseThrow(() ->
                    new BucketNotFoundException(
                            "Bucket not found: " + name
                    ));

    List<StoredObject> objects =
            storedObjectRepository.findByBucketId(
                    bucket.getId()
            );

    if (!objects.isEmpty()) {

        throw new BucketNotEmptyException(name);
    }

    bucketRepository.delete(bucket);
localFileStorage.deleteBucket(name);
}

}