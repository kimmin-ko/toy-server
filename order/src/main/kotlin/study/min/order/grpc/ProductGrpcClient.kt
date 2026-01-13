package study.min.order.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import study.min.product.grpc.*

/**
 * Product 서버 gRPC 클라이언트
 * - Order 서버에서 Product 서버로 gRPC 호출
 */
@Component
class ProductGrpcClient(
    @Value("\${grpc.client.product-service.host:localhost}")
    private val host: String,
    @Value($$"${grpc.client.product-service.port:8091}")
    private val port: Int
) {

    private lateinit var channel: ManagedChannel
    private lateinit var productServiceStub: ProductServiceGrpc.ProductServiceBlockingStub

    @PostConstruct
    fun init() {
        channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build()

        productServiceStub = ProductServiceGrpc.newBlockingStub(channel)
        println("✅ [gRPC Client] Product Service 연결됨 - $host:$port")
    }

    @PreDestroy
    fun destroy() {
        channel.shutdown()
        println("🔌 [gRPC Client] Product Service 연결 종료")
    }

    /**
     * 재고 확인
     */
    fun checkStock(productId: Long, quantity: Int): CheckStockResponse {
        val request = CheckStockRequest.newBuilder()
            .setProductId(productId)
            .setQuantity(quantity)
            .build()

        return try {
            println("🔗 [gRPC Client] CheckStock 요청 - productId=$productId, quantity=$quantity")
            val response = productServiceStub.checkStock(request)
            println("✅ [gRPC Client] CheckStock 응답 - available=${response.available}, stock=${response.currentStock}")
            response
        } catch (e: StatusRuntimeException) {
            println("❌ [gRPC Client] CheckStock 실패: ${e.status}")
            throw RuntimeException("재고 확인 실패: ${e.status.description}", e)
        }
    }

    /**
     * 재고 차감
     */
    fun decreaseStock(productId: Long, quantity: Int, orderId: String): DecreaseStockResponse {
        val request = DecreaseStockRequest.newBuilder()
            .setProductId(productId)
            .setQuantity(quantity)
            .setOrderId(orderId)
            .build()

        return try {
            println("🔗 [gRPC Client] DecreaseStock 요청 - productId=$productId, quantity=$quantity, orderId=$orderId")
            val response = productServiceStub.decreaseStock(request)
            println("✅ [gRPC Client] DecreaseStock 응답 - success=${response.success}, remaining=${response.remainingStock}")
            response
        } catch (e: StatusRuntimeException) {
            println("❌ [gRPC Client] DecreaseStock 실패: ${e.status}")
            throw RuntimeException("재고 차감 실패: ${e.status.description}", e)
        }
    }

    /**
     * 재고 증가 (주문 취소 시)
     */
    fun increaseStock(productId: Long, quantity: Int, reason: String): IncreaseStockResponse {
        val request = IncreaseStockRequest.newBuilder()
            .setProductId(productId)
            .setQuantity(quantity)
            .setReason(reason)
            .build()

        return try {
            println("🔗 [gRPC Client] IncreaseStock 요청 - productId=$productId, quantity=$quantity, reason=$reason")
            val response = productServiceStub.increaseStock(request)
            println("✅ [gRPC Client] IncreaseStock 응답 - success=${response.success}, remaining=${response.remainingStock}")
            response
        } catch (e: StatusRuntimeException) {
            println("❌ [gRPC Client] IncreaseStock 실패: ${e.status}")
            throw RuntimeException("재고 증가 실패: ${e.status.description}", e)
        }
    }

    /**
     * 상품 정보 조회
     */
    fun getProduct(productId: Long): GetProductResponse {
        val request = GetProductRequest.newBuilder()
            .setProductId(productId)
            .build()

        return try {
            println("🔗 [gRPC Client] GetProduct 요청 - productId=$productId")
            val response = productServiceStub.getProduct(request)
            println("✅ [gRPC Client] GetProduct 응답 - name=${response.name}, price=${response.price}, stock=${response.stock}")
            response
        } catch (e: StatusRuntimeException) {
            println("❌ [gRPC Client] GetProduct 실패: ${e.status}")
            throw RuntimeException("상품 조회 실패: ${e.status.description}", e)
        }
    }

    /**
     * 여러 상품 정보 조회
     */
    fun getProducts(productIds: List<Long>): GetProductsResponse {
        val request = GetProductsRequest.newBuilder()
            .addAllProductIds(productIds)
            .build()

        return try {
            println("🔗 [gRPC Client] GetProducts 요청 - productIds=$productIds")
            val response = productServiceStub.getProducts(request)
            println("✅ [gRPC Client] GetProducts 응답 - 조회된 상품 수=${response.productsCount}")
            response
        } catch (e: StatusRuntimeException) {
            println("❌ [gRPC Client] GetProducts 실패: ${e.status}")
            throw RuntimeException("상품 목록 조회 실패: ${e.status.description}", e)
        }
    }
}
