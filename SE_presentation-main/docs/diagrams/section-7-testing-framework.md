# Section 7 — Testing Framework

A high-concurrency seckill system fails in two qualitatively different ways:
**logical bugs** (overselling, double-booking, wrong totals) and **capacity
bugs** (timeouts, queue overflows, OOM). The test plan below covers both.

```
                ┌──────────────────────────┐
   correctness │ Unit + Integration tests │  JUnit 5 / Mockito / Testcontainers
                └──────────────────────────┘
                ┌──────────────────────────┐
   capacity     │ Stress / Load tests      │  JMeter 5.6 (200–300 QPS peak)
                └──────────────────────────┘
                ┌──────────────────────────┐
   resilience   │ Chaos / failure tests    │  Manual: kill Redis / Kafka / MySQL
                └──────────────────────────┘
```

## 7.1 Unit Tests — JUnit 5 + Mockito

**Goal**: prove that **each Java class** behaves correctly in isolation,
including all branches of the seckill admission logic.

### 7.1.1 Tooling

| Concern                   | Library / Tool                                    |
| ------------------------- | ------------------------------------------------- |
| Test runner               | JUnit Jupiter 5.x (bundled in `spring-boot-test`) |
| Mocking                   | Mockito 3.x + `mockito-inline` for static mocks   |
| Assertion DSL             | AssertJ                                           |
| Spring slice              | `@SpringBootTest`, `@WebMvcTest`, `@DataRedisTest` |
| Coverage                  | JaCoCo (Maven plugin, fail-build below 70%)       |

### 7.1.2 Coverage targets

| Layer            | What is tested                                                                       |
| ---------------- | ------------------------------------------------------------------------------------ |
| Controller       | HTTP status / body shape via `MockMvc`; argument validation                          |
| Service          | Domain logic with all collaborators mocked (`@Mock` + `@InjectMocks`)                |
| Mapper           | Real SQL against an embedded DB (H2 with MySQL mode) or a Testcontainer MySQL        |
| Cache utilities  | `CacheClient` paths: cache-hit, miss, null-stub, logical-expire, mutex re-build      |
| Lua admission    | Run `seckill.lua` against `embedded-redis` to assert all three return codes (0/1/2)  |

### 7.1.3 Representative test cases (anti-oversell focus)

| # | Class under test                  | Scenario                                                      | Expected                                                                |
| - | --------------------------------- | ------------------------------------------------------------- | ----------------------------------------------------------------------- |
| 1 | `VoucherOrderServiceImpl`         | Stock = 0 in Redis when `seckillVoucher` is called            | `Result.fail("库存不足")` and **no** Kafka message produced              |
| 2 | `VoucherOrderServiceImpl`         | Same user calls twice within the open window                  | First → `ok(orderId)`, second → `Result.fail("不能重复下单")`            |
| 3 | `VoucherOrderServiceImpl`         | `KafkaTemplate.send` throws `TimeoutException`                | Method throws / logs; stock in Redis is **rolled back** (or compensated)|
| 4 | `SeckillVoucherListener`          | Same `OrderEvent` consumed twice                              | Only one row persisted thanks to `(user_id, voucher_id)` UNIQUE index   |
| 5 | `CacheClient.queryWithLogicalExpire` | Cached value with `expireTime` in the past                 | Returns stale value AND triggers async rebuild on the executor          |
| 6 | `RedisIdWorker.nextId`            | Called 100k times concurrently                                | Zero collisions; monotonic per millisecond                              |
| 7 | `RefreshTokenInterceptor`         | Missing / unknown token                                       | Returns `true` (passes through) but does **not** populate `UserHolder`  |
| 8 | `LoginInterceptor`                | `UserHolder` empty                                            | Sets HTTP 401 and returns `false`                                       |

Example skeleton (illustrative):

