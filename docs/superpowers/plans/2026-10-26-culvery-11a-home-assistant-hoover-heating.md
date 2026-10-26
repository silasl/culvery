# Culvery — Plan 11a: Home Assistant, the hoover and the heating link Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–9 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 10 is the controller. Task 11 is the controller on the `culvery-5` AVD with the user and the household's Home Assistant. Tasks 12–13 need the user and the son's SM-T510.

**Goal:** Culvery links to the household's Home Assistant with a token (behind a fresh Admin PIN), an Adult or Admin starts the Eufy hoover from a One-tap tray on Home with a fresh PIN, and Culvery reads and sets the Nest thermostat's Eco preset (no screen of its own yet; 11b's Holiday mode will call it).

**Architecture:**
- **`:core:homeassistant`** (new) is the one link both devices share: the saved address and token (`HomeAssistantSettings`), the address rule, a plain-JVM JSON reader on kotlinx.serialization (`HaJson`), `HomeAssistantClient` (OkHttp REST with the bearer header and the WebSocket API), `HomeAssistant` (the one client, the entity cache, the problem, and the socket while Culvery is in front and a screen watches some entities), the `HomeAssistantDevice` seam for Settings' pickers, and Settings › Other integrations.
- **`:capability:hoover`** (new): the `HooverSystem` seam, the chosen device (`hoover_settings`), the permission `hoover.start`, the words, and a capability with no tab whose only contribution is the Hoover row in the One-tap tray.
- **`:capability:heating`** (new): the `HeatingSystem` seam and the chosen device (`heating_settings`). No `Capability` in 11a.
- **`:provider:hoover-homeassistant`** and **`:provider:heating-homeassistant`** (new): each maps its entity to its seam over `HomeAssistant`, and binds a `HomeAssistantDevice` for the Settings picker.
- **Home** gains the One-tap tray: capabilities contribute `TrayButton`s (`:core:plugin`); the shell draws them in `HhTrayColumn` in the utility row, 333 dp wide, beside Shopping or at the row's left.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2, OkHttp 4.12.0 (REST and `OkHttpClient.newWebSocket`; MockWebServer 4.12.0 with `withWebSocketUpgrade`), kotlinx-serialization-json 1.9.0 (`Json.parseToJsonElement`, `buildJsonObject`); JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-26-culvery-11-hoover-heating-holiday-design.md`: §3, §4 whole, the 11a rows of §6, the 11a parts of §7 and §8 (§8.1 without the clock offset, §8.2.2, §8.2.3, §8.3's 11a line). **Not this plan:** §5 (Holiday mode, 11b), `:core:house`, renaming Holiday lights, the Away check §8.2.1 (it runs between 11a and 11b). The drawing is the handoff `docs/design/2026-10-redesign/handoff.md`, "One-tap tray (note 13)" and "Right column"; where the spec and the handoff disagree, the spec wins.

**Previous plans (format, shared parts):** `docs/superpowers/plans/2026-10-23-culvery-10-shopping-list.md` (the house format, the utility row, the image routine, an Admin PIN to connect), `docs/superpowers/plans/2026-10-16-culvery-07-hue.md` (a provider on the home network, the debug capture, Settings › Lights, a fresh-PIN permission).

**Builds on:** branch `plan-11-home-hub` in the one checkout `C:\Users\si\code\siland\culvery`, cut from `main` at a7733590. No worktrees. Never push.

## Global Constraints

**From the spec, verbatim:**
- §2.1 "**Hoover:** a Start button only. No stop, no "send home", no rooms or scenes from the wall."
- §2.9 "**Nothing existing moves** to Home Assistant: Hue and Sonos stay direct, Google Calendar and Tasks stay direct." Hue and Sonos code is read-only for this plan.
- §4.1 the page is "**Settings › Other integrations** (admin-only, after Settings › Lights, order 480)"; the token is a "**long-lived access token**" of "a **separate, non-admin Home Assistant user named Culvery**"; "Culvery never sees a Home Assistant password." "**Saving a new address or token asks for a fresh Admin PIN** (as connecting Google does)". "The help text also says to give the NAS a fixed address on the router."
- §4.1 "**Check** calls `GET /api/` with the token; then the page lists the `vacuum.*` and `climate.*` entities from `GET /api/states` as two pickers, **Hoover** and **Thermostat**, each with "None". One of each is picked automatically when there is exactly one."
- §4.2 battery "found by name in `/api/states` as `sensor.<the vacuum's object id>_battery`"; "no such sensor, no percentage. No registry read". "The vacuum entity's own `battery_level` attribute is **deprecated** in Home Assistant and not used." Heating: "`climate.set_preset_mode` with `eco` or `none`."
- §4.3 "Commands go over REST (`POST /api/services/<domain>/<service>`), 5 s timeout. The row shows the change when Home Assistant reports it, not before"; "Not in front: the socket closes. On reopen: backoff, and the full read covers the gap."
- §4.4 "**Start** is enabled while docked, idle or paused"; "**Start asks for a fresh Adult or Admin PIN** every time (permission `hoover.start`, "Start the hoover", default roles Admin and Adult, fresh PIN, as Holiday mode). A Child's PIN is refused."; "The tray shows only when it has a button".
- §6 "Logs carry the call and its outcome only: no address, no token, no entity ids or names". `LogHygieneTest` covers the new modules.
- §8.1 "No address or token goes into a committed file." In notes and commits the address is `[ha address]`.

**Copy** (verbatim from the spec; curly apostrophes and "›", "·" in code):
- **Tray:** "One-tap"; "Hoover"; "Start"; "Cleaning" (the inactive pill); statuses "Docked · 86%", "Cleaning · 60%", "Going home · 40%", "Paused · 55%", "Stuck. Check the Eufy app."; the permission "Start the hoover".
- **§6 lines** (row and Settings): "Can’t reach Home Assistant. Check the NAS is on."; "The tablet is offline. Check the Wi-Fi."; "Home Assistant no longer accepts Culvery’s token. Add a new one in Settings › Other integrations."; "Home Assistant has no hoover called that any more. Choose it again in Settings › Other integrations." (and "…no thermostat called that…"); "Home Assistant can’t reach the hoover. Check the Eufy app."; "Home Assistant can’t reach the thermostat. Check the Nest integration."; "The hoover didn’t start. Check the Eufy app."
- **Settings:** "Other integrations"; "Home Assistant" (the source, as "Hue Bridge"); "Check"; "Hoover"; "Thermostat"; "None"; the thermostat line "`{name} · 20.8 °C · heating to 21.2 °C`" or "`{name} · 20.8 °C · Eco`".
- **TalkBack:** "Hoover, docked, battery 86 percent." with the button "Start cleaning"; while cleaning "Hoover, cleaning, battery 60 percent." and no button.
- **This plan's copy** (spec gaps; the owning task tests each):
  - statuses "`Idle · 55%`", "Checking…" (state `unknown`, or before the first read); with no battery the word alone ("Docked"); while a start is out the pill reads "Starting…";
  - the 403 line: "Home Assistant has blocked the tablet. Unblock its address in Home Assistant.";
  - 11b's calls with nothing to call: "Home Assistant isn’t linked. Link it in Settings › Other integrations."; "No hoover is chosen. Choose one in Settings › Other integrations."; "No thermostat is chosen. Choose one in Settings › Other integrations."; a thermostat change Home Assistant refuses: "The thermostat didn’t change. Check the Nest integration.";
  - the thermostat line when not heating: "`{name} · 20.8 °C · set to 21.2 °C`", with no target "`{name} · 20.8 °C`";
  - TalkBack with no battery "Hoover, docked."; with a problem "Hoover. {line}";
  - Settings: the title line "Devices Culvery reaches through Home Assistant."; "Not linked yet. Enter Home Assistant’s address and a token for Culvery, then Check."; "Linked."; the fields "Home Assistant’s address" (placeholder "http://192.168.1.20:8123") and "Token for Culvery" (placeholder "Paste the token here", once saved "Saved. Paste a new token to replace it."); the help line "Give the NAS a fixed address on the router. Then sign in to Home Assistant as the Culvery user (not an administrator), open Profile › Security › Long-lived access tokens › Create token, and paste the token here.";
  - Check's failures: "Use an address on the home network, like http://192.168.1.20:8123, or one that starts https://."; "Paste a token for Culvery first."; "Home Assistant didn’t accept that token. Create a new one and paste it here."; "Nothing at that address answered as Home Assistant. Check the address."; unreachable, offline and blocked give their §6 lines; a refused Admin PIN saves nothing and shows no line;
  - no entity of a kind: "Home Assistant has no hoover." / "Home Assistant has no thermostat.".

**Privacy and the token:**
- The address, the token, entity ids and friendly names are never logged, never in an exception message or cause, never in a `toString`, never in a test name or tag. They show on screen only on Settings › Other integrations (names on the pickers and the thermostat line).
- Log lines pass a call label as `what` (`what.label` is in `LogHygiene.ALLOWED`) and otherwise interpolate only `e::class.simpleName` and HTTP codes. `HaCall` labels are fixed words ("check", "states", "state", "service", "socket"), never a domain, service or entity.
- The token field is masked, never learned by the keyboard, held in plain `remember` (never `rememberSaveable`), and cleared after Linked; the clipboard is cleared after Linked when it holds the token.
- Main-source JSON is built with `buildJsonObject`, never as string literals (`CopyScanTest` refuses straight quotes).
- Sample data only: entities `vacuum.hoover`, `sensor.hoover_battery`, `climate.hall` ("Hall"), and the spec's `vacuum.eufy_clean_x10_pro_omni` / `sensor.eufy_clean_x10_pro_omni_battery` for the naming rule; addresses `192.168.1.20`, `ha.example.com`; tokens `test-token`, `new-token`. Files are UTF-8.

