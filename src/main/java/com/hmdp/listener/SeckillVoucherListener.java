package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.SeckillVoucherServiceImpl;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * 秒杀订单 MQ 消费者：保存订单并扣减库存
 */
@Component
@Slf4j
public class SeckillVoucherListener {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @RabbitListener(queues = "QA")
    public void onNormalQueue(Message message) {
        processMessage(message, "QA");
    }

    @RabbitListener(queues = "QD")
    public void onDeadLetterQueue(Message message) {
        processMessage(message, "QD");
    }

    private void processMessage(Message message, String queueLabel) {
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("{} 队列收到秒杀订单消息", queueLabel);
        VoucherOrder voucherOrder = JSONUtil.toBean(msg, VoucherOrder.class);
        log.info("订单内容: {}", voucherOrder);
        voucherOrderService.save(voucherOrder);

        Long voucherId = voucherOrder.getVoucherId();
        boolean updated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!updated) {
            log.warn("扣减秒杀库存未生效, voucherId={}, queue={}", voucherId, queueLabel);
        }
    }
}
