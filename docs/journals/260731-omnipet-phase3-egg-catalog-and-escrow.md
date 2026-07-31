---
date: 2026-07-31
session: omnipet-phase3-egg-catalog-and-item-escrow
status: verified-core-checkpoint
---

# Journal: 2026-07-31 - OmniPet Phase 3 Egg Catalog and Item Escrow

## Context

Phase 3 extended the dependency-neutral incubation core with canonical egg definitions and a durable item-escrow contract. The Gradle-only greenfield rewrite remains authoritative: new behavior lives behind tested core/Paper boundaries, while the legacy plugin supplies migration evidence instead of a second runtime or build path.

## What Happened

- Canonical runtime eggs now use one schema-versioned file per ID under `plugins/OmniPet/eggs/*.yml`. Root `eggs.yml` remains read-only migration input, preventing legacy and new schemas from competing for authority.
- Egg loading validates filename/`eggId` identity, case-unique safe IDs, tiers, weighted candidates, compact/compound/ISO durations, 64 KiB files, and a 10,000-file catalog bound.
- Item escrow uses the incubation UUID as the transaction UUID and persists the exact hand, slot, material, nonce, fingerprint, expected amount, player revision, and durable stage.
- Multiple journal/service instances in one process share a lock keyed by canonical root and transaction UUID. Atomic create and expected-stage compare-transition make retries idempotent and reject identity mutation or stale transitions.
- Recovery requires an explicit inventory observation: matching item present, absent, or ambiguous. Durable removal/payment stages require proof of absence; ambiguity, `FAILED`, and contradictory incubation state stop at operator review.
- TDD established failing parser, codec, repository, saga, concurrency, size-bound, and crash-boundary cases before implementation. Green runs and review cycles then tightened cross-instance serialization, explicit item observation, fail-closed recovery, and the Windows case-sensitive fixture.
- Final Java 21 Gradle verification passed 65 suites and 239 tests: 157 core and 82 Paper. The release JAR is 930,379 bytes with SHA-256 `D2F14E803BF5FCCB1D6B1FCEB22130D926C76BD2AB4BCC006E5D3C3CD0EF0109`.
- AgentWiki publishing was skipped because no AgentWiki CLI or MCP capability was available.

## Reflection

The core now has conservative durable boundaries before Bukkit inventory code exists. That separation is valuable: crashes and retries can be reasoned about without pretending filesystem state and a live player inventory form one atomic transaction. The checkpoint is not a shipped hatch flow; Paper must still supply main-thread item observation/removal/refund and async persistence orchestration.

## Decisions Made

| Decision | Rationale | Impact |
| --- | --- | --- |
| Keep Gradle Wrapper as the sole build authority | One reproducible greenfield path avoids Maven/runtime drift | Build, CI, tests, and release evidence use Gradle only |
| Store canonical eggs in `eggs/*.yml` | One file per stable ID gives unambiguous schema and bounded loading | Root `eggs.yml` is migration-only |
| Use atomic create plus compare-transition | Concurrent retries must not overwrite identity or skip durable stages | Escrow operations are idempotent across repository instances |
| Require explicit inventory observation | Journal state alone cannot prove whether Bukkit removed the item | Ambiguous recovery fails closed for operator review |
| Keep Paper integration deferred | Bukkit APIs require main-thread access and lifecycle recovery | Core contracts are verified; live hatch gameplay is still pending |

## Next Steps

- Bind the egg repository and escrow journal in `omnipet-paper`.
- Add PDC identity/fingerprint utilities and exact main-thread item removal/refund.
- Implement the monotonic online-only coordinator, bounded checkpoints, join/quit/crash recovery, hatch commands/GUI, and capacity-safe live claim.
- Complete Paper/server smoke tests before marking Phase 3 complete.

## Unresolved Questions

- None for the verified core checkpoint; existing product decisions about the head-catalog vendor and optional world countdown preview remain open.
