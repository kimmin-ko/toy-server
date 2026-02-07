package study.min.product.service.product

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import study.min.product.event.StockDecreasedEvent
import study.min.product.event.StockEventPublisher
import study.min.product.event.StockIncreasedEvent
import study.min.product.event.StockLowWarningEvent
import study.min.product.event.StockOutEvent
import study.min.product.persistence.product.ProductStock
import study.min.product.persistence.product.ProductStockRepository
import study.min.product.persistence.product.getByProductId

@Service
class ProductStockService(
    private val productStockRepository: ProductStockRepository,
    private val productStockLockService: ProductStockLockService,
    private val stockEventPublisher: StockEventPublisher
) {

    companion object {
        private const val LOW_STOCK_THRESHOLD = 10
    }

    /**
     * 재고 차감
     * - 분산락 안: DB update만 (ProductStockLockService)
     * - 분산락 밖: 이벤트 발행
     */
    fun decrease(productId: Long, quantity: Int, orderId: String? = null) {
        val remainingStock = productStockLockService.decrease(productId, quantity)

        stockEventPublisher.publishStockDecreased(
            StockDecreasedEvent(
                productId = productId,
                decreasedQuantity = quantity,
                remainingStock = remainingStock,
                orderId = orderId
            )
        )

        if (remainingStock == 0) {
            stockEventPublisher.publishStockOut(
                StockOutEvent(productId = productId)
            )
        } else if (remainingStock <= LOW_STOCK_THRESHOLD) {
            stockEventPublisher.publishStockLowWarning(
                StockLowWarningEvent(
                    productId = productId,
                    currentStock = remainingStock,
                    threshold = LOW_STOCK_THRESHOLD
                )
            )
        }
    }

    /**
     * 재고 조회
     */
    @Transactional(readOnly = true)
    fun getStock(productId: Long): ProductStock {
        return productStockRepository.getByProductId(productId)
    }

    /**
     * 재고 증가
     * - 분산락 안: DB update만 (ProductStockLockService)
     * - 분산락 밖: 이벤트 발행
     */
    fun increase(productId: Long, quantity: Int, reason: String = "입고") {
        val remainingStock = productStockLockService.increase(productId, quantity)

        stockEventPublisher.publishStockIncreased(
            StockIncreasedEvent(
                productId = productId,
                increasedQuantity = quantity,
                remainingStock = remainingStock,
                reason = reason
            )
        )
    }
}
