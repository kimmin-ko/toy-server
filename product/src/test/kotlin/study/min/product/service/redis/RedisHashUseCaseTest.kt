package study.min.product.service.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import study.min.product.ProductApplication

/**
 * Redis Hash 실전 사용 사례 테스트
 */
@SpringBootTest(classes = [ProductApplication::class])
class RedisHashUseCaseTest {

    @Autowired
    private lateinit var redisService: RedisService

    @DisplayName("사용 사례 1: 장바구니 구현")
    @Test
    fun shoppingCart() {
        // given - 사용자 1의 장바구니
        val cartKey = "cart:user:1"

        // when - 상품 추가 (상품ID: 수량)
        redisService.putHash(cartKey, "product:100", 2)  // MacBook 2개
        redisService.putHash(cartKey, "product:101", 1)  // iPhone 1개
        redisService.putHash(cartKey, "product:102", 5)  // AirPods 5개

        // 수량 변경
        redisService.putHash(cartKey, "product:100", 3)  // MacBook 3개로 변경

        // 상품 삭제
        redisService.deleteHash(cartKey, "product:102")

        // then - 장바구니 조회
        val cart = redisService.getAllHash(cartKey)
        assertThat(cart).hasSize(2)
        assertThat(cart["product:100"]).isEqualTo(3)
        assertThat(cart["product:101"]).isEqualTo(1)
        assertThat(cart).doesNotContainKey("product:102")
    }

    @DisplayName("사용 사례 2: 실시간 재고 관리 (동시성 안전)")
    @Test
    fun inventoryManagement() {
        // given - 상품별 재고 정보
        val product100 = "inventory:product:100"

        // when - 재고 정보 초기화
        redisService.putHash(product100, "stock", 100)
        redisService.putHash(product100, "reserved", 0)
        redisService.putHash(product100, "sold", 0)

        // ✅ Atomic 연산 사용 - 동시성 안전!
        // 주문 발생 (재고 감소 5개)
        redisService.decrementHash(product100, "stock", 5)
        redisService.incrementHash(product100, "sold", 5)

        // 추가 주문 발생 (재고 감소 3개)
        redisService.decrementHash(product100, "stock", 3)
        redisService.incrementHash(product100, "sold", 3)

        // then - 재고 확인
        val inventory = redisService.getAllHash(product100)
        assertThat(inventory["stock"]).isEqualTo(92)  // 100 - 5 - 3
        assertThat(inventory["sold"]).isEqualTo(8)    // 0 + 5 + 3
    }

    @DisplayName("사용 사례 3: 사용자 활동 통계")
    @Test
    fun userActivityStats() {
        // given - 사용자 활동 통계
        val statsKey = "stats:user:1:daily:2024-01-10"

        // when - 다양한 활동 카운트
        redisService.putHash(statsKey, "loginCount", 3)
        redisService.putHash(statsKey, "pageViews", 50)
        redisService.putHash(statsKey, "clicks", 120)
        redisService.putHash(statsKey, "purchases", 2)

        // 추가 활동 발생
        val clicks = redisService.getHash(statsKey, "clicks") as Int
        redisService.putHash(statsKey, "clicks", clicks + 10)

        // then - 통계 조회
        val stats = redisService.getAllHash(statsKey)
        assertThat(stats["loginCount"]).isEqualTo(3)
        assertThat(stats["clicks"]).isEqualTo(130)
        assertThat(stats["purchases"]).isEqualTo(2)
    }

    @DisplayName("사용 사례 4: 게시글 메타데이터")
    @Test
    fun postMetadata() {
        // given - 게시글 메타 정보
        val postKey = "post:1001:meta"

        // when - 메타데이터 저장
        redisService.putHash(postKey, "viewCount", 0)
        redisService.putHash(postKey, "likeCount", 0)
        redisService.putHash(postKey, "commentCount", 0)
        redisService.putHash(postKey, "shareCount", 0)

        // 사용자 활동 반영
        val views = redisService.getHash(postKey, "viewCount") as Int
        val likes = redisService.getHash(postKey, "likeCount") as Int
        redisService.putHash(postKey, "viewCount", views + 1)
        redisService.putHash(postKey, "likeCount", likes + 1)

        // then - 특정 필드만 조회 (효율적)
        val viewCount = redisService.getHash(postKey, "viewCount")
        val likeCount = redisService.getHash(postKey, "likeCount")
        assertThat(viewCount).isEqualTo(1)
        assertThat(likeCount).isEqualTo(1)
    }

    @DisplayName("사용 사례 5: 세션 관리")
    @Test
    fun sessionManagement() {
        // given - 세션 정보
        val sessionKey = "session:xyz789"

        // when - 세션 생성
        redisService.putHash(sessionKey, "userId", 1)
        redisService.putHash(sessionKey, "username", "kim")
        redisService.putHash(sessionKey, "role", "USER")
        redisService.putHash(sessionKey, "loginTime", System.currentTimeMillis())
        redisService.putHash(sessionKey, "lastAccessTime", System.currentTimeMillis())

        // 세션 업데이트 (마지막 접근 시간만 갱신)
        Thread.sleep(100)
        redisService.putHash(sessionKey, "lastAccessTime", System.currentTimeMillis())

        // then - 세션 정보 조회
        val session = redisService.getAllHash(sessionKey)
        assertThat(session["userId"]).isEqualTo(1)
        assertThat(session["username"]).isEqualTo("kim")
        assertThat(session["role"]).isEqualTo("USER")

        // 특정 필드만 확인
        val userId = redisService.getHash(sessionKey, "userId")
        assertThat(userId).isEqualTo(1)
    }

    @DisplayName("사용 사례 6: 애플리케이션 설정")
    @Test
    fun applicationConfiguration() {
        // given - 애플리케이션 전역 설정
        val configKey = "app:config:prod"

        // when - 설정 저장
        redisService.putHash(configKey, "maintenanceMode", false)
        redisService.putHash(configKey, "maxUploadSizeMB", 10)
        redisService.putHash(configKey, "allowSignup", true)
        redisService.putHash(configKey, "apiRateLimit", 1000)

        // 동적 설정 변경 (점검 모드 활성화)
        redisService.putHash(configKey, "maintenanceMode", true)

        // then - 설정 확인
        val config = redisService.getAllHash(configKey)
        assertThat(config["maintenanceMode"]).isEqualTo(true)
        assertThat(config["maxUploadSizeMB"]).isEqualTo(10)
        assertThat(config["allowSignup"]).isEqualTo(true)
    }
}