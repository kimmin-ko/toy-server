package study.min.order.event

import java.time.Instant

/**
 * 주문 생성 이벤트
 * Order Service에서 Kafka로 발행하여 Product Service에서 소비
 */
data class OrderCreatedEvent(
    val orderId: String,
    val productId: Long,
    val quantity: Int,
    val price: Long,
    val customerId: String,
    val timestamp: Instant = Instant.now()
)
