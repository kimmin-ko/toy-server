package study.min.product.service.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import study.min.product.ProductApplication
import study.min.product.persistence.product.Product
import study.min.product.persistence.product.ProductRepository
import study.min.product.persistence.product.ProductStock
import study.min.product.persistence.product.ProductStockRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * ProductStockService 동시성 테스트
 * - Redisson 분산락을 사용한 재고 차감 동시성 제어 검증
 */
@SpringBootTest(classes = [ProductApplication::class])
class ProductStockServiceConcurrencyTest {

    @Autowired
    private lateinit var productStockService: ProductStockService

    @Autowired
    private lateinit var productStockRepository: ProductStockRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    private var testProduct: Product? = null;
    private var testProductId: Long = 0

    @BeforeEach
    fun setUp() {
        // 테스트용 상품 생성
        val product = Product().apply {
            name = "테스트 상품"
            price = 10000
        }
        testProduct = productRepository.save(product)
        testProductId = testProduct?.id!!
    }

    @AfterEach
    fun tearDown() {
        productStockRepository.deleteAllInBatch()
        productRepository.deleteAllInBatch()
    }

    @DisplayName("분산락을 사용한 재고 차감 - 100개 스레드 동시 실행")
    @Test
    fun concurrentDecreaseWithDistributedLock() {
        // given - 초기 재고 1000개
        val productStock = ProductStock().apply {
            this.product = testProduct
            this.quantity = 1000
        }
        productStockRepository.save(productStock)

        val threadCount = 100
        val decreaseQuantity = 10
        val executor = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when - 100개 스레드가 동시에 10개씩 차감
        val elapsedTime = measureTimeMillis {
            repeat(threadCount) { index ->
                executor.submit {
                    try {
                        productStockService.decrease(testProductId, decreaseQuantity)
                        successCount.incrementAndGet()
                        println("Thread $index: 재고 차감 성공")
                    } catch (e: Exception) {
                        failCount.incrementAndGet()
                        println("Thread $index: 재고 차감 실패 - ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
        }
        executor.shutdown()

        // then - 최종 재고 검증
        val finalStock = productStockRepository.findByProductId(testProductId)
        println("========================================")
        println("실행 시간: ${elapsedTime}ms")
        println("성공: ${successCount.get()}건, 실패: ${failCount.get()}건")
        println("최종 재고: ${finalStock?.quantity}")
        println("기대 재고: ${1000L - (threadCount * decreaseQuantity)}")
        println("========================================")

        // 모든 요청이 성공해야 함
        assertThat(successCount.get()).isEqualTo(threadCount)
        assertThat(failCount.get()).isEqualTo(0)

        // 최종 재고가 정확히 0이어야 함 (1000 - 100*10 = 0)
        assertThat(finalStock?.quantity).isEqualTo(0L)
    }

    @DisplayName("재고 부족 시나리오 - 초기 재고 50개, 100개 스레드가 10개씩 차감 시도")
    @Test
    fun insufficientStock() {
        // given - 초기 재고 50개로 재설정
        val productStock = ProductStock().apply {
            this.product = testProduct
            this.quantity = 50
        }
        productStockRepository.save(productStock)

        val threadCount = 100
        val decreaseQuantity = 10
        val executor = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when - 100개 스레드가 동시에 10개씩 차감 시도
        repeat(threadCount) { index ->
            executor.submit {
                try {
                    productStockService.decrease(testProductId, decreaseQuantity)
                    successCount.incrementAndGet()
                    println("Thread $index: 재고 차감 성공")
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                    println("Thread $index: 재고 차감 실패 - ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        executor.shutdown()

        // then - 5건만 성공, 95건 실패
        println("========================================")
        println("성공: ${successCount.get()}건, 실패: ${failCount.get()}건")
        println("최종 재고: ${productStockRepository.findByProductId(testProductId)?.quantity}")
        println("========================================")

        // 정확히 5건만 성공해야 함 (50 / 10 = 5)
        assertThat(successCount.get()).isEqualTo(5)
        assertThat(failCount.get()).isEqualTo(95)

        // 최종 재고가 0이어야 함
        val finalStock = productStockRepository.findByProductId(testProductId)
        assertThat(finalStock?.quantity).isEqualTo(0L)
    }

    @DisplayName("대량 트래픽 시뮬레이션 - 1000개 스레드")
    @Test
    fun highTrafficSimulation() {
        // given - 초기 재고 10000개로 재설정
        val productStock = ProductStock().apply {
            this.product = testProduct
            this.quantity = 10000
        }
        productStockRepository.save(productStock)

        val threadCount = 1000
        val decreaseQuantity = 10
        val executor = Executors.newFixedThreadPool(100)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when - 1000개 스레드가 동시에 10개씩 차감
        val elapsedTime = measureTimeMillis {
            repeat(threadCount) { index ->
                executor.submit {
                    try {
                        productStockService.decrease(testProductId, decreaseQuantity)
                        successCount.incrementAndGet()
                        if (index % 100 == 0) {
                            println("Thread $index: 재고 차감 성공")
                        }
                    } catch (e: Exception) {
                        failCount.incrementAndGet()
                        println("Thread $index: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
        }
        executor.shutdown()

        // then
        val finalStock = productStockRepository.findByProductId(testProductId)
        println("========================================")
        println("대량 트래픽 테스트 결과")
        println("실행 시간: ${elapsedTime}ms")
        println("TPS: ${threadCount * 1000 / elapsedTime}/sec")
        println("성공: ${successCount.get()}건, 실패: ${failCount.get()}건")
        println("최종 재고: ${finalStock?.quantity}")
        println("========================================")

        // 모든 요청이 성공하고 재고가 정확히 0이어야 함
        assertThat(successCount.get()).isEqualTo(threadCount)
        assertThat(finalStock?.quantity).isEqualTo(0L)
    }
}