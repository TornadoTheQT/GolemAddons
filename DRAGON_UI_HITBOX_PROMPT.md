# GolemAddons: Dragon Seller checklist layout, dragon-only hitboxes, hot-path cleanup

## OBJECTIVE

Make three changes to the existing GolemAddons Fabric client mod (Minecraft 26.1.2):

1. **Dragon Seller layout.** Replace the ✔/✘ toggle tiles in `DragonSellerScreen` with checklist rows. Each row shows the colored name on the left and a checkbox on the right, for dragons, pieces and extras.
2. **Dragon hitboxes.** Add a setting that draws hitbox outlines on ender dragons only. The outlines follow the dragon smoothly, react to what the player is aiming at, and turn on or off as soon as the setting changes. No other entity ever gets an outline.
3. **Performance.** Simplify the code paths that run every frame or every few ticks. Behavior and selling safety must stay the same.

## PROJECT CONTEXT

- **Stack:**
  - Fabric Loom 1.19-SNAPSHOT, Minecraft 26.1.2 (unobfuscated, Mojang names), Fabric API 0.155.3+26.1.2, Java 25.
  - JUnit 5 tests run with `./gradlew build`.
  - Mod Menu is compileOnly.
- **Renamed APIs in 26.1.2** (do not guess the old names):
  - `GuiGraphicsExtractor`, `extractRenderState`, `extractBackground`, `extractWidgetRenderState` / `extractContents`.
  - `ContainerInput.QUICK_MOVE`, `Identifier`.
  - Fabric's level render hooks are in `net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents` (`AfterSolidFeatures`, `AfterTranslucentFeatures`, `BeforeBlockOutline`, …), alongside `LevelRenderContext` and `LevelExtractionContext`.
  - Vanilla hitbox drawing now lives in `net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer`. Its `emitGizmos(...)` and `showHitboxes(Entity, float, boolean)` use the debug gizmo system.
  - Dragon classes: `net.minecraft.world.entity.boss.enderdragon.EnderDragon` and `EnderDragonPart`.
- **Relevant code:**
  - `ui/DragonSellerScreen` (203 lines) builds `Tile` records into `CycleButton<Boolean>` widgets laid out in a centered column grid inside `EditorScreen` panels.
  - `ui/EditorScreen` supplies the header, `Panel`, footer, `maxContentWidth`, `centeredTop`, `addFooter`, and the PANEL_* / GAP constants.
  - `ui/UiText` holds the colors.
  - `dragon/DragonCatalog` holds `DragonType` (displayName, RGB color, sellByDefault), `Piece`, `Extra`, `itemNames`, `rulesFor`.
  - `config/GolemConfig` is at CURRENT_VERSION 2. It has `selling`, `miningAutoSell` and `dragonSeller`; `ConfigManager` does atomic saves and notifies listeners; `ConfigMigration` handles migration.
  - `autosell/AutoSellFeature` registers screen events and keybinds and runs the quick-sell state machine.
  - `autosell/ItemMatcher` holds `compileEnabled` and `match`. It validates every rule against canary names and runs a bounded regex per rule per stack.
- **Hot paths found during inspection:**
  - `DragonSellerScreen.refreshPreview()` rebuilds `ItemMatcher.compileEnabled(DragonCatalog.rulesFor(...))` every 10 ticks and on every click. That means up to 60 rules, each validated against all canaries, then a regex per rule for each of 36 slots.
  - `AutoSellFeature.updateButton` calls `InventoryScan.sellableSlots` every 5 ticks while a container is open. With dragon rules enabled, each stack runs through 60+ bounded regex matches.
  - `GolemHubScreen.extractBackground` does a pose push/translate/scale/pop for each of 64 particles every frame.
  - `RuleEditScreen` refreshes its preview every 10 ticks. Check whether it recompiles each time.

## PRE-IMPLEMENTATION INSPECTION

