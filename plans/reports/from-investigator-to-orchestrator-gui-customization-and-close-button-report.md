# GUI close-button bug + customization gap inventory

Read-only investigation. All claims verified by reading source. Paths relative to
`D:\Project\.1_PROJECT_SL_PLUGINS\OmniBundle\OmniPet`.

---

## 1. Close button root cause (REPORT 1)

### The broken button

**Drawn at:** `omnipet-paper/src/main/java/io/github/salyvn/omnipet/paper/studio/bukkit/StudioInventoryRenderer.java:45`

```java
inventory.setItem(22, item(Material.BARRIER, "Close", NamedTextColor.RED, "Exit OmniPet Studio"));
```

Screen: `StudioInventoryHolder.Screen.TIERS` — the Studio landing screen, the admin pet-management
entry point (`PetStudioController.openBrowse` -> `render(state, Screen.TIERS)`,
`PetStudioController.java:117-119`, `:581`).

### Cause: drawn-but-unbound slot. Two independent gaps, both must be fixed.

**Gap A — no action registered for slot 22.**
`StudioInventoryRenderer.java:32-46` builds the `actions` map for the TIERS screen. Only tier slots
are populated: `actions.put(slots[index], ...)` at `:40` where `slots = {10, 11, 12, 14, 15}`
(`:36`). There is **no `actions.put(22, ...)`** anywhere in `tiers()`. Contrast with every other
Studio screen, where each drawn control has a matching `actions.put`:
- `:71-76` list screen registers 45/46/48/49/50/53 before drawing them at `:77-85`
- `:95-107` editor registers 10-16/19/20/44/45/49/53
- `:163-165` archive-confirm registers 11/13/15
Slot 22 on TIERS is the only drawn interactive-looking item in the whole Studio with no map entry.

**Handler that fails to act:**
`omnipet-paper/src/main/java/io/github/salyvn/omnipet/paper/studio/bukkit/PetStudioListener.java:44-45`

```java
StudioAction action = holder.action(event.getRawSlot());
if (action != null) controller.click(player, holder, action);
```

`holder.action(22)` returns `null` (`StudioInventoryHolder.java:46`, backed by the unmodifiable map
built at `:32`), so the listener falls off the end. The click was already cancelled at
`PetStudioListener.java:35` (`event.setCancelled(true)`), so the visible result is: item does not
move, nothing else happens, inventory stays open. Exactly the reported symptom.

**Gap B — no CLOSE branch in the click switch.**
Even with an action bound, there is nothing to dispatch to. `StudioActionType`
(`omnipet-paper/.../studio/bukkit/StudioActionType.java:3-32`) has 28 members —
`TIER, PET, CREATE, SEARCH, TOGGLE_ARCHIVE, PREVIOUS, NEXT, BACK, EDIT_*, SAVE, CANCEL,
CONFIRM_ARCHIVE, CANCEL_ARCHIVE, HARD_DELETE, STAT*, CLONE` — and **no `CLOSE`**. The switch in
`PetStudioController.click` (`PetStudioController.java:156-212`) therefore has no close branch. Note
`CANCEL` at `:202` is not a close: it calls `openBrowse(player, state.tier, true)`, i.e. re-opens
the list screen. `BACK` at `:172-183` likewise navigates, never closes.

### Ruled out (checked, not the cause)

- **No slot index mismatch.** Drawn slot 22 == the slot the handler queries (`getRawSlot()`); the
  22 is simply absent from the map. Inventory is 27 slots (`:34`) so 22 is in range and passes the
  bounds guard.
- **The guard is not the blocker.** `StudioInventoryActionPolicy.click`
  (`omnipet-paper/.../studio/view/StudioInventoryActionPolicy.java:29-43`) allows viewer-matched
  LEFT/RIGHT clicks on in-range top-inventory slots. A plain left click on slot 22 returns
  `allow()`. The early return at `PetStudioListener.java:43` is not reached.
