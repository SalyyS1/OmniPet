# Troubleshooting

Start with the exact runtime facts: OmniPet version/commit, Paper build, Java version, optional plugin versions, and whether the problem reproduces without custom pet files.

## Plugin does not start

Check:

1. The server is Paper, not Spigot or CraftBukkit.
2. Paper 1.21.x runs on Java 21; Paper 26.1+ runs on Java 25.
3. The JAR is named `OmniPet-<version>.jar` and contains `paper-plugin.yml`.
4. The descriptor entry point resolves to `io.github.salyvn.omnipet.paper.OmniPetPlugin`; there is no bootstrapper entry in the current Gradle artifact.
5. No old PassivePet JAR is loading beside OmniPet.
6. The first exception in the log is captured; later errors may only be symptoms.

An `UnsupportedClassVersionError` usually means the Java runtime is older than the compiled target. A `NoClassDefFoundError` mentioning a vendor plugin usually means an optional adapter loaded against a missing or incompatible API.

## Current runtime boundary

The current release ships definition persistence, migration journaling, `/pet [page]`, the player vault/active-intent slice, slot purchase/reconciliation logic, Admin Pet Studio, Paper egg items/PDC, online incubation timing, conservative recovery, hatch commands/GUI, and escrow-gated claims. Renderers, triggers, and live pet runtime remain future work.

## Configuration fails to load

- Use spaces, not tabs.
- For current storage config, require the complete `storage.vault` and `storage.activeSlots` tree shown in [Configuration](configuration.md). Unknown or partial current-schema keys fail closed.
- A legacy file with only `globalMaxSlots` and/or `slotPermission` is migrated automatically. Inspect `config.yml.bak` after first successful boot.
- PlayerPoints prices must be whole integers. Vault prices permit at most eight decimal places and must fit the provider-safe `double` range.
- Quote slot keys such as `"2"`; unquoted numeric YAML keys are rejected by the strict map decoder.
- Permission templates must contain exactly one `%s` and produce safe nodes no longer than 128 characters. The LuckPerms template is checked for every slot through `storage.activeSlots.max`.
- Definition `display.provider` accepts `HEAD` or `MODELENGINE`; ModelEngine metadata is stored but no live ModelEngine renderer ships yet.
- Canonical egg definitions are one schema 1 file per ID under `eggs/*.yml`, with matching filename/`eggId`, a valid compact/compound/ISO `baseDuration`, and weighted candidates. The Paper bootstrap loads this catalog at startup; restart OmniPet after editing because `/pet admin reload` does not refresh eggs yet.

Test one pet file at a time. Preserve the failed file and log; do not replace player data with an empty profile to make an error disappear.

## Player has zero storage slots

The default permission sequence is:

```text
petstorage.slot.1
petstorage.slot.2
petstorage.slot.3
...
```

Slots are consecutive. A player with slot 3 but without slot 1 contributes no legacy capacity. Confirm `storage.vault.legacyPermission.template` was not changed during migration and `baseCapacity` is not intentionally zero.

## `/pets` works but an admin subcommand does not

Admin branches require the specific `omnipet.admin.*` node and the root `omnipet.general` check. Verify both when a permissions plugin explicitly denies inherited/default nodes.

Console use must include a target when the syntax otherwise defaults to the executing player.

## Slot purchase is unavailable or ambiguous

- Run `/pet slot` and read the disabled provider reason. Vault needs both the Vault plugin and a registered economy service; PlayerPoints needs its current UUID/int API.
- If LuckPerms/hybrid mode is configured, confirm consecutive `omnipet.slot.unlocked.N` nodes match the persisted OmniPet count.
- A timeout or exception after an external call is intentionally not retried. Run `/pet admin transactions 20`, verify the provider ledger, then use the matching `charge`, `no-charge`, or `refund` decision.
- If the economy decision is settled but the row is `ENTITLEMENT_SYNC_PENDING`, verify the local entitlement and configured node, then run `/pet admin reconcile <transaction-uuid> sync`; this retries only idempotent entitlement verification/grant.
- Schema 1 rows previously marked `COMPLETED` or `ENTITLEMENT_PERSISTED` intentionally appear in this queue. Sync does not replay the economy call; successful persistence writes schema 2 and keeps `.bak`.
- If one provider disappears after a plugin disable, inspect only its dependency: Vault plus the registered economy service owner, PlayerPoints, or LuckPerms. The affected adapter is unavailable until the coalesced next-tick refresh; unrelated healthy providers remain usable.
- If a next cursor is printed, copy the opaque token unchanged into `/pet admin transactions 20 <cursor>`. The limit range is 1-50.
- Unreadable transaction files are listed separately. Reads stop at 16 KiB per file; output includes at most 20 issue details plus omission/truncation summaries. Preserve entries and their `.bak` files; do not delete the whole `data/purchases` folder.
- The adapters are not live-certified yet. Reproduce on staging with exact provider versions before treating a failure as an OmniPet data problem.

## A deferred gameplay feature does nothing

Egg/item distribution, reducer/instant-hatch item gameplay, a separately versioned public hatch API, summoned/rendered companions, movement, triggers, runtime MythicLib buffs, MythicMobs execution, hard MMOItems integration, and progression are not shipped. The in-repo core hatch listener reports persisted state only; it is not payment settlement or a public vendor event bus. The current hatch command/GUI does not activate those later runtime systems. Definition metadata or legacy files may be preserved without an owning runtime; check [Roadmap](roadmap.md) instead.

## Hatch is locked or not advancing

- Claim and countdown intentionally remain locked until the matching egg escrow row is `COMMITTED`; a persisted `STARTED` incubation or core event is not commit proof.
- Online time begins when commit is observed. Failed persisted ticks retain their elapsed time and retry later rather than silently losing the interval.
- Start failures, pending tick observations, and joins request conservative recovery. Keep the player online when exact inventory observation/removal/refund is required.
- Recovery scans are bounded. If the log says the bound was reached, inspect older pending files in `data/egg-escrow/` manually; do not delete or bulk-edit them.
- Malformed PDC, duplicate nonces, fingerprint/material mismatches, or ambiguous stack amounts require operator review. Preserve the player file, escrow row, item, and first relevant log message.

## Migration loaded an empty profile

Stop the server before another save. Preserve the failed OmniPet folder, restore the known-good backup, and inspect the first decode error. Common causes are missing pet/egg IDs, renamed component IDs, a stale `currentPetIndex`, or mixing two different data-folder revisions.

Never keep playing on a server that replaced a failed decode with blank data; later saves can overwrite the recoverable file.

## Useful issue report

Include:

```text
OmniPet version/commit:
Paper exact build:
Java version:
MythicLib version or absent:
MMOItems version or absent:
Clean starter config reproduces: yes/no
Steps to reproduce:
First relevant exception:
```

Attach only minimal redacted YAML. Remove UUIDs, IP addresses, tokens, database credentials, and private model assets.
