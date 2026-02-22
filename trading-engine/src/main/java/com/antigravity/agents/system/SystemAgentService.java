package com.antigravity.agents.system;

import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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
     * Mocks a Bank API deposit gateway
     */
    @Transactional
    public Portfolio processDeposit(String userId, BigDecimal amount) {
        log.info("[SystemAgentService] Processing deposit for {}, Amount: {}", userId, amount);

        Portfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElse(new Portfolio(userId, BigDecimal.ZERO));

        portfolio.addDeposit(amount);
        return portfolioRepository.save(portfolio);
    }

    /**
     * Mocks a Bank API withdrawal gateway.
     * Enforces the `Current_Value(t) >= Initial_Capital + Σ Deposits(t)` rule
     * organically
     * by calling `portfolio.processWithdrawal` which denies taking from protected
     * base.
     */
    @Transactional
    public boolean processWithdrawal(String userId, BigDecimal amount) {
        log.info("[SystemAgentService] Processing withdrawal request for {}, Amount: {}", userId, amount);

        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserId(userId);
        if (portfolioOpt.isEmpty()) {
            log.warn("[SystemAgentService] Denied. Portfolio not found.");
            return false;
        }

        Portfolio portfolio = portfolioOpt.get();
        boolean success = portfolio.processWithdrawal(amount);

        if (success) {
            portfolioRepository.save(portfolio);
            log.info("[SystemAgentService] Withdrawal Approved. FRS Constraints maintained.");
            // Call HTTP Bank Gateway here in production
        } else {
            log.warn(
                    "[SystemAgentService] Withdrawal Denied. Action would have breached initial capital integrity limits.");
        }

        return success;
    }
}
