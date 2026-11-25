# GolemAddons: synced custom names and capes (Cloudflare Workers + KV), plus a Dragon's Nest travel scroll

## OBJECTIVE

Add a cosmetics system to the GolemAddons Fabric client mod (Minecraft 26.1.2). Every player running the mod sees every other mod user's **custom name** and **custom cape**, on any server, keyed by Minecraft account UUID. A small Cloudflare Worker backed by Workers KV stores the data on the free plan.

**Commands (client-side):**
- `/ga custom name <text>`: sets your name. `&` color/format codes are allowed, e.g. `&6&lZach`.
- `/ga custom cape <https URL>`: sets your cape from a PNG at that URL.
- `/ga custom name reset`, `/ga custom cape reset`, `/ga custom status`. These are small, obvious companions; implement them.

**Priorities, in order:**
1. Never crash, freeze or stutter Minecraft.
2. Performance: batched, async, cached, deduplicated, with no blocking or network/disk I/O on the render or client thread.
3. Security: no UUID spoofing, no SSRF, no hostile images.
4. Vanilla behavior preserved as the fallback.

**Separately:** add **Travel Scroll to Dragon's Nest** to the Dragon Seller's extra drops.

**Deliverables:** the complete client code, the Worker (TypeScript) with its KV schema, the deployment config, tests for both, and a `worker/README.md` setup guide written for someone who has never used Cloudflare.

## PROJECT CONTEXT

- **Mod:**
  - Fabric, Loom 1.19-SNAPSHOT, Minecraft **26.1.2**: unobfuscated, with Mojang names. Many classes were renamed from older versions, so never assume a pre-26.x name.
  - Fabric API 0.155.3+26.1.2 and Java 25. The mod version is in `gradle.properties` (currently 0.1.1).
  - JUnit 5 tests run in `./gradlew build`.
- **Existing structure** (read it; match its style):
  - `GolemAddonsClient`: entrypoint, keybind category, feature registration, `config()`.
  - `config/`:
    - `GolemConfig`: v2, with sections `selling`, `miningAutoSell`, `dragonSeller`, `render`. Each has `copy()` and `sanitize()`.
    - `ConfigManager`: atomic save, listeners, backup of unreadable files.
    - `ConfigMigration`.
  - `autosell/`: the regex safety layer (`RuleValidator`, `NameNormalizer`, `BoundedCharSequence`) and `AutoSellFeature` (a sibling feature that registers its own events). Use it as the pattern for a new `cosmetics/` feature.
  - `dragon/DragonCatalog`: plain Java, unit tested. It has an `Extra` enum of exact item names, and each item becomes a `Pattern.quote` rule.
  - `render/DragonHitboxRenderer` + `mixin/DebugRendererMixin`: an example of a small mixin delegating to a plain class.
  - `ui/`:
    - `GolemHubScreen` (cards).
    - `EditorScreen` base (header, panels, auto-sized footer).
    - `CheckRow` and `UiText` (colors).
- **Relevant 26.1.2 APIs found during inspection** (confirm signatures with `javap` on the merged jar in `~/.gradle/caches/fabric-loom/minecraftMaven/.../26.1.2/`; extract only into the scratchpad, never into the project):
  - `Minecraft.services()` returns `net.minecraft.server.Services`, which exposes the authlib `MinecraftSessionService` (`joinServer(UUID, String accessToken, String serverId)`).
  - `Minecraft.getUser()` returns `User` (`getProfileId()`, `getName()`, `getAccessToken()`).
  - `PlayerSkin` is a record (`body`, `cape`, `elytra` as `ClientAsset.Texture`, `model`, `secure`) with `with(PlayerSkin.Patch)`.
  - `PlayerInfo.getSkin()`, `PlayerInfo.getTabListDisplayName()` and `PlayerInfo.getProfile()`.
  - `PlayerTabOverlay.getNameForDisplay(PlayerInfo)` and `decorateName(...)`.
  - Player rendering uses `AvatarRenderState` and `layers/CapeLayer`.
  - `TextureManager` is at `Minecraft.getTextureManager()`.
  - Fabric's client commands are in `fabric-command-api-v2` (`ClientCommandRegistrationCallback`, `ClientCommandManager`).
