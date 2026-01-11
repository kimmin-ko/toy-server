package study.min.product.common.lock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.redisson.api.RedissonClient
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component
import study.min.product.exception.AppException
import study.min.product.exception.AppExceptionCode

/**
 * @DistributedLock 어노테이션을 처리하는 AOP Aspect
 *
 * @Order(Ordered.HIGHEST_PRECEDENCE)로 설정하여
 * @Transactional보다 먼저 실행되도록 보장
 *
 * 실행 순서:
 * 1. DistributedLockAspect (Lock 획득)
 * 2. TransactionAspect (트랜잭션 시작)
 * 3. 비즈니스 로직
 * 4. TransactionAspect (트랜잭션 커밋)
 * 5. DistributedLockAspect (Lock 해제)
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class DistributedLockAspect(
    private val redissonClient: RedissonClient
) {

    private val parser: ExpressionParser = SpelExpressionParser()

    @Around("@annotation(study.min.product.common.lock.DistributedLock)")
    fun around(joinPoint: ProceedingJoinPoint): Any? {
        // 메서드에서 @DistributedLock 어노테이션 가져오기
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val distributedLock = method.getAnnotation(DistributedLock::class.java)
            ?: throw IllegalStateException("@DistributedLock annotation not found")

        val lockKey = getLockKey(joinPoint, distributedLock)
        val lock = redissonClient.getLock(lockKey)

        try {
            val available = lock.tryLock(
                distributedLock.waitTime,
                distributedLock.leaseTime,
                distributedLock.timeUnit
            )

            if (!available) {
                throw AppException(
                    AppExceptionCode.PRODUCT_STOCK_02,
                    distributedLock.errorMessage
                )
            }

            return joinPoint.proceed()
        } finally {
            if (lock.isLocked && lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    /**
     * SpEL을 사용하여 Lock 키를 동적으로 생성
     *
     * 예시:
     * ```kotlin
     * @DistributedLock(key = "'stock:' + #productId")
     * fun decrease(productId: Long, quantity: Int)
     * ```
     *
     * 실행 과정:
     * 1. decrease(123, 10) 호출
     * 2. parameterNames = ["productId", "quantity"]
     * 3. args = [123, 10]
     * 4. SpEL Context에 변수 등록:
     *    - productId = 123
     *    - quantity = 10
     * 5. SpEL 표현식 "'stock:' + #productId" 평가
     *    - 'stock:' (문자열 리터럴)
     *    - + (연결 연산자)
     *    - #productId (변수 참조 → 123)
     * 6. 결과: "stock:123"
     *
     * 다른 예시:
     * - "'user:' + #userId" → "user:456"
     * - "'cart:' + #user.id + ':' + #productId" → "cart:789:123"
     */
    private fun getLockKey(joinPoint: ProceedingJoinPoint, distributedLock: DistributedLock): String {
        val signature = joinPoint.signature as MethodSignature
        val parameterNames = signature.parameterNames  // ["productId", "quantity"]
        val args = joinPoint.args                      // [123, 10]

        // SpEL Evaluation Context 생성 (변수를 담는 컨테이너)
        val context = StandardEvaluationContext()

        // 메서드 파라미터를 SpEL 변수로 등록
        // 예: productId=123, quantity=10
        for (i in parameterNames.indices) {
            context.setVariable(parameterNames[i], args[i])
        }

        // SpEL 표현식 파싱 및 평가
        // "'stock:' + #productId" → "stock:123"
        val expression = parser.parseExpression(distributedLock.key)
        return expression.getValue(context, String::class.java)
            ?: throw IllegalArgumentException("Lock key cannot be null")
    }
}