# Day 3 — 인제스트 파이프라인 + 업로드 API

## 목표
문서 파일을 올리면 파싱 → 청킹 → 임베딩 → pgvector 저장까지 비동기로 끝나고, 상태를 조회할 수 있다.

## 범위
1. `POST /api/documents` (multipart, 여러 파일) → `202 Accepted` + `documentId[]`
2. `GET /api/documents/{id}` → `{id, filename, status: QUEUED|PROCESSING|DONE|FAILED, chunkCount, elapsedMs, error?}`
3. `GET /api/documents` 목록
4. 파이프라인: `TikaDocumentReader` → `TokenTextSplitter(chunkSize, overlap)` → `VectorStore.add()`
   - 청크 metadata: `documentId, filename, page(가능하면), chunkIndex, profile`
   - 같은 파일 재업로드 시 sha256 기준 중복 스킵
5. Flyway `V1__documents.sql`: `documents(id uuid pk, filename, sha256 unique, status, chunk_count, elapsed_ms, error, created_at)`
6. 비동기 실행: `@Async` + 전용 스레드풀(동시 2). 임베딩 API rate limit 대비 청크 32개 배치.

## 테스트 시나리오 (먼저 작성)
- [단위] `TokenTextSplitter` 600/80 설정으로 3,000토큰 텍스트 → 청크 수와 오버랩 검증
- [단위] 같은 sha256 두 번 업로드 → 두 번째는 `DUPLICATE` 상태로 즉시 종료
- [통합, Testcontainers pgvector + 임베딩 모델 Mock(고정 벡터)] MD 파일 1개 업로드 → status DONE, chunk_count > 0, 벡터 테이블 row 수 = chunk_count
- [ArchUnit] `domain` 패키지는 `org.springframework..` 의존 금지

## DoD
- `scripts/verify.sh --full` 통과
- 샘플 문서 10개(`eval/docs/`) 업로드 스크립트 `scripts/seed.sh` 실행 후 전부 DONE, 총 소요 시간을 README 표에 기록

## 참고
- Spring AI 2.0 ETL 문서: DocumentReader / DocumentTransformer / DocumentWriter
- 임베딩 차원은 프로파일별 yml `dimensions`와 모델이 일치해야 함(불일치 시 기동 실패하도록 헬스체크 추가)
