package com.antigravity.agents.observer;

import com.antigravity.agents.BaseAgent;
import com.antigravity.config.KafkaConfig;
import com.antigravity.models.AgentAuditLog;
import com.antigravity.models.AgentAuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ObserverAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(ObserverAgent.class);

    private final AgentAuditLogRepository auditRepository;
    private final ObjectMapper objectMapper;

    // Threshold from GEMINI.md: isolate sub-agents if confidence < 0.85
    private static final double CONFIDENCE_THRESHOLD = 0.85;

    public ObserverAgent(ChatClient.Builder chatClientBuilder,
            AgentAuditLogRepository auditRepository,
            ObjectMapper objectMapper) {
        super(chatClientBuilder, "ObserverAgent");
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Listens to Trade Execution Logs and the generic Audit Traces topic.
     * Evaluates each payload against the GEMINI.md constraints with a structured AI
     * evaluation:
     * - Cloud provider hallucinations (Azure, GCP references)
     * - LocalMarket specification compliance
     * - Confidence scoring for sub-agent isolation
     */
    @KafkaListener(topics = { KafkaConfig.TOPIC_AUDIT_TRACES,
            KafkaConfig.TOPIC_TRADE_LOGS }, groupId = "antigravity-agents")
    public void monitorAgentActivities(String payload) {
        log.debug("[ObserverAgent] Intercepted event payload for GEMINI.md audit checks.");

        String systemPrompt = """
                You are the Antigravity Observer Agent enforcing the rules defined in GEMINI.md.
                Analyze the payload and return ONLY a valid JSON object with the following schema:
                {
                  "confidence_score": <double 0.0 to 1.0>,
                  "hallucination_detected": <boolean>,
                  "reasoning": "<one-sentence explanation>",
                  "origin_agent": "<agent name if identifiable, else 'unknown'>"
                }
                Rules:
                - Deduct from confidence if Azure, AWS, non-GCP, or non-LocalMarket references are found.
                - Deduct from confidence if capital constraint logic appears missing or incorrect.
                - A confidence_score below 0.85 must set hallucination_detected to true.
                """;

        try {
            String evaluation = this.chatClient.prompt()
                    .system(systemPrompt)
                    .user("Evaluate the following agent payload:\n" + payload)
                    .call()
                    .content();

            log.debug("[ObserverAgent] AI evaluation received. Parsing structured response.");

            JsonNode root = objectMapper.readTree(evaluation);
            double confidenceScore = root.path("confidence_score").asDouble(1.0);
            boolean hallucinationDetected = root.path("hallucination_detected").asBoolean(false);
            String reasoning = root.path("reasoning").asText("N/A");
            String originAgent = root.path("origin_agent").asText("unknown");

            if (hallucinationDetected || confidenceScore < CONFIDENCE_THRESHOLD) {
                log.warn("[ObserverAgent] ALERT: Confidence={} for agent='{}'. Reason: {}. " +
                        "Consider context reset per GEMINI.md rules.", confidenceScore, originAgent, reasoning);
            } else {
                log.info("[ObserverAgent] Agent='{}' passed validation. Confidence={}.", originAgent, confidenceScore);
            }

            AgentAuditLog auditRecord = new AgentAuditLog(
                    originAgent,
                    "KafkaEventTrace",
                    evaluation,
                    confidenceScore);
            auditRepository.save(auditRecord);

        } catch (Exception e) {
            log.error(
                    "[ObserverAgent] Failed to parse or evaluate agent payload. Storing raw payload for manual audit.",
                    e);
            auditRepository.save(new AgentAuditLog("unknown", "ParseFailure", payload, 0.0));
        }
    }
}
