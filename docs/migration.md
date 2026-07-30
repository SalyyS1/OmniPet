# Migration from PassivePet

Rebranding changes the plugin descriptor, data-folder name, Java package, permissions, and item namespace. A safe migration preserves user IDs and configuration field names, takes backups, and rehearses rollback on a staging server.

## Before you start

1. Stop the server completely.
2. Back up the server and these folders separately:
   - `plugins/PassivePet/`
   - `plugins/OmniPet/` if it already exists
3. Record the old and new JAR names, exact Paper build, Java version, MythicLib version, and MMOItems version.
4. Export permission groups and save representative legacy items.
5. Do not run two plugins that own the same pet data at the same time.

## Data-folder move

Paper derives the default folder from the descriptor name. OmniPet uses `plugins/OmniPet/`; legacy releases used `plugins/PassivePet/`.

For a first migration into an empty OmniPet folder:

1. Start OmniPet once on a staging copy to see the generated layout, then stop it.
2. Keep the generated OmniPet folder as a separate backup.
3. Copy the legacy configuration and player data into the empty OmniPet layout:
   - `config.yml`
   - `eggs.yml`
   - `gui.yml`
   - `items.yml`
   - `lang.yml`
   - `pets/`
   - `data/players/`
4. Do not merge two non-empty `data/players/` directories without a per-player conflict policy.
5. Start the staging server and inspect every load warning before production rollout.

This repository snapshot does not promise a silent data-folder merge. Manual, backed-up migration is safer than guessing which live folder wins.

## Contracts to preserve

### Player data

Legacy player files use:

```text
data/players/<uuid>.yml
  uuid
  pets
  currentPetIndex
  currentEgg
  capacity
```

Nested egg state uses `type` and `timeLeft`. Pet state uses `type` and `components`. Do not hand-edit or rename these fields during a brand migration.

### Component and state IDs

Preserve `general`, `display`, `hatching`, `leveling`, `stamina`, `trigger`, and optional `mythiclibBuffs`. Stateful keys include:

- `hatching.rarity`, `hatching.seed`;
- `leveling.exp`, `leveling.level`, `leveling.evolution`;
- `stamina.value`, `stamina.init`.

### User-defined IDs

Pet IDs come from filenames. Egg, food, and hatcher IDs come from YAML map keys. These IDs can appear in saves, items, commands, expressions, MMOItems templates, and other plugins. Keep them byte-for-byte stable.

### Global configuration

Keep `globalMaxSlots` and `slotPermission`. The default `petstorage.slot.%s` permission is a public server contract; changing it without duplicating grants locks storage slots.

## Items and PDC

New standalone items use:

```text
omnipet:food
omnipet:evolver
omnipet:egg
omnipet:hatcher
```

OmniPet also reads the legacy `passivepet:*` variants so existing items remain identifiable. Legacy reads do not justify deleting backups or mass-editing item NBT. Test all four item kinds before production deployment.

## MMOItems

New templates should use:

```text
OMNIPET_PET_FOOD
OMNIPET_PET_EVOLVER
OMNIPET_EGG
OMNIPET_EGG_HATCHER
```

The adapter also registers/reads the legacy `PASSIVEPET_*` IDs. Migrate templates gradually and keep representative old items in the test matrix.

## Permissions and commands

- Add `omnipet.general` and the matching `omnipet.admin.*` nodes to groups and automation.
- OmniPet accepts matching `passivepet.*` command grants during the migration window; migrate groups to `omnipet.*` before removing the legacy plugin.
- Preserve `/pets` and `/pet`; both remain stable aliases.
- Preserve `petstorage.slot.%s` unless every sequential slot grant is migrated.

## Java API

Compiled addons using `io.github.nahkd123.comm.passivepet` do not automatically link to `io.github.salyvn.omnipet`. Recompile addons against the OmniPet API or provide a separately maintained legacy facade. Do not assume a YAML migration solves a binary API break.

## Staging verification

- All pet and egg definitions load with no missing IDs.
- Representative players retain pet count, active selection, egg, level, evolution, and stamina.
- Legacy and new standalone items work.
- Legacy and new MMOItems stats work if MMOItems is enabled.
- Slot capacity matches pre-migration permissions.
- `/pet`, `/pets`, admin commands, GUI, summon/recall, hatch, quit/rejoin, reload, and disable pass.
- Logs contain no decode error, blank-profile replacement, linkage error, or optional-hook failure.

## Rollback

1. Stop the server immediately if player data or item recognition is wrong.
2. Archive the failed `plugins/OmniPet/` folder for diagnosis; do not overwrite the backup with it.
3. Restore the original PassivePet JAR and `plugins/PassivePet/` backup together.
4. Restore the previous Paper/Java/vendor versions if they changed in the same maintenance window.
5. Reapply permission exports if group changes were part of the migration.
6. Reproduce the failure on staging before another attempt.

Never copy newer player files back into the old plugin without proving the schema is backward-compatible.
