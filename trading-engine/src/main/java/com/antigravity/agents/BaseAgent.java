package com.antigravity.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
public abstract class BaseAgent {

    protected final ChatClient chatClient;
    protected final String agentName;

    public BaseAgent(ChatClient.Builder chatClientBuilder, String agentName) {
        this.chatClient = chatClientBuilder.build();
        this.agentName = agentName;
    }

    /**
     * Core execution loop for Agent logic. This intercepts the prompt payload
     * so it can be subsequently audited by the Observer Agent via Kafka/Events
     * later.
     */
    public ChatResponse executeReasoning(Prompt prompt) {
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

        // Future Integration: Ship 'response.getMetadata()' to the Observer Agent via
        // Kafka

        return response;
    }

    public String getAgentName() {
        return agentName;
    }
}
