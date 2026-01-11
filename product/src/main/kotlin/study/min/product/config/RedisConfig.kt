package study.min.product.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer

@Configuration
class RedisConfig {

    /**
     * RedisTemplate 설정
     *
     * 선택지:
     * 1. RedisSerializer.json() - 타입 정보 없음 (간단, deprecated 아님)
     * 2. GenericJackson2JsonRedisSerializer() - 타입 정보 포함 (deprecated)
     *
     * 현재 선택: RedisSerializer.json()
     * - 장점: deprecated 아님, 간단함, 보안상 안전
     * - 단점: 타입 정보(@class)를 포함하지 않음
     *   → 조회 시 타입을 명시해야 함: redisService.get<User>(key)
     *   → 복잡한 객체는 LinkedHashMap으로 역직렬화됨
     *
     * 참고: Kotlin data class를 역직렬화하려면 @JsonCreator, @JsonProperty 필요
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            this.connectionFactory = connectionFactory

            // Key는 String으로 직렬화
            keySerializer = RedisSerializer.string()
            hashKeySerializer = RedisSerializer.string()

            // Value는 JSON으로 직렬화 (타입 정보 없음)
            // Spring Boot가 제공하는 기본 JSON serializer 사용
            valueSerializer = RedisSerializer.json()
            hashValueSerializer = RedisSerializer.json()
        }
    }
}