---
phase: 1
type: baseline
created: 2026-08-03
---

# Phase 1 baseline + hardcoded-value worklist

## Build baseline

`gradlew.bat clean build --no-daemon --console=plain` → **BUILD SUCCESSFUL in 58s**, 18 actionable tasks (11 executed, 7 from cache).

| Metric | Value |
| --- | --- |
| omnipet-core | 63 suites, 256 tests, 0 failures, 0 errors, 0 skipped |
| omnipet-paper | 84 suites, 349 tests, 0 failures, 0 errors, 0 skipped |
| **Total** | **147 suites, 605 tests, 0 failures/errors/skips** |
| `omnipet-core-3.0.0-SNAPSHOT.jar` | 590,199 bytes — sha256 `688a242e3bf1af95841b0942972d4a191ae383fa9cf47712890158407c066218` |
| `OmniPet-3.0.0-SNAPSHOT.jar` | 1,674,717 bytes — sha256 `485ac4c6aa25042b3cbd4d2805c4d76b317f37aaacfe7326639d89d86b4299a9` |

Guard tasks all green: `checkBranding`, `checkCoreBoundary`, `checkDistributionArtifact`, `checkGradleOnly`. `compileCompatibilityJava` FROM-CACHE.

Java source files repo-wide (excluding `build/`): 699.

## Feedback surface — confirmed zero

`grep -rc "playSound\|showTitle\|sendActionBar\|BossBar"` over `omnipet-paper/src/main` → **0 matches in every file**. The plan's headline claim holds: the plugin is entirely silent.

## Hardcoded-value worklist (Phase 2 input) — 14 literals verified

| Value | File:line | Concept |
| --- | --- | --- |
| `Duration.ofMinutes(2)` | `studio/bukkit/PetStudioController.java:276` | chat-input timeout |
| `Duration.ofMinutes(2)` | `studio/bukkit/PetStudioController.java:335` | chat-input timeout |
| `Duration.ofMinutes(2)` | `studio/bukkit/PetStudioController.java:408` | chat-input timeout |
| `Duration.ofMinutes(2)` | `studio/bukkit/PetStudioController.java:438` | chat-input timeout |
| `Duration.ofMinutes(2)` | `studio/bukkit/PetStudioController.java:460` | chat-input timeout |
| `Duration.ofMinutes(2)` | `studio/bukkit/PetStudioController.java:498` | chat-input timeout |
| `2 * 60 * 20L` | `studio/bukkit/PetStudioController.java:291` | tick expiry, pairs with `:276` |
| `2 * 60 * 20L` | `studio/bukkit/PetStudioController.java:352` | tick expiry, pairs with `:335` |
| `2 * 60 * 20L` | `studio/bukkit/PetStudioController.java:413` | tick expiry, pairs with `:408` |
| `2 * 60 * 20L` | `studio/bukkit/PetStudioController.java:451` | tick expiry, pairs with `:438` |
| `2 * 60 * 20L` | `studio/bukkit/PetStudioController.java:469` | tick expiry, pairs with `:460` |
| `2 * 60 * 20L` | `studio/bukkit/PetStudioController.java:506` | tick expiry, pairs with `:498` |
| `PETS_PER_PAGE = 45` | `gui/player/PlayerPetMenuRenderer.java:25` | vault page size |
| `LINES_PER_PAGE = 8` | `command/CommandHelp.java:14` | help page size |

Out of scope, confirmed distinct: `Duration.ofMinutes(15)` at `PetStudioController.java:84` is the **session** timeout passed to `PetStudioSessionManager`, not the prompt timeout. Leave alone.

## Duplication worklist (Phase 5 input)

Countdown formatter — `grep -rn 86_400` over `omnipet-paper/src/main` returns exactly two files:
- `gui/hatch/HatchMenuRenderer.java:112,113`
- `gui/hub/HubMenuRenderer.java:122,123`

Decimal formatter — `grep -rn stripTrailingZeros` returns exactly two:
- `gui/player/PetManagementMenuRenderer.java:180`
- `studio/bukkit/StudioStatScreens.java:163`

Enum-to-text stragglers — all five plan-named lines confirmed present (they use fully-qualified `java.util.Locale.ROOT`, so a `Locale.ROOT` grep misses them):
- `player/PlayerPetController.java:315`
- `player/PlayerSlotPurchaseController.java:321`
- `player/PlayerHatchController.java:390`
- `skill/PaperActiveSkillController.java:299`
- `management/PetManagementMenuSupport.java:164`

Two further sites exist but are **out of the plan's named scope**: `incubation/action/IncubationActionItemController.java:129` and `studio/bukkit/StatModifierPresentation.java:21`. Both are already local private helpers; `Displays.java:28` is the canonical implementation.

## Phase 1 target verification

| Plan claim | Verified |
| --- | --- |
| `PlayerHubController.click` calls `slotPurchases.open(player, 1)` | Yes — `PlayerHubController.java:122` |
| `PlayerHubController` has no in-flight `Set<UUID>` | Yes — only `PlayerRequestTracker requests` at `:40` |
| `SlotPurchaseInventoryHolder` enforces `returnPage >= 1` | Yes — `:34` |
| Three return points dispatch on `returnPage` | Yes — cancel `:153` (`pet " + holder.returnPage()`), success `:257`, `STALE_QUOTE` reopen `:270` (`open(player, holder.returnPage())`) |
| `InteractionIndex` exists, all methods `synchronized` | Yes — `register :13`, `resolve :27`, `unregister :31`, `removeOwner :38`, `size :44` |
| `OmniPetConfigLoader.ROOT_KEYS` lacks `gui` | Yes — `:20`, five keys; `encode()` hardcodes the same five |

**Note on the cancel path:** the plan says cancel runs `pet <returnPage>`. Verified literal is `player.performCommand("pet " + holder.returnPage())` — i.e. `pet 1`, not `pet vault 1`. `OmniPetCommand` treats a bare numeric argument as a vault page. The origin dispatch must therefore emit `pet` for HUB and preserve the existing numeric form for VAULT, or vault paging regresses.
