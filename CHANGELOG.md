# Changelog

## Unreleased - OmniPet 3.0 development line

### Changed

- Rebranded the public project, descriptor, examples, and operator documentation as OmniPet by SalyVn.
- Switched the documented build workflow from Maven to Gradle Kotlin DSL.
- Added Paper 1.21.x/Java 21 and Paper 26.1+/Java 25 compatibility guidance with an explicit alpha/experimental caveat for 26.x.
- Added a static GitHub Pages workflow that publishes `docs/` without repository secrets.
- Added migration guidance for legacy `passivepet` permissions, PDC keys, MMOItems stat IDs, data folders, and stable YAML fields.
- Added dual-read documentation for legacy item identifiers and optional MythicLib/MMOItems integrations.

### Compatibility notes

- Paper 1.21.x is the primary release line.
- Paper 26.1.1 artifacts observed during research were alpha builds; no blanket `26.1.1+` support claim is made.
- Paper 26.1+ requires Java 25. MythicLib, MMOItems, MythicMobs, and ModelEngine vendor support must be verified separately.
- ModelEngine rendering remains a roadmap/adapter boundary; the current built-in renderer uses Paper display entities.

### Migration notes

- Keep `globalMaxSlots`, `slotPermission`, `duration`, `rarity`, `pets`, and component IDs unchanged when migrating existing YAML.
- Existing `passivepet:*` item PDC keys and `PASSIVEPET_*` MMOItems stat IDs are treated as legacy compatibility identifiers. New items use OmniPet identifiers.
- Back up `plugins/PassivePet/` and `plugins/OmniPet/` before switching descriptors or copying data. Resolve conflicts manually; never merge two live player-data directories blindly.

## Previous releases

The historical PassivePet release notes were not present in this repository snapshot. Add versioned entries here as releases are cut; do not invent prior behavior or support claims.

