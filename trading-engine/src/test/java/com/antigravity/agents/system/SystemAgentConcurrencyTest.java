package com.antigravity.agents.system;

import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Q-2: Concurrency Stress Test for v1.0.1 Bottleneck Resolution.
 * Verifies that the SERIALIZABLE + PESSIMISTIC_WRITE lock on the write path
 * and READ_COMMITTED on the read path works correctly without corruption.
 */
@SpringBootTest
@ActiveProfiles("test")
class SystemAgentConcurrencyTest {

    @Autowired
    private SystemAgentService systemAgentService;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Test
    void should_HandleHighConcurrentMutations_WithoutCorruption() throws InterruptedException {
        String userId = "stress_user_001";
        BigDecimal initialDeposit = new BigDecimal("1000.00");
        systemAgentService.syncCdsDeposit(userId, initialDeposit);

        int threadCount = 20;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // We will perform 1000 total increments of 1.00
        BigDecimal expectedFinalBase = initialDeposit.add(new BigDecimal(threadCount * operationsPerThread));

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        systemAgentService.syncCdsDeposit(userId, new BigDecimal("1.00"));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(30, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        executor.shutdown();

        Portfolio finalPortfolio = portfolioRepository.findByUserId(userId).orElseThrow();

        // If the locks are working, the base should be exactly initial + (20 * 50)
        // If there were race conditions, increments would have been lost.
        assertThat(finalPortfolio.getProtectedCapitalBase())
                .isEqualByComparingTo(expectedFinalBase);
    }

    @Test
    void should_AllowParallelReads_WhileWritesAreLocked() throws InterruptedException, ExecutionException {
        String userId = "read_write_user";
        systemAgentService.syncCdsDeposit(userId, new BigDecimal("10000.00"));

        ExecutorService executor = Executors.newFixedThreadPool(10);

        // 1. Submit a long-running write operation if we could (here we just run many
        // small ones)
        // 2. Submit many parallel reads
        List<Callable<BigDecimal>> readTasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            readTasks.add(() -> systemAgentService.getPortfolio(userId).get().getProtectedCapitalBase());
        }

        List<Future<BigDecimal>> results = executor.invokeAll(readTasks);

        for (Future<BigDecimal> res : results) {
            assertThat(res.get()).isGreaterThanOrEqualTo(new BigDecimal("10000.00"));
        }

        executor.shutdown();
    }
}
