package study.min.order.dto

/**
 * 주문 생성 요청 DTO
 */
data class CreateOrderRequest(
    val userId: Long,
    val productId: Long,
    val quantity: Int
)

/**
 * 주문 응답 DTO
 */
data class OrderResponse(
    val id: Long,
    val orderNumber: String,
    val userId: Long,
    val totalPrice: Long,
    val status: String,
    val items: List<OrderItemResponse>
)

/**
 * 주문 상품 응답 DTO
 */
data class OrderItemResponse(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val price: Long
)

/**
 * 상품 정보 응답 DTO
 */
data class ProductResponse(
    val id: Long,
    val name: String,
    val price: Long,
    val stock: Int,
    val available: Boolean
)
