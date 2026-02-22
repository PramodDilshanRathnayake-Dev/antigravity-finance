package com.antigravity.agents.userfacing;

import com.antigravity.agents.BaseAgent;
import com.antigravity.agents.system.SystemAgentService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
public class UserFacingAgentController extends BaseAgent {

    private final SystemAgentService systemAgentService;

    public UserFacingAgentController(ChatClient.Builder chatClientBuilder, SystemAgentService systemAgentService) {
        super(chatClientBuilder, "UserFacingAgent");
        this.systemAgentService = systemAgentService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> handleDeposit(@RequestParam String userId, @RequestParam BigDecimal amount) {
        systemAgentService.processDeposit(userId, amount);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Capital base securely updated."));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> handleWithdrawal(@RequestParam String userId, @RequestParam BigDecimal amount) {
        boolean success = systemAgentService.processWithdrawal(userId, amount);
        if (success) {
            return ResponseEntity
                    .ok(Map.of("status", "SUCCESS", "message", "Bank transfer initiated from Accumulated Profit."));
        } else {
            return ResponseEntity.badRequest().body(Map.of("status", "DENIED", "message",
                    "Capital Preservation Constraint breach. Unable to withdraw protected initial capital."));
        }
    }

    /**
     * The primary generative endpoint where the user queries system status.
     * The User-Facing agent translates raw system metrics into trusted,
     * non-hallucinated conversational text.
     */
    @PostMapping("/chat")
    public String converseSystemStatus(@RequestBody String userQuery) {
        String systemPrompt = """
                You are the Antigravity User-Facing Agent. Your goal is to establish trust with the human investor.
                Strip away raw technical DB IDs, Kafka logs, or excessive AI terminologies.
                Assure them that their Initial Capital is fundamentally cryptographically separated from trade risk bounds per the FRS constraints.
                """;

        return this.chatClient.prompt()
                .system(systemPrompt)
                .user(userQuery)
                .call()
                .content();
    }
}
