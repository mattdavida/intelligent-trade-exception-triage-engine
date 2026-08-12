# Intelligent Trade Exception Triage Engine

> Middle-office trade exception triage reference.
> Pattern: Java owns facts and state; Python/LLM proposes severity + resolution text only; humans approve.

## Core thesis

Java is the system of record for trade exceptions. AI proposes severity and resolution reasoning —
it never invents or recomputes amounts, trade IDs, or status transitions. Humans approve, reject, or
override. Numbers, lifecycle, and **confidence** live in deterministic Java (`:orchestrator`), not in
the LLM's opinion of itself.

**HITL model:** async analyst review — AI finishes a suggestion, UI reviews a completed artifact.
Not mid-graph LangGraph `interrupt()`.

**AI required:** run `.\infra\deploy.ps1` and keep a populated `.env` (gitignored). No stub /
AI-optional path — simpler ops, real Azure OpenAI from the start. Apps fail fast if keys missing.

**Confidence (enterprise):** do **not** use LLM self-grading ("ask the model how sure it is").
That score is not auditable and cannot be defended in a middle-office control review.
This project uses a **traceable, rule-/signal-backed confidence** that we can explain and recompute
from persisted inputs. See design note below.

---

## Architecture Overview

An event-driven, polyglot stack simulating a middle-office trade exception workspace.

```
sample-data (JSON/CSV)
  → Java producer (:producer)
  → Kafka topic raw-trade-exceptions
  → Spring Boot orchestrator (:orchestrator)
       ├─ persist NEW → PostgreSQL → commit/ack
       ├─ AFTER_COMMIT async → FastAPI AI (LangGraph / Azure OpenAI)
       ├─ Java confidence scorer → PENDING_REVIEW
       ├─ REST + SSE → Angular HITL desk
       └─ (optional) produce → resolved-trades
```

### Tech Stack (locked)

| Layer | Choice |
|---|---|
| **Build** | Gradle (multi-project), Java 22 |
| **Message bus** | Kafka (Docker Compose) |
| **Orchestrator** | Spring Boot 3 — Web, Data JPA, Kafka, validation |
| **Database** | PostgreSQL (local Compose; optional Azure Flexible Server later) |
| **AI engine** | Python 3.11+ / FastAPI / LangGraph / Azure OpenAI |
| **Infra (cloud AI)** | Azure Bicep + `deploy.ps1` |
| **Frontend** | Angular 19 standalone / TypeScript / Tailwind / AG Grid |
| **Automation** | PowerShell stack scripts (Windows) |

---

## Design note: auditable confidence (not LLM self-grade)

**Problem:** asking the LLM to emit a 0–100 confidence is convenient for demos but weak for
enterprise ops — the model grades its own homework, the number is not reproducible without the
opaque generation, and auditors cannot map the score to controls or reference data.

**Requirement:** confidence must be something we can **back**:

1. **Derived, not confessed** — computed from explicit signals (rules, lookups, completeness checks),
   not from "how sure are you?" in the prompt.
2. **Traceable** — persist the factor breakdown with the exception (what fired, what weight, what
   missing data).
3. **Recomputable** — given the same inputs + factor table version, the same score comes back
   (deterministic; version-stamp the rubric).
4. **Owned by `:orchestrator` (Java only)** — reference data (taxonomy, allow-lists, amount bands)
   lives with the system of record. Do **not** put the rubric in Python; that blurs the boundary.
   Python returns proposed text (+ severity suggestion); Java scores before persisting.
5. **UI shows the why** — desk shows score **and** contributing factors with real evidence.

**v1 signal sketch (implement in Phase 3 — keep small):**

| Signal | Example | Effect |
|---|---|---|
| Known `discrepancyType` in taxonomy | SSI_MISMATCH mapped | +base |
| Amount / notional band | above threshold | +risk / severity bias (rule), not LLM math |
| Field completeness | missing LEI / SSI refs in `rawDetails` | −confidence |
| Counterparty / instrument on allow-list | present in seed ref data | +confidence |

