# Code Complexity Visualizer — Backend (Spring Boot)

This project is a production-minded Spring Boot backend that analyzes Java projects (uploaded as ZIP or single .java files) and returns code metrics:

- Cyclomatic complexity (per-file, approximated per-method)
- Maintainability Index (approximate, uses simple Halstead volume)
- Duplication detection (method body hashing across files)

## Tech
- Java 17
- Spring Boot
- JavaParser (AST parsing)
- Maven

## Endpoints
- `POST /api/v1/analyze` — multipart form `file` (zip or .java)
- `GET /api/v1/health` — health check

## How to run
1. Build: `mvn clean package`
2. Run: `java -jar target/code-complexity-visualizer-0.0.1-SNAPSHOT.jar`
3. The server starts on port 8080.

## Docker
A Dockerfile is included — build and run in production.

## SonarQube
The service is intentionally modular. Integrating with SonarQube is possible via configuration; see TODOs in code.

