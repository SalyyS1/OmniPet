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

The current release ships definition persistence, migration journaling, the `/pet` command, and the Admin Pet Studio. The egg, hatching, slot, render, trigger, economy, and player-storage sections below are future design troubleshooting notes; they are not live gameplay contracts until their roadmap phases land.

## Configuration fails to load

- Use spaces, not tabs.
- Quote MiniMessage strings when YAML punctuation makes them ambiguous.
- Confirm material IDs exist on the exact Paper version.
- Confirm every egg pet ID exactly matches a filename under `pets/`.
- Use a positive duration such as `10s`, `30m`, or `2h15m`.
- Remove `mythiclibBuffs` or `mythiclib.*` expressions from configs that must run without MythicLib.
- Keep ModelEngine fields out of `display`; the current schema accepts only `texture`.

Test one pet file at a time. Preserve the failed file and log; do not replace player data with an empty profile to make an error disappear.

## Player has zero storage slots

The default permission sequence is:

```text
petstorage.slot.1
petstorage.slot.2
petstorage.slot.3
...
```

Slots are consecutive. A player with slot 3 but without slot 1 still has no usable first slot. Confirm `slotPermission` was not changed during migration.

## `/pets` works but an admin subcommand does not

Admin branches require the specific `omnipet.admin.*` node and the root `omnipet.general` check. Verify both when a permissions plugin explicitly denies inherited/default nodes.

Console use must include a target when the syntax otherwise defaults to the executing player.

## Egg item does nothing

Check:

- the player has an empty permitted slot;
- the player is not already hatching an egg;
- the egg ID still exists in `eggs.yml`;
- the item was created by OmniPet or a registered MMOItems stat;
- legacy items carry one of the `passivepet:*` keys OmniPet reads;
- MMOItems string stat matches the egg ID exactly.

Do not identify eggs from lore or display names.

## Hatching bar is invalid or stuck

- Reject zero/blank durations.
- Confirm a hatcher did not reduce the value into an unexpected state.
- Test quit/rejoin and server restart behavior on staging.
- Record whether progress is expected to continue offline; legacy state stores remaining time and may not match a future completion-timestamp design.

## Pet does not render

- Confirm the pet has a `display.texture` URL using the Minecraft texture host.
- Confirm the player summoned the pet from `/pets`.
- Check for entity cleanup after world changes, death, recall, or reload.
- ModelEngine models are not supported by the current runtime; use the Paper display renderer.

## Trigger does not run

- Confirm the active pet has a `trigger` component and the trigger ID is correct.
- Cooldown values are positive ticks; `100` is approximately five seconds, and `interval` triggers require one.
- Confirm the precondition evaluates true and referenced namespaces exist.
- For `walk`, test ordinary same-world movement, not teleport or vehicle movement.
- For MythicLib casts, confirm the exact skill ID exists and the hook initialized at startup.
- Keep scripts short; an expression error occurs on the main server thread.

## MythicLib stats are missing

- Confirm MythicLib loaded before OmniPet and OmniPet logged hook initialization.
- Confirm the stat ID and modifier type exist in the pinned MythicLib build.
- Summon and recall once while watching logs.
- Verify modifiers are removed on recall, logout, reload, and disable.
- Test without MMOItems to distinguish MythicLib core from MMOItems adapter issues.

## MMOItems item is not recognized

Check the registered stat ID and data type:

- number: `OMNIPET_PET_FOOD`, `OMNIPET_EGG_HATCHER`;
- boolean: `OMNIPET_PET_EVOLVER`;
- string egg ID: `OMNIPET_EGG`.

Legacy `PASSIVEPET_*` IDs are also read. Rebuild/reload the MMOItems template after stat registration and verify the item carries live stat data, not only matching lore.

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
