# Culvery — Plan 3a: Google Calendar and crash-proofing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The household connects its Google account from Settings or the Connect-a-calendar card and the tablet shows, adds, edits, deletes and assigns events on the real family calendar, follows the calendars ticked in Google, survives Google access lapsing (reconnect from the chip, nothing queued lost), and no provider or store failure can crash the app.

**Architecture:** A new `:provider:calendar-google` implements `CalendarProvider` and `CalendarWriter` over Google Calendar API v3 with OkHttp (every call cancellable) and kotlinx.serialization; tokens come from Play services' `AuthorizationClient` behind an `Authorizer` seam, so no Play services are needed in tests, and a `MockWebServer` with a `Dispatcher`-based fake Google server stands in for Google. The contract grows (`update(fields)`, `find`, `SourceGoneException`, `shown`/`primary` sources, `recurrenceRule`, `forPersonColor`, `userConnectable`) with a contract check per rule. `calendar.db` v4 stores the touched fields, the recurrence rule, the source-refresh time and the paused outbox age clock (D16). The engine gains source refresh and default mapping, touched-field edits, the aged-create and out-of-window lookups, and the crash-proofing of R3 and the store-failure follow-ups; the UI gains the connecting card, the Settings Calendars block, the reconnect chip's flow, the Repeats wording and the service name in copy.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1 (KSP), Room 2.8.0 + room-testing, Coroutines 1.10.2 (+ `kotlinx-coroutines-play-services` 1.10.2), OkHttp 4.12.0 + MockWebServer 4.12.0, kotlinx.serialization 1.9.0 (plugin `org.jetbrains.kotlin.plugin.serialization` 2.2.20), Play services `play-services-auth` 21.4.0, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1.

**Spec:** `docs/superpowers/specs/2026-09-28-culvery-3a-google-calendar-design.md` (binding, including D16). Parent spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§5, §6, §7, §9.6, §9.7, §12).
**Previous plan (format, constraints, review outcome):** `docs/superpowers/plans/2026-09-25-culvery-02b2-add-edit-events.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`. **Spike:** branch `spike/google-auth`, `app/src/debug/java/uk/co/siland/culvery/SpikeGoogleAuthActivity.kt` (read it with `git show spike/google-auth:app/src/debug/java/uk/co/siland/culvery/SpikeGoogleAuthActivity.kt`; never check the branch out). **Setup doc:** `docs/setup/google-calendar.md`.

**Plan series:** 1 Foundation (done) · 2a Calendar read path (done) · 2b-1 Change events (done) · 2b-2 Add and edit (done) · **3a Google Calendar and crash-proofing (this plan)** · 4 Weather, setup, settings, release. The direct ICS provider is deferred (spec D1).

**Task order and why:** contract and data first, then the engine that uses them, then the Google provider, then the UI and the app wiring, so every task ends green and each layer is tested before anything depends on it.
1. **The contract, and every writer in one task.** `update(…, fields)` and `find` stop `:provider:calendar-fake`, the testkit's `TinyProvider` and the calendar tests' `ScriptedWriter` compiling until each implements them, and the fake must pass the five new contract checks at the same gate.
2. `calendar.db` v4 and the store: `MIGRATION_3_4` (the spec's three columns and D16's two), removing connections and sources, the source refresh, `makeDue`, the early returns, and the paused age clock.
3. Crash-proofing the engine (D13): the application scope's handler, `Throwable` per source with logging, store calls outside the provider's try, `retryWhen` on the flows, the drain backoff (m2), C2, the shared write lock (orphan race) and the counting-DAO test.
4. Crash-proofing the sheets (D13): Try again reuses the key, the sheet's load failure, the editor's Delete wording, and `EventDetailHost`'s `onEdit` default.
5. Touched fields (C3) and the person's colour, sheet → editor → outbox → drain.
6. The drain's Google follow-ups: the paused age clock in the drain (D16), `NeedsSignIn` from a write, the aged create (C9) and the assign outside the window (m3).
7. Sources: `defaultMapping`, `SourceRefresher` (start, daily, after `SourceGoneException`), the master-gone toast, and `CalendarSetup.connectWithDefaults`/`reconnect` behind the IO + timeout wrapper.
8. `:provider:calendar-google`, the fake Google server fixture, and `GoogleApi` (cancellable HTTP, JSON, error mapping, the 401 refresh).
9. Sign-in: `TokenSource`, the `Authorizer` seam over `AuthorizationClient`, and the connect flow's logic.
10. `GoogleCalendarProvider` reading (sources, sync, paging, 410, recurrence) and the contract suite's read checks through the fake server.
11. `GoogleCalendarProvider` writing (insert/409, PATCH of touched fields, delete, `find`, `colorId`, R8 wording) and the whole contract suite through the fake server, R9 included.
12. Wording and Repeats (D11, D12).
13. Connecting: `CalendarConnectHost`, Google's `ConnectScreen`, the Connect card, the Settings Calendars block, the reconnect chip, `:app` wiring, and the debug sample's removal (D5); the release APK check.
14. The emulator walkthrough with the user's Google account, the **USER CHECKPOINT**, then the README, `docs/setup/google-calendar.md` and the follow-ups.

**Deviations from the design and extensions (deliberate):**
- **D16 as the spec now states it:** the pause starts at the first `NeedsSignIn` (`connection.needsSignInSinceMillis`) and is folded into each outbox row's `pausedMillis` only when the connection becomes `Ok` (`markSynced` or the reconnect's `setHealth(Ok)`); `Unreachable` and `Error` leave it running. `setHealth` gains `nowMillis`.
- **The connecting card is drawn by `CalendarConnectHost`**, with the provider's `ConnectScreen` inside it running the flow (Google's draws nothing). The spec's "draws the provider's ConnectScreen (a centred surf card …)" reads either way; this keeps every layout number in `CalendarDimens` and the provider free of UI.
- **`Capability.SettingsSection()`** (a `@Composable` with an empty default, in `:core:plugin`): the Settings placeholder draws each capability's section, so `:app` never learns calendar UI. The Calendars block is the calendar's section.
- **`CONFIG_ACCOUNT = "account"`** in the contract: the `Connection.config` key a provider stores the signed-in account under, which Settings shows ("Google Calendar · {account}").
- **`CalendarConnections`** (`:capability:calendar`) serves the three entry points (card, Settings, chip): which providers can be connected, the Admin check, and finishing a connect or reconnect on the application scope, so closing the card can't cancel it.
- **One `retryWithBackoff` helper** in `:core:plugin` for `CalendarSyncLoop` and `ShellViewModel` (1 s doubling to 60 s, each failure logged).
- **A queued change's overlay applies only its stored fields**, so a phone change to a field the tablet didn't touch shows at once, not only after the drain. `EVENT_GONE` becomes public so providers refuse with the app's own wording. The debug fake's weekly samples carry `RRULE:FREQ=WEEKLY`, so the debug build shows "Every week".
- **The walkthrough connects from Settings, not the Connect card** (spec §7 says "sign in from the card"): a debug build always has the sample calendar, so the card never shows on the emulator. The card's button is covered by `CardsTest` and `CalendarConnectHostTest`, and the follow-ups note it for the on-device pass.
- **Small API changes:** `CalendarRepository.masterLabel` becomes `masterService` (it names the service for the failure card); `CalendarSetup`, `SourceRefresher` and `CalendarEditor` take the household's people and the providers' names as functions, so their tests need no provider module. **Small test hooks:** `ScriptedWriter.dropNextReply` (the provider makes the event, then the connection drops) and `ScriptedWriter.findable`; the fake's `writeGate` (the R9 check). `CalendarStore` gets an internal `(db, dao)` constructor for the counting DAO.

**Carried forward (still settled; do not reintroduce):** every 2b-2 review outcome stands, except where this plan's spec says otherwise: C2, C3, C9, C10, m2, m3, m4, R8, R9, DL1 and U3 are done here. One `callWriter` shared by the editor and the drain (2b-1 Simp1); every accepted write reaches the mirror through `CalendarStore.applyAcceptedWrite`; no `LocalToaster` (2b-1 Simp5); `rememberSingleAction` for the sheets; `SilentToaster` in test sources and no `CalendarSync` defaults (2b-2 m7). The follow-ups marked **For Plan 3** are in scope except the two the spec defers (ICS fixtures, the JVM-only module guard, §8).

## Global Constraints

- Package root `uk.co.siland.culvery`; app name "Culvery".
- `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Pinned versions: AGP 8.13.0, Gradle 8.13, Kotlin 2.2.20, KSP 2.2.20-2.0.3, Compose BOM 2025.09.00, Hilt 2.57.1, Room 2.8.0 (with `room-testing` 2.8.0), Robolectric 4.16, Roborazzi 1.46.1. **New in this plan:** OkHttp 4.12.0 and MockWebServer 4.12.0, kotlinx-serialization-json 1.9.0 with the `org.jetbrains.kotlin.plugin.serialization` plugin at the Kotlin version, `play-services-auth` 21.4.0 (the first with `AuthorizationClient.clearToken`), `kotlinx-coroutines-play-services` 1.10.2. All in `gradle/libs.versions.toml`. If a version fails to resolve, take the newest **patch** in the same minor line. Never move to a new major or minor version without asking.
- **Deprecated APIs:** use none without asking the user first. If any API this plan uses shows a deprecation warning in these versions, **stop and ask**. The APIs worth checking are:
  - `GoogleSignIn`, `GoogleSignInAccount`, `GoogleSignInOptions` and `AuthorizationResult.toGoogleSignInAccount()`: deprecated sign-in; **never use them** (spec §3.1);
  - `Identity.getAuthorizationClient`, `AuthorizationRequest.builder().setRequestedScopes(…).setAccount(…)`, `AuthorizationClient.authorize`, `getAuthorizationResultFromIntent`, `clearToken(ClearTokenRequest)` (Task 9);
  - `CancellableContinuation.resume(value, onCancellation)`: the one-argument `onCancellation: (Throwable) -> Unit` overload **is deprecated** in coroutines 1.10; use the three-argument `{ _, value, _ -> … }` form (Task 8);
  - OkHttp 4's Java-style accessors (`response.body()`, `response.code()`, `request.url()`, `RecordedRequest.getPath()` as a call) are deprecated in Kotlin: use the properties (`response.body`, `response.code`, `request.url`, `recorded.path`, `recorded.requestUrl`) and the extension functions (`toMediaType()`, `toRequestBody()`, `toHttpUrl()`) (Tasks 8–11);
  - `rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult())` and `IntentSenderRequest.Builder(IntentSender)` (Task 13);
  - `EntryPointAccessors.fromApplication` (already used by `DebugOfflineReceiver`).

  `@OptIn` to an *experimental* API is allowed only where the plan says so: `ExperimentalCoroutinesApi`, in tests and in `CalendarRepository`, as today. Do not use `androidx.security:security-crypto` / `EncryptedSharedPreferences`.
- Design canvas 1280×800 dp. Hand-off §7 values are authoritative, and the spec's §4 values where the hand-off has none. Copy them exactly as the steps give them.
- No shadows; flat colours; no blur.
- No secrets, tokens or household data in source or build config. Sample people and events live only in `app/src/debug` and `:provider:calendar-fake`. Google needs no client id or secret in the app: the Android OAuth client is matched by package name and SHA-1 (`docs/setup/google-calendar.md`). **Never log a token**; Google's own error text is logged, never shown.
- PINs are exactly 4 ASCII digits.
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on the current branch; never push.
- Module rules (enforced by `build-logic`'s `ModuleBoundaries`):
  - `:core:*` depends only on `:core:*`.
  - `:capability:X…` depends only on `:core:*` and its own family.
  - `:provider:X-…` depends only on `:core:*` and `:capability:X`, plus `:capability:X-testkit` in test configurations.
  - `:app` may depend on anything.
  - This plan adds `:provider:calendar-google` (depending on `:capability:calendar`, `:core:plugin`, `:core:ui`, and `:capability:calendar-testkit` in tests) and `:app`'s `implementation` dependency on it in every build type. The fake stays `debugImplementation`.
  - Read `ProjectDependency.path`, never the deprecated `dependencyProject`.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test` or `check`. A task that touches one module runs that module's `testDebugUnitTest`, plus its `verifyRoborazziDebug` where it has screenshots; every task ends with the full gate before its commit.
- Screenshots:
  - Baselines live in `<module>/src/test/screenshots/`, recorded and verified on Windows.
  - Record with `./gradlew <module>:recordRoborazziDebug`, optionally with `--tests "<pattern>"` to record only new images.
  - Look at every new or changed image before committing.
  - `@GraphicsMode(GraphicsMode.Mode.NATIVE)` goes only on classes that capture screenshots or measure real text.
- Colours and type:
  - Colours come only from `Culvery.colors`, a person's own colour, `ShellTokens`' two scrims (`pinScrim` also sits behind the pickers), and `DarkColors.bg` as the Who chip's ink.
  - Destructive and warning tones use the `danger`, `dangerSoft` and `dangerInk` tokens.
  - Text styles come from `HhType`, or are named `.copy()`s of it in `CalendarType` (calendar), `ShellType` (shell components in `:core:ui`) or `PinPadType` (`:core:access`).
  - **Layout numbers live in `CalendarDimens`, `ShellTokens` or `PinPadDimens`**, never inline. The fake's numbers are named constants too. Reuse `ShellTokens`/`CalendarDimens`/`CalendarType`; never an inline layout number or style.
- **Storage / migration policy:** `calendar.db` holds user configuration. Every schema version ships a hand-written Room `Migration` with a `MigrationTestHelper` test. Never use destructive fallback. Schemas are exported to `capability/calendar/schemas/` and committed. **v4 must ship `MIGRATION_3_4`, its `CalendarMigrationTest` case (with the table list) and `schemas/…/4.json`.** `MigrationTestHelper` uses the driver-based constructor with `AndroidSQLiteDriver`, because androidx.sqlite 2.6.x's default driver mis-handles Windows paths; copy the pattern already in `CalendarMigrationTest`. KSP writes the new schema JSON while compiling: if the migration test can't find `4.json` on the first run, run it once more.
- Calendar providers and writers must be main-safe and cancellable. The engine runs every provider or writer call on `Dispatchers.IO` under a timeout:
  - sync, drain, source refresh, `connect`, `connectWithDefaults` and `setMaster` calls under 60 s (`PROVIDER_TIMEOUT_MS`);
  - the editor's direct attempt under `WRITE_ATTEMPT_MS = 10_000`.

  **Cancelling the coroutine must cancel the HTTP call** (R9): the Google provider enqueues every OkHttp `Call` inside `suspendCancellableCoroutine` and cancels it from `invokeOnCancellation`; nothing calls the blocking `execute()`. Every writer call goes through the shared `callWriter`, and every accepted write reaches the mirror through `CalendarStore.applyAcceptedWrite` (both in `Writes.kt`). Reuse them, `couldNotSave`, `SingleAction`/`rememberSingleAction`; never write a second path.
- Writers throw only `WriteRejectedException` (a permanent refusal), `NeedsSignInException` or `UnreachableException` (`SourceGoneException` is one). `create` is idempotent by its client key, returns `remoteId == clientKey`, and never recreates a deleted event. `update` changes only its `fields`. `find` throws only `NeedsSignInException` or `UnreachableException`.
- **The outbox never spins:** nothing may leave an outbox row due now at the end of a pass (the loop would run a pass every second). Every "try again later" path reschedules with `retryLater` (backoff), and a failed drain backs the loop off (m2).
- Viewing never needs a PIN. Every change calls `AccessControl.authorise` **when tapped**. Buttons are never hidden for permission reasons. (The three add entry points hide only when there is no writable master calendar; Connect Google Calendar hides once Google is connected.) Connecting and reconnecting need `settings.manage` (Admin; not a fresh-PIN permission, spec §6).
- **Tests and threads:**
  - Room runs on its own threads, and `runCurrent()` doesn't wait for it. A test that holds a write open uses the writers' `entered`/`gate` `CompletableDeferred`s and waits in bounded real time: `withContext(Dispatchers.Default) { withTimeout(5_000) { … } }`.
  - Asynchronous UI outcomes use `compose.waitUntil(5_000) { … }`.
  - A Compose test that needs touch mode uses the test-only `TouchModeRule` at `@get:Rule(order = 0)` (in the calendar test sources), never `setInTouchMode` in `@Before`.
  - A Compose test that needs a tag under a clickable parent (a scrim, a week column) finds it with `useUnmergedTree = true`. Never change production semantics for a test.
  - Tests of `:provider:calendar-google` run under Robolectric (`android.util.Log`) against a real `MockWebServer` on localhost; they never reach the network.
- Compose: `pointerInput` keys must be stable (use `rememberUpdatedState` for callbacks; the week recomposes every 30 s). Functions that call each other need explicit return types. Formatters are defined once, `internal`, with `Locale.ENGLISH` (JDK 17's `Locale.UK` prints "Sept").
- **Debug-only code lives in `app/src/debug` (or the debug-only fake) and must not reach release:** Task 13 checks the release APK.
- The shell's overlay layers live inside `ShellLayers` (the graphics-layered `Box` in `OverlayLayers.kt`). Don't restructure it.
- **Copy (spec §3.3, §3.7, §4, D7, D11, D12), exactly:** "Connect Google Calendar"; "Connecting to Google Calendar…"; "Choose the family's Google account and allow access."; "Cancel"; "Google Calendar connected"; "Couldn't connect to Google Calendar — try again"; "Google Calendar reconnected"; "That's a different Google account. Reconnect with {email}."; "Google Calendar: can't find the master calendar, so new events can't be added"; "Connect your family's calendar to see it here."; "Calendars"; "Synced {x} ago"; "Can't reach Google Calendar"; "Needs reconnecting"; "Something went wrong"; "Reconnect"; "Edit repeating events in Google Calendar on your phone."; "“{title}” will be removed from Google Calendar for everyone."; "Couldn't save to Google Calendar — {reason}"; the fixed reasons "the change was refused" and "this calendar can't be changed from the tablet"; "Couldn't open the event — try again"; "(No title)"; "Every day", "Every week", "Every month", "Every year", "Every {n} days/weeks/months/years", "Yes". Everywhere "Google Calendar" appears it is the provider's display name, so the fake reads "Sample calendar (debug)".

## Review Focus

The spec's review-focus inputs (§9), each pinned by named tests in the task that owns the code:

1. **Google access lapses mid-save, over a weekend.**
   - The change is queued, the chip shows `NeedsSignIn` at once, nothing is dropped for age while the connection needs sign-in (D16), and the reconnect makes the queue due and drains it.
   - Tests:
     - Task 2 `aNeedsSignInLapseIsFoldedIntoEachRowWhenTheConnectionIsOk`, `aRowQueuedDuringTheLapseIsPausedOnlyFromItsCreation`
     - Task 6 `aWriteThatNeedsSignInFlagsTheConnectionAndStartsThePause`, `aChangeQueuedBeforeAThreeDayLapseSurvivesItAndAgesAgainAfterTheReconnect`
     - Task 6 `aSaveThatNeedsSignInIsQueuedAndFlagsTheConnectionAtOnce` (editor)
     - Task 7 `reconnectSetsHealthOkMakesTheQueueDueAndAsksForASync`
     - Task 14 Step 3, walkthrough item 9 (revoke at myaccount.google.com, reconnect)
2. **A 409 on a create after a lost reply.**
   - No duplicate, and never a deleted event brought back.
   - Tests:
     - Task 1 contract checks `aRepeatedCreateWithTheSameKeyReturnsTheSameEvent` and `aCreateNeverRecreatesADeletedEvent`, with the fixture `RecreatingContract`
     - Task 11 `aRepeatedInsertReturnsTheEventItsKeyMade`, `anInsertWhoseEventWasDeletedOnAPhoneIsRefused`
     - Task 6 `anAgedCreateThatGoogleHasIsCompletedAndItsFollowersAreSent` (C9)
3. **A phone edit made while an offline tablet edit waits.**
   - Only the touched fields are sent, so the phone's other change is kept.
   - Tests:
     - Task 5 `anOfflineTitleEditIsSentAsATitleOnlyUpdateAfterAPhoneMovedTheEvent`
     - Task 5 `aQueuedTitleEditShowsOverThePhonesNewTime` (overlay)
     - Task 11 `aTitlePatchKeepsAPhoneChangeToTheTime` and `aPersonPatchKeepsCreatedBy` (fake Google server)
     - Task 1 contract check `anUpdateChangesOnlyItsFields`, with the fixture `OverwritingContract`
4. **A calendar deleted in Google while it is the master.**
   - The master is cleared, one toast, the add buttons hide, nothing crashes; its events, cursor and queued changes go.
   - Tests:
     - Task 2 `refreshingWithoutTheMasterClearsItAndRemovesItsRows`
     - Task 7 `aMasterDeletedInGoogleIsClearedWithOneToast`, `aSourceGoneFlagsARefreshThatRemovesIt`
     - Task 10 `aListOnADeletedCalendarIsSourceGone`
5. **A provider throws an `Error`.**
   - No crash; that source's health is Error, logged; the other sources and connections keep syncing, and the loop keeps running.
   - Tests:
     - Task 3 `aProviderThrowingAnErrorFlagsOnlyItsConnection`
     - Task 3 `anUncaughtFailureInAnApplicationJobIsLoggedAndItsSiblingsRun`
     - Task 3 `aStoreFailureFailsThePassNotTheProvidersHealth`

The spec's sixth note (release kiosk mode may block Play services' account chooser) is untested until the Plan 4 on-device pass; Task 14 adds it to the follow-ups.

---

## File Structure

```
gradle/libs.versions.toml                          (modify: okhttp, mockwebserver, serialization, play-services-auth, coroutines-play-services, the serialization plugin)
settings.gradle.kts, build.gradle.kts              (modify: include :provider:calendar-google; the serialization plugin, apply false)

core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/
  Connections.kt       (modify: ProviderDescriptor.userConnectable)
  Capability.kt        (modify: SettingsSection())
  FlowRetry.kt         (create: retryWithBackoff, retryDelayMillis)
core/plugin/src/test/…/FlowRetryTest.kt            (create)

capability/calendar/
  schemas/…CalendarDatabase/4.json                 (generated, committed)
  src/main/java/uk/co/siland/culvery/capability/calendar/
    CalendarContract.kt   (modify: shown, primary, recurrenceRule, forPersonColor, EventField, update(fields), find, SourceGoneException, CONFIG_ACCOUNT)
    Writes.kt             (modify: EVENT_GONE public, fieldsFor, ageMillis, Retry.needsSignIn, assignDraft with colour)
    Stored.kt             (modify: StoredConnection's refresh and pause times, StoredEvent.recurrenceRule, PendingChange.fields/pausedMillis)
    db/CalendarDatabase.kt, db/Migrations.kt, di/CalendarModule.kt   (modify: v4, MIGRATION_3_4)
    CalendarStore.kt      (modify: removeConnection, refreshSources, makeDue, early returns, D16, internal (db, dao) constructor)
    CalendarWriteLock.kt  (create)
    CalendarSync.kt       (modify: Throwable per source, logging, store calls outside the try, drain backoff, C2, lock, fields, D16, C9, m3, NeedsSignIn, refresher)
    CalendarSyncLoop.kt   (modify: retryWithBackoff, drain backoff floor)
    CalendarEditor.kt     (modify: shared lock, create(draft, key), update(ref, draft, fields), colour, NeedsSignIn, service name, couldNotOpen)
    EventForm.kt          (modify: touched)
    PendingOverlay.kt     (modify: an update applies only its fields)
    DefaultMapping.kt     (create: defaultMapping, namesPerson)
    SourceRefresher.kt    (create)
    CalendarSetup.kt      (modify: connectWithDefaults, reconnect, IO + timeout, connectionIds, removeConnection)
    CalendarConnections.kt (create)
    Repeats.kt            (create: repeatsLabel)
    CalendarUi.kt, CalendarRepository.kt   (modify: serviceName, repeats, masterService, reconnectId)
    CalendarCapability.kt (modify: connect card host, SettingsSection)
    ui/EventEditorHost.kt, ui/EventDetailHost.kt, ui/EventDetailSheet.kt, ui/WeekView.kt, ui/CardHosts.kt, ui/ConnectCalendarCard.kt,
    ui/Components.kt (AddButton), ui/Pickers.kt (PickerCard, PickerButton internal), ui/CalendarType.kt   (modify)
    ui/CalendarConnectHost.kt, ui/CalendarSettings.kt   (create)
  src/test/java/uk/co/siland/culvery/capability/calendar/
    ScriptedWriter.kt, CountingDao.kt (create), CalendarMigrationTest, CalendarStoreTest, CalendarSyncTest, CalendarSyncLoopTest, CalendarEditorTest,
    EventFormTest, PendingOverlayTest, DefaultMappingTest (create), SourceRefresherTest (create), CalendarSetupTest, RepeatsTest (create),
    CalendarRepositoryTest, CalendarCapabilityTest, StubEditor, ui/EventEditorHostTest, ui/EventDetailHostTest, ui/EventDetailSheetTest,
    ui/CardsTest, ui/WeekViewTest, ui/CalendarConnectHostTest (create), ui/CalendarSettingsTest (create), ui/ConnectScreenshotTest (create), ui/CardScreenshotTest
  src/test/screenshots/connect_{dark,light}.png (re-recorded), connecting_*.png, settings_calendars_*.png (new), detail_recurring_*, detail_delete_confirm_* (re-recorded)

capability/calendar-testkit/
  src/main/…/CalendarProviderContractTest.kt       (modify: five checks, gateWrites)
  src/test/…/fixtures/TinyProvider.kt, TinyContracts.kt; ContractSuiteSelfTest.kt   (modify: five broken fixtures)

provider/calendar-fake/
  src/main/…/FakeCalendarProvider.kt, SampleEvents.kt   (modify: fields, find, no recreate, userConnectable, primary, writeGate, RRULE)
  src/test/…/FakeCalendarProviderTest.kt, FakeCalendarProviderContractTest.kt   (modify)

provider/calendar-google/                          (create)
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/java/uk/co/siland/culvery/provider/calendar_google/
    GoogleJson.kt          (the JSON models and the one Json)
    GoogleHttp.kt          (Call.await, GoogleApi: requests, the 401 refresh, error mapping)
    GoogleColors.kt        (the 11 event colours, nearestColorId)
    GoogleEvents.kt        (mapping one event; PATCH and insert bodies)
    TokenSource.kt         (TokenSource, Authorizer, Authorization, PlayServicesAuthorizer, PlayServicesTokenSource, authorizationFailure)
    GoogleConnectFlow.kt   (the connect flow's logic)
    GoogleCalendarProvider.kt
    di/GoogleCalendarModule.kt
  src/test/resources/robolectric.properties
  src/test/java/uk/co/siland/culvery/provider/calendar_google/
    FakeGoogleServer.kt, FakeTokenSource.kt, FakeAuthorizer.kt, GoogleApiTest.kt, TokenSourceTest.kt, GoogleConnectFlowTest.kt,
    GoogleReadTest.kt, GoogleConnectScreenTest.kt, GoogleWriteTest.kt, GoogleColorsTest.kt, GoogleCalendarProviderContractTest.kt

app/
  build.gradle.kts                                 (modify: :provider:calendar-google)
  src/main/java/uk/co/siland/culvery/di/AppModule.kt   (modify: the exception handler)
  src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt   (modify: retryWithBackoff)
  src/main/java/uk/co/siland/culvery/shell/ui/SettingsPlaceholder.kt, MainActivity.kt, CulveryApp.kt   (modify)
  src/debug/java/uk/co/siland/culvery/DebugSeed.kt, src/release/java/uk/co/siland/culvery/DebugSeed.kt   (modify: removeSampleWhenReplaced)
  src/test/java/uk/co/siland/culvery/ApplicationScopeTest.kt (create), shell/ShellViewModelTest.kt, shell/Fakes.kt
  src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt, SampleAddTest.kt, SampleRollbackTest.kt

README.md, docs/setup/google-calendar.md, docs/superpowers/plans/2026-09-23-plan1-followups.md   (modify, Task 14, after the checkpoint)
```

`…` in a path stands for the module's package directory; every step spells out the full path.

---
### Task 1: The contract — `update(fields)`, `find`, `SourceGoneException`, sources' `shown`/`primary`, and every writer

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Connections.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`
- Modify: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedWriter.kt`
- Modify: `capability/calendar-testkit/src/main/java/uk/co/siland/culvery/capability/calendar_testkit/CalendarProviderContractTest.kt`
- Modify: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyProvider.kt`
- Modify: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyContracts.kt`
- Test: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/ContractSuiteSelfTest.kt` (modify)
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/SampleEvents.kt`
- Test: `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt` (modify)
- Test: `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderContractTest.kt` (modify)

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `ProviderDescriptor(id, displayName, icon, features, userConnectable: Boolean = true)`
  - `CalendarSource(id, name, writable, shown: Boolean = true, primary: Boolean = false)`
  - `RemoteEvent(…, createdBy: String? = null, recurrenceRule: String? = null)`
  - `EventDraft(title, start, end, forPerson, createdBy, forPersonColor: Long? = null)`
  - `enum class EventField { TITLE, TIMES, FOR_PERSON }`
  - `CalendarWriter.update(conn, source, remoteId, draft, fields: Set<EventField>): RemoteEvent`; `CalendarWriter.find(conn, source, remoteId): RemoteEvent?`
  - `open class UnreachableException`; `class SourceGoneException(message: String? = null, cause: Throwable? = null) : UnreachableException`
  - `const val CONFIG_ACCOUNT = "account"`
  - `const val EVENT_GONE = "The event no longer exists"` (now public); `internal fun fieldsFor(kind: ChangeKind, fields: Set<EventField>?): Set<EventField>`
  - `CalendarProviderContractTest.gateWrites(): (() -> Unit)?` (optional hook: hold every write's reply; the returned function releases them)
  - `ScriptedWriter.fieldSets: List<Set<EventField>>`, `ScriptedWriter.findable: MutableMap<String, RemoteEvent>`
  - `FakeCalendarProvider.writeGate: CompletableDeferred<Unit>?` (internal); `SOURCE_FAMILY` is the fake's `primary`

- [ ] **Step 1: Write the failing contract checks**

In `capability/calendar-testkit/src/main/java/uk/co/siland/culvery/capability/calendar_testkit/CalendarProviderContractTest.kt`:

1. Replace the imports block with:
```kotlin
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.capability.calendar.instantIn
import uk.co.siland.culvery.capability.calendar.newClientKey
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature

/** Real time for a held write to reach the service before the check cancels its caller. */
private const val HOLD_SETTLE_MS = 200L

/** How soon a cancelled write must return (3a design §3.10, follow-up R9). */
private const val CANCEL_WITHIN_MS = 1_000L
```

2. After `protected open fun writableSource(): CalendarSource? = null`, add:
```kotlin
    /**
     * Makes the service hold its reply to every write from now on and returns the function that releases them; null
     * skips the cancellation check. On a cooperative in-memory provider it proves little; on a real service's test
     * double (Google through the fake server) it proves the HTTP call is cancelled with its caller.
     */
    protected open fun gateWrites(): (() -> Unit)? = null
```

3. In `updatedFieldsRoundTrip`, replace
```kotlin
        val updated = w.update(conn, source, created.remoteId, changed)
```
with
```kotlin
        val updated = w.update(conn, source, created.remoteId, changed, EventField.entries.toSet())
```

4. Add these members at the end of the class:
```kotlin
    /** 3a design C10: a create whose key belonged to a deleted event is refused; it never makes the event again. */
    @Test
    fun aCreateNeverRecreatesADeletedEvent() = runTest {
        val (w, source) = requireWriting()
        val key = newClientKey()
        val draft = draftIn("Gone for good", 3)
        val made = w.create(conn, source, draft, key)
        w.delete(conn, source, made.remoteId)
        val error = try {
            w.create(conn, source, draft, key)
            null
        } catch (e: Exception) {
            e
        }
        assertWithMessage("a create repeating a deleted event's key must be refused, never make the event again")
            .that(error).isInstanceOf(WriteRejectedException::class.java)
        assertThat(subject.sync(conn, source, window, null).upserts.map { it.remoteId }).doesNotContain(made.remoteId)
    }

    /** 3a design C3: an update changes only its fields, so a change made elsewhere to the others is kept. */
    @Test
    fun anUpdateChangesOnlyItsFields() = runTest {
        val (w, source) = requireWriting()
        val created = w.create(conn, source, draftIn("Only fields", 1), newClientKey())
        val moved = draftIn("Not this title", 5, forPerson = "not-this-person", createdBy = "not-this-creator")
        w.update(conn, source, created.remoteId, moved, setOf(EventField.TIMES))
        val renamed = draftIn("Renamed", 9, forPerson = "not-this-person", createdBy = "not-this-creator")
        val updated = w.update(conn, source, created.remoteId, renamed, setOf(EventField.TITLE))
        val synced = subject.sync(conn, source, window, null).upserts.firstOrNull { it.remoteId == created.remoteId }
        assertWithMessage("the next sync must return the updated event").that(synced).isNotNull()
        listOf("update's result" to updated, "the next sync" to synced!!).forEach { (what, e) ->
            assertWithMessage("$what: the title").that(e.title).isEqualTo("Renamed")
            assertWithMessage("$what: the times the earlier TIMES update set").that(e.start to e.end).isEqualTo(moved.start to moved.end)
            assertWithMessage("$what: who it is for, which neither update changed").that(e.forPerson).isEqualTo(created.forPerson)
            assertWithMessage("$what: who created it, which no update changes").that(e.createdBy).isEqualTo(created.createdBy)
        }
    }

    @Test
    fun findReturnsACreatedEventAndNullOnceItIsDeleted() = runTest {
        val (w, source) = requireWriting()
        val draft = draftIn("Findable", 2)
        val created = w.create(conn, source, draft, newClientKey())
        val found = w.find(conn, source, created.remoteId)
        assertWithMessage("find must return an event that exists").that(found).isNotNull()
        assertMatches("find's result", found!!, draft, checkTags = false)
        w.delete(conn, source, created.remoteId)
        assertWithMessage("find must return null for a deleted event").that(w.find(conn, source, created.remoteId)).isNull()
    }

    /** Follow-up R9: cancelling the caller must cancel the service call, or the engine's timeouts can't hold. */
    @Test
    fun aWriteReturnsPromptlyWhenItsCallerIsCancelled() = runTest {
        val (w, source) = requireWriting()
        val release = gateWrites()
        assumeTrue("provider cannot hold a write's reply", release != null)
        val returnedInTime = withContext(Dispatchers.Default) {
            val call = launch {
                try {
                    w.create(conn, source, draftIn("Held", 4), newClientKey())
                } catch (e: Exception) {
                    // Cancelled, as this check intends.
                }
            }
            delay(HOLD_SETTLE_MS)
            call.cancel()
            val joined = withTimeoutOrNull(CANCEL_WITHIN_MS) { call.join() } != null
            release!!.invoke()
            joined
        }
        assertWithMessage("a write whose caller is cancelled must return within 1 s: cancel the service call with it")
            .that(returnedInTime).isTrue()
    }

    @Test
    fun sourcesReportAtMostOnePrimary() = runTest {
        assertWithMessage("at most one source may be the account's primary calendar")
            .that(subject.sources(conn).count { it.primary }).isAtMost(1)
    }
```
The release runs inside the `withContext` block, before it returns: `withContext` waits for its child, and a child stuck in a non-cancellable wait would otherwise never finish.

- [ ] **Step 2: Write the failing self-tests**

In `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/ContractSuiteSelfTest.kt`:
1. Add these imports beside the other fixture imports:
```kotlin
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.BlindContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.OverwritingContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RecreatingContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.StubbornContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.TwoPrimariesContract
```
2. In `wellBehavedProviderPassesEveryCheck` and `aReadOnlyProviderSkipsOnlyTheWriteChecks`, replace `isEqualTo(17)` with `isEqualTo(22)`. In `aReadOnlyProviderSkipsOnlyTheWriteChecks`, replace `assertThat(result.assumptionFailureCount).isEqualTo(7)` with `assertThat(result.assumptionFailureCount).isEqualTo(11)`.
3. Add at the end of the class:
```kotlin
    @Test
    fun aWriterThatRecreatesADeletedEventIsCaught() {
        assertThat(failuresOf(RecreatingContract::class.java)).containsExactly("aCreateNeverRecreatesADeletedEvent")
    }

    @Test
    fun anUpdateThatChangesEveryFieldIsCaught() {
        assertThat(failuresOf(OverwritingContract::class.java)).containsExactly("anUpdateChangesOnlyItsFields")
    }

    @Test
    fun aWriterThatCannotFindItsEventsIsCaught() {
        assertThat(failuresOf(BlindContract::class.java)).containsExactly("findReturnsACreatedEventAndNullOnceItIsDeleted")
    }

    @Test
    fun aWriteThatIgnoresCancellationIsCaught() {
        assertThat(failuresOf(StubbornContract::class.java)).containsExactly("aWriteReturnsPromptlyWhenItsCallerIsCancelled")
    }

    @Test
    fun twoPrimaryCalendarsAreCaught() {
        assertThat(failuresOf(TwoPrimariesContract::class.java)).containsExactly("sourcesReportAtMostOnePrimary")
    }
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :capability:calendar-testkit:testDebugUnitTest`
Expected: compilation FAILS: `EventField`, `find` and the five fixtures are unresolved.

- [ ] **Step 4: Change the contract**

In `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Connections.kt`, replace `ProviderDescriptor` (with its KDoc) with:
```kotlin
/** A provider module's identity. [id] is stable and namespaced by capability, e.g. "calendar.google". */
data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    /** Material Symbols ligature name. */
    val icon: String,
    val features: Set<Feature>,
    /** Offered as "Connect {displayName}" in Settings and on the Connect-a-calendar card; false for the debug sample. */
    val userConnectable: Boolean = true,
)
```
and replace the `Connection` KDoc with:
```kotlin
/**
 * One user-configured instance of a provider. [config] holds non-secret settings (Google: only the account email).
 * A SecretStore arrives with the first provider that has a secret; Google needs none (3a design D2).
 */
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`:

1. Replace `data class CalendarSource(val id: String, val name: String, val writable: Boolean)` with:
```kotlin
/**
 * One calendar in a connection. [shown]: ticked and not hidden in the service (Google: `selected` and not `hidden`).
 * [primary]: the account's own calendar, at most one per connection; connecting makes it the master (3a design D4).
 */
data class CalendarSource(
    val id: String,
    val name: String,
    val writable: Boolean,
    val shown: Boolean = true,
    val primary: Boolean = false,
)

/** The [Connection.config] key a provider stores the signed-in account under; Settings shows it ("Google Calendar · {account}"). */
const val CONFIG_ACCOUNT = "account"
```

2. In the `RemoteEvent` KDoc, replace the sentence `[forPerson] and [createdBy] are household PersonId values when the provider stores them.` with `[forPerson] and [createdBy] are household PersonId values when the provider stores them. [recurrenceRule] is the series' RRULE line (e.g. "RRULE:FREQ=WEEKLY") when the provider knows it.` Then after `val createdBy: String? = null,` in `RemoteEvent` add:
```kotlin
    val recurrenceRule: String? = null,
```

3. Replace the `EventDraft` KDoc and class with:
```kotlin
/**
 * What the tablet asks a provider to write. [end] is exclusive, as in [RemoteEvent]. [forPerson] and [createdBy]
 * are household PersonId values ("family" allowed) that the provider stores with the event (Google:
 * extendedProperties.private). Names are never written. [forPersonColor] is that person's colour (ARGB), so the
 * provider can colour the event (Google: the nearest colorId); null for Family or untagged.
 */
data class EventDraft(
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val forPerson: String?,
    val createdBy: String?,
    val forPersonColor: Long? = null,
)

/** What an update changes (3a design C3): the title, the start and end together, or who the event is for. */
enum class EventField { TITLE, TIMES, FOR_PERSON }
```

4. Replace the `UnreachableException` declaration (keep its KDoc) with:
```kotlin
open class UnreachableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * The source isn't there any more (Google: 404, or a 403 that isn't a rate limit, on events.list). The engine
 * records it as unreachable and refreshes the connection's sources on the next pass, which removes it if it has gone.
 */
class SourceGoneException(message: String? = null, cause: Throwable? = null) : UnreachableException(message, cause)
```

5. In the `CalendarWriter` KDoc, after the bullet starting `- [create] is idempotent by its client key` (three lines), add:
```kotlin
 * - [create] never recreates a deleted event: a create whose key belonged to an event since deleted throws
 *   [WriteRejectedException] (Google: the 409's event is cancelled).
 * - [find] throws only [NeedsSignInException] or [UnreachableException].
```

6. Replace the `update` declaration and its KDoc with:
```kotlin
    /**
     * Changes only [fields] of the event to [draft]'s values; the draft's other fields are ignored. Everything else the
     * service holds (description, location, attendees, reminders, the createdBy tag) is kept (Google: PATCH, never
     * PUT). Returns the event as the service now holds it.
     */
    suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft, fields: Set<EventField>): RemoteEvent

    /** The event as the service holds it; null when it doesn't exist or was deleted. */
    suspend fun find(conn: Connection, source: CalendarSource, remoteId: String): RemoteEvent?
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`, replace `internal const val EVENT_GONE = "The event no longer exists"` with:
```kotlin
/** Why a change to an event that is gone is refused; public so providers refuse with the same words. */
const val EVENT_GONE = "The event no longer exists"

/**
 * What an update of [kind] changes: an assign only who the event is for; an update its stored [fields], or every
 * field for a row queued before v4 stored them.
 */
internal fun fieldsFor(kind: ChangeKind, fields: Set<EventField>?): Set<EventField> =
    if (kind == ChangeKind.ASSIGN) setOf(EventField.FOR_PERSON) else fields ?: EventField.entries.toSet()
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`, in `deliver`:
- replace `callWriter(io, timeoutMillis) { writer.update(conn, source, remoteId, draft) }` with
```kotlin
                callWriter(io, timeoutMillis) { writer.update(conn, source, remoteId, draft, fieldsFor(ChangeKind.UPDATE, null)) }
```
- replace `callWriter(io, timeoutMillis) { writer.update(conn, source, current.remoteId, draft) }` with
```kotlin
                callWriter(io, timeoutMillis) { writer.update(conn, source, current.remoteId, draft, fieldsFor(ChangeKind.ASSIGN, null)) }
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`, in `attempt`, replace
```kotlin
                ChangeKind.UPDATE, ChangeKind.ASSIGN -> to.writer.update(to.connection, to.source, checkNotNull(remoteId), checkNotNull(draft))
```
with
```kotlin
                ChangeKind.UPDATE, ChangeKind.ASSIGN ->
                    to.writer.update(to.connection, to.source, checkNotNull(remoteId), checkNotNull(draft), fieldsFor(kind, null))
```
Task 5 passes the touched fields in place of `null`.

- [ ] **Step 5: Bring the calendar tests' writer in line**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedWriter.kt`, replace everything from the class KDoc to the end of the file with:
```kotlin
/**
 * An in-test writer. [calls] reads "create:<title>", "update:<remoteId>", "delete:<remoteId>" or "find:<remoteId>".
 * Creates keep the contract: the client key is the event's id, and a repeated key returns the event it made.
 */
internal class ScriptedWriter(override val providerId: String) : CalendarWriter {
    val calls = mutableListOf<String>()
    /** The drafts sent to create and update, in order. */
    val drafts = mutableListOf<EventDraft>()
    /** The fields sent with each update, in order. */
    val fieldSets = mutableListOf<Set<EventField>>()
    /** The events create made, by client key. */
    val created = linkedMapOf<String, RemoteEvent>()
    /** Events find returns besides those create made: an event the mirror doesn't hold (3a design m3). */
    val findable = linkedMapOf<String, RemoteEvent>()
    var failWith: Throwable? = null
    /** When set, each write waits for it: a slow network the test releases. */
    var gate: CompletableDeferred<Unit>? = null
    /** The next create makes its event, then never replies: a reply lost after the provider acted. */
    var loseNextReply = false
    /** Completes when the first write starts. */
    val entered = CompletableDeferred<Unit>()

    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent {
        record("create:${draft.title}", draft)
        gate?.await()
        failWith?.let { throw it }
        val event = synchronized(this) {
            created.getOrPut(clientKey) {
                RemoteEvent(clientKey, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
            }
        }
        if (loseNextReply) {
            loseNextReply = false
            awaitCancellation()
        }
        return event
    }

    override suspend fun update(
        conn: Connection,
        source: CalendarSource,
        remoteId: String,
        draft: EventDraft,
        fields: Set<EventField>,
    ): RemoteEvent {
        record("update:$remoteId", draft)
        synchronized(this) { fieldSets += fields }
        gate?.await()
        failWith?.let { throw it }
        return RemoteEvent(remoteId, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
    }

    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) {
        record("delete:$remoteId", null)
        gate?.await()
        failWith?.let { throw it }
    }

    override suspend fun find(conn: Connection, source: CalendarSource, remoteId: String): RemoteEvent? {
        record("find:$remoteId", null)
        gate?.await()
        failWith?.let { throw it }
        return synchronized(this) { findable[remoteId] ?: created[remoteId] }
    }

    // Some tests run writes on Dispatchers.Default, so the lists are locked.
    private fun record(call: String, draft: EventDraft?) {
        synchronized(this) {
            calls += call
            if (draft != null) drafts += draft
        }
        entered.complete(Unit)
    }
}
```

- [ ] **Step 6: The testkit's tiny provider, with the five broken fixtures**

In `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyProvider.kt`:

1. Replace the imports block with:
```kotlin
import androidx.compose.runtime.Composable
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
```

2. In the constructor, after `private val duplicateOnRepeat: Boolean = false,` add:
```kotlin
    private val recreateDeleted: Boolean = false,
    private val updateEverything: Boolean = false,
    private val findNothing: Boolean = false,
    private val uncancellableWrites: Boolean = false,
    private val twoPrimaries: Boolean = false,
```

3. After `private var nextId = 0` add:
```kotlin
    private var writeGate: CompletableDeferred<Unit>? = null
    // The key each written event was made with, and the keys of events since deleted.
    private val keyOf = mutableMapOf<String, String>()
    private val deletedKeys = mutableSetOf<String>()
```

4. After `fun failNextWith(error: Throwable) { … }` add:
```kotlin
    /** Holds every write until the returned function is called (the R9 check). */
    fun gateWrites(): () -> Unit {
        val gate = CompletableDeferred<Unit>()
        writeGate = gate
        return { gate.complete(Unit) }
    }

    private suspend fun awaitGate() {
        val gate = writeGate ?: return
        // Broken on purpose when uncancellable: the caller's cancellation can't reach the wait.
        if (uncancellableWrites) withContext(NonCancellable) { gate.await() } else gate.await()
    }
```

5. Replace `override suspend fun sources(conn: Connection) = …` with:
```kotlin
    override suspend fun sources(conn: Connection) =
        if (canWrite) listOf(SOURCE.copy(primary = twoPrimaries), WRITABLE) else listOf(SOURCE.copy(primary = twoPrimaries))
```

6. Replace `create`, `update` and `delete` with:
```kotlin
    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent {
        checkWritable(source)
        awaitGate()
        if (!recreateDeleted && clientKey in deletedKeys) throw WriteRejectedException("Tiny's event for $clientKey was deleted")
        if (!ignoreClientKey) {
            written[clientKey]?.let { existing ->
                if (duplicateOnRepeat) {
                    // Broken on purpose: it answers with the event the key made, but writes a second one too.
                    val copy = "w${++nextId}"
                    written[copy] = existing.copy(remoteId = copy)
                    version++
                }
                return existing
            }
        }
        val event = RemoteEvent(
            if (ignoreClientKey) "w${++nextId}" else clientKey,
            draft.title,
            draft.start,
            draft.end,
            recurring = false,
            forPerson = if (dropTagsOnCreate) null else draft.forPerson,
            createdBy = if (dropTagsOnCreate) null else draft.createdBy,
        )
        written[event.remoteId] = event
        keyOf[event.remoteId] = clientKey
        version++
        return event
    }

    override suspend fun update(
        conn: Connection,
        source: CalendarSource,
        remoteId: String,
        draft: EventDraft,
        fields: Set<EventField>,
    ): RemoteEvent {
        checkWritable(source)
        awaitGate()
        val current = written[remoteId] ?: throw WriteRejectedException("No event $remoteId")
        // Broken on purpose when updating everything: it writes the whole draft, creator included.
        val applied = if (updateEverything) EventField.entries.toSet() else fields
        val event = current.copy(
            title = if (EventField.TITLE in applied) draft.title else current.title,
            start = if (EventField.TIMES in applied) draft.start else current.start,
            end = if (EventField.TIMES in applied) draft.end else current.end,
            forPerson = if (EventField.FOR_PERSON in applied) draft.forPerson else current.forPerson,
            createdBy = if (updateEverything) draft.createdBy else current.createdBy,
        )
        written[remoteId] = event
        version++
        return event
    }

    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) {
        checkWritable(source)
        awaitGate()
        when {
            written.remove(remoteId) != null -> {
                deletedKeys += keyOf[remoteId] ?: remoteId
                version++
            }
            rejectMissingDelete -> throw WriteRejectedException("No event $remoteId")
        }
    }

    override suspend fun find(conn: Connection, source: CalendarSource, remoteId: String): RemoteEvent? =
        if (findNothing || source.id != WRITABLE.id) null else written[remoteId]
```

7. In the companion, replace `val WRITABLE = CalendarSource("tiny-w", "Tiny writable", writable = true)` with:
```kotlin
        val WRITABLE = CalendarSource("tiny-w", "Tiny writable", writable = true, primary = true)
```

In `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyContracts.kt`:
1. After `override fun writableSource() = TinyProvider.WRITABLE` add:
```kotlin
    override fun gateWrites(): (() -> Unit)? = tiny.gateWrites()
```
2. Add at the end of the file:
```kotlin
class RecreatingContract : TinyContract(TinyProvider(recreateDeleted = true))
class OverwritingContract : TinyContract(TinyProvider(updateEverything = true))
class BlindContract : TinyContract(TinyProvider(findNothing = true))
class StubbornContract : TinyContract(TinyProvider(uncancellableWrites = true))
class TwoPrimariesContract : TinyContract(TinyProvider(twoPrimaries = true))
```

- [ ] **Step 7: Run the testkit**

Run: `./gradlew :capability:calendar-testkit:testDebugUnitTest`
Expected: PASS. `KeylessContract` still fails only its one check: its delete records the key its event was made with, so the recreate check refuses the repeated key. `DroppingTagsContract` still fails only its one: the new checks compare tags with what `create` returned, and `find`'s check skips tags.

- [ ] **Step 8: Write the failing fake tests**

In `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt`:
1. Add `import uk.co.siland.culvery.capability.calendar.EventField`.
2. In `updatingASampleReplacesItOnTheNextSync`, replace the `fake.update(…)` line with:
```kotlin
        fake.update(conn, family, plumber.remoteId, EventDraft(plumber.title, plumber.start, plumber.end, "sam-id", null), setOf(EventField.FOR_PERSON))
```
3. In `repeatingSamplesCannotBeChanged`, replace the `runBlocking { … }` line with:
```kotlin
            runBlocking { fake.update(conn, family, swim.remoteId, EventDraft(swim.title, swim.start, swim.end, null, null), setOf(EventField.TITLE)) }
```
4. Add at the end of the class:
```kotlin
    @Test
    fun theSampleIsNeverOfferedToConnectAndItsFamilyCalendarIsThePrimary() = runTest {
        val fake = providerOn(today)
        assertThat(fake.descriptor.userConnectable).isFalse()
        assertThat(fake.sources(conn).filter { it.primary }.map { it.id }).containsExactly(FakeCalendarProvider.SOURCE_FAMILY)
    }

    @Test
    fun anUpdateOfASampleChangesOnlyItsFieldsAndKeepsItsCreator() = runTest {
        val fake = providerOn(today)
        fake.tagSamples(mapOf("Alex" to "alex-id"))
        val dinner = fake.familyEvents().single { it.title == "Dinner with Jo & Priya" }
        val updated = fake.update(conn, family, dinner.remoteId, draft("Dinner at Gran's"), setOf(EventField.TITLE))
        assertThat(listOf(updated.title, updated.start, updated.forPerson, updated.createdBy))
            .containsExactly("Dinner at Gran's", dinner.start, "alex-id", "alex-id").inOrder()
        assertThat(fake.familyEvents().single { it.remoteId == dinner.remoteId }.title).isEqualTo("Dinner at Gran's")
    }

    @Test
    fun findReturnsWhatTheCalendarHoldsAndNullOnceDeleted() = runTest {
        val fake = providerOn(today)
        val boiler = fake.familyEvents().single { it.title == "Boiler service" }
        assertThat(fake.find(conn, family, boiler.remoteId)?.title).isEqualTo("Boiler service")
        fake.delete(conn, family, boiler.remoteId)
        assertThat(fake.find(conn, family, boiler.remoteId)).isNull()
    }

    @Test
    fun aKeyWhoseEventWasDeletedIsRefused() = runTest {
        val fake = providerOn(today)
        val key = newClientKey()
        fake.create(conn, family, draft("Sleepover"), key)
        fake.delete(conn, family, key)
        assertThrows(WriteRejectedException::class.java) {
            runBlocking { fake.create(conn, family, draft("Sleepover"), key) }
        }
        assertThat(fake.familyEvents().map { it.title }).doesNotContain("Sleepover")
    }
```

In `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderContractTest.kt`:
1. Add `import kotlinx.coroutines.CompletableDeferred`, and remove the `CalendarSource` import.
2. Replace `private val familyCalendar = CalendarSource(FakeCalendarProvider.SOURCE_FAMILY, "Family calendar", writable = true)` with:
```kotlin
    private val familyCalendar = FakeCalendarProvider.SOURCES.single { it.id == FakeCalendarProvider.SOURCE_FAMILY }
```
3. After `override fun simulateUnreachable() = …` add:
```kotlin
    override fun gateWrites(): (() -> Unit)? {
        val gate = CompletableDeferred<Unit>()
        fake.writeGate = gate
        return { gate.complete(Unit) }
    }
```

- [ ] **Step 9: Run them to see them fail**

Run: `./gradlew :provider:calendar-fake:testDebugUnitTest`
Expected: compilation FAILS: the fake doesn't implement `find` or the new `update`, and `writeGate` is unresolved.

- [ ] **Step 10: Bring the fake in line**

In `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`:

1. Replace the imports block with:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.HhPillButton
```

2. After `private const val OFFLINE_MESSAGE = "Sample calendar is offline"` add:
```kotlin
private const val GONE_MESSAGE = "That event no longer exists"
```

3. Replace `override val descriptor = …` with:
```kotlin
    override val descriptor =
        ProviderDescriptor(ID, "Sample calendar (debug)", "event", setOf(Feature.READ, Feature.WRITE), userConnectable = false)
```

4. After `@Volatile private var offline = false` add:
```kotlin
    // The last sync's zone, so an update can rebuild the sample it changes.
    @Volatile private var zone: ZoneId = ZoneOffset.UTC

    /** For the contract's R9 check: while set, every write waits for it. */
    @Volatile internal var writeGate: CompletableDeferred<Unit>? = null
```

5. In `sync`, make the first line after `throwIfFailing()`:
```kotlin
        zone = range.zone
```

6. Replace `create`, `update` and `delete` with:
```kotlin
    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent =
        write(source) {
            // A key whose event was deleted is refused, never made again (CalendarWriter contract).
            if (clientKey in deleted) throw WriteRejectedException(GONE_MESSAGE)
            // The key is the event's id, so a retried create returns the event it made (CalendarWriter contract).
            created.getOrPut(clientKey) {
                RemoteEvent(clientKey, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
            }
        }

    override suspend fun update(
        conn: Connection,
        source: CalendarSource,
        remoteId: String,
        draft: EventDraft,
        fields: Set<EventField>,
    ): RemoteEvent = write(source) {
        val current = current(remoteId) ?: throw WriteRejectedException(GONE_MESSAGE)
        if (current.recurring) throw WriteRejectedException("Repeating events can't be changed here")
        val event = current.copy(
            title = if (EventField.TITLE in fields) draft.title else current.title,
            start = if (EventField.TIMES in fields) draft.start else current.start,
            end = if (EventField.TIMES in fields) draft.end else current.end,
            forPerson = if (EventField.FOR_PERSON in fields) draft.forPerson else current.forPerson,
        )
        if (remoteId in created) created[remoteId] = event else changed[remoteId] = event
        event
    }

    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) = write(source) {
        // Deleting something already gone succeeds (CalendarWriter contract).
        created.remove(remoteId)
        changed.remove(remoteId)
        deleted += remoteId
        Unit
    }

    override suspend fun find(conn: Connection, source: CalendarSource, remoteId: String): RemoteEvent? {
        if (offline) throw UnreachableException(OFFLINE_MESSAGE)
        return synchronized(lock) { if (source.id == SOURCE_FAMILY) current(remoteId) else null }
    }

    /** The Family calendar's event as it is now; null once deleted. Called under [lock]. */
    private fun current(remoteId: String): RemoteEvent? = when (remoteId) {
        in deleted -> null
        in created -> created[remoteId]
        in changed -> changed[remoteId]
        else -> SampleEvents.forSource(SOURCE_FAMILY, LocalDate.now(clock.withZone(zone)), zone, idsByName)
            .firstOrNull { it.remoteId == remoteId }
    }
```

7. Replace `private inline fun <T> write(…)` with:
```kotlin
    private suspend inline fun <T> write(source: CalendarSource, block: () -> T): T {
        writeGate?.await()
        return synchronized(lock) {
            if (offline) throw UnreachableException(OFFLINE_MESSAGE)
            if (unreachableNext) {
                unreachableNext = false
                throw UnreachableException(OFFLINE_MESSAGE)
            }
            rejectNext?.let {
                rejectNext = null
                throw WriteRejectedException(it)
            }
            if (source.id != SOURCE_FAMILY) throw WriteRejectedException("${source.name} can't be changed here")
            block().also { version++ }
        }
    }
```

8. In the companion's `SOURCES`, replace the Family line with:
```kotlin
            CalendarSource(SOURCE_FAMILY, "Family calendar", writable = true, primary = true),
```

In `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/SampleEvents.kt`, delete `familySampleRepeats` and its KDoc: nothing calls it now.

- [ ] **Step 11: Run the fake's tests**

Run: `./gradlew :provider:calendar-fake:testDebugUnitTest`
Expected: PASS, the contract suite's five new checks included.

- [ ] **Step 12: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. The calendar tests pass unchanged: the drain and the editor send every field for an update and `FOR_PERSON` for an assign, and the draft carries the event's own values for the rest, as before.

- [ ] **Step 13: Commit**

```bash
git add core/plugin capability provider
git commit -m "Add touched-field updates, find, gone sources and the primary calendar to the calendar contract, with a check for each"
```

---

### Task 2: `calendar.db` v4 and the store — `MIGRATION_3_4`, removing connections and sources, the source refresh, `makeDue`, and the paused age clock (D16)

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Stored.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/Migrations.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Create (generated): `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/4.json`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarMigrationTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt` (modify)

**Interfaces:**
- Consumes: `EventField`, `CalendarSource.shown`/`primary`, `RemoteEvent.recurrenceRule`, `EventDraft.forPersonColor` (Task 1).
- Produces:
  - `StoredConnection(connection, health, lastSyncMillis, sourcesCheckedMillis: Long? = null, needsSignInSinceMillis: Long? = null)`
  - `StoredEvent(…, endSort, recurrenceRule: String? = null)`
  - `PendingChange(…, clientKey: String? = null, fields: Set<EventField>? = null, pausedMillis: Long = 0)`
  - `val MIGRATION_3_4: Migration`; `CalendarDatabase` version 4
  - `CalendarStore.setHealth(connectionId: String, health: ConnectionHealth, nowMillis: Long)` (gains `nowMillis`); `markSynced(connectionId, atMillis)` also ends a pause
  - `CalendarStore.removeConnection(connectionId: String)`
  - `CalendarStore.refreshSources(connectionId: String, sources: List<CalendarSource>, nowMillis: Long, mappingForNew: (CalendarSource) -> SourceMapping): Boolean` — true when it cleared the master
  - `CalendarStore.makeDue(connectionId: String, nowMillis: Long)`
  - `CalendarStore.addConnection(connection, sources, mapping, masterSourceId: String? = null)`
  - `applySync` and `applyAccepted` write nothing once their source row is gone
  - `internal fun ageMillis(change: PendingChange, pausedSince: Long?, nowMillis: Long): Long` (in `Writes.kt`)

- [ ] **Step 1: Write the failing migration test**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarMigrationTest.kt`:

1. Add the import `import uk.co.siland.culvery.capability.calendar.db.MIGRATION_3_4`.
2. The v1 and v2 tests now open a v4 database. In both, replace
```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```
with
```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```
3. Add these members before `tableNames`:
```kotlin
    @Test
    fun migrationFromV3AddsTheNewColumnsEmptyAndKeepsEverything() = runTest {
        file.parentFile?.mkdirs()
        file.delete()

        val v3 = helper.createDatabase(3)
        v3.execSQL(
            "INSERT INTO connection (id, providerId, label, configJson, health, healthMessage, lastSyncMillis) " +
                "VALUES ('c1', 'calendar.test', 'Google', '{\"account\":\"family@example.com\"}', 'NEEDS_SIGN_IN', NULL, 1234)",
        )
        v3.execSQL(
            "INSERT INTO source (connectionId, sourceId, name, writable, visible, personId, isMaster) " +
                "VALUES ('c1', 's1', 'Family', 1, 1, 'family', 1), ('c1', 's2', 'Alex', 0, 0, 'alex-id', 0)",
        )
        v3.execSQL(
            "INSERT INTO event (connectionId, sourceId, remoteId, title, startInstant, startDate, endInstant, endDate, " +
                "recurring, forPerson, createdBy, startSort, endSort) " +
                "VALUES ('c1', 's1', 'e1', 'Swim', 1000, NULL, 2000, NULL, 1, 'alex-id', 'sam-id', 1000, 2000)",
        )
        v3.execSQL(
            "INSERT INTO sync_state (connectionId, sourceId, cursor, rangeStart) " +
                "VALUES ('c1', 's1', 'k7', '2026-09-22|Europe/London')",
        )
        v3.execSQL(
            "INSERT INTO outbox (connectionId, sourceId, remoteId, kind, draftJson, attempts, nextAttemptMillis, createdMillis, clientKey) VALUES " +
                "('c1', 's1', NULL, 'CREATE', '$DRAFT_JSON', 0, 10, 100, 'k-1'), " +
                "('c1', 's1', 'e1', 'UPDATE', '$DRAFT_JSON', 1, 20, 200, NULL), " +
                "('c1', 's1', 'e1', 'ASSIGN', '$DRAFT_JSON', 0, 30, 300, NULL), " +
                "('c1', 's1', 'e1', 'DELETE', NULL, 2, 40, 400, NULL)",
        )
        v3.close()

        val v4 = helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4))
        try {
            assertThat(tableNames(v4)).containsExactly("connection", "event", "outbox", "source", "sync_state").inOrder()
        } finally {
            v4.close()
        }

        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val store = CalendarStore(db)
            val stored = store.connectionsNow().single()
            assertThat(stored.connection.config).containsExactly("account", "family@example.com")
            assertThat(stored.health).isEqualTo(ConnectionHealth.NeedsSignIn)
            // D16: an existing NEEDS_SIGN_IN connection's pause starts at the next NeedsSignIn a sync reports.
            assertThat(listOf(stored.sourcesCheckedMillis, stored.needsSignInSinceMillis)).containsExactly(null, null)
            assertThat(store.master().first()?.source?.id).isEqualTo("s1")
            assertThat(store.source("c1", "s2")!!.mapping).isEqualTo(SourceMapping(PersonId("alex-id"), visible = false))

            val event = store.eventNow(EventRef("c1", "s1", "e1"))!!
            assertThat(listOf(event.title, event.recurring, event.recurrenceRule)).containsExactly("Swim", true, null).inOrder()
            val range = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), ZoneId.of("Europe/London"))
            assertThat(store.cursor("c1", "s1", range)).isEqualTo(SyncCursor("k7"))

            val pending = store.pendingNow()
            assertThat(pending.map { it.kind })
                .containsExactly(ChangeKind.CREATE, ChangeKind.UPDATE, ChangeKind.ASSIGN, ChangeKind.DELETE).inOrder()
            assertThat(pending.map { it.clientKey }).containsExactly("k-1", null, null, null).inOrder()
            assertThat(pending.map { it.fields }).containsExactly(null, null, null, null)
            assertThat(pending.map { it.pausedMillis }).containsExactly(0L, 0L, 0L, 0L)
            assertThat(pending.first().draft?.forPersonColor).isNull()
            // A v3 row's fields are what it sent then: every field for an update, who for an assign.
            assertThat(fieldsFor(ChangeKind.UPDATE, pending[1].fields)).containsExactlyElementsIn(EventField.entries)
            assertThat(fieldsFor(ChangeKind.ASSIGN, pending[2].fields)).containsExactly(EventField.FOR_PERSON)
        } finally {
            db.close()
        }
    }
```

- [ ] **Step 2: Write the failing store tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`:

1. The health calls gain a time. In `healthRoundTripsIncludingTheErrorMessage`, replace the three `store.setHealth("c1", X)` calls with `store.setHealth("c1", X, 0L)` (for `ConnectionHealth.Error("quota exceeded")`, `ConnectionHealth.NeedsSignIn` and `ConnectionHealth.Unreachable`). In `markSyncedSetsOkAndTheTime`, replace `store.setHealth("c1", ConnectionHealth.NeedsSignIn)` with `store.setHealth("c1", ConnectionHealth.NeedsSignIn, 0L)`.

2. Add these members after `rescheduleAndDropChange`:
```kotlin
    private suspend fun rowsFor(sourceId: String): Triple<Int, SyncCursor?, Int> = Triple(
        titlesBetween(22, 30).size,
        store.cursor("c1", sourceId, window),
        store.pendingNow().count { it.sourceId == sourceId },
    )

    private fun listed(id: String, writable: Boolean = false, shown: Boolean = true, primary: Boolean = false) =
        CalendarSource(id, id.uppercase(), writable, shown, primary)

    private fun mapping(person: String) = { _: CalendarSource -> SourceMapping(PersonId(person), visible = true) }

    @Test
    fun aConnectionAndEverythingItHoldsIsRemovedTogether() = runTest {
        connect("s1", "s2")
        store.setMaster("c1", "s1")
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null))
        store.removeConnection("c1")
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(store.sources().first()).isEmpty()
        assertThat(titlesBetween(22, 30)).isEmpty()
        assertThat(store.cursor("c1", "s1", window)).isNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.master().first()).isNull()
    }

    @Test
    fun refreshingAddsNewSourcesWithTheirMappingAndKeepsExistingMappings() = runTest {
        connect("s1", mapping = mapOf("s1" to SourceMapping(PersonId("alex"), visible = true)))
        store.refreshSources("c1", listOf(listed("s1"), listed("s2")), 5_000L, mapping("mia"))
        assertThat(store.sources().first().associate { it.source.id to it.mapping.person })
            .containsExactly("s1", PersonId("alex"), "s2", PersonId("mia"))
    }

    @Test
    fun refreshingFollowsTheTicksButKeepsThePrimaryVisible() = runTest {
        connect("s1", "s2")
        store.refreshSources("c1", listOf(listed("s1", shown = false), listed("s2", shown = false, primary = true)), 5_000L, mapping("mia"))
        assertThat(store.sources().first().associate { it.source.id to it.mapping.visible }).containsExactly("s1", false, "s2", true)
        store.refreshSources("c1", listOf(listed("s1", shown = true), listed("s2", primary = true)), 6_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
    }

    @Test
    fun refreshingUpdatesNamesAndWritability() = runTest {
        connect("s1")
        store.refreshSources("c1", listOf(CalendarSource("s1", "Swimming club", writable = true)), 5_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.source.let { it.name to it.writable }).isEqualTo("Swimming club" to true)
    }

    @Test
    fun refreshingRemovesAGoneSourceWithItsEventsCursorAndQueue() = runTest {
        connect("s1", "s2")
        store.applySync("c1", "s2", window, full(timed("a", "Walk", 23, 9)))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null).copy(sourceId = "s2"))
        val cleared = store.refreshSources("c1", listOf(listed("s1")), 5_000L, mapping("mia"))
        assertThat(cleared).isFalse()
        assertThat(store.sources().first().map { it.source.id }).containsExactly("s1")
        assertThat(rowsFor("s2")).isEqualTo(Triple(0, null, 0))
    }

    @Test
    fun refreshingWithoutTheMasterClearsItAndRemovesItsRows() = runTest {
        connect("s1", "s2")
        store.setMaster("c1", "s1")
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null))
        val cleared = store.refreshSources("c1", listOf(listed("s2")), 5_000L, mapping("mia"))
        assertThat(cleared).isTrue()
        assertThat(store.master().first()).isNull()
        assertThat(rowsFor("s1")).isEqualTo(Triple(0, null, 0))
    }

    @Test
    fun aMasterThatBecomesReadOnlyIsClearedButStays() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        val cleared = store.refreshSources("c1", listOf(listed("s1", writable = false)), 5_000L, mapping("mia"))
        assertThat(cleared).isTrue()
        assertThat(store.master().first()).isNull()
        assertThat(store.source("c1", "s1")!!.source.writable).isFalse()
    }

    @Test
    fun aWritableMasterStaysTheMasterThroughARefresh() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        assertThat(store.refreshSources("c1", listOf(listed("s1", writable = true)), 5_000L, mapping("mia"))).isFalse()
        assertThat(store.master().first()?.source?.id).isEqualTo("s1")
    }

    @Test
    fun refreshingRecordsWhenItChecked() = runTest {
        connect("s1")
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isNull()
        store.refreshSources("c1", listOf(listed("s1")), 5_000L, mapping("mia"))
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isEqualTo(5_000L)
    }

    @Test
    fun aSyncOrAcceptedWriteForARemovedSourceWritesNothing() = runTest {
        connect("s1")
        val queued = store.enqueue(change(ChangeKind.UPDATE, remoteId = "a"))
        store.removeConnection("c1")
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        store.applyAccepted("c1", "s1", timed("a", "Walk", 23, 9), zone, completing = queued)
        assertThat(titlesBetween(22, 30)).isEmpty()
        assertThat(store.cursor("c1", "s1", window)).isNull()
    }

    @Test
    fun makeDueBringsAConnectionsQueueDueWithoutCountingAnAttempt() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, draft = null, next = 90_000L, attempts = 3))
        store.makeDue("c1", 1_000L)
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis }).isEqualTo(3 to 1_000L)
    }

    @Test
    fun addingAConnectionCanMakeOneOfItsSourcesTheMaster() = runTest {
        store.addConnection(conn, listOf(listed("s1", writable = true, primary = true)), emptyMap(), masterSourceId = "s1")
        assertThat(store.master().first()?.source?.id).isEqualTo("s1")
    }

    @Test
    fun aNeedsSignInLapseIsFoldedIntoEachRowWhenTheConnectionIsOk() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, draft = null).copy(createdMillis = 1_000L))
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 2_000L)
        assertThat(store.connectionsNow().single().needsSignInSinceMillis).isEqualTo(2_000L)
        store.setHealth("c1", ConnectionHealth.Ok, 10_000L)
        assertThat(store.pendingNow().single().pausedMillis).isEqualTo(8_000L)
        assertThat(store.connectionsNow().single().needsSignInSinceMillis).isNull()
    }

    @Test
    fun aRowQueuedDuringTheLapseIsPausedOnlyFromItsCreation() = runTest {
        connect("s1")
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 2_000L)
        store.enqueue(change(ChangeKind.DELETE, draft = null).copy(createdMillis = 5_000L))
        store.markSynced("c1", 10_000L)
        assertThat(store.pendingNow().single().pausedMillis).isEqualTo(5_000L)
        assertThat(store.connectionsNow().single().needsSignInSinceMillis).isNull()
    }

    @Test
    fun aRepeatedNeedsSignInKeepsWhenThePauseBeganAndOnlyOkEndsIt() = runTest {
        connect("s1")
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 2_000L)
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 4_000L)
        store.setHealth("c1", ConnectionHealth.Unreachable, 6_000L)
        store.setHealth("c1", ConnectionHealth.Error("quota exceeded"), 7_000L)
        val stored = store.connectionsNow().single()
        assertThat(stored.health).isEqualTo(ConnectionHealth.Error("quota exceeded"))
        assertThat(stored.needsSignInSinceMillis).isEqualTo(2_000L)
    }

    @Test
    fun anOkWithNoPauseRunningChangesNoRow() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, draft = null).copy(createdMillis = 1_000L))
        store.markSynced("c1", 10_000L)
        assertThat(store.pendingNow().single().pausedMillis).isEqualTo(0L)
    }

    @Test
    fun ageMillisCountsOnlyTimeTheConnectionWasNotWaitingForSignIn() {
        val change = change(ChangeKind.DELETE, draft = null).copy(createdMillis = 1_000L, pausedMillis = 100L)
        assertThat(ageMillis(change, pausedSince = null, nowMillis = 5_000L)).isEqualTo(3_900L)
        assertThat(ageMillis(change, pausedSince = 3_000L, nowMillis = 5_000L)).isEqualTo(1_900L)
        // Made during the running pause: no age yet.
        assertThat(ageMillis(change.copy(createdMillis = 4_000L, pausedMillis = 0L), pausedSince = 3_000L, nowMillis = 5_000L)).isEqualTo(0L)
    }

    @Test
    fun outboxKeepsItsFieldsPausedTimeAndTheColour() = runTest {
        connect("s1")
        val update = change(ChangeKind.UPDATE).copy(
            draft = eventDraft("Swim", 23, 9).copy(forPersonColor = 0xFF4CB387),
            fields = setOf(EventField.TITLE, EventField.TIMES),
            pausedMillis = 42L,
        )
        val id = store.enqueue(update)
        assertThat(store.pendingNow().single()).isEqualTo(update.copy(id = id))
    }

    @Test
    fun eventsKeepTheirRecurrenceRule() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "Swim", 23, 9).copy(recurring = true, recurrenceRule = "RRULE:FREQ=WEEKLY")))
        assertThat(store.eventNow(EventRef("c1", "s1", "a"))!!.recurrenceRule).isEqualTo("RRULE:FREQ=WEEKLY")
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt`, replace `store.setHealth("c1", ConnectionHealth.NeedsSignIn)` with `store.setHealth("c1", ConnectionHealth.NeedsSignIn, 0L)` and `store.setHealth("c2", ConnectionHealth.Unreachable)` with `store.setHealth("c2", ConnectionHealth.Unreachable, 0L)`.

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarMigrationTest*" --tests "*CalendarStoreTest*" --tests "*CalendarRepositoryTest*"`
Expected: compilation FAILS: `MIGRATION_3_4`, `removeConnection`, `refreshSources`, `makeDue`, `ageMillis` and the new fields are unresolved.

- [ ] **Step 4: The stored types**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Stored.kt`:

1. Replace `StoredConnection` with:
```kotlin
/**
 * [sourcesCheckedMillis]: when the sources were last refreshed from the provider; null before the first refresh
 * (3a design §3.4). [needsSignInSinceMillis]: when the running sign-in pause of the outbox age clock began; null
 * when none runs (3a design D16).
 */
data class StoredConnection(
    val connection: Connection,
    val health: ConnectionHealth,
    val lastSyncMillis: Long?,
    val sourcesCheckedMillis: Long? = null,
    val needsSignInSinceMillis: Long? = null,
)
```

2. In `StoredEvent`, after `val endSort: Long,` add:
```kotlin
    /** The series' RRULE line, for the Repeats row (3a design D12); null when unknown. */
    val recurrenceRule: String? = null,
```

3. In `PendingChange`, after `val clientKey: String? = null,` add:
```kotlin
    val fields: Set<EventField>? = null,
    val pausedMillis: Long = 0,
```
and add to its KDoc, before "[id] is 0 until the store assigns one.": `[fields] are what an UPDATE changes (3a design C3); null on a row queued before v4, which sent every field. [pausedMillis] is the sign-in pause already folded into its age (D16).`

- [ ] **Step 5: The entities, v4 and `MIGRATION_3_4`**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`:

1. In `ConnectionEntity`, after `val lastSyncMillis: Long?,` add:
```kotlin
    /** v4: when the sources were last refreshed from the provider. */
    val sourcesCheckedMillis: Long? = null,
    /** v4 (D16): when the running sign-in pause of the outbox age clock began; null when none runs. */
    val needsSignInSinceMillis: Long? = null,
```
2. In `EventEntity`, after `val endSort: Long,` add:
```kotlin
    /** v4: the series' RRULE line; null when unknown. */
    val recurrenceRule: String? = null,
```
3. In `OutboxEntity`, after `val clientKey: String? = null,` add:
```kotlin
    /** v4, UPDATE only: the EventField names it changes, comma-separated; null on an older row (every field). */
    val fields: String? = null,
    /** v4 (D16): the sign-in pause already folded into this row's age. */
    @ColumnInfo(defaultValue = "0") val pausedMillis: Long = 0,
```
4. In `CalendarDao`, after `suspend fun markSynced(id: String, at: Long)` add:
```kotlin
    @Query("SELECT * FROM connection WHERE id = :id")
    suspend fun connection(id: String): ConnectionEntity?

    @Query("DELETE FROM connection WHERE id = :id")
    suspend fun deleteConnection(id: String)

    @Query("UPDATE connection SET sourcesCheckedMillis = :at WHERE id = :id")
    suspend fun markSourcesChecked(id: String, at: Long)

    @Query("UPDATE connection SET needsSignInSinceMillis = :since WHERE id = :id")
    suspend fun setNeedsSignInSince(id: String, since: Long?)

    /** D16: adds the pause since [since] to each of the connection's rows, from when each was made. */
    @Query(
        "UPDATE outbox SET pausedMillis = pausedMillis + MAX(0, :now - MAX(:since, createdMillis)) " +
            "WHERE connectionId = :connectionId",
    )
    suspend fun addPause(connectionId: String, since: Long, now: Long)

    @Query("UPDATE outbox SET nextAttemptMillis = :now WHERE connectionId = :connectionId")
    suspend fun makeDue(connectionId: String, now: Long)

    @Query("DELETE FROM source WHERE connectionId = :connectionId")
    suspend fun deleteSourcesOf(connectionId: String)

    @Query("DELETE FROM event WHERE connectionId = :connectionId")
    suspend fun deleteEventsOf(connectionId: String)

    @Query("DELETE FROM sync_state WHERE connectionId = :connectionId")
    suspend fun deleteSyncStatesOf(connectionId: String)

    @Query("DELETE FROM outbox WHERE connectionId = :connectionId")
    suspend fun deleteOutboxOf(connectionId: String)

    @Query("DELETE FROM source WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun deleteSource(connectionId: String, sourceId: String)

    @Query("DELETE FROM sync_state WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun deleteSyncState(connectionId: String, sourceId: String)

    @Query("DELETE FROM outbox WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun deleteOutboxOfSource(connectionId: String, sourceId: String)

    @Query(
        "UPDATE source SET name = :name, writable = :writable, visible = :visible " +
            "WHERE connectionId = :connectionId AND sourceId = :sourceId",
    )
    suspend fun updateSource(connectionId: String, sourceId: String, name: String, writable: Boolean, visible: Boolean)
```
5. Replace `version = 3,` with `version = 4,`.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/Migrations.kt`, add at the end of the file:
```kotlin

/**
 * v4 (Plan 3a): the series' RRULE on each event; an update's touched fields and the paused age (D16) on the outbox;
 * when a connection's sources were last refreshed and when its sign-in pause began (D16). The SQL must match
 * schemas/…/4.json exactly.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `event` ADD COLUMN `recurrenceRule` TEXT")
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `fields` TEXT")
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `pausedMillis` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `connection` ADD COLUMN `sourcesCheckedMillis` INTEGER")
        db.execSQL("ALTER TABLE `connection` ADD COLUMN `needsSignInSinceMillis` INTEGER")
    }
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`, add `import uk.co.siland.culvery.capability.calendar.db.MIGRATION_3_4` and replace `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` with `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.

- [ ] **Step 6: The store**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`:

1. Replace `addConnection` (with its KDoc) with:
```kotlin
    /**
     * One transaction, so the sync loop never sees a connection without its sources. A source missing from
     * [mapping] shows as Family. [masterSourceId] makes that source the master, clearing any other (3a design D4).
     */
    suspend fun addConnection(
        connection: Connection,
        sources: List<CalendarSource>,
        mapping: Map<String, SourceMapping>,
        masterSourceId: String? = null,
    ) = db.withTransaction {
        dao.insertConnection(
            ConnectionEntity(
                id = connection.id,
                providerId = connection.providerId,
                label = connection.label,
                configJson = encodeConfig(connection.config),
                health = ConnectionHealth.Ok.code(),
                healthMessage = null,
                lastSyncMillis = null,
            ),
        )
        dao.insertSources(
            sources.map { s ->
                val m = mapping[s.id] ?: SourceMapping.Default
                SourceEntity(connection.id, s.id, s.name, s.writable, m.visible, m.person.value)
            },
        )
        if (masterSourceId != null) {
            dao.clearMaster()
            require(dao.markMaster(connection.id, masterSourceId) == 1) { "No source $masterSourceId in ${connection.id}" }
        }
    }

    /** The connection with its sources, events, cursors and queued changes, in one transaction (3a design D5). */
    suspend fun removeConnection(connectionId: String) = db.withTransaction {
        dao.deleteOutboxOf(connectionId)
        dao.deleteSyncStatesOf(connectionId)
        dao.deleteEventsOf(connectionId)
        dao.deleteSourcesOf(connectionId)
        dao.deleteConnection(connectionId)
    }

    /**
     * Follows the provider's list of [sources] (3a design §3.4), in one transaction: a new source is added with
     * [mappingForNew]; an existing one keeps its person and takes the listed name, writability and visibility (the
     * primary stays visible); one no longer listed goes with its events, cursor and queued changes. Returns true when
     * the master went, or became read-only, and was cleared.
     */
    suspend fun refreshSources(
        connectionId: String,
        sources: List<CalendarSource>,
        nowMillis: Long,
        mappingForNew: (CalendarSource) -> SourceMapping,
    ): Boolean = db.withTransaction {
        val stored = dao.sources(connectionId).associateBy { it.sourceId }
        val listed = sources.associateBy { it.id }
        var masterCleared = false
        stored.values.filter { it.sourceId !in listed }.forEach { gone ->
            if (gone.isMaster) masterCleared = true
            removeSourceRows(connectionId, gone.sourceId)
        }
        sources.forEach { s ->
            val existing = stored[s.id]
            if (existing == null) {
                val m = mappingForNew(s)
                dao.insertSources(listOf(SourceEntity(connectionId, s.id, s.name, s.writable, m.visible, m.person.value)))
            } else {
                dao.updateSource(connectionId, s.id, s.name, s.writable, visible = s.shown || s.primary)
                if (existing.isMaster && !s.writable) {
                    dao.clearMaster()
                    masterCleared = true
                }
            }
        }
        dao.markSourcesChecked(connectionId, nowMillis)
        masterCleared
    }

    private suspend fun removeSourceRows(connectionId: String, sourceId: String) {
        dao.deleteOutboxOfSource(connectionId, sourceId)
        dao.deleteSyncState(connectionId, sourceId)
        dao.deleteEventsForSource(connectionId, sourceId)
        dao.deleteSource(connectionId, sourceId)
    }
```

2. Replace `setHealth` and `markSynced` with:
```kotlin
    /**
     * Also keeps the outbox's sign-in pause (3a design D16): the first NeedsSignIn starts it; Ok folds it into each of
     * the connection's rows and ends it; Unreachable and Error leave it running, since neither says access is back.
     */
    suspend fun setHealth(connectionId: String, health: ConnectionHealth, nowMillis: Long) = db.withTransaction {
        val row = dao.connection(connectionId) ?: return@withTransaction
        when {
            health == ConnectionHealth.NeedsSignIn && row.needsSignInSinceMillis == null -> dao.setNeedsSignInSince(connectionId, nowMillis)
            health == ConnectionHealth.Ok -> endPause(row, nowMillis)
        }
        dao.setHealth(connectionId, health.code(), (health as? ConnectionHealth.Error)?.message)
    }

    suspend fun markSynced(connectionId: String, atMillis: Long) = db.withTransaction {
        dao.connection(connectionId)?.let { endPause(it, atMillis) }
        dao.markSynced(connectionId, atMillis)
    }

    private suspend fun endPause(row: ConnectionEntity, nowMillis: Long) {
        val since = row.needsSignInSinceMillis ?: return
        dao.addPause(row.id, since, nowMillis)
        dao.setNeedsSignInSince(row.id, null)
    }

    /** Reconnect (3a design D6, follow-up m4): the connection's queued changes are tried at the next pass. */
    suspend fun makeDue(connectionId: String, nowMillis: Long) = dao.makeDue(connectionId, nowMillis)
```

3. In `applySync`, make the first line inside `db.withTransaction {`:
```kotlin
            // A source removed while this sync was in flight: no orphan events or stale cursor.
            if (dao.source(connectionId, sourceId) == null) return@withTransaction
```
and update its KDoc to `Touches only the event mirror and the cursor; queued changes in the outbox are never affected. Writes nothing once the source is gone.`

4. Replace `applyAccepted` with:
```kotlin
    /**
     * Puts a write the provider accepted into the mirror and, if it came from the outbox, completes it. Writes nothing
     * once the source is gone: its queued changes went with it.
     */
    suspend fun applyAccepted(connectionId: String, sourceId: String, event: RemoteEvent, zone: ZoneId, completing: Long? = null) =
        db.withTransaction {
            if (dao.source(connectionId, sourceId) == null) return@withTransaction
            dao.upsertEvents(listOf(event.toEntity(connectionId, sourceId, zone)))
            completing?.let { dao.deleteOutbox(it) }
        }
```

5. Replace `encodeDraft` and `decodeDraft` with:
```kotlin
private fun encodeDraft(d: EventDraft): String = JSONObject()
    .put("title", d.title)
    .put("start", encodeTime(d.start))
    .put("end", encodeTime(d.end))
    .put("forPerson", d.forPerson ?: JSONObject.NULL)
    .put("createdBy", d.createdBy ?: JSONObject.NULL)
    .put("forPersonColor", d.forPersonColor ?: JSONObject.NULL)
    .toString()

private fun decodeDraft(json: String): EventDraft {
    val o = JSONObject(json)
    return EventDraft(
        title = o.getString("title"),
        start = decodeTime(o.getJSONObject("start")),
        end = decodeTime(o.getJSONObject("end")),
        forPerson = o.stringOrNull("forPerson"),
        createdBy = o.stringOrNull("createdBy"),
        // A row from before v4 has no colour.
        forPersonColor = if (o.has("forPersonColor") && !o.isNull("forPersonColor")) o.getLong("forPersonColor") else null,
    )
}

private fun encodeFields(fields: Set<EventField>?): String? = fields?.joinToString(",") { it.name }

private fun decodeFields(text: String?): Set<EventField>? =
    text?.split(",")?.filter { it.isNotEmpty() }?.map(EventField::valueOf)?.toSet()
```

6. Replace `ConnectionEntity.toStored()` with:
```kotlin
private fun ConnectionEntity.toStored() = StoredConnection(
    Connection(id, providerId, label, decodeConfig(configJson)),
    healthOf(health, healthMessage),
    lastSyncMillis,
    sourcesCheckedMillis,
    needsSignInSinceMillis,
)
```

7. In `RemoteEvent.toEntity`, after `endSort = end.instantIn(zone).toEpochMilli(),` add `recurrenceRule = recurrenceRule,`. In `EventRow.toStored`, after `endSort = event.endSort,` add `recurrenceRule = event.recurrenceRule,`.

8. In `PendingChange.toEntity()`, after `clientKey = clientKey,` add:
```kotlin
    fields = encodeFields(fields),
    pausedMillis = pausedMillis,
```
In `OutboxEntity.toPending()`, after `clientKey = clientKey,` add:
```kotlin
    fields = decodeFields(fields),
    pausedMillis = pausedMillis,
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`, after `const val OUTBOX_MAX_AGE_MS = …` add:
```kotlin

/**
 * How long [change] has waited, not counting time its connection spent waiting for sign-in (3a design D16):
 * [PendingChange.pausedMillis] is the pause already folded in, and [pausedSince] starts the pause still running (null
 * when none runs); a change made during that pause has aged only from when it was made.
 */
internal fun ageMillis(change: PendingChange, pausedSince: Long?, nowMillis: Long): Long {
    val running = pausedSince?.let { (nowMillis - maxOf(it, change.createdMillis)).coerceAtLeast(0) } ?: 0L
    return nowMillis - change.createdMillis - change.pausedMillis - running
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`, in `sync`:
- replace `store.setHealth(conn.id, ConnectionHealth.Error("Provider not installed"))` with `store.setHealth(conn.id, ConnectionHealth.Error("Provider not installed"), clock.nowMillis())`;
- replace `store.setHealth(conn.id, worst)` with `store.setHealth(conn.id, worst, clock.nowMillis())`.

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarMigrationTest*" --tests "*CalendarStoreTest*" --tests "*CalendarRepositoryTest*"`
Expected: PASS. The build writes `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/4.json`.
- If the migration test can't find `4.json` in the assets, run the same command again: KSP writes the schema while compiling, which can come after the debug assets were merged on the first run.
- Open `4.json` and check: `outbox` has `fields` (TEXT, not `notNull`) and `pausedMillis` (INTEGER, `notNull`, `defaultValue` `'0'`); `event` has `recurrenceRule`; `connection` has `sourcesCheckedMillis` and `needsSignInSinceMillis`. If Room reports a schema mismatch, the migration's SQL differs from `4.json`: make the migration match it; never edit the JSON.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add capability/calendar
git commit -m "Store touched fields, recurrence rules, source refreshes and the paused outbox age (calendar.db v4)"
```

---

### Task 3: Crash-proofing the engine — the application scope, `Throwable` per source, flows that recover, the drain backoff, C2, the shared write lock, and the 999-variable check

**Files:**
- Modify: `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`
- Create: `app/src/test/java/uk/co/siland/culvery/ApplicationScopeTest.kt`
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/FlowRetry.kt`
- Create: `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/FlowRetryTest.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt`, `app/src/test/java/uk/co/siland/culvery/shell/Fakes.kt` (modify)
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarWriteLock.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoop.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CountingDao.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`, `CalendarSyncLoopTest.kt`, `CalendarStoreTest.kt`, `CalendarEditorTest.kt`, `StubEditor.kt`, `ui/EventDetailHostTest.kt`, `ui/EventEditorHostTest.kt` (modify)
- Test: `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`, `SampleRollbackTest.kt` (modify)

**Interfaces:**
- Consumes: `CalendarStore.setHealth(…, nowMillis)` (Task 2).
- Produces:
  - `fun retryDelayMillis(attempt: Long): Long` and `fun <T> Flow<T>.retryWithBackoff(onFailure: (Throwable) -> Unit): Flow<T>` in `uk.co.siland.culvery.core.plugin`
  - `internal val LoggingExceptionHandler: CoroutineExceptionHandler` in `:app`'s `AppModule.kt`
  - `@Singleton class CalendarWriteLock @Inject constructor() : Mutex`
  - `CalendarSync` internal constructor `(store, providers, zone, clock, io, timeoutMillis, writers, toaster, writeLock: CalendarWriteLock)`; `@Inject` constructor `(store, providers, writers, toaster, zone, clock, writeLock)`; `internal fun drainBackoffMillis(): Long?`
  - `CalendarSyncLoop` internal constructor gains `drainBackoff: () -> Long? = { null }` (last)
  - `CalendarEditor` internal constructor `(store, writers, access, toaster, zone, clock, scope, requestSync, io, attemptMillis, writeLock: CalendarWriteLock, newKey = ::newClientKey)`; `@Inject` constructor gains `writeLock: CalendarWriteLock` (last)
  - `CalendarStore` internal constructor `(db: CalendarDatabase, dao: CalendarDao)`; the `@Inject` one is unchanged
  - Test sources: `CountingDao(real: CalendarDao) : CalendarDao` with `mostBound: Int`

- [ ] **Step 1: Write the failing tests**

Create `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/FlowRetryTest.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowRetryTest {
    @Test
    fun retriesWaitOneSecondDoublingToAMinute() {
        assertThat((0L..7L).map(::retryDelayMillis))
            .containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L).inOrder()
    }

    @Test
    fun aFailingFlowStartsAgainAfterItsWaitAndEachFailureIsReported() = runTest {
        var starts = 0
        val failures = mutableListOf<String>()
        val flaky = flow {
            starts++
            if (starts < 3) throw IllegalStateException("store hiccup $starts")
            emit("ok")
        }
        val result = async { flaky.retryWithBackoff { failures += it.message.orEmpty() }.first() }
        runCurrent()
        assertThat(starts).isEqualTo(1)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(2)
        advanceTimeBy(2_000)
        runCurrent()
        assertThat(result.await()).isEqualTo("ok")
        assertThat(failures).containsExactly("store hiccup 1", "store hiccup 2").inOrder()
    }
}
```

Create `app/src/test/java/uk/co/siland/culvery/ApplicationScopeTest.kt`:
```kotlin
package uk.co.siland.culvery

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.di.AppModule

// Robolectric for android.util.Log, which the handler writes to.
@RunWith(AndroidJUnit4::class)
class ApplicationScopeTest {
    @Test
    fun anUncaughtFailureInAnApplicationJobIsLoggedAndItsSiblingsRun() = runBlocking {
        val scope = AppModule.applicationScope()
        try {
            scope.launch { error("a sync job failed") }.join()
            val sibling = CompletableDeferred<Unit>()
            scope.launch { sibling.complete(Unit) }
            withTimeout(5_000) { sibling.await() }
            assertThat(ShadowLog.getLogsForTag("Culvery").map { it.throwable?.message }).contains("a sync job failed")
        } finally {
            scope.cancel()
        }
    }
}
```

In `app/src/test/java/uk/co/siland/culvery/shell/Fakes.kt`, add at the end:
```kotlin
/** A capability whose hasTab flow fails once, as a store hiccup would, then says it has a tab. */
class FlakyTabCapability(override val id: String, override val order: Int) : Capability {
    private var failures = 1
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    override val hasTab: Flow<Boolean> = flow {
        if (failures-- > 0) throw IllegalStateException("store hiccup")
        emit(true)
    }
    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())
    @Composable override fun TabContent() {}
}
```

In `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt`:
1. Add the imports:
```kotlin
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.runner.RunWith
```
2. Annotate the class (the retries log through `android.util.Log`):
```kotlin
// Robolectric for android.util.Log: a failing capability flow is logged before it is retried.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShellViewModelTest {
```
3. Add at the end of the class:
```kotlin
    @Test
    fun aTabWhoseFlowFailsComesBackAfterTheRetry() = runTest {
        val vm = vm(setOf(FlakyTabCapability("calendar", order = 10)))
        vm.uiState.test {
            assertThat(expectMostRecentItem().tabs).isEmpty()
            advanceTimeBy(1_001)
            assertThat(expectMostRecentItem().tabs.map { it.id }).containsExactly("calendar")
        }
    }
```

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CountingDao.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.capability.calendar.db.CalendarDao

/** The real DAO, remembering the most variables a list query bound: API 30's SQLite allows 999 per statement. */
internal class CountingDao(private val real: CalendarDao) : CalendarDao by real {
    var mostBound = 0
        private set

    override suspend fun deleteEvents(connectionId: String, sourceId: String, ids: List<String>) {
        // The ids, plus the connection and the source.
        mostBound = maxOf(mostBound, ids.size + 2)
        real.deleteEvents(connectionId, sourceId, ids)
    }
}
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`, replace `removalsBeyondSqlitesVariableLimitAreApplied` with:
```kotlin
    @Test
    fun removalsBeyondSqlitesVariableLimitAreAppliedWithNoStatementOverIt() = runTest {
        val counting = CountingDao(db.calendarDao())
        val store = CalendarStore(db, counting)
        store.addConnection(conn, listOf(CalendarSource("s1", "S1", writable = false)), emptyMap())
        val many = (1..1_200).map { timed("e$it", "Event $it", 23, 9) }
        store.applySync("c1", "s1", window, full(*many.toTypedArray(), timed("keep", "Keep", 23, 10)))
        store.applySync(
            "c1", "s1", window,
            SyncResult(emptyList(), many.map { it.remoteId }, SyncCursor("k2"), fullReplace = false),
        )
        assertThat(store.eventsBetween(millis(23), millis(24)).first().map { it.title }).containsExactly("Keep")
        assertThat(counting.mostBound).isAtMost(999)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`:
1. Add the imports `import kotlinx.coroutines.withTimeout` and `import uk.co.siland.culvery.core.plugin.WallClock` (if not present) — `WallClock` already is; add `withTimeout` only.
2. Replace `engine()` (with its KDoc), `writingEngine(…)` and the construction in `aFailedDrainIsLoggedAndTheSyncStillRuns` so every engine is built in one place. Replace
```kotlin
    /** Provider calls stay on the test dispatcher, so the 1 s timeout runs on virtual time. */
    private suspend fun engine(): CalendarSync {
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        return CalendarSync(
            store, setOf(a, b), HouseholdZone(household), clock, EmptyCoroutineContext, timeoutMillis = 1_000,
            writers = emptySet(), toaster = SilentToaster,
        )
    }
```
with
```kotlin
    /** Every engine this class builds. Provider calls stay on the test dispatcher, so the 1 s timeout runs on virtual time. */
    private suspend fun engineWith(
        writers: Set<CalendarWriter>,
        toaster: Toaster,
        io: CoroutineContext = EmptyCoroutineContext,
        timeoutMillis: Long = 1_000,
        lock: CalendarWriteLock = CalendarWriteLock(),
    ): CalendarSync {
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        return CalendarSync(store, setOf(a, b), HouseholdZone(household), clock, io, timeoutMillis, writers, toaster, lock)
    }

    private suspend fun engine(): CalendarSync = engineWith(emptySet(), SilentToaster)
```
Replace `writingEngine` with:
```kotlin
    private suspend fun writingEngine(io: CoroutineContext = EmptyCoroutineContext, timeoutMillis: Long = 1_000): CalendarSync =
        engineWith(setOf(w), toaster, io, timeoutMillis)
```
In `aFailedDrainIsLoggedAndTheSyncStillRuns`, delete its `household.setLocation(…)` line (`engineWith` sets the location) and replace `val sync = CalendarSync(store, setOf(a, b), HouseholdZone(household), clock, EmptyCoroutineContext, 1_000, setOf(w), broken)` with:
```kotlin
        val sync = engineWith(setOf(w), broken)
```
3. Replace the comment and test `aCreateWhoseMirrorWriteFailsIsRetriedAndMakesOneEvent` with (C2: the row is rescheduled, not left due):
```kotlin
    @Test
    fun aCreateWhoseMirrorWriteFailsIsRetriedLaterAndMakesOneEvent() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        a.failWith = UnreachableException("reads are down")
        // The provider accepts the create, then the tablet can't store it (a full disk).
        calendar.useWriterConnection {
            it.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON event BEGIN SELECT RAISE(ABORT, 'disk full'); END")
        }
        sync.syncAll()
        // Rescheduled with the next backoff step, not left due: the loop would run a pass every second.
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis }).isEqualTo(2 to now.toEpochMilli() + 60_000)
        calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_event") }
        sync.syncAll()
        assertThat(w.calls).containsExactly("create:Swim")
        now = now.plusSeconds(60)
        sync.syncAll()
        // Sent twice with one key: the provider returned the event it had already made.
        assertThat(w.calls).containsExactly("create:Swim", "create:Swim")
        assertThat(w.created.keys).containsExactly(key)
        assertThat(store.eventNow(EventRef("c1", "s1", key))).isNotNull()
        assertThat(store.pendingNow()).isEmpty()
    }
```
4. Add at the end of the class:
```kotlin
    @Test
    fun aProviderThrowingAnErrorFlagsOnlyItsConnection() = runTest {
        connect("c1", "calendar.a", s1)
        connect("c2", "calendar.b", s1)
        a.failWith = StackOverflowError("provider recursed")
        b.events = { listOf(swim()) }
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Error("provider recursed"))
        assertThat(health("c2")).isEqualTo(ConnectionHealth.Ok)
        assertThat(cachedTitles()).containsExactly("Swim")
    }

    @Test
    fun aStoreFailureFailsThePassNotTheProvidersHealth() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = engine()
        calendar.useWriterConnection {
            it.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON event BEGIN SELECT RAISE(ABORT, 'disk full'); END")
        }
        assertThat(runCatching { sync.syncAll() }.exceptionOrNull()).isNotNull()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
        calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_event") }
        sync.syncAll()
        assertThat(cachedTitles()).containsExactly("Swim")
    }

    @Test
    fun aFailingDrainBacksTheLoopOffUntilADrainCompletes() = runTest {
        connect("c1", "calendar.a", s1)
        var toastsWork = false
        val flaky = object : Toaster {
            override fun show(message: String, icon: String) {
                if (!toastsWork) error("toasts are down")
            }
        }
        val sync = engineWith(setOf(w), flaky)
        assertThat(sync.drainBackoffMillis()).isNull()
        queue(ChangeKind.DELETE, draft = null, sourceId = "gone")
        sync.syncAll()
        assertThat(sync.drainBackoffMillis()).isEqualTo(30_000L)
        queue(ChangeKind.DELETE, draft = null, sourceId = "gone")
        sync.syncAll()
        assertThat(sync.drainBackoffMillis()).isEqualTo(60_000L)
        toastsWork = true
        sync.syncAll()
        assertThat(sync.drainBackoffMillis()).isNull()
    }

    @Test
    fun anEditQueuedWhileARefusedCreateIsDroppedGoesWithIt() = runTest {
        connect("c1", "calendar.a", s1)
        val lock = CalendarWriteLock()
        // Real threads for the write, so runTest can't skip virtual time while the test holds the lock.
        val sync = engineWith(setOf(w), toaster, io = Dispatchers.Default, timeoutMillis = PROVIDER_TIMEOUT_MS, lock = lock)
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        w.failWith = WriteRejectedException("Calendar is full")
        a.failWith = UnreachableException("reads are down")
        // As the editor does while it queues a change behind the create.
        lock.lock()
        val pass = launch { sync.syncAll() }
        withContext(Dispatchers.Default) { withTimeout(5_000) { w.entered.await() } }
        queue(ChangeKind.UPDATE, remoteId = key, draft = swimDraft("sam-id"))
        lock.unlock()
        pass.join()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save 2 changes to C1")
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoopTest.kt`:
1. Add `import kotlinx.coroutines.flow.flow`.
2. Add at the end of the class:
```kotlin
    @Test
    fun aFailedDrainHoldsTheNextPassBackWhateverTheQueueSays() = runTest {
        var count = 0
        CalendarSyncLoop(
            { count++ }, MutableStateFlow(listOf("c1")), backgroundScope,
            untilNextRetry = { -5_000L }, drainBackoff = { 30_000L },
        ).start()
        runCurrent()
        assertThat(count).isEqualTo(1)
        advanceTimeBy(29_999)
        runCurrent()
        assertThat(count).isEqualTo(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aFailingConnectionListIsReadAgainAndStillStartsAPass() = runTest {
        var reads = 0
        var count = 0
        val ids = flow {
            reads++
            if (reads == 1) throw IllegalStateException("database locked")
            emit(listOf("c1"))
        }
        CalendarSyncLoop({ count++ }, ids, backgroundScope).start()
        runCurrent()
        assertThat(count).isEqualTo(0)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(count).isEqualTo(1)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :core:plugin:testDebugUnitTest :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`
Expected: compilation FAILS: `retryWithBackoff`, `retryDelayMillis`, `CalendarWriteLock`, `drainBackoffMillis`, the `drainBackoff` parameter and `CalendarStore(db, dao)` are unresolved.

- [ ] **Step 3: The shared retry and the application scope**

Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/FlowRetry.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

private const val FIRST_RETRY_MS = 1_000L
private const val LAST_RETRY_MS = 60_000L
private const val MAX_DOUBLINGS = 6L

/** The wait before retrying after failure number [attempt] (from 0): 1 s, doubling each time, at most 60 s. */
fun retryDelayMillis(attempt: Long): Long =
    (FIRST_RETRY_MS shl attempt.coerceAtMost(MAX_DOUBLINGS).toInt()).coerceAtMost(LAST_RETRY_MS)

/**
 * Starts the flow again after a failure, waiting [retryDelayMillis], instead of ending it: a store hiccup must not
 * leave a tab, a card or the sync loop's trigger gone until the app restarts (3a design §3.12). [onFailure] logs it.
 */
fun <T> Flow<T>.retryWithBackoff(onFailure: (Throwable) -> Unit): Flow<T> = retryWhen { cause, attempt ->
    onFailure(cause)
    delay(retryDelayMillis(attempt))
    true
}
```

In `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`:
1. Add the imports:
```kotlin
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
```
2. Above `@Module`, add:
```kotlin
/**
 * An application job's uncaught failure is logged and the process lives on; with the SupervisorJob its siblings keep
 * running (3a design §3.12).
 */
internal val LoggingExceptionHandler = CoroutineExceptionHandler { _, e -> Log.e("Culvery", "An application job failed", e) }
```
3. Replace `fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` with:
```kotlin
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + LoggingExceptionHandler)
```

In `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`:
1. Replace `import kotlinx.coroutines.flow.catch` with `import android.util.Log` (keep the import list sorted: `android.util.Log` first) and add `import uk.co.siland.culvery.core.plugin.retryWithBackoff`.
2. Replace
```kotlin
                    cap.hasTab.onStart { emit(false) }.catch { emit(false) }
```
with
```kotlin
                    // onStart after the retry: a retry must not hide a tab that was showing.
                    cap.hasTab.retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read whether it has a tab; retrying", it) }
                        .onStart { emit(false) }
```
3. Replace
```kotlin
                ordered.map { it.cards().onStart { emit(emptyList()) }.catch { emit(emptyList()) } },
```
with
```kotlin
                ordered.map { cap ->
                    cap.cards().retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its Home cards; retrying", it) }
                        .onStart { emit(emptyList()) }
                },
```
4. At the end of the file add:
```kotlin

private const val TAG = "ShellViewModel"
```

- [ ] **Step 4: The write lock, the engine and the loop**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarWriteLock.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/**
 * The one lock around calendar writes (3a design §3.12): the editor holds it while it reads the queue and writes or
 * queues a change, and the drain while it drops a create with the changes behind it, so an edit being queued behind
 * that create either lands first (and goes with it) or finds no create.
 */
@Singleton
class CalendarWriteLock @Inject constructor() : Mutex by Mutex()
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`:

1. Replace the constructors with:
```kotlin
@Singleton
class CalendarSync internal constructor(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    private val zone: HouseholdZone,
    private val clock: WallClock,
    private val io: CoroutineContext,
    private val timeoutMillis: Long,
    private val writers: Set<@JvmSuppressWildcards CalendarWriter>,
    private val toaster: Toaster,
    private val writeLock: CalendarWriteLock,
) {
    @Inject
    constructor(
        store: CalendarStore,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        writers: Set<@JvmSuppressWildcards CalendarWriter>,
        toaster: Toaster,
        zone: HouseholdZone,
        clock: WallClock,
        writeLock: CalendarWriteLock,
    ) : this(store, providers, zone, clock, Dispatchers.IO, PROVIDER_TIMEOUT_MS, writers, toaster, writeLock)
```

2. After `private val passLock = Mutex()` add:
```kotlin

    // Passes in a row whose drain failed (m2); a completed drain resets it.
    @Volatile private var failedDrains = 0

    /** How long the loop waits at least after a failed drain (30 s, 1 min, 2 min, then 5 min); null after one completes. */
    internal fun drainBackoffMillis(): Long? = failedDrains.takeIf { it > 0 }?.let(::backoffMillis)
```

3. In `syncAll`, replace the `try { drainOutbox(…) } catch …` block with:
```kotlin
        try {
            drainOutbox(connections, window.zone)
            failedDrains = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The changes stay queued for a later pass; the sync must still run.
            failedDrains++
            Log.w(TAG, "The outbox drain failed ($failedDrains in a row); syncing anyway", e)
        }
```
and update the KDoc of `syncAll` to: `Delivers queued changes, then syncs each connection, and each source within it, independently. A provider failure only flags that connection (with its worst source's health) and never clears its cache; a store failure fails the pass, and the loop logs it and tries again.`

4. In `drainOutbox`'s local `drop`, replace
```kotlin
                droppedCreates += ref
                store.dropCreate(ref)
```
with
```kotlin
                droppedCreates += ref
                // Under the editor's lock: an edit being queued behind this create goes with it, or finds it gone.
                writeLock.withLock { store.dropCreate(ref) }
```

5. In `deliver`, replace
```kotlin
        if (outcome is WriteOutcome.Accepted) {
            store.applyAcceptedWrite(conn.id, source.id, change.remoteId, outcome, zone, completing = change.id)
        }
        return outcome
```
with
```kotlin
        if (outcome is WriteOutcome.Accepted) {
            try {
                store.applyAcceptedWrite(conn.id, source.id, change.remoteId, outcome, zone, completing = change.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // C2: sent again later, which is safe: creates are idempotent by key, updates and deletes by nature.
                Log.w(TAG, "The provider accepted a queued ${change.kind} but the tablet couldn't store it; retrying later", e)
                return WriteOutcome.Retry(blocksConnection = false)
            }
        }
        return outcome
```

6. Replace `syncSource` with:
```kotlin
    /**
     * The store calls sit outside the provider's try: a store failure fails the pass rather than reading as the
     * provider's health. Any Throwable from the provider (an Error included) flags only this source.
     */
    private suspend fun syncSource(
        provider: CalendarProvider,
        conn: Connection,
        source: CalendarSource,
        window: DateRange,
    ): ConnectionHealth {
        val cursor = store.cursor(conn.id, source.id, window)
        val result = try {
            callProvider { provider.sync(conn, source, window, cursor) }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "${conn.label} / ${source.name}: timed out", e)
            return ConnectionHealth.Unreachable
        } catch (e: CancellationException) {
            // Rethrows if this sync was really cancelled; otherwise the provider leaked a stray cancellation.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "${conn.label} / ${source.name}: cancelled inside the provider", e)
            return ConnectionHealth.Unreachable
        } catch (e: NeedsSignInException) {
            Log.w(TAG, "${conn.label} / ${source.name}: needs signing in again", e)
            return ConnectionHealth.NeedsSignIn
        } catch (e: UnreachableException) {
            Log.w(TAG, "${conn.label} / ${source.name}: unreachable", e)
            return ConnectionHealth.Unreachable
        } catch (e: Throwable) {
            Log.e(TAG, "${conn.label} / ${source.name}: failed", e)
            return ConnectionHealth.Error(e.message ?: e.javaClass.simpleName)
        }
        store.applySync(conn.id, source.id, window, result)
        return ConnectionHealth.Ok
    }
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoop.kt`:
1. Add `import uk.co.siland.culvery.core.plugin.retryWithBackoff`.
2. Replace the constructors with:
```kotlin
@Singleton
class CalendarSyncLoop internal constructor(
    private val syncAll: suspend () -> Unit,
    private val connectionIds: Flow<List<String>>,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = SYNC_INTERVAL_MS,
    private val untilNextRetry: suspend () -> Long? = { null },
    private val drainBackoff: () -> Long? = { null },
) : Startable {
    @Inject
    constructor(sync: CalendarSync, store: CalendarStore, clock: WallClock, @ApplicationScope scope: CoroutineScope) :
        this(
            sync::syncAll,
            store.connectionIds(),
            scope,
            SYNC_INTERVAL_MS,
            { store.nextAttemptMillis()?.let { it - clock.nowMillis() } },
            sync::drainBackoffMillis,
        )
```
3. In `start`, replace
```kotlin
            launch { connectionIds.distinctUntilChanged().collect { wake.trySend(Unit) } }
```
with
```kotlin
            launch {
                connectionIds.distinctUntilChanged()
                    .retryWithBackoff { Log.w(TAG, "Couldn't read the connections; retrying", it) }
                    .collect { wake.trySend(Unit) }
            }
```
4. Replace the last line of `nextWait` with:
```kotlin
        // After a failed drain, the queue's own times don't count: waiting a second would retry the failure (m2).
        val floor = maxOf(MIN_PASS_GAP_MS, drainBackoff() ?: 0L).coerceAtMost(intervalMillis)
        return (retry ?: intervalMillis).coerceIn(floor, intervalMillis)
```

- [ ] **Step 5: The editor takes the shared lock**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`:
1. Remove the imports `kotlinx.coroutines.sync.Mutex` (keep `kotlinx.coroutines.sync.withLock`).
2. In the internal constructor, after `private val attemptMillis: Long,` add `private val writeLock: CalendarWriteLock,`.
3. Replace the `@Inject` constructor with:
```kotlin
    @Inject
    constructor(
        store: CalendarStore,
        writers: Set<@JvmSuppressWildcards CalendarWriter>,
        access: AccessControl,
        toaster: Toaster,
        zone: HouseholdZone,
        clock: WallClock,
        @ApplicationScope scope: CoroutineScope,
        loop: CalendarSyncLoop,
        writeLock: CalendarWriteLock,
    ) : this(store, writers, access, toaster, zone, clock, scope, loop::requestSync, Dispatchers.IO, WRITE_ATTEMPT_MS, writeLock)
```
4. Replace
```kotlin
    // One write at a time, so two sheets on one event can't send their changes out of order. Held only around the
    // write itself, never while the PIN pad is up.
    private val writeLock = Mutex()
```
with nothing (the constructor's `writeLock` is that lock now, shared with the drain; its KDoc says why).

- [ ] **Step 6: The store's counting-DAO seam**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`:
1. Add `import uk.co.siland.culvery.capability.calendar.db.CalendarDao`.
2. Replace
```kotlin
class CalendarStore @Inject constructor(private val db: CalendarDatabase) {
    private val dao = db.calendarDao()
```
with
```kotlin
class CalendarStore internal constructor(private val db: CalendarDatabase, private val dao: CalendarDao) {
    @Inject constructor(db: CalendarDatabase) : this(db, db.calendarDao())
```

- [ ] **Step 7: Pass the lock at every construction**

- `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt`:
  - add `private val lock = CalendarWriteLock()` after `private var syncRequests = 0`;
  - in `editor(…)`, after `attemptMillis = WRITE_ATTEMPT_MS,` add `writeLock = lock,`;
  - in `drain(…)` and in `aCreateAcceptedButNotStoredIsDoneAndMakesOneEvent`, replace `EmptyCoroutineContext, PROVIDER_TIMEOUT_MS, setOf(writer), access.toasts,` with `EmptyCoroutineContext, PROVIDER_TIMEOUT_MS, setOf(writer), access.toasts, lock,`;
  - in both `noWriter` constructions, replace `WallClock { testScheduler.currentTime }, backgroundScope, {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,` with `WallClock { testScheduler.currentTime }, backgroundScope, {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS, lock,`.
- `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt`: replace `CoroutineScope(Dispatchers.Unconfined), {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,` with `CoroutineScope(Dispatchers.Unconfined), {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS, CalendarWriteLock(),`.
- `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHostTest.kt` and `ui/EventEditorHostTest.kt`: add `import uk.co.siland.culvery.capability.calendar.CalendarWriteLock`, and replace `scope, {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,` with `scope, {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS, CalendarWriteLock(),`.
- `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`: add `import uk.co.siland.culvery.capability.calendar.CalendarWriteLock`, and replace the two lines
```kotlin
        val sync = CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, clock)
        val editor = CalendarEditor(store, setOf(fake), access, toasts, zone, clock, backgroundScope, CalendarSyncLoop(sync, store, clock, backgroundScope))
```
  with
```kotlin
        val lock = CalendarWriteLock()
        val sync = CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, clock, lock)
        val editor = CalendarEditor(store, setOf(fake), access, toasts, zone, clock, backgroundScope, CalendarSyncLoop(sync, store, clock, backgroundScope), lock)
```
- `app/src/testDebug/java/uk/co/siland/culvery/SampleRollbackTest.kt`: add the same import, and replace `CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, WallClock { System.currentTimeMillis() })` with `CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, WallClock { System.currentTimeMillis() }, CalendarWriteLock())`.

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :core:plugin:testDebugUnitTest :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS.
- `capabilityFlowThatThrowsIsIgnored` keeps its always-failing capability: it now retries on virtual time (1 s, 2 s, … 60 s) in `viewModelScope`, and the test ends before those waits; the other capability's card shows from the start.
- If `anEditQueuedWhileARefusedCreateIsDroppedGoesWithIt` fails with the update still queued, the drop isn't under the lock: check Step 4.4.

- [ ] **Step 9: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add core/plugin capability/calendar app
git commit -m "Keep the app alive through provider errors and store failures, back the loop off after a failed drain, and share the write lock with the drain"
```

---

### Task 4: Crash-proofing the sheets — Try again reuses the key, the sheet's load failure, the editor's Delete wording, and no default `onEdit`

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHost.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHost.kt`
- Modify: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedWriter.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHostTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHostTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt` (modify)
- Test: `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt` (modify)

**Interfaces:**
- Consumes: `CalendarWriteLock` and the editor's constructor (Task 3).
- Produces:
  - `CalendarEditor.create(draft: EventDraft, clientKey: String): EditResult` (the host chooses the key); the internal constructor loses `newKey`
  - `const val COULD_NOT_OPEN = "Couldn't open the event — try again"`; `internal fun CalendarEditor.couldNotOpen()`; `internal fun CalendarEditor.couldNotDelete(label: String)`
  - `EventDetailHost(…, initialMode: DetailMode = DetailMode.Idle, onEdit: () -> Unit)` — `onEdit` has no default
  - `ScriptedWriter.dropNextReply: Boolean` — the next create makes its event, then throws `UnreachableException` (the connection dropped before the reply)

- [ ] **Step 1: Write the failing tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHostTest.kt`, add the import `import uk.co.siland.culvery.capability.calendar.COULD_NOT_OPEN` and add at the end of the class:
```kotlin
    @Test
    fun tryAgainAfterATabletFailureReusesTheKeyAndMakesOneEvent() {
        // The provider makes the event, the connection drops before its reply, and queueing it fails on the tablet.
        writer.dropNextReply = true
        runBlocking {
            calendar.useWriterConnection {
                it.execSQL("CREATE TRIGGER fail_outbox BEFORE INSERT ON outbox BEGIN SELECT RAISE(ABORT, 'disk full'); END")
            }
        }
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        waitForText(couldNotSave("Sample calendar", TRY_AGAIN))
        runBlocking { calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_outbox") } }
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        // The same key both times: the provider returned the event it had made.
        assertThat(writer.calls).containsExactly("create:Parents evening", "create:Parents evening")
        assertThat(writer.created).hasSize(1)
    }

    @Test
    fun aSheetThatCannotLoadClosesAndSaysSo() {
        // A row with neither a start time nor a start date can't be read, so loading the event throws.
        runBlocking {
            calendar.useWriterConnection {
                it.execSQL(
                    "INSERT INTO event (connectionId, sourceId, remoteId, title, startInstant, startDate, endInstant, endDate, " +
                        "recurring, forPerson, createdBy, startSort, endSort) " +
                        "VALUES ('c1', 's-family', 'broken', 'Broken', NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0)",
                )
            }
        }
        showEditor(EditorRequest.Edit(EventRef("c1", "s-family", "broken")))
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.toasts.messages).containsExactly(COULD_NOT_OPEN)
    }

    @Test
    fun aStoreFailureInTheEditorsDeleteToastsLikeTheDetailSheet() {
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        // An unreadable queued row, which reading the queue deletes, and a disk that refuses the delete.
        runBlocking {
            calendar.useWriterConnection {
                it.execSQL(
                    "INSERT INTO outbox (connectionId, sourceId, remoteId, kind, draftJson, attempts, nextAttemptMillis, createdMillis) " +
                        "VALUES ('c1', 's-family', 'dinner', 'MOVE', NULL, 0, 0, 0)",
                )
                it.execSQL("CREATE TRIGGER fail_outbox BEFORE DELETE ON outbox BEGIN SELECT RAISE(ABORT, 'disk full'); END")
            }
        }
        compose.onNodeWithTag("editor_delete").performClick()
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly(couldNotSave("Sample calendar", TRY_AGAIN))
        compose.onNodeWithTag("editor_failure").assertDoesNotExist()
        assertThat(deleting).isEmpty()
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHostTest.kt`, in `show`, replace
```kotlin
            EventDetailHost(EventRef("c1", "s-family", id), today, repo, editor, onClose = { closed++ })
```
with
```kotlin
            EventDetailHost(EventRef("c1", "s-family", id), today, repo, editor, onClose = { closed++ }, onEdit = {})
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt`:
1. In `editor(…)`, delete the line `newKey = { "key-${++keys}" },`, and replace the last sentence of its KDoc (`Client keys are "key-1", "key-2"… in the order Save is tapped.`) with nothing.
2. After `private fun TestScope.editor(…)`, add:
```kotlin
    /** As the sheet chooses them: "key-1", "key-2"… in the order Save is tapped. */
    private suspend fun CalendarEditor.create(draft: EventDraft): EditResult = create(draft, "key-${++keys}")
```
3. Replace `eachSaveUsesANewKey` with:
```kotlin
    @Test
    fun aCreateUsesTheKeyTheSheetGivesIt() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value), "chosen-by-the-sheet")).isEqualTo(EditResult.Done)
        assertThat(writer.created.keys).containsExactly("chosen-by-the-sheet")
    }
```

In `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`, add `import uk.co.siland.culvery.capability.calendar.newClientKey` and replace `editor.create(draft("Parents evening"))` with `editor.create(draft("Parents evening"), newClientKey())`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventEditorHostTest*" --tests "*EventDetailHostTest*" --tests "*CalendarEditorTest*"`
Expected: compilation FAILS: `dropNextReply`, `COULD_NOT_OPEN` and `create(draft, key)` are unresolved.

- [ ] **Step 3: The writer drops a reply**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedWriter.kt`:
1. After `var loseNextReply = false` add:
```kotlin
    /** The next create makes its event, then the connection drops before the reply (UnreachableException). */
    var dropNextReply = false
```
2. In `create`, after the `if (loseNextReply) { … }` block add:
```kotlin
        if (dropNextReply) {
            dropNextReply = false
            throw UnreachableException("connection reset")
        }
```

- [ ] **Step 4: The editor takes the sheet's key and can say it couldn't open or delete**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`:
1. After `const val CHANGES_SAVED = "Changes saved"` add:
```kotlin

/** 3a design §3.12: the add/edit sheet couldn't load what it needs, so it closed. */
const val COULD_NOT_OPEN = "Couldn't open the event — try again"
```
2. In the internal constructor, delete `private val newKey: () -> String = ::newClientKey,`.
3. After `internal fun refuseOtherWho(name: String) = toaster.show(cannotAddForOthers(name))` add:
```kotlin

    /** The add/edit sheet couldn't load (a store failure): it closes and says so. */
    internal fun couldNotOpen() = toaster.show(COULD_NOT_OPEN)

    /** The editor's Delete failed before it could ask to confirm (a store failure): the detail sheet's delete wording. */
    internal fun couldNotDelete(label: String) = toaster.show(couldNotSave(label, TRY_AGAIN))
```
4. Replace `create` and its KDoc with:
```kotlin
    /**
     * Adds [draft] to the master calendar (2b-2 design §3.3) as [clientKey], the id the event keeps. The sheet chooses
     * the key, and keeps it for a Try again after the tablet failed (3a design §3.12): if the provider did make the event,
     * the retry gets it back rather than making a second. Adults may add for anyone; a child only for themselves.
     * [EventDraft.createdBy] is ignored: the person who authorises is recorded. A queued create keeps its key for every
     * retry.
     */
    suspend fun create(draft: EventDraft, clientKey: String): EditResult {
        val to = master() ?: return EditResult.NotEditable
        val who = access.authorise(
            CalendarPermissions.CREATE,
            CalendarPermissions.CREATE_SELF,
            reason = PinReason.Save,
            allow = { person, granted -> mayCreateFor(granted, person, draft.forPerson) },
            refusal = Refusal.Toast(::cannotAddForOthers),
        ) ?: return EditResult.Cancelled
        val toSend = draft.copy(createdBy = who.person.id.value)
        return onAppScope(ChangeKind.CREATE, to.connection.label) {
            writeLock.withLock { attempt(to, ChangeKind.CREATE, remoteId = null, toSend, clientKey = clientKey) }
        }
    }
```

- [ ] **Step 5: The add/edit host keeps the key, survives its load, and words a failed Delete**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHost.kt`:
1. Add the imports:
```kotlin
import kotlinx.coroutines.CancellationException
import uk.co.siland.culvery.capability.calendar.newClientKey
```
2. Replace
```kotlin
    val opened: Opened? by produceState<Opened?>(null, request) { value = open(request, repo, editor) }
```
with
```kotlin
    val opened: Opened? by produceState<Opened?>(null, request) {
        value = try {
            open(request, repo, editor)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The sheet closes (no form) and says so, instead of the store failure killing the app.
            Log.w(TAG, "Couldn't open the add/edit sheet", e)
            editor.couldNotOpen()
            Opened(request, null, "")
        }
    }
```
3. After `var picker by remember(request) { mutableStateOf(EditorPicker.None) }` add:
```kotlin
    // Kept only after the tablet failed (TRY_AGAIN), so Try again can't make a second event (3a design §3.12).
    var createKey by remember(request) { mutableStateOf(newClientKey()) }
```
4. In `save()`, replace
```kotlin
            val result = when (val mode = form.mode) {
                EventForm.Mode.New -> editor.create(form.draft(createdBy = null))
                is EventForm.Mode.Edit -> editor.update(mode.original.ref, form.draft(createdBy = null))
            }
```
with
```kotlin
            val result = when (val mode = form.mode) {
                EventForm.Mode.New -> editor.create(form.draft(createdBy = null), createKey)
                is EventForm.Mode.Edit -> editor.update(mode.original.ref, form.draft(createdBy = null))
            }
            if (result != EditResult.Rejected(TRY_AGAIN)) createKey = newClientKey()
```
5. Replace `delete()` with:
```kotlin
    fun delete() {
        val mode = form.mode as? EventForm.Mode.Edit ?: return
        action.run {
            try {
                if (editor.mayDelete(mode.original.ref)) onDeleteAuthorised(mode.original.ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A delete, so the detail sheet's wording rather than the Save failure card.
                Log.w(TAG, "Couldn't start a delete", e)
                editor.couldNotDelete(loaded.label)
            }
        }
    }
```
6. Update the host's KDoc: after the sentence ending `…the tablet failing before the write; a queued or saved change closes it.` add ` Try again after a tablet failure reuses the create's key. A sheet that can't load closes with a toast.`

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHost.kt`, replace `    onEdit: () -> Unit = {},` with `    onEdit: () -> Unit,` so no caller can get a dead Edit button.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventEditorHostTest*" --tests "*EventDetailHostTest*" --tests "*CalendarEditorTest*"`
Expected: PASS.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar app
git commit -m "Reuse the create's key on Try again, close the sheet with a toast when it can't load, and word a failed editor Delete as a delete"
```

---

### Task 5: Touched fields (C3) and the person's colour — sheet → editor → outbox → drain

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/EventForm.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/PendingOverlay.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHost.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/EventFormTest.kt`, `PendingOverlayTest.kt`, `CalendarEditorTest.kt`, `StubEditor.kt`, `ui/EventEditorHostTest.kt`, `ui/EventDetailHostTest.kt` (modify)
- Test: `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt` (modify)

**Interfaces:**
- Consumes: `EventField`, `fieldsFor` (Task 1); `PendingChange.fields` (Task 2); the editor's constructor with `writeLock` (Task 3); `create(draft, key)` (Task 4).
- Produces:
  - `EventForm.touched: Set<EventField>` — what an edit changed from the event as it opened; every field for a new event. `unchanged` is `touched.isEmpty()` for an edit.
  - `internal fun StoredEvent.withFields(d: EventDraft, fields: Set<EventField>, zone: ZoneId): StoredEvent` (in `PendingOverlay.kt`); the overlay applies only a queued change's fields
  - `internal fun assignDraft(event: StoredEvent, forPerson: String?, forPersonColor: Long?): EventDraft`
  - `CalendarEditor.update(ref: EventRef, draft: EventDraft, fields: Set<EventField>): EditResult`
  - `CalendarEditor` internal constructor gains `personOf: suspend (PersonId) -> Person?` (last); `@Inject` constructor gains `household: HouseholdRepository` (last)
  - A queued UPDATE stores its fields; the drain sends `fieldsFor(UPDATE, change.fields)`

- [ ] **Step 1: Write the failing tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/EventFormTest.kt`, add at the end of the class:
```kotlin
    @Test
    fun anEditTouchesOnlyWhatChanged() {
        val form = editTimed(LocalDateTime.of(2026, 9, 23, 19, 30), 90)
        assertThat(form.touched).isEmpty()
        form.updateTitle("Dinner at Gran's")
        assertThat(form.touched).containsExactly(EventField.TITLE)
        form.chooseLength(Duration.ofHours(2))
        assertThat(form.touched).containsExactly(EventField.TITLE, EventField.TIMES)
        form.chooseWho(mia)
        assertThat(form.touched).containsExactly(EventField.TITLE, EventField.TIMES, EventField.FOR_PERSON)
    }

    @Test
    fun aTitleChangedAndChangedBackIsNotTouched() {
        val form = editTimed(LocalDateTime.of(2026, 9, 23, 19, 30), 90)
        form.updateTitle("Dinner")
        form.updateTitle("Dinner with Jo & Priya")
        assertThat(form.touched).isEmpty()
        assertThat(form.unchanged).isTrue()
    }

    @Test
    fun aNewEventTouchesEveryField() {
        assertThat(new().touched).containsExactlyElementsIn(EventField.entries)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/PendingOverlayTest.kt`, add at the end of the class:
```kotlin
    @Test
    fun aQueuedTitleEditShowsOverThePhonesNewTime() {
        // Queued with the times the sheet opened on; a phone has since moved the event to 11:00.
        val titleOnly = EventDraft("Renamed", at(9), at(10), forPerson = null, createdBy = null)
        val queued = change(1, ChangeKind.UPDATE, "a", titleOnly).copy(fields = setOf(EventField.TITLE))
        val shown = overlay(listOf(stored("a", 11)), listOf(queued)).single()
        assertThat(shown.event.title).isEqualTo("Renamed")
        assertThat(shown.event.start).isEqualTo(at(11))
        assertThat(shown.event.startSort).isEqualTo(at(11).instant.toEpochMilli())
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt`:
1. In `editor(…)`, after `writeLock = lock,` add `personOf = household::person,`.
2. In both `noWriter` constructions, replace `WRITE_ATTEMPT_MS, lock,` with `WRITE_ATTEMPT_MS, lock, household::person,`.
3. After the `CalendarEditor.create(draft)` extension (Task 4), add:
```kotlin
    /** A change to everything the sheet shows, as 2b-2's sheet sent it. */
    private suspend fun CalendarEditor.update(ref: EventRef, draft: EventDraft): EditResult = update(ref, draft, EventField.entries.toSet())
```
4. Add at the end of the class:
```kotlin
    /** The dinner as a phone left it: an hour later than [event] puts it. */
    private fun movedOnAPhone(createdBy: String?): RemoteEvent {
        val later = LocalDate.of(2026, 9, 23).atTime(20, 30).atZone(london).toInstant()
        return RemoteEvent("dinner", "dinner", EventTime.Timed(later), EventTime.Timed(later.plusSeconds(5_400)), false, null, createdBy)
    }

    @Test
    fun anEditSendsOnlyItsFieldsLaidOverTheEventAsItIsNow() = runTest {
        val access = testAccess(household)
        // The sheet opened on the event at 19:30; a phone moved it while the sheet was open.
        put(movedOnAPhone(createdBy = access.alex.id.value))
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("Dinner at Gran's"), setOf(EventField.TITLE))).isEqualTo(EditResult.Done)
        assertThat(writer.fieldSets.single()).containsExactly(EventField.TITLE)
        val sent = writer.drafts.single()
        assertThat(listOf(sent.title, sent.start, sent.forPerson, sent.createdBy))
            .containsExactly("Dinner at Gran's", movedOnAPhone(null).start, null, access.alex.id.value).inOrder()
    }

    @Test
    fun anOfflineTitleEditIsSentAsATitleOnlyUpdateAfterAPhoneMovedTheEvent() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("Dinner at Gran's"), setOf(EventField.TITLE))).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow().single().fields).containsExactly(EventField.TITLE)
        // Before the tablet is back online, a phone moves the event an hour later.
        put(movedOnAPhone(createdBy = access.alex.id.value))
        writer.failWith = null
        drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first()).syncAll()
        // Only the title goes: the provider keeps the phone's new time.
        assertThat(writer.fieldSets.last()).containsExactly(EventField.TITLE)
        assertThat(writer.drafts.last().title).isEqualTo("Dinner at Gran's")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aChangeOfWhoIsARetagOnlyWhenWhoIsTouched() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value, forPerson = access.mia.id.value))
        access.answer(TestAccess.MIA)
        // The sheet's draft names Family, but Who isn't among the touched fields: Mia may change her own title.
        assertThat(editor(access).update(ref("football"), edited("Football at the park", PersonId.FAMILY.value), setOf(EventField.TITLE)))
            .isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().forPerson).isEqualTo(access.mia.id.value)
    }

    @Test
    fun newEventsAndAssignsCarryThePersonsColourAndFamilyCarriesNone() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        editor.create(draft("Sleepover", access.mia.id.value))
        editor.create(draft("Pizza night", PersonId.FAMILY.value))
        editor.assign(ref("plumber"), access.sam.id)
        assertThat(writer.drafts.map { it.forPersonColor }).containsExactly(access.mia.color, null, access.sam.color).inOrder()
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHostTest.kt`:
1. Add `import uk.co.siland.culvery.capability.calendar.EventField`.
2. In `setUp`, replace `WRITE_ATTEMPT_MS, CalendarWriteLock(),` with `WRITE_ATTEMPT_MS, CalendarWriteLock(), household::person,`.
3. In `savingAnEditSendsTheChange`, after `assertThat(writer.calls).containsExactly("update:dinner")` add:
```kotlin
        // Only Length was changed, so only the times are sent (3a design C3).
        assertThat(writer.fieldSets.single()).containsExactly(EventField.TIMES)
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHostTest.kt`, replace `WRITE_ATTEMPT_MS, CalendarWriteLock(),` with `WRITE_ATTEMPT_MS, CalendarWriteLock(), household::person,`.

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt`, replace `WRITE_ATTEMPT_MS, CalendarWriteLock(),` with `WRITE_ATTEMPT_MS, CalendarWriteLock(), { null },` (it never writes, so it needs no colours).

In `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`:
1. Add `import uk.co.siland.culvery.capability.calendar.EventField`.
2. Replace `CalendarSyncLoop(sync, store, clock, backgroundScope), lock)` with `CalendarSyncLoop(sync, store, clock, backgroundScope), lock, household)`.
3. Replace `editor.update(added.ref, draft("Parents' evening at school"))` with `editor.update(added.ref, draft("Parents' evening at school"), setOf(EventField.TITLE))`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventFormTest*" --tests "*PendingOverlayTest*" --tests "*CalendarEditorTest*" --tests "*EventEditorHostTest*"`
Expected: compilation FAILS: `touched`, `update(ref, draft, fields)`, `personOf` and `PendingChange.fields` in the overlay are unresolved or unused.

- [ ] **Step 3: The form knows what was touched**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/EventForm.kt`, replace `unchanged` (with its KDoc) with:
```kotlin
    /**
     * What an edit changed from the event as it opened (3a design C3): the fields its update sends, so a change made
     * on a phone meanwhile to any other field is kept. A field changed and changed back isn't touched. A new event is
     * every field.
     */
    val touched: Set<EventField>
        get() {
            val o = original ?: return EventField.entries.toSet()
            val d = draft(createdBy = null)
            return buildSet {
                if (d.title != o.title.trim()) add(EventField.TITLE)
                if (d.start != o.start || d.end != o.end) add(EventField.TIMES)
                if (d.forPerson != o.forPerson) add(EventField.FOR_PERSON)
            }
        }

    /** An edit that changes nothing: the sheet just closes, with no PIN (2b-2 design §3.3). A new event never is. */
    val unchanged: Boolean get() = original != null && touched.isEmpty()
```

- [ ] **Step 4: A queued change shows only its fields**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/PendingOverlay.kt`:
1. Replace the `UPDATE` and `ASSIGN` branches of `overlayPending` with:
```kotlin
            ChangeKind.UPDATE, ChangeKind.ASSIGN -> {
                val ref = change.ref ?: continue
                val current = shown[ref] ?: continue
                if (draft != null) {
                    shown[ref] = ShownEvent(current.event.withFields(draft, fieldsFor(change.kind, change.fields), zone), syncing = true)
                }
            }
```
2. Replace `private fun StoredEvent.withDraft(…)` with:
```kotlin
/**
 * [d]'s values for [fields] only (3a design C3): a phone's change to a field the tablet didn't touch still shows. The
 * creator never changes here.
 */
internal fun StoredEvent.withFields(d: EventDraft, fields: Set<EventField>, zone: ZoneId): StoredEvent {
    val times = EventField.TIMES in fields
    val newStart = if (times) d.start else start
    val newEnd = if (times) d.end else end
    return copy(
        title = if (EventField.TITLE in fields) d.title else title,
        start = newStart,
        end = newEnd,
        forPerson = if (EventField.FOR_PERSON in fields) d.forPerson else forPerson,
        startSort = newStart.instantIn(zone).toEpochMilli(),
        endSort = newEnd.instantIn(zone).toEpochMilli(),
    )
}
```
3. In the `overlayPending` KDoc, replace `An update shows the draft's values; an assign shows only its person.` with `An update shows the draft's values for its fields; an assign shows only its person.`

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`, replace `assignDraft` with:
```kotlin
/** An assign as it is sent: the event's current title, times and creator, with the new person and their colour. */
internal fun assignDraft(event: StoredEvent, forPerson: String?, forPersonColor: Long?): EventDraft =
    EventDraft(event.title, event.start, event.end, forPerson, event.createdBy, forPersonColor)
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`, in `deliver`:
- replace `callWriter(io, timeoutMillis) { writer.update(conn, source, remoteId, draft, fieldsFor(ChangeKind.UPDATE, null)) }` with
```kotlin
                // Only the fields the sheet touched (3a design C3): a phone's change to the others is kept.
                callWriter(io, timeoutMillis) { writer.update(conn, source, remoteId, draft, fieldsFor(ChangeKind.UPDATE, change.fields)) }
```
- replace `val draft = assignDraft(current, change.draft?.forPerson)` with `val draft = assignDraft(current, change.draft?.forPerson, change.draft?.forPersonColor)`.

- [ ] **Step 5: The editor lays the touched fields over the event as it is now**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`:
1. Add the imports:
```kotlin
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
```
2. In the internal constructor, after `private val writeLock: CalendarWriteLock,` add `private val personOf: suspend (PersonId) -> Person?,`.
3. In the `@Inject` constructor, add the parameter `household: HouseholdRepository,` after `writeLock: CalendarWriteLock,`, and replace its delegation with:
```kotlin
    ) : this(
        store, writers, access, toaster, zone, clock, scope, loop::requestSync, Dispatchers.IO, WRITE_ATTEMPT_MS, writeLock,
        household::person,
    )
```
4. In `create`, replace `val toSend = draft.copy(createdBy = who.person.id.value)` with:
```kotlin
        val toSend = draft.copy(createdBy = who.person.id.value, forPersonColor = colorOf(draft.forPerson))
```
5. Replace `update` (with its KDoc) with:
```kotlin
    /**
     * Changes [fields] of [ref] to [draft]'s values (3a design C3): the sheet's touched fields, laid over the event as
     * re-read under the write lock, so a phone's change to the others is kept, in the mirror and the queue alike.
     * Everything else the provider holds, and the event's creator, is kept: [EventDraft.createdBy] is ignored. Needs
     * edit, or edit.own on an event this person created, checked again once the PIN pad has closed. A touched Who
     * that differs follows the add rule (2b-2 design §6).
     */
    suspend fun update(ref: EventRef, draft: EventDraft, fields: Set<EventField>): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        if (target.pending.any { it.kind == ChangeKind.DELETE }) return EditResult.NotEditable
        val shownPerson = asShown(target.event, target.pending, zone.current()).forPerson
        val retags = EventField.FOR_PERSON in fields && draft.forPerson != shownPerson
        val who = authoriseChange(target, PinReason.Edit, retags, draft.forPerson) ?: return EditResult.Cancelled
        return write(target, ChangeKind.UPDATE, who, edit = draft, fields = fields)
    }
```
6. Replace `write` (keep its KDoc) with:
```kotlin
    private suspend fun write(
        target: Target,
        kind: ChangeKind,
        who: Authorised?,
        forPerson: String? = null,
        edit: EventDraft? = null,
        fields: Set<EventField>? = null,
    ): EditResult = onAppScope(kind, target.to.connection.label) {
        writeLock.withLock {
            val ref = target.event.ref
            val pending = store.pendingNow().filter { it.ref == ref }
            val event = store.eventNow(ref) ?: queuedCreate(pending, target.event.sourcePerson, zone.current())
            when {
                pending.any { it.kind == ChangeKind.DELETE } ->
                    if (kind == ChangeKind.DELETE) EditResult.Queued else EditResult.NotEditable
                event == null -> if (kind == ChangeKind.DELETE) EditResult.Done else EditResult.NotEditable
                who != null && !mayChange(who.granted, Identified(who.person, who.role), createdByOf(event, pending)) -> {
                    toaster.show(cannotChangeOthers(who.person.name))
                    // As a refusal on the session shortcut does (2b-1 U2): the next tap asks for a PIN.
                    access.lock()
                    EditResult.Cancelled
                }
                else -> {
                    val draft = draftFor(kind, event, pending, forPerson, edit, fields)
                    if (pending.isNotEmpty()) {
                        queue(target.to, kind, ref.remoteId, draft, attempted = false, fields = fields)
                    } else {
                        attempt(target.to, kind, ref.remoteId, draft, fields = fields)
                    }
                }
            }
        }
    }
```
7. Replace `draftFor` (with its KDoc) with:
```kotlin
    /**
     * No draft for a delete. An assign sends the event as it is now, with the new person and their colour. An update
     * lays the sheet's [fields] over the event as shown now (its queued changes included) and keeps its creator.
     */
    private suspend fun draftFor(
        kind: ChangeKind,
        event: StoredEvent,
        pending: List<PendingChange>,
        forPerson: String?,
        edit: EventDraft?,
        fields: Set<EventField>?,
    ): EventDraft? = when (kind) {
        ChangeKind.DELETE -> null
        ChangeKind.ASSIGN -> assignDraft(event, forPerson, colorOf(forPerson))
        ChangeKind.UPDATE -> {
            val sheet = checkNotNull(edit) { "An update needs its draft" }
            val touched = checkNotNull(fields) { "An update needs its fields" }
            val shown = asShown(event, pending, zone.current()).withFields(sheet, touched, zone.current())
            EventDraft(shown.title, shown.start, shown.end, shown.forPerson, createdByOf(event, pending), colorOf(shown.forPerson))
        }
        ChangeKind.CREATE -> error("A create has no event to change")
    }

    /** The colour of the person [forPerson] names, for the provider's event colour; null for Family, untagged or someone gone. */
    private suspend fun colorOf(forPerson: String?): Long? =
        forPerson?.takeIf { it != PersonId.FAMILY.value }?.let { personOf(PersonId(it))?.color }
```
8. In `attempt`, add the parameter `fields: Set<EventField>? = null,` after `clientKey: String? = null,`; replace `fieldsFor(kind, null)` with `fieldsFor(kind, fields)`; and replace `is WriteOutcome.Retry -> queue(to, kind, remoteId, draft, attempted = true, clientKey = clientKey)` with:
```kotlin
            is WriteOutcome.Retry -> queue(to, kind, remoteId, draft, attempted = true, clientKey = clientKey, fields = fields)
```
9. In `queue`, add the parameter `fields: Set<EventField>? = null,` after `clientKey: String? = null,` and, in the `PendingChange(…)`, after `clientKey = clientKey,` add:
```kotlin
                // An UPDATE's touched fields, which the drain sends (3a design C3); nothing else stores fields.
                fields = fields.takeIf { kind == ChangeKind.UPDATE },
```
10. Replace the file's last function, `forPersonOf`, with:
```kotlin
/** The event as the sheets show it: its queued updates and assigns laid over it in order, each with only its fields. */
private fun asShown(event: StoredEvent, pending: List<PendingChange>, zone: ZoneId): StoredEvent =
    pending.fold(event) { shown, change ->
        val d = change.draft
        if (d != null && (change.kind == ChangeKind.UPDATE || change.kind == ChangeKind.ASSIGN)) {
            shown.withFields(d, fieldsFor(change.kind, change.fields), zone)
        } else {
            shown
        }
    }
```

- [ ] **Step 6: The sheet sends what it touched**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHost.kt`, in `save()`, replace
```kotlin
                is EventForm.Mode.Edit -> editor.update(mode.original.ref, form.draft(createdBy = null))
```
with
```kotlin
                is EventForm.Mode.Edit -> editor.update(mode.original.ref, form.draft(createdBy = null), form.touched)
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS. 2b-2's editor tests pass through the test's all-fields `update(ref, draft)`, which is what the sheet sent before.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add capability/calendar app
git commit -m "Send only the fields an edit touched, laid over the event as it is now, and colour events for their person"
```

---

### Task 6: The drain's Google follow-ups — the paused age (D16), `NeedsSignIn` from a write, the aged create (C9) and the assign outside the window (m3)

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt` (modify)

**Interfaces:**
- Consumes: `ageMillis`, `StoredConnection.needsSignInSinceMillis`, `setHealth(…, nowMillis)` (Task 2); `CalendarWriter.find`, `ScriptedWriter.findable` (Task 1); `assignDraft(event, forPerson, color)` (Task 5).
- Produces:
  - `WriteOutcome.Retry(blocksConnection: Boolean, needsSignIn: Boolean = false)`; `callWriter` sets `needsSignIn` for a `NeedsSignInException`
  - The drain: drops by `ageMillis(change, connection's pause, now) > OUTBOX_MAX_AGE_MS`; looks an aged create up with `find` before dropping it; looks an assign's event up with `find` when the mirror doesn't hold it; sets `NeedsSignIn` when a write needs it
  - The editor sets `NeedsSignIn` when a write needs it

- [ ] **Step 1: Write the failing tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`:

1. In `aQueuedAssignForAnEventThatIsGoneIsDroppedWithAToast`, replace `assertThat(w.calls).isEmpty()` with:
```kotlin
        // Not in the mirror, so the provider is asked first (m3); it has no such event.
        assertThat(w.calls).containsExactly("find:swim")
```
2. In `needsSignInRetriesWithTheNormalBackoff`, replace the comment `// The pass's own read flags the connection; the failed write only reschedules.` with `// The failed write flags the connection at once (3a design §3.7); the pass's own read agrees.`
3. Replace `aCreateDroppedAfterTwoDaysTakesItsQueuedChangesWithIt` with:
```kotlin
    @Test
    fun anAgedCreateTheProviderNeverMadeIsDroppedWithItsFollowers() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        val longAgo = now.minusMillis(OUTBOX_MAX_AGE_MS + 1)
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key, created = longAgo)
        queue(ChangeKind.UPDATE, remoteId = key, draft = swimDraft("sam-id"))
        sync.syncAll()
        // C9: asked for by its key before it is dropped.
        assertThat(w.calls).containsExactly("find:$key")
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save 2 changes to C1")
    }
```
4. Add at the end of the class:
```kotlin
    @Test
    fun anAgedCreateThatGoogleHasIsCompletedAndItsFollowersAreSent() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        val longAgo = now.minusMillis(OUTBOX_MAX_AGE_MS + 1)
        // The provider made the event, but its reply was lost; the delete queued behind it has waited as long.
        w.findable[key] = RemoteEvent(key, "Swim", swim().start, swim().end, recurring = false, "mia-id", "alex-id")
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key, created = longAgo)
        queue(ChangeKind.DELETE, remoteId = key, draft = null, created = longAgo)
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        assertThat(w.calls).containsExactly("find:$key", "delete:$key").inOrder()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.eventNow(EventRef("c1", "s1", key))).isNull()
        assertThat(toaster.messages).isEmpty()
    }

    @Test
    fun anAgedCreateWhoseLookupFailsWaitsForALaterPass() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key, created = now.minusMillis(OUTBOX_MAX_AGE_MS + 1))
        w.failWith = UnreachableException("offline")
        sync.syncAll()
        assertThat(w.calls).containsExactly("find:$key")
        // Rescheduled, not left due: the loop would spin.
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis }).isEqualTo(2 to now.toEpochMilli() + 60_000)
        assertThat(toaster.messages).isEmpty()
    }

    @Test
    fun anAssignForAnEventOutsideTheWindowIsSentOnceTheProviderFindsIt() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        w.findable["swim"] = swim()
        queue(ChangeKind.ASSIGN, draft = swimDraft("sam-id"))
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        assertThat(w.calls).containsExactly("find:swim", "update:swim").inOrder()
        assertThat(w.fieldSets.single()).containsExactly(EventField.FOR_PERSON)
        assertThat(w.drafts.single().forPerson).isEqualTo("sam-id")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aWriteThatNeedsSignInFlagsTheConnectionAndStartsThePause() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null)
        w.failWith = NeedsSignInException("expired")
        a.failWith = UnreachableException("offline")
        sync.syncAll()
        val stored = store.connectionsNow().single()
        // The read then found the network down, which doesn't end the pause (D16).
        assertThat(stored.health).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(stored.needsSignInSinceMillis).isEqualTo(now.toEpochMilli())
        assertThat(store.pendingNow()).hasSize(1)
    }

    @Test
    fun aChangeQueuedBeforeAThreeDayLapseSurvivesItAndAgesAgainAfterTheReconnect() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        val hour = 3_600_000L
        queue(ChangeKind.DELETE, draft = null, created = now.minusMillis(47 * hour))
        w.failWith = NeedsSignInException("expired")
        a.failWith = NeedsSignInException("expired")
        sync.syncAll()
        // A long weekend with Google's access lapsed: nothing ages.
        now = now.plusMillis(72 * hour)
        sync.syncAll()
        assertThat(store.pendingNow()).hasSize(1)
        assertThat(toaster.messages).isEmpty()
        // Reconnected (health Ok folds the lapse in), but Google is now unreachable, so the change waits on.
        store.setHealth("c1", ConnectionHealth.Ok, now.toEpochMilli())
        w.failWith = UnreachableException("offline")
        a.failWith = UnreachableException("offline")
        now = now.plusMillis(hour - 60_000)
        sync.syncAll()
        assertThat(store.pendingNow()).hasSize(1)
        // 48 hours of healthy time: dropped as before.
        now = now.plusMillis(60_001)
        sync.syncAll()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1")
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt`, add the import `import uk.co.siland.culvery.core.plugin.ConnectionHealth` and at the end of the class:
```kotlin
    @Test
    fun aSaveThatNeedsSignInIsQueuedAndFlagsTheConnectionAtOnce() = runTest {
        val access = testAccess(household)
        writer.failWith = NeedsSignInException("expired")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        val stored = store.connectionsNow().single()
        // The reconnect chip shows now, not at the next sync (3a design §3.7), and the queue stops ageing (D16).
        assertThat(stored.health).isEqualTo(ConnectionHealth.NeedsSignIn)
        assertThat(stored.needsSignInSinceMillis).isEqualTo(testScheduler.currentTime)
        assertThat(store.pendingNow()).hasSize(1)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSyncTest*" --tests "*CalendarEditorTest*"`
Expected: FAIL: no `find` is called, the aged create is dropped unasked, the three-day lapse drops the change, and no write sets `NeedsSignIn`.

- [ ] **Step 3: A write can say it needs sign-in**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`:
1. Replace the `Retry` class (with its KDoc) with:
```kotlin
    /**
     * Try again later. [blocksConnection]: the provider didn't answer, so its other changes should wait too.
     * [needsSignIn]: the connection needs signing in again, so its health says so at once (3a design §3.7).
     */
    data class Retry(val blocksConnection: Boolean, val needsSignIn: Boolean = false) : WriteOutcome
```
2. In `callWriter`, replace
```kotlin
    } catch (e: NeedsSignInException) {
        WriteOutcome.Retry(blocksConnection = true)
```
with
```kotlin
    } catch (e: NeedsSignInException) {
        WriteOutcome.Retry(blocksConnection = true, needsSignIn = true)
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`:
1. Add `import uk.co.siland.culvery.core.plugin.ConnectionHealth`.
2. In `attempt`, replace
```kotlin
            is WriteOutcome.Retry -> queue(to, kind, remoteId, draft, attempted = true, clientKey = clientKey, fields = fields)
```
with
```kotlin
            is WriteOutcome.Retry -> {
                // The reconnect chip shows at once rather than at the next sync (3a design §3.7).
                if (outcome.needsSignIn) store.setHealth(to.connection.id, ConnectionHealth.NeedsSignIn, clock.nowMillis())
                queue(to, kind, remoteId, draft, attempted = true, clientKey = clientKey, fields = fields)
            }
```

- [ ] **Step 4: The drain**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`:

1. Replace the `drainOutbox` KDoc and its body's `try { for (change in store.pendingNow()) { … } }` loop (keep the `finally`) with:
```kotlin
    /**
     * Delivers queued changes in the order they were made. An event's later changes wait while an earlier one is
     * waiting or has just failed, so they can't land first and be undone; they are rescheduled to its next attempt,
     * so the loop doesn't wake for them before it. Once a connection fails to answer, its other changes wait for
     * their next attempt instead of each costing a timeout. Changes nothing can deliver, changes the provider refuses,
     * and changes older than [OUTBOX_MAX_AGE_MS] are dropped, with one toast per connection, shown even if the pass
     * then fails. Age leaves out time the connection spent waiting for sign-in (3a design D16). An aged create is
     * first looked up by its key (C9): if the provider made it, it is completed and the changes behind it are sent
     * whatever their age. A dropped create takes the changes queued behind it with it (2b-2 design §5).
     */
    private suspend fun drainOutbox(connections: List<StoredConnection>, zone: ZoneId) {
        val now = clock.nowMillis()
        val byId = connections.associateBy { it.connection.id }
        // Each blocked event, with the time its earliest waiting change is next tried.
        val blockedRefs = mutableMapOf<EventRef, Long>()
        val blockedConnections = mutableSetOf<String>()
        val dropped = linkedMapOf<String, MutableList<String?>>()
        val droppedCreates = mutableSetOf<EventRef>()
        // Aged creates the provider turned out to have: the changes behind them are sent whatever their age.
        val foundCreates = mutableSetOf<EventRef>()

        suspend fun drop(change: PendingChange, label: String, reason: String?) {
            val ref = change.ref
            val count = if (change.kind == ChangeKind.CREATE && ref != null) {
                droppedCreates += ref
                // Under the editor's lock: an edit being queued behind this create goes with it, or finds it gone.
                writeLock.withLock { store.dropCreate(ref) }
            } else {
                store.dropChange(change.id)
                1
            }
            val reasons = dropped.getOrPut(label) { mutableListOf() }
            reasons += reason
            // The changes that went with a create have no reason of their own.
            repeat(count - 1) { reasons += null }
        }

        try {
            for (change in store.pendingNow()) {
                val ref = change.ref
                val stored = byId[change.connectionId]
                val label = stored?.connection?.label ?: REMOVED_CALENDAR
                // Already deleted, and counted, with its create.
                if (ref != null && ref in droppedCreates) continue
                val blockedUntil = ref?.let(blockedRefs::get)
                if (blockedUntil != null) {
                    // Not an attempt, so it costs no backoff step.
                    if (change.nextAttemptMillis < blockedUntil) store.reschedule(change.id, change.attempts, blockedUntil)
                    continue
                }
                val aged = (ref == null || ref !in foundCreates) &&
                    ageMillis(change, stored?.needsSignInSinceMillis, now) > OUTBOX_MAX_AGE_MS
                if (aged && change.kind != ChangeKind.CREATE) {
                    Log.w(TAG, "Dropping a queued ${change.kind} for ${change.connectionId}: unsent for 48 hours")
                    drop(change, label, null)
                    continue
                }
                if (change.nextAttemptMillis > now) {
                    ref?.let { blockedRefs[it] = change.nextAttemptMillis }
                    continue
                }
                if (change.connectionId in blockedConnections) {
                    val next = retryLater(change, now)
                    ref?.let { blockedRefs[it] = next }
                    continue
                }
                val source = store.source(change.connectionId, change.sourceId)
                val writer = stored?.let { s -> writers.firstOrNull { it.providerId == s.connection.providerId } }
                if (stored == null || source == null || writer == null || !change.isComplete()) {
                    Log.w(TAG, "Dropping a queued ${change.kind} for ${change.connectionId}/${change.sourceId}: nothing can deliver it")
                    drop(change, label, null)
                    continue
                }
                val outcome = if (aged) {
                    // C9: its reply may have been lost after the provider made it; ask before dropping it.
                    val found = callWriter(io, timeoutMillis) { writer.find(stored.connection, source.source, checkNotNull(change.clientKey)) }
                    if (found is WriteOutcome.Accepted && found.event == null) {
                        Log.w(TAG, "Dropping a queued CREATE for ${change.connectionId}: unsent for 48 hours, and never made")
                        drop(change, label, null)
                        continue
                    }
                    if (found is WriteOutcome.Accepted) complete(change, stored.connection, source.source, found, zone) else found
                } else {
                    deliver(change, stored.connection, source.source, writer, zone)
                }
                when (outcome) {
                    is WriteOutcome.Accepted -> if (aged && ref != null) foundCreates += ref
                    is WriteOutcome.Rejected -> drop(change, label, outcome.message)
                    is WriteOutcome.Retry -> {
                        val next = retryLater(change, now)
                        ref?.let { blockedRefs[it] = next }
                        if (outcome.blocksConnection) blockedConnections += change.connectionId
                        // The reconnect chip shows at once, and the queue stops ageing (3a design §3.7, D16).
                        if (outcome.needsSignIn) store.setHealth(change.connectionId, ConnectionHealth.NeedsSignIn, now)
                    }
                }
            }
        } finally {
```
(The `finally { dropped.forEach { … } }` block and the function's closing brace stay as they are.)

2. Replace `deliver` (with its KDoc) with:
```kotlin
    /** Sends one change and, when the provider accepts it, applies the result to the mirror and completes it. */
    private suspend fun deliver(
        change: PendingChange,
        conn: Connection,
        source: CalendarSource,
        writer: CalendarWriter,
        zone: ZoneId,
    ): WriteOutcome {
        // Checked outside callWriter, so a malformed change fails the drain instead of being retried as a write.
        val ref = change.ref
        val outcome = when (change.kind) {
            ChangeKind.CREATE -> {
                val draft = checkNotNull(change.draft)
                val key = checkNotNull(change.clientKey)
                // The key makes a retry safe: if an earlier attempt did create the event, the provider returns it.
                callWriter(io, timeoutMillis) { writer.create(conn, source, draft, key) }
            }
            ChangeKind.UPDATE -> {
                val remoteId = checkNotNull(change.remoteId)
                val draft = checkNotNull(change.draft)
                // Only the fields the sheet touched (3a design C3): a phone's change to the others is kept.
                callWriter(io, timeoutMillis) { writer.update(conn, source, remoteId, draft, fieldsFor(ChangeKind.UPDATE, change.fields)) }
            }
            ChangeKind.ASSIGN -> {
                val remoteId = checkNotNull(change.remoteId)
                val forPerson = change.draft?.forPerson
                val color = change.draft?.forPersonColor
                val current = ref?.let { store.eventNow(it) }
                val draft = if (current != null) {
                    assignDraft(current, forPerson, color)
                } else {
                    // m3: out of the mirror's window, not necessarily gone. Never PATCH blind: it could bring a deleted event back.
                    val found = callWriter(io, timeoutMillis) { writer.find(conn, source, remoteId) }
                    if (found !is WriteOutcome.Accepted) return found
                    val event = found.event ?: return WriteOutcome.Rejected(EVENT_GONE)
                    EventDraft(event.title, event.start, event.end, forPerson, event.createdBy, color)
                }
                callWriter(io, timeoutMillis) { writer.update(conn, source, remoteId, draft, fieldsFor(ChangeKind.ASSIGN, null)) }
            }
            ChangeKind.DELETE -> {
                val remoteId = checkNotNull(change.remoteId)
                callWriter(io, timeoutMillis) {
                    writer.delete(conn, source, remoteId)
                    null
                }
            }
        }
        return if (outcome is WriteOutcome.Accepted) complete(change, conn, source, outcome, zone) else outcome
    }

    /** Stores a write the provider accepted and completes its row; a store failure is retried later (C2). */
    private suspend fun complete(
        change: PendingChange,
        conn: Connection,
        source: CalendarSource,
        accepted: WriteOutcome.Accepted,
        zone: ZoneId,
    ): WriteOutcome =
        try {
            store.applyAcceptedWrite(conn.id, source.id, change.remoteId, accepted, zone, completing = change.id)
            accepted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sent again later, which is safe: creates are idempotent by key, updates and deletes by nature.
            Log.w(TAG, "The provider accepted a queued ${change.kind} but the tablet couldn't store it; retrying later", e)
            WriteOutcome.Retry(blocksConnection = false)
        }
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add capability/calendar
git commit -m "Pause the queue's age while sign-in is needed, flag it from a write, look aged creates and out-of-window assigns up first"
```

---

### Task 7: Sources — the default mapping, the daily refresh, a gone source, and connecting with defaults

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/DefaultMapping.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/SourceRefresher.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSetup.kt`
- Modify: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedProvider.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/DefaultMappingTest.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/SourceRefresherTest.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`, `CalendarSetupTest.kt`, `CalendarEditorTest.kt` (modify)
- Test: `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt`, `SampleAddTest.kt`, `SampleRollbackTest.kt` (modify)

**Interfaces:**
- Consumes: `CalendarSource.shown`/`primary`, `SourceGoneException` (Task 1); `refreshSources`, `addConnection(…, masterSourceId)`, `setHealth(…, now)`, `makeDue`, `removeConnection`, `StoredConnection.sourcesCheckedMillis` (Task 2).
- Produces:
  - `fun defaultMapping(source: CalendarSource, people: List<Person>): SourceMapping`; `internal fun namesPerson(summary: String, name: String): Boolean`
  - `const val SOURCE_REFRESH_MS = 24 * 60 * 60_000L`; `fun masterGone(serviceName: String): String`
  - `@Singleton class SourceRefresher` — internal constructor `(store, people: suspend () -> List<Person>, clock, toaster, io, timeoutMillis)`; `@Inject` constructor `(store, household: HouseholdRepository, clock, toaster)`; `fun flag(connectionId: String)`; `suspend fun refreshIfDue(provider: CalendarProvider, stored: StoredConnection)`
  - `CalendarSync` constructors gain `refresher: SourceRefresher` (last)
  - `CalendarSetup(store, providers, people: suspend () -> List<Person>, toaster: Toaster, clock: WallClock, io: CoroutineContext = Dispatchers.IO, timeoutMillis: Long = PROVIDER_TIMEOUT_MS, requestSync: () -> Unit = {})`; `@Inject` constructor `(store, providers, household, toaster, clock, loop)`
  - `CalendarSetup.connectWithDefaults(connection: Connection): Boolean`, `reconnect(connection: Connection): Boolean`, `connectionIds(): Flow<List<String>>`, `removeConnection(connectionId: String)`; `connect` and `setMaster` read sources on `io` under the timeout
  - `fun connected(service: String)`, `fun reconnected(service: String)`, `fun couldNotConnect(service: String)`: "{service} connected", "{service} reconnected", "Couldn't connect to {service} — try again"
  - `ScriptedProvider(id, sourceList, features, displayName: String = id)` with `sourcesFailWith: Throwable?`

- [ ] **Step 1: Write the failing tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedProvider.kt`:
1. Replace the class header and descriptor with:
```kotlin
internal class ScriptedProvider(
    id: String,
    var sourceList: List<CalendarSource> = emptyList(),
    features: Set<Feature> = setOf(Feature.READ),
    displayName: String = id,
) : CalendarProvider {
    override val descriptor = ProviderDescriptor(id, displayName, "event", features)
```
2. After `var failFor: Map<String, Throwable> = emptyMap()` add:
```kotlin
    /** When set, sources() throws it. */
    var sourcesFailWith: Throwable? = null
```
3. Replace `override suspend fun sources(conn: Connection): List<CalendarSource> = sourceList` with:
```kotlin
    override suspend fun sources(conn: Connection): List<CalendarSource> {
        sourcesFailWith?.let { throw it }
        return sourceList
    }
```

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/DefaultMappingTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class DefaultMappingTest {
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val people = listOf(mia, sam)

    private fun mapped(name: String, shown: Boolean = true, primary: Boolean = false) =
        defaultMapping(CalendarSource(name, name, writable = false, shown = shown, primary = primary), people)

    @Test
    fun thePrimaryIsFamilyAndAlwaysVisibleWhateverItIsCalled() {
        assertThat(mapped("Mia", shown = false, primary = true)).isEqualTo(SourceMapping(PersonId.FAMILY, visible = true))
    }

    @Test
    fun aCalendarNamingOnePersonIsTheirs() {
        assertThat(mapped("Mia").person).isEqualTo(mia.id)
        assertThat(mapped("Mia's swimming").person).isEqualTo(mia.id)
        assertThat(mapped("Mia’s swimming").person).isEqualTo(mia.id)
        assertThat(mapped("swimming with SAM").person).isEqualTo(sam.id)
    }

    @Test
    fun twoPeopleOrNoneIsFamily() {
        assertThat(mapped("Mia & Sam football").person).isEqualTo(PersonId.FAMILY)
        assertThat(mapped("Bin days").person).isEqualTo(PersonId.FAMILY)
    }

    @Test
    fun aNameInsideAnotherWordDoesNotCount() {
        assertThat(mapped("Samantha's book club").person).isEqualTo(PersonId.FAMILY)
        assertThat(mapped("Miami trip").person).isEqualTo(PersonId.FAMILY)
    }

    @Test
    fun visibilityFollowsTheTick() {
        assertThat(mapped("Mia", shown = false)).isEqualTo(SourceMapping(mia.id, visible = false))
        assertThat(mapped("Mia", shown = true)).isEqualTo(SourceMapping(mia.id, visible = true))
    }
}
```

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/SourceRefresherTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room and android.util.Log.
@RunWith(AndroidJUnit4::class)
class SourceRefresherTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private var now = 1_000_000L
    private val toaster = RecordingToaster()
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val primary = CalendarSource("family@example.com", "Family", writable = true, primary = true)
    private val swimming = CalendarSource("mia-swim", "Mia's swimming", writable = false)
    private val provider = ScriptedProvider(
        "calendar.google", sourceList = listOf(primary), features = setOf(Feature.READ, Feature.WRITE), displayName = "Google Calendar",
    )
    private val refresher by lazy { SourceRefresher(store, { listOf(mia) }, WallClock { now }, toaster, EmptyCoroutineContext, 1_000) }

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun connect() =
        store.addConnection(Connection("g1", "calendar.google", "Google", emptyMap()), listOf(primary), emptyMap(), masterSourceId = primary.id)

    private suspend fun refresh() = refresher.refreshIfDue(provider, store.connectionsNow().single())

    private suspend fun sourceIds() = store.sources().first().map { it.source.id }

    @Test
    fun theFirstPassAfterTheAppStartsAddsNewCalendarsMappedByName() = runTest {
        connect()
        provider.sourceList = listOf(primary, swimming)
        refresh()
        assertThat(store.source("g1", "mia-swim")!!.mapping).isEqualTo(SourceMapping(mia.id, visible = true))
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isEqualTo(now)
    }

    @Test
    fun theNextRefreshIsADayLater() = runTest {
        connect()
        refresh()
        provider.sourceList = listOf(primary, swimming)
        now += SOURCE_REFRESH_MS - 1
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id)
        now += 1
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id, swimming.id)
    }

    @Test
    fun aFlaggedConnectionIsRefreshedAtTheNextPass() = runTest {
        connect()
        refresh()
        provider.sourceList = listOf(primary, swimming)
        refresher.flag("g1")
        now += 60_000
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id, swimming.id)
    }

    @Test
    fun aMasterDeletedInGoogleIsClearedWithOneToast() = runTest {
        connect()
        provider.sourceList = listOf(swimming)
        refresh()
        assertThat(store.master().first()).isNull()
        assertThat(sourceIds()).containsExactly(swimming.id)
        refresher.flag("g1")
        refresh()
        assertThat(toaster.messages).containsExactly("Google Calendar: can't find the master calendar, so new events can't be added")
    }

    @Test
    fun aCalendarListThatFailsKeepsTheSourcesAndIsTriedAgain() = runTest {
        connect()
        provider.sourcesFailWith = UnreachableException("offline")
        provider.sourceList = listOf(primary, swimming)
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id)
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isNull()
        provider.sourcesFailWith = null
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id, swimming.id)
    }
}
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`:
1. Replace the `connect` helper with:
```kotlin
    private suspend fun connect(id: String, providerId: String, vararg sources: CalendarSource, mapping: Map<String, SourceMapping> = emptyMap()) {
        // The provider lists the same calendars, so the first pass's source refresh keeps them.
        listOf(a, b).firstOrNull { it.descriptor.id == providerId }?.sourceList = sources.toList()
        store.addConnection(Connection(id, providerId, id.uppercase(), emptyMap()), sources.toList(), mapping)
    }
```
2. In `engineWith`, replace `return CalendarSync(store, setOf(a, b), HouseholdZone(household), clock, io, timeoutMillis, writers, toaster, lock)` with:
```kotlin
        return CalendarSync(
            store, setOf(a, b), HouseholdZone(household), clock, io, timeoutMillis, writers, toaster, lock,
            SourceRefresher(store, { household.people.first() }, clock, toaster, io, timeoutMillis),
        )
```
3. In `hiddenSourcesAreNotSynced`, replace `connect("c1", "calendar.a", s1, s2, mapping = …)` with `connect("c1", "calendar.a", s1, s2.copy(shown = false), mapping = mapOf("s2" to SourceMapping(PersonId.FAMILY, visible = false)))`: an unticked calendar stays hidden through the refresh.
4. Add at the end of the class:
```kotlin
    @Test
    fun aSourceGoneFlagsARefreshThatRemovesIt() = runTest {
        connect("c1", "calendar.a", s1, s2)
        val sync = engine()
        sync.syncAll()
        // Deleted in the service: its list call says so before a refresh would.
        a.failFor = mapOf("s2" to SourceGoneException("calendar deleted"))
        a.sourceList = listOf(s1)
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(store.sources().first().map { it.source.id }).containsExactly("s1")
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt`:
1. Replace `drain(…)` with:
```kotlin
    /** The outbox drain as the sync loop runs it, [aheadMillis] after the test's clock, with this test's writer. */
    private fun TestScope.drain(access: TestAccess, aheadMillis: Long): CalendarSync {
        val clock = WallClock { testScheduler.currentTime + aheadMillis }
        return CalendarSync(
            store, emptySet(), HouseholdZone(household), clock, EmptyCoroutineContext, PROVIDER_TIMEOUT_MS, setOf(writer), access.toasts, lock,
            SourceRefresher(store, { household.people.first() }, clock, access.toasts, EmptyCoroutineContext, PROVIDER_TIMEOUT_MS),
        )
    }
```
2. In `aCreateAcceptedButNotStoredIsDoneAndMakesOneEvent`, replace from `val reads = ScriptedProvider("calendar.a").apply {` to `).syncAll()` with:
```kotlin
        val reads = ScriptedProvider("calendar.a", sourceList = listOf(family, school)).apply {
            events = { source -> if (source.id == family.id) writer.created.values.toList() else emptyList() }
        }
        val clock = WallClock { LocalDate.of(2026, 9, 23).atStartOfDay(london).toInstant().toEpochMilli() }
        CalendarSync(
            store, setOf(reads), HouseholdZone(household), clock, EmptyCoroutineContext, PROVIDER_TIMEOUT_MS, setOf(writer), access.toasts, lock,
            SourceRefresher(store, { household.people.first() }, clock, access.toasts, EmptyCoroutineContext, PROVIDER_TIMEOUT_MS),
        ).syncAll()
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSetupTest.kt`:
1. Replace the imports block with:
```kotlin
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.WallClock
```
2. After `private lateinit var store: CalendarStore` add:
```kotlin
    private val toaster = RecordingToaster()
    private var now = 1_000L
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)

    private fun setup(providers: Set<CalendarProvider>, onSync: () -> Unit = {}) =
        CalendarSetup(store, providers, { listOf(mia) }, toaster, WallClock { now }, EmptyCoroutineContext, 1_000, onSync)
```
3. Replace every construction: `CalendarSetup(store, setOf(provider))` → `setup(setOf(provider))`; `CalendarSetup(store, setOf(writable))` → `setup(setOf(writable))`; `CalendarSetup(store, setOf(writable)) { syncs++ }` → `setup(setOf(writable)) { syncs++ }`; `CalendarSetup(store, setOf(writable, provider))` → `setup(setOf(writable, provider))`.
4. Add at the end of the class:
```kotlin
    private val google = ScriptedProvider(
        "calendar.google",
        sourceList = listOf(
            CalendarSource("family@example.com", "Family", writable = true, primary = true),
            CalendarSource("mia", "Mia's swimming", writable = false),
            CalendarSource("holidays", "UK holidays", writable = false, shown = false),
        ),
        features = setOf(Feature.READ, Feature.WRITE),
        displayName = "Google Calendar",
    )
    private val googleConnection = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))

    @Test
    fun connectingWithDefaultsMakesThePrimaryTheMasterMapsByNameAndAsksForASync() = runTest {
        var syncs = 0
        assertThat(setup(setOf(google)) { syncs++ }.connectWithDefaults(googleConnection)).isTrue()
        assertThat(store.master().first()?.source?.id).isEqualTo("family@example.com")
        assertThat(store.sources().first().associate { it.source.id to it.mapping }).containsExactly(
            "family@example.com", SourceMapping(PersonId.FAMILY, visible = true),
            "mia", SourceMapping(mia.id, visible = true),
            "holidays", SourceMapping(PersonId.FAMILY, visible = false),
        )
        assertThat(syncs).isEqualTo(1)
        assertThat(toaster.messages).containsExactly("Google Calendar connected")
    }

    @Test
    fun aConnectThatFailsStoresNothingAndSaysSo() = runTest {
        google.sourcesFailWith = UnreachableException("offline")
        assertThat(setup(setOf(google)).connectWithDefaults(googleConnection)).isFalse()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't connect to Google Calendar — try again")
    }

    @Test
    fun aConnectThatTimesOutStoresNothingAndSaysSo() = runTest {
        google.sourcesFailWith = timeoutCancellation()
        assertThat(setup(setOf(google)).connectWithDefaults(googleConnection)).isFalse()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't connect to Google Calendar — try again")
    }

    @Test
    fun reconnectSetsHealthOkMakesTheQueueDueAndAsksForASync() = runTest {
        var syncs = 0
        val setup = setup(setOf(google)) { syncs++ }
        setup.connectWithDefaults(googleConnection)
        store.setHealth("g1", ConnectionHealth.NeedsSignIn, 500L)
        store.enqueue(
            PendingChange(0, "g1", "family@example.com", "e1", ChangeKind.DELETE, null, attempts = 3, nextAttemptMillis = 90_000L, createdMillis = 100L),
        )
        now = 5_000L
        assertThat(setup.reconnect(googleConnection)).isTrue()
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Ok)
        // Due now without an extra attempt, and the lapse (from 500) no longer counts towards its age (D16).
        assertThat(store.pendingNow().single().let { Triple(it.attempts, it.nextAttemptMillis, it.pausedMillis) })
            .isEqualTo(Triple(3, 5_000L, 4_500L))
        assertThat(syncs).isEqualTo(2)
        assertThat(toaster.messages).containsExactly("Google Calendar connected", "Google Calendar reconnected").inOrder()
    }

    @Test
    fun removingAConnectionTakesEverythingWithIt() = runTest {
        val setup = setup(setOf(google))
        setup.connectWithDefaults(googleConnection)
        setup.removeConnection("g1")
        assertThat(setup.connectionIds().first()).isEmpty()
        assertThat(store.sources().first()).isEmpty()
    }
```

In `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt`:
1. Add the imports `import uk.co.siland.culvery.core.plugin.Toaster` and `import uk.co.siland.culvery.core.plugin.WallClock`.
2. Above `@RunWith(AndroidJUnit4::class)` add:
```kotlin
private object NoToasts : Toaster {
    override fun show(message: String, icon: String) = Unit
}
```
3. Replace `setup = CalendarSetup(store, setOf(fake)) { syncs++ }` with:
```kotlin
        setup = CalendarSetup(store, setOf(fake), { household.people.first() }, NoToasts, WallClock { 0L }) { syncs++ }
```

In `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt` and `SampleRollbackTest.kt`:
1. Add `import uk.co.siland.culvery.capability.calendar.SourceRefresher` (and, in `SampleAddTest`, `import kotlinx.coroutines.flow.first` is already there).
2. Replace `CalendarSetup(store, setOf(fake))` with `CalendarSetup(store, setOf(fake), { household.people.first() }, toasts, WallClock { System.currentTimeMillis() })`.
3. In `SampleAddTest`, replace `CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, clock, lock)` with `CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, clock, lock, SourceRefresher(store, household, clock, toasts))`.
4. In `SampleRollbackTest`, replace `CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, WallClock { System.currentTimeMillis() }, CalendarWriteLock())` with:
```kotlin
CalendarSync(
            store, setOf(fake), setOf(fake), toasts, zone, WallClock { System.currentTimeMillis() }, CalendarWriteLock(),
            SourceRefresher(store, household, WallClock { System.currentTimeMillis() }, toasts),
        )
```
and add `import kotlinx.coroutines.flow.first` if it isn't there.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: compilation FAILS: `defaultMapping`, `SourceRefresher`, `connectWithDefaults`, `reconnect` and the new constructors are unresolved.

- [ ] **Step 3: The default mapping**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/DefaultMapping.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/**
 * How a newly found source is set up (3a design D4): the primary is Family and always visible, since the tablet's
 * events go there; any other calendar whose name names exactly one household person is theirs, otherwise Family;
 * visible when ticked in the service. Editing mappings is Plan 4's.
 */
fun defaultMapping(source: CalendarSource, people: List<Person>): SourceMapping {
    if (source.primary) return SourceMapping(PersonId.FAMILY, visible = true)
    val named = people.filter { !it.isFamily && namesPerson(source.name, it.name) }
    return SourceMapping(named.singleOrNull()?.id ?: PersonId.FAMILY, visible = source.shown)
}

/** [name] as a whole word in [summary], ignoring case, a trailing "'s" allowed: "Mia's swimming" names Mia; "Samantha" doesn't name Sam. */
internal fun namesPerson(summary: String, name: String): Boolean =
    Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?:['’]s)?(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(summary)
```

- [ ] **Step 4: The refresher**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/SourceRefresher.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** How often a connection's sources are read again from the provider (3a design D7). */
const val SOURCE_REFRESH_MS = 24 * 60 * 60_000L

/** 3a design D7: the master calendar went, or became read-only, so the add buttons hide. */
fun masterGone(serviceName: String): String = "$serviceName: can't find the master calendar, so new events can't be added"

/**
 * Follows the calendars ticked in the service (3a design §3.4, D7): at the start of a connection's part of a pass,
 * when it hasn't been refreshed since the app started, when its last refresh is a day old, or after [flag].
 */
@Singleton
class SourceRefresher internal constructor(
    private val store: CalendarStore,
    private val people: suspend () -> List<Person>,
    private val clock: WallClock,
    private val toaster: Toaster,
    private val io: CoroutineContext,
    private val timeoutMillis: Long,
) {
    @Inject
    constructor(store: CalendarStore, household: HouseholdRepository, clock: WallClock, toaster: Toaster) :
        this(store, { household.people.first() }, clock, toaster, Dispatchers.IO, PROVIDER_TIMEOUT_MS)

    private val refreshed = ConcurrentHashMap.newKeySet<String>()
    private val flagged = ConcurrentHashMap.newKeySet<String>()

    /** A sync found one of [connectionId]'s sources gone (SourceGoneException): refresh at the next pass. */
    fun flag(connectionId: String) {
        flagged += connectionId
    }

    /**
     * A failure to read the sources is logged and tried again at the next pass; the sync still runs on the sources
     * already stored. A store failure fails the pass, as any other.
     */
    suspend fun refreshIfDue(provider: CalendarProvider, stored: StoredConnection) {
        val id = stored.connection.id
        val now = clock.nowMillis()
        val checked = stored.sourcesCheckedMillis
        val due = id !in refreshed || id in flagged || checked == null || now - checked >= SOURCE_REFRESH_MS
        if (!due) return
        val sources = try {
            withContext(io) { withTimeout(timeoutMillis) { provider.sources(stored.connection) } }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "${stored.connection.label}: timed out reading its calendars", e)
            return
        } catch (e: CancellationException) {
            // Rethrows if this pass was really cancelled; otherwise the provider leaked a stray cancellation.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "${stored.connection.label}: reading its calendars was cancelled inside the provider", e)
            return
        } catch (e: Throwable) {
            Log.w(TAG, "${stored.connection.label}: couldn't read its calendars; trying again next pass", e)
            return
        }
        val household = people()
        val masterCleared = store.refreshSources(id, sources, now) { defaultMapping(it, household) }
        refreshed += id
        flagged -= id
        if (masterCleared) toaster.show(masterGone(provider.descriptor.displayName))
    }

    private companion object {
        const val TAG = "SourceRefresher"
    }
}
```

- [ ] **Step 5: The engine refreshes, and a gone source flags it**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`:
1. In the internal constructor, after `private val writeLock: CalendarWriteLock,` add `private val refresher: SourceRefresher,`. In the `@Inject` constructor, add `refresher: SourceRefresher,` after `writeLock: CalendarWriteLock,`, and pass it last: `…, writers, toaster, writeLock, refresher)`.
2. In `syncAll`, replace `connections.forEach { sync(it.connection, window) }` with `connections.forEach { sync(it, window) }`.
3. Replace the start of `sync` down to and including its `for` loop header with:
```kotlin
    private suspend fun sync(stored: StoredConnection, window: DateRange) {
        val conn = stored.connection
        val provider = providers.firstOrNull { it.descriptor.id == conn.providerId }
        if (provider == null) {
            store.setHealth(conn.id, ConnectionHealth.Error("Provider not installed"), clock.nowMillis())
            return
        }
        refresher.refreshIfDue(provider, stored)
        var worst: ConnectionHealth = ConnectionHealth.Ok
        for (source in store.visibleSourcesFor(conn.id)) {
```
(the loop body `val health = syncSource(provider, conn, source.source, window)` replaces `stored.source`: rename the loop variable's uses from `stored.source` to `source.source`.)
4. In `syncSource`, before `} catch (e: UnreachableException) {` add:
```kotlin
        } catch (e: SourceGoneException) {
            Log.w(TAG, "${conn.label} / ${source.name}: gone from the service; refreshing its calendars at the next pass", e)
            refresher.flag(conn.id)
            return ConnectionHealth.Unreachable
```

- [ ] **Step 6: Connecting with defaults, and reconnecting**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSetup.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** 3a design §3.3 toasts. */
fun connected(service: String): String = "$service connected"

fun reconnected(service: String): String = "$service reconnected"

fun couldNotConnect(service: String): String = "Couldn't connect to $service — try again"

/**
 * Adds and reconnects provider connections and chooses the master calendar: Settings, the Connect card and the
 * reconnect chip (3a design §3.3), and the debug seed. Every read of a provider runs on [io] under [timeoutMillis].
 */
@Singleton
class CalendarSetup(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    private val people: suspend () -> List<Person>,
    private val toaster: Toaster,
    private val clock: WallClock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val timeoutMillis: Long = PROVIDER_TIMEOUT_MS,
    private val requestSync: () -> Unit = {},
) {
    @Inject
    constructor(
        store: CalendarStore,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        household: HouseholdRepository,
        toaster: Toaster,
        clock: WallClock,
        loop: CalendarSyncLoop,
    ) : this(store, providers, { household.people.first() }, toaster, clock, Dispatchers.IO, PROVIDER_TIMEOUT_MS, loop::requestSync)

    suspend fun hasConnections(): Boolean = store.connectionsNow().isNotEmpty()

    /** Every connection's id, as it changes. */
    fun connectionIds(): Flow<List<String>> = store.connectionIds()

    /** A connection with everything it holds (3a design D5: the debug sample once a real calendar is connected). */
    suspend fun removeConnection(connectionId: String) = store.removeConnection(connectionId)

    /** Sources missing from [mapping] show as Family. */
    suspend fun connect(connection: Connection, mapping: Map<String, SourceMapping>) {
        val provider = providerFor(connection.providerId)
        store.addConnection(connection, callProvider { provider.sources(connection) }, mapping)
    }

    /**
     * Connects with automatic setup (3a design D4): every source, mapped by [defaultMapping] from the household's
     * people, and the primary as the master; then a sync. Toasts the outcome. A failure stores nothing. Returns whether
     * the connection was stored.
     */
    suspend fun connectWithDefaults(connection: Connection): Boolean {
        val provider = providerFor(connection.providerId)
        val service = provider.descriptor.displayName
        val stored = try {
            val sources = callProvider { provider.sources(connection) }
            val household = people()
            val master = sources.firstOrNull { it.primary && it.writable }?.id
            store.addConnection(connection, sources, sources.associate { it.id to defaultMapping(it, household) }, master)
            true
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Connecting ${connection.providerId} timed out", e)
            false
        } catch (e: CancellationException) {
            // Rethrows if the caller was really cancelled; otherwise the provider leaked a stray cancellation.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Connecting ${connection.providerId} was cancelled inside the provider", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't connect ${connection.providerId}", e)
            false
        }
        if (stored) {
            requestSync()
            toaster.show(connected(service))
        } else {
            toaster.show(couldNotConnect(service))
        }
        return stored
    }

    /**
     * Reconnect (3a design D6): the same connection, healthy again, so its sign-in pause ends (D16); its queued
     * changes are due now (m4) and a sync is asked for. Returns whether it was recorded.
     */
    suspend fun reconnect(connection: Connection): Boolean {
        val service = providerFor(connection.providerId).descriptor.displayName
        return try {
            val now = clock.nowMillis()
            store.setHealth(connection.id, ConnectionHealth.Ok, now)
            store.makeDue(connection.id, now)
            requestSync()
            toaster.show(reconnected(service))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't record ${connection.id} as reconnected", e)
            toaster.show(couldNotConnect(service))
            false
        }
    }

    /** The household's master calendar; null until one is chosen. */
    suspend fun master(): StoredSource? = store.master().first()

    /**
     * Makes [sourceId] the master calendar, the only one the tablet writes to (spec §6). The provider must declare
     * WRITE and report the source as writable now. That is recorded too, because an install from before 2b-1
     * stored every source's writability as it was then.
     */
    suspend fun setMaster(connectionId: String, sourceId: String) {
        val connection = store.connectionsNow().firstOrNull { it.connection.id == connectionId }?.connection
            ?: throw IllegalArgumentException("No connection $connectionId")
        val provider = providerFor(connection.providerId)
        require(Feature.WRITE in provider.descriptor.features) { "${provider.descriptor.displayName} can't write" }
        val source = callProvider { provider.sources(connection) }.firstOrNull { it.id == sourceId }
            ?: throw IllegalArgumentException("No source $sourceId in ${connection.label}")
        require(source.writable) { "${source.name} is read-only" }
        store.setMaster(connectionId, sourceId)
        requestSync()
    }

    /** Asks the sync loop for a pass now. */
    fun syncSoon() = requestSync()

    private fun providerFor(providerId: String): CalendarProvider =
        providers.firstOrNull { it.descriptor.id == providerId } ?: throw IllegalArgumentException("No calendar provider $providerId")

    private suspend fun <T> callProvider(block: suspend () -> T): T = withContext(io) { withTimeout(timeoutMillis) { block() } }

    private companion object {
        const val TAG = "CalendarSetup"
    }
}
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add capability/calendar app
git commit -m "Follow the calendars ticked in the service, map new ones by name, and connect or reconnect with automatic setup"
```

---

### Task 8: `:provider:calendar-google`, the fake Google server, and `GoogleApi`

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`
- Create: `provider/calendar-google/build.gradle.kts`
- Create: `provider/calendar-google/src/main/AndroidManifest.xml`
- Create: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleJson.kt`
- Create: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/TokenSource.kt`
- Create: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleHttp.kt`
- Create: `provider/calendar-google/src/test/resources/robolectric.properties`
- Create: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/FakeGoogleServer.kt`
- Create: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/FakeTokenSource.kt`
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleApiTest.kt` (create)

**Interfaces:**
- Consumes: `NeedsSignInException`, `UnreachableException`, `WriteRejectedException` (the contract).
- Produces:
  - Catalog entries `libs.okhttp`, `libs.okhttp.mockwebserver`, `libs.kotlinx.serialization.json`, `libs.play.services.auth`, `libs.kotlinx.coroutines.play.services`, `libs.androidx.activity.compose` (exists), plugin `libs.plugins.kotlin.serialization`
  - `internal val GoogleJson: Json`; the `@Serializable` models `CalendarListPage`, `CalendarListEntry`, `EventsPage`, `GoogleEvent`, `GoogleTime`, `ExtendedProperties(privateProperties)`, `CalendarResource`, `ErrorBody`, `ErrorDetail`, `ErrorItem`
  - `interface TokenSource { suspend fun token(account: String): String; suspend fun invalidate(token: String) }`
  - `internal suspend fun Call.await(): Response`
  - `internal class GoogleResponse(code: Int, body: String)` with `isSuccessful`, `reason`, `message`; `internal fun <T> GoogleResponse.decode(deserializer: DeserializationStrategy<T>): T`; `internal fun GoogleResponse.readOrUnreachable(what: String): GoogleResponse`; `internal fun GoogleResponse.refusal(what: String): WriteRejectedException`; `internal const val REFUSED = "the change was refused"`, `internal const val READ_ONLY_HERE = "this calendar can't be changed from the tablet"`
  - `class GoogleApi(baseUrl: HttpUrl, tokens: TokenSource, client: OkHttpClient)` with `fun url(vararg segments: String, query: Map<String, String?> = emptyMap()): HttpUrl`, `internal suspend fun send(account: String, method: String, url: HttpUrl, body: JsonElement? = null): GoogleResponse`, `internal suspend fun sendWithToken(token: String, method: String, url: HttpUrl): GoogleResponse`; `const val GOOGLE_CALENDAR_BASE_URL = "https://www.googleapis.com/calendar/v3/"`
  - Test sources: `FakeGoogleServer` (calendars, events, sync tokens, pages, PATCH merge, failures, a write hold, recorded requests) and `FakeTokenSource`

- [ ] **Step 1: Pin the libraries and add the module**

In `gradle/libs.versions.toml`:
1. Under `[versions]`, after `roborazzi = "1.46.1"`, add:
```toml
okhttp = "4.12.0"
kotlinxSerialization = "1.9.0"
playServicesAuth = "21.4.0"
```
2. Under `[libraries]`, after the `roborazzi-compose` line, add:
```toml
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "playServicesAuth" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }
```
3. Under `[plugins]`, add:
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

In `build.gradle.kts` (root), after `alias(libs.plugins.roborazzi) apply false` add `alias(libs.plugins.kotlin.serialization) apply false`.

In `settings.gradle.kts`, after `include(":provider:calendar-fake")` add `include(":provider:calendar-google")`.

Create `provider/calendar-google/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":capability:calendar"))
    implementation(project(":core:plugin"))
    implementation(project(":core:ui"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":capability:calendar-testkit"))
    testImplementation(libs.okhttp.mockwebserver)
}
```
Task 9 adds Play services, Task 13 `activity-compose`, each in the task that first uses it.

Create `provider/calendar-google/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
</manifest>
```

Create `provider/calendar-google/src/test/resources/robolectric.properties`:
```properties
qualifiers=w1280dp-h800dp-land-hdpi
```

Run: `./gradlew :provider:calendar-google:dependencies --configuration debugRuntimeClasspath`
Expected: `BUILD SUCCESSFUL`, with `com.squareup.okhttp3:okhttp:4.12.0` and `kotlinx-serialization-json:1.9.0` resolved. If a version doesn't resolve, take the newest patch in its line (Global Constraints) and say so in the report.

- [ ] **Step 2: The test doubles**

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/FakeTokenSource.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

/** Hands out "token-1", "token-2"… and records what it was asked and told. */
internal class FakeTokenSource : TokenSource {
    private var issued = 0
    val asked = mutableListOf<String>()
    val invalidated = mutableListOf<String>()
    /** When set, token() throws it: Play services wanting the user, or unreachable. */
    var failWith: Exception? = null

    override suspend fun token(account: String): String {
        synchronized(this) { asked += account }
        failWith?.let { throw it }
        return synchronized(this) { "token-${++issued}" }
    }

    override suspend fun invalidate(token: String) {
        synchronized(this) { invalidated += token }
    }
}
```

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/FakeGoogleServer.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * Google Calendar API v3 on a MockWebServer (3a design §7): calendars and their events in memory, sync tokens, pages,
 * cancelled (deleted) events, extended properties and PATCH's merge. All-day dates are read in [zone], the calendar's
 * own zone. Tests seed it, queue failures, hold writes, and read what it was sent.
 */
internal class FakeGoogleServer(private val zone: ZoneId = ZoneId.of("Europe/London")) : Dispatcher() {
    private class Stored(var json: JsonObject, var version: Long)

    private class Failure(val matches: (RecordedRequest) -> Boolean, val response: MockResponse)

    private val server = MockWebServer()
    private val calendars = CopyOnWriteArrayList<JsonObject>()
    private val events = ConcurrentHashMap<String, LinkedHashMap<String, Stored>>()
    private val version = AtomicLong(0)
    private val failures = ConcurrentLinkedQueue<Failure>()
    private var primaryId: String? = null

    /** Every request, in order. */
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** The JSON body of every request that had one, in order (a RecordedRequest's body can be read only once). */
    val bodies = CopyOnWriteArrayList<JsonObject>()

    /** Items per page for both lists. */
    @Volatile var pageSize = 250

    /** Sync tokens older than this get 410 Gone, as when Google expires one. */
    @Volatile var oldestValidToken = 0L

    /** While set, every write waits for it before answering (the R9 check). */
    @Volatile var writeHold: CountDownLatch? = null

    /** Starts the server and returns the base URL a GoogleApi uses. */
    fun start(): HttpUrl {
        server.dispatcher = this
        server.start()
        return server.url("/calendar/v3/")
    }

    fun shutdown() {
        writeHold?.countDown()
        server.shutdown()
    }

    fun addCalendar(
        id: String,
        summary: String,
        accessRole: String = "owner",
        selected: Boolean = true,
        hidden: Boolean = false,
        primary: Boolean = false,
        summaryOverride: String? = null,
    ) {
        calendars += buildJsonObject {
            put("id", id)
            put("summary", summary)
            summaryOverride?.let { put("summaryOverride", it) }
            put("accessRole", accessRole)
            put("selected", selected)
            if (hidden) put("hidden", true)
            if (primary) put("primary", true)
        }
        events[id] = LinkedHashMap()
        if (primary) primaryId = id
    }

    fun removeCalendar(id: String) {
        calendars.removeIf { it.id == id }
        events.remove(id)
    }

    /** Adds or replaces [event] in [calendarId], as a phone would. */
    fun putEvent(calendarId: String, event: JsonObject) {
        val stored = events.getValue(calendarId)
        synchronized(stored) { stored[event.id] = Stored(event, version.incrementAndGet()) }
    }

    /** Deletes an event as a phone would: it stays, cancelled. */
    fun cancel(calendarId: String, eventId: String) {
        val stored = events.getValue(calendarId)
        synchronized(stored) {
            val e = stored.getValue(eventId)
            e.json = JsonObject(e.json + ("status" to JsonPrimitive("cancelled")))
            e.version = version.incrementAndGet()
        }
    }

    fun event(calendarId: String, eventId: String): JsonObject? {
        val stored = events[calendarId] ?: return null
        return synchronized(stored) { stored[eventId]?.json }
    }

    /** The next request [matches] gets [status] with Google's error body for [reason]. */
    fun failNext(status: Int, reason: String? = null, matches: (RecordedRequest) -> Boolean = { true }) {
        failures += Failure(matches, error(status, reason ?: "backendError"))
    }

    fun failNextWith(response: MockResponse, matches: (RecordedRequest) -> Boolean = { true }) {
        failures += Failure(matches, response)
    }

    fun timed(id: String, summary: String?, start: Instant, end: Instant, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject =
        buildJsonObject {
            put("id", id)
            put("status", "confirmed")
            summary?.let { put("summary", it) }
            putJsonObject("start") { put("dateTime", start.toString()) }
            putJsonObject("end") { put("dateTime", end.toString()) }
            extra()
        }

    fun allDay(id: String, summary: String?, start: LocalDate, endExclusive: LocalDate, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject =
        buildJsonObject {
            put("id", id)
            put("status", "confirmed")
            summary?.let { put("summary", it) }
            putJsonObject("start") { put("date", start.toString()) }
            putJsonObject("end") { put("date", endExclusive.toString()) }
            extra()
        }

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        val body = request.body.readUtf8().takeIf { it.isNotBlank() }?.let { GoogleJson.parseToJsonElement(it).jsonObject }
        body?.let { bodies += it }
        failures.firstOrNull { it.matches(request) }?.let {
            failures.remove(it)
            return it.response
        }
        val url = request.requestUrl ?: return error(400, "badRequest")
        // After "calendar/v3".
        val path = url.pathSegments.drop(2)
        val method = request.method
        return when {
            path == listOf("users", "me", "calendarList") && method == "GET" -> calendarList(url)
            path.size == 2 && path[0] == "calendars" && method == "GET" -> calendar(path[1])
            path.size == 3 && path[0] == "calendars" && path[2] == "events" && method == "GET" -> list(path[1], url)
            path.size == 3 && path[0] == "calendars" && path[2] == "events" && method == "POST" -> held { insert(path[1], body ?: JsonObject(emptyMap())) }
            path.size == 4 && path[0] == "calendars" && path[2] == "events" && method == "GET" -> get(path[1], path[3])
            path.size == 4 && path[0] == "calendars" && path[2] == "events" && method == "PATCH" -> held { patch(path[1], path[3], body ?: JsonObject(emptyMap())) }
            path.size == 4 && path[0] == "calendars" && path[2] == "events" && method == "DELETE" -> held { delete(path[1], path[3]) }
            else -> error(404, "notFound")
        }
    }

    private fun held(answer: () -> MockResponse): MockResponse {
        writeHold?.await()
        return answer()
    }

    private fun calendarList(url: HttpUrl): MockResponse {
        val from = url.queryParameter("pageToken")?.toInt() ?: 0
        val page = calendars.drop(from).take(pageSize)
        val next = from + pageSize
        return ok(
            buildJsonObject {
                put("items", JsonArray(page))
                if (next < calendars.size) put("nextPageToken", next.toString())
            },
        )
    }

    private fun calendar(id: String): MockResponse {
        val found = if (id == "primary") primaryId else calendars.firstOrNull { it.id == id }?.id
        return found?.let { ok(buildJsonObject { put("id", it) }) } ?: error(404, "notFound")
    }

    private fun list(calendarId: String, url: HttpUrl): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        val syncToken = url.queryParameter("syncToken")
        val timeMin = url.queryParameter("timeMin")
        val timeMax = url.queryParameter("timeMax")
        // Google refuses a sync token with a time range.
        if (syncToken != null && (timeMin != null || timeMax != null)) return error(400, "invalid")
        val since = syncToken?.let { t -> t.removePrefix("t").toLongOrNull()?.takeIf { it >= oldestValidToken } ?: return error(410, "fullSyncRequired") }
        val all = synchronized(stored) { stored.values.toList() }
            // singleEvents=true: a series' master isn't listed, only its instances.
            .filter { it.json["recurrence"] == null }
            .filter { s -> if (since != null) s.version > since else s.json.status != "cancelled" && overlaps(s.json, timeMin, timeMax) }
        val from = url.queryParameter("pageToken")?.toInt() ?: 0
        val next = from + pageSize
        return ok(
            buildJsonObject {
                put("items", JsonArray(all.drop(from).take(pageSize).map { it.json }))
                if (next < all.size) put("nextPageToken", next.toString()) else put("nextSyncToken", "t${version.get()}")
            },
        )
    }

    private fun get(calendarId: String, eventId: String): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        // A deleted event comes back with status "cancelled", as Google returns it.
        return synchronized(stored) { stored[eventId] }?.let { ok(it.json) } ?: error(404, "notFound")
    }

    private fun insert(calendarId: String, body: JsonObject): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        if (!writable(calendarId)) return error(403, "forbidden")
        val id = body["id"]?.jsonPrimitive?.content ?: "g${version.incrementAndGet()}"
        synchronized(stored) {
            if (id in stored) return error(409, "duplicate")
            val event = JsonObject(body + ("id" to JsonPrimitive(id)) + ("status" to JsonPrimitive("confirmed")))
            stored[id] = Stored(event, version.incrementAndGet())
            return ok(event)
        }
    }

    private fun patch(calendarId: String, eventId: String, body: JsonObject): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        if (!writable(calendarId)) return error(403, "forbidden")
        synchronized(stored) {
            val e = stored[eventId] ?: return error(404, "notFound")
            if (e.json.status == "cancelled") return error(410, "deleted")
            e.json = merge(e.json, body)
            e.version = version.incrementAndGet()
            return ok(e.json)
        }
    }

    private fun delete(calendarId: String, eventId: String): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        if (!writable(calendarId)) return error(403, "forbidden")
        synchronized(stored) {
            val e = stored[eventId] ?: return error(404, "notFound")
            if (e.json.status == "cancelled") return error(410, "deleted")
            e.json = JsonObject(e.json + ("status" to JsonPrimitive("cancelled")))
            e.version = version.incrementAndGet()
            return MockResponse().setResponseCode(204)
        }
    }

    private fun writable(calendarId: String) = calendars.firstOrNull { it.id == calendarId }?.string("accessRole") in setOf("owner", "writer")

    private fun overlaps(event: JsonObject, timeMin: String?, timeMax: String?): Boolean {
        val start = instantOf(event["start"]?.jsonObject) ?: return false
        val end = instantOf(event["end"]?.jsonObject) ?: return false
        val after = timeMin?.let { Instant.parse(it) }
        val before = timeMax?.let { Instant.parse(it) }
        return (before == null || start < before) && (after == null || end > after)
    }

    private fun instantOf(time: JsonObject?): Instant? {
        time ?: return null
        time.string("dateTime")?.let { return OffsetDateTime.parse(it).toInstant() }
        return time.string("date")?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant() }
    }

    private fun ok(json: JsonObject) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(json.toString())

    private fun error(status: Int, reason: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(
            buildJsonObject {
                putJsonObject("error") {
                    put("code", status)
                    put("message", "Google's own words for $reason")
                    put("errors", buildJsonArray { add(buildJsonObject { put("reason", reason) }) })
                }
            }.toString(),
        )
}

private val JsonObject.id: String get() = getValue("id").jsonPrimitive.content

private val JsonObject.status: String? get() = string("status")

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Google's PATCH: a key set to null goes, an object merges into the one it replaces, anything else replaces it. */
private fun merge(into: JsonObject, patch: JsonObject): JsonObject {
    val out = into.toMutableMap()
    for ((key, value) in patch) {
        val old = out[key]
        when {
            value is JsonNull -> out.remove(key)
            value is JsonObject && old is JsonObject -> out[key] = merge(old, value)
            else -> out[key] = value
        }
    }
    return JsonObject(out)
}
```
`add` inside `buildJsonArray` is `JsonArrayBuilder`'s own member, so it needs no import.

- [ ] **Step 3: Write the failing API tests**

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleApiTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException

private const val ACCOUNT = "family@example.com"

// Robolectric for android.util.Log; the server is a real MockWebServer on localhost.
@RunWith(AndroidJUnit4::class)
class GoogleApiTest {
    private val google = FakeGoogleServer()
    private val tokens = FakeTokenSource()
    private val client = OkHttpClient.Builder().readTimeout(Duration.ofSeconds(1)).build()
    private lateinit var api: GoogleApi

    @Before
    fun setUp() {
        api = GoogleApi(google.start(), tokens, client)
        google.addCalendar(ACCOUNT, "Family", primary = true)
    }

    @After
    fun tearDown() = google.shutdown()

    private val calendarList get() = api.url("users", "me", "calendarList")

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: Exception) {
            e
        }

    @Test
    fun everyRequestCarriesTheAccountsToken() = runTest {
        assertThat(api.send(ACCOUNT, "GET", calendarList).code).isEqualTo(200)
        assertThat(tokens.asked).containsExactly(ACCOUNT)
        assertThat(google.requests.single().getHeader("Authorization")).isEqualTo("Bearer token-1")
    }

    @Test
    fun a401ClearsTheTokenAndTriesOnceMoreWithAFreshOne() = runTest {
        google.failNext(401, "authError")
        assertThat(api.send(ACCOUNT, "GET", calendarList).code).isEqualTo(200)
        assertThat(tokens.invalidated).containsExactly("token-1")
        assertThat(google.requests.map { it.getHeader("Authorization") }).containsExactly("Bearer token-1", "Bearer token-2").inOrder()
    }

    @Test
    fun a401TwiceMeansTheAccountNeedsSigningIn() = runTest {
        google.failNext(401, "authError")
        google.failNext(401, "authError")
        assertThat(failureOf { api.send(ACCOUNT, "GET", calendarList) }).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun tryLaterAnswersAreUnreachable() = runTest {
        listOf(429 to "rateLimitExceeded", 403 to "rateLimitExceeded", 403 to "userRateLimitExceeded", 500 to "backendError", 503 to "backendError")
            .forEach { (status, reason) ->
                google.failNext(status, reason)
                assertWithMessage("$status $reason").that(failureOf { api.send(ACCOUNT, "GET", calendarList) })
                    .isInstanceOf(UnreachableException::class.java)
            }
    }

    @Test
    fun anotherForbiddenIsLeftToTheCallerWithGooglesReason() = runTest {
        google.failNext(403, "forbidden")
        val answer = api.send(ACCOUNT, "GET", calendarList)
        assertThat(answer.code to answer.reason).isEqualTo(403 to "forbidden")
    }

    @Test
    fun aDroppedConnectionIsUnreachable() = runTest {
        google.failNextWith(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertThat(failureOf { api.send(ACCOUNT, "GET", calendarList) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aSlowReplyIsUnreachable() = runTest {
        google.failNextWith(MockResponse().setBody("{}").setHeadersDelay(3, TimeUnit.SECONDS))
        assertThat(failureOf { api.send(ACCOUNT, "GET", calendarList) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aBodyThatDoesNotParseIsUnreachable() = runTest {
        google.failNextWith(MockResponse().setBody("<html>Service Unavailable</html>"))
        assertThat(failureOf { api.send(ACCOUNT, "GET", calendarList).decode(CalendarListPage.serializer()) })
            .isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun cancellingTheCallerCancelsTheHttpCall() = runTest {
        google.writeHold = CountDownLatch(1)
        val body = buildJsonObject { put("id", "held0123") }
        val returned = withContext(Dispatchers.Default) {
            val call = launch { api.send(ACCOUNT, "POST", api.url("calendars", ACCOUNT, "events"), body) }
            delay(200)
            call.cancel()
            withTimeoutOrNull(1_000) { call.join() } != null
        }
        assertThat(returned).isTrue()
        // Nothing left running in OkHttp: the call itself was cancelled, not just abandoned.
        withContext(Dispatchers.Default) { withTimeout(1_000) { while (client.dispatcher.runningCallsCount() > 0) delay(10) } }
    }
}
```

- [ ] **Step 4: Run them to see them fail**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: compilation FAILS: `GoogleApi`, `TokenSource`, `GoogleJson` and the models are unresolved.

- [ ] **Step 5: The JSON models**

Create `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleJson.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The one JSON setup (3a design §3.1): unknown keys ignored; a null field is left out unless a PATCH sends it. */
internal val GoogleJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
internal data class CalendarListPage(val items: List<CalendarListEntry> = emptyList(), val nextPageToken: String? = null)

@Serializable
internal data class CalendarListEntry(
    val id: String,
    val summary: String? = null,
    val summaryOverride: String? = null,
    val accessRole: String? = null,
    val selected: Boolean? = null,
    val hidden: Boolean? = null,
    val primary: Boolean? = null,
)

@Serializable
internal data class EventsPage(
    val items: List<GoogleEvent> = emptyList(),
    val nextPageToken: String? = null,
    val nextSyncToken: String? = null,
)

@Serializable
internal data class GoogleEvent(
    val id: String,
    val status: String? = null,
    val summary: String? = null,
    val start: GoogleTime? = null,
    val end: GoogleTime? = null,
    val recurringEventId: String? = null,
    val recurrence: List<String>? = null,
    val eventType: String? = null,
    val extendedProperties: ExtendedProperties? = null,
    val colorId: String? = null,
)

@Serializable
internal data class GoogleTime(val dateTime: String? = null, val date: String? = null, val timeZone: String? = null)

@Serializable
internal data class ExtendedProperties(@SerialName("private") val privateProperties: Map<String, String>? = null)

@Serializable
internal data class CalendarResource(val id: String)

@Serializable
internal data class ErrorBody(val error: ErrorDetail? = null)

@Serializable
internal data class ErrorDetail(val code: Int? = null, val message: String? = null, val errors: List<ErrorItem> = emptyList())

@Serializable
internal data class ErrorItem(val reason: String? = null, val message: String? = null)
```

- [ ] **Step 6: The token source and the HTTP layer**

Create `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/TokenSource.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

/** Short-lived access tokens for a Google account (3a design D2). Culvery stores none. */
interface TokenSource {
    /** A token for [account]: throws NeedsSignInException when the user must act, UnreachableException when offline. */
    suspend fun token(account: String): String

    /** Google refused [token]: clear it from the cache so the next [token] is fresh. */
    suspend fun invalidate(token: String)
}
```

Create `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleHttp.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import android.util.Log
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException

const val GOOGLE_CALENDAR_BASE_URL = "https://www.googleapis.com/calendar/v3/"

/** Follow-up R8's fixed wording: Google's own text is logged, never shown. */
internal const val REFUSED = "the change was refused"
internal const val READ_ONLY_HERE = "this calendar can't be changed from the tablet"

private const val TAG = "GoogleCalendar"
private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
private val RATE_LIMITS = setOf("rateLimitExceeded", "userRateLimitExceeded")

/** Enqueues the call and suspends until it answers; cancelling the coroutine cancels the call (follow-up R9). */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, value, _ -> value.close() }
            }
        },
    )
}

/** What Google answered: the status, the body, and Google's first error reason and message (for the log). */
internal class GoogleResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
    val reason: String? get() = error()?.errors?.firstOrNull()?.reason
    val message: String? get() = error()?.message

    private fun error(): ErrorDetail? = runCatching { GoogleJson.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
}

/** A successful answer as [deserializer] reads it; a body that doesn't parse means try later (3a design §3.7). */
internal fun <T> GoogleResponse.decode(deserializer: DeserializationStrategy<T>): T =
    try {
        GoogleJson.decodeFromString(deserializer, body)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Google Calendar sent a body the tablet can't read", e)
        throw UnreachableException("Google Calendar sent an answer the tablet can't read", e)
    }

/** A read's answer: success, or "try later" for any status the caller didn't handle, logged with Google's words. */
internal fun GoogleResponse.readOrUnreachable(what: String): GoogleResponse {
    if (isSuccessful) return this
    Log.w(TAG, "$what: Google Calendar answered $code ($reason): $message")
    throw UnreachableException("Google Calendar answered $code")
}

/** A write's refusal in the tablet's own words (3a design §3.7, R8); Google's are logged. */
internal fun GoogleResponse.refusal(what: String): WriteRejectedException {
    Log.w(TAG, "$what: Google Calendar refused it with $code ($reason): $message")
    return WriteRejectedException(if (code == 403) READ_ONLY_HERE else REFUSED)
}

/**
 * Google Calendar API v3 over OkHttp (3a design §3.1, §3.7). Each request asks [tokens] for the account's token; a
 * 401 clears it and tries once more with a fresh one, and a second 401 means the account needs signing in again.
 * The answers every call treats alike are "try later" (UnreachableException): 429, a rate-limit 403, 5xx, and a
 * connection that fails or times out. Everything else is returned for the caller to map.
 */
class GoogleApi(private val baseUrl: HttpUrl, private val tokens: TokenSource, private val client: OkHttpClient) {
    /** [segments] below the base URL, each encoded (calendar ids hold '@' and '#'), with the non-null [query] values. */
    fun url(vararg segments: String, query: Map<String, String?> = emptyMap()): HttpUrl {
        val builder = baseUrl.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) -> if (value != null) builder.addQueryParameter(key, value) }
        return builder.build()
    }

    internal suspend fun send(account: String, method: String, url: HttpUrl, body: JsonElement? = null): GoogleResponse {
        val first = tokens.token(account)
        val answer = execute(first, method, url, body)
        if (answer.code != 401) return answer
        tokens.invalidate(first)
        val second = execute(tokens.token(account), method, url, body)
        if (second.code == 401) throw NeedsSignInException("Google refused the account's token twice")
        return second
    }

    /** With a token already in hand: the connect flow's, before the account is known. */
    internal suspend fun sendWithToken(token: String, method: String, url: HttpUrl): GoogleResponse {
        val answer = execute(token, method, url, null)
        if (answer.code == 401) throw NeedsSignInException("Google refused the new token")
        return answer
    }

    private suspend fun execute(token: String, method: String, url: HttpUrl, body: JsonElement?): GoogleResponse {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(method, body?.toString()?.toRequestBody(JSON_TYPE))
            .build()
        val answer = try {
            client.newCall(request).await().use { response ->
                // Interruptible, so a slow body can't outlast a cancelled caller either.
                GoogleResponse(response.code, runInterruptible { response.body?.string().orEmpty() })
            }
        } catch (e: IOException) {
            throw UnreachableException("Couldn't reach Google Calendar", e)
        }
        if (answer.code == 429 || answer.code >= 500 || (answer.code == 403 && answer.reason in RATE_LIMITS)) {
            Log.w(TAG, "$method ${url.encodedPath}: Google Calendar said try later: ${answer.code} (${answer.reason}): ${answer.message}")
            throw UnreachableException("Google Calendar asked to try later (${answer.code})")
        }
        return answer
    }
}
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: PASS. `aSlowReplyIsUnreachable` takes about a second (the client's read timeout).
- If the build warns that `cont.resume(value, onCancellation)` or any OkHttp or MockWebServer member is deprecated, stop and ask (Global Constraints).
- The log lines name the method and path, never the token.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. `ModuleBoundaries` accepts the new module (`:provider:calendar-…` → `:capability:calendar`, `:core:*`, and the testkit in tests).

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts settings.gradle.kts provider/calendar-google
git commit -m "Add the Google Calendar provider module with cancellable HTTP, its error mapping and a fake Google server"
```

---

### Task 9: Sign-in — `TokenSource` over Play services, the `Authorizer` seam, and the connect flow's logic

**Files:**
- Modify: `provider/calendar-google/build.gradle.kts`
- Modify: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/TokenSource.kt`
- Create: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleConnectFlow.kt`
- Create: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/FakeAuthorizer.kt`
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/TokenSourceTest.kt` (create)
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleConnectFlowTest.kt` (create)

**Interfaces:**
- Consumes: `GoogleApi.sendWithToken`, `readOrUnreachable`, `decode`, `CalendarResource`, `FakeGoogleServer` (Task 8); `CONFIG_ACCOUNT` (Task 1); `couldNotConnect` (Task 7).
- Produces:
  - `sealed interface Authorization { data class Granted(val token: String); class NeedsUser(val intent: PendingIntent) }`
  - `interface Authorizer { suspend fun authorize(account: String?): Authorization; fun authorizationFrom(data: Intent?): Authorization; suspend fun clearToken(token: String) }`
  - `@Singleton class PlayServicesAuthorizer @Inject constructor(@ApplicationContext context: Context) : Authorizer`
  - `class PlayServicesTokenSource @Inject constructor(authorizer: Authorizer) : TokenSource`
  - `internal fun authorizationFailure(statusCode: Int, cause: Throwable? = null): Exception`
  - `const val GOOGLE_PROVIDER_ID = "calendar.google"`, `internal const val GOOGLE_DISPLAY_NAME = "Google Calendar"`, `internal const val GOOGLE_LABEL = "Google"`
  - `sealed interface ConnectStep { data class Done(connection); class ShowScreens(intent: PendingIntent); data object Stopped }`
  - `internal class GoogleConnectFlow(authorizer, api, toaster)` with `suspend fun start(existing: Connection?): ConnectStep` and `suspend fun afterScreens(existing: Connection?, data: Intent?): ConnectStep`
  - `fun differentAccount(email: String): String` — "That's a different Google account. Reconnect with {email}."
  - Test sources: `FakeAuthorizer`

- [ ] **Step 1: Add Play services**

In `provider/calendar-google/build.gradle.kts`, after `implementation(libs.kotlinx.serialization.json)` add:
```kotlin
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
```
Run: `./gradlew :provider:calendar-google:dependencies --configuration debugRuntimeClasspath`
Expected: `com.google.android.gms:play-services-auth:21.4.0` and `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2` resolve.

- [ ] **Step 2: Write the failing tests**

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/FakeAuthorizer.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import android.content.Intent

/** Play services' answers, scripted: no Play services in tests. */
internal class FakeAuthorizer : Authorizer {
    /** What the next authorize answers, unless [failWith] is set. */
    var next: Authorization = Authorization.Granted("token-granted")
    var failWith: Exception? = null
    /** What the account chooser and consent screens answer. */
    var fromScreens: Authorization = Authorization.Granted("token-after-screens")
    val accounts = mutableListOf<String?>()
    val cleared = mutableListOf<String>()

    override suspend fun authorize(account: String?): Authorization {
        accounts += account
        failWith?.let { throw it }
        return next
    }

    override fun authorizationFrom(data: Intent?): Authorization = fromScreens

    override suspend fun clearToken(token: String) {
        cleared += token
    }
}
```

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/TokenSourceTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException

// Robolectric for PendingIntent.
@RunWith(AndroidJUnit4::class)
class TokenSourceTest {
    private val authorizer = FakeAuthorizer()
    private val tokens = PlayServicesTokenSource(authorizer)

    private fun screens(): PendingIntent =
        PendingIntent.getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: Exception) {
            e
        }

    @Test
    fun aGrantIsTheTokenForThatAccount() = runTest {
        authorizer.next = Authorization.Granted("t1")
        assertThat(tokens.token("family@example.com")).isEqualTo("t1")
        assertThat(authorizer.accounts).containsExactly("family@example.com")
    }

    @Test
    fun screensTheUserMustSeeMeanTheConnectionNeedsSignIn() = runTest {
        authorizer.next = Authorization.NeedsUser(screens())
        assertThat(failureOf { tokens.token("family@example.com") }).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun anUnreachablePlayServicesIsUnreachable() = runTest {
        authorizer.failWith = UnreachableException("offline")
        assertThat(failureOf { tokens.token("family@example.com") }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aNetworkStatusIsTryLaterAndAnyOtherNeedsTheUser() {
        assertThat(authorizationFailure(CommonStatusCodes.NETWORK_ERROR)).isInstanceOf(UnreachableException::class.java)
        assertThat(authorizationFailure(CommonStatusCodes.TIMEOUT)).isInstanceOf(UnreachableException::class.java)
        assertThat(authorizationFailure(CommonStatusCodes.SIGN_IN_REQUIRED)).isInstanceOf(NeedsSignInException::class.java)
        assertThat(authorizationFailure(CommonStatusCodes.CANCELED)).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun invalidatingClearsTheTokenInPlayServices() = runTest {
        tokens.invalidate("t1")
        assertThat(authorizer.cleared).containsExactly("t1")
    }
}
```

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleConnectFlowTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster

@RunWith(AndroidJUnit4::class)
class GoogleConnectFlowTest {
    private class Toasts : Toaster {
        val messages = mutableListOf<String>()

        override fun show(message: String, icon: String) {
            messages += message
        }
    }

    private val google = FakeGoogleServer()
    private val authorizer = FakeAuthorizer()
    private val toasts = Toasts()
    private lateinit var flow: GoogleConnectFlow
    private val stored = Connection("g1", GOOGLE_PROVIDER_ID, GOOGLE_LABEL, mapOf(CONFIG_ACCOUNT to "family@example.com"))

    @Before
    fun setUp() {
        flow = GoogleConnectFlow(authorizer, GoogleApi(google.start(), FakeTokenSource(), OkHttpClient()), toasts)
    }

    @After
    fun tearDown() = google.shutdown()

    private fun screens(): PendingIntent =
        PendingIntent.getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)

    @Test
    fun aGrantBecomesANewConnectionForTheAccountItsPrimaryCalendarNames() = runTest {
        google.addCalendar("family@example.com", "Family", primary = true)
        val step = flow.start(existing = null) as ConnectStep.Done
        assertThat(authorizer.accounts).containsExactly(null)
        assertThat(step.connection.providerId).isEqualTo(GOOGLE_PROVIDER_ID)
        assertThat(step.connection.label).isEqualTo(GOOGLE_LABEL)
        assertThat(step.connection.config).containsExactly(CONFIG_ACCOUNT, "family@example.com")
        assertThat(step.connection.id).isNotEmpty()
        assertThat(google.requests.single().getHeader("Authorization")).isEqualTo("Bearer token-granted")
    }

    @Test
    fun screensTheUserMustSeeComeFirstThenTheConnectionIsMade() = runTest {
        google.addCalendar("family@example.com", "Family", primary = true)
        authorizer.next = Authorization.NeedsUser(screens())
        assertThat(flow.start(existing = null)).isInstanceOf(ConnectStep.ShowScreens::class.java)
        val done = flow.afterScreens(existing = null, data = Intent()) as ConnectStep.Done
        assertThat(done.connection.config[CONFIG_ACCOUNT]).isEqualTo("family@example.com")
    }

    @Test
    fun aReconnectAsksForTheStoredAccountAndKeepsTheConnectionsId() = runTest {
        google.addCalendar("family@example.com", "Family", primary = true)
        val done = flow.start(existing = stored) as ConnectStep.Done
        assertThat(authorizer.accounts).containsExactly("family@example.com")
        assertThat(done.connection.id).isEqualTo("g1")
    }

    @Test
    fun aReconnectWithADifferentAccountIsRefused() = runTest {
        google.addCalendar("someone.else@example.com", "Theirs", primary = true)
        assertThat(flow.start(existing = stored)).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).containsExactly("That's a different Google account. Reconnect with family@example.com.")
    }

    @Test
    fun backingOutOfTheChooserStopsQuietly() = runTest {
        authorizer.failWith = NeedsSignInException("the user cancelled")
        assertThat(flow.start(existing = null)).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).isEmpty()
    }

    @Test
    fun noNetworkStopsAndSaysSo() = runTest {
        authorizer.failWith = UnreachableException("offline")
        assertThat(flow.start(existing = null)).isEqualTo(ConnectStep.Stopped)
        google.addCalendar("family@example.com", "Family", primary = true)
        authorizer.failWith = null
        google.failNext(503)
        assertThat(flow.start(existing = null)).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).containsExactly(
            "Couldn't connect to Google Calendar — try again",
            "Couldn't connect to Google Calendar — try again",
        )
    }
}
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: compilation FAILS: `Authorization`, `Authorizer`, `PlayServicesTokenSource`, `authorizationFailure` and `GoogleConnectFlow` are unresolved.

- [ ] **Step 4: Play services behind the seam**

Replace `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/TokenSource.kt` with:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException

/** Short-lived access tokens for a Google account (3a design D2). Culvery stores none. */
interface TokenSource {
    /** A token for [account]: throws NeedsSignInException when the user must act, UnreachableException when offline. */
    suspend fun token(account: String): String

    /** Google refused [token]: clear it from the cache so the next [token] is fresh. */
    suspend fun invalidate(token: String)
}

/** The calendar scopes Culvery asks for (3a design D2): every calendar read, events written. */
internal val CALENDAR_SCOPES = listOf(
    "https://www.googleapis.com/auth/calendar.readonly",
    "https://www.googleapis.com/auth/calendar.events",
)

private const val GOOGLE_ACCOUNT_TYPE = "com.google"
private val NETWORK_STATUSES = setOf(CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT)

/** Play services' answer: a token, or screens (the account chooser, Google's consent) the user must see first. */
sealed interface Authorization {
    data class Granted(val token: String) : Authorization

    class NeedsUser(val intent: PendingIntent) : Authorization
}

/** Play services' AuthorizationClient behind a seam, so tests need no Play services (3a design §3.2). */
interface Authorizer {
    /** Asks for the calendar scopes; for [account] when known, so the chooser is skipped and the token is its. */
    suspend fun authorize(account: String?): Authorization

    /** The answer the screens returned in [data]. */
    fun authorizationFrom(data: Intent?): Authorization

    suspend fun clearToken(token: String)
}

/** A Play services failure (3a design §3.2): a network status means try later; any other needs the user. */
internal fun authorizationFailure(statusCode: Int, cause: Throwable? = null): Exception =
    if (statusCode in NETWORK_STATUSES) {
        UnreachableException("Play services couldn't reach Google", cause)
    } else {
        NeedsSignInException("Play services didn't grant the calendar scopes ($statusCode)", cause)
    }

@Singleton
class PlayServicesAuthorizer @Inject constructor(@ApplicationContext private val context: Context) : Authorizer {
    private val client get() = Identity.getAuthorizationClient(context)

    override suspend fun authorize(account: String?): Authorization {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(CALENDAR_SCOPES.map(::Scope))
            .apply { if (account != null) setAccount(Account(account, GOOGLE_ACCOUNT_TYPE)) }
            .build()
        val result = try {
            client.authorize(request).await()
        } catch (e: ApiException) {
            throw authorizationFailure(e.statusCode, e)
        }
        return result.toAuthorization()
    }

    override fun authorizationFrom(data: Intent?): Authorization =
        try {
            client.getAuthorizationResultFromIntent(data).toAuthorization()
        } catch (e: ApiException) {
            throw authorizationFailure(e.statusCode, e)
        }

    override suspend fun clearToken(token: String) {
        try {
            client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
        } catch (e: ApiException) {
            throw authorizationFailure(e.statusCode, e)
        }
    }

    private fun AuthorizationResult.toAuthorization(): Authorization {
        val screens = pendingIntent
        if (hasResolution() && screens != null) return Authorization.NeedsUser(screens)
        return Authorization.Granted(accessToken ?: throw NeedsSignInException("Play services granted no token"))
    }
}

/**
 * Asks Play services for a token on every call (3a design §3.2): it caches and refreshes them itself. Screens the user
 * must see mean the connection needs signing in again.
 */
class PlayServicesTokenSource @Inject constructor(private val authorizer: Authorizer) : TokenSource {
    override suspend fun token(account: String): String = when (val answer = authorizer.authorize(account)) {
        is Authorization.Granted -> answer.token
        is Authorization.NeedsUser -> throw NeedsSignInException("Google needs the user to sign in again")
    }

    override suspend fun invalidate(token: String) = authorizer.clearToken(token)
}
```

- [ ] **Step 5: The connect flow**

Create `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleConnectFlow.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CancellationException
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.couldNotConnect
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster

/** Stored with every Google connection: never change it. */
const val GOOGLE_PROVIDER_ID = "calendar.google"

internal const val GOOGLE_DISPLAY_NAME = "Google Calendar"

/** The short connection label: the syncing pill and the reconnect chip (3a design D11). */
internal const val GOOGLE_LABEL = "Google"

private const val TAG = "GoogleConnect"

/** 3a design §3.2: a reconnect must be the same account. */
fun differentAccount(email: String): String = "That's a different Google account. Reconnect with $email."

/** One step of connecting: done, screens the user must see, or stopped (after saying why, if there is a why). */
sealed interface ConnectStep {
    data class Done(val connection: Connection) : ConnectStep

    class ShowScreens(val intent: PendingIntent) : ConnectStep

    data object Stopped : ConnectStep
}

/**
 * The connect and reconnect flow's logic, apart from the screens it launches (3a design §3.2): ask Play services for
 * the calendar scopes (for the stored account on a reconnect); once granted, ask Google whose primary calendar this
 * is, which is the account's email. A reconnect to a different account is refused. Backing out says nothing.
 */
internal class GoogleConnectFlow(private val authorizer: Authorizer, private val api: GoogleApi, private val toaster: Toaster) {
    suspend fun start(existing: Connection?): ConnectStep = step(existing) { authorizer.authorize(existing?.config?.get(CONFIG_ACCOUNT)) }

    suspend fun afterScreens(existing: Connection?, data: Intent?): ConnectStep = step(existing) { authorizer.authorizationFrom(data) }

    private suspend fun step(existing: Connection?, ask: suspend () -> Authorization): ConnectStep =
        try {
            when (val answer = ask()) {
                is Authorization.NeedsUser -> ConnectStep.ShowScreens(answer.intent)
                is Authorization.Granted -> finish(existing, answer.token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NeedsSignInException) {
            Log.i(TAG, "Google sign-in stopped", e)
            ConnectStep.Stopped
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't connect to Google", e)
            toaster.show(couldNotConnect(GOOGLE_DISPLAY_NAME))
            ConnectStep.Stopped
        }

    private suspend fun finish(existing: Connection?, token: String): ConnectStep {
        val email = api.sendWithToken(token, "GET", api.url("calendars", "primary"))
            .readOrUnreachable("Finding the account's primary calendar")
            .decode(CalendarResource.serializer())
            .id
        val stored = existing?.config?.get(CONFIG_ACCOUNT)
        if (stored != null && !email.equals(stored, ignoreCase = true)) {
            toaster.show(differentAccount(stored))
            return ConnectStep.Stopped
        }
        val id = existing?.id ?: UUID.randomUUID().toString()
        return ConnectStep.Done(Connection(id, GOOGLE_PROVIDER_ID, GOOGLE_LABEL, mapOf(CONFIG_ACCOUNT to email)))
    }
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: PASS. If the build warns that any `AuthorizationClient`, `AuthorizationRequest.Builder` or `ClearTokenRequest` member is deprecated, stop and ask (Global Constraints). Nothing here may touch `GoogleSignIn` or `GoogleSignInAccount`.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add provider/calendar-google
git commit -m "Get Google tokens from Play services behind a seam, and work out which account a connect or reconnect signed in"
```

---

### Task 10: `GoogleCalendarProvider` reading — calendars, full and incremental syncs, 410, paging, recurrence rules, and its connect screen

**Files:**
- Modify: `provider/calendar-google/build.gradle.kts`
- Create: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleEvents.kt`
- Create: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProvider.kt`
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleReadTest.kt` (create)
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleConnectScreenTest.kt` (create)
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProviderContractTest.kt` (create)

**Interfaces:**
- Consumes: `GoogleApi`, `GoogleResponse` helpers, the JSON models, `FakeGoogleServer`, `FakeTokenSource` (Task 8); `Authorizer`, `GoogleConnectFlow`, `GOOGLE_PROVIDER_ID`, `GOOGLE_DISPLAY_NAME`, `FakeAuthorizer` (Task 9); `SourceGoneException`, `CONFIG_ACCOUNT` (Task 1).
- Produces:
  - `internal const val PERSON_KEY = "culvery.person"`, `internal const val CREATED_BY_KEY = "culvery.createdBy"`, `internal const val NO_TITLE = "(No title)"`
  - `internal val GoogleEvent.isGone: Boolean` (cancelled, or a working location); `internal fun GoogleEvent.toRemoteEvent(rule: String?): RemoteEvent?`; `internal fun GoogleTime.toEventTime(): EventTime?`
  - `@Singleton class GoogleCalendarProvider @Inject constructor(api: GoogleApi, authorizer: Authorizer, toaster: Toaster) : CalendarProvider` — descriptor id `calendar.google`, "Google Calendar", `calendar_month`, `READ` (Task 11 adds `WRITE` and `CalendarWriter`)

- [ ] **Step 1: The connect screen needs activity-compose**

In `provider/calendar-google/build.gradle.kts`, after `implementation(libs.kotlinx.coroutines.play.services)` add:
```kotlin
    implementation(libs.androidx.activity.compose)
```

- [ ] **Step 2: Write the failing tests**

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleReadTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.SourceGoneException
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster

@RunWith(AndroidJUnit4::class)
class GoogleReadTest {
    private object NoToasts : Toaster {
        override fun show(message: String, icon: String) = Unit
    }

    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val range = DateRange(today.minusDays(1), today.plusDays(15), london)
    private val google = FakeGoogleServer(london)
    private lateinit var provider: GoogleCalendarProvider
    private val conn = Connection("g1", GOOGLE_PROVIDER_ID, "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val family = CalendarSource("family@example.com", "Family", writable = true, primary = true)

    @Before
    fun setUp() {
        provider = GoogleCalendarProvider(GoogleApi(google.start(), FakeTokenSource(), OkHttpClient()), FakeAuthorizer(), NoToasts)
        google.addCalendar(family.id, "Family", primary = true)
    }

    @After
    fun tearDown() = google.shutdown()

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant = LocalDate.of(2026, 9, day).atTime(hour, minute).atZone(london).toInstant()

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: Exception) {
            e
        }

    @Test
    fun calendarsAreEveryPageWithTheirNamesRightsTicksAndThePrimary() = runTest {
        google.pageSize = 1
        google.addCalendar("mia@group", "Mia", accessRole = "writer", summaryOverride = "Mia's swimming")
        google.addCalendar("holidays@group", "UK holidays", accessRole = "reader", selected = false)
        google.addCalendar("busy@group", "Busy", accessRole = "freeBusyReader", hidden = true)
        assertThat(provider.sources(conn)).containsExactly(
            CalendarSource(family.id, "Family", writable = true, shown = true, primary = true),
            CalendarSource("mia@group", "Mia's swimming", writable = true, shown = true, primary = false),
            CalendarSource("holidays@group", "UK holidays", writable = false, shown = false, primary = false),
            CalendarSource("busy@group", "Busy", writable = false, shown = false, primary = false),
        ).inOrder()
    }

    @Test
    fun aFullSyncAsksForTheWindowInTheHouseholdZoneFollowsPagesAndReturnsTheSyncToken() = runTest {
        google.pageSize = 2
        google.putEvent(family.id, google.timed("a", "Walk", at(23, 9), at(23, 10)))
        google.putEvent(family.id, google.timed("b", "Swim", at(23, 16), at(23, 17)))
        google.putEvent(family.id, google.allDay("c", "Bin day", today, today.plusDays(1)))
        val result = provider.sync(conn, family, range, null)
        assertThat(result.upserts.map { it.remoteId }).containsExactly("a", "b", "c")
        assertThat(result.fullReplace).isTrue()
        assertThat(result.cursor).isNotNull()
        val first = google.requests.first().requestUrl!!
        assertThat(first.queryParameter("timeMin")).isEqualTo("2026-09-21T23:00:00Z")
        assertThat(first.queryParameter("timeMax")).isEqualTo("2026-10-08T23:00:00Z")
        assertThat(first.queryParameter("singleEvents")).isEqualTo("true")
        assertThat(google.requests).hasSize(2)
    }

    @Test
    fun anIncrementalSyncSendsOnlyItsTokenAndReturnsTheChanges() = runTest {
        google.putEvent(family.id, google.timed("a", "Walk", at(23, 9), at(23, 10)))
        val first = provider.sync(conn, family, range, null)
        google.putEvent(family.id, google.timed("a", "Long walk", at(23, 9), at(23, 11)))
        google.putEvent(family.id, google.timed("b", "Swim", at(23, 16), at(23, 17)))
        google.cancel(family.id, "b")
        val next = provider.sync(conn, family, range, first.cursor)
        val url = google.requests.last().requestUrl!!
        assertThat(url.queryParameter("syncToken")).isEqualTo(first.cursor!!.value)
        assertThat(url.queryParameter("timeMin")).isNull()
        assertThat(next.fullReplace).isFalse()
        assertThat(next.upserts.map { it.title }).containsExactly("Long walk")
        assertThat(next.removedIds).containsExactly("b")
    }

    @Test
    fun anExpiredSyncTokenGivesAFullReplace() = runTest {
        google.putEvent(family.id, google.timed("a", "Walk", at(23, 9), at(23, 10)))
        val first = provider.sync(conn, family, range, null)
        google.oldestValidToken = Long.MAX_VALUE
        val next = provider.sync(conn, family, range, first.cursor)
        assertThat(next.fullReplace).isTrue()
        assertThat(next.upserts.map { it.remoteId }).containsExactly("a")
    }

    @Test
    fun anEventReadsItsTimesTagsAndTitle() = runTest {
        google.putEvent(
            family.id,
            google.timed("a", null, at(23, 19, 30), at(23, 21)) {
                putJsonObject("extendedProperties") { putJsonObject("private") { put(PERSON_KEY, "mia-id"); put(CREATED_BY_KEY, "sam-id") } }
            },
        )
        val event = provider.sync(conn, family, range, null).upserts.single()
        assertThat(event.title).isEqualTo("(No title)")
        assertThat(event.start).isEqualTo(EventTime.Timed(at(23, 19, 30)))
        assertThat(event.forPerson to event.createdBy).isEqualTo("mia-id" to "sam-id")
        assertThat(event.recurring).isFalse()
    }

    @Test
    fun googlesOffsetsAndDatesAreRead() {
        assertThat(GoogleTime(dateTime = "2026-09-23T19:30:00+01:00").toEventTime()).isEqualTo(EventTime.Timed(at(23, 19, 30)))
        assertThat(GoogleTime(date = "2026-09-23").toEventTime()).isEqualTo(EventTime.AllDay(today))
        assertThat(GoogleTime().toEventTime()).isNull()
    }

    @Test
    fun aWorkingLocationIsSkippedInAFullSyncAndRemovedInAnIncrementalOne() = runTest {
        google.putEvent(family.id, google.allDay("office", "Office", today, today.plusDays(1)) { put("eventType", "workingLocation") })
        val first = provider.sync(conn, family, range, null)
        assertThat(first.upserts).isEmpty()
        google.putEvent(family.id, google.allDay("office", "Office", today, today.plusDays(1)) { put("eventType", "workingLocation") })
        assertThat(provider.sync(conn, family, range, first.cursor).removedIds).containsExactly("office")
    }

    @Test
    fun instancesOfASeriesAreRecurringWithTheSeriesRuleFetchedOnce() = runTest {
        google.putEvent(family.id, google.timed("piano", "Piano", at(22, 15), at(22, 16)) { putJsonArray("recurrence") { add("RRULE:FREQ=WEEKLY;BYDAY=TU") } })
        google.putEvent(family.id, google.timed("piano_1", "Piano", at(22, 15), at(22, 16)) { put("recurringEventId", "piano") })
        google.putEvent(family.id, google.timed("piano_2", "Piano", at(29, 15), at(29, 16)) { put("recurringEventId", "piano") })
        val upserts = provider.sync(conn, family, range, null).upserts
        assertThat(upserts.map { it.recurring to it.recurrenceRule })
            .containsExactly(true to "RRULE:FREQ=WEEKLY;BYDAY=TU", true to "RRULE:FREQ=WEEKLY;BYDAY=TU")
        assertThat(google.requests.count { it.requestUrl!!.pathSegments.last() == "piano" }).isEqualTo(1)
        // A full sync starts the cache again.
        provider.sync(conn, family, range, null)
        assertThat(google.requests.count { it.requestUrl!!.pathSegments.last() == "piano" }).isEqualTo(2)
    }

    @Test
    fun aSeriesWhoseRuleCantBeReadStillSyncsWithNoRule() = runTest {
        google.putEvent(family.id, google.timed("piano_1", "Piano", at(22, 15), at(22, 16)) { put("recurringEventId", "piano") })
        google.failNext(500) { it.requestUrl!!.pathSegments.last() == "piano" }
        val event = provider.sync(conn, family, range, null).upserts.single()
        assertThat(event.recurring to event.recurrenceRule).isEqualTo(true to null)
    }

    @Test
    fun aDeletedOrForbiddenCalendarIsSourceGone() = runTest {
        google.failNext(404, "notFound")
        assertThat(failureOf { provider.sync(conn, family, range, null) }).isInstanceOf(SourceGoneException::class.java)
        google.failNext(403, "forbidden")
        assertThat(failureOf { provider.sync(conn, family, range, null) }).isInstanceOf(SourceGoneException::class.java)
    }

    @Test
    fun aListOnADeletedCalendarIsSourceGone() = runTest {
        google.addCalendar("gone@group", "Gone")
        google.removeCalendar("gone@group")
        val gone = CalendarSource("gone@group", "Gone", writable = false)
        assertThat(failureOf { provider.sync(conn, gone, range, null) }).isInstanceOf(SourceGoneException::class.java)
    }

    @Test
    fun anyOtherRefusalOrAnUnreadableBodyIsUnreachable() = runTest {
        google.failNext(400, "invalid")
        assertThat(failureOf { provider.sync(conn, family, range, null) }).isInstanceOf(UnreachableException::class.java)
        google.failNextWith(okhttp3.mockwebserver.MockResponse().setBody("not json"))
        assertThat(failureOf { provider.sync(conn, family, range, SyncCursor("t0")) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aConnectionWithNoAccountNeedsSigningIn() = runTest {
        assertThat(failureOf { provider.sources(conn.copy(config = emptyMap())) }).isInstanceOf(NeedsSignInException::class.java)
    }
}
```

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleConnectScreenTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster

@RunWith(AndroidJUnit4::class)
class GoogleConnectScreenTest {
    private object NoToasts : Toaster {
        override fun show(message: String, icon: String) = Unit
    }

    @get:Rule val compose = createComposeRule()

    private val google = FakeGoogleServer()
    private val authorizer = FakeAuthorizer()
    private lateinit var provider: GoogleCalendarProvider

    @Before
    fun setUp() {
        provider = GoogleCalendarProvider(GoogleApi(google.start(), FakeTokenSource(), OkHttpClient()), authorizer, NoToasts)
        google.addCalendar("family@example.com", "Family", primary = true)
    }

    @After
    fun tearDown() = google.shutdown()

    @Test
    fun anAccountAlreadyGrantedConnectsWithNoScreens() {
        var connected: Connection? = null
        compose.setContent { provider.ConnectScreen(existing = null, onConnected = { connected = it }, onCancel = {}) }
        compose.waitUntil(5_000) { connected != null }
        assertThat(connected!!.config).containsExactly(CONFIG_ACCOUNT, "family@example.com")
    }

    @Test
    fun backingOutCancels() {
        authorizer.failWith = NeedsSignInException("the user cancelled")
        var cancelled = 0
        compose.setContent { provider.ConnectScreen(existing = null, onConnected = {}, onCancel = { cancelled++ }) }
        compose.waitUntil(5_000) { cancelled == 1 }
    }
}
```

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProviderContractTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar_testkit.CalendarProviderContractTest
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster

/** The shared contract, against Google through the fake server (3a design D15). */
@RunWith(RobolectricTestRunner::class)
class GoogleCalendarProviderContractTest : CalendarProviderContractTest() {
    private object NoToasts : Toaster {
        override fun show(message: String, icon: String) = Unit
    }

    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val google = FakeGoogleServer(london)
    private val family = CalendarSource("family@example.com", "Family", writable = true, primary = true)
    private val subject = GoogleCalendarProvider(GoogleApi(google.start(), FakeTokenSource(), OkHttpClient()), FakeAuthorizer(), NoToasts)

    init {
        google.addCalendar(family.id, "Family", primary = true)
        google.addCalendar("school@group", "School terms", accessRole = "reader")
        fun at(day: Long, hour: Int) = today.plusDays(day).atTime(hour, 0).atZone(london).toInstant()
        google.putEvent(family.id, google.timed("swim", "Swim", at(0, 16), at(0, 17)))
        google.putEvent(family.id, google.allDay("bins", "Bin day", today.plusDays(2), today.plusDays(3)))
        google.putEvent(family.id, google.timed("piano", "Piano", at(1, 15), at(1, 16)) { putJsonArray("recurrence") { add("RRULE:FREQ=WEEKLY") } })
        google.putEvent(family.id, google.timed("piano_1", "Piano", at(1, 15), at(1, 16)) { put("recurringEventId", "piano") })
        google.putEvent(family.id, google.timed("piano_2", "Piano", at(8, 15), at(8, 16)) { put("recurringEventId", "piano") })
        google.putEvent(family.id, google.timed("trip", "School trip", at(20, 8), at(20, 15)))
        google.putEvent("school@group", google.allDay("inset", "INSET day", today.plusDays(3), today.plusDays(4)))
    }

    @After
    fun tearDown() = google.shutdown()

    override fun provider() = subject
    override fun connection() = Connection("g1", GOOGLE_PROVIDER_ID, "Google", mapOf(CONFIG_ACCOUNT to family.id))
    override fun range() = DateRange(today.minusDays(1), today.plusDays(15), london)
    override fun sourceWithEvents() = family
    override fun outOfRangeEventTitle() = "School trip"
    override fun recurringTitle() = "Piano"
    override fun simulateAuthFailure() = {
        google.failNext(401, "authError")
        google.failNext(401, "authError")
    }
    override fun simulateUnreachable() = { google.failNext(503) }
}
```
(`@RunWith` on a subclass of the contract suite: the suite's own tests run under Robolectric for `android.util.Log`. JUnit uses the subclass's runner.)

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: compilation FAILS: `GoogleCalendarProvider`, `toEventTime`, `PERSON_KEY` and `CREATED_BY_KEY` are unresolved.

- [ ] **Step 4: One event, mapped**

Create `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleEvents.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import java.time.LocalDate
import java.time.OffsetDateTime
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent

/** The tablet's tags in extendedProperties.private (parent spec §6). */
internal const val PERSON_KEY = "culvery.person"
internal const val CREATED_BY_KEY = "culvery.createdBy"

/** As Google shows an event with no title. */
internal const val NO_TITLE = "(No title)"

private const val CANCELLED = "cancelled"
private const val WORKING_LOCATION = "workingLocation"

/** Deleted, or a working location, which isn't an event people plan around (3a design §3.5). */
internal val GoogleEvent.isGone: Boolean get() = status == CANCELLED || eventType == WORKING_LOCATION

/** A start or end: a dateTime with Google's offset, or an all-day date (Google's end date is already exclusive). */
internal fun GoogleTime.toEventTime(): EventTime? = runCatching {
    when {
        dateTime != null -> EventTime.Timed(OffsetDateTime.parse(dateTime).toInstant())
        date != null -> EventTime.AllDay(LocalDate.parse(date))
        else -> null
    }
}.getOrNull()

/** The event as the contract has it, with its series' [rule]; null when it has no start or end the tablet can read. */
internal fun GoogleEvent.toRemoteEvent(rule: String?): RemoteEvent? {
    val from = start?.toEventTime() ?: return null
    val to = end?.toEventTime() ?: return null
    val tags = extendedProperties?.privateProperties.orEmpty()
    return RemoteEvent(
        remoteId = id,
        title = summary?.takeIf { it.isNotBlank() } ?: NO_TITLE,
        start = from,
        end = to,
        recurring = recurringEventId != null,
        forPerson = tags[PERSON_KEY],
        createdBy = tags[CREATED_BY_KEY],
        recurrenceRule = rule,
    )
}
```

- [ ] **Step 5: The provider's reads and its connect screen**

Create `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProvider.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SourceGoneException
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.Toaster

private const val TAG = "GoogleCalendar"
private const val PAGE_SIZE = "250"
private val WRITE_ROLES = setOf("owner", "writer")

/**
 * Google Calendar API v3 (3a design §3.1–§3.7). The connection's config holds only the account's email; every call
 * takes a fresh token from Play services. Each series' RRULE is fetched once and kept in memory per calendar until
 * that calendar's next full sync.
 */
@Singleton
class GoogleCalendarProvider @Inject constructor(
    private val api: GoogleApi,
    private val authorizer: Authorizer,
    private val toaster: Toaster,
) : CalendarProvider {
    override val descriptor = ProviderDescriptor(GOOGLE_PROVIDER_ID, GOOGLE_DISPLAY_NAME, "calendar_month", setOf(Feature.READ))

    // By connection and calendar: each series' RRULE, "" for a series with none.
    private val rules = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    private class Listed(val items: List<GoogleEvent>, val syncToken: String?)

    /**
     * Runs the account chooser and Google's consent through Play services (3a design §3.2). CalendarConnectHost draws
     * the card around it; this draws nothing and only follows the flow. An account already granted needs no screens.
     */
    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        val flow = remember { GoogleConnectFlow(authorizer, api, toaster) }
        val scope = rememberCoroutineScope()
        val connected by rememberUpdatedState(onConnected)
        val cancelled by rememberUpdatedState(onCancel)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            scope.launch {
                // Play services has its answer now; screens asked for a second time read as a stop.
                when (val step = flow.afterScreens(existing, result.data)) {
                    is ConnectStep.Done -> connected(step.connection)
                    else -> cancelled()
                }
            }
        }
        LaunchedEffect(existing) {
            when (val step = flow.start(existing)) {
                is ConnectStep.Done -> connected(step.connection)
                is ConnectStep.ShowScreens -> launcher.launch(IntentSenderRequest.Builder(step.intent.intentSender).build())
                ConnectStep.Stopped -> cancelled()
            }
        }
    }

    override suspend fun sources(conn: Connection): List<CalendarSource> {
        val account = accountOf(conn)
        val sources = mutableListOf<CalendarSource>()
        var pageToken: String? = null
        do {
            val page = api.send(account, "GET", api.url("users", "me", "calendarList", query = mapOf("pageToken" to pageToken)))
                .readOrUnreachable("The calendar list")
                .decode(CalendarListPage.serializer())
            page.items.forEach { e ->
                sources += CalendarSource(
                    id = e.id,
                    name = e.summaryOverride ?: e.summary ?: e.id,
                    writable = e.accessRole in WRITE_ROLES,
                    shown = e.selected == true && e.hidden != true,
                    primary = e.primary == true,
                )
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return sources
    }

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        val account = accountOf(conn)
        // 410 Gone: Google expired the sync token, so the provider runs a full sync itself (3a design §3.5).
        if (cursor != null) incremental(conn, account, source, cursor)?.let { return it }
        return full(conn, account, source, range)
    }

    private suspend fun full(conn: Connection, account: String, source: CalendarSource, range: DateRange): SyncResult {
        rulesFor(conn, source).clear()
        val window = mapOf("timeMin" to range.startInstant.toString(), "timeMax" to range.endInstant.toString())
        val listed = list(account, source, window) ?: throw UnreachableException("Google Calendar refused a full sync of ${source.name}")
        val upserts = listed.items.filterNot { it.isGone }.mapNotNull { it.toRemoteEvent(ruleOf(conn, account, source, it)) }
        return SyncResult(upserts, emptyList(), listed.syncToken?.let(::SyncCursor), fullReplace = true)
    }

    private suspend fun incremental(conn: Connection, account: String, source: CalendarSource, cursor: SyncCursor): SyncResult? {
        val listed = list(account, source, mapOf("syncToken" to cursor.value)) ?: return null
        val upserts = mutableListOf<RemoteEvent>()
        val removed = mutableListOf<String>()
        listed.items.forEach { event ->
            if (event.isGone) {
                removed += event.id
            } else {
                event.toRemoteEvent(ruleOf(conn, account, source, event))?.let { upserts += it }
            }
        }
        return SyncResult(upserts, removed, listed.syncToken?.let(::SyncCursor) ?: cursor, fullReplace = false)
    }

    /** Every page of events.list with [query]; null for 410 Gone. A deleted or forbidden calendar is SourceGone (§3.5). */
    private suspend fun list(account: String, source: CalendarSource, query: Map<String, String>): Listed? {
        val items = mutableListOf<GoogleEvent>()
        var pageToken: String? = null
        while (true) {
            val url = api.url(
                "calendars", source.id, "events",
                query = query + mapOf("singleEvents" to "true", "maxResults" to PAGE_SIZE, "pageToken" to pageToken),
            )
            val answer = api.send(account, "GET", url)
            if (answer.code == 410) return null
            if (answer.code == 404 || answer.code == 403) {
                Log.w(TAG, "${source.name}: Google Calendar answered ${answer.code} (${answer.reason}): ${answer.message}")
                throw SourceGoneException("${source.name} isn't in Google Calendar any more")
            }
            val page = answer.readOrUnreachable("The events of ${source.name}").decode(EventsPage.serializer())
            items += page.items
            pageToken = page.nextPageToken ?: return Listed(items, page.nextSyncToken)
        }
    }

    /** An instance's series rule (3a design D12): fetched once per series; a failed fetch is null and doesn't fail the sync. */
    private suspend fun ruleOf(conn: Connection, account: String, source: CalendarSource, event: GoogleEvent): String? {
        val series = event.recurringEventId ?: return null
        val cache = rulesFor(conn, source)
        cache[series]?.let { return it.ifEmpty { null } }
        val rule = try {
            api.send(account, "GET", api.url("calendars", source.id, "events", series))
                .readOrUnreachable("The series $series")
                .decode(GoogleEvent.serializer())
                .recurrence
                ?.firstOrNull { it.startsWith("RRULE:") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read a series' rule; its Repeats row says Yes", e)
            return null
        }
        cache[series] = rule.orEmpty()
        return rule
    }

    private fun rulesFor(conn: Connection, source: CalendarSource) = rules.getOrPut("${conn.id}\u0000${source.id}") { ConcurrentHashMap() }

    private fun accountOf(conn: Connection): String =
        conn.config[CONFIG_ACCOUNT] ?: throw NeedsSignInException("The Google connection ${conn.id} has no account")
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: PASS, the contract suite's read checks included; its write checks are skipped (the descriptor says only `READ` until Task 11).

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add provider/calendar-google
git commit -m "Read Google calendars and events: full and incremental syncs, expired tokens, pages, series rules, and the connect screen"
```

---

### Task 11: `GoogleCalendarProvider` writing — insert with the key, 409, touched-field PATCH, delete, `find`, colours, R8 wording — and the whole contract suite

**Files:**
- Create: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleColors.kt`
- Modify: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleEvents.kt`
- Modify: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProvider.kt`
- Create: `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/di/GoogleCalendarModule.kt`
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleColorsTest.kt` (create)
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleWriteTest.kt` (create)
- Test: `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProviderContractTest.kt` (modify)

**Interfaces:**
- Consumes: everything in Tasks 8–10; `EventField`, `EVENT_GONE`, `CalendarWriter.update(fields)`/`find` (Task 1).
- Produces:
  - `internal val EVENT_COLORS: Map<String, Int>`; `internal fun nearestColorId(argb: Long): String`
  - `internal fun insertBody(draft: EventDraft, clientKey: String): JsonObject`; `internal fun patchBody(draft: EventDraft, fields: Set<EventField>): JsonObject`
  - `GoogleCalendarProvider : CalendarProvider, CalendarWriter` with `READ` + `WRITE`; `providerId = "calendar.google"`
  - `GoogleCalendarModule`: binds the provider `@IntoSet` as `CalendarProvider` and `CalendarWriter`, `TokenSource` → `PlayServicesTokenSource`, `Authorizer` → `PlayServicesAuthorizer`; provides the `OkHttpClient` (connect 15 s, read 30 s) and the `GoogleApi` on `GOOGLE_CALENDAR_BASE_URL`

- [ ] **Step 1: Write the failing tests**

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleColorsTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GoogleColorsTest {
    @Test
    fun eachOfGooglesColoursIsItsOwnNearest() {
        EVENT_COLORS.forEach { (id, rgb) -> assertThat(nearestColorId(0xFF000000L or rgb.toLong())).isEqualTo(id) }
    }

    @Test
    fun theSamplePeopleGetBasilBlueberryAndFlamingo() {
        assertThat(nearestColorId(0xFF4CB387)).isEqualTo("10")
        assertThat(nearestColorId(0xFF5B9BE0)).isEqualTo("9")
        assertThat(nearestColorId(0xFFE07BA8)).isEqualTo("4")
    }
}
```

Create `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleWriteTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.EVENT_GONE
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster

@RunWith(AndroidJUnit4::class)
class GoogleWriteTest {
    private object NoToasts : Toaster {
        override fun show(message: String, icon: String) = Unit
    }

    private val london = ZoneId.of("Europe/London")
    private val google = FakeGoogleServer(london)
    private lateinit var provider: GoogleCalendarProvider
    private val conn = Connection("g1", GOOGLE_PROVIDER_ID, "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val family = CalendarSource("family@example.com", "Family", writable = true, primary = true)
    private val holidays = CalendarSource("holidays@group", "UK holidays", writable = false)
    private val key = "0123456789abcdef0123456789abcdef"

    @Before
    fun setUp() {
        provider = GoogleCalendarProvider(GoogleApi(google.start(), FakeTokenSource(), OkHttpClient()), FakeAuthorizer(), NoToasts)
        google.addCalendar(family.id, "Family", primary = true)
        google.addCalendar(holidays.id, "UK holidays", accessRole = "reader")
    }

    @After
    fun tearDown() = google.shutdown()

    private fun at(day: Int, hour: Int): Instant = LocalDate.of(2026, 9, day).atTime(hour, 0).atZone(london).toInstant()

    private fun draft(title: String = "Swim", forPerson: String? = "mia-id", color: Long? = 0xFFE07BA8) =
        EventDraft(title, EventTime.Timed(at(23, 16)), EventTime.Timed(at(23, 17)), forPerson, "sam-id", color)

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: Exception) {
            e
        }

    @Test
    fun anInsertUsesTheKeyAsItsIdWithTheTagsColourAndUtcTimes() = runTest {
        val made = provider.create(conn, family, draft(), key)
        assertThat(made.remoteId).isEqualTo(key)
        val sent = google.bodies.single()
        assertThat(sent.text("id")).isEqualTo(key)
        assertThat(sent.text("summary")).isEqualTo("Swim")
        assertThat(sent.obj("start").text("dateTime")).isEqualTo("2026-09-23T15:00:00Z")
        assertThat(sent.obj("start")["timeZone"]).isNull()
        assertThat(sent.obj("extendedProperties").obj("private")).isEqualTo(
            JsonObject(mapOf(PERSON_KEY to JsonPrimitive("mia-id"), CREATED_BY_KEY to JsonPrimitive("sam-id"))),
        )
        assertThat(sent.text("colorId")).isEqualTo("4")
    }

    @Test
    fun anAllDayInsertSendsDatesAndFamilySendsNoColour() = runTest {
        val bins = EventDraft("Bin day", EventTime.AllDay(LocalDate.of(2026, 9, 24)), EventTime.AllDay(LocalDate.of(2026, 9, 25)), "family", "sam-id")
        provider.create(conn, family, bins, key)
        val sent = google.bodies.single()
        assertThat(sent.obj("start").text("date")).isEqualTo("2026-09-24")
        assertThat(sent.obj("end").text("date")).isEqualTo("2026-09-25")
        assertThat(sent["colorId"]).isNull()
    }

    @Test
    fun aRepeatedInsertReturnsTheEventItsKeyMade() = runTest {
        provider.create(conn, family, draft(), key)
        // The reply was lost; the queued create is sent again with the same key.
        val again = provider.create(conn, family, draft(), key)
        assertThat(again.remoteId).isEqualTo(key)
        assertThat(again.title).isEqualTo("Swim")
        assertThat(google.requests.map { it.method }).containsExactly("POST", "POST", "GET").inOrder()
    }

    @Test
    fun anInsertWhoseEventWasDeletedOnAPhoneIsRefused() = runTest {
        provider.create(conn, family, draft(), key)
        google.cancel(family.id, key)
        val error = failureOf { provider.create(conn, family, draft(), key) }
        assertThat(error).isInstanceOf(WriteRejectedException::class.java)
        assertThat(error?.message).isEqualTo(EVENT_GONE)
        assertThat(google.event(family.id, key)!!.text("status")).isEqualTo("cancelled")
    }

    @Test
    fun aPatchSendsOnlyItsFields() = runTest {
        provider.create(conn, family, draft(), key)
        provider.update(conn, family, key, draft(title = "Swim club"), setOf(EventField.TITLE))
        assertThat(google.bodies.last().keys).containsExactly("summary")
        provider.update(conn, family, key, draft(), setOf(EventField.TIMES))
        assertThat(google.bodies.last().keys).containsExactly("start", "end")
        assertThat(google.bodies.last().obj("start")["date"]).isEqualTo(JsonNull)
        provider.update(conn, family, key, draft(forPerson = "sam-id", color = 0xFF5B9BE0), setOf(EventField.FOR_PERSON))
        assertThat(google.bodies.last().keys).containsExactly("extendedProperties", "colorId")
        assertThat(google.bodies.last().text("colorId")).isEqualTo("9")
    }

    @Test
    fun aTitlePatchKeepsAPhoneChangeToTheTime() = runTest {
        provider.create(conn, family, draft(), key)
        // A phone moves it to 18:00 while the tablet's title edit waits.
        google.putEvent(family.id, google.timed(key, "Swim", at(23, 18), at(23, 19)))
        val updated = provider.update(conn, family, key, draft(title = "Swim club"), setOf(EventField.TITLE))
        assertThat(updated.title).isEqualTo("Swim club")
        assertThat(updated.start).isEqualTo(EventTime.Timed(at(23, 18)))
    }

    @Test
    fun aPersonPatchKeepsCreatedBy() = runTest {
        provider.create(conn, family, draft(), key)
        val updated = provider.update(conn, family, key, draft(forPerson = "sam-id"), setOf(EventField.FOR_PERSON))
        assertThat(updated.forPerson to updated.createdBy).isEqualTo("sam-id" to "sam-id")
        val stored = google.event(family.id, key)!!.obj("extendedProperties").obj("private")
        assertThat(stored.keys).containsExactly(PERSON_KEY, CREATED_BY_KEY)
    }

    @Test
    fun whoChangedToFamilyClearsTheColour() = runTest {
        provider.create(conn, family, draft(), key)
        provider.update(conn, family, key, draft(forPerson = "family", color = null), setOf(EventField.FOR_PERSON))
        assertThat(google.bodies.last()["colorId"]).isEqualTo(JsonNull)
        assertThat(google.event(family.id, key)!!["colorId"]).isNull()
    }

    @Test
    fun aTimedEventMadeAllDayLosesItsTime() = runTest {
        provider.create(conn, family, draft(), key)
        val allDay = EventDraft("Swim", EventTime.AllDay(LocalDate.of(2026, 9, 23)), EventTime.AllDay(LocalDate.of(2026, 9, 24)), null, null)
        val updated = provider.update(conn, family, key, allDay, setOf(EventField.TIMES))
        assertThat(updated.start).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 9, 23)))
        assertThat(google.event(family.id, key)!!.obj("start").keys).containsExactly("date")
    }

    @Test
    fun updatingAnEventThatIsGoneIsRefusedAsGone() = runTest {
        assertThat(failureOf { provider.update(conn, family, "nope0", draft(), setOf(EventField.TITLE)) }?.message).isEqualTo(EVENT_GONE)
        provider.create(conn, family, draft(), key)
        google.cancel(family.id, key)
        assertThat(failureOf { provider.update(conn, family, key, draft(), setOf(EventField.TITLE)) }?.message).isEqualTo(EVENT_GONE)
    }

    @Test
    fun deletingSucceedsWhateverIsLeft() = runTest {
        provider.create(conn, family, draft(), key)
        provider.delete(conn, family, key)
        // 410: already deleted; 404: never there.
        provider.delete(conn, family, key)
        provider.delete(conn, family, "nope0")
        assertThat(google.event(family.id, key)!!.text("status")).isEqualTo("cancelled")
    }

    @Test
    fun findReturnsTheEventOrNull() = runTest {
        provider.create(conn, family, draft(), key)
        assertThat(provider.find(conn, family, key)?.title).isEqualTo("Swim")
        assertThat(provider.find(conn, family, "nope0")).isNull()
        google.cancel(family.id, key)
        assertThat(provider.find(conn, family, key)).isNull()
    }

    @Test
    fun refusalsUseTheTabletsWordsNotGooglesAndTheOthersAreRetried() = runTest {
        assertThat(failureOf { provider.create(conn, holidays, draft(), key) }?.message).isEqualTo("this calendar can't be changed from the tablet")
        google.failNext(400, "invalid")
        assertThat(failureOf { provider.create(conn, family, draft(), key) }?.message).isEqualTo("the change was refused")
        google.failNext(401, "authError")
        google.failNext(401, "authError")
        assertThat(failureOf { provider.create(conn, family, draft(), key) }).isInstanceOf(NeedsSignInException::class.java)
        google.failNext(503)
        assertThat(failureOf { provider.create(conn, family, draft(), key) }).isInstanceOf(UnreachableException::class.java)
    }
}
```

In `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProviderContractTest.kt`:
1. Add `import java.util.concurrent.CountDownLatch`.
2. After `override fun simulateUnreachable() = …` add:
```kotlin
    override fun writer() = subject
    override fun writableSource() = family
    override fun gateWrites(): (() -> Unit)? {
        val hold = CountDownLatch(1)
        google.writeHold = hold
        return { hold.countDown() }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: compilation FAILS: `nearestColorId`, `EVENT_COLORS`, and the provider's `create`, `update`, `delete` and `find` are unresolved.

- [ ] **Step 3: Google's colours**

Create `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleColors.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google

/** Google Calendar's 11 event colours (colors.get, "event"): id to background RGB. */
internal val EVENT_COLORS: Map<String, Int> = mapOf(
    "1" to 0xa4bdfc,
    "2" to 0x7ae7bf,
    "3" to 0xdbadff,
    "4" to 0xff887c,
    "5" to 0xfbd75b,
    "6" to 0xffb878,
    "7" to 0x46d6db,
    "8" to 0xe1e1e1,
    "9" to 0x5484ed,
    "10" to 0x51b749,
    "11" to 0xdc2127,
)

private const val BYTE = 0xFF

/** The event colour nearest [argb] (a person's colour) by RGB distance (3a design §3.6). */
internal fun nearestColorId(argb: Long): String {
    val r = (argb shr 16).toInt() and BYTE
    val g = (argb shr 8).toInt() and BYTE
    val b = argb.toInt() and BYTE
    return EVENT_COLORS.minBy { (_, rgb) ->
        val dr = (rgb shr 16 and BYTE) - r
        val dg = (rgb shr 8 and BYTE) - g
        val db = (rgb and BYTE) - b
        dr * dr + dg * dg + db * db
    }.key
}
```

- [ ] **Step 4: Write bodies**

In `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleEvents.kt`:
1. Add the imports:
```kotlin
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
```
2. Add at the end of the file:
```kotlin
/**
 * events.insert (3a design §3.6): the client key as the id, the tags (a null one left out), and the colour. Timed
 * times go as UTC with no timeZone, so Google shows them in the calendar's own zone; all-day as dates.
 */
internal fun insertBody(draft: EventDraft, clientKey: String): JsonObject = buildJsonObject {
    put("id", clientKey)
    put("summary", draft.title)
    put("start", timeJson(draft.start, forPatch = false))
    put("end", timeJson(draft.end, forPatch = false))
    putJsonObject("extendedProperties") {
        putJsonObject("private") {
            draft.forPerson?.let { put(PERSON_KEY, it) }
            draft.createdBy?.let { put(CREATED_BY_KEY, it) }
        }
    }
    draft.forPersonColor?.let { put("colorId", nearestColorId(it)) }
}

/**
 * A PATCH of [fields] only (3a design C3). Switching between timed and all-day sends the other key as null. Google
 * merges the keys of extendedProperties.private, so culvery.createdBy and anything else there is kept; it is never
 * sent. Family or untagged clears the colour.
 */
internal fun patchBody(draft: EventDraft, fields: Set<EventField>): JsonObject = buildJsonObject {
    if (EventField.TITLE in fields) put("summary", draft.title)
    if (EventField.TIMES in fields) {
        put("start", timeJson(draft.start, forPatch = true))
        put("end", timeJson(draft.end, forPatch = true))
    }
    if (EventField.FOR_PERSON in fields) {
        putJsonObject("extendedProperties") {
            putJsonObject("private") { put(PERSON_KEY, draft.forPerson?.let(::JsonPrimitive) ?: JsonNull) }
        }
        put("colorId", draft.forPersonColor?.let { JsonPrimitive(nearestColorId(it)) } ?: JsonNull)
    }
}

private fun timeJson(time: EventTime, forPatch: Boolean): JsonObject = buildJsonObject {
    when (time) {
        is EventTime.Timed -> {
            put("dateTime", time.instant.toString())
            if (forPatch) put("date", JsonNull)
        }
        is EventTime.AllDay -> {
            put("date", time.date.toString())
            if (forPatch) put("dateTime", JsonNull)
        }
    }
}
```

- [ ] **Step 5: The writer**

In `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleCalendarProvider.kt`:
1. Add the imports:
```kotlin
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.EVENT_GONE
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
```
2. Replace `) : CalendarProvider {` with `) : CalendarProvider, CalendarWriter {`, and the descriptor with:
```kotlin
    override val descriptor = ProviderDescriptor(GOOGLE_PROVIDER_ID, GOOGLE_DISPLAY_NAME, "calendar_month", setOf(Feature.READ, Feature.WRITE))
    override val providerId = GOOGLE_PROVIDER_ID
```
3. Before `private fun rulesFor(…)` add:
```kotlin
    /** events.insert with id = the client key (3a design §3.6); a 409 means it exists, so it is looked up (the lost-reply retry). */
    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent {
        val account = accountOf(conn)
        val answer = api.send(account, "POST", api.url("calendars", source.id, "events"), insertBody(draft, clientKey))
        return when {
            answer.isSuccessful -> answer.decode(GoogleEvent.serializer()).written()
            // A create never recreates a deleted event (C10): a key whose event is gone is refused.
            answer.code == 409 -> lookUp(account, source, clientKey) ?: throw WriteRejectedException(EVENT_GONE)
            else -> throw answer.refusal("Adding an event")
        }
    }

    override suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft, fields: Set<EventField>): RemoteEvent {
        val answer = api.send(accountOf(conn), "PATCH", api.url("calendars", source.id, "events", remoteId), patchBody(draft, fields))
        return when {
            answer.isSuccessful -> answer.decode(GoogleEvent.serializer()).written()
            answer.code == 404 || answer.code == 410 -> throw WriteRejectedException(EVENT_GONE)
            else -> throw answer.refusal("Changing an event")
        }
    }

    /** 404 and 410 are success: the event is already gone. */
    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) {
        val answer = api.send(accountOf(conn), "DELETE", api.url("calendars", source.id, "events", remoteId))
        if (answer.isSuccessful || answer.code == 404 || answer.code == 410) return
        throw answer.refusal("Deleting an event")
    }

    override suspend fun find(conn: Connection, source: CalendarSource, remoteId: String): RemoteEvent? =
        lookUp(accountOf(conn), source, remoteId)

    /** The event; null for 404, 410 or a cancelled one. */
    private suspend fun lookUp(account: String, source: CalendarSource, remoteId: String): RemoteEvent? {
        val answer = api.send(account, "GET", api.url("calendars", source.id, "events", remoteId))
        if (answer.code == 404 || answer.code == 410) return null
        val event = answer.readOrUnreachable("Looking an event up").decode(GoogleEvent.serializer())
        return if (event.isGone) null else event.written()
    }

    /** A written event, as Google holds it now: never an instance of a series, so no rule. */
    private fun GoogleEvent.written(): RemoteEvent =
        toRemoteEvent(rule = null) ?: throw UnreachableException("Google Calendar returned an event with no times the tablet can read")
```

- [ ] **Step 6: Bind it**

Create `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/di/GoogleCalendarModule.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_google.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Duration
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.provider.calendar_google.Authorizer
import uk.co.siland.culvery.provider.calendar_google.GOOGLE_CALENDAR_BASE_URL
import uk.co.siland.culvery.provider.calendar_google.GoogleApi
import uk.co.siland.culvery.provider.calendar_google.GoogleCalendarProvider
import uk.co.siland.culvery.provider.calendar_google.PlayServicesAuthorizer
import uk.co.siland.culvery.provider.calendar_google.PlayServicesTokenSource
import uk.co.siland.culvery.provider.calendar_google.TokenSource

// OkHttp's own timeouts sit inside the engine's (10 s editor, 60 s drain and sync), so the engine's decide (3a design §3.1).
private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
private val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleCalendarModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: GoogleCalendarProvider): CalendarProvider

    @Binds
    @IntoSet
    abstract fun writer(impl: GoogleCalendarProvider): CalendarWriter

    @Binds
    abstract fun tokens(impl: PlayServicesTokenSource): TokenSource

    @Binds
    abstract fun authorizer(impl: PlayServicesAuthorizer): Authorizer

    companion object {
        @Provides
        @Singleton
        fun client(): OkHttpClient = OkHttpClient.Builder().connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build()

        @Provides
        @Singleton
        fun api(client: OkHttpClient, tokens: TokenSource): GoogleApi = GoogleApi(GOOGLE_CALENDAR_BASE_URL.toHttpUrl(), tokens, client)
    }
}
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :provider:calendar-google:testDebugUnitTest`
Expected: PASS: the whole contract suite runs against Google through the fake server, the write checks included, and `aWriteReturnsPromptlyWhenItsCallerIsCancelled` proves the OkHttp call is cancelled with its caller (R9).

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. (`:app` doesn't depend on the module yet; Task 13 wires it.)

- [ ] **Step 9: Commit**

```bash
git add provider/calendar-google
git commit -m "Write to Google Calendar: keyed inserts, touched-field patches, deletes, lookups and person colours, passing the whole contract suite"
```

---

### Task 12: Wording and Repeats — the service name in copy (D11) and the Repeats row from the RRULE (D12)

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Repeats.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarRepository.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheet.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHost.kt`
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/SampleEvents.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/RepeatsTest.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt`, `CalendarSyncTest.kt`, `CalendarEditorTest.kt`, `StubEditor.kt`, `ui/SampleUi.kt`, `ui/EventDetailSheetTest.kt`, `ui/EventDetailHostTest.kt`, `ui/EventEditorHostTest.kt` (modify)
- Test: `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt` (modify)
- Test: `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`, `SampleRollbackTest.kt` (modify)
- Screenshots: `capability/calendar/src/test/screenshots/detail_recurring_{dark,light}.png`, `detail_delete_confirm_{dark,light}.png` (re-recorded)

**Interfaces:**
- Consumes: `RemoteEvent.recurrenceRule`, `StoredEvent.recurrenceRule` (Tasks 1, 2); the editor's constructor (Tasks 3, 5).
- Produces:
  - `const val REPEATS_YES = "Yes"`; `fun repeatsLabel(rule: String?, start: EventTime, zone: ZoneId): String`
  - `EventUi(…, serviceName: String = "", repeats: String = REPEATS_YES)`
  - `CalendarRepository.masterService: Flow<String?>` (replaces `masterLabel`): the master's service name ("Google Calendar"), falling back to its connection label when its provider isn't installed; null when there is nowhere to add
  - `CalendarEditor` internal constructor gains `serviceOf: (providerId: String) -> String?` (last); `@Inject` constructor gains `providers: Set<@JvmSuppressWildcards CalendarProvider>` (last). The editor's and the drain's failure toasts name the service, falling back to the connection label.

- [ ] **Step 1: Write the failing tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/RepeatsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class RepeatsTest {
    private val london = ZoneId.of("Europe/London")

    // Tuesday 22 September 2026.
    private val tuesday = EventTime.AllDay(LocalDate.of(2026, 9, 22))

    private fun label(rule: String?) = repeatsLabel(rule, tuesday, london)

    @Test
    fun simpleRulesReadAsWords() {
        val expected = mapOf(
            "RRULE:FREQ=DAILY" to "Every day",
            "RRULE:FREQ=WEEKLY" to "Every week",
            "RRULE:FREQ=WEEKLY;BYDAY=TU" to "Every week",
            "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TU" to "Every 2 weeks",
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=22" to "Every month",
            "RRULE:FREQ=YEARLY" to "Every year",
            "RRULE:FREQ=DAILY;INTERVAL=3" to "Every 3 days",
            "RRULE:FREQ=MONTHLY;INTERVAL=6" to "Every 6 months",
            "RRULE:FREQ=WEEKLY;UNTIL=20261231T000000Z;WKST=MO" to "Every week",
            "RRULE:FREQ=WEEKLY;COUNT=10" to "Every week",
        )
        expected.forEach { (rule, words) -> assertThat(label(rule)).isEqualTo(words) }
    }

    @Test
    fun anythingElseIsYes() {
        listOf(
            null,
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE",
            "RRULE:FREQ=WEEKLY;BYDAY=WE",
            "RRULE:FREQ=MONTHLY;BYDAY=4TU",
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=1",
            "RRULE:FREQ=HOURLY",
            "RRULE:FREQ=DAILY;INTERVAL=0",
            "RRULE:FREQ=WEEKLY;BYSETPOS=1",
            "not a rule",
        ).forEach { assertThat(label(it)).isEqualTo(REPEATS_YES) }
    }

    @Test
    fun aTimedStartIsReadInTheHouseholdZone() {
        // 23:30 UTC on Monday 21 September is 00:30 on Tuesday 22 September in London.
        assertThat(repeatsLabel("RRULE:FREQ=WEEKLY;BYDAY=TU", EventTime.Timed(Instant.parse("2026-09-21T23:30:00Z")), london))
            .isEqualTo("Every week")
    }
}
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt`:
1. Rename `theMasterLabelNamesWhereNewEventsGo` to `theMasterServiceNamesWhereNewEventsGo` and replace `repo.masterLabel` with `repo.masterService` in it (still `"Google"`: this repository has no providers, so the connection label stands in). In `thereIsNowhereToAddWithoutAWriterForTheMaster`, replace `readOnly.masterLabel` with `readOnly.masterService`.
2. Add at the end of the class:
```kotlin
    @Test
    fun eventsNameTheirServiceAndSayHowTheyRepeat() = runTest {
        val google = ScriptedProvider("calendar.test", displayName = "Google Calendar")
        val named = CalendarRepository(store, household, HouseholdZone(household), setOf(google), setOf(ScriptedWriter("calendar.test")))
        put("s-family", timed("Piano", 23, 15, 30, 60).copy(recurring = true, recurrenceRule = "RRULE:FREQ=WEEKLY"))
        val piano = named.day(LocalDate.of(2026, 9, 23)).first().single { it.title == "Piano" }
        assertThat(piano.serviceName).isEqualTo("Google Calendar")
        assertThat(piano.connectionLabel).isEqualTo("Google")
        assertThat(piano.repeats).isEqualTo("Every week")
        assertThat(named.masterService.first()).isEqualTo("Google Calendar")
    }
```
(`put` and `timed` are this class's existing helpers; its connection is `calendar.test`, labelled "Google".)

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/SampleUi.kt`:
1. Replace the body of `onFamilyCalendar` with:
```kotlin
        copy(sourceName = "Family calendar", connectionLabel = "Sample calendar", serviceName = "Google Calendar", createdBy = createdBy)
```
2. Replace `val detailRecurring = EventDetailUi(event("Swimming", "16:00–17:00", mia, recurring = true).onFamilyCalendar("Sam"), "Today · 16:00–17:00")` with:
```kotlin
    val detailRecurring = EventDetailUi(
        event("Swimming", "16:00–17:00", mia, recurring = true).onFamilyCalendar("Sam").copy(repeats = "Every week"),
        "Today · 16:00–17:00",
    )
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheetTest.kt`:
- replace `"Edit repeating events in Sample calendar on your phone."` with `"Edit repeating events in Google Calendar on your phone."`, and after that line add `compose.onNodeWithText("Every week").assertExists()`;
- replace `"“Dinner with Jo & Priya” will be removed from Sample calendar for everyone."` with `"“Dinner with Jo & Priya” will be removed from Google Calendar for everyone."`.

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`:
1. Replace `private val a = ScriptedProvider("calendar.a")` and `private val b = ScriptedProvider("calendar.b")` with:
```kotlin
    private val a = ScriptedProvider("calendar.a", displayName = "Service A")
    private val b = ScriptedProvider("calendar.b", displayName = "Service B")
```
2. The drain's toasts now name the service: replace every occurrence of the text `to C1` with `to Service A` (each is a toast expectation, e.g. `"Couldn't save to C1 — Event is locked"` → `"Couldn't save to Service A — Event is locked"`, `"Couldn't save 2 changes to C1"` → `"Couldn't save 2 changes to Service A"`).
3. Add at the end of the class:
```kotlin
    @Test
    fun aChangeForAConnectionWhoseProviderIsGoneIsNamedByItsLabel() = runTest {
        connect("c1", "calendar.gone", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null)
        sync.syncAll()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1")
    }
```

In `app/src/testDebug/java/uk/co/siland/culvery/SampleRollbackTest.kt`, replace `"Couldn't save to Sample calendar — Boiler service is locked"` with `"Couldn't save to Sample calendar (debug) — Boiler service is locked"`: the drain names the provider now.

In `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt`, add at the end of the class:
```kotlin
    @Test
    fun weeklySamplesCarryTheirRule() = runTest {
        val events = providerOn(today).familyEvents()
        assertThat(events.first { it.title == "Piano" }.recurrenceRule).isEqualTo("RRULE:FREQ=WEEKLY")
        assertThat(events.first { it.title == "Boiler service" }.recurrenceRule).isNull()
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest`
Expected: compilation FAILS: `repeatsLabel`, `REPEATS_YES`, `serviceName`, `repeats`, `masterService` and the provider's `displayName` parameter are unresolved.

- [ ] **Step 3: The Repeats label**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Repeats.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.ZoneId

/** The Repeats row when the rule isn't simple, or unknown. */
const val REPEATS_YES = "Yes"

private val UNITS = mapOf("DAILY" to "day", "WEEKLY" to "week", "MONTHLY" to "month", "YEARLY" to "year")

/** Parts that only bound the series or restate its start; the series' end (UNTIL, COUNT) isn't shown. */
private val PLAIN_PARTS = setOf("FREQ", "INTERVAL", "UNTIL", "COUNT", "WKST")

/**
 * The detail sheet's Repeats row (3a design D12, §3.5): "Every day", "Every week", "Every month", "Every year", or
 * "Every {n} days/weeks/months/years", when [rule] is simple. Simple allows a weekly BYDAY of the start's own weekday
 * and a monthly BYMONTHDAY of its own day, which Google writes for "Weekly on Tuesday". Anything else is "Yes".
 */
fun repeatsLabel(rule: String?, start: EventTime, zone: ZoneId): String {
    val parts = rule?.removePrefix("RRULE:")?.split(';')?.associate { part ->
        val pair = part.split('=', limit = 2)
        if (pair.size != 2) return REPEATS_YES
        pair[0].uppercase() to pair[1].uppercase()
    } ?: return REPEATS_YES
    val freq = parts["FREQ"] ?: return REPEATS_YES
    val unit = UNITS[freq] ?: return REPEATS_YES
    val day = start.instantIn(zone).atZone(zone).toLocalDate()
    val simple = parts.all { (key, value) ->
        key in PLAIN_PARTS ||
            (key == "BYDAY" && freq == "WEEKLY" && value == day.dayOfWeek.name.take(2)) ||
            (key == "BYMONTHDAY" && freq == "MONTHLY" && value == day.dayOfMonth.toString())
    }
    val interval = parts["INTERVAL"]?.let { it.toIntOrNull() ?: return REPEATS_YES } ?: 1
    if (!simple || interval < 1) return REPEATS_YES
    return if (interval == 1) "Every $unit" else "Every $interval ${unit}s"
}
```

- [ ] **Step 4: The service name and the repeats in the UI model**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`:
1. In `EventUi`, after `val connectionLabel: String = "",` add:
```kotlin
    /** The service's name ("Google Calendar"): failure, repeating-event and delete copy (3a design D11). */
    val serviceName: String = "",
```
and after `val createdBy: String = "",` add:
```kotlin
    /** The Repeats row: "Every week"… or "Yes" (3a design D12). */
    val repeats: String = REPEATS_YES,
```
2. Replace `SourceCatalog` with:
```kotlin
/** Sources, connection labels, service names and which providers can write: what the UI needs beyond the event row. */
internal class SourceCatalog(
    sources: List<StoredSource>,
    connections: List<StoredConnection>,
    private val writerIds: Set<String>,
    private val serviceNames: Map<String, String> = emptyMap(),
) {
    private val sources = sources.associateBy { it.connectionId to it.source.id }
    private val connections = connections.associate { it.connection.id to it.connection }

    fun source(connectionId: String, sourceId: String): StoredSource? = sources[connectionId to sourceId]

    fun label(connectionId: String): String = connections[connectionId]?.label.orEmpty()

    /** The provider's display name; the connection label when the provider isn't installed. */
    fun serviceName(connectionId: String): String =
        connections[connectionId]?.providerId?.let(serviceNames::get) ?: label(connectionId)

    fun hasWriter(connectionId: String): Boolean = connections[connectionId]?.providerId in writerIds
}
```
3. In `toUi`, after `connectionLabel = catalog.label(connectionId),` add:
```kotlin
        serviceName = catalog.serviceName(connectionId),
```
and after `createdBy = createdByLabel(createdBy, source, people),` add:
```kotlin
        repeats = repeatsLabel(recurrenceRule, start, zone),
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarRepository.kt`:
1. After `private val writerIds = writers.map { it.providerId }.toSet()` add:
```kotlin
    private val serviceNames = providers.associate { it.descriptor.id to it.descriptor.displayName }
```
2. Replace `masterLabel` (with its KDoc) with:
```kotlin
    /**
     * The service name of the writable master calendar, where new events go (the connection label if its provider isn't
     * installed); null when there is nowhere to add, which hides the add entry points (2b-2 design §4.1).
     */
    val masterService: Flow<String?> = combine(store.master(), store.connections()) { master, connections ->
        writableMaster(master, connections, writerIds)?.connection?.let { serviceNames[it.providerId] ?: it.label }
    }.distinctUntilChanged()
```
3. Replace `SourceCatalog(sources, connections, writerIds)` with `SourceCatalog(sources, connections, writerIds, serviceNames)`.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHost.kt`, replace `repo.masterLabel` with `repo.masterService` (twice).

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheet.kt`:
- replace `body = "Edit repeating events in ${e.connectionLabel} on your phone.",` with `body = "Edit repeating events in ${e.serviceName} on your phone.",`;
- replace `InfoRow("repeat", "Repeats") { Value("Yes") }` with `InfoRow("repeat", "Repeats") { Value(e.repeats) }`;
- replace `"“${e.title}” will be removed from ${e.connectionLabel} for everyone.",` with `"“${e.title}” will be removed from ${e.serviceName} for everyone.",`.
(The syncing pill keeps `connectionLabel`: "Syncing to Google…", spec §4.5.)

- [ ] **Step 5: The editor's and the drain's toasts name the service**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`:
1. In the internal constructor, after `private val personOf: suspend (PersonId) -> Person?,` add `private val serviceOf: (providerId: String) -> String?,`.
2. In the `@Inject` constructor, add the parameter `providers: Set<@JvmSuppressWildcards CalendarProvider>,` after `household: HouseholdRepository,`, and pass `{ id -> providers.firstOrNull { it.descriptor.id == id }?.descriptor?.displayName }` after `household::person,`.
3. After the `colorOf` function add:
```kotlin
    /** Failure copy names the service ("Google Calendar", 3a design D11); the connection label if it isn't installed. */
    private fun serviceName(connection: Connection): String = serviceOf(connection.providerId) ?: connection.label
```
4. Replace `onAppScope(ChangeKind.CREATE, to.connection.label)` with `onAppScope(ChangeKind.CREATE, serviceName(to.connection))`, and `onAppScope(kind, target.to.connection.label)` with `onAppScope(kind, serviceName(target.to.connection))`.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`, in `drainOutbox`, replace
```kotlin
                val label = stored?.connection?.label ?: REMOVED_CALENDAR
```
with
```kotlin
                // The service's name ("Google Calendar", 3a design D11); the label if its provider isn't installed.
                val label = stored?.connection?.let { c ->
                    providers.firstOrNull { it.descriptor.id == c.providerId }?.descriptor?.displayName ?: c.label
                } ?: REMOVED_CALENDAR
```

Pass the new editor argument at every construction:
- `CalendarEditorTest.kt`: in `editor(…)` add `serviceOf = { null },` after `personOf = household::person,`; in both `noWriter` constructions replace `lock, household::person,` with `lock, household::person, { null },` (no provider in these tests, so the connection label stays: their expected toasts are unchanged).
- `StubEditor.kt`: replace `CalendarWriteLock(), { null },` with `CalendarWriteLock(), { null }, { null },`.
- `ui/EventDetailHostTest.kt` and `ui/EventEditorHostTest.kt`: replace `CalendarWriteLock(), household::person,` with `CalendarWriteLock(), household::person, { null },`.
- `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`: replace `lock, household)` with `lock, household, setOf(fake))`.

In `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/SampleEvents.kt`, in both `RemoteEvent(…)` constructions in `forSource`, after `createdBy = tag(e.byName, idsByName),` add:
```kotlin
                            recurrenceRule = if (e.weekly) WEEKLY_RULE else null,
```
and add below `internal object SampleEvents {`:
```kotlin
    // So the debug build's Repeats row reads "Every week", as Google's would.
    private const val WEEKLY_RULE = "RRULE:FREQ=WEEKLY"
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS, except `DetailScreenshotTest` under `verifyRoborazziDebug` (next step).

- [ ] **Step 7: Re-record the changed detail screenshots and look at them**

Run: `./gradlew :capability:calendar:recordRoborazziDebug --tests "*DetailScreenshotTest*"`
Open `detail_recurring_dark.png`, `detail_recurring_light.png`, `detail_delete_confirm_dark.png` and `detail_delete_confirm_light.png`:
- the repeating note reads "Edit repeating events in Google Calendar on your phone." and the Repeats row "Every week";
- the confirmation reads "“Dinner with Jo & Priya” will be removed from Google Calendar for everyone.";
- nothing else moved (compare with `git diff --stat`: only these four images change).

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add capability/calendar provider/calendar-fake app
git commit -m "Name the service in failure, repeating-event and delete copy, and describe a series' repeat from its rule"
```

---

### Task 13: Connecting — the connecting card, the Connect card, Settings' Calendars block, the reconnect chip, `:app` wiring, and the sample's removal (D5)

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Capability.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarConnections.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarConnectHost.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarSettings.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`, `CalendarUi.kt`, `CalendarRepository.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ConnectCalendarCard.kt`, `CardHosts.kt`, `WeekView.kt`, `Components.kt`, `Pickers.kt`, `CalendarType.kt`
- Modify: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedProvider.kt`, `StubEditor.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CalendarConnectHostTest.kt`, `ui/CalendarSettingsTest.kt`, `ui/ConnectScreenshotTest.kt` (create)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarCapabilityTest.kt`, `CalendarRepositoryTest.kt`, `ui/CardsTest.kt`, `ui/WeekViewTest.kt`, `ui/CardScreenshotTest.kt`, `ui/OpenEventTest.kt`, `ui/CardHostsMidnightRolloverTest.kt` (modify)
- Modify: `app/build.gradle.kts`, `app/src/main/java/uk/co/siland/culvery/MainActivity.kt`, `CulveryApp.kt`, `shell/ui/SettingsPlaceholder.kt`
- Modify: `app/src/debug/java/uk/co/siland/culvery/DebugSeed.kt`, `app/src/release/java/uk/co/siland/culvery/DebugSeed.kt`
- Test: `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt` (modify)
- Screenshots (capability): `connect_{dark,light}` (re-recorded); `connecting_{dark,light}`, `settings_calendars_{dark,light}`, `settings_calendars_reconnect_{dark,light}`, `settings_calendars_connect_dark` (new)

**Interfaces:**
- Consumes: `ProviderDescriptor.userConnectable`, `CONFIG_ACCOUNT` (Task 1); `CalendarSetup.connectWithDefaults`/`reconnect`/`connectionIds`/`removeConnection` (Task 7); `GoogleCalendarModule` and the provider's `ConnectScreen` (Tasks 10, 11); `PickerCard`, `PickerButton` (made internal here).
- Produces:
  - `Capability.SettingsSection()` — `@Composable`, empty by default
  - `data class CalendarRow(connection: Connection, service: String, icon: String, health: ConnectionHealth, lastSyncMillis: Long?)`
  - `@Singleton class CalendarConnections @Inject constructor(store, setup, access: AccessControl, providers, @ApplicationScope scope)` with `connectable: Flow<List<CalendarProvider>>`, `rows: Flow<List<CalendarRow>>`, `provider(providerId): CalendarProvider?`, `suspend fun mayConnect(): Boolean`, `fun finish(connection: Connection, reconnecting: Boolean)`
  - `internal data class ConnectRequest(val provider: CalendarProvider, val existing: Connection?)`; `internal fun OverlayHost.showConnect(request: ConnectRequest, connections: CalendarConnections)`; `CalendarConnectHost`; `ConnectingCard(service, onCancel, content)`
  - `internal class Connector` / `rememberConnector(connections)` with `connect(providerId)` and `reconnect(connection)`
  - `CalendarSettings(rows, connectable, nowMillis, onReconnect, onConnect)`; `internal fun healthWords(row: CalendarRow, nowMillis: Long): String`
  - `ConnectCalendarCard(connectService: String?, onConnect: () -> Unit, modifier)`; `SyncStatusUi(…, reconnect: Connection? = null)`; `WeekView(…, onReconnect: () -> Unit = {})`; `WeekViewHost(repo, editor, today, nowMillis, onReconnect: (Connection) -> Unit)`
  - `internal fun AddButton(text: String, tag: String, onClick: () -> Unit)` (Components.kt; was `WeekView`'s private `AddEventButton`)
  - `ScriptedProvider.connectsAs: Connection?`; `internal fun stubConnections(store: CalendarStore): CalendarConnections` (StubEditor.kt)
  - `:app`: `SettingsPlaceholder(onExitKiosk, onClose, sections: @Composable () -> Unit = {})`; `suspend fun removeSampleWhenReplaced(calendar: CalendarSetup)` (debug; a no-op in release)

- [ ] **Step 1: Write the failing tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedProvider.kt`:
1. Add `import androidx.compose.runtime.LaunchedEffect`.
2. After `var events: … = { emptyList() }` add:
```kotlin
    /** When set, the connect screen reports this at once; otherwise it waits, like a person still choosing. */
    var connectsAs: Connection? = null
```
3. Replace the empty `ConnectScreen` with:
```kotlin
    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        val answer = connectsAs
        LaunchedEffect(answer) { answer?.let(onConnected) }
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt` (its imports already cover these), add at the end of the file:
```kotlin
/** Connections that nobody may make, for tests that only need the capability's screens to compose. */
internal fun stubConnections(store: CalendarStore): CalendarConnections = CalendarConnections(
    store,
    CalendarSetup(store, emptySet(), { emptyList() }, RecordingToaster(), WallClock { 0L }, EmptyCoroutineContext),
    NobodyMay,
    emptySet(),
    CoroutineScope(Dispatchers.Unconfined),
)
```

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CalendarConnectHostTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.EmptyCoroutineContext
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
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.PROVIDER_TIMEOUT_MS
import uk.co.siland.culvery.capability.calendar.ScriptedProvider
import uk.co.siland.culvery.capability.calendar.TestAccess
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.testAccess
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The connecting card over real access rules, setup and store; PIN pads are answered from a queue. */
@RunWith(AndroidJUnit4::class)
class CalendarConnectHostTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var access: TestAccess
    private lateinit var connections: CalendarConnections

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val overlay = RecordingOverlay()
    private val google = ScriptedProvider(
        "calendar.google",
        sourceList = listOf(CalendarSource("family@example.com", "Family", writable = true, primary = true)),
        features = setOf(Feature.READ, Feature.WRITE),
        displayName = "Google Calendar",
    )
    private val googleConnection = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))

    @Before
    fun setUp() = runBlocking {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        val household = HouseholdRepository(householdDb)
        val clock = WallClock { System.currentTimeMillis() }
        access = testAccess(household, scope, clock, scope)
        val setup = CalendarSetup(store, setOf(google), { household.people.first() }, access.toasts, clock, EmptyCoroutineContext, PROVIDER_TIMEOUT_MS)
        connections = CalendarConnections(store, setup, access.control, setOf(google), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        calendar.close()
        householdDb.close()
    }

    private fun show(existing: Connection?) {
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay) {
                CulveryTheme(dark = true) { Box { overlay.content?.invoke() } }
            }
        }
        compose.runOnIdle { overlay.showConnect(ConnectRequest(google, existing), connections) }
    }

    @Test
    fun anAdminConnectsAndTheCalendarIsSetUpWithDefaults() {
        google.connectsAs = googleConnection
        access.answer(TestAccess.ALEX)
        show(existing = null)
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Google Calendar connected")
        assertThat(overlay.dismissed).isEqualTo(1)
        runBlocking { assertThat(store.master().first()?.source?.id).isEqualTo("family@example.com") }
    }

    @Test
    fun anAdultCannotConnect() {
        google.connectsAs = googleConnection
        // Sam's PIN is refused in the pad, which asks again; then Cancel.
        access.answer(TestAccess.SAM, null)
        show(existing = null)
        compose.waitUntil(5_000) { overlay.dismissed == 1 }
        runBlocking { assertThat(store.connectionsNow()).isEmpty() }
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun cancelClosesTheCardWithNothingSaid() {
        access.answer(TestAccess.ALEX)
        show(existing = null)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Connecting to Google Calendar…").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("connecting_cancel").performClick()
        compose.waitUntil(5_000) { overlay.dismissed == 1 }
        assertThat(access.toasts.messages).isEmpty()
        runBlocking { assertThat(store.connectionsNow()).isEmpty() }
    }

    @Test
    fun aReconnectRecordsTheSameConnectionHealthyAgain() {
        runBlocking {
            store.addConnection(googleConnection, google.sourceList, emptyMap())
            store.setHealth("g1", ConnectionHealth.NeedsSignIn, 0L)
        }
        google.connectsAs = googleConnection
        access.answer(TestAccess.ALEX)
        show(existing = googleConnection)
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Google Calendar reconnected")
        runBlocking { assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Ok) }
    }
}
```

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CalendarSettingsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

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
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class CalendarSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L
    private val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val googleDescriptor = ProviderDescriptor("calendar.google", "Google Calendar", "calendar_month", setOf(Feature.READ, Feature.WRITE))

    private fun row(health: ConnectionHealth, id: String = "g1") =
        CalendarRow(google.copy(id = id), "Google Calendar", "calendar_month", health, lastSyncMillis = now - 5 * 60_000)

    private fun show(
        rows: List<CalendarRow>,
        connectable: List<ProviderDescriptor> = emptyList(),
        onReconnect: (Connection) -> Unit = {},
        onConnect: (ProviderDescriptor) -> Unit = {},
    ) = compose.setContent { CulveryTheme(dark = true) { CalendarSettings(rows, connectable, now, onReconnect, onConnect) } }

    @Test
    fun aHealthyConnectionNamesItsAccountAndWhenItSynced() {
        show(listOf(row(ConnectionHealth.Ok)))
        compose.onNodeWithText("Calendars").assertExists()
        compose.onNodeWithText("Google Calendar · family@example.com").assertExists()
        compose.onNodeWithText("Synced 5 min ago").assertExists()
        compose.onNodeWithTag("settings_reconnect").assertDoesNotExist()
    }

    @Test
    fun eachHealthReadsInWords() {
        show(listOf(row(ConnectionHealth.Unreachable, "a"), row(ConnectionHealth.Error("quota"), "b"), row(ConnectionHealth.NeedsSignIn, "c")))
        compose.onNodeWithText("Can't reach Google Calendar").assertExists()
        compose.onNodeWithText("Something went wrong").assertExists()
        compose.onNodeWithText("Needs reconnecting").assertExists()
    }

    @Test
    fun aConnectionNeedingSignInOffersReconnect() {
        var reconnected: Connection? = null
        show(listOf(row(ConnectionHealth.NeedsSignIn)), onReconnect = { reconnected = it })
        compose.onNodeWithTag("settings_reconnect").performClick()
        assertThat(reconnected).isEqualTo(google)
    }

    @Test
    fun aProviderNotYetConnectedIsOffered() {
        var connected: ProviderDescriptor? = null
        show(emptyList(), listOf(googleDescriptor), onConnect = { connected = it })
        compose.onNodeWithText("Connect Google Calendar").performClick()
        assertThat(connected).isEqualTo(googleDescriptor)
    }
}
```

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/ConnectScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens

/** The canvas the connecting card sits on, and the width Settings' content has there. */
private val CANVAS_W = 1280.dp
private val CANVAS_H = 800.dp
private val SETTINGS_W = 700.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L
    private val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val googleDescriptor = ProviderDescriptor("calendar.google", "Google Calendar", "calendar_month", setOf(Feature.READ, Feature.WRITE))

    private fun row(health: ConnectionHealth) = CalendarRow(google, "Google Calendar", "calendar_month", health, now - 2 * 60_000)

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { CulveryTheme(dark = dark) { content() } }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun connecting(name: String, dark: Boolean) = snap(name, dark) {
        Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H).background(Culvery.colors.bg)) {
            Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
            ConnectingCard("Google Calendar", onCancel = {})
        }
    }

    private fun settings(name: String, dark: Boolean, rows: List<CalendarRow>, connectable: List<ProviderDescriptor> = emptyList()) = snap(name, dark) {
        Box(Modifier.testTag("shot").size(SETTINGS_W, CANVAS_H / 2).background(Culvery.colors.bg).padding(16.dp)) {
            CalendarSettings(rows, connectable, now, onReconnect = {}, onConnect = {})
        }
    }

    @Test fun connectingDark() = connecting("connecting_dark", true)
    @Test fun connectingLight() = connecting("connecting_light", false)
    @Test fun settingsDark() = settings("settings_calendars_dark", true, listOf(row(ConnectionHealth.Ok)))
    @Test fun settingsLight() = settings("settings_calendars_light", false, listOf(row(ConnectionHealth.Ok)))
    @Test fun settingsReconnectDark() = settings("settings_calendars_reconnect_dark", true, listOf(row(ConnectionHealth.NeedsSignIn)))
    @Test fun settingsReconnectLight() = settings("settings_calendars_reconnect_light", false, listOf(row(ConnectionHealth.NeedsSignIn)))
    @Test fun settingsConnectDark() = settings("settings_calendars_connect_dark", true, emptyList(), listOf(googleDescriptor))
}
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt`, replace `connectCardOpensSettings` with:
```kotlin
    @Test
    fun connectCardOpensSettingsWhenNothingCanBeConnected() {
        show { ConnectCalendarCard(connectService = null, onConnect = {}) }
        compose.onNodeWithText("Connect a calendar").assertExists()
        compose.onNodeWithText("Connect your family's calendar to see it here.").assertExists()
        compose.onNodeWithText("Open settings").performClick()
        assertThat(navigator.settingsOpened).isEqualTo(1)
    }

    @Test
    fun connectCardOffersGoogle() {
        var connects = 0
        show { ConnectCalendarCard(connectService = "Google Calendar", onConnect = { connects++ }) }
        compose.onNodeWithText("Connect Google Calendar").performClick()
        assertThat(connects).isEqualTo(1)
        assertThat(navigator.settingsOpened).isEqualTo(0)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekViewTest.kt`, replace `reconnectChipOpensSettings` with:
```kotlin
    @Test
    fun theReconnectChipRunsTheReconnect() {
        var reconnects = 0
        show { WeekView(state(sync(agoMinutes = 0, needsSignIn = listOf("Google"))), onReconnect = { reconnects++ }) }
        compose.onNodeWithText("Google needs reconnecting").assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("Google needs reconnecting").performClick()
        assertThat(reconnects).isEqualTo(1)
        assertThat(navigator.settingsOpened).isEqualTo(0)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardScreenshotTest.kt`, replace both `{ ConnectCalendarCard() }` with `{ ConnectCalendarCard(connectService = "Google Calendar", onConnect = {}) }`.

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/OpenEventTest.kt` (five calls) and `ui/CardHostsMidnightRolloverTest.kt` (one), add `onReconnect = {}` as the last argument of every `WeekViewHost(…)` call, e.g. `WeekViewHost(repo, editor, today, nowMillis = 0L, onReconnect = {})`.

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarCapabilityTest.kt`, replace `WallClock { 0L }, stubEditor(store, zone),` with `WallClock { 0L }, stubEditor(store, zone), stubConnections(store),`.

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt`, in `syncStatusUsesTheStalestConnectionAndListsThoseNeedingSignIn`, after `assertThat(status.needsSignIn).containsExactly("Google")` add:
```kotlin
        // The chip reconnects the first connection that needs it.
        assertThat(status.reconnect?.id).isEqualTo("c1")
```

In `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt`:
1. Add the imports:
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
```
2. Add at the end of the class:
```kotlin
    @Test
    fun aRealConnectionRemovesTheSampleWithEverythingItHeld() = runTest {
        seed()
        store.addConnection(Connection("g1", "calendar.google", "Google", emptyMap()), emptyList(), emptyMap())
        val watcher = launch { removeSampleWhenReplaced(setup) }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (store.connectionsNow().any { it.connection.id == "debug-sample" }) delay(10) }
        }
        watcher.cancel()
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
        assertThat(store.sources().first()).isEmpty()
    }

    @Test
    fun aStartAfterGoogleIsConnectedAddsNoSample() = runTest {
        store.addConnection(Connection("g1", "calendar.google", "Google", emptyMap()), emptyList(), emptyMap())
        seed()
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`
Expected: compilation FAILS: `CalendarConnections`, `CalendarRow`, `ConnectRequest`, `showConnect`, `CalendarSettings`, `ConnectingCard`, `stubConnections`, `removeSampleWhenReplaced`, `SyncStatusUi.reconnect` and the new parameters are unresolved.

- [ ] **Step 3: The capability's Settings hook**

In `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Capability.kt`, after `fun TabContent()` add:
```kotlin

    /** This capability's block in Settings, if it has one (3a design §4.1: the calendar's connections). */
    @Composable
    fun SettingsSection() {
    }
```
If the Compose compiler rejects the default body, make it abstract and add `@Composable override fun SettingsSection() {}` to each capability in `app/src/test/java/uk/co/siland/culvery/shell/Fakes.kt`.

- [ ] **Step 4: Connections, the connecting card and Settings' block**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarConnections.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/** A connection as Settings lists it (3a design §4.1): the service's name and icon, and its health. */
data class CalendarRow(
    val connection: Connection,
    val service: String,
    val icon: String,
    val health: ConnectionHealth,
    val lastSyncMillis: Long?,
)

/**
 * Connecting and reconnecting (3a design §3.3), for the Connect card, Settings and the reconnect chip: which providers
 * can be connected, the Admin check, and finishing what a provider's screen connected.
 */
@Singleton
class CalendarConnections @Inject constructor(
    private val store: CalendarStore,
    private val setup: CalendarSetup,
    private val access: AccessControl,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Offered as "Connect {name}": providers a household may connect (not the debug sample) and hasn't yet. */
    val connectable: Flow<List<CalendarProvider>> = store.connections().map { stored ->
        providers
            .filter { p -> p.descriptor.userConnectable && stored.none { it.connection.providerId == p.descriptor.id } }
            .sortedBy { it.descriptor.displayName }
    }

    val rows: Flow<List<CalendarRow>> = store.connections().map { stored ->
        stored.map { s ->
            val descriptor = provider(s.connection.providerId)?.descriptor
            CalendarRow(s.connection, descriptor?.displayName ?: s.connection.label, descriptor?.icon ?: DEFAULT_ICON, s.health, s.lastSyncMillis)
        }
    }

    fun provider(providerId: String): CalendarProvider? = providers.firstOrNull { it.descriptor.id == providerId }

    /** settings.manage: an Admin, and one already signed in isn't asked again (3a design §6). */
    suspend fun mayConnect(): Boolean = access.authorise(CorePermissions.SETTINGS_MANAGE) != null

    /** Stores what the provider's screen connected, on the application scope, so closing the card can't cancel it. */
    fun finish(connection: Connection, reconnecting: Boolean) {
        scope.launch { if (reconnecting) setup.reconnect(connection) else setup.connectWithDefaults(connection) }
    }

    private companion object {
        const val DEFAULT_ICON = "calendar_month"
    }
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Pickers.kt`, make `PickerCard` and `PickerButton` `internal` (replace `private fun PickerCard(` with `internal fun PickerCard(` and `private fun PickerButton(` with `internal fun PickerButton(`): the connecting card is the same card.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`:
1. In `CalendarType`, after `val addEventButton = HhType.buttonLabel` add:
```kotlin

    /** 22 sp / 700: "Connecting to Google Calendar…" and Settings' "Calendars" (3a design §4). */
    val connectingTitle = HhType.sectionTitle
    val settingsTitle = HhType.sectionTitle

    /** 15 sp / 400: the connecting card's line and a Settings row's health. */
    val connectingBody = subtitle
    val settingsStatus = subtitle

    /** 18 sp / 600: a Settings row's "Google Calendar · {account}". */
    val settingsRowTitle = HhType.rowTitle.copy(fontSize = 18.sp)
```
2. In `CalendarDimens`, before `/** Chip tint: …` add:
```kotlin
    // Connecting card (3a design §4.4): the pickers' card (radius 30, padding 26) 440 wide, a 32 dp icon; 14 between
    // blocks and 4 between its two lines (not in the spec: the date picker's spacing); Cancel as the date picker's.
    val connectingWidth = 440.dp
    val connectingGap = 14.dp
    val connectingTextGap = 4.dp
    val connectingIcon = 32.dp

    // Settings' Calendars block (3a design §4.1): a row `surf`, radius 18, padding 16×20; pills 48 dp, radius 24,
    // padding 0 20. Not in the spec: 12 below the title, rows 10 apart, a 26 dp icon 16 from the text, the health 2
    // below the name, and rows 600 wide (the sheets' width) so a row reads as one line.
    val settingsTitleGap = 12.dp
    val settingsRowGap = 10.dp
    val settingsRowWidth = 600.dp
    val settingsRowRadius = 18.dp
    val settingsRowPaddingV = 16.dp
    val settingsRowPaddingH = 20.dp
    val settingsIcon = 26.dp
    val settingsIconGap = 16.dp
    val settingsStatusTop = 2.dp
    val settingsPillHeight = 48.dp
    val settingsPillRadius = 24.dp
    val settingsPillPaddingH = 20.dp
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Components.kt`, add the imports `androidx.compose.foundation.layout.padding` (if missing) and add at the end:
```kotlin

/**
 * An `accent` pill with an `add` icon: hand-off §7's Add event, and Settings' Connect (3a design §4.1). 48 dp,
 * radius 24, padding 0 20 0 14, a 24 dp icon 6 from its 15 sp / 700 label.
 */
@Composable
internal fun AddButton(text: String, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.addEventIconGap),
        modifier = Modifier
            .testTag(tag)
            .height(CalendarDimens.addEventHeight)
            .clip(RoundedCornerShape(CalendarDimens.addEventRadius))
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(start = CalendarDimens.addEventPaddingStart, end = CalendarDimens.addEventPaddingEnd),
    ) {
        HhIcon("add", size = CalendarDimens.addEventIcon, tint = c.accentInk)
        Text(text, style = CalendarType.addEventButton, color = c.accentInk, maxLines = 1)
    }
}
```
In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt`, replace `if (onAdd != null) AddEventButton { onAdd(state.today) }` with `if (onAdd != null) AddButton("Add event", "week_add_event") { onAdd(state.today) }`, and delete the private `AddEventButton`.

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarConnectHost.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon

/** 3a design §4.4. */
internal fun connectingTitle(service: String): String = "Connecting to $service…"

internal const val CONNECTING_LINE = "Choose the family's Google account and allow access."

/** What the connecting card is for: a new connection to [provider], or reconnecting [existing]. */
internal data class ConnectRequest(val provider: CalendarProvider, val existing: Connection?)

internal fun OverlayHost.showConnect(request: ConnectRequest, connections: CalendarConnections): Unit = show {
    CalendarConnectHost(request, connections, onClose = { dismiss() })
}

/** Opens the connecting card for a new connection or a reconnect (3a design §3.3). */
internal class Connector(private val overlay: OverlayHost, private val connections: CalendarConnections) {
    fun connect(providerId: String) {
        connections.provider(providerId)?.let { overlay.showConnect(ConnectRequest(it, existing = null), connections) }
    }

    fun reconnect(connection: Connection) {
        connections.provider(connection.providerId)?.let { overlay.showConnect(ConnectRequest(it, existing = connection), connections) }
    }
}

@Composable
internal fun rememberConnector(connections: CalendarConnections): Connector {
    val overlay = LocalOverlayHost.current
    return remember(overlay, connections) { Connector(overlay, connections) }
}

/**
 * The connecting card (3a design §3.3): an Admin check (settings.manage), then the card with the provider's connect
 * screen inside it, which runs the system's account chooser and consent over it. A connection is finished on the
 * application scope; Cancel, or backing out of the system screens, closes with nothing said.
 */
@Composable
internal fun CalendarConnectHost(request: ConnectRequest, connections: CalendarConnections, onClose: () -> Unit) {
    val allowed: Boolean? by produceState<Boolean?>(null, request) { value = connections.mayConnect() }
    when (allowed) {
        // The PIN pad is up.
        null -> Unit
        false -> LaunchedEffect(request) { onClose() }
        true -> ConnectingCard(request.provider.descriptor.displayName, onCancel = onClose) {
            request.provider.ConnectScreen(
                existing = request.existing,
                onConnected = { connection ->
                    connections.finish(connection, reconnecting = request.existing != null)
                    onClose()
                },
                onCancel = onClose,
            )
        }
    }
}

/** 3a design §4.4: centred over the scrim; [content] is the provider's connect screen. */
@Composable
internal fun ConnectingCard(service: String, onCancel: () -> Unit, content: @Composable () -> Unit = {}) {
    val c = Culvery.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PickerCard(CalendarDimens.connectingWidth, CalendarDimens.connectingGap, "connecting_card") {
            HhIcon("calendar_month", size = CalendarDimens.connectingIcon, tint = c.accent)
            Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.connectingTextGap)) {
                Text(connectingTitle(service), style = CalendarType.connectingTitle, color = c.ink)
                Text(CONNECTING_LINE, style = CalendarType.connectingBody, color = c.mute)
            }
            content()
            PickerButton(
                "Cancel", primary = false, tag = "connecting_cancel",
                height = CalendarDimens.pickerCancelHeight, radius = CalendarDimens.pickerCancelRadius,
                modifier = Modifier.fillMaxWidth(), onClick = onCancel,
            )
        }
    }
}
```

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarSettings.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.syncedLabel
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon

/** 3a design §4.1. */
internal const val NEEDS_RECONNECTING = "Needs reconnecting"
internal const val SOMETHING_WENT_WRONG = "Something went wrong"

/** A row's health in words: "Synced 5 min ago", "Can't reach Google Calendar", "Needs reconnecting" or "Something went wrong". */
internal fun healthWords(row: CalendarRow, nowMillis: Long): String = when (row.health) {
    ConnectionHealth.Ok -> syncedLabel(row.lastSyncMillis, nowMillis).replaceFirstChar { it.uppercase() }
    ConnectionHealth.Unreachable -> "Can't reach ${row.service}"
    ConnectionHealth.NeedsSignIn -> NEEDS_RECONNECTING
    is ConnectionHealth.Error -> SOMETHING_WENT_WRONG
}

@Composable
internal fun CalendarSettingsHost(connections: CalendarConnections, clock: WallClock) {
    val rows by connections.rows.collectAsState(initial = emptyList())
    val connectable by connections.connectable.collectAsState(initial = emptyList())
    val connector = rememberConnector(connections)
    CalendarSettings(
        rows = rows,
        connectable = connectable.map { it.descriptor },
        nowMillis = rememberNowMillis(clock),
        onReconnect = connector::reconnect,
        onConnect = { connector.connect(it.id) },
    )
}

/**
 * Settings' Calendars block (3a design §4.1): a row per connection with its health, Reconnect when it needs signing in,
 * and Connect for each provider that can be connected. No disconnect, mapping or master controls (Plan 4).
 */
@Composable
internal fun CalendarSettings(
    rows: List<CalendarRow>,
    connectable: List<ProviderDescriptor>,
    nowMillis: Long,
    onReconnect: (Connection) -> Unit,
    onConnect: (ProviderDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    Column(modifier.testTag("settings_calendars")) {
        Text("Calendars", style = CalendarType.settingsTitle, color = c.ink)
        Spacer(Modifier.height(CalendarDimens.settingsTitleGap))
        Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.settingsRowGap)) {
            rows.forEach { row -> ConnectionRow(row, nowMillis, onReconnect) }
            connectable.forEach { d -> AddButton("Connect ${d.displayName}", "settings_connect_${d.id}") { onConnect(d) } }
        }
    }
}

@Composable
private fun ConnectionRow(row: CalendarRow, nowMillis: Long, onReconnect: (Connection) -> Unit) {
    val c = Culvery.colors
    val needsReconnect = row.health == ConnectionHealth.NeedsSignIn
    val account = row.connection.config[CONFIG_ACCOUNT]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.settingsIconGap),
        modifier = Modifier
            .testTag("settings_row_${row.connection.id}")
            .width(CalendarDimens.settingsRowWidth)
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
            Text(healthWords(row, nowMillis), style = CalendarType.settingsStatus, color = if (needsReconnect) c.danger else c.mute, maxLines = 1)
        }
        if (needsReconnect) ReconnectButton { onReconnect(row.connection) }
    }
}

/** 3a design §4.1: 48 dp, `accent`. */
@Composable
private fun ReconnectButton(onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("settings_reconnect")
            .height(CalendarDimens.settingsPillHeight)
            .clip(RoundedCornerShape(CalendarDimens.settingsPillRadius))
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.settingsPillPaddingH),
    ) {
        Text("Reconnect", style = CalendarType.addEventButton, color = c.accentInk, maxLines = 1)
    }
}
```
`rememberNowMillis(clock)` is the existing one in `ui/Now.kt` (it ticks every 30 s).

- [ ] **Step 5: The Connect card, the chip, and the capability**

Replace the body of `ConnectCalendarCard` in `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ConnectCalendarCard.kt` (and its KDoc and signature) with:
```kotlin
/**
 * Takes the Today slot until a calendar is connected (spec §9.2), laid out like the hand-off's Holiday tile. With a
 * provider the household can connect, its button connects it (3a design §4.2); otherwise it opens Settings.
 */
@Composable
fun ConnectCalendarCard(connectService: String?, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(
        modifier = modifier.fillMaxSize().testTag("calendar_connect"),
        radius = CalendarDimens.cardRadius,
        padding = PaddingValues(horizontal = CalendarDimens.connectPaddingH, vertical = CalendarDimens.connectPaddingV),
    ) {
        HhIcon("calendar_add_on", size = CalendarDimens.connectIconSize, tint = c.accent)
        Spacer(Modifier.weight(1f))
        Text("Connect a calendar", style = HhType.cardTitle, color = c.ink)
        Spacer(Modifier.height(CalendarDimens.connectSubtitleTop))
        Text("Connect your family's calendar to see it here.", style = HhType.secondary, color = c.mute)
        Spacer(Modifier.height(CalendarDimens.connectButtonTop))
        if (connectService != null) {
            HhPillButton("Connect $connectService", onClick = onConnect, primary = true)
        } else {
            HhPillButton("Open settings", onClick = navigator::openSettings, primary = true)
        }
    }
}

/** The Connect card with the first provider the household can connect. */
@Composable
internal fun ConnectCardHost(connections: CalendarConnections) {
    val connectable by connections.connectable.collectAsState(initial = emptyList())
    val connector = rememberConnector(connections)
    val first = connectable.firstOrNull()
    ConnectCalendarCard(first?.descriptor?.displayName, onConnect = { first?.let { connector.connect(it.descriptor.id) } })
}
```
and add the imports `androidx.compose.runtime.collectAsState`, `androidx.compose.runtime.getValue` and `uk.co.siland.culvery.capability.calendar.CalendarConnections`.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`, add `import uk.co.siland.culvery.core.plugin.Connection` and in `SyncStatusUi`, after `val failingBeforeFirstSync: Boolean,` add:
```kotlin
    /** The first connection that needs signing in again: what the reconnect chip reconnects (3a design §4.3). */
    val reconnect: Connection? = null,
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarRepository.kt`, in `syncStatus`, after `failingBeforeFirstSync = …,` add:
```kotlin
            reconnect = connections.firstOrNull { it.health == ConnectionHealth.NeedsSignIn }?.connection,
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt`:
1. Add the parameter `onReconnect: () -> Unit = {},` after `onAdd: ((LocalDate) -> Unit)? = null,`, and in the KDoc add `The reconnect chip runs [onReconnect] (3a design §4.3).`
2. Replace `ReconnectChip(reconnectLabel(state.sync.needsSignIn), onClick = navigator::openSettings)` with `ReconnectChip(reconnectLabel(state.sync.needsSignIn), onClick = onReconnect)`, and delete `val navigator = LocalShellNavigator.current` and its import.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CardHosts.kt`:
1. Add `import uk.co.siland.culvery.core.plugin.Connection`.
2. Replace `WeekViewHost` with:
```kotlin
/**
 * Today plus six days; [today] moves at midnight, so the week rolls with it. Shows nothing until both flows load.
 * Add event and the column taps show only when there is a writable master calendar to add to. The reconnect chip
 * reconnects the first connection that needs it ([onReconnect]).
 */
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
    val week: WeekUi? by remember(today) { repo.week(today) }.collectAsState(initial = null)
    val sync: SyncStatusUi? by repo.syncStatus.collectAsState(initial = null)
    val w = week ?: return
    val s = sync ?: return
    WeekView(WeekViewState(w, today, s, nowMillis), onOpen = open, onAdd = add, onReconnect = { s.reconnect?.let(onReconnect) })
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`:
1. Add the imports:
```kotlin
import uk.co.siland.culvery.capability.calendar.ui.CalendarSettingsHost
import uk.co.siland.culvery.capability.calendar.ui.ConnectCardHost
import uk.co.siland.culvery.capability.calendar.ui.rememberConnector
```
and remove `import uk.co.siland.culvery.capability.calendar.ui.ConnectCalendarCard`.
2. Add the constructor parameter `private val connections: CalendarConnections,` after `private val editor: CalendarEditor,`.
3. Replace `{ ConnectCalendarCard() }` with `{ ConnectCardHost(connections) }`.
4. Replace `TabContent` with:
```kotlin
    @Composable
    override fun TabContent() {
        val now = rememberNowMillis(clock)
        val connector = rememberConnector(connections)
        WeekViewHost(repo, editor, today = todayIn(rememberZoneId(zone), now), nowMillis = now, onReconnect = connector::reconnect)
    }

    @Composable
    override fun SettingsSection() {
        CalendarSettingsHost(connections, clock)
    }
```

- [ ] **Step 6: Run the capability's tests**

Run: `./gradlew :core:plugin:testDebugUnitTest :capability:calendar:testDebugUnitTest`
Expected: PASS except the new and changed screenshots, which have no baselines yet.

- [ ] **Step 7: Record the screenshots and look at them**

Run: `./gradlew :capability:calendar:recordRoborazziDebug --tests "*ConnectScreenshotTest*" --tests "*CardScreenshotTest*"`
Open and check:
- `connecting_{dark,light}`: a 440 dp `surf` card centred over the scrim, `calendar_month` in `accent`, "Connecting to Google Calendar…", "Choose the family's Google account and allow access." in `mute`, a full-width Cancel;
- `settings_calendars_{dark,light}`: "Calendars", one row "Google Calendar · family@example.com" with "Synced 2 min ago", no Reconnect;
- `settings_calendars_reconnect_{dark,light}`: "Needs reconnecting" in `danger` and an `accent` **Reconnect** pill on the right;
- `settings_calendars_connect_dark`: the **Connect Google Calendar** pill with `add`, like Add event;
- `connect_{dark,light}`: the new body line and **Connect Google Calendar**;
- `git status` shows no other changed baselines (the week's Add event is the same pill as before).

- [ ] **Step 8: Wire `:app`**

In `app/build.gradle.kts`, replace
```kotlin
    // Sample data only; release builds have no calendar provider until Plan 3.
    debugImplementation(project(":provider:calendar-fake"))
```
with
```kotlin
    implementation(project(":provider:calendar-google"))
    // Sample data only, in debug builds.
    debugImplementation(project(":provider:calendar-fake"))
```

In `app/src/main/java/uk/co/siland/culvery/shell/ui/SettingsPlaceholder.kt`, replace the function with:
```kotlin
/** Replaced by real Settings screens in Plan 4. [sections] are the capabilities' blocks (3a: the calendar's connections). */
@Composable
fun SettingsPlaceholder(onExitKiosk: () -> Unit, onClose: () -> Unit, sections: @Composable () -> Unit = {}) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings")
            .background(c.bg)
            // Swallow taps on empty space so they don't reach the rail underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(48.dp),
    ) {
        Text("Settings", style = HhType.screenTitle, color = c.ink)
        Text("Household, people and connections arrive in a later update.", style = HhType.body, color = c.mute)
        sections()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HhPillButton("Exit kiosk", onExitKiosk)
            HhPillButton("Close", onClose, primary = true)
        }
    }
}
```
(The placeholder's own numbers were already inline in Plan 1 and it is replaced in Plan 4; this plan adds none.)

In `app/src/main/java/uk/co/siland/culvery/MainActivity.kt`, replace
```kotlin
                            SettingsPlaceholder(onExitKiosk = shell::exitKiosk, onClose = shell::closeSettings)
```
with
```kotlin
                            SettingsPlaceholder(onExitKiosk = shell::exitKiosk, onClose = shell::closeSettings) {
                                capabilities.sortedBy { it.order }.forEach { it.SettingsSection() }
                            }
```

In `app/src/debug/java/uk/co/siland/culvery/DebugSeed.kt`:
1. In `seedDebugData`, replace `if (fake != null && calendar.master() == null) {` with:
```kotlin
    if (fake != null && calendar.master() == null && DEBUG_CONNECTION_ID in calendar.connectionIds().first()) {
```
2. Add at the end of the file:
```kotlin

/**
 * Debug builds only (3a design D5, §3.9): once any other connection exists, the sample connection goes, with its events,
 * sync state and queue, so sample and real events never mix; with a connection, no later start seeds it again.
 */
suspend fun removeSampleWhenReplaced(calendar: CalendarSetup) {
    calendar.connectionIds().collect { ids ->
        if (DEBUG_CONNECTION_ID in ids && ids.any { it != DEBUG_CONNECTION_ID }) calendar.removeConnection(DEBUG_CONNECTION_ID)
    }
}
```

In `app/src/release/java/uk/co/siland/culvery/DebugSeed.kt`, add at the end:
```kotlin

@Suppress("UNUSED_PARAMETER")
suspend fun removeSampleWhenReplaced(calendar: CalendarSetup) = Unit
```

In `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`, after `appScope.launch { seedDebugData(household, pins, calendarSetup, calendarProviders) }` add:
```kotlin
        appScope.launch { removeSampleWhenReplaced(calendarSetup) }
```

- [ ] **Step 9: Run the gate, and build both variants**

Run: `./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. Hilt resolves `GoogleCalendarProvider` (its `GoogleApi`, `Authorizer` and `Toaster`), `CalendarConnections` and `SourceRefresher`. If Hilt names a missing binding, it is one of these; stop and report rather than adding a binding this plan doesn't list.

- [ ] **Step 10: Check the release APK holds no debug code**

Run:
```bash
unzip -p app/build/outputs/apk/release/app-release-unsigned.apk 'classes*.dex' | grep -ac "provider/calendar_fake"
unzip -p app/build/outputs/apk/release/app-release-unsigned.apk 'classes*.dex' | grep -ac "DebugOfflineReceiver"
unzip -p app/build/outputs/apk/release/app-release-unsigned.apk 'classes*.dex' | grep -ac "provider/calendar_google"
```
Expected: `0`, `0`, and a number above `0`. (If the release APK has another name, `ls app/build/outputs/apk/release/`.)

- [ ] **Step 11: Commit**

```bash
git add core/plugin capability/calendar app
git commit -m "Connect and reconnect Google from the Connect card, Settings and the reconnect chip, and drop the sample once a real calendar is connected"
```

---

### Task 14: The walkthrough with the user's Google account, the USER CHECKPOINT, the README, the setup doc, and the follow-ups

**Files:**
- Modify (after the checkpoint): `README.md`
- Modify (after the checkpoint): `docs/setup/google-calendar.md`
- Modify (after the checkpoint): `docs/superpowers/plans/2026-09-23-plan1-followups.md`

**Interfaces:**
- Consumes: everything above; the debug build's sample calendar and `DebugOfflineReceiver` (2b-2).
- Produces: documentation only.

Who does what: the implementer prepares the emulator and checks the install (Step 1–2) and stops. **The controller** runs the walkthrough with the user (Step 3): the controller drives the tablet with `adb` and screenshots; **the user** adds their Google account on the emulator, picks it in the chooser and consents, makes changes and checks on their phone, and revokes access. The emulator is the **already-running API 35 Google Play emulator, `emulator-5554`**; don't start or wipe another.

- [ ] **Step 1: Check the emulator and the debug client**

1. Confirm the emulator is up and is a Google Play image:
   ```bash
   adb devices
   adb -s emulator-5554 shell getprop ro.build.version.sdk
   adb -s emulator-5554 shell pm list packages com.android.vending
   ```
   Expected: `emulator-5554 device`, `35`, and `package:com.android.vending`. If `adb` is not on PATH, use `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb"`. If the emulator isn't running, stop and ask the user to start it (don't create an AVD).
2. Print the debug SHA-1 for the controller to compare with the Cloud project's Android client (`docs/setup/google-calendar.md` §4):
   ```bash
   ./gradlew :app:signingReport | grep -A4 "Variant: debug" | grep SHA1
   ```

- [ ] **Step 2: Install over the existing app (the v3 → v4 migration on a real install)**

```bash
adb -s emulator-5554 shell pm list packages uk.co.siland.culvery
./gradlew :app:installDebug
adb -s emulator-5554 shell am start -n uk.co.siland.culvery/.MainActivity
adb -s emulator-5554 logcat -d | grep -iE "Migration|IllegalStateException|FATAL" | tail -20
```
Expected: if Culvery was installed, Home opens on the sample week with no migration error or crash in the excerpt. If it wasn't, say "migration not exercised on device; covered by `CalendarMigrationTest`".

Take `adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-3a-home.png"`, then **stop and report** Steps 1–2 (and the SHA-1). The implementer does not go on.

- [ ] **Step 3: STOP — the controller runs the walkthrough and the USER CHECKPOINT with the user**

The controller, not the implementer, does this, and does not start Step 4 until the user approves. Take `adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-3a-<n>.png"` at each numbered item and send them with the report. Ask the user before each **USER** action and wait for them.

Before starting, ask the user:
- that the debug SHA-1 printed in Step 1 is registered as an Android client in their Cloud project, and their account is a test user (`docs/setup/google-calendar.md` §3–4);
- **USER:** to add their Google account on the emulator (Settings › Accounts), if it isn't there;
- **USER:** to have, in that account, one calendar named after a household person (e.g. "Mia's swimming", ticked) and one weekly repeating event in the next week, or to create them now on their phone.

A debug build has the sample calendar, so the Connect card never shows there: connecting from Settings is the path to walk (the card's button is covered by `CardsTest` and `CalendarConnectHostTest`).

1. **Connect from Settings.** Tap the rail's Settings, enter `1234` (Alex). Below the text, "Calendars" lists "Sample calendar (debug)" with "Synced … ago", and a **Connect Google Calendar** pill. Tap it: the connecting card ("Connecting to Google Calendar…") shows over the scrim.
   - **USER:** choose the account in the system chooser, continue past the "unverified app" screen if the project is in Testing, and allow calendar access.
   - Then: the card closes, the toast reads "Google Calendar connected", the sample row is gone (D5), and the row reads "Google Calendar · {email}" with "Synced just now" within a minute.
2. **The real calendars.** Home and the Calendar tab show the account's events, the person-named calendar's events in that person's colour, and no sample event. In the logcat, nothing from `CalendarSync` but normal passes:
   ```bash
   adb -s emulator-5554 logcat -d | grep -iE "CalendarSync|GoogleCalendar|SourceRefresher|FATAL" | tail -30
   ```
   No token appears in these lines.
3. **Add, then check on the phone.** Tap Today's **+**, title "Culvery test", Who **Mia**, **Evening**, **Save event**, PIN `1234`. The toast reads "Event added".
   - **USER:** on the phone, check "Culvery test" is there at 18:00 today, in Flamingo (pink).
4. **Edit, and a phone change to an untouched field.**
   1. Take the tablet offline: `adb -s emulator-5554 shell cmd connectivity airplane-mode enable`.
   2. Open "Culvery test", **Edit**, change the title to "Culvery test 2", **Save changes**: "Changes saved"; the chip is syncing.
   3. **USER:** on the phone, move "Culvery test" to 19:00.
   4. Back online: `adb -s emulator-5554 shell cmd connectivity airplane-mode disable`. Within 5 minutes (the queue's backoff), the tablet shows "Culvery test 2" at 19:00.
   5. **USER:** check the phone shows "Culvery test 2" at **19:00** (the phone's time kept, 3a design C3), once.
5. **Assign.** **USER:** on the phone, add "Plumber" today with no other details. On the tablet it shows "Added from a phone". Tap it, **Assign to…**, **Sam**, PIN `1234`.
   - **USER:** check "Plumber" turns Blueberry (blue) on the phone.
6. **A repeating event.** Open the weekly event: the detail sheet says "Edit repeating events in Google Calendar on your phone." and Repeats reads "Every week" (or "Every 2 weeks"…, as the phone set it).
7. **Offline add, delivered once.** Airplane mode on (as in 4.1), add "Offline test" for tomorrow, airplane mode off, wait up to 5 minutes.
   - **USER:** check the phone has exactly one "Offline test".
8. **Delete.** Open "Culvery test 2", **Delete**, **Delete event**: "Event deleted".
   - **USER:** check it's gone from the phone.
9. **Access lapses, and the reconnect.**
   1. **USER:** at myaccount.google.com › Security › Third-party access (or "Your connections to third-party apps"), remove Culvery's access.
   2. On the tablet, force a pass: `adb -s emulator-5554 shell am force-stop uk.co.siland.culvery && adb -s emulator-5554 shell am start -n uk.co.siland.culvery/.MainActivity`. Within a minute the Calendar tab shows "Google needs reconnecting", and Settings' row reads "Needs reconnecting" with **Reconnect**.
   3. Add "Reconnect test" for tomorrow: "Event added", syncing.
   4. Tap the chip, PIN `1234`. **USER:** approve Google's consent again. The toast reads "Google Calendar reconnected", the chip goes, and "Reconnect test" stops syncing within a minute.
   5. **USER:** check "Reconnect test" is on the phone, once.
10. **A newly ticked calendar.** **USER:** in Google Calendar, create (or tick) a calendar named after another household person, e.g. "Sam's gym", with an event this week. Restart the app (as in 9.2): the calendar's events show in Sam's colour.
11. **Kiosk.** Debug builds never lock the task, so whether a release build's lock-task mode lets Play services' chooser appear is untested (spec §9); note it for Plan 4.

Send the user these images (from `capability/calendar/src/test/screenshots/`), dark and light: `connecting_*`, `settings_calendars_*` (Ok, reconnect, connect), `connect_*`, `detail_recurring_*`, `detail_delete_confirm_*`, and the walkthrough screenshots. Name the parts that are the controller's own design, which the spec doesn't specify:
- the connecting card borrows the date picker's card and Cancel (radius 30, padding 26, 14 between blocks);
- Settings' rows are 600 dp wide with a 26 dp provider icon, 12 below "Calendars" and 10 apart;
- a Settings Connect pill looks exactly like the week's **Add event**;
- the debug build's sample events read "Every week" in Repeats (the fake now carries a weekly rule).

Ask: "Do these match what you want? Any changes before I update the README and the setup doc?" Also ask for the outcome of the 2026-10-01 seven-day check on the spike (did the Testing-status grant still work silently after 7 days?) and which publishing status they have chosen.

- **If the user asks for changes:** make them, re-record only the affected images with `--tests`, look at them, run `./gradlew testDebugUnitTest verifyRoborazziDebug`, re-send them, and commit with a message describing the change. Repeat until approved.
- **When approved:** continue to Step 4.

- [ ] **Step 4: Update the README**

In `README.md`:

1. **Build and run.** After the paragraph starting "To see what the tablet does while a calendar can't be reached", add:
```markdown
To connect a real Google account, first set up a Google Cloud project with an Android client for your debug key (`docs/setup/google-calendar.md`), then Settings › Connect Google Calendar (Admin PIN). Connecting removes the sample calendar, with its events and queued changes, for good; to get it back, clear the app's data. Release builds offer Google Calendar only.
```
2. **Modules.** After the `:provider:calendar-fake` row add:
```markdown
| `:provider:calendar-google` | Google Calendar API v3 over OkHttp; sign-in and tokens through Play services, nothing stored |
```
3. **`calendar.db`.** In the paragraph starting "`calendar.db` stores user configuration", after "(the `outbox` table; …)" add: ` A queued change's 48-hour age doesn't count time its connection spent waiting for sign-in, so a lapse over a weekend drops nothing.`
4. **Adding a calendar provider.**
   - In step 2, replace the bullet starting "`sources(conn)`:" with:
```markdown
   - `sources(conn)`: the calendars in this connection, with ids that never change between calls. Set `shown` (ticked in the service) and `primary` (the account's own calendar, at most one): connecting maps every source by name to a person or Family, shows the ticked ones, and makes the primary the master. A daily refresh follows later ticks, additions and removals.
```
   - In step 2's `sync` list, replace "throw `NeedsSignInException` for auth failures and `UnreachableException` for network failures, and nothing else." with "throw `NeedsSignInException` for auth failures and `UnreachableException` for network failures, or `SourceGoneException` (an `UnreachableException`) when the source itself has gone (the app then refreshes the sources), and nothing else. Set `recurrenceRule` to the series' RRULE line when you know it: the detail sheet describes it (\"Every week\").".
   - In step 2's `ConnectScreen` bullet, append: " Put the signed-in account in `config[CONFIG_ACCOUNT]` for Settings to show. Set `ProviderDescriptor.userConnectable = false` for a provider nobody should connect from Settings (the debug sample)."
   - In step 3, replace the bullet starting "`update` changes only the title, the times and the tags" with:
```markdown
   - `update(…, fields)` changes only the given `EventField`s (`TITLE`, `TIMES`, `FOR_PERSON`) and keeps everything else, the `createdBy` tag included (Google: a PATCH of just those keys). The tablet sends only what the person touched, so a change made on a phone to anything else is kept.
   - `find` returns the event as the service holds it, or null when it doesn't exist or was deleted.
   - `EventDraft.forPersonColor` is the person's colour; use it if the service can colour events (Google: the nearest `colorId`).
```
   - In step 3, after the bullet starting "`create` takes a client key:", add:
```markdown
   - A create never recreates a deleted event: when the key belonged to an event since deleted, throw `WriteRejectedException` (Google: the 409's event is cancelled).
   - Every call must return promptly when its caller is cancelled: cancel the network call with it (OkHttp: enqueue inside `suspendCancellableCoroutine` and cancel from `invokeOnCancellation`).
```
   - In step 3's last paragraph, replace "A change still queued after 48 hours is dropped with a toast." with "A change still queued after 48 hours (not counting time the connection waited for sign-in) is dropped with a toast."
   - In step 5's contract example, replace `// WRITE providers only; the seven write checks fail if these are missing.` with `// WRITE providers only; the eleven write checks fail if these are missing.` and after `override fun writableSource() = …` add the line `       override fun gateWrites() = …           // optional: hold the service's replies, for the cancellation check`.
   - In step 5's last line, replace "`:provider:calendar-fake` is a worked example." with "`:provider:calendar-fake` and `:provider:calendar-google` (through a fake Google server on `MockWebServer`) are worked examples."

- [ ] **Step 5: Update the setup doc**

In `docs/setup/google-calendar.md`:
1. In §3, replace item 3 ("**Audience:** **External** … Leave publishing status as **Testing** for now; the spike checks whether that's good enough.") with the text for the user's answer from Step 3:
   - **If they chose In production:**
```markdown
3. **Audience:** **External**, which is needed for personal Gmail accounts. Set the publishing status to **In production**. The app stays unverified, which is fine for a household: the first sign-in shows a "Google hasn't verified this app" screen (choose **Advanced**, then **Go to Culvery (unsafe)**), up to 100 users can sign in, and access doesn't expire. In **Testing**, Google ends access every 7 days, so the tablet would show "Google needs reconnecting" weekly.
```
   - **If they chose to stay in Testing:**
```markdown
3. **Audience:** **External**, which is needed for personal Gmail accounts. Leave the publishing status as **Testing**: only the test users below can sign in, and Google ends access every 7 days, so the tablet shows "Google needs reconnecting" once a week; an Admin taps it and approves again. Nothing queued meanwhile is lost. **In production** (unverified) avoids the weekly reconnect, at the cost of a "Google hasn't verified this app" screen at the first sign-in.
```
   Then add, after the list, a line with the seven-day check's result as the user gave it, e.g. `The seven-day check (2026-10-01) found: …`.
2. Replace §5 ("Emulator note") with:
```markdown
## 5. Connect on the tablet
The emulator (or tablet) needs Google Play services: an image with **Google Play**. Add the Google account under **Settings › Accounts** if it isn't there. Then, in Culvery, **Settings › Connect Google Calendar** (Admin PIN): pick the account, allow calendar access, and the household's calendars appear. Every calendar in the account is added; the account's own calendar becomes the one the tablet adds events to, and a calendar named after one person (e.g. "Mia's swimming") shows in their colour. If access lapses, the Calendar tab shows "Google needs reconnecting": tap it and approve again.

Release builds need a second Android client with the release key's SHA-1 (Plan 4). In release kiosk mode the account chooser may not appear; if so, exit kiosk (Settings › Exit kiosk), connect, and return.
```

- [ ] **Step 6: Check the docs read sensibly**

Read `README.md` and `docs/setup/google-calendar.md` through once: the provider steps still number 1 to 6, the code blocks are balanced, and:
```bash
grep -nE "seven write checks|until Plan 3|the spike checks whether" README.md docs/setup/google-calendar.md
```
Expected: no output.

- [ ] **Step 7: Update the follow-ups**

In `docs/superpowers/plans/2026-09-23-plan1-followups.md`:
1. Under "## From Plan 2a review (deferred)" → **For Plan 3**, delete every bullet except "Cover the ICS empty feed with `fullReplace` and zero-duration events." and "Extend the module guard to JVM-only modules." and rename the heading to **For the ICS provider, or the first JVM-only module** (3a design §8).
2. Under "## From Plan 2a review (deferred)" → **For Plan 4**, delete "`CalendarSetup.connect` must go through the IO + timeout wrapper." (Task 7 did it).
3. Under "## From Plan 2b-1 (deferred)" → **For Plan 3**, delete the whole block (R8, R9, DL1, U3, m2, m3, m4: Tasks 1, 5, 6, 7, 8, 11, 12). Under its **For Plan 4**, delete "`CalendarSetup.setMaster` calls `provider.sources()` without the IO + timeout wrapper. …" (Task 7).
4. Under "## From Plan 2b-2 (deferred)" → **For Plan 3**, delete the whole block (C2, C3, C9, C10, the key reuse, the Google writer, the sheet's load failure, the orphan race, the Delete wording, the `onEdit` default: Tasks 3–6, 11).
5. Under "## For Plan 4 (weather, setup, settings, release)" → "Calendar sources", delete the three sub-bullets and the heading: Task 2 and Task 7 did the pruning, the refresh and the early returns; the foreign keys were replaced by explicit deletes (3a design §3.8).
6. Add at the end of the file:
```markdown
## From Plan 3a (deferred)

**For Plan 4**
- The on-device pass: in a signed release in lock-task mode, check Play services' account chooser and consent screens appear when connecting and reconnecting (3a design §9); if not, exit kiosk around them.
- The release OAuth client (the release key's SHA-1) with release signing.
- The Connect-a-calendar card's Google button was checked by tests only: a debug build always has the sample calendar, so the card never shows on the emulator.
- Settings: disconnecting a connection, and editing mappings, visibility and the master (3a design §10).
```
   and add under it any item you or the user noted during this plan that was deferred rather than fixed.

- [ ] **Step 8: Commit**

```bash
git add README.md docs/setup/google-calendar.md docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Document the Google Calendar provider, connecting it, and the contract's new rules"
```

---

## Spec coverage (3a design → tasks)

| Design | Where |
|---|---|
| §1 scope; D1 ICS deferred; Google in release, the fake debug-only | Tasks 8–13; Task 13 Step 8 (`:app` wiring), Step 10 (release APK) |
| D2 sign-in through `AuthorizationClient`; only the email stored; `NeedsSignIn` from a resolution; §3.2 401 refresh, `clearToken`, the account from `calendars/primary`, a different account refused | Tasks 8 (401), 9 (token source, connect flow), 10 (connect screen) |
| D3 Connect in Settings and on the card, Admin PIN, one Google connection, `userConnectable` | Tasks 1, 13 |
| D4 automatic setup: every calendar, visible = ticked, writable = owner/writer, primary → master and Family, others by name | Tasks 2 (`addConnection(…, master)`), 7 (`defaultMapping`, `connectWithDefaults`), 10 (`sources`) |
| D5 connecting removes the sample; `removeConnection`; removing a source or connection removes its rows | Tasks 2, 7, 13 |
| D6 reconnect from the chip, Admin PIN, health Ok, queue due (m4), sync | Tasks 7, 13 |
| D7 daily source refresh and on start; hide unticked, remove deleted, keep mappings; the master gone or read-only → cleared, one toast | Tasks 2, 7 |
| D8 OkHttp + kotlinx.serialization, MockWebServer fake server, cancellable calls (R9), pinned versions | Tasks 8, 11 |
| D9 Google API behaviour (list, syncToken, 410, paging, insert with the key, 409, PATCH, delete 404/410, tags, `colorId`, cancelled, recurring) | Tasks 10, 11 |
| D10 error mapping (§3.7 table), R8 wording | Tasks 8, 10, 11 |
| D11 the service name in failure, repeating and delete copy; the label on the pill and chip | Task 12 |
| D12 Repeats from the RRULE (§3.5) | Tasks 1, 2, 10, 12 |
| D13 crash-proofing: handler, `Throwable` per source, logging, store calls outside the try, `retryWhen`, m2, C2, orphan race, key reuse, sheet load failure, Delete wording, `onEdit`, counting DAO | Tasks 3, 4 |
| D14 C3 touched fields (form, editor, queue, drain), m3, C9, C10 | Tasks 1, 5, 6, 11 |
| D15 testing | every task; the fake server Tasks 8–11; the walkthrough Task 14 |
| D16 the outbox age clock pauses while sign-in is needed | Tasks 2 (columns, `setHealth`/`markSynced`, `ageMillis`), 6 (the drain; `NeedsSignIn` from a write), 7 (reconnect folds the pause) |
| §3.1 module, `GoogleApi`, `TokenSource`, `:app` in every build type | Tasks 8, 9, 11, 13 |
| §3.3 `CalendarConnectHost`, `connectWithDefaults`, `reconnect`, entry points | Tasks 7, 13 |
| §3.4 `shown`/`primary`, `defaultMapping`, `SourceRefresher`, `SourceGoneException` → refresh | Tasks 1, 7 |
| §3.5 reading, the RRULE cache, "simple" rules | Tasks 10, 12 |
| §3.6 writing: create, 409, update (C3), ASSIGN as `{FOR_PERSON}` and m3, `colorId`, delete, `find`, C9 | Tasks 5, 6, 11 |
| §3.7 `Retry.needsSignIn` sets health at once | Task 6 |
| §3.8 store: `removeConnection`, `refreshSources`, master clearing, `makeDue`, early returns, no foreign keys | Task 2 |
| §3.9 debug builds: the sample removed once another connection exists, never re-seeded | Task 13 |
| §3.10 contract changes, five checks and their fixtures, the fake in line, README | Tasks 1, 14 |
| §3.11 `calendar.db` v4 (the spec's three columns and D16's two), migration test with the table list, `fields`, `forPersonColor` in the draft JSON | Task 2 |
| §3.12 crash-proofing details | Tasks 3, 4 |
| §3.13 publishing status in the setup doc | Task 14 Steps 3, 5 |
| §4.1–§4.4 Settings block, Connect card, reconnect chip, connecting card; §4.5 copy | Tasks 12, 13 |
| §5 errors and offline | Tasks 3, 5, 6, 7, 11; the walkthrough |
| §6 decisions where ambiguous | Tasks 7 (primary, two names), 2 (visibility follows ticks), 13 (Settings Reconnect), 2/6 (D16) |
| §7 testing, Roborazzi, walkthrough | Tasks 1–14 |
| §8 follow-ups | Tasks 1–13; Task 14 Step 7 |
| §9 review focus | Review Focus above |

