# Software Architecture Document — Antigravity Trading System v1.0.1
> **Document Version:** v1.0.1
> **Date:** 2026-02-24
> **Author:** Architecture Agent
> **Supersedes:** v1.0.0 architecture_design.md

---

## 1. Change Summary from v1.0.0

| Area | v1.0.0 | v1.0.1 |
|------|--------|--------|
| Banking Flow | Bank API Gateway (HTTPS) | Manual CDS account — no Bank API |
| Deposit | `POST /deposit` → Bank API | UI confirmation → `SyncCdsDeposit` MCP tool |
| Withdrawal | `POST /withdraw` → Bank API | Stock SELL order → CDS cash → manual bank transfer |
| Frontend | None | NextJS 14 / React 18 (Wallet Dashboard + Chat) |
| Sandbox | N/A | `open-paper-trading-mcp` in Docker Compose |
| System Agent Bottleneck | Single thread, synchronous | Async reader-writer pattern with separated read/write paths |

---

## 2. High-Level System Architecture

The Antigravity trading system is a reactive, event-driven multi-agent platform built on **Java 21, Spring Boot 3.4.x, and Spring AI 1.0.0-M6**. The v1.0.1 architecture adds a **NextJS/React frontend**, a **local sandbox trading environment**, and resolves the **System Agent concurrency bottleneck** via an async reader-writer separation pattern.

### 2.1 AWS Infrastructure Footprint

```
┌─────────────────────────────────────────────────────────────────────┐
│                         AWS Cloud (Production)                       │
│                                                                       │
│  ┌────────────┐    ┌────────────────────────────────────────────┐   │
│  │  Route 53  │───▶│  API Gateway (HTTPS TLS Termination)       │   │
│  └────────────┘    └──────────────────┬─────────────────────────┘   │
│                                        │                              │
│  ┌─────────────────────────────────────▼───────────────────────┐    │
│  │                Amazon EKS (Kubernetes)                        │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │    │
│  │  │User-     │ │System    │ │Analysis  │ │Trade         │  │    │
│  │  │Facing    │ │Agent     │ │Agent     │ │Agent         │  │    │
│  │  │Agent Pod │ │Pod       │ │Pod       │ │Pod           │  │    │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘  │    │
│  │       │             │             │               │           │    │
│  │  ┌────┴─────────────┴─────────────┴───────────────┴──────┐  │    │
│  │  │            Amazon MSK (Managed Kafka)                  │  │    │
│  │  │  Topics: market.analysis.health | trade.execution.logs │  │    │
│  │  │           system.audit.traces | system.cds.balance.sync│  │    │
│  │  └────────────────────────────────────────────────────────┘  │    │
│  │                                                                │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐   │    │
│  │  │Aurora PostgreSQL│  │ElastiCache     │  │Observer Agent│   │    │
│  │  │(Capital, Trades│  │/Redis (Market  │  │Pod           │   │    │
│  │  │ AuditLog)      │  │ data buffer)   │  │              │   │    │
│  │  └────────────────┘  └────────────────┘  └──────────────┘   │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                       │
│  ┌──────────────┐   ┌──────────────────┐   ┌────────────────────┐  │
│  │AWS Secrets   │   │CloudWatch        │   │AWS X-Ray           │  │
│  │Manager       │   │(Metrics/Alerts)  │   │(Distributed Traces)│  │
│  └──────────────┘   └──────────────────┘   └────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│              External Integrations                        │
│  CSE/Broker API (Market Data + Order Execution)          │
│  OpenAI API (LLM via Spring AI)                          │
└──────────────────────────────────────────────────────────┘
```

### 2.2 Local Docker Compose Stack (Development/Demo)