- **Permission / session guards are not the blocker.** `PetStudioController.click:152-155` checks
  `omnipet.admin.managepet`, `sessions.isCurrent(token)` and `state.matches(token)` — but these run
  *after* the listener already gave up, so they are downstream of the failure, not the cause.
- **Not a `closeInventory()`-inside-event problem.** `player.closeInventory()` is never called on a
  close-button path in the Studio; the only Studio call sites are the chat-prompt flows
  (`PetStudioController.java:294, 353, 460, 491, 513, 551`) and lifecycle cleanup (`:619, :632`).

### The working comparison (player side)

Two references, both structurally correct:

1. **Vault hub button** — drawn `PlayerPetMenuRenderer.java:80-81` (slot 48, `Material.COMPASS`,
   `MessageKey.HUB_BACK`), action registered one line earlier at `:79`
   (`actions.put(48, PlayerPetInventoryHolder.Action.hub())`), dispatched by
   `PlayerPetMenuListener.java:48-54` into `PlayerPetController.click`, which has an explicit
   `case HUB ->` branch at `PlayerPetController.java:127-130`.
2. **Management back/hub buttons** — drawn `PetManagementMenuRenderer.java:96-102` (slot 35
   `BARRIER`/`GUI_MANAGE_BACK`, slot 27 `COMPASS`/`HUB_BACK`) via the `put(...)` helper
   (`:168-178`) that writes the action map and the item entry **together in one call** — which is
   precisely the invariant the Studio's hand-rolled two-step `actions.put` + `setItem` violates.
   Dispatched by `PetManagementMenuListener.java:24-27` through
   `PetManagementInventoryGuard.acceptsAction` (which additionally requires
   `holder.action(rawSlot) != null`, `PetManagementInventoryGuard.java:25`) into
   `PetManagementMenuController.click:75-84`, which handles BACK/HUB before async dispatch.

### Full close/back button census

| Screen | file:line drawn | slot | action bound? | switch branch? | works |
|---|---|---|---|---|---|
| Studio TIERS "Close" | `StudioInventoryRenderer.java:45` | 22 | **NO** | **NO (`CLOSE` type absent)** | **BROKEN** |
| Studio LIST "Back to tiers" | `StudioInventoryRenderer.java:82` | 49 | yes `:74` | `BACK` `PetStudioController.java:172` | ok |
| Studio EDITOR "Back" | `StudioInventoryRenderer.java:130` | 45 | yes `:105` | `BACK` | ok |
| Studio EDITOR "Cancel" | `StudioInventoryRenderer.java:132` | 53 | yes `:107` | `CANCEL` `:202` (navigates, not close) | ok |
| Studio ARCHIVE "Cancel" | `StudioInventoryRenderer.java:170` | 15 | yes `:165` | `CANCEL_ARCHIVE` `:204` | ok |
| Studio STAT_PICKER "Back to editor" | `StudioStatScreens.java:83` | 49 | yes `:73` | `BACK` | ok |
| Studio STAT_MODIFIER "Back to stats" | `StudioStatScreens.java:128` | 22 | yes `:127` | `BACK` | ok |
| Management "Back to vault" | `PetManagementMenuRenderer.java:96-98` | 35 | yes (same `put`) | `BACK` `PetManagementMenuController.java:75` | ok |
| Management "Back to hub" | `PetManagementMenuRenderer.java:100-102` | 27 | yes | `HUB` `:76` | ok |
| Release "Cancel" | `PetManagementMenuRenderer.java:127-130` | 11 | yes `:127` | `CANCEL_RELEASE` `:130` | ok |
| Vault "Back to hub" | `PlayerPetMenuRenderer.java:79-81` | 48 | yes | `HUB` `PlayerPetController.java:127` | ok |
| Hatch "Back to hub" | `HatchMenuRenderer.java:60-62` | 18 | yes `:60` | (HatchInventoryHolder.Action.hub) | ok |
| Slot select "Back to vault" | `SlotPurchaseMenuRenderer.java:61-63` | 22 | yes `:61` | cancel | ok |
| Slot confirm "Cancel" | `SlotPurchaseMenuRenderer.java:96-97` | 15 | yes `:85` | cancel | ok |

