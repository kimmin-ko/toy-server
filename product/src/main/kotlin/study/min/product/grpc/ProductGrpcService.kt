package study.min.product.grpc

import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.grpc.server.service.GrpcService
import study.min.product.persistence.product.ProductRepository
import study.min.product.persistence.product.ProductStockRepository
import study.min.product.persistence.product.getByProductId
import study.min.product.service.product.ProductStockService

/**
 * Product gRPC 서버 구현
 * - Order 서버에서 호출하는 gRPC 서비스
 */
@GrpcService
class ProductGrpcService(
    private val productStockService: ProductStockService,
    private val productStockRepository: ProductStockRepository,
    private val productRepository: ProductRepository
) : ProductServiceGrpc.ProductServiceImplBase() {

    /**
     * 재고 확인
     */
    override fun checkStock(
        request: CheckStockRequest,
        responseObserver: StreamObserver<CheckStockResponse>
    ) {
        try {
            val productStock = productStockRepository.getByProductId(request.productId)

            val currentStock = productStock.quantity ?: 0
            val available = currentStock >= request.quantity

            val response = CheckStockResponse.newBuilder()
                .setAvailable(available)
                .setCurrentStock(productStock.quantity ?: 0)
                .setMessage(
                    if (available) "재고 충분"
                    else "재고 부족 (현재: ${productStock.quantity ?: 0}개, 필요: ${request.quantity}개)"
                )
                .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()

            println("📞 [gRPC] CheckStock - productId=${request.productId}, available=$available")
        } catch (e: Exception) {
            println("❌ [gRPC] CheckStock 오류: ${e.message}")
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("재고 확인 실패: ${e.message}")
                    .asRuntimeException()
            )
        }
    }

    /**
     * 재고 차감
     */
    override fun decreaseStock(
        request: DecreaseStockRequest,
        responseObserver: StreamObserver<DecreaseStockResponse>
    ) {
        try {
            // 재고 차감 (분산락 적용됨)
            productStockService.decrease(
                productId = request.productId,
                quantity = request.quantity,
                orderId = request.orderId
            )

            val productStock = productStockRepository.getByProductId(request.productId)

            val response = DecreaseStockResponse.newBuilder()
                .setSuccess(true)
                .setRemainingStock(productStock.quantity ?: 0)
                .setMessage("재고 차감 완료 (남은 재고: ${productStock.quantity ?: 0}개)")
                .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()

            println("📞 [gRPC] DecreaseStock - productId=${request.productId}, quantity=${request.quantity}, orderId=${request.orderId}")
        } catch (e: IllegalStateException) {
            println("⚠️ [gRPC] DecreaseStock 실패: ${e.message}")
            responseObserver.onError(
                Status.RESOURCE_EXHAUSTED
                    .withDescription(e.message)
                    .asRuntimeException()
            )
        } catch (e: Exception) {
            println("❌ [gRPC] DecreaseStock 오류: ${e.message}")
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("재고 차감 실패: ${e.message}")
                    .asRuntimeException()
            )
        }
    }

    /**
     * 재고 증가 (주문 취소 시)
     */
    override fun increaseStock(
        request: IncreaseStockRequest,
        responseObserver: StreamObserver<IncreaseStockResponse>
    ) {
        try {
            productStockService.increase(
                productId = request.productId,
                quantity = request.quantity
            )

            val productStock = productStockRepository.findByProductId(request.productId)

            val response = IncreaseStockResponse.newBuilder()
                .setSuccess(true)
                .setRemainingStock(productStock?.quantity ?: 0)
                .setMessage("재고 증가 완료 (사유: ${request.reason}, 현재 재고: ${productStock?.quantity ?: 0}개)")
                .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()

            println("📞 [gRPC] IncreaseStock - productId=${request.productId}, quantity=${request.quantity}, reason=${request.reason}")
        } catch (e: Exception) {
            println("❌ [gRPC] IncreaseStock 오류: ${e.message}")
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("재고 증가 실패: ${e.message}")
                    .asRuntimeException()
            )
        }
    }

    /**
     * 상품 정보 조회
     */
    override fun getProduct(
        request: GetProductRequest,
        responseObserver: StreamObserver<GetProductResponse>
    ) {
        try {
            val product = productRepository.findById(request.productId).orElse(null)
                ?: throw IllegalArgumentException("상품을 찾을 수 없습니다: ${request.productId}")

            val productStock = productStockRepository.getByProductId(request.productId)

            val stock = productStock.quantity ?: 0

            val response = GetProductResponse.newBuilder()
                .setId(product.id!!)
                .setName(product.name)
                .setPrice((product.price ?: 0).toLong())
                .setStock(stock)
                .setAvailable(stock > 0)
                .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()

            println("📞 [gRPC] GetProduct - productId=${request.productId}, name=${product.name}")

        } catch (e: IllegalArgumentException) {
            println("⚠️ [gRPC] GetProduct 실패: ${e.message}")
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription(e.message)
                    .asRuntimeException()
            )
        } catch (e: Exception) {
            println("❌ [gRPC] GetProduct 오류: ${e.message}")
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("상품 조회 실패: ${e.message}")
                    .asRuntimeException()
            )
        }
    }

    /**
     * 여러 상품 정보 조회
     */
    override fun getProducts(
        request: GetProductsRequest,
        responseObserver: StreamObserver<GetProductsResponse>
    ) {
        try {
            val productInfoList = request.productIdsList.mapNotNull { productId ->
                val product = productRepository.findById(productId).orElse(null) ?: return@mapNotNull null
                val productStock = productStockRepository.getByProductId(productId)
                val stock = productStock.quantity ?: 0

                ProductInfo.newBuilder()
                    .setId(product.id!!)
                    .setName(product.name)
                    .setPrice((product.price ?: 0).toLong())
                    .setStock(stock)
                    .setAvailable(stock > 0)
                    .build()
            }

            val response = GetProductsResponse.newBuilder()
                .addAllProducts(productInfoList)
                .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()

            println("📞 [gRPC] GetProducts - 조회한 상품 수: ${productInfoList.size}")
        } catch (e: Exception) {
            println("❌ [gRPC] GetProducts 오류: ${e.message}")
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("상품 목록 조회 실패: ${e.message}")
                    .asRuntimeException()
            )
        }
    }
}
