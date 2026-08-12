# Confidence rubric v1

**Owner:** Java `:orchestrator` only.  
**Version stamp:** `v1` (persist as `confidenceRubricVersion`).

Not LLM self-grading. Score is derived from explicit signals, recomputable from
persisted exception fields + this rubric.

## Formula (naive POC)

```
score = clamp(0.0 .. 1.0, sum(weight for each fired factor))
```

Factors are additive. Missing negative factors simply do not add their weight
(or use explicit negative weights below).

## Factors

| Code | Weight | Fires when |
|---|---:|---|
| `KNOWN_DISCREPANCY_TYPE` | +0.35 | `discrepancyType` ∈ v1 taxonomy (`docs/contracts.md`) |
| `AMOUNT_BAND_HIGH` | +0.20 | `amount` ≥ 1_000_000 (USD-equivalent for POC; no FX) |
| `FIELDS_COMPLETE` | +0.15 | `tradeId`, `counterparty`, `instrument`, `currency`, `side`, `rawDetails` all non-blank |
| `COUNTERPARTY_KNOWN` | +0.15 | `counterparty` ∈ seed allow-list (orchestrator config) |
| `INSTRUMENT_KNOWN` | +0.15 | `instrument` ∈ seed allow-list (e.g. `ZN`, `ZB`, `ES`) |

If `KNOWN_DISCREPANCY_TYPE` does **not** fire, apply:

| Code | Weight | Fires when |
|---|---:|---|
| `UNKNOWN_DISCREPANCY_TYPE` | −0.25 | type not in taxonomy |

Clamp final score to `[0.0, 1.0]`.

## Seed allow-lists (v1)

**Counterparties:** `ACME-BANK`, `NORTH-CLEARING`, `PACIFIC-BROKER`, `EURO-DESK`, `LATAM-PRIME`  
**Instruments:** `ZN`, `ZB`, `ZF`, `ES`, `NQ`, `CL`

## Persistence

On each AI-success path, store:

- `confidenceScore` (double)
- `confidenceRubricVersion` (`"v1"`)
- `confidenceFactors` (JSON array of `{ code, weight, fired }`)

## UI

Desk shows score **and** factor breakdown (fired / not fired). Analysts see why,
not a naked float.

## Change control

Bump to `v2` when weights or signals change. Old rows keep their stamped version
for audit replay.
