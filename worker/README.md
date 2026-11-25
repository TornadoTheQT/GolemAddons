# GolemAddons server (Cloudflare Worker + KV + Durable Objects)

This small server stores custom names and capes for GolemAddons players, and runs the live Dragon Party Finder. Every copy of the mod talks to the same server, which is how players see each other's cosmetics. It runs on Cloudflare's free Workers plan.

You set it up **once**, put its URL into the mod, and share that jar.

## What you need

- A free Cloudflare account: https://dash.cloudflare.com/sign-up. No card is needed for the free Workers plan.
- Node.js LTS: https://nodejs.org. Check it works with `node --version`.

## First-time setup

Run these in PowerShell from this `worker` folder:

1. **Install the tools:**
   ```powershell
   npm install
   ```
   npm 11 asks before running install scripts. This project already allows the two it needs, `esbuild` and `workerd`, in `package.json`.

2. **Open Workers once in the dashboard.** In the Cloudflare dashboard, open **Workers & Pages**. This picks your free `<something>.workers.dev` subdomain.

3. **Log in from the command line.** This opens your browser; approve it there.
   ```powershell
   npx wrangler login
   ```

4. **Create the storage** (a KV namespace):
   ```powershell
   npx wrangler kv namespace create COSMETICS
   ```
   It prints an `id`. Open `wrangler.jsonc` and replace `PASTE_YOUR_KV_NAMESPACE_ID_HERE` with it. The id isn't secret.

5. **Deploy:**
   ```powershell
   npx wrangler deploy
   ```
   It prints your URL, e.g. `https://golemaddons-cosmetics.<you>.workers.dev`. Until step 6 is done, sign-in answers `server_misconfigured`, so nobody can set cosmetics yet.

6. **Set the signing secret.** It signs sign-in tokens. The name of the secret is `TOKEN_SECRET`; its value is a random string that you never need to see. This command generates one and hands it straight to Cloudflare, without printing it:
   ```powershell
   node -e "process.stdout.write(require('crypto').randomBytes(32).toString('hex'))" | npx wrangler secret put TOKEN_SECRET
   ```
   Optional: the moderation endpoint below needs `ADMIN_SECRET`, and you need to know its value to use it. This sets it and prints it once; save it in your password manager:
   ```powershell
   $admin = node -e "process.stdout.write(require('crypto').randomBytes(32).toString('hex'))"; $admin | npx wrangler secret put ADMIN_SECRET; $admin
   ```
   Piping the value in like this also works in terminals where Wrangler can't show its "enter a secret value" prompt (the "cannot be run in a non-interactive context" error).

   Never put secrets in `wrangler.jsonc`, in the mod, or in git. If a secret leaks, run the same command again for a new value. Everyone then just signs in again automatically.

7. **Check that it's up.** Open `https://golemaddons-cosmetics.<you>.workers.dev/v1/health` in a browser. You should see `{"ok":true,"version":"1"}`.

8. **Build the URL into the mod.** In the repo root's `gradle.properties`, set:
   ```properties
   cosmetics_api_url=https://golemaddons-cosmetics.<you>.workers.dev
   ```
   Then rebuild the mod (`./gradlew build`) and share the jar. A player can also point their own copy at another server in **GolemAddons menu → Cosmetics**, but they only see players who use the same server.

**Updating the server later:** run `npx wrangler deploy` again. Stored data and secrets are kept.

## Party finder

The party finder lives in the same Worker. Its parties are kept in a **Durable Object** (`PartyRoom`), one per Minecraft server, with players connected over WebSockets. Durable Objects that use SQLite storage are included in the free plan, and `wrangler.jsonc` already declares the binding and the migration that creates them.

**Turning it on (or updating):** from this `worker` folder, run:
```powershell
npx wrangler deploy
```
The first deploy after this update prints that it's applying migration `v1-party-finder`. No new secrets are needed: it signs in with the same `TOKEN_SECRET` tokens as cosmetics. The mod jar doesn't need rebuilding for the server side, but players need the new jar to see the finder.

**How it works:**
- `GET /v1/pf/ws?server=<hash>&name=<username>` with `Authorization: Bearer <token>` upgrades to a WebSocket.
- The Worker checks the token, then passes only the verified UUID to the room named by `server`. The mod hashes the Minecraft server's address, so this server never sees it.
- Rooms use the WebSocket Hibernation API: idle connections cost nothing, heartbeats (`ping` → `pong`) are answered without waking the room, and an alarm closes connections whose heartbeat stopped.
- The room's state is saved to its storage after every change, so it survives restarts.

**Messages** (JSON text frames; the client may add a numeric `ref`, which replies echo):

