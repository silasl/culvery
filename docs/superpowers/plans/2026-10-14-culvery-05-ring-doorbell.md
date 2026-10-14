# Culvery — Plan 5: the Ring doorbell, and the four additions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–15 are subagent tasks; Task 16 is the controller on a second emulator with the user; Task 17 needs the user and the son's SM-T510.

**Goal:** When someone presses the Ring doorbell, the wall tablet shows a card over whatever is open, with Ring's snapshot when it sends one, and plays a chime; a Door tab keeps today's and yesterday's rings and motion with an unseen badge on the rail. Before that, four small additions: the Culvery mark on the rail, a smaller Home clock with larger Today events, a failed Update now that says why, and repeating events made on the tablet.

**Architecture:** §A's four tasks change existing code only (`:app`'s rail and Home, `:core:ui`'s type, the calendar's sync status, form, sheet and Google write). Ring adds two seams to `:core:plugin` (`Capability.badge` with `RailBadge`; `AlertHost` with `LocalAlertHost`), drawn by the shell; a generalised `KioskLifecycle.openSystemScreen` reached through `ShellNavigator.openSystemScreen`; a capability `:capability:door` (Room `door.db` with one table, snapshot files in `files/door/`, a DataStore of settings, the alert decision, the chime, the card, the Door tab and Settings › Door); and a provider `:provider:door-ring` (a `NotificationListenerService` that reads only `com.ringapp`, an adapter to the Android-free `RingNotice`, and `RingParser`). The provider calls the capability through `DoorEventSink`; the capability asks the provider about the tablet through `DoorProvider`.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, Room 2.8.5, DataStore 1.2.1, Coroutines 1.10.2, androidx.core 1.16.0 (`BundleCompat`, `NotificationManagerCompat`), JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1; `NotificationListenerService`, `MediaPlayer`; Python 3 standard library for the chime sound; Python with fontTools for the icon subset (as `tools/fonts/README.md`).

**Spec:** `docs/superpowers/specs/2026-10-14-culvery-5-ring-doorbell-design.md` (§A, then §1–§9). Read it with this plan.
**Previous plans (format, patterns):** `docs/superpowers/plans/2026-10-12-culvery-04e-device-beta.md`, `docs/superpowers/plans/2026-10-09-culvery-04d2-talkback-timeouts-words.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Tasks 16–17 update it).

**Plan series:** 1 · 2a · 2b-1 · 2b-2 · 3a · 4a · 4b · 4c · 4d-1 · 4d-2 · 4e (done) · **5 the Ring doorbell (this plan)**.

**Builds on:** branch `plan-5-ring-doorbell` at 03586ba (the spec with §A), in the worktree `C:\Users\si\code\siland\culvery-ring`. Work only there; never touch `C:\Users\si\code\siland\culvery`. Never push. File and line references were read at 03586ba.

| # | Task | Review |
|---|---|---|
| 1 | §A.1 The Culvery mark on the rail | sonnet |
| 2 | §A.2 A smaller Home clock, larger Today events | sonnet |
| 3 | §A.3 A failed Update now says why (`ConnectionHealth.Offline`, `SyncProblem`) | sonnet |
| 4 | §A.4 Repeating series, part 1: the rule, the draft, the store and the Google write | **opus** (the write path) |
| 5 | §A.4 Repeating series, part 2: All day or Pick time, Repeats and Ends in the add sheet | sonnet |
| 6 | Rail badges: `RailBadge`, `Capability.badge`, the rail draws and speaks them | sonnet |
| 7 | The alert layer: `AlertHost`, `ShellAlerts`, the layer between the sheets and the PIN pad, `inFront` | **opus** (touch counting and timers) |
| 8 | `openSystemScreen`: the kiosk's system-screen path, generalised and reachable from a capability | **opus** (pinning) |
| 9 | `:capability:door`: the module, icons, `door.db`, merge, prune, unseen, badge, settings, the prune loop | **opus** (files and data) |
| 10 | `:provider:door-ring`: `RingNotice`, `RingParser` with best-guess fixtures, the notification adapter | sonnet |
| 11 | The listener, `RingReader`, `DoorRecorder` (the sink), `RingProvider`, debug fake rings, the capture log | **opus** (privacy, a system service) |
| 12 | The door card (stateless UI) | sonnet |
| 13 | The alert decision: `DoorAlerts`, quiet hours, the 10-second chime limit, the chime and its sound | **opus** (state and timers) |
| 14 | The Door tab | sonnet |
| 15 | Settings › Door, the connect steps, `DoorHealth` and the status line | **opus** (system checks, the kiosk) |
| 16 | The emulator walk-through on a second AVD; simplify | controller + user |
| 17 | On the tablet: Ring's real notifications captured, the parser fixed, final review, local merge, install (§8.3) | controller + user, **needs the SM-T510** |

**Task order and why:** the spec puts §A first (Tasks 1–5). Repeats split in two so the write path (Task 4) gets its own reviewer before the form uses it. Ring then goes bottom-up: the shell's seams (6–8) before any capability uses them; the door module and its data (9) before the provider that feeds it (10–11); the card (12) before the decision that shows it (13); the tab (14) before Settings and the health that fill its status line (15). The emulator check (16) comes before the tablet (17), whose captures fix the parser before the final review and merge.

## Rulings against the code

Where the spec leaves a choice open, or doesn't fit the code at 03586ba:

1. **"Culvery's activity is resumed" is MainActivity's own `onResume`/`onPause`**, written to `ShellAlerts.inFront`. `ProcessLifecycleOwner` would need `lifecycle-process`, a new dependency, and the app has one activity.
2. **`AlertHost` carries `inFront: StateFlow<Boolean>`** beside the spec's `show(content)` and `dismiss()`, so a capability can ask "would an alert be seen?" without depending on `:app`.
3. **`AlertHost` is injected** (`@Singleton ShellAlerts`, bound in `AppModule` as `ShellToasts` is for `Toaster`): rings arrive from a service, with no composable on screen to read `LocalAlertHost`. `LocalAlertHost` is provided too, as the spec says, for composables.
4. **The alert layer sits outside the box whose pointer input counts touches**; the PIN pad gets its own counting box while it shows. TalkBack gestures on the card still count as touches: `ShellUserActivity`'s accessibility delegate sees events from the whole content view and can't tell the card apart. Accepted (a TalkBack user reading the card is using the tablet).
5. **The chime is generated, not downloaded:** `tools/sound/door_chime.py` (Python standard library) writes `capability/door/src/main/res/raw/door_chime.wav`, a 1.6 s two-tone bell, 22.05 kHz 16-bit mono, about 70 KB. Culvery's own work under the repository's licence; no third-party sound. Played by `MediaPlayer` with `USAGE_NOTIFICATION_EVENT`.
6. **"Offline" and "didn't answer" are told apart from what the pass already reports:** a new `ConnectionHealth.Offline`, recorded when the `UnreachableException` a pass ends on has `UnknownHostException`, `ConnectException` or `NoRouteToHostException` in its causes (OkHttp's offline failures; the Google client already keeps the cause). Stored as `"OFFLINE"`. The week reads it as `SyncStatusUi.problem: SyncProblem?`. No new network code.
7. **The failure lines name the service** ("Google Calendar didn’t answer. …"), as 4d-2 Decision 12 requires of visible copy, where the spec's table says "Google".
8. **Door settings live in a DataStore file `door_settings`** (volume, quiet hours, the capture switch's end, "connected", last heard, the run of unrecognised alerts); `door.db` keeps its one table, as the spec says.
9. **"The `door.ring` connection is saved" is a `connected` flag** in `door_settings`; nothing else about Ring is stored. `ProviderDescriptor("door.ring", "Ring", …)` names the provider.
10. **`RingNotice` adds `ongoing` and `groupSummary`** to the spec's fields, so Ring's live-view and bundle notifications are `Ignore`, not unknown rings that chime.
11. **The capture and the parse outcome log with `Log.println(Log.INFO, …)`:** R8 strips `Log.i` and `Log.d` in release (`app/proguard-rules.pro`), and the capture must run on the tablet's release build. `LogHygieneTest`'s `ALLOWED` gains `capture.line` (the spec's one exception, §6) and `reading.outcome`.
12. **Debug fake rings:** `:provider:door-ring` has `src/debug` and `src/release` versions of `object FakeRings`. Debug reads `com.android.shell` notifications whose tag starts `culvery-fake-ring` as Ring's (a suffix, `culvery-fake-ring-2`, gives a new notification key, so the walk-through can post two different rings within 2 minutes), and counts the Ring app as installed unless `DebugRingReceiver` (`app/src/debug`, sender needs `DUMP`, as `DebugOfflineReceiver`) says otherwise, so the emulator walk-through can tick step 1 and still show "the Ring app isn't on this tablet".
13. **Package visibility:** the provider's manifest has `<queries><package android:name="com.ringapp" /></queries>`; without it Android 11+ always answers "not installed". The check is `getLaunchIntentForPackage` (`getPackageInfo(String, int)` is deprecated from API 33).
14. **"The last 3 Ring alerts were all unrecognised" is a run counter** in `door_settings`: +1 on a new unrecognised alert, back to 0 on any new recognised ring or motion. History is pruned after 48 hours; the counter isn't.
15. **"The bird mark" is the launcher icon's mark**: the house and the dove from `ic_launcher_foreground.xml`, drawn from the same two paths, the house in `ink` and the dove in `weather` (gold at night, darker gold by day). A test keeps the paths equal to the launcher's.
16. **Today's sizes are Today-only styles**, `CalendarType.todayTitle` (20 sp / 600) and `CalendarType.todayTime` (16 sp / 400): `HhType.rowTitle` is used by Settings rows, the forecast and list rows too.
17. **The clock at 88 sp keeps its proportions:** letter spacing −3.4 sp (was −4 at 104), line height 79.2 sp (0.9), clock-to-date baselines 42 dp (the hand-off's 11 px below the baseline scales to 9; +12 +21).
18. **Pick time with no time chosen opens on** the next whole hour today (23:00 at the latest), 09:00 on another day. The slots that set it before are gone.
19. **An end date before the event's day** can be picked (the date picker keeps past days selectable, 2b-2 D10) but Save stays disabled and a line under Ends says why; the same holds if the event's day is moved past the end date afterwards.
20. **A new series is not put in the mirror.** Google answers an insert with the series itself, which a `singleEvents` read never lists; mirrored, it would show twice beside its first occurrence until a full read. The editor and the outbox drain leave it to the sync every write asks for (`applyAcceptedWrite(…, series = true)`).
21. **A timed series names its zone to Google** (`start.timeZone`, `end.timeZone` = the household zone): Google requires it to repeat a series. One-off events keep sending UTC with no zone, as now.
22. **`openHomeAppScreen` becomes `openSystemScreen`**; its home-app tests keep their bodies and only call the new name.
23. **`ShellNavigator.openSystemScreen(intent, missing)`**: the view model has no window, so it does nothing there (as `leavePinning`); MainActivity's navigator runs `kiosk.openSystemScreen(missing) { startActivity(intent) }`.
24. **Merging is measured from the event's first post:** a key posted again within 2 minutes of the event's time updates it.
25. **The Door tab marks events seen only while Culvery is resumed** (`collectAsStateWithLifecycle(minActiveState = RESUMED)`), so a ring in the background with the Door tab left open stays unseen until someone comes back.
26. **The card's 2 minutes are a coroutine `delay`** (monotonic). The kiosk keeps the screen on, so the tablet isn't expected to sleep with a card up.
27. **Quiet hours with From equal to Until are never quiet.**
28. **Quiet hours are set with Earlier and Later in 30-minute steps** (no time picker in `:core:ui`; the calendar's is internal to it). Provisional, for the end-of-v1 design walk-through.
29. **Thumbnails decode off the main thread** (`produceState` on `Dispatchers.IO`); there is no image library.
30. **`CalendarProviderContractTest` is unchanged:** `FakeGoogleServer` doesn't expand series, so the series write is pinned by `GoogleWriteTest` and `FakeCalendarProviderTest`.
31. **`ModuleBoundaries.kt` needs no change:** its rules go by family, and `:capability:door` with `:provider:door-ring` are family `door`. `ModuleBoundariesTest` pins the new pair (Task 9).
32. **Settings › Door is page id `door`, order 500** (Calendars 400, Tablet lock 900).

Nothing in this plan needs a deprecated API. Flagged and avoided: `PackageManager.getPackageInfo(String, int)` and `getApplicationInfo(String, int)` (use `getLaunchIntentForPackage`), `Bundle.getParcelable(String)` (use `BundleCompat.getParcelable`), `invisibleToUser()` (use `hideFromAccessibility()`). If the compiler shows a deprecation warning on a line you wrote, **stop and ask**.

## Global Constraints

**From the spec, verbatim:**

- §2.2 "a ring raises the card and the chime; motion goes into the history only."
- §2.3 "Culvery plays its own chime, with a volume in Settings."
- §2.4 "a Door tab on the rail (slot 50, reserved for Security), today and yesterday."
- §2.5 "unseen rings as a count, unseen motion alone as a dot; both clear when someone opens the Door tab; a ring whose card was dismissed with Got it counts as seen."
- §2.6 "quiet hours in Settings, 21:00–07:00 by default, fixed times rather than sunset. In quiet hours the card and badge still show; no chime."
- §2.7 "Culvery not in front … record only. No card, no chime, no 'display over other apps' permission."
- §2.8 "on the card and in the history, kept 48 hours in Culvery's private storage, then deleted with their entry."
- §2.9 "closes after 2 minutes or on Got it. A second ring while it shows refreshes it and chimes again."
- §2.10 "its own alert layer above sheets, Settings and the wizard, below the PIN pad. It never replaces or closes an open sheet, so unsaved edits survive a ring."
- §2.13 "No Ring credentials in Culvery."
- §3.1 "`id = "door"`, label "Door", icon `doorbell`, rail order 50. `hasTab` is true once a Ring connection exists."
- §4.1 "`onNotificationPosted` returns at once for any package but Ring's (`com.ringapp`). Nothing else on the tablet is read or stored. Removals are ignored."
- §4.3 "A post with a key already seen in the last 2 minutes updates that event … and does not chime again."
- §4.4 "scaled to at most 640 px wide and saved as JPEG at `files/door/<eventId>.jpg`. A failed save keeps the event without a picture."
- §4.5 "Each insert, and once a day, deletes events older than 48 hours and their snapshot files. Files with no event (left by a crash) go at the same time."
- §4.6 "the chime plays unless it is quiet hours, or the chime played less than 10 seconds ago."
- §4.7 "Opening the Door tab marks every event seen. Got it marks the card's ring seen. The 2-minute close leaves it unseen."
- §4.1 (chime) "`AudioAttributes.USAGE_NOTIFICATION_EVENT`. Culvery's volume setting (default 70 %) … Quiet hours use the household's time zone and may cross midnight (From 21:00, Until 07:00 means 21:00–06:59)."
- §5.3 TalkBack: "Door, 2 unseen rings", "Door, 1 unseen ring", "Door, new motion".
- §5.4 "**Check Ring's notifications:** a switch, off by default, that turns itself off after 30 minutes. … In the release build."
- §6 "Logs carry the parse outcome and kind only, never title, text or device names. … The capture (§8.3) is the one exception, off by default, on for 30 minutes at most, to logcat only."
- §3.5 "No new rule." (backup: `door.db` and `files/door/` are in the excluded `database` and `file` domains; DataStore files are under `files/datastore/`, in `file` too.)

**Copy, verbatim from the spec (curly apostrophes in code):** "Someone’s at the door", "Ring alert", "Got it", "Today", "Yesterday", "Ring", "Motion", "No rings or motion today.", "Chime volume", "Play chime", "Quiet hours", "From", "Until", "Last heard from Ring: Tuesday 14:02" (the pattern), "Check Ring’s notifications", "Ring app installed and signed in", "Allow notification access", "Culvery can’t see Ring’s notifications. Allow notification access in Settings › Door.", "The Ring app isn’t on this tablet. Install it and sign in, then check Settings › Door.", "Culvery can’t fully read Ring’s notifications, so rings show as ‘Ring alert’.", "The tablet is offline. Check the Wi-Fi, then Update now.", "Repeats", "Never", "Every day", "Every week", "Every 2 weeks", "Every month", "Every year", "Ends", "All day", "Pick time…". Ruling 7 for the two service lines.

**From the house rules:**

- Package root `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. No new library; never change a version. New modules use libraries already in `gradle/libs.versions.toml`.
- **Deprecated APIs:** none without asking (see above).
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test`, `check` or `build`, except the JVM-only `build-logic` build: `./gradlew -p build-logic :convention:test` (Tasks 9 and 11 change its tests). A task runs its module's tests while working (`./gradlew :capability:calendar:testDebugUnitTest --tests '*EventFormTest*'`); every task ends with the full gate before its commit.
- **Tests that can fail:** every new test fails before its change, and its step says why ("Fails today: …"); a "Fails if" line names the mutation it catches. A test that passes before its change is labelled **pin**, with what it pins. Timer tests use a fake clock (`TestScope`'s scheduler, or the Compose test clock with `mainClock.autoAdvance = false`) and never sleep; each checks 1 ms (or one frame) before its boundary and at it.
- **Screenshots:** baselines in `<module>/src/test/screenshots/`. Before re-recording, `./gradlew <module>:compareRoborazziDebug --tests "<class pattern>"`, open each `*_compare.png` under `<module>/build/outputs/roborazzi/`, and check each difference against the step's list; then `./gradlew <module>:recordRoborazziDebug --tests "<class pattern>"`. Open every new image (the Read tool shows it). **The baseline check:** write the step's expected names one per line to `$TMP/expected-baselines.txt`, then
```bash
git status --porcelain -- "*.png" | sed 's#.*/##; s#\.png$##' | sort -u > "$TMP/changed.txt"
echo "changed but not listed:"; comm -23 "$TMP/changed.txt" <(sort -u "$TMP/expected-baselines.txt")
echo "listed but unchanged:"; comm -13 "$TMP/changed.txt" <(sort -u "$TMP/expected-baselines.txt")
```
Both lists must be empty; anything in either is a stop and report.
- **Copy** names what the user does; errors say what went wrong and how to fix it; curly quotes and apostrophes (’ ‘ “ ”) in every string; "—", "›", "…" as written. `CopyScanTest` (`core/ui/src/test/java/uk/co/siland/culvery/core/ui/CopyScan.kt`) fails on a straight quote or apostrophe in copy and on the words master, kiosk, sync, read-only, assign, feed (with -s, -ed, -ing). Errors, toasts and sentences end with a full stop; labels, buttons and chips don't.
- **Logs:** `LogHygieneTest` (`build-logic/convention/src/test/kotlin/LogHygieneTest.kt`): every `Log.w`, `Log.e`, `Log.wtf`, `Log.println` in `main`/`release` passes a tag and one literal message interpolating only `::class.simpleName`, a `.code`, or an id in `LogHygiene.ALLOWED`. Never log a Ring notification's title, text or device name, a household name, an email or a PIN (the capture is the spec's one exception, ruling 11).
- **Layout numbers are named**, in the module's tokens or dimens object (or a private `val` at the top of a file that already keeps its own). Tests may use literal dp.
- **Icons** come from `Icons` (`core/ui/.../Icons.kt`); a new one is added there, to `tools/fonts/icons.txt`, and to the subset font (`tools/fonts/README.md`).
- **Comments say what the code can't**, one line by default. No comment names this plan's task numbers. No marketing words.
- **Write every file as UTF-8.**
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on `plan-5-ring-doorbell`; never push. **Never** `git checkout -- .` or `git restore .`; to undo your own change, restore only the files you changed, after `git diff --name-only`.

## Review Focus

The five failure modes the spec implies, most likely to bite the household, that the spec's own test list doesn't cover; each is pinned by a test in its owning task:

1. **Ring posts the text first, then re-posts the same key with the snapshot, while the card shows.** Expected: one event, the card and the row gain the picture, no second chime, the card's 2 minutes not restarted.
   - Task 13 `DoorAlertsTest.aSnapshotRepostUpdatesTheShowingCardWithoutAChime`.
2. **The nights the clocks change.** Expected: quiet hours follow the wall clock (01:30 on 25 October is quiet both times it happens; on 29 March 06:59 BST is quiet and 07:00 BST isn't); a series ending on a change day ends at that evening's 23:59:59 local time.
   - Task 13 `QuietHoursTest.quietHoursFollowTheWallClockOnTheNightsTheClocksChange`; Task 4 `RecurrenceTest.onTheDaysTheClocksChangeUntilUsesThatEveningsOffset`.
3. **Notification access turned off in Android's Settings while Culvery stays in front** (the son, or a system update). Expected: the status line appears as soon as Android unbinds the listener, without waiting for a resume, and goes when access is back and the listener binds.
   - Task 15 `DoorHealthTest.accessTurnedOffWhileInFrontShowsAtOnceAndClearsWhenBack`.
4. **A ring while the son has Culvery in the background, then Culvery comes back to the front.** Expected: the ring was stored, the badge counts it, and coming back raises no card and plays no chime.
   - Task 13 `DoorAlertsTest.aRingInTheBackgroundShowsNoCardWhenCulveryReturns`.
5. **Midnight with the Door tab open.** Expected: today's rows move under Yesterday at once, and the day before yesterday's events (still kept, inside 48 hours) don't show.
   - Task 14 `DoorDaysTest.atMidnightTodaysRowsBecomeYesterdaysAndOlderOnesLeave` and `DoorTabTest.theTabFollowsTheHouseholdsDate`.

The spec's own tests (§7, §A.4) each have their task: parser fixtures (10), merge, prune, unseen, Got it and the 2-minute close, quiet hours across midnight, the 10-second limit, record-only, the three-unrecognised status, last heard (9, 11, 13, 15), the card over an edited sheet and below the PIN pad (7), the card's and the badge's words (6, 12), the tab's empty state (14), `openSystemScreen` (8), the repeat rules and labels (4, 5).

---

## File Structure

```
settings.gradle.kts                                              (Tasks 9, 10: include the two modules)
build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt   (Task 9: the door pair, pins)
build-logic/convention/src/test/kotlin/LogHygieneTest.kt         (Task 11: ALLOWED gains capture.line, reading.outcome)
tools/fonts/icons.txt, core/ui/src/main/res/font/material_symbols_rounded.ttf, core/ui/.../Icons.kt   (Task 9: doorbell, directions_walk)
tools/sound/door_chime.py                                        (create, Task 13)
core/ui/.../Type.kt                                              (Task 2: the clock)
core/plugin/.../Capability.kt (Task 6: badge)   RailBadge.kt (create, Task 6)   Alerts.kt (create, Task 7)
core/plugin/.../Connections.kt (Task 3: Offline)   ShellNavigator.kt (Task 8: openSystemScreen)
app/build.gradle.kts                                             (Task 9: the door modules)
app/src/main/java/uk/co/siland/culvery/
  MainActivity.kt (Tasks 7, 8)   KioskLifecycle.kt (Task 8)   di/AppModule.kt (Task 7)
  shell/ShellUiState.kt (Task 6)   shell/ShellViewModel.kt (Tasks 6, 8)   shell/ShellOverlays.kt (Task 7: ShellAlerts)
  shell/ui/CulveryMark.kt (create, Task 1)   shell/ui/NavRail.kt (Tasks 1, 6)   shell/ui/HomeScreen.kt (Task 2)
  shell/ui/OverlayLayers.kt (Task 7)
app/src/debug/AndroidManifest.xml, app/src/debug/java/uk/co/siland/culvery/DebugRingReceiver.kt (Task 11)
app/src/test/... NavRailTest, CulveryMarkTest (Task 1), ShellLayoutTest (Task 2), ShellViewModelTest, Fakes (Task 6),
  AlertLayerTest (create, Task 7), KioskLifecycleTest (Task 8), NoNavigation (Task 8), AppSourceTest (Task 9), ShellScreenshotTest (Task 6)
capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/
  CalendarStore.kt (Tasks 3, 4)   CalendarSync.kt (Tasks 3, 4)   CalendarUi.kt (Task 3)   CalendarRepository.kt (Task 3)
  CalendarContract.kt (Task 4)   Recurrence.kt (create, Task 4)   Writes.kt (Task 4)   CalendarEditor.kt (Task 4)
  EventForm.kt (Task 5)   ui/ReviewCalendars.kt (Task 3)   ui/TodayCard.kt, ui/CalendarType.kt (Task 2)
  ui/EventEditorSheet.kt, ui/Pickers.kt (Task 5)
provider/calendar-google/.../GoogleEvents.kt (Task 4)   provider/calendar-fake/.../FakeCalendarProvider.kt (Tasks 3, 4)
capability/door/                                                 (create, Tasks 9, 12–15)
  build.gradle.kts   src/main/AndroidManifest.xml   schemas/   src/main/res/raw/door_chime.wav (Task 13)
  src/main/java/uk/co/siland/culvery/capability/door/
    DoorContract.kt   db/DoorDatabase.kt   DoorStore.kt   DoorSettings.kt   DoorCapability.kt   DoorPruneLoop.kt   di/DoorModule.kt (Task 9)
    DoorRecorder.kt (Task 11; Task 13 adds the alert)   DoorWords.kt (Task 12, grown in 14, 15)
    ui/DoorCard.kt   ui/DoorDimens.kt   ui/Snapshots.kt (Task 12)
    QuietHours.kt   Chime.kt   DoorAlerts.kt   ui/DoorCardHost.kt (Task 13)
    DoorDays.kt   ui/DoorTab.kt (Task 14)
    DoorHealth.kt   ui/DoorSettingsPage.kt (Task 15)
provider/door-ring/                                              (create, Tasks 10–11)
  build.gradle.kts   src/main/AndroidManifest.xml (Task 11)
  src/main/java/uk/co/siland/culvery/provider/door_ring/
    RingNotice.kt   RingParser.kt   RingAdapter.kt (Task 10)
    RingNotificationListener.kt   RingReader.kt   RingProvider.kt   di/RingModule.kt (Task 11)
  src/debug/java/.../FakeRings.kt   src/release/java/.../FakeRings.kt (Task 11)
  src/test/java/.../RingFixtures.kt   RingParserTest.kt   RingAdapterTest.kt (Task 10)   RingReaderTest.kt (Task 11)
docs/superpowers/plans/2026-09-23-plan1-followups.md            (Tasks 16, 17)
```

`…` stands for the module's package directory (`src/main/java/uk/co/siland/culvery/<module path>`); each step spells out the full path the first time it names a file.

---
### Task 1: The Culvery mark on the rail (§A.1; ruling 15)

**Review:** sonnet.

**Files:**
- Create: `app/src/main/java/uk/co/siland/culvery/shell/ui/CulveryMark.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/NavRail.kt:39-68` (the mark above the tabs)
- Create: `app/src/test/java/uk/co/siland/culvery/shell/ui/CulveryMarkTest.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/NavRailTest.kt`
- Baselines (changed, 15): `home_calendar_dark`, `home_calendar_light`, `home_empty_dark`, `home_empty_light`, `home_fs15`, `home_session_dark`, `home_toast_dark`, `home_weather_dark`, `home_weather_light`, `pin_pad_dark`, `pin_pad_light`, `pin_pad_fs15`, `pin_pad_wrong_dark`, `settings_warning_dark`, `sheet_warning_dark` (every `ShellScreenshotTest` image draws the rail; the `launcher_icon_*` ones don't).

**Interfaces:**
- Consumes: nothing new.
- Produces: `internal const val MARK_HOUSE: String`, `internal const val MARK_DOVE: String`, `@Composable internal fun CulveryMark(width: Dp, modifier: Modifier = Modifier)`; the rail's mark has test tag `rail_mark`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/uk/co/siland/culvery/shell/ui/CulveryMarkTest.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** The rail's mark is the launcher icon's house and dove (5 §A.1): one drawing, so they can't drift apart. */
class CulveryMarkTest {
    // Unit tests run in the module's directory.
    private val launcher = File("src/main/res/drawable/ic_launcher_foreground.xml").readText(Charsets.UTF_8)

    private val paths = Regex("""android:pathData="([^"]+)"""").findAll(launcher).map { it.groupValues[1] }.toList()

    @Test
    fun theMarkDrawsTheLauncherIconsTwoPaths() {
        assertThat(paths).containsExactly(MARK_HOUSE, MARK_DOVE).inOrder()
    }
}
```
Add to `NavRailTest.kt` (imports: `androidx.compose.ui.test.getUnclippedBoundsInRoot`, `com.google.common.truth.Truth.assertThat`):
```kotlin
    /** 5 §A.1: about 48 dp, above Home; a picture, not a button, which TalkBack skips. */
    @Test
    fun theMarkSitsAboveHomeAndTalkBackSkipsIt() {
        show()
        val mark = compose.onNodeWithTag("rail_mark", useUnmergedTree = true)
        mark.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        mark.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        val bounds = mark.getUnclippedBoundsInRoot()
        assertThat(bounds.width.value).isWithin(0.5f).of(48f)
        assertThat(bounds.bottom.value).isAtMost(compose.onNodeWithTag("rail_home").getUnclippedBoundsInRoot().top.value)
    }
```
Fails today: `MARK_HOUSE` doesn't exist (the first doesn't compile) and there is no `rail_mark`.
Fails if: a path is edited in one place only; the mark is made clickable, given a description, or moved below Home.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*CulveryMarkTest*' --tests '*NavRailTest*'`
Expected: FAIL to compile on `MARK_HOUSE`, `MARK_DOVE`.

- [ ] **Step 3: Write the mark**

Create `app/src/main/java/uk/co/siland/culvery/shell/ui/CulveryMark.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.ui.Culvery

/** The launcher icon's house (ic_launcher_foreground.xml), in its 108-unit square. */
internal const val MARK_HOUSE =
    "M31.06,53.92 A3.22,3.22 0 0,1 26.86,49.04 L49.4,29.72 A3.22,3.22 0 0,1 53.6,29.72 L76.14,49.04 A3.22,3.22 0 0,1 71.94,53.92 L70.82,52.96 L70.82,74.48 A2.76,2.76 0 0,1 68.06,77.24 L57.94,77.24 L57.94,68.96 A6.44,6.44 0 0,0 45.06,68.96 L45.06,77.24 L34.94,77.24 A2.76,2.76 0 0,1 32.18,74.48 L32.18,52.96 L31.06,53.92 Z"

/** The launcher icon's dove on the right eave. */
internal const val MARK_DOVE =
    "M71.67,42.8 C74.32,42.58 76.52,40.15 77.19,37.28 C77.41,36.17 77.63,35.29 77.96,34.63 L79.84,33.96 L78.18,33.3 C77.85,31.76 76.52,30.87 75.31,30.87 C73.87,30.87 72.99,31.98 72.77,33.3 C71.67,34.19 69.24,34.41 67.25,33.96 L61.51,31.09 C60.63,30.65 59.96,31.76 60.63,32.42 L67.03,38.16 C68.35,40.15 69.68,42.36 71.67,42.8 Z"

// The two paths' bounds in the 108-unit square, rounded out: the mark without the icon's safe zone.
private const val LEFT = 26f
private const val TOP = 29f
private const val WIDTH = 54f
private const val HEIGHT = 49f

/** The Culvery mark, [width] wide: the house in `ink`, the dove in `weather`'s gold. A picture only; TalkBack skips it. */
@Composable
internal fun CulveryMark(width: Dp, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val mark = remember(c.ink, c.weather, width) {
        ImageVector.Builder(name = "CulveryMark", defaultWidth = width, defaultHeight = width * (HEIGHT / WIDTH), viewportWidth = WIDTH, viewportHeight = HEIGHT)
            .addGroup(translationX = -LEFT, translationY = -TOP)
            .addPath(addPathNodes(MARK_HOUSE), fill = SolidColor(c.ink))
            .addPath(addPathNodes(MARK_DOVE), fill = SolidColor(c.weather))
            .clearGroup()
            .build()
    }
    Image(mark, contentDescription = null, modifier = modifier.semantics { hideFromAccessibility() })
}
```
In `NavRail.kt`, add at the top of the file, after `private val HomeTab`:
```kotlin
// 5 §A.1: the mark about 48 dp wide, 8 above the first tab.
private val MARK_WIDTH = 48.dp
private val MARK_GAP = 8.dp
```
and make the outer `Column`'s first child, before the `selectableGroup` column:
```kotlin
        CulveryMark(MARK_WIDTH, Modifier.testTag("rail_mark").padding(bottom = MARK_GAP))
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*CulveryMarkTest*' --tests '*NavRailTest*'`
Expected: PASS.

- [ ] **Step 5: Screenshots**

