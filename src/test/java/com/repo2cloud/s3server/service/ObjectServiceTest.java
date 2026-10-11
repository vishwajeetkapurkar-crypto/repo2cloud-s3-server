package com.repo2cloud.s3server.service;

import com.repo2cloud.s3server.entity.Bucket;
import com.repo2cloud.s3server.entity.StoredObject;
import com.repo2cloud.s3server.repository.BucketRepository;
import com.repo2cloud.s3server.repository.StoredObjectRepository;
import com.repo2cloud.s3server.storage.MinioObjectStorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectServiceTest {

    @Mock
    private BucketRepository bucketRepository;

    @Mock
    private StoredObjectRepository storedObjectRepository;

    @Mock
    private MinioObjectStorage minioObjectStorage;

    private ObjectService objectService;

    private Bucket bucket;
    private StoredObject existingObject;

    @Test
void deletesNewObjectWhenMetadataSaveFails() {

    String objectKey = "new-failure-test.txt";

    // Arrange: the bucket exists.
    when(bucketRepository.findByNameAndOwnerId("photos", 1L))
            .thenReturn(Optional.of(bucket));

    // Arrange: this object does not exist yet.
    when(storedObjectRepository.findByBucketIdAndObjectKey(
            19L, objectKey))
            .thenReturn(Optional.empty());

    // Arrange: MinIO upload succeeds.
    when(minioObjectStorage.uploadObject(
            eq(19L),
            eq(objectKey),
            any(),
            eq("text/plain")))
            .thenReturn(
                    new MinioObjectStorage.UploadResult(
                            15L,
                            "new-object-etag"
                    )
            );

    // Arrange: saving metadata to MySQL fails.
    when(storedObjectRepository.saveAndFlush(any(StoredObject.class)))
            .thenThrow(
                    new RuntimeException("Simulated database failure")
            );

    // Act: the service should throw after attempting cleanup.
    assertThrows(
            RuntimeException.class,
            () -> objectService.uploadObject(
                    1L,
                    "photos",
                    objectKey,
                    new ByteArrayInputStream(
                            "New object data".getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            )
                    ),
                    "text/plain"
            )
    );

    // Assert: the newly uploaded object is removed.
    verify(minioObjectStorage).deleteObject(19L, objectKey);
}

@Test
void preservesBackupWhenRestoringOriginalObjectFails() {

    String objectKey = "rollback-test.txt";

    when(bucketRepository.findByNameAndOwnerId("photos", 1L))
            .thenReturn(Optional.of(bucket));

    when(storedObjectRepository.findByBucketIdAndObjectKey(
            19L, objectKey))
            .thenReturn(Optional.of(existingObject));

    // Creating the backup succeeds.
    // Restoring the original object fails.
    org.mockito.Mockito.doAnswer(invocation -> {
        String sourceKey = invocation.getArgument(1);
        String destinationKey = invocation.getArgument(2);

        if (sourceKey.startsWith(".repo2cloud-internal-backup/")) {
            throw new RuntimeException("Simulated restore failure");
        }

        return null;
    }).when(minioObjectStorage).copyObject(
            eq(19L),
            anyString(),
            anyString()
    );

    when(minioObjectStorage.uploadObject(
            eq(19L),
            eq(objectKey),
            any(),
            eq("text/plain")
    )).thenReturn(
            new MinioObjectStorage.UploadResult(
                    15L,
                    "updated-etag"
            )
    );

    when(storedObjectRepository.saveAndFlush(any(StoredObject.class)))
            .thenThrow(
                    new RuntimeException("Simulated database failure")
            );

    assertThrows(
            RuntimeException.class,
            () -> objectService.uploadObject(
                    1L,
                    "photos",
                    objectKey,
                    new ByteArrayInputStream(
                            "Updated version".getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            )
                    ),
                    "text/plain"
            )
    );

    // Capture the generated backup key.
    org.mockito.ArgumentCaptor<String> backupKeyCaptor =
            org.mockito.ArgumentCaptor.forClass(String.class);

    verify(minioObjectStorage).copyObject(
            eq(19L),
            eq(objectKey),
            backupKeyCaptor.capture()
    );

    String backupKey = backupKeyCaptor.getValue();

    // Verify that the service attempted to restore the original.
    verify(minioObjectStorage).copyObject(
            19L,
            backupKey,
            objectKey
    );

    // The backup must NOT be deleted when restoration fails.
    org.mockito.Mockito.verify(
            minioObjectStorage,
            org.mockito.Mockito.never()
    ).deleteObject(19L, backupKey);
}

