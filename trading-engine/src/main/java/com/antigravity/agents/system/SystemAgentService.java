package com.antigravity.agents.system;

import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import com.antigravity.models.ActionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
public class SystemAgentService {

    private static final Logger log = LoggerFactory.getLogger(SystemAgentService.class);

    private final PortfolioRepository portfolioRepository;
    private final WebClient webClient;

    @Value("${antigravity.api.banking.url}")
    private String bankingApiUrl;

    public SystemAgentService(PortfolioRepository portfolioRepository, WebClient.Builder webClientBuilder) {
        this.portfolioRepository = portfolioRepository;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Processes a secure deposit by adding to the protected capital base.
     * The initial capital and all deposits are permanently firewall-protected per
     * FRS Constraint #1.
     */
    @Transactional
    public Portfolio processDeposit(String userId, BigDecimal amount) {
        log.info("[SystemAgentService] Processing deposit for userId={}, amount={}", userId, amount);

        Portfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElse(new Portfolio(userId, BigDecimal.ZERO));

        portfolio.addDeposit(amount);
        Portfolio saved = portfolioRepository.save(portfolio);

        log.info("[SystemAgentService] Deposit complete. userId={}, newBase={}", userId,
                saved.getProtectedCapitalBase());
        return saved;
    }

    /**
     * Processes a withdrawal request against accumulated profit ONLY.
     * Enforces `Current_Value(t) >= Initial_Capital + Σ Deposits(t)` by strictly
     * denying any withdrawal that would touch the protected capital base.
     * On approval, triggers the configured Banking API endpoint via WebClient.
     */
    @Transactional
    public ActionStatus processWithdrawal(String userId, BigDecimal amount) {
        log.info("[SystemAgentService] Processing withdrawal request for userId={}, amount={}", userId, amount);

        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserId(userId);
        if (portfolioOpt.isEmpty()) {
            log.warn("[SystemAgentService] DENIED - Portfolio not found for userId={}", userId);
            return ActionStatus.DENIED;
        }

        Portfolio portfolio = portfolioOpt.get();
        boolean canWithdraw = portfolio.processWithdrawal(amount);

        if (!canWithdraw) {
            log.warn("[SystemAgentService] DENIED - Withdrawal breaches FRS capital preservation for userId={}",
                    userId);
            return ActionStatus.DENIED;
        }

        portfolioRepository.save(portfolio);
        log.info("[SystemAgentService] Portfolio updated. Calling Banking API at {}", bankingApiUrl);

        // Async HTTP call to configured Banking API Gateway
        webClient.post()
                .uri(bankingApiUrl + "/transfer")
                .bodyValue(Map.of("userId", userId, "amount", amount.toPlainString()))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("[SystemAgentService] Banking API transfer acknowledged. userId={}", userId))
                .doOnError(e -> log.error("[SystemAgentService] Banking API transfer failed for userId={}: {}", userId,
                        e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();

        return ActionStatus.SUCCESS;
    }
}
