# GolemAddons

A client-side Fabric mod for **Minecraft 26.1.2**. Press **K** to open the GolemAddons menu. It has four cards; the first three open a page with that category's features:

- **Selling**
  - **Mining Auto-Sell**: sells items matching your own regex rules, e.g. Enchanted Obsidian.
  - **Dragon Seller**: sells dragon armor, fragments and other dragon drops. You pick which dragons.
  - **Sell Settings**: the sell screen title, click delay and hotbar protection, which both sellers use, plus the **Quick sell** key.
- **QoL**
  - **Auto Clicker**: left-clicks at a random speed between two CPS values while you hold a key.
  - **Camera**: skip the front F5 view, set the third-person distance, and let the camera pass through blocks.
  - **Terminals**: Floor 7 terminal solvers with a compact, recolourable GUI, wrong-click blocking and hover click.
- **Visuals**
  - **Cosmetics**: a custom name and cape that every other GolemAddons player sees, on any server, with a live preview of your player.
  - **Animations**: move, rotate, scale and re-time your first-person held item, with a live preview and shareable presets.
  - **Dragon Hitboxes**: click the tile to turn dragon hit-part outlines on or off.
- **Party Finder**: list and join GDrag and NDrag parties with other GolemAddons players on the same server, live. Also `/gapf`.

The bottom-right of the menu shows the version and a refresh button: click it to check for updates (or to install one that is ready), right-click it for update settings. A short note next to it says what happened.

The menu is drawn on a fixed 640×360 layout scaled to your window, so it fills the screen the same way at any GUI scale.

When you're on your NPC's sell screen, one **Sell N matching** button sells everything both features select. It shift-clicks one stack at a time, with a short delay between clicks.

## Requirements

To play:
- Minecraft 26.1.2, Fabric Loader 0.20.1+, Fabric API
- Mod Menu is optional. If it's installed, you can also open the settings from it.

