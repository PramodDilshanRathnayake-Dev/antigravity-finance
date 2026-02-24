package com.antigravity.agents.system;

import com.antigravity.models.ActionStatus;
import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Capital Constraint & System Agent Service Tests")
public class CapitalConstraintTest {

        // =========================================================
        // CapitalVerificationTool Tests (MCP Tool - verifyCapitalConstraint)
        // =========================================================
        @Nested
        @DisplayName("MCP Tool: verifyCapitalConstraint")
        class CapitalVerificationToolTests {

                @Mock
                private PortfolioRepository portfolioRepository;
                private SystemAgentTools systemAgentTools;

                @BeforeEach
                void setUp() {
                        systemAgentTools = new SystemAgentTools(portfolioRepository);
                        ReflectionTestUtils.setField(systemAgentTools, "cvarThresholdPercentage",
                                        new BigDecimal("0.10"));
                }

                @Test
                @DisplayName("DENY trade when portfolio does not exist")
                void testConstraint_PortfolioNotFound_Denied() {
                        when(portfolioRepository.findByUserId("unknown")).thenReturn(Optional.empty());

                        Function<SystemAgentTools.CapitalVerificationRequest, SystemAgentTools.CapitalVerificationResponse> verifier = systemAgentTools
                                        .verifyCapitalConstraint();

                        var response = verifier.apply(new SystemAgentTools.CapitalVerificationRequest(
                                        "unknown", new BigDecimal("500.00"), new BigDecimal("50.00")));

                        assertEquals(ActionStatus.ERROR, response.status());
                        assertTrue(response.message().contains("Portfolio not found"));
                }

                @Test
                @DisplayName("DENY trade when accumulated profit is zero (no trades yet)")
                void testConstraint_WhenNoProfit_TradeDenied() {
                        Portfolio portfolio = new Portfolio("user1", new BigDecimal("10000.00"));
                        when(portfolioRepository.findByUserId("user1")).thenReturn(Optional.of(portfolio));

                        Function<SystemAgentTools.CapitalVerificationRequest, SystemAgentTools.CapitalVerificationResponse> verifier = systemAgentTools
                                        .verifyCapitalConstraint();

                        var response = verifier.apply(new SystemAgentTools.CapitalVerificationRequest(
                                        "user1", new BigDecimal("500.00"), new BigDecimal("50.00")));

                        assertEquals(ActionStatus.DENIED, response.status());
                        assertEquals(BigDecimal.ZERO, response.maxAllowableDrawdown());
                        assertTrue(response.message().contains("initial capital is strictly firewalled"));
                }

                @Test
                @DisplayName("DENY trade when CVaR risk exceeds 10% of profit")
                void testConstraint_RiskExceeds10PercentProfit_TradeDenied() {
                        Portfolio portfolio = new Portfolio("user2", new BigDecimal("10000.00"));
                        portfolio.addProfit(new BigDecimal("2000.00")); // Max risk = 10% of 2000 = 200
                        when(portfolioRepository.findByUserId("user2")).thenReturn(Optional.of(portfolio));

                        Function<SystemAgentTools.CapitalVerificationRequest, SystemAgentTools.CapitalVerificationResponse> verifier = systemAgentTools
                                        .verifyCapitalConstraint();

                        var response = verifier.apply(new SystemAgentTools.CapitalVerificationRequest(
                                        "user2", new BigDecimal("1000.00"), new BigDecimal("300.00"))); // Risk 300 >
                                                                                                        // max 200

                        assertEquals(ActionStatus.DENIED, response.status());
                        assertEquals(0, response.maxAllowableDrawdown().compareTo(new BigDecimal("200.00")));
                }

                @Test
                @DisplayName("DENY trade when allocation exceeds total portfolio value")
                void testConstraint_AllocationExceedsTotalValue_Denied() {
                        Portfolio portfolio = new Portfolio("user3", new BigDecimal("1000.00"));
                        portfolio.addProfit(new BigDecimal("500.00")); // Total = 1500
                        when(portfolioRepository.findByUserId("user3")).thenReturn(Optional.of(portfolio));

                        Function<SystemAgentTools.CapitalVerificationRequest, SystemAgentTools.CapitalVerificationResponse> verifier = systemAgentTools
                                        .verifyCapitalConstraint();

                        var response = verifier.apply(new SystemAgentTools.CapitalVerificationRequest(
                                        "user3", new BigDecimal("5000.00"), new BigDecimal("10.00"))); // Allocation >
                                                                                                       // total

                        assertEquals(ActionStatus.DENIED, response.status());
                        assertTrue(response.message().contains("Insufficient total capital"));
                }

