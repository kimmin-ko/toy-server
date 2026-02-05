package study.min.order.client

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import study.min.order.dto.*

/**
 * Product Service REST API 클라이언트
 * - ProductGrpcClient와 동일한 인터페이스
 * - 성능 비교를 위해 동일한 메서드 시그니처 사용
 */
@Component
class ProductRestClient(
    private val productServiceRestClient: RestClient
) {

    /**
     * 재고 확인
     * POST /api/products/stock/check
     */
    fun checkStock(productId: Long, quantity: Int): CheckStockRestResponse {
        return productServiceRestClient.post()
            .uri("/api/products/stock/check")
            .body(CheckStockRestRequest(productId, quantity))
            .retrieve()
            .body(CheckStockRestResponse::class.java)
            ?: throw RuntimeException("Empty response from checkStock")
    }

    /**
     * 상품 정보 조회
     * GET /api/products/{productId}
     */
    fun getProduct(productId: Long): GetProductRestResponse {
        return productServiceRestClient.get()
            .uri("/api/products/{id}", productId)
            .retrieve()
            .body(GetProductRestResponse::class.java)
            ?: throw RuntimeException("Empty response from getProduct")
    }

    /**
     * 재고 차감
     * POST /api/products/{productId}/stock/decrease
     */
    fun decreaseStock(productId: Long, quantity: Int, orderId: String): DecreaseStockRestResponse {
        return productServiceRestClient.post()
            .uri("/api/products/{id}/stock/decrease", productId)
            .body(DecreaseStockRestRequest(quantity, orderId))
            .retrieve()
            .body(DecreaseStockRestResponse::class.java)
            ?: throw RuntimeException("Empty response from decreaseStock")
    }
}
