# Red-team review — release-readiness plan (10 phases)

From: red-team. To: planner / team-lead. Advisory only; no code, plan, or config file modified.
Bash unavailable this session (returns empty), so every claim below is Read/Grep-verified against
source, build files, and workflows — nothing was executed.

Scope: `plans/260903-1813-release-readiness-bugfix-upgrade-wiki/` (`plan.md` + 10 phase files).
Attack vectors: dependency order, missing scope, hidden risk, feasibility, unverified assertions,
absent release blockers. Orphan-scope grading is not repeated here — the coverage audit already did it
mechanically (`from-coverage-audit-to-planner-scout-findings-coverage-matrix.md`, O1–O11); this review
covers what that audit did not look at: the build system, the CI surface, factual claims inside the
phases, and file ownership across the parallel tracks.

## Verdict

**NEEDS REVISION** — sequencing and coverage are sound, but six items would fail at cook time: an
unimplementable bStats packaging step, a write from a stale snapshot after money has left the player,
a "P1" the repo's own CI matrix may already disprove, a docs phase that depends on outcomes it does
not list as dependencies, two files owned by phases the plan says may run in parallel, and no owner
for irreversible on-disk migrations.

## Critical

**Phase 6 / `omnipet-paper/build.gradle.kts:71-84`, `build.gradle.kts:83-92` → "bStats + relocation"
cannot be implemented as written.** There is no Shadow plugin in this repo; the fat JAR is a hand
rolled merge (`from(configurations.runtimeClasspath … map { zipTree(it) })`), which bundles any new
`implementation` dependency **unrelocated**, and `checkDistributionArtifact`'s forbidden prefixes
(`org/bukkit/`, `io/papermc/`, `io/lumine/`, `net/Indyuce/`, `META-INF/maven/`) do not include
`org/bstats/`, so the mistake ships silently and breaks metrics for every other plugin bundling
bStats. **Fix:** copy bStats' single-file `Metrics.java` into
`io.github.salyvn.omnipet.paper.metrics` (the packaging path bStats documents for Bukkit — no
dependency, no Shadow, no lockfile churn), and add `org/bstats/` to the forbidden-prefix list so an
accidental dependency fails `check`. If a real dependency is preferred instead, that is its own
packaging step: apply Shadow, set `archiveClassifier=""`, and re-point `distributionJar`
(`build.gradle.kts:9`), `checkDistributionArtifact`, and `copyReleaseArtifact`.

**Phase 3 step 4 / `omnipet-core/.../economy/SlotUnlockService.java:232-262` → releasing the player
lock around the provider call leaves the grant writing a stale snapshot.**
`case PROVEN_SUCCESS -> SlotPurchaseSagaSupport.grant(current, observed)` (`:262`) uses the
`PlayerState current` captured *before* `support.withdraw(pending)` (`:255`). Once the lock is
released across the withdraw, `current` can be arbitrarily stale — so the outcome write is either a
lost write or a `StaleRevisionException` **after** the money left the player. **Fix:** after
re-locking, re-read player state and re-read the journal transaction, re-check
`hasSlotEntitlement`/next-slot preconditions against the fresh state, and route any divergence to
`UNKNOWN_REQUIRES_RECONCILIATION` (already modelled) instead of writing the pre-call snapshot. Add a
test that mutates player state during the simulated provider call.

**Phase 4 Key Insight + step 8 / `.github/workflows/build.yml:32-48` → the max-health P1 is asserted
against a version matrix CI already tests, and the documented fallback breaks the floor.** Phase 4:25
states as fact that `Attribute.GENERIC_MAX_HEALTH` (`SkillTriggerListener.java:197`) "breaks on Paper
1.21.3+". The repo already compiles the whole Paper boundary against
`1.21-R0.1-SNAPSHOT`, `1.21.11-R0.1-SNAPSHOT`, and three Paper `26.x` builds on Java 25 via
`compileCompatibilityJava` (`omnipet-paper/build.gradle.kts:54-69`), wired into `check` (`:67-69`).
Either that job is green — and the P1 framing is wrong, at most a deprecation — or it is red, and a
failing required check nobody mentions is itself a 3.0.0 blocker. Separately, the fallback offered in
Phase 4:25, `Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"))`, does not work on the
stated 1.21.0/1.21.1 floor: the attribute key was `generic.max_health` before the 1.21.2/1.21.3
flattening, so the lookup returns null and `attribute == null ? 20` (`SkillTriggerListener.java:198`)
silently treats every player as 20 max HP, mis-thresholding the low-health trigger. **Fix:** Phase 1
records the current status of the `compatibility` job in the baseline; Phase 4 step 8 runs
`compileCompatibilityJava -PcompatibilityPaperApiVersion=…` at each matrix point *before* choosing a
fix; drop the `Registry.ATTRIBUTE` option and keep `player.getMaxHealth()` as the single path.

