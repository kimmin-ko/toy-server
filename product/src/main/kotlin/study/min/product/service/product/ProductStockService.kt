package study.min.product.service.product

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import study.min.product.common.lock.DistributedLock
import study.min.product.event.StockDecreasedEvent
import study.min.product.event.StockEventPublisher
import study.min.product.event.StockLowWarningEvent
import study.min.product.event.StockOutEvent
import study.min.product.persistence.product.ProductStockRepository
import study.min.product.persistence.product.getByProductId

@Slf4j
@RequiredArgsConstructor
@Service
class ProductStockService(
    private val productStockRepository: ProductStockRepository,
    private val stockEventPublisher: StockEventPublisher
) {

    companion object {
        private const val LOW_STOCK_THRESHOLD = 10 // 재고 부족 임계값
    }

    /**
     * 재고 차감 + Redis Pub/Sub 이벤트 발행
     */
    @DistributedLock(
        key = "'stock:' + #productId",
        waitTime = 10,
        leaseTime = 5,
        errorMessage = "현재 많은 주문이 몰리고 있습니다. 잠시 후 다시 시도해주세요."
    )
    @Transactional
    fun decrease(productId: Long, quantity: Int, orderId: String? = null) {
        val productStock = productStockRepository.getByProductId(productId)
        productStock.decrease(quantity)

        val remainingStock = productStock.quantity ?: 0

        // 1. 재고 차감 이벤트 발행
        stockEventPublisher.publishStockDecreased(
            StockDecreasedEvent(
                productId = productId,
                decreasedQuantity = quantity,
                remainingStock = remainingStock,
                orderId = orderId
            )
        )

        // 2. 재고 소진 이벤트 발행
        if (remainingStock == 0) {
            stockEventPublisher.publishStockOut(
                StockOutEvent(productId = productId)
            )
        }
        // 3. 재고 부족 경고 발행
        else if (remainingStock <= LOW_STOCK_THRESHOLD) {
            stockEventPublisher.publishStockLowWarning(
                StockLowWarningEvent(
                    productId = productId,
                    currentStock = remainingStock,
                    threshold = LOW_STOCK_THRESHOLD
                )
            )
        }
    }
}