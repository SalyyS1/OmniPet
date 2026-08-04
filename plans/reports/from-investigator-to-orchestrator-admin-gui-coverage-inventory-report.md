# OmniPet — command → GUI coverage inventory

Read-only investigation. No code modified. All paths relative to
`D:\Project\.1_PROJECT_SL_PLUGINS\OmniBundle\OmniPet\`.

Shorthand:
- `CMD` = `omnipet-paper/src/main/java/io/github/salyvn/omnipet/paper/command/OmniPetCommand.java`
- `TREE` = `.../command/OmniPetCommandTree.java`

---

## 1. Command → GUI coverage table

Tree source: `TREE:23-194`. Dispatcher: `CMD:352-550`.

| Command | GUI today? | Dispatch site | Handler |
|---|---|---|---|
| `/pet` (no args) | **YES** — hub inventory | `CMD:536-544` | `.../player/PlayerHubController.java:77` (`open`), holder `.../gui/hub/HubInventoryHolder.java:18` |
| `/pet <page>` | **YES** — vault inventory | `CMD:545-546` | `.../player/PlayerPetController.java:73` / `:77` |
| `/pet vault [page]` | **YES** — vault inventory | `CMD:545-546` via `AdminPetCommandParser.java:43` | `PlayerPetController.java:77` |
| `/pet slot` | **YES** — slot purchase + confirm screens | `CMD:470-492` (`slotPurchases.open` at `:491`) | `.../player/PlayerSlotPurchaseController.java:72`, renderer `.../gui/player/SlotPurchaseMenuRenderer.java:69` |
| `/pet hatch` | **YES** — incubation menu | `CMD:361-376` | `.../player/PlayerHatchController.java:94` → `:70` |
| `/pet hatch main` | **YES** — equivalent button exists | `CMD:374` | `PlayerHatchController.java:100`; button `.../gui/hatch/HatchMenuRenderer.java:53` |
| `/pet hatch off` | **YES** — equivalent button | `CMD:374` | `PlayerHatchController.java:101`; button `HatchMenuRenderer.java:56` |
| `/pet hatch claim` | **YES** — equivalent button | `CMD:374` | `PlayerHatchController.java:102`; button `HatchMenuRenderer.java:62` |
| `/pet hatch refresh` | **YES** — equivalent button | `CMD:374` | `PlayerHatchController.java:103`; button `HatchMenuRenderer.java:66` |
| `/pet hatch use-main` | **NO GUI BUTTON** (menu exists, action absent) | `CMD:374` | `PlayerHatchController.java:104` → `redeemItem` `:110`. `HatchInventoryHolder.Type` (`.../gui/hatch/HatchInventoryHolder.java:60`) has only `START_MAIN, START_OFF_HAND, CLAIM, REFRESH, HUB` — no redeem action |
| `/pet hatch use-off` | **NO GUI BUTTON** | `CMD:374` | `PlayerHatchController.java:105` → `:110`; same holder gap `HatchInventoryHolder.java:60` |
| `/pet skill <pet-uuid> <binding-id>` | **NO** — chat only | `CMD:377-395` | `.../skill/PaperActiveSkillController.java:69` (`cast`) |
| `/pet help [page]` | **NO** — chat only | `CMD:357-360` | `.../command/CommandHelpRenderer.java:23`, paging in `.../command/CommandHelp.java:53` |
| `/pet admin browse` | **YES** — Pet Studio | `CMD:533-535` | `.../studio/bukkit/PetStudioController.java:117` |
| `/pet admin reload` | **NO** — chat only | `CMD:517-527` | inline `reloadRuntime.getAsBoolean()` at `CMD:523` |
| `/pet admin item reducer` | **NO** — chat only | `CMD:397-412` | `.../incubation/action/IncubationActionItemController.java:101` |
| `/pet admin item instant` | **NO** — chat only | `CMD:397-412` | `IncubationActionItemController.java:101` |
| `/pet admin item candy` | **NO** — chat only | `CMD:402-407` | `.../management/PetCultivationItemController.java:24` |
| `/pet admin item breakthrough` | **NO** — chat only | `CMD:402-407` | `PetCultivationItemController.java:24` |
| `/pet admin egg give` | **NO** — chat only | `CMD:415-424` | `.../incubation/EggAdminController.java:149` → `give` `:261` |
| `/pet admin egg create` | **NO** — chat only | `CMD:415-424` | `EggAdminController.java:149` → `create` `:287` |
| `/pet admin pet give` | **NO** — chat only | `CMD:426-435` | `EggAdminController.java:166` (`petCommand`) |
| `/pet admin hatch inspect` | **NO** — chat only | `CMD:494-498` → `dispatchHatchAdmin` `CMD:552`, `:559-569` | `.../incubation/HatchAdminController.java:49` |
| `/pet admin hatch reduce` | **NO** — chat only | `CMD:579-580` | `HatchAdminController.java:54` |
| `/pet admin hatch set` | **NO** — chat only | `CMD:581-582` | `HatchAdminController.java:65` |
| `/pet admin hatch complete` | **NO** — chat only | `CMD:583-584` | `HatchAdminController.java:76` |
| `/pet admin hatch cancel` | **NO** — chat only | `CMD:585-586` | `HatchAdminController.java:82` |
| `/pet admin skill pending` | **NO** — chat only | `CMD:437-446` | `PaperActiveSkillController.java:85` → `inspectPending` `:215` |
| `/pet admin skill rollback` | **NO** — chat only | `CMD:437-446` | `PaperActiveSkillController.java:93` → `rollbackPending` `:247` |
| `/pet admin cultivation pending` | **NO** — chat only | `CMD:448-457` | `.../management/PaperCultivationAdminCommandTarget.java:22` → `list` `:50` |
| `/pet admin cultivation review` | **NO** — chat only | `CMD:448-457` | `PaperCultivationAdminCommandTarget.java:50` (same `list`, `operatorReview` branch `:66-68`) |
| `/pet admin cultivation recover` | **NO** — chat only | `CMD:448-457` | `PaperCultivationAdminCommandTarget.java:80` |
| `/pet admin release list` | **NO** — chat only | `CMD:459-468` | `.../release/PaperReleaseAdminCommandTarget.java:29` → `.../release/ReleaseAdminController.java:63` |
| `/pet admin release recover` | **NO** — chat only | `CMD:459-468` | `ReleaseAdminController.java:89` |
| `/pet admin release reconcile` | **NO** — chat only | `CMD:459-468` | `ReleaseAdminController.java:103` |
| `/pet admin transactions [limit] [cursor]` | **NO** — chat only | `CMD:507-508` | `.../economy/SlotTransactionAdminController.java:60` |
| `/pet admin reconcile <uuid> <decision>` | **NO** — chat only | `CMD:509-510` | `SlotTransactionAdminController.java:104` |

Hub tiles today: `VAULT, HATCH, SLOTS, HELP, STUDIO` (`.../gui/hub/HubInventoryHolder.java:54-60`). There is **no admin tile other than Studio**, so nothing under `/pet admin` except `browse` is reachable by clicking.

---

## 2. Cheap / moderate / hard classification

### CHEAP — acts on a bounded already-loaded list, no new data source

| Command | Why cheap |
|---|---|
| `/pet help [page]` | `CommandHelp.lines` (`CommandHelp.java:28`) already builds the whole permission-filtered line list in memory and `CommandHelp.page` (`:53`) already pages it at 8/page (`:23`). A GUI is a re-render of data already computed; zero new input. |
| `/pet hatch use-main` / `use-off` | Input is only "which hand". `PlayerHatchController.redeemItem` `:110` takes an `EggInventoryHand`. The hatch menu is already open, already has a `MenuLayout`-driven action map (`HatchMenuRenderer.java:53-69`), and `HatchInventoryHolder.Action` factories (`:49-57`) are one-line additions. |
| `/pet admin reload` | No input at all (`CMD:523`, a `BooleanSupplier`). One button + result message. |
| `/pet admin cultivation pending` / `review` | Handler needs `(playerId, limit)` only (`PaperCultivationAdminCommandTarget.java:57`, capped 1..100 `:62`); the result list is already fully materialised in memory (`:69-77`) and `PaperCultivationRecoveryController.pending/operatorReview` `:62`/`:66` return a bounded `List`. Paging in a GUI is over a list the code already holds. |
| `/pet admin skill pending` | Handler input is one player (`PaperActiveSkillController.java:89`). Rows are already collected and hard-capped at 100 in memory (`:219-224`). GUI = draw those rows. |
| `/pet admin release list` | Bounded, mailbox-owned, limit 1..50 (`ReleaseAdminCommandParser.java:48`); `mailbox.list(limit)` (`ReleaseAdminController.java:64`) already returns the full list. |

### MODERATE — needs a repository scan/paging step, or a money/item confirm

| Command | Why moderate |
|---|---|
| `/pet admin transactions` | The list itself is fine, but paging is cursor-based over a journal scan: `SlotPurchaseReconciliationService.pending(limit, cursor)` (`omnipet-core/.../economy/SlotPurchaseReconciliationService.java:43`) and `PurchaseJournalScanResult.nextCursor()` (`.../economy/PurchaseJournalScanResult.java:11`). A GUI must **carry the opaque cursor in holder state** across pages rather than accept it typed. Also async off-thread (`SlotTransactionAdminController.java:66`, `:175`). Doable, not free. |
| `/pet admin reconcile <uuid> <decision>` | Once a transaction is selected from the paged list the UUID stops being free text — it comes from the row. But the decision is a **money decision** with four irreversible outcomes (`SlotTransactionAdminCommandParser.java:37-40`: `CHARGE_CONFIRMED / NO_CHARGE_CONFIRMED / REFUND_CONFIRMED / ENTITLEMENT_SYNC_RETRY`), so it needs a confirm screen exactly like the slot purchase confirm (`SlotPurchaseMenuRenderer.java:69`). |
| `/pet admin release recover` | Inputs after row selection: `(playerId, transactionId, INTERNAL\|EXTERNAL)` — all three come from the listed row plus a 2-way channel pick (`ReleaseAdminCommandParser.java:52-58`). Moderate only because it re-delivers rewards; needs confirm. |
| `/pet admin release reconcile` | Same shape, but the 4-way decision (`ReleaseAdminController.java:104-124`: `EXTERNAL_DELIVERED`, `EXTERNAL_NOT_DELIVERED`, `MAILBOX_PENDING`, `INVENTORY_DELIVERED`) declares reward delivery truth. Confirm step mandatory. |
| `/pet admin cultivation recover` | UUID comes from the pending row, and the handler already **requires the player online** (`PaperCultivationAdminCommandTarget.java:92-95`), so the GUI operator can reasonably be looking at them. Moderate rather than cheap because it mutates progression and consumes an item transaction (`PaperCultivationRecoveryController.java:70`). |
| `/pet admin skill rollback` | Inputs `(player, petId, actionId)` (`PaperActiveSkillController.java:93-99`) all appear in the `pending` rows (`:221-223`), so a click-through from the pending list eliminates the typing. Moderate because it rolls back a durable reservation. |
| `/pet admin item candy` / `breakthrough` | Inputs: online player + amount 1..36 (`PetCultivationItemController.java:37`, `:42`). Player is pickable from the online roster; amount needs either a stepper or an amount-preset row — a real second screen, not one button. |
| `/pet admin item instant` | Same: online player + amount bounded by `item.getMaxStackSize()` (`IncubationActionItemController.java:143`). |
| `/pet admin egg give` | Inputs: online player, `eggId` from the catalog, amount ≤ 36 (`EggAdminController.java:266-272`). `eggId` is **not free text** — it is a repository key (`eggs.read(eggId)` `:268`), so it can be a paged picker. Moderate because that means paging `EggDefinitionRepository`, plus the ≤36 empty-slot check (`:276-280`) needs to be surfaced in the GUI. |
| `/pet admin pet give` | Inputs: online player + `definitionId` from the live registry (`EggAdminController.java:179-180`, `requireDefinition` `:427`). The registry is already browsable in Pet Studio (`StudioInventoryRenderer.java:49` list screen with paging `:61-86`), so the picker exists; the grant itself is async with retry (`:188-211`). Sits at moderate because it is a permanent grant that warrants a confirm. |
| `/pet admin hatch inspect` | Only input is a player (`HatchAdminCommandParser.java:55-59`, accepts online name). Output is a one-line snapshot (`HatchAdminController.java:142-151`). Would be cheap except it is deliberately **console-safe and offline-capable** (see HARD note below) — a GUI would cover only the online subset. |

### HARD — free text, offline players, or genuinely bulk/scripted

| Command | Why hard |
|---|---|
| `/pet admin egg create <egg-id> <definition-id> [duration]` | `egg-id` is an operator-invented **new** identifier (no list to pick from — `EggAdminController.java:292`) and `duration` is a free-text duration string parsed by `IncubationDurationParser.parseMillis` (`:294`, default `"1h"` `:56`). Two free-text fields; only the chat-input service can supply them. |
| `/pet admin item reducer <player> <seconds> [amount]` | `seconds` is arbitrary and multiplied to millis with overflow checking (`IncubationActionItemController.java:123`, `Math.multiplyExact`). Arbitrary number → free text. |
| `/pet admin hatch reduce` / `set` | Require `<incubation-uuid>` **and** `<millis>` (`HatchAdminCommandParser.java:61-77`). The incubation UUID is not enumerated by any list command; `inspect` prints exactly one current incubation (`HatchAdminController.java:148`), and millis is an arbitrary long (`:98-105`). Also player-arg accepts a raw UUID for offline players (`PlayerArgumentResolver` wired at `CMD:37-43`, and the parser resolves name-then-UUID). |
| `/pet admin hatch complete` / `cancel` | Same offline-UUID posture. `HatchAdminController` explicitly exists to serialise **offline** hatch administration (class doc `HatchAdminController.java:19`), and the whole flow runs on the per-player task queue (`:124`) with no online requirement. A GUI cannot target an offline player from a roster. |
| `/pet skill <pet-uuid> <binding-id>` (player-facing) | Needs a pet UUID plus a binding ID string (`CMD:386-394`). *However*: both are enumerable from the player's own vault + definition skill list (`SkillBindingProjection.read` at `PaperActiveSkillController.java:119`), so this is HARD **as written** but is the one chat-only player command with a realistic path to a picker off the vault. Flagging it as the highest-value non-admin GUI candidate. |

---

## 3. Reusable GUI infrastructure

### Holder pattern (per-viewer, snapshotted immutable action map, revision/token guard)
| Piece | file:line |
|---|---|
| Hub holder — simplest template, `Map<Integer, Action>` + `expectedRevision` | `.../gui/hub/HubInventoryHolder.java:18`, `bind` `:31`, `action(rawSlot)` `:44` |
| Vault holder | `.../gui/player/PlayerPetInventoryHolder.java` |
| Slot-purchase holder — **carries extra per-view state** (`slot`, `origin`, `transactionId`, `Stage`) — the model for carrying a paging cursor | `.../gui/player/SlotPurchaseInventoryHolder.java:13`, ctor `:23`, `Stage` enum `:74` |
| Pet-management holder — validates that a confirm view has a frozen preview | `.../gui/player/PetManagementInventoryHolder.java:31`, `View` enum `:78`, `Type` `:87` |
| Studio holder — token-bound (session generation), the strongest guard | `.../studio/bukkit/StudioInventoryHolder.java:14`, viewer/token equality check `:30`, `Screen` enum `:15` |

### Listener pattern (cancel-everything, verify viewer, bound-check raw slot, restrict click types, cancel drags, release on close)
| Piece | file:line |
|---|---|
| Canonical short listener — copy this | `.../gui/hub/HubMenuListener.java:27-51` (viewer check `:31`, slot bound-check `:32`, LEFT/RIGHT only `:33`, drag guard `:39`, close→release `:46`) |
| Vault listener | `.../gui/player/PlayerPetMenuListener.java` |
| Management listener + separate inventory guard | `.../gui/player/PetManagementMenuListener.java`, `.../gui/player/PetManagementInventoryGuard.java` |
| Studio listener — adds quit/kick cleanup and the **async chat hook** | `.../studio/bukkit/PetStudioListener.java:31` (click), `:48` (drag), `:60` (close), `:68`/`:71` (quit/kick), `:74` (`AsyncChatEvent` → `captureChat`) |
| Click/drag decision policy extracted for unit test | `.../studio/view/StudioInventoryActionPolicy.java`, used at `PetStudioListener.java:36` |

### Slot layout / collision-safe button binding
| Piece | file:line |
|---|---|
| `MenuLayout<A>` — operator-configurable slots, refuses collisions, binds action+item together | `.../gui/MenuLayout.java:37`, `put` `:71`, `bind` `:96`, `putStack` `:106`, `size` `:51`, `filler` `:57` |
| Shared item builders | `.../gui/GuiItems.java:46` (`of`), `:71` (`filler`), `:30` (`label`) |
| Material resolution from config | `.../gui/MenuMaterials.java`, e.g. used `.../gui/player/VaultMenuControls.java:45` |

### Paging helper
There is **no single generic paging class**. Three independent implementations to copy from:
| Piece | file:line |
|---|---|
| **Best reusable one — pure, no Bukkit, unit-testable**: snapshot + view state → one page | `.../gui/player/VaultPetView.java:29` (`of`), `firstPage/lastPage` `:60`/`:64`, page clamp `:39` |
| Vault view state (page/sort/filter, immutable `withPage`) | `.../gui/player/VaultViewState.java`; prev/next wiring `.../player/PlayerPetController.java:122-123` |
| Vault control row incl. disabled-last-page arrow | `.../gui/player/VaultMenuControls.java:49`, `paintLastPage` `:80` |
| Studio list paging (45/page, inline in renderer) | `.../studio/bukkit/StudioInventoryRenderer.java:61-86`; PREV/NEXT click handling `.../studio/bukkit/PetStudioController.java:162-171` |
| Chat paging (for `/pet help`, already generic + tested) | `.../command/CommandHelp.java:53`, `LINES_PER_PAGE` `:23` |

Recommendation: an admin GUI should follow `VaultPetView` — a pure record that takes the already-loaded list + a view state and returns the page — rather than the inline Studio arithmetic.

### Confirm-step pattern
| Piece | file:line |
|---|---|
| **Release confirmation** — separate `View`, frozen preview stored in the holder so the decision cannot drift | renderer `.../gui/player/PetManagementMenuRenderer.java:131` (cancel `:134`, confirm `:138`, preview item `:142`); holder invariant `.../gui/player/PetManagementInventoryHolder.java:31`; dispatch `.../management/PetManagementMenuController.java:129`; service `.../management/PaperPetManagementController.java:94` → `.../management/PetManagementReleaseActions.java:74` |
| **Slot purchase confirmation** — two-stage `SELECT_PROVIDER` → `CONFIRM`, re-validates price at confirm time | renderer `.../gui/player/SlotPurchaseMenuRenderer.java:69` (confirm button `:86`); stage enum `.../gui/player/SlotPurchaseInventoryHolder.java:74`; controller `.../player/PlayerSlotPurchaseController.java:154` (`select` `:184`, `confirm` `:193`, **price-changed re-check** `:196-200`) |
| **Studio archive confirm** — dedicated screen + a typed-ID hard-delete gate | `.../studio/bukkit/StudioInventoryRenderer.java:159`; `Screen.ARCHIVE_CONFIRM` `.../studio/bukkit/StudioInventoryHolder.java:15`; dispatch `.../studio/bukkit/PetStudioController.java:203-205`, staleness guard `:437`, `:454` |

The slot-purchase price re-check at `PlayerSlotPurchaseController.java:196-200` is the pattern any money-decision admin confirm must copy: re-read authoritative state at confirm time, refuse if it moved.

### Chat-input service for free text (what Pet Studio uses)
| Piece | file:line |
|---|---|
| Service — captures async chat, defers all state transitions to main thread, timeout + `cancel` keyword + stale-token rejection | `.../studio/input/ChatInputService.java:18`; `await` `:48`; `captureAsync` `:71`; `cancel` `:78`; `cancelSession` `:87`; `expire` `:109`; `cancel` keyword handled `:137`; parse failure → `INVALID_INPUT` without dropping the prompt `:147-159` |
| Pending record | `.../studio/input/PendingChatInput.java` |
| Failure reasons (`REPLACED, CANCELED, SESSION_CLOSED, EXPIRED, STALE_SESSION, INVALID_INPUT`) | `.../studio/input/ChatInputFailure.java` |
| Parser SAM | `.../studio/input/ChatInputParser.java`; concrete parsers `.../studio/input/StudioInputParsers.java`, `.../studio/bukkit/StudioDraftInputParsers.java` |
| Main-thread dispatcher seam | `.../studio/input/StudioMainThreadDispatcher.java` |
| **Await-and-resume wiring to copy** — prompt, close inventory, capture, re-render on accept, re-prompt on invalid | `.../studio/bukkit/PetStudioController.java:547` (`awaitField`), timeout scheduling `:562`, failure handling `inputFailure` `:566` |
| **Prompt-text contract** — every await path must send what/format/example/cancel; enum guarantees it | `.../studio/bukkit/StudioFieldPrompt.java:20`, `send` `:81`, `paths()` `:107` (a contract test asserts no silent await path) |
| Chat hook + capture gate | `.../studio/bukkit/PetStudioListener.java:74`; `capturesChat` `.../studio/bukkit/PetStudioController.java:138`, `captureChat` `:140` |
| Session/token machinery the input service validates against | `.../studio/session/PetStudioSessionManager.java`, `.../studio/session/StudioViewToken.java`, `.../studio/session/StudioThreadGuard.java` |

This service is **not Studio-specific in its types** — it is keyed on `UUID playerId` + `StudioViewToken`, and the token validator is injected (`ChatInputService.java:31`, wired at `PetStudioController.java:109`). An admin GUI would need either its own session manager producing compatible tokens, or a small generalisation of `StudioViewToken`. That is the single largest reuse obstacle.

---

## 4. Commands where a GUI would be actively WORSE

1. **`/pet admin hatch reduce|set|complete|cancel`** — these are explicitly the *offline* administration path. `HatchAdminController` class doc says so (`.../incubation/HatchAdminController.java:19`), the parser accepts a bare player UUID (`.../command/HatchAdminCommandParser.java:57`, `:67`, `:85`), and work is queued on the per-player task queue with no online check (`:124`). A GUI can only offer the online roster (`CMD:37-43` deliberately refuses offline lookups because it would be a blocking profile fetch on the main thread, `CMD:29-36`). Wrapping these in a GUI would silently drop the exact case they exist for. Additionally `<millis>` is an arbitrary long — a GUI stepper is strictly worse than typing it.

2. **`/pet admin transactions [limit] [cursor]`** — the cursor is an **opaque versioned token** with a validated structural format (`.../command/SlotTransactionAdminCommandParser.java:51-59`, `v1.<prefix>.<hash>`) and is printed back to the operator for verbatim re-paste (`.../economy/SlotTransactionAdminController.java:96-99`). A GUI can hold the cursor in holder state (that's the MODERATE path above), but the *command form* must stay, because it is the only way an operator can resume a scan from a cursor captured in a log or a ticket. Do not remove it.

3. **`/pet admin egg create`** — an authoring command run once per definition, typically while scripting a content batch alongside `/pet admin egg give`. Both `egg-id` and `duration` are free text (`.../incubation/EggAdminController.java:292-294`), and the command already prints the exact follow-up command to run (`:297-299`). This is a scripting/console workflow; a GUI would add clicks without removing typing.

4. **`/pet admin reload`** — console-first by design; it is the one command an operator runs from a remote console after editing YAML. It is not player-only in the tree (`TREE:73-74` has no `.playerOnly()`). Adding a hub button is harmless but the command must remain console-reachable.

5. **`/pet admin item reducer <player> <seconds> [amount]`** — bulk distribution with an arbitrary seconds value (`.../incubation/action/IncubationActionItemController.java:123`). Handing out N items to M players is a loop in a script; a GUI makes that strictly slower.

6. **`/pet admin cultivation pending|review <player> [limit]`** and **`/pet admin skill pending <player>`** — worth GUI-ifying (they are CHEAP), but the command form must stay: both are diagnostic output an operator pastes into a bug report. `PaperActiveSkillController` even warns when output is capped at 100 rows and tells the operator to inspect the data file directly (`.../skill/PaperActiveSkillController.java:233-236`) — an explicitly non-GUI escape hatch.

**Net recommendation:** GUI-ify additively, never as a replacement. The highest value-per-effort items are, in order: (a) `use-main`/`use-off` buttons in the existing hatch menu — a hole in an already-built GUI; (b) an admin hub tile leading to a pending-work list GUI covering `cultivation pending/review`, `skill pending`, `release list`, `transactions`, each row click-through to a confirm screen; (c) a `/pet skill` picker off the vault. Everything with an arbitrary number, a new identifier, or an offline target should stay a command.

---

## Unresolved questions

1. `ChatInputService` is keyed on `StudioViewToken` (`.../studio/session/StudioViewToken.java`) and validated via `sessions::isCurrent` (`.../studio/bukkit/PetStudioController.java:109`). Should a new admin GUI (a) reuse `PetStudioSessionManager` directly, (b) get its own session manager producing `StudioViewToken`s, or (c) does the token type get generalised out of the `studio` package? This decides whether free-text admin fields can reuse the chat-input service at all.
2. No generic paging helper exists — three implementations (`VaultPetView.java:29`, `StudioInventoryRenderer.java:61`, `CommandHelp.java:53`). Extract a shared one, or copy the `VaultPetView` shape a fourth time? YAGNI argues for copying once more and extracting only on the fifth.
3. `/pet admin transactions` cursor paging in a GUI needs the cursor in holder state. Is forward-only paging acceptable (no "previous page"), given the journal scan API exposes only `nextCursor` (`omnipet-core/.../economy/PurchaseJournalScanResult.java:11`)?
4. Is an admin hub tile wanted at all, or should admin GUIs be reached only by command (`/pet admin ...`)? The hub currently gates its Studio tile on `omnipet.admin.managepet` — adding more admin tiles means per-tile permission filtering in `HubMenuRenderer`.
