---
phase: 3
title: "Vault usability at scale"
status: pending
priority: P1
effort: "1d"
dependencies: [2]
---

# Phase 3: Vault usability at scale

## Overview

The vault is unusable past a few dozen pets. `grep -c "filter\|sort\|search"` over `PlayerPetMenuRenderer.java` returns **0**, while the Studio has both a definition search and a stat search. Staff got the tool; players did not.

**Scope decision:** click-cycled sort and filter only. No text search. Chat search was investigated and rejected — `StudioFieldPrompt` is package-private in `studio.bukkit` (`StudioFieldPrompt.java:20`), and its capture machinery requires a `StudioViewToken`, a `PetStudioSessionManager` validator (`PetStudioController.java:94`), and the `AsyncChatEvent` hook in `PetStudioListener.java:75`. The vault has none of these. Standing up a second chat-capture subsystem is disproportionate to the value when sorting already puts the right pets on page 1.

## Requirements

- Functional: click-cycled sort (favorites-first, level, rarity, name, recent) and a click-cycled status filter; honest empty and last-page states; overflow warning that names its remedies.
- Non-functional: sorting and filtering are in-memory over the already-loaded snapshot — no new repository read, no I/O, nothing per-tick.
- Non-functional: no new chat listener, no new session or token scope.

## Architecture

### View state

`PlayerPetMenuRenderer.render(player, snapshot, page)` gains a `VaultViewState` — sort order, filter, page — carried on `PlayerPetInventoryHolder`.

```java
record VaultViewState(VaultSortOrder sort, VaultFilter filter, int page) {
    static VaultViewState initial() { return new VaultViewState(VaultSortOrder.FAVORITES_FIRST, VaultFilter.ALL, 1); }
}
```

Default sort is `FAVORITES_FIRST`. A player who bothered to favorite a pet has told us what they want first.

**View state does not survive a command round-trip, and that is accepted.** `PlayerPetController:116` routes PURCHASE_SLOT via `performCommand("pet slot " + page)`, `:119` routes HUB via `performCommand("pet")`, the slot cancel path returns via `pet vault <page>`, and `PetManagementMenuController:81` returns BACK via `performCommand("pet vault")`. Each reconstructs a fresh vault at `VaultViewState.initial()`. Rewiring all four to direct controller calls is out of scope here — it would touch the money-handling purchase flow for a display concern. **Document the behavior**: returning from a pet's management screen resets sort to favorites-first. If that proves annoying in practice, it is a follow-up.

Add `openVault(Player, VaultViewState)` as an **overload**; keep `openVault(Player, int)` intact, since `OmniPetCommand:486,492` and `PlayerHubController:120` call it. `toggle`'s re-render (`:244`) must thread the holder's view state so activating a pet does not silently reset the sort.

### Sorting

`VaultSortOrder`: `FAVORITES_FIRST`, `LEVEL_DESC`, `RARITY_DESC`, `NAME_ASC`, `RECENT` (current insertion order, kept as an explicit choice).

Every comparator must be **total and stable**: ties break on pet UUID. An unstable comparator is a real bug — a pet could appear on two pages or none. This is the highest-value test in the phase.

Level and rarity come from `VaultPetSummary`, which returns `Optional`. Pets with no readable level sort **last** in `LEVEL_DESC`, not as level 0 — absent data is not a low value.

**The favorite flag is reachable with zero I/O** — verified: `PetManagementMetadata.read(PetInstance)` reads `extensions().get("management").get("favorite")`, is pure, lives in `omnipet-core`, and needs no Bukkit. `PetStorageSnapshot.pets()` already exposes `PetInstance`. `VaultPetSummary` currently reads only `rawComponents()`; extend it with an `extensions()` read. An earlier draft hedged that this might need a repository read — that was wrong.

### Filtering

`VaultFilter`: `ALL`, `FAVORITES`, `ACTIVE`, `STORED`. Cycles on click, same idiom as sort. All four are answerable from the snapshot plus metadata already in hand — no I/O.

### Controls

Occupied vault slots are **45, 48, 49, 50, 53** (verified: `PlayerPetMenuRenderer.java:48,59,57,62,53`). Free: **46, 47, 51, 52**. Sort takes 46, filter takes 47.

`fill()` paints all 54 slots with filler first, so "free" means action-free, not item-free. Also fix the now-stale comment at `:58` claiming slot 48 is free — Phase 6 of the previous plan took it.

### Empty and last-page states

- Zero pets owned: a centered item saying the vault is empty and how to get a pet (`/pet hatch`), not 45 filler panes.
- Filter matches nothing: a **distinct** message naming the active filter and how to clear it. Conflating this with an empty vault is what the Studio's list screen currently gets wrong.
- Last page: the NEXT arrow currently *disappears*, which reads as a glitch. Replace with a disabled-looking control stating "last page".

### Overflow

The status row shows `vaultOverflow()` as a bare count. Extend the lore with what it means (read-only, nothing deleted) and the two ways out — release a pet, or buy capacity.

### MessageKey colour discipline

