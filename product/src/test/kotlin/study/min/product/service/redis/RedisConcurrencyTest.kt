package study.min.product.service.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import study.min.product.ProductApplication
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

/**
 * Redis 동시성 제어 테스트
 */
@SpringBootTest(classes = [ProductApplication::class])
class RedisConcurrencyTest {

    @Autowired
    private lateinit var redisService: RedisService

    @DisplayName("동시성 문제 재현 - Race Condition (잘못된 방법)")
    @Test
    fun raceConditionProblem() {
        // given - 초기 재고 1000개
        val key = "test:stock:unsafe"
        redisService.putHash(key, "count", 1000)

        val threadCount = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // when - 100개 스레드가 각각 10개씩 감소 시도
        repeat(threadCount) {
            executor.submit {
                try {
                    // ❌ 비원자적 연산 (Race Condition 발생)
                    val current = redisService.getHash(key, "count") as Int
                    Thread.sleep(1) // 동시성 문제를 더 명확하게 재현
                    redisService.putHash(key, "count", current - 10)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then - 기대값: 0 (1000 - 100*10)
        // 실제값: 0보다 큼 (Race Condition으로 인한 손실)
        val finalCount = redisService.getHash(key, "count") as Int
        println("❌ Race Condition 결과: $finalCount (기대값: 0)")
        assertThat(finalCount).isGreaterThan(0)  // Race Condition 발생 확인
    }

    @DisplayName("동시성 안전 - Atomic 연산 (올바른 방법)")
    @Test
    fun atomicOperationSafe() {
        // given - 초기 재고 1000개
        val key = "test:stock:safe"
        redisService.putHash(key, "count", 1000)

        val threadCount = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // when - 100개 스레드가 각각 10개씩 감소 시도
        val elapsedTime = measureTimeMillis {
            repeat(threadCount) {
                executor.submit {
                    try {
                        // ✅ Atomic 연산 사용 (Thread-Safe)
                        redisService.decrementHash(key, "count", 10)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
        }
        executor.shutdown()

        // then - 정확히 0이 됨
        val finalCount = redisService.getHash(key, "count") as Int
        println("✅ Atomic 연산 결과: $finalCount (기대값: 0, 소요시간: ${elapsedTime}ms)")
        assertThat(finalCount).isEqualTo(0)
    }

    @DisplayName("실전 시나리오: 재고 차감 (1000명 동시 구매)")
    @Test
    fun inventoryDeduction() {
        // given - 상품 재고 1000개
        val productKey = "product:12345:inventory"
        redisService.putHash(productKey, "stock", 1000)
        redisService.putHash(productKey, "sold", 0)

        val customerCount = 1000
        val purchaseQuantity = 1L
        val executor = Executors.newFixedThreadPool(50)
        val latch = CountDownLatch(customerCount)

        // when - 1000명이 동시에 1개씩 구매 시도
        repeat(customerCount) { customerId ->
            executor.submit {
                try {
                    // ✅ Atomic 연산으로 재고 차감
                    val remainingStock = redisService.decrementHash(productKey, "stock", purchaseQuantity)

                    // 재고가 0 이상이면 구매 성공
                    if (remainingStock >= 0) {
                        redisService.incrementHash(productKey, "sold", purchaseQuantity)
                        println("Customer $customerId: 구매 성공 (남은 재고: $remainingStock)")
                    } else {
                        // 재고 부족 시 롤백
                        redisService.incrementHash(productKey, "stock", purchaseQuantity)
                        println("Customer $customerId: 재고 부족")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then - 재고와 판매량의 합이 1000이어야 함
        val finalStock = redisService.getHash(productKey, "stock") as Int
        val finalSold = redisService.getHash(productKey, "sold") as Int

        println("최종 재고: $finalStock, 판매량: $finalSold")
        assertThat(finalStock + finalSold).isEqualTo(1000)
        assertThat(finalStock).isEqualTo(0)
        assertThat(finalSold).isEqualTo(1000)
    }

    @DisplayName("조회수 증가 - 동시 접근")
    @Test
    fun viewCountIncrement() {
        // given - 게시글 초기 조회수 0
        val postKey = "post:9999:meta"
        redisService.putHash(postKey, "viewCount", 0)

        val viewerCount = 500
        val executor = Executors.newFixedThreadPool(50)
        val latch = CountDownLatch(viewerCount)

        // when - 500명이 동시에 조회
        repeat(viewerCount) {
            executor.submit {
                try {
                    // ✅ Atomic 증가 연산
                    redisService.incrementHash(postKey, "viewCount", 1)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then - 정확히 500
        val viewCount = redisService.getHash(postKey, "viewCount") as Long
        println("조회수: $viewCount")
        assertThat(viewCount).isEqualTo(500L)
    }

    @DisplayName("장바구니 수량 변경 - 동시 업데이트")
    @Test
    fun shoppingCartConcurrentUpdate() {
        // given - 장바구니에 상품 추가
        val cartKey = "cart:user:999"
        redisService.putHash(cartKey, "product:100", 10)

        val operationCount = 100
        val executor = Executors.newFixedThreadPool(20)
        val latch = CountDownLatch(operationCount)

        // when - 동시에 50번 증가, 50번 감소
        repeat(50) {
            executor.submit {
                try {
                    // 수량 증가
                    redisService.incrementHash(cartKey, "product:100", 2)
                } finally {
                    latch.countDown()
                }
            }
            executor.submit {
                try {
                    // 수량 감소
                    redisService.decrementHash(cartKey, "product:100", 1)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then - 10 + (50*2) - (50*1) = 60
        val quantity = redisService.getHash(cartKey, "product:100") as Long
        println("최종 수량: $quantity")
        assertThat(quantity).isEqualTo(60L)
    }
}