| From the mod | Meaning |
|---|---|
| `list` | Get every party, plus your incoming and outgoing requests |
| `create` / `edit` | `type` (`gdrag`/`ndrag`), `name` (≤32), `desc` (≤120), `max` (1–6, including the leader), `roles` (`{stun,melee,term,mage,support}`, each 0–5, total ≤ max−1), `autoAccept` |
| `status` | `{inProgress}` |
| `disband`, `leave`, `cancel` | As named |
| `join` | `{party, role?}`; asks as yourself only |
| `respond` | Leader: `{request, accept}` |
| `kick` | Leader: `{uuid, reason?}` where reason is `kicked`, `not_online` (invite failed) or `offline` |

The server sends `parties`, `upsert`, `remove`, `request`, `request_gone`, `invite` (to the leader, who then runs the invite command), `result` (to the player: accepted, denied, expired, full, kicked, disbanded…), `left`, `ok` and `error` with a code.

**Rules the server enforces** (so a modified mod gains nothing):
- One party per leader, one pending request per player, and nobody can be in two parties.
- At most 8 waiting requests per party, a 30-second cooldown after a denial or expiry, and requests expire after 60 seconds.
- 20-message bursts refilling at 2 per second, 10 creates/edits per minute, 8 joins per minute, 12 connections per minute per account, one connection per account (a new one replaces the old), 400 connections and 150 parties per server, and 2 KB per message.
- Sizes, open slots and statuses are computed by the server, never taken from clients.
- Text is trimmed and rejected if it has control characters, `§`, links, IPs, emails or phone numbers.
- A party is removed 60 seconds after its leader disconnects, unless they reconnect.

## Who can see what

- **Other players** see only custom names and capes. Players never connect to each other, only to this server, so no player can see another player's IP address. Cape links are downloaded by this server, so whoever hosts a cape image never sees players' IPs either.
- **You, the server owner,** are the only person who can see anything sensitive:
  - **Request logs:** they're on (`"observability": { "enabled": true }` in `wrangler.jsonc`), so the dashboard shows each request with the player's IP address.
  - **Batch lookups:** the requests also show which players were looked up together, i.e. who was in the same tab list.
  - **Turning it off:** to stop collecting this, set it to `false` and run `npx wrangler deploy`.
- **Cloudflare** carries the connections, so it sees IPs like any website host.
- **What's stored per player:** only their custom name, the hash of their cape image, and the time of their last change. No IPs, usernames or cape links.
- **Party finder:** everyone in the finder on the same Minecraft server sees each listed party's name, description, size, roles, and the usernames and UUIDs of its leader and members. A room keeps only its current parties and pending requests; they're gone when disbanded or abandoned. Request logs (if on) also show party finder connections and their IPs, to you only.

## Free plan limits (check https://developers.cloudflare.com/workers/platform/limits/ and https://developers.cloudflare.com/kv/platform/limits/ for current numbers)

| Resource | Free per day | What uses it |
|---|---|---|
| Worker requests | 100,000 | A mod user makes about one lookup request per server join, plus one per new player every 10 minutes, and one per cape image the first time they see it. Each party finder connection counts once |
| Durable Object requests | 100,000 | One per connection, plus one per 20 party finder messages. Heartbeats are free |
| Durable Object storage writes | 100,000 rows | One per party change (create, join, leave, edit) |
| KV reads | 100,000 | Only on a cache miss: profiles are cached in memory and in Cloudflare's cache for 1 to 5 minutes |
| KV writes | 1,000 | One per name or cape change, plus one per new cape image |
| KV storage | 1 GB | A profile is under 200 bytes; a cape image is at most 256 KB |

A group of friends, or even a few hundred players, stays far below this. If you ever exceed a limit, Cloudflare returns errors for the rest of the day. The mod then quietly shows vanilla names and capes and retries later.

**Delay:** KV is eventually consistent, so a change can take up to about a minute to show for other players.

**Cache API on workers.dev:** Cloudflare's edge cache only applies to Workers on your own domain. On `*.workers.dev` the server still caches in memory, which is plenty at this scale. To add a custom domain, go to the dashboard → your Worker → Settings → Domains & Routes.

## API

