# Software Feature Document (SFD) — Antigravity Trading System v1.0.1
> **Document Version:** v1.0.1
> **Date:** 2026-02-24
> **Author:** Developer Agent (v1.0.1)

---

## 1. System Overview

Antigravity v1.0.1 is an autonomous trading platform designed for the Colombo Stock Exchange (CSE). It provides a secure, multi-agent execution environment where the investor's principal capital is firewalled from trading losses.

### 1.1 Use-Case Diagram (Logical)

```text
+----------------+       +---------------------------------------------+
|                |       |          Antigravity Trading System         |
|    Investor    |       |                                             |
|                |       |  +------------------+     +--------------+  |
| (via NextJS UI)|<----->|  | User-Facing Agent|     | System Agent |  |
|                |       |  | (Trust & Chat)   |     | (Enforcer)   |  |
+----------------+       |  +--------+---------+     +-------+------+  |
        |                |           |                       |         |
        | (Manual CDS    |           |                       |         |
        |  Flow)         |  +--------v---------+     +-------v------+  |
        |                |  |  Analysis Agent  |     |  Trade Agent |  |
        +--------------->|  | (Data Digest)    |     |  (Execution) |  |
                         |  +--------+---------+     +-------+------+  |
                         |           |                       |         |
                         |  +--------v-----------------------v------+  |
                         |  |            Observer Agent             |  |
                         |  |           (Anti-Hallucination)        |  |
                         |  +---------------------------------------+  |
                         +---------------------------------------------+
```

---

## 2. API Reference (v1.0.1)

All endpoints are hosted on `http://localhost:8080/api/v1`.

### 2.1 Portfolio & Banking (Manual CDS)

#### `GET /portfolio/{userId}`
Retrieves the aggregated wallet state.
- **Sample Response:**
```json
{
  "userId": "usr_001",
  "protectedCapitalBase": 100000.00,
  "accumulatedProfit": 12500.00,
  "totalWithdrawals": 5000.00,
  "totalCurrentValue": 112500.00,
  "lastUpdatedAt": "2026-02-24T12:00:00"
}
```

#### `POST /portfolio/sync-deposit`
Confirms a manual broker deposit.
- **Parameters:** `userId` (String), `amount` (BigDecimal)
- **Sample Response:**
```json
{
  "status": "SUCCESS",
  "newProtectedCapitalBase": 150000.00,
  "message": "Deposit synced. Protected capital updated."
}
```

#### `POST /portfolio/request-withdrawal`
Requests a withdrawal from accumulated profit.
- **Parameters:** `userId` (String), `amount` (BigDecimal)
- **Status 200 (Success):**
```json
{
  "status": "SUCCESS",
  "message": "Sell order initiated. Once settled, transfer cash from CDS manually."
}
```
- **Status 400 (Denied):**
```json
{
  "status": "DENIED",
  "message": "Withdrawal denied. Breaches capital preservation constraint."
}
```

### 2.2 Trade History

#### `GET /trades/{userId}`
Returns the most recent algorithmic trades.
- **Sample Response:**
```json
[
  {
    "id": "...",
    "assetId": "CSE:JKH",
    "action": "BUY",
    "amountAllocated": 1500.00,
    "executionPrice": 185.50,
    "strategyUsed": "TREND_FOLLOWER",
    "timestamp": "2026-02-24T14:30:00"
  }
]
```

### 2.3 AI Agent Interaction

#### `POST /agent/chat`
Conversational link to the User-Facing Agent.
- **Request Body (Plain Text):** "How much profit have I made today?"
- **Sample Response:** "Your portfolio has generated LKR 2,400 in profit today. Your initial capital of LKR 100,000 remains securely firewalled."

---

## 3. Core Logic Walkthroughs

### 3.1 Capital Constraint (The "Firewall")
1. **Trade Agent** identifies an opportunity.
2. It calls the `VerifyCapitalConstraint` tool (Synchronous MCP).
3. **System Agent** checks: `Required_Risk <= Accumulated_Profit * 0.10`.
4. If `Accumulated_Profit` is 0 or insufficient, the trade is **DENIED**. The `protectedCapitalBase` is never locked or allocated to a trade.

### 3.2 Manual CDS Flow
There is no direct Bank API connection.
- **Deposits:** User deposits to broker → UI confirms → Database base capital increases.
- **Withdrawals:** UI requests amount → System verifies profit → Trade Agent sells stocks → User manually transfers cash from Broker to Bank.

### 3.3 Observer Loop
1. Every Kafka event from **Trade** or **Analysis** agents is mirrored to the **Observer Agent**.
2. Observer scores the reasoning:
   - "Are they mentioning Azure?" (Hallucination)
   - "Is the capital logic correct?"
3. If score < 0.85, the incident is logged as an ALERT.

---

## 4. Configurable Variables

Defined in `application.properties`:

| Variable | Default | Purpose |
|----------|---------|---------|
| `antigravity.risk.cvar-threshold-percentage` | `0.10` | Max % of profit exposed to risk. |
| `antigravity.agent.analysis.poll-rate-ms` | `60000` | Frequency of market data updates. |
| `antigravity.api.sandbox.broker.url` | `http://localhost:8090` | Endpoint for the mock paper trading broker. |
| `spring.ai.openai.chat.options.temperature` | `0.2` | Creativity vs. deterministic safety for AI. |
