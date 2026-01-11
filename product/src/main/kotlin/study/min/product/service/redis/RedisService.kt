package study.min.product.service.redis

import lombok.RequiredArgsConstructor
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@RequiredArgsConstructor
@Service
class RedisService(
    private val redisTemplate: RedisTemplate<String, Any>
) {

    /**
     * Redis에 데이터 저장
     */
    fun put(key: String, value: Any) {
        redisTemplate.opsForValue()[key] = value
    }

    /**
     * Redis에 데이터 저장 (TTL 포함)
     */
    fun put(key: String, value: Any, ttl: Duration) {
        redisTemplate.opsForValue()[key, value] = ttl
    }

    /**
     * Redis에서 데이터 조회 (타입 지정)
     *
     * 참고:
     * - @Service 클래스는 kotlin-spring plugin에 의해 자동으로 open이 되므로
     *   inline fun 사용 불가 (inline은 final 메서드에서만 가능)
     * - 제네릭 타입으로 조회: redisService.get<User>(key)
     * - Any 타입으로 조회: redisService.get<Any>(key)
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        return redisTemplate.opsForValue()[key] as? T
    }

    /**
     * Redis에서 데이터 삭제
     */
    fun delete(key: String): Boolean {
        return redisTemplate.delete(key)
    }

    /**
     * 키 존재 여부 확인
     */
    fun hasKey(key: String): Boolean {
        return redisTemplate.hasKey(key)
    }

    /**
     * TTL 설정
     */
    fun expire(key: String, ttl: Duration): Boolean {
        return redisTemplate.expire(key, ttl)
    }

    /**
     * Hash 데이터 저장
     */
    fun putHash(key: String, hashKey: String, value: Any) {
        redisTemplate.opsForHash<String, Any>().put(key, hashKey, value)
    }

    /**
     * Hash 데이터 조회
     */
    fun getHash(key: String, hashKey: String): Any? {
        return redisTemplate.opsForHash<String, Any>()[key, hashKey]
    }

    /**
     * Hash 전체 조회
     */
    fun getAllHash(key: String): Map<String, Any> {
        return redisTemplate.opsForHash<String, Any>().entries(key)
    }

    /**
     * Hash 데이터 삭제
     */
    fun deleteHash(key: String, hashKey: String): Long {
        return redisTemplate.opsForHash<String, Any>().delete(key, hashKey)
    }

    /**
     * Hash 필드 값 증가 (Atomic 연산)
     * - Thread-Safe: 동시성 문제 없음
     * - 재고 감소, 조회수 증가 등에 사용
     */
    fun incrementHash(key: String, hashKey: String, delta: Long): Long {
        return redisTemplate.opsForHash<String, Any>().increment(key, hashKey, delta)
    }

    /**
     * Hash 필드 값 감소 (Atomic 연산)
     */
    fun decrementHash(key: String, hashKey: String, delta: Long): Long {
        return incrementHash(key, hashKey, -delta)
    }
}