```
┌─────────────────────────────────────────────────────────────────┐
│                    docker-compose.yml                            │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │ trading-     │  │ ui           │  │ open-paper-trading  │    │
│  │ engine       │  │ (NextJS)     │  │ (sandbox broker API)│    │
│  │ :8080        │  │ :3000        │  │ :8090               │    │
│  └──────────────┘  └──────────────┘  └────────────────────┘    │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │ postgres     │  │ zookeeper    │  │ kafka              │    │
│  │ :5432        │  │ :2181        │  │ :29092             │    │
│  └──────────────┘  └──────────────┘  └────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Multi-Agent Network Topology (v1.0.1)

```
User (Browser)
      │ HTTPS
      ▼
┌─────────────────────┐
│  NextJS/React UI    │  Wallet Dashboard + Chat
└────────┬────────────┘
         │ REST/HTTPS
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│  User-Facing Agent  (Spring Boot REST Controller)                   │
│  - Translates UI intent → system actions                            │
│  - Anti-hallucination system prompt                                 │
│  - Reads Portfolio state (READ-ONLY via PortfolioRepository)        │
└────────┬────────────────────────────────────┬────────────────────── ┘
         │ MCP: SyncCdsDeposit                │ MCP: RequestStockSellWithdrawal
         │ MCP: VerifyCapitalConstraint (READ) │ (triggers SELL order)
         ▼                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  System Agent  (Spring Service + MCP Tool Host)                     │
│                                                                       │
│  READ PATH (non-blocking, @Transactional(readOnly=true)):            │
│    VerifyCapitalConstraint → PortfolioRepository.findByUserId()      │
│                                                                       │
│  WRITE PATH (single-writer, @Transactional with SERIALIZABLE):       │
│    SyncCdsDeposit → portfolio.addDeposit() → save()                  │
│    RequestStockSellWithdrawal → portfolio.processWithdrawal() → save()│
│                                                                       │
│  Bottleneck Resolution: See Section 4                                │
└────────┬────────────────────────────────────────────────────────────┘
         │ Kafka: trade.execution.logs (consume)
         │ DB write: Portfolio profit/loss update
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Trade Agent  (Kafka Consumer + Spring AI ChatClient)               │
│  - Subscribes to market.analysis.health                             │
│  - Calls VerifyCapitalConstraint (read-only MCP) before every BUY  │
│  - Publishes to trade.execution.logs on decision                    │
└────────┬────────────────────────────────────────────────────────────┘
         │ Kafka: market.analysis.health (consume)
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Analysis Agent  (Scheduled Spring Service + LocalMarketApiClient)  │
│  - Polls CSE Sandbox/Broker API every 60s                          │
│  - Publishes market.analysis.health events                          │
│  - Publishes system.cds.balance.sync events                         │
└────────┬────────────────────────────────────────────────────────────┘
         │ Kafka: system.audit.traces + trade.execution.logs (consume)
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Observer Agent  (Kafka Consumer + AI Confidence Scorer)            │
│  - Evaluates all payloads against GEMINI.md rules                  │
│  - Confidence < 0.85 → publishes AdjustAgentContext MCP call       │
│  - Saves all evaluations to AgentAuditLog table                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. System Agent Bottleneck Resolution (A-4)

### Problem Statement
In v1.0.0, the System Agent is a single `@Service` with `@Transactional` methods. Both the **Trade Agent** (calling `verifyCapitalConstraint` before every BUY — potentially every 60s) and the **User-Facing Agent** (handling live user deposit/withdrawal HTTP requests) call the System Agent concurrently. Under load, this creates:
- Database row-level lock contention on the `portfolios` table
- Potential transaction timeout or deadlock if a write (deposit) and a verification read collide

### Resolution: Async Reader-Writer Separation Pattern

**Design Decision:** Separate read and write paths at the transaction isolation level, with no additional infrastructure required (no Redis queue, no separate service — stays within the same Spring context).

