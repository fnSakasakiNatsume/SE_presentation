# Section 6 — Application Skeleton

## 6.1 Layered Architecture

The backend follows a classical four-layer Spring Boot structure. Each layer has
one — and only one — responsibility, and depends only on the layer immediately
below it through Spring-managed interfaces.

```
┌─────────────────────────────────────────────────────────────┐
│  Web / Edge layer    Nginx + Interceptors (auth, rate-limit) │
├─────────────────────────────────────────────────────────────┤
│  Controller layer    @RestController  ─ HTTP boundary        │
├─────────────────────────────────────────────────────────────┤
│  Service layer       @Service         ─ business logic       │
├─────────────────────────────────────────────────────────────┤
│  DAO / Mapper layer  MyBatis-Plus     ─ persistence          │
└─────────────────────────────────────────────────────────────┘
                           ▲
                           │
              MySQL  +  Redis  +  Kafka  +  LLM
```

### 6.1.1 Controller layer (`com.hmdp.controller`)

* Sole concern: translate the HTTP world into the Java world.
* Annotated with `@RestController` + `@RequestMapping`; no business logic.
* Validates incoming DTOs (`LoginFormDTO`, `Blog`, `Voucher`, …) and returns the
  unified envelope `Result { success, errorMsg, data, total }`.
* Reads the authenticated user from `UserHolder.getUser()` (populated by
  `RefreshTokenInterceptor` for the current request thread).
* Example responsibilities by controller:

  | Controller                    | Endpoints (selected)                                | Notes                                |
  | ----------------------------- | --------------------------------------------------- | ------------------------------------ |
  | `UserController`              | `POST /user/code`, `POST /user/login`, `POST /sign` | SMS + Token + Sign-in                |
  | `ShopController`              | `GET /shop/{id}`, `GET /shop/of/type`               | Cached + GEO                         |
  | `BlogController`              | `POST /blog`, `PUT /blog/like/{id}`, `/of/follow`   | Notes + Feed                         |
  | `VoucherController`           | `POST /voucher/seckill`                             | Activity admin                       |
  | `VoucherOrderController`      | `POST /voucher-order/seckill/{id}`                  | **Hot seckill entry point**          |
  | `FollowController`            | `PUT /follow/{id}/{isFollow}`                       | Follow / common-follow               |

### 6.1.2 Service layer (`com.hmdp.service` + `service.impl`)

* Each service is split into an **interface** (`IXxxService`) and an
  **implementation** (`XxxServiceImpl`). Controllers depend only on the
  interface, which makes the layer trivially mockable in tests.
* Implementations extend MyBatis-Plus `ServiceImpl<XxxMapper, XxxEntity>`,
  inheriting CRUD helpers (`save`, `getById`, `query()`, `update()`).
* This is where domain logic, caching strategy, distributed locks, Lua scripts,
  Kafka producer calls and transaction boundaries live.
* Cross-cutting helpers used by services:
  * `CacheClient` — generic logical-expire / pass-through cache wrapper.
  * `RedisIdWorker` — snowflake-like 64-bit globally unique IDs.
  * `SimpleRedisLock` / `Redisson` — distributed locks.
  * `UserHolder` — `ThreadLocal<UserDTO>` for the current request.

### 6.1.3 DAO / Mapper layer (`com.hmdp.mapper`)

* Pure interfaces extending `BaseMapper<Entity>` from MyBatis-Plus. No SQL is
  hand-written for the standard CRUD; complex SQL (batch upsert, ranking) is
  expressed in `@Select` / `@Update` annotations or in `mapper/*.xml`.
* The mapper layer never references Redis, Kafka or HTTP types — it is a thin,
  testable JDBC wrapper.

### 6.1.4 Cross-cutting infrastructure

| Concern             | Component                                            |
| ------------------- | ---------------------------------------------------- |
| Authentication      | `RefreshTokenInterceptor`, `LoginInterceptor`        |
| Async messaging     | `QueueConfig`, `SeckillVoucherListener` (→ Kafka)    |
| Caching             | `CacheClient`, `RedisData`                           |
| Distributed locking | `RedissonConfig`, `SimpleRedisLock`                  |
| Global error        | `WebExceptionAdvice` (`@RestControllerAdvice`)       |
| MVC wiring          | `MvcConfig` registers interceptors and order chain   |

### 6.1.5 Package map