1. Read `DragonSellerScreen`, `EditorScreen`, `HubCard` (as a custom-widget example using `extractContents`) and `UiText` before changing the layout.
2. Look for a vanilla `Checkbox` widget in the 26.1.2 merged jar (`net.minecraft.client.gui.components.Checkbox`) and how it is built (`Checkbox.builder(...)`?). Decide between it and a small custom `AbstractButton` subclass. Vanilla puts the box on the left of its label, so a custom row widget is probably needed. Confirm by inspecting the class (`javap -p` on the class extracted to the scratchpad, never into the project folder).
3. Inspect `EntityHitboxDebugRenderer` (`showHitboxes`) and the gizmo/`ShapeRenderer` APIs to see how vanilla draws an AABB outline in 26.1.2.
   - Choose between `LevelRenderEvents` + `ShapeRenderer` / line render types and emitting gizmos the way vanilla does.
   - Use the officially supported path with the least code. Prefer a Fabric event over a mixin.
4. Check how `EnderDragon.getSubEntities()` (or the current name) exposes the parts. Parts are not in the level's entity list, so iterate them from the dragon.
5. Read `GolemConfig`, `ConfigMigration` and `ConfigMigrationTest` to see how a new section or field is added, defaulted and sanitized.
6. Read `ItemMatcher`, `InventoryScan`, `AutoSellRules` and `SellSession` before optimizing, so that every safety re-check stays in place.

## REQUIREMENTS

### 1. Dragon Seller checklist rows

- Each selectable item is one row widget.
  - Left side: the name in its color. Dragons use `DragonType.color`, pieces and extras use the existing colors. Disabled rows use the existing `OFF_COLOR`/gray.
  - Right side: a checkbox, drawn as a square border, filled with ✔ when checked.
  - The whole row is the click target, not just the box.
- Keep the three sections (Dragons / Armor and fragments / Extras), the master on/off toggle, the hint, the live preview line and the footer (Defaults / Save / Cancel).
- **Layout:**
  - Row width = widest label + gap + checkbox; text must never clip.
  - Columns: as many as fit within `maxContentWidth(560)`, all sections sharing one panel width, everything centered.
  - Rows pack top to bottom in each column, so names line up in a readable list.
  - At small window sizes or high GUI scale, fall back to fewer columns. Never overflow the screen or the footer. If even one column doesn't fit vertically, check the existing `EditorScreen` behavior and keep the content reachable (for example by shrinking row height to 16 or adding paging). Do not overlap the footer.
- **Interaction:**
  - Hovering a row highlights it.
  - Keyboard focus (Tab) shows a visible focus outline, and Space/Enter toggles the row.
  - Keep each row's tooltip and narration ("Young Dragon: sell" / "keep"). Narration must state the checked state.
  - When the master toggle is off, rows are inactive and dimmed.
- Update the hint text in `en_us.json` to match the checkbox metaphor (checked = sell). Remove lang keys that are no longer used.

### 2. Dragon hitboxes

- **Setting:**
  - Add a config field, default **off**, for example `dragonSeller.showHitboxes`, or a new `render.dragonHitboxes` section if it fits better. Decide from the config structure; a new section is cleaner if more render features are likely.
  - Expose it as a toggle in the GUI: on the Dragon Seller screen, or a hub card or Settings row if that fits the existing structure better. Explain the choice.
  - Optionally add an unbound keybind to toggle it, registered in the existing GolemAddons category, if that is trivial.
- **Dragons only:** draw outlines only for `EnderDragon` instances and their `EnderDragonPart`s. Do not draw anything for any other entity, including other mobs, players, armor stands or item frames.
  - The server may name its dragons (Young, Wise, …). The check is the entity class, never the name, so the feature can't be used as a general ESP.
- **Responsive:**
  - Positions use the frame's partial tick, interpolating between the old and current positions or bounding boxes, so outlines don't jitter or lag behind the model.
  - The part the crosshair targets (`Minecraft.hitResult` / `crosshairPickEntity`, resolved from a part to its parent dragon) is drawn in a highlight color. The rest of that dragon uses a second color, and other dragons a normal color.
  - Toggling the setting takes effect on the next frame with no restart.
  - Optionally fade alpha with distance. Do not add extra decoration.