Everything is JSON under `/v1`.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/v1/health` | none | `{ok, version}` |
| POST | `/v1/profiles/batch` | none | `{uuids:[≤100]}` → `{profiles:{uuid:{name?,cape?}}}` |
| POST | `/v1/auth/challenge` | none | `{uuid}` → `{nonce, expiresAt}` |
| POST | `/v1/auth/verify` | none | `{nonce, publicKey, keyExpiresAt, keySignature, signature}` → `{token, uuid, expiresAt}` |
| PUT | `/v1/profile` | `Authorization: Bearer <token>` | `{name?, capeUrl?}`; `null` clears a field |
| GET | `/v1/cape/<sha256>.png` | none | The PNG, cached forever |
| DELETE | `/v1/admin/profile/<uuid>` | `X-Admin-Secret` | Remove someone's name and cape |
| GET | `/v1/pf/ws?server=&name=` | `Authorization: Bearer <token>` | Party finder WebSocket (see above) |

**Storage (KV):**
- `p:<uuid>` holds `{"v":1,"name":"&6Zach"|null,"cape":"<sha256>"|null,"updatedAt":ms}`.
- `c:<sha256>` holds the PNG bytes. An image is stored once, however many players use it.
- Capes nobody uses any more are not deleted. They're small; delete them in the dashboard (Storage & Databases → KV) if you ever need to.

## Security

### Nobody can change someone else's cosmetics

Sign-in uses the chat key that Mojang gives every Microsoft-authenticated account. Mojang signs a certificate that binds that key to the account's UUID; only the player's own game has the private half.
1. The server gives the mod a signed one-minute nonce for its UUID.
2. The mod signs the nonce with its chat private key, and sends the public key, its expiry and Mojang's certificate signature.
3. The server checks Mojang's signature using Mojang's public keys (built into `src/mojangKeys.ts`), checks it's for the same UUID and not expired, and checks the player's signature over the nonce.
4. Only then does the server issue a one-hour token. Updates use only the UUID inside that token.

The server never contacts Mojang; Mojang refuses requests from Cloudflare's network, so the older "hasJoined" check can't work here. The player's access token and private key never leave the game. Offline-mode (cracked) accounts have no chat key, so they can't sign in.

**If Mojang rotates its keys** (rare), sign-in starts failing with `bad_certificate`. Fetch https://api.minecraftservices.com/publickeys in a browser, copy the `playerCertificateKeys` → `publicKey` values into `src/mojangKeys.ts`, and run `npx wrangler deploy`.

### Cape URLs are fetched by the server, never by players

When someone sets a cape:
- **The URL must be safe:** `https`, port 443, no IP addresses (including tricks like `0x7f.1` or `2130706433`), no `localhost`/`.local`/`.internal` names, and no credentials in the URL.
- **Redirects:** at most 3, each one checked again.
- **Time and size:** the whole download gets 5 seconds and at most 256 KB, even if the server lies about the size.
- **The image must be a cape:** a real PNG, 2:1, 64×32 up to 512×256.

Players then download only from this server, by content hash, and the mod checks the hash and header again. So a malicious link can't see players' IP addresses, and can't send them anything but a checked cape.

**Limitation:** a Worker can't control DNS. A public hostname that resolves to a private IP can't be fully excluded by name checks. Workers run on Cloudflare's network with no route into your home network, which limits the impact.

### Names are checked on both ends

- Only `&` color/format codes are allowed, at most 24 visible characters.
- No control, bidi or zero-width characters, and no `§`.
- The mod hides any name containing a link, IP address, email or phone number when showing it, so other players can't use names to spread personal info.
- The tab list shows the real username in gray next to a custom name, and hovering a custom name in chat shows the real username.

### Abuse limits

- One update per player every 30 seconds.
- 10 sign-in attempts per minute per IP (per server instance).
- Request bodies are limited to 4 KB.
- Known gap: there's no daily cap per account, so one account changing its cosmetics nonstop could use up the free plan's 1,000 daily writes. Nobody could change cosmetics for the rest of that day; existing ones keep working.

### Moderation

If someone sets an offensive name or cape, remove it (requires `ADMIN_SECRET`):
```powershell
curl.exe -X DELETE -H "X-Admin-Secret: <your admin secret>" https://golemaddons-cosmetics.<you>.workers.dev/v1/admin/profile/<their-uuid>
```
They can set a new one afterwards. To block someone permanently, you'd need to add a denylist to `src/api.ts`.

## Development

```powershell
npm test
```
```powershell
npm run typecheck
```
To run the server on your PC:
1. Copy `.dev.vars.example` to `.dev.vars` and fill in random secrets.
2. Run `npm run dev` (serves `http://localhost:8787`).
3. In the mod, set the Cosmetics server to `http://localhost:8787` (plain http is allowed for localhost only).

The tests run in Node with a fake KV, cache and `fetch` (image hosts are mocked, and a test key pair stands in for Mojang), and fake Durable Object sockets and storage for the party finder (`test/party.test.ts`, `test/partyRoom.test.ts`). The Worker uses only standard web APIs. `src/index.ts` must contain only entry points (the default export and the `PartyRoom` class), because the Workers runtime treats every named export of the main module as one.

Party finder text rules are duplicated in the mod (`PartyText`), so keep `src/party.ts` `cleanText` in sync with it.

Validation rules are duplicated in the mod (`src/main/java/com/golemaddons/cosmetics/`), so keep `src/validate.ts` in sync with `CosmeticName`, `CapeUrlRules`, `CapePng` and `Hashes`.
