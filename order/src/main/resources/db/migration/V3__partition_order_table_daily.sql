-- ============================================================
-- 일별 Range Partitioning 적용 (파티션 키: created_at)
-- order_item FK 제거, 앱 레벨에서 참조 무결성 보장
-- ============================================================

-- 1. order_item FK 제거
ALTER TABLE order_item DROP CONSTRAINT fk_order_item_order;

-- 2. 파티셔닝 테이블 생성
CREATE TABLE order_partitioned (
    id           BIGINT      NOT NULL DEFAULT nextval('order_id_seq'),
    order_number VARCHAR(50) NOT NULL,
    user_id      BIGINT      NOT NULL,
    total_price  BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 3. 인덱스 생성
CREATE UNIQUE INDEX idx_order_part_order_number ON order_partitioned (order_number, created_at);
CREATE INDEX idx_order_part_user_id ON order_partitioned (user_id);
CREATE INDEX idx_order_part_status ON order_partitioned (status);
CREATE INDEX idx_order_part_created_at ON order_partitioned (created_at);

-- 4. 일별 파티션 자동 생성 (2025-01 ~ 2026-02)
DO $$
DECLARE
    start_date DATE := '2026-01-01';
    end_date   DATE := '2026-06-01';
    curr       DATE := start_date;
    part_name  TEXT;
BEGIN
    WHILE curr < end_date LOOP
        part_name := 'order_p' || to_char(curr, 'YYYYMMDD');
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF order_partitioned FOR VALUES FROM (%L) TO (%L)',
            part_name, curr, curr + INTERVAL '1 day'
        );
        curr := curr + INTERVAL '1 day';
    END LOOP;
END $$;

-- DEFAULT 파티션 (범위 밖 데이터 수용)
CREATE TABLE order_default PARTITION OF order_partitioned DEFAULT;

-- 5. 기존 데이터 마이그레이션
INSERT INTO order_partitioned (id, order_number, user_id, total_price, status, created_at, updated_at)
SELECT id, order_number, user_id, total_price, status, created_at, updated_at
FROM "order";

-- 6. 테이블 교체
DROP TRIGGER IF EXISTS update_order_updated_at ON "order";
ALTER TABLE "order" RENAME TO order_old;
ALTER TABLE order_partitioned RENAME TO "order";

-- 7. 시퀀스 소유권을 새 테이블로 이전 (order_old DROP 시 시퀀스가 함께 삭제되지 않도록)
ALTER SEQUENCE order_id_seq OWNED BY "order".id;

-- 8. 트리거 재설정
CREATE TRIGGER update_order_updated_at
    BEFORE UPDATE ON "order"
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 9. 시퀀스 현재 값 동기화
SELECT setval('order_id_seq', COALESCE((SELECT MAX(id) FROM "order"), 0) + 1, false);

-- 10. 기존 테이블 제거
DROP TABLE order_old;

-- 10. 코멘트 재설정
COMMENT ON TABLE "order" IS '주문 (일별 파티셔닝)';
COMMENT ON COLUMN "order".id IS '주문 ID';
COMMENT ON COLUMN "order".order_number IS '주문 번호 (ORDER-{timestamp}-{random})';
COMMENT ON COLUMN "order".user_id IS '사용자 ID';
COMMENT ON COLUMN "order".total_price IS '총 금액';
COMMENT ON COLUMN "order".status IS '주문 상태 (PENDING, CONFIRMED, CANCELLED, FAILED)';
COMMENT ON COLUMN "order".created_at IS '주문 생성 시간 (파티션 키)';
COMMENT ON COLUMN "order".updated_at IS '주문 수정 시간';