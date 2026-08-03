# Phase 1 baseline and visible-string worklist

Plan: `plans/260803-1146-omnipet-ux-overhaul-help-tab-complete-message-catalog-studio-input-player-hub/`
Recorded: 2026-08-03
Branch: `feature/omnipet-pet-studio` at `ea76eb6`

## 1. Build baseline

`gradlew.bat clean build --no-daemon --console=plain` — **BUILD SUCCESSFUL**, 18 actionable tasks.

| Module | Suites | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| `omnipet-core` | 62 | 238 | 0 | 0 | 0 |
| `omnipet-paper` | 70 | 223 | 0 | 0 | 0 |
| **Total** | **132** | **461** | **0** | **0** | **0** |

Artifact `build/release/OmniPet-3.0.0-SNAPSHOT.jar` — 1,589,478 bytes,
SHA-256 `0DC987AED51CD28775627DE7491850AB5FF1684D5C1ACE83A33746BE19B92386`.
Identical to the metrics `README.md:68` records, so the branch starts from the published checkpoint.

Guard tasks green: `checkBranding`, `checkCoreBoundary`, `checkDistributionArtifact`, `checkGradleOnly`.

Every later phase compares against these numbers. Test counts may only grow.

## 2. Classification rule

- `PLAYER` — a non-staff player can see this line during normal play. Becomes a `MessageKey`.
- `OPERATOR` — only a console or admin sender sees it, and it is an audit/diagnostic line
  (transaction pages, opaque cursors, reconcile reasons, offline inspect dumps, delivery receipts).
  Stays hardcoded in Java so an edited YAML file cannot distort an audit record.
- `STUDIO` — interactive Admin Studio UI text (field prompts, modifier explanations, screen labels).
  Catalog-owned, but migrated in **Phase 4**, not Phase 2. Studio *diagnostic* strings that embed
  `StudioErrorMessages.forAdmin(error)` keep their Java prefix and are marked `OPERATOR`.

Classification is per call site, never per file: `IncubationActionItemController` holds both classes.

## 3. Chat worklist — 80 `sendMessage` call sites across 14 files

### Phase 2 migration targets (PLAYER)

| File | Lines | Class | Note |
| --- | --- | --- | --- |
| `player/PlayerPetController.java` | 162, 187-188, 224-227, 241-243, 311 | PLAYER | 5 sites. `:242` also needs `Displays.status` in Phase 5. |
| `player/PlayerHatchController.java` | 375 (`message` helper) | PLAYER | Helper is the only sink; 19 call sites feed it. |
| `player/PlayerSlotPurchaseController.java` | 325 (`message` helper) | PLAYER | Helper is the only sink; 14 call sites feed it. |
| `skill/PaperActiveSkillController.java` | 277 (`message(Player,…)`) | PLAYER | Cast feedback. |
| `skill/PaperActiveSkillController.java` | 281 (`message(CommandSender,…)`) | OPERATOR | `/pet admin skill` inspect/rollback audit rows. Not migrated. |
| `incubation/action/IncubationActionItemController.java` | 128 (`message(Player,…)`) | PLAYER | Redemption feedback. |
| `incubation/action/IncubationActionItemController.java` | 79, 107, 112, 114, 119 | OPERATOR | Delivery receipts and usage. Not migrated. |

`management/PetManagementMenuSupport.java:86` (`message` helper, 12 call sites) is PLAYER but is
**not** a Phase 2 target. It is the chat half of the management GUI, so it migrates in Phase 5 with
`PetManagementMenuRenderer` — one edit per feature instead of two.

### Command dispatcher (PLAYER, Phase 3)

`command/OmniPetCommand.java` — 30 sites. The usage/permission strings at
`:314, :318, :322, :330, :334, :338, :344, :352, :355, :360, :369, :371, :380, :382, :391, :393,
:401, :409, :413, :417, :432, :440, :448, :451, :467, :473, :480, :484, :491, :495`
are duplicated usage text. Phase 3 replaces the *usage* subset with generated help from
`OmniPetCommandTree`; the permission-denied and not-available subset becomes `error.*` keys.

