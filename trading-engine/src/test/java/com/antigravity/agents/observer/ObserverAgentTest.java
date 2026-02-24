package com.antigravity.agents.observer;

import com.antigravity.models.AgentAuditLog;
import com.antigravity.models.AgentAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ObserverAgentTest {

    @Mock
    private AgentAuditLogRepository auditRepository;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    private ObserverAgent observerAgent;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        // Mocking the chain: chatClient.prompt().system().user().call().content()
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        observerAgent = new ObserverAgent(chatClientBuilder, auditRepository, objectMapper);
    }

    @Test
    void should_LogAudit_WhenPayloadIsValid() {
        String mockAiResponse = "{\"confidence_score\": 0.95, \"hallucination_detected\": false, \"reasoning\": \"Clean\", \"origin_agent\": \"TradeAgent\"}";
        when(responseSpec.content()).thenReturn(mockAiResponse);

        observerAgent.monitorAgentActivities("test payload");

        ArgumentCaptor<AgentAuditLog> captor = ArgumentCaptor.forClass(AgentAuditLog.class);
        verify(auditRepository).save(captor.capture());

        AgentAuditLog savedLog = captor.getValue();
        assertThat(savedLog.getAgentName()).isEqualTo("TradeAgent");
        assertThat(savedLog.getConfidenceScore()).isEqualTo(0.95);
        assertThat(savedLog.getActionType()).isEqualTo("KafkaEventTrace");
    }

    @Test
    void should_HandleParseFailure_Gracefully() {
        when(responseSpec.content()).thenReturn("invalid json");

        observerAgent.monitorAgentActivities("test payload");

        ArgumentCaptor<AgentAuditLog> captor = ArgumentCaptor.forClass(AgentAuditLog.class);
        verify(auditRepository).save(captor.capture());

        AgentAuditLog savedLog = captor.getValue();
        assertThat(savedLog.getAgentName()).isEqualTo("unknown");
        assertThat(savedLog.getActionType()).isEqualTo("ParseFailure");
        assertThat(savedLog.getConfidenceScore()).isEqualTo(0.0);
    }

    @Test
    void should_LogAlert_WhenConfidenceIsLow() {
        String mockAiResponse = "{\"confidence_score\": 0.5, \"hallucination_detected\": true, \"reasoning\": \"Azure found\", \"origin_agent\": \"TradeAgent\"}";
        when(responseSpec.content()).thenReturn(mockAiResponse);

        observerAgent.monitorAgentActivities("test payload with azure");

        verify(auditRepository).save(any(AgentAuditLog.class));
    }
}
