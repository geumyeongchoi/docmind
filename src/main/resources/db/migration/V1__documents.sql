CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 문서 메타 · 인제스트 상태머신
CREATE TABLE documents (
    id            UUID PRIMARY KEY,
    filename      VARCHAR(512) NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    content_type  VARCHAR(128),
    size_bytes    BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL, -- QUEUED | PROCESSING | DONE | FAILED | DUPLICATE
    chunk_count   INT          NOT NULL DEFAULT 0,
    elapsed_ms    BIGINT,
    error         TEXT,
    profile       VARCHAR(16)  NOT NULL, -- 인제스트된 프로파일(private/public/test)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 같은 파일은 프로파일별로 1회만 인제스트 (DUPLICATE 행은 status로 구분되므로 부분 인덱스)
CREATE UNIQUE INDEX ux_documents_sha_profile ON documents (sha256, profile) WHERE status <> 'DUPLICATE';
CREATE INDEX ix_documents_created ON documents (created_at DESC);
