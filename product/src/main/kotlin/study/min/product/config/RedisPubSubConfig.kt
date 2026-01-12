package study.min.product.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.serializer.RedisSerializer

/**
 * Redis Pub/Sub 설정
 */
@Configuration
class RedisPubSubConfig {

    companion object {
        // Channel 이름 상수
        const val STOCK_DECREASED_CHANNEL = "stock:decreased"
        const val STOCK_INCREASED_CHANNEL = "stock:increased"
        const val STOCK_LOW_WARNING_CHANNEL = "stock:low-warning"
        const val STOCK_OUT_CHANNEL = "stock:out"
    }

    /**
     * Redis 메시지 리스너 컨테이너
     * - Pub/Sub 구독을 관리하는 컨테이너
     */
    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory
    ): RedisMessageListenerContainer {
        return RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
        }
    }

    /**
     * 재고 차감 이벤트 채널
     */
    @Bean
    fun stockDecreasedTopic(): ChannelTopic {
        return ChannelTopic(STOCK_DECREASED_CHANNEL)
    }

    /**
     * 재고 증가 이벤트 채널
     */
    @Bean
    fun stockIncreasedTopic(): ChannelTopic {
        return ChannelTopic(STOCK_INCREASED_CHANNEL)
    }

    /**
     * 재고 부족 경고 채널
     */
    @Bean
    fun stockLowWarningTopic(): ChannelTopic {
        return ChannelTopic(STOCK_LOW_WARNING_CHANNEL)
    }

    /**
     * 재고 소진 채널
     */
    @Bean
    fun stockOutTopic(): ChannelTopic {
        return ChannelTopic(STOCK_OUT_CHANNEL)
    }

    /**
     * 이벤트 발행용 RedisTemplate
     * - JSON 직렬화 사용 (RedisSerializer.json())
     * - RedisConfig와 동일한 방식으로 직렬화/역직렬화
     */
    @Bean
    fun eventRedisTemplate(
        connectionFactory: RedisConnectionFactory
    ): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = RedisSerializer.string()
            valueSerializer = RedisSerializer.json()
        }
    }
}