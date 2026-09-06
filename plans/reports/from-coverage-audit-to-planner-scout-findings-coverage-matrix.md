# Coverage audit: scout findings vs release-readiness plan

From: coverage-audit. To: planner / team-lead. Advisory only — no code or plan file was modified.

Inputs: 5 scout reports in `plans/reports/from-scout-*` (126 finding rows + 51 upgrade bullets) against
`plans/260903-1813-release-readiness-bugfix-upgrade-wiki/` (`plan.md` + `phase-01` … `phase-10`; the
`phase-11`…`phase-19` entries a stale glob returned do not exist on disk — Read confirms).

Grading rule (mechanical, applied uniformly):

- **COVERED** — fix named in an Implementation Step, a Todo line, or a Related-Code-Files entry that states the fix (incl. a "P3 batch" step whose batch list names the file).
- **PARTIAL** — finding appears in a phase (Key Insights / Architecture / bare file list) but no fix instruction reaches Implementation Steps or Todo, so an implementer working the checklist would not execute it.
- **ORPHAN** — not named in any of the 10 phases.

## Summary

| Report | Findings | Covered | Partial | Orphan |
|---|---|---|---|---|
| scout-core | 24 | 19 | 2 | 3 |
| scout-sagas | 25 | 20 | 4 | 1 |
| scout-runtime | 26 | 22 | 3 | 1 |
| scout-ux | 22 | 15 | 1 | 6 |
| scout-wiki | 29 | 29 | 0 | 0 |
| **Total** | **126** | **105 (83%)** | **10 (8%)** | **11 (9%)** |

By severity:

| Severity | Findings | Covered | Partial | Orphan |
|---|---|---|---|---|
| P1 / High | 9 | 9 | 0 | 0 |
| P2 / Med | 40 | 36 | 4 | 0 |
| P3 / Low | 77 | 60 | 6 | 11 |

Headline: every P1 and every wiki High has a concrete step. All 11 orphans and all 6 P3 partials are
P3/Low; the 4 P2 partials are the real gap to close (one step or Todo line each).

Separately: **11 of 51 upgrade bullets** are picked up by no phase (see "Unpicked upgrade bullets").

## Closure status (re-verified against the phase files, 2026-09-04)

The planner has since folded this report's recommendations into the phase files. Re-verified item by
item against `phase-01`…`phase-10` + `plan.md` as they now stand on disk:

| Batch | Items | Folded in | Still open |
|---|---|---|---|
| P2 partials | 4 | 4 | 0 |
| P3 partials | 6 | 6 | 0 |
| Orphans | 11 | 11 | 0 |
| Sub-item gaps inside covered rows | 6 | 6 | 0 |
| Split candidates missing from Ph7 | 4 | 4 | 0 |
| Plan accuracy nits | 3 | 3 | 0 |
| Unpicked upgrade bullets | 11 | 9 | 2 (deliberate, post-release) |

Every finding row in the matrix below now has a step or Todo line behind it. The two deferrals are the
`MainThreadBridge` consolidation (ux) and the md-stub/wiki diff-check (wiki): neither is a defect, both
are refactors that would widen an already 17-day release. The tables below keep their original grading
(state at audit time) plus a "Landed as" note showing where each item went, so this file stays an audit
trail instead of turning into an open worklist.

## Orphans (11)

11 findings named in no phase. All P3/Low; none blocks the release gate, but each needs a decision
(fix, batch, or explicit waiver) so the plan's Goal 2 claim ("P3s fixed or ticketed") stays true.

| # | Report | Sev | file:line | What it is | Should own |
|---|---|---|---|---|---|
| O1 | core | P3 | `economy/FilePurchaseJournal.java:45-57` | `create()` writes over an existing record without the `sameIdentity` check its siblings do — a retried purchase with different args silently replaces the journal row | Ph2 (P3 batch) |
| O2 | core | P3 | `persistence/YamlPetDefinitionRepository.java:58,66,76,187-197,:28` | 3 directory scans per save, O(n²) reference scan, locks map never evicted — quadratic Studio latency at catalog scale | Ph2 (single-scan `readAll()`) or Ph7 |
| O3 | core | P3 | `persistence/AtomicFileStore.java:12-36` | No parent-directory fsync after rename; symlink TOCTOU on the temp path — crash can lose a rename that appeared durable | Ph2, or waive with reason (Ph9 durability drills would surface it) |
| O4 | sagas | P3 | `incubation/action/PaperIncubationItemActionCodec.java:137,187-194` | Two statements on one line; `supports()` re-deserializes the full action payload on every item check (hot path) | Ph3 (P3 batch) |
| O5 | runtime | P3 | `runtime/PaperRuntimePetState.java:225-233` | Per-tick vector/`Location` allocations in the state update path — GC pressure at 100 pets | Ph2's alloc bullet is core-only → Ph4, or waive to Ph9 spark numbers |
| O6 | ux | P3 | `config/OmniPetConfigLoader.java:363-367` | `numberMap` hardcodes the `formulaSamples.` prefix, so any other numeric map decodes under the wrong error prefix | Ph5 |
| O7 | ux | P3 | `command/AdminPetCommandParser.java:55-60` | Permission checked before argument validation → a mistyped subcommand reports "no permission" instead of usage | Ph5 (tree-derivation step) |
| O8 | ux | P3 | `command/PlayerCommandRouter.java:84-88,113` | `IllegalArgumentException` catch too wide: any IAE from the saga is reported as "bad UUID"; `slotPurchases` dereferenced unguarded | Ph5 |
| O9 | ux | P3 | `command/SlotTransactionAdminCommandParser.java:33-37` | Accepts non-canonical UUID spellings, so admin transactions can key on a form the repos never wrote | Ph5 |
| O10 | ux | P3 | `gui/player/PlayerPetMenuListener.java:21-25,58,77` | Legacy null-accepting constructor still present; null collaborators surface as NPEs mid-click | Ph5 (its "dead surfaces removed" step) |
| O11 | ux | P3 | `studio/bukkit/PetStudioController.java:503` | Two statements on one line (style-only) | Ph5 or Ph7 |

