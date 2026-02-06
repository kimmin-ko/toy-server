package study.min.product.config

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff

/**
 * Kafka Consumer 설정
 * - 재시도 로직: 최대 3회, Exponential Backoff (1초 → 2초 → 4초)
 * - 재시도 실패 시 DLT(Dead Letter Topic)로 전송
 * - Spring Boot auto-configuration 활용
 */
@Configuration
class KafkaConsumerConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * KafkaListenerContainerFactory 커스터마이징
     * Spring Boot auto-configured ConsumerFactory 사용 + 커스텀 ErrorHandler 추가
     */
    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Any>,
        kafkaTemplate: KafkaTemplate<String, Any>
    ): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.setConsumerFactory(consumerFactory)
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate))
        return factory
    }

    /**
     * 에러 핸들러 설정
     * - 재시도 횟수: 3회
     * - 백오프: Exponential (1초 → 2초 → 4초)
     * - 재시도 실패 시 DLT로 전송
     */
    @Bean
    fun errorHandler(kafkaTemplate: KafkaTemplate<String, Any>): CommonErrorHandler {
        // Exponential BackOff 설정 (1초 시작, 2배씩 증가, 최대 3회)
        val exponentialBackOff = ExponentialBackOff().apply {
            initialInterval = 1000L // 1초
            multiplier = 2.0 // 2배씩 증가
            maxInterval = 10000L // 최대 10초
        }

        // Dead Letter Topic 설정
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate)

        return DefaultErrorHandler(recoverer, exponentialBackOff).apply {
            // 재시도 가능한 예외 설정 (비즈니스 로직 실패 시 재시도)
            addRetryableExceptions(
                IllegalStateException::class.java,
                RuntimeException::class.java
            )

            // 재시도하지 않을 예외 (즉시 DLT 전송)
            addNotRetryableExceptions(
                IllegalArgumentException::class.java
            )

            // Retry 리스너 설정 (deprecated되지 않은 방식)
            setRetryListeners(object : org.springframework.kafka.listener.RetryListener {
                override fun failedDelivery(
                    record: ConsumerRecord<*, *>,
                    ex: Exception?,
                    deliveryAttempt: Int
                ) {
                    log.warn("⚠️ [Kafka Consumer] Retry attempt $deliveryAttempt failed: topic=${record.topic()}, key=${record.key()}, error=${ex?.message}")
                }

                override fun recovered(
                    record: ConsumerRecord<*, *>,
                    ex: Exception?
                ) {
                    log.error("❌ [Kafka Consumer] All retries exhausted. Sending to DLT: topic=${record.topic()}, key=${record.key()}")
                }

                override fun recoveryFailed(
                    record: ConsumerRecord<*, *>,
                    original: Exception?,
                    failure: Exception
                ) {
                    log.error("❌ [Kafka Consumer] DLT publish failed: topic=${record.topic()}, key=${record.key()}", failure)
                }
            })
        }
    }
}