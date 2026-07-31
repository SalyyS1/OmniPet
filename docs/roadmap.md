# Roadmap

The Gradle/persistence foundation, Admin Pet Studio slice, and Phase 4 storage/economy code slice are implemented and verified by automated tests. The execution plan remains in progress until later phases add live gameplay evidence. Later phases must not be described as shipped until their code, tests, exact Paper matrix, migration fixtures, and live smoke evidence exist.

## Shipped foundation

- Gradle Wrapper-only build with dependency locks, Java 21, UTF-8, branding checks, core-boundary checks, Gradle-only checks, and one release artifact.
- Two-module boundary: `omnipet-core` owns domain/schema/migration/storage seams; `omnipet-paper` owns Paper bootstrap and distribution.
- Schema 3 player and schema 2 pet definition envelopes with deterministic legacy pet-instance IDs, vault/active intent migration, revision checks, raw-node preservation, and finite-value validation.
- Atomic write/backup, archive, canonical path/no-follow-link checks, quarantine, and fail-closed player recovery behavior.
- Legacy egg validation and idempotent semantic-hash journal at bootstrap.
- Minimal `/pet` command with `/pets` alias and `omnipet.general` default permission.
- Schema 3 player vault with separate owned capacity and ordered active intent, live consecutive legacy permission resolution, safe overflow behavior, coalesced reads, and serialized per-player Paper I/O.
- Provider-neutral economy amount, reflection-safe Vault/PlayerPoints adapters, explicit provider-choice GUI, balance display, durable purchase/refund/entitlement-sync saga, LuckPerms precedence, and audited unknown-provider reconciliation.
- Conservative schema 1 purchase-journal completion migration, local-entitlement verification, schema 2 atomic rewrite/backup, opaque cursor paging, 16 KiB entry reads, bounded issue summaries, and dependency-aware provider invalidation with coalesced next-tick refresh.
- Compile probes for exact Paper 1.21.x and 26.x coordinates. These remain probes, not runtime certification.

## Deferred phases

| Capability | Status | Boundary/acceptance note |
| --- | --- | --- |
| Admin Pet Studio GUI and Studio Save | Shipped Phase 2 slice | D/C/B/A/S browser, dynamic MythicLib stat picker/manual fallback, draft editor, archive, exact-ID hard delete with reference blocking, clone-only authoring, guarded inventory/chat input, and shared atomic generation transaction. Full reference migration and live server UX smoke remain gates. |
| Incubation/hatching and rarity rolls | Deferred to Phase 3 | Must define online-time, escrow, idempotence, and migration semantics. |
| Canonical multi-pet active intent and vault admission | Shipped Phase 4 slice | Core schema/storage invariants and Paper player vault are present; live renderer reconciliation belongs to Phase 5. |
| Economy-backed slot purchases | Shipped Phase 4 code slice | Vault/PlayerPoints choices, LuckPerms policy, journal discovery, and audited reconciliation are implemented. Exact provider boot/disable and live inventory smoke remain release gates. |
| Paper/ModelEngine renderers and smooth movement | Deferred to Phase 5 | ModelEngine class loading, cleanup, fallback, and exact-version tests required. |
| MythicMobs skills/interactions/riding | Deferred to Phase 6 | No direct MythicMobs adapter is shipped in the current JAR. |
| Progression, cultivation, release, and player management | Deferred to Phase 7 | Requires the earlier transactions and runtime handles. |
| Integration QA and release certification | Deferred to Phase 8 | Includes exact vendor/server smoke matrix and release-grade examples. |
| GitHub Pages | Deployed from `main` | The public site is live at `https://salyys1.github.io/OmniPet/`; current branch changes publish only after merge and a successful workflow. This is not runtime support certification. |
| GitHub wiki | Not initialized | Wiki is enabled, but no initial wiki repository/page exists; repository Markdown remains authoritative. |

## Non-claims

The current JAR ships the definition Studio, player vault/active-intent slice, and slot purchase/recovery code, but not the hatching loop, live multi-pet renderer, MythicMobs skill execution, ModelEngine adapter, or full gameplay API. Provider runtime compatibility remains unclaimed until live smoke evidence exists.

## Release gates

Every roadmap feature needs focused unit tests, migration/rollback coverage, exact Paper compatibility evidence, clean boot without optional plugins where applicable, and a documented fallback/fail-closed path. Update the relevant docs only after those gates pass.
