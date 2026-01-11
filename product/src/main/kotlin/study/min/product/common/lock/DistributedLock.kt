package study.min.product.common.lock

import java.util.concurrent.TimeUnit

/**
 * 분산 락을 선언적으로 사용하기 위한 어노테이션
 *
 * 사용 예시:
 * ```
 * @DistributedLock(key = "#productId", waitTime = 10, leaseTime = 5)
 * fun decrease(productId: Long, quantity: Int) {
 *     // 비즈니스 로직
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    /**
     * Lock의 키 (SpEL 표현식 지원)
     * 예: "stock:#productId", "#user.id"
     */
    val key: String,

    /**
     * Lock 획득을 기다리는 시간 (초)
     * 기본값: 10초
     */
    val waitTime: Long = 10,

    /**
     * Lock을 점유하는 최대 시간 (초)
     * 기본값: 5초
     */
    val leaseTime: Long = 5,

    /**
     * 시간 단위
     * 기본값: 초
     */
    val timeUnit: TimeUnit = TimeUnit.SECONDS,

    /**
     * Lock 획득 실패 시 에러 메시지
     */
    val errorMessage: String = "Lock 획득에 실패했습니다."
)