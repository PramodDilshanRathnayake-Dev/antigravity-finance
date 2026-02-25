package com.antigravity.agents.trade;

import com.antigravity.models.Trade;
import com.antigravity.models.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TradeAgentTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    private TradeAgent tradeAgent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(chatClientBuilder.defaultFunctions(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        tradeAgent = new TradeAgent(chatClientBuilder, kafkaTemplate, tradeRepository, mock(SandboxBrokerClient.class));
    }

    @Test
    void should_SaveTrade_WhenModelApproves() {
        // Build a JSON that matches TradeDecision fields
        String mockAiResponse = "{\"assetId\":\"CSE:JKH\", \"action\":\"BUY\", \"amountAllocated\":1000.0, \"executionPrice\":150.0, \"strategyUsed\":\"TREND_FOLLOWER\", \"cvarExposure\":10.0}";
        when(responseSpec.content()).thenReturn(mockAiResponse);

        tradeAgent.processMarketEvent("{\"asset_id\":\"CSE:JKH\", \"trend\":\"BULLISH\"}");

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(tradeCaptor.capture());

        Trade saved = tradeCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo("usr_001");
        assertThat(saved.getAssetId()).isEqualTo("CSE:JKH");
        assertThat(saved.getAction()).isEqualTo("BUY");
        assertThat(saved.getAmountAllocated()).isEqualByComparingTo("1000.0");

        verify(kafkaTemplate).send(anyString(), eq(mockAiResponse));
    }

    @Test
    void should_NotSaveTrade_WhenModelDenies() {
        String mockAiResponse = "DENIED: Risk threshold exceeded.";
        when(responseSpec.content()).thenReturn(mockAiResponse);

        tradeAgent.processMarketEvent("{\"asset_id\":\"CSE:JKH\", \"trend\":\"BULLISH\"}");

        verify(tradeRepository, never()).save(any(Trade.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    void should_HandleException_Gracefully() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("AI Down"));

        tradeAgent.processMarketEvent("{\"asset_id\":\"CSE:JKH\", \"trend\":\"BULLISH\"}");

        verify(tradeRepository, never()).save(any(Trade.class));
    }
}