```java
@ExtendWith(MockitoExtension.class)
class VoucherOrderServiceImplTest {

    @Mock StringRedisTemplate redis;
    @Mock RedisIdWorker redisIdWorker;
    @Mock KafkaTemplate<String, String> kafkaTemplate;
    @InjectMocks VoucherOrderServiceImpl service;

    @Test
    void should_reject_when_stock_zero() {
        when(redis.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);
        try (MockedStatic<UserHolder> uh = mockStatic(UserHolder.class)) {
            uh.when(UserHolder::getUser).thenReturn(new UserDTO(1L, "stu", ""));
            Result r = service.seckillVoucher(99L);
            assertThat(r.getSuccess()).isFalse();
            verifyNoInteractions(kafkaTemplate);
        }
    }
}
```

### 7.1.4 Integration tests — Testcontainers

Spin up Redis 7, Kafka 3.6 and MySQL 8 in Docker for a CI run that exercises
the **real** wire protocols. Each test class uses a fresh container set so
state never leaks across cases.

```java
@SpringBootTest
@Testcontainers
class SeckillEndToEndIT {
    @Container static GenericContainer<?> redis  = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container static KafkaContainer kafka       = new KafkaContainer(parse("confluentinc/cp-kafka:7.6.0"));
    @Container static MySQLContainer<?> mysql    = new MySQLContainer<>("mysql:8.0");
    // ... wire spring properties via @DynamicPropertySource
}
```

## 7.2 Stress / Load Tests — Apache JMeter 5.6

**Goal**: prove the system sustains the **target peak of 200–300 QPS** on the
seckill endpoint without overselling and within the latency SLA.

### 7.2.1 Test environment

| Item              | Value                                                                          |
| ----------------- | ------------------------------------------------------------------------------ |
| Application tier  | 3 × Spring Boot pods, JDK 8, `-Xmx2g`, behind Nginx                            |
| Redis             | 1 × Redis 7 (campus VM, 4 vCPU / 8 GiB)                                        |
| Kafka             | 3-broker cluster, topic `seckill.orders` partitions = 6, replication-factor = 3|
| MySQL             | 1 primary, InnoDB, `innodb_buffer_pool_size = 4G`                              |
| Load generator    | JMeter 5.6 — controller + 2 worker nodes; total 1,000 virtual users headroom   |

### 7.2.2 Test plan structure (`.jmx`)

```
Test Plan : campus-seckill
├── User Defined Variables  ─ baseUrl, vouchersIds, rampUp, peakQPS
├── HTTP Cookie Manager
├── HTTP Header Manager     ─ Content-Type: application/json
├── Setup Thread Group  «Token Pre-warm»
│   ├── CSV Data Set       students.csv  (phone)
│   ├── HTTP Request       POST /user/code  (200 students)
│   └── HTTP Request       POST /user/login → extract `data` as ${TOKEN}
│       └── JSR223 PostProcessor → write tokens.csv
│
├── Thread Group  «1. Catalog browse (read load)»
│   • Threads: 100   Ramp-up: 5 s   Loop: 30
│   • HTTP Request GET /shop/{id}        (id from CSV)
│   • Constant Throughput Timer: 60 req/s   ─ steady background read load
│
├── Thread Group  «2. Seckill burst (write load)»
│   • Threads: 300   Ramp-up: 5 s   Loop: 1
│   • CSV Data Set tokens.csv  → Header authorization=${TOKEN}
│   • HTTP Request POST /voucher-order/seckill/${voucherId}
│   • Response Assertion : `"success":true` OR errorMsg ∈ {库存不足, 不能重复下单}
│   • JSON Extractor    : data → orderId
│   • View Results in Table  /  Aggregate Report  /  Summary Report
│
├── Thread Group  «3. My-orders polling»
│   • Threads: 100   Ramp-up: 10 s   Loop: 5
│   • HTTP Request GET /voucher-order/my
│
└── Backend Listener        ─ InfluxDB → Grafana dashboard
```

### 7.2.3 Three load scenarios

