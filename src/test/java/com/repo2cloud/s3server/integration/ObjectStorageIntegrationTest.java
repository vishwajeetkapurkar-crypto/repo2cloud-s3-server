package com.repo2cloud.s3server.integration;

import com.repo2cloud.s3server.dto.CommonPrefix;
import com.repo2cloud.s3server.dto.ObjectDownload;
import com.repo2cloud.s3server.entity.Bucket;
import com.repo2cloud.s3server.entity.StoredObject;
import com.repo2cloud.s3server.repository.StoredObjectRepository;
import com.repo2cloud.s3server.service.BucketService;
import com.repo2cloud.s3server.service.ObjectService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ObjectStorageIntegrationTest {

    @Autowired
    private BucketService bucketService;

    @Autowired
    private ObjectService objectService;

    @Autowired
    private StoredObjectRepository storedObjectRepository;

    private final Long ownerId = 987654L;

    private String bucketName;

    @BeforeEach
    void setUp() {
        bucketName = "integration-" +
                UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 12);

        bucketService.createBucket(bucketName, ownerId);
    }

    @AfterEach
    void tearDown() {
        if (bucketName == null) {
            return;
        }

        try {
            // Integration tests may intentionally leave objects behind.
            // Delete through ObjectService so MinIO and MySQL stay in sync.
            Bucket bucket = bucketService.getBucket(bucketName, ownerId);
            for (StoredObject storedObject :
                    storedObjectRepository.findByBucketId(bucket.getId())) {
                objectService.deleteObject(
                        ownerId,
                        bucketName,
                        storedObject.getObjectKey()
                );
            }

            bucketService.deleteBucket(bucketName, ownerId);
        } catch (com.repo2cloud.s3server.exception.BucketNotFoundException ignored) {
            // The test may already have deleted the bucket.
        } catch (Exception exception) {
            System.err.println(
                    "Test cleanup failed for bucket: " + bucketName
                            + " - " + exception.getMessage()
            );
        }
    }

    @Test
    void uploadsAndDownloadsObjectSuccessfully() throws Exception {

        String objectKey = "integration-test.txt";
        String originalContent =
                "Hello from the Repo2Cloud S3 integration test!";

        byte[] originalBytes =
                originalContent.getBytes(StandardCharsets.UTF_8);

        // Upload using the real ObjectService.
        StoredObject storedObject = objectService.uploadObject(
                ownerId,
                bucketName,
                objectKey,
                new ByteArrayInputStream(originalBytes),
                "text/plain"
        );

        // Verify metadata was returned.
        assertNotNull(storedObject);
        assertEquals(objectKey, storedObject.getObjectKey());
        assertEquals((long) originalBytes.length, storedObject.getSize());
        assertEquals("text/plain", storedObject.getContentType());

        // Verify metadata was actually persisted in MySQL.
        Bucket bucket = bucketService.getBucket(bucketName, ownerId);

        StoredObject databaseObject =
                storedObjectRepository
                        .findByBucketIdAndObjectKey(
                                bucket.getId(),
                                objectKey
                        )
                        .orElseThrow();

        assertEquals(objectKey, databaseObject.getObjectKey());
        assertEquals((long) originalBytes.length, databaseObject.getSize());

        // Download the actual object from MinIO.
        ObjectDownload download = objectService.downloadObject(
                ownerId,
                bucketName,
                objectKey
        );

        assertNotNull(download);

        try (InputStream inputStream =
                     download.getResource().getInputStream()) {

            byte[] downloadedBytes = inputStream.readAllBytes();

            assertArrayEquals(
                    originalBytes,
                    downloadedBytes,
                    "Downloaded content must match the original upload"
            );
        }
    }
    @Test
void deletesObjectFromMinioAndMysqlSuccessfully() throws Exception {

    String objectKey = "delete-test.txt";
    byte[] content = "Object to be deleted".getBytes(StandardCharsets.UTF_8);

    // 1. Upload the object.
    objectService.uploadObject(
            ownerId,
            bucketName,
            objectKey,
            new ByteArrayInputStream(content),
            "text/plain"
    );

    // 2. Verify that metadata exists in MySQL.
    Bucket bucket = bucketService.getBucket(bucketName, ownerId);

    StoredObject databaseObject = storedObjectRepository
            .findByBucketIdAndObjectKey(bucket.getId(), objectKey)
            .orElseThrow(() -> new AssertionError(
                    "Object metadata should exist before deletion"
            ));

    assertNotNull(databaseObject);

    // 3. Delete the object using the real ObjectService.
    objectService.deleteObject(ownerId, bucketName, objectKey);

    // 4. Verify that metadata was removed from MySQL.
    assertTrue(
            storedObjectRepository
                    .findByBucketIdAndObjectKey(bucket.getId(), objectKey)
                    .isEmpty(),
            "Object metadata should be removed from MySQL"
    );

    // 5. Verify that the object can no longer be downloaded.
    assertThrows(
            Exception.class,
            () -> objectService.downloadObject(
                    ownerId,
                    bucketName,
                    objectKey
            ),
            "A deleted object should not be downloadable"
    );
}

