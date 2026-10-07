# Repo2Cloud S3-Compatible Object Storage Server

A lightweight S3-compatible object storage server built with Java and Spring Boot for the Repo2Cloud project.

The server provides S3-style bucket and object APIs while using:

- MySQL for bucket and object metadata
- Local filesystem for actual object data
- REST APIs compatible with common S3 operations
- XML responses and S3-style error responses

---

## Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA
- MySQL
- Maven
- Local Filesystem Storage

---

## Architecture

```text
                 +----------------------+
                 |      Repo2Cloud     |
                 +----------+-----------+
                            |
                            | S3 API
                            v
                 +----------------------+
                 |   S3 Server :9000    |
                 +----------+-----------+
                            |
                 +----------+-----------+
                 |                      |
                 v                      v
        +----------------+     +----------------+
        |     MySQL      |     | Local Storage  |
        |   Metadata     |     | ./s3-storage/  |
        +----------------+     +----------------+

        MySQL stores metadata such as:

Bucket information
Object names
Object sizes
ETags
Object content types
Last modified timestamps

The actual object contents are stored on the local filesystem.

Features
Bucket Operations
Create bucket
List buckets
Check bucket existence
Delete empty bucket
Prevent deletion of non-empty buckets
Object Operations
Upload object
Download object
Delete object
Check object existence
Overwrite existing objects
Nested object keys
ETag generation using MD5
Object Listing
List objects
Prefix filtering
Delimiter support
CommonPrefixes
Pagination
Continuation tokens
MaxKeys support
Advanced Object Handling
Streaming uploads
Streaming downloads
Atomic object uploads
HTTP Range requests
Object overwrite rollback
Storage and metadata consistency
Validation and Errors

The server provides S3-style error responses for common errors including:

NoSuchBucket
NoSuchKey
BucketAlreadyExists
BucketNotEmpty
InvalidBucketName
InvalidObjectKey
InvalidArgument
InvalidRange
Requirements

Before running the server, install:

Java 21
MySQL 8+
Maven (optional because Maven Wrapper is included)

Verify Java:

java -version
Database Setup

Create the database in MySQL:

CREATE DATABASE repo2cloud_s3;

The application uses Spring Data JPA/Hibernate to create and update the required tables.

Configuration

The repository contains:

src/main/resources/application-example.properties

Copy it to:

src/main/resources/application.properties
Windows PowerShell
Copy-Item src/main/resources/application-example.properties src/main/resources/application.properties

Then update the MySQL credentials:

spring.datasource.url=jdbc:mysql://localhost:3306/repo2cloud_s3
spring.datasource.username=YOUR_MYSQL_USERNAME
spring.datasource.password=YOUR_MYSQL_PASSWORD

The default server configuration is:

server.port=9000
s3.storage.root=./s3-storage
Important

application.properties is intentionally excluded from Git because it may contain local database credentials.

Do not commit passwords or other secrets to GitHub.

Running the Server
Windows

Using Maven Wrapper:

.\mvnw.cmd spring-boot:run
Linux / macOS
./mvnw spring-boot:run

The server runs on:

http://localhost:9000
Building the Project

Windows:

.\mvnw.cmd clean package

Linux/macOS:

./mvnw clean package

The generated JAR will be located inside:

target/
S3 API

The server exposes S3-style HTTP endpoints.

Bucket Operations
Create Bucket
PUT /{bucket}

Example:

PUT /my-bucket
List Buckets
GET /
Check Bucket
HEAD /{bucket}
Delete Bucket
DELETE /{bucket}

A bucket must be empty before it can be deleted.

Object Operations
Upload Object
PUT /{bucket}/{objectKey}

Example:

PUT /my-bucket/test.txt

Request body:

Hello S3
Download Object
GET /{bucket}/{objectKey}

Example:

GET /my-bucket/test.txt
Check Object
HEAD /{bucket}/{objectKey}
Delete Object
DELETE /{bucket}/{objectKey}
Object Listing

List objects in a bucket:

GET /{bucket}

Example:

GET /my-bucket
Prefix
GET /my-bucket?prefix=projects/
Delimiter
GET /my-bucket?delimiter=/
Pagination
GET /my-bucket?max-keys=2

If more objects are available, the response provides a continuation token.

The next request can use:

GET /my-bucket?max-keys=2&continuation-token=<TOKEN>
Range Requests

The server supports HTTP Range requests for object downloads.

Example:

GET /my-bucket/video.mp4
Range: bytes=0-1023

The server returns the requested byte range when it is valid.

Invalid ranges return an S3-style InvalidRange error.

Storage Model

The server separates metadata from object data.

Example:

s3-storage/
└── my-bucket/
    ├── file.txt
    └── projects/
        └── 101/
            └── build.log

The database stores metadata for these objects.

This allows the server to use the filesystem for actual object storage while MySQL manages searchable metadata.

Data Consistency

Object uploads use temporary files before replacing the final object.

The upload flow is approximately:

Client
   |
   v
Temporary File
   |
   | successful write
   v
Atomic Move
   |
   v
Object Storage
   |
   v
Metadata Update
   |
   v
MySQL

When overwriting an existing object, the previous object is temporarily backed up so that the server can restore it if the metadata update fails.

This prevents the filesystem and database from becoming inconsistent during normal overwrite operations.

Current Limitations

This project currently focuses on the core S3-compatible object storage functionality required by Repo2Cloud.

The following features are not currently implemented:

AWS Signature Version 4 authentication
Presigned URLs
Object versioning
Bucket versioning
Access Control Lists (ACLs)
Multipart uploads
Distributed storage
Replication
Cloud-based object storage backends

Authentication is intentionally not part of the current S3 server implementation.

Repo2Cloud Integration

The S3 server is designed to run as a separate service from the main Repo2Cloud application.

Example:

+----------------------+
|      Repo2Cloud      |
|                      |
| Deployment Platform  |
+----------+-----------+
           |
           | S3 API
           |
           v
+----------------------+
|     S3 Server        |
|     Port 9000        |
+----------+-----------+
           |
      +----+----+
      |         |
      v         v
   MySQL    Filesystem

Repo2Cloud can communicate with the server using its S3-style HTTP API.

Default endpoint:

http://localhost:9000
Development

Clone the repository:

git clone https://github.com/vishwajeetkapurkar-crypto/repo2cloud-s3-server.git

Enter the project:

cd repo2cloud-s3-server

Configure MySQL credentials:

src/main/resources/application.properties

Start the server:

.\mvnw.cmd spring-boot:run
Project Status

Core S3-compatible server functionality is implemented and integration-tested.

The server currently supports:

Bucket CRUD
Object CRUD
Object metadata
ETags
Prefix listing
Delimiter/CommonPrefixes
Pagination
Continuation tokens
Range requests
Atomic uploads
Storage/metadata consistency
S3-style errors
Input validation

The project is ready for integration with Repo2Cloud as a separate S3 storage service.