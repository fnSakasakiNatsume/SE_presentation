package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.QueueConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Objects;

/**
 * 秒杀订单服务实现
 *
 * <p>优化点（偏代码质量 C）：</p>
 * <ul>
 *   <li>拆分方法：Lua 校验、构建订单、发送 MQ、落库分别处理</li>
 *   <li>去掉 self 注入绕过代理的写法：让事务方法只负责“真正的落库逻辑”</li>
 *   <li>锁与失败路径更清晰：哪些会返回失败，哪些会抛异常让 MQ 消费端重试</li>
 * </ul>
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final String ORDER_LOCK_PREFIX = "lock:order:";

    private final ISeckillVoucherService seckillVoucherService;
    private final RabbitTemplate rabbitTemplate;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService,
                                   RabbitTemplate rabbitTemplate,
                                   RedisIdWorker redisIdWorker,
                                   StringRedisTemplate stringRedisTemplate,
                                   RedissonClient redissonClient) {
        this.seckillVoucherService = seckillVoucherService;
        this.rabbitTemplate = rabbitTemplate;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 入口：用户发起秒杀请求（同步快速返回）
     * <p>Lua 脚本做库存/一人一单资格校验，通过后把订单消息发到 MQ。</p>
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        if (voucherId == null) {
            return Result.fail("voucherId不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        SeckillCheckResult check = executeSeckillLua(voucherId, userId, orderId);
        if (!check.success) {
            return Result.fail(check.failMessage);
        }

        VoucherOrder order = buildVoucherOrder(orderId, userId, voucherId);
        sendOrderToMq(order);

        return Result.ok(orderId);
    }

    /**
     * MQ 消费端调用：处理订单落库
     * <p>这里用 Redisson 锁做“同一用户串行落单”，避免并发重复创建。</p>
     */
    @Override
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        if (voucherOrder == null || voucherOrder.getUserId() == null) {
            log.error("handleVoucherOrder 参数非法: {}", voucherOrder);
            return;
        }

        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock(ORDER_LOCK_PREFIX + userId);

        boolean locked = false;
        try {
            // 这里仍沿用 tryLock() 的无等待策略（与你现有行为一致），只是把流程写清楚
            locked = lock.tryLock();
            if (!locked) {
                log.warn("不允许重复下单(无法获取锁), userId={}, voucherId={}", userId, voucherOrder.getVoucherId());
                return;
            }
            // 真正落库（带事务）
            createVoucherOrder(voucherOrder);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 真正的“落库事务”方法：幂等校验 + 扣库存 + 保存订单
     *
     * <p>注意：如果你希望 MQ 在失败时重试，这里就应该抛异常而不是 return。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Objects.requireNonNull(voucherOrder, "voucherOrder不能为空");
        Objects.requireNonNull(voucherOrder.getUserId(), "userId不能为空");
        Objects.requireNonNull(voucherOrder.getVoucherId(), "voucherId不能为空");

        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 1) 一人一单校验（DB 兜底）
        int count = lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId)
                .count();
        if (count > 0) {
            log.info("用户已购买过该券，忽略重复消息 userId={}, voucherId={}, orderId={}",
                    userId, voucherId, voucherOrder.getId());
            return;
        }

        // 2) 扣库存（乐观锁：where stock > 0）
        boolean stockOk = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!stockOk) {
            log.info("库存不足，无法创建订单 userId={}, voucherId={}, orderId={}",
                    userId, voucherId, voucherOrder.getId());
            return;
        }

        // 3) 保存订单
        boolean saved = save(voucherOrder);
        if (!saved) {
            // 这里抛异常让事务回滚（库存扣减也会回滚）
            throw new IllegalStateException("保存订单失败，orderId=" + voucherOrder.getId());
        }
    }

    // ------------------------ private helpers ------------------------

    private SeckillCheckResult executeSeckillLua(Long voucherId, Long userId, long orderId) {
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );

        if (result == null) {
            log.error("秒杀 Lua 返回 null, voucherId={}, userId={}", voucherId, userId);
            return SeckillCheckResult.fail("秒杀繁忙，请稍后重试");
        }

        int r = result.intValue();
        if (r == 0) {
            return SeckillCheckResult.ok();
        }
        if (r == 1) {
            return SeckillCheckResult.fail("库存不足");
        }
        return SeckillCheckResult.fail("不能重复下单");
    }

    private static VoucherOrder buildVoucherOrder(long orderId, Long userId, Long voucherId) {
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        return order;
    }

    private void sendOrderToMq(VoucherOrder order) {
        String jsonStr = JSONUtil.toJsonStr(order);
        try {
            rabbitTemplate.convertAndSend(
                    QueueConfig.X_EXCHANGE,
                    QueueConfig.SECKILL_ORDER_ROUTING_KEY,
                    jsonStr
            );
        } catch (Exception e) {
            // 这里选择抛异常：让调用方感知失败（并由上层统一处理）
            log.error("发送 RabbitMQ 消息失败，orderId={}, userId={}, voucherId={}",
                    order.getId(), order.getUserId(), order.getVoucherId(), e);
            throw new RuntimeException("发送消息失败", e);
        }
    }

    private static final class SeckillCheckResult {
        private final boolean success;
        private final String failMessage;

        private SeckillCheckResult(boolean success, String failMessage) {
            this.success = success;
            this.failMessage = failMessage;
        }

        static SeckillCheckResult ok() {
            return new SeckillCheckResult(true, null);
        }

        static SeckillCheckResult fail(String msg) {
            return new SeckillCheckResult(false, msg);
        }
    }
}