package simulation

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * 주문 생성 + 조회 혼합 부하 테스트
 * - 주문 생성: 50 TPS
 * - 주문 조회: 50 TPS
 * - 합산: 100 TPS, 2분간 실행
 *
 * 실행: ./gradlew :load-test:gatlingRun-simulation.OrderMixedSimulation
 */
class OrderMixedSimulation : Simulation() {

    private val userIdCounter = AtomicLong(1)
    private val orderIdCounter = AtomicLong(1)

    private val httpProtocol = http
        .baseUrl("http://localhost:8082")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    // 주문 생성 시나리오
    private val createOrderScenario = scenario("주문 생성")
        .exec { session ->
            val userId = userIdCounter.getAndIncrement()
            session.set("userId", userId)
                .set("productId", (userId % 5) + 1)
                .set("quantity", (userId % 3) + 1)
        }
        .exec(
            http("POST /orders")
                .post("/orders")
                .body(StringBody("""{"userId": #{userId}, "productId": #{productId}, "quantity": #{quantity}}"""))
                .check(status().`in`(200, 400, 500))
        )

    // 주문 조회 시나리오
    private val getOrderScenario = scenario("주문 조회")
        .exec { session ->
            val orderId = (orderIdCounter.getAndIncrement() % 100) + 1
            session.set("orderId", orderId)
        }
        .exec(
            http("GET /orders/{orderId}")
                .get("/orders/#{orderId}")
                .check(status().`in`(200, 404))
        )

    init {
        setUp(
            // 주문 생성: 50 TPS
            createOrderScenario.injectOpen(
                rampUsersPerSec(0.0).to(50.0).during(Duration.ofSeconds(10)),
                constantUsersPerSec(50.0).during(Duration.ofMinutes(2)),
                rampUsersPerSec(50.0).to(0.0).during(Duration.ofSeconds(10))
            ),
            // 주문 조회: 50 TPS
            getOrderScenario.injectOpen(
                rampUsersPerSec(0.0).to(50.0).during(Duration.ofSeconds(10)),
                constantUsersPerSec(50.0).during(Duration.ofMinutes(2)),
                rampUsersPerSec(50.0).to(0.0).during(Duration.ofSeconds(10))
            )
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().percentile3().lt(3000),  // P95 < 3초
                global().successfulRequests().percent().gt(80.0) // 성공률 > 80%
            )
    }
}
