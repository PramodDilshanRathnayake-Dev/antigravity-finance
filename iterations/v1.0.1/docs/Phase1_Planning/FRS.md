# Functional Requirements Specification (FRS) — v1.0.1
> **Document Version:** v1.0.1
> **Date:** 2026-02-24
> **Status:** Approved by PM Agent — Pending User Sign-off
> **Change Summary from v1.0.0:** Section 4 (Banking Flow) fully revised. Bank API integration removed. Deposit and withdrawal are now entirely manual, brokered through the Colombo Stock Exchange (CSE) CDS (Central Depository System) account. No external Bank API gateway is wired into the system.

---

> [!NOTE]
> **Context:** This FRS is the v1.0.1 single source of truth. It supersedes v1.0.0 in all areas marked as updated. Unchanged sections carry forward from v1.0.0 without modification.

## Goal Description
Develop a secure, autonomous, multi-agent financial investing system using **Java & Spring AI**, deployed on **AWS**. The system operates on **Local Markets (CSE — Colombo Stock Exchange)** in v1, using **HTTPS and MCP** protocols. It ensures strict capital preservation while maximizing compounding interest, managed by a network of 5 specialized runtime agents and a NextJS/React user interface.

---

## Proposed System Architecture

### Multi-Agent Runtime Network
1. **User-Facing Agent**: Handles secure user communication, renders wallet state via the UI, strips technical hallucinations, establishes trust.
2. **System Agent**: Enforces structural limits, system integrity, manages CDS cash balance state, and validates capital constraint before every trade. *(No Bank API. No direct banking calls.)*
3. **Analysis Agent**: Ingests Local Market (CSE) data to perform technical, fundamental, and sentiment analysis.
4. **Trade Agent**: Subscribes to analysis events, dynamically selects quantitative strategies, and executes Buy/Sell orders on the CSE sandbox/broker.
5. **Observer Agent**: Meta-monitors all agent prompt reasoning, logs data, flags hallucinations per `GEMINI.md`.

### Frontend
- **Technology**: NextJS 14 / React 18
- **Components**: Wallet Dashboard (protected capital, deposits, withdrawals, accumulated profit) + Chat Interface (connected to User-Facing Agent).

### Infrastructure Component Stack
- **Core App**: Java 21, Spring Boot 3.4.x, Spring AI 1.0.0-M6
- **Protocols**: HTTPS, MCP (Model Context Protocol), Kafka (event-driven)
- **Deployment**: AWS (EKS, MSK, Aurora PostgreSQL, ElastiCache, API Gateway, Secrets Manager, CloudWatch/X-Ray)
- **Local Development**: Docker Compose (All services + UI)

---

## System Requirements & Mathematical Constraints

### Variable Dictionary
*   **`P(t)`**: Principal amount at time `t`.
*   **`A(t)`**: Total accumulated portfolio value (Amount) at time `t`.
*   **`r_dynamic(t)`**: Dynamic annual interest rate (or expected ROI yield) determined by the Trade Agent at time `t`.
*   **`n`**: Number of compounding periods per year.
*   **`t`**: Time elapsed in years.
*   **`C`**: Total current portfolio capital.
*   **`C_0`**: Total capital inflection point.
*   **`L`**: Maximum stable yield limit (horizontal asymptote for stable ROI).
*   **`k`**: Logistic growth steepness/decay rate.
*   **`CDS_Balance`**: The current investable cash balance held in the user's CDS account at the CSE broker.

### 1. Capital Preservation Requirement *(Unchanged from v1.0.0)*
Initial capital and intermediate deposits must *never* be touched by system trading losses.

- **Equation**: `Current_Value(t) >= Initial_Capital + Σ Deposits(t)`
- **Implementation**: The System Agent reads `protectedCapitalBase` from the Portfolio DB record. Withdrawals are only processed if `Accumulated_Profit >= Withdrawal_Amount`. Protected capital is never reduced.

### 2. Best Compounding Interest Strategies *(Unchanged from v1.0.0)*
- **Equation**: `A(t) = P(t) * (1 + r_dynamic(t)/n)^(n*t)`
- **Implementation**: Trade Agent dynamically selects `n` and strategy per market conditions.

### 3. ROI Tapering (Market Integrity) *(Unchanged from v1.0.0)*
- **Equation**: `ROI_Target(C) = L / (1 + e^(-k(C - C_0)))`

### 4. Capital Flow — Manual CDS Model *(UPDATED from v1.0.0)*

> **Breaking Change from v1.0.0:** The Bank API Gateway integration has been removed entirely. All capital flow is manual and brokered through the CSE CDS account.

#### 4.1 Deposit Flow (Manual — No API)
1. The user transfers funds manually to their broker account via internet banking.
2. The broker credits the user's **CDS cash balance**.
3. The user informs the system via the UI (`POST /api/v1/portfolio/sync-deposit`).
4. The System Agent records the deposit into `protectedCapitalBase` in the Portfolio DB.
5. **No outbound banking API call is made by the system.**

#### 4.2 Withdrawal Flow (Manual — Stock-to-Cash)
1. The user requests a withdrawal via the UI (`POST /api/v1/portfolio/request-withdrawal`).
2. The System Agent validates: `Withdrawal_Amount <= Accumulated_Profit`.
3. If approved, the Trade Agent is instructed to **sell the equivalent value in stocks** on the CSE, converting holdings to CDS cash balance.
4. The user manually initiates a bank transfer from their CDS cash balance via their broker portal.
5. The System Agent logs the withdrawal event and updates `accumulatedProfit`.
6. **No Bank API is called. Bank transfer is manual.**

#### 4.3 CDS Balance Sync
- The Analysis Agent periodically queries the CSE broker API for the real-time CDS cash balance to keep the system's Portfolio DB in sync.
- This is read-only; no write actions are performed via the API.

### 5. Dynamic/Scheduled Withdrawals *(Revised from v1.0.0)*
- **Equation**: `Withdrawal(t) = min(User_Request, Accumulated_Profit(t) * Risk_Buffer_Ratio)`
- **Implementation**: Handled by System Agent per the manual flow above (Constraint 4.2). No Bank API involved.

### 6. Edge-Case Dominance (VaR, Whales, Red-Team) *(Unchanged from v1.0.0)*
- **VaR(90%) & CVaR(99%)**: Must not exceed 10% of accumulated profit.
- **Whale Dominance**: Analysis Agent volume anomaly detection triggers HOLD.
- **Security**: Observer Agent isolates sub-agents on confidence < 0.85.

---

## Validations & Stats Plan *(Updated from v1.0.0)*
1. **Backtesting Validation**: Minimum 5 rounds using CSE historical market data patterns (Normal, Stress, Red-Team, VaR/CVaR breach, Shark/Whale event).
2. **Monte Carlo Simulations**: 10,000 synthetic market paths to verify 10% CVaR threshold.
3. **Red-Team Architecture Review**: Simulate API payload injection, prompt injection, and capital constraint override attempts.
4. **Security Penetration Testing**: OWASP Top 10 + AI-specific attack vectors (indirect prompt injection, model DoS).
