---
title: "OmniPet operator customization, command usability, and placed-egg incubation"
description: "Four operator-facing gaps from live testing: no GUI customization surface, no egg/item format control, admin commands that demand raw UUIDs, and a placed-egg incubation mechanic with holograms and environment gates."
status: in-progress
priority: P1
effort: "6-9d"
tags: [gui, config, commands, incubation, ux]
created: 2026-08-04
blockedBy: []
blocks: []
---

# OmniPet operator customization, command usability, and placed-egg incubation

## Overview

Four gaps reported from live testing. Three are missing operator surfaces; one is a new gameplay
mechanic that replaces a guard shipped in `32834f8`.

The bug half of that session is already fixed and is **not** in scope here: escrow slot-pinning (the
egg-not-consumed and frozen-countdown reports), unlabelled consumables, and the dead Studio Close
button all landed in `32834f8`.

| Gap | Evidence | Operator/player experience |
| --- | --- | --- |
| No GUI customization | ~90 `Material` literals, every slot index inline, 6 Studio titles hardcoded | Cannot rebrand a single menu without a rebuild. |
| No egg/item format control | `EggAdminController.java:343` `Material.TURTLE_EGG`; grep for `setCustomModelData`/`ItemFlag`/`addEnchant` over `omnipet-paper/src/main` → **0** | Cannot use a resource-pack model. Every server's egg looks identical. |
| Admin commands demand raw UUIDs | `OmniPetCommandTree.java:138-150` `<player-uuid> <incubation-uuid> <action-uuid>`; `HatchAdminCommandParser.uuid` accepts UUID only | Operator must read a UUID out of a YAML file and invent a third one. |
| Egg cannot be placed to incubate | `EggBlockPlacementListener` (from `32834f8`) cancels placement | Placing is the intuitive action; today it is refused. |

## Goals

| # | Goal | Priority |
| --- | --- | --- |
| 1 | Admin commands accept player names, auto-generate action IDs, and suggest online names | P1 |
| 2 | Egg and consumable appearance is operator-configurable, escrow-safe | P1 |
| 3 | Menus are operator-restyleable: material, slot, size, title, lore | P2 |
| 4 | An egg placed beside a heat source incubates, showing a hologram countdown | P2 |

## Phases

| # | Phase | Status | Depends on | Why this order |
| --- | --- | --- | --- | --- |
| 1 | Command usability | Complete | — | Cheapest, largest daily impact, touches no persistence. |
| 2 | Egg and item format | Complete | — | Cheap, self-contained; establishes the item-appearance config shape Phase 3 reuses. |
| 3 | [GUI customization](./phase-03-gui-customization.md) | Pending | 2 | Largest. Needs the appearance-config precedent from Phase 2. |
| 4 | [Placed-egg incubation](./phase-04-placed-egg-incubation.md) | Pending | 2 | Riskiest — touches escrow. Goes last so it cannot block the other three. |

Phase 4 is deliberately last. It is the only phase that changes where a paid egg lives, and escrow is
the component holding players' items and money. If it stalls, Phases 1-3 still ship.

## Key constraints

- `omnipet-core` must not gain Bukkit/Paper/Adventure imports. `checkCoreBoundary` enforces it. All
  config, GUI, listener, and hologram code lands in `omnipet-paper`.
- **No new bundled dependency.** `checkDistributionArtifact` must stay green. Display entities and
  `TextDisplay` are Paper API already on the compile classpath — no hologram library.
- Egg identity is load-bearing. `omnipet:egg`, `omnipet:item_nonce`, and `omnipet:item_schema`, the
  `ITEM_SCHEMA` value, the amount-one invariant, and the 8 KiB snapshot cap must not change. Appearance
  is captured *before* the fingerprint is derived, so appearance is safe to configure — but only read at
  mint time. Mutating an already-escrowed egg's appearance invalidates its fingerprint.
- Every new config section is **lenient**, following `messages.yml` and `gui:`, not strict `storage:`.
  A typo in a material name must not stop players using their pets.
- Files over 200 lines get split rather than grown. `OmniPetCommand` (630) and `PetStudioController`
  (639) are already over and must not grow further.

## Accepted contract changes

1. Admin recovery commands accept a player **name or** UUID, and the trailing `<action-uuid>` becomes
   optional. Existing UUID invocations keep working — this is purely additive.
2. `config.yml` gains an `items:` appearance block per item and a new `gui.menus:` section. Both
   optional; absent means today's hardcoded values.
3. Phase 4 removes the `EggBlockPlacementListener` guard from `32834f8` and replaces it with a
   placement path that keeps the egg's durable identity in a block record.

## Success Criteria

- [ ] `gradlew.bat clean build` passes; all four guard tasks green; no test skipped.
- [ ] `/pet admin hatch inspect <name>` works without a UUID, and the old UUID form still works.
- [ ] Tab-complete suggests online player names for admin commands that take a player.
- [ ] An operator can change the egg material and give it custom model data from config, and a minted
      egg still round-trips mint → observe → capture.
- [ ] An operator can change a menu's material, title, and one button's slot from config, and a moved
      button still dispatches its action.
- [ ] An egg placed beside a lit heat source incubates and shows a countdown hologram; placing it
      anywhere else refuses with the reason.
- [ ] A fire-affinity egg refuses to incubate unless surrounded by lava, and says so.
- [ ] Breaking a placed egg returns the exact egg item, with identity intact and no duplication.

## Risks

| Risk | Mitigation |
| --- | --- |
| Configurable material breaks escrow fingerprinting | Appearance read at mint only; fingerprint still derived at capture from the actual stack. Existing round-trip test extended to a non-default material. |
| Operator-moved button reintroduces the dead-Close bug | Config-driven layout must bind action and item in one atomic call, as `PetManagementMenuRenderer.put` does. A drawn slot with no action is a load-time warning. |
| A placed egg is duplicated by break/place cycling | Block record keyed by the egg's existing nonce; break consumes the record before returning the item. Single durable owner at all times. |
| Hologram entities leak on chunk unload or crash | Display entities are re-derived from the block record on load, never persisted as the source of truth. |
| A bad material name in config disables a menu | Lenient per-key fallback to the current hardcoded value, with a load warning naming the key. |
| Placed-egg escrow diverges from held-egg escrow | Phase 4 reuses the same journal and stages; only the *location* of the item changes, not the saga. |

## Out of scope

Riding, Active Party, rename control, MMOItems identities, a live MythicMobs skill picker, admin
target mode, and live-server certification. Studio text localization is Phase 3 only if it fits;
`MessageKey` deliberately keeps operator audit text in Java.

<!-- slug: omnipet-customization-commands-egg-placement -->
