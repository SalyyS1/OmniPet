---
phase: 2
title: "Feedback primitives and gui config"
status: complete
priority: P1
effort: "1.5d"
dependencies: [1]
---

# Phase 2: Feedback primitives and gui config

## Overview

Give the plugin a voice and give the operator a dial. Today every interaction is silent — `grep playSound|showTitle|sendActionBar|BossBar` over `omnipet-paper/src/main` returns **zero** — and every tunable number is a Java literal.

## Requirements

- Functional: a single `FeedbackService` plays a sound, and optionally an action bar line, for success / failure / blocked / progress outcomes. A `gui:` config section owns page sizes, prompt timeouts, help page length, and feedback toggles.
- Non-functional: no new bundled dependency (`Sound` and Adventure are already on the compile classpath); `checkDistributionArtifact` stays green; feedback is fully disableable; sound is rate-limited per player.
- Security: feedback plays **to the acting player only**. Never to nearby players — an audible broadcast on a spammable click is a griefing vector.

## Architecture

### `FeedbackService`

One class in `paper/feedback/`, constructed once and bound like `Messages`. It exposes intent, not mechanism:

```java
feedback.success(player, FeedbackEvent.PET_ACTIVATED);
feedback.failure(player, FeedbackEvent.SLOT_UNAFFORDABLE);
feedback.blocked(player, FeedbackEvent.PET_LOCKED);
feedback.progress(player, FeedbackEvent.HATCH_QUEUED);
```

Call sites never name a `Sound` constant. The event→sound mapping lives in config, so an operator can retune without a rebuild, and a call site cannot drift from the config schema.

Four outcome categories, not one per event: `SUCCESS`, `FAILURE`, `BLOCKED`, `PROGRESS`. Each maps to one sound, pitch, and volume. `FeedbackEvent` is a finer-grained enum used only for the optional action-bar text key, so adding an event does not require an operator to configure a new sound.

Rate limiting is mandatory and lives here, not at call sites: a per-player timestamp map with a configured minimum interval. A 20-click burst must produce at most the configured number of sounds. Without this, a fast-clicking player is an audible nuisance and the guard cannot be enforced consistently across ~20 call sites. The map must be **evicted on `PlayerQuitEvent`**, or it leaks a UUID per player for the server's lifetime.

### The test seam — required, not optional

Asserting "disabled config produces zero sound calls" and "a burst respects the interval" requires *observing* playback. This repo has **no MockBukkit** (`grep mockbukkit` → 0) and hand-rolls `Proxy.newProxyInstance` doubles. Counting calls through `Player`'s **12** `playSound` overloads via a dynamic proxy is fragile and will rot.

So `FeedbackService` must write to a narrow interface, not to `Player` directly:

```java
interface FeedbackOutput {
    void sound(Player player, ResolvedSound sound);
    void actionBar(Player player, Component text);
}
```

Production binds a Bukkit-backed implementation; tests bind a recording fake and assert against counts. Without this seam the phase's two headline criteria are not achievable, and the temptation is to weaken them into "the code contains a playSound call" — which asserts nothing.

Sound resolution must be **fail-soft**: an operator naming a `Sound` that does not exist on this server version must produce a warning and silence for that category, never an exception into a click handler. Resolve by name once at load through `Registry.SOUNDS`, not per call.

### `gui:` config section

New, and deliberately **lenient** — following the `messages.yml` precedent, not the strict `storage:` one. `OmniPetConfigLoader.rejectUnknown` (`config/OmniPetConfigLoader.java:143`) throws on any unexpected key; a bad page size must not disable pets.

```yaml
gui:
  feedback:
    enabled: true
    minIntervalMillis: 150
    actionBar: true
    success:   { sound: "ENTITY_EXPERIENCE_ORB_PICKUP", volume: 0.6, pitch: 1.2 }
    failure:   { sound: "BLOCK_NOTE_BLOCK_BASS",        volume: 0.6, pitch: 0.8 }
    blocked:   { sound: "BLOCK_CHEST_LOCKED",           volume: 0.6, pitch: 1.0 }
    progress:  { sound: "UI_BUTTON_CLICK",              volume: 0.4, pitch: 1.0 }
  vault:
    petsPerPage: 45
  help:
    linesPerPage: 8
  studio:
    promptTimeoutSeconds: 120
```

Every key optional; an absent `gui:` section yields today's hardcoded values exactly. `petsPerPage` must be validated against the inventory geometry — a value above 45 cannot fit the 54-slot layout alongside the control row, and a value below 1 is nonsense. Clamp with a warning rather than failing.

**`encode()` must be updated too.** `OmniPetConfigLoader.encode()` (`:42-75`) hardcodes the five root keys and is invoked on legacy migration (`OmniPetPlugin.java:346`). Adding `GuiConfig` to `OmniPetConfig` without touching `encode` means a legacy migration **silently drops the operator's `gui:` section**. Either serialize it, or explicitly exclude it with a stated reason.

