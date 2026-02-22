package com.antigravity.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_MARKET_HEALTH = "market.analysis.health";
    public static final String TOPIC_TRADE_LOGS = "trade.execution.logs";
    public static final String TOPIC_AUDIT_TRACES = "system.audit.traces";

    @Bean
    public NewTopic marketHealthTopic() {
        return TopicBuilder.name(TOPIC_MARKET_HEALTH)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic tradeLogsTopic() {
        return TopicBuilder.name(TOPIC_TRADE_LOGS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditTracesTopic() {
        return TopicBuilder.name(TOPIC_AUDIT_TRACES)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
