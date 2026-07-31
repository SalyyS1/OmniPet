# Commands and permissions

The authoritative runtime registers the Gradle-built Paper command, player vault, and Admin Pet Studio. Hatching and live pet gameplay remain phased work.

## Command entry point

| Command | Alias | Permission | Current behavior |
| --- | --- | --- | --- |
| `/pet [page]` | `/pets [page]` | `omnipet.general` | Opens the paginated player vault and toggles persisted desired-active intent. |
| `/pet slot` | `/pets slot` | `omnipet.general` | Opens explicit Vault/PlayerPoints choices for the next configured active slot. |
| `/pet admin browse` | `/pets admin browse` | `omnipet.general` + `omnipet.admin.managepet` | Opens the D/C/B/A/S definition browser and editor. |
| `/pet admin reload` | `/pets admin reload` | `omnipet.admin.reload` | Stages config/definitions, swaps the live generation, then queues online-player limit reconciliation. |
| `/pet admin transactions [limit] [cursor]` | `/pets ...` | `omnipet.admin.reconcile` | Lists a bounded page of pending/ambiguous slot transactions and unreadable journal entries. |
| `/pet admin reconcile <transaction-uuid> <charge\|no-charge\|refund\|sync>` | `/pets ...` | `omnipet.admin.reconcile` | Applies explicit operator evidence without replaying ambiguous economy calls; `sync` retries only idempotent entitlement verification/grant. |

Studio Save, archive, clone-only authoring, and exact-ID hard delete mutate only definition files through the shared transaction boundary. Hard delete is blocked while YAML, egg, player, or active Studio references exist. Player vault activation changes only ordered UUID intent; no renderer entity is spawned until Phase 5. Slot purchase work runs off-thread, while Vault/PlayerPoints calls are bridged to Paper's main thread and journaled around every external outcome. A required LuckPerms grant is also journaled and must finish before the purchase becomes `COMPLETED`.

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
| `omnipet.admin.inspect` | `false` | Reserved; no inspect command is registered yet. |
| `omnipet.admin.explore` | `false` | Reserved; no explore command is registered yet. |
| `omnipet.admin.managepet` | `false` | Allows Pet Studio browse, create, edit, and archive. |
| `omnipet.admin.manageegg` | `false` | Reserved; no egg-management command is registered yet. |
| `omnipet.admin.item` | `false` | Reserved; no item command is registered yet. |
| `omnipet.admin.reconcile` | `false` | Allows listing and explicitly reconciling ambiguous slot transactions. |

Every Studio action checks both the viewer permission and the current session/view token. A stale inventory, reload, close, timeout, or superseded session cannot save a draft.

## Migration note

Before cutover, export old permission groups and add the `omnipet.*` names deliberately. Do not assume the rewritten command accepts legacy `passivepet.*` grants. Preserve consecutive `petstorage.slot.N` grants while migrating: the current vault resolver can consume them live according to `config.yml`.
