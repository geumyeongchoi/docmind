# docmind — 사내 문서 RAG 챗봇 (private / public 전환형)

> 문서를 올리면 그 문서만 근거로 답하고 출처를 붙여주는 사무용 챗봇.
> 사내망에서는 Ollama(로컬 모델), 외부에서는 OpenAI/Anthropic 최신 모델로 **설정 한 줄로 전환**.

## 1. 문제 정의

- 사내 규정·기술 문서·회의록은 Confluence/PDF/MD로 흩어져 있고, 검색은 키워드 매칭이라 "휴가 이월 규정이 뭐야" 같은 질문에 답을 못 준다.
- 문서를 외부 LLM에 보내는 것이 금지된 조직이 많다 → **데이터가 서버 밖으로 나가지 않는 모드**가 필요.
- 반대로 정확도가 중요할 때는 최신 상용 모델을 쓰고 싶다 → **동일 코드로 모델만 교체**.

## 2. 아키텍처

```
[Web UI(정적)] ──SSE──▶ [docmind API: Kotlin · Spring Boot 4.1 · Spring AI 2.0]
                              │
        ┌─────────────────────┼──────────────────────────┐
        ▼                     ▼                          ▼
 Ingest Pipeline        Retrieval                    ChatClient + Advisors
 Tika Reader            pgvector(cosine)             ├ RetrievalAugmentationAdvisor
 → TokenTextSplitter    + PostgreSQL FTS(BM25 유사)  ├ SafeGuardAdvisor(주입 방어)
 → EmbeddingModel       → RRF 병합 → (선택)재순위    └ ChatMemory(대화 창 10턴)
 → VectorStore                                        │
                                                      ▼
                       profile=private → Ollama(qwen3:8b / bge-m3 임베딩)
                       profile=public  → OpenAI gpt-4.1-mini / text-embedding-3-small
                                          (Anthropic claude-haiku도 스위치 가능)
 MCP Server(@McpTool search_docs) ──▶ Claude Desktop / Claude Code에서 툴로 호출
```

핵심 설계 결정(면접용):

| 결정 | 대안 | 선택 이유 |
|---|---|---|
| Spring AI 2.0 | LangChain4j, Python LangChain 별도 서비스 | 단일 JVM 서비스로 배포·관측 단순화, Boot 4.1 네이티브 통합, MCP 내장. LangChain의 Loader/Splitter/Retriever 개념과 1:1 대응 |
| pgvector | Qdrant, Milvus, Elasticsearch | 이미 RDB가 있는 사내 환경에서 **추가 인프라 0**. 전문검색(tsvector)까지 한 DB에서 하이브리드 가능. 규모 커지면 VectorStore 인터페이스만 바꾸면 됨 |
| 하이브리드 검색(벡터 + FTS, RRF) | 벡터 단독 | 사내 문서는 고유명사·코드명이 많아 벡터 단독은 recall 손실. 평가셋으로 수치 증명 |
| 임베딩 모델을 프로파일별로 분리 | 공용 임베딩 | 임베딩 차원이 달라 인덱스 호환 불가 → 프로파일별 테이블(`docmind_private`, `docmind_public`) 분리, 문서 원본은 공유 |
| SSE 스트리밍 | WebSocket | 단방향 토큰 스트림엔 SSE가 단순하고 프록시 친화적 |

## 3. 기능 범위 (Day 3~5)

**MUST**
- 문서 업로드 API(`POST /api/documents`, PDF·DOCX·MD·TXT) → 비동기 인제스트, 상태 조회
- 채팅 API(`POST /api/chat`, SSE) — 답변 + 인용 청크(문서명·페이지·유사도)
- `spring.profiles.active=private|public` 전환, 모델명은 yml
- 하이브리드 검색 + RRF
- 평가셋 30문항 `eval/golden.yaml` + `./gradlew eval` 로 정답포함률·인용정확률 출력
- 단위/통합 테스트(Testcontainers pgvector)

**SHOULD**
- MCP 서버(`search_docs`, `ask_docs`) — Spring AI MCP Server starter
- 대화 메모리(세션 ID 기준 10턴)
- 프롬프트 인젝션 가드(문서 안의 "이전 지시 무시" 문구 무력화 테스트 케이스)

**WON'T (이번엔 안 함)**
- 권한별 문서 접근 제어, 멀티 테넌시, OCR, 파인튜닝

## 4. 평가 방법 — 수치를 남기는 것이 이 프로젝트의 핵심

1. 샘플 문서 세트: 공개 문서 10개(예: Spring 공식 레퍼런스 일부, 근로기준법 요약, 자작 사규 3개).
2. `eval/golden.yaml`: 질문·정답 키워드·근거 문서. 30문항.
3. 지표: **정답 키워드 포함률**(answer contains), **인용 정확률**(top-k 안에 근거 문서 존재), **p50 응답 시간**.
4. 비교 축: 청킹(300/600/1000 토큰) × 검색(벡터/하이브리드) × 모델(private/public). 결과 표를 README에 고정.

## 5. 실행

```bash
docker compose up -d            # pgvector, ollama(옵션)
ollama pull qwen3:8b && ollama pull bge-m3   # private 모드용 (Mac 로컬 권장)
./gradlew bootRun --args='--spring.profiles.active=private'
# 또는
OPENAI_API_KEY=... ./gradlew bootRun --args='--spring.profiles.active=public'
./gradlew eval                  # 평가셋 실행
```

## 6. 이력서/면접 포인트

- "왜 LangChain 안 썼나" → JVM 단일 서비스, 관측·배포 통합. 개념은 동일하고 Retriever/Advisor 체인으로 구현.
- "환각은 어떻게 막나" → 근거 없으면 "문서에서 찾지 못했습니다" 강제(시스템 프롬프트 + 유사도 임계값 + 평가셋 항목).
- "정확도 얼마나 올렸나" → 하이브리드 도입 전후 수치.