**From the house rules** (as Plan 10; binding):
- **Platform:** package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only; **no new library; never change a version**; no deprecated API, and any one met is flagged to the user before use. Known here and avoided: the vacuum's `battery_level` attribute; `KeyboardOptions(autoCorrect = …)` (use `autoCorrectEnabled`); `LocalClipboardManager` (use Android's `ClipboardManager` service: `primaryClip`, `clearPrimaryClip()`, API 28+); Works with Nest. Checked: `OkHttpClient.newWebSocket`, `WebSocketListener`, `OkHttpClient.Builder.pingInterval`, `MockResponse.withWebSocketUpgrade`, `PasswordVisualTransformation`, `KeyboardType.Password`.
- **New modules:** `:core:homeassistant` (`culvery.android.library`, `culvery.android.compose`, `culvery.hilt`, roborazzi; `api(project(":core:plugin"))`, `api(project(":core:net"))` (`HttpUrl` is in its API), `implementation` of `:core:ui`, `:core:access`, `libs.kotlinx.serialization.json`, `libs.androidx.datastore.preferences`; `testImplementation(libs.okhttp.mockwebserver)`; `android { testFixtures { enable = true } }` with the Compose fixture dependencies as `core/google/build.gradle.kts`); `:capability:hoover` (as `capability/lights/build.gradle.kts`, with `:core:access`); `:capability:heating` (library and hilt; `:core:plugin`, DataStore); `:provider:hoover-homeassistant` and `:provider:heating-homeassistant` (library and hilt; their capability, `:core:homeassistant`, `testImplementation(testFixtures(project(":core:homeassistant")))`). Each is added to `settings.gradle.kts`, `app/build.gradle.kts` and the README's module list in the task that creates it.
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`, plus `./gradlew -p build-logic :convention:test` (LogHygiene, ModuleBoundaries) in any task that adds a log line or a module; never plain `test`, `check` or `build`, and never a Gradle `install…` task; no subagent touches a device. **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`**: a long temp path breaks Robolectric's SQLite. Scratch and prediction lists go in `$LISTS` = `C:/t9`, a plain shell variable, never exported.
- **Tests that can fail:** every new test fails before its change; one that passes before it is labelled **pin**. Timer tests run on virtual time and assert one tick before the boundary and at it. Socket and HTTP tests use MockWebServer; nothing reaches a real address. Each module that needs a PIN copies Lights' `TestAccess(role)` (`capability/lights/src/test/.../TestLights.kt`) with its own `PermissionSource`.
- **Every UI task ends the same way** (Plan 10): `compareRoborazziDebug` on the module; open each `*_compare.png` and each new image against the drawing named; `recordRoborazziDebug`; the baseline check (write `$LISTS/expected-baselines.txt` and `$LISTS/expected-prefixes.txt` afresh, an empty file for the one not used, then run Plan 10's four `comm`/`grep` lines over `git status --porcelain -- "*.png"`; both lists of the pair that applies must be empty, or stop); the gate; commit. Every new screen is recorded `_dark` and `_light`.
- **Copy and accessibility:** `CopyScanTest` passes; every screen test ends with `compose.assertClickablesHaveRoleAndLabel()`.
- **Code:** icons from `Icons`, a new one in `tools/fonts/icons.txt` with the subset rebuilt (`python tools/fonts/subset.py --source "$LISTS/material-symbols-full.ttf"`, the full font from `git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf`; a missing glyph means a download: stop and ask); layout numbers in the module's dimens object; comments say what the code can't, one line, spec refs as `(11 §4.4)`, never a task or ruling number. Each new file with a `TAG` is named in `LogHygieneTest.theScanReachesTheShippedCode` by the task that adds it.
- **Devices:** the emulator is `culvery-5` (`emulator-5554` holds a signed release and the household: never install on it). The tablet (SM-T510) gets `adb install -r` of a signed release only; never uninstall, never wipe.
- **Git:** a commit is a plain sentence with no prefix and no trailer of any kind; never push; **never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **The token is revoked, or Home Assistant blocks the tablet, while Home shows.**
   - **Expected:** a 401 or `auth_invalid` gives the token line; a 403 gives the blocked line, not the token one; after a refused token the socket and every read but Check stop until a Check succeeds, because Home Assistant can ban an address after failed logins; a good Check (even of the same token) clears the line and reopens the socket.
   - **Tests** (Task 2): `HomeAssistantClientTest.anInvalidAuthIsTokenRefused`, `aForbiddenIsNotTokenRefused`; (Task 3) `HomeAssistantTest.aRefusedTokenStopsEverythingButCheck`, `aGoodCheckOfTheSameTokenReopensTheSocket`.
2. **The address as people type it.**
   - **Expected:** `192.168.1.20`, ` http://192.168.1.20:8123/ ` and `HTTP://192.168.1.20` give `http://192.168.1.20:8123/`; `http://192.168.1.20:80` keeps 80; `https://ha.example.com` gives port 8123, `https://ha.example.com:443` keeps 443; `http://ha.example.com`, `ftp://…`, a path, a query or user info are refused, and the token is never sent for a refused address.
   - **Tests** (Task 1): `HomeAssistantAddressTest.*`; (Task 9) `HomeAssistantLinkingTest.aRefusedAddressSendsNothing`.
3. **Start tapped twice, a Child at the wall, or Start while it's already cleaning.**
   - **Expected:** one PIN pad, one `vacuum/start`; a Child's PIN starts nothing; a start with the hoover already cleaning sends nothing.
   - **Tests** (Task 5): `HaHooverSystemTest.aSecondStartWhileOneRunsSendsNothing`, `startWhileCleaningSendsNothing`; (Task 8) `HooverActionsTest.aChildsPinStartsNothing`, `HooverRowTest.aDoubleTapAsksOnce`.
4. **The NAS restarts while Home shows, and Culvery goes behind another app.**
   - **Expected:** the row says "Can’t reach Home Assistant…"; the socket reopens with backoff and the subscription's first event re-reads every entity; the row recovers without a tap; behind another app the socket is closed.
   - **Tests** (Task 3): `HomeAssistantTest.aDroppedSocketReopensAndReadsAgain`, `theSocketClosesBehindAnotherApp`.
5. **The integration in odd states:** `unknown`, `unavailable`, a word not in the six, the battery sensor missing, `unavailable` or out of range, the chosen vacuum deleted, a service Home Assistant refuses.
   - **Expected:** "Checking…" with Start inactive; the §6 line; "Checking…"; the status without "· %"; the "no hoover called that" line; a refused `vacuum/start` is "The hoover didn’t start…" and leaves the row's status alone.
   - **Tests** (Task 5): `HaHooverSystemTest.oddStatesReadSafely`, `aMissingBatteryLeavesThePercentOut`, `aDeletedHooverSaysChooseItAgain`, `aRefusedServiceIsNotStarted`.

---

| # | Task | Review |
|---|---|---|
| 1 | `:core:homeassistant` 1: the module, the link and its store, the address rule, `HaJson`, the capture | sonnet |
| 2 | `:core:homeassistant` 2: `HomeAssistantClient`: REST, the socket, errors | **opus** (the token, the socket, timeouts) |
| 3 | `:core:homeassistant` 3: `HomeAssistant` (cache, problem, socket), the device seam, the fixtures | **opus** (backoff, in front, the refused token) |
| 4 | `:capability:hoover` and `:capability:heating`: seams, stores, words, the permission, the hoover's capability, the icon | sonnet |
| 5 | `:provider:hoover-homeassistant`: state, battery, Start and its 30-second check, the picker | **opus** (states, races) |
| 6 | `:provider:heating-homeassistant`: state, `setEco`, the line, the picker | sonnet |
| 7 | Home: tray buttons, `HhTrayColumn`, the tray in the utility row | sonnet |
| 8 | The Hoover row and Start's fresh PIN | **opus** (access) |
| 9 | Settings › Other integrations: linking behind an Admin PIN, the pickers, the token field, wiring | **opus** (the token, what may be contacted) |
| 10 | Simplify the whole branch | controller |
| 11 | The emulator walk-through on `culvery-5`, the captures | controller + user |
| 12 | Fixtures from the captures; on the tablet | controller + user, **needs the SM-T510** |
| 13 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:** the shared link bottom-up (1–3), so both providers meet a tested client and cache; the seams (4) before the providers (5–6), each tested against `FakeHomeAssistantApi`; Home's tray (7) before the row that fills it (8); Settings and the app wiring (9) last of the code, because it is what links a real Home Assistant.

## Plan review rulings (2026-10-26)

**The user's calls:** R1 Start asks for a fresh Adult or Admin PIN every time (`hoover.start`; a Child is refused), asked by the row, not by `HooverSystem.start()`, so 11b's scheduled clean needs none (Task 8). R2 saving a new address or token asks for a fresh Admin PIN (`CorePermissions.CONNECTIONS_MANAGE` through `mayManageConnections()`, as Shopping; Task 9). R3 the token is a plain masked field, typed or pasted; no QR. R4 no registry read: the battery is `sensor.<vacuum object id>_battery` from the states (Task 5). R5 the household's Culvery user exists (non-admin, local network only); only its token is made, during Task 11.

**The review's findings,** each applied in the task named: test fixtures that build `HomeAssistant` (3); `:core:net` as `api` (1); the typed port read before parsing (1); the secret field's tests (9); the refused-token stop (3); public DataStore constructors and flow-taking internal constructors (4–6); the module's test setup (1); the boundary test a pin (4); the row tests in `HhTrayColumn` (8); JSON built in code (2); `assertFitsTheCard` (7); the fake's `follows` and `cancels` (3); the 30-second check cancelled with the row (8); 403 apart from 401 (2); a Check bumps the link's generation (3); the capture only of chosen entities and allowed numbers (1); the token field's state and the clipboard (9); `start()` and `setEco()` safe for 11b's calls (5, 6); no cause on socket failures (2); no reads after a refused token (3); a refused service is the device's failure, not the link's (2, 5, 6); the timeout test on a short client (2); `HeatingSystem.watch` cut, `heatingLine` in the provider (4, 6); one `HhTrayColumn`, rows at their own height (7); `HomeAssistant.watch(ids)` in place of a separate live class (3); the token's blast radius noted in the follow-ups (13).

## Rulings against the code

The spec leaves these to the builder, and a few fall out of the code. Each is this plan's call.

1. **The link lives in `:core:homeassistant`** as `HomeAssistantLink(baseUrl, token)` in a DataStore file `homeassistant_settings` (`HOME_ASSISTANT_ID = "home.homeassistant"`). The chosen entity ids sit in `hoover_settings` and `heating_settings`, owned by the capabilities. All three are under `files/datastore/`, which `data_extraction_rules.xml` already excludes from cloud backup and device transfer for every domain; `NoBackupTest` pins it.
2. **The address rule** (`homeAssistantUrl`): trimmed; no scheme means `http://`; only `http` and `https`; no user info, path (beyond "/"), query or fragment; whether a port was typed is read from the authority before parsing, and no port means 8123 for either scheme; `http` only to a home-network host (`isHomeNetworkHost`), so the token never crosses anything but the home Wi-Fi in clear; `https` to any host.
3. **One `HomeAssistant`** (`@Singleton`, `Startable`) owns the one `HomeAssistantApi`, `entities: StateFlow<Map<String, EntityState?>>` (a null value: Home Assistant has no such entity), `problem` and the socket. `problem` holds only `UNREACHABLE`, `OFFLINE`, `TOKEN_REFUSED` and `BLOCKED`: a refused service call is the device's failure, and "not Home Assistant" only Check's.
4. **The socket is `subscribe_entities`** (the spec's lean). Its first event lists every followed entity, so it is the full read on connect; an id it leaves out is gone. The client applies the compressed changes (`a`, `c` with `+`/`-`, `r`) and emits whole states. `pingInterval` 30 s; OkHttp drops the 5 s read timeout once a socket is upgraded.
5. **Who watches:** `HomeAssistant.watch(ids: Flow<Set<String>>)`: the Hoover row watches the vacuum and its battery; Settings › Other integrations watches its devices' entities. The socket follows the union while a link is saved, Culvery is in front (`AlertHost.inFront`) and the union isn't empty, keyed on the link and its generation. A refused token stops it (caught upstream of `retryWithBackoff`, then `awaitCancellation()`) until the generation changes; while the problem is `TOKEN_REFUSED`, `read`, `states` and `call` throw without calling. A Linked Check bumps the generation, clears the problem and reopens the socket.
6. **The battery sensor** is `sensor.` + the vacuum's object id + `_battery` (R4), read from the cache like any entity; absent, `unavailable` or outside 0..100, no percentage.
7. **Start's check (§6), on and off Home:** if the cache has no state for the vacuum, one `read` first; with no link, no device, or an activity not in docked, idle and paused, it returns without sending. Then `vacuum/start`, then `read` every 5 s for up to 30 s; the live cache counts too. Started when the activity leaves docked, idle and paused (not to `error`); `error` or 30 s is `NotStarted`. One start at a time: `Mutex.tryLock` false is `Busy`; the lock is released in `finally`. The check runs in the caller's scope: the row leaving Home cancels it.
8. **`setEco` is the same rule** with `climate/set_preset_mode` (`eco` or `none`) and the preset as the check: `Done`, `NotConfirmed` after 30 s, or `Failed(line)`. 11b words its own tile lines from these.
9. **The tray is the shell's, the buttons the capabilities'** (spec §4.4). `TrayButton(id, order, content)` in `:core:plugin`; `Capability.trayButtons()` defaults to none. Hoover is order 20 (11b's Holiday tile will be 10).
10. **Placement** (spec §4.4): `HomeLayout` gains `tray: List<TrayButton>`; with a tray the utility row exists: the utility card (Shopping) at `weight(1f)`, then the tray at 333 dp; with no utility card the tray stands at the row's left and the rest of the row is empty; with no REGULAR card the row fills the column. WIDE (Coming up) never shows while the row does, as with Shopping. Without a tray, everything is Plan 10's.
11. **`HhTray` stays as it is** (a row with the label inline, used by Lights). `HhTrayColumn` in `:core:ui` is the Home tray: the same edge, colour and label, the label on top, rows stacked top down at their own height, 8 apart.
12. **The row's status is up to two lines**, ending in "…": the §6 lines don't fit one line in 333 dp. TalkBack and Settings carry the whole line.
13. **A failed start's line** replaces the status until the activity changes or `START_LINE_MS = 300_000` pass.
14. **Heating has no `Capability` in 11a.** Its seam is bound (`@Multibinds Set<HeatingSystem>`) for 11b; the only thing on screen is Settings' line.
15. **Settings' pickers are `HomeAssistantDevice`s**, multibound by the providers: Hoover (order 10), Thermostat (order 20). The page is `@IntoSet SettingsPage` from `:core:homeassistant` (as `DisplayPage` in `:app`), `id = "other_integrations"`, order 480, behind Settings' own Admin PIN, with R2's fresh Admin PIN before a new address or token is saved.
16. **Auto-pick** happens only right after a Check that saved a link, for a kind with nothing chosen and exactly one entity; "None" chosen later stays None.
17. **The capture** mirrors `HueCapture`: `HaCapture` in `core/homeassistant/src/debug` logs `HaJson.redact(body, keep)` under tag `HaCapture` when `adb shell setprop log.tag.HaCapture DEBUG`, `keep` being the ids currently watched; `src/release` has a no-op.
18. **Network config:** the cleartext rule stays; its comment gains one line for Home Assistant (spec §4.1).

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Tray | 500 wide; edge 2 `s3`; radius 36; padding 16; "ONE-TAP" 18/700 | 333 wide (`ShellDimens.trayWidth`); edge `PageHeaderTokens.trayEdge` (1 dp, the header tray's), `surf3`; radius 24; padding 11; label `HhType.sectionLabel` with the filled `touch_app` |
| Tray rows | 12 apart | 8 apart, 8 under the label, each at its own height |
| Hoover row | `s2`, radius 26; icon (tiles' 36); "Hoover" 22/700; status 18 muted; pill 54 | `surf2`, radius 17, padding 10 × 12; `cleaning_services` 24, `accent`, filled, 12 before the text; 15 sp W700 `ink`; 12 sp `mute`, two lines at most; `HhButton` Primary with `play_arrow` (48), 12 after the text |
| Inactive pill | — | 48 high, 20 sides, 1 dp `surf3` outline, 15 sp W700 `mute`, not a button |
| Utility row with the tray | Shopping 530, tray 500, gap 22 | Shopping 353 × 359, tray 333 × 359, 15 apart |
| Settings › Other integrations | — | as Settings › Lights: gaps 16, buttons 12 apart (`HomeAssistantDimens`) |

## File Structure

```
core/homeassistant/ (new): HomeAssistantLink.kt (link, HOME_ASSISTANT_ID, homeAssistantUrl), HomeAssistantSettings.kt,
  HaJson.kt (EntityState, SocketMessage, redact), HaCall.kt; src/debug|release/HaCapture.kt; test resources ha/*.json (Task 1)
  HomeAssistantClient.kt (HomeAssistantApi, HaProblem, HomeAssistantException)                             (Task 2)
  HomeAssistant.kt, HomeAssistantDevice.kt, HomeAssistantWords.kt, di/HomeAssistantModule.kt;
  src/testFixtures: FakeHomeAssistantApi.kt, TestHomeAssistant.kt (testHomeAssistant, entityState)         (Task 3)
  HomeAssistantLinking.kt, ui/OtherIntegrationsPage.kt, ui/HomeAssistantDimens.kt                          (Task 9)
capability/hoover/ (new): HooverContract.kt, HooverSettings.kt, HooverWords.kt, HooverPermissions.kt,
  HooverCapability.kt, di/HooverModule.kt (Task 4); HooverActions.kt, ui/HooverRow.kt, ui/HooverDimens.kt (Task 8)
capability/heating/ (new): HeatingContract.kt, HeatingSettings.kt, di/HeatingModule.kt                     (Task 4)
core/ui: Icons.kt, tools/fonts/icons.txt, the font (cleaning_services) (Task 4); PageHeader.kt (HhTrayColumn) (Task 7);
  Controls.kt (HhTextField secret, SecretKeyboardOptions) (Task 9)
provider/hoover-homeassistant/ (new): HaHooverSystem.kt, HaHooverDevice.kt, HaHooverWords.kt, di/          (Task 5)
provider/heating-homeassistant/ (new): HaHeatingSystem.kt, HaThermostatDevice.kt, HaHeatingWords.kt, di/   (Task 6)
core/plugin: Capability.kt (trayButtons), TrayButton.kt, HomeCardPlacer.kt; app/.../shell/ShellViewModel.kt,
  shell/ui/HomeGrid.kt, HomeTray.kt, ShellDimens.kt                                                        (Task 7)
build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt, LogHygieneTest.kt                          (Tasks 2–6, 9)
app/src/main/res/xml/network_security_config.xml                                                           (Task 9)
settings.gradle.kts, app/build.gradle.kts, README.md                                                       (Tasks 1, 4, 5, 6, 9)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                                       (Tasks 11–13)
```

---

### Task 1: `:core:homeassistant` 1: the link, the address, `HaJson`, the capture (spec §4.1, §4.2, §6, §8.2.3; rulings 1, 2, 17)

**Review:** sonnet.

**Files:**
- Create `core/homeassistant/build.gradle.kts` per Global Constraints; register it in `settings.gradle.kts`, `app/build.gradle.kts`, README.
- Create in `core/homeassistant/src/main/java/uk/co/siland/culvery/core/homeassistant/`: `HomeAssistantLink.kt`, `HomeAssistantSettings.kt`, `HaJson.kt`, `HaCall.kt`; `src/debug/.../HaCapture.kt`, `src/release/.../HaCapture.kt`.
- Create `core/homeassistant/src/test/resources/ha/`: `api.json`, `states.json` (a vacuum in each of `docked`, `cleaning`, `returning`, `paused`, `idle`, `error`, `unavailable`, `unknown`; `sensor.hoover_battery` state `"86"`, `unit_of_measurement: "%"`; `climate.hall` with `preset_mode` `eco` and one with `none`, `hvac_action` `heating`, `current_temperature` 20.8, `temperature` 21.2, `friendly_name` "Hall"; a climate `unavailable`; a `person.alex` with `latitude`/`longitude`, and a `device_tracker` entity), `ws_auth_required.json`, `ws_auth_ok.json`, `ws_auth_invalid.json`, `ws_result_ok.json`, `ws_entities_added.json`, `ws_entities_changed.json`, `ws_entities_removed.json`. Shapes from Home Assistant's REST and WebSocket documentation; Task 12 replaces them with redacted captures.
- Test: `HomeAssistantAddressTest.kt`, `HomeAssistantSettingsTest.kt`, `HaJsonTest.kt`, `HaRedactTest.kt`, `src/testDebug/.../HaCaptureTest.kt`, `HaCaptureSourceTest.kt` (as Hue's).

**Interfaces:**
- Produces:
  - `const val HOME_ASSISTANT_ID = "home.homeassistant"`; `const val HOME_ASSISTANT_PORT = 8123`.
  - `class HomeAssistantLink(val baseUrl: HttpUrl, val token: String)` — `equals` by both; `toString() = "HomeAssistantLink"`.
  - `fun homeAssistantUrl(typed: String): HttpUrl?` — ruling 2; the result ends in "/".
  - `@Singleton class HomeAssistantSettings(store: DataStore<Preferences>)` (public), `@Inject constructor(@ApplicationContext context)` over `settingsDataStore { context.preferencesDataStoreFile("homeassistant_settings") }`: `val link: Flow<HomeAssistantLink?>`; `suspend fun save(link: HomeAssistantLink)`. Writes throw `IOException`.
  - `class EntityState(val entityId: String, val state: String, val texts: Map<String, String>, val numbers: Map<String, Double>)` — attributes split by kind, others dropped; `val friendlyName: String?`; `val domain: String`; `val objectId: String`; `equals` by all; `toString() = "EntityState(domain=$domain, state=$state)"`.
  - `internal object HaJson`: `fun apiRunning(text: String): Boolean` (`message == "API running."`); `fun states(text: String): List<EntityState>`; `fun state(text: String): EntityState`; `fun socket(text: String): SocketMessage`; `fun redact(text: String, keep: Set<String>): String`. A shape it can't read throws `HaJsonException` (no message from the input, no cause).
  - `internal sealed interface SocketMessage`: `AuthRequired`, `AuthOk`, `AuthInvalid`, `Result(id: Int, success: Boolean)`, `Entities(id: Int, added: Map<String, EntityState>, changed: Map<String, EntityChange>, removed: Set<String>)`, `Other`; `EntityChange(state: String?, texts, numbers, removedAttributes: Set<String>)`; all plain classes whose `toString` names only the type. `fun EntityState.apply(change: EntityChange): EntityState`.
  - `enum class HaCall(val label: String) { CHECK("check"), STATES("states"), STATE("state"), SERVICE("service"), SOCKET("socket") }`.
  - `internal object HaCapture { @Volatile var keep: Set<String>; fun log(what: HaCall, text: String) }` — debug: `Log.isLoggable("HaCapture", Log.DEBUG)` then `Log.d` of `redact(text, keep)`; release: does nothing.
  - `redact` (ruling 17): entities whose id isn't in `keep` are dropped (from state lists and from `a`/`c`/`r` maps); kept ids become `"<domain>.[n]"`, in values and as keys; strings are kept only under `state`, `s`, `preset_mode`, `preset_modes`, `hvac_action`, `hvac_mode`, `hvac_modes`, `device_class`, `unit_of_measurement`, `type`, `success`, else `"[s]"`; numbers are kept only under `current_temperature`, `temperature`, `target_temp_low`, `target_temp_high`, `id`, and a kept `sensor` entity's state, else `0`.

- [ ] **Step 1: The failing tests.**
  - `HomeAssistantAddressTest`: `homeAssistantUrl("192.168.1.20")`, `(" http://192.168.1.20:8123/ ")` and `("HTTP://192.168.1.20")` all `.toString() == "http://192.168.1.20:8123/"`; `("http://192.168.1.20:80")` keeps 80; `("https://ha.example.com")` is `"https://ha.example.com:8123/"`; `("https://ha.example.com:443")` keeps 443; `null` for `"http://ha.example.com"`, `"http://8.8.8.8"`, `"ftp://192.168.1.20"`, `"http://192.168.1.20:8123/lovelace"`, `"http://192.168.1.20?x=1"`, `"http://user@192.168.1.20"`, `""`.
  - `HomeAssistantSettingsTest` (a store on a file under `context.filesDir`): `save` then `link.first()` equals it; nothing saved gives null; `HomeAssistantLink(url, "test-token").toString()` contains neither "192.168" nor "test-token".
  - `HaJsonTest`: `apiRunning` true for `api.json`, false for `{"message":"Hello"}`; `states(states.json)` reads each vacuum's `state`, the sensor's `"86"`, `numbers["current_temperature"] == 20.8`, `texts["preset_mode"] == "eco"`, `friendlyName == "Hall"`, `objectId == "hoover"`; `socket` reads each `ws_*.json` as its type; `apply` of `ws_entities_changed.json` turns `docked` to `cleaning`, keeps the attributes it doesn't name, and drops one named under `-`; `removed` from `ws_entities_removed.json`; `states("not json")` throws `HaJsonException` with a null cause and a message holding none of the input; no `EntityState`, `SocketMessage` or `EntityChange` `toString()` holds an id or a name.
  - `HaRedactTest`:
    - `namesIdsAndAddressesGo`: over `states.json` and `ws_entities_added.json` with `keep = {vacuum.hoover, sensor.hoover_battery, climate.hall}` and an address put in a text: none of `"Hall"`, `"hoover"`, `"192.168"`, `"test-token"`; kept: `"docked"`, `"eco"`, `"heating"`, `"%"`, `20.8`, `86`, `"vacuum.[1]"`.
    - `otherDomainsAndCoordinatesGo`: nothing of `person.alex` or the `device_tracker` remains (no `"person"`, `"latitude"`, no coordinate number); an unlisted number (`battery_level` on the vacuum) is `0`.
  - `HaCaptureTest` (`testDebug`): nothing logged at `INFO`; at `DEBUG` one line, redacted. `HaCaptureSourceTest`: the release `HaCapture.kt` has no `Log.` call.

  Run `./gradlew :core:homeassistant:testDebugUnitTest`. Expected: FAIL (doesn't compile).
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: The gate** and the convention tests. Expected: PASS, no screenshot changes.
- [ ] **Step 4: Commit.**
```bash
git add settings.gradle.kts app/build.gradle.kts README.md core/homeassistant
git commit -m "Add the Home Assistant core module: the saved link, the address rule, a JSON reader for its answers and a redacted debug capture"
```

---

### Task 2: `:core:homeassistant` 2: `HomeAssistantClient` (spec §4.1–§4.3, §6; rulings 3, 4)

**Review:** opus. The token on the wire, the socket's auth, timeouts and what reaches the log.

**Files:**
- Create `core/homeassistant/.../HomeAssistantClient.kt`.
- Modify `LogHygieneTest.theScanReachesTheShippedCode`: expects `HomeAssistantClient.kt`.
- Test: `HomeAssistantClientTest.kt`, `HomeAssistantSocketTest.kt` (MockWebServer, `withWebSocketUpgrade`).

**Interfaces:**
- Consumes: Task 1; `homeHttpClient`, `Call.await`, `AndroidNetwork` (`:core:net`).
- Produces:
  - `enum class HaProblem { UNREACHABLE, OFFLINE, TOKEN_REFUSED, BLOCKED, NOT_HOME_ASSISTANT, REFUSED }`; `class HomeAssistantException(val problem: HaProblem) : Exception("Home Assistant: ${problem.name}")` — never a cause.
  - `const val COMMAND_TIMEOUT_MS = 5_000L`; `const val PING_MS = 30_000L`; `const val MAX_ANSWER_BYTES = 4_000_000L`.
  - ```kotlin
    interface HomeAssistantApi {
        /** `GET /api/`: returns when it answers as Home Assistant and takes the token. */
        suspend fun check(link: HomeAssistantLink)
        suspend fun states(link: HomeAssistantLink): List<EntityState>
        /** `GET /api/states/<id>`; null on 404. */
        suspend fun state(link: HomeAssistantLink, entityId: String): EntityState?
        /** `POST /api/services/<domain>/<service>` with `entity_id` and [data] in the body. */
        suspend fun callService(link: HomeAssistantLink, domain: String, service: String, entityId: String, data: Map<String, String> = emptyMap())
        /** One socket: auth, `subscribe_entities` for [entityIds], then the followed entities after each event, a missing one null. Ends by throwing [HomeAssistantException] when the socket closes. */
        fun follow(link: HomeAssistantLink, entityIds: Set<String>): Flow<Map<String, EntityState?>>
    }
    ```
  - `class HomeAssistantClient internal constructor(http: OkHttpClient, online: () -> Boolean, capture: (HaCall, String) -> Unit) : HomeAssistantApi`, `@Inject constructor(@ApplicationContext context)` with `homeHttpClient(COMMAND_TIMEOUT_MS)`, `AndroidNetwork(context)::online`, `HaCapture::log`. The socket client is the same client with `pingInterval(PING_MS)`. Requests resolve against `link.baseUrl` (`api/`, `api/states`, `api/websocket`); bodies and socket messages are built with `buildJsonObject`; every answer body and socket message goes to `capture`.
  - Errors: no answer, refused or timed out → `UNREACHABLE`, or `OFFLINE` when `online()` is false; only 401 and `auth_invalid` → `TOKEN_REFUSED`; 403 → `BLOCKED` (Home Assistant's address ban); `check`'s 200 without "API running." or 404 → `NOT_HOME_ASSISTANT`; any other non-2xx on a service → `REFUSED`, on a read → `UNREACHABLE`; an unreadable answer → `UNREACHABLE`. Each failure logs once: `Log.w(TAG, "${what.label} failed (${e::class.simpleName})")` or with `answer.code`.

- [ ] **Step 1: The failing tests.**
  - `HomeAssistantClientTest`:
    - `everyCallCarriesTheBearerToken`: each REST call's request has `Authorization: Bearer test-token`.
    - `checkAcceptsHomeAssistant`: `api.json` at `/api/` returns; `{"message":"Hello"}` and 404 are `NOT_HOME_ASSISTANT`.
    - `aRefusedTokenIsTokenRefused`: 401 on `check`, `states` and `callService`.
    - `aForbiddenIsNotTokenRefused` (Review Focus 1): 403 is `BLOCKED` on each.
    - `aServiceCallSendsItsPathAndBody`: `callService(link, "vacuum", "start", "vacuum.hoover")` is `POST /api/services/vacuum/start` with body `{"entity_id":"vacuum.hoover"}`; `("climate", "set_preset_mode", "climate.hall", mapOf("preset_mode" to "eco"))` sends `{"entity_id":"climate.hall","preset_mode":"eco"}`.
    - `aMissingEntityIsNull`: 404 on `/api/states/vacuum.hoover` gives null.
    - `noAnswerInTimeIsUnreachable`: on a client built with `homeHttpClient(100)`, a response delayed 1 s fails `UNREACHABLE`. `theCommandTimeoutIsFiveSeconds` (**pin**): `COMMAND_TIMEOUT_MS == 5_000L`.
    - `offlineIsOffline`: a refused connection with `online = { false }` is `OFFLINE`.
    - `aServiceHomeAssistantRefusesIsRefused`: 400 and 500 on a service are `REFUSED`; 500 on `states` is `UNREACHABLE`.
    - `cancellingTheCallerCancelsTheCall` (as `GoogleApiTest.cancellingTheCallerCancelsTheHttpCall`).
    - `nothingPersonalReachesTheLog` (`ShadowLog`): after a 401, a 403, a 500 and a timeout, no log line holds "192.168", "test-token" or "hoover".
  - `HomeAssistantSocketTest` (a `WebSocketListener` on MockWebServer playing the server):
    - `theSocketAuthsThenSubscribes`: after `auth_required` the client sends `{"type":"auth","access_token":"test-token"}`; after `auth_ok`, `{"id":1,"type":"subscribe_entities","entity_ids":["vacuum.hoover","sensor.hoover_battery"]}`.
    - `theFirstEventIsTheFullRead`: an `a` event with only the vacuum emits `{vacuum.hoover: docked, sensor.hoover_battery: null}`.
    - `changesApplyToWhatWasRead`: a `c` event emits the vacuum `cleaning` with the battery still `86`; an `r` event emits the vacuum null.
    - `anInvalidAuthIsTokenRefused` (Review Focus 1): `auth_invalid` ends the flow with `TOKEN_REFUSED`.
    - `aClosedSocketEndsTheFlow`: the server closing ends it with a `HomeAssistantException(UNREACHABLE)` whose `cause` is null; cancelling the collector closes the socket (the server sees a close).

  Run `./gradlew :core:homeassistant:testDebugUnitTest --tests '*HomeAssistantClient*' --tests '*HomeAssistantSocket*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: The gate** and the convention tests.
- [ ] **Step 4: Commit.**
```bash
git add core/homeassistant build-logic
git commit -m "Add the Home Assistant client: REST calls with the token and the entity subscription over the WebSocket API"
```

---

### Task 3: `:core:homeassistant` 3: `HomeAssistant`, the device seam, the fixtures (spec §4.3, §6; rulings 3, 5, 15)

**Review:** opus. When the socket opens and closes, backoff, and the refused token.

**Files:**
- Create in `core/homeassistant/.../`: `HomeAssistant.kt`, `HomeAssistantDevice.kt`, `HomeAssistantWords.kt`, `di/HomeAssistantModule.kt` (`@Binds HomeAssistantApi`, `@Multibinds Set<HomeAssistantDevice>`, `@IntoSet Startable` for `HomeAssistant`).
- Create in `core/homeassistant/src/testFixtures/java/uk/co/siland/culvery/core/homeassistant/test/`: `FakeHomeAssistantApi.kt`, `TestHomeAssistant.kt`.
- Modify `LogHygieneTest`: expects `HomeAssistant.kt`.
- Test: `HomeAssistantTest.kt`, `HomeAssistantWordsTest.kt`.

**Interfaces:**
- Consumes: Tasks 1–2; `AlertHost.inFront`, `@ApplicationScope`, `retryWithBackoff`, `attempt`.
- Produces:
  - `const val CONFIRM_EVERY_MS = 5_000L`; `const val CONFIRM_FOR_MS = 30_000L` (rulings 7, 8).
  - `fun problemLine(problem: HaProblem): String` — `UNREACHABLE` (and, as a fallback, `NOT_HOME_ASSISTANT`, `REFUSED`): "Can’t reach Home Assistant. Check the NAS is on."; `OFFLINE`: "The tablet is offline. Check the Wi-Fi."; `TOKEN_REFUSED`: "Home Assistant no longer accepts Culvery’s token. Add a new one in Settings › Other integrations."; `BLOCKED`: "Home Assistant has blocked the tablet. Unblock its address in Home Assistant."; `const val NOT_LINKED = "Home Assistant isn’t linked. Link it in Settings › Other integrations."`.
  - `@Singleton class HomeAssistant(api: HomeAssistantApi, link: Flow<HomeAssistantLink?>, inFront: StateFlow<Boolean>, scope: CoroutineScope) : Startable` (public, so the fixtures build it), `@Inject constructor(api, settings: HomeAssistantSettings, host: AlertHost, @ApplicationScope scope)`:
    - `val link: StateFlow<HomeAssistantLink?>`; `val entities: StateFlow<Map<String, EntityState?>>` (cleared when the link changes); `val problem: StateFlow<HaProblem?>` (ruling 3);
    - `suspend fun check(link: HomeAssistantLink)` — `api.check`, and on success bumps the generation and clears the problem (ruling 5);
    - `suspend fun read(entityId: String): EntityState?`, `suspend fun states(): List<EntityState>`, `suspend fun call(domain: String, service: String, entityId: String, data: Map<String, String> = emptyMap())` — each throws `HomeAssistantException` (`TOKEN_REFUSED` without calling when there is no link or the problem is `TOKEN_REFUSED`); a failure records the problem unless it is `REFUSED` or `NOT_HOME_ASSISTANT`; a success clears it; reads write the cache;
    - `fun watch(ids: Flow<Set<String>>): () -> Unit` — the stop counted once (as `LightsFollow.watch`); `internal val watching: Int`;
    - `internal fun failed(problem: HaProblem)`;
    - `start()`: the socket per ruling 5; each emission merges into `entities` and clears the problem; it sets `HaCapture.keep` to the union.
  - ```kotlin
    /** A kind of device Settings › Other integrations offers a picker for (11 §4.1), bound by its provider. */
    interface HomeAssistantDevice {
        val order: Int
        /** The picker's label: "Hoover", "Thermostat". */
        val title: String
        /** The entity domain it lists: "vacuum", "climate". */
        val domain: String
        val noneFound: String
        val chosen: Flow<String?>
        /** What Settings watches for it: the chosen entity and any it reads beside it. */
        val watched: Flow<Set<String>>
        /** Under the picker: its state or what's wrong; null with nothing chosen. */
        val line: Flow<String?>
        suspend fun choose(entityId: String?)
    }
    ```
  - Test fixtures: `fun testHomeAssistant(api: HomeAssistantApi, link: Flow<HomeAssistantLink?> = flowOf(TEST_LINK), inFront: StateFlow<Boolean> = MutableStateFlow(true), scope: CoroutineScope): HomeAssistant` (started); `val TEST_LINK` (`http://192.168.1.20:8123/`, `test-token`); `fun entityState(id: String, state: String, attributes: Map<String, Any> = emptyMap()): EntityState`; `class FakeHomeAssistantApi : HomeAssistantApi` with `val states: MutableMap<String, EntityState>`, `val calls: List<String>` (`"check"`, `"states"`, `"state vacuum.hoover"`, `"service vacuum/start vacuum.hoover"`, `"service climate/set_preset_mode climate.hall preset_mode=eco"`), `val follows: List<Set<String>>`, `val cancels: Int`, `var failNext: HaProblem?`, `var failAll: HaProblem?`, `var onService: (domain: String, service: String, entityId: String, data: Map<String, String>) -> Unit`, `val socket: MutableSharedFlow<Map<String, EntityState?>>` that `follow` emits from, `fun dropSocket(problem: HaProblem)`, and a `gate: CompletableDeferred<Unit>?` that `check` waits on.

- [ ] **Step 1: The failing tests.**
  - `HomeAssistantWordsTest`: each `HaProblem`'s line, verbatim.
  - `HomeAssistantTest` (the fixtures; virtual time):
    - `aReadFillsTheCacheAndAMissingOneIsNull`.
    - `aFailureSetsTheProblemAndASuccessClearsIt`: `failNext = UNREACHABLE` → `read` throws and `problem == UNREACHABLE`; the next `read` clears it.
    - `aRefusedServiceLeavesTheProblemAlone`: `failNext = REFUSED` on `call` → throws, `problem` null.
    - `noLinkIsTokenRefusedAndCallsNothing`.
    - `aNewLinkStartsAfresh`: a new link clears `entities` and `problem`.
    - `nothingOpensUntilAScreenWatches`: no `follows` after a minute.
    - `watchersShareOneSocket`: `watch(flowOf({vacuum.hoover, sensor.hoover_battery}))` and `watch(flowOf({climate.hall}))` give one current follow of all three (`follows.last()`); emissions reach `entities`.
    - `theSocketClosesBehindAnotherApp` (Review Focus 4): `inFront = false` → `cancels == 1`; back in front, a new follow.
    - `aDroppedSocketReopensAndReadsAgain` (Review Focus 4): `dropSocket(UNREACHABLE)` sets the problem; one tick before `retryWithBackoff`'s first wait no new follow, at it one; its first emission clears the problem.
    - `aRefusedTokenStopsEverythingButCheck` (Review Focus 1): `dropSocket(TOKEN_REFUSED)`: no follow in the next 10 minutes; `read` and `call` throw `TOKEN_REFUSED` with nothing added to `calls`; `check` still calls.
    - `aGoodCheckOfTheSameTokenReopensTheSocket` (Review Focus 1): after the refusal, `check(TEST_LINK)` succeeds: `problem` null and one new follow at once.
    - `stoppingTwiceCountsOnce`; `nothingWatchedNoSocket`.

  Run `./gradlew :core:homeassistant:testDebugUnitTest --tests '*HomeAssistantTest*' --tests '*HomeAssistantWords*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: The gate** and the convention tests.
- [ ] **Step 4: Commit.**
```bash
git add core/homeassistant build-logic
git commit -m "Keep Home Assistant's entities in one cache, follow them over one socket while a screen shows them, and stop on a refused token until a Check succeeds"
```

---

### Task 4: `:capability:hoover` and `:capability:heating` (spec §4.2, §4.4–§4.6; rulings 8, 9, 14; R1)

**Review:** sonnet.

**Files:**
- Create `capability/hoover/build.gradle.kts`, `capability/heating/build.gradle.kts`; register both.
- Create in `capability/hoover/src/main/java/uk/co/siland/culvery/capability/hoover/`: `HooverContract.kt`, `HooverSettings.kt`, `HooverWords.kt`, `HooverPermissions.kt`, `HooverCapability.kt`, `di/HooverModule.kt` (`@Multibinds Set<HooverSystem>`, `@IntoSet Capability`, `@IntoSet PermissionSource`).
- Create in `capability/heating/src/main/java/uk/co/siland/culvery/capability/heating/`: `HeatingContract.kt`, `HeatingSettings.kt`, `di/HeatingModule.kt` (`@Multibinds Set<HeatingSystem>`).
- Modify `core/plugin/.../Capability.kt` (the `order` KDoc adds "Hoover 70, no tab"), `core/ui/.../Icons.kt` (`CLEANING_SERVICES = "cleaning_services"`), `tools/fonts/icons.txt`, the font.
- Modify `build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt`.
- Test: `HooverWordsTest.kt`, `HooverSettingsTest.kt`, `HooverCapabilityTest.kt`, `HooverPermissionsTest.kt`, `HeatingSettingsTest.kt`; `core/ui` `IconFontTest`.

**Interfaces:**
- Produces (`capability/hoover`):
  - `enum class HooverActivity { DOCKED, CLEANING, RETURNING, PAUSED, IDLE, ERROR }`; `val STARTABLE = setOf(DOCKED, IDLE, PAUSED)`.
  - `data class HooverState(val activity: HooverActivity?, val batteryPercent: Int?, val problem: String?)` — `activity` null while unknown; `problem` the line to show.
  - `sealed interface HooverResult { data object Started; data object NotStarted; data object Busy; data class Failed(val line: String) }`.
  - ```kotlin
    interface HooverSystem {
        /** Null while no hoover is chosen: no tray row (11 §4.4). */
        val state: Flow<HooverState?>
        /** No PIN here: the row asks for it (11 §4.4), and 11b's scheduled clean calls this directly. */
        suspend fun start(): HooverResult
        /** A screen showing the hoover is on; call the result when it goes. */
        fun watch(): () -> Unit
    }
    ```
  - `data class HooverPrefs(val device: String? = null)` (`toString` without it); `@Singleton class HooverSettings(store: DataStore<Preferences>)` (public), `@Inject constructor(@ApplicationContext context)` over `hoover_settings`: `val prefs: Flow<HooverPrefs>`, `suspend fun choose(device: String?)`.
  - `object HooverPermissions { const val START = "hoover.start" }`; `class HooverPermissionSource @Inject constructor() : PermissionSource` — `PermissionDef(START, "Start the hoover", setOf(Role.ADMIN, Role.ADULT), freshPin = true)`.
  - Words: `const val HOOVER = "Hoover"`, `START = "Start"`, `START_SPOKEN = "Start cleaning"`, `STARTING = "Starting…"`, `NOT_STARTED = "The hoover didn’t start. Check the Eufy app."`, `STUCK = "Stuck. Check the Eufy app."`, `NO_HOOVER_CHOSEN = "No hoover is chosen. Choose one in Settings › Other integrations."`; `fun hooverStatus(state: HooverState): String` (Copy); `fun hooverSpoken(state: HooverState): String` (Copy's TalkBack lines); `fun resultLine(result: HooverResult): String?` (`NotStarted` → `NOT_STARTED`, `Failed` → its line, else null).
  - `@Singleton class HooverCapability @Inject constructor(systems: Set<@JvmSuppressWildcards HooverSystem>) : Capability` — `id = "hoover"`, `label = HOOVER`, `icon = Icons.CLEANING_SERVICES`, `order = 70`, `hasTab = flowOf(false)`, `cards()` empty; `TabContent()` draws nothing; `trayButtons()` arrives in Task 8.
- Produces (`capability/heating`):
  - `data class HeatingState(val name: String, val eco: Boolean, val currentC: Double?, val targetC: Double?, val heating: Boolean, val problem: String?)` — `toString` without `name`.
  - `sealed interface HeatingResult { data object Done; data object NotConfirmed; data class Failed(val line: String) }`.
  - `interface HeatingSystem { val state: Flow<HeatingState?>; suspend fun setEco(on: Boolean): HeatingResult }`.
  - `data class HeatingPrefs(val device: String? = null)`; `@Singleton class HeatingSettings(store)` (public), `@Inject` over `heating_settings`: `prefs`, `suspend fun choose(device: String?)`.

- [ ] **Step 1: The failing tests.**
  - `HooverWordsTest`: each status in Copy for `HooverState(DOCKED, 86, null)`, `(CLEANING, 60, null)`, `(RETURNING, 40, null)`, `(PAUSED, 55, null)`, `(IDLE, 55, null)`, `(DOCKED, null, null)` → "Docked", `(null, null, null)` → "Checking…", `(ERROR, 30, null)` → "Stuck. Check the Eufy app.", a problem → the problem; spoken: "Hoover, docked, battery 86 percent.", "Hoover, cleaning, battery 60 percent.", "Hoover, docked.", "Hoover. Can’t reach Home Assistant. Check the NAS is on."; `resultLine(NotStarted)` is `NOT_STARTED`, `resultLine(Started)` null.
  - `HooverPermissionsTest`: `hoover.start` is "Start the hoover", Admin and Adult, `freshPin`; a `PermissionRegistry` doesn't grant it to `Role.CHILD`.
  - `HooverSettingsTest`, `HeatingSettingsTest`: a choice survives a new store on the same file; `toString` holds no id.
  - `HooverCapabilityTest`: `hasTab` is false; `cards()` empty; `order == 70`; icon `cleaning_services`.
  - `ModuleBoundariesTest.theHomeAssistantModulesKeepToTheirFamilies` (**pin**: the rules don't change): `:provider:hoover-homeassistant` → `:capability:hoover`, → `:core:homeassistant` allowed; → `:capability:heating`, → `:provider:heating-homeassistant` refused; `:capability:hoover` → `:capability:heating` refused; `:core:homeassistant` → `:capability:hoover` refused.
  - `IconFontTest` covers `cleaning_services`.

  Run `./gradlew :capability:hoover:testDebugUnitTest :capability:heating:testDebugUnitTest :core:ui:testDebugUnitTest --tests '*IconFont*'` and `./gradlew -p build-logic :convention:test`. Expected: FAIL (the pin aside).
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: The gate** and the convention tests. No screenshot changes.
- [ ] **Step 4: Commit.**
```bash
git add settings.gradle.kts app/build.gradle.kts README.md build-logic core/plugin core/ui tools/fonts/icons.txt capability/hoover capability/heating
git commit -m "Add the hoover and heating capabilities: their seams, the chosen devices, the hoover's start permission and the words for its states"
```

---

### Task 5: `:provider:hoover-homeassistant` (spec §4.2–§4.4, §6; rulings 6, 7; R4; Review Focus 3, 5)

**Review:** opus. State mapping, the 30-second check, one start at a time.

**Files:**
- Create `provider/hoover-homeassistant/build.gradle.kts`; register it.
- Create in `provider/hoover-homeassistant/src/main/java/uk/co/siland/culvery/provider/hoover_homeassistant/`: `HaHooverSystem.kt`, `HaHooverDevice.kt`, `HaHooverWords.kt`, `di/HaHooverModule.kt` (`@IntoSet HooverSystem`, `@IntoSet HomeAssistantDevice`).
- Modify `LogHygieneTest`: expects `HaHooverSystem.kt`.
- Test: `HaHooverSystemTest.kt`, `HaHooverDeviceTest.kt`.

**Interfaces:**
- Consumes: Tasks 3–4 (`HomeAssistant`, `problemLine`, `NOT_LINKED`, `CONFIRM_EVERY_MS`, `CONFIRM_FOR_MS`, the fixtures; the hoover seam, `HooverSettings`, `STARTABLE`, `NO_HOOVER_CHOSEN`).
- Produces:
  - `internal fun activityOf(state: String): HooverActivity?` — `docked`, `cleaning`, `returning`, `paused`, `idle`, `error`; anything else null.
  - `internal fun batteryIdOf(vacuumId: String): String` = `"sensor." + objectId + "_battery"` (R4).
  - Words: `GONE_HOOVER = "Home Assistant has no hoover called that any more. Choose it again in Settings › Other integrations."`, `HOOVER_UNREACHABLE = "Home Assistant can’t reach the hoover. Check the Eufy app."`, `NO_HOOVER = "Home Assistant has no hoover."`.
  - `internal fun hooverState(prefs: HooverPrefs, linked: Boolean, entities: Map<String, EntityState?>, problem: HaProblem?): HooverState?` (pure; the system and the device both use it): null with no link or no device; else, in order: a `problem` → `problemLine(problem)`; the vacuum null in `entities` → `GONE_HOOVER`; `unavailable` → `HOOVER_UNREACHABLE`; not in `entities` yet, `unknown` or an unknown word → `HooverState(null, null, null)`; else the activity and the battery (ruling 6).
  - `@Singleton class HaHooverSystem internal constructor(ha: HomeAssistant, prefs: Flow<HooverPrefs>)`, `@Inject constructor(ha, settings: HooverSettings)` `: HooverSystem` — `state` = `hooverState` over `prefs` and `ha`'s flows; `start()` per ruling 7 (`Failed(NOT_LINKED)` / `Failed(NO_HOOVER_CHOSEN)` with nothing sent; a `REFUSED` service → `NotStarted`; any other `HomeAssistantException` → `Failed(problemLine(e.problem))`); `watch()` = `ha.watch(prefs.map { setOfNotNull(device, device?.let(::batteryIdOf)) })`.
  - `@Singleton class HaHooverDevice @Inject constructor(ha: HomeAssistant, settings: HooverSettings) : HomeAssistantDevice` — `order = 10`, `title = HOOVER`, `domain = "vacuum"`, `noneFound = NO_HOOVER`, `watched` = the vacuum and its battery, `line` = `hooverStatus` of `hooverState`; `choose` saves the id.

- [ ] **Step 1: The failing tests** (the fixtures; virtual time).
  - `HaHooverSystemTest`:
    - `noLinkOrNoDeviceNoRow`: no link → `state.first() == null`; no device → null.
    - `eachStateReads`: each fixture vacuum state maps to its activity, with battery 86 from `sensor.hoover_battery`.
    - `theBatteryIsFoundByName`: `batteryIdOf("vacuum.eufy_clean_x10_pro_omni") == "sensor.eufy_clean_x10_pro_omni_battery"`.
    - `oddStatesReadSafely` (Review Focus 5): `unknown` and `"mopping"` give `HooverState(null, null, null)`; `unavailable` gives `HOOVER_UNREACHABLE`.
    - `aMissingBatteryLeavesThePercentOut`: the battery absent, null, `unavailable` or `"120"`: `batteryPercent == null`.
    - `aDeletedHooverSaysChooseItAgain`: the vacuum null → `GONE_HOOVER`.
    - `aProblemShowsItsLine`: `failNext = TOKEN_REFUSED`, then a `read` through `ha` → the state's problem is the token line.
    - `startSendsTheServiceAndWaitsForTheChange`: docked; `onService` turns it `cleaning` from the second read: one `"service vacuum/start vacuum.hoover"`, reads at 5 s and 10 s, `Started`.
    - `aHooverThatStaysDockedDidNotStart`: `NotStarted` at 30 s, not at 29 999 ms.
    - `anErrorIsNotStartedAtOnce`: `error` at the first read → `NotStarted` at 5 s.
    - `aLiveChangeCountsBeforeTheNextRead`: the socket emits `cleaning` at 2 s → `Started` at 2 s.
    - `aSecondStartWhileOneRunsSendsNothing` (Review Focus 3): two `start()`s at once: one service call; the second is `Busy`; after the first ends, a third may start (the lock released).
    - `startWhileCleaningSendsNothing` (Review Focus 3): cached `cleaning` → `NotStarted`, no service call.
    - `aStartWithNothingCachedReadsFirst`: an empty cache: `calls` begins `"state vacuum.hoover"`.
    - `startWithNoLinkOrNoDeviceSendsNothing`: `Failed(NOT_LINKED)`, `Failed(NO_HOOVER_CHOSEN)`; `calls` empty.
    - `aRefusedServiceIsNotStarted` (Review Focus 5): `failNext = REFUSED` on the service → `NotStarted`, no reads, `ha.problem` null.
    - `cancellingTheCheckReleasesTheLock`: cancel a `start()` mid-check; the next `start()` isn't `Busy`.
  - `HaHooverDeviceTest`: `watchedIsTheVacuumAndItsBattery`; `choosingSavesTheId`; `noneClears`; `theLineIsTheStatus` ("Docked · 86%").

  Run `./gradlew :provider:hoover-homeassistant:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: The gate** and the convention tests.
- [ ] **Step 4: Commit.**
```bash
git add settings.gradle.kts app/build.gradle.kts README.md build-logic provider/hoover-homeassistant
git commit -m "Start the hoover through Home Assistant and read its state and battery sensor, checking for 30 seconds that it left the dock"
```

---

### Task 6: `:provider:heating-homeassistant` (spec §4.2, §4.5, §6; ruling 8)

**Review:** sonnet.

**Files:**
- Create `provider/heating-homeassistant/build.gradle.kts`; register it.
- Create in `provider/heating-homeassistant/src/main/java/uk/co/siland/culvery/provider/heating_homeassistant/`: `HaHeatingSystem.kt`, `HaThermostatDevice.kt`, `HaHeatingWords.kt`, `di/HaHeatingModule.kt` (`@IntoSet HeatingSystem`, `@IntoSet HomeAssistantDevice`).
- Modify `LogHygieneTest`: expects `HaHeatingSystem.kt`.
- Test: `HaHeatingSystemTest.kt`, `HaThermostatDeviceTest.kt`, `HaHeatingWordsTest.kt`.

**Interfaces:**
- Consumes: Tasks 3–4.
- Produces:
  - Words: `GONE_THERMOSTAT = "Home Assistant has no thermostat called that any more. Choose it again in Settings › Other integrations."`, `THERMOSTAT_UNREACHABLE = "Home Assistant can’t reach the thermostat. Check the Nest integration."`, `NO_THERMOSTAT = "Home Assistant has no thermostat."`, `NO_THERMOSTAT_CHOSEN = "No thermostat is chosen. Choose one in Settings › Other integrations."`, `THERMOSTAT_REFUSED = "The thermostat didn’t change. Check the Nest integration."`, `THERMOSTAT = "Thermostat"`; `fun heatingLine(state: HeatingState): String` — the problem when set; else Copy's four forms, temperatures `"%.1f"` in `Locale.UK`.
  - `internal fun heatingState(prefs: HeatingPrefs, linked: Boolean, entities: Map<String, EntityState?>, problem: HaProblem?): HeatingState?` (pure, `hooverState`'s order of rules): `name = friendlyName ?: THERMOSTAT`, `eco = preset_mode == "eco"`, `currentC = current_temperature`, `targetC = temperature`, `heating = hvac_action == "heating"`.
  - `@Singleton class HaHeatingSystem internal constructor(ha, prefs: Flow<HeatingPrefs>)`, `@Inject constructor(ha, settings: HeatingSettings)` `: HeatingSystem` — `state` = `heatingState`; `setEco(on)`: nothing sent with no link (`Failed(NOT_LINKED)`) or no device (`Failed(NO_THERMOSTAT_CHOSEN)`); `climate/set_preset_mode` with `eco` or `none`; confirms per ruling 8 under a `Mutex` released in `finally`; `REFUSED` → `Failed(THERMOSTAT_REFUSED)`; other failures → `Failed(problemLine(…))`.
  - `@Singleton class HaThermostatDevice @Inject constructor(ha, settings) : HomeAssistantDevice` — `order = 20`, `title = THERMOSTAT`, `domain = "climate"`, `noneFound = NO_THERMOSTAT`, `watched = setOfNotNull(device)`, `line` = `heatingLine` of `heatingState`.

- [ ] **Step 1: The failing tests.**
  - `HaHeatingWordsTest`: `HeatingState("Hall", false, 20.8, 21.2, true, null)` → "Hall · 20.8 °C · heating to 21.2 °C"; eco → "Hall · 20.8 °C · Eco"; not heating → "Hall · 20.8 °C · set to 21.2 °C"; no target → "Hall · 20.8 °C"; 21.0 shows "21.0 °C"; a problem → the problem.
  - `HaHeatingSystemTest`: `theStateReads` (`HeatingState("Hall", false, 20.8, 21.2, true, null)`; the eco one → `eco == true`); `unavailableSaysCheckTheNestIntegration`; `aDeletedThermostatSaysChooseItAgain`; `setEcoSendsThePreset` (`"service climate/set_preset_mode climate.hall preset_mode=eco"`; `setEco(false)` sends `preset_mode=none`); `ecoIsDoneWhenThePresetShows` (the preset shows from the second read: not done at 9 999 ms, `Done` at 10 s); `aPresetThatNeverChangesIsNotConfirmed` (at 30 s, not before); `aRefusedCallSaysTheThermostatDidntChange` (`REFUSED` → `Failed(THERMOSTAT_REFUSED)`, `ha.problem` null); `setEcoWithNoLinkOrNoDeviceSendsNothing`; `noLinkOrNoDeviceIsNull`.
  - `HaThermostatDeviceTest`: `theLineIsTheThermostatsState`; `watchedIsTheThermostat`.

  Run `./gradlew :provider:heating-homeassistant:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: The gate** and the convention tests.
- [ ] **Step 4: Commit.**
```bash
git add settings.gradle.kts app/build.gradle.kts README.md build-logic provider/heating-homeassistant
git commit -m "Read the thermostat and set its Eco preset through Home Assistant, checking that the change took"
```

---

### Task 7: Home: tray buttons, `HhTrayColumn`, the tray in the utility row (spec §4.4; rulings 9–11)

**Review:** sonnet.

**Files:**
- Create `core/plugin/.../TrayButton.kt`; modify `Capability.kt` (`trayButtons()`), `HomeCardPlacer.kt` (`HomeLayout.tray`, `place(cards, tray)`), `HomeCard.kt` KDoc.
- Modify `core/ui/.../PageHeader.kt`: `HhTrayColumn`, sharing `TrayLabel`.
- Modify `app/.../shell/ShellViewModel.kt` (collects `trayButtons()` as it does `headerItems()`, into `place`), `shell/ui/HomeGrid.kt`, `shell/ui/ShellDimens.kt` (`trayWidth = 333.dp`); create `shell/ui/HomeTray.kt`.
- Test: `HomeCardPlacerTest.kt`, `HomeGridTest.kt`, `ShellViewModelTest.kt`, `ShellScreenshotTest.kt`, `core/ui` `PageHeaderTest.kt`, `PageHeaderScreenshotTest.kt`; `capability/shopping` `ShoppingCardTest.kt`, `ShoppingCardScreenshotTest.kt`.

**Interfaces:**
- Produces:
  - `class TrayButton(val id: String, val order: Int, val content: @Composable () -> Unit)` — KDoc: "A One-tap tray row a capability adds to Home (11 §4.4), placed by [order]: Holiday mode 10, Hoover 20."
  - `Capability.trayButtons(): Flow<List<TrayButton>> = flowOf(emptyList())`.
  - `data class HomeLayout(val left: HomeCard?, val right: List<HomeCard>, val utility: HomeCard? = null, val tray: List<TrayButton> = emptyList())`; `HomeCardPlacer.place(cards: List<HomeCard>, tray: List<TrayButton> = emptyList()): HomeLayout` — `tray` sorted by order then id; ruling 10.
  - `object TrayTokens { val radius = 24.dp; val padding = 11.dp; val gap = 8.dp }` and `@Composable fun HhTrayColumn(label: String, rows: List<Pair<String, @Composable () -> Unit>>, modifier: Modifier = Modifier)` — rows keyed by their string, stacked top down at their own height (ruling 11); tags `tray_column`, `tray_row_{key}`.
  - `@Composable internal fun HomeTray(buttons: List<TrayButton>, modifier: Modifier = Modifier)` = `HhTrayColumn("One-tap", buttons.map { it.id to it.content })`, tagged `home_tray`.

- [ ] **Step 1: The failing tests.**
  - `HomeCardPlacerTest` (the existing tests are **pins**): `aTrayKeepsTheUtilityRow` (today, comingUp, lights, music and one tray button: `tray` holds it, `right == [lights, music]`, no WIDE); `trayButtonsAreInOrder` (orders 20, 10 → 10 first; equal orders by id); `noTrayIsAsBefore` (**pin**).
  - `HomeGridTest` (1280 × 800, as the file's other tests; a tray button is a tagged 333 × 68 box):
    - `shoppingAndTheTrayShareTheRow`: shopping 353 ±1 × 359 ±1 at the column's left; `home_tray` 333 ±0.5 wide, the same top and height, 15 ±0.5 to shopping's right, its right the column's right; lights and music below as Plan 10's.
    - `withoutShoppingTheTrayTakesTheRowsLeft`: tray, lights, music: `home_tray` at the column's left, 333 × 359; lights and music in the 212 row.
    - `theTrayAloneFillsTheColumnsHeight`: tray only: 333 × 586 ±1.
    - The existing tests without a tray are **pins**.
  - `ShellViewModelTest.trayButtonsReachHome`: a capability whose `trayButtons()` emits one button puts it in `uiState.homeCards.tray`.
  - `PageHeaderTest.theColumnTrayStacksRowsUnderItsLabel`: two rows of 40 and 60 dp: `tray_label` on top, the first row 8 ±0.5 under it at 40 high, the second 8 under that at 60 high; the label isn't spoken.
  - `ShoppingCardTest.theCardFitsBesideTheTray` (Shopping is read-only to this plan unless this fails): the card at `DpSize(353.dp, 359.dp)` with 20 items, in both themes, passes `assertFitsTheCard()`. If it fails, stop and ask.
- [ ] **Step 2: Implement.** `HomeGrid`: with a tray, the row is a `Row(weight(1f))` of the utility card (`weight(1f)`) and `HomeTray` (`width(trayWidth)`), then the `cardRow` as now.
- [ ] **Step 3: Images.**
  - **Must not change:** every existing `home_` image, `page_header_*`, `shopping_card_*`.
  - **New** (each `_dark` and `_light`): `home_tray_shopping` (Shopping as a tagged box, the tray with one tagged row, Lights and Music), `home_tray_no_shopping`, `home_tray_alone`; `page_header_tray_column`; `shopping_card_beside_tray` (353 × 359).
  - **Check against** the handoff's "Right column" 1a and "One-tap tray (note 13)".
```bash
git add core/plugin core/ui app capability/shopping
git commit -m "Give Home a One-tap tray in the utility row, beside Shopping or on its own, filled with buttons the capabilities add"
```

---

### Task 8: The Hoover row and Start's fresh PIN (spec §4.4, §4.6, §6; rulings 7, 12, 13; R1; Review Focus 3)

**Review:** opus (access).

**Files:**
- Create in `capability/hoover/.../`: `HooverActions.kt`, `ui/HooverRow.kt` (`HooverRow` stateless; `HooverRowHost(system, actions)`), `ui/HooverDimens.kt`.
- Modify `HooverCapability.kt`: takes `HooverActions`; `trayButtons()` = `[TrayButton("hoover.start", 20) { HooverRowHost(…) }]` while the first system's `state` is not null.
- Test: `HooverActionsTest.kt`, `ui/HooverRowTest.kt`, `ui/HooverRowScreenshotTest.kt`, `HooverCapabilityTest.kt`; in `src/test`: a `FakeHooverSystem` (scriptable `state`, `next: HooverResult`, a gate, `starts: Int`, `watchers: Int`) and `TestAccess` copied from Lights with `HooverPermissionSource`.

**Interfaces:**
- Consumes: Task 4's words, seam and permission; Task 7's `HhTrayColumn`; `AccessControl`; `HhIcon`, `HhButton`, `rememberSingleAction`.
- Produces:
  - `@Singleton class HooverActions @Inject constructor(access: AccessControl, systems: Set<@JvmSuppressWildcards HooverSystem>)`: `suspend fun start(): HooverResult?` — `access.authorise(HooverPermissions.START)`; null (cancelled or refused) returns null and starts nothing; else the system's `start()`.
  - `@Composable internal fun HooverRow(state: HooverState, line: String?, starting: Boolean, onStart: () -> Unit)` — `line` replaces the status when set; tags `hoover_row`, `hoover_status`, `hoover_start`, `hoover_inactive`.
  - `const val START_LINE_MS = 300_000L`.
  - `HooverRowHost`: collects `state`; `system.watch()` in a `DisposableEffect`; a tap runs `actions.start()` once through `rememberSingleAction` (`starting` while the PIN pad and the check run; the row leaving Home cancels both); `resultLine(result)` is held until the activity changes or `START_LINE_MS` pass.
- Behaviour: one semantics group for the icon and the words, spoken `hooverSpoken(state)` (or `"Hoover. " + line`); Start is `HhButton(START, tone = Primary, icon = PLAY_ARROW, on = ButtonSurface.Surf, spokenName = START_SPOKEN)` while the activity is in `STARTABLE` and nothing is starting; while starting it reads `STARTING` and ignores taps; otherwise the inactive pill reads "Cleaning" for `CLEANING` and `RETURNING`, hidden from TalkBack, and nothing shows for an unknown activity, `ERROR` or a problem.

- [ ] **Step 1: The failing tests.** Screen tests end with `assertClickablesHaveRoleAndLabel()`.
  - `HooverActionsTest`:
    - `anAdultsPinStarts`: `TestAccess(Role.ADULT)` → `asked == [["hoover.start"]]`, `starts == 1`, the system's result returned.
    - `anAdminsPinStarts`.
    - `aChildsPinStartsNothing` (Review Focus 3): `Role.CHILD` → null, `starts == 0`.
    - `aCancelledPadStartsNothing`: `TestAccess(null)` → null, `starts == 0`.
    - `everyStartAsksAgain`: two starts → two asks.
  - `HooverRowTest` (the row in an `HhTrayColumn` at 333 dp wide):
    - `dockedOffersStart`: "Hoover", "Docked · 86%"; `hoover_start` "Start", a button spoken "Start cleaning"; the group spoken "Hoover, docked, battery 86 percent.".
    - `cleaningHasNoButton`: "Cleaning · 60%"; `hoover_inactive` "Cleaning", no node with a click action; spoken "Hoover, cleaning, battery 60 percent.".
    - `idleAndPausedOfferStart`; `goingHomeHasNoButton`; `unknownOrAProblemHasNoButton` (the problem's line shows, two lines at most).
    - `aDoubleTapAsksOnce` (Review Focus 3): with the fake gated, two taps: one ask, `starts == 1`; the button reads "Starting…" until the gate opens.
    - `aFailedStartShowsItsLineUntilTheHooverMoves`: `NotStarted` → "The hoover didn’t start. Check the Eufy app." in `hoover_status`; still there at `START_LINE_MS − 1` (`compose.mainClock`), gone at `START_LINE_MS`; and gone at once when the activity turns `CLEANING`.
    - `leavingHomeCancelsTheStart`: removing the row mid-check cancels the fake's `start()`.
    - `theLongestLineFitsTwoLines`: the token line at 333 dp: `hoover_status` has at most 2 lines.
    - `theRowWatchesWhileShown`: the fake's watchers 1 while composed, 0 after.
  - `HooverCapabilityTest.aChosenHooverAddsTheRow`: a system with a state → one `TrayButton("hoover.start", 20)`; a null state → none.
  - `HooverRowScreenshotTest` (each `_dark` and `_light`, the row in an `HhTrayColumn` 333 wide): `hoover_row_docked`, `hoover_row_cleaning`, `hoover_row_starting`, `hoover_row_stuck`, `hoover_row_cant_reach`, `hoover_row_not_started`.

  Run `./gradlew :capability:hoover:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Images.** **New:** the 12 above. **Check against** "Row 2, Hoover" in the handoff's note 13.
```bash
git add capability/hoover
git commit -m "Add the Hoover row to the One-tap tray: its state and battery, and Start behind a fresh Adult or Admin PIN while it's docked, idle or paused"
```

---

### Task 9: Settings › Other integrations, the token field, the app wired in (spec §4.1, §4.5, §6; rulings 2, 15, 16, 18; R2, R3; Review Focus 2)

**Review:** opus. Where the token goes, what may be contacted, what is shown and kept.

**Files:**
- Modify `core/ui/.../Controls.kt`: `HhTextField(secret: Boolean = false)` using `internal val SecretKeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)` and `PasswordVisualTransformation()`.
- Create in `core/homeassistant/.../`: `HomeAssistantLinking.kt`, `ui/OtherIntegrationsPage.kt` (`OtherIntegrationsPage : SettingsPage`, `OtherIntegrations` stateless, `OtherIntegrationsHost`), `ui/HomeAssistantDimens.kt`; `di/HomeAssistantModule.kt` adds `@IntoSet SettingsPage`.
- Modify `app/src/main/res/xml/network_security_config.xml`: the comment gains "Home Assistant (11 §4.1) answers plain HTTP on the home network too; its token crosses the home Wi-Fi unencrypted, and https:// works if the household sets it up."
- Modify README (the five modules; "Other integrations 480" in the Settings order list); `LogHygieneTest` expects `HomeAssistantLinking.kt`.
- Test: `core/ui` `ControlsTest.kt`; `HomeAssistantLinkingTest.kt`, `ui/OtherIntegrationsTest.kt`, `ui/OtherIntegrationsScreenshotTest.kt` (a `TestAccess` with `CorePermissionSource`); `app` `NoBackupTest` (**pin**).

**Interfaces:**
- Consumes: Tasks 1–6; `AccessControl.mayManageConnections()`; `HhPageTitle`, `HhSectionLabel`, `HhTextField`, `HhButton`, `HhChoiceChip`, `HhMessageLine`, `CHECKING`.
- Produces:
  - `sealed interface CheckOutcome { data object Linked; data object PinRefused; data class Failed(val line: String, val field: CheckField) }`; `enum class CheckField { ADDRESS, TOKEN, NONE }`.
  - `@Singleton class HomeAssistantLinking internal constructor(ha: HomeAssistant, settings: HomeAssistantSettings, access: AccessControl, devices: Set<HomeAssistantDevice>, url: (String) -> HttpUrl?)`, `@Inject` with `::homeAssistantUrl`:
    - `suspend fun check(address: String, token: String): CheckOutcome` — the address per the rule (a refused one sends nothing); a blank token uses the saved one when the address is unchanged, else "Paste a token for Culvery first."; `ha.check(link)`; when the link differs from the saved one, `access.mayManageConnections()` before saving (refused: `PinRefused`, nothing saved); then auto-pick (ruling 16); failures per Copy (`TOKEN_REFUSED` → the Check line; `BLOCKED`, `UNREACHABLE`, `OFFLINE` → their §6 lines).
    - `suspend fun entities(domain: String): List<EntityChoice>`; `class EntityChoice(val id: String, val name: String)` (`name = friendlyName ?: id`; `toString` without either).
  - Tags: `settings_other`, `ha_status`, `ha_address`, `ha_token`, `ha_help`, `ha_check`, `ha_check_line`, `ha_device_{domain}`, `ha_choice_{domain}_{index}`, `ha_none_{domain}`, `ha_line_{domain}`, `ha_none_found_{domain}`.
  - `OtherIntegrationsPage`: `id = "other_integrations"`, `title = "Other integrations"`, `order = 480`.
- Behaviour: the page title with its line; "Home Assistant" section; the status ("Not linked yet…", "Linked.", or the problem line in `danger`); the address field (the saved address filled in); the token field (`secret`, in plain `remember`, empty, its placeholder saying whether one is saved; cleared after Linked); the help line in `mute`; Check ("Checking…" while it runs; one at a time); the Check line; after Linked, when the clipboard's primary clip text equals the token, `clearPrimaryClip()`; once linked, per device by `order`: its `title` as a section label, a chip per entity and "None" (`HhChoiceChip`, `Role.RadioButton`), its `line`, or `noneFound` when there are none. The page watches the devices' `watched` ids through `ha.watch` while shown and reads `entities` on open when linked.

- [ ] **Step 1: The failing tests.** Screen tests end with `assertClickablesHaveRoleAndLabel()`.
  - `ControlsTest.aSecretFieldIsMaskedAndNotLearned`: with `secret = true` and value "test-token", the node has `SemanticsProperties.Password` and its `EditableText` isn't "test-token"; `SecretKeyboardOptions.keyboardType == KeyboardType.Password` and `autoCorrectEnabled == false`; without `secret`, as today (**pin**).
  - `HomeAssistantLinkingTest` (the fixtures, `TestAccess`):
    - `aGoodCheckAsksForAnAdminPinThenSavesAndPicksTheOnlyOnes`: `asked == [["connections.manage"]]`; link saved; one vacuum and one climate: both chosen; with two climates the thermostat stays unchosen.
    - `aRefusedPinSavesNothing`: `TestAccess(Role.ADULT)` → `PinRefused`, the old link kept.
    - `aRefusedAddressSendsNothing` (Review Focus 2): `"http://ha.example.com"` → `Failed(address line, ADDRESS)`, `calls` empty, no PIN asked.
    - `aRefusedTokenSaysMakeANewOne`: `TOKEN_REFUSED` → "Home Assistant didn’t accept that token. Create a new one and paste it here." on `TOKEN`; nothing saved, no PIN asked.
    - `notHomeAssistantSaysCheckTheAddress`; `unreachableOfflineAndBlockedGiveTheirLines`.
    - `theSameLinkAsksNoPin`: re-checking the saved address with a blank token → `Linked`, no PIN, and `ha.problem` cleared.
    - `aChangedAddressNeedsAToken`: a changed address and a blank token → "Paste a token for Culvery first.".
    - `noneChosenLaterStaysNone`.
  - `OtherIntegrationsTest` (a stateless state, then the host over the fixtures and two fake devices):
    - `notLinkedSaysHow`: the status, the two fields with their labels, the help line verbatim, Check.
    - `checkingShowsCheckingAndChecksOnce`: two taps while the fake's `check` is gated → one `"check"`.
    - `linkedShowsThePickers`: "Hoover" with "Robot" and "None", "Thermostat" with "Hall" and "None"; tapping "None" calls `choose(null)`; `ha_line_climate` is "Hall · 20.8 °C · heating to 21.2 °C".
    - `noThermostatSaysSo`.
    - `theTokenIsNeverShownOrKept`: after a Linked Check, no node's text or description contains "test-token", and the token field is empty.
    - `theClipboardIsClearedOnlyWhenItHoldsTheToken`: the clipboard holding "test-token" is empty after Linked; holding "Milk" it keeps it.
    - `aProblemShowsInTheStatus`: `TOKEN_REFUSED` → the §6 token line in `ha_status`.
    - `thePageWatchesWhileShown`: `ha.watching` 1 while shown, 0 after.
  - `OtherIntegrationsScreenshotTest` (each `_dark` and `_light`): `settings_other_not_linked`, `settings_other_check_failed`, `settings_other_linked`, `settings_other_no_thermostat`, `settings_other_problem`.
  - `NoBackupTest` (**pin**).

  Run `./gradlew :core:ui:testDebugUnitTest --tests '*ControlsTest*' :core:homeassistant:testDebugUnitTest :app:testDebugUnitTest --tests '*NoBackup*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: Images.** **New:** the 10 above. **Must not change:** `settings_lights_*`, `controls_*`. **Check against** Settings › Lights (the section and button pattern).
```bash
git add core/ui core/homeassistant app README.md build-logic
git commit -m "Add Settings › Other integrations: link Home Assistant with an address and a token behind an Admin PIN, then choose the hoover and the thermostat"
```

---

### Task 10: Simplify the whole branch

**Review:** the controller.

- [ ] **Step 1:** Run the `simplify` skill over `git diff main...HEAD`. Look hardest at: `hooverState` and `heatingState` and the two confirm loops (one helper in `:core:homeassistant` if they read alike), the two settings stores, and `HaJson` against `HueJson` for anything worth sharing without touching Hue.
- [ ] **Step 2:** For each change, the affected module's tests, then the gate and the convention tests.
- [ ] **Step 3: Commit** (when anything changed): `git commit -am "Simplify the Home Assistant, hoover and heating code"`.

---

### Task 11: The emulator walk-through on `culvery-5`, the captures (spec §8.1, §8.2.2, §8.2.3)

**Review:** the controller, with the user at the emulator and the household's Home Assistant.

**Rules** (as Plan 10's Task 11 and Plan 7's Task 17): only the `culvery-5` AVD (`-no-snapshot-save -camera-front emulated`; check `adb -s "$S" emu avd name`); `./gradlew :app:assembleDebug` then `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`; never uninstall, never a Gradle install task with a device connected; the user types every Admin and Adult PIN (the debug sample PINs are Claude's), the address and the token; **the hoover runs only when the user says so**; nothing changes the thermostat; collect faults and ask before fixing. The address, token, entity ids and names never go into a file or commit (`[ha address]`); captures stay in `$LISTS`.

- [ ] **Step 0: The token** (the user, R5): the Culvery user already exists (non-admin, local network only); signed in as it, Profile › Security › Long-lived access tokens › Create token; the NAS's address is fixed on the router.
- [ ] **Step 1: The build on the AVD.**
- [ ] **Step 2: Walk the checklist** (one line each).

  **Not in this build, on purpose:** Stop and Return to dock; rooms and scenes; any heating control on the wall; Holiday mode (11b); Settings › Home buttons. **Design cuts to say again:** the tray edge at 1 dp (the header tray's) not 2 px; one row at the tray's top, the rest empty until 11b; the utility row's right part empty when there's no Shopping; Coming up gives way to the tray; the row's status wraps to two lines; the Start pill at the house 48 dp, not 36.
  1. Before linking: Home is as before; no tray.
  2. Settings › Other integrations: a wrong address, a public `http://` address, a bad token: each line; the real address and token: the Admin PIN, "Linked.", the token field empty, the hoover and the thermostat picked, the thermostat line.
  3. Home: the tray beside Shopping (and, with Shopping off, at the row's left); "Hoover", "Docked · {n}%" (the battery found by name); TalkBack reads the row and "Start cleaning".
  4. Start: a Child's PIN refused; then (when the user says) an Adult's: "Starting…", then "Cleaning · {n}%" with the inactive pill; Return to dock from the Eufy app: "Going home"; docked again: Start back.
  5. Home Assistant stopped briefly (the user, in Container Manager) or the AVD's network off: the line, then recovery without a tap.
  6. In Home Assistant, the Culvery token deleted: the token line on Home and Settings; a new token (Admin PIN) fixes it.
  7. Choose None for the hoover: the tray goes; choose it again: it's back.
- [ ] **Step 3: The captures (§8.2.2, §8.2.3).** `adb -s "$S" logcat -c`; `adb -s "$S" shell setprop log.tag.HaCapture DEBUG`; open Settings › Other integrations, Check, open Home; with the user, a Start and a return, and the bin emptying or mop washing if the station does it; `adb -s "$S" logcat -d -s HaCapture:D > "$LISTS/ha-capture.txt"`; `setprop log.tag.HaCapture INFO`. **The names check:** grep the file for the address, the token's first 12 characters, every entity id and every friendly name (the user's list, kept in `$LISTS`): nothing may match; else widen `HaJson.redact` (a failing case first), fix, recapture. Note which states the vacuum reported and the battery sensor's shape.
- [ ] **Step 4: The fault loop.** For each fault, with the user's go: a failing test, the fix, the gate, a commit naming it, `install -r`, a re-check.
- [ ] **Step 5: Put back** every setting; `adb -s "$S" emu kill`.
- [ ] **Step 6: Record** under "Plan 11a (Home Assistant, the hoover, the heating link): emulator walk-through", with no address, token, id or name.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 11a's emulator walk-through"
```

---

### Task 12: Fixtures from the captures; on the tablet (spec §7, §8.2.3, §8.3)

**Review:** the controller with the user. **Needs the SM-T510 on the cable.** 4e's tablet rules apply: save and restore every setting changed; signed releases with `adb -s <tablet> install -r`; never uninstall; the names check before any commit.

- [ ] **Step 1: Fixtures from the captures (failing first).** Rewrite `core/homeassistant/src/test/resources/ha/*.json` from `$LISTS/ha-capture.txt` with the sample ids and names; run `./gradlew :core:homeassistant:testDebugUnitTest :provider:hoover-homeassistant:testDebugUnitTest :provider:heating-homeassistant:testDebugUnitTest`. Expected: FAIL where the shapes differ (else say so). Fix the reader with the user's go, the gate, the names check, and commit: `git commit -am "Replace the Home Assistant fixtures with redacted answers from the household's instance"`.
- [ ] **Step 2: Install the release.** Sign it per `docs/setup/release.md`, keep `mapping.txt` in `culvery-notes/mappings/`, then `adb -s <tablet> install -r app/build/outputs/apk/release/app-release.apk`.
- [ ] **Step 3: Link and Start** (the user): Settings › Other integrations with the address and the token (Admin PIN); the pickers; Home's tray; Start the hoover from the wall with an Adult's PIN (when the user says), watching it leave the dock and the row follow; the thermostat line matches the Nest app.
- [ ] **Step 4: The log scan.** `adb -s <tablet> logcat -d -s HomeAssistantClient HomeAssistant HaHooverSystem HaHeatingSystem HomeAssistantLinking > "$LISTS/plan11a-log.txt"`, then grep it for the address, the token's first 12 characters, the entity ids and the names: no match.
- [ ] **Step 5: Record** under "Plan 11a: on the tablet": what worked and what didn't. No address, token, ids or names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 11a on the tablet"
```

---

### Task 13: Final review, merge, install

- [ ] **Step 1: The review.** Opus reviews the whole branch against the spec and this plan, the token, the PINs and the logs first. Then one fix wave, each fix with its failing test first and the gate.
- [ ] **Step 2: Finish** with `superpowers:finishing-a-development-branch`: on the user's say, `git merge --ff-only plan-11-home-hub` on `main`; the gate on `main`; never push without asking. In the follow-ups: move the design cuts (Task 11); note that §8.2.1's Away check is next, before 11b is planned; and add under "Feature ideas" the token's reach: "Culvery's Home Assistant token reaches everything the Culvery user can see. To narrow it: put the NAS and the tablet on a guest or IoT network, move Home Assistant to https://, and hide entities from the Culvery user where Home Assistant allows."
- [ ] **Step 3: Install** the release from the merged `main` on the tablet with `install -r`.

---

## Spec coverage (11a → tasks)

| Spec | Task |
|---|---|
| §2.1 Start only; §2.8 Home Assistant on the NAS; §2.9 nothing moves | 5, 8 |
| §3 the Culvery user, the entity names | 5, 11 |
| §4.1 the page, the token, the Admin PIN to save, the help text, Check, the pickers, auto-pick, the stores, plain HTTP | 1, 9 |
| §4.2 modules; `vacuum.start`; activities; battery by name, no registry; `set_preset_mode` | 1–6 |
| §4.3 the socket while in front and showing; backoff and the full read; REST commands, 5 s; the 30 s check | 2, 3, 5, 6 |
| §4.4 the tray, placement, contributions, the row, Start's rule, Start's fresh PIN, no tray without a button | 4, 7, 8 |
| §4.5 the thermostat's line; `setEco` built and tested | 6, 9 |
| §4.6 TalkBack | 4, 8 |
| §6 the 11a rows; privacy | 2–6, 8, 9 |
| §7 JSON, client, tray, access (Start's PIN, a Child refused, the link's Admin PIN, Settings admin-only), UI | 1–9 |
| §8.1, §8.2.2, §8.2.3, §8.3 (11a) | 11, 12 |
