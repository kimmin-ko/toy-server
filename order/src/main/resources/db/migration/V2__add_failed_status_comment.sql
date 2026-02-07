-- FAILED 상태 추가 반영 (VARCHAR 컬럼이므로 DDL 변경 불필요, 코멘트만 업데이트)
COMMENT ON COLUMN "order".status IS '주문 상태 (PENDING, CONFIRMED, CANCELLED, FAILED)';
