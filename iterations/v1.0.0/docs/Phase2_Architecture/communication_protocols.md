# Communication Protocols & Schemas (Phase 2)

This document defines the formal data contracts used across the Antigravity trading system. There are two primary mediums of internal communication: **Kafka (Asynchronous Events)** and **MCP (Model Context Protocol - Synchronous Tool Calls)**.

All payloads use **JSON** to maintain strict serialization for the Spring AI `ChatClient` structured output converters.

---

## 1. Event-Driven Protocols (Amazon MSK / Kafka)

Kafka is used for high-volume, asynchronous, or "publish-and-forget" data streams.

### Topic: `market.analysis.health`
**Publisher**: Analysis Agent
**Consumers**: Trade Agent, Observer Agent
**Purpose**: Emits dynamic market health, volatility metrics, and detected whale anomalies in the LocalMarket API.

```json
{
  "timestamp": "2026-02-23T00:00:00Z",
  "asset_id": "LOCAL_INDEX_1",
  "volatility_score": 0.45,
  "trend": "BULLISH",
  "anomaly_detected": false,
  "recommended_strategy": "DISCRETE_SWING",
  "confidence": 0.92
}
```

### Topic: `trade.execution.logs`
**Publisher**: Trade Agent
**Consumers**: System Agent (for wallet updates), Observer Agent (for audit logging)
**Purpose**: Emits notifications of executed trades.

```json
{
  "trade_id": "trd_883a991b",
  "timestamp": "2026-02-23T00:05:00Z",
  "asset_id": "LOCAL_INDEX_1",
  "action": "BUY",
  "capital_used": 1500.00,
  "expected_roi": 0.08,
  "cvar_exposure": 0.05
}
```

### Topic: `system.audit.traces`
**Publisher**: All Agents
**Consumers**: Observer Agent
**Purpose**: Submits agent reasoning, prompt chains, and Spring AI context window usage for anti-hallucination monitoring.

```json
{
  "agent_id": "AnalysisAgent-Node1",
  "trace_id": "req_88124x",
  "timestamp": "2026-02-23T00:00:01Z",
  "prompt_tokens_used": 450,
  "completion_tokens_used": 112,
  "reasoning_summary": "Detected 5% volume spike over 10min MA. Marked trend as BULLISH.",
  "self_confidence_score": 0.96
}
```

---

## 2. MCP (Model Context Protocol) Tools

MCP is used when an Agent must perform a *synchronous* action requiring immediate authorization, validation, or strict data retrieval before continuing its reasoning chain. It leverages Spring AI's `@Tool` architecture.

### Tool: `VerifyCapitalConstraint`
**Host**: System Agent
**Invoker**: Trade Agent, User-Facing Agent
**Purpose**: Strictly enforces `Current_Value(t) >= Initial_Capital + Σ Deposits(t)`. Trade agent *must* call this before allocating capital to a new trade.
- **Input Schema:**
  ```json
  { "requested_allocation": 1500.00 }
  ```
- **Output Schema:**
  ```json
  {
    "approved": true,
    "max_allowable_drawdown": 8500.00,
    "current_cvar_utilization": 0.03,
    "message": "Allocation approved. 10% CVaR threshold remains unbreached."
  }
  ```

### Tool: `RequestBankWithdrawal`
**Host**: System Agent
**Invoker**: User-Facing Agent
**Purpose**: Processes a verified user intent to execute a banking withdrawal.
- **Input Schema:**
  ```json
  { 
    "user_id": "usr_992", 
    "withdrawal_amount": 500.00,
    "destination_bank_routing": "XXXXX"
  }
  ```
- **Output Schema:**
  ```json
  {
    "status": "APPROVED",
    "transaction_id": "wt_77619",
    "message": "Withdrawal processed from accumulated profit. Preserved capital untouched."
  }
  ```

### Tool: `AdjustAgentContext`
**Host**: System Agent / Main Orchestrator
**Invoker**: Observer Agent
**Purpose**: Used to dynamically reboot or clear the context window of an agent if confidence drops below 0.85 per the `GEMINI.md` constraints.
- **Input Schema:**
  ```json
  { "target_agent_id": "TradeAgent-Node2", "action": "CLEAR_CONTEXT" }
  ```
- **Output Schema:**
  ```json
  { "status": "SUCCESS", "message": "TradeAgent-Node2 context flushed." }
  ```