Cheapest close: O6–O11 are all Phase 5 files already open in that phase — six lines added to its P3
batch. O1/O2 land in Phase 2's existing P3 batch. O3 and O5 are the only two with a real "waive"
argument (both are cost/robustness, both would be observed by Phase 9 rather than fixed by it).

**Landed as** (verified on disk, all eleven): O1 → Ph2 P3 batch, `FilePurchaseJournal.create` gains the
`sameIdentity` check · O2 → Ph2 P3 batch, single-scan `readAll()` + cached `scan()` + evicting `locks`
map · O3 → Ph2 P3 batch, `AtomicFileStore` parent-directory fsync on non-Windows · O4 → Ph3 P3 batch,
cache the PDC decode instead of re-deserializing per `supports()` · O5 → Ph4 step 9, conditionally —
"reuse scratch instances only if the change stays obviously safe; otherwise leave it and let Phase 9's
spark numbers decide" (the waiver, made explicit rather than silent) · O6–O9 and O11 → Ph5 Related-files
P3 batch, Ph5 step 8, and the Ph5 Todo line · O10 → Ph5 Delete list plus step 7.

## P2 partials (4) — the lines that needed adding

These were the only gaps worth promoting before implementers split the tracks. Each row is the exact
one-line step that was missing, and where it has since landed.

| # | Report | file:line | Line to add | Phase | Landed as |
|---|---|---|---|---|---|
| P-1 | core | `economy/FilePurchaseJournal.java:20,119-122` | "Evict per-player lock entries in `FilePurchaseJournal` and `CultivationItemActionFileJournal` — move both onto the refcounted, evicting `EggEscrowLockRegistry` shape; test that an entry disappears once its last holder releases." | 2 | step **2b**, verbatim, + Todo "Journal lock maps refcounted + evicting (purchase, cultivation)" |
| P-3 | sagas | `PlacedEggView.java:84-85,108-113`; `PlacedEggHolograms.java:54` | "`PlacedEggView.refresh` skips records whose chunk is unloaded; holograms (re)spawn on `ChunkLoadEvent`; test both." | 3 | step **1b**, with the boot-stall rationale spelled out, + Todo |
| P-4 | sagas | `release/ReleaseAdminController.java:74-87` | "`ReleaseAdminController.list` defaults to online players with an `--all` opt-in and a bounded scan." | 3 | folded into step **6** + Todo "Admin `release list` online-default + bounded scan" |
| P-10 | ux | `command/OmniPetCommand.java:390` | "Render the `:390` rejection enum through the message catalog (add key + vi.yml) — the `sendMessage` sweep does not reach it." | 5 | step **4** now names it explicitly with that reasoning |

## P3 partials (6)

Same shape, lower stakes. All six also landed; details in the combined table below.

| # | Report | file:line | Landed as |
|---|---|---|---|
| P-2 | core | `FilePlayerStateRepository.java:135-140,142-144`; `YamlPetDefinitionRepository.java:213-214` | Ph2 step **3b** — quarantine only on codec-classified decode failure, + injected-transient test, + Todo |
| P-5 | sagas | `OmniPetPlugin.java:425-459`; `PlacedEggCoordinator.java:268-270` | Ph3 step **1** owns `invalidateDefinitions()`; Ph6 step 7 now defers to it by name instead of hedging |
| P-6 | sagas | `ReleaseAdminController.java:110,121` | Ph3 step **6** — reconcile records the acting sender, parity with `SlotTransactionAdminController:138` |
| P-7 | runtime | `PaperActiveSkillController.java:224,226-228,319,557-563` | Ph4 step **3b** (new) — per-pet cap, reindex log-once, `ThreadLocalRandom`, pending-reservation doc, + a success-criteria line |
| P-8 | runtime | `SkillTriggerListener.java:132` | Ph4 step **2** — projectile shooter unwrapped, + a success-criteria line for bow/trident |
| P-9 | runtime | `ReflectiveMythicLibBuffPort.java:83-91` | Ph4 step **3** — modifier diffing by deterministic UUID |

