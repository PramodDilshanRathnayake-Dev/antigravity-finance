# Deployment Document — Antigravity Trading System v1.0.1
> **Document Version:** v1.0.1
> **Date:** 2026-02-24
> **Author:** Architecture Agent
> **Deployment Targets:** (1) Local Docker Compose — Development/Demo | (2) AWS EKS — Production

---

## 1. Executive Summary

This document covers all deployment configurations for the Antigravity Trading System v1.0.1. It defines environment prerequisites, infrastructure setup, step-by-step deployment procedures, rollback plans, and security hardening requirements for both **local development** (Docker Compose) and **production** (AWS EKS).

---

## 2. Prerequisites

### 2.1 Local Development Prerequisites

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Docker Desktop | ≥ 25.x | Container runtime |
| Docker Compose | ≥ 2.x | Multi-service orchestration |
| Java JDK | 21 | Backend build |
| Apache Maven | 3.9.x | Backend build system |
| Node.js & npm | 20 LTS | Frontend build |
| OpenAI API Key | Active | LLM inference |

### 2.2 AWS Production Prerequisites

| Dependency | Account/Tool |
|-----------|-------------|
| AWS Account | With billing enabled |
| AWS CLI | v2, configured with IAM role |
| `eksctl` | For EKS cluster provisioning |
| `kubectl` | v1.29 |
| GitHub repository | With Actions secrets configured |
| Docker Hub / Amazon ECR | Image registry |

---

## 3. Environment Configuration

### 3.1 Environment Variables

| Variable | Description | Example |
|---------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI LLM API key | `sk-...` |
| `POSTGRES_URL` | JDBC connection string | `jdbc:postgresql://postgres:5432/trading_engine` |
| `POSTGRES_USER` | DB username | `antigravity` |
| `POSTGRES_PASSWORD` | DB password | (use secrets manager in prod) |
| `KAFKA_BOOTSTRAP` | Kafka broker address | `kafka:29092` |
| `LOCALMARKET_API_URL` | Broker/sandbox market API URL | `http://sandbox-broker:8090` |
| `ANTIGRAVITY_CVAR_THRESHOLD` | CVaR risk % of profit | `0.10` |
| `ANALYSIS_POLL_RATE_MS` | Market poll frequency | `60000` |

> **SECURITY RULE:** No secrets shall be committed to Git. All secrets are injected via environment variables or AWS Secrets Manager.

---

## 4. Local Docker Deployment (Development/Demo)

### 4.1 Services

| Service | Image | Port | Description |
|---------|-------|------|-------------|
| `postgres` | `postgres:15` | `5432` | Portfolio, trades, audit DB |
| `zookeeper` | `confluentinc/cp-zookeeper:7.6.0` | `2181` | Kafka coordination |
| `kafka` | `confluentinc/cp-kafka:7.6.0` | `29092` | Event bus |
| `sandbox-broker` | `ghcr.io/open-agent-tools/open-paper-trading-mcp:latest` | `8090` | Paper trading broker API |
| `trading-engine` | Built from `./trading-engine` | `8080` | Spring Boot backend |
| `ui` | Built from `./ui` | `3000` | NextJS frontend |

### 4.2 Step-by-Step Local Deployment

**Step 1: Clone & configure environment**
```bash
git clone https://github.com/your-org/antigravity-finance.git
cd antigravity-finance
cp .env.example .env
# Edit .env and set OPENAI_API_KEY
```

**Step 2: Build the Spring Boot backend JAR**
```bash
cd trading-engine
./mvnw clean package -DskipTests
cd ..
```

**Step 3: Build and start all services**
```bash
docker-compose up --build -d
```

**Step 4: Verify all services are healthy**
```bash
docker-compose ps
# All services should show status: Up (healthy)
```

**Step 5: Initialise the sandbox broker with seed data**
```bash
curl -X POST http://localhost:8090/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"account_id": "usr_001", "initial_balance": 1000000.00}'
```

**Step 6: Seed the portfolio via the System Agent**
```bash
curl -X POST "http://localhost:8080/api/v1/portfolio/sync-deposit?userId=usr_001&amount=100000"
```

**Step 7: Access the UI**
- Open browser → `http://localhost:3000`
- Navigate to Wallet Dashboard — verify KPIs render.
- Navigate to Chat — send "What is my portfolio status?" to the AI agent.

**Step 8: Verify Kafka topics are active**
```bash
docker exec -it antigravity-kafka kafka-topics --list --bootstrap-server localhost:29092
# Expected: market.analysis.health, trade.execution.logs, system.audit.traces, system.cds.balance.sync
```

### 4.3 Stopping the Environment
```bash
docker-compose down
# To also remove volumes (full reset):
docker-compose down -v
```

---

## 5. AWS Production Deployment

### 5.1 Infrastructure Provisioning

**Step 1: Create EKS Cluster**
```bash
eksctl create cluster \
  --name antigravity-prod \
  --region ap-southeast-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 3 \
  --nodes-min 2 \
  --nodes-max 5 \
  --managed
```

