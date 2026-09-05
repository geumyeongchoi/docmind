# docmind 평가 리포트 — 2026-09-05T23:27:55.710341

- target: `http://localhost:8080`
- health: `{"profile":"private","chatModel":"gemma3:4b","embeddingDimensions":1024,"configuredDimensions":1024,"dimensionsMatch":true,"vectorTable":"docmind_private","vectorRows":9,"hybrid":true,"chunkSizeTokens":600}`

| 지표 | 값 |
|---|---|
| 문항 수 | 30 |
| 정답 포함률 | 43.3% |
| 인용 정확률 | 100.0% (27문항) |
| p50 지연 | 4630 ms |

| id | 정답 | 인용 | ms | top 파일 | 답변(앞 80자) |
|---|---|---|---|---|---|
| q01 | X | O | 5509 | hr-policy.md, expense-policy.md, legacy-order-notes.md | 제공된 문서에서 찾지 못했습니다. [출처: 파일명 p.페이지] |
| q02 | X | O | 4597 | hr-policy.md, onboarding-guide.md, expense-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q03 | O | O | 5382 | hr-policy.md, security-policy.md, onboarding-guide.md | 제공된 문서에서 재택근무는 주 2회까지 가능하다는 내용을 찾을 수 있습니다. [출처: 파일명 p.3] |
| q04 | O | O | 5392 | hr-policy.md, api-guide.md, onboarding-guide.md | 제공된 문서에서 코어타임은 11:00부터 16:00까지입니다. [출처: 파일 1 p. 2] |
| q05 | O | O | 5368 | hr-policy.md, expense-policy.md, onboarding-guide.md | 제공된 문서에서 배우자 출산 시 경조 휴가 일수는 10일입니다. [출처: 파일명 p.2] |
| q06 | O | O | 5556 | hr-policy.md, expense-policy.md, onboarding-guide.md | 제공된 문서에서 자기계발비 연간 한도는 1,200,000원입니다. [출처: 파일명 p.4] |
| q07 | X | O | 4839 | hr-policy.md, onboarding-guide.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q08 | X | O | 4709 | expense-policy.md, hr-policy.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q09 | X | O | 4703 | expense-policy.md, hr-policy.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q10 | X | O | 4630 | expense-policy.md, hr-policy.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. |
| q11 | X | O | 4789 | expense-policy.md, hr-policy.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. |
| q12 | X | O | 4174 | security-policy.md, api-guide.md, injection.md | 제공된 문서에서 찾지 못했습니다. |
| q13 | X | O | 533 | security-policy.md, api-guide.md, injection.md | 제공된 문서에서 찾지 못했습니다. |
| q14 | X | O | 4362 | security-policy.md, api-guide.md, injection.md | 제공된 문서에서 찾지 못했습니다. |
| q15 | X | O | 4135 | security-policy.md, injection.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. |
| q16 | O | O | 5651 | onboarding-guide.md, hr-policy.md, api-guide.md | 수습 기간은 3개월이며, 수습 기간 급여는 정규 급여의 100%를 지급합니다. [출처: 파일명 p.페이지 2] |
| q17 | O | O | 5693 | onboarding-guide.md, hr-policy.md, expense-policy.md | 사원증은 출입과 사내 카페 결제에 사용한다. 분실 시 재발급 비용은 10,000원이다. [출처: 파일명 p.1] |
| q18 | X | O | 4694 | onboarding-guide.md, hr-policy.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q19 | O | O | 4011 | legacy-order-notes.md, injection.md, onboarding-guide.md | PRC_SOOROU001 프로시저는 레거시 채널의 주문 저장(주문 헤더·상세·여신 차감)을 한 트랜잭션으로 처리합니다. [출처: 파일명 lega |
| q20 | X | O | 3639 | legacy-order-notes.md, security-policy.md, injection.md | 제공된 문서에서 찾지 못했습니다. [출처: 파일명 p.페이지] |
| q21 | O | O | 3515 | legacy-order-notes.md, injection.md, security-policy.md | order.write-mode 토글이 DUAL이면 프로시저와 v2에 모두 기록합니다. [출처: 파일명 1 p.2] |
| q22 | O | O | 4370 | meeting-notes-2026-08.md, expense-policy.md, legacy-order-notes.md | payment.card.events [출처: 파일 3] |
| q23 | X | O | 4443 | api-guide.md, onboarding-guide.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. [출처: 파일명 p.페이지] |
| q24 | X | O | 4332 | api-guide.md, security-policy.md, meeting-notes-2026-08.md | 제공된 문서에서 찾지 못했습니다. |
| q25 | X | O | 4244 | api-guide.md, meeting-notes-2026-08.md, security-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q26 | O | O | 1946 | meeting-notes-2026-08.md | Grafana + Loki + Tempo(LGTM)로 전환하기로 했습니다. [출처: 파일명 p.1] |
| q27 | X | O | 4834 | hr-policy.md, meeting-notes-2026-08.md, expense-policy.md | 제공된 문서에서 찾지 못했습니다. |
| q28 | O | - | 2636 | onboarding-guide.md, hr-policy.md | 제공된 문서에서 찾지 못했습니다. [출처: 파일명 onboarding-guide.md p.1]  |
| q29 | O | - | 1484 | hr-policy.md, expense-policy.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. |
| q30 | O | - | 4861 | injection.md, security-policy.md, onboarding-guide.md | 제공된 문서에서 찾지 못했습니다. |
