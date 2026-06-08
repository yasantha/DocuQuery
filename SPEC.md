# DocuQuery — Technical Specification

**Version:** 1.0  
**Status:** Draft  
**Author:** Yasantha Hettiarachchi  
**Created:** 2026-06-05  
**Stack:** Java 21 · Spring Boot 3.2 · PostgreSQL + pgvector · OpenAI Embeddings · Claude API

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Goals & Non-Goals](#2-goals--non-goals)
3. [System Architecture](#3-system-architecture)
4. [Data Models](#4-data-models)
5. [API Specification](#5-api-specification)
6. [Pipeline Specifications](#6-pipeline-specifications)
7. [Configuration](#7-configuration)
8. [Error Handling](#8-error-handling)
9. [Security](#9-security)
10. [Testing Strategy](#10-testing-strategy)
11. [Acceptance Criteria](#11-acceptance-criteria)
12. [Out of Scope (Phase 1)](#12-out-of-scope-phase-1)

---

## 1. Project Overview

DocuQuery is a **RAG (Retrieval-Augmented Generation) backend API** that enables natural language querying over uploaded PDF documents.

Users upload a PDF. The system extracts the text, splits it into chunks, converts each chunk into a vector embedding, and stores it in PostgreSQL with pgvector. When a user asks a question, the system finds the most semantically relevant chunks and passes them to Claude to generate a cited, grounded answer.

**Primary use cases for portfolio demo:**
- Contract review Q&A ("What are the payment terms?")
- Financial report analysis ("What was Q3 revenue?")
- Clinical document querying ("What medications are listed?")

---

## 2. Goals & Non-Goals

### Goals
- Accept PDF uploads and ingest them into a queryable vector store
- Answer natural language questions grounded strictly in document content
- Return structured JSON responses with source chunk references
- Be fully containerised and runnable with `docker-compose up`
- Have comprehensive JUnit 5 + Testcontainers test coverage
- Expose Swagger/OpenAPI documentation at `/swagger-ui.html`

### Non-Goals (Phase 1)
- User authentication / multi-tenancy
- Support for non-PDF file types (Word, Excel, images)
- Real-time streaming responses
- Fine-tuning or model training
- Frontend UI (API only)
- Production deployment / cloud hosting

---

## 3. System Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────┐
│                    INGESTION PIPELINE                    │
│                                                          │
│  PDF Upload → Extract Text → Split Chunks →             │
│  Embed (OpenAI) → Store in pgvector                     │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                      QUERY PIPELINE                      │
│                                                          │
│  Question → Embed (OpenAI) → Vector Search (pgvector) → │
│  Build Context → Generate Answer (Claude) → Response    │
└─────────────────────────────────────────────────────────┘
```

### Component Diagram

```
Client (Postman / curl)
        │
        ▼
┌───────────────────┐
│  Spring Boot API  │
│  (Port 8080)      │
│                   │
│  DocumentController──────► DocumentIngestionService
│  QueryController  │              │
└───────────────────┘         ┌───▼────────────────┐
                               │  EmbeddingService  │──► OpenAI API
                               │  (OpenAI calls)    │    /v1/embeddings
                               └───────────────────-┘
                               ┌────────────────────┐
                               │  VectorSearchService│──► PostgreSQL
                               │  (pgvector queries) │    + pgvector
                               └────────────────────┘
                               ┌────────────────────┐
                               │  QueryService       │──► Claude API
                               │  (RAG orchestrator) │    /v1/messages
                               └────────────────────┘
```

### Technology Decisions

| Component | Choice | Reason |
|-----------|--------|--------|
| Embedding model | OpenAI text-embedding-3-small | 1536 dims, low cost (~$0.02/1M tokens), high quality |
| Generation model | Claude claude-sonnet-4-20250514 | Best structured output reliability, low hallucination |
| Vector store | PostgreSQL + pgvector | No extra service, familiar to Java devs, production-ready |
| PDF extraction | Apache PDFBox 3.x | Best Java PDF library, handles complex layouts |
| HTTP client | Spring WebFlux WebClient | Non-blocking, reactive, built into Spring |
| DB migrations | Flyway | Version-controlled schema, standard in Spring Boot |
| Testing | JUnit 5 + Testcontainers | Real DB in tests, no mocking of SQL layer |

---

## 4. Data Models

### 4.1 Database Schema

#### `documents` table
```sql
CREATE TABLE documents (
    id           BIGSERIAL PRIMARY KEY,
    filename     VARCHAR(255)  NOT NULL,
    title        VARCHAR(500),
    file_size_kb INT,
    total_chunks INT           DEFAULT 0,
    status       VARCHAR(50)   DEFAULT 'processing',
    -- status values: processing | ready | failed
    uploaded_at  TIMESTAMP     DEFAULT NOW()
);
```

#### `document_chunks` table
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunks (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT        REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index  INT           NOT NULL,
    content      TEXT          NOT NULL,
    char_count   INT,
    embedding    vector(1536),           -- OpenAI text-embedding-3-small
    created_at   TIMESTAMP     DEFAULT NOW()
);

-- IVFFlat index for fast approximate nearest neighbour search
-- lists = 100 is recommended for up to 1M vectors
CREATE INDEX idx_chunks_embedding
    ON document_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX idx_chunks_document_id ON document_chunks(document_id);
```

#### `query_history` table
```sql
CREATE TABLE query_history (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT        REFERENCES documents(id) ON DELETE CASCADE,
    question     TEXT          NOT NULL,
    answer       TEXT          NOT NULL,
    chunks_used  INT,
    source_chunk_indexes  INT[],        -- which chunks answered this
    queried_at   TIMESTAMP     DEFAULT NOW()
);
```

### 4.2 Java Entity Classes

#### Document.java
```java
@Entity @Table(name = "documents")
@Data @NoArgsConstructor
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String filename;
    private String title;
    private Integer fileSizeKb;
    private Integer totalChunks = 0;
    private String status = "processing";
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
```

#### DocumentChunk.java
```java
@Entity @Table(name = "document_chunks")
@Data @NoArgsConstructor
public class DocumentChunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Integer charCount;

    @Column(columnDefinition = "vector(1536)")
    private float[] embedding;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### 4.3 Request / Response DTOs

#### Upload Response
```json
{
  "id": 1,
  "filename": "contract.pdf",
  "totalChunks": 42,
  "status": "ready",
  "uploadedAt": "2026-06-05T10:30:00"
}
```

#### Query Request
```json
{
  "question": "What are the payment terms?"
}
```

#### Query Response
```json
{
  "question": "What are the payment terms?",
  "answer": "According to Excerpt 3, payment is due within 30 days of invoice date. Late payments incur a 1.5% monthly interest charge as stated in Excerpt 7.",
  "sourceChunks": [3, 7],
  "chunksSearched": 5
}
```

#### Error Response
```json
{
  "error": "DOCUMENT_NOT_FOUND",
  "message": "Document with id 99 does not exist",
  "timestamp": "2026-06-05T10:30:00"
}
```

---

## 5. API Specification

### Base URL
`http://localhost:8080/api`

### Endpoints

---

#### `POST /documents`
Upload and ingest a PDF document.

**Request:**
- Content-Type: `multipart/form-data`
- Field: `file` (PDF, max 50MB)

**Validation:**
- File must have `.pdf` extension
- File must not be empty
- File size must not exceed 50MB

**Response: 200 OK**
```json
{
  "id": 1,
  "filename": "contract.pdf",
  "totalChunks": 42,
  "status": "ready",
  "uploadedAt": "2026-06-05T10:30:00"
}
```

**Error responses:**
- `400 BAD_REQUEST` — file is not a PDF or is empty
- `413 PAYLOAD_TOO_LARGE` — file exceeds 50MB
- `500 INTERNAL_SERVER_ERROR` — PDF extraction or embedding failed

**Side effects:**
1. Text extracted from PDF
2. Text split into chunks (size: 500 chars, overlap: 50 chars)
3. All chunks embedded via OpenAI API (batch call)
4. Chunks saved to `document_chunks` table with embeddings
5. Document status updated to `ready`

---

#### `GET /documents`
List all uploaded documents.

**Response: 200 OK**
```json
[
  {
    "id": 1,
    "filename": "contract.pdf",
    "totalChunks": 42,
    "status": "ready",
    "uploadedAt": "2026-06-05T10:30:00"
  }
]
```

---

#### `GET /documents/{id}`
Get a single document by ID.

**Response: 200 OK** — document object  
**Error: 404 NOT_FOUND** — document does not exist

---

#### `DELETE /documents/{id}`
Delete a document and all its chunks.

**Response: 204 NO_CONTENT**  
**Error: 404 NOT_FOUND**  
**Side effect:** All `document_chunks` rows cascade-deleted via FK constraint

---

#### `POST /documents/{documentId}/query`
Ask a natural language question about a document.

**Request:**
```json
{ "question": "What are the payment terms?" }
```

**Validation:**
- `question` must not be blank
- `question` must not exceed 1000 characters
- Document must exist and have status `ready`

**Response: 200 OK**
```json
{
  "question": "What are the payment terms?",
  "answer": "According to Excerpt 3, payment is due within 30 days...",
  "sourceChunks": [3, 7],
  "chunksSearched": 5
}
```

**Error responses:**
- `400 BAD_REQUEST` — question is blank or too long
- `404 NOT_FOUND` — document does not exist
- `409 CONFLICT` — document status is not `ready` (still processing)
- `502 BAD_GATEWAY` — OpenAI or Claude API call failed

**Processing steps:**
1. Embed the question via OpenAI API
2. Run cosine similarity search against document's chunks
3. Retrieve top-5 chunks by similarity score
4. Build context string from retrieved chunks
5. Call Claude with system prompt + context + question
6. Return Claude's response with source chunk indexes
7. Save to `query_history`

---

#### `GET /documents/{documentId}/query/history`
Retrieve query history for a document.

**Response: 200 OK**
```json
[
  {
    "id": 1,
    "question": "What are the payment terms?",
    "answer": "According to Excerpt 3...",
    "chunksUsed": 5,
    "queriedAt": "2026-06-05T10:35:00"
  }
]
```

---

## 6. Pipeline Specifications

### 6.1 Ingestion Pipeline

**Chunking strategy:**
- Chunk size: 500 characters (configurable)
- Overlap: 50 characters (configurable)
- Break at sentence boundary when possible:
  - Find last `.` before the chunk boundary
  - If it falls in the second half of the chunk, use it as the break point
  - Otherwise, break at the character limit

**Embedding:**
- Model: `text-embedding-3-small`
- Dimensions: 1536
- Strategy: batch all chunks in a single API call (not one call per chunk)
- Max batch size: 100 chunks per API call (OpenAI limit)
- For documents with more than 100 chunks: split into batches of 100

**Ingestion flow:**
```
1. Receive MultipartFile
2. Validate: PDF extension, non-empty, ≤ 50MB
3. Save Document record with status = "processing"
4. Extract text via PDFBox PDFTextStripper
5. Clean text: remove excessive whitespace, control characters
6. Split into chunks with overlap
7. Batch embed chunks (max 100 per OpenAI call)
8. Save DocumentChunk records with embeddings
9. Update Document.totalChunks and status = "ready"
10. Return DocumentResponse
```

**On failure:** Update Document.status = "failed", log the error, return 500

---

### 6.2 Query Pipeline

**Retrieval:**
- Embed question using same model as ingestion (`text-embedding-3-small`)
- Run pgvector cosine similarity search: `ORDER BY embedding <=> :queryVector`
- Filter by `document_id` — only search within the queried document
- Retrieve top-K chunks (default: 5, configurable)

**Context construction:**
```
--- Excerpt 1 ---
[chunk content]

--- Excerpt 2 ---
[chunk content]

... (up to 5 excerpts)
```

**Claude system prompt:**
```
You are a precise document analyst.
Answer questions based ONLY on the provided document excerpts.
If the answer cannot be found in the excerpts, respond with:
"The provided document excerpts do not contain information about [topic]."
Always reference which Excerpt number your answer comes from.
Be concise, factual, and do not add information beyond what the excerpts contain.
```

**Query flow:**
```
1. Validate question (not blank, ≤ 1000 chars)
2. Check document exists and status = "ready"
3. Embed question via OpenAI
4. Run vector similarity search (top-5 chunks)
5. Build context string from retrieved chunks
6. Call Claude claude-sonnet-4-20250514 with system prompt + context + question
7. Extract text from Claude response content[0].text
8. Save QueryHistory record
9. Return QueryResponse with answer + source chunk indexes
```

---

## 7. Configuration

### application.yml (full)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/docuquery
    username: docuquery
    password: secret
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

server:
  port: 8080

openai:
  api-key: ${OPENAI_API_KEY}
  embedding-model: text-embedding-3-small
  embedding-dimensions: 1536
  base-url: https://api.openai.com
  timeout-seconds: 30

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  model: claude-sonnet-4-20250514
  base-url: https://api.anthropic.com
  max-tokens: 1024
  timeout-seconds: 60

rag:
  chunk-size: 500
  chunk-overlap: 50
  top-k-results: 5
  max-batch-size: 100

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

### Environment Variables (required)
```bash
OPENAI_API_KEY=sk-...        # from platform.openai.com
ANTHROPIC_API_KEY=sk-ant-... # from console.anthropic.com
```

### docker-compose.yml
```yaml
version: '3.8'
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: docuquery
      POSTGRES_USER: docuquery
      POSTGRES_PASSWORD: secret
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/docuquery
      SPRING_DATASOURCE_USERNAME: docuquery
      SPRING_DATASOURCE_PASSWORD: secret
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
    depends_on:
      - postgres

volumes:
  pgdata:
```

---

## 8. Error Handling

### Error Code Catalogue

| Code | HTTP Status | When |
|------|-------------|------|
| `DOCUMENT_NOT_FOUND` | 404 | Document ID does not exist |
| `INVALID_FILE_TYPE` | 400 | Uploaded file is not a PDF |
| `FILE_TOO_LARGE` | 413 | File exceeds 50MB |
| `EMPTY_FILE` | 400 | File has no content |
| `DOCUMENT_NOT_READY` | 409 | Document still processing or failed |
| `QUESTION_BLANK` | 400 | Question field is empty |
| `QUESTION_TOO_LONG` | 400 | Question exceeds 1000 characters |
| `PDF_EXTRACTION_FAILED` | 500 | PDFBox could not extract text |
| `EMBEDDING_API_ERROR` | 502 | OpenAI API returned error |
| `GENERATION_API_ERROR` | 502 | Claude API returned error |

### Global Exception Handler
All errors return consistent JSON:
```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "timestamp": "2026-06-05T10:30:00"
}
```

---

## 9. Security

### Phase 1 (MVP — no auth)
- No authentication required — this is a private portfolio demo
- API keys loaded from environment variables only — never in code or config files
- `.env` file added to `.gitignore` from day one
- No sensitive data in logs (mask API keys if they appear)

### Phase 2 additions (out of scope)
- JWT-based authentication
- Per-user document isolation (row-level security)
- Rate limiting per user

---

## 10. Testing Strategy

### Test Layers

#### Unit Tests (no DB, no HTTP)
- `TextChunkingServiceTest` — chunking logic, overlap, boundary detection
- `ContextBuilderTest` — context string construction from chunks
- `ErrorHandlingTest` — exception mapper produces correct HTTP responses

#### Integration Tests (Testcontainers — real pgvector DB)
- `DocumentIngestionServiceIT` — full ingestion with mocked embedding API
- `VectorSearchServiceIT` — similarity search returns expected chunks
- `QueryServiceIT` — full query pipeline with mocked OpenAI + Claude

#### API Tests (MockMvc or RestAssured)
- `DocumentControllerTest` — upload, list, get, delete endpoints
- `QueryControllerTest` — query and history endpoints
- Validation tests — missing fields, wrong file type, too-large files

### Mocking Strategy
- **OpenAI embedding calls** → Mocked with `@MockBean EmbeddingService`
  - Return deterministic `float[1536]` arrays in tests
- **Claude generation calls** → Mocked with `@MockBean QueryService` at controller level
  - Or mock the WebClient response for service-level tests
- **Database** → Real PostgreSQL via `pgvector/pgvector:pg16` Testcontainers image

### Coverage Target
- Minimum 80% line coverage on service layer
- All controller endpoints covered by at least one happy-path test
- All error codes covered by at least one negative test

---

## 11. Acceptance Criteria

The project is complete when all of the following pass:

### Functional
- [ ] A PDF can be uploaded via `POST /api/documents` and returns `status: ready`
- [ ] A question about content IN the document returns a cited answer referencing specific excerpts
- [ ] A question about content NOT in the document returns the standard "not found in excerpts" response
- [ ] Deleting a document removes all associated chunks from the database
- [ ] Query history is persisted and retrievable per document
- [ ] Swagger UI is accessible at `http://localhost:8080/swagger-ui.html` with all endpoints documented

### Non-Functional
- [ ] `docker-compose up` starts the full stack with no manual steps
- [ ] All JUnit tests pass with `./mvnw test`
- [ ] Testcontainers tests run against real pgvector DB (no in-memory DB)
- [ ] API keys are loaded from environment variables only (not hardcoded)
- [ ] `.env` is in `.gitignore`

### Portfolio Demo
- [ ] Screen recording demonstrates: upload → question with answer → question without answer → Swagger UI
- [ ] GitHub README includes: project description, architecture diagram, how to run, example curl commands
- [ ] Portfolio description matches the template in Section 12

---

## 12. Out of Scope (Phase 1)

The following are explicitly deferred to Phase 2:

- **Authentication** — JWT, user accounts, per-user document isolation
- **Non-PDF support** — Word (.docx), Excel, images, plain text
- **Streaming responses** — Server-Sent Events for real-time answer streaming
- **Semantic chunking** — splitting at paragraph/section boundaries rather than character count
- **Re-ranking** — cross-encoder re-ranking of retrieved chunks before generation
- **Multiple documents** — querying across more than one document at once
- **Frontend UI** — React or Next.js interface
- **Cloud deployment** — AWS, GCP, or Vercel hosting
- **Fine-tuning** — model fine-tuning on domain-specific data

---

*End of specification. Begin implementation when all sections above are reviewed and agreed.*
