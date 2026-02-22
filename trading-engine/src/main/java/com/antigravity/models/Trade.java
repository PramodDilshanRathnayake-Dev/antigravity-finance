package com.antigravity.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String assetId;

    @Column(nullable = false)
    private String action; // BUY, SELL

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amountAllocated;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal executionPrice;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String strategyUsed;

    // Risk threshold used at the moment of trade
    @Column(nullable = false)
    private BigDecimal cvarExposure; 

    public Trade() {}

    public Trade(String assetId, String action, BigDecimal amountAllocated, BigDecimal executionPrice, String strategyUsed, BigDecimal cvarExposure) {
        this.assetId = assetId;
        this.action = action;
        this.amountAllocated = amountAllocated;
        this.executionPrice = executionPrice;
        this.strategyUsed = strategyUsed;
        this.cvarExposure = cvarExposure;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getAssetId() { return assetId; }
    public String getAction() { return action; }
    public BigDecimal getAmountAllocated() { return amountAllocated; }
    public BigDecimal getExecutionPrice() { return executionPrice; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getStrategyUsed() { return strategyUsed; }
    public BigDecimal getCvarExposure() { return cvarExposure; }
}
