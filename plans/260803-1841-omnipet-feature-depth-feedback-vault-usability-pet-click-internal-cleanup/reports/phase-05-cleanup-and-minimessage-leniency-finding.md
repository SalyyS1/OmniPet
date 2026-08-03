---
phase: 5
type: report
created: 2026-08-03
---

# Phase 5 report: internal cleanup and test gaps

No rendered text changed. Every item was either a duplicate collapsed to one implementation or a test
for behavior that already ships.

## Deduplication — done, with two sites the plan did not name

| Concept | Before | After |
| --- | --- | --- |
| Countdown formatter | `HatchMenuRenderer.java:112`, `HubMenuRenderer.java:122` | `text/Durations.countdown` |
| Decimal formatter | `PetManagementMenuRenderer.java:180`, `StudioStatScreens.java:163` | `text/Durations.decimal` |
| Enum-to-text | 5 sites named by the plan, **plus 2 it did not** | `text/Displays` |

The two extra enum-text sites were found by the new contract test, not by the plan's inventory:

- `incubation/action/IncubationActionItemController.java:129` — a private `words` helper identical to
  `Displays.words`.
- `studio/bukkit/StatModifierPresentation.java:21` — a `label` method whose body was character-for-
  character `Displays.of`.

Both are player-facing and both were migrated. The plan's grep missed them because the five it named
use fully-qualified `java.util.Locale.ROOT` while these two import `Locale`. `MigratedControllerTextContractTest`'s
operator-owned exclusions were respected: audit and admin strings keep their raw enum form.

`grep -rl 86_400 omnipet-paper/src/main` and `grep -rl stripTrailingZeros` each return exactly one
file. `omnipet-core/.../IncubationDurationParser.java:61` contains `86_400_000L` and is deliberately
out of scope, as the phase stated.

`DurationsTest` pins the exact output — including the negative-input clamp and the truncating
sub-second remainder — and was **written and run green against the pre-migration behavior** before the
private copies were deleted, so the deduplication is proven output-identical rather than assumed to be.

## The `MessageCatalog` warning was NOT added, and the phase's premise was wrong

The phase asked for a per-key warning in `MessageCatalog.render`, on the reasoning that its
`catch (RuntimeException)` hides a malformed value from the operator. Implementation was completed —
sink threaded through the constructor, keyed `reported` set, warning emitted once per key — and then
**reverted**, because the premise does not hold.

Empirical check against the bundled Adventure, run through the test harness rather than assumed:

| Input | Result |
| --- | --- |
| `<bogus:unclosed` | renders literally, **no throw** |
| `<click:bogus_action:x>hi</click>` | no throw |
| `<color:#zzzzzz>x</color>` | no throw |
| `<gradient:red>x</gradient>` | no throw |
| `<rainbow:notanumber>x</rainbow>` | no throw |
| 15 further malformed cases | no throw |
| a `TagResolver` that itself throws | **no throw** — swallowed inside MiniMessage |
| `MiniMessage.builder().strict(true)` on `<gray>unclosed` | throws `ParsingExceptionImpl` |

`MiniMessage.miniMessage()` is lenient, and lenient mode does not throw. The catch is therefore
**unreachable on the current configuration**, so a warning inside it would have been dead code that
tests could only cover by constructing a strict parser the production path never uses. Adding it would
have meant threading a sink through `defaults()` — changing two contract-test call sites — to feed a
branch that never fires.

The catch is kept and its comment now states why it exists (a guard for a future strict-mode build) and
why it is silent (nothing reachable to report, and it sits in a render path where a log could fire per
frame). That is the honest outcome; a keyed warning would have been ceremony.

**This corrects a factual claim in the phase file.** The phase's own note that argument-free lookups are
memoised at `:99`, "which narrows the benefit", was directionally right — the benefit is in fact zero.

## Catch audit — 218 blocks reviewed, none silently swallowing

Every `catch` in `omnipet-paper/src/main` either logs, reports through a failure sink, returns a
documented sentinel, or takes a recovery action. No unconditional log was added to a render or tick
path.

Four took a deliberate action whose *reason* was not stated, and now carry one:

| Site | Why it is deliberate |
| --- | --- |
| `catalog/ReflectiveMythicLibStatCatalogSource.java:114` | version is diagnostic text; a provider that cannot report it is still usable and must not fail health |
| `management/PetManagementControllerContext.java:139` | null is the caller's contract for "no view"; the caller reports a stale session |
| `management/PetManagementMenuController.java:178` | the scheduler refuses work during shutdown; releasing the lifecycle request is the point, or a disable mid-open locks the screen |
| `task/PerPlayerTaskQueue.java:174` | interrupt is flagged and re-asserted after draining, so shutdown completes instead of abandoning queued work |

## Blind-spot coverage

| Target | Suite |
| --- | --- |
| `Durations` byte-identical output | `text/DurationsTest` |
| Deduplication cannot regress | `text/SharedFormatterContractTest` |
| `PlayerHubController` routing, every tile | `player/PlayerHubRoutingTest` |
| `VaultSortOrder` / `VaultFilter` totality and stability | `gui/player/VaultPetViewTest` (Phase 3) |
| `FeedbackService` rate limiting | `feedback/FeedbackServiceTest` (Phase 2) |
| `GuiConfigLoader` clamping and fallback | `config/GuiConfigLoaderTest` (Phase 2) |
| `PetInteractListener` hand filtering | `gui/pet/PetInteractListenerTest` (Phase 4) |

No coverage percentage was chased. Getters and behavior-free records are untested on purpose.

## Verification

`gradlew.bat build` — BUILD SUCCESSFUL. **94 suites, 432 paper tests + 256 core = 688**, 0 failures,
0 errors, 0 skips. Baseline was 605. No existing test was weakened or deleted.

## Unresolved

None for this phase.
