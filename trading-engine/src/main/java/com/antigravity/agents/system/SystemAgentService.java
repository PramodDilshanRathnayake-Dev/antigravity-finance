package com.antigravity.agents.system;

import com.antigravity.models.ActionStatus;
import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class SystemAgentService {

    private static final Logger log = LoggerFactory.getLogger(SystemAgentService.class);

    private final PortfolioRepository portfolioRepository;

    public SystemAgentService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * D-1/D-2: Manual CDS Deposit Flow.
     * Uses SERIALIZABLE isolation and pessimistic write lock to resolve concurrency
     * bottleneck.
     * This adds to the protected capital base permanently. No Bank API involved.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Portfolio syncCdsDeposit(String userId, BigDecimal amount) {
        log.info("[SystemAgentService] Processing manual CDS deposit sync for userId={}, amount={}", userId, amount);

        Portfolio portfolio = portfolioRepository.findByUserIdForUpdate(userId)
                .orElse(new Portfolio(userId, BigDecimal.ZERO));

        portfolio.addDeposit(amount);
        Portfolio saved = portfolioRepository.save(portfolio);

        log.info("[SystemAgentService] CDS Deposit synced. userId={}, newBase={}", userId,
                saved.getProtectedCapitalBase());
        return saved;
    }

    /**
     * D-1/D-2: Manual Withdrawal Flow (Stock Sell -> CDS Cash).
     * Uses SERIALIZABLE isolation and pessimistic write lock.
     * Enforces `Current_Value(t) >= Initial_Capital + Σ Deposits(t)`.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ActionStatus processWithdrawal(String userId, BigDecimal amount) {
        log.info("[SystemAgentService] Processing stock-sell withdrawal request for userId={}, amount={}", userId,
                amount);

        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserIdForUpdate(userId);
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
        log.info(
                "[SystemAgentService] Stock-sell withdrawal processed. User must now manually transfer from CDS to Bank.");

        // No Bank API call. The user converts stock to CDS cash, then manually
        // transfers out.
        return ActionStatus.SUCCESS;
    }

    /**
     * Helper for UI dashboard to retrieve portfolio state.
     */
    @Transactional(readOnly = true)
    public Optional<Portfolio> getPortfolio(String userId) {
        return portfolioRepository.findByUserId(userId);
    }
}
