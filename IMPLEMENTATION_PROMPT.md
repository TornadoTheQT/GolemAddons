# Implementation Prompt: GolemAddons, a Fabric Auto-Sell Mod (Minecraft 26.1.2)

## OBJECTIVE

Build a new **client-side Fabric mod** called **GolemAddons** (mod id `golemaddons`) for **Minecraft Java 26.1.2**. Its first feature is **Auto-Sell**:

1. The user writes a list of **sell rules** in a clean in-game config screen. Each rule is a regex for an item's name, with an optional item ID.
2. The user opens an NPC's sell GUI on their server. Selling works by shift-clicking a stack from the player inventory into that GUI.
3. The user presses a keybind or clicks a button on that screen. The mod then **shift-clicks (quick-move) each matching stack one at a time, with a short configurable delay between clicks**, until every matching stack has been sold or something goes wrong.

The project must be a working skeleton that is easy to extend later: one real feature, built so more features can be added without restructuring.

**Safety comes first.** Selling can't be undone, so the mod must never sell anything the user didn't clearly target. If the mod isn't sure, it must skip the stack or stop. It must never guess.

## PROJECT CONTEXT

- The working directory `GolemAddons/` is **empty**. This is a greenfield project. Generate it from the official Fabric template (fabricmc.net/develop/template) or an equivalent hand-written Loom setup.
- The server belongs to the user. The NPC sell GUI is a server-side container screen. The mod is **client-only**. It needs no server component and must not use custom networking.
- Items to sell are custom items like "Enchanted Obsidian", "Enchanted End Stone" and "Enchanted Crying Obsidian". They are probably vanilla items with a custom display name and an enchantment glint, so **the display name is the main thing to match**. The underlying item ID is an optional extra constraint.
- **Version caution:** Minecraft 26.x uses Mojang's year-based version scheme. Starting with 26.1 it ships unobfuscated, and the Fabric toolchain uses Mojang's official names. Most tutorials and older code online use Yarn names (`HandledScreen`, `ScreenHandler`, `SlotActionType`, `MinecraftClient`) that **don't apply here**. Before writing code that depends on the version, look up these values from official sources for 26.1.2 and pin them:
  - the Fabric Loader, Fabric API and Fabric Loom versions (fabricmc.net/develop)
  - the required Java version (check the template or the Minecraft release notes; don't assume 21)
  - the actual class and method names for: the container screen base class, the container menu, slot click handling (`MultiPlayerGameMode#handleInventoryMouseClick` or whatever it's called now), the click type for quick-move, keybinding registration, client tick events, and screen events
  If an API in this prompt doesn't exist in 26.1.2, use its real equivalent and note the difference. Don't invent APIs.

## PRE-IMPLEMENTATION INSPECTION

1. Confirm the directory is still empty. If any files now exist, inspect them and build on them; don't overwrite them.
2. Look up the current 26.1.2 Fabric versions and template. Record the exact versions you use in `gradle.properties`.
3. Check Fabric API's 26.1.2 modules for: `ClientTickEvents`, `ScreenEvents` (for adding a button to an existing screen, and `afterRender`/`remove` hooks), `KeyBindingHelper` (or its renamed equivalent), and `ClientLifecycleEvents`.
4. Find the vanilla quick-move click path the client uses when a player shift-clicks a slot. The macro must send **exactly the same click packet as a real shift-click**, by calling the game-mode click method with the quick-move click type. It must not simulate OS keyboard or mouse input.
5. Confirm how to get a stack's display name as plain text with all formatting removed (the `Component#getString()` equivalent, plus any legacy `§` codes stripped).

## REQUIREMENTS

### Sell rules
- A rule has: `enabled` (bool), `namePattern` (regex string), `itemId` (optional, e.g. `minecraft:obsidian`, blank means any), `caseInsensitive` (bool, default `false`), and `label` (optional, display only).
- A stack matches a rule only if **all** of these are true:
  - the rule is enabled
  - the rule is valid (it passed validation, see the Security section)
  - the stack is not empty
  - the stack's plain-text display name **fully matches** the pattern (`Matcher.matches()`, never `find()`)
  - if `itemId` is set, the stack's registry ID equals it exactly
- A stack is sold if it matches **any** enabled, valid rule.
- Ship with **no rules enabled by default**. Optionally include the three example rules (`Enchanted Obsidian`, `Enchanted End ?Stone`, `Enchanted Crying Obsidian`) as **disabled** rules so the user can see the format.

### Sell-screen detection
- Config holds `sellScreenTitlePattern`, a regex fully matched against the open container screen's plain-text title, e.g. `Sell Items` or whatever the user's NPC GUI is called.
- **The macro must refuse to run unless** the current screen is a container screen with a menu (not the player inventory screen) **and** its title fully matches this pattern. If the pattern is blank or invalid, the macro is disabled and the UI says why.
- This stops the macro from shift-clicking valuables into an ender chest, a regular chest, or any other GUI.

### Triggering
- The macro **only starts on an explicit user action**:
  - a keybind (registered under a "GolemAddons" category, unbound by default or a sensible unused key), pressed while the sell screen is open; and
  - a small button drawn on the sell screen showing the live match count, e.g. `Sell 7 matching`. When nothing matches, the button reads `Nothing to sell` and is disabled.
- While running, the same keybind or button (now `Stop`) cancels immediately. Pressing Escape or closing the screen also cancels.
- Do **not** add auto-sell-on-open in this version. Keep a clean place for it (a config flag that isn't exposed yet is fine, or just leave room for it).

### Sell execution
- Scan only **player inventory slots** inside the open menu: main inventory, plus hotbar if allowed. Never touch container (NPC-side) slots, armor, offhand, or crafting slots. Find player slots by checking that the slot's container is the player's inventory, not by hardcoded slot indexes, because the NPC GUI's size varies.
- Config `protectHotbar` (default `true`): hotbar slots are never sold.
- Never sell the stack in the player's selected hotbar slot, whatever the other settings say.
- Put the matching slots in a queue. Every `clickDelayTicks` ticks (default `4`, allowed `1–20`), process one slot:
  1. **Re-check right before clicking.** Confirm that the same screen and the same menu/container ID are still open, the title still matches, the cursor-carried stack is empty, and the slot's current stack *still* matches a rule. If the slot no longer matches, skip it. If the screen or menu changed, abort the whole run.
  2. Send one quick-move click for that slot.
  3. On a later tick, check the result. If the slot is unchanged after the click (the server rejected it or the NPC didn't accept it), count a failure. After `3` failures in a row, abort and tell the user.
- Run all timing on the client tick (`END_CLIENT_TICK`). **No `Thread.sleep`, no background threads, no timers** touching game state.
- Hard cap of clicks per run (e.g. `36 × 2`) as a backstop against loops.
- When a run finishes or aborts, show a short message (action bar or chat, client-only): `Sold 7 stacks` / `Stopped: screen closed` / `Stopped: server rejected 3 clicks`.

### Config screen
- Opened by its own keybind. If Mod Menu is installed, also open it from Mod Menu, but only through an **optional** integration. The mod must work fine without Mod Menu.
- Contents:
  - global settings: sell-screen title pattern, click delay (ticks, with ms shown), protect hotbar
  - a scrollable rule list. Each row shows: enabled toggle, label/pattern, item ID if set, a validity marker, and Edit and Delete buttons
  - an "Add rule" button
- Rule edit screen: fields for label, name pattern, item ID, and case-insensitive. It also shows:
  - **live validation**: an inline error message under the field, with Save disabled while the rule is invalid
  - a **live preview** of every stack currently in the player's inventory that this rule would match, plus a count. If nothing matches, show `Matches nothing in your current inventory`. This preview is the user's main protection against a bad regex.
- Ask for confirmation before deleting a rule.
- Save to disk on Save/Done. Cancel throws away changes.

## IMPLEMENTATION GUIDANCE

Suggested structure. Adjust names to fit the actual template, and avoid adding layers that don't serve a purpose yet:

```
com.golemaddons
  GolemAddonsClient            // ClientModInitializer: registers keybinds, events, loads config
  config/
    GolemConfig                // plain data: global settings + List<SellRule>
    ConfigManager              // load/save JSON, validation on load, atomic writes
  autosell/
    SellRule                   // data
    RuleValidator              // PURE Java, no Minecraft imports: validates + compiles patterns
    ItemMatcher                // PURE Java core: (plainName, itemId) -> matches?
    SellSession                // tick-driven state machine for one run
    AutoSellFeature            // wires keybind, screen button, tick handler for this feature
  ui/
    ConfigScreen, RuleListWidget, RuleEditScreen
```

- **Keep the matcher and validator free of Minecraft classes.** They take plain `String`s, so they can be unit tested with plain JUnit. The Minecraft-facing code only turns an `ItemStack` into `(plainName, itemId)`.
- Compile each pattern **once**, when it's validated or the config is loaded. Never compile inside the scan loop.
- `SellSession` is an explicit state machine (`IDLE → RUNNING → DONE/ABORTED`) that stores the abort reason. That way the UI and later features can show status without guessing.
- One `AutoSellFeature` class owns this feature's registrations. Future features (other macros, overlays) can each get a sibling class. **Don't** build a generic plugin/feature framework yet. A simple list of features set up in the initializer is enough.
- Config: Gson (bundled with Minecraft) at `config/golemaddons.json`. Save by writing to a temp file and then doing an atomic move. On load, if the JSON can't be parsed, **rename the bad file to `golemaddons.json.bad-<timestamp>`**, log a warning, and use defaults. Don't silently overwrite the user's rules. Include a `configVersion` field for future migrations.
- Use vanilla widgets (`Screen`, `EditBox`, `Button`, the vanilla scrollable list classes) under their 26.1.2 names. No extra UI library unless the template already has one.
- Mixins: only if Fabric API's screen events can't do what's needed. If one is needed, keep it small and documented.

## SECURITY / SAFETY REQUIREMENTS

The "attacker" here is mostly **the user's own typos** plus anything strange the server does. Every safeguard below must be built and enforced in `RuleValidator`/`ItemMatcher`, not only in the UI.

**Regex validation.** A rule is invalid, and never used for matching, if any of these apply:
- the pattern is blank, or longer than **128 characters**
- it fails to compile (show the `PatternSyntaxException` description to the user)
- it matches the empty string
- it matches anything in a built-in **canary list** of names that a sell rule should never hit, e.g. `Diamond Sword`, `Netherite Pickaxe`, `Elytra`, `Enchanted Golden Apple`, `Totem of Undying`, `Shulker Box`, `a`, `aaaaaaaaaaaaaaaa`, `123`, plus a few randomly generated strings. This catches `.*`, `.+`, `\w+`, `[A-Za-z ]+`, `.*Obsidian.*`-style catch-alls, and similar.
- it contains no literal letter at all (patterns made only of wildcards or character classes)
- it uses embedded flags that change matching scope (`(?s)`, `(?m)`, inline `(?i)`). Case-insensitivity only comes from the rule's explicit checkbox, so the UI always shows the real behaviour.

The canary list must not block the user's intended rules. Test that `Enchanted Obsidian`, `Enchanted End ?Stone` and `Enchanted (Crying )?Obsidian` all pass.

**Full match only.** `matches()`, always. `Enchanted Obsidian` must not match `Enchanted Obsidian Pickaxe` or `Super Enchanted Obsidian`.

**Normalize names before matching.** Remove all formatting (styles and legacy `§x` codes) and trim. Don't collapse internal whitespace. If a name contains control characters, fail closed and skip the stack.

**ReDoS protection.** Java regex has no timeout. Wrap the input in a `CharSequence` that counts `charAt` calls and throws after a fixed budget (e.g. 10,000). Treat a thrown exception as **no match**, and mark the rule invalid for the session with a visible warning. Cap input length (skip any name over 256 characters). Run the same protection during validation, so a pattern like `(a+)+$` is caught before it's saved.

**Fail closed everywhere.** Any exception during matching or session handling means no match, or abort. Log it and never let it crash the client. An invalid config means no rules are active.

**Scope.** No server-side code, no custom packets, no network calls, no reading of any files except the config file.

## PERFORMANCE REQUIREMENTS

- Match counting for the screen button and the preview runs only when the inventory changes, or at most once every few ticks. It must not run every frame.
- Patterns are precompiled. Scanning about 36 slots against a handful of rules is negligible. Don't add caching beyond that.

## UI/UX REQUIREMENTS

- Plain, clean vanilla-style layout: consistent spacing, aligned labels, a readable dark background. Use the normal vanilla screen background. No custom gradients, glow effects, translucent glass panels, or decorative icons.
- Show errors in red, directly under the field they're about. Valid rules show a small, plain "OK" marker.
- Every interactive element works with keyboard navigation (Tab, Enter, Escape) like vanilla screens do. Escape on an edit screen counts as Cancel.
- Empty state for the rule list: `No sell rules yet. Add one to get started.`
- The sell-screen button must not cover the container's slots. Place it relative to the container's left/top position and image size, beside or above the GUI, and keep it fully on screen at small GUI scales and window sizes.
- All user-facing strings go in `assets/golemaddons/lang/en_us.json`.

## EDGE CASES

- The player opens the sell screen with an empty inventory, or no stacks match.
- A stack is moved or used up between the scan and its click (the re-check skips it).
- The server closes the GUI or opens a different one partway through a run (abort).
- The server reopens a GUI with the **same title but a new container ID** (abort; don't carry on).
- The user is holding an item on the cursor (refuse to start, or abort).
- The NPC refuses the item, or the server lags so the slot updates late. Don't count a failure until at least one tick after the click. If the slot is still unchanged a few ticks later, count it as a failure.
- Stacks of the same custom item in both the hotbar and the main inventory while `protectHotbar` is on.
- An item with a matching name but a different item ID when the rule sets one.
- The user changes the config while a sell run is in progress (finish or abort the run using the rules it started with; no hot-swapping halfway through).
- The config file is missing, empty, or corrupted, or has an unknown `configVersion`.
- A disconnect or world change during a run (reset the session).

## VERIFICATION

**Unit tests (JUnit, no Minecraft runtime)** for `RuleValidator` and `ItemMatcher`:
- the intended patterns are valid and match their exact names
- full-match behaviour: no matches on names with extra text before or after
- every rejection rule: blank, too long, not compilable, matches empty, matches a canary, wildcard-only, embedded flags
- catastrophic patterns like `(a+)+$` and `(.*a){12}` are rejected or neutralized within the step budget, and the test finishes quickly
- case sensitivity on and off
- item ID constraint on and off
- names with formatting codes removed before matching

**Build:** `./gradlew build` must pass, including tests.

**Manual in-game test (`./gradlew runClient`)**, on a local test world or the user's server. Describe exactly what was tested. If you can't run the client in your environment, say so and give the user a step-by-step manual test checklist covering:
1. A rule for a renamed item (use an anvil or `/give` with a custom name) sells only that item. A similarly named item with extra text in its name is left alone.
2. The macro does nothing in a normal chest, an ender chest, and the player inventory screen.
3. Hotbar protection and the selected-slot protection both work.
4. Closing the GUI partway through stops the run immediately.
5. Invalid regexes show errors, can't be saved, and `.*` is rejected.
6. The config survives a restart. A corrupted config is backed up, not wiped.

## COMPLETION CRITERIA

- The project builds against pinned, verified 26.1.2 Fabric versions and loads in a client with no errors in the log.
- A user can add, edit, toggle and delete rules in the config screen, with live validation and a live inventory preview. Rules persist across restarts.
- On a screen whose title matches the configured pattern, the keybind or button sells all matching player-inventory stacks one at a time with the configured delay, and reports the result.
- The macro never runs on a non-matching screen, never touches non-player slots, never sells protected slots, and stops safely on every abort condition listed above.
- Every validator and matcher safety test passes.
- The code has no TODOs, placeholder handlers, fake data, or debug logging spam. A short `README.md` explains installation, the config screen, how to write a safe rule, and the safety guarantees.

## FINAL REVIEW

1. Does it do exactly what was asked: regex rules, a clean UI, shift-click selling one stack at a time with a delay?
2. Were every Minecraft and Fabric API name and version checked against 26.1.2 rather than copied from older Yarn-era material?
3. Can **any** code path sell a stack that doesn't fully match a valid, enabled rule, sell from a protected slot, or run on the wrong screen? Trace each one.
4. Is every safety check enforced in core logic, not only in the UI?
5. Is all timing tick-based on the client thread?
6. Is ReDoS handled for both validation and matching?
7. Do config load and save fail safely, without losing user data?
8. Is the structure easy to extend with new features, without speculative abstractions?
9. Is the UI keyboard-accessible, unobtrusive on the sell screen, and free of decorative styling?
10. Are the tests meaningful? Did `./gradlew build` pass?
11. Is there any leftover dead code, debug output, or unused dependency?

## FINAL INSTRUCTION

> Inspect the relevant existing implementation before making changes. Do not assume architecture, APIs, dependencies, schemas, or versions that can be determined from the project. Implement the complete solution rather than a superficial patch. Preserve working behavior unless changing it is necessary for this task. Verify security-sensitive behavior at the trusted boundary, test realistic failure and edge cases, run the project's available validation tools, and review the final diff for regressions, unnecessary complexity, security issues, stale APIs, placeholders, debug artifacts, and unfinished work before considering the task complete. Do not claim functionality has been verified unless you actually verified it.
