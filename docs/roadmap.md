# Roadmap

The Phase 1 Gradle/persistence foundation is implemented and verified. The execution plan remains in progress until later phases consume the shared registry transaction and add live gameplay evidence. Later phases must not be described as shipped until their code, tests, exact Paper matrix, migration fixtures, and live smoke evidence exist.

## Shipped foundation

- Gradle Wrapper-only build with dependency locks, Java 21, UTF-8, branding checks, core-boundary checks, Gradle-only checks, and one release artifact.
- Two-module boundary: `omnipet-core` owns domain/schema/migration/storage seams; `omnipet-paper` owns Paper bootstrap and distribution.
- Schema 2 player and pet definition envelopes with deterministic legacy pet-instance IDs, revision checks, raw-node preservation, and finite-value validation.
- Atomic write/backup, archive, canonical path/no-follow-link checks, quarantine, and fail-closed player recovery behavior.
- Legacy egg validation and idempotent semantic-hash journal at bootstrap.
- Minimal `/pet` command with `/pets` alias and `omnipet.general` default permission.
- Compile probes for exact Paper 1.21.x and 26.x coordinates. These remain probes, not runtime certification.

## Deferred phases

| Capability | Status | Boundary/acceptance note |
| --- | --- | --- |
| Admin Pet Studio GUI and Studio Save | Deferred to Phase 2 | Must use the staged registry transaction and rollback seam. |
| Incubation/hatching and rarity rolls | Deferred to Phase 3 | Must define online-time, escrow, idempotence, and migration semantics. |
| Multi-pet active slots, vault, and economy | Deferred to Phase 4 | Requires canonical activation intent and provider reconciliation. |
| Paper/ModelEngine renderers and smooth movement | Deferred to Phase 5 | ModelEngine class loading, cleanup, fallback, and exact-version tests required. |
| MythicMobs skills/interactions/riding | Deferred to Phase 6 | No direct MythicMobs adapter is shipped in Phase 1. |
| Progression, cultivation, release, and player management | Deferred to Phase 7 | Requires the earlier transactions and runtime handles. |
| Integration QA and public documentation release | Deferred to Phase 8 | Includes exact vendor/server smoke matrix and examples update. |
| GitHub Pages publication and maintenance | Deferred | A workflow file exists in the repository, but Pages publication is not Phase 1 acceptance or a support guarantee. |

## Non-claims

The current JAR does not ship a Studio menu, hatching loop, multi-pet slots, vault/economy provider, live renderer, MythicMobs skill system, ModelEngine adapter, or full gameplay API. Old configuration and integration examples are retained as future design/reference material and are explicitly non-authoritative until their phase lands.

## Release gates

Every roadmap feature needs focused unit tests, migration/rollback coverage, exact Paper compatibility evidence, clean boot without optional plugins where applicable, and a documented fallback/fail-closed path. Update the relevant docs only after those gates pass.
