# JMeter 压测（NTU Smarthub）

与课程演示 PPT Slide 21–22 对应。

## 准备

1. 启动 MySQL、Redis、Kafka，并运行 Spring Boot 应用（`application.yaml` 已配置 Kafka）。
2. 执行环境准备脚本：

```powershell
.\demo_data\setup.ps1 -MySqlPassword <your-password>
```

脚本会：

- 清空 `tb_voucher_order`
- 清除 Redis `seckill:*`
- 创建 100 库存秒杀券并写入 Redis
- 注入 500 个 `login:token:*` 到 Redis
- 生成 `jmeter/tokens.csv` 与 `jmeter/seckill-meta.env`

3. 新增商铺后执行缓存预热：

```powershell
.\demo_data\add_ntu_data.ps1
```

## 测试计划

在 JMeter 5.6+ 中打开 `NTU-Smarthub.jmx`，配置：

| 线程组 | 说明 |
|--------|------|
| Cache Hit | 1000 用户访问 `GET /shop/1` |
| Same-User Duplicate | 1 用户 10 次并发秒杀同一券 |
| Oversell | 500 用户抢 100 库存 |

请求头需带：`authorization: ${token}`（从 CSV 读取）。

## 预期结果（Slide 22）

- 缓存命中：TPS 200+，错误率 0%，平均延迟 ~18ms
- 超卖测试：MySQL 订单 100 条、100 个不同用户、Redis 库存为 0
- JMeter 约 20%「错误」来自业务拒绝（库存不足），属正常行为
