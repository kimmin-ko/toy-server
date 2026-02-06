package study.min.order.bigquery

import com.google.cloud.bigquery.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Repository
class BigQueryRepository(
    private val bigQuery: BigQuery
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${bigquery.dataset:test_dataset}")
    private lateinit var datasetId: String

    @Value("\${bigquery.project-id:test-project}")
    private lateinit var projectId: String

    companion object {
        private const val ORDER_TABLE = "orders"
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT
    }

    /**
     * 데이터셋이 없으면 생성
     */
    fun ensureDatasetExists() {
        try {
            val dataset = bigQuery.getDataset(datasetId)
            if (dataset == null) {
                val datasetInfo = DatasetInfo.newBuilder(datasetId)
                    .setLocation("US")
                    .build()
                bigQuery.create(datasetInfo)
                log.info("✅ [BigQuery] Dataset created: $datasetId")
            }
        } catch (e: Exception) {
            log.warn("Dataset check/create failed (may already exist): ${e.message}")
        }
    }

    /**
     * 주문 테이블이 없으면 생성
     */
    fun ensureOrderTableExists() {
        try {
            val tableId = TableId.of(projectId, datasetId, ORDER_TABLE)
            val table = bigQuery.getTable(tableId)

            if (table == null) {
                val schema = Schema.of(
                    Field.of("order_id", StandardSQLTypeName.STRING),
                    Field.of("order_number", StandardSQLTypeName.STRING),
                    Field.of("user_id", StandardSQLTypeName.INT64),
                    Field.of("product_id", StandardSQLTypeName.INT64),
                    Field.of("quantity", StandardSQLTypeName.INT64),
                    Field.of("price", StandardSQLTypeName.INT64),
                    Field.of("total_price", StandardSQLTypeName.INT64),
                    Field.of("status", StandardSQLTypeName.STRING),
                    Field.of("created_at", StandardSQLTypeName.TIMESTAMP)
                )

                val tableDefinition = StandardTableDefinition.of(schema)
                val tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build()
                bigQuery.create(tableInfo)
                log.info("✅ [BigQuery] Table created: $ORDER_TABLE")
            }
        } catch (e: Exception) {
            log.warn("Table check/create failed (may already exist): ${e.message}")
        }
    }

    /**
     * 단건 주문 데이터 INSERT (Streaming Insert)
     */
    fun insertOrder(order: OrderBigQueryDto): Boolean {
        return try {
            val tableId = TableId.of(projectId, datasetId, ORDER_TABLE)

            val rowContent = mapOf(
                "order_id" to order.orderId,
                "order_number" to order.orderNumber,
                "user_id" to order.userId,
                "product_id" to order.productId,
                "quantity" to order.quantity.toLong(),
                "price" to order.price,
                "total_price" to order.totalPrice,
                "status" to order.status,
                "created_at" to order.createdAt.atOffset(ZoneOffset.UTC).format(TIMESTAMP_FORMATTER)
            )

            val insertRequest = InsertAllRequest.newBuilder(tableId)
                .addRow(rowContent)
                .build()

            val response = bigQuery.insertAll(insertRequest)

            if (response.hasErrors()) {
                response.insertErrors.forEach { (index, errors) ->
                    errors.forEach { error ->
                        log.error("❌ [BigQuery] Insert error at row $index: ${error.message}")
                    }
                }
                false
            } else {
                log.info("✅ [BigQuery] Order inserted: ${order.orderNumber}")
                true
            }
        } catch (e: Exception) {
            log.error("❌ [BigQuery] Insert failed: ${e.message}", e)
            false
        }
    }

    /**
     * 배치 주문 데이터 INSERT (Streaming Insert)
     */
    fun insertOrders(orders: List<OrderBigQueryDto>): Int {
        if (orders.isEmpty()) return 0

        return try {
            val tableId = TableId.of(projectId, datasetId, ORDER_TABLE)

            val requestBuilder = InsertAllRequest.newBuilder(tableId)

            orders.forEach { order ->
                val rowContent = mapOf(
                    "order_id" to order.orderId,
                    "order_number" to order.orderNumber,
                    "user_id" to order.userId,
                    "product_id" to order.productId,
                    "quantity" to order.quantity.toLong(),
                    "price" to order.price,
                    "total_price" to order.totalPrice,
                    "status" to order.status,
                    "created_at" to order.createdAt.atOffset(ZoneOffset.UTC).format(TIMESTAMP_FORMATTER)
                )
                requestBuilder.addRow(rowContent)
            }

            val response = bigQuery.insertAll(requestBuilder.build())

            val successCount = orders.size - (response.insertErrors?.size ?: 0)

            if (response.hasErrors()) {
                response.insertErrors.forEach { (index, errors) ->
                    errors.forEach { error ->
                        log.error("❌ [BigQuery] Batch insert error at row $index: ${error.message}")
                    }
                }
            }

            log.info("✅ [BigQuery] Batch insert completed: $successCount/${orders.size}")
            successCount
        } catch (e: Exception) {
            log.error("❌ [BigQuery] Batch insert failed: ${e.message}", e)
            0
        }
    }

    /**
     * 주문 조회 (쿼리)
     */
    fun queryOrders(limit: Int = 10): List<Map<String, Any?>> {
        val query = """
            SELECT * FROM `$projectId.$datasetId.$ORDER_TABLE`
            ORDER BY created_at DESC
            LIMIT $limit
        """.trimIndent()

        val queryConfig = QueryJobConfiguration.newBuilder(query).build()
        val results = bigQuery.query(queryConfig)

        return results.iterateAll().map { row ->
            mapOf(
                "order_id" to row.get("order_id")?.stringValue,
                "order_number" to row.get("order_number")?.stringValue,
                "user_id" to row.get("user_id")?.longValue,
                "product_id" to row.get("product_id")?.longValue,
                "quantity" to row.get("quantity")?.longValue,
                "price" to row.get("price")?.longValue,
                "total_price" to row.get("total_price")?.longValue,
                "status" to row.get("status")?.stringValue,
                "created_at" to row.get("created_at")?.stringValue
            )
        }
    }
}