**Phase 8 `dependencies: [5, 6]` → it documents Phase 3's and Phase 4's outcomes without depending on
them.** Step 4b (phase-08:40) writes the placed-egg CLAIM action and READY-break semantics (Phase 3's
binding decision) and the supported-Paper-range statement (Phase 4's outcome). If either phase slips
or lands different semantics, the wiki ships wrong on the two behaviours most likely to change.
**Fix:** `dependencies: [3, 4, 5, 6]`.

**plan.md:42 / phase-03:44, phase-04:47, phase-05:46 → the parallel tracks are not file-disjoint.**
The plan states phases 2+3, 4, and 5 "can run in parallel by different implementers (disjoint
packages)". Two files break that: `OmniPetPlugin.java` is owned by Phase 3 (`hatchPlacedEgg` failure
reporting, `invalidateDefinitions()`) and Phase 4 (SEVERE logging, `LinkageError` catch, Javadoc
prune); `paper-plugin.yml` is owned by Phase 4 (dedupe permissions) and Phase 5 (delete
`omnipet.admin.explore`, which lives in that block). `invalidateDefinitions()` is additionally claimed
twice — phase-03:44 and phase-06:43 ("if not already landed in Phase 3"). **Fix:** give each of the
two files one owning phase (simplest: both edits move into Phase 4, with Phase 3 and Phase 5 handing
over their one-line needs), and assign `invalidateDefinitions()` to Phase 3 only, deleting the hedge
from Phase 6.

**No phase owns the on-disk upgrade path, and `docs/migration.md` appears in no phase's file set.**
This release changes persisted shape and identity in at least five ways: escrow records move to
`done/` (Phase 2), `ALGORITHM_ID` → `splitmix64-v2` (Phase 2), a new `INTERNAL_ATTEMPTING` state is
written pre-delivery (Phase 2), legacy egg stacks gain per-item nonces by **mutating player
inventories** (Phase 3), new config keys (Phase 6). None is reversible, and no phase states what
happens if an operator rolls back to 2.x. **Fix:** add an explicit migration step (Phase 2 or 3 for
behaviour, Phase 8 for the doc): back up `plugins/OmniPet/` before first 3.0.0 boot; enumerate the
one-way migrations; state "no supported downgrade after first boot" or the restore-from-backup path;
emit one log line per migration so operators can see it ran.

## Major

**Phase 3 Architecture / `omnipet-paper/.../incubation/placed/PlacedEggListener.java` →
"`setDropItems(false)` stays at LOWEST" describes a structure that does not exist.** All nine handlers
are `@EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)` — onInteract:74,
onPlace:87, onBreak:117, onTrample:153, onPhysics:168, onEntityExplode:175, onBlockExplode:180,
onPistonExtend:185, onPistonRetract:190 — and `event.setDropItems(false)` is line 122 inside `onBreak`
at NORMAL. There is no LOWEST handler to keep. **Fix:** restate the real split — drop suppression
stays in the NORMAL `onBreak`; reclaim/grant moves to a new MONITOR handler. Note while rewriting that
`ignoreCancelled = true` at MONITOR already means "a protection cancel wins", so the plan's extra
`!event.isCancelled()` re-check is redundant on both the place and break paths; say so or drop it.

**Phase 8 step 2 → the commands-JSON drift check is circular, and the writer will land in the wrong
directory.** A JUnit test that *writes* `docs/wiki-commands.json` can never fail on drift (it rewrites
the file), and `tools/verify-wiki-render.mjs` is Node — it cannot read `OmniPetCommandTree`, so
nothing detects a stale committed file. The existing source-reading test idiom is module-relative
(`OmniPetCommandTreeContractTest.java:26` uses `Path.of("src/main/resources/paper-plugin.yml")`;
`PetInteractListenerTest.java:137` walks `Path.of("src/main/java")`), so a writer test resolves to
`omnipet-paper/docs/`, not `docs/`; it would also make `gradlew test` dirty the working tree. **Fix:**
a Gradle task generates the JSON into `rootProject.layout.projectDirectory.dir("docs")`; a JUnit test
*asserts* committed == generated and writes nothing; the Node verifier only checks shape and render.

**Phase 8 step 6 / `.github/workflows/pages.yml:3-13` → build.yml cannot hand an artifact to
pages.yml.** They are separate workflows on separate runs; `actions/download-artifact` in the Pages
job cannot reach the Build run's artifact without a `workflow_run` trigger and an explicit `run-id`.
The `paths` filter also omits `tools/build-wiki-stats.mjs`, so touching the stats generator deploys
nothing. **Fix:** pick one — `workflow_run`-trigger Pages after Build succeeds and download by
`run-id`; or commit `docs/wiki-stats.json` from Build on `main` with `[skip ci]`; or generate stats
inside Pages (needs Java + Gradle there). Add `tools/build-wiki-stats.mjs` and the new config-coverage
script to the `paths` filter either way.

