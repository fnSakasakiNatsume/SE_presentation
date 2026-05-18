# NTU Smarthub — Frontend

Vue 3 + Vite + Element Plus + Pinia + Axios

## Prerequisites

Before starting the frontend, make sure the backend stack is up:

- MySQL 8 on `3306`, schema `dingping`, imported from `src/main/resources/db/hmdp.sql`
- Redis on `6379` (Windows users: `C:\redis\redis-server.exe redis.windows.conf`)
- Kafka on `9092`, KRaft mode, installed at `C:\kafka`
- Spring Boot app on `8081` (run `HmDianPingApplication` in IDEA)

> ⚠️ **Pre-warm the shop cache first**
> Shop detail uses a *logical-expiration* caching strategy, so you must run
> `src/test/java/com/hmdp/ShopCacheTest.testCacheAllShops()` **once** after
> the first SQL import — otherwise every shop detail page reports "shop not found".

## Run

```bash
cd frontend
npm install --registry=https://registry.npmmirror.com
npm run dev
```

Open http://localhost:5173 — Node 16+ is required (Vite 4 is pinned for compatibility).

## Demo flow (5 minutes covers every tech highlight)

### 1. Sign in (Redis-Hash session + dual interceptor)
1. Open the login page and enter any 11-digit phone number (e.g. `13800138000`).
2. Click **Send code** → switch to the IDE console and look for:
   `短信验证码发送成功：123456`
3. Paste the code, sign in, you land on the home page.

**What to mention:** the returned token keys a Redis Hash that stores the user DTO; an outer interceptor refreshes its TTL on every request, an inner one rejects unauthenticated traffic.

### 2. Browse shops (cache breakdown + logical expiration)
1. Pick a category in the home page.
2. Open any shop card.

**What to mention:** shop categories sit in a Redis List; shop detail uses *logical expiration* with an asynchronous rebuild thread + mutex lock to defeat cache breakdown without freezing the request.

### 3. Seckill (Lua + distributed lock + Kafka async)
1. In the shop detail page, click **🛠 Create voucher (demo)** to seed a seckill voucher.
2. Click **Seckill Now** → an order id pops up.
3. Click **Seckill Now** again → rejected by the one-per-user predicate.
4. Open `tb_voucher_order` in the DB — your order is there (Kafka consumer wrote it).

**What to mention:**
- A single Lua script does *stock check + dedup + decrement + record user* atomically.
- The order message is dropped into Kafka topic `seckill.orders` (3 partitions, keyed by `voucherId` → same voucher → same partition → ordered consumption).
- HTTP returns the order id immediately; MySQL is written asynchronously by the consumer group.

### 4. Notes & likes (ZSet leaderboard)
1. Click **Notes** in the top nav.
2. The page shows a Top-3 podium (🥇🥈🥉) followed by the rest of the posts.
3. Open any post → click ❤️ → see your avatar in the *Top 5 First Likers* row.

**What to mention:** `like` uses a Redis ZSet with timestamp scores — toggle semantics via `ZSCORE` then `ZADD`/`ZREM`. The Top-5 likers row is just `ZRANGE key 0 4`, the leaderboard is `ORDER BY liked DESC`.

## File layout

```
frontend/
├── package.json
├── vite.config.js              # /api proxy → :8081
├── index.html
└── src/
    ├── main.js                  # Vue + Pinia + ElementPlus (en locale)
    ├── App.vue
    ├── api/
    │   ├── request.js           # Axios: auto-attach token, 401 → /login
    │   └── index.js             # All endpoint wrappers
    ├── router/index.js          # Routes + login guard
    ├── stores/user.js           # Pinia: token + user in localStorage
    ├── components/
    │   └── AppHeader.vue
    └── views/
        ├── Login.vue
        ├── Home.vue            # Shop browsing
        ├── ShopDetail.vue      # Shop info + voucher seckill
        ├── BlogList.vue        # Discovery posts + leaderboard
        └── BlogDetail.vue      # Post + like + top likers
```

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| "shop not found" on shop detail | Redis not pre-warmed | Run `ShopCacheTest.testCacheAllShops()` |
| "stock not enough" on seckill | Redis key `seckill:stock:{id}` is 0 / missing | Create a new voucher via the dialog (it auto-writes to Redis) |
| `npm install` hangs | Mainland network | Append `--registry=https://registry.npmmirror.com` |
| CORS error in browser console | Backend CORS missing | Check `MvcConfig.addCorsMappings` allows `http://localhost:5173` |
| Login always returns 401 | Kafka not running | `cd C:\kafka && bin\windows\kafka-server-start.bat config\kraft\server.properties` |

## What to add next

- Comments on posts (`POST /blog-comments`)
- Follow / unfollow merchants and friends (`POST /follow/{id}`)
- Off-campus delivery feed (Coconut-style scheduled drops)
- Event organiser dashboard (ticket-rush statistics, refunds)