- **Worker side:**
  - The repo has no Worker yet. Create `worker/` at the repo root as its own npm project (TypeScript, `wrangler`, `vitest` + `@cloudflare/vitest-pool-workers`).
  - Look up the current Wrangler config format (`wrangler.jsonc` is the current recommendation) and the current KV and Workers free-plan limits in Cloudflare's docs before writing. Don't rely on memory.

## PRE-IMPLEMENTATION INSPECTION

1. Read `GolemAddonsClient`, `AutoSellFeature`, `ConfigManager`, `GolemConfig`, `DragonCatalog`, `DragonSellerScreen`, `SellSettingsScreen`, `mixins.json`, `fabric.mod.json` and the lang file.
2. Find where the player name is turned into what the client shows, and pick the narrowest hook for each:
   - **Nametag:** inspect `AvatarRenderer` / `AvatarRenderState` extraction and where `nameTag` is set (it probably comes from `getDisplayName()` plus scoreboard team decoration).
   - **Tab list:** `PlayerTabOverlay.getNameForDisplay`.
   - Do **not** change `Player.getDisplayName()` globally, because that leaks into chat, commands and death messages.
3. Find how the cape texture reaches `CapeLayer` and the elytra layer. Most likely it goes through `PlayerInfo.getSkin()` → `AvatarRenderState.skin`. Decide whether to patch the `PlayerSkin` returned by `PlayerInfo.getSkin()` (via `PlayerSkin.with(Patch)`) or the render state.
   - Prefer the place where the original value is still available, so falling back is trivial.
4. Check how `ClientAsset.Texture` and `ClientAsset.ResourceTexture` are built from an `Identifier`. Check how to register a `DynamicTexture`/`NativeImage` with `TextureManager` in 26.1.2, including on which thread, how to release it, and whether `NativeImage.read(...)` is safe off the render thread.
5. Check how authlib's `MinecraftSessionService.joinServer` behaves in 26.1.2: its signature, its exceptions (`AuthenticationException`, `AuthenticationUnavailableException`, `InsufficientPrivilegesException`), and that it is blocking. It must run off-thread.
6. Check Fabric's client command API for 26.1.2 (registration callback, `ClientCommandManager.literal/argument`, `FabricClientCommandSource.sendFeedback/sendError`).
7. Check that `java.net.http.HttpClient` is usable. It is part of the JDK, so no new dependency is needed. Gson is already on the classpath via Minecraft.

## REQUIREMENTS

### A. Travel Scroll to Dragon's Nest (small, do first)

- Add `Extra.TRAVEL_SCROLL_DRAGONS_NEST("Travel Scroll to Dragon's Nest")` to `DragonCatalog`. It's matched as an exact name, like the other extras.
  - Use a straight apostrophe `'` unless the user confirms the server uses `’`.
  - Mention in the README that names are exact, so a different apostrophe means it won't match.
- **Fresh installs:** sold by default, consistent with the other extras.
- **Existing configs must not start selling it silently.** Change `DragonSeller.sanitize()` so a key missing from a *stored* map defaults to `false` (keep). Defaults apply only when there is no stored map at all, or on Defaults reset.
  - Rule: an update never begins selling something the user didn't choose.
  - Add a test for both paths (fresh = on, upgraded config = off).
- Update the `DragonCatalogTest` counts and any README item lists. The Dragon Seller screen picks it up automatically; confirm the extras panel still fits (its rows are the widest labels).

### B. Commands (client)

- Register `/ga` with Fabric's **client** command API, so it never reaches the server. If a server also has `/ga`, the client command wins. Document that.
- **`/ga custom name <text>`** (greedy string):
  - Validate locally with the same rules as the Worker (section E): it must parse and be within length limits.
  - Show a preview of the rendered name, then submit.
- **`/ga custom cape <url>`:** basic local URL validation (section E), then submit. The *Worker* fetches and validates the image; the client never fetches user-supplied URLs.
- **`reset`** variants delete the field.
- **`status`:** shows your current cosmetics, whether the API is configured and reachable, and the last error.
- Every command returns immediately, prints "Updating…", and reports the result asynchronously via chat on the client thread (`mc.execute`).
  - Chat feedback must never make a URL clickable: no `ClickEvent.OpenUrl`, no `Util.getPlatform().openUri`. Show URLs as plain text.
