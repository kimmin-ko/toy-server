package study.min.product.event

import java.time.LocalDateTime

/**
 * 재고 변경 이벤트
 */
sealed class StockEvent(
    open val productId: Long,
    open val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 재고 차감 이벤트
 */
data class StockDecreasedEvent(
    override val productId: Long,
    val decreasedQuantity: Int,
    val remainingStock: Int,
    val orderId: String? = null,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : StockEvent(productId, timestamp)

/**
 * 재고 증가 이벤트 (입고, 취소 등)
 */
data class StockIncreasedEvent(
    override val productId: Long,
    val increasedQuantity: Int,
    val remainingStock: Int,
    val reason: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : StockEvent(productId, timestamp)

/**
 * 재고 부족 경고 이벤트
 */
data class StockLowWarningEvent(
    override val productId: Long,
    val currentStock: Int,
    val threshold: Int,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : StockEvent(productId, timestamp)

/**
 * 재고 소진 이벤트
 */
data class StockOutEvent(
    override val productId: Long,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : StockEvent(productId, timestamp)