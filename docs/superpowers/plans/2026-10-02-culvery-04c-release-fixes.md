# Culvery — Plan 4c: Release build, fixes and performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (chosen: a sonnet implementer per task, then a reviewer per task; tasks marked **Review: opus** get an opus reviewer) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A signed, minified release build with its own OAuth client and a logging policy; Culvery as the tablet's home app (device owner optional); the whole non-device fix backlog; four weeks ahead in the Calendar tab; and the size, start-up, runtime and economy fixes from the 4c audit.

**Architecture:** Build first (signing, the icon subset, R8 and the logging guard), then performance (start-up off Main with a splash, recomposition, one household clock, calendar flows), then the kiosk (the lockout, the home app and device owner, Connect leaving screen pinning), then calendar sync (ask for less, keep the sync token across midnight with pruning and the 4-week window, `calendar.db` v6, series deleted on a phone), the 4-week Calendar tab, correctness and per-calendar health, then people and health lists, then the emulator walkthrough on the signed, minified release. New seams live in `:core:plugin` (`HouseholdClock`, `FirstDraw`, `HomeApp`, `AppVersion`, `ShellNavigator.leavePinning()`) and `:capability:calendar` (`StoredSeries`); `:app` binds the Android ones.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1 (KSP), Room 2.8.5, Coroutines 1.10.2, OkHttp 4.12.0 + MockWebServer, kotlinx.serialization 1.9.0, Play services auth 21.4.0, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1, AGP 8.13.0 (R8). One new dependency: `androidx.core:core-splashscreen` 1.2.0 (ruling 19). Python 3 + fontTools for `tools/fonts/subset.py` only; the build never runs Python.

**Spec:** `docs/superpowers/specs/2026-10-02-culvery-4c-release-fixes-design.md` (binding; decisions D1–D11, audit ids in its Appendix A; amended after this plan's review in commit 9b46684). Parent spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§4 module rules, §7 storage and sync, §8 access, §9.3 week view as amended by D10, §10 kiosk).
**Previous plan (format, constraints):** `docs/superpowers/plans/2026-10-01-culvery-04b-weather.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (spec §9 says what 4c takes; Task 19 moves them out).

**Plan series:** 1 Foundation · 2a · 2b-1 · 2b-2 · 3a · 4a · 4b (done) · **4c Release, fixes and performance (this plan)** · 4d design, UX and accessibility · 4e the device.

**Plan review (2026-10-02).** The user ruled on this plan's first draft "all as recommended", and the spec gained D10, D11 and §6.6. This version applies them: the subset keeps the filled icons (Task 2); **Choose home app** asks Android's role dialog and **Change home app** opens the home settings on Exit kiosk's fresh PIN, both surviving a missing screen (Task 9, ruling 15); as the home app, Exit kiosk stays in front, unpinned (Task 9, ruling 28); `LockTask` and `pinAgain` are gone, replaced by `ShellNavigator.leavePinning()` (Task 10, ruling 16); C9 matches instance ids exactly and keeps a split series (Task 13, ruling 2); R8 keeps only three rules of its own and the release build is the check (Task 3, ruling 30); `SampleAddTest` gets `FirstDraw` (Task 4); the lockout, `fields=`, pruning, horizon and icon-guard tests now fail on a mutant; the old Task 8 and Task 11 are split (now Tasks 8–9 and 12–13); `LogHygieneTest` accepts only qualified names and checks `Log.wtf`/`Log.println` (ruling 18); every calendar refused is no sync (Task 16); the cursor key keeps the read's start (Task 12); as device owner Play services is allowlisted and Connect doesn't leave lock-task (ruling 29); the key script refuses Git Bash without `winpty` and has a PowerShell twin, and `.gitignore` ignores keystores (Task 1); signing is inline in `app/build.gradle.kts`; vacuous and duplicate tests are dropped, `remember(animatedColors)` goes, P2 goes through `CulveryShell`, `wallTimeEachMinute`/`WallTimeTest`/`MinuteTicker` go, and `rememberToday` never falls back to the device's zone; an accepted write clears a NeedsSignIn a refused write set (Task 15). Questions: the 42-day read stays (ruling 1); series-rule seeding is dropped (D11, ruling 3); the real release key is made in Task 1 (ruling 21); core-splashscreen 1.2.0 with no fallback (ruling 19). New: the Calendar tab looks four weeks ahead (D10, §6.6; Tasks 12 and 14, ruling 31).

**Task order and why:** D1's order, with three moves the code forces. E3 (skip a connection's other calendars after Unreachable or NeedsSignIn) moves from C3/C4's task (15) to Task 16: the refusal it must not count is a `SourceGoneException`, which *is* an `UnreachableException`, so E3 can only be right once §6.4 gives refusals their own path. The `readProblem` column lands with the v6 migration in Task 12 (spec §6.2), its behaviour and UI in Task 16. D10's wider window lands with the cursor rules in Task 12, the Calendar tab's arrows in Task 14. Every task ends green.

| # | Task | Review |
|---|---|---|
| 1 | Release signing, version, `docs/setup/release.md`, the key scripts, the "before" numbers (§3.1, §3.2, §4.5) | sonnet |
| 2 | The icon font: `Icons`, `IconFontTest`, the S3 glyphs, the subset with filled forms, `ThemeTest`, `lifecycle-viewmodel-ktx` out (§3.5) | sonnet |
| 3 | R8, resource shrinking, `proguard-rules.pro`, release logging, `LogHygieneTest`, the `Log.w/e` scrub (§3.3, §3.4, D8) | **opus** (R8, logs) |
| 4 | Start-up off Main, the first-draw gate, the splash (§4.1) | sonnet |
| 5 | Recomposition: the remembered `ColorScheme`, `now` out of the root (§4.2) | sonnet |
| 6 | `HouseholdClock`, one time source (§4.3) | sonnet |
| 7 | Calendar flows: `distinctUntilChanged`, the stability file, `flowOn` (§4.4) | sonnet |
| 8 | The lockout: the clock guard, no disk I/O on Main (§5.4 K3, K4) | **opus** (lockout) |
| 9 | The home app, Exit kiosk in front, device owner, K1, K2 (§5.1, §5.2, §5.4) | **opus** (lock-task over the lifecycle) |
| 10 | Connecting on the kiosk: `connections.manage`, Play services, leaving pinning (§5.3) | **opus** (connect flow) |
| 11 | Google asks for less: `fields=`, the gzip User-Agent, `callTimeout` (§6.1) | sonnet |
| 12 | The cursor across midnight, pruning, the 4-week window, `calendar.db` v6 (§6.2, §6.6's window) | **opus** (sync state, migration) |
| 13 | A series deleted on a phone: `StoredSeries`, C9 (§6.3 C9, D11) | **opus** (removals) |
| 14 | Looking ahead four weeks in the Calendar tab (§6.6, D10) | sonnet |
| 15 | C3 and C4 (§6.3) | sonnet |
| 16 | One calendar that can't be read, E3, the toast count, the `addConnection` test (§6.3 E3, §6.4, §6.5) | **opus** (health) |
| 17 | Reordering people (§7.1) | sonnet |
| 18 | Code health and test health (§7.2, §7.3) | **opus** (session state) |
| 19 | The walkthrough on the signed, minified release; Appendix B; README; follow-ups (§8.2, §9) | sonnet + controller |

## Rulings against the code and the APIs

Where the spec is silent, ambiguous, or doesn't fit the code or the real API, this plan rules as follows. Pinned by a test in the task named: 1, 2, 3, 4, 6, 11, 12, 13, 15, 16, 18, 23, 26, 28, 29, 31. Not pinned by a test: 5 (the header is checked, not its source), 7 (task order), 8 (measured in Task 19), 9 (docs), 10 and 22 (by eye, the latter in a screenshot), 14 (the plural is tested, the singular is copy), 17 (compiles or the step stops), 19 and 20 (build configuration), 21 (process), 24, 25 (screenshots), 27 (insets aren't measurable in Robolectric), 30 (the release build is the check, Task 3 Step 10), 32 (compiler).

1. **Google's incremental sync and the window — a spec deviation (Task 12).** Checked against the Calendar API v3 docs (`guides/sync`, `events/list`, 2026-10-02): `timeMin` and `timeMax` are *incompatible with* `syncToken`; "the result will always contain deleted entries"; "each list request should use the same set of query parameters, including the initial request"; 410 means wipe and full sync. So an incremental result holds every change to the calendar at any date, and *nothing* about an unchanged event. A token from a full sync bounded at the window's end would never deliver the events already sitting one day past it when the window reaches them. Therefore: a full sync reads **42 days past the window's end** (`SYNC_AHEAD_DAYS`); the cursor's key is "`<start of what was read>|<end of what was read>|<zone>`" (the window's start date as such is gone, §6.2); a cursor is used while what was read still covers the window — its start on or before the window's start, its end on or after the window's end — the zone is the same and Google hasn't answered 410; and pruning keeps **[window start, end of what was read)**, not the window alone as §6.2 says, because pruning anything that was read and is still ahead would lose it for good. With D10's window (today − 1 to + 28) the token lives 42 days, a full sync comes about every six weeks instead of nightly, and storage stays bounded (≈ 72 days a calendar). The user accepted the 42 days at the plan review.
2. **A cancelled series (C9, Task 13).** With `singleEvents=true` Google lists only instances, whose ids are the series id, `_`, and the original start (`piano_20261005T150000Z`, or `piano_20261005` all day). The cancelled master carries only its id. The capability's contract stays as it is (removals are ids); `:capability:calendar` gains a small read seam, `StoredSeries` (stored repeating instances of one calendar, id → rule), bound to `CalendarStore`, and the Google provider adds every stored instance whose id matches `^<Regex.escape(id)>_\d{8}(T\d{6}Z)?$` to the removals: a series split from it by "this and following" (`piano_R20261006T141500_…`) is another series and stays. Google's id convention stays inside the Google provider.
3. **No series-rule seeding (D11, Task 13).** `StoredSeries` serves C9 only; a full sync fetches each series' rule as today, now about once every six weeks. Since no nightly full sync refreshes the rules any more, an incremental pass that carries an instance of a series fetches that series' rule again (once per pass): changes are rare, and a series edited on a phone shows its new Repeats text.
4. **`fields=` (Task 11, deviation).** §6.1's list leaves out two fields the provider parses — `eventType` and `attendees(self,responseStatus)`, which drop working locations and declined invitations (`GoogleEvent.isGone`) — and includes `updated`, which nothing reads. The plan sends what the provider parses: `items(id,status,summary,start,end,recurringEventId,recurrence,eventType,attendees(self,responseStatus),extendedProperties/private,colorId),nextPageToken,nextSyncToken`; `calendarList.list` sends `items(id,summary,summaryOverride,accessRole,selected,hidden,primary),nextPageToken`. The test parses both strings and walks every serializer's descriptor, nested ones too.
5. **The User-Agent's version (Task 11).** `:provider:calendar-google` can't see `BuildConfig`. `:core:plugin` gains a qualifier `@AppVersion`; `:app` provides `BuildConfig.VERSION_NAME` under it.
6. **`kioskExited` is a `ShellViewModel` field, not a `SavedStateHandle` value (K2, Task 9, deviation).** A ViewModel field survives a configuration change, which is K2's case. A `SavedStateHandle` would also restore "exited" after a process death, so a tablet killed while unpinned would come back unpinned; a field is forgotten, and `KioskLifecycle` pins again when Culvery is in front (Review Focus 2).
7. **E3 moves to Task 16** (see Task order).
8. **What leaves Main at start-up (Task 4, partial by necessity).** `CulveryApp` starts the `Startable`s and the debug seed from `Dispatchers.Default` through `dagger.Lazy`. `MainActivity` still injects the capabilities on Main, and `CalendarCapability → CalendarRepository → Set<CalendarProvider>` builds `GoogleCalendarProvider` there. So the two costly members the audit names become lazy: `GoogleApi`'s `OkHttpClient` and `PlayServicesAuthorizer`'s `AuthorizationClient`.
9. **A release over a debug install (Task 1, docs).** §3.2 says "`pm clear` or uninstall"; `pm clear` keeps the debug signature, so Android still refuses the install. `docs/setup/release.md` says uninstall.
10. **The splash (Task 4).** `Theme.SplashScreen` with `windowSplashScreenBackground` `#FF0E1011` (the theme's `windowBackground`); no icon attribute until 4d's launcher icon, so Android shows its default.
11. **Pruning keeps an event a queued change targets (Task 12):** its sheet and its syncing mark stay right until the change is delivered (Review Focus 3).
12. **`source.readProblem` (Task 16)** holds `"REFUSED"` or null. A refusal flags a source refresh only when the calendar had no problem yet (so a deleted calendar still leaves at the next pass, and a refused one isn't re-read every pass); `SourceRefresher`'s in-memory "still listed" set goes. **Hide this calendar** clears it (a hidden calendar isn't read). A successful read clears it. A pass in which every visible calendar was refused read nothing: the connection is Unreachable and isn't marked synced (plan review 11). "Or the primary" isn't applied: `StoredSource` holds no primary flag.
13. **NeedsSignIn after a refused write (C3, Task 15).** `markSynced` keeps `NEEDS_SIGN_IN` while the connection has queued changes; a reconnect sets Ok and makes them due, as today; and a write the provider accepts (the editor's or the drain's) sets Ok too, since it proves sign-in works (plan review 17). A read-only NeedsSignIn (nothing queued) still clears when reads work again.
14. **C7's singular (Task 16):** "1 change waiting to sync was dropped." (as 4a ruling 13 does for the disconnect question).
15. **The home app (Task 9).** "Is Culvery the default home app" is `RoleManager.isRoleHeld(ROLE_HOME)` (API 29+), behind `HomeApp` in `:core:plugin`, read again each time Culvery is in front. **Choose home app** authorises `settings.manage` (the open Settings session, or the wizard's setup session) and starts `RoleManager.createRequestRoleIntent(ROLE_HOME)`: Android's yes/no dialog, with no way from it into Settings. **Change home app** asks for a fresh PIN through `kiosk.exit` (its pad says "Exit kiosk mode", which is what it does), closes Settings and signs out, then opens `Settings.ACTION_HOME_SETTINGS`. Both go through `ShellNavigator` because only the activity can unpin, and nothing opens over a pinned app; both catch `ActivityNotFoundException` and pin again.
16. **Leaving pinning (Task 10).** `ShellNavigator.leavePinning()` replaces `LockTask`, `ActivityLockTask`, `LocalLockTask` and `pinAgain`: Connect calls it just before launching Google's account chooser, and `KioskLifecycle` pins again whenever Culvery comes back to the front after being stopped, whatever the chooser answered — so a process death behind the chooser also ends pinned. An account already granted never leaves pinning.
17. **The Play services check (Task 10)** sits in `GoogleConnectFlow.start`, after the PIN (in `CalendarConnectHost`) and before anything else. `GoogleApiAvailability` comes with `play-services-auth` (its `play-services-base`); if the compiler can't see it, stop and ask — it would be a second new dependency.
18. **`LogHygieneTest`'s reach (Task 3):** `Log.w`, `Log.e`, `Log.wtf` and `Log.println` in `src/main` and `src/release` of every module (debug code never ships). Allowed in a message: `${…::class.simpleName}`, `${….code}`, and a named allowlist of *qualified* ids and fixed words (`conn.id`, `change.kind`, `what.label`, `answer.reason`, …): a bare local (`$id`, `$name`) never passes, whatever it holds today, so locals that carry an id are renamed or qualified.
19. **`core-splashscreen` 1.2.0 (Task 4)**, the `release` value of Google Maven's `maven-metadata.xml` on 2026-10-02; its `minCompileSdk` is 35, which this project has. No fallback version.
20. **The stability file (Task 7):** `composeCompiler { stabilityConfigurationFiles.add(…) }` in `culvery.android.compose`; the Kotlin 2.2.20 plugin marks the single-file `stabilityConfigurationFile` deprecated (checked in its jar). The file is `compose-stability.conf` at the repo root.
21. **"Before" numbers (Task 1)** come from the signed release with R8 off and the full font, so the user creates the real release key in Task 1 (`tools/new-release-key.sh`, or `tools/new-release-key.ps1` in PowerShell; the script refuses to run in Git Bash without `winpty`, where `keytool` can't ask for a password).
22. **The icons (Task 2):** `clear_night` → `bedtime` unless the side-by-side screenshot shows `nightlight` reads better as a clear night; `smartphone` → `mobile`. `arrow_upward` and `arrow_downward` (Task 17's buttons) join the subset now. The subset keeps every listed glyph's filled form (the single substitutions behind `rclt` and the FILL axis's FeatureVariations), so the rail's selected tab still draws filled; `IconFontTest` checks each one, and the `home_*` screenshots don't change.
23. **Setup-session folding (Task 18).** `DefaultAccessControl` keeps one private `SignedIn(who, setup)` value, changed only by one setter that also updates the public `session`; every way a session ends goes through it. The "settings.manage unless no Admin yet" helper is `mayChangeSetup` in `WizardRules.kt`, used by the Location step.
24. **`rememberNowMillis` ticks each minute (Task 6)**, from `HouseholdClock`, not every 30 s: everything that reads it shows minutes. `rememberToday` is null until the household's zone is read; it never falls back to the device's zone.
25. **Move up / Move down only in Settings › People (Task 17)**, not the wizard's Household step (§7.1 names Settings).
26. **`LockoutStore` (K4, Task 8)** loads its two numbers once, on `Dispatchers.IO`, then serves them from memory and writes with `apply()`; its methods become `suspend`, as every caller already is. `MAX_LOCK_MS = BASE_LOCK_MS shl MAX_DOUBLINGS` (16 minutes); a lock ending more than that away has expired (K3).
27. **K1's padding (Task 9).** The root pads by `WindowInsets.systemBars` always: those insets are zero while the bars are hidden (the pinned kiosk, and debug builds, which hide them too), so the padding shows only when the bars do — after Exit kiosk — without a pinned-state flag.
28. **Exit kiosk as the home app (Task 9, spec §5.1 as amended).** Exit kiosk unpins and shows the system bars. When Culvery is the home app it stays in front: moving to the back would resume Culvery as home and pin it again at once. A Home press while it is in front pauses and resumes it without stopping it, so it stays unpinned; leaving for another app and coming back pins again. Otherwise it moves to the back as today. `KioskLifecycle` (a `DefaultLifecycleObserver`) holds this, tested on a Robolectric activity.
29. **Device owner (Task 9).** As device owner Culvery allowlists itself and Google Play services (`com.google.android.gms`) for lock-task, so Google's account chooser opens inside lock-task and Connect doesn't call `leavePinning()`; otherwise screen pinning is left as it is. Tested with `ShadowDevicePolicyManager`.
30. **R8's own rules (Task 3).** kotlinx.serialization 1.9 and OkHttp 4.12 ship their consumer rules, so `app/proguard-rules.pro` holds only `-keepattributes SourceFile,LineNumberTable`, `-renamesourcefileattribute SourceFile` and the `Log` `-assumenosideeffects` for `v`/`d`/`i`. No test reads the rule text; the minified release running the weather and the town search (Task 3 Step 10), and sign-in and sync in the walkthrough, are the check.
31. **Looking ahead (D10, §6.6; Tasks 12 and 14).** `SYNC_FUTURE_DAYS` = 28; `MAX_WEEKS_AHEAD` = 3, so the last day shown is today + 27, inside the window (a test pins it). ‹ › sit together before the title, so › stays under a finger while the title's width changes; each greys at its end. The 2-minute wait restarts at any touch in the tab (seen before the children, never consumed); a new day makes a new `WeekShown`, so midnight is back on this week. **+** adds on the first day shown. The editor toasts "Event added for {EEE d MMMM}" when the event starts after today + 27 in the household zone. `WeekViewHost` uses `produceState`, so the shown week stays while the next one loads. The token lifetime stays 42 days (ruling 1).
32. **`@OptIn(ExperimentalSerializationApi::class)` (Task 11)** is allowed on `GoogleReadTest`'s descriptor walk only, if the compiler asks for it there.

Nothing in this plan needs a deprecated API. Flagged and avoided: `stabilityConfigurationFile` (ruling 20); `PackageManager.getPackageInfo(String, int)` (the version comes from `BuildConfig`, ruling 5).

## Global Constraints

- Package root `uk.co.siland.culvery`. Module rules unchanged (`build-logic`'s `ModuleBoundaries`): `:core:*` → `:core:*`; `:capability:*` → `:core:*`; `:provider:*` → `:core:*` and its own capability; `:app` → anything. `:core:plugin` may now depend on `:core:household` (core → core).
- `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Pinned versions as in `gradle/libs.versions.toml`. **One new dependency** (`androidx.core:core-splashscreen` 1.2.0, Task 4). Never change another version. `lifecycle-viewmodel-ktx` leaves (Task 2).
- **Deprecated APIs:** use none without asking. If an API shows a deprecation warning, **stop and ask**. Watch: OkHttp 4's Java-style accessors (use `response.code`, `request.header(…)`); `stabilityConfigurationFile`; `PackageManager.getPackageInfo(String, int)`; `androidx.compose.ui.platform.LocalLifecycleOwner`. `@OptIn` only for `ExperimentalCoroutinesApi` in tests, the existing `ExperimentalTextApi`/`flatMapLatest` uses, and ruling 32's one. **No new `flatMapLatest` or `debounce`** in main code.
- Release signing: the keystore lives **outside the repo** (`.gitignore` ignores `*.jks` and `*.keystore` anyway); its four values live in the user's `~/.gradle/gradle.properties`. **Never** put a password in code, a doc, a log, a commit, or a command line that Claude runs. The user types every password into `keytool`.
- **Privacy in logs (D8):** `Log.w`/`Log.e`/`Log.wtf`/`Log.println` carry only an exception's `::class.simpleName`, an HTTP code, a qualified id from `LogHygiene.ALLOWED`, or fixed text; never an email, a person's, calendar's or town's name, a PIN, coordinates or a zone, and never the throwable itself. `LogHygieneTest` (Task 3) guards it from then on.
- **Layout numbers are named, never inline**, in the module's dimens object (`SetupDimens`, `CalendarDimens`) or as a named `private val` beside the composable, as each file already does. Timing values are named constants.
- **Copy (spec), exactly:**
  - Signing: "Release signing isn't set up: add culvery.release.storeFile, storePassword, keyAlias and keyPassword to ~/.gradle/gradle.properties (see docs/setup/release.md)."
  - Kiosk: "Make Culvery the home app so it comes back after a restart."; "Choose home app"; "Change home app".
  - Connect: "Update Google Play services on this tablet, then try again."
  - Calendars: "Can't read this calendar — check it's still shared with this account"; "Hide this calendar"; "Choose another master calendar first".
  - Master gone: "{Service}: can't find the master calendar — choose a new one in Settings › Calendars. {n} changes waiting to sync were dropped." (singular: "1 change waiting to sync was dropped.")
  - People: "Move {name} up" / "Move {name} down" (spoken); toasts "{name} moved up" / "{name} moved down".
  - Calendar tab: "This week", "Next week", "In 2 weeks", "In 3 weeks"; the chip "This week"; the toast "Event added for {Tue 17 November}".
- **Storage:** `calendar.db` goes to v6 with a hand-written `MIGRATION_5_6` and a `MigrationTestHelper` test; commit the exported `6.json`. A capability never edits another module's schema.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell); plus `./gradlew -p build-logic :convention:test` in every task that changes `build-logic`. Never plain `test`, `check` or `build` (release unit tests can't run Compose tests, and from Task 1 a release task needs the signing properties). A task runs its module's `testDebugUnitTest` while working; every task ends with the full gate before its commit.
- Screenshots: baselines in `<module>/src/test/screenshots/`, recorded and verified on Windows; record only the images named with `./gradlew <module>:recordRoborazziDebug --tests "<pattern>"`; look at every new or changed image before committing (the step says what it must show); `@GraphicsMode(GraphicsMode.Mode.NATIVE)` only on classes that capture screenshots or measure text.
- Tests and threads (as 4b): Room runs on its own threads; wait for it in bounded real time (`withContext(Dispatchers.Default) { withTimeout(5_000) { … } }`); asynchronous UI outcomes use `compose.waitUntil(5_000) { … }`; no fixed sleeps. A test that changes the JVM's default zone restores it in `@After`.
- Fixtures use London, Leeds, Wellington or other public places, never the household's own.
- **Write every file as UTF-8** (an earlier implementer wrote ISO-8859-1 through Python; copy contains "—", "›", "·", "…", "‹"). Use the Write/Edit tools, or `encoding="utf-8"` in Python.
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on the current branch; never push. **Never** `git checkout -- .` or `git restore .`; to undo your own change, restore only the files you changed, after `git diff --name-only`.

## Review Focus

The six failures most likely to reach a household that the spec's own tests don't pin, each pinned by the tests or checks named in its owning task:

1. **R8 strips something a library reaches only by reflection or a generated companion,** so the release build's Google sign-in, calendar sync or weather fails while every debug test passes. Expected: the minified release reads every answer it read in debug.
   - Task 3 Step 10 runs the weather and the town search on the minified build and reads the log for `SerializationException`; Task 19 walkthrough items 3, 4 and 5 run sign-in, sync, a write and the weather on it.
2. **A process death behind Google's account chooser** (Culvery left pinning, then was killed) leaves the tablet unpinned. Expected: when Culvery is next in front it pins again; nothing remembers "exited" across a process death.
   - Task 9 `KioskLifecycleTest.aFreshStartPins`, `leavingAfterExitKioskAndComingBackPinsAgain`, `ShellViewModelTest.exitKioskIsRememberedUntilCulveryIsInFrontAgain`; Task 10 `GoogleConnectScreenTest.theChooserOpensOnlyAfterLeavingPinning`.
3. **Pruning deletes an event the user is looking at or a queued change's target** (an event moved out of the window on a phone while an edit for it waits). Expected: a row a queued change targets stays until the change is delivered or dropped.
   - Task 12 `CalendarStoreTest.pruningKeepsAnEventAQueuedChangeTargets`, `pruningDropsWhatEndsBeforeTheWindowOrStartsAfterWhatWasRead`.
4. **`MIGRATION_5_6` on an install with queued changes.** Expected: queued changes, mappings, visibility, the master and the events survive; only the cursors go; the next pass fully syncs each calendar.
   - Task 12 `CalendarMigrationTest.migrationFromV5ClearsTheCursorsAndKeepsQueuedChangesMappingsAndTheMaster` (it also checks `store.cursor(…)` is null after the upgrade, so the first pass reads each calendar in full).
5. **The Home role and lock-task after Exit kiosk.** Culvery is the home app, so every Home press brings it back. Expected: after Exit kiosk it stays in front unpinned, and pins again once it has left and come back (D3, §5.1); only **Change home app** (fresh PIN) lets the tablet go back to the normal launcher.
   - Task 9 `KioskLifecycleTest.exitKioskAsTheHomeAppStaysInFrontUnpinnedThroughAHomePress`, `withAnotherHomeAppExitKioskMovesToTheBack`; `ShellViewModelTest.changeHomeAppAsksForAFreshPinClosesSettingsAndSignsOut`, `chooseHomeAppUsesTheOpenSession`.
6. **The Calendar tab shows a week the tablet doesn't hold,** so a later week looks empty. Expected: the furthest day shown is inside the window every pass keeps; a later week goes back to this week at midnight and after 2 minutes untouched.
   - Task 14 `CalendarSyncTest.theFurthestWeekShownIsInsideTheWindow`, `WeekViewTest.aLaterWeekGoesBackToThisWeekAtMidnight`, `aLaterWeekGoesBackToThisWeekAfterTwoMinutesWithoutATouch`, `CardHostsMidnightRolloverTest.weekViewHostShowsTheWeekItStepsTo`.

The spec's own review focus (§10), each pinned:
- Release signs, minifies and works end to end; no secret reaches the repo or a log: Task 1 `ReleaseSigningTest` and Step 5; Task 3 Step 10; Task 19 items 1–5.
- Nothing in a release log names a person, calendar, account, town, coordinates or zone: Task 3 `LogHygieneTest`; Task 19 item 8.
- After a reboot the tablet is back in Culvery; Connect can't strand anyone and pins again on every outcome: Task 9 (`HOME` category, `HomeApp`, `KioskLifecycleTest`); Task 10 `GoogleConnectScreenTest.theChooserOpensOnlyAfterLeavingPinning`, `anAccountAlreadyGrantedNeverLeavesPinning`; Task 19 items 1 and 3.
- No nightly full resync, no growth, no missed deletions: Task 12 `CalendarSyncTest.thePassAfterLocalMidnightKeepsTheCursor`, `theTokenServesUntilTheWindowPassesWhatWasRead`, `CalendarStoreTest.pruning…`; Task 13 `GoogleReadTest.aCancelledSeriesRemovesEveryStoredInstance`.
- v6 migrates without losing mappings, the master or queued changes: Review Focus 4.
- Every icon is in the subset, filled forms included: Task 2 `IconFontTest`.

---

## File Structure

```
gradle/libs.versions.toml                         (Task 2: lifecycle-viewmodel-ktx out; Task 4: core-splashscreen in)
.gitignore                                        (Task 1: *.jks, *.keystore)
compose-stability.conf                            (create, Task 7)
build-logic/convention/build.gradle.kts           (Task 3: the log test's inputs)
build-logic/convention/src/main/kotlin/
  ReleaseSigning.kt                                        (create, Task 1)
  AndroidComposeConventionPlugin.kt                        (modify, Task 7)
build-logic/convention/src/test/kotlin/
  ReleaseSigningTest.kt (Task 1), LogHygieneTest.kt (Task 3)
app/build.gradle.kts                              (Tasks 1, 2, 3, 4)
app/proguard-rules.pro                            (create, Task 3)
app/src/main/AndroidManifest.xml                  (Tasks 4, 9)
app/src/main/res/values/themes.xml                (Task 4)
app/src/main/res/xml/device_admin.xml             (create, Task 9)
app/src/main/java/uk/co/siland/culvery/
  CulveryApp.kt (Tasks 3, 4), MainActivity.kt (Tasks 4, 5, 9, 10), SetupWiring.kt (Task 4),
  CulveryDeviceAdmin.kt, HomeApps.kt, KioskLifecycle.kt (create, Task 9)
  di/AppModule.kt (Tasks 3, 6, 9, 11)
  shell/ShellUiState.kt, ShellViewModel.kt (Tasks 3, 4, 5, 6, 9, 10), MinuteTicker.kt (deleted, Task 6),
  shell/ui/CulveryShell.kt, HomeScreen.kt, StatusBar.kt (Task 5)
app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt (Task 4)
core/ui/src/main/java/uk/co/siland/culvery/core/ui/Icons.kt (create), Theme.kt (Task 5); res/font/material_symbols_rounded.ttf (Task 2)
core/ui/src/test/java/uk/co/siland/culvery/core/ui/OpenTypeLigatures.kt, IconFontTest.kt (create, Task 2); ThemeTest.kt (Tasks 2, 5)
core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/
  FirstDraw.kt (Task 4), HouseholdClock.kt (Task 6), HomeApp.kt (Task 9), Runtime.kt (Tasks 6, 11),
  ShellNavigator.kt (Tasks 9, 10), FlowRetry.kt (Task 18)
core/access/…/LockoutStore.kt (Task 8), Permissions.kt (Task 10), DefaultAccessControl.kt (Task 18)
core/household/…/HouseholdRepository.kt (Task 17)
core/setup/…/pages/KioskPage.kt, steps/DoneStep.kt, SetupUi.kt (Task 9), SetupCopy.kt, SetupDimens.kt (Tasks 9, 17), PeopleUi.kt,
  PeopleEditor.kt, pages/PeoplePage.kt, steps/HouseholdStep.kt (Task 17), WizardRules.kt, steps/LocationStep.kt (Task 18)
capability/calendar/…/CalendarStore.kt, CalendarSync.kt, CalendarContract.kt, db/CalendarDatabase.kt, db/Migrations.kt,
  di/CalendarModule.kt (Tasks 12, 13, 15, 16), Stored.kt, SourceRefresher.kt, ui/ReviewCalendars.kt (Task 16),
  CalendarRepository.kt, PendingOverlay.kt (Tasks 7, 15), CalendarSyncLoop.kt (Task 4), CalendarConnections.kt (Task 10),
  CalendarUi.kt, CalendarEditor.kt, ui/WeekView.kt, ui/CardHosts.kt, ui/Pickers.kt, ui/CalendarType.kt (Task 14; CalendarEditor.kt also 15),
  ui/Now.kt, CalendarCapability.kt (Task 6)
capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/6.json   (generated, Task 12)
capability/weather/…/WeatherRepository.kt, WeatherCapability.kt, ui/ForecastCard.kt (Task 6), WeatherSyncLoop.kt (Task 4),
  WeatherWords.kt (Task 2)
provider/calendar-google/…/GoogleCalendarProvider.kt (Tasks 10, 11, 13), GoogleConnectFlow.kt (Task 10), GoogleHttp.kt (Tasks 3, 4),
  TokenSource.kt (Task 4), PlayServices.kt (create, Task 10), di/GoogleCalendarModule.kt (Tasks 4, 10, 11)
provider/weather-openmeteo/…/Http.kt, OpenMeteoForecast.kt (Task 18)
tools/new-release-key.sh, tools/new-release-key.ps1, tools/measure-release.sh (create, Task 1);
  tools/fonts/subset.py, icons.txt, README.md (create, Task 2)
docs/setup/release.md (create, Task 1; Tasks 3, 9 add to it), docs/setup/google-calendar.md (Tasks 1, 10)
docs/superpowers/specs/2026-10-02-culvery-4c-release-fixes-design.md (Appendix B, Tasks 1 and 19)
*/src/test/resources/robolectric.properties (Task 18)
README.md, docs/superpowers/plans/2026-09-23-plan1-followups.md (Task 19)
```

`…` stands for the module's package directory; every step spells out the full path.

---

### Task 1: Release signing, the version, `docs/setup/release.md` and the "before" numbers (§3.1, §3.2, §4.5; rulings 9, 21)

**Review:** sonnet.

**Files:**
- Create: `build-logic/convention/src/main/kotlin/ReleaseSigning.kt`
- Test: `build-logic/convention/src/test/kotlin/ReleaseSigningTest.kt` (create)
- Modify: `app/build.gradle.kts`, `.gitignore`
- Create: `tools/new-release-key.sh`, `tools/new-release-key.ps1`, `tools/measure-release.sh`, `docs/setup/release.md`
- Modify: `docs/setup/google-calendar.md` (§4's last paragraph)
- Modify (after the checkpoint, by the controller): `docs/superpowers/specs/2026-10-02-culvery-4c-release-fixes-design.md` (Appendix B, "Before" column)

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `object ReleaseSigning { STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD: String; KEYS: List<String>; MESSAGE: String; fun missing(values: Map<String, String?>): List<String> }` in `build-logic` (default package). `:app`'s build script sees it because it applies `build-logic`'s plugins; there is no signing plugin (plan review 15).
  - In `app/build.gradle.kts`: a `release` signing config when all four properties are set; a task `checkReleaseSigning`, which `preReleaseBuild` depends on.
  - `versionCode = 2`, `versionName = "1.0.0-beta1"` (Task 11's User-Agent reads `BuildConfig.VERSION_NAME`).
  - `tools/measure-release.sh [serial]`, used again in Task 19.

- [ ] **Step 1: Write the failing test**

Create `build-logic/convention/src/test/kotlin/ReleaseSigningTest.kt`:
```kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReleaseSigningTest {
    // Not a real password: the test only checks that a value is there.
    private val all = mapOf(
        ReleaseSigning.STORE_FILE to "C:/keys/culvery-release.jks",
        ReleaseSigning.STORE_PASSWORD to "set",
        ReleaseSigning.KEY_ALIAS to "culvery",
        ReleaseSigning.KEY_PASSWORD to "set",
    )

    @Test
    fun allFourSetIsComplete() {
        assertThat(ReleaseSigning.missing(all)).isEmpty()
    }

    @Test
    fun anAbsentOrBlankValueIsMissing() {
        val partial = all - ReleaseSigning.KEY_ALIAS + (ReleaseSigning.STORE_PASSWORD to " ")
        assertThat(ReleaseSigning.missing(partial)).containsExactly(ReleaseSigning.STORE_PASSWORD, ReleaseSigning.KEY_ALIAS).inOrder()
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew -p build-logic :convention:test --tests ReleaseSigningTest`
Expected: FAIL to compile with "Unresolved reference 'ReleaseSigning'".

- [ ] **Step 3: Write `ReleaseSigning`**

Create `build-logic/convention/src/main/kotlin/ReleaseSigning.kt`:
```kotlin
/** Release signing from the user's own `~/.gradle/gradle.properties` (4c design D4, §3.1). No value is ever logged. */
object ReleaseSigning {
    const val STORE_FILE = "culvery.release.storeFile"
    const val STORE_PASSWORD = "culvery.release.storePassword"
    const val KEY_ALIAS = "culvery.release.keyAlias"
    const val KEY_PASSWORD = "culvery.release.keyPassword"
    val KEYS = listOf(STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)

    const val MESSAGE = "Release signing isn't set up: add culvery.release.storeFile, storePassword, keyAlias and keyPassword " +
        "to ~/.gradle/gradle.properties (see docs/setup/release.md)."

    /** The properties with no value, in [KEYS] order; a blank value counts as none. */
    fun missing(values: Map<String, String?>): List<String> = KEYS.filter { values[it].isNullOrBlank() }
}
```
Run: `./gradlew -p build-logic :convention:test`
Expected: PASS (the two new tests and `ModuleBoundariesTest`).

- [ ] **Step 4: Sign release builds, inline in `:app`, and set the version**

`app/build.gradle.kts`: in `defaultConfig` replace `versionCode = 1` and `versionName = "0.1.0"` with
```kotlin
        versionCode = 2
        versionName = "1.0.0-beta1"
```
Above `android {` add:
```kotlin
// Release signing from the user's own ~/.gradle/gradle.properties (4c design D4); debug builds and the tests never need it.
val releaseSigning = ReleaseSigning.KEYS.associateWith { providers.gradleProperty(it) }
```
Inside `android { … }`, after `buildFeatures.buildConfig = true`:
```kotlin
    signingConfigs {
        // AGP's signing config takes plain values, so these are read while configuring, and only when all four are set.
        if (ReleaseSigning.missing(releaseSigning.mapValues { it.value.orNull }).isEmpty()) {
            create("release") {
                storeFile = file(releaseSigning.getValue(ReleaseSigning.STORE_FILE).get())
                storePassword = releaseSigning.getValue(ReleaseSigning.STORE_PASSWORD).get()
                keyAlias = releaseSigning.getValue(ReleaseSigning.KEY_ALIAS).get()
                keyPassword = releaseSigning.getValue(ReleaseSigning.KEY_PASSWORD).get()
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
```
After the `android { … }` block:
```kotlin
// A release task stops at once without the properties; it holds the providers, read only when it runs.
val checkReleaseSigning by tasks.registering {
    val properties = releaseSigning
    doLast {
        if (ReleaseSigning.missing(properties.mapValues { it.value.orNull }).isNotEmpty()) throw GradleException(ReleaseSigning.MESSAGE)
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseSigning) }
```
`.gitignore`: add two lines, `*.jks` and `*.keystore`, so a key copied into the repo by mistake is never committed.

- [ ] **Step 5: Check a release build without the properties fails with the message**

Check, without printing any value, that the properties aren't set yet:
```bash
grep -c "^culvery.release" ~/.gradle/gradle.properties 2>/dev/null || echo 0
```
Expected: `0`. (If not 0, the user has set them already: skip the rest of this step and say so in the report.)

Run: `./gradlew :app:assembleRelease`
Expected: `BUILD FAILED` at `:app:checkReleaseSigning` with exactly "Release signing isn't set up: add culvery.release.storeFile, storePassword, keyAlias and keyPassword to ~/.gradle/gradle.properties (see docs/setup/release.md)."

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (debug never needs them).

- [ ] **Step 6: Write the key wizards**

Java on Git Bash's terminal can't hide what is typed, so the Bash wizard refuses there unless `winpty` gives `keytool` a console; the PowerShell one is the default on Windows (plan review 14).

Create `tools/new-release-key.ps1` (UTF-8):
```powershell
# Makes Culvery's release signing key, one step at a time (4c design D4).
# You type every password into keytool itself: none is passed on a command line, printed, or written to the repo.
$ErrorActionPreference = 'Stop'

$keytool = if ($env:KEYTOOL) { $env:KEYTOOL } else { 'keytool' }
if (-not (Get-Command $keytool -ErrorAction SilentlyContinue)) {
    Write-Host "keytool isn't on PATH. Use the JDK 17 that builds Culvery, e.g.:"
    Write-Host '  $env:KEYTOOL = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"; .\tools\new-release-key.ps1'
    exit 1
}
$alias = 'culvery'
$defaultDir = Join-Path $HOME '.culvery'
$repo = (git rev-parse --show-toplevel 2>$null)

Write-Host 'Culvery release key'
Write-Host ''
Write-Host 'Step 1 of 4: where the key lives.'
Write-Host 'It must be outside the repo, and backed up somewhere safe: if it is lost, Culvery has to be uninstalled from the'
Write-Host 'tablet (losing its setup) and Google needs a new OAuth client.'
$dir = Read-Host "Folder [$defaultDir]"
if (-not $dir) { $dir = $defaultDir }
New-Item -ItemType Directory -Force $dir | Out-Null
$dir = (Resolve-Path $dir).Path
if ($repo -and $dir.Replace('\', '/').StartsWith(($repo.TrimEnd('/') + '/'), [StringComparison]::OrdinalIgnoreCase)) {
    Write-Host 'That folder is inside the repo. Choose one outside it.'
    exit 1
}
$store = Join-Path $dir 'culvery-release.jks'
if (Test-Path $store) {
    Write-Host "$store already exists; it is left as it is. Move it away first to make a new key."
    exit 1
}

Write-Host ''
Write-Host 'Step 2 of 4: keytool asks for a keystore password (twice), then a name and organisation (anything, e.g.'
Write-Host '"Culvery"), then asks you to confirm. Choose a long password and keep it with the backup.'
& $keytool -genkeypair -v -keystore $store -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 -validity 10000
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ''
Write-Host "Step 3 of 4: add these four lines to $HOME\.gradle\gradle.properties (your own Gradle file, never the repo's),"
Write-Host 'putting the password you just chose in place of <password> on both password lines (a PKCS12 key uses the'
Write-Host "keystore's password):"
Write-Host ''
Write-Host ("culvery.release.storeFile=" + $store.Replace('\', '/'))
Write-Host 'culvery.release.storePassword=<password>'
Write-Host "culvery.release.keyAlias=$alias"
Write-Host 'culvery.release.keyPassword=<password>'
Write-Host ''
Read-Host 'Press Enter once they are saved' | Out-Null

Write-Host ''
Write-Host 'Step 4 of 4: the key''s SHA-1, for the release OAuth client (docs/setup/google-calendar.md, section 4).'
Write-Host "Run this from the repo root and copy the SHA1 line under 'Variant: release':"
Write-Host ''
Write-Host '  .\gradlew.bat :app:signingReport'
Write-Host ''
Write-Host "Then back up $store and its password."
```

Create `tools/new-release-key.sh` (UTF-8, LF line endings), for macOS, Linux, or Git Bash with `winpty`:
```bash
#!/usr/bin/env bash
# Makes Culvery's release signing key, one step at a time (4c design D4).
# You type every password into keytool itself: none is passed on a command line, printed, or written to the repo.
# On Windows, use tools/new-release-key.ps1 in PowerShell instead.
set -euo pipefail

KEYTOOL=${KEYTOOL:-keytool}
if ! command -v "$KEYTOOL" >/dev/null; then
  echo "keytool isn't on PATH. Use the JDK 17 that builds Culvery (set KEYTOOL to its keytool)."
  exit 1
fi
RUN=("$KEYTOOL")
if [ -n "${MSYSTEM:-}" ]; then
  # Git Bash's terminal can't hide typing from Java: keytool would show the password as it is typed.
  if command -v winpty >/dev/null; then
    RUN=(winpty "$KEYTOOL")
  else
    echo "In Git Bash keytool would show your password as you type it. Run this instead, in PowerShell:"
    echo "  .\\tools\\new-release-key.ps1"
    exit 1
  fi
fi

ALIAS=culvery
DEFAULT_DIR="$HOME/.culvery"
REPO=$(git rev-parse --show-toplevel 2>/dev/null || echo "")

echo "Culvery release key"
echo
echo "Step 1 of 4: where the key lives."
echo "It must be outside the repo, and backed up somewhere safe: if it is lost, Culvery has to be uninstalled from the"
echo "tablet (losing its setup) and Google needs a new OAuth client."
read -r -p "Folder [$DEFAULT_DIR]: " DIR
DIR=${DIR:-$DEFAULT_DIR}
mkdir -p "$DIR"
DIR=$(cd "$DIR" && pwd -P)
if [ -n "$REPO" ]; then
  case "$DIR/" in
    "$(cd "$REPO" && pwd -P)/"*) echo "That folder is inside the repo. Choose one outside it."; exit 1 ;;
  esac
fi
STORE="$DIR/culvery-release.jks"
if [ -e "$STORE" ]; then
  echo "$STORE already exists; it is left as it is. Move it away first to make a new key."
  exit 1
fi

echo
echo "Step 2 of 4: keytool asks for a keystore password (twice), then a name and organisation (anything, e.g."
echo "\"Culvery\"), then asks you to confirm. Choose a long password and keep it with the backup."
"${RUN[@]}" -genkeypair -v -keystore "$STORE" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000

STORE_FOR_GRADLE=$STORE
if command -v cygpath >/dev/null; then STORE_FOR_GRADLE=$(cygpath -m "$STORE"); fi

echo
echo "Step 3 of 4: add these four lines to $HOME/.gradle/gradle.properties (your own Gradle file, never the repo's),"
echo "putting the password you just chose in place of <password> on both password lines (a PKCS12 key uses the"
echo "keystore's password):"
echo
echo "culvery.release.storeFile=$STORE_FOR_GRADLE"
echo "culvery.release.storePassword=<password>"
echo "culvery.release.keyAlias=$ALIAS"
echo "culvery.release.keyPassword=<password>"
echo
read -r -p "Press Enter once they are saved. "

echo
echo "Step 4 of 4: the key's SHA-1, for the release OAuth client (docs/setup/google-calendar.md, section 4)."
echo "Run this from the repo root and copy the SHA1 line under 'Variant: release':"
echo
echo "  ./gradlew :app:signingReport"
echo
echo "Then back up $STORE and its password."
```

- [ ] **Step 7: Write the measuring script**

Create `tools/measure-release.sh` (UTF-8, LF):
```bash
#!/usr/bin/env bash
# The 4c Appendix B numbers for the Culvery release installed on one emulator or device (4c design §4.5): the APK's
# size, five cold starts (am start -W TotalTime and skipped frames each), then gfxinfo's p50/p90 and Dalvik and native
# PSS after the last start. It only stops and starts Culvery. Takes about a minute.
# Usage: bash tools/measure-release.sh [serial]   (default emulator-5554)
set -euo pipefail

SERIAL=${1:-emulator-5554}
PKG=uk.co.siland.culvery
APK=app/build/outputs/apk/release/app-release.apk
SETTLE_SECONDS=10
ADB=${ADB:-adb}
command -v "$ADB" >/dev/null || ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb"
dev() { "$ADB" -s "$SERIAL" "$@"; }

echo "APK size: $(wc -c < "$APK") bytes"
for run in 1 2 3 4 5; do
  dev shell am force-stop "$PKG"
  dev logcat -c
  total=$(dev shell am start -W -n "$PKG/.MainActivity" | tr -d '\r' | sed -n 's/^TotalTime: //p')
  sleep "$SETTLE_SECONDS"
  skipped=$(dev logcat -d -s Choreographer:I | tr -d '\r' | sed -n 's/.*Skipped \([0-9]*\) frames.*/\1/p' | awk '{ s += $1 } END { print s + 0 }')
  echo "Run $run: TotalTime $total ms, skipped frames $skipped"
done
echo "gfxinfo (last cold start):"
dev shell dumpsys gfxinfo "$PKG" | tr -d '\r' | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile"
echo "PSS in KB (last cold start):"
dev shell dumpsys meminfo "$PKG" | tr -d '\r' | grep -E "^ *(Dalvik Heap|Native Heap) "
```

- [ ] **Step 8: Write `docs/setup/release.md`**

Create `docs/setup/release.md`:
````markdown
# Building and installing a release

A release build is signed with your own key, shrunk by R8, and logs only warnings and errors. It needs its own Google OAuth client, because Google matches the app by its signing key.

## 1. Make the key (once)

On Windows, from the repo root in PowerShell:

```powershell
.\tools\new-release-key.ps1
```

(elsewhere: `bash tools/new-release-key.sh`; in Git Bash it runs only with `winpty`, as keytool would otherwise show the password as you type). It asks where to keep the key (outside the repo; the default is `~/.culvery/`), then runs `keytool`, which asks for the password itself. It prints the four lines to add to **your own** `~/.gradle/gradle.properties` (never the repo's):

```properties
culvery.release.storeFile=C:/Users/you/.culvery/culvery-release.jks
culvery.release.storePassword=<password>
culvery.release.keyAlias=culvery
culvery.release.keyPassword=<password>
```

Use an absolute path with forward slashes. Back up the `.jks` file and its password together, somewhere other than this computer. **Losing the key** means Culvery can't be updated on the tablet: it has to be uninstalled (its setup is lost) and Google needs a new OAuth client for the new key. The repo ignores `*.jks` and `*.keystore`, so a key copied here by mistake isn't committed.

Without all four lines a release build stops at once with "Release signing isn't set up: …". Debug builds and `./gradlew testDebugUnitTest verifyRoborazziDebug` never need them.

## 2. Give Google the key

Run `./gradlew :app:signingReport` and copy the `SHA1:` line under `Variant: release`. Then make the release Android OAuth client: `docs/setup/google-calendar.md`, section 4, last paragraph.

## 3. Build and install

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

A release can't be installed over a debug build (their signatures differ): run `adb uninstall uk.co.siland.culvery` first, which removes the debug build's setup and data. Installing one release over the next keeps everything.
````

- [ ] **Step 9: Name the release client in the Google setup doc**

In `docs/setup/google-calendar.md`, replace the paragraph
"A release build is signed with a different key, so it needs a second Android client with that key's SHA-1. That comes with release signing in Plan 4."
with:
```markdown
A release build is signed with your release key (`docs/setup/release.md`), so it needs a second Android client: create another client of type **Android** in the same project, with the same package name and the release key's SHA-1, from `./gradlew :app:signingReport` under `Variant: release` (it shows once the release signing properties are set). Keep the debug client: both can sign in.
```

- [ ] **Step 10: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` and `./gradlew -p build-logic :convention:test`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 11: Commit**

```bash
git add build-logic app/build.gradle.kts .gitignore tools/new-release-key.sh tools/new-release-key.ps1 tools/measure-release.sh docs/setup/release.md docs/setup/google-calendar.md
git commit -m "Sign release builds with a local key from the user's Gradle properties, version 1.0.0-beta1"
```

- [ ] **Step 12: STOP — USER CHECKPOINT: the release key, then the "before" numbers**

The implementer stops here and reports. **The controller** asks the user to:
1. Run `.\tools\new-release-key.ps1` in their own PowerShell (ruling 21: the real key, which Task 19 uses too), add the four lines, back up the key, and say when done. The controller never sees, asks for or types a password.

Then the controller, on the running API 35 Google Play emulator `emulator-5554` (don't start or wipe another; ask before uninstalling):
2. `./gradlew :app:assembleRelease` → `BUILD SUCCESSFUL`. R8 is still off and the font still full: this is §4.5's "before" build.
3. Ask, then `adb -s emulator-5554 uninstall uk.co.siland.culvery` (it removes the debug build's data), then `adb -s emulator-5554 install app/build/outputs/apk/release/app-release.apk`.
4. Start it and finish a minimal setup with the user: Welcome › Start; Home location London; You: a test Admin (the user types the PIN); skip the rest; Open Culvery. Home shows the Connect card and London's weather. Every measurement, here and in Task 19, uses this household: London, one Admin, no calendar.
5. Run `bash tools/measure-release.sh emulator-5554` in the background (about a minute).
6. Fill the "Before" column of Appendix B in `docs/superpowers/specs/2026-10-02-culvery-4c-release-fixes-design.md`: the APK size in MB (one decimal); the five TotalTimes and their median; skipped frames per run; p50 / p90; Dalvik / native PSS in MB. Show the user the table, then:
```bash
git add docs/superpowers/specs/2026-10-02-culvery-4c-release-fixes-design.md
git commit -m "Record the release build's size and start-up before 4c's fixes"
```

---

### Task 2: The icon font — `Icons`, `IconFontTest`, the S3 glyphs, the subset (§3.5; ruling 22)

**Review:** sonnet.

**Before this task (controller):** `tools/fonts/subset.py` needs fontTools, and installing it downloads from PyPI. Ask the user to run `python -m pip install --user fonttools` in their own terminal (or confirm `python -c "import fontTools; print(fontTools.version)"` prints a version). The build never runs Python; only whoever re-subsets the font needs it.

**Files:**
- Create: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Icons.kt`
- Modify: `core/ui/build.gradle.kts` (the test's inputs)
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/OpenTypeLigatures.kt`, `IconFontTest.kt` (create); `ThemeTest.kt` (modify)
- Create: `tools/fonts/subset.py`, `tools/fonts/icons.txt`, `tools/fonts/README.md`
- Replace (generated): `core/ui/src/main/res/font/material_symbols_rounded.ttf`
- Modify (string literals → `Icons`): `app/src/main/java/uk/co/siland/culvery/shell/ui/NavRail.kt`, `StatusBar.kt`; `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`, `CalendarConnections.kt`, `CalendarUi.kt`, `ui/CalendarConnectHost.kt`, `ui/Components.kt`, `ui/ConnectCalendarCard.kt`, `ui/EventDetailSheet.kt`, `ui/EventEditorSheet.kt`, `ui/Pickers.kt`, `ui/ReviewCalendars.kt`, `ui/TodayCard.kt`, `ui/WeekView.kt`; `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherCapability.kt`, `WeatherWords.kt`; `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt`; `core/setup/src/main/java/uk/co/siland/culvery/core/setup/LocationPane.kt`, `PeopleUi.kt`; `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Shell.kt`; `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProvider.kt`; `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml` (`lifecycle-viewmodel-ktx` out)
- Screenshots: `capability/weather/src/test/screenshots/header_clear_night_dark.png`, `header_clear_night_light.png` (new, from `WeatherScreenshotTest`); `capability/calendar/src/test/screenshots/detail_untagged_dark.png`, `detail_untagged_light.png` (re-recorded)

**Interfaces:**
- Consumes: nothing new.
- Produces: `object Icons` in `uk.co.siland.culvery.core.ui` with one `const val` per glyph (46 names, below), including `Icons.ARROW_UPWARD` and `Icons.ARROW_DOWNWARD` for Task 17 and `Icons.BEDTIME` (or `Icons.NIGHTLIGHT`, ruling 22) and `Icons.MOBILE`. Every later task names glyphs through it. `:core:plugin`'s `TOAST_ICON_INFO` and `:provider:weather-openmeteo`'s descriptor icon stay literal: those modules don't depend on `:core:ui`; `Icons.INFO` and `Icons.PARTLY_CLOUDY_DAY` keep them in the subset.

- [ ] **Step 1: Write the font reader the test uses**

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/OpenTypeLigatures.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import java.io.File
import java.nio.ByteBuffer

/**
 * The ligatures a TrueType/OpenType font makes: [has] says whether typing a name gives one glyph, and [hasFilledForm]
 * whether that glyph has its single-substitution alternate (Material Symbols' FILL ≥ 0.99 variant, reached through
 * `rclt`). Reads the `cmap` (format 12, else 4), every GSUB ligature lookup (type 4) and every single substitution
 * (type 1), each also inside a type 7 extension.
 */
internal class OpenTypeLigatures(file: File) {
    private val bytes: ByteBuffer = ByteBuffer.wrap(file.readBytes())
    private val tables: Map<String, Int> = tableOffsets()
    private val glyphOf: (Int) -> Int = characterMap()
    private val ligatures: Map<List<Int>, Int> = HashMap()
    private val singles: Map<Int, Int> = HashMap()

    init {
        readSubstitutions()
    }

    fun has(name: String): Boolean = ligatures.containsKey(name.map { glyphOf(it.code) })

    fun hasFilledForm(name: String): Boolean = ligatures[name.map { glyphOf(it.code) }]?.let(singles::containsKey) == true

    private fun u16(at: Int): Int = bytes.getShort(at).toInt() and 0xFFFF

    private fun u32(at: Int): Long = bytes.getInt(at).toLong() and 0xFFFFFFFFL

    private fun tableOffsets(): Map<String, Int> = (0 until u16(4)).associate { i ->
        val record = 12 + 16 * i
        String(ByteArray(4) { bytes.get(record + it) }, Charsets.US_ASCII) to u32(record + 8).toInt()
    }

    private fun characterMap(): (Int) -> Int {
        val cmap = tables.getValue("cmap")
        val subtables = (0 until u16(cmap + 2)).map { i -> cmap + u32(cmap + 4 + 8 * i + 4).toInt() }
        subtables.firstOrNull { u16(it) == 12 }?.let { t -> return { c -> format12(t, c) } }
        val format4 = subtables.first { u16(it) == 4 }
        return { c -> format4(format4, c) }
    }

    private fun format12(table: Int, c: Int): Int {
        for (g in 0 until u32(table + 12).toInt()) {
            val at = table + 16 + 12 * g
            val start = u32(at)
            if (c in start..u32(at + 4)) return (u32(at + 8) + (c - start)).toInt()
        }
        return 0
    }

    private fun format4(table: Int, c: Int): Int {
        val segments = u16(table + 6) / 2
        val ends = table + 14
        val starts = ends + 2 * segments + 2
        val deltas = starts + 2 * segments
        val ranges = deltas + 2 * segments
        for (s in 0 until segments) {
            if (c > u16(ends + 2 * s)) continue
            val start = u16(starts + 2 * s)
            if (c < start) return 0
            val delta = u16(deltas + 2 * s)
            val rangeAt = ranges + 2 * s
            val range = u16(rangeAt)
            if (range == 0) return (c + delta) and 0xFFFF
            val glyph = u16(rangeAt + range + 2 * (c - start))
            return if (glyph == 0) 0 else (glyph + delta) and 0xFFFF
        }
        return 0
    }

    private fun readSubstitutions() {
        val gsub = tables.getValue("GSUB")
        val lookupList = gsub + u16(gsub + 8)
        for (l in 0 until u16(lookupList)) {
            val lookup = lookupList + u16(lookupList + 2 + 2 * l)
            val type = u16(lookup)
            for (s in 0 until u16(lookup + 4)) {
                var subtable = lookup + u16(lookup + 6 + 2 * s)
                var subtableType = type
                if (type == EXTENSION) {
                    subtableType = u16(subtable + 2)
                    subtable += u32(subtable + 4).toInt()
                }
                when (subtableType) {
                    SINGLE -> addSingles(subtable)
                    LIGATURE -> addLigatures(subtable)
                }
            }
        }
    }

    private fun addSingles(subtable: Int) {
        val from = coverage(subtable + u16(subtable + 2))
        val found = singles as HashMap
        when (u16(subtable)) {
            1 -> from.forEach { g -> found[g] = (g + bytes.getShort(subtable + 4).toInt()) and 0xFFFF }
            else -> from.forEachIndexed { i, g -> found[g] = u16(subtable + 6 + 2 * i) }
        }
    }

    private fun addLigatures(subtable: Int) {
        val firsts = coverage(subtable + u16(subtable + 2))
        val found = ligatures as HashMap
        for (i in 0 until u16(subtable + 4)) {
            val set = subtable + u16(subtable + 6 + 2 * i)
            for (j in 0 until u16(set)) {
                val ligature = set + u16(set + 2 + 2 * j)
                val components = u16(ligature + 2)
                found[listOf(firsts[i]) + (0 until components - 1).map { u16(ligature + 4 + 2 * it) }] = u16(ligature)
            }
        }
    }

    private fun coverage(at: Int): List<Int> = when (u16(at)) {
        1 -> (0 until u16(at + 2)).map { u16(at + 4 + 2 * it) }
        else -> (0 until u16(at + 2)).flatMap { r -> (u16(at + 4 + 6 * r)..u16(at + 6 + 6 * r)).toList() }
    }

    private companion object {
        const val SINGLE = 1
        const val LIGATURE = 4
        const val EXTENSION = 7
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/IconFontTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Test

/** Every glyph `Icons` names is a ligature in the committed font, and tools/fonts/icons.txt lists exactly them (4c §3.5). */
class IconFontTest {
    // Unit tests run in the module's directory.
    private val repo = File("../..")
    private val font = OpenTypeLigatures(File("src/main/res/font/material_symbols_rounded.ttf"))

    private val named: List<String> = Icons::class.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .map { it.get(null) as String }

    @Test
    fun theReaderFindsARealLigatureAndNotAWord() {
        assertThat(font.has("home")).isTrue()
        assertThat(font.has("culvery")).isFalse()
    }

    @Test
    fun everyNamedIconIsALigatureInTheFont() {
        assertWithMessage("Icons with no ligature in the font").that(named.filterNot(font::has)).isEmpty()
    }

    /** HhIcon(filled = true) — the rail's selected tab — draws the FILL ≥ 0.99 alternate; the subset must keep it. */
    @Test
    fun everyNamedIconKeepsItsFilledForm() {
        assertWithMessage("Icons whose filled form isn't in the font").that(named.filterNot { it in NO_FILLED_FORM || font.hasFilledForm(it) }).isEmpty()
    }

    @Test
    fun theSubsetListMatchesIcons() {
        val listed = File(repo, "tools/fonts/icons.txt").readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        assertThat(listed).containsNoDuplicates()
        assertThat(listed).containsExactlyElementsIn(named)
    }

    private fun mainSources(): Sequence<File> {
        val main = "${File.separator}src${File.separator}main${File.separator}"
        return repo.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") }
            .filter { it.isFile && it.extension == "kt" && main in it.path }
    }

    @Test
    fun noHhIconCallNamesAGlyphInAString() {
        val callers = mainSources().filter { "HhIcon(\"" in it.readText() }.map { it.relativeTo(repo).path }.toList()
        assertWithMessage("Use Icons.* instead of a string in").that(callers).isEmpty()
    }

    /**
     * S3 was `icon = "smartphone"`, a name the font doesn't have. Every icon still named by a string (in modules without
     * `:core:ui`, such as `:core:plugin`'s toast icon and Open-Meteo's descriptor) must be one `Icons` names.
     */
    @Test
    fun everyIconNamedInAStringIsInIcons() {
        val unknown = mainSources().flatMap { file ->
            ICON_LITERAL.findAll(file.readText()).map { it.groupValues[1] }.filterNot { it in named }.map { "${file.relativeTo(repo).path}: $it" }
        }.toList()
        assertWithMessage("Icon names not in Icons").that(unknown).isEmpty()
    }

    private companion object {
        /** `icon = "…"`, `icon: String = "…"`, `TOAST_ICON_INFO = "…"`, and the like. */
        val ICON_LITERAL = Regex("""(?i)icon\w*(?:\s*:\s*String\??)?\s*[=:]\s*"([a-z0-9_]+)"""")

        /** Glyphs whose full font has no filled alternate either (found in Step 9); empty unless that step says otherwise. */
        val NO_FILLED_FORM: Set<String> = emptySet()
    }
}
```

The test reads files outside `:core:ui`, so tell Gradle, or it would skip the test as up to date after they change. In `core/ui/build.gradle.kts` add at the top `import org.gradle.api.tasks.PathSensitivity` and at the end:
```kotlin
// IconFontTest reads tools/fonts/icons.txt and every module's main sources.
tasks.withType<Test>().configureEach {
    inputs.files(fileTree(rootDir) { include("tools/fonts/icons.txt", "*/src/main/**/*.kt", "*/*/src/main/**/*.kt") })
        .withPropertyName("iconSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
```

- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*IconFontTest*"`
Expected: FAIL to compile with "Unresolved reference 'Icons'".

- [ ] **Step 4: Name today's glyphs, the two broken ones included**

Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Icons.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

/**
 * Every Material Symbols glyph the app draws, by ligature name (4c design §3.5). The bundled font holds only these: to
 * add one, add it here and to tools/fonts/icons.txt, then run tools/fonts/subset.py (see tools/fonts/README.md).
 */
object Icons {
    const val ACCOUNT_CIRCLE = "account_circle"
    const val ADD = "add"
    const val ARROW_DOWNWARD = "arrow_downward"
    const val ARROW_UPWARD = "arrow_upward"
    const val BACKSPACE = "backspace"
    const val CALENDAR_ADD_ON = "calendar_add_on"
    const val CALENDAR_MONTH = "calendar_month"
    const val CHECK = "check"
    const val CHEVRON_LEFT = "chevron_left"
    const val CHEVRON_RIGHT = "chevron_right"
    const val CLEAR_NIGHT = "clear_night"
    const val CLOSE = "close"
    const val CLOUD = "cloud"
    const val CLOUD_OFF = "cloud_off"
    const val CLOUD_UPLOAD = "cloud_upload"
    const val DARK_MODE = "dark_mode"
    const val DATE_RANGE = "date_range"
    const val DELETE = "delete"
    const val DELETE_FOREVER = "delete_forever"
    const val EDIT = "edit"
    const val EDIT_NOTE = "edit_note"
    const val EVENT = "event"
    const val EVENT_AVAILABLE = "event_available"
    const val EVENT_REPEAT = "event_repeat"
    const val EXPAND_LESS = "expand_less"
    const val EXPAND_MORE = "expand_more"
    const val FOGGY = "foggy"
    const val HOME = "home"
    const val INFO = "info"
    const val LIGHT_MODE = "light_mode"
    const val LOCK = "lock"
    const val PARTLY_CLOUDY_DAY = "partly_cloudy_day"
    const val PARTLY_CLOUDY_NIGHT = "partly_cloudy_night"
    const val PERSON = "person"
    const val RAINY = "rainy"
    const val RAINY_HEAVY = "rainy_heavy"
    const val RAINY_LIGHT = "rainy_light"
    const val REFRESH = "refresh"
    const val REPEAT = "repeat"
    const val SCHEDULE = "schedule"
    const val SETTINGS = "settings"
    const val SMARTPHONE = "smartphone"
    const val SUNNY = "sunny"
    const val SYNC_PROBLEM = "sync_problem"
    const val THUNDERSTORM = "thunderstorm"
    const val WEATHER_SNOWY = "weather_snowy"
}
```

Create `tools/fonts/icons.txt` (UTF-8, LF):
```text
# The Material Symbols glyphs Culvery draws: one ligature name a line, the same names as core/ui's Icons.kt.
# IconFontTest fails when the two disagree. Run tools/fonts/subset.py after changing this list.
account_circle
add
arrow_downward
arrow_upward
backspace
calendar_add_on
calendar_month
check
chevron_left
chevron_right
clear_night
close
cloud
cloud_off
cloud_upload
dark_mode
date_range
delete
delete_forever
edit
edit_note
event
event_available
event_repeat
expand_less
expand_more
foggy
home
info
light_mode
lock
partly_cloudy_day
partly_cloudy_night
person
rainy
rainy_heavy
rainy_light
refresh
repeat
schedule
settings
smartphone
sunny
sync_problem
thunderstorm
weather_snowy
```

- [ ] **Step 5: Run it to see S3**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*IconFontTest*"`
Expected: `everyNamedIconIsALigatureInTheFont` FAILS listing exactly `[clear_night, smartphone]` (bug S3); `everyNamedIconKeepsItsFilledForm` FAILS listing at least those two (no ligature, so no filled form); `noHhIconCallNamesAGlyphInAString` FAILS listing the files of Step 8; `theReaderFindsARealLigatureAndNotAWord`, `theSubsetListMatchesIcons` and `everyIconNamedInAStringIsInIcons` PASS. If the ligature test lists any *other* name, the reader is wrong, not the font (every other glyph draws in today's screenshots): stop and report.

- [ ] **Step 6: Look at the candidates for clear night**

Create a throwaway `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/GlyphChoiceTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.SunAmber

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlyphChoiceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun clearNightCandidates() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Row(Modifier.testTag("shot").background(Culvery.colors.bg).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    listOf("bedtime", "nightlight", "dark_mode", "mobile").forEach { HhIcon(it, size = WeatherDimens.headerIcon, tint = SunAmber) }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("build/glyph-candidates.png")
    }
}
```
Run: `./gradlew :capability:weather:recordRoborazziDebug --tests "*GlyphChoiceTest*"` and look at `capability/weather/build/glyph-candidates.png`: four glyphs left to right — `bedtime`, `nightlight`, `dark_mode` (the status bar's night icon, for reference), `mobile`. Choose `bedtime` unless `nightlight` reads more clearly as a clear night beside the status bar's moon (ruling 22); `mobile` must read as a phone. Note the choice for the report, then delete the file:
```bash
rm capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/GlyphChoiceTest.kt
```
The rest of this task writes `BEDTIME`/`"bedtime"`; if you chose `nightlight`, write `NIGHTLIGHT`/`"nightlight"` in its place everywhere below.

- [ ] **Step 7: Swap the two glyphs (S3)**

In `Icons.kt`: delete `CLEAR_NIGHT` and `SMARTPHONE`; add, in alphabetical place, `const val BEDTIME = "bedtime"` (after `BACKSPACE`) and `const val MOBILE = "mobile"` (after `LOCK`).
In `tools/fonts/icons.txt`: replace `clear_night` with `bedtime` and `smartphone` with `mobile`, each moved to its alphabetical place.

- [ ] **Step 8: Name every glyph through `Icons`**

Replace each string literal with the constant, adding `import uk.co.siland.culvery.core.ui.Icons` where the file lacks it:
- `app/.../shell/ui/NavRail.kt`: `TabItem(HOME_TAB_ID, "Home", "home")` → `Icons.HOME`; `HhIcon("settings", …)` → `Icons.SETTINGS`.
- `app/.../shell/ui/StatusBar.kt`: `"account_circle"` → `Icons.ACCOUNT_CIRCLE`; `if (dark) "dark_mode" else "light_mode"` → `if (dark) Icons.DARK_MODE else Icons.LIGHT_MODE`.
- `capability/calendar/.../CalendarCapability.kt`: `override val icon = "calendar_month"` → `Icons.CALENDAR_MONTH`.
- `capability/calendar/.../CalendarConnections.kt`: `const val DEFAULT_ICON = "calendar_month"` → `const val DEFAULT_ICON = Icons.CALENDAR_MONTH`.
- `capability/calendar/.../CalendarUi.kt` (`enum class Badge`): `"cloud_upload"` → `Icons.CLOUD_UPLOAD`, `"lock"` → `Icons.LOCK`, `"repeat"` → `Icons.REPEAT`.
- `capability/calendar/.../ui/CalendarConnectHost.kt`: `HhIcon("calendar_month", …)` → `Icons.CALENDAR_MONTH`.
- `capability/calendar/.../ui/Components.kt`: `HhIcon("delete", …)` → `Icons.DELETE`; `AddButton(…, icon: String? = "add", …)` → `icon: String? = Icons.ADD`.
- `capability/calendar/.../ui/ConnectCalendarCard.kt`: `"calendar_add_on"` → `Icons.CALENDAR_ADD_ON`; `"event_available"` → `Icons.EVENT_AVAILABLE`.
- `capability/calendar/.../ui/EventDetailSheet.kt`: `icon = "lock"` → `Icons.LOCK`; `icon = "event_repeat"` → `Icons.EVENT_REPEAT`; `icon = "smartphone"` → `Icons.MOBILE` (S3); `HhIcon("delete", …)` → `Icons.DELETE`; `confirmIcon = "delete_forever"` → `Icons.DELETE_FOREVER`; `PrimaryButton("Edit", "edit", …)` → `PrimaryButton("Edit", Icons.EDIT, …)`; `HhIcon("cloud_upload", …)` → `Icons.CLOUD_UPLOAD`; `InfoRow("schedule", …)` → `Icons.SCHEDULE`, `InfoRow("person", …)` → `Icons.PERSON`, `InfoRow("edit_note", …)` → `Icons.EDIT_NOTE`, `InfoRow("calendar_month", …)` → `Icons.CALENDAR_MONTH`, `InfoRow("repeat", …)` → `Icons.REPEAT`.
- `capability/calendar/.../ui/EventEditorSheet.kt`: `HhIcon("check", …)` → `Icons.CHECK`; `HhIcon("calendar_month", …)` → `Icons.CALENDAR_MONTH`; `HhIcon("schedule", …)` → `Icons.SCHEDULE`; `HhIcon("date_range", …)` → `Icons.DATE_RANGE`; `HhIcon("cloud_off", …)` → `Icons.CLOUD_OFF`; `icon = if (failed) "refresh" else "check"` → `if (failed) Icons.REFRESH else Icons.CHECK`.
- `capability/calendar/.../ui/Pickers.kt`: `RoundButton("chevron_left", …)` → `Icons.CHEVRON_LEFT`; `RoundButton("chevron_right", …)` → `Icons.CHEVRON_RIGHT`; `StepButton("expand_less", …)` → `Icons.EXPAND_LESS`; `StepButton("expand_more", …)` → `Icons.EXPAND_MORE`.
- `capability/calendar/.../ui/ReviewCalendars.kt`: `HhIcon("check", …)` → `Icons.CHECK`.
- `capability/calendar/.../ui/TodayCard.kt`: `HhIcon("add", …)` → `Icons.ADD`.
- `capability/calendar/.../ui/WeekView.kt`: `HhIcon("add", …)` → `Icons.ADD`; `HhIcon("sync_problem", …)` → `Icons.SYNC_PROBLEM`.
- `capability/weather/.../WeatherCapability.kt`: `override val icon = "partly_cloudy_day"` → `Icons.PARTLY_CLOUDY_DAY`.
- `capability/weather/.../WeatherWords.kt`: `weatherIcon` becomes
```kotlin
/** Material Symbols names (§4.2), each in the subset font (IconFontTest). */
internal fun weatherIcon(condition: Condition, night: Boolean): String = when (condition) {
    Condition.CLEAR -> if (night) Icons.BEDTIME else Icons.SUNNY
    Condition.PARTLY_CLOUDY -> if (night) Icons.PARTLY_CLOUDY_NIGHT else Icons.PARTLY_CLOUDY_DAY
    Condition.CLOUDY -> Icons.CLOUD
    Condition.FOG -> Icons.FOGGY
    Condition.DRIZZLE -> Icons.RAINY_LIGHT
    Condition.RAIN -> Icons.RAINY
    Condition.SHOWERS -> Icons.RAINY_HEAVY
    Condition.SNOW -> Icons.WEATHER_SNOWY
    Condition.THUNDER -> Icons.THUNDERSTORM
}
```
- `core/access/.../ui/PinPad.kt`: `HhIcon("lock", …)` → `Icons.LOCK`; `HhIcon("backspace", …)` → `Icons.BACKSPACE`.
- `core/setup/.../LocationPane.kt`: `HhIcon("check", …)` → `Icons.CHECK`. `core/setup/.../PeopleUi.kt`: `HhIcon("chevron_right", …)` → `Icons.CHEVRON_RIGHT`.
- `core/ui/.../Shell.kt`: `HhIcon("close", …)` → `Icons.CLOSE` (same package: no import).
- `provider/calendar-google/.../GoogleCalendarProvider.kt`: the descriptor's `"calendar_month"` → `Icons.CALENDAR_MONTH`. `provider/calendar-fake/.../FakeCalendarProvider.kt`: the descriptor's `"event"` → `Icons.EVENT`.

If `WeatherWordsTest` asserts `"clear_night"`, change that expectation to `Icons.BEDTIME`; change nothing else in tests.

Check:
```bash
git grep -n 'HhIcon("' -- '*/src/main/*'
```
Expected: no output.

- [ ] **Step 9: Run the test on the full font**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*IconFontTest*"`
Expected: PASS (6 tests): `bedtime` and `mobile` are ligatures in today's full font, and every named glyph has its filled alternate there. If either name isn't a ligature, stop and report which. If `everyNamedIconKeepsItsFilledForm` still lists a name, the full font itself has no filled form for it: put exactly those names in `NO_FILLED_FORM` and say so in the report.

- [ ] **Step 10: Write the subsetter**

Create `tools/fonts/subset.py` (UTF-8):
```python
#!/usr/bin/env python3
"""Subsets Material Symbols Rounded to the glyphs Culvery names in tools/fonts/icons.txt (4c design §3.5).

Keeps all four axes (FILL, GRAD, opsz, wght), the ligatures and the filled alternates, and writes the font the app bundles:
core/ui/src/main/res/font/material_symbols_rounded.ttf. Needs fontTools (python -m pip install --user fonttools).

Usage: python tools/fonts/subset.py --source <the full Material Symbols Rounded variable .ttf>
"""
import argparse
import pathlib
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
ICONS = ROOT / "tools" / "fonts" / "icons.txt"
OUT = ROOT / "core" / "ui" / "src" / "main" / "res" / "font" / "material_symbols_rounded.ttf"
SINGLE = 1
LIGATURE = 4
EXTENSION = 7


def substitutions(font):
    """Each ligature as (its component glyph names) -> the glyph it makes; and each single substitution, glyph -> glyph.

    The single substitutions include rclt's filled alternates, which the font's FeatureVariations turn on at FILL >= 0.99.
    """
    ligatures, singles = {}, {}
    for lookup in font["GSUB"].table.LookupList.Lookup:
        for table in lookup.SubTable:
            if lookup.LookupType == EXTENSION:
                table = table.ExtSubTable
            if table.LookupType == SINGLE:
                singles.update(table.mapping)
            elif table.LookupType == LIGATURE:
                for first, ligs in table.ligatures.items():
                    for lig in ligs:
                        ligatures[tuple([first] + list(lig.Component))] = lig.LigGlyph
    return ligatures, singles


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--source", required=True, help="the full Material Symbols Rounded variable font")
    args = parser.parse_args()

    names = [line.strip() for line in ICONS.read_text(encoding="utf-8").splitlines()]
    names = [name for name in names if name and not name.startswith("#")]
    font = TTFont(args.source)
    cmap = font.getBestCmap()
    found, singles = substitutions(font)

    glyphs, missing = set(), []
    for name in names:
        sequence = tuple(cmap.get(ord(c)) for c in name)
        if None in sequence or sequence not in found:
            missing.append(name)
            continue
        glyphs.update(sequence)
        glyphs.add(found[sequence])
    if missing:
        sys.exit("No ligature in the source font for: " + ", ".join(missing))
    # HhIcon(filled = true) draws each glyph's filled alternate: keep those too.
    glyphs.update(singles[g] for g in list(glyphs) if g in singles)

    options = subset.Options()
    # Without this the subsetter follows every ligature from the kept letters and keeps almost the whole font.
    options.layout_closure = False
    # Every feature, rclt included, and with them the FeatureVariations that switch to the filled glyphs.
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(glyphs=sorted(glyphs), unicodes=sorted({ord(c) for name in names for c in name}))
    subsetter.subset(font)
    font.save(OUT)
    print(f"{len(names)} icons, {OUT.stat().st_size // 1024} KB: {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
```

Create `tools/fonts/README.md`:
````markdown
# The icon font

Culvery draws its icons with Material Symbols Rounded (Apache 2.0, `core/ui/licenses/Apache-MaterialSymbols.txt`) by ligature: `HhIcon(Icons.HOME)` types "home" in the font. The bundled `core/ui/src/main/res/font/material_symbols_rounded.ttf` is a subset holding only the glyphs in `icons.txt` and their filled alternates (what `HhIcon(filled = true)` draws), with all four axes (FILL, GRAD, opsz, wght): about 180 KB instead of 15 MB. The build never runs Python; the subset is committed.

## Adding an icon

1. Find its ligature name at <https://fonts.google.com/icons> (Material Symbols, Rounded).
2. Add a `const val` to `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Icons.kt` and the same name to `icons.txt`.
3. Get the full font. The one Culvery shipped before 4c is in the repo's history:
   ```bash
   git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$TMP/material-symbols-full.ttf"
   ```
   A glyph newer than that font needs Google's current `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` from <https://github.com/google/material-design-icons/tree/master/variablefont>.
4. With fontTools installed (`python -m pip install --user fonttools`), from the repo root:
   ```bash
   python tools/fonts/subset.py --source "$TMP/material-symbols-full.ttf"
   ```
   It stops and names any icon the source font has no ligature for.
5. `./gradlew :core:ui:testDebugUnitTest --tests "*IconFontTest*"`: every name in `Icons` must be a ligature in the committed font with its filled form, and `icons.txt` must match `Icons`.
````

- [ ] **Step 11: Subset the font**

```bash
git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$TMP/material-symbols-full.ttf"
python tools/fonts/subset.py --source "$TMP/material-symbols-full.ttf"
ls -l core/ui/src/main/res/font/material_symbols_rounded.ttf
```
Expected: "46 icons, about 180 KB: core/ui/src/main/res/font/material_symbols_rounded.ttf" (within 150–250 KB). If it reports missing names, stop and report them.

- [ ] **Step 12: Relax `ThemeTest` and guard the subset**

In `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ThemeTest.kt`, replace `assertThat(symbols).isGreaterThan(1_000_000)` with:
```kotlin
        // The subset of the glyphs Icons names (4c §3.5): about 180 KB; the full font is 15 MB.
        assertThat(symbols).isGreaterThan(50_000)
        assertThat(symbols).isLessThan(1_000_000)
```

- [ ] **Step 13: Run the font tests on the subset**

Run: `./gradlew :core:ui:testDebugUnitTest`
Expected: PASS (`IconFontTest` against the subset; `ThemeTest`).

- [ ] **Step 14: Check the screenshots: only S3's change**

Run: `./gradlew verifyRoborazziDebug`
Expected: only `detail_untagged_dark` and `detail_untagged_light` (calendar) differ; in particular every `home_*` image (`:app`), whose rail draws the selected tab's icon filled, is unchanged: the "Added from a phone" note's glyph used to be the text "smartphone". (The existing `header_night_*` fixture is partly cloudy, whose glyph always existed, so it doesn't change; Step 15 adds the clear-night images.) Any other difference means the subset draws differently: open `<module>/build/outputs/roborazzi/*_compare.png`, stop and report. Then record those two:
```bash
./gradlew :capability:calendar:recordRoborazziDebug --tests "*DetailScreenshotTest.untagged*"
```
Look at both: a phone glyph in accent colour at the left of the "Added from a phone" note, not the word "smartphone"; the rest of the sheet as before.

- [ ] **Step 15: Pin the clear-night header with its own screenshot**

The night header fixture is partly cloudy, so S3's glyph needs its own image. In `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/WeatherScreenshotTest.kt`, after `private val night = day.copy(night = true)` add:
```kotlin
    private val clearNight = night.copy(condition = Condition.CLEAR)
```
and after the `headerNightLight` test add:
```kotlin
    @Test fun headerClearNightDark() = snap("header_clear_night_dark", true) { WeatherHeaderItem(clearNight) }
    @Test fun headerClearNightLight() = snap("header_clear_night_light", false) { WeatherHeaderItem(clearNight) }
```
Run: `./gradlew :capability:weather:recordRoborazziDebug --tests "*WeatherScreenshotTest.headerClearNight*"`
Look at both: the `bedtime` moon in sun amber, then "17°" and "High 19° · Low 11°"; no text where the icon goes.

- [ ] **Step 16: Drop `lifecycle-viewmodel-ktx`**

In `app/build.gradle.kts` delete the line `implementation(libs.androidx.lifecycle.viewmodel.ktx)`. In `gradle/libs.versions.toml` delete the line `androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }`.
Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (`ViewModel` and `viewModelScope` come with `lifecycle-viewmodel` through activity-compose and Hilt). If `viewModelScope` is unresolved, stop and report.

- [ ] **Step 17: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 18: Commit**

```bash
git add core/ui tools/fonts app capability provider gradle/libs.versions.toml
git commit -m "Subset the icon font to the glyphs the app names, and draw the clear-night and added-from-a-phone icons"
```

---

### Task 3: R8, resource shrinking, release logging and `LogHygieneTest` (§3.3, §3.4, D8; ruling 18)

**Review:** opus (R8 and log privacy).

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Modify: `build-logic/convention/build.gradle.kts` (the test's inputs)
- Test: `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` (create)
- Modify (logging): `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`, `di/AppModule.kt`, `shell/ShellViewModel.kt`; in `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/`: `CalendarEditor.kt`, `CalendarSetup.kt`, `CalendarStore.kt`, `CalendarSync.kt`, `CalendarSyncLoop.kt`, `SourceRefresher.kt`, `Writes.kt`, `ui/CalendarConnectHost.kt`, `ui/EventDetailHost.kt`, `ui/EventEditorHost.kt`; in `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/`: `GoogleHttp.kt`, `GoogleCalendarProvider.kt`, `GoogleConnectFlow.kt`
- Modify (tests): `app/src/test/java/uk/co/siland/culvery/ApplicationScopeTest.kt`; `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoopTest.kt`, `CalendarSyncTest.kt`; `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleApiTest.kt`
- Modify: `docs/setup/release.md` (a section on reading a release crash)

**Interfaces:**
- Consumes: Task 1's signing (the minified build is built and installed in Step 12).
- Produces:
  - `LogHygiene.problems(source: String): List<String>` and `LogHygiene.ALLOWED` (in `build-logic`'s tests). From here on every `Log.w`/`Log.e`/`Log.wtf`/`Log.println` in shipped code passes a tag and one string-literal message (no throwable) whose templates are `${…::class.simpleName}`, `${….code}` or a qualified id in `ALLOWED`; later tasks write theirs that way.
  - `@JvmInline internal value class GoogleCall(val label: String)` (`GoogleHttp.kt`): what `decode`, `readOrUnreachable`, `refusal` and `oneOffToChange` log, in place of their `what: String`.

- [ ] **Step 1: Write the failing logging test**

Create `build-logic/convention/src/test/kotlin/LogHygieneTest.kt`:
```kotlin
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * D8: what a release build logs. Every Log.w, Log.e, Log.wtf and Log.println in shipped code passes a tag and one
 * message, never a throwable, and the message interpolates only a type, an HTTP code, or a qualified id in
 * [LogHygiene.ALLOWED].
 */
class LogHygieneTest {
    // build-logic/convention's tests run in that directory.
    private val repo = File("../..").canonicalFile

    @Test
    fun shippedWarningsAndErrorsCarryNoThrowableAndNoPersonalData() {
        val problems = shippedSources().flatMap { file ->
            LogHygiene.problems(file.readText()).map { "${file.relativeTo(repo)}: $it" }
        }
        assertWithMessage(problems.joinToString("\n")).that(problems).isEmpty()
    }

    @Test
    fun aThrowableArgumentIsCaught() {
        assertThat(LogHygiene.problems("""Log.w(TAG, "Couldn't save", e)""")).hasSize(1)
        assertThat(LogHygiene.problems("""Log.wtf(TAG, "Couldn't save", e)""")).hasSize(1)
    }

    @Test
    fun anInterpolatedNameOrABareLocalIsCaught() {
        assertThat(LogHygiene.problems("""Log.e(TAG, "Couldn't add ${'$'}{person.name}")""")).hasSize(1)
        assertThat(LogHygiene.problems("""Log.e(TAG, "Couldn't read ${'$'}id")""")).hasSize(1)
        assertThat(LogHygiene.problems("""Log.println(Log.WARN, TAG, "Couldn't read ${'$'}email")""")).hasSize(1)
    }

    @Test
    fun aMessageThatIsNotAStringIsCaught() {
        assertThat(LogHygiene.problems("""Log.w(TAG, message)""")).hasSize(1)
    }

    @Test
    fun aTypeACodeAndAnAllowedIdPassAndDebugLinesAreNotChecked() {
        val source = """
            Log.w(TAG, "${'$'}{conn.id}: answered ${'$'}{answer.code} (${'$'}{e::class.simpleName})")
            Log.e(TAG, "first part " +
                "second (${'$'}{it::class.simpleName})")
            Log.println(Log.WARN, TAG, "${'$'}{conn.id}: fixed words")
            Log.d(TAG, "stripped from release: ${'$'}{person.name}", e)
        """.trimIndent()
        assertThat(LogHygiene.problems(source)).isEmpty()
    }

    private fun shippedSources(): List<File> = repo.walkTopDown()
        .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") }
        .filter { it.isFile && it.extension == "kt" }
        .filter { f -> SHIPPED.any { "${File.separator}src${File.separator}$it${File.separator}" in f.path } }
        .toList()

    private companion object {
        /** Source sets that reach a release build. */
        val SHIPPED = listOf("main", "release")
    }
}

/** Finds the Log.w, Log.e, Log.wtf and Log.println calls in Kotlin source and says what is wrong with each. */
internal object LogHygiene {
    /**
     * Qualified ids and fixed words a message may interpolate (plan review 10: never a bare local, whose meaning the
     * line can't show): never a name, email, place, PIN, coordinates or zone.
     */
    val ALLOWED = setOf(
        "cap.id", "conn.id", "connection.id", "connection.providerId", "change.connectionId", "change.kind", "it.descriptor.id",
        "step.id", "stored.connection.id", "this.id", "this.kind", "kind.name", "this.failedDrains", "this.reason",
        "answer.reason", "what.label", "request.method", "pathTemplate(request.url)", "grant.scopes.size", "CALENDAR_SCOPES.size",
    )

    private val CALL = Regex("""\bLog\.(w|e|wtf|println)\(""")
    private val TEMPLATE = Regex("""\$\{([^}]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)""")

    fun problems(source: String): List<String> = CALL.findAll(source).mapNotNull { match ->
        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
        val args = arguments(source, match.range.last + 1)
        // Log.println(priority, tag, message): the same rule after its priority.
        problem(if (match.groupValues[1] == "println") args.drop(1) else args)?.let { "line $line: $it" }
    }.toList()

    private fun problem(args: List<String>): String? {
        if (args.size != 2) return "passes ${args.size} arguments after any priority: a tag and one message only, never a throwable"
        val message = args[1]
        val literals = literals(message)
        if (literals.isEmpty() || message.without(literals).any { it != '+' && !it.isWhitespace() }) {
            return "the message must be string literals"
        }
        val bad = literals
            .flatMap { r -> TEMPLATE.findAll(message.substring(r)).map { it.groupValues[1].ifEmpty { it.groupValues[2] } } }
            .filterNot(::allowed)
        return if (bad.isEmpty()) null else "interpolates ${bad.joinToString()}: only ::class.simpleName, a .code or a qualified id in LogHygiene.ALLOWED"
    }

    private fun allowed(expression: String): Boolean = expression.trim().let {
        it.endsWith("::class.simpleName") || it.endsWith(".code") || it in ALLOWED
    }

    /** The top-level arguments of the call whose "(" ends just before [from]. */
    private fun arguments(source: String, from: Int): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = from
        while (i < source.length) {
            val c = source[i]
            if (c == '"') {
                val end = stringEnd(source, i)
                current.append(source, i, end)
                i = end
                continue
            }
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth == 0) {
                    args += current.toString().trim()
                    return args
                } else {
                    depth--
                }
                ',' -> if (depth == 0) {
                    args += current.toString().trim()
                    current.clear()
                    i++
                    continue
                }
            }
            current.append(c)
            i++
        }
        error("A Log call that never closes")
    }

    /** Where the string literals in [text] are. */
    private fun literals(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '"') {
                val end = stringEnd(text, i)
                ranges += i until end
                i = end
            } else {
                i++
            }
        }
        return ranges
    }

    /** Just past the string literal that starts at [start], plain or raw, stepping over `${…}` templates. */
    private fun stringEnd(text: String, start: Int): Int {
        val raw = text.startsWith("\"\"\"", start)
        var i = start + if (raw) 3 else 1
        while (i < text.length) {
            when {
                !raw && text[i] == '\\' -> i += 2
                text.startsWith("\${", i) -> {
                    var depth = 1
                    i += 2
                    while (depth > 0) {
                        when (text[i]) {
                            '{' -> depth++
                            '}' -> depth--
                            '"' -> i = stringEnd(text, i) - 1
                        }
                        i++
                    }
                }
                raw && text.startsWith("\"\"\"", i) -> return i + 3
                !raw && text[i] == '"' -> return i + 1
                else -> i++
            }
        }
        error("A string that never closes")
    }

    private fun String.without(ranges: List<IntRange>): String {
        val out = StringBuilder()
        var from = 0
        ranges.forEach { r ->
            out.append(this, from, r.first)
            from = r.last + 1
        }
        out.append(this, from, length)
        return out.toString()
    }
}
```
Then make the test run again whenever the code it reads changes (Gradle otherwise treats it as up to date, since the app's sources aren't its inputs). In `build-logic/convention/build.gradle.kts`, add at the top `import org.gradle.api.tasks.PathSensitivity` and at the end:
```kotlin
// LogHygieneTest reads the app's own sources.
tasks.test {
    inputs.files(fileTree(rootDir.parentFile) { include("*/src/main/**/*.kt", "*/*/src/main/**/*.kt", "*/src/release/**/*.kt") })
        .withPropertyName("culverySources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
```

- [ ] **Step 2: Run it to see what it finds**

Run: `./gradlew -p build-logic :convention:test --tests LogHygieneTest`
Expected: the five self-checks PASS; `shippedWarningsAndErrorsCarryNoThrowableAndNoPersonalData` FAILS listing about 25 calls that pass a throwable and about 10 that interpolate a bare local — those in Step 3, and nothing else. A call it lists that Step 3 doesn't is either personal data (fix the message) or a fixed id the list lacks (stop and ask before widening `ALLOWED`).

- [ ] **Step 3: Scrub the throwables and qualify the ids**

Make each change exactly; nothing else in these files changes:
- `app/.../CulveryApp.kt`: `Log.e(TAG, "${startable.javaClass.name} failed to start", e)` → `Log.e(TAG, "${startable::class.simpleName} failed to start (${e::class.simpleName})")`
- `app/.../di/AppModule.kt`: `Log.e("Culvery", "An application job failed", e)` → `Log.e("Culvery", "An application job failed (${e::class.simpleName})")`
- `app/.../shell/ShellViewModel.kt`: `Log.w(TAG, "${cap.id}: couldn't read whether it has a tab; retrying", it)` → `Log.w(TAG, "${cap.id}: couldn't read whether it has a tab (${it::class.simpleName}); retrying")`; `Log.w(TAG, "${cap.id}: couldn't read its Home cards; retrying", it)` → `Log.w(TAG, "${cap.id}: couldn't read its Home cards (${it::class.simpleName}); retrying")`
- `CalendarEditor.kt`: `Log.w(TAG, "Couldn't save a $kind", e)` → `Log.w(TAG, "Couldn't save a ${kind.name} (${e::class.simpleName})")`; `Log.w(TAG, "The provider accepted a $kind but the tablet couldn't store it; the next sync will", e)` → `Log.w(TAG, "The provider accepted a ${kind.name} but the tablet couldn't store it (${e::class.simpleName}); the next sync will")`
- `CalendarSetup.kt`: `Log.w(TAG, "Couldn't record ${connection.id} as reconnected", e)` → `Log.w(TAG, "Couldn't record ${connection.id} as reconnected (${e::class.simpleName})")`
- `CalendarStore.kt`: `.onFailure { Log.w(TAG, "Dropping unreadable outbox row $id (kind $kind)", it) }` → `.onFailure { Log.w(TAG, "Dropping unreadable outbox row ${this.id} (kind ${this.kind}, ${it::class.simpleName})") }`
- `CalendarSync.kt`:
  - `"The outbox drain failed ($failedDrains in a row); syncing anyway", e)` → `"The outbox drain failed (${this.failedDrains} in a row, ${e::class.simpleName}); syncing anyway")`
  - `"The provider accepted a queued ${change.kind} but the tablet couldn't store it; retrying later", e)` → `"The provider accepted a queued ${change.kind} but the tablet couldn't store it (${e::class.simpleName}); retrying later")`
  - `"${conn.id}: a source needs signing in again", e)` → `"${conn.id}: a source needs signing in again (${e::class.simpleName})")`
  - `"${conn.id}: a source is gone from the service; flagging a refresh of its calendars", e)` → `"${conn.id}: a source is gone from the service (${e::class.simpleName}); flagging a refresh of its calendars")`
  - `"${conn.id}: a source is unreachable", e)` → `"${conn.id}: a source is unreachable (${e::class.simpleName})")`
  - `Log.e(TAG, "${conn.id}: a source failed", e)` → `Log.e(TAG, "${conn.id}: a source failed (${e::class.simpleName})")`
- `CalendarSyncLoop.kt`: `"Couldn't read the connections; retrying", it)` → `"Couldn't read the connections (${it::class.simpleName}); retrying")`; `"Calendar sync was cancelled internally", e)` → `"Calendar sync was cancelled internally (${e::class.simpleName})")`; `"Calendar sync failed", e)` → `"Calendar sync failed (${e::class.simpleName})")`; `"Couldn't read the outbox; waiting the full interval", e)` → `"Couldn't read the outbox (${e::class.simpleName}); waiting the full interval")`
- `SourceRefresher.kt`: `"$id: couldn't read its calendars; trying again next pass", it)` → `"${stored.connection.id}: couldn't read its calendars (${it::class.simpleName}); trying again next pass")`; `"$id: its calendar list has no primary calendar; treating it as a failed read")` → `"${stored.connection.id}: its calendar list has no primary calendar; treating it as a failed read")`
- `Writes.kt`: `"A calendar write failed unexpectedly; it will be retried", e)` → `"A calendar write failed unexpectedly (${e::class.simpleName}); it will be retried")`
- `ui/CalendarConnectHost.kt`: `Log.w(TAG, "No calendar provider $providerId in this build; nothing to connect")` → `Log.w(TAG, "No calendar provider for that id in this build; nothing to connect")`
- `ui/EventDetailHost.kt`: `{ e -> Log.w(TAG, "Couldn't change an event", e) }` → `{ e -> Log.w(TAG, "Couldn't change an event (${e::class.simpleName})") }`
- `ui/EventEditorHost.kt`: `"Couldn't open the add/edit sheet", e)` → `"Couldn't open the add/edit sheet (${e::class.simpleName})")`; `"Couldn't save", e)` → `"Couldn't save (${e::class.simpleName})")`; `"Couldn't start a delete", e)` → `"Couldn't start a delete (${e::class.simpleName})")`
- `GoogleConnectFlow.kt`: `Log.i(TAG, "Google sign-in was cancelled", e)` → `Log.i(TAG, "Google sign-in was cancelled")`; `Log.w(TAG, "Couldn't connect to Google", e)` → `Log.w(TAG, "Couldn't connect to Google (${e::class.simpleName})")`; `.readOrUnreachable("calendars.get primary")` → `.readOrUnreachable(GoogleCall("calendars.get primary"))` and `.decode(CalendarResource.serializer(), "calendars.get primary")` → `.decode(CalendarResource.serializer(), GoogleCall("calendars.get primary"))`.
- `GoogleHttp.kt`: after `TAG` add
```kotlin
/** Which call a log line is about: a fixed name such as "events.list", never a calendar, an email or an id. */
@JvmInline
internal value class GoogleCall(val label: String)
```
  `decode`, `readOrUnreachable` and `refusal` take `what: GoogleCall` (was `String`), and their lines become `Log.w(TAG, "${what.label}: Google Calendar sent a body the tablet can't read (${e::class.simpleName})")`, `Log.w(TAG, "${what.label}: Google Calendar answered ${this.code} (${this.reason})")`, `Log.w(TAG, "${what.label}: Google Calendar refused it with ${this.code} (${this.reason})")`; in `execute`, `"$method ${pathTemplate(url)}: Google Calendar says the grant lacks a calendar scope (${answer.reason})"` → `"${request.method} ${pathTemplate(request.url)}: Google Calendar says the grant lacks a calendar scope (${answer.reason})"` and `"$method ${pathTemplate(url)}: Google Calendar said try later: ${answer.code} (${answer.reason})"` → `"${request.method} ${pathTemplate(request.url)}: Google Calendar said try later: ${answer.code} (${answer.reason})"`.
- `GoogleCalendarProvider.kt`: every string passed to `readOrUnreachable`, `decode` or `refusal` is wrapped, e.g. `.readOrUnreachable("events.list")` → `.readOrUnreachable(GoogleCall("events.list"))`, `answer.refusal("Adding an event")` → `answer.refusal(GoogleCall("Adding an event"))` (11 places); `oneOffToChange(…, what: String)` takes `what: GoogleCall`, its three callers pass `GoogleCall("Changing an event")` / `GoogleCall("Deleting an event")`, and its line becomes `Log.w(TAG, "${what.label}: it has become a repeating event in Google Calendar, so the tablet leaves it alone")`.
- `GoogleApiTest.kt`: wrap the strings it passes to `decode`, `refusal` and `readOrUnreachable` in `GoogleCall(…)`.

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew -p build-logic :convention:test --tests LogHygieneTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Point the tests that read a logged throwable at the message**

- `app/src/test/java/uk/co/siland/culvery/ApplicationScopeTest.kt`: replace `assertThat(ShadowLog.getLogsForTag("Culvery").map { it.throwable?.message }).contains("a sync job failed")` with `assertThat(ShadowLog.getLogsForTag("Culvery").map { it.msg }).contains("An application job failed (IllegalStateException)")`.
- `CalendarSyncLoopTest.anErrorInAPassIsLoggedAndTheNextPassStillRuns`: replace `.map { it.throwable?.message }).contains("a writer recursed")` with `.map { it.msg }).contains("Calendar sync failed (StackOverflowError)")`.
- `CalendarSyncTest.aWriterThrowingAnErrorIsLoggedAndTheChangeIsTriedAgainLater`: replace `.map { it.throwable?.message }).contains("writer recursed")` with `.map { it.msg }).contains("A calendar write failed unexpectedly (StackOverflowError); it will be retried")`.

Run: `./gradlew :app:testDebugUnitTest :capability:calendar:testDebugUnitTest :provider:calendar-google:testDebugUnitTest`
Expected: PASS. Any other test that read `it.throwable` from one of Step 3's lines fails here: change it to read the message with the type, the same way, and nothing else.

- [ ] **Step 6: Turn R8 on, with only the rules the libraries don't ship**

kotlinx-serialization-core 1.9 ships the keeps for `@Serializable` classes and OkHttp 4.12 the `-dontwarn` lines for its optional TLS providers (plan review 6), so the app's own file holds only what nothing else does. Create `app/proguard-rules.pro`:
```proguard
# Culvery's own R8 rules (4c design §3.3, §3.4); the libraries ship theirs.

# Release stack traces keep their line numbers; source file names are hidden. Read them with the build's mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Release logging (D8): debug and info lines are removed; warnings and errors stay, and name no one.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```
In `app/build.gradle.kts`, the `buildTypes { release { … } }` block Task 1 added becomes:
```kotlin
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
```

- [ ] **Step 7: Say how to read a release crash**

Append to `docs/setup/release.md`:
````markdown

## 4. Reading a release crash

R8 renames the app's classes in a release build, and the log names exceptions by their short (renamed) names. Each build writes `app/build/outputs/mapping/release/mapping.txt`: keep a copy beside every APK you install. To read a stack trace or a log line from that build, run the Android SDK's `retrace` with it:

```bash
"$LOCALAPPDATA/Android/Sdk/cmdline-tools/latest/bin/retrace" mapping.txt crash.txt
```

A release logs only warnings and errors, and they name no person, calendar, account, town, coordinates or time zone.
````

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` and `./gradlew -p build-logic :convention:test`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 9: Commit**

```bash
git add build-logic app capability provider docs/setup/release.md
git commit -m "Shrink release builds with R8, strip debug logging, and keep personal data and throwables out of warnings"
```

- [ ] **Step 10: Check the minified build works (Review Focus 1)**

Unit tests can't run R8, so the release build itself is the check: what kotlinx.serialization reads (the weather now, Google later in Task 19) must still read. The signing properties are set (Task 1's checkpoint); this reads none of them.
```bash
./gradlew :app:assembleRelease
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -n uk.co.siland.culvery/.MainActivity
adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4c-r8.png"
adb -s emulator-5554 logcat -d | grep -E "AndroidRuntime|FATAL|SerializationException|uk.co.siland.culvery" | tail -20
```
Expected: `BUILD SUCCESSFUL`; the install keeps Task 1's household (same key); within a minute Home shows London's weather in the header and the Forecast card (the forecast's serializers work under R8); in Settings › Home location, a search for "Leeds" lists towns (the geocoding serializers); no `FATAL`, no `SerializationException`, and no `D/` or `I/` line from Culvery's tags. Set the location back to London. Attach the screenshot to the report. If the weather or the search fails here and works in debug, R8 removed something the libraries' rules didn't keep: stop and report with the log lines.

---

### Task 4: Start-up off Main, the first-draw gate and the splash (§4.1; rulings 8, 10, 19)

**Review:** sonnet.

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/FirstDraw.kt`
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Startable.kt` (KDoc)
- Test: `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/FirstDrawTest.kt` (create)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoop.kt`; test `CalendarSyncLoopTest.kt`; `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt` (it builds the loop with the injected constructor, positionally)
- Modify: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherSyncLoop.kt`; test `WeatherSyncLoopTest.kt`
- Modify: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleHttp.kt`, `TokenSource.kt`, `di/GoogleCalendarModule.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`, `MainActivity.kt`, `SetupWiring.kt`, `shell/ShellUiState.kt`, `shell/ShellViewModel.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/SetupWiringTest.kt`, `AppContentTest.kt`, `shell/ShellViewModelTest.kt`

**Interfaces:**
- Consumes: Task 3's logging rule.
- Produces:
  - `const val FIRST_DRAW_WAIT_MS = 3_000L`; `@Singleton class FirstDraw @Inject constructor() { fun markDrawn(); suspend fun await(timeoutMillis: Long = FIRST_DRAW_WAIT_MS) }` in `uk.co.siland.culvery.core.plugin`.
  - `CalendarSyncLoop`'s internal constructor gains a last parameter `firstDraw: suspend () -> Unit = {}`; `WeatherSyncLoop`'s gains `firstDraw: suspend () -> Unit = {}`.
  - `ShellUiState.cardsLoaded: Boolean` (false until every capability's `cards()` has answered once).
  - `internal const val SPLASH_MAX_MS = 2_000L`; `internal fun holdSplash(complete: Boolean?, cardsLoaded: Boolean, shownMillis: Long): Boolean` in `SetupWiring.kt`.
  - `AppContent(…, onHomeDrawn: () -> Unit)`: a new last parameter, called once after the shell's first frame.
  - `GoogleApi(baseUrl: HttpUrl, tokens: TokenSource, clients: Lazy<OkHttpClient>)` with the old `(…, client: OkHttpClient)` kept as a secondary constructor.

- [ ] **Step 1: Write the failing `FirstDraw` test**

Create `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/FirstDrawTest.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirstDrawTest {
    @Test
    fun aWaiterGoesOnOnceHomeHasDrawn() = runTest {
        val draw = FirstDraw()
        var through = false
        launch { draw.await(); through = true }
        runCurrent()
        assertThat(through).isFalse()
        draw.markDrawn()
        runCurrent()
        assertThat(through).isTrue()
    }

    @Test
    fun withoutAFrameAWaiterGoesOnAfterThreeSeconds() = runTest {
        var through = false
        launch { FirstDraw().await(); through = true }
        advanceTimeBy(FIRST_DRAW_WAIT_MS - 1)
        runCurrent()
        assertThat(through).isFalse()
        advanceTimeBy(1)
        runCurrent()
        assertThat(through).isTrue()
    }

    @Test
    fun aLaterWaiterDoesNotWaitAtAll() = runTest {
        val draw = FirstDraw()
        draw.markDrawn()
        var through = false
        launch { draw.await(); through = true }
        runCurrent()
        assertThat(through).isTrue()
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:plugin:testDebugUnitTest --tests "*FirstDrawTest*"`
Expected: FAIL to compile with "Unresolved reference 'FirstDraw'".

- [ ] **Step 3: Write `FirstDraw`**

Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/FirstDraw.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** The longest the first background passes wait for Home's first frame (4c design §4.1). */
const val FIRST_DRAW_WAIT_MS = 3_000L

/**
 * A one-shot "Home has drawn" signal from the shell (4c design §4.1): the first calendar and weather passes wait for it,
 * or [FIRST_DRAW_WAIT_MS], so they don't compete with start-up's first frames.
 */
@Singleton
class FirstDraw @Inject constructor() {
    private val drawn = CompletableDeferred<Unit>()

    fun markDrawn() {
        drawn.complete(Unit)
    }

    suspend fun await(timeoutMillis: Long = FIRST_DRAW_WAIT_MS) {
        withTimeoutOrNull(timeoutMillis) { drawn.await() }
    }
}
```
In `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Startable.kt` replace the KDoc with:
```kotlin
/**
 * Bound `@IntoSet` by a capability that needs background work (e.g. a sync loop). The app calls [start] once at
 * start-up, off the main thread (4c design §4.1); it must return quickly and launch its work on the @ApplicationScope scope.
 */
```

Run: `./gradlew :core:plugin:testDebugUnitTest --tests "*FirstDrawTest*"`
Expected: PASS (3 tests).

- [ ] **Step 4: Write the failing loop tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoopTest.kt` add (import `uk.co.siland.culvery.core.plugin.FirstDraw`; the 3-second fallback is `FirstDrawTest`'s alone):
```kotlin
    @Test
    fun theFirstPassWaitsForHomesFirstFrame() = runTest {
        var count = 0
        val draw = FirstDraw()
        CalendarSyncLoop({ count++ }, MutableStateFlow(listOf("c1")), backgroundScope, firstDraw = { draw.await() }).start()
        runCurrent()
        assertThat(count).isEqualTo(0)
        draw.markDrawn()
        runCurrent()
        assertThat(count).isEqualTo(1)
    }
```
In `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherSyncLoopTest.kt` add (import `uk.co.siland.culvery.core.plugin.FirstDraw`; the class's `london` field is `WeatherPlace(LONDON)`):
```kotlin
    @Test
    fun theFirstFetchWaitsForHomesFirstFrame() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val draw = FirstDraw()
        WeatherSyncLoop({ fetched += it }, MutableStateFlow<WeatherPlace?>(london), backgroundScope, firstDraw = { draw.await() }).start()
        runCurrent()
        assertThat(fetched).isEmpty()
        draw.markDrawn()
        runCurrent()
        assertThat(fetched).containsExactly(london)
    }
```

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSyncLoopTest*" :capability:weather:testDebugUnitTest --tests "*WeatherSyncLoopTest*"`
Expected: FAIL to compile: "No parameter with name 'firstDraw' found".

- [ ] **Step 5: Make both loops wait**

`CalendarSyncLoop.kt`: add the parameter last in the internal constructor, pass it from the injected one, and wait before the loop:
```kotlin
class CalendarSyncLoop internal constructor(
    private val syncAll: suspend () -> Unit,
    private val connectionIds: Flow<List<String>>,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = SYNC_INTERVAL_MS,
    private val untilNextRetry: suspend () -> Long? = { null },
    private val drainBackoff: () -> Long? = { null },
    private val firstDraw: suspend () -> Unit = {},
) : Startable {
    @Inject
    constructor(sync: CalendarSync, store: CalendarStore, clock: WallClock, firstDraw: FirstDraw, @ApplicationScope scope: CoroutineScope) :
        this(
            sync::syncAll,
            store.connectionIds(),
            scope,
            SYNC_INTERVAL_MS,
            { store.nextAttemptMillis()?.let { it - clock.nowMillis() } },
            sync::drainBackoffMillis,
            { firstDraw.await() },
        )
```
and in `start()`, after the `launch { connectionIds… }` block and before `var wait = intervalMillis`:
```kotlin
            // 4c §4.1: start-up's frames first.
            firstDraw()
```
Update the class KDoc's first sentence to "Runs a pass … as soon as Home has drawn (or [FIRST_DRAW_WAIT_MS] has passed) and the first connection list has arrived, …" (keep the rest), importing `uk.co.siland.culvery.core.plugin.FirstDraw` and `uk.co.siland.culvery.core.plugin.FIRST_DRAW_WAIT_MS`.

`WeatherSyncLoop.kt`:
```kotlin
class WeatherSyncLoop internal constructor(
    private val fetch: suspend (WeatherPlace) -> Unit,
    private val places: Flow<WeatherPlace?>,
    private val scope: CoroutineScope,
    private val firstDraw: suspend () -> Unit = {},
) : Startable {
    @Inject
    constructor(fetcher: WeatherFetcher, household: HouseholdRepository, firstDraw: FirstDraw, @ApplicationScope scope: CoroutineScope) :
        this(fetcher::fetch, household.location.places(), scope, { firstDraw.await() })
```
and in `start()`, after the `launch { places… }` block and before `// Null: wait for a location, with no timer.`:
```kotlin
            // 4c §4.1: start-up's frames first; a place that arrived meanwhile is waiting in the channel.
            firstDraw()
```

`app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt` line 109 builds the loop through the injected constructor: `CalendarSyncLoop(sync, store, clock, backgroundScope)` becomes `CalendarSyncLoop(sync, store, clock, FirstDraw().also { it.markDrawn() }, backgroundScope)` (import `uk.co.siland.culvery.core.plugin.FirstDraw`; already drawn, so its passes don't wait).

Run the two test classes again, and `./gradlew :app:testDebugUnitTest --tests "*SampleAddTest*"`.
Expected: PASS, the existing tests included (they pass no `firstDraw`).

- [ ] **Step 6: Build Google's heavy members lazily (ruling 8)**

`provider/calendar-google/.../GoogleHttp.kt`: the `GoogleApi` class header becomes
```kotlin
class GoogleApi(private val baseUrl: HttpUrl, private val tokens: TokenSource, clients: Lazy<OkHttpClient>) {
    /** Tests pass a client they built; the app's is built on its first call, off the main thread (4c §4.1). */
    constructor(baseUrl: HttpUrl, tokens: TokenSource, client: OkHttpClient) : this(baseUrl, tokens, lazyOf(client))

    private val client: OkHttpClient by clients
```
(the rest of the class unchanged).
`TokenSource.kt`: `class PlayServicesAuthorizer @Inject constructor(@ApplicationContext context: Context) : Authorizer { private val client = Identity.getAuthorizationClient(context)` becomes
```kotlin
class PlayServicesAuthorizer @Inject constructor(@ApplicationContext private val context: Context) : Authorizer {
    // Built on the first call: Hilt makes this class on the main thread while the shell starts (4c §4.1).
    private val client by lazy { Identity.getAuthorizationClient(context) }
```
`di/GoogleCalendarModule.kt`, in `api(tokens)`:
```kotlin
        fun api(tokens: TokenSource): GoogleApi =
            GoogleApi(GOOGLE_CALENDAR_BASE_URL.toHttpUrl(), tokens, lazy { OkHttpClient.Builder().connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build() })
```
Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: PASS (tests use the secondary constructor).

- [ ] **Step 7: Start the loops and the debug seed off Main**

`app/.../CulveryApp.kt` becomes:
```kotlin
package uk.co.siland.culvery

import android.app.Application
import android.util.Log
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

@HiltAndroidApp
class CulveryApp : Application() {
    // Lazy and read on the application scope (Dispatchers.Default): the loops and the providers are built off Main (4c §4.1).
    @Inject lateinit var household: Lazy<HouseholdRepository>
    @Inject lateinit var calendarSetup: Lazy<CalendarSetup>
    @Inject lateinit var calendarProviders: Lazy<Set<@JvmSuppressWildcards CalendarProvider>>
    @Inject lateinit var startables: Lazy<Set<@JvmSuppressWildcards Startable>>
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            startAll(startables.get()) { startable, e -> Log.e(TAG, "${startable::class.simpleName} failed to start (${e::class.simpleName})") }
        }
        appScope.launch { seedDebugData(household.get(), calendarSetup.get(), calendarProviders.get()) }
        appScope.launch { removeSampleWhenReplaced(calendarSetup.get()) }
    }

    private companion object {
        const val TAG = "Culvery"
    }
}
```

- [ ] **Step 8: Write the failing shell tests**

`app/src/test/java/uk/co/siland/culvery/SetupWiringTest.kt`, add:
```kotlin
    @Test
    fun theSplashHoldsUntilSetupIsKnown() {
        assertThat(holdSplash(complete = null, cardsLoaded = false, shownMillis = 0)).isTrue()
    }

    @Test
    fun theWizardShowsAtOnce() {
        assertThat(holdSplash(complete = false, cardsLoaded = false, shownMillis = 0)).isFalse()
    }

    @Test
    fun homeWaitsForItsFirstCards() {
        assertThat(holdSplash(complete = true, cardsLoaded = false, shownMillis = 0)).isTrue()
        assertThat(holdSplash(complete = true, cardsLoaded = true, shownMillis = 0)).isFalse()
    }

    @Test
    fun theSplashNeverHoldsForTwoSeconds() {
        assertThat(holdSplash(complete = true, cardsLoaded = false, shownMillis = SPLASH_MAX_MS - 1)).isTrue()
        assertThat(holdSplash(complete = null, cardsLoaded = false, shownMillis = SPLASH_MAX_MS)).isFalse()
    }
```
`app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt`, add:
```kotlin
    @Test
    fun cardsAreLoadedOnceEveryCapabilityHasAnswered() = runTest {
        vm(setOf(FakeCapability("calendar", order = 10, shown = true))).uiState.test {
            assertThat(expectMostRecentItem().cardsLoaded).isTrue()
        }
        vm(setOf(FakeCapability("calendar", order = 10, shown = true), NeverEmittingCapability("lights", order = 20))).uiState.test {
            assertThat(expectMostRecentItem().cardsLoaded).isFalse()
        }
    }
```
`app/src/test/java/uk/co/siland/culvery/AppContentTest.kt`: the `AppContent(` call in `show()` gains a last argument `onHomeDrawn = { homeDrawn++ }` with a field `private var homeDrawn = 0`; add:
```kotlin
    @Test
    fun theWizardIsNotHome() {
        complete = false
        show()
        compose.waitForIdle()
        assertThat(homeDrawn).isEqualTo(0)
    }
```
Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL to compile ("Unresolved reference 'holdSplash'", "'cardsLoaded'", "No parameter with name 'onHomeDrawn'").

- [ ] **Step 9: The shell knows when its cards are in, and the splash rule**

`shell/ShellUiState.kt`: add after `headerItems`:
```kotlin
    /** Every capability's Home cards have answered once: the splash can go (4c §4.1). */
    val cardsLoaded: Boolean = false,
```
`shell/ShellViewModel.kt`: replace the `placements` property with
```kotlin
    private class PlacedCards(val placements: List<HomePlacement>, val loaded: Boolean)

    private val placements: Flow<PlacedCards> =
        if (ordered.isEmpty()) {
            flowOf(PlacedCards(emptyList(), loaded = true))
        } else {
            combine(
                ordered.map { cap ->
                    cap.cards()
                        .map<List<HomeCard>, List<HomeCard>?> { it }
                        .retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its Home cards (${it::class.simpleName}); retrying") }
                        // Null until this capability answers.
                        .onStart { emit(null) }
                },
            ) { lists -> PlacedCards(HomeCardPlacer.place(lists.filterNotNull().flatten()), loaded = lists.none { it == null }) }
        }
```
(import `uk.co.siland.culvery.core.plugin.HomeCard`), and in `uiState`'s inner `combine` change `homeCards = cards,` to `homeCards = cards.placements,` and add `cardsLoaded = cards.loaded,` after it.

`SetupWiring.kt`, add after `pinOnSetupRead`:
```kotlin
/** The splash's longest stay (4c design §4.1). */
internal const val SPLASH_MAX_MS = 2_000L

/** 4c §4.1: the splash stays until setup's state is known and, when Home shows, its first cards are in; at most [SPLASH_MAX_MS]. */
internal fun holdSplash(complete: Boolean?, cardsLoaded: Boolean, shownMillis: Long): Boolean =
    shownMillis < SPLASH_MAX_MS && (complete == null || (complete && !cardsLoaded))
```
and give `AppContent` a last parameter `onHomeDrawn: () -> Unit`, with the `true` branch becoming:
```kotlin
        true -> {
            shell()
            if (settingsOpen) settings()
            // Once: the first background passes wait for it (4c §4.1).
            LaunchedEffect(Unit) {
                withFrameNanos { }
                onHomeDrawn()
            }
        }
```
(import `androidx.compose.runtime.withFrameNanos`; update the KDoc's last sentence to add "The shell's first frame is reported once to [onHomeDrawn].").

Run: `./gradlew :app:testDebugUnitTest`
Expected: still FAIL to compile in `MainActivity` (the new `AppContent` argument); the tests compile once Step 10 is done.

- [ ] **Step 10: The splash in the activity**

`gradle/libs.versions.toml`: under `[versions]` after `coreKtx = "1.16.0"` add `coreSplashscreen = "1.2.0"`; under `[libraries]` after `androidx-core-ktx` add
```toml
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }
```
`app/build.gradle.kts`: after `implementation(libs.androidx.core.ktx)` add `implementation(libs.androidx.core.splashscreen)`.

`app/src/main/res/values/themes.xml` becomes:
```xml
<resources>
    <style name="Theme.Culvery" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">#FF0E1011</item>
    </style>

    <!-- 4c design §4.1: until setup's state and Home's first cards are known, at most 2 s. Its icon comes in 4d. -->
    <style name="Theme.Culvery.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#FF0E1011</item>
        <item name="postSplashScreenTheme">@style/Theme.Culvery</item>
    </style>
</resources>
```
`app/src/main/AndroidManifest.xml`: on the `<activity android:name=".MainActivity"` element add `android:theme="@style/Theme.Culvery.Starting"`.

`MainActivity.kt`:
- add `@Inject lateinit var firstDraw: FirstDraw` (import `uk.co.siland.culvery.core.plugin.FirstDraw`);
- `onCreate` starts:
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shownAt = SystemClock.uptimeMillis()
```
- move the line `setupComplete = setupState.setupComplete.stateIn(lifecycleScope, SharingStarted.Eagerly, null)` up to just after `shownAt`, followed by:
```kotlin
        splash.setKeepOnScreenCondition {
            holdSplash(setupComplete.value, shell.uiState.value.cardsLoaded, SystemClock.uptimeMillis() - shownAt)
        }
```
- the `AppContent(` call gains `onHomeDrawn = firstDraw::markDrawn,` as its last argument.
(imports: `android.os.SystemClock`, `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen`.)

Run: `./gradlew :app:checkDebugAarMetadata :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (core-splashscreen 1.2.0's minCompileSdk is 35, ruling 19), the new tests PASS.

- [ ] **Step 11: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` and `./gradlew -p build-logic :convention:test`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 12: Commit**

```bash
git add core/plugin capability provider gradle/libs.versions.toml app
git commit -m "Start the loops off the main thread after Home's first frame, and hold a splash until Home's cards are in"
```

---

### Task 5: Recomposition — the remembered `ColorScheme`, and `now` out of the root (§4.2, P1, P2; §7.2's `headerItems` default)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Theme.kt`; test `ThemeTest.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellUiState.kt`, `shell/ShellViewModel.kt`, `shell/ui/CulveryShell.kt`, `shell/ui/HomeScreen.kt`, `shell/ui/StatusBar.kt`, `MainActivity.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt`, `shell/ui/ShellLayoutTest.kt`, `shell/ui/ShellScreenshotTest.kt`; `shell/ui/NoNavigation.kt` (create, moved out of `ShellScreenshotTest`)

**Interfaces:**
- Consumes: Task 4's `ShellUiState.cardsLoaded`.
- Produces:
  - `ShellUiState` has no `now`. `ShellViewModel.now: StateFlow<LocalDateTime>` is public.
  - `CulveryShell(state, now: () -> LocalDateTime, onSelectTab, onOpenSettings, onSignOut, onToggleThemePreview, tabContent)`; `StatusBar(now: () -> LocalDateTime, dark, previewing, session, onSignOut, onToggleThemePreview)`; `HomeScreen(now: () -> LocalDateTime, placements, headerItems)` with no default for `headerItems` (§7.2, done here as every caller changes); `HomeHeader(now: () -> LocalDateTime, items, modifier)`. The time is read inside the clocks, so a minute's tick recomposes only them.

- [ ] **Step 1: Write the failing theme test**

In `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ThemeTest.kt` add, above the class:
```kotlin
private var probeCompositions = 0

/** Reads the theme as content does; counts its compositions. */
@Composable
private fun Probe() {
    probeCompositions++
    Text("probe", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge.copy(color = Culvery.colors.ink))
}
```
and in the class:
```kotlin
    /** P1: the theme rebuilt with the same colours hands its content the same scheme, so content that hasn't changed skips. */
    @Test
    fun aRecomposedThemeKeepsItsSchemeSoUnchangedContentSkips() {
        var tick by mutableIntStateOf(0)
        compose.setContent {
            val t = tick
            // A new content lambda each tick, so CulveryTheme itself runs again.
            CulveryTheme(dark = true) { if (t >= 0) Probe() }
        }
        compose.waitForIdle()
        val before = probeCompositions
        tick++
        compose.waitForIdle()
        assertThat(probeCompositions).isEqualTo(before)
    }
```
(imports: `androidx.compose.material3.MaterialTheme`, `androidx.compose.material3.Text`, `androidx.compose.runtime.Composable`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableIntStateOf`, `androidx.compose.runtime.setValue`.)

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*ThemeTest*"`
Expected: `aRecomposedThemeKeepsItsSchemeSoUnchangedContentSkips` FAILS (expected the count unchanged, it went up: a fresh `ColorScheme` and `Typography` invalidate everything under the theme).

- [ ] **Step 3: Remember the scheme; make the typography a value**

`Theme.kt` becomes (the `animated` helper and `Culvery` object unchanged):
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalHhColors = staticCompositionLocalOf { DarkColors }

object Culvery {
    val colors: HhColors
        @Composable get() = LocalHhColors.current
}

private val CulveryTypography: Typography = Typography().let { t ->
    t.copy(
        bodyLarge = t.bodyLarge.copy(fontFamily = DmSans),
        bodyMedium = t.bodyMedium.copy(fontFamily = DmSans),
        labelLarge = t.labelLarge.copy(fontFamily = DmSans),
        titleMedium = t.titleMedium.copy(fontFamily = DmSans),
    )
}

@Composable
fun CulveryTheme(dark: Boolean, content: @Composable () -> Unit) {
    val target = if (dark) DarkColors else LightColors
    val colors = HhColors(
        bg = animated(target.bg),
        surf = animated(target.surf),
        surf2 = animated(target.surf2),
        surf3 = animated(target.surf3),
        line = animated(target.line),
        ink = animated(target.ink),
        mute = animated(target.mute),
        accent = animated(target.accent),
        accentInk = animated(target.accentInk),
        accentSoft = animated(target.accentSoft),
        danger = animated(target.danger),
        dangerSoft = animated(target.dangerSoft),
        dangerInk = animated(target.dangerInk),
    )
    // Equal colours, same scheme: a theme that runs again doesn't invalidate everything under it (4c §4.2, P1).
    val scheme = remember(dark, colors) {
        (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = colors.accent,
            onPrimary = colors.accentInk,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surf,
            onSurface = colors.ink,
            onSurfaceVariant = colors.mute,
        )
    }
    CompositionLocalProvider(LocalHhColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = CulveryTypography, content = content)
    }
}

@Composable
private fun animated(target: Color): Color =
    animateColorAsState(target, animationSpec = tween(durationMillis = 400), label = "theme").value
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*ThemeTest*"`
Expected: PASS (5 tests).

- [ ] **Step 5: Write the failing shell test**

In `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt` add:
```kotlin
    /** P2: through the whole shell, as MainActivity draws it, a minute's tick redraws the clocks and nothing on the cards. */
    @Test
    fun aMinuteTickRedrawsTheClockAndNotTheCards() {
        var now by mutableStateOf(at)
        var cardCompositions = 0
        val card = HomeCard("card", HomeCardSize.TALL, 1) {
            cardCompositions++
            Text("card")
        }
        val state = ShellUiState(homeCards = HomeCardPlacer.place(listOf(card)))
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides NoNavigation) {
                CulveryTheme(dark = true) {
                    CulveryShell(
                        state = state,
                        now = { now },
                        onSelectTab = {},
                        onOpenSettings = {},
                        onSignOut = {},
                        onToggleThemePreview = {},
                        tabContent = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        val before = cardCompositions
        now = at.plusMinutes(1)
        compose.waitForIdle()
        compose.onNodeWithTag("home_clock").assertTextEquals("11:55")
        assertThat(cardCompositions).isEqualTo(before)
    }
```
(imports as needed: `androidx.compose.material3.Text`, `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.test.assertTextEquals`, `uk.co.siland.culvery.core.plugin.HomeCard`, `HomeCardPlacer`, `HomeCardSize`, `LocalShellNavigator`, `uk.co.siland.culvery.shell.ShellUiState`. `NoNavigation` is `ShellScreenshotTest`'s private object: move it to `app/src/test/java/uk/co/siland/culvery/shell/ui/NoNavigation.kt` as `internal object NoNavigation : ShellNavigator` with the same body, and use it from both.)
In the same file change the existing calls: `HomeScreen(at, emptyList())` → `HomeScreen({ at }, emptyList(), emptyList())`; `HomeScreen(at, emptyList(), items.toList())` → `HomeScreen({ at }, emptyList(), items.toList())`; `StatusBar(at, dark = true, …)` → `StatusBar({ at }, dark = true, …)`.

Run: `./gradlew :app:testDebugUnitTest --tests "*ShellLayoutTest*"`
Expected: FAIL to compile (`CulveryShell` has no `now`; `HomeScreen` takes a `LocalDateTime`).

- [ ] **Step 6: The clocks read the time themselves**

`shell/ShellUiState.kt`: delete `val now: LocalDateTime = LocalDateTime.now(),` and the then-unused `import java.time.LocalDateTime`.

`shell/ShellViewModel.kt`:
- `private val now: StateFlow<LocalDateTime> =` → `val now: StateFlow<LocalDateTime> =`, with the KDoc `/** The household's wall time, for the status bar and Home's clock; not part of [uiState], so a tick redraws only them (4c §4.2). */`
- `uiState` becomes
```kotlin
    val uiState: StateFlow<ShellUiState> =
        combine(
            combine(tabs, selected, access.session, placements, settingsOpen) { tabs, sel, session, cards, settings ->
                ShellUiState(
                    tabs = tabs,
                    selectedTabId = if (sel == HOME_TAB_ID || tabs.any { it.id == sel }) sel else HOME_TAB_ID,
                    session = session?.let { SessionUi(it.person.name, roleLabel(it.role)) },
                    homeCards = cards.placements,
                    cardsLoaded = cards.loaded,
                    settingsOpen = settings && session != null,
                )
            },
            scheduledDark,
            previewing,
            headerItems,
        ) { state, scheduled, preview, header ->
            state.copy(dark = scheduled != preview, previewing = preview, headerItems = header)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState(dark = scheduledDark.value))
```

`shell/ui/CulveryShell.kt`: add the parameter `now: () -> LocalDateTime,` after `state`, and pass it: `StatusBar(now, state.dark, state.previewing, state.session, onSignOut, onToggleThemePreview)` and `HomeScreen(now, state.homeCards, state.headerItems)` (import `java.time.LocalDateTime`).

`shell/ui/StatusBar.kt`: the parameter `now: LocalDateTime` becomes `now: () -> LocalDateTime`, and `Text(now.format(TIME), …)` becomes `Text(now().format(TIME), …)`.

`shell/ui/HomeScreen.kt`:
```kotlin
@Composable
fun HomeScreen(now: () -> LocalDateTime, placements: List<HomePlacement>, headerItems: List<HeaderItem>) {
```
and in `HomeHeader(now: () -> LocalDateTime, items: List<HeaderItem>, modifier: Modifier = Modifier)` the two texts read `now().format(CLOCK)` and `now().format(DATE)`.

`MainActivity.kt`, inside `setContent { … }` after `val state by …`:
```kotlin
            // Read inside the clocks only: a minute's tick recomposes them, not the shell (4c §4.2).
            val now = shell.now.collectAsStateWithLifecycle()
```
and the `CulveryShell(` call gets `now = { now.value },` after `state = state,`.

- [ ] **Step 7: Point the other tests at the new shapes**

- `ShellViewModelTest.noCapabilitiesGivesHomeOnlyAndTracksTheClock`: `vm().uiState.test { … }` keeps its body but `assertThat(s.now).isEqualTo(noon)` becomes `assertThat(vm.now.value).isEqualTo(noon)` (hold the view model in a `val vm = vm()` first).
- `ShellViewModelTest.sunTimesThatFailToLoadLeaveSevenAndSevenUntilTheRetryThenFollowTheSun`: `assertThat(it.now.toLocalTime()).isEqualTo(LocalTime.of(19, 10))` becomes `assertThat(vm.now.value.toLocalTime()).isEqualTo(LocalTime.of(19, 10))`.
- `ShellScreenshotTest`: drop `now = at` from every `ShellUiState(…)`; the `CulveryShell(` call in `snap` gets `now = { at },` after `state = state,`.

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, `aMinuteTickRedrawsTheClockAndNotTheCards` included.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences (nothing drawn changed).

- [ ] **Step 9: Commit**

```bash
git add core/ui app
git commit -m "Keep the theme's colour scheme across recompositions and let a minute's tick redraw only the clocks"
```

---

### Task 6: `HouseholdClock`, one time source (§4.3, P7, C6; ruling 24)

**Review:** sonnet.

**Files:**
- Modify: `core/plugin/build.gradle.kts`
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/HouseholdClock.kt`
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Runtime.kt`
- Test: `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/HouseholdClockTest.kt` (create); `WallTimeTest.kt` (delete: its two cases move into `HouseholdClockTest`)
- Delete: `app/src/main/java/uk/co/siland/culvery/shell/MinuteTicker.kt`; `app/src/test/java/uk/co/siland/culvery/shell/HouseholdTickerTest.kt` (its cases move into `HouseholdClockTest`)
- Modify: `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`, `shell/ShellViewModel.kt`; test `shell/ShellViewModelTest.kt`
- Modify: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherRepository.kt`, `WeatherCapability.kt`, `ui/ForecastCard.kt`; tests `WeatherCapabilityTest.kt`, `ui/ForecastCardTest.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`, `CalendarSetupSteps.kt`, `ui/Now.kt`, `ui/ReviewCalendars.kt`; tests `CalendarCapabilityTest.kt`, `ui/NowTest.kt`, `ui/CardHostsMidnightRolloverTest.kt`

**Interfaces:**
- Consumes: `HouseholdZone.zone: Flow<ZoneId>` (`:core:household`); `WallClock`; `@ApplicationScope CoroutineScope`; `retryWithBackoff`.
- Produces (package `uk.co.siland.culvery.core.plugin`):
  - `@Singleton class HouseholdClock(zones: Flow<ZoneId>, wall: WallClock, scope: CoroutineScope, ticks: Flow<Long> = minuteTicks(wall))` with `@Inject constructor(zone: HouseholdZone, wall: WallClock, @ApplicationScope scope: CoroutineScope)`; members `val now: StateFlow<LocalDateTime?>`, `val minutes: Flow<LocalDateTime>`, `val today: Flow<LocalDate>`, `fun nowMillis(): Long`. `ticks` are the epoch millis to show: by default now, then each minute's start.
  - `@Composable fun rememberNowMillis(clock: HouseholdClock): Long` (replaces the `WallClock` + `ticks` one). `nowTicks`, `wallTimeEachMinute` and `MinuteTicker` go (plan review 16).
  - `ShellViewModel(capabilities, clock: HouseholdClock, access, daylight)`.
  - `:capability:calendar` `internal fun rememberToday(clock: HouseholdClock): LocalDate?` — null until the household's zone is read, never the device's (plan review 16); `CalendarCapability(repo, clock: HouseholdClock, editor, connections, review)`; `ReviewCalendarsStep(…, clock: HouseholdClock)`, `CalendarsPage(…, clock: HouseholdClock)`, `ReviewCalendarsHost(…, clock: HouseholdClock, …)`.
  - `:capability:weather` `WeatherCapability(repo, clock: HouseholdClock)`; `ForecastCardHost(repo: WeatherRepository, clock: HouseholdClock)`.

- [ ] **Step 1: Write the failing clock test**

Create `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/HouseholdClockTest.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

/** Robolectric for android.util.Log and Room. */
@RunWith(AndroidJUnit4::class)
class HouseholdClockTest {
    private val auckland = ZoneId.of("Pacific/Auckland")
    private val london = ZoneId.of("Europe/London")

    // 12:00 UTC on 1 October 2026: 01:00 the next day in Wellington.
    private val noonUtc = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli()
    private val wall = WallClock { noonUtc }

    // 09:59:30 UTC on 1 October 2026: 10:59:30 in London (BST), 22:59:30 in Auckland (NZDT).
    private val beforeTheMinute = Instant.parse("2026-10-01T09:59:30Z").toEpochMilli()

    @Test
    fun itGivesTheTimeNowThenAtTheStartOfEachMinute() = runTest {
        val clock = HouseholdClock(flowOf(london), WallClock { beforeTheMinute + testScheduler.currentTime }, backgroundScope)
        assertThat(clock.minutes.take(3).toList()).containsExactly(
            LocalDateTime.of(2026, 10, 1, 10, 59, 30),
            LocalDateTime.of(2026, 10, 1, 11, 0),
            LocalDateTime.of(2026, 10, 1, 11, 1),
        ).inOrder()
    }

    @Test
    fun aZoneChangeGivesTheTimeInTheNewZoneAtOnce() = runTest {
        val zones = MutableStateFlow(london)
        val clock = HouseholdClock(zones, WallClock { beforeTheMinute }, backgroundScope, flowOf(beforeTheMinute))
        clock.minutes.test {
            assertThat(awaitItem()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 59, 30))
            zones.value = auckland
            assertThat(awaitItem()).isEqualTo(LocalDateTime.of(2026, 10, 1, 22, 59, 30))
        }
    }

    /** The shell's clock, date and theme follow the household's zone, read from Room, not the tablet's. */
    @Test
    fun itShowsTheHouseholdsTimeNotTheDevices() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java).build()
        try {
            val household = HouseholdRepository(db)
            household.setLocation(HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland"))
            val clock = HouseholdClock(HouseholdZone(household), wall, backgroundScope)
            // Room answers on its own threads: wait in bounded real time (4b test health).
            val first = withContext(Dispatchers.Default) { withTimeout(5_000) { clock.minutes.first() } }
            assertThat(first).isEqualTo(LocalDateTime.of(2026, 10, 2, 1, 0))
        } finally {
            db.close()
        }
    }

    @Test
    fun everyReaderSharesOneTicker() = runTest {
        var tickers = 0
        val ticks = flow {
            tickers++
            emit(noonUtc)
            awaitCancellation()
        }
        val clock = HouseholdClock(flowOf(auckland), wall, backgroundScope, ticks)
        repeat(3) { backgroundScope.launch { clock.minutes.collect {} } }
        backgroundScope.launch { clock.today.collect {} }
        runCurrent()
        assertThat(tickers).isEqualTo(1)
    }

    @Test
    fun todayChangesAtMidnightAndNotEachMinute() = runTest {
        val at = { t: LocalDateTime -> t.atZone(london).toInstant().toEpochMilli() }
        val ticks = MutableStateFlow(at(LocalDateTime.of(2026, 9, 23, 23, 59)))
        val clock = HouseholdClock(flowOf(london), wall, backgroundScope, ticks)
        clock.today.test {
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 9, 23))
            ticks.value = at(LocalDateTime.of(2026, 9, 24, 0, 0))
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 9, 24))
            ticks.value = at(LocalDateTime.of(2026, 9, 24, 0, 1))
            expectNoEvents()
        }
    }

    /** 4b plan review 1: the zone comes from Room, which can fail; the clock carries on. */
    @Test
    fun aFailedZoneReadIsRetriedAndTheClockCarriesOn() = runTest {
        ShadowLog.clear()
        var reads = 0
        val zones = flow {
            if (reads++ == 0) throw IllegalStateException("database locked")
            emit(auckland)
        }
        val clock = HouseholdClock(zones, wall, backgroundScope, flowOf(noonUtc))
        assertThat(clock.minutes.first()).isEqualTo(LocalDateTime.of(2026, 10, 2, 1, 0))
        assertThat(ShadowLog.getLogs().map { it.msg })
            .contains("Couldn't read the household's time zone (IllegalStateException); retrying")
    }
}
```
Delete `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/WallTimeTest.kt` (its two cases are the first two above).

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:plugin:testDebugUnitTest --tests "*HouseholdClockTest*"`
Expected: FAIL to compile with "Unresolved reference 'HouseholdClock'".

- [ ] **Step 3: Write `HouseholdClock`**

`core/plugin/build.gradle.kts`, in `dependencies`, add `implementation(project(":core:household"))` (core → core, allowed).

Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/HouseholdClock.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.household.HouseholdZone

/**
 * The household's wall time (4c design §4.3): one minute ticker for the whole app, in the household's zone. The shell's
 * clocks and theme, the weather and the calendar's "today" all read it. [ticks] are the epoch millis to show; by
 * default now, then each minute's start. Every zone in use is offset by whole minutes, so an epoch minute is a minute
 * on its clock.
 */
@Singleton
class HouseholdClock(
    zones: Flow<ZoneId>,
    private val wall: WallClock,
    scope: CoroutineScope,
    ticks: Flow<Long> = minuteTicks(wall),
) {
    @Inject
    constructor(zone: HouseholdZone, wall: WallClock, @ApplicationScope scope: CoroutineScope) : this(zone.zone, wall, scope)

    /** Now, then at each minute's start and at once when the zone changes; null until the zone is first read. */
    val now: StateFlow<LocalDateTime?> =
        combine(zones.retryWithBackoff { Log.w(TAG, "Couldn't read the household's time zone (${it::class.simpleName}); retrying") }, ticks) { zone, millis ->
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val minutes: Flow<LocalDateTime> = now.filterNotNull()

    /** Today's date, changing only at midnight. */
    val today: Flow<LocalDate> = minutes.map { it.toLocalDate() }.distinctUntilChanged()

    fun nowMillis(): Long = wall.nowMillis()

    private companion object {
        const val TAG = "HouseholdClock"
    }
}
```

`Runtime.kt`: keep `WallClock`, `ApplicationScope` and `MINUTE_MS`; replace everything below `MINUTE_MS` (`wallTimeEachMinute`, `minuteTicks`, `NOW_TICK_MS`, `nowTicks`, the old `rememberNowMillis`) with:
```kotlin
/** Now, then each minute's start, as epoch millis. */
internal fun minuteTicks(clock: WallClock): Flow<Long> = flow {
    while (true) {
        val now = clock.nowMillis()
        emit(now)
        delay(MINUTE_MS - Math.floorMod(now, MINUTE_MS))
    }
}

/** Wall-clock millis, read again at each of the household clock's minutes (4c design §4.3). */
@Composable
fun rememberNowMillis(clock: HouseholdClock): Long {
    val now by produceState(clock.nowMillis(), clock) {
        clock.minutes.collect { value = clock.nowMillis() }
    }
    return now
}
```
(keep only the imports the file still uses.)

Run: `./gradlew :core:plugin:testDebugUnitTest`
Expected: PASS (`HouseholdClockTest` 7, `FlowRetryTest`, `FirstDrawTest`, `HomeCardPlacerTest`).

- [ ] **Step 4: The shell reads it, with no pass-through**

Delete `app/src/main/java/uk/co/siland/culvery/shell/MinuteTicker.kt` and `app/src/test/java/uk/co/siland/culvery/shell/HouseholdTickerTest.kt`. In `di/AppModule.kt` delete the `minuteTicker` provider and its imports (`HouseholdZone`, `MinuteTicker`, `householdTicker`).

`shell/ShellViewModel.kt`: the constructor's `ticker: MinuteTicker,` becomes `clock: HouseholdClock,` (import `uk.co.siland.culvery.core.plugin.HouseholdClock`), and
```kotlin
    val now: StateFlow<LocalDateTime> =
        clock.minutes.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())
```
(its KDoc from Task 5 stays.)

`ShellViewModelTest.kt`: the clock is a `HouseholdClock` over test ticks in London:
```kotlin
    private val london = ZoneId.of("Europe/London")
    private val clockScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private fun at(time: LocalDateTime): Long = time.atZone(london).toInstant().toEpochMilli()
    private val ticks = MutableStateFlow(at(noon))
    private val clock = HouseholdClock(flowOf(london), WallClock { ticks.value }, clockScope, ticks)

    @After
    fun tearDown() = clockScope.cancel()

    private fun vm(caps: Set<Capability> = emptySet(), daylight: Daylight? = null) =
        ShellViewModel(caps, clock, access, Optional.ofNullable(daylight))
```
(replacing the old `ticks` field and `vm`); `ShellViewModel(emptySet(), { ticks }, noSessionAccess, Optional.empty())` becomes `ShellViewModel(emptySet(), clock, noSessionAccess, Optional.empty())`; and every `ticks.value = X` becomes `ticks.value = at(X)` (e.g. `ticks.value = at(noon.with(LocalTime.of(19, 10)))`). Imports as needed.

- [ ] **Step 5: Weather reads it**

`WeatherRepository.kt`, the injected constructor becomes
```kotlin
    @Inject
    constructor(household: HouseholdRepository, store: WeatherStore, clock: HouseholdClock) :
        this(household.location, store.stored, clock.minutes)
```
(imports: `HouseholdClock`; drop `HouseholdZone`, `WallClock`, `wallTimeEachMinute`.)
`WeatherCapability.kt`: `private val clock: WallClock` → `private val clock: HouseholdClock` (import swap).
`ui/ForecastCard.kt`, the host becomes:
```kotlin
/** The card over the repository: nothing until the first view; the age line moves on each minute. */
@Composable
internal fun ForecastCardHost(repo: WeatherRepository, clock: HouseholdClock) {
    val view by repo.view.collectAsState(initial = null)
    val nowMillis = rememberNowMillis(clock)
    view?.let { ForecastCard(it, nowMillis) }
}
```
(drop the `Flow`, `WallClock` and `nowTicks` imports.)

Tests: `WeatherCapabilityTest`: `WallClock { 0L }` → `HouseholdClock(flowOf(ZoneId.of("Europe/London")), WallClock { 0L }, clockScope)`, with `private val clockScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)` declared before it and `@After fun tearDown() = clockScope.cancel()`. `ForecastCardTest.theHostMovesTheAgeLineOnWithTheClock`: replace `val clock = WallClock { now }` and `val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)` with
```kotlin
        val ticks = MutableStateFlow(now)
        val clock = HouseholdClock(flowOf(ZoneId.of("Europe/London")), WallClock { now }, clockScope, ticks)
```
`ForecastCardHost(repo, clock, ticks)` → `ForecastCardHost(repo, clock)`, and `ticks.tryEmit(Unit)` → `ticks.value = now`; give the class the same `clockScope` field and `@After` cancel.

Run: `./gradlew :capability:weather:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: The calendar reads it**

`ui/Now.kt` becomes:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.time.LocalDate
import uk.co.siland.culvery.core.plugin.HouseholdClock

/**
 * Today in the household's zone, or null until that zone is read (never the device's, which can be a different day);
 * it changes only at midnight, so a card keyed on it rebuilds once a day (4c §4.3, C6).
 */
@Composable
internal fun rememberToday(clock: HouseholdClock): LocalDate? {
    val today by clock.today.collectAsState(initial = clock.now.value?.toLocalDate())
    return today
}
```
`CalendarCapability.kt`: the constructor's `private val zone: HouseholdZone, private val clock: WallClock,` become `private val clock: HouseholdClock,`; the cards draw once today is known:
```kotlin
                HomeCard(TODAY_CARD_ID, HomeCardSize.TALL, 100) { rememberToday(clock)?.let { TodayCardHost(repo, editor, it) } },
                HomeCard(COMING_UP_CARD_ID, HomeCardSize.WIDE, 50) { rememberToday(clock)?.let { ComingUpCardHost(repo, it) } },
```
and `TabContent`:
```kotlin
    @Composable
    override fun TabContent() {
        val connector = rememberConnector(connections)
        val today = rememberToday(clock) ?: return
        WeekViewHost(repo, editor, today = today, nowMillis = rememberNowMillis(clock), onReconnect = connector::reconnect)
    }
```
(drop the `rememberZoneId`, `todayIn`, `HouseholdZone`, `WallClock` imports; import `HouseholdClock`.)
`CalendarSetupSteps.kt`: in `ReviewCalendarsStep` and `CalendarsPage`, `clock: WallClock` → `clock: HouseholdClock`. `ui/ReviewCalendars.kt`: `ReviewCalendarsHost(…, clock: WallClock, …)` → `clock: HouseholdClock`; `nowMillis = rememberNowMillis(clock)` stays.

Tests:
- `CalendarCapabilityTest.setUp`: `CalendarCapability(CalendarRepository(…), zone, WallClock { 0L }, …)` → `CalendarCapability(CalendarRepository(…), HouseholdClock(zone.zone, WallClock { 0L }, clockScope), …)`, with a `clockScope` field as in Step 5, cancelled in `tearDown`.
- `ui/NowTest.kt` becomes:
```kotlin
@RunWith(AndroidJUnit4::class)
class NowTest {
    @get:Rule val compose = createComposeRule()
    private val london = ZoneId.of("Europe/London")
    private val clockScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = clockScope.cancel()

    @Test
    fun todayRollsOverAtMidnight() {
        var now = LocalDateTime.of(2026, 9, 23, 23, 59, 50).atZone(london).toInstant().toEpochMilli()
        val ticks = MutableStateFlow(now)
        val clock = HouseholdClock(flowOf(london), WallClock { now }, clockScope, ticks)
        compose.setContent { Text(rememberToday(clock).toString()) }
        compose.onNodeWithText("2026-09-23").assertExists()

        now += 20_000 // 00:00:10 on the 24th
        ticks.value = now
        compose.waitForIdle()
        compose.onNodeWithText("2026-09-24").assertExists()
    }

    @Test
    fun untilTheZoneIsReadThereIsNoToday() {
        val clock = HouseholdClock(flow { awaitCancellation() }, WallClock { 0L }, clockScope, flowOf(0L))
        compose.setContent { Text(rememberToday(clock).toString()) }
        compose.onNodeWithText("null").assertExists()
    }
}
```
- `ui/CardHostsMidnightRolloverTest.kt`, in both tests: replace `val clock = WallClock { now }` and `val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)` with
```kotlin
        val ticks = MutableStateFlow(now)
        val clock = HouseholdClock(zone.zone, WallClock { now }, clockScope, ticks)
```
`TodayCardHost(repo, editor, rememberToday(zone, clock, ticks))` → `rememberToday(clock)?.let { TodayCardHost(repo, editor, it) }`; `val today = rememberToday(zone, clock, ticks)` → `val today = rememberToday(clock) ?: return@CulveryTheme`; `rememberNowMillis(clock, ticks)` → `rememberNowMillis(clock)`; `ticks.tryEmit(Unit)` → `ticks.value = now`; add the `clockScope` field, cancelled in `tearDown`.

Check nothing names the old shapes:
```bash
git grep -n "nowTicks\|householdTicker\|rememberZoneId\|wallTimeEachMinute\|MinuteTicker\|todayIn(" -- '*.kt'
```
Expected: no output.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 8: Commit**

```bash
git add core/plugin app capability
git commit -m "Give the app one household clock: the shell, the weather and the calendar's today share its minute"
```

---

### Task 7: Calendar flows — `distinctUntilChanged`, the stability file, `flowOn` (§4.4, P4, P5, U7; ruling 20)

**Review:** sonnet.

**Files:**
- Create: `compose-stability.conf`
- Modify: `build-logic/convention/src/main/kotlin/AndroidComposeConventionPlugin.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarRepository.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `CalendarRepository.days(start, count)` and `week(start)` emit only when what they show changes, and map on `Dispatchers.Default`. Every Compose module reads `compose-stability.conf`.

- [ ] **Step 1: Write the failing test**

In `CalendarRepositoryTest.kt` add (imports `app.cash.turbine.test`):
```kotlin
    /** P4: a pass that only records its time (markSynced) changes nothing on screen, so nothing is re-sent. */
    @Test
    fun aPassThatOnlyMarksTheSyncTimeSendsNothing() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        repo.days(sept(23), 1).test {
            assertThat(awaitItem().single().events.map { it.title }).containsExactly("Boiler service")
            store.markSynced("c1", 5_000L)
            put("s-family", timed("Boiler service", 23, 10, 0, 60), timed("Swim", 23, 16, 0, 60))
            // An equal list from the sync time's write would arrive first and fail this.
            assertThat(awaitItem().single().events.map { it.title }).containsExactly("Boiler service", "Swim").inOrder()
        }
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarRepositoryTest.aPassThatOnlyMarksTheSyncTimeSendsNothing*"`
Expected: FAIL: the second item is the unchanged `[Boiler service]`.

- [ ] **Step 3: Send only changes, and map off Main**

In `CalendarRepository.kt`, `days()` ends with the two operators after the `flatMapLatest { … }` block:
```kotlin
    @OptIn(ExperimentalCoroutinesApi::class)
    fun days(start: LocalDate, count: Int): Flow<List<DayUi>> = zone.zone.flatMapLatest { z ->
        // … the existing body, unchanged …
    }
        // 4c §4.4: a pass that only records its time changes nothing on screen (P4), and the mapping runs off Main (U7).
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun week(start: LocalDate): Flow<WeekUi> =
        combine(days(start, 7), household.people) { days, people -> WeekUi(start, days, people + Person.Family) }.distinctUntilChanged()
```
(imports `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.flow.flowOn`.)

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: The stability file**

Create `compose-stability.conf` at the repo root:
```text
// Types the Compose compiler can't see into but that never change once made (4c design §4.4).
java.time.*
uk.co.siland.culvery.core.household.Person
uk.co.siland.culvery.core.household.PersonId
```
`build-logic/convention/src/main/kotlin/AndroidComposeConventionPlugin.kt`, after `android.buildFeatures.compose = true` add:
```kotlin
        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
        }
```
(imports `org.gradle.kotlin.dsl.configure`, `org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension`. Use `stabilityConfigurationFiles`, not the deprecated `stabilityConfigurationFile`.)

Run: `./gradlew :capability:calendar:compileDebugKotlin --rerun-tasks`
Expected: `BUILD SUCCESSFUL` with no deprecation warning from the build script.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` and `./gradlew -p build-logic :convention:test`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 7: Commit**

```bash
git add compose-stability.conf build-logic capability/calendar
git commit -m "Send calendar days only when they change, map them off the main thread, and mark java.time and Person stable"
```

---

### Task 8: The lockout — the clock guard and no disk I/O on Main (§5.4 K3, K4; ruling 26)

**Review:** opus (the lockout).

**Files:**
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/LockoutStore.kt`
- Test: `core/access/src/test/java/uk/co/siland/culvery/core/access/LockoutStoreTest.kt`; `DefaultAccessControlTest.kt` where it calls the store directly

**Interfaces:**
- Consumes: nothing new.
- Produces: `LockoutStore`'s `lockedUntil`, `recordFailure`, `reset` are `suspend`; `const val LockoutStore.MAX_LOCK_MS = BASE_LOCK_MS shl MAX_DOUBLINGS` (16 minutes).

- [ ] **Step 1: Write the failing lockout tests**

`core/access/src/test/java/uk/co/siland/culvery/core/access/LockoutStoreTest.kt`: every test body becomes `= runTest { … }` (import `kotlinx.coroutines.test.runTest`; the store's methods become `suspend`). `lockDurationIsCappedAtSixteenMinutes` asserts against the constant, and two tests join it:
```kotlin
    @Test
    fun lockDurationIsCappedAtSixteenMinutes() = runTest {
        repeat(100) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + LockoutStore.MAX_LOCK_MS)
    }

    /** The longest lock, exactly 16 minutes away, is a real lock: the guard below is "more than", not "at least". */
    @Test
    fun tenFailuresLockForTheLongestTime() = runTest {
        repeat(10) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + LockoutStore.MAX_LOCK_MS)
    }

    /** K3: a lock more than 16 minutes away means the wall clock went back; it has expired. */
    @Test
    fun aLockFurtherAwayThanTheLongestIsExpired() = runTest {
        repeat(5) { store.recordFailure(t0) }
        val clockWentBack = t0 - 20 * 60_000L
        assertThat(store.lockedUntil(clockWentBack)).isNull()
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + 30_000)
    }
```
Run: `./gradlew :core:access:testDebugUnitTest --tests "*LockoutStoreTest*"`
Expected: FAIL to compile ("Unresolved reference 'MAX_LOCK_MS'").

- [ ] **Step 2: The store in memory, written in the background, with the clock guard**

`LockoutStore.kt` becomes:
```kotlin
package uk.co.siland.culvery.core.access

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Consecutive-failure counter, in SharedPreferences so it survives the app being killed. Read once, off the main thread,
 * then kept in memory; each change is written in the background (4c K4).
 */
@Singleton
class LockoutStore internal constructor(private val open: () -> SharedPreferences, private val io: CoroutineContext) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }, Dispatchers.IO)

    private class Counts(val failures: Int, val until: Long)

    private val loading = Mutex()
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var counts: Counts? = null

    /** When the pad unlocks, or null. A lock ending more than [MAX_LOCK_MS] away means the clock went back: expired (4c K3). */
    suspend fun lockedUntil(nowMillis: Long): Long? =
        load().until.takeIf { it > nowMillis && it - nowMillis <= MAX_LOCK_MS }

    suspend fun recordFailure(nowMillis: Long) {
        val current = load()
        val failures = current.failures + 1
        val until = if (failures >= FREE_ATTEMPTS) {
            nowMillis + (BASE_LOCK_MS shl minOf(failures - FREE_ATTEMPTS, MAX_DOUBLINGS))
        } else {
            current.until
        }
        write(Counts(failures, until))
    }

    suspend fun reset() {
        load()
        write(Counts(0, 0L))
    }

    private suspend fun load(): Counts = counts ?: loading.withLock {
        counts ?: withContext(io) {
            val file = open().also { prefs = it }
            Counts(file.getInt(KEY_FAILURES, 0), file.getLong(KEY_UNTIL, 0L))
        }.also { counts = it }
    }

    private fun write(next: Counts) {
        counts = next
        checkNotNull(prefs).edit().putInt(KEY_FAILURES, next.failures).putLong(KEY_UNTIL, next.until).apply()
    }

    companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_DOUBLINGS = 5

        /** The longest lock: 16 minutes. */
        const val MAX_LOCK_MS = BASE_LOCK_MS shl MAX_DOUBLINGS

        private const val FILE = "lockout"
        private const val KEY_FAILURES = "failures"
        private const val KEY_UNTIL = "lockedUntil"
    }
}
```
`DefaultAccessControl` already calls these from `suspend` code; nothing changes there. In `DefaultAccessControlTest`, any direct call to `lockout.recordFailure(…)`, `lockout.lockedUntil(…)` or `lockout.reset()` outside a coroutine moves into the test's `runTest` body.

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: PASS (`LockoutStoreTest` 8; `DefaultAccessControlTest` unchanged in what it checks).

- [ ] **Step 3: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 4: Commit**

```bash
git add core/access
git commit -m "Keep the PIN lockout in memory, write it in the background, and expire a lock the clock moved too far ahead"
```

---

### Task 9: The home app, Exit kiosk in front, device owner, K1 and K2 (§5.1, §5.2, §5.4 K1–K2, D3; rulings 6, 15, 27, 28)

**Review:** opus (lock-task state over the activity's lifecycle).

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/HomeApp.kt`
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/ShellNavigator.kt`
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/pages/KioskPage.kt`, `steps/DoneStep.kt`, `SetupUi.kt`, `SetupCopy.kt`, `SetupDimens.kt`
- Test: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestUi.kt`, `SettingsScreenTest.kt`, `SettingsScreenshotTest.kt`, `SetupScreenshotTest.kt`, `StepsTest.kt`, `StepsUiTest.kt`; `KioskPageTest.kt` (create)
- Create: `app/src/main/java/uk/co/siland/culvery/CulveryDeviceAdmin.kt`, `HomeApps.kt`, `KioskLifecycle.kt`, `app/src/main/res/xml/device_admin.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `MainActivity.kt`, `shell/ShellViewModel.kt`, `di/AppModule.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/HomeAppsTest.kt`, `KioskLifecycleTest.kt` (create), `shell/ShellViewModelTest.kt`, `shell/ui/NoNavigation.kt`
- Modify (navigator fakes): `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/RecordingNavigator.kt`, `ui/ReviewScreenshotTest.kt`; `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/RecordingNavigator.kt`
- Modify: `docs/setup/release.md`
- Screenshots (re-recorded): `core/setup/src/test/screenshots/settings_kiosk_dark.png`, `settings_kiosk_light.png`, `done_dark.png`, `done_light.png`

**Interfaces:**
- Consumes: `CorePermissions.KIOSK_EXIT`, `SETTINGS_MANAGE`; `shouldPin`, `pinOnSetupRead` (`SetupWiring.kt`).
- Produces:
  - `interface HomeApp { val isDefault: StateFlow<Boolean> }` (`:core:plugin`); `:app`'s `@Singleton class AndroidHomeApp @Inject constructor(@ApplicationContext context: Context) : HomeApp { fun refresh() }`, bound in `AppModule`.
  - `ShellNavigator.chooseHomeApp()` and `ShellNavigator.changeHomeApp()`.
  - `enum class HomeAppRequest { CHOOSE, CHANGE }`; `ShellViewModel.homeAppRequests: Flow<HomeAppRequest>`, `ShellViewModel.kioskExited: Boolean` (read-only), `ShellViewModel.returnedToFront()`.
  - `internal fun allowLockTaskIfOwner(context: Context): Boolean` (`HomeApps.kt`): as device owner, allowlists Culvery and Google Play services (`GMS_PACKAGE`) for lock-task; `class CulveryDeviceAdmin : DeviceAdminReceiver`. Task 10 reads the result.
  - `internal interface KioskWindow`, `internal class KioskLifecycle(window, setupComplete, isHomeApp, kioskExited, returnedToFront) : DefaultLifecycleObserver { fun exitKiosk() }` (`KioskLifecycle.kt`).
  - `:core:setup` `internal fun HomeAppPrompt(onChoose: () -> Unit)`; `KioskPage(homeApp: HomeApp)`; `DoneStep(state, access, gate, homeApp: HomeApp)`; test fake `FakeHomeApp(default: Boolean)`.

- [ ] **Step 1: The seams**

Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/HomeApp.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import kotlinx.coroutines.flow.StateFlow

/** Whether Culvery is the tablet's default home app (4c design D3, §5.1); `:app` checks again each time Culvery is in front. */
interface HomeApp {
    val isDefault: StateFlow<Boolean>
}
```
`ShellNavigator.kt`, add after `exitKiosk()`:
```kotlin

    /** The wizard's Done step and Settings › Kiosk (4c §5.1): asks Android to make Culvery the home app (the open session). */
    fun chooseHomeApp()

    /** Settings › Kiosk (4c §5.1): opens the home-app setting to go back to another launcher, after a fresh Admin PIN. */
    fun changeHomeApp()
```
Give every fake navigator the methods:
- `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/RecordingNavigator.kt`, `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/RecordingNavigator.kt` and `app/src/test/java/uk/co/siland/culvery/shell/ui/NoNavigation.kt`: `override fun chooseHomeApp() = Unit` and `override fun changeHomeApp() = Unit`.
- `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestUi.kt`'s `RecordingNavigator`: add `var homeAppChoices = 0`, `var homeAppChanges = 0`, `override fun chooseHomeApp() { homeAppChoices++ }`, `override fun changeHomeApp() { homeAppChanges++ }`; and add to the same file:
```kotlin
class FakeHomeApp(default: Boolean = false) : HomeApp {
    override val isDefault = MutableStateFlow(default)
}
```
- `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/ReviewScreenshotTest.kt` (it shows the Calendars page, not Kiosk): `KioskPage()` → `KioskPage(object : HomeApp { override val isDefault = MutableStateFlow(true) })` (imports `uk.co.siland.culvery.core.plugin.HomeApp`, `kotlinx.coroutines.flow.MutableStateFlow`); this compiles once Step 7 gives `KioskPage` its parameter.

- [ ] **Step 2: Write the failing view-model tests**

In `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt` add (import `uk.co.siland.culvery.shouldPin`):
```kotlin
    @Test
    fun exitKioskIsRememberedUntilCulveryIsInFrontAgain() = runTest {
        val vm = vm()
        access.result = admin
        vm.exitKiosk()
        runCurrent()
        assertThat(shouldPin(setupComplete = true, kioskExited = vm.kioskExited)).isFalse()
        vm.returnedToFront()
        assertThat(shouldPin(setupComplete = true, kioskExited = vm.kioskExited)).isTrue()
    }

    @Test
    fun changeHomeAppAsksForAFreshPinClosesSettingsAndSignsOut() = runTest {
        val vm = vm()
        access.result = admin
        vm.openSettings()
        runCurrent()
        vm.homeAppRequests.test {
            vm.changeHomeApp()
            assertThat(awaitItem()).isEqualTo(HomeAppRequest.CHANGE)
        }
        assertThat(access.requested.last()).containsExactly(CorePermissions.KIOSK_EXIT)
        assertThat(access.session.value).isNull()
        assertThat(vm.kioskExited).isTrue()
        vm.uiState.test { assertThat(expectMostRecentItem().settingsOpen).isFalse() }
    }

    @Test
    fun chooseHomeAppUsesTheOpenSession() = runTest {
        val vm = vm()
        access.result = admin
        vm.homeAppRequests.test {
            vm.chooseHomeApp()
            assertThat(awaitItem()).isEqualTo(HomeAppRequest.CHOOSE)
        }
        assertThat(access.requested.last()).containsExactly(CorePermissions.SETTINGS_MANAGE)
        assertThat(access.session.value).isNotNull()
        assertThat(vm.kioskExited).isFalse()
    }

    @Test
    fun aRefusedPinAsksAndroidNothing() = runTest {
        val vm = vm()
        vm.homeAppRequests.test {
            vm.changeHomeApp()
            vm.chooseHomeApp()
            expectNoEvents()
        }
    }
```
Run: `./gradlew :app:testDebugUnitTest --tests "*ShellViewModelTest*"`
Expected: FAIL to compile (`kioskExited`, `changeHomeApp`, `HomeAppRequest` unresolved).

- [ ] **Step 3: The view model's half**

`shell/ShellViewModel.kt`:
- above the class:
```kotlin
/** What Settings › Kiosk or the Done step asked of Android (4c §5.1). */
enum class HomeAppRequest { CHOOSE, CHANGE }
```
- after `val kioskExit: Flow<Unit> = kioskExitEvents.receiveAsFlow()` add
```kotlin
    private val homeAppEvents = Channel<HomeAppRequest>(Channel.BUFFERED)

    /** Each request to Android about the home app; MainActivity unpins first, as nothing opens over a pinned app. */
    val homeAppRequests: Flow<HomeAppRequest> = homeAppEvents.receiveAsFlow()

    /**
     * Exit kiosk or Change home app, and Culvery not back in front since (4c K2). A field, not saved state: a
     * configuration change keeps it, a process death forgets it, so the kiosk pins again when next in front (ruling 6).
     */
    var kioskExited: Boolean = false
        private set
```
- replace `exitKiosk()` and add the rest:
```kotlin
    override fun exitKiosk() {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.KIOSK_EXIT) != null) {
                leaveKiosk()
                kioskExitEvents.send(Unit)
            }
        }
    }

    override fun chooseHomeApp() {
        viewModelScope.launch {
            access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return@launch
            homeAppEvents.send(HomeAppRequest.CHOOSE)
        }
    }

    /** Ruling 15: kiosk.exit's fresh PIN (its pad says "Exit kiosk mode", which is what this does). */
    override fun changeHomeApp() {
        viewModelScope.launch {
            access.authorise(CorePermissions.KIOSK_EXIT) ?: return@launch
            leaveKiosk()
            homeAppEvents.send(HomeAppRequest.CHANGE)
        }
    }

    /** Culvery is in front again after being stopped; with D3 that includes a Home press from another app. */
    fun returnedToFront() {
        kioskExited = false
    }

    private fun leaveKiosk() {
        kioskExited = true
        settingsOpen.value = false
        access.lock()
    }
```
Run the test class again.
Expected: PASS.

- [ ] **Step 4: Write the failing activity-level tests (Review Focus 2 and 5)**

Create `app/src/test/java/uk/co/siland/culvery/KioskLifecycleTest.kt`:
```kotlin
package uk.co.siland.culvery

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** The kiosk's pinning over a real activity lifecycle (Robolectric drives it), with the window's effects recorded. */
@RunWith(AndroidJUnit4::class)
class KioskLifecycleTest {
    private class RecordingWindow : KioskWindow {
        val calls = mutableListOf<String>()
        override fun pin() { calls += "pin" }
        override fun unpin() { calls += "unpin" }
        override fun hideBars() { calls += "hideBars" }
        override fun showBars() { calls += "showBars" }
        override fun moveToBack() { calls += "moveToBack" }
    }

    private val window = RecordingWindow()
    private var exited = false
    private var home = true
    private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
    private val kiosk = KioskLifecycle(
        window,
        setupComplete = { true },
        isHomeApp = { home },
        kioskExited = { exited },
        returnedToFront = { exited = false },
    )

    @Before
    fun setUp() {
        controller.get().lifecycle.addObserver(kiosk)
        controller.setup()
    }

    /** Review Focus 2: a fresh start (after a process death, say) remembers no "exited", so it pins. */
    @Test
    fun aFreshStartPins() {
        assertThat(window.calls).contains("pin")
    }

    /** Review Focus 5, D3: as the home app, Exit kiosk stays in front, and a Home press doesn't pin it again. */
    @Test
    fun exitKioskAsTheHomeAppStaysInFrontUnpinnedThroughAHomePress() {
        window.calls.clear()
        exited = true
        kiosk.exitKiosk()
        assertThat(window.calls).containsExactly("unpin", "showBars").inOrder()
        // Home pressed while Culvery is home and in front: paused and resumed, never stopped.
        controller.pause().resume()
        assertThat(window.calls).doesNotContain("pin")
    }

    @Test
    fun leavingAfterExitKioskAndComingBackPinsAgain() {
        exited = true
        kiosk.exitKiosk()
        window.calls.clear()
        controller.pause().stop().start().resume()
        assertThat(window.calls).contains("pin")
    }

    @Test
    fun withAnotherHomeAppExitKioskMovesToTheBack() {
        home = false
        exited = true
        window.calls.clear()
        kiosk.exitKiosk()
        assertThat(window.calls).containsExactly("unpin", "showBars", "moveToBack").inOrder()
    }
}
```
Create `app/src/test/java/uk/co/siland/culvery/HomeAppsTest.kt`:
```kotlin
package uk.co.siland.culvery

import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class HomeAppsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val policies = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, CulveryDeviceAdmin::class.java)

    /** §5.2: as device owner Culvery allowlists itself, and Play services so Google's chooser opens inside lock-task. */
    @Test
    fun asDeviceOwnerCulveryAndPlayServicesMayRunInLockTask() {
        shadowOf(policies).setDeviceOwner(admin)
        assertThat(allowLockTaskIfOwner(context)).isTrue()
        assertThat(policies.getLockTaskPackages(admin).toList()).containsExactly(context.packageName, GMS_PACKAGE)
    }

    @Test
    fun otherwiseScreenPinningIsLeftAsItIs() {
        assertThat(allowLockTaskIfOwner(context)).isFalse()
    }

    @Test
    fun theHomeRoleIsReadAgainOnRefresh() {
        val roles = shadowOf(context.getSystemService(RoleManager::class.java))
        roles.addAvailableRole(RoleManager.ROLE_HOME)
        val home = AndroidHomeApp(context)
        assertThat(home.isDefault.value).isFalse()
        roles.addHeldRole(RoleManager.ROLE_HOME)
        home.refresh()
        assertThat(home.isDefault.value).isTrue()
    }
}
```
Run: `./gradlew :app:testDebugUnitTest --tests "*KioskLifecycleTest*" --tests "*HomeAppsTest*"`
Expected: FAIL to compile (`KioskLifecycle`, `KioskWindow`, `allowLockTaskIfOwner`, `GMS_PACKAGE`, `AndroidHomeApp`, `CulveryDeviceAdmin` unresolved).

- [ ] **Step 5: The kiosk's lifecycle, the home app and device owner**

Create `app/src/main/java/uk/co/siland/culvery/KioskLifecycle.kt`:
```kotlin
package uk.co.siland.culvery

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** What the kiosk does to the activity's window and task. */
internal interface KioskWindow {
    fun pin()

    fun unpin()

    fun hideBars()

    fun showBars()

    fun moveToBack()
}

/**
 * The kiosk over the activity's lifecycle (4a design D10, 4c §5.1, K2): every resume hides the bars and pins, once setup
 * is complete and the kiosk wasn't exited; coming back to the front after being stopped clears "exited".
 */
internal class KioskLifecycle(
    private val window: KioskWindow,
    private val setupComplete: () -> Boolean,
    private val isHomeApp: () -> Boolean,
    private val kioskExited: () -> Boolean,
    private val returnedToFront: () -> Unit,
) : DefaultLifecycleObserver {
    // A configuration change makes a new one, unstopped, so it doesn't count as coming back.
    private var stopped = false

    override fun onStop(owner: LifecycleOwner) {
        stopped = true
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!stopped) return
        stopped = false
        returnedToFront()
    }

    override fun onResume(owner: LifecycleOwner) {
        if (!kioskExited()) window.hideBars()
        if (shouldPin(setupComplete(), kioskExited())) window.pin()
    }

    /**
     * Exit kiosk (4c §5.1, ruling 28): unpinned, with the bars showing. As the home app Culvery stays in front: moving to
     * the back would resume it as home and pin it again at once. Otherwise it moves to the back, as before.
     */
    fun exitKiosk() {
        window.unpin()
        window.showBars()
        if (!isHomeApp()) window.moveToBack()
    }
}
```
Create `app/src/main/java/uk/co/siland/culvery/CulveryDeviceAdmin.kt`:
```kotlin
package uk.co.siland.culvery

import android.app.admin.DeviceAdminReceiver

/** Device owner for true lock-task (4c design §5.2): no policy beyond allowing lock-task. */
class CulveryDeviceAdmin : DeviceAdminReceiver()
```
Create `app/src/main/res/xml/device_admin.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 4c design §5.2: no policies; as device owner Culvery only allowlists apps for lock-task. -->
<device-admin>
    <uses-policies />
</device-admin>
```
Create `app/src/main/java/uk/co/siland/culvery/HomeApps.kt`:
```kotlin
package uk.co.siland.culvery

import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.siland.culvery.core.plugin.HomeApp

/** Google Play services: its account chooser and consent screens. */
internal const val GMS_PACKAGE = "com.google.android.gms"

/**
 * As device owner (4c design §5.2), Culvery allowlists itself, so startLockTask() is true lock-task (no prompt, no exit
 * gesture), and Play services, so Google's chooser opens inside it (plan review 13). False when not device owner.
 */
internal fun allowLockTaskIfOwner(context: Context): Boolean {
    val policies = context.getSystemService(DevicePolicyManager::class.java)
    if (!policies.isDeviceOwnerApp(context.packageName)) return false
    policies.setLockTaskPackages(ComponentName(context, CulveryDeviceAdmin::class.java), arrayOf(context.packageName, GMS_PACKAGE))
    return true
}

/** [HomeApp] through RoleManager (API 29+). The choice is made outside Culvery, so [refresh] runs on every resume. */
@Singleton
class AndroidHomeApp @Inject constructor(@ApplicationContext private val context: Context) : HomeApp {
    private val held = MutableStateFlow(check())
    override val isDefault: StateFlow<Boolean> = held.asStateFlow()

    fun refresh() {
        held.value = check()
    }

    private fun check(): Boolean {
        val roles = context.getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME)
    }
}
```
`di/AppModule.kt`, in the abstract class after `toaster`:
```kotlin
    @Binds
    abstract fun homeApp(impl: AndroidHomeApp): HomeApp
```
(imports `uk.co.siland.culvery.AndroidHomeApp`, `uk.co.siland.culvery.core.plugin.HomeApp`.)

`app/src/main/AndroidManifest.xml`: after the activity's existing `<intent-filter>` add
```xml
            <!-- 4c design D3: offered as the home app, so a reboot, power cut or crash comes back to Culvery once chosen. -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
```
and inside `<application>` after the activity:
```xml
        <receiver
            android:name=".CulveryDeviceAdmin"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
        </receiver>
```
Run: `./gradlew :app:testDebugUnitTest --tests "*KioskLifecycleTest*" --tests "*HomeAppsTest*"`
Expected: PASS (4 + 3).

- [ ] **Step 6: The activity**

`MainActivity.kt`:
- add `@Inject lateinit var homeApp: AndroidHomeApp`, and a field `internal var ownerAllowsGoogle = false` (Task 10 reads it);
- delete `private var kioskExited = false` and its comment, and the `onRestart` override; `pinOnSetupRead(previous, complete, resumed, kioskExited)` becomes `pinOnSetupRead(previous, complete, resumed, shell.kioskExited)`; `onWindowFocusChanged` reads `shell.kioskExited`;
- add the window and the lifecycle:
```kotlin
    // Not `window`: that is the Activity's own.
    private val kioskWindow = object : KioskWindow {
        override fun pin() = pinToScreen()

        override fun unpin() = unpinFromScreen()

        override fun hideBars() = hideSystemBars()

        override fun showBars() = showSystemBars()

        override fun moveToBack() {
            moveTaskToBack(true)
        }
    }

    private val kiosk by lazy {
        KioskLifecycle(
            kioskWindow,
            setupComplete = { setupComplete.value == true },
            isHomeApp = { homeApp.isDefault.value },
            kioskExited = { shell.kioskExited },
            returnedToFront = shell::returnedToFront,
        )
    }

    // Android's yes/no "make Culvery the home app?" dialog (4c §5.1); its answer is read again on resume anyway.
    private val askHomeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { homeApp.refresh() }
```
- in `onCreate`, after `setupComplete = …stateIn(…)` (Task 4 moved it up) add
```kotlin
        ownerAllowsGoogle = allowLockTaskIfOwner(this)
        lifecycle.addObserver(kiosk)
```
- the `repeatOnLifecycle(Lifecycle.State.STARTED)` block becomes:
```kotlin
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { shell.kioskExit.collect { kiosk.exitKiosk() } }
                launch { shell.homeAppRequests.collect(::openHomeAppScreen) }
            }
```
- add:
```kotlin
    /** Android's screens can't open over a pinned app; Culvery pins again when it is next in front (4c §5.1). */
    private fun openHomeAppScreen(request: HomeAppRequest) {
        unpinFromScreen()
        try {
            when (request) {
                HomeAppRequest.CHOOSE -> askHomeRole.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME))
                HomeAppRequest.CHANGE -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No screen to change the home app on this tablet (${e::class.simpleName})")
            shell.returnedToFront()
            if (shouldPin(setupComplete.value == true, shell.kioskExited)) pinToScreen()
        }
    }

    private companion object {
        const val TAG = "Culvery"
    }
```
- `onResume` becomes (the kiosk's own `onResume` runs right after it, as a lifecycle observer):
```kotlin
    override fun onResume() {
        super.onResume()
        homeApp.refresh()
    }
```
- the content root pads by the system bars (ruling 27): wrap the `ShellLayers(…) { … }` call in
```kotlin
                    // Zero while the bars are hidden (the pinned kiosk); after Exit kiosk the content clears them (K1).
                    Box(Modifier.fillMaxSize().background(Culvery.colors.bg).windowInsetsPadding(WindowInsets.systemBars)) {
                        ShellLayers(…)  // unchanged
                    }
```
(imports: `android.app.role.RoleManager`, `android.content.ActivityNotFoundException`, `android.content.Intent`, `android.provider.Settings`, `android.util.Log`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.WindowInsets`, `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.foundation.layout.systemBars`, `androidx.compose.foundation.layout.windowInsetsPadding`, `androidx.compose.ui.Modifier`, `uk.co.siland.culvery.core.ui.Culvery`, `uk.co.siland.culvery.shell.HomeAppRequest`.)

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Write the failing Settings and Done tests**

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/KioskPageTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class KioskPageTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val home = FakeHomeApp(default = false)

    private fun show() = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { Column { KioskPage(home).Content() } }
        }
    }

    @Test
    fun untilCulveryIsTheHomeAppItOffersToChooseIt() {
        show()
        compose.onNodeWithText("Make Culvery the home app so it comes back after a restart.").assertExists()
        compose.onNodeWithText("Change home app").assertDoesNotExist()
        compose.onNodeWithText("Choose home app").performClick()
        assertThat(navigator.homeAppChoices).isEqualTo(1)
    }

    @Test
    fun asTheHomeAppItOffersToChangeIt() {
        home.isDefault.value = true
        show()
        compose.onNodeWithText("Make Culvery the home app so it comes back after a restart.").assertDoesNotExist()
        compose.onNodeWithText("Change home app").performClick()
        assertThat(navigator.homeAppChanges).isEqualTo(1)
    }

    @Test
    fun choosingItElsewhereShowsAtOnce() {
        show()
        home.isDefault.value = true
        compose.onNodeWithText("Change home app").assertExists()
    }
}
```
`SettingsScreenTest.theKioskPageExitsThroughTheShell`: `show(KioskPage())` → `show(KioskPage(FakeHomeApp()))`. `SettingsScreenshotTest`: `KioskPage()` → `KioskPage(FakeHomeApp(default = false))`. `StepsTest` and `StepsUiTest`: every `DoneStep(state, access.control, gate)` / `DoneStep(state, access.control, SetupSessionGate(…))` gets a last argument `FakeHomeApp()`. In `StepsUiTest`, add beside its Done test:
```kotlin
    @Test
    fun doneOffersTheHomeAppUntilItIsChosen() {
        val home = FakeHomeApp(default = false)
        val navigator = RecordingNavigator()
        val done = DoneStep(runBlocking { states.start() }, access.control, SetupSessionGate(household, access.control), home)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides navigator) { CulveryTheme(dark = true) { done.Content(onNext = {}) } }
        }
        compose.onNodeWithText("Choose home app").performClick()
        assertThat(navigator.homeAppChoices).isEqualTo(1)
        home.isDefault.value = true
        compose.waitForIdle()
        compose.onNodeWithText("Choose home app").assertDoesNotExist()
    }
```
(If `StepsUiTest` names its `SetupState` differently from `states.start()`, use the one its existing Done test uses.)

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: FAIL to compile (`KioskPage` and `DoneStep` take no `HomeApp`).

- [ ] **Step 8: The page, the step and the prompt**

`SetupCopy.kt`, under `// Settings (§4.6, §4.7)` add:
```kotlin
internal const val MAKE_HOME_APP = "Make Culvery the home app so it comes back after a restart."
internal const val CHOOSE_HOME_APP = "Choose home app"
internal const val CHANGE_HOME_APP = "Change home app"
```
`SetupDimens.kt`, after `titleLineGap`:
```kotlin

    // The home-app prompt (4c §5.1; not in the spec): its button 12 below its line.
    val homeAppPromptGap = 12.dp
```
`SetupUi.kt`, add:
```kotlin
/** 4c design §5.1: shown while Culvery isn't the home app, in the wizard's Done step and Settings › Kiosk. */
@Composable
internal fun HomeAppPrompt(onChoose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.homeAppPromptGap)) {
        Text(MAKE_HOME_APP, style = SetupType.line, color = Culvery.colors.ink)
        HhPillButton(CHOOSE_HOME_APP, onChoose, Modifier.testTag("choose_home_app"), primary = true)
    }
}
```
(imports as the file needs: `Modifier`, `testTag`, `HhPillButton`.)
`pages/KioskPage.kt` becomes:
```kotlin
package uk.co.siland.culvery.core.setup.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.HomeApp
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.CHANGE_HOME_APP
import uk.co.siland.culvery.core.setup.EXIT_KIOSK
import uk.co.siland.culvery.core.setup.HomeAppPrompt
import uk.co.siland.culvery.core.setup.KIOSK
import uk.co.siland.culvery.core.setup.KIOSK_LINE
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.ui.HhPillButton

/**
 * Settings › Kiosk (4a design §4.7, 4c §5.1): the home app — Choose it while Culvery isn't, Change it (fresh PIN) while
 * it is — and Exit kiosk, which asks for a fresh PIN.
 */
@Singleton
class KioskPage @Inject constructor(private val homeApp: HomeApp) : SettingsPage {
    override val id = "kiosk"
    override val title = KIOSK
    override val order = 900

    @Composable
    override fun Content() {
        val navigator = LocalShellNavigator.current
        val isHome by homeApp.isDefault.collectAsState()
        StepTitle(KIOSK, KIOSK_LINE)
        if (isHome) {
            HhPillButton(CHANGE_HOME_APP, navigator::changeHomeApp, Modifier.testTag("settings_change_home_app"))
        } else {
            HomeAppPrompt(onChoose = navigator::chooseHomeApp)
        }
        HhPillButton(EXIT_KIOSK, navigator::exitKiosk, Modifier.testTag("settings_exit_kiosk"))
    }
}
```
`steps/DoneStep.kt`: the constructor gains `private val homeApp: HomeApp` last, and `Content` becomes:
```kotlin
    @Composable
    override fun Content(onNext: () -> Unit) {
        val navigator = LocalShellNavigator.current
        val isHome by homeApp.isDefault.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap)) {
            StepTitle(CULVERY_IS_READY)
            if (!isHome) HomeAppPrompt(onChoose = navigator::chooseHomeApp)
        }
    }
```
(imports as needed.) The KDoc line becomes `/** 4a design §3.3, §4.2: "Culvery is ready", and the home-app prompt (4c §5.1). Never done, so a resume that gets this far stops here. */`.

Run: `./gradlew :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest`
Expected: PASS (`KioskPageTest` 3, the Done test, and the rest; screenshots aren't compared by this step).

- [ ] **Step 9: Re-record the two pages and look**

In `SetupScreenshotTest`, the `done` helper's content becomes:
```kotlin
    private fun done(name: String, dark: Boolean) = step(name, dark, 6, Forward.Next("Open Culvery", enabled = true)) {
        StepTitle("Culvery is ready")
        HomeAppPrompt(onChoose = {})
    }
```
Run:
```bash
./gradlew :core:setup:recordRoborazziDebug --tests "*SettingsScreenshotTest.kiosk*" --tests "*SetupScreenshotTest.done*"
```
Look at all four: `settings_kiosk_*` — the Kiosk title and its line, then "Make Culvery the home app so it comes back after a restart." with a primary **Choose home app** pill below it, then **Exit kiosk**; `done_*` — "Culvery is ready", the same line and pill, Back and **Open Culvery** in the frame. Nothing overlaps.

- [ ] **Step 10: Document the home app and device owner**

Append to `docs/setup/release.md`:
````markdown

## 5. Make Culvery the home app

After setup, the Done step (and Settings › Kiosk) shows "Make Culvery the home app so it comes back after a restart." Tap **Choose home app** and answer Android's question. From then on a reboot, a power cut or a crash lands back in Culvery, and every return to the front pins it again. **Exit kiosk** unpins it and, as the home app, leaves it in front with the system bars showing; switch apps from there with Overview. To give the tablet back its normal launcher, use **Settings › Kiosk › Change home app** (a fresh Admin PIN; the pad says "Exit kiosk mode") and choose the other launcher.

## 6. Device owner (optional)

As device owner, Culvery allowlists itself (and Google Play services, for the account chooser) for lock-task, so pinning needs no confirmation and can't be undone with Back + Overview. It needs a **freshly reset tablet with no accounts on it yet**: set the tablet up without signing in to Google, install the release, then:

```bash
adb shell dpm set-device-owner uk.co.siland.culvery/.CulveryDeviceAdmin
```

Add the family's Google account afterwards (Settings › Accounts). Undoing device owner needs a factory reset. Without it, screen pinning works as described in the README's Kiosk section.
````

- [ ] **Step 11: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences beyond the four recorded in Step 9.

- [ ] **Step 12: Commit**

```bash
git add core app capability docs/setup/release.md
git commit -m "Offer Culvery as the home app through Android's dialog, keep Exit kiosk in front, and allowlist lock-task as device owner"
```

---

### Task 10: Connecting Google on the kiosk — `connections.manage`, the Play services check, leaving pinning (§5.3, D5, K5, K6, L3, M4; rulings 16, 17)

**Review:** opus (the connect flow and pinning).

**Files:**
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/Permissions.kt`; test `DefaultAccessControlTest.kt`
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/ShellNavigator.kt`
- Modify (navigator fakes): `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/RecordingNavigator.kt`, `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/RecordingNavigator.kt`, `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestUi.kt`, `app/src/test/java/uk/co/siland/culvery/shell/ui/NoNavigation.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarConnections.kt`; test `ui/CalendarConnectHostTest.kt`
- Create: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/PlayServices.kt`
- Modify: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleConnectFlow.kt`, `GoogleCalendarProvider.kt`, `di/GoogleCalendarModule.kt`
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/TestSupport.kt`, `RecordingNavigator.kt` (create), `GoogleConnectScreenTest.kt`, `GoogleConnectFlowTest.kt`, `GoogleReadTest.kt`, `GoogleWriteTest.kt`, `GoogleCalendarProviderContractTest.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/MainActivity.kt`, `shell/ShellViewModel.kt`
- Modify: `docs/setup/google-calendar.md` (§5's release paragraph)

**Interfaces:**
- Consumes: Task 9's `MainActivity.ownerAllowsGoogle` and `KioskLifecycle` (which pins again on resume).
- Produces:
  - `CorePermissions.CONNECTIONS_MANAGE = "connections.manage"` (ADMIN, fresh PIN, label "Connect calendars").
  - `ShellNavigator.leavePinning()`: unpins at once so a system screen can open; Culvery pins again when next in front. `MainActivity` provides a navigator that does it (not as device owner, where Play services is allowlisted); `ShellViewModel`'s own is a no-op, as it has no window.
  - `fun interface PlayServicesCheck { fun usable(): Boolean }`, `class GooglePlayServicesCheck`, `const val UPDATE_PLAY_SERVICES` in `:provider:calendar-google`.
  - `GoogleCalendarProvider(api: GoogleApi, authorizer: Authorizer, toaster: Toaster, playServices: PlayServicesCheck)`; `GoogleConnectFlow(authorizer, api, toaster, playServices)`.
  - Test helper `testProvider(api, authorizer = FakeAuthorizer(), toaster = NoToasts, playServices = PlayServicesCheck { true })` in `TestSupport.kt` (Task 13 adds a parameter to it).

- [ ] **Step 1: Write the failing permission tests**

`DefaultAccessControlTest.kt`: in `aSetupSessionPassesEveryPermissionWithoutAPin`, the list becomes `listOf(CorePermissions.SETTINGS_MANAGE, CorePermissions.PEOPLE_MANAGE, CorePermissions.KIOSK_EXIT, CorePermissions.CONNECTIONS_MANAGE)`; and add:
```kotlin
    /** L3: connecting can lead out of the app through Google's screens, so it always takes a fresh Admin PIN. */
    @Test
    fun connectingAsksForAFreshPinEvenDuringASession() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        val request = firstPromptFor(access, CorePermissions.CONNECTIONS_MANAGE)
        assertThat(request.label).isEqualTo("Connect calendars")
    }
```
Run: `./gradlew :core:access:testDebugUnitTest --tests "*DefaultAccessControlTest*"`
Expected: FAIL to compile ("Unresolved reference 'CONNECTIONS_MANAGE'").

- [ ] **Step 2: The permission**

`Permissions.kt`: in `CorePermissions` add `const val CONNECTIONS_MANAGE = "connections.manage"`; in `CorePermissionSource.permissions` add, after `KIOSK_EXIT`'s entry:
```kotlin
        // 4c design D5: Google's account chooser can lead out of the app.
        PermissionDef(CorePermissions.CONNECTIONS_MANAGE, "Connect calendars", setOf(Role.ADMIN), freshPin = true),
```
Run the same test class.
Expected: PASS.

- [ ] **Step 3: Connect and Reconnect authorise it**

`CalendarConnections.kt`:
```kotlin
    /** connections.manage: an Admin's fresh PIN every time (4c design D5); the wizard's setup session passes it. */
    suspend fun mayConnect(): Boolean = access.authorise(CorePermissions.CONNECTIONS_MANAGE) != null
```
`ui/CalendarConnectHostTest.kt`: add `private const val CONNECT_LABEL = "Connect calendars"` beside `SETTINGS_LABEL`; every assertion about the PIN pad of a connect or reconnect (`access.requests.map { it.label }` in the connect tests) expects `CONNECT_LABEL` instead of `SETTINGS_LABEL`. Add:
```kotlin
    @Test
    fun anAdminAlreadySignedInEntersAFreshPinToConnect() {
        google.connectsAs = googleConnection
        access.answer(TestAccess.ALEX, TestAccess.ALEX)
        runBlocking { access.control.authorise(CorePermissions.SETTINGS_MANAGE) }
        show(existing = null)
        waitFor { access.toasts.messages.isNotEmpty() }
        assertThat(access.requests.map { it.label }).containsExactly(SETTINGS_LABEL, CONNECT_LABEL).inOrder()
        assertThat(access.toasts.messages).containsExactly("Google Calendar connected")
    }
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarConnectHostTest*"`
Expected: PASS.


- [ ] **Step 4: The `leavePinning` seam**

`ShellNavigator.kt`, add after `changeHomeApp()`:
```kotlin

    /**
     * Unpins at once, so a system screen such as Google's account chooser can open (4c design §5.3, D5); Culvery pins
     * again when it is next in front. Nothing when not pinned (the wizard, debug builds).
     */
    fun leavePinning()
```
Every fake navigator gets `override fun leavePinning() = Unit` (`capability/calendar/…/ui/RecordingNavigator.kt`, `capability/weather/…/ui/RecordingNavigator.kt`, `core/setup/…/TestUi.kt`'s `RecordingNavigator`, `app/…/shell/ui/NoNavigation.kt`). `shell/ShellViewModel.kt` gets
```kotlin
    /** The view model has no window: MainActivity's navigator unpins (4c §5.3). */
    override fun leavePinning() = Unit
```
Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/RecordingNavigator.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import uk.co.siland.culvery.core.plugin.ShellNavigator

/** Records what the connect screen asked of the shell into [calls], which a test may share with other recorders. */
internal class RecordingNavigator(private val calls: MutableList<String>) : ShellNavigator {
    override fun openTab(id: String) = Unit

    override fun openSettings() = Unit

    override fun exitKiosk() = Unit

    override fun chooseHomeApp() = Unit

    override fun changeHomeApp() = Unit

    override fun leavePinning() {
        calls += "leave pinning"
    }
}
```

- [ ] **Step 5: Write the failing provider tests**

`TestSupport.kt`, add:
```kotlin
/** The provider over [api], with Play services usable unless a test says otherwise. */
internal fun testProvider(
    api: GoogleApi,
    authorizer: Authorizer = FakeAuthorizer(),
    toaster: Toaster = NoToasts,
    playServices: PlayServicesCheck = PlayServicesCheck { true },
) = GoogleCalendarProvider(api, authorizer, toaster, playServices)
```
(import `uk.co.siland.culvery.core.plugin.Toaster`; if `NoToasts` lives in another test file, leave it there.) Replace every `GoogleCalendarProvider(GoogleApi(…), authorizer, toasts)` / `GoogleCalendarProvider(api, FakeAuthorizer(), NoToasts)` in `GoogleConnectScreenTest`, `GoogleReadTest`, `GoogleWriteTest`, `GoogleCalendarProviderContractTest` with `testProvider(GoogleApi(…), authorizer, toasts)` / `testProvider(api)` (same arguments, by name where they aren't the defaults). In `GoogleConnectFlowTest`, keep the `GoogleApi` `setUp` builds in a field `api` (so the server starts once) and `GoogleConnectFlow(authorizer, api, toasts)` gains a last argument `PlayServicesCheck { true }`.

`GoogleConnectScreenTest.kt`: the connect screen now reads `LocalShellNavigator`, so every `setContent` provides one. Add a field and a wrapper:
```kotlin
    /** What happened, in order: the navigator's "leave pinning" and the registry's "launch". */
    private val calls = mutableListOf<String>()

    private fun screen(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator(calls)) { content() }
    }
```
Every `compose.setContent { provider.ConnectScreen(…) }` becomes `screen { provider.ConnectScreen(…) }`; in `showWithScreens`, `compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides …) { … } }` becomes `screen { CompositionLocalProvider(LocalActivityResultRegistryOwner provides …) { … } }`; and `screensAnswering` records the launch:
```kotlin
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                calls += "launch"
                dispatchResult(requestCode, resultCode, null)
            }
```
Add:
```kotlin
    /** D5: Google's screens can't open over a pinned app, so pinning is left first. */
    @Test
    fun theChooserOpensOnlyAfterLeavingPinning() {
        var connected: Connection? = null
        showWithScreens(Activity.RESULT_OK, onConnected = { connected = it }, onCancel = {})
        compose.waitUntil(5_000) { connected != null }
        assertThat(calls).containsExactly("leave pinning", "launch").inOrder()
    }

    @Test
    fun anAccountAlreadyGrantedNeverLeavesPinning() {
        var connected: Connection? = null
        screen { provider.ConnectScreen(existing = null, onConnected = { connected = it }, onCancel = {}) }
        compose.waitUntil(5_000) { connected != null }
        assertThat(calls).isEmpty()
    }
```
(imports `androidx.compose.runtime.Composable`, `uk.co.siland.culvery.core.plugin.LocalShellNavigator`.)

`GoogleConnectFlowTest.kt`, add (the Play services check is tested here only):
```kotlin
    /** M4: without a usable Play services, Google's screens would fail; say what to do and start nothing. */
    @Test
    fun withoutUsablePlayServicesNothingStartsAndItSaysWhy() = runTest {
        val stuck = GoogleConnectFlow(authorizer, api, toasts, PlayServicesCheck { false })
        assertThat(stuck.start(existing = null)).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).containsExactly(UPDATE_PLAY_SERVICES)
        assertThat(authorizer.accounts).isEmpty()
    }
```

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: FAIL to compile (`PlayServicesCheck` unresolved; four-argument `GoogleCalendarProvider`).

- [ ] **Step 6: The check, and leaving pinning before Google's screens**

Create `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/PlayServices.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** 4c design §5.3: what the tablet says when Play services can't run Google's sign-in. */
const val UPDATE_PLAY_SERVICES = "Update Google Play services on this tablet, then try again."

/** Whether Play services can run Google's sign-in now (4c design §5.3, M4), behind a seam for tests. */
fun interface PlayServicesCheck {
    fun usable(): Boolean
}

class GooglePlayServicesCheck @Inject constructor(@ApplicationContext private val context: Context) : PlayServicesCheck {
    override fun usable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
```
(If `GoogleApiAvailability` doesn't resolve, stop and ask: ruling 17.)

`GoogleConnectFlow.kt`: the class header becomes
```kotlin
internal class GoogleConnectFlow(
    private val authorizer: Authorizer,
    private val api: GoogleApi,
    private val toaster: Toaster,
    private val playServices: PlayServicesCheck,
) {
    suspend fun start(existing: Connection?): ConnectStep {
        if (!playServices.usable()) {
            Log.w(TAG, "Play services can't run Google's sign-in on this tablet; nothing started")
            toaster.show(UPDATE_PLAY_SERVICES)
            return ConnectStep.Stopped
        }
        return step(existing, screensAllowed = true) { authorizer.authorize(existing?.config?.get(CONFIG_ACCOUNT)) }
    }
```
(the rest unchanged; update the class KDoc's first sentence to begin "Checks Play services can sign in, then the connect and reconnect flow's logic…").

`GoogleCalendarProvider.kt`: the constructor gains `private val playServices: PlayServicesCheck,` after `toaster`; in `ConnectScreen`:
```kotlin
        val flow = remember { GoogleConnectFlow(authorizer, api, toaster, playServices) }
        val navigator = LocalShellNavigator.current
```
and the `ConnectStep.ShowScreens` branch of its `LaunchedEffect` becomes
```kotlin
                is ConnectStep.ShowScreens -> {
                    // A pinned app can't open another app's screens (4c §5.3); Culvery pins again when it is back in front.
                    navigator.leavePinning()
                    launcher.launch(IntentSenderRequest.Builder(step.intent.intentSender).build())
                }
```
(the launcher's callback is unchanged; import `uk.co.siland.culvery.core.plugin.LocalShellNavigator`.)

`di/GoogleCalendarModule.kt`, in the abstract class:
```kotlin
    @Binds
    abstract fun playServices(impl: GooglePlayServicesCheck): PlayServicesCheck
```
Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: PASS.


- [ ] **Step 7: The activity's navigator leaves pinning**

`MainActivity.kt`: add
```kotlin
    /**
     * The shell's navigator, with leaving pinning done here and at once: the chooser opens right after it returns and
     * can't open over a pinned app. As device owner Play services is allowlisted, so the chooser opens in lock-task.
     */
    private val navigator: ShellNavigator by lazy {
        object : ShellNavigator by shell {
            override fun leavePinning() {
                if (!ownerAllowsGoogle) unpinFromScreen()
            }
        }
    }
```
and in `setContent`, `LocalShellNavigator provides shell,` becomes `LocalShellNavigator provides navigator,` (import `uk.co.siland.culvery.core.plugin.ShellNavigator`). Pinning again needs nothing new: when Google's screens close, Culvery resumes and `KioskLifecycle.onResume` pins (Task 9), on every outcome and after a process death alike.

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 8: Say how connecting works on the kiosk**

In `docs/setup/google-calendar.md`, replace the paragraph
"Release builds need a second Android client with the release key's SHA-1 (Plan 4). In release kiosk mode the account chooser may not appear; if so, exit kiosk (Settings › Kiosk › Exit kiosk), connect, and return."
with:
```markdown
Release builds need the second Android client from section 4. On the kiosk, **Connect** and **Reconnect** ask for an Admin's PIN every time, even mid-session, because Google's screens can lead out of the app. If Google Play services can't sign in, Culvery says "Update Google Play services on this tablet, then try again." and starts nothing. While Google's account chooser and consent screens show, Culvery leaves screen pinning, and pins again as soon as it is back in front, whatever they answered (as device owner it stays in lock-task: Play services is allowed in it).
```

- [ ] **Step 9: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 10: Commit**

```bash
git add core capability provider app docs/setup/google-calendar.md
git commit -m "Ask for a fresh Admin PIN to connect Google, check Play services first, and leave screen pinning only while Google's screens show"
```

---

### Task 11: Google asks for less — `fields=`, the gzip User-Agent, `callTimeout` (§6.1, E1, C1; rulings 4, 5)

**Review:** sonnet.

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Runtime.kt` (`@AppVersion`)
- Modify: `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`
- Modify: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProvider.kt`, `di/GoogleCalendarModule.kt`
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleClientTest.kt` (create), `GoogleReadTest.kt`

**Interfaces:**
- Consumes: Task 1's `versionName`; Task 4's lazy client in `GoogleApi`; Task 10's `testProvider(…)`.
- Produces: `@Qualifier annotation class AppVersion` (`:core:plugin`), provided by `AppModule` as `BuildConfig.VERSION_NAME`; `internal const val EVENT_FIELDS`, `CALENDAR_LIST_FIELDS`; `internal fun userAgent(version: String): String`, `internal fun googleClient(version: String): OkHttpClient` (in `di/GoogleCalendarModule.kt`).

- [ ] **Step 1: Write the failing tests**

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleClientTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import com.google.common.truth.Truth.assertThat
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import uk.co.siland.culvery.provider.calendar_google.di.googleClient

class GoogleClientTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    /** E1: Google compresses only for a User-Agent containing "gzip"; OkHttp asks for gzip itself. */
    @Test
    fun everyCallSaysCulveryAndGzipSoGoogleCompresses() {
        server.enqueue(MockResponse().setBody("{}"))
        server.start()
        googleClient("1.0.0-beta1").newCall(Request.Builder().url(server.url("/calendar/v3/")).build()).execute().use { }
        val sent = server.takeRequest()
        assertThat(sent.getHeader("User-Agent")).isEqualTo("Culvery/1.0.0-beta1 (gzip)")
        assertThat(sent.getHeader("Accept-Encoding")).isEqualTo("gzip")
    }

    /** 4b follow-up: a body that drips in can't hold a sync pass past a minute. */
    @Test
    fun aWholeCallIsLimitedToAMinute() {
        assertThat(googleClient("1.0.0-beta1").callTimeoutMillis).isEqualTo(60_000)
    }
}
```
In `GoogleReadTest.kt` add (imports `kotlinx.serialization.descriptors.SerialDescriptor`, `kotlinx.serialization.descriptors.StructureKind`, `com.google.common.truth.Truth.assertWithMessage`; if the compiler asks for `@OptIn(ExperimentalSerializationApi::class)` on the walk, add it there only — ruling 32):
```kotlin
    @Test
    fun calendarListsAskOnlyForWhatTheTabletReads() = runTest {
        provider.sources(conn)
        assertThat(google.requests.first().requestUrl!!.queryParameter("fields")).isEqualTo(CALENDAR_LIST_FIELDS)
    }

    @Test
    fun fullAndIncrementalEventListsAskOnlyForWhatTheTabletReads() = runTest {
        val full = provider.sync(conn, family, range, cursor = null)
        provider.sync(conn, family, range, full.cursor)
        val lists = google.requests.map { it.requestUrl!! }.filter { it.encodedPath.endsWith("/events") }
        assertThat(lists).hasSize(2)
        assertThat(lists.map { it.queryParameter("fields") }).containsExactly(EVENT_FIELDS, EVENT_FIELDS)
    }

    /**
     * Ruling 4: a field the provider parses but doesn't ask for would come back missing, silently. Walks each answer's
     * classes, nested ones too, against Google's `fields` syntax: a field named alone comes whole; one with a
     * selection, `a(b,c)` or `a/b`, comes with only those.
     */
    @Test
    fun everyFieldTheProviderParsesIsAskedFor() {
        assertAskedFor(FieldsSelection.parse(EVENT_FIELDS), EventsPage.serializer().descriptor, "")
        assertAskedFor(FieldsSelection.parse(CALENDAR_LIST_FIELDS), CalendarListPage.serializer().descriptor, "")
    }

    private fun assertAskedFor(asked: FieldsSelection, descriptor: SerialDescriptor, path: String) {
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            assertWithMessage("$path$name isn't asked for").that(asked.fields).containsKey(name)
            val only = asked.fields[name] ?: continue
            var element = descriptor.getElementDescriptor(index)
            if (element.kind == StructureKind.LIST) element = element.getElementDescriptor(0)
            if (element.kind == StructureKind.CLASS) assertAskedFor(only, element, "$path$name.")
        }
    }

```
and at the end of the file, outside the class:
```kotlin
/** Google's `fields` syntax as a tree: each name maps to its own selection, or to null when it comes whole. */
private class FieldsSelection(val fields: Map<String, FieldsSelection?>) {
    companion object {
        fun parse(text: String): FieldsSelection = FieldsParser(text).parse()
    }
}

/** Reads `a,b(c,d),e/f`: a list of names, each with a bracketed list or a one-name path below it. */
private class FieldsParser(private val text: String) {
    private var i = 0

    fun parse(): FieldsSelection = list().also { check(i == text.length) { "unparsed fields text" } }

    private fun list(): FieldsSelection {
        val out = mutableMapOf<String, FieldsSelection?>()
        while (true) {
            val (name, only) = item()
            out[name] = only
            if (i < text.length && text[i] == ',') i++ else return FieldsSelection(out)
        }
    }

    private fun item(): Pair<String, FieldsSelection?> {
        val start = i
        while (i < text.length && text[i] !in ",()/") i++
        val name = text.substring(start, i)
        return name to when {
            i < text.length && text[i] == '(' -> {
                i++
                list().also {
                    check(text[i] == ')')
                    i++
                }
            }
            i < text.length && text[i] == '/' -> {
                i++
                FieldsSelection(mapOf(item()))
            }
            else -> null
        }
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest --tests "*GoogleClientTest*" --tests "*GoogleReadTest*"`
Expected: FAIL to compile ("Unresolved reference 'googleClient'", "'EVENT_FIELDS'", "'CALENDAR_LIST_FIELDS'"). Once Step 3 is in, the walk passes; removing any one name from either string (say `colorId`, or `self` inside `attendees(…)`) must make it fail — try one, then put it back.

- [ ] **Step 3: The version, the client and the fields**

`core/plugin/.../Runtime.kt`, after `ApplicationScope`:
```kotlin
/** The app's version name, e.g. "1.0.0-beta1"; bound by `:app` from BuildConfig (4c design §6.1). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppVersion
```
`app/.../di/AppModule.kt`, in the companion:
```kotlin
        @Provides
        @AppVersion
        fun appVersion(): String = BuildConfig.VERSION_NAME
```
(imports `uk.co.siland.culvery.BuildConfig`, `uk.co.siland.culvery.core.plugin.AppVersion`.)

`provider/calendar-google/.../di/GoogleCalendarModule.kt`: after `READ_TIMEOUT` add
```kotlin
// The engine gives a call 60 s too; this ends a body that drips in (4b follow-up).
private val CALL_TIMEOUT: Duration = Duration.ofSeconds(60)

/** 4c design §6.1: Google compresses only for a User-Agent that contains "gzip"; OkHttp sends Accept-Encoding itself. */
internal fun userAgent(version: String): String = "Culvery/$version (gzip)"

internal fun googleClient(version: String): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(CONNECT_TIMEOUT)
    .readTimeout(READ_TIMEOUT)
    .callTimeout(CALL_TIMEOUT)
    .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent(version)).build()) }
    .build()
```
and `api` becomes
```kotlin
        @Provides
        @Singleton
        fun api(tokens: TokenSource, @AppVersion version: String): GoogleApi =
            GoogleApi(GOOGLE_CALENDAR_BASE_URL.toHttpUrl(), tokens, lazy { googleClient(version) })
```
(import `uk.co.siland.culvery.core.plugin.AppVersion`.)

`GoogleCalendarProvider.kt`, after `WRITE_ROLES`:
```kotlin
/** What events.list returns (4c §6.1, ruling 4): only what the provider parses; the same on full and incremental lists. */
internal const val EVENT_FIELDS =
    "items(id,status,summary,start,end,recurringEventId,recurrence,eventType,attendees(self,responseStatus)," +
        "extendedProperties/private,colorId),nextPageToken,nextSyncToken"

/** What calendarList.list returns (4c §6.1). */
internal const val CALENDAR_LIST_FIELDS = "items(id,summary,summaryOverride,accessRole,selected,hidden,primary),nextPageToken"
```
`CALENDAR_LIST_QUERY` gains `"fields" to CALENDAR_LIST_FIELDS`:
```kotlin
private val CALENDAR_LIST_QUERY = mapOf("showHidden" to "true", "minAccessRole" to "reader", "fields" to CALENDAR_LIST_FIELDS)
```
and in `list(…)` the query becomes
```kotlin
                query = query + mapOf("singleEvents" to "true", "maxResults" to PAGE_SIZE, "fields" to EVENT_FIELDS, "pageToken" to pageToken),
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: PASS (the fake server ignores `fields` and answers in full, so every other test is unchanged).

- [ ] **Step 5: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 6: Commit**

```bash
git add core/plugin app provider/calendar-google
git commit -m "Ask Google Calendar only for the fields the tablet reads, compressed, and bound each call to a minute"
```

---

### Task 12: The sync token across midnight, pruning, the 4-week window, `calendar.db` v6 (§6.2, §6.6's window, E2, P9, C2; rulings 1, 11)

**Review:** opus (sync state, pruning, the migration).

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`, `db/Migrations.kt`, `di/CalendarModule.kt`, `CalendarStore.kt`, `CalendarSync.kt`, `CalendarContract.kt`
- Generated (commit): `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/6.json`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarMigrationTest.kt`, `CalendarStoreTest.kt`, `CalendarSyncTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `SourceEntity.readProblem: String?` (v6 column; its behaviour is Task 16's).
  - `val MIGRATION_5_6: Migration`; `val ALL_MIGRATIONS: Array<Migration>` (1→2 … 5→6), used by `CalendarModule` and the tests.
  - `CalendarStore.cursor(connectionId, sourceId, window)` now means "while what the full sync read covers [window], in the same zone"; `applySync` keeps a cursor's key on an incremental result; `CalendarStore.prune(connectionId: String, sourceId: String, window: DateRange): Int` keeps [window start, end of what was read).
  - `const val SYNC_FUTURE_DAYS = 28L` (was 14; D10: the window is today −1 to +28) and `const val SYNC_AHEAD_DAYS = 42L` (`CalendarSync.kt`).

- [ ] **Step 1: Write the failing migration test (Review Focus 4)**

In `CalendarMigrationTest.kt` (imports `uk.co.siland.culvery.capability.calendar.db.ALL_MIGRATIONS`, `MIGRATION_5_6`):
- every `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)` becomes `.addMigrations(*ALL_MIGRATIONS)` (Room now opens at v6);
- the three `assertThat(store.cursor("c1", "s1", range)).isEqualTo(SyncCursor("k7"))` become `assertThat(store.cursor("c1", "s1", range)).isNull()` with the comment `// v6 clears every cursor (4c §6.2).`;
- add:
```kotlin
    @Test
    fun migrationFromV5ClearsTheCursorsAndKeepsQueuedChangesMappingsAndTheMaster() = runTest {
        file.parentFile?.mkdirs()
        file.delete()

        val v5 = helper.createDatabase(5)
        v5.execSQL(
            "INSERT INTO connection (id, providerId, label, configJson, health, healthMessage, lastSyncMillis, sourcesCheckedMillis, " +
                "needsSignInSinceMillis) VALUES ('c1', 'calendar.test', 'Google', '{}', 'OK', NULL, 1234, 1000, NULL)",
        )
        v5.execSQL(
            "INSERT INTO source (connectionId, sourceId, name, writable, visible, personId, isMaster, shownInService) " +
                "VALUES ('c1', 's1', 'Family', 1, 1, 'family', 1, 1), ('c1', 's2', 'Alex', 0, 0, 'alex-id', 0, 0)",
        )
        v5.execSQL(
            "INSERT INTO event (connectionId, sourceId, remoteId, title, startInstant, startDate, endInstant, endDate, " +
                "recurring, forPerson, createdBy, startSort, endSort, recurrenceRule) " +
                "VALUES ('c1', 's1', 'e1', 'Swim', 1000, NULL, 2000, NULL, 0, 'alex-id', 'sam-id', 1000, 2000, NULL)",
        )
        v5.execSQL("INSERT INTO sync_state (connectionId, sourceId, cursor, rangeStart) VALUES ('c1', 's1', 'k7', '2026-09-22|Europe/London')")
        v5.execSQL(
            "INSERT INTO outbox (connectionId, sourceId, remoteId, kind, draftJson, attempts, nextAttemptMillis, createdMillis, clientKey, fields) " +
                "VALUES ('c1', 's1', 'e1', 'UPDATE', '$DRAFT_JSON', 2, 5000, 4000, NULL, 'TITLE')",
        )
        v5.close()

        helper.runMigrationsAndValidate(6, listOf(MIGRATION_5_6)).close()

        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, file.path)
            .addMigrations(*ALL_MIGRATIONS)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val store = CalendarStore(db)
            assertThat(store.master().first()?.source?.id).isEqualTo("s1")
            assertThat(store.sources().first().associate { it.source.id to it.mapping }).containsExactly(
                "s1", SourceMapping(PersonId.FAMILY, visible = true),
                "s2", SourceMapping(PersonId("alex-id"), visible = false),
            )
            assertThat(store.pendingNow().map { Triple(it.remoteId, it.kind, it.fields) })
                .containsExactly(Triple("e1", ChangeKind.UPDATE, setOf(EventField.TITLE)))
            assertThat(store.eventNow(EventRef("c1", "s1", "e1"))?.title).isEqualTo("Swim")
            // No cursor: the first pass after the upgrade reads each calendar in full.
            val window = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), ZoneId.of("Europe/London"))
            assertThat(store.cursor("c1", "s1", window)).isNull()
        } finally {
            db.close()
        }
    }
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarMigrationTest*"`
Expected: FAIL to compile ("Unresolved reference 'ALL_MIGRATIONS'", "'MIGRATION_5_6'").

- [ ] **Step 2: v6**

`db/CalendarDatabase.kt`:
- in `SourceEntity` after `shownInService` add
```kotlin
    /**
     * v6 (4c design §6.4): "REFUSED" while the service refuses this calendar's events although it still lists it; null
     * otherwise.
     */
    val readProblem: String? = null,
```
- `SyncStateEntity.rangeStart`'s KDoc becomes `/** The cursor's key, "<first day its full sync read>|<first day it didn't>|<zone id>" (ISO dates; 4c ruling 1); the column keeps its v1 name. */`
- `version = 5` → `version = 6`.

`db/Migrations.kt`, at the end:
```kotlin
/**
 * v6 (Plan 4c): a calendar's read problem (§6.4); and every sync cursor cleared, as their key changes meaning (§6.2), so
 * each calendar is read in full once. The SQL must match schemas/…/6.json exactly.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `source` ADD COLUMN `readProblem` TEXT")
        db.execSQL("DELETE FROM `sync_state`")
    }
}

/** Every migration, oldest first: the database builder and the tests add these. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
```
`di/CalendarModule.kt`: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)` → `.addMigrations(*ALL_MIGRATIONS)` (imports swap to `ALL_MIGRATIONS`).

Generate the schema and check it:
```bash
./gradlew :capability:calendar:kspDebugKotlin
git status --short capability/calendar/schemas
```
Expected: `?? …/6.json`; in it the `source` table's `readProblem` is `"affinity": "TEXT"` with no `notNull` and no `defaultValue`, matching the `ALTER TABLE`.

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarMigrationTest*"`
Expected: PASS (5 tests).


- [ ] **Step 3: Write the failing store tests**

`CalendarStoreTest.kt`:
- rename `cursorIsDroppedWhenTheWindowMoves` to `cursorIsDroppedOnceTheWindowPassesWhatWasRead` (body unchanged: what was read ends where `window` ends, so a window a day later isn't covered);
- add:
```kotlin
    private fun spanning(id: String, from: Instant, to: Instant) =
        RemoteEvent(id, id, EventTime.Timed(from), EventTime.Timed(to), recurring = false)

    private val read = DateRange(window.start, window.endExclusive.plusDays(SYNC_AHEAD_DAYS), zone)

    private suspend fun storedIds() = store.eventsBetween(Long.MIN_VALUE, Long.MAX_VALUE).first().map { it.remoteId }

    /** E2: no nightly full resync — a token serves every window that what it read still covers. */
    @Test
    fun cursorIsKeptWhileWhatWasReadCoversTheWindow() = runTest {
        connect("s1")
        store.applySync("c1", "s1", read, full(timed("a", "One", 23, 9)))
        val nextDay = DateRange(window.start.plusDays(1), window.endExclusive.plusDays(1), zone)
        assertThat(store.cursor("c1", "s1", nextDay)).isEqualTo(SyncCursor("k1"))
    }

    /** Ruling 1: a clock set back asks for days the token never read, so it reads in full again. */
    @Test
    fun aClockMovedBackReadsInFullAgain() = runTest {
        connect("s1")
        store.applySync("c1", "s1", read, full(timed("a", "One", 23, 9)))
        val dayBefore = DateRange(window.start.minusDays(1), window.endExclusive.minusDays(1), zone)
        assertThat(store.cursor("c1", "s1", dayBefore)).isNull()
    }

    @Test
    fun anIncrementalResultKeepsWhatTheFullSyncRead() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "One", 23, 9)))
        val later = DateRange(window.start.plusDays(10), window.endExclusive.plusDays(10), zone)
        store.applySync("c1", "s1", later, SyncResult(emptyList(), emptyList(), SyncCursor("k2"), fullReplace = false))
        // Still only what the full sync read: a window past it isn't covered, whatever the incremental pass asked with.
        assertThat(store.cursor("c1", "s1", DateRange(window.start.plusDays(1), window.endExclusive.plusDays(1), zone))).isNull()
        assertThat(store.cursor("c1", "s1", window)).isEqualTo(SyncCursor("k2"))
    }

    /** §6.2: what ends before the window, or starts at or after the end of what was read, goes; the edges are exact. */
    @Test
    fun pruningDropsWhatEndsBeforeTheWindowOrStartsAfterWhatWasRead() = runTest {
        connect("s1")
        store.applySync(
            "c1", "s1", read,
            full(
                timed("old", "Old", 20, 9),
                spanning("endsAtTheStart", window.startInstant.minusSeconds(3_600), window.startInstant),
                spanning("edge", at(21, 23), at(22, 1)),
                timed("kept", "Kept", 23, 9),
                spanning("lastHour", read.endInstant.minusSeconds(3_600), read.endInstant),
                spanning("startsAtTheEnd", read.endInstant, read.endInstant.plusSeconds(3_600)),
            ),
        )
        assertThat(store.prune("c1", "s1", window)).isEqualTo(3)
        assertThat(storedIds()).containsExactly("edge", "kept", "lastHour").inOrder()
    }

    /** Review Focus 3: an event a queued change targets stays until the change is delivered or dropped. */
    @Test
    fun pruningKeepsAnEventAQueuedChangeTargets() = runTest {
        connect("s1")
        store.applySync("c1", "s1", read, full(timed("old", "Old", 20, 9), timed("gone", "Gone", 20, 11)))
        val draft = EventDraft("Old, renamed", EventTime.Timed(at(20, 9)), EventTime.Timed(at(20, 10)), forPerson = null, createdBy = null)
        store.enqueue(PendingChange(0, "c1", "s1", "old", ChangeKind.UPDATE, draft, attempts = 0, nextAttemptMillis = 0, createdMillis = 0, fields = setOf(EventField.TITLE)))
        store.prune("c1", "s1", window)
        assertThat(storedIds()).containsExactly("old")
    }
```
(imports as the file needs: `java.time.Instant`, `ChangeKind`, `EventDraft`, `EventField`, `PendingChange`.)
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarStoreTest*"`
Expected: FAIL to compile (`prune`, `SYNC_AHEAD_DAYS` unresolved).

- [ ] **Step 4: The store**

`CalendarContract.kt`: in `CalendarProvider`'s KDoc, the last bullet's sentence "With a cursor, incremental upserts MAY lie outside the range (Google's syncToken can't carry timeMin/timeMax); the store keeps them and queries filter by range." becomes "With a cursor, incremental upserts MAY lie outside the range (Google's syncToken can't carry timeMin/timeMax); the engine prunes what lies outside what it keeps."
`db/CalendarDatabase.kt`, in `CalendarDao`:
```kotlin
    /** Events outside [start, end) (spanOverlaps' opposite), except those a queued change targets (4c ruling 11). */
    @Query(
        """
        DELETE FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId
        AND (startSort >= :end OR (endSort <= :start AND startSort < :start))
        AND remoteId NOT IN (SELECT remoteId FROM outbox WHERE connectionId = :connectionId AND sourceId = :sourceId AND remoteId IS NOT NULL)
        AND remoteId NOT IN (SELECT clientKey FROM outbox WHERE connectionId = :connectionId AND sourceId = :sourceId AND clientKey IS NOT NULL)
        """,
    )
    suspend fun pruneEvents(connectionId: String, sourceId: String, start: Long, end: Long): Int
```
`CalendarStore.kt`:
- `cursor(…)` becomes
```kotlin
    /**
     * The sync token while what its full sync read still covers [window], in the same zone (4c ruling 1): a window
     * that has moved past what was read, or before it (a clock set back), or a zone change, reads in full. A zone
     * change must: all-day events are stored at midnight in the zone they were synced in.
     */
    suspend fun cursor(connectionId: String, sourceId: String, window: DateRange): SyncCursor? {
        val state = dao.syncState(connectionId, sourceId) ?: return null
        val key = CursorKey.parse(state.rangeStart) ?: return null
        val covers = !key.readStart.isAfter(window.start) && !key.readEnd.isBefore(window.endExclusive) && key.zoneId == window.zone.id
        return state.cursor?.takeIf { covers }?.let(::SyncCursor)
    }
```
- in `applySync`, replace `dao.upsertSyncState(SyncStateEntity(connectionId, sourceId, result.cursor?.value, range.cursorKey()))` with
```kotlin
            // An incremental result keeps what the full sync read: only a full sync reads a new range (4c ruling 1).
            val key = if (result.fullReplace) range.cursorKey() else dao.syncState(connectionId, sourceId)?.rangeStart ?: range.cursorKey()
            dao.upsertSyncState(SyncStateEntity(connectionId, sourceId, result.cursor?.value, key))
```
- after `applySync` add
```kotlin
    /**
     * Drops [sourceId]'s events that end before [window] starts, or start at or after the end of what its full sync read
     * (4c design §6.2, ruling 1): an incremental result can carry any date, and nothing else removes them. What was read
     * past the window stays, as no incremental result will bring it back. A row a queued change targets stays. Returns
     * how many went.
     */
    suspend fun prune(connectionId: String, sourceId: String, window: DateRange): Int = db.withTransaction {
        val readEnd = dao.syncState(connectionId, sourceId)?.let { CursorKey.parse(it.rangeStart) }?.readEnd ?: window.endExclusive
        dao.pruneEvents(
            connectionId,
            sourceId,
            window.startInstant.toEpochMilli(),
            readEnd.atStartOfDay(window.zone).toInstant().toEpochMilli(),
        )
    }
```
- `private fun DateRange.cursorKey(): String = "$start|${zone.id}"` becomes
```kotlin
/** A sync cursor's key (4c ruling 1): the days its full sync read, end exclusive, and the zone it read them in. */
private class CursorKey(val readStart: LocalDate, val readEnd: LocalDate, val zoneId: String) {
    override fun toString(): String = "$readStart|$readEnd|$zoneId"

    companion object {
        /** Null for a key from before v6 (cleared by the migration) or anything unreadable: a full sync. */
        fun parse(text: String): CursorKey? {
            val parts = text.split('|')
            if (parts.size != 3) return null
            return runCatching { CursorKey(LocalDate.parse(parts[0]), LocalDate.parse(parts[1]), parts[2]) }.getOrNull()
        }
    }
}

private fun DateRange.cursorKey(): String = CursorKey(start, endExclusive, zone.id).toString()
```
`CalendarSync.kt`: `const val SYNC_FUTURE_DAYS = 14L` becomes `const val SYNC_FUTURE_DAYS = 28L` with the KDoc `/** How far ahead the tablet shows: the Calendar tab's four weeks (4c D10); the window is today − 1 to today + 28. */`, and after `PROVIDER_TIMEOUT_MS` add
```kotlin
/**
 * How much further than the window a full sync reads (4c ruling 1): Google's incremental results say nothing about an
 * unchanged event, so the token serves only while what was read covers the window — six weeks.
 */
const val SYNC_AHEAD_DAYS = 42L
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarStoreTest*"`
Expected: PASS.

- [ ] **Step 5: Write the failing sync tests**

`CalendarSyncTest.kt`:
- `windowIsYesterdayToTwoWeeksAheadInTheHouseholdZone`: rename it `aFullSyncReadsFromYesterdayToSixWeeksPastTheWindow`; its expected range becomes `DateRange(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 12, 4), auckland)` (the window, 23 September to 23 October, read 42 days further).
- replace `aNewDayResyncsFromScratch` with:
```kotlin
    /** E2: the pass after local midnight carries on from its token. */
    @Test
    fun thePassAfterLocalMidnightKeepsTheCursor() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        sync.syncAll()
        now = Instant.parse("2026-09-24T11:00:00Z")
        sync.syncAll()
        assertThat(a.calls[1].cursor).isEqualTo(SyncCursor("k1"))
    }

    /** Ruling 1: exactly SYNC_AHEAD_DAYS later the token still serves; a day after, it reads in full. */
    @Test
    fun theTokenServesUntilTheWindowPassesWhatWasRead() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        sync.syncAll()
        now = now.plusSeconds(SYNC_AHEAD_DAYS * 86_400)
        sync.syncAll()
        assertThat(a.calls[1].cursor).isEqualTo(SyncCursor("k1"))
        now = now.plusSeconds(86_400)
        sync.syncAll()
        assertThat(a.calls[2].cursor).isNull()
    }

    @Test
    fun eventsAnIncrementalResultBringsOutsideWhatIsKeptArePruned() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        a.events = { listOf(swim()) }
        sync.syncAll()
        a.events = { listOf(swim(), at("2026-12-25T12:00:00Z", "Christmas lunch"), at("2026-09-01T10:00:00Z", "Long ago")) }
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(a.calls.last().cursor).isNotNull()
        assertThat(store.eventsBetween(Long.MIN_VALUE, Long.MAX_VALUE).first().map { it.title }).containsExactly("Swim")
    }
```
with a helper beside `swim()`:
```kotlin
    private fun at(instant: String, title: String) = RemoteEvent(
        title.lowercase(), title,
        EventTime.Timed(Instant.parse(instant)),
        EventTime.Timed(Instant.parse(instant).plusSeconds(3_600)),
        recurring = false,
    )
```
(Christmas is past the read's end, 3 December; 1 September is before the window's start, 22 September.)
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSyncTest*"`
Expected: FAIL: the range is still the window; the next day's cursor is null; nothing is pruned.

- [ ] **Step 6: The engine reads further, keeps its token, and prunes**

`CalendarSync.kt`, `syncSource` becomes
```kotlin
    private suspend fun syncSource(
        provider: CalendarProvider,
        conn: Connection,
        source: CalendarSource,
        window: DateRange,
    ): ConnectionHealth {
        val cursor = store.cursor(conn.id, source.id, window)
        val read = DateRange(window.start, window.endExclusive.plusDays(SYNC_AHEAD_DAYS), window.zone)
        val result = callReader(io, timeoutMillis) { provider.sync(conn, source, read, cursor) }
            .getOrElse { return healthAfter(it, conn, source.id) }
        store.applySync(conn.id, source.id, read, result)
        store.prune(conn.id, source.id, window)
        return ConnectionHealth.Ok
    }
```
(the store calls stay outside the provider's call, as the KDoc above it says.)

Run: `./gradlew :capability:calendar:testDebugUnitTest`, then `./gradlew testDebugUnitTest`
Expected: PASS. A test elsewhere that asserted the range passed to a provider, or a window 14 days long, now expects the 28-day window read `SYNC_AHEAD_DAYS` further; change only that expectation.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar
git commit -m "Keep calendar sync tokens across midnight, prune what falls outside what was read, and sync four weeks ahead"
```

---

### Task 13: A series deleted on a phone — `StoredSeries` and C9 (§6.3 C9, §6.2 D11; rulings 2, 3)

**Review:** opus (removals from the mirror).

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`, `CalendarStore.kt`, `db/CalendarDatabase.kt`, `di/CalendarModule.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`
- Modify: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProvider.kt`
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/TestSupport.kt`, `GoogleReadTest.kt`

**Interfaces:**
- Consumes: Task 10's `testProvider(…)`; Task 12's store.
- Produces:
  - `fun interface StoredSeries { suspend fun instances(connectionId: String, sourceId: String): Map<String, String?> }` in `CalendarContract.kt`; `CalendarStore : StoredSeries`, bound in `CalendarModule`. Used only to find a deleted series' stored instances: the rule cache is not seeded from it (D11).
  - `GoogleCalendarProvider(api, authorizer, toaster, playServices, stored: StoredSeries)`; `testProvider(…, stored: StoredSeries = StoredSeries { _, _ -> emptyMap() })`.

- [ ] **Step 1: Write the failing store test**

`CalendarStoreTest.kt`, add:
```kotlin
    @Test
    fun theStoredSeriesAreEachRepeatingInstanceWithItsRule() = runTest {
        connect("s1")
        val weekly = RemoteEvent("piano_1", "Piano", EventTime.Timed(at(23, 15)), EventTime.Timed(at(23, 16)), recurring = true, recurrenceRule = "RRULE:FREQ=WEEKLY")
        store.applySync("c1", "s1", window, full(weekly, timed("once", "Once", 23, 9)))
        assertThat(store.instances("c1", "s1")).containsExactly("piano_1", "RRULE:FREQ=WEEKLY")
    }
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarStoreTest*"`
Expected: FAIL to compile ("Unresolved reference 'instances'").

- [ ] **Step 2: The seam, read from the store**

`CalendarContract.kt`, after `SyncResult`:
```kotlin
/**
 * What the tablet already holds of one calendar's repeating events (4c design §6.3, C9): each stored instance's id with
 * its series' rule, null when unknown. A provider reads it to find a deleted series' instances.
 */
fun interface StoredSeries {
    suspend fun instances(connectionId: String, sourceId: String): Map<String, String?>
}
```
`db/CalendarDatabase.kt`: after `EventRow` add
```kotlin
data class SeriesInstance(val remoteId: String, val recurrenceRule: String?)
```
and in `CalendarDao`:
```kotlin
    @Query("SELECT remoteId, recurrenceRule FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId AND recurring = 1")
    suspend fun seriesInstances(connectionId: String, sourceId: String): List<SeriesInstance>
```
`CalendarStore.kt`: the class header becomes `class CalendarStore internal constructor(private val db: CalendarDatabase, private val dao: CalendarDao) : StoredSeries {`, and after `prune` add
```kotlin
    override suspend fun instances(connectionId: String, sourceId: String): Map<String, String?> =
        dao.seriesInstances(connectionId, sourceId).associate { it.remoteId to it.recurrenceRule }
```
`di/CalendarModule.kt`, in the abstract class:
```kotlin
    @Binds
    abstract fun storedSeries(impl: CalendarStore): StoredSeries
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarStoreTest*"`
Expected: PASS.

- [ ] **Step 3: Write the failing Google tests**

`TestSupport.kt`: `testProvider` gains a last parameter `stored: StoredSeries = StoredSeries { _, _ -> emptyMap() }` passed as the provider's last argument (import `uk.co.siland.culvery.capability.calendar.StoredSeries`).
`GoogleReadTest.kt`: keep the API `setUp` builds in a field (`private lateinit var api: GoogleApi`; `api = GoogleApi(google.start(), FakeTokenSource(), OkHttpClient())`; `provider = testProvider(api)`), and add:
```kotlin
    private fun asksFor(series: String) = google.requests.count { it.requestUrl!!.pathSegments.last() == series }

    private fun pianoSeries(rule: String = "RRULE:FREQ=WEEKLY;BYDAY=TU") {
        google.putEvent(family.id, google.timed("piano", "Piano", at(22, 15), at(22, 16)) { putJsonArray("recurrence") { add(rule) } })
        google.putEvent(family.id, google.timed("piano_20260922T141500Z", "Piano", at(22, 15), at(22, 16)) { put("recurringEventId", "piano") })
        google.putEvent(family.id, google.timed("piano_20260929T141500Z", "Piano", at(29, 15), at(29, 16)) { put("recurringEventId", "piano") })
    }

    /**
     * C9: a series deleted on a phone comes back as its master's id alone; every stored instance of it goes — and only
     * its own: not a series split from it ("this and following" makes "<id>_R<start>"), nor another series.
     */
    @Test
    fun aCancelledSeriesRemovesEveryStoredInstance() = runTest {
        val stored = mutableMapOf<String, String?>()
        val mirrored = testProvider(api, stored = StoredSeries { _, _ -> stored })
        pianoSeries()
        google.putEvent(family.id, google.timed("swim_20260923T161500Z", "Swim", at(23, 17), at(23, 18)) { put("recurringEventId", "swim") })
        val first = mirrored.sync(conn, family, range, null)
        first.upserts.forEach { stored[it.remoteId] = it.recurrenceRule }
        stored["piano_R20261006T141500_20261006T141500Z"] = "RRULE:FREQ=WEEKLY;BYDAY=TU"
        stored["piano_20261003"] = null
        google.putEvent(family.id, buildJsonObject { put("id", "piano"); put("status", "cancelled") })
        val next = mirrored.sync(conn, family, range, first.cursor)
        assertThat(next.removedIds).containsAtLeast("piano", "piano_20260922T141500Z", "piano_20260929T141500Z", "piano_20261003")
        assertThat(next.removedIds).containsNoneOf("swim_20260923T161500Z", "piano_R20261006T141500_20261006T141500Z")
    }

    /** Ruling 3: with no nightly full sync, a series changed on a phone has its rule read again. */
    @Test
    fun aChangedSeriesHasItsRuleReadAgainOnAnIncrementalPass() = runTest {
        pianoSeries()
        val first = provider.sync(conn, family, range, null)
        pianoSeries(rule = "RRULE:FREQ=DAILY")
        val next = provider.sync(conn, family, range, first.cursor)
        assertThat(next.upserts.map { it.recurrenceRule }.distinct()).containsExactly("RRULE:FREQ=DAILY")
        assertThat(asksFor("piano")).isEqualTo(2)
    }
```
(imports `kotlinx.serialization.json.buildJsonObject` and `put` if not there; `StoredSeries`.)

Run: `./gradlew :provider:calendar-google:testDebugUnitTest --tests "*GoogleReadTest*"`
Expected: FAIL to compile (`testProvider` has no `stored`, the provider takes four arguments), then, once Step 4's constructor is in, the two new tests FAIL on behaviour.

- [ ] **Step 4: Cancel and refresh in the provider**

`GoogleCalendarProvider.kt`:
- the constructor gains `private val stored: StoredSeries,` last (import `uk.co.siland.culvery.capability.calendar.StoredSeries`); update the class KDoc's last sentence to "Each series' RRULE is fetched once and kept in memory per calendar, until that calendar's next full sync; an incremental pass reads a changed series' rule again (4c ruling 3)."
- `incremental(…)` becomes
```kotlin
    private suspend fun incremental(conn: Connection, account: String, source: CalendarSource, cursor: SyncCursor): SyncResult? {
        val listed = list(account, source, mapOf("syncToken" to cursor.value)) ?: return null
        val cache = rules.getOrPut(keyOf(conn, source)) { ConcurrentHashMap() }
        // No nightly full sync refreshes a rule any more: a series with a changed instance is read again (4c ruling 3).
        listed.items.filterNot { it.isGone }.mapNotNullTo(HashSet()) { it.recurringEventId }.forEach { cache.remove(it) }
        val upserts = mutableListOf<RemoteEvent>()
        val removed = mutableListOf<String>()
        val failed = mutableSetOf<String>()
        var instances: Map<String, String?>? = null
        listed.items.forEach { event ->
            if (event.isGone) {
                removed += event.id
                if (event.recurringEventId == null) {
                    // C9: a deleted series comes back as its own id alone (ruling 2).
                    val known = instances ?: stored.instances(conn.id, source.id).also { instances = it }
                    removed += known.keys.filter { instanceOf(event.id, it) }
                    cache.remove(event.id)
                }
            } else {
                event.toRemoteEvent(ruleOf(account, source, event, cache, failed))?.let { upserts += it }
            }
        }
        return SyncResult(upserts, removed, listed.syncToken?.let(::SyncCursor) ?: cursor, fullReplace = false)
    }
```
- and at the top level of the file:
```kotlin
/**
 * Whether [remoteId] is an instance of the series [seriesId] (ruling 2): Google names one "<series>_<original start>",
 * the start as 20261005T141500Z or, for an all-day event, 20261005. A series split from it ("<series>_R<start>") is
 * another series, so only that exact shape counts.
 */
internal fun instanceOf(seriesId: String, remoteId: String): Boolean =
    Regex("^${Regex.escape(seriesId)}_\\d{8}(T\\d{6}Z)?$").matches(remoteId)
```

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: PASS (the existing `instancesOfASeriesAreRecurringWithTheSeriesRuleFetchedOnce` still sees a full sync fetch each rule again: D11).

- [ ] **Step 5: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 6: Commit**

```bash
git add capability/calendar provider/calendar-google
git commit -m "Drop every stored instance of a series deleted on a phone, and read a changed series' rule again"
```

---

### Task 14: Looking ahead four weeks in the Calendar tab (§6.6, D10)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`, `CalendarEditor.kt`, `ui/WeekView.kt`, `ui/CardHosts.kt`, `ui/Pickers.kt`, `ui/CalendarType.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt`, `CalendarSyncTest.kt`, `ui/SampleUi.kt`, `ui/WeekViewTest.kt`, `ui/CardHostsMidnightRolloverTest.kt`, `ui/WeekScreenshotTest.kt`
- Screenshots (record, commit): `capability/calendar/src/test/screenshots/week_*.png` (the five existing ones change: the header gains ‹ ›), plus `week_next_light.png`, `week_next_dark.png`, `week_furthest_light.png`, `week_furthest_dark.png`

**Interfaces:**
- Consumes: Task 2's `Icons.CHEVRON_LEFT` / `Icons.CHEVRON_RIGHT` (already in `icons.txt`); Task 6's `HouseholdClock`, `rememberToday(clock)`, `rememberNowMillis(clock)` and the reworked `CardHostsMidnightRolloverTest`; Task 12's `SYNC_FUTURE_DAYS = 28L`.
- Produces:
  - `CalendarUi.kt`: `const val MAX_WEEKS_AHEAD = 3`; `fun lastShownDay(today: LocalDate): LocalDate` (today + 27); `fun weekTitle(weeksAhead: Int): String`; `fun eventAddedFor(day: LocalDate): String`.
  - `WeekViewState(week, today, sync, nowMillis, weeksAhead: Int = 0)`; `WeekView(state, modifier, onOpen, onAdd, onWeeksAhead: (Int) -> Unit = {}, onReconnect)`.
  - `ui/WeekView.kt`: `internal class WeekShown` (`weeks`, `show(weeks)`, `touched()`), `@Composable internal fun rememberWeekShown(today: LocalDate): WeekShown`, `internal const val BACK_TO_THIS_WEEK_MS = 120_000L`, `internal fun Modifier.onEveryTouch(onTouch: () -> Unit): Modifier`.
  - `ui/Pickers.kt`: `RoundButton` becomes `internal`, with `enabled: Boolean = true` before `onClick`.
  - The editor's add toast: `EVENT_ADDED`, or `eventAddedFor(day)` when the event starts after `lastShownDay(today)`.

- [ ] **Step 1: Write the failing tests for the shown range and the toast**

`CalendarSyncTest.kt`, add (imports `lastShownDay`, `SYNC_FUTURE_DAYS` are same-package):
```kotlin
    /** 4c D10: the Calendar tab's furthest day is inside the window each pass keeps. */
    @Test
    fun theFurthestWeekShownIsInsideTheWindow() {
        val today = LocalDate.of(2026, 9, 24)
        assertThat(lastShownDay(today)).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(lastShownDay(today)).isAtMost(today.plusDays(SYNC_FUTURE_DAYS))
    }
```
`CalendarEditorTest.kt`: the editor's "now" moves from 1970 to a real morning, so "today" means something to the toast:
- after `london`, add
```kotlin
    /** The editor's and the drain's clock start here: Wednesday 23 September, 09:00 in London. */
    private val opened = LocalDate.of(2026, 9, 23).atTime(9, 0).atZone(london).toInstant().toEpochMilli()
```
- in `editor(…)`, `WallClock { testScheduler.currentTime }` → `WallClock { opened + testScheduler.currentTime }`; in `drain(…)`, `WallClock { testScheduler.currentTime + aheadMillis }` → `WallClock { opened + testScheduler.currentTime + aheadMillis }`.
- the four assertions that compare a stored time with the test's clock gain `opened + `: in `offlineDeleteIsQueued` (`isEqualTo(opened + testScheduler.currentTime + 30_000)`), `aChangeBehindAPendingOneIsQueuedNotWrittenDirectly` (`isEqualTo(opened + testScheduler.currentTime)`), `anOfflineAddIsQueuedWithItsKey` (`isEqualTo(opened + testScheduler.currentTime + OUTBOX_BACKOFF_MS.first())`) and `aSaveThatNeedsSignInIsQueuedAndFlagsTheConnectionAtOnce` (`isEqualTo(opened + testScheduler.currentTime)`). `aWriteSlowerThanTenSecondsIsQueued` reads the scheduler alone and stays. Find them with `git grep -n "testScheduler.currentTime" -- capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt`; an editor built inline with its own `WallClock { testScheduler.currentTime }` (two tests that add nothing) stays as it is.
- add
```kotlin
    /** A one-hour event at 18:00 London on [day]. */
    private fun draftOn(day: LocalDate, title: String): EventDraft {
        val start = day.atTime(18, 0).atZone(london).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), PersonId.FAMILY.value, null)
    }

    /** 4c §6.6: an event after the last day the Calendar tab shows names its day; one on that day doesn't. */
    @Test
    fun anEventAddedBeyondTheFurthestWeekNamesItsDay() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        val last = lastShownDay(LocalDate.of(2026, 9, 23))
        assertThat(editor.create(draftOn(last, "Last shown"))).isEqualTo(EditResult.Done)
        assertThat(editor.create(draftOn(last.plusDays(1), "Beyond"))).isEqualTo(EditResult.Done)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED, "Event added for Wed 21 October").inOrder()
    }
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSyncTest*" --tests "*CalendarEditorTest*"`
Expected: FAIL to compile ("Unresolved reference 'lastShownDay'").

- [ ] **Step 2: The shown range, the titles and the dated toast**

`CalendarUi.kt`, after `WEEKDAY`:
```kotlin
internal val DAY_AND_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)

/** How many weeks past this one the Calendar tab steps to (4c D10): four weeks in all, inside the synced window. */
const val MAX_WEEKS_AHEAD = 3

/** The last day the Calendar tab can show from [today]: the seventh day of its furthest week. */
fun lastShownDay(today: LocalDate): LocalDate = today.plusWeeks(MAX_WEEKS_AHEAD + 1L).minusDays(1)

/** The Calendar tab's title for the week [weeksAhead] after this one. */
fun weekTitle(weeksAhead: Int): String = when (weeksAhead) {
    0 -> "This week"
    1 -> "Next week"
    else -> "In $weeksAhead weeks"
}

/** 4c §6.6: the toast after adding an event the Calendar tab can't reach, so the family know where it went. */
fun eventAddedFor(day: LocalDate): String = "Event added for ${day.format(DAY_AND_MONTH)}"
```
`CalendarEditor.kt`:
- `create(…)`: before `return onAppScope(…)` add `val added = addedToast(draft.start)`, and the call becomes `onAppScope(ChangeKind.CREATE, serviceNameOf(to.connection, serviceOf), added) { … }`.
- `onAppScope(kind: ChangeKind, label: String, block: …)` → `onAppScope(kind: ChangeKind, label: String, added: String = EVENT_ADDED, block: …)`, and inside it `report(kind, label, result)` → `report(kind, label, result, added)`.
- `report(kind: ChangeKind, label: String, result: EditResult)` → `report(kind: ChangeKind, label: String, result: EditResult, added: String)`, and its `saved && kind == ChangeKind.CREATE -> toaster.show(EVENT_ADDED)` → `toaster.show(added)`.
- after `openedAt()` add
```kotlin
    /** "Event added", or with its day when that is past what the Calendar tab shows (4c §6.6). */
    private suspend fun addedToast(start: EventTime): String {
        val z = zone.current()
        val day = when (start) {
            is EventTime.AllDay -> start.date
            is EventTime.Timed -> start.instant.atZone(z).toLocalDate()
        }
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(z).toLocalDate()
        return if (day > lastShownDay(today)) eventAddedFor(day) else EVENT_ADDED
    }
```
Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS. Any other test asserting `EVENT_ADDED` whose editor clock is far from its event's date (as `CalendarEditorTest`'s was): give its clock a real morning near the event, as above; don't change the expectation.

- [ ] **Step 3: Write the failing view tests**

`ui/SampleUi.kt`, after `week`:
```kotlin
    /** [week] moved [weeks] on: the Calendar tab stepped ahead (4c D10). */
    fun weekAhead(weeks: Long) = week.copy(
        start = TODAY.plusWeeks(weeks),
        days = week.days.map { it.copy(date = it.date.plusWeeks(weeks)) },
    )
```
`ui/WeekViewTest.kt`, add (imports `androidx.compose.material3.Text`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.test.assertIsEnabled`, `androidx.compose.ui.test.assertIsNotEnabled`, `uk.co.siland.culvery.capability.calendar.MAX_WEEKS_AHEAD`, `uk.co.siland.culvery.capability.calendar.weekTitle`):
```kotlin
    /** 4c §6.6: ‹ › step a week at a time; › stops at the furthest week, ‹ at this week; This week jumps back. */
    @Test
    fun theArrowsStepAWeekAndStopAtEachEnd() {
        val asked = mutableListOf<Int>()
        var weeks by mutableStateOf(0)
        show { WeekView(state().copy(weeksAhead = weeks), onWeeksAhead = { asked += it; weeks = it }) }
        compose.onNodeWithTag("week_earlier").assertIsNotEnabled()
        compose.onNodeWithTag("week_this_week").assertDoesNotExist()
        compose.onNodeWithTag("week_later").performClick()
        compose.onNodeWithText("Next week").assertExists()
        repeat(MAX_WEEKS_AHEAD) { compose.onNodeWithTag("week_later").performClick() }
        compose.onNodeWithText("In 3 weeks").assertExists()
        compose.onNodeWithTag("week_later").assertIsNotEnabled()
        compose.onNodeWithTag("week_earlier").assertIsEnabled()
        compose.onNodeWithTag("week_this_week").performClick()
        assertThat(asked).containsExactly(1, 2, 3, 0).inOrder()
        compose.onNodeWithText("This week").assertExists()
        compose.onNodeWithTag("week_this_week").assertDoesNotExist()
    }

    @Test
    fun eachWeekHasItsTitle() {
        assertThat((0..MAX_WEEKS_AHEAD).map(::weekTitle)).containsExactly("This week", "Next week", "In 2 weeks", "In 3 weeks").inOrder()
    }

    /** 4c §6.6: on a later week, + adds on its first day and a column on its own day; no column says Today. */
    @Test
    fun aLaterWeekAddsOnItsOwnDays() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(WeekViewState(SampleUi.weekAhead(1), SampleUi.TODAY, sync(), now, weeksAhead = 1), onAdd = { added += it }) }
        compose.onNodeWithTag("week_add_event").performClick()
        compose.onNodeWithTag("week_add_${SampleUi.TODAY.plusDays(9)}").performClick()
        assertThat(added).containsExactly(SampleUi.TODAY.plusWeeks(1), SampleUi.TODAY.plusDays(9)).inOrder()
        compose.onNodeWithText("Today").assertDoesNotExist()
    }

    /** A touch anywhere restarts the 2-minute wait, and still reaches what was touched. */
    @Test
    fun aTouchIsSeenWithoutBeingTaken() {
        var touches = 0
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), Modifier.onEveryTouch { touches++ }, onAdd = { added += it }) }
        compose.onNodeWithTag("week_add_${SampleUi.TODAY}").performClick()
        assertThat(touches).isEqualTo(1)
        assertThat(added).containsExactly(SampleUi.TODAY)
    }

    /** 4c §6.6: a later week goes back to this week after 2 minutes without a touch; a touch starts the wait again. */
    @Test
    fun aLaterWeekGoesBackToThisWeekAfterTwoMinutesWithoutATouch() {
        compose.mainClock.autoAdvance = false
        lateinit var shown: WeekShown
        show {
            shown = rememberWeekShown(SampleUi.TODAY)
            Text(weekTitle(shown.weeks))
        }
        compose.runOnIdle { shown.show(2) }
        compose.mainClock.advanceTimeBy(BACK_TO_THIS_WEEK_MS - 1_000)
        compose.onNodeWithText("In 2 weeks").assertExists()
        compose.runOnIdle { shown.touched() }
        compose.mainClock.advanceTimeBy(BACK_TO_THIS_WEEK_MS - 1_000)
        compose.onNodeWithText("In 2 weeks").assertExists()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("This week").assertExists()
    }

    /** 4c §6.6: at midnight the view is back on this week, whatever it showed. */
    @Test
    fun aLaterWeekGoesBackToThisWeekAtMidnight() {
        var today by mutableStateOf(SampleUi.TODAY)
        lateinit var shown: WeekShown
        show {
            shown = rememberWeekShown(today)
            Text(weekTitle(shown.weeks))
        }
        compose.runOnIdle { shown.show(MAX_WEEKS_AHEAD + 1) }
        compose.onNodeWithText("In 3 weeks").assertExists()
        today = today.plusDays(1)
        compose.onNodeWithText("This week").assertExists()
    }
```
`ui/CardHostsMidnightRolloverTest.kt`, add (it already has the `clockScope` field from Task 6; imports `androidx.compose.ui.test.performClick`, `kotlinx.coroutines.flow.MutableStateFlow` if not there):
```kotlin
    /** 4c §6.6: › loads the next week from the store. */
    @Test
    fun weekViewHostShowsTheWeekItStepsTo() {
        val now = LocalDateTime.of(2026, 9, 23, 12, 0).atZone(london).toInstant().toEpochMilli()
        val clock = HouseholdClock(zone.zone, WallClock { now }, clockScope, MutableStateFlow(now))
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator(), LocalOverlayHost provides RecordingOverlay()) {
                CulveryTheme(dark = true) {
                    val today = rememberToday(clock) ?: return@CulveryTheme
                    WeekViewHost(repo, editor, today, rememberNowMillis(clock), onReconnect = {})
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("week_day_2026-09-23").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("week_later").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("week_day_2026-10-06").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("week_day_2026-09-30").assertExists()
        compose.onNodeWithTag("week_day_2026-09-29").assertDoesNotExist()
        compose.onNodeWithText("Next week").assertExists()
    }
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*WeekViewTest*" --tests "*CardHostsMidnightRolloverTest*"`
Expected: FAIL to compile ("No parameter with name 'weeksAhead'", "Unresolved reference 'rememberWeekShown'").

- [ ] **Step 4: The arrows, the chip and the wait**

`ui/CalendarType.kt`, in `CalendarDimens` after `reconnectIcon`:
```kotlin
    // Looking ahead (4c §6.6): ‹ › are the date picker's 48 dp round buttons, 8 apart and 16 before the title; the
    // This week chip is a 44 dp `surf2` pill 16 after it. An arrow at its end shows at 30%.
    val weekStepGap = 8.dp
    val weekTitleGap = 16.dp
    const val DISABLED_ALPHA = 0.3f
```
`ui/Pickers.kt`: `private fun RoundButton(icon: String, label: String, tag: String, onClick: () -> Unit)` becomes
```kotlin
@Composable
internal fun RoundButton(icon: String, label: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(CalendarDimens.pageButton)
            .alpha(if (enabled) 1f else CalendarDimens.DISABLED_ALPHA)
            .clip(CircleShape)
            .background(c.surf2)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
    ) {
        HhIcon(icon, size = CalendarDimens.pageButtonIcon, tint = c.ink, contentDescription = label)
    }
}
```
`ui/WeekView.kt`:
- `data class WeekViewState(val week: WeekUi, val today: LocalDate, val sync: SyncStatusUi, val nowMillis: Long)` → add `, val weeksAhead: Int = 0` (KDoc: `/** [weeksAhead]: how many weeks after this one [week] is (4c D10). */`).
- the KDoc of `WeekView` becomes:
```kotlin
/**
 * Hand-off §2 and §7: seven days, person-coloured chips and the sync state. ‹ › step a week at a time, from this week
 * (today and six days) to [MAX_WEEKS_AHEAD] weeks on, inside the synced window (4c D10), through [onWeeksAhead]; This
 * week jumps back. With [onAdd] (there is a writable master calendar), Add event sits right of the legend and adds on
 * the first day shown, and a tap on the space below a column's chips, or on its hint, adds on that column's day; chip
 * taps still open their event. Without it, the legend keeps a 24 dp gap to the right edge. The reconnect chip runs
 * [onReconnect] (3a design §4.3).
 */
```
- the parameters gain `onWeeksAhead: (Int) -> Unit = {},` before `onReconnect`.
- `Text("This week", style = CalendarType.weekTitle, color = c.ink)` becomes
```kotlin
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.weekStepGap)) {
                            RoundButton(Icons.CHEVRON_LEFT, "Earlier week", "week_earlier", enabled = state.weeksAhead > 0) {
                                onWeeksAhead(state.weeksAhead - 1)
                            }
                            RoundButton(Icons.CHEVRON_RIGHT, "Later week", "week_later", enabled = state.weeksAhead < MAX_WEEKS_AHEAD) {
                                onWeeksAhead(state.weeksAhead + 1)
                            }
                        }
                        Spacer(Modifier.width(CalendarDimens.weekTitleGap))
                        Text(weekTitle(state.weeksAhead), style = CalendarType.weekTitle, color = c.ink, maxLines = 1)
                        if (state.weeksAhead > 0) {
                            Spacer(Modifier.width(CalendarDimens.weekTitleGap))
                            ThisWeekChip { onWeeksAhead(0) }
                        }
                    }
```
(the arrows sit together before the title, so › stays under a finger tapping through the weeks while the title's width changes.)
- `AddButton("Add event", "week_add_event") { onAdd(state.today) }` → `{ onAdd(state.week.start) }`.
- after `ReconnectChip`, add
```kotlin
/** Back to this week (4c §6.6): a quiet 44 dp pill beside a later week's title. */
@Composable
private fun ThisWeekChip(onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("week_this_week")
            .height(CalendarDimens.touchTarget)
            .clip(RoundedCornerShape(CalendarDimens.pillRadius))
            .background(c.surf2)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.pillPaddingH),
    ) {
        Text("This week", style = CalendarType.pill, color = c.ink, maxLines = 1)
    }
}

/** After this long without a touch, a later week goes back to this week (4c §6.6). */
internal const val BACK_TO_THIS_WEEK_MS = 120_000L

/** Which week the Calendar tab shows (4c D10): [weeks] after this one, 0 to [MAX_WEEKS_AHEAD]. */
@Stable
internal class WeekShown {
    var weeks by mutableIntStateOf(0)
        private set
    var touches by mutableIntStateOf(0)
        private set

    fun show(weeks: Int) {
        this.weeks = weeks.coerceIn(0, MAX_WEEKS_AHEAD)
    }

    fun touched() {
        touches++
    }
}

/**
 * A new [WeekShown] each [today], so the view is back on this week at midnight; a later week goes back after
 * [BACK_TO_THIS_WEEK_MS] without a touch.
 */
@Composable
internal fun rememberWeekShown(today: LocalDate): WeekShown {
    val shown = remember(today) { WeekShown() }
    LaunchedEffect(shown, shown.weeks, shown.touches) {
        if (shown.weeks > 0) {
            delay(BACK_TO_THIS_WEEK_MS)
            shown.show(0)
        }
    }
    return shown
}

/** Calls [onTouch] for every finger that goes down inside, before the children see it, without taking it. */
internal fun Modifier.onEveryTouch(onTouch: () -> Unit): Modifier = pointerInput(onTouch) {
    awaitPointerEventScope {
        while (true) {
            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) onTouch()
        }
    }
}
```
(imports: `androidx.compose.runtime.LaunchedEffect`, `Stable`, `getValue`, `setValue`, `mutableIntStateOf`, `remember`; `androidx.compose.ui.input.pointer.PointerEventPass`, `PointerEventType`; `kotlinx.coroutines.delay`; `uk.co.siland.culvery.capability.calendar.MAX_WEEKS_AHEAD`, `weekTitle`; `uk.co.siland.culvery.core.ui.Icons`.)

`ui/CardHosts.kt`, `WeekViewHost` becomes (KDoc: "The week shown is today plus six days, or a later one the family stepped to (4c D10); [today] moves at midnight, so the week rolls with it and is back on this week. Shows nothing until both flows load. …" — keep the rest of the KDoc):
```kotlin
@Composable
internal fun WeekViewHost(
    repo: CalendarRepository,
    editor: CalendarEditor,
    today: LocalDate,
    nowMillis: Long,
    onReconnect: (Connection) -> Unit,
) {
    val open = rememberEventOpener(repo, editor, today)
    val add = rememberEventAdder(repo, editor, today)
    val shown = rememberWeekShown(today)
    val start = today.plusWeeks(shown.weeks.toLong())
    // produceState keeps the week on screen while the next one loads, so a step doesn't blank the view.
    val week: WeekUi? by produceState<WeekUi?>(null, repo, start) { repo.week(start).collect { value = it } }
    val sync: SyncStatusUi? by repo.syncStatus.collectAsState(initial = null)
    val w = week ?: return
    val s = sync ?: return
    WeekView(
        WeekViewState(w, today, s, nowMillis, shown.weeks),
        modifier = Modifier.onEveryTouch(shown::touched),
        onOpen = open,
        onAdd = add,
        onWeeksAhead = shown::show,
        onReconnect = { s.reconnect?.let(onReconnect) },
    )
}
```
(imports `androidx.compose.runtime.produceState`, `androidx.compose.ui.Modifier`.)

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*WeekViewTest*" --tests "*CardHostsMidnightRolloverTest*"`
Expected: PASS. If `aLaterWeekGoesBackToThisWeekAfterTwoMinutesWithoutATouch` sees the delay never fire, the effect isn't on the test clock: stop and report rather than wait in real time.

- [ ] **Step 5: Screenshots of a later week, and the header everywhere**

`ui/WeekScreenshotTest.kt`: `snap` gains `weeksAhead: Int = 0` after `canAdd`, passed as `WeekViewState(week, SampleUi.TODAY, sync, now, weeksAhead)`; add
```kotlin
    @Test fun weekNextLight() = snap("week_next_light", false, fresh, SampleUi.weekAhead(1), canAdd = true, weeksAhead = 1)
    @Test fun weekNextDark() = snap("week_next_dark", true, fresh, SampleUi.weekAhead(1), canAdd = true, weeksAhead = 1)
    @Test fun weekFurthestLight() = snap("week_furthest_light", false, fresh, SampleUi.weekAhead(3), canAdd = true, weeksAhead = 3)
    @Test fun weekFurthestDark() = snap("week_furthest_dark", true, fresh, SampleUi.weekAhead(3), canAdd = true, weeksAhead = 3)
```
Run: `./gradlew :capability:calendar:recordRoborazziDebug --tests "*WeekScreenshotTest*"`
Look at each `week_*.png`: the five existing ones differ only in the header (‹ greyed, › before "This week"; columns, legend, Add event and the subtitle unmoved, the reconnect chip still below the subtitle); `week_next_*` say "Next week" with the This week chip and no Today ring; `week_furthest_*` say "In 3 weeks" with › greyed. Anything else moved: stop and report.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 7: Commit**

```bash
git add capability/calendar
git commit -m "Step the Calendar tab up to four weeks ahead, and name the day when an event is added further out"
```

---

### Task 15: C3 and C4 — NeedsSignIn after a refused write; all-day events by their dates (§6.3; ruling 13, plan review 17)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`, `db/CalendarDatabase.kt`, `CalendarRepository.kt`, `PendingOverlay.kt`, `CalendarEditor.kt`, `CalendarSync.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`, `CalendarRepositoryTest.kt`, `CalendarEditorTest.kt`

**Interfaces:**
- Consumes: Task 12's store; Task 14's `opened` clock in `CalendarEditorTest`.
- Produces: `CalendarDao.markSyncTime(id: String, at: Long)`; `internal fun StoredEvent.isOn(date: LocalDate, zone: ZoneId): Boolean` (`PendingOverlay.kt`). `CalendarStore.markSynced` keeps `NEEDS_SIGN_IN` while the connection has queued changes; `CalendarStore.writeAccepted(connectionId: String, nowMillis: Long)` clears it when a write gets through.

- [ ] **Step 1: Write the failing tests**

`CalendarSyncTest.kt`:
```kotlin
    /** C3: a write refused for sign-in keeps the reconnect chip while reads still work, until a reconnect. */
    @Test
    fun aWriteRefusedForSignInKeepsTheConnectionNeedingSignInWhileReadsWork() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null)
        w.failWith = NeedsSignInException("a calendar scope is missing")
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.NeedsSignIn)
        // A later pass, before the change is due again: the reads work, the chip stays.
        now = now.plusSeconds(10)
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.NeedsSignIn)
        assertThat(store.connectionsNow().single().lastSyncMillis).isEqualTo(now.toEpochMilli())
        store.reconnect("c1", now.toEpochMilli())
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
    }

    @Test
    fun aReadThatNeededSigningInClearsOnceReadsWorkWithNothingQueued() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        a.failWith = NeedsSignInException("expired")
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.NeedsSignIn)
        a.failWith = null
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
    }
```
`CalendarRepositoryTest.kt`:
```kotlin
    /** C4: an all-day event synced in another zone shows on its own date only, not straddling two. */
    @Test
    fun anAllDayEventSyncedInAnotherZoneShowsOnItsOwnDate() = runTest {
        val auckland = ZoneId.of("Pacific/Auckland")
        store.applySync(
            "c1", "s-family", DateRange(window.start, window.endExclusive, auckland),
            SyncResult(listOf(allDayOn("Bin day", sept(23))), emptyList(), null, fullReplace = true),
        )
        assertThat(repo.day(sept(23)).first().map { it.title }).containsExactly("Bin day")
        assertThat(repo.day(sept(22)).first()).isEmpty()
        assertThat(repo.day(sept(24)).first()).isEmpty()
    }
```
`CalendarEditorTest.kt` (`ConnectionHealth` and `NeedsSignInException` are same-package):
```kotlin
    /** Plan review 17: a save the provider accepts shows sign-in works again, so the chip a refused one left goes. */
    @Test
    fun anAcceptedSaveClearsTheSignInARefusedOneSet() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = NeedsSignInException("a calendar scope is missing")
        assertThat(editor.create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.NeedsSignIn)
        writer.failWith = null
        assertThat(editor.create(draft("Pizza night", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        val stored = store.connectionsNow().single()
        assertThat(stored.health).isEqualTo(ConnectionHealth.Ok)
        assertThat(stored.needsSignInSinceMillis).isNull()
    }
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSyncTest.aWriteRefused*" --tests "*CalendarSyncTest.aReadThatNeeded*" --tests "*CalendarRepositoryTest.anAllDayEvent*" --tests "*CalendarEditorTest.anAcceptedSave*"`
Expected: `aWriteRefusedForSignIn…` FAILS (the second pass's read sets Ok); `aReadThatNeeded…` PASSES (today's behaviour, kept); `anAllDayEvent…` FAILS ("Bin day" also on the 22nd); `anAcceptedSave…` FAILS (the health stays NeedsSignIn).

- [ ] **Step 2: C3 — keep NeedsSignIn while changes wait for it**

`db/CalendarDatabase.kt`, in `CalendarDao` after `markSynced`:
```kotlin
    @Query("UPDATE connection SET lastSyncMillis = :at WHERE id = :id")
    suspend fun markSyncTime(id: String, at: Long)
```
`CalendarStore.kt`, `markSynced` becomes:
```kotlin
    /**
     * A pass read every calendar. A connection needing sign-in while changes wait (a write was refused for it) stays so
     * until a reconnect, so the chip doesn't vanish while writes keep failing (4c C3); otherwise it is Ok again.
     */
    suspend fun markSynced(connectionId: String, atMillis: Long) = db.withTransaction {
        val row = dao.connection(connectionId) ?: return@withTransaction
        if (row.health == ConnectionHealth.NeedsSignIn.code() && dao.countOutboxOf(connectionId) > 0) {
            dao.markSyncTime(connectionId, atMillis)
            return@withTransaction
        }
        endPause(row, atMillis)
        dao.markSynced(connectionId, atMillis)
    }

    /** A write the provider accepted shows sign-in works: a connection needing it is Ok again (plan review 17). */
    suspend fun writeAccepted(connectionId: String, nowMillis: Long) = db.withTransaction {
        val row = dao.connection(connectionId) ?: return@withTransaction
        if (row.health == ConnectionHealth.NeedsSignIn.code()) setHealth(connectionId, ConnectionHealth.Ok, nowMillis)
    }
```
Both places a write is accepted call it straight after storing it, inside the same `try`:
- `CalendarEditor.kt`, `attempt(…)`: after `store.applyAcceptedWrite(to.connection.id, to.source.id, remoteId, outcome, zone.current())` add `store.writeAccepted(to.connection.id, clock.nowMillis())`.
- `CalendarSync.kt`, `complete(…)`: after `store.applyAcceptedWrite(conn.id, source.id, change.remoteId, accepted, zone, completing = change.id)` add `store.writeAccepted(conn.id, clock.nowMillis())`.

- [ ] **Step 3: C4 — all-day events by their own dates**

`PendingOverlay.kt`, at the end:
```kotlin
/**
 * Whether this event shows on [date] in [zone]. An all-day event goes by its own dates (end exclusive; a zero-length one
 * on its start date): its sort keys are midnight in the zone it was synced in, which a zone change leaves stale (4c C4).
 */
internal fun StoredEvent.isOn(date: LocalDate, zone: ZoneId): Boolean {
    val from = start
    val to = end
    if (from is EventTime.AllDay && to is EventTime.AllDay) {
        return !date.isBefore(from.date) && (date.isBefore(to.date) || date == from.date)
    }
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return spanOverlaps(startSort, endSort, dayStart, dayEnd)
}
```
(import `java.time.LocalDate`.)
`CalendarRepository.kt`, in `days(…)`:
```kotlin
    fun days(start: LocalDate, count: Int): Flow<List<DayUi>> = zone.zone.flatMapLatest { z ->
        // Wide enough for all-day rows synced in any other zone; each day then picks its own (4c C4).
        val from = millis(start, z) - ZONE_MARGIN_MS
        val to = millis(start.plusDays(count.toLong()), z) + ZONE_MARGIN_MS
        combine(store.eventsBetween(from, to), store.pending(), catalog, household.peopleWithFamily) { events, pending, cat, people ->
            val byId = people.associateBy { it.id }
            val shown = overlayPending(events, pending, cat::source, z, from, to)
            (0 until count).map { i ->
                val date = start.plusDays(i.toLong())
                DayUi(
                    date,
                    shown.filter { it.event.isOn(date, z) }
                        .map { it.event.toUi(date, z, byId, cat, it.syncing) }
                        .sortedWith(compareByDescending<EventUi> { it.allDay }.thenBy { it.startSort }.thenBy { it.title }),
                )
            }
        }
    }
```
(keeping Task 7's `.distinctUntilChanged().flowOn(Dispatchers.Default)` after it), and in its companion:
```kotlin
        /** Two days: more than any two zones' offsets differ. */
        const val ZONE_MARGIN_MS = 2 * 86_400_000L
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS, the four new tests included.

- [ ] **Step 5: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 6: Commit**

```bash
git add capability/calendar
git commit -m "Keep a connection needing sign-in while its writes wait for it, clear it when one gets through, and place all-day events by their own dates"
```

---

### Task 16: One calendar that can't be read, E3, the master-gone count, the `addConnection` test (§6.3 E3, §6.4, §6.5; D6, C5, C7, C8, M7; rulings 7, 12, 14; plan review 11)

**Review:** opus (connection and calendar health).

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`, `CalendarStore.kt`, `Stored.kt`, `CalendarSync.kt`, `SourceRefresher.kt`, `ui/ReviewCalendars.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`, `CalendarStoreTest.kt`, `SourceRefresherTest.kt`, `ui/ReviewCalendarsTest.kt`, `ui/ReviewScreenshotTest.kt`
- Screenshots (new): `capability/calendar/src/test/screenshots/review_read_problem_dark.png`, `review_read_problem_light.png`

**Interfaces:**
- Consumes: Task 12's `readProblem` column and pruning; Task 3's log rules.
- Produces:
  - `StoredSource.readProblem: String?`; `internal const val READ_REFUSED = "REFUSED"`.
  - `CalendarStore.markReadRefused(connectionId: String, sourceId: String): Boolean` (true when newly set); `applySync` clears it; `setMapping(…, visible = false)` clears it.
  - `data class SourcesRefreshed(val masterCleared: Boolean, val droppedChanges: Int)` returned by `CalendarStore.refreshSources`.
  - `fun masterGone(serviceName: String, droppedChanges: Int): String`.
  - `SourceRefresher.flag(connectionId, sourceId)` no longer filters (the store's `markReadRefused` decides).
  - `CalendarSync.syncSource(…)` and `healthAfter(…)` return `ConnectionHealth?`: null is one calendar refused while its connection answers. A pass in which every visible calendar was refused read nothing: the connection is Unreachable and not marked synced (plan review 11; `StoredSource` holds no primary flag, so "or the primary" can't be told apart and isn't).

- [ ] **Step 1: Write the failing engine tests**

`CalendarSyncTest.kt`: replace `aSourceGoneFlagsARefreshThatRemovesIt` and `aSourceGoneThatIsStillListedIsKeptAndNotReadAgainEachPass` with:
```kotlin
    /** §6.4: a calendar deleted in the service is flagged once, and the next pass's refresh removes it. */
    @Test
    fun aRefusedCalendarThatIsGoneLeavesAtTheNextPass() = runTest {
        connect("c1", "calendar.a", s1, s2)
        val sync = engine()
        sync.syncAll()
        a.failFor = mapOf("s2" to SourceGoneException("calendar deleted"))
        a.sourceList = listOf(s1)
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
        assertThat(store.source("c1", "s2")!!.readProblem).isEqualTo(READ_REFUSED)
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(store.sources().first().map { it.source.id }).containsExactly("s1")
    }

    /** D6, M7: one refused calendar still listed keeps its row's message; the connection stays healthy, it is read each pass, and the list isn't re-read. */
    @Test
    fun aRefusedCalendarStillListedKeepsItsProblemAndTheConnectionStaysHealthy() = runTest {
        connect("c1", "calendar.a", s1, s2)
        val sync = engine()
        sync.syncAll()
        a.failFor = mapOf("s2" to SourceGoneException("403"))
        now = now.plusSeconds(300)
        sync.syncAll()
        now = now.plusSeconds(300)
        sync.syncAll()
        val reads = a.sourcesCalls
        val syncsOfS2 = a.calls.count { it.sourceId == "s2" }
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(a.sourcesCalls).isEqualTo(reads)
        assertThat(a.calls.count { it.sourceId == "s2" }).isEqualTo(syncsOfS2 + 1)
        assertThat(store.source("c1", "s2")!!.readProblem).isEqualTo(READ_REFUSED)
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
        assertThat(store.connectionsNow().single().lastSyncMillis).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun aRefusedCalendarIsClearedByItsNextGoodRead() = runTest {
        connect("c1", "calendar.a", s1, s2)
        val sync = engine()
        a.failFor = mapOf("s2" to SourceGoneException("403"))
        sync.syncAll()
        a.failFor = emptyMap()
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(store.source("c1", "s2")!!.readProblem).isNull()
    }

    /** E3: once the connection can't be reached, its other calendars wait for the next pass. */
    @Test
    fun onceAConnectionCantBeReachedItsOtherCalendarsWait() = runTest {
        connect("c1", "calendar.a", s1, s2)
        a.failWith = UnreachableException("no network")
        engine().syncAll()
        assertThat(a.calls).hasSize(1)
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
    }

    @Test
    fun onceAConnectionNeedsSigningInItsOtherCalendarsWait() = runTest {
        connect("c1", "calendar.a", s1, s2)
        a.failWith = NeedsSignInException("expired")
        engine().syncAll()
        assertThat(a.calls).hasSize(1)
    }

    /** Plan review 11: with every calendar refused nothing was read, so the connection isn't synced and says it can't reach them. */
    @Test
    fun everyCalendarRefusedIsNotASync() = runTest {
        connect("c1", "calendar.a", s1, s2)
        val sync = engine()
        sync.syncAll()
        val synced = store.connectionsNow().single().lastSyncMillis
        a.failFor = mapOf("s1" to SourceGoneException("403"), "s2" to SourceGoneException("403"))
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(store.connectionsNow().single().lastSyncMillis).isEqualTo(synced)
    }

    /** E3: a refusal of one calendar isn't the connection failing. */
    @Test
    fun aRefusedCalendarDoesNotStopTheOthers() = runTest {
        connect("c1", "calendar.a", s1, s2)
        a.failFor = mapOf("s1" to SourceGoneException("403"))
        engine().syncAll()
        assertThat(a.calls.map { it.sourceId }).containsExactly("s1", "s2").inOrder()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
    }
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSyncTest*"`
Expected: FAIL to compile (`readProblem`, `READ_REFUSED` unresolved).

- [ ] **Step 2: The store's half**

`db/CalendarDatabase.kt`, in `CalendarDao`:
- `suspend fun deleteOutboxOfSource(connectionId: String, sourceId: String)` returns `Int` (`: Int`).
- add
```kotlin
    @Query("UPDATE source SET readProblem = :problem WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun setReadProblem(connectionId: String, sourceId: String, problem: String)

    /** Only a row that has one, so a pass that reads well writes nothing (and wakes no flow). */
    @Query("UPDATE source SET readProblem = NULL WHERE connectionId = :connectionId AND sourceId = :sourceId AND readProblem IS NOT NULL")
    suspend fun clearReadProblem(connectionId: String, sourceId: String)
```
`Stored.kt`: `StoredSource` gains `val readProblem: String? = null,` after `isMaster`, with the KDoc line `[readProblem]: [READ_REFUSED] while the service refuses this calendar's events although it still lists it (4c §6.4).`

`CalendarStore.kt`:
- after `REMOVE_CHUNK`'s companion, at top level beside `cursorKey`:
```kotlin
/** A calendar the service still lists but whose events it refuses (403/404): its row says so (4c design §6.4). */
internal const val READ_REFUSED = "REFUSED"

/** What refreshing a connection's calendars did (3a design D7, 4c C7). */
data class SourcesRefreshed(val masterCleared: Boolean, val droppedChanges: Int)
```
- `refreshSources` returns `SourcesRefreshed`: its signature's `: Boolean` becomes `: SourcesRefreshed`; `?: return@withTransaction false` becomes `?: return@withTransaction SourcesRefreshed(masterCleared = false, droppedChanges = 0)`; add `var dropped = 0` beside `masterCleared`; `removeSourceRows(connectionId, gone.sourceId)` becomes `dropped += removeSourceRows(connectionId, gone.sourceId)`; the last line `masterCleared` becomes `SourcesRefreshed(masterCleared, dropped)`; the KDoc's "Returns true when the master went, or became read-only, and was cleared." becomes "Says whether the master went, or became read-only, and was cleared, and how many queued changes went with removed calendars."
- `removeSourceRows` returns the outbox count:
```kotlin
    private suspend fun removeSourceRows(connectionId: String, sourceId: String): Int {
        val dropped = dao.deleteOutboxOfSource(connectionId, sourceId)
        dao.deleteSyncState(connectionId, sourceId)
        dao.deleteEventsForSource(connectionId, sourceId)
        dao.deleteSource(connectionId, sourceId)
        return dropped
    }
```
- `setMapping`: after `dao.setMapping(…)` add `if (!visible) dao.clearReadProblem(connectionId, sourceId)` with the comment `// A hidden calendar isn't read, so its problem has no news (4c ruling 12).`
- add after `queuedChanges`:
```kotlin
    /** Records that the service refused [sourceId]'s events; true when it had no problem before (4c §6.4). */
    suspend fun markReadRefused(connectionId: String, sourceId: String): Boolean = db.withTransaction {
        val row = dao.source(connectionId, sourceId) ?: return@withTransaction false
        if (row.readProblem != null) return@withTransaction false
        dao.setReadProblem(connectionId, sourceId, READ_REFUSED)
        true
    }
```
- in `applySync`'s transaction, after the source check, add `dao.clearReadProblem(connectionId, sourceId)`.
- `SourceEntity.toStored()` passes `readProblem = readProblem`.

`SourceRefresher.kt`:
- delete the `stillListed` map and its comment; `flag` becomes
```kotlin
    /** A sync found [sourceId] refused for the first time (4c §6.4): refresh [connectionId] at the next pass. */
    fun flag(connectionId: String, sourceId: String) {
        flagged.getOrPut(connectionId) { ConcurrentHashMap.newKeySet() } += sourceId
    }
```
- in `refreshIfDue`, delete the lines from `val listed = sources.mapTo(HashSet()) { it.id }` to `kept += gone.filter { it in listed }`; the end becomes
```kotlin
        val household = people()
        val outcome = store.refreshSources(id, sources, now) { defaultMapping(it, household) }
        refreshed += id
        flagged[id]?.removeAll(gone)
        if (outcome.masterCleared) toaster.show(masterGone(provider.descriptor.displayName, outcome.droppedChanges))
```
- `masterGone` becomes (C7, ruling 14):
```kotlin
/**
 * 3a design D7, 4a design §4.5: the master calendar went, or became read-only, so the add buttons hide until another is
 * chosen; 4c C7 adds the queued changes that went with it.
 */
fun masterGone(serviceName: String, droppedChanges: Int): String =
    "$serviceName: can't find the master calendar — choose a new one in Settings › Calendars." + when (droppedChanges) {
        0 -> ""
        1 -> " 1 change waiting to sync was dropped."
        else -> " $droppedChanges changes waiting to sync were dropped."
    }
```

- [ ] **Step 3: The engine's half (E3, §6.4)**

`CalendarSync.kt`:
- in `sync(…)`, the loop becomes
```kotlin
        val visible = store.visibleSourcesFor(conn.id)
        var refused = 0
        for (source in visible) {
            val health = syncSource(provider, conn, source.source, window)
            if (health == null) {
                refused++
                continue
            }
            if (health.severity() > worst.severity()) worst = health
            // E3: the connection itself failed; its other calendars would fail the same way, each costing a timeout.
            if (health == ConnectionHealth.Unreachable || health == ConnectionHealth.NeedsSignIn) break
        }
        // Every calendar refused: the service answers, but nothing was read, so this isn't a sync (plan review 11).
        if (visible.isNotEmpty() && refused == visible.size) worst = ConnectionHealth.Unreachable
```
(the `if (worst == ConnectionHealth.Ok) markSynced … else setHealth …` after it is unchanged.)
- `syncSource` returns `ConnectionHealth?` (its KDoc gains "Null: the service refused this calendar alone (4c §6.4).").
- `healthAfter` becomes `private suspend fun healthAfter(…): ConnectionHealth?`, and its `SourceGoneException` branch:
```kotlin
        is SourceGoneException -> {
            // §6.4: one calendar refused while its connection answers. The connection stays healthy; the row says so.
            if (store.markReadRefused(conn.id, sourceId)) {
                Log.w(TAG, "${conn.id}: a calendar refused to be read (${e::class.simpleName}); flagging a refresh of its calendars")
                refresher.flag(conn.id, sourceId)
            }
            null
        }
```
(Its KDoc keeps saying what is logged; add "A refused calendar (SourceGone) is the calendar's problem, not the connection's: null (4c §6.4).")

- [ ] **Step 4: Point the older tests at the new shapes**

- `SourceRefresherTest.aGoneSourceStillListedIsNotFlaggedAgainUntilTheDailyRefresh`: delete it — the store now remembers a refusal (`markReadRefused` returns false the second time), which `CalendarSyncTest.aRefusedCalendarStillListedKeepsItsProblemAndTheConnectionStaysHealthy` pins.
- Every test that asserted `refreshSources(…)`'s Boolean (`CalendarStoreTest`, `SourceRefresherTest`) asserts `.masterCleared` instead; every `masterGone(name)` in tests becomes `masterGone(name, 0)`.
- In `SourceRefresherTest` add:
```kotlin
    /** C7: changes waiting for a master that went are counted in its toast. */
    @Test
    fun aMasterDeletedWithChangesWaitingSaysHowManyWent() = runTest {
        connect(sources = listOf(primary, other), master = other.id)
        repeat(2) {
            store.enqueue(PendingChange(0, "c1", other.id, "e$it", ChangeKind.DELETE, draft = null, attempts = 0, nextAttemptMillis = 0, createdMillis = 0))
        }
        provider.sourceList = listOf(primary)
        refresh()
        assertThat(toasts.messages).containsExactly(
            "Service: can't find the master calendar — choose a new one in Settings › Calendars. 2 changes waiting to sync were dropped.",
        )
    }
```
(use the names `SourceRefresherTest` already has for its provider, its second source, its connection id, its toaster and the provider's display name; its `aMasterDeletedInGoogleIsClearedWithOneToast` shows them.)
- `CalendarStoreTest`, add:
```kotlin
    @Test
    fun hidingACalendarClearsItsReadProblem() = runTest {
        connect("s1", "s2")
        assertThat(store.markReadRefused("c1", "s2")).isTrue()
        assertThat(store.markReadRefused("c1", "s2")).isFalse()
        store.setMapping("c1", "s2", PersonId.FAMILY, visible = false)
        assertThat(store.source("c1", "s2")!!.readProblem).isNull()
    }

    /** C8: a connection that fails part-way is never half stored. */
    @Test
    fun addConnectionWritesNothingWhenItFails() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.addConnection(conn, listOf(CalendarSource("s1", "One", writable = true)), emptyMap(), masterSourceId = "missing") }
        }
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(store.sources().first()).isEmpty()
    }
```
(imports `org.junit.Assert.assertThrows`, `kotlinx.coroutines.runBlocking`.)

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS, apart from the UI tests Step 5 adds.

- [ ] **Step 5: Write the failing UI tests**

`ui/ReviewCalendarsTest.kt`, add:
```kotlin
    private fun showSources(vararg sources: StoredSource) = compose.setContent {
        CulveryTheme(dark = true) {
            ReviewCalendars(
                title = "Calendars",
                connections = listOf(ReviewConnection(row(), sources.toList())),
                people = listOf(Person.Family, mia),
                nowMillis = now,
                busy = false,
                picking = null,
                confirming = null,
                connectable = emptyList(),
                actions = ReviewActions(onShown = { source, visible -> shown = source.source.id to visible }),
            )
        }
    }

    /** D6: the row says so and offers Hide; the connection's line stays "Synced 2 min ago". */
    @Test
    fun aCalendarThatCantBeReadSaysSoAndOffersToHideIt() {
        showSources(family, swim.copy(readProblem = READ_REFUSED))
        compose.onNodeWithText("Can't read this calendar — check it's still shared with this account").assertExists()
        compose.onNodeWithText("Synced 2 min ago").assertExists()
        compose.onNodeWithText("Hide this calendar").performClick()
        assertThat(shown).isEqualTo("swim" to false)
    }

    @Test
    fun theMasterThatCantBeReadAsksForAnotherMasterFirst() {
        showSources(family.copy(readProblem = READ_REFUSED), swim)
        compose.onNodeWithText("Choose another master calendar first").assertExists()
        compose.onNodeWithText("Hide this calendar").assertDoesNotExist()
    }
```
`ui/ReviewScreenshotTest.kt`, add:
```kotlin
    /** §6.4: an unreadable calendar and an unreadable master, in a healthy connection. */
    private fun readProblem(name: String, dark: Boolean) = snap(name, dark) {
        Box(Modifier.padding(MARGIN).width(COLUMN_W)) {
            ReviewCalendars(
                "Calendars",
                listOf(
                    ReviewConnection(
                        CalendarRow(google, "Google Calendar", Icons.CALENDAR_MONTH, ConnectionHealth.Ok, now - 2 * 60_000),
                        listOf(sources[0].copy(readProblem = READ_REFUSED), sources[1].copy(readProblem = READ_REFUSED), sources[2]),
                    ),
                ),
                listOf(Person.Family, sam, mia), now, false, null, null, emptyList(), ReviewActions(),
            )
        }
    }

    @Test fun reviewReadProblemDark() = readProblem("review_read_problem_dark", true)
    @Test fun reviewReadProblemLight() = readProblem("review_read_problem_light", false)
```
(import `uk.co.siland.culvery.core.ui.Icons`.)

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*ReviewCalendarsTest*"`
Expected: FAIL: the texts don't exist.

- [ ] **Step 6: The row**

`ui/ReviewCalendars.kt`:
- after `MAKE_MASTER` add
```kotlin

// 4c design §6.4.
internal const val CANT_READ_CALENDAR = "Can't read this calendar — check it's still shared with this account"
internal const val HIDE_THIS_CALENDAR = "Hide this calendar"
internal const val CHOOSE_ANOTHER_MASTER = "Choose another master calendar first"
```
- in `SourceRow`, between the first `Row(…) { … }` and `if (picking) {`, add `if (source.readProblem != null) ReadProblem(source, busy, onShown)`;
- add:
```kotlin
/** 4c design §6.4, D6: one calendar that can't be read; Hide it, or, for the master, choose another first. */
@Composable
private fun ReadProblem(source: StoredSource, busy: Boolean, onShown: (StoredSource, Boolean) -> Unit) {
    val c = Culvery.colors
    val id = source.source.id
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reviewItemGap),
        modifier = Modifier.testTag("review_read_problem_$id"),
    ) {
        Text(CANT_READ_CALENDAR, style = CalendarType.subtitle, color = c.danger, modifier = Modifier.weight(1f))
        if (source.isMaster) {
            Text(CHOOSE_ANOTHER_MASTER, style = CalendarType.subtitle, color = c.mute)
        } else {
            HhPillButton(HIDE_THIS_CALENDAR, { onShown(source, false) }, Modifier.testTag("review_hide_$id"), enabled = !busy)
        }
    }
}
```

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*ReviewCalendarsTest*"`
Expected: PASS.

- [ ] **Step 7: Record the screenshots and look**

Run: `./gradlew :capability:calendar:recordRoborazziDebug --tests "*ReviewScreenshotTest.reviewReadProblem*"`
Look at both: the connection row says "Google Calendar · family@example.com" and "Synced 2 min ago" in the muted colour (healthy); under **Family** (Master, "New events go here") a danger-coloured "Can't read this calendar — check it's still shared with this account" and, at its right, "Choose another master calendar first" in muted text; under **Mia's swimming** the same line with a **Hide this calendar** pill; **Chores** as before. Nothing overlaps or truncates the danger line mid-word.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences beyond the two recorded.

- [ ] **Step 9: Commit**

```bash
git add capability/calendar
git commit -m "Show a calendar that can't be read on its own row, keep its connection healthy, and count dropped changes when the master goes"
```

---

### Task 17: Reordering people (§7.1, D7; ruling 25)

**Review:** sonnet.

**Files:**
- Modify: `core/household/src/main/java/uk/co/siland/culvery/core/household/HouseholdRepository.kt`; test `HouseholdRepositoryTest.kt`
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/PeopleEditor.kt`, `PeopleUi.kt`, `SetupCopy.kt`, `SetupDimens.kt`, `pages/PeoplePage.kt`, `steps/HouseholdStep.kt`
- Test: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/PeopleEditorTest.kt`, `PeopleUiTest.kt`, `StepsUiTest.kt`, `SettingsScreenshotTest.kt`, `SetupScreenshotTest.kt`
- Screenshots (re-recorded): `core/setup/src/test/screenshots/settings_people_dark.png`, `settings_people_light.png`

**Interfaces:**
- Consumes: Task 2's `Icons.ARROW_UPWARD`, `Icons.ARROW_DOWNWARD`.
- Produces: `suspend fun HouseholdRepository.move(id: PersonId, up: Boolean): Boolean`; `suspend fun PeopleEditor.move(id: PersonId, up: Boolean): PeopleOutcome`; `PeopleList(members, onEdit, onAdd, onMove: ((Member, Boolean) -> Unit)?)`; `PeoplePane(editor, reorder: Boolean)`.

- [ ] **Step 1: Write the failing repository tests**

`HouseholdRepositoryTest.kt`:
```kotlin
    @Test
    fun movingSomeoneUpSwapsThemWithThePersonAbove() = runTest {
        repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        assertThat(repo.move(mia.id, up = true)).isTrue()
        assertThat(repo.people.first().map { it.name }).containsExactly("Alex", "Mia", "Sam").inOrder()
        assertThat(repo.move(mia.id, up = false)).isTrue()
        assertThat(repo.people.first().map { it.name }).containsExactly("Alex", "Sam", "Mia").inOrder()
    }

    @Test
    fun nobodyMovesAboveTheFirstOrBelowTheLast() = runTest {
        val alex = repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        val sam = repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        assertThat(repo.move(alex.id, up = true)).isFalse()
        assertThat(repo.move(sam.id, up = false)).isFalse()
        assertThat(repo.people.first().map { it.name }).containsExactly("Alex", "Sam").inOrder()
    }
```
Run: `./gradlew :core:household:testDebugUnitTest --tests "*HouseholdRepositoryTest*"`
Expected: FAIL to compile ("Unresolved reference 'move'").

- [ ] **Step 2: Swap two places in one transaction**

`HouseholdRepository.kt`, after `removePerson`:
```kotlin
    /**
     * Swaps [id] with the person above it ([up]) or below it, in one transaction (4c design §7.1); nobody moves past
     * either end, and Family, never in the list, never moves. Returns whether anyone moved.
     */
    suspend fun move(id: PersonId, up: Boolean): Boolean = db.withTransaction {
        val all = dao.all()
        val index = all.indexOfFirst { it.id == id.value }
        val other = all.getOrNull(if (up) index - 1 else index + 1)
        if (index < 0 || other == null) return@withTransaction false
        val person = all[index]
        dao.upsertPerson(person.copy(sortOrder = other.sortOrder))
        dao.upsertPerson(other.copy(sortOrder = person.sortOrder))
        true
    }
```
Run the same tests.
Expected: PASS.

- [ ] **Step 3: Write the failing editor and list tests**

`PeopleEditorTest.kt`:
```kotlin
    /** D7: the open Settings session, no fresh PIN; a toast says who moved. */
    @Test
    fun movingSomeoneTakesTheOpenSessionAndSaysSo() = runTest {
        start()
        val sam = sam()
        assertThat(editor.move(sam.id, up = true)).isEqualTo(PeopleOutcome.Done)
        assertThat(access.requests).isEmpty()
        assertThat(household.people.first().map { it.name }).containsExactly("Sam", "Alex").inOrder()
        assertThat(access.toasts.messages).containsExactly("Sam moved up")
        assertThat(editor.move(sam.id, up = false)).isEqualTo(PeopleOutcome.Done)
        assertThat(access.toasts.messages.last()).isEqualTo("Sam moved down")
    }
```
`PeopleUiTest.kt`: `PeoplePane(editor)` becomes `PeoplePane(editor, reorder = true)` and `PeoplePane(failing)` becomes `PeoplePane(failing, reorder = true)`; add (using the class's existing way of showing the pane over `overlay` — the test at line 73's `setContent` block):
```kotlin
    @Test
    fun theMoveButtonsMoveSomeoneAndNoneGoesPastAnEnd() {
        runBlocking { access.pins.addPerson("Sam", PersonPalette.colors[1], Role.ADULT, null) }
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_up_Sam").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("person_up_Alex").assertDoesNotExist()
        compose.onNodeWithTag("person_down_Sam").assertDoesNotExist()
        compose.onNodeWithContentDescription("Move Sam up").performClick()
        compose.waitUntil(5_000) { runBlocking { household.people.first().map { it.name } } == listOf("Sam", "Alex") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_up_Sam").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("person_down_Sam").assertExists()
    }
```
(If the class has no `show()` helper, wrap its line-73 `setContent` block in one; imports `androidx.compose.ui.test.onNodeWithContentDescription`.)
`StepsUiTest.kt`: `PeoplePane(editor)` → `PeoplePane(editor, reorder = false)`.
`SetupScreenshotTest.kt`: `PeopleList(listOf(alex, sam, mia), onEdit = {}, onAdd = {})` → add `onMove = null` (the wizard's list doesn't reorder: unchanged images).
`SettingsScreenshotTest.kt`: `PeopleList(people, onEdit = {}, onAdd = {})` → add `onMove = { _, _ -> }`.

Run: `./gradlew :core:setup:testDebugUnitTest --tests "*PeopleEditorTest*" --tests "*PeopleUiTest*"`
Expected: FAIL to compile (`move`, `reorder`, `onMove`).

- [ ] **Step 4: The editor, the copy and the buttons**

`SetupCopy.kt`, under `// People (§4.4)`:
```kotlin
// 4c design §7.1.
internal fun moveUpLabel(name: String): String = "Move $name up"

internal fun moveDownLabel(name: String): String = "Move $name down"

internal fun movedUp(name: String): String = "$name moved up"

internal fun movedDown(name: String): String = "$name moved down"
```
`SetupDimens.kt`, after `rowIcon`:
```kotlin

    // Move up / Move down (4c §7.1; not in the spec): 48 dp touch targets around the row's 24 dp icon.
    val moveButton = 48.dp
```
`PeopleEditor.kt`, after `remove`:
```kotlin
    /** 4c design §7.1, D7: the open Settings session (settings.manage), no fresh PIN; a toast says who moved, or that it failed. */
    suspend fun move(id: PersonId, up: Boolean): PeopleOutcome {
        val name = orNull("read a person") { household.person(id)?.name }
        if (name == null) {
            toaster.show(COULD_NOT_SAVE)
            return PeopleOutcome.Refused(COULD_NOT_SAVE)
        }
        access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return PeopleOutcome.Cancelled
        var moved = false
        val outcome = saving("move a person") { moved = household.move(id, up) }
        when {
            outcome is PeopleOutcome.Refused -> toaster.show(outcome.message)
            moved -> toaster.show(if (up) movedUp(name) else movedDown(name))
        }
        return outcome
    }
```
`PeopleUi.kt`:
```kotlin
/**
 * 4a design §4.4: each person with their colour, name, role and whether they have a PIN; tap to edit. With [onMove]
 * (Settings › People, 4c §7.1) each row has Move up and Move down, none above the first or below the last.
 */
@Composable
internal fun PeopleList(members: List<Member>, onEdit: (Member) -> Unit, onAdd: (() -> Unit)?, onMove: ((Member, Boolean) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.rowGap), modifier = Modifier.testTag("people_list")) {
        members.forEachIndexed { i, member ->
            PersonRow(
                member,
                onClick = { onEdit(member) },
                reorderable = onMove != null,
                onUp = if (onMove != null && i > 0) ({ onMove(member, true) }) else null,
                onDown = if (onMove != null && i < members.lastIndex) ({ onMove(member, false) }) else null,
            )
        }
        if (onAdd != null) HhPillButton(ADD_PERSON, onAdd, Modifier.testTag("people_add"), primary = true)
    }
}

@Composable
internal fun PersonRow(member: Member, onClick: () -> Unit, reorderable: Boolean, onUp: (() -> Unit)?, onDown: (() -> Unit)?) {
    val c = Culvery.colors
    val name = member.person.name
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SetupDimens.rowDotGap),
        modifier = Modifier
            .testTag("person_$name")
            .fillMaxWidth()
            .clip(RoundedCornerShape(SetupDimens.rowRadius))
            .background(c.surf)
            .clickable(onClick = onClick)
            .padding(horizontal = SetupDimens.rowPaddingH, vertical = SetupDimens.rowPaddingV),
    ) {
        Box(Modifier.size(SetupDimens.rowDot).clip(CircleShape).background(Color(member.person.color)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SetupDimens.rowLineGap)) {
            Text(name, style = SetupType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${roleName(member.role)} · ${if (member.hasPin) PIN_SET else NO_PIN}", style = SetupType.secondary, color = c.mute, maxLines = 1)
        }
        if (reorderable) {
            MoveButton(Icons.ARROW_UPWARD, moveUpLabel(name), "person_up_$name", onUp)
            MoveButton(Icons.ARROW_DOWNWARD, moveDownLabel(name), "person_down_$name", onDown)
        }
        HhIcon(Icons.CHEVRON_RIGHT, size = SetupDimens.rowIcon, tint = c.mute)
    }
}

/** A Move up or Move down button; at the list's ends its empty space, so the buttons line up. */
@Composable
private fun MoveButton(icon: String, label: String, tag: String, onClick: (() -> Unit)?) {
    if (onClick == null) {
        Spacer(Modifier.size(SetupDimens.moveButton))
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(SetupDimens.moveButton)
            .clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
    ) {
        HhIcon(icon, size = SetupDimens.rowIcon, tint = Culvery.colors.ink, contentDescription = label)
    }
}

/** The people list and its editor sheet: the wizard's Household step and Settings › People ([reorder]). Add person goes at eight. */
@Composable
internal fun PeoplePane(editor: PeopleEditor, reorder: Boolean) {
    val members by editor.members.collectAsState(initial = emptyList())
    val overlay = LocalOverlayHost.current
    val action = rememberSingleAction(Unit) { e -> Log.w(TAG, "Couldn't move a person (${e::class.simpleName})") }
    val open: (Member?) -> Unit = { overlay.showPersonEditor(editor, it, members) }
    PeopleList(
        members,
        onEdit = open,
        onAdd = if (members.size < MAX_PEOPLE) ({ open(null) }) else null,
        onMove = if (reorder) ({ member, up -> action.run { editor.move(member.person.id, up) } }) else null,
    )
}

private const val TAG = "People"
```
(imports: `android.util.Log`, `androidx.compose.foundation.layout.Spacer`, `androidx.compose.ui.semantics.Role`, `uk.co.siland.culvery.core.ui.Icons`, `uk.co.siland.culvery.core.ui.rememberSingleAction`.)
`pages/PeoplePage.kt`: `PeoplePane(editor)` → `PeoplePane(editor, reorder = true)`; its KDoc becomes `/** Settings › People (4a design §4.4): the Household step's list and sheet, and reordering (4c §7.1). */`. `steps/HouseholdStep.kt`: `PeoplePane(editor)` → `PeoplePane(editor, reorder = false)`.

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Re-record the Settings people page and look**

Run: `./gradlew :core:setup:recordRoborazziDebug --tests "*SettingsScreenshotTest.people*"`
Look at both: Alex's row has no up arrow (an empty space) and a down arrow; Sam's row has an up arrow and no down arrow; the arrows sit between the name column and the chevron, ink-coloured, lined up between the rows; nothing truncates the names. The wizard's `people_list_*` images are unchanged (the gate checks).

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences beyond the two recorded.

- [ ] **Step 7: Commit**

```bash
git add core/household core/setup
git commit -m "Reorder people in Settings with Move up and Move down"
```

---

### Task 18: Code health and test health (§7.2, §7.3; ruling 23)

**Review:** opus (the access session's state).

**Files:**
- Modify: `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/Http.kt`, `OpenMeteoForecast.kt`; test `OpenMeteoForecastTest.kt`
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/FlowRetry.kt`; test `FlowRetryTest.kt`
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/DefaultAccessControl.kt` (its tests are the check)
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/WizardRules.kt`, `steps/LocationStep.kt`
- Test: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/StepsUiTest.kt`; `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt`; `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherStoreTest.kt`, `WeatherSyncLoopTest.kt`
- Modify: every `src/test/resources/robolectric.properties` (8: `app`, `capability/calendar`, `capability/weather`, `core/access`, `core/setup`, `provider/calendar-fake`, `provider/calendar-google`, `provider/weather-openmeteo`)

(`HomeScreen`'s test-only `headerItems` default went in Task 5; `HouseholdTickerTest` went with `MinuteTicker` in Task 6.)

**Interfaces:**
- Consumes: nothing new.
- Produces: `internal class ReadFailed : IOException` (`Http.kt`); `internal fun coordinate(value: Double): String` (`OpenMeteoForecast.kt`); `internal suspend fun mayChangeSetup(household: HouseholdRepository, access: AccessControl): Boolean` (`WizardRules.kt`). `retryWithBackoff` keeps its signature.

- [ ] **Step 1: Write the failing Open-Meteo tests**

`OpenMeteoForecastTest.kt`, add:
```kotlin
    /** §7.2: a failure reading the body that isn't an IOException is still the forecast's own failure. */
    @Test
    fun aBodyThatFailsToReadInAnyWayIsWeatherUnavailable() = runTest {
        val broken = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        object : ResponseBody() {
                            override fun contentType(): MediaType? = null

                            override fun contentLength(): Long = -1

                            override fun source(): BufferedSource = throw IllegalStateException("a broken body")
                        },
                    )
                    .build()
            }
            .build()
        val withBrokenBody = OpenMeteoForecast(server.url("/v1/forecast"), broken, clock)
        val failure = runCatching { withBrokenBody.forecast(51.5074, -0.1278, ZoneId.of("Europe/London")) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(WeatherUnavailableException::class.java)
    }

    /** §7.2: Greenwich and the equator are sent as plain numbers, never "-5.0E-4". */
    @Test
    fun coordinatesNearZeroAreSentWithoutAnExponent() = runTest {
        answer(small())
        forecast.forecast(-0.0005, 51.5, ZoneId.of("Europe/London"))
        val asked = server.takeRequest().requestUrl!!
        assertThat(asked.queryParameter("latitude")).isEqualTo("-0.0005")
        assertThat(asked.queryParameter("longitude")).isEqualTo("51.5")
    }

    /** 4b ruling 15: no cause, whose text could hold the URL, and so the coordinates. */
    @Test
    fun everyFailureCarriesNoCause() = runTest {
        answer("{}", code = 500)
        assertThat(failure()?.cause).isNull()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThat(failure()?.cause).isNull()
        answer("<html>Gateway</html>")
        assertThat(failure()?.cause).isNull()
    }

    @Test
    fun aNullDateOrLowDropsThatDayAndANullHourDropsThatHour() = runTest {
        answer(
            small(
                daily = """ "time":["2026-10-01",null,"2026-10-03"],"weather_code":[3,3,3],"temperature_2m_max":[19.0,18.0,17.0],"temperature_2m_min":[11.0,10.0,null],"sunrise":["2026-10-01T07:01",null,null],"sunset":["2026-10-01T18:38",null,null]""",
                hourly = """ "time":["2026-10-01T00:00",null],"temperature_2m":[17.0,16.0],"weather_code":[3,3]""",
            ),
        )
        val f = london()
        assertThat(f.days.map { it.date }).containsExactly(LocalDate.of(2026, 10, 1))
        assertThat(f.hours.map { it.start }).containsExactly(LocalDateTime.of(2026, 10, 1, 0, 0))
    }
```
(imports `okhttp3.MediaType`, `okhttp3.Protocol`, `okhttp3.Response`, `okhttp3.ResponseBody`, `okio.BufferedSource`.)
Run: `./gradlew :provider:weather-openmeteo:testDebugUnitTest`
Expected: `aBodyThatFailsToReadInAnyWayIsWeatherUnavailable` FAILS (an `IllegalStateException` escapes); `coordinatesNearZeroAreSentWithoutAnExponent` FAILS (`-5.0E-4`); the other two PASS (they pin what is already right).

- [ ] **Step 2: Fix both**

`Http.kt`: add
```kotlin
/** Reading the answer failed in a way that isn't I/O. No cause: its text could quote the body. */
internal class ReadFailed : IOException("Reading the answer failed")
```
and in `onResponse` the `catch (e: Throwable)` block becomes two:
```kotlin
                } catch (e: IOException) {
                    // After a cancel this is the closed socket and the continuation is already cancelled.
                    cont.resumeWithException(e)
                    return
                } catch (e: Throwable) {
                    // The caller's contract is IOException or the body (§7.2): anything else is a failed read.
                    cont.resumeWithException(ReadFailed())
                    return
                }
```
and update the function's KDoc: "Resumes with the body, or an IOException ([BodyTooLarge], [ReadFailed], or OkHttp's own)."

`OpenMeteoForecast.kt`: add
```kotlin
/** A coordinate as Open-Meteo reads it: plain digits, never an exponent ("-0.0005", not "-5.0E-4"). */
internal fun coordinate(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()
```
and in `forecast(…)`: `.addQueryParameter("latitude", latitude.toString())` → `.addQueryParameter("latitude", coordinate(latitude))`; the same for longitude.

Run: `./gradlew :provider:weather-openmeteo:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 3: Write the failing backoff tests**

`FlowRetryTest.kt`: replace `aValueGettingThroughStartsTheWaitsAgainFromOneSecond` with:
```kotlin
    /** §7.2: a value that stood for one wait starts the waits again from 1 s. */
    @Test
    fun aValueThatStandsStartsTheWaitsAgainFromOneSecond() = runTest {
        var starts = 0
        val flaky = flow {
            starts++
            if (starts == 1) throw IllegalStateException("store hiccup 1")
            if (starts == 2) {
                emit("a")
                // Just past the wait it would have had next.
                delay(retryDelayMillis(1) + 1)
                throw IllegalStateException("store hiccup 2")
            }
            emit("b")
        }
        val result = async { flaky.retryWithBackoff {}.take(2).toList() }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(2)
        advanceTimeBy(retryDelayMillis(1) + 1)
        runCurrent()
        // "a" stood for a whole wait: the next wait is 1 s again.
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(3)
        assertThat(result.await()).containsExactly("a", "b").inOrder()
    }

    /** §7.2: a read that emits and fails at once doesn't retry every second. */
    @Test
    fun aValueThatFailsAtOnceKeepsTheWaitsGrowing() = runTest {
        var starts = 0
        val flaky = flow {
            starts++
            if (starts == 1) throw IllegalStateException("store hiccup 1")
            if (starts == 2) {
                emit("a")
                throw IllegalStateException("store hiccup 2")
            }
            emit("b")
        }
        val result = async { flaky.retryWithBackoff {}.take(2).toList() }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(2)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(2)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(3)
        assertThat(result.await()).containsExactly("a", "b").inOrder()
    }
```
(import `kotlinx.coroutines.delay`.)
Run: `./gradlew :core:plugin:testDebugUnitTest --tests "*FlowRetryTest*"`
Expected: `aValueThatFailsAtOnceKeepsTheWaitsGrowing` FAILS (the third start comes after 1 s).

- [ ] **Step 4: Reset the waits only after a value has stood**

`FlowRetry.kt`'s `retryWithBackoff` becomes:
```kotlin
/**
 * Starts the flow again after a failure, waiting [retryDelayMillis], instead of ending it: a store hiccup must not
 * leave a tab, a card or the sync loop's trigger gone until the app restarts (3a design §3.12). [onFailure] logs it.
 * A value that stands for one whole wait starts the waits again from 1 s; one that fails at once doesn't (4c §7.2), so a
 * read that emits and then fails can't log every second.
 */
fun <T> Flow<T>.retryWithBackoff(onFailure: (Throwable) -> Unit): Flow<T> = flow {
    // Per collection; onEach sits upstream of retryWhen, so a downstream failure is never caught or retried.
    val failures = AtomicLong(0)
    coroutineScope {
        var standing: Job? = null
        emitAll(
            onEach {
                standing?.cancel()
                val wait = failures.get().takeIf { it > 0 }?.let(::retryDelayMillis)
                standing = wait?.let { launch { delay(it); failures.set(0) } }
            }.retryWhen { cause, _ ->
                standing?.cancel()
                onFailure(cause)
                delay(retryDelayMillis(failures.getAndIncrement()))
                true
            },
        )
        standing?.cancel()
    }
}
```
(imports `java.util.concurrent.atomic.AtomicLong`, `kotlinx.coroutines.Job`, `kotlinx.coroutines.coroutineScope`, `kotlinx.coroutines.launch`.)

Run: `./gradlew :core:plugin:testDebugUnitTest`
Expected: PASS (both new tests and `aFailingFlowStartsAgainAfterItsWaitAndEachFailureIsReported`). Then `./gradlew :app:testDebugUnitTest :capability:calendar:testDebugUnitTest :capability:weather:testDebugUnitTest`: PASS (every retrying flow uses it).

- [ ] **Step 5: Fold the setup session into the session (ruling 23)**

`DefaultAccessControl.kt`: replace `private val _session …`, `override val session …` and `@Volatile private var setupPerson …` with
```kotlin
    /** Who is signed in, and whether it is the wizard's setup session (4a §3.4): one value, so every end ends both. */
    private class SignedIn(val who: Identified, val setup: Boolean)

    private val signedIn = MutableStateFlow<SignedIn?>(null)
    private val _session = MutableStateFlow<Identified?>(null)
    override val session: StateFlow<Identified?> = _session.asStateFlow()
    private val stateLock = Any()

    /** The one place the session changes. */
    private fun set(next: SignedIn?) = synchronized(stateLock) {
        signedIn.value = next
        _session.value = next?.who
    }
```
and change its users:
- in `authorise`, the two shortcuts become
```kotlin
            val current = signedIn.value
            if (current != null && current.setup) {
                val grants = grantedFor(current.who.role, anyOf)
                if (grants.isNotEmpty() && allow(current.who, grants)) {
                    restartExpiry(SETUP_IDLE_MS)
                    return@withLock Authorised(current.who.person, current.who.role, grants)
                }
            }
            if (current != null && defs.none { it.freshPin }) {
                val grants = grantedFor(current.who.role, anyOf)
                if (grants.isNotEmpty() && allow(current.who, grants)) {
                    restartExpiry()
                    return@withLock Authorised(current.who.person, current.who.role, grants)
                }
                if (refusal is Refusal.Toast) {
                    toaster.show(refusal.message(current.who.person.name))
                    // Signed out, so the next tap brings up the PIN pad: an adult can take over from a child.
                    lock()
                    return@withLock null
                }
            }
```
- in `promptUntilResolved`, `setupPerson = null` and `_session.value = identified` become `set(SignedIn(identified, setup = false))` (keep the comment "A PIN at the pad starts an ordinary session, whoever it is.");
- the rest:
```kotlin
    override fun beginSetupSession(person: Identified) {
        set(SignedIn(person, setup = true))
        restartExpiry(SETUP_IDLE_MS)
    }

    override fun endSetupSession() {
        val current = signedIn.value ?: return
        if (!current.setup) return
        set(SignedIn(current.who, setup = false))
        restartExpiry()
    }

    override fun touch() {
        val current = signedIn.value ?: return
        restartExpiry(if (current.setup) SETUP_IDLE_MS else SESSION_TIMEOUT_MS)
    }

    override fun lock() {
        synchronized(expiryLock) { expiry?.cancel() }
        set(null)
    }

    private fun restartExpiry(timeoutMillis: Long = SESSION_TIMEOUT_MS) = synchronized(expiryLock) {
        expiry?.cancel()
        val guarded = signedIn.value
        expiry = scope.launch {
            delay(timeoutMillis)
            synchronized(stateLock) { if (signedIn.value === guarded) set(null) }
        }
    }
```
Run: `./gradlew :core:access:testDebugUnitTest :core:setup:testDebugUnitTest`
Expected: PASS, unchanged (`DefaultAccessControlTest`'s setup-session tests pin this behaviour).

- [ ] **Step 6: One helper for "settings.manage unless no Admin yet"**

`WizardRules.kt`, add at the end:
```kotlin
/**
 * 4a design §3.4: a setup change before the first Admin exists needs nobody's PIN; after that, settings.manage (which the
 * setup session passes). False when the PIN pad was cancelled or refused.
 */
internal suspend fun mayChangeSetup(household: HouseholdRepository, access: AccessControl): Boolean =
    !household.hasActiveAdmin.first() || access.authorise(CorePermissions.SETTINGS_MANAGE) != null
```
`steps/LocationStep.kt`: `if (household.hasActiveAdmin.first() && access.authorise(CorePermissions.SETTINGS_MANAGE) == null) return false` → `if (!mayChangeSetup(household, access)) return false` (drop imports it no longer needs).
Run: `./gradlew :core:setup:testDebugUnitTest --tests "*StepsTest*"`
Expected: PASS (`beforeAnyAdminAPlaceIsSavedWithoutAPin`, `onceAnAdminExistsSavingAPlaceAsksForTheirPin` pin it).

- [ ] **Step 7: Test health**

- `StepsUiTest` (the flake: a Room query outliving its database). Replace the two rules and `tearDown` with ordered rules, so the database closes only after the composition is disposed:
```kotlin
    @get:Rule(order = 0) val folder = TemporaryFolder()

    // After the compose rule has disposed the composition: no query can outlive the database.
    @get:Rule(order = 1) val closing = object : ExternalResource() {
        override fun after() {
            scope.cancel()
            states.close()
            db.close()
        }
    }

    @get:Rule(order = 2) val compose = createComposeRule()
```
(delete the `@After fun tearDown()`; import `org.junit.rules.ExternalResource`.)
- `DebugSeedTest` (the 4a follow-up), add:
```kotlin
    /** The sample still opens when its Family calendar can't be made the master; a master is chosen in Settings later. */
    @Test
    fun theSampleHouseholdCompletesWhenItsMasterCantBeSet() = runTest {
        ShadowLog.clear()
        val secondListFails = object : CalendarProvider by fake {
            private var lists = 0

            override suspend fun sources(conn: Connection): List<CalendarSource> =
                if (lists++ == 0) fake.sources(conn) else throw UnreachableException("offline")
        }
        val setup = CalendarSetup(store, setOf(secondListFails), { household.people.first() }, NoToasts, WallClock { 0L }) { syncs++ }
        DebugSampleHousehold(household, pins, setup, setOf(fake), state::markComplete).create()
        assertThat(state.setupComplete.first()).isTrue()
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly(DEBUG_CONNECTION_ID)
        assertThat(store.master().first()).isNull()
        assertThat(ShadowLog.getLogs().map { it.msg }).contains("Couldn't make the sample Family calendar the master (UnreachableException)")
    }
```
(imports `uk.co.siland.culvery.capability.calendar.CalendarProvider`, `CalendarSource`, `UnreachableException`.)
- `WeatherStoreTest`, beside `aRepeatedHourIsStoredOnce`:
```kotlin
    @Test
    fun aRepeatedDayIsStoredOnce() = runTest {
        val f = forecast(days = 1)
        store.replace(WeatherPlace(LONDON), f.copy(days = f.days + f.days.first().copy(high = 30.0)), 1_000L)
        val days = store.stored.first()!!.days
        assertThat(days).hasSize(1)
        assertThat(days.single().high).isEqualTo(f.days.first().high)
    }
```
- `WeatherSyncLoopTest`:
```kotlin
    @Test
    fun clearingTheLocationStopsFetching() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val places = MutableStateFlow<WeatherPlace?>(london)
        loop(places) { fetched += it }
        runCurrent()
        assertThat(fetched).containsExactly(london)
        places.value = null
        runCurrent()
        after(3 * WEATHER_REFRESH_MS)
        assertThat(fetched).containsExactly(london)
    }
```
- Robolectric at SDK 35: add the line `sdk=35` to each of the eight `src/test/resources/robolectric.properties` (after `qualifiers=…`).

Run: `./gradlew :core:setup:testDebugUnitTest :app:testDebugUnitTest :capability:weather:testDebugUnitTest 2>&1 | grep -c "requires Java 21"`
Expected: the tests PASS and the count is `0` (Robolectric no longer tries SDK 36).

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 9: Commit**

```bash
git add provider/weather-openmeteo core app capability
git commit -m "Tidy retry backoff, Open-Meteo reads and coordinates, and the setup session; fix the StepsUiTest flake and fill 4b's test gaps"
```

---

### Task 19: The walkthrough on the signed, minified release; Appendix B; the README; the follow-ups (§4.5, §8.2, §9)

**Review:** sonnet (Steps 1–2 by the implementer; Step 3 by the controller with the user).

**Files:**
- Modify (after the checkpoint): `docs/superpowers/specs/2026-10-02-culvery-4c-release-fixes-design.md` (Appendix B, "After"), `README.md`, `docs/superpowers/plans/2026-09-23-plan1-followups.md`

**Interfaces:**
- Consumes: everything above; `tools/measure-release.sh`; the real release key and the four properties (Task 1); the release OAuth client (`docs/setup/google-calendar.md` §4).
- Produces: documentation only.

Who does what: the implementer checks the emulator and the build (Steps 1–2) and stops. **The controller** runs the walkthrough with the user (Step 3), driving the emulator with `adb` and screenshots. The emulator is the **already-running API 35 Google Play emulator `emulator-5554`**; don't start or wipe another. To go offline use `adb -s emulator-5554 shell svc wifi disable` and `svc data disable` (`enable` to come back), **never airplane mode** (Play services' crash loop, `docs/setup/google-calendar.md` §5). Town search and connecting Google need the network: keep the emulator online for them. Use only public towns (London, Edinburgh, Tokyo). The user types every PIN, every keystore password and every Google password; the controller never types one.

- [ ] **Step 1: Check the emulator and the signing setup**

```bash
adb devices
adb -s emulator-5554 shell getprop ro.build.version.sdk
grep -c "^culvery.release" ~/.gradle/gradle.properties
```
Expected: `emulator-5554 device`; `35`; `4` (the count only: never print the lines). If `adb` isn't on PATH use `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb"`. If the emulator isn't running, stop and ask the user to start it.

- [ ] **Step 2: Build and install the signed, minified release**

```bash
./gradlew :app:assembleRelease
ls -l app/build/outputs/apk/release/app-release.apk app/build/outputs/mapping/release/mapping.txt
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
adb -s emulator-5554 shell am start -n uk.co.siland.culvery/.MainActivity
adb -s emulator-5554 logcat -d | grep -E "AndroidRuntime|FATAL" | tail -20
adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4c-install.png"
```
Expected: `BUILD SUCCESSFUL`; the APK and its mapping file exist; the install succeeds (same key as Task 1's install; if it says `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the key changed since: stop and report); Culvery opens with no crash. **Stop and report** Steps 1–2 with the screenshot; the implementer does not go on.

- [ ] **Step 3: STOP — the controller runs the walkthrough and the USER CHECKPOINT with the user**

**Before starting, with the user:**
1. The user makes the release OAuth client, if not done yet: in <https://console.cloud.google.com/>, the Culvery project › **Google Auth Platform › Clients › Create client** › **Android**; package `uk.co.siland.culvery`; the SHA-1 from `./gradlew :app:signingReport` under `Variant: release` (the controller may run that command: it prints fingerprints, not passwords); **Create**. The emulator has the household's test Google account under Settings › Accounts (`docs/setup/google-calendar.md` §5).

The controller does not go to Step 4 until the user approves. At each numbered item take `adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4c-<n>.png"` and send the images with the report. Ask the user before each item that changes the household's data.

1. **Setup, the home app, and a reboot (§8.2 item 2; online).** `adb -s emulator-5554 shell pm clear uk.co.siland.culvery`; start it: the splash (the theme's dark background) then the wizard. Welcome › Start; Home location: London; You: a test Admin (the user types the PIN); Household: skip; Connect: skip; Done shows "Make Culvery the home app so it comes back after a restart." Tap **Choose home app**: Android's own dialog asks whether to make Culvery the default home app, with no way into Settings from it; the user answers yes, and the line has gone. **Open Culvery**: Home, pinned (`adb -s emulator-5554 shell dumpsys activity activities | grep -i locktaskmodestate` shows `LOCKED` or `PINNED`). `adb -s emulator-5554 reboot`, wait for boot (`adb -s emulator-5554 wait-for-device`, then until `getprop sys.boot_completed` is `1`): Culvery is in front again, pinned.
2. **Cold start numbers (§8.2 item 1).** In this household (London, one Admin, no calendar — as Task 1 measured), run `bash tools/measure-release.sh emulator-5554` in the background. Fill Appendix B's "After" column in the spec the same way as "Before". If any after-number is worse than before, say so plainly in the report.
3. **Connect Google on the kiosk (§8.2 item 3; online).** Settings (the user's PIN) › Calendars › **Connect Google Calendar**: the PIN pad asks again even though Settings is open ("Enter your PIN to connect calendars."). The account chooser opens; while it shows, `dumpsys activity activities | grep -i locktaskmodestate` says `NONE`. The user picks the account and allows both scopes; Culvery shows "Google Calendar connected" and is pinned again (`LOCKED`/`PINNED`). Then once more with **Back** on the chooser: nothing is said, and it is pinned again.
4. **Sync, a write, offline (§8.2 item 4).** Within a minute the household's calendars show on the Calendar tab. Add an event for today (+, a title, Save): it appears; the user checks it is in Google Calendar on their phone or the web. Offline (`svc wifi disable; svc data disable`): after the next pass (up to 5 minutes) Settings › Calendars says "Can't reach Google Calendar" and the cached events still show; online again (`enable`): "Synced … ago" within 5 minutes. Midnight: the "no nightly full resync" rule is pinned by `CalendarSyncTest.thePassAfterLocalMidnightKeepsTheCursor` (the Google Play image refuses `adb root`, and a release can't be read with `run-as`); on the emulator check only that after **Exit kiosk**, Android Settings › System › Date & time › automatic off and the date set to tomorrow, then back to Culvery, Home moves to the new day and its events still show; set automatic time back on.
5. **Clear night (§8.2 item 5).** If the header shows a clear night anywhere convenient (try setting Home location to a town where it is night now, e.g. Tokyo during the UK's afternoon, and check its sky), its icon is the moon glyph, never the words "clear_night"; otherwise compare against `capability/weather/src/test/screenshots/header_clear_night_*.png` and say which was checked. Set Home location back to London.
6. **A calendar that can't be read (§8.2 item 6).** The user, in Google Calendar on the web, stops sharing one calendar with the household's account (or removes the account from a shared calendar), keeping it in the account's list if Google allows; within two passes Settings › Calendars shows that calendar's row with "Can't read this calendar — check it's still shared with this account" and **Hide this calendar**, while the connection still says "Synced … ago". Tap **Hide this calendar**: "{calendar} hidden", and the message goes. If Google removed the calendar from the account's list instead, the row disappears at the next pass: say which happened.
7. **Reorder people, look ahead, Exit kiosk (§8.2 item 7).**
   - Settings › People › Add person: "Sam", Adult, no PIN (fresh PIN). Sam's row has **Move up**; tap it: "Sam moved up", Sam first; the Calendar tab's legend and the add sheet's Who chips show Sam first.
   - Calendar tab: **›** three times: "Next week", "In 2 weeks", "In 3 weeks", each with seven later days and the **This week** chip; **›** is then greyed and does nothing. **This week** goes back; **‹** is greyed there.
   - Step to next week, tap the empty space under one day's events: the add sheet opens on that day. Save an event: "Event added", and it shows in that column.
   - In the add sheet, **Pick date…** a day five weeks ahead and save: the toast says "Event added for {that day, as Tue 17 November}". Delete it afterwards in Google Calendar (or keep it, as the user prefers).
   - Step to a later week and touch nothing for 2 minutes: the tab is back on "This week".
   - With Culvery the home app: Settings › Kiosk › **Exit kiosk** (fresh PIN): Culvery stays in front with the system bars showing and `locktaskmodestate` says `NONE`; a Home press leaves it so. Open another app from Overview, then press Home: Culvery is in front and pinned again.
8. **The release log (§8.2 item 8).**
   ```bash
   adb -s emulator-5554 logcat -d --pid=$(adb -s emulator-5554 shell pidof uk.co.siland.culvery) | tail -200 > "$TMP/culvery-4c-log.txt"
   grep -cE " [VDI] " "$TMP/culvery-4c-log.txt"
   grep -iE "@|london|tokyo|edinburgh|europe/|asia/|51\.5|-0\.12|sam\b|alex\b" "$TMP/culvery-4c-log.txt"
   ```
   Expected: the first count is 0 for Culvery's own tags (any `V/D/I` lines must be the system's or a library's: name them); the second prints nothing. Look through the warnings: each names a type, a code or an id only.

Send the user these images, dark and light: `settings_kiosk_*`, `done_*` (core/setup), `settings_people_*`, `review_read_problem_*`, `detail_untagged_*`, `header_clear_night_*`, `week_next_*`, `week_furthest_*` (capability/calendar), and the walkthrough screenshots; and Appendix B. Name this plan's own choices the spec doesn't give (all provisional, for 4d's review): the home-app prompt under the Done title and above Exit kiosk; the 48 dp Move buttons with arrows, blank at the list's ends; the read-problem line in danger colour with the Hide pill at its right; `bedtime` (or `nightlight`) for clear night and `mobile` for a phone; the splash with no icon until 4d; ‹ › together before the week's title (so › doesn't move as the title's width changes), greyed at their ends, and the quiet This week pill after it.

Ask: "Do these match what you want? Any changes before I update the README?"
- **If the user asks for changes:** make them, re-record only the affected images with `--tests`, look at them, run `./gradlew testDebugUnitTest verifyRoborazziDebug`, re-send them, and commit with a message describing the change. Repeat until approved.
- **When approved:** commit Appendix B, then continue to Step 4.
```bash
git add docs/superpowers/specs/2026-10-02-culvery-4c-release-fixes-design.md
git commit -m "Record the signed, minified release's size and start-up after 4c"
```

- [ ] **Step 4: Update the README**

In `README.md`:
1. **Build and run.** After the code block with `./gradlew :app:installDebug` and its "Use `testDebugUnitTest`…" line, add:
```markdown
A release build is signed with your own key, shrunk by R8, and logs only warnings and errors: `./gradlew :app:assembleRelease` once the signing properties are set (`docs/setup/release.md`). Without them, any release task stops at once and says what to set; debug builds and the tests never need them.
```
   In the paragraph starting "To connect a real Google account", replace "Release builds offer Google Calendar only, and its Connect fails until the release key's SHA-1 has its own Android client (Plan 4)." with "Release builds offer Google Calendar only, through a second Android OAuth client for the release key (`docs/setup/google-calendar.md` §4). Connecting and reconnecting always ask for an Admin's PIN." After that paragraph's last sentence add: "A calendar the account still lists but whose events Google refuses shows on its own row in Settings › Calendars with **Hide this calendar**; its connection stays healthy."
2. **Kiosk mode.** Replace the section's three paragraphs with:
```markdown
Release builds pin the app to the screen (Android "screen pinning") once setup is complete, so the first Google connection happens outside it. Setup's Done step, and Settings › Kiosk, offer **Choose home app**, which asks Android's own question: with Culvery as the default home app, a reboot, a power cut or a crash comes back to Culvery, and every return to the front pins it again. **Settings › Kiosk › Exit kiosk** (Admin PIN, always asked) unpins it; as the home app it stays in front with the system bars showing, and pins again once it has left the front and come back. **Change home app** (Admin PIN, always asked) gives the tablet back its normal launcher. While Google's account chooser shows during Connect or Reconnect, Culvery leaves screen pinning and pins again when it is back in front (as device owner it stays in lock-task: Google Play services is allowed in it).

Screen pinning can also be undone by holding **Back + Overview**. To stop a child doing that, on the tablet: set a screen lock (PIN), then turn on **Settings › Security › Other security settings › Pin windows › Ask for PIN before unpinning**. Unpinning then drops to the lock screen.

For a stronger lock, make Culvery the device owner on a freshly reset tablet (`docs/setup/release.md` §6): it then pins with no confirmation and no exit gesture. Undoing that needs a factory reset.
```
3. **PINs.** Replace the line beginning "- Exiting kiosk, adding or removing someone," with "- Exiting kiosk, changing the home app, connecting or reconnecting a calendar, adding or removing someone, changing a role and setting, changing or removing a PIN always ask for a PIN, even mid-session; renaming, recolouring and reordering people don't. Changing your own role or PIN, or removing yourself, signs you out." and the lockout line ends "…up to 16 minutes (a lock set further ahead than that, after the clock went back, has expired). Only a PIN that is allowed to do the thing clears the count."
4. **Modules.** In the `:core:plugin` row add, after `Daylight`: "`HouseholdClock` (the one minute ticker, in the household's zone), `FirstDraw`, `HomeApp`".
   In the section describing the Calendar tab (or, if there is none, in the paragraph that names the tabs), say: "The Calendar tab shows this week and steps ahead a week at a time, up to three weeks on; it comes back to this week after 2 minutes untouched and at midnight. Any later date can still be added through **Pick date…**." 
5. **Privacy.** Add a second paragraph:
```markdown
Google Calendar is asked only for the fields the tablet shows, compressed, about every five minutes per calendar; a full read happens when a calendar is connected, after a change of time zone, or about every six weeks. A release build logs only warnings and errors, naming exception types, HTTP codes and internal ids: never a person, calendar, account, town, coordinates or time zone.
```
6. **Licences.** After "Material Symbols: Apache 2.0 (`core/ui/licenses/Apache-MaterialSymbols.txt`)" add ", subset to the glyphs Culvery uses (`tools/fonts/README.md`)".

Then read `README.md` through once; and
```bash
grep -n "Plan 4)\|not built yet" README.md
```
Expected: no output.

- [ ] **Step 5: Update the follow-ups**

In `docs/superpowers/plans/2026-09-23-plan1-followups.md`, delete these items (4c took them, spec §9):
- Under "## For Plan 4 (weather, setup, settings, release)": "Guard the lockout against a backwards jump…"; "Subset the 15 MB Material Symbols font…" (its memory half is in the new 4e list below); "After Exit kiosk, the system bars overlap the content…"; "`LockoutStore` uses `commit()`…"; "`kioskExited` is lost when the Activity is recreated…"; "Before shipping to the wall, run the Task 10 Step 7 checks on the device and do a signed release build…" (the device half is in the 4e list).
- Under "## From Plan 2a review (deferred)" › "**For Plan 4**": "Test that `addConnection` is atomic."; "All-day events straddle two days after a household zone change…".
- Under "## From Plan 3a review (deferred)" › "**For Plan 4**": L3; M4; M7; L4; L6.
- Under "## From Plan 3a (deferred)" › "**For Plan 4**": "The release OAuth client…"; "Bound recurring series in the mirror…"; "`TodayCardHost` recomposes every 30 s…"; "A write refused for a missing scope (403, NeedsSignIn)…".
- Under "## From Plan 4a (deferred)" › "**For Plan 4c**": "Reordering people (4a design D15)."; "Per-calendar health in Settings › Calendars (M7)…"; "Count the queued changes a source removal dropped…"; "`DebugSampleHousehold.create()` still completes when making the Family calendar the master fails…"; "Cold start skips 130–190 frames…" (its device half is in the 4e list). › "**Later (code health)**": both items. › "**Test health**": the `StepsUiTest` item.
- Under "## From Plan 4b (deferred)" › "**For Plan 4c**": "The calendar's Google client has no `callTimeout`…". › "**Test health (4b final review)**": all six items. › "**Later (code health, 4b final review)**": every item except "Header items could overlap the clock or date if they grow wide…".

Then rename the headings whose items now wait for the device or the design review, so each says where it goes: "## For Plan 4 (weather, setup, settings, release)" → "## For Plan 4e (the device) — from Plan 1"; the remaining "**For Plan 4**" and "**For Plan 4c**" sub-headings → "**For Plan 4e (the device)**", except an item about `opsz`, accessibility or design, which moves under the nearest "**For the end-of-v1 design and UX review**" or "**Accessibility pass (with the `HhIcon` item)**" heading in its section. Add at the end of the file:
```markdown

## From Plan 4c (deferred)

**For Plan 4d (design, UX and accessibility)**
- All 4c layout and copy choices are provisional: the home-app prompt (under Done's title; above Exit kiosk), the 48 dp Move up / Move down buttons, the read-problem line in danger colour with Hide at its right, `bedtime` for a clear night and `mobile` for a phone, the splash with no icon until the launcher icon exists, and the Calendar tab's ‹ › and This week pill.
- Move up / Move down have spoken labels but no other accessibility review yet.

**For Plan 4e (the device)**
- Measure Appendix B's numbers on the SM-T510 (start-up, skipped frames, gfxinfo, memory), and the subset font's memory saving there.
- Connect and Reconnect in lock-task on the SM-T510, with screen pinning and as device owner: the account chooser opens outside pinning and Culvery pins again.
- The home app after a real power cut; device-owner provisioning on a freshly reset tablet (`docs/setup/release.md` §6).
- Confirm on a real account that a calendar unshared from it is refused (403/404) while still listed, and how long Google keeps listing it.
- The R8 build's sign-in, sync, writes and weather on the tablet.

**Later**
- Google Calendar's sync token now lives about six weeks; check quota and data use over a month on the wall (Appendix A's economy estimate assumed a nightly full read).
```
and add under it any item the user or the controller noted during this plan that was deferred rather than fixed.

- [ ] **Step 6: Commit**

```bash
git add README.md docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Document the release build, the home app and kiosk, and what 4c took from the follow-ups"
```

---

## Spec coverage (4c design → tasks)

| Design | Where |
|---|---|
| §1 scope; D9 (no 4d/4e work) | Tasks 1–19; Task 19 Step 5 moves device and design items to 4e and 4d |
| D1 order | Task order above (E3 in Task 16, ruling 7) |
| D2 audit (Appendix A) | S2/S3 Task 2; U3, U4, U6, U8 Task 4; P1, P2 Task 5; P7, C6 Task 6; P4, P5, U7 Task 7; E1, C1 Task 11; E2, P9, C2 Task 12; E3 Task 16 |
| D3, §5.1 home app (Android's role dialog; Change home app on a fresh PIN; Exit kiosk in front as the home app); E6 | Task 9 (`HOME` category, `HomeApp`, Done step, Settings › Kiosk, `KioskLifecycle`); Task 19 items 1 and 7 |
| D4, §3.1 signing, version | Task 1 (the real key; the failure message checked in Step 5) |
| §3.2 release OAuth client, `docs/setup/release.md` | Task 1; Task 3 (§4); Task 9 (§5, §6); Task 19 (user creates the client) |
| §3.3 R8, shrinking, `proguard-rules.pro` | Task 3 (the three rules; Step 10 checks the minified build works); Task 19 items 3–5 |
| D8, §3.4 release logging, `LogHygieneTest` | Task 3 (ruling 18, plan review 10); Task 19 item 8 |
| §3.5 icon font, `Icons`, `IconFontTest`, S3, `ThemeTest`, `lifecycle-viewmodel-ktx` | Task 2 (ruling 22; filled forms kept, plan review 1) |
| §4.1 start-up off Main, first-draw gate, splash | Task 4 (rulings 8, 10, 19; core-splashscreen 1.2.0) |
| §4.2 remembered `ColorScheme`, `now` out of the root | Task 5 |
| §4.3 `HouseholdClock`; `TodayCardHost` keyed on the date | Task 6 (ruling 24) |
| §4.4 `distinctUntilChanged`, stability file, `flowOn` | Task 7 (ruling 20) |
| §4.5 measuring, Appendix B | Task 1 (before, ruling 21); Task 19 item 2 (after) |
| D5, §5.3 `connections.manage`, Play services, leaving pinning | Task 10 (rulings 16, 17; `ShellNavigator.leavePinning()`); Task 19 item 3 |
| §5.2 device owner | Task 9 (`CulveryDeviceAdmin`, `allowLockTaskIfOwner` with Play services, docs) |
| §5.4 K1, K2 | Task 9 (rulings 27, 6) |
| §5.4 K3, K4 | Task 8 (ruling 26) |
| §6.1 `fields=`, gzip User-Agent, `callTimeout` | Task 11 (rulings 4, 5) |
| §6.2 cursor key, pruning, v6 | Task 12 (rulings 1, 11; the prune bound is a deviation, ruling 1) |
| §6.2 D11 no series-rule seeding | Task 13 (`StoredSeries` serves C9 only) |
| §6.3 C3, C4 | Task 15 (ruling 13; an accepted write clears NeedsSignIn, plan review 17) |
| §6.3 C9 | Task 13 (`GoogleReadTest.aCancelledSeriesRemovesEveryStoredInstance`, the split series kept) |
| §6.3 E3 | Task 16 (ruling 7) |
| D6, §6.4 one calendar that can't be read (C5, M7) | Task 12 (the column); Task 16 (behaviour, UI; ruling 12; every calendar refused is no sync, plan review 11); Task 19 item 6 |
| §6.5 C7 toast count, C8 `addConnection` test | Task 16 (ruling 14) |
| D10, §6.6 looking ahead | Task 12 (`SYNC_FUTURE_DAYS` = 28); Task 14 (‹ ›, This week, the 2-minute and midnight return, the presets, the dated toast); Task 19 item 7 |
| D7, §7.1 reordering people | Task 17 (ruling 25); Task 19 item 7 |
| §7.2 code health | Task 18 (`Http.kt`, coordinates, `retryWithBackoff`, setup-session folding and helper, ruling 23); `HomeScreen`'s default: Task 5 |
| §7.3 test health | Task 18 (`StepsUiTest`, `DebugSampleHousehold`, 4b gaps, SDK 35); `HouseholdTickerTest` goes with `MinuteTicker`: Task 6 |
| §8.1 unit, Robolectric and Roborazzi list | Each task's tests; Roborazzi: clear-night header and detail (Task 2), Settings › Kiosk and Done (Task 9), next and furthest week (Task 14), unreadable calendar and master (Task 16), people with move buttons (Task 17) |
| §8.1 gate, release build with and without the properties | Global Constraints; Task 1 Step 5; Task 3 Step 10; Task 19 Step 2 |
| §8.2 walkthrough | Task 19 Step 3 (items 1–8; the midnight check is a unit test on the Google Play image) |
| §9 follow-ups taken | Task 19 Step 5 |
