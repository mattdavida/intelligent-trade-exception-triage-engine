# ITETE contracts

Frozen for Phase 0. Implementations must match these shapes.

## Topics

| Topic | Direction | Purpose |
|---|---|---|
| `raw-trade-exceptions` | producer → orchestrator | Inbound exception events |
| `resolved-trades` | orchestrator → (optional) | Terminal HITL outcomes |

## Kafka event — `raw-trade-exceptions`

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

### `discrepancyType` taxonomy (v1)

| Code | Meaning |
|---|---|
| `SSI_MISMATCH` | Settlement instructions disagree |
| `QUANTITY_BREAK` | Qty / notional mismatch |
| `PRICE_TOLERANCE` | Price outside tolerance |
| `LATE_CONFIRMATION` | Affirm / confirm timed out |
| `MISSING_LEI` | Legal entity identifier absent |
| `CURRENCY_MISMATCH` | CCY disagree across legs/systems |
| `DUPLICATE_TRADE` | Suspected duplicate |
| `UNKNOWN_COUNTERPARTY` | Counterparty not on master |

### `side`

`BUY` | `SELL`

## Status machine

```
NEW → ANALYZING → PENDING_REVIEW → RESOLVED | REJECTED | OVERRIDDEN
                ↘ ANALYZING_FAILED (retryable)
```

| Status | Owner |
|---|---|
| `NEW` | Kafka ingest (commit/ack; no AI in tx) |
| `ANALYZING` | Async worker before/during AI call |
| `PENDING_REVIEW` | AI + Java confidence complete |
| `ANALYZING_FAILED` | AI/HTTP failure |
| `RESOLVED` / `REJECTED` / `OVERRIDDEN` | HITL resolve API |

## AI — `POST /api/v1/analyze-exception`

**Auth:** `X-API-Key: $AI_ENGINE_API_KEY`

**Request:** Kafka event fields (+ optional `id` from DB).

**Response** (LLM qualitative only — no confidence):

```json
{
  "severity": "HIGH",
  "recommendation": "Confirm SSI with counterparty ops; hold settlement.",
  "reasoning": "SSI mismatches frequently cause fails; sell side raises break risk."
}
```

`severity`: `HIGH` | `MEDIUM` | `LOW`

AI must not return recomputed `amount`, invent `tradeId`, or emit self-graded `confidence`.

## Confidence (Java `:orchestrator` only)

See [confidence-rubric-v1.md](./confidence-rubric-v1.md).

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

## REST + SSE (orchestrator `:8081`)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Aggregate readiness: `status` is `UP` / `DEGRADED` / `DOWN` from `db` + `aiEngine` |
| `GET` | `/api/exceptions` | List (filter by status) |
| `GET` | `/api/exceptions/{id}` | Detail + AI fields + confidence |
| `POST` | `/api/exceptions/{id}/resolve` | HITL action |
| `GET` | `/api/stream` | SSE lifecycle events |

### Resolve body

```json
{
  "action": "APPROVE",
  "notes": "optional",
  "overrideRecommendation": "optional when action=OVERRIDE"
}
```

`action`: `APPROVE` | `REJECT` | `OVERRIDE`

### SSE event (example)

```json
{
  "type": "EXCEPTION_UPDATED",
  "id": "…",
  "tradeId": "TRD-10042",
  "status": "PENDING_REVIEW",
  "severity": "HIGH",
  "confidenceScore": 0.78
}
```

Emitted on ingest (`NEW`), AI complete (`PENDING_REVIEW` / `ANALYZING_FAILED`), and resolve.
