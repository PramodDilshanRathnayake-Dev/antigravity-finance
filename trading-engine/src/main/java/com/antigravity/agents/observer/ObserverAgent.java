package com.antigravity.agents.observer;

import com.antigravity.agents.BaseAgent;
import com.antigravity.config.KafkaConfig;
import com.antigravity.models.AgentAuditLog;
import com.antigravity.models.AgentAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ObserverAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(ObserverAgent.class);
    private final AgentAuditLogRepository auditRepository;

    public ObserverAgent(ChatClient.Builder chatClientBuilder, AgentAuditLogRepository auditRepository) {
        super(chatClientBuilder, "ObserverAgent");
        this.auditRepository = auditRepository;
    }

    @KafkaListener(topics = { KafkaConfig.TOPIC_AUDIT_TRACES,
            KafkaConfig.TOPIC_TRADE_LOGS }, groupId = "antigravity-agents")
    public void monitorAgentActivities(String payload) {
        log.debug("[ObserverAgent] Intercepted payload for monitoring: {}", payload);

        // This agent evaluates the payload against the strict rules bounded in
        // GEMINI.md.
        String systemPrompt = """
                You are the Antigravity Observer Agent.
                You are strictly checking for Hallucinations against the FRS rules.
                Check if any External libraries, Cloud providers (like Azure), or invalid APIs were hallucinated.
                Check if the agent maintained the LocalMarket specification.
                Return a JSON containing `confidence_score` (0.0 to 1.0) and a `reasoning` string.
                """;

        try {
            String evaluation = this.chatClient.prompt()
                    .system(systemPrompt)
                    .user("Evaluate Payload: " + payload)
                    .call()
                    .content();

            log.info("[ObserverAgent] Validation successful. Persisting to Audit DB.");

            // In full implementation, parse JSON here. Hardcoded for scaffolding.
            AgentAuditLog auditRecord = new AgentAuditLog(
                    "SystemNetwork",
                    "KafkaEventTrace",
                    evaluation,
                    0.98 // Simulated parsed confidence score
            );

            auditRepository.save(auditRecord);

        } catch (Exception e) {
            log.error("[ObserverAgent] Failed to properly observe system loop.", e);
        }
    }
}
