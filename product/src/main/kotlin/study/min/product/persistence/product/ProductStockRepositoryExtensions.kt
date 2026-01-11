package study.min.product.persistence.product

import study.min.product.exception.AppException
import study.min.product.exception.AppExceptionCode

fun ProductStockRepository.getByProductId(productId: Long): ProductStock {
    return findByProductId(productId)
        ?: throw AppException(AppExceptionCode.PRODUCT_STOCK_01, "ProductStock not found for productId: $productId")
}