- Respect vanilla's F3+B: if vanilla hitboxes are already shown, don't double-draw dragons. Skip ours in that case, or document why both are fine.
- Draw nothing when there is no level or player, when the GUI is hidden (F1), or when the setting is off. Make no per-entity allocations when off.

### 3. Performance simplification

Make only changes that reduce real work without lowering safety:

- **Exact-name fast path in `ItemMatcher`:**
  - Dragon rules are always `Pattern.quote(name)`, case sensitive, with no item ID. Match them with a `HashSet<String>` lookup on the normalized name before the regex loop.
  - Implement this cleanly: either `DragonCatalog` supplies the name set directly to `AutoSellRules`, or `ItemMatcher` detects literal rules at compile time.
  - Every name still goes through `NameNormalizer`. Regex rules still use the bounded matcher. The safety check order in `SellSession` (same screen/menu/containerId, cursor empty, slot still matches) is unchanged.
- **`DragonSellerScreen` preview:** compute the name set once per settings change, not every 10 ticks. Recounting matching stacks every 10 ticks is fine with a set lookup. Do not re-run `RuleValidator` canary validation on catalog rules each refresh.
- **`AutoSellFeature.updateButton`:** recount only when something changes.
  - Cache the result for a `(menu.containerId, menu.getStateId() or inventory change counter, rules instance)` key if 26.1.2 exposes a cheap change signal. Otherwise keep the 5-tick refresh, which is cheap once matching is a hash lookup.
  - While a session runs, use the session's counters and don't rescan. Confirm that is already the case.
- **`GolemHubScreen` particles:** compute the scaled rectangle coordinates directly and call `fill` once per particle, without the matrix push/pop. Keep the same look.
- **Other per-frame code:** cache `Component`s and widths that don't change between frames instead of rebuilding them each frame. Don't micro-optimize code that runs once per screen init.
- **Hitbox rendering** must be O(number of dragons) per frame. Find dragons by iterating `level.entitiesForRendering()` with a cheap `instanceof EnderDragon` check, or by tracking dragons on entity load/unload. Do not use an `AABB` world query each frame. Allocate nothing per frame beyond what the render API needs.

## IMPLEMENTATION GUIDANCE

- Put the checklist row in `ui/` as a small package-private widget (for example `CheckRow extends AbstractButton`).
  - It takes a label `Component`, color, initial value and an `onChange` consumer.
  - Override `extractContents`, `onPress(InputWithModifiers)` and `updateWidgetNarration`, as `HubCard` does.
- Put hitbox code in a new `render/DragonHitboxRenderer` registered from `GolemAddonsClient`. It reads config through `GolemAddonsClient.config()`, or better, caches a boolean from the existing config listener so the hot path doesn't touch the config object.
- Keep `DragonCatalog` free of Minecraft classes so it stays unit-testable.
- Bump config to v3 only if the schema change needs a migration. A new field with a default merged by `sanitize()` does not. If you bump it, add a migration test.
- No new dependencies.

## SECURITY / FAIR-PLAY REQUIREMENTS

- **Render-only.** The hitbox feature sends no packets, changes no entity state and adds no aim assist or auto-attack. Rendering hitboxes reveals nothing the client doesn't already have, but limiting it to dragons and defaulting it off keeps the feature narrow. Document this in the README.
- **Selling safety unchanged.** The fast path must not match anything the regex path wouldn't have matched. Exact equality after normalization equals the old `\Q…\E` full match, case sensitive. Prove it with a test.
- Config input from disk is still sanitized. An unknown or missing hitbox field falls back to off.

## UI/UX REQUIREMENTS