Store e.g. `confidenceScore`, `confidenceRubricVersion`, `confidenceFactors[]` on the exception row.
Exact weights can be naive for the POC — the **architecture** is the signal.

---

## Design note: ingest vs AI transaction boundaries

**Anti-pattern:** Kafka listener opens a DB transaction, saves `NEW`, then synchronously calls the
Python AI engine on the same thread before commit/ack. A slow LLM holds the Postgres transaction
open, stalls consumer lag, and risks redelivery/timeouts.

**Required flow (Phase 1 + 3):**

1. Consumer maps event → persist `NEW` → **commit** → **ack Kafka offset** (done; no HTTP in this tx).
2. Publish an internal Spring application event (or `@Async` / outbound channel) after commit
   (`TransactionalEventListener(phase = AFTER_COMMIT)` preferred).
3. Async worker: set `ANALYZING` (short tx) → HTTP to FastAPI (no DB tx held) → on success run
   **Java confidence scorer** → persist AI fields + score → `PENDING_REVIEW` (fresh tx) → SSE emit.
4. On AI failure: `ANALYZING_FAILED` in its own tx; retry is a separate concern (simple later).

Phase 1 only needs step 1. Phase 3 adds 2–4.

---

## Suggested ports

| Service | Port | Notes |
|---|---|---|
| Kafka | `9092` | Bootstrap |
| Kafka UI | `8080` | http://localhost:8080 |
| PostgreSQL | `5433` | Compose host port (container 5432; avoids local Windows Postgres) |
| Spring Boot orchestrator | `8081` | Avoid clash with Kafka UI |
| FastAPI AI engine | `8000` | |
| Angular | `4200` | |

---

## Target repo layout

```
intelligent-trade-exception-triage-engine/
├── plan.md
├── README.md
├── DEMO.md
├── docker-compose.yml
├── settings.gradle.kts
├── build.gradle.kts
├── producer/                 # Java Kafka mock feed
├── orchestrator/             # Spring Boot consumer + REST + AI client
├── ai-engine/                # FastAPI + LangGraph
├── ui/                       # Angular 19
├── infra/                    # Azure Bicep (OpenAI, Key Vault, optional App Service)
│   ├── main.bicep
│   ├── deploy.ps1
│   ├── cleanup.ps1
│   ├── modules/
│   └── params/
├── sample-data/              # Synthetic exception scenarios
├── scripts/
│   ├── start-stack.ps1       # preferred cold start
│   ├── stop-stack.ps1
│   ├── lib/                  # shared helpers
│   ├── services/             # per-process start/stop
│   └── smoke/                # API smoke checks
└── docs/                     # contracts, rubric, architecture
```

---

## Contracts (freeze in Phase 0)

### Kafka — `raw-trade-exceptions`

```json
{
  "tradeId": "TRD-10042",
  "counterparty": "ACME-BANK",
  "discrepancyType": "SSI_MISMATCH",
  "instrument": "ZN",
  "amount": 2500000.00,
  "currency": "USD",
  "side": "SELL",
  "detectedAt": "2026-08-12T13:00:00Z",
  "rawDetails": "Settlement account on affirm differs from SSI master"
}
```

### AI — `POST /api/v1/analyze-exception`

Request: same facts as above (plus optional `id` from DB).  
Response (Pydantic-bounded) — **LLM outputs qualitative fields only**; confidence is not an LLM field:

```json
{
  "severity": "HIGH",
  "recommendation": "Confirm SSI with counterparty ops; hold settlement.",
  "reasoning": "SSI mismatches frequently cause fails; amount and sell side raise break risk."
}
```

AI must not return recomputed `amount`, invent `tradeId`, or emit a self-graded `confidence`.

### Confidence (deterministic — `:orchestrator` Java only)

Computed in Java after Azure OpenAI analysis returns; persisted on the exception:

