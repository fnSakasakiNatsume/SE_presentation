package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.config.KafkaConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 秒杀订单 Kafka 消费者（group: hmdp-seckill-group）。
 */
@Component
@Slf4j
public class SeckillVoucherListener {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @KafkaListener(
            topics = KafkaConfig.SECKILL_ORDER_TOPIC,
            groupId = KafkaConfig.SECKILL_ORDER_GROUP
    )
    public void onSeckillOrder(ConsumerRecord<String, String> record) {
        String msg = record.value();
        log.info("Kafka 收到秒杀订单, partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());
        VoucherOrder voucherOrder = JSONUtil.toBean(msg, VoucherOrder.class);
        log.info("订单内容: {}", voucherOrder);
        voucherOrderService.handleVoucherOrder(voucherOrder);
    }
}