To build:
- [Git](https://git-scm.com/downloads)
- Any JDK 17 or newer on your `PATH` (or `JAVA_HOME`), e.g. [Temurin 21](https://adoptium.net/). This only launches Gradle. The mod itself compiles with Java 25, which Gradle downloads automatically into `~/.gradle/jdks` the first time (via the foojay toolchain resolver and `gradle/gradle-daemon-jvm.properties`).
- An internet connection for the first build: Gradle 9.8.2, Java 25, Minecraft 26.1.2 and the Fabric/Mod Menu dependencies are all downloaded and cached. The first build takes several minutes; later builds are fast.

Nothing else needs installing. The Gradle wrapper (`gradlew` / `gradlew.bat`) is in the repo.

## Build

Clone and build:

```bash
git clone https://github.com/TornadoTheQT/GolemAddons.git
cd GolemAddons
./gradlew build          # Windows PowerShell/cmd: .\gradlew.bat build
```

The mod jar is `build/libs/golemaddons-<version>.jar` (the `-sources.jar` next to it is not needed). Copy it into your `.minecraft/mods` folder, next to Fabric API. `build` also runs the unit tests; use `./gradlew build -x test` to skip them.

Other useful tasks:

| Command | What it does |
|---|---|
| `./gradlew runClient` | Starts a dev Minecraft client with the mod loaded (game files go in `run/`). |
| `./gradlew test` | Unit tests only. |
| `./gradlew runClientGameTest` | In-game smoke test: opens every menu and saves screenshots to `build/run/clientGameTest/screenshots`. |

**Version and cosmetics server:** the mod version (`version=`) and the cosmetics/party-finder server URL (`cosmetics_api_url=`) live in `gradle.properties`. The URL is baked into the jar at build time, so a jar built from this repo talks to the same server as everyone else. Set it blank to build a jar with cosmetics and the party finder off. You only need the [worker](worker/README.md) (Node.js + a Cloudflare account) if you want to change or redeploy the server, not to build the mod.

**IDE:** open the folder in IntelliJ IDEA as a Gradle project; it imports the run configurations automatically.

**Troubleshooting:**
- `Unsupported class file major version` or Gradle refusing to start: your launch JDK is older than 17. Check `java -version`.
- Toolchain download fails (offline or behind a proxy): install a Java 25 JDK yourself; Gradle will find it.
- On macOS/Linux, if `./gradlew` says permission denied: `chmod +x gradlew`.

## Using it

1. Press **K** (rebindable under Controls → GolemAddons) to open the menu.
2. Open **Selling → Sell Settings** and set the **sell screen title** to a regex that matches your NPC GUI's title exactly, e.g. `Sell Items`. Nothing sells until this is set.
3. Set up what to sell:
   - **Mining Auto-Sell**: add or enable rules. Three example rules for Enchanted Obsidian, End Stone and Crying Obsidian are included, all disabled.
   - **Dragon Seller**: tick the dragons, pieces and drops to sell. Each row is the colored name with a checkbox on the right; click anywhere on the row.
4. Click **Save** on each screen.
5. Open the NPC's GUI. A **Sell N matching** button appears beside it. Click it, or press the Auto-sell keybind (unbound by default). Click again, or close the GUI, to stop.

### Quick sell

Quick sell does the whole thing with one key: it runs a command (default `/shop`), waits for your sell screen to open, sells everything selected, then closes the GUI like pressing Escape.

1. In **Sell Settings → Quick sell**, set the command (without or with the `/`).
2. Click **Key** and press the key you want to use. Escape clears the binding. The key is saved immediately; you can also change it under Controls → GolemAddons.
3. Press that key while no GUI is open.

How it behaves:
- If the command doesn't open a screen matching your sell screen title within 5 seconds, it stops and tells you the title it saw.
- It waits a few ticks after the screen opens so the server can fill it, then scans and sells with the same checks as the button.
- The GUI is closed only after a clean finish ("sold" or "nothing to sell"). If the run was stopped (by you, the server, or a held item), the GUI stays open so you can see why.
- The command must be a single command word plus optional simple arguments (no newlines or `;`), at most 64 characters.

Sell Settings:

| Setting | Default | Meaning |
|---|---|---|
| Sell screen title | *(blank)* | Regex the whole GUI title must match. Blank means selling is off. |
| Delay | 4 ticks (200 ms) | Time between clicks, from 1 to 20 ticks. |
| Protect hotbar | On | Hotbar slots are never sold. |
| Quick sell command | `shop` | Run by the quick-sell key. Blank turns quick sell off. |

The config file is `config/golemaddons.json`. A version 1 file from an older build is upgraded automatically, and the original is kept as `golemaddons.json.v1-backup`.

## Dragon Seller

The Dragon Seller covers 11 dragons: Young, Wise, Strong, Unstable, Old, Protector, Superior, Arcane, Chaotic, Berserk and Golden. For each selected dragon it sells the pieces you pick: **Helmet, Chestplate, Leggings, Boots, Fragment**. It also sells these drops, each with its own toggle: **Summoning Eye, Crystal Fragment, Dragon Scale, Dragon Claw, Aspect of the Dragons, Travel Scroll to Dragon's Nest**.

**Defaults:**
- Sells every piece and every drop.
- Sells the Young, Strong, Unstable, Old and Protector dragons.
- Keeps **Chaotic, Berserk, Arcane, Superior, Wise and Golden**.

The **Defaults** button restores these settings.

When an update adds a new item (like the travel scroll), it starts **unchecked** for existing configs. An update never starts selling something you didn't pick. Fresh installs get the defaults above.

Item names are matched **exactly**, e.g. `Young Dragon Helmet` or `Aspect of the Dragons`. A reforged or starred item (`Strong Wise Dragon Helmet`, `Young Dragon Boots ✪`) has a different name, so it is never sold. If your server names these items differently, use Mining Auto-Sell rules instead.

### Dragon hitboxes

Click the **Dragon Hitboxes** tile under **Visuals** (it saves straight away), or bind **Toggle dragon hitboxes** under Controls → GolemAddons (unbound by default; the key saves the setting straight away). Off by default.

- Only ender dragons get outlines, one box per hit part (head, neck, body, wings, tail). The check is the entity type, not its name, so no other mob or player is ever outlined.
- The part under your crosshair is red and the rest of that dragon is gold. Other dragons are teal.
- Boxes are interpolated every frame, so they stay on the dragon while it flies.
- Nothing is drawn while F1 hides the HUD, or while vanilla F3+B hitboxes are on (those already show dragon parts).
- Render-only: it sends no packets and doesn't change aiming or attacking.

If your server's "dragons" are not real ender dragon entities (for example a custom model on another mob), they won't get outlines.

## Writing a safe rule

A rule's regex must match the item's **entire** display name (with colours removed), not just part of it.

- `Enchanted Obsidian` matches only `Enchanted Obsidian`. It doesn't match `Enchanted Obsidian Pickaxe`.
- `Enchanted (Crying )?Obsidian` matches both obsidian variants.
- `Enchanted End ?[Ss]tone` matches `Enchanted End Stone`, `Enchanted Endstone` and `Enchanted EndStone`.
- **Item ID** is optional. Setting it (e.g. `minecraft:obsidian`) also requires the underlying item to be that exact item.
- **Ignore case** makes the match case-insensitive. Inline flags such as `(?i)` aren't allowed.

The edit screen shows every stack in your inventory that the rule would match right now. Check it before you save.

## Safety guarantees

Selling can't be undone, so the mod refuses to act whenever it isn't sure:

- **It rejects broad or risky regexes:**
  - blank patterns or patterns over 128 characters
  - patterns that match an empty name
  - patterns with no literal text (`.*`, `\w+`, `[A-Za-z ]+`)
  - inline flags
  - patterns that would match a built-in list of valuable or generic names (Diamond Sword, Elytra, Totem of Undying, Enchanted Book, Shulker Box…), plus some random names. This also catches catch-alls like `Enchanted .*`.
- **Slow patterns can't freeze the game.** Every match runs with a step budget. A pattern that runs out during validation is rejected. A rule that runs out on a real item name is skipped for the rest of the session, and you get a warning in chat.
- **It only runs on the sell screen.** That means a container GUI whose title fully matches your pattern, and never your own inventory. The title pattern also can't match vanilla container names like Chest, Ender Chest or Barrel.
- **It only touches your own inventory.** It only uses your 36 main and hotbar slots, never armor, offhand or the NPC's slots. The item in your hand is never sold, and the hotbar is protected by default.
- **It re-checks before every click.** Each click is preceded by a check that the same GUI and menu are still open, your cursor is empty, and the slot still holds a matching stack. A stack that changed is skipped. A changed GUI stops the run.
- **It only starts when you ask.** It never sells automatically when a GUI opens. Clicks are the normal shift-click packet, and timing comes from the game tick.
- **It stops if the server pushes back.** The run stops after 3 clicks in a row that the server didn't act on, and there's a hard cap of 72 clicks per run.
- **Your config is protected.** An unreadable config file is renamed to `golemaddons.json.bad-<timestamp>` instead of being overwritten.

## Cosmetics: custom names and capes

Everyone running GolemAddons with the same cosmetics server sees each other's custom name and cape, on any server. Players are identified by their Minecraft account UUID.

Set them in **GolemAddons → Visuals → Cosmetics**: type a name or a cape link and press **Apply** (or **Reset** to remove it). A preview on the left shows your player walking and turning, with the nametag (the name you're typing, if it's valid) and your cape, custom if you have one, otherwise your normal cape. Hover over a field for the rules. The same can be done with commands:

```
/ga custom name &6&lZach          set your name (&0-9 &a-f colors, &k-o formats, &r reset, && for &)
/ga custom cape https://…/cape.png set your cape (PNG, 64×32 / 128×64 / 256×128 / 512×256)
/ga custom name reset             remove your name (same for cape)
/ga custom status                 what you have set, and whether the server is reachable
```

- **Where it shows:**
  - The name replaces your username in **chat**, the **scoreboard sidebar**, the **tab list** and **nametags**, including names the server formats itself. Nametags include the ones servers draw with separate entities (text displays, named armor stands) above players, which is what you and others see above your head.
  - A rank in brackets right before the username is removed with it: `[DRAGON] qtTornado: hi` becomes `YourName: hi`. Without a rank only the username is replaced. Only the bracket directly before the name goes (`[120] [DRAGON] qtTornado` becomes `[120] YourName`).
  - Usernames match exactly and as whole words (`qtTornado` never matches inside `qtTornado2`), only for players currently in the tab list. Colors and click/hover actions around the name are kept.
  - In the **tab list**, the name is followed by your real username in gray, so nobody can impersonate someone.
  - Chat lines that arrived before someone's custom name was loaded stay as they were.
  - The cape also applies to your **elytra**.
  - Without a custom cape, or while one loads, your normal Minecraft cape shows.
- **Signing in:** changing your cosmetics needs a Microsoft-authenticated account. The mod proves who you are by signing with the chat key Mojang issues to your account. Your login token and private key never leave the game.
- **Privacy:**
  - Other players can't see your IP address; the mod never connects players to each other.
  - The mod sends the UUIDs of players in your tab list to the cosmetics server to look up their cosmetics. Only the server owner can see those requests, and their IPs, in Cloudflare's logs.
  - Cape images are downloaded from the cosmetics server, never from the link someone typed.
  - Names containing links, IP addresses, emails or phone numbers are hidden.
  - Turn it all off in **GolemAddons → Visuals → Cosmetics**. Details are in [worker/README.md](worker/README.md#who-can-see-what).
- **Performance:**
  - Lookups are batched (up to 100 players per request), cached for 10 minutes, and never run on the game thread.
  - Each cape image is loaded once and shared by every player using it.
  - Capes are cached on disk in `config/golemaddons/cape-cache` (20 MB max).
- **If the server is down,** everyone simply looks vanilla. After repeated errors the mod pauses for 5 minutes.
- `/ga` is a client command. If a server has its own `/ga`, this one takes over while the mod is installed.

**Server setup:** the cosmetics server is a free Cloudflare Worker that you deploy once. See [worker/README.md](worker/README.md). Put its URL in `gradle.properties` (`cosmetics_api_url=`) before building the jar you share. With no URL, cosmetics stay off.

## Auto Clicker

Open **GolemAddons → QoL → Auto Clicker**, click **Hold to click** and press the key or mouse button you want (Escape clears it; side mouse buttons work well). Set **Min** and **Max** CPS (1 to 20), then Save. In game, with no menu open, hold that key: it left-clicks at a random rate between the two values, picking a new random gap before every click.

Each click is handed to Minecraft exactly like a real mouse click, so it attacks, breaks blocks and swings the normal way, including the attack cooldown. Releasing the key stops it immediately. Clicks are timed per game tick (20 per second), so 20 CPS is the maximum.

## Camera

**GolemAddons → QoL → Camera**. Turn on **Camera tweaks**, then any of:

- **Skip front view**: F5 goes first person → behind you → first person, never the view facing you.
- **Custom distance**: the third-person camera sits this far away (1 to 12 blocks) instead of the game's own distance (4).
- **Clip through blocks**: in third person the camera goes through walls instead of being pulled in towards you.

Only your own view changes. Your position, rotation and everything sent to the server stay exactly as in vanilla.

## Terminals

**GolemAddons → QoL → Terminals**. Solves all six Floor 7 terminals, recognised by their chest title:
Click in Order, Correct All the Panes, What Starts With, Select All the Colour, Change All to Same Colour and Melody.

- **Compact GUI**: the chest is replaced by a clean grid of just the puzzle, with the answer lit up. In Click in Order the
  next three numbers are coloured, and Same Colour shows how many clicks each pane needs and with which button (L/R).
  Turn it off to keep the normal chest with the answer highlighted on it.
- **Block wrong clicks**: clicks that aren't part of the answer are never sent; the tile flashes red instead. In Same
  Colour either mouse button turns a pane the short way round.
- **Hover click**: clicks a correct tile as soon as your cursor rests on it, so you only sweep the cursor across the
  highlights. **Hover delay**, **Click delay** (least time between two clicks) and **First click** (no clicks right after
  the terminal opens) set its timing.
- **Auto terms** (marked with a red **!**): solves the terminal with no input, one correct click every **Click delay**
  after the **First click** delay. Servers may treat this as cheating; use it at your own risk.
- **Dim others**: in Starts With and Select All the items that aren't an answer are darkened and washed out; **Darken** and **Saturation** set how much.
- **Melody** only says *click now* while the click can still reach the server before the pane moves on: it times how long
  the pane stays in each column and measures your ping from earlier terminal clicks.
- **Look**: tile size, gap, and every colour (background, tile, the three highlight colours, blocked flash, text) with
  hue / saturation / brightness / opacity sliders or a hex code. The preview on the left is clickable.

Every click is an ordinary click on a slot. It is sent without vanilla's local guess at the result, so panes never
flicker or end up on your cursor, and a click the server never answers comes back as a hint after a moment.

**Practice:** `/gatestterms` (or the Practice button) runs every terminal in a random order, entirely on your own
game, with the same GUI, blocking and hover click. `/gatestterms <order|panes|starts_with|colors|rubix|melody>` runs
just one. Each click is answered after the **Test ping**, and chat shows your time and wrong clicks. Nothing is sent to
the server.

## Held item animations

**GolemAddons → Visuals → Animations** changes how the item in your hand looks in first person. It uses the same model as the Animations module of [OdinLegacy](https://github.com/odtheking/OdinLegacy) (see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)). The preview on the left shows your held item (a diamond sword if your hand is empty) exactly as the game will draw it; **Swing** plays a swing, **Loop** keeps swinging.

**Adjust:**
- **X / Y / Z** move the item relative to where it normally sits (0 = vanilla, 1 = twice as far right / down / forward).
- **Pitch / Yaw / Roll** rotate it. The swing happens after the rotation, so a tilted item swings along its tilt.
- **Size** scales it. **Speed** makes the swing faster or slower; it changes only your own swing animation, not attacks or the attack cooldown.
- **Swing: Full / Pivot**: Full is the normal swing. Pivot keeps the swing's rotation but not its forward movement (Odin's "No Swing").
- Drag a slider or use the arrow keys (Shift for bigger steps); double-click or right-click it to reset that value. **Reset all** sets everything back.

**Options:** **Scale swing** (the swing's movement follows the item's size), **Ignore haste** (Haste and Mining Fatigue don't change the swing speed), **No equip animation** (switching items doesn't lower and raise your hand).

**Presets:** type a name and press **Save new**. Click a preset to load it; **Overwrite**, **Rename** and **Delete** (click twice) act on the selected one. Up to 50 presets. **Copy to clipboard** turns the current values into a short text (`GA-ANIM1:…`) you can send to someone; **Paste from clipboard** loads one. Pasted text is checked and rejected if it isn't a valid preset.

Press **Save** to apply. With every value at its default the item looks exactly like vanilla. The animations only change rendering on your screen.

## Updates

Click the refresh button next to the version in the bottom-right of the menu to check now, and again to install a new version; right-click it for the settings below. GolemAddons also checks [this repository's GitHub releases](https://github.com/TornadoTheQT/GolemAddons/releases) when the game starts (turn off **Check on start** to stop that) and shows a toast when a newer version is out.

- **Install** downloads the new jar, checks it, and puts it in your mods folder. It's used from the **next start**. The running game is never changed.
- **Auto-update** (off by default) does the same by itself after each check.
- **Skip version** stops reminders and auto-update for that version. **Pre-releases** also offers test versions.

How an install is checked:
- It only downloads from `github.com/TornadoTheQT/GolemAddons` releases over HTTPS (redirects only to GitHub's own file servers). This is built into the mod and can't be changed by config, servers or chat.
- The file must match the size and **SHA-256 checksum** GitHub publishes for it, and the jar's own metadata must say it's GolemAddons at that version. If GitHub publishes no checksum, nothing is installed and you get the release page instead.
- The new jar waits as `golemaddons-<version>.jar.part` (which the game ignores) until the game closes. Then the old GolemAddons jar is removed and the new one takes its name, so there are never two copies. On Windows, where the running jar is locked, a small helper process finishes the swap right after the game exits.
- It only ever deletes the jar the running mod was loaded from, and only after checking that it really is GolemAddons. Installing is disabled if the mod isn't a jar directly in the game's `mods` folder (e.g. a development build).

## Dragon Party Finder

Open it with `/gapf` or **GolemAddons → Party Finder**. It shows the parties listed by GolemAddons players on the server you're on, and updates live.

**Browsing:**
- **All / GDrag / NDrag** tabs, a search box (party name, description, leader or member), **Open only**, and a sort order (newest, oldest, most or fewest players, most space).
- Each row shows the leader's head, the name and type, Open / Full / In progress, the size, and how many places are free. Click a row to show its members.
- The list scrolls smoothly and is paged (20 per page). It refreshes whenever you open the screen and when you press **Refresh**.

**Joining:** press **Join**. The leader gets a pop-up and a chat line with **[Accept] [Deny]** buttons, and their game makes a sound. Once they accept, their game runs `/p <your name>` for them and you get a pop-up telling you to accept the invite. Unanswered requests expire after a minute.

**Your party** (**Create party** / **My party**):
- Set the dragon type, name, description, size (1–6, including you), how many Stun / Melee / Term / Mage / Support places to reserve, and **Auto-accept**.
- When you lead a party, the right panel lists members (with **Kick**) and requests (✔ / ✖). **Status** marks it In progress, which hides Join for everyone. **Disband** asks you to click twice.
- Members of someone else's party see it there, with **Leave**.
- Your last party settings, filters and notification options (pop-ups, chat, sound, invite command) are saved in `config/golemaddons.json`.

**Commands:** `/gapf`, `/gapf accept <id>`, `/gapf deny <id>`, `/gapf leave`, `/gapf disband`, `/gapf refresh`.

**How it stays honest:**
- You sign in with the same Mojang chat-key proof as cosmetics, so nobody can list or join a party as you.
- A join request only ever asks for *you*. The leader's game looks you up by UUID in its own tab list and invites the name it finds there. Nobody can get a leader to invite someone else, or run anything but the invite command.
- Member counts, open slots and statuses are worked out by the server, not by clients.
- Limits: one party per leader, one pending request per player, a cooldown after a denial, a cap on waiting requests per party, and message rate limits.
- The leader's game sends at most one invite every 2.5 seconds, so a burst of accepts can't get them kicked for spam.
- A party disappears a minute after its leader disconnects, unless they come back. Members who leave the Minecraft server come off the listing after 90 seconds.

**Privacy:**
- Parties are grouped by a hash of the server address, so the GolemAddons server never learns which Minecraft server you play on.
- Other players see your username, UUID (for your head) and the party details you type. Names and descriptions can't contain links, IPs, emails or phone numbers.
- The connection is only open while a finder screen is open (plus 30 seconds), or while you're in, leading or asking to join a party.

**Server setup:** the party finder runs on the same Cloudflare Worker as cosmetics; after updating it, run `npx wrangler deploy` once (see [worker/README.md](worker/README.md#party-finder)). Until then the finder says the server doesn't have it yet. The invite command defaults to `p {name}`; change it under **My party → Options** if your server uses something else, e.g. `party invite {name}`.

## Project layout

```
com.golemaddons
  GolemAddonsClient        entrypoint: config, keybinds, feature registration
  config/                  GolemConfig (data), ConfigManager (JSON load/save), ConfigMigration
  autosell/
    RuleValidator, ItemMatcher, NameNormalizer, BoundedCharSequence   plain Java, unit tested
    InventoryScan          Minecraft stacks/slots -> matcher inputs, slot protection
    SellSession            tick-driven state machine for one sell run
    AutoSellFeature        keybind, sell-screen button, tick hook
  dragon/DragonCatalog     dragon types, pieces, extra drops -> exact-name rules (unit tested)
  autosell/QuickSellCommand  validates the quick-sell command (unit tested)
  ui/                      GolemHubScreen + HubCard + HubCanvas (animated menu, GUI-scale independent),
                           EditorScreen base (header, centered panels, auto-sized footer),
                           SellSettingsScreen, MiningAutoSellScreen, RuleEditScreen, DragonSellerScreen
  render/DragonHitboxRenderer  dragon-only hitbox outlines (vanilla gizmos)
  cosmetics/
    CosmeticName, CapeUrlRules, CapePng, Hashes, ProfileCache, ApiUrl   plain Java, unit tested
    CosmeticsApi           bounded HTTP client for the Worker
    CosmeticsFeature       discovery, batching, cache, sign-in, updates; render hooks
    CapeTextures           per-hash cape textures: disk cache, checks, off-thread decode, release
    CosmeticText, CosmeticsCommands   name Components and /ga custom
  mixin/                   container GUI accessor; DebugRenderer (dragon hitboxes);
                           AvatarRenderer (nametag + cape); PlayerTabOverlay (tab name)
  autoclick/                ClickScheduler (random CPS timing, unit tested), AutoClickerFeature (hold key -> clicks)
  partyfinder/
    PartyModels, PartyJson, PartyText, PartyQuery, InviteThrottle   plain Java, unit tested
    PartySocket            the WebSocket (size caps, one send in flight, heartbeats)
    PartyFinderFeature     connection lifecycle, live listings, invites via the tab list, notifications
    PartyFinderCommands    /gapf
  ui/PartyFinderScreen, MyPartyScreen, PartyUi   browse list, your party, chips and heads
  src/gametest/            in-game smoke test: `./gradlew runClientGameTest` opens every screen and saves screenshots
worker/                    Cloudflare Worker: cosmetics (KV) and the party finder (Durable Objects); own README and tests
```

**Adding a feature:**
1. Add a section to `GolemConfig`. If it renames or moves existing fields, bump `CURRENT_VERSION` and add a step to `ConfigMigration`.
2. Add a card for it in `GolemHubScreen.init()`, and give it a screen that extends `EditorScreen`.
3. If it sells items, return its rules from `AutoSellRules.from` so the existing sell button and safety checks cover it. Otherwise, create a sibling class to `AutoSellFeature` that registers its own events, and construct it in `GolemAddonsClient`.

Run `./gradlew test` for the validator, matcher, dragon catalog, migration, quick-sell command, exact-name fast-path and cosmetics validation/cache tests. Run `npm test` in `worker/` for the server's tests.

Exact-name rules (every Dragon Seller rule, or any `\Q...\E` rule without an item ID or case-insensitivity) are matched with a hash lookup instead of a regex. A full match of a quoted literal is string equality, so they match exactly the same names; a test checks this against the regex result.
