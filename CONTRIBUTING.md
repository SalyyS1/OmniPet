# Contributing to OmniPet

Thanks for helping improve OmniPet. The project is a Java/Paper plugin maintained by SalyVn; changes should be small, testable, and compatible with the documented server matrix.

## Before opening a change

1. Read `README.md` and the relevant page in `docs/`.
2. Search existing code and configuration for the contract you want to change.
3. Preserve stable YAML keys, component IDs, item IDs, command aliases, and permission nodes unless the change includes a migration note.
4. Do not commit server data, player UUID files, credentials, private model assets, or generated build output.

## Development setup

- Java 21 is required for the primary Paper 1.21.x line.
- Java 25 is required for a Paper 26.1+ validation lane.
- Use the Gradle wrapper from `OmniPet/`; do not add Maven-only instructions.
- Keep optional integrations behind runtime plugin checks. A clean server without MythicLib or MMOItems must still boot.

Useful checks:

```bash
gradlew.bat clean test jar
```

For documentation-only changes, validate YAML syntax, links, and UTF-8/ASCII-safe example files before opening a pull request.

## Code and configuration guidelines

- Prefer the existing component, codec, and adapter boundaries over new global state.
- Route vault and slot-purchase player work through the plugin-owned shared `PerPlayerTaskQueue`. Coalescible reads must use namespaced keys (`vault:view` or `slot:view`); mutations and purchases must never use `submitLatest`, and controllers must not create local per-player queues.
- Keep Bukkit entity, inventory, permission, and provider work on the server thread; keep repository work async.
- Keep persistence and migration changes atomic and recoverable.
- Document user-visible behavior, compatibility claims, and migration requirements.
- Add focused tests for parser, codec, command, migration, and optional-hook changes.
- Keep comments useful: explain an invariant or compatibility decision, not an obvious assignment.

## Pull requests

Use a concise title such as `fix: reject invalid egg duration` or `docs: publish compatibility matrix`. The description should state:

- what changed and why;
- files/config contracts affected;
- tests or validation commands run;
- compatibility and migration impact;
- any unresolved question or follow-up.

Do not include local absolute paths, screenshots containing private server data, or copied vendor code. A maintainer will decide release notes and versioning.