- A second update while one is in flight is refused with a clear message.

### C. Client sync

- **Config:**
  - A new `cosmetics` section with: `enabled` (default true), `apiBaseUrl` (default from a constant set at build time, see below), `showOthers` (default true), `showOwn` (default true).
  - Sanitize `apiBaseUrl`: https only, or `http://localhost` / `127.0.0.1` for `wrangler dev`. A blank value disables the feature.
  - No config version bump is needed for an added section, since `sanitize()` fills it.
  - Add a small **Cosmetics** panel to the hub, or to Settings if that fits better, with these toggles, the API URL and a status line.
- **Shared default URL:** every player must use the same Worker to see each other. Put the deployed URL in `gradle.properties` (e.g. `cosmetics_api_url=`) and expand it into a generated constant or `fabric.mod.json` custom value. The jar then ships with it, and a user override in config is still possible.
- **Discovery:**
  - Every 20 ticks, diff the current tab list (`ClientPacketListener.getOnlinePlayers()`) against a known-UUID set, and queue UUIDs that have no fresh cache entry.
  - Don't allocate per tick when nothing changed. Reuse collections.
  - Skip NPC/fake profiles: UUIDs whose version is not 4, which servers commonly use for NPCs, and anything the server hides.
  - Keep the rule in one place, with a comment.
- **Batching:**
  - Flush the queue at most once per second, or immediately at 100 UUIDs, as one `POST /v1/profiles/batch` with ≤ 100 UUIDs.
  - At most 2 requests in flight in total.
  - Deduplicate UUIDs that are queued or in flight.
- **Cache:**
  - A `ConcurrentHashMap<UUID, Entry>`.
  - Positive entries are fresh for 10 minutes. Negative entries ("no cosmetics") for 15 minutes. Failed lookups retry with exponential backoff (30 s → 10 min).
  - Cap at about 5,000 entries with LRU-ish eviction.
  - Render-thread reads must be a single map `get` plus a field read. Precompute the parsed name `Component` once per profile, never per frame.
- **Self:** after a successful update, replace your own entry with the server's response instead of waiting for the TTL.
- **Circuit breaker:** after 5 consecutive network failures, pause all requests for 5 minutes and show this in `status`. Never spam chat with network errors.
- **Lifecycle:** clear per-world state when disconnecting (`ClientPlayConnectionEvents.DISCONNECT`). Keep the UUID cache and disk cache.

### D. Rendering

- **Names:**
  - Replace only the *username part* of the nametag and tab-list name with the custom name.
  - Keep scoreboard team prefix/suffix and other decoration the server adds.
  - If the server provides a custom `tabListDisplayName`, leave it untouched, because the server's formatting wins.
  - Never show a nametag where vanilla wouldn't: team visibility, sneaking, invisibility and F1 all still apply.
- **Anti-impersonation:**
  - The real username must stay discoverable. In the tab list, render `CustomName` followed by the real name in dark gray, e.g. `CustomName (RealName)`, or provide it on hover.
  - The nametag shows the custom name only.
  - Document the choice.
- **Capes:**
  - When a profile has a cape hash, use it for both the cape and the elytra texture, via `PlayerSkin.with(...)`.
  - If there's no custom cape, the texture isn't loaded yet, or it failed, use the original `PlayerSkin` unchanged (official Mojang/Minecraft capes).
  - Optional: a "prefer official cape" toggle.
- **Textures:**
  - **Fetching:** fetch capes only from the Worker (`GET /v1/cape/<sha256>.png`), off-thread.
  - **Checks on the bytes, off-thread:**
    - SHA-256 of the bytes equals the hash in the URL.
    - The PNG signature is present.
    - IHDR is the first chunk, with width and height within limits (section E). Check this before decoding.
    - Size ≤ 256 KiB.
  - **Decode and upload:** decode with `NativeImage.read` off-thread if inspection shows that's safe; otherwise decode on the render thread under the same size limits. Register on the render thread via `mc.execute` as `golemaddons:capes/<sha256>`.
  - **Deduplicate by hash:** one texture per hash, however many players use it.
  - **Release:** a reference-counted or last-used LRU, capped at 64 textures, releases textures unused for 10 minutes. `close()` the `NativeImage`/texture on release.
  - **Disk cache:** `config/golemaddons/cape-cache/<sha256>.png`, capped at 20 MiB with LRU eviction. Re-verify the hash on load and delete the file on mismatch. Do all disk I/O off-thread.

