package com.antigravity.agents.system;

import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CapitalConstraintTest {

        @Mock
        private PortfolioRepository portfolioRepository;

        private SystemAgentTools systemAgentTools;

        @BeforeEach
        void setUp() {
                systemAgentTools = new SystemAgentTools(portfolioRepository);
                ReflectionTestUtils.setField(systemAgentTools, "cvarThresholdPercentage", new BigDecimal("0.10"));
        }

        @Test
        void testConstraint_WhenNoProfit_TradeDenied() {
                // Arrange
                String userId = "user1";
                Portfolio portfolio = new Portfolio(userId, new BigDecimal("10000.00")); // Initial Capital = 10,000,
                                                                                         // Profit = 0
                when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

                Function<SystemAgentTools.CapitalVerificationRequest, SystemAgentTools.CapitalVerificationResponse> verifier = systemAgentTools
                                .verifyCapitalConstraint();

                // Act: Trade Agent requests to allocate $500 with $50 CVaR risk
                SystemAgentTools.CapitalVerificationRequest request = new SystemAgentTools.CapitalVerificationRequest(
                                userId, new BigDecimal("500.00"), new BigDecimal("50.00"));

                SystemAgentTools.CapitalVerificationResponse response = verifier.apply(request);

                // Assert
                assertEquals(com.antigravity.models.ActionStatus.DENIED, response.status());
                assertTrue(response.message().contains("DENIED"));
                assertEquals(0, response.maxAllowableDrawdown().compareTo(BigDecimal.ZERO));
        }

        @Test
        void testConstraint_WhenRiskExceeds10PercentProfit_TradeDenied() {
                // Arrange
                String userId = "user2";
                Portfolio portfolio = new Portfolio(userId, new BigDecimal("10000.00"));
                portfolio.addProfit(new BigDecimal("2000.00")); // Profit = 2000. Max risk = 10% of 2000 = 200.
                when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

                Function<SystemAgentTools.CapitalVerificationRequest, SystemAgentTools.CapitalVerificationResponse> verifier = systemAgentTools
                                .verifyCapitalConstraint();

                // Act: Trade Agent requests to allocate $1000 but the CVaR risk is $300
                // (Exceeds max allowable risk of $200)
                SystemAgentTools.CapitalVerificationRequest request = new SystemAgentTools.CapitalVerificationRequest(
                                userId, new BigDecimal("1000.00"), new BigDecimal("300.00"));

                SystemAgentTools.CapitalVerificationResponse response = verifier.apply(request);

                // Assert
                assertEquals(com.antigravity.models.ActionStatus.DENIED, response.status());
                assertTrue(response.message().contains("DENIED: Risk (300.00) exceeds configured CVaR threshold"));
                assertEquals(0, response.maxAllowableDrawdown().compareTo(new BigDecimal("200.00")));
        }

        @Test
        void testConstraint_WhenRiskWithinBounds_TradeApproved() {
                // Arrange
                String userId = "user3";
                Portfolio portfolio = new Portfolio(userId, new BigDecimal("10000.00"));
                portfolio.addProfit(new BigDecimal("5000.00")); // Profit = 5000. Max risk = 10% of 5000 = 500.
                when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

                Function<SystemAgentTools.CapitalVerificationRequest, SystemAgentTools.CapitalVerificationResponse> verifier = systemAgentTools
                                .verifyCapitalConstraint();

                // Act: Trade Agent requests to allocate $2000 with a CVaR risk of $150 (Within
                // $500 bound)
                SystemAgentTools.CapitalVerificationRequest request = new SystemAgentTools.CapitalVerificationRequest(
                                userId, new BigDecimal("2000.00"), new BigDecimal("150.00"));

                SystemAgentTools.CapitalVerificationResponse response = verifier.apply(request);

                // Assert
                assertEquals(com.antigravity.models.ActionStatus.SUCCESS, response.status());
                assertTrue(response.message().contains("APPROVED"));
                assertEquals(0, response.maxAllowableDrawdown().compareTo(new BigDecimal("500.00")));
        }
}
