package study.min.order.event

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * Kafka 주문 이벤트 발행자
 * Order Service에서 주문 생성 시 Kafka Topic으로 이벤트 발행
 */
@Component
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, OrderCreatedEvent>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TOPIC_ORDER_CREATED = "order-created"
    }
    
    /**
     * 주문 생성 이벤트 발행
     * @param event 주문 생성 이벤트
     * @return CompletableFuture<SendResult> 비동기 전송 결과
     */
    fun publishOrderCreated(event: OrderCreatedEvent): CompletableFuture<SendResult<String, OrderCreatedEvent>> {
        log.info("📤 [Kafka Producer] Publishing order-created event: orderId=${event.orderId}, productId=${event.productId}, quantity=${event.quantity}")

        return kafkaTemplate.send(TOPIC_ORDER_CREATED, event.orderId, event).apply {
            whenComplete { result, exception ->
                if (exception != null) {
                    log.error("❌ [Kafka Producer] Failed to send event: orderId=${event.orderId}", exception)
                } else {
                    val metadata = result?.recordMetadata
                    log.info("✅ [Kafka Producer] Event sent successfully: topic=${metadata?.topic()}, partition=${metadata?.partition()}, offset=${metadata?.offset()}")
                }
            }
        }
    }
}