                @Test
                @DisplayName("APPROVE trade when CVaR risk is within 10% profit bound")
                void testConstraint_RiskWithinBounds_TradeApproved() {
                        Portfolio portfolio = new Portfolio("user4", new BigDecimal("10000.00"));
                        portfolio.addProfit(new BigDecimal("5000.00")); // Max risk = 10% of 5000 = 500
                        when(portfolioRepository.findByUserId("user4")).thenReturn(Optional.of(portfolio));

                        Function<SystemAgentTools.CapitalVerificationRequest, SystemAgentTools.CapitalVerificationResponse> verifier = systemAgentTools
                                        .verifyCapitalConstraint();

                        var response = verifier.apply(new SystemAgentTools.CapitalVerificationRequest(
                                        "user4", new BigDecimal("2000.00"), new BigDecimal("150.00"))); // Risk 150 <
                                                                                                        // max 500

                        assertEquals(ActionStatus.SUCCESS, response.status());
                        assertEquals(0, response.maxAllowableDrawdown().compareTo(new BigDecimal("500.00")));
                        assertTrue(response.message().contains("APPROVED"));
                }

                @Test
                @DisplayName("APPROVE trade when CVaR risk is exactly at the 10% threshold boundary")
                void testConstraint_RiskAtExactBoundary_TradeApproved() {
                        Portfolio portfolio = new Portfolio("user5", new BigDecimal("10000.00"));
                        portfolio.addProfit(new BigDecimal("1000.00")); // Max risk = 10% of 1000 = 100
                        when(portfolioRepository.findByUserId("user5")).thenReturn(Optional.of(portfolio));

                        Function<SystemAgentTools.CapitalVerificationRequest, SystemAgentTools.CapitalVerificationResponse> verifier = systemAgentTools
                                        .verifyCapitalConstraint();

                        var response = verifier.apply(new SystemAgentTools.CapitalVerificationRequest(
                                        "user5", new BigDecimal("500.00"), new BigDecimal("100.00"))); // Risk = max
                                                                                                       // exactly

                        assertEquals(ActionStatus.SUCCESS, response.status());
                }
        }

        // =========================================================
        // Portfolio Entity Logic Tests
        // =========================================================
        @Nested
        @DisplayName("Portfolio Entity Business Logic")
        class PortfolioEntityTests {

                @Test
                @DisplayName("Portfolio total value = protected base + accumulated profit")
                void testTotalCurrentValue() {
                        Portfolio p = new Portfolio("u1", new BigDecimal("10000"));
                        p.addProfit(new BigDecimal("2000"));
                        assertEquals(0, p.getTotalCurrentValue().compareTo(new BigDecimal("12000")));
                }

                @Test
                @DisplayName("Deposit adds to protected base, not profit")
                void testDeposit_UpdatesBase() {
                        Portfolio p = new Portfolio("u2", new BigDecimal("5000"));
                        p.addDeposit(new BigDecimal("3000"));
                        assertEquals(0, p.getProtectedCapitalBase().compareTo(new BigDecimal("8000")));
                        assertEquals(0, p.getAccumulatedProfit().compareTo(BigDecimal.ZERO));
                }

                @Test
                @DisplayName("Withdrawal succeeds when profit covers the amount")
                void testWithdrawal_WhenProfitSufficient_Succeeds() {
                        Portfolio p = new Portfolio("u3", new BigDecimal("10000"));
                        p.addProfit(new BigDecimal("3000"));
                        boolean result = p.processWithdrawal(new BigDecimal("1000"));
                        assertTrue(result);
                        assertEquals(0, p.getAccumulatedProfit().compareTo(new BigDecimal("2000")));
                        // Protected capital must remain intact
                        assertEquals(0, p.getProtectedCapitalBase().compareTo(new BigDecimal("10000")));
                }

