package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.SeckillVoucherServiceImpl;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Kafka 消费者：从 seckill.orders 拉取消息，异步入库。
 * 消费者组 hmdp-seckill-group 保证同一条订单只被组内一个实例消费。
 */
@Component
@Slf4j
public class SeckillVoucherListener {

    @Resource
    SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    VoucherOrderServiceImpl voucherOrderService;

    @KafkaListener(topics = "seckill.orders", groupId = "hmdp-seckill-group")
    public void onSeckillOrder(String msg) {
        log.info("[Kafka] 收到秒杀订单消息: {}", msg);
        VoucherOrder voucherOrder = JSONUtil.toBean(msg, VoucherOrder.class);
        // 1. 保存订单
        voucherOrderService.save(voucherOrder);
        // 2. 数据库库存 -1（with stock > 0 防止超卖兜底）
        Long voucherId = voucherOrder.getVoucherId();
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        log.info("[Kafka] 订单 {} 已入库", voucherOrder.getId());
    }
}
