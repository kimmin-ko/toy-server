package study.min.product.performance

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.client.RestClient
import study.min.product.ProductApplication
import study.min.product.dto.*
import study.min.product.grpc.*
import study.min.product.performance.metrics.*
import study.min.product.persistence.product.Product
import study.min.product.persistence.product.ProductRepository
import study.min.product.persistence.product.ProductStock
import study.min.product.persistence.product.ProductStockRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * gRPC vs REST API 성능 비교 테스트
 * - Latency 측정 (평균/P95/P99)
 * - 페이로드 크기 비교 (Protobuf vs JSON)
 * - 동시성 부하 테스트 (100/500/1000 스레드)
 */
@SpringBootTest(
    classes = [ProductApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
class GrpcVsRestPerformanceTest {

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var productStockRepository: ProductStockRepository

    private lateinit var restClient: RestClient
    private lateinit var grpcChannel: ManagedChannel
    private lateinit var grpcStub: ProductServiceGrpc.ProductServiceBlockingStub
    private lateinit var metricsCollector: MetricsCollector

    private var testProductId: Long = 0

    @BeforeEach
    fun setUp() {
        println("\n========================================")
        println("✅ 테스트 환경 준비 중...")
        println("========================================")

        // REST 클라이언트 초기화
        restClient = RestClient.builder()
            .baseUrl("http://localhost:8081")
            .build()

        // gRPC 스텁 초기화
        grpcChannel = ManagedChannelBuilder
            .forAddress("localhost", 8091)
            .usePlaintext()
            .build()
        grpcStub = ProductServiceGrpc.newBlockingStub(grpcChannel)

        metricsCollector = MetricsCollector()

        // 테스트 데이터 생성
        val product = Product().apply {
            name = "성능테스트 상품"
            price = 10000
        }
        val savedProduct = productRepository.save(product)
        testProductId = savedProduct.id!!

        val productStock = ProductStock().apply {
            this.product = savedProduct
            this.quantity = 100000  // 충분한 재고
        }
        productStockRepository.save(productStock)

        println("✅ 테스트 환경 준비 완료")
        println("   - testProductId: $testProductId")
        println("   - 초기 재고: 100,000개")
        println("   - REST: http://localhost:8081")
        println("   - gRPC: localhost:8091")
        println("========================================\n")
    }

    @AfterEach
    fun tearDown() {
        productStockRepository.deleteAllInBatch()
        productRepository.deleteAllInBatch()
        grpcChannel.shutdown()
        println("\n✅ 테스트 데이터 정리 완료\n")
    }

    @Test
    @DisplayName("1. 단일 요청 Latency 측정")
    fun singleRequestLatencyTest() {
        val iterations = 1000
        println("\n🔍 단일 요청 Latency 측정 (반복: $iterations 회)")

        // checkStock 측정
        println("\n[1/3] checkStock 측정 중...")
        val checkStockGrpc = measureGrpcCheckStock(iterations)
        val checkStockRest = measureRestCheckStock(iterations)
        println("   - gRPC 완료: 평균 ${String.format("%.3f", checkStockGrpc.getAvgLatencyMs())}ms")
        println("   - REST 완료: 평균 ${String.format("%.3f", checkStockRest.getAvgLatencyMs())}ms")

        // getProduct 측정
        println("\n[2/3] getProduct 측정 중...")
        val getProductGrpc = measureGrpcGetProduct(iterations)
        val getProductRest = measureRestGetProduct(iterations)
        println("   - gRPC 완료: 평균 ${String.format("%.3f", getProductGrpc.getAvgLatencyMs())}ms")
        println("   - REST 완료: 평균 ${String.format("%.3f", getProductRest.getAvgLatencyMs())}ms")

        // decreaseStock 측정 (재고 복원 필요)
        println("\n[3/3] decreaseStock 측정 중...")
        val decreaseStockGrpc = measureGrpcDecreaseStock(100)
        restoreStock(100)
        val decreaseStockRest = measureRestDecreaseStock(100)
        println("   - gRPC 완료: 평균 ${String.format("%.3f", decreaseStockGrpc.getAvgLatencyMs())}ms")
        println("   - REST 완료: 평균 ${String.format("%.3f", decreaseStockRest.getAvgLatencyMs())}ms")

        // 결과 출력
        ResultFormatter.printComparisonTable(
            checkStockGrpc, checkStockRest,
            getProductGrpc, getProductRest,
            decreaseStockGrpc, decreaseStockRest
        )
    }

    @Test
    @DisplayName("2. 페이로드 크기 비교")
    fun payloadSizeTest() {
        println("\n📦 페이로드 크기 비교 테스트")

        // gRPC 페이로드
        val grpcRequest = CheckStockRequest.newBuilder()
            .setProductId(testProductId)
            .setQuantity(10)
            .build()
        val grpcResponse = grpcStub.checkStock(grpcRequest)
        val grpcPayload = metricsCollector.measureGrpcPayloadSize(grpcRequest, grpcResponse)

        // REST 페이로드
        val restRequest = CheckStockRestRequest(testProductId, 10)
        val restResponse = restClient.post()
            .uri("/api/products/stock/check")
            .body(restRequest)
            .retrieve()
            .body(CheckStockRestResponse::class.java)!!
        val restPayload = metricsCollector.measureRestPayloadSize(restRequest, restResponse)

        println("\n" + "=".repeat(80))
        println("📦 페이로드 크기 비교 (checkStock)")
        println("=".repeat(80))
        println("gRPC (Protobuf):")
        println("  - Request:  ${grpcPayload.requestBytes} bytes")
        println("  - Response: ${grpcPayload.responseBytes} bytes")
        println("  - Total:    ${grpcPayload.totalBytes} bytes")
        println()
        println("REST (JSON):")
        println("  - Request:  ${restPayload.requestBytes} bytes")
        println("  - Response: ${restPayload.responseBytes} bytes")
        println("  - Total:    ${restPayload.totalBytes} bytes")
        println()
        val diff = ((restPayload.totalBytes - grpcPayload.totalBytes).toDouble() / grpcPayload.totalBytes * 100)
        println(
            "차이: %.2f%% (REST가 %s)".format(
                kotlin.math.abs(diff),
                if (diff > 0) "더 큼" else "더 작음"
            )
        )
        println("=".repeat(80) + "\n")
    }

    @Test
    @DisplayName("3. 동시성 부하 테스트 (100/500/1000 스레드)")
    fun concurrencyLoadTest() {
        val threadCounts = listOf(100, 500, 1000)
        val results = mutableListOf<ConcurrencyMetrics>()

        threadCounts.forEach { threadCount ->
            println("\n🔥 [$threadCount 스레드] 동시 요청 테스트 시작...")

            // gRPC 테스트
            println("  - gRPC 테스트 진행 중...")
            val (grpcMetrics, grpcElapsed) = runConcurrencyTest(threadCount, Protocol.GRPC)
            println("    ✓ 완료: ${grpcElapsed}ms, 성공=${grpcMetrics.successCount.get()}, 실패=${grpcMetrics.failCount.get()}")

            // 시스템 안정화 대기
            Thread.sleep(2000)

            // REST 테스트
            println("  - REST 테스트 진행 중...")
            val (restMetrics, restElapsed) = runConcurrencyTest(threadCount, Protocol.REST)
            println("    ✓ 완료: ${restElapsed}ms, 성공=${restMetrics.successCount.get()}, 실패=${restMetrics.failCount.get()}")

            results.add(
                ConcurrencyMetrics(
                    threadCount = threadCount,
                    grpcMetrics = grpcMetrics,
                    restMetrics = restMetrics,
                    grpcElapsedTimeMs = grpcElapsed,
                    restElapsedTimeMs = restElapsed
                )
            )

            // 대기
            Thread.sleep(3000)
        }

        // 결과 출력
        ResultFormatter.printConcurrencyTable(*results.toTypedArray())
    }

    /**
     * 동시성 테스트 실행
     */
    private fun runConcurrencyTest(threadCount: Int, protocol: Protocol): Pair<MethodMetrics, Long> {
        val executor = Executors.newFixedThreadPool(min(threadCount, 100))
        val latch = CountDownLatch(threadCount)
        val metrics = MethodMetrics("checkStock", protocol)

        val startTime = System.currentTimeMillis()

        repeat(threadCount) {
            executor.submit {
                try {
                    val (_, elapsed) = when (protocol) {
                        Protocol.GRPC -> {
                            metricsCollector.measureLatency {
                                grpcStub.checkStock(
                                    CheckStockRequest.newBuilder()
                                        .setProductId(testProductId)
                                        .setQuantity(1)
                                        .build()
                                )
                            }
                        }

                        Protocol.REST -> {
                            metricsCollector.measureLatency {
                                restClient.post()
                                    .uri("/api/products/stock/check")
                                    .body(CheckStockRestRequest(testProductId, 1))
                                    .retrieve()
                                    .body(CheckStockRestResponse::class.java)
                            }
                        }
                    }
                    metrics.addLatency(elapsed)
                    metrics.successCount.incrementAndGet()
                } catch (e: Exception) {
                    metrics.failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        val elapsedTime = System.currentTimeMillis() - startTime
        executor.shutdown()

        return Pair(metrics, elapsedTime)
    }

    /**
     * gRPC checkStock 측정
     */
    private fun measureGrpcCheckStock(iterations: Int): MethodMetrics {
        val metrics = MethodMetrics("checkStock", Protocol.GRPC)

        repeat(iterations) { index ->
            val request = CheckStockRequest.newBuilder()
                .setProductId(testProductId)
                .setQuantity(10)
                .build()

            val (response, elapsed) = metricsCollector.measureLatency {
                grpcStub.checkStock(request)
            }

            metrics.addLatency(elapsed)
            metrics.successCount.incrementAndGet()

            // 첫 번째 반복에서 페이로드 크기 측정
            if (index == 0) {
                metrics.payloadSize = metricsCollector.measureGrpcPayloadSize(request, response)
            }
        }

        return metrics
    }

    /**
     * REST checkStock 측정
     */
    private fun measureRestCheckStock(iterations: Int): MethodMetrics {
        val metrics = MethodMetrics("checkStock", Protocol.REST)

        repeat(iterations) { index ->
            val request = CheckStockRestRequest(testProductId, 10)

            val (response, elapsed) = metricsCollector.measureLatency {
                restClient.post()
                    .uri("/api/products/stock/check")
                    .body(request)
                    .retrieve()
                    .body(CheckStockRestResponse::class.java)!!
            }

            metrics.addLatency(elapsed)
            metrics.successCount.incrementAndGet()

            if (index == 0) {
                metrics.payloadSize = metricsCollector.measureRestPayloadSize(request, response)
            }
        }

        return metrics
    }

    /**
     * gRPC getProduct 측정
     */
    private fun measureGrpcGetProduct(iterations: Int): MethodMetrics {
        val metrics = MethodMetrics("getProduct", Protocol.GRPC)

        repeat(iterations) { index ->
            val request = GetProductRequest.newBuilder()
                .setProductId(testProductId)
                .build()

            val (response, elapsed) = metricsCollector.measureLatency {
                grpcStub.getProduct(request)
            }

            metrics.addLatency(elapsed)
            metrics.successCount.incrementAndGet()

            if (index == 0) {
                metrics.payloadSize = metricsCollector.measureGrpcPayloadSize(request, response)
            }
        }

        return metrics
    }

    /**
     * REST getProduct 측정
     */
    private fun measureRestGetProduct(iterations: Int): MethodMetrics {
        val metrics = MethodMetrics("getProduct", Protocol.REST)

        repeat(iterations) { index ->
            val (response, elapsed) = metricsCollector.measureLatency {
                restClient.get()
                    .uri("/api/products/{id}", testProductId)
                    .retrieve()
                    .body(GetProductRestResponse::class.java)!!
            }

            metrics.addLatency(elapsed)
            metrics.successCount.incrementAndGet()

            if (index == 0) {
                // GET 요청은 body가 없으므로 response만 측정
                metrics.payloadSize = PayloadSize(
                    0,
                    metricsCollector.measureRestPayloadSize(Unit, response).responseBytes
                )
            }
        }

        return metrics
    }

    /**
     * gRPC decreaseStock 측정
     */
    private fun measureGrpcDecreaseStock(iterations: Int): MethodMetrics {
        val metrics = MethodMetrics("decreaseStock", Protocol.GRPC)

        repeat(iterations) { index ->
            val request = DecreaseStockRequest.newBuilder()
                .setProductId(testProductId)
                .setQuantity(1)
                .setOrderId("TEST-ORDER-$index")
                .build()

            val (response, elapsed) = metricsCollector.measureLatency {
                grpcStub.decreaseStock(request)
            }

            metrics.addLatency(elapsed)
            metrics.successCount.incrementAndGet()

            if (index == 0) {
                metrics.payloadSize = metricsCollector.measureGrpcPayloadSize(request, response)
            }
        }

        return metrics
    }

    /**
     * REST decreaseStock 측정
     */
    private fun measureRestDecreaseStock(iterations: Int): MethodMetrics {
        val metrics = MethodMetrics("decreaseStock", Protocol.REST)

        repeat(iterations) { index ->
            val request = DecreaseStockRestRequest(1, "TEST-ORDER-REST-$index")

            val (response, elapsed) = metricsCollector.measureLatency {
                restClient.post()
                    .uri("/api/products/{id}/stock/decrease", testProductId)
                    .body(request)
                    .retrieve()
                    .body(DecreaseStockRestResponse::class.java)!!
            }

            metrics.addLatency(elapsed)
            metrics.successCount.incrementAndGet()

            if (index == 0) {
                metrics.payloadSize = metricsCollector.measureRestPayloadSize(request, response)
            }
        }

        return metrics
    }

    /**
     * 재고 복원
     */
    private fun restoreStock(quantity: Int) {
        val stock = productStockRepository.findByProductId(testProductId)
        stock?.let {
            it.increase(quantity)
            productStockRepository.save(it)
        }
    }
}
