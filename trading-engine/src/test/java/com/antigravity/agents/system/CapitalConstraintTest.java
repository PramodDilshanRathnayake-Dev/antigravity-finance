package com.antigravity.agents.system;

import com.antigravity.models.ActionStatus;
import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Validates the core capital preservation logic enforcing FRS Constraint #1.
 * Tests cover the Entity, the Service, and the MCP Tool.
 * Target: >80% code coverage for the System Agent module.
 */
class CapitalConstraintTest {

        @Mock
        private PortfolioRepository portfolioRepository;

        @Mock
        private SystemAgentService systemAgentService;

        private SystemAgentTools systemAgentTools;

        @BeforeEach
        void setUp() {
                MockitoAnnotations.openMocks(this);
                // Inject with manual threshold for testing (e.g., 0.10)
                systemAgentTools = new SystemAgentTools(portfolioRepository, systemAgentService);
                try {
                        var field = SystemAgentTools.class.getDeclaredField("cvarThresholdPercentage");
                        field.setAccessible(true);
                        field.set(systemAgentTools, new BigDecimal("0.10"));
                } catch (Exception e) {
                        throw new RuntimeException("Failed to set cvarThresholdPercentage via reflection", e);
                }
        }

        @Nested
        class CapitalVerificationToolTests {

                String userId = "usr_999";
                SystemAgentTools.CapitalVerificationRequest req;
                SystemAgentTools.CapitalVerificationResponse res;

                @Test
                void should_Error_WhenPortfolioNotFound() {
                        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.empty());

                        req = new SystemAgentTools.CapitalVerificationRequest(userId, new BigDecimal("100"),
                                        new BigDecimal("10"));
                        res = systemAgentTools.verifyCapitalConstraint().apply(req);

                        assertThat(res.status()).isEqualTo(ActionStatus.ERROR);
                        assertThat(res.message()).containsSequence("Portfolio not found for user.");
                }

                @Test
                void should_Deny_WhenNoProfitToAbsorbRisk() {
                        Portfolio p = new Portfolio(userId, new BigDecimal("10000")); // Base only, 0 profit
                        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(p));

                        req = new SystemAgentTools.CapitalVerificationRequest(userId, new BigDecimal("500"),
                                        new BigDecimal("50"));
                        res = systemAgentTools.verifyCapitalConstraint().apply(req);