**Step 2: Provision Amazon MSK (Kafka)**
- Create via AWS Console or Terraform
- Cluster type: Provisioned, `kafka.m5.large`, 3 brokers
- Enable encryption in transit (TLS) and at rest

**Step 3: Provision Amazon Aurora PostgreSQL**
```bash
aws rds create-db-cluster \
  --db-cluster-identifier antigravity-aurora \
  --engine aurora-postgresql \
  --engine-version 15.4 \
  --master-username antigravity \
  --master-user-password <from-secrets-manager> \
  --db-subnet-group-name <your-subnet-group>
```

**Step 4: Store secrets in AWS Secrets Manager**
```bash
aws secretsmanager create-secret \
  --name /antigravity/prod/openai-api-key \
  --secret-string '{"OPENAI_API_KEY": "sk-..."}'

aws secretsmanager create-secret \
  --name /antigravity/prod/db-password \
  --secret-string '{"POSTGRES_PASSWORD": "..."}'
```

**Step 5: Build and push Docker images to ECR**
```bash
# Authenticate
aws ecr get-login-password --region ap-southeast-1 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-southeast-1.amazonaws.com

# Build and push backend
docker build -t antigravity-trading-engine ./trading-engine
docker tag antigravity-trading-engine:latest <ecr-uri>/antigravity-trading-engine:v1.0.1
docker push <ecr-uri>/antigravity-trading-engine:v1.0.1

# Build and push frontend
docker build -t antigravity-ui ./ui
docker tag antigravity-ui:latest <ecr-uri>/antigravity-ui:v1.0.1
docker push <ecr-uri>/antigravity-ui:v1.0.1
```

**Step 6: Deploy to EKS via Kubernetes manifests**
```bash
kubectl apply -f k8s/trading-engine-deployment.yaml
kubectl apply -f k8s/ui-deployment.yaml
kubectl apply -f k8s/services.yaml
kubectl apply -f k8s/configmap.yaml
```

**Step 7: Configure AWS API Gateway**
- Create REST API
- Integrate with the `trading-engine` LoadBalancer service
- Enable TLS via ACM certificate
- Configure usage plans and API keys for rate limiting

**Step 8: Verify Deployment**
```bash
kubectl get pods -n antigravity
# All pods should show STATUS: Running, READY: 1/1

kubectl logs -f deployment/trading-engine -n antigravity
# Verify: "[AnalysisAgent] Starting market evaluation cycle"
# Verify: "[ObserverAgent] Agent passed validation"
```

### 5.2 CI/CD Pipeline (GitHub Actions)

The pipeline defined in `.github/workflows/deploy-aws.yml` executes automatically on push to `main`:

1. **Build:** `./mvnw clean test` — all 17+ tests must pass
2. **Coverage Gate:** JaCoCo report — fails if coverage < 80%
3. **Security Scan:** `dependency-check` Maven plugin — fails on CRITICAL CVEs
4. **Docker Build & Push:** Backend + UI images pushed to ECR
5. **EKS Deploy:** `kubectl set image` rolling update
6. **Health Check:** POST-deploy smoke test against `/actuator/health`

---

## 6. Rollback Procedure

### Local
```bash
docker-compose down
git checkout v1.0.0
docker-compose up --build -d
```

### AWS (Zero-Downtime Rollback)
```bash
# Roll back to previous deployment revision
kubectl rollout undo deployment/trading-engine -n antigravity
kubectl rollout undo deployment/ui -n antigravity

# Verify rollback status
kubectl rollout status deployment/trading-engine -n antigravity
```

---

## 7. Security Hardening Checklist

| Control | Implementation | Status |
|---------|--------------|--------|
| No secrets in Git | `.env` in `.gitignore`, AWS Secrets Manager in prod | ✅ |
| HTTPS everywhere | API Gateway TLS termination, MSK TLS, Aurora SSL | ✅ |
| Least privilege IAM | EKS pod IAM roles scoped to required resources only | ✅ |
| Network isolation | VPC private subnets for DB and Kafka; no public access | ✅ |
| Image vulnerability scan | ECR image scanning on push | ✅ |
| Dependency CVE scan | `dependency-check` in CI pipeline | ✅ |
| API rate limiting | AWS API Gateway usage plans | ✅ |
| DB at-rest encryption | Aurora storage encryption enabled | ✅ |
| Kafka TLS | MSK in-transit encryption enabled | ✅ |
| Observer Agent monitoring | Confidence scoring + GEMINI.md guardrails | ✅ |

---

## 8. Post-Deployment Verification Checklist

- [ ] All pods running in EKS (`kubectl get pods -n antigravity`)
- [ ] Health endpoint returns `UP`: `GET /actuator/health`
- [ ] Kafka topics visible and producing messages
- [ ] Aurora DB accessible from trading-engine pod
- [ ] UI accessible at API Gateway URL — wallet dashboard renders
- [ ] Chat interface responds to test query
- [ ] Observer Agent logging confidence scores > 0.85 in CloudWatch
- [ ] No CRITICAL findings in dependency-check report
- [ ] GitHub Actions pipeline green