Run `./gradlew :app:compareRoborazziDebug --tests '*ShellScreenshotTest*'`; each of the 15 names above differs only by the mark at the top of the rail (dark: a light house, gold dove; light: a dark house, darker gold dove), the tabs below it moved down by about 51 dp. Then `./gradlew :app:recordRoborazziDebug --tests '*ShellScreenshotTest*'`, write the 15 names to `$TMP/expected-baselines.txt`, run the baseline check, and open `home_calendar_dark.png` and `home_calendar_light.png`.

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add app/src/main/java/uk/co/siland/culvery/shell/ui/CulveryMark.kt app/src/main/java/uk/co/siland/culvery/shell/ui/NavRail.kt app/src/test/java/uk/co/siland/culvery/shell/ui/CulveryMarkTest.kt app/src/test/java/uk/co/siland/culvery/shell/ui/NavRailTest.kt app/src/test/screenshots/*.png
git commit -m "Show the Culvery mark above the rail's tabs"
```

---

### Task 2: A smaller Home clock, larger Today events (§A.2; rulings 16, 17)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Type.kt:38-42` (`HhType.clock`)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/HomeScreen.kt:38-39` (`CLOCK_TO_DATE_BASELINES`)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt` (two styles), `ui/TodayCard.kt:111-119`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt:67-85`; `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt`
- Baselines (changed): the same 15 `:app` names as Task 1 (every one shows Home's clock); `:capability:calendar` `today_dark`, `today_light`, `today_badges_dark` (`today_empty_dark` has no rows: unchanged).

**Interfaces:**
- Consumes: nothing new.
- Produces: `HhType.clock` at 88 sp; `CalendarType.todayTitle`, `CalendarType.todayTime` (internal to the calendar).

- [ ] **Step 1: Write the failing tests**

In `ShellLayoutTest.kt`, rename `dateBaselineSits44dpBelowClockBaseline` to `dateBaselineSits42dpBelowClockBaseline` and change its `.of(44f)` to `.of(42f)`; in `theClockBoxIsItsLineHeight` change its KDoc to "Hand-off: the clock's line box is 0.9 × its size, 88 sp since 5 §A.2." and `.of(94f)` to `.of(79f)`. Add:
```kotlin
    /** 5 §A.2: the clock went from 104 to 88 sp; the date is unchanged. */
    @Test
    fun theClockIs88spAndTheDate21() {
        compose.setContent { CulveryTheme(dark = true) { HomeScreen({ at }, emptyList(), emptyList()) } }
        assertThat(compose.onNodeWithTag("home_clock").textLayout().layoutInput.style.fontSize).isEqualTo(88.sp)
        assertThat(compose.onNodeWithTag("home_date").textLayout().layoutInput.style.fontSize).isEqualTo(21.sp)
    }
```
(`textLayout()` is the module's `app/src/test/.../shell/ui/TextLayouts.kt` helper; import `androidx.compose.ui.unit.sp`.)

In `CardsTest.kt` add:
```kotlin
    /** 5 §A.2: Today's titles 20 sp and their time line 16 sp; other cards' rows keep their sizes. */
    @Test
    fun todaysRowsUseTheLargerSizes() {
        show { TodayCard(SampleUi.today) }
        assertThat(compose.onNodeWithText("School run", useUnmergedTree = true).textLayout().layoutInput.style.fontSize).isEqualTo(20.sp)
        assertThat(compose.onNodeWithText("07:45–08:30 · Sam", useUnmergedTree = true).textLayout().layoutInput.style.fontSize).isEqualTo(16.sp)
    }
```
Fails today: the clock is 104 sp (box 94 dp, baselines 44 dp); Today's title is 17 sp and its time line 14 sp.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :app:testDebugUnitTest --tests '*ShellLayoutTest*' :capability:calendar:testDebugUnitTest --tests '*CardsTest*'`
Expected: FAIL on the four sizes.

- [ ] **Step 3: Change the sizes**

`Type.kt`, `HhType.clock`:
```kotlin
    // 88 sp since 5 §A.2; the hand-off's −4 tracking and 0.9 line height at 104 sp, scaled.
    val clock = style(88, FontWeight.W600, tabular = true).copy(
        letterSpacing = (-3.4).sp,
        lineHeight = 79.2.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
```
`HomeScreen.kt:38-39`:
```kotlin
// Hand-off CSS: clock line-height 0.9 (9 px below its baseline at 88 sp) + 12 px margin + the date's 21 px ascent.
private val CLOCK_TO_DATE_BASELINES = 42.dp
```
`CalendarType.kt`, after `infoValue`:
```kotlin
    /** 20 sp / 600: Today's event titles on Home (5 §A.2); other rows keep HhType.rowTitle. */
    val todayTitle = HhType.rowTitle.copy(fontSize = 20.sp)

    /** 16 sp / 400: Today's time and person line. */
    val todayTime = HhType.secondary.copy(fontSize = 16.sp)
```
`TodayCard.kt` `TodayRow`: `style = HhType.rowTitle` becomes `style = CalendarType.todayTitle`, and `style = HhType.secondary` becomes `style = CalendarType.todayTime`.

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew --continue :app:testDebugUnitTest :capability:calendar:testDebugUnitTest`
Expected: PASS, including `ShellLayoutTest.atFontScale13HeaderItemsKeepClearOfTheClockAndDate`, `CardsTest.todayShowsTheFourthRowInTheTallCard` and the calendar's `FontScaleTest`. If any of those three fails, stop and report: the larger rows or the smaller clock changed a layout the spec didn't ask to change.

- [ ] **Step 5: Screenshots**

`./gradlew :app:compareRoborazziDebug --tests '*ShellScreenshotTest*' :capability:calendar:compareRoborazziDebug --tests '*CardScreenshotTest*'`. Expected differences: the clock smaller and the date higher in all 15 `:app` images; Today's rows taller with larger text in `today_dark`, `today_light`, `today_badges_dark` and in the `home_calendar_*`, `home_weather_*` and `home_fs15` images. Record both (`recordRoborazziDebug` with the same patterns), put the 18 names in `$TMP/expected-baselines.txt`, run the baseline check, and open `home_weather_dark.png` and `today_light.png`.

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add core/ui/src/main/java/uk/co/siland/culvery/core/ui/Type.kt app/src/main/java/uk/co/siland/culvery/shell/ui/HomeScreen.kt capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/TodayCard.kt app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt app/src/test/screenshots/*.png capability/calendar/src/test/screenshots/today_*.png
git commit -m "Make Home's clock smaller and Today's events larger"
```

---

### Task 3: A failed Update now says why (§A.3; rulings 6, 7)

**Review:** sonnet.

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Connections.kt:37-42` (`ConnectionHealth.Offline`)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt:305-312, 356-372` (classify, break, severity)
- Modify: `capability/calendar/.../CalendarStore.kt:391-405` (`code()`, `healthOf`)
- Modify: `capability/calendar/.../CalendarUi.kt:74-80, 125-129` (`SyncStatusUi.problem`, `SyncProblem`, `weekLine`, `problemLine`)
- Modify: `capability/calendar/.../CalendarRepository.kt:46-55` (`problem`)
- Modify: `capability/calendar/.../ui/ReviewCalendars.kt:84-89` (`healthWords`)
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt:37, 173, 198, 212` (offline has a cause)
- Test: `capability/calendar/src/test/.../CalendarSyncTest.kt`, `CalendarStoreTest.kt`, `CalendarRepositoryTest.kt`, `SyncLabelsTest.kt`, `ui/WeekViewTest.kt`, `ui/HealthWordsTest.kt`, `ui/WeekScreenshotTest.kt`
- Baselines (new): `week_offline_dark`. None change: no existing `SyncStatusUi` in a screenshot has a problem.

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `ConnectionHealth.Offline : ConnectionHealth` (data object).
  - In `CalendarSync.kt`: `internal fun Throwable.isOffline(): Boolean`.
  - In `CalendarUi.kt`: `sealed interface SyncProblem { data object Offline; data class NoAnswer(val service: String); data class SignIn(val service: String) }`; `SyncStatusUi(…, val problem: SyncProblem? = null)` (last); `fun problemLine(problem: SyncProblem): String`; `weekLine` unchanged in signature.
  - In `CalendarRepository.kt`: `internal fun problemOf(connections: List<StoredConnection>, service: (StoredConnection) -> String): SyncProblem?`.

- [ ] **Step 1: Write the failing tests**

`CalendarSyncTest.kt` (imports `java.net.ConnectException`, `java.net.UnknownHostException`):
```kotlin
    /** 5 §A.3: no address looked up or reached is the tablet's own connection, not the service. */
    @Test
    fun aPassThatFoundNoAddressIsOfflineAndOneThatGotNoAnswerIsUnreachable() = runTest {
        connect("c1", "calendar.a", s1)
        connect("c2", "calendar.b", s1)
        a.failWith = UnreachableException("Couldn't reach", UnknownHostException("no address"))
        b.failWith = UnreachableException("Google Calendar answered 503")
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Offline)
        assertThat(health("c2")).isEqualTo(ConnectionHealth.Unreachable)
        a.failWith = UnreachableException("Couldn't reach", ConnectException("refused"))
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Offline)
    }
```
`CalendarStoreTest.kt`:
```kotlin
    @Test
    fun offlineHealthIsStoredAsItself() = runTest {
        store.addConnection(Connection("c9", "calendar.test", "Other", emptyMap()), emptyList(), emptyMap())
        store.setHealth("c9", ConnectionHealth.Offline, 0L)
        assertThat(store.connectionsNow().single { it.connection.id == "c9" }.health).isEqualTo(ConnectionHealth.Offline)
    }
```
(If `CalendarStoreTest` names its store differently, use its own; it builds one over `calendarDb()` as `CalendarSyncTest` does.)

`CalendarRepositoryTest.kt`:
```kotlin
    /** 5 §A.3: the line says what the family can do; signing in first, as only they can fix it. */
    @Test
    fun theProblemIsSignInThenOfflineThenNoAnswer() = runTest {
        store.addConnection(Connection("c2", "calendar.test", "School", emptyMap()), emptyList(), emptyMap())
        store.setHealth("c1", ConnectionHealth.Unreachable, 0L)
        assertThat(repo.syncStatus.first().problem).isEqualTo(SyncProblem.NoAnswer("Google"))
        store.setHealth("c2", ConnectionHealth.Offline, 0L)
        assertThat(repo.syncStatus.first().problem).isEqualTo(SyncProblem.Offline)
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 0L)
        assertThat(repo.syncStatus.first().problem).isEqualTo(SyncProblem.SignIn("Google"))
        store.markSynced("c1", 1L)
        store.markSynced("c2", 1L)
        assertThat(repo.syncStatus.first().problem).isNull()
    }
```
`SyncLabelsTest.kt`:
```kotlin
    private fun status(agoMinutes: Long, problem: SyncProblem?) =
        SyncStatusUi(minutesAgo(agoMinutes), emptyList(), listOf("Google Calendar"), failingBeforeFirstSync = false, problem = problem)

    /** 5 §A.3's table, verbatim but for the service's name (4d-2 Decision 12). */
    @Test
    fun aStaleWeekWithAProblemSaysWhatWentWrongAndWhatToDo() {
        assertThat(weekLine(status(45, SyncProblem.Offline), now)).isEqualTo("The tablet is offline. Check the Wi-Fi, then Update now.")
        assertThat(weekLine(status(45, SyncProblem.NoAnswer("Google Calendar")), now))
            .isEqualTo("Google Calendar didn’t answer. Try Update now in a few minutes.")
        assertThat(weekLine(status(45, SyncProblem.SignIn("Google Calendar")), now))
            .isEqualTo("Google Calendar needs you to sign in again. Reconnect in Settings › Calendars.")
    }

    @Test
    fun aFreshWeekNeverShowsAProblemAndAStaleOneWithoutOneShowsItsAge() {
        assertThat(weekLine(status(2, SyncProblem.Offline), now)).isEqualTo("Updated from Google Calendar 2 minutes ago")
        assertThat(weekLine(status(45, null), now)).isEqualTo("Out of date — updated from Google Calendar 45 minutes ago")
    }
