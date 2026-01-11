-- 1. 상품 테이블
CREATE TABLE product
(
    id    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '상품 ID',
    name  VARCHAR(255) NOT NULL COMMENT '상품명',
    price INT          NOT NULL COMMENT '가격'
);

-- 2. 재고 테이블 (상품과 1:1 관계)
CREATE TABLE product_stock
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '재고 ID',
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    quantity   BIGINT NOT NULL DEFAULT 0 COMMENT '재고 수량',
    CONSTRAINT fk_product_stock_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
);