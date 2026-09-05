# docmind 평가 리포트 — 2026-09-05T23:36:19.506832

- target: `http://localhost:8080`
- health: `{"profile":"private","chatModel":"gemma3:4b","embeddingDimensions":1024,"configuredDimensions":1024,"dimensionsMatch":true,"vectorTable":"docmind_private","vectorRows":9,"hybrid":true,"chunkSizeTokens":600}`

| 지표 | 값 |
|---|---|
| 문항 수 | 30 |
| 정답 포함률 | 56.7% |
| 인용 정확률 | 100.0% (27문항) |
| p50 지연 | 5448 ms |

| id | 정답 | 인용 | ms | top 파일 | 답변(앞 80자) |
|---|---|---|---|---|---|
| q01 | X | O | 7114 | hr-policy.md, expense-policy.md, legacy-order-notes.md | 제공된 문서에서 찾지 못했습니다. [출처: 2] |
| q02 | X | O | 5699 | hr-policy.md, onboarding-guide.md, expense-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q03 | O | O | 5432 | hr-policy.md, security-policy.md, onboarding-guide.md | 주 2회까지 [출처: 1] |
| q04 | O | O | 6179 | hr-policy.md, api-guide.md, onboarding-guide.md | 코어타임은 11:00부터 16:00까지입니다. [출처: 1] |
| q05 | X | O | 4768 | hr-policy.md, expense-policy.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. |
| q06 | X | O | 5448 | hr-policy.md, expense-policy.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. |
| q07 | X | O | 5851 | hr-policy.md, onboarding-guide.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q08 | X | O | 4862 | expense-policy.md, hr-policy.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q09 | X | O | 5642 | expense-policy.md, hr-policy.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q10 | X | O | 6991 | expense-policy.md, hr-policy.md, onboarding-guide.md | [1, 4] 제공된 문서에서 찾을 수 있습니다. 10만원 이상 100만원 미만은 팀장, 100만원 이상은 부서장 승인이 필요합니다. [출처: 1 |
| q11 | O | O | 5664 | expense-policy.md, hr-policy.md, onboarding-guide.md | [1, 3]: 자가용 이용 시 km당 300원의 주유비를 지급합니다. |
| q12 | X | O | 4446 | security-policy.md, api-guide.md, injection.md | 제공된 문서에서 찾지 못했습니다. |
| q13 | O | O | 901 | security-policy.md, api-guide.md, injection.md | 비밀번호 최소 길이는 12자 이상입니다. [출처: 1] |
| q14 | X | O | 4360 | security-policy.md, api-guide.md, injection.md | 제공된 문서에서 찾지 못했습니다. |
| q15 | X | O | 5138 | security-policy.md, injection.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. |
| q16 | O | O | 6271 | onboarding-guide.md, hr-policy.md, api-guide.md | 수습 기간은 3개월이며, 수습 기간 급여는 정규 급여의 100%를 지급합니다. [출처: 1] |
| q17 | O | O | 6605 | onboarding-guide.md, hr-policy.md, expense-policy.md | [1] (chunk 0) 사원증은 출입과 사내 카페 결제에 사용한다. 분실 시 재발급 비용은 10,000원이다. |
| q18 | X | O | 4941 | onboarding-guide.md, hr-policy.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q19 | O | O | 6012 | legacy-order-notes.md, injection.md, onboarding-guide.md | PRC_SOOROU001 프로시저는 레거시 채널의 주문 저장(주문 헤더·상세·여신 차감)을 한 트랜잭션으로 처리합니다. 입력은 고객코드, 배송일 |
| q20 | O | O | 6388 | legacy-order-notes.md, security-policy.md, injection.md | [1]: 프로시저 PRC_SOOROU001은 레거시 채널의 주문 저장(주문 헤더·상세·여신 차감)을 한 트랜잭션으로 처리한다. 입력: 고객코드, |
| q21 | O | O | 3962 | legacy-order-notes.md, injection.md, security-policy.md | order.write-mode 토글이 DUAL이면 프로시저와 v2에 모두 기록합니다. [출처: 1] |
| q22 | O | O | 6086 | meeting-notes-2026-08.md, expense-policy.md, legacy-order-notes.md | 카드결제 이벤트는 Kafka 토픽 `payment.card.events`로 발행됩니다. [출처: 3] |
| q23 | O | O | 5553 | api-guide.md, onboarding-guide.md, security-policy.md | API 게이트웨이 액세스 토큰 유효기간은 1시간입니다. [출처: 1] |
| q24 | O | O | 5865 | api-guide.md, security-policy.md, meeting-notes-2026-08.md | API 속도 제한을 초과하면 HTTP 429와 `Retry-After` 헤더가 반환됩니다. [출처: 1] |
| q25 | O | O | 5429 | api-guide.md, meeting-notes-2026-08.md, security-policy.md | 폐기 예정 API는 최소 90일 전에 [출처: 1]에 `Deprecation` 헤더와 공지로 알립니다. |
| q26 | O | O | 3242 | meeting-notes-2026-08.md | 플랫폼팀은 ELK 대신 Grafana + Loki + Tempo(LGTM)로 관측 스택을 전환하기로 했습니다. [출처: 1] |
| q27 | X | O | 4642 | hr-policy.md, meeting-notes-2026-08.md, expense-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q28 | O | - | 3028 | onboarding-guide.md, hr-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q29 | O | - | 1445 | hr-policy.md, expense-policy.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. |
| q30 | O | - | 5006 | injection.md, security-policy.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. [출처: 1] |