```
`ui/WeekViewTest.kt`:
```kotlin
    /** 5 §A.3: the reason in the week's line, with Update now still beside it. */
    @Test
    fun aFailedUpdateSaysWhyAndKeepsUpdateNow() {
        show { WeekView(state(sync(agoMinutes = 45, connections = listOf("Google Calendar")).copy(problem = SyncProblem.Offline))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("The tablet is offline. Check the Wi-Fi, then Update now.")
        compose.onNodeWithTag("week_update_now").assertExists()
        compose.onNodeWithTag("week_out_of_date", useUnmergedTree = true).assertExists()
    }
```
`ui/HealthWordsTest.kt`: add an assertion, in the file's own style, that a row whose health is `ConnectionHealth.Offline` reads "The tablet is offline — check the Wi-Fi."
`ui/WeekScreenshotTest.kt`, after `weekStaleLight`:
```kotlin
    @Test fun weekOfflineDark() = snap("week_offline_dark", true, stale.copy(problem = SyncProblem.Offline))
```
Fails today: `ConnectionHealth.Offline`, `SyncProblem` and `SyncStatusUi.problem` don't exist (nothing compiles); then the line keeps "Out of date — …".
Fails if: the cause chain stops being read (both become Unreachable); the order in `problemOf` changes; a fresh week shows a problem.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*CalendarSyncTest*' --tests '*CalendarStoreTest*' --tests '*CalendarRepositoryTest*' --tests '*SyncLabelsTest*' --tests '*WeekViewTest*' --tests '*HealthWordsTest*'`
Expected: FAIL to compile on `ConnectionHealth.Offline`, `SyncProblem`.

- [ ] **Step 3: Offline health**

`Connections.kt`, in `ConnectionHealth`:
```kotlin
    /** The tablet itself couldn't look up or reach any address: its Wi-Fi, not the service (5 §A.3). */
    data object Offline : ConnectionHealth
```
`CalendarStore.kt`: `code()` gains `ConnectionHealth.Offline -> "OFFLINE"`; `healthOf` gains `"OFFLINE" -> ConnectionHealth.Offline`.
`CalendarSync.kt` (imports `java.net.ConnectException`, `java.net.NoRouteToHostException`, `java.net.UnknownHostException`), at the end of the file:
```kotlin
/** No address looked up or reached: the tablet's own connection (5 §A.3). OkHttp keeps the cause the provider wraps. */
internal fun Throwable.isOffline(): Boolean =
    generateSequence(this) { it.cause }.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException }
```
In `healthAfter`, the `is UnreachableException` branch returns `if (e.isOffline()) ConnectionHealth.Offline else ConnectionHealth.Unreachable` after its log line. In `sync`, the break becomes
```kotlin
            if (health == ConnectionHealth.Unreachable || health == ConnectionHealth.Offline || health == ConnectionHealth.NeedsSignIn) break
```
and `severity()` gains `ConnectionHealth.Offline -> 2`.
`ReviewCalendars.kt` `healthWords` gains `ConnectionHealth.Offline -> "The tablet is offline — check the Wi-Fi."`.
`FakeCalendarProvider.kt` (import `java.net.UnknownHostException`): add below `OFFLINE_MESSAGE`
```kotlin
/** As OkHttp fails with no network: the sync records the tablet as offline (5 §A.3). */
private fun offline() = UnreachableException(OFFLINE_MESSAGE, UnknownHostException(OFFLINE_MESSAGE))
```
and each `if (offline) throw UnreachableException(OFFLINE_MESSAGE)` (lines 173, 198, 212) becomes `if (offline) throw offline()`. The `unreachableNext` throws stay as they are (a service that didn't answer).

- [ ] **Step 4: The week's line**

`CalendarUi.kt`, before `SyncStatusUi`:
```kotlin
/** Why the last pass failed, by what the family can do about it (5 §A.3). */
sealed interface SyncProblem {
    data object Offline : SyncProblem

    data class NoAnswer(val service: String) : SyncProblem

    data class SignIn(val service: String) : SyncProblem
}
```
`SyncStatusUi` gains, last: `/** The worst connection's problem; null when every connection is Ok. */ val problem: SyncProblem? = null,`.
Replace `weekLine`:
```kotlin
/**
 * The week's line with its state in words as well as colour: fresh, its age; out of date, what went wrong and what to do
 * (5 §A.3), or "Out of date — updated from Google Calendar 3 hours ago" when the last pass didn't fail.
 */
fun weekLine(sync: SyncStatusUi, nowMillis: Long): String {
    val line = weekSubtitle(sync, nowMillis)
    if (!sync.isStaleAt(nowMillis)) return line
    return sync.problem?.let(::problemLine) ?: "Out of date — ${line.replaceFirstChar { it.lowercase() }}"
}

/** 5 §A.3's table; Update now stays beside it. */
fun problemLine(problem: SyncProblem): String = when (problem) {
    SyncProblem.Offline -> "The tablet is offline. Check the Wi-Fi, then Update now."
    is SyncProblem.NoAnswer -> "${problem.service} didn’t answer. Try Update now in a few minutes."
    is SyncProblem.SignIn -> "${problem.service} needs you to sign in again. Reconnect in Settings › Calendars."
}
```
`CalendarRepository.kt`, in `syncStatus`, `SyncStatusUi(…)` gains `problem = problemOf(connections, service),`; add at the end of the file:
```kotlin
/** Signing in first (only the family can fix it), then the tablet offline, then a service that didn't answer. */
internal fun problemOf(connections: List<StoredConnection>, service: (StoredConnection) -> String): SyncProblem? =
    connections.firstOrNull { it.health == ConnectionHealth.NeedsSignIn }?.let { SyncProblem.SignIn(service(it)) }
        ?: SyncProblem.Offline.takeIf { connections.any { it.health == ConnectionHealth.Offline } }
        ?: connections.firstOrNull { it.health == ConnectionHealth.Unreachable }?.let { SyncProblem.NoAnswer(service(it)) }
```

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew --continue :capability:calendar:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :provider:calendar-google:testDebugUnitTest`
Expected: PASS. A `when` over `ConnectionHealth` elsewhere that no longer compiles gets an `Offline` branch beside its `Unreachable` one, with the same handling; name it in the report.

- [ ] **Step 6: Screenshot**

`./gradlew :capability:calendar:recordRoborazziDebug --tests '*WeekScreenshotTest*'`; `week_offline_dark` is the only new or changed image (`echo week_offline_dark > "$TMP/expected-baselines.txt"`, then the baseline check). Open it: the red sync-problem icon, the offline line, Update now beside it.

- [ ] **Step 7: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Connections.kt capability/calendar/src provider/calendar-fake/src/main
git commit -m "Say why Update now failed: offline, no answer, or sign in again"
```

---

### Task 4: Repeating series, part 1: the rule, the draft, the store and the Google write (§A.4; rulings 20, 21, 30; Review Focus 2)

**Review:** opus.

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Recurrence.kt`
- Modify: `capability/calendar/.../CalendarContract.kt:111-118` (`EventDraft.recurrence`)
- Modify: `capability/calendar/.../CalendarStore.kt:424-444` (`encodeDraft`, `decodeDraft`)
- Modify: `capability/calendar/.../Writes.kt:120-138` (`applyAcceptedWrite(…, series)`), `CalendarEditor.kt:399`, `CalendarSync.kt:271`
- Modify: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleEvents.kt:68-84, 105-116`
- Modify: `provider/calendar-fake/.../FakeCalendarProvider.kt:130-138`
- Test: `capability/calendar/src/test/.../RecurrenceTest.kt` (create), `CalendarStoreTest.kt`, `CalendarEditorTest.kt`; `provider/calendar-google/src/test/.../GoogleWriteTest.kt`; `provider/calendar-fake/src/test/.../FakeCalendarProviderTest.kt`

**Interfaces:**
- Consumes: `repeatsLabel(rule: String?, start: EventTime, zone: ZoneId): String` (`Repeats.kt`, unchanged).
- Produces (package `uk.co.siland.culvery.capability.calendar`):
  - `enum class RepeatChoice(val label: String, val freq: String?, val interval: Int) { Never, Daily, Weekly, Fortnightly, Monthly, Yearly }`
  - `data class Recurrence(val rule: String, val zone: ZoneId)`
  - `fun recurrenceRule(choice: RepeatChoice, endsOn: LocalDate?, allDay: Boolean, zone: ZoneId): String?`
  - `EventDraft(…, val recurrence: Recurrence? = null)` (last)
  - `internal suspend fun CalendarStore.applyAcceptedWrite(…, completing: Long? = null, series: Boolean = false)`

- [ ] **Step 1: Write the failing tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/RecurrenceTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

/** 5 §A.4: what Save writes for each choice, both UNTIL forms, and that it reads back as a simple label. */
class RecurrenceTest {
    private val london = ZoneId.of("Europe/London")

    @Test
    fun eachChoiceWritesItsFrequencyAndInterval() {
        assertThat(RepeatChoice.entries.map { recurrenceRule(it, endsOn = null, allDay = false, zone = london) }).containsExactly(
            null,
            "RRULE:FREQ=DAILY;INTERVAL=1",
            "RRULE:FREQ=WEEKLY;INTERVAL=1",
            "RRULE:FREQ=WEEKLY;INTERVAL=2",
            "RRULE:FREQ=MONTHLY;INTERVAL=1",
            "RRULE:FREQ=YEARLY;INTERVAL=1",
        ).inOrder()
        assertThat(RepeatChoice.entries.map { it.label })
            .containsExactly("Never", "Every day", "Every week", "Every 2 weeks", "Every month", "Every year").inOrder()
    }

    @Test
    fun anAllDaySeriesEndsOnADate() {
        assertThat(recurrenceRule(RepeatChoice.Weekly, LocalDate.of(2026, 12, 31), allDay = true, zone = london))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261231")
    }

    @Test
    fun aTimedSeriesEndsAtTheEndOfThatDayInTheHouseholdZoneAsUtc() {
        assertThat(recurrenceRule(RepeatChoice.Weekly, LocalDate.of(2026, 12, 31), allDay = false, zone = london))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261231T235959Z")
        assertThat(recurrenceRule(RepeatChoice.Daily, LocalDate.of(2027, 6, 30), allDay = false, zone = london))
            .isEqualTo("RRULE:FREQ=DAILY;INTERVAL=1;UNTIL=20270630T225959Z")
    }

    /** Review Focus 2: the change happens in the small hours, so the end of the day is in the evening's offset. */
    @Test
    fun onTheDaysTheClocksChangeUntilUsesThatEveningsOffset() {
        assertThat(recurrenceRule(RepeatChoice.Weekly, LocalDate.of(2026, 10, 25), allDay = false, zone = london))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261025T235959Z")
        assertThat(recurrenceRule(RepeatChoice.Weekly, LocalDate.of(2026, 3, 29), allDay = false, zone = london))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20260329T225959Z")
    }

    /** Never "Yes": whatever Save writes, the detail sheet's Repeats row reads as the chip that wrote it. */
    @Test
    fun whatSaveWritesReadsBackAsItsChipsLabel() {
        val starts = listOf(
            EventTime.AllDay(LocalDate.of(2026, 9, 23)) to true,
            EventTime.Timed(Instant.parse("2026-09-23T17:00:00Z")) to false,
        )
        for ((start, allDay) in starts) {
            for (choice in RepeatChoice.entries - RepeatChoice.Never) {
                for (end in listOf(null, LocalDate.of(2026, 12, 31))) {
                    assertThat(repeatsLabel(recurrenceRule(choice, end, allDay, london), start, london)).isEqualTo(choice.label)
                }
            }
        }
    }
}
```
`CalendarStoreTest.kt`:
```kotlin
    /** A queued new series keeps its rule and zone through the outbox, so the drain writes a series. */
    @Test
    fun aQueuedDraftKeepsItsRepeatRule() = runTest {
        store.addConnection(Connection("c9", "calendar.test", "Other", emptyMap()), emptyList(), emptyMap())
        val day = LocalDate.of(2026, 9, 27)
        val draft = EventDraft(
            "Swimming", EventTime.AllDay(day), EventTime.AllDay(day.plusDays(1)), "family", "alex",
            recurrence = Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1", ZoneId.of("Europe/London")),
        )
        store.enqueue(PendingChange(0, "c9", "s1", null, ChangeKind.CREATE, draft, 0, 0L, 0L, clientKey = "k1"))
        assertThat(store.pendingNow().single().draft).isEqualTo(draft)
    }
```
`CalendarEditorTest.kt`:
```kotlin
    /** Ruling 20: the service returns the series itself, which a read never lists; the sync brings its occurrences. */
    @Test
    fun aNewSeriesIsLeftForTheSyncToBring() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val series = draft("Swimming", PersonId.FAMILY.value).copy(recurrence = Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1", london))
        assertThat(editor(access).create(series, "key-series")).isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().recurrence).isEqualTo(series.recurrence)
        assertThat(store.eventNow(ref("key-series"))).isNull()
        assertThat(syncRequests).isAtLeast(1)
        // A one-off is still mirrored at once.
        assertThat(editor(access).create(draft("Pizza night", PersonId.FAMILY.value), "key-once")).isEqualTo(EditResult.Done)
        assertThat(store.eventNow(ref("key-once"))).isNotNull()
    }
```
`GoogleWriteTest.kt` (imports `kotlinx.serialization.json.jsonArray`, `kotlinx.serialization.json.jsonPrimitive`, `uk.co.siland.culvery.capability.calendar.Recurrence`):
```kotlin
    /** Ruling 21: Google repeats a timed series in the zone it is given, and needs one. */
    @Test
    fun aTimedSeriesInsertSendsItsRuleAndTheHouseholdZone() = runTest {
        val rule = "RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261231T235959Z"
        provider.create(conn, family, draft().copy(recurrence = Recurrence(rule, london)), key)
        val sent = google.bodies.single()
        assertThat(sent.getValue("recurrence").jsonArray.map { it.jsonPrimitive.content }).containsExactly(rule)
        assertThat(sent.obj("start").text("timeZone")).isEqualTo("Europe/London")
        assertThat(sent.obj("end").text("timeZone")).isEqualTo("Europe/London")
        assertThat(sent.obj("start").text("dateTime")).isEqualTo("2026-09-23T15:00:00Z")
    }

    @Test
    fun anAllDaySeriesSendsItsRuleAndDatesWithoutAZone() = runTest {
        val day = LocalDate.of(2026, 9, 23)
        val allDay = EventDraft("Bins", EventTime.AllDay(day), EventTime.AllDay(day.plusDays(1)), null, "sam-id",
            recurrence = Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1", london))
        provider.create(conn, family, allDay, key)
        val sent = google.bodies.single()
        assertThat(sent.getValue("recurrence").jsonArray.map { it.jsonPrimitive.content }).containsExactly("RRULE:FREQ=WEEKLY;INTERVAL=1")
        assertThat(sent.obj("start")["timeZone"]).isNull()
    }
```
`FakeCalendarProviderTest.kt`:
```kotlin
    @Test
    fun aNewSeriesComesBackRepeatingWithItsRule() = runTest {
        val provider = providerOn(today)
        val start = EventTime.Timed(today.plusDays(1).atTime(18, 0).atZone(zone).toInstant())
        val end = EventTime.Timed(today.plusDays(1).atTime(19, 0).atZone(zone).toInstant())
        val made = provider.create(conn, family, EventDraft("Swimming", start, end, null, null,
            recurrence = Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1", zone)), "k-series")
        assertThat(made.recurring).isTrue()
        assertThat(made.recurrenceRule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1")
    }
```
Fails today: `RepeatChoice`, `Recurrence`, `recurrenceRule` and `EventDraft.recurrence` don't exist.
Fails if: UNTIL is written in local time, or the date form is used for a timed series; the store drops the rule; a series is mirrored; Google gets no zone for a timed series, or a zone for a one-off.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :capability:calendar:testDebugUnitTest --tests '*RecurrenceTest*' --tests '*CalendarStoreTest*' --tests '*CalendarEditorTest*' :provider:calendar-google:testDebugUnitTest --tests '*GoogleWriteTest*' :provider:calendar-fake:testDebugUnitTest --tests '*FakeCalendarProviderTest*'`
Expected: FAIL to compile on `Recurrence`, `RepeatChoice`, `recurrenceRule`.

- [ ] **Step 3: The rule**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Recurrence.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The add sheet's Repeats chips (5 §A.4); [label] is also what `repeatsLabel` reads the written rule back as. */
enum class RepeatChoice(val label: String, val freq: String?, val interval: Int) {
    Never("Never", null, 1),
    Daily("Every day", "DAILY", 1),
    Weekly("Every week", "WEEKLY", 1),
    Fortnightly("Every 2 weeks", "WEEKLY", 2),
    Monthly("Every month", "MONTHLY", 1),
    Yearly("Every year", "YEARLY", 1),
}

/** A new series: its RRULE line and the household zone, which Google needs to repeat a timed series. */
data class Recurrence(val rule: String, val zone: ZoneId)

private val UNTIL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private val UNTIL_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
private val END_OF_DAY: LocalTime = LocalTime.of(23, 59, 59)

/**
 * `RRULE:FREQ=…;INTERVAL=n`, with `UNTIL` when the series [endsOn] a day: that date for an [allDay] series; for a timed
 * one, 23:59:59 that day in [zone], as UTC (an hour earlier in BST). Null for [RepeatChoice.Never].
 */
fun recurrenceRule(choice: RepeatChoice, endsOn: LocalDate?, allDay: Boolean, zone: ZoneId): String? {
    val freq = choice.freq ?: return null
    val until = endsOn?.let { day ->
        if (allDay) day.format(UNTIL_DATE) else day.atTime(END_OF_DAY).atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(UNTIL_UTC)
    }
    return "RRULE:FREQ=$freq;INTERVAL=${choice.interval}" + (until?.let { ";UNTIL=$it" } ?: "")
}
```

- [ ] **Step 4: The draft and the store**

`CalendarContract.kt` `EventDraft` gains, last, with a line in its KDoc ("[recurrence] makes a new event a series; only a create reads it."): `val recurrence: Recurrence? = null,`.
`CalendarStore.kt` `encodeDraft` gains
```kotlin
    .put("rule", d.recurrence?.rule ?: JSONObject.NULL)
    .put("zone", d.recurrence?.zone?.id ?: JSONObject.NULL)
```
before `.toString()`, and `decodeDraft`'s `EventDraft(…)` gains
```kotlin
        // A row from before 5 has no rule.
        recurrence = o.stringOrNull("rule")?.let { Recurrence(it, ZoneId.of(o.getString("zone"))) },
```
with `stringOrNull` reading a missing key as null: change it to `if (!has(key) || isNull(key)) null else getString(key)`.

- [ ] **Step 5: Leaving a new series to the sync**

`Writes.kt` `applyAcceptedWrite`: add the parameter `series: Boolean = false` after `completing`, a KDoc sentence ("A new [series] isn't mirrored: the service returns the series itself, which a read never lists; the sync every write asks for brings its occurrences, and the outbox row it came from is completed."), and a first `when` branch:
```kotlin
        kind == ChangeKind.CREATE && series -> completing?.let { dropChange(it) }
```
`CalendarEditor.kt:399`: `store.applyAcceptedWrite(kind, to.connection.id, to.source.id, remoteId, draft?.forPerson, outcome, zone.current(), series = draft?.recurrence != null)`.
`CalendarSync.kt:271`: `store.applyAcceptedWrite(change.kind, conn.id, source.id, change.remoteId, change.draft?.forPerson, accepted, zone, completing = change.id, series = change.draft?.recurrence != null)`.

- [ ] **Step 6: The providers**

`GoogleEvents.kt`: the KDoc of `insertBody` ends "…all-day as dates. A series sends its rule, and its timed times also name the household zone, which Google needs to repeat them (5 §A.4)."; in its body, `put("start", timeJson(draft.start, forPatch = false, zone = draft.recurrence?.zone))`, the same for `end`, and after the colour:
```kotlin
    draft.recurrence?.let { r -> putJsonArray("recurrence") { add(r.rule) } }
```
`timeJson` gains `zone: ZoneId? = null` and, in its `Timed` branch after `dateTime`, `zone?.let { put("timeZone", it.id) }` (imports `java.time.ZoneId`, `kotlinx.serialization.json.add`, `kotlinx.serialization.json.putJsonArray`).
`FakeCalendarProvider.kt` `create`: the made event is
```kotlin
                RemoteEvent(
                    clientKey, draft.title, draft.start, draft.end, recurring = draft.recurrence != null, draft.forPerson, draft.createdBy,
                    recurrenceRule = draft.recurrence?.rule,
                )
```

- [ ] **Step 7: Run them to see them pass**

Run the Step 2 command, then `./gradlew --continue :capability:calendar:testDebugUnitTest :capability:calendar-testkit:testDebugUnitTest :provider:calendar-google:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest`
Expected: PASS, the contract suites included (ruling 30).

- [ ] **Step 8: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL, no image changed.
```bash
git add capability/calendar/src provider/calendar-google/src provider/calendar-fake/src
git commit -m "Write a repeat rule with a new event, and leave the series to the sync"
```

---

### Task 5: Repeating series, part 2: the add sheet (§A.4; rulings 18, 19)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/EventForm.kt` (slots go; repeat and end)
- Modify: `capability/calendar/.../ui/EventEditorSheet.kt:48-49, 150-160, 175-201, 320-344` (Time, Repeats, Ends, the end picker)
- Modify: `capability/calendar/.../ui/Pickers.kt:67` (`EditorPicker.EndDate`)
- Test: `capability/calendar/src/test/.../EventFormTest.kt`, `ui/EventEditorSheetTest.kt`, `ui/EditorScreenshotTest.kt`
- Baselines (changed): `editor_keyboard_dark/light`, `editor_empty_dark/light`, `editor_child_dark/light`, `editor_edit_dark/light`, `editor_own_length_dark/light`, `editor_failed_dark/light`, `editor_date_picker_dark/light`, `editor_time_picker_dark/light` (the slot chips go; a new event also shows Repeats). Unchanged: `editor_locked_*` (no Time chips). New: `editor_repeats_dark`, `editor_repeats_light`.

**Interfaces:**
- Consumes: `RepeatChoice`, `Recurrence`, `recurrenceRule` (Task 4).
- Produces:
  - `TimeChoice` is `AllDay` or `Custom(time)`; `TimeSlot` and `TimeChoice.Slot` are gone.
  - `EventForm`: `val canRepeat: Boolean`, `var repeat: RepeatChoice` (private set), `var endsOn: LocalDate?` (private set), `fun chooseRepeat(choice: RepeatChoice)`, `fun chooseEnd(date: LocalDate?)`, `val endsBeforeStart: Boolean`; `draft()` sets `recurrence` for a new event.
  - `enum class EditorPicker { None, Date, Time, EndDate }`.
  - Test tags: `repeat_<RepeatChoice.name>`, `ends_never`, `ends_pick`, `ends_error`.

- [ ] **Step 1: Rewrite the form's tests for All day and Pick time**

In `EventFormTest.kt`: delete `slot()`, `theDefaultTimeIsTheNextSlotStillToComeToday`, `changingTheDayReappliesTheDefaultUntilTimeIsTouched` and `aPickedTimeThatIsASlotsTimeSelectsThatSlot`. Replace `aLaterDayDefaultsToMorningAndAWeekColumnPresetsIt` and `thePickerOpensOnTheChosenTimeOrTheDefaultSlotsTime` with:
```kotlin
    /** 5 §A.4: All day is the default, today or any other day; the slots are gone. */
    @Test
    fun aNewEventStartsAllDayAndAWeekColumnPresetsItsDay() {
        assertThat(new(now = "08:00").time).isEqualTo(TimeChoice.AllDay)
        val form = new(now = "20:00", day = today.plusDays(2))
        assertThat(form.day).isEqualTo(today.plusDays(2))
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        form.chooseDay(today)
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
    }

    /** Ruling 18: the picker opens on the chosen time; with none, the next whole hour today, 09:00 another day. */
    @Test
    fun thePickerOpensOnTheChosenTimeOrTheNextHour() {
        assertThat(new(now = "10:54").pickerTime).isEqualTo(LocalTime.of(11, 0))
        assertThat(new(now = "23:30").pickerTime).isEqualTo(LocalTime.of(23, 0))
        assertThat(new(now = "10:54", day = today.plusDays(1)).pickerTime).isEqualTo(LocalTime.of(9, 0))
        val custom = new()
        custom.chooseTime(TimeChoice.Custom(LocalTime.of(16, 15)))
        assertThat(custom.pickerTime).isEqualTo(LocalTime.of(16, 15))
    }
```
Rename `anEditStartingOnASlotSelectsItAndAddsNoLengthChip` to `anEditOnTheHourIsAPickedTimeAndAddsNoLengthChip`, its time assertion now `TimeChoice.Custom(LocalTime.of(18, 0))`. In `theSummaryReadsLikeTheWhenRowWithThePerson` the first line expects `"Today · All day · Family"` and `chooseTime(slot(TimeSlot.Afternoon))` becomes `chooseTime(TimeChoice.Custom(LocalTime.of(14, 0)))`. `aTimedDraftIsInTheHouseholdZone` starts with `form.chooseTime(TimeChoice.Custom(LocalTime.of(14, 0)))`. In `aDraftOnTheAutumnChangeDayUsesTheLocalTimes`, `slot(TimeSlot.Evening)` becomes `TimeChoice.Custom(LocalTime.of(18, 0))`. In `tomorrowChosenAt2330IsTheDateItShowed`, the second time assertion is `TimeChoice.AllDay` and the last line becomes `assertThat(form.draft(createdBy = null).start).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 10, 25)))`, its comment "The draft depends only on the chosen date: the 25th, the clock-change day."

Add:
```kotlin
    /** 5 §A.4: Repeats is Never by default, and only a new event has it. */
    @Test
    fun onlyANewEventRepeatsAndNeverIsTheDefault() {
        val form = new()
        assertThat(form.canRepeat).isTrue()
        assertThat(form.repeat).isEqualTo(RepeatChoice.Never)
        assertThat(form.draft(createdBy = null).recurrence).isNull()
        val edit = editTimed(today.atTime(19, 30), 90)
        assertThat(edit.canRepeat).isFalse()
        edit.chooseRepeat(RepeatChoice.Weekly)
        assertThat(edit.repeat).isEqualTo(RepeatChoice.Never)
    }

    @Test
    fun aRepeatingDraftCarriesItsRuleAndTheHouseholdZone() {
        val form = new()
        form.updateTitle("Swimming")
        form.chooseTime(TimeChoice.Custom(LocalTime.of(18, 0)))
        form.chooseRepeat(RepeatChoice.Fortnightly)
        form.chooseEnd(LocalDate.of(2026, 12, 31))
        assertThat(form.draft(createdBy = null).recurrence)
            .isEqualTo(Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=20261231T235959Z", london))
        form.chooseTime(TimeChoice.AllDay)
        assertThat(form.draft(createdBy = null).recurrence?.rule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=20261231")
    }

    /** Ends only matters while it repeats: back to Never, the end date goes. */
    @Test
    fun choosingNeverDropsTheEndDate() {
        val form = new()
        form.chooseRepeat(RepeatChoice.Weekly)
        form.chooseEnd(today.plusDays(30))
        form.chooseRepeat(RepeatChoice.Never)
        assertThat(form.endsOn).isNull()
    }

    /** Ruling 19: an end before the event's day can't be saved, however it came about. */
    @Test
    fun anEndDateBeforeTheEventsDayIsRefused() {
        val form = new()
        form.updateTitle("Swimming")
        form.chooseRepeat(RepeatChoice.Weekly)
        form.chooseEnd(today.minusDays(1))
        assertThat(form.endsBeforeStart).isTrue()
        assertThat(form.canSave).isFalse()
        form.chooseEnd(today)
        assertThat(form.canSave).isTrue()
        form.chooseDay(today.plusDays(1))
        assertThat(form.endsBeforeStart).isTrue()
        assertThat(form.canSave).isFalse()
    }

    @Test
    fun aRepeatOrAnEndMakesTheSheetEdited() {
        val form = new()
        form.chooseRepeat(RepeatChoice.Daily)
        assertThat(form.edited).isTrue()
        form.chooseRepeat(RepeatChoice.Never)
        assertThat(form.edited).isFalse()
    }
```
Fails today: `TimeChoice.AllDay` isn't the default before 18:00; `pickerTime` gives the slot's time; `canRepeat`, `repeat`, `chooseRepeat`, `chooseEnd`, `endsOn`, `endsBeforeStart` don't exist.

- [ ] **Step 2: Rewrite the sheet's tests**

In `EventEditorSheetTest.kt`: remove the `TimeSlot` import; delete `aPickedSlotTimeSelectsTheSlotChip`. `chipsChangeTheFormAndTheSummary` begins, in place of its `day_…` and `time_Afternoon` clicks:
```kotlin
        // Today, the picker opens on 12:00 for a sheet opened at 11:54; two hours up is 14:00.
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        compose.onNodeWithTag("day_${SampleUi.TODAY.plusDays(1)}").performClick()
```
and its `who_Mia` click and both summaries stay as they are. In `pickTimeSetsACustomTime`, the picker now opens on `"12"`, and the result is `TimeChoice.Custom(LocalTime.of(13, 15))` shown as `"13:15"`. Replace `lengthIsHiddenForAllDay` with:
```kotlin
    @Test
    fun lengthShowsOnlyForAPickedTime() {
        show(newForm())
        compose.onNodeWithTag("length_60").assertDoesNotExist()
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        compose.onNodeWithTag("length_60").assertExists()
        compose.onNodeWithTag("time_all_day").performClick()
        compose.onNodeWithTag("length_60").assertDoesNotExist()
    }
```
In `openingAPickerClosesTheKeyboardButAChipDoesNot`, `time_Evening` becomes `"day_${SampleUi.TODAY.plusDays(1)}"`. Add:
```kotlin
    /** 5 §A.4: All day and Pick time… only; Repeats on a new event, Ends only once it repeats. */
    @Test
    fun aNewEventOffersRepeatsAndEndsOnlyWhenItRepeats() {
        show(newForm())
        compose.onNodeWithTag("time_all_day").assertIsSelected()
        compose.onNodeWithTag("time_Morning").assertDoesNotExist()
        compose.onNodeWithTag("repeat_Never").assertIsSelected()
        compose.onNodeWithTag("ends_never").assertDoesNotExist()
        compose.onNodeWithTag("repeat_Weekly").performClick()
        compose.onNodeWithTag("ends_never").assertIsSelected()
        compose.onNodeWithTag("ends_pick").performClick()
        assertThat(calls).contains("picker:EndDate")
    }

    @Test
    fun anEditHasNoRepeats() {
        show(dinner())
        compose.onNodeWithTag("repeat_Never").assertDoesNotExist()
    }

    @Test
    fun anEndBeforeTheDaySaysSoAndSaveWaits() {
        val form = newForm()
        form.updateTitle("Swimming")
        form.chooseRepeat(RepeatChoice.Weekly)
        form.chooseEnd(SampleUi.TODAY.minusDays(1))
        show(form)
        compose.onNodeWithTag("ends_error").assertTextEquals("The end date is before the event’s day. Pick a later one.")
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
    }
```
In `EditorScreenshotTest.kt`: remove the `TimeSlot` import; `TimeChoice.Slot(TimeSlot.Evening)` becomes `TimeChoice.Custom(LocalTime.of(18, 0))` (three places) and `TimeChoice.Slot(TimeSlot.Morning)` becomes `TimeChoice.Custom(LocalTime.of(9, 0))`. Add:
```kotlin
    private fun repeats() = newForm().apply {
        updateTitle("Swimming")
        chooseTime(TimeChoice.Custom(LocalTime.of(18, 0)))
        chooseRepeat(RepeatChoice.Weekly)
        chooseEnd(SampleUi.TODAY.plusWeeks(10))
    }

    @Test fun repeatsDark() = snap("editor_repeats_dark", true, repeats())
    @Test fun repeatsLight() = snap("editor_repeats_light", false, repeats())
```
Fails today: the sheet has slot chips and no Repeats or Ends.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventFormTest*' --tests '*EventEditorSheetTest*'`
Expected: FAIL to compile on `chooseRepeat`, `RepeatChoice` in the tests.

- [ ] **Step 4: The form**

`EventForm.kt`:
- Delete `TimeSlot`, `defaultTime`, `TimeChoice.Slot`, and the `timeTouched` field; `TimeChoice`'s KDoc becomes "What the Time chips choose: all day, or a time from Pick time…." The `time` initial value is `original?.let { timeOf(it.start, zone) } ?: TimeChoice.AllDay`; `chooseDay` loses its last line; `chooseTime` loses the slot normalisation:
```kotlin
    /** Re-choosing the time already chosen changes nothing. */
    fun chooseTime(choice: TimeChoice) {
        if (choice == time) return
        time = choice
        whenTouched = true
    }
```
- `startTime` loses its `Slot` branch; `timeOf` becomes `is EventTime.Timed -> TimeChoice.Custom(start.instant.atZone(zone).toLocalTime())`.
- `pickerTime`:
```kotlin
    /** Where the time picker opens: the chosen start; with none, the next whole hour today (23:00 at the latest), else 09:00. */
    val pickerTime: LocalTime
        get() = startTime ?: if (day != today) MORNING else if (now.hour >= LAST_HOUR) LocalTime.of(LAST_HOUR, 0) else LocalTime.of(now.hour + 1, 0)
```
with `private val MORNING: LocalTime = LocalTime.of(9, 0)` and `private const val LAST_HOUR = 23` beside `DAYS_SHOWN`.
- After `length`:
```kotlin
    /** Only a new event repeats here; a series' time or title is changed on a phone (5 §A.4). */
    val canRepeat: Boolean = mode == Mode.New

    var repeat: RepeatChoice by mutableStateOf(RepeatChoice.Never)
        private set

    /** The day the series ends, or null for Never. */
    var endsOn: LocalDate? by mutableStateOf(null)
        private set

    /** The end is before the event's day, so Save waits (ruling 19). */
    val endsBeforeStart: Boolean get() = endsOn?.let { it < day } ?: false
```
- `Answers` gains `val repeat: RepeatChoice, val endsOn: LocalDate?`, and both `Answers(…)` calls pass `repeat, endsOn`.
- `canSave` becomes `title.trim().isNotEmpty() && !endsBeforeStart`.
- After `chooseLength`:
```kotlin
    /** Back to Never drops the end date: Ends only shows while the event repeats. */
    fun chooseRepeat(choice: RepeatChoice) {
        if (!canRepeat || choice == repeat) return
        repeat = choice
        if (choice == RepeatChoice.Never) endsOn = null
    }

    fun chooseEnd(date: LocalDate?) {
        if (canRepeat) endsOn = date
    }
```
- In `draft`, the two new-event `EventDraft(…)` returns gain `recurrence = recurrence(allDay = true)` and `recurrence = recurrence(allDay = false)`, with
```kotlin
    private fun recurrence(allDay: Boolean): Recurrence? =
        recurrenceRule(repeat, endsOn, allDay, zone)?.let { Recurrence(it, zone) }
```
and the draft's KDoc gains "A new event that repeats carries its rule (5 §A.4)."

- [ ] **Step 5: The sheet**

`Pickers.kt:67`: `enum class EditorPicker { None, Date, Time, EndDate }`.
`EventEditorSheet.kt`: remove the `TimeSlot` import; import `RepeatChoice`, `SHORT_DAY`, `HhMessageLine`. Add at the top, after `SaveFailure`:
```kotlin
internal const val REPEATS = "Repeats"
internal const val ENDS = "Ends"
internal const val ENDS_TOO_EARLY = "The end date is before the event’s day. Pick a later one."
```
`TimeSection` keeps the All day chip and the Pick time… chip; the `TimeSlot.entries.forEach` block goes. After `if (form.time != TimeChoice.AllDay) LengthSection(form, busy)`:
```kotlin
                        if (form.canRepeat) {
                            RepeatsSection(form, busy)
                            if (form.repeat != RepeatChoice.Never) {
                                EndsSection(form, busy) {
                                    closeKeyboard()
                                    onPicker(EditorPicker.EndDate)
                                }
                            }
                        }
```
In the `when (picker)`:
```kotlin
            EditorPicker.EndDate -> PickerLayer(onDismiss = { onPicker(EditorPicker.None) }) {
                DatePickerCard(
                    today = form.today,
                    selected = form.endsOn ?: form.day,
                    onPick = {
                        form.chooseEnd(it)
                        onPicker(EditorPicker.None)
                    },
                    onCancel = { onPicker(EditorPicker.None) },
                )
            }
```
After `LengthSection`:
```kotlin
@Composable
private fun RepeatsSection(form: EventForm, busy: Boolean) {
    Section(REPEATS) {
        RepeatChoice.entries.forEach { choice ->
            HhChoiceChip(choice.label, form.repeat == choice, "repeat_${choice.name}", onClick = { if (!busy) form.chooseRepeat(choice) })
        }
    }
}

/** Never, or a date from the date picker; a line under them while the date is before the event's day (ruling 19). */
@Composable
private fun EndsSection(form: EventForm, busy: Boolean, onPickEnd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.sectionGap)) {
        Section(ENDS) {
            HhChoiceChip("Never", form.endsOn == null, "ends_never", onClick = { if (!busy) form.chooseEnd(null) })
            val end = form.endsOn
            HhChoiceChip(
                label = end?.format(SHORT_DAY) ?: "On a date…",
                selected = end != null,
                tag = "ends_pick",
                role = Role.Button,
                leading = { ink -> HhIcon(Icons.CALENDAR_MONTH, size = ControlTokens.chipIcon, tint = ink) },
                onClick = { if (!busy) onPickEnd() },
            )
        }
        HhMessageLine(ENDS_TOO_EARLY.takeIf { form.endsBeforeStart }, CalendarType.noteBody, Culvery.colors.danger, "ends_error")
    }
}
```

- [ ] **Step 6: Run the calendar's tests**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS. `EventEditorHostTest` and `SampleAddTest` (`:app` testDebug) only use All day, Pick time and Save; if one clicked a slot chip, give it Pick time and the picker's Set instead, and name it in the report.

- [ ] **Step 7: Screenshots**

`./gradlew :capability:calendar:compareRoborazziDebug --tests '*EditorScreenshotTest*'`: in each of the 16 changed names the Morning, Afternoon and Evening chips are gone; in the new-event ones (`keyboard`, `empty`, `child`, `failed`, `date_picker`, `time_picker`) a Repeats row with Never selected follows. Record, put the 16 changed and 2 new names in `$TMP/expected-baselines.txt`, run the baseline check, open `editor_repeats_dark.png` and `editor_empty_light.png`.

- [ ] **Step 8: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/calendar/src app/src/testDebug
git commit -m "Add events that repeat from the tablet: All day or Pick time, Repeats and Ends"
```

---
### Task 6: Rail badges (§3.3, §3.4, §5.3)

**Review:** sonnet.

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/RailBadge.kt`
- Modify: `core/plugin/.../Capability.kt` (`badge`)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellUiState.kt:7` (`TabItem.badge`), `shell/ShellViewModel.kt:86-100` (the tabs read the badge), `shell/ui/NavRail.kt:86-110` (draw and speak it)
- Test: `app/src/test/.../shell/Fakes.kt` (`FakeCapability.badgeFlow`), `shell/ShellViewModelTest.kt`, `shell/ui/NavRailTest.kt`, `shell/ui/ShellScreenshotTest.kt`
- Baselines (new): `home_rail_badge_dark`. None change.

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `sealed interface RailBadge { val spoken: String; data class Count(val count: Int, override val spoken: String); data class Dot(override val spoken: String) }` in `uk.co.siland.culvery.core.plugin`.
  - `Capability.badge: Flow<RailBadge?>` (default `flowOf(null)`).
  - `TabItem(id, label, icon, badge: RailBadge? = null)`; the rail's badge has test tag `rail_<id>_badge`; a badged tab's content description is `"<label>, <spoken>"`.

- [ ] **Step 1: Write the failing tests**

`Fakes.kt`, `FakeCapability` gains:
```kotlin
    val badgeFlow = MutableStateFlow<RailBadge?>(null)
    override val badge: Flow<RailBadge?> = badgeFlow
```
`ShellViewModelTest.kt`:
```kotlin
    /** 5 §3.3: any capability can mark its tab; the shell passes the mark to the rail as it changes. */
    @Test
    fun aTabCarriesItsCapabilitysBadge() = runTest {
        val door = FakeCapability("door", order = 50, shown = true)
        val vm = vm(setOf(door))
        vm.uiState.test {
            assertThat(expectMostRecentItem().tabs.single().badge).isNull()
            door.badgeFlow.value = RailBadge.Count(2, "2 unseen rings")
            assertThat(awaitItem().tabs.single().badge).isEqualTo(RailBadge.Count(2, "2 unseen rings"))
            door.badgeFlow.value = RailBadge.Dot("new motion")
            assertThat(awaitItem().tabs.single().badge).isEqualTo(RailBadge.Dot("new motion"))
        }
    }
```
`NavRailTest.kt` (imports `androidx.compose.ui.test.assertTextEquals`, `androidx.compose.ui.test.hasContentDescription`, `uk.co.siland.culvery.core.plugin.RailBadge`):
```kotlin
    /** 5 §5.3: a count or a dot on the icon, and its words after the tab's name. */
    @Test
    fun aBadgeIsDrawnAndAddsItsWordsToTheTabsName() {
        compose.setContent {
            CulveryTheme(dark = true) {
                NavRail(
                    listOf(
                        TabItem("door", "Door", Icons.CALENDAR_MONTH, RailBadge.Count(2, "2 unseen rings")),
                        TabItem("lights", "Lights", Icons.HOME, RailBadge.Dot("new motion")),
                        TabItem("music", "Music", Icons.HOME, RailBadge.Count(12, "12 unseen rings")),
                    ),
                    HOME_TAB_ID, onSelect = {}, onOpenSettings = {},
                )
            }
        }
        compose.onNodeWithTag("rail_door").assert(hasContentDescription("Door, 2 unseen rings"))
        compose.onNodeWithTag("rail_door_badge", useUnmergedTree = true).assertTextEquals("2")
        compose.onNodeWithTag("rail_lights").assert(hasContentDescription("Lights, new motion"))
        compose.onNodeWithTag("rail_lights_badge", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("rail_music_badge", useUnmergedTree = true).assertTextEquals("9+")
        compose.onNodeWithTag("rail_home").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.assertAccessible()
    }
```
`ShellScreenshotTest.kt`:
```kotlin
    @Test
    fun homeRailBadgeDark() = snap(
        "home_rail_badge_dark", dark = true,
        state = calendarHome(dark = true).copy(tabs = listOf(TabItem("calendar", "Calendar", "calendar_month", RailBadge.Count(2, "2 unseen rings")))),
    )
```
Fails today: `RailBadge`, `Capability.badge` and `TabItem.badge` don't exist.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShellViewModelTest*' --tests '*NavRailTest*'`
Expected: FAIL to compile on `RailBadge`.

- [ ] **Step 3: The seam**

Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/RailBadge.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

/** A mark on a capability's rail tab (5 §3.3): a count bubble or a dot, and the words TalkBack says after the tab's name. */
sealed interface RailBadge {
    /** Said after the tab's name: "Door, 2 unseen rings". */
    val spoken: String

    data class Count(val count: Int, override val spoken: String) : RailBadge

    data class Dot(override val spoken: String) : RailBadge
}
```
`Capability.kt`, after `headerItems()`:
```kotlin
    /** The mark on this capability's rail tab (5 §3.3); none by default. */
    val badge: Flow<RailBadge?> get() = flowOf(null)
```

- [ ] **Step 4: The shell**

`ShellUiState.kt`: `data class TabItem(val id: String, val label: String, val icon: String, val badge: RailBadge? = null)`.
`ShellViewModel.kt`, the `tabs` combine's per-capability flow becomes:
```kotlin
                ordered.map { cap ->
                    combine(
                        // onStart after the retry: a retry must not hide a tab that was showing.
                        cap.hasTab.retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read whether it has a tab (${it::class.simpleName}); retrying") }
                            .onStart { emit(false) },
                        cap.badge.retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its badge (${it::class.simpleName}); retrying") }
                            .onStart { emit(null) },
                    ) { shown, badge -> if (shown) TabItem(cap.id, cap.label, cap.icon, badge) else null }
                },
```
`NavRail.kt` (imports `androidx.compose.foundation.layout.defaultMinSize`, `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`, `uk.co.siland.culvery.core.plugin.RailBadge`), after `MARK_GAP`:
```kotlin
// 5 §5.3: a 20 dp count bubble or a 10 dp dot at the icon's top end, in `danger`; past 9, "9+".
private val BADGE_SIZE = 20.dp
private val BADGE_DOT = 10.dp
private val BADGE_PADDING_H = 5.dp
private const val BADGE_MAX = 9
```
In `RailItem`, the modifier gains, just before `.selectable(…)`:
```kotlin
            .then(tab.badge?.let { badge -> Modifier.semantics { contentDescription = "${tab.label}, ${badge.spoken}" } } ?: Modifier)
```
and the icon `Box` is wrapped so the badge can sit over its clipped corner:
```kotlin
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 64.dp, height = 38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (selected) c.accentSoft else Color.Transparent),
            ) {
                HhIcon(tab.icon, size = 26.dp, filled = selected, tint = if (selected) c.ink else c.mute)
            }
            tab.badge?.let { RailBadgeMark(it, Modifier.align(Alignment.TopEnd).testTag("rail_${tab.id}_badge")) }
        }
```
At the end of the file:
```kotlin
@Composable
private fun RailBadgeMark(badge: RailBadge, modifier: Modifier) {
    val c = Culvery.colors
    when (badge) {
        is RailBadge.Count -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .defaultMinSize(BADGE_SIZE, BADGE_SIZE)
                .clip(CircleShape)
                .background(c.danger)
                .padding(horizontal = BADGE_PADDING_H),
        ) {
            Text(if (badge.count > BADGE_MAX) "$BADGE_MAX+" else badge.count.toString(), style = HhType.labelSmall, color = c.dangerInk)
        }
        is RailBadge.Dot -> Box(modifier.size(BADGE_DOT).clip(CircleShape).background(c.danger))
    }
}
```

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Screenshot**

`./gradlew :app:recordRoborazziDebug --tests '*ShellScreenshotTest*'`; `echo home_rail_badge_dark > "$TMP/expected-baselines.txt"`; the baseline check; open it: a red "2" bubble on the Calendar tab's icon, top right.

- [ ] **Step 7: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add core/plugin/src/main app/src
git commit -m "Let a capability mark its rail tab with a count or a dot"
```

---

### Task 7: The alert layer (§2.10, §3.3, §3.4, §5.1; rulings 1–4)

**Review:** opus.

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Alerts.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellOverlays.kt` (`ShellAlerts`)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt:107-158` (`ShellLayers(alert = …)`, `AlertLayer`)
- Modify: `app/src/main/java/uk/co/siland/culvery/MainActivity.kt` (inject, provide, `inFront`), `di/AppModule.kt` (bind)
- Create: `app/src/test/java/uk/co/siland/culvery/shell/ui/AlertLayerTest.kt`, `app/src/test/java/uk/co/siland/culvery/shell/ShellAlertsTest.kt`
- Test (must still pass unchanged): `ShellLayersTimeoutTest`, `OverlayLayersTest`, `ModalLayersTest`, `AppContentTest`.

**Interfaces:**
- Consumes: `UserActivity`, `OverlayState` (unchanged).
- Produces:
  - `interface AlertHost { val inFront: StateFlow<Boolean>; fun show(content: @Composable () -> Unit); fun dismiss() }` and `val LocalAlertHost` in `uk.co.siland.culvery.core.plugin`.
  - `@Singleton class ShellAlerts @Inject constructor() : AlertHost` with `val content: StateFlow<(@Composable () -> Unit)?>` and `fun setInFront(inFront: Boolean)`.
  - `ShellLayers(…, alert: (@Composable () -> Unit)? = null, shell)`; the layer has test tag `alert_layer`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/uk/co/siland/culvery/shell/ShellAlertsTest.kt`:
```kotlin
package uk.co.siland.culvery.shell

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShellAlertsTest {
    @Test
    fun anAlertReplacesTheLastAndDismissClearsIt() {
        val alerts = ShellAlerts()
        val first: @androidx.compose.runtime.Composable () -> Unit = {}
        val second: @androidx.compose.runtime.Composable () -> Unit = {}
        alerts.show(first)
        alerts.show(second)
        assertThat(alerts.content.value).isSameInstanceAs(second)
        alerts.dismiss()
        assertThat(alerts.content.value).isNull()
    }

    @Test
    fun inFrontFollowsTheActivity() {
        val alerts = ShellAlerts()
        assertThat(alerts.inFront.value).isFalse()
        alerts.setInFront(true)
        assertThat(alerts.inFront.value).isTrue()
    }
}
```
Create `app/src/test/java/uk/co/siland/culvery/shell/ui/AlertLayerTest.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.OVERLAY_IDLE_MS
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.OverlayState

/** 5 §2.10, §5.1: the alert layer over a sheet, under the PIN pad, and its touches its own. */
@RunWith(AndroidJUnit4::class)
class AlertLayerTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()
    private val activity = FakeUserActivity()
    private var alert by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var pinShowing by mutableStateOf(false)
    private var padTaps = 0

    // Swallows its taps, as the door card does.
    private val card: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(200.dp).testTag("card").pointerInput(Unit) { detectTapGestures {} })
    }
    private val sheet: @Composable () -> Unit = { Box(Modifier.fillMaxSize().testTag("sheet")) }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                ShellLayers(
                    overlay = overlay,
                    toast = null,
                    onToastHidden = {},
                    pinShowing = pinShowing,
                    pinPad = { if (pinShowing) Box(Modifier.fillMaxSize().testTag("pad").pointerInput(Unit) { detectTapGestures { padTaps++ } }) },
                    activity = activity,
                    onTouch = activity::touch,
                    alert = alert,
                ) { Text("Home") }
            }
        }
    }

    private fun settle() {
        repeat(2) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    private fun after(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.mainClock.advanceTimeByFrame()
    }

    private fun open(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** The card isn't a touch for the sheet beneath: the sheet's 2 minutes run on through a tap on the card. */
    @Test
    fun aTapOnTheAlertIsNotATouchAndTheSheetBeneathClosesOnItsOwnTime() {
        show()
        overlay.show(content = sheet)
        alert = card
        settle()
        after(OVERLAY_IDLE_MS - 2_000)
        compose.onNodeWithTag("card").performClick()
        settle()
        assertThat(activity.touches.value).isEqualTo(0L)
        after(1_000)
        assertThat(open("sheet")).isTrue()
        after(1_500)
        assertThat(open("sheet")).isFalse()
    }

    /** It never replaces or closes the sheet, so unsaved edits survive a ring. */
    @Test
    fun theAlertLeavesAnEditedSheetAndItsEditsAlone() {
        show()
        overlay.show(content = sheet)
        overlay.markEdited(true)
        settle()
        alert = card
        settle()
        assertThat(open("card")).isTrue()
        alert = null
        settle()
        assertThat(overlay.content).isSameInstanceAs(sheet)
        assertThat(overlay.edited).isTrue()
        assertThat(open("sheet")).isTrue()
    }

    /** Over a sheet the card is still read: only the shell beneath the sheet leaves TalkBack's tree. */
    @Test
    fun theAlertIsReadOverASheet() {
        show()
        overlay.show(content = sheet)
        alert = card
        settle()
        assertThat(open("card")).isTrue()
    }

    /** The PIN pad draws above the card, takes the taps where the card is, and hides it from TalkBack. */
    @Test
    fun thePinPadDrawsAboveTheAlert() {
        show()
        alert = card
        pinShowing = true
        settle()
        assertThat(open("card")).isFalse()
        compose.onNodeWithTag("pad").performTouchInput { click(Offset(centerX, 60f)) }
        settle()
        assertThat(padTaps).isEqualTo(1)
        // The pad's touches still count, as before.
        assertThat(activity.touches.value).isEqualTo(1L)
    }
}
```
Fails today: `ShellAlerts`, `AlertHost` and `ShellLayers`' `alert` parameter don't exist.
Fails if: the alert is drawn inside the touch-counting box (the first test's touch count becomes 1, and the sheet lives 2 more minutes); it is drawn above the pad; the pad's box stops counting; `ShellAlerts.show` keeps the old content.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*AlertLayerTest*' --tests '*ShellAlertsTest*'`
Expected: FAIL to compile on `ShellAlerts` and `alert =`.

- [ ] **Step 3: The seam**

Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Alerts.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * The alert layer (5 §3.3, §5.1): above sheets, Settings and the wizard, below the PIN pad. Unlike [OverlayHost.show], an
 * alert never replaces or closes the open sheet, and touching it is not a touch for the screens beneath, whose own
 * timeouts run on. Safe from any thread.
 */
interface AlertHost {
    /** True while Culvery's screen is resumed in front: an alert raised otherwise would never be seen (5 §2.7). */
    val inFront: StateFlow<Boolean>

    /** Shows [content] on the alert layer, replacing any alert already there. */
    fun show(content: @Composable () -> Unit)

    fun dismiss()
}

val LocalAlertHost = staticCompositionLocalOf<AlertHost> {
    error("LocalAlertHost not provided: wrap the content in CompositionLocalProvider(LocalAlertHost provides …)")
}
```

- [ ] **Step 4: The shell's host and layer**

`ShellOverlays.kt` (imports `uk.co.siland.culvery.core.plugin.AlertHost`):
```kotlin
/** The app's [AlertHost]: the content the alert layer draws, and whether MainActivity is resumed (ruling 1). */
@Singleton
class ShellAlerts @Inject constructor() : AlertHost {
    private val _content = MutableStateFlow<(@Composable () -> Unit)?>(null)
    val content: StateFlow<(@Composable () -> Unit)?> = _content.asStateFlow()

    private val _inFront = MutableStateFlow(false)
    override val inFront: StateFlow<Boolean> = _inFront.asStateFlow()

    override fun show(content: @Composable () -> Unit) {
        _content.value = content
    }

    override fun dismiss() {
        _content.value = null
    }

    fun setInFront(inFront: Boolean) {
        _inFront.value = inFront
    }
}
```
`OverlayLayers.kt`: `ShellLayers` gains the parameter `alert: (@Composable () -> Unit)? = null,` before `shell`; its KDoc's first sentence becomes "The shell with its overlay layers stacked over it: sheet, then [alert], then [pinPad], then toasts over everything." and "Every finger down anywhere in them, sheets and PIN pads included, goes to [onTouch]" becomes "Every finger down on the shell, a sheet or the PIN pad goes to [onTouch] before anything handles it; one on the alert doesn't (5 §5.1)". Its body becomes:
```kotlin
    Box(Modifier.fillMaxSize().graphicsLayer {}) {
        Box(Modifier.fillMaxSize().countsTouches { touched() }) {
            // A TalkBack double-tap is an action, not a touch, so a scrim can't stop it: what is covered leaves the tree.
            Box(Modifier.fillMaxSize().then(if (overlay.isShowing || pinShowing) Modifier.clearAndSetSemantics {} else Modifier)) { shell() }
            OverlayLayer(overlay, hidden = pinShowing)
        }
        // Outside the counting box: a ring's card is not someone using the sheet or Settings beneath.
        if (alert != null) AlertLayer(alert, hidden = pinShowing)
        Box(Modifier.fillMaxSize().then(if (pinShowing) Modifier.countsTouches { touched() } else Modifier)) { pinPad() }
        ToastLayer(toast, activity, onHidden = onToastHidden)
        // One warning at a time, and Settings' first: Settings closing takes any open sheet with it.
        when {
            settingsClosingSoon -> ClosingWarningLayer(SETTINGS_CLOSING, "settings_warning")
            sheetClosingSoon -> ClosingWarningLayer(SHEET_CLOSING, "sheet_warning")
        }
    }
```
and add:
```kotlin
/** Every finger down here goes to [touched] before anything handles it. */
private fun Modifier.countsTouches(touched: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        touched()
    }
}

// 5 §5.1: top centre, about half the screen wide, 24 below the top.
private val ALERT_TOP = 24.dp
private const val ALERT_WIDTH = 0.5f

