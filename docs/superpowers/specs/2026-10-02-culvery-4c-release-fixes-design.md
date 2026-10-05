# Culvery 4c: release build, fixes and performance (design)

**Date:** 2026-10-02
**Status:** Draft, for the user's review
**Parent spec:** `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§7, §8, §10, §12)
**Previous plan:** `docs/superpowers/specs/2026-10-01-culvery-4b-weather-design.md` (4b, weather)
**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (§9 says which items 4c takes)
**Builds on:** Plan 4b, merged to `main`.

## 1. Scope

The rest of v1 is split three ways (D1):
- **4c (this spec):** a signed, minified release build with its own OAuth client and logging policy; Culvery as the tablet's home app; the whole non-device fix backlog (kiosk, calendar, people, test and code health); and the fixes from a size, start-up, runtime and economy audit (Appendix A).
- **4d:** the end-of-v1 design and UX review with the accessibility pass and the launcher icon.
- **4e:** the beta on the SM-T510: every device-only check, a baseline profile, the `eventsBetween` index, and the start-up and memory measurements on the tablet.

The user tests on the tablet only once there is a product they are happy to use; 4c makes the build that 4d polishes and 4e installs.

## 2. Decisions (agreed with the user)

| # | Decision |
|---|---|
| D1 | **Split:** 4c release + non-device fixes + audit fixes; 4d design, UX and accessibility; 4e the device. One spec and one plan for 4c, ordered build → performance → kiosk → calendar → people and health → walkthrough. |
| D2 | **Audit now.** A read-only size, start-up, runtime and economy audit ran during the brainstorm; its triage (Appendix A) is part of this spec. |
| D3 | **Home app, device owner optional.** Culvery declares the Home role, so a reboot, power cut or crash lands back in it once it is chosen as the default launcher. When provisioned as device owner (a documented one-time adb command), it allowlists itself for true lock-task. |
| D4 | **Release signing from a local key.** The keystore lives outside the repo; its path, alias and passwords come from user-level `~/.gradle/gradle.properties`; a release build without them fails with a message saying what to set. |
| D5 | **Connecting Google on the kiosk:** a fresh Admin PIN, a Play services check first, and Culvery steps out of lock-task for the sign-in and pins again afterwards. |
| D6 | **One calendar that can't be read** shows on its own row in Settings › Calendars with **Hide this calendar**; the connection stays healthy while its other calendars sync. |
| D7 | **Reordering people** with Move up / Move down buttons, in the open Settings session (no fresh PIN). |
| D8 | **Release logging:** R8 strips `Log.v/d/i`; `Log.w/e` carry only class names, HTTP codes or fixed text; a test guards it. |
| D10 | **Looking ahead** *(amended after the plan review, 2026-10-02)*: the Calendar tab steps a week at a time up to 4 weeks (this week + 3); adding an event stays possible for any future date; the sync window grows to +28 days. Overrides v1 §9.3's "no week navigation". |
| D11 | **No series-rule seeding** *(plan review)*: with the sync token kept across midnight, seeding the rule cache from stored rules saves one call per series per full resync; dropped (YAGNI). |
| D9 | **Out of 4c:** accessibility, the launcher icon and design notes (4d); everything that needs the tablet (4e); the follow-ups file's Later items. |

## 3. Build and release

### 3.1 Signing (D4)

- `app/build.gradle.kts` gains a `release` signing config read from Gradle properties `culvery.release.storeFile`, `culvery.release.storePassword`, `culvery.release.keyAlias`, `culvery.release.keyPassword`.
- When a release task runs and any is missing, the build fails with: "Release signing isn't set up: add culvery.release.storeFile, storePassword, keyAlias and keyPassword to ~/.gradle/gradle.properties (see docs/setup/release.md)." Debug builds and the unit-test gate never need them (once set, Gradle reads them while configuring and keeps them in its encrypted, gitignored configuration cache).
- `tools/new-release-key.sh` (a wizard script) walks the user through `keytool`: the user types the passwords; the script prints the four property lines to add and the key's SHA-1. No password is passed on a command line Claude runs or written to the repo.
- `versionName = "1.0.0-beta1"`, `versionCode = 2`.

### 3.2 Release OAuth client

- `docs/setup/google-calendar.md` §4 gains the release client: a second Android OAuth client with the same package and the release key's SHA-1 (`./gradlew :app:signingReport`).
- A new `docs/setup/release.md`: creating and backing up the key (losing it means uninstalling and a new OAuth client), building, installing over adb (a release over a debug install needs `pm clear` or uninstall, as the signature differs), choosing Culvery as the home app, and the optional device-owner step (§5.2).

### 3.3 R8 and resource shrinking

- `isMinifyEnabled = true` and `isShrinkResources = true` for release, with `proguard-android-optimize.txt` and `app/proguard-rules.pro`.
- `proguard-rules.pro` holds only what libraries don't ship: `-keepattributes SourceFile,LineNumberTable` with `-renamesourcefileattribute SourceFile`, the `Log` block (§3.4), and `-keepnames` for the app's own `Throwable` subclasses so warnings still name the exception (Task 3 review). *(Plan review: kotlinx.serialization 1.9 and OkHttp 4.12 already ship their keeps and `-dontwarn` lines.)*
- Unit tests can't exercise R8 (Compose tests run in debug only), so the walkthrough (§8.2) runs the signed, minified build and must connect Google, sync, write, and show weather.

### 3.4 Release logging (D8)

- `proguard-rules.pro`: `-assumenosideeffects class android.util.Log { v(...); d(...); i(...); }`.
- Every `Log.w` and `Log.e` in main sources carries only an exception's `::class.simpleName`, an HTTP code, a capability or provider id, or fixed text — never an email, calendar or person name, PIN, town, coordinates or zone (3a L4, 4a, 4b).
- `LogHygieneTest` (JVM, in `build-logic`'s convention tests beside `ModuleBoundariesTest`): scans `src/main` of every module for `Log.w(`/`Log.e(` calls; fails on a call that passes a throwable argument or interpolates anything other than `${…::class.simpleName}`, `${…code}` or an allowlisted id.

### 3.5 The icon font (S2, S3)

- **Bug S3:** `clear_night` (weather header, clear sky at night) and `smartphone` (event detail sheet, "added on this tablet") aren't ligatures in the bundled Material Symbols font, so they render as text. They become glyphs that exist (`clear_night` → `bedtime` or `nightlight`, `smartphone` → `mobile`; the plan picks after looking at both in a screenshot).
- An `Icons` object in `:core:ui` names every glyph the app uses (about 45); callers of `HhIcon` use it instead of string literals.
- `tools/fonts/subset.py` (fontTools; all four axes kept; `layout_closure=False`, without which the subset stays 15 MB) writes a committed `core/ui/src/main/res/font/material_symbols_rounded.ttf` of about 180 KB from `tools/fonts/icons.txt`; `tools/fonts/README.md` says how to add an icon.
- `IconFontTest` reads the committed font's ligature table and fails when an `Icons` name has no ligature, and when `icons.txt` and `Icons` disagree. `ThemeTest`'s "font is over 1 MB" assertion is relaxed.
- `lifecycle-viewmodel-ktx` (redundant) is removed from `:app`.

## 4. Performance

### 4.1 Start-up (U3, U4, U6, U8)

- `CulveryApp` injects `Lazy<Set<Startable>>` and starts them from `Dispatchers.Default`, so the network clients, the Google sign-in client and the loops are built off Main.
- The first calendar and weather passes wait for a one-shot "Home has drawn" signal from the shell, or 3 s, whichever comes first.
- A splash screen (`androidx.core:core-splashscreen`, new dependency) holds until the setup state is known and, when Home shows, the first card list has arrived — at most 2 s. The splash uses the theme's background; its icon comes with 4d's launcher icon.

### 4.2 Recomposition (P1, P2)

- `CulveryTheme` remembers its `ColorScheme`, keyed on the colours; `Typography` becomes a top-level value. *(Task 5 review: material3 1.3.2 provides the scheme through a static local with no `equals`, so a fresh scheme would invalidate everything below — but `CulveryTheme` skipped whenever `dark` was unchanged, so the per-minute trigger the audit described never happened. The change stays as hardening.)*
- `now` leaves `ShellUiState`; the status bar and the Home header read the time from their own state, so a minute tick recomposes the two clocks only.

### 4.3 One time source (P7, C6)

- `:core:plugin` gains a `@Singleton HouseholdClock`: wall time in the household zone, today's date and a minute tick, shared with `stateIn` on the application scope.
- The shell's ticker, `WeatherRepository`, `rememberNowMillis` / `nowTicks` and the calendar's `rememberToday` read it. `TodayCardHost` keys on the date alone, so the 30-second recomposition goes.

### 4.4 Flows and threads (P4, P5, U7)

- `CalendarRepository.days()` and `week()` gain `distinctUntilChanged()`: a pass that only updates `lastSyncMillis` changes nothing on screen.
- A Compose stability configuration file marks `java.time.*` and `:core:household`'s `Person` and `PersonId` stable.
- The calendar's view mapping runs with `flowOn(Dispatchers.Default)`.

### 4.5 Measuring

The plan records, on the emulator: **before** on a signed release with R8 off and the full font (measured right after signing lands, before §3.3 and §3.5), and **after** on the signed, minified release at the end of 4c: `am start -W` TotalTime (5 runs), skipped frames, `dumpsys gfxinfo`, Dalvik and native PSS, and the APK size. The numbers go in Appendix B; 4e repeats them on the tablet.

## 5. Kiosk and access

### 5.1 Culvery as the home app (D3, E6)

- `MainActivity` gains the `HOME` and `DEFAULT` categories.
- When Culvery isn't the default home app, the wizard's Done step and Settings › Kiosk show: "Make Culvery the home app so it comes back after a restart." with **Choose home app**, which asks Android's yes/no role dialog (`RoleManager.createRequestRoleIntent(ROLE_HOME)`) — no way into Settings from it. *(Plan review: was `ACTION_HOME_SETTINGS`, a Settings page a child could back out of into all of Settings.)* Starting either intent catches `ActivityNotFoundException`.
- Settings › Kiosk gains **Change home app** (fresh Admin PIN), to go back to the normal launcher.
- **Exit kiosk** unpins. When Culvery is the home app it stays in front, unpinned with the system bars showing (moving to the back would resume Culvery as home and pin again at once); otherwise it moves to the back as today. Culvery pins again the next time it comes to the front after leaving it.

- *(Walkthrough, 2026-10-05.)* One Culvery on screen: when Culvery is the default home app but an instance isn't in the home task (granting the role, or a launcher, recents or Settings start), it starts HOME and closes its own task if it runs again, and the home instance removes every other Culvery task each time it comes to the front (on a role grant Android starts the home instance itself and the asking instance never runs again); the home instance is the only one left. The wizard keeps the furthest step passed (Next or Skip for now) and resumes after it — amending 4a §3.3's "first shown step whose done is false"; a step whose required input is missing still can't be passed, and unsaved input on the current step isn't carried across.

### 5.2 Device owner (optional)

- A `DeviceAdminReceiver` (`CulveryDeviceAdmin`) with no policies beyond lock-task is declared.
- When `DevicePolicyManager.isDeviceOwnerApp`, Culvery calls `setLockTaskPackages(own package)` on start, and adds `com.google.android.gms` only while the Google chooser is open, removing it when Culvery is back in front (plan review; Task 9 review: a permanent allowlist would let any Play services screen start over the locked kiosk), so `startLockTask()` enters true lock-task (no prompt, no exit gesture). Otherwise screen pinning works as today.
- `docs/setup/release.md` gives `adb shell dpm set-device-owner uk.co.siland.culvery/.CulveryDeviceAdmin`, its precondition (a freshly reset tablet with no accounts yet; add the Google account afterwards) and that undoing it needs a factory reset.

### 5.3 Connecting Google on the kiosk (D5, K5, K6)

- A new core permission `connections.manage` (ADMIN), added to the fresh-PIN set with `kiosk.exit` and `people.manage`. Connect and Reconnect (wizard and Settings › Calendars) authorise it.
- Before starting: `GoogleApiAvailability.isGooglePlayServicesAvailable`; if not usable, "Update Google Play services on this tablet, then try again." and nothing starts.
- Just before the chooser opens, the provider calls `ShellNavigator.leavePinning()` (unless Culvery is device owner, when the chooser is allowlisted inside lock-task); Culvery pins again in `onResume` when the chooser returns — connected, cancelled or failed. In the wizard nothing is pinned (4a D10), so the step is skipped. *(Plan review: an explicit re-pin on the result never ran, as results arrive before `onResume`.)*

### 5.4 Kiosk fixes (K1–K4)

- K1: when not pinned (debug, after Exit kiosk) the root pads by `WindowInsets.systemBars`.
- K2: `kioskExited` is a `ShellViewModel` field, so a configuration change keeps it and a process death forgets it: a tablet killed while unpinned pins again when Culvery is next in front. *(Plan ruling 6: a `SavedStateHandle` would restore "exited" after a process death.)*
- K3: `LockoutStore` treats a stored `lockedUntil` more than 16 minutes in the future as expired.
- K4: `LockoutStore` keeps an in-memory mirror and writes with `apply()`; no disk I/O on Main.

## 6. Calendar sync

### 6.1 Ask for less (E1, C1)

- `events.list` sends `fields=` with only what the provider parses: `items(id,status,summary,start,end,recurringEventId,recurrence,extendedProperties/private,colorId,updated),nextPageToken,nextSyncToken`; `calendarList.list` likewise.
- The Google client sends `User-Agent: Culvery/<versionName> (gzip)` (Google compresses only for a user agent containing "gzip"); OkHttp already sends `Accept-Encoding: gzip`.
- The Google client gets a 60 s `callTimeout`, as the forecast's.

### 6.2 No nightly full resync (E2, P9, C2)

- The sync cursor's key drops the window's start date (`CalendarStore.kt` keys it `"$start|${zone.id}"` today, so every source fully resyncs at local midnight). A source's sync token lives until Google answers 410 (the existing full-resync path), the source changes, the household zone changes, or the window moves past what its full sync read (below).
- A full sync reads the window (today −1 to +28 days, household zone, §6.6) and 42 days more (`SYNC_AHEAD_DAYS`); the cursor's key is what was read and the zone, and the cursor serves while what was read still covers the window in the same zone. After each pass, stored events that end before the window starts or start at or after the end of what was read are deleted, so incremental results outside it don't accumulate; what was read past the window stays, as no incremental result would bring it back. *(Plan ruling 1: Google's incremental results carry changes at any date and nothing about unchanged events, so pruning to the window alone would lose events for good.)*
- *(D11)* The series-rule cache is not seeded; a full sync fetches each series' rule as today, now about once per token lifetime instead of nightly.
- `calendar.db` v6 (with §6.4): `MIGRATION_5_6` clears the stored cursors (one full sync per source on the first start) and adds `source.readProblem`. Hand-written, with a `MigrationTestHelper` test, as the repo requires.

### 6.3 Correctness (C3, C4, C9, E3)

- C3: a write refused with NeedsSignIn keeps the connection `NeedsSignIn` until a reconnect or an accepted write; a successful read in the same pass no longer resets it.
- C4: all-day events are selected by their local date, not by instant, so they don't straddle two days after a household zone change.
- C9: when an incremental result cancels a series' master event, every stored instance of that series is removed in the same pass.
- E3: within a pass, once one of a connection's calendars fails with Unreachable or NeedsSignIn, its remaining calendars are skipped until the next pass. A refusal of one calendar (403/404 while it's still listed) doesn't count — that's §6.4.

### 6.4 One calendar that can't be read (D6, C5, M7)

- `source.readProblem` (nullable) is set when a calendar's `events.list` is refused (403/404) while it is still in the calendar list, and cleared on its next successful read.
- The connection's health ignores calendar-level problems: it stays "Synced 2 min ago" while its other calendars sync.
- A refused calendar is retried on the normal pass, no longer flagged for a source refresh every pass.
- Settings › Calendars: the row says "Can't read this calendar — check it's still shared with this account" with **Hide this calendar** (turns Show off; hidden calendars aren't synced, and a true remove would come back on the daily refresh while Google still lists it). On the master calendar the row says "Choose another master calendar first" instead of the link.

### 6.5 Smaller items

- C7: the master-gone toast adds the dropped changes when there are any: "{Service}: can't find the master calendar — choose a new one in Settings › Calendars. {n} changes waiting to sync were dropped."
- C8: a test that `addConnection` is atomic.

### 6.6 Looking ahead (D10)

- The Calendar tab header gets **‹** and **›** beside the title, a week at a time: "This week", "Next week", "In 2 weeks", "In 3 weeks". **›** stops at 3 weeks ahead, **‹** at this week. A **This week** chip shows when not on this week.
- The view returns to this week after 2 minutes without a touch, and at midnight.
- Tapping empty space in a future week's day column opens quick-add with that day preset; **+** presets the first day of the week shown (today on this week).
- Adding stays unlimited through **Pick date…** (already unbounded). When the saved event starts after the last day the tablet shows, the toast says "Event added for {Tue 17 November}" instead of "Event added".
- `SYNC_FUTURE_DAYS` = 28: the window is today −1 to +28 days, inside the read horizon, so no extra requests; the token is kept while what it read still covers the window, so a full resync comes a little more often. Home's Today and Coming up cards are unchanged.

## 7. People, and test and code health

### 7.1 Reordering people (D7, P1)

- Each row in Settings › People gets **Move up** / **Move down** icon buttons with spoken labels ("Move Sam up"); none above the first or below the last.
- `HouseholdRepository.move(personId, up)` swaps `sortOrder` with the neighbour in one transaction; Family never moves.
- Authorised by `settings.manage` (the open Settings session). Toast: "{name} moved up" / "{name} moved down".
- The order already drives the Calendar legend, the Who chips and the people list.

### 7.2 Code health

- `Http.kt` resumes only with an `IOException` or the body; anything else becomes the caller's own exception (the forecast's "nothing else escapes" contract).
- Open-Meteo coordinates are sent with `toPlainString()` (no `-5.0E-4` near Greenwich).
- `retryWithBackoff` resets its backoff only after a value has stood for one backoff step, so a read that emits then fails doesn't log every second.
- `HomeScreen`'s test-only `headerItems` default goes.
- Setup-session state folds into the session value, with one "settings.manage unless no Admin yet" helper (4a).

### 7.3 Test health

- `StepsUiTest`: close the database only after the composition is disposed (the flake).
- `DebugSampleHousehold`: a test that fails its master-calendar step.
- 4b gaps: repeated days stored once; `WeatherUnavailableException` without a cause; null time and null low dropped; clearing the location stops fetching; `HouseholdTickerTest`'s bounded wait.
- Robolectric runs at SDK 35 (`robolectric.properties`), ending the "SDK 36 requires Java 21" warning.

## 8. Testing

### 8.1 In the build

- **Unit and Robolectric:** the signing failure message; `LogHygieneTest`; `IconFontTest`; `HouseholdClock` (one ticker for all); the cursor key, pruning, `MIGRATION_5_6`, series cancellation, skipping a failing connection; NeedsSignIn kept after a refused write; all-day by local date; `readProblem` set and cleared without touching connection health; `connections.manage` needs a fresh PIN; `leavePinning()` called before the chooser (and skipped as device owner); the Play services check; device-owner allowlisting (through a seam); the lockout clock guard; `move` at both ends; the splash hold (cards or 2 s); `distinctUntilChanged` on the calendar lists; the remembered `ColorScheme` (a recomposition counter).
- **Roborazzi, light and dark:** the clear-night header icon; the event detail "added on this tablet" icon; Settings › Kiosk with the home-app line; Settings › Calendars with an unreadable calendar and an unreadable master; the people list with move buttons; the Calendar tab on next week and on the furthest week.
- **Gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`; and `./gradlew :app:assembleRelease` succeeds with the signing properties set and fails with §3.1's message without them.

### 8.2 Emulator walkthrough, on the signed, minified release

1. Cold start with the splash; record Appendix B's numbers.
2. The wizard, choosing Culvery as the home app, then `adb reboot`: the tablet comes back to Culvery.
3. Connect Google with the release OAuth client (the user creates the key and the client first): a fresh PIN, out of lock-task for the chooser, pinned again after.
4. Calendars sync; an event saves; the pass after local midnight doesn't fully resync (log and a short clock test).
5. Clear night shows the corrected icon.
6. Unshare one calendar from the account in Google Calendar: the row's message and **Hide this calendar**.
7. Reorder people; step the Calendar tab ahead to its limit, add an event in a future week, add one beyond 4 weeks (the toast names its date), and leave it for 2 minutes (back to this week).
   Exit kiosk with Culvery as the home app: it stays in front, unpinned.
8. Release logcat: no debug or info lines from Culvery; no names, emails, towns, coordinates or zones.

## 9. Follow-ups: what 4c takes

Takes, from `2026-09-23-plan1-followups.md`: Plan 1's lockout guard, font subset, insets after Exit kiosk, `LockoutStore.commit()`, `kioskExited`, the signed release build; 2a's `addConnection` atomic test and all-day events after a zone change; 3a's L3, M4 (the non-device half), M7, L4, L6, the release OAuth client, the repeat-series rows (as §6.2), `TodayCardHost`, NeedsSignIn after a refused write; 4a's reordering people, per-calendar health, the toast count, the `DebugSampleHousehold` test, the `StepsUiTest` flake, the setup-session folding; 4b's Google `callTimeout` and its test-health and code-health lists.
Leaves for 4d: the accessibility items, the clock–date gap, `opsz`, header items overlapping the clock, all provisional design notes, the launcher icon. Leaves for 4e: every "on the SM-T510" item, the cold-start zone flash, the baseline profile, the `eventsBetween` index, memory and start-up on the device. Leaves for Later: L9 and E4 (a token per call), E5 (TLS reconnect each pass), E8/E9 (writes on empty passes), P6 (shared Room queries for Today and Coming up), S4 (pinning DM Sans), E7 (night brightness — v1 §14), the Open-Meteo Unix-time fix, the event extras, the ICS items, the `androidxSqlite` pin, shell Roborazzi tests.

## 10. Review focus

- The release build signs, minifies and still works end to end (sign-in, sync, writes, weather); no secret or password reaches the repo or a log.
- Nothing in a release log names a person, calendar, account, town, coordinates or zone.
- After a reboot the tablet is back in Culvery; Connect can't strand anyone outside the app, and pins again on every outcome.
- Dropping the nightly full resync doesn't let the `event` table grow or miss deletions (pruning, 410, series cancellation).
- `calendar.db` v6 migrates without losing mappings, the master or queued changes.
- Every icon the app names is in the subset font.

## 11. Out of scope

Accessibility, the launcher icon and design changes (4d); anything measured on the SM-T510, the baseline profile and the `eventsBetween` index (4e); the Later items in §9; the feature ideas recorded on 2026-10-02 (sleep and wake, council bin collections).

## Appendix A: audit (2026-10-02, read-only, emulator API 35, debug build unless stated)

**Size.** Release APK (unsigned) 33.5 MB, debug 42.4 MB. Release dex 25.9 MB (14.1 + 9.6 + 2.2 MB, 19,027 classes; includes ~1,606 Play services, 294 unused material-icons and ~3,050 foundation classes). Material Symbols Rounded 15.14 MB raw / 6.59 MB in the APK; subset of the ~45 used glyphs with all axes ≈ 179 KB (pinned to wght 400 / GRAD 0 / opsz 24 ≈ 27 KB). DM Sans 240 KB raw. Native libs ≈ 75 KB. Estimates: dex after R8 4–7 MB; release APK after R8 and the subset ≈ 15–20 MB. Release dex contains no debug-only classes (`calendar-fake`, test libraries).

**Start-up** (debug, run-from-APK, not representative of release). `am start -W` TotalTime over runs: 8242, 13959, 4671, 3259, 3960, 4497 ms. Skipped frames before Displayed 41–136; every cold start then had one further main-thread stall of 76–304 frames (1.3–5 s). gfxinfo after a cold start: 7 frames, 6 janky, p50 300 ms, p90 1400 ms. Memory after start: Dalvik 19.9 MB PSS, native 16.5 MB, total 114 MB. Timeline: splash to 4.7 s, grey blank to 5.3 s, shell 5.7 s, empty card frames 6.1 s, full Home 6.6 s. Sampled main thread: `CulveryApp.onCreate` 1.48 s (Hilt 1.39 s, of which building the calendar providers — OkHttp, the Google authorization client — 0.66 s); first measure 1.35 s, of which 0.93 s inflating the Material Symbols font.

**Runtime.** The shell root recomposed every minute (`now` in the root state); the audit's "skipping disabled by a fresh `ColorScheme`" proved not to happen (Task 5 review); the calendar UI rebuilds every 5 minutes on equal data; six or more independent minute and 30-second tickers; calendar mapping on Main. No leaks, no unbounded caches, no endless animations, no blur.

**Economy** (assumed 3 connections × 4 calendars). ~280 calendar passes a day; ~3,400 `events.list` a day plus ~190 at the midnight full resync (≈ 0.36% of the default Google project quota); ~8–9 MB a day, uncompressed and with every field. Weather 48 requests a day, 0.1–0.3 MB. Storage bounded: `calendar.db` ≈ 0.2–1 MB after a year (only the sync window is kept; the midnight full replace has been what removes out-of-window rows). The timers stop if the activity leaves the foreground, and nothing brings the app back after a reboot.

## Appendix B: before and after (filled in by the plan)

| Measure (emulator-5554, API 35; London, one Admin, no calendar) | Before: signed, R8 off, full font (2026-10-02) | After: signed, minified, all of 4c |
|---|---|---|
| Release APK size | 33.5 MB | 3.9 MB |
| `am start -W` TotalTime, 5 runs | 2240, 2467, 1110, 1855, 1025 ms (median 1855) | 2682, 3029, 3102, 2192, 3052 ms (median 3029) — *now Home with its cards, see below* |
| Skipped frames, cold start | 56, 119, 0, 0, 0 | 135, 51, 0, 146, 105 |
| gfxinfo p50 / p90 | 44 / 350 ms | 300 / 1900 ms |
| Dalvik / native PSS after start | 17.2 / 13.8 MB | 2.8 / 17.5 MB |

*Reading these numbers (Task 19, 2026-10-02).* The two columns don't measure the same moment. Before 4c, TotalTime ended at the first, empty frame and the Forecast card arrived 1–1.5 s later; with 4c's splash it ends when Home is drawn with its cards (the "Fully drawn" time `measure-release.sh` now prints equals it to the millisecond). The emulator is also noisy: the first starts after any install take 9–14 s for both builds, and Google Play services' crash loop lines up with the worst runs, so the script now discards five warm-up starts. An interleaved A/B of both builds on the same emulator (steady state, after six discarded starts each) gave: TotalTime median before 1392 / 1668 ms vs after 1691 / 2019 ms (after includes the cards); gfxinfo p90 before 850 / 1250 ms vs after 550 / 1150 ms; time until the Forecast card shows, before 2.7–6 s vs after 2–3.5 s. 4c gets to a usable Home sooner and uses about 15 MB less Dalvik heap; the real numbers come from the SM-T510 in 4e.

