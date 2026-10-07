package com.repo2cloud.s3server.repository;

import com.repo2cloud.s3server.entity.StoredObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoredObjectRepository extends JpaRepository<StoredObject, Long> {


        
    Optional<StoredObject> findByBucketIdAndObjectKey(
            Long bucketId,
            String objectKey
    );

    boolean existsByBucketIdAndObjectKey(
            Long bucketId,
            String objectKey
    );

    List<StoredObject> findByBucketId(Long bucketId);

    List<StoredObject> findByBucketIdAndObjectKeyStartingWith(
            Long bucketId,
            String prefix
    );
}