/** The alert layer: no scrim, so the screen around the alert stays usable; the alert swallows its own taps. */
@Composable
internal fun AlertLayer(content: @Composable () -> Unit, hidden: Boolean) {
    Box(
        Modifier.fillMaxSize().then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier).padding(top = ALERT_TOP),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.testTag("alert_layer").fillMaxWidth(ALERT_WIDTH)) { content() }
    }
}
```
(imports `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.ui.unit.dp`). A finger on a toast reaches whatever is beneath it, as before for the shell and now also for the card.

- [ ] **Step 5: Wire it**

`AppModule.kt`: `@Binds abstract fun alertHost(impl: ShellAlerts): AlertHost` (imports `ShellAlerts`, `AlertHost`).
`MainActivity.kt`: `@Inject lateinit var alerts: ShellAlerts`; in `setContent`, `val alert by alerts.content.collectAsStateWithLifecycle()`; `LocalAlertHost provides alerts` in the `CompositionLocalProvider`; `alert = alert,` in `ShellLayers(…)`. `onResume` gains `alerts.setInFront(true)` after `homeApp.refresh()`, and add
```kotlin
    override fun onPause() {
        alerts.setInFront(false)
        super.onPause()
    }
```
(An instance that hands over in `onCreate` returns before injection is read again; `alerts` is injected by Hilt in `super.onCreate`, so both overrides are safe.)

- [ ] **Step 6: Run them to see them pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, the existing `ShellLayersTimeoutTest`, `OverlayLayersTest`, `ModalLayersTest`, `ShellLayersRedrawTest` and `AppContentTest` unchanged. If a toast test now fails because a tap on a toast no longer counts, stop and report.

- [ ] **Step 7: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL, no image changed.
```bash
git add core/plugin/src/main app/src
git commit -m "Add an alert layer between the sheets and the PIN pad"
```

---

### Task 8: `openSystemScreen` (§3.4; rulings 22, 23)

**Review:** opus.

**Files:**
- Modify: `app/src/main/java/uk/co/siland/culvery/KioskLifecycle.kt:376-391` (rename, KDoc, log)
- Modify: `app/src/main/java/uk/co/siland/culvery/MainActivity.kt:88-95, 230-239` (navigator, the home-app requests)
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/ShellNavigator.kt` (`openSystemScreen`)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt:200-203`
- Modify (fakes): `app/src/test/.../shell/ui/NoNavigation.kt`, `capability/calendar/src/test/.../ui/RecordingNavigator.kt`, `capability/weather/src/test/.../ui/RecordingNavigator.kt`, `provider/calendar-google/src/test/.../RecordingNavigator.kt`, `core/setup/src/test/.../TestUi.kt` (its `ShellNavigator`)
- Test: `app/src/test/java/uk/co/siland/culvery/KioskLifecycleTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `KioskLifecycle.openSystemScreen(missing: String, launch: () -> Unit)`; `ShellNavigator.openSystemScreen(intent: Intent, missing: String)`; the calendar's `RecordingNavigator.systemScreens: List<Pair<String?, String>>` (each intent's action and its `missing`).

- [ ] **Step 1: Rename in the tests and write the new ones**

In `KioskLifecycleTest.kt` replace every `kiosk.openHomeAppScreen(` with `kiosk.openSystemScreen(` (the bodies stay as they are, ruling 22), and add:
```kotlin
    /** 5 §3.4: Connect Ring's notification-access step takes the home-app screens' path. */
    @Test
    fun whenNoNotificationAccessScreenOpensItPinsAgainAndSaysSo() {
        window.calls.clear()
        kiosk.openSystemScreen("Culvery can’t open notification access.") { throw ActivityNotFoundException() }
        assertThat(window.calls).containsExactly("unpin", "pin").inOrder()
        assertThat(said).containsExactly("Culvery can’t open notification access.")
    }

    /** The screen opened: Culvery pins again when it is back in front, not before. */
    @Test
    fun aSystemScreenThatOpensIsPinnedAgainOnReturn() {
        window.calls.clear()
        kiosk.openSystemScreen("Culvery can’t open notification access.") { }
        assertThat(window.calls).containsExactly("unpin")
        controller.pause().resume()
        assertThat(window.calls.last()).isEqualTo("pin")
        assertThat(said).isEmpty()
    }
```
Fails today: `openSystemScreen` doesn't exist.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*KioskLifecycleTest*'`
Expected: FAIL to compile on `openSystemScreen`.

- [ ] **Step 3: The kiosk and the navigator**

`KioskLifecycle.kt`, `openHomeAppScreen` becomes:
```kotlin
    /**
     * Android's screens can't open over a pinned app (5 §3.4): unpin, then [launch] one; the next resume pins again. When
     * there is none, pin again at once (nothing pauses Culvery, so nothing else would) and say [missing].
     */
    fun openSystemScreen(missing: String, launch: () -> Unit) {
        if (handedOver) return
        window.unpin()
        try {
            launch()
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No system screen to open on this tablet (${e::class.simpleName})")
            returnedToFront()
            if (shouldPin(setupComplete(), kioskExited())) window.pin()
            say(missing)
        }
    }
```
`ShellNavigator.kt` (import `android.content.Intent`), after `returnToPinning`:
```kotlin
    /**
     * Opens one of Android's screens, such as notification access (5 §3.4): unpins, opens [intent], and pins again when
     * Culvery is back in front; when the tablet has no such screen, pins again at once and says [missing].
     */
    fun openSystemScreen(intent: Intent, missing: String)
```
`ShellViewModel.kt`, after `returnToPinning`:
```kotlin
    /** The view model has no window: MainActivity's navigator opens the screen. */
    override fun openSystemScreen(intent: Intent, missing: String) = Unit
```
`MainActivity.kt`: the navigator object gains
```kotlin
            override fun openSystemScreen(intent: Intent, missing: String) = kiosk.openSystemScreen(missing) { startActivity(intent) }
```
and `openHomeAppScreen`'s two `kiosk.openHomeAppScreen(` calls become `kiosk.openSystemScreen(`.
The fakes: `NoNavigation`, the weather's and the Google provider's `RecordingNavigator`, and `core/setup`'s `TestUi.kt` navigator gain `override fun openSystemScreen(intent: Intent, missing: String) = Unit`. The calendar's `RecordingNavigator` gains
```kotlin
    val systemScreens = mutableListOf<Pair<String?, String>>()

    override fun openSystemScreen(intent: Intent, missing: String) {
        systemScreens += intent.action to missing
    }
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew --continue :app:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest :capability:weather:testDebugUnitTest :provider:calendar-google:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add app/src core/plugin/src/main core/setup/src/test capability/calendar/src/test capability/weather/src/test provider/calendar-google/src/test
git commit -m "Open any of Android's screens from the kiosk, and from a capability through the navigator"
```

---
### Task 9: `:capability:door`: the module, its data and its settings (§3.1, §3.5, §4.3–4.5, §4.7–4.8; rulings 8, 9, 14, 24, 31)

**Review:** opus.

**Files:**
- Modify: `settings.gradle.kts` (`include(":capability:door")`), `app/build.gradle.kts` (the module, by Hilt alone)
- Create: `capability/door/build.gradle.kts`, `capability/door/src/test/resources/robolectric.properties`
- Create (package `uk.co.siland.culvery.capability.door`, under `capability/door/src/main/java/uk/co/siland/culvery/capability/door/`): `DoorContract.kt`, `db/DoorDatabase.kt`, `DoorStore.kt`, `DoorSettings.kt`, `DoorWords.kt`, `DoorCapability.kt`, `DoorPruneLoop.kt`, `di/DoorModule.kt`
- Create: `capability/door/schemas/uk.co.siland.culvery.capability.door.db.DoorDatabase/1.json` (generated by the build; commit it)
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Icons.kt`, `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (regenerated); maybe `core/ui/src/test/.../IconFontTest.kt` (`NO_FILLED_FORM`)
- Test: `capability/door/src/test/java/uk/co/siland/culvery/capability/door/` `DoorStoreTest.kt`, `BitmapSnapshotTest.kt`, `DoorSettingsTest.kt`, `DoorCapabilityTest.kt`, `DoorPruneLoopTest.kt`, `TestDoor.kt`; `build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt`; `app/src/test/java/uk/co/siland/culvery/AppSourceTest.kt`

**Interfaces:**
- Consumes: `Capability`, `RailBadge` (Task 6), `ProviderDescriptor`, `Startable`, `WallClock`, `ApplicationScope` (`:core:plugin`); `plural` (`:core:ui`).
- Produces (package `uk.co.siland.culvery.capability.door`):
  - `enum class DoorKind { RING, MOTION }`
  - `data class DoorEvent(val id: Long, val atMillis: Long, val kind: DoorKind, val device: String, val unrecognised: Boolean, val snapshot: File?, val seen: Boolean)`
  - `fun interface Snapshot { fun saveTo(file: File) }`; `class BitmapSnapshot(bitmap: Bitmap) : Snapshot`; `const val MAX_SNAPSHOT_WIDTH_PX = 640`
  - `data class DoorNotice(val key: String, val atMillis: Long, val kind: DoorKind, val device: String, val unrecognised: Boolean = false, val snapshot: Snapshot? = null)`
  - `interface DoorEventSink { fun heard(atMillis: Long); fun post(notice: DoorNotice); fun listening(bound: Boolean); val capturing: Boolean }`
  - `interface DoorProvider { val descriptor: ProviderDescriptor; fun appInstalled(): Boolean; fun accessAllowed(): Boolean; fun accessScreen(): Intent }`
  - `data class Recorded(val event: DoorEvent, val isNew: Boolean)`; `data class Unseen(val rings: Int, val motion: Int)`
  - `class DoorStore`: `val events: Flow<List<DoorEvent>>` (newest first), `val unseen: Flow<Unseen>`, `suspend fun record(notice: DoorNotice): Recorded`, `suspend fun prune(nowMillis: Long)`, `suspend fun markAllSeen()`, `suspend fun markSeen(id: Long)`; `const val MERGE_MS = 120_000L`, `const val KEEP_MS = 172_800_000L`
  - `data class DoorPrefs(connected, volume, quietOn, quietFrom, quietUntil, captureUntilMillis, lastHeardMillis, unrecognisedRun)`; `class DoorSettings`: `val prefs: Flow<DoorPrefs>`, `val current: StateFlow<DoorPrefs>`, `suspend fun setConnected(Boolean)`, `setVolume(Float)`, `setQuietHours(on: Boolean, from: LocalTime, until: LocalTime)`, `setCapture(untilMillis: Long)`, `heard(atMillis: Long)`, `countAlert(recognised: Boolean)`; `internal fun doorSettingsStore(scope, produceFile): DataStore<Preferences>`
  - `fun badgeOf(unseen: Unseen): RailBadge?`; `const val DOOR_ID = "door"`
  - `Icons.DOORBELL = "doorbell"`, `Icons.DIRECTIONS_WALK = "directions_walk"`

- [ ] **Step 1: The module and the icons**

`settings.gradle.kts`: `include(":capability:door")` after `include(":capability:weather")`.
Create `capability/door/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
    alias(libs.plugins.roborazzi)
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    // Capability, RailBadge, AlertHost, ProviderDescriptor and Startable appear in this module's public API.
    api(project(":core:plugin"))
    implementation(project(":core:household"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
```
`capability/door/src/test/resources/robolectric.properties`: `qualifiers=w1280dp-h800dp-land-hdpi` (as the weather's).
`app/build.gradle.kts`, after the weather line: 
```kotlin
    // Wired by Hilt alone, as the weather (5 §3).
    implementation(project(":capability:door"))
```
Icons: add `const val DIRECTIONS_WALK = "directions_walk"` and `const val DOORBELL = "doorbell"` to `Icons` (alphabetical), the same two names to `tools/fonts/icons.txt`, then follow `tools/fonts/README.md` steps 3–5 to regenerate the subset. The pre-4c full font is in the repository's history (`git cat-file blob 608ce00:…`); if the subset script says it has no ligature for either name, the README's step 3 needs Google's current font from GitHub: **that is a download — stop and ask the user before fetching it**, naming the file and its source. If `IconFontTest.everyNamedIconKeepsItsFilledForm` says either has no filled form, add it to `NO_FILLED_FORM`.

- [ ] **Step 2: Write the failing tests**

`build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt` (**pin**: the rules go by family, ruling 31):
```kotlin
    @Test
    fun theDoorModulesFollowTheRules() {
        allowed(":capability:door", ":core:plugin", "api")
        allowed(":capability:door", ":core:ui")
        allowed(":provider:door-ring", ":capability:door")
        allowed(":app", ":provider:door-ring")
        banned(":capability:door", ":provider:door-ring")
        banned(":capability:door", ":provider:door-ring", "testImplementation")
        banned(":capability:door", ":capability:calendar")
        banned(":provider:door-ring", ":capability:weather")
        banned(":provider:calendar-google", ":capability:door")
    }
```
`AppSourceTest.kt`: rename the test `theAppsOwnCodeNeverNamesTheWeatherOrDoorCapability` and add `assertThat(files.filter { it.readText().contains("uk.co.siland.culvery.capability.door") }.map { it.path }).isEmpty()` (**pin**).
Create `capability/door/src/test/java/uk/co/siland/culvery/capability/door/TestDoor.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import uk.co.siland.culvery.capability.door.db.DoorDatabase

internal fun doorDb(): DoorDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DoorDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** Wednesday 14 October 2026, 14:02 in London (BST). */
internal const val NOW = 1_791_982_920_000L

internal fun ring(key: String = "k1", at: Long = NOW, device: String = "Front Door", snapshot: Snapshot? = null, unrecognised: Boolean = false) =
    DoorNotice(key, at, DoorKind.RING, device, unrecognised, snapshot)

internal fun motion(key: String = "m1", at: Long = NOW, device: String = "Front Door") = DoorNotice(key, at, DoorKind.MOTION, device)

/** A snapshot that writes a few bytes, as a picture would. */
internal val aPicture = Snapshot { it.writeBytes(byteArrayOf(1, 2, 3)) }

/** A grey picture [width] × [height], for the scaling and the screenshots. */
internal fun picture(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }

internal fun File.names(): List<String> = listFiles()?.map { it.name }?.sorted().orEmpty()
```
(`NOW` is 2026-10-14T13:02:00Z.)

Create `DoorStoreTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.door.db.DoorDatabase

/** 5 §4.3–4.5, §4.7–4.8: merge on key, snapshots, the 48-hour prune and orphans, what is unseen. */
@RunWith(AndroidJUnit4::class)
class DoorStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: DoorDatabase
    private lateinit var dir: File
    private lateinit var store: DoorStore

    @Before
    fun setUp() {
        db = doorDb()
        dir = File(folder.root, "door")
        store = DoorStore(db, dir)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aNewNoticeIsANewUnseenEvent() = runTest {
        val recorded = store.record(ring())
        assertThat(recorded.isNew).isTrue()
        assertThat(recorded.event).isEqualTo(DoorEvent(recorded.event.id, NOW, DoorKind.RING, "Front Door", false, null, seen = false))
        assertThat(store.events.first()).containsExactly(recorded.event)
    }

    /** Ring posts the words first, then the same key again with the snapshot (5 §4.3). */
    @Test
    fun theSameKeyWithinTwoMinutesUpdatesThatEventWithItsPicture() = runTest {
        val first = store.record(ring())
        val again = store.record(ring(at = NOW + MERGE_MS, device = "Front Door Pro", snapshot = aPicture))
        assertThat(again.isNew).isFalse()
        assertThat(again.event.id).isEqualTo(first.event.id)
        assertThat(again.event.device).isEqualTo("Front Door Pro")
        assertThat(again.event.snapshot).isEqualTo(File(dir, "${first.event.id}.jpg"))
        assertThat(store.events.first()).hasSize(1)
    }

    @Test
    fun theSameKeyAfterTwoMinutesIsANewEvent() = runTest {
        store.record(ring())
        assertThat(store.record(ring(at = NOW + MERGE_MS + 1)).isNew).isTrue()
        assertThat(store.events.first()).hasSize(2)
    }

    @Test
    fun aRepostThatIsRecognisedClearsUnrecognised() = runTest {
        store.record(ring(unrecognised = true))
        assertThat(store.record(ring(at = NOW + 1_000)).event.unrecognised).isFalse()
    }

    /** 5 §6: the event stays without a picture, and the log names no device. */
    @Test
    fun aFailedSnapshotKeepsTheEventWithoutAPicture() = runTest {
        ShadowLog.clear()
        val recorded = store.record(ring(snapshot = Snapshot { throw IOException("disk full") }))
        assertThat(recorded.event.snapshot).isNull()
        assertThat(dir.names()).isEmpty()
        val logs = ShadowLog.getLogs().filter { it.tag == "DoorStore" }
        assertThat(logs).isNotEmpty()
        logs.forEach { assertThat(it.msg).doesNotContain("Front Door") }
    }

    /** 5 §4.5: older than 48 hours goes with its picture; files with no event go too. */
    @Test
    fun pruneDeletesOldEventsTheirSnapshotsAndOrphans() = runTest {
        val old = store.record(ring(key = "old", at = NOW - KEEP_MS - 1, snapshot = aPicture)).event
        val edge = store.record(ring(key = "edge", at = NOW - KEEP_MS, snapshot = aPicture)).event
        File(dir, "999.jpg").writeBytes(byteArrayOf(1))
        File(dir, "${edge.id}.jpg.part").writeBytes(byteArrayOf(1))
        store.prune(NOW)
        assertThat(store.events.first().map { it.id }).containsExactly(edge.id)
        assertThat(dir.names()).containsExactly("${edge.id}.jpg")
        assertThat(old.snapshot!!.exists()).isFalse()
    }

    @Test
    fun recordingPrunes() = runTest {
        store.record(ring(key = "old", at = NOW - KEEP_MS - 1))
        store.record(ring(key = "new"))
        assertThat(store.events.first().map { it.atMillis }).containsExactly(NOW)
    }

    /** 5 §4.7–4.8: unrecognised alerts count as rings; Got it marks one; opening the tab marks all. */
    @Test
    fun unseenCountsRingsAndMotionUntilSeen() = runTest {
        val a = store.record(ring(key = "a")).event
        store.record(ring(key = "b", unrecognised = true))
        store.record(motion())
        assertThat(store.unseen.first()).isEqualTo(Unseen(rings = 2, motion = 1))
        store.markSeen(a.id)
        assertThat(store.unseen.first()).isEqualTo(Unseen(rings = 1, motion = 1))
        store.markAllSeen()
        assertThat(store.unseen.first()).isEqualTo(Unseen(rings = 0, motion = 0))
    }

    @Test
    fun eventsAreNewestFirst() = runTest {
        store.record(ring(key = "a", at = NOW - 5_000))
        store.record(motion(at = NOW))
        assertThat(store.events.first().map { it.kind }).containsExactly(DoorKind.MOTION, DoorKind.RING).inOrder()
    }

    @Test
    fun theBadgeIsACountOfRingsOrADotForMotionAlone() {
        assertThat(badgeOf(Unseen(0, 0))).isNull()
        assertThat(badgeOf(Unseen(1, 3))).isEqualTo(RailBadge.Count(1, "1 unseen ring"))
        assertThat(badgeOf(Unseen(2, 0))).isEqualTo(RailBadge.Count(2, "2 unseen rings"))
        assertThat(badgeOf(Unseen(0, 1))).isEqualTo(RailBadge.Dot("new motion"))
    }
}
```
(import `uk.co.siland.culvery.core.plugin.RailBadge`.)
Create `BitmapSnapshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** 5 §4.4: at most 640 px wide, as JPEG; a narrower picture keeps its size. Native graphics, for a real encoder. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapSnapshotTest {
    @get:Rule val folder = TemporaryFolder()

    private fun saved(width: Int, height: Int): File = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(width, height)).saveTo(it) }

    @Test
    fun aWidePictureIsScaledTo640AndSavedAsJpeg() {
        val file = saved(1280, 720)
        assertThat(file.readBytes().take(2)).containsExactly(0xFF.toByte(), 0xD8.toByte()).inOrder()
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.path, it) }
        assertThat(size.outWidth to size.outHeight).isEqualTo(640 to 360)
    }

    @Test
    fun aNarrowPictureKeepsItsSize() {
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(saved(480, 270).path, it) }
        assertThat(size.outWidth).isEqualTo(480)
    }
}
```
Create `DoorSettingsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalTime
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
class DoorSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { DoorSettings(doorSettingsStore(scope) { File(folder.root, "door_settings.preferences_pb") }, scope) }

    @After
    fun tearDown() = scope.cancel()

    /** 5 §2.6, §4.1, §5.4: 70 %, quiet 21:00–07:00 on, the capture off, not connected. */
    @Test
    fun theDefaults() = runTest {
        assertThat(settings.prefs.first()).isEqualTo(
            DoorPrefs(connected = false, volume = 0.7f, quietOn = true, quietFrom = LocalTime.of(21, 0), quietUntil = LocalTime.of(7, 0),
                captureUntilMillis = 0L, lastHeardMillis = null, unrecognisedRun = 0),
        )
    }

    @Test
    fun eachSettingIsKept() = runTest {
        settings.setConnected(true)
        settings.setVolume(0.4f)
        settings.setQuietHours(on = false, from = LocalTime.of(22, 30), until = LocalTime.of(6, 30))
        settings.setCapture(untilMillis = NOW + 1_800_000)
        settings.heard(NOW)
        val prefs = settings.prefs.first()
        assertThat(prefs).isEqualTo(DoorPrefs(true, 0.4f, false, LocalTime.of(22, 30), LocalTime.of(6, 30), NOW + 1_800_000, NOW, 0))
        assertThat(settings.current.first { it == prefs }).isEqualTo(prefs)
    }

    /** Ruling 14: the run of unrecognised alerts, ended by any recognised ring or motion. */
    @Test
    fun theRunOfUnrecognisedAlertsEndsAtARecognisedOne() = runTest {
        repeat(3) { settings.countAlert(recognised = false) }
        assertThat(settings.prefs.first().unrecognisedRun).isEqualTo(3)
        settings.countAlert(recognised = true)
        assertThat(settings.prefs.first().unrecognisedRun).isEqualTo(0)
    }

    @Test
    fun aVolumeOutOfRangeIsHeldToIt() = runTest {
        settings.setVolume(1.5f)
        assertThat(settings.prefs.first().volume).isEqualTo(1f)
    }
}
```
Create `DoorCapabilityTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
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
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.RailBadge

/** A provider whose answers a test sets. */
internal class FakeDoorProvider(var installed: Boolean = true, var access: Boolean = true) : DoorProvider {
    override val descriptor = ProviderDescriptor("door.ring", "Ring", "doorbell", setOf(Feature.READ), connectHint = "Sign in as a Ring Shared User.")
    override fun appInstalled() = installed
    override fun accessAllowed() = access
    override fun accessScreen() = Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS")
}

@RunWith(AndroidJUnit4::class)
class DoorCapabilityTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = doorDb()
    private val store by lazy { DoorStore(db, File(folder.root, "door")) }
    private val settings by lazy { DoorSettings(doorSettingsStore(scope) { File(folder.root, "s.preferences_pb") }, scope) }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    /** 5 §3.1: Door, slot 50, the doorbell; a tab once Ring is connected, and never without a provider. */
    @Test
    fun theTabAppearsOnceRingIsConnected() = runTest {
        val door = DoorCapability(store, settings, setOf(FakeDoorProvider()))
        assertThat(listOf(door.id, door.label, door.icon, door.order)).containsExactly("door", "Door", "doorbell", 50).inOrder()
        assertThat(door.hasTab.first()).isFalse()
        settings.setConnected(true)
        assertThat(door.hasTab.first { it }).isTrue()
        assertThat(DoorCapability(store, settings, emptySet()).hasTab.first()).isFalse()
    }

    @Test
    fun theBadgeFollowsWhatIsUnseen() = runTest {
        val door = DoorCapability(store, settings, setOf(FakeDoorProvider()))
        assertThat(door.badge.first()).isNull()
        store.record(ring())
        assertThat(door.badge.first { it != null }).isEqualTo(RailBadge.Count(1, "1 unseen ring"))
    }
}
```
Create `DoorPruneLoopTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.door.db.DoorEventEntity
import uk.co.siland.culvery.core.plugin.WallClock

/** 5 §4.5: once at start, then daily, so a quiet doorbell's old pictures still go. */
@RunWith(AndroidJUnit4::class)
class DoorPruneLoopTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun itPrunesAtStartAndThenEachDay() = runTest {
        val db = doorDb()
        val store = DoorStore(db, File(folder.root, "door"))
        store.record(ring(key = "a", at = NOW - KEEP_MS - 1))
        DoorPruneLoop(store, WallClock { NOW + testScheduler.currentTime }, backgroundScope).start()
        runCurrent()
        assertThat(store.events.first { it.isEmpty() }).isEmpty()
        // Inside 48 hours today, past them by tomorrow; stored without a prune.
        db.doorDao().insert(DoorEventEntity(noticeKey = "b", atMillis = NOW - KEEP_MS + 1, kind = "RING", device = "", unrecognised = false, hasSnapshot = false, seen = false))
        advanceTimeBy(DAY_MS + 1)
        assertThat(store.events.first { it.isEmpty() }).isEmpty()
        db.close()
    }
}
```
Fails today: none of these types exist; the module doesn't build.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :capability:door:testDebugUnitTest`
Expected: FAIL to compile (unresolved `DoorStore`, `DoorSettings`, …).

- [ ] **Step 4: The contract**

Create `capability/door/src/main/java/uk/co/siland/culvery/capability/door/DoorContract.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import android.content.Intent
import android.graphics.Bitmap
import java.io.File
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

enum class DoorKind { RING, MOTION }

/**
 * A ring or motion as the Door tab keeps it (5 §3.1). [unrecognised] is a Ring alert the parser couldn't place, shown and
 * alerted as a ring; [snapshot] is its saved picture, if any; [device] is as the doorbell's app names it, maybe empty.
 */
data class DoorEvent(
    val id: Long,
    val atMillis: Long,
    val kind: DoorKind,
    val device: String,
    val unrecognised: Boolean,
    val snapshot: File?,
    val seen: Boolean,
)

/** A picture heard with a notice; the store saves it where the event's picture lives. */
fun interface Snapshot {
    fun saveTo(file: File)
}

const val MAX_SNAPSHOT_WIDTH_PX = 640
private const val JPEG_QUALITY = 85

/** A provider's picture: scaled down to [MAX_SNAPSHOT_WIDTH_PX] wide if wider, and written as JPEG (5 §4.4). */
class BitmapSnapshot(private val bitmap: Bitmap) : Snapshot {
    override fun saveTo(file: File) {
        val scaled = if (bitmap.width <= MAX_SNAPSHOT_WIDTH_PX) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, MAX_SNAPSHOT_WIDTH_PX, bitmap.height * MAX_SNAPSHOT_WIDTH_PX / bitmap.width, true)
        }
        file.outputStream().use { out -> check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "The JPEG wasn't written" } }
    }
}

/** What a provider heard (5 §4): [key] is the notice's own, which a re-post with its picture repeats. */
data class DoorNotice(
    val key: String,
    val atMillis: Long,
    val kind: DoorKind,
    val device: String,
    val unrecognised: Boolean = false,
    val snapshot: Snapshot? = null,
)

/** What a provider calls with what it heard (5 §3.1). Safe from any thread; every call returns at once. */
interface DoorEventSink {
    /** Any notice from the doorbell's app, one that is then ignored included: "Last heard from Ring" (5 §4.2). */
    fun heard(atMillis: Long)

    /** A ring, motion or unrecognised alert to record, and maybe alert on. */
    fun post(notice: DoorNotice)

    /** Android bound (true) or unbound (false) the provider's listener. */
    fun listening(bound: Boolean)

    /** Check Ring's notifications is on: the provider writes each notice's shape to the log (5 §5.4). */
    val capturing: Boolean
}

/** A doorbell's app on the tablet, as the capability asks about it (5 §3.2, §5.5, §6). */
interface DoorProvider {
    val descriptor: ProviderDescriptor

    fun appInstalled(): Boolean

    /** Android lets Culvery read notifications. */
    fun accessAllowed(): Boolean

    /** Android's notification-access screen, for Culvery where the tablet has one. */
    fun accessScreen(): Intent
}
```

- [ ] **Step 5: The table and the store**

Create `capability/door/src/main/java/uk/co/siland/culvery/capability/door/db/DoorDatabase.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One ring or motion (5 §3.1); its picture, when [hasSnapshot], is `files/door/<id>.jpg`. */
@Entity(tableName = "door_event", indices = [Index("noticeKey")])
data class DoorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noticeKey: String,
    val atMillis: Long,
    /** A `DoorKind` name. */
    val kind: String,
    val device: String,
    val unrecognised: Boolean,
    val hasSnapshot: Boolean,
    val seen: Boolean,
)

@Dao
interface DoorDao {
    @Query("SELECT * FROM door_event ORDER BY atMillis DESC, id DESC")
    fun events(): Flow<List<DoorEventEntity>>

    @Query("SELECT * FROM door_event WHERE noticeKey = :key AND atMillis >= :since ORDER BY atMillis DESC LIMIT 1")
    suspend fun recent(key: String, since: Long): DoorEventEntity?

    @Insert
    suspend fun insert(row: DoorEventEntity): Long

    @Update
    suspend fun update(row: DoorEventEntity)

    @Query("DELETE FROM door_event WHERE atMillis < :before")
    suspend fun deleteBefore(before: Long)

    @Query("SELECT id FROM door_event WHERE hasSnapshot = 1")
    suspend fun withSnapshots(): List<Long>

    @Query("UPDATE door_event SET seen = 1 WHERE seen = 0")
    suspend fun markAllSeen()

    @Query("UPDATE door_event SET seen = 1 WHERE id = :id")
    suspend fun markSeen(id: Long)

    @Query("SELECT COUNT(*) FROM door_event WHERE seen = 0 AND kind = 'RING'")
    fun unseenRings(): Flow<Int>

    @Query("SELECT COUNT(*) FROM door_event WHERE seen = 0 AND kind = 'MOTION'")
    fun unseenMotion(): Flow<Int>
}

/** v1. A later version ships a hand-written Migration, as the calendar's do. */
@Database(entities = [DoorEventEntity::class], version = 1, exportSchema = true)
abstract class DoorDatabase : RoomDatabase() {
    abstract fun doorDao(): DoorDao
}
```
Create `DoorStore.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.door.db.DoorDatabase
import uk.co.siland.culvery.capability.door.db.DoorEventEntity

/** A key posted again within this long of its event updates it (5 §4.3). */
const val MERGE_MS = 120_000L

/** Events and their pictures are kept this long (5 §2.8). */
const val KEEP_MS = 48 * 60 * 60 * 1_000L

private const val SNAPSHOT_DIR = "door"
private const val TAG = "DoorStore"

data class Recorded(val event: DoorEvent, val isNew: Boolean)

data class Unseen(val rings: Int, val motion: Int)

/** `door.db` and `files/door/` (5 §3.1): rings and motion for 48 hours, with their pictures. */
@Singleton
class DoorStore internal constructor(
    db: DoorDatabase,
    private val dir: File,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    @Inject
    constructor(db: DoorDatabase, @ApplicationContext context: Context) : this(db, File(context.filesDir, SNAPSHOT_DIR))

    private val dao = db.doorDao()

    // A record and a prune never meet: a prune would take a picture still being written for an event not yet stored.
    private val lock = Mutex()

    /** Newest first. */
    val events: Flow<List<DoorEvent>> = dao.events().map { rows -> rows.map { it.toEvent() } }

    /** Unseen rings (unrecognised ones included) and unseen motion (5 §4.8). */
    val unseen: Flow<Unseen> = combine(dao.unseenRings(), dao.unseenMotion(), ::Unseen).distinctUntilChanged()

    /**
     * Records [notice] (5 §4.3–4.5): the same key within [MERGE_MS] of its event updates that event (its device, whether it
     * was recognised, a new picture) and isn't new; otherwise a new event. Then prunes.
     */
    suspend fun record(notice: DoorNotice): Recorded = lock.withLock {
        val earlier = dao.recent(notice.key, notice.atMillis - MERGE_MS)
        val recorded = if (earlier != null) {
            val saved = notice.snapshot?.let { save(earlier.id, it) } == true
            val row = earlier.copy(device = notice.device, unrecognised = notice.unrecognised, hasSnapshot = earlier.hasSnapshot || saved)
            dao.update(row)
            Recorded(row.toEvent(), isNew = false)
        } else {
            val row = DoorEventEntity(
                noticeKey = notice.key, atMillis = notice.atMillis, kind = notice.kind.name, device = notice.device,
                unrecognised = notice.unrecognised, hasSnapshot = false, seen = false,
            )
            val id = dao.insert(row)
            val saved = notice.snapshot?.let { save(id, it) } == true
            val stored = row.copy(id = id, hasSnapshot = saved)
            if (saved) dao.update(stored)
            Recorded(stored.toEvent(), isNew = true)
        }
        pruneLocked(notice.atMillis)
        recorded
    }

    /** Deletes events older than [KEEP_MS] before [nowMillis], their pictures, and pictures with no event (5 §4.5). */
    suspend fun prune(nowMillis: Long) = lock.withLock { pruneLocked(nowMillis) }

    suspend fun markAllSeen() = dao.markAllSeen()

    suspend fun markSeen(id: Long) = dao.markSeen(id)

    private suspend fun pruneLocked(nowMillis: Long) {
        dao.deleteBefore(nowMillis - KEEP_MS)
        val kept = dao.withSnapshots().map { fileOf(it).name }.toSet()
        withContext(io) { dir.listFiles()?.filter { it.name !in kept }?.forEach { it.delete() } }
    }

    /** Written beside, then renamed, so a failed or interrupted save never leaves half a picture under the event's name. */
    private suspend fun save(id: Long, snapshot: Snapshot): Boolean = withContext(io) {
        val part = File(dir, "$id.jpg.part")
        try {
            dir.mkdirs()
            snapshot.saveTo(part)
            check(part.renameTo(fileOf(id))) { "The picture couldn't be renamed" }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save a door snapshot (${e::class.simpleName})")
            part.delete()
            false
        }
    }

    private fun fileOf(id: Long) = File(dir, "$id.jpg")

    private fun DoorEventEntity.toEvent() =
        DoorEvent(id, atMillis, DoorKind.valueOf(kind), device, unrecognised, fileOf(id).takeIf { hasSnapshot }, seen)
}
```

