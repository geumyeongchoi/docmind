# docmind — 사내 문서 RAG 챗봇 (private / public 전환형)

> 문서를 올리면 그 문서만 근거로 답하고 출처를 붙여주는 사무용 챗봇.
> 사내망에서는 Ollama(로컬 모델), 외부에서는 OpenAI 최신 모델로 **설정 한 줄로 전환**.

설계 문서: `docs/design.md` (C4 · 시퀀스 · ERD · ADR 8건) · FigJam: https://www.figma.com/board/XBqQCmaNaMFG98Cx2xP9ZH

## 1. 무엇을 해결하나

- 사규·기술 문서·회의록은 여기저기 흩어져 있고 키워드 검색은 "연차 이월돼요?" 같은 질문에 답을 못 준다.
- 문서를 외부 LLM에 보내면 안 되는 조직이 많다 → **데이터가 서버 밖으로 나가지 않는 모드**가 필요하다.
- 정확도가 중요할 때는 상용 모델을 쓰고 싶다 → **같은 코드·같은 이미지로 모델만 교체**.

## 2. 아키텍처 한눈에

```
[Web UI] ──SSE──▶ [docmind-api  Kotlin 2.2 · Spring Boot 3.5 · Spring AI 1.1]
                     ├ Ingest: Tika → OverlappingTokenSplitter(600/80) → Embedding → pgvector
                     ├ Retrieval: 벡터 ∥ 전문검색(tsvector) → RRF 병합 → topK 6
                     ├ Chat: 근거 없으면 LLM 미호출 / ChatClient + MessageChatMemory / 질문 세이프가드
                     └ MCP Server: search_docs · ask_docs  (Claude Desktop / Claude Code)
              profile=private → Ollama (bge-m3 1024d · gemma3/qwen3)
              profile=public  → OpenAI (text-embedding-3-small 1536d · gpt-4.1-mini)
              PostgreSQL 17 + pgvector  (documents · docmind_private · docmind_public · tsvector GIN)
```

패키지는 헥사고날 경량(`api / application / domain / infra / mcp`)이며 `domain`의 프레임워크 무의존, 유스케이스의 포트 전용 의존, Spring AI 타입의 어댑터 격리를 **ArchUnit 테스트로 강제**한다.

## 3. 핵심 설계 결정 (요약 — 상세는 docs/design.md §7)

| 결정 | 대안 | 이유 |
|---|---|---|
| Spring AI (JVM 단일 서비스) | Python LangChain 별도 서비스 | 배포·관측·테스트 한 벌. Loader/Splitter/VectorStore/Advisor가 LangChain 개념과 1:1 |
| pgvector + tsvector 하이브리드(RRF) | 벡터 단독, 전용 벡터DB | 추가 인프라 0, 고유명사·코드명 recall 보완, 파라미터 k 하나 |
| 프로파일별 벡터 테이블 분리 | 공용 테이블 | 임베딩 차원(1024 vs 1536)이 달라 같은 인덱스에 못 섞는다 |
| 근거 없으면 LLM 미호출 | 프롬프트로 억제 | 비용 0·환각 차단·지연 최소. 임계값은 평가셋으로 튠 |
| 오버랩 토큰 분할기 자체 구현 | Spring AI TokenTextSplitter | 오버랩 미지원. 문장 경계 컷 + 유실 없는 시작점 계산 |
| 세이프가드를 질문에만 적용 | SafeGuardAdvisor(프롬프트 전체) | 인젝션 문구가 든 문서가 검색되면 정상 질문까지 거절되는 문제 |
| Boot 3.5 / Spring AI 1.1 (GA) | Boot 4.1 / Spring AI 2.0 | 첫 스프린트는 안정 라인, 2.0 업그레이드는 별도 브랜치(로드맵) |

## 4. 실행

```bash
docker compose up -d pgvector                 # localhost:55432 (사내 DB 5432와 충돌 방지)

# private — Ollama (Mac Apple Silicon 권장)
brew install ollama && ollama serve &
ollama pull bge-m3 && ollama pull gemma3:4b
./gradlew bootRun --args='--spring.profiles.active=private'

# public — OpenAI
OPENAI_API_KEY=sk-... ./gradlew bootRun --args='--spring.profiles.active=public'

scripts/seed.sh                                # eval/docs 8개 업로드 → DONE 대기 → 상태 표
./gradlew eval                                 # 평가셋 30문항 → build/eval/report.md
open http://localhost:8080                     # 최소 UI (업로드·채팅·출처)
```

API: `POST /api/documents`(multipart) · `GET /api/documents[/{id}]` · `DELETE /api/documents/{id}` · `POST /api/chat`(SSE: citations → token* → done) · `GET /api/health/ai`
MCP: `GET /sse` + `POST /mcp/message` — Claude Desktop 설정 예시는 `docs/mcp.md`

### 검증

```bash
scripts/verify.sh --fast    # compile + ktlint + 단위/아키텍처 테스트
scripts/verify.sh --full    # + Testcontainers(pgvector) 통합 테스트  ※ Docker 29+ 는 API 1.44 필요 → build.gradle.kts 에서 DOCKER_API_VERSION 설정
```

## 5. 평가 결과

`./gradlew eval` 결과를 여기에 고정한다. (평가셋: 일반 사실 60% · 고유명사 20% · 문서에 없음 13% · 인젝션 7%)

| 프로파일 / 모델 | 청크 | 검색 | 정답 포함률 | 인용 정확률 | p50 |
|---|---|---|---|---|---|
| private · gemma3:4b · bge-m3 | 600/80 | hybrid | _측정 중_ | | |
| private · gemma3:4b · bge-m3 | 600/80 | vector only | | | |
| public · gpt-4.1-mini · te3-small | 600/80 | hybrid | _API 키 필요_ | | |

관찰 기록
- qwen3:4b 는 기본이 thinking 모드라 RAG 컨텍스트(≈2.6k 토큰)에서 완료 토큰 2.7k, 응답 103초. Spring AI 1.1의 Ollama 옵션으로 thinking을 끌 수 없어 private 기본 모델을 gemma3:4b 로 변경.
- 한국어 전문검색은 `simple` 사전(공백 토큰)이라 조사가 붙은 어절("연차는")과 질문 어절("연차")이 불일치 → 하이브리드 효과가 제한적. `pg_bigm` 전환은 로드맵.

## 6. 로드맵

Spring AI 2.0 / Boot 4.1 업그레이드 · 크로스인코더 재순위 · pg_bigm 한국어 전문검색 · 문서 ACL(metadata 필터) · Confluence 커넥터 · k3s 배포(homelab-platform)

## 7. 개발 방식

이 저장소는 Claude(Cowork 세션)가 스펙(`specs/day*.md`) 기준으로 구현·테스트를 수행하고, 설계 판단·검증·커밋은 사람이 확인하는 방식으로 진행했다. 규칙은 `CLAUDE.md`(=`AGENTS.md`), 게이트는 `scripts/verify.sh`.
