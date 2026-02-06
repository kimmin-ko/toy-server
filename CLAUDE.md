# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Toy-server는 **마이크로서비스 아키텍처(MSA)** 학습용 프로젝트로, 이커머스 시스템을 구현합니다:
- **Product Service**: 상품 및 재고 관리 (분산락 적용)
- **Order Service**: 주문 관리 (gRPC로 Product 서비스 호출, BigQuery 주문 분석)
- **서비스 간 통신**: gRPC (동기 호출), REST API
- **이벤트 기반 아키텍처**:
  - Redis Pub/Sub (재고 변경 알림)
  - Kafka (주문 이벤트 발행/소비, 재시도 로직)

## 빌드 및 실행 명령어

### 사전 준비
```bash
# Java 21 설정
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 필수 서비스 실행 (Docker)
docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8          # Product DB
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=root postgres:latest    # Order DB
docker run -d -p 6379:6379 redis

# Kafka (주문 이벤트 메시징)
docker run -d --name kafka -p 9092:9092 \
  -e KAFKA_CFG_NODE_ID=0 \
  -e KAFKA_CFG_PROCESS_ROLES=controller,broker \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@localhost:9093 \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  bitnami/kafka:latest
```

### 데이터베이스 생성
```bash
# Product DB (MySQL)
mysql -u root -p
CREATE DATABASE product;

# Order DB (PostgreSQL)
psql -U postgres
CREATE DATABASE orders;
```

### 빌드
```bash
# 전체 빌드
./gradlew build

# 특정 서비스 빌드
./gradlew :product:build
./gradlew :order:build

# 테스트 제외 빌드
./gradlew build -x test

# Proto 파일에서 gRPC 코드 생성 (product-api 모듈에서 관리)
./gradlew :product-api:generateProto
```

### 서비스 실행
```bash
# Product 서비스 (터미널 1)
./gradlew :product:bootRun
# HTTP: localhost:8081, gRPC: localhost:8091

# Order 서비스 (터미널 2)
./gradlew :order:bootRun
# HTTP: localhost:8082
```