#### 4.1 Read Path — Non-blocking (`verifyCapitalConstraint`)
```java
// READ-ONLY: No lock taken. Uses database snapshot read.
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public CapitalVerificationResponse verifyCapitalConstraint(CapitalVerificationRequest req) { ... }
```
- `readOnly = true` tells Hibernate/PostgreSQL to not acquire row-level write locks
- `READ_COMMITTED` isolation: reads the last committed snapshot — appropriate for risk checks
- Multiple Trade Agent threads can call this concurrently without blocking each other
- **Expected throughput:** near-unlimited concurrent reads

#### 4.2 Write Path — Serialized (`syncCdsDeposit`, `processWithdrawal`)
```java
// WRITE: Uses SERIALIZABLE isolation for strict ordering
@Transactional(isolation = Isolation.SERIALIZABLE)
public ActionStatus syncCdsDeposit(String userId, BigDecimal amount) { ... }
```
- `SERIALIZABLE` ensures only one write at a time per portfolio row
- User-facing writes are low-frequency (human-initiated) — serialization overhead is acceptable
- **Expected throughput:** 1-2 writes per user session — no bottleneck

#### 4.3 Portfolio Row-Level Lock (Extra Safety)
```java
// In PortfolioRepository — pessimistic write lock on writes only
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Portfolio p WHERE p.userId = :userId")
Optional<Portfolio> findByUserIdForUpdate(@Param("userId") String userId);
```
- Write methods use `findByUserIdForUpdate()` — explicit pessimistic lock during writes
- Read methods use `findByUserId()` — no lock

#### 4.4 Summary Table

| Caller | Method | Isolation | Lock | Concurrency |
|--------|--------|-----------|------|-------------|
| Trade Agent | `verifyCapitalConstraint` | READ_COMMITTED | None | ✅ High concurrency |
| User (Deposit) | `syncCdsDeposit` | SERIALIZABLE | PESSIMISTIC_WRITE | ✅ Safe, sequential |
| User (Withdrawal) | `processWithdrawal` | SERIALIZABLE | PESSIMISTIC_WRITE | ✅ Safe, sequential |

---

## 5. Sandbox Trading Environment (A-5)

### Finding
The **Colombo Stock Exchange (CSE) does not provide an official free sandbox or paper trading API** as of 2025. The only available CSE endpoint access is via an unofficial GitHub-documented API (reverse-engineered from the CSE web portal), which provides read-only market data — not order execution capability.

### Recommendation: `open-paper-trading-mcp`

