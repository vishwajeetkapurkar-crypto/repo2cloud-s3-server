# Repo2Cloud S3-Compatible Object Storage Server

A lightweight, S3-compatible object storage server built with Java and Spring Boot. The project provides REST APIs for managing buckets and objects, stores object metadata in MySQL, and uses MinIO for physical object storage.

Repo2Cloud is designed as an object storage component for a cloud deployment platform, allowing applications to organize, upload, retrieve, list, and delete files through HTTP APIs.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [API Reference](#api-reference)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [Security Considerations](#security-considerations)
- [Limitations](#limitations)
- [Future Enhancements](#future-enhancements)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

Repo2Cloud provides an S3-compatible object storage layer for a cloud deployment platform.

In object storage, files are stored as objects inside buckets. Each object has a key that identifies it within its bucket.

For example:

```text
Bucket: project-files

Objects:
├── documents/report.pdf
├── images/logo.png
└── backups/database.sql
```

The server separates object data from object metadata:

- **MinIO:** Stores the actual file contents.
- **MySQL:** Stores bucket information, object metadata, ownership information, and other database-managed attributes.
- **Spring Boot:** Exposes REST APIs and coordinates storage operations.

This architecture separates the API and metadata management from the underlying object storage implementation.

## Features

### Bucket Management

- Create buckets.
- List buckets belonging to a user.
- Retrieve bucket information.
- Delete empty buckets.
- Prevent deletion of non-empty buckets.
- Maintain ownership information for buckets.

### Object Management

- Upload objects to buckets.
- Download stored objects.
- Retrieve object metadata.
- Delete objects.
- List objects within a bucket.
- Support object keys containing directory-like prefixes.
- Support prefix and delimiter-based listing.
- Support pagination when listing objects.
- Provide ETag metadata.
- Support HTTP range requests for partial object retrieval.

### Storage and Metadata

- Store object contents using MinIO.
- Store metadata using MySQL.
- Separate storage operations from business logic.
- Maintain bucket and object ownership information.

### Development and Testing

- Environment-based configuration.
- Docker Compose configuration for running MinIO.
- Unit tests for object service logic.
- Integration tests for storage-related functionality.
- Separate test database configuration.

---

## Architecture

```text
                  Client Application
                          |
                          | HTTP Requests
                          v
                +--------------------+
                |   Spring Boot API  |
                +--------------------+
                          |
                          v
                +--------------------+
                |  Service Layer     |
                |                    |
                | BucketService      |
                | ObjectService      |
                +--------------------+
                     /          \
                    /            \
                   v              v
          +----------------+  +----------------+
          |     MySQL      |  |     MinIO      |
          |                |  |                |
          | Bucket data    |  | Actual objects |
          | Object metadata|  | File contents  |
          | Ownership data |  |                |
          +----------------+  +----------------+
```

### Request Flow

For an object upload:

1. The client sends an HTTP request containing the object and its bucket/key information.
2. The Spring Boot application validates the request and applies service-level rules.
3. The service layer coordinates object storage and metadata persistence.
4. MinIO stores the file contents.
5. MySQL stores the corresponding metadata.
6. The API returns an appropriate HTTP response.

For a download, the application retrieves the object using its bucket and key, then returns the object contents to the client.

The exact order of operations and failure-handling behavior depends on the relevant service implementation.

---

## Technology Stack

| Technology | Purpose |
|---|---|
| Java | Primary programming language |
| Spring Boot | REST API and application framework |
| Spring Data JPA | Database persistence |
| MySQL | Bucket and object metadata |
| MinIO | S3-compatible object storage backend |
| Maven | Dependency management and build |
| JUnit | Automated testing |
| Docker Compose | MinIO development environment |

---

## Prerequisites

Install the following software before running the project:

- Java Development Kit (JDK) compatible with the version configured in `pom.xml`.
- MySQL Server.
- Docker Desktop or Docker Engine with Docker Compose support.
- Git.
- A terminal such as PowerShell, Command Prompt, or Bash.

The repository includes a Maven wrapper, so a separate Maven installation should not be necessary.

Verify your Java installation:

```powershell
java -version
```

Verify Docker:

```powershell
docker --version
docker compose version
```

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/vishwajeetkapurkar-crypto/repo2cloud-s3-server.git
```

Navigate to the project directory:

```bash
cd repo2cloud-s3-server
```

### 2. Configure Environment Variables

Copy the example environment file:

**PowerShell:**

```powershell
Copy-Item .env.example .env
```

**Linux/macOS:**

```bash
cp .env.example .env
```

Open `.env` and replace the placeholder values with your local configuration.

Example:

```dotenv
MYSQL_DATABASE=repo2cloud_s3
MYSQL_USERNAME=your_mysql_username
MYSQL_PASSWORD=your_mysql_password

MINIO_ENDPOINT=http://localhost:9000
MINIO_ROOT_USER=your_minio_username
MINIO_ROOT_PASSWORD=your_strong_minio_password

TEST_DB_USERNAME=your_mysql_username
TEST_DB_PASSWORD=your_mysql_password
```

These are example values, not production credentials.

**Important:** Never commit your actual `.env` file or real passwords to GitHub.

### 3. Create the MySQL Database

Connect to your local MySQL server and create the application database:

```sql
CREATE DATABASE repo2cloud_s3;
```

Create a separate database for integration tests:

```sql
CREATE DATABASE repo2cloud_s3_test;
```

Configure the corresponding usernames and passwords in `.env` according to your MySQL setup.

The application must have permission to access its database. The test database should be configured separately to avoid affecting development data.

### 4. Start MinIO

Start the MinIO service using Docker Compose:

```bash
docker compose up -d
```

Check the container status:

```bash
docker compose ps
```

With the default port mappings in `compose.yaml`:

- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`

Use the MinIO credentials configured in `.env` to access the console.

To view the container logs:

```bash
docker compose logs -f minio
```

To stop MinIO:

```bash
docker compose down
```

Object data is stored in the Docker volume configured by `compose.yaml`.

Stopping the service does not remove the volume. Avoid deleting persistent volumes unless you intentionally want to remove their stored data.

### 5. Configure the Spring Boot Application

Review the following configuration files:

```text
src/main/resources/application-example.properties
src/test/resources/application-test.properties
```

The example application configuration documents the settings needed for the application, while the test configuration defines settings used by the automated tests.

Configure the database connection, MinIO endpoint, and credentials through the environment variables expected by the application.

If your local setup requires an `application.properties` file, create it locally using the example configuration as a reference. Do not commit files containing real credentials.

### 6. Run the Application

From the project root, run:

**Windows PowerShell:**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux/macOS:**

```bash
./mvnw spring-boot:run
```

Wait for Spring Boot to finish starting.

The default MinIO endpoint is `http://localhost:9000`. Ensure the application configuration points to the correct endpoint and that MySQL and MinIO are running.

---

## API Reference

The server exposes HTTP APIs for bucket and object operations.

The exact endpoint paths, request parameters, authentication requirements, and response formats should be verified against the current controller implementation.

### Base URL

```text
http://localhost:8080
```

The actual port depends on the Spring Boot configuration.

### Bucket Operations

The following table describes the supported operation categories.

| Operation | Description |
|---|---|
| Create bucket | Creates a bucket for the requesting user |
| List buckets | Lists buckets accessible to the requesting user |
| Get bucket | Retrieves bucket information |
| Delete bucket | Deletes an empty bucket |

### Object Operations

| Operation | Description |
|---|---|
| Upload object | Stores an object in a bucket |
| Download object | Retrieves object contents |
| Get object metadata | Retrieves metadata for an object |
| Delete object | Removes an object |
| List objects | Lists objects in a bucket |
| Prefix listing | Filters object keys by prefix |
| Delimiter listing | Groups keys into common prefixes |
| Paginated listing | Retrieves objects in multiple pages |
| Range retrieval | Retrieves a requested byte range where supported |

### Example: Uploading an Object

A typical object upload contains:

- A bucket identifier or name.
- An object key.
- The file contents.

Conceptually, an object might be stored as:

```text
Bucket: project-files
Key: documents/report.pdf
```

The exact HTTP method, URL, and request format depend on the implemented controller mappings.

### Example: Listing Objects

Object listing can support filtering by prefix.

For example, a request to list objects under the prefix:

```text
documents/
```

might return:

```text
documents/report.pdf
documents/notes.txt
documents/requirements.docx
```

Delimiter-based listing can group keys under common prefixes, producing directory-like results without requiring actual directories in object storage.

### Example: HTTP Range Requests

For objects that support range retrieval, a client can request a portion of the file using an HTTP header such as:

```http
Range: bytes=0-99
```

A successful partial response normally uses HTTP status `206 Partial Content`.

Check the current implementation for the precise behavior and supported range formats.

### Testing the API

Use an API client such as Postman or curl to test the endpoints.

Before testing, verify that:

1. MySQL is running.
2. MinIO is running.
3. The Spring Boot application has started.
4. The request uses the correct endpoint and required headers.
5. The requesting user has the appropriate permissions.

Refer to the controller classes in the source code for the exact routes and request formats.

---

## Testing

The project includes unit tests and integration tests.

### Run All Tests

**Windows PowerShell:**

```powershell
.\mvnw.cmd clean test
```

**Linux/macOS:**

```bash
./mvnw clean test
```

### Run a Specific Test Class

For the object service unit tests:

```powershell
.\mvnw.cmd -Dtest=ObjectServiceTest test
```

Run the integration test with:

```powershell
.\mvnw.cmd -Dtest=ObjectStorageIntegrationTest test
```

Integration tests require the external services and configuration expected by the test profile. Configure the test database and MinIO credentials before running them.

The integration test configuration is located at:

```text
src/test/resources/application-test.properties
```

**Warning:** The test profile may create and drop database tables. Always verify that the test database points to a dedicated test database, not a database containing important application data.

---

## Project Structure

```text
repo2cloud-s3-server/
│
├── src/
│   ├── main/
│   │   ├── java/com/repo2cloud/s3server/
│   │   │   ├── config/
│   │   │   │   └── MinioConfig.java
│   │   │   ├── service/
│   │   │   │   ├── BucketService.java
│   │   │   │   └── ObjectService.java
│   │   │   └── storage/
│   │   │       └── MinioObjectStorage.java
│   │   │
│   │   └── resources/
│   │       └── application-example.properties
│   │
│   └── test/
│       ├── java/com/repo2cloud/s3server/
│       │   ├── integration/
│       │   │   └── ObjectStorageIntegrationTest.java
│       │   └── service/
│       │       └── ObjectServiceTest.java
│       │
│       └── resources/
│           └── application-test.properties
│
├── .env.example
├── .gitignore
├── compose.yaml
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

This is a simplified view of the project. Other source files, such as controllers, entities, DTOs, and repositories, may also be present.

---

## Security Considerations

The current project is intended as a development and learning implementation.

Before exposing it to untrusted users or deploying it publicly, consider the following:

- **Authentication:** Verify user identity before allowing access to buckets and objects.
- **Authorization:** Enforce ownership and permissions on every relevant operation.
- **Trusted identity headers:** Do not trust user-identity headers supplied directly by arbitrary clients. An authentication layer or trusted gateway must validate identity and control these headers.
- **Credentials:** Keep database and MinIO credentials in environment variables or a suitable secrets manager.
- **Network security:** Avoid exposing the MinIO console or storage API publicly without appropriate access controls.
- **Transport security:** Use HTTPS for external traffic.
- **Input validation:** Validate bucket names, object keys, request sizes, and other user-controlled inputs.
- **Resource limits:** Apply appropriate limits to uploads and concurrent requests.
- **Error handling:** Avoid returning internal exception details or secrets to clients.
- **Database protection:** Use a dedicated application database account with only the permissions it requires.

Do not treat this project as production-ready solely because its APIs and tests work.

---

## Limitations

This project implements a subset of S3-style object storage functionality. It is not a complete replacement for Amazon S3.

Depending on the current implementation, it may not support features such as:

- AWS Signature Version 4 authentication.
- Multipart uploads.
- Presigned URLs.
- Object versioning.
- Bucket policies and access control lists.
- Server-side encryption configuration.
- Object lifecycle rules.
- Event notifications.
- Full compatibility with the AWS S3 API and SDK behavior.

S3 compatibility should be evaluated against the specific operations and client libraries required by the intended use case.

---

## Future Enhancements

Potential improvements include:

- Implementing AWS Signature Version 4 request authentication.
- Adding presigned URLs for temporary object access.
- Supporting multipart uploads.
- Adding object versioning.
- Improving authorization and bucket policies.
- Adding storage quotas and upload limits.
- Implementing robust cleanup and recovery for failed storage/database operations.
- Adding structured logging, metrics, and health checks.
- Expanding integration tests for edge cases.
- Testing compatibility with AWS S3 SDKs and standard S3 clients.
- Preparing the application for containerized deployment.

These are potential future improvements, not claims about existing functionality.

---

## Contributing

Contributions and suggestions are welcome.

To propose a change:

1. Fork the repository.
2. Create a feature branch.
3. Implement the change.
4. Run the automated tests.
5. Commit your changes with a descriptive message.
6. Open a pull request.

Please avoid committing credentials, local configuration files, generated build output, or local storage data.

---

## License

A license has not been specified in this README.

Before making the project available for reuse, add an appropriate license file and update this section.

---

## Author

**Vishwajeet Kapurkar**

GitHub: [vishwajeetkapurkar-crypto](https://github.com/vishwajeetkapurkar-crypto)

Repository: [Repo2Cloud S3 Server](https://github.com/vishwajeetkapurkar-crypto/repo2cloud-s3-server)
