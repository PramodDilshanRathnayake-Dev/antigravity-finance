# Functional Requirements Specification (FRS) & Implementation Plan

> [!NOTE]
> **Context:** I am communicating with you as the **Project Manager Agent**. All technical decisions have been discussed and finalized with the **Architecture Agent**. This FRS acts as the micro-detailed single source of truth. It is purposefully designed to be as simple as possible while covering every decision point to avoid repeat development/ambiguity.

## Goal Description
Develop a secure, autonomous, multi-agent financial investing system using **Java & Spring AI**, deployed on **AWS**. The system will operate across **Crypto and Local Markets**, using **HTTPS and MCP** protocols. It ensures strict capital preservation while maximizing compounding interest, managed by a network of 5 specialized runtime agents.

## User Review Required
> [!IMPORTANT]
> Please review the updated variables and the separate math derivation artifact `math_derivations.md`. Check if the 10% CVaR threshold defined in the constraints aligns with your risk tolerance.

## Proposed System Architecture

### Multi-Agent Runtime Network
1. **User-Facing Agent**: Handles secure user communication, strips technical hallucinations, establishes trust.
2. **System Agent**: Enforces structural limits, system integrity, handles banking API communication and scheduled/dynamic withdrawals.
3. **Analysis Agent**: Ingests API Gateway data to perform technical, fundamental, and sentiment analysis on Crypto and Local markets.
4. **Trade Agent**: Subscribes to analysis events, dynamically selects quantitative strategies, and executes Buy/Sell orders.
5. **Observer Agent**: Meta-monitors the prompt reasoning of all agents, logs data, flags hallucinations, and adjusts context windows via A2A context tuning. Rules from `GEMINI.md` are strictly enforced here.

### Infrastructure Component Stack
- **Core App**: Java 21+, Spring Boot 3.x, Spring AI.
- **Protocols**: HTTPS, MCP (Model Context Protocol), and Realtime Event-Driven protocols.
- **Deployment**: AWS.
- **Monitoring**: Native AWS dashboards + Observer Agent logs.

---

## System Requirements & Mathematical Constraints

### Variable Dictionary
*   **`P(t)`**: Principal amount at time `t`.
*   **`A(t)`**: Total accumulated portfolio value (Amount) at time `t`.
*   **`r_dynamic(t)`**: Dynamic annual interest rate (or expected ROI yield) determined by the Trade Agent at time `t`.
*   **`n`**: Number of compounding periods per year (e.g., 365 for daily, discrete integer for localized events).
*   **`t`**: Time elapsed in years.
*   **`C`**: Total current portfolio capital.
*   **`C_0`**: Total capital inflection point (the portfolio size at which the target ROI starts tapering).
*   **`L`**: Maximum stable yield limit (horizontal asymptote for stable ROI).
*   **`k`**: Logistic growth steepness/decay rate controlling how fast ROI tapers.

### 1. Capital Preservation Requirement
Initial capital and intermediate deposits must *never* be touched by system trading losses, and must be tracked entirely independent of withdrawals.
*   **Equation**: `Current_Value(t) >= Initial_Capital + Σ Deposits(t)`
*   **Implementation**: Withdrawals are processed safely without distorting the tracking of the preserved base. If a user attempts a withdrawal that breaches this constraint (i.e. draining actual protected capital rather than accumulated profit), the system will halt the standard flow and explicitly notify the user that their action breaches the preservation bounds. Overrides are logged.

### 2. Best Compounding Interest Strategies
Dynamic strategies adapting to Crypto and Local markets.
*   **Equation**: `A(t) = P(t) * (1 + r_dynamic(t)/n)^(n*t)`
*   **Implementation**: The **Trade Agent** will dynamically substitute `n`. Derivations for Continuous vs Discrete limits have been moved to `math_derivations.md`.

### 3. ROI Tapering (Market Integrity)
ROI targets grow initially, then gently taper to a stable constant `L` to avoid over-leveraging as capital scales.
*   **Equation**: `ROI_Target(C) = L / (1 + e^(-k(C - C_0)))` 
*   **Implementation**: As portfolio value `C` significantly increases, the mathematical target tapers, meaning risk exposure per trade automatically dynamically shrinks to preserve integrity.

### 4. Dynamic/Scheduled Withdrawals
*   **Equation**: `Withdrawal(t) = min(User_Request, Accumulated_Profit(t) * Risk_Buffer_Ratio)`
*   **Implementation**: Handled by the **System Agent** in accordance with Constraint 1.

### 5. Edge-Case Dominance (VaR, Whales, Red-Team)
*   **Value at Risk (VaR) & CVaR**: System calculated risk bounding. The VaR(90%) and CVaR(99%) must not exceed **10%** of accumulated profit (Fraction: 0.10 of profit). Initial principal remains exposed at 0%.
*   **Whale Dominance**: The **Analysis Agent** implements volume anomaly detection to pause trading during extreme deviation events.
*   **Security/Hallucinations**: The **Observer Agent** will isolate sub-agents if output deviates via `GEMINI.md` hard limits.

## Validations & Stats Plan
1.  **Backtesting Validation**: Run the Trade & Analysis agents on historical Crypto/Local market data to prove Constraint #1 is absolute.
2.  **Monte Carlo Simulations**: Generate 10,000 synthetic market paths to verify the 10% CVaR threshold.
3.  **Red-Team Architecture Review**: Simulate API payload injection.
