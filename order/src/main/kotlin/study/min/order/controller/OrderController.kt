package study.min.order.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import study.min.order.dto.CreateOrderRequest
import study.min.order.dto.OrderResponse
import study.min.order.dto.ProductResponse
import study.min.order.service.OrderService

/**
 * 주문 REST API Controller
 * - gRPC 통신 테스트용
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {

    /**
     * 주문 생성
     * POST /orders
     * userId는 Gateway에서 JWT 검증 후 X-Auth-User 헤더로 전달
     */
    @PostMapping
    fun createOrder(
        @RequestBody request: CreateOrderRequest,
        @RequestHeader("X-Auth-User") userId: String
    ): ResponseEntity<OrderResponse> {
        return try {
            val order = orderService.createOrder(request, userId.toLong())
            ResponseEntity.ok(order)
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 주문 조회
     * GET /orders/{orderId}
     */
    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: Long): ResponseEntity<OrderResponse> {
        return try {
            val order = orderService.getOrder(orderId)
            ResponseEntity.ok(order)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * 주문 취소
     * POST /orders/{orderId}/cancel
     */
    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(@PathVariable orderId: Long): ResponseEntity<OrderResponse> {
        return try {
            val order = orderService.cancelOrder(orderId)
            ResponseEntity.ok(order)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * 상품 정보 조회
     * GET /orders/products/{productId}
     */
    @GetMapping("/products/{productId}")
    fun getProduct(@PathVariable productId: Long): ResponseEntity<ProductResponse> {
        return try {
            val product = orderService.getProduct(productId)
            ResponseEntity.ok(product)
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }
}
