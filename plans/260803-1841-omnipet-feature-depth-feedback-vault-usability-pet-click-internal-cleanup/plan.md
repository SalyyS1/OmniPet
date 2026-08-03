---
title: "OmniPet feature depth: feedback, vault usability, pet click, internal cleanup"
description: "Second-tier UX and logic work on shipped features: audible/visual feedback, an operator-owned gui config surface, vault search and sort, right-click pet interaction, and the DRY/test debt left by the first overhaul."
status: pending
priority: P1
effort: "4-6d"
tags: [ux, paper, gui, config, interaction, cleanup]
created: 2026-08-03
blockedBy: []
blocks: []
---

# OmniPet feature depth: feedback, vault usability, pet click, internal cleanup

## Overview

The previous plan fixed *discoverability* — help, tab-complete, catalog text, Studio prompts, non-italic lore, and a hub. This plan fixes *responsiveness and scale*: the plugin never makes a sound, never confirms an action visually, cannot be tuned by an operator, and becomes unusable at 100+ pets. It also pays down the debt that overhaul created.

Every item below is evidence-backed. Nothing here is speculative polish.

| Finding | Evidence | Player experience |
| --- | --- | --- |
| Zero audible/visual feedback plugin-wide | `grep playSound\|showTitle\|sendActionBar\|BossBar` over `omnipet-paper/src/main` → **0 matches** | Every click is silent. Activating a pet, buying a slot, and failing a purchase all feel identical. |
| No `gui:` config section | `config.yml` declares only `storage`, `runtime`, `progression`, `items`, `integrations` | Operators cannot change page size, prompt timeout, or help page length without a rebuild. |
| Vault has no search, filter, or sort | `grep -c "filter\|sort\|search" PlayerPetMenuRenderer.java` → **0**; Studio has both `SEARCH` and `STAT_SEARCH` | A player with 100 pets pages blindly through 3 screens. Staff get a search; players do not. |
| Countdown formatter duplicated | `HatchMenuRenderer.java:112` and `HubMenuRenderer.java:122` (both `86_400`) | Two copies drift. Introduced by the previous plan's Phase 6 — self-inflicted. |
| Hub slot tile returns to the wrong screen | `PlayerHubController.click` → `slotPurchases.open(player, 1)`; cancel path is `pet <returnPage>` | Cancelling a slot purchase opened from the hub lands the player in the vault, not the hub. **Bug.** |
| Hub has no in-flight guard | `PlayerHubController` → 0 matches for `mutations`/`inFlight`, vs 8-15 in every sibling controller | Spamming a hub tile queues repeated reads. Low severity (read-only), but inconsistent. |
| Interaction lookup exists and is **already wired** | `omnipet-core/.../runtime/InteractionIndex.java` — `resolve` `:27`, populated at `PetActivationService.java:107`, purged at `:129,141`, constructed at `PaperRuntimeBootstrap.java:30` with the reference **discarded**; zero `PlayerInteractEntityEvent` listeners repo-wide | Pets carry a working interaction hitbox and a working reverse lookup that nothing consumes. The gap is exposure plus a listener, not a lookup. |

## Verification record

This plan was red-teamed before approval. Four claims in the first draft were **false** and are corrected here:

| First-draft claim | Reality | Where corrected |
| --- | --- | --- |
| A new entity→owner index is needed | `InteractionIndex` already exists, is thread-safe, populated, purged, and constructed — only the reference is dropped. A new `PetEntityRef` would also have dropped `rendererGeneration`. | Phase 4 rewritten; effort 1d → 0.5d |
| `PlayerRequestTracker` can reject a second open | `begin()` unconditionally overwrites and returns a new token. It is a stale-completion filter, not a mutex. | Phase 1 uses `ConcurrentHashMap.newKeySet()` |
| The favorite flag may need a repository read | `PetManagementMetadata.read` reads it from `PetInstance.extensions()` — pure, in core, zero I/O. | Phase 3 hedge deleted |
| Chat search can reuse `StudioFieldPrompt` | It is package-private in `studio.bukkit`, and its machinery needs a `StudioViewToken`, session manager, and chat listener the vault does not have. | Phase 3 scoped to sort + click-filter |

Also corrected: the hardcoded-value count (3 → **14 literals**, six `Duration`/tick pairs), a pinned test string that the first draft promised would survive (`PlayerHubControllerContractTest:60`), a missing `encode()` update that would drop `gui:` on legacy migration, a missing `getHand()` guard that would double-open every pet click, an unsatisfiable `grep -c 86_400` criterion, and a false "compatibility untouched" claim.

## Goals

| # | Goal | Priority |
| --- | --- | --- |
| 1 | Every state change produces proportional audible + visual feedback, operator-tunable and fully disableable | P1 |
| 2 | A `gui:` config section owns page sizes, prompt timeouts, feedback toggles, and paging bounds | P1 |
| 3 | Vault supports sort and click-filter with honest empty and last-page states | P1 |
| 4 | Right-clicking your own rendered pet opens its management screen | P2 |
| 5 | The hub slot tile returns to the hub; the hub gains the in-flight guard its siblings have | P1 |
| 6 | Duplicated formatters and remaining enum-to-text paths collapse to one implementation | P2 |
| 7 | Named test blind spots gain direct coverage | P2 |

## Phases

| # | Phase | Status | Depends on |
| --- | --- | --- | --- |
| 1 | [Phase 1: Start](./phase-01-start.md) | Complete | — |
| 2 | [Phase 2: Feedback primitives and gui config](./phase-02-feedback-primitives-and-gui-config.md) | Complete | 1 |
| 3 | [Phase 3: Vault usability at scale](./phase-03-vault-usability-at-scale.md) | Complete | 2 |
| 4 | [Phase 4: Pet click interaction](./phase-04-pet-click-interaction.md) | Pending | 2 |
| 5 | [Phase 5: Internal cleanup and test gaps](./phase-05-internal-cleanup-and-test-gaps.md) | Pending | 2, 3 |
| 6 | [Phase 6: Docs and verification](./phase-06-docs-and-verification.md) | Pending | 2, 3, 4, 5 |

