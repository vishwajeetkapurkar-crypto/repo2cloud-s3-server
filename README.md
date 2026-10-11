# Repo2Cloud S3-Compatible Object Storage Server

A lightweight, S3-compatible object storage server built with Java and Spring Boot for the Repo2Cloud cloud deployment platform.

The server provides REST APIs for storing, retrieving, listing, and deleting objects using S3-style bucket and object operations.

It uses MySQL to store metadata and the local filesystem to store actual object contents. The service runs independently of the main Repo2Cloud backend and identifies bucket ownership using numeric user IDs.

## Features

### Bucket Management
- Create buckets.
- List buckets belonging to a user.
- Check whether a bucket exists.
- Delete empty buckets.
- Prevent deletion of non-empty buckets.
- Allow different users to own buckets with the same name.
- Enforce bucket ownership using numeric user IDs.

### Object Management
- Upload objects.
- Download objects.
- Delete objects.
- Check whether an object exists.
- Overwrite existing objects.
- Support nested object keys.
- Store object metadata in MySQL.
- Store actual object contents on the local filesystem.
- Generate MD5-based ETags.
- Enforce object access through bucket ownership.

### Object Listing
- List objects within a bucket.
- Filter objects using prefixes.
- Support delimiters and common prefixes.
- Paginate object listings.
- Support maximum-key limits.
- Support continuation tokens.

### Advanced Object Handling
- Stream object uploads and downloads.
- Support HTTP Range requests for partial downloads.
- Use temporary files during uploads.
- Support atomic file replacement where available.
- Attempt to restore overwritten files if metadata updates fail.
- Validate bucket names and object keys.
- Return S3-style XML responses and error messages.

### Multi-User Isolation
- Associate each bucket with a numeric Repo2Cloud user ID.
- Prevent users from listing or accessing another user's buckets through the owner-aware API.
- Allow different users to use identical bucket names.
- Allow different users to store objects with identical keys independently.
- Store objects in separate bucket-ID-based filesystem directories.

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 21 | Backend programming language |
| Spring Boot | Application framework |
| Spring Web | REST API implementation |
| Spring Data JPA | Database access |
| Hibernate | ORM and entity management |
| MySQL 8+ | Bucket and object metadata |
| Maven | Dependency management and build |
| Local Filesystem | Object data storage |
| XML | S3-style API responses |

---

## Architecture

The storage server runs as a separate service from the main Repo2Cloud backend.

```text
                 +---------------------------+
                 |        Repo2Cloud         |
                 |      Main Backend         |
                 +-------------+-------------+
                               |
                               | Trusted service-to-service
                               | communication
                               v
                 +---------------------------+
                 |      S3 Storage Server    |
                 |       Spring Boot         |
                 |         Port 9000         |
                 +-------------+-------------+
                               |
                     +---------+---------+
                     |                   |
                     v                   v
             +---------------+   +----------------+
             |     MySQL     |   | Local Filesystem|
             |   Metadata    |   |  Object Data    |
             +---------------+   +----------------+
```

### MySQL stores
- Bucket IDs and names.
- Bucket ownership.
- Bucket creation timestamps.
- Object keys and filenames.
- Object sizes and content types.
- ETags.
- Object storage paths.
- Object creation timestamps.

### Local filesystem stores
- Actual uploaded object contents.
- Files organized by bucket ID and object key.

This separation allows MySQL to manage searchable metadata while the filesystem stores the actual bytes.

---

## Requirements

Before running the server, install:

- Java 21 or compatible configured Java runtime.
- MySQL 8 or later.
- Git.
- Maven is optional because the Maven Wrapper is included.

Verify Java:

```bash
java -version
```

Verify Git:

```bash
git --version
```

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/vishwajeetkapurkar-crypto/repo2cloud-s3-server.git
```

Navigate to the project directory:

```bash
cd repo2cloud-s3-server
```

### 2. Create the database

Open MySQL and execute:

```sql
CREATE DATABASE repo2cloud_s3;
```

The application connects to this database for bucket and object metadata.

### 3. Configure the application

The repository includes:

```text
src/main/resources/application-example.properties
```

Create your local configuration file by copying the example.

**Windows PowerShell:**

```powershell
Copy-Item src/main/resources/application-example.properties src/main/resources/application.properties
```

**Linux/macOS:**

```bash
cp src/main/resources/application-example.properties src/main/resources/application.properties
```

Configure the database URL and credentials in your local `application.properties`.

The database configuration uses these environment variable names:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/repo2cloud_s3
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD}
```

