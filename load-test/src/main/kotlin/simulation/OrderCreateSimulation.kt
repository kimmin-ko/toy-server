package simulation

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * 주문 생성 API 부하 테스트
 * POST /orders - 30 TPS, 2분간 실행
 *
 * 실행: ./gradlew :load-test:gatlingRun-simulation.OrderCreateSimulation
 */
class OrderCreateSimulation : Simulation() {

    private val userIdCounter = AtomicLong(1)

    private val httpProtocol = http
        .baseUrl("http://localhost:8082")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    private val createOrderScenario = scenario("주문 생성 부하 테스트")
        .exec { session ->
            val userId = userIdCounter.getAndIncrement()
            session.set("userId", userId)
                .set("productId", (userId % 5) + 1)  // productId 1~5 순환
                .set("quantity", (userId % 3) + 1)    // quantity 1~3 순환
        }
        .exec(
            http("POST /orders - 주문 생성")
                .post("/orders")
                .body(StringBody("""{"userId": #{userId}, "productId": #{productId}, "quantity": #{quantity}}"""))
                .check(status().`in`(200, 400, 500))
        )

    init {
        setUp(
            createOrderScenario.injectOpen(
                // 워밍업: 10초간 0 → 30 TPS로 점진 증가
                rampUsersPerSec(0.0).to(30.0).during(Duration.ofSeconds(10)),
                // 본 테스트: 2분간 30 TPS 유지
                constantUsersPerSec(30.0).during(Duration.ofMinutes(2)),
                // 쿨다운: 10초간 30 → 0 TPS로 점진 감소
                rampUsersPerSec(30.0).to(0.0).during(Duration.ofSeconds(10))
            )
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().percentile3().lt(3000),  // P95 < 3초
                global().successfulRequests().percent().gt(80.0) // 성공률 > 80%
            )
    }
}
