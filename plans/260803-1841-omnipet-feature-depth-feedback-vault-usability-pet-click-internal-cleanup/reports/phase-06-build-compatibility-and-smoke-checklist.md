---
phase: 6
type: verification
created: 2026-08-03
---

# Phase 6 report: build verification, compatibility, and manual smoke checklist

## Clean build

`gradlew.bat clean build --no-daemon --console=plain` → **BUILD SUCCESSFUL in 51s**, 18 actionable
tasks.

| Metric | Baseline (pre-plan) | Now | Delta |
| --- | --- | --- | --- |
| omnipet-core | 63 suites / 256 tests | 63 suites / 256 tests | — |
| omnipet-paper | 84 suites / 349 tests | 94 suites / 432 tests | +10 suites, +83 tests |
| **Total** | **147 / 605** | **157 / 688** | **+83 tests** |
| Failures / errors / skips | 0 / 0 / 0 | 0 / 0 / 0 | — |
| `OmniPet-3.0.0-SNAPSHOT.jar` | 1,674,717 B | 1,724,718 B | +50,001 B |
| JAR sha256 | `485ac4c6…4299a9` | `6087903b33300d605e97db30b6db715d494c2aa0889fe6528d2c431c61237c5b` | — |
| JAR entries / classes | 1015 / 925 | 1049 / 957 | +34 / +32 |

No existing test was weakened, skipped, or deleted.

## Guard tasks — verified by their effect, not by a green tick

| Guard | Asserted independently |
| --- | --- |
| `checkCoreBoundary` | `grep -rl "org.bukkit\|net.kyori\|io.papermc" omnipet-core/src/main` → **no matches**. This plan added nothing to core; all feedback, config, listener, and view code landed in `omnipet-paper`. |
| `checkDistributionArtifact` | The only third-party package bundled is `org/yaml/snakeyaml`, which predates this plan. `git diff f46644a..HEAD -- '*.gradle.kts' 'gradle/*' 'gradle.properties' 'settings*'` is **empty**, so no dependency declaration changed. `Sound`, `Title`, and action bars are Paper/Adventure API already on the compile classpath. Zero Maven and zero MiniMessage entries. |
| `checkBranding` | Passed. |
| `checkGradleOnly` | Passed. |

## Compatibility — two new API surfaces, both probes rerun

`docs/compatibility.md` previously implied no Paper API surface changed. That is now corrected: this
plan added two, and both were compiled against two API versions rather than assumed.

| Probe | Result |
| --- | --- |
| `compileCompatibilityJava -PcompatibilityPaperApiVersion=1.21-R0.1-SNAPSHOT` | **BUILD SUCCESSFUL**, zero warnings |
| `compileCompatibilityJava -PcompatibilityPaperApiVersion=1.21.1-R0.1-SNAPSHOT` | **BUILD SUCCESSFUL**, zero warnings |

Shape verified directly from both jars with `javap`, not inferred:

- `org.bukkit.Sound` is `public final class ... extends Enum<Sound> implements Keyed, adventure.sound.Sound$Type`
  on **both**. So `Sound.valueOf` resolves names at load without a live registry, which is also what
  makes the unknown-name path unit-testable.
- `PlayerInteractAtEntityEvent extends PlayerInteractEntityEvent` **but overrides `getHandlers()` with
  its own `HandlerList`** on both. A handler registered for the plain event therefore never receives
  the At-variant, which is why only the plain event is registered.

## Manual smoke checklist

No live server was available in this session. Every item is marked **UNRUN** and mapped to whatever
automated coverage stands in for it, so the gap is explicit rather than implied.