## Partials (combined detail, as graded at audit time)

Named in a phase but with no instruction on the Steps/Todo path.

| # | Report | Sev | file:line | What the plan says | Concrete step missing |
|---|---|---|---|---|---|
| P-1 | core | P2 | `economy/FilePurchaseJournal.java:20,119-122` | Ph2 Related files: "reuse `EggEscrowLockRegistry`-style eviction". Phase Overview lists only 7 of the 8 core P2s and omits this one | No Implementation Step and no Todo line — Ph2 step 2 covers `SharedRepositoryLockRegistry` only. Add "evict per-player lock entries in `FilePurchaseJournal` + `CultivationItemActionFileJournal`" as a step with a refcount test |
| P-2 | core | P3 | `FilePlayerStateRepository.java:135-140,142-144`; `YamlPetDefinitionRepository.java:213-214` | Ph2 Key Insights: "Quarantine-on-any-RuntimeException in read paths … amplifies every transient fault into permanent player lockout" | Nothing in Steps/Todo. The ThreadLocal fix removes the *known* trigger but leaves the policy; add "quarantine only on codec-classified decode failure; other RuntimeExceptions propagate" + a test that an injected transient IOException does not quarantine |
| P-3 | sagas | P2 | `PlacedEggView.java:84-85,108-113`; `PlacedEggHolograms.java:54` | Ph3 Related files: "`PlacedEggHolograms.java` (chunk-loaded guard in `refresh`, respawn on `ChunkLoadEvent`)" | Not in Steps or Todo; the sync chunk-load at enable is a startup stall on large egg counts. Add a step + a test that `refresh` skips unloaded chunks and a `ChunkLoadEvent` respawns |
| P-4 | sagas | P2 | `release/ReleaseAdminController.java:74-87` | Ph3 Related files: "(online-default list, actor in reconcile evidence)" | No step/todo; `list` still does a locked read per player. Add "default `list` to online players, bound the scan" to Ph3 step 6 |
| P-5 | sagas | P3 | `OmniPetPlugin.java:425-459`; `PlacedEggCoordinator.java:268-270` | Ph3 Related files: "`invalidateDefinitions()` after reload"; Ph6 hedges "if not already landed in Phase 3" | Neither phase owns it in Steps/Todo — a reload keeps serving stale definitions. Assign to Ph3 step 1 explicitly so Ph6's hedge resolves |
| P-6 | sagas | P3 | `ReleaseAdminController.java:110,121` vs `SlotTransactionAdminController.java:138` | Ph3 Related files: "actor in reconcile evidence" | No step; add to Ph3 step 6 (one-line change, but it is the audit trail for a money-adjacent command) |
| P-7 | runtime | P3 | `PaperActiveSkillController.java:224,226-228,319,557-563` | Ph4 Related files: "(per-pet MAX_BINDINGS, reindex log-once, `ThreadLocalRandom`)" | Not in Steps/Todo. `MAX_BINDINGS` counted globally instead of per pet is a behavior cap bug, not a cleanup — promote to a step. `runMain` while disabled leaving a pending reservation is undocumented either way |
| P-8 | runtime | P3 | `SkillTriggerListener.java:132` | Ph4 Related files: "(gates, MAX_HEALTH strategy, projectile shooter unwrap)" | No step/todo for the shooter unwrap — bow/trident kills currently miss the owner attribution the new trigger gates assume |
| P-9 | runtime | P3 | `ReflectiveMythicLibBuffPort.java:83-91` | Ph4 Related files: "(per-buff validation, modifier diffing)" | Step 3 covers per-buff validation only; the re-register-unchanged-modifiers churn has no step |
| P-10 | ux | P2 | `command/OmniPetCommand.java:390` | Ph5 Related files: "raw enum at :390 rendered via catalog" | Ph5 step 4 migrates the 83 `sendMessage` literals; this is an enum-name render, not a literal, so the step does not reach it. Add it to step 4's scope explicitly |

## Coverage matrix

P3/Low rows are grouped by owning phase-batch; `·` separates keys inside a grouped row.

### scout-core (24)

