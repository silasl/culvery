# Culvery — Plan 4a: Household setup wizard and Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A fresh install walks the household through a first-run wizard (welcome, home town, the first Admin and their PIN, the rest of the household, connecting and reviewing the family's calendars) before Home ever shows, and an Admin can change all of it later in a two-pane Settings that stays open while it is being used.

**Architecture:** A new `:core:setup` owns the wizard frame, the Settings frame, the core pages and `SetupState` (one DataStore flag, decided once per install, D8); capabilities contribute their own wizard steps and Settings pages through `Capability.setupSteps()` / `settingsPages()` (D7), so the calendar adds Connect, Review calendars and Settings › Calendars without `:core:setup` knowing it. `AccessControl` gains an in-memory setup session (no PIN, no timeout, ended at Done) and `touch()` for Settings; the household gains name and colour rules, an eight-colour palette and one-transaction member edits; `calendar.db` v5 stores the service's last-seen tick so the daily refresh never undoes a hide made on the tablet. A new `:provider:weather-openmeteo` binds town search (Open-Meteo geocoding, cancellable OkHttp) for 4b to extend with the forecast.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1 (KSP), Room 2.8.5 + room-testing (androidx.sqlite 2.6.2), DataStore Preferences 1.2.1 (new), Coroutines 1.10.2, OkHttp 4.12.0 + MockWebServer 4.12.0, kotlinx.serialization 1.9.0, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1.

**Spec:** `docs/superpowers/specs/2026-09-29-culvery-4a-setup-settings-design.md` (binding). Parent spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§7, §8, §9.5, §9.6, §10).
**Previous plan (format, constraints, review outcome):** `docs/superpowers/plans/2026-09-28-culvery-03a-google-calendar.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (spec §8 says which 4a takes). **Setup doc:** `docs/setup/google-calendar.md`.

**Plan series:** 1 Foundation (done) · 2a Calendar read path (done) · 2b-1 Change events (done) · 2b-2 Add and edit (done) · 3a Google Calendar (done) · **4a Household setup and Settings (this plan)** · 4b Weather · 4c Release and the on-device pass.

**Task order and why:** contracts and data first, then the logic that uses them, then the screens, then the app wiring, so every task ends green and each layer is tested before anything depends on it.
1. The contracts (`SetupStep`, `SettingsPage`, the capability hooks), the `:core:setup` module and `SetupState` (D8).
2. The person palette and the shared controls promoted to `:core:ui` (chips, text field, person chip, swatch, switch, the sheets' footer button, `SingleAction`); the calendar's screenshots must not move.
3. Household rules: names, colours, eight people, one-transaction member edits, `addPerson`'s order in the transaction, Family refused, PINs set with the person.
4. Access: the setup session (ended by 10 idle minutes), `touch()`, and the choose-a-PIN pad.
5. `LocationSearch` and `:provider:weather-openmeteo`.
6. `calendar.db` v5, the refresh rule, `setMapping`, `remapMissingPeople`, the master always shown, the queued-change count, and the household follower (removed people, zone changes).
7. The wizard frame: navigation, resume, dots, Skip for now, and the PIN gate whenever the setup session is gone.
8. People: `PeopleEditor` (rules, fresh PIN, lock) and the list and editor sheet.
9. The core wizard steps: Welcome, Home location (town search), You, Household, Done.
10. The Settings frame, its core pages (Home location, People, Kiosk), and Exit kiosk's move.
11. The calendar's Connect step, Review calendars (wizard and Settings), and Disconnect.
12. `:app`: the wizard or the shell, touches (sheets included) that keep Settings open, lock-task as soon as setup completes, Settings replacing the placeholder.
13. The debug seed reworked and **Use a sample household**.
14. The emulator walkthrough with the user's Google account, the **USER CHECKPOINTS**, the README, the setup doc and the follow-ups.

## Rulings against the code

Where the spec is ambiguous or doesn't fit the code as it stands, this plan rules as follows. Each is pinned by a test in the task named.

1. **The step contract needs three more members (Task 1).** Spec §3.2's `done` both decides where the wizard resumes and enables Next. That can't work for Welcome (Start must be tappable before Welcome is "done"), You (Next *creates* the Admin, so You can't be done before Next) or Done (never "done", or a resume would skip it). `SetupStep` therefore also has `canGoOn: Flow<Boolean>` (default `done`; enables Next), `nextLabel` (default "Next"; Welcome "Start", Done "Open Culvery") and `suspend fun onNext(): Boolean` (default true; runs before the wizard moves, false stays). Resume still uses `done` alone.
2. **D8's "first start of this version" is "the first read that finds no stored flag" (Task 1).** The flag is stored at that read: true if an active Admin exists, false otherwise. A fresh install therefore stores false before its wizard creates an Admin, so a kill after the You step can never mark setup complete (spec §9). A setup file that can't be read is replaced by an empty one, and a missing flag is always decided from `household.db` (an active Admin or not), so corruption never flips an existing install into the wizard. Tests pin both.
3. **"The app's OkHttp client" doesn't exist (Task 5).** 3a deliberately kept Google's client inside `GoogleApi`, out of the graph, so no other module's client can collide with it. The Open-Meteo module builds its own client inside its `@Provides`, with the same timeouts.
4. **`LocationSearchException` carries no cause (Task 5).** An `IOException`'s text can hold the request URL, which holds the query; the exception is thrown with fixed words so a caller that logs it can't leak the town.
5. **A blank name stays `IllegalArgumentException` (Task 3).** The existing contract and test say so; the sheet's Save is disabled while the name is blank. Nobody may be called "Family" (whatever the case): that is a `DuplicateNameException`, "Someone is already called Family."
6. **Adding a person asks for a fresh PIN (Task 8).** D6 lists only edits; adding sets a role, and usually a PIN, so it takes `people.manage`. In the wizard the setup session passes it silently.
7. **A person's edits are one transaction (Task 3).** "Nothing changes" on a failure (§5) can't hold if rename, role and PIN are separate writes, so `HouseholdRepository.updateMember` applies name, colour, role and PIN together, with the name, colour and last-Admin rules checked inside; the PIN is hashed (and checked for clashes) first. A new person's PIN is stored with them (`PinManager.addPerson`), so a clash never leaves someone added without it. The single-field methods stay.
8. **An Admin always needs a PIN (Task 8).** Choosing Admin without a PIN, or removing an Admin's PIN, is refused with "An Admin needs a PIN." before any PIN pad; the last-Admin rule ("Culvery needs at least one Admin with a PIN.") stays the repository's.
9. **`endSetupSession()` alone never leaves a session without a timeout (Task 4).** It ends the setup session and starts the usual two minutes; Done then calls `lock()` as the spec says. `lock()`, a PIN entered at a pad, and the session's own expiry all end a setup session; only `beginSetupSession` starts one. The setup session itself ends after 10 minutes without a touch in the wizard (spec D9 and §3.4, amended with this review).
10. **A PIN or role change of yourself during the wizard signs you out (Task 8).** §3.7's `lock()` rule applies in setup too; the wizard's PIN gate then asks for the PIN and begins the setup session again. The setup session is never carried across such a change.
11. **The PIN gate follows the session (Task 7).** It shows whenever an active Admin exists, nobody is signed in and Done isn't finishing: after a kill past You, after the setup session's 10 idle minutes, after a lock. It opens the PIN pad at once with "Enter your PIN to carry on setting up" (§3.4); if the pad is cancelled, the wizard shows that line and an **Enter PIN** pill (the spec gives no copy for this). Done tells the gate it is finishing before it signs out, so the gate never flashes. Nothing about the gate is saved, so a wizard restored after its process died asks again. Home location comes before You in order, so it too takes `settings.manage` once an Admin exists (Task 9), and so does Done (the setup session passes both silently).
12. **The refresh's "tick" is `visibleOnTablet` (Task 6).** §3.10 compares the service's `shown`. 3a shows the primary calendar whatever its tick, and `MIGRATION_4_5` copies `visible` (which is `shown || primary`) into `shownInService`; comparing against `shown || primary` means an upgraded row reads as unchanged and the 3a primary rule stands. The master stays shown whatever the service says.
13. **"{n} changes" for one change (Task 11).** The disconnect line reads ", and 1 change still waiting to sync is dropped." for one; the spec's wording otherwise.
14. **The Settings › Calendars page keeps 3a's Connect pills (Task 11)** under the review list, so a household that skipped the wizard's Connect can still connect from Settings.
15. **Exit kiosk moves through `ShellNavigator` (Task 10).** The Kiosk page lives in `:core:setup` and can't see `ShellViewModel`; `ShellNavigator` gains `exitKiosk()`, which the view model already implements.
16. **Pinning when setup completes (Task 12; spec §3.6 amended).** `setupComplete` is read asynchronously, so the activity pins whenever it turns true (the first read included) while resumed, as well as on every resume once it is true. Waiting for "the next resume" left a household that had just tapped Open Culvery unpinned until the tablet next came to the front.
17. **`HomeLocation.name` holds the result's full line** ("Brighton, England, United Kingdom"), which Settings shows as the current location (Task 9).
18. **Copy and layout the spec doesn't give (Tasks 8, 9, 10):** the search field's placeholder "Town or city"; the editor sheet's heading "Add person" (new) or the person's name; a row's second line "{role} · PIN set"; each role chip carries its whole line ("Admin — can change settings and people") rather than a name and a line apart; each Settings page is titled with its name. Once the Admin exists, the You page shows their row (tap to edit in the sheet) instead of the empty form.
19. **"Couldn't save — try again." is one constant in `:core:plugin` (`COULD_NOT_SAVE`)**, shared by the people editor and the calendar's review page (Tasks 1, 8, 11). `SingleAction` and the calendar sheets' footer button (`HhSheetButton`) move to `:core:ui` for the same reason (Task 2).
20. **At most eight people is enforced twice (Task 3, 8):** the repository refuses a ninth, and **Add person** is hidden once eight exist (the spec gives no copy for "full").
21. **Touches that keep Settings open include its sheets and PIN pads (Task 12).** They draw in the shell's overlay layers, above Settings, so the touch observer sits on the `ShellLayers` root while Settings is open (and while the wizard shows, for the setup session's idle limit). Closing Settings dismisses any sheet it opened.

Nothing in this plan needs a deprecated API. One new dependency: **DataStore Preferences 1.2.1** (the spec asks for a DataStore file; the user chose the version).

## Global Constraints

- Package root `uk.co.siland.culvery`; app name "Culvery". New packages: `uk.co.siland.culvery.core.setup`, `uk.co.siland.culvery.provider.weather_openmeteo`.
- `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Pinned versions: AGP 8.13.0, Gradle 8.13, Kotlin 2.2.20, KSP 2.2.20-2.0.3, Compose BOM 2025.09.00, Hilt 2.57.1, Room 2.8.5 (with `room-testing` 2.8.5), androidx.sqlite 2.6.2, Robolectric 4.16, Roborazzi 1.46.1, OkHttp/MockWebServer 4.12.0, kotlinx-serialization-json 1.9.0. **New in this plan:** `androidx.datastore:datastore-preferences` 1.2.1 (if its AAR metadata needs `compileSdk` above 35, stop and ask). All in `gradle/libs.versions.toml`. If a version fails to resolve, take the newest **patch** in the same minor line. Never move to a new major or minor version without asking.
- **Deprecated APIs:** use none without asking the user first. If any API this plan uses shows a deprecation warning in these versions, **stop and ask**. The ones worth checking:
  - `forEachGesture` is deprecated: the Settings touch uses `awaitEachGesture` with `awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)` (Task 10);
  - `androidx.compose.ui.platform.LocalLifecycleOwner` is deprecated: don't use it (nothing here needs it);
  - OkHttp 4's Java-style accessors (`response.body()`, `response.code()`, `request.url()`, `RecordedRequest.getPath()` as a call): use the properties and `toHttpUrl()` (Task 5);
  - `CancellableContinuation.resume(value, onCancellation)` with a one-argument lambda is deprecated: `Call.await` uses the plain `kotlin.coroutines.resume(value)` (Task 5);
  - `PreferenceDataStoreFactory.create(corruptionHandler, …, produceFile)`, `ReplaceFileCorruptionHandler`, `preferencesDataStoreFile`, `booleanPreferencesKey`, `edit` (Task 1): if any is deprecated in 1.2.1, stop and ask;
  - Material 3 `Switch` and `SwitchDefaults.colors(…)` (Task 2); `BasicTextField(value: String, …)` (as the calendar already uses it);
  - MockWebServer's `setHeadersDelay` and `SocketPolicy.DISCONNECT_AFTER_REQUEST` (Task 5).

  `@OptIn` to an *experimental* API is allowed only where the plan says so: `ExperimentalCoroutinesApi` in tests, as today. **No `Flow.debounce`** (it is `@FlowPreview`): the town search waits with `delay` inside an effect keyed on the query.
- Design canvas 1280×800 dp. Hand-off §7 values are authoritative, then the spec's §4. Copy the values exactly as the steps give them.
- No shadows; flat colours; no blur.
- No secrets, tokens or household data in source or build config. Sample people live only in `app/src/debug` and `:provider:calendar-fake`.
- PINs are exactly 4 ASCII digits.
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on the current branch (`main`); never push.
- Module rules (enforced by `build-logic`'s `ModuleBoundaries`):
  - `:core:*` depends only on `:core:*`. **New:** `:core:setup` depends on `:core:plugin`, `:core:household`, `:core:access`, `:core:ui`.
  - `:capability:X…` depends only on `:core:*` and its own family. `:capability:calendar` may depend on `:core:setup` (tests only in this plan).
  - `:provider:X-…` depends only on `:core:*` and `:capability:X`. **New:** `:provider:weather-openmeteo` depends on `:core:setup` only (there is no `:capability:weather` until 4b).
  - `:app` may depend on anything; it gains `:core:setup` and `:provider:weather-openmeteo` in every build type.
  - Read `ProjectDependency.path`, never the deprecated `dependencyProject`.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test` or `check`. A task that touches one module runs that module's `testDebugUnitTest` (plus its `verifyRoborazziDebug` where it has screenshots) while working; every task ends with the full gate before its commit.
- Screenshots:
  - Baselines live in `<module>/src/test/screenshots/`, recorded and verified on Windows.
  - Record with `./gradlew <module>:recordRoborazziDebug --tests "<pattern>"` to record only the new images.
  - Look at every new or changed image before committing; the step says what each must show.
  - `@GraphicsMode(GraphicsMode.Mode.NATIVE)` goes only on classes that capture screenshots.
  - A module's tests share one canvas size, one `StillPage`, one `RecordingNavigator`, one `TouchModeRule` and one `assertNoSecretsLogged` (`core/setup`'s `TestUi.kt`; the calendar keeps its own, `TestLogs.kt` beside its `RecordingNavigator` and `TouchModeRule`).
- Colours and type:
  - Colours come only from `Culvery.colors`, a person's own colour (`PersonPalette`, or a stored person's), `ShellTokens`' two scrims, and `DarkColors.bg` as a person chip's ink.
  - Destructive and warning tones use `danger`, `dangerSoft` and `dangerInk`.
  - Text styles come from `HhType`, or named `.copy()`s of it in `ControlType` (`:core:ui`), `SetupType` (`:core:setup`), `CalendarType`, `ShellType` or `PinPadType`.
  - **Layout numbers live in `SetupDimens` (`:core:setup`), `ControlTokens` (`:core:ui`), `CalendarDimens`, `ShellTokens` or `PinPadDimens`**, never inline. Timing values are named constants.
- **Storage / migration policy:** `calendar.db` holds user configuration. **v5 ships `MIGRATION_4_5`, its `CalendarMigrationTest` case (with the table list) and `schemas/…/5.json`.** `MigrationTestHelper` uses the driver-based constructor with `AndroidSQLiteDriver`; copy the pattern in `CalendarMigrationTest`. KSP writes the schema JSON while compiling: if the migration test can't find `5.json` on the first run, run it once more. `household.db` doesn't change schema. `SetupState` is its own DataStore file, `setup.preferences_pb`.
- **Access:**
  - Viewing never needs a PIN. Every change calls `AccessControl.authorise` **when tapped**; buttons are never hidden for permission reasons.
  - In Settings: rename and recolour need `settings.manage` (the open session); add, remove, role change and PIN set/change/remove need `people.manage` (fresh PIN); calendar mappings, visibility, master and disconnect need `settings.manage`; Exit kiosk needs `kiosk.exit` (fresh PIN).
  - In the wizard: before the You step, pages call the repositories directly; from You on, they call `authorise` as in Settings and the setup session satisfies it.
  - After a remove, role change or PIN change of the signed-in person, `AccessControl.lock()`.
  - A touch anywhere while Settings is open (its sheets and PIN pads included), or anywhere in the wizard, calls `AccessControl.touch()`. The setup session ends after 10 minutes without one (`SETUP_IDLE_MS`).
  - `beginSetupSession` is the wizard's alone: the shell never calls it (a test pins it).
- **Privacy in logs:** nothing logs a PIN, a person's name, a place name or query, coordinates, or an account email. Log an exception's class name, never its message, wherever the message could hold one of these. Each task with a failure path has a test that reads `ShadowLog`.
- **Every save goes through `SingleAction`** (now in `:core:ui`), so a double tap saves once.
- Tests and threads (as 3a): Room runs on its own threads; wait for it in bounded real time with `withContext(Dispatchers.Default) { withTimeout(5_000) { … } }`; asynchronous UI outcomes use `compose.waitUntil(5_000) { … }`; a tag under a clickable parent is found with `useUnmergedTree = true`; never change production semantics for a test. Compose tests that step time set `compose.mainClock.autoAdvance = false`. **A UI test over Room or DataStore waits for its first content** (`compose.waitUntil(5_000) { compose.onAllNodesWithText(…).fetchSemanticsNodes().isNotEmpty() }`) before its first assertion or tap. A test that types uses `TouchModeRule` at `@get:Rule(order = 0)`. No fixed sleeps in tests: wait for a condition, bounded.
- **Debug-only code lives in `app/src/debug`** and must not reach release: Task 13 checks the release APK.
- The shell's overlay layers live inside `ShellLayers`; the wizard and Settings both draw inside it, so sheets, the PIN pad and toasts work there. Don't restructure it.
- **Copy (spec §3.4, §4, §5), exactly:**
  - Wizard: "Welcome to Culvery"; "Your home's location, the people who live here, and your calendars."; "Start"; "Back"; "Next"; "Skip for now"; "Who's setting this up?"; "Set your PIN"; "Choose a 4-digit PIN"; "Enter it again"; "Those PINs didn't match — try again."; "Who else lives here?"; "Culvery is ready"; "Open Culvery"; "Enter your PIN to carry on setting up"; "Use a sample household" (debug only).
  - Location: "Where's home?"; "Used for the time zone, and for weather."; "Couldn't search for towns — check the tablet's Wi-Fi and try again."; `No towns match "{query}".`
  - People: "Add person"; "PIN set"; "No PIN"; "Name"; "Colour"; "Role"; "PIN"; "Admin — can change settings and people"; "Adult — can add and change any event"; "Child — can add their own events"; "Set PIN"; "Change PIN"; "Remove PIN"; "Save person"; "Save changes"; "Remove person"; "Someone is already called {name}."; "That colour is taken."; "That PIN is taken — choose another."; "An Admin needs a PIN."; "Culvery needs at least one Admin with a PIN."; "Remove {name}? {name}'s events and calendars show as Family."; "Keep"; "{name} removed"; "Couldn't save — try again."
  - Calendars: "Your calendars" (wizard); "Calendars" (Settings); "{Service} · {account}"; "Synced 2 min ago" (3a's `healthWords`); "Needs reconnecting"; "Can't reach {Service}"; "Reconnect"; "Disconnect"; "Show"; "Master"; "New events go here"; "Make master"; "{calendar} now shows as {person}"; "{calendar} hidden"; "{calendar} shown"; "New events now go to {calendar}"; "Disconnect {Service}? Its calendars leave the tablet." / "…, and {n} changes still waiting to sync are dropped."; "{Service} disconnected"; "{Service}: can't find the master calendar — choose a new one in Settings › Calendars."
  - Settings: "Settings"; "Home location"; "People"; "Calendars"; "Kiosk"; "Close"; "Exit kiosk"; "Culvery keeps the tablet on this app. Exit to use other apps; it locks again next time Culvery opens."

## Review Focus

The spec's review-focus inputs (§9) and this plan's own, each pinned by named tests in the task that owns the code:

1. **A kill at every wizard step resumes at the right step with nothing duplicated** (no second Admin, no second connection).
   - Task 7 `WizardRulesTest.itResumesAtTheFirstShownStepThatIsNotDone`, `aSkippedStepIsWhereItResumes`, `aHiddenStepIsNeverWhereItResumes`
   - Task 9 `StepsTest.aSecondNextAfterTheAdminExistsAddsNobody`, `aKillAfterYouResumesPastIt`, `welcomeIsDoneOnceStartedAndStaysDoneAfterAKill`
   - Task 7 `SetupWizardTest.aDoubleTapOnNextRunsTheStepOnce`
   - Task 7 `WizardRulesTest.itResumesAtReviewOnceConnectIsDone`
   - Task 11 `CalendarReviewTest.connectingTheSameAccountTwiceKeepsOneConnection`; `CalendarCapabilityTest.itAddsConnectAndReviewToTheWizardAndCalendarsToSettings` (Connect is done once any connection exists)
2. **The setup session can't outlive setup**: after Done, or after a kill before Done, the next start has no silent Admin session.
   - Task 4 `aSetupSessionEndsAfterTenMinutesWithoutATouch`, `aPinEnteredDuringSetupEndsTheSetupSession`, `lockEndsTheSetupSession`, `endingTheSetupSessionStartsTheUsualTwoMinutes`; `ShellViewModelTest.theShellNeverBeginsASetupSession`
   - Task 9 `StepsTest.openCulveryEndsTheSetupSessionBeforeSetupIsComplete`, `withNobodySignedInOpenCulveryAsksForAnAdmin`; `StepsUiTest.openCulveryNeverShowsTheGate`
   - Task 7 `SetupWizardTest.anAdminWithNobodySignedInIsAskedForTheirPinBeforeAnyStep`, `whenTheSetupSessionEndsTheWizardAsksForThePinAgain`, `aRestoredWizardAsksForThePinAgain`
3. **An existing install (Admin present) never sees the wizard; a fresh one always does.**
   - Task 1 `SetupStateTest.anInstallWithAnActiveAdminIsCompleteOnItsFirstStart`, `aFreshInstallIsNotComplete`, `anAdminAddedAfterTheFirstStartNeverCompletesSetup`, `aCorruptSetupFileIsDecidedAgainFromTheHousehold`
   - Task 12 `AppContentTest.untilSetupIsKnownNothingShows`, `anIncompleteSetupShowsTheWizard`, `aCompleteSetupShowsTheShellAndSettings`
   - Task 13 `DebugSeedTest.aFreshDebugInstallHasNoPeopleNoCalendarAndIsNotSetUp`
4. **The daily refresh never re-shows a calendar the user hid unless its tick in the service changed.**
   - Task 6 `aHiddenCalendarStaysHiddenWhileItsTickIsUnchanged`, `aChangedTickInTheServiceWins`, `theMasterStaysShownWhateverTheServiceSays`, `migrationFromV4CopiesVisibilityIntoTheLastSeenTick`
5. **No PIN, place name, coordinates or account email reaches a log.**
   - Task 5 `nothingLoggedHoldsTheQueryOrAPlace` (every failure path)
   - Task 8 `aStoreFailureSaysCouldNotSaveAndLogsNoNameOrPin`
   - Task 11 `aFailedCalendarChangeToastsAndLogsNoAccountOrCalendar`
6. **The household can't lose its last Admin through the editor** (demote, remove, clear PIN, or an Admin with no PIN).
   - Task 3 `theLastAdminIsKeptThroughAMemberEdit`; Task 8 `theLastAdminCannotBeDemotedRemovedOrLoseTheirPin`, `anAdminWithoutAPinIsRefusedBeforeAnyPinPad`
7. **Settings stays open while an adult uses it, and closes two minutes after the last touch.**
   - Task 4 `aTouchRestartsTheTwoMinutes`; Task 12 `AppContentTest.aTapInsideAnOpenSheetKeepsSettingsOpen`, `closingSettingsDismissesItsSheet`; `SetupWiringTest.touchesCountWhileSettingsIsOpenOrTheWizardShows`
8. **A person removed while a calendar is mapped to them doesn't leave the calendar pointing at nobody.**
   - Task 6 `HouseholdFollowerTest.aCalendarMappedToSomeoneRemovedShowsAsFamily`, `CalendarStoreTest.calendarsOfSomeoneGoneBecomeFamilyAndTheRestStay`
9. **A mistyped or changed town search never sends two searches at once, and a slow one never overwrites a newer answer.**
   - Task 9 `LocationPaneTest.aNewQueryCancelsTheSearchBeforeIt`, `itSearchesOnlyAfterTwoLettersAndAPause`; Task 5 `cancellingTheSearchCancelsTheCall`
10. **The setup session ends after 10 minutes without a touch, and the wizard carries on after a PIN** (spec D9 amended).
   - Task 4 `aSetupSessionEndsAfterTenMinutesWithoutATouch`, `aTouchRestartsTheSetupSessionsTenMinutes`, `aSetupSessionOutlastsTheUsualTwoMinutes`
   - Task 7 `SetupWizardTest.whenTheSetupSessionEndsTheWizardAsksForThePinAgain`
11. **Finishing setup pins the kiosk at once, not at some later resume.**
   - Task 12 `SetupWiringTest.theKioskPinsAsSoonAsSetupCompletesWhileResumed`

---

## File Structure

```
gradle/libs.versions.toml                          (modify: datastore)
settings.gradle.kts                                (modify: include :core:setup, :provider:weather-openmeteo)

core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/
  Setup.kt             (create: SetupStep, SettingsPage, NEXT_LABEL, COULD_NOT_SAVE)
  Capability.kt        (modify: setupSteps(), settingsPages(); SettingsSection() removed in Task 12)
  ShellNavigator.kt    (modify: exitKiosk(), Task 10)

core/ui/src/main/java/uk/co/siland/culvery/core/ui/
  PersonPalette.kt     (create)
  Controls.kt          (create: ControlTokens, ControlType, HhChoiceChip, HhTextField, HhPersonChip, HhSwatch, HhSwitch,
                        ButtonTone, HhSheetButton)
  SingleAction.kt      (create: moved from the calendar's Sheets.kt)
  Components.kt        (modify: HhPillButton gains enabled)
core/ui/src/test/…/PersonPaletteTest.kt, ControlsTest.kt   (create)

core/household/src/main/java/uk/co/siland/culvery/core/household/
  Model.kt, HouseholdRepository.kt                 (modify)
core/household/src/test/…/HouseholdRepositoryTest.kt   (modify)

core/access/src/main/java/uk/co/siland/culvery/core/access/
  AccessControl.kt, DefaultAccessControl.kt, PinManager.kt   (modify)
  ui/PinPad.kt                                     (modify: PinPadFrame, ChoosePinPad)
core/access/src/test/…/DefaultAccessControlTest.kt, PinManagerTest.kt, ui/ChoosePinPadTest.kt (create)

core/setup/                                        (create)
  build.gradle.kts
  src/main/java/uk/co/siland/culvery/core/setup/
    SetupState.kt, LocationSearch.kt, SampleHousehold.kt
    SetupDimens.kt (SetupDimens, SetupType), SetupCopy.kt (every string), SetupUi.kt (StepTitle)
    WizardRules.kt, SetupWizard.kt, SetupSessionGate.kt
    PeopleEditor.kt, PeopleUi.kt, PersonEditorSheet.kt
    LocationPane.kt
    steps/WelcomeStep.kt, steps/LocationStep.kt, steps/YouStep.kt, steps/HouseholdStep.kt, steps/DoneStep.kt
    SettingsScreen.kt, pages/LocationPage.kt, pages/PeoplePage.kt, pages/KioskPage.kt
    di/SetupModule.kt
  src/test/resources/robolectric.properties
  src/test/java/uk/co/siland/culvery/core/setup/
    TestHousehold.kt, TestUi.kt (canvas, StillPage, RecordingNavigator, TouchModeRule, assertNoSecretsLogged),
    RecordingOverlay.kt, SetupStateTest.kt, WizardRulesTest.kt, SetupWizardTest.kt, PeopleEditorTest.kt,
    PeopleUiTest.kt, LocationPaneTest.kt, StepsTest.kt, StepsUiTest.kt, SettingsScreenTest.kt, SetupScreenshotTest.kt,
    SettingsScreenshotTest.kt
  src/test/screenshots/*.png

provider/weather-openmeteo/                        (create)
  build.gradle.kts, src/main/AndroidManifest.xml
  src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoLocationSearch.kt, di/OpenMeteoModule.kt
  src/test/resources/robolectric.properties
  src/test/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoLocationSearchTest.kt

capability/calendar/
  build.gradle.kts                                 (modify: testImplementation :core:setup)
  schemas/…CalendarDatabase/5.json                 (generated, committed)
  src/main/java/uk/co/siland/culvery/capability/calendar/
    db/CalendarDatabase.kt, db/Migrations.kt, di/CalendarModule.kt   (modify: v5)
    CalendarStore.kt, SourceRefresher.kt           (modify)
    HouseholdFollower.kt (create), CalendarReview.kt (create), CalendarSetupSteps.kt (create)
    CalendarCapability.kt                          (modify: steps and page; SettingsSection gone)
    ui/ReviewCalendars.kt (create), ui/CalendarSettings.kt (delete), ui/Sheets.kt, ui/EventEditorSheet.kt,
    ui/EventDetailSheet.kt, ui/EventDetailHost.kt, ui/EventEditorHost.kt, ui/Components.kt, ui/CalendarType.kt   (modify)
  src/test/java/uk/co/siland/culvery/capability/calendar/
    CalendarStoreTest, CalendarMigrationTest, SourceRefresherTest, StubEditor, CalendarCapabilityTest, ui/ConnectScreenshotTest,
    ui/RecordingNavigator, the fakes' new AccessControl members   (modify)
    HouseholdFollowerTest, CalendarReviewTest, TestLogs (assertNoSecretsLogged), ui/ReviewCalendarsTest, ui/ReviewScreenshotTest   (create)
    ui/CalendarSettingsTest (delete)
  src/test/screenshots/settings_calendars_*.png (delete); review_*, connect_step_*, settings_calendars_page_* (new)

app/
  build.gradle.kts                                 (modify)
  src/main/java/uk/co/siland/culvery/MainActivity.kt, CulveryApp.kt, shell/ui/OverlayLayers.kt (ShellLayers' onTouch)   (modify)
  src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt   (modify, Task 10: exitKiosk overrides)
  src/main/java/uk/co/siland/culvery/SetupWiring.kt   (create: wizardSteps, settingsPages, pinOnSetupRead, touchTarget, AppContent)
  src/main/java/uk/co/siland/culvery/shell/ui/SettingsPlaceholder.kt   (delete)
  src/debug/java/uk/co/siland/culvery/DebugSeed.kt, di/DebugSetupModule.kt (create), src/release/…/DebugSeed.kt   (modify)
  src/test/java/uk/co/siland/culvery/SetupWiringTest.kt, AppContentTest.kt (create); shell/Fakes.kt, shell/ShellViewModelTest.kt,
    shell/ui/ShellScreenshotTest.kt   (modify)
  src/test/screenshots/settings_dark.png           (delete)
  src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt, SampleAddTest.kt, SampleRollbackTest.kt   (modify)

README.md, docs/setup/google-calendar.md, docs/superpowers/plans/2026-09-23-plan1-followups.md   (modify, Task 14, after the checkpoints)
```

`…` in a path stands for the module's package directory; every step spells out the full path.

---

### Task 1: The setup contracts, `:core:setup`, and whether setup is complete (D7, D8)

**Files:**
- Modify: `gradle/libs.versions.toml`, `settings.gradle.kts`
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Setup.kt`
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Capability.kt`
- Create: `core/setup/build.gradle.kts`, `core/setup/src/test/resources/robolectric.properties`
- Create: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupState.kt`
- Test: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestHousehold.kt`, `SetupStateTest.kt` (create)

**Interfaces:**
- Consumes: `HouseholdRepository.credentials()`, `Credential.isActiveAdmin` (Plan 1).
- Produces:
  - `interface SetupStep { val id: String; val order: Int; val shown: Flow<Boolean> (default flowOf(true)); val done: Flow<Boolean>; val canGoOn: Flow<Boolean> (default done); val skippable: Boolean (default false); val nextLabel: String (default NEXT_LABEL); suspend fun onNext(): Boolean (default true); @Composable fun Content(onNext: () -> Unit) }`
  - `interface SettingsPage { val id: String; val title: String; val order: Int; @Composable fun Content() }`
  - `const val NEXT_LABEL = "Next"`; `const val COULD_NOT_SAVE = "Couldn't save — try again."` (both `:core:plugin`)
  - `Capability.setupSteps(): List<SetupStep>` and `Capability.settingsPages(): List<SettingsPage>`, both empty by default
  - `fun setupStore(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile: () -> File): DataStore<Preferences>` — replaces an unreadable file with an empty one
  - `@Singleton class SetupState` — constructor `(store: DataStore<Preferences>, household: HouseholdRepository)` (public: the debug seed's tests build one); `@Inject` constructor `(@ApplicationContext context: Context, household: HouseholdRepository)`; `val setupComplete: Flow<Boolean>`; `val welcomed: Flow<Boolean>`; `suspend fun markWelcomed()`; `suspend fun markComplete()`
  - Test helpers (`:core:setup` tests): `internal fun householdDb(): HouseholdDatabase`

- [ ] **Step 1: Add the module and the dependency**

In `gradle/libs.versions.toml`:
1. Under `[versions]`, after `playServicesAuth = "21.4.0"`, add `datastore = "1.2.1"`.
2. Under `[libraries]`, after the `kotlinx-coroutines-play-services` line, add:
```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

In `settings.gradle.kts`, after `include(":core:access")` add `include(":core:setup")`.

Create `core/setup/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

dependencies {
    // SetupStep, SettingsPage, AccessControl and the household's types appear in this module's public API.
    api(project(":core:plugin"))
    api(project(":core:access"))
    api(project(":core:household"))
    implementation(project(":core:ui"))
    // SetupState's constructor takes a DataStore, which `:app`'s debug tests build.
    api(libs.androidx.datastore.preferences)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
```

Create `core/setup/src/test/resources/robolectric.properties`:
```properties
qualifiers=w1280dp-h800dp-land-hdpi
```

**Stop and ask the user** if the build then fails with an AAR metadata error asking for `compileSdk` above 35 (DataStore 1.2.1's requirement, not ours to raise), or if any API this task uses from it (`PreferenceDataStoreFactory.create`, `ReplaceFileCorruptionHandler`, `preferencesDataStoreFile`, `booleanPreferencesKey`, `edit`) shows a deprecation warning. Don't fall back to another version without asking.

- [ ] **Step 2: Write the failing tests**

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestHousehold.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

internal fun householdDb(): HouseholdDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
        .allowMainThreadQueries()
        .build()
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SetupStateTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

// Robolectric for Room.
@RunWith(AndroidJUnit4::class)
class SetupStateTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
    }

    /** A start of the app: a new DataStore over the same file, once the last one has let go of it. */
    private suspend fun start(): SetupState {
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return SetupState(setupStore(scope) { file }, household)
    }

    private val file get() = File(folder.root, "setup.preferences_pb")

    private suspend fun addAdmin(withPin: Boolean = true) {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        if (withPin) household.setPinHash(alex.id, "hash", "salt")
    }

    @Test
    fun aFreshInstallIsNotComplete() = runTest {
        assertThat(start().setupComplete.first()).isFalse()
    }

    @Test
    fun anInstallWithAnActiveAdminIsCompleteOnItsFirstStart() = runTest {
        addAdmin()
        assertThat(start().setupComplete.first()).isTrue()
    }

    @Test
    fun anAdminWithoutAPinDoesNotCount() = runTest {
        addAdmin(withPin = false)
        assertThat(start().setupComplete.first()).isFalse()
    }

    @Test
    fun anAdminAddedAfterTheFirstStartNeverCompletesSetup() = runTest {
        assertThat(start().setupComplete.first()).isFalse()
        // The wizard's You step makes the Admin; then the app is killed and starts again.
        addAdmin()
        assertThat(start().setupComplete.first()).isFalse()
    }

    @Test
    fun completeIsKeptAcrossARestart() = runTest {
        val first = start()
        assertThat(first.setupComplete.first()).isFalse()
        first.markComplete()
        assertThat(start().setupComplete.first()).isTrue()
    }

    @Test
    fun aCorruptSetupFileIsDecidedAgainFromTheHousehold() = runTest {
        // Not a preferences file: DataStore reads it as corrupt, and the handler replaces it with an empty one.
        file.writeText("not a preferences file")
        assertThat(start().setupComplete.first()).isFalse()
        addAdmin()
        file.writeText("not a preferences file")
        assertThat(start().setupComplete.first()).isTrue()
    }

    @Test
    fun welcomeIsRememberedAcrossARestart() = runTest {
        val first = start()
        assertThat(first.welcomed.first()).isFalse()
        first.markWelcomed()
        assertThat(start().welcomed.first()).isTrue()
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'SetupState'" and "Unresolved reference 'setupStore'".

- [ ] **Step 4: Write the contracts**

Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Setup.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The wizard's forward button, unless a step names its own. */
const val NEXT_LABEL = "Next"

/** 4a design §5: a setting that didn't save; nothing changed. */
const val COULD_NOT_SAVE = "Couldn't save — try again."

/**
 * One page of the first-run wizard (4a design §3.2, §3.3). Core steps use orders 0–399, capabilities 400 and up in
 * rail order, Done 1000.
 */
interface SetupStep {
    val id: String
    val order: Int

    /** False hides the step (e.g. Review calendars before a connection exists). */
    val shown: Flow<Boolean> get() = flowOf(true)

    /** True once the step's required input is saved: the wizard resumes at the first shown step that isn't. */
    val done: Flow<Boolean>

    /** Enables the forward button; by default once [done]. */
    val canGoOn: Flow<Boolean> get() = done

    /** A skippable step that isn't [done] offers "Skip for now" in place of the forward button. */
    val skippable: Boolean get() = false

    val nextLabel: String get() = NEXT_LABEL

    /** Runs when the forward button is tapped, before the wizard moves on; false keeps the wizard on this step. */
    suspend fun onNext(): Boolean = true

    /** [onNext] does what the forward button does, for a step with a button of its own. */
    @Composable
    fun Content(onNext: () -> Unit)
}

/** One section of Settings (4a design §3.2, §4.6): Home location 0, People 100, Calendars 400, Kiosk 900. */
interface SettingsPage {
    val id: String

    /** Its name in Settings' left-hand list. */
    val title: String
    val order: Int

    @Composable
    fun Content()
}
```

In `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Capability.kt`, after the `SettingsSection()` function (inside the interface) add:
```kotlin

    /** This capability's wizard steps (4a design D7), placed by [SetupStep.order] among the core ones. */
    fun setupSteps(): List<SetupStep> = emptyList()

    /** This capability's Settings pages (4a design D7), placed by [SettingsPage.order]. */
    fun settingsPages(): List<SettingsPage> = emptyList()
```
(`SettingsSection()` stays until Task 12 wires the new Settings.)

- [ ] **Step 5: Write `SetupState`**

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupState.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository

private const val TAG = "SetupState"

/** The setup file's store. A file that can't be read is replaced by an empty one; SetupState decides it again. */
fun setupStore(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = produceFile,
    )

/**
 * Whether first-run setup has finished, and whether Welcome was passed (4a design §3.1, D8): a small DataStore file of
 * its own, not Room, as it is two flags.
 */
@Singleton
class SetupState(private val store: DataStore<Preferences>, private val household: HouseholdRepository) {
    @Inject
    constructor(@ApplicationContext context: Context, household: HouseholdRepository) :
        this(setupStore { context.preferencesDataStoreFile(FILE) }, household)

    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e !is IOException) throw e
        Log.w(TAG, "Couldn't read the setup file (${e::class.simpleName})")
        emit(emptyPreferences())
    }

    /**
     * D8: the first read on an install that has never stored the flag decides it, once: complete when an active Admin
     * exists (an install from before 4a), not complete otherwise. A fresh install stores false at that first read, so
     * the Admin its wizard makes later never marks setup complete (ruling 2). A flag that can't be read or written is
     * decided the same way, from the household, every time.
     */
    val setupComplete: Flow<Boolean> = flow {
        try {
            store.edit { stored -> if (COMPLETE !in stored) stored[COMPLETE] = activeAdmin() }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't write the setup file (${e::class.simpleName})")
        }
        emitAll(prefs.map { it[COMPLETE] ?: activeAdmin() })
    }.distinctUntilChanged()

    /** Welcome was passed, so a start after a kill resumes past it (4a design §6). */
    val welcomed: Flow<Boolean> = prefs.map { it[WELCOMED] == true }.distinctUntilChanged()

    suspend fun markWelcomed() {
        store.edit { it[WELCOMED] = true }
    }

    suspend fun markComplete() {
        store.edit { it[COMPLETE] = true }
    }

    private suspend fun activeAdmin(): Boolean = household.credentials().any { it.isActiveAdmin }

    private companion object {
        const val FILE = "setup"
        val COMPLETE = booleanPreferencesKey("setupComplete")
        val WELCOMED = booleanPreferencesKey("welcomed")
    }
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :core:plugin:testDebugUnitTest :core:setup:testDebugUnitTest`
Expected: PASS (7 tests in `:core:setup`; the contract's defaults are exercised by the wizard's tests from Task 7 and `SetupWiringTest` in Task 12).

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. `:core:setup` has no screenshots yet; its `verifyRoborazziDebug` finds nothing to compare.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts core/plugin core/setup
git commit -m "Add the setup contracts, the :core:setup module and whether setup is complete"
```

---

### Task 2: The person palette and the shared controls in `:core:ui` (D12, §4.4)

**Files:**
- Modify: `core/ui/build.gradle.kts`
- Create: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/PersonPalette.kt`, `Controls.kt`, `SingleAction.kt`
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Components.kt` (`HhPillButton` gains `enabled`)
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/PersonPaletteTest.kt`, `ControlsTest.kt` (create)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Sheets.kt`, `EventEditorSheet.kt`, `EventDetailSheet.kt`, `EventDetailHost.kt`, `EventEditorHost.kt`, `CalendarType.kt`, `Components.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (all `:core:ui`, public):
  - `object PersonPalette { val colors: List<Long>; fun firstFree(taken: Collection<Long>): Long? }`
  - `object ControlTokens` (chips, whose values the person chip shares; text field; swatch; the sheets' footer button; `DISABLED_ALPHA`, `SECONDARY_ALPHA`, `STRIKE_INSET`); `object ControlType { chip; chipSecondary; field; button }`
  - `@Composable fun HhChoiceChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit, enabled: Boolean = true, selectedColor: Color = Culvery.colors.accent, selectedInk: Color = Culvery.colors.accentInk, secondary: String? = null, leading: (@Composable (ink: Color) -> Unit)? = null)`
  - `@Composable fun HhTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, tag: String, modifier: Modifier = Modifier, readOnly: Boolean = false, capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences, onDone: () -> Unit = {})`
  - `@Composable fun HhPersonChip(name: String, color: Color, tag: String, enabled: Boolean, onClick: () -> Unit)`
  - `@Composable fun HhSwatch(color: Color, chosen: Boolean, taken: Boolean, tag: String, onClick: () -> Unit)`
  - `@Composable fun HhSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String, enabled: Boolean = true)`
  - `HhPillButton(text, onClick, modifier = Modifier, primary = false, enabled = true)`
  - `class SingleAction(scope: CoroutineScope, onError: (Exception) -> Unit)` with `busy: Boolean` and `fun run(action: suspend () -> Unit)`; `@Composable fun rememberSingleAction(key: Any?, onError: (Exception) -> Unit): SingleAction` (moved from the calendar, unchanged)
  - `enum class ButtonTone { Primary, Plain, Quiet, Danger, Destroy }`; `@Composable fun HhSheetButton(text: String, tone: ButtonTone, enabled: Boolean, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null)` — the calendar sheets' footer button (was the detail sheet's private `ConfirmButton`), used by the person sheet (Task 8) and Review calendars (Task 11)

The five extra colours are violet `#9C7CE3`, lime `#9CC44E`, red `#E06666`, slate `#8B96A8` and brown `#A1785A`. No two of the eight, nor any of them and Family's `#E0A85B`, are closer than 66 in RGB distance (the test's floor is 60). A person's colour doesn't change with the theme: it is drawn on `bg` (`#0E1011` dark, `#EDF0EE` light) and under a `#0E1011` ink, and each of the five keeps that ink readable (the lightest, lime, is lighter than the hand-off's green; the darkest, brown, is lighter than the hand-off's blue).

- [ ] **Step 1: Write the failing tests**

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/PersonPaletteTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.sqrt
import org.junit.Test

/** `:core:household`'s FAMILY_COLOR, which `:core:ui` can't see. */
private const val FAMILY_AMBER = 0xFFE0A85B

/** The smallest RGB distance allowed between two person colours, or one and Family's; the closest pair is about 66. */
private const val MIN_DISTANCE = 60.0

class PersonPaletteTest {
    private fun channels(c: Long) = listOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    private fun distance(a: Long, b: Long): Double =
        sqrt(channels(a).zip(channels(b)).sumOf { (x, y) -> ((x - y) * (x - y)).toDouble() })

    @Test
    fun eightDistinctColoursStartingWithTheHandOffsThree() {
        assertThat(PersonPalette.colors).hasSize(8)
        assertThat(PersonPalette.colors.toSet()).hasSize(8)
        assertThat(PersonPalette.colors.take(3)).containsExactly(0xFF4CB387, 0xFF5B9BE0, 0xFFE07BA8).inOrder()
    }

    @Test
    fun everyColourIsFarFromEveryOtherAndFromFamily() {
        val all = PersonPalette.colors + FAMILY_AMBER
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                assertWithMessage("%s and %s", all[i].toString(16), all[j].toString(16))
                    .that(distance(all[i], all[j]))
                    .isAtLeast(MIN_DISTANCE)
            }
        }
    }

    @Test
    fun firstFreeSkipsTakenColoursAndIsNullOnceAllAreTaken() {
        assertThat(PersonPalette.firstFree(emptyList())).isEqualTo(0xFF4CB387)
        assertThat(PersonPalette.firstFree(PersonPalette.colors.take(2))).isEqualTo(0xFFE07BA8)
        assertThat(PersonPalette.firstFree(PersonPalette.colors)).isNull()
    }
}
```

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ControlsTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) =
        compose.setContent { CulveryTheme(dark = true) { content() } }

    @Test
    fun aTakenSwatchIsDisabledAndIgnoresATap() {
        var taps = 0
        show { HhSwatch(Color(0xFF5B9BE0), chosen = false, taken = true, tag = "swatch") { taps++ } }
        compose.onNodeWithTag("swatch").assertIsNotEnabled().performClick()
        assertThat(taps).isEqualTo(0)
    }

    @Test
    fun aChosenSwatchIsSelectedAndAFreeOneTakesATap() {
        var taps = 0
        show {
            Row {
                HhSwatch(Color(0xFF4CB387), chosen = true, taken = false, tag = "chosen") {}
                HhSwatch(Color(0xFF5B9BE0), chosen = false, taken = false, tag = "free") { taps++ }
            }
        }
        compose.onNodeWithTag("chosen").assertIsSelected()
        compose.onNodeWithTag("free").assertIsNotSelected().assertIsEnabled().performClick()
        assertThat(taps).isEqualTo(1)
    }

    @Test
    fun aChoiceChipReportsWhetherItIsSelected() {
        show {
            Row {
                HhChoiceChip("Admin", selected = true, tag = "admin", onClick = {})
                HhChoiceChip("Adult", selected = false, tag = "adult", onClick = {})
            }
        }
        compose.onNodeWithTag("admin").assertIsSelected()
        compose.onNodeWithTag("adult").assertIsNotSelected()
    }

    @Test
    fun aTextFieldShowsItsPlaceholderOnlyWhileEmpty() {
        var value by mutableStateOf("")
        show { HhTextField(value, { value = it }, placeholder = "Town or city", tag = "field") }
        compose.onNodeWithText("Town or city").assertExists()
        value = "Ca"
        compose.onNodeWithText("Town or city").assertDoesNotExist()
    }

    @Test
    fun aDisabledSheetButtonIgnoresTaps() {
        var taps = 0
        show { HhSheetButton("Save person", ButtonTone.Primary, enabled = false, tag = "save", onClick = { taps++ }) }
        compose.onNodeWithTag("save").assertIsNotEnabled().performClick()
        assertThat(taps).isEqualTo(0)
    }

    @Test
    fun aDisabledPillIgnoresTaps() {
        var taps = 0
        show { HhPillButton("Next", { taps++ }, primary = true, enabled = false) }
        compose.onNodeWithText("Next").performClick()
        assertThat(taps).isEqualTo(0)
    }

    @Test
    fun singleActionRunsOneActionAtATimeAndReportsFailures() = runTest {
        val errors = mutableListOf<Exception>()
        val action = SingleAction(backgroundScope) { errors += it }
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        action.run { runs++; gate.await() }
        action.run { runs++ }
        testScheduler.runCurrent()
        assertThat(action.busy).isTrue()
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertThat(runs).isEqualTo(1)
        assertThat(action.busy).isFalse()
        action.run { throw IllegalStateException("boom") }
        testScheduler.runCurrent()
        assertThat(errors.single()).isInstanceOf(IllegalStateException::class.java)
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :core:ui:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'PersonPalette'", "'HhSwatch'", "'HhSheetButton'", "'SingleAction'", and "No parameter with name 'enabled' found" for `HhPillButton`.

- [ ] **Step 3: Write the palette, the controls and `SingleAction`**

In `core/ui/build.gradle.kts`, add after the `plugins` block:
```kotlin

dependencies {
    // SingleAction launches on a CoroutineScope.
    implementation(libs.kotlinx.coroutines.core)
}
```

Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/PersonPalette.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

/**
 * The eight person colours (4a design D12). A colour in use can't be picked again, so eight is also the most people a
 * household can have. Family keeps its amber, which is none of these.
 */
object PersonPalette {
    // The hand-off's green, blue and pink; then violet, lime, red, slate and brown, each at least 60 apart in RGB from
    // every other and from Family's amber (PersonPaletteTest).
    val colors: List<Long> = listOf(
        0xFF4CB387,
        0xFF5B9BE0,
        0xFFE07BA8,
        0xFF9C7CE3,
        0xFF9CC44E,
        0xFFE06666,
        0xFF8B96A8,
        0xFFA1785A,
    )

    /** The first colour nobody in [taken] has; null once all eight are used. */
    fun firstFree(taken: Collection<Long>): Long? = colors.firstOrNull { it !in taken }
}
```

Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Controls.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/** Control values from the hand-off (§7 chips, the title field, person chips) and 4a's colour swatches. */
object ControlTokens {
    // Choice chips: 48 dp, padding 0 18, radius 24, 8 apart; a 12 dp dot or 20 dp icon 8 from the label.
    val chipHeight = 48.dp
    val chipPaddingH = 18.dp
    val chipRadius = 24.dp
    val chipGap = 8.dp
    val chipIconGap = 8.dp
    val chipDot = 12.dp
    val chipIcon = 20.dp

    /** Hand-off §7: a disabled chip is drawn at 38% and stays tappable; a chip's secondary text is at 72%. */
    const val DISABLED_ALPHA = 0.38f
    const val SECONDARY_ALPHA = 0.72f

    // Text field (the hand-off's title field): 64 dp, radius 18, `surf`, padding 0 20, a 2 dp `accent` border while focused.
    val fieldHeight = 64.dp
    val fieldRadius = 18.dp
    val fieldPaddingH = 20.dp
    val fieldBorder = 2.dp

    // The sheets' footer button (hand-off §7): 60 dp, radius 30, padding 0 26, a 24 dp icon 8 from the label.
    val buttonHeight = 60.dp
    val buttonRadius = 30.dp
    val buttonPaddingH = 26.dp
    val buttonIcon = 24.dp
    val buttonIconGap = 8.dp

    // Colour swatch (4a; not in the spec): a 44 dp circle, 12 apart; chosen, a 3 dp `ink` ring 3 dp outside it; taken,
    // at 38% with a 2 dp `ink` line across it.
    val swatch = 44.dp
    val swatchGap = 12.dp
    val swatchRing = 3.dp
    val swatchRingGap = 3.dp
    val swatchStrike = 2.dp

    /** Where the taken line starts and ends, as a share of the swatch: on the circle, corner to corner. */
    const val STRIKE_INSET = 0.15f
}

/** Control text styles, derived from HhType. */
object ControlType {
    /** 16 sp / 600: chips and person chips. */
    val chip = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 16 sp / 500: a chip's secondary text, e.g. a time chip's "09:00". */
    val chipSecondary = HhType.body.copy(fontWeight = FontWeight.W500)

    /** 22 sp / 600: a text field. */
    val field = HhType.sectionTitle.copy(fontWeight = FontWeight.W600)

    /** 17 sp / 700: a sheet's footer button. */
    val button = HhType.rowTitle.copy(fontWeight = FontWeight.W700)
}

/**
 * Hand-off §7 chip: 48 dp, padding 0 18, radius 24, 16 sp / 600; `surf`/`ink`, or [selectedColor]/[selectedInk] when
 * selected. A disabled chip is drawn at 38% but stays tappable, so a tap can explain why.
 */
@Composable
fun HhChoiceChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selectedColor: Color = Culvery.colors.accent,
    selectedInk: Color = Culvery.colors.accentInk,
    secondary: String? = null,
    leading: (@Composable (ink: Color) -> Unit)? = null,
) {
    val c = Culvery.colors
    val ink = if (selected) selectedInk else c.ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipIconGap),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.selected = selected }
            .alpha(if (enabled) 1f else ControlTokens.DISABLED_ALPHA)
            .height(ControlTokens.chipHeight)
            .clip(RoundedCornerShape(ControlTokens.chipRadius))
            .background(if (selected) selectedColor else c.surf)
            .clickable(onClick = onClick)
            .padding(horizontal = ControlTokens.chipPaddingH),
    ) {
        leading?.invoke(ink)
        Text(label, style = ControlType.chip, color = ink, maxLines = 1)
        if (secondary != null) {
            Text(secondary, style = ControlType.chipSecondary, color = ink.copy(alpha = ControlTokens.SECONDARY_ALPHA), maxLines = 1)
        }
    }
}

/** 64 dp, `surf`, 22 sp / 600, one line; [placeholder] while empty; the keyboard's Done calls [onDone] and never saves. */
@Composable
fun HhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onDone: () -> Unit = {},
) {
    val c = Culvery.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ControlTokens.fieldRadius)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        readOnly = readOnly,
        textStyle = ControlType.field.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .testTag(tag)
            .fillMaxWidth()
            .height(ControlTokens.fieldHeight)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(c.surf)
                    .then(if (focused) Modifier.border(ControlTokens.fieldBorder, c.accent, shape) else Modifier)
                    .padding(horizontal = ControlTokens.fieldPaddingH),
            ) {
                if (value.isEmpty()) Text(placeholder, style = ControlType.field, color = c.mute, maxLines = 1)
                inner()
            }
        },
    )
}

/** Hand-off §7 person chip: a chip's size and shape, `surf`, a 12 dp dot in the person's [color] 8 from their [name]. */
@Composable
fun HhPersonChip(name: String, color: Color, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipIconGap),
        modifier = Modifier
            .testTag(tag)
            .height(ControlTokens.chipHeight)
            .clip(RoundedCornerShape(ControlTokens.chipRadius))
            .background(c.surf)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ControlTokens.chipPaddingH),
    ) {
        Box(Modifier.size(ControlTokens.chipDot).clip(CircleShape).background(color))
        Text(name, style = ControlType.chip, color = c.ink, maxLines = 1)
    }
}

/**
 * [Primary] `accent`; [Plain] `surf2`; [Quiet] `surf` (Keep, on a `dangerSoft` card); [Danger] `surf2` with `danger`
 * text; [Destroy] `danger` with `dangerInk`.
 */
enum class ButtonTone { Primary, Plain, Quiet, Danger, Destroy }

/** The sheets' footer button (hand-off §7): 60 dp, radius 30, 17 sp / 700, an optional icon. Disabled, `surf2` and `mute`. */
@Composable
fun HhSheetButton(
    text: String,
    tone: ButtonTone,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
) {
    val c = Culvery.colors
    val (background, ink) = if (!enabled) {
        c.surf2 to c.mute
    } else {
        when (tone) {
            ButtonTone.Primary -> c.accent to c.accentInk
            ButtonTone.Plain -> c.surf2 to c.ink
            ButtonTone.Quiet -> c.surf to c.ink
            ButtonTone.Danger -> c.surf2 to c.danger
            ButtonTone.Destroy -> c.danger to c.dangerInk
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.buttonIconGap, Alignment.CenterHorizontally),
        modifier = modifier
            .testTag(tag)
            .height(ControlTokens.buttonHeight)
            .clip(RoundedCornerShape(ControlTokens.buttonRadius))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ControlTokens.buttonPaddingH),
    ) {
        if (icon != null) HhIcon(icon, size = ControlTokens.buttonIcon, tint = ink)
        Text(text, style = ControlType.button, color = ink, maxLines = 1)
    }
}

/** A person colour to pick (4a design §4.4): ringed when [chosen]; [taken] by someone else, struck through and disabled. */
@Composable
fun HhSwatch(color: Color, chosen: Boolean, taken: Boolean, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    val outside = ControlTokens.swatchRing + ControlTokens.swatchRingGap
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .semantics { selected = chosen }
            .size(ControlTokens.swatch + outside * 2)
            .then(if (chosen) Modifier.border(ControlTokens.swatchRing, c.ink, CircleShape) else Modifier)
            .clip(CircleShape)
            .clickable(enabled = !taken, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(ControlTokens.swatch)
                // Before the alpha, so the line stays solid over the faded colour.
                .drawWithContent {
                    drawContent()
                    if (taken) {
                        val inset = size.width * ControlTokens.STRIKE_INSET
                        drawLine(c.ink, Offset(inset, inset), Offset(size.width - inset, size.height - inset), ControlTokens.swatchStrike.toPx())
                    }
                }
                .alpha(if (taken) ControlTokens.DISABLED_ALPHA else 1f)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** A Show-style switch in the theme's colours: `accent` when on; a disabled one that is on stays faded `accent`. */
@Composable
fun HhSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String, enabled: Boolean = true) {
    val c = Culvery.colors
    val faded = c.accent.copy(alpha = ControlTokens.DISABLED_ALPHA)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.testTag(tag),
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.accentInk,
            checkedTrackColor = c.accent,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = c.mute,
            uncheckedTrackColor = c.surf2,
            uncheckedBorderColor = c.mute,
            disabledCheckedThumbColor = c.accentInk,
            disabledCheckedTrackColor = faded,
            disabledCheckedBorderColor = faded,
        ),
    )
}
```

Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/SingleAction.kt` with the calendar's `SingleAction` and `rememberSingleAction`, now public (cut them from `Sheets.kt` in Step 4):
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One action at a time for a sheet or page: while one runs, [busy] is true and further taps are ignored, so a double tap
 * saves once. An exception the action lets through goes to [onError] instead of crashing the app; the sheet closing
 * mid-action cancels it, which is not an error.
 */
class SingleAction(private val scope: CoroutineScope, private val onError: (Exception) -> Unit) {
    var busy by mutableStateOf(false)
        private set

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            } finally {
                busy = false
            }
        }
    }
}

/** A [SingleAction] for one opening of a sheet or page: a new [key] starts a fresh one. */
@Composable
fun rememberSingleAction(key: Any?, onError: (Exception) -> Unit): SingleAction {
    val scope = rememberCoroutineScope()
    val currentOnError by rememberUpdatedState(onError)
    return remember(key) { SingleAction(scope) { currentOnError(it) } }
}
```

In `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Components.kt`, replace `HhPillButton` with:
```kotlin
/** A pill button; a disabled one is `surf2` with `mute` text and ignores taps. */
@Composable
fun HhPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Culvery.colors
    Text(
        text = text,
        style = HhType.buttonLabel,
        color = when {
            !enabled -> c.mute
            primary -> c.accentInk
            else -> c.ink
        },
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (primary && enabled) c.accent else c.surf2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
    )
}
```
(Its three numbers were inline in Plan 1 and are unchanged; this plan adds none.)

- [ ] **Step 4: Point the calendar at the shared controls**

The calendar's screenshots must not change: every value moved is the same value.

1. `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Sheets.kt`: delete the `SingleAction` class and `rememberSingleAction` (from the KDoc "One action at a time for a sheet" to the end of the file) and the imports only they used, leaving:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.core.plugin.OverlayHost
```
followed by the unchanged KDoc and bodies of `showDetail` and `showEditor`.
2. `EventDetailHost.kt` and `EventEditorHost.kt`: add `import uk.co.siland.culvery.core.ui.rememberSingleAction`.
3. `EventEditorSheet.kt`:
   - delete the private `ChoiceChip` (its KDoc from "Hand-off §7 chip: 48 dp" through its closing brace) and replace every call `ChoiceChip(` with `HhChoiceChip(`;
   - replace `CalendarDimens.choiceChipGap` with `ControlTokens.chipGap` (twice), `CalendarDimens.choiceChipIcon` with `ControlTokens.chipIcon` (three times) and `CalendarDimens.choiceChipDot` with `ControlTokens.chipDot`;
   - replace the body of `TitleField` (everything inside its braces) with:
```kotlin
    HhTextField(
        value = form.title,
        onValueChange = { form.updateTitle(it) },
        placeholder = "What's happening?",
        tag = "editor_title",
        modifier = Modifier.focusRequester(focus),
        readOnly = readOnly,
        onDone = onDone,
    )
```
   - add the imports `uk.co.siland.culvery.core.ui.ControlTokens`, `uk.co.siland.culvery.core.ui.HhChoiceChip`, `uk.co.siland.culvery.core.ui.HhTextField`, and remove the ones the compiler now reports unused (`BasicTextField`, `KeyboardActions`, `KeyboardOptions`, `SolidColor`, `onFocusChanged`, `border`, `selected`, `semantics`, `ImeAction`, `KeyboardCapitalization`, and `mutableStateOf`/`setValue`/`alpha` if no longer used).
4. `EventDetailSheet.kt`:
   - replace `people.forEach { person -> PersonChip(person, enabled = !busy) { onAssign(person) } }` with
```kotlin
people.forEach { person -> HhPersonChip(person.name, Color(person.color), "assign_${person.name}", enabled = !busy) { onAssign(person) } }
```
   - delete the private `PersonChip`;
   - replace the delete confirmation's two buttons with the shared one and delete the private `ConfirmButton`:
```kotlin
            HhSheetButton("Keep event", ButtonTone.Quiet, enabled = !busy, tag = "detail_keep", onClick = onKeep, modifier = Modifier.weight(1f))
            HhSheetButton(
                "Delete event", ButtonTone.Destroy, enabled = !busy, tag = "detail_confirm_delete", onClick = onConfirmDelete,
                modifier = Modifier.weight(1f), icon = "delete_forever",
            )
```
     (Same size, shape, colours, icon and centred label as before; the button's 26 dp side padding doesn't move a centred label.)
   - add the imports `uk.co.siland.culvery.core.ui.HhPersonChip`, `uk.co.siland.culvery.core.ui.HhSheetButton`, `uk.co.siland.culvery.core.ui.ButtonTone`.
5. `CalendarType.kt`:
   - in `CalendarType`, delete `chip`, `chipSecondary` and `titleField` (keep `personChip`: `pickerCell` and `lockedDates` use it);
   - in `CalendarDimens`, delete `titleHeight`, `titleRadius`, `titlePaddingH`, `titleBorder`, `choiceChipHeight`, `choiceChipPaddingH`, `choiceChipRadius`, `choiceChipGap`, `choiceChipIconGap`, `choiceChipDot`, `choiceChipIcon`, `DISABLED_CHIP_ALPHA`, `CHIP_SECONDARY_ALPHA`, `personChipHeight`, `personChipRadius`, `personChipPaddingH`, `personChipDot` and `personChipDotGap`, with their comment lines (keep `personChipGap`, which spaces the Assign row, and change its comment to "Assign: "Assign to…" 44 dp, radius 22; person chips 8 apart (ControlTokens has the chip).").

Check nothing still uses the removed names:
```bash
grep -rnE "choiceChip|titleHeight|titleRadius|titlePaddingH|titleBorder|DISABLED_CHIP_ALPHA|CHIP_SECONDARY_ALPHA|personChip(Height|Radius|PaddingH|Dot)|CalendarType\.(chip|chipSecondary|titleField)\b|SingleAction\(scope|ConfirmButton" capability/calendar/src
```
Expected: no output.

- [ ] **Step 5: Run the tests and the calendar's screenshots**

Run: `./gradlew :core:ui:testDebugUnitTest :capability:calendar:testDebugUnitTest :capability:calendar:verifyRoborazziDebug`
Expected: PASS, with no screenshot differences (the editor, detail and week images are the same pixels).

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add core/ui capability/calendar
git commit -m "Add the person palette and share the chip, text field, person chip, swatch, switch and single-action guard"
```

---

### Task 3: Household rules — names, colours, eight people, one-transaction member edits, and PINs set with the person (§3.7)

**Files:**
- Modify: `core/household/src/main/java/uk/co/siland/culvery/core/household/Model.kt`, `HouseholdRepository.kt`
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/PinManager.kt`
- Test: `core/household/src/test/java/uk/co/siland/culvery/core/household/HouseholdRepositoryTest.kt`, `core/access/src/test/java/uk/co/siland/culvery/core/access/PinManagerTest.kt`, `core/access/src/test/java/uk/co/siland/culvery/core/access/DefaultAccessControlTest.kt` (modify)

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `const val MAX_PEOPLE = 8`
  - `data class Member(val person: Person, val role: Role, val hasPin: Boolean) { val isActiveAdmin: Boolean }`
  - `sealed interface PinChange { data object Keep; data object Remove; class Set(val hash: String, val salt: String) }`
  - `class DuplicateNameException(val name: String) : Exception`; `class ColourInUseException : Exception` (their texts hold no name)
  - `HouseholdRepository`: `val members: Flow<List<Member>>`; `val hasActiveAdmin: Flow<Boolean>`; `suspend fun member(id: PersonId): Member?`; `suspend fun addPerson(name: String, color: Long, role: Role, pinHash: String? = null, salt: String? = null): Person` (throws `DuplicateNameException`, `ColourInUseException`, `IllegalArgumentException` for a blank name or a ninth person; `sortOrder` read in the transaction, which Room serialises, so two adds never share a place); `updatePerson(person)` applies the name and colour rules; `suspend fun updateMember(id: PersonId, name: String, color: Long, role: Role, pin: PinChange)` (one transaction; the name, colour and last-Admin rules); `setRole`, `setPinHash`, `clearPin` refuse Family with `IllegalArgumentException`
  - `PinManager`: `suspend fun hashNew(pin: String, owner: PersonId?): Pair<String, String>` (hash to salt; `PinInUseException` when anyone but [owner] has it); `suspend fun addPerson(name: String, color: Long, role: Role, pin: String?): Person` (the PIN stored with the person)

- [ ] **Step 1: Give the existing tests' people their own colours**

Two people may no longer share a colour.

In `core/household/src/test/java/uk/co/siland/culvery/core/household/HouseholdRepositoryTest.kt`, replace the `admin` helper with:
```kotlin
    private var nextColour = 0xFF101010L

    private suspend fun admin(name: String): Person =
        repo.addPerson(name, nextColour++, Role.ADMIN).also { repo.setPinHash(it.id, "hash-$name", "salt") }
```

In `core/access/src/test/java/uk/co/siland/culvery/core/access/DefaultAccessControlTest.kt`, replace the `person` helper with:
```kotlin
    private var nextColour = 0xFF101010L

    private suspend fun person(name: String, role: Role, pin: String): Person =
        household.addPerson(name, nextColour++, role).also { pins.setPin(it.id, pin) }
```

- [ ] **Step 2: Write the failing tests**

In `HouseholdRepositoryTest.kt`, add these tests:
```kotlin
    @Test
    fun aNameInUseIsRefusedWhateverItsCaseOrSpaces() = runTest {
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        val refused = assertThrows(DuplicateNameException::class.java) {
            runBlocking { repo.addPerson("  sAM ", 0xFFE07BA8, Role.CHILD) }
        }
        assertThat(refused.name).isEqualTo("sAM")
        assertThat(refused.message).doesNotContain("sAM")
        assertThat(repo.people.first().map { it.name }).containsExactly("Sam")
    }

    @Test
    fun nobodyCanBeCalledFamily() = runTest {
        assertThrows(DuplicateNameException::class.java) { runBlocking { repo.addPerson("family", 0xFF5B9BE0, Role.ADULT) } }
    }

    @Test
    fun aColourInUseIsRefused() = runTest {
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        assertThrows(ColourInUseException::class.java) { runBlocking { repo.addPerson("Mia", 0xFF5B9BE0, Role.CHILD) } }
    }

    @Test
    fun renamingAndRecolouringFollowTheSameRules() = runTest {
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        assertThrows(DuplicateNameException::class.java) { runBlocking { repo.updatePerson(mia.copy(name = "SAM")) } }
        assertThrows(ColourInUseException::class.java) { runBlocking { repo.updatePerson(mia.copy(color = 0xFF5B9BE0)) } }
        repo.updatePerson(mia.copy(name = "MIA"))
        assertThat(repo.person(mia.id)?.name).isEqualTo("MIA")
    }

    @Test
    fun aNinthPersonIsRefused() = runTest {
        repeat(MAX_PEOPLE) { repo.addPerson("P$it", 0xFF100000L + it, Role.ADULT) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.addPerson("P8", 0xFF200000L, Role.ADULT) } }
        assertThat(repo.people.first()).hasSize(MAX_PEOPLE)
    }

    @Test
    fun aPersonAddedWithAPinHasItFromTheStart() = runTest {
        assertThat(repo.hasActiveAdmin.first()).isFalse()
        val alex = repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN, pinHash = "h", salt = "s")
        assertThat(repo.credential(alex.id)).isEqualTo(Credential(alex.id, Role.ADMIN, "h", "s"))
        assertThat(repo.hasActiveAdmin.first()).isTrue()
        assertThat(repo.members.first()).containsExactly(Member(alex, Role.ADMIN, hasPin = true))
    }

    @Test
    fun familyHasNoRoleOrPin() = runTest {
        // Today these fail only as an unknown person; the refusal must name Family.
        val refusals = listOf(
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.setRole(PersonId.FAMILY, Role.ADULT) } },
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.setPinHash(PersonId.FAMILY, "h", "s") } },
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.clearPin(PersonId.FAMILY) } },
        )
        assertThat(refusals.map { it.message }.toSet()).containsExactly("Family has no role or PIN")
    }

    @Test
    fun aMemberEditChangesNameColourRoleAndPinTogether() = runTest {
        admin("Alex")
        val sam = repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        repo.updateMember(sam.id, "Samuel", 0xFF9C7CE3, Role.ADMIN, PinChange.Set("h", "s"))
        assertThat(repo.member(sam.id)).isEqualTo(Member(Person(sam.id, "Samuel", 0xFF9C7CE3), Role.ADMIN, hasPin = true))
        repo.updateMember(sam.id, "Samuel", 0xFF9C7CE3, Role.ADULT, PinChange.Remove)
        assertThat(repo.credential(sam.id)).isEqualTo(Credential(sam.id, Role.ADULT, null, null))
    }

    @Test
    fun aRefusedMemberEditChangesNothing() = runTest {
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        assertThrows(ColourInUseException::class.java) {
            runBlocking { repo.updateMember(mia.id, "Amelia", 0xFF5B9BE0, Role.ADULT, PinChange.Set("h", "s")) }
        }
        assertThat(repo.member(mia.id)).isEqualTo(Member(mia, Role.CHILD, hasPin = false))
    }

    @Test
    fun theLastAdminIsKeptThroughAMemberEdit() = runTest {
        val alex = admin("Alex")
        assertThrows(LastAdminException::class.java) {
            runBlocking { repo.updateMember(alex.id, "Alex", alex.color, Role.ADULT, PinChange.Keep) }
        }
        assertThrows(LastAdminException::class.java) {
            runBlocking { repo.updateMember(alex.id, "Alex", alex.color, Role.ADMIN, PinChange.Remove) }
        }
        assertThat(repo.member(alex.id)?.isActiveAdmin).isTrue()
    }
```

In `core/access/src/test/java/uk/co/siland/culvery/core/access/PinManagerTest.kt`, add the import `kotlinx.coroutines.flow.first` and these tests:
```kotlin
    @Test
    fun aPersonAddedWithAPinIsKnownByIt() = runTest {
        val mia = pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, "1357")
        assertThat(pins.identify("1357")).isEqualTo(Identified(mia, Role.CHILD))
    }

    @Test
    fun aPersonAddedWithATakenPinIsNotAdded() = runTest {
        pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        assertThrows(PinInUseException::class.java) { runBlocking { pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, "1234") } }
        assertThat(household.people.first().map { it.name }).containsExactly("Alex")
    }

    @Test
    fun aNewHashAllowsTheOwnersOwnPinAndNobodyElses() = runTest {
        val alex = pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        assertThat(pins.hashNew("1234", owner = alex.id).second).isNotEmpty()
        assertThrows(PinInUseException::class.java) { runBlocking { pins.hashNew("1234", owner = null) } }
    }
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :core:household:testDebugUnitTest :core:access:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'DuplicateNameException'", "'MAX_PEOPLE'", "'Member'", "'PinChange'" and, in `:core:access`, "Unresolved reference 'hashNew'".

- [ ] **Step 4: Write the rules**

In `core/household/src/main/java/uk/co/siland/culvery/core/household/Model.kt`, add at the end:
```kotlin

/** At most eight people (4a design D12): one for each person colour. */
const val MAX_PEOPLE = 8

/** A person with their role and whether they have a PIN: the people list and the editor (4a design §4.4). */
data class Member(val person: Person, val role: Role, val hasPin: Boolean) {
    val isActiveAdmin: Boolean get() = role == Role.ADMIN && hasPin
}

/** What a member edit does to the PIN. [Set] carries PinManager's hash and salt, both Base64. */
sealed interface PinChange {
    data object Keep : PinChange

    data object Remove : PinChange

    class Set(val hash: String, val salt: String) : PinChange
}

/** [name] is for the editor's message; the exception's own text holds no name, as it may be logged. */
class DuplicateNameException(val name: String) : Exception("Someone already has that name")

class ColourInUseException : Exception("That colour is already someone's")
```

In `core/household/src/main/java/uk/co/siland/culvery/core/household/HouseholdRepository.kt`:
1. Add the import `kotlinx.coroutines.flow.distinctUntilChanged`.
2. After `val peopleWithFamily …` add:
```kotlin

    /** Real people with their roles and whether they have a PIN, in display order (4a design §4.4). */
    val members: Flow<List<Member>> = dao.people().map { rows -> rows.map { it.toMember() } }

    val hasActiveAdmin: Flow<Boolean> = members.map { list -> list.any { it.isActiveAdmin } }.distinctUntilChanged()
```
3. After `suspend fun person(…)` add:
```kotlin

    suspend fun member(id: PersonId): Member? = dao.person(id.value)?.toMember()
```
4. Replace `addPerson` and `updatePerson` with:
```kotlin
    /**
     * 4a design §3.7: a name nobody else has (ignoring case and spaces; nobody is "Family"), a colour nobody else has, and
     * at most [MAX_PEOPLE]. [pinHash] and [salt] set the PIN in the same transaction (PinManager.addPerson), so a person
     * is never left half made.
     */
    suspend fun addPerson(name: String, color: Long, role: Role, pinHash: String? = null, salt: String? = null): Person {
        require((pinHash == null) == (salt == null)) { "A PIN needs both its hash and its salt" }
        val clean = cleanName(name)
        val id = PersonId.new()
        db.withTransaction {
            val all = dao.all()
            require(all.size < MAX_PEOPLE) { "At most $MAX_PEOPLE people" }
            checkUnique(all, id, clean, color)
            dao.upsertPerson(PersonEntity(id.value, clean, color, dao.maxSortOrder() + 1, role, pinHash, salt))
        }
        return Person(id, clean, color)
    }

    suspend fun updatePerson(person: Person) {
        require(!person.isFamily) { "Family cannot be edited" }
        val clean = cleanName(person.name)
        db.withTransaction {
            val existing = requireNotNull(dao.person(person.id.value)) { "Unknown person ${person.id.value}" }
            checkUnique(dao.all(), person.id, clean, person.color)
            dao.upsertPerson(existing.copy(name = clean, color = person.color))
        }
    }

    /**
     * Name, colour, role and PIN in one transaction (4a design §5: a refused edit changes nothing), under [addPerson]'s
     * rules and the last-Admin rule.
     */
    suspend fun updateMember(id: PersonId, name: String, color: Long, role: Role, pin: PinChange) {
        require(id != PersonId.FAMILY) { "Family cannot be edited" }
        val clean = cleanName(name)
        db.withTransaction {
            val current = requireNotNull(dao.person(id.value)) { "Unknown person ${id.value}" }
            checkUnique(dao.all(), id, clean, color)
            val renamed = current.copy(name = clean, color = color, role = role)
            val next = when (pin) {
                PinChange.Keep -> renamed
                PinChange.Remove -> renamed.copy(pinHash = null, salt = null)
                is PinChange.Set -> renamed.copy(pinHash = pin.hash, salt = pin.salt)
            }
            guardLastAdmin(current, next)
            dao.upsertPerson(next)
        }
    }
```
5. In `change`, add as its first line:
```kotlin
        require(id != PersonId.FAMILY) { "Family has no role or PIN" }
```
   (so `private suspend fun change(id: PersonId, edit: (PersonEntity) -> PersonEntity) = db.withTransaction {` becomes a block body: `private suspend fun change(id: PersonId, edit: (PersonEntity) -> PersonEntity) { require(…); db.withTransaction { …as before… } }`).
6. After `guardLastAdmin` add:
```kotlin

    private fun checkUnique(all: List<PersonEntity>, id: PersonId, name: String, color: Long) {
        val others = all.filter { it.id != id.value }
        if (name.equals(Person.Family.name, ignoreCase = true) || others.any { it.name.equals(name, ignoreCase = true) }) {
            throw DuplicateNameException(name)
        }
        if (others.any { it.color == color }) throw ColourInUseException()
    }
```
7. After `private fun PersonEntity.toPerson() …` add:
```kotlin
    private fun PersonEntity.toMember() = Member(toPerson(), role, pinHash != null)
```

In `core/access/src/main/java/uk/co/siland/culvery/core/access/PinManager.kt`, replace `setPin` with:
```kotlin
    /** A hash and salt for [pin]; PinInUseException when anyone but [owner] has it (PINs identify people). */
    suspend fun hashNew(pin: String, owner: PersonId?): Pair<String, String> {
        hasher.validate(pin)
        return withContext(Dispatchers.Default) {
            val others = household.credentials().filter { it.personId != owner }
            if (others.any { it.matches(pin) }) throw PinInUseException()
            val salt = hasher.newSalt()
            hasher.hash(pin, salt) to salt
        }
    }

    suspend fun setPin(id: PersonId, pin: String) {
        val (hash, salt) = hashNew(pin, id)
        household.setPinHash(id, hash, salt)
    }

    /** Adds a person with [pin] (or none) in one step, so a PIN in use leaves nobody added. */
    suspend fun addPerson(name: String, color: Long, role: Role, pin: String?): Person {
        val hashed = pin?.let { hashNew(it, owner = null) }
        return household.addPerson(name, color, role, hashed?.first, hashed?.second)
    }
```
Add the import `uk.co.siland.culvery.core.household.Role` if it isn't there (it is, for `Identified`).

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :core:household:testDebugUnitTest :core:access:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. (Every other test that adds people already gives each a colour of their own: the calendar's `TestAccess`, `CalendarRepositoryTest`, the debug seed.)

- [ ] **Step 7: Commit**

```bash
git add core/household core/access
git commit -m "Refuse a name or colour in use and a ninth person, edit a person in one transaction, and store a new person's PIN with them"
```

---

### Task 4: Access — the setup session and its 10 idle minutes, `touch()`, and the choose-a-PIN pad (§3.4, §3.5, D5, D9)

**Files:**
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/AccessControl.kt`, `DefaultAccessControl.kt`, `ui/PinPad.kt`
- Test: `core/access/src/test/java/uk/co/siland/culvery/core/access/DefaultAccessControlTest.kt`, `ui/PinPadTest.kt` (modify); `ui/ChoosePinPadTest.kt` (create)
- Modify (fakes): `app/src/test/java/uk/co/siland/culvery/shell/Fakes.kt`, `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt` (modify: the shell never begins a setup session)

**Interfaces:**
- Consumes: `Identified`, `PinPromptController`, `SESSION_TIMEOUT_MS` (Plan 1).
- Produces:
  - `AccessControl.beginSetupSession(person: Identified)` (the wizard's alone), `endSetupSession()`, `touch()`; `const val SETUP_IDLE_MS = 600_000L` — the setup session ends after 10 minutes without a touch or an authorised action (spec D9, amended)
  - `PinReason.ContinueSetup`; `const val CONTINUE_SETUP = "Enter your PIN to carry on setting up"`; `pinReasonText(PinReason.ContinueSetup, _) == CONTINUE_SETUP`
  - `@Composable fun PinPadFrame(title: String, line: String?, message: String, wrongPin: Boolean, enabled: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit, overSheet: Boolean = false, drawScrim: Boolean = true)`
  - `@Composable fun ChoosePinPad(onChosen: (String) -> Unit, onCancel: () -> Unit, overSheet: Boolean = false, drawScrim: Boolean = true)`; `const val CHOOSE_PIN = "Choose a 4-digit PIN"`, `ENTER_IT_AGAIN = "Enter it again"`, `PINS_DIDNT_MATCH = "Those PINs didn't match — try again."`
  - `FakeAccessControl.touches: Int`, `FakeAccessControl.setupSessionsBegun: Int` (app tests)

- [ ] **Step 1: Write the failing tests**

In `core/access/src/test/java/uk/co/siland/culvery/core/access/DefaultAccessControlTest.kt`, add these tests (the file already imports what they use; add `kotlinx.coroutines.test.advanceTimeBy` and `runCurrent` if missing):
```kotlin
    private suspend fun alex(): Identified = Identified(person("Alex", Role.ADMIN, "1234"), Role.ADMIN)

    @Test
    fun aSetupSessionPassesEveryPermissionWithoutAPin() = runTest {
        val access = access()
        access.beginSetupSession(alex())
        for (permission in listOf(CorePermissions.SETTINGS_MANAGE, CorePermissions.PEOPLE_MANAGE, CorePermissions.KIOSK_EXIT)) {
            assertThat(withTimeout(1_000) { access.authorise(permission) }).isNotNull()
        }
        assertThat(prompt.request.value).isNull()
    }

    @Test
    fun aSetupSessionOutlastsTheUsualTwoMinutes() = runTest {
        val access = access()
        val admin = alex()
        access.beginSetupSession(admin)
        advanceTimeBy(SESSION_TIMEOUT_MS * 4)
        runCurrent()
        assertThat(access.session.value).isEqualTo(admin)
    }

    @Test
    fun aSetupSessionEndsAfterTenMinutesWithoutATouch() = runTest {
        val access = access()
        access.beginSetupSession(alex())
        advanceTimeBy(SETUP_IDLE_MS + 1)
        runCurrent()
        assertThat(access.session.value).isNull()
        // Gone for good: the next fresh-PIN permission asks.
        assertThat(firstPromptFor(access, CorePermissions.PEOPLE_MANAGE).label).isEqualTo("Manage people")
    }

    @Test
    fun aTouchRestartsTheSetupSessionsTenMinutes() = runTest {
        val access = access()
        val admin = alex()
        access.beginSetupSession(admin)
        advanceTimeBy(SETUP_IDLE_MS - 60_000)
        access.touch()
        advanceTimeBy(SETUP_IDLE_MS - 60_000)
        runCurrent()
        assertThat(access.session.value).isEqualTo(admin)
        advanceTimeBy(60_001)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun endingTheSetupSessionStartsTheUsualTwoMinutes() = runTest {
        val access = access()
        val admin = alex()
        access.beginSetupSession(admin)
        access.endSetupSession()
        assertThat(access.session.value).isEqualTo(admin)
        advanceTimeBy(SESSION_TIMEOUT_MS + 1)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun lockEndsTheSetupSession() = runTest {
        val access = access()
        access.beginSetupSession(alex())
        access.lock()
        assertThat(access.session.value).isNull()
        assertThat(firstPromptFor(access, CorePermissions.PEOPLE_MANAGE).label).isEqualTo("Manage people")
    }

    @Test
    fun aPinEnteredDuringSetupEndsTheSetupSession() = runTest {
        val access = access()
        access.beginSetupSession(alex())
        person("Mia", Role.CHILD, "9876")
        answerPins("9876")
        // Refused for Alex by the check, so the pad asks; Mia's PIN starts an ordinary session.
        val mia = access.authorise("test.any", allow = { who, _ -> who.person.name == "Mia" })
        assertThat(mia?.person?.name).isEqualTo("Mia")
        advanceTimeBy(SESSION_TIMEOUT_MS + 1)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun aTouchRestartsTheTwoMinutes() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        advanceTimeBy(100_000)
        access.touch()
        advanceTimeBy(100_000)
        runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(20_001)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun aTouchWithNobodySignedInSignsNobodyIn() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        access.touch()
        assertThat(access.session.value).isNull()
        assertThat(firstPromptFor(access, CorePermissions.SETTINGS_MANAGE).label).isEqualTo("Change settings")
    }
```

In `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/PinPadTest.kt`, add (with the import `uk.co.siland.culvery.core.access.pinReasonText`):
```kotlin
    @Test
    fun theCarryOnReasonIsTheWizardsOwnLine() {
        assertThat(pinReasonText(PinReason.ContinueSetup, "Change settings")).isEqualTo("Enter your PIN to carry on setting up")
    }
```

Create `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/ChoosePinPadTest.kt`:
```kotlin
package uk.co.siland.culvery.core.access.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class ChoosePinPadTest {
    @get:Rule val compose = createComposeRule()
    private var chosen: String? = null
    private var cancelled = 0

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) { ChoosePinPad(onChosen = { chosen = it }, onCancel = { cancelled++ }) }
    }

    private fun tap(pin: String) = pin.forEach { compose.onNodeWithTag("pin_key_$it").performClick() }

    @Test
    fun twoMatchingEntriesChooseThePin() {
        show()
        compose.onNodeWithText("Choose a 4-digit PIN").assertExists()
        tap("2468")
        compose.onNodeWithText("Enter it again").assertExists()
        tap("2468")
        compose.waitForIdle()
        assertThat(chosen).isEqualTo("2468")
    }

    @Test
    fun aMismatchStartsAgainAndSaysSo() {
        show()
        tap("2468")
        tap("1357")
        compose.onNodeWithText("Choose a 4-digit PIN").assertExists()
        compose.onNodeWithText("Those PINs didn't match — try again.").assertExists()
        assertThat(chosen).isNull()
        tap("1357")
        tap("1357")
        compose.waitForIdle()
        assertThat(chosen).isEqualTo("1357")
    }

    @Test
    fun cancelLeavesWithNothingChosen() {
        show()
        tap("24")
        compose.onNodeWithTag("pin_cancel").performClick()
        assertThat(cancelled).isEqualTo(1)
        assertThat(chosen).isNull()
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'beginSetupSession'", "'touch'", "'SETUP_IDLE_MS'", "'ContinueSetup'", "'ChoosePinPad'".

- [ ] **Step 3: Add the setup session and `touch()`**

In `core/access/src/main/java/uk/co/siland/culvery/core/access/AccessControl.kt`:
1. Replace the KDoc on `SESSION_TIMEOUT_MS` with `/** Hand-off §7: signed in for 2 minutes after the last authorised action, or the last touch while Settings is open (4a design D5). */`, and after it add:
```kotlin

/** 4a design D9 (amended): the setup session ends after 10 minutes without a touch in the wizard or an authorised action. */
const val SETUP_IDLE_MS = 600_000L
```
2. Replace `enum class PinReason { Generic, Save, Edit, Delete, Assign }` with `enum class PinReason { Generic, Save, Edit, Delete, Assign, ContinueSetup }`.
3. Replace `pinReasonText` with:
```kotlin
fun pinReasonText(reason: PinReason, label: String): String = when (reason) {
    PinReason.Generic -> "Enter your PIN to ${label.replaceFirstChar { it.lowercase() }}."
    PinReason.ContinueSetup -> CONTINUE_SETUP
    else -> "Enter your PIN to ${reason.name.lowercase()} this event. It also records who made the change."
}

/** 4a design §3.4: the wizard started again after a kill past its You step. */
const val CONTINUE_SETUP = "Enter your PIN to carry on setting up"
```
4. In `interface AccessControl`, before `fun lock()`, add:
```kotlin
    /**
     * The setup wizard only; the shell never calls it. 4a design §3.4: [person], the Admin the wizard has just made (or
     * who entered their PIN at its gate), is signed in and every authorise for them passes without a PIN (fresh-PIN
     * permissions too) until [endSetupSession], [lock], a PIN entered at a pad, or [SETUP_IDLE_MS] without a touch or an
     * authorised action. Kept in memory only, so no later start of the app inherits it.
     */
    fun beginSetupSession(person: Identified)

    /** Ends the setup session; whoever was signed in stays signed in for the usual two minutes. */
    fun endSetupSession()

    /**
     * A touch while Settings is open or the wizard shows (4a design D5, D9): restarts the session's two minutes, or the
     * setup session's ten. Nothing with nobody signed in.
     */
    fun touch()
```

In `core/access/src/main/java/uk/co/siland/culvery/core/access/DefaultAccessControl.kt`:
1. Replace `private var expiry: Job? = null` with:
```kotlin
    private var expiry: Job? = null
    // authorise restarts the timer under authoriseLock; touch() restarts it from the UI thread.
    private val expiryLock = Any()
    @Volatile private var setupPerson: Identified? = null
```
2. In `authorise`, replace `val current = _session.value` (the first line inside `authoriseLock.withLock {`) with:
```kotlin
            val current = _session.value
            val setup = setupPerson
            if (setup != null && current == setup) {
                val grants = grantedFor(setup.role, anyOf)
                if (grants.isNotEmpty() && allow(setup, grants)) {
                    restartExpiry(SETUP_IDLE_MS)
                    return@withLock Authorised(setup.person, setup.role, grants)
                }
            }
```
3. In `promptUntilResolved`, replace `_session.value = identified` with:
```kotlin
            // A PIN at the pad starts an ordinary session, whoever it is.
            setupPerson = null
            _session.value = identified
```
4. Replace `lock()` and `restartExpiry()` with:
```kotlin
    override fun beginSetupSession(person: Identified) {
        setupPerson = person
        _session.value = person
        restartExpiry(SETUP_IDLE_MS)
    }

    override fun endSetupSession() {
        if (setupPerson == null) return
        setupPerson = null
        if (_session.value != null) restartExpiry()
    }

    override fun touch() {
        if (_session.value == null) return
        restartExpiry(if (setupPerson != null) SETUP_IDLE_MS else SESSION_TIMEOUT_MS)
    }

    override fun lock() {
        setupPerson = null
        synchronized(expiryLock) { expiry?.cancel() }
        _session.value = null
    }

    private fun restartExpiry(timeoutMillis: Long = SESSION_TIMEOUT_MS) = synchronized(expiryLock) {
        expiry?.cancel()
        val guarded = _session.value
        expiry = scope.launch {
            delay(timeoutMillis)
            // Only beginSetupSession sets setupPerson; every way a session ends clears it.
            if (_session.compareAndSet(guarded, null)) setupPerson = null
        }
    }
```

- [ ] **Step 4: Add the choose-a-PIN pad**

In `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt`:
1. Add the imports `androidx.compose.runtime.mutableIntStateOf` and `androidx.compose.runtime.getValue`/`setValue` (already there).
2. Replace `PinPadSheet` with:
```kotlin
/**
 * Hand-off §7 PIN pad asking who's there. The `rgba(0,0,0,.5)` scrim covers the sheet's 600 dp when [overSheet],
 * otherwise the whole screen; the card is centred in it.
 */
@Composable
fun PinPadSheet(
    label: String,
    reason: PinReason,
    error: PinError?,
    lockedUntilMillis: Long?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    overSheet: Boolean = false,
) {
    // Counts down from the initial value rather than re-reading the wall clock, so tests with a
    // virtual frame clock stay deterministic.
    val secondsLeft by produceState(secondsUntil(lockedUntilMillis), lockedUntilMillis) {
        while (value > 0) {
            delay(1_000)
            value -= 1
        }
    }
    val locked = secondsLeft > 0
    PinPadFrame(
        title = "Who's this?",
        line = pinReasonText(reason, label),
        message = when {
            locked -> "Too many tries — wait ${secondsLeft}s"
            error is PinError.WrongPin -> "Wrong PIN — try again"
            error is PinError.NotAllowed -> error.message
            else -> ""
        },
        wrongPin = error is PinError.WrongPin,
        enabled = !locked,
        onSubmit = onSubmit,
        onCancel = onCancel,
        overSheet = overSheet,
    )
}

/** 4a design §4.2: choosing a new PIN. */
const val CHOOSE_PIN = "Choose a 4-digit PIN"
const val ENTER_IT_AGAIN = "Enter it again"
const val PINS_DIDNT_MATCH = "Those PINs didn't match — try again."

/**
 * 4a design §4.2: the PIN pad twice, [CHOOSE_PIN] then [ENTER_IT_AGAIN]; a mismatch starts again with
 * [PINS_DIDNT_MATCH]. [onChosen] gets the PIN once both agree. [drawScrim] false leaves the scrim to whoever shows it
 * (the shell's overlay already draws one).
 */
@Composable
fun ChoosePinPad(onChosen: (String) -> Unit, onCancel: () -> Unit, overSheet: Boolean = false, drawScrim: Boolean = true) {
    var first by remember { mutableStateOf<String?>(null) }
    var mismatches by remember { mutableIntStateOf(0) }
    val retrying = first == null && mismatches > 0
    // A new key for each stage, so the pad starts with no digits.
    key(first, mismatches) {
        PinPadFrame(
            title = if (first == null) CHOOSE_PIN else ENTER_IT_AGAIN,
            line = null,
            message = if (retrying) PINS_DIDNT_MATCH else "",
            wrongPin = retrying,
            enabled = true,
            onSubmit = { pin ->
                val chosen = first
                when {
                    chosen == null -> first = pin
                    chosen == pin -> onChosen(pin)
                    else -> {
                        first = null
                        mismatches++
                    }
                }
            },
            onCancel = onCancel,
            overSheet = overSheet,
            drawScrim = drawScrim,
        )
    }
}

/**
 * The PIN pad's card and keypad (hand-off §7). It catches every tap on the screen: a tap outside the card cancels. The
 * pad submits on the fourth digit. [wrongPin] rings the empty dots in `danger`; [line] null leaves the reason line out.
 */
@Composable
fun PinPadFrame(
    title: String,
    line: String?,
    message: String,
    wrongPin: Boolean,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    overSheet: Boolean = false,
    drawScrim: Boolean = true,
) {
    val c = Culvery.colors
    var digits by remember { mutableStateOf("") }
    val canType = enabled && digits.length < PinHasher.PIN_LENGTH
    val type: (String) -> Unit = { d ->
        digits += d
        if (digits.length == PinHasher.PIN_LENGTH) onSubmit(digits)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pin_scrim")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Cancel",
                onClick = onCancel,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(if (overSheet) Alignment.CenterEnd else Alignment.Center)
                .then(if (overSheet) Modifier.fillMaxHeight().width(ShellTokens.sheetWidth) else Modifier.fillMaxSize())
                .testTag("pin_area")
                .then(if (drawScrim) Modifier.background(ShellTokens.pinScrim) else Modifier),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PinPadDimens.gap),
                modifier = Modifier
                    .testTag("pin_card")
                    .width(PinPadDimens.cardWidth)
                    .clip(RoundedCornerShape(PinPadDimens.cardRadius))
                    .background(c.surf)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = PinPadDimens.cardPaddingH, vertical = PinPadDimens.cardPaddingV),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(PinPadDimens.badge).clip(CircleShape).background(c.accentSoft),
                ) {
                    HhIcon("lock", size = PinPadDimens.badgeIcon, tint = c.accent)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PinPadDimens.titleReasonGap),
                ) {
                    Text(title, style = PinPadType.title, color = c.ink)
                    if (line != null) Text(line, style = PinPadType.reason, color = c.mute, textAlign = TextAlign.Center)
                }
                Dots(filled = digits.length, error = wrongPin && digits.isEmpty())
                Text(
                    message,
                    style = PinPadType.error,
                    color = c.danger,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("pin_error").height(PinPadDimens.errorLine),
                )
                Column(verticalArrangement = Arrangement.spacedBy(PinPadDimens.keyRowGap)) {
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(PinPadDimens.keyColumnGap)) {
                            row.forEach { d -> DigitKey(d, enabled = canType) { type(d) } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(PinPadDimens.keyColumnGap)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .testTag("pin_cancel")
                                .size(PinPadDimens.key)
                                .clip(CircleShape)
                                .clickable(onClick = onCancel),
                        ) {
                            Text("Cancel", style = PinPadType.cancel, color = c.ink)
                        }
                        DigitKey("0", enabled = canType) { type("0") }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .testTag("pin_backspace")
                                .size(PinPadDimens.key)
                                .clip(CircleShape)
                                .clickable(enabled = digits.isNotEmpty() && enabled) { digits = digits.dropLast(1) },
                        ) {
                            HhIcon("backspace", size = PinPadDimens.backspaceIcon, tint = c.ink, contentDescription = "Delete last digit")
                        }
                    }
                }
            }
        }
    }
}
```
`Dots`, `DigitKey` and `secondsUntil` are unchanged. The existing PIN pad's pixels don't change: `PinPadHost` still calls `PinPadSheet`, whose frame draws exactly what it drew.

- [ ] **Step 5: Keep the fakes compiling**

In `app/src/test/java/uk/co/siland/culvery/shell/Fakes.kt`, in `FakeAccessControl`, after `override fun lock() { session.value = null }` add:
```kotlin
    var touches = 0
    var setupSessionsBegun = 0
    override fun beginSetupSession(person: Identified) {
        setupSessionsBegun++
        session.value = person
    }
    override fun endSetupSession() = Unit
    override fun touch() { touches++ }
```

In `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt`, add:
```kotlin
    @Test
    fun theShellNeverBeginsASetupSession() = runTest {
        access.result = admin
        val vm = vm()
        vm.openSettings()
        vm.closeSettings()
        vm.exitKiosk()
        vm.signOut()
        assertThat(access.setupSessionsBegun).isEqualTo(0)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt`, in `NobodyMay`, after `override fun lock() = Unit` add:
```kotlin

    override fun beginSetupSession(person: Identified) = Unit

    override fun endSetupSession() = Unit

    override fun touch() = Unit
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :core:access:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS, the existing `PinPadTest` cases included.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`; `:app`'s `pin_pad_*` baselines are unchanged.

- [ ] **Step 8: Commit**

```bash
git add core/access app/src/test capability/calendar/src/test
git commit -m "Add the setup session, touch to keep a session open, and a pad for choosing a new PIN"
```

---

### Task 5: Town search — `LocationSearch` and `:provider:weather-openmeteo` (D2, §3.8)

**Files:**
- Create: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/LocationSearch.kt`
- Modify: `settings.gradle.kts`
- Create: `provider/weather-openmeteo/build.gradle.kts`, `provider/weather-openmeteo/src/main/AndroidManifest.xml`, `provider/weather-openmeteo/src/test/resources/robolectric.properties`
- Create: `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoLocationSearch.kt`, `di/OpenMeteoModule.kt`
- Test: `provider/weather-openmeteo/src/test/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoLocationSearchTest.kt` (create)

**Interfaces:**
- Consumes: the `:core:setup` module (Task 1).
- Produces:
  - `data class PlaceMatch(val name: String, val region: String?, val country: String?, val latitude: Double, val longitude: Double, val timeZoneId: String) { val label: String }` — "Brighton, England, United Kingdom"
  - `class LocationSearchException(message: String) : Exception`
  - `interface LocationSearch { suspend fun search(query: String): List<PlaceMatch> }` — up to five towns; throws `LocationSearchException` on a network, HTTP or parse failure; cancelling the caller cancels the HTTP call
  - `const val OPEN_METEO_GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"`
  - `class OpenMeteoLocationSearch(url: HttpUrl, client: OkHttpClient) : LocationSearch`
  - `OpenMeteoModule` binds `LocationSearch` (`@Singleton`)

- [ ] **Step 1: Add the contract and the module**

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/LocationSearch.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

/** A town the search found (4a design §3.1). [region] and [country] are null when the service gives none. */
data class PlaceMatch(
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
) {
    /** "Brighton, England, United Kingdom"; a region that repeats the name is left out. */
    val label: String get() = listOfNotNull(name, region?.takeIf { it != name }, country).joinToString(", ")
}

/** The search couldn't be made or its answer read. Fixed words only: a cause could quote the query (ruling 4). */
class LocationSearchException(message: String) : Exception(message)

/** Home location by town (4a design D2). Bound by `:provider:weather-openmeteo`. */
interface LocationSearch {
    /** Up to five towns matching [query]. Main-safe; cancelling the caller cancels the request. */
    suspend fun search(query: String): List<PlaceMatch>
}
```

In `settings.gradle.kts`, after `include(":provider:calendar-google")` add `include(":provider:weather-openmeteo")`.

Create `provider/weather-openmeteo/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:setup"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
}
```

Create `provider/weather-openmeteo/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
</manifest>
```

Create `provider/weather-openmeteo/src/test/resources/robolectric.properties`:
```properties
qualifiers=w1280dp-h800dp-land-hdpi
```

- [ ] **Step 2: Write the failing test**

Create `provider/weather-openmeteo/src/test/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoLocationSearchTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.weather_openmeteo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.setup.LocationSearchException
import uk.co.siland.culvery.core.setup.PlaceMatch

/** Open-Meteo's answer for "Brighton", trimmed; the third town has no time zone. */
private const val BRIGHTON = """{"results":[
  {"id":2654710,"name":"Brighton","latitude":50.82838,"longitude":-0.13947,"elevation":19.0,"feature_code":"PPLA2",
   "country_code":"GB","admin1":"England","admin2":"East Sussex","timezone":"Europe/London","country":"United Kingdom"},
  {"id":2174003,"name":"Brighton","latitude":-37.90539,"longitude":144.99,"country_code":"AU",
   "admin1":"Victoria","timezone":"Australia/Melbourne","country":"Australia"},
  {"id":9999999,"name":"Brighton Siding","latitude":10.5,"longitude":20.5,"country":"Nowhere"}
],"generationtime_ms":0.61}"""

// Robolectric for android.util.Log; the server is a real MockWebServer on localhost.
@RunWith(AndroidJUnit4::class)
class OpenMeteoLocationSearchTest {
    private lateinit var server: MockWebServer
    private lateinit var search: OpenMeteoLocationSearch
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()

    @Before
    fun setUp() {
        ShadowLog.clear()
        server = MockWebServer()
        server.start()
        search = OpenMeteoLocationSearch(server.url("/v1/search"), client)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private suspend fun failure(query: String = "Brighton"): Throwable? =
        try {
            search.search(query)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }

    @Test
    fun itAsksForFiveEnglishResultsAndReadsEachTownWithAZone() = runTest {
        answer(BRIGHTON)
        val found = search.search("Brighton")
        val url = server.takeRequest().requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/v1/search")
        assertThat(listOf("name", "count", "language", "format").map { url.queryParameter(it) })
            .containsExactly("Brighton", "5", "en", "json").inOrder()
        assertThat(found).containsExactly(
            PlaceMatch("Brighton", "England", "United Kingdom", 50.82838, -0.13947, "Europe/London"),
            PlaceMatch("Brighton", "Victoria", "Australia", -37.90539, 144.99, "Australia/Melbourne"),
        ).inOrder()
        assertThat(found.first().label).isEqualTo("Brighton, England, United Kingdom")
    }

    @Test
    fun noMatchIsAnEmptyList() = runTest {
        answer("""{"generationtime_ms":0.3}""")
        assertThat(search.search("Xqzt")).isEmpty()
    }

    @Test
    fun anErrorAnswerIsALocationSearchException() = runTest {
        answer("""{"error":true,"reason":"Parameter count must be between 1 and 100."}""", code = 400)
        assertThat(failure()).isInstanceOf(LocationSearchException::class.java)
    }

    @Test
    fun aDroppedConnectionIsALocationSearchException() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThat(failure()).isInstanceOf(LocationSearchException::class.java)
    }

    @Test
    fun anUnreadableAnswerIsALocationSearchException() = runTest {
        answer("<html>Gateway</html>")
        assertThat(failure()).isInstanceOf(LocationSearchException::class.java)
    }

    @Test
    fun cancellingTheSearchCancelsTheCall() = runTest {
        // Its own client with a long read timeout, so only cancelling the call can end it inside the second.
        val patient = OkHttpClient.Builder().readTimeout(Duration.ofSeconds(30)).build()
        val slow = OpenMeteoLocationSearch(server.url("/v1/search"), patient)
        // One byte every 3 s: the headers arrive, the body stalls.
        server.enqueue(MockResponse().setBody(BRIGHTON).throttleBody(1, 3, TimeUnit.SECONDS))
        val returned = withContext(Dispatchers.Default) {
            val call = launch { slow.search("Brighton") }
            checkNotNull(runInterruptible(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }) { "the search never reached the server" }
            call.cancel()
            withTimeoutOrNull(1_000) { call.join() } != null
        }
        assertThat(returned).isTrue()
        withContext(Dispatchers.Default) { withTimeout(1_000) { while (patient.dispatcher.runningCallsCount() > 0) delay(10) } }
    }

    @Test
    fun nothingLoggedHoldsTheQueryOrAPlace() = runTest {
        answer("{}", code = 500)
        failure()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        failure()
        answer("""{"results":[{"name":"Brighton","latitude":"fifty-one"}]}""")
        failure()
        assertNoSecretsLogged(TAG, listOf("Brighton", "brighton", "50.", "name=", "fifty-one"), minLines = 3)
    }
}

/**
 * Nothing logged under [tag], with its whole chain of causes, holds any of [secrets]; at least [minLines] were logged,
 * so a check that saw no log can't pass by default. (`:core:setup` and the calendar have the same helper in their own
 * test sources; test sources aren't shared between modules.)
 */
private fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1) {
    val logs = ShadowLog.getLogs().filter { it.tag == tag }
    assertWithMessage("lines logged under $tag").that(logs.size).isAtLeast(minLines)
    logs.forEach { log ->
        val text = "${log.msg} ${generateSequence(log.throwable) { it.cause }.joinToString(" ")}"
        secrets.forEach { assertWithMessage(text).that(text).doesNotContain(it) }
    }
}
```

- [ ] **Step 3: Run the test to see it fail**

Run: `./gradlew :provider:weather-openmeteo:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'OpenMeteoLocationSearch'" and "'TAG'".

- [ ] **Step 4: Write the search**

Create `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoLocationSearch.kt`:
```kotlin
package uk.co.siland.culvery.provider.weather_openmeteo

import android.util.Log
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.core.setup.LocationSearchException
import uk.co.siland.culvery.core.setup.PlaceMatch

/** Open-Meteo's geocoding API: no key (4a design D2). */
const val OPEN_METEO_GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"

/** The module's one log tag. */
internal const val TAG = "OpenMeteo"

private const val RESULTS = "5"

@Serializable
internal data class GeocodingAnswer(val results: List<GeocodingPlace> = emptyList())

@Serializable
internal data class GeocodingPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    val admin1: String? = null,
    val country: String? = null,
)

private val GeocodingJson = Json { ignoreUnknownKeys = true }

/**
 * Town search over Open-Meteo's geocoding (4a design §3.8): up to five towns, English names; one without a time zone is
 * left out, as a home needs one. Nothing logged names the query, a town or its coordinates.
 */
class OpenMeteoLocationSearch(private val url: HttpUrl, private val client: OkHttpClient) : LocationSearch {
    override suspend fun search(query: String): List<PlaceMatch> {
        val request = Request.Builder()
            .url(
                url.newBuilder()
                    .addQueryParameter("name", query)
                    .addQueryParameter("count", RESULTS)
                    .addQueryParameter("language", "en")
                    .addQueryParameter("format", "json")
                    .build(),
            )
            .build()
        val answer = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            // A cancelled caller gets its cancellation, never "couldn't search".
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Town search failed (${e::class.simpleName})")
            throw LocationSearchException("Couldn't reach the town search")
        }
        if (answer.code !in 200..299) {
            Log.w(TAG, "Town search answered ${answer.code}")
            throw LocationSearchException("The town search answered ${answer.code}")
        }
        val parsed = try {
            GeocodingJson.decodeFromString(GeocodingAnswer.serializer(), answer.body)
        } catch (e: IllegalArgumentException) {
            // Not the exception itself: kotlinx.serialization quotes the body, which holds the towns.
            Log.w(TAG, "Town search sent an answer the tablet can't read (${e::class.simpleName})")
            throw LocationSearchException("The town search sent an answer the tablet can't read")
        }
        return parsed.results.mapNotNull { place ->
            place.timezone?.let { zone -> PlaceMatch(place.name, place.admin1, place.country, place.latitude, place.longitude, zone) }
        }
    }
}

private class Answer(val code: Int, val body: String)

/**
 * Enqueues the call and suspends until its whole body is in, reading it on OkHttp's thread (3a's `Call.await`):
 * cancelling the coroutine cancels the call, which ends a stalled read at once.
 */
private suspend fun Call.await(): Answer = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = try {
                    response.use { Answer(it.code, it.body?.string().orEmpty()) }
                } catch (e: Throwable) {
                    // After a cancel this is the closed socket and the continuation is already cancelled; anything else
                    // must reach the caller, or it would hang.
                    cont.resumeWithException(e)
                    return
                }
                cont.resume(answer)
            }
        },
    )
}
```

Create `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/di/OpenMeteoModule.kt`:
```kotlin
package uk.co.siland.culvery.provider.weather_openmeteo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.provider.weather_openmeteo.OPEN_METEO_GEOCODING_URL
import uk.co.siland.culvery.provider.weather_openmeteo.OpenMeteoLocationSearch

// A search is typed live: give up sooner than the calendar's sync does.
private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
private val READ_TIMEOUT: Duration = Duration.ofSeconds(15)

@Module
@InstallIn(SingletonComponent::class)
object OpenMeteoModule {
    // Its own client, inside the binding, as Google's is (ruling 3): no OkHttpClient in the graph to collide with.
    @Provides
    @Singleton
    fun locationSearch(): LocationSearch = OpenMeteoLocationSearch(
        OPEN_METEO_GEOCODING_URL.toHttpUrl(),
        OkHttpClient.Builder().connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build(),
    )
}
```

- [ ] **Step 5: Run the test to see it pass**

Run: `./gradlew :provider:weather-openmeteo:testDebugUnitTest :core:setup:testDebugUnitTest`
Expected: PASS (7 tests in the provider).

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. `ModuleBoundaries` accepts `:provider:weather-openmeteo` → `:core:setup` (a provider may depend on any core module).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts core/setup provider/weather-openmeteo
git commit -m "Search for the home town through Open-Meteo's geocoding"
```

---

### Task 6: `calendar.db` v5, the refresh that keeps a hide, mappings, the master always shown, and the household follower (§3.8–§3.10, D13)

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`, `db/Migrations.kt`, `di/CalendarModule.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`, `SourceRefresher.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/HouseholdFollower.kt`
- Create (generated): `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/5.json`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`, `CalendarMigrationTest.kt`, `SourceRefresherTest.kt` (modify); `HouseholdFollowerTest.kt` (create)

**Interfaces:**
- Consumes: `HouseholdRepository.people`, `HouseholdZone.zone`, `CalendarSetup.syncSoon()`, `retryWithBackoff` (3a), `Startable`.
- Produces:
  - `SourceEntity.shownInService: Boolean?` (v5); `val MIGRATION_4_5`; `CalendarDatabase` version 5
  - `CalendarStore.setMapping(connectionId: String, sourceId: String, person: PersonId, visible: Boolean)` — `IllegalArgumentException` for hiding the master or an unknown calendar
  - `CalendarStore.remapMissingPeople(existing: Set<PersonId>)` — every calendar mapped to someone not in [existing] becomes Family
  - `CalendarStore.queuedChanges(connectionId: String): Int`
  - `CalendarStore.setMaster` also shows the calendar; `refreshSources` follows a calendar's tick only when it changed in the service (the master always shown)
  - `fun masterGone(serviceName: String): String` = "{Service}: can't find the master calendar — choose a new one in Settings › Calendars."
  - `@Singleton class HouseholdFollower @Inject constructor(household: HouseholdRepository, zone: HouseholdZone, store: CalendarStore, setup: CalendarSetup, @ApplicationScope scope: CoroutineScope) : Startable`, bound `@IntoSet`; `internal val zoneRead: CompletableDeferred<Unit>` (completes when it has read the zone it starts with; tests wait on it)

- [ ] **Step 1: Write the failing tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`, add these tests after `aDuplicatedSourceIdInTheProviderListIsAddedOnce` (the helpers `connect`, `listed`, `mapping`, `change` and `rowsFor` are the file's own):
```kotlin
    @Test
    fun aHiddenCalendarStaysHiddenWhileItsTickIsUnchanged() = runTest {
        connect("s1", "s2")
        store.setMapping("c1", "s1", PersonId.FAMILY, visible = false)
        store.refreshSources("c1", listOf(listed("s1"), listed("s2")), 5_000L, mapping("mia"))
        store.refreshSources("c1", listOf(listed("s1"), listed("s2")), 6_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isFalse()
    }

    @Test
    fun aChangedTickInTheServiceWins() = runTest {
        connect("s1")
        store.setMapping("c1", "s1", PersonId.FAMILY, visible = false)
        store.refreshSources("c1", listOf(listed("s1", shown = false)), 5_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isFalse()
        store.refreshSources("c1", listOf(listed("s1", shown = true)), 6_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
        store.setMapping("c1", "s1", PersonId.FAMILY, visible = true)
        store.refreshSources("c1", listOf(listed("s1", shown = false)), 7_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isFalse()
    }

    @Test
    fun theMasterStaysShownWhateverTheServiceSays() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        store.refreshSources("c1", listOf(listed("s1", writable = true, shown = false)), 5_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
    }

    @Test
    fun setMappingChangesWhoItIsForAndWhetherItShows() = runTest {
        connect("s1")
        store.setMapping("c1", "s1", PersonId("mia"), visible = false)
        assertThat(store.source("c1", "s1")!!.mapping).isEqualTo(SourceMapping(PersonId("mia"), visible = false))
    }

    @Test
    fun theMasterCannotBeHidden() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.setMapping("c1", "s1", PersonId.FAMILY, visible = false) }
        }
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
    }

    @Test
    fun makingAHiddenCalendarTheMasterShowsIt() = runTest {
        connect("s1")
        store.setMapping("c1", "s1", PersonId.FAMILY, visible = false)
        store.setMaster("c1", "s1")
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
    }

    @Test
    fun calendarsOfSomeoneGoneBecomeFamilyAndTheRestStay() = runTest {
        connect("s1", "s2", "s3", mapping = mapOf(
            "s1" to SourceMapping(PersonId("mia"), visible = true),
            "s2" to SourceMapping(PersonId("sam"), visible = false),
        ))
        store.remapMissingPeople(setOf(PersonId("sam")))
        assertThat(store.sources().first().associate { it.source.id to it.mapping }).containsExactly(
            "s1", SourceMapping(PersonId.FAMILY, visible = true),
            "s2", SourceMapping(PersonId("sam"), visible = false),
            "s3", SourceMapping(PersonId.FAMILY, visible = true),
        )
    }

    @Test
    fun queuedChangesCountsOnlyThatConnection() = runTest {
        connect("s1")
        store.addConnection(Connection("c2", "calendar.test", "Other", emptyMap()), listOf(CalendarSource("t1", "T1", writable = false)), emptyMap())
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "b", draft = null))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "c", draft = null).copy(connectionId = "c2", sourceId = "t1"))
        assertThat(store.queuedChanges("c1")).isEqualTo(2)
        assertThat(store.queuedChanges("c2")).isEqualTo(1)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarMigrationTest.kt`:
1. Add the import `uk.co.siland.culvery.capability.calendar.db.MIGRATION_4_5`, and in each of the three `Room.databaseBuilder(…)` chains replace `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)` with `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`.
2. Add, before `tableNames`:
```kotlin
    @Test
    fun migrationFromV4CopiesVisibilityIntoTheLastSeenTick() = runTest {
        file.parentFile?.mkdirs()
        file.delete()

        val v4 = helper.createDatabase(4)
        v4.execSQL(
            "INSERT INTO connection (id, providerId, label, configJson, health, healthMessage, lastSyncMillis, " +
                "sourcesCheckedMillis, needsSignInSinceMillis) " +
                "VALUES ('c1', 'calendar.test', 'Google', '{}', 'OK', NULL, 1234, 5000, NULL)",
        )
        v4.execSQL(
            "INSERT INTO source (connectionId, sourceId, name, writable, visible, personId, isMaster) " +
                "VALUES ('c1', 's1', 'Family', 1, 1, 'family', 1), ('c1', 's2', 'Alex', 0, 0, 'alex-id', 0), " +
                "('c1', 's3', 'Swimming', 0, 1, 'mia-id', 0)",
        )
        v4.close()

        val v5 = helper.runMigrationsAndValidate(5, listOf(MIGRATION_4_5))
        try {
            assertThat(tableNames(v5)).containsExactly("connection", "event", "outbox", "source", "sync_state").inOrder()
            val statement = v5.prepare("SELECT sourceId, shownInService FROM source ORDER BY sourceId")
            val ticks = mutableListOf<Pair<String, Long>>()
            try {
                while (statement.step()) ticks += statement.getText(0) to statement.getLong(1)
            } finally {
                statement.close()
            }
            assertThat(ticks).containsExactly("s1" to 1L, "s2" to 0L, "s3" to 1L).inOrder()
        } finally {
            v5.close()
        }

        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val store = CalendarStore(db)
            // The service still ticks as before: an upgraded install keeps every choice, the hidden Alex included.
            val listed = listOf(
                CalendarSource("s1", "Family", writable = true, primary = true),
                CalendarSource("s2", "Alex", writable = false, shown = false),
                CalendarSource("s3", "Swimming", writable = false),
            )
            store.refreshSources("c1", listed, 6_000L) { SourceMapping.Default }
            assertThat(store.sources().first().associate { it.source.id to it.mapping }).containsExactly(
                "s1", SourceMapping(PersonId.FAMILY, visible = true),
                "s2", SourceMapping(PersonId("alex-id"), visible = false),
                "s3", SourceMapping(PersonId("mia-id"), visible = true),
            )
            assertThat(store.master().first()?.source?.id).isEqualTo("s1")
        } finally {
            db.close()
        }
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/SourceRefresherTest.kt`, replace both `"Google Calendar: can't find the master calendar, so new events can't be added"` with `"Google Calendar: can't find the master calendar — choose a new one in Settings › Calendars."`.

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/HouseholdFollowerTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room; the follower runs on its own scope, so the waits are in real time.
@RunWith(AndroidJUnit4::class)
class HouseholdFollowerTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var people: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var store: CalendarStore
    private val syncs = AtomicInteger()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        calendar = calendarDb()
        people = householdDb()
        household = HouseholdRepository(people)
        store = CalendarStore(calendar)
    }

    @After
    fun tearDown() {
        scope.cancel()
        calendar.close()
        people.close()
    }

    private fun follow(): HouseholdFollower = HouseholdFollower(
        household,
        HouseholdZone(household),
        store,
        CalendarSetup(store, emptySet(), { emptyList() }, RecordingToaster(), WallClock { 0L }, EmptyCoroutineContext) { syncs.incrementAndGet() },
        scope,
    ).also { it.start() }

    private suspend fun waitFor(condition: suspend () -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (!condition()) delay(10) } }

    @Test
    fun aCalendarMappedToSomeoneRemovedShowsAsFamily() = runBlocking {
        val mia = household.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        store.addConnection(
            Connection("c1", "calendar.test", "Test", emptyMap()),
            listOf(CalendarSource("swim", "Mia's swimming", writable = false)),
            mapOf("swim" to SourceMapping(mia.id, visible = true)),
        )
        follow()
        household.removePerson(mia.id)
        waitFor { store.source("c1", "swim")!!.mapping.person == PersonId.FAMILY }
    }

    @Test
    fun aChangeOfTimeZoneAsksForASyncAndTheSameZoneDoesNot() = runBlocking {
        household.setLocation(HomeLocation("London, England, United Kingdom", 51.5074, -0.1278, "Europe/London"))
        val follower = follow()
        // The zone it starts with never asks for a sync.
        withTimeout(5_000) { follower.zoneRead.await() }
        household.setLocation(HomeLocation("Paris, Île-de-France, France", 48.8534, 2.3488, "Europe/Paris"))
        waitFor { syncs.get() == 1 }
        // Lyon shares Paris's zone, so only Berlin asks.
        household.setLocation(HomeLocation("Lyon, Auvergne-Rhône-Alpes, France", 45.7485, 4.8467, "Europe/Paris"))
        household.setLocation(HomeLocation("Berlin, Land Berlin, Germany", 52.5244, 13.4105, "Europe/Berlin"))
        waitFor { syncs.get() >= 2 }
        assertThat(syncs.get()).isEqualTo(2)
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'setMapping'", "'remapMissingPeople'", "'queuedChanges'", "'MIGRATION_4_5'" and "'HouseholdFollower'".

- [ ] **Step 3: Write v5 and the store changes**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`:
1. In `SourceEntity`, after `isMaster`, add:
```kotlin
    /**
     * v5 (4a design §3.10): the service's tick when it was last seen, so a refresh changes `visible` only when the tick
     * changes; null until first seen.
     */
    val shownInService: Boolean? = null,
```
2. Replace the `updateSource` query and function with:
```kotlin
    @Query(
        "UPDATE source SET name = :name, writable = :writable, visible = :visible, shownInService = :shownInService " +
            "WHERE connectionId = :connectionId AND sourceId = :sourceId",
    )
    suspend fun updateSource(connectionId: String, sourceId: String, name: String, writable: Boolean, visible: Boolean, shownInService: Boolean)
```
3. Replace `markMaster`'s query with `"UPDATE source SET isMaster = 1, writable = 1, visible = 1 WHERE connectionId = :connectionId AND sourceId = :sourceId"`.
4. After `markMaster`, add:
```kotlin
    @Query("UPDATE source SET personId = :personId, visible = :visible WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun setMapping(connectionId: String, sourceId: String, personId: String, visible: Boolean): Int

    @Query("UPDATE source SET personId = :personId WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun setPerson(connectionId: String, sourceId: String, personId: String)

    @Query("SELECT * FROM source ORDER BY connectionId, name")
    suspend fun allSourcesNow(): List<SourceEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE connectionId = :connectionId")
    suspend fun countOutboxOf(connectionId: String): Int
```
5. Change `version = 4` to `version = 5`.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/Migrations.kt`, add at the end:
```kotlin

/**
 * v5 (Plan 4a): the service's last-seen tick per calendar, seeded from `visible` (which until now always followed the
 * tick, the primary always shown), so the first refresh after the upgrade changes nothing. The SQL must match
 * schemas/…/5.json exactly.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `source` ADD COLUMN `shownInService` INTEGER")
        db.execSQL("UPDATE `source` SET `shownInService` = `visible`")
    }
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`, import `MIGRATION_4_5` and replace `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)` with `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`. Also add the `HouseholdFollower` binding after `syncLoop`:
```kotlin
    @Binds
    @IntoSet
    abstract fun householdFollower(impl: HouseholdFollower): Startable
```
with the import `uk.co.siland.culvery.capability.calendar.HouseholdFollower`.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`:
1. In `addConnection`, replace `SourceEntity(connection.id, s.id, s.name, s.writable, m.visible, m.person.value)` with `SourceEntity(connection.id, s.id, s.name, s.writable, m.visible, m.person.value, shownInService = s.visibleOnTablet)`.
2. Replace `refreshSources`' KDoc and the `listed.values.forEach { … }` block with:
```kotlin
    /**
     * Follows the provider's list of [sources] (3a design §3.4, 4a design §3.10), in one transaction: a new source is
     * added with [mappingForNew]; an existing one keeps its person, takes the listed name and writability, and takes the
     * service's tick as its visibility only when that tick has changed since it was last seen (ruling 12: the tick is
     * `shown || primary`), so a calendar hidden or shown on the tablet stays so; the master is always shown. One no
     * longer listed goes with its events, cursor and queued changes. Returns true when the master went, or became
     * read-only, and was cleared. Writes nothing once the connection is gone.
     */
```
   and
```kotlin
        listed.values.forEach { s ->
            val existing = stored[s.id]
            val tick = s.visibleOnTablet
            if (existing == null) {
                val m = mappingForNew(s)
                dao.insertSources(listOf(SourceEntity(connectionId, s.id, s.name, s.writable, m.visible, m.person.value, shownInService = tick)))
            } else {
                val visible = when {
                    existing.isMaster -> true
                    existing.shownInService == tick -> existing.visible
                    else -> tick
                }
                dao.updateSource(connectionId, s.id, s.name, s.writable, visible, tick)
                if (existing.isMaster && !s.writable) {
                    dao.clearMaster()
                    masterCleared = true
                }
            }
        }
```
3. Replace `setMaster`'s KDoc first sentence with "Makes this source the one master calendar, and shows it, clearing any other master, in one transaction." (the body is unchanged; `markMaster` now sets `visible`).
4. After `setMaster`, add:
```kotlin

    /**
     * Who a calendar is for and whether it shows, chosen on the tablet (4a design §3.9, D13). The master is always shown:
     * hiding it is refused.
     */
    suspend fun setMapping(connectionId: String, sourceId: String, person: PersonId, visible: Boolean) = db.withTransaction {
        val row = requireNotNull(dao.source(connectionId, sourceId)) { "No such calendar" }
        require(visible || !row.isMaster) { "The master calendar is always shown" }
        dao.setMapping(connectionId, sourceId, person.value, visible)
    }

    /** Every calendar mapped to someone not in [existing] (removed from the household) now shows as Family (4a design §3.9). */
    suspend fun remapMissingPeople(existing: Set<PersonId>) = db.withTransaction {
        dao.allSourcesNow()
            .filter { it.personId != PersonId.FAMILY.value && PersonId(it.personId) !in existing }
            .forEach { dao.setPerson(it.connectionId, it.sourceId, PersonId.FAMILY.value) }
    }

    /** The disconnect confirmation's count (4a design D14). */
    suspend fun queuedChanges(connectionId: String): Int = dao.countOutboxOf(connectionId)
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/SourceRefresher.kt`, replace `masterGone` and its KDoc with:
```kotlin
/** 3a design D7, 4a design §4.5: the master calendar went, or became read-only, so the add buttons hide until another is chosen. */
fun masterGone(serviceName: String): String = "$serviceName: can't find the master calendar — choose a new one in Settings › Calendars."
```

- [ ] **Step 4: Write the household follower**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/HouseholdFollower.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.retryWithBackoff

/**
 * Keeps the calendar in step with the household (4a design §3.8, §3.9): a calendar mapped to someone removed shows as
 * Family, and a change of the household's time zone asks for a sync (all-day events are stored in the zone they were
 * synced in).
 */
@Singleton
class HouseholdFollower @Inject constructor(
    private val household: HouseholdRepository,
    private val zone: HouseholdZone,
    private val store: CalendarStore,
    private val setup: CalendarSetup,
    @ApplicationScope private val scope: CoroutineScope,
) : Startable {
    /** Completes once the zone it starts with is read; the tests wait on it. */
    internal val zoneRead = CompletableDeferred<Unit>()

    override fun start() {
        scope.launch {
            household.people
                // The type only: a message could hold a name.
                .retryWithBackoff { Log.w(TAG, "Couldn't read the household's people; retrying (${it::class.simpleName})") }
                .collect { people ->
                    try {
                        store.remapMissingPeople(people.mapTo(HashSet()) { it.id })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't move a removed person's calendars to Family (${e::class.simpleName})")
                    }
                }
        }
        scope.launch {
            zone.zone
                .retryWithBackoff { Log.w(TAG, "Couldn't read the household's time zone; retrying (${it::class.simpleName})") }
                .distinctUntilChanged()
                .onEach { zoneRead.complete(Unit) }
                // The first value is the zone at start, which the sync loop already uses.
                .drop(1)
                .collect { setup.syncSoon() }
        }
    }

    private companion object {
        const val TAG = "HouseholdFollower"
    }
}
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS. KSP writes `schemas/…/5.json` while compiling; if `migrationFromV4CopiesVisibilityIntoTheLastSeenTick` can't find `5.json` on the first run, run it once more. Check the schema: `grep -n "shownInService" capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/5.json` shows the column with `"affinity": "INTEGER"` and `"notNull": false`.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add capability/calendar
git commit -m "Keep a calendar hidden or shown on the tablet until its tick changes in the service, map calendars on the tablet, and follow the household's people and time zone"
```

---

### Task 7: The wizard frame — steps in order, resume, dots, Back, Next or Skip for now, and the PIN gate whenever the setup session is gone (§3.3, §3.4, §4.1, D9)

**Files:**
- Create: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupDimens.kt` (`SetupDimens`, `SetupType`), `SetupCopy.kt`, `SetupUi.kt` (`StepTitle`)
- Create: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/WizardRules.kt`, `SetupWizard.kt`, `SetupSessionGate.kt`
- Modify: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestHousehold.kt`
- Create: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestUi.kt`
- Test: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/WizardRulesTest.kt`, `SetupWizardTest.kt`, `SetupScreenshotTest.kt` (create)
- Screenshots (`core/setup`): `wizard_frame_{dark,light}`, `wizard_pin_gate_dark` (new)

**Interfaces:**
- Consumes: `SetupStep` (Task 1); `HouseholdRepository.hasActiveAdmin` (Task 3); `AccessControl.beginSetupSession`, `PinReason.ContinueSetup`, `CONTINUE_SETUP` (Task 4); `SingleAction`, `rememberSingleAction`, `HhPillButton(enabled)` (Task 2).
- Produces:
  - `internal object SetupDimens` (every layout value for Tasks 7–10); `internal object SetupType`
  - `SetupCopy.kt`: every `:core:setup` string (Tasks 7–10 use them by these names)
  - `@Composable internal fun StepTitle(title: String, line: String? = null)` (sheet buttons are `:core:ui`'s `HhSheetButton`, Task 2)
  - `internal data class StepStatus(val shown: Boolean, val done: Boolean, val canGoOn: Boolean)`; `internal sealed interface Forward { data class Next(val label: String, val enabled: Boolean); data object Skip }`
  - `internal object WizardRules { fun resumeAt(statuses: List<StepStatus>): Int; fun nextShown(from: Int, statuses: List<StepStatus>): Int?; fun previousShown(from: Int, statuses: List<StepStatus>): Int?; fun dotCount(statuses: List<StepStatus>): Int; fun dotIndex(current: Int, statuses: List<StepStatus>): Int; fun forward(skippable: Boolean, label: String, status: StepStatus): Forward }`
  - `@Singleton class SetupSessionGate @Inject constructor(household: HouseholdRepository, access: AccessControl)` with `val needsPin: Flow<Boolean>`, `fun finish()` and `suspend fun carryOn(): Boolean`
  - `@Composable fun SetupWizard(steps: List<SetupStep>, gate: SetupSessionGate)` (steps already in order); `@Composable internal fun WizardFrame(dotCount: Int, dotIndex: Int, back: (() -> Unit)?, forward: Forward, busy: Boolean, onForward: () -> Unit, onSkip: () -> Unit, content: @Composable () -> Unit)`; `@Composable internal fun PinGateContent(busy: Boolean, onEnterPin: () -> Unit)`
  - Test helpers: `internal class RecordingToaster`; `internal class TestAccess(household, clock, sessionScope)` with `control`, `prompt`, `pins`, `toasts`, `requests`, `answer(vararg pins: String?)`, `listen(scope)`; `internal fun TestScope.testAccess(household): TestAccess`; `internal suspend fun TestAccess.addAdmin(name: String = "Alex", pin: String = "1234"): Person`; in `TestUi.kt`: `CANVAS_W`, `CANVAS_H`, `class TouchModeRule`, `fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1)`, `fun ComposeContentTestRule.awaitText(text: String)`, `fun ComposeContentTestRule.awaitTag(tag: String)`

The gate follows the session (ruling 11): it shows whenever an active Admin exists, nobody is signed in and Done isn't finishing — after a kill past You, after the setup session's 10 idle minutes (Task 4), after a lock. Nothing about it is saved, so a wizard restored after its process died asks again.

- [ ] **Step 1: Add the test helpers**

Replace `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestHousehold.kt` with:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.DefaultAccessControl
import uk.co.siland.culvery.core.access.LockoutStore
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.PinRequest
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.PersonPalette

internal fun householdDb(): HouseholdDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal class RecordingToaster : Toaster {
    // Written from Room's and the app scope's threads, read on the test's.
    val messages: MutableList<String> = CopyOnWriteArrayList()

    override fun show(message: String, icon: String) {
        messages += message
    }
}

/**
 * The real access rules over [household], with PIN pads answered in order from [answer] (null taps Cancel); a pad with
 * no answer queued stays open. The session's timer runs on [sessionScope].
 */
internal class TestAccess(household: HouseholdRepository, clock: WallClock, sessionScope: CoroutineScope) {
    val prompt = PinPromptController()
    val pins = PinManager(household, PinHasher())
    val toasts = RecordingToaster()
    val requests: MutableList<PinRequest> = CopyOnWriteArrayList()
    private val answers = ArrayDeque<String?>()

    val control = DefaultAccessControl(
        registry = PermissionRegistry(setOf(CorePermissionSource())),
        pins = pins,
        lockout = LockoutStore(ApplicationProvider.getApplicationContext()),
        prompt = prompt,
        clock = clock,
        toaster = toasts,
        scope = sessionScope,
    )

    fun answer(vararg pins: String?) = synchronized(answers) { answers.addAll(pins) }

    fun listen(scope: CoroutineScope) {
        scope.launch {
            prompt.request.filterNotNull().collect { request ->
                requests += request
                val next = synchronized(answers) { if (answers.isEmpty()) return@collect else answers.removeFirst() }
                if (next == null) prompt.cancel() else prompt.submit(next)
            }
        }
    }
}

/** For runTest: PIN pads are answered on the test's background scope; the session timer runs outside it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.testAccess(household: HouseholdRepository): TestAccess {
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    backgroundScope.coroutineContext.job.invokeOnCompletion { sessionScope.cancel() }
    return TestAccess(household, WallClock { testScheduler.currentTime }, sessionScope).also { it.listen(backgroundScope) }
}

internal suspend fun TestAccess.addAdmin(name: String = "Alex", pin: String = "1234"): Person =
    pins.addPerson(name, PersonPalette.colors.first(), Role.ADMIN, pin)
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestUi.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.shadows.ShadowLog

/** The tablet's canvas, for every screenshot in this module. */
internal val CANVAS_W = 1280.dp
internal val CANVAS_H = 800.dp

/**
 * Puts Robolectric in touch mode, as the tablet is, so focus and the keyboard behave as they do there (the calendar's
 * rule, copied: test sources aren't shared between modules). Use it at `@get:Rule(order = 0)`, before the compose rule.
 */
class TouchModeRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
            base.evaluate()
        }
    }
}

/**
 * Nothing logged under [tag], with its whole chain of causes, holds any of [secrets]; at least [minLines] were logged,
 * so a check that saw no log can't pass by default.
 */
internal fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1) {
    val logs = ShadowLog.getLogs().filter { it.tag == tag }
    assertWithMessage("lines logged under $tag").that(logs.size).isAtLeast(minLines)
    logs.forEach { log ->
        val text = "${log.msg} ${generateSequence(log.throwable) { it.cause }.joinToString(" ")}"
        secrets.forEach { assertWithMessage(text).that(text).doesNotContain(it) }
    }
}

/** Waits for content read from Room or DataStore before a test's first check or tap. */
internal fun ComposeContentTestRule.awaitText(text: String) =
    waitUntil(5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

internal fun ComposeContentTestRule.awaitTag(tag: String) =
    waitUntil(5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
```

- [ ] **Step 2: Write the failing tests**

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/WizardRulesTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WizardRulesTest {
    private fun status(shown: Boolean = true, done: Boolean = false, canGoOn: Boolean = done) = StepStatus(shown, done, canGoOn)

    @Test
    fun aFreshWizardOpensAtTheFirstStep() {
        assertThat(WizardRules.resumeAt(listOf(status(), status(), status()))).isEqualTo(0)
    }

    @Test
    fun itResumesAtTheFirstShownStepThatIsNotDone() {
        assertThat(WizardRules.resumeAt(listOf(status(done = true), status(done = true), status(), status()))).isEqualTo(2)
    }

    @Test
    fun aSkippedStepIsWhereItResumes() {
        // Welcome passed, Home location skipped (not done), You done.
        assertThat(WizardRules.resumeAt(listOf(status(done = true), status(), status(done = true), status()))).isEqualTo(1)
    }

    @Test
    fun itResumesAtReviewOnceConnectIsDone() {
        // Welcome, …, Connect done; Review shown and not yet done; Done.
        val statuses = listOf(status(done = true), status(done = true), status(done = true), status(), status(canGoOn = true))
        assertThat(WizardRules.resumeAt(statuses)).isEqualTo(3)
    }

    @Test
    fun aHiddenStepIsNeverWhereItResumes() {
        assertThat(WizardRules.resumeAt(listOf(status(done = true), status(shown = false), status()))).isEqualTo(2)
    }

    @Test
    fun withEveryShownStepDoneItOpensAtTheLastShownOne() {
        assertThat(WizardRules.resumeAt(listOf(status(done = true), status(done = true), status(shown = false)))).isEqualTo(1)
    }

    @Test
    fun nextAndBackSkipHiddenSteps() {
        val statuses = listOf(status(), status(shown = false), status(), status(shown = false))
        assertThat(WizardRules.nextShown(0, statuses)).isEqualTo(2)
        assertThat(WizardRules.nextShown(2, statuses)).isNull()
        assertThat(WizardRules.previousShown(2, statuses)).isEqualTo(0)
        assertThat(WizardRules.previousShown(0, statuses)).isNull()
    }

    @Test
    fun theDotsCountOnlyShownSteps() {
        val statuses = listOf(status(), status(shown = false), status(), status())
        assertThat(WizardRules.dotCount(statuses)).isEqualTo(3)
        assertThat(WizardRules.dotIndex(2, statuses)).isEqualTo(1)
    }

    @Test
    fun aSkippableStepNotDoneOffersSkipForNow() {
        assertThat(WizardRules.forward(skippable = true, "Next", status())).isEqualTo(Forward.Skip)
        assertThat(WizardRules.forward(skippable = true, "Next", status(done = true))).isEqualTo(Forward.Next("Next", enabled = true))
    }

    @Test
    fun nextIsEnabledWhenTheStepCanGoOn() {
        assertThat(WizardRules.forward(skippable = false, "Next", status())).isEqualTo(Forward.Next("Next", enabled = false))
        assertThat(WizardRules.forward(skippable = false, "Start", status(canGoOn = true))).isEqualTo(Forward.Next("Start", enabled = true))
    }
}
```

```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

/** A step whose flows the test drives; [onNextCalls] counts forward taps that reached it. */
private class FakeStep(
    override val id: String,
    override val order: Int,
    done: Boolean = false,
    shown: Boolean = true,
    ready: Boolean = true,
    override val skippable: Boolean = false,
    var goesOn: Boolean = true,
    var hold: CompletableDeferred<Unit>? = null,
) : SetupStep {
    val shownFlow = MutableStateFlow(shown)
    val doneFlow = MutableStateFlow(done)
    var onNextCalls = 0
    override val shown: Flow<Boolean> = shownFlow
    override val done: Flow<Boolean> = doneFlow
    override val canGoOn: Flow<Boolean> = MutableStateFlow(ready)

    override suspend fun onNext(): Boolean {
        onNextCalls++
        hold?.await()
        return goesOn
    }

    @Composable
    override fun Content(onNext: () -> Unit) {
        Text("Step $id")
    }
}

// The gate reads Room, so each test waits for the wizard's first content before checking or tapping it.
@RunWith(AndroidJUnit4::class)
class SetupWizardTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var access: TestAccess
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
        access = TestAccess(household, WallClock { System.currentTimeMillis() }, scope).also { it.listen(scope) }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show(vararg steps: FakeStep) = compose.setContent {
        CulveryTheme(dark = true) { SetupWizard(steps.toList(), SetupSessionGate(household, access.control)) }
    }

    /** Alex, the Admin, in the setup session, as the You step leaves them. */
    private fun alexSettingUp() {
        val alex = runBlocking { access.addAdmin() }
        access.control.beginSetupSession(Identified(alex, Role.ADMIN))
    }

    @Test
    fun itOpensAtTheFirstStepNotDoneWithADotForEachShownStep() {
        show(FakeStep("a", 0, done = true), FakeStep("b", 1), FakeStep("c", 2, shown = false), FakeStep("d", 3))
        compose.awaitText("Step b")
        compose.onAllNodesWithTag("wizard_dot").assertCountEquals(3)
    }

    @Test
    fun nextRunsTheStepThenMovesOn() {
        val a = FakeStep("a", 0)
        show(a, FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.onNodeWithText("Step b").assertExists()
        assertThat(a.onNextCalls).isEqualTo(1)
    }

    @Test
    fun aStepThatCannotGoOnDisablesNext() {
        show(FakeStep("a", 0, ready = false), FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").assertIsNotEnabled()
    }

    @Test
    fun aStepThatSaysNoStays() {
        show(FakeStep("a", 0, goesOn = false), FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.onNodeWithText("Step a").assertExists()
    }

    @Test
    fun skipForNowMovesOnWithoutTheStep() {
        val a = FakeStep("a", 0, skippable = true)
        show(a, FakeStep("b", 1))
        compose.awaitText("Skip for now")
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Step b").assertExists()
        assertThat(a.onNextCalls).isEqualTo(0)
    }

    @Test
    fun backGoesToTheShownStepBeforeAndTheFirstHasNone() {
        show(FakeStep("a", 0, done = true), FakeStep("b", 1, shown = false), FakeStep("c", 2))
        compose.awaitText("Step c")
        compose.onNodeWithTag("wizard_back").performClick()
        compose.onNodeWithText("Step a").assertExists()
        compose.onNodeWithTag("wizard_back").assertDoesNotExist()
    }

    @Test
    fun aStepThatAppearsIsReachedByNext() {
        val review = FakeStep("review", 1, shown = false)
        show(FakeStep("connect", 0), review, FakeStep("done", 2))
        compose.awaitText("Step connect")
        review.shownFlow.value = true
        compose.onNodeWithTag("wizard_next").performClick()
        compose.onNodeWithText("Step review").assertExists()
    }

    @Test
    fun aStepHiddenWhileShowingGivesWayToTheOneBefore() {
        // Disconnect on Review hides it: the wizard goes back to Connect, not on to Done.
        val review = FakeStep("review", 1)
        show(FakeStep("connect", 0, done = true), review, FakeStep("done", 2))
        compose.awaitText("Step review")
        review.shownFlow.value = false
        compose.awaitText("Step connect")
        compose.onNodeWithText("Step done").assertDoesNotExist()
    }

    @Test
    fun aDoubleTapOnNextRunsTheStepOnce() {
        val hold = CompletableDeferred<Unit>()
        val a = FakeStep("a", 0, hold = hold)
        show(a, FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.onNodeWithTag("wizard_next").assertIsNotEnabled().performClick()
        hold.complete(Unit)
        compose.awaitText("Step b")
        assertThat(a.onNextCalls).isEqualTo(1)
    }

    @Test
    fun withNoAdminYetTheStepShowsAtOnce() {
        show(FakeStep("a", 0))
        compose.awaitText("Step a")
        assertThat(access.prompt.request.value).isNull()
    }

    @Test
    fun anAdminWithNobodySignedInIsAskedForTheirPinBeforeAnyStep() {
        runBlocking { access.addAdmin() }
        show(FakeStep("a", 0))
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        assertThat(access.prompt.request.value!!.reason).isEqualTo(PinReason.ContinueSetup)
        compose.onNodeWithText("Step a").assertDoesNotExist()
        // The pad is already open, so it is answered directly rather than through the listener's queue.
        access.prompt.submit("1234")
        compose.awaitText("Step a")
        // The setup session is back: even a fresh-PIN permission passes without a pad.
        val before = access.requests.size
        assertThat(runBlocking { access.control.authorise(CorePermissions.PEOPLE_MANAGE) }).isNotNull()
        assertThat(access.requests.size).isEqualTo(before)
    }

    @Test
    fun whenTheSetupSessionEndsTheWizardAsksForThePinAgain() {
        // The session's 10 idle minutes are Task 4's tests; here it ends the way they end it, by clearing the session.
        alexSettingUp()
        show(FakeStep("a", 0), FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.awaitText("Step b")
        access.control.lock()
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        assertThat(access.prompt.request.value!!.reason).isEqualTo(PinReason.ContinueSetup)
        access.prompt.submit("1234")
        // It carries on where it was.
        compose.awaitText("Step b")
    }

    @Test
    fun aRestoredWizardAsksForThePinAgain() {
        alexSettingUp()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CulveryTheme(dark = true) { SetupWizard(listOf(FakeStep("a", 0)), SetupSessionGate(household, access.control)) }
        }
        compose.awaitText("Step a")
        // A process death loses the in-memory session; the restored wizard must not carry on without a PIN.
        access.control.lock()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        compose.onNodeWithText("Step a").assertDoesNotExist()
    }

    @Test
    fun aCancelledPinLeavesTheGateWithEnterPin() {
        runBlocking { access.addAdmin() }
        show(FakeStep("a", 0))
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        access.prompt.cancel()
        compose.awaitText("Enter your PIN to carry on setting up")
        compose.onNodeWithTag("wizard_enter_pin").assertExists()
        compose.onNodeWithText("Step a").assertDoesNotExist()
    }
}
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SetupScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean, content: @Composable BoxScope.() -> Unit) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H).background(Culvery.colors.bg)) { content() }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun frame(name: String, dark: Boolean) = snap(name, dark) {
        WizardFrame(
            dotCount = 6,
            dotIndex = 2,
            back = {},
            forward = Forward.Next("Next", enabled = false),
            busy = false,
            onForward = {},
            onSkip = {},
        ) {
            StepTitle("Who's setting this up?")
            Text("The step's content goes here.")
        }
    }

    @Test fun wizardFrameDark() = frame("wizard_frame_dark", true)
    @Test fun wizardFrameLight() = frame("wizard_frame_light", false)

    @Test
    fun wizardPinGateDark() = snap("wizard_pin_gate_dark", true) { PinGateContent(busy = false, onEnterPin = {}) }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'StepStatus'", "'WizardRules'", "'SetupWizard'", "'SetupSessionGate'", "'WizardFrame'", "'PinGateContent'" (`TestUi.kt` and `TestHousehold.kt` compile on their own).

- [ ] **Step 4: Write the dimensions, the copy and the shared pieces**

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupDimens.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** Layout values for the wizard and Settings (4a design §4), named once; those the spec doesn't give say so. */
internal object SetupDimens {
    // Wizard frame (§4.1): the step in a centred column 720 wide. Not in the spec: padding 36 top, 32 bottom, 48 at the
    // sides; dots 10 dp, 10 apart, 32 above the step; 24 above the buttons.
    val wizardColumn = 720.dp
    val wizardPaddingTop = 36.dp
    val wizardPaddingBottom = 32.dp
    val wizardPaddingH = 48.dp
    val dot = 10.dp
    val dotGap = 10.dp
    val dotsBottom = 32.dp
    val buttonsTop = 24.dp

    // A step or page (not in the spec): blocks 20 apart; a title's line 8 below it.
    val blockGap = 20.dp
    val titleLineGap = 8.dp

    // List rows, people and towns (not in the spec): `surf`, radius 18, padding 16×20, 10 apart; a 16 dp colour dot 16
    // from the text, whose second line is 2 below; a 24 dp icon at the end.
    val rowRadius = 18.dp
    val rowPaddingV = 16.dp
    val rowPaddingH = 20.dp
    val rowGap = 10.dp
    val rowDot = 16.dp
    val rowDotGap = 16.dp
    val rowLineGap = 2.dp
    val rowIcon = 24.dp

    // The person sheet (§4.4: HhSheet, 600 dp), padded as the calendar's sheets (28×30×26). Not in the spec: sections 20
    // apart, a label 10 above its controls; role chips 10 apart; footer buttons 10 apart.
    val sheetPaddingTop = 28.dp
    val sheetPaddingH = 30.dp
    val sheetPaddingBottom = 26.dp
    val sectionGap = 20.dp
    val labelGap = 10.dp
    val roleGap = 10.dp
    val footerGap = 10.dp

    // A confirmation, as the calendar's delete confirmation: `dangerSoft`, radius 24, padding 20, 16 between blocks,
    // buttons 12 apart.
    val confirmRadius = 24.dp
    val confirmPadding = 20.dp
    val confirmGap = 16.dp
    val confirmButtonGap = 12.dp

    // Settings (§4.6): the left column 320 wide. Not in the spec: `surf`, padding 32×28; the title 24 above the list;
    // items 56 dp, radius 16, padding 0 18, 4 apart; the page padded 40×48, at most the wizard's 720 wide.
    val settingsList = 320.dp
    val settingsListPaddingV = 32.dp
    val settingsListPaddingH = 28.dp
    val settingsTitleGap = 24.dp
    val settingsItemHeight = 56.dp
    val settingsItemRadius = 16.dp
    val settingsItemPaddingH = 18.dp
    val settingsItemGap = 4.dp
    val pagePaddingV = 40.dp
    val pagePaddingH = 48.dp
}

/** Setup and Settings text styles, derived from HhType. */
internal object SetupType {
    /** 34 sp / 700: a step's or page's title. */
    val title = HhType.screenTitle

    /** 16 sp / 400: a title's line, and body text. */
    val line = HhType.body

    /** 17 sp / 600: a row's name, a Settings item. */
    val rowTitle = HhType.rowTitle

    /** 14 sp / 400: a row's second line, a role's line. */
    val secondary = HhType.secondary

    /** 13 sp / 700: a sheet section's label. */
    val label = HhType.label.copy(fontWeight = FontWeight.W700)

    /** 30 sp / 700, −0.5 tracking: the person sheet's heading (the calendar editor's title). */
    val sheetTitle = HhType.screenTitle.copy(fontSize = 30.sp, letterSpacing = (-0.5).sp)

    /** 14 sp / 700: a refusal in `danger`. */
    val message = HhType.secondary.copy(fontWeight = FontWeight.W700)

    /** 18 sp / 700: a confirmation's question. */
    val confirm = HhType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.W700)
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupCopy.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import uk.co.siland.culvery.core.household.Role

// 4a design §4. Ruling 11 and 18 name the strings the spec doesn't give.

// Wizard frame (§4.1, §3.4)
internal const val BACK = "Back"
internal const val SKIP_FOR_NOW = "Skip for now"
internal const val ENTER_PIN = "Enter PIN"

// Welcome, You, Household, Done (§4.2)
internal const val START = "Start"
internal const val WELCOME = "Welcome to Culvery"
internal const val WELCOME_LINE = "Your home's location, the people who live here, and your calendars."
internal const val USE_SAMPLE_HOUSEHOLD = "Use a sample household"
internal const val WHOS_SETTING_UP = "Who's setting this up?"
internal const val SET_YOUR_PIN = "Set your PIN"
internal const val WHO_ELSE = "Who else lives here?"
internal const val CULVERY_IS_READY = "Culvery is ready"
internal const val OPEN_CULVERY = "Open Culvery"

// Home location (§4.3)
internal const val WHERES_HOME = "Where's home?"
internal const val TOWN_OR_CITY = "Town or city"
internal const val USED_FOR = "Used for the time zone, and for weather."
internal const val COULD_NOT_SEARCH = "Couldn't search for towns — check the tablet's Wi-Fi and try again."

internal fun noTownsMatch(query: String): String = "No towns match \"$query\"."

// People (§4.4)
internal const val ADD_PERSON = "Add person"
internal const val PIN_SET = "PIN set"
internal const val NO_PIN = "No PIN"
internal const val NAME = "Name"
internal const val COLOUR = "Colour"
internal const val ROLE = "Role"
internal const val PIN = "PIN"
internal const val SET_PIN = "Set PIN"
internal const val CHANGE_PIN = "Change PIN"
internal const val REMOVE_PIN = "Remove PIN"
internal const val SAVE_PERSON = "Save person"
internal const val SAVE_CHANGES = "Save changes"
internal const val REMOVE_PERSON = "Remove person"
internal const val KEEP = "Keep"
internal const val COLOUR_TAKEN = "That colour is taken."
internal const val PIN_TAKEN = "That PIN is taken — choose another."
internal const val ADMIN_NEEDS_PIN = "An Admin needs a PIN."
internal const val NEEDS_AN_ADMIN = "Culvery needs at least one Admin with a PIN."

internal fun someoneCalled(name: String): String = "Someone is already called $name."

internal fun removeQuestion(name: String): String = "Remove $name? $name's events and calendars show as Family."

internal fun removed(name: String): String = "$name removed"

internal fun roleName(role: Role): String = when (role) {
    Role.ADMIN -> "Admin"
    Role.ADULT -> "Adult"
    Role.CHILD -> "Child"
}

internal fun roleLine(role: Role): String = when (role) {
    Role.ADMIN -> "Admin — can change settings and people"
    Role.ADULT -> "Adult — can add and change any event"
    Role.CHILD -> "Child — can add their own events"
}

// Settings (§4.6, §4.7)
internal const val SETTINGS = "Settings"
internal const val CLOSE = "Close"
internal const val HOME_LOCATION = "Home location"
internal const val PEOPLE = "People"
internal const val KIOSK = "Kiosk"
internal const val EXIT_KIOSK = "Exit kiosk"
internal const val KIOSK_LINE = "Culvery keeps the tablet on this app. Exit to use other apps; it locks again next time Culvery opens."
```

```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import uk.co.siland.culvery.core.ui.Culvery

/** A step's or page's title, with an optional line under it in `mute`. */
@Composable
internal fun StepTitle(title: String, line: String? = null) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.titleLineGap)) {
        Text(title, style = SetupType.title, color = c.ink)
        if (line != null) Text(line, style = SetupType.line, color = c.mute)
    }
}
```

- [ ] **Step 5: Write the rules, the gate and the wizard**

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/WizardRules.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

/** A step as the wizard reads it at this moment. */
internal data class StepStatus(val shown: Boolean, val done: Boolean, val canGoOn: Boolean)

/** The forward button: the step's own label, enabled or not, or "Skip for now". */
internal sealed interface Forward {
    data class Next(val label: String, val enabled: Boolean) : Forward

    data object Skip : Forward
}

/** 4a design §3.3 as plain functions of the steps' statuses, in order. */
internal object WizardRules {
    /** The first shown step not done; with every shown step done, the last shown one. */
    fun resumeAt(statuses: List<StepStatus>): Int =
        statuses.indexOfFirst { it.shown && !it.done }.takeIf { it >= 0 } ?: statuses.indexOfLast { it.shown }.coerceAtLeast(0)

    fun nextShown(from: Int, statuses: List<StepStatus>): Int? = (from + 1 until statuses.size).firstOrNull { statuses[it].shown }

    fun previousShown(from: Int, statuses: List<StepStatus>): Int? = (from - 1 downTo 0).firstOrNull { statuses[it].shown }

    fun dotCount(statuses: List<StepStatus>): Int = statuses.count { it.shown }

    /** [current]'s place among the shown steps. */
    fun dotIndex(current: Int, statuses: List<StepStatus>): Int = statuses.take(current).count { it.shown }

    /** A skippable step that isn't done offers Skip for now; otherwise Next, enabled once the step can go on. */
    fun forward(skippable: Boolean, label: String, status: StepStatus): Forward =
        if (skippable && !status.done) Forward.Skip else Forward.Next(label, status.canGoOn || skippable)
}
```

```kotlin
package uk.co.siland.culvery.core.setup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HouseholdRepository

/**
 * 4a design §3.4, D9: the setup session lives in memory only and lapses after 10 idle minutes, so whenever the wizard
 * has an Admin but nobody signed in, it asks for an Admin's PIN, then begins the setup session again.
 */
@Singleton
class SetupSessionGate @Inject constructor(
    household: HouseholdRepository,
    private val access: AccessControl,
) {
    private val finishing = MutableStateFlow(false)

    /** An active Admin, nobody signed in, and Done not finishing (ruling 11). */
    val needsPin: Flow<Boolean> =
        combine(household.hasActiveAdmin, access.session, finishing) { admin, session, done -> admin && session == null && !done }
            .distinctUntilChanged()

    /** Done is ending setup: its sign-out must not bring the gate back. Set before it signs out. */
    fun finish() {
        finishing.value = true
    }

    /** The PIN pad; an Admin's PIN begins the setup session again. False when cancelled or refused. */
    suspend fun carryOn(): Boolean {
        val who = access.authorise(CorePermissions.SETTINGS_MANAGE, reason = PinReason.ContinueSetup) ?: return false
        access.beginSetupSession(Identified(who.person, who.role))
        return true
    }
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupWizard.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.combine
import uk.co.siland.culvery.core.access.CONTINUE_SETUP
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "SetupWizard"

/**
 * The first-run wizard (4a design §3.3, §4.1): the shown [steps], already in order, starting where
 * [WizardRules.resumeAt] says, with progress dots, Back, and Next or Skip for now. Whenever [gate] needs a PIN (an Admin
 * exists and nobody is signed in: a start after a kill, the setup session's idle limit, a lock), it asks before any step
 * shows, then the wizard carries on at the same step.
 */
@Composable
fun SetupWizard(steps: List<SetupStep>, gate: SetupSessionGate) {
    val statusFlow = remember(steps) {
        combine(steps.map { step -> combine(step.shown, step.done, step.canGoOn, ::StepStatus) }) { it.toList() }
    }
    val statuses = statusFlow.collectAsState(initial = null).value
    // Not saved: a wizard restored after its process died has lost the setup session and must ask again.
    val needsPin = remember(gate) { gate.needsPin }.collectAsState<Boolean?>(initial = null).value
    // Saved, and kept here rather than in Steps, so the gate coming and going doesn't lose the step.
    var current by rememberSaveable { mutableStateOf(-1) }
    Box(Modifier.fillMaxSize().background(Culvery.colors.bg).testTag("wizard")) {
        when {
            statuses == null || needsPin == null -> Unit
            needsPin -> PinGate(gate)
            else -> {
                // Where it resumes is read once, from the first statuses; after that only Next, Back and Skip move it.
                val at = current.takeIf { it >= 0 } ?: WizardRules.resumeAt(statuses)
                SideEffect { if (current < 0) current = at }
                Steps(steps, statuses, at) { current = it }
            }
        }
    }
}

@Composable
private fun Steps(steps: List<SetupStep>, statuses: List<StepStatus>, current: Int, onGoTo: (Int) -> Unit) {
    // A step hidden while it shows (Review, after Disconnect) gives way to the one before it.
    val index = if (statuses.getOrNull(current)?.shown == true) {
        current
    } else {
        WizardRules.previousShown(current, statuses) ?: WizardRules.nextShown(current, statuses) ?: 0
    }
    val step = steps[index]
    val latest by rememberUpdatedState(statuses)
    val goTo by rememberUpdatedState(onGoTo)
    val action = rememberSingleAction(step.id) { e -> Log.w(TAG, "${step.id}: Next failed (${e::class.simpleName})") }
    val goOn: () -> Unit = {
        action.run {
            if (step.onNext()) WizardRules.nextShown(index, latest)?.let { goTo(it) }
        }
    }
    WizardFrame(
        dotCount = WizardRules.dotCount(statuses),
        dotIndex = WizardRules.dotIndex(index, statuses),
        back = WizardRules.previousShown(index, statuses)?.let { previous -> { goTo(previous) } },
        forward = WizardRules.forward(step.skippable, step.nextLabel, statuses[index]),
        busy = action.busy,
        onForward = goOn,
        onSkip = { WizardRules.nextShown(index, latest)?.let { goTo(it) } },
    ) {
        step.Content(onNext = goOn)
    }
}

/** 4a design §4.1: dots at the top centre, the step in a 720 dp column, Back left and the forward button right. */
@Composable
internal fun WizardFrame(
    dotCount: Int,
    dotIndex: Int,
    back: (() -> Unit)?,
    forward: Forward,
    busy: Boolean,
    onForward: () -> Unit,
    onSkip: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = SetupDimens.wizardPaddingTop,
                bottom = SetupDimens.wizardPaddingBottom,
                start = SetupDimens.wizardPaddingH,
                end = SetupDimens.wizardPaddingH,
            ),
    ) {
        Dots(dotCount, dotIndex)
        Spacer(Modifier.height(SetupDimens.dotsBottom))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap),
                modifier = Modifier
                    .testTag("wizard_step")
                    .width(SetupDimens.wizardColumn)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        }
        Spacer(Modifier.height(SetupDimens.buttonsTop))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) HhPillButton(BACK, back, Modifier.testTag("wizard_back"), enabled = !busy)
            Spacer(Modifier.weight(1f))
            when (forward) {
                Forward.Skip -> HhPillButton(SKIP_FOR_NOW, onSkip, Modifier.testTag("wizard_skip"), enabled = !busy)
                is Forward.Next -> HhPillButton(
                    forward.label,
                    onForward,
                    Modifier.testTag("wizard_next"),
                    primary = true,
                    enabled = forward.enabled && !busy,
                )
            }
        }
    }
}

@Composable
private fun Dots(count: Int, index: Int) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SetupDimens.dotGap, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().testTag("wizard_dots"),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .testTag("wizard_dot")
                    .semantics { selected = i == index }
                    .size(SetupDimens.dot)
                    .clip(CircleShape)
                    .background(if (i == index) c.accent else c.surf3),
            )
        }
    }
}

/**
 * Opens the PIN pad at once; if it is cancelled, [CONTINUE_SETUP] and an Enter PIN pill to try again (ruling 11). The gate
 * goes when the setup session begins again.
 */
@Composable
private fun PinGate(gate: SetupSessionGate) {
    val action = rememberSingleAction(gate) { e -> Log.w(TAG, "Couldn't carry on setting up (${e::class.simpleName})") }
    val ask = { action.run { gate.carryOn() } }
    LaunchedEffect(gate) { ask() }
    PinGateContent(busy = action.busy, onEnterPin = ask)
}

@Composable
internal fun PinGateContent(busy: Boolean, onEnterPin: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().testTag("wizard_gate"),
    ) {
        Text(CONTINUE_SETUP, style = SetupType.line, color = Culvery.colors.mute)
        HhPillButton(ENTER_PIN, onEnterPin, Modifier.testTag("wizard_enter_pin"), primary = true, enabled = !busy)
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: PASS except `SetupScreenshotTest` under `verifyRoborazziDebug` (no baselines yet; `testDebugUnitTest` alone records nothing and passes).

- [ ] **Step 7: Record the screenshots and look at them**

Run: `./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest*"`
Open and check:
- `wizard_frame_{dark,light}`: six 10 dp dots centred at the top, the third in `accent`, the others `surf3`; "Who's setting this up?" at 34 sp in a 720 dp column centred on the canvas; **Back** (a `surf2` pill) bottom-left and a disabled **Next** (`surf2`, `mute` text) bottom-right;
- `wizard_pin_gate_dark`: "Enter your PIN to carry on setting up" in `mute` above an `accent` **Enter PIN** pill, both centred.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add core/setup
git commit -m "Add the setup wizard's frame: steps in order, resuming where setup stopped, and a PIN before carrying on after a restart"
```

---

### Task 8: People — the editor's rules, the list and the editor sheet (§3.5, §3.7, §4.4, D6, D12)

**Files:**
- Create: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/PeopleEditor.kt`, `PersonEditorSheet.kt`, `PeopleUi.kt`
- Test: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/RecordingOverlay.kt`, `PeopleEditorTest.kt`, `PeopleUiTest.kt` (create); `SetupScreenshotTest.kt` (modify)
- Screenshots (`core/setup`): `people_list_{dark,light}`, `person_new_{dark,light}`, `person_error_dark`, `person_remove_dark`, `person_pin_dark` (new)

**Interfaces:**
- Consumes: `Member`, `MAX_PEOPLE`, `PinChange`, `DuplicateNameException`, `ColourInUseException`, `HouseholdRepository.members`/`member`/`updateMember`/`removePerson`, `PinManager.addPerson`/`hashNew` (Task 3); `AccessControl.session`/`authorise`/`lock`, `ChoosePinPad` (Task 4); `PersonPalette`, `HhSwatch`, `HhChoiceChip`, `HhTextField`, `rememberSingleAction` (Task 2); `SetupDimens`, `SetupType`, `StepTitle`, the copy (Task 7); `HhSheetButton`, `ButtonTone` (Task 2); `COULD_NOT_SAVE` (Task 1).
- Produces:
  - `sealed interface PeopleOutcome { data object Done; data object Cancelled; data class Refused(val message: String) }`
  - `data class PersonDraft(val name: String, val color: Long, val role: Role, val newPin: String? = null, val removePin: Boolean = false)`
  - `@Singleton class PeopleEditor @Inject constructor(household: HouseholdRepository, pins: PinManager, access: AccessControl, toaster: Toaster)` with `val members: Flow<List<Member>>`, `suspend fun add(draft: PersonDraft): PeopleOutcome`, `suspend fun save(id: PersonId, draft: PersonDraft): PeopleOutcome`, `suspend fun remove(id: PersonId): PeopleOutcome`, `fun isSignedIn(id: PersonId): Boolean`
  - `@Stable internal class PersonForm(val existing: Member?, val taken: Set<Long>)` with `name`, `color`, `role`, `newPin`, `removePin`, `message`, `hasPin`, `canSave`, `choosePin(pin)`, `clearPin()`, `draft()`
  - `@Composable internal fun PersonEditorSheet(form, busy, canRemove, confirmingRemove, choosingPin, onChoosePin, onPinChosen, onPinCancelled, onSave, onRemove, onKeep, onConfirmRemove, onClose)`; `internal fun OverlayHost.showPersonEditor(editor: PeopleEditor, existing: Member?, taken: Set<Long>, lastAdmin: Boolean)`
  - `@Composable internal fun PeopleList(members: List<Member>, onEdit: (Member) -> Unit, onAdd: (() -> Unit)?)`; `@Composable internal fun PersonRow(member: Member, onClick: () -> Unit)`; `@Composable internal fun PeoplePane(editor: PeopleEditor)`
  - Test helper: `class RecordingOverlay : OverlayHost` (`core:setup` tests)

- [ ] **Step 1: Write the failing tests**

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/RecordingOverlay.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.co.siland.culvery.core.plugin.OverlayHost

class RecordingOverlay : OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)

    override fun show(content: @Composable () -> Unit) {
        this.content = content
    }

    override fun dismiss() {
        content = null
    }
}
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/PeopleEditorTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.ui.PersonPalette

// Robolectric for Room and android.util.Log.
@RunWith(AndroidJUnit4::class)
class PeopleEditorTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var access: TestAccess
    private lateinit var editor: PeopleEditor
    private lateinit var alex: Person

    @Before
    fun setUp() {
        ShadowLog.clear()
        db = householdDb()
        household = HouseholdRepository(db)
    }

    @After
    fun tearDown() = db.close()

    /** Alex, the Admin, has Settings open: signed in with PIN 1234. */
    private suspend fun TestScope.start() {
        access = testAccess(household)
        editor = PeopleEditor(household, access.pins, access.control, access.toasts)
        alex = access.addAdmin()
        access.answer("1234")
        access.control.authorise(CorePermissions.SETTINGS_MANAGE)
        access.requests.clear()
    }

    private fun draft(name: String, colour: Int, role: Role = Role.ADULT, pin: String? = null) =
        PersonDraft(name, PersonPalette.colors[colour], role, pin)

    private suspend fun sam(pin: String? = null, role: Role = Role.ADULT): Person =
        access.pins.addPerson("Sam", PersonPalette.colors[1], role, pin)

    @Test
    fun addingSomeoneAsksForAFreshPinAndAddsThem() = runTest {
        start()
        access.answer("1234")
        assertThat(editor.add(draft("Sam", 1))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.requests.map { it.label }).containsExactly("Manage people")
        assertThat(household.members.first().map { it.person.name }).containsExactly("Alex", "Sam").inOrder()
    }

    @Test
    fun renamingAndRecolouringNeedOnlyTheOpenSession() = runTest {
        start()
        val sam = sam()
        assertThat(editor.save(sam.id, draft("Samuel", 3))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.requests).isEmpty()
        assertThat(household.person(sam.id)).isEqualTo(Person(sam.id, "Samuel", PersonPalette.colors[3]))
    }

    @Test
    fun aRoleChangeOrAnyPinChangeAsksForAFreshPin() = runTest {
        start()
        val sam = sam()
        access.answer("1234", "1234", "1234")
        assertThat(editor.save(sam.id, draft("Sam", 1, Role.CHILD))).isEqualTo(PeopleOutcome.Done)
        assertThat(editor.save(sam.id, draft("Sam", 1, Role.CHILD, pin = "2468"))).isEqualTo(PeopleOutcome.Done)
        assertThat(household.member(sam.id)?.hasPin).isTrue()
        assertThat(editor.save(sam.id, draft("Sam", 1, Role.CHILD).copy(removePin = true))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.requests.map { it.label }).containsExactly("Manage people", "Manage people", "Manage people")
        assertThat(household.member(sam.id)).isEqualTo(Member(sam, Role.CHILD, hasPin = false))
    }

    @Test
    fun aNameInUseIsRefusedAndNothingChanges() = runTest {
        start()
        val sam = sam()
        assertThat(editor.save(sam.id, draft("ALEX", 1))).isEqualTo(PeopleOutcome.Refused("Someone is already called ALEX."))
        assertThat(household.person(sam.id)?.name).isEqualTo("Sam")
    }

    @Test
    fun aTakenColourIsRefused() = runTest {
        start()
        val sam = sam()
        assertThat(editor.save(sam.id, draft("Sam", 0))).isEqualTo(PeopleOutcome.Refused("That colour is taken."))
    }

    @Test
    fun aTakenPinIsRefusedAndNobodyIsAdded() = runTest {
        start()
        access.answer("1234")
        assertThat(editor.add(draft("Sam", 1, pin = "1234"))).isEqualTo(PeopleOutcome.Refused("That PIN is taken — choose another."))
        assertThat(household.members.first()).hasSize(1)
    }

    @Test
    fun anAdminWithoutAPinIsRefusedBeforeAnyPinPad() = runTest {
        start()
        assertThat(editor.add(draft("Sam", 1, Role.ADMIN))).isEqualTo(PeopleOutcome.Refused("An Admin needs a PIN."))
        val sam = sam(pin = "2468", role = Role.ADMIN)
        assertThat(editor.save(sam.id, draft("Sam", 1, Role.ADMIN).copy(removePin = true))).isEqualTo(PeopleOutcome.Refused("An Admin needs a PIN."))
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun theLastAdminCannotBeDemotedRemovedOrLoseTheirPin() = runTest {
        start()
        access.answer("1234", "1234")
        val refused = PeopleOutcome.Refused("Culvery needs at least one Admin with a PIN.")
        assertThat(editor.save(alex.id, draft("Alex", 0, Role.ADULT).copy(removePin = true))).isEqualTo(refused)
        assertThat(editor.remove(alex.id)).isEqualTo(refused)
        assertThat(household.member(alex.id)?.isActiveAdmin).isTrue()
    }

    @Test
    fun changingTheSignedInPersonsPinSignsThemOut() = runTest {
        start()
        sam(pin = "2468", role = Role.ADMIN)
        access.answer("1234")
        assertThat(editor.save(alex.id, draft("Alex", 0, Role.ADMIN, pin = "4321"))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun changingSomeoneElsesRoleKeepsYouSignedIn() = runTest {
        start()
        val sam = sam()
        access.answer("1234")
        editor.save(sam.id, draft("Sam", 1, Role.CHILD))
        assertThat(access.control.session.value?.person?.id).isEqualTo(alex.id)
    }

    @Test
    fun renamingTheSignedInPersonKeepsThemSignedIn() = runTest {
        start()
        assertThat(editor.save(alex.id, draft("Alexandra", 0, Role.ADMIN))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.control.session.value?.person?.id).isEqualTo(alex.id)
    }

    @Test
    fun removingSomeoneSaysSo() = runTest {
        start()
        val sam = sam()
        access.answer("1234")
        assertThat(editor.remove(sam.id)).isEqualTo(PeopleOutcome.Done)
        assertThat(access.toasts.messages).containsExactly("Sam removed")
        assertThat(household.members.first().map { it.person.name }).containsExactly("Alex")
    }

    @Test
    fun removingYourselfSignsYouOut() = runTest {
        start()
        sam(pin = "2468", role = Role.ADMIN)
        access.answer("1234")
        assertThat(editor.remove(alex.id)).isEqualTo(PeopleOutcome.Done)
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun aCancelledPinChangesNothing() = runTest {
        start()
        access.answer(null)
        assertThat(editor.add(draft("Sam", 1))).isEqualTo(PeopleOutcome.Cancelled)
        assertThat(household.members.first()).hasSize(1)
    }

    @Test
    fun aStoreFailureSaysCouldNotSaveAndLogsNoNameOrPin() = runTest {
        start()
        val sam = sam(pin = "2468")
        db.close()
        assertThat(editor.save(sam.id, draft("Samantha", 1))).isEqualTo(PeopleOutcome.Refused(COULD_NOT_SAVE))
        assertNoSecretsLogged("People", listOf("Sam", "2468", "1234"))
    }
}
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/PeopleUiTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.MAX_PEOPLE
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

/** The people list and sheet over the real editor; Alex is in the setup session, so no PIN pad shows. */
@RunWith(AndroidJUnit4::class)
class PeopleUiTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var access: TestAccess
    private lateinit var editor: PeopleEditor
    private val overlay = RecordingOverlay()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
        access = TestAccess(household, WallClock { System.currentTimeMillis() }, scope).also { it.listen(scope) }
        editor = PeopleEditor(household, access.pins, access.control, access.toasts)
        val alex = runBlocking { access.addAdmin() }
        access.control.beginSetupSession(Identified(alex, Role.ADMIN))
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** The list reads Room: wait for Alex's row before the first check or tap. */
    private fun show() {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box {
                        PeoplePane(editor)
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.awaitTag("person_Alex")
    }

    private fun addSam(pin: String? = null) = runBlocking { access.pins.addPerson("Sam", PersonPalette.colors[1], Role.ADULT, pin) }

    @Test
    fun theListShowsEachPersonsRoleAndWhetherTheyHaveAPin() {
        addSam()
        show()
        compose.awaitText("Adult · No PIN")
        compose.onNodeWithText("Admin · PIN set").assertExists()
        compose.onNodeWithText("Adult · No PIN").assertExists()
    }

    @Test
    fun aNewPersonStartsOnTheFirstFreeColourWithTakenOnesStruck() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("swatch_0").assertIsNotEnabled()
        compose.onNodeWithTag("swatch_1").assertIsEnabled().assertIsSelected()
    }

    @Test
    fun saveWaitsForAName() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("person_save").assertIsNotEnabled()
        compose.onNodeWithTag("person_name").performTextInput("Sam")
        compose.onNodeWithTag("person_save").assertIsEnabled()
    }

    @Test
    fun aRefusalShowsInTheSheetAndKeepsWhatWasTyped() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("person_name").performTextInput("alex")
        compose.onNodeWithTag("person_save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_message").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("person_message").assert(hasText("Someone is already called alex."))
        compose.onNodeWithTag("person_name").assert(hasText("alex"))
    }

    @Test
    fun savingANewPersonAddsThemAndClosesTheSheet() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("person_name").performTextInput("Sam")
        compose.onNodeWithTag("person_save").performClick()
        compose.waitUntil(5_000) { overlay.content == null }
        assertThat(runBlocking { household.members.first() }.map { it.person.name }).containsExactly("Alex", "Sam").inOrder()
    }

    @Test
    fun theLastAdminCannotRemoveThemself() {
        show()
        compose.onNodeWithTag("person_Alex").performClick()
        compose.onNodeWithTag("person_save").assertExists()
        compose.onNodeWithTag("person_remove").assertDoesNotExist()
    }

    @Test
    fun removingSomeoneAsksFirst() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("person_remove").performClick()
        compose.onNodeWithText("Remove Sam? Sam's events and calendars show as Family.").assertExists()
        compose.onNodeWithTag("person_keep").performClick()
        compose.onNodeWithTag("person_confirm_remove").assertDoesNotExist()
        compose.onNodeWithTag("person_remove").performClick()
        compose.onNodeWithTag("person_confirm_remove").performClick()
        compose.waitUntil(5_000) { overlay.content == null }
        assertThat(access.toasts.messages).containsExactly("Sam removed")
    }

    @Test
    fun aPinChosenInTheSheetCanBeChangedOrRemoved() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("person_set_pin").performClick()
        compose.onNodeWithText("Choose a 4-digit PIN").assertExists()
        repeat(2) { "2468".forEach { d -> compose.onNodeWithTag("pin_key_$d").performClick() } }
        compose.onNodeWithTag("person_change_pin").assertExists()
        compose.onNodeWithTag("person_remove_pin").performClick()
        compose.onNodeWithTag("person_set_pin").assertExists()
    }

    @Test
    fun addPersonGoesOnceEightPeopleLiveHere() {
        runBlocking { (1 until MAX_PEOPLE).forEach { i -> access.pins.addPerson("P$i", PersonPalette.colors[i], Role.ADULT, null) } }
        show()
        compose.awaitTag("person_P7")
        compose.onNodeWithTag("people_add").assertDoesNotExist()
    }

}
```

In `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SetupScreenshotTest.kt`:
1. Add the imports `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.ui.Alignment`, `uk.co.siland.culvery.core.household.Member`, `uk.co.siland.culvery.core.household.Person`, `uk.co.siland.culvery.core.household.PersonId`, `uk.co.siland.culvery.core.household.Role`, `uk.co.siland.culvery.core.ui.PersonPalette`, `uk.co.siland.culvery.core.ui.ShellTokens`.
2. Add inside the class:
```kotlin
    private val alex = Member(Person(PersonId("alex"), "Alex", PersonPalette.colors[0]), Role.ADMIN, hasPin = true)
    private val sam = Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), Role.ADULT, hasPin = false)
    private val mia = Member(Person(PersonId("mia"), "Mia", PersonPalette.colors[2]), Role.CHILD, hasPin = true)

    private fun people(name: String, dark: Boolean) = snap(name, dark) {
        WizardFrame(6, 3, back = {}, forward = Forward.Next("Next", enabled = true), busy = false, onForward = {}, onSkip = {}) {
            StepTitle("Who else lives here?")
            PeopleList(listOf(alex, sam, mia), onEdit = {}, onAdd = {})
        }
    }

    private fun sheet(
        name: String,
        dark: Boolean,
        form: PersonForm,
        confirmingRemove: Boolean = false,
        choosingPin: Boolean = false,
    ) = snap(name, dark) {
        Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
        Box(Modifier.align(Alignment.CenterEnd)) {
            PersonEditorSheet(
                form = form,
                busy = false,
                canRemove = form.existing != null,
                confirmingRemove = confirmingRemove,
                choosingPin = choosingPin,
                onChoosePin = {},
                onPinChosen = {},
                onPinCancelled = {},
                onSave = {},
                onRemove = {},
                onKeep = {},
                onConfirmRemove = {},
                onClose = {},
            )
        }
    }

    private fun newPerson() = PersonForm(existing = null, taken = setOf(PersonPalette.colors[0]))

    @Test fun peopleListDark() = people("people_list_dark", true)
    @Test fun peopleListLight() = people("people_list_light", false)
    @Test fun personNewDark() = sheet("person_new_dark", true, newPerson())
    @Test fun personNewLight() = sheet("person_new_light", false, newPerson())

    @Test
    fun personErrorDark() = sheet(
        "person_error_dark",
        true,
        PersonForm(existing = null, taken = setOf(PersonPalette.colors[0], PersonPalette.colors[1])).apply {
            name = "Sam"
            message = "Someone is already called Sam."
        },
    )

    @Test
    fun personRemoveDark() = sheet("person_remove_dark", true, PersonForm(sam, taken = setOf(PersonPalette.colors[0])), confirmingRemove = true)

    @Test
    fun personPinDark() = sheet("person_pin_dark", true, PersonForm(sam, taken = setOf(PersonPalette.colors[0])), choosingPin = true)
```
(`snap`'s content runs in the canvas `Box`'s scope, so `Modifier.align` works.)

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'PeopleEditor'", "'PersonDraft'", "'PeopleOutcome'", "'PeoplePane'", "'PersonEditorSheet'", "'PersonForm'", "'PeopleList'".

- [ ] **Step 3: Write the editor's rules**

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/PeopleEditor.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.PinInUseException
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.ColourInUseException
import uk.co.siland.culvery.core.household.DuplicateNameException
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.LastAdminException
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.PinChange
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.Toaster

/** What the editor sheet's Save or Remove came to. */
sealed interface PeopleOutcome {
    data object Done : PeopleOutcome

    /** The PIN pad was cancelled, or the person may not do this; nothing changed. */
    data object Cancelled : PeopleOutcome

    /** Nothing changed; the sheet shows [message] and keeps what was typed. */
    data class Refused(val message: String) : PeopleOutcome
}

/** A person as the sheet has them. [newPin] is a PIN chosen in the sheet; [removePin] is Remove PIN tapped. */
data class PersonDraft(
    val name: String,
    val color: Long,
    val role: Role,
    val newPin: String? = null,
    val removePin: Boolean = false,
)

/**
 * The people editor's rules (4a design §3.5, §3.7, §4.4, D6): renaming and recolouring take the open session
 * (`settings.manage`); adding, removing, a role change and any PIN change take a fresh PIN (`people.manage`, ruling 6).
 * An Admin always needs a PIN (ruling 8). Changing the signed-in person's role or PIN, or removing them, signs them out.
 */
@Singleton
class PeopleEditor @Inject constructor(
    private val household: HouseholdRepository,
    private val pins: PinManager,
    private val access: AccessControl,
    private val toaster: Toaster,
) {
    val members: Flow<List<Member>> = household.members

    suspend fun add(draft: PersonDraft): PeopleOutcome {
        if (draft.role == Role.ADMIN && draft.newPin == null) return PeopleOutcome.Refused(ADMIN_NEEDS_PIN)
        access.authorise(CorePermissions.PEOPLE_MANAGE) ?: return PeopleOutcome.Cancelled
        return saving("add a person") { pins.addPerson(draft.name, draft.color, draft.role, draft.newPin) }
    }

    suspend fun save(id: PersonId, draft: PersonDraft): PeopleOutcome {
        val before = orNull("read a person") { household.member(id) } ?: return PeopleOutcome.Refused(COULD_NOT_SAVE)
        val roleChanged = draft.role != before.role
        val pinChanged = draft.newPin != null || draft.removePin
        val keepsAPin = draft.newPin != null || (before.hasPin && !draft.removePin)
        if (draft.role == Role.ADMIN && !keepsAPin) return PeopleOutcome.Refused(ADMIN_NEEDS_PIN)
        val permission = if (roleChanged || pinChanged) CorePermissions.PEOPLE_MANAGE else CorePermissions.SETTINGS_MANAGE
        access.authorise(permission) ?: return PeopleOutcome.Cancelled
        val outcome = saving("save a person") {
            val pin = when {
                draft.newPin != null -> pins.hashNew(draft.newPin, owner = id).let { (hash, salt) -> PinChange.Set(hash, salt) }
                draft.removePin -> PinChange.Remove
                else -> PinChange.Keep
            }
            household.updateMember(id, draft.name, draft.color, draft.role, pin)
        }
        if (outcome == PeopleOutcome.Done && (roleChanged || pinChanged)) lockIfSignedIn(id)
        return outcome
    }

    suspend fun remove(id: PersonId): PeopleOutcome {
        val name = orNull("read a person") { household.person(id)?.name } ?: return PeopleOutcome.Refused(COULD_NOT_SAVE)
        access.authorise(CorePermissions.PEOPLE_MANAGE) ?: return PeopleOutcome.Cancelled
        val outcome = saving("remove a person") { household.removePerson(id) }
        if (outcome == PeopleOutcome.Done) {
            lockIfSignedIn(id)
            toaster.show(removed(name))
        }
        return outcome
    }

    /** Whether [id] is who is signed in now. */
    fun isSignedIn(id: PersonId): Boolean = access.session.value?.person?.id == id

    private fun lockIfSignedIn(id: PersonId) {
        if (isSignedIn(id)) access.lock()
    }

    private suspend fun saving(what: String, block: suspend () -> Unit): PeopleOutcome =
        try {
            block()
            PeopleOutcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: DuplicateNameException) {
            PeopleOutcome.Refused(someoneCalled(e.name))
        } catch (e: ColourInUseException) {
            PeopleOutcome.Refused(COLOUR_TAKEN)
        } catch (e: PinInUseException) {
            PeopleOutcome.Refused(PIN_TAKEN)
        } catch (e: LastAdminException) {
            PeopleOutcome.Refused(NEEDS_AN_ADMIN)
        } catch (e: Exception) {
            failed(what, e)
            PeopleOutcome.Refused(COULD_NOT_SAVE)
        }

    private suspend fun <T> orNull(what: String, block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(what, e)
            null
        }

    // The type only: a message could hold a name.
    private fun failed(what: String, e: Exception) = Log.w(TAG, "Couldn't $what (${e::class.simpleName})")

    private companion object {
        const val TAG = "People"
    }
}
```

- [ ] **Step 4: Write the sheet and the list**

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/PersonEditorSheet.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.core.access.ui.ChoosePinPad
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSheetButton
import uk.co.siland.culvery.core.ui.HhSwatch
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "PersonSheet"

/** The editor sheet's fields (4a design §4.4). [existing] is null for a new person; [taken] are other people's colours. */
@Stable
internal class PersonForm(val existing: Member?, val taken: Set<Long>) {
    var name by mutableStateOf(existing?.person?.name.orEmpty())
    var color by mutableStateOf(existing?.person?.color ?: PersonPalette.firstFree(taken))
    var role by mutableStateOf(existing?.role ?: Role.ADULT)
    var newPin by mutableStateOf<String?>(null)
    var removePin by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    val hasPin: Boolean get() = newPin != null || (existing?.hasPin == true && !removePin)
    val canSave: Boolean get() = name.isNotBlank() && color != null

    fun choosePin(pin: String) {
        newPin = pin
        removePin = false
        message = null
    }

    fun clearPin() {
        newPin = null
        removePin = existing?.hasPin == true
        message = null
    }

    fun draft(): PersonDraft = PersonDraft(name, checkNotNull(color), role, newPin, removePin)
}

/** Opens the editor for [existing], or for a new person when null. [lastAdmin]: [existing] is the only active Admin. */
internal fun OverlayHost.showPersonEditor(editor: PeopleEditor, existing: Member?, taken: Set<Long>, lastAdmin: Boolean): Unit = show {
    PersonEditorHost(editor, existing, taken, lastAdmin, onClose = { dismiss() })
}

@Composable
private fun PersonEditorHost(editor: PeopleEditor, existing: Member?, taken: Set<Long>, lastAdmin: Boolean, onClose: () -> Unit) {
    val form = remember(existing) { PersonForm(existing, taken) }
    var confirmingRemove by remember { mutableStateOf(false) }
    var choosingPin by remember { mutableStateOf(false) }
    val action = rememberSingleAction(existing) { e ->
        Log.w(TAG, "Couldn't save a person (${e::class.simpleName})")
        form.message = COULD_NOT_SAVE
    }
    val settle: (PeopleOutcome) -> Unit = { outcome ->
        when (outcome) {
            PeopleOutcome.Done -> onClose()
            PeopleOutcome.Cancelled -> Unit
            is PeopleOutcome.Refused -> {
                form.message = outcome.message
                confirmingRemove = false
            }
        }
    }
    PersonEditorSheet(
        form = form,
        busy = action.busy,
        // 4a design §4.4: not yourself while you're the last Admin.
        canRemove = existing != null && !(lastAdmin && editor.isSignedIn(existing.person.id)),
        confirmingRemove = confirmingRemove,
        choosingPin = choosingPin,
        onChoosePin = { choosingPin = true },
        onPinChosen = { pin ->
            form.choosePin(pin)
            choosingPin = false
        },
        onPinCancelled = { choosingPin = false },
        onSave = {
            action.run { settle(if (existing == null) editor.add(form.draft()) else editor.save(existing.person.id, form.draft())) }
        },
        onRemove = { confirmingRemove = true },
        onKeep = { confirmingRemove = false },
        onConfirmRemove = { action.run { existing?.let { settle(editor.remove(it.person.id)) } } },
        onClose = onClose,
    )
}

/**
 * 4a design §4.4: the right-hand sheet (HhSheet, 600 dp) with Name, Colour (others' colours struck through), Role and PIN;
 * Save person or Save changes; Remove person, which asks first. [choosingPin] shows the PIN pad over the sheet.
 */
@Composable
internal fun PersonEditorSheet(
    form: PersonForm,
    busy: Boolean,
    canRemove: Boolean,
    confirmingRemove: Boolean,
    choosingPin: Boolean,
    onChoosePin: () -> Unit,
    onPinChosen: (String) -> Unit,
    onPinCancelled: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
    onConfirmRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Culvery.colors
    val existing = form.existing
    Box(Modifier.fillMaxHeight().width(ShellTokens.sheetWidth)) {
        HhSheet(
            PaddingValues(
                top = SetupDimens.sheetPaddingTop,
                start = SetupDimens.sheetPaddingH,
                end = SetupDimens.sheetPaddingH,
                bottom = SetupDimens.sheetPaddingBottom,
            ),
            Modifier.testTag("person_sheet"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    existing?.person?.name ?: ADD_PERSON,
                    style = SetupType.sheetTitle,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                HhCloseButton(onClose)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(SetupDimens.sectionGap),
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                Section(NAME) {
                    HhTextField(
                        value = form.name,
                        onValueChange = {
                            form.name = it
                            form.message = null
                        },
                        placeholder = NAME,
                        tag = "person_name",
                        capitalization = KeyboardCapitalization.Words,
                    )
                }
                Section(COLOUR) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
                        verticalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
                    ) {
                        PersonPalette.colors.forEachIndexed { i, colour ->
                            HhSwatch(Color(colour), chosen = form.color == colour, taken = colour in form.taken, tag = "swatch_$i") {
                                form.color = colour
                                form.message = null
                            }
                        }
                    }
                }
                Section(ROLE) {
                    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.roleGap)) {
                        Role.entries.forEach { role ->
                            HhChoiceChip(roleLine(role), selected = form.role == role, tag = "role_${role.name}", onClick = {
                                form.role = role
                                form.message = null
                            })
                        }
                    }
                }
                Section(PIN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
                        if (form.hasPin) {
                            HhPillButton(CHANGE_PIN, onChoosePin, Modifier.testTag("person_change_pin"), enabled = !busy)
                            HhPillButton(REMOVE_PIN, form::clearPin, Modifier.testTag("person_remove_pin"), enabled = !busy)
                        } else {
                            HhPillButton(SET_PIN, onChoosePin, Modifier.testTag("person_set_pin"), enabled = !busy)
                        }
                    }
                }
            }
            form.message?.let { Text(it, style = SetupType.message, color = c.danger, modifier = Modifier.testTag("person_message")) }
            if (confirmingRemove && existing != null) {
                RemoveConfirmation(existing.person.name, busy, onKeep, onConfirmRemove)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(SetupDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
                    if (canRemove) HhSheetButton(REMOVE_PERSON, ButtonTone.Danger, enabled = !busy, tag = "person_remove", onClick = onRemove)
                    Spacer(Modifier.weight(1f))
                    HhSheetButton(
                        if (existing == null) SAVE_PERSON else SAVE_CHANGES,
                        ButtonTone.Primary,
                        enabled = form.canSave && !busy,
                        tag = "person_save",
                        onClick = onSave,
                    )
                }
            }
        }
        if (choosingPin) ChoosePinPad(onChosen = onPinChosen, onCancel = onPinCancelled, overSheet = true)
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.labelGap)) {
        Text(label, style = SetupType.label, color = Culvery.colors.mute)
        content()
    }
}

@Composable
private fun RemoveConfirmation(name: String, busy: Boolean, onKeep: () -> Unit, onConfirm: () -> Unit) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(SetupDimens.confirmGap),
        modifier = Modifier
            .testTag("person_confirm")
            .fillMaxWidth()
            .clip(RoundedCornerShape(SetupDimens.confirmRadius))
            .background(c.dangerSoft)
            .padding(SetupDimens.confirmPadding),
    ) {
        Text(removeQuestion(name), style = SetupType.confirm, color = c.ink)
        // Keep sits where Remove person was, so a double tap on Remove is harmless.
        Row(horizontalArrangement = Arrangement.spacedBy(SetupDimens.confirmButtonGap), modifier = Modifier.fillMaxWidth()) {
            HhSheetButton(KEEP, ButtonTone.Plain, enabled = !busy, tag = "person_keep", onClick = onKeep, modifier = Modifier.weight(1f))
            HhSheetButton(REMOVE_PERSON, ButtonTone.Destroy, enabled = !busy, tag = "person_confirm_remove", onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/PeopleUi.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.core.household.MAX_PEOPLE
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPillButton

/** 4a design §4.4: each person with their colour, name, role and whether they have a PIN; tap to edit. */
@Composable
internal fun PeopleList(members: List<Member>, onEdit: (Member) -> Unit, onAdd: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.rowGap), modifier = Modifier.testTag("people_list")) {
        members.forEach { member -> PersonRow(member) { onEdit(member) } }
        if (onAdd != null) HhPillButton(ADD_PERSON, onAdd, Modifier.testTag("people_add"), primary = true)
    }
}

@Composable
internal fun PersonRow(member: Member, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SetupDimens.rowDotGap),
        modifier = Modifier
            .testTag("person_${member.person.name}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(SetupDimens.rowRadius))
            .background(c.surf)
            .clickable(onClick = onClick)
            .padding(horizontal = SetupDimens.rowPaddingH, vertical = SetupDimens.rowPaddingV),
    ) {
        Box(Modifier.size(SetupDimens.rowDot).clip(CircleShape).background(Color(member.person.color)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SetupDimens.rowLineGap)) {
            Text(member.person.name, style = SetupType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${roleName(member.role)} · ${if (member.hasPin) PIN_SET else NO_PIN}", style = SetupType.secondary, color = c.mute, maxLines = 1)
        }
        HhIcon("chevron_right", size = SetupDimens.rowIcon, tint = c.mute)
    }
}

/** The people list and its editor sheet: the wizard's Household step and Settings › People. Add person goes at eight. */
@Composable
internal fun PeoplePane(editor: PeopleEditor) {
    val members by editor.members.collectAsState(initial = emptyList())
    val overlay = LocalOverlayHost.current
    val activeAdmins = members.count { it.isActiveAdmin }
    val open: (Member?) -> Unit = { existing ->
        overlay.showPersonEditor(
            editor,
            existing,
            taken = members.filter { it != existing }.mapTo(HashSet()) { it.person.color },
            lastAdmin = existing?.isActiveAdmin == true && activeAdmins == 1,
        )
    }
    PeopleList(members, onEdit = { open(it) }, onAdd = if (members.size < MAX_PEOPLE) ({ open(null) }) else null)
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: PASS (the new screenshots have no baselines yet, which `testDebugUnitTest` doesn't check).

- [ ] **Step 6: Record the screenshots and look at them**

Run: `./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest*"`
Open and check:
- `people_list_{dark,light}`: "Who else lives here?"; three `surf` rows: a green dot, "Alex", "Admin · PIN set"; blue "Sam", "Adult · No PIN"; pink "Mia", "Child · PIN set"; each with a `mute` chevron; an `accent` **Add person** pill below;
- `person_new_{dark,light}`: a 600 dp sheet on the right over the scrim; "Add person" with ✕; NAME, an empty field reading "Name"; COLOUR, eight swatches, the green struck through and faded, the blue ringed; ROLE, three chips one under the other, "Adult — can add and change any event" selected; PIN, **Set PIN**; bottom-right a disabled **Save person**;
- `person_error_dark`: "Sam" typed, green and blue struck, pink ringed, "Someone is already called Sam." in `danger` above an enabled **Save person**;
- `person_remove_dark`: heading "Sam", **Change PIN** absent (Sam has none), and in place of the footer a `dangerSoft` card "Remove Sam? Sam's events and calendars show as Family." with **Keep** and a `danger` **Remove person**;
- `person_pin_dark`: the PIN pad centred over the sheet (not the whole screen), titled "Choose a 4-digit PIN", with no reason line.
- `git status` shows no other changed baselines.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add core/setup
git commit -m "Add the people list and editor: names, colours, roles and PINs, with a fresh PIN for the sensitive changes"
```

---

### Task 9: The core wizard steps — Welcome, Home location, You, Household, Done (§3.3, §3.4, §4.2, §4.3, D2, D8, D9, D11)

**Files:**
- Create: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SampleHousehold.kt`, `LocationPane.kt`
- Create: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/steps/WelcomeStep.kt`, `LocationStep.kt`, `YouStep.kt`, `HouseholdStep.kt`, `DoneStep.kt`
- Create: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/di/SetupModule.kt`
- Modify: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestHousehold.kt`
- Test: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/StepsTest.kt`, `StepsUiTest.kt`, `LocationPaneTest.kt` (create); `SetupScreenshotTest.kt` (modify)
- Screenshots (`core/setup`): `welcome_{dark,light}`, `welcome_sample_dark`, `location_results_{dark,light}`, `location_failed_dark`, `you_{dark,light}`, `done_{dark,light}` (new)

**Interfaces:**
- Consumes: `SetupState` (Task 1); `LocationSearch`, `PlaceMatch`, `LocationSearchException` (Task 5); `HouseholdRepository.members`/`hasActiveAdmin`/`location`/`setLocation`, `PinManager.addPerson`, `DuplicateNameException` (Task 3); `AccessControl.beginSetupSession`/`endSetupSession`/`lock`, `ChoosePinPad` (Task 4); `PeopleEditor`, `PeoplePane`, `PersonRow`, `showPersonEditor` (Task 8); `StepTitle`, `WizardFrame`, `Forward`, `SetupSessionGate.finish()`, the copy, `TouchModeRule`, `awaitText`/`awaitTag` (Task 7).
- Produces:
  - `fun interface SampleHousehold { suspend fun create() }` (bound only in debug, Task 13)
  - `internal const val MIN_QUERY = 2`, `SEARCH_PAUSE_MS = 400L`; `internal sealed interface TownResults { Idle; Found(places); NoMatch(query); Failed }`; `internal fun PlaceMatch.toHome(): HomeLocation`; `@Composable internal fun LocationPane(search: LocationSearch, current: HomeLocation?, showCurrent: Boolean, save: suspend (PlaceMatch) -> Boolean)`; `@Composable internal fun LocationContent(query, onQuery, results, current, showCurrent, busy, onChoose)`
  - `@Singleton class WelcomeStep @Inject constructor(state: SetupState, household: HouseholdRepository, sample: Optional<SampleHousehold>)` — id "welcome", order 0, `nextLabel` "Start", done = welcomed
  - `@Singleton class LocationStep @Inject constructor(household, search: LocationSearch, access: AccessControl)` — "location", 100, skippable; `internal suspend fun saveHome(place: PlaceMatch): Boolean`
  - `@Singleton class YouStep @Inject constructor(household, pins: PinManager, access, editor: PeopleEditor)` — "you", 200; `internal val form: YouForm`; `onNext` makes the Admin and begins the setup session
  - `@Singleton class HouseholdStep @Inject constructor(editor: PeopleEditor)` — "household", 300, skippable, done with two or more people
  - `@Singleton class DoneStep @Inject constructor(state: SetupState, access: AccessControl, gate: SetupSessionGate)` — "done", 1000, `nextLabel` "Open Culvery"; `onNext` takes `settings.manage` (silent in the setup session), tells the gate it is finishing, ends the setup session, locks, then marks setup complete
  - `SetupModule` (`:core:setup` `di`): the five steps `@IntoSet`; `@Multibinds Set<SettingsPage>`; `@BindsOptionalOf SampleHousehold`
  - Test helper: `internal class SetupStates(folder: TemporaryFolder, household)` with `suspend fun start(): SetupState` and `fun close()`

- [ ] **Step 1: Write the failing tests**

In `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestHousehold.kt`, add the imports `java.io.File`, `kotlinx.coroutines.cancelAndJoin`, `kotlinx.coroutines.runBlocking`, `org.junit.rules.TemporaryFolder`, and at the end:
```kotlin

/** SetupState over one file; each [start] is the app starting again, with a new DataStore once the last has let go. */
internal class SetupStates(private val folder: TemporaryFolder, private val household: HouseholdRepository) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun start(): SetupState {
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return SetupState(setupStore(scope) { File(folder.root, "setup.preferences_pb") }, household)
    }

    fun close() = runBlocking { scope.coroutineContext.job.cancelAndJoin() }
}
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/StepsTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.setup.steps.DoneStep
import uk.co.siland.culvery.core.setup.steps.HouseholdStep
import uk.co.siland.culvery.core.setup.steps.LocationStep
import uk.co.siland.culvery.core.setup.steps.WelcomeStep
import uk.co.siland.culvery.core.setup.steps.YouStep
import uk.co.siland.culvery.core.ui.PersonPalette

/** A search nobody should reach: these tests save towns directly. */
private object NoSearch : LocationSearch {
    override suspend fun search(query: String): List<PlaceMatch> = error("not searched in these tests")
}

// Robolectric for Room and DataStore's files.
@RunWith(AndroidJUnit4::class)
class StepsTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var states: SetupStates
    private val brighton = PlaceMatch("Brighton", "England", "United Kingdom", 50.82838, -0.13947, "Europe/London")

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
        states = SetupStates(folder, household)
    }

    @After
    fun tearDown() {
        states.close()
        db.close()
    }

    @Test
    fun welcomeIsDoneOnceStartedAndStaysDoneAfterAKill() = runTest {
        val welcome = WelcomeStep(states.start(), household, Optional.empty())
        assertThat(welcome.nextLabel).isEqualTo("Start")
        assertThat(welcome.canGoOn.first()).isTrue()
        assertThat(welcome.done.first()).isFalse()
        assertThat(welcome.onNext()).isTrue()
        assertThat(WelcomeStep(states.start(), household, Optional.empty()).done.first()).isTrue()
    }

    @Test
    fun beforeAnyAdminAPlaceIsSavedWithoutAPin() = runTest {
        val access = testAccess(household)
        val step = LocationStep(household, NoSearch, access.control)
        assertThat(step.done.first()).isFalse()
        assertThat(step.saveHome(brighton)).isTrue()
        assertThat(household.location.first()).isEqualTo(brighton.toHome())
        assertThat(household.location.first()?.name).isEqualTo("Brighton, England, United Kingdom")
        assertThat(access.requests).isEmpty()
        assertThat(step.done.first()).isTrue()
    }

    @Test
    fun onceAnAdminExistsSavingAPlaceAsksForTheirPin() = runTest {
        val access = testAccess(household)
        access.addAdmin()
        val step = LocationStep(household, NoSearch, access.control)
        access.answer(null)
        assertThat(step.saveHome(brighton)).isFalse()
        assertThat(household.location.first()).isNull()
        access.answer("1234")
        assertThat(step.saveHome(brighton)).isTrue()
        assertThat(access.requests).hasSize(2)
    }

    private fun youStep(access: TestAccess) =
        YouStep(household, access.pins, access.control, PeopleEditor(household, access.pins, access.control, access.toasts))

    @Test
    fun youCanGoOnOnceThereIsANameAndAPin() = runTest {
        val you = youStep(testAccess(household))
        assertThat(you.canGoOn.first()).isFalse()
        you.form.name = "Alex"
        assertThat(you.canGoOn.first()).isFalse()
        you.form.pin = "1234"
        assertThat(you.canGoOn.first()).isTrue()
    }

    @Test
    fun nextMakesOneAdminWithTheirPinAndSignsThemInForSetup() = runTest {
        val access = testAccess(household)
        val you = youStep(access)
        you.form.name = "Alex"
        you.form.color = PersonPalette.colors[3]
        you.form.pin = "1234"
        assertThat(you.onNext()).isTrue()
        val admin = household.members.first().single()
        assertThat(listOf(admin.person.name, admin.person.color, admin.role, admin.hasPin))
            .containsExactly("Alex", PersonPalette.colors[3], Role.ADMIN, true).inOrder()
        assertThat(access.pins.identify("1234")?.person?.id).isEqualTo(admin.person.id)
        assertThat(access.control.authorise(CorePermissions.PEOPLE_MANAGE)).isNotNull()
        assertThat(access.requests).isEmpty()
        assertThat(you.done.first()).isTrue()
        // The PIN isn't held once the Admin has it.
        assertThat(you.form.pin).isNull()
    }

    @Test
    fun aSecondNextAfterTheAdminExistsAddsNobody() = runTest {
        val you = youStep(testAccess(household))
        you.form.name = "Alex"
        you.form.pin = "1234"
        you.onNext()
        assertThat(you.onNext()).isTrue()
        assertThat(household.members.first()).hasSize(1)
    }

    @Test
    fun aKillAfterYouResumesPastIt() = runTest {
        val you = youStep(testAccess(household))
        you.form.name = "Alex"
        you.form.pin = "1234"
        you.onNext()
        // The app starts again: a new step over the same household.
        assertThat(youStep(testAccess(household)).done.first()).isTrue()
    }

    @Test
    fun nobodyCanBeCalledFamilyEvenFirst() = runTest {
        val you = youStep(testAccess(household))
        you.form.name = "Family"
        you.form.pin = "1234"
        assertThat(you.onNext()).isFalse()
        assertThat(you.form.message).isEqualTo("Someone is already called Family.")
        assertThat(household.members.first()).isEmpty()
    }

    @Test
    fun theHouseholdStepIsDoneOnceSomeoneElseLivesHere() = runTest {
        val access = testAccess(household)
        access.addAdmin()
        val step = HouseholdStep(PeopleEditor(household, access.pins, access.control, access.toasts))
        assertThat(step.skippable).isTrue()
        assertThat(step.done.first()).isFalse()
        access.pins.addPerson("Sam", PersonPalette.colors[1], Role.ADULT, null)
        assertThat(step.done.first()).isTrue()
    }

    @Test
    fun openCulveryEndsTheSetupSessionBeforeSetupIsComplete() = runTest {
        val access = testAccess(household)
        val alex = access.addAdmin()
        access.control.beginSetupSession(Identified(alex, Role.ADMIN))
        val state = states.start()
        assertThat(state.setupComplete.first()).isFalse()
        val done = DoneStep(state, access.control, SetupSessionGate(household, access.control))
        assertThat(done.nextLabel).isEqualTo("Open Culvery")
        assertThat(done.done.first()).isFalse()
        assertThat(done.onNext()).isTrue()
        assertThat(access.control.session.value).isNull()
        assertThat(state.setupComplete.first()).isTrue()
        access.answer(null)
        assertThat(access.control.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(access.requests).hasSize(1)
    }

    @Test
    fun withNobodySignedInOpenCulveryAsksForAnAdmin() = runTest {
        val access = testAccess(household)
        access.addAdmin()
        val state = states.start()
        val done = DoneStep(state, access.control, SetupSessionGate(household, access.control))
        access.answer(null)
        assertThat(done.onNext()).isFalse()
        assertThat(state.setupComplete.first()).isFalse()
        access.answer("1234")
        assertThat(done.onNext()).isTrue()
        assertThat(state.setupComplete.first()).isTrue()
    }

}
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/StepsUiTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.setup.steps.DoneStep
import uk.co.siland.culvery.core.setup.steps.WelcomeStep
import uk.co.siland.culvery.core.setup.steps.YouStep
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette
import kotlinx.coroutines.flow.first

@RunWith(AndroidJUnit4::class)
class StepsUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var states: SetupStates
    private lateinit var access: TestAccess
    private val overlay = RecordingOverlay()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var samples = 0

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
        states = SetupStates(folder, household)
        access = TestAccess(household, WallClock { System.currentTimeMillis() }, scope).also { it.listen(scope) }
    }

    @After
    fun tearDown() {
        scope.cancel()
        states.close()
        db.close()
    }

    private fun welcome(sample: Boolean) {
        val step = WelcomeStep(runBlocking { states.start() }, household, if (sample) Optional.of(SampleHousehold { samples++ }) else Optional.empty())
        compose.setContent { CulveryTheme(dark = true) { Column { step.Content(onNext = {}) } } }
    }

    @Test
    fun aDebugBuildOffersTheSampleHouseholdToAnEmptyHousehold() {
        welcome(sample = true)
        // Offered only once the household is read as empty.
        compose.awaitText("Use a sample household")
        compose.onNodeWithText("Use a sample household").performClick()
        compose.waitUntil(5_000) { samples == 1 }
    }

    @Test
    fun aReleaseBuildNeverOffersIt() {
        welcome(sample = false)
        compose.awaitText("Welcome to Culvery")
        compose.onNodeWithTag("welcome_sample").assertDoesNotExist()
    }

    @Test
    fun itIsNotOfferedOnceSomeoneLivesHere() {
        runBlocking { access.pins.addPerson("Sam", PersonPalette.colors[1], Role.ADULT, null) }
        welcome(sample = true)
        compose.awaitText("Welcome to Culvery")
        compose.onNodeWithTag("welcome_sample").assertDoesNotExist()
    }

    @Test
    fun setYourPinChoosesItTwiceOverThePad() {
        val you = YouStep(household, access.pins, access.control, PeopleEditor(household, access.pins, access.control, access.toasts))
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box {
                        Column { you.Content(onNext = {}) }
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.awaitTag("you_set_pin")
        compose.onNodeWithTag("you_set_pin").performClick()
        repeat(2) { "1357".forEach { d -> compose.onNodeWithTag("pin_key_$d").performClick() } }
        compose.waitUntil(5_000) { overlay.content == null }
        compose.onNodeWithTag("you_pin_set").assertExists()
        assertThat(you.form.pin).isEqualTo("1357")
    }

    @Test
    fun openCulveryNeverShowsTheGate() {
        val alex = runBlocking { access.addAdmin() }
        access.control.beginSetupSession(Identified(alex, Role.ADMIN))
        val state = runBlocking { states.start() }
        val gate = SetupSessionGate(household, access.control)
        val done = DoneStep(state, access.control, gate)
        compose.setContent { CulveryTheme(dark = true) { SetupWizard(listOf(done), gate) } }
        compose.awaitText("Culvery is ready")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.waitUntil(5_000) { runBlocking { state.setupComplete.first() } }
        compose.waitForIdle()
        // Signed out, and no PIN pad or gate on the way out.
        assertThat(access.control.session.value).isNull()
        assertThat(access.requests).isEmpty()
        compose.onNodeWithTag("wizard_gate").assertDoesNotExist()
    }
}
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/LocationPaneTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class LocationPaneTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val brighton = PlaceMatch("Brighton", "England", "United Kingdom", 50.82838, -0.13947, "Europe/London")
    private val saved = CopyOnWriteArrayList<PlaceMatch>()

    private class FakeSearch : LocationSearch {
        val queries: MutableList<String> = CopyOnWriteArrayList()
        var cancelled = 0
        var answer: suspend (String) -> List<PlaceMatch> = { emptyList() }

        override suspend fun search(query: String): List<PlaceMatch> {
            queries += query
            try {
                return answer(query)
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
        }
    }

    private val search = FakeSearch()

    private fun show(current: HomeLocation? = null, showCurrent: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                LocationPane(search, current, showCurrent) { place ->
                    saved += place
                    true
                }
            }
        }
    }

    private fun type(text: String) {
        compose.onNodeWithTag("location_query").performTextInput(text)
    }

    private fun wait(millis: Long) = compose.mainClock.advanceTimeBy(millis)

    @Test
    fun itSearchesOnlyAfterTwoLettersAndAPause() {
        show()
        type("C")
        wait(1_000)
        assertThat(search.queries).isEmpty()
        type("a")
        wait(SEARCH_PAUSE_MS - 50)
        assertThat(search.queries).isEmpty()
        wait(100)
        assertThat(search.queries).containsExactly("Ca")
    }

    @Test
    fun aNewQueryCancelsTheSearchBeforeIt() {
        search.answer = { q -> if (q == "Ca") awaitCancellation() else listOf(brighton) }
        show()
        type("Ca")
        wait(500)
        type("n")
        wait(500)
        assertThat(search.queries).containsExactly("Ca", "Can").inOrder()
        assertThat(search.cancelled).isEqualTo(1)
        compose.onNodeWithTag("place_0").assertTextContains("Brighton, England, United Kingdom")
    }

    @Test
    fun choosingATownSavesIt() {
        search.answer = { listOf(brighton) }
        show()
        type("Can")
        wait(500)
        compose.onNodeWithTag("place_0").assertIsNotSelected().performClick()
        wait(100)
        assertThat(saved).containsExactly(brighton)
    }

    @Test
    fun theSavedHomeIsTicked() {
        search.answer = { listOf(brighton) }
        show(current = brighton.toHome())
        type("Can")
        wait(500)
        compose.onNodeWithTag("place_0").assertIsSelected()
    }

    @Test
    fun aFailedSearchSaysToCheckTheWiFi() {
        search.answer = { throw LocationSearchException("offline") }
        show()
        type("Can")
        wait(500)
        compose.onNodeWithText("Couldn't search for towns — check the tablet's Wi-Fi and try again.").assertExists()
    }

    @Test
    fun noMatchNamesTheQuery() {
        show()
        type("Xqz")
        wait(500)
        compose.onNodeWithText("No towns match \"Xqz\".").assertExists()
    }

    @Test
    fun settingsShowsTheSavedHomeAboveTheField() {
        show(current = brighton.toHome(), showCurrent = true)
        wait(100)
        compose.onNodeWithTag("location_current").assertTextContains("Brighton, England, United Kingdom")
    }
}
```

In `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SetupScreenshotTest.kt`, add the import `uk.co.siland.culvery.core.setup.steps.YouForm` and `uk.co.siland.culvery.core.setup.steps.WelcomeContent`, and inside the class:
```kotlin
    private fun step(name: String, dark: Boolean, dot: Int, forward: Forward, back: Boolean = true, content: @Composable () -> Unit) =
        snap(name, dark) {
            WizardFrame(7, dot, back = if (back) ({}) else null, forward = forward, busy = false, onForward = {}, onSkip = {}, content = content)
        }

    private val brighton = PlaceMatch("Brighton", "England", "United Kingdom", 50.82838, -0.13947, "Europe/London")
    private val brightonNz = PlaceMatch("Brighton", "Brighton", "New Zealand", -45.95, 170.33, "Pacific/Auckland")

    private fun welcome(name: String, dark: Boolean, sample: Boolean) =
        step(name, dark, 0, Forward.Next("Start", enabled = true), back = false) {
            WelcomeContent(onSample = if (sample) ({}) else null, busy = false)
        }

    private fun location(name: String, dark: Boolean, results: TownResults) = step(name, dark, 1, Forward.Skip) {
        StepTitle("Where's home?")
        LocationContent("Brigh", {}, results, current = brighton.toHome(), showCurrent = false, busy = false, onChoose = {})
    }

    private fun you(name: String, dark: Boolean) = step(name, dark, 2, Forward.Next("Next", enabled = true)) {
        StepTitle("Who's setting this up?")
        YouFormContent(YouForm().apply { this.name = "Alex"; pin = "1234" }, onSetPin = {})
    }

    private fun done(name: String, dark: Boolean) = step(name, dark, 6, Forward.Next("Open Culvery", enabled = true)) {
        StepTitle("Culvery is ready")
    }

    @Test fun welcomeDark() = welcome("welcome_dark", true, sample = false)
    @Test fun welcomeLight() = welcome("welcome_light", false, sample = false)
    @Test fun welcomeSampleDark() = welcome("welcome_sample_dark", true, sample = true)
    @Test fun locationResultsDark() = location("location_results_dark", true, TownResults.Found(listOf(brighton, brightonNz)))
    @Test fun locationResultsLight() = location("location_results_light", false, TownResults.Found(listOf(brighton, brightonNz)))
    @Test fun locationFailedDark() = location("location_failed_dark", true, TownResults.Failed)
    @Test fun youDark() = you("you_dark", true)
    @Test fun youLight() = you("you_light", false)
    @Test fun doneDark() = done("done_dark", true)
    @Test fun doneLight() = done("done_light", false)
```
(`YouFormContent` lives in `steps/YouStep.kt`; import `uk.co.siland.culvery.core.setup.steps.YouFormContent` too.)

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'WelcomeStep'", "'LocationStep'", "'YouStep'", "'HouseholdStep'", "'DoneStep'", "'SampleHousehold'", "'LocationPane'", "'SEARCH_PAUSE_MS'", "'TownResults'".

- [ ] **Step 3: Write the town search pane and the sample hook**

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SampleHousehold.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

/**
 * Debug builds' **Use a sample household** on Welcome (4a design D11): makes the sample household and marks setup
 * complete. Bound only in `app/src/debug`, so a release build's Welcome doesn't offer it.
 */
fun interface SampleHousehold {
    suspend fun create()
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/LocationPane.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.rememberSingleAction

/** 4a design §3.8: search once the query has two letters and typing has paused for 400 ms. */
internal const val MIN_QUERY = 2
internal const val SEARCH_PAUSE_MS = 400L

private const val TAG = "LocationPane"

internal sealed interface TownResults {
    data object Idle : TownResults

    data class Found(val places: List<PlaceMatch>) : TownResults

    data class NoMatch(val query: String) : TownResults

    data object Failed : TownResults
}

/** Stored with the result's whole line as its name (ruling 17). */
internal fun PlaceMatch.toHome(): HomeLocation = HomeLocation(label, latitude, longitude, timeZoneId)

private fun HomeLocation?.isAt(place: PlaceMatch): Boolean =
    this != null && name == place.label && latitude == place.latitude && longitude == place.longitude

/**
 * 4a design §4.3: the town search and its results, the saved home ticked. A new query cancels the search before it, and
 * with it the request. [showCurrent] (Settings) lists the saved home above the field. [save] stores the chosen town and
 * says whether it did.
 */
@Composable
internal fun LocationPane(search: LocationSearch, current: HomeLocation?, showCurrent: Boolean, save: suspend (PlaceMatch) -> Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by produceState<TownResults>(TownResults.Idle, query) {
        val q = query.trim()
        if (q.length < MIN_QUERY) {
            value = TownResults.Idle
            return@produceState
        }
        delay(SEARCH_PAUSE_MS)
        value = try {
            search.search(q).let { if (it.isEmpty()) TownResults.NoMatch(q) else TownResults.Found(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocationSearchException) {
            TownResults.Failed
        } catch (e: Exception) {
            Log.w(TAG, "Town search failed (${e::class.simpleName})")
            TownResults.Failed
        }
    }
    val action = rememberSingleAction(Unit) { e -> Log.w(TAG, "Couldn't save the home (${e::class.simpleName})") }
    LocationContent(query, { query = it }, results, current, showCurrent, action.busy) { place -> action.run { save(place) } }
}

@Composable
internal fun LocationContent(
    query: String,
    onQuery: (String) -> Unit,
    results: TownResults,
    current: HomeLocation?,
    showCurrent: Boolean,
    busy: Boolean,
    onChoose: (PlaceMatch) -> Unit,
) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap)) {
        if (showCurrent && current != null) PlaceRow(current.name, ticked = true, tag = "location_current", onClick = null)
        HhTextField(query, onQuery, TOWN_OR_CITY, "location_query", capitalization = KeyboardCapitalization.Words)
        when (results) {
            is TownResults.Found -> Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.rowGap)) {
                results.places.forEachIndexed { i, place ->
                    PlaceRow(place.label, ticked = current.isAt(place), tag = "place_$i", onClick = if (busy) null else ({ onChoose(place) }))
                }
            }
            is TownResults.NoMatch ->
                Text(noTownsMatch(results.query), style = SetupType.line, color = c.mute, modifier = Modifier.testTag("location_none"))
            TownResults.Failed ->
                Text(COULD_NOT_SEARCH, style = SetupType.line, color = c.danger, modifier = Modifier.testTag("location_failed"))
            TownResults.Idle -> Unit
        }
        Text(USED_FOR, style = SetupType.secondary, color = c.mute)
    }
}

@Composable
private fun PlaceRow(label: String, ticked: Boolean, tag: String, onClick: (() -> Unit)?) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SetupDimens.rowDotGap),
        modifier = Modifier
            .testTag(tag)
            .semantics { selected = ticked }
            .fillMaxWidth()
            .clip(RoundedCornerShape(SetupDimens.rowRadius))
            .background(c.surf)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = SetupDimens.rowPaddingH, vertical = SetupDimens.rowPaddingV),
    ) {
        Text(label, style = SetupType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (ticked) HhIcon("check", size = SetupDimens.rowIcon, tint = c.accent)
    }
}
```

- [ ] **Step 4: Write the steps and their bindings**

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/steps/WelcomeStep.kt`:
```kotlin
package uk.co.siland.culvery.core.setup.steps

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.SampleHousehold
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.START
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.USE_SAMPLE_HOUSEHOLD
import uk.co.siland.culvery.core.setup.WELCOME
import uk.co.siland.culvery.core.setup.WELCOME_LINE
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.rememberSingleAction

/** 4a design §4.2: what setup covers, and Start. Done once passed, so a start after a kill resumes past it (§6). */
@Singleton
class WelcomeStep @Inject constructor(
    private val state: SetupState,
    private val household: HouseholdRepository,
    private val sample: Optional<SampleHousehold>,
) : SetupStep {
    override val id = "welcome"
    override val order = 0
    override val done: Flow<Boolean> = state.welcomed
    override val canGoOn: Flow<Boolean> = flowOf(true)
    override val nextLabel = START

    override suspend fun onNext(): Boolean {
        state.markWelcomed()
        return true
    }

    @Composable
    override fun Content(onNext: () -> Unit) {
        val nobodyYet by remember { household.members.map { it.isEmpty() } }.collectAsState(initial = false)
        val action = rememberSingleAction(Unit) { e -> Log.w(TAG, "Couldn't make the sample household (${e::class.simpleName})") }
        WelcomeContent(
            onSample = if (sample.isPresent && nobodyYet) ({ action.run { sample.get().create() } }) else null,
            busy = action.busy,
        )
    }

    private companion object {
        const val TAG = "WelcomeStep"
    }
}

/** [onSample] is null except in a debug build with nobody set up yet (4a design D11). */
@Composable
internal fun WelcomeContent(onSample: (() -> Unit)?, busy: Boolean) {
    StepTitle(WELCOME, WELCOME_LINE)
    if (onSample != null) HhPillButton(USE_SAMPLE_HOUSEHOLD, onSample, Modifier.testTag("welcome_sample"), enabled = !busy)
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/steps/LocationStep.kt`:
```kotlin
package uk.co.siland.culvery.core.setup.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.LocationPane
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.core.setup.PlaceMatch
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.WHERES_HOME
import uk.co.siland.culvery.core.setup.toHome

/** 4a design §4.3, D2: the home town, or Skip for now (the tablet's own time zone). */
@Singleton
class LocationStep @Inject constructor(
    private val household: HouseholdRepository,
    private val search: LocationSearch,
    private val access: AccessControl,
) : SetupStep {
    override val id = "location"
    override val order = 100
    override val skippable = true
    override val done: Flow<Boolean> = household.location.map { it != null }

    @Composable
    override fun Content(onNext: () -> Unit) {
        val current by household.location.collectAsState(initial = null)
        StepTitle(WHERES_HOME)
        LocationPane(search, current, showCurrent = false, save = ::saveHome)
    }

    /** Before the first Admin exists the wizard saves directly; once one does, it takes `settings.manage` (4a design §3.4). */
    internal suspend fun saveHome(place: PlaceMatch): Boolean {
        if (household.hasActiveAdmin.first() && access.authorise(CorePermissions.SETTINGS_MANAGE) == null) return false
        household.setLocation(place.toHome())
        return true
    }
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/steps/YouStep.kt`:
```kotlin
package uk.co.siland.culvery.core.setup.steps

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.access.ui.ChoosePinPad
import uk.co.siland.culvery.core.household.DuplicateNameException
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.NAME
import uk.co.siland.culvery.core.setup.PIN_SET
import uk.co.siland.culvery.core.setup.PeopleEditor
import uk.co.siland.culvery.core.setup.PersonRow
import uk.co.siland.culvery.core.setup.SET_YOUR_PIN
import uk.co.siland.culvery.core.setup.SetupDimens
import uk.co.siland.culvery.core.setup.SetupType
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.WHOS_SETTING_UP
import uk.co.siland.culvery.core.setup.showPersonEditor
import uk.co.siland.culvery.core.setup.someoneCalled
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.HhSwatch
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.PersonPalette

/** The You step's answers until Next makes the Admin; only the PIN's two entries matching fill [pin]. */
@Stable
internal class YouForm {
    var name by mutableStateOf("")
    var color by mutableStateOf(PersonPalette.colors.first())
    var pin by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)

    val ready: Boolean get() = name.isNotBlank() && pin != null
}

/** 4a design §4.2, §3.4: the first Admin — name, colour and PIN. Next makes them and signs them in until Done. */
@Singleton
class YouStep @Inject constructor(
    private val household: HouseholdRepository,
    private val pins: PinManager,
    private val access: AccessControl,
    private val editor: PeopleEditor,
) : SetupStep {
    internal val form = YouForm()

    override val id = "you"
    override val order = 200
    override val done: Flow<Boolean> = household.hasActiveAdmin
    override val canGoOn: Flow<Boolean> = combine(done, snapshotFlow { form.ready }) { made, ready -> made || ready }

    /** Makes the first Admin with their PIN, once: after a kill, or on Back then Next, the Admin already there is kept. */
    override suspend fun onNext(): Boolean {
        if (household.hasActiveAdmin.first()) return true
        val pin = form.pin ?: return false
        return try {
            val admin = pins.addPerson(form.name, form.color, Role.ADMIN, pin)
            access.beginSetupSession(Identified(admin, Role.ADMIN))
            form.pin = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: DuplicateNameException) {
            form.message = someoneCalled(e.name)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't add the first Admin (${e::class.simpleName})")
            form.message = COULD_NOT_SAVE
            false
        }
    }

    @Composable
    override fun Content(onNext: () -> Unit) {
        val members by editor.members.collectAsState(initial = emptyList())
        val admin = members.firstOrNull { it.isActiveAdmin }
        val overlay = LocalOverlayHost.current
        StepTitle(WHOS_SETTING_UP)
        if (admin != null) {
            // Ruling 18: once made, the Admin is edited in the sheet like anyone else.
            PersonRow(admin) {
                overlay.showPersonEditor(
                    editor,
                    admin,
                    taken = members.filter { it != admin }.mapTo(HashSet()) { it.person.color },
                    lastAdmin = members.count { it.isActiveAdmin } == 1,
                )
            }
        } else {
            YouFormContent(form, onSetPin = {
                overlay.show {
                    ChoosePinPad(
                        onChosen = {
                            form.pin = it
                            form.message = null
                            overlay.dismiss()
                        },
                        onCancel = overlay::dismiss,
                        drawScrim = false,
                    )
                }
            })
        }
    }

    private companion object {
        const val TAG = "YouStep"
    }
}

@Composable
internal fun YouFormContent(form: YouForm, onSetPin: () -> Unit) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap)) {
        HhTextField(
            value = form.name,
            onValueChange = {
                form.name = it
                form.message = null
            },
            placeholder = NAME,
            tag = "you_name",
            capitalization = KeyboardCapitalization.Words,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
            verticalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
        ) {
            PersonPalette.colors.forEachIndexed { i, colour ->
                HhSwatch(Color(colour), chosen = form.color == colour, taken = false, tag = "you_swatch_$i") { form.color = colour }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SetupDimens.rowDotGap)) {
            HhPillButton(SET_YOUR_PIN, onSetPin, Modifier.testTag("you_set_pin"))
            if (form.pin != null) Text(PIN_SET, style = SetupType.secondary, color = c.mute, modifier = Modifier.testTag("you_pin_set"))
        }
        form.message?.let { Text(it, style = SetupType.message, color = c.danger, modifier = Modifier.testTag("you_message")) }
    }
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/steps/HouseholdStep.kt`:
```kotlin
package uk.co.siland.culvery.core.setup.steps

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.PeopleEditor
import uk.co.siland.culvery.core.setup.PeoplePane
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.WHO_ELSE

/** 4a design §4.2: everyone else. Always passable: "Skip for now" while only you live here. */
@Singleton
class HouseholdStep @Inject constructor(private val editor: PeopleEditor) : SetupStep {
    override val id = "household"
    override val order = 300
    override val skippable = true
    override val done: Flow<Boolean> = editor.members.map { it.size > 1 }

    @Composable
    override fun Content(onNext: () -> Unit) {
        StepTitle(WHO_ELSE)
        PeoplePane(editor)
    }
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/steps/DoneStep.kt`:
```kotlin
package uk.co.siland.culvery.core.setup.steps

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.CULVERY_IS_READY
import uk.co.siland.culvery.core.setup.OPEN_CULVERY
import uk.co.siland.culvery.core.setup.SetupSessionGate
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.StepTitle

/** 4a design §3.3, §4.2: "Culvery is ready". Never done, so a resume that gets this far stops here. */
@Singleton
class DoneStep @Inject constructor(
    private val state: SetupState,
    private val access: AccessControl,
    private val gate: SetupSessionGate,
) : SetupStep {
    override val id = "done"
    override val order = 1000
    override val done: Flow<Boolean> = flowOf(false)
    override val canGoOn: Flow<Boolean> = flowOf(true)
    override val nextLabel = OPEN_CULVERY

    /**
     * An Admin finishes setup (silent in the setup session). Signed out before setup is marked complete, so Home never
     * opens with the setup session (4a design §9); the gate is told first, so the sign-out doesn't bring it back.
     */
    override suspend fun onNext(): Boolean {
        access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return false
        gate.finish()
        access.endSetupSession()
        access.lock()
        state.markComplete()
        return true
    }

    @Composable
    override fun Content(onNext: () -> Unit) {
        StepTitle(CULVERY_IS_READY)
    }
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/di/SetupModule.kt`:
```kotlin
package uk.co.siland.culvery.core.setup.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.SampleHousehold
import uk.co.siland.culvery.core.setup.steps.DoneStep
import uk.co.siland.culvery.core.setup.steps.HouseholdStep
import uk.co.siland.culvery.core.setup.steps.LocationStep
import uk.co.siland.culvery.core.setup.steps.WelcomeStep
import uk.co.siland.culvery.core.setup.steps.YouStep

@Module
@InstallIn(SingletonComponent::class)
abstract class SetupModule {
    @Binds
    @IntoSet
    abstract fun welcome(step: WelcomeStep): SetupStep

    @Binds
    @IntoSet
    abstract fun location(step: LocationStep): SetupStep

    @Binds
    @IntoSet
    abstract fun you(step: YouStep): SetupStep

    @Binds
    @IntoSet
    abstract fun household(step: HouseholdStep): SetupStep

    @Binds
    @IntoSet
    abstract fun done(step: DoneStep): SetupStep

    @Multibinds
    abstract fun settingsPages(): Set<SettingsPage>

    /** Present only in debug builds (4a design D11). */
    @BindsOptionalOf
    abstract fun sampleHousehold(): SampleHousehold
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: PASS (new screenshots not yet recorded).

- [ ] **Step 6: Record the screenshots and look at them**

Run: `./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest*"`
Open and check (seven dots each, the step's dot in `accent`):
- `welcome_{dark,light}`: "Welcome to Culvery", the line in `mute`, no Back, an `accent` **Start**; `welcome_sample_dark` adds a `surf2` **Use a sample household** pill under the line;
- `location_results_{dark,light}`: "Where's home?", the field reading "Brigh", two rows ("Brighton, England, United Kingdom" ticked in `accent`, "Brighton, New Zealand"), "Used for the time zone, and for weather." in `mute`, **Back** and **Skip for now**;
- `location_failed_dark`: the field, then "Couldn't search for towns — check the tablet's Wi-Fi and try again." in `danger`;
- `you_{dark,light}`: "Who's setting this up?", the field reading "Alex", eight swatches with the green ringed, **Set your PIN** with "PIN set" beside it, an enabled **Next**;
- `done_{dark,light}`: "Culvery is ready" and an `accent` **Open Culvery**.
- `git status` shows no other changed baselines.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add core/setup
git commit -m "Add the wizard's own steps: welcome, home town, the first Admin, the household, and done"
```

---

### Task 10: Settings — the two-pane frame, Home location, People and Kiosk (§3.6, §4.6, §4.7, D4)

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/ShellNavigator.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`; `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`; `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/RecordingNavigator.kt`
- Create: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SettingsScreen.kt`, `pages/LocationPage.kt`, `pages/PeoplePage.kt`, `pages/KioskPage.kt`
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/di/SetupModule.kt`
- Test: `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SettingsScreenTest.kt`, `SettingsScreenshotTest.kt` (create); `StepsTest.kt`, `TestUi.kt` (modify)
- Screenshots (`core/setup`): `settings_location_{dark,light}`, `settings_people_{dark,light}`, `settings_kiosk_{dark,light}` (new)

**Interfaces:**
- Consumes: `SettingsPage` (Task 1); `LocationPane`, `toHome` (Task 9); `PeoplePane`, `PeopleList` (Task 8); `StepTitle`, `SetupDimens`, the copy (Task 7).
- Produces:
  - `ShellNavigator.exitKiosk()`; `ShellViewModel.exitKiosk()` now overrides it; `RecordingNavigator.kioskExits`
  - `@Composable fun SettingsScreen(pages: List<SettingsPage>, onClose: () -> Unit)` (pages already in order). The touch that keeps Settings open is on the shell's layers (Task 12), so it counts touches in Settings' sheets and PIN pads too.
  - Test helpers in `TestUi.kt`: `class StillPage(id, title, order, content: @Composable () -> Unit) : SettingsPage`; `class RecordingNavigator : ShellNavigator` with `kioskExits`
  - `@Singleton class LocationPage @Inject constructor(household, search: LocationSearch, access: AccessControl) : SettingsPage` — "location", "Home location", 0; `internal suspend fun saveHome(place: PlaceMatch): Boolean` (always `settings.manage`)
  - `@Singleton class PeoplePage @Inject constructor(editor: PeopleEditor) : SettingsPage` — "people", "People", 100
  - `@Singleton class KioskPage @Inject constructor() : SettingsPage` — "kiosk", "Kiosk", 900
  - `SetupModule` binds the three pages `@IntoSet`

- [ ] **Step 1: Write the failing tests**

In `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TestUi.kt`, add the imports `androidx.compose.runtime.Composable`, `uk.co.siland.culvery.core.plugin.SettingsPage`, `uk.co.siland.culvery.core.plugin.ShellNavigator`, and at the end:
```kotlin

/** A Settings page that draws [content]: the Settings tests' and screenshots' pages. */
class StillPage(override val id: String, override val title: String, override val order: Int, val content: @Composable () -> Unit) : SettingsPage {
    @Composable
    override fun Content() = content()
}

class RecordingNavigator : ShellNavigator {
    var kioskExits = 0

    override fun openTab(id: String) = Unit

    override fun openSettings() = Unit

    override fun exitKiosk() {
        kioskExits++
    }
}
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SettingsScreenTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private var closes = 0
    private val navigator = RecordingNavigator()

    private fun page(id: String, title: String, order: Int) = StillPage(id, title, order) { Text("Page $id") }

    private fun show(vararg pages: SettingsPage) = compose.setContent {
        CulveryTheme(dark = true) {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                SettingsScreen(pages.toList(), onClose = { closes++ })
            }
        }
    }

    @Test
    fun theFirstPageIsChosenOnOpen() {
        show(page("a", "Alpha", 0), page("b", "Beta", 1))
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithTag("settings_page_a").assertIsSelected()
        compose.onNodeWithText("Page a").assertExists()
    }

    @Test
    fun choosingATitleShowsItsPage() {
        show(page("a", "Alpha", 0), page("b", "Beta", 1))
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("Page b").assertExists()
        compose.onNodeWithText("Page a").assertDoesNotExist()
    }

    @Test
    fun closeCloses() {
        show(page("a", "Alpha", 0))
        compose.onNodeWithTag("settings_close").performClick()
        assertThat(closes).isEqualTo(1)
    }

    @Test
    fun theKioskPageExitsThroughTheShell() {
        show(KioskPage())
        compose.onNodeWithText("Culvery keeps the tablet on this app. Exit to use other apps; it locks again next time Culvery opens.").assertExists()
        compose.onNodeWithText("Exit kiosk").performClick()
        assertThat(navigator.kioskExits).isEqualTo(1)
    }
}
```

In `core/setup/src/test/java/uk/co/siland/culvery/core/setup/StepsTest.kt`, add the import `uk.co.siland.culvery.core.setup.pages.LocationPage` and:
```kotlin
    @Test
    fun theHomeLocationPageAlwaysTakesTheOpenSession() = runTest {
        val access = testAccess(household)
        access.addAdmin()
        val page = LocationPage(household, NoSearch, access.control)
        access.answer(null)
        assertThat(page.saveHome(brighton)).isFalse()
        assertThat(household.location.first()).isNull()
        access.answer("1234")
        assertThat(page.saveHome(brighton)).isTrue()
        assertThat(household.location.first()).isEqualTo(brighton.toHome())
    }
```

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SettingsScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val brighton = PlaceMatch("Brighton", "England", "United Kingdom", 50.82838, -0.13947, "Europe/London")
    private val people = listOf(
        Member(Person(PersonId("alex"), "Alex", PersonPalette.colors[0]), Role.ADMIN, hasPin = true),
        Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), Role.ADULT, hasPin = false),
    )

    private val pages = listOf(
        StillPage("location", "Home location", 0) {
            StepTitle("Home location")
            LocationContent("", {}, TownResults.Idle, brighton.toHome(), showCurrent = true, busy = false, onChoose = {})
        },
        StillPage("people", "People", 100) {
            StepTitle("People")
            PeopleList(people, onEdit = {}, onAdd = {})
        },
        StillPage("calendars", "Calendars", 400) { StepTitle("Calendars") },
        KioskPage(),
    )

    private fun snap(name: String, dark: Boolean, pageId: String) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                    Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H)) { SettingsScreen(pages, onClose = {}) }
                }
            }
        }
        compose.onNodeWithTag("settings_page_$pageId").performClick()
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun locationDark() = snap("settings_location_dark", true, "location")
    @Test fun locationLight() = snap("settings_location_light", false, "location")
    @Test fun peopleDark() = snap("settings_people_dark", true, "people")
    @Test fun peopleLight() = snap("settings_people_light", false, "people")
    @Test fun kioskDark() = snap("settings_kiosk_dark", true, "kiosk")
    @Test fun kioskLight() = snap("settings_kiosk_light", false, "kiosk")
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :core:setup:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'SettingsScreen'", "'KioskPage'", "'LocationPage'", and "'exitKiosk' overrides nothing".

- [ ] **Step 3: Move Exit kiosk behind the navigator**

In `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/ShellNavigator.kt`, after `fun openSettings()` add:
```kotlin

    /** Settings › Kiosk (4a design §4.7): leaves kiosk mode after a fresh Admin PIN. */
    fun exitKiosk()
```

In `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`, change `fun exitKiosk() {` to `override fun exitKiosk() {`.

In `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`, in `NoNavigation`, add `override fun exitKiosk() = Unit`.

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/RecordingNavigator.kt`, add:
```kotlin
    var kioskExits = 0

    override fun exitKiosk() {
        kioskExits++
    }
```

- [ ] **Step 4: Write the Settings frame and its pages**

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SettingsScreen.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhPillButton

/**
 * Settings (4a design §4.6): the pages' titles on the left with Close, the chosen page on the right, the first chosen on
 * open. Taps on empty space stop here rather than reaching the shell underneath. The touches that keep it open are
 * counted on the shell's layers (Task 12), so its sheets and PIN pads count too.
 */
@Composable
fun SettingsScreen(pages: List<SettingsPage>, onClose: () -> Unit) {
    val c = Culvery.colors
    var chosen by rememberSaveable { mutableStateOf(pages.firstOrNull()?.id) }
    val page = pages.firstOrNull { it.id == chosen } ?: pages.firstOrNull()
    Row(
        Modifier
            .fillMaxSize()
            .testTag("settings")
            .background(c.bg)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .width(SetupDimens.settingsList)
                .fillMaxHeight()
                .background(c.surf)
                .padding(horizontal = SetupDimens.settingsListPaddingH, vertical = SetupDimens.settingsListPaddingV),
        ) {
            Text(SETTINGS, style = SetupType.title, color = c.ink)
            Spacer(Modifier.height(SetupDimens.settingsTitleGap))
            Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.settingsItemGap)) {
                pages.forEach { p -> PageItem(p.title, chosen = p.id == page?.id, tag = "settings_page_${p.id}") { chosen = p.id } }
            }
            Spacer(Modifier.weight(1f))
            HhPillButton(CLOSE, onClose, Modifier.testTag("settings_close"))
        }
        // Each page scrolls from its top.
        key(page?.id) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SetupDimens.pagePaddingH, vertical = SetupDimens.pagePaddingV),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap),
                    modifier = Modifier.widthIn(max = SetupDimens.wizardColumn).testTag("settings_pane"),
                ) {
                    page?.Content()
                }
            }
        }
    }
}

@Composable
private fun PageItem(title: String, chosen: Boolean, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .testTag(tag)
            .semantics { selected = chosen }
            .fillMaxWidth()
            .height(SetupDimens.settingsItemHeight)
            .clip(RoundedCornerShape(SetupDimens.settingsItemRadius))
            .then(if (chosen) Modifier.background(c.accentSoft) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = SetupDimens.settingsItemPaddingH),
    ) {
        Text(title, style = SetupType.rowTitle, color = if (chosen) c.accent else c.ink, maxLines = 1)
    }
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/pages/LocationPage.kt`:
```kotlin
package uk.co.siland.culvery.core.setup.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.HOME_LOCATION
import uk.co.siland.culvery.core.setup.LocationPane
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.core.setup.PlaceMatch
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.toHome

/** Settings › Home location (4a design §4.3): the saved home above the search. */
@Singleton
class LocationPage @Inject constructor(
    private val household: HouseholdRepository,
    private val search: LocationSearch,
    private val access: AccessControl,
) : SettingsPage {
    override val id = "location"
    override val title = HOME_LOCATION
    override val order = 0

    @Composable
    override fun Content() {
        val current by household.location.collectAsState(initial = null)
        StepTitle(HOME_LOCATION)
        LocationPane(search, current, showCurrent = true, save = ::saveHome)
    }

    internal suspend fun saveHome(place: PlaceMatch): Boolean {
        access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return false
        household.setLocation(place.toHome())
        return true
    }
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/pages/PeoplePage.kt`:
```kotlin
package uk.co.siland.culvery.core.setup.pages

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.PEOPLE
import uk.co.siland.culvery.core.setup.PeopleEditor
import uk.co.siland.culvery.core.setup.PeoplePane
import uk.co.siland.culvery.core.setup.StepTitle

/** Settings › People (4a design §4.4): the Household step's list and sheet. */
@Singleton
class PeoplePage @Inject constructor(private val editor: PeopleEditor) : SettingsPage {
    override val id = "people"
    override val title = PEOPLE
    override val order = 100

    @Composable
    override fun Content() {
        StepTitle(PEOPLE)
        PeoplePane(editor)
    }
}
```

Create `core/setup/src/main/java/uk/co/siland/culvery/core/setup/pages/KioskPage.kt`:
```kotlin
package uk.co.siland.culvery.core.setup.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.EXIT_KIOSK
import uk.co.siland.culvery.core.setup.KIOSK
import uk.co.siland.culvery.core.setup.KIOSK_LINE
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.ui.HhPillButton

/** Settings › Kiosk (4a design §4.7): Exit kiosk, moved here from the old Settings footer; it asks for a fresh PIN. */
@Singleton
class KioskPage @Inject constructor() : SettingsPage {
    override val id = "kiosk"
    override val title = KIOSK
    override val order = 900

    @Composable
    override fun Content() {
        val navigator = LocalShellNavigator.current
        StepTitle(KIOSK, KIOSK_LINE)
        HhPillButton(EXIT_KIOSK, navigator::exitKiosk, Modifier.testTag("settings_exit_kiosk"))
    }
}
```

In `core/setup/src/main/java/uk/co/siland/culvery/core/setup/di/SetupModule.kt`, import the three pages and add after `done`:
```kotlin

    @Binds
    @IntoSet
    abstract fun locationPage(page: LocationPage): SettingsPage

    @Binds
    @IntoSet
    abstract fun peoplePage(page: PeoplePage): SettingsPage

    @Binds
    @IntoSet
    abstract fun kioskPage(page: KioskPage): SettingsPage
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:plugin:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS (new screenshots not yet recorded).

- [ ] **Step 6: Record the screenshots and look at them**

Run: `./gradlew :core:setup:recordRoborazziDebug --tests "*SettingsScreenshotTest*"`
Open and check:
- all six: a 320 dp `surf` column on the left with "Settings", then "Home location", "People", "Calendars", "Kiosk" (the chosen one on `accentSoft` in `accent`), and **Close** at the bottom; the page on `bg` to the right;
- `settings_location_*`: "Home location", the saved home as a ticked row "Brighton, England, United Kingdom", the empty field reading "Town or city", "Used for the time zone, and for weather.";
- `settings_people_*`: "People", Alex and Sam rows, **Add person**;
- `settings_kiosk_*`: "Kiosk", the line "Culvery keeps the tablet on this app. Exit to use other apps; it locks again next time Culvery opens." in `mute`, and **Exit kiosk**.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add core/plugin core/setup app capability/calendar/src/test
git commit -m "Add two-pane Settings with Home location, People and Kiosk"
```

---

### Task 11: The calendar's Connect step, Review calendars (wizard and Settings), and Disconnect (§3.9, §4.5, D3, D13, D14)

**Files:**
- Modify: `capability/calendar/build.gradle.kts`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarReview.kt`, `CalendarSetupSteps.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ReviewCalendars.kt`
- Delete: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarSettings.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`, `ui/CalendarType.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarReviewTest.kt`, `TestLogs.kt`, `ui/ReviewCalendarsTest.kt`, `ui/ReviewScreenshotTest.kt` (create); `StubEditor.kt`, `CalendarCapabilityTest.kt`, `ui/ConnectScreenshotTest.kt` (modify); `ui/CalendarSettingsTest.kt` (delete)
- Screenshots (`capability/calendar`): delete `settings_calendars_{,reconnect_,connect_}{dark,light}.png`; new `review_ok_{dark,light}`, `review_reconnect_{dark,light}`, `review_person_picker_dark`, `review_disconnect_dark`, `connect_step_{dark,light}`, `settings_calendars_page_{dark,light}`

**Interfaces:**
- Consumes: `CalendarStore.setMapping`/`queuedChanges`/`removeConnection`/`sources` (Task 6, 3a); `CalendarSetup.setMaster`, `CalendarConnections.rows`/`connectable`, `rememberConnector`, `ConnectCardHost`, `AddButton`, `healthWords` (3a); `HhSheetButton`, `ButtonTone`, `HhChoiceChip`, `HhSwitch`, `rememberSingleAction` (Task 2); `SetupStep`, `SettingsPage`, `COULD_NOT_SAVE` (Task 1); `SettingsScreen`, `KioskPage` (Task 10, tests only).
- Produces:
  - `data class ReviewConnection(val row: CalendarRow, val sources: List<StoredSource>)`
  - `fun nowShowsAs(calendar: String, person: String)`, `calendarHidden(calendar)`, `calendarShown(calendar)`, `newEventsGoTo(calendar)`, `disconnected(service)`, `disconnectQuestion(service: String, queued: Int)`
  - `@Singleton class CalendarReview @Inject constructor(store: CalendarStore, setup: CalendarSetup, calendarConnections: CalendarConnections, household: HouseholdRepository, access: AccessControl, toaster: Toaster)` with `val connections: Flow<List<ReviewConnection>>`, `val people: Flow<List<Person>>` (Family first), `suspend fun setPerson(source: StoredSource, person: Person): Boolean`, `setShown(source, shown: Boolean): Boolean`, `makeMaster(source): Boolean`, `queuedChanges(connectionId: String): Int`, `disconnect(row: CalendarRow): Boolean`
  - `class CalendarConnectStep(repo, connections) : SetupStep` ("calendar.connect", 400, skippable); `class ReviewCalendarsStep(repo, review, connections, clock) : SetupStep` ("calendar.review", 410, shown once connected); `class CalendarsPage(review, connections, clock) : SettingsPage` ("calendars", "Calendars", 400)
  - `CalendarCapability` gains `review: CalendarReview` (last constructor parameter), `setupSteps()`, `settingsPages()`; its `SettingsSection()` override is removed
  - `internal data class Confirming(val row: CalendarRow, val queued: Int)`; `internal class ReviewActions(onConnect, onReconnect, onPick, onPerson, onShown, onMakeMaster, onDisconnect, onKeep, onConfirmDisconnect)` (each defaulting to nothing); `@Composable internal fun ReviewCalendars(title: String, connections: List<ReviewConnection>, people: List<Person>, nowMillis: Long, busy: Boolean, picking: String?, confirming: Confirming?, connectable: List<ProviderDescriptor>, actions: ReviewActions)`; `internal fun sourceKey(source: StoredSource): String`
  - Test helper: `internal fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1)` (`capability/calendar/src/test/…/TestLogs.kt`)
  - `internal fun stubReview(store: CalendarStore, household: HouseholdRepository): CalendarReview` (tests)

- [ ] **Step 1: Write the failing tests**

In `capability/calendar/build.gradle.kts`, after `testImplementation(libs.androidx.sqlite.framework)` add:
```kotlin
    // Settings' frame and Kiosk page, for the Calendars page's screenshot.
    testImplementation(project(":core:setup"))
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt`, add the import `uk.co.siland.culvery.core.household.HouseholdRepository` and at the end:
```kotlin

/** A review that nobody may change, for tests that only need the capability's pages to exist. */
internal fun stubReview(store: CalendarStore, household: HouseholdRepository): CalendarReview = CalendarReview(
    store,
    CalendarSetup(store, emptySet(), { emptyList() }, RecordingToaster(), WallClock { 0L }, EmptyCoroutineContext),
    stubConnections(store),
    household,
    NobodyMay,
    RecordingToaster(),
)
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarCapabilityTest.kt`:
1. Replace the `capability = CalendarCapability(…)` statement with:
```kotlin
        capability = CalendarCapability(
            CalendarRepository(store, household, zone, emptySet(), emptySet()), zone, WallClock { 0L }, stubEditor(store, zone), stubConnections(store),
            stubReview(store, household),
        )
```
2. Add:
```kotlin
    @Test
    fun itAddsConnectAndReviewToTheWizardAndCalendarsToSettings() = runTest {
        assertThat(capability.setupSteps().map { it.id to it.order }).containsExactly("calendar.connect" to 400, "calendar.review" to 410).inOrder()
        assertThat(capability.settingsPages().map { Triple(it.id, it.title, it.order) }).containsExactly(Triple("calendars", "Calendars", 400))
        val (connect, review) = capability.setupSteps()
        assertThat(connect.skippable).isTrue()
        assertThat(listOf(connect.done.first(), review.shown.first())).containsExactly(false, false)
        connect()
        assertThat(listOf(connect.done.first(), review.shown.first(), review.done.first())).containsExactly(true, true, true)
    }
```
(`connect()` is the file's helper that adds a connection.)

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/TestLogs.kt` (the same helper as `:core:setup`'s `TestUi.kt`; test sources aren't shared between modules):
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertWithMessage
import org.robolectric.shadows.ShadowLog

/**
 * Nothing logged under [tag], with its whole chain of causes, holds any of [secrets]; at least [minLines] were logged,
 * so a check that saw no log can't pass by default.
 */
internal fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1) {
    val logs = ShadowLog.getLogs().filter { it.tag == tag }
    assertWithMessage("lines logged under $tag").that(logs.size).isAtLeast(minLines)
    logs.forEach { log ->
        val text = "${log.msg} ${generateSequence(log.throwable) { it.cause }.joinToString(" ")}"
        secrets.forEach { assertWithMessage(text).that(text).doesNotContain(it) }
    }
}
```

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarReviewTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room and android.util.Log.
@RunWith(AndroidJUnit4::class)
class CalendarReviewTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var people: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository
    private val family = CalendarSource("family@example.com", "Family", writable = true, primary = true)
    private val swim = CalendarSource("swim", "Mia's swimming", writable = false)
    private val chores = CalendarSource("chores", "Chores", writable = true)
    private val google = ScriptedProvider(
        "calendar.google", sourceList = listOf(family, swim, chores), features = setOf(Feature.READ, Feature.WRITE), displayName = "Google Calendar",
    )
    private val connection = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val toaster = RecordingToaster()

    @Before
    fun setUp() {
        ShadowLog.clear()
        calendar = calendarDb()
        people = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(people)
    }

    @After
    fun tearDown() {
        calendar.close()
        people.close()
    }

    private suspend fun TestScope.review(): Pair<CalendarReview, TestAccess> {
        val access = testAccess(household)
        val setup = CalendarSetup(store, setOf(google), { household.people.first() }, toaster, WallClock { 0L }, EmptyCoroutineContext)
        val connections = CalendarConnections(store, setup, access.control, setOf(google), backgroundScope)
        store.addConnection(connection, google.sourceList, emptyMap(), masterSourceId = family.id)
        return CalendarReview(store, setup, connections, household, access.control, toaster) to access
    }

    private suspend fun source(id: String): StoredSource = store.source("g1", id)!!

    @Test
    fun choosingWhoACalendarIsForTakesTheOpenSessionAndSaysSo() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.ALEX)
        assertThat(review.setPerson(source("swim"), access.mia)).isTrue()
        assertThat(source("swim").mapping.person).isEqualTo(access.mia.id)
        assertThat(toaster.messages).containsExactly("Mia's swimming now shows as Mia")
        assertThat(access.requests.map { it.label }).containsExactly("Change settings")
    }

    @Test
    fun hidingAndShowingIsSaid() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.ALEX)
        review.setShown(source("swim"), shown = false)
        assertThat(source("swim").mapping.visible).isFalse()
        review.setShown(source("swim"), shown = true)
        assertThat(toaster.messages).containsExactly("Mia's swimming hidden", "Mia's swimming shown").inOrder()
    }

    @Test
    fun theMasterCantBeHidden() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.ALEX)
        assertThat(review.setShown(source(family.id), shown = false)).isFalse()
        assertThat(source(family.id).mapping.visible).isTrue()
        assertThat(toaster.messages).containsExactly(COULD_NOT_SAVE)
    }

    @Test
    fun makingAnotherCalendarTheMasterSaysWhereNewEventsGo() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.ALEX)
        assertThat(review.makeMaster(source("chores"))).isTrue()
        assertThat(store.master().first()?.source?.id).isEqualTo("chores")
        assertThat(toaster.messages).containsExactly("New events now go to Chores")
    }

    @Test
    fun disconnectingCountsTheQueueThenRemovesEverything() = runTest {
        val (review, access) = review()
        repeat(3) { i -> store.enqueue(PendingChange(0, "g1", family.id, "e$i", ChangeKind.DELETE, null, 0, 0L, 0L)) }
        assertThat(review.queuedChanges("g1")).isEqualTo(3)
        val row = review.connections.first().single().row
        access.answer(TestAccess.ALEX)
        assertThat(review.disconnect(row)).isTrue()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Google Calendar disconnected")
    }

    @Test
    fun aCancelledPinChangesNothing() = runTest {
        val (review, access) = review()
        access.answer(null)
        assertThat(review.setShown(source("swim"), shown = false)).isFalse()
        assertThat(source("swim").mapping.visible).isTrue()
        assertThat(toaster.messages).isEmpty()
    }

    @Test
    fun aChildCantChangeACalendar() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.MIA, null)
        assertThat(review.setPerson(source("swim"), Person.Family)).isFalse()
        assertThat(toaster.messages).isEmpty()
    }

    @Test
    fun aFailedCalendarChangeToastsAndLogsNoAccountOrCalendar() = runTest {
        val (review, access) = review()
        val swimming = source("swim")
        access.answer(TestAccess.ALEX)
        calendar.close()
        assertThat(review.setPerson(swimming, access.mia)).isFalse()
        assertThat(toaster.messages).containsExactly(COULD_NOT_SAVE)
        assertNoSecretsLogged("CalendarReview", listOf("family@example.com", "Mia's swimming", "swim"))
    }

    @Test
    fun connectingTheSameAccountTwiceKeepsOneConnection() = runTest {
        val access = testAccess(household)
        val setup = CalendarSetup(store, setOf(google), { household.people.first() }, toaster, WallClock { 0L }, EmptyCoroutineContext)
        val connections = CalendarConnections(store, setup, access.control, setOf(google), backgroundScope)
        // The wizard's Connect step finishes a connect this way; a second tap while the first sets up does the same.
        connections.finish(connection, reconnecting = false)
        connections.finish(connection, reconnecting = false)
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (toaster.messages.size < 2) delay(10) } }
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
        assertThat(toaster.messages).containsExactly("Google Calendar connected", "Google Calendar reconnected").inOrder()
    }

    @Test
    fun theDisconnectQuestionCountsWhatIsDropped() {
        assertThat(disconnectQuestion("Google Calendar", 0)).isEqualTo("Disconnect Google Calendar? Its calendars leave the tablet.")
        assertThat(disconnectQuestion("Google Calendar", 1))
            .isEqualTo("Disconnect Google Calendar? Its calendars leave the tablet, and 1 change still waiting to sync is dropped.")
        assertThat(disconnectQuestion("Google Calendar", 3))
            .isEqualTo("Disconnect Google Calendar? Its calendars leave the tablet, and 3 changes still waiting to sync are dropped.")
    }
}
```

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/ReviewCalendarsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.capability.calendar.SourceMapping
import uk.co.siland.culvery.capability.calendar.StoredSource
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class ReviewCalendarsTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val family = StoredSource("g1", CalendarSource("family", "Family", writable = true), SourceMapping(PersonId.FAMILY, visible = true), isMaster = true)
    private val swim = StoredSource("g1", CalendarSource("swim", "Mia's swimming", writable = false), SourceMapping(mia.id, visible = true))
    private val chores = StoredSource("g1", CalendarSource("chores", "Chores", writable = true), SourceMapping(PersonId.FAMILY, visible = false))

    private fun row(health: ConnectionHealth = ConnectionHealth.Ok) =
        CalendarRow(google, "Google Calendar", "calendar_month", health, lastSyncMillis = now - 2 * 60_000)

    private var picked: String? = null
    private var chosen: Pair<String, String>? = null
    private var shown: Pair<String, Boolean>? = null
    private var mastered: String? = null
    private var reconnected: Connection? = null
    private var disconnecting: CalendarRow? = null
    private var kept = 0
    private var confirmed: CalendarRow? = null

    private fun show(health: ConnectionHealth = ConnectionHealth.Ok, picking: String? = null, confirming: Confirming? = null) = compose.setContent {
        CulveryTheme(dark = true) {
            ReviewCalendars(
                title = "Calendars",
                connections = listOf(ReviewConnection(row(health), listOf(family, swim, chores))),
                people = listOf(Person.Family, mia),
                nowMillis = now,
                busy = false,
                picking = picking,
                confirming = confirming,
                connectable = emptyList(),
                actions = ReviewActions(
                    onReconnect = { reconnected = it },
                    onPick = { picked = it },
                    onPerson = { source, person -> chosen = source.source.id to person.name },
                    onShown = { source, visible -> shown = source.source.id to visible },
                    onMakeMaster = { mastered = it.source.id },
                    onDisconnect = { disconnecting = it },
                    onKeep = { kept++ },
                    onConfirmDisconnect = { confirmed = it },
                ),
            )
        }
    }

    @Test
    fun theConnectionNamesItsAccountAndHealth() {
        show()
        compose.onNodeWithText("Calendars").assertExists()
        compose.onNodeWithText("Google Calendar · family@example.com").assertExists()
        compose.onNodeWithText("Synced 2 min ago").assertExists()
        compose.onNodeWithTag("settings_reconnect").assertDoesNotExist()
    }

    @Test
    fun aConnectionNeedingSignInOffersReconnect() {
        show(ConnectionHealth.NeedsSignIn)
        compose.onNodeWithText("Needs reconnecting").assertExists()
        compose.onNodeWithTag("settings_reconnect").performClick()
        assertThat(reconnected).isEqualTo(google)
    }

    @Test
    fun theMasterIsMarkedAndAlwaysShown() {
        show()
        compose.onNodeWithText("Master").assertExists()
        compose.onNodeWithText("New events go here").assertExists()
        compose.onNodeWithTag("review_show_family").assertIsNotEnabled()
        compose.onNodeWithTag("review_show_chores").assertIsEnabled().performClick()
        assertThat(shown).isEqualTo("chores" to true)
    }

    @Test
    fun onlyAnotherWritableCalendarOffersMakeMaster() {
        show()
        compose.onNodeWithTag("review_make_master_family").assertDoesNotExist()
        compose.onNodeWithTag("review_make_master_swim").assertDoesNotExist()
        compose.onNodeWithTag("review_make_master_chores").performClick()
        assertThat(mastered).isEqualTo("chores")
    }

    @Test
    fun aCalendarsPersonOpensFamilyAndThePeople() {
        show()
        compose.onNodeWithTag("review_person_swim").performClick()
        assertThat(picked).isEqualTo(sourceKey(swim))
    }

    @Test
    fun choosingSomeoneFromTheOpenChips() {
        show(picking = sourceKey(swim))
        compose.onNodeWithTag("review_pick_Mia").assertIsSelected()
        compose.onNodeWithTag("review_pick_Family").performClick()
        assertThat(chosen).isEqualTo("swim" to "Family")
    }

    @Test
    fun disconnectAsksFirstAndCountsWhatWaits() {
        var confirming by mutableStateOf<Confirming?>(null)
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    "Calendars", listOf(ReviewConnection(row(), listOf(family))), listOf(Person.Family), now, false, null, confirming, emptyList(),
                    ReviewActions(
                        onDisconnect = { confirming = Confirming(it, 3) },
                        onKeep = {
                            kept++
                            confirming = null
                        },
                        onConfirmDisconnect = { confirmed = it },
                    ),
                )
            }
        }
        compose.onNodeWithTag("review_disconnect_g1").performClick()
        compose.onNodeWithText("Disconnect Google Calendar? Its calendars leave the tablet, and 3 changes still waiting to sync are dropped.").assertExists()
        compose.onNodeWithTag("review_keep").performClick()
        assertThat(kept).isEqualTo(1)
        compose.onNodeWithTag("review_disconnect_g1").performClick()
        compose.onNodeWithTag("review_confirm_disconnect").performClick()
        assertThat(confirmed).isEqualTo(row())
    }
}
```

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/ReviewScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.capability.calendar.SourceMapping
import uk.co.siland.culvery.capability.calendar.StoredSource
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.SettingsScreen
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The tablet's canvas, and the wizard's 720 dp column (4a design §4.1). */
private val CANVAS_W = 1280.dp
private val CANVAS_H = 800.dp
private val COLUMN_W = 720.dp
private val MARGIN = 48.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val sources = listOf(
        StoredSource("g1", CalendarSource("family", "Family", writable = true), SourceMapping(PersonId.FAMILY, visible = true), isMaster = true),
        StoredSource("g1", CalendarSource("swim", "Mia's swimming", writable = false), SourceMapping(mia.id, visible = true)),
        StoredSource("g1", CalendarSource("chores", "Chores", writable = true), SourceMapping(PersonId.FAMILY, visible = false)),
    )

    private fun connections(health: ConnectionHealth) =
        listOf(ReviewConnection(CalendarRow(google, "Google Calendar", "calendar_month", health, now - 2 * 60_000), sources))

    @Composable
    private fun Review(title: String, health: ConnectionHealth, picking: String? = null, confirming: Confirming? = null) = ReviewCalendars(
        title, connections(health), listOf(Person.Family, sam, mia), now, false, picking, confirming, emptyList(), ReviewActions(),
    )

    /** Draws [content] on the canvas, runs [before] (a tap, say), then captures it as [name]. */
    private fun snap(name: String, dark: Boolean, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                    Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H).background(Culvery.colors.bg)) { content() }
                }
            }
        }
        before()
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun wizard(name: String, dark: Boolean, health: ConnectionHealth, picking: String? = null, confirming: Confirming? = null) =
        snap(name, dark) {
            Box(Modifier.padding(MARGIN).width(COLUMN_W)) { Review("Your calendars", health, picking, confirming) }
        }

    @Test fun reviewOkDark() = wizard("review_ok_dark", true, ConnectionHealth.Ok)
    @Test fun reviewOkLight() = wizard("review_ok_light", false, ConnectionHealth.Ok)
    @Test fun reviewReconnectDark() = wizard("review_reconnect_dark", true, ConnectionHealth.NeedsSignIn)
    @Test fun reviewReconnectLight() = wizard("review_reconnect_light", false, ConnectionHealth.NeedsSignIn)
    @Test fun reviewPersonPickerDark() = wizard("review_person_picker_dark", true, ConnectionHealth.Ok, picking = sourceKey(sources[1]))

    @Test
    fun reviewDisconnectDark() = wizard(
        "review_disconnect_dark", true, ConnectionHealth.Ok,
        confirming = Confirming(connections(ConnectionHealth.Ok).single().row, 3),
    )

    private fun connectStep(name: String, dark: Boolean) = snap(name, dark) {
        Box(Modifier.padding(MARGIN)) { ConnectStepCard(connectService = "Google Calendar", onConnect = {}) }
    }

    @Test fun connectStepDark() = connectStep("connect_step_dark", true)
    @Test fun connectStepLight() = connectStep("connect_step_light", false)

    private class StillPage(override val id: String, override val title: String, override val order: Int, val content: @Composable () -> Unit) : SettingsPage {
        @Composable
        override fun Content() = content()
    }

    private fun settings(name: String, dark: Boolean) {
        val pages = listOf(
            StillPage("location", "Home location", 0) {},
            StillPage("people", "People", 100) {},
            StillPage("calendars", "Calendars", 400) { Review("Calendars", ConnectionHealth.Ok) },
            KioskPage(),
        )
        snap(name, dark, before = { compose.onNodeWithTag("settings_page_calendars").performClick() }) { SettingsScreen(pages, onClose = {}) }
    }

    @Test fun settingsCalendarsPageDark() = settings("settings_calendars_page_dark", true)
    @Test fun settingsCalendarsPageLight() = settings("settings_calendars_page_light", false)
}
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/ConnectScreenshotTest.kt`, delete the `settings` helper, `SETTINGS_W`, `row`, `googleDescriptor`, the six `settings…` tests and the imports only they used; keep `connectingDark`/`connectingLight`. Delete the six `settings_calendars_*.png` baselines:
```bash
git rm capability/calendar/src/test/screenshots/settings_calendars_dark.png capability/calendar/src/test/screenshots/settings_calendars_light.png \
  capability/calendar/src/test/screenshots/settings_calendars_reconnect_dark.png capability/calendar/src/test/screenshots/settings_calendars_reconnect_light.png \
  capability/calendar/src/test/screenshots/settings_calendars_connect_dark.png capability/calendar/src/test/screenshots/settings_calendars_connect_light.png
git rm capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CalendarSettingsTest.kt
```
(`ReviewCalendarsTest` covers what `CalendarSettingsTest` did: the account line, the health words, Reconnect.)

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'CalendarReview'", "'ReviewConnection'", "'ReviewCalendars'", "'Confirming'", "'sourceKey'", "'ConnectStepCard'", "'disconnectQuestion'", and "Too many arguments for CalendarCapability".

- [ ] **Step 3: Write the review's logic**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarReview.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.Toaster

/** One connection as Review calendars shows it (4a design §4.5): its row, and its calendars. */
data class ReviewConnection(val row: CalendarRow, val sources: List<StoredSource>)

// 4a design §4.5.
fun nowShowsAs(calendar: String, person: String): String = "$calendar now shows as $person"

fun calendarHidden(calendar: String): String = "$calendar hidden"

fun calendarShown(calendar: String): String = "$calendar shown"

fun newEventsGoTo(calendar: String): String = "New events now go to $calendar"

fun disconnected(service: String): String = "$service disconnected"

/** One change reads in the singular (ruling 13). */
fun disconnectQuestion(service: String, queued: Int): String = "Disconnect $service? Its calendars leave the tablet" + when (queued) {
    0 -> "."
    1 -> ", and 1 change still waiting to sync is dropped."
    else -> ", and $queued changes still waiting to sync are dropped."
}

/**
 * Review calendars (4a design §3.9, D13, D14), for the wizard and Settings › Calendars: who each calendar is for, whether
 * it shows, the master, and disconnecting. Every change takes `settings.manage`, applies at once and on the tablet only,
 * and is toasted; a failure is "Couldn't save — try again." and changes nothing.
 */
@Singleton
class CalendarReview @Inject constructor(
    private val store: CalendarStore,
    private val setup: CalendarSetup,
    calendarConnections: CalendarConnections,
    household: HouseholdRepository,
    private val access: AccessControl,
    private val toaster: Toaster,
) {
    val connections: Flow<List<ReviewConnection>> = combine(calendarConnections.rows, store.sources()) { rows, sources ->
        rows.map { row -> ReviewConnection(row, sources.filter { it.connectionId == row.connection.id }) }
    }

    /** Family first, then the household: the person chips. */
    val people: Flow<List<Person>> = household.peopleWithFamily

    suspend fun setPerson(source: StoredSource, person: Person): Boolean = change {
        store.setMapping(source.connectionId, source.source.id, person.id, source.mapping.visible)
        toaster.show(nowShowsAs(source.source.name, person.name))
    }

    suspend fun setShown(source: StoredSource, shown: Boolean): Boolean = change {
        store.setMapping(source.connectionId, source.source.id, source.mapping.person, shown)
        toaster.show(if (shown) calendarShown(source.source.name) else calendarHidden(source.source.name))
    }

    /** CalendarSetup.setMaster checks with the provider that the calendar can be written, then shows it. */
    suspend fun makeMaster(source: StoredSource): Boolean = change {
        setup.setMaster(source.connectionId, source.source.id)
        toaster.show(newEventsGoTo(source.source.name))
    }

    /** For the disconnect confirmation; changes nothing, so it asks for no PIN. */
    suspend fun queuedChanges(connectionId: String): Int = store.queuedChanges(connectionId)

    /** D14: the connection with its calendars, events and queued changes. The grant in the Google account stays. */
    suspend fun disconnect(row: CalendarRow): Boolean = change {
        store.removeConnection(row.connection.id)
        toaster.show(disconnected(row.service))
    }

    private suspend fun change(block: suspend () -> Unit): Boolean {
        access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return false
        return try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The type only (P8): a message can hold a calendar id, which is often the account's email.
            Log.w(TAG, "Couldn't save a calendar change (${e::class.simpleName})")
            toaster.show(COULD_NOT_SAVE)
            false
        }
    }

    private companion object {
        const val TAG = "CalendarReview"
    }
}
```

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSetupSteps.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.capability.calendar.ui.ConnectStepHost
import uk.co.siland.culvery.capability.calendar.ui.ReviewCalendarsHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.plugin.WallClock

/** 4a design §4.5. */
const val YOUR_CALENDARS = "Your calendars"
const val CALENDARS = "Calendars"

/** The wizard's Connect step (4a design §3.9): the Connect card, or Skip for now. Done once any connection exists. */
class CalendarConnectStep(repo: CalendarRepository, private val connections: CalendarConnections) : SetupStep {
    override val id = "calendar.connect"
    override val order = 400
    override val skippable = true
    override val done: Flow<Boolean> = repo.hasConnections

    @Composable
    override fun Content(onNext: () -> Unit) = ConnectStepHost(connections)
}

/** The wizard's Review calendars (4a design D3), shown once a connection exists. */
class ReviewCalendarsStep(
    repo: CalendarRepository,
    private val review: CalendarReview,
    private val connections: CalendarConnections,
    private val clock: WallClock,
) : SetupStep {
    override val id = "calendar.review"
    override val order = 410
    override val shown: Flow<Boolean> = repo.hasConnections
    override val done: Flow<Boolean> = repo.hasConnections

    @Composable
    override fun Content(onNext: () -> Unit) = ReviewCalendarsHost(review, connections, clock, YOUR_CALENDARS, offerConnect = false)
}

/** Settings › Calendars (4a design D4): Review calendars, and Connect for a service not yet connected (ruling 14). */
class CalendarsPage(private val review: CalendarReview, private val connections: CalendarConnections, private val clock: WallClock) : SettingsPage {
    override val id = "calendars"
    override val title = CALENDARS
    override val order = 400

    @Composable
    override fun Content() = ReviewCalendarsHost(review, connections, clock, CALENDARS, offerConnect = true)
}
```

- [ ] **Step 4: Write the review's screen**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`:
1. In `CalendarType`, after `settingsRowTitle` add:
```kotlin

    /** 34 sp / 700: "Your calendars" and "Calendars", as the wizard's and Settings' titles (4a design §4.5). */
    val reviewTitle = HhType.screenTitle

    /** 13 sp / 700: the "Master" badge. */
    val badge = syncingPill
```
2. In `CalendarDimens`, before `CHIP_ALPHA_DARK`, add:
```kotlin
    // Review calendars (4a design §4.5; not in the spec): blocks 12 apart; a calendar's row `surf`, radius 18, padding
    // 14×20, its parts 12 apart; the Master badge `accentSoft`, radius 12, padding 4×10. The wizard's Connect card
    // 380×460, about a Home card's size.
    val reviewBlockGap = 12.dp
    val reviewRowRadius = 18.dp
    val reviewRowPaddingV = 14.dp
    val reviewRowPaddingH = 20.dp
    val reviewItemGap = 12.dp
    val badgeRadius = 12.dp
    val badgePaddingV = 4.dp
    val badgePaddingH = 10.dp
    val connectStepWidth = 380.dp
    val connectStepHeight = 460.dp
```

`git rm capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarSettings.kt`, then create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ReviewCalendars.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarReview
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.capability.calendar.StoredSource
import uk.co.siland.culvery.capability.calendar.disconnectQuestion
import uk.co.siland.culvery.capability.calendar.syncedLabel
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.HhSheetButton
import uk.co.siland.culvery.core.ui.HhSwitch
import uk.co.siland.culvery.core.ui.rememberSingleAction

/** 3a design §4.1. */
internal const val NEEDS_RECONNECTING = "Needs reconnecting"
internal const val SOMETHING_WENT_WRONG = "Something went wrong"

// 4a design §4.5.
internal const val DISCONNECT = "Disconnect"
internal const val SHOW = "Show"
internal const val MASTER = "Master"
internal const val NEW_EVENTS_GO_HERE = "New events go here"
internal const val MAKE_MASTER = "Make master"
internal const val KEEP = "Keep"

private const val TAG = "ReviewCalendars"

/** A row's health in words: "Synced 5 min ago", "Can't reach Google Calendar", "Needs reconnecting" or "Something went wrong". */
internal fun healthWords(row: CalendarRow, nowMillis: Long): String = when (row.health) {
    ConnectionHealth.Ok -> syncedLabel(row.lastSyncMillis, nowMillis).replaceFirstChar { it.uppercase() }
    ConnectionHealth.Unreachable -> "Can't reach ${row.service}"
    ConnectionHealth.NeedsSignIn -> NEEDS_RECONNECTING
    is ConnectionHealth.Error -> SOMETHING_WENT_WRONG
}

/** Which calendar's person chips are open; NUL never appears in a provider id. */
internal fun sourceKey(source: StoredSource): String = "${source.connectionId}\u0000${source.source.id}"

/** The disconnect being confirmed, with the queued changes it would drop. */
internal data class Confirming(val row: CalendarRow, val queued: Int)

/** The wizard's Connect step: the Home screen's Connect card at about its Home size (4a design §3.9). */
@Composable
internal fun ConnectStepHost(connections: CalendarConnections) {
    val connectable by connections.connectable.collectAsState(initial = emptyList())
    val connector = rememberConnector(connections)
    val first = connectable.firstOrNull()
    ConnectStepCard(first?.descriptor?.displayName, onConnect = { first?.let { connector.connect(it.descriptor.id) } })
}

@Composable
internal fun ConnectStepCard(connectService: String?, onConnect: () -> Unit) {
    Box(Modifier.size(CalendarDimens.connectStepWidth, CalendarDimens.connectStepHeight)) { ConnectCalendarCard(connectService, onConnect) }
}

@Composable
internal fun ReviewCalendarsHost(review: CalendarReview, connections: CalendarConnections, clock: WallClock, title: String, offerConnect: Boolean) {
    val list by review.connections.collectAsState(initial = emptyList())
    val people by review.people.collectAsState(initial = listOf(Person.Family))
    val connectable by connections.connectable.collectAsState(initial = emptyList())
    val connector = rememberConnector(connections)
    var picking by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<Confirming?>(null) }
    val action = rememberSingleAction(Unit) { e -> Log.w(TAG, "A calendar change failed (${e::class.simpleName})") }
    ReviewCalendars(
        title = title,
        connections = list,
        people = people,
        nowMillis = rememberNowMillis(clock),
        busy = action.busy,
        picking = picking,
        confirming = confirming,
        connectable = if (offerConnect) connectable.map { it.descriptor } else emptyList(),
        actions = ReviewActions(
            onConnect = { connector.connect(it.id) },
            onReconnect = connector::reconnect,
            onPick = { key -> picking = if (picking == key) null else key },
            onPerson = { source, person ->
                picking = null
                action.run { review.setPerson(source, person) }
            },
            onShown = { source, shown -> action.run { review.setShown(source, shown) } },
            onMakeMaster = { source -> action.run { review.makeMaster(source) } },
            onDisconnect = { row -> action.run { confirming = Confirming(row, review.queuedChanges(row.connection.id)) } },
            onKeep = { confirming = null },
            onConfirmDisconnect = { row -> action.run { if (review.disconnect(row)) confirming = null } },
        ),
    )
}

/** What Review calendars' taps do; each does nothing unless given, so a test or screenshot passes only what it checks. */
internal class ReviewActions(
    val onConnect: (ProviderDescriptor) -> Unit = {},
    val onReconnect: (Connection) -> Unit = {},
    val onPick: (String) -> Unit = {},
    val onPerson: (StoredSource, Person) -> Unit = { _, _ -> },
    val onShown: (StoredSource, Boolean) -> Unit = { _, _ -> },
    val onMakeMaster: (StoredSource) -> Unit = {},
    val onDisconnect: (CalendarRow) -> Unit = {},
    val onKeep: () -> Unit = {},
    val onConfirmDisconnect: (CalendarRow) -> Unit = {},
)

/**
 * 4a design §4.5: per connection, "{Service} · {account}", its health, Reconnect when needed and Disconnect (confirmed
 * first); per calendar, its name, who it is for (tap for Family and the people), Show, and Master or Make master.
 */
@Composable
internal fun ReviewCalendars(
    title: String,
    connections: List<ReviewConnection>,
    people: List<Person>,
    nowMillis: Long,
    busy: Boolean,
    picking: String?,
    confirming: Confirming?,
    connectable: List<ProviderDescriptor>,
    actions: ReviewActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.reviewBlockGap), modifier = Modifier.testTag("review_calendars")) {
        Text(title, style = CalendarType.reviewTitle, color = Culvery.colors.ink)
        connections.forEach { connection ->
            ConnectionHeader(connection.row, nowMillis, busy, actions.onReconnect, actions.onDisconnect)
            if (confirming != null && confirming.row.connection.id == connection.row.connection.id) {
                DisconnectConfirmation(confirming, busy, actions.onKeep, actions.onConfirmDisconnect)
            }
            connection.sources.forEach { source ->
                SourceRow(source, people, busy, picking == sourceKey(source), actions.onPick, actions.onPerson, actions.onShown, actions.onMakeMaster)
            }
        }
        connectable.forEach { d -> AddButton("Connect ${d.displayName}", "settings_connect_${d.id}") { actions.onConnect(d) } }
    }
}

@Composable
private fun ConnectionHeader(row: CalendarRow, nowMillis: Long, busy: Boolean, onReconnect: (Connection) -> Unit, onDisconnect: (CalendarRow) -> Unit) {
    val c = Culvery.colors
    val needsReconnect = row.health == ConnectionHealth.NeedsSignIn
    val account = row.connection.config[CONFIG_ACCOUNT]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.settingsIconGap),
        modifier = Modifier
            .testTag("settings_row_${row.connection.id}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.settingsRowRadius))
            .background(c.surf)
            .padding(horizontal = CalendarDimens.settingsRowPaddingH, vertical = CalendarDimens.settingsRowPaddingV),
    ) {
        HhIcon(row.icon, size = CalendarDimens.settingsIcon, tint = c.ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CalendarDimens.settingsStatusTop)) {
            Text(
                if (account == null) row.service else "${row.service} · $account",
                style = CalendarType.settingsRowTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(healthWords(row, nowMillis), style = CalendarType.subtitle, color = if (needsReconnect) c.danger else c.mute, maxLines = 1)
        }
        if (needsReconnect) AddButton("Reconnect", "settings_reconnect", icon = null) { onReconnect(row.connection) }
        HhPillButton(DISCONNECT, { onDisconnect(row) }, Modifier.testTag("review_disconnect_${row.connection.id}"), enabled = !busy)
    }
}

@Composable
private fun SourceRow(
    source: StoredSource,
    people: List<Person>,
    busy: Boolean,
    picking: Boolean,
    onPick: (String) -> Unit,
    onPerson: (StoredSource, Person) -> Unit,
    onShown: (StoredSource, Boolean) -> Unit,
    onMakeMaster: (StoredSource) -> Unit,
) {
    val c = Culvery.colors
    val id = source.source.id
    // A person removed since is Family until HouseholdFollower catches up.
    val person = people.firstOrNull { it.id == source.mapping.person } ?: Person.Family
    Column(
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.reviewItemGap),
        modifier = Modifier
            .testTag("review_source_$id")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.reviewRowRadius))
            .background(c.surf)
            .padding(horizontal = CalendarDimens.reviewRowPaddingH, vertical = CalendarDimens.reviewRowPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reviewItemGap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CalendarDimens.settingsStatusTop)) {
                Text(source.source.name, style = CalendarType.settingsRowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (source.isMaster) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reviewItemGap)) {
                        Text(
                            MASTER,
                            style = CalendarType.badge,
                            color = c.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(CalendarDimens.badgeRadius))
                                .background(c.accentSoft)
                                .padding(horizontal = CalendarDimens.badgePaddingH, vertical = CalendarDimens.badgePaddingV),
                        )
                        Text(NEW_EVENTS_GO_HERE, style = CalendarType.subtitle, color = c.mute)
                    }
                } else if (source.source.writable) {
                    HhPillButton(MAKE_MASTER, { onMakeMaster(source) }, Modifier.testTag("review_make_master_$id"), enabled = !busy)
                }
            }
            HhChoiceChip(person.name, selected = false, tag = "review_person_$id", onClick = { onPick(sourceKey(source)) }, leading = { _ -> Dot(person) })
            Text(SHOW, style = CalendarType.subtitle, color = c.mute)
            // The master is always shown (4a design §3.9).
            HhSwitch(source.mapping.visible, { onShown(source, it) }, tag = "review_show_$id", enabled = !source.isMaster && !busy)
        }
        if (picking) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap), verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
                people.forEach { p ->
                    HhChoiceChip(
                        p.name,
                        selected = p.id == source.mapping.person,
                        tag = "review_pick_${p.name}",
                        onClick = { onPerson(source, p) },
                        enabled = !busy,
                        selectedColor = Color(p.color),
                        selectedInk = DarkColors.bg,
                        leading = { _ -> Dot(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Dot(person: Person) {
    Box(Modifier.size(ControlTokens.chipDot).clip(CircleShape).background(Color(person.color)))
}

@Composable
private fun DisconnectConfirmation(confirming: Confirming, busy: Boolean, onKeep: () -> Unit, onConfirm: (CalendarRow) -> Unit) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.confirmGap),
        modifier = Modifier
            .testTag("review_confirm")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.confirmRadius))
            .background(c.dangerSoft)
            .padding(CalendarDimens.confirmPadding),
    ) {
        Text(disconnectQuestion(confirming.row.service, confirming.queued), style = CalendarType.confirmTitle, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.confirmButtonGap), modifier = Modifier.fillMaxWidth()) {
            HhSheetButton(KEEP, ButtonTone.Quiet, enabled = !busy, tag = "review_keep", onClick = onKeep, modifier = Modifier.weight(1f))
            HhSheetButton(
                DISCONNECT, ButtonTone.Destroy, enabled = !busy, tag = "review_confirm_disconnect",
                onClick = { onConfirm(confirming.row) }, modifier = Modifier.weight(1f),
            )
        }
    }
}
```

- [ ] **Step 5: Give the capability its steps and page**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`:
1. Add the constructor parameter `private val review: CalendarReview,` after `connections`.
2. Remove the import `uk.co.siland.culvery.capability.calendar.ui.CalendarSettingsHost` and the whole `SettingsSection()` override.
3. Add the imports `uk.co.siland.culvery.core.plugin.SettingsPage`, `uk.co.siland.culvery.core.plugin.SetupStep`, and after `override val hasTab …`:
```kotlin

    private val steps: List<SetupStep> = listOf(
        CalendarConnectStep(repo, connections),
        ReviewCalendarsStep(repo, review, connections, clock),
    )
    private val pages: List<SettingsPage> = listOf(CalendarsPage(review, connections, clock))

    override fun setupSteps(): List<SetupStep> = steps

    override fun settingsPages(): List<SettingsPage> = pages
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS (new screenshots not yet recorded).

- [ ] **Step 7: Record the screenshots and look at them**

Run: `./gradlew :capability:calendar:recordRoborazziDebug --tests "*ReviewScreenshotTest*"`
Open and check:
- `review_ok_{dark,light}`: "Your calendars"; a `surf` connection row with the calendar icon, "Google Calendar · family@example.com", "Synced 2 min ago" in `mute`, and **Disconnect** on the right; then three calendar rows: "Family" with an `accentSoft` "Master" badge and "New events go here", a Family chip with its amber dot, "Show" and a faded-on switch; "Mia's swimming" with a Mia chip (pink dot), Show on; "Chores" with **Make master**, a Family chip, Show off;
- `review_reconnect_{dark,light}`: "Needs reconnecting" in `danger` and an `accent` **Reconnect** before Disconnect;
- `review_person_picker_dark`: under "Mia's swimming", a row of chips Family, Sam, Mia, with Mia filled pink and `bg`-coloured ink;
- `review_disconnect_dark`: under the connection row, a `dangerSoft` card "Disconnect Google Calendar? Its calendars leave the tablet, and 3 changes still waiting to sync are dropped." with **Keep** (`surf`) and **Disconnect** (`danger`);
- `connect_step_{dark,light}`: the Home screen's Connect card, 380×460, with **Connect Google Calendar**;
- `settings_calendars_page_{dark,light}`: the Settings frame with "Calendars" chosen and the review list on the right.
- `git status` shows the six `settings_calendars_*` baselines deleted and no other changed baseline.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. (Hilt isn't validated until `:app` depends on `:core:setup` in Task 12; `CalendarReview` needs only bindings the calendar already has.)

- [ ] **Step 9: Commit**

```bash
git add capability/calendar
git commit -m "Review calendars in the wizard and Settings: who each is for, show or hide, the master, and disconnect"
```

---

### Task 12: `:app` — the wizard or the shell, touches that keep Settings open, lock-task as soon as setup completes, and the new Settings (§3.3, §3.5, §3.6, D5, D8, D9, D10)

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/uk/co/siland/culvery/SetupWiring.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/MainActivity.kt`, `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt`
- Delete: `app/src/main/java/uk/co/siland/culvery/shell/ui/SettingsPlaceholder.kt`, `app/src/test/screenshots/settings_dark.png`
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Capability.kt` (`SettingsSection()` removed)
- Modify: `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/SetupWiringTest.kt`, `AppContentTest.kt` (create)

**Interfaces:**
- Consumes: `SetupState` (Task 1); `SetupWizard`, `SetupSessionGate` (Task 7); `SetupModule` (Tasks 9, 10); `SettingsScreen` (Task 10); `OpenMeteoModule` (Task 5); `Capability.setupSteps()`/`settingsPages()` (Tasks 1, 11); `AccessControl.touch()` (Task 4); `FakeAccessControl.touches` (Task 4).
- Produces:
  - `internal fun wizardSteps(core: Set<SetupStep>, capabilities: Set<Capability>): List<SetupStep>`; `internal fun settingsPages(core: Set<SettingsPage>, capabilities: Set<Capability>): List<SettingsPage>` — each sorted by `order`
  - `internal fun shouldPin(setupComplete: Boolean, kioskExited: Boolean): Boolean` (on resume)
  - `internal fun pinOnSetupRead(previous: Boolean?, now: Boolean, resumed: Boolean, kioskExited: Boolean): Boolean` — pin when `setupComplete` turns true (the first read included) while resumed
  - `internal fun touchTarget(complete: Boolean?, settingsOpen: Boolean, access: AccessControl): (() -> Unit)?` — `access::touch` while the wizard shows or Settings is open, else null
  - `@Composable internal fun AppContent(complete: Boolean?, settingsOpen: Boolean, overlay: OverlayHost, wizard: @Composable () -> Unit, shell: @Composable () -> Unit, settings: @Composable () -> Unit)` — null: a blank `bg` (tag `app_blank`); false: the wizard; true: the shell, and Settings over it when open; Settings closing dismisses the overlay
  - `ShellLayers(overlay, toast, onToastHidden, pinPad, onTouch: (() -> Unit)? = null, shell)` — every touch anywhere in the layers (sheets and PIN pads included) goes to [onTouch] first
  - `Capability` no longer has `SettingsSection()`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/uk/co/siland/culvery/SetupWiringTest.kt`:
```kotlin
package uk.co.siland.culvery

import androidx.compose.runtime.Composable
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.shell.FakeAccessControl
import uk.co.siland.culvery.shell.FakeCapability

private class Step(override val id: String, override val order: Int) : SetupStep {
    override val done: Flow<Boolean> = flowOf(false)

    @Composable
    override fun Content(onNext: () -> Unit) = Unit
}

private class Page(override val id: String, override val order: Int) : SettingsPage {
    override val title = id

    @Composable
    override fun Content() = Unit
}

private class WithSetup(private val steps: List<SetupStep>, private val pages: List<SettingsPage>) : Capability {
    override val id = "calendar"
    override val label = "Calendar"
    override val icon = "calendar_month"
    override val order = 10
    override val hasTab: Flow<Boolean> = flowOf(false)
    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())
    override fun setupSteps() = steps
    override fun settingsPages() = pages

    @Composable
    override fun TabContent() = Unit
}

class SetupWiringTest {
    private val calendar = WithSetup(listOf(Step("calendar.review", 410), Step("calendar.connect", 400)), listOf(Page("calendars", 400)))

    @Test
    fun theWizardRunsTheCoreStepsAndEachCapabilitysInOrder() {
        val core = setOf(Step("done", 1000), Step("welcome", 0), Step("household", 300), Step("you", 200), Step("location", 100))
        assertThat(wizardSteps(core, setOf(calendar)).map { it.id })
            .containsExactly("welcome", "location", "you", "household", "calendar.connect", "calendar.review", "done").inOrder()
    }

    @Test
    fun settingsListsTheCorePagesAndEachCapabilitysInOrder() {
        val core = setOf(Page("kiosk", 900), Page("location", 0), Page("people", 100))
        assertThat(settingsPages(core, setOf(calendar)).map { it.id }).containsExactly("location", "people", "calendars", "kiosk").inOrder()
    }

    @Test
    fun aCapabilityWithoutStepsOrPagesAddsNone() {
        val plain = FakeCapability("lights", order = 20, shown = true)
        assertThat(wizardSteps(setOf(Step("welcome", 0)), setOf(plain)).map { it.id }).containsExactly("welcome")
        assertThat(settingsPages(emptySet(), setOf(plain))).isEmpty()
    }

    @Test
    fun onResumeTheKioskPinsOnlyOnceSetupIsCompleteAndNotAfterExitKiosk() {
        assertThat(shouldPin(setupComplete = false, kioskExited = false)).isFalse()
        assertThat(shouldPin(setupComplete = true, kioskExited = false)).isTrue()
        assertThat(shouldPin(setupComplete = true, kioskExited = true)).isFalse()
    }

    @Test
    fun theKioskPinsAsSoonAsSetupCompletesWhileResumed() {
        // Open Culvery on Done: false then true, with the activity in front.
        assertThat(pinOnSetupRead(previous = false, now = true, resumed = true, kioskExited = false)).isTrue()
        // An existing install's first read, after onResume has run.
        assertThat(pinOnSetupRead(previous = null, now = true, resumed = true, kioskExited = false)).isTrue()
        // Not behind the scenes, not after Exit kiosk, not for a read that changed nothing, never for the wizard.
        assertThat(pinOnSetupRead(previous = false, now = true, resumed = false, kioskExited = false)).isFalse()
        assertThat(pinOnSetupRead(previous = false, now = true, resumed = true, kioskExited = true)).isFalse()
        assertThat(pinOnSetupRead(previous = true, now = true, resumed = true, kioskExited = false)).isFalse()
        assertThat(pinOnSetupRead(previous = null, now = false, resumed = true, kioskExited = false)).isFalse()
    }

    @Test
    fun touchesCountWhileSettingsIsOpenOrTheWizardShows() {
        val access = FakeAccessControl()
        assertThat(touchTarget(complete = null, settingsOpen = false, access = access)).isNull()
        assertThat(touchTarget(complete = true, settingsOpen = false, access = access)).isNull()
        touchTarget(complete = true, settingsOpen = true, access = access)!!.invoke()
        touchTarget(complete = false, settingsOpen = false, access = access)!!.invoke()
        assertThat(access.touches).isEqualTo(2)
    }
}
```

Create `app/src/test/java/uk/co/siland/culvery/AppContentTest.kt`:
```kotlin
package uk.co.siland.culvery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.shell.FakeAccessControl
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ui.ShellLayers
import androidx.compose.foundation.layout.PaddingValues

@RunWith(AndroidJUnit4::class)
class AppContentTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()
    private val access = FakeAccessControl()
    private var complete by mutableStateOf<Boolean?>(null)
    private var settingsOpen by mutableStateOf(false)

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) {
            ShellLayers(
                overlay = overlay,
                toast = null,
                onToastHidden = {},
                pinPad = {},
                onTouch = touchTarget(complete, settingsOpen, access),
            ) {
                AppContent(
                    complete = complete,
                    settingsOpen = settingsOpen,
                    overlay = overlay,
                    wizard = { Text("Wizard") },
                    shell = { Text("Shell") },
                    settings = { Box(Modifier.fillMaxSize().testTag("settings_stand_in")) },
                )
            }
        }
    }

    @Test
    fun untilSetupIsKnownNothingShows() {
        show()
        compose.onNodeWithTag("app_blank").assertExists()
        compose.onNodeWithText("Wizard").assertDoesNotExist()
        compose.onNodeWithText("Shell").assertDoesNotExist()
    }

    @Test
    fun anIncompleteSetupShowsTheWizard() {
        complete = false
        show()
        compose.onNodeWithText("Wizard").assertExists()
        compose.onNodeWithText("Shell").assertDoesNotExist()
    }

    @Test
    fun aCompleteSetupShowsTheShellAndSettings() {
        complete = true
        show()
        compose.onNodeWithText("Shell").assertExists()
        compose.onNodeWithTag("settings_stand_in").assertDoesNotExist()
        settingsOpen = true
        compose.onNodeWithTag("settings_stand_in").assertExists()
    }

    @Test
    fun aTapInsideAnOpenSheetKeepsSettingsOpen() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle {
            overlay.show { HhSheet(PaddingValues()) { Text("Sheet", Modifier.testTag("sheet_text")) } }
        }
        compose.onNodeWithTag("sheet_text").performClick()
        assertThat(access.touches).isEqualTo(1)
    }

    @Test
    fun closingSettingsDismissesItsSheet() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.onNodeWithText("Sheet").assertExists()
        settingsOpen = false
        compose.onNodeWithText("Sheet").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
    }

    @Test
    fun withSettingsClosedTouchesDoNothing() {
        complete = true
        show()
        compose.onNodeWithText("Shell").performClick()
        assertThat(access.touches).isEqualTo(0)
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*SetupWiringTest*" --tests "*AppContentTest*"`
Expected: FAIL to compile with "Unresolved reference 'wizardSteps'", "'settingsPages'", "'shouldPin'", "'pinOnSetupRead'", "'touchTarget'", "'AppContent'", and "No parameter with name 'onTouch' found" for `ShellLayers`.

- [ ] **Step 3: Wire the modules, the rules and the content**

In `app/build.gradle.kts`, after `implementation(project(":core:access"))` add:
```kotlin
    implementation(project(":core:setup"))
```
and after `implementation(project(":provider:calendar-google"))` add:
```kotlin
    implementation(project(":provider:weather-openmeteo"))
```

Create `app/src/main/java/uk/co/siland/culvery/SetupWiring.kt`:
```kotlin
package uk.co.siland.culvery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.ui.Culvery

/** The wizard's steps: the core ones and each capability's, by order (4a design D7). */
internal fun wizardSteps(core: Set<SetupStep>, capabilities: Set<Capability>): List<SetupStep> =
    (core + capabilities.flatMap { it.setupSteps() }).sortedBy { it.order }

/** Settings' pages: the core ones and each capability's, by order (4a design §4.6). */
internal fun settingsPages(core: Set<SettingsPage>, capabilities: Set<Capability>): List<SettingsPage> =
    (core + capabilities.flatMap { it.settingsPages() }).sortedBy { it.order }

/** 4a design D10: on resume, the kiosk pins once setup is complete, and not while the household has exited it. */
internal fun shouldPin(setupComplete: Boolean, kioskExited: Boolean): Boolean = setupComplete && !kioskExited

/**
 * Ruling 16: `setupComplete` turning true (the first read included, which can land after onResume) pins at once if
 * the activity is in front, so Open Culvery locks the tablet straight away.
 */
internal fun pinOnSetupRead(previous: Boolean?, now: Boolean, resumed: Boolean, kioskExited: Boolean): Boolean =
    now && previous != true && resumed && !kioskExited

/** 4a design D5, D9: touches restart the session while Settings is open or the wizard shows (the setup session's ten minutes). */
internal fun touchTarget(complete: Boolean?, settingsOpen: Boolean, access: AccessControl): (() -> Unit)? =
    if (complete == false || (complete == true && settingsOpen)) access::touch else null

/**
 * What the app shows (4a design §3.3): nothing until SetupState is read, the wizard until setup is complete, then the
 * shell with Settings over it when open. Settings closing (its session ended, or Close) takes any sheet it opened with it.
 */
@Composable
internal fun AppContent(
    complete: Boolean?,
    settingsOpen: Boolean,
    overlay: OverlayHost,
    wizard: @Composable () -> Unit,
    shell: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    LaunchedEffect(settingsOpen) { if (!settingsOpen) overlay.dismiss() }
    when (complete) {
        null -> Box(Modifier.fillMaxSize().testTag("app_blank").background(Culvery.colors.bg))
        false -> wizard()
        true -> {
            shell()
            if (settingsOpen) settings()
        }
    }
}
```
(The effect runs once at start too, with nothing to dismiss; a calendar sheet opened later from a tab isn't touched, as `settingsOpen` doesn't change.)

In `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt`:
1. Add the imports `androidx.compose.foundation.gestures.awaitEachGesture`, `androidx.compose.foundation.gestures.awaitFirstDown`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.rememberUpdatedState`, `androidx.compose.ui.input.pointer.PointerEventPass`, `androidx.compose.ui.input.pointer.pointerInput`.
2. Replace `ShellLayers` with:
```kotlin
/**
 * The shell with its overlay layers stacked over it: sheet, then [pinPad] over the sheet, then toasts over everything.
 * The layers need a parent with its own graphics layer: removing a node redraws only its nearest layered ancestor,
 * and the composition root has none, so a layer closed with no other animation running (a scrim tap, a toast timing
 * out) would get a layout pass but no new frame and stay on screen. Every touch anywhere in them, sheets and PIN pads
 * included, goes to [onTouch] before anything handles it (4a design D5: Settings stays open while used).
 */
@Composable
fun ShellLayers(
    overlay: OverlayState,
    toast: ToastMessage?,
    onToastHidden: (Long) -> Unit,
    pinPad: @Composable () -> Unit,
    onTouch: (() -> Unit)? = null,
    shell: @Composable () -> Unit,
) {
    val touched by rememberUpdatedState(onTouch)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {}
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touched?.invoke()
                }
            },
    ) {
        shell()
        OverlayLayer(overlay)
        pinPad()
        ToastLayer(toast, onHidden = onToastHidden)
    }
}
```

- [ ] **Step 4: Show the wizard or the shell**

Replace `app/src/main/java/uk/co/siland/culvery/MainActivity.kt` with:
```kotlin
package uk.co.siland.culvery

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.ui.PinPadHost
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.SettingsScreen
import uk.co.siland.culvery.core.setup.SetupSessionGate
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.SetupWizard
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ShellToasts
import uk.co.siland.culvery.shell.ShellViewModel
import uk.co.siland.culvery.shell.ui.CulveryShell
import uk.co.siland.culvery.shell.ui.ShellLayers

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    @Inject lateinit var pinPrompt: PinPromptController
    @Inject lateinit var capabilities: Set<@JvmSuppressWildcards Capability>
    @Inject lateinit var toasts: ShellToasts
    @Inject lateinit var access: AccessControl
    @Inject lateinit var setupState: SetupState
    @Inject lateinit var gate: SetupSessionGate
    @Inject lateinit var coreSteps: Set<@JvmSuppressWildcards SetupStep>
    @Inject lateinit var corePages: Set<@JvmSuppressWildcards SettingsPage>

    // Set by Settings › Exit kiosk; cleared when the app comes back to the foreground.
    private var kioskExited = false

    // Last known from SetupState (4a design D10); false until the first read.
    private var setupComplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this) { }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                shell.kioskExit.collect {
                    kioskExited = true
                    unpinFromScreen()
                    showSystemBars()
                    moveTaskToBack(true)
                }
            }
        }
        lifecycleScope.launch {
            var previous: Boolean? = null
            setupState.setupComplete.collect { complete ->
                setupComplete = complete
                val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (pinOnSetupRead(previous, complete, resumed, kioskExited)) pinToScreen()
                previous = complete
            }
        }
        setContent {
            val state by shell.uiState.collectAsStateWithLifecycle()
            val toast by toasts.current.collectAsStateWithLifecycle()
            val complete by setupState.setupComplete.collectAsStateWithLifecycle<Boolean?>(initialValue = null)
            val overlay = remember { OverlayState() }
            val steps = remember { wizardSteps(coreSteps, capabilities) }
            val pages = remember { settingsPages(corePages, capabilities) }
            CompositionLocalProvider(
                LocalShellNavigator provides shell,
                LocalOverlayHost provides overlay,
            ) {
                CulveryTheme(dark = state.dark) {
                    ShellLayers(
                        overlay = overlay,
                        toast = toast,
                        onToastHidden = toasts::hide,
                        pinPad = { PinPadHost(pinPrompt, overSheet = overlay.isShowing) },
                        onTouch = touchTarget(complete, state.settingsOpen, access),
                    ) {
                        AppContent(
                            complete = complete,
                            settingsOpen = state.settingsOpen,
                            overlay = overlay,
                            wizard = { SetupWizard(steps, gate) },
                            shell = {
                                CulveryShell(
                                    state = state,
                                    onSelectTab = shell::selectTab,
                                    onOpenSettings = shell::openSettings,
                                    onSignOut = shell::signOut,
                                    onToggleThemePreview = shell::toggleThemePreview,
                                    tabContent = { id -> capabilities.firstOrNull { it.id == id }?.TabContent() },
                                )
                            },
                            settings = { SettingsScreen(pages, onClose = shell::closeSettings) },
                        )
                    }
                }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        kioskExited = false
    }

    override fun onResume() {
        super.onResume()
        if (!kioskExited) hideSystemBars()
        if (shouldPin(setupComplete, kioskExited)) pinToScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !kioskExited) hideSystemBars()
    }
}
```
(`SetupState` already catches a failed read of its file, Task 1, so these collectors see a value, never an `IOException`.)

- [ ] **Step 5: Retire the placeholder and the old Settings hook**

```bash
git rm app/src/main/java/uk/co/siland/culvery/shell/ui/SettingsPlaceholder.kt app/src/test/screenshots/settings_dark.png
```

In `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`, delete the `settingsDark` test and the now-unused `SettingsPlaceholder` import. Settings' own screenshots live in `:core:setup` (Task 10) and the calendar (Task 11).

In `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Capability.kt`, delete `SettingsSection()` with its KDoc. Check nothing still uses it:
```bash
grep -rn "SettingsSection\|SettingsPlaceholder" --include=*.kt .
```
Expected: no output.

- [ ] **Step 6: Run the tests and build both variants**

Run: `./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`; `ShellLayersRedrawTest` and the shell's baselines are unchanged (the touch observer draws nothing). Hilt now resolves `SetupState`, `SetupSessionGate`, the five core steps, the three core pages, `Optional<SampleHousehold>` (empty until Task 13), `LocationSearch` (`OpenMeteoModule`), `PeopleEditor`, `CalendarReview` and `HouseholdFollower`. If Hilt names a missing binding, it is one of these; stop and report rather than adding a binding this plan doesn't list.

Until Task 13, a fresh debug install still seeds Alex at start and may skip the wizard; don't judge the wizard on the emulator before then.

- [ ] **Step 7: Commit**

```bash
git add app core/plugin
git commit -m "Open the setup wizard until setup is complete, keep Settings open while it or its sheets are touched, and pin the kiosk as soon as setup completes"
```

---

### Task 13: The debug seed reworked, and **Use a sample household** (§3.11, D11)

**Files:**
- Modify: `app/src/debug/java/uk/co/siland/culvery/DebugSeed.kt`, `app/src/release/java/uk/co/siland/culvery/DebugSeed.kt`, `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`
- Create: `app/src/debug/java/uk/co/siland/culvery/di/DebugSetupModule.kt`
- Test: `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt` (rewrite), `SampleAddTest.kt`, `SampleRollbackTest.kt` (modify)

**Interfaces:**
- Consumes: `SampleHousehold`, `SetupState.markComplete()`, `@BindsOptionalOf SampleHousehold` (Tasks 1, 9); `PersonPalette` (Task 2); `PinManager.addPerson` (Task 3); `CalendarSetup.connect`/`setMaster`/`connectionIds`/`syncSoon`, `FakeCalendarProvider.tagSamples` (3a).
- Produces:
  - `suspend fun seedDebugData(household: HouseholdRepository, calendar: CalendarSetup, providers: Set<CalendarProvider>)` — creates nobody; re-tags the sample week once the sample calendar is connected (a no-op in release)
  - `class DebugSampleHousehold(household, pins: PinManager, calendar: CalendarSetup, providers: Set<CalendarProvider>, markComplete: suspend () -> Unit) : SampleHousehold`, `@Inject` constructor `(household, pins, calendar, providers, state: SetupState)` (debug only)
  - `internal val SAMPLE_HOME = HomeLocation("London, England, United Kingdom", 51.5074, -0.1278, "Europe/London")`; `internal const val DEBUG_CONNECTION_ID = "debug-sample"`
  - `DebugSetupModule` binds `SampleHousehold` (debug only)

- [ ] **Step 1: Write the failing tests**

Replace `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt` with:
```kotlin
package uk.co.siland.culvery

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.ChangeKind
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.PendingChange
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.setupStore
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

private object NoToasts : Toaster {
    override fun show(message: String, icon: String) = Unit
}

@RunWith(AndroidJUnit4::class)
class DebugSeedTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var state: SetupState
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var calendarDb: CalendarDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager
    private lateinit var store: CalendarStore
    private val fake = FakeCalendarProvider()
    private var syncs = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        calendarDb = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).allowMainThreadQueries().build()
        household = HouseholdRepository(householdDb)
        pins = PinManager(household, PinHasher())
        store = CalendarStore(calendarDb)
        state = SetupState(setupStore(scope) { File(folder.root, "setup.preferences_pb") }, household)
    }

    @After
    fun tearDown() {
        scope.cancel()
        householdDb.close()
        calendarDb.close()
    }

    private fun setupFor(provider: FakeCalendarProvider) =
        CalendarSetup(store, setOf(provider), { household.people.first() }, NoToasts, WallClock { 0L }) { syncs++ }

    private suspend fun seed(provider: FakeCalendarProvider = fake) = seedDebugData(household, setupFor(provider), setOf(provider))

    private suspend fun sampleHousehold() = DebugSampleHousehold(household, pins, setupFor(fake), setOf(fake), state::markComplete).create()

    @Test
    fun aFreshDebugInstallHasNoPeopleNoCalendarAndIsNotSetUp() = runTest {
        seed()
        assertThat(household.people.first()).isEmpty()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(store.master().first()).isNull()
        // So the app opens the wizard.
        assertThat(state.setupComplete.first()).isFalse()
    }

    @Test
    fun theSampleHouseholdIsTheOneTheOldSeedMadeAndCompletesSetup() = runTest {
        sampleHousehold()
        val members = household.members.first()
        assertThat(members.map { Triple(it.person.name, it.person.color, it.role) }).containsExactly(
            Triple("Alex", PersonPalette.colors[0], Role.ADMIN),
            Triple("Sam", PersonPalette.colors[1], Role.ADULT),
            Triple("Mia", PersonPalette.colors[2], Role.CHILD),
        ).inOrder()
        assertThat(pins.identify("1234")?.person?.name).isEqualTo("Alex")
        assertThat(pins.identify("2468")?.person?.name).isEqualTo("Sam")
        assertThat(pins.identify("1357")?.person?.name).isEqualTo("Mia")
        assertThat(household.location.first()).isEqualTo(HomeLocation("London, England, United Kingdom", 51.5074, -0.1278, "Europe/London"))
        assertThat(state.setupComplete.first()).isTrue()
    }

    @Test
    fun sampleSourcesAreMappedToThePeopleWithTheFamilyCalendarAsTheWritableMaster() = runTest {
        sampleHousehold()
        val byName = household.people.first().associate { it.name to it.id }
        val sources = store.visibleSourcesFor(DEBUG_CONNECTION_ID)
        assertThat(sources.associate { it.source.id to it.mapping.person }).containsExactly(
            FakeCalendarProvider.SOURCE_ALEX, byName.getValue("Alex"),
            FakeCalendarProvider.SOURCE_SAM, byName.getValue("Sam"),
            FakeCalendarProvider.SOURCE_MIA, byName.getValue("Mia"),
            FakeCalendarProvider.SOURCE_FAMILY, PersonId.FAMILY,
            FakeCalendarProvider.SOURCE_SCHOOL, PersonId.FAMILY,
        )
        val master = store.master().first()!!
        assertThat(master.source.id).isEqualTo(FakeCalendarProvider.SOURCE_FAMILY)
        assertThat(master.source.writable).isTrue()
    }

    @Test
    fun eachStartTagsTheSampleWeekWithThePeopleAndAsksForASync() = runTest {
        sampleHousehold()
        // A later start: the fake has forgotten its tags.
        val restarted = FakeCalendarProvider()
        syncs = 0
        seed(restarted)
        val alexId = household.people.first().single { it.name == "Alex" }.id.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val family = FakeCalendarProvider.SOURCES.single { it.id == FakeCalendarProvider.SOURCE_FAMILY }
        val events = restarted.sync(
            Connection(DEBUG_CONNECTION_ID, FakeCalendarProvider.ID, "Sample calendar", emptyMap()),
            family,
            DateRange(today.minusDays(1), today.plusDays(15), zone),
            null,
        ).upserts
        assertThat(events.single { it.title == "Dinner with Jo & Priya" }.forPerson).isEqualTo(alexId)
        assertThat(syncs).isAtLeast(1)
    }

    @Test
    fun aRealConnectionRemovesTheSampleWithEverythingItHeld() = runTest {
        sampleHousehold()
        // The sample holds events, a cursor and a queued change, as it would after a sync and an offline add.
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val range = DateRange(today.minusDays(1), today.plusDays(15), zone)
        val sample = store.connectionsNow().single().connection
        val family = FakeCalendarProvider.SOURCES.single { it.id == FakeCalendarProvider.SOURCE_FAMILY }
        store.applySync(DEBUG_CONNECTION_ID, family.id, range, fake.sync(sample, family, range, null))
        store.enqueue(PendingChange(0, DEBUG_CONNECTION_ID, family.id, "e1", ChangeKind.DELETE, null, 0, 0L, 0L))
        assertThat(sampleRows()).containsExactly("event", true, "outbox", true, "sync_state", true)
        store.addConnection(Connection("g1", "calendar.google", "Google", emptyMap()), emptyList(), emptyMap())
        val watcher = launch { removeSampleWhenReplaced(setupFor(fake)) }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (store.connectionsNow().any { it.connection.id == DEBUG_CONNECTION_ID }) delay(10) }
        }
        watcher.cancel()
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
        assertThat(sampleRows()).containsExactly("event", false, "outbox", false, "sync_state", false)
    }

    /** Whether each table still holds a row of the sample's, counted directly. */
    private suspend fun sampleRows(): Map<String, Boolean> = listOf("event", "outbox", "sync_state").associateWith { table ->
        calendarDb.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM $table WHERE connectionId = ?") { statement ->
                statement.bindText(1, DEBUG_CONNECTION_ID)
                statement.step()
                statement.getLong(0) > 0
            }
        }
    }

    @Test
    fun aStartAfterGoogleIsConnectedAddsNoSampleAndSaysNothing() = runTest {
        store.addConnection(Connection("g1", "calendar.google", "Google", emptyMap()), emptyList(), emptyMap())
        seed()
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
        assertThat(ShadowLog.getLogsForTag("Culvery")).isEmpty()
    }
}
```

In `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`, replace
```kotlin
        seedDebugData(household, pins, CalendarSetup(store, setOf(fake), { household.people.first() }, toasts, WallClock { System.currentTimeMillis() }), setOf(fake))
```
with
```kotlin
        DebugSampleHousehold(household, pins, CalendarSetup(store, setOf(fake), { household.people.first() }, toasts, WallClock { System.currentTimeMillis() }), setOf(fake)) {}.create()
```

In `app/src/testDebug/java/uk/co/siland/culvery/SampleRollbackTest.kt`, replace
```kotlin
        seedDebugData(household, PinManager(household, PinHasher()), CalendarSetup(store, setOf(fake), { household.people.first() }, toasts, WallClock { System.currentTimeMillis() }), setOf(fake))
```
with
```kotlin
        DebugSampleHousehold(
            household,
            PinManager(household, PinHasher()),
            CalendarSetup(store, setOf(fake), { household.people.first() }, toasts, WallClock { System.currentTimeMillis() }),
            setOf(fake),
        ) {}.create()
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'DebugSampleHousehold'", "Cannot access 'DEBUG_CONNECTION_ID': it is private in file", and "Too many arguments for seedDebugData" in `DebugSeedTest`.

- [ ] **Step 3: Rework the seed and add the sample household**

Replace `app/src/debug/java/uk/co/siland/culvery/DebugSeed.kt` with:
```kotlin
package uk.co.siland.culvery

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.SourceMapping
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.setup.SampleHousehold
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

private class SamplePerson(val name: String, val color: Long, val role: Role, val pin: String, val source: String)

/** The hand-off's family, in the palette's first three colours. */
private val SAMPLE_PEOPLE = listOf(
    SamplePerson("Alex", PersonPalette.colors[0], Role.ADMIN, "1234", FakeCalendarProvider.SOURCE_ALEX),
    SamplePerson("Sam", PersonPalette.colors[1], Role.ADULT, "2468", FakeCalendarProvider.SOURCE_SAM),
    SamplePerson("Mia", PersonPalette.colors[2], Role.CHILD, "1357", FakeCalendarProvider.SOURCE_MIA),
)

internal val SAMPLE_HOME = HomeLocation("London, England, United Kingdom", 51.5074, -0.1278, "Europe/London")

internal const val DEBUG_CONNECTION_ID = "debug-sample"

/**
 * Debug builds only, at every start (4a design D11): once the sample calendar is connected, re-tags its week with the
 * people's ids, which the fake keeps only in memory. It creates nobody: a fresh install runs the real wizard.
 */
suspend fun seedDebugData(household: HouseholdRepository, calendar: CalendarSetup, providers: Set<CalendarProvider>) {
    val fake = providers.filterIsInstance<FakeCalendarProvider>().singleOrNull() ?: return
    if (DEBUG_CONNECTION_ID !in calendar.connectionIds().first()) return
    fake.tagSamples(household.people.first().associate { it.name to it.id.value })
    // The tags may have changed after the start-up sync ran.
    calendar.syncSoon()
}

/**
 * Welcome's **Use a sample household** (4a design D11): Alex (Admin, 1234), Sam (Adult, 2468) and Mia (Child, 1357),
 * London, and the sample calendar with each person's calendar mapped to them and the Family calendar as the master; then
 * setup is complete, so Home opens.
 */
class DebugSampleHousehold(
    private val household: HouseholdRepository,
    private val pins: PinManager,
    private val calendar: CalendarSetup,
    private val providers: Set<CalendarProvider>,
    private val markComplete: suspend () -> Unit,
) : SampleHousehold {
    @Inject
    constructor(
        household: HouseholdRepository,
        pins: PinManager,
        calendar: CalendarSetup,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        state: SetupState,
    ) : this(household, pins, calendar, providers, state::markComplete)

    override suspend fun create() {
        val fake = providers.filterIsInstance<FakeCalendarProvider>().single()
        val ids = SAMPLE_PEOPLE.associate { p -> p.name to pins.addPerson(p.name, p.color, p.role, p.pin).id }
        household.setLocation(SAMPLE_HOME)
        fake.tagSamples(ids.mapValues { it.value.value })
        val mapping = SAMPLE_PEOPLE.associate { p -> p.source to SourceMapping(ids.getValue(p.name), visible = true) } + mapOf(
            FakeCalendarProvider.SOURCE_FAMILY to SourceMapping(PersonId.FAMILY, visible = true),
            FakeCalendarProvider.SOURCE_SCHOOL to SourceMapping(PersonId.FAMILY, visible = true),
        )
        calendar.connect(Connection(DEBUG_CONNECTION_ID, FakeCalendarProvider.ID, "Sample calendar", emptyMap()), mapping)
        try {
            calendar.setMaster(DEBUG_CONNECTION_ID, FakeCalendarProvider.SOURCE_FAMILY)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The sample still opens; events can be added once a master is chosen in Settings › Calendars.
            Log.w("Culvery", "Couldn't make the sample Family calendar the master (${e::class.simpleName})")
        }
        markComplete()
    }
}

/**
 * Debug builds only (3a design D5, §3.9): once any other connection exists, the sample connection goes, with its events,
 * sync state and queue, so sample and real events never mix.
 */
suspend fun removeSampleWhenReplaced(calendar: CalendarSetup) {
    calendar.connectionIds().collect { ids ->
        if (DEBUG_CONNECTION_ID in ids && ids.any { it != DEBUG_CONNECTION_ID }) calendar.removeConnection(DEBUG_CONNECTION_ID)
    }
}
```

Create `app/src/debug/java/uk/co/siland/culvery/di/DebugSetupModule.kt`:
```kotlin
package uk.co.siland.culvery.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.DebugSampleHousehold
import uk.co.siland.culvery.core.setup.SampleHousehold

/** Debug builds only: Welcome offers Use a sample household (4a design D11). */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugSetupModule {
    @Binds
    abstract fun sampleHousehold(impl: DebugSampleHousehold): SampleHousehold
}
```

Replace `app/src/release/java/uk/co/siland/culvery/DebugSeed.kt` with:
```kotlin
package uk.co.siland.culvery

import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.core.household.HouseholdRepository

@Suppress("UNUSED_PARAMETER")
suspend fun seedDebugData(household: HouseholdRepository, calendar: CalendarSetup, providers: Set<CalendarProvider>) = Unit

@Suppress("UNUSED_PARAMETER")
suspend fun removeSampleWhenReplaced(calendar: CalendarSetup) = Unit
```

In `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`, delete `@Inject lateinit var pins: PinManager` and its import, and replace `seedDebugData(household, pins, calendarSetup, calendarProviders)` with `seedDebugData(household, calendarSetup, calendarProviders)`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, `SampleAddTest` and `SampleRollbackTest` included.

- [ ] **Step 5: Build both variants and check the release APK**

Run: `./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

Then:
```bash
unzip -p app/build/outputs/apk/release/app-release-unsigned.apk 'classes*.dex' | grep -ac "DebugSampleHousehold"
unzip -p app/build/outputs/apk/release/app-release-unsigned.apk 'classes*.dex' | grep -ac "provider/calendar_fake"
unzip -p app/build/outputs/apk/release/app-release-unsigned.apk 'classes*.dex' | grep -ac "provider/weather_openmeteo"
unzip -p app/build/outputs/apk/release/app-release-unsigned.apk 'classes*.dex' | grep -ac "core/setup/SetupWizard"
```
Expected: `0`, `0`, then numbers above `0`. (If the release APK has another name, `ls app/build/outputs/apk/release/`.)

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "Seed nobody in debug builds and offer a sample household on Welcome instead"
```

---

### Task 14: The emulator walkthrough with the user's Google account, the USER CHECKPOINTS, the README, the setup doc and the follow-ups

**Files:**
- Modify (after the checkpoint): `README.md`, `docs/setup/google-calendar.md`, `docs/superpowers/plans/2026-09-23-plan1-followups.md`

**Interfaces:**
- Consumes: everything above; the debug build's sample calendar and `DebugOfflineReceiver`.
- Produces: documentation only.

Who does what: the implementer prepares the emulator and checks the install over the old app (Steps 1–2) and stops. **The controller** runs the walkthrough with the user (Step 3), driving the tablet with `adb` and screenshots; **the user** types their own names and PINs on the emulator (so no real PIN passes through the chat), chooses and consents to their Google account, and checks their phone. The emulator is the **already-running API 35 Google Play emulator, `emulator-5554`**; don't start or wipe another. To go offline use `adb -s emulator-5554 shell svc wifi disable` and `svc data disable` (and `enable` to come back), never airplane mode (the setup doc's Play services note).

- [ ] **Step 1: Check the emulator**

```bash
adb devices
adb -s emulator-5554 shell getprop ro.build.version.sdk
adb -s emulator-5554 shell pm list packages com.android.vending
```
Expected: `emulator-5554 device`, `35`, `package:com.android.vending`. If `adb` isn't on PATH, use `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb"`. If the emulator isn't running, stop and ask the user to start it (don't create an AVD).

- [ ] **Step 2: Install over the existing app (v4 → v5, and an existing install skipping the wizard)**

```bash
adb -s emulator-5554 shell pm list packages uk.co.siland.culvery
./gradlew :app:installDebug
adb -s emulator-5554 shell am start -n uk.co.siland.culvery/.MainActivity
adb -s emulator-5554 logcat -d | grep -iE "Migration|IllegalStateException|FATAL" | tail -20
adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4a-upgrade.png"
```
Expected: if Culvery was installed (with its Admin from the old seed or 3a's walkthrough), **Home** opens, not the wizard, with the calendar as before and no migration error. If it wasn't installed, say "upgrade not exercised on device; covered by `SetupStateTest` and `CalendarMigrationTest`". **Stop and report** Steps 1–2 with the screenshot; the implementer does not go on.

- [ ] **Step 3: STOP — the controller runs the walkthrough and the USER CHECKPOINTS with the user**

The controller, not the implementer, does this, and does not start Step 4 until the user approves. Take `adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4a-<n>.png"` at each numbered item and send them with the report. Ask the user before each **USER** action and wait for them.

Before starting, ask the user:
- that their Google account is on the emulator (Settings › Accounts) and the debug SHA-1 is still registered (unchanged since 3a);
- for their home town, and the names and roles of the people they want to set up (PINs they will type themselves);
- **USER:** to have, in the Google account, a calendar named after one of those people, ticked, with an event this week.

The walkthrough:
1. **A fresh install opens the wizard.** `adb -s emulator-5554 shell pm clear uk.co.siland.culvery`, then start it. Welcome shows "Welcome to Culvery", the line, **Start**, one dot of seven lit, and (debug) **Use a sample household** — don't tap it yet.
2. **Welcome, then a kill.** Tap **Start**: Home location. Kill and start again (`am force-stop uk.co.siland.culvery`, `am start …`): the wizard reopens at Home location, not Welcome.
3. **Home location.** Go offline (`svc wifi disable; svc data disable`), type the town: "Couldn't search for towns — check the tablet's Wi-Fi and try again."; **Skip for now** is there. Back online; clear and type the user's town: up to five rows ("Town, Region, Country") appear about half a second after typing stops; tap theirs: ticked. **Next**.
4. **You.** **USER:** type their name, pick a colour, tap **Set your PIN** and choose their PIN twice (try a mismatch first: "Those PINs didn't match — try again."). **Next** turns on only once name and PIN are set; tap it.
5. **A kill after You.** Kill and start again: the PIN pad asks "Enter your PIN to carry on setting up". **USER:** enter their PIN. The wizard opens at Household, and the list has exactly one person, them, "Admin · PIN set". Then leave the wizard untouched for 10 minutes: the same PIN pad returns (the setup session's idle limit), and after the PIN the wizard is still on Household.
6. **Household.** **USER:** add each person (name, colour, role; a PIN for adults who want one). Try a second person with a name already used: "Someone is already called {name}." in the sheet, name kept. Each saves without a PIN pad (the setup session). **Next** (or **Skip for now** if they live alone).
7. **Connect.** The Connect card: **Connect Google Calendar**. **USER:** choose the account and allow access (both boxes). Toast "Google Calendar connected"; **Next**.
8. **Review calendars.** "Your calendars" lists "Google Calendar · {email}" and each calendar, the person-named one on that person, the account's own calendar "Master · New events go here" with its switch off-limits. **USER:** change one calendar's person (toast "{calendar} now shows as {person}"), hide one ("{calendar} hidden"). **Next**.
9. **Done.** "Culvery is ready", **Open Culvery**: Home, with the calendar in the people's colours and **nobody signed in** in the status bar. Check the log holds no PIN, name, town, coordinates or email:
   ```bash
   adb -s emulator-5554 logcat -d | grep -E "OpenMeteo|People|PersonSheet|SetupWizard|YouStep|LocationPane|CalendarReview|HouseholdFollower" | tail -40
   ```
10. **Settings stays open while used.** Rail › Settings, **USER:** their PIN. Two panes: "Settings", Home location / People / Calendars / Kiosk, Close. Tap around the pages, and inside a person's sheet, every 30 s for 2½ minutes: it stays open. Then leave it: it closes about 2 minutes after the last touch.
11. **People edits.** Settings (PIN) › People: rename someone and change their colour — **Save changes**, no PIN pad. Change someone's role — a fresh PIN pad appears. Set a PIN for someone — fresh PIN pad. The status bar shows the signed-in Admin throughout, until a change to their own role or PIN signs them out.
12. **Remove someone mapped to a calendar.** Open the person the named calendar maps to, **Remove person**: "Remove {name}? {name}'s events and calendars show as Family." **Remove person**, PIN: toast "{name} removed". Settings › Calendars: that calendar now shows Family.
13. **A hide survives a restart.** Settings › Calendars: hide a calendar that is ticked in Google ("{calendar} hidden"). Kill and start again (the source refresh runs at start): it is still hidden, and its events stay off Home and the Calendar tab.
14. **Change the master.** **Make master** on another writable calendar: "New events now go to {calendar}". Add an event from Today's **+**. **USER:** check it appears in that calendar on the phone.
15. **Disconnect and reconnect.** Take the tablet offline and add an event: it queues. Still offline, tap **Disconnect**: "Disconnect Google Calendar? Its calendars leave the tablet, and 1 change still waiting to sync is dropped." **Disconnect**: toast "Google Calendar disconnected"; Home shows the Connect-a-calendar card (the 3a follow-up: the card on the emulator). Back online, tap it, **USER:** choose the account again: the calendars come back with default mappings, and the queued event never reaches the phone.
16. **Kiosk.** Settings › Kiosk: the line, and **Exit kiosk** asks for a fresh PIN (debug builds never lock the task; the release check is 4c's).
17. **The sample household.** `pm clear` again; Welcome › **Use a sample household**: Home opens on the sample week; Alex's 1234 opens Settings.

Send the user these images, dark and light, from `core/setup/src/test/screenshots/` and `capability/calendar/src/test/screenshots/`: `wizard_frame_*`, `welcome_*`, `location_*`, `you_*`, `people_list_*`, `person_*`, `done_*`, `settings_*`, `review_*`, `connect_step_*`, `settings_calendars_page_*`, and the walkthrough screenshots. Name the parts that are this plan's own design, which the spec doesn't give:
- the wizard's padding (36/32/48), 10 dp dots 10 apart, and **Back**/**Next** as the shell's pill buttons;
- rows (people, towns) `surf`, radius 18, padding 16×20, a 16 dp colour dot; "{role} · PIN set" as the second line;
- swatches 44 dp, ringed when chosen, struck through when taken; role chips carrying the whole line ("Admin — can change settings and people");
- the PIN pad for choosing a PIN has no reason line; the gate's **Enter PIN** after a cancelled pad;
- Settings' left column in `surf`, the chosen page on `accentSoft`; each page titled with its name;
- the placeholder "Town or city"; the sheet heading "Add person";
- Review calendars' "Master" badge on `accentSoft`; the Connect step's card at 380×460;
- the five extra person colours (violet, lime, red, slate, brown).

Ask: "Do these match what you want? Any changes before I update the README and the setup doc?"

- **If the user asks for changes:** make them, re-record only the affected images with `--tests`, look at them, run `./gradlew testDebugUnitTest verifyRoborazziDebug`, re-send them, and commit with a message describing the change. Repeat until approved.
- **When approved:** continue to Step 4.

- [ ] **Step 4: Update the README**

In `README.md`:
1. **Build and run.** Replace the paragraph starting "Debug builds seed a sample household on first launch" with:
```markdown
A fresh install opens the setup wizard before anything else: Welcome, Home location (town search, through Open-Meteo), You (the first Admin and their PIN), Household, Connect a calendar, Review calendars, Done. Each step saves as it goes, so a restart resumes where setup stopped; after the first Admin exists, a restart asks for their PIN once. An install from an earlier build that already has an Admin goes straight to Home. To run setup again, clear the app's data (`adb shell pm clear uk.co.siland.culvery`).

In debug builds Welcome also offers **Use a sample household**: **Alex** (Admin, PIN 1234), **Sam** (Adult, PIN 2468), **Mia** (Child, PIN 1357), London, and a "Sample calendar" connection showing the design hand-off's week, with its "Family calendar" as the master calendar. The sample calendar keeps changes in memory and forgets them when the app restarts. Release builds offer no sample and include no sample calendar.
```
2. In the paragraph starting "To connect a real Google account", replace "then Settings › Connect Google Calendar (Admin PIN)." with "then connect it in the wizard's Connect step, or later in Settings › Calendars (Admin PIN)."; replace "There is no disconnect yet: to undo a connection (the wrong account, or to get the sample back), clear the app's data." with "Settings › Calendars says who each calendar is for, shows or hides it, picks the master calendar new events go to, and disconnects (dropping its queued changes; Google keeps the grant until you remove it, see `docs/setup/google-calendar.md`). A calendar hidden on the tablet stays hidden until it is ticked or unticked again in Google Calendar."; and replace "; Plan 4's Settings will let you change it." with "; change it in Settings › Calendars."
3. **Modules.** After the `:core:access` row add:
```markdown
| `:core:setup` | The first-run wizard, two-pane Settings, the core pages (Home location, People, Kiosk), `SetupState`, `LocationSearch` |
```
   and after the `:provider:calendar-google` row add:
```markdown
| `:provider:weather-openmeteo` | Town search through Open-Meteo's geocoding (no key); the forecast comes in Plan 4b |
```
   In the `:core:plugin` row, add `SetupStep`, `SettingsPage` after `Capability`.
4. **`calendar.db`.** After "(the `outbox` table; the `event` table is only ever a copy of what the provider has)." add: " Since v5 each calendar also remembers its tick in the service when last seen, so the daily refresh changes whether it shows only when that tick changes."
5. **Adding a capability.** In step 2, after the sentence about `Startable`, add: "A capability can add wizard steps and Settings pages by returning `SetupStep`s and `SettingsPage`s from `setupSteps()` and `settingsPages()` (`:core:setup` places them by `order`: core steps 0–399, capabilities 400 and up, Done 1000; Settings pages Home location 0, People 100, Kiosk 900)."
6. **Kiosk mode.** Replace "Release builds pin the app to the screen (Android "screen pinning"). Leave properly via **Settings › Exit kiosk** (Admin PIN, always asked)." with "Release builds pin the app to the screen (Android "screen pinning") once setup is complete, so the first Google connection happens outside it. Leave properly via **Settings › Kiosk › Exit kiosk** (Admin PIN, always asked)."
7. **PINs.**
   - Replace "A session lasts 2 minutes after the last PIN-checked action; touching the screen doesn't extend it." with "A session lasts 2 minutes after the last PIN-checked action; while Settings is open, every touch in it (its sheets and PIN pads included) restarts the 2 minutes. During setup the first Admin stays signed in until Done, or until 10 minutes pass without a touch; the wizard then asks for their PIN to carry on."
   - Replace "Exiting kiosk and managing people always ask for a PIN, even mid-session." with "Exiting kiosk, adding or removing someone, changing a role and setting, changing or removing a PIN always ask for a PIN, even mid-session; renaming and recolouring don't. Changing your own role or PIN signs you out."
   - Replace "**Forgotten PIN:** an Admin can reset anyone's PIN in Settings." with "**Forgotten PIN:** an Admin can reset anyone's PIN in Settings › People."
   - After the first bullet add: "- Each person has one of eight colours, and no two share one; so a household has at most eight people. Every household must keep at least one Admin with a PIN."

- [ ] **Step 5: Update the setup doc**

In `docs/setup/google-calendar.md`, replace §5's first paragraph (from "The emulator (or tablet) needs Google Play services" to "clear Culvery's data (**Settings › Apps › Culvery › Storage**) and connect again.") with:
```markdown
The emulator (or tablet) needs Google Play services: an image with **Google Play**. Add the family's Google account under **Settings › Accounts** first, so the chooser offers it. Then connect in the setup wizard's Connect step, or later in Culvery's **Settings › Calendars** (Admin PIN): pick the account, allow calendar access (both boxes), and the household's calendars appear. Every calendar in the account is added; the account's own calendar becomes the one the tablet adds events to, and a calendar named after one person (e.g. "Mia's swimming") shows in their colour. **Review calendars** (in the wizard, and Settings › Calendars) changes who each calendar is for, shows or hides it, and picks the master calendar; a calendar hidden there stays hidden until it is ticked or unticked again in Google Calendar. If access lapses, the Calendar tab shows "Google needs reconnecting": tap it and approve again.

**Disconnect** (Settings › Calendars) removes the connection from the tablet with its calendars, events and any changes still waiting to sync. It doesn't remove Culvery's access from the Google account: do that at <https://myaccount.google.com/connections> (Security › Your connections to third-party apps), under Culvery.
```
and replace "exit kiosk (Settings › Exit kiosk), connect, and return." with "exit kiosk (Settings › Kiosk › Exit kiosk), connect, and return."

- [ ] **Step 6: Check the docs read sensibly**

Read `README.md` and `docs/setup/google-calendar.md` through once: the provider steps still number 1 to 6, the tables and code blocks are balanced, and:
```bash
grep -nE "There is no disconnect yet|Plan 4's Settings|Settings › Exit kiosk|seed a sample household|Settings › Connect Google Calendar" README.md docs/setup/google-calendar.md
```
Expected: no output.

- [ ] **Step 7: Update the follow-ups**

In `docs/superpowers/plans/2026-09-23-plan1-followups.md`:
1. Under "## For Plan 4 (weather, setup, settings, release)", delete "The session keeps a snapshot of the person … The people editor should call `AccessControl.lock()`.", "Read `addPerson`'s `sortOrder` inside a transaction. Add tests that `setRole`, `setPinHash` and `clearPin` reject Family." and "Make the debug seed check for an active Admin rather than an empty household. Remove the debug Admin when the wizard creates the first real one." (Tasks 3, 8, 13).
2. Under "## From Plan 2a review (deferred)" → **For Plan 4**, delete "All-day events straddle two days after a household zone change until the next sync: filter all-day events by date." only if the walkthrough's zone change (item 3 moves the zone from the tablet's to the town's) showed no straddled all-day event; otherwise leave it. The zone-change resync (spec §8) is `HouseholdFollower`.
3. Under "## From Plan 2b-1 (deferred)" → **For Plan 4**, delete "m5: Settings closes when the session ends, …" (Tasks 4, 10).
4. Under "## From Plan 3a review (deferred)" → **For Plan 4**, delete "H2: when a connection has no master …" (the toast now points to Settings › Calendars, where **Make master** is: Tasks 6, 11). Leave L3, M4, M7, L4 and L6 (4c).
5. Under "## From Plan 3a (deferred)" → **For Plan 4**, delete "The Connect-a-calendar card's Google button was checked by tests only …" (walkthrough item 15) and "Settings: disconnecting a connection, and editing mappings, visibility and the master (3a design §10)." (Task 11). Leave every other **For Plan 4** item where it is: each is 4b's (weather, the theme schedule) or 4c's (release and the on-device pass), as spec §8 lists.
6. Add at the end of the file:
```markdown
## From Plan 4a (deferred)

**For Plan 4c**
- Reordering people (4a design D15).
- Per-calendar health in Settings › Calendars (M7) and the repeat-series cap stay 4c's (4a design D15).
- The PIN pad for choosing a PIN has no reason line; check on the SM-T510 that its two stages read clearly.
- The wizard's steps and Settings' pages on the SM-T510 with the Samsung keyboard up (the town search, the person sheet's name field).
- The choose-a-PIN pad (and a sheet's Save) says "That PIN is taken — choose another." when someone else has the PIN, so an Admin can learn that a PIN is in use. Accepted: only an Admin (with a fresh PIN) gets there, and it follows from PINs being unique, since a PIN identifies its person.
- `DebugSampleHousehold.create()` still completes when making the Family calendar the master fails, but no test forces that failure: the fake has no hook to fail its second calendar-list read alone.
```
   and add under it any item you or the user noted during this plan that was deferred rather than fixed.

- [ ] **Step 8: Commit**

```bash
git add README.md docs/setup/google-calendar.md docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Document the setup wizard, Settings, disconnecting and the new modules"
```

---

## Spec coverage (4a design → tasks)

| Design | Where |
|---|---|
| §1 scope; D1 the split (4a, then 4b weather, 4c release) | Tasks 1–14; 4b/4c items stay in the follow-ups (Task 14 Step 7) |
| D2 home location by town search, no key; Skip for now keeps the tablet's zone | Tasks 5, 9 (`HouseholdZone`'s fallback is 3a's) |
| D3 the wizard reviews calendars after connecting | Task 11 (`ReviewCalendarsStep`) |
| D4 Settings is two panes; each right pane is the wizard's matching step | Tasks 10, 11 |
| D5 Settings closes 2 minutes after the last touch (m5), sheets and PIN pads included | Tasks 4 (`touch`), 12 (`ShellLayers`' observer, `touchTarget`, Settings closing dismisses its sheet) |
| D6 fresh PIN for remove, role and PIN changes; rename and recolour on the open session | Task 8 (adding too: ruling 6) |
| D7 capabilities contribute steps and pages | Tasks 1, 11, 12 |
| D8 the wizard runs while setup isn't complete; each step saves; resume; an existing install is complete | Tasks 1 (`SetupState`, ruling 2), 7 (`resumeAt`), 9 (each step's `done`), 12 |
| D9 (amended) no PIN before an Admin; the setup session until Done or 10 minutes without a touch, then the PIN gate | Tasks 4 (`SETUP_IDLE_MS`), 7 (gate), 9 (You, Location, Done), 12 (touches in the wizard) |
| D10 lock-task only after setup, as soon as it completes | Task 12 (`shouldPin`, `pinOnSetupRead`, ruling 16) |
| D11 the debug seed creates nobody; Use a sample household | Tasks 9 (`SampleHousehold`, Welcome), 13 |
| D12 eight colours, a colour in use can't be picked, Family's amber, at most eight people | Tasks 2, 3, 8 |
| D13 mapping changes are tablet-only and immediate; the refresh follows a changed tick only | Tasks 6, 11 |
| D14 disconnect with a count of queued changes; the grant stays; the setup doc says where to revoke | Tasks 6 (`queuedChanges`), 11, 14 (setup doc) |
| D15 deferred | Task 14 Step 7 |
| §3.1 `:core:setup`, `SetupState`, `LocationSearch`/`PlaceMatch`/`LocationSearchException`, core steps and pages by `@IntoSet` | Tasks 1, 5, 9, 10 |
| §3.2 `SetupStep`, `SettingsPage`, `Capability.setupSteps()`/`settingsPages()`; `SettingsSection()` removed | Tasks 1 (with ruling 1's three members), 11, 12 |
| §3.3 wizard flow: orders, dots, Back, Next/Skip, resume, Done | Tasks 7, 9, 11, 12 |
| §3.4 access during setup: `beginSetupSession`/`endSetupSession`, no PIN before You, the PIN once after a kill | Tasks 4, 7, 9 |
| §3.5 Settings session: `openSettings` unchanged, `touch()`, people permissions | Tasks 4, 8, 10 |
| §3.6 (amended) kiosk: pin as soon as setup completes, Settings › Kiosk | Tasks 10, 12 |
| §3.7 people: palette, name and colour rules, `sortOrder` in the transaction, Family refused, `lock()` after changing the signed-in person | Tasks 2, 3, 8 |
| §3.8 location: Open-Meteo, cancellable, no timezone dropped, 2 letters and 400 ms, zone change → sync, nothing logged | Tasks 5, 6 (`HouseholdFollower`), 9 |
| §3.9 Connect step, Review calendars, `setMapping`, `remapMissingPeople`, master always shown, disconnect | Tasks 6, 11 |
| §3.10 `calendar.db` v5, `MIGRATION_4_5`, the refresh rule | Task 6 (ruling 12) |
| §3.11 debug builds | Task 13 |
| §4.1 wizard frame | Task 7 |
| §4.2 Welcome, You, Household, Done | Tasks 8, 9 |
| §4.3 Home location | Tasks 9, 10 |
| §4.4 People list, sheet, messages; shared components promoted | Tasks 2, 8 |
| §4.5 Review calendars; the master-cleared toast | Tasks 6 (`masterGone`), 11 |
| §4.6 Settings frame | Task 10 |
| §4.7 Kiosk page | Task 10 |
| §5 errors and offline | Tasks 5, 8, 9, 11; walkthrough items 3, 15 |
| §6 decisions where ambiguous | Tasks 6 and 11 (a removed person's calendars → Family), 8 (removing yourself), 9 (Welcome's done), 11 (a row per connection), 3a's zone fallback |
| §7 testing: unit, Roborazzi light and dark, the emulator walkthrough | every task; Task 14 |
| §8 follow-ups taken | m5 (Tasks 4, 10); `lock()` (Task 8); `sortOrder` and the Family tests (Task 3); the seed's Admin check (Task 13); H2 (Tasks 6, 11); zone resync (Task 6); the Connect card on the emulator (Task 14 item 15) |
| §9 review focus | Review Focus above |