### E. Validation rules (shared by client and Worker; keep them identical and tested on both sides)

- **Custom name:**
  - Raw input ≤ 64 chars. Visible text (codes removed) between 1 and 24 code points.
  - Allowed codes: `&0-9`, `&a-f`, `&k-o`, `&r` (case-insensitive). `&&` means a literal `&`; any other `&x` is invalid.
  - Reject:
    - `§`.
    - Control and format characters (Unicode `Cc`, `Cf`, which includes bidi overrides and zero-width characters).
    - Line and paragraph separators.
    - Unpaired surrogates.
    - Visible text that is only whitespace.
  - Trim leading and trailing whitespace.
  - Optional: a decision for `&k` (obfuscated), which hides the name. Allowing it is acceptable, but at least one visible non-obfuscated character must remain.
  - Store the raw `&` form. Clients build `Component`s directly from parsed runs; never round-trip through legacy `§` strings.
- **Cape URL (Worker is authoritative; the client does the cheap checks):**
  - `https:` only. Port 443 or none. No userinfo (`user:pass@`). Length ≤ 512.
  - The host must be a DNS name with a dot.
  - Reject:
    - IP literals in any form: IPv4, IPv6 `[...]`, and decimal, octal or hex integer hosts such as `2130706433` or `0x7f.1`.
    - `localhost`, and names ending in `.localhost`, `.local`, `.internal`, `.lan`, `.home.arpa`.
    - The Worker's own hostname.
- **Cape image:**
  - PNG signature.
  - IHDR first, with width:height = 2:1 and width ∈ {64, 128, 256, 512}. Optionally also accept 22×17 / 46×22 legacy cape-only images if inspection shows the renderer can use them; otherwise reject them with a clear message.
  - Bit depth and color type valid per the PNG spec.
  - Compressed size ≤ 256 KiB.
  - Bounding the IHDR dimensions bounds the decoded size, which is the decompression-bomb defence. Also reject files whose IDAT total is implausibly small or large for the dimensions, if that's cheap.

### F. Worker API (`worker/`)

All JSON, versioned under `/v1`. CORS isn't needed (no browser clients); return no CORS headers.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/v1/health` | none | `{ok:true, version}` |
| POST | `/v1/auth/challenge` | none | body `{uuid, username}` → `{nonce, expiresAt}` |
| POST | `/v1/auth/verify` | none | body `{username, nonce}` → `{token, uuid, expiresAt}` |
| POST | `/v1/profiles/batch` | none | body `{uuids:[≤100]}` → `{profiles:{uuid:{name?, cape?}}}`; UUIDs without cosmetics are omitted |
| PUT | `/v1/profile` | Bearer | body `{name?: string|null, capeUrl?: string|null}`; `null` clears the field → the new profile |
| GET | `/v1/cape/:sha256.png` | none | PNG bytes, `Cache-Control: public, max-age=31536000, immutable` |
| DELETE | `/v1/admin/profile/:uuid` | `ADMIN_SECRET` header | moderation removal, constant-time compare |

- **KV schema** (one namespace, binding `COSMETICS`):
  - `p:<uuid-no-dashes>` → `{"v":1,"name":"&6Zach"|null,"cape":"<sha256>"|null,"updatedAt":<ms>}`
  - `c:<sha256>` → raw PNG bytes (`arrayBuffer` put), with metadata `{w,h,size}`.
  - Identical capes are stored once. Unreferenced capes may remain: they are tiny, so document this rather than building refcounting. An optional scheduled cleanup is out of scope unless trivial.
- **Reads, minimising KV usage (state the free-plan limits in the README):**
  - For each UUID in a batch, first check the Workers **Cache API** (`caches.default`) using a synthetic per-UUID cache key, e.g. `https://cache.internal/v1/p/<uuid>`. Use a 60 s TTL for found profiles and 300 s for absent ones.
  - Then `KV.get` the misses in parallel (`Promise.all`), and populate the cache.
  - Capes: Cache API first, then KV. Capes are immutable, so cache them for a long time.
