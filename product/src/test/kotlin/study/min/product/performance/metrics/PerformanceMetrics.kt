package study.min.product.performance.metrics

import java.util.concurrent.atomic.AtomicInteger

/**
 * 프로토콜 타입
 */
enum class Protocol {
    GRPC, REST
}

/**
 * 메서드별 성능 메트릭
 * - latencies: 각 요청의 응답 시간 (나노초)
 * - payloadSize: 요청/응답 페이로드 크기
 * - successCount/failCount: 성공/실패 횟수
 */
data class MethodMetrics(
    val methodName: String,
    val protocol: Protocol,
    val latencies: MutableList<Long> = mutableListOf(),
    var payloadSize: PayloadSize? = null,
    val successCount: AtomicInteger = AtomicInteger(0),
    val failCount: AtomicInteger = AtomicInteger(0)
) {
    /**
     * Latency 추가
     */
    fun addLatency(nanos: Long) {
        latencies.add(nanos)
    }

    /**
     * 평균 Latency (밀리초)
     */
    fun getAvgLatencyMs(): Double =
        if (latencies.isEmpty()) 0.0 else latencies.average() / 1_000_000

    /**
     * 최소 Latency (밀리초)
     */
    fun getMinLatencyMs(): Double =
        (latencies.minOrNull() ?: 0) / 1_000_000.0

    /**
     * 최대 Latency (밀리초)
     */
    fun getMaxLatencyMs(): Double =
        (latencies.maxOrNull() ?: 0) / 1_000_000.0

    /**
     * P95 Latency (밀리초)
     * - 95% 요청이 이 시간 이내에 완료됨
     */
    fun getP95LatencyMs(): Double {
        if (latencies.isEmpty()) return 0.0
        val sorted = latencies.sorted()
        return sorted[sorted.size * 95 / 100] / 1_000_000.0
    }

    /**
     * P99 Latency (밀리초)
     * - 99% 요청이 이 시간 이내에 완료됨
     */
    fun getP99LatencyMs(): Double {
        if (latencies.isEmpty()) return 0.0
        val sorted = latencies.sorted()
        return sorted[sorted.size * 99 / 100] / 1_000_000.0
    }
}

/**
 * 페이로드 크기
 * - requestBytes: 요청 크기 (바이트)
 * - responseBytes: 응답 크기 (바이트)
 */
data class PayloadSize(
    val requestBytes: Int,
    val responseBytes: Int
) {
    val totalBytes: Int get() = requestBytes + responseBytes
}

/**
 * 동시성 부하 테스트 메트릭
 * - threadCount: 동시 스레드 수
 * - grpcMetrics/restMetrics: 각 프로토콜의 메트릭
 * - grpcElapsedTimeMs/restElapsedTimeMs: 전체 실행 시간
 * - grpcTps/restTps: 초당 처리량 (TPS)
 */
data class ConcurrencyMetrics(
    val threadCount: Int,
    val grpcMetrics: MethodMetrics,
    val restMetrics: MethodMetrics,
    val grpcElapsedTimeMs: Long,
    val restElapsedTimeMs: Long
) {
    val grpcTps: Double = if (grpcElapsedTimeMs > 0)
        threadCount * 1000.0 / grpcElapsedTimeMs else 0.0

    val restTps: Double = if (restElapsedTimeMs > 0)
        threadCount * 1000.0 / restElapsedTimeMs else 0.0
}