**Conclusion: exactly one broken close button in the plugin, and it is the Studio TIERS "Close" at
`StudioInventoryRenderer.java:45`.** The admin management GUI (`paper/management/**` +
`gui/player/PetManagement*`) close buttons are all correctly wired — the bug report's "admin pet
management GUI" maps to the Studio landing screen.

### Secondary observation (not the reported bug)

`PetStudioController` calls `player.closeInventory()` synchronously inside the click-event call chain
(`:294, :353, :460, :491, :513, :551` — all reached from `click()` via `awaitField`/`awaitId`/
`awaitSearch`/`awaitCloneId`/`awaitStatSearch`/`awaitHardDelete`). Paper tolerates this, and these
paths also reopen via a scheduled task, so no defect observed — noted only because a naive
"add CLOSE -> player.closeInventory()" fix would land on the same pattern.

---

## 2. GUI customization inventory (REPORT 2)

### What the operator controls today

Two, and only two, surfaces:

1. **`messages.yml`** — every `MessageKey` (generated from
   `omnipet-paper/.../text/MessageKey.java`), MiniMessage-formatted, so colours and wording are
   fully operator-owned for any string that goes through `Messages.line(...)`.
2. **`config.yml` `gui:` section** — loaded by `GuiConfigLoader.java` into `GuiConfig.java`.
   Contents (verified against `config.yml` `gui:` block and `GuiConfig.java:16-21`):
   - `gui.feedback.*` (sounds/action bar; not visual layout)
   - `gui.vault.petsPerPage` (default 45, capped `GuiConfig.MAX_VAULT_PETS_PER_PAGE = 45`,
     `GuiConfig.java:23`; read once at renderer construction, `PlayerPetMenuRenderer.java:30-36`)
   - `gui.help.linesPerPage`
   - `gui.studio.promptTimeoutSeconds`, `gui.studio.autoCreateEgg`
   **No slot, material, size, or title key exists.**

Also operator-controlled but unrelated to menus: `items.experienceCandy.material` and
`items.breakthroughStone.material` (`config.yml:126, :129`) — proof that a
material-from-config pattern already exists in this codebase.

### Titles

| Menu | Title source | file:line |
|---|---|---|
| Hub | `MessageKey.GUI_TITLE_HUB` | `HubMenuRenderer.java:52-53`; key `MessageKey.java:133` |
| Vault | `GUI_TITLE_VAULT` (`<page>`/`<pages>`) | `PlayerPetMenuRenderer.java:48-50`; key `:134` |
| Hatch | `GUI_TITLE_HATCH` | `HatchMenuRenderer.java:34-35`; key `:135` |
| Management | `GUI_TITLE_MANAGE` | `PetManagementMenuRenderer.java:121`; key `:136` |
| Release confirm | `GUI_TITLE_RELEASE` | `PetManagementMenuRenderer.java:144`; key `:137` |
| Slot select | `GUI_TITLE_SLOT_SELECT` | `SlotPurchaseMenuRenderer.java:42-43`; key `:138` |
| Slot confirm | `GUI_TITLE_SLOT_CONFIRM` | `SlotPurchaseMenuRenderer.java:80-81`; key `:140` |
| **Studio TIERS** | **hardcoded** `"OmniPet Studio \| Tiers"` | `StudioInventoryRenderer.java:34` |
| **Studio LIST** | **hardcoded** `"OmniPet Studio \| " + tier` | `StudioInventoryRenderer.java:52` |
| **Studio EDITOR** | **hardcoded** `"OmniPet Studio \| Edit " + id` | `StudioInventoryRenderer.java:92` |
| **Studio ARCHIVE** | **hardcoded** `"OmniPet Studio \| Archive"` | `StudioInventoryRenderer.java:161` |
| **Studio STAT_PICKER** | **hardcoded** `"OmniPet Studio \| Stats"` | `StudioStatScreens.java:35` |
| **Studio STAT_MODIFIER** | **hardcoded** `"OmniPet Studio \| " + stat` | `StudioStatScreens.java:103` |

