# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

Toy-server는 **마이크로서비스 아키텍처(MSA)** 학습용 프로젝트로, 이커머스 시스템을 구현합니다:
- **Product Service**: 상품 및 재고 관리 (분산락 적용)
- **Order Service**: 주문 관리 (gRPC로 Product 서비스 호출)
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
docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8
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
mysql -u root -p
CREATE DATABASE product;
CREATE DATABASE orders;  # 'order'는 예약어이므로 'orders' 사용
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
# HTTP: localhost:8081
# gRPC: localhost:8091

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

# 포트 확인
lsof -i :8081  # Product HTTP
lsof -i :8091  # Product gRPC
lsof -i :8082  # Order HTTP
```

## 아키텍처

### 서비스 간 통신 구조
```
Order Service (Client)  ──gRPC/REST──>  Product Service (Server)
     │                                       │
     │                                       ├─ REST API (8081)
     ├─ REST API (8082)                      │   └─ /api/products/*
     ├─ gRPC Client                          ├─ gRPC Server (8091)
     ├─ REST Client                          ├─ MySQL (product DB)
     ├─ MySQL (orders DB)                    ├─ Redis (분산락)
     └─ Kafka Producer                       ├─ Redis Pub/Sub (재고 이벤트)
              │                              └─ Kafka Consumer
              └──> Kafka (order-created) ────┘
                        └─> DLT (재시도 실패)
```

**통신 방식**:
- **gRPC (주력)**: 타입 안전성, 효율적인 직렬화, HTTP/2 멀티플렉싱
- **REST API**: 표준 HTTP/JSON, gRPC와 동일한 비즈니스 로직 재사용
- **Kafka**: 비동기 이벤트 기반 통신, 재시도 및 DLT 지원

### gRPC 구현 방식
- **Proto 파일**: `product-api/src/main/proto/product_service.proto` (별도 모듈로 분리)
- **product-api 모듈**: Proto 파일과 생성된 gRPC 스텁 코드를 포함하는 공유 라이브러리
- **Product Service**: `ProductServiceGrpc.ProductServiceImplBase`를 상속하고 `@Component`로 등록
- **수동 gRPC 서버**: `GrpcServerConfig.kt`에서 8091 포트로 수동 시작 (Spring gRPC auto-config이 Spring Boot 4.0.1에서 작동하지 않음)
- **Order Service**: `ManagedChannel`로 Product gRPC 서버 연결
- **Proto 동기화**: `product-api` 모듈을 의존성으로 추가하여 자동 동기화 (`implementation project(':product-api')`)

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

**구현 세부사항**:
- `@DistributedLock` 어노테이션 + SpEL로 동적 키 생성
- `DistributedLockAspect`는 `@Order(Ordered.HIGHEST_PRECEDENCE)`로 `@Transactional`보다 먼저 실행
- 트랜잭션 시작 전 락 획득, 트랜잭션 커밋 후 락 해제

### Redis Pub/Sub 아키텍처
```
ProductStockService.decrease()
    └─> StockEventPublisher.publishStockDecreased()
            └─> Redis Pub/Sub (stock:decreased 채널)
                    └─> StockEventSubscriber가 수신
                            └─> 비즈니스 로직 (로깅, 알림, 분석 등)
```

**핵심 사항**:
- 직렬화: `RedisSerializer.json()` 사용 (GenericJackson2JsonRedisSerializer 아님)
- 이벤트 종류: StockDecreasedEvent, StockIncreasedEvent, StockLowWarningEvent, StockOutEvent
- 채널: `stock:decreased`, `stock:increased`, `stock:low-warning`, `stock:out`
- 제약사항: 여러 인스턴스에서 중복 수신 (운영 환경에서는 Redis Streams 또는 멱등성 처리 고려)

### Kafka 이벤트 기반 아키텍처
```
Order Service (Producer)
    └─> OrderEventPublisher.publishOrderCreated()
            └─> Kafka Topic (order-created)
                    └─> Product Service (Consumer)
                            ├─> OrderEventConsumer.consumeOrderCreated()
                            │       ├─ 성공: 이벤트 처리 완료
                            │       └─ 실패: 재시도 (3회, Exponential Backoff)
                            └─> 재시도 실패 시 DLT (order-created.DLT)
                                    └─> OrderEventConsumer.consumeOrderCreatedDLT()
```

**재시도 로직**:
- **재시도 횟수**: 3회
- **백오프 전략**: Exponential Backoff (1초 → 2초 → 4초)
- **재시도 가능 예외**: IllegalStateException, RuntimeException
- **즉시 DLT 전송 예외**: IllegalArgumentException
- **DLT 처리**: 수동 처리, 알림, 모니터링 시스템 연동

**핵심 사항**:
- 직렬화: Spring Kafka JsonSerializer (Spring Boot auto-configuration 사용)
- 이벤트: OrderCreatedEvent (orderId, productId, quantity, price, customerId)
- Topic: `order-created` (메인), `order-created.DLT` (Dead Letter Topic)
- Consumer Group: `product-service-group`
- 테스트 가이드: `product/KAFKA_RETRY_TEST_GUIDE.md` 참조

**Spring Boot 4.0 설정 방식**:
- `JsonSerializer` 직접 사용 대신 application.yml 설정 사용
- Spring Boot auto-configuration으로 KafkaTemplate, ConsumerFactory 자동 생성
- 커스텀 ErrorHandler만 Bean으로 등록하여 재시도 로직 적용

### 데이터베이스 스키마

**Product Service**:
- `product`: 상품 마스터 데이터
- `product_stock`: 재고 (낙관적 락 적용)

**Order Service**:
- `order`: 주문 헤더 (order_item과 1:N 관계)
- `order_item`: 주문 상품 (productId, quantity, 주문 당시 price)

### JPA 패턴
- **BaseEntity**: `createdAt`, `updatedAt`를 JPA Auditing으로 자동 관리 (`@CreatedDate`, `@LastModifiedDate`)
- **Repository 확장 함수**: Kotlin extension function으로 null-safe 메서드 제공
  ```kotlin
  fun ProductStockRepository.getByProductId(productId: Long): ProductStock {
      return findByProductId(productId)
          ?: throw EntityNotFoundException("ProductStock not found")
  }
  ```

## 핵심 구현 세부사항

### Proto 파일 관리 방식
`product-api` 모듈에서 Proto 파일을 중앙 관리:
```
product-api/
├── build.gradle              # protobuf 플러그인으로 gRPC 코드 생성
└── src/main/proto/
    └── product_service.proto # 원본 Proto 파일
```

**의존성 구조**:
- `product` → `implementation project(':product-api')` (서버 구현)
- `order` → `implementation project(':product-api')` (클라이언트 구현)

**Proto 파일 변경 시**:
1. `product-api/src/main/proto/product_service.proto` 수정
2. `./gradlew :product-api:generateProto` 실행
3. product와 order 모듈 재빌드 (의존성으로 자동 반영)

**실무 MSA에서의 배포** (Nexus/Artifactory 사용 시):
- `product-api`를 별도 저장소로 분리
- jar로 배포: `implementation 'com.company:product-api:1.0.0'`

### REST API 구현
Product Service는 gRPC와 동일한 기능을 REST API로도 제공:
- **엔드포인트**:
  - `POST /api/products/stock/check` - 재고 확인
  - `GET /api/products/{productId}` - 상품 정보 조회
  - `POST /api/products/{productId}/stock/decrease` - 재고 차감
- **비즈니스 로직 재사용**: `ProductStockService`를 그대로 사용하여 분산락과 Redis Pub/Sub 자동 적용
- **에러 매핑**: gRPC Status → HTTP Status (RESOURCE_EXHAUSTED→409, NOT_FOUND→404, INTERNAL→500)

### gRPC 에러 처리
Product Service는 gRPC Status 코드 사용:
- `Status.RESOURCE_EXHAUSTED`: 재고 부족
- `Status.NOT_FOUND`: 상품 없음
- `Status.INTERNAL`: 기타 오류

### 성능 비교 테스트
`GrpcVsRestPerformanceTest`로 gRPC vs REST 성능 비교:
- **Latency 측정**: 평균/최소/최대/P95/P99 응답 시간
- **페이로드 크기**: Protobuf vs JSON 직렬화 비용
- **동시성 부하**: 100/500/1000 스레드 동시 요청 시 TPS 측정
- **결과**: 콘솔에 표 형식으로 비교 결과 출력

### 주문 생성 플로우
1. Order Service → gRPC `checkStock()` → Product Service (재고 확인)
2. Order Service → gRPC `getProduct()` → Product Service (가격 조회)
3. Order Service → Order 엔티티 생성 (PENDING 상태)
4. Order Service → gRPC `decreaseStock()` → Product Service (분산락 적용)
5. Order Service → Order 상태를 CONFIRMED로 변경
6. **Order Service → Kafka 이벤트 발행** (`order-created` 토픽)
7. Product Service → Redis Pub/Sub 이벤트 발행 (재고 차감, 재고 부족 경고, 재고 소진)
8. **Product Service → Kafka 이벤트 소비** (비즈니스 로직 처리, 재시도 로직 적용)

### 기술 스택
- **언어**: Kotlin 2.2.21
- **프레임워크**: Spring Boot 4.0.1, Spring 7.0.2
- **JVM**: Java 21 (Virtual Threads 활성화)
- **데이터베이스**: MySQL 8 + Flyway 마이그레이션
- **캐시/락**: Redis + Redisson
- **gRPC**: io.grpc + Protocol Buffers (proto3)
- **메시징**: Kafka (Spring Kafka with auto-configuration)
- **빌드**: Gradle (Kotlin DSL)

## 자주 발생하는 문제

### gRPC 서버가 시작되지 않음
Product Service는 실행되지만 gRPC 포트 8091이 리스닝하지 않는 경우:
- `GrpcServerConfig.kt`가 존재하고 제대로 로드되는지 확인
- 로그 확인: `✅ [gRPC Server] Started on port: 8091`
- Spring gRPC auto-configuration이 실패할 수 있음 (수동 서버 시작 구현됨)

### gRPC 호출 시 Connection Refused
- Product Service가 실행 중인지 확인: `lsof -i :8091`
- Order Service는 시작 시 `✅ [gRPC Client] Product Service 연결됨` 로그를 출력하지만, 실제 연결은 lazy (첫 RPC 호출 시 연결)

### 데이터베이스 접속 정보
`application.yml` 파일에서 MySQL 접속 정보 수정:
- Product: `jdbc:mysql://localhost:3306/product`
- Order: `jdbc:mysql://localhost:3306/orders`

### Kafka 연결 실패
Order/Product Service가 시작 시 Kafka에 연결하지 못하는 경우:
- Kafka가 실행 중인지 확인: `docker ps | grep kafka`
- Kafka 로그 확인: `docker logs kafka`
- application.yml의 `spring.kafka.bootstrap-servers` 확인 (기본값: localhost:9092)

### Kafka 재시도 로직 테스트
- 테스트 가이드: `product/KAFKA_RETRY_TEST_GUIDE.md` 참조
- Kafka Console Producer로 수동 메시지 발행 가능
- DLT 메시지는 `order-created.DLT` 토픽에서 확인

## 프로젝트 구조
```
toy-server/
├── product-api/                # gRPC API 공유 모듈 (Proto 파일 및 생성 코드)
│   ├── build.gradle           # protobuf 플러그인 설정
│   └── src/main/proto/
│       └── product_service.proto
│
├── product/                    # 상품 및 재고 서비스
│   ├── build.gradle           # implementation project(':product-api')
│   ├── KAFKA_RETRY_TEST_GUIDE.md  # Kafka 재시도 로직 테스트 가이드
│   ├── src/main/kotlin/
│   │   ├── config/            # GrpcServerConfig, RedisPubSubConfig, KafkaConsumerConfig
│   │   ├── controller/        # ProductRestController (REST API)
│   │   ├── dto/               # ProductRestDto (REST 요청/응답)
│   │   ├── exception/         # RestExceptionHandler
│   │   ├── grpc/              # ProductGrpcService (서버 구현)
│   │   ├── service/           # ProductStockService (@DistributedLock 적용)
│   │   ├── event/             # StockEventPublisher/Subscriber, OrderEventConsumer (Kafka)
│   │   └── persistence/       # JPA 엔티티, Repository
│   ├── src/main/resources/
│   │   ├── application.yml    # kafka.consumer 설정 포함
│   │   └── db/migration/      # Flyway SQL 스크립트
│   └── src/test/kotlin/
│       └── performance/       # GrpcVsRestPerformanceTest, 성능 측정 인프라
│
└── order/                      # 주문 서비스
    ├── build.gradle           # implementation project(':product-api')
    ├── src/main/kotlin/
    │   ├── client/            # ProductRestClient (REST 클라이언트)
    │   ├── config/            # RestClientConfig
    │   ├── dto/               # ProductRestDto (REST 요청/응답)
    │   ├── event/             # OrderEventPublisher (Kafka), OrderCreatedEvent
    │   ├── grpc/              # ProductGrpcClient (클라이언트 구현)
    │   ├── service/           # OrderService (gRPC/REST + Kafka 이벤트 발행)
    │   ├── controller/        # REST API
    │   └── persistence/       # Order, OrderItem 엔티티
    └── src/main/resources/
        ├── application.yml    # kafka.producer 설정 포함
        └── db/migration/      # Flyway SQL 스크립트
```

## REST API 수동 테스트

Product Service REST API 테스트:
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
