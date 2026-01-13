package study.min.product.config

import io.grpc.Server
import io.grpc.ServerBuilder
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import study.min.product.grpc.ProductGrpcService

/**
 * gRPC 서버 수동 설정
 * - Spring gRPC auto-configuration이 작동하지 않을 때 사용
 */
@Configuration
class GrpcServerConfig(
    private val productGrpcService: ProductGrpcService,
    @Value("\${grpc.server.port:8091}")
    private val grpcPort: Int
) {

    private var grpcServer: Server? = null

    @PostConstruct
    fun startGrpcServer() {
        grpcServer = ServerBuilder
            .forPort(grpcPort)
            .addService(productGrpcService)
            .build()
            .start()

        println("✅ [gRPC Server] Started on port: $grpcPort")

        // JVM 종료 시 gRPC 서버도 종료
        Runtime.getRuntime().addShutdownHook(Thread {
            stopGrpcServer()
        })
    }

    @PreDestroy
    fun stopGrpcServer() {
        grpcServer?.shutdown()?.awaitTermination()
        println("🔌 [gRPC Server] Stopped")
    }
}
