---
phase: 4
title: "Studio input UX"
status: complete
priority: P1
effort: "1.5d"
dependencies: [2]
---

# Phase 4: Studio input UX

## Overview

Fix the two authoring dead-ends: the stat value prompt that never explains what a modifier is, and the head icon field that rejects a pasted base64 blob. Also make the stat picker readable instead of 45 identical paper sheets.

## Requirements

- Functional:
  - Clicking a catalog stat opens a modifier screen with one explained button per modifier the stat supports.
  - The follow-up chat prompt states format, a concrete example, the accepted range, and `cancel`.
  - Chat accepts both `10 50` (modifier pre-chosen) and `FLAT 10 50` (full form, legacy-compatible).
  - Head field accepts a pasted base64 payload, an `http(s)` texture URL, a bare 64-hex texture hash, and the legacy `<SOURCE> <value>` form.
  - Stat entries render distinct materials with grouped lore.
- Non-functional: every new screen carries `StudioViewToken` and honors `sessions.isCurrent`; validation stays in `omnipet-core`; no new vendor call.

## Architecture

### Prompted chat input

`PetStudioController.awaitField` (`:400-415`) closes the inventory and waits — silently. Only `awaitHardDelete` (`:330`) sends a prompt, which is why that one field is usable and the others are not.

Add a required prompt to the await path: `awaitField(..., MessageKey promptKey)`. Every existing caller supplies a key. The prompt block is three lines — what is being edited, format + example, `type cancel to abort` — sent immediately before `player.closeInventory()`. `StudioDraftInputParsers` messages already surface through `inputFailure` (`:421`); the prompt is what was missing, not the error path.

### Modifier selection

`StatModifierType` has exactly three values (`core/studio/StatModifierType.java`). `catalogStat` currently demands `<modifier> <min> <max>` as three tokens (`StudioDraftInputParsers.java:66-80`) with no hint of what `FLAT` means — the reported "nhập cả chữ cả số đều không được".

New screen `StudioInventoryHolder.Screen.STAT_MODIFIER`, new `StudioActionType.STAT_MODIFIER`, new `StudioState` fields `pendingStatId` + `pendingModifier`. Flow:

1. Click stat → store `pendingStatId`, render `STAT_MODIFIER`.
2. Screen shows one button per `entry.supportedModifierTypes()`, each with an explanatory lore line:
   - `FLAT` — adds a fixed amount (`+10 attack damage`)
   - `RELATIVE` — adds a percentage of the base value (`+10%`)
   - `ADDITIVE_MULTIPLIER` — stacks additively with other multipliers
   Unsupported modifiers are omitted, so the screen cannot offer a choice `catalogStat` would then reject.
3. Click modifier → store `pendingModifier`, prompt for `min max`, resume on `STAT_PICKER`.

Parser change in `StudioDraftInputParsers`:

- `catalogStat(String input, StatCatalogEntry entry, StatModifierType preselected)`:
  - 2 tokens → use `preselected` (must be non-null), parse min/max.
  - 3 tokens → parse modifier from token 0, ignoring `preselected`. Preserves today's behavior and the existing test at `StudioDraftInputParsersTest.java:47`.
  - Modifier not in `entry.supportedModifierTypes()` → reject with the same message as today.
- Keep the 2-arg overload delegating with `preselected = null` so `StudioDraftInputParsersTest` keeps compiling unchanged.

The manual-list path (`STAT_MANUAL` → `stats(String)`) is untouched: it still accepts `id MODIFIER min max;...` for bulk entry and remains the escape hatch when MythicLib is absent.

### Head icon auto-detection

`icon(String)` requires two tokens (`:27-35`). Replace with detection, then validate:

| Input shape | Resolved |
| --- | --- |
| Legacy `<TEXTURE_URL\|BASE64\|HEAD_CATALOG> <value>` | unchanged (checked first, so a two-token input can never be misread) |
| starts `http://` or `https://` | `TEXTURE_URL` |
| 64 hex chars, no separator | `TEXTURE_URL` with `https://textures.minecraft.net/texture/<hash>` |
| otherwise single token, base64 alphabet, decodes to a `textures.SKIN.url` object | `BASE64` |

Detection only picks the source. The authoritative check stays in
`PetDefinitionStudioService.validateBase64Texture` (`core/studio/PetDefinitionStudioService.java:327-353`), which already parses the JSON, requires `textures.SKIN.url`, enforces absolute HTTP(S), and caps at 16 KiB — the user's sample payload satisfies all of it. To fail fast in chat instead of at Save, the parser calls a small core-side `HeadIconSources.detect(String)` helper that reuses the same rules; core keeps ownership of format truth and gains no Bukkit import.

`HEAD_CATALOG` stays reachable only through the explicit two-token form (no provider ships yet).

### Stat picker rendering

`StudioInventoryRenderer.stats` (`:151-163`) uses `PAPER`/`LIME_DYE` for every row and prints `Set.toString()` of modifiers. Replace with:

