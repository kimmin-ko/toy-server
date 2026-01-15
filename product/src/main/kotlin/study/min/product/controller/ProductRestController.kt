package study.min.product.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import study.min.product.dto.*
import study.min.product.persistence.product.ProductRepository
import study.min.product.persistence.product.ProductStockRepository
import study.min.product.persistence.product.getByProductId
import study.min.product.service.product.ProductStockService

/**
 * Product REST API Controller
 * - gRPC와 동일한 기능을 REST API로 제공
 * - ProductStockService 비즈니스 로직 재사용
 * - @DistributedLock과 Redis Pub/Sub은 Service 계층에서 자동 처리
 */
@RestController
@RequestMapping("/api/products")
class ProductRestController(
    private val productStockService: ProductStockService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository
) {

    /**
     * 재고 확인
     * POST /api/products/stock/check
     */
    @PostMapping("/stock/check")
    fun checkStock(@RequestBody request: CheckStockRestRequest): ResponseEntity<CheckStockRestResponse> {
        return try {
            val productStock = productStockRepository.getByProductId(request.productId)
            val currentStock = productStock.quantity ?: 0
            val available = currentStock >= request.quantity

            ResponseEntity.ok(
                CheckStockRestResponse(
                    available = available,
                    currentStock = currentStock,
                    message = if (available) "재고 충분"
                             else "재고 부족 (현재: ${currentStock}개, 필요: ${request.quantity}개)"
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 상품 정보 조회
     * GET /api/products/{productId}
     */
    @GetMapping("/{productId}")
    fun getProduct(@PathVariable productId: Long): ResponseEntity<GetProductRestResponse> {
        return try {
            val product = productRepository.findById(productId).orElse(null)
                ?: throw IllegalArgumentException("상품을 찾을 수 없습니다: $productId")

            val productStock = productStockRepository.getByProductId(productId)
            val stock = productStock.quantity ?: 0

            ResponseEntity.ok(
                GetProductRestResponse(
                    id = product.id!!,
                    name = product.name ?: "",
                    price = (product.price ?: 0).toLong(),
                    stock = stock,
                    available = stock > 0
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 재고 차감
     * POST /api/products/{productId}/stock/decrease
     *
     * - ProductStockService.decrease() 호출
     * - @DistributedLock 자동 적용됨
     * - Redis Pub/Sub 이벤트 자동 발행됨
     */
    @PostMapping("/{productId}/stock/decrease")
    fun decreaseStock(
        @PathVariable productId: Long,
        @RequestBody request: DecreaseStockRestRequest
    ): ResponseEntity<DecreaseStockRestResponse> {
        return try {
            // 비즈니스 로직 재사용 (분산락 자동 적용)
            productStockService.decrease(
                productId = productId,
                quantity = request.quantity,
                orderId = request.orderId
            )

            val productStock = productStockRepository.getByProductId(productId)

            ResponseEntity.ok(
                DecreaseStockRestResponse(
                    success = true,
                    remainingStock = productStock.quantity ?: 0,
                    message = "재고 차감 완료 (남은 재고: ${productStock.quantity ?: 0}개)"
                )
            )
        } catch (e: IllegalStateException) {
            // 재고 부족 → HTTP 409 Conflict
            ResponseEntity.status(409).body(
                DecreaseStockRestResponse(
                    success = false,
                    remainingStock = 0,
                    message = e.message ?: "재고 부족"
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }
}
