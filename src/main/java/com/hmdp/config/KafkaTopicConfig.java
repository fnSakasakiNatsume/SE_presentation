package com.hmdp.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic 声明：Spring 启动时若 broker 上不存在则自动创建。
 * 秒杀订单 topic 用 3 个分区，演示水平扩展能力。
 */
@Configuration
public class KafkaTopicConfig {

    public static final String SECKILL_ORDER_TOPIC = "seckill.orders";

    @Bean
    public NewTopic seckillOrderTopic() {
        return TopicBuilder.name(SECKILL_ORDER_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