- **Writes:**
  - Purge the per-UUID cache key after a write in the colo that handled it. Note in the README that other colos and KV's eventual consistency mean other players can take up to about a minute to see changes.
  - Rate limit: at most one profile update per UUID per 30 s, using `updatedAt` in the stored profile (no extra writes). Return 429 with `Retry-After`.
  - Writing the same cape again is idempotent: skip the `c:` put if the key already exists.
- **Input limits:**
  - Reject bodies over 4 KiB (check `Content-Length`, then cap the read).
  - Reject non-JSON, unknown fields, wrong types, and malformed UUIDs (32 hex or dashed 36 chars, normalised to lowercase without dashes).
  - Return generic error messages: no stack traces, no echo of secrets.

## SECURITY REQUIREMENTS

### Authentication: no UUID spoofing; the access token never leaves the Mojang flow

- Use the Mojang session-server "join/hasJoined" proof, the same mechanism servers use:
  1. The client calls `/v1/auth/challenge` with its UUID and username.
     - The Worker returns a **stateless** nonce: `base64url(random 16 bytes ‖ expiry ‖ uuid) + "." + HMAC-SHA256(TOKEN_SECRET, …)`, expiring in 60 s.
     - Stateless means no KV writes for nonces.
  2. The client computes `serverId = sha1Hex("golemaddons-cosmetics:" + nonce)`, using Minecraft's signed two's-complement hex digest form (check how authlib/vanilla compute it). Off-thread, it calls `sessionService.joinServer(user.getProfileId(), user.getAccessToken(), serverId)`.
  3. The client calls `/v1/auth/verify` with its username and the nonce. The Worker:
     - Checks the HMAC and expiry with a constant-time comparison.
     - Recomputes `serverId`.
     - Calls `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=<urlencoded>&serverId=<id>` with a 5 s timeout.
     - Requires HTTP 200, and that the returned `id` equals the UUID bound into the nonce.
     - Issues a token: HMAC-signed `{uuid, exp: now+1h}`.
  4. `PUT /v1/profile` trusts **only** the UUID inside a valid, unexpired token. It never trusts a UUID in the body.
- The client caches the token in memory only, never on disk, and re-authenticates when it expires or on 401.
- The Minecraft access token is passed **only** to authlib's `joinServer`. It is never sent to the Worker, never logged, never written to disk.
- Offline-mode or cracked sessions can't authenticate. Show a clear "a Microsoft-authenticated account is required" message.
- Treat Mojang 204 / empty / 403 / 5xx as an auth failure. Don't hammer: once a challenge fails, the client waits ≥ 30 s before trying again.
- Secrets live only in Worker secrets (`wrangler secret put TOKEN_SECRET`, `ADMIN_SECRET`): never in `wrangler.jsonc`, the repo or the mod jar.
  - The Worker refuses to start handling auth if `TOKEN_SECRET` is missing or shorter than 32 bytes. Return 500 with a generic message, and log server-side.

### Cape URL fetching (Worker only): SSRF, redirects, size, time

- Validate the URL (section E) **before every hop**.
- Use `fetch(url, {redirect: "manual", signal: AbortSignal.timeout(5000), headers: {"User-Agent": "golemaddons-cosmetics/<version>"}})`. Follow at most 3 redirects manually, resolving `Location` relative to the current URL and re-validating each one.
- Reject:
  - Non-200 final statuses.
  - A `Content-Type` that's present and isn't `image/png`. Absent is allowed; the signature is checked anyway.
  - A `Content-Length` over 256 KiB.
- Stream the body with a byte counter and abort once it passes 256 KiB, even if `Content-Length` lied.
- Validate the PNG (section E), compute SHA-256 with `crypto.subtle`, and store it.
- **Document the limitation honestly:** Workers can't control DNS resolution, so DNS rebinding to internal IPs can't be fully excluded by hostname checks. However, Cloudflare's Worker runtime has no access to your private network, which limits the impact.

### Client hardening

