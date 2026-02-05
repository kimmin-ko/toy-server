-- 주문 테이블
CREATE TABLE "order"
(
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    user_id      BIGINT      NOT NULL,
    total_price  BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE "order" IS '주문';
COMMENT ON COLUMN "order".id IS '주문 ID';
COMMENT ON COLUMN "order".order_number IS '주문 번호 (ORDER-{timestamp}-{random})';
COMMENT ON COLUMN "order".user_id IS '사용자 ID';
COMMENT ON COLUMN "order".total_price IS '총 금액';
COMMENT ON COLUMN "order".status IS '주문 상태 (PENDING, CONFIRMED, CANCELLED)';
COMMENT ON COLUMN "order".created_at IS '주문 생성 시간';
COMMENT ON COLUMN "order".updated_at IS '주문 수정 시간';

CREATE INDEX idx_order_order_number ON "order" (order_number);
CREATE INDEX idx_order_user_id ON "order" (user_id);
CREATE INDEX idx_order_status ON "order" (status);
CREATE INDEX idx_order_created_at ON "order" (created_at);

-- 주문 상품 테이블 (Order : OrderItem = 1 : N)
CREATE TABLE order_item
(
    id BIGSERIAL PRIMARY KEY,
    order_id   BIGINT    NOT NULL,
    product_id BIGINT    NOT NULL,
    quantity   INT       NOT NULL,
    price      BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES "order" (id) ON DELETE CASCADE
);

COMMENT ON TABLE order_item IS '주문 상품';
COMMENT ON COLUMN order_item.id IS '주문 상품 ID';
COMMENT ON COLUMN order_item.order_id IS '주문 ID';
COMMENT ON COLUMN order_item.product_id IS '상품 ID';
COMMENT ON COLUMN order_item.quantity IS '주문 수량';
COMMENT ON COLUMN order_item.price IS '단가 (주문 당시 가격)';
COMMENT ON COLUMN order_item.created_at IS '생성 시간';
COMMENT ON COLUMN order_item.updated_at IS '수정 시간';

CREATE INDEX idx_order_item_order_id ON order_item (order_id);
CREATE INDEX idx_order_item_product_id ON order_item (product_id);

-- updated_at 자동 갱신 트리거
CREATE
OR
REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

CREATE TRIGGER update_order_updated_at
    BEFORE UPDATE
    ON "order"
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_order_item_updated_at
    BEFORE UPDATE
    ON order_item
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();