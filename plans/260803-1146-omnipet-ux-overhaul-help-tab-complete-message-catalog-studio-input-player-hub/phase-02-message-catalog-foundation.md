---
phase: 2
title: "Message catalog foundation"
status: complete
priority: P1
effort: "1d"
dependencies: [1]
---

# Phase 2: Message catalog foundation

<!-- Updated: Validation Session 1 - scope narrowed to catalog infra + player-facing chat; GUI text migration moved to Phase 5; admin/console output stays hardcoded -->

## Overview

Build the catalog and migrate **player-facing chat text** only. GUI titles and lore migrate in Phase 5, in the same pass that restyles the renderers, so no renderer is edited twice. Admin/console output stays hardcoded — it is an audit trail, not UI.

## Requirements

- Functional: keyed lookup returning `Component`; MiniMessage formatting; named placeholders; `List<Component>` for lore blocks (used from Phase 4 onward); hot-reload via `/pet admin reload`.
- Non-functional: unknown key in file → warn + ignore; missing key in file → built-in default; malformed MiniMessage → warn + literal text, never an exception into a click handler.
- Security: all dynamic values inserted as `Placeholder.unparsed` so player-controlled text cannot inject tags.
- Scope boundary (validated): text a **player** sees is catalog-owned. Text only an operator sees on console — transaction pages, opaque cursors, reconcile reasons, offline inspect dumps — stays in Java so an edited YAML file cannot distort an audit line.

## Architecture

New package `omnipet-paper/.../text/`:

- `MessageKey` — enum of every key, each carrying its built-in default MiniMessage string. The enum is the single source of truth: the shipped `messages.yml` resource is generated from it, so file and code cannot drift.
- `MessageCatalog` — loads YAML into `Map<MessageKey, String>`, exposes `Component line(MessageKey, TagResolver...)` and `List<Component> lore(MessageKey, TagResolver...)`. Holds `MiniMessage.miniMessage()`. Immutable after load; `reload()` builds a replacement instance and swaps the reference (same staged-then-swap discipline as `PetStudioController.reload`).
- `Messages` — thin static accessor bound once in `onEnable`, so the ~82 call sites do not each need a constructor parameter threaded through.

Why not extend `OmniPetConfigLoader`: that loader calls `rejectUnknown` and throws on any unexpected key (`config/OmniPetConfigLoader.java:143`). Message text must degrade, not fail closed — a typo in a lore line must not disable pets. Separate file, separate loader, deliberately lenient.

Lore keys hold a YAML string list; a single-line value is accepted and wrapped. Multi-line lore therefore needs no key-per-line explosion.

Placeholder convention: `<pet>`, `<player>`, `<amount>`, `<page>`, `<pages>`, `<status>`, `<reason>`, `<detail>`, `<provider>`, `<cost>`, `<balance>`, `<level>`, `<exp>`, `<remaining>`. Every value is passed `unparsed`.

## Related Code Files

- Create: `text/MessageKey.java`, `text/MessageCatalog.java`, `text/Messages.java`
- Create: `omnipet-paper/src/main/resources/messages.yml` (generated from `MessageKey` defaults)
- Create: `text/MessageCatalogTest.java`, `text/MessageKeyDefaultsTest.java`
- Modify: `OmniPetPlugin.java` — save/load `messages.yml` alongside `config.yml` in `onEnable` (mirror the symlink guard at `OmniPetPlugin.java:107-111`), bind `Messages`, add catalog reload inside `reloadRuntime`
- Modify (this phase — player-facing chat only): `player/PlayerPetController.java`, `player/PlayerHatchController.java`, `player/PlayerSlotPurchaseController.java`, `skill/PaperActiveSkillController.java`
- Modify (player-facing feedback lines only; leave console/audit output alone): `incubation/action/IncubationActionItemController.java` (the `message(Player, ...)` helper at `:127`, not the `sender.sendMessage` usage lines)
- Deliberately untouched: `release/ReleaseAdminController.java`, `economy/SlotTransactionAdminController.java`, `incubation/HatchAdminController.java`, `management/PaperCultivationAdminCommandTarget.java`, `management/PetCultivationItemController.java` — operator/audit output stays in Java
- Deferred to Phase 5: inventory titles and lore in the five renderers