- The client only talks to the configured API base URL and to Mojang via authlib. It never fetches a user-supplied URL, and never opens a browser for anything in this feature.
- Response bodies are capped: 256 KiB for batch JSON, 256 KiB for capes. Parse with Gson into fixed DTOs; ignore unknown fields.
- Re-validate every name and hash received from the Worker. Assume the Worker or the network could be compromised: a bad entry is treated as "no cosmetics".
- Every task catches `Throwable` at its boundary and logs once (rate-limited), never per frame. Nothing propagates into render or tick code.
- Use HTTPS only to the API, except for an explicit localhost dev URL. Use the JDK's default TLS validation and never disable it.

### Abuse

- Names are user-generated content visible to everyone. Provide the admin delete endpoint, and a client toggle to hide others' cosmetics.
- Optional: a small denylist in the Worker, kept configurable rather than hard-coded slurs. A client-side "hide this player's cosmetics" list (`/ga custom hide <player>`) is a reasonable optional extra; skip it if time is short and say so.

## PERFORMANCE REQUIREMENTS

- **Threads:**
  - The render and client threads do no network, disk, hashing, PNG decoding or JSON work.
  - The client uses one shared `HttpClient`, with HTTP/2, a connect timeout of 5 s and a per-request timeout of 8 s, and its own small daemon executor (2 threads, named `GolemAddons-Cosmetics-*`).
  - No `CompletableFuture.join()` or `get()` on game threads.
- **Hot-path lookups** (nametag, tab list, skin) must be O(1), allocation-free on cache hits, and must not take locks. Use immutable entry objects swapped atomically.
- **Requests:**
  - Batching and dedup mean that joining a server with 100 players costs at most 1–2 HTTP requests, and staying there costs about one request per 10 minutes per new player.
  - Capes are fetched once per hash per session. After the first time, the disk cache means zero network requests.
- **Worker:** batch lookups run in parallel and use the Cache API before KV.
- **Measure:** add debug-level timing logs for batch round trips and texture uploads, off by default. Confirm in a dev client that frame time is unaffected with cosmetics on and off (F3 frame graph, or a quick test with many fake entries).

## UI/UX REQUIREMENTS

- Command feedback uses the existing chat prefix style (`[GolemAddons]`) and clear, short messages, e.g.:
  - "Name updated."
  - "Cape rejected: image must be a PNG 64×32 to 512×256 (2:1)."
  - "Can't reach the cosmetics server; try again later."
- Brigadier suggestions: `reset` for name and cape, and a hint showing the code syntax.
- **Cosmetics panel:** an `EditorScreen` with the toggles, the API URL field (validated, with a status line), and the current name preview and cape status. Follow the existing panel look; nothing decorative.
- The tab-list real-name suffix is dark gray and unobtrusive.

## EDGE CASES

- No API URL configured, or the API down or slow: everything falls back to vanilla silently, and `status` explains why.
- The player isn't in the tab list (hidden by the server), or there are NPCs with random UUIDs: no lookups, or the queries are cheap and negative.
- Same UUID across servers: the cache is keyed by UUID only, so it's reused.
- Rapid server hopping: in-flight results for an old world are still cached, never applied to a stale object.
- Cape hash present but the image download fails: fall back to the vanilla cape and back off retries for that hash.
- The user changes cape repeatedly: the rate limit returns 429, and the client shows the retry time.
- Two players share the same cape: one texture.
- Resource reload (F3+T): dynamic textures survive, or are re-registered. Check what `TextureManager` does on reload.
- Token expires mid-update: one transparent re-auth, then fail with a clear message.
- The Worker returns garbage, oversized JSON, or a name that violates the rules: ignored, logged once.
- Name with only codes and no visible text, or visible text over 24: rejected with a specific message.
- Disk cache directory unwritable: keep working with the memory cache only.
- Existing config without a `cosmetics` section: defaults apply.
- Existing Dragon Seller config: the travel scroll stays unchecked.

## VERIFICATION

