package com.antigravity.agents.analysis;

import com.antigravity.agents.BaseAgent;
import com.antigravity.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AnalysisAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(AnalysisAgent.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public AnalysisAgent(ChatClient.Builder chatClientBuilder, KafkaTemplate<String, String> kafkaTemplate) {
        super(chatClientBuilder, "AnalysisAgent");
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Simulated scheduled poll of the LocalMarket backend.
     * In reality, this would connect via WebSockets or REST to the external Broker
     * Gateway.
     */
    // @Scheduled(fixedRate = 60000)
    public void evaluateMarket() {
        log.info("[AnalysisAgent] Evaluating Local Market Data...");

        String prompt = "Generate a JSON payload simulating a LocalMarket bullish shift detected via volume indicators. Use the format specified in Phase 2 for market.analysis.health.";

        try {
            String eventPayload = this.chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("[AnalysisAgent] Market Health derived. Emitting Kafka Event.");
            kafkaTemplate.send(KafkaConfig.TOPIC_MARKET_HEALTH, eventPayload);

        } catch (Exception e) {
            log.error("[AnalysisAgent] LocalMarket API polling failed.");
        }
    }
}
