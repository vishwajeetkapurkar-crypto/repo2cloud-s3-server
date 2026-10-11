package com.repo2cloud.s3server.repository;

import com.repo2cloud.s3server.entity.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BucketRepository extends JpaRepository<Bucket, Long> {
    Optional<Bucket> findByNameAndOwnerId(String name, Long ownerId);
    boolean existsByNameAndOwnerId(String name, Long ownerId);
    List<Bucket> findAllByOwnerIdOrderByNameAsc(Long ownerId);
}
