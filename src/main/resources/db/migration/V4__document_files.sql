-- 원본 파일 보관: 출처 카드 → 원문 열기(뷰어)용. 문서 삭제 시 함께 삭제.
-- 50MB 이하 사무 문서를 대상으로 하므로 BYTEA(인라인 TOAST)로 충분하다. 대용량이면 오브젝트 스토리지로 교체(포트 DocumentFileStore).
CREATE TABLE document_files (
    document_id   UUID PRIMARY KEY REFERENCES documents(id) ON DELETE CASCADE,
    content_type  VARCHAR(128),
    bytes         BYTEA NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