**Selected Sandbox:** [`open-paper-trading-mcp`](https://github.com/Open-Agent-Tools/open-paper-trading-mcp)

| Attribute | Detail |
|-----------|--------|
| Type | Open-source, self-hosted |
| Interface | REST API (FastAPI, 49 endpoints) + MCP protocol |
| Asset Support | Stocks, ETFs, Options, Bonds |
| Deployment | Docker container |
| Cost | Free |
| Data Source | Configurable (can use Yahoo Finance via `yfinance` for real market price data, including historical CSE-equivalent patterns) |
| MCP Support | Native — directly compatible with Spring AI MCP tool calling |

### Integration Architecture
- `open-paper-trading-mcp` runs as service `sandbox-broker` in `docker-compose.yml` on internal port `2080` (mapped to `8090` for host access)
- `LocalMarketApiClient` points to `http://sandbox-broker:2080/api/v1/trading` in local profile
- `TradeAgent` sends BUY/SELL to `http://sandbox-broker:2080/api/v1/trading/orders`
- Real-world CSE asset prices loaded via `yfinance` using CSE stock ticker equivalents or NIKKEI/BSE patterns for simulation realism
- On AWS: replaced with real CSE broker API endpoint (e.g., Arreya or future official CSE API)

---

## 6. Frontend UI Architecture (A-6)

### Technology
- **Framework:** NextJS 14 (App Router)
- **UI Library:** React 18 + TailwindCSS
- **State Management:** React Context API (lightweight — no Redux needed for v1)
- **HTTP Client:** `axios` or native `fetch` with SWR for real-time polling
- **Deployment:** Docker container (`node:20-alpine`) on port `3000`

### Component Tree

```
app/
├── layout.tsx                 # Global layout, fonts, nav
├── page.tsx                   # Root → redirects to /dashboard
├── dashboard/
│   └── page.tsx               # Wallet Dashboard page
│       ├── WalletCard.tsx     # Displays capital metrics (4 KPIs)
│       ├── ProfitChart.tsx    # Accumulated profit over time (line chart)
│       └── RecentTrades.tsx   # Last 10 trades from /api/v1/trades
└── chat/
    └── page.tsx               # Chat interface page
        ├── ChatWindow.tsx     # Renders messages thread
        ├── ChatInput.tsx      # Text input + send
        └── ChatMessage.tsx    # Individual message bubble (user/agent)
```

### Wallet Dashboard — Data Contract

| Metric | Source Endpoint | Field |
|--------|----------------|-------|
| Initial Capital | `GET /api/v1/portfolio/{userId}` | `protectedCapitalBase` |
| Total Deposits | `GET /api/v1/portfolio/{userId}` | `protectedCapitalBase` (cumulative) |
| Accumulated Profit | `GET /api/v1/portfolio/{userId}` | `accumulatedProfit` |
| Total Withdrawals | `GET /api/v1/portfolio/{userId}/withdrawals` | Sum of withdrawal records |
| Current Total Value | `GET /api/v1/portfolio/{userId}` | `totalCurrentValue` (base + profit) |

### Chat Interface — API Contract

```
POST /api/v1/agent/chat
Content-Type: text/plain
Body: "How is my portfolio performing?"

Response: 200 OK
Content-Type: text/plain
Body: "Your portfolio is performing well. Your protected capital of LKR 100,000 remains untouched..."
```

### New Backend Endpoints Required (for Developer Agent)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/portfolio/{userId}` | Wallet summary (all KPIs) |
| `GET` | `/api/v1/portfolio/{userId}/withdrawals` | Withdrawal history |
| `POST` | `/api/v1/portfolio/sync-deposit` | Confirm manual CDS deposit |
| `POST` | `/api/v1/portfolio/request-withdrawal` | Initiate stock-sell withdrawal |
| `GET` | `/api/v1/trades/{userId}` | Recent trade history for UI |

---

## 7. Technology Stack Summary

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend Runtime | Java, Spring Boot, Spring AI | 21, 3.4.3, 1.0.0-M6 |
| LLM | OpenAI GPT-4o via Spring AI | gpt-4o |
| Message Broker | Apache Kafka / Amazon MSK | 3.x |
| Database | PostgreSQL / Amazon Aurora | 15 |
| Cache | Redis / Amazon ElastiCache | 7.x |
| Frontend | NextJS, React, TailwindCSS | 14, 18, 3.x |
| Sandbox Broker | open-paper-trading-mcp | latest |
| Containerisation | Docker, Docker Compose | 25.x, 2.x |
| Orchestration (Prod) | Amazon EKS + KEDA | 1.29 |
| CI/CD | GitHub Actions | N/A |
| Secret Management | AWS Secrets Manager | N/A |
| Observability | CloudWatch + X-Ray + AOP Aspect | N/A |

---

## 8. SOLID & Design Pattern Compliance

| Principle | Implementation |
|-----------|---------------|
| **S** — Single Responsibility | Each agent class has exactly one responsibility |
| **O** — Open/Closed | `BaseAgent` extended by all agents; new agents add without modifying base |
| **L** — Liskov Substitution | All agents implement their respective interfaces via Spring Beans |
| **I** — Interface Segregation | `PortfolioRepository` separates read (`findByUserId`) and write (`findByUserIdForUpdate`) |
| **D** — Dependency Inversion | All agents receive dependencies via constructor injection |
| **Pattern: Strategy** | Trade Agent selects compounding strategy dynamically per market condition |
| **Pattern: Observer** | Observer Agent monitors all agent event streams asynchronously |
| **Pattern: Template Method** | `BaseAgent.chatClient` template used by all agent subclasses |
