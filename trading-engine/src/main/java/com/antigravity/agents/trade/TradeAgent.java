package com.antigravity.agents.trade;

import com.antigravity.agents.BaseAgent;
import com.antigravity.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TradeAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(TradeAgent.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public TradeAgent(ChatClient.Builder chatClientBuilder, KafkaTemplate<String, String> kafkaTemplate) {
        // Trade Agent specifically requires function calling capabilities bound to
        // 'VerifyCapitalConstraint'
        super(chatClientBuilder.defaultFunctions("verifyCapitalConstraint"), "TradeAgent");
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_MARKET_HEALTH, groupId = "antigravity-agents")
    public void processMarketEvent(String marketHealthJson) {
        log.info("[TradeAgent] Received Market Event: {}", marketHealthJson);

        // Use Spring AI ChatClient to interpret the JSON event and decide to trade
        String systemPrompt = """
                You are the Antigravity Trade Agent.
                A market health event has just occurred. You represent the core strategy execution for LocalMarket assets.
                You are strictly forbidden from placing a trade without FIRST invoking the verifyCapitalConstraint tool.
                If the verification tool denies the allocation, you MUST output a log explaining the denial and abort.
                If approved, output the JSON structure for the trade.execution.logs topic.
                """;

        try {
            String aiResponse = this.chatClient.prompt()
                    .system(systemPrompt)
                    .user("Market Event: \n" + marketHealthJson)
                    .call()
                    .content();

            log.info("[TradeAgent] AI Reasoning Output: {}", aiResponse);

            // In a full implementation, parser logic converts the AI JSON text block and
            // pushes to Kafka TOPIC_TRADE_LOGS.

        } catch (Exception e) {
            log.error("[TradeAgent] Reasoning engine failed. Defaulting to safe passive posture.", e);
        }
    }
}
