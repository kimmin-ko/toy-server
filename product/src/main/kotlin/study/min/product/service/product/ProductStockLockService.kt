package study.min.product.service.product

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import study.min.product.common.lock.DistributedLock
import study.min.product.persistence.product.ProductStockRepository
import study.min.product.persistence.product.getByProductId

/**
 * 분산락 + 트랜잭션 내부에서 DB update만 수행하는 서비스
 *
 * ProductStockService에서 self-invocation 시 AOP 프록시가 동작하지 않으므로,
 * 락이 필요한 DB 작업을 별도 Bean으로 분리.
 */
@Service
class ProductStockLockService(
    private val productStockRepository: ProductStockRepository
) {

    @DistributedLock(
        key = "'stock:' + #productId",
        waitTime = 10,
        leaseTime = 1,
        errorMessage = "현재 많은 주문이 몰리고 있습니다. 잠시 후 다시 시도해주세요."
    )
    @Transactional
    fun decrease(productId: Long, quantity: Int): Int {
        val productStock = productStockRepository.getByProductId(productId)
        productStock.decrease(quantity)
        return productStock.quantity ?: 0
    }

    @DistributedLock(
        key = "'stock:' + #productId",
        waitTime = 10,
        leaseTime = 3,
        errorMessage = "재고 증가 처리 중 오류가 발생했습니다."
    )
    @Transactional
    fun increase(productId: Long, quantity: Int): Int {
        val productStock = productStockRepository.getByProductId(productId)
        productStock.increase(quantity)
        return productStock.quantity ?: 0
    }
}