Note the precedent question: `integrations:` is in `ROOT_KEYS` (`:20`) but never parsed. Decide whether `gui:` follows that accepted-and-ignored pattern or is genuinely parsed, and say which — "lenient" must not become ambiguous.

### Reload scope — decided

`gui.*` is **not** uniformly reload-live, and the plan says so rather than implying otherwise:

| Setting | On `/pet admin reload` | Why |
| --- | --- | --- |
| `gui.feedback.*` | **Live** | `FeedbackService` is rebuilt with the staged-then-swap discipline already used for the message catalog. |
| `gui.vault.petsPerPage` | **Restart only** | `PlayerPetMenuRenderer` is constructed once inside `PlayerPetController`'s constructor (`:58`) with no setter, and `reloadRuntime` (`OmniPetPlugin:300-338`) has no hook for it. |
| `gui.help.linesPerPage` | **Restart only** | `CommandHelp.LINES_PER_PAGE` is `public static final` on a utility class and is referenced by `CommandHelpTest:74,97`. Making it live means mutable static state — worse than a documented restart. |
| `gui.studio.promptTimeoutSeconds` | **Restart only** | `ChatInputService` is built in `PetStudioController`'s constructor (`:94`); `studio.reload()` (`:107-114`) does not rebuild it. |

Restart-only values must be **documented as such** in `configuration.md`, and Phase 6 must not claim otherwise. Silently not applying is the failure mode to avoid.

### Where feedback attaches

Derived from the UX scout's silent-path findings, in priority order:

| Call site | Outcome |
| --- | --- |
| `PlayerPetController.toggle` success | `SUCCESS` — activating/recalling currently gives only a GUI re-render |
| `PlayerPetController.toggle` rejected | `FAILURE` |
| `PlayerPetController` in-flight | `BLOCKED` |
| `PlayerHatchController` start queued | `PROGRESS` |
| `PlayerHatchController` claim success | `SUCCESS` |
| `PlayerHatchController` claim locked/failed | `FAILURE` |
| `PlayerSlotPurchaseController` unlocked | `SUCCESS` |
| `PlayerSlotPurchaseController` rejected / unaffordable | `FAILURE` |
| `PetManagementMenuSupport.message` by colour | maps to the same three keys it already branches on |
| `PaperActiveSkillController` succeeded / rejected / chance-missed | `SUCCESS` / `FAILURE` / `PROGRESS` |

`PROGRESS` for chance-missed rather than `FAILURE`: a missed proc is a normal outcome, not an error, and should not sound like one.

## Related Code Files

- Create: `paper/feedback/FeedbackService.java`, `FeedbackCategory.java`, `FeedbackEvent.java`, `FeedbackSettings.java`, `FeedbackOutput.java`, `BukkitFeedbackOutput.java`
- Create: `paper/feedback/FeedbackServiceTest.java`, `FeedbackSettingsTest.java`
- Create: `paper/config/GuiConfig.java`, `GuiConfigLoader.java` + `GuiConfigLoaderTest.java`
- Modify: `paper/config/OmniPetConfig.java` — carry `GuiConfig`
- Modify: `paper/config/OmniPetConfigLoader.java` — parse `gui:` leniently, add `"gui"` to `ROOT_KEYS` (`:20`), **and update `encode()` (`:42-75`)**
- Modify: `OmniPetPlugin.java` — construct/bind `FeedbackService`, rebuild it in `reloadRuntime`, evict the rate limiter on quit
- Modify: `paper/gui/player/PlayerPetMenuRenderer.java` — read `petsPerPage` from config (restart-only)
- Modify: `paper/command/CommandHelp.java` — `LINES_PER_PAGE` from config (restart-only; keep the constant, seed it at load)
- Modify: `paper/studio/bukkit/PetStudioController.java` — **12 literals**, value-only edits (see below); do not restructure this 583-line file
- Modify: the controllers in the table above — add feedback calls
- Modify: `omnipet-paper/src/main/resources/config.yml` — document the `gui:` section, marking restart-only keys

## The hardcoded-value inventory — corrected

An earlier draft said "three call sites". The verified count is **14 literals across 3 concepts**:

| Value | Sites | Note |
| --- | --- | --- |
| `Duration.ofMinutes(2)` | `PetStudioController.java:276,335,408,438,460,498` — **6** | Chat-input timeout |
| `2 * 60 * 20L` | `PetStudioController.java:291,352,413,451,469,506` — **6** | Tick expiry, **paired** with the line above |
| `PETS_PER_PAGE` | `PlayerPetMenuRenderer.java:25` | |
| `LINES_PER_PAGE` | `CommandHelp.java:14` | |

`Duration.ofMinutes(15)` at `PetStudioController.java:84` is the **session** timeout, a different concern — leave it alone or give it its own key, but do not fold it into the prompt timeout.

The `Duration` and tick values are hand-maintained twins. **Derive the tick count from the `Duration`** so they cannot drift, and assert that in a test. Six independent pairs is six chances for a mismatched expiry.

