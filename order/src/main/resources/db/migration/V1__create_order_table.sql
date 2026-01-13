-- 주문 테이블
CREATE TABLE `order`
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '주문 ID',
    order_number VARCHAR(50) NOT NULL UNIQUE COMMENT '주문 번호 (ORDER-{timestamp}-{random})',
    user_id      BIGINT      NOT NULL COMMENT '사용자 ID',
    total_price  BIGINT      NOT NULL COMMENT '총 금액',
    status       VARCHAR(20) NOT NULL COMMENT '주문 상태 (PENDING, CONFIRMED, CANCELLED)',
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '주문 생성 시간',
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '주문 수정 시간',
    INDEX idx_order_number (order_number),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='주문';

-- 주문 상품 테이블 (Order : OrderItem = 1 : N)
CREATE TABLE order_item
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '주문 상품 ID',
    order_id   BIGINT      NOT NULL COMMENT '주문 ID',
    product_id BIGINT      NOT NULL COMMENT '상품 ID',
    quantity   INT         NOT NULL COMMENT '주문 수량',
    price      BIGINT      NOT NULL COMMENT '단가 (주문 당시 가격)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시간',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시간',
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES `order` (id) ON DELETE CASCADE,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='주문 상품';