**Phase 7 success metric → "test count ≥ baseline" is unsatisfiable as written.** Phase 2 deletes
`EffectBudget` plus two tests, Phase 5 deletes `FoundationCommand`/`COMMAND_FOUNDATION_READY` and
their coverage, and Phase 7 itself deletes mechanism-pinning contract tests. **Fix:** replace with two
auditable criteria — (a) every deleted test either names its behavioural replacement or the code it
covered is gone; (b) suite/test counts are *reported* with the delta explained, not gated. Baseline is
210 suites / 1116 tests.

**Phase 2 / `FilePlayerStateRepository.java:135-140` → quarantine amplification is a Key Insight
(phase-02:24) with no implementation step and no success criterion.** Phase 2's own P1 argument is
that a transient YAML race becomes permanent player lockout, so narrowing quarantine is half that
fix — but the checklist an implementer works never mentions it, and the scout files it as P3.
**Fix:** add a step and criterion: quarantine only on codec-classified decode failures; `IOException`
and unexpected `RuntimeException` surface as a load failure that leaves the file untouched; test both
branches.

**Phase 10 / `gradle.properties:3` → the version bump targets the wrong file.** Phase 10 says "Modify
`build.gradle.kts` (version 3.0.0)"; the version is `pluginVersion=3.0.0-SNAPSHOT` in
`gradle.properties:3`, consumed at `build.gradle.kts:8`. **Fix:** bump `gradle.properties`; leave
`build.gradle.kts` alone. Also: `paper-plugin.yml` is processed through Groovy `expand()`
(`omnipet-paper/build.gradle.kts:44-48`), so any `$` introduced into that file by Phase 4, 5, or 10
edits breaks the build — worth one line in each of those phases.

**Phase 9 → the release gate depends on paid vendor plugins with no waiver path, so Phase 10 can
deadlock.** ModelEngine is paid and the MythicMobs line is premium, yet the ModelEngine-vs-head spark
comparison and the live MythicMobs kill-EXP check are gate items, and Phase 10 depends on Phase 9.
**Fix:** define evidence tiers. Tier 1 (blocking): vendor-absent boot, switches-off silence,
durability drills, placed-egg drills, perf tiers on the head renderer. Tier 2 (waivable with a
recorded note): ModelEngine and MythicMobs live checks — and a waiver means `docs/compatibility.md`
says "untested against <vendor> <version>", never "supported".

**Phase 6 / `OmniPetConfigLoader.encode` → new config keys must be added to `encode()` in the same
commit that adds them.** `gui.studio.sessionTimeout`, `metrics.enabled`, and `updateCheck.enabled` are
exactly the class of bug Phase 5 is fixing for `render.nameplateStatus` (a key the loader reads but
never re-encodes, so legacy migration drops it). **Fix:** make "every new key round-trips through
`encode()`, asserted by a test" an explicit Phase 6 success criterion.

## Minor

- **plan.md:6** — `effort: "17d"` matches the serial sum of the phases (16.75d). Since plan.md:42
  endorses three parallel tracks, publish the critical path too (~8.75d: 1 → 2 → 3 → 7 → 9 → 10) so
  staffing can be decided from the plan.
- **`omnipet-paper/build.gradle.kts:35-37`** — `dependencyLocking { lockAllConfigurations() }` is
  declared but no `gradle.lockfile` exists anywhere in the repo, so locking is inert. Decide before
  Phase 6 touches dependencies: generate lockfiles (`--write-locks`) or drop the block.
- **Phase 9** — if the Paper `26.x` / Java 25 rows stay in the CI matrix, the certification
  environment needs a Java 25 toolchain, not just Java 21.
- **Coverage matrix truncation** — `from-coverage-audit-…-coverage-matrix.md:37` promises an "Unpicked
  upgrade bullets" section (11 of 51 bullets) that the file does not contain; it ends at `<!-- MORE -->`
  (line 62). Those 11 bullets are currently unknown to the plan.
- **Orphan P3s** — O6–O11 all live in files Phase 5 already opens, and O1/O2 in Phase 2's existing P3
  batch; six added checklist lines close them. None is in the phase files yet.
- **`plans/reports/` hygiene** — closed audits sit beside live ones with no status marker, so a reader
  can re-open fixed work (see Verified-OK below). Worth a one-line header on each closed report.

## Verified-OK

- **Phase 3 ← Phase 2 is a real dependency, not ceremony.** Both phases modify
  `SlotUnlockService.java` (phase-02:42 passes state through `completeIfEntitled`; phase-03:45 adds the
  EXTERNAL_PENDING pattern), and Phase 3's provider-call rework needs Phase 2's bounded lock registry.
