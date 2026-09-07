# Order Service v2 인터페이스 명세

## 개요
- 레거시 프로시저 `PRC_SOOROU001`을 대체하는 주문 저장 서비스이며, 2026년 6월부터 듀얼라이트로 운영 중이다.
- 피처토글 `order.write-mode` 값이 `DUAL`이면 프로시저와 v2에 모두 기록하고, `V2`면 v2에만 기록한다.

## 엔드포인트
- `POST /v2/orders` — 주문 생성. 요청 헤더에 `Idempotency-Key`(UUID 36자) 필수.
- `GET /v2/orders/{orderNo}` — 주문 조회.
- `POST /v2/orders/{orderNo}/cancel` — 취소. 결제 승인 이후에는 취소 대신 환불 흐름으로 전환된다.

## 규칙
- 주문번호 형식은 `ORD-YYYYMMDD-NNNNNN` 으로 레거시와 동일하다.
- 동일 `Idempotency-Key` 재요청은 최초 응답을 그대로 반환한다(E4090).
- 여신 한도 초과는 E4012, 재고 부족은 E4020으로 응답한다.
- 컷오버 후 6주간 v1/v2 주문이 공존하며 대사 배치로 검증한다.