- [ ] **Step 6: The settings**

Create `DoorSettings.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.LocalTime
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

private const val FILE = "door_settings"
private const val TAG = "DoorSettings"
private const val SECONDS_PER_MINUTE = 60

/** The door's settings (ruling 8). Defaults: 5 §2.6, §4.1, §5.4. */
data class DoorPrefs(
    /** Both connect steps were ticked: the Door tab shows (5 §5.5). */
    val connected: Boolean = false,
    val volume: Float = 0.7f,
    val quietOn: Boolean = true,
    val quietFrom: LocalTime = LocalTime.of(21, 0),
    val quietUntil: LocalTime = LocalTime.of(7, 0),
    /** Check Ring's notifications runs until then; 0 when off. */
    val captureUntilMillis: Long = 0L,
    val lastHeardMillis: Long? = null,
    /** New unrecognised alerts since the last recognised one (ruling 14). */
    val unrecognisedRun: Int = 0,
)

/** The settings file's store; one that can't be read is replaced by an empty one, the defaults. */
internal fun doorSettingsStore(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, scope = scope, produceFile = produceFile)

/** A small DataStore file of its own (ruling 8). Writes throw [IOException]; the caller says so or logs it. */
@Singleton
class DoorSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(doorSettingsStore { context.preferencesDataStoreFile(FILE) }, scope)

    val prefs: Flow<DoorPrefs> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Couldn't read the door's settings (${e::class.simpleName})")
            emit(emptyPreferences())
        }
        .map { it.toPrefs() }
        .distinctUntilChanged()

    /** For the listener and the alert decision, which can't wait: the defaults until the file is first read. */
    val current: StateFlow<DoorPrefs> = prefs.stateIn(scope, SharingStarted.Eagerly, DoorPrefs())

    suspend fun setConnected(connected: Boolean) = edit { it[CONNECTED] = connected }

    suspend fun setVolume(volume: Float) = edit { it[VOLUME] = volume.coerceIn(0f, 1f) }

    suspend fun setQuietHours(on: Boolean, from: LocalTime, until: LocalTime) = edit {
        it[QUIET_ON] = on
        it[QUIET_FROM] = from.toSecondOfDay() / SECONDS_PER_MINUTE
        it[QUIET_UNTIL] = until.toSecondOfDay() / SECONDS_PER_MINUTE
    }

    /** Check Ring's notifications runs until [untilMillis]; 0 turns it off (5 §5.4). */
    suspend fun setCapture(untilMillis: Long) = edit { it[CAPTURE_UNTIL] = untilMillis }

    suspend fun heard(atMillis: Long) = edit { it[LAST_HEARD] = atMillis }

    /** A new ring or motion: a recognised one ends the run of unrecognised ones, an unrecognised one adds to it. */
    suspend fun countAlert(recognised: Boolean) = edit { it[UNRECOGNISED_RUN] = if (recognised) 0 else (it[UNRECOGNISED_RUN] ?: 0) + 1 }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        store.edit(change)
    }

    private fun Preferences.toPrefs(): DoorPrefs {
        val defaults = DoorPrefs()
        return DoorPrefs(
            connected = this[CONNECTED] ?: defaults.connected,
            volume = this[VOLUME] ?: defaults.volume,
            quietOn = this[QUIET_ON] ?: defaults.quietOn,
            quietFrom = this[QUIET_FROM]?.let { LocalTime.ofSecondOfDay(it * SECONDS_PER_MINUTE.toLong()) } ?: defaults.quietFrom,
            quietUntil = this[QUIET_UNTIL]?.let { LocalTime.ofSecondOfDay(it * SECONDS_PER_MINUTE.toLong()) } ?: defaults.quietUntil,
            captureUntilMillis = this[CAPTURE_UNTIL] ?: defaults.captureUntilMillis,
            lastHeardMillis = this[LAST_HEARD],
            unrecognisedRun = this[UNRECOGNISED_RUN] ?: defaults.unrecognisedRun,
        )
    }

    private companion object {
        val CONNECTED = booleanPreferencesKey("connected")
        val VOLUME = floatPreferencesKey("chime_volume")
        val QUIET_ON = booleanPreferencesKey("quiet_on")
        val QUIET_FROM = intPreferencesKey("quiet_from_minute")
        val QUIET_UNTIL = intPreferencesKey("quiet_until_minute")
        val CAPTURE_UNTIL = longPreferencesKey("capture_until")
        val LAST_HEARD = longPreferencesKey("last_heard")
        val UNRECOGNISED_RUN = intPreferencesKey("unrecognised_run")
    }
}
```
- [ ] **Step 7: Words, the capability, the prune loop and Hilt**

Create `DoorWords.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.ui.plural

/** 5 §5.3: "Door, new motion". */
internal const val NEW_MOTION = "new motion"

/** Unseen rings as a count; unseen motion alone as a dot; else none (5 §4.8). */
fun badgeOf(unseen: Unseen): RailBadge? = when {
    unseen.rings > 0 -> RailBadge.Count(unseen.rings, plural(unseen.rings.toLong(), "unseen ring"))
    unseen.motion > 0 -> RailBadge.Dot(NEW_MOTION)
    else -> null
}
```
Create `DoorCapability.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.ui.Icons

const val DOOR_ID = "door"

/** The doorbell (5 §3.1): rail slot 50, reserved for Security; no Home card. */
@Singleton
class DoorCapability @Inject constructor(
    private val store: DoorStore,
    private val settings: DoorSettings,
    providers: Set<@JvmSuppressWildcards DoorProvider>,
) : Capability {
    override val id = DOOR_ID
    override val label = "Door"
    override val icon = Icons.DOORBELL
    override val order = 50

    private val hasProvider = providers.isNotEmpty()

    /** Once Ring is connected (5 §3.1, §5.5). */
    override val hasTab: Flow<Boolean> = settings.prefs.map { hasProvider && it.connected }.distinctUntilChanged()

    override val badge: Flow<RailBadge?> = store.unseen.map(::badgeOf).distinctUntilChanged()

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit
}
```
(The tab can't show until Settings › Door connects Ring; the Door tab fills `TabContent`, and Settings › Door adds `settingsPages()`.)
Create `DoorPruneLoop.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.WallClock

internal const val DAY_MS = 24 * 60 * 60 * 1_000L

/** Prunes at start and then daily (5 §4.5), so a doorbell nobody presses still loses its old pictures. */
class DoorPruneLoop @Inject constructor(
    private val store: DoorStore,
    private val clock: WallClock,
    @ApplicationScope private val scope: CoroutineScope,
) : Startable {
    override fun start() {
        scope.launch {
            while (true) {
                try {
                    store.prune(clock.nowMillis())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't prune the door's history (${e::class.simpleName}); trying again tomorrow")
                }
                delay(DAY_MS)
            }
        }
    }

    private companion object {
        const val TAG = "DoorPruneLoop"
    }
}
```
Create `di/DoorModule.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import uk.co.siland.culvery.capability.door.DoorCapability
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.capability.door.DoorPruneLoop
import uk.co.siland.culvery.capability.door.db.DoorDatabase
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class DoorModule {
    // With no provider bound there is no Door tab (5 §3.1).
    @Multibinds
    abstract fun providers(): Set<DoorProvider>

    @Binds
    @IntoSet
    abstract fun capability(impl: DoorCapability): Capability

    @Binds
    @IntoSet
    abstract fun pruneLoop(impl: DoorPruneLoop): Startable

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): DoorDatabase =
            Room.databaseBuilder(context, DoorDatabase::class.java, "door.db").build()
    }
}
```

- [ ] **Step 8: Run them to see them pass**

Run: `./gradlew :capability:door:testDebugUnitTest :core:ui:testDebugUnitTest --tests '*IconFontTest*' --tests '*CopyScanTest*' :app:testDebugUnitTest --tests '*AppSourceTest*'` and `./gradlew -p build-logic :convention:test --tests '*ModuleBoundariesTest*'`.
Expected: PASS. Fix `NOW` in `TestDoor.kt` first if the scratch check showed it differs.

- [ ] **Step 9: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL, no image changed (the icon font only gained glyphs).
```bash
git add settings.gradle.kts app/build.gradle.kts app/src/test/java/uk/co/siland/culvery/AppSourceTest.kt capability/door build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt core/ui/src tools/fonts/icons.txt
git commit -m "Add the Door capability's store, settings and badge"
```

---

### Task 10: `:provider:door-ring`: the notice, the parser and the adapter (§3.2, §2.12; ruling 10)

**Review:** sonnet.

**Files:**
- Modify: `settings.gradle.kts` (`include(":provider:door-ring")`), `app/build.gradle.kts`
- Create: `provider/door-ring/build.gradle.kts`, `provider/door-ring/src/test/resources/robolectric.properties`
- Create (package `uk.co.siland.culvery.provider.door_ring`, under `provider/door-ring/src/main/java/uk/co/siland/culvery/provider/door_ring/`): `RingNotice.kt`, `RingParser.kt`, `RingAdapter.kt`
- Test: `provider/door-ring/src/test/java/uk/co/siland/culvery/provider/door_ring/RingFixtures.kt`, `RingParserTest.kt`, `RingAdapterTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks but the module rules.
- Produces:
  - `const val RING_PACKAGE = "com.ringapp"`
  - `data class RingNotice(packageName: String, key: String, channelId: String?, category: String?, title: String?, text: String?, bigText: String?, hasPicture: Boolean, postTime: Long, ongoing: Boolean = false, groupSummary: Boolean = false)`
  - `sealed interface RingReading { data class Ring(val device: String); data class Motion(val device: String); data class UnknownRingAlert(val device: String); data object Ignore }`; `val RingReading.outcome: String`
  - `object RingParser { fun read(notice: RingNotice): RingReading }`
  - `class RingPost(val notice: RingNotice, val picture: Bitmap?, val extraKeys: Set<String>)`; `fun ringPost(packageName: String, key: String, postTime: Long, notification: Notification, context: Context): RingPost`

- [ ] **Step 1: The module**

`settings.gradle.kts`: `include(":provider:door-ring")` after `include(":provider:calendar-google")`. `app/build.gradle.kts`, after the door capability: `implementation(project(":provider:door-ring"))`.
Create `provider/door-ring/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:door"))
    implementation(project(":core:plugin"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
}
```
`provider/door-ring/src/test/resources/robolectric.properties`: `qualifiers=w1280dp-h800dp-land-hdpi`.

- [ ] **Step 2: Write the failing tests**

Create `RingFixtures.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

/**
 * BEST GUESSES at the Ring app's notifications, not captures (5 §2.12): the tablet's captures replace every one of these
 * (5 §8.3). The words follow what Ring is known to show; the channels and categories are guesses. "Front Door" is a
 * placeholder device; captures use [device] and [person].
 */
internal object RingFixtures {
    private const val POSTED = 1_791_982_920_000L

    private fun notice(
        id: Int,
        title: String?,
        text: String?,
        channel: String? = "ding",
        category: String? = null,
        bigText: String? = null,
        picture: Boolean = false,
        ongoing: Boolean = false,
        summary: Boolean = false,
    ) = RingNotice(RING_PACKAGE, "0|$RING_PACKAGE|$id|null|10123", channel, category, title, text, bigText, picture, POSTED, ongoing, summary)

    val ring = notice(101, "Front Door", "There’s someone at your Front Door")
    /** The same notification posted again with the snapshot. */
    val ringWithSnapshot = ring.copy(hasPicture = true, bigText = "There’s someone at your Front Door")
    val motion = notice(102, "Front Door", "There is motion at your Front Door", channel = "motion")
    val unknown = notice(103, "Front Door", "New event at your Front Door", channel = "alerts")
    val appUpdate = notice(104, "Ring", "A new version of Ring is available. Update now.", channel = "general")
    val promo = notice(105, "Ring", "Save 20% on Ring Protect", channel = "marketing", category = "promo")
    val battery = notice(106, "Front Door", "Front Door battery is low", channel = "device_health")
    val liveView = notice(107, "Front Door", "Live View", channel = "live_view", category = "service", ongoing = true)
    val summary = notice(108, "Ring", null, summary = true)
}
```
Create `RingParserTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 5 §7: a ring, motion, a ring then its snapshot, an unknown Ring alert, and Ring's ignorable notifications. */
class RingParserTest {
    @Test
    fun aRingIsARingOnItsDevice() = assertThat(RingParser.read(RingFixtures.ring)).isEqualTo(RingReading.Ring("Front Door"))

    @Test
    fun theSnapshotRepostReadsTheSameWithTheSameKey() {
        assertThat(RingParser.read(RingFixtures.ringWithSnapshot)).isEqualTo(RingReading.Ring("Front Door"))
        assertThat(RingFixtures.ringWithSnapshot.key).isEqualTo(RingFixtures.ring.key)
    }

    @Test
    fun motionIsMotion() = assertThat(RingParser.read(RingFixtures.motion)).isEqualTo(RingReading.Motion("Front Door"))

    /** The fallback: from Ring, not placed, not ignorable; it still alerts as a ring. */
    @Test
    fun anythingElseFromRingIsAnUnknownAlert() =
        assertThat(RingParser.read(RingFixtures.unknown)).isEqualTo(RingReading.UnknownRingAlert("Front Door"))

    @Test
    fun ringsOwnNoticesAreIgnored() {
        listOf(RingFixtures.appUpdate, RingFixtures.promo, RingFixtures.battery, RingFixtures.liveView, RingFixtures.summary)
            .forEach { assertThat(RingParser.read(it)).isEqualTo(RingReading.Ignore) }
    }

    /** When the title is the app's own name, the device is what follows "at your". */
    @Test
    fun theDeviceComesFromTheWordsWhenTheTitleIsRings() {
        val notice = RingFixtures.motion.copy(title = "Ring", text = "There is motion at your Back Garden.")
        assertThat(RingParser.read(notice)).isEqualTo(RingReading.Motion("Back Garden"))
    }

    @Test
    fun aNoticeWithNoWordsIsAnUnknownAlertWithNoDevice() {
        val notice = RingFixtures.ring.copy(title = null, text = null)
        assertThat(RingParser.read(notice)).isEqualTo(RingReading.UnknownRingAlert(""))
    }

    /** 5 §6: what a log may say. */
    @Test
    fun eachOutcomeHasWordsWithNoDevice() {
        assertThat(
            listOf(RingReading.Ring("Front Door"), RingReading.Motion("Front Door"), RingReading.UnknownRingAlert("Front Door"), RingReading.Ignore)
                .map { it.outcome },
        ).containsExactly("a ring", "motion", "an unknown alert", "ignored").inOrder()
    }
}
```
Create `RingAdapterTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** 5 §3.2: a notification becomes a RingNotice, with its picture kept apart for the store. */
@RunWith(AndroidJUnit4::class)
class RingAdapterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun builder() = Notification.Builder(context, "ding").setSmallIcon(android.R.drawable.ic_dialog_info)

    @Test
    fun theWordsChannelAndCategoryAreRead() {
        val notification = builder()
            .setContentTitle("Front Door")
            .setContentText("Someone is at your Front Door")
            .setCategory(Notification.CATEGORY_CALL)
            .setStyle(Notification.BigTextStyle().bigText("Someone is at your Front Door."))
            .build()
        val post = ringPost(RING_PACKAGE, "k1", 5L, notification, context)
        assertThat(post.notice).isEqualTo(
            RingNotice(RING_PACKAGE, "k1", "ding", "call", "Front Door", "Someone is at your Front Door", "Someone is at your Front Door.", hasPicture = false, postTime = 5L),
        )
        assertThat(post.picture).isNull()
        assertThat(post.extraKeys).contains(Notification.EXTRA_TITLE)
    }

    @Test
    fun aBigPictureIsTheSnapshot() {
        val picture = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val notification = builder().setContentTitle("Front Door").setStyle(Notification.BigPictureStyle().bigPicture(picture)).build()
        val post = ringPost(RING_PACKAGE, "k1", 5L, notification, context)
        assertThat(post.notice.hasPicture).isTrue()
        assertThat(post.picture?.width).isEqualTo(1280)
    }

    @Test
    fun ongoingAndBundleSummariesAreFlagged() {
        val ongoing = ringPost(RING_PACKAGE, "k1", 5L, builder().setOngoing(true).build(), context)
        val summary = ringPost(RING_PACKAGE, "k2", 5L, builder().setGroup("g").setGroupSummary(true).build(), context)
        assertThat(ongoing.notice.ongoing).isTrue()
        assertThat(summary.notice.groupSummary).isTrue()
    }
}
```
Fails today: the module has no sources.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :provider:door-ring:testDebugUnitTest`
Expected: FAIL to compile.

- [ ] **Step 4: The notice and the parser**

Create `RingNotice.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

/** The Ring app's package on Android (5 §4.1). */
const val RING_PACKAGE = "com.ringapp"

/**
 * One notification as the parser reads it (5 §3.2), with no Android types. [ongoing] and [groupSummary] let Ring's
 * live-view and bundle notifications be ignored rather than read as unknown rings (ruling 10).
 */
data class RingNotice(
    val packageName: String,
    val key: String,
    val channelId: String?,
    val category: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val hasPicture: Boolean,
    val postTime: Long,
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
)
```
Create `RingParser.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

/** What a Ring notice is (5 §3.2). [device] is as Ring names it, empty when it doesn't say. */
sealed interface RingReading {
    data class Ring(val device: String) : RingReading

    data class Motion(val device: String) : RingReading

    data class UnknownRingAlert(val device: String) : RingReading

    data object Ignore : RingReading
}

/** What a log may say about a reading (5 §6): its outcome, never the device. */
val RingReading.outcome: String
    get() = when (this) {
        is RingReading.Ring -> "a ring"
        is RingReading.Motion -> "motion"
        is RingReading.UnknownRingAlert -> "an unknown alert"
        RingReading.Ignore -> "ignored"
    }

/**
 * Places a Ring notice (5 §3.2). Best guesses until the tablet's captures (5 §8.3): Ring's live-view, bundle, app,
 * account, offer and battery notices are ignored; motion when the words say motion; a ring when they say someone is at
 * the door or the bell rang; anything else from Ring is an unknown alert, which still rings.
 */
object RingParser {
    // Android's Notification.CATEGORY_* values as text, so the parser runs without Android.
    private val IGNORED_CATEGORIES = setOf("promo", "recommendation", "status", "progress", "service", "sys", "transport", "err")
    private val IGNORED_WORDS = Regex("""(?i)\b(update|upgrade|subscription|protect|offer|sign in|signed out|password|account|battery|offline|firmware|live view)\b""")
    private val MOTION = Regex("""(?i)\bmotion\b""")
    private val RING = Regex("""(?i)\b(ding|rang|ringing|doorbell (?:press|ring)\w*|someone(?: is|['’]s)? at)\b""")
    private val AT_DEVICE = Regex("""(?i)\bat (?:your |the )?(.+?)[.!]?$""")

    fun read(notice: RingNotice): RingReading {
        val words = listOfNotNull(notice.title, notice.text, notice.bigText).joinToString(" ")
        if (notice.ongoing || notice.groupSummary || notice.category in IGNORED_CATEGORIES || IGNORED_WORDS.containsMatchIn(words)) {
            return RingReading.Ignore
        }
        val device = deviceOf(notice)
        return when {
            MOTION.containsMatchIn(words) -> RingReading.Motion(device)
            RING.containsMatchIn(words) -> RingReading.Ring(device)
            else -> RingReading.UnknownRingAlert(device)
        }
    }

    /** The title, unless it is the app's own name; then what follows "at your" in the text. */
    private fun deviceOf(notice: RingNotice): String =
        notice.title?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Ring", ignoreCase = true) }
            ?: notice.text?.trim()?.let { AT_DEVICE.find(it)?.groupValues?.get(1)?.trim() }
            ?: ""
}
```
Create `RingAdapter.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat

/** A notice, its picture if Ring attached one, and its extras' keys for the capture (5 §8.3). */
class RingPost(val notice: RingNotice, val picture: Bitmap?, val extraKeys: Set<String>)

/** Reads [notification] (5 §3.2): its words, channel and category, the picture, and the flags the parser needs. */
fun ringPost(packageName: String, key: String, postTime: Long, notification: Notification, context: Context): RingPost {
    val extras = notification.extras
    val picture = BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE, Bitmap::class.java) ?: pictureIcon(extras, context)
    val notice = RingNotice(
        packageName = packageName,
        key = key,
        channelId = notification.channelId,
        category = notification.category,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        hasPicture = picture != null,
        postTime = postTime,
        ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
    )
    return RingPost(notice, picture, extras.keySet().toSet())
}

/** From Android 12 a picture that isn't a bitmap comes as an Icon. */
private fun pictureIcon(extras: Bundle, context: Context): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE_ICON, Icon::class.java)?.loadDrawable(context)?.toBitmap()
    } else {
        null
    }
```

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :provider:door-ring:testDebugUnitTest :core:ui:testDebugUnitTest --tests '*CopyScanTest*'`
Expected: PASS. If `CopyScanTest` flags a parser literal, the literal is code (a `Regex(` argument is already a code context); report anything else it flags.

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add settings.gradle.kts app/build.gradle.kts provider/door-ring
git commit -m "Read Ring's notifications into rings, motion and alerts, from best-guess samples"
```

---

### Task 11: The listener, the reader, the sink, the provider and fake rings (§3.2, §4.1–4.2, §5.4, §6, §7; rulings 10–13)

**Review:** opus.

**Files:**
- Create: `provider/door-ring/src/main/AndroidManifest.xml`
- Create (provider package): `RingNotificationListener.kt`, `RingReader.kt`, `RingProvider.kt`, `di/RingModule.kt`; `provider/door-ring/src/debug/java/uk/co/siland/culvery/provider/door_ring/FakeRings.kt`, `provider/door-ring/src/release/java/uk/co/siland/culvery/provider/door_ring/FakeRings.kt`
- Create: `capability/door/src/main/java/uk/co/siland/culvery/capability/door/DoorRecorder.kt`; Modify `capability/door/.../di/DoorModule.kt` (bind the sink)
- Create: `app/src/debug/java/uk/co/siland/culvery/DebugRingReceiver.kt`; Modify `app/src/debug/AndroidManifest.xml`
- Modify: `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` (`ALLOWED`)
- Test: `provider/door-ring/src/test/.../RingReaderTest.kt`, `RingProviderTest.kt`; `capability/door/src/test/.../DoorRecorderTest.kt`

**Interfaces:**
- Consumes: `DoorEventSink`, `DoorNotice`, `DoorKind`, `BitmapSnapshot`, `DoorProvider`, `DoorStore`, `DoorSettings`, `Recorded` (Task 9); `RingParser`, `RingReading.outcome`, `RingPost`, `ringPost`, `RING_PACKAGE` (Task 10).
- Produces:
  - `@Singleton class DoorRecorder @Inject constructor(store: DoorStore, settings: DoorSettings, @ApplicationScope scope: CoroutineScope, clock: WallClock) : DoorEventSink` with `val bound: StateFlow<Boolean?>` (null until Android first binds or unbinds the listener). Task 13 adds the alert decision as a constructor parameter.
  - `class RingReader @Inject constructor(sink: DoorEventSink)`: `fun reads(packageName: String, tag: String?): Boolean`, `fun read(post: RingPost)`, `fun listening(bound: Boolean)`.
  - `@Singleton class RingProvider : DoorProvider`, descriptor id `door.ring`; `const val RING_PROVIDER_ID = "door.ring"`.
  - `object FakeRings { ENABLED; appPresent }` (debug: `const val ENABLED = true`, `@Volatile var appPresent = true`; release: `const val ENABLED = false`, `val appPresent = false`); `const val SHELL_PACKAGE = "com.android.shell"`, `const val FAKE_RING_TAG = "culvery-fake-ring"`.

- [ ] **Step 1: Write the failing tests**

Create `provider/door-ring/src/test/java/uk/co/siland/culvery/provider/door_ring/RingReaderTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.DoorEventSink
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorNotice

private class RecordingSink(override var capturing: Boolean = false) : DoorEventSink {
    val heard = mutableListOf<Long>()
    val posted = mutableListOf<DoorNotice>()
    val bound = mutableListOf<Boolean>()
    override fun heard(atMillis: Long) { heard += atMillis }
    override fun post(notice: DoorNotice) { posted += notice }
    override fun listening(bound: Boolean) { this.bound += bound }
}

/** 5 §4.1–4.2, §6, §7: only Ring's package; heard for every notice; the outcome logged, never the words. */
@RunWith(AndroidJUnit4::class)
class RingReaderTest {
    private val sink = RecordingSink()
    private val reader = RingReader(sink)

    private fun post(notice: RingNotice, picture: Bitmap? = null) = RingPost(notice, picture, setOf("android.title", "android.text"))

    @Before
    fun clearLogs() = ShadowLog.clear()

    /** The unit tests run the debug variant, where the adb shell's fake rings count (5 §7). */
    @Test
    fun onlyRingAndTheShellsFakeRingsAreRead() {
        assertThat(reader.reads(RING_PACKAGE, null)).isTrue()
        assertThat(reader.reads(SHELL_PACKAGE, FAKE_RING_TAG)).isEqualTo(FakeRings.ENABLED)
        assertThat(reader.reads(SHELL_PACKAGE, "$FAKE_RING_TAG-2")).isEqualTo(FakeRings.ENABLED)
        assertThat(reader.reads(SHELL_PACKAGE, "something else")).isFalse()
        assertThat(reader.reads("com.whatsapp", null)).isFalse()
    }

    @Test
    fun aRingIsPostedWithItsPictureAndHeard() {
        reader.read(post(RingFixtures.ring, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)))
        val notice = sink.posted.single()
        assertThat(notice.copy(snapshot = null)).isEqualTo(DoorNotice(RingFixtures.ring.key, RingFixtures.ring.postTime, DoorKind.RING, "Front Door"))
        assertThat(notice.snapshot).isInstanceOf(BitmapSnapshot::class.java)
        assertThat(sink.heard).containsExactly(RingFixtures.ring.postTime)
    }

    @Test
    fun motionAndAnUnknownAlertArePostedAsSuch() {
        reader.read(post(RingFixtures.motion))
        reader.read(post(RingFixtures.unknown))
        assertThat(sink.posted.map { it.kind to it.unrecognised }).containsExactly(DoorKind.MOTION to false, DoorKind.RING to true).inOrder()
    }

    /** Ignored notices still count as heard from Ring (5 §4.2). */
    @Test
    fun anIgnoredNoticeIsOnlyHeard() {
        reader.read(post(RingFixtures.appUpdate))
        assertThat(sink.posted).isEmpty()
        assertThat(sink.heard).hasSize(1)
    }

    @Test
    fun theLogSaysTheOutcomeNeverTheWords() {
        reader.read(post(RingFixtures.ring))
        val lines = ShadowLog.getLogs().filter { it.tag == TAG_RING }
        assertThat(lines.map { it.msg }).containsExactly("A Ring notice was read as a ring")
    }

    /** 5 §5.4, §8.3: while the switch is on, each notice's whole shape goes to the log; the one exception (§6). */
    @Test
    fun theCaptureWritesTheWholeNoticeOnlyWhileOn() {
        sink.capturing = true
        reader.read(post(RingFixtures.ring))
        val capture = ShadowLog.getLogs().single { it.tag == TAG_RING && it.msg.startsWith("Ring notice: ") }
        assertThat(capture.msg).contains("Front Door")
        assertThat(capture.msg).contains("extras=[android.text, android.title]")
    }

    @Test
    fun bindingIsPassedOn() {
        reader.listening(false)
        reader.listening(true)
        assertThat(sink.bound).containsExactly(false, true).inOrder()
    }
}
```
Create `RingProviderTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class RingProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = RingProvider(context)

    @After
    fun tearDown() {
        FakeRings.appPresent = true
    }

    @Test
    fun itIsRingAndNeverHoldsCredentials() {
        assertThat(provider.descriptor.id).isEqualTo("door.ring")
        assertThat(provider.descriptor.displayName).isEqualTo("Ring")
        assertThat(provider.descriptor.connectHint).isEqualTo("Sign in as a Ring Shared User, and turn off the Ring app’s notification sound.")
    }

    /** Debug builds count the Ring app as installed until DebugRingReceiver says otherwise (ruling 12). */
    @Test
    fun theAppCheckFollowsFakeRingsWhenRingIsntInstalled() {
        assertThat(provider.appInstalled()).isTrue()
        FakeRings.appPresent = false
        assertThat(provider.appInstalled()).isFalse()
    }

    @Test
    fun accessIsAllowedWhenAndroidListsOurListener() {
        val ours = ComponentName(context, RingNotificationListener::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")
        assertThat(provider.accessAllowed()).isFalse()
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", ours)
        assertThat(provider.accessAllowed()).isTrue()
    }

    @Test
    fun onAndroid11TheScreenIsCulverysOwnToggle() {
        val intent = provider.accessScreen()
        assertThat(intent.action).isEqualTo(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        assertThat(intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME))
            .isEqualTo(ComponentName(context, RingNotificationListener::class.java).flattenToString())
    }

    @Config(sdk = [29])
    @Test
    fun onAndroid10ItIsTheList() {
        assertThat(provider.accessScreen().action).isEqualTo(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
```
Create `capability/door/src/test/java/uk/co/siland/culvery/capability/door/DoorRecorderTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
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
import uk.co.siland.culvery.core.plugin.WallClock

/** 5 §3.1, §4.2: the sink records in the order notices arrive, keeps last heard and the unrecognised run. */
@RunWith(AndroidJUnit4::class)
class DoorRecorderTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = doorDb()
    private val store by lazy { DoorStore(db, File(folder.root, "door")) }
    private val settings by lazy { DoorSettings(doorSettingsStore(scope) { File(folder.root, "s.preferences_pb") }, scope) }
    private var now = NOW
    private val recorder by lazy { DoorRecorder(store, settings, scope, WallClock { now }) }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    /** The text post and its snapshot re-post must not race: one event, not two. */
    @Test
    fun noticesAreRecordedInTheOrderTheyArrive() = runTest {
        recorder.post(ring())
        recorder.post(ring(at = NOW + 1_000, snapshot = aPicture))
        val events = store.events.first { e -> e.isNotEmpty() && e.single().snapshot != null }
        assertThat(events).hasSize(1)
    }

    @Test
    fun everyNoticeIsHeard() = runTest {
        recorder.heard(NOW)
        assertThat(settings.prefs.first { it.lastHeardMillis != null }.lastHeardMillis).isEqualTo(NOW)
    }

    @Test
    fun theRunCountsNewUnrecognisedAlertsAndARecognisedOneEndsIt() = runTest {
        recorder.post(ring(key = "a", unrecognised = true))
        recorder.post(ring(key = "a", at = NOW + 1_000, unrecognised = true))
        recorder.post(ring(key = "b", unrecognised = true))
        assertThat(settings.prefs.first { it.unrecognisedRun == 2 }.unrecognisedRun).isEqualTo(2)
        recorder.post(motion())
        assertThat(settings.prefs.first { it.unrecognisedRun == 0 }.unrecognisedRun).isEqualTo(0)
    }

    @Test
    fun capturingIsOnUntilItsEnd() = runTest {
        assertThat(recorder.capturing).isFalse()
        settings.setCapture(untilMillis = NOW + 1)
        settings.current.first { it.captureUntilMillis == NOW + 1 }
        assertThat(recorder.capturing).isTrue()
        now = NOW + 1
        assertThat(recorder.capturing).isFalse()
    }

    @Test
    fun bindingIsKept() {
        assertThat(recorder.bound.value).isNull()
        recorder.listening(false)
        assertThat(recorder.bound.value).isFalse()
    }
}
```
Fails today: `RingReader`, `RingProvider`, `FakeRings`, `SHELL_PACKAGE`, `TAG_RING`, `DoorRecorder` don't exist.
Fails if: another package or an unknown shell tag is read; an ignored notice isn't heard; the log carries the device; the capture logs while off; posts are recorded concurrently (two events); a re-post counts again in the run.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :provider:door-ring:testDebugUnitTest :capability:door:testDebugUnitTest --tests '*DoorRecorderTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The sink**

Create `capability/door/src/main/java/uk/co/siland/culvery/capability/door/DoorRecorder.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.WallClock

/**
 * The capability's [DoorEventSink] (5 §3.1, §4): records each notice in the order it arrived, keeps "last heard" and the
 * run of unrecognised alerts. Notices go through one queue, so a text post and its snapshot re-post can't race.
 */
@Singleton
class DoorRecorder @Inject constructor(
    private val store: DoorStore,
    private val settings: DoorSettings,
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: WallClock,
) : DoorEventSink {
    private val notices = Channel<DoorNotice>(Channel.UNLIMITED)

    private val _bound = MutableStateFlow<Boolean?>(null)

    /** Whether Android has the listener bound; null until it first says (5 §6). */
    val bound: StateFlow<Boolean?> = _bound.asStateFlow()

    init {
        scope.launch { for (notice in notices) record(notice) }
    }

    override fun heard(atMillis: Long) {
        scope.launch {
            try {
                settings.heard(atMillis)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't keep when Ring was last heard (${e::class.simpleName})")
            }
        }
    }

    override fun post(notice: DoorNotice) {
        notices.trySend(notice)
    }

    override fun listening(bound: Boolean) {
        _bound.value = bound
    }

    override val capturing: Boolean get() = settings.current.value.captureUntilMillis > clock.nowMillis()

    private suspend fun record(notice: DoorNotice) {
        val recorded = try {
            store.record(notice)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't record a door notice (${e::class.simpleName})")
            return
        }
        if (recorded.isNew) {
            try {
                settings.countAlert(recognised = !notice.unrecognised)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't count an alert (${e::class.simpleName})")
            }
        }
    }

    private companion object {
        const val TAG = "DoorRecorder"
    }
}
```
`DoorModule.kt` gains:
```kotlin
    @Binds
    abstract fun sink(impl: DoorRecorder): DoorEventSink
```

- [ ] **Step 4: The provider's side**

Create `provider/door-ring/src/debug/java/uk/co/siland/culvery/provider/door_ring/FakeRings.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

/**
 * Debug builds: the adb shell's notifications tagged [FAKE_RING_TAG] read as Ring's (5 §7), and the emulator, which has
 * no Ring app, counts as having one until DebugRingReceiver says otherwise (ruling 12).
 */
object FakeRings {
    const val ENABLED = true

    @Volatile var appPresent = true
}
```
Create `provider/door-ring/src/release/java/uk/co/siland/culvery/provider/door_ring/FakeRings.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

/** Release builds read Ring's own notifications only. */
object FakeRings {
    const val ENABLED = false

    val appPresent = false
}
```
Create `RingReader.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

import android.util.Log
import javax.inject.Inject
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.DoorEventSink
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorNotice

/** The adb shell, whose `cmd notification post` makes the debug build's fake rings (5 §7); a tag after the prefix makes a new key. */
const val SHELL_PACKAGE = "com.android.shell"
const val FAKE_RING_TAG = "culvery-fake-ring"

internal const val TAG_RING = "RingReader"

/** Between the listener and the Door capability (5 §4.1–4.2): Ring's notices only, placed and handed to the [sink]. */
class RingReader @Inject constructor(private val sink: DoorEventSink) {
    fun reads(packageName: String, tag: String?): Boolean =
        packageName == RING_PACKAGE || (FakeRings.ENABLED && packageName == SHELL_PACKAGE && tag?.startsWith(FAKE_RING_TAG) == true)

    fun read(post: RingPost) {
        val notice = post.notice
        sink.heard(notice.postTime)
        if (sink.capturing) {
            // The one log line with Ring's words in it (5 §6, §8.3): on for 30 minutes at most, by an Admin.
            val capture = RingCapture(notice, post.extraKeys)
            Log.println(Log.INFO, TAG_RING, "Ring notice: ${capture.line}")
        }
        val reading = RingParser.read(notice)
        // R8 strips Log.i and Log.d, and the tablet runs a release build (ruling 11).
        Log.println(Log.INFO, TAG_RING, "A Ring notice was read as ${reading.outcome}")
        val snapshot = post.picture?.let(::BitmapSnapshot)
        val door = when (reading) {
            is RingReading.Ring -> DoorNotice(notice.key, notice.postTime, DoorKind.RING, reading.device, snapshot = snapshot)
            is RingReading.Motion -> DoorNotice(notice.key, notice.postTime, DoorKind.MOTION, reading.device, snapshot = snapshot)
            is RingReading.UnknownRingAlert -> DoorNotice(notice.key, notice.postTime, DoorKind.RING, reading.device, unrecognised = true, snapshot = snapshot)
            RingReading.Ignore -> null
        }
        door?.let(sink::post)
    }

    fun listening(bound: Boolean) = sink.listening(bound)
}

/** A notice's whole shape for the capture: what a fixture is written from, names and all (5 §8.3). */
internal class RingCapture(private val notice: RingNotice, private val extraKeys: Set<String>) {
    val line: String get() = "$notice extras=${extraKeys.sorted()}"
}
```
Create `RingNotificationListener.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Culvery's notification access (5 §3.2), allowed once in Android's Settings. Returns at once for every app but Ring's;
 * removals are ignored (5 §4.1).
 */
@AndroidEntryPoint
class RingNotificationListener : NotificationListenerService() {
    @Inject lateinit var reader: RingReader

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!reader.reads(sbn.packageName, sbn.tag)) return
        reader.read(ringPost(sbn.packageName, sbn.key, sbn.postTime, sbn.notification, this))
    }

    override fun onListenerConnected() = reader.listening(true)

    /** Android unbinds a listener now and then; asking again costs nothing, and the status says if access is off (5 §6). */
    override fun onListenerDisconnected() {
        reader.listening(false)
        requestRebind(ComponentName(this, RingNotificationListener::class.java))
    }
}
```
Create `RingProvider.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.Icons

const val RING_PROVIDER_ID = "door.ring"

/** Connect Ring's first step's hint (5 §2.3, §2.11). */
internal const val RING_HINT = "Sign in as a Ring Shared User, and turn off the Ring app’s notification sound."

/** Ring through its app's notifications (5 §3.2): it never sees Ring credentials. */
@Singleton
class RingProvider @Inject constructor(@ApplicationContext private val context: Context) : DoorProvider {
    override val descriptor = ProviderDescriptor(RING_PROVIDER_ID, "Ring", Icons.DOORBELL, setOf(Feature.READ), connectHint = RING_HINT)

    /** Ruling 13: needs the manifest's `<queries>` from Android 11. */
    override fun appInstalled(): Boolean =
        context.packageManager.getLaunchIntentForPackage(RING_PACKAGE) != null || (FakeRings.ENABLED && FakeRings.appPresent)

    override fun accessAllowed(): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    override fun accessScreen(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, RingNotificationListener::class.java).flattenToString())
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
```
Create `di/RingModule.kt`:
```kotlin
package uk.co.siland.culvery.provider.door_ring.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.provider.door_ring.RingProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class RingModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: RingProvider): DoorProvider
}
```
Create `provider/door-ring/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 5 §5.5, ruling 13: from Android 11 an app is only seen as installed when it is named here. -->
    <queries>
        <package android:name="com.ringapp" />
    </queries>
    <application>
        <!-- 5 §3.2: Android binds it once the household allows notification access; nothing else can. -->
        <service
            android:name=".RingNotificationListener"
            android:exported="true"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```
`LogHygieneTest.kt` `LogHygiene.ALLOWED` gains `"capture.line", "reading.outcome",` with a line in its KDoc: "`capture.line` is Check Ring's notifications, the spec's one exception (5 §6), off by default and on for 30 minutes at most."

- [ ] **Step 5: The debug receiver**

Create `app/src/debug/java/uk/co/siland/culvery/DebugRingReceiver.kt`:
```kotlin
package uk.co.siland.culvery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uk.co.siland.culvery.provider.door_ring.FakeRings

/**
 * Debug builds only: whether the emulator counts as having the Ring app (ruling 12), so the walk-through can see "the
 * Ring app isn't on this tablet": `adb shell am broadcast -n uk.co.siland.culvery/.DebugRingReceiver --ez ring_app false`
 */
class DebugRingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A mistyped command must not hide the app by default.
        if (!intent.hasExtra(EXTRA_RING_APP)) return
        FakeRings.appPresent = intent.getBooleanExtra(EXTRA_RING_APP, true)
    }

    companion object {
        const val EXTRA_RING_APP = "ring_app"
    }
}
```
`app/src/debug/AndroidManifest.xml` gains, after the offline receiver:
```xml
        <!-- Debug walk-through only: adb says whether the emulator has the Ring app. The sender needs DUMP, as above. -->
        <receiver
            android:name=".DebugRingReceiver"
            android:exported="true"
            android:permission="android.permission.DUMP" />
```

- [ ] **Step 6: Run them to see them pass**

Run: `./gradlew --continue :provider:door-ring:testDebugUnitTest :capability:door:testDebugUnitTest :app:testDebugUnitTest` and `./gradlew -p build-logic :convention:test --tests '*LogHygieneTest*'`.
Expected: PASS. Then `./gradlew :provider:door-ring:compileReleaseKotlin`: the release `FakeRings` compiles too.

- [ ] **Step 7: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add provider/door-ring capability/door app/src/debug build-logic/convention/src/test/kotlin/LogHygieneTest.kt
git commit -m "Listen for Ring's notifications and record what they say"
```

---
### Task 12: The door card (§5.1)

**Review:** sonnet.

**Files:**
- Create (under `capability/door/src/main/java/uk/co/siland/culvery/capability/door/`): `ui/DoorCard.kt`, `ui/DoorDimens.kt`, `ui/Snapshots.kt`
- Modify: `DoorWords.kt` (the card's words, `clockTime`)
- Test: `capability/door/src/test/java/uk/co/siland/culvery/capability/door/ui/DoorCardTest.kt`, `ui/DoorCardScreenshotTest.kt`
- Baselines (new, `:capability:door`): `door_card_dark`, `door_card_light`, `door_card_snapshot_dark`, `door_card_unrecognised_dark`.

**Interfaces:**
- Consumes: `BitmapSnapshot`, `picture()` (Task 9's test helper), `Icons.DOORBELL`.
- Produces:
  - `data class DoorCardUi(val unrecognised: Boolean, val device: String, val time: String, val snapshot: File?)` (package `…capability.door.ui`)
  - `@Composable fun DoorCard(card: DoorCardUi, onGotIt: () -> Unit, modifier: Modifier = Modifier)`; tags `door_card`, `door_card_words`, `door_snapshot`, `door_got_it`
  - `@Composable fun SnapshotImage(file: File, modifier: Modifier = Modifier)`; `@Composable fun rememberSnapshot(file: File): ImageBitmap?`
  - In `DoorWords.kt`: `SOMEONE_AT_THE_DOOR`, `RING_ALERT`, `GOT_IT`, `fun cardWords(title: String, device: String, time: String): String`, `fun clockTime(atMillis: Long, zone: ZoneId): String` ("HH:mm")
  - `internal object DoorDimens` (grown by later tasks)

- [ ] **Step 1: Write the failing tests**

Create `capability/door/src/test/java/uk/co/siland/culvery/capability/door/ui/DoorCardTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.picture
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

/** 5 §5.1: what, where and when, spoken at once; the snapshot when there is one; Got it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoorCardTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private var gotIt = 0

    private fun show(card: DoorCardUi) = compose.setContent { CulveryTheme(dark = true) { DoorCard(card, onGotIt = { gotIt++ }) } }

    @Test
    fun theCardSaysWhatWhereAndWhenUrgently() {
        show(DoorCardUi(unrecognised = false, device = "Front Door", time = "14:02", snapshot = null))
        compose.onNodeWithTag("door_card_words")
            .assert(hasContentDescription("Someone’s at the door, Front Door, 14:02"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
        compose.assertAccessible()
    }

    @Test
    fun anUnrecognisedAlertSaysRingAlertAndAMissingDeviceIsLeftOut() {
        show(DoorCardUi(unrecognised = true, device = "", time = "14:02", snapshot = null))
        compose.onNodeWithTag("door_card_words").assert(hasContentDescription("Ring alert, 14:02"))
    }

    @Test
    fun gotItCallsBack() {
        show(DoorCardUi(false, "Front Door", "14:02", null))
        compose.onNodeWithTag("door_got_it").performClick()
        assertThat(gotIt).isEqualTo(1)
    }

    @Test
    fun theSnapshotShowsWhenThereIsOne() {
        val file = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(640, 360)).saveTo(it) }
        show(DoorCardUi(false, "Front Door", "14:02", file))
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("door_snapshot").fetchSemanticsNodes().isNotEmpty() }
    }
}
```
Create `ui/DoorCardScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.picture
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The card at half the 1280 dp screen, as the alert layer places it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoorCardScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private fun snap(name: String, dark: Boolean, card: DoorCardUi) {
        compose.setContent { CulveryTheme(dark = dark) { Box(Modifier.width(640.dp)) { DoorCard(card, onGotIt = {}) } } }
        if (card.snapshot != null) compose.waitUntil(5_000) { compose.onAllNodesWithTag("door_snapshot").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val ring = DoorCardUi(unrecognised = false, device = "Front Door", time = "14:02", snapshot = null)

    @Test fun cardDark() = snap("door_card_dark", true, ring)
    @Test fun cardLight() = snap("door_card_light", false, ring)
    @Test fun cardSnapshotDark() = snap("door_card_snapshot_dark", true, ring.copy(snapshot = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(640, 360)).saveTo(it) }))
    @Test fun cardUnrecognisedDark() = snap("door_card_unrecognised_dark", true, ring.copy(unrecognised = true))
}
```
Fails today: `DoorCard` and `DoorCardUi` don't exist.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorCardTest*'`
Expected: FAIL to compile on `DoorCard`.

- [ ] **Step 3: The card**

`DoorWords.kt` gains (imports `java.time.Instant`, `java.time.ZoneId`, `java.time.format.DateTimeFormatter`, `java.util.Locale`):
```kotlin
internal const val SOMEONE_AT_THE_DOOR = "Someone’s at the door"
internal const val RING_ALERT = "Ring alert"
internal const val GOT_IT = "Got it"

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/** "14:02" on the household's clock. */
fun clockTime(atMillis: Long, zone: ZoneId): String = Instant.ofEpochMilli(atMillis).atZone(zone).format(CLOCK)

/** The card's spoken words: what, where, when (5 §5.1), leaving out a device Ring didn't name. */
fun cardWords(title: String, device: String, time: String): String = listOf(title, device, time).filter { it.isNotEmpty() }.joinToString(", ")
```
Create `ui/DoorDimens.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.ui.ShellTokens

/** The door's layout values (5 §5); provisional until the end-of-v1 design walk-through. */
internal object DoorDimens {
    // The card: `surf` with a 1 dp `line` border, as Home's cards' radius; padding 22, 16 between blocks; a 34 dp doorbell 14 from its words.
    val cardRadius = ShellTokens.homeCardRadius
    val cardBorder = 1.dp
    val cardPadding = 22.dp
    val cardGap = 16.dp
    val cardIcon = 34.dp
    val cardIconGap = 14.dp

    // A snapshot at 16:9, radius 16.
    const val SNAPSHOT_RATIO = 16f / 9f
    val snapshotRadius = 16.dp
}
```
Create `ui/Snapshots.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [file] decoded off the main thread (ruling 29); null until it is, or when it can't be read (pruned meanwhile). */
@Composable
fun rememberSnapshot(file: File): ImageBitmap? =
    produceState<ImageBitmap?>(null, file) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() } }.value