- **"Recovery matrix already covers EXTERNAL_PENDING" holds.** `SlotPurchaseRecovery.java:30`
  (`case EXTERNAL_PENDING -> recoverExternalPending`), `:181`, and
  `SlotPurchaseReconciliationService.java:15` include that state.
- **`done/` archival is compatible with the existing scan.** `FileEggEscrowJournal.java:85-91` uses
  `Files.newDirectoryStream(root, "*.yml")` with a regular-file filter, so a subdirectory is skipped
  without code change; `MAX_FILE_BYTES = 16 * 1024` and `MAX_SCAN_FILES = 10_000` confirmed at `:20-21`.
- **The fingerprint change needs no record-version gate — delete that risk item (phase-02:86).**
  Traced every comparison: `ReleaseService.java:88` and `:120` compare a freshly computed fingerprint
  against one carried by the *in-session* `ReleasePreview`, and `ReleaseOutboxStateUpdater.sameIdentity`
  (`:112-118`) compares two persisted entries with each other. Grep for
  `petFingerprint|confirmationToken` across `omnipet-paper/src/main/java` returns no matches, so no
  preview or token is persisted. Worst case across the upgrade is a preview issued before a restart
  needing re-issue — which already dies with the session.
- **Phase 4's MythicMobs classloader claim holds.** `PaperMythicMobsSkillContext.java:23-24` is the
  correct pattern it cites (plugin classloader with a null-safe fallback).
- **Phase 4's kick/quit reasoning holds.** `PlayerStorageLifecycleListener.java:60-70` runs the same
  `controller.release` + `ownerQuit` pair for both events, so checking `release` for idempotency first
  and then dropping `onKick` is the right order.
- **No other in-range Bukkit drift hazard found.** `feedback/SoundResolver.java:17,48,53` already
  resolves through `Registry.SOUNDS` rather than `Sound.valueOf`; `Particle.valueOf`
  (`config/OmniPetConfigLoader.java:221`, `feedback/FeedbackSettings.java:82`) is safe across the range.
  `SkillTriggerListener.java:197` is the only hazardous site.
- **`from-code-reviewer-to-orchestrator-post-plan-defect-review-report.md` is closed — do not re-open
  it.** All five findings are fixed in current code: the CRITICAL unhatchable companion egg by
  `PetIncubationProfileReader.java:24-48` (`IMPLICIT_BAND`, with a Javadoc describing that exact
  failure); main-thread grant I/O and the escaping `StaleRevisionException` by `EggAdminController`'s
  `PerPlayerTaskQueue` + `mainDispatcher` wiring (`:116-127`, `:210`, `:220`, `:315-321`, `:354-359`);
  the tier-letter `rarityId` at `:375-378`; egg re-tiering at `:231-259`. The coverage audit graded only
  `from-scout-*`, so this report was never walked against the phases — hence the check.
- **Phase 10's "version if not derived" resolves to "it is derived".** `processResources` expands
  `paper-plugin.yml`'s version from the project version (`omnipet-paper/build.gradle.kts:44-48`).
- **The two assumptions the team lead verified are accepted as-is** and not re-flagged: the
  `BukkitReleaseInventoryGateway` PDC transaction tagging with `ALREADY_DELIVERED`, and LuckPerms
  `future.get` running adapter-side outside core's `withLocked`.

## Unresolved questions

1. Is the `compatibility` job in `build.yml` currently green? Bash was unavailable, so I could not run
   Gradle or `gh`. The answer decides whether the max-health item is a false P1 or a red required check.
2. Does the project intend to keep asserting Paper `26.x` / Java 25 compile support (the CI matrix) while
   documenting "supported: 1.21.x", or should the matrix be trimmed to the documented range? Phase 8
   needs one answer, stated as two facts (compile range vs certified range).
3. Is there a registered bStats project/service ID for OmniPet? Phase 6 cannot post metrics without one.
4. Where are the 11 unpicked upgrade bullets the coverage matrix promises but does not list?
5. Will whoever runs Phase 9 have ModelEngine and the premium MythicMobs builds, or is the Tier-2
   waiver the expected path?

Status: DONE
Summary: Verdict NEEDS REVISION — six Critical items (bStats packaging impossible with the current
hand-rolled fat JAR, a stale-snapshot write after the provider call in `SlotUnlockService:262`, a
max-health P1 the repo's own compatibility matrix may disprove, Phase 8 missing dependencies on 3 and
4, `OmniPetPlugin.java`/`paper-plugin.yml` owned by parallel phases, and no owner for irreversible
on-disk migrations) plus nine Major. Separately verified that the older post-plan code-reviewer report
is fully closed in current code and that Phase 2's fingerprint version-gate risk can be deleted.
