package study.min.order.bigquery

import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BigQueryIntegrationTest {

    private lateinit var bigQuery: BigQuery

    companion object {
        private const val PROJECT_ID = "test-project"
        private const val DATASET_ID = "test_dataset"
        private const val TABLE_ID = "orders"
        private const val EMULATOR_HOST = "http://localhost:9050"
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    @BeforeAll
    fun setup() {
        bigQuery = BigQueryOptions.newBuilder()
            .setProjectId(PROJECT_ID)
            .setHost(EMULATOR_HOST)
            .setCredentials(NoCredentials.getInstance())
            .build()
            .service

        ensureTableExists()
        println("[OK] BigQuery Emulator connected: $EMULATOR_HOST")
    }

    private fun ensureTableExists() {
        try {
            val tableId = TableId.of(PROJECT_ID, DATASET_ID, TABLE_ID)
            val existingTable = bigQuery.getTable(tableId)

            if (existingTable != null) {
                println("[INFO] Table already exists: $TABLE_ID")
                return
            }

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
            println("[OK] Table created: $TABLE_ID")
        } catch (e: Exception) {
            println("[WARN] Table setup: ${e.message}")
        }
    }

    private fun insertOrderByQuery(order: OrderBigQueryDto): Boolean {
        val timestamp = order.createdAt.atOffset(ZoneOffset.UTC).format(TIMESTAMP_FORMATTER)

        val insertQuery = """
            INSERT INTO `$PROJECT_ID.$DATASET_ID.$TABLE_ID`
            (order_id, order_number, user_id, product_id, quantity, price, total_price, status, created_at)
            VALUES
            ('${order.orderId}', '${order.orderNumber}', ${order.userId}, ${order.productId},
             ${order.quantity}, ${order.price}, ${order.totalPrice}, '${order.status}',
             TIMESTAMP('$timestamp'))
        """.trimIndent()

        return try {
            val queryConfig = QueryJobConfiguration.newBuilder(insertQuery).build()
            bigQuery.query(queryConfig)
            true
        } catch (e: Exception) {
            println("[ERROR] INSERT failed: ${e.message}")
            false
        }
    }

    @Test
    @Order(1)
    fun test1_insertSingleOrder() {
        // Given
        val orderNumber = "ORDER-TEST-${System.currentTimeMillis()}"
        val order = OrderBigQueryDto(
            orderId = "test-single-1",
            orderNumber = orderNumber,
            userId = 123L,
            productId = 100L,
            quantity = 2,
            price = 15000L,
            totalPrice = 30000L,
            status = "CONFIRMED",
            createdAt = Instant.now()
        )

        // When
        val insertSuccess = insertOrderByQuery(order)

        // Then
        assertTrue(insertSuccess, "INSERT should succeed")

        // Verify
        val query = """
            SELECT * FROM `$PROJECT_ID.$DATASET_ID.$TABLE_ID`
            WHERE order_number = '$orderNumber'
        """.trimIndent()

        val queryConfig = QueryJobConfiguration.newBuilder(query).build()
        val results = bigQuery.query(queryConfig)
        val rows = results.iterateAll().toList()

        println("")
        println("==================================================")
        println("         Single INSERT Test Result")
        println("==================================================")
        println("Rows found: ${rows.size}")
        rows.forEach { row ->
            println("--------------------------------------------------")
            println("  order_id     : ${row.get("order_id").stringValue}")
            println("  order_number : ${row.get("order_number").stringValue}")
            println("  user_id      : ${row.get("user_id").longValue}")
            println("  product_id   : ${row.get("product_id").longValue}")
            println("  quantity     : ${row.get("quantity").longValue}")
            println("  price        : ${row.get("price").longValue}")
            println("  total_price  : ${row.get("total_price").longValue}")
            println("  status       : ${row.get("status").stringValue}")
        }
        println("==================================================")
        println("")

        assertEquals(1, rows.size, "Should find 1 row")
        assertEquals(orderNumber, rows[0].get("order_number").stringValue)
        assertEquals(123L, rows[0].get("user_id").longValue)
        assertEquals(30000L, rows[0].get("total_price").longValue)

        println("[OK] Single INSERT test passed!")
    }

    @Test
    @Order(2)
    fun test2_insertBatchOrders() {
        // Given
        val batchId = System.currentTimeMillis()
        val orders = (1..5).map { i ->
            OrderBigQueryDto(
                orderId = "batch-$batchId-$i",
                orderNumber = "ORDER-BATCH-$batchId-$i",
                userId = (i * 10).toLong(),
                productId = (i * 100).toLong(),
                quantity = i,
                price = 10000L * i,
                totalPrice = 10000L * i * i,
                status = if (i % 2 == 0) "CONFIRMED" else "PENDING",
                createdAt = Instant.now()
            )
        }

        // When
        var successCount = 0
        orders.forEach { order ->
            if (insertOrderByQuery(order)) successCount++
        }

        // Then
        assertEquals(5, successCount, "All 5 inserts should succeed")

        // Verify
        val query = """
            SELECT * FROM `$PROJECT_ID.$DATASET_ID.$TABLE_ID`
            WHERE order_number LIKE 'ORDER-BATCH-$batchId-%'
            ORDER BY order_number
        """.trimIndent()

        val queryConfig = QueryJobConfiguration.newBuilder(query).build()
        val results = bigQuery.query(queryConfig)
        val rows = results.iterateAll().toList()

        println("")
        println("======================================================================")
        println("                    Batch INSERT Test Result")
        println("======================================================================")
        println("Rows found: ${rows.size}")
        println("----------------------------------------------------------------------")
        println(String.format("%-28s | %8s | %12s | %10s", "order_number", "user_id", "total_price", "status"))
        println("----------------------------------------------------------------------")
        rows.forEach { row ->
            println(String.format("%-28s | %8d | %12d | %10s",
                row.get("order_number").stringValue,
                row.get("user_id").longValue,
                row.get("total_price").longValue,
                row.get("status").stringValue))
        }
        println("======================================================================")
        println("")

        assertEquals(5, rows.size, "Should find 5 rows")
        println("[OK] Batch INSERT test passed!")
    }

    @Test
    @Order(3)
    fun test3_queryAllOrders() {
        // Count
        val countQuery = """
            SELECT COUNT(*) as total_count FROM `$PROJECT_ID.$DATASET_ID.$TABLE_ID`
        """.trimIndent()

        val countConfig = QueryJobConfiguration.newBuilder(countQuery).build()
        val countResults = bigQuery.query(countConfig)
        val totalCount = countResults.iterateAll().first().get("total_count").longValue

        // Query all
        val allDataQuery = """
            SELECT * FROM `$PROJECT_ID.$DATASET_ID.$TABLE_ID`
            ORDER BY created_at DESC
            LIMIT 10
        """.trimIndent()

        val allDataConfig = QueryJobConfiguration.newBuilder(allDataQuery).build()
        val allDataResults = bigQuery.query(allDataConfig)
        val rows = allDataResults.iterateAll().toList()

        println("")
        println("================================================================================")
        println("                       BigQuery Data Summary")
        println("================================================================================")
        println("Total records: $totalCount")
        println("--------------------------------------------------------------------------------")
        println(String.format("%-20s | %-30s | %8s | %10s", "order_id", "order_number", "user_id", "status"))
        println("--------------------------------------------------------------------------------")
        rows.forEach { row ->
            println(String.format("%-20s | %-30s | %8d | %10s",
                row.get("order_id").stringValue,
                row.get("order_number").stringValue,
                row.get("user_id").longValue,
                row.get("status").stringValue))
        }
        println("================================================================================")
        println("")
        println("[OK] Data successfully stored in BigQuery Emulator!")
        println("================================================================================")
        println("")

        assertTrue(totalCount >= 6, "Should have at least 6 records (1 single + 5 batch)")
    }
}
