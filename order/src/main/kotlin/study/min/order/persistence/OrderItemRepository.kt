package study.min.order.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 주문 상품 Repository
 */
@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long> {

    /**
     * 주문 ID로 조회
     */
    fun findByOrderId(orderId: Long): List<OrderItem>

    /**
     * 상품 ID로 조회
     */
    fun findByProductId(productId: Long): List<OrderItem>
}
