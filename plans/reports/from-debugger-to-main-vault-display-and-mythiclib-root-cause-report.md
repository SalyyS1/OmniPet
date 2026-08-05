# Vault Display + MythicLib Root Cause Report

Date: 2026-08-05. Read-only investigation. No code modified, no build run.

---

## BUG A — vault missing stats / level / level progress

**Verdict: three distinct causes. Two are missing features, one is a real data bug.**

### Renderer

`omnipet-paper/.../paper/gui/player/PlayerPetMenuRenderer.java:101` — `petRow(PetInstance, boolean)` is the
method that draws a vault pet entry. This is the exact method that would need to change.

Lore it builds (`:103`–`:115`), in order:

| Line | Source | Condition |
|---|---|---|
| Favorite | `PetManagementMetadata.read(pet).favorite()` | `:104` if favorite |
| Level | `summary.level()` | `:105` **only if present** |
| Rarity | `summary.rarity()` | `:107` only if present |
| blank spacer | — | `:111` |
| Activate/Recall hint | — | `:112` |
| Manage hint | — | `:115` |

Data source is `VaultPetSummary` (`omnipet-paper/.../gui/player/VaultPetSummary.java:22`), which exposes
exactly three values: `level`, `rarity`, `favorite` (fields at `:23`–`:25`).

### A1 — stats: never rendered, and absent from the summary type (missing feature)

`VaultPetSummary` has **no stats field at all**. There is no reader for the `stats` component, and no
`MessageKey` for a stat line exists — the only vault pet keys are
`GUI_VAULT_PET_LEVEL`, `GUI_VAULT_PET_RARITY`, `GUI_VAULT_PET_FAVORITE`
(`omnipet-paper/.../paper/text/MessageKey.java:213`, `:214`, `:230`).

The data **does exist on the pet**. `IncubationPetFactory` writes realized stats into the
`stats` component: `omnipet-core/.../incubation/IncubationPetFactory.java:25`–`:36` iterates
`outcome.realizedStats()` writing `{id, modifierType, value}` per stat, then
`components.put("stats", stats)` at `:36`.

A working reader for that exact shape already exists and is reusable:
`omnipet-core/.../core/buff/PetStatBuffProjection.read(PetInstance)` at
`omnipet-core/.../buff/PetStatBuffProjection.java:15`–`:34`.

So: **present in the snapshot, never read by the GUI, no message key to render it.** Missing feature.

### A2 — level progress / experience: never rendered (missing feature)

`ProgressionState` carries `level, experience, evolution, stamina, lastStaminaEpochMillis`
(`omnipet-core/.../progression/ProgressionState.java:7`–`:13`). `VaultPetSummary` reads **only** `level`
(`:37`, `readLevel` at `:64`). `experience` is never read, and there is no
`GUI_VAULT_PET_PROGRESS`/`_EXPERIENCE` message key. Nothing to render progress with. Missing feature.

Note the deliberate design constraint documented at `VaultPetSummary.java:12`–`:16`: the renderer must not
call `PetProgressionProjection.read`, because that method throws on any malformed field
(`PetProgressionProjection.java:56`, `:62`, `:70`) and one bad legacy pet would blank the whole page. Any
fix must keep the type-test-and-omit approach, not switch to the throwing projection.

### A3 — level line silently absent on every freshly hatched pet (REAL BUG)

`VaultPetSummary:37` reads level from `rawComponents().get("progression")`.
`PetProgressionProjection.COMPONENT_KEY = "progression"` (`PetProgressionProjection.java:10`).

**`IncubationPetFactory` never writes a `progression` component.** It writes only `hatching`, `stats`,
`appearance`, and optionally `release` (`IncubationPetFactory.java:35`–`:42`). Grep for the literal
`"progression"` across main source confirms the only writer is
`PetProgressionProjection.write` (`:42`), reached solely via
`RepositoryProgressionService` (`omnipet-core/.../progression/RepositoryProgressionService.java:167`).

Consequence chain:
1. Pet hatches → no `progression` node.
2. `VaultPetSummary.readLevel` receives `null` map → returns `null` at `:65`.
3. `summary.level()` is empty → `PlayerPetMenuRenderer:105` `ifPresent` never fires.
4. Vault shows **no level line at all** until the owner performs a cultivation action that triggers
   `PetProgressionProjection.write`.