All player-facing titles are text-driven. All six Studio titles are Java string literals. Colour of
Studio titles is also fixed: `GuiColors.TITLE` at `StudioInventoryRenderer.java:181`.

### Item names and lore

**From `messages.yml` (all of these):** hub tiles (`HubMenuRenderer.java:60-118`, keys
`HUB_*` `MessageKey.java:281-293`); vault rows, status, paging, sort, filter, empty states
(`PlayerPetMenuRenderer.java:65-135`, `VaultMenuControls.java:40-102`, keys `GUI_VAULT_*`
`MessageKey.java:143-178`); hatch tiles (`HatchMenuRenderer.java:43-102`, keys `GUI_HATCH_*`
`MessageKey.java:181-208`); slot purchase (`SlotPurchaseMenuRenderer.java:62-123`, keys
`GUI_SLOT_*` `MessageKey.java:211-222`); management + release confirm
(`PetManagementMenuRenderer.java:44-160`, keys `GUI_MANAGE_*` / `GUI_RELEASE_*`
`MessageKey.java:225-270`).

**Hardcoded (all Studio, without exception):** every Studio name and every lore line is a Java
literal passed to `StudioInventoryRenderer.item(...)` (`:197-201`) —
`StudioInventoryRenderer.java:41-45` (tier tiles, "Close"), `:67-85` (list rows, "Previous",
"Archive mode: ON/OFF", "Create definition", "Back to tiers", "Search", "Next"), `:108-132` (all 11
editor buttons), `:166-170` (archive confirm); `StudioStatScreens.java:50-129` (stat rows,
"Dynamic catalog unavailable", "No matching stats", "Manual stat list", "Refresh provider catalog",
"Back to editor", "Search stats", "Back to stats"), plus `StatModifierPresentation` labels/
explanations/examples. Studio lore also embeds runtime values by string concatenation
(e.g. `"Definitions: " + count` at `:43`), so no placeholder grammar exists there.

One deliberate non-GUI carve-out, documented at `MessageKey.java:12-15` and
`EggAdminController.java:46-48`: admin/console audit output stays in Java.

### Hardcoded materials (complete list with file:line)

Filler (shared by every menu): `Material.GRAY_STAINED_GLASS_PANE` — `GuiItems.java:72`.

Player menus:
- `HubMenuRenderer.java:60` `BOOK`; `:64` `CARTOGRAPHY_TABLE`; `:85` `ENDER_CHEST`;
  `:105` `LIME_DYE` / `DRAGON_EGG` / `EGG`; `:111` `EXPERIENCE_BOTTLE`
- `PlayerPetMenuRenderer.java:65` `ARROW`; `:73` `ARROW`; `:80` `COMPASS`; `:84`
  `EXPERIENCE_BOTTLE`; `:113` `LIME_DYE` / `PLAYER_HEAD`; `:132` `RED_STAINED_GLASS` /
  `ENDER_CHEST`
- `VaultMenuControls.java:41` `HOPPER`; `:54` `SPYGLASS`; `:67` `GRAY_STAINED_GLASS_PANE`;
  `:82` `ENDER_CHEST`; `:89` `BARRIER`
- `HatchMenuRenderer.java:43` `DRAGON_EGG`; `:46` `DRAGON_EGG`; `:56` `CLOCK`; `:61` `COMPASS`;
  `:67` `ENDER_CHEST`; `:84` `PLAYER_HEAD`
