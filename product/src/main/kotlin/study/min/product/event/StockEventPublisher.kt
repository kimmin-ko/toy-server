package study.min.product.event

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.stereotype.Component

/**
 * Redis Pub/Sub을 사용한 재고 이벤트 발행자
 */
@Component
class StockEventPublisher(
    private val eventRedisTemplate: RedisTemplate<String, Any>,
    private val stockDecreasedTopic: ChannelTopic,
    private val stockIncreasedTopic: ChannelTopic,
    private val stockLowWarningTopic: ChannelTopic,
    private val stockOutTopic: ChannelTopic
) {

    /**
     * 재고 차감 이벤트 발행
     */
    fun publishStockDecreased(event: StockDecreasedEvent) {
        eventRedisTemplate.convertAndSend(stockDecreasedTopic.topic, event)
        println("📢 [Published] 재고 차감: 상품 ID=${event.productId}, 차감량=${event.decreasedQuantity}, 잔여=${event.remainingStock}")
    }

    /**
     * 재고 증가 이벤트 발행
     */
    fun publishStockIncreased(event: StockIncreasedEvent) {
        eventRedisTemplate.convertAndSend(stockIncreasedTopic.topic, event)
        println("📢 [Published] 재고 증가: 상품 ID=${event.productId}, 증가량=${event.increasedQuantity}, 잔여=${event.remainingStock}")
    }

    /**
     * 재고 부족 경고 이벤트 발행
     */
    fun publishStockLowWarning(event: StockLowWarningEvent) {
        eventRedisTemplate.convertAndSend(stockLowWarningTopic.topic, event)
        println("⚠️ [Published] 재고 부족 경고: 상품 ID=${event.productId}, 현재 재고=${event.currentStock}, 임계값=${event.threshold}")
    }

    /**
     * 재고 소진 이벤트 발행
     */
    fun publishStockOut(event: StockOutEvent) {
        eventRedisTemplate.convertAndSend(stockOutTopic.topic, event)
        println("🚨 [Published] 재고 소진: 상품 ID=${event.productId}")
    }
}