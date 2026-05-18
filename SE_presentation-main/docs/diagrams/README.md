# 大众点评（hm-dianping）项目 UML 图

本目录使用 Mermaid 描述本项目的架构。文件分为两类：

## 类图（Class Diagrams）

| 文件 | 说明 |
| ---- | ---- |
| [class-entities.md](./class-entities.md) | 数据库实体（`com.hmdp.entity`）及其关联关系 |
| [class-service-layer.md](./class-service-layer.md) | Controller / Service / Mapper 三层架构关系 |
| [class-utils-interceptor.md](./class-utils-interceptor.md) | 工具类、拦截器、配置、消息监听器 |

## 时序图（Sequence Diagrams）

| 文件 | 说明 |
| ---- | ---- |
| [sequence-login.md](./sequence-login.md) | 手机号 + 验证码登录、token 校验/刷新 |
| [sequence-seckill.md](./sequence-seckill.md) | 优惠券秒杀（Lua 脚本 + RabbitMQ 异步下单 + 死信队列） |
| [sequence-shop-query.md](./sequence-shop-query.md) | 商铺查询（逻辑过期解决缓存击穿） |
| [sequence-blog-feed.md](./sequence-blog-feed.md) | 发布探店笔记 + 关注收件箱（Feed 流） |
| [sequence-follow.md](./sequence-follow.md) | 关注 / 取关 / 共同关注 |
| [sequence-sign.md](./sequence-sign.md) | 签到与连续签到统计（Redis BitMap） |

## 作业章节（English, Submission #3）

| 文件 | 说明 |
| ---- | ---- |
| [section-5-system-architecture.md](./section-5-system-architecture.md) | Section 5 — System Architecture (Nginx → Spring Boot → Redis → Kafka → MySQL) |
| [section-6-application-skeleton.md](./section-6-application-skeleton.md) | Section 6 — Application Skeleton (layers + tech stack with versions) |
| [section-7-testing-framework.md](./section-7-testing-framework.md) | Section 7 — Testing Framework (JUnit / Mockito + JMeter 200–300 QPS) |

> 渲染：在 IDE / GitHub / `mermaid.live` 中直接打开 `.md` 即可。