Reader-side is asymmetric and correct-by-design: `PetProgressionProjection.read:17` returns
`ProgressionState.initial(...)` when the node is absent, so services see level 1. Only the GUI's
independent null-returning reader shows nothing. That asymmetry is the bug: hatch does not persist an
initial progression node, and the GUI reader does not default to level 1.

Two viable fixes (either resolves A3): write `ProgressionState.initial` at hatch in
`IncubationPetFactory`, or default `readLevel` to `1` on an absent node. The second also fixes existing
saved pets without a migration.

---

## BUG B — MythicLib stats "do not work"

**Verdict: runtime stat application IS fully implemented and wired. `docs/integrations.md` is
factually stale. This is a real integration bug, not a missing feature.**

### Touch points

| File | Role |
|---|---|
| `omnipet-paper/.../paper/catalog/ReflectiveMythicLibStatCatalogSource.java` | Studio stat-picker catalog |
| `omnipet-paper/.../paper/catalog/PaperStatCatalogContext.java` | catalog lifecycle |
| `omnipet-paper/.../paper/catalog/StatCatalogLifecycleListener.java` | catalog enable/disable |
| `omnipet-paper/.../paper/buff/ReflectiveMythicLibBuffPort.java` | **runtime stat application** |
| `omnipet-paper/.../paper/buff/PaperOwnerBuffCoordinator.java` | snapshot → main-thread apply |
| `omnipet-paper/.../paper/buff/MythicLibBuffLifecycleListener.java` | provider hot-reload |
| `omnipet-paper/.../paper/buff/MythicLibBuffResult.java` | result/status record |
| `omnipet-core/.../core/buff/PetStatBuff.java`, `PetStatBuffProjection.java` | vendor-neutral projection |

### Application is implemented, not deferred

`ReflectiveMythicLibBuffPort.reconcile` (`ReflectiveMythicLibBuffPort.java:43`) genuinely mutates
MythicLib: resolves the stat map (`:53`), gets each `StatInstance` (`:60`), removes a stale modifier
(`:66`), and **registers a real `StatModifier`** (`:70`). Stale-modifier cleanup at `:75`–`:81`.

Reflection targets MythicLib 1.7.1 (`Bindings.load`, `:131`–`:151`):
`io.lumine.mythic.lib.api.player.MMOPlayerData#get(UUID)`, `#getStatMap`,
`api.stat.StatMap#getInstance(String)`,
`api.stat.StatInstance#getModifier/removeModifier/registerModifier`,
`api.stat.modifier.StatModifier(UUID, String, String, double, ModifierType, EquipmentSlot, ModifierSource)`.

Fully wired in the plugin:
- constructed `OmniPetPlugin.java:161`, provider resolved `:162`
- **fed every storage snapshot** `:202` (`ownerBuffs.accept(snapshotUpdate)`)
- lifecycle listener registered `:253`
- owner-quit cleanup `:265`, shutdown clear `:322`, reload refresh `:409`

Stat source: `PetStatBuffProjection.readActive` (`PetStatBuffProjection.java:36`) over
`snapshot.pets()` filtered by `snapshot.desiredActivePetIds()`, called at
`PaperOwnerBuffCoordinator.java:45`. So only **active** pets buff the owner.

### Docs are wrong

`docs/integrations.md:16` states:

> `| Owner stat application | MythicLib | Deferred | No runtime buff is applied. |`

Contradicted by `ReflectiveMythicLibBuffPort.java:70` plus the `OmniPetPlugin` wiring above. **Docs need
correcting regardless of the fix.** Do not let this line mislead triage — the user is reporting a broken
feature, not requesting a new one.

### Why it silently fails in production — ranked hypotheses

Every failure path returns a status instead of throwing, and **only `QUARANTINED` is ever logged**
(`PaperOwnerBuffCoordinator.java:105`–`:108`). `UNAVAILABLE`, `OFF_THREAD`, and per-stat `skipped` are
discarded. That is the observability gap making this look like "nothing happens".

1. **Silent per-stat skip — most likely.** `ReflectiveMythicLibBuffPort.java:61`–`:63`: if
   `getInstance(statId)` returns `null` the stat is counted `skipped` and dropped with **no log**. Stat
   IDs authored in the Studio that are not registered MythicLib stats vanish invisibly. `APPLIED` is still
   returned (`:84`) even when `applied == 0` and every stat was skipped.
