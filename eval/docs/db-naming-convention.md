# 데이터베이스 명명 규칙

## 테이블
- 테이블명은 소문자 스네이크케이스 복수형을 쓴다: `orders`, `order_items`, `payment_events`.
- 연결 테이블은 두 테이블명을 알파벳 순으로 잇는다: `product_tags`.

## 컬럼
- 기본키는 `id`, 외래키는 `<단수 테이블명>_id` 형식이다: `order_id`.
- 시각 컬럼은 `_at` 접미사와 `timestamptz` 타입을 쓴다: `created_at`, `updated_at`, `deleted_at`.
- 불리언은 `is_`/`has_` 접두사를 쓴다: `is_active`.
- 금액은 `numeric(18,2)`, 통화는 별도 `currency` 컬럼(ISO 4217)에 둔다.

## 인덱스와 제약
- 인덱스명은 `idx_<테이블>_<컬럼들>`, 유니크는 `uq_`, 외래키는 `fk_` 접두사를 쓴다.
- 운영 테이블에 물리 삭제를 하지 않는다. `deleted_at`을 채우는 논리 삭제를 기본으로 한다.
- 마이그레이션 도구는 Flyway로 통일하며 파일명은 `V<번호>__<설명>.sql` 형식이다.
