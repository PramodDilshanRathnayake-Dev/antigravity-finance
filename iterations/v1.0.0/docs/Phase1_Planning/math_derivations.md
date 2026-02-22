# Mathematical Derivations for Compounding Strategies

This document acts as an appendix to the Functional Requirements Specification (FRS). It isolates the derivations to keep the main specification simple, serving as a clean reference for the Developer & Architecture Agents.

## 1. Discrete to Continuous Compounding Derivation

### Base Equation (Discrete Compounding)
For an investment compounded `n` times per year at an annual rate `r`:

**`A(t) = P(t) * (1 + r/n)^(n*t)`**

*Where:*
*   `A(t)` = Final Amount (Total value)
*   `P(t)` = Principal Capital
*   `r`    = Annual interest rate (or expected yield)
*   `n`    = Number of compounding periods per year
*   `t`    = Time in years

### Application in Crypto (Continuous Compounding Limit)
Due to the 24/7, high-frequency nature of cryptocurrency markets, capital is constantly repositioned. This mathematically pushes `n` towards infinity. We take the limit of the discrete equation as `n → ∞`:

**`lim (n→∞) P * (1 + r/n)^(n*t) = P * e^(r*t)`**

### Strategy Implications for the Trade Agent

1.  **Crypto Market Modeller**: The Trade Agent incorporates **Continuous Compounding**: `A = P * e^(r*t)` for real-time, high-frequency strategy viability mapping.
2.  **Local Market Modeller**: The Trade Agent incorporates the **Discrete Compounding** form `A = P * (1 + r/n)^(n*t)`. Here, `n` aligns with real-world limitations: limited market hours, dividend payout schedules, or specific swing trading cycles. 
