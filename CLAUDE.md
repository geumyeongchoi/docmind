# docmind — 에이전트 작업 규칙 (SSOT)

## 스택
- Kotlin 2.3, JDK 21, Spring Boot 4.1, Spring AI 2.0 (BOM), Gradle Kotlin DSL
- PostgreSQL 17 + pgvector (Testcontainers `pgvector/pgvector:pg17`)
- 문서 파싱: spring-ai-tika-document-reader / 벡터: spring-ai-starter-vector-store-pgvector
- 모델: profile `private` = Ollama, `public` = OpenAI (Anthropic 옵션)
- 테스트: Kotest + MockK + Testcontainers, ktlint

## HARD-GATE (위반 시 작업 중단)
1. `main` 브랜치에 직접 push 금지. 항상 `feat/*` 브랜치 + PR.
2. API 키·토큰을 코드·yml·테스트에 하드코딩 금지. 환경변수(`OPENAI_API_KEY`)만.
3. 문서 원문·임베딩을 로그에 출력 금지(`DEBUG`에서도 청크 앞 80자만).
4. 변경 후 `scripts/verify.sh --fast` 통과 전 커밋 금지.

## 구조 (패키지 = 헥사고날 경량)
```
dev.gychoi.docmind
├─ api/          Controller, DTO, SSE
├─ application/  UseCase (IngestDocument, AskQuestion, EvaluateRag)
├─ domain/       Document, Chunk, Citation, Answer — 프레임워크 의존 없음
├─ infra/
│   ├─ ai/       ChatClient 구성, Advisor, 프로파일별 모델 설정
│   ├─ vector/   PgVectorStore 어댑터, HybridRetriever(RRF)
│   └─ ingest/   Tika reader, Splitter
└─ mcp/          @McpTool 정의
```
- `domain`은 spring 패키지 import 금지 (ArchUnit 테스트로 강제).
- 검색 로직은 반드시 `Retriever` 인터페이스 뒤에 두고, 벡터/하이브리드를 구현체로 교체 가능하게.

## 작업 방식
- `specs/dayN.md`를 읽고 그 안의 DoD와 테스트 시나리오를 먼저 테스트 코드로 옮긴 뒤 구현.
- 같은 오류 3회 반복 시 중단하고 원인·시도한 것·필요한 정보를 보고.
- 커밋 메시지: `feat(spec): …` / `feat: …` / `test: …` / `docs: …`. Co-Authored-By 등 에이전트 서명 금지.

## 검증
- `scripts/verify.sh --fast` : compile + ktlint + 단위 테스트
- `scripts/verify.sh --full` : + Testcontainers 통합 테스트 (Docker 필요)
- `./gradlew eval` : 평가셋 실행, `build/eval/report.md` 생성
