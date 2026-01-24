# Kafka Consumer 재시도 로직 테스트 가이드

## 개요
Product Service의 Kafka Consumer가 메시지 처리 실패 시 재시도하고, 모든 재시도 실패 후 DLT(Dead Letter Topic)로 전송하는 로직을 테스트합니다.

## 재시도 로직 스펙
- **재시도 횟수**: 3회
- **백오프 전략**: Exponential Backoff (1초 → 2초 → 4초)
- **DLT 토픽**: `order-created.DLT`
- **재시도 가능 예외**: IllegalStateException, RuntimeException
- **즉시 DLT 전송 예외**: IllegalArgumentException

## 사전 준비

### 1. Kafka 실행
```bash
# Docker로 Kafka 실행
docker run -d \
  --name kafka \
  -p 9092:9092 \
  -e KAFKA_CFG_NODE_ID=0 \
  -e KAFKA_CFG_PROCESS_ROLES=controller,broker \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@localhost:9093 \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  bitnami/kafka:latest

# Kafka 상태 확인
docker logs kafka
```

### 2. 서비스 실행
```bash
# Product Service 실행 (Consumer)
./gradlew :product:bootRun

# Order Service 실행 (Producer)
./gradlew :order:bootRun
```

## 테스트 시나리오

### 시나리오 1: 정상 처리
주문 ID에 "FAIL"이 포함되지 않으면 정상 처리됩니다.

```bash
# Order 생성 API 호출
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-001",
    "productId": 1,
    "quantity": 5
  }'
```

**예상 결과**:
- Order Service: Kafka 이벤트 발행 성공 로그
- Product Service: Consumer가 1회 시도로 정상 처리
- 로그: `✅ [Kafka Consumer] Successfully processed order-created event`

### 시나리오 2: 재시도 후 성공
주문 ID에 "FAIL"이 포함되면 처음 3회는 실패, 4회차에 성공합니다.

**테스트 방법**:
Product Service의 `OrderEventConsumer.kt` 코드를 보면 다음 로직이 있습니다:
```kotlin
if (event.orderId.contains("FAIL", ignoreCase = true) && retryCount <= 3) {
    throw IllegalStateException("Simulated failure for retry testing")
}
```

주문을 생성하면 Order ID가 자동 생성되므로, 직접 Kafka에 메시지를 발행해야 합니다.

```bash
# Kafka Producer Console 실행
docker exec -it kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-created

# 다음 JSON 입력 (한 줄로)
{"orderId":"ORDER-FAIL-001","productId":1,"quantity":10,"price":10000,"customerId":"customer-1","timestamp":"2026-01-24T10:00:00Z"}
```

**예상 결과**:
- 1초 후 1차 재시도 (총 2회 시도)
- 2초 후 2차 재시도 (총 3회 시도)
- 4초 후 3차 재시도 (총 4회 시도)
- 4회차에 성공 (`retryCount > 3`이므로 예외 발생하지 않음)
- 로그:
  ```
  ⚠️ [Kafka Consumer] Retry attempt 1 failed
  ⚠️ [Kafka Consumer] Retry attempt 2 failed
  ⚠️ [Kafka Consumer] Retry attempt 3 failed
  ✅ [Kafka Consumer] Successfully processed order-created event
  ```

### 시나리오 3: 모든 재시도 실패 후 DLT 전송
영구적으로 실패하도록 하려면 `OrderEventConsumer.kt`의 조건을 수정하거나, 존재하지 않는 productId를 사용합니다.

```bash
# Kafka Producer Console에서 입력
{"orderId":"ORDER-FAIL-PERMANENT","productId":999,"quantity":10,"price":10000,"customerId":"customer-1","timestamp":"2026-01-24T10:00:00Z"}
```

**예상 결과**:
- 3회 재시도 모두 실패
- DLT 토픽 `order-created.DLT`로 메시지 전송
- 로그:
  ```
  ⚠️ [Kafka Consumer] Retry attempt 1 failed
  ⚠️ [Kafka Consumer] Retry attempt 2 failed
  ⚠️ [Kafka Consumer] Retry attempt 3 failed
  ❌ [Kafka Consumer] All retries exhausted. Sending to DLT
  💀 [Kafka Consumer DLT] Received failed event in DLT: orderId=ORDER-FAIL-PERMANENT
  ```

**DLT 메시지 확인**:
```bash
# DLT Consumer Console 실행
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-created.DLT \
  --from-beginning
```

## Kafka 토픽 관리

### 토픽 목록 조회
```bash
docker exec -it kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

### 토픽 메시지 확인
```bash
# order-created 토픽
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-created \
  --from-beginning

# order-created.DLT 토픽
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-created.DLT \
  --from-beginning
```

### 토픽 삭제 (테스트 초기화)
```bash
docker exec -it kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --delete \
  --topic order-created

docker exec -it kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --delete \
  --topic order-created.DLT
```

## 재시도 로직 코드 위치

- **Consumer 설정**: `product/src/main/kotlin/study/min/product/config/KafkaConsumerConfig.kt`
- **Consumer 로직**: `product/src/main/kotlin/study/min/product/event/OrderEventConsumer.kt`
- **Producer 설정**: `order/src/main/resources/application.yml`
- **Producer 로직**: `order/src/main/kotlin/study/min/order/event/OrderEventPublisher.kt`

## 주의사항
- 재시도 테스트를 위한 `retryCountMap`은 테스트 목적이며, 운영 환경에서는 제거하거나 별도 모니터링 시스템 사용 권장
- DLT 메시지는 수동 처리 또는 별도 알림 시스템과 연동 필요
- Kafka Streams 또는 Redis Streams를 사용하면 중복 수신 문제 해결 가능