## Implementation Steps

1. Enumerate `MessageKey` from the Phase 1 worklist, **player-facing entries only**. Group by prefix: `command.*`, `help.*`, `vault.*`, `hatch.*`, `slot.*`, `manage.*`, `studio.*`, `hub.*`, `error.*`. Set each default to **today's exact string**, adding only the MiniMessage color tag matching the current `NamedTextColor`. Skip `admin.*` — operator output is out of scope.
2. Implement `MessageCatalog`: read with `YamlDocuments.readMap` (reuse core's reader), flatten to dotted keys, resolve unknown keys to a warning, cache deserialized `Component` per (key, no-arg) to avoid re-parsing static lines every render tick.
3. Implement `Messages` with an explicit `bind(MessageCatalog)` / `catalog()` pair; `catalog()` throws a clear `IllegalStateException` if unbound so a missed bind fails loudly in tests, not silently at runtime.
4. Generate `messages.yml` from the enum defaults, with a header comment naming the MiniMessage docs URL and stating that removing a key restores its default.
5. Migrate the listed chat controllers key-by-key. Keep rendered output byte-identical; this is a refactor, not a rewording.
6. Wire `onEnable` (save if absent, symlink guard, load, bind) and `reloadRuntime` (stage a new catalog, swap only on success — a bad edit leaves the previous catalog live, matching how `reloadRuntime` already treats the registry at `OmniPetPlugin.java:291`).
7. Tests: default-vs-file precedence; unknown key warns without throwing; malformed MiniMessage yields literal text; `Placeholder.unparsed` renders `<red>evil` literally; every `MessageKey` has a non-blank default and a matching entry in the shipped resource.

## Success Criteria

- [x] Every `MessageKey` has a non-blank default; resource file and enum agree (asserted by test).
- [x] The five listed controllers contain no player-facing string literals.
- [x] Admin/console output is unchanged (no key added for it).
- [x] Rendered text is unchanged from the Phase 1 baseline, except two documented deviations.
- [x] Missing file regenerates; unknown key warns; malformed value degrades to literal; none disables the plugin.
- [x] A pet name containing `<red>` or `<click:...>` renders literally.
- [x] `/pet admin reload` picks up an edited `messages.yml`; a broken edit keeps the previous catalog and logs why.

Report: [`plans/reports/phase-02-message-catalog-foundation-report.md`](../reports/phase-02-message-catalog-foundation-report.md).
136 suites / 493 tests (+32). `messages.yml` is generated from the enum at runtime instead of shipped
as a resource, so file and code cannot drift. Two deliberate text deviations are recorded in the
report: a null claim-failure detail now falls back to the exception name, and `hatch.claim-result`
split into success/rejected keys because a catalog value cannot branch on colour.

`PetManagementMenuSupport.message` moved out of this phase into Phase 5 — it is the chat half of the
management GUI, so migrating it here would edit that feature twice.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| Missed literal leaves text unlocalizable | Worklist from Phase 1 is the checklist, with player/operator classification per line. A grep for `sendMessage(Component.text("` in the five migrated files must return nothing. |
| Per-render MiniMessage parsing costs CPU on the main thread | Cache parsed `Component` for argument-free keys; only placeholder-bearing keys re-parse. |
| Enum grows past 200 lines | It is a data table, not logic; if it exceeds ~250 lines split by domain prefix into `MessageKeys*` holders referenced by one facade. |
| Player/operator boundary judged wrong per line | Classification lives in the Phase 1 worklist and is reviewed before migration; when a method serves both (e.g. a shared `message` helper), only the `Player` overload migrates. |
