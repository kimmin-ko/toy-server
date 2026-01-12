package study.min.product.event

import jakarta.annotation.PostConstruct
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.stereotype.Component

/**
 * Redis Pub/Sub 재고 이벤트 구독자
 */
@Component
class StockEventSubscriber(
    private val redisMessageListenerContainer: RedisMessageListenerContainer,
    private val stockDecreasedTopic: ChannelTopic,
    private val stockIncreasedTopic: ChannelTopic,
    private val stockLowWarningTopic: ChannelTopic,
    private val stockOutTopic: ChannelTopic
) {

    // RedisSerializer.json()을 사용하여 역직렬화 (RedisConfig와 동일한 방식)
    private val jsonSerializer = RedisSerializer.json()

    /**
     * 애플리케이션 시작 시 구독 등록
     */
    @PostConstruct
    fun subscribeToStockEvents() {
        // 재고 차감 이벤트 구독
        redisMessageListenerContainer.addMessageListener(
            StockDecreasedListener(jsonSerializer),
            stockDecreasedTopic
        )

        // 재고 증가 이벤트 구독
        redisMessageListenerContainer.addMessageListener(
            StockIncreasedListener(jsonSerializer),
            stockIncreasedTopic
        )

        // 재고 부족 경고 구독
        redisMessageListenerContainer.addMessageListener(
            StockLowWarningListener(jsonSerializer),
            stockLowWarningTopic
        )

        // 재고 소진 구독
        redisMessageListenerContainer.addMessageListener(
            StockOutListener(jsonSerializer),
            stockOutTopic
        )

        println("✅ Redis Pub/Sub 구독 시작됨")
    }
}

/**
 * 재고 차감 이벤트 리스너
 */
class StockDecreasedListener(
    private val jsonSerializer: RedisSerializer<Any>
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val event = jsonSerializer.deserialize(message.body) as StockDecreasedEvent
        handleStockDecreased(event)
    }

    private fun handleStockDecreased(event: StockDecreasedEvent) {
        println("📦 [Received] 재고 차감 이벤트")
        println("   - 상품 ID: ${event.productId}")
        println("   - 차감량: ${event.decreasedQuantity}")
        println("   - 잔여 재고: ${event.remainingStock}")
        println("   - 주문 ID: ${event.orderId ?: "N/A"}")
        println("   - 시간: ${event.timestamp}")

        // 여기에 비즈니스 로직 추가
        // 예: 알림 발송, 로그 기록, 분석 데이터 수집 등
    }
}

/**
 * 재고 증가 이벤트 리스너
 */
class StockIncreasedListener(
    private val jsonSerializer: RedisSerializer<Any>
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val event = jsonSerializer.deserialize(message.body) as StockIncreasedEvent
        handleStockIncreased(event)
    }

    private fun handleStockIncreased(event: StockIncreasedEvent) {
        println("📥 [Received] 재고 증가 이벤트")
        println("   - 상품 ID: ${event.productId}")
        println("   - 증가량: ${event.increasedQuantity}")
        println("   - 잔여 재고: ${event.remainingStock}")
        println("   - 사유: ${event.reason}")
        println("   - 시간: ${event.timestamp}")
    }
}

/**
 * 재고 부족 경고 리스너
 */
class StockLowWarningListener(
    private val jsonSerializer: RedisSerializer<Any>
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val event = jsonSerializer.deserialize(message.body) as StockLowWarningEvent
        handleStockLowWarning(event)
    }

    private fun handleStockLowWarning(event: StockLowWarningEvent) {
        println("⚠️ [Received] 재고 부족 경고!")
        println("   - 상품 ID: ${event.productId}")
        println("   - 현재 재고: ${event.currentStock}")
        println("   - 임계값: ${event.threshold}")
        println("   - 시간: ${event.timestamp}")

        // 여기에 비즈니스 로직 추가
        // 예: 관리자에게 이메일/SMS 발송, Slack 알림 등
    }
}

/**
 * 재고 소진 리스너
 */
class StockOutListener(
    private val jsonSerializer: RedisSerializer<Any>
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val event = jsonSerializer.deserialize(message.body) as StockOutEvent
        handleStockOut(event)
    }

    private fun handleStockOut(event: StockOutEvent) {
        println("🚨 [Received] 재고 소진!")
        println("   - 상품 ID: ${event.productId}")
        println("   - 시간: ${event.timestamp}")

        // 여기에 비즈니스 로직 추가
        // 예: 긴급 알림, 자동 발주 시스템 트리거 등
    }
}