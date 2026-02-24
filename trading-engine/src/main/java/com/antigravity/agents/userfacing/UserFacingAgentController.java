package com.antigravity.agents.userfacing;

import com.antigravity.agents.BaseAgent;
import com.antigravity.agents.system.SystemAgentService;
import com.antigravity.models.ActionStatus;
import com.antigravity.models.Portfolio;
import com.antigravity.models.Trade;
import com.antigravity.models.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*") // Allow NextJS UI to connect
public class UserFacingAgentController extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(UserFacingAgentController.class);
    private final SystemAgentService systemAgentService;
    private final TradeRepository tradeRepository;

    public UserFacingAgentController(ChatClient.Builder chatClientBuilder, SystemAgentService systemAgentService,
            TradeRepository tradeRepository) {
        super(chatClientBuilder, "UserFacingAgent");
        this.systemAgentService = systemAgentService;
        this.tradeRepository = tradeRepository;
    }

    // --- UI Dashboard Endpoints ---

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<Portfolio> getPortfolio(@PathVariable String userId) {
        Optional<Portfolio> portfolioOpt = systemAgentService.getPortfolio(userId);
        return portfolioOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/portfolio/{userId}/withdrawals")
    public ResponseEntity<Map<String, Object>> getWithdrawalHistory(@PathVariable String userId) {
        Optional<Portfolio> portfolioOpt = systemAgentService.getPortfolio(userId);
        // Note: Returning total withdrawals count/amount for v1 Dashboard.
        // Can be expanded to return list of actual withdrawal records later.
        return portfolioOpt
                .map(p -> ResponseEntity.ok(Map.<String, Object>of("totalWithdrawals", p.getTotalWithdrawals())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/trades/{userId}")
    public ResponseEntity<List<Trade>> getRecentTrades(@PathVariable String userId) {
        List<Trade> trades = tradeRepository.findByUserIdOrderByTimestampDesc(userId);
        return ResponseEntity.ok(trades);
    }

    // --- Capital Flow Endpoints (Manual CDS) ---

    @PostMapping("/portfolio/sync-deposit")
    public ResponseEntity<Map<String, Object>> syncDeposit(
            @RequestParam String userId,
            @RequestParam BigDecimal amount) {

        log.info("[UserFacingAgent] UI CDS Deposit sync request - userId={}, amount={}", userId, amount);
        Portfolio updated = systemAgentService.syncCdsDeposit(userId, amount);

        return ResponseEntity.ok(Map.of(
                "status", ActionStatus.SUCCESS,
                "newProtectedCapitalBase", updated.getProtectedCapitalBase(),
                "message", "Deposit synced. Protected capital updated."));
    }

    @PostMapping("/portfolio/request-withdrawal")
    public ResponseEntity<Map<String, Object>> requestWithdrawal(
            @RequestParam String userId,
            @RequestParam BigDecimal amount) {

        log.info("[UserFacingAgent] UI Withdrawal request (stock sell) - userId={}, amount={}", userId, amount);
        ActionStatus result = systemAgentService.processWithdrawal(userId, amount);

        if (result == ActionStatus.SUCCESS) {
            return ResponseEntity.ok(Map.of(
                    "status", ActionStatus.SUCCESS,
                    "message", "Sell order initiated. Once settled, transfer cash from CDS manually."));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", ActionStatus.DENIED,
                    "message", "Withdrawal denied. Breaches capital preservation constraint."));
        }
    }

    // --- Chat Endpoint ---

    @PostMapping("/agent/chat")
    public ResponseEntity<String> converseSystemStatus(@RequestBody String userQuery) {
        log.info("[UserFacingAgent] Chat query received.");

        String systemPrompt = """
                You are the Antigravity User-Facing Trust Agent. Your role is to bridge the investor and the trading system.
                Rules:
                - The system utilizes a manual CDS banking flow. Deposits and Bank transfers are handled at the broker level.
                - Translate system events into plain English. No raw DB IDs, Kafka topic names, or technical stack details.
                - Always reassure the user that their Initial Capital and Deposits are protected by a mathematical firewall.
                - If you are unsure, respond with "Let me verify this with the system." Never hallucinate financial data.
                - Do NOT mention Azure, AWS, or any cloud provider other than GCP.
                """;

        String response = this.chatClient.prompt()
                .system(systemPrompt)
                .user(userQuery)
                .call()
                .content();

        return ResponseEntity.ok(response);
    }
}