Set your MySQL credentials before starting the application.

**Windows PowerShell:**

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-local-mysql-password"
```

Replace the example password with your actual local MySQL password.

**Linux/macOS:**

```bash
export DB_USERNAME=root
export DB_PASSWORD='your-local-mysql-password'
```

Do not commit real passwords, tokens, or other secrets to GitHub.

The local `application.properties` file is excluded from version control.

### 4. Start the server

**Windows PowerShell:**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux/macOS:**

```bash
./mvnw spring-boot:run
```

The server starts at:

```text
http://localhost:9000
```

### 5. Build the project

**Windows:**

```powershell
.\mvnw.cmd clean package
```

**Linux/macOS:**

```bash
./mvnw clean package
```

The generated JAR file is placed in the `target/` directory.

### 6. Run tests

**Windows:**

```powershell
.\mvnw.cmd test
```

**Linux/macOS:**

```bash
./mvnw test
```

---

## User Ownership and Isolation

Every bucket belongs to a Repo2Cloud user identified by a numeric user ID.

The storage service stores this ID in the `owner_id` column of the `buckets` table.

The storage service does not import the main Repo2Cloud `User` entity or query the main application's user database.

Instead, the main backend is responsible for authenticating the user and propagating the authenticated user's ID through trusted service-to-service communication.

### Required Request Header

Every bucket and object API request requires:

```http
X-Repo2Cloud-User-Id: 101
```

Here, `101` is an example user ID. Replace it with the actual authenticated user's ID.

The header is required by the current API implementation.

### Example

Suppose two users have these IDs:

```text
User 101
  └── my-project
      └── text.txt

User 202
  └── my-project
      └── text.txt
```

Both users can own a bucket named `my-project` and an object named `text.txt`.

Their buckets have different database IDs, and their objects are stored separately.

For example:

```text
s3-storage/
├── 12/
│   └── text.txt
└── 18/
    └── text.txt