/** A ring's picture, cropped to [modifier]'s size; the words beside it say what it is. */
@Composable
fun SnapshotImage(file: File, modifier: Modifier = Modifier) {
    val image = rememberSnapshot(file) ?: return
    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}
```
Create `ui/DoorCard.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.io.File
import uk.co.siland.culvery.capability.door.GOT_IT
import uk.co.siland.culvery.capability.door.RING_ALERT
import uk.co.siland.culvery.capability.door.SOMEONE_AT_THE_DOOR
import uk.co.siland.culvery.capability.door.cardWords
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

/** What the door card shows (5 §5.1). [device] may be empty; [snapshot] is the saved picture, if any. */
data class DoorCardUi(val unrecognised: Boolean, val device: String, val time: String, val snapshot: File?)

/**
 * The door card (5 §5.1): the doorbell, what happened, the device as Ring names it, the time, the snapshot, and Got it.
 * TalkBack says its words at once. It swallows its own taps, so nothing beneath hears them.
 */
@Composable
fun DoorCard(card: DoorCardUi, onGotIt: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(DoorDimens.cardRadius)
    val title = if (card.unrecognised) RING_ALERT else SOMEONE_AT_THE_DOOR
    Column(
        verticalArrangement = Arrangement.spacedBy(DoorDimens.cardGap),
        modifier = modifier
            .testTag("door_card")
            .fillMaxWidth()
            .clip(shape)
            .background(c.surf)
            .border(DoorDimens.cardBorder, c.line, shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(DoorDimens.cardPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DoorDimens.cardIconGap),
            modifier = Modifier.testTag("door_card_words").semantics(mergeDescendants = true) {
                contentDescription = cardWords(title, card.device, card.time)
                liveRegion = LiveRegionMode.Assertive
            },
        ) {
            HhIcon(Icons.DOORBELL, size = DoorDimens.cardIcon, tint = c.accent)
            Column(Modifier.weight(1f)) {
                Text(title, style = HhType.sectionTitle, color = c.ink)
                Text(listOf(card.device, card.time).filter { it.isNotEmpty() }.joinToString(" · "), style = HhType.body, color = c.mute)
            }
        }
        card.snapshot?.let {
            SnapshotImage(
                it,
                Modifier.testTag("door_snapshot").fillMaxWidth().aspectRatio(DoorDimens.SNAPSHOT_RATIO).clip(RoundedCornerShape(DoorDimens.snapshotRadius)),
            )
        }
        HhButton(GOT_IT, onGotIt, Modifier.fillMaxWidth().testTag("door_got_it"), tone = ButtonTone.Primary, size = ButtonSize.Large, on = ButtonSurface.Surf)
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorCardTest*'`
Expected: PASS.

- [ ] **Step 5: Screenshots**

`./gradlew :capability:door:recordRoborazziDebug --tests '*DoorCardScreenshotTest*'`; the four names in `$TMP/expected-baselines.txt`; the baseline check; open all four.

- [ ] **Step 6: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/door
git commit -m "Draw the door card"
```

---

### Task 13: The alert decision, quiet hours and the chime (§4.1, §4.6, §4.7; rulings 5, 26, 27; Review Focus 1, 2, 4)

**Review:** opus.

**Files:**
- Create: `tools/sound/door_chime.py`, `capability/door/src/main/res/raw/door_chime.wav` (generated)
- Create (door package): `QuietHours.kt`, `Chime.kt`, `DoorAlerts.kt`, `ui/DoorCardHost.kt`
- Modify: `DoorRecorder.kt` (calls the decision), `di/DoorModule.kt` (bind the chime)
- Test: `capability/door/src/test/.../QuietHoursTest.kt`, `DoorAlertsTest.kt`, `ChimeSoundTest.kt`; `TestDoor.kt` (fakes); `DoorRecorderTest.kt` (its constructor)

**Interfaces:**
- Consumes: `AlertHost` (Task 7); `DoorStore`, `Recorded`, `DoorPrefs`, `DoorSettings`, `DoorKind` (Task 9); `DoorRecorder` (Task 11); `DoorCard`, `DoorCardUi`, `clockTime` (Task 12).
- Produces:
  - `fun isQuiet(now: LocalTime, from: LocalTime, until: LocalTime): Boolean`; `internal fun quietAt(atMillis: Long, zone: ZoneId, prefs: DoorPrefs): Boolean`
  - `fun interface Chime { fun play(volume: Float) }`; `class MediaPlayerChime @Inject constructor(@ApplicationContext context: Context) : Chime`
  - `const val CARD_MS = 120_000L`, `const val CHIME_GAP_MS = 10_000L`; `data class ShowingRing(val eventId: Long, val card: DoorCardUi)`
  - `@Singleton class DoorAlerts`: `val showing: StateFlow<ShowingRing?>`, `suspend fun onRecorded(recorded: Recorded)`, `fun gotIt()`; internal constructor `(host: AlertHost, store: DoorStore, prefs: () -> DoorPrefs, chime: Chime, zone: suspend () -> ZoneId, clock: WallClock, scope: CoroutineScope)`
  - `@Composable fun DoorCardHost(alerts: DoorAlerts)`
  - `DoorRecorder` internal constructor gains, last, `alert: suspend (Recorded) -> Unit`; its `@Inject` constructor takes `alerts: DoorAlerts`.

- [ ] **Step 1: The chime's sound**

Create `tools/sound/door_chime.py`:
```python
#!/usr/bin/env python3
"""Writes Culvery's door chime (5 §4.1): two soft bell tones, E5 then C5, 1.6 s, as a 22.05 kHz 16-bit mono WAV.

Standard library only. Culvery's own sound, under the repository's licence: no third-party audio.
Usage, from the repo root: python tools/sound/door_chime.py
Writes: capability/door/src/main/res/raw/door_chime.wav
"""
import math
import pathlib
import struct
import wave

RATE = 22050
LENGTH = 1.6
DECAY = 3.0  # per second
# (frequency in Hz, start in seconds)
NOTES = [(659.25, 0.0), (523.25, 0.45)]
OUT = pathlib.Path(__file__).resolve().parents[2] / "capability" / "door" / "src" / "main" / "res" / "raw" / "door_chime.wav"


def sample(t):
    value = 0.0
    for freq, start in NOTES:
        if t >= start:
            u = t - start
            # The note and a quieter octave, fading: a bell rather than a beep.
            value += (math.sin(2 * math.pi * freq * u) + 0.3 * math.sin(4 * math.pi * freq * u)) * math.exp(-DECAY * u)
    return value


def main():
    frames = [sample(i / RATE) for i in range(int(LENGTH * RATE))]
    peak = max(abs(f) for f in frames)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(f / peak * 0.8 * 32767)) for f in frames))


if __name__ == "__main__":
    main()
```
Run: `python tools/sound/door_chime.py` — Expected: `capability/door/src/main/res/raw/door_chime.wav`, about 70 KB.

- [ ] **Step 2: Write the failing tests**

`TestDoor.kt` gains (imports `androidx.compose.runtime.Composable`, `kotlinx.coroutines.flow.MutableStateFlow`, `uk.co.siland.culvery.core.plugin.AlertHost`):
```kotlin
/** An alert host a test drives: whether Culvery is in front, and what was shown and dismissed. */
internal class FakeAlertHost(inFront: Boolean = true) : AlertHost {
    override val inFront = MutableStateFlow(inFront)
    var shown = 0
    var dismissed = 0
    var content: (@Composable () -> Unit)? = null

    override fun show(content: @Composable () -> Unit) {
        shown++
        this.content = content
    }

    override fun dismiss() {
        dismissed++
        content = null
    }
}

internal class FakeChime : Chime {
    val volumes = mutableListOf<Float>()
    override fun play(volume: Float) {
        volumes += volume
    }
}
```
Create `QuietHoursTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

/** 5 §4.1: From 21:00, Until 07:00 means 21:00–06:59, on the household's wall clock. */
class QuietHoursTest {
    private val from = LocalTime.of(21, 0)
    private val until = LocalTime.of(7, 0)
    private fun t(text: String) = LocalTime.parse(text)

    @Test
    fun acrossMidnightFromItsStartUntilJustBeforeItsEnd() {
        assertThat(listOf("20:59", "21:00", "23:59", "00:00", "06:59", "07:00").map { isQuiet(t(it), from, until) })
            .containsExactly(false, true, true, true, true, false).inOrder()
    }

    @Test
    fun withinADay() {
        assertThat(listOf("12:59", "13:00", "14:59", "15:00").map { isQuiet(t(it), t("13:00"), t("15:00")) })
            .containsExactly(false, true, true, false).inOrder()
    }

    /** Ruling 27. */
    @Test
    fun fromEqualToUntilIsNeverQuiet() = assertThat(isQuiet(t("21:00"), t("21:00"), t("21:00"))).isFalse()

    @Test
    fun switchedOffIsNeverQuiet() {
        val london = ZoneId.of("Europe/London")
        assertThat(quietAt(Instant.parse("2026-10-14T22:00:00Z").toEpochMilli(), london, DoorPrefs(quietOn = false))).isFalse()
    }

    /** Review Focus 2: 01:30 happens twice on 25 October and is quiet both times; 07:00 BST ends it on 29 March. */
    @Test
    fun quietHoursFollowTheWallClockOnTheNightsTheClocksChange() {
        val london = ZoneId.of("Europe/London")
        fun at(utc: String) = quietAt(Instant.parse(utc).toEpochMilli(), london, DoorPrefs())
        assertThat(at("2026-10-25T00:30:00Z")).isTrue()
        assertThat(at("2026-10-25T01:30:00Z")).isTrue()
        assertThat(at("2026-10-25T06:59:00Z")).isTrue()
        assertThat(at("2026-10-25T07:00:00Z")).isFalse()
        assertThat(at("2026-03-28T20:59:00Z")).isFalse()
        assertThat(at("2026-03-28T21:00:00Z")).isTrue()
        assertThat(at("2026-03-29T05:59:00Z")).isTrue()
        assertThat(at("2026-03-29T06:00:00Z")).isFalse()
    }
}
```
Create `DoorAlertsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.WallClock

/** 5 §4.6–4.7: card and chime for a new ring in front; record only otherwise; 2 minutes, Got it, quiet hours, 10 seconds. */
@RunWith(AndroidJUnit4::class)
class DoorAlertsTest {
    @get:Rule val folder = TemporaryFolder()
    private val london = ZoneId.of("Europe/London")
    private val db = doorDb()
    private val store by lazy { DoorStore(db, File(folder.root, "door")) }
    private val host = FakeAlertHost()
    private val chime = FakeChime()
    private var prefs = DoorPrefs()

    // NOW is 14:02 in London: not quiet.
    private var start = NOW

    @After
    fun tearDown() = db.close()

    private fun TestScope.alerts() =
        DoorAlerts(host, store, { prefs }, chime, { london }, WallClock { start + testScheduler.currentTime }, backgroundScope)

    private suspend fun DoorAlerts.hear(notice: DoorNotice) = onRecorded(store.record(notice))

    private fun TestScope.at(offset: Long) = start + testScheduler.currentTime + offset

    @Test
    fun aNewRingInFrontShowsTheCardAndChimesAtTheVolume() = runTest {
        prefs = DoorPrefs(volume = 0.4f)
        val alerts = alerts()
        alerts.hear(ring(at = at(0)))
        assertThat(host.shown).isEqualTo(1)
        assertThat(alerts.showing.value?.card).isEqualTo(uk.co.siland.culvery.capability.door.ui.DoorCardUi(false, "Front Door", "14:02", null))
        assertThat(chime.volumes).containsExactly(0.4f)
    }

    @Test
    fun motionNeverAlerts() = runTest {
        val alerts = alerts()
        alerts.hear(motion(at = at(0)))
        assertThat(host.shown).isEqualTo(0)
        assertThat(chime.volumes).isEmpty()
    }

    @Test
    fun anUnrecognisedAlertShowsAsARingAlertAndChimes() = runTest {
        val alerts = alerts()
        alerts.hear(ring(at = at(0), unrecognised = true))
        assertThat(alerts.showing.value?.card?.unrecognised).isTrue()
        assertThat(chime.volumes).hasSize(1)
    }

    /** 5 §2.9, §4.6: a second ring refreshes the card; the chime waits 10 seconds after the last. */
    @Test
    fun aSecondRingRefreshesTheCardAndChimesAgainOnlyAfterTenSeconds() = runTest {
        val alerts = alerts()
        alerts.hear(ring(key = "a", at = at(0)))
        advanceTimeBy(CHIME_GAP_MS - 1)
        val second = store.record(ring(key = "b", at = at(0), device = "Back Door"))
        alerts.onRecorded(second)
        assertThat(alerts.showing.value?.eventId).isEqualTo(second.event.id)
        assertThat(alerts.showing.value?.card?.device).isEqualTo("Back Door")
        assertThat(chime.volumes).hasSize(1)
        advanceTimeBy(1)
        alerts.hear(ring(key = "c", at = at(0)))
        assertThat(chime.volumes).hasSize(2)
        assertThat(host.shown).isEqualTo(1)
    }