## Implementation Steps

**Split into two commits.** This phase is too large to land safely as one: 6 new feedback classes, 2 new config classes, an `OmniPetConfig` record change, loader + `encode` changes, plugin and reload wiring, 14 literal migrations, ~20 call sites, `config.yml`, and 9 distinct test scenarios.

### 2a — `gui:` config and hardcoded-value extraction

1. Add `GuiConfig` + `GuiConfigLoader`: lenient parse, per-key fallback, clamp `petsPerPage` to 1..45 and `linesPerPage` to 1..20 with a warning, validate `promptTimeoutSeconds` as positive.
2. Wire `gui` into `OmniPetConfig`, add `"gui"` to `ROOT_KEYS`, and update `encode()`. Confirm a config **without** `gui:` still loads and that a legacy migration round-trip does not drop `gui:`.
3. Migrate the 14 literals. Derive each tick expiry from its paired `Duration`.
4. Tests: absent `gui:` reproduces today's values; `petsPerPage: 99` clamps to 45 with a warning; `petsPerPage: 0` clamps to 1; a malformed value never throws; the strict root check still rejects a genuinely unknown section (`OmniPetConfigLoaderTest:61-62` must stay green); tick expiry matches its `Duration` for all six pairs.

### 2b — `FeedbackService` and call sites

5. Add `FeedbackCategory` (4 constants) and `FeedbackEvent` (one per row above).
6. Add `FeedbackOutput` + `BukkitFeedbackOutput`. The service depends on the interface only.
7. Add `FeedbackSettings` as an immutable record of resolved sounds, volumes, pitches, master toggle, and rate-limit interval. Resolve names **once at load** via `Registry.SOUNDS`, warning and disabling that category on an unknown name.
8. Add `FeedbackService`: four intent methods, per-player rate limiter, no-op when disabled, playback to the acting player only.
9. Bind in `onEnable`; rebuild in `reloadRuntime` with staged-then-swap so a bad sound name leaves the previous settings live; evict the rate limiter on `PlayerQuitEvent`.
10. Add feedback calls per the table. Each sits **beside** the existing `sendMessage`, never replacing it — text remains the accessible channel.
11. Tests: disabled config produces zero output through the recording fake; a 20-click burst respects the interval; an unknown sound name warns and silences only that category; the rate limiter is evicted on quit.

## Success Criteria

- [ ] `grep -c playSound` over `omnipet-paper/src/main` > 0, and every call site routes through `FeedbackService`.
- [ ] `gui.feedback.enabled: false` yields zero output through the recording `FeedbackOutput` fake (asserted).
- [ ] A 20-click burst produces at most the configured number of sounds (asserted through the fake).
- [ ] The rate limiter is evicted on `PlayerQuitEvent` (asserted).
- [ ] An absent `gui:` section reproduces today's behavior byte-for-byte (asserted).
- [ ] A legacy migration round-trip does not drop `gui:` (asserted against `encode`).
- [ ] `OmniPetConfigLoaderTest:61-62` stays green — the strict root check still rejects a genuinely unknown section.
- [ ] An unknown sound name warns and silences one category without disabling the plugin (asserted).
- [ ] All 14 literals read from config; each tick expiry derives from its paired `Duration` (asserted for all six pairs).
- [ ] Restart-only keys are documented as restart-only in `config.yml` and `configuration.md`.
- [ ] No call site names a `Sound` constant directly.
- [ ] Playback is player-scoped — no `World.playSound` and no `Location`-broadcast overload (asserted by review, not by naive grep).
- [ ] `checkDistributionArtifact` green; nothing new in the JAR.
- [ ] Landed as two commits (2a config, 2b feedback).

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| Sound spam as a griefing vector | Player-scoped playback only, mandatory rate limit in the service, burst test through the fake. |
| Rate-limiter map leaks a UUID per player | Evicted on `PlayerQuitEvent`; asserted. |
| An operator's sound name breaks on a Paper version | Names resolve once at load via `Registry.SOUNDS`; unknown name warns and silences that category. Never resolved per click. |
| **Untestable criteria get silently weakened** | `FeedbackOutput` seam is mandatory, not optional. Without it the two headline criteria degrade into "the source contains a playSound call", which asserts nothing. |
| `encode()` drops `gui:` on legacy migration | `encode` is in the modify list with an explicit round-trip test. |
| `gui:` typo disables pets | Lenient loader, per-key fallback, clamps with warnings. Malformed-value test required. |
| Adding `"gui"` to `ROOT_KEYS` weakens the strict root check | Only the known key is added; `OmniPetConfigLoaderTest:61-62` must stay green. |
| Restart-only values silently not applying | Reload scope is decided and tabled; `configuration.md` states it; Phase 6 must not claim live reload. |
| Phase too large for one commit | Split into 2a and 2b. |
| Touching `PetStudioController` (583 lines) | Value-only edits at 12 known lines. No restructuring. |