```

The directory names above represent bucket IDs, not user IDs.

Deleting an object belonging to one user must not delete another user's object, even if both objects have the same bucket name and object key.

### Database Constraint

The bucket table uses a composite unique constraint:

```sql
UNIQUE (owner_id, name)
```

This allows the same bucket name for different users while preventing duplicate bucket names for the same user.

### Security Warning

The `X-Repo2Cloud-User-Id` header is an identity-propagation mechanism, not authentication.

A client must not be allowed to select another user's ID.

Before production deployment:

- The main backend must derive the ID from the authenticated user's security principal.
- The main backend must remove or overwrite any client-supplied user-ID header.
- The storage server must accept identity information only through trusted server-to-server communication.
- Restrict access to port 9000 using appropriate network controls.
- Add service authentication, such as mutual TLS or a securely validated signed internal token.

Do not expose the current header-based identity mechanism directly to untrusted clients.

---

## API Reference

All endpoints are served from:

```text
http://localhost:9000
```

All requests require:

```http
X-Repo2Cloud-User-Id: <USER_ID>
```

The examples below use `101` as the user ID.

### 1. Bucket Operations

| Method | Endpoint | Description |
|---|---|---|
| PUT | `/{bucket}` | Create a bucket |
| GET | `/` | List the current user's buckets |
| HEAD | `/{bucket}` | Check whether a bucket exists |
| DELETE | `/{bucket}` | Delete an empty bucket |

#### Create a bucket

```http
PUT /my-project
X-Repo2Cloud-User-Id: 101
```

Creates the `my-project` bucket for user `101`, provided that the user does not already own a bucket with that name.

#### List buckets

```http
GET /
X-Repo2Cloud-User-Id: 101
```

Returns the buckets owned by user `101`.

#### Check a bucket

```http
HEAD /my-project
X-Repo2Cloud-User-Id: 101
```

Checks whether the specified bucket exists for the current user.

#### Delete a bucket

```http
DELETE /my-project
X-Repo2Cloud-User-Id: 101
```

A bucket must be empty before it can be deleted.

---

### 2. Object Operations

| Method | Endpoint | Description |
|---|---|---|
| PUT | `/{bucket}/{objectKey}` | Upload or overwrite an object |
| GET | `/{bucket}/{objectKey}` | Download an object |
| HEAD | `/{bucket}/{objectKey}` | Check whether an object exists |
| DELETE | `/{bucket}/{objectKey}` | Delete an object |

#### Upload an object

```http
PUT /my-project/test.txt
X-Repo2Cloud-User-Id: 101
Content-Type: text/plain
```

Request body:

```text
Hello from Repo2Cloud
```

The server stores the object contents on the filesystem and its metadata in MySQL.

#### Download an object

```http
GET /my-project/test.txt
X-Repo2Cloud-User-Id: 101
```

Returns the stored object contents.

#### Check an object

```http
HEAD /my-project/test.txt
X-Repo2Cloud-User-Id: 101
```

Checks whether the object exists in the specified bucket for the current user.

#### Delete an object

```http
DELETE /my-project/test.txt
X-Repo2Cloud-User-Id: 101
```

Deletes the specified user's object.

Deleting an object belonging to one user must not affect an object with the same key belonging to another user.

---

### 3. Object Listing

List objects in a bucket:

```http
GET /my-project
X-Repo2Cloud-User-Id: 101
```

#### Prefix filtering

List objects whose keys start with `projects/`:

```http
GET /my-project?prefix=projects/
X-Repo2Cloud-User-Id: 101
```

#### Delimiter support

```http
GET /my-project?delimiter=/
X-Repo2Cloud-User-Id: 101
```

The delimiter can be used to group nested object keys into common prefixes.

#### Pagination

Request up to two objects:

```http
GET /my-project?max-keys=2
X-Repo2Cloud-User-Id: 101
```

If additional objects are available, the response provides a continuation token.

Use the returned token in the next request:

```http
GET /my-project?max-keys=2&continuation-token=<TOKEN>
X-Repo2Cloud-User-Id: 101
```

The actual continuation token must come from the previous response.

---

### 4. Range Requests

The server supports HTTP Range requests for partial object downloads.

Example:

```http
GET /my-project/video.mp4
X-Repo2Cloud-User-Id: 101
Range: bytes=0-1023
```

This requests the first 1,024 bytes of the object.

Valid range requests return the requested portion of the file. Invalid ranges produce an S3-style `InvalidRange` error.

---

## Error Handling

The server returns S3-style error responses for supported error conditions.

Examples include:

| Error Code | Meaning |
|---|---|
| `NoSuchBucket` | The requested bucket does not exist or is unavailable to the current user |
| `NoSuchKey` | The requested object does not exist or is unavailable to the current user |
| `BucketAlreadyExists` | The current user already owns a bucket with that name |
| `BucketNotEmpty` | The bucket cannot be deleted because it contains objects |
| `InvalidBucketName` | The bucket name is invalid |
| `InvalidObjectKey` | The object key is invalid |
| `InvalidArgument` | A request argument is invalid |
| `InvalidRange` | The requested byte range is invalid |

Example response:

```xml
<Error>
    <Code>NoSuchKey</Code>
    <Message>The specified key does not exist.</Message>
    <Key>text.txt</Key>
</Error>
```

The exact HTTP status and error response depend on the failure condition.

---

## Storage Model

The server separates bucket/object metadata from the actual object contents.

### Example Filesystem Structure

```text
s3-storage/
├── 12/
│   ├── test.txt
│   └── projects/
│       └── build.log
└── 18/
    └── test.txt
```

Here, `12` and `18` are bucket IDs from MySQL.

Two users can have buckets with the same name because their buckets have different IDs.

### Object Metadata

The `StoredObject` entity stores information such as:

- Object ID.
- Bucket reference.
- Object key.
- Original filename.
- Content type.
- File size.
- Filesystem storage path.
- ETag.
- Creation timestamp.

The `Bucket` entity stores:

- Bucket ID.
- Bucket name.
- Numeric owner ID.
- Creation timestamp.

### Upload Flow

The upload process uses a temporary file before replacing the final object.

```text
Client Request
      |
      v
Validate User and Bucket Ownership
      |
      v
Write Temporary File
      |
      v
Move to Final Storage Location
      |
      v
Update Object Metadata in MySQL
      |
      v