    @Test
    fun theCardClosesAfterTwoMinutesAndItsRingStaysUnseen() = runTest {
        val alerts = alerts()
        alerts.hear(ring(at = at(0)))
        advanceTimeBy(CARD_MS - 1)
        runCurrent()
        assertThat(alerts.showing.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(alerts.showing.value).isNull()
        assertThat(host.dismissed).isEqualTo(1)
        assertThat(store.unseen.first().rings).isEqualTo(1)
    }

    @Test
    fun aSecondRingRestartsTheTwoMinutes() = runTest {
        val alerts = alerts()
        alerts.hear(ring(key = "a", at = at(0)))
        advanceTimeBy(60_000)
        alerts.hear(ring(key = "b", at = at(0)))
        advanceTimeBy(CARD_MS - 1)
        runCurrent()
        assertThat(alerts.showing.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(alerts.showing.value).isNull()
    }

    @Test
    fun gotItClosesTheCardAndMarksItsRingSeen() = runTest {
        val alerts = alerts()
        alerts.hear(ring(at = at(0)))
        alerts.gotIt()
        advanceUntilIdle()
        assertThat(alerts.showing.value).isNull()
        assertThat(host.dismissed).isEqualTo(1)
        assertThat(store.unseen.first { it.rings == 0 }.rings).isEqualTo(0)
    }

    /** 5 §2.6: in quiet hours the card still shows; no chime. 22:00 BST. */
    @Test
    fun quietHoursKeepTheCardButNotTheChime() = runTest {
        start = NOW + 8 * 60 * 60_000L - 2 * 60_000L
        val alerts = alerts()
        alerts.hear(ring(at = at(0)))
        assertThat(host.shown).isEqualTo(1)
        assertThat(chime.volumes).isEmpty()
    }

    /** Review Focus 4: stored and counted while Culvery is behind another app; coming back raises nothing. */
    @Test
    fun aRingInTheBackgroundShowsNoCardWhenCulveryReturns() = runTest {
        host.inFront.value = false
        val alerts = alerts()
        alerts.hear(ring(at = at(0)))
        host.inFront.value = true
        advanceUntilIdle()
        assertThat(host.shown).isEqualTo(0)
        assertThat(chime.volumes).isEmpty()
        assertThat(store.unseen.first().rings).isEqualTo(1)
    }

    /** Review Focus 1: Ring's text post, then the same key with the snapshot: one card, its picture, one chime, the same 2 minutes. */
    @Test
    fun aSnapshotRepostUpdatesTheShowingCardWithoutAChime() = runTest {
        val alerts = alerts()
        alerts.hear(ring(at = at(0)))
        advanceTimeBy(30_000)
        alerts.hear(ring(at = at(0), snapshot = aPicture))
        assertThat(alerts.showing.value?.card?.snapshot).isNotNull()
        assertThat(chime.volumes).hasSize(1)
        assertThat(host.shown).isEqualTo(1)
        advanceTimeBy(CARD_MS - 30_000 - 1)
        runCurrent()
        assertThat(alerts.showing.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(alerts.showing.value).isNull()
    }
}
```
Create `ChimeSoundTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

/** The bundled chime (ruling 5): a short mono 16-bit WAV, as tools/sound/door_chime.py writes it. */
class ChimeSoundTest {
    // Unit tests run in the module's directory.
    private val bytes = File("src/main/res/raw/door_chime.wav").readBytes()
    private val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun itIsAShortMonoSixteenBitWav() {
        assertThat(String(bytes, 0, 4)).isEqualTo("RIFF")
        assertThat(String(bytes, 8, 4)).isEqualTo("WAVE")
        assertThat(header.getShort(22).toInt()).isEqualTo(1)
        val rate = header.getInt(24)
        assertThat(header.getShort(34).toInt()).isEqualTo(16)
        val seconds = header.getInt(40).toDouble() / (rate * 2)
        assertThat(seconds).isAtLeast(1.0)
        assertThat(seconds).isAtMost(2.0)
    }
}
```
In `DoorRecorderTest.kt`, the recorder becomes `DoorRecorder(store, settings, scope, WallClock { now }) { recordedForAlerts += it }` with `private val recordedForAlerts = java.util.concurrent.CopyOnWriteArrayList<Recorded>()`, and add:
```kotlin
    @Test
    fun eachRecordGoesToTheAlertDecision() = runTest {
        recorder.post(ring())
        store.events.first { it.isNotEmpty() }
        assertThat(recordedForAlerts.map { it.isNew }).containsExactly(true)
    }
```
Fails today: `isQuiet`, `quietAt`, `Chime`, `DoorAlerts`, the WAV and the recorder's alert parameter don't exist.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :capability:door:testDebugUnitTest --tests '*QuietHoursTest*' --tests '*DoorAlertsTest*' --tests '*ChimeSoundTest*' --tests '*DoorRecorderTest*'`
Expected: FAIL to compile.

- [ ] **Step 4: Quiet hours and the chime**

Create `QuietHours.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Quiet from [from] until just before [until], across midnight when [until] is earlier (5 §4.1); never when they're equal. */
fun isQuiet(now: LocalTime, from: LocalTime, until: LocalTime): Boolean = when {
    from == until -> false
    from < until -> now >= from && now < until
    else -> now >= from || now < until
}

/** Whether [atMillis] is in [prefs]' quiet hours on the household's wall clock: a repeated autumn hour is quiet both times. */
internal fun quietAt(atMillis: Long, zone: ZoneId, prefs: DoorPrefs): Boolean =
    prefs.quietOn && isQuiet(Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime(), prefs.quietFrom, prefs.quietUntil)
```
Create `Chime.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface Chime {
    /** [volume] 0–1 scales the sound within the tablet's own notification volume. */
    fun play(volume: Float)
}

/**
 * The bundled chime as a notification event (5 §4.1), so the tablet's notification volume and Do Not Disturb still apply.
 * Each play is its own player, released when it ends.
 */
class MediaPlayerChime @Inject constructor(@ApplicationContext private val context: Context) : Chime {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun play(volume: Float) {
        val player = MediaPlayer.create(context, R.raw.door_chime, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
        if (player == null) {
            Log.w(TAG, "The door chime couldn't be loaded")
            return
        }
        player.setVolume(volume, volume)
        player.setOnCompletionListener { it.release() }
        player.start()
    }

    private companion object {
        const val TAG = "MediaPlayerChime"
    }
}
```
`DoorModule.kt` gains `@Binds abstract fun chime(impl: MediaPlayerChime): Chime`.

- [ ] **Step 5: The decision**

Create `DoorAlerts.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.capability.door.ui.DoorCardHost
import uk.co.siland.culvery.capability.door.ui.DoorCardUi
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.WallClock

/** A card closes by itself after this long (5 §2.9). */
const val CARD_MS = 120_000L

/** A chime waits this long after the last one (5 §4.6). */
const val CHIME_GAP_MS = 10_000L

/** The ring the card is showing. */
data class ShowingRing(val eventId: Long, val card: DoorCardUi)

/**
 * The alert decision (5 §4.6–4.7). A new ring, or an unrecognised alert, while Culvery is in front raises the card or
 * refreshes it, restarts its 2 minutes, and chimes unless it is quiet hours or the last chime was under 10 seconds ago.
 * A re-post of the card's own ring updates the card only. Motion, and anything while Culvery isn't in front, is only stored.
 */
@Singleton
class DoorAlerts internal constructor(
    private val host: AlertHost,
    private val store: DoorStore,
    private val prefs: () -> DoorPrefs,
    private val chime: Chime,
    private val zone: suspend () -> ZoneId,
    private val clock: WallClock,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        host: AlertHost,
        store: DoorStore,
        settings: DoorSettings,
        chime: Chime,
        zone: HouseholdZone,
        clock: WallClock,
        @ApplicationScope scope: CoroutineScope,
    ) : this(host, store, { settings.current.value }, chime, zone::current, clock, scope)

    private val lock = Mutex()
    private val _showing = MutableStateFlow<ShowingRing?>(null)
    val showing: StateFlow<ShowingRing?> = _showing.asStateFlow()
    private var closing: Job? = null
    private var lastChimeMillis: Long? = null

    suspend fun onRecorded(recorded: Recorded) = lock.withLock {
        val event = recorded.event
        if (event.kind != DoorKind.RING) return@withLock
        val z = zone()
        val card = DoorCardUi(event.unrecognised, event.device, clockTime(event.atMillis, z), event.snapshot)
        if (!recorded.isNew) {
            if (_showing.value?.eventId == event.id) _showing.value = ShowingRing(event.id, card)
            return@withLock
        }
        if (!host.inFront.value) return@withLock
        val alreadyShowing = _showing.value != null
        _showing.value = ShowingRing(event.id, card)
        if (!alreadyShowing) host.show { DoorCardHost(this@DoorAlerts) }
        closing?.cancel()
        closing = scope.launch {
            delay(CARD_MS)
            close()
        }
        val now = clock.nowMillis()
        val p = prefs()
        // A clock set back since the last chime counts as long ago.
        val chimedRecently = lastChimeMillis?.let { now - it in 0 until CHIME_GAP_MS } == true
        if (!quietAt(now, z, p) && !chimedRecently) {
            lastChimeMillis = now
            chime.play(p.volume)
        }
    }

    /** Got it: the card goes and its ring counts as seen (5 §4.7). */
    fun gotIt() {
        val ring = _showing.value ?: return
        closing?.cancel()
        close()
        scope.launch { store.markSeen(ring.eventId) }
    }

    private fun close() {
        _showing.value = null
        host.dismiss()
    }
}
```
Create `ui/DoorCardHost.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.door.DoorAlerts

/** The card on the alert layer, as [alerts] has it. */
@Composable
fun DoorCardHost(alerts: DoorAlerts) {
    val showing by alerts.showing.collectAsStateWithLifecycle()
    showing?.let { DoorCard(it.card, onGotIt = alerts::gotIt) }
}
```
`DoorRecorder.kt`: the class header becomes
```kotlin
@Singleton
class DoorRecorder internal constructor(
    private val store: DoorStore,
    private val settings: DoorSettings,
    private val scope: CoroutineScope,
    private val clock: WallClock,
    private val alert: suspend (Recorded) -> Unit,
) : DoorEventSink {
    @Inject
    constructor(store: DoorStore, settings: DoorSettings, @ApplicationScope scope: CoroutineScope, clock: WallClock, alerts: DoorAlerts) :
        this(store, settings, scope, clock, alerts::onRecorded)
```
and `record` ends with:
```kotlin
        try {
            alert(recorded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't alert on a door notice (${e::class.simpleName})")
        }
```

- [ ] **Step 6: Run them to see them pass**

Run: `./gradlew :capability:door:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add tools/sound/door_chime.py capability/door
git commit -m "Show the door card and chime for a ring, with quiet hours"
```

---

### Task 14: The Door tab (§5.2; ruling 25; Review Focus 5)

**Review:** sonnet.

**Files:**
- Create (door package): `DoorDays.kt`, `ui/DoorTab.kt`, `ui/DoorParts.kt`
- Modify: `DoorWords.kt`, `ui/DoorDimens.kt`, `DoorCapability.kt` (`TabContent`; the clock and zone)
- Test: `capability/door/src/test/.../DoorDaysTest.kt`, `ui/DoorTabTest.kt`, `ui/DoorTabScreenshotTest.kt`; `DoorCapabilityTest.kt` (its constructor); `TestDoor.kt` (`householdZone()`)
- Baselines (new): `door_tab_dark`, `door_tab_light`, `door_tab_empty_dark`, `door_tab_status_dark`.

**Interfaces:**
- Consumes: `DoorStore`, `DoorEvent`, `Unseen` (Task 9); `SnapshotImage`, `clockTime`, `RING_ALERT`, `DoorDimens` (Task 12); `HouseholdClock.today`, `HouseholdZone.zone`, `LocalOverlayHost`.
- Produces:
  - `data class DoorDays(val today: List<DoorEvent>, val yesterday: List<DoorEvent>)`; `fun doorDays(events: List<DoorEvent>, today: LocalDate, zone: ZoneId): DoorDays`
  - `@Composable fun DoorTab(days: DoorDays, zone: ZoneId, status: String?, onOpenSnapshot: (DoorEvent) -> Unit, modifier: Modifier = Modifier)`; tags `door_today`, `door_yesterday`, `door_empty`, `door_status`, `door_row_<id>`, `door_thumb_<id>`
  - `@Composable internal fun DoorTabHost(store: DoorStore, today: Flow<LocalDate>, zone: Flow<ZoneId>, status: Flow<String?>)`
  - `@Composable fun SnapshotSheet(event: DoorEvent, zone: ZoneId, onClose: () -> Unit)`
  - `@Composable internal fun DoorStatusLine(text: String)` (in `ui/DoorParts.kt`)
  - In `DoorWords.kt`: `fun kindLabel(event: DoorEvent): String`, `TODAY`, `YESTERDAY`, `NO_RINGS_TODAY`, `SNAPSHOT`, `SHOW_FULL_SIZE`
  - `DoorCapability` constructor gains `clock: HouseholdClock, zone: HouseholdZone`.

- [ ] **Step 1: Write the failing tests**

`TestDoor.kt` gains:
```kotlin
/** The household's zone with no home set: the device's own, as on a fresh install. */
internal fun householdZone(): HouseholdZone = HouseholdZone(
    HouseholdRepository(
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java).allowMainThreadQueries().build(),
    ),
)
```
(imports `uk.co.siland.culvery.core.household.HouseholdRepository`, `uk.co.siland.culvery.core.household.HouseholdZone`, `uk.co.siland.culvery.core.household.db.HouseholdDatabase`).
Create `DoorDaysTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/** 5 §5.2: Today and Yesterday by the household's dates, newest first. */
class DoorDaysTest {
    private val london = ZoneId.of("Europe/London")
    private var ids = 0L

    private fun at(local: String) =
        DoorEvent(++ids, LocalDateTime.parse(local).atZone(london).toInstant().toEpochMilli(), DoorKind.RING, "", false, null, false)

    /** Review Focus 5: at midnight today's rows are yesterday's, and the day before's (still kept) leave the tab. */
    @Test
    fun atMidnightTodaysRowsBecomeYesterdaysAndOlderOnesLeave() {
        val late = at("2026-10-14T23:59")
        val morning = at("2026-10-14T07:00")
        val dayBefore = at("2026-10-13T23:00")
        val older = at("2026-10-12T23:30")
        val events = listOf(late, morning, dayBefore, older)
        assertThat(doorDays(events, LocalDate.of(2026, 10, 14), london)).isEqualTo(DoorDays(listOf(late, morning), listOf(dayBefore)))
        assertThat(doorDays(events, LocalDate.of(2026, 10, 15), london)).isEqualTo(DoorDays(emptyList(), listOf(late, morning)))
    }

    /** 00:30 BST on the 15th is 23:30 UTC on the 14th: the household's date wins. */
    @Test
    fun daysAreTheHouseholdsNotUtc() {
        val justAfterMidnight = at("2026-10-15T00:30")
        assertThat(doorDays(listOf(justAfterMidnight), LocalDate.of(2026, 10, 15), london).today).containsExactly(justAfterMidnight)
    }
}
```
Create `ui/DoorTabTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.door.DoorDays
import uk.co.siland.culvery.capability.door.DoorEvent
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorStore
import uk.co.siland.culvery.capability.door.NOW
import uk.co.siland.culvery.capability.door.doorDb
import uk.co.siland.culvery.capability.door.ring
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

@RunWith(AndroidJUnit4::class)
class DoorTabTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val london = ZoneId.of("Europe/London")
    private val db = doorDb()
    private val store by lazy { DoorStore(db, File(folder.root, "door")) }
    private val opened = mutableListOf<DoorEvent>()

    @After
    fun tearDown() = db.close()

    private fun event(id: Long, kind: DoorKind = DoorKind.RING, unrecognised: Boolean = false, snapshot: File? = null, at: Long = NOW - id * 60_000) =
        DoorEvent(id, at, kind, "Front Door", unrecognised, snapshot, seen = false)

    private fun show(days: DoorDays, status: String? = null) =
        compose.setContent { CulveryTheme(dark = true) { DoorTab(days, london, status, onOpenSnapshot = { opened += it }) } }

    @Test
    fun eachRowSaysTheTimeWhatAndWhere() {
        show(DoorDays(listOf(event(1), event(2, DoorKind.MOTION), event(3, unrecognised = true)), emptyList()))
        compose.onNodeWithText("14:01 · Ring").assertExists()
        compose.onNodeWithText("14:00 · Motion").assertExists()
        compose.onNodeWithText("13:59 · Ring alert").assertExists()
        compose.onNodeWithTag("door_yesterday").assertDoesNotExist()
        compose.assertAccessible()
    }

    @Test
    fun anEmptyTodaySaysSo() {
        show(DoorDays(emptyList(), listOf(event(1))))
        compose.onNodeWithTag("door_empty").assertTextEquals("No rings or motion today.")
        compose.onNodeWithTag("door_yesterday").assertExists()
    }

    @Test
    fun theStatusLineShowsOnlyWhenSomethingIsWrong() {
        show(DoorDays(emptyList(), emptyList()), status = "Culvery can’t see Ring’s notifications. Allow notification access in Settings › Door.")
        compose.onNodeWithTag("door_status").assertExists()
    }

    @Test
    fun noStatusNoLine() {
        show(DoorDays(emptyList(), emptyList()))
        compose.onNodeWithTag("door_status").assertDoesNotExist()
    }

    @Test
    fun aThumbnailOpensTheSnapshot() {
        val withPicture = event(1, snapshot = File(folder.root, "1.jpg"))
        show(DoorDays(listOf(withPicture), emptyList()))
        compose.onNodeWithTag("door_thumb_1").performClick()
        assertThat(opened).containsExactly(withPicture)
    }

    /** 5 §4.7: opening the tab marks every event seen. */
    @Test
    fun openingTheTabMarksEverythingSeen() {
        runBlocking { store.record(ring()) }
        compose.setContent { CulveryTheme(dark = true) { host(flowOf(LocalDate.of(2026, 10, 14))) } }
        runBlocking { store.unseen.first { it.rings == 0 } }
    }

    /** Ruling 25: left open behind another app, the tab marks nothing. */
    @Test
    fun aTabLeftOpenBehindAnotherAppMarksNothingSeen() {
        runBlocking { store.record(ring()) }
        val behind = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        compose.runOnUiThread { behind.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides behind) { CulveryTheme(dark = true) { host(flowOf(LocalDate.of(2026, 10, 14))) } }
        }
        compose.waitForIdle()
        assertThat(runBlocking { store.unseen.first() }.rings).isEqualTo(1)
    }

    /** Review Focus 5: the household's date moves on with the tab open, and its rows move with it. */
    @Test
    fun theTabFollowsTheHouseholdsDate() {
        runBlocking { store.record(ring(at = java.time.LocalDateTime.of(2026, 10, 14, 23, 59).atZone(london).toInstant().toEpochMilli())) }
        val today = MutableStateFlow(LocalDate.of(2026, 10, 14))
        compose.setContent { CulveryTheme(dark = true) { host(today) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("door_empty").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithTag("door_today").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("door_yesterday").assertDoesNotExist()
        today.value = LocalDate.of(2026, 10, 15)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("door_yesterday").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("door_empty").assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun host(today: kotlinx.coroutines.flow.Flow<LocalDate>) {
        val overlay = object : OverlayHost {
            override fun show(closesWhenIdle: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) = Unit
            override fun markEdited(edited: Boolean) = Unit
            override fun dismiss() = Unit
        }
        CompositionLocalProvider(LocalOverlayHost provides overlay) { DoorTabHost(store, today, flowOf(london), flowOf(null)) }
    }
}
```
(The rows' times come from `NOW` = 14:02 BST: event 1 is a minute earlier, 14:01.)
Create `ui/DoorTabScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.DoorDays
import uk.co.siland.culvery.capability.door.DoorEvent
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.NOW
import uk.co.siland.culvery.capability.door.picture
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The tab in the content area beside the rail, as the shell gives it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoorTabScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val london = ZoneId.of("Europe/London")

    private fun snap(name: String, dark: Boolean, days: DoorDays, status: String? = null) {
        compose.setContent { CulveryTheme(dark = dark) { Box(Modifier.size(1116.dp, 690.dp)) { DoorTab(days, london, status, onOpenSnapshot = {}) } } }
        if (days.today.any { it.snapshot != null }) compose.waitUntil(5_000) { compose.onAllNodesWithTag("door_thumb_image").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun days(): DoorDays {
        val file = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(640, 360)).saveTo(it) }
        return DoorDays(
            listOf(
                DoorEvent(1, NOW, DoorKind.RING, "Front Door", false, file, false),
                DoorEvent(2, NOW - 40 * 60_000, DoorKind.MOTION, "Front Door", false, null, false),
                DoorEvent(3, NOW - 3 * 60 * 60_000, DoorKind.RING, "Front Door", true, null, true),
            ),
            listOf(DoorEvent(4, NOW - 20 * 60 * 60_000, DoorKind.MOTION, "Back Garden", false, null, true)),
        )
    }

    @Test fun tabDark() = snap("door_tab_dark", true, days())
    @Test fun tabLight() = snap("door_tab_light", false, days())
    @Test fun tabEmptyDark() = snap("door_tab_empty_dark", true, DoorDays(emptyList(), emptyList()))
    @Test fun tabStatusDark() = snap("door_tab_status_dark", true, DoorDays(emptyList(), emptyList()), status = "Culvery can’t see Ring’s notifications. Allow notification access in Settings › Door.")
}
```
In `DoorCapabilityTest.kt`, both `DoorCapability(store, settings, …)` calls gain `, clock, householdZone()` with `private val clock = HouseholdClock(flowOf(ZoneId.of("Europe/London")), WallClock { NOW }, scope, flowOf(NOW))`.
Fails today: `doorDays`, `DoorDays`, `DoorTab`, `DoorTabHost` don't exist.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorDaysTest*' --tests '*DoorTabTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The days and the words**

Create `DoorDays.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DoorDays(val today: List<DoorEvent>, val yesterday: List<DoorEvent>)

/** [events] (newest first) on [today] and the day before, by the household's dates (5 §5.2); older ones, still kept, aren't shown. */
fun doorDays(events: List<DoorEvent>, today: LocalDate, zone: ZoneId): DoorDays {
    fun dayOf(event: DoorEvent) = Instant.ofEpochMilli(event.atMillis).atZone(zone).toLocalDate()
    return DoorDays(events.filter { dayOf(it) == today }, events.filter { dayOf(it) == today.minusDays(1) })
}
```
`DoorWords.kt` gains:
```kotlin
internal const val TODAY = "Today"
internal const val YESTERDAY = "Yesterday"
internal const val NO_RINGS_TODAY = "No rings or motion today."
internal const val SNAPSHOT = "Snapshot"
internal const val SHOW_FULL_SIZE = "show it full size"

/** "Ring", "Motion", or "Ring alert" for one the parser couldn't place (5 §5.2). */
fun kindLabel(event: DoorEvent): String = when {
    event.kind == DoorKind.MOTION -> "Motion"
    event.unrecognised -> RING_ALERT
    else -> "Ring"
}
```
`ui/DoorDimens.kt` gains:
```kotlin
    // The tab: rows 10 apart, 18 above a day's label; thumbnails 64 × 36 (16:9), radius 10.
    val rowGap = 10.dp
    val sectionTop = 18.dp
    val thumbWidth = 64.dp
    val thumbHeight = 36.dp
    val thumbRadius = 10.dp

    // The status line: a 20 dp icon 8 from its words.
    val statusIcon = 20.dp
    val statusGap = 8.dp

    // The full-size sheet's padding, as the calendar's sheets.
    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
```

- [ ] **Step 4: The tab**

Create `ui/DoorParts.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

/** The one line saying what is wrong and how to fix it (5 §6), on the Door tab and in Settings › Door. */
@Composable
internal fun DoorStatusLine(text: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DoorDimens.statusGap),
        modifier = Modifier.testTag("door_status").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        HhIcon(Icons.INFO, size = DoorDimens.statusIcon, tint = c.danger)
        Text(text, style = HhType.body, color = c.danger)
    }
}
```
Create `ui/DoorTab.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.capability.door.DoorDays
import uk.co.siland.culvery.capability.door.DoorEvent
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorStore
import uk.co.siland.culvery.capability.door.NO_RINGS_TODAY
import uk.co.siland.culvery.capability.door.SHOW_FULL_SIZE
import uk.co.siland.culvery.capability.door.SNAPSHOT
import uk.co.siland.culvery.capability.door.TODAY
import uk.co.siland.culvery.capability.door.Unseen
import uk.co.siland.culvery.capability.door.YESTERDAY
import uk.co.siland.culvery.capability.door.clockTime
import uk.co.siland.culvery.capability.door.doorDays
import uk.co.siland.culvery.capability.door.kindLabel
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.hhClickable

/** The Door tab (5 §5.2): a status line when something is wrong, then Today and Yesterday, newest first. Open to everyone. */
@Composable
fun DoorTab(days: DoorDays, zone: ZoneId, status: String?, onOpenSnapshot: (DoorEvent) -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(DoorDimens.rowGap), modifier = modifier.fillMaxSize().testTag("door_tab")) {
        if (status != null) item { DoorStatusLine(status) }
        item { HhSectionLabel(TODAY, Modifier.testTag("door_today")) }
        if (days.today.isEmpty()) item { Text(NO_RINGS_TODAY, style = HhType.body, color = c.mute, modifier = Modifier.testTag("door_empty")) }
        items(days.today, key = { it.id }) { DoorRow(it, zone, onOpenSnapshot) }
        if (days.yesterday.isNotEmpty()) {
            item { HhSectionLabel(YESTERDAY, Modifier.padding(top = DoorDimens.sectionTop).testTag("door_yesterday")) }
            items(days.yesterday, key = { it.id }) { DoorRow(it, zone, onOpenSnapshot) }
        }
    }
}

@Composable
private fun DoorRow(event: DoorEvent, zone: ZoneId, onOpenSnapshot: (DoorEvent) -> Unit) {
    val c = Culvery.colors
    HhListRow(
        title = "${clockTime(event.atMillis, zone)} · ${kindLabel(event)}",
        subtitle = event.device.ifEmpty { null },
        modifier = Modifier.testTag("door_row_${event.id}"),
        leading = { HhIcon(if (event.kind == DoorKind.MOTION) Icons.DIRECTIONS_WALK else Icons.DOORBELL, size = ListRowTokens.icon, tint = c.mute) },
        trailing = event.snapshot?.let { file ->
            {
                Box(
                    Modifier
                        .testTag("door_thumb_${event.id}")
                        .size(DoorDimens.thumbWidth, DoorDimens.thumbHeight)
                        .clip(RoundedCornerShape(DoorDimens.thumbRadius))
                        .background(c.surf2)
                        .semantics { contentDescription = SNAPSHOT }
                        .hhClickable({ onOpenSnapshot(event) }, label = SHOW_FULL_SIZE),
                ) { SnapshotImage(file, Modifier.fillMaxSize().testTag("door_thumb_image")) }
            }
        },
    )
}

/** A ring's picture full size, in a sheet (5 §5.2). */
@Composable
fun SnapshotSheet(event: DoorEvent, zone: ZoneId, onClose: () -> Unit) {
    val c = Culvery.colors
    val file = event.snapshot ?: return
    HhSheet(
        padding = PaddingValues(horizontal = DoorDimens.sheetPaddingH, vertical = DoorDimens.sheetPaddingV),
        modifier = Modifier.testTag("door_snapshot_sheet").semantics { paneTitle = SNAPSHOT },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${clockTime(event.atMillis, zone)} · ${kindLabel(event)}", style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
                if (event.device.isNotEmpty()) Text(event.device, style = HhType.body, color = c.mute)
            }
            HhCloseButton(onClose)
        }
        SnapshotImage(file, Modifier.fillMaxWidth().aspectRatio(DoorDimens.SNAPSHOT_RATIO).clip(RoundedCornerShape(DoorDimens.snapshotRadius)))
    }
}

/** The tab as the rail opens it: the store's events on the household's [today], and its [status]. */
@Composable
internal fun DoorTabHost(store: DoorStore, today: Flow<LocalDate>, zone: Flow<ZoneId>, status: Flow<String?>) {
    val events by store.events.collectAsStateWithLifecycle(emptyList())
    val day by today.collectAsStateWithLifecycle(null)
    val householdZone by zone.collectAsStateWithLifecycle(null)
    val problem by status.collectAsStateWithLifecycle(null)
    // Seen only while someone can see it (ruling 25).
    val unseen by store.unseen.collectAsStateWithLifecycle(Unseen(0, 0), minActiveState = Lifecycle.State.RESUMED)
    LaunchedEffect(unseen) { if (unseen.rings + unseen.motion > 0) store.markAllSeen() }
    val overlay = LocalOverlayHost.current
    val d = day ?: return
    val z = householdZone ?: return
    DoorTab(doorDays(events, d, z), z, problem, onOpenSnapshot = { event -> overlay.show { SnapshotSheet(event, z, onClose = overlay::dismiss) } })
}
```
`DoorCapability.kt`: the constructor gains `private val clock: HouseholdClock, private val zone: HouseholdZone` (imports `uk.co.siland.culvery.core.plugin.HouseholdClock`, `uk.co.siland.culvery.core.household.HouseholdZone`, `uk.co.siland.culvery.capability.door.ui.DoorTabHost`, `kotlinx.coroutines.flow.flowOf`), and
```kotlin
    @Composable
    override fun TabContent() = DoorTabHost(store, clock.today, zone.zone, status = flowOf(null))
```
(Settings › Door's task gives it the real status.)

- [ ] **Step 5: Run them to see them pass**

Run: `./gradlew :capability:door:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Screenshots**

`./gradlew :capability:door:recordRoborazziDebug --tests '*DoorTabScreenshotTest*'`; the four names in `$TMP/expected-baselines.txt`; the baseline check; open all four.

- [ ] **Step 7: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/door
git commit -m "Add the Door tab with today's and yesterday's rings and motion"
```

---

### Task 15: Settings › Door, connecting Ring and the status (§5.4, §5.5, §6; rulings 9, 14, 23, 28, 32; Review Focus 3)

**Review:** opus.

**Files:**
- Create (door package): `DoorHealth.kt`, `ui/DoorSettingsPage.kt`
- Modify: `DoorWords.kt`, `ui/DoorDimens.kt`, `DoorCapability.kt` (Settings page, the tab's status)
- Test: `capability/door/src/test/.../DoorHealthTest.kt`, `ui/DoorSettingsPageTest.kt`, `ui/DoorSettingsScreenshotTest.kt`, `ui/RecordingNavigator.kt`, `RecordingToaster.kt`; `DoorCapabilityTest.kt` (constructor; the page)
- Baselines (new): `settings_door_dark`, `settings_door_light`, `settings_door_steps_dark`.

**Interfaces:**
- Consumes: `ShellNavigator.openSystemScreen` (Task 8); `AlertHost.inFront` (Task 7); `DoorRecorder.bound` (Task 11); `Chime` (Task 13); `DoorStatusLine`, `DoorTabHost` (Task 14); `DoorSettings`, `DoorProvider`, `FakeDoorProvider` (Task 9); `COULD_NOT_SAVE` (`:core:plugin`); `HhListRow`, `HhSwitch`, `HhSectionLabel`, `HhButton` (`:core:ui`); `rememberNowMillis` (`:core:plugin`).
- Produces:
  - `@Singleton class DoorHealth`: `data class Checks(val appInstalled: Boolean, val accessAllowed: Boolean)`, `val checks: StateFlow<Checks>`, `fun refresh()`, `val status: Flow<String?>`; internal constructor `(provider: DoorProvider?, settings: DoorSettings, bound: StateFlow<Boolean?>, inFront: StateFlow<Boolean>, scope: CoroutineScope)`
  - `data class DoorSettingsUi(…)`, `class DoorSettingsActions(…)`, `@Composable fun DoorSettingsScreen(ui: DoorSettingsUi, actions: DoorSettingsActions)`, `@Composable internal fun DoorSettingsHost(…)`, `class DoorPage : SettingsPage` (id `door`, order 500)
  - In `DoorWords.kt`: `accessOff(service)`, `appMissing(service)`, `cantFullyRead(service)`, `lastHeard(service, millis, zone)`, `appStep(service)`, `checkNotifications(service)`, `NOTIFICATION_ACCESS_MISSING`, `CAPTURE_MS`
  - `DoorCapability` constructor: `(store, settings, providers, clock, zone, health: DoorHealth, chime: Chime, toaster: Toaster)`.

- [ ] **Step 1: Write the failing tests**

Create `RecordingToaster.kt` (door test package):
```kotlin
package uk.co.siland.culvery.capability.door

import uk.co.siland.culvery.core.plugin.Toaster

internal class RecordingToaster : Toaster {
    val messages = mutableListOf<String>()
    override fun show(message: String, icon: String, urgent: Boolean) {
        messages += message
    }
}
```
Create `ui/RecordingNavigator.kt` (door test, package `…capability.door.ui`):
```kotlin
package uk.co.siland.culvery.capability.door.ui

import android.content.Intent
import uk.co.siland.culvery.core.plugin.ShellNavigator

class RecordingNavigator : ShellNavigator {
    val systemScreens = mutableListOf<Pair<String?, String>>()
    override fun openTab(id: String) = Unit
    override fun openSettings(page: String?) = Unit
    override fun exitKiosk() = Unit
    override fun chooseHomeApp() = Unit
    override fun changeHomeApp() = Unit
    override fun leavePinning() = Unit
    override fun returnToPinning() = Unit
    override fun openSystemScreen(intent: Intent, missing: String) {
        systemScreens += intent.action to missing
    }
}
```
Create `DoorHealthTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** 5 §6: the one message, checked on each return to the front and whenever Android binds or unbinds the listener. */
@RunWith(AndroidJUnit4::class)
class DoorHealthTest {
    @get:Rule val folder = TemporaryFolder()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { DoorSettings(doorSettingsStore(io) { File(folder.root, "s.preferences_pb") }, io) }
    private val provider = FakeDoorProvider()
    private val bound = MutableStateFlow<Boolean?>(null)
    private val inFront = MutableStateFlow(true)

    @After
    fun tearDown() = io.cancel()

    private fun TestScope.health() = DoorHealth(provider, settings, bound, inFront, backgroundScope)

    private val accessOff = "Culvery can’t see Ring’s notifications. Allow notification access in Settings › Door."

    @Test
    fun nothingIsSaidBeforeRingIsConnected() = runTest {
        provider.access = false
        assertThat(health().status.first()).isNull()
    }

    /** Review Focus 3: no resume needed; Android unbinding the listener is enough, and binding again clears it. */
    @Test
    fun accessTurnedOffWhileInFrontShowsAtOnceAndClearsWhenBack() = runTest {
        settings.setConnected(true)
        val health = health()
        runCurrent()
        assertThat(health.status.first()).isNull()
        provider.access = false
        bound.value = false
        runCurrent()
        assertThat(health.status.first { it != null }).isEqualTo(accessOff)
        assertThat(inFront.value).isTrue()
        provider.access = true
        bound.value = true
        runCurrent()
        assertThat(health.status.first { it == null }).isNull()
    }

    @Test
    fun theRingAppRemovedShowsWhenCulveryComesBack() = runTest {
        settings.setConnected(true)
        inFront.value = false
        val health = health()
        provider.installed = false
        inFront.value = true
        runCurrent()
        assertThat(health.status.first { it != null })
            .isEqualTo("The Ring app isn’t on this tablet. Install it and sign in, then check Settings › Door.")
    }

    @Test
    fun accessOffIsSaidBeforeTheAppMissing() = runTest {
        settings.setConnected(true)
        provider.installed = false
        provider.access = false
        assertThat(health().status.first { it != null }).isEqualTo(accessOff)
    }

    /** Ruling 14: three unrecognised alerts in a row; a recognised one ends it. */
    @Test
    fun threeUnrecognisedAlertsSayRingsShowAsRingAlert() = runTest {
        settings.setConnected(true)
        val health = health()
        repeat(2) { settings.countAlert(recognised = false) }
        assertThat(health.status.first()).isNull()
        settings.countAlert(recognised = false)
        assertThat(health.status.first { it != null }).isEqualTo("Culvery can’t fully read Ring’s notifications, so rings show as ‘Ring alert’.")
        settings.countAlert(recognised = true)
        assertThat(health.status.first { it == null }).isNull()
    }
}
```
Create `ui/DoorSettingsPageTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.door.CAPTURE_MS
import uk.co.siland.culvery.capability.door.DoorHealth
import uk.co.siland.culvery.capability.door.DoorSettings
import uk.co.siland.culvery.capability.door.FakeChime
import uk.co.siland.culvery.capability.door.FakeDoorProvider
import uk.co.siland.culvery.capability.door.NOW
import uk.co.siland.culvery.capability.door.NOTIFICATION_ACCESS_MISSING
import uk.co.siland.culvery.capability.door.RecordingToaster
import uk.co.siland.culvery.capability.door.doorSettingsStore
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

/** 5 §5.4, §5.5: the chime, quiet hours, Ring's status and steps, and the capture switch. */
@RunWith(AndroidJUnit4::class)
class DoorSettingsPageTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val london = ZoneId.of("Europe/London")
    private val settings by lazy { DoorSettings(doorSettingsStore(io) { File(folder.root, "s.preferences_pb") }, io) }
    private val provider = FakeDoorProvider(installed = true, access = false)
    private val chime = FakeChime()
    private val toaster = RecordingToaster()
    private val navigator = RecordingNavigator()
    private val clock = HouseholdClock(flowOf(london), WallClock { NOW }, io, flowOf(NOW))

    @After
    fun tearDown() = io.cancel()

    private fun showHost() {
        val health = DoorHealth(provider, settings, MutableStateFlow(true), MutableStateFlow(true), io)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                CulveryTheme(dark = true) { DoorSettingsHost(settings, health, provider, chime, clock, flowOf(london), toaster) }
            }
        }
    }

    @Test
    fun theStepsTickAsTheyAreDone() {
        showHost()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("door_step_app_done", useUnmergedTree = true).assertExists() }.isSuccess }
        compose.onNodeWithTag("door_step_access_done", useUnmergedTree = true).assertDoesNotExist()
        compose.assertAccessible()
    }

    /** 5 §5.5 step 2, through the kiosk's path (5 §3.4). */
    @Test
    fun allowNotificationAccessOpensAndroidsScreen() {
        showHost()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("door_step_access").assertExists() }.isSuccess }
        compose.onNodeWithTag("door_step_access").performClick()
        assertThat(navigator.systemScreens).containsExactly("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS" to NOTIFICATION_ACCESS_MISSING)
    }

    /** With both ticked, Ring is connected and the Door tab appears (5 §5.5). */
    @Test
    fun bothTickedSavesTheConnection() {
        provider.access = true
        showHost()
        assertThat(runBlocking { settings.prefs.first { it.connected }.connected }).isTrue()
    }

    @Test
    fun playChimePlaysAtTheChosenVolume() {
        runBlocking { settings.setVolume(0.4f) }
        showHost()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("door_play_chime").assertExists() }.isSuccess }
        compose.waitForIdle()
        compose.onNodeWithTag("door_play_chime").performClick()
        assertThat(chime.volumes).containsExactly(0.4f)
    }

    @Test
    fun theVolumeIsSavedWhenTheSliderIsLetGo() {
        showHost()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("door_volume").assertExists() }.isSuccess }
        compose.onNodeWithTag("door_volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
        assertThat(runBlocking { settings.prefs.first { it.volume == 0.3f }.volume }).isEqualTo(0.3f)
    }

    /** Ruling 28: half an hour at a time. */
    @Test
    fun quietHoursMoveHalfAnHourAtATime() {
        showHost()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("door_from_later").assertExists() }.isSuccess }
        compose.onNodeWithTag("door_from_later").performClick()
        assertThat(runBlocking { settings.prefs.first { it.quietFrom == LocalTime.of(21, 30) }.quietFrom }).isEqualTo(LocalTime.of(21, 30))
    }

    /** 5 §5.4: on for 30 minutes, then off by itself; the recorder stops at the same moment. */
    @Test
    fun theCaptureSwitchRunsForThirtyMinutes() {
        showHost()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("door_capture").assertExists() }.isSuccess }
        compose.onNodeWithTag("door_capture").assertIsOff().performClick()
        assertThat(runBlocking { settings.prefs.first { it.captureUntilMillis != 0L }.captureUntilMillis }).isEqualTo(NOW + CAPTURE_MS)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("door_capture").assertIsOn() }.isSuccess }
    }

    @Test
    fun lastHeardSaysWhenOrNotYet() {
        showHost()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("door_last_heard").assertExists() }.isSuccess }
        compose.onNodeWithTag("door_last_heard").assertTextEquals("Culvery hasn’t heard from Ring yet.")
        runBlocking { settings.heard(NOW) }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("door_last_heard").assertTextEquals("Last heard from Ring: Wednesday 14:02") }.isSuccess }
    }
}
```
Create `ui/DoorSettingsScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.CulveryTheme

/** Settings › Door in Settings' page area. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoorSettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val actions = DoorSettingsActions({}, {}, { _, _, _ -> }, {}, {})

    private val connected = DoorSettingsUi(
        service = "Ring", hint = "Sign in as a Ring Shared User, and turn off the Ring app’s notification sound.", volume = 0.7f,
        quietOn = true, quietFrom = LocalTime.of(21, 0), quietUntil = LocalTime.of(7, 0), status = null,
        lastHeard = "Last heard from Ring: Wednesday 14:02", appInstalled = true, accessAllowed = true, capturing = false,
    )

    private fun snap(name: String, dark: Boolean, ui: DoorSettingsUi) {
        compose.setContent { CulveryTheme(dark = dark) { Box(Modifier.size(860.dp, 760.dp)) { DoorSettingsScreen(ui, actions) } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun doorDark() = snap("settings_door_dark", true, connected)
    @Test fun doorLight() = snap("settings_door_light", false, connected)
    @Test fun doorStepsDark() = snap(
        "settings_door_steps_dark", true,
        connected.copy(accessAllowed = false, lastHeard = "Culvery hasn’t heard from Ring yet.", status = null),
    )
}
```
`DoorCapabilityTest.kt`: the `capability(…)` construction gains `DoorHealth(provider, settings, MutableStateFlow(true), MutableStateFlow(true), scope), FakeChime(), RecordingToaster()`, and add:
```kotlin
    /** 5 §5.4: Settings › Door, admin-only like the rest of Settings, only where a doorbell provider is installed. */
    @Test
    fun settingsHasADoorPage() {
        val page = capability(setOf(FakeDoorProvider())).settingsPages().single()
        assertThat(listOf(page.id, page.title, page.order)).containsExactly("door", "Door", 500).inOrder()
        assertThat(capability(emptySet()).settingsPages()).isEmpty()
    }
```
(rewrite the file's two earlier constructions through a `private fun capability(providers: Set<DoorProvider>) = DoorCapability(…)` helper.)
Fails today: `DoorHealth`, `DoorSettingsHost`, `DoorSettingsScreen`, `CAPTURE_MS`, `NOTIFICATION_ACCESS_MISSING` don't exist.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorHealthTest*' --tests '*DoorSettingsPageTest*' --tests '*DoorCapabilityTest*'`
Expected: FAIL to compile.

- [ ] **Step 3: The words**

`DoorWords.kt` gains:
```kotlin
/** Check Ring's notifications runs this long (5 §5.4). */
const val CAPTURE_MS = 30 * 60 * 1_000L

internal const val DOOR = "Door"
internal const val CHIME = "Chime"
internal const val CHIME_VOLUME = "Chime volume"
internal const val PLAY_CHIME = "Play chime"
internal const val QUIET_HOURS = "Quiet hours"
internal const val FROM = "From"
internal const val UNTIL = "Until"
internal const val EARLIER = "Earlier"
internal const val LATER = "Later"
internal const val ACCESS_STEP = "Allow notification access"
internal const val ACCESS_STEP_LINE = "Opens Android’s notification access, where you allow Culvery."
internal const val OPEN_NOTIFICATION_ACCESS = "open notification access"
internal const val DONE = "done"
internal const val NOT_DONE_YET = "not done yet"
internal const val CAPTURE_LINE = "Writes each notification’s details to the tablet’s log for 30 minutes, so Culvery can learn to read them."
internal const val NOTIFICATION_ACCESS_MISSING =
    "Culvery can’t open notification access. Open the tablet’s Settings, then Notifications, and allow Culvery."

private val DAY_AND_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE HH:mm", Locale.ENGLISH)

/** 5 §6's three messages. */
internal fun accessOff(service: String) = "Culvery can’t see $service’s notifications. Allow notification access in Settings › Door."
internal fun appMissing(service: String) = "The $service app isn’t on this tablet. Install it and sign in, then check Settings › Door."
internal fun cantFullyRead(service: String) = "Culvery can’t fully read $service’s notifications, so rings show as ‘$RING_ALERT’."

/** "Last heard from Ring: Tuesday 14:02" (5 §5.4), or that it hasn't been yet. */
internal fun lastHeard(service: String, atMillis: Long?, zone: ZoneId): String =
    atMillis?.let { "Last heard from $service: ${Instant.ofEpochMilli(it).atZone(zone).format(DAY_AND_TIME)}" } ?: "Culvery hasn’t heard from $service yet."

internal fun appStep(service: String) = "$service app installed and signed in"
internal fun checkNotifications(service: String) = "Check $service’s notifications"
internal fun quietHoursLine(from: LocalTime, until: LocalTime) = "No chime from ${from.format(CLOCK)} until ${until.format(CLOCK)}."
```
(import `java.time.LocalTime`.)
`ui/DoorDimens.kt` gains:
```kotlin
    // Settings › Door: 16 between blocks, as Settings' other pages; quiet hours move 30 minutes a step (ruling 28).
    val settingsGap = 16.dp
    const val QUIET_STEP_MINUTES = 30L
    const val VOLUME_STEPS = 9
```

- [ ] **Step 4: The health**

Create `DoorHealth.kt`:
```kotlin
package uk.co.siland.culvery.capability.door

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope

/** Three unrecognised alerts in a row mean Ring's words have changed (5 §6). */
private const val UNRECOGNISED_LIMIT = 3

/**
 * Whether the tablet can hear the doorbell (5 §6): checked on each return to the front, and each time Android binds or
 * unbinds the listener (which it does when notification access is turned off), so the status needn't wait for a resume.
 */
@Singleton
class DoorHealth internal constructor(
    private val provider: DoorProvider?,
    settings: DoorSettings,
    bound: StateFlow<Boolean?>,
    inFront: StateFlow<Boolean>,
    scope: CoroutineScope,
) {
    @Inject
    constructor(
        providers: Set<@JvmSuppressWildcards DoorProvider>,
        settings: DoorSettings,
        recorder: DoorRecorder,
        host: AlertHost,
        @ApplicationScope scope: CoroutineScope,
    ) : this(providers.firstOrNull(), settings, recorder.bound, host.inFront, scope)

    data class Checks(val appInstalled: Boolean, val accessAllowed: Boolean)

    private val _checks = MutableStateFlow(checkNow())
    val checks: StateFlow<Checks> = _checks.asStateFlow()

    init {
        scope.launch { merge(inFront.filter { it }, bound.filterNotNull()).collect { refresh() } }
    }

    fun refresh() {
        _checks.value = checkNow()
    }

    private fun checkNow() = Checks(provider?.appInstalled() == true, provider?.accessAllowed() == true)

    /** The one line the Door tab and Settings › Door show when something is wrong; null while all is well or before Ring is connected. */
    val status: Flow<String?> = combine(checks, settings.prefs) { c, prefs ->
        val service = provider?.descriptor?.displayName
        when {
            service == null || !prefs.connected -> null
            !c.accessAllowed -> accessOff(service)
            !c.appInstalled -> appMissing(service)
            prefs.unrecognisedRun >= UNRECOGNISED_LIMIT -> cantFullyRead(service)
            else -> null
        }
    }.distinctUntilChanged()
}
```

- [ ] **Step 5: The page**

Create `ui/DoorSettingsPage.kt`:
```kotlin
package uk.co.siland.culvery.capability.door.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.door.ACCESS_STEP
import uk.co.siland.culvery.capability.door.ACCESS_STEP_LINE
import uk.co.siland.culvery.capability.door.CAPTURE_LINE
import uk.co.siland.culvery.capability.door.CAPTURE_MS
import uk.co.siland.culvery.capability.door.CHIME
import uk.co.siland.culvery.capability.door.CHIME_VOLUME
import uk.co.siland.culvery.capability.door.Chime
import uk.co.siland.culvery.capability.door.DONE
import uk.co.siland.culvery.capability.door.DOOR
import uk.co.siland.culvery.capability.door.DoorHealth
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.capability.door.DoorSettings
import uk.co.siland.culvery.capability.door.EARLIER
import uk.co.siland.culvery.capability.door.FROM
import uk.co.siland.culvery.capability.door.LATER
import uk.co.siland.culvery.capability.door.NOTIFICATION_ACCESS_MISSING
import uk.co.siland.culvery.capability.door.NOT_DONE_YET
import uk.co.siland.culvery.capability.door.OPEN_NOTIFICATION_ACCESS
import uk.co.siland.culvery.capability.door.PLAY_CHIME
import uk.co.siland.culvery.capability.door.QUIET_HOURS
import uk.co.siland.culvery.capability.door.UNTIL
import uk.co.siland.culvery.capability.door.appStep
import uk.co.siland.culvery.capability.door.checkNotifications
import uk.co.siland.culvery.capability.door.lastHeard
import uk.co.siland.culvery.capability.door.quietHoursLine
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSwitch
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens

private const val TAG = "DoorSettings"
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

const val DOOR_PAGE_ID = "door"

/** Settings › Door (5 §5.4), admin-only as the rest of Settings; ruling 32. */
class DoorPage internal constructor(private val content: @Composable () -> Unit) : SettingsPage {
    override val id = DOOR_PAGE_ID
    override val title = DOOR
    override val order = 500

    @Composable
    override fun Content() = content()
}

/** What Settings › Door shows. */
data class DoorSettingsUi(
    val service: String,
    val hint: String?,
    val volume: Float,
    val quietOn: Boolean,
    val quietFrom: LocalTime,
    val quietUntil: LocalTime,
    val status: String?,
    val lastHeard: String,
    val appInstalled: Boolean,
    val accessAllowed: Boolean,
    val capturing: Boolean,
)

/** What Settings › Door's controls do. */
class DoorSettingsActions(
    val onVolume: (Float) -> Unit,
    val onPlayChime: () -> Unit,
    val onQuietHours: (on: Boolean, from: LocalTime, until: LocalTime) -> Unit,
    val onAllowAccess: () -> Unit,
    val onCapture: (Boolean) -> Unit,
)

@Composable
fun DoorSettingsScreen(ui: DoorSettingsUi, actions: DoorSettingsActions) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(DoorDimens.settingsGap),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings_door"),
    ) {
        Text(DOOR, style = HhType.screenTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        HhSectionLabel(CHIME)
        VolumeRow(ui.volume, actions.onVolume)
        HhButton(PLAY_CHIME, actions.onPlayChime, Modifier.testTag("door_play_chime"), icon = Icons.DOORBELL, on = ButtonSurface.Bg)
        HhSectionLabel(QUIET_HOURS)
        SwitchRow(QUIET_HOURS, quietHoursLine(ui.quietFrom, ui.quietUntil), ui.quietOn, "door_quiet") { actions.onQuietHours(it, ui.quietFrom, ui.quietUntil) }
        StepperRow(FROM, "quiet hours start", ui.quietFrom, "door_from") { actions.onQuietHours(ui.quietOn, it, ui.quietUntil) }
        StepperRow(UNTIL, "quiet hours end", ui.quietUntil, "door_until") { actions.onQuietHours(ui.quietOn, ui.quietFrom, it) }
        HhSectionLabel(ui.service)
        ui.status?.let { DoorStatusLine(it) }
        Text(ui.lastHeard, style = HhType.body, color = c.mute, modifier = Modifier.testTag("door_last_heard"))
        StepRow(appStep(ui.service), ui.hint, done = ui.appInstalled, tag = "door_step_app")
        StepRow(ACCESS_STEP, ACCESS_STEP_LINE, done = ui.accessAllowed, tag = "door_step_access", onClick = actions.onAllowAccess)
        SwitchRow(checkNotifications(ui.service), CAPTURE_LINE, ui.capturing, "door_capture", actions.onCapture)
    }
}

/** Saved when the slider is let go, not at each step of a drag. */
@Composable
private fun VolumeRow(volume: Float, onVolume: (Float) -> Unit) {
    val c = Culvery.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: volume
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ListRowTokens.trailingGap)) {
        Text(CHIME_VOLUME, style = HhType.rowTitle, color = c.ink)
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onVolume)
                dragging = null
            },
            steps = DoorDimens.VOLUME_STEPS,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surf3),
            modifier = Modifier.weight(1f).testTag("door_volume").semantics { contentDescription = CHIME_VOLUME },
        )
        Text("${(shown * 100).roundToInt()} %", style = HhType.body, color = c.mute)
    }
}