Phase 2 lands as **two commits** (2a config, 2b feedback) — it is too large for one.

Phase 4 is deliberately independent of Phase 3: it is the only *new feature* here and carries different risk (entity events, hand filtering, grief vectors). If it stalls, Phases 3 and 5 still ship. Its live-server precondition should be checked **before** writing the listener.

## Key constraints

- `omnipet-core` must not gain Bukkit/Paper/Adventure imports. `checkCoreBoundary` (`build.gradle.kts:17`) enforces this. All feedback, config, and listener code lands in `omnipet-paper`.
- **No new bundled dependency.** `Sound`, `Title`, and action bars are Paper/Adventure API already on the compile classpath. `checkDistributionArtifact` must stay green — nothing new may appear in the JAR.
- Feedback must be **fully disableable**. A server with its own sound design, or a player using a screen reader, must be able to turn it off. Default on, `gui.feedback.enabled: false` turns everything off in one switch.
- No change to escrow, journal, reservation, revision, generation, or queue semantics. Phase 4 adds a read-only lookup; it must not mutate runtime state.
- Files over 200 lines get split rather than grown. `OmniPetCommand` (594) and `PetStudioController` (583) are **not** to be touched by this plan.
- Sound must never be spammed. A per-player rate limit is required, or a fast-clicking player becomes an audible nuisance to everyone nearby.

## Accepted contract changes

1. `config.yml` gains a `gui:` section. It is **optional** — a config without it loads with today's hardcoded values, so existing installs are unaffected. It follows the lenient `messages.yml` precedent, not the strict `storage:` one, because a bad page size must not disable pets. `OmniPetConfigLoader.encode()` must serialize it, or a legacy migration silently drops it.
2. `gui.feedback.*` reloads live; `gui.vault.petsPerPage`, `gui.help.linesPerPage`, and `gui.studio.promptTimeoutSeconds` are **restart-only** and documented as such. The three consumers hold their collaborators in constructors with no reload hook, and adding mutable static state for `linesPerPage` would be worse than a documented restart.
3. `PlayerHubController.click` routes the slot tile through a return-to-hub path. This is a **bug fix**. `PlayerHubControllerContractTest:60` pins the buggy string and must be updated.
4. `PlayerPetMenuRenderer.render` gains a view-state parameter; `openVault` gains an overload. View state is **not** preserved across command round-trips (`pet`, `pet vault`, `pet slot <page>`) — accepted and documented rather than rewiring the money-handling purchase flow for a display concern.

## Success Criteria

- [ ] `gradlew.bat clean build --no-daemon --console=plain` passes with zero failures/errors/skips; all four guard tasks green.
- [ ] `grep -c playSound` over `omnipet-paper/src/main` is greater than zero, and every call site routes through `FeedbackService`.
- [ ] `gui.feedback.enabled: false` produces zero output through the recording `FeedbackOutput` fake.
- [ ] Deleting the `gui:` section entirely leaves behavior identical to today's hardcoded values.
- [ ] A legacy config migration does not drop `gui:`.
- [ ] A vault with 120 pets can be sorted favorites-first, level-desc, rarity-desc, name-asc, and recent, and filtered to favorites/active/stored — with no pet appearing twice or vanishing.
- [ ] An empty vault and a zero-match filter render **different** messages.
- [ ] The last page says it is the last page.
- [ ] One right-click opens exactly **one** management screen (off-hand event filtered).
- [ ] Right-clicking another player's pet does nothing.
- [ ] Cancelling a slot purchase opened from the hub returns to the hub.
- [ ] `grep -rl 86_400 omnipet-paper/src/main` returns exactly one file.
- [ ] Sound is rate-limited per player and the limiter is evicted on quit.

## Risks

| Risk | Mitigation |
| --- | --- |
| Sound spam becomes an audible griefing vector | Per-player rate limit with a configured floor, enforced in one `FeedbackService`; asserted by a burst test. Sounds play to the acting player only, never to nearby players. |
| Feedback annoys players who did not ask for it | Default volume conservative; one master switch; per-category toggles. Documented in `configuration.md`. |
| A `gui:` typo disables pets | Lenient loader following the `messages.yml` precedent: unknown key warns, bad value falls back to the default, never fatal. Explicitly **not** the strict `rejectUnknown` path. |
| Vault view state widens the holder and breaks click tests | View state is added as new fields with defaults; existing constructors keep working. `PlayerPetInventoryHolderTest` must pass unmodified. |
| Pet click opens someone else's pet | Ownership is verified against the handle's `ownerId`, not the clicked entity alone. Negative test required. |
| Pet click fires on a non-OmniPet entity | Lookup returns empty for unknown entity IDs; the listener returns without cancelling the event, so other plugins are unaffected. |
| Search on the main thread scans a large pet list | Filtering is in-memory over an already-loaded snapshot — no I/O. Bounded by `effectiveVaultCapacity`, which is capped at 100,000; a linear scan of that is acceptable off a click, but must not run per tick. |

## Out of scope

Riding, `RideService`, passive event triggers, Active Party, rename control, admin target mode, MMOItems identities, a live MythicMobs skill picker, and any live-server certification. This plan does not touch persistence, escrow, or reconciliation.

<!-- slug: omnipet-feature-depth-feedback-vault-usability-pet-click-internal-cleanup -->