Return Response
```

The implementation also attempts to restore an overwritten object if the metadata update fails.

These mechanisms improve consistency between the filesystem and MySQL, although they do not provide a fully transactional filesystem/database operation.

---

## Configuration Reference

| Property | Purpose |
|---|---|
| `server.port` | HTTP server port; defaults to `9000` |
| `spring.datasource.url` | MySQL connection URL |
| `spring.datasource.username` | MySQL username |
| `spring.datasource.password` | MySQL password |
| `s3.storage.root` | Root directory for object data |
| `spring.jpa.hibernate.ddl-auto` | Hibernate schema-management behavior |

Default storage directory:

```text
./s3-storage
```

The directory is relative to the application's working directory unless configured otherwise.

For production use, configure the storage directory explicitly and ensure the application has the necessary filesystem permissions.

---

## Database Migration Notes

If you are setting up a new development database, create the database and start the application using the configured settings.

If you are migrating an existing database that used email-based bucket ownership, do not simply drop and recreate the bucket table.

Existing bucket IDs are referenced by stored objects. Recreating the table could break those relationships.

Before migrating an existing database:

1. Back up the database.
2. Stop the storage service.
3. Inspect existing bucket and object records.
4. Map each existing bucket to the correct numeric user ID.
5. Add the `owner_id` column and populate it.
6. Verify that every bucket has the correct owner.
7. Apply the composite unique constraint on `(owner_id, name)`.
8. Remove the old email-based ownership column and obsolete constraints only after verifying the migration.

Use the actual user IDs from the main Repo2Cloud system when mapping real users. Development IDs such as `101` and `202` are examples, not production identities.

The database migration must preserve existing bucket IDs and their object relationships.

---

## Current Limitations

This project implements a subset of S3-style object storage operations. It does not currently implement every AWS S3 feature.

The following capabilities are not currently implemented:

- AWS Signature Version 4 authentication.
- Presigned URLs.
- Object versioning.
- Bucket versioning.
- Access Control Lists (ACLs).
- Multipart uploads.
- Distributed storage.
- Cross-region or cross-node replication.
- Cloud-based storage backends.
- Complete AWS S3 API compatibility.

The current service uses a local filesystem rather than a distributed storage cluster.

Authentication is expected to be handled by the main Repo2Cloud backend and trusted service-to-service communication must be secured before production deployment.

---

## Repo2Cloud Integration

The storage server is designed to operate independently of the main Repo2Cloud Spring Boot application.

The main backend is responsible for authenticating users and determining their numeric user IDs.

The storage service uses the propagated user ID to enforce bucket and object ownership.

The storage service does not need direct access to the main application's user database.

### Integration Endpoint

Development endpoint:

```text
http://localhost:9000
```

Example request:

```http
PUT /my-project/source.zip
X-Repo2Cloud-User-Id: 101
Content-Type: application/zip
```

The example demonstrates the API shape. The main backend must supply the authenticated user's ID through a trusted internal mechanism.

Before production deployment, configure appropriate service authentication and network restrictions.

---

## Development and Testing

### Run the application

```powershell
.\mvnw.cmd spring-boot:run
```

### Run tests

```powershell
.\mvnw.cmd test
```

### Build the application

```powershell
.\mvnw.cmd clean package
```

The project should be tested against the configured MySQL database before changes are deployed or integrated.

Manual integration tests should cover:

- Bucket creation and listing.
- Bucket deletion rules.
- Object uploads and downloads.
- Object overwrites.
- Object deletion.
- Prefix and delimiter listing.
- Pagination.
- Range requests.
- Missing bucket and object errors.
- Isolation between different user IDs.
- Filesystem and database failure scenarios.

---

## Repository

GitHub:

https://github.com/vishwajeetkapurkar-crypto/repo2cloud-s3-server

Clone the project:

```bash
git clone https://github.com/vishwajeetkapurkar-crypto/repo2cloud-s3-server.git
```

---

## Project Status

The core storage operations have been implemented and manually tested.

Verified functionality includes:

- Bucket ownership using numeric user IDs.
- Bucket isolation between users.
- Duplicate bucket prevention per owner.
- Independent objects for users sharing the same bucket name and object key.
- Object upload and download.
- Object deletion isolation.
- MySQL metadata storage.
- Local filesystem object storage.

The project is ready for continued development and integration with Repo2Cloud, subject to the security, testing, and deployment considerations described above.

**Note:** A successful Maven build and manual API tests do not establish complete S3 compatibility or production readiness. Automated coverage, service authentication, operational monitoring, and failure-recovery testing should be expanded as the platform develops.