                        assertThat(res.status()).isEqualTo(ActionStatus.DENIED);
                        assertThat(res.message()).containsSequence("No accumulated profits exist");
                }

                @Test
                void should_Deny_WhenRiskExceedsThreshold() {
                        Portfolio p = new Portfolio(userId, new BigDecimal("10000"));
                        p.addProfit(new BigDecimal("1000")); // Max allowable risk = 100
                        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(p));

                        req = new SystemAgentTools.CapitalVerificationRequest(userId, new BigDecimal("500"),
                                        new BigDecimal("150")); // Risk 150 > 100
                        res = systemAgentTools.verifyCapitalConstraint().apply(req);

                        assertThat(res.status()).isEqualTo(ActionStatus.DENIED);
                        assertThat(res.message()).containsSequence("exceeds 10.00% CVaR threshold");
                }

                @Test
                void should_Deny_WhenAllocationExceedsTotalCapital() {
                        Portfolio p = new Portfolio(userId, new BigDecimal("10000"));
                        p.addProfit(new BigDecimal("1000")); // Total = 11000. Max risk = 100.
                        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(p));

                        req = new SystemAgentTools.CapitalVerificationRequest(userId, new BigDecimal("12000"),
                                        new BigDecimal("50"));
                        res = systemAgentTools.verifyCapitalConstraint().apply(req);

                        assertThat(res.status()).isEqualTo(ActionStatus.DENIED);
                        assertThat(res.message()).containsSequence("Insufficient total capital in portfolio");
                }

                @Test
                void should_Approve_WhenWithinBounds() {
                        Portfolio p = new Portfolio(userId, new BigDecimal("10000"));
                        p.addProfit(new BigDecimal("1000")); // Total = 11000. Max risk = 100.
                        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(p));

                        req = new SystemAgentTools.CapitalVerificationRequest(userId, new BigDecimal("2000"),
                                        new BigDecimal("50")); // Both within bounds
                        res = systemAgentTools.verifyCapitalConstraint().apply(req);

                        assertThat(res.status()).isEqualTo(ActionStatus.SUCCESS);
                        assertThat(res.message()).containsSequence("APPROVED");
                }

                @Test
                void should_Approve_WhenRiskExactlyAtBoundary() {
                        Portfolio p = new Portfolio(userId, new BigDecimal("10000"));
                        p.addProfit(new BigDecimal("1000")); // Total = 11000. Max risk = 100.
                        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(p));

                        req = new SystemAgentTools.CapitalVerificationRequest(userId, new BigDecimal("2000"),
                                        new BigDecimal("100.00")); // Exactly boundary
                        res = systemAgentTools.verifyCapitalConstraint().apply(req);

                        assertThat(res.status()).isEqualTo(ActionStatus.SUCCESS);
                }
        }

        @Nested
        class PortfolioEntityTests {
                @Test
                void totalCurrentValue_ShouldSumBaseAndProfit() {
                        Portfolio p = new Portfolio("u1", new BigDecimal("1000"));
                        p.addProfit(new BigDecimal("500"));
                        assertThat(p.getTotalCurrentValue()).isEqualByComparingTo(new BigDecimal("1500"));
                }

                @Test
                void addDeposit_ShouldIncreaseProtectedBase() {
                        Portfolio p = new Portfolio("u1", new BigDecimal("1000"));
                        p.addDeposit(new BigDecimal("500"));
                        assertThat(p.getProtectedCapitalBase()).isEqualByComparingTo(new BigDecimal("1500"));
                        // Profit remains 0
                        assertThat(p.getAccumulatedProfit()).isEqualByComparingTo(BigDecimal.ZERO);
                }

                @Test
                void processWithdrawal_ShouldDenyIfExceedsProfit() {
                        Portfolio p = new Portfolio("u1", new BigDecimal("1000"));
                        p.addProfit(new BigDecimal("500"));

                        boolean allowed = p.processWithdrawal(new BigDecimal("600"));
                        assertThat(allowed).isFalse();
                        assertThat(p.getAccumulatedProfit()).isEqualByComparingTo(new BigDecimal("500")); // Unchanged
                }

                @Test
                void processWithdrawal_ShouldApproveAndDeductFromProfit() {
                        Portfolio p = new Portfolio("u1", new BigDecimal("1000"));
                        p.addProfit(new BigDecimal("500"));

                        boolean allowed = p.processWithdrawal(new BigDecimal("200"));
                        assertThat(allowed).isTrue();
                        assertThat(p.getAccumulatedProfit()).isEqualByComparingTo(new BigDecimal("300")); // Deducted
                        assertThat(p.getProtectedCapitalBase()).isEqualByComparingTo(new BigDecimal("1000")); // Base
                                                                                                              // safe
                }

                @Test
                void deductLoss_ShouldDeductFromProfitOnly() {
                        Portfolio p = new Portfolio("u1", new BigDecimal("1000"));
                        p.addProfit(new BigDecimal("500"));

                        p.deductLoss(new BigDecimal("100"));
                        assertThat(p.getAccumulatedProfit()).isEqualByComparingTo(new BigDecimal("400"));
                        assertThat(p.getProtectedCapitalBase()).isEqualByComparingTo(new BigDecimal("1000"));
                }
        }

        @Nested
        class SystemAgentServiceTests {

                @Mock
                private PortfolioRepository localRepo;

                @InjectMocks
                private SystemAgentService service;

                @BeforeEach
                void setup() {
                        MockitoAnnotations.openMocks(this);
                }

                @Test
                void syncCdsDeposit_ShouldCreateNewPortfolioIfNotFound() {
                        when(localRepo.findByUserIdForUpdate("u99")).thenReturn(Optional.empty());
                        when(localRepo.save(any(Portfolio.class))).thenAnswer(i -> i.getArguments()[0]);

                        Portfolio p = service.syncCdsDeposit("u99", new BigDecimal("500"));

                        assertThat(p.getProtectedCapitalBase()).isEqualByComparingTo(new BigDecimal("500"));
                        verify(localRepo).save(any(Portfolio.class));
                }

                @Test
                void syncCdsDeposit_ShouldAddToExistingPortfolio() {
                        Portfolio existing = new Portfolio("u99", new BigDecimal("1000"));
                        when(localRepo.findByUserIdForUpdate("u99")).thenReturn(Optional.of(existing));
                        when(localRepo.save(any(Portfolio.class))).thenAnswer(i -> i.getArguments()[0]);

                        Portfolio p = service.syncCdsDeposit("u99", new BigDecimal("200"));

                        assertThat(p.getProtectedCapitalBase()).isEqualByComparingTo(new BigDecimal("1200"));
                        verify(localRepo).save(existing);
                }

                @Test
                void processWithdrawal_ShouldDenyIfNotFound() {
                        when(localRepo.findByUserIdForUpdate("u99")).thenReturn(Optional.empty());
                        ActionStatus status = service.processWithdrawal("u99", new BigDecimal("100"));
                        assertThat(status).isEqualTo(ActionStatus.DENIED);
                }

                @Test
                void processWithdrawal_ShouldApproveIfSufficientProfit() {
                        Portfolio existing = new Portfolio("u99", new BigDecimal("1000"));
                        existing.addProfit(new BigDecimal("500"));
                        when(localRepo.findByUserIdForUpdate("u99")).thenReturn(Optional.of(existing));

                        ActionStatus status = service.processWithdrawal("u99", new BigDecimal("400"));

                        assertThat(status).isEqualTo(ActionStatus.SUCCESS);
                        assertThat(existing.getAccumulatedProfit()).isEqualByComparingTo(new BigDecimal("100"));
                        verify(localRepo).save(existing);
                }
        }
}
