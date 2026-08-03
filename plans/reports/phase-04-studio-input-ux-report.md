# Phase 4 Studio input UX

Plan: `plans/260803-1146-omnipet-ux-overhaul-help-tab-complete-message-catalog-studio-input-player-hub/`
Recorded: 2026-08-03

## Root causes fixed

| Report | Cause | Fix |
| --- | --- | --- |
| "nhập cả chữ cả số đều không được" | `awaitField` closed the inventory and waited for chat **without sending anything** (`PetStudioController:400-415`). Only `awaitHardDelete` prompted, which is why that one field worked. | Prompting is now structurally required — the capturing overload takes a `Runnable sendPrompt`. |
| Modifier meaningless | `catalogStat` demanded three tokens with no hint what `FLAT` meant. | Click-to-choose screen with an explanation and example per modifier; chat still accepts `FLAT 10 50`. |
| Pasted base64 rejected | `icon(String)` required two tokens, so a one-token blob failed — even though save-time validation already accepted that exact payload. | `HeadIconSources.detect` in core; four accepted shapes. |
| Stat picker "all paper" | `stats` hardcoded `PAPER`/`LIME_DYE` and printed `Set.toString()`. | Semantic icons by keyword + grouped lore with readable modifier names. |

## Shipped

| File | Role |
| --- | --- |
| `core/studio/HeadIconSources.java` | Detection + validation. Core-side, import-clean. |
| `paper/studio/bukkit/StudioFieldPrompt.java` | 12 fields, each with name, format, example. Sends the four-line block. |
| `paper/studio/bukkit/StatModifierPresentation.java` | Labels, explanations, examples, range hints. |
| `paper/studio/bukkit/StatMaterialPalette.java` | Ordered keyword→icon table. |
| `paper/studio/bukkit/StudioStatScreens.java` | Picker + modifier screens, split out of the renderer. |

`StudioInventoryRenderer` 251 → 216 lines. `PetStudioController` 465 → 583; it absorbed the two-step
flow, and the prompt text itself lives in `StudioFieldPrompt` rather than inline.

## Head icon shapes accepted

| Input | Resolved |
| --- | --- |
| `<TEXTURE_URL\|BASE64\|HEAD_CATALOG> <value>` | unchanged — checked **first**, so a two-token input can never be misread |
| `http(s)://…` | `TEXTURE_URL` |
| 64 hex chars | `TEXTURE_URL` + `https://textures.minecraft.net/texture/<hash>`, lowercased |
| single token decoding to `textures.SKIN.url` | `BASE64` |

`HEAD_CATALOG` stays reachable only through the explicit form — no provider ships yet, so a bare
`wolf_head` is rejected rather than silently becoming a catalog reference.

Detection applies the **same** rules as `PetDefinitionStudioService.validateBase64Texture` (16 KiB
cap, decodable, `textures.SKIN.url`, absolute HTTP(S)). A test drives a chat-accepted payload through
a real `service.save` to prove chat and Save cannot disagree.

## Two-step stat flow

Click stat → `pendingStatId` stored, modifier screen rendered with one button per **supported**
modifier, so the screen cannot offer a choice `catalogStat` would reject. Click modifier → prompt asks
only `min max`.

`pendingModifier` is cleared on completion, cancel, expiry, `BACK` from either stat screen, a stale
catalog entry, and an unsupported modifier — a stale modifier can never be applied to the next stat.
A stale-catalog click between the two steps falls back to the picker instead of rendering a screen for
a stat that no longer exists.

## Two bugs the tests caught

1. **Palette ordering.** `mana_regeneration` resolved to `GOLDEN_APPLE` because the health row
   preceded magic and `regen` matched first. Magic now precedes health; `health_regeneration` is
   unaffected because it matches no magic keyword.
2. **`projectile_damage` → `IRON_SWORD`**, since `damage` precedes `projectile`. Left as-is — it *is*
   damage — but the collision is now pinned by a test so a future reorder cannot silently reshuffle
   the picker. Three collision outcomes are asserted explicitly.

## Verification

`gradlew.bat clean build --no-daemon --console=plain` — **BUILD SUCCESSFUL**, all guard tasks green,
including `checkCoreBoundary` against the one new core file.

| Module | Suites | Tests | Δ vs Phase 3 |
| --- | ---: | ---: | --- |
| `omnipet-core` | 63 | 256 | +1 suite, +18 tests |
| `omnipet-paper` | 79 | 308 | +2 suites, +24 tests |
| **Total** | **142** | **564** | +42 |

`StudioDraftInputParsersTest` passes with all four original assertions intact plus seven new ones.
`StudioInputPromptContractTest` guards the fix structurally: it walks the controller source and fails
if any `closeInventory()` that precedes an `inputs.await(` is not preceded by a prompt.

## Unresolved questions

None.
