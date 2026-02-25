# Task Checklist - Sandbox Simulation Run

## Project Phase: v1.0.1 - Simulation Execution
**Initial Capital:** LKR 100,000
**Market:** CSE (Sandbox Broker)

---

### 1. Architecture (Claude 4.6 Opus)
- [ ] **Verify Simulation Logic**: Confirm that the `SimulationController` correctly initializes the `protectedCapitalBase` to 100,000.
- [ ] **GCP Alignment**: Ensure configuration variables are ready for GCP transition even if running locally.
- [ ] **Simulation Parameters**: Set the tick rate and strategy parameters for the 2-hour window.

### 2. Development (Claude 4.6 Sonnet)
- [ ] **Portfolio Initialization**: Create an endpoint or script to set the initial capital to 100,000 in the `trading_engine` DB.
- [ ] **Simulation Kick-off**: Update/Verify `SimulationController.java` to start the Analysis and Trade agent loops.
- [ ] **Constraint Enforcement**: Verify that the "Firewall" logic is active.

### 3. QA (Gemini 3.1 High)
- [ ] **Pre-Simulation Smoke Test**: Verify `sandbox-broker` is reachable from `trading-engine`.
- [ ] **Negative Case**: Try to force a trade that exceeds a risk buffer of 10% of profit (which is 0 initially). Verify it is blocked.
- [ ] **Capital Zero-Touch**: Verify that `Initial_Capital` remains unchanged after a series of simulated trades.

### 4. DevOps (Gemini 3.1 High)
- [ ] **Infrastructure Launch**: Run `docker-compose up -d`.
- [ ] **DB Initialization**: Run `init_db.py` or equivalent to set up users and capital.
- [ ] **Logging & Monitoring**: Ensure Kafka and logs are capturing agent reasoning for the 2-hour audit.

---
**Status:** Awaiting PM Spawning.