@Test
void usersCannotAccessEachOthersBuckets() {

    Long anotherOwnerId = 987655L;

    String sharedBucketName = "isolation-" +
            UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12);

    boolean firstBucketCreated = false;
    boolean secondBucketCreated = false;

    try {
        // Create a bucket for the original owner.
        bucketService.createBucket(sharedBucketName, ownerId);
        firstBucketCreated = true;

        // Create a bucket with the same name for another owner.
        bucketService.createBucket(sharedBucketName, anotherOwnerId);
        secondBucketCreated = true;

        // Both owners can access their own bucket.
        Bucket ownerBucket =
                bucketService.getBucket(sharedBucketName, ownerId);

        Bucket anotherOwnerBucket =
                bucketService.getBucket(sharedBucketName, anotherOwnerId);

        assertNotNull(ownerBucket);
        assertNotNull(anotherOwnerBucket);

        // Each owner must have a separate logical bucket.
        assertNotEquals(
                ownerBucket.getId(),
                anotherOwnerBucket.getId()
        );

        // A third user must not access either bucket.
        assertThrows(
                com.repo2cloud.s3server.exception.BucketNotFoundException.class,
                () -> bucketService.getBucket(sharedBucketName, 999999L)
        );

    } finally {
        // Clean up only buckets that were successfully created.
        if (secondBucketCreated) {
            bucketService.deleteBucket(sharedBucketName, anotherOwnerId);
        }

        if (firstBucketCreated) {
            bucketService.deleteBucket(sharedBucketName, ownerId);
        }
    }
}


@Test
void usersCannotAccessEachOthersObjects() throws Exception {

    Long anotherOwnerId = 987655L;

    String objectKey = "private.txt";
    byte[] originalContent =
            "This file belongs to the first owner."
                    .getBytes(StandardCharsets.UTF_8);

    // Create a separate bucket for the second owner.
    String anotherBucketName = "isolation-" +
            UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12);

    boolean anotherBucketCreated = false;

    try {
        bucketService.createBucket(anotherBucketName, anotherOwnerId);
        anotherBucketCreated = true;

        // Owner A uploads a private object to Owner A's bucket.
        objectService.uploadObject(
                ownerId,
                bucketName,
                objectKey,
                new ByteArrayInputStream(originalContent),
                "text/plain"
        );

        // Owner B cannot download Owner A's object
        // by requesting it from Owner A's bucket.
        assertThrows(
                com.repo2cloud.s3server.exception.BucketNotFoundException.class,
                () -> objectService.downloadObject(
                        anotherOwnerId,
                        bucketName,
                        objectKey
                )
        );

        // Owner B cannot delete Owner A's object.
        assertThrows(
                com.repo2cloud.s3server.exception.BucketNotFoundException.class,
                () -> objectService.deleteObject(
                        anotherOwnerId,
                        bucketName,
                        objectKey
                )
        );

        // Owner A's object must remain accessible.
        try (InputStream inputStream = objectService
                .downloadObject(ownerId, bucketName, objectKey)
                .getResource()
                .getInputStream()) {

            assertArrayEquals(
                    originalContent,
                    inputStream.readAllBytes(),
                    "The original owner's object must remain unchanged"
            );
        }

    } finally {
        if (anotherBucketCreated) {
            bucketService.deleteBucket(
                    anotherBucketName,
                    anotherOwnerId
            );
        }
    }
}

@Test
void listsObjectsWithPrefixAndPagination() throws Exception {

    String[] objectKeys = {
            "docs/a.txt",
            "docs/b.txt",
            "images/c.txt"
    };

    // Upload three objects.
    for (String objectKey : objectKeys) {
        byte[] content = ("Content of " + objectKey)
                .getBytes(StandardCharsets.UTF_8);

        objectService.uploadObject(
                ownerId,
                bucketName,
                objectKey,
                new ByteArrayInputStream(content),
                "text/plain"
        );
    }

    // Verify prefix filtering.
    var prefixResult = objectService.listObjects(
            ownerId,
            bucketName,
            "docs/",
            null,
            100,
            null
    );

    assertEquals(
            2,
            prefixResult.getContents().size(),
            "The docs/ prefix should return two objects"
    );

    // Retrieve the first page.
    var firstPage = objectService.listObjects(
            ownerId,
            bucketName,
            null,
            null,
            2,
            null
    );

    assertEquals(2, firstPage.getContents().size());
    assertTrue(firstPage.isTruncated());

    String continuationToken = firstPage.getNextContinuationToken();
    assertNotNull(continuationToken);

    // Retrieve the next page.
    var secondPage = objectService.listObjects(
            ownerId,
            bucketName,
            null,
            null,
            2,
            continuationToken
    );

    assertEquals(1, secondPage.getContents().size());
    assertFalse(secondPage.isTruncated());
}

@Test
void listsObjectsUsingDelimiterAndReturnsCommonPrefixes() throws Exception {

    String[] objectKeys = {
            "docs/a.txt",
            "docs/b.txt",
            "images/c.txt",
            "readme.txt"
    };

    try {
        // Upload objects for the delimiter test.
        for (String objectKey : objectKeys) {
            byte[] content = ("Content of " + objectKey)
                    .getBytes(StandardCharsets.UTF_8);

            objectService.uploadObject(
                    ownerId,
                    bucketName,
                    objectKey,
                    new ByteArrayInputStream(content),
                    "text/plain"
            );
        }

        // Request a listing grouped by directory.
        var result = objectService.listObjects(
                ownerId,
                bucketName,
                null,
                "/",
                100,
                null
        );

        // Verify the listing.
        assertEquals(1, result.getContents().size());
        assertEquals(
                "readme.txt",
                result.getContents().get(0).getKey()
        );

        assertEquals(2, result.getCommonPrefixes().size());

        var prefixes = result.getCommonPrefixes()
                .stream()
                .map(CommonPrefix::getPrefix)
                .toList();

        assertTrue(prefixes.contains("docs/"));
        assertTrue(prefixes.contains("images/"));
        assertFalse(result.isTruncated());

    } finally {
        // Remove test objects from MinIO and MySQL.
        for (String objectKey : objectKeys) {
            objectService.deleteObject(
                    ownerId,
                    bucketName,
                    objectKey
            );
        }
    }
}

}