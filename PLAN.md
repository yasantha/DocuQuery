# DocuQuery — Development Plan

**Companion to:** [SPEC.md](SPEC.md) v1.0
**Created:** 2026-06-06
**Stack:** Java 21 · Spring Boot 3.2 · PostgreSQL + pgvector · OpenAI Embeddings · Claude API

This plan turns the spec into an ordered, testable implementation. Decisions made up front:

- **Vector mapping:** use the **hibernate-vector** type so `float[]` maps to `vector(1536)` directly (no hand-rolled JDBC for the common path).
- **Claude model:** use **`claude-sonnet-4-6`** (current Sonnet 4.6), replacing the spec's stale `claude-sonnet-4-20250514`. Same `/v1/messages` API shape, better structured-output reliability.

---

## Spec issues to resolve during implementation

| # | Issue | Resolution |
|---|-------|-----------|
| 1 | Model id `claude-sonnet-4-20250514` is outdated | Use `claude-sonnet-4-6`. |
| 2 | `@Column(columnDefinition = "vector(1536)")` with `float[]` — Hibernate has no native `vector` type by default | Add **hibernate-vector** dependency; annotate embedding field with the vector type. Ensure it matches Flyway DDL exactly. |
| 3 | `ddl-auto: validate` + Flyway may fail validation on the `vector` column | Confirm the Hibernate vector mapping validates against the Flyway-created `vector(1536)` column. |
| 4 | IVFFlat index created before any rows exist (needs data to train `lists`) | Acceptable for a demo. Document in README; optionally rebuild index after first large ingest. |
| 5 | Ingestion is synchronous in the HTTP request, yet a `processing` status exists | Phase 1 keeps ingestion **synchronous** — endpoint returns `ready` directly. `status` still used for `failed`. Async deferred (out of scope). |
| 6 | Upload returns `200 OK` (not `201`) | Keep spec's `200` to match the contract and tests. |
| 7 | API keys must never be logged | Add log masking + load only from env vars (`.env` gitignored). |

---

## Project structure

```
docuquery/
├─ pom.xml
├─ Dockerfile
├─ docker-compose.yml
├─ .env.example
├─ .gitignore
├─ README.md
├─ SPEC.md
├─ PLAN.md
└─ src/
   ├─ main/
   │  ├─ java/com/docuquery/
   │  │  ├─ DocuQueryApplication.java
   │  │  ├─ controller/   (DocumentController, QueryController)
   │  │  ├─ service/      (PdfExtractionService, TextChunkingService,
   │  │  │                 EmbeddingService, VectorSearchService,
   │  │  │                 ContextBuilderService, ClaudeClient,
   │  │  │                 DocumentIngestionService, QueryService)
   │  │  ├─ repository/   (DocumentRepository, DocumentChunkRepository,
   │  │  │                 QueryHistoryRepository)
   │  │  ├─ entity/       (Document, DocumentChunk, QueryHistory)
   │  │  ├─ dto/          (DocumentResponse, QueryRequest, QueryResponse,
   │  │  │                 QueryHistoryResponse, ErrorResponse)
   │  │  ├─ config/       (OpenAiProperties, AnthropicProperties,
   │  │  │                 RagProperties, WebClientConfig, OpenApiConfig)
   │  │  └─ exception/    (GlobalExceptionHandler, ApiException + subtypes)
   │  └─ resources/
   │     ├─ application.yml
   │     └─ db/migration/V1__init.sql
   └─ test/java/com/docuquery/...
```

---

## Build order

```
P0 → P1 → P2 ─┐
              ├→ P4 → P5 → P6 → P7
        P3 ───┘
```
`EmbeddingService` is built in P2 and reused by P3. P2 (ingestion) and P3 (query) are otherwise independent and could be parallelised.

---

## Phase 0 — Project scaffold

**Goal:** compilable, runnable empty Spring Boot app.

- `git init` + `.gitignore` (include `.env`, `target/`, IDE files).
- `pom.xml` — Java 21, Spring Boot 3.2 parent. Dependencies:
  - `spring-boot-starter-web`, `spring-boot-starter-webflux` (WebClient),
    `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`
  - `postgresql`, `flyway-core`, `flyway-database-postgresql`
  - `hibernate-vector`
  - `pdfbox` 3.x
  - `lombok`
  - `springdoc-openapi-starter-webmvc-ui`
  - test: `spring-boot-starter-test`, `testcontainers` (`junit-jupiter`, `postgresql`), `rest-assured` (optional)
- `DocuQueryApplication.java`, minimal `application.yml`.

**Done when:** `./mvnw spring-boot:run` starts (DB config can point at compose Postgres).

---

## Phase 1 — Persistence layer

**Goal:** schema + entities + repositories verified against real pgvector.

- Flyway `V1__init.sql`: `CREATE EXTENSION vector`, `documents`, `document_chunks`, `query_history`, IVFFlat + FK indexes (exactly per SPEC §4.1).
- Entities `Document`, `DocumentChunk` (with hibernate-vector `float[] embedding`), `QueryHistory`.
- Repositories (Spring Data JPA).

**Tests**
- `RepositoryIT` — Testcontainers `pgvector/pgvector:pg16`, Flyway migrates, save/load round-trips including an embedding vector.

**Done when:** migrations apply and entities validate against the DB (`ddl-auto: validate` passes).

---