2. **Constructor/ABI drift → quarantine.** `Bindings.load` (`:131`) resolves an exact 7-arg
   `StatModifier` constructor and three enums. Any 1.7.x signature change throws
   `ReflectiveOperationException`, caught at `:36`, leaving `bindings == null` and every `reconcile`
   returning `UNAVAILABLE` at `:48` — **never logged**, because that path is not the quarantine branch.
   Note no MythicLib test stub exists for the buff port: `omnipet-paper/src/test/java/io/lumine/mythic/lib/MythicLib.java`
   stubs only `inst()/getStats()/getHandler` for the **catalog**. There is no test at all for
   `ReflectiveMythicLibBuffPort` (only `omnipet-core/.../buff/PetStatBuffProjectionTest.java` exists), so
   real ABI drift would not be caught by the suite.
3. **Quarantine is permanent-until-reload.** `unavailableReason` is set at `:86` and never cleared inside
   the port; `:48` then short-circuits forever. One transient failure disables owner stats for the whole
   server session. Recovery requires a new port instance via `refreshProvider()`
   (`PaperOwnerBuffCoordinator.java:29`), which only fires on MythicLib enable/disable or config reload.
4. **Zero-valued stats never applied.** `:69` guards `if (buff.value() != 0)`. A stat rolled to exactly
   0.0 registers nothing — correct, but reads as "not working" if a rarity floor rolls zero.
5. Eliminated: type mapping. `PetStatBuff` normalizes to `FLAT`/`RELATIVE`/`ADDITIVE_MULTIPLIER`
   (`PetStatBuff.java:14`–`:21`); `core/studio/StatModifierType.java:4`–`:7` declares the same three, and
   `Enum.valueOf(modifierType, ...)` at `:175` matches MythicLib's `ModifierType`. Consistent.
6. Eliminated: offline/off-thread as a root cause. `:46` and `:50` are correct guards, and the
   coordinator hops to the main thread properly (`PaperOwnerBuffCoordinator.java:115`–`:119`).

Confirming which of 1–3 applies requires a live server with MythicLib; the code cannot distinguish them
statically because the distinguishing statuses are not logged. **Cheapest diagnostic: log `skipped > 0`
and the `UNAVAILABLE` reason at `PaperOwnerBuffCoordinator.java:105`.** That single change turns an
invisible failure into an identified one.

---

## Recurrence prevention

- `PaperOwnerBuffCoordinator.report` (`:105`) logs only `QUARANTINED`. Log `UNAVAILABLE` reasons and any
  non-zero `skipped` count. Highest-value change in this report.
- No test exercises `ReflectiveMythicLibBuffPort`; the existing `io.lumine` stub covers the catalog only.
  A stub matching the 1.7.1 `StatMap`/`StatInstance`/`StatModifier` shape would catch ABI drift at build
  time instead of in production silence.
- Quarantine has no self-heal and no operator-visible state. Consider surfacing it in a diagnostics
  command.
- No test asserts that a hatched pet carries a `progression` component — the A3 gap between
  `IncubationPetFactory` and the GUI reader is unguarded on both sides.
- `docs/integrations.md:16` drifted from code. A doc claiming "Deferred" for shipped, wired behavior
  actively misdirects triage.

---

## Unresolved questions

1. A3 fix preference: write `ProgressionState.initial` at hatch (`IncubationPetFactory.java:35`), or
   default `VaultPetSummary.readLevel` to `1`? The reader default also repairs already-saved pets with no
   migration; the factory write makes persisted data self-describing. Both is defensible.
2. For A1, how many stat lines should a vault row show? Realized stats are capped at 256
   (`PetStatBuffProjection.MAX_STATS_PER_PET`); a vault row needs a hard display cap and an ordering rule.
3. For A2, should progress render as a percentage, `current/next` XP, or a bar? Requires
   `ExperienceFormula`/`ProgressionConfig` access the vault renderer does not currently have — worth
   confirming whether the GUI can reach the active `ProgressionConfig` without new plumbing.
4. Should vault stat/progress display be gated by a `gui` config flag, or unconditional?
5. Unverified against a live server: which of Bug B hypotheses 1–3 is actually firing. Cannot be
   determined statically. Is a MythicLib test server available, or should the diagnostic logging land
   first to collect evidence?
6. Are the reported "missing stats" in A1 expected to be the raw hatch-realized stats, or the same values
   after progression/level scaling? No level-scaling of stats was found anywhere in the code, which may
   itself be a further gap.
