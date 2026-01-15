package study.min.product.performance.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.MessageLite

/**
 * 성능 메트릭 수집기
 * - Latency 측정
 * - 페이로드 크기 측정 (gRPC Protobuf vs REST JSON)
 */
class MetricsCollector {

    private val objectMapper = ObjectMapper()

    /**
     * 실행 시간 측정
     * @return Pair<결과, 경과시간(나노초)>
     */
    fun <T> measureLatency(block: () -> T): Pair<T, Long> {
        val startTime = System.nanoTime()
        val result = block()
        val elapsed = System.nanoTime() - startTime
        return Pair(result, elapsed)
    }

    /**
     * REST 페이로드 크기 측정 (JSON)
     * - Jackson ObjectMapper로 직렬화하여 바이트 크기 측정
     */
    fun measureRestPayloadSize(request: Any, response: Any): PayloadSize {
        val requestBytes = objectMapper.writeValueAsBytes(request).size
        val responseBytes = objectMapper.writeValueAsBytes(response).size
        return PayloadSize(requestBytes, responseBytes)
    }

    /**
     * gRPC 페이로드 크기 측정 (Protobuf)
     * - MessageLite.serializedSize로 직렬화 크기 측정
     */
    fun measureGrpcPayloadSize(request: MessageLite, response: MessageLite): PayloadSize {
        return PayloadSize(
            requestBytes = request.serializedSize,
            responseBytes = response.serializedSize
        )
    }
}