                @Test
                @DisplayName("Withdrawal denied when it would breach the protected capital base")
                void testWithdrawal_WhenExceedsProfit_Denied() {
                        Portfolio p = new Portfolio("u4", new BigDecimal("10000"));
                        p.addProfit(new BigDecimal("500"));
                        boolean result = p.processWithdrawal(new BigDecimal("1000")); // Tries to pull from base
                        assertFalse(result);
                        // Protected capital and profit remain unchanged
                        assertEquals(0, p.getProtectedCapitalBase().compareTo(new BigDecimal("10000")));
                        assertEquals(0, p.getAccumulatedProfit().compareTo(new BigDecimal("500")));
                }

                @Test
                @DisplayName("Loss only deducts from profit, never base")
                void testDeductLoss_OnlyFromProfit() {
                        Portfolio p = new Portfolio("u5", new BigDecimal("10000"));
                        p.addProfit(new BigDecimal("2000"));
                        p.deductLoss(new BigDecimal("500"));
                        assertEquals(0, p.getAccumulatedProfit().compareTo(new BigDecimal("1500")));
                        assertEquals(0, p.getProtectedCapitalBase().compareTo(new BigDecimal("10000")));
                }
        }

        // =========================================================
        // SystemAgentService Tests
        // =========================================================
        @Nested
        @DisplayName("SystemAgentService Banking Operations")
        class SystemAgentServiceTests {

                @Mock
                private PortfolioRepository portfolioRepository;
                @Mock
                private WebClient.Builder webClientBuilder;
                @Mock
                private WebClient webClient;

                private SystemAgentService systemAgentService;

                @BeforeEach
                void setUp() {
                        when(webClientBuilder.build()).thenReturn(webClient);
                        systemAgentService = new SystemAgentService(portfolioRepository, webClientBuilder);
                        ReflectionTestUtils.setField(systemAgentService, "bankingApiUrl", "http://mock-bank.api");
                }

                @Test
                @DisplayName("Deposit creates new portfolio if none exists")
                void testProcessDeposit_CreatesNewPortfolio() {
                        when(portfolioRepository.findByUserId("newUser")).thenReturn(Optional.empty());
                        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        Portfolio result = systemAgentService.processDeposit("newUser", new BigDecimal("5000"));

                        assertNotNull(result);
                        assertEquals(0, result.getProtectedCapitalBase().compareTo(new BigDecimal("5000")));
                        verify(portfolioRepository).save(any(Portfolio.class));
                }

                @Test
                @DisplayName("Deposit adds to existing portfolio")
                void testProcessDeposit_AddsToExisting() {
                        Portfolio existing = new Portfolio("existingUser", new BigDecimal("1000"));
                        when(portfolioRepository.findByUserId("existingUser")).thenReturn(Optional.of(existing));
                        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        Portfolio result = systemAgentService.processDeposit("existingUser", new BigDecimal("2000"));
                        assertEquals(0, result.getProtectedCapitalBase().compareTo(new BigDecimal("3000")));
                }

                @Test
                @DisplayName("Withdrawal DENIED when portfolio not found")
                void testProcessWithdrawal_PortfolioNotFound_Denied() {
                        when(portfolioRepository.findByUserId("ghost")).thenReturn(Optional.empty());
                        ActionStatus result = systemAgentService.processWithdrawal("ghost", new BigDecimal("100"));
                        assertEquals(ActionStatus.DENIED, result);
                }

                @Test
                @DisplayName("Withdrawal DENIED when amount exceeds profit")
                void testProcessWithdrawal_ExceedsProfit_Denied() {
                        Portfolio portfolio = new Portfolio("richUser", new BigDecimal("10000"));
                        portfolio.addProfit(new BigDecimal("200")); // Only 200 in profit
                        when(portfolioRepository.findByUserId("richUser")).thenReturn(Optional.of(portfolio));

                        ActionStatus result = systemAgentService.processWithdrawal("richUser", new BigDecimal("500"));
                        assertEquals(ActionStatus.DENIED, result);
                        verify(portfolioRepository, never()).save(any());
                }
        }
}
