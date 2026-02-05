package study.min.order.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import study.min.order.dto.CreateOrderRequest
import study.min.order.dto.OrderItemResponse
import study.min.order.dto.OrderResponse
import study.min.order.dto.ProductResponse
import study.min.order.event.OrderCreatedEvent
import study.min.order.event.OrderEventPublisher
import study.min.order.exception.AppException
import study.min.order.exception.AppExceptionCode
import study.min.order.grpc.ProductGrpcClient
import study.min.order.persistence.*
import java.time.LocalDateTime

/**
 * 주문 서비스
 * - gRPC를 사용하여 Product 서버와 통신
 */
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productGrpcClient: ProductGrpcClient,
    private val orderEventPublisher: OrderEventPublisher
) {

    /**
     * 주문 생성
     * - gRPC로 재고 확인 및 차감
     */
    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        println("🛒 [Order] 주문 생성 시작 - userId=${request.userId}, productId=${request.productId}, quantity=${request.quantity}")

        // 1. gRPC로 재고 확인
        val stockResponse = productGrpcClient.checkStock(request.productId, request.quantity)
        if (!stockResponse.available) {
            throw AppException(AppExceptionCode.ORDER_01, "재고 부족: ${stockResponse.message}")
        }
        println("✅ [Order] 재고 확인 완료 - 현재 재고: ${stockResponse.currentStock}개")

        // 2. gRPC로 상품 정보 조회
        val product = productGrpcClient.getProduct(request.productId)
        println("✅ [Order] 상품 정보 조회 완료 - ${product.name}, ${product.price}원")

        // 3. 주문 생성
        val order = Order().apply {
            this.orderNumber = generateOrderNumber()
            this.userId = request.userId
            this.status = OrderStatus.PENDING
        }

        val orderItem = OrderItem().apply {
            this.productId = request.productId
            this.quantity = request.quantity
            this.price = product.price
        }

        order.addOrderItem(orderItem)
        order.calculateTotalPrice()

        // 4. gRPC로 재고 차감
        val decreaseResponse = productGrpcClient.decreaseStock(
            request.productId,
            request.quantity,
            order.orderNumber
        )
        println("✅ [Order] 재고 차감 완료 - 남은 재고: ${decreaseResponse.remainingStock}개")

        // 5. 주문 저장
        val savedOrder = orderRepository.save(order)
        println("✅ [Order] 주문 생성 완료 - ${savedOrder.orderNumber}")

        // 6. 주문 확정
        savedOrder.status = OrderStatus.CONFIRMED

        // 7. Kafka 이벤트 발행
        val orderCreatedEvent = OrderCreatedEvent(
            orderId = savedOrder.orderNumber,
            productId = request.productId,
            quantity = request.quantity,
            price = product.price,
            customerId = request.userId.toString()
        )
        orderEventPublisher.publishOrderCreated(orderCreatedEvent)
        println("📤 [Order] Kafka 이벤트 발행 완료 - ${savedOrder.orderNumber}")

        return OrderResponse(
            id = savedOrder.id!!,
            orderNumber = savedOrder.orderNumber,
            userId = savedOrder.userId,
            totalPrice = savedOrder.totalPrice,
            status = savedOrder.status.name,
            items = listOf(
                OrderItemResponse(
                    productId = request.productId,
                    productName = product.name,
                    quantity = request.quantity,
                    price = product.price
                )
            )
        )
    }

    /**
     * 주문 취소
     * - gRPC로 재고 복구
     */
    @Transactional
    fun cancelOrder(orderId: Long): OrderResponse {
        println("❌ [Order] 주문 취소 시작 - orderId=$orderId")

        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalArgumentException("주문을 찾을 수 없습니다: $orderId") }

        check(order.status != OrderStatus.CANCELLED) { "이미 취소된 주문입니다" }

        // gRPC로 재고 복구
        order.orderItems.forEach { item ->
            val increaseResponse = productGrpcClient.increaseStock(
                item.productId,
                item.quantity,
                "주문 취소: ${order.orderNumber}"
            )
            println("✅ [Order] 재고 복구 완료 - productId=${item.productId}, 현재 재고: ${increaseResponse.remainingStock}개")
        }

        // 주문 상태 변경
        order.status = OrderStatus.CANCELLED
        val savedOrder = orderRepository.save(order)

        println("✅ [Order] 주문 취소 완료 - ${savedOrder.orderNumber}")

        return OrderResponse(
            id = savedOrder.id!!,
            orderNumber = savedOrder.orderNumber,
            userId = savedOrder.userId,
            totalPrice = savedOrder.totalPrice,
            status = savedOrder.status.name,
            items = savedOrder.orderItems.map { item ->
                OrderItemResponse(
                    productId = item.productId,
                    productName = "상품",
                    quantity = item.quantity,
                    price = item.price
                )
            }
        )
    }

    /**
     * 주문 조회
     */
    @Transactional(readOnly = true)
    fun getOrder(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalArgumentException("주문을 찾을 수 없습니다: $orderId") }

        return OrderResponse(
            id = order.id!!,
            orderNumber = order.orderNumber,
            userId = order.userId,
            totalPrice = order.totalPrice,
            status = order.status.name,
            items = order.orderItems.map { item ->
                // gRPC로 상품 정보 조회
                val product = try {
                    productGrpcClient.getProduct(item.productId)
                } catch (e: Exception) {
                    null
                }

                OrderItemResponse(
                    productId = item.productId,
                    productName = product?.name ?: "알 수 없음",
                    quantity = item.quantity,
                    price = item.price
                )
            }
        )
    }

    /**
     * 상품 정보 조회 (gRPC 테스트용)
     */
    fun getProduct(productId: Long): ProductResponse {
        val product = productGrpcClient.getProduct(productId)

        return ProductResponse(
            id = product.id,
            name = product.name,
            price = product.price,
            stock = product.stock,
            available = product.available
        )
    }

    /**
     * 주문 번호 생성
     */
    private fun generateOrderNumber(): String {
        val timestamp = LocalDateTime.now()
        return "ORDER-${timestamp.year}${
            timestamp.monthValue.toString().padStart(2, '0')
        }${timestamp.dayOfMonth.toString().padStart(2, '0')}-${System.currentTimeMillis()}"
    }
}