- Material by meaning, then state. `StatMaterialPalette` maps the stat's logical key to a semantic icon by keyword:
  | Keyword in stat ID | Icon |
  | --- | --- |
  | `attack`, `damage`, `weapon` | `IRON_SWORD` |
  | `defen`, `armor`, `resist` | `SHIELD` |
  | `health`, `regen`, `heal` | `GOLDEN_APPLE` |
  | `speed`, `movement`, `haste` | `FEATHER` |
  | `magic`, `mana`, `spell` | `ENCHANTED_BOOK` |
  | `crit`, `luck` | `GOLD_NUGGET` |
  | `range`, `projectile`, `bow` | `ARROW` |
  | no match | `PAPER` (today's default) |
  Selection and health override the icon: selected → `LIME_DYE`; degraded catalog → `RED_STAINED_GLASS_PANE`. Matching is lowercase substring on the logical key, first match wins, order fixed by the table so the result is deterministic.
- Lore block: display name (already the item name), `ID: <id>`, `Provider: <provider>`, `Modifiers: Flat, Relative` (human-readable names, not enum `toString`), and either `Selected: FLAT 10 → 50` or `Click to configure`.

Material derivation is presentation-only; `StatLogicalIdentity.key` remains the identity used for selection matching (`:153-155`), so no persistence behavior changes. A wrong-looking icon is cosmetic and cannot affect what is saved.

## Related Code Files

- Create: `omnipet-core/src/main/java/io/github/salyvn/omnipet/core/studio/HeadIconSources.java`
- Create: `core/studio/HeadIconSourcesTest.java`
- Create: `paper/studio/bukkit/StatModifierPresentation.java` (display names + explanation keys)
- Create: `paper/studio/bukkit/StatMaterialPalette.java` + `StatMaterialPaletteTest.java` (keyword→icon table, deterministic first-match)
- Modify: `studio/bukkit/StudioDraftInputParsers.java` — `icon` detection, `catalogStat` preselected overload
- Modify: `studio/bukkit/PetStudioController.java` — prompt-carrying `awaitField`, `STAT_MODIFIER` flow, `awaitStat` split
- Modify: `studio/bukkit/StudioInventoryRenderer.java` — modifier screen + stat rows (extract stat screens to `StudioStatScreens.java` if the file passes 200 lines; it is at 251 already, so plan on the split)
- Modify: `studio/bukkit/StudioActionType.java`, `StudioInventoryHolder.java` (new `Screen`), `StudioState.java` (pending fields)
- Modify: `text/MessageKey.java` — `studio.prompt.*`, `studio.modifier.*`, `studio.stat.*`
- Modify: `studio/bukkit/StudioDraftInputParsersTest.java` — add cases; keep existing assertions intact

## Implementation Steps

1. Add `HeadIconSources.detect` in core with the table above; unit-test the user's exact base64 sample, a texture URL, a bare hash, the legacy two-token form, and rejects (bad base64, JSON without `SKIN.url`, non-HTTP url, oversize).
2. Rewrite `StudioDraftInputParsers.icon` to delegate to `HeadIconSources.detect`, preserving legacy two-token precedence.
3. Add the `catalogStat` preselected overload; keep the 2-arg delegate.
4. Extend `StudioState` with `pendingStatId` / `pendingModifier`; clear both whenever a stat flow completes, is cancelled, or the session closes, so a stale modifier cannot be reused by the next stat.
5. Add `Screen.STAT_MODIFIER` + `StudioActionType.STAT_MODIFIER`; route in `PetStudioController.click` and in `render`'s switch.
6. Split stat screens out of `StudioInventoryRenderer` into `StudioStatScreens`; implement the modifier screen and the new stat rows there.
7. Add the prompt parameter to `awaitField` and update all nine callers (`icon`, `display`, `rarity`, `progression`, `skills`, `behavior`, `release`, `stats` manual, `stats.<id>`), each with format + example.
8. Route `STAT` clicks to the modifier screen; route `STAT_MODIFIER` clicks to the min/max prompt.
9. Tests: parser cases above; modifier screen offers only supported modifiers; a stale token click renders nothing and mutates no draft; `pendingModifier` is cleared on cancel; `10 50` and `FLAT 10 50` both produce the same `StudioStat`; `StatMaterialPalette` is deterministic, first-match ordered, and falls back to `PAPER` for an unmatched ID.

## Success Criteria

- [x] Pasting the user's raw base64 into the head field is accepted; the editor slot previews the head.
- [x] `TEXTURE_URL`, bare 64-hex hash, and legacy `<SOURCE> <value>` all still work.
- [x] Clicking a stat shows explained modifier buttons limited to supported types.
- [x] Every Studio chat prompt names the field, format, an example, and `cancel`.
- [x] Both `10 50` and `FLAT 10 50` are accepted; unsupported modifiers rejected as before.
- [x] Stat rows show semantic icons and grouped lore with human-readable modifier names.
- [x] `StudioDraftInputParsersTest` passes with its original assertions plus new ones.
- [x] Save-time validation and `StudioCatalogSelections` behavior are unchanged.

Report: [`plans/reports/phase-04-studio-input-ux-report.md`](../reports/phase-04-studio-input-ux-report.md).
142 suites / 564 tests (+42). `StudioInventoryRenderer` 251 → 216 lines via `StudioStatScreens`.

Deviations from the plan, both deliberate:

1. Prompt text lives in a new `StudioFieldPrompt` enum rather than being passed as a `MessageKey`
   argument to `awaitField`. One enum entry per field keeps name, format, and example together, and
   makes "every field has a prompt" assertable.
2. `StatMaterialPalette` orders the magic row **before** health. `mana_regeneration` otherwise matched
   `regen` first and rendered a golden apple. `projectile_damage` still resolves to `IRON_SWORD`
   because `damage` precedes `projectile`; that collision is now pinned by a test rather than
   reordered, since a projectile damage stat is still a damage stat.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| Detection misclassifies a legacy value | Two-token legacy form is matched first and returns immediately. |
| Base64 accepted in chat but rejected at Save | Both paths call the same core rules; add a test asserting a chat-accepted payload also passes `validateBase64Texture`. |
| Extra screen widens the stale-view surface | New screen uses the same `nextView`/`isCurrent` discipline; explicit stale-click test. |
| `StudioInventoryRenderer` outgrows 200 lines | Split to `StudioStatScreens` in step 6, not later. |
| Leftover `pendingModifier` applied to the wrong stat | Cleared on completion, cancel, session close; asserted by test. |
