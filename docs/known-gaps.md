# Known gaps (POC)

Honest scope notes for anyone running or extending this project. This is a **local Windows reference POC**, not a production platform.

## By design

| Gap | Rationale |
|---|---|
| **Azure OpenAI required** | No stub / fake-AI path — the point is a real qualitative model behind a hard boundary |
| **Windows PowerShell stack scripts** | Demo automation targets one OS; macOS/Linux would need shell ports |
| **Local DB password `itee`/`itee`** | Compose-only POC; not a cloud secret |
| **UI hardcodes `http://localhost:8081`** | Matches local CORS + `ng serve`; not a multi-env frontend build |

## Operational / engineering debt

| Gap | Notes |
|---|---|
| **No ingest idempotency** | Replaying Kafka / producer creates duplicate rows (same `tradeId`, new UUID). Use `start-stack.ps1 -FreshDb` for a clean desk |
| **Bounded AI pool rejection** | Extreme backlog can leave `NEW` until orchestrator restart (`AnalysisRecovery`) |
| **SSE without heartbeat** | Some proxies drop idle streams; desk has Reconnect |
| **No DLT for poison Kafka messages** | Bad payloads rely on default consumer error handling |
| **`resolved-trades` topic unused** | Config placeholder; terminal disposition is Postgres + desk today |
| **Confidence allow-lists in code** | Rubric v1 sets are in `ConfidenceScorer`, not externalized YAML |

## What the POC already demonstrates

- Java owns lifecycle + **recomputable** confidence (see JUnit on `ConfidenceScorer`)
- AI never returns confidence; analyze is outside the Kafka transaction
- HITL Approve / Reject / Override with audit fields in Postgres
- Cold path documented: `deploy.ps1` → `start-stack.ps1` → [DEMO.md](../DEMO.md)

See also [architecture.md](./architecture.md) and [contracts.md](./contracts.md).
