package study.min.product.dto

/**
 * REST API용 DTO 정의
 * - gRPC Protobuf 메시지와 대응되는 JSON 형식
 */

// ========================================
// 재고 확인 (CheckStock)
// ========================================

data class CheckStockRestRequest(
    val productId: Long,
    val quantity: Int
)

data class CheckStockRestResponse(
    val available: Boolean,
    val currentStock: Int,
    val message: String
)

// ========================================
// 상품 조회 (GetProduct)
// ========================================

data class GetProductRestResponse(
    val id: Long,
    val name: String,
    val price: Long,
    val stock: Int,
    val available: Boolean
)

// ========================================
// 재고 차감 (DecreaseStock)
// ========================================

data class DecreaseStockRestRequest(
    val quantity: Int,
    val orderId: String
)

data class DecreaseStockRestResponse(
    val success: Boolean,
    val remainingStock: Int,
    val message: String
)