| Sev | file:line key | Phase | Covered by |
|---|---|---|---|
| P1 | `persistence/YamlDocuments.java:14-15` | 2 | "ThreadLocal Yaml + parallel-load test" |
| P2 | `SharedRepositoryLockRegistry.java:52-65` | 2 | "Lock registry: owner thread, bounded wait, nested-acquire error + tests" |
| P2 | `FilePlayerStateRepository.java:127` · `YamlPetDefinitionRepository.java:203` | 2 | "BoundedFiles helper adopted by repos/journals" (4 MiB / 1 MiB) |
| P2 | `YamlPetDefinitionFiles.java:73` | 2 | "Skip-and-report stray files in 3 scanners" |
| P2 | `economy/FilePurchaseJournal.java:20,119-122` | 2 | **PARTIAL** — Related files only (P-1) |
| P2 | `incubation/FileEggEscrowJournal.java:73-101` | 2 | "Escrow terminal-record archival (`done/`)"; 10k bound becomes truncation warning |
| P2 | `incubation/HatchService.java:168-174,:111` | 2 | "Token-cap exemption for cancel/complete" |
| P2 | `RepositoryProgressionService.java:89-98` · `ProgressionService.java:84-96` · `ReleaseIdentity.java:16-24` | 2 | "Stamina no-op at cap + fingerprint exclusion" |
| P2 | `release/ReleaseOutboxDeliveryService.java:21-45` | 2 | "INTERNAL_ATTEMPTING before internal delivery" |
| P3 | `ReleaseOutboxStore.java:59-62,115-134` · `PurchaseJournalScanner.java:64-65,73-74` · `CultivationItemActionFileJournal.java:88-108` | 2 | "corrupt-outbox statuses"; "`PurchaseJournalScanner` null-filter"; step 4 "three scanners" |
| P3 | `DeterministicHatchRollService.java:65-79,130-144` · `ProgressionService.java:39,112-115` · `SkillBindingProjection.java:90-92` · `SkillBinding`/`RepositorySkillActionService.java:95` · `RawNodeValues.java` · `EffectBudget.java` · `MovementController`/`IdleBehaviour.java:229-232` · `ReleaseOutboxDeliveryService.java:108` | 2 | step 9 "P3 batch (hatch-roll ordering + algorithm bump, EXP saturation, bindingId, cooldown caps, RawNodeValues, delete EffectBudget)" + "recursion → loop" |
| P3 | `FilePlayerStateRepository.java:135-140` (quarantine policy) | 2 | **PARTIAL** — Key Insights only (P-2) |
| P3 | `FilePurchaseJournal.java:45-57` · `YamlPetDefinitionRepository.java:58,66,76,187-197` · `AtomicFileStore.java:12-36` | — | **ORPHAN** (O1, O2, O3) |

### scout-sagas (25)

| Sev | file:line key | Phase | Covered by |
|---|---|---|---|
| P1 | `PlacedEggView.java:217-220` · `PlacedEggCoordinator.java:172` · `PlacedEggSupportController.java:152-163` · `OmniPetPlugin.java:475,480-487` | 3 | "Ready-egg retry pass + spend hook + failure messaging"; "READY break grants pet + CLAIM menu action" |
| P1 | `PlacedEggListener.java:87-108` (dup) · `:117-134` (grief) | 3 | "MONITOR persistence + cancel re-check + creative handling"; "isEggBlock + owner/admin gate + MONITOR reclaim" |
| P1 | `SlotUnlockService.java:94,255` · `PaperProviderCallExecutor.java:47` · `IncubationActionItemController.java:71-73,89` | 2,3 | "Provider calls outside player lock; future.cancel on timeout"; Ph2 bounded lock wait |
| P2 | `PaperProviderCallExecutor.java:47-57` | 3 | step 4 "executor future-cancel … uncancellable → UNKNOWN" |
| P2 | `PlacedEggListener.java:117-134` (protection cancel) | 3 | step 2 "tests simulate a HIGHEST canceller: … no reclaim on cancelled break" |
| P2 | `PlacedEggView.java:84-85,108-113` · `PlacedEggHolograms.java:54` | 3 | **PARTIAL** — Related files only (P-3) |
| P2 | `FileReleaseRewardMailbox.java:63-82,92-95,133` | 3 | "Join-time release outbox + mailbox recovery; mailbox archival" (+ truncation flag, corrupt tolerance) |
| P2 | `OmniPetManagementServices.java:157-159` · `ReleaseOutboxDeliveryService.java:130-142` | 3 | step 6 join-time async recovery `delivery.pending(playerId, N)` |
| P2 | `ReleaseAdminController.java:74-87` | 3 | **PARTIAL** — Related files only (P-4) |
| P2 | `IncubationActionItemController.java:69-99` · `IncubationItemActionCoordinator` | 3 | "Action-item redeem off main thread" |
| P2 | `PlacedEggListener.java:117-134` (no owner check) | 3 | "isEggBlock + owner/admin gate"; `PLACED_EGG_NOT_OWNER` |
| P3 | `EggAdminController.java:342-344` · `BukkitReleaseInventoryGateway.java:39-47,60-77` · `PaperReleaseInternalRewardPort.java:64-66,89` | 3 | "InternalRewardPort INVENTORY_CLAIMING self-heal (PDC evidence) + stack cap"; "propagate grant failure detail" |
| P3 | `PaperIncubationRecoveryExecutor.java:186-206,217-229` · `PaperEggItemCodec.java:56-67` · `PaperPlayerEggInventory.java:47-50` · `PlayerSlotPurchaseController.java:125` · `PaperReleaseExternalRewardPort.java:36-48` · `PlayerHatchController.java:186-187,295-301` · `PaperIncubationItemActionInventory.java:114-175` | 3 | step 7 "P3 batch with focused tests (legacy stack nonce split, cursor slot, refund message)" + Related-files batch (re-snapshot revision, balance read on main, evict attempts, clear mutations, merge scan helper) |
| P3 | `OmniPetPlugin.java:425-459` (`invalidateDefinitions`) · `ReleaseAdminController.java:110,121` | 3 | **PARTIAL** — Related files only (P-5, P-6) |
| P3 | `PaperIncubationItemActionCodec.java:137,187-194` | — | **ORPHAN** (O4) |

