package com.antigravity.config;

import com.antigravity.models.Portfolio;
import com.antigravity.models.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final PortfolioRepository portfolioRepository;

    public DataInitializer(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @Override
    public void run(String... args) {
        String userId = "usr_001";
        if (portfolioRepository.findByUserId(userId).isEmpty()) {
            log.info("Initializing sandbox simulation data for user: {}", userId);
            // 100,000 is the protected base requested by user.
            // 5,000 is seed profit to allow the system to start trading without violating
            // CVaR on 0 profit.
            Portfolio portfolio = new Portfolio(userId, new BigDecimal("100000.00"));
            portfolio.addProfit(new BigDecimal("5000.00"));
            portfolioRepository.save(portfolio);
            log.info("Initial capital of LKR 100,000.00 and seed profit of LKR 5,000.00 established.");
        } else {
            log.info("Portfolio for {} already exists. Skipping initialization.", userId);
        }
    }
}