```
com.hmdp
├── HmDianPingApplication.java
├── config/        # Spring configuration (Mvc, Redisson, Queue, Mybatis)
├── controller/    # HTTP layer (@RestController)
├── dto/           # Request / response DTOs (Result, UserDTO, ScrollResult)
├── entity/        # MyBatis-Plus entities annotated with @TableName
├── interceptor/   # Token refresh + login gate
├── listener/      # @KafkaListener (target) / @RabbitListener (current)
├── mapper/        # MyBatis-Plus BaseMapper<T>
├── service/       # IService interfaces
│   └── impl/      # ServiceImpl<XxxMapper, XxxEntity>
└── utils/         # CacheClient, RedisIdWorker, UserHolder, lock, regex
```

## 6.2 Development Stack & Versions

The version pins below are taken from the project's `pom.xml`. Items marked
**(target)** are added as part of the new architecture (Section 5).

| Layer              | Library / Tool                                  | Version             | Source                  |
| ------------------ | ----------------------------------------------- | ------------------- | ----------------------- |
| Runtime            | OpenJDK                                         | **1.8**             | `<java.version>1.8`     |
| Application        | `spring-boot-starter-parent`                    | **2.3.12.RELEASE**  | `pom.xml`               |
| Web                | `spring-boot-starter-web` (Spring MVC + Tomcat) | inherited 2.3.12    | `pom.xml`               |
| Persistence — ORM  | `mybatis-plus-boot-starter`                     | **3.4.3**           | `pom.xml`               |
| Persistence — JDBC | `mysql-connector-java`                          | **8.0.28**          | `pom.xml`               |
| Cache              | `spring-boot-starter-data-redis` (Lettuce)      | inherited 2.3.12    | `pom.xml`               |
|                    | `spring-data-redis`                             | **2.6.2**           | `pom.xml`               |
|                    | `lettuce-core`                                  | **6.1.6.RELEASE**   | `pom.xml`               |
| Distributed lock   | `redisson`                                      | **3.13.6**          | `pom.xml`               |
| Async (current)    | `spring-boot-starter-amqp` (RabbitMQ)           | inherited 2.3.12    | `pom.xml`               |
| Async **(target)** | `spring-kafka`                                  | **2.6.7**           | matches Spring Boot 2.3 |
|                    | Apache Kafka broker                             | **3.6.x**           | infra deployment        |
| AI **(target)**    | `langchain4j`                                   | **0.34.0**          | latest stable for JDK 8 |
|                    | `langchain4j-open-ai`                           | **0.34.0**          | LLM adapter             |
| Boilerplate        | `lombok`                                        | inherited           | `pom.xml`               |
| Utilities          | `hutool-all`                                    | **5.7.17**          | `pom.xml`               |
| AOP                | `aspectjweaver`                                 | inherited           | `pom.xml`               |
| Test runtime       | `spring-boot-starter-test` (JUnit 5 + Mockito)  | inherited 2.3.12    | `pom.xml`               |
| Stress test        | Apache JMeter                                   | **5.6.3**           | external tool           |
| Container infra    | Docker / docker-compose                         | 24.x / v2           | local dev               |
| Edge               | Nginx                                           | **1.18.0+**         | campus VM               |

### 6.3 Choice Rationale

| Choice                        | Why for an on-campus seckill                                                             |
| ----------------------------- | ---------------------------------------------------------------------------------------- |
| **Spring Boot 2.3 / JDK 8**   | Long-term toolchain available on the campus build server; matches operations skill set.  |
| **MyBatis-Plus 3.4**          | Lambda query, built-in CRUD, optimistic-lock plugin — drastically less boilerplate.      |
| **Redis (Lettuce)**           | Atomic counters + Lua scripts give the anti-oversell guarantee; sub-ms latency.          |
| **Redisson**                  | Reentrant distributed lock with watch-dog; safer than hand-rolled `SETNX`.               |
| **Kafka 3.x + spring-kafka**  | Partitioned, durable buffer for write peak shaving; replayable for post-mortems.         |
| **MySQL 8**                   | Strong unique-index guarantees for the final idempotency check; campus DBA standard.     |
| **Nginx**                     | Cheap TLS termination, static asset CDN, per-IP rate limit before any JVM is hit.        |
| **LangChain4j 0.34**          | Java-native LLM integration so we don't have to introduce a Python service to the stack. |
| **JMeter 5.6**                | Established, scriptable load generator with cluster mode for ≥ 1k concurrent threads.    |

### 6.4 Build & runtime layout (single environment, multiple replicas)

```
nginx (1 instance, port 443) ─┬─► spring-boot pod #1 (port 8081)
                              ├─► spring-boot pod #2 (port 8082)
                              └─► spring-boot pod #3 (port 8083)

redis           : single-node + sentinel (3 nodes) on the campus VM
kafka           : 3-broker cluster, 1× ZooKeeper or KRaft mode
mysql           : 1 primary + 1 read replica, async replication
langchain4j svc : co-deployed with the application JVMs
```
