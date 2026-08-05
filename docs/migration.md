# Migration from PassivePet

> The [wiki](https://salyys1.github.io/OmniPet/wiki.html#/migration) is the reference for this topic. Where this file disagrees with it, the wiki is correct.

> **Warning:** Stop the server and make a complete, restorable backup before changing the plugin JAR or data-folder name. Do not run PassivePet and OmniPet against the same data at the same time. Preserve every pet, egg, component, expression, command, and user-defined ID.

The current checkpoint provides read/migration contracts in `omnipet-core`, schema 4 incubation/catalog/escrow core services, Admin Pet Studio, the player vault/active-intent slice, and transaction-backed active-slot purchases. It does not promise a complete gameplay cutover or a silent folder merge. Rehearse on a staging copy and keep the original source untouched until verification is complete.

## Rebrand and folder handling

OmniPet's descriptor and package are new (`OmniPet`, `io.github.salyvn.omnipet`). A first boot creates `plugins/OmniPet/`; it does not automatically merge a non-empty `plugins/PassivePet/` folder. Copy only after a per-file backup and conflict decision.

Keep separate archives of:

- `plugins/PassivePet/` (source backup);
- `plugins/OmniPet/` (generated target and any failed attempt);
- representative old items and exported permissions;
- exact Paper, Java, and optional-plugin versions.

## Player state migration contract

The schema 1 reader accepts the legacy top-level shape:

```text
uuid
pets: [{ type, components, ... }]
currentPetIndex
currentEgg: { type, timeLeft }
capacity
```

It emits a schema 4 envelope and:

- assigns each legacy pet a deterministic UUID from player UUID, list position, and raw pet content;
- preserves `definitionId` (`type` fallback), definition revision, raw component nodes, and unknown pet fields;
- converts non-negative `capacity` once to the persisted `vaultCapacity` floor;
- converts the old `currentPetIndex` once to an ordered `desiredActivePetIds` UUID list;
- starts `activeSlotCount` at the implicit base slot and leaves `slotEntitlements` empty;
- keeps `currentEgg` as raw legacy incubation data;
- preserves any arbitrary schema 1-3 top-level `incubation` node under legacy extension data instead of decoding it as the typed schema 4 contract;
- normalizes an out-of-range `currentPetIndex` to an empty active-intent list and records a warning;
- treats the old `capacity: -1` cache-invalid sentinel as `vaultCapacity: 0` with a warning;
- writes the migrated file atomically, retaining the previous file as `<uuid>.yml.bak`.

The canonical v4 writer removes `capacity` and `currentPetIndex` after conversion. A second read is idempotent and does not create another backup. `vaultCapacity` may be below the number of owned pets after permissions/configuration change; those pets remain read-only overflow and are never deleted. Live `petstorage.slot.N` permission grants are reconciled by the Paper storage service, not by the Bukkit-free codec.

Raw legacy `currentEgg` or preserved pre-v4 incubation data blocks a new core incubation start until an explicit migration resolves it. This is fail-closed protection against silently losing or duplicating an in-progress legacy egg.

Never treat a missing/invalid player file as an empty profile. Invalid input is quarantined and the repository throws. If the main file is missing while a matching quarantine file exists, reads and mutations fail closed until an operator explicitly restores or recovers the data.

## Pet definition migration contract

Legacy pet YAML with `general.texture` is read by the migration-only reader. It becomes a schema 2 definition with a mandatory texture head icon, tier `D`, and `HEAD` display provider. The complete legacy map is retained under raw extension data so unknown components are not discarded. The converted file replaces the original atomically and the prior file remains in `.bak`.

Current schema 2 definitions retain their raw nodes on decode/encode. IDs come from filenames and are case-folded/path-checked before read, save, archive, or reference scans.

## Egg definition journal

When `plugins/OmniPet/eggs.yml` exists, the Paper bootstrap validates stable egg IDs and pet references before repository initialization. It writes a canonical, sorted journal to:

```text
plugins/OmniPet/migration/legacy-eggs-v1.yml
```

The journal includes `schemaVersion`, `kind: legacy-egg-definitions`, a SHA-256 semantic hash of the source, and canonical raw definitions. Repeating the migration with the same content is idempotent. The source `eggs.yml` is never overwritten by the journal, and source/journal identity is rejected.

The canonical new catalog is separate: one schema 1 file per egg under `plugins/OmniPet/eggs/*.yml`, with filename equal to `eggId`, tier, base duration, and weighted pet candidates. Root `eggs.yml` remains migration input only; the Paper bootstrap does not convert it automatically, while the hatch flow consumes only the canonical per-file catalog.

The item-escrow core uses the incubation UUID as its transaction UUID and preserves item hand, inventory slot, nonce, SHA-256 fingerprint, and expected amount across `PREPARED`, `ITEM_REMOVED`, `COMMITTED`, cancellation, refund, and failure states. Its recovery directives require explicit inventory observation and send ambiguous failures to operator review. Paper requests recovery after start failures, pending online ticks, and joins. Each player scan prioritizes actionable non-terminal stages and stops at a bounded limit; a bound warning means older pending records need manual operator review, not that recovery completed. Crash-injection and live certification remain release gates.

## Purchase journal schema 1

The current purchase journal schema is 2. Older schema 1 rows need conservative entitlement verification because historical completion did not prove the current external entitlement contract.

- Schema 1 `COMPLETED`, `ENTITLEMENT_PERSISTED`, and `ENTITLEMENT_SYNC_PENDING` decode as `ENTITLEMENT_SYNC_PENDING` with `externalEntitlementRequired: true`.
- Withdrawal/refund evidence and financial state are preserved. Migration never replays a Vault or PlayerPoints call.
- The row appears in `/pet admin transactions [limit] [cursor]` after upgrade; continuation cursors are opaque and must be copied from command output.
- Verify the matching local slot entitlement and configured external node, then run `/pet admin reconcile <transaction-uuid> sync`.
- Sync refuses completion if the transaction's local OmniPet entitlement is missing; repair/reconcile that data instead of forcing terminal state.
- Successful sync/save writes schema 2 atomically and preserves the schema 1 journal as `<transaction-uuid>.yml.bak`.
- Journal reads are capped at 16 KiB. Corrupt, oversized, or overlong entries are reported without hiding valid pending transactions; at most 20 issue details are printed before omission/truncation summaries.

Schema 1 `FAILED` rows with a proven withdrawal and proven refund failure enter `REFUND_PENDING`; they still require explicit ledger-backed operator recovery.

## IDs, items, and permissions

Keep pet filenames, egg map keys, item IDs, component IDs, and expression references byte-for-byte stable. The migration boundary recognizes legacy item namespace `passivepet` for `pet`, `egg`, `food`, `hatcher`, and `evolver` keys. The current JAR consumes supported egg PDC for exact-hand hatch start but does not provide item conversion/distribution commands, reducer/instant-hatch items, or hard MMOItems integration. Future consumables require a durable redemption/escrow contract; copied action tokens or state events alone cannot prove settlement.

The command entry point checks `omnipet.general`, while Studio branches additionally check their declared admin permission. Move permission grants to `omnipet.*` names; the rewritten runtime does not provide the old runtime's legacy permission fallback.

## Staging checklist

1. Stop the server and archive both plugin folders.
2. Copy legacy files into a clean target layout without merging conflicting player folders.
3. Start the foundation on staging and inspect every migration warning/error.
4. Verify schema 4 player output, schema 2 pet definitions, `.bak` files, raw unknown/legacy incubation nodes, journal hash, and quarantine contents.
5. Verify migrated `config.yml`, vault/active limits, `/pet`, `/pet slot`, and `/pet admin transactions` only after successful initialization.
6. Test Vault/PlayerPoints-disabled fallbacks and LuckPerms entitlement policy without claiming live provider certification.
7. Resolve schema 1 purchase rows by transaction UUID; prove no economy replay and confirm the schema 2 rewrite/backup.
8. Stage-test Paper egg/PDC migration, online incubation timing, recovery, hatch GUI, and claims before production; keep renderers, skills, movement, and progression migration deferred until those runtime phases ship.

## Rollback

1. Stop the server if any data is missing, quarantined, or unexpectedly rewritten.
2. Archive the failed `plugins/OmniPet/` folder; do not overwrite the known-good backup.
3. Restore the original PassivePet JAR and folder together.
4. Restore the previous Paper/Java/vendor versions if they changed in the same window.
5. Reapply the exported permission state.
6. Reproduce the issue on staging before another attempt.

Never copy newer player files back into the old plugin without proving schema compatibility.
