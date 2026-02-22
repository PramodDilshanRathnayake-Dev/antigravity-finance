package com.antigravity.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_audit_logs")
public class AgentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String agentName; // AnalysisAgent, TradeAgent, etc.

    @Column(nullable = false)
    private String actionType; // KafkaEvent, ToolCall, Rebuttal

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reasoningBody; // The prompt reasoning exported by the Spring AI ChatResponse

    @Column(nullable = false)
    private Double confidenceScore;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public AgentAuditLog() {}

    public AgentAuditLog(String agentName, String actionType, String reasoningBody, Double confidenceScore) {
        this.agentName = agentName;
        this.actionType = actionType;
        this.reasoningBody = reasoningBody;
        this.confidenceScore = confidenceScore;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getAgentName() { return agentName; }
    public String getActionType() { return actionType; }
    public String getReasoningBody() { return reasoningBody; }
    public Double getConfidenceScore() { return confidenceScore; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
