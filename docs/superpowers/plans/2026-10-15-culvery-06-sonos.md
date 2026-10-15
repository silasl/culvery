# Culvery — Plan 6: Sonos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–15 are subagent tasks; Task 16 is the controller on the `culvery-5` AVD with the user and a real speaker; Task 17 needs the user, the household's speakers and the son's SM-T510.

**Goal:** Each Sonos room is a remote on a Music tab; Home shows the room that's playing with Play/Pause, Next and its volume; Shuffle everything and the household's Sonos Favourites start music in a group; and while the door card shows, every playing group goes quieter and comes back afterwards.

**Architecture:** `:core:plugin` gains `AlertQuieter` (a Hilt multibinding) and a loud alert (`AlertHost.show(loud = true)`, `quieted()`), run by the shell's `ShellAlerts`; Door marks its card loud and waits for `quieted()` before the chime. A new capability `:capability:music` owns the `MusicSystem` seam, the saved connection (DataStore `music_settings`), the Home-room rule, the 2-second checks, the doorbell lowering (`MusicQuieter`, room by room), the Home card, the Music tab, the Favourites sheet and Settings › Music. A new provider `:provider:music-sonos` finds the speakers by SSDP under a multicast lock and talks SOAP to port 1400 over OkHttp, reading answers with a plain-JVM `SonosXml`; it contacts nothing but a private IPv4 address on port 1400 and HTTPS stream art. There are no sample speakers (6 §3.4): tests use a test `MusicSystem`, walk-throughs the household's speakers. Music and Door never see each other: the shell runs the quieters.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2, OkHttp 4.12.0 (+ MockWebServer 4.12.0, already in the catalogue), `javax.xml.parsers` DOM, `java.net.DatagramSocket`, `WifiManager.MulticastLock`, `ConnectivityManager.activeNetwork`, `BitmapFactory` + `android.util.LruCache`; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new dependency**: MockWebServer is already `libs.okhttp.mockwebserver`, and there is no image library (ruling 15).

**Spec:** `docs/superpowers/specs/2026-10-15-culvery-6-sonos-design.md` at 1352ad9 (§1–§9, with the plan review's rulings in §2.2, §3.4, §4.4–§4.6, §8.1). Read it with this plan.
**Previous plan (format, patterns):** `docs/superpowers/plans/2026-10-14-culvery-05-ring-doorbell.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Tasks 16–17 update it).

**Plan series:** 1 · 2a · 2b-1 · 2b-2 · 3a · 4a · 4b · 4c · 4d-1 · 4d-2 · 4e · 5 the Ring doorbell (done, not yet merged) · **6 Sonos (this plan)**.

**Builds on:** branch `plan-6-sonos` at 1352ad9 (the spec after the plan review), cut from `plan-5-ring-doorbell`, in the worktree `C:\Users\si\code\siland\culvery-sonos`. Work only there; never touch `C:\Users\si\code\siland\culvery` or `C:\Users\si\code\siland\culvery-ring`. Never push. Rebased onto `main` once Plan 5 merges (the controller does that between tasks, not a subagent). File and line references were read at 4cc46c4 (the code is unchanged since).

| # | Task | Review |
|---|---|---|
| 1 | Music's icons in the icon font | sonnet |
| 2 | Loud alerts: `AlertQuieter`, `show(loud)`, `quieted()`, the shell runs and restores the quieters | **opus** (concurrency, timers) |
| 3 | Door: the card is loud and the chime waits for `quieted()` | sonnet |
| 4 | `:capability:music`: the contract, the home-network rule, the saved connection, `Speakers`, the capability and its tab rule | **opus** (the seam every later task uses) |
| 5 | The Home-room rule (held on the application scope), the tab order, and the checks (`MusicChecks`) | **opus** (timers) |
| 6 | `MusicQuieter`: lowering for the doorbell, room by room | **opus** (state across two calls) |
| 7 | `MusicActions`: commands, the row lines, Favourites, the §6 words | sonnet |
| 8 | `:provider:music-sonos` 1: `SonosXml`, the speaker-address rule, and the documented fixtures | sonnet |
| 9 | `:provider:music-sonos` 2: SOAP, `SonosClient`, the debug capture | sonnet |
| 10 | `:provider:music-sonos` 3: `SonosSystem` (layout, checks, commands, Shuffle everything, Favourites, art, the rescan, the household) | **opus** (the protocol, recovery) |
| 11 | `:provider:music-sonos` 4: SSDP, `SonosProvider`, the network check, cleartext, wiring into the app | **opus** (a system service, the network policy) |
| 12 | Art, the shared controls and the Home card | sonnet |
| 13 | The Music tab and the Favourites sheet | sonnet |
| 14 | Connecting: a typed address and `MusicConnect` | **opus** (what may be contacted, saving) |
| 15 | Settings › Music: the page, the address sheet, the usual room, the doorbell share | sonnet |
| 16 | The emulator walk-through on `culvery-5` with a real speaker by address; the protocol checks; the captures | controller + user |
| 17 | On the tablet: fixtures from the captures, release `install -r`, Find Sonos by scan, the lowering timed, final review, local merge, install | controller + user, **needs the SM-T510 and the speakers** |

**Task order and why:** the shell's seam first (2) and Door's one-argument change on it (3), so Door's tests guard the alert layer before Music binds a quieter. Music then goes bottom-up: the contract (4) before the logic that only needs a test double (5–7); the provider's parser (8) before the client that uses it (9), the system that uses the client (10), and the discovery and wiring that put it in the app (11). The screens are tested against plain values and a test system (12–13); connecting gets its own reviewer (14) before the page that uses it (15). The emulator (16) before the tablet (17), whose fixtures come from the emulator's captures.

## Rulings against the code

Where the spec leaves a choice open, or doesn't fit the code at 4cc46c4. The user's plan-review rulings (2026-10-15) are folded in: the shuffle order (12), the paused room on Home (8), per-room lowering (10, 30), no sample speakers (19).

1. **`AlertHost.show(loud: Boolean = false, content)`**: `loud` comes first so every `host.show { … }` keeps compiling with a trailing lambda; the spec writes `show(content, loud = false)`. **`quieted()` is an interface member** (`suspend fun quieted()`), so a test host can hold it; the spec writes it as an extension.
2. **The 500 ms run from the `show` that started quieting**, not from each `quieted()` call: a second ring's `quieted()` while the first card shows returns at once. **A quiet alert that replaces a loud one restores**, as a dismiss does. **Each quiet or restore pass waits for the one before**, so a dismiss while a quieter is still lowering (a slow speaker) restores after it finishes rather than before.
3. **Door awaits `quieted()` only when it is about to chime.** In quiet hours, or within 10 seconds of the last chime, the card is still loud (§2.3 "in quiet hours too") but nothing waits. **It awaits inside its lock**, so a Got it tap during those 500 ms waits for them.
4. **`MusicSystem` carries more than the spec's list:** `problem: StateFlow<MusicProblem?>` (the §6 line), `checkLayout()`, `checkPlayback()` (the capability decides when, §4.2; the provider does the reading), `playingNow()` and `roomVolume(room)` (fresh reads for the lowering, rulings 10 and 30), and `art(url)` (ruling 15). `groups` is a `StateFlow`. **A second seam, `MusicProvider`** (`descriptor`, `find()`, `check(address)`, `open(link, onMoved)`), lets Settings › Music, which the capability owns, find speakers without knowing Sonos.
5. **The connection is a DataStore file `music_settings`** (provider id, household id, address, usual room id, the doorbell share), as Door's `door_settings` (Plan 5 rulings 8–9): no `Connection` table exists to put it in. It sits under `files/datastore/`, already excluded from backup and transfer (§3.5). The usual room is kept by its Sonos id (`RINCON_…`), never its name; the rooms list stays in memory. `ProviderDescriptor("music.sonos", "Sonos", …)`.
6. **The household id comes from `DeviceProperties#GetHouseholdID`**: `ZoneGroupTopology` doesn't carry it. It is asked of the saved speaker at each layout check, so a reset system shows at the next one (§6).
7. **What's playing reads four things per group each check** (transport, position, media and group volume, in parallel): the station's name is only in `GetMediaInfo`'s `CurrentURIMetaData`. A group plays from its queue when its media URI starts `x-rincon-queue:`; then it shows title and artist and `canSkip` is true; anything else (a station, line-in, TV) is radio: the station (`GetMediaInfo`'s title) and the programme (`r:radioShowMd`, else `r:streamContent` unless it is a `ZPSTR_` placeholder), Next greyed.
8. **A room paused from Home stays on Home** until it stops or another room plays (§2.2 since the plan review), so Pause then Play resumes it; such a card shows the track with "paused". The spec's "Nothing playing" card is the usual room's when nothing was showing. **The memory lives in `MusicView.home`, a `StateFlow` on the application scope**, so it survives the card leaving composition (Settings over Home, another tab and back).
9. **"Home or the Music tab shows" means the Home card or the Music tab is composed**, read through `DisposableEffect`; with `AlertHost.inFront` for "Culvery is in front". A sheet or Settings drawn over Home doesn't stop the checks. Settings › Music reads the layout once as it opens, for its room picker.
10. **The lowering reads afresh which groups are playing** (`playingNow()`: one `GetTransportInfo` per group) instead of trusting the last check: on the Calendar or Door tab no checks run, and the bell must still lower the music.
11. **The automatic rescan runs at most once every 30 seconds.** §4.1's "scans once by itself" happens on the first failure; while the speakers stay unreachable, later checks report at once rather than scanning (5 s) every 2 s; the next scan after 30 s still finds a speaker that came back at a new address. Before scanning, the system re-reads the layout from the saved speaker, which is all a regrouped or moved leader needs.
12. **Shuffle everything (§4.4 since the plan review): `RemoveAllTracksFromQueue`, `AddURIToQueue`, `SetAVTransportURI` to the queue, `SetPlayMode SHUFFLE_NOREPEAT`, `Play`**: Sonos refuses a play mode (UPnP 712) while the source is a stream. It adds `x-rincon-playlist:<leader id>#A:TRACKS` with empty metadata, and its library check browses `A:TRACKS` with `RequestedCount` 1 and reads `TotalMatches`. These request shapes, and `SnapshotGroupVolume` before `SetGroupVolume` (14), come from SoCo's and node-sonos's use of the local API, not Sonos documentation: the tests pin them until Task 16 confirms them on the household's speakers and records the outcome.
13. **A Favourite's kind comes from its `r:resMD` class**: a container (`object.container…`) or a `x-rincon-cpcontainer:`/`x-rincon-playlist:` URI is `QUEUED`; anything else, a single track included, is `STREAM` (set as the transport, as a station is).
14. **`SetGroupVolume` (the group slider) is preceded by `SnapshotGroupVolume`**, as Sonos asks, so the rooms keep their ratio. The lowering doesn't use it (ruling 30).
15. **No image library.** Art comes as bytes through `MusicSystem.art(url)` on the provider's OkHttp client, is decoded off the main thread with `BitmapFactory` sampled to about 256 px, and up to 24 are kept in an `LruCache` in memory (§4.2). **Only two kinds of art are fetched:** a leader's own `http://<leader>:1400/getaa…` (a relative `/getaa…` path is made the leader's), and a stream's `https://` art. Any other `http://` art is no art. No redirect is followed.
16. **Cleartext:** the speakers answer plain HTTP on port 1400 at whatever address the router gives them, and Android's network security config can't name an address range. The app gets `res/xml/network_security_config.xml` allowing cleartext in its base config and forbidding it for `googleapis.com`, `google.com` and `open-meteo.com`, the internet services Culvery uses. **`InternetHttpsTest` keeps every literal URL in main sources HTTPS** (XML namespace names excepted), so the base config can't silently cover a future client.
17. **A typed address is four numbers in a private range** (10/8, 172.16/12, 192.168/16, 169.254/16), so the wall never contacts anything off the home network by mistake (§6 privacy); leading zeros are dropped and the provider adds `:1400`. Anything else says how to type it.
18. **The capture switch (§8.2) is a log property:** `adb shell setprop log.tag.SonosCapture DEBUG` turns it on, `… INFO` off. `SonosCapture` exists in `src/debug` (redacts with `SonosXml.redact`, logs at debug) and `src/release` (does nothing); `SonosCaptureSourceTest` keeps the release one empty, as `FakeRingsSourceTest` does.
19. **No sample speakers** (§3.4 since the plan review): no `:provider:music-fake`, no **Use sample speakers**. Find Sonos and Enter a speaker's address use the bound provider (Sonos); screenshots and tests use plain values and `TestMusicSystem`.
20. **One rule decides what may be contacted: `isHomeNetworkHost(host)`** (Task 4, the capability: a private IPv4 literal) and, in the provider, **`isSpeakerAuthority(host, port)`** (that, and port 1400). `SonosClient` refuses any other address before it sends anything (a `NotASpeaker` I/O failure, so a bad saved address leads to a rescan, never a request); topology members whose `Location` fails it are dropped; an SSDP answer counts only when its `LOCATION` host is the packet's sender and passes it. Tests that run speakers on localhost pass their own accept rule.
21. **Logs:** `MusicActions` logs `"${command.name} failed (${e::class.simpleName})"` with an enum `MusicCommand`; `LogHygiene.ALLOWED` gains `command.name`. Nothing logs a room, a title, a Favourite or an address (§6).
22. **Copy-scan allowlist:** three protocol literals with straight quotes (the SOAP envelope, the action element, SSDP's search) and RenderingControl's channel name `Master` go in `core/ui/src/test/resources/copy-scan-allow.txt`, each "sent to the speakers, never shown". No raw string in main code ends in a run of four quotes (`CopyScan` closes a raw string at the first `"""`): a closing quote inside a regex is written `["]`.
23. **Offline is Android's answer when a call fails:** `ConnectivityManager.activeNetwork == null` (`ACCESS_NETWORK_STATE`) gives "The tablet is offline. Check the Wi-Fi." without a rescan.
24. **Settings › Music is page id `music`, order 450** (Calendars 400, Door 500). **The Home card is `music.now`, REGULAR, priority 30**: after Today (100), Coming up (50) and the forecast (40), so it takes row 2, column 3.
25. **The room picker and the doorbell slider save when let go**, as Door's chime volume does; the share moves in 5 % steps (0, 5 … 50).
26. **Timeouts:** OkHttp connect, read and whole-call 3 s (§6), no retry on connection failure, no redirects; an answer over 4 MB (art 2 MB) is a failed call.
27. **XML with a `<!DOCTYPE` or `<!ENTITY` is refused before parsing**, every time, the escaped DIDL that is parsed a second time included: a speaker never needs either.
28. **Answers are parsed on `Dispatchers.Default`**: Favourites and Settings call from composition scopes on the main thread.
29. **Restoring tolerates a step of 1** (Sonos may round a volume it was given), and a failed restore is tried once more (§4.6).
30. **The lowering is per room (§4.6 since the plan review):** each room of each playing group is read, remembered as `(v, set)` before its command goes, and set with `SetVolume` on that room; restore puts each room back the same way. A group whose rooms can't all be read is left alone. A two-room group keeps its balance, even from 0 %.

Nothing in this plan needs a deprecated API. Flagged and avoided: `ConnectivityManager.getActiveNetworkInfo()`/`NetworkInfo` (use `activeNetwork`), `RequestBody.create(MediaType, String)` and `MediaType.parse` (use `toRequestBody`, `toMediaType`), `androidx.compose.ui.platform.LocalLifecycleOwner` (not needed), `Modifier.semantics { invisibleToUser() }` (use `hideFromAccessibility()`). If the compiler shows a deprecation warning on a line you wrote, **stop and ask**.

## Global Constraints

**From the spec, verbatim:**

- §2.2 "Home shows the playing room; with several playing, the usual room if it's one of them, else the one that started most recently; with none playing, the usual room. A room paused from Home stays on Home until it stops or another room plays, so Pause then Play resumes it."
- §2.3 "every playing group drops to a share of its volume (Settings, default 20 %) while the door card shows, in quiet hours too, and goes back when the card closes. A group someone changed meanwhile is left as they set it."
- §2.4 "shown as Sonos groups them, one row each, with a group volume and each room's own. Grouping and ungrouping stay in the Sonos app."
- §2.6 "everyone can use the controls … Settings › Music is admin-only like the rest of Settings."
- §2.7 "Culvery talks to the speakers directly on the home Wi-Fi … No Sonos sign-in, nothing secret stored."
- §3.1 "`id = "music"`, label "Music", icon `speaker`, rail order 30. `hasTab` is true once a `music.sonos` connection exists."
- §3.3 "When a loud alert shows, the shell runs every quieter's `quiet()` at once. `quieted()` returns when they have all finished, or after 500 ms, whichever comes first. When the alert is dismissed, the shell runs every `restore()`. A loud alert that replaces a loud one runs nothing again." "Door never sees Music."
- §3.4 "tests and screenshots use plain values and a test `MusicSystem`, and walk-throughs use the household's real speakers by address".
- §4.1 "When the saved address stops answering, Culvery scans once by itself, keeps the first speaker of the same household, and saves its address. Only then does it report a problem." (with ruling 11)
- §4.2 "While Culvery is in front and Home or the Music tab shows: each group's coordinator every **2 seconds** … Group layout … every **30 seconds**, and when the Music tab opens. After a command: that group at once. Not in front, or neither screen showing: no checks. Art … cached in memory only."
- §4.3 "Each goes to the group's coordinator. The screen changes at once and the next check corrects it if the speaker disagrees. Next is greyed out when `canSkip` is false (radio streams)."
- §4.4 "`RemoveAllTracksFromQueue`; `AddURIToQueue` with the library container (`A:TRACKS`); `SetAVTransportURI` to the group's queue (`x-rincon-queue:<coordinator>#0`); `SetPlayMode SHUFFLE_NOREPEAT`; `Play`." "An empty library browse stops before step 1."
- §4.5 "A `STREAM` favourite … is set as the group's transport URI with its metadata and played. A `QUEUED` one … replaces the queue, as in §4.4 steps 1, 2, 3 and 5, without changing the play mode."
- §4.6 "for each group playing at that moment, and each room in it, read the room's volume `v`, remember `(v, set)` with `set = round(v × share)` … then set it. Rooms are remembered before the command goes … Groups are lowered in parallel. A group that fails is skipped. If groups are already lowered, it does nothing." "room by room, read its volume. If it is within 1 of `set`, put it back to `v`; otherwise leave it. A failed restore is tried once more. Then forget them all." "Kept in memory only."
- §5.1 "**Shuffle everything**, only while that group is stopped or paused".
- §5.2 "One row per group, playing groups first, then the rest in Sonos's order." "Open to everyone, no PIN. No rail badge."
- §6 privacy: "Only speakers on the home network are contacted; nothing goes to the internet except art for a stream that names its own URL. Logs carry the command and its outcome only: no room names …, track titles, favourites or addresses. The rooms list is kept in memory, not stored."
- §3.5 "No new rule." (backup)

**Copy, verbatim from the spec (curly apostrophes in code):** "Music", "Nothing playing", "Shuffle everything", "Favourites", "Rooms", "No Sonos Favourites yet. Add them in the Sonos app.", "Sonos", "Find Sonos", "Enter a speaker’s address", "Usual room", "When the doorbell rings, lower music to", "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.", "The tablet is offline. Check the Wi-Fi.", "Sonos didn’t do that. Try again.", "Sonos couldn’t play this here.", "Sonos has no music library. Add it in the Sonos app.", "Culvery found a different Sonos system. Find Sonos again in Settings › Music."; TalkBack "Kitchen, playing Song by Artist", "Kitchen, paused", "Kitchen, nothing playing", "Pause Kitchen", "Play Kitchen", "Next track in Kitchen", "Shuffle everything in Kitchen", "Kitchen volume, 30 percent".
**Not in the spec, written here** (for the end-of-v1 design walk-through): "Looking for the speakers…", "Getting your Favourites…", "Favourites for Kitchen" (spoken), "show each room’s volume"/"hide each room’s volume" (the Rooms chip's action), "play in Kitchen" (a Favourite's action), "Looking for Sonos…", "Not connected yet. Find Sonos, or enter a speaker’s address.", "Connected.", "Found Sonos." and "Connected to Sonos." (toasts), "No Sonos speakers answered. Check they’re on, or enter a speaker’s address.", "Shown in the Sonos app under About My System.", "Speaker’s address", "192.168.1.20" (placeholder), "Connect to this speaker", "Checking…", "Type the address as four numbers, like 192.168.1.20.", "No Sonos speaker answered at that address. Check it in the Sonos app under About My System.", "The rooms appear here once Sonos is found.", "Doorbell", "20 % of its volume", "0 % (muted)", "Radio station, programme" as the spoken form of radio ("Kitchen, playing Culvery FM, The Breakfast Show").

**From the house rules:**

- Package root `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. **No new library; never change a version.** New modules use libraries already in `gradle/libs.versions.toml`.
- **Deprecated APIs:** none without asking (see above).
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test`, `check` or `build`, except the JVM-only `build-logic` build: `./gradlew -p build-logic :convention:test` (Tasks 4 and 7 change its tests). A task runs its module's tests while working (`./gradlew :capability:music:testDebugUnitTest --tests '*MusicQuieterTest*'`); every task ends with the full gate before its commit.
- **Tests that can fail:** every new test fails before its change, and its step says why ("Fails today: …"); a "Fails if" line names the mutation it catches. A test that passes before its change is labelled **pin**, with what it pins. Timer tests use a fake clock (`TestScope`'s scheduler) and never sleep; each checks 1 ms before its boundary and at it. Network tests use MockWebServer on localhost with short client timeouts; none touches a real speaker.
- **Screenshots:** baselines in `<module>/src/test/screenshots/`. Before re-recording, `./gradlew <module>:compareRoborazziDebug --tests "<class pattern>"`, open each `*_compare.png` under `<module>/build/outputs/roborazzi/`, and check each difference against the step's list; then `./gradlew <module>:recordRoborazziDebug --tests "<class pattern>"`. Open every new image (the Read tool shows it). **The baseline check:** write the step's expected names one per line to `$TMP/expected-baselines.txt`, then
```bash
git status --porcelain -- "*.png" | sed 's#.*/##; s#\.png$##' | sort -u > "$TMP/changed.txt"
echo "changed but not listed:"; comm -23 "$TMP/changed.txt" <(sort -u "$TMP/expected-baselines.txt")
echo "listed but unchanged:"; comm -13 "$TMP/changed.txt" <(sort -u "$TMP/expected-baselines.txt")
```
Both lists must be empty; anything in either is a stop and report.
- **Copy** names what the user does; errors say what went wrong and how to fix it; curly quotes and apostrophes (’ ‘ “ ”) in every string; "—", "›", "…" as written. `CopyScanTest` fails on a straight quote or apostrophe in copy and on the words master, kiosk, sync, read-only, assign, feed. Errors, toasts and sentences end with a full stop; labels, buttons and chips don't.
- **Logs:** `LogHygieneTest` (`build-logic/convention/src/test/kotlin/LogHygieneTest.kt`): every `Log.w`, `Log.e`, `Log.wtf`, `Log.println` in `main`/`release` passes a tag and one literal message interpolating only `::class.simpleName`, a `.code`, or an id in `LogHygiene.ALLOWED`. Never log a room's name, a title, a Favourite, an address or a household id. The capture (ruling 18) is `src/debug` only.
- **Layout numbers are named**, in the module's dimens object (`MusicDimens`). Tests may use literal dp.
- **Icons** come from `Icons` (`core/ui/.../Icons.kt`); a new one is added there, to `tools/fonts/icons.txt`, and to the subset font (`tools/fonts/README.md`).
- **Comments say what the code can't**, one line by default; spec references as `(6 §4.6)`. No comment names this plan's task or ruling numbers: cite the spec's section or give the reason. No marketing words.
- **Robolectric and waiting:** never `runBlocking { flow.first { … } }` right after `setContent` or a click: the main looper only runs while the test idles. Wait as `compose.waitUntil(5_000) { compose.waitForIdle(); condition() }`. A test that reaches `android.util.Log` runs under Robolectric (`@RunWith(AndroidJUnit4::class)`); `SonosXmlTest` deliberately doesn't (§3.2 "Plain JVM").
- **No personal data in files:** the fixtures use generic rooms (Kitchen, Lounge, Dining room, Bedroom) until the captures, then `[room n]` and `[person]`; never the household's names, its addresses or its household id.
- **Write every file as UTF-8.**
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on `plan-6-sonos`; never push. **Never** `git checkout -- .` or `git restore .`; to undo your own change, restore only the files you changed, after `git diff --name-only`.

## Review Focus

The five failure modes the spec implies, most likely to bite the household, that the spec's own test list doesn't cover; each is pinned by a test in its owning task:

1. **Pause on Home, open Settings or another tab, come back, then Play.** Expected: Play resumes the room just paused, not the usual room (ruling 8); once that room is stopped, Home falls back to the spec's rule.
   - Task 5 `HomePickTest.aPausedGroupHomeShowedStaysUntilItStops`, `MusicViewTest.pausingTheShownGroupKeepsItOnHome` and `MusicViewTest.theRoomPausedFromHomeSurvivesTheCardLeaving`.
2. **The bell rings while the Calendar or Door tab is open and music plays** (no checks running, so the last-known state is stale or empty). Expected: the playing groups still go quieter.
   - Task 6 `MusicQuieterTest.lowersWhatIsPlayingNowNotWhatTheLastCheckSaw`; Task 10 `SonosSystemTest.playingNowAsksEachLeaderAfresh`.
3. **The router gives the speakers new addresses overnight.** Expected: the next check finds the household again by scan, saves the new address, and no status line ever shows.
   - Task 10 `SonosSystemTest.aMovedSpeakerIsFoundAndSavedWithoutAProblem`; Task 4 `SpeakersTest.aNewAddressForTheSameHouseholdKeepsTheOpenSystem`.
4. **Something on the network that isn't a home speaker** (a typed "192.168.1.20:1400", "sonos.local" or "8.8.8.8"; a topology or SSDP answer naming an internet address; a speaker's art pointing off the network). Expected: a line saying how to type it, and nothing contacted off the home network.
   - Task 14 `SpeakerAddressTest.onlyFourNumbersInAPrivateRangeAreASpeakerAddress` and `MusicConnectTest.aMistypedAddressContactsNothing`; Task 8 `SonosXmlTest.aMemberOffTheHomeNetworkIsDropped`; Task 9 `SonosClientTest.anAddressOffTheHomeNetworkIsNeverContacted`; Task 10 `SonosSystemTest.onlyALeadersOwnArtOrHttpsArtIsFetched`; Task 11 `SsdpTest.anAnswerPointingElsewhereIsDropped`.
5. **The door card closes while a quieter is still lowering a slow speaker.** Expected: the restore runs after that lowering finishes, so the music comes back instead of staying low.
   - Task 2 `ShellAlertsTest.aDismissDuringASlowQuietRestoresAfterIt`; Task 6 `MusicQuieterTest.aRestoreWaitsForTheQuietItUndoes`.

The spec's own tests (§7) each have their task: the Sonos XML fixtures (8), each command's SOAP body and header, Shuffle everything's order, stream and queued Favourites, the rescan, a different household (9, 10), the Home-room rule (5), the lowering with its balance, drift and retry (6), the checks (5, 12, 13), the UI with its screenshots and TalkBack words (12, 13, 15), the alert layer (2) and Door's wait (3). Also pinned: escaped room and Favourite names (8), DOCTYPE and entities refused (8), the 3-second timeouts and no redirects (9), offline without a scan (10), the 30-second rescan gap (10), the capture's redaction (8, 9), cleartext only off the internet services and HTTPS everywhere else (11).

---

## File Structure

```
settings.gradle.kts                                              (Tasks 4, 8: include the two modules)
gradle/libs.versions.toml                                        (unchanged)
build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt   (Task 4: the music family, pins)
build-logic/convention/src/test/kotlin/LogHygieneTest.kt         (Task 7: ALLOWED gains command.name)
core/ui/src/test/resources/copy-scan-allow.txt                   (Tasks 9, 11: protocol literals and "Master")
tools/fonts/icons.txt, core/ui/src/main/res/font/material_symbols_rounded.ttf, core/ui/.../Icons.kt   (Task 1)
core/plugin/.../Alerts.kt                                        (Task 2: AlertQuieter, loud, quieted)
app/build.gradle.kts                                             (Tasks 4, 11)
app/src/main/AndroidManifest.xml, app/src/main/res/xml/network_security_config.xml (create)   (Task 11)
app/src/main/java/uk/co/siland/culvery/shell/ShellOverlays.kt (Task 2)   di/AppModule.kt (Task 2)
app/src/test/... ShellAlertsTest (Task 2), AppSourceTest (Task 4), CleartextTest, InternetHttpsTest (create, Task 11)
capability/door/.../DoorAlerts.kt (Task 3)   capability/door/src/test/.../TestDoor.kt (Tasks 2, 3), DoorAlertsTest.kt (Task 3)
capability/music/                                                (create, Tasks 4–7, 12–15)
  build.gradle.kts   src/test/resources/robolectric.properties
  src/main/java/uk/co/siland/culvery/capability/music/
    MusicContract.kt   HomeNetwork.kt   MusicSettings.kt   Speakers.kt   MusicCapability.kt   MusicWords.kt   di/MusicModule.kt (Task 4)
    HomePick.kt   MusicView.kt   MusicChecks.kt (Task 5)
    MusicQuieter.kt (Task 6)
    MusicActions.kt (Task 7)
    ui/MusicDimens.kt   ui/Art.kt   ui/MusicParts.kt   ui/MusicCard.kt (Task 12)
    ui/MusicTab.kt   ui/FavouritesSheet.kt (Task 13)
    SpeakerAddress.kt   MusicConnect.kt (Task 14)
    ui/MusicSettingsPage.kt (Task 15)
  src/test/java/uk/co/siland/culvery/capability/music/
    TestMusic.kt   HomeNetworkTest.kt   MusicSettingsTest.kt   SpeakersTest.kt   MusicCapabilityTest.kt (Task 4)
    HomePickTest.kt   MusicViewTest.kt   MusicChecksTest.kt (Task 5)   MusicQuieterTest.kt (Task 6)   MusicActionsTest.kt (Task 7)
    MusicWordsTest.kt   ui/ArtCacheTest.kt   ui/MusicCardTest.kt   ui/MusicCardScreenshotTest.kt (Task 12)
    ui/MusicTabTest.kt   ui/FavouritesSheetTest.kt   ui/MusicTabScreenshotTest.kt (Task 13)
    SpeakerAddressTest.kt   MusicConnectTest.kt (Task 14)
    ui/InSettingsPane.kt   ui/MusicSettingsPageTest.kt   ui/MusicSettingsScreenshotTest.kt (Task 15)
provider/music-sonos/                                            (create, Tasks 8–11)
  build.gradle.kts   src/main/AndroidManifest.xml (Task 11)
  src/main/java/uk/co/siland/culvery/provider/music_sonos/
    SpeakerAuthority.kt   SonosXml.kt (Task 8)   Http.kt   Soap.kt   SonosClient.kt (Task 9)   SonosSystem.kt (Task 10)
    SonosDiscovery.kt   Network.kt   SonosProvider.kt   di/SonosModule.kt (Task 11)
  src/debug/java/.../SonosCapture.kt   src/release/java/.../SonosCapture.kt (Task 9)
  src/test/java/.../SonosFixtures.kt   SonosXmlTest.kt (Task 8)   FakeSpeaker.kt   SonosClientTest.kt   SonosCaptureSourceTest.kt (Task 9)
    SonosSystemTest.kt (Task 10)   SsdpTest.kt   SonosDiscoveryTest.kt   SonosProviderTest.kt (Task 11)
  src/testDebug/java/.../SonosCaptureTest.kt (Task 9)
docs/superpowers/plans/2026-09-23-plan1-followups.md            (Tasks 16, 17)
```

`…` stands for the module's package directory (`src/main/java/uk/co/siland/culvery/<module path>`); each step spells out the full path the first time it names a file.

---

### Task 1: Music's icons (§3.1, §5; the icon font)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Icons.kt`, `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (regenerated); maybe `core/ui/src/test/java/uk/co/siland/culvery/core/ui/IconFontTest.kt` (`NO_FILLED_FORM`)

**Interfaces:**
- Produces: `Icons.MUSIC_NOTE = "music_note"`, `Icons.PAUSE = "pause"`, `Icons.PLAY_ARROW = "play_arrow"`, `Icons.SEARCH = "search"`, `Icons.SHUFFLE = "shuffle"`, `Icons.SKIP_NEXT = "skip_next"`, `Icons.SPEAKER = "speaker"`, `Icons.STAR = "star"`.

- [ ] **Step 1: The failing test**

Add the eight `const val`s to `Icons`, keeping the object's alphabetical order (`MOBILE`, `MUSIC_NOTE`, `PARTLY_CLOUDY_DAY`, `PARTLY_CLOUDY_NIGHT`, `PAUSE`, `PERSON`, `PLAY_ARROW`, …, `SCHEDULE`, `SEARCH`, `SETTINGS`, `SHUFFLE`, `SKIP_NEXT`, `SPEAKER`, `STAR`, `SUNNY`, …), and the same eight names to `tools/fonts/icons.txt` in its alphabetical order.
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*IconFontTest*'`
Expected: FAIL in `everyNamedIconIsALigatureInTheFont` naming the eight: the committed subset doesn't have them.

- [ ] **Step 2: The subset**

Follow `tools/fonts/README.md` steps 3–4 (the pre-4c full font from `git cat-file blob 608ce00:…`). All eight are long-standing Material Symbols, so that font should have them. If the subset script says it has no ligature for any, the README's step 3 needs Google's current font from GitHub: **that is a download — stop and ask the user before fetching it**, naming the file, its source and its size.

- [ ] **Step 3: Run it to see it pass**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*IconFontTest*'`
Expected: PASS. If `everyNamedIconKeepsItsFilledForm` says one has no filled form in the full font, add it to `NO_FILLED_FORM`.

- [ ] **Step 4: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL, no image changed (the font only gained glyphs).
```bash
git add core/ui/src tools/fonts/icons.txt
git commit -m "Add the music icons to the icon font"
```

---

### Task 2: Loud alerts and the quieters (§3.3; rulings 1, 2; Review Focus 5)

**Review:** opus.

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Alerts.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellOverlays.kt:49-90` (`ShellAlerts`)
- Modify: `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt` (`@Multibinds` quieters)
- Modify: `capability/door/src/test/java/uk/co/siland/culvery/capability/door/TestDoor.kt:55-72` (`FakeAlertHost` compiles against the new interface)
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ShellAlertsTest.kt`

**Interfaces:**
- Consumes: `@ApplicationScope CoroutineScope` (`:core:plugin`).
- Produces (package `uk.co.siland.culvery.core.plugin`):
  - `interface AlertQuieter { suspend fun quiet(); suspend fun restore() }`
  - `const val QUIET_CAP_MS = 500L`
  - `interface AlertHost { val inFront: StateFlow<Boolean>; fun show(loud: Boolean = false, content: @Composable () -> Unit); suspend fun quieted(); fun dismiss() }`
  - `:app`: `ShellAlerts @Inject constructor(quieters: Set<@JvmSuppressWildcards AlertQuieter>, @ApplicationScope scope: CoroutineScope)`

- [ ] **Step 1: Write the failing tests**

Replace `app/src/test/java/uk/co/siland/culvery/shell/ShellAlertsTest.kt` with:
```kotlin
package uk.co.siland.culvery.shell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import uk.co.siland.culvery.core.plugin.AlertQuieter
import uk.co.siland.culvery.core.plugin.QUIET_CAP_MS

/** A quieter that writes what it was asked into [log]; [gate] holds its quiet() until completed. */
private class RecordingQuieter(private val name: String, private val log: MutableList<String>) : AlertQuieter {
    var gate: CompletableDeferred<Unit>? = null
    var fails = false

    override suspend fun quiet() {
        log += "$name quiet"
        gate?.await()
        if (fails) throw IOException("the speaker went away")
        log += "$name quieted"
    }

    override suspend fun restore() {
        log += "$name restore"
    }
}

@RunWith(AndroidJUnit4::class)
class ShellAlertsTest {
    private fun alerts(vararg quieters: AlertQuieter, scope: CoroutineScope = CoroutineScope(SupervisorJob())) = ShellAlerts(quieters.toSet(), scope)

    private val nothing: @Composable () -> Unit = {}

    /** Fails if show keeps the old content, or dismiss leaves one behind. */
    @Test
    fun anAlertReplacesTheLastAndDismissClearsIt() {
        val alerts = alerts()
        val first: @Composable () -> Unit = {}
        val second: @Composable () -> Unit = {}
        alerts.show(content = first)
        alerts.show(content = second)
        assertThat(alerts.content.value).isSameInstanceAs(second)
        alerts.dismiss()
        assertThat(alerts.content.value).isNull()
    }

    /** 6 §3.3 "at once". Fails if a quieter is skipped, waits for the one before it, or quieted() returns before the slowest finished. */
    @Test
    fun aLoudAlertRunsEveryQuieterAtOnceAndQuietedWaitsForThem() = runTest {
        val log = mutableListOf<String>()
        val first = RecordingQuieter("first", log).apply { gate = CompletableDeferred() }
        val second = RecordingQuieter("second", log)
        val alerts = alerts(first, second, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        var quieted = false
        launch {
            alerts.quieted()
            quieted = true
        }
        runCurrent()
        // The second ran while the first was still held.
        assertThat(log).containsExactly("first quiet", "second quiet", "second quieted").inOrder()
        assertThat(quieted).isFalse()
        first.gate!!.complete(Unit)
        runCurrent()
        assertThat(quieted).isTrue()
    }

    /** 6 §3.3: a hung quieter holds the chime back 500 ms at most. Fails at 499 if the cap is shorter, at 500 if longer. */
    @Test
    fun quietedReturnsAt500MsWhenAQuieterHangs() = runTest {
        val hung = RecordingQuieter("hung", mutableListOf()).apply { gate = CompletableDeferred() }
        val alerts = alerts(hung, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        var returnedAt: Long? = null
        launch {
            alerts.quieted()
            returnedAt = testScheduler.currentTime
        }
        advanceTimeBy(QUIET_CAP_MS - 1)
        runCurrent()
        assertThat(returnedAt).isNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(returnedAt).isEqualTo(QUIET_CAP_MS)
    }

    /** Ruling: the 500 ms run from the show, not from each call. Fails if a quieted() asked 300 ms in waits until 800. */
    @Test
    fun theCapRunsFromTheShowNotFromTheCall() = runTest {
        val hung = RecordingQuieter("hung", mutableListOf()).apply { gate = CompletableDeferred() }
        val alerts = alerts(hung, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        advanceTimeBy(300)
        runCurrent()
        var returnedAt: Long? = null
        launch {
            alerts.quieted()
            returnedAt = testScheduler.currentTime
        }
        advanceTimeBy(QUIET_CAP_MS - 300 - 1)
        runCurrent()
        assertThat(returnedAt).isNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(returnedAt).isEqualTo(QUIET_CAP_MS)
    }

    /** Fails if dismiss forgets a restore, or restores a quieter for a quiet alert. */
    @Test
    fun dismissRunsEveryRestore() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("a", log), RecordingQuieter("b", log), scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        log.clear()
        alerts.dismiss()
        runCurrent()
        assertThat(log).containsExactly("a restore", "b restore")
    }

    /** 6 §3.3: a second ring while the card shows quietens nothing again. Fails if replacing a loud alert re-runs quiet(). */
    @Test
    fun aLoudAlertOverALoudOneDoesNotQuietAgain() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("music", log), scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        alerts.show(loud = true, content = nothing)
        runCurrent()
        var quieted = false
        launch {
            alerts.quieted()
            quieted = true
        }
        runCurrent()
        assertThat(quieted).isTrue()
        alerts.dismiss()
        runCurrent()
        assertThat(log).containsExactly("music quiet", "music quieted", "music restore").inOrder()
    }

    /** Fails if a quiet alert lowers anything, or quieted() waits when nothing is loud. */
    @Test
    fun aQuietAlertNeverQuietens() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("music", log), scope = backgroundScope)
        alerts.show(content = nothing)
        var quieted = false
        launch {
            alerts.quieted()
            quieted = true
        }
        runCurrent()
        alerts.dismiss()
        runCurrent()
        assertThat(quieted).isTrue()
        assertThat(log).isEmpty()
    }

    /** Ruling: a quiet alert replacing a loud one ends the loud one, so the music comes back. */
    @Test
    fun aQuietAlertReplacingALoudOneRestores() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("music", log), scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        alerts.show(content = nothing)
        runCurrent()
        assertThat(log).containsExactly("music quiet", "music quieted", "music restore").inOrder()
    }

    /** Review Focus 5. Fails if restore runs while the slow quiet() is still lowering: the music would stay low. */
    @Test
    fun aDismissDuringASlowQuietRestoresAfterIt() = runTest {
        val log = mutableListOf<String>()
        val slow = RecordingQuieter("music", log).apply { gate = CompletableDeferred() }
        val alerts = alerts(slow, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        alerts.dismiss()
        runCurrent()
        assertThat(log).containsExactly("music quiet")
        slow.gate!!.complete(Unit)
        runCurrent()
        assertThat(log).containsExactly("music quiet", "music quieted", "music restore").inOrder()
    }

    /** One failing quieter (a speaker gone) neither stops the others nor holds quieted() past their finish. */
    @Test
    fun aFailingQuieterDoesNotStopTheOthers() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("gone", log).apply { fails = true }, RecordingQuieter("music", log), scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        var quieted = false
        launch {
            alerts.quieted()
            quieted = true
        }
        runCurrent()
        assertThat(quieted).isTrue()
        assertThat(log).containsExactly("gone quiet", "music quiet", "music quieted")
        alerts.dismiss()
        runCurrent()
        assertThat(log).containsAtLeast("gone restore", "music restore")
    }

    /** Fails if inFront is true before the screen resumes or stays true after it pauses (5 §2.7). */
    @Test
    fun inFrontFollowsTheScreenItObserves() {
        val alerts = alerts()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().lifecycle.addObserver(alerts)
        assertThat(alerts.inFront.value).isFalse()
        controller.setup()
        assertThat(alerts.inFront.value).isTrue()
        controller.pause()
        assertThat(alerts.inFront.value).isFalse()
    }

    /** Fails if an instance that hands over to the home task in onResume still writes inFront. */
    @Test
    fun anInstanceThatHandsOverInOnResumeWritesNothing() {
        val alerts = alerts()
        var handedOver = false
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val lifecycle = controller.get().lifecycle
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                handedOver = true
            }
        })
        lifecycle.addObserver(alerts.unlessHandedOver { handedOver })
        controller.setup()
        assertThat(alerts.inFront.value).isFalse()
        controller.pause()
        assertThat(alerts.inFront.value).isFalse()
    }
}
```
Fails today: `ShellAlerts` takes no quieters, `AlertQuieter`, `QUIET_CAP_MS` and `show(loud = …)` don't exist (doesn't compile).
Fails if: a quieter is skipped or waits for another; quieted() ignores the cap, measures it from the call, or waits for a quiet alert; a loud-over-loud re-quietens; a restore overtakes its quiet; a throwing quieter cancels its siblings.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShellAlertsTest*'`
Expected: FAIL to compile on `AlertQuieter`, `QUIET_CAP_MS`, `loud`.

- [ ] **Step 3: The seam**

Replace the body of `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Alerts.kt` below its imports with:
```kotlin
/**
 * Something loud the shell turns down while a loud alert shows (6 §3.3), bound `@IntoSet` by a capability: Music lowers
 * the playing groups for the doorbell. Neither call may assume the other ran; each may fail, which the shell logs.
 */
interface AlertQuieter {
    suspend fun quiet()

    suspend fun restore()
}

/** A loud alert waits this long at most for the quieters before its own sound (6 §3.3). */
const val QUIET_CAP_MS = 500L

/**
 * The alert layer (5 §3.3, §5.1): above sheets, Settings and the wizard, below the PIN pad. Unlike [OverlayHost.show], an
 * alert never replaces or closes the open sheet, and touching it is not a touch for the screens beneath, whose own
 * timeouts run on. Safe from any thread.
 */
interface AlertHost {
    /** True while Culvery's screen is resumed in front: an alert raised otherwise would never be seen (5 §2.7). */
    val inFront: StateFlow<Boolean>

    /**
     * Shows [content] on the alert layer, replacing any alert already there. A [loud] alert runs every [AlertQuieter]'s
     * quiet() while it shows and their restore() when it goes (6 §3.3); one replacing a loud alert runs nothing again.
     */
    fun show(loud: Boolean = false, content: @Composable () -> Unit)

    /** Returns once the quieters have finished for the loud alert showing, or [QUIET_CAP_MS] after it showed; at once otherwise. */
    suspend fun quieted()

    fun dismiss()
}
```
(Keep `LocalAlertHost` and the imports.)

- [ ] **Step 4: The shell runs them**

In `app/src/main/java/uk/co/siland/culvery/shell/ShellOverlays.kt`, replace `class ShellAlerts …` (its KDoc stays, extended) with:
```kotlin
/**
 * The app's [AlertHost]: the content the alert layer draws, whether the screen it observes is resumed, and the quieters
 * a loud alert runs (6 §3.3). Each quiet or restore pass starts after the one before, so a restore never overtakes the
 * quiet it undoes.
 */
@Singleton
class ShellAlerts @Inject constructor(
    private val quieters: Set<@JvmSuppressWildcards AlertQuieter>,
    @ApplicationScope private val scope: CoroutineScope,
) : AlertHost, DefaultLifecycleObserver {
    private val _content = MutableStateFlow<(@Composable () -> Unit)?>(null)
    val content: StateFlow<(@Composable () -> Unit)?> = _content.asStateFlow()

    private val _inFront = MutableStateFlow(false)
    override val inFront: StateFlow<Boolean> = _inFront.asStateFlow()

    private val lock = Any()
    private var loud = false
    private var lastPass: Job = Job().apply { complete() }
    private var quietened: Deferred<Unit> = CompletableDeferred(Unit)

    override fun show(loud: Boolean, content: @Composable () -> Unit) {
        synchronized(lock) {
            _content.value = content
            if (loud && !this.loud) startQuiet() else if (!loud && this.loud) startRestore()
            this.loud = loud
        }
    }

    override suspend fun quieted() {
        synchronized(lock) { quietened }.await()
    }

    override fun dismiss() {
        synchronized(lock) {
            _content.value = null
            if (loud) startRestore()
            loud = false
        }
    }

    private fun startQuiet() {
        val before = lastPass
        val pass = scope.launch {
            before.join()
            runAll { it.quiet() }
        }
        lastPass = pass
        quietened = scope.async {
            withTimeoutOrNull(QUIET_CAP_MS) { pass.join() }
            Unit
        }
    }

    private fun startRestore() {
        val before = lastPass
        lastPass = scope.launch {
            before.join()
            runAll { it.restore() }
        }
        quietened = CompletableDeferred(Unit)
    }

    /** Every quieter at once; one that fails is logged and the others go on. */
    private suspend fun runAll(step: suspend (AlertQuieter) -> Unit) = coroutineScope {
        quieters.forEach { quieter ->
            launch {
                try {
                    step(quieter)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "A quieter failed (${e::class.simpleName})")
                }
            }
        }
    }

    /** This host as an observer that writes nothing once [handedOver] is true: an instance giving way to the home task is not in front. */
    fun unlessHandedOver(handedOver: () -> Boolean): DefaultLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            if (!handedOver()) this@ShellAlerts.onResume(owner)
        }

        override fun onPause(owner: LifecycleOwner) {
            if (!handedOver()) this@ShellAlerts.onPause(owner)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        _inFront.value = true
    }

    override fun onPause(owner: LifecycleOwner) {
        _inFront.value = false
    }

    private companion object {
        const val TAG = "ShellAlerts"
    }
}
```
Imports to add: `android.util.Log`, `kotlinx.coroutines.CancellationException`, `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Deferred`, `kotlinx.coroutines.Job`, `kotlinx.coroutines.async`, `kotlinx.coroutines.coroutineScope`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.withTimeoutOrNull`, `uk.co.siland.culvery.core.plugin.AlertQuieter`, `uk.co.siland.culvery.core.plugin.ApplicationScope`, `uk.co.siland.culvery.core.plugin.QUIET_CAP_MS`.

In `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`, after `startables()`:
```kotlin
    /** Bound by a capability with something to turn down for a loud alert, e.g. Music (6 §3.3). */
    @Multibinds
    abstract fun quieters(): Set<AlertQuieter>
```
(import `uk.co.siland.culvery.core.plugin.AlertQuieter`).

In `capability/door/src/test/java/uk/co/siland/culvery/capability/door/TestDoor.kt`, make `FakeAlertHost` compile (Task 3 gives it more):
```kotlin
    override fun show(loud: Boolean, content: @Composable () -> Unit) {
        shown++
        this.content = content
    }

    override suspend fun quieted() = Unit
```

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShellAlertsTest*'` then `./gradlew :capability:door:testDebugUnitTest`
Expected: PASS; Door's tests unchanged and passing (`host.show { … }` still compiles).

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL, no image changed.
```bash
git add core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Alerts.kt app/src/main/java/uk/co/siland/culvery/shell/ShellOverlays.kt app/src/main/java/uk/co/siland/culvery/di/AppModule.kt app/src/test/java/uk/co/siland/culvery/shell/ShellAlertsTest.kt capability/door/src/test/java/uk/co/siland/culvery/capability/door/TestDoor.kt
git commit -m "Let a loud alert turn other things down while it shows and back up when it goes"
```

---

### Task 3: Door's card is loud and the chime waits (§3.3; ruling 3)

**Review:** sonnet.

**Files:**
- Modify: `capability/door/src/main/java/uk/co/siland/culvery/capability/door/DoorAlerts.kt:60-86` (`onRecorded`)
- Test: `capability/door/src/test/java/uk/co/siland/culvery/capability/door/TestDoor.kt` (`FakeAlertHost`), `DoorAlertsTest.kt`

**Interfaces:**
- Consumes: `AlertHost.show(loud, content)`, `AlertHost.quieted()` (Task 2).
- Produces: nothing new; Door never names Music.

- [ ] **Step 1: Write the failing tests**

In `TestDoor.kt`, give `FakeAlertHost` (imports `kotlinx.coroutines.CompletableDeferred`):
```kotlin
    /** Whether the last show was loud. */
    var loud: Boolean? = null

    /** Holds quieted() until completed; null returns at once. */
    var quietedGate: CompletableDeferred<Unit>? = null
    var quietedCalls = 0

    override fun show(loud: Boolean, content: @Composable () -> Unit) {
        shown++
        this.loud = loud
        this.content = content
    }

    override suspend fun quieted() {
        quietedCalls++
        quietedGate?.await()
    }
```
Add to `DoorAlertsTest.kt` (imports `kotlinx.coroutines.CompletableDeferred`, `java.time.LocalTime`):
```kotlin
    /** 6 §3.3: the card is loud, and the chime waits until the music is lower. Fails if the chime plays before quieted() returns. */
    @Test
    fun theCardIsLoudAndTheChimeWaitsForTheMusicToQuieten() = runTest {
        val gate = CompletableDeferred<Unit>()
        host.quietedGate = gate
        val alerts = alerts()
        val ringing = launch { alerts.onRecorded(rung(1)) }
        runCurrent()
        assertThat(host.loud).isTrue()
        assertThat(chime.volumes).isEmpty()
        gate.complete(Unit)
        ringing.join()
        assertThat(chime.volumes).hasSize(1)
    }

    /** 6 §2.3: in quiet hours the card still lowers the music, and with no chime nothing waits. */
    @Test
    fun inQuietHoursTheCardIsStillLoudAndNothingWaits() = runTest {
        prefs = DoorPrefs(quietFrom = LocalTime.of(14, 0), quietUntil = LocalTime.of(15, 0))
        host.quietedGate = CompletableDeferred()
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        assertThat(host.loud).isTrue()
        assertThat(host.quietedCalls).isEqualTo(0)
        assertThat(chime.volumes).isEmpty()
    }

    /** 6 §4.6: a ring within 10 seconds of the last chime plays none, so nothing waits for the music. */
    @Test
    fun aRingWithinTenSecondsOfTheLastChimeDoesNotWait() = runTest {
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        assertThat(host.quietedCalls).isEqualTo(1)
        advanceTimeBy(CHIME_GAP_MS - 1)
        alerts.onRecorded(rung(2))
        assertThat(host.quietedCalls).isEqualTo(1)
        assertThat(chime.volumes).hasSize(1)
    }
```
Fails today: `onRecorded` shows a quiet alert (`loud` is false) and never calls `quieted()`, so the first test's chime plays at once.
Fails if: the card isn't loud; the chime doesn't wait; quiet hours or the 10-second gap skip the lowering or wait anyway.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorAlertsTest*'`
Expected: FAIL on `host.loud` (false) in both.

- [ ] **Step 3: The change**

In `DoorAlerts.onRecorded`, change the show to:
```kotlin
        if (!alreadyShowing) host.show(loud = true) { DoorCardHost(this@DoorAlerts) }
```
and the chime's last lines to:
```kotlin
        if (quietAt(now, z, p) || chimedRecently) return@withLock
        // Music and the like go quieter first, for half a second at most (6 §3.3).
        host.quieted()
        if (chime.play(p.volume)) lastChimeMillis = now
```
Extend the class KDoc's first sentence: "…raises the card or refreshes it (a loud alert: the shell turns music down while it shows, 6 §3.3), restarts its 2 minutes, and chimes …", and add to the KDoc: "quieted() is awaited inside the lock, so a Got it tap in those 500 ms waits for them."

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :capability:door:testDebugUnitTest`
Expected: PASS, every existing Door test included.

- [ ] **Step 5: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/door/src/main/java/uk/co/siland/culvery/capability/door/DoorAlerts.kt capability/door/src/test/java/uk/co/siland/culvery/capability/door/TestDoor.kt capability/door/src/test/java/uk/co/siland/culvery/capability/door/DoorAlertsTest.kt
git commit -m "Make the door card a loud alert and chime once the music is lower"
```

---
### Task 4: `:capability:music`: the contract, the home-network rule, the connection and the tab (§3.1, §3.5, §6; rulings 4, 5, 19, 20, 24; Review Focus 3)

**Review:** opus.

**Files:**
- Modify: `settings.gradle.kts` (`include(":capability:music")` after `include(":capability:door")`), `app/build.gradle.kts` (the module, by Hilt alone)
- Create: `capability/music/build.gradle.kts`, `capability/music/src/test/resources/robolectric.properties`
- Create (under `capability/music/src/main/java/uk/co/siland/culvery/capability/music/`): `MusicContract.kt`, `HomeNetwork.kt`, `MusicSettings.kt`, `Speakers.kt`, `MusicCapability.kt`, `MusicWords.kt`, `di/MusicModule.kt`
- Test (under `capability/music/src/test/java/uk/co/siland/culvery/capability/music/`): `TestMusic.kt`, `HomeNetworkTest.kt`, `MusicSettingsTest.kt`, `SpeakersTest.kt`, `MusicCapabilityTest.kt`; `build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt`; `app/src/test/java/uk/co/siland/culvery/AppSourceTest.kt`

**Interfaces:**
- Consumes: `Capability`, `HomeCard`, `ProviderDescriptor`, `Feature`, `ApplicationScope` (`:core:plugin`); `Icons.SPEAKER` (Task 1).
- Produces (package `uk.co.siland.culvery.capability.music`):
  - `enum class PlayState { PLAYING, PAUSED, STOPPED, TRANSITIONING; val isPlaying: Boolean }`
  - `data class SpeakerRoom(val id: String, val name: String, val volume: Int)`
  - `sealed interface NowPlaying { data class Track(val title: String, val artist: String?, val album: String?); data class Radio(val station: String, val programme: String?) }`
  - `data class SpeakerGroup(val id: String, val rooms: List<SpeakerRoom>, val coordinatorId: String, val state: PlayState, val nowPlaying: NowPlaying?, val artUrl: String?, val canSkip: Boolean, val volume: Int, val startedAtMillis: Long?)` with `val isPlaying: Boolean` and `val name: String` (rooms joined " + ", leader first)
  - `enum class FavouriteKind { STREAM, QUEUED }`; `data class Favourite(val id: String, val name: String, val artUrl: String?, val kind: FavouriteKind)`
  - `enum class MusicProblem { UNREACHABLE, OFFLINE, OTHER_HOUSEHOLD }`
  - `class MusicRefusedException(val code: Int? = null) : Exception`; `class MusicUnreachableException(val problem: MusicProblem) : Exception`; `class NoLibraryException : Exception`
  - `data class MusicLink(val householdId: String, val address: String)`
  - `fun isHomeNetworkHost(host: String): Boolean` (`HomeNetwork.kt`: a private IPv4 literal as written; the one rule for what may be contacted, ruling 20)
  - `interface MusicSystem` (below; `roomVolume(room)`, not a group volume, for the lowering); `interface MusicProvider { val descriptor: ProviderDescriptor; suspend fun find(): MusicLink?; suspend fun check(address: String): MusicLink?; fun open(link: MusicLink, onMoved: suspend (MusicLink) -> Unit): MusicSystem }`
  - `data class MusicPrefs(providerId: String?, householdId: String?, address: String?, usualRoomId: String?, lowerTo: Int = 20)` with `val link: MusicLink?`; `const val DEFAULT_LOWER_TO = 20`, `const val MAX_LOWER_TO = 50`
  - `class MusicSettings(store: DataStore<Preferences>, scope: CoroutineScope)`: `prefs`, `current`, `suspend fun connect(providerId: String, link: MusicLink)`, `suspend fun moved(link: MusicLink)`, `suspend fun setUsualRoom(roomId: String?)`, `suspend fun setLowerTo(percent: Int)`; `internal fun musicSettingsStore(scope, produceFile)`
  - `class Speakers @Inject constructor(providers: Set<MusicProvider>, settings: MusicSettings, @ApplicationScope scope)`: `val providers: List<MusicProvider>`, `val system: StateFlow<MusicSystem?>`
  - `const val MUSIC_ID = "music"`; `class MusicCapability`
  - Test doubles (`TestMusic.kt`): `TestMusicSystem`, `TestMusicProvider`, `group(…)`, `SONG`, `STATION`, `musicSettings(dir, scope)`

- [ ] **Step 1: The module**

Create `capability/music/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    // Capability, AlertQuieter, ProviderDescriptor and Startable appear in this module's public API.
    api(project(":core:plugin"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
```
`capability/music/src/test/resources/robolectric.properties`: `qualifiers=w1280dp-h800dp-land-hdpi` (as the door's).
`settings.gradle.kts`: `include(":capability:music")` after `include(":capability:door")`.
`app/build.gradle.kts`, after the door lines:
```kotlin
    // Wired by Hilt alone, as the door (6 §3).
    implementation(project(":capability:music"))
```

- [ ] **Step 2: Write the failing tests**

`build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt` (**pin**: the rules go by family):
```kotlin
    @Test
    fun theMusicModulesFollowTheRules() {
        allowed(":capability:music", ":core:plugin", "api")
        allowed(":capability:music", ":core:ui")
        allowed(":provider:music-sonos", ":capability:music")
        allowed(":app", ":provider:music-sonos")
        banned(":capability:music", ":provider:music-sonos")
        banned(":capability:music", ":provider:music-sonos", "testImplementation")
        banned(":capability:music", ":capability:door")
        banned(":capability:door", ":capability:music")
        banned(":provider:music-sonos", ":capability:door")
        banned(":provider:music-sonos", ":capability:calendar")
    }
```
`AppSourceTest.kt`: rename the test `theAppsOwnCodeNeverNamesTheWeatherDoorOrMusicCapability`, its class KDoc to "`:app` wires the weather, door and music capabilities through Hilt alone and never names them in its own code.", and add (**pin**):
```kotlin
        assertThat(files.filter { it.readText().contains("uk.co.siland.culvery.capability.music") }.map { it.path }).isEmpty()
```
Create `capability/music/src/test/java/uk/co/siland/culvery/capability/music/TestMusic.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.Icons

internal val SONG = NowPlaying.Track("Song", "Artist", "Album")
internal val STATION = NowPlaying.Radio("Culvery FM", "The Breakfast Show")

/** A group of [rooms], the first leading; each room's id is its name with "-id". */
internal fun group(
    id: String,
    vararg rooms: String,
    state: PlayState = PlayState.STOPPED,
    nowPlaying: NowPlaying? = null,
    volume: Int = 30,
    startedAt: Long? = null,
    canSkip: Boolean = nowPlaying !is NowPlaying.Radio,
    artUrl: String? = null,
) = SpeakerGroup(id, rooms.map { SpeakerRoom("$it-id", it, volume) }, "${rooms.first()}-id", state, nowPlaying, artUrl, canSkip, volume, startedAt)

internal fun musicSettings(dir: File, scope: CoroutineScope) =
    MusicSettings(musicSettingsStore(scope) { File(dir, "music_settings.preferences_pb") }, scope)

/** A music system a test drives: its groups and problem, the calls made of it in order, and calls that fail or wait. */
internal class TestMusicSystem(groups: List<SpeakerGroup> = emptyList()) : MusicSystem {
    override val groups = MutableStateFlow(groups)
    override val problem = MutableStateFlow<MusicProblem?>(null)

    /** Written from whatever thread a check runs on; read by the test. */
    val calls: MutableList<String> = CopyOnWriteArrayList()

    /** Each room's volume as its speaker holds it, by room id; a room not here answers its [SpeakerRoom.volume]. */
    val roomVolumes = mutableMapOf<String, Int>()

    /** What a call throws, by its name as [calls] writes it ("roomVolume Kitchen-id"), every time. */
    val failures = mutableMapOf<String, Exception>()

    /** What a call throws the first time only. */
    val failOnce = mutableMapOf<String, Exception>()

    /** Holds a call, by its name, until completed. */
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** What playingNow() answers; null answers the playing ones of [groups]. */
    var playingNowAnswer: List<SpeakerGroup>? = null
    var favouriteList: List<Favourite> = emptyList()

    private suspend fun record(call: String) {
        calls += call
        gates[call]?.await()
        failures[call]?.let { throw it }
        failOnce.remove(call)?.let { throw it }
    }

    override suspend fun checkLayout() = record("checkLayout")

    override suspend fun checkPlayback() = record("checkPlayback")

    override suspend fun playingNow(): List<SpeakerGroup> {
        record("playingNow")
        return playingNowAnswer ?: groups.value.filter { it.isPlaying }
    }

    override suspend fun roomVolume(room: SpeakerRoom): Int {
        record("roomVolume ${room.id}")
        return roomVolumes[room.id] ?: room.volume
    }

    override suspend fun setGroupVolume(group: SpeakerGroup, percent: Int) = record("setGroupVolume ${group.id} $percent")

    /** The speaker takes the volume before it answers, so a failure here is a command that took effect but whose answer was lost. */
    override suspend fun setRoomVolume(room: SpeakerRoom, percent: Int) {
        roomVolumes[room.id] = percent
        record("setRoomVolume ${room.id} $percent")
    }

    override suspend fun play(group: SpeakerGroup) = record("play ${group.id}")

    override suspend fun pause(group: SpeakerGroup) = record("pause ${group.id}")

    override suspend fun next(group: SpeakerGroup) = record("next ${group.id}")

    override suspend fun favourites(): List<Favourite> {
        record("favourites")
        return favouriteList
    }

    override suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite) = record("playFavourite ${group.id} ${favourite.id}")

    override suspend fun shuffleEverything(group: SpeakerGroup) = record("shuffleEverything ${group.id}")

    override suspend fun art(url: String): ByteArray? {
        record("art $url")
        return null
    }
}

/** A provider whose answers a test sets; it opens a [TestMusicSystem] and keeps each. */
internal class TestMusicProvider(id: String = "music.sonos") : MusicProvider {
    override val descriptor = ProviderDescriptor(id, "Sonos", Icons.SPEAKER, setOf(Feature.READ, Feature.WRITE))
    var found: MusicLink? = null

    /** What check(address) answers, by address. */
    val speakers = mutableMapOf<String, MusicLink>()

    /** Thrown by find() and check(): offline, say. */
    var failure: Exception? = null
    val asked = mutableListOf<String>()
    val opened = mutableListOf<MusicLink>()
    val systems = mutableListOf<TestMusicSystem>()
    var onMoved: (suspend (MusicLink) -> Unit)? = null

    override suspend fun find(): MusicLink? {
        asked += "find"
        failure?.let { throw it }
        return found
    }

    override suspend fun check(address: String): MusicLink? {
        asked += "check $address"
        failure?.let { throw it }
        return speakers[address]
    }

    override fun open(link: MusicLink, onMoved: suspend (MusicLink) -> Unit): MusicSystem {
        opened += link
        this.onMoved = onMoved
        return TestMusicSystem().also { systems += it }
    }
}
```
Create `MusicSettingsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { musicSettings(folder.root, scope) }
    private val home = MusicLink("Sonos_home", "192.168.1.20:1400")

    @After
    fun tearDown() = scope.cancel()

    /** 6 §2.3, §5.4: not connected, no usual room, 20 %. */
    @Test
    fun theDefaults() = runTest {
        assertThat(settings.prefs.first()).isEqualTo(MusicPrefs(null, null, null, null, 20))
        assertThat(settings.prefs.first().link).isNull()
    }

    @Test
    fun aConnectionIsKeptWithItsUsualRoomAndShare() = runTest {
        settings.connect("music.sonos", home)
        settings.setUsualRoom("RINCON_A")
        settings.setLowerTo(35)
        val expected = MusicPrefs("music.sonos", "Sonos_home", "192.168.1.20:1400", "RINCON_A", 35)
        assertThat(settings.prefs.first()).isEqualTo(expected)
        assertThat(settings.prefs.first().link).isEqualTo(home)
        settings.current.first { it.lowerTo == 35 }
        assertThat(settings.current.value).isEqualTo(expected)
    }

    /** Another household's rooms aren't this one's. Fails if Find Sonos on a new system keeps the old usual room. */
    @Test
    fun anotherHouseholdForgetsTheUsualRoom() = runTest {
        settings.connect("music.sonos", home)
        settings.setUsualRoom("RINCON_A")
        settings.connect("music.sonos", home.copy(address = "192.168.1.30:1400"))
        assertThat(settings.prefs.first().usualRoomId).isEqualTo("RINCON_A")
        settings.connect("music.sonos", MusicLink("Sonos_other", "192.168.1.40:1400"))
        assertThat(settings.prefs.first().usualRoomId).isNull()
    }

    /** 6 §4.1: a rescan saves the new address of the same household only. */
    @Test
    fun movedChangesOnlyTheSameHouseholdsAddress() = runTest {
        settings.connect("music.sonos", home)
        settings.moved(MusicLink("Sonos_other", "192.168.1.99:1400"))
        assertThat(settings.prefs.first().address).isEqualTo("192.168.1.20:1400")
        settings.moved(home.copy(address = "192.168.1.21:1400"))
        assertThat(settings.prefs.first().link).isEqualTo(home.copy(address = "192.168.1.21:1400"))
    }

    /** 6 §5.4: 0–50 %. */
    @Test
    fun theShareStaysBetweenNothingAndHalf() = runTest {
        settings.setLowerTo(80)
        assertThat(settings.prefs.first().lowerTo).isEqualTo(50)
        settings.setLowerTo(-5)
        assertThat(settings.prefs.first().lowerTo).isEqualTo(0)
    }
}
```
Create `SpeakersTest.kt` (each test ends on a change the system must show, so a missed or extra open can't pass unseen):
```kotlin
package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeakersTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { musicSettings(folder.root, scope) }
    private val sonos = TestMusicProvider()
    private val home = MusicLink("Sonos_home", "192.168.1.20:1400")
    private val other = MusicLink("Sonos_other", "192.168.1.40:1400")

    @After
    fun tearDown() = scope.cancel()

    private suspend fun Speakers.opensAnother(than: MusicSystem?) = withTimeout(5_000) { system.first { it != null && it !== than } }

    @Test
    fun noConnectionOpensNothingUntilOneIsSaved() = runTest {
        val speakers = Speakers(setOf(sonos), settings, scope)
        settings.prefs.first()
        assertThat(speakers.system.value).isNull()
        settings.connect("music.sonos", home)
        speakers.opensAnother(than = null)
        assertThat(sonos.opened).containsExactly(home)
    }

    /** Review Focus 3: the open system found the household at a new address itself; saving it must not reopen it. */
    @Test
    fun aNewAddressForTheSameHouseholdKeepsTheOpenSystem() = runTest {
        settings.connect("music.sonos", home)
        val speakers = Speakers(setOf(sonos), settings, scope)
        val first = speakers.opensAnother(than = null)
        sonos.onMoved!!(home.copy(address = "192.168.1.21:1400"))
        assertThat(settings.prefs.first().address).isEqualTo("192.168.1.21:1400")
        settings.connect("music.sonos", other)
        speakers.opensAnother(than = first)
        // Had the move reopened the system, home would be listed twice.
        assertThat(sonos.opened).containsExactly(home, other).inOrder()
    }

    /** A connection to a provider this build doesn't have opens nothing; a later Sonos one opens. */
    @Test
    fun aConnectionToAProviderNotInTheBuildOpensNothing() = runTest {
        settings.connect("music.cloud", MusicLink("cloud-household", "cloud"))
        val speakers = Speakers(setOf(sonos), settings, scope)
        settings.prefs.first { it.providerId == "music.cloud" }
        assertThat(speakers.system.value).isNull()
        settings.connect("music.sonos", home)
        speakers.opensAnother(than = null)
        assertThat(sonos.opened).containsExactly(home)
    }
}
```
Create `HomeNetworkTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §6 privacy: only the home network is contacted. Exactly as written; tidying a typed address is Settings' job. */
class HomeNetworkTest {
    @Test
    fun privateIpv4LiteralsOnly() {
        listOf("10.0.0.5", "172.16.4.2", "172.31.255.1", "192.168.1.20", "169.254.10.1").forEach { assertThat(isHomeNetworkHost(it)).isTrue() }
        listOf(
            "172.32.0.1", "8.8.8.8", "127.0.0.1", "192.168.1", "192.168.1.256", "192.168.001.020", " 192.168.1.20",
            "sonos.local", "localhost", "", "::1", "192.168.1.2O",
        ).forEach { assertThat(isHomeNetworkHost(it)).isFalse() }
    }
}
```
Create `MusicCapabilityTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class MusicCapabilityTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { musicSettings(folder.root, scope) }

    @After
    fun tearDown() = scope.cancel()

    /** 6 §3.1: rail slot 30, "Music", `speaker`. */
    @Test
    fun itIsMusicAtSlotThirty() {
        val music = MusicCapability(Speakers(setOf(TestMusicProvider()), settings, scope))
        assertThat(listOf(music.id, music.label, music.icon, music.order)).containsExactly("music", "Music", Icons.SPEAKER, 30).inOrder()
    }

    /** 6 §3.1: the tab once a connection exists. Fails if the tab shows before Find Sonos. */
    @Test
    fun theTabShowsOnceSpeakersAreConnected() = runTest {
        val music = MusicCapability(Speakers(setOf(TestMusicProvider()), settings, scope))
        assertThat(music.hasTab.first()).isFalse()
        settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
        withTimeout(5_000) { music.hasTab.first { it } }
    }
}
```
Fails today: the module has no sources (nothing compiles).
Fails if: the household's usual room survives a new system; a moved address reopens the system; a connection to a provider not in the build opens anything; the tab shows before a connection; the home-network rule lets through a public, mistyped or padded address.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :capability:music:testDebugUnitTest` and `./gradlew -p build-logic :convention:test --tests '*ModuleBoundariesTest*'`
Expected: the music tests FAIL to compile; `ModuleBoundariesTest` PASSES (pin).

- [ ] **Step 4: The contract**

Create `capability/music/src/main/java/uk/co/siland/culvery/capability/music/MusicContract.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import uk.co.siland.culvery.core.plugin.ProviderDescriptor

enum class PlayState {
    PLAYING,
    PAUSED,
    STOPPED,
    TRANSITIONING;

    /** Sounding, or about to: a group starting a track passes through TRANSITIONING. */
    val isPlaying: Boolean get() = this == PLAYING || this == TRANSITIONING
}

data class SpeakerRoom(val id: String, val name: String, val volume: Int)

/** What a group plays: a track from its queue, or a station (6 §3.1). */
sealed interface NowPlaying {
    data class Track(val title: String, val artist: String?, val album: String?) : NowPlaying

    data class Radio(val station: String, val programme: String?) : NowPlaying
}

/**
 * One Sonos group as Sonos groups the rooms (6 §2.4): its rooms with the leader first, what it plays and its group volume.
 * [canSkip] is false for a stream; [startedAtMillis] is when it last started playing, for Home's choice (6 §2.2).
 */
data class SpeakerGroup(
    val id: String,
    val rooms: List<SpeakerRoom>,
    val coordinatorId: String,
    val state: PlayState,
    val nowPlaying: NowPlaying?,
    val artUrl: String?,
    val canSkip: Boolean,
    val volume: Int,
    val startedAtMillis: Long?,
) {
    val isPlaying: Boolean get() = state.isPlaying

    /** "Kitchen + Lounge" (6 §5.2). */
    val name: String get() = rooms.joinToString(" + ") { it.name }
}

enum class FavouriteKind { STREAM, QUEUED }

data class Favourite(val id: String, val name: String, val artUrl: String?, val kind: FavouriteKind)

/** What the status line says is wrong (6 §6). */
enum class MusicProblem { UNREACHABLE, OFFLINE, OTHER_HOUSEHOLD }

/** A speaker refused a command with a fault (6 §6); [code] is UPnP's when it gave one. */
class MusicRefusedException(val code: Int? = null) : Exception("A speaker refused the command (UPnP error ${code ?: "unknown"})")

/** No speaker answered even after looking again, or the tablet is offline, or the household changed: [problem] says which. */
class MusicUnreachableException(val problem: MusicProblem) : Exception("The speakers can't be reached (${problem.name})")

/** The library has no tracks: Shuffle everything stops before touching the queue (6 §4.4). */
class NoLibraryException : Exception("The music library is empty")

/** A saved household and the speaker address that last answered for it (6 §3.2). Neither is a secret. */
data class MusicLink(val householdId: String, val address: String)

/**
 * What a music provider implements (6 §3.1). [groups] and [problem] are kept by the system; the capability says when to
 * check (6 §4.2). Commands go to the group's leader, change [groups] at once, and check that group again; they throw
 * [MusicRefusedException], [MusicUnreachableException] or [NoLibraryException].
 */
interface MusicSystem {
    val groups: StateFlow<List<SpeakerGroup>>
    val problem: StateFlow<MusicProblem?>

    /** Rooms, groups and room volumes. */
    suspend fun checkLayout()

    /** Each group's leader: what plays and the group volume. */
    suspend fun checkPlayback()

    /** The groups playing right now, read afresh rather than from the last check (for the doorbell, 6 §4.6). */
    suspend fun playingNow(): List<SpeakerGroup>

    /** [room]'s own volume as its speaker holds it now (the lowering works room by room, 6 §4.6). */
    suspend fun roomVolume(room: SpeakerRoom): Int

    suspend fun play(group: SpeakerGroup)

    suspend fun pause(group: SpeakerGroup)

    suspend fun next(group: SpeakerGroup)

    suspend fun setGroupVolume(group: SpeakerGroup, percent: Int)

    suspend fun setRoomVolume(room: SpeakerRoom, percent: Int)

    suspend fun favourites(): List<Favourite>

    suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite)

    suspend fun shuffleEverything(group: SpeakerGroup)

    /** The picture at [url], or null when it can't be had. */
    suspend fun art(url: String): ByteArray?
}

/** A way to the household's speakers (6 §3.2), for Settings › Music: find them, check an address, open the system. */
interface MusicProvider {
    val descriptor: ProviderDescriptor

    /** Looks for speakers on the home Wi-Fi (6 §4.1); null when none answered. Throws [MusicUnreachableException] offline. */
    suspend fun find(): MusicLink?

    /** Asks the speaker at [address] (four numbers) for its household; null when nothing there answers as a speaker. */
    suspend fun check(address: String): MusicLink?

    /** The household behind [link]; [onMoved] saves the address the system found it at after a rescan. */
    fun open(link: MusicLink, onMoved: suspend (MusicLink) -> Unit): MusicSystem
}
```
(import `kotlinx.coroutines.flow.StateFlow`.)

Create `HomeNetwork.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

private const val PARTS = 4
private const val MAX_PART = 255

/**
 * Whether [host] is on the home network as written: four numbers 0–255 without leading zeros, in a private range
 * (10/8, 172.16/12, 192.168/16, 169.254/16). The one rule for what Music may contact (6 §6 privacy); the provider adds
 * its port to it.
 */
fun isHomeNetworkHost(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != PARTS) return false
    val n = parts.map { part ->
        val plain = part.length in 1..3 && part.all { it in '0'..'9' } && (part.length == 1 || part[0] != '0')
        if (plain) part.toInt() else return false
    }
    if (n.any { it > MAX_PART }) return false
    return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254)
}
```
Create `MusicSettings.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope

private const val FILE = "music_settings"
private const val TAG = "MusicSettings"

/** The doorbell's share of a group's volume (6 §2.3, §5.4). */
const val DEFAULT_LOWER_TO = 20
const val MAX_LOWER_TO = 50

/** Music's settings and its connection (6 §3.2): ids and an address, nothing secret, no names. */
data class MusicPrefs(
    val providerId: String? = null,
    val householdId: String? = null,
    val address: String? = null,
    /** The usual room's Sonos id (6 §2.2). */
    val usualRoomId: String? = null,
    val lowerTo: Int = DEFAULT_LOWER_TO,
) {
    /** The saved household and the speaker that last answered for it; null before Find Sonos. */
    val link: MusicLink? get() = if (householdId != null && address != null) MusicLink(householdId, address) else null
}

/** The settings file's store; one that can't be read is replaced by an empty one, the defaults. */
internal fun musicSettingsStore(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, scope = scope, produceFile = produceFile)

/**
 * A small DataStore file, as the door's: under `files/datastore/`, which backup and transfer already leave alone (6 §3.5).
 * Writes throw [IOException]; the caller says so or logs it.
 */
@Singleton
class MusicSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(musicSettingsStore { context.preferencesDataStoreFile(FILE) }, scope)

    val prefs: Flow<MusicPrefs> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Couldn't read music's settings (${e::class.simpleName})")
            emit(emptyPreferences())
        }
        .map { it.toPrefs() }
        .distinctUntilChanged()

    /** For the lowering, which can't wait: the defaults until the file is first read. */
    val current: StateFlow<MusicPrefs> = prefs.stateIn(scope, SharingStarted.Eagerly, MusicPrefs())

    /** Find Sonos or a typed address found [link] (6 §4.1); another household's usual room isn't this one's. */
    suspend fun connect(providerId: String, link: MusicLink) {
        store.edit {
            if (it[HOUSEHOLD] != link.householdId) it.remove(USUAL_ROOM)
            it[PROVIDER] = providerId
            it[HOUSEHOLD] = link.householdId
            it[ADDRESS] = link.address
        }
    }

    /** The household answered at a new address after a rescan (6 §4.1). */
    suspend fun moved(link: MusicLink) {
        store.edit { if (it[HOUSEHOLD] == link.householdId) it[ADDRESS] = link.address }
    }

    suspend fun setUsualRoom(roomId: String?) {
        store.edit { if (roomId == null) it.remove(USUAL_ROOM) else it[USUAL_ROOM] = roomId }
    }

    suspend fun setLowerTo(percent: Int) {
        store.edit { it[LOWER_TO] = percent.coerceIn(0, MAX_LOWER_TO) }
    }

    private fun Preferences.toPrefs() = MusicPrefs(this[PROVIDER], this[HOUSEHOLD], this[ADDRESS], this[USUAL_ROOM], this[LOWER_TO] ?: DEFAULT_LOWER_TO)

    private companion object {
        val PROVIDER = stringPreferencesKey("provider")
        val HOUSEHOLD = stringPreferencesKey("household")
        val ADDRESS = stringPreferencesKey("address")
        val USUAL_ROOM = stringPreferencesKey("usual_room")
        val LOWER_TO = intPreferencesKey("lower_to")
    }
}
```
Create `Speakers.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope

private const val TAG = "Speakers"

/**
 * The household's music system: the saved connection's provider, opened on its household. A new address for the same
 * household keeps the open system, which moved there itself (6 §4.1); another household or provider opens afresh.
 */
@Singleton
class Speakers @Inject constructor(
    providers: Set<@JvmSuppressWildcards MusicProvider>,
    private val settings: MusicSettings,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Find Sonos and a typed address use the first (Sonos, the only one bound, 6 §3.4). */
    val providers: List<MusicProvider> = providers.toList()

    private data class Opening(val providerId: String, val link: MusicLink)

    val system: StateFlow<MusicSystem?> = settings.prefs
        .map { p -> p.providerId?.let { id -> p.link?.let { Opening(id, it) } } }
        .distinctUntilChanged { a, b -> a?.providerId == b?.providerId && a?.link?.householdId == b?.link?.householdId }
        .map { it?.let(::open) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun open(opening: Opening): MusicSystem? =
        providers.firstOrNull { it.descriptor.id == opening.providerId }?.open(opening.link) { moved ->
            try {
                settings.moved(moved)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't save the speakers' new address (${e::class.simpleName})")
            }
        }
}
```
Create `MusicWords.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

internal const val MUSIC = "Music"
```
Create `MusicCapability.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.ui.Icons

const val MUSIC_ID = "music"

/** Sonos on the wall (6 §3.1): rail slot 30, open to everyone, no badge. */
@Singleton
class MusicCapability @Inject constructor(private val speakers: Speakers) : Capability {
    override val id = MUSIC_ID
    override val label = MUSIC
    override val icon = Icons.SPEAKER
    override val order = 30

    /** Once a connection is saved and its provider is in this build (6 §3.1). */
    override val hasTab: Flow<Boolean> = speakers.system.map { it != null }.distinctUntilChanged()

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit
}
```
Create `di/MusicModule.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.music.MusicCapability
import uk.co.siland.culvery.capability.music.MusicProvider
import uk.co.siland.culvery.core.plugin.Capability

@Module
@InstallIn(SingletonComponent::class)
abstract class MusicModule {
    // With no provider bound there is no Music tab (6 §3.1).
    @Multibinds
    abstract fun providers(): Set<MusicProvider>

    @Binds
    @IntoSet
    abstract fun capability(impl: MusicCapability): Capability
}
```

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :capability:music:testDebugUnitTest` and `./gradlew :app:testDebugUnitTest --tests '*AppSourceTest*'`
Expected: PASS.

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` and `./gradlew -p build-logic :convention:test` — Expected: BUILD SUCCESSFUL, no image changed (no provider yet, so no tab).
```bash
git add settings.gradle.kts app/build.gradle.kts capability/music build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt app/src/test/java/uk/co/siland/culvery/AppSourceTest.kt
git commit -m "Add the Music capability's contract, its saved connection and its tab rule"
```

---

### Task 5: The Home-room rule, the tab order and the checks (§2.2, §4.2, §5.2; rulings 8, 9; Review Focus 1)

**Review:** opus.

**Files:**
- Create (under `capability/music/src/main/java/uk/co/siland/culvery/capability/music/`): `HomePick.kt`, `MusicView.kt`, `MusicChecks.kt`
- Modify: `di/MusicModule.kt` (`MusicChecks` as a `Startable`)
- Test: `HomePickTest.kt`, `MusicViewTest.kt`, `MusicChecksTest.kt`

**Interfaces:**
- Consumes: `SpeakerGroup`, `MusicSystem`, `Speakers.system`, `MusicSettings.prefs` (Task 4); `AlertHost.inFront`, `Startable` (`:core:plugin`).
- Produces:
  - `data class HomePick(val group: SpeakerGroup, val nothingPlaying: Boolean)`
  - `fun homePick(groups: List<SpeakerGroup>, usualRoomId: String?, keptId: String?): HomePick?`
  - `fun homePicks(groups: Flow<List<SpeakerGroup>>, usualRoomId: Flow<String?>): Flow<HomePick?>`
  - `fun tabOrder(groups: List<SpeakerGroup>): List<SpeakerGroup>`
  - `class MusicView @Inject constructor(speakers: Speakers, settings: MusicSettings, @ApplicationScope scope: CoroutineScope)`: `val groups: Flow<List<SpeakerGroup>>`, `val problem: Flow<MusicProblem?>`, `val home: StateFlow<HomePick?>` (held on the application scope, ruling 8)
  - `const val PLAYBACK_CHECK_MS = 2_000L`, `const val LAYOUT_CHECK_MS = 30_000L`
  - `class MusicChecks internal constructor(system: StateFlow<MusicSystem?>, inFront: StateFlow<Boolean>, scope: CoroutineScope) : Startable`, `@Inject constructor(speakers: Speakers, host: AlertHost, @ApplicationScope scope)`; `fun watch(layoutNow: Boolean = false): () -> Unit`; `internal val watching: Int`

- [ ] **Step 1: Write the failing tests**

Create `HomePickTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §2.2, with Home keeping a group it showed playing while that group is paused. */
class HomePickTest {
    private val kitchen = group("k", "Kitchen")
    private val lounge = group("l", "Lounge", "Dining room")
    private val bedroom = group("b", "Bedroom")
    private val all = listOf(kitchen, lounge, bedroom)

    private fun List<SpeakerGroup>.with(id: String, state: PlayState, startedAt: Long? = null) =
        map { if (it.id == id) it.copy(state = state, nowPlaying = SONG, startedAtMillis = startedAt) else it }

    @Test
    fun theOnlyPlayingGroup() {
        assertThat(homePick(all.with("b", PlayState.PLAYING), "Kitchen-id", null)).isEqualTo(HomePick(all.with("b", PlayState.PLAYING)[2], false))
    }

    @Test
    fun ofSeveralPlayingTheOneWithTheUsualRoom() {
        val groups = all.with("k", PlayState.PLAYING, 100).with("l", PlayState.PLAYING, 200)
        assertThat(homePick(groups, "Dining room-id", null)?.group?.id).isEqualTo("l")
        assertThat(homePick(groups, "Kitchen-id", null)?.group?.id).isEqualTo("k")
    }

    /** Fails if the earlier start wins, or Sonos's order does. */
    @Test
    fun ofSeveralPlayingWithoutTheUsualRoomTheLatestStart() {
        val groups = all.with("k", PlayState.PLAYING, 300).with("l", PlayState.PLAYING, 200)
        assertThat(homePick(groups, "Bedroom-id", null)?.group?.id).isEqualTo("k")
    }

    @Test
    fun nonePlayingTheUsualRoomSaysNothingPlaying() {
        assertThat(homePick(all, "Lounge-id", null)).isEqualTo(HomePick(lounge, nothingPlaying = true))
    }

    @Test
    fun noUsualRoomAndNothingPlayingTheFirstSonosLists() {
        assertThat(homePick(all, null, null)).isEqualTo(HomePick(kitchen, nothingPlaying = true))
        assertThat(homePick(all, "gone-id", null)?.group?.id).isEqualTo("k")
    }

    @Test
    fun aTransitioningGroupCountsAsPlaying() {
        assertThat(homePick(all.with("b", PlayState.TRANSITIONING), "Kitchen-id", null)?.group?.id).isEqualTo("b")
    }

    /** Review Focus 1. Fails if a paused group Home showed is swapped for the usual room, or a stopped one is kept. */
    @Test
    fun aPausedGroupHomeShowedStaysUntilItStops() {
        assertThat(homePick(all.with("b", PlayState.PAUSED), "Kitchen-id", keptId = "b")).isEqualTo(HomePick(all.with("b", PlayState.PAUSED)[2], false))
        assertThat(homePick(all.with("b", PlayState.STOPPED), "Kitchen-id", keptId = "b")?.group?.id).isEqualTo("k")
        assertThat(homePick(all.with("l", PlayState.PLAYING).with("b", PlayState.PAUSED), "Kitchen-id", keptId = "b")?.group?.id).isEqualTo("l")
    }

    @Test
    fun noGroupsNoPick() {
        assertThat(homePick(emptyList(), "Kitchen-id", null)).isNull()
    }

    /** 6 §5.2. Fails if the rest leave Sonos's order. */
    @Test
    fun theTabPutsPlayingGroupsFirstThenSonossOrder() {
        val groups = all.with("b", PlayState.PLAYING).with("l", PlayState.PAUSED)
        assertThat(tabOrder(groups).map { it.id }).containsExactly("b", "k", "l").inOrder()
    }
}
```
Create `MusicViewTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicViewTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val kitchen = group("k", "Kitchen")
    private val bedroom = group("b", "Bedroom")

    @After
    fun tearDown() = scope.cancel()

    /**
     * Review Focus 1, ruling 8: Home's memory outlives the card (Settings over Home, another tab, then back): a second
     * collector still sees the paused room. Fails if `home` is a cold flow that starts its memory afresh.
     */
    @Test
    fun theRoomPausedFromHomeSurvivesTheCardLeaving() = runTest {
        val settings = musicSettings(folder.root, scope)
        val provider = TestMusicProvider()
        val speakers = Speakers(setOf(provider), settings, scope)
        settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
        settings.setUsualRoom("Kitchen-id")
        withTimeout(5_000) { speakers.system.first { it != null } }
        val system = provider.systems.single()
        val view = MusicView(speakers, settings, scope)
        system.groups.value = listOf(kitchen, bedroom.copy(state = PlayState.PLAYING, nowPlaying = SONG))
        withTimeout(5_000) { view.home.first { it?.group?.id == "b" } }
        val paused = bedroom.copy(state = PlayState.PAUSED, nowPlaying = SONG)
        system.groups.value = listOf(kitchen, paused)
        withTimeout(5_000) { view.home.first { it?.group?.state == PlayState.PAUSED } }
        assertThat(view.home.first()).isEqualTo(HomePick(paused, nothingPlaying = false))
    }

    /** Review Focus 1: Pause on Home, then Play, is the same room. Fails if homePicks forgets what it showed. */
    @Test
    fun pausingTheShownGroupKeepsItOnHome() = runTest {
        val groups = MutableStateFlow(listOf(kitchen, bedroom.copy(state = PlayState.PLAYING, nowPlaying = SONG)))
        homePicks(groups, MutableStateFlow("Kitchen-id")).test {
            assertThat(awaitItem()?.group?.id).isEqualTo("b")
            groups.value = listOf(kitchen, bedroom.copy(state = PlayState.PAUSED, nowPlaying = SONG))
            assertThat(awaitItem()).isEqualTo(HomePick(bedroom.copy(state = PlayState.PAUSED, nowPlaying = SONG), nothingPlaying = false))
            groups.value = listOf(kitchen, bedroom.copy(state = PlayState.STOPPED))
            assertThat(awaitItem()).isEqualTo(HomePick(kitchen, nothingPlaying = true))
        }
    }

    /** A paused usual room that Home never showed playing is "Nothing playing" (6 §5.1). */
    @Test
    fun aGroupPausedBeforeHomeShowedItIsNothingPlaying() = runTest {
        val paused = kitchen.copy(state = PlayState.PAUSED, nowPlaying = SONG)
        homePicks(MutableStateFlow(listOf(paused, bedroom)), MutableStateFlow("Kitchen-id")).test {
            assertThat(awaitItem()).isEqualTo(HomePick(paused, nothingPlaying = true))
        }
    }
}
```
Create `MusicChecksTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 6 §4.2: checks only while in front and a music screen shows; 2 seconds and 30 seconds; the tab opening. */
@RunWith(AndroidJUnit4::class)
class MusicChecksTest {
    private val system = TestMusicSystem()
    private val systemFlow = MutableStateFlow<MusicSystem?>(system)
    private val inFront = MutableStateFlow(true)

    private fun TestScope.checks() = MusicChecks(systemFlow, inFront, backgroundScope).also { it.start() }

    private fun count(call: String) = system.calls.count { it == call }

    @Test
    fun nothingIsCheckedUntilAScreenWatches() = runTest {
        checks()
        advanceTimeBy(LAYOUT_CHECK_MS * 2)
        runCurrent()
        assertThat(system.calls).isEmpty()
    }

    /** Fails at 1 999 ms if the loop is faster, at 2 000 if slower; and if playback is read before the layout. */
    @Test
    fun watchingChecksTheLayoutThenPlaybackEveryTwoSeconds() = runTest {
        val checks = checks()
        checks.watch()
        runCurrent()
        assertThat(system.calls).containsExactly("checkLayout", "checkPlayback").inOrder()
        advanceTimeBy(PLAYBACK_CHECK_MS - 1)
        runCurrent()
        assertThat(count("checkPlayback")).isEqualTo(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(count("checkPlayback")).isEqualTo(2)
    }

    @Test
    fun theLayoutIsCheckedEveryThirtySeconds() = runTest {
        checks().watch()
        runCurrent()
        advanceTimeBy(LAYOUT_CHECK_MS - 1)
        runCurrent()
        assertThat(count("checkLayout")).isEqualTo(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(count("checkLayout")).isEqualTo(2)
    }

    /** 6 §4.2: the Music tab opening reads the layout at once, though Home was already watching. */
    @Test
    fun theMusicTabOpeningChecksTheLayoutAtOnce() = runTest {
        val checks = checks()
        checks.watch()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        checks.watch(layoutNow = true)
        runCurrent()
        assertThat(count("checkLayout")).isEqualTo(2)
    }

    /** Fails if checks run on after the last screen goes, or while Culvery is behind another app. */
    @Test
    fun checksStopWhenTheLastScreenGoesOrCulveryLeavesTheFront() = runTest {
        val checks = checks()
        val stop = checks.watch()
        runCurrent()
        stop()
        system.calls.clear()
        advanceTimeBy(LAYOUT_CHECK_MS)
        runCurrent()
        assertThat(system.calls).isEmpty()
        checks.watch()
        runCurrent()
        inFront.value = false
        runCurrent()
        system.calls.clear()
        advanceTimeBy(LAYOUT_CHECK_MS)
        runCurrent()
        assertThat(system.calls).isEmpty()
    }

    /** A screen's stop called twice (a recomposition, say) mustn't stop another screen's checks. */
    @Test
    fun stoppingTwiceCountsOnce() = runTest {
        val checks = checks()
        val home = checks.watch()
        checks.watch()
        runCurrent()
        home()
        home()
        system.calls.clear()
        advanceTimeBy(PLAYBACK_CHECK_MS)
        runCurrent()
        assertThat(system.calls).contains("checkPlayback")
    }

    /** The speakers being off is the status line's business; the checks keep trying. */
    @Test
    fun aFailingCheckDoesNotStopTheChecks() = runTest {
        system.failures["checkPlayback"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        checks().watch()
        runCurrent()
        advanceTimeBy(PLAYBACK_CHECK_MS)
        runCurrent()
        assertThat(count("checkPlayback")).isEqualTo(2)
    }

    @Test
    fun noSystemNoChecks() = runTest {
        systemFlow.value = null
        checks().watch()
        runCurrent()
        assertThat(system.calls).isEmpty()
    }
}
```
Fails today: `homePick`, `homePicks`, `tabOrder`, `MusicChecks` and the constants don't exist (nothing compiles).
Fails if: the Home rule picks the earlier start, ignores the usual room, forgets a paused group it showed (also when the card leaves and comes back) or keeps a stopped one; checks run unwatched or behind another app, or at the wrong pace; one failure stops them.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*HomePickTest*' --tests '*MusicViewTest*' --tests '*MusicChecksTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The rule and the order**

Create `HomePick.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.scan

/** What Home shows (6 §5.1): [nothingPlaying] when it shows [group] only because nothing plays. */
data class HomePick(val group: SpeakerGroup, val nothingPlaying: Boolean)

/**
 * 6 §2.2: the playing group; of several, the one with the usual room, else the one that started last. With none
 * playing, [keptId] while it is paused (the group Home last showed playing, so Play resumes it), else the usual room's
 * group, else the first Sonos lists.
 */
fun homePick(groups: List<SpeakerGroup>, usualRoomId: String?, keptId: String?): HomePick? {
    fun SpeakerGroup.hasUsualRoom() = rooms.any { it.id == usualRoomId }
    val playing = groups.filter { it.isPlaying }
    val shown = when (playing.size) {
        0 -> null
        1 -> playing.single()
        else -> playing.firstOrNull { it.hasUsualRoom() } ?: playing.maxByOrNull { it.startedAtMillis ?: Long.MIN_VALUE }
    }
    if (shown != null) return HomePick(shown, nothingPlaying = false)
    groups.firstOrNull { it.id == keptId && it.state == PlayState.PAUSED }?.let { return HomePick(it, nothingPlaying = false) }
    val fallback = groups.firstOrNull { it.hasUsualRoom() } ?: groups.firstOrNull() ?: return null
    return HomePick(fallback, nothingPlaying = true)
}

/** [homePick] over time, remembering what Home last showed playing. */
fun homePicks(groups: Flow<List<SpeakerGroup>>, usualRoomId: Flow<String?>): Flow<HomePick?> =
    combine(groups, usualRoomId, ::Pair)
        .scan<Pair<List<SpeakerGroup>, String?>, HomePick?>(null) { last, (g, usual) ->
            homePick(g, usual, keptId = last?.takeUnless { it.nothingPlaying }?.group?.id)
        }
        .drop(1)

/** The Music tab's order (6 §5.2): playing groups first, then the rest, each in Sonos's order. */
fun tabOrder(groups: List<SpeakerGroup>): List<SpeakerGroup> = groups.sortedBy { !it.isPlaying }
```
Create `MusicView.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope

/** What the screens show: the open system's groups and problem, and Home's pick (6 §2.2). */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MusicView @Inject constructor(speakers: Speakers, settings: MusicSettings, @ApplicationScope scope: CoroutineScope) {
    val groups: Flow<List<SpeakerGroup>> = speakers.system.flatMapLatest { it?.groups ?: flowOf(emptyList()) }
    val problem: Flow<MusicProblem?> = speakers.system.flatMapLatest { it?.problem ?: flowOf(null) }

    /** Held on the application scope, so a room paused from Home is remembered while the card is away (6 §2.2). */
    val home: StateFlow<HomePick?> = homePicks(groups, settings.prefs.map { it.usualRoomId }.distinctUntilChanged())
        .stateIn(scope, SharingStarted.Eagerly, null)
}
```

- [ ] **Step 4: The checks**

Create `MusicChecks.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

/** What plays, and the group volumes, while a music screen shows (6 §4.2). */
const val PLAYBACK_CHECK_MS = 2_000L

/** Rooms, groups and room volumes (6 §4.2). */
const val LAYOUT_CHECK_MS = 30_000L

private const val TAG = "MusicChecks"

/**
 * Checks the speakers on a timer rather than subscribing (6 §4.2): only while Culvery is in front and a screen that shows
 * music (Home's card, the Music tab) is on, which [watch] counts. The layout first, then playback every 2 seconds and the
 * layout every 30, or at once when the Music tab opens.
 */
@Singleton
class MusicChecks internal constructor(
    private val system: StateFlow<MusicSystem?>,
    private val inFront: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(speakers: Speakers, host: AlertHost, @ApplicationScope scope: CoroutineScope) : this(speakers.system, host.inFront, scope)

    private val watchers = MutableStateFlow(0)
    private val layoutAsked = Channel<Unit>(Channel.CONFLATED)

    /** How many music screens are on, for the screens' tests. */
    internal val watching: Int get() = watchers.value

    /** A music screen is on; call the result when it goes. [layoutNow] for the Music tab opening (6 §4.2). */
    fun watch(layoutNow: Boolean = false): () -> Unit {
        watchers.update { it + 1 }
        if (layoutNow) layoutAsked.trySend(Unit)
        val stopped = AtomicBoolean(false)
        return { if (stopped.compareAndSet(false, true)) watchers.update { it - 1 } }
    }

    override fun start() {
        scope.launch {
            combine(system, inFront, watchers) { s, front, n -> s.takeIf { front && n > 0 } }
                .distinctUntilChanged()
                .collectLatest { s -> if (s != null) checking(s) }
        }
    }

    private suspend fun checking(s: MusicSystem) = coroutineScope {
        safely { s.checkLayout() }
        layoutAsked.tryReceive()
        launch {
            while (true) {
                safely { s.checkPlayback() }
                delay(PLAYBACK_CHECK_MS)
            }
        }
        launch {
            while (true) {
                withTimeoutOrNull(LAYOUT_CHECK_MS) { layoutAsked.receive() }
                safely { s.checkLayout() }
            }
        }
    }

    /** A failed check shows in the system's problem; the next one tries again. */
    private suspend fun safely(check: suspend () -> Unit) {
        try {
            check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "A music check failed (${e::class.simpleName})")
        }
    }
}
```
In `di/MusicModule.kt` add (imports `uk.co.siland.culvery.capability.music.MusicChecks`, `uk.co.siland.culvery.core.plugin.Startable`):
```kotlin
    @Binds
    @IntoSet
    abstract fun checks(impl: MusicChecks): Startable
```

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :capability:music:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/music/src
git commit -m "Choose Home's room and check the speakers only while a music screen shows"
```

---
### Task 6: `MusicQuieter`: lowering for the doorbell, room by room (§2.3, §4.6; rulings 10, 29, 30; Review Focus 2, 5)

**Review:** opus.

**Files:**
- Create: `capability/music/src/main/java/uk/co/siland/culvery/capability/music/MusicQuieter.kt`
- Modify: `di/MusicModule.kt` (`@IntoSet` `AlertQuieter`)
- Test: `capability/music/src/test/java/uk/co/siland/culvery/capability/music/MusicQuieterTest.kt`

**Interfaces:**
- Consumes: `AlertQuieter` (Task 2); `MusicSystem.playingNow()`, `roomVolume()`, `setRoomVolume()`, `Speakers.system`, `MusicSettings.current` (Task 4); `TestMusicSystem.roomVolumes`, `failures`, `failOnce`, `gates`, `playingNowAnswer` (Task 4's `TestMusic.kt`).
- Produces: `class MusicQuieter internal constructor(system: () -> MusicSystem?, share: () -> Int) : AlertQuieter`, `@Inject constructor(speakers: Speakers, settings: MusicSettings)`; `internal const val RESTORE_DRIFT = 1`.

- [ ] **Step 1: Write the failing tests**

Create `MusicQuieterTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 6 §4.6: each room of each playing group, remembered before it is set; groups in parallel; restore within 1 of what was
 * set, tried twice; a second ring; a group that can't be read. Kitchen plays alone at 50; Lounge (40) leads Dining room
 * (25), playing; Bedroom is paused.
 */
@RunWith(AndroidJUnit4::class)
class MusicQuieterTest {
    private val kitchen = group("k", "Kitchen", state = PlayState.PLAYING, nowPlaying = SONG, volume = 50)
    private val lounge = group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION, volume = 40)
    private val bedroom = group("b", "Bedroom", state = PlayState.PAUSED, nowPlaying = SONG, volume = 30)
    private val system = TestMusicSystem(listOf(kitchen, lounge, bedroom)).apply { roomVolumes["Dining room-id"] = 25 }
    private var share = 20

    private fun quieter(s: MusicSystem? = system) = MusicQuieter({ s }, { share })

    private fun sets() = system.calls.filter { it.startsWith("setRoomVolume") }

    @Test
    fun eachRoomOfEachPlayingGroupDropsToItsShare() = runTest {
        quieter().quiet()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 10", "setRoomVolume Lounge-id 8", "setRoomVolume Dining room-id 5")
    }

    /** round(v × share): 33 × 20 % is 6.6, so 7. */
    @Test
    fun theShareRounds() = runTest {
        system.roomVolumes["Kitchen-id"] = 33
        system.groups.value = listOf(kitchen)
        quieter().quiet()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 7")
    }

    /** 6 §4.6: room by room keeps a group's balance, even from 0 % (a group volume of 0 would lose it). */
    @Test
    fun aTwoRoomGroupComesBackWithItsBalanceEvenFromNothing() = runTest {
        system.groups.value = listOf(lounge)
        share = 0
        val q = quieter()
        q.quiet()
        assertThat(sets()).containsExactly("setRoomVolume Lounge-id 0", "setRoomVolume Dining room-id 0")
        system.calls.clear()
        q.restore()
        assertThat(sets()).containsExactly("setRoomVolume Lounge-id 40", "setRoomVolume Dining room-id 25")
        share = 20
        system.calls.clear()
        q.quiet()
        q.restore()
        assertThat(sets()).containsExactly(
            "setRoomVolume Lounge-id 8", "setRoomVolume Dining room-id 5", "setRoomVolume Lounge-id 40", "setRoomVolume Dining room-id 25",
        )
    }

    /** Fails if one group is lowered only after another finished. */
    @Test
    fun groupsAreLoweredInParallel() = runTest {
        val slow = CompletableDeferred<Unit>()
        system.gates["roomVolume Kitchen-id"] = slow
        val q = quieter()
        val quieting = launch { q.quiet() }
        runCurrent()
        assertThat(sets()).containsExactly("setRoomVolume Lounge-id 8", "setRoomVolume Dining room-id 5")
        slow.complete(Unit)
        quieting.join()
        assertThat(sets()).contains("setRoomVolume Kitchen-id 10")
    }

    /** 6 §2.3: someone turned the Dining room up while the card showed; it stays as they set it. */
    @Test
    fun restorePutsBackOnlyRoomsNobodyChanged() = runTest {
        val q = quieter()
        q.quiet()
        system.roomVolumes["Dining room-id"] = 15
        system.calls.clear()
        q.restore()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 50", "setRoomVolume Lounge-id 40")
    }

    /** 6 §4.6: within 1 of what was set still restores (Sonos may round); 2 away is someone's change. */
    @Test
    fun aDriftOfOneStillRestores() = runTest {
        val q = quieter()
        q.quiet()
        system.roomVolumes["Kitchen-id"] = 11
        system.roomVolumes["Lounge-id"] = 10
        system.calls.clear()
        q.restore()
        assertThat(sets()).contains("setRoomVolume Kitchen-id 50")
        assertThat(sets()).doesNotContain("setRoomVolume Lounge-id 40")
    }

    /** 6 §4.6: the speaker took the volume but its answer was lost. Fails if a room is remembered only once its command returns. */
    @Test
    fun aCommandThatTookEffectButTimedOutIsStillRestored() = runTest {
        system.failures["setRoomVolume Kitchen-id 10"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        val q = quieter()
        q.quiet()
        system.failures.clear()
        system.calls.clear()
        q.restore()
        assertThat(sets()).contains("setRoomVolume Kitchen-id 50")
    }

    /** 6 §4.6: a failed restore is tried once more. */
    @Test
    fun aFailedRestoreIsTriedOnceMore() = runTest {
        val q = quieter()
        q.quiet()
        system.failOnce["roomVolume Kitchen-id"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        system.calls.clear()
        q.restore()
        assertThat(system.calls.count { it == "roomVolume Kitchen-id" }).isEqualTo(2)
        assertThat(sets()).contains("setRoomVolume Kitchen-id 50")
    }

    /** 6 §4.6: a second ring while lowered does nothing; after the restore the next ring lowers again. */
    @Test
    fun aSecondQuietWhileLoweredDoesNothing() = runTest {
        val q = quieter()
        q.quiet()
        q.quiet()
        assertThat(sets()).hasSize(3)
        q.restore()
        system.calls.clear()
        q.quiet()
        assertThat(sets()).hasSize(3)
    }

    /** A group one of whose rooms can't be read is left as it is (6 §6); the others still drop and come back. */
    @Test
    fun aGroupThatCantBeReadIsSkipped() = runTest {
        system.failures["roomVolume Dining room-id"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        val q = quieter()
        q.quiet()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 10")
        system.failures.clear()
        system.calls.clear()
        q.restore()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 50")
    }

    /** Review Focus 2: on the Calendar tab no checks run, so the last-known state says nothing plays. */
    @Test
    fun lowersWhatIsPlayingNowNotWhatTheLastCheckSaw() = runTest {
        system.groups.value = listOf(kitchen.copy(state = PlayState.STOPPED))
        system.playingNowAnswer = listOf(kitchen)
        quieter().quiet()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 10")
    }

    /** Review Focus 5: a restore asked while the quiet is still lowering waits for it, then puts everything back. */
    @Test
    fun aRestoreWaitsForTheQuietItUndoes() = runTest {
        val slow = CompletableDeferred<Unit>()
        system.gates["setRoomVolume Kitchen-id 10"] = slow
        val q = quieter()
        val quieting = launch { q.quiet() }
        runCurrent()
        val restoring = launch { q.restore() }
        runCurrent()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 10", "setRoomVolume Lounge-id 8", "setRoomVolume Dining room-id 5")
        slow.complete(Unit)
        quieting.join()
        restoring.join()
        assertThat(sets()).containsAtLeast("setRoomVolume Kitchen-id 50", "setRoomVolume Lounge-id 40", "setRoomVolume Dining room-id 25")
    }

    /** Neither a missing system nor a failed read of what plays throws into the shell. */
    @Test
    fun withNoSystemOrNoAnswerNothingHappens() = runTest {
        quieter(s = null).quiet()
        system.failures["playingNow"] = IOException("no speaker")
        val q = quieter()
        q.quiet()
        q.restore()
        assertThat(sets()).isEmpty()
    }
}
```
Fails today: `MusicQuieter` doesn't exist.
Fails if: a paused group drops; the lowering works on the group volume (the balance test); the share is truncated; groups are lowered one after another; a changed room is put back, or a drift of 1 isn't; a room is remembered only after its command returns; a failed restore isn't retried; a second ring lowers again; a half-read group is half-lowered; the lowering trusts the last check; a restore overtakes its quiet.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicQuieterTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The quieter**

Create `MusicQuieter.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import android.util.Log
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.plugin.AlertQuieter

private const val TAG = "MusicQuieter"

/** Sonos may round a volume it was given; a room this close to what was set is still ours to put back (6 §4.6). */
internal const val RESTORE_DRIFT = 1

/**
 * Music's [AlertQuieter] (6 §4.6): each room of each group playing as the bell rings drops to [share] percent of its
 * volume, and goes back afterwards unless someone changed it meanwhile. Room by room, so a group keeps its balance. One
 * lock keeps a restore behind the quiet it undoes. Held in memory only: a restart while lowered leaves those rooms low.
 */
@Singleton
class MusicQuieter internal constructor(private val system: () -> MusicSystem?, private val share: () -> Int) : AlertQuieter {
    @Inject
    constructor(speakers: Speakers, settings: MusicSettings) : this({ speakers.system.value }, { settings.current.value.lowerTo })

    private class Lowered(val room: SpeakerRoom, val was: Int, val set: Int)

    private val lock = Mutex()
    private var lowered: Pair<MusicSystem, List<Lowered>>? = null

    override suspend fun quiet() = lock.withLock {
        if (lowered != null) return@withLock
        val s = system() ?: return@withLock
        val percent = share()
        val playing = attempt { s.playingNow() } ?: return@withLock
        val rooms: MutableList<Lowered> = Collections.synchronizedList(mutableListOf())
        lowered = s to rooms
        coroutineScope { playing.forEach { group -> launch { lowerGroup(s, group, percent, rooms) } } }
    }

    override suspend fun restore() = lock.withLock {
        val (s, rooms) = lowered ?: return@withLock
        lowered = null
        coroutineScope {
            rooms.toList().forEach { room -> launch { if (attempt { putBack(s, room) } == null) attempt { putBack(s, room) } } }
        }
    }

    /** All the group's rooms are read first; a group that can't be read is left as it is (6 §4.6). */
    private suspend fun lowerGroup(s: MusicSystem, group: SpeakerGroup, percent: Int, rooms: MutableList<Lowered>) {
        val read = attempt { group.rooms.map { it to s.roomVolume(it) } } ?: return
        val mine = read.map { (room, was) -> Lowered(room, was, (was * percent / 100.0).roundToInt()) }
        // Remembered before the commands go, so one that took effect but timed out is still put back.
        rooms += mine
        mine.forEach { attempt { s.setRoomVolume(it.room, it.set) } }
    }

    private suspend fun putBack(s: MusicSystem, room: Lowered) {
        if (abs(s.roomVolume(room.room) - room.set) <= RESTORE_DRIFT) s.setRoomVolume(room.room, room.was)
    }

    private suspend fun <T> attempt(step: suspend () -> T): T? = try {
        step()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't change a room's volume for the doorbell (${e::class.simpleName})")
        null
    }
}
```
In `di/MusicModule.kt` add (imports `uk.co.siland.culvery.capability.music.MusicQuieter`, `uk.co.siland.culvery.core.plugin.AlertQuieter`):
```kotlin
    @Binds
    @IntoSet
    abstract fun quieter(impl: MusicQuieter): AlertQuieter
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicQuieterTest*'`
Expected: PASS.

- [ ] **Step 5: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/music/src
git commit -m "Lower each playing room while the door card shows and put back the ones nobody changed"
```

---

### Task 7: `MusicActions`: commands, row lines, Favourites and the §6 words (§4.3, §5.3, §6; ruling 21)

**Review:** sonnet.

**Files:**
- Create: `capability/music/src/main/java/uk/co/siland/culvery/capability/music/MusicActions.kt`
- Modify: `capability/music/src/main/java/uk/co/siland/culvery/capability/music/MusicWords.kt` (the §6 lines)
- Modify: `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` (`ALLOWED` gains `command.name`)
- Test: `capability/music/src/test/java/uk/co/siland/culvery/capability/music/MusicActionsTest.kt`

**Interfaces:**
- Consumes: `MusicSystem`, the exceptions, `MusicProblem`, `Speakers.system` (Task 4).
- Produces:
  - `enum class MusicCommand { PLAY, PAUSE, NEXT, GROUP_VOLUME, ROOM_VOLUME, SHUFFLE, FAVOURITES, FAVOURITE }`
  - `sealed interface FavouritesLoad { data class Ready(val favourites: List<Favourite>); data class Failed(val line: String) }`
  - `class MusicActions internal constructor(system: () -> MusicSystem?, scope: CoroutineScope)`, `@Inject constructor(speakers: Speakers, @ApplicationScope scope)`: `val lines: StateFlow<Map<String, String>>`; `fun play(group)`, `pause(group)`, `next(group)`, `setGroupVolume(group, percent: Int)`, `setRoomVolume(group, room: SpeakerRoom, percent: Int)`, `shuffleEverything(group)`; `suspend fun favourites(): FavouritesLoad`; `suspend fun playFavourite(group, favourite): String?`
  - `MusicWords.kt`: `CANT_REACH_SONOS`, `TABLET_OFFLINE`, `OTHER_SONOS`, `SONOS_REFUSED`, `CANT_PLAY_HERE`, `NO_LIBRARY`; `fun problemLine(problem: MusicProblem): String`

- [ ] **Step 1: Write the failing tests**

In `LogHygieneTest.kt`'s `ALLOWED`, add `"command.name"` after `"reading.outcome"`, and to its KDoc: "`command.name` is a music command's name (`MusicCommand`, 6 §6: the command and its outcome only)." (**pin** until `MusicActions` logs it.)
Create `MusicActionsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** 6 §4.3, §5.3, §6: commands to the system, the row's line when Sonos refuses, Favourites' outcomes, the logs. */
@RunWith(AndroidJUnit4::class)
class MusicActionsTest {
    private val kitchen = group("k", "Kitchen", state = PlayState.PLAYING, nowPlaying = SONG)
    private val system = TestMusicSystem(listOf(kitchen))
    private val station = Favourite("FV:2/1", "Culvery FM", null, FavouriteKind.STREAM)

    private fun TestScope.actions() = MusicActions({ system }, backgroundScope)

    @Test
    fun eachCommandGoesToTheSystem() = runTest {
        val actions = actions()
        actions.pause(kitchen)
        actions.play(kitchen)
        actions.next(kitchen)
        actions.setGroupVolume(kitchen, 45)
        actions.setRoomVolume(kitchen, kitchen.rooms.single(), 35)
        actions.shuffleEverything(kitchen)
        runCurrent()
        assertThat(system.calls).containsExactly(
            "pause k", "play k", "next k", "setGroupVolume k 45", "setRoomVolume Kitchen-id 35", "shuffleEverything k",
        ).inOrder()
    }

    /** 6 §6: "Sonos didn’t do that. Try again." on that row, until its next command. */
    @Test
    fun aRefusedCommandSaysSoOnItsRowUntilTheNextCommand() = runTest {
        val actions = actions()
        system.failures["pause k"] = MusicRefusedException(701)
        actions.pause(kitchen)
        runCurrent()
        assertThat(actions.lines.value).containsExactly("k", "Sonos didn’t do that. Try again.")
        system.failures.clear()
        actions.play(kitchen)
        assertThat(actions.lines.value).isEmpty()
    }

    @Test
    fun anEmptyLibrarySaysSo() = runTest {
        val actions = actions()
        system.failures["shuffleEverything k"] = NoLibraryException()
        actions.shuffleEverything(kitchen)
        runCurrent()
        assertThat(actions.lines.value).containsExactly("k", "Sonos has no music library. Add it in the Sonos app.")
    }

    /** No speaker answering is the status line's to say, once, not every row's. */
    @Test
    fun anUnreachableSpeakerLeavesTheRowToTheStatusLine() = runTest {
        val actions = actions()
        system.failures["play k"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        actions.play(kitchen)
        runCurrent()
        assertThat(actions.lines.value).isEmpty()
    }

    @Test
    fun favouritesLoadOrSayWhy() = runTest {
        val actions = actions()
        system.favouriteList = listOf(station)
        assertThat(actions.favourites()).isEqualTo(FavouritesLoad.Ready(listOf(station)))
        system.failures["favourites"] = MusicUnreachableException(MusicProblem.OFFLINE)
        assertThat(actions.favourites()).isEqualTo(FavouritesLoad.Failed("The tablet is offline. Check the Wi-Fi."))
        system.failures["favourites"] = MusicRefusedException()
        assertThat(actions.favourites()).isEqualTo(FavouritesLoad.Failed("Sonos didn’t do that. Try again."))
    }

    /** 6 §6: "Sonos couldn’t play this here." and the sheet stays; null closes it. */
    @Test
    fun aFavouriteThatCanNotPlayHereSaysSo() = runTest {
        val actions = actions()
        assertThat(actions.playFavourite(kitchen, station)).isNull()
        system.failures["playFavourite k FV:2/1"] = MusicRefusedException(714)
        assertThat(actions.playFavourite(kitchen, station)).isEqualTo("Sonos couldn’t play this here.")
        system.failures["playFavourite k FV:2/1"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        assertThat(actions.playFavourite(kitchen, station)).isEqualTo(
            "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.",
        )
    }

    /** 6 §6 privacy: the command and its outcome only. Fails if a room's name reaches the log. */
    @Test
    fun theLogNamesTheCommandOnly() = runTest {
        ShadowLog.clear()
        val actions = actions()
        system.failures["pause k"] = MusicRefusedException(701)
        actions.pause(kitchen)
        runCurrent()
        val logs = ShadowLog.getLogs().filter { it.tag == "MusicActions" }
        assertThat(logs.map { it.msg }).containsExactly("PAUSE failed (MusicRefusedException)")
    }

    @Test
    fun theProblemLinesAreTheSpecs() {
        assertThat(MusicProblem.entries.map(::problemLine)).containsExactly(
            "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.",
            "The tablet is offline. Check the Wi-Fi.",
            "Culvery found a different Sonos system. Find Sonos again in Settings › Music.",
        ).inOrder()
    }
}
```
Fails today: `MusicActions`, `FavouritesLoad` and `problemLine` don't exist.
Fails if: a command goes nowhere; a refusal shows on no row or stays after the next command; offline and unreachable lines are swapped; a room's name is logged.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicActionsTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The words**

Append to `MusicWords.kt`:
```kotlin
internal const val CANT_REACH_SONOS = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi."
internal const val TABLET_OFFLINE = "The tablet is offline. Check the Wi-Fi."
internal const val OTHER_SONOS = "Culvery found a different Sonos system. Find Sonos again in Settings › Music."
internal const val SONOS_REFUSED = "Sonos didn’t do that. Try again."
internal const val CANT_PLAY_HERE = "Sonos couldn’t play this here."
internal const val NO_LIBRARY = "Sonos has no music library. Add it in the Sonos app."

/** The one line the Music tab, the Home card and Settings › Music show when something is wrong (6 §6). */
fun problemLine(problem: MusicProblem): String = when (problem) {
    MusicProblem.UNREACHABLE -> CANT_REACH_SONOS
    MusicProblem.OFFLINE -> TABLET_OFFLINE
    MusicProblem.OTHER_HOUSEHOLD -> OTHER_SONOS
}
```

- [ ] **Step 4: The actions**

Create `MusicActions.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope

private const val TAG = "MusicActions"

/** What the household asked of the speakers, the only thing the logs name (6 §6). */
enum class MusicCommand { PLAY, PAUSE, NEXT, GROUP_VOLUME, ROOM_VOLUME, SHUFFLE, FAVOURITES, FAVOURITE }

sealed interface FavouritesLoad {
    data class Ready(val favourites: List<Favourite>) : FavouritesLoad

    data class Failed(val line: String) : FavouritesLoad
}

/**
 * The screens' commands (6 §4.3): each runs on the application scope, so leaving the screen doesn't cancel it. When Sonos
 * refuses one, or there's no library, its group's row says so until that group's next command; no speaker answering is
 * the status line's to say.
 */
@Singleton
class MusicActions internal constructor(private val system: () -> MusicSystem?, private val scope: CoroutineScope) {
    @Inject
    constructor(speakers: Speakers, @ApplicationScope scope: CoroutineScope) : this({ speakers.system.value }, scope)

    private val _lines = MutableStateFlow<Map<String, String>>(emptyMap())

    /** A group's line after its last command went wrong (6 §6), by group id. */
    val lines: StateFlow<Map<String, String>> = _lines.asStateFlow()

    fun play(group: SpeakerGroup) = send(group, MusicCommand.PLAY) { it.play(group) }

    fun pause(group: SpeakerGroup) = send(group, MusicCommand.PAUSE) { it.pause(group) }

    fun next(group: SpeakerGroup) = send(group, MusicCommand.NEXT) { it.next(group) }

    fun setGroupVolume(group: SpeakerGroup, percent: Int) = send(group, MusicCommand.GROUP_VOLUME) { it.setGroupVolume(group, percent) }

    fun setRoomVolume(group: SpeakerGroup, room: SpeakerRoom, percent: Int) = send(group, MusicCommand.ROOM_VOLUME) { it.setRoomVolume(room, percent) }

    fun shuffleEverything(group: SpeakerGroup) = send(group, MusicCommand.SHUFFLE) { it.shuffleEverything(group) }

    /** Read each time the sheet opens (6 §4.5). */
    suspend fun favourites(): FavouritesLoad {
        val s = system() ?: return FavouritesLoad.Failed(CANT_REACH_SONOS)
        return try {
            FavouritesLoad.Ready(s.favourites())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure(MusicCommand.FAVOURITES, e)
            FavouritesLoad.Failed(sheetLine(e, SONOS_REFUSED))
        }
    }

    /** Plays [favourite] in [group]: null when it played, else the line the sheet shows as it stays open (6 §5.3, §6). */
    suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite): String? {
        val s = system() ?: return CANT_REACH_SONOS
        return try {
            s.playFavourite(group, favourite)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure(MusicCommand.FAVOURITE, e)
            sheetLine(e, CANT_PLAY_HERE)
        }
    }

    private fun send(group: SpeakerGroup, command: MusicCommand, step: suspend (MusicSystem) -> Unit) {
        _lines.update { it - group.id }
        val s = system() ?: return
        scope.launch {
            try {
                step(s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure(command, e)
                rowLine(e)?.let { line -> _lines.update { it + (group.id to line) } }
            }
        }
    }

    private fun rowLine(e: Exception): String? = when (e) {
        is MusicRefusedException -> SONOS_REFUSED
        is NoLibraryException -> NO_LIBRARY
        else -> null
    }

    private fun sheetLine(e: Exception, refused: String): String = when (e) {
        is MusicUnreachableException -> problemLine(e.problem)
        is MusicRefusedException -> refused
        else -> CANT_REACH_SONOS
    }

    private fun logFailure(command: MusicCommand, e: Exception) {
        Log.w(TAG, "${command.name} failed (${e::class.simpleName})")
    }
}
```

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicActionsTest*'` and `./gradlew -p build-logic :convention:test`
Expected: PASS.

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/music/src build-logic/convention/src/test/kotlin/LogHygieneTest.kt
git commit -m "Send the music commands and say on the row when Sonos refuses one"
```

---
### Task 8: `:provider:music-sonos` 1: `SonosXml`, the speaker-address rule and the documented fixtures (§3.2, §6, §7, §8.2; rulings 6, 7, 13, 20, 22, 27; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify: `settings.gradle.kts` (`include(":provider:music-sonos")` after `include(":provider:door-ring")`)
- Create: `provider/music-sonos/build.gradle.kts`
- Create (under `provider/music-sonos/src/main/java/uk/co/siland/culvery/provider/music_sonos/`): `SpeakerAuthority.kt`, `SonosXml.kt`
- Test: `provider/music-sonos/src/test/java/uk/co/siland/culvery/provider/music_sonos/SonosFixtures.kt`, `SonosXmlTest.kt`

**Interfaces:**
- Consumes: `PlayState`, `isHomeNetworkHost` (Task 4).
- Produces (package `uk.co.siland.culvery.provider.music_sonos`, all `internal`):
  - `SpeakerAuthority.kt`: `const val SONOS_PORT = 1400`; `fun isSpeakerAuthority(host: String, port: Int): Boolean`; `fun isSpeakerAddress(address: String, accept: (String, Int) -> Boolean = ::isSpeakerAuthority): Boolean`
  - `data class ZoneMember(val uuid: String, val name: String, val address: String)`; `data class ZoneGroupInfo(val id: String, val coordinator: String, val members: List<ZoneMember>)` (leader first)
  - `data class TrackInfo(val uri: String?, val title: String?, val artist: String?, val album: String?, val artUri: String?, val programme: String?)`
  - `data class MediaInfo(val uri: String?, val title: String?, val artUri: String?)`
  - `data class DidlItem(val id: String, val title: String, val artUri: String?, val uri: String?, val metadata: String?, val metadataClass: String?)`; `data class BrowseResult(val items: List<DidlItem>, val total: Int)`
  - `class SonosXmlException : Exception`
  - `fun hostAndPort(url: String): Pair<String, Int>?`
  - `object SonosXml { zoneGroups(answer, accept: (String, Int) -> Boolean = ::isSpeakerAuthority), householdId, transportState, position, media, volume, browse, faultCode, redact }`
  - Tests: `SonosFixtures` (constants and builders below)

- [ ] **Step 1: The module**

Create `provider/music-sonos/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:music"))
    implementation(project(":core:ui"))
    implementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}
```
`settings.gradle.kts`: `include(":provider:music-sonos")` after `include(":provider:door-ring")`. (The app takes it in Task 11.)

- [ ] **Step 2: The fixtures**

Create `provider/music-sonos/src/test/java/uk/co/siland/culvery/provider/music_sonos/SonosFixtures.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

/**
 * Sonos's answers as its UPnP services give them, built from Sonos's published service descriptions and SoCo's and
 * node-sonos's examples (6 §7); replaced by the household's redacted captures (6 §8.2). Rooms are generic; the ids and
 * the household are made up.
 */
internal object SonosFixtures {
    const val HOUSEHOLD = "Sonos_aBcDeFgHiJkLmNoP"
    const val KITCHEN = "RINCON_000E58AAAAAA01400"
    const val LOUNGE = "RINCON_000E58BBBBBB01400"
    const val DINING = "RINCON_000E58CCCCCC01400"
    const val SUB = "RINCON_000E58DDDDDD01400"

    private const val DIDL_OPEN =
        """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">"""

    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** A SOAP answer to [action] with [values], each escaped as Sonos escapes them. */
    fun soap(action: String, vararg values: Pair<String, String>): String =
        """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>""" +
            """<u:${action}Response xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">""" +
            values.joinToString("") { (name, value) -> "<$name>${escape(value)}</$name>" } +
            """</u:${action}Response></s:Body></s:Envelope>"""

    fun fault(code: Int) =
        """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>$code</errorCode></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""

    /** Kitchen alone; Lounge leading Dining room (listed first), with a sub bonded to the Lounge that the app hides. */
    fun topology(kitchen: String, lounge: String, dining: String, kitchenName: String = "Kitchen") = soap(
        "GetZoneGroupState",
        "ZoneGroupState" to
            """<ZoneGroupState><ZoneGroups>""" +
            """<ZoneGroup Coordinator="$KITCHEN" ID="$KITCHEN:12"><ZoneGroupMember UUID="$KITCHEN" Location="http://$kitchen/xml/device_description.xml" ZoneName="${escape(kitchenName)}" Icon="x-rincon-roomicon:kitchen" Invisible="0"/></ZoneGroup>""" +
            """<ZoneGroup Coordinator="$LOUNGE" ID="$LOUNGE:34"><ZoneGroupMember UUID="$DINING" Location="http://$dining/xml/device_description.xml" ZoneName="Dining room"/>""" +
            """<ZoneGroupMember UUID="$LOUNGE" Location="http://$lounge/xml/device_description.xml" ZoneName="Lounge"><Satellite UUID="$SUB" Location="http://$lounge/xml/device_description.xml" ZoneName="Lounge" Invisible="1"/></ZoneGroupMember>""" +
            """<ZoneGroupMember UUID="$SUB" Location="http://$lounge/xml/device_description.xml" ZoneName="Lounge" Invisible="1"/></ZoneGroup>""" +
            """</ZoneGroups><VanishedDevices/></ZoneGroupState>""",
    )

    fun household(id: String = HOUSEHOLD) = soap("GetHouseholdID", "CurrentHouseholdID" to id)

    /** An answer that declares an entity: no speaker sends one, so it is refused before parsing. */
    const val HOUSEHOLD_WITH_ENTITY =
        """<?xml version="1.0"?><!DOCTYPE s:Envelope [<!ENTITY room "Kitchen">]><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>""" +
            """<u:GetHouseholdIDResponse xmlns:u="urn:schemas-upnp-org:service:DeviceProperties:1"><CurrentHouseholdID>&room;</CurrentHouseholdID></u:GetHouseholdIDResponse></s:Body></s:Envelope>"""

    fun transport(state: String) = soap("GetTransportInfo", "CurrentTransportState" to state, "CurrentTransportStatus" to "OK", "CurrentSpeed" to "1")

    fun volume(volume: Int) = soap("GetVolume", "CurrentVolume" to "$volume")

    fun groupVolume(volume: Int) = soap("GetGroupVolume", "CurrentVolume" to "$volume")

    /** A FLAC from the NAS's music library, its art on the speaker. */
    const val LIBRARY_TRACK = DIDL_OPEN +
        """<item id="-1" parentID="-1" restricted="true"><res protocolInfo="x-file-cifs:*:audio/flac:*" duration="0:04:12">x-file-cifs://nas/music/Artist/Album/01%20Song.flac</res>""" +
        """<r:streamContent></r:streamContent><upnp:albumArtURI>/getaa?s=1&amp;u=x-file-cifs%3a%2f%2fnas%2fmusic%2fArtist%2fAlbum%2f01%2520Song.flac</upnp:albumArtURI>""" +
        """<dc:title>Song</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class><dc:creator>Artist</dc:creator><upnp:album>Album</upnp:album></item></DIDL-Lite>"""

    /** A BBC station from BBC Sounds: the programme in radioShowMd, the stream's own art on the internet. */
    const val RADIO_TRACK = DIDL_OPEN +
        """<item id="-1" parentID="-1" restricted="true"><res protocolInfo="sonos.com-http:*:application/x-mpegURL:*">x-sonosapi-hls:stations%7eBBC6%7eBBC6?sid=325&amp;flags=8224&amp;sn=1</res>""" +
        """<r:streamContent>ZPSTR_CONNECTING</r:streamContent><r:radioShowMd>The Breakfast Show,p0abc123</r:radioShowMd>""" +
        """<upnp:albumArtURI>https://art.example/p0abc123.jpg</upnp:albumArtURI><dc:title>x-sonosapi-hls:stations%7eBBC6%7eBBC6?sid=325&amp;flags=8224&amp;sn=1</dc:title>""" +
        """<upnp:class>object.item</upnp:class></item></DIDL-Lite>"""

    const val RADIO_STATION = DIDL_OPEN +
        """<item id="-1" parentID="-1" restricted="true"><dc:title>Culvery FM</dc:title><upnp:class>object.item.audioItem.audioBroadcast</upnp:class>""" +
        """<desc id="cdudn" nameSpace="urn:schemas-rinconnetworks-com:metadata-1-0/">SA_RINCON83207_</desc></item></DIDL-Lite>"""

    const val RADIO_URI = "x-sonosapi-hls:stations%7eBBC6%7eBBC6?sid=325&flags=8224&sn=1"
    const val TRACK_URI = "x-file-cifs://nas/music/Artist/Album/01%20Song.flac"

    fun position(uri: String = TRACK_URI, metadata: String = LIBRARY_TRACK) =
        soap("GetPositionInfo", "Track" to "1", "TrackDuration" to "0:04:12", "TrackMetaData" to metadata, "TrackURI" to uri, "RelTime" to "0:01:02")

    fun media(uri: String, metadata: String = "") =
        soap("GetMediaInfo", "NrTracks" to "12", "MediaDuration" to "NOT_IMPLEMENTED", "CurrentURI" to uri, "CurrentURIMetaData" to metadata, "NextURI" to "")

    /** Stopped with nothing loaded: Sonos answers NOT_IMPLEMENTED for the metadata. */
    val emptyPosition = soap("GetPositionInfo", "Track" to "0", "TrackMetaData" to "NOT_IMPLEMENTED", "TrackURI" to "")

    fun queue(leader: String) = "x-rincon-queue:$leader#0"

    /** The household's Favourites (FV:2): a station, a NAS playlist, and a single track. */
    val favourites = browse(
        total = 3,
        """<item id="FV:2/5" parentID="FV:2" restricted="false"><dc:title>Culvery FM</dc:title><upnp:class>object.itemobject.item.sonos-favorite</upnp:class>""" +
            """<upnp:albumArtURI>https://art.example/station.png</upnp:albumArtURI><r:type>instantPlay</r:type><res protocolInfo="x-rincon-mp3radio:*:*:*">${escape(RADIO_URI)}</res>""" +
            """<r:resMD>${escape(RADIO_STATION)}</r:resMD></item>""",
        """<item id="FV:2/9" parentID="FV:2" restricted="false"><dc:title>Sunday &amp; slow</dc:title><upnp:class>object.itemobject.item.sonos-favorite</upnp:class>""" +
            """<upnp:albumArtURI>/getaa?s=1&amp;u=x-rincon-playlist</upnp:albumArtURI><r:type>instantPlay</r:type><res protocolInfo="file:*:audio/mpegurl:*">file:///jffs/settings/savedqueues.rsq#3</res>""" +
            """<r:resMD>${escape(DIDL_OPEN + """<item id="SQ:3" parentID="SQ:" restricted="true"><dc:title>Sunday &amp; slow</dc:title><upnp:class>object.container.playlistContainer</upnp:class></item></DIDL-Lite>""")}</r:resMD></item>""",
        """<item id="FV:2/12" parentID="FV:2" restricted="false"><dc:title>One song</dc:title><upnp:class>object.itemobject.item.sonos-favorite</upnp:class>""" +
            """<res protocolInfo="x-file-cifs:*:audio/flac:*">$TRACK_URI</res><r:resMD>${escape(LIBRARY_TRACK)}</r:resMD></item>""",
    )

    fun library(total: Int) = browse(total = total, *(if (total == 0) emptyArray() else arrayOf(
        """<item id="S://nas/music/Artist/Album/01%20Song.flac" parentID="A:TRACKS" restricted="true"><res protocolInfo="x-file-cifs:*:audio/flac:*">$TRACK_URI</res><dc:title>Song</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class></item>""",
    )))

    fun browse(total: Int, vararg items: String) = soap(
        "Browse",
        "Result" to DIDL_OPEN + items.joinToString("") + "</DIDL-Lite>",
        "NumberReturned" to "${items.size}",
        "TotalMatches" to "$total",
        "UpdateID" to "1",
    )
}
```

- [ ] **Step 3: Write the failing tests**

Create `SonosXmlTest.kt` (no `@RunWith`: §3.2 says plain JVM, and it must stay so):
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.DINING
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.KITCHEN
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.LOUNGE

/** 6 §7: Sonos's answers read on the plain JVM, from the documented fixtures. */
class SonosXmlTest {
    private val topology = SonosFixtures.topology("192.168.1.20:1400", "192.168.1.21:1400", "192.168.1.22:1400")

    /** Fails if the hidden sub shows as a room, the leader isn't first, or an address keeps its path. */
    @Test
    fun theTopologyGivesEachGroupItsVisibleRoomsLeaderFirst() {
        assertThat(SonosXml.zoneGroups(topology)).containsExactly(
            ZoneGroupInfo("$KITCHEN:12", KITCHEN, listOf(ZoneMember(KITCHEN, "Kitchen", "192.168.1.20:1400"))),
            ZoneGroupInfo(
                "$LOUNGE:34", LOUNGE,
                listOf(ZoneMember(LOUNGE, "Lounge", "192.168.1.21:1400"), ZoneMember(DINING, "Dining room", "192.168.1.22:1400")),
            ),
        ).inOrder()
    }

    /** A room named with "&", "’" and "<" survives the two levels of escaping. */
    @Test
    fun anEscapedRoomNameIsReadAsWritten() {
        val odd = SonosFixtures.topology("192.168.1.20:1400", "192.168.1.21:1400", "192.168.1.22:1400", kitchenName = "Tom & Jo’s <den>")
        assertThat(SonosXml.zoneGroups(odd).first().members.single().name).isEqualTo("Tom & Jo’s <den>")
    }

    @Test
    fun theHouseholdId() {
        assertThat(SonosXml.householdId(SonosFixtures.household())).isEqualTo(SonosFixtures.HOUSEHOLD)
    }

    @Test
    fun transportStates() {
        val states = listOf("PLAYING", "PAUSED_PLAYBACK", "STOPPED", "TRANSITIONING", "NO_MEDIA_PRESENT")
        assertThat(states.map { SonosXml.transportState(SonosFixtures.transport(it)) })
            .containsExactly(PlayState.PLAYING, PlayState.PAUSED, PlayState.STOPPED, PlayState.TRANSITIONING, PlayState.STOPPED).inOrder()
    }

    @Test
    fun aLibraryTrack() {
        assertThat(SonosXml.position(SonosFixtures.position())).isEqualTo(
            TrackInfo(
                SonosFixtures.TRACK_URI, "Song", "Artist", "Album",
                "/getaa?s=1&u=x-file-cifs%3a%2f%2fnas%2fmusic%2fArtist%2fAlbum%2f01%2520Song.flac", programme = null,
            ),
        )
    }

    /** The station is GetMediaInfo's; the programme radioShowMd's without its id; ZPSTR_ placeholders are nothing. */
    @Test
    fun aBbcRadioStream() {
        val track = SonosXml.position(SonosFixtures.position(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_TRACK))
        assertThat(track.programme).isEqualTo("The Breakfast Show")
        assertThat(track.artUri).isEqualTo("https://art.example/p0abc123.jpg")
        assertThat(SonosXml.media(SonosFixtures.media(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_STATION)))
            .isEqualTo(MediaInfo(SonosFixtures.RADIO_URI, "Culvery FM", null))
    }

    @Test
    fun stoppedWithNothingLoaded() {
        assertThat(SonosXml.position(SonosFixtures.emptyPosition)).isEqualTo(TrackInfo(null, null, null, null, null, null))
        assertThat(SonosXml.media(SonosFixtures.media(""))).isEqualTo(MediaInfo(null, null, null))
    }

    @Test
    fun volumes() {
        assertThat(SonosXml.volume(SonosFixtures.volume(27))).isEqualTo(27)
        assertThat(SonosXml.volume(SonosFixtures.groupVolume(100))).isEqualTo(100)
    }

    /** 6 §4.5: each Favourite's name, art, link and metadata; its kind comes from the metadata's class. */
    @Test
    fun favourites() {
        val result = SonosXml.browse(SonosFixtures.favourites)
        assertThat(result.total).isEqualTo(3)
        assertThat(result.items.map { listOf(it.id, it.title, it.uri, it.metadataClass) }).containsExactly(
            listOf("FV:2/5", "Culvery FM", SonosFixtures.RADIO_URI, "object.item.audioItem.audioBroadcast"),
            listOf("FV:2/9", "Sunday & slow", "file:///jffs/settings/savedqueues.rsq#3", "object.container.playlistContainer"),
            listOf("FV:2/12", "One song", SonosFixtures.TRACK_URI, "object.item.audioItem.musicTrack"),
        ).inOrder()
        assertThat(result.items.first().metadata).contains("<dc:title>Culvery FM</dc:title>")
    }

    @Test
    fun theLibraryBrowseCountsItsTracks() {
        assertThat(SonosXml.browse(SonosFixtures.library(total = 23_456)).total).isEqualTo(23_456)
        assertThat(SonosXml.browse(SonosFixtures.library(total = 0))).isEqualTo(BrowseResult(emptyList(), 0))
    }

    @Test
    fun aFaultsCode() {
        assertThat(SonosXml.faultCode(SonosFixtures.fault(701))).isEqualTo(701)
        assertThat(SonosXml.faultCode("not xml")).isNull()
    }

    /** No cause and no text: either could quote the answer into a log. */
    @Test
    fun anAnswerOfTheWrongShapeIsASonosXmlException() {
        assertThrows(SonosXmlException::class.java) { SonosXml.householdId("<html>router login</html>") }
        assertThrows(SonosXmlException::class.java) { SonosXml.transportState("not xml") }
        assertThrows(SonosXmlException::class.java) { SonosXml.volume(SonosFixtures.soap("GetVolume", "CurrentVolume" to "loud")) }
    }

    @Test
    fun aHostAndPortFromALocation() {
        assertThat(hostAndPort("http://192.168.1.20:1400/xml/device_description.xml")).isEqualTo("192.168.1.20" to 1400)
        assertThat(hostAndPort("not a url")).isNull()
        assertThat(hostAndPort("http://192.168.1.20/xml")).isNull()
    }

    /** 6 §6 privacy, ruling 20: a speaker on the home network, on Sonos's port, and nothing else. */
    @Test
    fun onlyAHomeSpeakerOnItsPortMayBeContacted() {
        assertThat(isSpeakerAuthority("192.168.1.20", 1400)).isTrue()
        assertThat(isSpeakerAuthority("192.168.1.20", 80)).isFalse()
        assertThat(isSpeakerAuthority("8.8.8.8", 1400)).isFalse()
        assertThat(isSpeakerAddress("192.168.1.20:1400")).isTrue()
        listOf("192.168.1.20", "localhost:1400", "8.8.8.8:1400", ":1400", "192.168.1.20:port").forEach { assertThat(isSpeakerAddress(it)).isFalse() }
    }

    /** Review Focus 4: a topology naming an internet address. Fails if the member is kept; a group whose leader goes, goes. */
    @Test
    fun aMemberOffTheHomeNetworkIsDropped() {
        val groups = SonosXml.zoneGroups(SonosFixtures.topology("192.168.1.20:1400", "8.8.8.8:80", "192.168.1.22:1400"))
        assertThat(groups.map { it.coordinator }).containsExactly(KITCHEN)
    }

    /** Ruling 27: a DOCTYPE or an entity is refused before parsing, in the answer or in the DIDL inside it. */
    @Test
    fun aDoctypeOrAnEntityIsRefused() {
        assertThrows(SonosXmlException::class.java) { SonosXml.householdId(SonosFixtures.HOUSEHOLD_WITH_ENTITY) }
        val inner = """<!DOCTYPE d [<!ENTITY t "x">]>""" + SonosFixtures.LIBRARY_TRACK
        assertThrows(SonosXmlException::class.java) { SonosXml.position(SonosFixtures.position(metadata = inner)) }
    }

    /** 6 §8.2: a capture keeps the shape; no room, title, link, id, household or address survives. */
    @Test
    fun theCaptureShapeReplacesNamesTitlesLinksIdsAndAddresses() {
        val answers = listOf(
            topology, SonosFixtures.household(), SonosFixtures.position(), SonosFixtures.favourites,
            SonosFixtures.media(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_STATION),
        )
        val shapes = answers.joinToString("\n", transform = SonosXml::redact)
        listOf("Kitchen", "Lounge", "Dining room", "Song", "Artist", "Album", "Culvery FM", "Sunday", "One song",
            "192.168.1.2", "AAAAAA", SonosFixtures.HOUSEHOLD, "x-file-cifs://nas", "art.example").forEach {
            assertThat(shapes).doesNotContain(it)
        }
        assertThat(shapes).contains("ZoneName=\"[room]\"")
        assertThat(shapes).contains("<dc:title>[text]</dc:title>")
        assertThat(shapes).contains("RINCON_[id]")
    }
}
```
Fails today: `SonosXml` and its types don't exist.
Fails if: a hidden or bonded speaker, or one off the home network, shows as a room; a DOCTYPE or entity is parsed; the leader isn't first; escaped names come out wrong; PAUSED_PLAYBACK isn't PAUSED; the programme keeps its id or a `ZPSTR_` placeholder shows; a Favourite's class is the Favourite's own rather than its metadata's; a malformed answer throws something that could carry its text; the capture keeps a name.

- [ ] **Step 4: Run them to see them fail**

Run: `./gradlew :provider:music-sonos:testDebugUnitTest --tests '*SonosXmlTest*'`
Expected: FAIL to compile.

- [ ] **Step 5: The address rule and the parser**

Create `provider/music-sonos/src/main/java/uk/co/siland/culvery/provider/music_sonos/SpeakerAuthority.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import uk.co.siland.culvery.capability.music.isHomeNetworkHost

internal const val SONOS_PORT = 1400

/** Whether Culvery may contact [host]:[port]: a speaker on the home network, on Sonos's port (6 §6 privacy). */
internal fun isSpeakerAuthority(host: String, port: Int): Boolean = port == SONOS_PORT && isHomeNetworkHost(host)

/** The same for "host:port"; tests that run speakers on this machine pass their own [accept]. */
internal fun isSpeakerAddress(address: String, accept: (String, Int) -> Boolean = ::isSpeakerAuthority): Boolean {
    val host = address.substringBeforeLast(':', "")
    val port = address.substringAfterLast(':', "").toIntOrNull() ?: return false
    return host.isNotEmpty() && accept(host, port)
}
```
Create `provider/music-sonos/src/main/java/uk/co/siland/culvery/provider/music_sonos/SonosXml.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URISyntaxException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import uk.co.siland.culvery.capability.music.PlayState

internal data class ZoneMember(val uuid: String, val name: String, val address: String)

/** A Sonos group: its id, its leader's id, and its visible rooms with the leader first. */
internal data class ZoneGroupInfo(val id: String, val coordinator: String, val members: List<ZoneMember>)

internal data class TrackInfo(val uri: String?, val title: String?, val artist: String?, val album: String?, val artUri: String?, val programme: String?)

internal data class MediaInfo(val uri: String?, val title: String?, val artUri: String?)

/** One DIDL-Lite item from a browse; [metadata] is its `r:resMD`, which Sonos wants back when it is played. */
internal data class DidlItem(val id: String, val title: String, val artUri: String?, val uri: String?, val metadata: String?, val metadataClass: String?)

internal data class BrowseResult(val items: List<DidlItem>, val total: Int)

/** An answer that isn't the shape asked for. No cause and no text: either could quote the answer. */
internal class SonosXmlException : Exception("A Sonos answer couldn't be read")

/** ("192.168.1.20", 1400) from a speaker's description URL; null without a host and port. */
internal fun hostAndPort(url: String): Pair<String, Int>? = try {
    URI(url).takeIf { it.host != null && it.port > 0 }?.let { it.host to it.port }
} catch (e: URISyntaxException) {
    null
}

/** Sonos's SOAP answers and their DIDL-Lite (6 §3.2). Plain JVM: no Android, so its tests need no emulator. */
internal object SonosXml {
    private const val NOTHING = "NOT_IMPLEMENTED"
    private const val PLACEHOLDER = "ZPSTR_"
    private const val UNESCAPE_DEPTH = 3

    /** Each group's visible rooms, leader first; a member [accept] refuses is dropped, and so is a group without its leader (6 §6). */
    fun zoneGroups(answer: String, accept: (String, Int) -> Boolean = ::isSpeakerAuthority): List<ZoneGroupInfo> =
        parse(text(answer, "ZoneGroupState")).all("ZoneGroup").mapNotNull { group ->
            val leader = group.getAttribute("Coordinator")
            val members = group.children("ZoneGroupMember")
                .filter { it.getAttribute("Invisible") != "1" && it.getAttribute("IsZoneBridge") != "1" }
                .mapNotNull { m ->
                    hostAndPort(m.getAttribute("Location"))
                        ?.takeIf { (host, port) -> accept(host, port) }
                        ?.let { (host, port) -> ZoneMember(m.getAttribute("UUID"), m.getAttribute("ZoneName"), "$host:$port") }
                }
                .sortedBy { it.uuid != leader }
            if (members.none { it.uuid == leader }) null else ZoneGroupInfo(group.getAttribute("ID"), leader, members)
        }

    fun householdId(answer: String): String = text(answer, "CurrentHouseholdID")

    fun transportState(answer: String): PlayState = when (text(answer, "CurrentTransportState")) {
        "PLAYING" -> PlayState.PLAYING
        "PAUSED_PLAYBACK" -> PlayState.PAUSED
        "TRANSITIONING" -> PlayState.TRANSITIONING
        else -> PlayState.STOPPED
    }

    fun position(answer: String): TrackInfo {
        val root = parse(answer)
        val item = didlItem(root.text("TrackMetaData"))
        return TrackInfo(
            uri = root.text("TrackURI"),
            title = item?.text("title"),
            artist = item?.text("creator") ?: item?.text("artist"),
            album = item?.text("album"),
            artUri = item?.text("albumArtURI"),
            programme = item?.let(::programme),
        )
    }

    fun media(answer: String): MediaInfo {
        val root = parse(answer)
        val item = didlItem(root.text("CurrentURIMetaData"))
        return MediaInfo(root.text("CurrentURI"), item?.text("title"), item?.text("albumArtURI"))
    }

    fun volume(answer: String): Int = text(answer, "CurrentVolume").toIntOrNull()?.coerceIn(0, 100) ?: throw SonosXmlException()

    fun browse(answer: String): BrowseResult {
        val root = parse(answer)
        val total = root.text("TotalMatches")?.toIntOrNull() ?: throw SonosXmlException()
        val result = root.text("Result") ?: return BrowseResult(emptyList(), total)
        val items = parse(result).all("item").map { item ->
            val metadata = item.text("resMD")
            DidlItem(item.getAttribute("id"), item.text("title").orEmpty(), item.text("albumArtURI"), item.text("res"), metadata, didlItem(metadata)?.text("class"))
        }
        return BrowseResult(items, total)
    }

    fun faultCode(answer: String): Int? = try {
        parse(answer).text("errorCode")?.toIntOrNull()
    } catch (e: SonosXmlException) {
        null
    }

    private val ENTITIES = listOf("&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&amp;" to "&")
    private val SPOKEN_TEXT = Regex(
        """<((?:\w+:)?(?:title|creator|album|artist|albumArtist|streamContent|radioShowMd|description|albumArtURI|res|""" +
            """CurrentURI|NextURI|TrackURI|AVTransportURI|NextAVTransportURI|EnqueuedTransportURI))(\s[^>]*)?>[^<]*</\1>""",
    )
    // A closing quote is ["]: a raw string ending in four quotes would end early for the copy scan.
    private val NAME_ATTRIBUTE = Regex("""(\w*Name)="[^"]*["]""")
    private val LOCATION_ATTRIBUTE = Regex("""(Location)="[^"]*["]""")
    private val HOUSEHOLD = Regex("""Sonos_[A-Za-z0-9._-]+""")
    private val SPEAKER_ID = Regex("""RINCON_[0-9A-Fa-f]+""")
    private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")

    /**
     * An answer's shape for a capture (6 §8.2): unescaped down to its DIDL, then every room name, title, artist, link,
     * speaker id, household and address replaced.
     */
    fun redact(answer: String): String {
        var text = answer
        repeat(UNESCAPE_DEPTH) { text = ENTITIES.fold(text) { t, (from, to) -> t.replace(from, to) } }
        return text
            .replace(SPOKEN_TEXT) { "<${it.groupValues[1]}${it.groupValues[2]}>[text]</${it.groupValues[1]}>" }
            .replace(NAME_ATTRIBUTE) { "${it.groupValues[1]}=\"[room]\"" }
            .replace(LOCATION_ATTRIBUTE) { "${it.groupValues[1]}=\"[address]\"" }
            .replace(HOUSEHOLD, "Sonos_[household]")
            .replace(SPEAKER_ID, "RINCON_[id]")
            .replace(IPV4, "[address]")
    }

    /** The programme from a station's metadata: radioShowMd without its id, else the stream's words unless a placeholder. */
    private fun programme(item: Element): String? {
        val show = item.text("radioShowMd")?.substringBeforeLast(',')?.takeIf { it.isNotBlank() }
        val stream = item.text("streamContent")?.takeIf { !it.startsWith(PLACEHOLDER) }
        return show ?: stream
    }

    private fun didlItem(didl: String?): Element? = didl?.let { parse(it).all("item").firstOrNull() ?: parse(it).all("container").firstOrNull() }

    private fun text(answer: String, name: String): String = parse(answer).text(name) ?: throw SonosXmlException()

    private fun factory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        try {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (e: ParserConfigurationException) {
            // Android's parser refuses the feature; it never fetches a DOCTYPE's outside parts anyway.
        }
    }

    private val DECLARATIONS = Regex("""<!(DOCTYPE|ENTITY)""", RegexOption.IGNORE_CASE)

    /** Refuses a DOCTYPE or an entity before parsing (no speaker sends one), here and in the DIDL parsed a second time. */
    private fun parse(xml: String): Element {
        if (DECLARATIONS.containsMatchIn(xml)) throw SonosXmlException()
        return try {
            factory().newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        } catch (e: SAXException) {
            throw SonosXmlException()
        } catch (e: IOException) {
            throw SonosXmlException()
        }
    }

    private fun Element.all(localName: String): List<Element> =
        getElementsByTagNameNS("*", localName).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    private fun Element.children(localName: String): List<Element> =
        (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>().filter { it.localName == localName }

    /** The first [localName]'s text, trimmed; null when missing, empty or Sonos's NOT_IMPLEMENTED. */
    private fun Element.text(localName: String): String? =
        all(localName).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() && it != NOTHING }
}
```

- [ ] **Step 6: Run them to see them pass**

Run: `./gradlew :provider:music-sonos:testDebugUnitTest --tests '*SonosXmlTest*'`
Expected: PASS. If `theCaptureShapeReplaces…` finds a name left, widen only the regex that should have caught it.

- [ ] **Step 7: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL. (`CopyScanTest` passes: the regexes sit in `Regex(…)` calls; the entity pairs are one-character literals.)
```bash
git add settings.gradle.kts provider/music-sonos
git commit -m "Read Sonos's answers and their DIDL-Lite, from documented examples"
```

---

### Task 9: `:provider:music-sonos` 2: SOAP, `SonosClient` and the debug capture (§3.2, §6, §8.2; rulings 12, 14, 18, 20, 22, 26, 28; Review Focus 4)

**Review:** sonnet.

**Files:**
- Create (under `provider/music-sonos/src/main/java/uk/co/siland/culvery/provider/music_sonos/`): `Http.kt`, `Soap.kt`, `SonosClient.kt`
- Create: `provider/music-sonos/src/debug/java/uk/co/siland/culvery/provider/music_sonos/SonosCapture.kt`, `provider/music-sonos/src/release/java/uk/co/siland/culvery/provider/music_sonos/SonosCapture.kt`
- Modify: `core/ui/src/test/resources/copy-scan-allow.txt` (two SOAP literals and `Master`)
- Test: `provider/music-sonos/src/test/java/uk/co/siland/culvery/provider/music_sonos/FakeSpeaker.kt`, `SonosClientTest.kt`, `SonosCaptureSourceTest.kt`; `provider/music-sonos/src/testDebug/java/uk/co/siland/culvery/provider/music_sonos/SonosCaptureTest.kt`

**Interfaces:**
- Consumes: `SonosXml`, `BrowseResult`, `TrackInfo`, `MediaInfo`, `ZoneGroupInfo`, `SonosXmlException`, `isSpeakerAuthority`, `isSpeakerAddress` (Task 8); `PlayState` (Task 4).
- Produces (all `internal`):
  - `Http.kt`: `class Answer(val code: Int, val bytes: ByteArray) { val text: String }`, `class BodyTooLarge : IOException`, `class ReadFailed : IOException`, `suspend fun Call.await(maxBytes: Long): Answer`
  - `Soap.kt`: `enum class SonosService(val path: String, val urn: String) { AV_TRANSPORT, RENDERING, GROUP_RENDERING, TOPOLOGY, DEVICE, CONTENT }`, `fun envelope(service, action: String, args: List<Pair<String, String>>): String`, `fun soapAction(service, action: String): String`, `fun escapeXml(text: String): String`
  - `SonosClient.kt`: `const val SONOS_TIMEOUT_MS = 3_000L`, `MAX_ANSWER_BYTES`, `MAX_ART_BYTES`, `LIBRARY = "A:TRACKS"`, `FAVOURITES = "FV:2"`, `FAVOURITES_PAGE = 100`, `SHUFFLE_NOREPEAT`, `QUEUE_PREFIX = "x-rincon-queue:"`, `LIBRARY_PREFIX = "x-rincon-playlist:"`, `fun queueUri(leader: String): String`; `class SonosFault(val code: Int?) : Exception`; `class NotASpeaker : IOException`; `fun sonosHttp(): OkHttpClient` (3 s, no retries, no redirects); `class SonosClient(http: OkHttpClient, accept: (String, Int) -> Boolean = ::isSpeakerAuthority, capture: (String, String) -> Unit = SonosCapture::log)` with `call(…)`, `bytes(url)`, `play`, `pause`, `next`, `transportInfo`, `positionInfo`, `mediaInfo`, `removeAllTracks`, `addToQueue(address, uri, metadata)`, `setPlayMode(address, mode)`, `setTransportUri(address, uri, metadata)`, `groupVolume`, `setGroupVolume(address, percent)`, `volume`, `setVolume(address, percent)`, `zoneGroups`, `householdId`, `browse(address, objectId, start, count)`; `suspend fun SonosClient.householdAt(address: String): String?`
  - `object SonosCapture { fun log(action: String, answer: String) }` (debug and release)
  - Tests: `FakeSpeaker` (`host`, `port`, `address`), `val onThisMachine: (String, Int) -> Boolean` (the tests' accept rule for speakers on localhost)

- [ ] **Step 1: Write the failing tests**

Create `FakeSpeaker.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy

/** The tests' accept rule: speakers on this machine, on whatever port MockWebServer took. */
internal val onThisMachine: (String, Int) -> Boolean = { host, _ -> host == "localhost" || host == "127.0.0.1" }

/** One Sonos speaker on localhost: answers by SOAP action, records each request; an action it wasn't given is fault 401. */
internal class FakeSpeaker : AutoCloseable {
    class Received(val path: String, val soapAction: String, val action: String, val body: String)

    private val server = MockWebServer()
    private val answers = ConcurrentHashMap<String, MockResponse>()
    val received = CopyOnWriteArrayList<Received>()

    /** True: every request goes unanswered, so the call times out. */
    @Volatile var silent = false

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val soapAction = request.getHeader("SOAPACTION").orEmpty()
                val action = soapAction.trim('"').substringAfter('#')
                received += Received(request.path.orEmpty(), soapAction, action, request.body.readUtf8())
                if (silent) return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                return answers[action] ?: MockResponse().setResponseCode(500).setBody(SonosFixtures.fault(401))
            }
        }
        server.start()
    }

    val host: String = server.hostName
    val port: Int = server.port
    val address: String = "$host:$port"

    fun answer(action: String, body: String) {
        answers[action] = MockResponse().setBody(body)
    }

    /** Answers [action] only after [millis], so a test can look while the command is on its way. */
    fun answerAfter(action: String, body: String, millis: Long) {
        answers[action] = MockResponse().setBody(body).setBodyDelay(millis, TimeUnit.MILLISECONDS)
    }

    /** Answers [action] (a GET for art has none: "") with a redirect to [location]. */
    fun redirect(action: String, location: String) {
        answers[action] = MockResponse().setResponseCode(302).setHeader("Location", location)
    }

    fun refuse(action: String, code: Int) {
        answers[action] = MockResponse().setResponseCode(500).setBody(SonosFixtures.fault(code))
    }

    /** Every command that changes something answers with an empty answer. */
    fun acceptCommands() {
        listOf(
            "Play", "Pause", "Next", "RemoveAllTracksFromQueue", "AddURIToQueue", "SetPlayMode", "SetAVTransportURI",
            "SnapshotGroupVolume", "SetGroupVolume", "SetVolume",
        ).forEach { answer(it, SonosFixtures.soap(it)) }
    }

    fun actions(): List<String> = received.map { it.action }

    fun bodyOf(action: String): String = received.last { it.action == action }.body

    override fun close() = server.shutdown()
}
```
Create `SonosClientTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.PlayState

private const val AV = "urn:schemas-upnp-org:service:AVTransport:1"

/** 6 §3.2, §7: each command's path, SOAPACTION and body; faults; the 3-second timeouts; the capture. */
@RunWith(AndroidJUnit4::class)
class SonosClientTest {
    private val speaker = FakeSpeaker()
    private val captured = mutableListOf<Pair<String, String>>()
    private val http = OkHttpClient.Builder().callTimeout(Duration.ofMillis(500)).retryOnConnectionFailure(false).build()
    private val client = SonosClient(http, onThisMachine) { action, answer -> captured += action to answer }

    @After
    fun tearDown() = speaker.close()

    private fun sent(action: String): FakeSpeaker.Received = speaker.received.last { it.action == action }

    /** Fails if a command goes to the wrong service, names itself wrongly, or loses an argument or its order. */
    @Test
    fun eachCommandSendsItsPathActionAndArguments() = runTest {
        speaker.acceptCommands()
        val a = speaker.address
        client.play(a)
        client.pause(a)
        client.next(a)
        client.removeAllTracks(a)
        client.addToQueue(a, "x-rincon-playlist:RINCON_1#A:TRACKS", "")
        client.setPlayMode(a, SHUFFLE_NOREPEAT)
        client.setTransportUri(a, queueUri("RINCON_1"), "")
        client.setVolume(a, 35)
        val expected = mapOf(
            "Play" to "<u:Play xmlns:u=\"$AV\"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play>",
            "Pause" to "<u:Pause xmlns:u=\"$AV\"><InstanceID>0</InstanceID></u:Pause>",
            "Next" to "<u:Next xmlns:u=\"$AV\"><InstanceID>0</InstanceID></u:Next>",
            "RemoveAllTracksFromQueue" to "<u:RemoveAllTracksFromQueue xmlns:u=\"$AV\"><InstanceID>0</InstanceID></u:RemoveAllTracksFromQueue>",
            "AddURIToQueue" to "<u:AddURIToQueue xmlns:u=\"$AV\"><InstanceID>0</InstanceID><EnqueuedURI>x-rincon-playlist:RINCON_1#A:TRACKS</EnqueuedURI>" +
                "<EnqueuedURIMetaData></EnqueuedURIMetaData><DesiredFirstTrackNumberEnqueued>0</DesiredFirstTrackNumberEnqueued><EnqueueAsNext>0</EnqueueAsNext></u:AddURIToQueue>",
            "SetPlayMode" to "<u:SetPlayMode xmlns:u=\"$AV\"><InstanceID>0</InstanceID><NewPlayMode>SHUFFLE_NOREPEAT</NewPlayMode></u:SetPlayMode>",
            "SetAVTransportURI" to "<u:SetAVTransportURI xmlns:u=\"$AV\"><InstanceID>0</InstanceID><CurrentURI>x-rincon-queue:RINCON_1#0</CurrentURI><CurrentURIMetaData></CurrentURIMetaData></u:SetAVTransportURI>",
            "SetVolume" to "<u:SetVolume xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\"><InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>35</DesiredVolume></u:SetVolume>",
        )
        expected.forEach { (action, body) ->
            assertThat(sent(action).body).contains(body)
            assertThat(sent(action).body).startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope")
        }
        assertThat(sent("Play").path).isEqualTo("/MediaRenderer/AVTransport/Control")
        assertThat(sent("Play").soapAction).isEqualTo("\"$AV#Play\"")
        assertThat(sent("SetVolume").path).isEqualTo("/MediaRenderer/RenderingControl/Control")
    }

    /** Sonos asks for a snapshot first, so the rooms keep their ratio. Pins ruling 12's guessed request shape until the walk-through. */
    @Test
    fun setGroupVolumeSnapshotsTheGroupFirst() = runTest {
        speaker.acceptCommands()
        client.setGroupVolume(speaker.address, 12)
        assertThat(speaker.actions()).containsExactly("SnapshotGroupVolume", "SetGroupVolume").inOrder()
        assertThat(sent("SetGroupVolume").path).isEqualTo("/MediaRenderer/GroupRenderingControl/Control")
        assertThat(sent("SetGroupVolume").body).contains("<DesiredVolume>12</DesiredVolume>")
    }

    @Test
    fun readsGoToTheirServicesAndAreParsed() = runTest {
        val a = speaker.address
        speaker.answer("GetTransportInfo", SonosFixtures.transport("PAUSED_PLAYBACK"))
        speaker.answer("GetGroupVolume", SonosFixtures.groupVolume(44))
        speaker.answer("GetVolume", SonosFixtures.volume(21))
        speaker.answer("GetHouseholdID", SonosFixtures.household())
        speaker.answer("GetZoneGroupState", SonosFixtures.topology(a, a, a))
        speaker.answer("Browse", SonosFixtures.library(total = 5))
        assertThat(client.transportInfo(a)).isEqualTo(PlayState.PAUSED)
        assertThat(client.groupVolume(a)).isEqualTo(44)
        assertThat(client.volume(a)).isEqualTo(21)
        assertThat(client.householdId(a)).isEqualTo(SonosFixtures.HOUSEHOLD)
        assertThat(client.zoneGroups(a)).hasSize(2)
        assertThat(client.browse(a, LIBRARY, 0, 1).total).isEqualTo(5)
        assertThat(sent("GetHouseholdID").path).isEqualTo("/DeviceProperties/Control")
        assertThat(sent("GetZoneGroupState").path).isEqualTo("/ZoneGroupTopology/Control")
        assertThat(sent("Browse").path).isEqualTo("/MediaServer/ContentDirectory/Control")
        assertThat(sent("Browse").body).contains(
            "<ObjectID>A:TRACKS</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter><StartingIndex>0</StartingIndex>" +
                "<RequestedCount>1</RequestedCount><SortCriteria></SortCriteria>",
        )
    }

    /** Metadata goes inside an element, so its markup and ampersands are escaped once. */
    @Test
    fun argumentsAreEscaped() = runTest {
        speaker.acceptCommands()
        client.setTransportUri(speaker.address, "x-sonosapi-hls:a?b=1&c=2", "<DIDL-Lite><dc:title>Tom & Jo’s</dc:title></DIDL-Lite>")
        assertThat(sent("SetAVTransportURI").body).contains(
            "<CurrentURI>x-sonosapi-hls:a?b=1&amp;c=2</CurrentURI><CurrentURIMetaData>&lt;DIDL-Lite&gt;&lt;dc:title&gt;Tom &amp; Jo’s&lt;/dc:title&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>",
        )
    }

    @Test
    fun aFaultIsASonosFaultWithItsCode() = runTest {
        speaker.refuse("Pause", 701)
        try {
            client.pause(speaker.address)
            fail("expected a fault")
        } catch (e: SonosFault) {
            assertThat(e.code).isEqualTo(701)
        }
    }

    /** Fails if a silent speaker hangs a check instead of failing as I/O, which starts the rescan (6 §6). */
    @Test
    fun aSilentSpeakerFailsAsIo() = runTest {
        speaker.silent = true
        try {
            client.transportInfo(speaker.address)
            fail("expected a timeout")
        } catch (e: IOException) {
            // The rescan's trigger.
        }
    }

    /** 6 §6: "Checks or commands time out (3 s)"; no redirect, which could lead off the home network. */
    @Test
    fun theSpeakersClientGivesUpAfterThreeSecondsAndFollowsNoRedirect() {
        val sonos = sonosHttp()
        assertThat(listOf(sonos.connectTimeoutMillis, sonos.readTimeoutMillis, sonos.callTimeoutMillis)).containsExactly(3_000, 3_000, 3_000)
        assertThat(sonos.retryOnConnectionFailure).isFalse()
        assertThat(sonos.followRedirects).isFalse()
        assertThat(sonos.followSslRedirects).isFalse()
    }

    /** Review Focus 4, ruling 20: an address off the home network, or on the wrong port, is refused before anything is sent. */
    @Test
    fun anAddressOffTheHomeNetworkIsNeverContacted() = runTest {
        val strict = SonosClient(http) { _, _ -> }
        listOf("8.8.8.8:1400", "192.168.1.20:80", speaker.address).forEach { address ->
            try {
                strict.householdId(address)
                fail("expected a refusal for $address")
            } catch (e: NotASpeaker) {
                // As ruled.
            }
        }
        assertThat(speaker.received).isEmpty()
        assertThat(strict.householdAt("8.8.8.8:1400")).isNull()
    }

    /** Art that redirects elsewhere is no art. */
    @Test
    fun aRedirectIsNotFollowed() = runTest {
        val elsewhere = FakeSpeaker()
        try {
            elsewhere.answer("", "picture")
            speaker.redirect("", "http://${elsewhere.address}/art.jpg")
            assertThat(SonosClient(sonosHttp(), onThisMachine) { _, _ -> }.bytes("http://${speaker.address}/getaa?s=1")).isNull()
            assertThat(elsewhere.received).isEmpty()
        } finally {
            elsewhere.close()
        }
    }

    @Test
    fun householdAtAnswersNullForAnythingButASpeaker() = runTest {
        assertThat(client.householdAt(speaker.address)).isNull()
        speaker.answer("GetHouseholdID", SonosFixtures.household())
        assertThat(client.householdAt(speaker.address)).isEqualTo(SonosFixtures.HOUSEHOLD)
        speaker.answer("GetHouseholdID", "<html>router</html>")
        assertThat(client.householdAt(speaker.address)).isNull()
        speaker.silent = true
        assertThat(client.householdAt(speaker.address)).isNull()
    }

    @Test
    fun artComesAsBytesOrNothing() = runTest {
        assertThat(client.bytes("http://${speaker.address}/getaa?s=1")).isNull()
        assertThat(client.bytes("ftp://example/art")).isNull()
        speaker.answer("", "picture")
        assertThat(client.bytes("http://${speaker.address}/getaa?s=1")).isEqualTo("picture".toByteArray())
    }

    /** 6 §8.2: the capture sees each answer with its action. */
    @Test
    fun theCaptureSeesEachAnswer() = runTest {
        speaker.answer("GetTransportInfo", SonosFixtures.transport("PLAYING"))
        client.transportInfo(speaker.address)
        assertThat(captured.map { it.first }).containsExactly("GetTransportInfo")
        assertThat(captured.single().second).contains("PLAYING")
    }
}
```
(`artComesAsBytesOrNothing`: a GET carries no SOAP action, so the fake files it under "": unanswered it is fault 401, a 500, so null; answered, its bytes. A scheme other than http(s) is never fetched.)
Create `SonosCaptureSourceTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** 6 §8.2: the capture is a debug build's only; the release one logs nothing. */
class SonosCaptureSourceTest {
    @Test
    fun theReleaseCaptureLogsNothing() {
        // Unit tests run in the module's directory.
        val release = File("src/release/java/uk/co/siland/culvery/provider/music_sonos/SonosCapture.kt").readText(Charsets.UTF_8)
        assertThat(release).doesNotContain("Log.")
        assertThat(release).contains("fun log(action: String, answer: String) = Unit")
    }
}
```
Create `provider/music-sonos/src/testDebug/java/uk/co/siland/culvery/provider/music_sonos/SonosCaptureTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** 6 §8.2: off unless the log property is set; on, the shape only. */
@RunWith(AndroidJUnit4::class)
class SonosCaptureTest {
    private val answer = SonosFixtures.topology("192.168.1.20:1400", "192.168.1.21:1400", "192.168.1.22:1400")

    @Test
    fun offByDefault() {
        ShadowLog.clear()
        ShadowLog.setLoggable("SonosCapture", Log.INFO)
        SonosCapture.log("GetZoneGroupState", answer)
        assertThat(ShadowLog.getLogsForTag("SonosCapture")).isEmpty()
    }

    @Test
    fun onItLogsTheShapeWithoutNames() {
        ShadowLog.clear()
        ShadowLog.setLoggable("SonosCapture", Log.DEBUG)
        SonosCapture.log("GetZoneGroupState", answer)
        val text = ShadowLog.getLogsForTag("SonosCapture").joinToString("") { it.msg }
        assertThat(text).contains("GetZoneGroupState")
        assertThat(text).contains("ZoneName=\"[room]\"")
        listOf("Kitchen", "192.168.1.2").forEach { assertThat(text).doesNotContain(it) }
    }
}
```
Fails today: none of the classes exist.
Fails if: a command's path, header, argument order or escaping is wrong; the group volume isn't snapshotted first; a fault loses its code; a silent speaker hangs; the timeouts aren't 3 s; a redirect is followed; an address off the home network is contacted; the release capture logs.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :provider:music-sonos:testDebugUnitTest`
Expected: FAIL to compile.

- [ ] **Step 3: HTTP and SOAP**

Create `Http.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody

internal class Answer(val code: Int, val bytes: ByteArray) {
    val text: String get() = bytes.toString(Charsets.UTF_8)
}

/** A body longer than the caller allows. An IOException, so the caller treats it as a failed call. */
internal class BodyTooLarge : IOException("The answer is longer than allowed")

/** Reading the answer failed in a way that isn't I/O. No cause: its text could quote the body. */
internal class ReadFailed : IOException("Reading the answer failed")

/**
 * Enqueues the call and suspends until its whole body is in, reading it on OkHttp's thread (as the weather's): cancelling
 * the coroutine cancels the call. A body over [maxBytes] is [BodyTooLarge], read no further than one byte past the limit.
 */
internal suspend fun Call.await(maxBytes: Long): Answer = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = try {
                    response.use { Answer(it.code, it.body?.let { body -> read(body, maxBytes) } ?: ByteArray(0)) }
                } catch (e: IOException) {
                    cont.resumeWithException(e)
                    return
                } catch (e: Exception) {
                    cont.resumeWithException(ReadFailed())
                    return
                }
                cont.resume(answer)
            }
        },
    )
}

private fun read(body: ResponseBody, maxBytes: Long): ByteArray {
    if (body.contentLength() > maxBytes) throw BodyTooLarge()
    val source = body.source()
    if (source.request(maxBytes + 1)) throw BodyTooLarge()
    return source.buffer.readByteArray()
}
```
Create `Soap.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

/** The speakers' UPnP services on port 1400 (6 §3.2). */
internal enum class SonosService(val path: String, val urn: String) {
    AV_TRANSPORT("/MediaRenderer/AVTransport/Control", "urn:schemas-upnp-org:service:AVTransport:1"),
    RENDERING("/MediaRenderer/RenderingControl/Control", "urn:schemas-upnp-org:service:RenderingControl:1"),
    GROUP_RENDERING("/MediaRenderer/GroupRenderingControl/Control", "urn:schemas-upnp-org:service:GroupRenderingControl:1"),
    TOPOLOGY("/ZoneGroupTopology/Control", "urn:schemas-upnp-org:service:ZoneGroupTopology:1"),
    DEVICE("/DeviceProperties/Control", "urn:schemas-upnp-org:service:DeviceProperties:1"),
    CONTENT("/MediaServer/ContentDirectory/Control", "urn:schemas-upnp-org:service:ContentDirectory:1"),
}

private const val ENVELOPE_START =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
private const val ENVELOPE_END = "</s:Body></s:Envelope>"

/** A SOAP request: [args] in the order the service declares them, each escaped. */
internal fun envelope(service: SonosService, action: String, args: List<Pair<String, String>>): String = buildString {
    append(ENVELOPE_START)
    append("<u:$action xmlns:u=\"${service.urn}\">")
    args.forEach { (name, value) -> append("<$name>${escapeXml(value)}</$name>") }
    append("</u:$action>")
    append(ENVELOPE_END)
}

internal fun soapAction(service: SonosService, action: String): String = "\"${service.urn}#$action\""

internal fun escapeXml(text: String): String = buildString(text.length) {
    text.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(ch)
        }
    }
}
```
Add to `core/ui/src/test/resources/copy-scan-allow.txt`:
```
provider/music-sonos/Soap.kt | <?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body> | SOAP's envelope, sent to the speakers, never shown
provider/music-sonos/Soap.kt | <u:$action xmlns:u=\"\"> | a SOAP action's element, sent to the speakers, never shown
provider/music-sonos/SonosClient.kt | Master | RenderingControl's channel name, sent to the speakers, never shown
```
(If `CopyScanTest` names a Soap.kt or SonosClient.kt protocol literal written differently, copy its text from the finding into the allowlist; any other finding is a stop.)

- [ ] **Step 4: The client and the capture**

Create `SonosClient.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uk.co.siland.culvery.capability.music.PlayState

/** A check or command gives up after this (6 §6). */
internal const val SONOS_TIMEOUT_MS = 3_000L

/** Favourites with their metadata run to a few hundred KB; past this isn't a Sonos answer. */
internal const val MAX_ANSWER_BYTES = 4L * 1024 * 1024
internal const val MAX_ART_BYTES = 2L * 1024 * 1024

internal const val LIBRARY = "A:TRACKS"
internal const val FAVOURITES = "FV:2"
internal const val FAVOURITES_PAGE = 100
internal const val SHUFFLE_NOREPEAT = "SHUFFLE_NOREPEAT"
internal const val QUEUE_PREFIX = "x-rincon-queue:"
internal const val LIBRARY_PREFIX = "x-rincon-playlist:"

/** A group's own queue, played from its leader (6 §4.4). */
internal fun queueUri(leader: String) = "$QUEUE_PREFIX$leader#0"

private const val INSTANCE = "InstanceID"
private const val CHANNEL = "Master"
private val SOAP_TYPE = "text/xml; charset=utf-8".toMediaType()

/** Sonos answered with a fault, or an HTTP error: [code] is UPnP's when given. */
internal class SonosFault(val code: Int?) : Exception("Sonos answered with a fault (${code ?: "no code"})")

/** An address that isn't a speaker on the home network: refused before anything is sent (6 §6). I/O, so it leads to a rescan. */
internal class NotASpeaker : IOException("Not a speaker on the home network")

internal fun sonosHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofMillis(SONOS_TIMEOUT_MS))
    .readTimeout(Duration.ofMillis(SONOS_TIMEOUT_MS))
    .callTimeout(Duration.ofMillis(SONOS_TIMEOUT_MS))
    .retryOnConnectionFailure(false)
    // A redirect could lead off the home network.
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

/**
 * SOAP to one speaker at `host:port` (6 §3.2), only where [accept] allows: a speaker on the home network, on port 1400.
 * Each call throws IOException when the speaker doesn't answer (or isn't allowed), [SonosFault] when it refuses, and
 * [SonosXmlException] when the answer isn't the shape asked for. Answers are parsed off the main thread.
 */
internal class SonosClient(
    private val http: OkHttpClient,
    private val accept: (String, Int) -> Boolean = ::isSpeakerAuthority,
    private val capture: (String, String) -> Unit = SonosCapture::log,
) {
    suspend fun call(address: String, service: SonosService, action: String, vararg args: Pair<String, String>): String {
        if (!isSpeakerAddress(address, accept)) throw NotASpeaker()
        val request = Request.Builder()
            .url("http://$address${service.path}")
            .header("SOAPACTION", soapAction(service, action))
            .post(envelope(service, action, args.toList()).toRequestBody(SOAP_TYPE))
            .build()
        val answer = http.newCall(request).await(MAX_ANSWER_BYTES)
        val text = answer.text
        capture(action, text)
        if (answer.code !in 200..299) throw SonosFault(read(text, SonosXml::faultCode))
        return text
    }

    /** The picture at [url], http or https only, no redirect; null for anything else or any failure. Which URLs may be asked is the caller's rule (6 §4.2). */
    suspend fun bytes(url: String): ByteArray? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return try {
            http.newCall(Request.Builder().url(url).build()).await(MAX_ART_BYTES).takeIf { it.code in 200..299 }?.bytes
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    suspend fun play(address: String) = av(address, "Play", "Speed" to "1")

    suspend fun pause(address: String) = av(address, "Pause")

    suspend fun next(address: String) = av(address, "Next")

    suspend fun transportInfo(address: String): PlayState = read(av(address, "GetTransportInfo"), SonosXml::transportState)

    suspend fun positionInfo(address: String): TrackInfo = read(av(address, "GetPositionInfo"), SonosXml::position)

    suspend fun mediaInfo(address: String): MediaInfo = read(av(address, "GetMediaInfo"), SonosXml::media)

    suspend fun removeAllTracks(address: String) = av(address, "RemoveAllTracksFromQueue")

    suspend fun addToQueue(address: String, uri: String, metadata: String) =
        av(address, "AddURIToQueue", "EnqueuedURI" to uri, "EnqueuedURIMetaData" to metadata, "DesiredFirstTrackNumberEnqueued" to "0", "EnqueueAsNext" to "0")

    suspend fun setPlayMode(address: String, mode: String) = av(address, "SetPlayMode", "NewPlayMode" to mode)

    suspend fun setTransportUri(address: String, uri: String, metadata: String) =
        av(address, "SetAVTransportURI", "CurrentURI" to uri, "CurrentURIMetaData" to metadata)

    suspend fun groupVolume(address: String): Int =
        read(call(address, SonosService.GROUP_RENDERING, "GetGroupVolume", INSTANCE to "0"), SonosXml::volume)

    /** The group slider's: a snapshot first, so the rooms keep their ratio. */
    suspend fun setGroupVolume(address: String, percent: Int) {
        call(address, SonosService.GROUP_RENDERING, "SnapshotGroupVolume", INSTANCE to "0")
        call(address, SonosService.GROUP_RENDERING, "SetGroupVolume", INSTANCE to "0", "DesiredVolume" to "$percent")
    }

    suspend fun volume(address: String): Int =
        read(call(address, SonosService.RENDERING, "GetVolume", INSTANCE to "0", "Channel" to CHANNEL), SonosXml::volume)

    suspend fun setVolume(address: String, percent: Int) {
        call(address, SonosService.RENDERING, "SetVolume", INSTANCE to "0", "Channel" to CHANNEL, "DesiredVolume" to "$percent")
    }

    suspend fun zoneGroups(address: String): List<ZoneGroupInfo> =
        read(call(address, SonosService.TOPOLOGY, "GetZoneGroupState")) { SonosXml.zoneGroups(it, accept) }

    suspend fun householdId(address: String): String = read(call(address, SonosService.DEVICE, "GetHouseholdID"), SonosXml::householdId)

    suspend fun browse(address: String, objectId: String, start: Int, count: Int): BrowseResult = read(
        call(
            address, SonosService.CONTENT, "Browse",
            "ObjectID" to objectId, "BrowseFlag" to "BrowseDirectChildren", "Filter" to "*",
            "StartingIndex" to "$start", "RequestedCount" to "$count", "SortCriteria" to "",
        ),
        SonosXml::browse,
    )

    private suspend fun av(address: String, action: String, vararg args: Pair<String, String>): String =
        call(address, SonosService.AV_TRANSPORT, action, INSTANCE to "0", *args)

    private suspend fun <T> read(text: String, parse: (String) -> T): T = withContext(Dispatchers.Default) { parse(text) }
}

/** The household of the speaker at [address], or null when nothing there answers as a Sonos speaker on the home network. */
internal suspend fun SonosClient.householdAt(address: String): String? = try {
    householdId(address)
} catch (e: IOException) {
    null
} catch (e: SonosFault) {
    null
} catch (e: SonosXmlException) {
    null
}
```
(The write commands return the answer's text; callers ignore it.)
Create `provider/music-sonos/src/debug/java/uk/co/siland/culvery/provider/music_sonos/SonosCapture.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import android.util.Log

/**
 * Debug builds only (6 §8.2): with `adb shell setprop log.tag.SonosCapture DEBUG`, each answer's shape goes to logcat,
 * names, titles, links, ids and addresses replaced; `… INFO` turns it off. In pieces, as logcat cuts long lines.
 */
internal object SonosCapture {
    private const val TAG = "SonosCapture"
    private const val PIECE = 3_000

    fun log(action: String, answer: String) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        SonosXml.redact(answer).chunked(PIECE).forEachIndexed { i, piece -> Log.d(TAG, "$action [$i] $piece") }
    }
}
```
Create `provider/music-sonos/src/release/java/uk/co/siland/culvery/provider/music_sonos/SonosCapture.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

/** Release builds capture nothing (6 §8.2). */
internal object SonosCapture {
    @Suppress("UNUSED_PARAMETER")
    fun log(action: String, answer: String) = Unit
}
```

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :provider:music-sonos:testDebugUnitTest` and `./gradlew :core:ui:testDebugUnitTest --tests '*CopyScanTest*'`
Expected: PASS.

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add provider/music-sonos core/ui/src/test/resources/copy-scan-allow.txt
git commit -m "Talk SOAP to a Sonos speaker, with a debug-only capture of each answer's shape"
```

---
### Task 10: `:provider:music-sonos` 3: `SonosSystem` (§4.1–4.6, §6; rulings 6, 7, 11–15, 20; Review Focus 2, 3, 4)

**Review:** opus.

**Files:**
- Create: `provider/music-sonos/src/main/java/uk/co/siland/culvery/provider/music_sonos/SonosSystem.kt`
- Test: `provider/music-sonos/src/test/java/uk/co/siland/culvery/provider/music_sonos/SonosSystemTest.kt`

**Interfaces:**
- Consumes: `SonosClient` and its constants, `householdAt`, `SonosFault`, `NotASpeaker`, `queueUri`, `FakeSpeaker`, `onThisMachine` (Task 9); `SonosXml` types (Task 8); `MusicSystem`, `SpeakerGroup`, `SpeakerRoom`, `NowPlaying`, `Favourite`, `FavouriteKind`, `MusicProblem`, the exceptions, `MusicLink` (Task 4); `WallClock` (`:core:plugin`).
- Produces (all `internal`): `const val RESCAN_GAP_MS = 30_000L`; `class GoneFromLayout : IOException`; `fun kindOf(item: DidlItem): FavouriteKind`; `class SonosSystem(client: SonosClient, scan: suspend () -> List<String>, online: () -> Boolean, link: MusicLink, onMoved: suspend (MusicLink) -> Unit, clock: WallClock) : MusicSystem` (with `roomVolume(room)` and the art rule).

- [ ] **Step 1: Write the failing tests**

Create `SonosSystemTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.FavouriteKind
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicRefusedException
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.capability.music.NoLibraryException
import uk.co.siland.culvery.capability.music.NowPlaying
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.DINING
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.HOUSEHOLD
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.KITCHEN
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.LOUNGE

/**
 * 6 §4, §6, §7: three speakers on localhost. Kitchen plays a library track on its own; the Lounge leads the Dining room,
 * paused on a station.
 */
@RunWith(AndroidJUnit4::class)
class SonosSystemTest {
    private val kitchen = FakeSpeaker()
    private val lounge = FakeSpeaker()
    private val dining = FakeSpeaker()
    private val client = SonosClient(OkHttpClient.Builder().callTimeout(Duration.ofMillis(500)).retryOnConnectionFailure(false).build(), onThisMachine) { _, _ -> }
    private var now = 1_000_000L
    private var online = true
    private var heard = listOf<String>()
    private var scans = 0
    private val moved = mutableListOf<MusicLink>()
    private val home get() = MusicLink(HOUSEHOLD, kitchen.address)

    /** Nothing listens on port 1: the saved speaker is gone. */
    private val gone = MusicLink(HOUSEHOLD, "127.0.0.1:1")

    @Before
    fun setUp() {
        val topology = SonosFixtures.topology(kitchen.address, lounge.address, dining.address)
        listOf(kitchen, lounge, dining).forEach {
            it.answer("GetHouseholdID", SonosFixtures.household())
            it.answer("GetZoneGroupState", topology)
            it.acceptCommands()
        }
        kitchen.answer("GetVolume", SonosFixtures.volume(30))
        lounge.answer("GetVolume", SonosFixtures.volume(40))
        dining.answer("GetVolume", SonosFixtures.volume(35))
        kitchen.answer("GetTransportInfo", SonosFixtures.transport("PLAYING"))
        kitchen.answer("GetPositionInfo", SonosFixtures.position())
        kitchen.answer("GetMediaInfo", SonosFixtures.media(SonosFixtures.queue(KITCHEN)))
        kitchen.answer("GetGroupVolume", SonosFixtures.groupVolume(30))
        lounge.answer("GetTransportInfo", SonosFixtures.transport("PAUSED_PLAYBACK"))
        lounge.answer("GetPositionInfo", SonosFixtures.position(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_TRACK))
        lounge.answer("GetMediaInfo", SonosFixtures.media(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_STATION))
        lounge.answer("GetGroupVolume", SonosFixtures.groupVolume(38))
        kitchen.answer("Browse", SonosFixtures.library(total = 1_234))
    }

    @After
    fun tearDown() {
        listOf(kitchen, lounge, dining).forEach { it.close() }
    }

    private fun system(link: MusicLink = home) =
        SonosSystem(client, { scans++; heard }, { online }, link, { moved += it }, WallClock { now })

    private suspend fun checked() = system().apply {
        checkLayout()
        checkPlayback()
    }

    private suspend fun unreachable(block: suspend () -> Unit): MusicProblem {
        try {
            block()
        } catch (e: MusicUnreachableException) {
            return e.problem
        }
        fail("expected the speakers to be unreachable")
        error("unreachable")
    }

    @Test
    fun theLayoutGivesEachGroupItsRoomsAndTheirVolumes() = runTest {
        val s = system()
        s.checkLayout()
        assertThat(s.groups.value.map { it.name }).containsExactly("Kitchen", "Lounge + Dining room").inOrder()
        assertThat(s.groups.value[1].rooms).containsExactly(SpeakerRoom(LOUNGE, "Lounge", 40), SpeakerRoom(DINING, "Dining room", 35)).inOrder()
        assertThat(s.problem.value).isNull()
    }

    /** 6 §3.1, §4.2: what each leader plays; Next only from a queue; the speaker's art made whole; only leaders asked. */
    @Test
    fun aCheckReadsEachLeadersPlayback() = runTest {
        val (k, l) = checked().groups.value
        assertThat(k.state).isEqualTo(PlayState.PLAYING)
        assertThat(k.nowPlaying).isEqualTo(NowPlaying.Track("Song", "Artist", "Album"))
        assertThat(k.canSkip).isTrue()
        assertThat(k.volume).isEqualTo(30)
        assertThat(k.artUrl).startsWith("http://${kitchen.address}/getaa?s=1&u=")
        assertThat(k.startedAtMillis).isEqualTo(now)
        assertThat(l.state).isEqualTo(PlayState.PAUSED)
        assertThat(l.nowPlaying).isEqualTo(NowPlaying.Radio("Culvery FM", "The Breakfast Show"))
        assertThat(l.canSkip).isFalse()
        assertThat(l.artUrl).isEqualTo("https://art.example/p0abc123.jpg")
        assertThat(dining.actions()).doesNotContain("GetTransportInfo")
    }

    /** 6 §2.2: when a group last started playing. Fails if a group already playing gets a new time at each check. */
    @Test
    fun aGroupThatStartsPlayingRecordsWhen() = runTest {
        val s = checked()
        lounge.answer("GetTransportInfo", SonosFixtures.transport("PLAYING"))
        val started = now + 60_000
        now = started
        s.checkPlayback()
        now += 2_000
        s.checkPlayback()
        assertThat(s.groups.value.map { it.startedAtMillis }).containsExactly(1_000_000L, started).inOrder()
    }

    /** 6 §4.3: to the leader, and that group checked at once. Fails if the Dining room gets the command. */
    @Test
    fun aCommandGoesToTheLeaderAndTheGroupIsCheckedAtOnce() = runTest {
        val s = checked()
        lounge.received.clear()
        s.play(s.groups.value[1])
        assertThat(lounge.actions().first()).isEqualTo("Play")
        assertThat(lounge.actions()).contains("GetTransportInfo")
        assertThat(dining.actions()).doesNotContain("Play")
    }

    /** 6 §4.3: the screen changes at once; the check after the command puts back what the speaker says. */
    @Test
    fun theScreenChangesAtOnceAndTheCheckCorrectsIt() = runTest {
        val s = checked()
        kitchen.answerAfter("Pause", SonosFixtures.soap("Pause"), millis = 300)
        val pausing = launch { s.pause(s.groups.value[0]) }
        runCurrent()
        assertThat(s.groups.value[0].state).isEqualTo(PlayState.PAUSED)
        pausing.join()
        // The fake speaker still says PLAYING.
        assertThat(s.groups.value[0].state).isEqualTo(PlayState.PLAYING)
    }

    @Test
    fun volumesGoWhereTheyBelong() = runTest {
        val s = checked()
        s.setRoomVolume(SpeakerRoom(DINING, "Dining room", 35), 20)
        assertThat(dining.bodyOf("SetVolume")).contains("<DesiredVolume>20</DesiredVolume>")
        assertThat(lounge.actions()).doesNotContain("SetVolume")
        assertThat(s.groups.value[1].rooms[1].volume).isEqualTo(20)
        s.setGroupVolume(s.groups.value[1], 150)
        assertThat(lounge.bodyOf("SetGroupVolume")).contains("<DesiredVolume>100</DesiredVolume>")
        assertThat(s.roomVolume(SpeakerRoom(LOUNGE, "Lounge", 0))).isEqualTo(40)
    }

    /**
     * 6 §4.4: the library checked, then its five steps in order, on the leader, with its own queue; the queue before the
     * play mode, which Sonos refuses (UPnP 712) while a stream is the source. The URI, its empty metadata and the browse
     * of one pin ruling 12's guessed request shapes until the walk-through.
     */
    @Test
    fun shuffleEverythingRunsItsStepsInOrder() = runTest {
        val s = checked()
        kitchen.received.clear()
        s.shuffleEverything(s.groups.value[0])
        assertThat(kitchen.actions().take(6)).containsExactly(
            "Browse", "RemoveAllTracksFromQueue", "AddURIToQueue", "SetAVTransportURI", "SetPlayMode", "Play",
        ).inOrder()
        assertThat(kitchen.bodyOf("Browse")).contains("<ObjectID>A:TRACKS</ObjectID>")
        assertThat(kitchen.bodyOf("Browse")).contains("<RequestedCount>1</RequestedCount>")
        assertThat(kitchen.bodyOf("AddURIToQueue")).contains("<EnqueuedURI>x-rincon-playlist:$KITCHEN#A:TRACKS</EnqueuedURI><EnqueuedURIMetaData></EnqueuedURIMetaData>")
        assertThat(kitchen.bodyOf("SetPlayMode")).contains("<NewPlayMode>SHUFFLE_NOREPEAT</NewPlayMode>")
        assertThat(kitchen.bodyOf("SetAVTransportURI")).contains("<CurrentURI>x-rincon-queue:$KITCHEN#0</CurrentURI>")
    }

    /** 6 §4.4, §6: an empty library stops before step 1, so the queue is untouched. */
    @Test
    fun anEmptyLibraryStopsBeforeTheQueue() = runTest {
        val s = checked()
        kitchen.answer("Browse", SonosFixtures.library(total = 0))
        try {
            s.shuffleEverything(s.groups.value[0])
            fail("expected no library")
        } catch (e: NoLibraryException) {
            // As §6 says.
        }
        assertThat(kitchen.actions()).doesNotContain("RemoveAllTracksFromQueue")
    }

    /** 6 §4.5: a station is set and played; a playlist replaces the queue without touching the play mode. */
    @Test
    fun favouritesPlayByTheirKind() = runTest {
        val s = checked()
        kitchen.answer("Browse", SonosFixtures.favourites)
        val favourites = s.favourites()
        assertThat(favourites.map { it.name to it.kind }).containsExactly(
            "Culvery FM" to FavouriteKind.STREAM, "Sunday & slow" to FavouriteKind.QUEUED, "One song" to FavouriteKind.STREAM,
        ).inOrder()
        assertThat(favourites[0].artUrl).isEqualTo("https://art.example/station.png")
        assertThat(favourites[1].artUrl).isEqualTo("http://${kitchen.address}/getaa?s=1&u=x-rincon-playlist")
        val k = s.groups.value[0]
        kitchen.received.clear()
        s.playFavourite(k, favourites[0])
        assertThat(kitchen.actions().take(2)).containsExactly("SetAVTransportURI", "Play").inOrder()
        assertThat(kitchen.bodyOf("SetAVTransportURI")).contains("<CurrentURI>x-sonosapi-hls:stations%7eBBC6%7eBBC6?sid=325&amp;flags=8224&amp;sn=1</CurrentURI>")
        assertThat(kitchen.bodyOf("SetAVTransportURI")).contains("&lt;dc:title&gt;Culvery FM&lt;/dc:title&gt;")
        kitchen.received.clear()
        s.playFavourite(k, favourites[1])
        assertThat(kitchen.actions().take(4)).containsExactly("RemoveAllTracksFromQueue", "AddURIToQueue", "SetAVTransportURI", "Play").inOrder()
        assertThat(kitchen.actions()).doesNotContain("SetPlayMode")
        assertThat(kitchen.bodyOf("AddURIToQueue")).contains("<EnqueuedURI>file:///jffs/settings/savedqueues.rsq#3</EnqueuedURI>")
    }

    /** 6 §6: a fault is "Sonos didn’t do that", and the group is checked again. */
    @Test
    fun aFaultIsRefusedAndTheGroupIsCheckedAgain() = runTest {
        val s = checked()
        kitchen.refuse("Pause", 701)
        kitchen.received.clear()
        try {
            s.pause(s.groups.value[0])
            fail("expected a refusal")
        } catch (e: MusicRefusedException) {
            assertThat(e.code).isEqualTo(701)
        }
        assertThat(kitchen.actions()).contains("GetTransportInfo")
        assertThat(s.problem.value).isNull()
    }

    /** A group regrouped in the Sonos app since the screen drew it is refused, not "can't reach Sonos". */
    @Test
    fun aGroupGoneFromTheLayoutIsRefused() = runTest {
        val s = checked()
        try {
            s.pause(s.groups.value[0].copy(id = "RINCON_000E58EEEEEE01400:9"))
            fail("expected a refusal")
        } catch (e: MusicRefusedException) {
            // The next layout check redraws the rows.
        }
        assertThat(s.problem.value).isNull()
        // Reading the layout again was enough: no scan.
        assertThat(scans).isEqualTo(0)
    }

    /** Review Focus 4, 6 §4.2: a member's own /getaa art, or https; never another http address or path. */
    @Test
    fun onlyALeadersOwnArtOrHttpsArtIsFetched() = runTest {
        val s = checked()
        kitchen.answer("", "picture")
        assertThat(s.art("http://${kitchen.address}/getaa?s=1&u=x")).isEqualTo("picture".toByteArray())
        kitchen.received.clear()
        assertThat(s.art("http://${kitchen.address}/elsewhere.jpg")).isNull()
        assertThat(s.art("http://8.8.8.8/art.jpg")).isNull()
        assertThat(s.art("ftp://art.example/x.jpg")).isNull()
        assertThat(kitchen.received).isEmpty()
    }

    /** Ruling 20: a saved address that isn't a home speaker is never asked; the scan finds the household instead. */
    @Test
    fun aSavedAddressOffTheHomeNetworkIsNeverContacted() = runTest {
        heard = listOf(lounge.address)
        val s = system(MusicLink(HOUSEHOLD, "8.8.8.8:1400"))
        s.checkLayout()
        assertThat(moved).containsExactly(MusicLink(HOUSEHOLD, lounge.address))
        assertThat(s.groups.value).hasSize(2)
    }

    /** Review Focus 3: the router moved the speakers; one scan finds the household, saves it, and nothing is reported. */
    @Test
    fun aMovedSpeakerIsFoundAndSavedWithoutAProblem() = runTest {
        heard = listOf(lounge.address)
        val s = system(gone)
        s.checkLayout()
        assertThat(s.groups.value).hasSize(2)
        assertThat(moved).containsExactly(MusicLink(HOUSEHOLD, lounge.address))
        assertThat(s.problem.value).isNull()
        assertThat(scans).isEqualTo(1)
    }

    /** 6 §4.1: report only after one rescan. */
    @Test
    fun noSpeakerAfterTheRescanIsUnreachable() = runTest {
        val s = system(gone)
        assertThat(unreachable { s.checkLayout() }).isEqualTo(MusicProblem.UNREACHABLE)
        assertThat(s.problem.value).isEqualTo(MusicProblem.UNREACHABLE)
        assertThat(scans).isEqualTo(1)
    }

    /** Fails at 29 999 ms if the gap is shorter, at 30 000 if longer. */
    @Test
    fun theRescanWaitsThirtySecondsBeforeScanningAgain() = runTest {
        val s = system(gone)
        unreachable { s.checkLayout() }
        now += RESCAN_GAP_MS - 1
        unreachable { s.checkLayout() }
        assertThat(scans).isEqualTo(1)
        now += 1
        unreachable { s.checkLayout() }
        assertThat(scans).isEqualTo(2)
    }

    /** 6 §6: Android says there's no network, so it's the tablet; no scan. */
    @Test
    fun offlineIsSaidWithoutAScan() = runTest {
        online = false
        val s = system(gone)
        assertThat(unreachable { s.checkLayout() }).isEqualTo(MusicProblem.OFFLINE)
        assertThat(s.problem.value).isEqualTo(MusicProblem.OFFLINE)
        assertThat(scans).isEqualTo(0)
    }

    @Test
    fun theProblemClearsWhenTheSpeakersAnswerAgain() = runTest {
        val s = system()
        kitchen.silent = true
        unreachable { s.checkLayout() }
        kitchen.silent = false
        s.checkLayout()
        assertThat(s.problem.value).isNull()
    }

    /** 6 §6: a reset system has a new household id; its rooms aren't shown as the household's. */
    @Test
    fun aDifferentHouseholdIsSaidAndItsRoomsAreNotShown() = runTest {
        val s = checked()
        kitchen.answer("GetHouseholdID", SonosFixtures.household("Sonos_someoneElse"))
        s.checkLayout()
        assertThat(s.problem.value).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(s.groups.value).isEmpty()
        kitchen.answer("GetHouseholdID", SonosFixtures.household())
        s.checkLayout()
        assertThat(s.problem.value).isNull()
        assertThat(s.groups.value).hasSize(2)
    }

    @Test
    fun aScanThatHearsOnlyAnotherHouseholdSaysSo() = runTest {
        lounge.answer("GetHouseholdID", SonosFixtures.household("Sonos_someoneElse"))
        heard = listOf(lounge.address)
        assertThat(unreachable { system(gone).checkLayout() }).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(moved).isEmpty()
    }

    /** Review Focus 2: the bell asks each leader what plays now, not the last check. */
    @Test
    fun playingNowAsksEachLeaderAfresh() = runTest {
        val s = system()
        s.checkLayout()
        assertThat(s.groups.value.map { it.state }).containsExactly(PlayState.STOPPED, PlayState.STOPPED)
        val playing = s.playingNow()
        assertThat(playing.map { it.id }).containsExactly("$KITCHEN:12")
        assertThat(kitchen.actions()).contains("GetTransportInfo")
    }
}
```
Fails today: `SonosSystem`, `RESCAN_GAP_MS` don't exist.
Fails if: a command goes to a member, or isn't followed by a check; art is fetched from anywhere but a member's /getaa or https; a saved address off the home network is asked; the optimistic change is missing or sticks; Shuffle everything sets the play mode before the queue, or touches the queue with no library; a queued Favourite changes the play mode; a moved speaker is reported; a rescan runs offline or more than once per 30 s; another household's rooms show; the lowering trusts the last check.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :provider:music-sonos:testDebugUnitTest --tests '*SonosSystemTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The system**

Create `SonosSystem.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.music.Favourite
import uk.co.siland.culvery.capability.music.FavouriteKind
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicRefusedException
import uk.co.siland.culvery.capability.music.MusicSystem
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.capability.music.NoLibraryException
import uk.co.siland.culvery.capability.music.NowPlaying
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.core.plugin.WallClock

/** While the speakers stay unreachable, the automatic scan runs at most this often (6 §4.1). */
internal const val RESCAN_GAP_MS = 30_000L

/** A group, or its leader, isn't in the layout: regrouped in the Sonos app. I/O, so the layout is read again first. */
internal class GoneFromLayout : IOException("The group isn't in the layout")

/** A container, or a link to one, replaces the queue; anything else is set as the transport (6 §4.5). */
internal fun kindOf(item: DidlItem): FavouriteKind {
    val uri = item.uri.orEmpty()
    val container = item.metadataClass?.startsWith("object.container") == true ||
        uri.startsWith("x-rincon-cpcontainer:") || uri.startsWith(LIBRARY_PREFIX)
    return if (container) FavouriteKind.QUEUED else FavouriteKind.STREAM
}

private data class Playback(val state: PlayState, val nowPlaying: NowPlaying?, val artUrl: String?, val canSkip: Boolean, val volume: Int)

/**
 * One household's speakers through Sonos's local API (6 §3.2, §4): the layout from the saved speaker, each group's
 * playback from its leader, commands to the leader. When no speaker answers it reads the layout again and, failing that,
 * scans for the household (at most every 30 s) and saves where it found it; only then is there a [problem].
 */
internal class SonosSystem(
    private val client: SonosClient,
    private val scan: suspend () -> List<String>,
    private val online: () -> Boolean,
    link: MusicLink,
    private val onMoved: suspend (MusicLink) -> Unit,
    private val clock: WallClock,
) : MusicSystem {
    private val _groups = MutableStateFlow<List<SpeakerGroup>>(emptyList())
    override val groups: StateFlow<List<SpeakerGroup>> = _groups.asStateFlow()

    private val _problem = MutableStateFlow<MusicProblem?>(null)
    override val problem: StateFlow<MusicProblem?> = _problem.asStateFlow()

    @Volatile private var link = link
    @Volatile private var layout: List<ZoneGroupInfo> = emptyList()
    @Volatile private var lastScanMillis: Long? = null
    private val playback = ConcurrentHashMap<String, Playback>()
    private val roomVolumes = ConcurrentHashMap<String, Int>()
    private val started = ConcurrentHashMap<String, Long>()
    private val favouriteItems = ConcurrentHashMap<String, DidlItem>()
    private val recovering = Mutex()

    override suspend fun checkLayout() = reaching {
        if (client.householdId(link.address) != link.householdId) {
            otherHousehold()
        } else {
            if (_problem.value == MusicProblem.OTHER_HOUSEHOLD) _problem.value = null
            readLayout(link.address)
        }
    }

    override suspend fun checkPlayback() {
        if (layout.isEmpty()) checkLayout()
        reaching { coroutineScope { layout.map { g -> async { readGroup(g) } }.awaitAll() } }
        publish()
    }

    override suspend fun playingNow(): List<SpeakerGroup> {
        if (layout.isEmpty()) checkLayout()
        val states = coroutineScope {
            layout.map { g -> async { g.id to quietly { client.transportInfo(g.leaderAddress()) } } }.awaitAll()
        }
        states.forEach { (id, state) -> if (state != null) record(id, playback[id]?.copy(state = state) ?: Playback(state, null, null, false, 0)) }
        publish()
        val playing = states.filter { it.second?.isPlaying == true }.map { it.first }.toSet()
        return _groups.value.filter { it.id in playing }
    }

    override suspend fun roomVolume(room: SpeakerRoom): Int = refusing { reaching { client.volume(memberAddress(room.id)) } }

    override suspend fun play(group: SpeakerGroup) = command(group.id, { it.copy(state = PlayState.PLAYING) }) { client.play(it.leaderAddress()) }

    override suspend fun pause(group: SpeakerGroup) = command(group.id, { it.copy(state = PlayState.PAUSED) }) { client.pause(it.leaderAddress()) }

    override suspend fun next(group: SpeakerGroup) = command(group.id, { it }) { client.next(it.leaderAddress()) }

    override suspend fun setGroupVolume(group: SpeakerGroup, percent: Int) {
        val volume = percent.coerceIn(0, 100)
        command(group.id, { it.copy(volume = volume) }) { client.setGroupVolume(it.leaderAddress(), volume) }
    }

    override suspend fun setRoomVolume(room: SpeakerRoom, percent: Int) {
        val volume = percent.coerceIn(0, 100)
        val groupId = layout.firstOrNull { g -> g.members.any { it.uuid == room.id } }?.id ?: throw MusicRefusedException()
        roomVolumes[room.id] = volume
        publish()
        command(groupId, { it }) { g -> client.setVolume(g.members.firstOrNull { it.uuid == room.id }?.address ?: throw GoneFromLayout(), volume) }
    }

    /** 6 §4.4: the library checked first, then its five steps; the queue is the source before the play mode, which a stream refuses. */
    override suspend fun shuffleEverything(group: SpeakerGroup) = command(group.id, { it }) { g ->
        val leader = g.leader()
        if (client.browse(leader.address, LIBRARY, 0, 1).total == 0) throw NoLibraryException()
        client.removeAllTracks(leader.address)
        client.addToQueue(leader.address, "$LIBRARY_PREFIX${leader.uuid}#$LIBRARY", "")
        client.setTransportUri(leader.address, queueUri(leader.uuid), "")
        client.setPlayMode(leader.address, SHUFFLE_NOREPEAT)
        client.play(leader.address)
    }

    override suspend fun favourites(): List<Favourite> {
        val items = refusing { reaching { client.browse(link.address, FAVOURITES, 0, FAVOURITES_PAGE).items } }
        favouriteItems.clear()
        items.forEach { favouriteItems[it.id] = it }
        return items.map { Favourite(it.id, it.title, artUrl(it.artUri, link.address), kindOf(it)) }
    }

    /** 6 §4.5: a station is set and played; a playlist or album replaces the queue (§4.4 steps 1, 2, 3 and 5), leaving the play mode. */
    override suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite) {
        val item = favouriteItems[favourite.id] ?: favourites().let { favouriteItems[favourite.id] } ?: throw MusicRefusedException()
        val uri = item.uri ?: throw MusicRefusedException()
        val metadata = item.metadata.orEmpty()
        command(group.id, { it }) { g ->
            val leader = g.leader()
            if (favourite.kind == FavouriteKind.QUEUED) {
                client.removeAllTracks(leader.address)
                client.addToQueue(leader.address, uri, metadata)
                client.setTransportUri(leader.address, queueUri(leader.uuid), "")
            } else {
                client.setTransportUri(leader.address, uri, metadata)
            }
            client.play(leader.address)
        }
    }

    /** Only a member's own /getaa art on the home network, or a stream's https art (6 §4.2, §6). */
    override suspend fun art(url: String): ByteArray? = if (artAllowed(url)) client.bytes(url) else null

    private fun artAllowed(url: String): Boolean =
        url.startsWith("https://") || layout.any { g -> g.members.any { url.startsWith("http://${it.address}/getaa") } }

    /**
     * Shows [shown] at once (6 §4.3), sends the command to the group's leader, and reads that group again whatever happened,
     * so the screen shows what the speaker did.
     */
    private suspend fun command(groupId: String, shown: (Playback) -> Playback, send: suspend (ZoneGroupInfo) -> Unit) {
        playback[groupId]?.let {
            record(groupId, shown(it))
            publish()
        }
        try {
            refusing { reaching { send(infoOf(groupId)) } }
        } finally {
            withContext(NonCancellable) {
                quietly { readGroup(infoOf(groupId)) }
                publish()
            }
        }
    }

    /**
     * Runs [block]; when no speaker answers, finds the household again and runs it once more. Offline, or still nothing,
     * is reported only then (6 §4.1, §6). A group still missing after the layout is read again is refused.
     */
    private suspend fun <T> reaching(block: suspend () -> T): T {
        try {
            return block().also { reached() }
        } catch (e: IOException) {
            if (!online()) fail(MusicProblem.OFFLINE)
            if (!recover()) fail(if (_problem.value == MusicProblem.OTHER_HOUSEHOLD) MusicProblem.OTHER_HOUSEHOLD else MusicProblem.UNREACHABLE)
        }
        return try {
            block().also { reached() }
        } catch (e: GoneFromLayout) {
            throw MusicRefusedException()
        } catch (e: IOException) {
            fail(if (online()) MusicProblem.UNREACHABLE else MusicProblem.OFFLINE)
        }
    }

    /** The layout from the saved speaker again; failing that, a scan, keeping the first speaker of this household. */
    private suspend fun recover(): Boolean = recovering.withLock {
        if (quietly { readLayout(link.address) } != null) return@withLock true
        val now = clock.nowMillis()
        if (lastScanMillis?.let { now - it in 0 until RESCAN_GAP_MS } == true) return@withLock false
        lastScanMillis = now
        val address = findHousehold() ?: return@withLock false
        link = MusicLink(link.householdId, address)
        onMoved(link)
        quietly { readLayout(address) } != null
    }

    private suspend fun findHousehold(): String? {
        var other = false
        for (address in scan()) {
            val household = client.householdAt(address) ?: continue
            if (household == link.householdId) return address
            other = true
        }
        if (other) _problem.value = MusicProblem.OTHER_HOUSEHOLD
        return null
    }

    private suspend fun readLayout(address: String) {
        val groups = client.zoneGroups(address)
        val volumes = coroutineScope {
            groups.flatMap { it.members }.map { m -> async { m.uuid to quietly { client.volume(m.address) } } }.awaitAll()
        }
        layout = groups
        volumes.forEach { (id, volume) -> if (volume != null) roomVolumes[id] = volume }
        val ids = groups.map { it.id }.toSet()
        playback.keys.retainAll(ids)
        started.keys.retainAll(ids)
        publish()
    }

    /** 6 §3.1: what plays, from the queue (title and artist, Next) or a stream (the station and its programme). */
    private suspend fun readGroup(group: ZoneGroupInfo) = coroutineScope {
        val address = group.leaderAddress()
        val state = async { client.transportInfo(address) }
        val track = async { client.positionInfo(address) }
        val media = async { client.mediaInfo(address) }
        val volume = async { client.groupVolume(address) }
        val t = track.await()
        val m = media.await()
        val fromQueue = m.uri?.startsWith(QUEUE_PREFIX) == true
        val nowPlaying = if (fromQueue) {
            t.title?.let { NowPlaying.Track(it, t.artist, t.album) }
        } else {
            (m.title ?: t.title?.takeUnless { it == t.uri })?.let { NowPlaying.Radio(it, t.programme) }
        }
        record(group.id, Playback(state.await(), nowPlaying, artUrl(t.artUri ?: m.artUri, address), fromQueue, volume.await()))
    }

    private fun record(groupId: String, now: Playback) {
        val before = playback.put(groupId, now)
        if (now.state.isPlaying && before?.state?.isPlaying != true) started[groupId] = clock.nowMillis()
    }

    private fun publish() {
        _groups.value = layout.map { g ->
            val p = playback[g.id]
            SpeakerGroup(
                id = g.id,
                rooms = g.members.map { SpeakerRoom(it.uuid, it.name, roomVolumes[it.uuid] ?: 0) },
                coordinatorId = g.coordinator,
                state = p?.state ?: PlayState.STOPPED,
                nowPlaying = p?.nowPlaying,
                artUrl = p?.artUrl,
                canSkip = p?.canSkip ?: false,
                volume = p?.volume ?: 0,
                startedAtMillis = started[g.id],
            )
        }
    }

    private fun otherHousehold() {
        _problem.value = MusicProblem.OTHER_HOUSEHOLD
        layout = emptyList()
        playback.clear()
        publish()
    }

    private fun reached() {
        if (_problem.value == MusicProblem.UNREACHABLE || _problem.value == MusicProblem.OFFLINE) _problem.value = null
    }

    private fun fail(problem: MusicProblem): Nothing {
        _problem.value = problem
        throw MusicUnreachableException(problem)
    }

    private fun infoOf(groupId: String): ZoneGroupInfo = layout.firstOrNull { it.id == groupId } ?: throw GoneFromLayout()

    private fun ZoneGroupInfo.leader(): ZoneMember = members.firstOrNull { it.uuid == coordinator } ?: throw GoneFromLayout()

    private fun ZoneGroupInfo.leaderAddress(): String = leader().address

    private fun memberAddress(roomId: String): String =
        layout.firstNotNullOfOrNull { g -> g.members.firstOrNull { it.uuid == roomId } }?.address ?: throw GoneFromLayout()

    /** A relative art path is the speaker's; a stream's own https URL stays; anything else is no art (6 §4.2, §6). */
    private fun artUrl(uri: String?, address: String): String? = when {
        uri.isNullOrBlank() -> null
        uri.startsWith("/") -> "http://$address$uri"
        uri.startsWith("https://") -> uri
        else -> null
    }

    private suspend fun <T> refusing(block: suspend () -> T): T = try {
        block()
    } catch (e: SonosFault) {
        throw MusicRefusedException(e.code)
    }

    private suspend fun <T> quietly(read: suspend () -> T): T? = try {
        read()
    } catch (e: IOException) {
        null
    } catch (e: SonosFault) {
        null
    } catch (e: SonosXmlException) {
        null
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :provider:music-sonos:testDebugUnitTest --tests '*SonosSystemTest*'`
Expected: PASS. (On Windows a connection to port 1 is refused only after about 2 s of retries; the tests' 500 ms call timeout ends it first, as I/O either way.)

- [ ] **Step 5: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add provider/music-sonos/src
git commit -m "Keep a Sonos household's groups up to date, send its commands, and find it again when its address changes"
```

---

### Task 11: `:provider:music-sonos` 4: SSDP, `SonosProvider`, cleartext and the app (§3.2, §4.1, §6; rulings 16, 20, 22, 23; Review Focus 4)

**Review:** opus.

**Files:**
- Create (under `provider/music-sonos/src/main/java/uk/co/siland/culvery/provider/music_sonos/`): `SonosDiscovery.kt`, `Network.kt`, `SonosProvider.kt`, `di/SonosModule.kt`
- Create: `provider/music-sonos/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Modify: `app/src/main/AndroidManifest.xml` (`android:networkSecurityConfig`), `app/build.gradle.kts` (the provider), `core/ui/src/test/resources/copy-scan-allow.txt` (SSDP's search)
- Test: `provider/music-sonos/src/test/java/uk/co/siland/culvery/provider/music_sonos/SsdpTest.kt`, `SonosDiscoveryTest.kt`, `SonosProviderTest.kt`; `app/src/test/java/uk/co/siland/culvery/CleartextTest.kt`, `InternetHttpsTest.kt`

**Interfaces:**
- Consumes: `SonosClient`, `sonosHttp()`, `householdAt`, `FakeSpeaker`, `onThisMachine` (Task 9); `SonosSystem` (Task 10); `hostAndPort`, `isSpeakerAuthority`, `SONOS_PORT` (Task 8); `MusicProvider`, `MusicLink`, `MusicUnreachableException`, `MusicProblem` (Task 4); `WallClock` (`:core:plugin`); `Icons.SPEAKER` (Task 1).
- Produces:
  - `internal data class SsdpAnswer(val from: String, val text: String)`; `internal fun ssdpAddresses(answers: List<SsdpAnswer>, accept: (String, Int) -> Boolean = ::isSpeakerAuthority): List<String>`; `internal fun multicastLock(context: Context): () -> AutoCloseable`; `internal class SonosDiscovery(lock: () -> AutoCloseable, target: InetSocketAddress = …, listenMillis: Long = SCAN_MS, accept: (String, Int) -> Boolean = ::isSpeakerAuthority) { suspend fun scan(): List<String> }`; `internal const val SCAN_MS = 5_000L`
  - `internal class AndroidNetwork(context: Context) { fun online(): Boolean }`
  - `const val SONOS_PROVIDER_ID = "music.sonos"`; `class SonosProvider internal constructor(client, scan, online, clock, port: Int = SONOS_PORT) : MusicProvider`, `@Inject constructor(@ApplicationContext context: Context, clock: WallClock)`

- [ ] **Step 1: Write the failing tests**

Create `SsdpTest.kt` (plain JVM):
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §3.2, §6: the speakers' addresses from SSDP's answers, only for speakers on the home network that sent them. */
class SsdpTest {
    private val zonePlayer = "urn:schemas-upnp-org:device:ZonePlayer:1"

    private fun answer(from: String, st: String, location: String, case: (String) -> String = { it }) = SsdpAnswer(
        from,
        "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age = 1800\r\nEXT:\r\n${case("LOCATION")}: $location\r\n" +
            "SERVER: Linux UPnP/1.0 Sonos/80.1-55014 (ZPS9)\r\n${case("ST")}: $st\r\nUSN: uuid:RINCON_000E58AAAAAA01400::$st\r\n\r\n",
    )

    /** Fails if a router's answer counts, a speaker is listed twice, or the order heard is lost. */
    @Test
    fun eachZonePlayerOnceInTheOrderHeard() {
        assertThat(
            ssdpAddresses(
                listOf(
                    answer("192.168.1.21", zonePlayer, "http://192.168.1.21:1400/xml/device_description.xml"),
                    answer("192.168.1.1", "urn:schemas-upnp-org:device:InternetGatewayDevice:1", "http://192.168.1.1:5000/rootDesc.xml"),
                    answer("192.168.1.20", zonePlayer, "http://192.168.1.20:1400/xml/device_description.xml"),
                    answer("192.168.1.21", zonePlayer, "http://192.168.1.21:1400/xml/device_description.xml"),
                    SsdpAnswer("192.168.1.30", "not an answer"),
                ),
            ),
        ).containsExactly("192.168.1.21:1400", "192.168.1.20:1400").inOrder()
    }

    @Test
    fun headersAreReadWhateverTheirCase() {
        assertThat(ssdpAddresses(listOf(answer("192.168.1.20", zonePlayer, "http://192.168.1.20:1400/xml/device_description.xml") { it.lowercase() })))
            .containsExactly("192.168.1.20:1400")
    }

    /** Review Focus 4, ruling 20: an answer whose LOCATION is off the home network, or isn't its sender, is dropped. */
    @Test
    fun anAnswerPointingElsewhereIsDropped() {
        assertThat(
            ssdpAddresses(
                listOf(
                    answer("192.168.1.20", zonePlayer, "http://8.8.8.8:1400/xml/device_description.xml"),
                    answer("192.168.1.20", zonePlayer, "http://192.168.1.99:1400/xml/device_description.xml"),
                    answer("192.168.1.20", zonePlayer, "http://192.168.1.20:80/xml/device_description.xml"),
                ),
            ),
        ).isEmpty()
    }
}
```
Create `SonosDiscoveryTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith

/** 6 §3.2: the search goes out under the multicast lock, and a speaker's answer is heard; a loopback socket plays the speaker. */
@RunWith(AndroidJUnit4::class)
class SonosDiscoveryTest {
    @Test
    fun aScanSearchesUnderTheLockAndHearsTheSpeaker() = runTest {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { speaker ->
            var search = ""
            val answering = thread {
                val packet = DatagramPacket(ByteArray(2_048), 2_048)
                speaker.receive(packet)
                search = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                val answer = ("HTTP/1.1 200 OK\r\nLOCATION: http://127.0.0.1:1400/xml/device_description.xml\r\n" +
                    "ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n").toByteArray(Charsets.US_ASCII)
                speaker.send(DatagramPacket(answer, answer.size, packet.socketAddress))
            }
            val events = mutableListOf<String>()
            val discovery = SonosDiscovery(
                lock = {
                    events += "lock"
                    AutoCloseable { events += "unlock" }
                },
                target = InetSocketAddress(InetAddress.getLoopbackAddress(), speaker.localPort),
                listenMillis = 1_000,
                accept = onThisMachine,
            )
            val found = withContext(Dispatchers.IO) { discovery.scan() }
            answering.join()
            assertThat(found).containsExactly("127.0.0.1:1400")
            assertThat(search).startsWith("M-SEARCH * HTTP/1.1\r\n")
            assertThat(search).contains("MAN: \"ssdp:discover\"\r\n")
            assertThat(search).contains("ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n")
            assertThat(events).containsExactly("lock", "unlock").inOrder()
        }
    }
}
```
Create `SonosProviderTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class SonosProviderTest {
    private val speaker = FakeSpeaker()
    private val printer = FakeSpeaker()
    private val client = SonosClient(OkHttpClient.Builder().callTimeout(Duration.ofMillis(500)).build(), onThisMachine) { _, _ -> }
    private var online = true
    private var heard = listOf<String>()
    private var scans = 0

    private fun provider(port: Int = speaker.port) = SonosProvider(client, { scans++; heard }, { online }, WallClock { 0L }, port)

    @After
    fun tearDown() {
        speaker.close()
        printer.close()
    }

    @Test
    fun itIsSonosAndTheHouseholdCanConnectIt() {
        val d = provider().descriptor
        assertThat(listOf(d.id, d.displayName, d.userConnectable)).containsExactly("music.sonos", "Sonos", true).inOrder()
    }

    /** 6 §4.1: any speaker that answers gives the household; something else on the network that answered SSDP doesn't. */
    @Test
    fun findKeepsTheFirstThatSaysItsHousehold() = runTest {
        speaker.answer("GetHouseholdID", SonosFixtures.household())
        heard = listOf(printer.address, speaker.address)
        assertThat(provider().find()).isEqualTo(MusicLink(SonosFixtures.HOUSEHOLD, speaker.address))
    }

    @Test
    fun findWithNothingAnsweringIsNull() = runTest {
        assertThat(provider().find()).isNull()
        assertThat(scans).isEqualTo(1)
    }

    @Test
    fun offlineSaysSoWithoutScanning() = runTest {
        online = false
        try {
            provider().find()
            fail("expected offline")
        } catch (e: MusicUnreachableException) {
            assertThat(e.problem).isEqualTo(MusicProblem.OFFLINE)
        }
        assertThat(scans).isEqualTo(0)
    }

    /** 6 §4.1: "Enter a speaker’s address", checked by asking it; the port is Sonos's. */
    @Test
    fun checkAsksTheSpeakerAtThatAddress() = runTest {
        speaker.answer("GetHouseholdID", SonosFixtures.household())
        assertThat(provider().check(speaker.host)).isEqualTo(MusicLink(SonosFixtures.HOUSEHOLD, "${speaker.host}:${speaker.port}"))
        assertThat(provider(port = printer.port).check(printer.host)).isNull()
        // Not a speaker on this test's rule, so never asked.
        assertThat(provider().check("8.8.8.8")).isNull()
    }
}
```
Create `app/src/test/java/uk/co/siland/culvery/CleartextTest.kt`:
```kotlin
package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** 6 §3.2: plain HTTP for the speakers on the home network; never for the internet services Culvery uses. */
class CleartextTest {
    // Unit tests run in the module's directory, as NoBackupTest relies on.
    private fun root(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path)).documentElement

    private fun Element.children(): List<Element> = (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>()

    @Test
    fun theManifestNamesTheConfig() {
        val application = root("src/main/AndroidManifest.xml").children().single { it.tagName == "application" }
        assertThat(application.getAttributeNS("http://schemas.android.com/apk/res/android", "networkSecurityConfig"))
            .isEqualTo("@xml/network_security_config")
    }

    /** Fails if cleartext is allowed for Google or Open-Meteo, or not for an address the router chose. */
    @Test
    fun cleartextOnlyOffTheInternetServices() {
        val config = root("src/main/res/xml/network_security_config.xml").children()
        assertThat(config.single { it.tagName == "base-config" }.getAttribute("cleartextTrafficPermitted")).isEqualTo("true")
        val internet = config.single { it.tagName == "domain-config" }
        assertThat(internet.getAttribute("cleartextTrafficPermitted")).isEqualTo("false")
        val domains = internet.children().filter { it.tagName == "domain" }
        assertThat(domains.map { it.textContent.trim() }).containsExactly("googleapis.com", "google.com", "open-meteo.com")
        assertThat(domains.map { it.getAttribute("includeSubdomains") }.toSet()).containsExactly("true")
    }
}
```
Create `app/src/test/java/uk/co/siland/culvery/InternetHttpsTest.kt`:
```kotlin
package uk.co.siland.culvery

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * 6 §3.2: the network security config allows plain HTTP in general (for speakers at any home address), so every URL
 * written in the app's main sources must be HTTPS: a future internet client can't fall under the base config unseen.
 * A speaker's address is built at run time (`http://$address…`); XML names are never fetched.
 */
class InternetHttpsTest {
    // Unit tests run in the module's directory; the repo is one up.
    private val repo = File("..")

    private val plainUrl = Regex(""""http://([A-Za-z0-9.-]+)""")

    /** XML namespace and feature names: identifiers that look like URLs. */
    private val names = setOf("schemas.xmlsoap.org", "apache.org", "schemas.android.com", "purl.org")

    @Test
    fun everyUrlInMainSourcesIsHttps() {
        val main = "${File.separator}src${File.separator}main${File.separator}"
        val found = repo.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") }
            .filter { it.isFile && it.extension == "kt" && main in it.path }
            .flatMap { file -> plainUrl.findAll(file.readText(Charsets.UTF_8)).map { file.name to it.groupValues[1] } }
            .filterNot { (_, host) -> host in names }
            .toList()
        assertWithMessage("plain-HTTP URLs in main sources").that(found).isEmpty()
    }

    @Test
    fun theScanSeesTheInternetClients() {
        val sources = repo.walkTopDown().onEnter { it.name !in setOf("build", ".gradle", ".git") }.filter { it.isFile && it.extension == "kt" }
        assertWithMessage("the forecast's base URL").that(sources.any { "\"https://api.open-meteo.com" in it.readText(Charsets.UTF_8) }).isTrue()
    }
}
```
Fails today: none of these classes or files exist.
Fails if: a router's SSDP answer, or one whose LOCATION is off the home network or not its sender, is taken for a speaker; the search isn't ZonePlayer's; a URL in main sources is plain HTTP; the lock isn't released; offline scans anyway; a typed address isn't asked on port 1400; cleartext is open to the internet services or closed to the speakers.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :provider:music-sonos:testDebugUnitTest` and `./gradlew :app:testDebugUnitTest --tests '*CleartextTest*' --tests '*InternetHttpsTest*'`
Expected: FAIL to compile (provider); `CleartextTest` FAILS (no config); `InternetHttpsTest` PASSES (**pin**: today every URL is HTTPS; it keeps them so).

- [ ] **Step 3: Discovery and the network**

Create `SonosDiscovery.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal const val SSDP_HOST = "239.255.255.250"
internal const val SSDP_PORT = 1900

/** How long a scan listens (6 §3.2). */
internal const val SCAN_MS = 5_000L

private const val TAG = "SonosDiscovery"
private const val ZONE_PLAYER = "urn:schemas-upnp-org:device:ZonePlayer:1"
private const val M_SEARCH = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n"

// UDP can drop the search; two cost nothing.
private const val SEARCHES = 2
private const val RECEIVE_MS = 250
private const val ANSWER_BYTES = 2_048
private const val NANOS_PER_MILLI = 1_000_000L
private const val LOCK_TAG = "Culvery:SonosScan"

/** One SSDP answer: the address of the machine that sent it, and its text. */
internal data class SsdpAnswer(val from: String, val text: String)

/**
 * Each ZonePlayer's address from SSDP's answers, once each, in the order heard: its LOCATION's host and port, only when
 * that host is the answer's sender and [accept] allows it (6 §6), so an answer can't point Culvery elsewhere.
 */
internal fun ssdpAddresses(answers: List<SsdpAnswer>, accept: (String, Int) -> Boolean = ::isSpeakerAuthority): List<String> = answers
    .filter { answer -> answer.text.lineSequence().any { it.startsWith("ST:", ignoreCase = true) && ZONE_PLAYER in it } }
    .mapNotNull { answer ->
        answer.text.lineSequence().firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.let(::hostAndPort)
            ?.takeIf { (host, port) -> host == answer.from && accept(host, port) }
            ?.let { (host, port) -> "$host:$port" }
    }
    .distinct()

/** Held for one scan: without it some Wi-Fi drivers drop multicast traffic to save power (6 §3.2). */
internal fun multicastLock(context: Context): () -> AutoCloseable = {
    val lock = context.applicationContext.getSystemService(WifiManager::class.java)?.createMulticastLock(LOCK_TAG)?.apply {
        setReferenceCounted(false)
        acquire()
    }
    AutoCloseable { lock?.release() }
}

/**
 * Finds Sonos speakers on the home Wi-Fi (6 §4.1): SSDP's search for ZonePlayers, then about 5 seconds of answers. A
 * network that can't send finds nothing rather than failing; the caller says nothing answered.
 */
internal class SonosDiscovery(
    private val lock: () -> AutoCloseable,
    private val target: InetSocketAddress = InetSocketAddress(SSDP_HOST, SSDP_PORT),
    private val listenMillis: Long = SCAN_MS,
    private val accept: (String, Int) -> Boolean = ::isSpeakerAuthority,
) {
    suspend fun scan(): List<String> = withContext(Dispatchers.IO) {
        val answers = mutableListOf<SsdpAnswer>()
        try {
            lock().use {
                DatagramSocket().use { socket ->
                    socket.soTimeout = RECEIVE_MS
                    val search = M_SEARCH.toByteArray(Charsets.US_ASCII)
                    repeat(SEARCHES) { socket.send(DatagramPacket(search, search.size, target)) }
                    val until = System.nanoTime() + listenMillis * NANOS_PER_MILLI
                    val buffer = ByteArray(ANSWER_BYTES)
                    while (System.nanoTime() < until) {
                        ensureActive()
                        val packet = DatagramPacket(buffer, buffer.size)
                        val heard = try {
                            socket.receive(packet)
                            true
                        } catch (e: SocketTimeoutException) {
                            false
                        }
                        if (heard) answers += SsdpAnswer(packet.address.hostAddress.orEmpty(), String(packet.data, packet.offset, packet.length, Charsets.US_ASCII))
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "The speaker scan stopped (${e::class.simpleName})")
        }
        ssdpAddresses(answers, accept)
    }
}
```
Create `Network.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import android.content.Context
import android.net.ConnectivityManager

/** Whether Android has any network: none means the tablet is offline, not the speakers (6 §6). */
internal class AndroidNetwork(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun online(): Boolean = connectivity?.activeNetwork != null
}
```
Create `provider/music-sonos/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 6 §3.2: SOAP to the speakers on port 1400, and art. -->
    <uses-permission android:name="android.permission.INTERNET" />
    <!-- 6 §6: "The tablet is offline" is Android saying it has no network. -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <!-- 6 §3.2: the scan's multicast lock. -->
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
</manifest>
```
Add to `core/ui/src/test/resources/copy-scan-allow.txt`:
```
provider/music-sonos/SonosDiscovery.kt | M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n | SSDP's search, sent on the Wi-Fi, never shown
```

- [ ] **Step 4: The provider and its wiring**

Create `SonosProvider.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicProvider
import uk.co.siland.culvery.capability.music.MusicSystem
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.Icons

const val SONOS_PROVIDER_ID = "music.sonos"

/** Sonos through its local API on the home Wi-Fi (6 §2.7, §3.2): no account, nothing secret. */
@Singleton
class SonosProvider internal constructor(
    private val client: SonosClient,
    private val scan: suspend () -> List<String>,
    private val online: () -> Boolean,
    private val clock: WallClock,
    private val port: Int = SONOS_PORT,
) : MusicProvider {
    @Inject
    constructor(@ApplicationContext context: Context, clock: WallClock) :
        this(SonosClient(sonosHttp()), SonosDiscovery(multicastLock(context))::scan, AndroidNetwork(context)::online, clock)

    override val descriptor = ProviderDescriptor(SONOS_PROVIDER_ID, "Sonos", Icons.SPEAKER, setOf(Feature.READ, Feature.WRITE))

    override suspend fun find(): MusicLink? {
        if (!online()) throw MusicUnreachableException(MusicProblem.OFFLINE)
        for (address in scan()) client.householdAt(address)?.let { return MusicLink(it, address) }
        return null
    }

    override suspend fun check(address: String): MusicLink? {
        if (!online()) throw MusicUnreachableException(MusicProblem.OFFLINE)
        val speaker = "$address:$port"
        return client.householdAt(speaker)?.let { MusicLink(it, speaker) }
    }

    override fun open(link: MusicLink, onMoved: suspend (MusicLink) -> Unit): MusicSystem = SonosSystem(client, scan, online, link, onMoved, clock)
}
```
Create `di/SonosModule.kt`:
```kotlin
package uk.co.siland.culvery.provider.music_sonos.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.music.MusicProvider
import uk.co.siland.culvery.provider.music_sonos.SonosProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class SonosModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: SonosProvider): MusicProvider
}
```
Create `app/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 6 §3.2: the speakers answer plain HTTP on port 1400 at whatever address the router gives them, which this file can't
     name; the internet services Culvery uses stay HTTPS-only. -->
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">googleapis.com</domain>
        <domain includeSubdomains="true">google.com</domain>
        <domain includeSubdomains="true">open-meteo.com</domain>
    </domain-config>
</network-security-config>
```
In `app/src/main/AndroidManifest.xml`, on `<application>` after `android:dataExtractionRules`:
```xml
        android:networkSecurityConfig="@xml/network_security_config"
```
In `app/build.gradle.kts`, after the music capability line:
```kotlin
    implementation(project(":provider:music-sonos"))
```

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :provider:music-sonos:testDebugUnitTest`, `./gradlew :app:testDebugUnitTest --tests '*CleartextTest*' --tests '*InternetHttpsTest*'` and `./gradlew :core:ui:testDebugUnitTest --tests '*CopyScanTest*'`
Expected: PASS.

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` and `./gradlew -p build-logic :convention:test` — Expected: BUILD SUCCESSFUL. Also `./gradlew :app:assembleDebug` (Hilt wires `SonosProvider`): BUILD SUCCESSFUL.
```bash
git add provider/music-sonos app/src/main/AndroidManifest.xml app/src/main/res/xml/network_security_config.xml app/build.gradle.kts app/src/test/java/uk/co/siland/culvery/CleartextTest.kt app/src/test/java/uk/co/siland/culvery/InternetHttpsTest.kt core/ui/src/test/resources/copy-scan-allow.txt
git commit -m "Find Sonos speakers by SSDP and connect the Sonos provider, with plain HTTP allowed only off the internet services"
```

---
### Task 12: Art, the shared controls and the Home card (§4.2, §5.1, §5.5; rulings 8, 9, 15, 24)

**Review:** sonnet.

**Files:**
- Create (under `capability/music/src/main/java/uk/co/siland/culvery/capability/music/ui/`): `MusicDimens.kt`, `Art.kt`, `MusicParts.kt`, `MusicCard.kt`
- Modify: `capability/music/src/main/java/uk/co/siland/culvery/capability/music/MusicWords.kt` (the card's and TalkBack's words), `MusicCapability.kt` (the card)
- Test (under `capability/music/src/test/java/uk/co/siland/culvery/capability/music/`): `MusicWordsTest.kt`, `ui/ArtCacheTest.kt`, `ui/MusicCardTest.kt`, `ui/MusicCardScreenshotTest.kt`, `MusicCapabilityTest.kt`
- Baselines (new, 7): `music_card_playing_dark`, `music_card_playing_light`, `music_card_radio_dark`, `music_card_paused_dark`, `music_card_nothing_dark`, `music_card_problem_dark`, `music_card_looking_dark`.

**Interfaces:**
- Consumes: `HomePick`, `MusicView`, `MusicChecks.watch()`, `MusicChecks.watching` (Task 5); `MusicActions`, `problemLine` (Task 7); `Speakers`, `SpeakerGroup` (Task 4); `HhCard`, `HhButton`, `HhIcon`, `HhType`, `ShellTokens`, `quietFill`, `hhClickable`, `Icons` (`:core:ui`).
- Produces (package `uk.co.siland.culvery.capability.music.ui` unless said):
  - `fun interface ArtSource { suspend fun load(url: String): ImageBitmap? }`; `val LocalArtSource`; `class ArtCache internal constructor(bytes: suspend (String) -> ByteArray?) : ArtSource`, `@Inject constructor(speakers: Speakers)`; `internal fun decodeArt(data: ByteArray, px: Int): ImageBitmap?`; `internal const val ART_PX = 256`; `@Composable internal fun ArtImage(url: String?, size: Dp, modifier: Modifier = Modifier)`
  - `class GroupActions(onPlay, onPause, onNext, onGroupVolume: (SpeakerGroup, Int) -> Unit, onRoomVolume: (SpeakerGroup, SpeakerRoom, Int) -> Unit, onShuffle, onFavourites)`; `internal fun MusicActions.forGroups(onFavourites: (SpeakerGroup) -> Unit): GroupActions`
  - `@Composable internal fun PlayPauseButton(group, tag, actions)`, `NextButton(group, tag, actions)`, `ShuffleButton(group, tag, on: ButtonSurface, actions)`, `VolumeSlider(name: String, volume: Int, tag: String, modifier, onVolume: (Int) -> Unit)`, `NowPlayingBlock(group, nothingPlaying, artSize, nameStyle, tag, modifier)`, `MusicStatusLine(text)`
  - `@Composable fun MusicCard(pick: HomePick?, status: String?, line: String?, actions: GroupActions, modifier: Modifier = Modifier)`; `@Composable internal fun MusicCardHost(view, actions, checks)`
  - `MusicWords.kt` (package `…capability.music`): `NOTHING_PLAYING`, `LOOKING_FOR_SPEAKERS`, `SHUFFLE_EVERYTHING`, `fun nowPlayingLines(group, nothingPlaying): Pair<String, String?>`, `fun groupSpoken(group, nothingPlaying = false): String`, `playSpoken`, `pauseSpoken`, `nextSpoken`, `shuffleSpoken`, `volumeName`, `percentWords`

- [ ] **Step 1: Write the failing tests**

Create `MusicWordsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §5.5: what TalkBack says for a group and its controls. */
class MusicWordsTest {
    private val kitchen = group("k", "Kitchen")

    @Test
    fun aGroupIsSpokenWithWhatItPlays() {
        assertThat(groupSpoken(kitchen.copy(state = PlayState.PLAYING, nowPlaying = SONG))).isEqualTo("Kitchen, playing Song by Artist")
        assertThat(groupSpoken(kitchen.copy(state = PlayState.PAUSED, nowPlaying = SONG))).isEqualTo("Kitchen, paused")
        assertThat(groupSpoken(kitchen)).isEqualTo("Kitchen, nothing playing")
        assertThat(groupSpoken(kitchen.copy(state = PlayState.PAUSED, nowPlaying = SONG), nothingPlaying = true)).isEqualTo("Kitchen, nothing playing")
        assertThat(groupSpoken(group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION)))
            .isEqualTo("Lounge + Dining room, playing Culvery FM, The Breakfast Show")
        assertThat(groupSpoken(kitchen.copy(state = PlayState.PLAYING, nowPlaying = NowPlaying.Track("Song", null, null)))).isEqualTo("Kitchen, playing Song")
    }

    @Test
    fun theControlsWords() {
        assertThat(listOf(pauseSpoken("Kitchen"), playSpoken("Kitchen"), nextSpoken("Kitchen"), shuffleSpoken("Kitchen"), "${volumeName("Kitchen")}, ${percentWords(30)}"))
            .containsExactly("Pause Kitchen", "Play Kitchen", "Next track in Kitchen", "Shuffle everything in Kitchen", "Kitchen volume, 30 percent").inOrder()
    }

    @Test
    fun theLinesShown() {
        assertThat(nowPlayingLines(kitchen.copy(nowPlaying = SONG), nothingPlaying = false)).isEqualTo("Song" to "Artist")
        assertThat(nowPlayingLines(kitchen.copy(nowPlaying = STATION), nothingPlaying = false)).isEqualTo("Culvery FM" to "The Breakfast Show")
        assertThat(nowPlayingLines(kitchen.copy(nowPlaying = SONG), nothingPlaying = true)).isEqualTo("Nothing playing" to null)
    }
}
```
Create `ui/ArtCacheTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** 6 §4.2: art decoded at about the size shown, kept in memory, fetched once. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtCacheTest {
    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    /** 1 200 px sampled by 4 is 300: smaller, never below 256. */
    @Test
    fun aLargePictureIsDecodedSmallerButNotBelowTheSizeShown() {
        val image = decodeArt(png(1_200, 1_200), ART_PX)!!
        assertThat(image.width).isAtLeast(ART_PX)
        assertThat(image.width).isLessThan(ART_PX * 2)
    }

    @Test
    fun somethingThatIsNotAPictureIsNoArt() {
        assertThat(decodeArt("<html/>".toByteArray(), ART_PX)).isNull()
    }

    @Test
    fun artIsFetchedOnceAndKept() = runTest {
        var fetches = 0
        val cache = ArtCache {
            fetches++
            png(64, 64)
        }
        assertThat(cache.load("http://speaker/getaa?s=1")).isNotNull()
        assertThat(cache.load("http://speaker/getaa?s=1")).isNotNull()
        assertThat(fetches).isEqualTo(1)
    }

    @Test
    fun noBytesIsNoArtAndIsAskedAgain() = runTest {
        var fetches = 0
        val cache = ArtCache {
            fetches++
            null
        }
        assertThat(cache.load("u")).isNull()
        cache.load("u")
        assertThat(fetches).isEqualTo(2)
    }
}
```
Create `ui/MusicCardTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.HomePick
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SONG
import uk.co.siland.culvery.capability.music.STATION
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.capability.music.TestMusicProvider
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.capability.music.musicSettings
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

/** 6 §5.1, §5.5: the Home card's states, its controls and their words; it watches the speakers while it shows. */
@RunWith(AndroidJUnit4::class)
class MusicCardTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val calls = mutableListOf<String>()
    private val actions = GroupActions(
        onPlay = { calls += "play ${it.id}" },
        onPause = { calls += "pause ${it.id}" },
        onNext = { calls += "next ${it.id}" },
        onGroupVolume = { g, v -> calls += "volume ${g.id} $v" },
        onRoomVolume = { _, r, v -> calls += "room ${r.id} $v" },
        onShuffle = { calls += "shuffle ${it.id}" },
        onFavourites = { calls += "favourites ${it.id}" },
    )
    private val playing = group("k", "Kitchen", state = PlayState.PLAYING, nowPlaying = SONG, volume = 30)

    private fun show(pick: HomePick?, status: String? = null, line: String? = null) = compose.setContent {
        CulveryTheme(dark = true) { Box(Modifier.size(345.dp, 279.dp)) { MusicCard(pick, status, line, actions) } }
    }

    @Test
    fun playingSaysWhatAndOffersPauseNextAndTheVolume() {
        show(HomePick(playing, nothingPlaying = false))
        compose.onNodeWithContentDescription("Kitchen, playing Song by Artist").assertExists()
        compose.onNodeWithTag("music_card_pause").assertContentDescriptionEquals("Pause Kitchen").performClick()
        compose.onNodeWithTag("music_card_next").assertIsEnabled().assertContentDescriptionEquals("Next track in Kitchen")
        compose.onNodeWithTag("music_card_volume")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Kitchen volume")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30 percent"))
        compose.onNodeWithTag("music_card_volume").performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        assertThat(calls).containsExactly("pause k", "volume k 45").inOrder()
        compose.assertAccessible()
    }

    /** 6 §5.1: a tap mid-song can't replace the queue. Fails if Shuffle everything shows while playing. */
    @Test
    fun shuffleEverythingIsHiddenWhilePlaying() {
        show(HomePick(playing, nothingPlaying = false))
        compose.onNodeWithTag("music_card_shuffle").assertDoesNotExist()
    }

    /** 6 §4.3: Next greyed for a stream. */
    @Test
    fun nextIsGreyedForRadio() {
        show(HomePick(group("l", "Lounge", state = PlayState.PLAYING, nowPlaying = STATION), nothingPlaying = false))
        compose.onNodeWithTag("music_card_next").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Lounge, playing Culvery FM, The Breakfast Show").assertExists()
    }

    /** 6 §5.1: nothing playing anywhere: the usual room, "Nothing playing", Play and Shuffle everything. */
    @Test
    fun nothingPlayingOffersPlayAndShuffle() {
        show(HomePick(group("k", "Kitchen", state = PlayState.PAUSED, nowPlaying = SONG), nothingPlaying = true))
        compose.onNodeWithContentDescription("Kitchen, nothing playing").assertExists()
        compose.onNodeWithTag("music_card_play").assertContentDescriptionEquals("Play Kitchen").performClick()
        compose.onNodeWithTag("music_card_shuffle").assertContentDescriptionEquals("Shuffle everything in Kitchen").performClick()
        assertThat(calls).containsExactly("play k", "shuffle k").inOrder()
    }

    /** Home kept the group it showed playing (its track shows: `music_card_paused_dark`), and offers Play. */
    @Test
    fun aPausedGroupHomeKeptSaysPaused() {
        show(HomePick(playing.copy(state = PlayState.PAUSED), nothingPlaying = false))
        compose.onNodeWithContentDescription("Kitchen, paused").assertExists()
        compose.onNodeWithTag("music_card_play").assertExists()
    }

    /** 6 §5.1: unreachable, the §6 line and no controls. */
    @Test
    fun aProblemShowsItsLineAndNoControls() {
        show(HomePick(playing, false), status = "The tablet is offline. Check the Wi-Fi.")
        compose.onNodeWithText("The tablet is offline. Check the Wi-Fi.").assertExists()
        compose.onNodeWithTag("music_card_pause").assertDoesNotExist()
        compose.onNodeWithTag("music_card_volume").assertDoesNotExist()
    }

    @Test
    fun aRefusedCommandShowsItsLine() {
        show(HomePick(playing, false), line = "Sonos didn’t do that. Try again.")
        compose.onNodeWithTag("music_card_line").assertTextEquals("Sonos didn’t do that. Try again.")
    }

    @Test
    fun beforeTheFirstCheckItSaysItIsLooking() {
        show(null)
        compose.onNodeWithText("Looking for the speakers…").assertExists()
    }

    /** 6 §4.2: the speakers are checked while the card shows, and not once it has gone. Fails if the watch outlives the card. */
    @Test
    fun theCardWatchesOnlyWhileItShows() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val settings = musicSettings(folder.root, scope)
            val speakers = Speakers(setOf(TestMusicProvider()), settings, scope)
            val view = MusicView(speakers, settings, scope)
            val checks = MusicChecks(speakers.system, MutableStateFlow(true), scope)
            var shown by mutableStateOf(true)
            compose.setContent { CulveryTheme(dark = true) { if (shown) MusicCardHost(view, MusicActions({ null }, scope), checks) } }
            compose.waitForIdle()
            assertThat(checks.watching).isEqualTo(1)
            shown = false
            compose.waitForIdle()
            assertThat(checks.watching).isEqualTo(0)
        } finally {
            scope.cancel()
        }
    }
}
```
Create `ui/MusicCardScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.music.HomePick
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SONG
import uk.co.siland.culvery.capability.music.STATION
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The Home card in a REGULAR cell as the grid gives it on the 1280×800 canvas (the forecast's 345 × 279). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class MusicCardScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val grey = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }.asImageBitmap()
    private val kitchen = group("k", "Kitchen", state = PlayState.PLAYING, nowPlaying = SONG, volume = 30, artUrl = "art")

    private fun snap(name: String, dark: Boolean, pick: HomePick?, status: String? = null) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalArtSource provides ArtSource { grey }) {
                    Box(Modifier.background(Culvery.colors.bg).padding(16.dp)) {
                        Box(Modifier.size(345.dp, 279.dp)) { MusicCard(pick, status, null, GroupActions.None) }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun playingDark() = snap("music_card_playing_dark", true, HomePick(kitchen, false))

    @Test fun playingLight() = snap("music_card_playing_light", false, HomePick(kitchen, false))

    @Test
    fun radioDark() = snap("music_card_radio_dark", true, HomePick(group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION, artUrl = "art"), false))

    @Test fun pausedDark() = snap("music_card_paused_dark", true, HomePick(kitchen.copy(state = PlayState.PAUSED), false))

    @Test fun nothingDark() = snap("music_card_nothing_dark", true, HomePick(group("b", "Bedroom"), nothingPlaying = true))

    @Test
    fun problemDark() = snap("music_card_problem_dark", true, null, status = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.")

    @Test fun lookingDark() = snap("music_card_looking_dark", true, null)
}
```
In `MusicCapabilityTest.kt`, replace the construction with a helper and add the card's test:
```kotlin
    private fun capability(speakers: Speakers) = MusicCapability(
        speakers, MusicView(speakers, settings, scope), MusicActions({ speakers.system.value }, scope),
        MusicChecks(speakers.system, MutableStateFlow(true), scope), ArtCache { null },
    )

    /** 6 §5.1: REGULAR, after the forecast (40), only once connected. */
    @Test
    fun theHomeCardShowsOnceConnected() = runTest {
        val music = capability(Speakers(setOf(TestMusicProvider()), settings, scope))
        assertThat(music.cards().first()).isEmpty()
        settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
        val card = withTimeout(5_000) { music.cards().first { it.isNotEmpty() } }.single()
        assertThat(listOf(card.id, card.size, card.priority)).containsExactly("music.now", HomeCardSize.REGULAR, 30).inOrder()
    }
```
(and `MusicCapability(Speakers(…))` in the other two tests becomes `capability(Speakers(…))`; imports `kotlinx.coroutines.flow.MutableStateFlow`, `uk.co.siland.culvery.capability.music.ui.ArtCache`, `uk.co.siland.culvery.core.plugin.HomeCardSize`.)
The block of art, name and lines is one TalkBack sentence (`clearAndSetSemantics`), so the tests find it by its words, not its text; the screenshots show the text.
Fails today: none of the UI exists; `MusicCapability` takes one argument and has no card.
Fails if: the card's watch outlives it; the spoken words drift from §5.5; Shuffle everything shows mid-song on Home; Next is live for radio; a problem leaves the controls; art is fetched twice or decoded at full size; the card shows before a connection or above the forecast.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:music:testDebugUnitTest`
Expected: FAIL to compile.

- [ ] **Step 3: The words**

Append to `MusicWords.kt`:
```kotlin
internal const val NOTHING_PLAYING = "Nothing playing"
internal const val LOOKING_FOR_SPEAKERS = "Looking for the speakers…"
internal const val SHUFFLE_EVERYTHING = "Shuffle everything"

/** The two lines under a group's name: title and artist, or station and programme; "Nothing playing" (6 §5.1). */
fun nowPlayingLines(group: SpeakerGroup, nothingPlaying: Boolean): Pair<String, String?> = when (val n = group.nowPlaying.takeUnless { nothingPlaying }) {
    null -> NOTHING_PLAYING to null
    is NowPlaying.Track -> n.title to n.artist
    is NowPlaying.Radio -> n.station to n.programme
}

/** 6 §5.5: "Kitchen, playing Song by Artist", "Kitchen, paused", "Kitchen, nothing playing". */
fun groupSpoken(group: SpeakerGroup, nothingPlaying: Boolean = false): String {
    val n = group.nowPlaying.takeUnless { nothingPlaying }
    return when {
        n == null -> "${group.name}, nothing playing"
        group.isPlaying -> "${group.name}, playing ${playingWords(n)}"
        group.state == PlayState.PAUSED -> "${group.name}, paused"
        else -> "${group.name}, nothing playing"
    }
}

private fun playingWords(n: NowPlaying): String = when (n) {
    is NowPlaying.Track -> listOfNotNull(n.title, n.artist).joinToString(" by ")
    is NowPlaying.Radio -> listOfNotNull(n.station, n.programme).joinToString(", ")
}

fun playSpoken(name: String) = "Play $name"

fun pauseSpoken(name: String) = "Pause $name"

fun nextSpoken(name: String) = "Next track in $name"

fun shuffleSpoken(name: String) = "$SHUFFLE_EVERYTHING in $name"

fun volumeName(name: String) = "$name volume"

fun percentWords(percent: Int) = "$percent percent"
```

- [ ] **Step 4: Dimens, art and the shared controls**

Create `ui/MusicDimens.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.ui.unit.dp

/** Music's layout values (6 §5); provisional until the end-of-v1 design walk-through. */
internal object MusicDimens {
    // The Home card: padding 20 × 22, as the forecast's; the radius is ShellTokens.homeCardRadius.
    val cardPaddingV = 20.dp
    val cardPaddingH = 22.dp
    val cardArt = 72.dp
    val artGap = 14.dp
    val artRadius = 12.dp
    val controlsGap = 10.dp

    // Round controls are the 48 dp touch target, with a 28 dp icon.
    val button = 48.dp
    val buttonIcon = 28.dp
    val statusIcon = 20.dp
    val statusGap = 8.dp

    // The tab's rows: art, the group slider, and each room's slider under the Rooms chip.
    val rowArt = 64.dp
    val rowGap = 10.dp
    val rowSlider = 220.dp
    val roomSlider = 280.dp

    // The Favourites and address sheets, as the calendar's.
    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
    val favouriteArt = 48.dp

    // Settings › Music: as Settings' other pages; the doorbell's share in 5 % steps, 0–50.
    val settingsGap = 16.dp
    val buttonGap = 12.dp
    const val LOWER_STEPS = 9
}
```
Create `ui/Art.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons

/** Where the screens get art; none by default, so a preview or a test draws the placeholder. */
fun interface ArtSource {
    suspend fun load(url: String): ImageBitmap?
}

val LocalArtSource = staticCompositionLocalOf { ArtSource { null } }

/** About twice the card's 72 dp art at the tablet's density. */
internal const val ART_PX = 256
private const val ART_KEPT = 24

/** Art in memory only (6 §4.2): the last few pictures, decoded off the main thread at about the size shown. */
@Singleton
class ArtCache internal constructor(private val bytes: suspend (String) -> ByteArray?) : ArtSource {
    @Inject
    constructor(speakers: Speakers) : this({ url -> speakers.system.value?.art(url) })

    private val cache = LruCache<String, ImageBitmap>(ART_KEPT)

    override suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val data = bytes(url) ?: return null
        val image = withContext(Dispatchers.Default) { decodeArt(data, ART_PX) } ?: return null
        cache.put(url, image)
        return image
    }
}

/** Bounds first, then sampled down by halves while still at least [px]; null for anything that isn't a picture or won't fit in memory. */
internal fun decodeArt(data: ByteArray, px: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    val longer = maxOf(bounds.outWidth, bounds.outHeight)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (longer / (sample * 2) >= px) sample *= 2
    return try {
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    } catch (e: OutOfMemoryError) {
        null
    }
}

/** A group's or a Favourite's art, or a music note on `surf2` while there's none. Decorative: the words say what plays. */
@Composable
internal fun ArtImage(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val source = LocalArtSource.current
    val image by produceState<ImageBitmap?>(null, url, source) { value = url?.let { source.load(it) } }
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size).clip(RoundedCornerShape(MusicDimens.artRadius)).background(c.surf2)) {
        val shown = image
        if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            HhIcon(Icons.MUSIC_NOTE, size = size / 2, tint = c.mute)
        }
    }
}
```
Create `ui/MusicParts.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.SHUFFLE_EVERYTHING
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.capability.music.groupSpoken
import uk.co.siland.culvery.capability.music.nextSpoken
import uk.co.siland.culvery.capability.music.nowPlayingLines
import uk.co.siland.culvery.capability.music.pauseSpoken
import uk.co.siland.culvery.capability.music.percentWords
import uk.co.siland.culvery.capability.music.playSpoken
import uk.co.siland.culvery.capability.music.shuffleSpoken
import uk.co.siland.culvery.capability.music.volumeName
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.quietFill

private const val MAX_VOLUME = 100f

/** What a group's controls do; the Home card and the tab's rows share them. */
class GroupActions(
    val onPlay: (SpeakerGroup) -> Unit,
    val onPause: (SpeakerGroup) -> Unit,
    val onNext: (SpeakerGroup) -> Unit,
    val onGroupVolume: (SpeakerGroup, Int) -> Unit,
    val onRoomVolume: (SpeakerGroup, SpeakerRoom, Int) -> Unit,
    val onShuffle: (SpeakerGroup) -> Unit,
    val onFavourites: (SpeakerGroup) -> Unit,
) {
    companion object {
        /** For screenshots: nothing happens. */
        val None = GroupActions({}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}, {})
    }
}

internal fun MusicActions.forGroups(onFavourites: (SpeakerGroup) -> Unit) =
    GroupActions(this::play, this::pause, this::next, this::setGroupVolume, this::setRoomVolume, this::shuffleEverything, onFavourites)

/** A round 48 dp control named by [spoken]; disabled, its icon is `mute` and TalkBack hears it as unavailable. */
@Composable
internal fun RoundButton(icon: String, spoken: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(MusicDimens.button)
            .clip(CircleShape)
            .background(quietFill(ButtonSurface.Surf, c))
            .semantics { contentDescription = spoken }
            .hhClickable(onClick, enabled = enabled),
    ) {
        HhIcon(icon, size = MusicDimens.buttonIcon, tint = if (enabled) c.ink else c.mute)
    }
}

@Composable
internal fun PlayPauseButton(group: SpeakerGroup, tag: String, actions: GroupActions) {
    if (group.isPlaying) {
        RoundButton(Icons.PAUSE, pauseSpoken(group.name), "${tag}_pause") { actions.onPause(group) }
    } else {
        RoundButton(Icons.PLAY_ARROW, playSpoken(group.name), "${tag}_play") { actions.onPlay(group) }
    }
}

/** Greyed for a stream (6 §4.3). */
@Composable
internal fun NextButton(group: SpeakerGroup, tag: String, actions: GroupActions) =
    RoundButton(Icons.SKIP_NEXT, nextSpoken(group.name), "${tag}_next", enabled = group.canSkip) { actions.onNext(group) }

@Composable
internal fun ShuffleButton(group: SpeakerGroup, tag: String, on: ButtonSurface, actions: GroupActions) = HhButton(
    SHUFFLE_EVERYTHING, { actions.onShuffle(group) }, Modifier.testTag("${tag}_shuffle"),
    icon = Icons.SHUFFLE, on = on, spokenName = shuffleSpoken(group.name),
)

/** A volume 0–100, sent when let go rather than at each step of a drag; spoken "Kitchen volume, 30 percent" (6 §5.5). */
@Composable
internal fun VolumeSlider(name: String, volume: Int, tag: String, modifier: Modifier = Modifier, onVolume: (Int) -> Unit) {
    val c = Culvery.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: volume.toFloat()
    Slider(
        value = shown,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onVolume(it.roundToInt()) }
            dragging = null
        },
        valueRange = 0f..MAX_VOLUME,
        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surf3),
        modifier = modifier.testTag(tag).semantics {
            contentDescription = volumeName(name)
            stateDescription = percentWords(shown.roundToInt())
        },
    )
}

/** Art, the group's name, and what it plays, read by TalkBack as one sentence (6 §5.5). */
@Composable
internal fun NowPlayingBlock(group: SpeakerGroup, nothingPlaying: Boolean, artSize: Dp, nameStyle: TextStyle, tag: String, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val spoken = groupSpoken(group, nothingPlaying)
    val (first, second) = nowPlayingLines(group, nothingPlaying)
    val quiet = nothingPlaying || group.nowPlaying == null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MusicDimens.artGap),
        modifier = modifier.testTag(tag).clearAndSetSemantics { contentDescription = spoken },
    ) {
        ArtImage(group.artUrl.takeUnless { quiet }, artSize)
        Column(Modifier.weight(1f)) {
            Text(group.name, style = nameStyle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(first, style = HhType.body, color = if (quiet) c.mute else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (second != null) Text(second, style = HhType.secondary, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The one line saying what is wrong and how to fix it (6 §6), on the card, the tab and Settings › Music. */
@Composable
internal fun MusicStatusLine(text: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MusicDimens.statusGap),
        modifier = Modifier.testTag("music_status").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        HhIcon(Icons.INFO, size = MusicDimens.statusIcon, tint = c.danger)
        Text(text, style = HhType.body, color = c.danger)
    }
}
```

- [ ] **Step 5: The card and the capability**

Create `ui/MusicCard.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.music.HomePick
import uk.co.siland.culvery.capability.music.LOOKING_FOR_SPEAKERS
import uk.co.siland.culvery.capability.music.MUSIC
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.problemLine
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens

/**
 * The REGULAR Home card (6 §5.1): the room Home follows and what it plays, Play/Pause, Next and the group's volume;
 * Shuffle everything only while that group isn't playing, so a tap mid-song can't replace the queue. With a problem,
 * the §6 line and no controls; before the first check, that it is looking.
 */
@Composable
fun MusicCard(pick: HomePick?, status: String?, line: String?, actions: GroupActions, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    HhCard(
        modifier = modifier.fillMaxSize().testTag("music_card"),
        radius = ShellTokens.homeCardRadius,
        padding = PaddingValues(horizontal = MusicDimens.cardPaddingH, vertical = MusicDimens.cardPaddingV),
    ) {
        if (status != null || pick == null) {
            Text(MUSIC, style = HhType.cardTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(MusicDimens.controlsGap))
            if (status != null) MusicStatusLine(status) else Text(LOOKING_FOR_SPEAKERS, style = HhType.body, color = c.mute)
            return@HhCard
        }
        val group = pick.group
        NowPlayingBlock(group, pick.nothingPlaying, MusicDimens.cardArt, HhType.cardTitle, "music_card_now")
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
            PlayPauseButton(group, "music_card", actions)
            NextButton(group, "music_card", actions)
            if (!group.isPlaying) ShuffleButton(group, "music_card", ButtonSurface.Surf, actions)
        }
        VolumeSlider(group.name, group.volume, "music_card_volume", Modifier.fillMaxWidth()) { actions.onGroupVolume(group, it) }
        if (line != null) Text(line, style = HhType.secondary, color = c.danger, maxLines = 2, modifier = Modifier.testTag("music_card_line"))
    }
}

/** The card over the view; while it shows, the speakers are checked (6 §4.2). */
@Composable
internal fun MusicCardHost(view: MusicView, actions: MusicActions, checks: MusicChecks) {
    DisposableEffect(checks) {
        val stop = checks.watch()
        onDispose { stop() }
    }
    val pick by view.home.collectAsStateWithLifecycle(null)
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val lines by actions.lines.collectAsStateWithLifecycle()
    val groupActions = remember(actions) { actions.forGroups(onFavourites = {}) }
    MusicCard(pick, problem?.let(::problemLine), pick?.let { lines[it.group.id] }, groupActions)
}
```
In `MusicCapability.kt`: the constructor becomes
```kotlin
@Singleton
class MusicCapability @Inject constructor(
    private val speakers: Speakers,
    private val view: MusicView,
    private val actions: MusicActions,
    private val checks: MusicChecks,
    private val art: ArtCache,
) : Capability {
```
add above the class
```kotlin
private const val MUSIC_CARD_ID = "music.now"

/** After Today (100), Coming up (50) and the forecast (40): row 2, column 3. */
private const val MUSIC_CARD_PRIORITY = 30
```
and replace `cards()` with
```kotlin
    private val card = HomeCard(MUSIC_CARD_ID, HomeCardSize.REGULAR, MUSIC_CARD_PRIORITY) {
        CompositionLocalProvider(LocalArtSource provides art) { MusicCardHost(view, actions, checks) }
    }

    /** Home's card once the speakers are connected (6 §5.1). */
    override fun cards(): Flow<List<HomeCard>> = hasTab.map { if (it) listOf(card) else emptyList() }
```
(imports `androidx.compose.runtime.CompositionLocalProvider`, `uk.co.siland.culvery.core.plugin.HomeCardSize`, `uk.co.siland.culvery.capability.music.ui.ArtCache`, `…ui.LocalArtSource`, `…ui.MusicCardHost`; drop `flowOf`.)

- [ ] **Step 6: Run them to see them pass**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicWordsTest*' --tests '*ArtCacheTest*' --tests '*MusicCardTest*' --tests '*MusicCapabilityTest*'`
Expected: PASS. (`MusicCardScreenshotTest` fails `verify` until recorded.)

- [ ] **Step 7: Screenshots**

Run `./gradlew :capability:music:recordRoborazziDebug --tests '*MusicCardScreenshotTest*'`, write the 7 names to `$TMP/expected-baselines.txt`, run the baseline check, and open every one. Check: the room's name over its art (grey) and two lines; the round Pause and Next, Next greyed on the radio card; Shuffle everything only on the paused and nothing-playing cards; the slider across the card; the problem card with the red info line and nothing else; nothing clipped at 345 × 279.

- [ ] **Step 8: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/music/src
git commit -m "Show the playing room on Home with Play/Pause, Next, its volume and Shuffle everything"
```

---
### Task 13: The Music tab and the Favourites sheet (§2.4, §2.5, §4.2, §5.2, §5.3, §5.5)

**Review:** sonnet.

**Files:**
- Create (under `capability/music/src/main/java/uk/co/siland/culvery/capability/music/ui/`): `MusicTab.kt`, `FavouritesSheet.kt`
- Modify: `MusicWords.kt` (the tab's and the sheet's words), `MusicCapability.kt` (`TabContent`)
- Test (under `capability/music/src/test/java/uk/co/siland/culvery/capability/music/ui/`): `MusicTabTest.kt`, `FavouritesSheetTest.kt`, `MusicTabScreenshotTest.kt`
- Baselines (new, 6): `music_tab_dark`, `music_tab_light`, `music_tab_rooms_dark`, `music_tab_status_dark`, `favourites_sheet_dark`, `favourites_sheet_empty_dark`.

**Interfaces:**
- Consumes: `GroupActions`, `forGroups`, `PlayPauseButton`, `NextButton`, `ShuffleButton`, `VolumeSlider`, `NowPlayingBlock`, `MusicStatusLine`, `ArtImage`, `LocalArtSource`, `ArtSource`, `MusicDimens` (Task 12); `tabOrder`, `MusicView`, `MusicChecks.watch(layoutNow = true)` (Task 5); `MusicActions.favourites()`, `playFavourite()`, `FavouritesLoad` (Task 7); `LocalOverlayHost` (`:core:plugin`); `HhChoiceChip`, `HhListRow`, `HhSheet`, `HhCloseButton`, `HhMessageLine` (`:core:ui`).
- Produces:
  - `@Composable fun MusicTab(groups: List<SpeakerGroup>, status: String?, lines: Map<String, String>, roomsShown: Set<String>, onRooms: (String) -> Unit, actions: GroupActions, modifier: Modifier = Modifier)`; `@Composable internal fun MusicTabHost(view, actions, checks, art: ArtSource)`
  - `@Composable fun FavouritesSheet(groupName: String, load: FavouritesLoad?, line: String?, onPick: (Favourite) -> Unit, onClose: () -> Unit)`; `@Composable internal fun FavouritesSheetHost(group: SpeakerGroup, actions: MusicActions, onClose: () -> Unit)`
  - `MusicWords.kt`: `FAVOURITES_LABEL`, `ROOMS`, `NO_FAVOURITES`, `GETTING_FAVOURITES`, `favouritesSpoken(name)`, `roomsAction(shown)`, `playInAction(name)`

- [ ] **Step 1: Write the failing tests**

Create `ui/MusicTabTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SONG
import uk.co.siland.culvery.capability.music.STATION
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.capability.music.TestMusicProvider
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.capability.music.musicSettings
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

/** 6 §5.2, §5.5: one row per group, playing first; the Rooms chip; the status line; Favourites; the tab reads the layout. */
@RunWith(AndroidJUnit4::class)
class MusicTabTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = mutableListOf<String>()
    private val actions = GroupActions(
        onPlay = { calls += "play ${it.id}" },
        onPause = { calls += "pause ${it.id}" },
        onNext = { calls += "next ${it.id}" },
        onGroupVolume = { g, v -> calls += "volume ${g.id} $v" },
        onRoomVolume = { _, r, v -> calls += "room ${r.id} $v" },
        onShuffle = { calls += "shuffle ${it.id}" },
        onFavourites = { calls += "favourites ${it.id}" },
    )
    private val lounge = group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION, volume = 40)
    private val kitchen = group("k", "Kitchen", state = PlayState.PAUSED, nowPlaying = SONG)

    @After
    fun tearDown() = scope.cancel()

    private fun showTab(status: String? = null, lines: Map<String, String> = emptyMap()) = compose.setContent {
        var shown by remember { mutableStateOf(emptySet<String>()) }
        CulveryTheme(dark = true) {
            MusicTab(listOf(lounge, kitchen), status, lines, shown, { id -> shown = if (id in shown) shown - id else shown + id }, actions)
        }
    }

    @Test
    fun eachGroupIsARowWithItsControlsAndWords() {
        showTab()
        compose.onNodeWithContentDescription("Lounge + Dining room, playing Culvery FM, The Breakfast Show").assertExists()
        compose.onNodeWithContentDescription("Kitchen, paused").assertExists()
        compose.onNodeWithTag("music_row_l_next").assertIsNotEnabled()
        compose.onNodeWithTag("music_row_k_next").assertIsEnabled()
        compose.onNodeWithTag("music_row_l_pause").assertContentDescriptionEquals("Pause Lounge + Dining room")
        compose.onNodeWithTag("music_row_k_play").performClick()
        // 6 §5.2: Favourites and Shuffle everything always shown here, playing or not.
        compose.onNodeWithTag("music_row_l_shuffle").assertContentDescriptionEquals("Shuffle everything in Lounge + Dining room")
        compose.onNodeWithTag("music_row_k_favourites").assertContentDescriptionEquals("Favourites for Kitchen").performClick()
        assertThat(calls).containsExactly("play k", "favourites k").inOrder()
        compose.assertAccessible()
    }

    /** 6 §5.2: only a grouped row has Rooms; it shows each room's own slider. */
    @Test
    fun theRoomsChipShowsEachRoomsVolume() {
        showTab()
        compose.onNodeWithTag("music_row_k_rooms").assertDoesNotExist()
        compose.onNodeWithTag("music_row_l_room_Dining room-id_volume").assertDoesNotExist()
        compose.onNodeWithTag("music_row_l_rooms").performClick()
        compose.onNodeWithContentDescription("Dining room volume").performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        assertThat(calls).containsExactly("room Dining room-id 20")
        compose.onNodeWithTag("music_row_l_rooms").performClick()
        compose.onNodeWithTag("music_row_l_room_Dining room-id_volume").assertDoesNotExist()
    }

    @Test
    fun theStatusLineShowsOnlyWhenSomethingIsWrong() {
        showTab(status = "The tablet is offline. Check the Wi-Fi.")
        compose.onNodeWithTag("music_status").assertExists()
    }

    @Test
    fun noStatusLineWhileAllIsWell() {
        showTab()
        compose.onNodeWithTag("music_status").assertDoesNotExist()
    }

    @Test
    fun aRefusedCommandShowsOnItsRow() {
        showTab(lines = mapOf("k" to "Sonos didn’t do that. Try again."))
        compose.onNodeWithTag("music_row_k_line").assertTextEquals("Sonos didn’t do that. Try again.")
        compose.onNodeWithTag("music_row_l_line").assertDoesNotExist()
    }

    /**
     * 6 §4.2, §5.2: with Home already watching (its layout read, the next not due for 30 s), opening the tab reads the
     * layout at once; the tab orders playing first and opens Favourites on the sheet.
     */
    @Test
    fun theTabReadsTheLayoutAsItOpensOrdersAndOpensFavourites() {
        val settings = musicSettings(folder.root, scope)
        val provider = TestMusicProvider()
        val speakers = Speakers(setOf(provider), settings, scope)
        runBlocking {
            settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
            withTimeout(5_000) { speakers.system.first { it != null } }
        }
        val system = provider.systems.single()
        system.groups.value = listOf(kitchen, lounge)
        val checks = MusicChecks(speakers.system, MutableStateFlow(true), scope).also { it.start() }
        checks.watch()
        runBlocking { withTimeout(5_000) { while ("checkLayout" !in system.calls) delay(10) } }
        system.calls.clear()
        val overlay = RecordingOverlay()
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    MusicTabHost(MusicView(speakers, settings, scope), MusicActions({ system }, scope), checks, ArtSource { null })
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            "checkLayout" in system.calls
        }
        val loungeTop = compose.onNodeWithTag("music_row_l").getUnclippedBoundsInRoot().top
        val kitchenTop = compose.onNodeWithTag("music_row_k").getUnclippedBoundsInRoot().top
        assertThat(loungeTop).isLessThan(kitchenTop)
        compose.onNodeWithTag("music_row_k_favourites").performClick()
        assertThat(overlay.shown).isEqualTo(1)
    }
}

/** An overlay host that counts what it was asked to show. */
internal class RecordingOverlay : OverlayHost {
    var shown = 0
    var dismissed = 0

    override fun show(closesWhenIdle: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        shown++
    }

    override fun markEdited(edited: Boolean) = Unit

    override fun dismiss() {
        dismissed++
    }
}
```
Create `ui/FavouritesSheetTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.Favourite
import uk.co.siland.culvery.capability.music.FavouriteKind
import uk.co.siland.culvery.capability.music.FavouritesLoad
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicRefusedException
import uk.co.siland.culvery.capability.music.TestMusicSystem
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.core.ui.CulveryTheme

/** 6 §5.3, §6: the household's Favourites; a tap plays one in the row's group and closes; one that can't play says so. */
@RunWith(AndroidJUnit4::class)
class FavouritesSheetTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val kitchen = group("k", "Kitchen")
    private val station = Favourite("FV:2/1", "Culvery FM", null, FavouriteKind.STREAM)
    private val playlist = Favourite("FV:2/2", "Sunday morning", null, FavouriteKind.QUEUED)
    private val system = TestMusicSystem(listOf(kitchen)).apply { favouriteList = listOf(station, playlist) }
    private var closed = 0

    @After
    fun tearDown() = scope.cancel()

    private fun host() = compose.setContent {
        CulveryTheme(dark = true) { FavouritesSheetHost(kitchen, MusicActions({ system }, scope)) { closed++ } }
    }

    @Test
    fun aTapPlaysTheFavouriteInTheGroupAndCloses() {
        host()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            "favourites" in system.calls
        }
        compose.onNodeWithText("Culvery FM").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            closed == 1
        }
        assertThat(system.calls).contains("playFavourite k FV:2/1")
    }

    /** 6 §6: "Sonos couldn’t play this here." and the sheet stays open. */
    @Test
    fun oneThatCanNotPlayHereSaysSoAndStays() {
        system.failures["playFavourite k FV:2/2"] = MusicRefusedException(714)
        host()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            "favourites" in system.calls
        }
        compose.onNodeWithText("Sunday morning").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            "playFavourite k FV:2/2" in system.calls
        }
        compose.waitForIdle()
        compose.onNodeWithTag("favourites_line").assertTextEquals("Sonos couldn’t play this here.")
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun noneSaysHowToAddThem() {
        compose.setContent { CulveryTheme(dark = true) { FavouritesSheet("Kitchen", FavouritesLoad.Ready(emptyList()), null, {}, {}) } }
        compose.onNodeWithTag("favourites_empty").assertTextEquals("No Sonos Favourites yet. Add them in the Sonos app.")
    }

    @Test
    fun aFailedReadSaysWhy() {
        compose.setContent { CulveryTheme(dark = true) { FavouritesSheet("Kitchen", FavouritesLoad.Failed("The tablet is offline. Check the Wi-Fi."), null, {}, {}) } }
        compose.onNodeWithText("The tablet is offline. Check the Wi-Fi.").assertExists()
    }
}
```
Create `ui/MusicTabScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.music.Favourite
import uk.co.siland.culvery.capability.music.FavouriteKind
import uk.co.siland.culvery.capability.music.FavouritesLoad
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SONG
import uk.co.siland.culvery.capability.music.STATION
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The tab in the shell's content area on the 1280×800 canvas; the sheet against the right edge, as the shell places it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class MusicTabScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val grey = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }.asImageBitmap()
    private val groups = listOf(
        group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION, volume = 40, artUrl = "art"),
        group("k", "Kitchen", state = PlayState.PAUSED, nowPlaying = SONG, volume = 30, artUrl = "art"),
        group("b", "Bedroom"),
    )

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalArtSource provides ArtSource { grey }) {
                    Box(Modifier.size(1_116.dp, 700.dp).background(Culvery.colors.bg)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun tab(name: String, dark: Boolean, status: String? = null, roomsShown: Set<String> = emptySet()) =
        snap(name, dark) { MusicTab(groups, status, emptyMap(), roomsShown, {}, GroupActions.None) }

    @Test fun tabDark() = tab("music_tab_dark", true)

    @Test fun tabLight() = tab("music_tab_light", false)

    @Test fun tabRoomsDark() = tab("music_tab_rooms_dark", true, roomsShown = setOf("l"))

    @Test
    fun tabStatusDark() = tab("music_tab_status_dark", true, status = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.")

    private fun sheet(name: String, load: FavouritesLoad) = snap(name, true) {
        Box(Modifier.size(1_116.dp, 700.dp), contentAlignment = Alignment.CenterEnd) { FavouritesSheet("Kitchen", load, null, {}, {}) }
    }

    @Test
    fun sheetDark() = sheet(
        "favourites_sheet_dark",
        FavouritesLoad.Ready(
            listOf(
                Favourite("FV:2/1", "Culvery FM", "art", FavouriteKind.STREAM),
                Favourite("FV:2/2", "Sunday morning", "art", FavouriteKind.QUEUED),
                Favourite("FV:2/3", "Party mix", null, FavouriteKind.QUEUED),
            ),
        ),
    )

    @Test fun sheetEmptyDark() = sheet("favourites_sheet_empty_dark", FavouritesLoad.Ready(emptyList()))
}
```
Fails today: `MusicTab`, `MusicTabHost`, `FavouritesSheet` and `FavouritesSheetHost` don't exist.
Fails if: opening the tab waits for the 30-second layout check; a single room gets a Rooms chip; a room's slider sends the group's volume; Next is live on a station row; Shuffle everything hides on a playing row; the status line shows while all is well; the tab doesn't read the layout as it opens; a Favourite that can't play closes the sheet.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicTabTest*' --tests '*FavouritesSheetTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The words**

Append to `MusicWords.kt`:
```kotlin
internal const val FAVOURITES_LABEL = "Favourites"
internal const val ROOMS = "Rooms"
internal const val NO_FAVOURITES = "No Sonos Favourites yet. Add them in the Sonos app."
internal const val GETTING_FAVOURITES = "Getting your Favourites…"

fun favouritesSpoken(name: String) = "$FAVOURITES_LABEL for $name"

/** What a tap on the Rooms chip does, for TalkBack. */
fun roomsAction(shown: Boolean) = if (shown) "hide each room’s volume" else "show each room’s volume"

fun playInAction(name: String) = "play in $name"
```

- [ ] **Step 4: The tab**

Create `ui/MusicTab.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.music.FAVOURITES_LABEL
import uk.co.siland.culvery.capability.music.LOOKING_FOR_SPEAKERS
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.ROOMS
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.favouritesSpoken
import uk.co.siland.culvery.capability.music.problemLine
import uk.co.siland.culvery.capability.music.roomsAction
import uk.co.siland.culvery.capability.music.tabOrder
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens

/**
 * The Music tab (6 §5.2): a status line only when something is wrong, then one row per group as given (playing first,
 * [tabOrder]). Open to everyone; grouping stays in the Sonos app (6 §2.4).
 */
@Composable
fun MusicTab(
    groups: List<SpeakerGroup>,
    status: String?,
    lines: Map<String, String>,
    roomsShown: Set<String>,
    onRooms: (String) -> Unit,
    actions: GroupActions,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(MusicDimens.rowGap), modifier = modifier.fillMaxSize().testTag("music_tab")) {
        if (status != null) item { MusicStatusLine(status) }
        if (groups.isEmpty() && status == null) item { Text(LOOKING_FOR_SPEAKERS, style = HhType.body, color = c.mute) }
        items(groups, key = { it.id }) { group -> MusicRow(group, lines[group.id], group.id in roomsShown, { onRooms(group.id) }, actions) }
    }
}

/** A group: what it plays and its controls; Rooms on a group of several shows each room's slider (6 §5.2). */
@Composable
private fun MusicRow(group: SpeakerGroup, line: String?, roomsShown: Boolean, onRooms: () -> Unit, actions: GroupActions) {
    val c = Culvery.colors
    val tag = "music_row_${group.id}"
    Column(
        verticalArrangement = Arrangement.spacedBy(ListRowTokens.belowGap),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(RoundedCornerShape(ListRowTokens.radius))
            .background(c.surf)
            .padding(horizontal = ListRowTokens.paddingH, vertical = ListRowTokens.paddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
            NowPlayingBlock(group, nothingPlaying = false, MusicDimens.rowArt, HhType.rowTitle, "${tag}_now", Modifier.weight(1f))
            PlayPauseButton(group, tag, actions)
            NextButton(group, tag, actions)
            VolumeSlider(group.name, group.volume, "${tag}_volume", Modifier.width(MusicDimens.rowSlider)) { actions.onGroupVolume(group, it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
            if (group.rooms.size > 1) {
                HhChoiceChip(ROOMS, selected = roomsShown, tag = "${tag}_rooms", onClick = onRooms, role = Role.Button, clickLabel = roomsAction(roomsShown))
            }
            HhButton(
                FAVOURITES_LABEL, { actions.onFavourites(group) }, Modifier.testTag("${tag}_favourites"),
                icon = Icons.STAR, on = ButtonSurface.Surf, spokenName = favouritesSpoken(group.name),
            )
            ShuffleButton(group, tag, ButtonSurface.Surf, actions)
        }
        if (roomsShown) {
            group.rooms.forEach { room ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
                    Text(room.name, style = HhType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    VolumeSlider(room.name, room.volume, "${tag}_room_${room.id}_volume", Modifier.width(MusicDimens.roomSlider)) {
                        actions.onRoomVolume(group, room, it)
                    }
                }
            }
        }
        if (line != null) Text(line, style = HhType.secondary, color = c.danger, modifier = Modifier.testTag("${tag}_line"))
    }
}

/** The tab over the view; while it shows the speakers are checked, the layout at once (6 §4.2). */
@Composable
internal fun MusicTabHost(view: MusicView, actions: MusicActions, checks: MusicChecks, art: ArtSource) {
    DisposableEffect(checks) {
        val stop = checks.watch(layoutNow = true)
        onDispose { stop() }
    }
    val groups by view.groups.collectAsStateWithLifecycle(emptyList())
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val lines by actions.lines.collectAsStateWithLifecycle()
    var roomsShown by remember { mutableStateOf(emptySet<String>()) }
    val overlay = LocalOverlayHost.current
    val groupActions = remember(actions, overlay, art) {
        actions.forGroups { group ->
            overlay.show { CompositionLocalProvider(LocalArtSource provides art) { FavouritesSheetHost(group, actions, onClose = overlay::dismiss) } }
        }
    }
    MusicTab(
        tabOrder(groups), problem?.let(::problemLine), lines, roomsShown,
        onRooms = { id -> roomsShown = if (id in roomsShown) roomsShown - id else roomsShown + id },
        actions = groupActions,
    )
}
```

- [ ] **Step 5: The sheet**

Create `ui/FavouritesSheet.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.music.FAVOURITES_LABEL
import uk.co.siland.culvery.capability.music.Favourite
import uk.co.siland.culvery.capability.music.FavouritesLoad
import uk.co.siland.culvery.capability.music.GETTING_FAVOURITES
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.NO_FAVOURITES
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.playInAction
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ListRowTokens

/**
 * The Favourites sheet (6 §5.3): the household's Sonos Favourites by name, with art; a tap plays one in [groupName]'s
 * group. [load] is null while they are read; [line] says a Favourite couldn't play here (6 §6).
 */
@Composable
fun FavouritesSheet(groupName: String, load: FavouritesLoad?, line: String?, onPick: (Favourite) -> Unit, onClose: () -> Unit) {
    val c = Culvery.colors
    HhSheet(
        padding = PaddingValues(horizontal = MusicDimens.sheetPaddingH, vertical = MusicDimens.sheetPaddingV),
        modifier = Modifier.testTag("favourites_sheet").semantics { paneTitle = FAVOURITES_LABEL },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(FAVOURITES_LABEL, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(groupName, style = HhType.body, color = c.mute)
            }
            HhCloseButton(onClose)
        }
        HhMessageLine(line, HhType.body, c.danger, "favourites_line")
        when (load) {
            null -> Text(GETTING_FAVOURITES, style = HhType.body, color = c.mute)
            is FavouritesLoad.Failed -> MusicStatusLine(load.line)
            is FavouritesLoad.Ready -> if (load.favourites.isEmpty()) {
                Text(NO_FAVOURITES, style = HhType.body, color = c.mute, modifier = Modifier.testTag("favourites_empty"))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(ListRowTokens.belowGap), modifier = Modifier.weight(1f)) {
                    items(load.favourites, key = { it.id }) { favourite ->
                        HhListRow(
                            title = favourite.name,
                            modifier = Modifier.testTag("favourite_${favourite.id}"),
                            leading = { ArtImage(favourite.artUrl, MusicDimens.favouriteArt) },
                            onClick = { onPick(favourite) },
                            clickLabel = playInAction(groupName),
                        )
                    }
                }
            }
        }
    }
}

/** Reads the Favourites as it opens (6 §4.5); a tap plays and closes, or says why and stays. */
@Composable
internal fun FavouritesSheetHost(group: SpeakerGroup, actions: MusicActions, onClose: () -> Unit) {
    var load by remember { mutableStateOf<FavouritesLoad?>(null) }
    var line by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(group.id) { load = actions.favourites() }
    FavouritesSheet(
        group.name, load, line,
        onPick = { favourite ->
            scope.launch {
                line = null
                val failed = actions.playFavourite(group, favourite)
                if (failed == null) onClose() else line = failed
            }
        },
        onClose = onClose,
    )
}
```
In `MusicCapability.kt`, replace `TabContent`:
```kotlin
    @Composable
    override fun TabContent() = CompositionLocalProvider(LocalArtSource provides art) { MusicTabHost(view, actions, checks, art) }
```
(import `uk.co.siland.culvery.capability.music.ui.MusicTabHost`.)

- [ ] **Step 6: Run them to see them pass**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicTabTest*' --tests '*FavouritesSheetTest*'`
Expected: PASS.

- [ ] **Step 7: Screenshots**

Run `./gradlew :capability:music:recordRoborazziDebug --tests '*MusicTabScreenshotTest*'`, write the 6 names to `$TMP/expected-baselines.txt`, run the baseline check (Task 12's 7 stay unchanged), and open every one. Check: the playing Lounge + Dining room row first with Next greyed and a Rooms chip, Kitchen paused, Bedroom "Nothing playing"; Favourites and Shuffle everything on every row; `music_tab_rooms_dark` shows the Lounge's and the Dining room's own sliders under the row; the status line above the rows; the sheet 600 dp at the right edge with three Favourites (two with grey art, one with the note), and the empty one's sentence.

- [ ] **Step 8: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/music/src
git commit -m "Add the Music tab with a row per Sonos group and the Favourites sheet"
```

---
### Task 14: Connecting: a typed address and `MusicConnect` (§4.1, §6; rulings 17, 19, 20; Review Focus 4)

**Review:** opus.

**Files:**
- Create (under `capability/music/src/main/java/uk/co/siland/culvery/capability/music/`): `SpeakerAddress.kt`, `MusicConnect.kt`
- Modify: `MusicWords.kt` (connecting's lines)
- Test (under `capability/music/src/test/java/uk/co/siland/culvery/capability/music/`): `SpeakerAddressTest.kt`, `MusicConnectTest.kt`

**Interfaces:**
- Consumes: `MusicProvider`, `MusicLink`, `MusicUnreachableException`, `isHomeNetworkHost`, `MusicSettings.connect`, `Speakers.providers` (Task 4); `problemLine` (Task 7); `COULD_NOT_SAVE` (`:core:plugin`); `TestMusicProvider` (`found`, `speakers`, `failure`, `asked`).
- Produces:
  - `fun speakerHost(typed: String): String?`
  - `sealed interface ConnectOutcome { data object Connected; data class Failed(val line: String) }`
  - `class MusicConnect internal constructor(provider: MusicProvider?, save: suspend (String, MusicLink) -> Unit)`, `@Inject constructor(speakers: Speakers, settings: MusicSettings)`: `suspend fun find(): ConnectOutcome`, `suspend fun enterAddress(typed: String): ConnectOutcome`
  - `MusicWords.kt`: `NOTHING_ANSWERED`, `TYPE_FOUR_NUMBERS`, `NO_SPEAKER_THERE`

- [ ] **Step 1: Write the failing tests**

Create `SpeakerAddressTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §4.1, §6: a typed address is four numbers on the home network, tidied; anything else contacts nothing. */
class SpeakerAddressTest {
    /** Review Focus 4. Fails if a port, a name, a public address or a fifth number gets through, or spaces and leading zeros don't. */
    @Test
    fun onlyFourNumbersInAPrivateRangeAreASpeakerAddress() {
        assertThat(speakerHost("192.168.1.20")).isEqualTo("192.168.1.20")
        assertThat(speakerHost(" 192.168.1.20 ")).isEqualTo("192.168.1.20")
        assertThat(speakerHost("192.168.001.020")).isEqualTo("192.168.1.20")
        assertThat(speakerHost("10.0.0.5")).isEqualTo("10.0.0.5")
        assertThat(speakerHost("172.16.4.2")).isEqualTo("172.16.4.2")
        assertThat(speakerHost("172.31.255.1")).isEqualTo("172.31.255.1")
        assertThat(speakerHost("169.254.10.1")).isEqualTo("169.254.10.1")
        listOf(
            "172.32.0.1", "8.8.8.8", "192.168.1", "192.168.1.20.1", "192.168.1.256", "192.168.1.20:1400", "sonos.local",
            "", "192.168.1.2O", "１９２.168.1.20", "192.168..20", "192.168.1.0020",
        ).forEach { assertThat(speakerHost(it)).isNull() }
    }
}
```
Create `MusicConnectTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 6 §4.1, §6: Find Sonos and a typed address, and what each says when it can't. */
@RunWith(AndroidJUnit4::class)
class MusicConnectTest {
    private val sonos = TestMusicProvider()
    private val saved = mutableListOf<Pair<String, MusicLink>>()
    private val home = MusicLink("Sonos_home", "192.168.1.20:1400")

    private fun connect(provider: MusicProvider? = sonos, failSave: Boolean = false) = MusicConnect(provider) { id, link ->
        if (failSave) throw IOException("disk full")
        saved += id to link
    }

    @Test
    fun findSavesWhatItFinds() = runTest {
        sonos.found = home
        assertThat(connect().find()).isEqualTo(ConnectOutcome.Connected)
        assertThat(saved).containsExactly("music.sonos" to home)
    }

    @Test
    fun nothingFoundSaysWhatToDo() = runTest {
        assertThat(connect().find()).isEqualTo(ConnectOutcome.Failed("No Sonos speakers answered. Check they’re on, or enter a speaker’s address."))
        assertThat(saved).isEmpty()
    }

    @Test
    fun offlineSaysSo() = runTest {
        sonos.failure = MusicUnreachableException(MusicProblem.OFFLINE)
        assertThat(connect().find()).isEqualTo(ConnectOutcome.Failed("The tablet is offline. Check the Wi-Fi."))
    }

    @Test
    fun aTypedAddressIsTidiedCheckedAndSaved() = runTest {
        sonos.speakers["192.168.1.20"] = home
        assertThat(connect().enterAddress(" 192.168.001.020 ")).isEqualTo(ConnectOutcome.Connected)
        assertThat(sonos.asked).containsExactly("check 192.168.1.20")
        assertThat(saved).containsExactly("music.sonos" to home)
    }

    /** Review Focus 4: nothing is asked of a mistyped or off-network address. */
    @Test
    fun aMistypedAddressContactsNothing() = runTest {
        val music = connect()
        val howToType = ConnectOutcome.Failed("Type the address as four numbers, like 192.168.1.20.")
        assertThat(music.enterAddress("192.168.1.20:1400")).isEqualTo(howToType)
        assertThat(music.enterAddress("8.8.8.8")).isEqualTo(howToType)
        assertThat(music.enterAddress("sonos.local")).isEqualTo(howToType)
        assertThat(sonos.asked).isEmpty()
    }

    @Test
    fun anAddressWithNoSpeakerSaysSo() = runTest {
        assertThat(connect().enterAddress("192.168.1.99"))
            .isEqualTo(ConnectOutcome.Failed("No Sonos speaker answered at that address. Check it in the Sonos app under About My System."))
    }

    /** A build with no music provider finds nothing rather than failing. */
    @Test
    fun noProviderFindsNothing() = runTest {
        assertThat(connect(provider = null).find()).isEqualTo(ConnectOutcome.Failed("No Sonos speakers answered. Check they’re on, or enter a speaker’s address."))
    }

    @Test
    fun aSaveThatFailsSaysSo() = runTest {
        sonos.found = home
        assertThat(connect(failSave = true).find()).isEqualTo(ConnectOutcome.Failed("Couldn’t save that change — try again."))
    }
}
```
Fails today: `speakerHost`, `MusicConnect` and `ConnectOutcome` don't exist.
Fails if: an off-network or mistyped address is asked; spaces or leading zeros aren't tidied; nothing found or offline says the wrong thing; a failed save is silent.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*SpeakerAddressTest*' --tests '*MusicConnectTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The lines, the address and connecting**

Append to `MusicWords.kt`:
```kotlin
internal const val NOTHING_ANSWERED = "No Sonos speakers answered. Check they’re on, or enter a speaker’s address."
internal const val TYPE_FOUR_NUMBERS = "Type the address as four numbers, like 192.168.1.20."
internal const val NO_SPEAKER_THERE = "No Sonos speaker answered at that address. Check it in the Sonos app under About My System."
```
Create `SpeakerAddress.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

private const val PARTS = 4
private const val MAX_DIGITS = 3

/**
 * A typed speaker address (6 §4.1) as four plain numbers, or null: spaces round it and leading zeros go, then it must be
 * on the home network ([isHomeNetworkHost]), so the wall never contacts anything off it by mistake (6 §6).
 */
fun speakerHost(typed: String): String? {
    val parts = typed.trim().split('.')
    if (parts.size != PARTS || parts.any { p -> p.isEmpty() || p.length > MAX_DIGITS || p.any { it !in '0'..'9' } }) return null
    return parts.joinToString(".") { it.toInt().toString() }.takeIf(::isHomeNetworkHost)
}
```
Create `MusicConnect.kt`:
```kotlin
package uk.co.siland.culvery.capability.music

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE

private const val TAG = "MusicConnect"

sealed interface ConnectOutcome {
    data object Connected : ConnectOutcome

    data class Failed(val line: String) : ConnectOutcome
}

/**
 * Settings › Music's ways to the speakers (6 §4.1): Find Sonos and a typed address, through the bound provider (Sonos,
 * 6 §3.4). What is found is saved as the connection, and the Music tab appears.
 */
@Singleton
class MusicConnect internal constructor(private val provider: MusicProvider?, private val save: suspend (String, MusicLink) -> Unit) {
    @Inject
    constructor(speakers: Speakers, settings: MusicSettings) : this(speakers.providers.firstOrNull(), settings::connect)

    suspend fun find(): ConnectOutcome = connect { it.find() } ?: ConnectOutcome.Failed(NOTHING_ANSWERED)

    suspend fun enterAddress(typed: String): ConnectOutcome {
        val host = speakerHost(typed) ?: return ConnectOutcome.Failed(TYPE_FOUR_NUMBERS)
        return connect { it.check(host) } ?: ConnectOutcome.Failed(NO_SPEAKER_THERE)
    }

    /** Null when [look] found nothing. */
    private suspend fun connect(look: suspend (MusicProvider) -> MusicLink?): ConnectOutcome? {
        val p = provider ?: return null
        val link = try {
            look(p)
        } catch (e: MusicUnreachableException) {
            return ConnectOutcome.Failed(problemLine(e.problem))
        } ?: return null
        return try {
            save(p.descriptor.id, link)
            ConnectOutcome.Connected
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't save the speakers (${e::class.simpleName})")
            ConnectOutcome.Failed(COULD_NOT_SAVE)
        }
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*SpeakerAddressTest*' --tests '*MusicConnectTest*'`
Expected: PASS.

- [ ] **Step 5: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/music/src
git commit -m "Connect to Sonos by scan or by a typed home-network address, and say why not"
```

---

### Task 15: Settings › Music: the page, the address sheet, the usual room and the doorbell share (§4.1, §5.4, §6; rulings 9, 19, 24, 25)

**Review:** sonnet.

**Files:**
- Create: `capability/music/src/main/java/uk/co/siland/culvery/capability/music/ui/MusicSettingsPage.kt`
- Modify: `MusicWords.kt` (Settings' words), `MusicCapability.kt` (`settingsPages`), `src/test/.../TestMusic.kt` (`RecordingToaster`)
- Test (under `capability/music/src/test/java/uk/co/siland/culvery/capability/music/`): `ui/InSettingsPane.kt`, `ui/MusicSettingsPageTest.kt`, `ui/MusicSettingsScreenshotTest.kt`, `MusicCapabilityTest.kt`
- Baselines (new, 4): `settings_music_dark`, `settings_music_light`, `settings_music_unconnected_dark`, `music_address_sheet_dark`.

**Interfaces:**
- Consumes: `MusicSettings.setUsualRoom/setLowerTo`, `Speakers.providers/system`, `MAX_LOWER_TO`, `SpeakerRoom` (Task 4); `MusicView` (Task 5); `problemLine` (Task 7); `MusicStatusLine`, `MusicDimens` (Task 12); `MusicConnect`, `ConnectOutcome` (Task 14); `COULD_NOT_SAVE`, `SettingsPage`, `Toaster`, `LocalOverlayHost` (`:core:plugin`); `HhTextField`, `HhMessageLine`, `HhChoiceChip`, `HhSectionLabel`, `HhSheet`, `HhCloseButton` (`:core:ui`).
- Produces:
  - `const val MUSIC_PAGE_ID = "music"`; `class MusicPage`; `data class MusicSettingsUi(connected, status, findLine, finding, rooms: List<SpeakerRoom>, usualRoomId, lowerTo)`; `class MusicSettingsActions(onFind, onEnterAddress, onUsualRoom: (String) -> Unit, onLowerTo: (Int) -> Unit)`; `@Composable fun MusicSettingsScreen(ui, actions)`; `@Composable internal fun AddressSheet(onConnect: suspend (String) -> String?, onEdited: (Boolean) -> Unit, onClose: () -> Unit)`; `@Composable internal fun MusicSettingsHost(settings, speakers, view, connect, toaster)`
  - `MusicWords.kt`: Settings' words (below; the usual room's label is `USUAL_ROOM_LABEL`), `fun lowerToWords(percent: Int): String`
  - Tests: `RecordingToaster` (`TestMusic.kt`)

- [ ] **Step 1: Write the failing tests**

Add to `TestMusic.kt` (imports `uk.co.siland.culvery.core.plugin.Toaster`):
```kotlin
internal class RecordingToaster : Toaster {
    val shown = mutableListOf<String>()

    override fun show(message: String, icon: String, urgent: Boolean) {
        shown += message
    }
}
```
Create `ui/InSettingsPane.kt` (as the door's: Settings hosts a page in a vertical scroll, 720 dp wide):
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Hosts a page as SettingsScreen does: inside a vertical scroll, in a column 720 dp wide. Add no scroll of the page's own. */
@Composable
internal fun InSettingsPane(modifier: Modifier = Modifier.fillMaxWidth().fillMaxHeight(), page: @Composable () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 720.dp)) { page() }
    }
}
```
Create `ui/MusicSettingsPageTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

/** 6 §5.4: Sonos, the usual room and the doorbell's share. */
@RunWith(AndroidJUnit4::class)
class MusicSettingsPageTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()
    private val actions = MusicSettingsActions(
        onFind = { calls += "find" },
        onEnterAddress = { calls += "address" },
        onUsualRoom = { calls += "usual $it" },
        onLowerTo = { calls += "lower $it" },
    )
    private val rooms = listOf(SpeakerRoom("RINCON_A", "Kitchen", 30), SpeakerRoom("RINCON_B", "Lounge", 40))
    private val connected = MusicSettingsUi(
        connected = true, status = null, findLine = null, finding = false, rooms = rooms, usualRoomId = "RINCON_A", lowerTo = 20,
    )

    private fun show(ui: MusicSettingsUi) = compose.setContent { CulveryTheme(dark = true) { InSettingsPane { MusicSettingsScreen(ui, actions) } } }

    @Test
    fun connectedShowsTheRoomsAndTheShare() {
        show(connected)
        compose.onNodeWithTag("music_connection").assertTextEquals("Connected.")
        compose.onNodeWithTag("music_usual_RINCON_A").assertIsSelected()
        compose.onNodeWithTag("music_usual_RINCON_B").performClick()
        compose.onNodeWithTag("music_lower_to")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("When the doorbell rings, lower music to")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "20 % of its volume"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertThat(calls).containsExactly("usual RINCON_B", "lower 0").inOrder()
        compose.assertAccessible()
    }

    /** 6 §5.4: 0 % mutes. */
    @Test
    fun noShareSaysMuted() {
        show(connected.copy(lowerTo = 0))
        compose.onNodeWithTag("music_lower_to").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 % (muted)"))
    }

    @Test
    fun notConnectedSaysWhatToDo() {
        show(connected.copy(connected = false, rooms = emptyList(), usualRoomId = null))
        compose.onNodeWithTag("music_connection").assertTextEquals("Not connected yet. Find Sonos, or enter a speaker’s address.")
        compose.onNodeWithText("The rooms appear here once Sonos is found.").assertExists()
        compose.onNodeWithTag("music_find").performClick()
        compose.onNodeWithTag("music_address").performClick()
        assertThat(calls).containsExactly("find", "address").inOrder()
    }

    @Test
    fun findingSaysSoAndWaits() {
        show(connected.copy(finding = true))
        compose.onNodeWithTag("music_find").assertTextEquals("Looking for Sonos…").assertIsNotEnabled()
    }

    /** 6 §6: the same line as the tab and the card. */
    @Test
    fun aProblemShowsItsLine() {
        show(connected.copy(status = "Culvery found a different Sonos system. Find Sonos again in Settings › Music."))
        compose.onNodeWithText("Culvery found a different Sonos system. Find Sonos again in Settings › Music.").assertExists()
        compose.onNodeWithTag("music_connection").assertDoesNotExist()
    }

    @Test
    fun aFindThatFoundNothingSaysWhy() {
        show(connected.copy(connected = false, findLine = "No Sonos speakers answered. Check they’re on, or enter a speaker’s address."))
        compose.onNodeWithTag("music_find_line").assertTextEquals("No Sonos speakers answered. Check they’re on, or enter a speaker’s address.")
    }

    /** The sheet sends what was typed and shows the line it gets back; nothing is sent while the field is empty. */
    @Test
    fun theAddressSheetSendsWhatWasTypedAndSaysWhyNot() {
        val sent = mutableListOf<String>()
        compose.setContent {
            CulveryTheme(dark = true) {
                AddressSheet(onConnect = {
                    sent += it
                    "Type the address as four numbers, like 192.168.1.20."
                }, onEdited = {}, onClose = {})
            }
        }
        compose.onNodeWithTag("music_address_connect").assertIsNotEnabled()
        compose.onNodeWithTag("music_address_field").performTextInput("192.168.1.20:1400")
        compose.onNodeWithTag("music_address_connect").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            sent.isNotEmpty()
        }
        compose.onNodeWithTag("music_address_line").assertTextEquals("Type the address as four numbers, like 192.168.1.20.")
        assertThat(sent).containsExactly("192.168.1.20:1400")
    }
}
```
Create `ui/MusicSettingsScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** Settings › Music as Settings shows it, beside the 320 dp page list; the address sheet at the right edge. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class MusicSettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val actions = MusicSettingsActions({}, {}, {}, {})
    private val connected = MusicSettingsUi(
        connected = true, status = null, findLine = null, finding = false,
        rooms = listOf(SpeakerRoom("RINCON_A", "Kitchen", 30), SpeakerRoom("RINCON_B", "Lounge", 40), SpeakerRoom("RINCON_C", "Dining room", 35)),
        usualRoomId = "RINCON_A", lowerTo = 20,
    )

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { CulveryTheme(dark = dark) { Box(Modifier.size(960.dp, 800.dp).background(Culvery.colors.bg)) { content() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun musicDark() = snap("settings_music_dark", true) { InSettingsPane { MusicSettingsScreen(connected, actions) } }

    @Test fun musicLight() = snap("settings_music_light", false) { InSettingsPane { MusicSettingsScreen(connected, actions) } }

    @Test
    fun unconnectedDark() = snap("settings_music_unconnected_dark", true) {
        InSettingsPane {
            MusicSettingsScreen(
                connected.copy(connected = false, rooms = emptyList(), usualRoomId = null,
                    findLine = "No Sonos speakers answered. Check they’re on, or enter a speaker’s address."),
                actions,
            )
        }
    }

    @Test
    fun addressSheetDark() = snap("music_address_sheet_dark", true) {
        Box(Modifier.size(960.dp, 800.dp), contentAlignment = Alignment.CenterEnd) { AddressSheet(onConnect = { null }, onEdited = {}, onClose = {}) }
    }
}
```
In `MusicCapabilityTest.kt`, extend the helper and add:
```kotlin
    private fun capability(speakers: Speakers) = MusicCapability(
        speakers, MusicView(speakers, settings, scope), MusicActions({ speakers.system.value }, scope),
        MusicChecks(speakers.system, MutableStateFlow(true), scope), ArtCache { null },
        settings, MusicConnect(speakers.providers.firstOrNull()) { id, link -> settings.connect(id, link) }, RecordingToaster(),
    )

    /** 6 §5.4: Settings › Music after Calendars (400), before Door (500); none in a build with no provider. */
    @Test
    fun theSettingsPageIsMusicAt450() {
        val page = capability(Speakers(setOf(TestMusicProvider()), settings, scope)).settingsPages().single()
        assertThat(listOf(page.id, page.title, page.order)).containsExactly("music", "Music", 450).inOrder()
        assertThat(capability(Speakers(emptySet(), settings, scope)).settingsPages()).isEmpty()
    }
```
(`MusicConnect`'s internal constructor takes the provider and a save; `RecordingToaster` is in `TestMusic.kt`.)
Fails today: `speakerHost`, `MusicConnect`, the page and the sheet don't exist; `MusicCapability` has no page.
Fails if: an off-network or mistyped address is asked; nothing found or offline says the wrong thing; the sample is offered in a build without it, or Find Sonos picks it; a failed save is silent; the share's words or 0 % drift; the page sits elsewhere in Settings.

Fails today: the page and the sheet don't exist; `MusicCapability` has no page.
Fails if: the share's words or 0 % drift; the usual room or the share isn't sent when chosen; the problem line doesn't take the connection line's place; the sheet sends an empty field or hides the line it gets back; the page sits elsewhere in Settings.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicSettingsPageTest*' --tests '*MusicCapabilityTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The words**

Append to `MusicWords.kt`:
```kotlin
internal const val SONOS = "Sonos"
internal const val FIND_SONOS = "Find Sonos"
internal const val LOOKING_FOR_SONOS = "Looking for Sonos…"
internal const val ENTER_ADDRESS = "Enter a speaker’s address"
internal const val NOT_CONNECTED = "Not connected yet. Find Sonos, or enter a speaker’s address."
internal const val CONNECTED = "Connected."
internal const val FOUND_SONOS = "Found Sonos."
internal const val CONNECTED_TO_SONOS = "Connected to Sonos."
internal const val ADDRESS_HINT = "Shown in the Sonos app under About My System."
internal const val SPEAKER_ADDRESS = "Speaker’s address"
internal const val ADDRESS_EXAMPLE = "192.168.1.20"
internal const val CONNECT_TO_SPEAKER = "Connect to this speaker"
internal const val CHECKING = "Checking…"
internal const val USUAL_ROOM_LABEL = "Usual room"
internal const val ROOMS_AFTER_FIND = "The rooms appear here once Sonos is found."
internal const val DOORBELL = "Doorbell"
internal const val LOWER_MUSIC_TO = "When the doorbell rings, lower music to"

/** The doorbell's share, said as what it does: 0 % mutes (6 §5.4). */
fun lowerToWords(percent: Int): String = if (percent == 0) "0 % (muted)" else "$percent % of its volume"
```

- [ ] **Step 4: The page**

Create `ui/MusicSettingsPage.kt`:
```kotlin
package uk.co.siland.culvery.capability.music.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.music.ADDRESS_EXAMPLE
import uk.co.siland.culvery.capability.music.ADDRESS_HINT
import uk.co.siland.culvery.capability.music.CHECKING
import uk.co.siland.culvery.capability.music.CONNECTED
import uk.co.siland.culvery.capability.music.CONNECTED_TO_SONOS
import uk.co.siland.culvery.capability.music.CONNECT_TO_SPEAKER
import uk.co.siland.culvery.capability.music.ConnectOutcome
import uk.co.siland.culvery.capability.music.DOORBELL
import uk.co.siland.culvery.capability.music.ENTER_ADDRESS
import uk.co.siland.culvery.capability.music.FIND_SONOS
import uk.co.siland.culvery.capability.music.FOUND_SONOS
import uk.co.siland.culvery.capability.music.LOOKING_FOR_SONOS
import uk.co.siland.culvery.capability.music.LOWER_MUSIC_TO
import uk.co.siland.culvery.capability.music.MAX_LOWER_TO
import uk.co.siland.culvery.capability.music.MUSIC
import uk.co.siland.culvery.capability.music.MusicConnect
import uk.co.siland.culvery.capability.music.MusicSettings
import uk.co.siland.culvery.capability.music.MusicSystem
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.NOT_CONNECTED
import uk.co.siland.culvery.capability.music.ROOMS_AFTER_FIND
import uk.co.siland.culvery.capability.music.SONOS
import uk.co.siland.culvery.capability.music.SPEAKER_ADDRESS
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.capability.music.USUAL_ROOM_LABEL
import uk.co.siland.culvery.capability.music.lowerToWords
import uk.co.siland.culvery.capability.music.problemLine
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens

private const val TAG = "MusicSettings"

const val MUSIC_PAGE_ID = "music"

/** Settings › Music (6 §5.4), admin-only as the rest of Settings; after Calendars (400), before Door (500). */
class MusicPage internal constructor(private val content: @Composable () -> Unit) : SettingsPage {
    override val id = MUSIC_PAGE_ID
    override val title = MUSIC
    override val order = 450

    @Composable
    override fun Content() = content()
}

data class MusicSettingsUi(
    val connected: Boolean,
    /** The §6 line, which takes the connection line's place. */
    val status: String?,
    /** Why the last Find Sonos found nothing. */
    val findLine: String?,
    val finding: Boolean,
    val rooms: List<SpeakerRoom>,
    val usualRoomId: String?,
    val lowerTo: Int,
)

class MusicSettingsActions(
    val onFind: () -> Unit,
    val onEnterAddress: () -> Unit,
    val onUsualRoom: (String) -> Unit,
    val onLowerTo: (Int) -> Unit,
)

@Composable
fun MusicSettingsScreen(ui: MusicSettingsUi, actions: MusicSettingsActions) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(MusicDimens.settingsGap), modifier = Modifier.testTag("settings_music")) {
        Text(MUSIC, style = HhType.screenTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        HhSectionLabel(SONOS)
        if (ui.status != null) {
            MusicStatusLine(ui.status)
        } else {
            Text(if (ui.connected) CONNECTED else NOT_CONNECTED, style = HhType.body, color = c.mute, modifier = Modifier.testTag("music_connection"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicDimens.buttonGap)) {
            HhButton(
                if (ui.finding) LOOKING_FOR_SONOS else FIND_SONOS, actions.onFind, Modifier.testTag("music_find"),
                tone = if (ui.connected) ButtonTone.Quiet else ButtonTone.Primary, icon = Icons.SEARCH, enabled = !ui.finding, on = ButtonSurface.Bg,
            )
            HhButton(ENTER_ADDRESS, actions.onEnterAddress, Modifier.testTag("music_address"), on = ButtonSurface.Bg)
        }
        HhMessageLine(ui.findLine, HhType.body, c.danger, "music_find_line")
        HhSectionLabel(USUAL_ROOM_LABEL)
        if (ui.rooms.isEmpty()) {
            Text(ROOMS_AFTER_FIND, style = HhType.body, color = c.mute)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MusicDimens.buttonGap), verticalArrangement = Arrangement.spacedBy(MusicDimens.buttonGap)) {
                ui.rooms.forEach { room ->
                    HhChoiceChip(room.name, selected = room.id == ui.usualRoomId, tag = "music_usual_${room.id}", onClick = { actions.onUsualRoom(room.id) })
                }
            }
        }
        HhSectionLabel(DOORBELL)
        LowerToRow(ui.lowerTo, actions.onLowerTo)
    }
}

/** 0–50 % in 5 % steps, saved when let go (6 §5.4). */
@Composable
private fun LowerToRow(lowerTo: Int, onLowerTo: (Int) -> Unit) {
    val c = Culvery.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: lowerTo.toFloat()).roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(ListRowTokens.lineGap)) {
        Text(LOWER_MUSIC_TO, style = HhType.rowTitle, color = c.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ListRowTokens.trailingGap)) {
            Slider(
                value = dragging ?: lowerTo.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onLowerTo(it.roundToInt()) }
                    dragging = null
                },
                valueRange = 0f..MAX_LOWER_TO.toFloat(),
                steps = MusicDimens.LOWER_STEPS,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surf3),
                modifier = Modifier.weight(1f).testTag("music_lower_to").semantics {
                    contentDescription = LOWER_MUSIC_TO
                    stateDescription = lowerToWords(shown)
                },
            )
            Text(lowerToWords(shown), style = HhType.body, color = c.mute)
        }
    }
}

/** Enter a speaker's address (6 §4.1): [onConnect] answers null when connected, else the line to show. */
@Composable
internal fun AddressSheet(onConnect: suspend (String) -> String?, onEdited: (Boolean) -> Unit, onClose: () -> Unit) {
    val c = Culvery.colors
    var typed by remember { mutableStateOf("") }
    var line by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun connect() {
        if (checking || typed.isBlank()) return
        scope.launch {
            checking = true
            line = onConnect(typed)
            checking = false
        }
    }
    HhSheet(
        padding = PaddingValues(horizontal = MusicDimens.sheetPaddingH, vertical = MusicDimens.sheetPaddingV),
        modifier = Modifier.testTag("music_address_sheet").semantics { paneTitle = ENTER_ADDRESS },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ENTER_ADDRESS, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(ADDRESS_HINT, style = HhType.body, color = c.mute)
            }
            HhCloseButton(onClose)
        }
        HhTextField(
            typed,
            {
                typed = it
                line = null
                onEdited(it.isNotEmpty())
            },
            placeholder = ADDRESS_EXAMPLE, label = SPEAKER_ADDRESS, tag = "music_address_field",
            capitalization = KeyboardCapitalization.None, error = line, onDone = ::connect,
        )
        HhMessageLine(line, HhType.body, c.danger, "music_address_line", fieldError = line)
        HhButton(
            if (checking) CHECKING else CONNECT_TO_SPEAKER, ::connect, Modifier.testTag("music_address_connect"),
            tone = ButtonTone.Primary, enabled = !checking && typed.isNotBlank(), on = ButtonSurface.Bg,
        )
    }
}

/** Settings › Music with its data; reads the layout once as it opens, for the room picker. */
@Composable
internal fun MusicSettingsHost(settings: MusicSettings, speakers: Speakers, view: MusicView, connect: MusicConnect, toaster: Toaster) {
    val prefs by settings.prefs.collectAsStateWithLifecycle(null)
    val groups by view.groups.collectAsStateWithLifecycle(emptyList())
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val system by speakers.system.collectAsStateWithLifecycle()
    var finding by remember { mutableStateOf(false) }
    var findLine by remember { mutableStateOf<String?>(null) }
    val overlay = LocalOverlayHost.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(system) { system?.let { readRooms(it) } }
    val p = prefs ?: return
    fun said(outcome: ConnectOutcome, done: String): String? = when (outcome) {
        ConnectOutcome.Connected -> {
            toaster.show(done)
            null
        }
        is ConnectOutcome.Failed -> outcome.line
    }
    MusicSettingsScreen(
        MusicSettingsUi(
            connected = system != null,
            status = problem?.let(::problemLine),
            findLine = findLine,
            finding = finding,
            rooms = groups.flatMap { it.rooms },
            usualRoomId = p.usualRoomId,
            lowerTo = p.lowerTo,
        ),
        MusicSettingsActions(
            onFind = {
                scope.launch {
                    finding = true
                    findLine = null
                    findLine = said(connect.find(), FOUND_SONOS)
                    finding = false
                }
            },
            onEnterAddress = {
                overlay.show {
                    AddressSheet(
                        onConnect = { typed -> said(connect.enterAddress(typed), CONNECTED_TO_SONOS).also { if (it == null) overlay.dismiss() } },
                        onEdited = overlay::markEdited,
                        onClose = overlay::dismiss,
                    )
                }
            },
            onUsualRoom = { id -> scope.launch { save(toaster) { settings.setUsualRoom(id) } } },
            onLowerTo = { percent -> scope.launch { save(toaster) { settings.setLowerTo(percent) } } },
        ),
    )
}

/** No check runs while only Settings shows, and the picker needs the rooms. */
private suspend fun readRooms(system: MusicSystem) {
    try {
        system.checkLayout()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the rooms for Settings (${e::class.simpleName})")
    }
}

/** A setting that didn't save says so; nothing changed. */
private suspend fun save(toaster: Toaster, write: suspend () -> Unit) {
    try {
        write()
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't save a music setting (${e::class.simpleName})")
        toaster.show(COULD_NOT_SAVE, urgent = true)
    }
}
```
In `MusicCapability.kt`, the constructor gains `private val settings: MusicSettings, private val connect: MusicConnect, private val toaster: Toaster` after `art`, and:
```kotlin
    private val pages: List<SettingsPage> =
        if (speakers.providers.isEmpty()) emptyList() else listOf(MusicPage { MusicSettingsHost(settings, speakers, view, connect, toaster) })

    override fun settingsPages(): List<SettingsPage> = pages
```
(imports `uk.co.siland.culvery.core.plugin.SettingsPage`, `uk.co.siland.culvery.core.plugin.Toaster`, `…ui.MusicPage`, `…ui.MusicSettingsHost`.)


- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :capability:music:testDebugUnitTest`
Expected: PASS. (`MusicSettingsScreenshotTest` fails `verify` until recorded.)

- [ ] **Step 6: Screenshots**

Run `./gradlew :capability:music:recordRoborazziDebug --tests '*MusicSettingsScreenshotTest*'`, write the 4 names to `$TMP/expected-baselines.txt`, run the baseline check, and open every one. Check: "Music", the SONOS label, "Connected." in mute, Find Sonos (quiet when connected, primary when not) with Enter a speaker's address beside it; the three room chips with Kitchen chosen; the DOORBELL label, the slider at 20 with "20 % of its volume"; unconnected: the danger line under the buttons and the rooms sentence; the sheet with its field, hint and a disabled Connect to this speaker.

- [ ] **Step 7: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` and `./gradlew :app:assembleDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/music/src
git commit -m "Add Settings › Music: find Sonos or enter a speaker's address, the usual room, and how far the doorbell lowers music"
```

---

### Task 16: The emulator walk-through on `culvery-5` with a real speaker by address; the protocol checks; the captures (§8.1, §8.2)

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch separately, before this task.)

**Files:**
- Modify: `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Step 7)
- Any file a fault fix needs (Step 6's loop)

**Rules for this task.** The emulator `emulator-5554` holds a signed release and the household: **never install on it, never uninstall anything there, and never run a Gradle `install…` task while it is connected** (Gradle installs on every device). Use only the `culvery-5` AVD Plan 5 made; every `adb` command names `-s "$S"`, its serial. The user types every PIN and reads the speaker's address from the Sonos app; the household's room names, addresses and household id never go into a file, a commit or this plan's notes. Captures stay in `$TMP` until Task 17 has used them, then are deleted. No Sonos setting is changed except what the checklist says, and each is put back (volumes, the queue the household had: ask the user before Shuffle everything replaces a group's queue).

- [ ] **Step 1: The AVD**

`"$ANDROID_HOME/emulator/emulator" -list-avds` must list `culvery-5`; if not, **stop and ask** (Plan 5 made it). Then:
```bash
"$ANDROID_HOME/emulator/emulator" -avd culvery-5 -no-snapshot-save &
adb devices
```
Write its serial (never `emulator-5554`) to `$TMP/culvery-5-serial` and `S=$(cat "$TMP/culvery-5-serial")`.

- [ ] **Step 2: Install the debug build there only**

```bash
./gradlew :app:assembleDebug
adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk
```
The test household from Plan 5's walk-through is still there; if not, the user runs the wizard with a test household.

- [ ] **Step 3: Connecting**

There are no sample speakers (§3.4): everything is checked on the household's speakers, by address. Fake rings are posted from the adb shell as in Plan 5 (`ring() { adb -s "$S" shell "cmd notification post -t 'Front Door' culvery-fake-ring '$1'"; }`). Tick each item with one line of what was seen (no room names); anything unexpected goes to Step 6. Ask the user before anything changes what a group is playing, its queue or its volume, and put each back.
1. Settings › Music (Admin PIN) sits between Calendars and Door: "Not connected yet. …", Find Sonos, Enter a speaker's address.
2. Find Sonos on the emulator: "No Sonos speakers answered. …" (its SSDP can't reach the home network, §8.1).
3. Enter a speaker's address: a mistyped one ("192.168.1") and a port ("…:1400") each say how to type it. The user reads one speaker's address from the Sonos app (Settings › System › About My System) and types it; the address goes nowhere but the field. "Connected to Sonos.", the sheet closes, the room chips are the household's. **Protocol check 1:** if the rooms never come, or the status line shows, read `adb -s "$S" logcat -d -s MusicChecks:W` and stop: `GetZoneGroupState` or `GetHouseholdID` may not answer on this firmware.
4. The rail shows Music at slot 30, between Calendar and Door, no badge; the Music tab's rows match the Sonos app's groups, playing first.

- [ ] **Step 4: The controls, Shuffle everything, Favourites and the doorbell**

5. Home: the Music card at row 2, column 3 on the playing group, with Pause, Next and the volume, and no Shuffle everything while it plays.
6. Play, Pause, Next and the group slider on a group (ask first); the Sonos app agrees within 2 seconds; a change made in the Sonos app shows on the wall within 2 seconds. **Protocol check 2:** the group slider (`SnapshotGroupVolume`, then `SetGroupVolume`) keeps the rooms' ratio, as the Sonos app's own slider does.
7. Pause on Home, open Settings and close it, go to Calendar and back to Home: the card still shows the paused room with Play (ruling 8); Play resumes it.
8. A grouped row's Rooms chip: each room's slider moves only that room.
9. A radio favourite (BBC Sounds) from the sheet: it plays, the row shows the station and the programme, Next greyed.
10. A playlist favourite: it replaces that group's queue (ask first) and plays; the Sonos app's play mode didn't change.
11. Shuffle everything on a group paused on a radio station (ask first): the whole library, shuffled. **Protocol check 3:** if Sonos refuses it ("Sonos didn't do that. Try again."), stop and report: `x-rincon-playlist:<leader>#A:TRACKS` with empty metadata, the one-item browse, or the step order (ruling 12) needs changing before anything else.
12. A Favourite the wall can't play (if the household has one, e.g. a service signed in only on a phone): "Sonos couldn’t play this here.", the sheet stays.
13. Settings › Music: choose the usual room the household names; stop everything on the tab: Home shows that room, "Nothing playing", Play and Shuffle everything. Doorbell share: 0 % reads "0 % (muted)"; back to 20 %.
14. With a two-room group playing, `ring 'Someone is at your Front Door'`: the card and the chime; each room drops to about a fifth (the Rooms chip shows both); Got it: both back, the balance as it was. Ring again and drag one room in the Sonos app while the card shows; Got it: that room stays where it was dragged, the other comes back. A second ring while the card shows: no second lowering. Set the share to 0 % and ring: silence, then both rooms back with their balance.
15. `adb -s "$S" shell svc wifi disable` and `svc data disable` (the emulator's network): within a check or two the tab says "The tablet is offline. Check the Wi-Fi."; enable both: the line goes.
16. TalkBack (short, with the user's yes, as Plan 5 did it): the card says "<room>, playing <title> by <artist>"; Pause "Pause <room>"; the slider "<room> volume, <n> percent"; Next on a radio row unavailable.

- [ ] **Step 5: The captures (§8.2)**

```bash
adb -s "$S" logcat -c
adb -s "$S" shell setprop log.tag.SonosCapture DEBUG
```
Then on the wall: open the Music tab (layout and playback), open a Favourites sheet, play a radio favourite in a group (ask first), let a library track play in another, and Find Sonos once. Then:
```bash
adb -s "$S" logcat -d -s SonosCapture:D > "$TMP/sonos-capture.txt"
adb -s "$S" shell setprop log.tag.SonosCapture INFO
adb -s "$S" logcat -c
```
Run the names check: grep `$TMP/sonos-capture.txt` for each of the household's room names, people's names, the speakers' addresses and the household id (the user gives the list; it stays in `$TMP`): nothing may match. If anything does, widen `SonosXml.redact` (a failing case in `SonosXmlTest.theCaptureShapeReplaces…` first), fix, recapture. Keep the file for Task 17.

- [ ] **Step 6: The fault loop**

Each fault: write it as a failing test in its owning module, fix it in that task's style (a subagent may do it), run the module's tests and then the gate, commit with a message naming the fault, reinstall with `adb -s "$S" install -r …` and re-check that item. A look-and-feel wish is a follow-up for the end-of-v1 design walk-through, not a fix.

- [ ] **Step 7: Record and close**

Add to `docs/superpowers/plans/2026-09-23-plan1-followups.md`, under a new "Plan 6" heading: the checklist's outcome in a few lines, and **the three protocol checks' outcomes** (`GetZoneGroupState` and `GetHouseholdID`; the group snapshot; Shuffle everything's URI, empty metadata, one-item browse and order: as ruled, or what had to change, so the tests' pins of ruling 12 can be relabelled as confirmed), each fault and its commit, and taste notes for the end-of-v1 design walk-through (the card's layout, the row's density, the copy written here). No room names, addresses or household id. Put back the Sonos volumes and queues as the household had them. Close the AVD (`adb -s "$S" emu kill`). Commit:
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 6's emulator walk-through on the household's speakers"
```

---

### Task 17: On the tablet: fixtures from the captures, Find Sonos by scan, the lowering timed, final review, merge and install (§7, §8.2, §8.3)

**Review:** the controller with the user; an opus final review of the whole branch. **Needs the son's SM-T510 on the cable, the household's speakers on, and the household's yes.**

**Files:**
- Modify: `provider/music-sonos/src/test/java/uk/co/siland/culvery/provider/music_sonos/SonosFixtures.kt` (captures replace the documented examples), `SonosXmlTest.kt`, `SonosSystemTest.kt`; maybe `SonosXml.kt` (Step 2)
- Modify: `docs/superpowers/plans/2026-09-23-plan1-followups.md`

**Rules.** 4e's tablet rules apply (`docs/superpowers/plans/2026-10-12-4e-walkthrough.md`, "Rules"): save and restore every setting changed, never touch other apps' data, install signed releases with `install -r`, **never uninstall Culvery** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` is a stop and ask), the serial in `$TMP/culvery-4e-serial` (`S=$(cat "$TMP/culvery-4e-serial")`), device paths inside one quoted `shell` string, and the names check before anything is committed.

- [ ] **Step 1: Fixtures from the captures (failing first)**

Rewrite `SonosFixtures` from `$TMP/sonos-capture.txt`: each answer's shape as Sonos sent it (the elements, attributes, escaping and the empty and `NOT_IMPLEMENTED` values the captures show), the rooms as `[room 1]`, `[room 2]`, … and any person as `[person]`, the speaker ids `RINCON_…` made up in the same shape, the household `Sonos_…` made up, addresses as the tests' own; titles, artists and stations stay generic (Song, Artist, Culvery FM). Its KDoc becomes "Captured from the household's speakers on <date> through the debug capture (6 §8.2), redacted: [room n] and [person] stand for the household's names." Update the tests' expected names to the placeholders. Run the names check (the 4e walk-through's grep, with the user's list) over the fixtures. Then:
Run: `./gradlew :provider:music-sonos:testDebugUnitTest`
Expected: FAIL where the real shapes differ from the documented ones (if nothing fails, the documented shapes were right: say so and go to Step 3).

- [ ] **Step 2: Fix the parser**

Change only `SonosXml` (and, if a capture shows it, which answer `SonosSystem` reads a value from) until the captures read right. Run the module's tests, then `./gradlew testDebugUnitTest verifyRoborazziDebug`. Commit:
```bash
git add provider/music-sonos/src
git commit -m "Read Sonos's answers as the household's speakers send them"
```
Delete `$TMP/sonos-capture.txt`.

- [ ] **Step 3: Install the release**

If Plan 5 has merged into `main` by now, the controller rebases `plan-6-sonos` onto `main` first (with the user's yes) and runs the gate. Build the signed release as `docs/setup/release.md` says, then `adb -s "$S" install -r app/build/outputs/apk/release/app-release.apk` (with Plan 5's build or after it; never uninstall).

- [ ] **Step 4: The real thing**

1. Settings › Music (Admin PIN) › **Find Sonos**: within about 5 seconds "Found Sonos." and the household's rooms (§8.3: the scan's real test; SSDP under the multicast lock). If nothing answers, read `adb -s "$S" logcat -d -s SonosDiscovery:W` and stop.
2. The user chooses the usual room.
3. Home follows the playing room; the Music tab's rows match the Sonos app.
4. The bell, rung for real with music playing: the rooms drop, the chime plays, Got it puts them back. **Time it** (ruling 3: `quieted()` waits up to 500 ms, inside Door's lock): with `adb -s "$S" logcat -v threadtime` running and the user listening, note whether the music had dropped before the chime began and how long the card took to chime after it appeared (a phone's stopwatch or a slow-motion video is enough); ring three times. Record the times in the follow-ups file; a chime that lags past about a second, or music still loud when it starts, is a fault for Step 5's review.
5. Read the tablet's logs: `adb -s "$S" logcat -d | grep -E "MusicActions|MusicChecks|MusicQuieter|SonosDiscovery|ShellAlerts|MusicConnect"`: commands and outcomes only, no room, title, Favourite or address.
From here on, the tablet's logs are read whenever it is plugged in while work is under way (§8.3). Restore anything changed under the 4e rules; leave Culvery pinned in front.

- [ ] **Step 5: Final review, merge, install**

Update the follow-ups file (what the captures showed, the lowering's timings, and what the tablet did, in words without names; anything left for later, e.g. the design walk-through notes). Commit. Then the opus final review of the whole branch against the spec, one fix round, and `superpowers:finishing-a-development-branch` (the user has always chosen to merge locally; never push). Build the release from the merged `main`, `install -r` it on the tablet, and check Home's card and the bell once more.

---

## Spec coverage (6 design → tasks)

| Spec | Task |
|---|---|
| §1 outcome; done on the emulator and the tablet | 16, 17 |
| §2.1 job: rooms, starting music, the doorbell | 5–7, 10, 12–15 |
| §2.2 Home follows the playing room; the usual room; a paused room stays | 4 (stored), 5, 12, 15 |
| §2.3 the doorbell share, quiet hours too, left if changed | 2, 3, 6, 15 |
| §2.4 groups as Sonos groups them; room volumes | 10, 13 |
| §2.5 Favourites | 7, 10, 13 |
| §2.6 no limits; Settings admin-only | 13, 15 (Settings is admin-only already) |
| §2.7 local API, nothing secret | 4, 8–11 |
| §3.1 `:capability:music`, `MusicSystem`, `SpeakerGroup`, `Favourite` | 4 |
| §3.2 `SonosDiscovery`, `SonosClient`, `SonosXml`, `music.sonos` | 8–11 |
| §3.3 `AlertQuieter`, `show(loud)`, `quieted()`, Door's one change | 2, 3 |
| §3.4 no sample speakers | none: tests use `TestMusicSystem` (4), walk-throughs the real speakers (16) |
| §3.5 backup: no new rule | Global Constraints (DataStore under `files/datastore/`) |
| §4.1 Find Sonos, a speaker's address, the automatic rescan | 10, 11, 14, 15 |
| §4.2 checks: 2 s, 30 s, the tab opening, after a command, art in memory | 5, 10, 12, 13 |
| §4.3 commands to the leader, at once, Next greyed | 7, 10, 12 |
| §4.4 Shuffle everything, queue before play mode | 9, 10, 16 |
| §4.5 Favourites by kind | 10 |
| §4.6 lowering room by room, remembered first, restore within 1, retried | 6, 10 |
| §5.1 the Home card | 12 |
| §5.2 the Music tab | 13 |
| §5.3 the Favourites sheet | 13 |
| §5.4 Settings › Music | 14, 15 |
| §5.5 TalkBack | 12, 13, 15 |
| §6 when things go wrong; privacy (home network only, https art, logs) | 7, 8–11, 12–15 |
| §7 tests | every task |
| §8.1 emulator, by address | 16 |
| §8.2 captures | 9, 16, 17 |
| §8.3 tablet | 17 |
| §9 not in this plan | none |
