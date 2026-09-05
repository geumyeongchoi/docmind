# Day 4 — 채팅 API(SSE) + 하이브리드 검색 + 프로파일 전환

## 목표
질문하면 문서 근거로 스트리밍 답변 + 출처가 오고, `private`/`public` 프로파일에서 같은 코드가 동작한다.

## 범위
1. `POST /api/chat` `{sessionId, question}` → `text/event-stream`
   - 이벤트: `citations`(먼저, top-k 청크 요약: documentId, filename, page, score) → `token`(N회) → `done`(usage, elapsedMs, profile, model)
2. `AskQuestionUseCase`: `Retriever.retrieve(q)` → 컨텍스트 조립(최대 N토큰) → `ChatClient.prompt().advisors(RAG advisor).stream()`
   - 검색 결과가 0건이거나 최고 점수 < threshold → LLM 호출 없이 "문서에서 찾지 못했습니다" 즉시 반환 (비용·환각 방지)
3. `HybridRetriever` 완성: Flyway `V2__fts.sql`(tsvector 생성 컬럼 + GIN), RRF 병합, `docmind.retrieval.hybrid` 토글
4. 프로파일 전환 검증: `private`(Ollama qwen3:8b/bge-m3), `public`(OpenAI) 각각 기동해 동일 질문 응답 캡처 → README
5. `GET /api/health/ai` : 현재 profile, chat model, embedding model, dimensions, 벡터 row 수

## 테스트 시나리오
- [단위] RRF: 두 리스트 `[A,B,C]`, `[C,A,D]` → 순서 `A, C, B, D` (k=60 계산값 검증)
- [단위] threshold 미만이면 ChatModel 호출 0회 (MockK verify)
- [통합] SSE 이벤트 순서 `citations → token+ → done`
- [통합] 하이브리드 on/off로 같은 질문 검색 → 고유명사 질문("PRC_SOOROU001 프로시저는 뭐야")에서 FTS 결과가 포함되는지

## DoD
- 두 프로파일 모두 `curl -N` 으로 스트리밍 확인
- 하이브리드 on/off 검색 결과 차이 예시 1건 README에 기록
