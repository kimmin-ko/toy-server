package study.min.product.performance.metrics

import kotlin.math.abs

/**
 * 성능 측정 결과 포맷터
 * - 메서드별 latency + payload 비교 테이블 출력
 * - 동시성 부하 테스트 결과 테이블 출력
 */
object ResultFormatter {

    /**
     * 메서드별 성능 비교 테이블 출력
     */
    fun printComparisonTable(
        checkStockGrpc: MethodMetrics,
        checkStockRest: MethodMetrics,
        getProductGrpc: MethodMetrics,
        getProductRest: MethodMetrics,
        decreaseStockGrpc: MethodMetrics,
        decreaseStockRest: MethodMetrics
    ) {
        println("\n" + "=".repeat(140))
        println("📊 gRPC vs REST API 성능 비교 결과")
        println("=".repeat(140))

        printMethodComparison("checkStock", checkStockGrpc, checkStockRest)
        printMethodComparison("getProduct", getProductGrpc, getProductRest)
        printMethodComparison("decreaseStock", decreaseStockGrpc, decreaseStockRest)

        println("=".repeat(140) + "\n")
    }

    /**
     * 단일 메서드 비교 출력
     */
    private fun printMethodComparison(
        methodName: String,
        grpcMetrics: MethodMetrics,
        restMetrics: MethodMetrics
    ) {
        println("\n[$methodName]")
        println("-".repeat(140))
        println(
            "%-15s | %12s | %12s | %12s | %12s | %12s | %20s"
                .format("프로토콜", "평균 (ms)", "최소 (ms)", "최대 (ms)", "P95 (ms)", "P99 (ms)", "페이로드 (요청/응답)")
        )
        println("-".repeat(140))

        // gRPC 결과
        val grpcPayloadStr = grpcMetrics.payloadSize?.let {
            "${it.requestBytes}B / ${it.responseBytes}B"
        } ?: "N/A"
        println(
            "%-15s | %12.3f | %12.3f | %12.3f | %12.3f | %12.3f | %20s"
                .format(
                    "gRPC",
                    grpcMetrics.getAvgLatencyMs(),
                    grpcMetrics.getMinLatencyMs(),
                    grpcMetrics.getMaxLatencyMs(),
                    grpcMetrics.getP95LatencyMs(),
                    grpcMetrics.getP99LatencyMs(),
                    grpcPayloadStr
                )
        )

        // REST 결과
        val restPayloadStr = restMetrics.payloadSize?.let {
            "${it.requestBytes}B / ${it.responseBytes}B"
        } ?: "N/A"
        println(
            "%-15s | %12.3f | %12.3f | %12.3f | %12.3f | %12.3f | %20s"
                .format(
                    "REST",
                    restMetrics.getAvgLatencyMs(),
                    restMetrics.getMinLatencyMs(),
                    restMetrics.getMaxLatencyMs(),
                    restMetrics.getP95LatencyMs(),
                    restMetrics.getP99LatencyMs(),
                    restPayloadStr
                )
        )

        println("-".repeat(140))

        // 성능 차이 계산
        val avgDiff = if (grpcMetrics.getAvgLatencyMs() > 0) {
            ((restMetrics.getAvgLatencyMs() - grpcMetrics.getAvgLatencyMs()) / grpcMetrics.getAvgLatencyMs() * 100)
        } else 0.0

        val payloadDiff = if (grpcMetrics.payloadSize != null && restMetrics.payloadSize != null) {
            val grpcTotal = grpcMetrics.payloadSize!!.totalBytes
            val restTotal = restMetrics.payloadSize!!.totalBytes
            if (grpcTotal > 0) ((restTotal - grpcTotal).toDouble() / grpcTotal * 100) else 0.0
        } else null

        println(
            "✓ 평균 응답시간 차이: %.2f%% %s".format(
                abs(avgDiff),
                when {
                    avgDiff > 0 -> "(REST가 느림)"
                    avgDiff < 0 -> "(gRPC가 느림)"
                    else -> "(동일)"
                }
            )
        )

        if (payloadDiff != null) {
            println(
                "✓ 페이로드 크기 차이: %.2f%% %s".format(
                    abs(payloadDiff),
                    when {
                        payloadDiff > 0 -> "(REST가 큼)"
                        payloadDiff < 0 -> "(gRPC가 큼)"
                        else -> "(동일)"
                    }
                )
            )
        }
    }

    /**
     * 동시성 부하 테스트 결과 테이블 출력
     */
    fun printConcurrencyTable(vararg results: ConcurrencyMetrics) {
        println("\n" + "=".repeat(120))
        println("📊 동시성 부하 테스트 결과")
        println("=".repeat(120))
        println(
            "%-15s | %-10s | %15s | %15s | %15s | %15s | %15s"
                .format("스레드 수", "프로토콜", "TPS", "평균 (ms)", "P95 (ms)", "성공", "실패")
        )
        println("-".repeat(120))

        results.forEach { result ->
            // gRPC 결과
            println(
                "%-15s | %-10s | %15.2f | %15.3f | %15.3f | %15d | %15d"
                    .format(
                        result.threadCount,
                        "gRPC",
                        result.grpcTps,
                        result.grpcMetrics.getAvgLatencyMs(),
                        result.grpcMetrics.getP95LatencyMs(),
                        result.grpcMetrics.successCount.get(),
                        result.grpcMetrics.failCount.get()
                    )
            )

            // REST 결과
            println(
                "%-15s | %-10s | %15.2f | %15.3f | %15.3f | %15d | %15d"
                    .format(
                        "",
                        "REST",
                        result.restTps,
                        result.restMetrics.getAvgLatencyMs(),
                        result.restMetrics.getP95LatencyMs(),
                        result.restMetrics.successCount.get(),
                        result.restMetrics.failCount.get()
                    )
            )

            println("-".repeat(120))
        }

        println("=".repeat(120) + "\n")
    }
}
