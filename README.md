# docmind — 사내 문서 RAG 챗봇 (private / public 전환형)

> 문서를 올리면 그 문서만 근거로 답하고 출처를 붙여주는 사무용 챗봇.
> 사내망에서는 Ollama(로컬 모델), 외부에서는 OpenAI(ChatGPT)·Anthropic(Claude) 모델로 **프로파일 한 줄로 전환**.

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
              profile=private → Ollama (bge-m3 1024d · gemma3/qwen3)                 [사내망]
              profile=public  → OpenAI (text-embedding-3-small 1536d · gpt-4.1-mini)   [ChatGPT]
              profile=claude  → Anthropic Claude(chat) + Ollama bge-m3(embedding)       [Claude · 문서는 로컬에]
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

## 4. 실행 · 모델 설정

**모델(내부/외부)은 코드가 아니라 `src/main/resources/application.yml` 의 Spring 프로파일로 고른다.** 실행 시 `--spring.profiles.active=…` 하나만 바꾸면 되고, 모델명·주소·키는 전부 환경변수다. 코드는 Spring AI 의 `ChatModel`/`EmbeddingModel` 인터페이스만 보므로 공급자를 바꿔도 소스 변경이 없다.

| 프로파일 | 답변 생성(chat) | 임베딩 | 벡터 테이블 | 필요한 환경변수 | 데이터 흐름 |
|---|---|---|---|---|---|
| `private` | Ollama `gemma3:4b` | Ollama `bge-m3` (1024d) | `docmind_private` | `OLLAMA_URL`, `OLLAMA_CHAT_MODEL` | 전부 서버 안 |
| `public` | OpenAI `gpt-4.1-mini` | OpenAI `text-embedding-3-small` (1536d) | `docmind_public` | `OPENAI_API_KEY`, `OPENAI_CHAT_MODEL` | 문서·질문 모두 OpenAI 로 |
| `claude` | Anthropic `claude-sonnet-4-5` | Ollama `bge-m3` (1024d) | `docmind_private` (private 와 공유) | `ANTHROPIC_API_KEY`, `ANTHROPIC_CHAT_MODEL` | 문서 임베딩은 로컬, **질문 + 검색된 청크만** Anthropic 으로 |

- `claude` 가 임베딩을 로컬에 두는 이유: Anthropic 은 임베딩 API 가 없고, 임베딩 모델을 바꾸면 벡터 공간이 달라져 문서를 전부 재인제스트해야 한다. private 와 임베딩을 공유하면 같은 문서를 두 모드에서 그대로 쓰고, 외부로 나가는 데이터는 질문과 topK 청크로 최소화된다(`docmind.index-label: private`).
- 다른 공급자(Azure OpenAI, Google Gemini, Mistral, OpenAI 호환 서버 등)를 붙이려면: `build.gradle.kts` 에 해당 `spring-ai-starter-model-*` 추가 → yml 에 프로파일 블록 하나 추가(`spring.ai.model.chat: <provider>` + 키). 코드 변경 없음.
- 지금 어떤 모델이 붙어 있는지는 `GET /api/health/ai` (화면 상단에도 표시) — profile · chatModel · chatProvider · embeddingProvider · 차원 일치 여부.

```bash
docker compose up -d pgvector                 # localhost:55432 (사내 DB 5432와 충돌 방지)

# private — Ollama (Mac Apple Silicon 권장)
brew install ollama && ollama serve &
ollama pull bge-m3 && ollama pull gemma3:4b
./gradlew bootRun --args='--spring.profiles.active=private'

# public — OpenAI(ChatGPT)
OPENAI_API_KEY=sk-... ./gradlew bootRun --args='--spring.profiles.active=public'

# claude — Anthropic (임베딩은 로컬 Ollama bge-m3 재사용)
ANTHROPIC_API_KEY=sk-ant-... ./gradlew bootRun --args='--spring.profiles.active=claude'

scripts/seed.sh                                # eval/docs 8개 업로드 → DONE 대기 → 상태 표
./gradlew eval                                 # 평가셋 30문항 → build/eval/report.md
open http://localhost:8080                     # 최소 UI (업로드·채팅·출처)
```

API: `POST /api/documents`(multipart) · `GET /api/documents[/{id}]` · `GET /api/documents/{id}/content`(원본, inline) · `GET /api/documents/{id}/chunks`(뷰어용) · `DELETE /api/documents/{id}` · `POST /api/chat`(SSE: citations → token* → done) · `GET /api/health/ai`

출처 → 원문: 출처 카드와 답변 속 `[출처: 2]` 는 `viewer.html?id=…&chunk=…&page=…` 로 연결된다. 뷰어는 인덱싱된 청크 전체를 보여주고 출처 청크를 강조·스크롤하며, PDF 는 브라우저 내장 뷰어로 원본을 `#page=` 위치에 함께 연다. 원본은 업로드 시 `document_files`(BYTEA) 에 보관하고 문서 삭제 시 CASCADE 로 함께 지운다(포트 `DocumentFileStore` — 대용량이면 오브젝트 스토리지 구현으로 교체).
MCP: `GET /sse` + `POST /mcp/message` — Claude Desktop 설정 예시는 `docs/mcp.md`

