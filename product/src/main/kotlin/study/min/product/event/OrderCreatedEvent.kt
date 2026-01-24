package study.min.product.event

import java.time.Instant

/**
 * 주문 생성 이벤트
 * Order Service에서 Kafka로 발행한 이벤트를 Product Service에서 소비
 */
data class OrderCreatedEvent(
    val orderId: String,
    val productId: Long,
    val quantity: Int,
    val price: Long,
    val customerId: String,
    val timestamp: Instant = Instant.now()
)