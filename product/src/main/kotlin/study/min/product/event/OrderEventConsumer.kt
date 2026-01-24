package study.min.product.event

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import study.min.product.service.product.ProductStockService
import java.util.concurrent.ConcurrentHashMap

/**
 * Kafka 주문 이벤트 소비자
 * Order Service에서 발행한 주문 생성 이벤트를 소비하여 비즈니스 로직 처리
 *
 * 재시도 테스트를 위한 기능:
 * - retryCount를 추적하여 재시도 횟수 확인
 * - 특정 조건에서 의도적으로 예외 발생 (재시도 테스트)
 */
@Component
class OrderEventConsumer(
    private val productStockService: ProductStockService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 재시도 횟수 추적 (테스트용)
    private val retryCountMap = ConcurrentHashMap<String, Int>()

    /**
     * 주문 생성 이벤트 소비
     * @throws IllegalStateException 재시도 테스트를 위한 의도적 예외
     */
    @KafkaListener(
        topics = ["order-created"],
        groupId = "product-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeOrderCreated(
        @Payload event: OrderCreatedEvent,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        val currentRetryCount = retryCountMap.compute(event.orderId) { _, count -> (count ?: 0) + 1 }!!

        log.info(
            "📥 [Kafka Consumer] Received order-created event (attempt $currentRetryCount): " +
                    "orderId=${event.orderId}, productId=${event.productId}, quantity=${event.quantity}, " +
                    "topic=$topic, partition=$partition, offset=$offset"
        )

        try {
            // 비즈니스 로직 처리
            processOrderCreated(event, currentRetryCount)

            // 성공 시 재시도 카운터 제거
            retryCountMap.remove(event.orderId)
            log.info("✅ [Kafka Consumer] Successfully processed order-created event: orderId=${event.orderId}")

        } catch (e: Exception) {
            log.error("❌ [Kafka Consumer] Failed to process order-created event (attempt $currentRetryCount): orderId=${event.orderId}", e)
            throw e // 재시도를 위해 예외를 다시 던짐
        }
    }

    /**
     * DLT(Dead Letter Topic) 이벤트 소비
     * 모든 재시도가 실패한 후 DLT로 전송된 메시지 처리
     */
    @KafkaListener(
        topics = ["order-created.DLT"],
        groupId = "product-service-dlt-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeOrderCreatedDLT(
        @Payload event: OrderCreatedEvent,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String
    ) {
        log.error(
            "💀 [Kafka Consumer DLT] Received failed event in DLT: " +
                    "orderId=${event.orderId}, productId=${event.productId}, topic=$topic"
        )

        // DLT 메시지 처리 로직 (알림, 수동 처리 대기, DB 저장 등)
        // 운영 환경에서는 별도 알림 시스템 또는 관리자 대시보드로 전송
        retryCountMap.remove(event.orderId)
    }

    /**
     * 비즈니스 로직 처리
     * 재시도 테스트를 위해 특정 조건에서 의도적으로 예외 발생
     */
    private fun processOrderCreated(event: OrderCreatedEvent, retryCount: Int) {
        // 테스트용: 재시도 테스트를 위한 의도적 실패 로직
        // orderId에 "FAIL"이 포함되어 있고, 재시도 횟수가 3회 미만이면 예외 발생
        if (event.orderId.contains("FAIL", ignoreCase = true) && retryCount <= 3) {
            log.warn("⚠️ [Kafka Consumer] Simulating business logic failure for testing (attempt $retryCount)")
            throw IllegalStateException("Simulated failure for retry testing (attempt $retryCount)")
        }

        // 실제 비즈니스 로직: 재고 조회 및 로깅
        // 주문 생성 시 이미 재고 차감이 완료되었으므로, 여기서는 추가 검증 또는 로깅만 수행
        log.info("🔍 [Kafka Consumer] Processing order: orderId=${event.orderId}, productId=${event.productId}")

        // 예시: 재고 상태 확인
        val stock = productStockService.getStock(event.productId)
        log.info("📦 [Kafka Consumer] Current stock after order: productId=${event.productId}, stock=${stock.quantity}")

        // 추가 비즈니스 로직 (분석, 알림, 외부 시스템 연동 등)
        // 예: 재고가 특정 수준 이하로 떨어지면 알림
        if (stock.quantity < 10) {
            log.warn("⚠️ [Kafka Consumer] Low stock alert: productId=${event.productId}, stock=${stock.quantity}")
        }
    }

    /**
     * 재시도 카운터 조회 (테스트용)
     */
    fun getRetryCount(orderId: String): Int {
        return retryCountMap[orderId] ?: 0
    }

    /**
     * 재시도 카운터 초기화 (테스트용)
     */
    fun clearRetryCount() {
        retryCountMap.clear()
    }
}