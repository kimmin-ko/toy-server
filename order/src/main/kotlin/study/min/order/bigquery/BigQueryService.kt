package study.min.order.bigquery

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import study.min.order.persistence.Order

@Service
class BigQueryService(
    private val bigQueryRepository: BigQueryRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        try {
            bigQueryRepository.ensureDatasetExists()
            bigQueryRepository.ensureOrderTableExists()
            log.info("✅ [BigQuery] Initialization completed")
        } catch (e: Exception) {
            log.warn("⚠️ [BigQuery] Initialization failed (emulator may not be running): ${e.message}")
        }
    }

    /**
     * 주문 생성 시 BigQuery로 전송
     */
    fun sendOrderToBigQuery(order: Order, productId: Long, quantity: Int, price: Long) {
        val dto = OrderBigQueryDto(
            orderId = order.id?.toString() ?: "unknown",
            orderNumber = order.orderNumber,
            userId = order.userId,
            productId = productId,
            quantity = quantity,
            price = price,
            totalPrice = order.totalPrice,
            status = order.status.name
        )

        val success = bigQueryRepository.insertOrder(dto)
        if (!success) {
            log.warn("⚠️ [BigQuery] Failed to send order: ${order.orderNumber}")
        }
    }

    /**
     * 배치 전송
     */
    fun sendOrdersToBigQuery(orders: List<OrderBigQueryDto>): Int {
        return bigQueryRepository.insertOrders(orders)
    }

    /**
     * 주문 조회 (분석용)
     */
    fun getRecentOrders(limit: Int = 10): List<Map<String, Any?>> {
        return bigQueryRepository.queryOrders(limit)
    }
}
