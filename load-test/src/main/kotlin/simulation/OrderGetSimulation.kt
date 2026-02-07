package simulation

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * 주문 조회 API 부하 테스트
 * GET /orders/{orderId} - 100 TPS, 2분간 실행
 *
 * 실행: ./gradlew :load-test:gatlingRun-simulation.OrderGetSimulation
 */
class OrderGetSimulation : Simulation() {

    private val orderIdCounter = AtomicLong(1)

    private val httpProtocol = http
        .baseUrl("http://localhost:8082")
        .acceptHeader("application/json")

    private val getOrderScenario = scenario("주문 조회 부하 테스트")
        .exec { session ->
            // orderId 1~100 순환 (DB에 존재하는 주문 ID 범위)
            val orderId = (orderIdCounter.getAndIncrement() % 100) + 1
            session.set("orderId", orderId)
        }
        .exec(
            http("GET /orders/{orderId} - 주문 조회")
                .get("/orders/#{orderId}")
                .check(status().`in`(200, 404))
        )

    init {
        setUp(
            getOrderScenario.injectOpen(
                // 워밍업: 10초간 0 → 100 TPS로 점진 증가
                rampUsersPerSec(0.0).to(100.0).during(Duration.ofSeconds(10)),
                // 본 테스트: 2분간 100 TPS 유지
                constantUsersPerSec(100.0).during(Duration.ofMinutes(2)),
                // 쿨다운: 10초간 100 → 0 TPS로 점진 감소
                rampUsersPerSec(100.0).to(0.0).during(Duration.ofSeconds(10))
            )
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().percentile3().lt(1000),  // P95 < 1초
                global().successfulRequests().percent().gt(90.0) // 성공률 > 90%
            )
    }
}