@Test
void reportsFailureWhenMetadataDeletionFails() {

    String objectKey = "delete-failure-test.txt";

    // Arrange: the bucket exists.
    when(bucketRepository.findByNameAndOwnerId("photos", 1L))
            .thenReturn(Optional.of(bucket));

    // Arrange: the object's metadata exists.
    StoredObject objectToDelete = new StoredObject();
    objectToDelete.setBucket(bucket);
    objectToDelete.setObjectKey(objectKey);
    objectToDelete.setFileName(objectKey);
    objectToDelete.setContentType("text/plain");
    objectToDelete.setSize(10L);
    objectToDelete.setStoragePath("minio");
    objectToDelete.setEtag("test-etag");

    when(storedObjectRepository.findByBucketIdAndObjectKey(
            19L, objectKey))
            .thenReturn(Optional.of(objectToDelete));

    // Arrange: deleting metadata fails.
    doThrow(new RuntimeException("Simulated database failure"))
            .when(storedObjectRepository)
            .delete(objectToDelete);

    // Act: deletion should propagate the failure.
    assertThrows(
            RuntimeException.class,
            () -> objectService.deleteObject(
                    1L,
                    "photos",
                    objectKey
            )
    );

    // Assert: MinIO deletion was attempted first.
    org.mockito.InOrder order = inOrder(
            minioObjectStorage,
            storedObjectRepository
    );

    order.verify(minioObjectStorage).deleteObject(19L, objectKey);
    order.verify(storedObjectRepository).delete(objectToDelete);
}

@Test
void deletesObjectFromStorageAndDatabaseSuccessfully() {

    String objectKey = "delete-success-test.txt";

    when(bucketRepository.findByNameAndOwnerId("photos", 1L))
            .thenReturn(Optional.of(bucket));

    StoredObject objectToDelete = new StoredObject();
    objectToDelete.setBucket(bucket);
    objectToDelete.setObjectKey(objectKey);
    objectToDelete.setFileName(objectKey);
    objectToDelete.setContentType("text/plain");
    objectToDelete.setSize(10L);
    objectToDelete.setStoragePath("minio");
    objectToDelete.setEtag("test-etag");

    when(storedObjectRepository.findByBucketIdAndObjectKey(
            19L, objectKey))
            .thenReturn(Optional.of(objectToDelete));

    // Act: deletion should complete without an exception.
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () -> objectService.deleteObject(
                    1L,
                    "photos",
                    objectKey
            )
    );

    // Assert: both storage and metadata are deleted.
    org.mockito.InOrder order = inOrder(
            minioObjectStorage,
            storedObjectRepository
    );

    order.verify(minioObjectStorage).deleteObject(19L, objectKey);
    order.verify(storedObjectRepository).delete(objectToDelete);
}

@Test
void retainsMetadataWhenMinioDeletionFails() {

    String objectKey = "minio-delete-failure.txt";

    when(bucketRepository.findByNameAndOwnerId("photos", 1L))
            .thenReturn(Optional.of(bucket));

    StoredObject objectToDelete = new StoredObject();
    objectToDelete.setBucket(bucket);
    objectToDelete.setObjectKey(objectKey);
    objectToDelete.setFileName(objectKey);
    objectToDelete.setContentType("text/plain");
    objectToDelete.setSize(10L);
    objectToDelete.setStoragePath("minio");
    objectToDelete.setEtag("test-etag");

    when(storedObjectRepository.findByBucketIdAndObjectKey(
            19L, objectKey))
            .thenReturn(Optional.of(objectToDelete));

    // Simulate MinIO deletion failure.
    doThrow(new RuntimeException("Simulated MinIO failure"))
            .when(minioObjectStorage)
            .deleteObject(19L, objectKey);

    assertThrows(
            RuntimeException.class,
            () -> objectService.deleteObject(
                    1L,
                    "photos",
                    objectKey
            )
    );

    // The database metadata must not be deleted
    // when MinIO deletion fails.
    org.mockito.Mockito.verify(
            storedObjectRepository,
            org.mockito.Mockito.never()
    ).delete(objectToDelete);
}


