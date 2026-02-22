package com.antigravity.agents.system;

import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

@Configuration
public class SystemAgentTools {

    private final PortfolioRepository portfolioRepository;

    // Constant mapped directly from the Phase 1 FRS
    private static final BigDecimal CVAR_THRESHOLD_PERCENTAGE = new BigDecimal("0.10");

    public SystemAgentTools(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public record CapitalVerificationRequest(String userId, BigDecimal requestedAllocation,
            BigDecimal estimatedCvarRisk) {
    }

    public record CapitalVerificationResponse(boolean approved, String message, BigDecimal maxAllowableDrawdown) {
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
            Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserId(request.userId());
            if (portfolioOpt.isEmpty()) {
                return new CapitalVerificationResponse(false, "Portfolio not found for user.", BigDecimal.ZERO);
            }

            Portfolio portfolio = portfolioOpt.get();
            BigDecimal accumulatedProfit = portfolio.getAccumulatedProfit();

            // FRS Rule #5: 10% CVaR threshold of accumulated profit.
            // Protected capital must remain exposed at 0%.
            BigDecimal maxAllowableRisk = accumulatedProfit.multiply(CVAR_THRESHOLD_PERCENTAGE);

            if (maxAllowableRisk.compareTo(BigDecimal.ZERO) <= 0) {
                return new CapitalVerificationResponse(
                        false,
                        "DENIED: Cannot trade. No accumulated profits exist to absorb risk. The initial capital is strictly firewalled.",
                        BigDecimal.ZERO);
            }

            if (request.estimatedCvarRisk().compareTo(maxAllowableRisk) > 0) {
                return new CapitalVerificationResponse(
                        false,
                        "DENIED: Risk (" + request.estimatedCvarRisk() + ") exceeds 10% CVaR threshold of profit ("
                                + maxAllowableRisk + ")",
                        maxAllowableRisk);
            }

            if (request.requestedAllocation().compareTo(portfolio.getTotalCurrentValue()) > 0) {
                return new CapitalVerificationResponse(false, "DENIED: Insufficient total capital.", maxAllowableRisk);
            }

            return new CapitalVerificationResponse(
                    true,
                    "APPROVED: Trade allocation is within bounds. Initial capital firewalled.",
                    maxAllowableRisk);
        };
    }
}
