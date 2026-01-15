package study.min.order.dto

/**
 * Product REST API용 DTO
 * - Product Service의 ProductRestDto와 동일한 구조
 * - 클라이언트-서버 계약 (Client-Server Contract)
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
