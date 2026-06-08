# DocuQuery

A **RAG (Retrieval-Augmented Generation) backend** that lets you ask natural-language
questions about uploaded PDF documents and get cited, grounded answers.

Upload a PDF → it's extracted, chunked, embedded, and stored in PostgreSQL + pgvector.
Ask a question → the most relevant chunks are retrieved by vector similarity and passed
to Claude, which answers using **only** the document content and cites its sources.

> Full technical specification: [SPEC.md](SPEC.md) · Development plan: [PLAN.md](PLAN.md)

---

## Architecture

```
INGESTION  PDF → extract (PDFBox) → chunk (500/50) → embed (OpenAI) → store (pgvector)
QUERY      question → embed (OpenAI) → vector search (pgvector) → build context → Claude → answer
```

```
Client ──► Spring Boot API (8080)
              ├─ DocumentController ─► DocumentIngestionService ─► PdfExtraction / Chunking
              │                                                  └─► EmbeddingService ─► OpenAI /v1/embeddings
              └─ QueryController ────► QueryService ─► VectorSearchService ─► PostgreSQL + pgvector
                                                     ├─► ContextBuilderService
                                                     └─► ClaudeClient ─────► Anthropic /v1/messages
```

### Tech stack

| Concern | Choice |
|---|---|
| Language / framework | Java 21 · Spring Boot 3.2 |
| Vector store | PostgreSQL 16 + pgvector (IVFFlat cosine index) |
| ORM / migrations | Spring Data JPA (hibernate-vector) · Flyway |
| Embeddings | OpenAI `text-embedding-3-small` (1536 dims) |
| Generation | Anthropic `claude-sonnet-4-6` |
| PDF extraction | Apache PDFBox 3.x |
| HTTP client | Spring WebFlux `WebClient` |
| API docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5 · Testcontainers (real pgvector) |

---

## Prerequisites

- **Docker + Docker Compose** (for the containerised run — no JDK needed)
- For local development/builds: **JDK 21** and Docker (Testcontainers needs a Docker daemon)
- API keys: an **OpenAI** key and an **Anthropic** key

---

## Quick start (Docker)

```bash
# 1. Provide your API keys
cp .env.example .env
#   then edit .env and set OPENAI_API_KEY and ANTHROPIC_API_KEY

# 2. Start the full stack (Postgres + app)
docker-compose up --build
```

The API is then available at `http://localhost:8080`, Swagger UI at
`http://localhost:8080/swagger-ui.html`.

`.env` is git-ignored — keys are read from the environment only and never committed.

---

## Run locally (without Docker for the app)

Start just the database in Docker, then run the app with Maven:

```bash
docker-compose up -d postgres
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run
```

---

## API

Base URL: `http://localhost:8080/api`

| Method | Path | Description |
|---|---|---|
| `POST` | `/documents` | Upload & ingest a PDF (`multipart/form-data`, field `file`, max 50MB) |
| `GET` | `/documents` | List all documents |
| `GET` | `/documents/{id}` | Get one document |
| `DELETE` | `/documents/{id}` | Delete a document and its chunks |
| `POST` | `/documents/{id}/query` | Ask a question about a document |
| `GET` | `/documents/{id}/query/history` | Query history for a document |

### Example: upload a PDF

```bash
curl -F "file=@contract.pdf" http://localhost:8080/api/documents
```

```json
{ "id": 1, "filename": "contract.pdf", "totalChunks": 42, "status": "ready",
  "uploadedAt": "2026-06-07T10:30:00" }
```

### Example: ask a question

```bash
curl -X POST http://localhost:8080/api/documents/1/query \
  -H "Content-Type: application/json" \
  -d '{"question": "What are the payment terms?"}'
```

```json
{
  "question": "What are the payment terms?",
  "answer": "According to Excerpt 3, payment is due within 30 days of the invoice date...",
  "sourceChunks": [3, 7],
  "chunksSearched": 5
}
```

### Example: list, fetch history, delete

```bash
curl http://localhost:8080/api/documents
curl http://localhost:8080/api/documents/1/query/history
curl -X DELETE http://localhost:8080/api/documents/1
```

### Error format

All errors return a consistent body with a stable `error` code (see [SPEC.md §8](SPEC.md)):

```json
{ "error": "DOCUMENT_NOT_FOUND", "message": "Document with id 99 does not exist",
  "timestamp": "2026-06-07T10:30:00" }
```

---

## Configuration

Settings live in [application.yml](src/main/resources/application.yml) and are environment-overridable.
Required environment variables:

| Variable | Purpose |
|---|---|
| `OPENAI_API_KEY` | OpenAI embeddings |
| `ANTHROPIC_API_KEY` | Claude generation |

RAG tunables (`rag.*`): `chunk-size` (500), `chunk-overlap` (50), `top-k-results` (5),
`max-batch-size` (100).

---

## Testing

```bash
./mvnw test
```

- **Unit tests** — chunking and context-building logic (no DB/HTTP).
- **Controller tests** (`@WebMvcTest`) — every endpoint and error-code mapping.
- **Integration tests** (`*IT`, Testcontainers) — full ingestion and query pipelines against a
  real `pgvector/pgvector:pg16` container, with the OpenAI and Claude calls mocked. Requires a
  running Docker daemon.

---

## Project structure

```
src/main/java/com/docuquery
├─ config/      configuration properties, WebClient + OpenAPI beans
├─ controller/  REST endpoints
├─ dto/         request/ and response/ payloads
├─ entity/      JPA entities
├─ exception/   error catalogue + global handler
├─ repository/  Spring Data repositories (incl. native pgvector search)
└─ service/
   ├─ client/     OpenAI + Claude API clients
   ├─ ingestion/  PDF extraction, chunking, ingestion orchestration
   └─ query/      vector search, context building, query orchestration
```

---

## Scope

Phase 1 (this project): single-document Q&A, no auth, PDF only. Out of scope (Phase 2):
authentication, non-PDF formats, streaming responses, re-ranking, cross-document queries.
See [SPEC.md §12](SPEC.md).