`command/FoundationCommand.java:12` — PLAYER, single line, migrate with the `command.*` group.

### Deliberately untouched (OPERATOR)

| File | Sites | Why |
| --- | ---: | --- |
| `economy/SlotTransactionAdminController.java` | 3 | Transaction pages and opaque cursors are an audit trail. |
| `incubation/HatchAdminController.java` | 1 | Offline inspect dump. |
| `management/PaperCultivationAdminCommandTarget.java` | 7 | Pending/review/recover audit output. |
| `management/PetCultivationItemController.java` | 5 | Item delivery receipts and usage. |
| `release/PaperReleaseAdminCommandTarget.java` | 2 | Reconcile reasons. |

### Studio (STUDIO — Phase 4)

`studio/bukkit/PetStudioController.java` — 15 sites at
`:188, :222, :241, :252, :291, :295, :305, :308, :312, :316, :322, :330, :347, :352, :421`.
Interactive: `:188, :222, :252, :291, :305, :308, :312, :322, :330, :347, :421`.
Diagnostic (`StudioErrorMessages.forAdmin`): `:241, :295, :316, :352` — keep the Java detail suffix.
`:421` (`invalid <field>. Try again or type cancel.`) is the error half of the prompt gap Phase 4 fixes.

## 4. GUI worklist — titles and item text (Phase 5)

Six `Bukkit.createInventory` titles, all `Component.text(..., GOLD)` with no location context:

| Renderer | Line | Current title |
| --- | ---: | --- |
| `gui/player/PlayerPetMenuRenderer` | 31 | `OmniPet Vault \| <page>/<pages>` |
| `gui/hatch/HatchMenuRenderer` | 32 | `OmniPet Hatch` |
| `gui/player/PetManagementMenuRenderer` | 24 | `OmniPet \| Manage` / `OmniPet \| Confirm Release` (`:82`, `:100`) |
| `gui/player/SlotPurchaseMenuRenderer` | 40 | `Unlock active slot <slot>` |
| `gui/player/SlotPurchaseMenuRenderer` | 80 | `Confirm slot <slot>` |
| `studio/bukkit/StudioInventoryRenderer` | 216 | five callers: Tiers / `<tier>` / `Edit <id>` / Stats / Archive |

## 5. Italic defect — confirmed

`grep -rn "TextDecoration" omnipet-paper/src/main` returned nothing before this phase. Every lore
line and display name therefore rendered vanilla italic. Five private `item(...)` builders each
reproduced the omission:

| Renderer | Builder | Extra helper to delete in Phase 5 |
| --- | ---: | --- |
| `PlayerPetMenuRenderer` | `:81` | `abbreviate` `:92` |
| `PetManagementMenuRenderer` | `:126` | `shortId` `:136` |
| `SlotPurchaseMenuRenderer` | `:117` | — |
| `HatchMenuRenderer` | `:85` + `:95` overload | — |
| `StudioInventoryRenderer` | `:229` | `abbreviate` `:250` |

## 6. Shipped this phase

`paper/gui/GuiItems.java` — the sole builder. `label`, `lore`, `upright`, `of(Material,…)`,
`of(ItemStack,…)`, `filler()`. Italic is resolved with `decorationIfAbsent(ITALIC, FALSE)`, so a
caller that deliberately set italic keeps it while everything else renders upright.
`GuiItemsTest` asserts `FALSE` (not `NOT_SET`) on names and lore, that an explicit italic survives,
that a `SkullMeta` stack keeps its own meta instance, and that a meta-less stack is rejected rather
than silently dropping its text.

No renderer is migrated yet, so rendered output is byte-identical to the baseline.

## Unresolved questions

None.
