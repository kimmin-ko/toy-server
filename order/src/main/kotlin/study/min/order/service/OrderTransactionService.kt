package study.min.order.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import study.min.order.dto.CreateOrderRequest
import study.min.order.persistence.*

/**
 * 주문 트랜잭션 서비스
 *
 * OrderService에서 self-invocation 시 @Transactional AOP 프록시가 동작하지 않으므로,
 * 짧은 트랜잭션 단위의 DB 작업을 별도 Bean으로 분리.
 */
@Service
class OrderTransactionService(
    private val orderRepository: OrderRepository,
) {

    @Transactional
    fun savePendingOrder(request: CreateOrderRequest, userId: Long, price: Long): Order {
        val order = Order().apply {
            this.orderNumber = generateOrderNumber()
            this.userId = userId
            this.status = OrderStatus.PENDING
        }

        val orderItem = OrderItem().apply {
            this.productId = request.productId
            this.quantity = request.quantity
            this.price = price
        }

        order.addOrderItem(orderItem)
        order.calculateTotalPrice()

        return orderRepository.save(order)
    }

    @Transactional
    fun updateOrderStatus(orderId: Long, status: OrderStatus) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalStateException("주문을 찾을 수 없습니다: $orderId") }
        order.status = status
    }

    private fun generateOrderNumber(): String {
        val timestamp = java.time.LocalDateTime.now()
        val uuid = java.util.UUID.randomUUID().toString().substring(0, 8)
        return "ORDER-${timestamp.year}${
            timestamp.monthValue.toString().padStart(2, '0')
        }${timestamp.dayOfMonth.toString().padStart(2, '0')}-${System.currentTimeMillis()}-$uuid"
    }
}
