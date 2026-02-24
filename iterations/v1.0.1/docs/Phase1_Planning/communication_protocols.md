# Communication Protocols & Schemas — v1.0.1
> **Document Version:** v1.0.1
> **Date:** 2026-02-24
> **Change Summary from v1.0.0:** `RequestBankWithdrawal` MCP tool removed. New `SyncCdsDeposit` and `RequestStockSellWithdrawal` tools added to reflect the manual CDS-based capital flow. `system.cds.balance.sync` Kafka topic added.

All payloads use **JSON**. Two communication mediums: **Kafka (Async)** and **MCP Tools (Sync)**.

---

## 1. Event-Driven Protocols (Amazon MSK / Kafka)

### Topic: `market.analysis.health`
**Publisher**: Analysis Agent | **Consumers**: Trade Agent, Observer Agent
**Purpose**: Emits real-time market health, volatility metrics, and CSE anomaly events.

```json
{
  "timestamp": "2026-02-24T10:00:00Z",
  "asset_id": "CSE:JKH",
  "volatility_score": 0.45,
  "trend": "BULLISH",
  "anomaly_detected": false,
  "recommended_strategy": "DISCRETE_SWING",
  "confidence": 0.92
}
```

### Topic: `trade.execution.logs`
**Publisher**: Trade Agent | **Consumers**: System Agent (portfolio update), Observer Agent (audit)
**Purpose**: Emits records of executed/rejected trade decisions.

```json
{
  "trade_id": "trd_883a991b",
  "timestamp": "2026-02-24T10:05:00Z",
  "asset_id": "CSE:JKH",
  "action": "BUY",
  "capital_used": 1500.00,
  "expected_roi": 0.08,
  "cvar_exposure": 0.05,
  "capital_constraint_status": "APPROVED"
}
```

### Topic: `system.audit.traces`
**Publisher**: All Agents | **Consumer**: Observer Agent
**Purpose**: Agent reasoning traces for anti-hallucination monitoring.

```json
{
  "agent_id": "AnalysisAgent-Node1",
  "trace_id": "req_88124x",
  "timestamp": "2026-02-24T10:00:01Z",
  "prompt_tokens_used": 450,
  "completion_tokens_used": 112,
  "reasoning_summary": "Detected 5% volume spike over 10min MA on CSE:JKH. Marked BULLISH.",
  "self_confidence_score": 0.96
}
```

### Topic: `system.cds.balance.sync` *(NEW in v1.0.1)*
**Publisher**: Analysis Agent (periodic CDS balance poll) | **Consumer**: System Agent
**Purpose**: Keeps the Portfolio DB in sync with the real CDS account cash balance. Read-only sync signal.

```json
{
  "timestamp": "2026-02-24T10:00:00Z",
  "user_id": "usr_992",
  "cds_account_id": "CDS_12345678",
  "cds_cash_balance": 87500.00,
  "last_trade_settlement_date": "2026-02-23"
}
```

---

## 2. MCP (Model Context Protocol) Tool Contracts

### Tool: `VerifyCapitalConstraint` *(Updated — now includes CDS balance check)*
**Host**: System Agent | **Invoker**: Trade Agent
**Purpose**: Enforces `Current_Value(t) >= Initial_Capital + Σ Deposits(t)` before any BUY. Now also validates `requestedAllocation <= CDS_cash_balance` to prevent overspending uninvested capital.

**Input:**
```json
{
  "user_id": "usr_992",
  "requested_allocation": 1500.00,
  "estimated_cvar_risk": 75.00
}
```
**Output:**
```json
{
  "status": "APPROVED",
  "max_allowable_drawdown": 500.00,
  "current_cvar_utilization": 0.03,
  "cds_cash_available": 12400.00,
  "message": "Allocation approved. CVaR within 10% profit bound. CDS balance sufficient."
}
```

### Tool: `SyncCdsDeposit` *(NEW — replaces RequestBankWithdrawal)*
**Host**: System Agent | **Invoker**: User-Facing Agent (via UI deposit confirmation)
**Purpose**: Records a manually completed broker deposit into the Portfolio `protectedCapitalBase`. No bank API call is made; this is a pure DB write confirming the user's manual CDS credit.

**Input:**
```json
{
  "user_id": "usr_992",
  "deposit_amount": 50000.00,
  "broker_reference_id": "BRK_TX_4491",
  "deposit_date": "2026-02-24"
}
```
**Output:**
```json
{
  "status": "SUCCESS",
  "new_protected_capital_base": 150000.00,
  "message": "Deposit recorded. Protected capital base updated. No bank API involved."
}
```

### Tool: `RequestStockSellWithdrawal` *(NEW — replaces Bank transfer call)*
**Host**: System Agent | **Invoker**: User-Facing Agent
**Purpose**: Validates the withdrawal amount against `accumulatedProfit`, instructs the Trade Agent to execute a SELL order to convert holdings to CDS cash, and logs the withdrawal event. The user then manually transfers from CDS to bank.

**Input:**
```json
{
  "user_id": "usr_992",
  "withdrawal_amount": 5000.00,
  "preferred_asset_to_sell": "CSE:JKH"
}
```
**Output:**
```json
{
  "status": "APPROVED",
  "withdrawal_amount": 5000.00,
  "sell_order_id": "so_77619",
  "message": "SELL order initiated on CSE:JKH. Funds will appear in CDS cash balance post-settlement (T+3). Initiate manual bank transfer from broker portal."
}
```

### Tool: `AdjustAgentContext` *(Unchanged from v1.0.0)*
**Host**: System Agent / Main Orchestrator | **Invoker**: Observer Agent
**Purpose**: Flushes or resets a sub-agent's context window when confidence < 0.85.

**Input:**
```json
{ "target_agent_id": "TradeAgent-Node2", "action": "CLEAR_CONTEXT" }
```
**Output:**
```json
{ "status": "SUCCESS", "message": "TradeAgent-Node2 context flushed." }
```