- `SlotPurchaseMenuRenderer.java:62` `ARROW`; `:87` `LIME_CONCRETE`; `:96` `RED_CONCRETE`;
  `:118` `BARRIER`; `:139` `GOLD_INGOT` / `NETHER_STAR`
- `PetManagementMenuRenderer.java:46` `NETHER_STAR` / `GRAY_DYE`; `:52` `TRIPWIRE_HOOK` /
  `IRON_NUGGET`; `:60` `ARROW`; `:66` `ARROW`; `:72` `EXPERIENCE_BOTTLE`; `:80`
  `AMETHYST_SHARD`; `:87` `BARRIER` / `LAVA_BUCKET`; `:94` `CLOCK`; `:98` `BARRIER`; `:102`
  `COMPASS`; `:107` `PLAYER_HEAD`; `:129` `BARRIER`; `:133` `LAVA_BUCKET`; `:135` `PAPER`

Studio:
- `StudioInventoryRenderer.java:41` `CHEST`; `:45` `BARRIER`; `:67` `PLAYER_HEAD`; `:77` `ARROW`;
  `:78` `LIME_DYE` / `REDSTONE`; `:81` `EMERALD`; `:82` `BARRIER`; `:83` `COMPASS`; `:85` `ARROW`;
  `:108` `NAME_TAG`; `:109` `PLAYER_HEAD`; `:111` `ARMOR_STAND`; `:113` `REDSTONE`; `:115`
  `NETHER_STAR`; `:117` `EXPERIENCE_BOTTLE`; `:120` `BLAZE_POWDER`; `:122` `HEART_OF_THE_SEA`;
  `:124` `ENDER_CHEST`; `:126` `CARTOGRAPHY_TABLE` / `GRAY_DYE`; `:130` `ARROW`; `:131`
  `EMERALD_BLOCK`; `:132` `BARRIER`; `:166` `LIME_DYE`; `:168` `LAVA_BUCKET`; `:170` `BARRIER`
- `StudioStatScreens.java:60` `RED_STAINED_GLASS_PANE`; `:64` `GRAY_DYE`; `:76` `ARROW`; `:78`
  `WRITABLE_BOOK`; `:81` `CLOCK`; `:83` `ARROW`; `:85` `COMPASS`; `:87` `ARROW`; `:128` `ARROW`;
  `:157-159` `IRON_INGOT` / `GOLD_INGOT` / `DIAMOND`
- `StatMaterialPalette.java:28-34` `IRON_SWORD, SHIELD, ENCHANTED_BOOK, GOLDEN_APPLE, FEATHER,
  GOLD_NUGGET, ARROW`; `:37` `PAPER` (fallback); `:40` `LIME_DYE` (selected); `:43`
  `RED_STAINED_GLASS_PANE` (degraded)
- `PaperHeadItems.java:29` `PLAYER_HEAD`

**Zero materials are operator-configurable.** Colours are equally fixed: the nine constants in
`GuiColors.java:17-38` are `static final`, no config path reads them.

### Hardcoded slot positions

Every one. Named constants exist only for the hub and two vault controls; everywhere else they are
inline magic numbers.

- Hub: `HubMenuRenderer.java:32-36` — `VAULT_SLOT=11, HATCH_SLOT=13, SLOTS_SLOT=15, HELP_SLOT=22,
  STUDIO_SLOT=26` (private static final)
- Vault: rows 0..n by loop index `PlayerPetMenuRenderer.java:55-60`; controls 45, 48, 49, 50, 53
  inline (`:64, :72, :77, :79, :82`); `VaultMenuControls.java:28-30` `SORT_SLOT=46,
  FILTER_SLOT=47, STATE_SLOT=22`