```json
{
  "confidenceScore": 0.78,
  "confidenceRubricVersion": "v1",
  "confidenceFactors": [
    { "code": "KNOWN_DISCREPANCY_TYPE", "weight": 0.35, "fired": true },
    { "code": "AMOUNT_BAND_HIGH", "weight": 0.20, "fired": true },
    { "code": "FIELDS_COMPLETE", "weight": 0.15, "fired": false }
  ]
}
```

### REST + SSE (orchestrator)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/exceptions` | List (filter by status) |
| `GET` | `/api/exceptions/{id}` | Detail + AI fields + confidence factors |
| `POST` | `/api/exceptions/{id}/resolve` | `{ "action": "APPROVE" \| "REJECT" \| "OVERRIDE", "notes"?: "...", "overrideRecommendation"?: "..." }` |
| `GET` | `/api/stream` | SSE: exception lifecycle events (`NEW`, `PENDING_REVIEW`, resolved, etc.) |
| `GET` | `/api/health` | Liveness |

SSE lives on the orchestrator (`:8081`), not a separate bridge process. Initial grid load via REST; live updates via SSE.

### Status machine

`NEW` → `ANALYZING` → `PENDING_REVIEW` → `RESOLVED` | `REJECTED` | `OVERRIDDEN`  
Failure path: `ANALYZING` → `ANALYZING_FAILED` (retryable).

### Synthetic scenarios (sample-data)

Aim for 5–8 fixtures covering: SSI mismatch, quantity break, price tolerance breach, late confirmation, missing LEI, currency mismatch, duplicate trade, counterparty unknown.

---

## Phase 0: Environment, decisions, scaffold

**Goal:** Lock tooling and contracts before writing feature code. Prove the toolchain end-to-end.

### Pre-0 / now: Azure OpenAI (required first)
- [x] Scaffold `infra/` (Bicep OpenAI + Key Vault, `deploy.ps1`, `cleanup.ps1`, params)
- [x] `.env.example` + `.gitignore` (`.env` never committed)
- [x] Azure CLI available; `az login` works
- [x] Run `.\infra\deploy.ps1` (or `-SkipWhatIf`) → repo-root `.env` written with keys
- [x] Confirm `.env` has `AZURE_OPENAI_*` and `AI_ENGINE_API_KEY`

### Tooling
- [ ] Docker Desktop available on Windows (start before `.\scripts\start-stack.ps1` or `.\scripts\services\start-kafka.ps1`)
- [x] Java 22 available on Windows
- [x] Gradle available on Windows (+ wrapper generated)
- [ ] Node / npm available on Windows (Angular 19) — needed by Phase 4
- [ ] Python 3.11+ available on Windows — needed by Phase 2

### Decisions (locked)
- [x] Build tool: **Gradle** (multi-project)
- [x] Java: **22**
- [x] Cloud AI: **Azure OpenAI via Bicep** — **required** (no stub / AI-optional path)
- [x] Frontend: **Angular 19**
- [x] HITL: **async review** of finished AI suggestion
- [x] Apps fail fast if Azure OpenAI env vars missing

### Scaffold (rest of Phase 0)
- [x] Fresh repo layout (`producer/`, `orchestrator/`, `ai-engine/`, `ui/`, `sample-data/`, `scripts/`, `docs/`)
- [x] Root Gradle multi-project (`settings.gradle.kts`: `:producer`, `:orchestrator`)
- [x] Compose baseline (Kafka + Zookeeper + Kafka UI); add PostgreSQL
- [x] Document ports in README stub
- [x] Write `docs/contracts.md` (Kafka event, AI request/response, REST, status enum, confidence factors)
- [x] Sketch `docs/confidence-rubric-v1.md` (signals, weights, version stamp — no LLM self-grade)
- [x] Author 5–8 synthetic exception JSON/CSV fixtures in `sample-data/`
- [x] Definition of done for the week written below (acceptance)

### Phase 0 acceptance
- [x] `.env` present from `.\infra\deploy.ps1` (OpenAI reachable)
- [x] `docker compose up -d` → Kafka UI reachable (`.\scripts\services\start-kafka.ps1`)
- [x] Empty Gradle modules build (`gradle build` OK)
- [x] Contracts + sample fixtures written (`docs/`, `sample-data/`)