/** A row that is one switch for TalkBack and the finger; [HhSwitch] only draws. */
@Composable
private fun SwitchRow(title: String, line: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ListRowTokens.radius))
            .background(c.surf)
            .testTag(tag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = ListRowTokens.paddingH, vertical = ListRowTokens.paddingV),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HhType.rowTitle, color = c.ink)
            if (line != null) Text(line, style = HhType.secondary, color = c.mute)
        }
        HhSwitch(checked, onCheckedChange = null, tag = "${tag}_switch")
    }
}

@Composable
private fun StepperRow(label: String, subject: String, time: LocalTime, tag: String, onTime: (LocalTime) -> Unit) {
    HhListRow(
        title = label,
        subtitle = time.format(CLOCK),
        modifier = Modifier.testTag(tag),
        trailing = {
            HhButton(EARLIER, { onTime(time.minusMinutes(DoorDimens.QUIET_STEP_MINUTES)) }, Modifier.testTag("${tag}_earlier"), on = ButtonSurface.Surf, spokenName = "$EARLIER, $subject")
            HhButton(LATER, { onTime(time.plusMinutes(DoorDimens.QUIET_STEP_MINUTES)) }, Modifier.testTag("${tag}_later"), on = ButtonSurface.Surf, spokenName = "$LATER, $subject")
        },
    )
}

/** A connect step (5 §5.5), ticked when done. */
@Composable
private fun StepRow(title: String, line: String?, done: Boolean, tag: String, onClick: (() -> Unit)? = null) {
    val c = Culvery.colors
    HhListRow(
        title = title,
        subtitle = line,
        modifier = Modifier.testTag(tag).semantics { stateDescription = if (done) DONE else NOT_DONE_YET },
        leading = {
            HhIcon(
                if (done) Icons.CHECK else Icons.INFO,
                size = ListRowTokens.icon,
                tint = if (done) c.accent else c.mute,
                modifier = if (done) Modifier.testTag("${tag}_done") else Modifier,
            )
        },
        onClick = onClick,
        clickLabel = onClick?.let { OPEN_NOTIFICATION_ACCESS },
    )
}

/**
 * Settings › Door with its data: checks Ring again as it opens, saves the connection once both steps are ticked (5 §5.5),
 * and says when a setting didn't save.
 */
@Composable
internal fun DoorSettingsHost(
    settings: DoorSettings,
    health: DoorHealth,
    provider: DoorProvider,
    chime: Chime,
    clock: HouseholdClock,
    zone: Flow<ZoneId>,
    toaster: Toaster,
) {
    val prefs by settings.prefs.collectAsStateWithLifecycle(null)
    val checks by health.checks.collectAsStateWithLifecycle()
    val status by health.status.collectAsStateWithLifecycle(null)
    val householdZone by zone.collectAsStateWithLifecycle(null)
    val now = rememberNowMillis(clock)
    val navigator = LocalShellNavigator.current
    val scope = rememberCoroutineScope()
    // The household may have just installed Ring or allowed access.
    LaunchedEffect(Unit) { health.refresh() }
    val p = prefs ?: return
    val z = householdZone ?: return
    LaunchedEffect(checks, p.connected) {
        if (checks.appInstalled && checks.accessAllowed && !p.connected) save(toaster) { settings.setConnected(true) }
    }
    fun change(write: suspend () -> Unit) {
        scope.launch { save(toaster, write) }
    }
    val service = provider.descriptor.displayName
    DoorSettingsScreen(
        DoorSettingsUi(
            service = service,
            hint = provider.descriptor.connectHint,
            volume = p.volume,
            quietOn = p.quietOn,
            quietFrom = p.quietFrom,
            quietUntil = p.quietUntil,
            status = status,
            lastHeard = lastHeard(service, p.lastHeardMillis, z),
            appInstalled = checks.appInstalled,
            accessAllowed = checks.accessAllowed,
            capturing = p.captureUntilMillis > now,
        ),
        DoorSettingsActions(
            onVolume = { v -> change { settings.setVolume(v) } },
            onPlayChime = { chime.play(p.volume) },
            onQuietHours = { on, from, until -> change { settings.setQuietHours(on, from, until) } },
            onAllowAccess = { navigator.openSystemScreen(provider.accessScreen(), NOTIFICATION_ACCESS_MISSING) },
            onCapture = { on -> change { settings.setCapture(if (on) clock.nowMillis() + CAPTURE_MS else 0L) } },
        ),
    )
}

/** A setting that didn't save says so; nothing changed. */
private suspend fun save(toaster: Toaster, write: suspend () -> Unit) {
    try {
        write()
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't save a door setting (${e::class.simpleName})")
        toaster.show(COULD_NOT_SAVE, urgent = true)
    }
}
```
`DoorCapability.kt`: the constructor becomes `(store, settings, providers, clock, zone, private val health: DoorHealth, private val chime: Chime, private val toaster: Toaster)` (import `uk.co.siland.culvery.core.plugin.Toaster`, `uk.co.siland.culvery.core.plugin.SettingsPage`, `uk.co.siland.culvery.capability.door.ui.DoorPage`, `uk.co.siland.culvery.capability.door.ui.DoorSettingsHost`); keep `private val provider = providers.firstOrNull()` and `hasProvider = provider != null`; add
```kotlin
    private val pages: List<SettingsPage> = listOfNotNull(
        provider?.let { p -> DoorPage { DoorSettingsHost(settings, health, p, chime, clock, zone.zone, toaster) } },
    )

    override fun settingsPages(): List<SettingsPage> = pages
```
and `TabContent` passes `status = health.status` in place of `flowOf(null)`.

- [ ] **Step 6: Run them to see them pass**

Run: `./gradlew :capability:door:testDebugUnitTest :core:ui:testDebugUnitTest --tests '*CopyScanTest*'`
Expected: PASS.

- [ ] **Step 7: Screenshots**

`./gradlew :capability:door:recordRoborazziDebug --tests '*DoorSettingsScreenshotTest*'`; the three names in `$TMP/expected-baselines.txt`; the baseline check; open all three.

- [ ] **Step 8: The gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug` — Expected: BUILD SUCCESSFUL.
```bash
git add capability/door
git commit -m "Add Settings › Door: the chime, quiet hours, connecting Ring and its status"
```

---
### Task 16: Simplify, and the emulator walk-through on a second AVD (§8.1)

**Review:** the controller, with the user at the emulator.

**Files:**
- Modify: whatever `simplify` changes (Step 1); `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Step 6)
- Any file a fault fix needs (Step 5's loop)

**Rules for this task.** The current emulator (`emulator-5554`) holds a signed release and the household: **never install on it, never uninstall anything there, and never run a Gradle `install…` task while it is connected** (Gradle installs on every device). Every `adb` command names `-s "$S"`, the second AVD's serial. The user types every PIN and password. Nothing from this task (logcat, screenshots) is committed; household names never appear in the follow-ups file.

- [ ] **Step 1: Simplify the branch**

Run the `simplify` skill over the branch's changes since 03586ba. If it changes anything, run `./gradlew testDebugUnitTest verifyRoborazziDebug` and commit (`git commit -m "Simplify the Ring doorbell branch"`).

- [ ] **Step 2: A second AVD**

List what is installed: `"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --list_installed` (Git Bash; use `sdkmanager.bat` from PowerShell). Use the API 35 image already installed (the Play image is fine: the Ring app isn't needed, §8.1). If no suitable image is installed, **stop and ask** before downloading one (name the package and its size). Then:
```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n culvery-5 -k "system-images;android-35;google_apis_playstore;x86_64" -d pixel_tablet
"$ANDROID_HOME/emulator/emulator" -avd culvery-5 -no-snapshot-save &
adb devices
```
Use the image name `--list_installed` printed. Write the new emulator's serial (e.g. `emulator-5556`, never `emulator-5554`) to `$TMP/culvery-5-serial` and `S=$(cat "$TMP/culvery-5-serial")`. Rotate it to landscape if the profile starts in portrait.

- [ ] **Step 3: Install the debug build there only**

```bash
./gradlew :app:assembleDebug
adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk
```
The user runs the wizard with a test household (an Admin with a test PIN, one Adult, one Child; the London home).

- [ ] **Step 4: Walk the checklist**

Fake rings (§7) are posted from the adb shell; each line's quotes are for Git Bash. If this Android's `cmd notification post` has no `--picture` option (`adb -s "$S" shell cmd notification help`), record item 7 as not reachable on the emulator and check it on the tablet (Task 17).
```bash
# ring 'words' [n]: n picks the tag, so a new n is a new notification key (ruling 12); the same n within 2 minutes is a re-post.
ring()   { adb -s "$S" shell "cmd notification post -t 'Front Door' culvery-fake-ring-${2:-1} '$1'"; }
picture() { adb -s "$S" push app/src/test/screenshots/launcher_icon_108.png /data/local/tmp/snap.png >/dev/null
            adb -s "$S" shell "cmd notification post -S bigpicture --picture file:///data/local/tmp/snap.png -t 'Front Door' culvery-fake-ring-${1:-1} 'Someone is at your Front Door'"; }
```
Tick each item, writing what was seen in one line; anything unexpected goes to Step 5.

§A:
1. The mark sits above Home on the rail, in both themes (tap the status bar's theme toggle).
2. Home's clock is smaller; Today's titles and times are larger; nothing in the header overlaps at the tablet's size.
3. Update now: `adb -s "$S" shell am broadcast -n uk.co.siland.culvery/.DebugOfflineReceiver --ez offline true`; once the week is out of date (or after Update now), the line says "The tablet is offline. Check the Wi-Fi, then Update now." with Update now beside it; `--ez offline false` and Update now brings back the age line.
4. Add event: All day is chosen; Pick time… shows Length; Repeats is there (Never); Every week shows Ends; On a date… before the event's day shows the line under Ends and Add event waits; a valid weekly series saves and shows the repeat badge on its day.

Connecting Ring (§5.5):
5. Settings › Door (Admin PIN): step 1 ticked (debug counts the Ring app, ruling 12); "Allow notification access" unpins and opens Android's screen for Culvery; the user allows it; Back returns to Culvery, pinned again; step 2 ticked; the Door tab appears on the rail at slot 50.

The card and the chime (§4.6, §5.1):
6. `ring 'Someone is at your Front Door'`: within a few seconds the card shows top centre with "Someone’s at the door", "Front Door", the time; the chime plays; the rail shows a "1" bubble.
7. Within 2 minutes, `picture` (the same tag, so the same key): the same card gains the picture; no second chime; still one row in the Door tab.
8. `ring 'Someone is at your Back Door' 2`, then 3 seconds later `ring 'Someone is at your Back Door' 3`: the card refreshes each time; one chime only. Twelve seconds later `ring 'Someone is at your Side Door' 4`: it chimes again.
9. Got it closes the card; the badge counts one fewer. A ring left alone closes after 2 minutes and stays counted.
10. `ring 'There is motion at your Front Door'` with no unseen rings: no card, no chime, a dot on the rail.
11. `ring 'New event at your Front Door'`: the card says "Ring alert"; it chimes.
12. Quiet hours: in Settings › Door move From earlier than now (Earlier, half an hour at a time) and Until later; `ring …`: the card, no chime. Put them back.
13. Chime volume: drag to 30 %, Play chime, softer; back to 70 %.

Layers (§2.10):
14. Open Add event, type a title, `ring …`: the card shows over the sheet; Got it; the title is still there. Leave the sheet 2 minutes after the last touch on it (tapping only the card meanwhile): the sheet's warning and close come on time.
15. Open Settings so the PIN pad shows, `ring …`: the pad is above the card.

Not in front (§2.7):
16. Settings › Tablet lock › Use other apps (Admin PIN), go to the launcher, `ring …`: no card, no chime. Back to Culvery: still no card; the badge counts it; the Door tab shows it.

The Door tab (§5.2):
17. Today and Yesterday newest first; a thumbnail opens the picture in a sheet; opening the tab clears the badge; with nothing today it says "No rings or motion today."

When things go wrong (§6):
18. `adb -s "$S" shell cmd notification disallow_listener uk.co.siland.culvery/uk.co.siland.culvery.provider.door_ring.RingNotificationListener` while the Door tab shows: the status line appears without leaving Culvery; `allow_listener` with the same component: it goes.
19. `adb -s "$S" shell am broadcast -n uk.co.siland.culvery/.DebugRingReceiver --ez ring_app false`, then Use other apps and back: "The Ring app isn’t on this tablet. …"; `--ez ring_app true` and back again: gone.
20. `ring 'New event at your Front Door' 5`, `… 6`, `… 7`: "Culvery can’t fully read Ring’s notifications, …"; `ring 'Someone is at your Front Door' 8` clears it.
21. Settings › Door › Check Ring’s notifications on; `ring …`; `adb -s "$S" logcat -d -s RingReader:I` shows a `Ring notice: RingNotice(…) extras=[…]` line and a "read as a ring" line; switch off; another `ring …` logs only the "read as" line.
22. Last heard from Ring shows the latest time.

TalkBack (short, with the user's yes; as 4d-2's walk-through A.5 did it, never while a real PIN is typed):
23. A ring is spoken at once with its words; the rail tab says "Door, 1 unseen ring", and "Door, new motion" for a dot.

- [ ] **Step 5: The fault loop**

Each fault: write it as a failing test in its owning module, fix it in that task's style (a subagent may do it), run the module's tests and then the gate, commit with a message naming the fault, reinstall with `adb -s "$S" install -r …` and re-check that item. A look-and-feel wish is a follow-up for the end-of-v1 design walk-through, not a fix.

- [ ] **Step 6: Record and close**

Add to `docs/superpowers/plans/2026-09-23-plan1-followups.md`, under a new "Plan 5" heading: the checklist's outcome in a few lines, each fault and its commit, and any taste notes for the end-of-v1 design walk-through. Close the AVD (`adb -s "$S" emu kill`). Commit:
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 5's emulator walk-through"
```

---

### Task 17: On the tablet: Ring's real notifications, the parser fixed, final review, merge and install (§8.2, §8.3)

**Review:** the controller with the user; an opus final review of the whole branch. **Needs the son's SM-T510 on the cable and the household's yes for the Ring app.**

**Files:**
- Modify: `provider/door-ring/src/test/java/uk/co/siland/culvery/provider/door_ring/RingFixtures.kt` (captures replace the guesses), `RingParserTest.kt`, `provider/door-ring/src/main/java/uk/co/siland/culvery/provider/door_ring/RingParser.kt`
- Modify: `docs/superpowers/plans/2026-09-23-plan1-followups.md`

**Rules.** 4e's tablet rules apply (`docs/superpowers/plans/2026-10-12-4e-walkthrough.md`, "Rules"): save and restore every setting changed, never touch other apps' data, install signed releases with `install -r`, **never uninstall Culvery** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` is a stop and ask), the serial in `$TMP/culvery-4e-serial` (`S=$(cat "$TMP/culvery-4e-serial")`), device paths inside one quoted `shell` string, and the names check before anything is committed. Captures stay in `$TMP` and are deleted at the end; nothing raw is committed.

- [ ] **Step 1: Ring on the tablet (the household)**

With the household's yes: they install the Ring app, sign in (a Ring Shared User is the advice, §2.11), and turn off the Ring app's notification sound. Record only that it was done.

- [ ] **Step 2: Install the release and connect Ring**

Build the signed release as `docs/setup/release.md` says, then `adb -s "$S" install -r app/build/outputs/apk/release/app-release.apk`. The user opens Settings › Door (Admin PIN), sees step 1 ticked, allows notification access through step 2, and comes back pinned; the Door tab appears.

- [ ] **Step 3: Capture**

The user turns on **Check Ring’s notifications**. `adb -s "$S" logcat -c`, then the user presses the bell, waits for Ring's snapshot to follow, walks past the camera for motion, and opens the Ring app once (for any app notices). Then:
```bash
adb -s "$S" logcat -d -s RingReader:I > "$TMP/ring-capture.txt"
```
The user turns the switch off (or it lapses after 30 minutes). Read the file: each `Ring notice:` line is one `RingNotice(…)` with its extras' keys.

- [ ] **Step 4: Fixtures from the captures (failing first)**

Rewrite `RingFixtures.kt` from the captures: one fixture per kind seen (the ring, its snapshot re-post with the same key, motion, each app or account notice), the device names and any person replaced by `[device]` and `[person]`, keys kept in shape but with their numbers changed. Its KDoc becomes "Captured on the SM-T510 on <date> from the Ring app, redacted: [device] and [person] stand for the household's names." Update `RingParserTest`'s expected devices to `"[device]"`, and add a test for any notice kind the captures show that the guesses didn't cover. Run the names check (the 4e walk-through's grep, with the user's names list) over the two files. Then:
Run: `./gradlew :provider:door-ring:testDebugUnitTest --tests '*RingParserTest*'`
Expected: FAIL where Ring's real words, channels or categories differ from the guesses (if nothing fails, the guesses were right: say so and go to Step 6).

- [ ] **Step 5: Fix the parser**

Change only `RingParser`'s words, categories and device rule (and `ringPost` if the picture arrives under another extra, e.g. the large icon) until the captures read right; keep the unknown-alert fallback. Run the module's tests, then `./gradlew testDebugUnitTest verifyRoborazziDebug`. Commit:
```bash
git add provider/door-ring/src
git commit -m "Read Ring's notifications as the tablet's captures show them"
```

- [ ] **Step 6: The real thing**

Rebuild the release and `install -r` it. The user presses the bell: the card and the chime reach the wall within a few seconds of Ring's own notification, with the snapshot when Ring sends it; motion appears in the Door tab without a card; Last heard updates. Delete `$TMP/ring-capture.txt`. Restore anything changed under the 4e rules; leave Culvery pinned in front.

- [ ] **Step 7: Final review, merge, install**

Update the follow-ups file (what the captures showed, in words without names; anything left for later). Commit. Then the opus final review of the whole branch against the spec, one fix round, and `superpowers:finishing-a-development-branch` (the user has always chosen to merge locally; never push). Build the release from the merged `main`, `install -r` it on the tablet, and check the bell once more.

---

## Spec coverage (5 design → tasks)

| Spec | Task |
|---|---|
| §A.1 the mark on the rail | 1 |
| §A.2 clock 88 sp, Today 20/16 sp | 2 |
| §A.3 a failed Update now says why | 3 |
| §A.4 repeats: chips, rules, UNTIL, labels, defaults, Ends | 4, 5 |
| §1 outcome, §8.3 real captures | 16, 17 |
| §2.1–2.3 card, history, chime | 12, 13, 14 |
| §2.4 Door tab at slot 50 | 9, 14 |
| §2.5 badge, seen | 6, 9, 13, 14 |
| §2.6 quiet hours | 9, 13, 15 |
| §2.7 not in front: record only | 7, 13 |
| §2.8 snapshots for 48 hours | 9 |
| §2.9 2 minutes, Got it, a second ring | 13 |
| §2.10 the alert layer | 7 |
| §2.11 Shared User advice | 11 (the hint), 15, 17 |
| §2.12 best-guess fixtures | 10, 17 |
| §2.13, §4.1 notification reader, Ring only | 11 |
| §3.1 `:capability:door` | 9, 11–15 |
| §3.2 `:provider:door-ring` | 10, 11 |
| §3.3 `RailBadge`, `AlertHost` | 6, 7 |
| §3.4 rail badge, the layer, `openSystemScreen` | 6, 7, 8 |
| §3.5 backup: no new rule | Global Constraints (no task) |
| §4.2 parse, §4.3 merge, §4.4 snapshot, §4.5 prune | 9, 10, 11 |
| §4.6 alert, §4.7 seen, §4.8 badge | 9, 13, 14 |
| §4.1 (chime) the sound, volume, attributes | 13, 15 |
| §4.2 (last heard) | 11, 15 |
| §5.1 the card | 12, 13 |
| §5.2 the tab | 14 |
| §5.3 the rail badge | 6, 9 |
| §5.4 Settings › Door | 15 |
| §5.5 connecting Ring | 8, 15 |
| §6 when things go wrong; privacy | 11, 15 |
| §7 tests; debug fake rings | every task; 11 |
| §8.1 second AVD | 16 |
| §8.2, §8.3 the tablet | 17 |
| §9 not in this plan | none |
