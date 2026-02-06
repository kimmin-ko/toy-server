package study.min.order.controller

import org.springframework.web.bind.annotation.*
import study.min.order.bigquery.BigQueryService
import study.min.order.bigquery.OrderBigQueryDto
import java.time.Instant

@RestController
@RequestMapping("/api/bigquery")
class BigQueryController(
    private val bigQueryService: BigQueryService
) {

    /**
     * BigQuery 테스트용 - 샘플 주문 데이터 전송
     */
    @PostMapping("/orders/test")
    fun sendTestOrder(): Map<String, Any> {
        val testDto = OrderBigQueryDto(
            orderId = "test-${System.currentTimeMillis()}",
            orderNumber = "ORDER-TEST-${System.currentTimeMillis()}",
            userId = 1L,
            productId = 100L,
            quantity = 2,
            price = 10000L,
            totalPrice = 20000L,
            status = "CONFIRMED",
            createdAt = Instant.now()
        )

        val success = bigQueryService.sendOrdersToBigQuery(listOf(testDto)) > 0

        return mapOf(
            "success" to success,
            "order" to testDto
        )
    }

    /**
     * BigQuery 테스트용 - 배치 주문 데이터 전송
     */
    @PostMapping("/orders/batch-test")
    fun sendBatchTestOrders(@RequestParam(defaultValue = "10") count: Int): Map<String, Any> {
        val orders = (1..count).map { i ->
            OrderBigQueryDto(
                orderId = "batch-test-$i-${System.currentTimeMillis()}",
                orderNumber = "ORDER-BATCH-$i-${System.currentTimeMillis()}",
                userId = (i % 10 + 1).toLong(),
                productId = (i % 5 + 1).toLong() * 100,
                quantity = i % 5 + 1,
                price = 10000L * (i % 3 + 1),
                totalPrice = 10000L * (i % 3 + 1) * (i % 5 + 1),
                status = if (i % 2 == 0) "CONFIRMED" else "PENDING",
                createdAt = Instant.now()
            )
        }

        val successCount = bigQueryService.sendOrdersToBigQuery(orders)

        return mapOf(
            "total" to count,
            "success" to successCount,
            "failed" to (count - successCount)
        )
    }

    /**
     * BigQuery에서 최근 주문 조회
     */
    @GetMapping("/orders")
    fun getRecentOrders(@RequestParam(defaultValue = "10") limit: Int): List<Map<String, Any?>> {
        return bigQueryService.getRecentOrders(limit)
    }
}