- Hatch: 11, 13, 15, 18, 22 inline — `HatchMenuRenderer.java:40-62`
- Management: 10, 11, 12, 13, 14, 15, 16, 22, 27, 31, 35 inline — `PetManagementMenuRenderer.java:44-118`
- Release confirm: 11, 13, 15 inline — `PetManagementMenuRenderer.java:127-140`
- Slot select: `int[] positions = {11, 15}` `SlotPurchaseMenuRenderer.java:47`, plus 22 at `:61`
- Slot confirm: 11, 15 — `SlotPurchaseMenuRenderer.java:84-96`
- Studio TIERS: `int[] slots = {10,11,12,14,15}` `StudioInventoryRenderer.java:36`, plus 22 at `:45`
- Studio LIST: 0..44 by index, controls 45/46/48/49/50/53 — `:65-85`
- Studio EDITOR: 10-16, 19, 20, 44, 45, 49, 53 — `:95-132`
- Studio ARCHIVE: 11, 13, 15 — `:163-170`
- Studio STAT_PICKER: 0..44, 22 (state), 45/46/47/49/50/53 — `StudioStatScreens.java:42-88`
- Studio STAT_MODIFIER: `int[] slots = {11,13,15}` `:105`, 4 (header) `:122`, 22 (back) `:127`

### Inventory sizes (all hardcoded)

| Menu | Size | file:line |
|---|---|---|
| Hub | 27 | `HubMenuRenderer.java:52-53` |
| Vault | 54 | `PlayerPetMenuRenderer.java:48` |
| Hatch | 27 | `HatchMenuRenderer.java:34-35` |
| Management | 45 | `PetManagementMenuRenderer.java:121` |
| Release confirm | 27 | `PetManagementMenuRenderer.java:144` |
| Slot select | 27 | `SlotPurchaseMenuRenderer.java:42` |
| Slot confirm | 27 | `SlotPurchaseMenuRenderer.java:80` |
| Studio TIERS | 27 | `StudioInventoryRenderer.java:34` |
| Studio LIST | 54 | `StudioInventoryRenderer.java:52` |
| Studio EDITOR | 54 | `StudioInventoryRenderer.java:92` |
| Studio ARCHIVE | 27 | `StudioInventoryRenderer.java:161` |
| Studio STAT_PICKER | 54 | `StudioStatScreens.java:35` |
| Studio STAT_MODIFIER | 27 | `StudioStatScreens.java:102` |

`gui.vault.petsPerPage` is the sole size-adjacent config value, and it only changes pagination
arithmetic, not the 54-slot inventory (`GuiConfig.java:22-23`).

### Gap summary: what a "custom GUI" config surface must add

**Cheap — already text-driven, needs only new keys / a routing change:**
1. Player-menu titles, names, lore: already 100% `messages.yml`. **Nothing to add.** The operator
   complaint "no custom GUI" is factually wrong for text on hub/vault/hatch/management/release/slot.
2. Studio titles (6 literals) and Studio names + lore (~55 literals): route through `MessageKey` +
   `Messages.line` the way the player menus already do. Purely mechanical; needs placeholder tags
   for the values currently string-concatenated.
3. Studio button colours: currently `NamedTextColor` arguments at each call site; MiniMessage in
   `messages.yml` subsumes them for free once (2) lands.

**Expensive — no config plumbing exists at all:**
4. **Materials.** ~90 literal `Material.*` sites (list above), many *conditional* on state
   (`favorite ? NETHER_STAR : GRAY_DYE`, `ready ? LIME_DYE : active ? DRAGON_EGG : EGG`,
   `locked ? BARRIER : LAVA_BUCKET`, `overflow ? RED_STAINED_GLASS : ENDER_CHEST`,
   `available ? material(provider) : BARRIER`). A material config must key on
   *(menu, button, state)*, not just *(menu, slot)*. Precedent for parsing a material name from
   config exists (`config.yml:126, :129`), so the loader pattern is known — the cost is the
   state-keyed schema plus an unknown-material fallback policy per button.
