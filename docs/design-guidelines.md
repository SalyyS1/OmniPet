# Design guidelines

How an OmniPet menu is expected to look and read. These conventions were already followed by most screens but written down nowhere, so a new contributor had to infer them from whichever renderer they happened to open first.

Everything here is enforceable by reading a renderer. Where a rule has a test, the test is named.

## Colour is a role, not a decoration

Renderers pick a role from `GuiColors`, never a `NamedTextColor` directly. The vocabulary is the whole file; if a new state does not fit an existing role, add a role rather than reaching for a raw colour.

| Role | Meaning |
| --- | --- |
| `TITLE` | Item names and neutral headings |
| `POSITIVE` | Active, affordable, succeeded |
| `WARNING` | Pending or actionable, not an error |
| `BLOCKED` | The click will not work — locked, unaffordable |
| `DESTRUCTIVE` | The click *will* work and cannot be undone |
| `DISABLED` | Present but with nothing to act on |
| `DESCRIPTION` | Static explanatory lore |
| `VALUE` | Dynamic numbers inside lore |
| `SECTION` | Headings inside a lore block |
| `ACCENT` | Informational identity rows |

`BLOCKED` and `DESTRUCTIVE` are both red on purpose — red means stop and read either way — but they are opposites, so the name has to carry the difference. A renderer that names the wrong one still looks correct today, which is exactly why the distinction cannot live in the colour.

## Lore has a fixed shape

Values first, then a blank line, then the action hint last:

```
<gray>Level</gray> <white>12</white>
<gray>Rarity</gray> <white>rare</white>
                          <- Component.empty()
<yellow>Click to manage</yellow>
```

The blank line is a real `Component.empty()`, not a space. Putting the hint last means a player's eye lands on what the click does after reading what the item is; interleaving hints among values makes both harder to scan.

Lore is never italic. `GuiItems` handles that — build items through it rather than setting meta directly.

## Titles use one grammar

`OmniPet <dark_gray>▸</dark_gray> <Screen>`, resolved from `MessageKey`. The hub is bare `OmniPet` because it is the root.

Never build a title from a raw Java string. Operators translate titles through `messages.yml`, so a hardcoded title is untranslatable and invisible to that file.

## Every button binds its action and its item together

Use `MenuLayout.put`, `putStack`, or `bind`. These record the click binding and the drawn item in one call, which is what makes a dead button impossible: a slot carrying an item always carries its action.

Writing `actions.put(n, ...)` and `inventory.setItem(n, ...)` as separate statements is the bug `MenuLayout` exists to prevent — the Studio's Close button was once drawn at a slot no action was bound to, so clicking it silently did nothing.

Slot and material overrides go through `MenuButtonStyle`, which refuses a collision or an out-of-range slot **with a warning**. Silently ignoring an operator's edit is the worst available outcome: they see it do nothing and cannot tell whether the key was wrong, taken, or out of bounds.

## Pagination

Fixed-size grids use `MenuPage`. It rounds correctly at an exact multiple of the page size and clamps a stale page index, so a list that shrinks under a viewer cannot leave them staring at an empty grid. Tests: `MenuPageTest`.

Cursor-paged screens — currently the admin transaction list — do **not** use `MenuPage`. An opaque continuation cursor has no page count and no way back, so showing it as "page 2 of 5" would be a lie. Those screens offer next-only and hide the arrow when the cursor runs out.

## Navigation

Every screen a player can reach from the hub offers a way back to it. A confirmation screen offers cancel. `COMPASS` returns to the hub; `ARROW` moves within a flow (previous, next, back to the list); `BARRIER` closes or cancels.

## Feedback belongs to one channel

Sound, action bar, and any visual effect go through `FeedbackService`. Call sites name a `FeedbackEvent`, never a `Sound` constant — the event-to-sound mapping is the operator's, and a call site naming a sound directly would drift from the config schema.

Feedback reaches the acting player only. This is a deliberate anti-griefing choice: a spammable click must not become a way to make noise or spawn particles at other players.

## Text belongs in `MessageKey`

All player-facing text resolves through `MessageKey` so operators can edit and translate it.

The documented exception is **operator audit output** — transaction pages, opaque cursors, reconcile reasons, delivery receipts. That output is an audit trail and stays in Java so an edited YAML file cannot distort it. The exception covers receipts, *not* command usage or permission errors, which are ordinary player-facing text.

Avoid putting `Displays.words(someEnum)` into player-facing text. It produces lowercased enum names, which cannot be translated and leak internal vocabulary like `PERSISTED_CONSUMPTION_PENDING`.

## Configurability

A menu listed in `MenuStyle.knownMenus()` must also be documented in `config.yml`. A button an operator can set but cannot discover is not configurable in any useful sense — `MenuStyleCodecTest.everyDocumentedButtonNameIsAcceptedByItsMenu` guards the reverse direction.

Display config is lenient: an unknown key warns and that setting alone falls back. Never fail startup over a cosmetic value — a typo in a menu material must not stop players using their pets. Config that steers entities (`render:`, `runtime:`) is strict instead, because there a silently ignored value leaves the operator believing they changed something they did not.
