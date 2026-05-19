package com.hmdp.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 秒杀订单异步管道配置（与 PPT P4 一致）。
 * <ul>
 *   <li>Topic: seckill.orders，3 个分区</li>
 *   <li>消息按 voucherId 分区，保证同一券的订单有序消费</li>
 *   <li>Consumer Group: hmdp-seckill-group</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    public static final String SECKILL_ORDER_TOPIC = "seckill.orders";
    public static final String SECKILL_ORDER_GROUP = "hmdp-seckill-group";

    @Bean
    public NewTopic seckillOrdersTopic() {
        return TopicBuilder.name(SECKILL_ORDER_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
