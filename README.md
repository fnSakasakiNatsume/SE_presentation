# NTU Smarthub

> A campus-marketplace + high-concurrency ticket-rush platform for **Nanyang Technological University**.

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-2.3.12-6DB33F)]()
[![Vue 3](https://img.shields.io/badge/Vue-3.3-4FC08D)]()
[![Kafka](https://img.shields.io/badge/Kafka-3.9_KRaft-231F20)]()
[![Redis](https://img.shields.io/badge/Redis-5.0-DC382D)]()
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)]()

---

## What is NTU Smarthub?

NTU Smarthub is a unified online hub that connects three kinds of campus players:

1. **On-campus merchants** — canteens, Prime, KFC, Domino's, Octopus Sushi, etc. Students browse, view promotions, and grab limited-stock seckill vouchers.
2. **Off-campus partners** — businesses like *Coconut* that schedule weekly campus drop-offs based on bundled student orders.
3. **Event organisers** — student clubs and external companies running **ticket rushes** (e.g. concerts, hall events, society galas) where thousands of students compete for limited tickets in a single instant.

On top of that, students can publish **discovery posts** to promote a shop or event, and the platform ranks them in real time by likes.

---

## Why does it need engineering?

Every feature above looks innocent on the surface, but the campus context produces traffic patterns that crush a naive implementation:

| Scenario | Stress |
|---|---|
| Hall ball tickets drop at 12:00 sharp | 5 000+ requests in the first second |
| KFC discount voucher posted on the IG story | Cache stampede on a single shop page |
| Society election results page | Hot read, must never go stale |
| One student tries to grab two of the same ticket | Race condition across multiple app instances |

NTU Smarthub solves these with **Redis caching, Lua-atomic stock control, Redisson distributed locks, and Kafka-decoupled order persistence**.

---

## Architecture

```
┌────────────┐    HTTPS     ┌──────────────────────────┐
│  Vue 3 SPA │ ───────────▶ │  Spring Boot (port 8081) │
│ (Element+) │              │  - Token interceptor     │
└────────────┘              │  - Login / Shop / Vouch. │
                            │  - Blog & Like           │
                            └─────────┬──────┬─────────┘
                                      │      │
                            ┌─────────▼─┐  ┌─▼──────────────┐
                            │  Redis    │  │ Kafka (KRaft)  │
                            │ (cache,   │  │ topic:         │
                            │  ZSet,    │  │ seckill.orders │
                            │  HashTok) │  │ 3 partitions   │
                            └─────────┬─┘  └─┬──────────────┘
                                      │      │ async consume
                            ┌─────────▼──────▼─────┐
                            │       MySQL 8        │
                            │  shop / voucher /    │
                            │  blog / user / order │
                            └──────────────────────┘
```

---

## Tech Highlights

### 1. Token-based session via Redis Hash
- No HTTP session at all — a UUID token returned at login keys a Redis Hash that holds the user DTO.
- Two-layer interceptor: outer one refreshes TTL on every request; inner one blocks unauthenticated paths.
- Survives load balancing and process restarts.

### 2. Three-cache-problem playbook
| Problem | Strategy used |
|---|---|
| Cache penetration | Cache empty value with short TTL |
| Cache avalanche | Random TTL offset |
| Cache breakdown | **Logical expiration** + async rebuild on a worker thread |

### 3. Lua-atomic ticket rush
A single Lua script in Redis performs:
```
stock check → user-already-bought check → decrement stock → SADD user → return code
```
Single round-trip, atomic across instances. The HTTP thread never touches MySQL.

### 4. Redisson distributed lock
For the per-user-one-ticket invariant across multiple app servers. Watchdog auto-renew, reentrant, retry on contention.

### 5. Kafka-async order persistence
- Producer: send to `seckill.orders` topic (3 partitions, keyed by `voucherId`).
- Consumer: a single consumer group `hmdp-seckill-group` persists to MySQL and decrements DB stock.
- Same partition for one voucher → ordered consumption per voucher when scaling up.
- HTTP response returns the order ID before any DB write.

### 6. Like leaderboard via Redis ZSet
- `like` uses ZSet with timestamp score → toggle semantics (`ZSCORE` check, `ZADD`/`ZREM`).
- `ZRANGE key 0 4` gives the first 5 likers (used in blog detail page).
- `ORDER BY liked DESC` query gives the global leaderboard (used in `/blog/hot`).

---

## Module Map

```
src/main/java/com/hmdp
├── config/                      # CORS, Mybatis, Redisson, Kafka topic
├── controller/                  # REST endpoints
│   ├── UserController          (SMS code, login, /me)
│   ├── ShopController          (shop types, paged listing, detail)
│   ├── VoucherController       (list vouchers per shop, create seckill)
│   ├── VoucherOrderController  (seckill entry point)
│   └── BlogController          (hot, detail, like toggle, top likers)
├── service/impl/                # Business logic
├── interceptor/                 # RefreshToken + Login
├── listener/SeckillVoucherListener.java   # Kafka consumer
└── utils/                       # CacheClient, RedisIdWorker, SimpleRedisLock, ...

src/main/resources
├── application.yaml             # MySQL + Redis + Kafka config
├── seckill.lua                  # Atomic seckill predicate
├── unLock.lua                   # Lua-based lock release
└── db/hmdp.sql                  # Schema + seed data

frontend/                        # Vue 3 SPA (see frontend/README.md)
```

---

## Quick Start

### 0. Prerequisites
| Software | Tested version |
|---|---|
| JDK | 1.8 |
| Maven | 3.9 |
| MySQL | 8.0 |
| Redis | 5.0+ (Windows: tporadowski/redis) |
| Kafka | 3.9.x (KRaft mode, no ZooKeeper) |
| Node | 16+ |

### 1. Backend setup
```bash
# Create database (default root password is empty in application.yaml)
mysql -uroot -e "CREATE DATABASE dingping DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot --init-command="SET SESSION sql_mode=''" dingping < src/main/resources/db/hmdp.sql

# Start Redis (Windows: cd C:\redis && redis-server.exe redis.windows.conf)
# Start Kafka in KRaft mode (see frontend/README.md for one-time format)

# Run the app
mvn spring-boot:run
```

Then in IDE, run `ShopCacheTest.testCacheAllShops()` **once** to pre-warm the shop cache (the detail endpoint uses logical-expiration caching).

### 2. Frontend setup
```bash
cd frontend
npm install --registry=https://registry.npmmirror.com
npm run dev          # http://localhost:5173
```

### 3. Demo flow
1. **Sign in** with any 11-digit phone (e.g. `13800138000`). The SMS code is printed in the backend console.
2. Browse **Shops** → pick a category → open a shop.
3. Click **🛠 Create voucher (demo)** in the shop detail page to seed a seckill voucher (the DB is empty by default).
4. Click **Seckill Now**. Click twice — the second attempt is rejected by the Lua "one-per-user" predicate.
5. Open **Notes** to see the like leaderboard. Click any post to like/unlike and see the top 5 likers.

---

## API Surface

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/user/code?phone=…` | – | Generate SMS code (printed in log) |
| `POST` | `/user/login` | – | Phone + code → returns token |
| `GET` | `/user/me` | ✅ | Current user |
| `GET` | `/shop-type/list` | – | Shop categories (cached in Redis List) |
| `GET` | `/shop/of/type?typeId=…` | – | Paged shops in a category |
| `GET` | `/shop/{id}` | – | Shop detail (logical-expiration cache) |
| `GET` | `/voucher/list/{shopId}` | – | Vouchers for a shop |
| `POST` | `/voucher/seckill` | – | Create a seckill voucher (demo tool) |
| `POST` | `/voucher-order/seckill/{id}` | ✅ | Buy → Lua → Kafka → DB |
| `GET` | `/blog/hot?current=…` | – | Posts ranked by likes |
| `GET` | `/blog/{id}` | ✅ | Post detail |
| `PUT` | `/blog/like/{id}` | ✅ | Toggle like |
| `GET` | `/blog/likes/{id}` | – | First 5 likers |

---

## Repository Layout

```
NTU-Smarthub/
├── README.md                   ← you are here
├── LEARNING_NOTES.md           ← original Chinese deep-dive on every tech decision
├── docs/diagrams/              ← UML class & sequence diagrams (Markdown + Mermaid)
├── frontend/                   ← Vue 3 demo SPA
├── pom.xml
└── src/
    ├── main/                   ← production code
    └── test/                   ← cache-prewarm + JMeter targets
```

> 📘 **`LEARNING_NOTES.md`** keeps the full Chinese walkthrough of *why* each pattern was chosen — kept verbatim for study purposes.

---

## Course Context

Built as the term project for **CZ3002 / SE Group Project** at NTU. The brief asked for an end-to-end software-engineering deliverable demonstrating UML modelling, system architecture, distributed concerns, and a working full-stack prototype.

License: MIT.