### 검증

```bash
scripts/verify.sh --fast    # compile + ktlint + 단위/아키텍처 테스트
scripts/verify.sh --full    # + Testcontainers(pgvector) 통합 테스트  ※ Docker 29+ 는 API 1.44 필요 → build.gradle.kts 에서 DOCKER_API_VERSION 설정
```

## 5. 평가 결과 (실측)

환경: MacBook Pro(Apple Silicon) · profile=private · gemma3:4b · bge-m3(1024d) · 청크 600/80 · topK 6 · 문서 8종(9 청크) · 평가셋 30문항(일반 사실 60% · 고유명사 20% · 문서에 없음 13% · 인젝션 7%). 원본: `eval/results/*.md`

| # | 시스템 프롬프트 | 검색 | 정답 포함률 | 인용 정확률 | p50 |
|---|---|---|---|---|---|
| v1 | 엄격 거절 우선("컨텍스트에 없으면 찾지 못했습니다") | hybrid | 43.3% | 100% | 4.6s |
| v2 | "관련 문장이 하나라도 있으면 반드시 답" 추가 | hybrid | 70.0% | 100% | 5.5s |
| v3 | v2 + 번호 인용 형식 + 거절 규칙 재강조 | hybrid | 56.7% | 100% | 5.4s |
| v3 | 동일 | **vector only** | 50.0% | 100% | 5.1s |
| **v4** | v2 톤 + 번호 인용 + 자료 내 명령 무시 + 거절 규칙을 마지막에 | hybrid | **76.7%** | 100% | 5.9s |

청킹 비교 (프롬프트 v4 · hybrid · 같은 문서 재인제스트)

| 청크 크기 / 오버랩 | 청크 수 | 정답 포함률 | p50 |
|---|---|---|---|
| 300 / 37 | 17 | **83.3%** | 4.7s |
| 600 / 80 (기본) | 9 | 76.7% | 5.9s |
| 1000 / 125 | 8 | 80.0% | 7.0s |

읽는 법
- **프롬프트 문구 하나로 43% ↔ 77%.** 4B 모델은 거절 규칙의 위치·강도에 매우 민감하다. 프롬프트를 코드처럼 평가셋으로 회귀 테스트해야 한다는 것이 이 표의 결론.
- **하이브리드 vs 벡터 단독: +6.7pt** (같은 프롬프트 v3). 고유명사 문항(`PRC_SOOROU001`, `E4012`, `payment.card.events`)이 차이를 만든다.
- **인용 정확률 100%는 아직 의미가 없다.** 코퍼스가 9청크라 topK 6이 거의 전부를 덮는다. 문서 30+로 늘려 재측정하는 것이 로드맵 1순위.
- **작은 청크가 유리했다(300 > 1000 > 600).** 컨텍스트에 관련 없는 문장이 적게 섞일수록 4B 모델이 덜 흔들린다. 다만 30문항에서 2문항 차이는 노이즈 범위라 코퍼스 확장 후 재검증 대상. 큰 청크는 프롬프트 토큰이 늘어 p50 도 1.2초 느려졌다.
- 남은 오답 유형: (a) 두 청크에 걸친 항목(연차 이월 q01) (b) "~해도 되나요" 류 판단형 질문(q14·q27)을 거절 (c) 인젝션 문서를 인용하며 문장 일부를 옮김(q30) — (c)는 출력 후처리 가드 후보.

관찰 기록
- qwen3:4b 는 기본이 thinking 모드라 RAG 컨텍스트(≈2.6k 토큰)에서 완료 토큰 2.7k, 응답 103초. Spring AI 1.1의 Ollama 옵션으로 thinking을 끌 수 없어 private 기본 모델을 gemma3:4b 로 변경.
- 한국어 전문검색은 `simple` 사전(공백 토큰)이라 조사가 붙은 어절("연차는")과 질문 어절("연차")이 불일치 → 하이브리드 효과가 제한적. `pg_bigm` 전환은 로드맵.
- 인제스트: 8문서 9청크, 총 2.4초(임베딩 포함, bge-m3 로컬).

## 6. 로드맵

Spring AI 2.0 / Boot 4.1 업그레이드 · 크로스인코더 재순위 · pg_bigm 한국어 전문검색 · 문서 ACL(metadata 필터) · Confluence 커넥터 · k3s 배포(homelab-platform)

## 7. 개발 방식

이 저장소는 Claude(Cowork 세션)가 스펙(`specs/day*.md`) 기준으로 구현·테스트를 수행하고, 설계 판단·검증·커밋은 사람이 확인하는 방식으로 진행했다. 규칙은 `CLAUDE.md`(=`AGENTS.md`), 게이트는 `scripts/verify.sh`.