- **Java unit tests (plain Java, no Minecraft classes):**
  - The name parser/validator: valid codes, `&&`, invalid codes, `§`, bidi/zero-width characters, surrogates, length limits, whitespace-only, and `&k`-only.
  - The URL validator: IP forms including decimal, octal and hex, IPv6, ports, userinfo, the localhost family, schemes and length.
  - The PNG header validator: signature, IHDR position, dimensions, a truncated file, and an oversized file.
  - The `serverId` digest against known vectors (vanilla's own test strings, e.g. "Notch" → `4ed1f46bbe04bc756bcb17c0c7ce3e4632f06a48`, "jeb_" → `-7c9d5b0044c130109a5d7b5fb5c317c02b4e28c1`, "simon" → `88e16a1019277b15d58faf0541e11910eb756f6`).
  - The cache's TTL, negative-entry and backoff logic with an injectable clock.
  - Batching and dedup with a fake transport.
  - The Dragon Seller migration behavior for the new extra.
- **Worker tests** (vitest + `@cloudflare/vitest-pool-workers`, with `fetch` to Mojang and image hosts mocked):
  - Challenge/verify happy path.
  - Tampered nonce, expired nonce, nonce for another UUID, and Mojang returning another `id` or 204.
  - PUT without a token, with a forged or expired token, and with a body UUID that differs from the token's (ignored).
  - The rate limit.
  - The batch endpoint: 101 UUIDs rejected, malformed UUIDs rejected, missing profiles omitted.
  - Cape fetch:
    - Redirect to an IP literal, redirect to `localhost`, more than 3 redirects.
    - Non-PNG, wrong dimensions, and oversized both with and without `Content-Length`.
    - Timeout.
    - The same cape stored twice → one `c:` key.
  - Oversized bodies, wrong types, unknown fields.
  - Admin delete with a wrong secret.
- **Manual (state clearly what was and wasn't done):**
  1. `wrangler dev` with the client pointed at `http://localhost:8787`.
  2. Set a name and cape in a dev client (a real Microsoft account is needed for auth; if one isn't available, verify the flow up to `joinServer` and say so).
  3. Check that the nametag, tab list, cape and elytra all show it.
  4. Check that turning cosmetics off restores vanilla.
  5. Kill the Worker mid-session and confirm there's no freeze or crash.
- Run `./gradlew build` and `npm test` / `npx tsc --noEmit` in `worker/`. All must pass.

## COMPLETION CRITERIA

- The travel scroll is in the Dragon Seller. It's on for fresh configs and off for upgraded ones, with tests.
- All the `/ga custom …` commands work and give asynchronous feedback.
- Names and capes sync between two clients through the deployed or dev Worker, and fall back to vanilla on any failure.
- Updates require a Mojang-verified session token. The Worker never sees the access token, and no secret is in the repo or the jar.
- All validation rules are implemented identically on both sides, with negative tests.
- There is no blocking work on game threads, lookups are batched and deduplicated, and textures are deduplicated and released.
- `worker/README.md` explains setup, deployment, secrets, the free-plan limits, the eventual-consistency delay, moderation, and how to bake the URL into the mod.
- The main README documents the commands and privacy: which UUIDs are sent where, and the toggle to turn it off.

## FINAL REVIEW

1. Does the result satisfy every part of the request, including the travel scroll and the setup guide?
2. Does it follow the existing feature, config, mixin and UI patterns?
3. Is the UUID taken only from a verified token? Is the access token confined to authlib?
4. Is every external input (command args, Worker JSON, image bytes, URLs, redirects) validated at the trusted boundary?
5. Are the secrets only in Worker secrets?
6. Are SSRF, redirect, size, timeout and decompression-bomb defences present and tested? Are their limits documented honestly?
7. Can any network, disk or decode path block, throw into, or allocate per frame on the render or client thread?
8. Are textures released, and are caches bounded?
9. Is anything shared between threads mutated unsafely?
10. Are there no new dependencies beyond the Worker's dev tooling?
11. Are vanilla names, capes, server formatting and nametag visibility preserved when cosmetics are absent, off or broken?
12. Do the builds and tests (Gradle, vitest, tsc) all pass?
13. Are there no placeholder URLs or secrets, no debug output, and no leftover scratch files in the repo?

## FINAL INSTRUCTION

> Inspect the relevant existing implementation before making changes. Do not assume architecture, APIs, dependencies, schemas, or versions that can be determined from the project. Implement the complete solution rather than a superficial patch. Preserve working behavior unless changing it is necessary for this task. Verify security-sensitive behavior at the trusted boundary, test realistic failure and edge cases, run the project's available validation tools, and review the final diff for regressions, unnecessary complexity, security issues, stale APIs, placeholders, debug artifacts, and unfinished work before considering the task complete. Do not claim functionality has been verified unless you actually verified it.