### scout-runtime (26)

| Sev | file:line key | Phase | Covered by |
|---|---|---|---|
| P1 | `ReflectiveMythicMobsIdentity.java:42,49-52,61-64` | 4 | step 1 — plugin classloader bind + refresh; "MythicMobs kill EXP dead everywhere" |
| P1 | `SkillTriggerListener.java:76,107,113,118,133,136,147,160,165,176` · `PaperActiveSkillController.java:122-130,137-160,211-228` | 4 | "`hasTrigger` gates" + `SkillTriggerIndex` fan-out fix |
| P2 | `PaperModelEngineRenderer.java:100-101,134-137,208-214,267-279` · resolver `:34-36` | 4 | step 3 quarantine split — per-input failure vs bridge-broken |
| P2 | `ReflectiveMythicLibBuffPort.java:103-106,:287` | 4 | step 3 (per-buff validation; typo no longer quarantines all buffs) |
| P2 | `PaperRuntimeBootstrap.java:62` · core `PetActivationService.java:119-135` | 4 | step 5 — new `RateLimitedWarnings.java` |
| P2 | `PaperSkillTargetResolver.java:90-103` | 4 | step 6 — nameplate/pet-entity exclusion + sphere filter |
| P2 | `PlayerStorageLifecycleListener.java:72-75` · `PaperPetRuntimeCoordinator.java:168-173` | 4 | step 7 — same-world teleport relocation + kick/quit idempotency |
| P2 | `PaperModelEngineRenderer.java:196-202` | 4 | step 7 "triple-teleport gating" |
| P2 | `OmniPetPlugin.java:332-335,455-458` | 4 | step 9 — stack traces + `LinkageError` catch |
| P2 | `SkillTriggerListener.java:197` (`Attribute.GENERIC_MAX_HEALTH`) | 4 | step 8 version-agnostic max-health; `plan.md` binding decision (Paper 1.21.x broad) |
| P2 | `ReflectiveMythicMobsSkillProvider.java:110-121,197-210,:115` | 4 | step 4 — MethodHandle cache per refresh epoch + overload resolution by signature |
| P2 | `MythicMobsSkillLifecycleListener.java:114-128,:81` | 4 | step 9 — listener unregister on disable |
| P3 | `PaperOwnerBuffCoordinator.java:95,96-101,180-183,211-213` · `CarrierMotion.java:72-76` · `ReflectiveModelEngineAnimationBindings.java:83-88` · `BukkitPaperHeadRendererBackend.java:203-219` · `PaperHeadRenderer.java:151-167` · `PaperModelEngineRenderer.java:89-90` | 4 | "Render P3s (tilt, yaw wrap, retire dedupe, generation check)" + step 3 (per-instance failed flag) + Related files (formatting, warnOnce concurrency, dead ctor) |
| P3 | `KillExperienceListener.java:86-90` · `OptionalAdapterLoader.java` · `OmniPetPlugin.java:461-472,513-522,530-546` | 4 | "Dead code removal + logging polish" (Delete list) |
| P3 | `paper-plugin.yml:5,56-79` · `OmniPetCommand.java:483` · `OmniPetCommandTree.java:84` | 4,6,10 | Ph4 permissions dedupe; Ph6 dedicated `omnipet.admin.stats`; Ph10 owns the stale `description` (explicitly deferred in Ph4) |
| P3 | `PaperActiveSkillController.java:224,226-228` · `SkillTriggerListener.java:132` · `ReflectiveMythicLibBuffPort.java:83-91` | 4 | **PARTIAL** — Related files only (P-7, P-8, P-9) |
| P3 | `PaperRuntimePetState.java:225-233` | — | **ORPHAN** (O5) |

### scout-ux (22)

