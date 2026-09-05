-- Spring AI PgVectorStore 스키마와 동일한 컬럼(id, content, metadata, embedding) + 전문검색 생성 컬럼.
-- initialize-schema=false 이므로 여기서 직접 관리한다. 임베딩 차원이 달라 프로파일별 테이블을 분리(ADR-4).

CREATE TABLE docmind_private (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content     TEXT,
    metadata    JSON,
    embedding   VECTOR(1024),
    content_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED
);
CREATE INDEX ix_docmind_private_embedding ON docmind_private USING HNSW (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX ix_docmind_private_tsv ON docmind_private USING GIN (content_tsv);
CREATE INDEX ix_docmind_private_doc ON docmind_private ((metadata->>'documentId'));

CREATE TABLE docmind_public (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content     TEXT,
    metadata    JSON,
    embedding   VECTOR(1536),
    content_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED
);
CREATE INDEX ix_docmind_public_embedding ON docmind_public USING HNSW (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX ix_docmind_public_tsv ON docmind_public USING GIN (content_tsv);
CREATE INDEX ix_docmind_public_doc ON docmind_public ((metadata->>'documentId'));
