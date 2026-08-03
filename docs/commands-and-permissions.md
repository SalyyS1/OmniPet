# Commands and permissions

The Gradle-built Paper runtime registers the player hub, the player vault, Admin Pet Studio, the durable hatch view, live pet rendering, right-click pet interaction, per-pet management and cultivation, active skills, and release reward administration. Riding is not implemented.

## Command entry point

| Command | Alias | Permission | Current behavior |
| --- | --- | --- | --- |
| `/pet` | `/pets` | `omnipet.general` | Opens the player hub: Vault, Hatch, Active slots, Help, and (for staff) Studio tiles. |
| `/pet vault [page]` | `/pets vault [page]` | `omnipet.general` | Opens the paginated player vault. Left-click toggles persisted desired-active intent; right-click opens per-pet management. Slot 46 cycles the sort order (favorites-first, level, rarity, name, recent) and slot 47 cycles the filter (all, favorites, active, stored); both reset to page 1 and to their defaults when the vault is reopened from a command. |
| `/pet <page>` | `/pets <page>` | `omnipet.general` | Opens the vault directly on the given page. |
| `/pet help [page]` | `/pets help [page]` | `omnipet.general` | Lists the commands the sender may use, paged. Tab-complete on `/pet ` is permission-filtered. |
| `/pet slot` | `/pets slot` | `omnipet.general` | Opens explicit Vault/PlayerPoints choices for the next configured active slot. |
| `/pet hatch [main\|off\|claim\|refresh\|use-main\|use-off]` | `/pets hatch ...` | `omnipet.general` | Opens the hatch GUI, captures an exact main/off-hand egg, claims a committed READY hatch, refreshes the durable view, or redeems a reducer/instant item from the named hand. |
| `/pet skill <pet-uuid> <binding-id>` | `/pets skill ...` | `omnipet.general` | Casts an ACTIVE skill binding on an owned, currently active pet. |
| `/pet admin browse` | `/pets admin browse` | `omnipet.general` + `omnipet.admin.managepet` | Opens the D/C/B/A/S definition browser and editor. |
| `/pet admin reload` | `/pets admin reload` | `omnipet.admin.reload` | Stages config/definitions, swaps the live generation, then queues online-player limit reconciliation. |
| `/pet admin item <...>` | `/pets ...` | `omnipet.admin.item` (plus `omnipet.admin.cultivation` for candy/breakthrough) | Distributes reducer, instant-hatch, EXP candy, and breakthrough items. |
| `/pet admin hatch inspect <player-uuid>` | `/pets ...` | `omnipet.admin.inspect` | Reads persisted incubation for an offline or online player. |
| `/pet admin hatch reduce\|set <player-uuid> <incubation-uuid> <millis> <action-uuid>` | `/pets ...` | `omnipet.admin.manageegg` | Adjusts remaining active time under the player revision lock; the action UUID makes retries idempotent. |
| `/pet admin hatch complete\|cancel <player-uuid> <incubation-uuid> <action-uuid>` | `/pets ...` | `omnipet.admin.manageegg` | Completes or cancels a persisted incubation. |
| `/pet admin skill pending\|rollback <...>` | `/pets ...` | `omnipet.admin.skill` | Lists durable skill reservations or rolls one back after a failed cast. |
| `/pet admin cultivation <...>` | `/pets ...` | `omnipet.admin.cultivation` | Recovers interrupted cultivation item transactions. |
| `/pet admin release <...>` | `/pets ...` | `omnipet.admin.release` | Lists bounded release mailbox rows and reconciles an exact transaction. |
| `/pet admin transactions [limit] [cursor]` | `/pets ...` | `omnipet.admin.reconcile` | Lists a bounded page of pending/ambiguous slot transactions and unreadable journal entries. |
| `/pet admin reconcile <transaction-uuid> <charge\|no-charge\|refund\|sync>` | `/pets ...` | `omnipet.admin.reconcile` | Applies explicit operator evidence without replaying ambiguous economy calls; `sync` retries only idempotent entitlement verification/grant. |

Studio Save, archive, clone-only authoring, and exact-ID hard delete mutate only definition files through the shared transaction boundary, and each accepted save writes one durable audit record. Hard delete is blocked while YAML, egg, player, or active Studio references exist. Slot purchase work runs off-thread, while Vault/PlayerPoints calls are bridged to Paper's main thread and journaled around every external outcome. A required LuckPerms grant is also journaled and must finish before the purchase becomes `COMPLETED`.

## Hatch flow

The 27-slot hatch GUI has four durable presentations: no active incubation offers exact main-hand/off-hand starts in slots 11/15; `INCUBATING` shows the resolved snapshot and countdown in slot 13; `READY` makes slot 13 claimable; terminal history permits another start. Slot 22 always refreshes persisted state.

Starting an egg persists `PREPARED` escrow and the resolved incubation before exact-hand removal, then advances through `ITEM_REMOVED` to `COMMITTED`. Countdown and claim stay locked until `COMMITTED`; a visible `STARTED` state or core `HatchEvent` is not proof of payment settlement. Online time begins from commit observation. The coordinator persists every 100 ticks, and that cadence is the maximum active-time rollback: a tick that cannot persist pauses accrual rather than accumulating unpersisted time in memory. Start failures, ticks that encounter pending escrow, and joins request conservative bounded recovery; older records beyond the scan bound require operator review.

