package com.antigravity.agents.analysis;

import com.antigravity.agents.BaseAgent;
import com.antigravity.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AnalysisAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(AnalysisAgent.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LocalMarketApiClient localMarketApiClient;

    @Value("${antigravity.api.localmarket.url}")
    private String localMarketEndpoint;

    public AnalysisAgent(ChatClient.Builder chatClientBuilder,
            KafkaTemplate<String, String> kafkaTemplate,
            LocalMarketApiClient localMarketApiClient) {
        super(chatClientBuilder, "AnalysisAgent");
        this.kafkaTemplate = kafkaTemplate;
        this.localMarketApiClient = localMarketApiClient;
    }

    /**
     * Scheduled poll of the LocalMarket backend.
     * Rate configured by antigravity.agent.analysis.poll-rate-ms in
     * application.properties.
     * The real market data is fetched via LocalMarketApiClient, then passed to the
     * AI
     * for structured analysis before emitting to the Kafka market.analysis.health
     * topic.
     */
    @Scheduled(fixedRateString = "${antigravity.agent.analysis.poll-rate-ms:60000}")
    public void evaluateMarket() {
        log.info("[AnalysisAgent] Starting scheduled market evaluation cycle.");
        try {
            Map<String, Object> rawData = localMarketApiClient.fetchLatestMarketData("AAL");
            processMarketData(rawData.toString());
        } catch (Exception e) {
            log.error("[AnalysisAgent] Market evaluation cycle failed.", e);
        }
    }

    /**
     * Public entry point for simulation mode.
     */
    public void processMarketData(String rawDataString) {
        log.debug("[AnalysisAgent] Processing market block: {}", rawDataString);
        try {

            String systemPrompt = """
                    You are the Antigravity Analysis Agent.
                    You receive raw LocalMarket data and must analyse it for trading signals.
                    Return ONLY a valid JSON object matching the market.analysis.health schema:
                    {
                      "timestamp": "<ISO8601>",
                      "asset_id": "<string>",
                      "volatility_score": <double 0.0-1.0>,
                      "trend": "BULLISH|BEARISH|NEUTRAL",
                      "anomaly_detected": <boolean>,
                      "recommended_strategy": "DISCRETE_SWING|HOLD|ACCUMULATE",
                      "confidence": <double 0.0-1.0>
                    }
                    Rules:
                    - anomaly_detected must be true if volume_24h is 3x the normal range.
                    - If anomaly_detected is true, recommended_strategy must always be HOLD.
                    """;

            String eventPayload = this.chatClient.prompt()
                    .system(systemPrompt)
                    .user("Raw LocalMarket Data: " + rawDataString)
                    .call()
                    .content();

            log.info("[AnalysisAgent] Market health derived. Emitting to Kafka topic={}.",
                    KafkaConfig.TOPIC_MARKET_HEALTH);
            kafkaTemplate.send(KafkaConfig.TOPIC_MARKET_HEALTH, eventPayload);

        } catch (Exception e) {
            log.error("[AnalysisAgent] Market evaluation cycle failed. Skipping this cycle.", e);
        }
    }
}
