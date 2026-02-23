package com.antigravity.agents.system;

import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import com.antigravity.models.ActionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

@Configuration
public class SystemAgentTools {

    private static final Logger log = LoggerFactory.getLogger(SystemAgentTools.class);
    private final PortfolioRepository portfolioRepository;

    @Value("${antigravity.risk.cvar-threshold-percentage:0.10}")
    private BigDecimal cvarThresholdPercentage;

    public SystemAgentTools(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public record CapitalVerificationRequest(String userId, BigDecimal requestedAllocation,
            BigDecimal estimatedCvarRisk) {
    }

    public record CapitalVerificationResponse(ActionStatus status, String message, BigDecimal maxAllowableDrawdown) {
    }

    /**
     * MCP Tool utilized by the Trade Agent to strictly verify if it is allowed to
     * execute a trade
     * without compromising the Protected Capital Base.
     */
    @Bean
    @Description("Strictly enforces system capital preservation limits before allowing trade execution. Usage: Call this tool before any BUY action.")
    public Function<CapitalVerificationRequest, CapitalVerificationResponse> verifyCapitalConstraint() {
        return request -> {
            log.info("[SystemAgentService] Processing MCP CapitalVerificationRequest for User: {}", request.userId());
            Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserId(request.userId());
            if (portfolioOpt.isEmpty()) {
                return new CapitalVerificationResponse(ActionStatus.ERROR, "Portfolio not found for user.",
                        BigDecimal.ZERO);
            }

            Portfolio portfolio = portfolioOpt.get();
            BigDecimal accumulatedProfit = portfolio.getAccumulatedProfit();

            // FRS Rule #5: Dynamic CVaR threshold of accumulated profit.
            // Protected capital must remain exposed at 0%.
            BigDecimal maxAllowableRisk = accumulatedProfit.multiply(cvarThresholdPercentage);

            if (maxAllowableRisk.compareTo(BigDecimal.ZERO) <= 0) {
                return new CapitalVerificationResponse(
                        ActionStatus.DENIED,
                        "DENIED: Cannot trade. No accumulated profits exist to absorb risk. The initial capital is strictly firewalled.",
                        BigDecimal.ZERO);
            }

            if (request.estimatedCvarRisk().compareTo(maxAllowableRisk) > 0) {
                return new CapitalVerificationResponse(
                        ActionStatus.DENIED,
                        "DENIED: Risk (" + request.estimatedCvarRisk() + ") exceeds 10% CVaR threshold of profit ("
                                + maxAllowableRisk + ")",
                        maxAllowableRisk);
            }

            if (request.requestedAllocation().compareTo(portfolio.getTotalCurrentValue()) > 0) {
                return new CapitalVerificationResponse(ActionStatus.DENIED, "DENIED: Insufficient total capital.",
                        maxAllowableRisk);
            }

            return new CapitalVerificationResponse(
                    ActionStatus.SUCCESS,
                    "APPROVED: Trade allocation is within bounds. Initial capital firewalled.",
                    maxAllowableRisk);
        };
    }
}
