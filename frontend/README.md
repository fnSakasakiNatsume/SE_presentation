# 黑马点评 · 桌面端演示前端

Vue 3 + Vite + Element Plus + Pinia + Axios

## 一、启动前依赖

先把后端这些东西跑起来：

- MySQL 3306（schema 名 `dingping`，导入 `src/main/resources/db/hmdp.sql`）
- Redis 6379
- Kafka 9092（已安装到 `C:\kafka`，KRaft 模式无需 ZooKeeper）
- 后端：在 IDEA 运行 `HmDianPingApplication`，端口 8081

> ⚠️ **重要：商铺详情用的是逻辑过期缓存，必须先预热！**
> 跑一次 `src/test/java/com/hmdp/ShopCacheTest.testCacheAllShops()`，
> 把所有商铺写入 Redis，不然点商铺会报"店铺不存在"。

## 二、启动前端

```bash
cd frontend
npm install        # 第一次安装，5~10 分钟
npm run dev        # 启动开发服务器
```

打开 http://localhost:5173 即可。

> Node 版本要求 ≥ 16（项目已锁定 Vite 4 兼容你当前的 Node 16.13）。
> 国内网慢可以：`npm install --registry=https://registry.npmmirror.com`

## 三、演示流程（≈ 5 分钟讲完所有亮点）

### 1. 登录（亮点：Redis 替代 Session + 双拦截器）

1. 浏览器打开 http://localhost:5173 → 自动跳到 `/login`
2. 输入任意手机号（如 `13800138000`）→ 点【发送验证码】
3. 切到 IDEA 控制台，找日志：`短信验证码发送成功：123456`
4. 把验证码填回去 → 登录成功，跳转首页

**讲解点**：
- 后端给的 token 存在 localStorage，每次请求带 `authorization` 头
- Redis 里 key `login:token:{token}` 存了用户信息，30 分钟自动续期

### 2. 商铺列表 + 详情（亮点：缓存击穿 / 逻辑过期）

1. 首页点 tab 切分类
2. 点击商铺卡片 → 进入详情页

**讲解点**：
- 商铺分类走的是 Redis List 缓存
- 商铺详情走的是 **逻辑过期** + **互斥锁** + **异步重建**

### 3. 优惠券秒杀（核心亮点：Lua + 分布式锁 + 异步 MQ）

1. 商铺详情页 → 点右上角【🛠 演示工具：创建一张秒杀券】
2. 直接点【创建】
3. 出现红色边框的秒杀券 → 点【立即秒杀】
4. 弹出订单号
5. 再点一次秒杀 → 弹出"不能重复下单"（一人一单生效 ✅）
6. 打开数据库 `tb_voucher_order` → 看到刚才的订单已经入库

**讲解点**：
- Lua 脚本在 Redis 单线程内原子完成：库存判断 + 一人一单 + 扣减
- 订单消息进 **Kafka topic `seckill.orders`（3 分区）**，消费者组 `hmdp-seckill-group` 异步入库
- 接口立即返回订单号，不等数据库写入，吞吐量大幅提升
- 用 voucherId 做 Kafka key → 同一张券的订单进同一分区，方便分区内顺序消费 / 水平扩展

## 四、目录结构

```
frontend/
├── package.json
├── vite.config.js             # /api 代理到 8081
├── index.html
└── src/
    ├── main.js
    ├── App.vue
    ├── api/
    │   ├── request.js          # Axios 封装，自动带 token，401 跳登录
    │   └── index.js            # 所有接口
    ├── router/index.js         # 路由 + 登录守卫
    ├── stores/user.js          # Pinia user store
    ├── components/
    │   └── AppHeader.vue
    └── views/
        ├── Login.vue
        ├── Home.vue
        └── ShopDetail.vue
```

## 五、常见报错

| 报错 | 原因 | 解决 |
|---|---|---|
| 登录后点商铺报"店铺不存在！" | Redis 没预热 | 跑 `ShopCacheTest.testCacheAllShops()` |
| 秒杀报"库存不足" | Redis 里 `seckill:stock:{id}` 是 0 或不存在 | 用演示工具重新创建秒杀券（自动写库存） |
| `npm install` 卡住 | 国内网络 | `npm install --registry=https://registry.npmmirror.com` |
| CORS 报错 | 后端没改 CORS | 确认 `MvcConfig.java` 有 `addCorsMappings` |
| 401 一直跳登录 | 后端 Kafka 没启动 listener 起不来 | 启动 Kafka：`cd C:\kafka && bin\windows\kafka-server-start.bat config\kraft\server.properties` |

## 六、后续可加的功能

- 探店笔记列表（`GET /blog/hot`）+ 点赞（`PUT /blog/like/{id}`）
- 关注 / 共同关注
- 附近商铺（需要给商铺造 GEO 数据）
- 签到日历
