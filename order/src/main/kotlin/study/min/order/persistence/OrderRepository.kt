package study.min.order.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 주문 Repository
 */
@Repository
interface OrderRepository : JpaRepository<Order, Long> {

    /**
     * 주문 번호로 조회
     */
    fun findByOrderNumber(orderNumber: String): Order?

    /**
     * 사용자 ID로 조회
     */
    fun findByUserId(userId: Long): List<Order>
}