---

## Phase 1: Infrastructure & Java ingest

**Goal:** Mock producer → Kafka → Spring Boot → PostgreSQL. No AI yet.

- [x] `docker-compose.yml`: Kafka, Zookeeper, Kafka UI, PostgreSQL (host **5433**)
- [x] `:orchestrator` Spring Boot 3 app (Web, Data JPA, Kafka, validation, Flyway)
- [x] `TradeException` entity + status enum; Flyway `V1__trade_exceptions.sql`
- [x] Kafka consumer on `raw-trade-exceptions` → map → persist (`NEW`) → commit/ack (**no AI call in this path**)
- [x] `:producer` Java app replaying `sample-data/` into the topic (delay-ms flag)
- [x] PowerShell: `scripts/services/start-kafka.ps1`, `start-orchestrator.ps1`, `start-producer.ps1`
- [x] Smoke: 8 rows in Postgres `status=NEW`; Kafka topic populated
- [x] Basic JUnit: JSON → entity mapping / status defaults (`TradeExceptionMapperTest`)

### Phase 1 acceptance
- [x] Script sequence proves end-to-end ingest with no AI/UI (`start-kafka` → `start-orchestrator` → `start-producer`)

---

## Phase 2: Python AI evaluation API

**Goal:** Probabilistic layer against real Azure OpenAI (infra already deployed in Pre-0).

- [x] Initialize `ai-engine/` FastAPI project (`requirements.txt`, uvicorn)
- [x] Load `.env`; **fail fast** if `AZURE_OPENAI_*` missing
- [x] `POST /api/v1/analyze-exception` with Pydantic models matching contracts
- [x] LangGraph (or single-node graph) calling Azure OpenAI:
  1. Categorize severity (`HIGH` | `MEDIUM` | `LOW`)
  2. Structured recommendation + reasoning (**no** self-graded confidence)
- [x] Secure with `X-API-Key` = `AI_ENGINE_API_KEY` from `.env`
- [x] Document confidence rubric in `docs/` (implemented by Java in Phase 3 — not by this API)
- [x] Scripts: `scripts/services/start-ai-engine.ps1`, `scripts/smoke/smoke-ai-engine.ps1`

### Phase 2 acceptance
- [x] Analyze endpoint returns severity/recommendation/reasoning against live Azure OpenAI (`.\scripts\smoke\smoke-ai-engine.ps1`)

---

## Phase 3: Spring integration, REST & SSE

**Goal:** Java orchestrates AI (async, post-commit), scores confidence, exposes HITL APIs + live desk feed.

- [x] After `NEW` commit: `TransactionalEventListener(AFTER_COMMIT)` + `@Async` → analyze worker (**never** call AI inside the Kafka consumer transaction)
- [x] Worker: short tx → `ANALYZING`; then HTTP to FastAPI **outside** any DB transaction (Java `HttpClient` HTTP/1.1, `AI_ENGINE_API_KEY`)
- [x] On success (fresh tx): store severity, recommendation, reasoning; run **Java** confidence scorer; → `PENDING_REVIEW`; SSE emit
- [x] Persist `confidenceScore`, `confidenceRubricVersion`, `confidenceFactors` (scorer strictly in `:orchestrator`)
- [x] On failure (fresh tx): `ANALYZING_FAILED`; log
- [x] REST endpoints for Angular (`GET` list/detail, `POST` resolve)
- [x] Resolve actions: `APPROVE` | `REJECT` | `OVERRIDE` with audit notes
- [x] SSE endpoint `GET /api/stream` — emit on ingest, AI complete, resolve
- [x] CORS for `http://localhost:4200` (REST + SSE)
- [ ] (Optional) Produce to `resolved-trades` on terminal states
- [x] `GET /api/health` includes DB + AI reachability
- [x] Scripts: `scripts/services/start-orchestrator.ps1` loads `.env`; `scripts/smoke/smoke-phase3.ps1`

