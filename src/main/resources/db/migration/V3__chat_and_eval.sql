CREATE TABLE chat_messages (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(128) NOT NULL,
    role        VARCHAR(16)  NOT NULL, -- user | assistant
    content     TEXT         NOT NULL,
    citations   JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_chat_messages_session ON chat_messages (session_id, created_at);

CREATE TABLE eval_runs (
    id                 BIGSERIAL PRIMARY KEY,
    profile            VARCHAR(16) NOT NULL,
    chunk_size         INT         NOT NULL,
    hybrid             BOOLEAN     NOT NULL,
    total              INT         NOT NULL,
    answer_hit_rate    NUMERIC(5,2) NOT NULL,
    citation_hit_rate  NUMERIC(5,2) NOT NULL,
    p50_ms             INT         NOT NULL,
    run_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