## Phase 2 — Ingestion pipeline

**Goal:** PDF → chunks → embeddings → persisted, status `ready`.

- `TextChunkingService` — 500 char / 50 overlap, sentence-boundary rule (last `.` in second half → break there).
- `PdfExtractionService` — PDFBox `PDFTextStripper`, whitespace/control-char cleaning.
- `EmbeddingService` — WebClient → OpenAI `/v1/embeddings`, batch ≤ 100, returns `List<float[]>`.
- `DocumentIngestionService` — the 10-step flow (SPEC §6.1); on failure set `status = failed`, log, rethrow → 500.

**Tests**
- `TextChunkingServiceTest` (unit) — sizes, overlap, boundary detection, edge cases (short text, no period).
- `DocumentIngestionServiceIT` — real DB, **mocked** `EmbeddingService` returning deterministic `float[1536]`.

**Done when:** uploading a sample PDF yields a `ready` document with the expected chunk count.

---

## Phase 3 — Query pipeline

**Goal:** question → retrieved chunks → cited Claude answer → persisted history.

- `VectorSearchService` — native query `... WHERE document_id = :id ORDER BY embedding <=> :vec LIMIT :k`, top-K (default 5).
- `ContextBuilderService` — `--- Excerpt N ---` formatting (SPEC §6.2).
- `ClaudeClient` — WebClient → Anthropic `/v1/messages`, model `claude-sonnet-4-6`, system prompt from SPEC §6.2, `max_tokens` from config; extract `content[0].text`.
- `QueryService` — orchestrates embed → search → context → Claude → save `QueryHistory` → `QueryResponse`.

**Tests**
- `ContextBuilderTest` (unit).
- `VectorSearchServiceIT` — seeded vectors, similarity returns expected order.
- `QueryServiceIT` — real DB, mocked OpenAI + Claude; asserts answer, `sourceChunks`, `chunksSearched`, and persisted history.

**Done when:** an in-document question returns a cited answer; an out-of-document question returns the standard "not found in excerpts" response.

---

## Phase 4 — REST layer

**Goal:** full API contract from SPEC §5 with validation and error handling.

- `DocumentController` — `POST /api/documents` (multipart, validate `.pdf`/non-empty/≤50MB), `GET /api/documents`, `GET /api/documents/{id}`, `DELETE /api/documents/{id}` (204, cascade).
- `QueryController` — `POST /api/documents/{id}/query` (blank/≤1000 validation, status `ready` check → 409), `GET /api/documents/{id}/query/history`.
- DTOs per SPEC §4.3.
- `GlobalExceptionHandler` mapping all 10 error codes (SPEC §8) to the `{error, message, timestamp}` shape.

**Tests**
- `DocumentControllerTest`, `QueryControllerTest` (MockMvc/RestAssured) — happy paths.
- Negative tests covering **every** error code (wrong type, empty, too large, not found, not ready, blank, too long, upstream 502s).

**Done when:** all endpoints + every error code have at least one passing test.

---

## Phase 5 — Config & docs

- Full `application.yml` (SPEC §7); `@ConfigurationProperties` classes for `openai`, `anthropic`, `rag`.
- API-key masking in logs; keys only from env.
- springdoc Swagger at `/swagger-ui.html`, `/api-docs`; annotate controllers/DTOs.

**Done when:** Swagger UI lists all endpoints with schemas.

---

## Phase 6 — Containerisation

- Multi-stage `Dockerfile` (build + slim runtime, JRE 21).
- `docker-compose.yml` (SPEC §7) — `pgvector/pgvector:pg16` + app, env wiring, `depends_on`.
- `.env.example` documenting `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`.

**Done when:** `docker-compose up` brings up the full stack with no manual steps.

---

## Phase 7 — Acceptance & portfolio

- Walk the SPEC §11 checklist (functional + non-functional).
- Coverage ≥ 80% on service layer (JaCoCo).
- `README.md` — description, architecture diagram, how to run, example `curl` commands.
- Demo recording: upload → in-doc question → out-of-doc question → Swagger UI.

**Done when:** every acceptance-criteria box is checked.

---

## Test inventory (maps to SPEC §10)

| Test | Type | Layer |
|------|------|-------|
| `TextChunkingServiceTest` | Unit | chunking |
| `ContextBuilderTest` | Unit | context |
| `GlobalExceptionHandlerTest` | Unit | error mapping |
| `RepositoryIT` | Testcontainers | persistence |
| `DocumentIngestionServiceIT` | Testcontainers (mock embeddings) | ingestion |
| `VectorSearchServiceIT` | Testcontainers | retrieval |
| `QueryServiceIT` | Testcontainers (mock OpenAI+Claude) | query |
| `DocumentControllerTest` | MockMvc/RestAssured | API |
| `QueryControllerTest` | MockMvc/RestAssured | API |

---

## Risks / watch-items

- **hibernate-vector ↔ Flyway DDL parity** (issue #2/#3) — verify early in Phase 1; it's the highest-uncertainty integration point.
- **External API shape drift** — pin OpenAI `/v1/embeddings` and Anthropic `/v1/messages` request/response DTOs; keep them mockable so tests never hit the network.
- **IVFFlat training** (issue #4) — index quality depends on data volume; fine for demo, note in README.
- **Cost/secrets** — never commit keys; mask in logs; tests must mock both providers.