`RendererTextContractTest.java:99-116` iterates every `MessageKey` whose path starts with `gui.` and requires an explicit colour tag in the default, unless the path is in a hardcoded `rendererColoured` allowlist. **Every new `gui.vault.*` key must carry a colour tag.** Do not widen the allowlist — that erodes the guard.

### File size

`PlayerPetMenuRenderer` is 120 lines. View state, two controls, three distinct states, and extended overflow lore will push it past the project's 200-line rule. Budget a split — extract the control row and state items into a `VaultMenuControls` helper. `RendererTextContractTest.java:122-131` hardcodes the renderer's path, so the file must remain present and `GuiItems`-based.

## Related Code Files

- Create: `paper/gui/player/VaultViewState.java`, `VaultSortOrder.java`, `VaultFilter.java`, `VaultPetView.java` (pure filter+sort function), `VaultMenuControls.java`
- Create: `paper/gui/player/VaultPetViewTest.java`, `VaultSortOrderTest.java`
- Modify: `paper/gui/player/PlayerPetMenuRenderer.java` — view state, controls, states, overflow lore, stale comment at `:58`
- Modify: `paper/gui/player/PlayerPetInventoryHolder.java` — carry view state, add `SORT` and `FILTER` actions
- Modify: `paper/gui/player/VaultPetSummary.java` — add the `extensions()`-based favorite read
- Modify: `paper/player/PlayerPetController.java` — `openVault` overload, handle the two actions, thread view state through `toggle`'s re-render (`:244`)
- Modify: `paper/text/MessageKey.java` — `gui.vault.*` keys, each with an explicit colour tag

## Implementation Steps

1. Extend `VaultPetSummary` with the favorite read via `PetManagementMetadata.read`.
2. Add `VaultSortOrder` with total, stable comparators; ties break on pet UUID; absent level/rarity sorts last.
3. Add `VaultFilter` with the four modes.
4. Add `VaultPetView` as a pure function: snapshot + view state → ordered, filtered list. No Bukkit types, so it unit-tests directly.
5. Add `VaultViewState`; carry it on the holder with defaulted fields so existing constructors keep working.
6. Extract `VaultMenuControls`; add sort at 46 and filter at 47.
7. Add the empty, no-match, and last-page states as distinct messages, each colour-tagged.
8. Extend the overflow lore; fix the stale comment at `:58`.
9. Add the `openVault(Player, VaultViewState)` overload; route `SORT`/`FILTER`; thread view state through `toggle`'s re-render; reset page to 1 when sort or filter changes.
10. Add `FeedbackCategory.PROGRESS` on sort/filter cycle (Phase 2 dependency).
11. Tests: each comparator is total and stable across repeated sorts; ties break deterministically; absent level sorts last; each filter mode selects correctly; empty vault and no-match render **different** messages; last page is labelled; page resets on sort/filter change; `toggle` preserves view state; favorite read works on a pet with no `management` node.

## Success Criteria

- [ ] All five sort orders work; every comparator is total and stable across repeated sorts (asserted).
- [ ] Ties break on pet UUID, so no pet appears on two pages or none (asserted).
- [ ] A pet with no readable level sorts last in `LEVEL_DESC` (asserted).
- [ ] All four filter modes select correctly, including a pet with no `management` extension node.
- [ ] `FAVORITES_FIRST` works with **no new repository read** (asserted by source grep).
- [ ] Empty vault and zero-filter-match render **different** messages (asserted).
- [ ] The last page is explicitly labelled; NEXT no longer silently vanishes.
- [ ] Overflow lore names both remedies.
- [ ] Controls occupy 46 and 47; no existing control displaced; the `:58` comment is corrected.
- [ ] Every new `gui.vault.*` default carries a colour tag; `rendererColoured` is **not** widened.
- [ ] `PlayerPetMenuRenderer` stays under 200 lines and remains `GuiItems`-based.
- [ ] `toggle` preserves view state (asserted).
- [ ] View-state reset on command round-trip is documented as accepted behavior.
- [ ] `PlayerPetInventoryHolderTest` and existing click tests pass unmodified.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| An unstable comparator puts a pet on two pages or none | Every comparator ties on pet UUID; repeated-sort test. Highest-value test in the phase. |
| Treating absent level as 0 misranks legacy pets | `VaultPetSummary` returns `Optional`; absent sorts last; asserted. |
| Favorite read throws on a pet with no management node | `PetManagementMetadata.read` handles an absent node; asserted with a bare pet. |
| Widening the holder breaks click tests | New fields defaulted; existing constructors preserved; pre-existing tests must pass unmodified. |
| Renderer outgrows the 200-line rule | `VaultMenuControls` split is budgeted in step 6, not deferred. |
| New keys break `RendererTextContractTest` | Colour tags required on every new key; allowlist explicitly not widened. |
| View state lost on round-trip surprises players | Documented as accepted, with the reason (touching the purchase flow for a display concern is worse). Follow-up if it annoys. |
| Filtering a large vault on the main thread | In-memory over a loaded snapshot, off a click, never per tick. Bounded by `effectiveVaultCapacity`. |