### Phase 3 acceptance
- [x] Ingest commits/acks without waiting on AI; AI completes async → `PENDING_REVIEW`
- [x] Same fixture → same confidence score + factor breakdown (recomputable in Java; unit tested)
- [x] Resolve via REST → terminal status in DB (`TRD-PHASE3-3` → `RESOLVED`)
- [x] SSE hub wired (`GET /api/stream`) — desk will consume in Phase 4

---

## Phase 4: Angular HITL desk

**Goal:** Human-in-the-loop review dashboard with live SSE updates.

- [x] Angular 19 standalone app in `ui/`
- [x] `HttpClient` API service → orchestrator `:8081` (initial load + resolve actions)
- [x] SSE service (Angular Signals) → `GET /api/stream` for live queue updates
- [x] Dashboard grid (AG Grid v33) of active-queue exceptions
- [x] Detail panel: raw trade fields | AI severity / reasoning / recommendation | **confidence score + factor breakdown**
- [x] Actions: Approve AI suggestion, Reject, Manual Override → resolve endpoint
- [x] Desk states: Offline / Idle / Live
- [x] Offline / empty / error states
- [x] Script: `scripts/services/start-ui.ps1` → http://localhost:4200 (also via `start-stack.ps1`)

### Phase 4 acceptance
- [x] Desk builds (`ng build`); operator can load queue over REST, receive SSE upserts, Approve/Reject/Override against running stack

---

## Phase 5: Capstone polish

**Goal:** Cold-start demo in under 10 minutes; documented reference architecture.

- [x] `scripts/start-stack.ps1` / `stop-stack.ps1` — Docker + AI + orchestrator + UI + producer
- [x] Scripts organized: `scripts/lib`, `scripts/services`, `scripts/smoke`
- [x] README: cold start, ports, reference-architecture framing, **required** Bicep deploy + `.env`
- [x] `DEMO.md` guided tour
- [x] Architecture & design decisions in `docs/architecture.md`
- [x] `.env.example` complete; no secrets in git
- [x] Minimal tests: `TradeExceptionMapper` + `ConfidenceScorer` rubric math (JUnit)
- [ ] Optional stretch: status transitions, AI client timeout behavior

### Phase 5 acceptance
- [x] Cold start → live HITL desk with sample exceptions in one documented path (`.\scripts\start-stack.ps1`)

---

## Progress checklist

Working board — check as you go; phase sections above hold detail.

### Now / Next
- [ ] Optional stretch: Kafka `resolved-trades` producer; AI client timeout tests

### Backlog
- [ ] Optional: Kafka `resolved-trades` producer
- [ ] Optional: desk polish (sticky HITL actions, tooltips on truncated columns)

### Done
- [x] Baseline idea captured
- [x] Stack locked: Docker, Java 22, Gradle, Azure OpenAI Bicep (required), Angular 19
- [x] Thesis + HITL + auditable confidence written into this plan
- [x] `infra/` Bicep + `deploy.ps1` / `cleanup.ps1` + `.env.example` scaffolded
- [x] Pre-0 deploy → `.env` populated
- [x] Phase 0 scaffold: Gradle modules, Compose, contracts, sample-data, README, wrapper
- [x] `gradle build` successful
- [x] Phase 1: producer → Kafka → Spring → Postgres (8 × `NEW` smoked)
- [x] Phase 2: FastAPI + LangGraph + live Azure OpenAI analyze API
- [x] Phase 3: async AI + Java confidence + REST resolve + SSE (smoked)
- [x] Phase 4: Angular HITL desk (AG Grid + SSE Signals)
- [x] Phase 5: start/stop stack, DEMO.md, architecture doc, README reference framing

---

## Definition of done

A cold clone on Windows can:

1. Deploy Azure OpenAI via `deploy.ps1` and start the stack  
2. Replay synthetic breaks into Kafka  
3. See Spring persist + Azure OpenAI suggestion with Java confidence  
4. Approve / Reject / Override on the Angular desk  
5. Confirm terminal status in Postgres  

…with README/DEMO clear enough to re-run from the repo alone.
