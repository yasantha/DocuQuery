-- DocuQuery initial schema (SPEC §4.1)

CREATE EXTENSION IF NOT EXISTS vector;

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

CREATE TABLE document_chunks (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT        REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index  INT           NOT NULL,
    content      TEXT          NOT NULL,
    char_count   INT,
    embedding    vector(1536),           -- OpenAI text-embedding-3-small
    created_at   TIMESTAMP     DEFAULT NOW()
);

-- IVFFlat index for fast approximate nearest neighbour search.
-- lists = 100 is recommended for up to ~1M vectors.
CREATE INDEX idx_chunks_embedding
    ON document_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX idx_chunks_document_id ON document_chunks(document_id);

CREATE TABLE query_history (
    id                   BIGSERIAL PRIMARY KEY,
    document_id          BIGINT     REFERENCES documents(id) ON DELETE CASCADE,
    question             TEXT       NOT NULL,
    answer               TEXT       NOT NULL,
    chunks_used          INT,
    source_chunk_indexes INT[],            -- which chunks answered this
    queried_at           TIMESTAMP  DEFAULT NOW()
);