5. **Slot positions.** Requires the renderers to stop being the layout authority. Real constraints
   any schema must enforce: vault rows occupy 0..`petsPerPage-1` and the bottom row is reserved
   (`GuiConfig.java:22-23`); Studio LIST/STAT_PICKER hard-assume 45 content slots
   (`StudioInventoryRenderer.java:60-65`, `StudioStatScreens.java:39-42`); the `actions` map and the
   `setItem` call must move in lockstep or Report 1's bug recurs per moved button.
6. **Sizes.** Coupled to (5) — changing a size invalidates every slot index in that menu and the
   pagination maths. Management is 45, everything else 27 or 54.
7. **Filler.** Single site (`GuiItems.java:72`) but shared by all 13 screens; cheapest of the
   expensive group.
8. **A structural safeguard.** `PetManagementMenuRenderer.put(...)` (`:168-178`) binds action+item
   atomically; the Studio and vault renderers do it in two separate statements. Any config-driven
   layout should adopthe atomic form, otherwise every operator-moved button is a new
   drawn-but-unbound bug.

---

## 3. Egg item format (REPORT 3)

### Where the egg ItemStack is built

`omnipet-paper/.../incubation/EggAdminController.java:333-347` (`item(EggDefinition)`), which calls
`PaperEggItemCodec.create(...)` (`PaperEggItemCodec.java:144-150`) which delegates the metadata write
to `sign(...)` (`:159-171`). Called from the `/pet admin egg give` path
(`EggAdminController.java:260`).

### Operator-controllable today

Four `MessageKey`s, all in `messages.yml` (`MessageKey.java:186-190`):

| Part | MessageKey | Placeholders | Built at |
|---|---|---|---|
| Display name | `GUI_EGG_ITEM_NAME` (`gui.egg.item-name`, default `<gold><pet> Egg</gold>`) | `<pet>` | `EggAdminController.java:344-345` |
| Lore line 1 | `GUI_EGG_ITEM_TIER` (`gui.egg.item-tier`) | `<status>` | `:335-336` |
| Lore line 2 | `GUI_EGG_ITEM_DURATION` (`gui.egg.item-duration`) | `<detail>` | `:337-338` |
| Lore line 4 | `GUI_EGG_ITEM_HINT` (`gui.egg.item-hint`) | — | `:340` |

Colour and formatting come free via MiniMessage in those keys.

### Hardcoded / not settable at all

- **Material: `Material.TURTLE_EGG`** — `EggAdminController.java:342`. No config path. Note
  `PaperEggItemCodec.create` already takes `Material` as a parameter (`:144`) and only rejects
  `AIR` (`:146`), so the codec imposes no material policy — the literal is purely the caller's.
- **Lore line count and order** — fixed 4-line list built at `EggAdminController.java:334-340`; the
  blank spacer at `:339` (`Component.empty()`) is a literal. Operator cannot add, remove, or reorder
  lines, only reword the four.
- **Amount: always 1** — `PaperEggItemCodec.java:149` (`new ItemStack(material, 1)`), deliberate:
  each egg carries a unique nonce so a stack of 2 would share an identity (comment `:147-148`).
  Also enforced on the delivery side, one egg per free inventory slot
  (`EggAdminController.java:252-261`).
- **Custom model data: NOT SETTABLE.** No `setCustomModelData` / `CustomModelData` anywhere in the
  module (grep-verified). `sign(...)` touches only `displayName`, `lore`, and the PDC
  (`PaperEggItemCodec.java:161-169`).
- **Item flags: NOT SETTABLE.** No `ItemFlag` / `addItemFlags` anywhere.
- **Enchant glint: NOT SETTABLE.** No `addEnchant` / `addUnsafeEnchantment` /
  `setEnchantmentGlintOverride` anywhere.
- Per-egg-definition overrides also impossible from the catalog side: `EggDefinition`
  (`omnipet-core/.../domain/incubation/EggDefinition.java:12-17`) carries `id, tier,
  baseActiveMillis, candidates, extensions` — **no presentation fields**. `extensions` is a
  free-form validated map, so it is the natural carrier if per-egg appearance is wanted.