- Match the existing GolemAddons look: dark panels, `UiText` colors, vanilla font. No new gradients, glow or glass effects.
- The checkbox is drawn crisply on whole pixels and is at least 9–10 px, so it reads at GUI scale 1–4.
- The checked state is clear without relying on color alone: the ✔ glyph plus the box fill.
- Test that labels never clip at GUI scales 1, 2, 3, 4 and Auto, and at a small window (for example 854×480).

## EDGE CASES

- The dragon dies or despawns mid-frame, its parts are removed, or the player changes dimension. There must be no NPE and no leftover outline.
- Multiple dragons at once; a dragon very far away (respect render distance and frustum where the API supports it).
- Crosshair on a part versus the main body; crosshair on a different entity in front of the dragon.
- Setting toggled with a screen open, or on the Dragon Seller screen when Cancel discards it. The live state must follow the saved config, not the unsaved working copy, unless you preview it deliberately.
- Master Dragon Seller toggle off: rows are inactive, but the hitbox setting is independent of selling.
- Config files from earlier versions (v1, v2) still load and migrate.

## VERIFICATION

- **Unit tests:**
  - The exact-name fast path matches exactly the same set as the old quoted-regex rules over all catalog names. Variants such as "Strong Wise Dragon Helmet", "Young Dragon Boots ✪", lowercase names and names with § codes give the same result as before.
  - Mixed regex + exact rules give the same results as before.
  - The new config field defaults off and survives a sanitize round trip. Add migration tests if the version is bumped.
- Run `./gradlew build`. All existing tests (67) plus the new ones must pass.
- **In-game,** in a dev client (`./gradlew runClient`) or with the user:
  - The Dragon Seller screen at several GUI scales: no clipping, centered, clicking anywhere on a row toggles it, keyboard Tab/Space works, the preview count updates.
  - Hitboxes: only ender dragons are outlined. Test in a single-player End with `/summon ender_dragon` and confirm with a nearby zombie or armor stand. Outlines stay locked to the dragon while it flies, the aimed part highlights, and toggling the setting takes effect right away. F3+B on and off.
  - Quick sell and the in-GUI sell button still sell the same stacks as before.
- Say exactly which of these were checked and which were not.

## COMPLETION CRITERIA

- The Dragon Seller uses name-left / checkbox-right rows with no clipping at any GUI scale.
- Dragon hitboxes are configurable (default off), drawn only on ender dragons and their parts, interpolated, and highlight the aimed part.
- Catalog matching uses a hash lookup. Preview and button refreshes don't recompile or revalidate rules. The hub particle loop draws without per-particle matrix operations.
- The build passes with all tests green, the README and lang are updated, and the jar is rebuilt and copied to the project root.

## FINAL REVIEW

1. Does the result satisfy all three parts of the request?
2. Does it reuse `EditorScreen`, `UiText`, the config listener and the existing event patterns?
3. Does the matching fast path match exactly what the old path matched, and nothing more?
4. Are config values from disk validated, with safe defaults?
5. Is the hitbox feature strictly render-only and limited to dragons?
6. Are dimension changes, despawns and missing levels handled?
7. Are there no new per-frame allocations or world queries?
8. Is there no unnecessary abstraction, dead lang key or leftover `CycleButton` tile code?
9. Is the UI readable and keyboard-accessible at all GUI scales?
10. Do the build and tests pass, and was no unrelated behavior changed?
11. Are no temporary files, debug output or placeholders left behind, including no class dumps in the project folder?

## FINAL INSTRUCTION

> Inspect the relevant existing implementation before making changes. Do not assume architecture, APIs, dependencies, schemas, or versions that can be determined from the project. Implement the complete solution rather than a superficial patch. Preserve working behavior unless changing it is necessary for this task. Verify security-sensitive behavior at the trusted boundary, test realistic failure and edge cases, run the project's available validation tools, and review the final diff for regressions, unnecessary complexity, security issues, stale APIs, placeholders, debug artifacts, and unfinished work before considering the task complete. Do not claim functionality has been verified unless you actually verified it.
