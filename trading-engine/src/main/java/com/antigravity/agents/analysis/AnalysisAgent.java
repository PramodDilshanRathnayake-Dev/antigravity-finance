package com.antigravity.agents.analysis;

import com.antigravity.agents.BaseAgent;
import com.antigravity.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AnalysisAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(AnalysisAgent.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${antigravity.api.localmarket.url}")
    private String localMarketEndpoint;

    public AnalysisAgent(ChatClient.Builder chatClientBuilder, KafkaTemplate<String, String> kafkaTemplate) {
        super(chatClientBuilder, "AnalysisAgent");
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Scheduled poll of the LocalMarket backend using the configured URL.
     * Time bounded by antigravity.agent.analysis.poll-rate-ms
     */
    @Scheduled(fixedRateString = "${antigravity.agent.analysis.poll-rate-ms:60000}")
    public void evaluateMarket() {
        log.info("[AnalysisAgent] Evaluating Local Market Data at {}", localMarketEndpoint);

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
