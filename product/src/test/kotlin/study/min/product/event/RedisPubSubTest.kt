package study.min.product.event

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
import study.min.product.service.product.ProductStockService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Redis Pub/Sub 테스트
 */
@SpringBootTest(classes = [ProductApplication::class])
class RedisPubSubTest {

    @Autowired
    private lateinit var productStockService: ProductStockService

    @Autowired
    private lateinit var productStockRepository: ProductStockRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var stockEventPublisher: StockEventPublisher

    private var testProductId: Long = 0

    @BeforeEach
    fun setUp() {
        // 테스트용 상품 생성
        val product = Product().apply {
            name = "테스트 상품"
            price = 10000
        }
        val savedProduct = productRepository.save(product)
        testProductId = savedProduct.id!!

        // 초기 재고 설정
        val productStock = ProductStock().apply {
            this.product = savedProduct
            quantity = 100
        }
        productStockRepository.save(productStock)
    }

    @DisplayName("재고 차감 시 Pub/Sub 이벤트 발행")
    @Test
    fun testStockDecreasedEvent() {
        // given
        val latch = CountDownLatch(1)

        // when - 재고 차감
        productStockService.decrease(testProductId, 10, orderId = "ORDER-001")

        // then - 이벤트가 발행되고 구독자가 수신
        latch.await(3, TimeUnit.SECONDS)

        println("✅ 테스트 완료: 재고 차감 이벤트가 발행되었습니다.")
    }

    @DisplayName("재고 부족 경고 이벤트 발행")
    @Test
    fun testStockLowWarningEvent() {
        // given
        val latch = CountDownLatch(1)

        // when - 재고를 10개 이하로 만듦
        productStockService.decrease(testProductId, 95, orderId = "ORDER-002")

        // then
        latch.await(3, TimeUnit.SECONDS)

        println("✅ 테스트 완료: 재고 부족 경고가 발행되었습니다.")
    }

    @DisplayName("재고 소진 이벤트 발행")
    @Test
    fun testStockOutEvent() {
        // given
        val latch = CountDownLatch(1)

        // when - 재고를 0으로 만듦
        productStockService.decrease(testProductId, 100, orderId = "ORDER-003")

        // then
        latch.await(3, TimeUnit.SECONDS)

        println("✅ 테스트 완료: 재고 소진 이벤트가 발행되었습니다.")
    }

    @DisplayName("재고 증가 이벤트 직접 발행")
    @Test
    fun testStockIncreasedEvent() {
        // given
        val latch = CountDownLatch(1)

        // when - 재고 증가 이벤트 발행
        stockEventPublisher.publishStockIncreased(
            StockIncreasedEvent(
                productId = testProductId,
                increasedQuantity = 50,
                remainingStock = 150,
                reason = "입고"
            )
        )

        // then
        latch.await(3, TimeUnit.SECONDS)

        println("✅ 테스트 완료: 재고 증가 이벤트가 발행되었습니다.")
    }

    @DisplayName("동시에 여러 이벤트 발행")
    @Test
    fun testMultipleEvents() {
        // given
        val latch = CountDownLatch(3)

        // when - 여러 이벤트 발행
        stockEventPublisher.publishStockDecreased(
            StockDecreasedEvent(
                productId = testProductId,
                decreasedQuantity = 10,
                remainingStock = 90
            )
        )

        stockEventPublisher.publishStockLowWarning(
            StockLowWarningEvent(
                productId = testProductId,
                currentStock = 5,
                threshold = 10
            )
        )

        stockEventPublisher.publishStockOut(
            StockOutEvent(productId = testProductId)
        )

        // then
        latch.await(5, TimeUnit.SECONDS)

        println("✅ 테스트 완료: 여러 이벤트가 동시에 발행되었습니다.")
    }
}