| # | Item | Status | Automated coverage standing in |
| --- | --- | --- | --- |
| 1 | **BLOCKING** — a Paper `Interaction` entity delivers `PlayerInteractEntityEvent` (not only the At-variant) when a passenger of an invisible marker | **UNRUN** | *Partial.* The handler-list mechanism is proven from the API jars, so the "both fire" risk is eliminated. Whether Paper raises the plain event for this entity type is not provable offline. **Fails safe:** if only the At-variant arrives the feature is inert — it never misfires, never double-opens, never cancels another plugin's event. |
| 2 | **BLOCKING** — one right-click opens exactly one management screen | **UNRUN** | *Strong.* `PetInteractListenerTest.oneRightClickOpensExactlyOneManagementScreenAcrossBothHands` drives both hand values and asserts one open plus that the filtered event is left uncancelled. |
| 3 | Toggle a pet with feedback on, then with `gui.feedback.enabled: false` | **UNRUN** | *Strong.* `FeedbackServiceTest.disabledFeedbackProducesNoOutputAtAll` asserts zero output across all four categories through the recording fake. |
| 4 | Spam a control; rate limit holds and no sound reaches nearby players | **UNRUN** | *Strong.* A 20-click burst yields one sound; a suppressed-click test proves the floor is measured from the last sound actually played. Player-scoped playback is asserted structurally by `FeedbackCallSiteContractTest` (no `World.playSound`, no `Location` overload, exactly one playback site). |
| 5 | `gui.vault.petsPerPage: 99` clamps to 45 with a warning | **UNRUN** | *Strong.* `GuiConfigLoaderTest.anOversizedVaultPageIsCappedToTheLayoutWithAWarning`. |
| 6 | A restart-only key does **not** change on `/pet admin reload` | **UNRUN** | *Weak.* Restart-only-ness follows from the renderer and `ChatInputService` reading their values in constructors, which review confirms, but no test drives a reload against a live renderer. Documented in `configuration.md` and `troubleshooting.md`. |
| 7 | Deleting the `gui:` section matches the pre-plan baseline | **UNRUN** | *Strong.* `OmniPetConfigGuiSectionTest.deletingTheSectionEntirelyLeavesBehaviorIdenticalToTheHardcodedDefaults` parses the shipped config with the section removed and asserts equality with `GuiConfig.defaults()`. |
| 8 | Cycle every sort and filter on a 100+ pet vault; no pet duplicates or vanishes | **UNRUN** | *Strong.* `VaultPetViewTest` asserts each comparator is idempotent, order-independent, and preserves the multiset; a paged test walks three pages of twelve tie-identical pets and fails if any pet is seen twice or lost. |
| 9 | Right-click own pet; another player's pet; a vanilla mob | **UNRUN** | *Strong.* All three are direct tests, including that the vanilla mob case does not cancel. |
| 10 | Open slot purchase from the hub, cancel, return to the hub | **UNRUN** | *Medium.* `SlotPurchaseOriginTest` asserts the origin's return command and that all three return points dispatch on it, at source level — the return points run inside scheduler callbacks a unit test cannot drive. |
| 11 | A legacy config migration preserves `gui:` | **UNRUN** | *Strong.* `aLegacyMigrationRoundTripDoesNotDropTheGuiSection` round-trips through `encode`. |

Items 1 and 2 remain the release gate for the pet-click feature. Item 6 is the weakest coverage and is
the one most worth running first on a live server.

## Docs updated

| File | Change |
| --- | --- |
| `docs/configuration.md` | Full `gui:` schema, the lenient-versus-strict rationale, the player-scoped and rate-limited feedback behaviour, and a table of which keys reload versus require a restart. |
| `docs/commands-and-permissions.md` | Vault row names the sort and filter controls and their reset behaviour; the management paragraph covers the world right-click; the intro no longer says entity click is unimplemented. |
| `docs/roadmap.md` | The single "Entity interaction and riding — Deferred" row **split in two**: entity click ships with its live gate named, riding stays deferred with `riding=false`. Entity click removed from the non-claims paragraph while riding and passive triggers stay. |
| `docs/compatibility.md` | Verification snapshot refreshed; a new section names the two newly relied-upon API surfaces with the shape verified against both probes; the `1.21.1` row added. |
| `docs/troubleshooting.md` | Three new sections: silent feedback, a `gui:` value that did not take effect, and right-clicking a pet doing nothing. |
| `docs/getting-started.md` | One line each for the vault controls and the world right-click; the config comment mentions `gui:`. |
| `README.md` | Metrics from this build; vault, feedback, and interaction bullets updated. |
| `CHANGELOG.md` | Four Added entries and three Changed entries, with the hub slot-return called out explicitly as a **fix**. |

`docs/migration.md` untouched: no schema or data-format change.

## No doc claims certification

Nothing here claims live-server or vendor certification. Riding, passive triggers, Active Party, rename
control, admin target mode, MMOItems identities, a live MythicMobs skill picker, and vendor/live
certification all remain deferred.

## Unresolved

- Smoke items 1 and 2 gate the pet-click feature on a live Paper server.
- Smoke item 6 (restart-only keys under reload) has the weakest automated coverage.
- `PetActivationService.remove` calls `interactions.unregister` inside its failing try block, so a
  renderer that throws leaves an index entry. Mitigated by the liveness filter in `petFor`; a proper fix
  is a separate core change (see the Phase 4 report).
