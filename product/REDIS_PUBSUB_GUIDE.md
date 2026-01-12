# Redis Pub/Sub 구현 가이드

## 📌 개요

Redis Pub/Sub을 사용하여 **재고 변경 이벤트를 실시간으로 발행하고 구독**하는 시스템입니다.

## 🏗️ 아키텍처

```
┌─────────────────────┐
│ ProductStockService │  재고 차감
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ StockEventPublisher │  이벤트 발행 (Publish)
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   Redis Pub/Sub     │  메시지 브로커
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│StockEventSubscriber │  이벤트 구독 (Subscribe)
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  비즈니스 로직 처리  │  알림, 로깅, 분석 등
└─────────────────────┘
```

## 📝 구현 파일

### 1. 이벤트 정의 (`StockEvent.kt`)

```kotlin
// 재고 차감 이벤트
data class StockDecreasedEvent(
    val productId: Long,
    val decreasedQuantity: Int,
    val remainingStock: Int,
    val orderId: String? = null
)

// 재고 증가 이벤트
data class StockIncreasedEvent(...)

// 재고 부족 경고
data class StockLowWarningEvent(...)

// 재고 소진
data class StockOutEvent(...)
```

### 2. Pub/Sub 설정 (`RedisPubSubConfig.kt`)

채널 정의:
- `stock:decreased` - 재고 차감
- `stock:increased` - 재고 증가
- `stock:low-warning` - 재고 부족 경고
- `stock:out` - 재고 소진

### 3. 이벤트 발행자 (`StockEventPublisher.kt`)

```kotlin
@Component
class StockEventPublisher {
    fun publishStockDecreased(event: StockDecreasedEvent) {
        eventRedisTemplate.convertAndSend(stockDecreasedTopic.topic, event)
    }
}
```

### 4. 이벤트 구독자 (`StockEventSubscriber.kt`)

```kotlin
@Component
class StockEventSubscriber {
    @PostConstruct
    fun subscribeToStockEvents() {
        // 애플리케이션 시작 시 자동으로 구독 시작
        redisMessageListenerContainer.addMessageListener(...)
    }
}
```

## 🚀 사용 방법

### 1. 재고 차감 시 자동 이벤트 발행

```kotlin
@Service
class ProductStockService(
    private val stockEventPublisher: StockEventPublisher
) {
    fun decrease(productId: Long, quantity: Int, orderId: String?) {
        // 재고 차감 로직
        productStock.decrease(quantity)

        // 이벤트 발행
        stockEventPublisher.publishStockDecreased(
            StockDecreasedEvent(
                productId = productId,
                decreasedQuantity = quantity,
                remainingStock = remainingStock,
                orderId = orderId
            )
        )
    }
}
```

### 2. 이벤트 수신 및 처리

이벤트는 `StockEventSubscriber`에서 자동으로 수신됩니다:

```kotlin
class StockDecreasedListener : MessageListener {
    override fun onMessage(message: Message, pattern: ByteArray?) {
        val event = objectMapper.readValue(message.body, StockDecreasedEvent::class.java)

        // 여기에 비즈니스 로직 추가
        // - 알림 발송 (이메일, SMS, Push)
        // - 로그 기록
        // - 분석 데이터 수집
        // - 외부 시스템에 전달
    }
}
```

## 🎯 실전 활용 예시

### 1. 관리자 알림

```kotlin
class StockLowWarningListener {
    private fun handleStockLowWarning(event: StockLowWarningEvent) {
        // Slack 알림
        slackClient.sendMessage(
            "#stock-alerts",
            "⚠️ 재고 부족: 상품 ${event.productId}, 현재 ${event.currentStock}개"
        )

        // 이메일 발송
        emailService.sendToAdmin(
            subject = "재고 부족 경고",
            body = "상품 ${event.productId}의 재고가 ${event.currentStock}개 남았습니다."
        )
    }
}
```

### 2. 실시간 대시보드 업데이트

```kotlin
class StockDecreasedListener {
    private fun handleStockDecreased(event: StockDecreasedEvent) {
        // WebSocket으로 실시간 대시보드에 전송
        websocketTemplate.convertAndSend(
            "/topic/stock/${event.productId}",
            StockUpdateMessage(
                productId = event.productId,
                stock = event.remainingStock
            )
        )
    }
}
```

### 3. 자동 발주 시스템

```kotlin
class StockOutListener {
    private fun handleStockOut(event: StockOutEvent) {
        // 재고 소진 시 자동으로 발주 시스템에 요청
        purchaseOrderService.createAutoPurchaseOrder(
            productId = event.productId,
            quantity = 100 // 기본 발주 수량
        )
    }
}
```

### 4. 분석 데이터 수집

```kotlin
class StockDecreasedListener {
    private fun handleStockDecreased(event: StockDecreasedEvent) {
        // 시간대별 판매 데이터 수집
        analyticsService.recordSale(
            productId = event.productId,
            quantity = event.decreasedQuantity,
            timestamp = event.timestamp
        )
    }
}
```

## 🧪 테스트

```bash
# 테스트 실행
./gradlew :product:test --tests RedisPubSubTest

# 특정 테스트만 실행
./gradlew :product:test --tests RedisPubSubTest.testStockDecreasedEvent
```

### 테스트 시나리오

1. **재고 차감 이벤트** - 10개 차감
2. **재고 부족 경고** - 재고가 10개 이하
3. **재고 소진** - 재고가 0개
4. **재고 증가** - 입고 처리

## 📊 모니터링

### Redis CLI로 실시간 이벤트 확인

```bash
# Redis 접속
redis-cli

# 모든 채널 구독
PSUBSCRIBE stock:*

# 특정 채널만 구독
SUBSCRIBE stock:decreased
```

출력 예시:
```
1) "message"
2) "stock:decreased"
3) "{\"productId\":123,\"decreasedQuantity\":10,\"remainingStock\":90}"
```

## 🔥 장점

### 1. **비동기 처리**
- 재고 차감과 알림 발송이 분리
- 알림 실패가 재고 차감에 영향 없음

### 2. **확장성**
- 여러 구독자 추가 가능 (마이크로서비스)
- 새로운 기능 추가 시 기존 코드 수정 불필요

### 3. **실시간성**
- 이벤트 발생 즉시 처리
- 관리자/사용자에게 실시간 알림

### 4. **낮은 결합도**
- Publisher와 Subscriber가 독립적
- 한쪽이 장애나도 다른 쪽에 영향 없음

## ⚠️ 주의사항

### 1. 메시지 유실 가능성
Redis Pub/Sub은 메시지를 저장하지 않습니다. 구독자가 없으면 메시지 손실!

**해결책:**
- 중요한 이벤트는 Kafka/RabbitMQ 사용
- Redis Stream 사용 고려

### 2. At-Most-Once 전달
메시지가 최대 1번만 전달됩니다 (중복 X, 유실 O)

**해결책:**
- 멱등성(Idempotency) 보장
- 중요한 작업은 별도 큐 사용

### 3. 구독자가 느릴 때
구독자 처리가 느리면 메시지 쌓임

**해결책:**
- 비동기 처리 (@Async)
- 별도 스레드풀 사용

## 🚀 다음 단계

### 1. WebSocket 연동
실시간 대시보드 구현

### 2. 외부 서비스 연동
- Slack 알림
- 이메일/SMS 발송
- 모니터링 시스템 (Grafana)

### 3. Redis Stream으로 업그레이드
메시지 영속성 보장

### 4. 마이크로서비스 확장
다른 서비스에서도 이벤트 구독