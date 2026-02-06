package study.min.order.bigquery

import java.time.Instant

/**
 * BigQuery에 저장할 주문 데이터 DTO
 */
data class OrderBigQueryDto(
    val orderId: String,
    val orderNumber: String,
    val userId: Long,
    val productId: Long,
    val quantity: Int,
    val price: Long,
    val totalPrice: Long,
    val status: String,
    val createdAt: Instant = Instant.now()
)
