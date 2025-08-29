package com.org.highconcurrencylike.config;

import com.org.highconcurrencylike.constant.ThumbConstant;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.RedeliveryBackoff;
import org.apache.pulsar.client.impl.MultiplierRedeliveryBackoff;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.pulsar.annotation.PulsarListenerConsumerBuilderCustomizer;

import java.util.concurrent.TimeUnit;

/**
 * 自定义 Customizer
 */
@Configuration
public class ThumbConsumerConfig<T> implements PulsarListenerConsumerBuilderCustomizer<T> {
    @Override
    public void customize(ConsumerBuilder<T> consumerBuilder) {
        // 这里配置批量接受策略，可以提高系统吞吐量和减少操作数据库的次数
        consumerBuilder.batchReceivePolicy(
                BatchReceivePolicy.builder()
                        // 每次处理1000条
                        .maxNumMessages(1000)
                        // 设置超时时间(单位：毫秒)
                        .timeout(10000, TimeUnit.MILLISECONDS)
                        .build()
        );
    }

    // 配置NACK重试策略
    @Bean
    public RedeliveryBackoff negativeAckRedeliveryBackoff(){
        return MultiplierRedeliveryBackoff.builder()
                // 初始延迟1秒
                .minDelayMs(1000)
                // 最大延迟60秒
                .maxDelayMs(60_000)
                // 每次重试延迟倍数
                .multiplier(2)
                .build();
    }

    // 配置ACK超时重试策略
    @Bean
    public RedeliveryBackoff ackTimeoutRedeliveryBackoff(){
        return MultiplierRedeliveryBackoff.builder()
                // 延迟5秒
                .minDelayMs(5000)
                // 延迟300秒
                .maxDelayMs(300_000)
                // 每次延迟倍数
                .multiplier(3)
                .build();
    }

    @Bean
    public DeadLetterPolicy deadLetterPolicy() {
        return DeadLetterPolicy.builder()
                // 最大重试次数
                .maxRedeliverCount(3)
                // 死信主题名称
                .deadLetterTopic(ThumbConstant.THUMB_DLQ_TOPIC)
                .build();
    }

}
