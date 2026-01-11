package study.min.product.persistence.product

import org.springframework.data.jpa.repository.JpaRepository

interface ProductStockRepository : JpaRepository<ProductStock, Long> {

    fun findByProductId(productId: Long): ProductStock?

}