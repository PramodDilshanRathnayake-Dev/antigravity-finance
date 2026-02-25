package com.antigravity.agents.trade;

import com.antigravity.agents.BaseAgent;
import com.antigravity.config.KafkaConfig;
import com.antigravity.models.Trade;
import com.antigravity.models.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TradeAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(TradeAgent.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TradeRepository tradeRepository;
    private final SandboxBrokerClient sandboxBrokerClient;

    public TradeAgent(ChatClient.Builder chatClientBuilder, KafkaTemplate<String, String> kafkaTemplate,
            TradeRepository tradeRepository, SandboxBrokerClient sandboxBrokerClient) {
        // Trade Agent specifically requires function calling capabilities bound to
        // 'VerifyCapitalConstraint'
        super(chatClientBuilder.defaultFunctions("verifyCapitalConstraint"), "TradeAgent");
        this.kafkaTemplate = kafkaTemplate;
        this.tradeRepository = tradeRepository;
        this.sandboxBrokerClient = sandboxBrokerClient;
    }

    public record TradeDecision(String assetId, String action, BigDecimal amountAllocated, BigDecimal executionPrice,
            String strategyUsed, BigDecimal cvarExposure) {
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
                If approved, decide on the appropriate trade details.
                """;

        BeanOutputConverter<TradeDecision> converter = new BeanOutputConverter<>(TradeDecision.class);
        String formatRequired = converter.getFormat();

        try {
            String aiResponse = this.chatClient.prompt()
                    .system(s -> s.text(systemPrompt + "\n\n{format}").param("format", formatRequired))
                    .user("Market Event: \n" + marketHealthJson)
                    .call()
                    .content();

            log.info("[TradeAgent] Reasoning completed. Parsing structured output if trade was approved.");

            if (aiResponse != null && aiResponse.contains("assetId") && !aiResponse.contains("DENIED")) {
                TradeDecision decision = converter.convert(aiResponse);

                Trade tradeRecord = new Trade(
                        "usr_001", // MVP: Single user system
                        decision.assetId(),
                        decision.action(),
                        decision.amountAllocated(),
                        decision.executionPrice(),
                        decision.strategyUsed(),
                        decision.cvarExposure());

                tradeRepository.save(tradeRecord);

                // Execute order in sandbox
                int quantity = decision.amountAllocated()
                        .divide(decision.executionPrice(), 0, RoundingMode.FLOOR)
                        .intValue();

                if (quantity > 0) {
                    log.info("[TradeAgent] Executing sandbox order: {} {} units", decision.action(), quantity);
                    sandboxBrokerClient.placeOrder(decision.assetId(), decision.action(), quantity,
                            decision.executionPrice());
                } else {
                    log.warn("[TradeAgent] Calculated quantity is 0 for amount={}. Skipping sandbox execution.",
                            decision.amountAllocated());
                }

                kafkaTemplate.send(KafkaConfig.TOPIC_TRADE_LOGS, aiResponse);
                log.info("[TradeAgent] Trade successfully persisted and broadcast to Kafka.");
            } else {
                log.warn("[TradeAgent] Non-trading decision reached. Potentially bounded by Capital Constraint.");
            }

        } catch (Exception e) {
            log.error("[TradeAgent] Reasoning engine failed. Defaulting to safe passive posture.", e);
        }
    }
}
