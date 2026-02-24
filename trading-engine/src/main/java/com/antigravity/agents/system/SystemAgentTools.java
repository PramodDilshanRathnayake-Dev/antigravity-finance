package com.antigravity.agents.system;

import com.antigravity.models.ActionStatus;
import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

@Configuration
public class SystemAgentTools {

    private static final Logger log = LoggerFactory.getLogger(SystemAgentTools.class);
    private final PortfolioRepository portfolioRepository;
    private final SystemAgentService systemAgentService;

    @Value("${antigravity.risk.cvar-threshold-percentage:0.10}")
    private BigDecimal cvarThresholdPercentage;

    public SystemAgentTools(PortfolioRepository portfolioRepository, SystemAgentService systemAgentService) {
        this.portfolioRepository = portfolioRepository;
        this.systemAgentService = systemAgentService;
    }

    // --- Data Records ---

    public record CapitalVerificationRequest(String userId, BigDecimal requestedAllocation,
            BigDecimal estimatedCvarRisk) {
    }

    public record CapitalVerificationResponse(ActionStatus status, String message, BigDecimal maxAllowableDrawdown) {
    }

    public record CdsDepositRequest(String userId, BigDecimal depositAmount, String brokerReferenceId,
            String depositDate) {
    }

    public record CdsDepositResponse(ActionStatus status, BigDecimal newProtectedCapitalBase, String message) {
    }

    public record StockSellWithdrawalRequest(String userId, BigDecimal withdrawalAmount, String preferredAssetToSell) {
    }

    public record StockSellWithdrawalResponse(ActionStatus status, BigDecimal withdrawalAmount, String message) {
    }

    // --- MCP Tools ---

    /**
     * MCP Tool: VerifyCapitalConstraint
     * READ_COMMITTED isolation. Fully concurrent.
     */
    @Bean
    @Description("Strictly enforces system capital preservation limits before allowing trade execution. Usage: Call this tool before any BUY action.")
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Function<CapitalVerificationRequest, CapitalVerificationResponse> verifyCapitalConstraint() {
        return request -> {
            log.info("[SystemAgentTools] MCP: VerifyCapitalConstraint for User: {}", request.userId());
            Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserId(request.userId());
            if (portfolioOpt.isEmpty()) {
                return new CapitalVerificationResponse(ActionStatus.ERROR, "Portfolio not found for user.",
                        BigDecimal.ZERO);
            }

            Portfolio portfolio = portfolioOpt.get();
            BigDecimal accumulatedProfit = portfolio.getAccumulatedProfit();
            BigDecimal maxAllowableRisk = accumulatedProfit.multiply(cvarThresholdPercentage);

            if (maxAllowableRisk.compareTo(BigDecimal.ZERO) <= 0) {
                return new CapitalVerificationResponse(ActionStatus.DENIED,
                        "DENIED: Cannot trade. No accumulated profits exist to absorb risk. The initial capital is strictly firewalled.",
                        BigDecimal.ZERO);
            }

            if (request.estimatedCvarRisk().compareTo(maxAllowableRisk) > 0) {
                return new CapitalVerificationResponse(ActionStatus.DENIED,
                        "DENIED: Risk (" + request.estimatedCvarRisk() + ") exceeds "
                                + (cvarThresholdPercentage.multiply(new BigDecimal(100)))
                                + "% CVaR threshold of profit (" + maxAllowableRisk + ")",
                        maxAllowableRisk);
            }

            if (request.requestedAllocation().compareTo(portfolio.getTotalCurrentValue()) > 0) {
                return new CapitalVerificationResponse(ActionStatus.DENIED,
                        "DENIED: Insufficient total capital in portfolio.", maxAllowableRisk);
            }

            return new CapitalVerificationResponse(ActionStatus.SUCCESS,
                    "APPROVED: Trade allocation is within bounds. Initial capital firewalled.", maxAllowableRisk);
        };
    }

    /**
     * MCP Tool: SyncCdsDeposit (Replaces RequestBankWithdrawal for incoming
     * capital)
     */
    @Bean
    @Description("Records a manually completed broker deposit into the Portfolio. This increases the protected capital base.")
    public Function<CdsDepositRequest, CdsDepositResponse> syncCdsDeposit() {
        return request -> {
            log.info("[SystemAgentTools] MCP: SyncCdsDeposit for User: {}", request.userId());
            Portfolio p = systemAgentService.syncCdsDeposit(request.userId(), request.depositAmount());
            return new CdsDepositResponse(ActionStatus.SUCCESS, p.getProtectedCapitalBase(),
                    "Deposit recorded. Protected capital base updated.");
        };
    }

    /**
     * MCP Tool: RequestStockSellWithdrawal (Replaces Bank transfer call for
     * outgoing capital)
     */
    @Bean
    @Description("Validates withdrawal amount against accumulated profit. If approved, user must manually sell stocks on CSE.")
    public Function<StockSellWithdrawalRequest, StockSellWithdrawalResponse> requestStockSellWithdrawal() {
        return request -> {
            log.info("[SystemAgentTools] MCP: RequestStockSellWithdrawal for User: {}", request.userId());
            ActionStatus status = systemAgentService.processWithdrawal(request.userId(), request.withdrawalAmount());

            if (status == ActionStatus.SUCCESS) {
                return new StockSellWithdrawalResponse(ActionStatus.SUCCESS, request.withdrawalAmount(),
                        "Withdrawal approved. Sell order required on CSE to convert holdings to CDS cash.");
            } else {
                return new StockSellWithdrawalResponse(ActionStatus.DENIED, request.withdrawalAmount(),
                        "DENIED: Withdrawal exceeds accumulated profits. Capital preservation constraint triggered.");
            }
        };
    }
}