| Sev | file:line key | Phase | Covered by |
|---|---|---|---|
| P2 | `gui/MenuLayout.java:91-97,131-145,159-169` | 5 | step 1 — layout validation, dead button fixed |
| P2 | `OmniPetCommand.java:557-566` vs `OmniPetAdminCommand.java:63-75` | 5 | step 3 — "canUse/suggestions/help derive from tree" |
| P2 | 83 hardcoded `sendMessage` literals across 13 files | 5 | step 4 — "Catalog migration of 83 literals" + vi.yml keys + parity + literal-scan test |
| P2 | `OmniPetCommand.java:390` (raw enum) | 5 | **PARTIAL** — Related files only (P-10) |
| P3 | `render/Nameplate.java:59,89` · `text/Messages.java:73-81` | 5 | step 5 — MiniMessage escaping of pet custom names |
| P3 | `config.yml:8-10` · `OmniPetConfigLoader.java:89-95` (`nameplateStatus`) · `GuiConfigLoader.java:194-204` | 5 | step 6 — encode `nameplateStatus`, reload warnings for render/gui, float-truncation warning (docs side in Ph8) |
| P3 | `OmniPetCommandTree.java:33-35,82-84,97-98` · `CommandSuggestions.java:54-63,69-71` | 5 | step 3 — add `slot [page]`, `stats all`; `visible()` honored in walk |
| P3 | `gui/hatch/HatchMenuListener.java:22-30` | 5 | step 7 — ClickType filter |
| P3 | `FoundationCommand` · `COMMAND_FOUNDATION_READY` · `omnipet.admin.explore` | 5 | step 7 — dead command surfaces removed |
| P3 | `PetManagementMenuRenderer.java:183-228` | 5 | step 2 — fold duplicate layout engine into `MenuLayout` |
| P3 | `OmniPetCommand.java:510-513` (online-only stats) · `PetStudioController.java:99` (15-min timeout) | 6 | "offline stats + `omnipet.admin.stats`"; "`gui.studio.sessionTimeout`" |
| P3 | `docs/commands-and-permissions.md:37-41` | 5,8 | Ph8 step 2 generated `wiki-commands.json` + drift check |
| P3 | `OmniPetConfigLoader.java:363-367` · `AdminPetCommandParser.java:55-60` · `PlayerCommandRouter.java:84-88,113` · `SlotTransactionAdminCommandParser.java:33-37` · `PlayerPetMenuListener.java:21-25,58,77` · `PetStudioController.java:503` | — | **ORPHAN** (O6–O11) |

### scout-wiki (29) — all covered by Phase 8 unless noted

| Sev | file:line key | Phase | Covered by |
|---|---|---|---|
| High | `wiki-content.js:428,446,450-452` (+VI) escaped-pipe table break | 8 | "h3 + table-escape renderer fixes + verify coverage" |
| High | `wiki-content.js:726,757,776,794` (+VI) `###` literal | 8 | same — `### ` support in `renderMarkdown` |
| High | `README.md:3,47` stale shipped claims | 8 | "Stale-claim cluster rewritten (README, getting-started, roadmap, landing)" |
| Med | `wiki-content.js:433-452` missing `/pet admin stats` row · `:441` reducer args + cultivation perm · `:422-431` missing `/pet <page>` (Low) | 8 | step 2 `wiki-commands.json` generated from `OmniPetCommandTree`; Related files "admin stats row, reducer args, cultivation perms", "`/pet <page>`" |
| Med | `wiki-content.js:509-1158` ~9 config sections absent · `:589-593` reload table missing `render`/`idle-play` · `config.yml` header circular authority (Low) | 8 | step 3 "Config-key coverage check + missing sections added EN+VI"; Related files `config.yml` header comment |
| Med | `paper-plugin.yml:5` stale description | 10 | Ph10 release-cut metadata (Ph4 defers it there explicitly) |
| Med | `wiki-content.js:115` vs `:1875` multi-pet contradiction · `:1877` MythicLib "Deferred" | 8 | step 4 "multi-pet precision; MythicLib buffs → Shipped with scope note" |
| Med | `configuration.md:13` vs `getting-started.md:84` egg-catalog restart-vs-hot-read | 8 | "Egg-catalog restart-vs-hot contradiction resolved from code" |
| Med | `README.md:69`, `getting-started.md:27,39`, `wiki-stats.json` — 3 conflicting test counts + stale SHA/stats | 8 | step 4 "drop test counts + JAR SHA from prose in favor of the generated stats surface" + CI stats pipeline |
| Med | `README.md:87` · `getting-started.md:5,50` · `roadmap.md:5,53` | 8 | step 4 stale-claim rewrite (all three files named) |
| Med | `wiki.html:68-76` + `wiki.css:678` stats panel hidden <1180px | 8 | "stats reachable on mobile" — panel moves into `article-foot` |
| Med | `.github/workflows/pages.yml` paths filter; `build-wiki-stats.mjs` never run | 8 | step 6 "stats generation in build.yml, consumption in pages.yml" |
| Low | `index.html:6,39` meta/hero jargon · no OG/Twitter/favicon/404 · version in 3 places · no language switch | 8 | step 5 "Landing: meta/OG/favicon/404/version/language toggle" (+Ph10 for the version bump source) |
| Low | `wiki.html` hardcoded EN chrome · `wiki.js:607` scroll-spy offset · Google Fonts external | 8 | step 5 "Chrome i18n + … scroll-spy offset … Google Fonts self-host" |
| Low | `tools/verify-wiki-render.mjs` 5 missing checks · `tools/verify-wiki-browser.mjs` no axe/keyboard/375px · Playwright no cache | 8 | Related files "(5 new checks)"; step 7 "Browser verify extensions (axe, keyboard, mobile landing)"; step 6 Playwright cache |

## Sub-item gaps inside covered rows

Row-level COVERED, but a named sub-item has no instruction. Low stakes; listed so they are not lost.

