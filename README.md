# Toy Server - MSA 이커머스 학습 프로젝트

Spring Boot 기반 마이크로서비스 아키텍처(MSA) 학습용 프로젝트입니다.

## 아키텍처 구성

```
Client
  │
  ▼
API Gateway (8080)  ─── JWT 인증, Rate Limiting, 라우팅
  │
  ├──▶ Product Service (8081 / gRPC 8091)
  │       MySQL, Redis 분산락, Kafka Consumer, Redis Pub/Sub
  │
  └──▶ Order Service (8082)
          PostgreSQL, gRPC Client, Kafka Producer
```

---

## 사전 준비

- **Java 21**
- **Docker**
- **Gradle** (wrapper 포함)

---

## 1단계: 인프라 실행 (Docker)

### MySQL (Product DB + Auth DB)

```bash
docker run -d --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=test \
  mysql:8
```

### PostgreSQL (Order DB)

```bash
docker run -d --name alloydb \
  -p 5432:5432 \
  -e POSTGRES_PASSWORD=test \
  -e POSTGRES_USER=test \
  postgres:latest
```

### Redis

```bash
docker run -d --name redis -p 6379:6379 redis
```

### Kafka

```bash
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

---

## 2단계: 데이터베이스 생성

### MySQL - product, auth DB 생성

```bash
docker exec -it mysql mysql -u root -ptest
```

```sql
CREATE
DATABASE IF NOT EXISTS product;
CREATE
DATABASE IF NOT EXISTS auth;
CREATE
USER IF NOT EXISTS 'test'@'%' IDENTIFIED BY 'test';
GRANT ALL PRIVILEGES ON product.* TO
'test'@'%';
GRANT ALL PRIVILEGES ON auth.* TO
'test'@'%';
FLUSH
PRIVILEGES;
EXIT;
```

### PostgreSQL - orders DB 생성

```bash
docker exec -it alloydb psql -U test
```

```sql
CREATE
DATABASE orders;
\q
```

---

## 3단계: 모니터링 스택 실행

```bash
cd monitoring
docker compose up -d
cd ..
```

| 서비스        | URL                                 |
|------------|-------------------------------------|
| Grafana    | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:19090              |
| Loki       | http://localhost:3100               |

---

## 4단계: 빌드

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 전체 빌드 (테스트 제외)
./gradlew build -x test
```

---

## 5단계: 서비스 실행 (순서 중요)

각 서비스는 **별도 터미널**에서 실행합니다.

### 터미널 1 - Product Service

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :product:bootRun
# HTTP: http://localhost:8081
# gRPC: localhost:8091
```

### 터미널 2 - Order Service (Product 기동 후 실행)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :order:bootRun
# HTTP: http://localhost:8082
```

### 터미널 3 - API Gateway

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :gateway:bootRun
# HTTP: http://localhost:8080
```

---

## 6단계: API 테스트

### 회원가입

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass123"}'
```

### 로그인 (JWT 토큰 발급)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo $TOKEN
```

### 상품 조회 (인증 불필요)

```bash
curl http://localhost:8080/api/products/1
```

### 주문 생성 (JWT 필수)

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":1,"quantity":2}'
```

### 주문 조회

```bash
curl http://localhost:8080/api/orders/{orderId}
```

---

## 헬스 체크

```bash
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Product
curl http://localhost:8082/actuator/health   # Order
```

---

## 포트 정리

| 서비스          | 포트    |
|--------------|-------|
| API Gateway  | 8080  |
| Product HTTP | 8081  |
| Product gRPC | 8091  |
| Order        | 8082  |
| MySQL        | 3306  |
| PostgreSQL   | 5432  |
| Redis        | 6379  |
| Kafka        | 9092  |
| Grafana      | 3000  |
| Prometheus   | 19090 |
| Loki         | 3100  |