Reducer and instant-hatch items redeem through the same durable rule as egg starts. The journal records the exact hand, slot, nonce, fingerprint, and expected amount; the item is removed on the main thread and the hatch mutation applies once under the same transaction ID. Forged, copied, type-substituted, or tampered payloads fail closed.

## Pet management and release

Right-clicking a vault pet opens management: favorite, lock, reorder, EXP candy, breakthrough, and release. Right-clicking your own **rendered** pet in the world opens the same screen; another player's pet does nothing, and a non-OmniPet entity behaves normally. Every rendered inventory carries viewer, owner, pet UUID, session, inventory generation, and expected revision; drags touching the top inventory are cancelled and stale views cannot act.

Cultivation removes the exact item before applying progression under a journal receipt, so a restart cannot reapply experience and a rejected mutation refunds the exact item. Release freezes a reward preview, then removes the pet and appends the internal outbox entry in one player-state write. Internal rewards deliver through a durable mailbox that is idempotent across restart; ambiguous external Vault/PlayerPoints outcomes persist as `UNKNOWN_COMMIT` and require `/pet admin release` reconciliation rather than automatic retry.

## Transaction paging and bounds

- `limit` defaults to 20 and accepts values from 1 through 50.
- When more candidates remain, OmniPet prints a versioned next cursor and the complete continuation command. Treat the cursor as opaque: copy it verbatim and do not construct, shorten, or edit it.
- Every journal read is capped at 16 KiB. Oversized, invalid, or unreadable entries are reported instead of loaded without a bound.
- A page prints at most 20 unreadable issue rows. Additional issues are summarized by an omission count; a separate message says when invalid-name discovery itself was truncated.

```text
/pet admin transactions 20
/pet admin transactions 20 <next-cursor-from-output>
```

Schema 1 journal rows previously marked `COMPLETED`, `ENTITLEMENT_PERSISTED`, or `ENTITLEMENT_SYNC_PENDING` are listed as `ENTITLEMENT_SYNC_PENDING` after upgrade. Verify the local slot entitlement and configured external node, then run `reconcile <transaction-uuid> sync`. This path never repeats the withdrawal; a successful save writes schema 2 and retains the prior file as `.bak`.

Provider disable events invalidate only the matching capability: Vault or its registered economy service owner affects `VAULT`, PlayerPoints affects `PLAYER_POINTS`, and LuckPerms affects external entitlement sync. Unrelated providers stay usable; one coalesced refresh runs on the next tick.

## Descriptor defaults

These nodes are declared in `omnipet-paper/src/main/resources/paper-plugin.yml`:

| Node | Default | Current meaning |
| --- | --- | --- |
| `omnipet.*` | `op` | Grants `omnipet.general` and the declared admin tree. |
| `omnipet.general` | `true` | Allows `/pet` and `/pets`. |
| `omnipet.admin.*` | `false` | Wildcard for declared admin controls. |
| `omnipet.admin.reload` | `false` | Allows transactional definition reload. |
| `omnipet.admin.inspect` | `false` | Allows read-only offline hatch inspection. |
| `omnipet.admin.explore` | `false` | Reserved; no explore command is registered yet. |
| `omnipet.admin.managepet` | `false` | Allows Pet Studio browse, create, edit, and archive. |
| `omnipet.admin.manageegg` | `false` | Allows offline hatch reduce, set, complete, and cancel. |
| `omnipet.admin.item` | `false` | Allows distributing OmniPet items. |
| `omnipet.admin.cultivation` | `false` | Allows distributing candy/breakthrough items and recovering cultivation transactions. |
| `omnipet.admin.skill` | `false` | Allows inspecting and rolling back pending skill reservations. |
| `omnipet.admin.release` | `false` | Allows listing and reconciling release rewards. |
| `omnipet.admin.reconcile` | `false` | Allows listing and explicitly reconciling ambiguous slot transactions. |

Every Studio action checks both the viewer permission and the current session/view token. A stale inventory, reload, close, timeout, or superseded session cannot save a draft.

## Not implemented

- Riding. `RendererCapabilities.riding` exists and the built-in head renderer reports `false`; no mount service or control is registered.
- Entity click interaction. Activation populates a stable entity-to-owner index, but no interact listener routes through it, so passive event triggers and click-to-target actions are unavailable.
- A combined player hub. Vault, hatch, slot purchase, and management are separate entry points.
- MMOItems cultivation identities. Candy and breakthrough items are standalone materials from `config.yml`.
- Admin target mode for another player's management menu.

## Migration note

Before cutover, export old permission groups and add the `omnipet.*` names deliberately. Do not assume the rewritten command accepts legacy `passivepet.*` grants. Preserve consecutive `petstorage.slot.N` grants while migrating: the current vault resolver can consume them live according to `config.yml`.
