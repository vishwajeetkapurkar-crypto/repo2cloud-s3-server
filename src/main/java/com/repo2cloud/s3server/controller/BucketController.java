package com.repo2cloud.s3server.controller;

import com.repo2cloud.s3server.entity.Bucket;
import com.repo2cloud.s3server.service.BucketService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;



@RestController
@RequestMapping("/")
public class BucketController {

    private final BucketService bucketService;

    public BucketController(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    @PutMapping("/{bucketName}")
    public ResponseEntity<Bucket> createBucket(
            @PathVariable String bucketName,
            @RequestHeader("X-Repo2Cloud-User-Id") Long ownerId) {

        Bucket bucket = bucketService.createBucket(bucketName, ownerId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bucket);
    }

    @GetMapping
    public ResponseEntity<List<Bucket>> getAllBuckets(
            @RequestHeader("X-Repo2Cloud-User-Id") Long ownerId) {

        return ResponseEntity.ok(
                bucketService.getAllBuckets(ownerId)
        );
    }

    @DeleteMapping("/{bucketName}")
    public ResponseEntity<Void> deleteBucket(
            @PathVariable String bucketName,
            @RequestHeader("X-Repo2Cloud-User-Id") Long ownerId) {

        bucketService.deleteBucket(bucketName, ownerId);

        return ResponseEntity.noContent().build();
    }

     @RequestMapping(
        value = "/{bucketName}",
        method = RequestMethod.HEAD
)
public ResponseEntity<Void> headBucket(
        @PathVariable String bucketName,
        @RequestHeader("X-Repo2Cloud-User-Id") Long ownerId) {

    bucketService.getBucket(bucketName, ownerId);

    return ResponseEntity.ok().build();
}}