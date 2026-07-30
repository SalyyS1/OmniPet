# Migration from PassivePet

> **Warning:** Stop the server and make a complete, restorable backup before changing the plugin JAR or data-folder name. Do not run PassivePet and OmniPet against the same data at the same time. Preserve every pet, egg, component, expression, command, and user-defined ID.

Phase 1 provides read/migration contracts in `omnipet-core`; it does not promise a complete gameplay cutover or a silent folder merge. Rehearse on a staging copy and keep the original source untouched until verification is complete.

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

It emits a schema 2 envelope and:

- assigns each legacy pet a deterministic UUID from player UUID, list position, and raw pet content;
- preserves `definitionId` (`type` fallback), definition revision, raw component nodes, and unknown pet fields;
- keeps `currentEgg`, `capacity`, and `currentPetIndex` as legacy migration hints;
- normalizes an out-of-range `currentPetIndex` to `-1` and records a warning;
- writes the migrated file atomically, retaining the previous file as `<uuid>.yml.bak`.

Never treat a missing/invalid player file as an empty profile. Invalid input is quarantined and the repository throws. If the main file is missing while a matching quarantine file exists, reads and mutations fail closed until an operator explicitly restores or recovers the data.

## Pet definition migration contract

Legacy pet YAML with `general.texture` is read by the migration-only reader. It becomes a schema 2 definition with a mandatory texture head icon, tier `D`, and `HEAD` display provider. The complete legacy map is retained under raw extension data so unknown components are not discarded. The converted file replaces the original atomically and the prior file remains in `.bak`.

Current schema 2 definitions retain their raw nodes on decode/encode. IDs come from filenames and are case-folded/path-checked before read, save, archive, or reference scans.

## Egg definition journal

When `plugins/OmniPet/eggs.yml` exists, the Phase 1 Paper bootstrap validates stable egg IDs and pet references before repository initialization. It writes a canonical, sorted journal to:

```text
plugins/OmniPet/migration/legacy-eggs-v1.yml
```

The journal includes `schemaVersion`, `kind: legacy-egg-definitions`, a SHA-256 semantic hash of the source, and canonical raw definitions. Repeating the migration with the same content is idempotent. The source `eggs.yml` is never overwritten by the journal, and source/journal identity is rejected.

## IDs, items, and permissions

Keep pet filenames, egg map keys, item IDs, component IDs, and expression references byte-for-byte stable. The migration boundary recognizes legacy item namespace `passivepet` for `pet`, `egg`, `food`, `hatcher`, and `evolver` keys. New Phase 1 Paper code does not ship item gameplay or an item conversion command.

The command entry point checks `omnipet.general`, while Studio branches additionally check their declared admin permission. Move permission grants to `omnipet.*` names; the rewritten runtime does not provide the old runtime's legacy permission fallback.

## Staging checklist

1. Stop the server and archive both plugin folders.
2. Copy legacy files into a clean target layout without merging conflicting player folders.
3. Start the foundation on staging and inspect every migration warning/error.
4. Verify schema 2 output, `.bak` files, raw unknown nodes, journal hash, and quarantine contents.
5. Verify `/pet` and `/pets` only after the plugin reports foundation initialization.
6. Keep full gameplay migration steps deferred until the owning phases implement hatching, slots, GUI, and runtime services.

## Rollback

1. Stop the server if any data is missing, quarantined, or unexpectedly rewritten.
2. Archive the failed `plugins/OmniPet/` folder; do not overwrite the known-good backup.
3. Restore the original PassivePet JAR and folder together.
4. Restore the previous Paper/Java/vendor versions if they changed in the same window.
5. Reapply the exported permission state.
6. Reproduce the issue on staging before another attempt.

Never copy newer player files back into the old plugin without proving schema compatibility.
