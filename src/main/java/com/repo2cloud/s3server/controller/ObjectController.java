package com.repo2cloud.s3server.controller;

import com.repo2cloud.s3server.dto.ByteRange;
import com.repo2cloud.s3server.dto.ListObjectsResponse;
import com.repo2cloud.s3server.dto.S3ListBucketResult;
import com.repo2cloud.s3server.dto.ObjectDownload;
import com.repo2cloud.s3server.entity.StoredObject;
import com.repo2cloud.s3server.service.ObjectService;
import com.repo2cloud.s3server.util.RangeUtil;

import org.springframework.http.HttpStatus;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import com.repo2cloud.s3server.exception.InvalidListParameterException;
import com.repo2cloud.s3server.exception.InvalidObjectKeyException;
import com.repo2cloud.s3server.validation.ObjectKeyValidator;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/")
public class ObjectController {

    private final ObjectService objectService;

    public ObjectController(ObjectService objectService) {
        this.objectService = objectService;
    }

   @GetMapping("/{bucketName}/**")
public ResponseEntity<Resource> downloadObject(
        @PathVariable String bucketName,
        @RequestHeader(
                value = "Range",
                required = false
        ) String rangeHeader,
        HttpServletRequest request) {

    String requestPath = request.getRequestURI();

    String bucketPrefix = "/" + bucketName + "/";

    String objectKey = requestPath.substring(
            requestPath.indexOf(bucketPrefix)
                    + bucketPrefix.length()
    );

    if (!ObjectKeyValidator.isValid(objectKey)) {
    throw new InvalidObjectKeyException(
            bucketName,
            objectKey
    );
}

  if (rangeHeader == null || rangeHeader.isBlank()) {

    ObjectDownload download =
            objectService.downloadObject(
                    bucketName,
                    objectKey
            );

    return ResponseEntity.ok()
            .contentType(
                    MediaType.parseMediaType(
                            download.getContentType()
                    )
            )
            .contentLength(
                    download.getContentLength()
            )
            .header(
                    HttpHeaders.ETAG,
                    download.getEtag()
            )
            .header(
                    HttpHeaders.ACCEPT_RANGES,
                    "bytes"
            )
            .body(
                    download.getResource()
            );
}
StoredObject object =
        objectService.headObject(
                bucketName,
                objectKey
        );

        ByteRange byteRange =
        RangeUtil.parseRange(
                rangeHeader,
                object.getSize()
        );

        ObjectDownload download =
        objectService.downloadObjectRange(
                bucketName,
                objectKey,
                byteRange.getStart(),
                byteRange.getLength()
        );

   return ResponseEntity
        .status(HttpStatus.PARTIAL_CONTENT)
        .contentType(
                MediaType.parseMediaType(
                        download.getContentType()
                )
        )
        .contentLength(
                download.getContentLength()
        )
        .header(
                HttpHeaders.ACCEPT_RANGES,
                "bytes"
        )
        .header(
                HttpHeaders.CONTENT_RANGE,
                "bytes "
                        + byteRange.getStart()
                        + "-"
                        + byteRange.getEnd()
                        + "/"
                        + object.getSize()
        )
        .header(
                HttpHeaders.ETAG,
                download.getEtag()
        )
        .body(
                download.getResource()
        );
}

@DeleteMapping("/{bucketName}/**")
public ResponseEntity<Void> deleteObject(
        @PathVariable String bucketName,
        HttpServletRequest request) {

    String requestPath = request.getRequestURI();

    String bucketPrefix = "/" + bucketName + "/";

    String objectKey = requestPath.substring(
            requestPath.indexOf(bucketPrefix)
                    + bucketPrefix.length()
    );

    if (!ObjectKeyValidator.isValid(objectKey)) {
    throw new InvalidObjectKeyException(
            bucketName,
            objectKey
    );
}

    objectService.deleteObject(
            bucketName,
            objectKey
    );

    return ResponseEntity.noContent().build();
}

@RequestMapping(
        value = "/{bucketName}/{objectKey:.+}",
        method = RequestMethod.HEAD
)
public ResponseEntity<Void> headObject(
        @PathVariable String bucketName,
        @PathVariable String objectKey) {

                if (!ObjectKeyValidator.isValid(objectKey)) {
    throw new InvalidObjectKeyException(
            bucketName,
            objectKey
    );
}

    StoredObject object =
            objectService.headObject(
                    bucketName,
                    objectKey
            );

    return ResponseEntity.ok()
            .header(
                    HttpHeaders.CONTENT_TYPE,
                    object.getContentType()
            )
            .header(
                    HttpHeaders.CONTENT_LENGTH,
                    String.valueOf(object.getSize())
            )
            .header(
                    HttpHeaders.ETAG,
                    object.getEtag()
            )
            .build();
}


@GetMapping(
        value = "/{bucketName}",
        produces = MediaType.APPLICATION_XML_VALUE
)
public ResponseEntity<S3ListBucketResult> listObjects(
        @PathVariable String bucketName,

        @RequestParam(
                value = "prefix",
                required = false
        ) String prefix,

        @RequestParam(
        value = "continuation-token",
        required = false
) String continuationToken,

        @RequestParam(
                value = "delimiter",
                required = false
        ) String delimiter,

        @RequestParam(
                value = "max-keys",
                required = false,
                defaultValue = "1000"
        ) int maxKeys) {

                if (maxKeys < 1 || maxKeys > 1000) {
    throw new InvalidListParameterException(
            "max-keys must be between 1 and 1000."
    );
}

   ListObjectsResponse response =
        objectService.listObjects(
                bucketName,
                prefix,
                delimiter,
                maxKeys,
                continuationToken
        );


    S3ListBucketResult xmlResponse =
        new S3ListBucketResult(
                response.getName(),
                response.getContents(),
                response.getCommonPrefixes(),
                response.getContents().size()
        + response.getCommonPrefixes().size(),
                maxKeys,
                response.isTruncated(),
                response.getNextContinuationToken()
        );

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_XML)
            .body(xmlResponse);
}

  @PutMapping("/{bucketName}/**")
public ResponseEntity<StoredObject> uploadObject(
        @PathVariable String bucketName,
        @RequestHeader(
                value = "Content-Type",
                required = false
        ) String contentType,
        HttpServletRequest request) throws IOException {

    String requestPath = request.getRequestURI();

    String bucketPrefix = "/" + bucketName + "/";

    String objectKey = requestPath.substring(
            requestPath.indexOf(bucketPrefix)
                    + bucketPrefix.length()
    );

    StoredObject storedObject =
            objectService.uploadObject(
                    bucketName,
                    objectKey,
                    request.getInputStream(),
                    contentType
            );

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(
                    "ETag",
                    storedObject.getEtag()
            )
            .body(storedObject);
}
}