| Scenario      | Threads | Ramp-up | Loop | Inventory | Target QPS  | Purpose                                                |
| ------------- | ------- | ------- | ---- | --------- | ----------- | ------------------------------------------------------ |
| Smoke         | 20      | 2 s     | 5    | 100       | ~10         | Sanity in CI                                           |
| Baseline      | 100     | 5 s     | 5    | 200       | ~80         | Steady-state throughput / latency baseline             |
| **Peak**      | **300** | **5 s** | **1**| **100**   | **200–300** | Reproduces the first-ten-seconds rush of a real event  |
| Soak          | 100     | 10 s    | 60   | 5,000     | ~80 for 30 m| Memory leak / GC pressure / connection pool exhaustion |

### 7.2.4 KPIs and pass criteria

| KPI                          | Target                                                                |
| ---------------------------- | --------------------------------------------------------------------- |
| Throughput at peak           | ≥ 200 successful requests per second on the seckill endpoint          |
| p95 latency (peak scenario)  | ≤ 200 ms                                                              |
| p99 latency (peak scenario)  | ≤ 300 ms                                                              |
| HTTP error rate (5xx)        | 0 %                                                                   |
| Application error rate       | ≤ business expectation (excess requests ⇒ "库存不足" is **expected**) |
| Final stock in MySQL         | **Exactly 0 — never negative** (anti-oversell invariant)              |
| Distinct orders in MySQL     | **= initial stock** (each accepted request becomes exactly one order) |
| Distinct users in orders     | **= initial stock** (one-student-one-order)                           |

### 7.2.5 Anti-oversell verification queries

After each peak run, the following queries are executed and must hold:

```sql
-- 1) Stock must be exactly zero, never negative.
SELECT stock FROM tb_seckill_voucher WHERE voucher_id = ?;
-- expected: 0

-- 2) Number of orders equals the initial stock.
SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id = ?;
-- expected: 100  (matches Inventory in scenario "Peak")

-- 3) No user got more than one order.
SELECT user_id, COUNT(*) c
FROM tb_voucher_order
WHERE voucher_id = ?
GROUP BY user_id
HAVING c > 1;
-- expected: 0 rows
```

If any of the three checks fails, the build is marked **red** and the run is
attached to the bug report.

### 7.2.6 Observability during the run

* **Grafana** dashboards driven by JMeter's InfluxDB Backend Listener show
  per-second throughput, p95 / p99 and error counts in real time.
* **Spring Boot Actuator + Micrometer** exports JVM, Hikari pool and Kafka
  producer metrics to Prometheus.
* **Redis `MONITOR`** is sampled to confirm Lua scripts dominate the call mix
  during the burst.
* **Kafka consumer lag** is plotted against ingest rate to confirm the buffer
  drains within the SLA after the burst ends.

## 7.3 Resilience / Chaos checks (manual)

| Fault injected                    | Expected behaviour                                                                       |
| --------------------------------- | ---------------------------------------------------------------------------------------- |
| Kill 1 Spring Boot pod mid-burst  | Nginx removes it from upstream; remaining pods absorb the traffic; no oversell           |
| `redis-cli DEBUG SLEEP 5`          | Front gate returns 503 / fail-fast; **no** order is mistakenly persisted                 |
| Stop 1 Kafka broker (RF=3)         | Producer continues against the surviving brokers; consumers re-balance partitions        |
| Stop MySQL primary                 | Consumer pauses, lag grows; on recovery it drains the backlog without duplicating orders |

## 7.4 Continuous integration

GitHub Actions / Jenkins pipeline:

```
mvn -B clean verify         # JUnit + JaCoCo
docker compose up -d        # Redis + Kafka + MySQL via Testcontainers
mvn failsafe:integration-test
jmeter -n -t plans/peak.jmx -l results.jtl
python verify_no_oversell.py    # runs the SQL above; exits non-zero on failure
```

A pull request cannot merge to `main` unless **all four** stages are green and
JaCoCo line-coverage is ≥ 70 %.