### Escrow-safety constraints — what must NOT be touched

The identity contract is three PDC keys written by `sign` (`PaperEggItemCodec.java:165-168`) and
validated on the way back in:

- `omnipet:egg` — the egg catalog ID (`STRING`), with legacy fallback `passivepet:egg` (`:38`,
  read at `:61` and `:87`)
- `omnipet:item_nonce` — per-item UUID (`STRING`), `:166-167`
- `omnipet:item_schema` — `ITEM_SCHEMA = 1` (`INTEGER`), `:24`, `:168`

`observe()` requires egg ID + parseable nonce + `schema == 1` or it marks the stack
`identityValid = false` and the fingerprint `"invalid"` (`:86-99`, `:186-189`). `capture()` throws on
an unsupported schema (`:59`, `:180-184`) and re-stamps all three keys (`:64-66`).

**The load-bearing derived value is the fingerprint.** `capture` serializes the amount-normalized
stack and SHA-256s it (`:69`, `:191-195`, `PaperEggItemSnapshot.fingerprint:43-50`), storing the
digest plus a Base64 copy of the whole item in `EggItemIdentity` (`:70-78`). Two later checks compare
against it:
- `EggInventoryEscrowService.java:71-72` — `identityMismatch` when the observed material key or
  fingerprint differs from the escrowed identity
- `PaperEggItemCodec.restore:113-124` — refuses a snapshot whose recomputed fingerprint, material
  key, or nonce disagrees

**Therefore:**

- **SAFE to make configurable** (all are captured *before* the fingerprint is computed, so they are
  simply included in it): display name, lore lines/order/count, custom model data, item flags,
  enchantments/glint, and **the material** — for *newly minted* eggs. Material is recorded per-item
  as `stack.getType().getKey().asString()` at capture (`:73`) and compared against that same
  recorded value later (`EggInventoryEscrowService.java:71`); it is never compared against a global
  constant.
- **MUST NOT be touched:** the three PDC keys, their types, `ITEM_SCHEMA = 1`, the amount-1
  invariant (`:149`, `:193`), and the 8 KiB snapshot cap
  (`PaperEggItemSnapshot.MAX_ITEM_BYTES = 8*1024`, `:12`, enforced `:52-59`). A heavy custom-model /
  long-lore egg is far from 8 KiB, but a config that allowed unbounded lore could push
  `serializeAsBytes()` over it and make `capture` throw.
- **Breaks escrow if changed after minting:** altering the material, name, lore, or any meta of an
  egg **already in flight** (escrowed) invalidates its fingerprint — `EggInventoryEscrowService`
  will report `identityMismatch` and `restore` will refuse. So any new appearance config must be
  read at mint time only, and a config edit must be understood as affecting future eggs only.
  Existing eggs in player inventories that are *not* escrowed are unaffected: `observe` re-derives
  the fingerprint on each read (`:96`) and only the PDC identity must survive.
- **Do not precompute a fingerprint at mint time.** `create`'s javadoc already states this
  (`:136-138`): the fingerprint has exactly one source of truth (capture time), and a
  configuration-driven builder must not add a second.

---

## Unresolved questions

1. Desired semantics of the Studio "Close" fix — literal `player.closeInventory()` (which triggers
   `PetStudioListener.onClose` -> `controller.onClose` -> `sessions.close(USER_CLOSE)`,
   `PetStudioController.java:146-149`), or an explicit session close then close? Both end at the
   same session state; the first is one line.
2. Is a "custom GUI" expected to cover the Studio (admin) at all, or only the six player menus? The
   codebase deliberately keeps admin text in Java (`MessageKey.java:12-15`) — moving Studio text to
   `messages.yml` reverses a stated decision and needs the user's call.
3. Should egg appearance be one global config block or per-egg-definition (via
   `EggDefinition.extensions`)? Different schema and different reload semantics.