@Test
void deletingNonexistentObjectDoesNothing() {

    String objectKey = "does-not-exist.txt";

    when(bucketRepository.findByNameAndOwnerId("photos", 1L))
            .thenReturn(Optional.of(bucket));

    when(storedObjectRepository.findByBucketIdAndObjectKey(
            19L, objectKey))
            .thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () -> objectService.deleteObject(
                    1L,
                    "photos",
                    objectKey
            )
    );

    // Neither storage nor metadata should be modified.
    org.mockito.Mockito.verify(
            minioObjectStorage,
            org.mockito.Mockito.never()
    ).deleteObject(19L, objectKey);

    org.mockito.Mockito.verify(
            storedObjectRepository,
            org.mockito.Mockito.never()
    ).delete(org.mockito.ArgumentMatchers.any(StoredObject.class));
}

    @BeforeEach
    void setUp() {
        // Create a bucket belonging to owner 1.
        bucket = new Bucket("photos", 1L);

        // Assign an ID to the entity for this unit test.
        setBucketId(bucket, 19L);

        // Prepare metadata for an existing object.
        existingObject = new StoredObject();
        existingObject.setBucket(bucket);
        existingObject.setObjectKey("rollback-test.txt");
        existingObject.setFileName("rollback-test.txt");
        existingObject.setContentType("text/plain");
        existingObject.setSize(16L);
        existingObject.setStoragePath("minio");
        existingObject.setEtag("original-etag");

        // Construct the service using Mockito's mocks.
        objectService = new ObjectService(
                bucketRepository,
                storedObjectRepository,
                minioObjectStorage
        );
    }

    private void setBucketId(Bucket bucket, Long id) {
        try {
            Field field = Bucket.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(bucket, id);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(
                    "Unable to assign bucket ID for unit test",
                    exception
            );
        }
    }

    @Test
    void restoresOriginalObjectWhenMetadataSaveFails() {

        // 1. Arrange: find the owner's bucket.
        when(bucketRepository.findByNameAndOwnerId("photos", 1L))
                .thenReturn(Optional.of(bucket));

        // 2. Arrange: the object already exists.
        when(storedObjectRepository.findByBucketIdAndObjectKey(
                19L, "rollback-test.txt"))
                .thenReturn(Optional.of(existingObject));

        // 3. Arrange: allow the backup copy to succeed.
        doNothing().when(minioObjectStorage).copyObject(
                eq(19L),
                eq("rollback-test.txt"),
                anyString()
        );

        // 4. Arrange: the replacement upload succeeds.
        when(minioObjectStorage.uploadObject(
                eq(19L),
                eq("rollback-test.txt"),
                any(),
                eq("text/plain")
        )).thenReturn(
                new MinioObjectStorage.UploadResult(
                        15L,
                        "updated-etag"
                )
        );

        // 5. Arrange: metadata persistence fails.
        when(storedObjectRepository.saveAndFlush(any(StoredObject.class)))
                .thenThrow(
                        new RuntimeException("Simulated database failure")
                );

        // 6. Act: the service should report the upload failure.
        assertThrows(
                RuntimeException.class,
                () -> objectService.uploadObject(
                        1L,
                        "photos",
                        "rollback-test.txt",
                        new ByteArrayInputStream(
                                "Updated version".getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8
                                )
                        ),
                        "text/plain"
                )
        );

        // 7. Verify: the service created a backup.
        ArgumentCaptor<String> backupKeyCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(minioObjectStorage).copyObject(
                eq(19L),
                eq("rollback-test.txt"),
                backupKeyCaptor.capture()
        );

        String backupKey = backupKeyCaptor.getValue();

        // Verify that the backup key uses our internal backup prefix.
        org.junit.jupiter.api.Assertions.assertTrue(
                backupKey.startsWith(".repo2cloud-internal-backup/"),
                "The backup key should use the internal backup prefix"
        );

        // 8. Verify: the service attempted to restore the original object.
        verify(minioObjectStorage).copyObject(
                19L,
                backupKey,
                "rollback-test.txt"
        );

        // 9. Verify: the service attempted to clean up the backup.
        verify(minioObjectStorage).deleteObject(
                19L,
                backupKey
        );
    }
}