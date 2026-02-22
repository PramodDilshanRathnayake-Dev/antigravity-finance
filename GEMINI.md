# Antigravity Trading System - Agent Context & Rules

Approval will be followed by demonstration of saved folder structure and documents formats which are compatible for enterprise audit as per latest rules.
This system is an autonomous, multi-agent financial investing platform bridging Crypto and Local Markets. Built in Java & Spring AI, deployed on AWS, communicating via HTTPS and MCP protocols.

## Hard Rules for AI Agents (Observer/Validation Prompts)
1. **Source of Truth**: The FRS (`implementation_plan.md`) is the unyielding single source of truth. All agents must defer strictly to its constraints.
2. **Context Preservation**: Do not hallucinate external libraries, cloud providers (e.g., Azure), or unapproved protocols. Stick to AWS, HTTPS, and MCP.
3. **Absolute Capital Constraint**: Never authorize or construct a logic path that violates: `Current_Value(t) >= Initial_Capital + Σ Deposits(t)`. The initial capital and deposits must be cryptographically/logically firewalled from trading losses.
4. **Agent Roles & Handoff**: 
    - *Architecture Agent*: System design and structural constraints.
    - *Project Manager Agent*: Scope, validation parameters, FRS updates.
    - *Developer Agent(s)*: Code generation strictly matching FRS.
    - *Tester Agent(s)*: Validating VaR, Monte Carlo, and Edge-Case limits.
    - *DevSecOps Agent*: AWS deployment and cloud security configurations.
5. **Architectural Permanence**: Options like Cloud (AWS/GCP) or APIs (Crypto/Local) were explicitly resolved in Planning. Do not use OR fallbacks. It is `AWS`, focus on `LocalMarket` in v1 (Crypto later).
6. **Mandatory Documentation**: Every phase completion must include a documentation update checkpoint to satisfy audit requirements.