| Report | Row | Sub-item with no step |
|---|---|---|
| core | `ReleaseOutboxStore.java` | `:184-188` `longValue()` truncation of oversized epoch millis |
| core | `CultivationItemActionFileJournal.java` | no `MAX_SCAN_FILES` bound; `synchronized` lock model not aligned with the other journals |
| sagas | `FileReleaseRewardMailbox` | `findByPlayer` still scans after archival — no per-player index |
| runtime | `OmniPetCommandTree.java:97-98` | stale comment left by the tree drift fix |
| wiki | chrome i18n | language choice not persisted across pages |
| wiki | landing | VI hero/landing copy still English after the toggle lands |

**Landed as** (all six, verified on disk): the `ReleaseOutboxStore:184-188` `longValue()` truncation is now
called out inside Ph2 step 8 · `CultivationItemActionFileJournal`'s missing `MAX_SCAN_FILES` and unaligned
`synchronized` are in Ph2 step 5 · the mailbox per-player index is in Ph3's Architecture and Todo, with the
explicit note that "archival alone only shrinks the scan" · the `OmniPetCommandTree:97-98` stale comment is
in Ph5 step 3 · language persistence and VI hero copy are both in Ph8 step 5.

## Split-list coverage (files >200 lines)

Phase 7 names 17 priority splits and adds a sweep ("no file this plan touched still exceeds ~200 lines
without recorded justification"). Four files the scouts nominated are absent from the named list and
are the largest such omissions:

| File | Lines (scout) | Note |
|---|---|---|
| `studio/bukkit/PetStudioController.java` | 652 | Largest paper file; touched by Ph5 (22 message literals) and Ph6 (session timeout) |
| `command/OmniPetCommand.java` | 576 | Touched by Ph5 (tree derivation, enum render) |
| `persistence/PlayerStateYamlCodec.java` | 224 | Core scout nominated it explicitly |
| `persistence/YamlPetDefinitionRepository.java` | 247 | Also orphan O2 |

**Landed as:** Phase 7's priority-split list now names 21 files instead of 17 — all four are in, each with
the cross-phase reason attached (`PetStudioController` "touched by Phases 5 and 6", `OmniPetCommand`
"touched by Phase 5", `YamlPetDefinitionRepository` "also gets the single-scan `readAll()` in Phase 2",
`PlayerStateYamlCodec` "legacy v1-v3 decode into its own unit").

## Unpicked upgrade bullets (11)

11 of the 51 "Upgrade opportunity" bullets across the five reports were picked up by no phase at audit
time. Nine have since been folded in; the closure line after the table names where, and which two are
deliberate post-release deferrals.

| Report | Upgrade bullet | Nearest phase | Gap |
|---|---|---|---|
| core | Single-scan `readAll()` for pet definitions | 2 | Ph2 does scan *hygiene* only; the 3-scans-per-save and O(n²) reference scan stay (same as O2) |
| core | Journal-lifecycle archival for purchase + cultivation-action journals | 2 | Ph2 archives the escrow journal only |
| core | Unify `FilePurchaseJournal` + `CultivationItemActionFileJournal` onto `EggEscrowLockRegistry` | 2 | Only the hint in Related files (same as P-1) |
| sagas | Per-player mailbox index for `findByPlayer` | 3 | Archival reduces the scan, does not index it |
| runtime | Paper API drift guard in CI (compat compile target / version matrix) | 4/9 | Ph4 greps for other renamed constants once; Ph9 boots two builds manually. Nothing keeps 1.21.x drift from returning after release |
| ux | One `MainThreadBridge` helper replacing 3 copies of the sync-hop idiom | 3/5 | Ph3 moves work off main but does not consolidate the idiom |
| ux | Port `PlayerPetMenuRenderer` onto `MenuLayout` | 5 | Ph5 ports `PetManagementMenuRenderer` only — the second duplicate layout engine survives |
| wiki | `prefers-color-scheme` support | 8 | Not mentioned |
| wiki | Print stylesheet | 8 | Not mentioned |
| wiki | Persist language choice | 8 | Chrome i18n lands; persistence does not |
| wiki | Reduce md-stub duplication + CI diff-check of md vs wiki | 8 | Intent partly met by dropping counts from prose; no diff-check |

Recommendation: fold #1–#4 into Ph2/Ph3 P3 batches, #7 into Ph5 step 2 (it is the same fix already
being written), #8–#10 into Ph8 step 5 (cheap, same files). #5 is the only one with real post-release
value as a standalone ticket; #6 and #11 are fine to defer.

