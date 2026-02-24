package com.antigravity.agents.userfacing;

import com.antigravity.agents.BaseAgent;
import com.antigravity.agents.system.SystemAgentService;
import com.antigravity.models.ActionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
public class UserFacingAgentController extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(UserFacingAgentController.class);
    private final SystemAgentService systemAgentService;

    public UserFacingAgentController(ChatClient.Builder chatClientBuilder, SystemAgentService systemAgentService) {
        super(chatClientBuilder, "UserFacingAgent");
        this.systemAgentService = systemAgentService;
    }

    /**
     * Deposits capital into the portfolio protected base.
     * FRS: protectedCapitalBase is never exposed to trading risk.
     */
    @PostMapping("/deposit")
    public ResponseEntity<Map<String, Object>> handleDeposit(
            @RequestParam String userId,
            @RequestParam BigDecimal amount) {

        log.info("[UserFacingAgent] Deposit request - userId={}, amount={}", userId, amount);
        systemAgentService.processDeposit(userId, amount);

        return ResponseEntity.ok(Map.of(
                "status", ActionStatus.SUCCESS,
                "message", "Capital base securely updated. Protected base increment confirmed."));
    }

    /**
     * Processes a withdrawal strictly from accumulated profit, never the protected
     * capital base.
     * Returns DENIED with an explanation if the request would breach FRS Constraint
     * #1.
     */
    @PostMapping("/withdraw")
    public ResponseEntity<Map<String, Object>> handleWithdrawal(
            @RequestParam String userId,
            @RequestParam BigDecimal amount) {

        log.info("[UserFacingAgent] Withdrawal request - userId={}, amount={}", userId, amount);
        ActionStatus result = systemAgentService.processWithdrawal(userId, amount);

        if (result == ActionStatus.SUCCESS) {
            return ResponseEntity.ok(Map.of(
                    "status", ActionStatus.SUCCESS,
                    "message", "Bank transfer initiated from Accumulated Profit. Protected capital untouched."));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", ActionStatus.DENIED,
                    "message",
                    "Capital Preservation Constraint breach detected. Your initial capital and deposits are firewalled. Withdrawal denied."));
        }
    }

    /**
     * Generative conversational endpoint. The User-Facing Agent translates raw
     * system
     * metrics into trusted, hallucination-filtered conversational text before
     * presenting to the investor.
     */
    @PostMapping("/chat")
    public ResponseEntity<String> converseSystemStatus(@RequestBody String userQuery) {
        log.info("[UserFacingAgent] Chat query received.");

        String systemPrompt = """
                You are the Antigravity User-Facing Trust Agent. Your role is to bridge the investor and the trading system.
                Rules:
                - Translate system events into plain English. No raw DB IDs, Kafka topic names, or technical stack details.
                - Always reassure the user that their Initial Capital and Deposits are protected by a mathematical firewall.
                - If you are unsure, respond with "Let me verify this with the system." Never hallucinate financial data.
                - Do NOT mention Azure, GCP, or any cloud provider other than AWS.
                """;

        String response = this.chatClient.prompt()
                .system(systemPrompt)
                .user(userQuery)
                .call()
                .content();

        return ResponseEntity.ok(response);
    }
}
