package study.min.product.service.product

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import study.min.product.common.lock.DistributedLock
import study.min.product.persistence.product.ProductStockRepository
import study.min.product.persistence.product.getByProductId

@Slf4j
@RequiredArgsConstructor
@Service
class ProductStockService(
    private val productStockRepository: ProductStockRepository
) {

    @DistributedLock(
        key = "'stock:' + #productId",
        waitTime = 10,
        leaseTime = 5,
        errorMessage = "현재 많은 주문이 몰리고 있습니다. 잠시 후 다시 시도해주세요."
    )
    @Transactional
    fun decrease(productId: Long, quantity: Int) {
        val productStock = productStockRepository.getByProductId(productId)
        productStock.decrease(quantity)
    }
}