**Landed as** (verified on disk): #1 and #2 → Ph2 P3 batch and step 5 (archival extended to the purchase
and cultivation-action journals, and `CultivationItemActionFileJournal.scan` gains `MAX_SCAN_FILES` plus
its siblings' lock model) · #3 → Ph2 step 2b · #4 → Ph3 Architecture + Todo, as a per-player mailbox
index rather than archival alone · #5 → Ph9 step 8 and Todo, `compileCompatibilityJava` extended or a CI
matrix job, so a future `Attribute`/`Registry`/`Material` rename fails a build instead of a player's
first damage event · #7 → Ph5 step 2, which now names both duplicate layout engines · #8, #9, #10 → Ph8
step 5, together with VI landing-hero copy ("an EN-only hero behind a language toggle is worse than no
toggle") and `localStorage` persistence in the Todo.

**Deferred, deliberately** (2): #6 the `MainThreadBridge` helper replacing three copies of the sync-hop
idiom, and #11 md-stub deduplication plus a CI diff-check of `docs/*.md` against the wiki. Both are
refactors, not defects; #11's intent is partly served by Ph8's generated `wiki-commands.json` drift check
and config-coverage script. Post-release tickets.

## Possible scope creep (plan steps with no scout finding behind them)

Traced against the four stated goals — upgrade, fix-all-bugs, beautify-wiki, release-ready.

| Phase | Step | Trace | Assessment |
|---|---|---|---|
| 7 | Reclassify and behaviorally rewrite 37 source-text contract tests | No scout finding; planner-originated from CHANGELOG evidence ("the pet-follow bug shipped exactly this way") | Largest non-scout workload in the plan. Defensible — Ph4/Ph7 refactors break these tests anyway — but it is a test-quality project riding a bugfix release. Worth an explicit in/out call before Ph7 starts |
| 6 | bStats + update checker | scout-ux upgrade bullets + `plan.md` binding decision | In-scope by the "upgrade" goal, but it adds the plugin's first outbound network egress and a privacy surface. Cost is already absorbed downstream (Ph8 privacy paragraph, Ph9 "all switches off = zero outbound"), so keep — just note it is a *feature*, not a fix |
| 1 | Commit the 49-file idle-play feature | No finding; release hygiene | Necessary to have a clean baseline, but it ships an unaudited feature inside a "fix everything the audit found" release. Scouts never reviewed it |
| 3 | Breaking a READY egg grants the pet | User decision (validation 2026-09-03), not a report finding | Changes shipped semantics and rewrites an existing test's assertions. Correctly release-noted in Ph10; flagged because it is behavior *change*, not defect repair |
| 2 | `ALGORITHM_ID` → `splitmix64-v2` | scout-core P3 (ordering) | Scout-sourced, but it changes a persisted determinism identity for a P3. Ph2's own risk note handles it; keep the "never rewrite stored results" guard |
| 9 | Two-Paper-build certification | Paper-range binding decision | Doubles drill time; in scope for "release-ready" |
| 10 | All Rights Reserved LICENSE | User decision | Not scout-sourced; belongs to the blocked license plan's territory but the file itself is a release prerequisite |
| 8 | `wiki-commands.json` generator (new JUnit + committed generated artifact) | scout-wiki upgrade bullet | In scope; note it couples the docs site to a test-produced artifact — the drift check must fail loudly or it decays like the hand-synced table did |

**Answered** (verified on disk): the two rows that needed an explicit in/out call now have one. `plan.md`'s
Review record keeps both deliberately and names them "scope beyond defect repair … so they are not mistaken
for bug fixes" — Ph7's contract-test rewrite (with the reduced form spelled out if it overruns) and Ph1's
49-file idle-play commit (idle-play defects found later are post-release fixes, not a 3.0.0 gate). Ph7's own
Risk Assessment carries the same reduced-form fallback. The other six rows were assessed keep-as-is and need
no plan change.

## Plan accuracy nits (not coverage)

- Ph6 Related files says `management/PetStudioController.java`; the file is at
  `omnipet-paper/src/main/java/io/github/salyvn/omnipet/paper/studio/bukkit/PetStudioController.java`
  (verified by Glob). Fix before an implementer searches the wrong package.
- Ph6 hedges "call `invalidateDefinitions()` on reload — if not already landed in Phase 3", and Ph3
  never puts it in a step. Neither phase currently owns it (P-5).
- `plan.md` Goal 2 lists "quarantine policy" once; two different quarantine problems exist (core
  repository read quarantine, P-2 — and the vendor-bridge quarantine split in Ph4). Only the second
  has steps.

**All three fixed** (verified on disk): Ph6 Related files now points at `studio/bukkit/PetStudioController.java`;
Ph6 step 7 now reads "(`invalidateDefinitions()` on reload is owned by Phase 3 step 1 — do not duplicate it
here.)" and Ph3 step 1 owns it; `plan.md` Goal 2 now names both quarantine problems separately ("core
read-path quarantine on transient faults, and the vendor-bridge bad-input-vs-broken-ABI split").

Status: DONE | Summary: 105 of 126 scout findings were covered by a concrete plan step at audit time, with 11 orphans and 10 partials — every P1/High covered, every orphan and P3 partial P3/Low. On re-verification against the phase files as they now stand, all 21 partials and orphans, all 6 sub-item gaps, all 4 missing split candidates and all 3 accuracy nits have been folded in, plus 9 of the 11 unpicked upgrade bullets; the only remaining deferrals are the `MainThreadBridge` consolidation and the md-stub/wiki diff-check, both refactors rather than defects and both correctly post-release.