### 테스트
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 실행
./gradlew :product:test --tests ProductStockServiceConcurrencyTest
./gradlew :product:test --tests RedisPubSubTest
./gradlew :product:test --tests GrpcVsRestPerformanceTest
```

## 아키텍처

### 서비스 간 통신 구조
```
Order Service (Client)  ──gRPC/REST──>  Product Service (Server)
     │                                       │
     ├─ REST API (8082)                      ├─ REST API (8081)
     ├─ gRPC Client                          │   └─ /api/products/*
     ├─ REST Client                          ├─ gRPC Server (8091)
     ├─ PostgreSQL (orders DB)               ├─ MySQL (product DB)
     ├─ BigQuery (주문 분석)                   ├─ Redis (분산락)
     └─ Kafka Producer                       ├─ Redis Pub/Sub (재고 이벤트)
              │                              └─ Kafka Consumer
              └──> Kafka (order-created) ────┘
                        └─> DLT (재시도 실패)
```

**통신 방식**:
- **gRPC (주력)**: 타입 안전성, 효율적인 직렬화, HTTP/2 멀티플렉싱
- **REST API**: 표준 HTTP/JSON, gRPC와 동일한 비즈니스 로직 재사용
- **Kafka**: 비동기 이벤트 기반 통신, 재시도 및 DLT 지원

### 데이터베이스 구성
- **Product Service**: MySQL 8 (`jdbc:mysql://localhost:3306/product`)
- **Order Service**: PostgreSQL (`jdbc:postgresql://localhost:5432/orders`)
- 두 서비스 모두 Flyway 마이그레이션 사용 (`src/main/resources/db/migration/`)

### gRPC 구현 방식
- **Proto 파일**: `product-api/src/main/proto/product_service.proto` (별도 모듈로 분리)
- **product-api 모듈**: Proto 파일과 생성된 gRPC 스텁 코드를 포함하는 공유 라이브러리
- **Product Service**: `ProductServiceGrpc.ProductServiceImplBase`를 상속하고 `@Component`로 등록
- **수동 gRPC 서버**: `GrpcServerConfig.kt`에서 8091 포트로 수동 시작 (Spring gRPC auto-config이 Spring Boot 4.0.1에서 작동하지 않음)
- **Order Service**: `ManagedChannel`로 Product gRPC 서버 연결

**Proto 파일 변경 시**:
1. `product-api/src/main/proto/product_service.proto` 수정
2. `./gradlew :product-api:generateProto` 실행
3. product와 order 모듈 재빌드 (의존성으로 자동 반영)

### 분산락 패턴
Product Service는 **Redisson 분산락**을 AOP로 적용:
```kotlin
@DistributedLock(
    key = "'stock:' + #productId",
    waitTime = 10,
    leaseTime = 5
)
@Transactional
fun decrease(productId: Long, quantity: Int, orderId: String?)
```

- `@DistributedLock` 어노테이션 + SpEL로 동적 키 생성
- `DistributedLockAspect`는 `@Order(Ordered.HIGHEST_PRECEDENCE)`로 `@Transactional`보다 먼저 실행
- 트랜잭션 시작 전 락 획득, 트랜잭션 커밋 후 락 해제

### Redis Pub/Sub
- 직렬화: `RedisSerializer.json()` 사용 (GenericJackson2JsonRedisSerializer 아님)
- 이벤트: StockDecreasedEvent, StockIncreasedEvent, StockLowWarningEvent, StockOutEvent
- 채널: `stock:decreased`, `stock:increased`, `stock:low-warning`, `stock:out`

### Kafka 이벤트 기반 아키텍처
- **재시도**: 3회, Exponential Backoff (1초 → 2초 → 4초)
- **재시도 가능 예외**: IllegalStateException, RuntimeException
- **즉시 DLT 전송 예외**: IllegalArgumentException
- Topic: `order-created` (메인), `order-created.DLT` (Dead Letter Topic)
- Consumer Group: `product-service-group`
- Spring Boot auto-configuration으로 KafkaTemplate, ConsumerFactory 자동 생성
- 커스텀 ErrorHandler만 Bean으로 등록하여 재시도 로직 적용
- 테스트 가이드: `product/KAFKA_RETRY_TEST_GUIDE.md` 참조

### BigQuery 연동 (Order Service)
Order Service는 BigQuery에 주문 데이터를 적재하여 분석:
- 설정: `bigquery.*` (application.yml), 에뮬레이터 지원 (localhost:9050)
- 구현: `order/src/main/kotlin/study/min/order/bigquery/` (BigQueryService, BigQueryRepository, OrderBigQueryDto)

### 주문 생성 플로우
1. Order Service → gRPC `checkStock()` → Product Service (재고 확인)
2. Order Service → gRPC `getProduct()` → Product Service (가격 조회)
3. Order Service → Order 엔티티 생성 (PENDING 상태)
4. Order Service → gRPC `decreaseStock()` → Product Service (분산락 적용)
5. Order Service → Order 상태를 CONFIRMED로 변경
6. Order Service → Kafka 이벤트 발행 (`order-created` 토픽)
7. Product Service → Redis Pub/Sub 이벤트 발행 (재고 차감, 재고 부족 경고, 재고 소진)
8. Product Service → Kafka 이벤트 소비 (비즈니스 로직 처리, 재시도 로직 적용)

### JPA 패턴
- **BaseEntity**: `createdAt`, `updatedAt`를 JPA Auditing으로 자동 관리
- **Repository 확장 함수**: Kotlin extension function으로 null-safe 메서드 제공
- **allOpen**: `@Entity`, `@MappedSuperclass`, `@Embeddable` 클래스에 open 키워드 자동 적용

### 기술 스택
- **언어**: Kotlin 2.2.21
- **프레임워크**: Spring Boot 4.0.1, Spring 7.0.2
- **JVM**: Java 21 (Virtual Threads 활성화)
- **데이터베이스**: MySQL 8 (Product), PostgreSQL (Order) + Flyway 마이그레이션
- **캐시/락**: Redis + Redisson
- **gRPC**: io.grpc + Protocol Buffers (proto3), Spring gRPC 1.0.1
- **메시징**: Kafka (Spring Kafka with auto-configuration)
- **분석**: Google Cloud BigQuery (Order Service)
- **빌드**: Gradle (Groovy DSL)
- **테스트**: JUnit 5, Testcontainers (MySQL, PostgreSQL, Kafka)

## 자주 발생하는 문제

### gRPC 서버가 시작되지 않음
- `GrpcServerConfig.kt`가 존재하고 제대로 로드되는지 확인
- 로그 확인: `✅ [gRPC Server] Started on port: 8091`
- Spring gRPC auto-configuration이 실패할 수 있음 (수동 서버 시작 구현됨)

### gRPC 호출 시 Connection Refused
- Product Service가 실행 중인지 확인: `lsof -i :8091`
- 실제 연결은 lazy (첫 RPC 호출 시 연결)

### Kafka 연결 실패
- Kafka가 실행 중인지 확인: `docker ps | grep kafka`
- application.yml의 `spring.kafka.bootstrap-servers` 확인 (기본값: localhost:9092)

## REST API 수동 테스트

```bash
# 재고 확인
curl -X POST http://localhost:8081/api/products/stock/check \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 10}'

# 상품 조회
curl http://localhost:8081/api/products/1

# 재고 차감
curl -X POST http://localhost:8081/api/products/1/stock/decrease \
  -H "Content-Type: application/json" \
  -d '{"quantity": 5, "orderId": "TEST-001"}'
```
