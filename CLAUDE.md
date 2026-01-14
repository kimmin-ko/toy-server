# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

Toy-server는 **마이크로서비스 아키텍처(MSA)** 학습용 프로젝트로, 이커머스 시스템을 구현합니다:
- **Product Service**: 상품 및 재고 관리 (분산락 적용)
- **Order Service**: 주문 관리 (gRPC로 Product 서비스 호출)
- **서비스 간 통신**: gRPC (동기 호출)
- **이벤트 기반 아키텍처**: Redis Pub/Sub (재고 변경 알림)

## 빌드 및 실행 명령어

### 사전 준비
```bash
# Java 21 설정
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 필수 서비스 실행 (Docker)
docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8
docker run -d -p 6379:6379 redis
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

# 포트 확인
lsof -i :8081  # Product HTTP
lsof -i :8091  # Product gRPC
lsof -i :8082  # Order HTTP
```

## 아키텍처

### 서비스 간 통신 구조
```
Order Service (Client)  ──gRPC──>  Product Service (Server)
     │                                  │
     │                                  ├─ REST API (8081)
     ├─ REST API (8082)                 ├─ gRPC Server (8091)
     ├─ gRPC Client                     ├─ MySQL (product DB)
     └─ MySQL (orders DB)               ├─ Redis (분산락)
                                        └─ Redis Pub/Sub (이벤트)
```

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

### gRPC 에러 처리
Product Service는 gRPC Status 코드 사용:
- `Status.RESOURCE_EXHAUSTED`: 재고 부족
- `Status.NOT_FOUND`: 상품 없음
- `Status.INTERNAL`: 기타 오류

### 주문 생성 플로우
1. Order Service → gRPC `checkStock()` → Product Service (재고 확인)
2. Order Service → gRPC `getProduct()` → Product Service (가격 조회)
3. Order Service → Order 엔티티 생성 (PENDING 상태)
4. Order Service → gRPC `decreaseStock()` → Product Service (분산락 적용)
5. Order Service → Order 상태를 CONFIRMED로 변경
6. Product Service → Redis Pub/Sub 이벤트 발행 (재고 차감, 재고 부족 경고, 재고 소진)

### 기술 스택
- **언어**: Kotlin 2.2.21
- **프레임워크**: Spring Boot 4.0.1, Spring 7.0.2
- **JVM**: Java 21 (Virtual Threads 활성화)
- **데이터베이스**: MySQL 8 + Flyway 마이그레이션
- **캐시/락**: Redis + Redisson
- **gRPC**: io.grpc + Protocol Buffers (proto3)
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
│   ├── src/main/kotlin/
│   │   ├── config/            # GrpcServerConfig, RedisPubSubConfig, JpaConfig
│   │   ├── grpc/              # ProductGrpcService (서버 구현)
│   │   ├── service/           # ProductStockService (@DistributedLock 적용)
│   │   ├── event/             # StockEventPublisher/Subscriber
│   │   └── persistence/       # JPA 엔티티, Repository
│   └── src/main/resources/
│       └── db/migration/      # Flyway SQL 스크립트
│
└── order/                      # 주문 서비스
    ├── build.gradle           # implementation project(':product-api')
    ├── src/main/kotlin/
    │   ├── grpc/              # ProductGrpcClient (클라이언트 구현)
    │   ├── service/           # OrderService (gRPC로 Product 호출)
    │   ├── controller/        # REST API
    │   └── persistence/       # Order, OrderItem 엔티티
    └── src/main/resources/
        └── db/migration/      # Flyway SQL 스크립트
```
