package study.min.order.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import study.min.order.dto.CreateOrderRequest
import study.min.order.dto.OrderItemResponse
import study.min.order.dto.OrderResponse
import study.min.order.dto.ProductResponse
import study.min.order.exception.AppException
import study.min.order.exception.AppExceptionCode
import study.min.order.grpc.ProductGrpcClient
import study.min.order.persistence.*

/**
 * 주문 서비스
 * - gRPC를 사용하여 Product 서버와 통신
 */
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderTransactionService: OrderTransactionService,
    private val productGrpcClient: ProductGrpcClient,
    private val meterRegistry: MeterRegistry
) {

    private val log = LoggerFactory.getLogger(OrderService::class.java)

    private val orderCreateSuccessCounter: Counter by lazy {
        Counter.builder("order.create.success")
            .description("Successfully created orders")
            .register(meterRegistry)
    }

    private val orderCreateFailureCounter: Counter by lazy {
        Counter.builder("order.create.failure")
            .description("Failed order creations")
            .register(meterRegistry)
    }

    private val orderCreateTimer: Timer by lazy {
        Timer.builder("order.create.duration")
            .description("Order creation duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
    }

    /**
     * 주문 생성
     *
     * DB connection 점유 최소화를 위해 트랜잭션을 분리:
     * 1. gRPC 조회 (트랜잭션 없음 - DB connection 불필요)
     * 2. 주문 PENDING 저장 (짧은 트랜잭션 - OrderTransactionService)
     * 3. gRPC 재고 차감 (트랜잭션 없음 - 분산락으로 오래 걸림)
     * 4. 주문 CONFIRMED 업데이트 (짧은 트랜잭션 - OrderTransactionService)
     * 5. 실패 시 FAILED 보상 처리
     */
    fun createOrder(request: CreateOrderRequest): OrderResponse = orderCreateTimer.record<OrderResponse> {
        try {
            doCreateOrder(request).also { orderCreateSuccessCounter.increment() }
        } catch (e: Exception) {
            orderCreateFailureCounter.increment()
            throw e
        }
    }!!

    private fun doCreateOrder(request: CreateOrderRequest): OrderResponse {
        log.info("[Order] 주문 생성 시작 - userId={}, productId={}, quantity={}", request.userId, request.productId, request.quantity)

        // 1. gRPC로 재고 확인 (트랜잭션 밖 - DB connection 불필요)
        val stockResponse = productGrpcClient.checkStock(request.productId, request.quantity)
        if (!stockResponse.available) {
            throw AppException(AppExceptionCode.ORDER_01, "재고 부족: ${stockResponse.message}")
        }
        log.info("[Order] 재고 확인 완료 - 현재 재고: {}개", stockResponse.currentStock)

        // 2. gRPC로 상품 정보 조회 (트랜잭션 밖 - DB connection 불필요)
        val product = productGrpcClient.getProduct(request.productId)
        log.info("[Order] 상품 정보 조회 완료 - {}, {}원", product.name, product.price)

        // 3. 주문 PENDING 저장 (짧은 트랜잭션 - DB connection 수ms만 점유)
        val savedOrder = orderTransactionService.savePendingOrder(request, product.price)
        log.info("[Order] 주문 PENDING 저장 완료 - {}", savedOrder.orderNumber)

        // 4. gRPC로 재고 차감 (트랜잭션 밖 - 분산락 대기 시간이 길어도 DB connection 점유 안 함)
        try {
            val decreaseResponse = productGrpcClient.decreaseStock(
                request.productId,
                request.quantity,
                savedOrder.orderNumber
            )
            log.info("[Order] 재고 차감 완료 - 남은 재고: {}개", decreaseResponse.remainingStock)
        } catch (e: Exception) {
            // 재고 차감 실패 시 주문 FAILED 보상 처리
            log.error("[Order] 재고 차감 실패 - {}", savedOrder.orderNumber, e)
            orderTransactionService.updateOrderStatus(savedOrder.id!!, OrderStatus.FAILED)
            throw e
        }

        // 5. 주문 CONFIRMED 업데이트 (짧은 트랜잭션)
        orderTransactionService.updateOrderStatus(savedOrder.id!!, OrderStatus.CONFIRMED)
        log.info("[Order] 주문 확정 완료 - {}", savedOrder.orderNumber)

        return OrderResponse(
            id = savedOrder.id!!,
            orderNumber = savedOrder.orderNumber,
            userId = savedOrder.userId,
            totalPrice = savedOrder.totalPrice,
            status = OrderStatus.CONFIRMED.name,
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
    fun cancelOrder(orderId: Long): OrderResponse {
        log.info("[Order] 주문 취소 시작 - orderId={}", orderId)

        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalArgumentException("주문을 찾을 수 없습니다: $orderId") }

        check(order.status != OrderStatus.CANCELLED) { "이미 취소된 주문입니다" }

        // gRPC로 재고 복구 (트랜잭션 밖)
        order.orderItems.forEach { item ->
            val increaseResponse = productGrpcClient.increaseStock(
                item.productId,
                item.quantity,
                "주문 취소: ${order.orderNumber}"
            )
            log.info("[Order] 재고 복구 완료 - productId={}, 현재 재고: {}개", item.productId, increaseResponse.remainingStock)
        }

        // 주문 상태 변경 (짧은 트랜잭션)
        orderTransactionService.updateOrderStatus(order.id!!, OrderStatus.CANCELLED)
        log.info("[Order] 주문 취소 완료 - {}", order.orderNumber)

        return OrderResponse(
            id = order.id!!,
            orderNumber = order.orderNumber,
            userId = order.userId,
            totalPrice = order.totalPrice,
            status = OrderStatus.CANCELLED.name,
            items = order.orderItems.map { item ->
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
    fun getOrder(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalArgumentException("주문을 찾을 수 없습니다: $orderId") }

        // gRPC로 상품 정보 일괄 조회 (트랜잭션 밖)
        val productNames = order.orderItems.associate { item ->
            item.productId to try {
                productGrpcClient.getProduct(item.productId).name
            } catch (_: Exception) {
                "알 수 없음"
            }
        }

        return OrderResponse(
            id = order.id!!,
            orderNumber = order.orderNumber,
            userId = order.userId,
            totalPrice = order.totalPrice,
            status = order.status.name,
            items = order.orderItems.map { item ->
                OrderItemResponse(
                    productId = item.productId,
                    productName = productNames[item.productId] ?: "알 수 없음",
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
}
