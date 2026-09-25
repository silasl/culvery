# Culvery — Plan 2b-2: Add and edit events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** People can add an event from Home or the week view, and edit any editable event from its detail sheet, in the hand-off's quick-add/edit sheet with its date and time pickers and the on-screen keyboard. Saves follow 2b-1's flow (PIN on Save, a 10 s direct attempt, the outbox when offline). Creates are idempotent by a client key, so a lost reply never makes a second event, and an event added offline can be opened, edited and deleted before it syncs.

**Architecture:** `calendar.db` v3 adds a nullable `clientKey` to `outbox`. `CalendarWriter.create` takes that key and must use it as the event's id, so a repeated create returns the first event; the contract suite checks it. A queued create's `EventRef` is built from its key, so the overlay, the detail sheet, the editor and the drain all treat it as the event it will become. `CalendarEditor` gains `create` and `update`, with the permission rules and a re-read of `createdBy` after the PIN pad. `EventForm` is a plain Kotlin state holder with Compose state (defaults, the summary line, the draft in the household zone). `EventEditorSheet` is stateless; `EventEditorHost` runs Save and Delete and swaps overlays with the detail sheet through the existing `OverlayHost`. The sheet and the toast layer pad by `WindowInsets.ime`, with the activity on `adjustResize`.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.x (KSP), Room 2.8.0 + room-testing, Coroutines/Flow, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1.

**Spec:** `docs/superpowers/specs/2026-09-25-culvery-2b2-add-edit-events-design.md` (binding). Parent spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§8, §9.3, §9.4).
**Design reference:** `docs/design/house_hub_handoff/README.md` §7 ("Entry points", "Sheet 2 — Quick-add / edit") and the Theming table; `screenshots/calendar-sheets/10`–`16` (dark and light); `Culvery.dc.html` for the few values the README doesn't list (each is named where it is used).
**Previous plan (format, constraints, review outcome):** `docs/superpowers/plans/2026-09-24-culvery-02b1-change-events.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`.

**Plan series:** 1 Foundation (done) · 2a Calendar read path (done) · 2b-1 Change events (done) · **2b-2 Add and edit (this plan)** · 3 Google and ICS providers · 4 Weather, setup, settings, release.

**Task order and why it differs from the suggested order:**
1. `calendar.db` v3: `clientKey`, `MIGRATION_2_3`, the migration test with the table list, and `PendingChange.clientKey`/`ref`.
2. **The contract change and every writer, in one task.** Adding `clientKey` to `CalendarWriter.create` stops `:provider:calendar-fake`, the testkit's `TinyProvider` and the calendar tests' `ScriptedWriter` compiling until each takes it, and the fake must pass the new contract check at the same gate. So the contract, the suite's check, the self-test fixtures, the drain's one call site and the fake's keyed create land together. The suggested tasks 2 and 3 are merged.
3. Drain: a dropped create takes every change queued for its event with it, in one statement and one toast; the duplicate-create retry; create → edit → delete delivered in order; housekeeping (`SilentToaster` to test sources, no `CalendarSync` defaults).
4. Overlay and repository: a queued create's ref from its key; `event(ref)` and a new `editable(ref)` include a queued create; `masterLabel`, where new events go, through one `writableMaster` rule the editor shares.
5. **Editor:** `create` and `update` in one task (both rewrite `CalendarEditor`): the create rules and wording, the Who rule on edits, a queued create as a target, the `createdBy` re-read after the PIN pad for update and delete, one `attempt` for every kind, and the save toasts. With the suggested tasks 2 and 3 merged, and the editor in one task, the plan has 12 tasks.
6. `EventForm` (pure) with its unit tests.
7. Date and time pickers.
8. `EventEditorSheet` (stateless), with screenshots of hand-off 10–16 and the locked multi-day edit, and the sheet's padding above the keyboard.
9. `EventEditorHost`, the detail ↔ editor swaps, Edit in the detail footer, and the one-action-at-a-time runner both hosts share.
10. Keyboard: `adjustResize` and toasts above the IME.
11. Entry points: Today **+**, **Add event** in the Calendar header, week-column taps and hints.
12. The offline switch for the walkthrough, the end-to-end offline test, the emulator walkthrough, the **USER CHECKPOINT**, then the README and the follow-ups.

**Deviations from the design and extensions (deliberate):**
- **A dropped create takes its queued changes with it whatever dropped it** (refused, 48 hours old, or undeliverable), not only when refused (spec §3.6 names the refusal). In each case the changes behind it have nothing to apply to.
- **The fake gains one switch, `setOffline(Boolean)`.** A debug-only `DebugOfflineReceiver`, reachable only from `adb` (the sender needs `android.permission.DUMP`), toggles it: the spec's walkthrough adds an event "offline (fake unreachable)" on the emulator, and nothing else can make the fake unreachable there. The lost-reply tests use the calendar tests' `ScriptedWriter.loseNextReply`.
- **An edit keeps the event's exact tag unless Who is changed.** This generalises the spec's "an untagged event stays untagged": an event tagged for someone who has left the household also keeps its tag, and the summary then ends at the time, since only the id is stored. A tap on the already-selected Who chip does nothing, so an untagged event is tagged `"family"` only once someone else is chosen and then Family.
- **An edit whose Day, Time and Length are untouched keeps the event's own start and end.** Rebuilding them from the chips would move an event that starts in the autumn's repeated hour (01:30 GMT on 25 October 2026) and make the edit look changed.
- **A timed draft ends at the next midnight at the latest**, so 23:30 + 1 h saves 23:30–00:00. D3 says new events can't span days; an event ending exactly at midnight is one day (§3.1), so it isn't locked when edited.
- **An event's own length is a fourth Length chip only when it is positive.** Provider data with an end at or before its start opens on 1 h.
- **Title limit:** typing past 100 characters is ignored, but shortening is always allowed, so a longer title added on a phone can still be edited down.
- **`CalendarRepository.masterLabel`** (the master's connection label; null when there is nowhere to add) hides the add entry points and names the connection on the failure card. `EditResult.Rejected` keeps its single field, so 2b-1's tests stand.
- **The Title field sits outside the scrolling body**, as the spec says ("the header, Title and footer stay in place"). The prototype scrolls it with the chips.
- **The Who chip's ink is `DarkColors.bg`** (the hand-off's `#0E1011` on a person's colour, in both themes), referenced as a token rather than written as a literal.
- **A tap on a disabled Who chip toasts through `CalendarEditor.refuseOtherWho(name)`.** There is no `LocalToaster` (2b-1 Simp5), and the editor holds the injected `Toaster`.
- **Approved by the user (spec amended):** U1, an edit that changes Who follows the add rule: a signed-in Child's other Who chips are disabled in an edit too, and the editor refuses the change with "{Name} can only add events for themselves." (spec D2, D7, §3.3, §3.7, §4.2, §6). C1, a write the provider accepted counts as done even if storing it on the tablet fails; the next sync stores it (spec D5, §3.3, §3.4, §5). A new key on each Save tap stays.

**Carried forward from 2b-1 (still settled; do not reintroduce):** U1 (the session extends only on an authorised action), U2 (a refusal on the session shortcut toasts, then signs out), U3 (the connection label in place of "Google"; deleting a missing event succeeds). B2: changes to one event are delivered in order and never collapsed (design D6 restates it). Simp1: one `callWriter`, shared by the editor and the drain. Simp5: no `LocalToaster`. R8's fixed wording and R9's `runInterruptible` stay with Plan 3. The 2b-1 follow-ups marked **For Plan 2b-2** are in scope:
- S1, idempotent creates: Tasks 1, 2.
- The duplicate CREATE when `applyAcceptedWrite` throws: Tasks 2, 3.
- The `createdBy` re-read after authorising: Task 5.
- m7, `SilentToaster` to test sources and no `CalendarSync` defaults: Task 3.
- "At v2 → v3, check the driver-based `MigrationTestHelper` catches a dropped table, or assert the table list": Task 1 asserts the list.

Two 2b-1 follow-ups marked **For Plan 3** are done here too, as the review asked: the editor's reads outside the write path's catch (Task 9's shared single-action runner catches them in both hosts), and the "try again" toast after the provider accepted (Task 5: it is `Done`).

**Review outcome (settled; do not reintroduce):**
- Accepted: B1, `: Unit` on `showDetail` and `showEditor` (they call each other).
- Accepted: B2, `import androidx.compose.ui.test.click` in `PickersTest`.
- Modified: C1, one key per Save stays; in the one `attempt`, a store failure after the provider accepted is logged and returns `Done` (`aCreateAcceptedButNotStoredIsDoneAndMakesOneEvent`); the narrower Retry-then-queue-failure case goes to Plan 3 (R3).
- Deferred to Plan 3: C2, the drain's spin on a store failure after an accepted write (2b-1 m2, R3).
- Modified: C3, an edit with Day, Time and Length untouched keeps the original instants (the 25 Oct 01:30 GMT test); merging only touched fields waits for Plan 3's PATCH.
- Modified: C4, a timed draft ends at the next midnight at the latest (23:30 + 1 h is 23:30–00:00 and isn't locked on reopening).
- Accepted: C5, the event's own length is a chip only when positive; otherwise 1 h.
- Modified: C6, the shared `rememberSingleAction(onError)` catches: the editor host shows the "try again" card, the detail host logs.
- Accepted: C7, the failure card's title is at most 2 lines, with an ellipsis.
- Accepted: C8, dropping a create deletes every outbox row for its key in one DAO statement (`dropCreate`), not the pass-start snapshot's followers.
- Deferred to Plan 3: C9, an aged create whose reply was lost, with a DELETE queued behind it.
- Deferred to Plan 3: C10, what a create repeating a deleted event's key does (Google's 409).
- Accepted: C11, `DebugOfflineReceiver` ignores a broadcast without the extra.
- Approved by the user: U1, an edit that changes Who follows the add rule (sheet and editor; spec §6 amended).
- Accepted: U2, the editor's creator re-check signs the person out after its toast.
- Accepted: T1, a host test with a moving clock for "Tomorrow" chosen at 23:30 and saved after midnight.
- Modified: T2, a check that the hosts pass no explicit insets; the real IME and `ShellLayers` go to Plan 4's on-device pass.
- Accepted: T3, the busy-Save test taps again after the writer is entered, with two answers queued, and checks Save is disabled.
- Accepted: T4, `setInTouchMode(true)` in `@Before`, proved as Task 8's first step.
- Accepted: T5, a contract fixture that uses the key as the id but writes a second event.
- Modified: T6, the refused-save test checks the card clears on the next tap; no key check.
- Modified: T7, tests for the UPDATE branch of the closed-sheet toast, a departed tag kept on a title-only edit, nowhere to add at host level, and the scrim closing the editor; the keyboard hiding goes to Plan 4.
- Rejected: T8, the keyboard baselines stay at 480 dp (spec §7).
- Modified: DL1, the summary omits " · name" for a tag with no current person.
- Accepted: DL2, a tap on the already-selected Who chip does nothing.
- Accepted: DL3, only the space below a column's chips and its hint add.
- Rejected: DL4, the add sheet keeps the day its chip showed (spec §3.1).
- Accepted: DL5, `editor_edit_*` uses 2 h as hand-off 13 does; `editor_own_length_*` shows the fourth chip.
- Accepted: DL6, no redundant safe call in the migration test.
- Deferred to Plan 4: I1, Samsung's floating and split keyboards on the on-device pass.
- Rejected: I2, already accepted as 2b-1 U1.
- Accepted: Simp1, the editor's create and update in one task.
- Accepted: Simp2, no `loseNextWriteReply` on the fake.
- Accepted: Simp3, one `attempt(to, kind, remoteId, draft, clientKey)`.
- Accepted: Simp4, one `daySpan` shared by `whenLabel` and `EventForm`.
- Rejected: Simp5, `EditableEvent` and `EventForm.Mode` stay.
- Rejected: Simp6, `write()` keeps its named extras.
- Rejected: Simp7, the host owns which picker is open (D4).
- Modified: Simp8, one `rememberSingleAction(key, onError)` for both hosts, carrying C6.
- Modified: Simp9, no drain test for a keyless v2 CREATE; the migration fixture keeps its CREATE row.
- Accepted: Simp10, `onAdd` only on the four base screenshots (`today_{dark,light}`, `week_{dark,light}`).
- Accepted: Simp11, "nowhere to add" is tested at host level only.
- Modified: Simp12, `RoundButton` and `StepButton` stay apart: they differ in shape as well as size.
- Accepted: Simp13, the sheet's keyboard padding is part of Task 8.
- Accepted: Simp14, `rememberEventAdder` returns null when there is nowhere to add.
- Accepted: Simp15, one `writableMaster` rule for `masterLabel` and the editor.
- Rejected: Simp16, whole-file replacements stay.

## Global Constraints

- Package root `uk.co.siland.culvery`; app name "Culvery".
- `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Pinned versions: AGP 8.13.0, Gradle 8.13, Kotlin 2.2.20, KSP 2.2.20-2.0.3, Compose BOM 2025.09.00, Hilt 2.57.1, Room 2.8.0 (with `room-testing` 2.8.0), Robolectric 4.16, Roborazzi 1.46.1. If a version fails to resolve, take the newest **patch** in the same minor line. Never move to a new major or minor version without asking.
- **Deprecated APIs:** use none without asking the user first. If any API this plan uses shows a deprecation warning in these versions, **stop and ask**. The APIs worth checking are:
  - `BasicTextField(value: String, onValueChange, …)` (Task 8);
  - `KeyboardOptions(capitalization = …, imeAction = …)`, which must resolve to the current constructor and not an `autoCorrect` overload (Task 8);
  - `FlowRow(horizontalArrangement = …, verticalArrangement = …)`, which must need no `@OptIn(ExperimentalLayoutApi::class)` and must not resolve to an overload taking `FlowRowOverflow` (Task 8);
  - `android:windowSoftInputMode="adjustResize"` (Task 10). Its code constant, `WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE`, is deprecated from API 30: set the mode in the manifest only, never in code;
  - `EntryPointAccessors.fromApplication` (Task 12).

  `@OptIn` to an *experimental* API is allowed only where the plan says so: `ExperimentalCoroutinesApi`, in tests and in `CalendarRepository`, as today. Do not use `androidx.security:security-crypto` / `EncryptedSharedPreferences`.
- Design canvas 1280×800 dp. Hand-off §7 values are authoritative. Copy them exactly as the steps give them.
- No shadows; flat colours; no blur.
- No secrets, tokens or household data in source or build config. Sample people and events live only in `app/src/debug` and `:provider:calendar-fake`.
- PINs are exactly 4 ASCII digits.
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on the current branch; never push.
- Module rules (enforced by `build-logic`'s `ModuleBoundaries`):
  - `:core:*` depends only on `:core:*`.
  - `:capability:X…` depends only on `:core:*` and its own family.
  - `:provider:X-…` depends only on `:core:*` and `:capability:X`, plus `:capability:X-testkit` in test configurations.
  - `:app` may depend on anything.
  - This plan adds no module dependency. `:app`'s debug source set already depends on `:provider:calendar-fake`.
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
  - **Layout numbers live in `CalendarDimens`, `ShellTokens` or `PinPadDimens`**, never inline. The fake's numbers are named constants too.
- **Storage / migration policy:** `calendar.db` holds user configuration. Every schema version ships a hand-written Room `Migration` with a `MigrationTestHelper` test. Never use destructive fallback. Schemas are exported to `capability/calendar/schemas/` and committed. **v3 must ship `MIGRATION_2_3`, its `CalendarMigrationTest` case and `schemas/…/3.json`.** `MigrationTestHelper` uses the driver-based constructor with `AndroidSQLiteDriver`, because androidx.sqlite 2.6.x's default driver mis-handles Windows paths; copy the pattern already in `CalendarMigrationTest`.
- Calendar providers and writers must be main-safe and cancellable. The engine runs every provider or writer call on `Dispatchers.IO` under a timeout:
  - sync and drain calls under 60 s (`PROVIDER_TIMEOUT_MS`);
  - the editor's direct attempt under `WRITE_ATTEMPT_MS = 10_000`.

  Every writer call goes through the shared `callWriter`, and every accepted write reaches the mirror through `CalendarStore.applyAcceptedWrite` (both in `Writes.kt`). Reuse them; never write a second path.
- Writers throw only `WriteRejectedException` (a permanent refusal), `NeedsSignInException` or `UnreachableException`. `create` is idempotent by its client key and returns `remoteId == clientKey`.
- Viewing never needs a PIN. Every change calls `AccessControl.authorise` **when tapped**. Buttons are never hidden for permission reasons. (The three add entry points hide only when there is no writable master calendar, because there is nowhere to add to.)
- **Tests and threads:**
  - Room runs on its own threads, and `runCurrent()` doesn't wait for it. A test that holds a write open uses the writers' `entered`/`gate` `CompletableDeferred`s and waits in bounded real time: `withContext(Dispatchers.Default) { withTimeout(5_000) { … } }`.
  - Asynchronous UI outcomes use `compose.waitUntil(5_000) { … }`.
  - A Compose test that needs a tag under a clickable parent (a scrim, a week column) finds it with `useUnmergedTree = true`. Never change production semantics for a test.
- The shell's overlay layers live inside `ShellLayers` (the graphics-layered `Box` in `OverlayLayers.kt`); that is why a removed layer redraws. Don't restructure it.

## Review Focus

1. **The keyboard covers Save, or a toast hides behind the keyboard, in kiosk mode.**
   - With the keyboard up, the header, Title and Save stay above it and the chips scroll; a toast rises above the keyboard.
   - Tests:
     - Task 8 `saveStaysAboveTheKeyboard`
     - Task 10 `theToastSitsAboveTheKeyboard`, and its check that the hosts leave the insets at their defaults
     - Task 12 walkthrough step 8 (the immersive kiosk window, the stock keyboard)
2. **An event added offline is edited or deleted before it syncs.**
   - It opens with the syncing pill under one ref; the edit and the delete queue behind the create and are delivered in order; afterwards nothing remains.
   - Tests:
     - Task 4 `aChangeAndADeleteQueuedBehindACreateShowUnderItsRef`
     - Task 5 `aChangeAndADeleteOfAQueuedCreateQueueBehindItAndDrainInOrder`
     - Task 9 `aQueuedCreateOpensAsSyncingAndItsEditQueuesBehindIt`
     - Task 12 `anEventAddedOfflineCanBeChangedAndDeletedBeforeItSyncsAndNothingRemains` (the sample calendar, end to end)
3. **A child saves an event for Family, with and without a session, or moves one to someone else.**
   - Refused with "{Name} can only add events for themselves."; the sheet stays open with its input; a signed-in child gets no PIN pad and is signed out.
   - Tests:
     - Task 5 `childIsRefusedAddingForFamilyAndIsToldWhy`
     - Task 5 `aSignedInChildIsRefusedAddingForOthersWithoutAPinPadAndIsSignedOut`
     - Task 5 `childCannotMoveTheirOwnEventToSomeoneElseAndIsToldWhy`
     - Task 9 `aChildSavingForFamilyIsToldAndTheSheetKeepsItsInput`
     - Task 9 `aSignedInChildsEditDisablesTheOtherWhoChipsToo`
4. **The provider created the event but its reply was lost (a timeout), or the tablet then failed to store it.**
   - The retry uses the same key and gets the existing event back: exactly one event. A create the provider accepted but the tablet couldn't store is done, and the next sync mirrors it.
   - Tests:
     - Task 2 contract check `aRepeatedCreateWithTheSameKeyReturnsTheSameEvent`, and its fixtures `KeylessContract` and `DuplicatingContract`
     - Task 3 `aCreateWhoseMirrorWriteFailsIsRetriedAndMakesOneEvent`
     - Task 5 `aTimeoutAfterTheProviderCreatedTheEventMakesOneEventAfterTheDrain`
     - Task 5 `aCreateAcceptedButNotStoredIsDoneAndMakesOneEvent`
5. **Household-zone day boundaries and clock changes when building drafts.**
   - "Tomorrow" chosen at 23:30 is still that date after midnight; on 25 Oct 2026 (the autumn change) and 29 Mar 2026 (the spring gap) the draft keeps the local times the chips showed; an edit that leaves the times alone keeps its instants; a new event stops at midnight.
   - Tests:
     - Task 6 `tomorrowChosenAt2330IsTheDateItShowed`
     - Task 6 `aDraftOnTheAutumnChangeDayUsesTheLocalTimes`
     - Task 6 `aDraftInTheSpringGapMovesForward`
     - Task 6 `anEditThatLeavesTheTimesAloneKeepsTheEventsOwnInstants`
     - Task 6 `aNewEventEndsAtMidnightAtTheLatest`
     - Task 9 `tomorrowChosenAt2330IsStillThatDateWhenSavedAfterMidnight` (the clock moves past midnight before Save)

---

## File Structure

```
capability/calendar/
  schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/3.json   (generated, committed)
  src/main/java/uk/co/siland/culvery/capability/calendar/
    Stored.kt                (modify: PendingChange.clientKey; a create's ref from its key)
    db/CalendarDatabase.kt   (modify: v3, OutboxEntity.clientKey; the DAO's deleteOutboxFor)
    db/Migrations.kt         (modify: MIGRATION_2_3)
    CalendarStore.kt         (modify: map clientKey; dropCreate)
    di/CalendarModule.kt     (modify: add MIGRATION_2_3)
    CalendarContract.kt      (modify: newClientKey, CalendarWriter.create(…, clientKey))
    CalendarSync.kt          (modify: create with its key; a dropped create takes its event's changes; no defaults)
    Writes.kt                (modify: SilentToaster moves to the tests)
    PendingOverlay.kt        (rewrite: asCreatedEvent; a create under its key)
    Editability.kt           (modify: writableMaster)
    CalendarUi.kt            (modify: EditableEvent; SHORT_DAY internal; daySpan shared by whenLabel and EventForm)
    CalendarRepository.kt    (rewrite: event/editable over queued creates, masterLabel)
    CalendarPermissions.kt   (modify: mayCreateFor, mayRetag, cannotAddForOthers)
    CalendarEditor.kt        (rewrite: create, update, the Who rule and the re-read, Task 5)
    EventForm.kt             (create: TimeSlot, TimeChoice, lengthLabel, EventForm)
    ui/CalendarType.kt       (modify: picker, editor and entry-point styles and dimens)
    ui/Pickers.kt            (create: EditorPicker, the date and time picker cards, the picker layer)
    ui/Components.kt         (rewrite: + DeleteButton, PrimaryButton)
    ui/EventEditorSheet.kt   (create)
    ui/EventEditorHost.kt    (create: EditorRequest, EventEditorHost, rememberEventAdder)
    ui/Sheets.kt             (create: OverlayHost.showDetail / showEditor, SingleAction, rememberSingleAction)
    ui/EventDetailSheet.kt   (modify: the shared DeleteButton; Edit in the footer)
    ui/EventDetailHost.kt    (rewrite: initialMode, onEdit, the shared single action, the opener through showDetail)
    ui/TodayCard.kt, ui/WeekView.kt, ui/CardHosts.kt   (rewrite: entry points)
  src/test/java/uk/co/siland/culvery/capability/calendar/
    CalendarMigrationTest, CalendarStoreTest, CalendarSyncTest, ScriptedWriter, SilentToaster (create),
    PendingOverlayTest, CalendarRepositoryTest, CalendarPermissionsTest, CalendarEditorTest, EventFormTest (create), StubEditor,
    ui/PickersTest (create), ui/EventEditorSheetTest (create), ui/EditorScreenshotTest (create),
    ui/EventEditorHostTest (create), ui/EventDetailSheetTest, ui/DetailScreenshotTest, ui/CardsTest,
    ui/WeekViewTest, ui/OpenEventTest, ui/CardScreenshotTest, ui/WeekScreenshotTest
  src/test/screenshots/editor_*.png (new); detail_{editable,untagged,syncing,pin}_*, today_{dark,light}, week_{dark,light} (re-recorded)

capability/calendar-testkit/
  src/main/…/CalendarProviderContractTest.kt   (modify: keys; the idempotent-create check)
  src/test/…/fixtures/TinyProvider.kt, TinyContracts.kt; ContractSuiteSelfTest.kt   (modify: the keyless and duplicating fixtures)

provider/calendar-fake/
  src/main/…/FakeCalendarProvider.kt           (modify: keyed create, setOffline)
  src/test/…/FakeCalendarProviderTest.kt       (modify)

app/
  src/main/AndroidManifest.xml                  (modify: adjustResize)
  src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt   (modify: toasts above the keyboard)
  src/debug/AndroidManifest.xml                 (create: DebugOfflineReceiver)
  src/debug/java/uk/co/siland/culvery/DebugOfflineReceiver.kt    (create)
  src/test/java/uk/co/siland/culvery/shell/ui/OverlayLayersTest.kt   (modify)
  src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt       (create)

README.md, docs/superpowers/plans/2026-09-23-plan1-followups.md   (modify, Task 12, after the checkpoint)
```

`…` in a path stands for the module's package directory; every step spells out the full path.

---

### Task 1: `calendar.db` v3 — the outbox's client key, `MIGRATION_2_3`, and the store

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Stored.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/Migrations.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`
- Create (generated): `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/3.json`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarMigrationTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt` (modify)

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `PendingChange(id, connectionId, sourceId, remoteId, kind, draft, attempts, nextAttemptMillis, createdMillis, clientKey: String? = null)`
  - `PendingChange.ref: EventRef?` — for `CREATE`, `EventRef(connectionId, sourceId, clientKey)` (null without a key); for every other kind, from `remoteId` as before
  - `OutboxEntity.clientKey: String?`; `CalendarDatabase` version 3
  - `val MIGRATION_2_3: Migration` in `uk.co.siland.culvery.capability.calendar.db`

- [ ] **Step 1: Write the failing migration test**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarMigrationTest.kt`:

1. Replace the imports block with:
```kotlin
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.MIGRATION_1_2
import uk.co.siland.culvery.capability.calendar.db.MIGRATION_2_3
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
```

2. The v1 test now opens a v3 database. In `migrationFromV1KeepsConnectionsSourcesEventsAndCursors`, replace
```kotlin
            .addMigrations(MIGRATION_1_2)
```
with
```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

3. Above `@RunWith(AndroidJUnit4::class)`, add:
```kotlin
/** A draft as a v2 outbox row stores it. */
private const val DRAFT_JSON =
    """{"title":"Swim","start":{"instant":1000},"end":{"instant":2000},"forPerson":"alex-id","createdBy":"alex-id"}"""
```

4. Add these members at the end of the class:
```kotlin
    @Test
    fun migrationFromV2AddsAnEmptyClientKeyAndKeepsEverything() = runTest {
        file.parentFile?.mkdirs()
        file.delete()

        val v2 = helper.createDatabase(2)
        v2.execSQL(
            "INSERT INTO connection (id, providerId, label, configJson, health, healthMessage, lastSyncMillis) " +
                "VALUES ('c1', 'calendar.test', 'Google', '{}', 'OK', NULL, 1234)",
        )
        v2.execSQL(
            "INSERT INTO source (connectionId, sourceId, name, writable, visible, personId, isMaster) " +
                "VALUES ('c1', 's1', 'Family', 1, 1, 'family', 1), ('c1', 's2', 'Alex', 0, 1, 'alex-id', 0)",
        )
        v2.execSQL(
            "INSERT INTO event (connectionId, sourceId, remoteId, title, startInstant, startDate, endInstant, endDate, " +
                "recurring, forPerson, createdBy, startSort, endSort) " +
                "VALUES ('c1', 's1', 'e1', 'Swim', 1000, NULL, 2000, NULL, 0, 'alex-id', 'sam-id', 1000, 2000)",
        )
        v2.execSQL(
            "INSERT INTO sync_state (connectionId, sourceId, cursor, rangeStart) " +
                "VALUES ('c1', 's1', 'k7', '2026-09-22|Europe/London')",
        )
        v2.execSQL(
            "INSERT INTO outbox (connectionId, sourceId, remoteId, kind, draftJson, attempts, nextAttemptMillis, createdMillis) VALUES " +
                "('c1', 's1', NULL, 'CREATE', '$DRAFT_JSON', 0, 10, 100), " +
                "('c1', 's1', 'e1', 'UPDATE', '$DRAFT_JSON', 1, 20, 200), " +
                "('c1', 's1', 'e1', 'ASSIGN', '$DRAFT_JSON', 0, 30, 300), " +
                "('c1', 's1', 'e1', 'DELETE', NULL, 2, 40, 400)",
        )
        v2.close()

        val v3 = helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3))
        try {
            // The driver-based helper may not notice a dropped table, so the list is checked here.
            assertThat(tableNames(v3)).containsExactly("connection", "event", "outbox", "source", "sync_state").inOrder()
        } finally {
            v3.close()
        }

        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val store = CalendarStore(db)
            assertThat(store.connectionsNow().single().connection.label).isEqualTo("Google")
            assertThat(store.master().first()?.source.id).isEqualTo("s1")
            assertThat(store.eventNow(EventRef("c1", "s1", "e1"))?.title).isEqualTo("Swim")
            val range = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), ZoneId.of("Europe/London"))
            assertThat(store.cursor("c1", "s1", range)).isEqualTo(SyncCursor("k7"))

            val pending = store.pendingNow()
            assertThat(pending.map { it.kind })
                .containsExactly(ChangeKind.CREATE, ChangeKind.UPDATE, ChangeKind.ASSIGN, ChangeKind.DELETE).inOrder()
            assertThat(pending.map { it.clientKey }).containsExactly(null, null, null, null)
            assertThat(pending.map { it.attempts }).containsExactly(0, 1, 0, 2).inOrder()
            assertThat(pending.first().draft?.title).isEqualTo("Swim")
            // A v2 create has no key, so no ref: the drain drops it as undeliverable (Task 2's isComplete).
            assertThat(pending.first().ref).isNull()
        } finally {
            db.close()
        }
    }

    /** The app's own tables, without SQLite's, Android's and Room's bookkeeping. */
    private fun tableNames(connection: SQLiteConnection): List<String> {
        val statement = connection.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
        )
        try {
            val names = mutableListOf<String>()
            while (statement.step()) names += statement.getText(0)
            return names
        } finally {
            statement.close()
        }
    }
```

- [ ] **Step 2: Write the failing store tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`, add these members after `outboxRoundTripsEveryKindAndDraftShape`:
```kotlin
    @Test
    fun aQueuedCreateKeepsItsClientKeyAndItsRefUsesIt() = runTest {
        connect("s1")
        val key = "0123456789abcdef0123456789abcdef"
        val create = change(ChangeKind.CREATE, remoteId = null).copy(clientKey = key)
        val id = store.enqueue(create)
        val read = store.pendingNow().single()
        assertThat(read).isEqualTo(create.copy(id = id))
        assertThat(read.ref).isEqualTo(EventRef("c1", "s1", key))
    }

    @Test
    fun onlyACreateTakesItsRefFromItsClientKey() {
        assertThat(change(ChangeKind.UPDATE).copy(clientKey = "stray").ref).isEqualTo(EventRef("c1", "s1", "e1"))
        assertThat(change(ChangeKind.CREATE, remoteId = null).ref).isNull()
    }
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarMigrationTest*" --tests "*CalendarStoreTest*"`
Expected: compilation FAILS: `MIGRATION_2_3` and `clientKey` are unresolved.

- [ ] **Step 4: Add the key to `PendingChange`**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Stored.kt`, replace the `PendingChange` KDoc and class (from `/**` above `data class PendingChange` to the end of the file) with:
```kotlin
/**
 * One queued write, kept until the provider accepts or refuses it. [remoteId] is null only for CREATE, which
 * carries its [clientKey] instead: the id the provider gives the event (CalendarWriter.create), so a retried create
 * can't make a second one. [draft] is null only for DELETE, and for ASSIGN only its forPerson counts. [id] is 0
 * until the store assigns one.
 */
data class PendingChange(
    val id: Long,
    val connectionId: String,
    val sourceId: String,
    val remoteId: String?,
    val kind: ChangeKind,
    val draft: EventDraft?,
    val attempts: Int,
    val nextAttemptMillis: Long,
    val createdMillis: Long,
    val clientKey: String? = null,
) {
    /**
     * The event this change is for. A create's ref is built from its client key, the id the event keeps once it
     * syncs, so the changes queued behind it and the sheets showing it use one ref throughout.
     */
    val ref: EventRef?
        get() = (if (kind == ChangeKind.CREATE) clientKey else remoteId)?.let { EventRef(connectionId, sourceId, it) }
}
```

- [ ] **Step 5: Store it: the entity, v3 and `MIGRATION_2_3`**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`:

1. In `OutboxEntity`, after `val createdMillis: Long,`, add:
```kotlin
    /** CREATE only (v3): the key the provider uses as the event's id, so a retried create can't duplicate it. */
    val clientKey: String? = null,
```
2. Replace `version = 2,` with `version = 3,`.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/Migrations.kt`, add at the end of the file:
```kotlin

/** v3 (Plan 2b-2): a create's client key on the outbox. The SQL must match schemas/…/3.json exactly. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `clientKey` TEXT")
    }
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`:
- replace `import uk.co.siland.culvery.capability.calendar.db.MIGRATION_1_2` with
```kotlin
import uk.co.siland.culvery.capability.calendar.db.MIGRATION_1_2
import uk.co.siland.culvery.capability.calendar.db.MIGRATION_2_3
```
- replace `.addMigrations(MIGRATION_1_2)` with `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`:
- in `PendingChange.toEntity()`, after `createdMillis = createdMillis,` add `clientKey = clientKey,`;
- in `OutboxEntity.toPending()`, after `createdMillis = createdMillis,` add `clientKey = clientKey,`.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarMigrationTest*" --tests "*CalendarStoreTest*"`
Expected: PASS. The build writes `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/3.json`.
- If the migration test says it can't find `3.json` in the assets, run the same command again. KSP writes the schema while compiling, which can come after the debug assets were merged on the first run.
- Open `3.json` and check that the `outbox` table's `createSql` ends with `` `clientKey` TEXT) `` and that the column is not `notNull`. If Room reports a schema mismatch, the migration's SQL differs from `3.json`: make the migration match it; never edit the JSON.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar
git commit -m "Store a client key with each queued create (calendar.db v3)"
```

---

### Task 2: `CalendarWriter.create` takes a client key — the contract, its check, and every writer

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Modify: `capability/calendar-testkit/src/main/java/uk/co/siland/culvery/capability/calendar_testkit/CalendarProviderContractTest.kt`
- Modify: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyProvider.kt`
- Modify: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyContracts.kt`
- Test: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/ContractSuiteSelfTest.kt` (modify)
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`
- Test: `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt` (modify)
- Modify: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedWriter.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt` (modify)

**Interfaces:**
- Consumes: `PendingChange.clientKey` (Task 1).
- Produces:
  - `fun newClientKey(): String` — a random UUID as 32 lowercase hex digits (a valid Google event id)
  - `CalendarWriter.create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent` — contract: `remoteId == clientKey`; a repeated key on that source returns the event it made
  - contract check `CalendarProviderContractTest.aRepeatedCreateWithTheSameKeyReturnsTheSameEvent`; fixtures `KeylessContract` (a new id per create) and `DuplicatingContract` (the key as the id, but a second event on a repeat)
  - test-only `ScriptedWriter.created: LinkedHashMap<String, RemoteEvent>` (by key) and `ScriptedWriter.loseNextReply: Boolean` (the next create makes its event, then never replies)
  - the drain sends a create with its key; a queued create without a key is incomplete and is dropped with a toast

- [ ] **Step 1: Write the failing contract check and its self-test**

In `capability/calendar-testkit/src/main/java/uk/co/siland/culvery/capability/calendar_testkit/CalendarProviderContractTest.kt`:

1. Add `import uk.co.siland.culvery.capability.calendar.newClientKey` to the imports.
2. Every create now takes a new key. Make these replacements (the first line occurs twice; replace both):

| Replace | With |
|---|---|
| `val created = w.create(conn, source, draft)` | `val created = w.create(conn, source, draft, newClientKey())` |
| `val created = w.create(conn, source, draftIn("Before", 1))` | `val created = w.create(conn, source, draftIn("Before", 1), newClientKey())` |
| `val created = w.create(conn, source, draftIn("Doomed", 1))` | `val created = w.create(conn, source, draftIn("Doomed", 1), newClientKey())` |
| `val created = w.create(conn, source, draftIn("Twice", 1))` | `val created = w.create(conn, source, draftIn("Twice", 1), newClientKey())` |
| `w.create(conn, unknown, draftIn("Lost", 1))` | `w.create(conn, unknown, draftIn("Lost", 1), newClientKey())` |

3. Add this check at the end of the class:
```kotlin
    /**
     * A retried create must not make a second event: the app queues a create whose reply was lost and sends it
     * again with the same key. The writer uses the key as the event's id (2b-2 design D5).
     */
    @Test
    fun aRepeatedCreateWithTheSameKeyReturnsTheSameEvent() = runTest {
        val (w, source) = requireWriting()
        val key = newClientKey()
        val draft = draftIn("Once only", 3)
        val first = w.create(conn, source, draft, key)
        assertWithMessage("the writer must use the client key as the event's id").that(first.remoteId).isEqualTo(key)
        val second = w.create(conn, source, draft, key)
        assertWithMessage("a repeated create must return the event its key made").that(second.remoteId).isEqualTo(first.remoteId)
        val synced = subject.sync(conn, source, window, null).upserts.filter { it.title == "Once only" }
        assertWithMessage("a repeated create must not make a second event").that(synced).hasSize(1)
    }
```

In `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyContracts.kt`, add at the end:
```kotlin
class KeylessContract : TinyContract(TinyProvider(ignoreClientKey = true))
class DuplicatingContract : TinyContract(TinyProvider(duplicateOnRepeat = true))
```

In `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/ContractSuiteSelfTest.kt`:
- add `import uk.co.siland.culvery.capability.calendar_testkit.fixtures.DuplicatingContract` and `import uk.co.siland.culvery.capability.calendar_testkit.fixtures.KeylessContract`;
- in `wellBehavedProviderPassesEveryCheck`, replace `assertThat(result.runCount).isEqualTo(16)` with `assertThat(result.runCount).isEqualTo(17)`;
- in `aReadOnlyProviderSkipsOnlyTheWriteChecks`, replace `assertThat(result.runCount).isEqualTo(16)` with `assertThat(result.runCount).isEqualTo(17)` and `assertThat(result.assumptionFailureCount).isEqualTo(6)` with `assertThat(result.assumptionFailureCount).isEqualTo(7)`;
- add these tests at the end of the class:
```kotlin
    @Test
    fun aWriterThatIgnoresTheClientKeyIsCaught() {
        assertThat(failuresOf(KeylessContract::class.java)).containsExactly("aRepeatedCreateWithTheSameKeyReturnsTheSameEvent")
    }

    @Test
    fun aWriterThatMakesASecondEventForARepeatedKeyIsCaught() {
        assertThat(failuresOf(DuplicatingContract::class.java)).containsExactly("aRepeatedCreateWithTheSameKeyReturnsTheSameEvent")
    }
```

- [ ] **Step 2: Write the failing fake-provider and drain tests**

In `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt`:
1. Add `import uk.co.siland.culvery.capability.calendar.newClientKey`.
2. Every `fake.create(conn, …, draft("…"))` call (in `readOnlySourcesCannotBeWritten`, `rejectNextWriteRejectsOnlyTheNextWrite` twice and `unreachableNextWriteFailsOnlyTheNextWrite` twice) gets `newClientKey()` as a fourth argument, for example `fake.create(conn, family, draft("Sleepover"), newClientKey())`.
3. Add at the end of the class:
```kotlin
    @Test
    fun aCreateUsesItsClientKeyAsTheEventsId() = runTest {
        val fake = providerOn(today)
        val key = newClientKey()
        assertThat(fake.create(conn, family, draft("Sleepover"), key).remoteId).isEqualTo(key)
        assertThat(fake.familyEvents().single { it.title == "Sleepover" }.remoteId).isEqualTo(key)
        // A repeated key returns the event it made.
        assertThat(fake.create(conn, family, draft("Sleepover"), key).remoteId).isEqualTo(key)
        assertThat(fake.familyEvents().count { it.title == "Sleepover" }).isEqualTo(1)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`:
1. After `private val toaster = RecordingToaster()`, add:
```kotlin
    private val key = "0123456789abcdef0123456789abcdef"
```
2. Replace the `queue` helper with:
```kotlin
    private suspend fun queue(
        kind: ChangeKind,
        remoteId: String? = "swim",
        draft: EventDraft? = swimDraft("sam-id"),
        attempts: Int = 1,
        sourceId: String = "s1",
        next: Instant = now,
        created: Instant = now,
        clientKey: String? = null,
    ): Long = store.enqueue(
        PendingChange(0, "c1", sourceId, remoteId, kind, draft, attempts, next.toEpochMilli(), created.toEpochMilli(), clientKey),
    )
```
3. Replace `drainDeliversACreateAndMirrorsIt` with:
```kotlin
    @Test
    fun drainDeliversACreateWithItsKeyAndMirrorsItUnderThatKey() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        assertThat(w.calls).containsExactly("create:Swim")
        assertThat(w.created.keys).containsExactly(key)
        assertThat(store.eventNow(EventRef("c1", "s1", key))!!.forPerson).isEqualTo("mia-id")
        assertThat(store.pendingNow()).isEmpty()
    }
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:calendar-testkit:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :capability:calendar:testDebugUnitTest --tests "*CalendarSyncTest*"`
Expected: compilation FAILS: `newClientKey`, `KeylessContract`, `DuplicatingContract` and `ScriptedWriter.created` are unresolved, and the four-argument `create` calls don't match `CalendarWriter`.

- [ ] **Step 4: Change the contract**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`:

1. Add `import java.util.UUID` to the imports.
2. Before the `/**` that opens `CalendarWriter`'s KDoc, add:
```kotlin
/**
 * A new client key: a random UUID as 32 lowercase hex digits, which is also a valid Google event id (base32hex,
 * 5–1024 characters). The editor chooses one on each Save tap; a queued create keeps its key for every retry.
 */
fun newClientKey(): String = UUID.randomUUID().toString().replace("-", "")

```
3. In `CalendarWriter`'s KDoc, after the line ` * - [delete] of an event that no longer exists succeeds: a retried delete must not be reported as a failure.`, add:
```kotlin
 * - [create] is idempotent by its client key: the writer uses the key as the event's id, so the returned remoteId
 *   equals it, and a create with a key already used on that source returns the event that key made, never a second
 *   one (Google: events.insert with id = clientKey; a 409 means it exists, so fetch and return it).
```
4. Replace
```kotlin
    suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEvent
```
with
```kotlin
    suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent
```

- [ ] **Step 5: Send a queued create with its key**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`:

1. In `isComplete()`, replace `ChangeKind.CREATE -> draft != null` with:
```kotlin
        ChangeKind.CREATE -> draft != null && clientKey != null
```
2. In `deliver`, replace the `ChangeKind.CREATE -> { … }` branch with:
```kotlin
            ChangeKind.CREATE -> {
                val draft = checkNotNull(change.draft)
                val key = checkNotNull(change.clientKey)
                // The key makes a retry safe: if an earlier attempt did create the event, the provider returns it.
                callWriter(io, timeoutMillis) { writer.create(conn, source, draft, key) }
            }
```

- [ ] **Step 6: Update the three writers**

**The testkit's fixture.** In `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyProvider.kt`:
- after `private val rejectMissingDelete: Boolean = false,` add `private val ignoreClientKey: Boolean = false,` and `private val duplicateOnRepeat: Boolean = false,`;
- replace the `create` function with:
```kotlin
    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent {
        checkWritable(source)
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
        version++
        return event
    }
```

**The calendar tests' writer.** Replace `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedWriter.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import uk.co.siland.culvery.core.plugin.Connection

/**
 * An in-test writer. [calls] reads "create:<title>", "update:<remoteId>" or "delete:<remoteId>". Creates keep the
 * contract: the client key is the event's id, and a repeated key returns the event it made.
 */
internal class ScriptedWriter(override val providerId: String) : CalendarWriter {
    val calls = mutableListOf<String>()
    /** The drafts sent to create and update, in order. */
    val drafts = mutableListOf<EventDraft>()
    /** The events create made, by client key. */
    val created = linkedMapOf<String, RemoteEvent>()
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

    override suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft): RemoteEvent {
        record("update:$remoteId", draft)
        gate?.await()
        failWith?.let { throw it }
        return RemoteEvent(remoteId, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
    }

    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) {
        record("delete:$remoteId", null)
        gate?.await()
        failWith?.let { throw it }
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

**The fake provider.** In `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`:

1. After `private val ConnectScreenButtonGap = 12.dp`, add:
```kotlin

private const val OFFLINE_MESSAGE = "Sample calendar is offline"
```
2. Remove the line `private var nextId = 0`.
3. Replace the `create` function with:
```kotlin
    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent =
        write(source) {
            // The key is the event's id, so a retried create returns the event it made (CalendarWriter contract).
            created.getOrPut(clientKey) {
                RemoteEvent(clientKey, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
            }
        }
```
4. In `write`, replace `throw UnreachableException("Sample calendar is offline")` with `throw UnreachableException(OFFLINE_MESSAGE)`.

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :capability:calendar-testkit:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :capability:calendar:testDebugUnitTest --tests "*CalendarSyncTest*"`
Expected: PASS.
- `ContractSuiteSelfTest`: the good provider runs 17 checks with no skips; `KeylessContract` and `DuplicatingContract` each fail only the new check.
- `FakeCalendarProviderContractTest` passes all 17, the new check included.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add capability/calendar capability/calendar-testkit provider/calendar-fake
git commit -m "Make calendar creates idempotent with a client key the writer uses as the event's id"
```

---

### Task 3: The drain — a dropped create takes its queued changes, the duplicate-create retry, and housekeeping

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/SilentToaster.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt` (modify)

**Interfaces:**
- Consumes: `PendingChange.ref` for a create (Task 1); `ScriptedWriter.created` and the four-argument `create` (Task 2).
- Produces:
  - `CalendarDao.deleteOutboxFor(connectionId, sourceId, key): Int` and `CalendarStore.dropCreate(ref: EventRef): Int` — a create and every change queued for its event (`clientKey = key` or `remoteId = key`), in one statement, including changes queued after the drain read the queue
  - drain behaviour: when a create is dropped (refused, older than 48 hours, or undeliverable), `dropCreate` takes every change queued for its ref, and they are counted in the same per-connection toast
  - `CalendarSync`'s internal constructor has no default arguments: `(store, providers, zone, clock, io, timeoutMillis, writers, toaster)`
  - test-only `internal object SilentToaster : Toaster` in `capability/calendar/src/test/java/…/SilentToaster.kt`

- [ ] **Step 1: Write the failing store and drain tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`, add after `onlyACreateTakesItsRefFromItsClientKey`:
```kotlin
    @Test
    fun droppingACreateTakesEveryChangeQueuedForItsEvent() = runTest {
        connect("s1")
        val key = "0123456789abcdef0123456789abcdef"
        store.enqueue(change(ChangeKind.CREATE, remoteId = null).copy(clientKey = key))
        store.enqueue(change(ChangeKind.UPDATE, remoteId = key))
        store.enqueue(change(ChangeKind.DELETE, remoteId = key, draft = null))
        val other = store.enqueue(change(ChangeKind.UPDATE, remoteId = "e1"))
        assertThat(store.dropCreate(EventRef("c1", "s1", key))).isEqualTo(3)
        assertThat(store.pendingNow().map { it.id }).containsExactly(other)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`:

1. Add these imports:
```kotlin
import androidx.room.execSQL
import androidx.room.useWriterConnection
```
2. `engine()` builds the engine without writers; spell that out, since the constructor loses its defaults. Replace
```kotlin
        return CalendarSync(store, setOf(a, b), HouseholdZone(household), clock, EmptyCoroutineContext, timeoutMillis = 1_000)
```
with
```kotlin
        return CalendarSync(
            store, setOf(a, b), HouseholdZone(household), clock, EmptyCoroutineContext, timeoutMillis = 1_000,
            writers = emptySet(), toaster = SilentToaster,
        )
```
3. Add at the end of the class:
```kotlin
    @Test
    fun aRefusedCreateDropsTheChangesQueuedBehindItWithOneToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        queue(ChangeKind.UPDATE, remoteId = key, draft = swimDraft("sam-id"))
        queue(ChangeKind.DELETE, remoteId = key, draft = null)
        w.failWith = WriteRejectedException("Calendar is full")
        sync.syncAll()
        // Nothing is sent for an event that was never made.
        assertThat(w.calls).containsExactly("create:Swim")
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save 3 changes to C1")
    }

    @Test
    fun aCreateDroppedAfterTwoDaysTakesItsQueuedChangesWithIt() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        val longAgo = now.minusMillis(OUTBOX_MAX_AGE_MS + 1)
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key, created = longAgo)
        queue(ChangeKind.UPDATE, remoteId = key, draft = swimDraft("sam-id"))
        sync.syncAll()
        assertThat(w.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save 2 changes to C1")
    }

    @Test
    fun aChangeForAnotherEventIsNotDroppedWithARefusedCreate() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        queue(ChangeKind.DELETE, remoteId = "swim", draft = null)
        w.failWith = WriteRejectedException("Calendar is full")
        sync.syncAll()
        // The delete was tried (and refused too) on its own account.
        assertThat(w.calls).containsExactly("create:Swim", "delete:swim").inOrder()
    }

    @Test
    fun aQueuedCreateEditAndDeleteAreDeliveredInOrder() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        queue(ChangeKind.UPDATE, remoteId = key, draft = swimDraft("sam-id"))
        queue(ChangeKind.DELETE, remoteId = key, draft = null)
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        assertThat(w.calls).containsExactly("create:Swim", "update:$key", "delete:$key").inOrder()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.eventNow(EventRef("c1", "s1", key))).isNull()
    }

    // The row stays due and is sent again on the next pass. Plan 3's drain backoff (2b-1 m2, R3) changes that wait
    // and must update this test with it.
    @Test
    fun aCreateWhoseMirrorWriteFailsIsRetriedAndMakesOneEvent() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        a.failWith = UnreachableException("reads are down")
        // The provider accepts the create, then the tablet can't store it (a full disk).
        calendar.useWriterConnection {
            it.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON event BEGIN SELECT RAISE(ABORT, 'disk full'); END")
        }
        sync.syncAll()
        assertThat(store.pendingNow()).hasSize(1)
        calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_event") }
        sync.syncAll()
        // Sent twice with one key: the provider returned the event it had already made.
        assertThat(w.calls).containsExactly("create:Swim", "create:Swim")
        assertThat(w.created.keys).containsExactly(key)
        assertThat(store.eventNow(EventRef("c1", "s1", key))).isNotNull()
        assertThat(store.pendingNow()).isEmpty()
    }
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarStoreTest*" --tests "*CalendarSyncTest*"`
Expected: compilation FAILS: `dropCreate` is unresolved. (`SilentToaster` is still in `main`, which the tests can see.) Until Step 3's drain uses `dropCreate`, two drain tests would fail:
- `aRefusedCreateDropsTheChangesQueuedBehindItWithOneToast`: the update and the delete are still sent, so `w.calls` has three entries;
- `aCreateDroppedAfterTwoDaysTakesItsQueuedChangesWithIt`: the update is still sent.

The other three new drain tests already pass. They pin behaviour that Tasks 1 and 2 built, and this task's change must keep it.

- [ ] **Step 3: Drop a dropped create's changes with it, in one statement**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`, in `CalendarDao`, after `suspend fun deleteOutbox(id: Long)`, add:
```kotlin

    /** A create and every change queued for its event: the create carries the key, the others target it as remoteId. */
    @Query(
        "DELETE FROM outbox WHERE connectionId = :connectionId AND sourceId = :sourceId " +
            "AND (clientKey = :key OR remoteId = :key)",
    )
    suspend fun deleteOutboxFor(connectionId: String, sourceId: String, key: String): Int
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`, after `suspend fun dropChange(id: Long) = dao.deleteOutbox(id)`, add:
```kotlin

    /**
     * Drops the create for [ref] and every change queued for that event, in one statement, so a change queued after
     * the caller read the queue goes too. Returns how many changes went.
     */
    suspend fun dropCreate(ref: EventRef): Int = dao.deleteOutboxFor(ref.connectionId, ref.sourceId, ref.remoteId)
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`, replace `drainOutbox`, from its KDoc to its closing brace, with:
```kotlin
    /**
     * Delivers queued changes in the order they were made. An event's later changes wait while an earlier one is
     * waiting or has just failed, so they can't land first and be undone; they are rescheduled to its next attempt,
     * so the loop doesn't wake for them before it. Once a connection fails to answer, its
     * other changes wait for their next attempt instead of each costing a timeout. Changes nothing can deliver,
     * changes the provider refuses, and changes older than [OUTBOX_MAX_AGE_MS] are dropped, with one toast per
     * connection, shown even if the pass then fails. A dropped create takes the changes queued behind it with it:
     * there is no event for them to change (2b-2 design §5).
     */
    private suspend fun drainOutbox(connections: List<StoredConnection>, zone: ZoneId) {
        val now = clock.nowMillis()
        val byId = connections.associateBy { it.connection.id }
        // Each blocked event, with the time its earliest waiting change is next tried.
        val blockedRefs = mutableMapOf<EventRef, Long>()
        val blockedConnections = mutableSetOf<String>()
        val dropped = linkedMapOf<String, MutableList<String?>>()
        val droppedCreates = mutableSetOf<EventRef>()

        suspend fun drop(change: PendingChange, label: String, reason: String?) {
            val ref = change.ref
            val count = if (change.kind == ChangeKind.CREATE && ref != null) {
                droppedCreates += ref
                store.dropCreate(ref)
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
                if (now - change.createdMillis > OUTBOX_MAX_AGE_MS) {
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
                when (val outcome = deliver(change, stored.connection, source.source, writer, zone)) {
                    is WriteOutcome.Accepted -> Unit
                    is WriteOutcome.Rejected -> drop(change, label, outcome.message)
                    is WriteOutcome.Retry -> {
                        val next = retryLater(change, now)
                        ref?.let { blockedRefs[it] = next }
                        if (outcome.blocksConnection) blockedConnections += change.connectionId
                    }
                }
            }
        } finally {
            // Those changes are already gone, so they are told even when a later change fails the pass.
            dropped.forEach { (label, reasons) -> toaster.show(couldNotSaveAll(label, reasons)) }
        }
    }
```

- [ ] **Step 4: Housekeeping — no defaults, and `SilentToaster` in the tests**

In `CalendarSync.kt`, in the internal constructor, replace
```kotlin
    private val writers: Set<@JvmSuppressWildcards CalendarWriter> = emptySet(),
    private val toaster: Toaster = SilentToaster,
```
with
```kotlin
    private val writers: Set<@JvmSuppressWildcards CalendarWriter>,
    private val toaster: Toaster,
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`, delete
```kotlin
/** For an engine built without a toaster, in tests that don't look at toasts. */
internal object SilentToaster : Toaster {
    override fun show(message: String, icon: String) = Unit
}

```
and delete the now-unused `import uk.co.siland.culvery.core.plugin.Toaster`.

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/SilentToaster.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.plugin.Toaster

/** For an engine built in a test that doesn't look at toasts. */
internal object SilentToaster : Toaster {
    override fun show(message: String, icon: String) = Unit
}
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarStoreTest*" --tests "*CalendarSyncTest*"`
Expected: PASS.

Then check that nothing in `main` still mentions the silent toaster:
```bash
grep -rn "SilentToaster" capability/calendar/src/main
```
Expected: no output.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. `SampleRollbackTest` builds `CalendarSync` through its public `@Inject` constructor, so it is unaffected.

- [ ] **Step 7: Commit**

```bash
git add capability/calendar
git commit -m "Drop the changes queued behind a create the calendar refused, and retry creates with their key"
```

---

### Task 4: Queued creates as events — the overlay, the detail, `editable`, and `masterLabel`

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/PendingOverlay.kt` (rewrite)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Editability.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarRepository.kt` (rewrite)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/PendingOverlayTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt` (modify)

**Interfaces:**
- Consumes: `PendingChange.clientKey`, `PendingChange.ref` (Task 1).
- Produces:
  - `internal fun PendingChange.asCreatedEvent(sourcePerson: PersonId, zone: ZoneId): StoredEvent?` — a queued create as the event it will become, `remoteId = clientKey`; null for other kinds or without a key or draft
  - `overlayPending` shows a queued create under `EventRef(connectionId, sourceId, clientKey)`; a keyless create isn't shown
  - `data class EditableEvent(val ref: EventRef, val title: String, val start: EventTime, val end: EventTime, val forPerson: String?)` (in `CalendarUi.kt`)
  - `CalendarRepository.event(ref, today): Flow<EventDetailUi?>` — now also a queued create not yet in the mirror
  - `CalendarRepository.editable(ref: EventRef): Flow<EditableEvent?>` — the event as shown, with its queued changes; null once gone or queued for deletion
  - `internal class WritableMaster(val source: StoredSource, val connection: Connection)` and `internal fun writableMaster(master: StoredSource?, connections: List<StoredConnection>, writerIds: Set<String>): WritableMaster?` (in `Editability.kt`) — the one rule for where new events go: a writable master whose provider binds a writer; the repository and the editor (Task 5) both use it
  - `CalendarRepository.masterLabel: Flow<String?>` — the writable master's connection label, null when there is nowhere to add

- [ ] **Step 1: Write the failing overlay tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/PendingOverlayTest.kt`:

1. Replace the `change` helper with:
```kotlin
    private fun change(id: Long, kind: ChangeKind, remoteId: String?, draft: EventDraft?, sourceId: String = "s1", clientKey: String? = null) =
        PendingChange(id, "c1", sourceId, remoteId, kind, draft, attempts = 0, nextAttemptMillis = 0, createdMillis = 0, clientKey = clientKey)
```
2. Replace `aQueuedCreateAppearsAsSyncingUnlessItsSourceIsHidden` with:
```kotlin
    @Test
    fun aQueuedCreateAppearsUnderItsClientKeyUnlessItsSourceIsHidden() {
        val draft = EventDraft("Sleepover", at(18), at(19), forPerson = "mia", createdBy = "mia")
        val shown = overlay(
            emptyList(),
            listOf(
                change(7, ChangeKind.CREATE, null, draft, clientKey = "k7"),
                change(8, ChangeKind.CREATE, null, draft, sourceId = "s2", clientKey = "k8"),
            ),
        )
        assertThat(shown.map { it.event.ref to it.syncing }).containsExactly(EventRef("c1", "s1", "k7") to true)
    }

    @Test
    fun aQueuedCreateWithoutAKeyIsNotShown() {
        val draft = EventDraft("Sleepover", at(18), at(19), forPerson = "mia", createdBy = "mia")
        assertThat(overlay(emptyList(), listOf(change(7, ChangeKind.CREATE, null, draft)))).isEmpty()
    }

    @Test
    fun changesQueuedBehindACreateApplyToItUnderItsKey() {
        val draft = EventDraft("Sleepover", at(18), at(19), forPerson = "mia", createdBy = "mia")
        val renamed = draft.copy(title = "Sleepover at Ava's")
        val create = change(1, ChangeKind.CREATE, null, draft, clientKey = "k1")
        val update = change(2, ChangeKind.UPDATE, "k1", renamed)
        assertThat(overlay(emptyList(), listOf(create, update)).single().event.title).isEqualTo("Sleepover at Ava's")
        assertThat(overlay(emptyList(), listOf(create, update, change(3, ChangeKind.DELETE, "k1", null)))).isEmpty()
    }

    @Test
    fun aCreateTheMirrorAlreadyHoldsShowsTheMirrorsCopyAsSyncing() {
        // The provider made it and a sync fetched it, but the reply was lost, so the create is still queued.
        val mirrored = stored("k1", 18).copy(title = "From the provider")
        val draft = EventDraft("From the sheet", at(18), at(19), forPerson = null, createdBy = null)
        val shown = overlay(listOf(mirrored), listOf(change(1, ChangeKind.CREATE, null, draft, clientKey = "k1"))).single()
        assertThat(shown.event.title to shown.syncing).isEqualTo("From the provider" to true)
    }
```

- [ ] **Step 2: Write the failing repository tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt`:

1. Replace the `queue` helper with:
```kotlin
    private val key = "0123456789abcdef0123456789abcdef"

    private suspend fun queue(
        kind: ChangeKind,
        remoteId: String?,
        draft: EventDraft? = null,
        source: String = "s-family",
        clientKey: String? = null,
    ): Long = store.enqueue(
        PendingChange(0, "c1", source, remoteId, kind, draft, attempts = 1, nextAttemptMillis = 0, createdMillis = 0, clientKey = clientKey),
    )
```
2. Replace `aQueuedCreateShowsAsSyncing` with:
```kotlin
    @Test
    fun aQueuedCreateShowsAsSyncingUnderItsKey() = runTest {
        val slot = timed("Sleepover", 23, 18, 0, 60)
        queue(ChangeKind.CREATE, null, EventDraft("Sleepover", slot.start, slot.end, forPerson = sam.id.value, createdBy = sam.id.value), clientKey = key)
        val e = today().single()
        assertThat(listOf(e.title, e.person.name)).containsExactly("Sleepover", "Sam").inOrder()
        assertThat(e.syncing).isTrue()
        assertThat(e.ref).isEqualTo(ref(key))
    }

    @Test
    fun aQueuedCreateOpensInTheDetailSheetAsSyncingAndEditable() = runTest {
        val slot = timed("Sleepover", 23, 18, 0, 60)
        queue(ChangeKind.CREATE, null, EventDraft("Sleepover", slot.start, slot.end, sam.id.value, sam.id.value), clientKey = key)
        val detail = repo.event(ref(key), today = sept(23)).first()!!
        assertThat(detail.whenLabel).isEqualTo("Today · 18:00–19:00")
        assertThat(detail.event.syncing).isTrue()
        assertThat(detail.event.editable).isTrue()
        assertThat(detail.event.createdBy).isEqualTo("Sam")
    }

    @Test
    fun aChangeAndADeleteQueuedBehindACreateShowUnderItsRef() = runTest {
        val slot = timed("Sleepover", 23, 18, 0, 60)
        val draft = EventDraft("Sleepover", slot.start, slot.end, sam.id.value, sam.id.value)
        queue(ChangeKind.CREATE, null, draft, clientKey = key)
        queue(ChangeKind.UPDATE, key, draft.copy(title = "Sleepover at Ava's"))
        assertThat(today().single().let { it.ref to it.title }).isEqualTo(ref(key) to "Sleepover at Ava's")
        assertThat(repo.event(ref(key), today = sept(23)).first()!!.event.title).isEqualTo("Sleepover at Ava's")
        queue(ChangeKind.DELETE, key)
        assertThat(today()).isEmpty()
        assertThat(repo.event(ref(key), today = sept(23)).first()).isNull()
    }

    @Test
    fun editableIsTheEventAsShownWithItsQueuedChanges() = runTest {
        val original = timed("Dinner with Jo & Priya", 23, 19, 30, 90, forPerson = alex.id.value)
        put("s-family", original)
        assertThat(repo.editable(ref(original.remoteId)).first())
            .isEqualTo(EditableEvent(ref(original.remoteId), original.title, original.start, original.end, alex.id.value))
        val moved = timed("Dinner at Gran's", 24, 18, 0, 60)
        queue(ChangeKind.UPDATE, original.remoteId, EventDraft(moved.title, moved.start, moved.end, sam.id.value, alex.id.value))
        assertThat(repo.editable(ref(original.remoteId)).first())
            .isEqualTo(EditableEvent(ref(original.remoteId), "Dinner at Gran's", moved.start, moved.end, sam.id.value))
        queue(ChangeKind.DELETE, original.remoteId)
        assertThat(repo.editable(ref(original.remoteId)).first()).isNull()
    }

    @Test
    fun theMasterLabelNamesWhereNewEventsGo() = runTest {
        assertThat(repo.masterLabel.first()).isEqualTo("Google")
    }

    @Test
    fun thereIsNowhereToAddWithoutAWriterForTheMaster() = runTest {
        val readOnly = CalendarRepository(store, household, HouseholdZone(household), emptySet(), emptySet())
        assertThat(readOnly.masterLabel.first()).isNull()
    }
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*PendingOverlayTest*" --tests "*CalendarRepositoryTest*"`
Expected: compilation FAILS: `EditableEvent`, `editable` and `masterLabel` are unresolved.

- [ ] **Step 4: Lay a queued create over the mirror under its key**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/PendingOverlay.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.ZoneId
import uk.co.siland.culvery.core.household.PersonId

/** A mirrored event as the UI shows it, with any queued change laid over it. */
internal data class ShownEvent(val event: StoredEvent, val syncing: Boolean)

/**
 * A queued create as the event it will become, under its client key: the ref it keeps once it syncs (2b-2 design
 * D5). Null for any other kind, or for a create without a key or a draft.
 */
internal fun PendingChange.asCreatedEvent(sourcePerson: PersonId, zone: ZoneId): StoredEvent? {
    if (kind != ChangeKind.CREATE) return null
    val key = clientKey ?: return null
    val d = draft ?: return null
    return StoredEvent(
        connectionId = connectionId,
        sourceId = sourceId,
        remoteId = key,
        title = d.title,
        start = d.start,
        end = d.end,
        recurring = false,
        forPerson = d.forPerson,
        createdBy = d.createdBy,
        sourcePerson = sourcePerson,
        startSort = d.start.instantIn(zone).toEpochMilli(),
        endSort = d.end.instantIn(zone).toEpochMilli(),
    )
}

/**
 * Lays [pending] changes over [events] in queue order. A delete hides the event. An update shows the draft's
 * values; an assign shows only its person. A create adds its draft, on a visible source, under its client key, so
 * the changes queued behind it apply to it and it keeps its ref once it syncs. Only events overlapping
 * [windowStart, windowEnd) are returned, in start order.
 */
internal fun overlayPending(
    events: List<StoredEvent>,
    pending: List<PendingChange>,
    sources: (connectionId: String, sourceId: String) -> StoredSource?,
    zone: ZoneId,
    windowStart: Long,
    windowEnd: Long,
): List<ShownEvent> {
    val shown = LinkedHashMap<EventRef, ShownEvent>()
    events.forEach { shown[it.ref] = ShownEvent(it, syncing = false) }
    for (change in pending) {
        val draft = change.draft
        when (change.kind) {
            ChangeKind.DELETE -> change.ref?.let { shown.remove(it) }
            ChangeKind.UPDATE -> {
                val ref = change.ref ?: continue
                val current = shown[ref] ?: continue
                if (draft != null) shown[ref] = ShownEvent(current.event.withDraft(draft, zone), syncing = true)
            }
            ChangeKind.ASSIGN -> {
                val ref = change.ref ?: continue
                val current = shown[ref] ?: continue
                if (draft != null) shown[ref] = ShownEvent(current.event.copy(forPerson = draft.forPerson), syncing = true)
            }
            ChangeKind.CREATE -> {
                val source = sources(change.connectionId, change.sourceId)?.takeIf { it.mapping.visible } ?: continue
                val event = change.asCreatedEvent(source.mapping.person, zone) ?: continue
                // A sync may already have fetched it (the provider made it, but its reply was lost): keep that copy.
                shown[event.ref] = ShownEvent(shown[event.ref]?.event ?: event, syncing = true)
            }
        }
    }
    return shown.values
        .filter { spanOverlaps(it.event.startSort, it.event.endSort, windowStart, windowEnd) }
        .sortedWith(compareBy<ShownEvent> { it.event.startSort }.thenBy { it.event.title })
}

private fun StoredEvent.withDraft(d: EventDraft, zone: ZoneId) = copy(
    title = d.title,
    start = d.start,
    end = d.end,
    forPerson = d.forPerson,
    createdBy = d.createdBy,
    startSort = d.start.instantIn(zone).toEpochMilli(),
    endSort = d.end.instantIn(zone).toEpochMilli(),
)
```

- [ ] **Step 5: Add `EditableEvent`**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`, after `data class EventDetailUi(val event: EventUi, val whenLabel: String)`, add:
```kotlin

/** Where the add/edit sheet starts: an event as shown, with its queued changes laid over it (2b-2 design §3.1). */
data class EditableEvent(
    val ref: EventRef,
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val forPerson: String?,
)
```

- [ ] **Step 6: One rule for where new events go**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Editability.kt`:
1. Add `import uk.co.siland.culvery.core.plugin.Connection` below the package line (with a blank line between).
2. Add at the end of the file:
```kotlin

/** Where new events go: the master calendar and its connection. */
internal class WritableMaster(val source: StoredSource, val connection: Connection)

/**
 * The master calendar if the tablet can add to it (2b-2 design §6): writable, on a connection whose provider binds a
 * writer. Null means there is nowhere to add, which hides the add entry points and makes a create NotEditable.
 */
internal fun writableMaster(master: StoredSource?, connections: List<StoredConnection>, writerIds: Set<String>): WritableMaster? {
    val m = master?.takeIf { it.source.writable } ?: return null
    val connection = connections.firstOrNull { it.connection.id == m.connectionId }?.connection ?: return null
    return if (connection.providerId in writerIds) WritableMaster(m, connection) else null
}
```

- [ ] **Step 7: Read queued creates, `editable` and `masterLabel` in the repository**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarRepository.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature

/** Read-only view of the cache as UI models, with queued changes laid over it. The UI never touches the network. */
@Singleton
class CalendarRepository @Inject constructor(
    private val store: CalendarStore,
    private val household: HouseholdRepository,
    private val zone: HouseholdZone,
    providers: Set<@JvmSuppressWildcards CalendarProvider>,
    writers: Set<@JvmSuppressWildcards CalendarWriter>,
) {
    private val writerIds = writers.map { it.providerId }.toSet()

    init {
        providers
            .filter { Feature.WRITE in it.descriptor.features && it.descriptor.id !in writerIds }
            .forEach { Log.w(TAG, "${it.descriptor.id} declares WRITE but binds no CalendarWriter; its events are read-only") }
    }

    val hasConnections: Flow<Boolean> = store.connectionIds().map { it.isNotEmpty() }.distinctUntilChanged()

    /** The household's people, for the detail sheet's Assign chips and the add/edit sheet's Who chips. */
    val people: Flow<List<Person>> = household.people

    val syncStatus: Flow<SyncStatusUi> = store.connections().map { connections ->
        SyncStatusUi(
            lastSyncMillis = connections.mapNotNull { it.lastSyncMillis }.minOrNull(),
            needsSignIn = connections.filter { it.health == ConnectionHealth.NeedsSignIn }.map { it.connection.label },
            connectionLabels = connections.map { it.connection.label },
            failingBeforeFirstSync = connections.any { it.lastSyncMillis == null && it.health != ConnectionHealth.Ok },
        )
    }.distinctUntilChanged()

    /**
     * The connection label of the writable master calendar, where new events go; null when there is nowhere to add
     * (no master, or its provider binds no writer), which hides the add entry points (2b-2 design §4.1).
     */
    val masterLabel: Flow<String?> = combine(store.master(), store.connections()) { master, connections ->
        writableMaster(master, connections, writerIds)?.connection?.label
    }.distinctUntilChanged()

    private val catalog: Flow<SourceCatalog> =
        combine(store.sources(), store.connections()) { sources, connections -> SourceCatalog(sources, connections, writerIds) }

    fun day(date: LocalDate): Flow<List<EventUi>> = days(date, 1).map { it.single().events }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun days(start: LocalDate, count: Int): Flow<List<DayUi>> = zone.zone.flatMapLatest { z ->
        val from = millis(start, z)
        val to = millis(start.plusDays(count.toLong()), z)
        combine(store.eventsBetween(from, to), store.pending(), catalog, household.peopleWithFamily) { events, pending, cat, people ->
            val byId = people.associateBy { it.id }
            val shown = overlayPending(events, pending, cat::source, z, from, to)
            (0 until count).map { i ->
                val date = start.plusDays(i.toLong())
                val dayStart = millis(date, z)
                val dayEnd = millis(date.plusDays(1), z)
                DayUi(
                    date,
                    shown.filter { spanOverlaps(it.event.startSort, it.event.endSort, dayStart, dayEnd) }
                        .map { it.event.toUi(date, z, byId, cat, it.syncing) }
                        .sortedWith(compareByDescending<EventUi> { it.allDay }.thenBy { it.startSort }.thenBy { it.title }),
                )
            }
        }
    }

    fun week(start: LocalDate): Flow<WeekUi> =
        combine(days(start, 7), household.people) { days, people -> WeekUi(start, days, people + Person.Family) }

    /**
     * One event for the detail sheet, a queued create not yet synced included; null once it is gone or queued for
     * deletion. [today] makes "Today · …".
     */
    fun event(ref: EventRef, today: LocalDate): Flow<EventDetailUi?> = shownEvent(ref).map { s ->
        if (s == null) return@map null
        val e = s.shown.event
        val day = e.start.instantIn(s.zone).atZone(s.zone).toLocalDate()
        EventDetailUi(e.toUi(day, s.zone, s.people, s.catalog, s.shown.syncing), whenLabel(e.start, e.end, s.zone, today))
    }

    /** What the add/edit sheet starts from: [ref] as shown; null once it is gone or queued for deletion. */
    fun editable(ref: EventRef): Flow<EditableEvent?> = shownEvent(ref).map { s ->
        s?.shown?.event?.let { EditableEvent(ref, it.title, it.start, it.end, it.forPerson) }
    }

    private class Shown(val shown: ShownEvent, val catalog: SourceCatalog, val people: Map<PersonId, Person>, val zone: ZoneId)

    /** [ref] with its queued changes laid over it; a queued create not yet in the mirror counts (2b-2 design D6). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun shownEvent(ref: EventRef): Flow<Shown?> = zone.zone.flatMapLatest { z ->
        combine(store.event(ref), store.pending(), catalog, household.peopleWithFamily) { stored, pending, cat, people ->
            overlayPending(listOfNotNull(stored), pending.filter { it.ref == ref }, cat::source, z, Long.MIN_VALUE, Long.MAX_VALUE)
                .singleOrNull()
                ?.let { Shown(it, cat, people.associateBy { p -> p.id }, z) }
        }
    }

    private fun millis(date: LocalDate, z: ZoneId) = date.atStartOfDay(z).toInstant().toEpochMilli()

    private companion object {
        const val TAG = "CalendarRepository"
    }
}
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*PendingOverlayTest*" --tests "*CalendarRepositoryTest*"`
Expected: PASS.

- [ ] **Step 9: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add capability/calendar
git commit -m "Show a queued create under its client key, open it in the detail sheet, and name where new events go"
```

---

### Task 5: The editor adds and changes events — `create`, `update`, the Who rule, and the `createdBy` re-read

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarPermissions.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt` (rewrite)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarPermissionsTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt` (modify)

**Interfaces:**
- Consumes: `CalendarWriter.create(…, clientKey)`, `newClientKey()` (Task 2); `ScriptedWriter.created`, `ScriptedWriter.loseNextReply` (Task 2); `PendingChange.clientKey` (Task 1); `PendingChange.asCreatedEvent`, `writableMaster` (Task 4); `callWriter`, `CalendarStore.applyAcceptedWrite` (`Writes.kt`, 2b-1); `mayChange`, `cannotChangeOthers` (2b-1).
- Produces:
  - `suspend fun CalendarEditor.create(draft: EventDraft): EditResult` — adds to the writable master; `draft.createdBy` is replaced by the authorised person; `NotEditable` when there is no writable master with a writer
  - `suspend fun CalendarEditor.update(ref: EventRef, draft: EventDraft): EditResult` — authorises `edit`/`edit.own` with `PinReason.Edit`; a change of who also needs `assign` or the add rule (`mayRetag`), and is refused with `cannotAddForOthers(name)`; sends only the title, times and who, keeping the event's current creator (`draft.createdBy` is ignored); queued behind any pending change for `ref`
  - `mayDelete`, `delete`, `assign` and `update` accept a queued create's ref (`EventRef(conn, source, clientKey)`) as their target
  - after authorising, `update` and `delete` re-read the event and its queue under the write lock and check `mayChange` again with the fresh creator; if it now fails they toast `cannotChangeOthers(name)`, sign the person out and return `Cancelled`
  - one private `attempt(to, kind, remoteId, draft, clientKey)` for every kind: a write the provider accepted is `Done` even if storing it in the mirror then fails (logged; the sync every write asks for mirrors it)
  - `const val EVENT_ADDED = "Event added"`, `const val CHANGES_SAVED = "Changes saved"`
  - `internal val CalendarEditor.session: StateFlow<Identified?>` (the live session, for the Who defaults and the disabled Who chips)
  - `internal suspend fun CalendarEditor.openedAt(): ZonedDateTime` (now, in the household zone)
  - `internal fun CalendarEditor.refuseOtherWho(name: String)` (toasts `cannotAddForOthers(name)`)
  - the internal constructor gains a last parameter `newKey: () -> String = ::newClientKey`
  - `fun cannotAddForOthers(name: String): String` = "{name} can only add events for themselves."; `internal fun mayCreateFor(granted: Set<String>, who: Identified, forPerson: String?): Boolean`; `internal fun mayRetag(granted: Set<String>, who: Identified, forPerson: String?): Boolean`
  - reporting: Done/Queued → `EVENT_DELETED` (delete), `EVENT_ADDED` (create), `CHANGES_SAVED` (update); a save's `Rejected` is not toasted while its caller waits (the sheet shows it) but is toasted if the caller has gone

- [ ] **Step 1: Write the failing permission tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarPermissionsTest.kt` (it already has `mia` and the imports), add at the end of the class:
```kotlin
    @Test
    fun createAddsForAnyoneAndCreateSelfOnlyForYourself() {
        assertThat(mayCreateFor(setOf(CalendarPermissions.CREATE), mia, PersonId.FAMILY.value)).isTrue()
        assertThat(mayCreateFor(setOf(CalendarPermissions.CREATE_SELF), mia, "mia")).isTrue()
        assertThat(mayCreateFor(setOf(CalendarPermissions.CREATE_SELF), mia, PersonId.FAMILY.value)).isFalse()
        assertThat(mayCreateFor(setOf(CalendarPermissions.CREATE_SELF), mia, null)).isFalse()
    }

    @Test
    fun changingWhoNeedsAssignOrTheAddRule() {
        assertThat(mayRetag(setOf(CalendarPermissions.ASSIGN), mia, PersonId.FAMILY.value)).isTrue()
        assertThat(mayRetag(setOf(CalendarPermissions.EDIT_OWN, CalendarPermissions.CREATE_SELF), mia, "mia")).isTrue()
        assertThat(mayRetag(setOf(CalendarPermissions.EDIT_OWN, CalendarPermissions.CREATE_SELF), mia, PersonId.FAMILY.value)).isFalse()
    }

    @Test
    fun theAddRefusalNamesThePerson() {
        assertThat(cannotAddForOthers("Mia")).isEqualTo("Mia can only add events for themselves.")
    }
```

- [ ] **Step 2: Write the failing editor tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt`:

1. Add these imports:
```kotlin
import uk.co.siland.culvery.core.household.PersonId
```
2. Replace the `editor` builder, including its KDoc, with:
```kotlin
    private var keys = 0

    /**
     * The editor shares access's toaster, as the app shares one. [io] is the writer's context: tests that hold a
     * write open use Dispatchers.Default, so its 10 s timeout runs on real time and runTest can't skip past it
     * while the test waits for Room. Client keys are "key-1", "key-2"… in the order Save is tapped.
     */
    private fun TestScope.editor(access: TestAccess, io: CoroutineContext = EmptyCoroutineContext) = CalendarEditor(
        store = store,
        writers = setOf(writer),
        access = access.control,
        toaster = access.toasts,
        zone = HouseholdZone(household),
        clock = WallClock { testScheduler.currentTime },
        scope = backgroundScope,
        requestSync = { syncRequests++ },
        io = io,
        attemptMillis = WRITE_ATTEMPT_MS,
        newKey = { "key-${++keys}" },
    )

    /** The outbox drain as the sync loop runs it, [aheadMillis] after the test's clock, with this test's writer. */
    private fun TestScope.drain(access: TestAccess, aheadMillis: Long) = CalendarSync(
        store, emptySet(), HouseholdZone(household), WallClock { testScheduler.currentTime + aheadMillis },
        EmptyCoroutineContext, PROVIDER_TIMEOUT_MS, setOf(writer), access.toasts,
    )

    /** A one-hour event on Sunday 27 September at 18:00, as the add sheet builds it. */
    private fun draft(title: String, forPerson: String?, createdBy: String? = null): EventDraft {
        val start = LocalDate.of(2026, 9, 27).atTime(18, 0).atZone(london).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), forPerson, createdBy)
    }

    /** A change to an event, as the edit sheet sends it; its createdBy is ignored by the editor. */
    private fun edited(title: String, forPerson: String? = null): EventDraft {
        val start = LocalDate.of(2026, 9, 23).atTime(20, 0).atZone(london).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), forPerson, createdBy = "ignored")
    }

    /** Waits, in bounded real time, for the PIN pad: the editor reads Room on its own threads before asking. */
    private suspend fun waitForThePad(access: TestAccess) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (access.requests.isEmpty()) delay(10) } }
```
3. A write the provider accepted is now done even when the tablet can't store it (2b-2 design §3.4). Replace `aDatabaseFailureAfterTheProviderAcceptsIsToldNotThrown` with:
```kotlin
    @Test
    fun aDeleteTheProviderAcceptedIsDoneEvenIfTheTabletCantStoreIt() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        failEvery("DELETE", "event")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("delete:dinner")
        assertThat(access.toasts.messages).containsExactly(EVENT_DELETED)
        // The sync this asks for brings the mirror up to date.
        assertThat(syncRequests).isEqualTo(1)
    }
```
4. Add these tests at the end of the class:
```kotlin
    @Test
    fun adultCanAddAnEventForAnyoneAndIsRecordedAsItsCreator() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.SAM)
        val result = editor(access).create(draft("Sleepover", access.mia.id.value, createdBy = "someone-else"))
        assertThat(result).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("create:Sleepover")
        assertThat(writer.drafts.single().let { it.forPerson to it.createdBy }).isEqualTo(access.mia.id.value to access.sam.id.value)
        val stored = store.eventNow(ref("key-1"))!!
        assertThat(stored.title to stored.createdBy).isEqualTo("Sleepover" to access.sam.id.value)
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Save)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
        assertThat(syncRequests).isEqualTo(1)
    }

    @Test
    fun anAdminCanAddForFamily() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Pizza night", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
    }

    @Test
    fun childCanAddAnEventForThemselves() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.MIA)
        assertThat(editor(access).create(draft("Sleepover", access.mia.id.value))).isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().createdBy).isEqualTo(access.mia.id.value)
    }

    @Test
    fun childIsRefusedAddingForFamilyAndIsToldWhy() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.MIA)
        assertThat(editor(access).create(draft("Pizza night", PersonId.FAMILY.value))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        assertThat(writer.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aSignedInChildIsRefusedAddingForOthersWithoutAPinPadAndIsSignedOut() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.MIA)
        val editor = editor(access)
        assertThat(editor.create(draft("Sleepover", access.mia.id.value))).isEqualTo(EditResult.Done)
        assertThat(editor.create(draft("Pizza night", PersonId.FAMILY.value))).isEqualTo(EditResult.Cancelled)
        assertThat(access.requests).hasSize(1)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED, "Mia can only add events for themselves.").inOrder()
        assertThat(access.control.session.value).isNull()
        assertThat(writer.calls).containsExactly("create:Sleepover")
    }

    @Test
    fun aRefusedAddChangesNothingAndLeavesTheSayingToTheSheet() = runTest {
        val access = testAccess(household)
        writer.failWith = WriteRejectedException("Calendar is full")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Rejected("Calendar is full"))
        assertThat(store.eventNow(ref("key-1"))).isNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun aCreateAcceptedButNotStoredIsDoneAndMakesOneEvent() = runTest {
        val access = testAccess(household)
        // The provider accepts the create, then the tablet can't store it (a full disk).
        failEvery("INSERT", "event")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        assertThat(writer.created.keys).containsExactly("key-1")
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
        assertThat(syncRequests).isEqualTo(1)

        // Once the disk has room, the sync the editor asked for mirrors the event the provider holds.
        calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_event") }
        val reads = ScriptedProvider("calendar.a").apply {
            events = { source -> if (source.id == family.id) writer.created.values.toList() else emptyList() }
        }
        CalendarSync(
            store, setOf(reads), HouseholdZone(household), WallClock { LocalDate.of(2026, 9, 23).atStartOfDay(london).toInstant().toEpochMilli() },
            EmptyCoroutineContext, PROVIDER_TIMEOUT_MS, setOf(writer), access.toasts,
        ).syncAll()
        assertThat(store.eventNow(ref("key-1"))!!.title).isEqualTo("Sleepover")
        assertThat(writer.calls).containsExactly("create:Sleepover")
    }

    @Test
    fun anOfflineAddIsQueuedWithItsKey() = runTest {
        val access = testAccess(household)
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        val queued = store.pendingNow().single()
        assertThat(listOf(queued.kind, queued.clientKey, queued.remoteId, queued.attempts))
            .containsExactly(ChangeKind.CREATE, "key-1", null, 1).inOrder()
        assertThat(queued.ref).isEqualTo(ref("key-1"))
        assertThat(queued.draft?.createdBy).isEqualTo(access.alex.id.value)
        assertThat(queued.nextAttemptMillis).isEqualTo(testScheduler.currentTime + OUTBOX_BACKOFF_MS.first())
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
    }

    @Test
    fun aTimeoutAfterTheProviderCreatedTheEventMakesOneEventAfterTheDrain() = runTest {
        val access = testAccess(household)
        writer.loseNextReply = true
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        // The provider made the event before its reply was lost; the tablet only knows it timed out.
        assertThat(writer.created.keys).containsExactly("key-1")
        assertThat(store.pendingNow().single().clientKey).isEqualTo("key-1")

        drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first()).syncAll()
        assertThat(writer.calls).containsExactly("create:Sleepover", "create:Sleepover")
        assertThat(writer.created.keys).containsExactly("key-1")
        assertThat(store.eventNow(ref("key-1"))!!.title).isEqualTo("Sleepover")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun eachSaveUsesANewKey() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        editor.create(draft("Sleepover", PersonId.FAMILY.value))
        editor.create(draft("Sleepover", PersonId.FAMILY.value))
        assertThat(writer.created.keys).containsExactly("key-1", "key-2").inOrder()
    }

    @Test
    fun closingTheSheetBeforeARefusalToastsIt() = runTest {
        val access = testAccess(household)
        writer.gate = CompletableDeferred()
        writer.failWith = WriteRejectedException("Calendar is full")
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val sheet = launch { editor.create(draft("Sleepover", PersonId.FAMILY.value)) }
        writer.entered.await()
        sheet.cancel()
        writer.gate?.complete(Unit)
        // Nothing shows the failure card any more, so the editor says it.
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (access.toasts.messages.isEmpty()) delay(10) } }
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Calendar is full")
    }

    @Test
    fun aMasterWithoutAWriterCanNotBeAddedToAndAsksForNoPin() = runTest {
        val access = testAccess(household)
        val noWriter = CalendarEditor(
            store, emptySet(), access.control, access.toasts, HouseholdZone(household),
            WallClock { testScheduler.currentTime }, backgroundScope, {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,
        )
        assertThat(noWriter.create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.NotEditable)
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun adultCanChangeAnyMasterEventAndItsCreatorIsKept() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.SAM)
        assertThat(editor(access).update(ref("dinner"), edited("Dinner at Gran's", access.sam.id.value))).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("update:dinner")
        assertThat(writer.drafts.single().createdBy).isEqualTo(access.alex.id.value)
        val stored = store.eventNow(ref("dinner"))!!
        assertThat(listOf(stored.title, stored.forPerson, stored.createdBy))
            .containsExactly("Dinner at Gran's", access.sam.id.value, access.alex.id.value).inOrder()
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Edit)
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
        assertThat(syncRequests).isEqualTo(1)
    }

    @Test
    fun childCanChangeTheirOwnEvent() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).update(ref("football"), edited("Football at the park"))).isEqualTo(EditResult.Done)
    }

    @Test
    fun childCannotChangeSomeoneElsesEventAndIsToldWhy() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).update(ref("dinner"), edited("Pizza"))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun childCannotMoveTheirOwnEventToSomeoneElseAndIsToldWhy() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value, forPerson = access.mia.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).update(ref("football"), edited("Football", PersonId.FAMILY.value))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun childCanChangeTheirOwnEventWhileWhoStaysAsItWas() = runTest {
        val access = testAccess(household)
        // An adult tagged Mia's event for Family; she can still rename it while Who stays on Family.
        put(event("football", createdBy = access.mia.id.value, forPerson = PersonId.FAMILY.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).update(ref("football"), edited("Football at the park", PersonId.FAMILY.value)))
            .isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().forPerson).isEqualTo(PersonId.FAMILY.value)
    }

    @Test
    fun aRefusedChangeLeavesTheSayingToTheSheet() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("Pizza"))).isEqualTo(EditResult.Rejected("Event is locked"))
        assertThat(store.eventNow(ref("dinner"))!!.title).isEqualTo("dinner")
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun closingTheSheetBeforeARefusedChangeToastsIt() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.gate = CompletableDeferred()
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val sheet = launch { editor.update(ref("dinner"), edited("Pizza")) }
        writer.entered.await()
        sheet.cancel()
        writer.gate?.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (access.toasts.messages.isEmpty()) delay(10) } }
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Event is locked")
    }

    @Test
    fun anOfflineChangeIsQueuedAndSaysSaved() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("Pizza"))).isEqualTo(EditResult.Queued)
        val queued = store.pendingNow().single()
        assertThat(listOf(queued.kind, queued.remoteId, queued.draft?.title)).containsExactly(ChangeKind.UPDATE, "dinner", "Pizza").inOrder()
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
    }

    @Test
    fun aChangeAndADeleteOfAQueuedCreateQueueBehindItAndDrainInOrder() = runTest {
        val access = testAccess(household)
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(draft("Sleepover", access.mia.id.value))).isEqualTo(EditResult.Queued)
        writer.failWith = null
        val created = ref("key-1")
        assertThat(editor.mayDelete(created)).isTrue()
        assertThat(editor.update(created, edited("Sleepover at Ava's", access.mia.id.value))).isEqualTo(EditResult.Queued)
        assertThat(editor.delete(created)).isEqualTo(EditResult.Queued)
        // Nothing goes past the queued create: it would reach the provider before the event exists.
        assertThat(writer.calls).containsExactly("create:Sleepover")
        val queued = store.pendingNow()
        assertThat(queued.map { it.kind }).containsExactly(ChangeKind.CREATE, ChangeKind.UPDATE, ChangeKind.DELETE).inOrder()
        assertThat(queued.map { it.ref }).containsExactly(created, created, created)
        assertThat(queued[1].draft?.createdBy).isEqualTo(access.alex.id.value)
        assertThat(access.requests).hasSize(1)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED, CHANGES_SAVED, EVENT_DELETED).inOrder()

        drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first()).syncAll()
        assertThat(writer.calls)
            .containsExactly("create:Sleepover", "create:Sleepover", "update:key-1", "delete:key-1").inOrder()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.eventNow(created)).isNull()
    }

    @Test
    fun theCreatorIsCheckedAgainAfterThePinPadForAChange() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value))
        val editor = editor(access)
        val result = async { editor.update(ref("football"), edited("Football at the park")) }
        waitForThePad(access)
        // While the pad is up, a sync brings the event back as someone else's.
        put(event("football", createdBy = access.alex.id.value))
        access.prompt.submit(TestAccess.MIA)
        assertThat(result.await()).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(writer.calls).isEmpty()
        // Signed out, as a refusal on the session shortcut would be (2b-1 U2).
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun theCreatorIsCheckedAgainAfterThePinPadForADelete() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value))
        val editor = editor(access)
        val result = async { editor.delete(ref("football")) }
        waitForThePad(access)
        put(event("football", createdBy = access.alex.id.value))
        access.prompt.submit(TestAccess.MIA)
        assertThat(result.await()).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(writer.calls).isEmpty()
        assertThat(store.eventNow(ref("football"))).isNotNull()
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun anEventQueuedForDeletionOrGoneCanNotBeChanged() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        assertThat(editor.update(ref("dinner"), edited("Pizza"))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.update(ref("nope"), edited("Pizza"))).isEqualTo(EditResult.NotEditable)
        assertThat(access.requests).hasSize(1)
    }
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarPermissionsTest*" --tests "*CalendarEditorTest*"`
Expected: compilation FAILS: `mayCreateFor`, `mayRetag`, `cannotAddForOthers`, `create`, `update`, `EVENT_ADDED`, `CHANGES_SAVED` and the `newKey` parameter are unresolved.

- [ ] **Step 4: Add the create and Who rules and their wording**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarPermissions.kt`, after `mayChange`, add:
```kotlin

/** Adding needs create, or create.self for an event that is for this person (2b-2 design D7). */
internal fun mayCreateFor(granted: Set<String>, who: Identified, forPerson: String?): Boolean =
    CalendarPermissions.CREATE in granted ||
        (CalendarPermissions.CREATE_SELF in granted && forPerson == who.person.id.value)

/** An edit that changes who follows the add rule (2b-2 design §6): assign or create may tag anyone. */
internal fun mayRetag(granted: Set<String>, who: Identified, forPerson: String?): Boolean =
    CalendarPermissions.ASSIGN in granted || mayCreateFor(granted, who, forPerson)
```
and after `fun cannotChangeOthers(…)`, add:
```kotlin

/** Hand-off §7 refusal wording for a child adding an event for someone else, or moving one to someone else. */
fun cannotAddForOthers(name: String): String = "$name can only add events for themselves."
```

- [ ] **Step 5: Add `create` and `update` to the editor**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** How long the editor tries the provider before queueing the change instead (2b-1 design D3). */
const val WRITE_ATTEMPT_MS = 10_000L

/** The reason shown when the tablet itself fails to save a change: nothing the provider said. */
internal const val TRY_AGAIN = "try again"

/** Hand-off §7: the toast after a delete. */
const val EVENT_DELETED = "Event deleted"

/** 2b-2 design D1: the toasts after a save. */
const val EVENT_ADDED = "Event added"
const val CHANGES_SAVED = "Changes saved"

sealed interface EditResult {
    /** The provider accepted the change. The mirror has it, or the next sync brings it (2b-2 design §3.4). */
    data object Done : EditResult

    /** The provider couldn't be reached in time: the change is queued and shows as syncing. */
    data object Queued : EditResult

    /**
     * The provider refused the change for good, or the tablet failed before the provider accepted it. For a delete
     * or an assign the editor has toasted why. For a save the sheet shows it; if the sheet has closed, the editor
     * toasts it.
     */
    data class Rejected(val message: String) : EditResult

    /** The PIN pad was cancelled, or the person was refused and told so by a toast. */
    data object Cancelled : EditResult

    /** The event is gone, queued for deletion, or can't be changed here; or there is no master calendar to add to. */
    data object NotEditable : EditResult
}

/**
 * Adds and changes master-calendar events on the tablet: authorises, tries the provider for up to [attemptMillis],
 * and queues the change in the outbox when the provider can't be reached. It reports outcomes as toasts itself,
 * and every write nudges the sync loop. A queued create counts as an event (2b-2 design D6): its changes queue
 * behind it, and in-order delivery with idempotent creates makes that correct.
 */
@Singleton
class CalendarEditor internal constructor(
    private val store: CalendarStore,
    private val writers: Set<@JvmSuppressWildcards CalendarWriter>,
    private val access: AccessControl,
    private val toaster: Toaster,
    private val zone: HouseholdZone,
    private val clock: WallClock,
    private val scope: CoroutineScope,
    private val requestSync: () -> Unit,
    private val io: CoroutineContext,
    private val attemptMillis: Long,
    private val newKey: () -> String = ::newClientKey,
) {
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
    ) : this(store, writers, access, toaster, zone, clock, scope, loop::requestSync, Dispatchers.IO, WRITE_ATTEMPT_MS)

    // One write at a time, so two sheets on one event can't send their changes out of order. Held only around the
    // write itself, never while the PIN pad is up.
    private val writeLock = Mutex()

    /** Who is signed in now: the add/edit sheet's Who default and its disabled chips (2b-2 design D2). */
    internal val session: StateFlow<Identified?> get() = access.session

    /** Now, in the household zone: the add sheet's "today" and its default time. */
    internal suspend fun openedAt(): ZonedDateTime = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone.current())

    /** A signed-in child tapped someone else's Who chip: say why it is disabled (2b-2 design §4.2). */
    internal fun refuseOtherWho(name: String) = toaster.show(cannotAddForOthers(name))

    /** The delete guard, run before the confirmation appears: true when this person may delete [ref]. */
    suspend fun mayDelete(ref: EventRef): Boolean {
        val target = resolve(ref) ?: return false
        return authoriseChange(target, PinReason.Delete) != null
    }

    /** Deletes [ref]. Authorises again, which passes silently while the session started by [mayDelete] lasts. */
    suspend fun delete(ref: EventRef): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        val who = authoriseChange(target, PinReason.Delete) ?: return EditResult.Cancelled
        return write(target, ChangeKind.DELETE, who)
    }

    /** Tags [ref] as being for [person] (hand-off: "Assign to…"). Adults only. */
    suspend fun assign(ref: EventRef, person: PersonId): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        if (target.pending.any { it.kind == ChangeKind.DELETE }) return EditResult.NotEditable
        access.authorise(
            CalendarPermissions.ASSIGN,
            reason = PinReason.Assign,
            refusal = Refusal.Toast { ASK_AN_ADULT },
        ) ?: return EditResult.Cancelled
        return write(target, ChangeKind.ASSIGN, who = null, forPerson = person.value)
    }

    /**
     * Adds [draft] to the master calendar (2b-2 design §3.3). Adults may add for anyone; a child only for themselves.
     * [EventDraft.createdBy] is ignored: the person who authorises is recorded. Each call chooses a new client key,
     * which a queued create keeps for every retry.
     */
    suspend fun create(draft: EventDraft): EditResult {
        val to = master() ?: return EditResult.NotEditable
        val who = access.authorise(
            CalendarPermissions.CREATE,
            CalendarPermissions.CREATE_SELF,
            reason = PinReason.Save,
            allow = { person, granted -> mayCreateFor(granted, person, draft.forPerson) },
            refusal = Refusal.Toast(::cannotAddForOthers),
        ) ?: return EditResult.Cancelled
        val key = newKey()
        val toSend = draft.copy(createdBy = who.person.id.value)
        return onAppScope(ChangeKind.CREATE, to.connection.label) {
            writeLock.withLock { attempt(to, ChangeKind.CREATE, remoteId = null, toSend, clientKey = key) }
        }
    }

    /**
     * Changes [ref]'s title, times and who to [draft]'s (2b-2 design D7); everything else the provider holds is kept.
     * The event's creator is kept too: [EventDraft.createdBy] is ignored. Needs edit, or edit.own on an event this
     * person created, checked again once the PIN pad has closed. A change of who follows the add rule (§6).
     */
    suspend fun update(ref: EventRef, draft: EventDraft): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        if (target.pending.any { it.kind == ChangeKind.DELETE }) return EditResult.NotEditable
        val retags = draft.forPerson != forPersonOf(target.event, target.pending)
        val who = authoriseChange(target, PinReason.Edit, retags, draft.forPerson) ?: return EditResult.Cancelled
        return write(target, ChangeKind.UPDATE, who, edit = draft)
    }

    /** Where a change goes: a connection, one of its sources, and the writer for its provider. */
    private class Destination(val connection: Connection, val source: CalendarSource, val writer: CalendarWriter)

    private class Target(val event: StoredEvent, val to: Destination, val pending: List<PendingChange>)

    /** The writable master calendar, where new events go (2b-2 design §6); null if there is none, or no writer. */
    private suspend fun master(): Destination? {
        val master = writableMaster(store.master().first(), store.connectionsNow(), writers.map { it.providerId }.toSet())
            ?: return null
        return destination(master.connection.id, master.source.source)
    }

    private suspend fun destination(connectionId: String, source: CalendarSource): Destination? {
        val connection = store.connectionsNow().firstOrNull { it.connection.id == connectionId }?.connection ?: return null
        // A provider that declares WRITE without binding a writer fails the contract suite, and the repository logs it.
        val writer = writers.firstOrNull { it.providerId == connection.providerId } ?: return null
        return Destination(connection, source, writer)
    }

    /** [ref] if the tablet may change it: an event in the mirror, or a queued create not yet synced. */
    private suspend fun resolve(ref: EventRef): Target? {
        val source = store.source(ref.connectionId, ref.sourceId) ?: return null
        val pending = store.pendingNow().filter { it.ref == ref }
        val event = store.eventNow(ref) ?: queuedCreate(pending, source.mapping.person, zone.current()) ?: return null
        val to = destination(ref.connectionId, source.source) ?: return null
        if (readOnlyReason(event, source, hasWriter = true) != null) return null
        return Target(event, to, pending)
    }

    /**
     * Edit, or edit.own on an event this person created. When the change [retags] the event to [forPerson], the
     * person also needs assign or the add rule, and a refusal on that says so instead.
     */
    private suspend fun authoriseChange(
        target: Target,
        reason: PinReason,
        retags: Boolean = false,
        forPerson: String? = null,
    ): Authorised? {
        val createdBy = createdByOf(target.event, target.pending)
        var refusedWho = false
        return access.authorise(
            *(if (retags) CHANGE_AND_RETAG else CHANGE),
            reason = reason,
            allow = { who, granted ->
                val mayEdit = mayChange(granted, who, createdBy)
                refusedWho = mayEdit && retags && !mayRetag(granted, who, forPerson)
                mayEdit && !refusedWho
            },
            refusal = Refusal.Toast { name -> if (refusedWho) cannotAddForOthers(name) else cannotChangeOthers(name) },
        )
    }

    /**
     * Runs [block] on the application scope, so closing the sheet mid-write can't lose the change or its toast, then
     * reports the outcome and nudges the sync loop. A store failure (disk full, corruption) becomes
     * Rejected(TRY_AGAIN) instead of reaching the sheet's scope and killing the app. A save's refusal is the sheet's
     * to show; if the sheet stops waiting (it was closed), the editor toasts it instead.
     */
    private suspend fun onAppScope(kind: ChangeKind, label: String, block: suspend () -> EditResult): EditResult {
        val job = scope.async {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save a $kind", e)
                EditResult.Rejected(TRY_AGAIN)
            }
            report(kind, label, result)
            // A sync pass already in flight may briefly put back the old mirror; the pass this asks for corrects it.
            requestSync()
            result
        }
        return try {
            job.await()
        } catch (e: CancellationException) {
            if (kind == ChangeKind.CREATE || kind == ChangeKind.UPDATE) {
                scope.launch { (job.await() as? EditResult.Rejected)?.let { toaster.show(couldNotSave(label, it.message)) } }
            }
            throw e
        }
    }

    /**
     * Under [writeLock] it reads the queue and the event afresh. A change behind a pending one for the same event
     * (a queued create included) is queued after it rather than sent directly, where it could land first and be
     * undone. [who] is checked again against the event's creator as it is now: it may have changed while the PIN pad
     * was up.
     */
    private suspend fun write(
        target: Target,
        kind: ChangeKind,
        who: Authorised?,
        forPerson: String? = null,
        edit: EventDraft? = null,
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
                    val draft = draftFor(kind, event, pending, forPerson, edit)
                    if (pending.isNotEmpty()) {
                        queue(target.to, kind, ref.remoteId, draft, attempted = false)
                    } else {
                        attempt(target.to, kind, ref.remoteId, draft)
                    }
                }
            }
        }
    }

    /**
     * No draft for a delete. An assign sends the event as it is now, with the new person. An update sends the
     * sheet's title, times and who, with the event's own creator.
     */
    private fun draftFor(
        kind: ChangeKind,
        event: StoredEvent,
        pending: List<PendingChange>,
        forPerson: String?,
        edit: EventDraft?,
    ): EventDraft? = when (kind) {
        ChangeKind.DELETE -> null
        ChangeKind.ASSIGN -> assignDraft(event, forPerson)
        ChangeKind.UPDATE -> checkNotNull(edit) { "An update needs its draft" }.copy(createdBy = createdByOf(event, pending))
        ChangeKind.CREATE -> error("A create has no event to change")
    }

    /**
     * Tries the provider once. A create sends its [clientKey], and a queued create keeps it: if the provider did make
     * the event, the retry gets that event back, not a second. Once the provider has accepted, the change is done
     * even if the tablet then fails to store it; the sync every write asks for mirrors it (2b-2 design §3.4).
     */
    private suspend fun attempt(
        to: Destination,
        kind: ChangeKind,
        remoteId: String?,
        draft: EventDraft?,
        clientKey: String? = null,
    ): EditResult {
        val outcome = callWriter(io, attemptMillis) {
            when (kind) {
                ChangeKind.CREATE -> to.writer.create(to.connection, to.source, checkNotNull(draft), checkNotNull(clientKey))
                ChangeKind.DELETE -> {
                    to.writer.delete(to.connection, to.source, checkNotNull(remoteId))
                    null
                }
                ChangeKind.UPDATE, ChangeKind.ASSIGN -> to.writer.update(to.connection, to.source, checkNotNull(remoteId), checkNotNull(draft))
            }
        }
        return when (outcome) {
            is WriteOutcome.Accepted -> {
                try {
                    store.applyAcceptedWrite(to.connection.id, to.source.id, remoteId, outcome, zone.current())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "The provider accepted a $kind but the tablet couldn't store it; the next sync will", e)
                }
                EditResult.Done
            }
            is WriteOutcome.Rejected -> EditResult.Rejected(outcome.message)
            is WriteOutcome.Retry -> queue(to, kind, remoteId, draft, attempted = true, clientKey = clientKey)
        }
    }

    /** An [attempted] change waits out the first backoff; one queued behind others is due at the next pass. */
    private suspend fun queue(
        to: Destination,
        kind: ChangeKind,
        remoteId: String?,
        draft: EventDraft?,
        attempted: Boolean,
        clientKey: String? = null,
    ): EditResult {
        val now = clock.nowMillis()
        store.enqueue(
            PendingChange(
                id = 0,
                connectionId = to.connection.id,
                sourceId = to.source.id,
                remoteId = remoteId,
                kind = kind,
                draft = draft,
                attempts = if (attempted) 1 else 0,
                nextAttemptMillis = if (attempted) now + backoffMillis(1) else now,
                createdMillis = now,
                clientKey = clientKey,
            ),
        )
        return EditResult.Queued
    }

    /** A queued change already shows (a delete hides the event), so it reads as done too. */
    private fun report(kind: ChangeKind, label: String, result: EditResult) {
        val saved = result == EditResult.Done || result == EditResult.Queued
        when {
            result is EditResult.Rejected && (kind == ChangeKind.DELETE || kind == ChangeKind.ASSIGN) ->
                toaster.show(couldNotSave(label, result.message))
            saved && kind == ChangeKind.DELETE -> toaster.show(EVENT_DELETED)
            saved && kind == ChangeKind.CREATE -> toaster.show(EVENT_ADDED)
            saved && kind == ChangeKind.UPDATE -> toaster.show(CHANGES_SAVED)
        }
    }

    private companion object {
        const val TAG = "CalendarEditor"

        /** What a change asks for; one that changes who also counts assign and the create permissions (§6). */
        val CHANGE = arrayOf(CalendarPermissions.EDIT, CalendarPermissions.EDIT_OWN)
        val CHANGE_AND_RETAG = CHANGE + arrayOf(CalendarPermissions.ASSIGN, CalendarPermissions.CREATE, CalendarPermissions.CREATE_SELF)
    }
}

/** A queued create stands in for the mirror's row until it syncs. */
private fun queuedCreate(pending: List<PendingChange>, sourcePerson: PersonId, zone: ZoneId): StoredEvent? =
    pending.firstNotNullOfOrNull { it.asCreatedEvent(sourcePerson, zone) }

/** Who made the event once its queued edits land; an assign never changes it. */
private fun createdByOf(event: StoredEvent, pending: List<PendingChange>): String? {
    val edit = pending.lastOrNull { it.kind == ChangeKind.UPDATE }?.draft
    return if (edit != null) edit.createdBy else event.createdBy
}

/** Who the event is for once its queued edits and assigns land: what the edit sheet showed. */
private fun forPersonOf(event: StoredEvent, pending: List<PendingChange>): String? {
    val last = pending.lastOrNull { it.kind == ChangeKind.UPDATE || it.kind == ChangeKind.ASSIGN }?.draft
    return if (last != null) last.forPerson else event.forPerson
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarPermissionsTest*" --tests "*CalendarEditorTest*"`
Expected: PASS, the 2b-1 editor tests included.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. `StubEditor` and the hosts' tests build the editor with ten positional arguments; the new `newKey` parameter has a default, so they compile unchanged.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar
git commit -m "Add and change events through the editor: the create and Who rules, a key per save, and the creator re-read after the PIN"
```

---

### Task 6: `EventForm` — the add/edit sheet's state, defaults, summary and draft

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/EventForm.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/EventFormTest.kt` (create; Robolectric)

**Interfaces:**
- Consumes: `EditableEvent` (Task 4); `whenLabel`, `EventTime`, `EventDraft`, `instantIn` (2a/2b-1).
- Produces (all in `uk.co.siland.culvery.capability.calendar`):
  - `enum class TimeSlot(val label: String, val time: LocalTime) { Morning(09:00), Afternoon(14:00), Evening(18:00) }`
  - `sealed interface TimeChoice { data object AllDay; data class Slot(val slot: TimeSlot); data class Custom(val time: LocalTime) }`
  - `const val MAX_TITLE_LENGTH = 100`; `val LENGTH_CHOICES: List<Duration>` (30 min, 1 h, 2 h); `fun lengthLabel(length: Duration): String`
  - `class EventForm(mode: EventForm.Mode, today: LocalDate, now: LocalTime, zone: ZoneId, signedIn: PersonId?, preselectedDay: LocalDate?)` with
    - `sealed interface Mode { data object New; data class Edit(val original: EditableEvent) }`
    - state: `title: String`, `who: PersonId`, `day: LocalDate`, `time: TimeChoice`, `length: Duration` (public getters, private setters)
    - `updateTitle(String)`, `chooseWho(PersonId)`, `chooseDay(LocalDate)`, `chooseTime(TimeChoice)`, `chooseLength(Duration)`
    - `val today: LocalDate`, `val zone: ZoneId`, `val mode: Mode`, `val datesLocked: Boolean`, `val lengths: List<Duration>`, `val dayChoices: List<LocalDate>` (today and the next six days)
    - `val canSave: Boolean`, `val unchanged: Boolean`, `val startTime: LocalTime?`, `val pickerTime: LocalTime`, `val pickedDateLabel: String?`, `val lockedDatesLabel: String?`
    - `fun summary(nameOf: (PersonId) -> String?): String` (a null name, a tag whose person has left, ends the line at the time), `fun draft(createdBy: String?): EventDraft`
    - `chooseWho` of the person already chosen changes nothing; an edit whose Day, Time and Length are untouched drafts the event's own start and end; a timed draft ends at the next midnight at the latest; an event's own length is a chip only when positive
  - `CalendarUi.kt`: `SHORT_DAY` becomes `internal`; `internal fun daySpan(start: EventTime, end: EventTime, zone: ZoneId): Pair<LocalDate, LocalDate>`, the midnight-end rule, is shared by `whenLabel` and `EventForm`

- [ ] **Step 1: Write the failing tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/EventFormTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.PersonId

/**
 * Wednesday 23 September 2026 in London. The form has no Android types (2b-2 design §3.1); Robolectric runs it only
 * because Compose's Android runtime backs `mutableStateOf` with a Parcelable.
 */
@RunWith(AndroidJUnit4::class)
class EventFormTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val alex = PersonId("alex")
    private val mia = PersonId("mia")
    private val names = mapOf(PersonId.FAMILY to "Family", alex to "Alex", mia to "Mia")

    private fun new(now: String = "10:54", day: LocalDate? = null, signedIn: PersonId? = null, on: LocalDate = today) =
        EventForm(EventForm.Mode.New, on, LocalTime.parse(now), london, signedIn, day)

    private fun edit(start: EventTime, end: EventTime, title: String = "Dinner with Jo & Priya", forPerson: String? = "alex") =
        EventForm(
            EventForm.Mode.Edit(EditableEvent(EventRef("c1", "s1", "e1"), title, start, end, forPerson)),
            today, LocalTime.of(10, 54), london, signedIn = null, preselectedDay = null,
        )

    private fun editTimed(start: LocalDateTime, minutes: Long, forPerson: String? = "alex"): EventForm {
        val from = start.atZone(london).toInstant()
        return edit(EventTime.Timed(from), EventTime.Timed(from.plusSeconds(minutes * 60)), forPerson = forPerson)
    }

    private fun EventForm.summary() = summary { names.getValue(it) }

    private fun slot(s: TimeSlot) = TimeChoice.Slot(s)

    @Test
    fun theDefaultTimeIsTheNextSlotStillToComeToday() {
        val expected = mapOf(
            "08:59" to slot(TimeSlot.Morning),
            "09:00" to slot(TimeSlot.Afternoon),
            "13:59" to slot(TimeSlot.Afternoon),
            "14:00" to slot(TimeSlot.Evening),
            "17:59" to slot(TimeSlot.Evening),
            "18:00" to TimeChoice.AllDay,
            "23:30" to TimeChoice.AllDay,
        )
        expected.forEach { (now, time) -> assertThat(new(now).time).isEqualTo(time) }
    }

    @Test
    fun aLaterDayDefaultsToMorningAndAWeekColumnPresetsIt() {
        val form = new(now = "20:00", day = today.plusDays(2))
        assertThat(form.day).isEqualTo(today.plusDays(2))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Morning))
        assertThat(form.length).isEqualTo(Duration.ofHours(1))
    }

    @Test
    fun changingTheDayReappliesTheDefaultUntilTimeIsTouched() {
        val form = new(now = "20:00")
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        form.chooseDay(today.plusDays(1))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Morning))
        form.chooseDay(today)
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        form.chooseTime(slot(TimeSlot.Evening))
        form.chooseDay(today.plusDays(1))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Evening))
    }

    @Test
    fun whoStartsOnTheSignedInPersonOtherwiseFamily() {
        assertThat(new(signedIn = mia).who).isEqualTo(mia)
        assertThat(new().who).isEqualTo(PersonId.FAMILY)
    }

    @Test
    fun aPickedTimeThatIsASlotsTimeSelectsThatSlot() {
        val form = new()
        form.chooseTime(TimeChoice.Custom(LocalTime.of(14, 0)))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Afternoon))
        form.chooseTime(TimeChoice.Custom(LocalTime.of(16, 15)))
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(16, 15)))
    }

    @Test
    fun thePickerOpensOnTheChosenTimeOrTheDefaultSlotsTime() {
        assertThat(new(now = "10:54").pickerTime).isEqualTo(LocalTime.of(14, 0))
        // All day is the default from 18:00; the picker then opens on Evening's time.
        assertThat(new(now = "20:00").pickerTime).isEqualTo(LocalTime.of(18, 0))
        val custom = new()
        custom.chooseTime(TimeChoice.Custom(LocalTime.of(16, 15)))
        assertThat(custom.pickerTime).isEqualTo(LocalTime.of(16, 15))
    }

    @Test
    fun anEditStartsFromTheEventWithItsOwnTimeAndLength() {
        val form = editTimed(today.atTime(19, 30), 90)
        assertThat(form.title).isEqualTo("Dinner with Jo & Priya")
        assertThat(form.who).isEqualTo(alex)
        assertThat(form.day).isEqualTo(today)
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(19, 30)))
        assertThat(form.length).isEqualTo(Duration.ofMinutes(90))
        assertThat(form.lengths.map(::lengthLabel)).containsExactly("30 min", "1 h", "2 h", "1 h 30").inOrder()
        assertThat(form.datesLocked).isFalse()
        assertThat(form.unchanged).isTrue()
    }

    @Test
    fun anEditStartingOnASlotSelectsItAndAddsNoLengthChip() {
        val form = editTimed(today.atTime(18, 0), 60)
        assertThat(form.time).isEqualTo(slot(TimeSlot.Evening))
        assertThat(form.lengths).isEqualTo(LENGTH_CHOICES)
    }

    @Test
    fun lengthLabels() {
        assertThat(listOf(30L, 45L, 60L, 65L, 90L, 180L).map { lengthLabel(Duration.ofMinutes(it)) })
            .containsExactly("30 min", "45 min", "1 h", "1 h 05", "1 h 30", "3 h").inOrder()
    }

    @Test
    fun aDateOutsideTheWeekShowsOnPickDate() {
        assertThat(editTimed(LocalDateTime.of(2026, 10, 5, 18, 0), 60).pickedDateLabel).isEqualTo("Mon 5 Oct")
        assertThat(editTimed(today.plusDays(6).atTime(18, 0), 60).pickedDateLabel).isNull()
        assertThat(new().dayChoices).containsExactlyElementsIn((0L..6L).map { today.plusDays(it) }).inOrder()
    }

    @Test
    fun anUntaggedEventStaysUntaggedUnlessWhoIsChanged() {
        val form = editTimed(today.atTime(13, 0), 30, forPerson = null)
        assertThat(form.who).isEqualTo(PersonId.FAMILY)
        assertThat(form.draft(createdBy = null).forPerson).isNull()
        assertThat(form.unchanged).isTrue()
        // Family is already chosen, so choosing it again changes nothing.
        form.chooseWho(PersonId.FAMILY)
        assertThat(form.draft(createdBy = null).forPerson).isNull()
        assertThat(form.unchanged).isTrue()
        form.chooseWho(mia)
        form.chooseWho(PersonId.FAMILY)
        assertThat(form.draft(createdBy = null).forPerson).isEqualTo("family")
        assertThat(form.unchanged).isFalse()
    }

    @Test
    fun aDepartedPersonsTagIsKeptOnATitleEditAndTheSummaryEndsAtTheTime() {
        val form = editTimed(today.atTime(19, 30), 90, forPerson = "gone")
        form.updateTitle("Dinner at Jo's")
        assertThat(form.draft(createdBy = null).forPerson).isEqualTo("gone")
        // Only the id is stored, so there is no name to show.
        assertThat(form.summary { names[it] }).isEqualTo("Today · 19:30–21:00")
    }

    @Test
    fun anEventWithNoLengthOpensOnOneHourWithNoExtraChip() {
        val form = editTimed(today.atTime(19, 30), 0)
        assertThat(form.lengths).isEqualTo(LENGTH_CHOICES)
        assertThat(form.length).isEqualTo(Duration.ofHours(1))
    }

    @Test
    fun anAllDayEditIsAllDayWithTheUsualLengths() {
        val saturday = LocalDate.of(2026, 9, 26)
        val form = edit(EventTime.AllDay(saturday), EventTime.AllDay(saturday.plusDays(1)), title = "Bin day", forPerson = "family")
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        assertThat(form.day).isEqualTo(saturday)
        assertThat(form.lengths).isEqualTo(LENGTH_CHOICES)
        assertThat(form.length).isEqualTo(Duration.ofHours(1))
        assertThat(form.unchanged).isTrue()
    }

    @Test
    fun anEventOverSeveralDaysLocksItsDates() {
        val halfTerm = edit(EventTime.AllDay(LocalDate.of(2026, 9, 22)), EventTime.AllDay(LocalDate.of(2026, 9, 25)), "Half term", "family")
        assertThat(halfTerm.datesLocked).isTrue()
        assertThat(halfTerm.lockedDatesLabel).isEqualTo("Tue 22 – Thu 24 · change dates on your phone")
        assertThat(halfTerm.summary()).isEqualTo("Yesterday – Tomorrow · All day · Family")
        assertThat(edit(EventTime.AllDay(today), EventTime.AllDay(today.plusDays(1))).datesLocked).isFalse()
        // 22:00 to 01:00 crosses midnight; 22:00 to 00:00 ends at midnight, which counts as the same day.
        assertThat(editTimed(today.atTime(22, 0), 180).datesLocked).isTrue()
        assertThat(editTimed(today.atTime(22, 0), 120).datesLocked).isFalse()
        assertThat(editTimed(today.atTime(22, 0), 120).lockedDatesLabel).isNull()
    }

    @Test
    fun aLockedEditKeepsItsDatesAndChangesOnlyTitleAndWho() {
        val start = EventTime.AllDay(LocalDate.of(2026, 9, 22))
        val end = EventTime.AllDay(LocalDate.of(2026, 9, 25))
        val form = edit(start, end, title = "Half term", forPerson = "family")
        form.updateTitle("Half term at Gran's")
        form.chooseWho(mia)
        val draft = form.draft(createdBy = null)
        assertThat(listOf(draft.start, draft.end)).containsExactly(start, end).inOrder()
        assertThat(draft.title to draft.forPerson).isEqualTo("Half term at Gran's" to "mia")
    }

    @Test
    fun theSummaryReadsLikeTheWhenRowWithThePerson() {
        val form = new(now = "10:54")
        assertThat(form.summary()).isEqualTo("Today · 14:00–15:00 · Family")
        form.chooseDay(today.plusDays(1))
        form.chooseTime(slot(TimeSlot.Afternoon))
        form.chooseWho(mia)
        assertThat(form.summary()).isEqualTo("Tomorrow · 14:00–15:00 · Mia")
        form.chooseDay(LocalDate.of(2026, 9, 26))
        form.chooseTime(TimeChoice.AllDay)
        assertThat(form.summary()).isEqualTo("Sat 26 Sep · All day · Mia")
    }

    @Test
    fun anAllDayDraftIsOneWholeDate() {
        val form = new(now = "20:00")
        form.updateTitle("  Bin day ")
        val draft = form.draft(createdBy = "alex")
        assertThat(draft).isEqualTo(
            EventDraft("Bin day", EventTime.AllDay(today), EventTime.AllDay(today.plusDays(1)), "family", "alex"),
        )
    }

    @Test
    fun aTimedDraftIsInTheHouseholdZone() {
        val form = new(now = "10:54")
        form.chooseLength(Duration.ofMinutes(30))
        val draft = form.draft(createdBy = null)
        // 14:00 BST is 13:00Z.
        assertThat(draft.start).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T13:00:00Z")))
        assertThat(draft.end).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T13:30:00Z")))
    }

    @Test
    fun aDraftOnTheAutumnChangeDayUsesTheLocalTimes() {
        val form = new(now = "10:00", on = LocalDate.of(2026, 10, 24))
        form.updateTitle("Bonfire")
        form.chooseDay(LocalDate.of(2026, 10, 25))
        form.chooseTime(slot(TimeSlot.Evening))
        // 18:00 on 25 October is after the clocks go back: GMT.
        val evening = form.draft(createdBy = null)
        assertThat(evening.start).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T18:00:00Z")))
        assertThat(evening.end).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T19:00:00Z")))
        // 01:30 happens twice that night: the earlier (BST) one is taken, and 2 h is two hours of real time.
        form.chooseTime(TimeChoice.Custom(LocalTime.of(1, 30)))
        form.chooseLength(Duration.ofHours(2))
        val early = form.draft(createdBy = null)
        assertThat(early.start).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T00:30:00Z")))
        assertThat(early.end).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T02:30:00Z")))
        assertThat(form.summary()).isEqualTo("Tomorrow · 01:30–02:30 · Family")
    }

    @Test
    fun aDraftInTheSpringGapMovesForward() {
        val form = new(on = LocalDate.of(2026, 3, 28))
        form.updateTitle("Early start")
        form.chooseDay(LocalDate.of(2026, 3, 29))
        form.chooseTime(TimeChoice.Custom(LocalTime.of(1, 30)))
        // 01:30 doesn't exist on 29 March: it moves forward by the gap, to 02:30 BST (01:30Z).
        assertThat(form.draft(createdBy = null).start).isEqualTo(EventTime.Timed(Instant.parse("2026-03-29T01:30:00Z")))
    }

    @Test
    fun anEditThatLeavesTheTimesAloneKeepsTheEventsOwnInstants() {
        // 01:30 GMT on 25 October is the second 01:30 that night; rebuilt from the chips it would be the first (BST).
        val secondHalfPast = Instant.parse("2026-10-25T01:30:00Z")
        val start = EventTime.Timed(secondHalfPast)
        val end = EventTime.Timed(secondHalfPast.plusSeconds(3_600))
        val form = edit(start, end)
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(1, 30)))
        assertThat(form.unchanged).isTrue()
        form.updateTitle("Night feed")
        val draft = form.draft(createdBy = null)
        assertThat(listOf(draft.start, draft.end)).containsExactly(start, end).inOrder()
    }

    @Test
    fun aNewEventEndsAtMidnightAtTheLatest() {
        val form = new()
        form.chooseTime(TimeChoice.Custom(LocalTime.of(23, 30)))
        val draft = form.draft(createdBy = null)
        // 23:30 BST + 1 h would cross midnight: it stops at 00:00 BST (23:00Z).
        assertThat(draft.start).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T22:30:00Z")))
        assertThat(draft.end).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T23:00:00Z")))
        assertThat(form.summary()).isEqualTo("Today · 23:30–00:00 · Family")
        // Opened again to edit, it is a one-day event, not a locked multi-day one.
        val reopened = edit(draft.start, draft.end)
        assertThat(reopened.datesLocked).isFalse()
        assertThat(reopened.length).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun tomorrowChosenAt2330IsTheDateItShowed() {
        // Opened at 23:30 on the 24th. The chip holds a date, not "today + 1", so a Save after midnight keeps it;
        // Task 9's host test moves the clock past midnight before Save.
        val form = new(now = "23:30", on = LocalDate.of(2026, 10, 24))
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        form.updateTitle("Bonfire")
        form.chooseDay(form.dayChoices[1])
        assertThat(form.day).isEqualTo(LocalDate.of(2026, 10, 25))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Morning))
        // The draft depends only on the chosen date: 09:00 GMT on the 25th, the clock-change day.
        assertThat(form.draft(createdBy = null).start).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T09:00:00Z")))
    }

    @Test
    fun canSaveNeedsATitleOtherThanSpaces() {
        val form = new()
        assertThat(form.canSave).isFalse()
        form.updateTitle("   ")
        assertThat(form.canSave).isFalse()
        form.updateTitle("Swim")
        assertThat(form.canSave).isTrue()
    }

    @Test
    fun typingPastTheLimitIsIgnoredButShorteningIsAllowed() {
        val form = new()
        form.updateTitle("a".repeat(MAX_TITLE_LENGTH))
        form.updateTitle("a".repeat(MAX_TITLE_LENGTH + 1))
        assertThat(form.title).hasLength(MAX_TITLE_LENGTH)
        val long = edit(EventTime.AllDay(today), EventTime.AllDay(today.plusDays(1)), title = "b".repeat(120))
        long.updateTitle("b".repeat(119))
        assertThat(long.title).hasLength(119)
        long.updateTitle("b".repeat(121))
        assertThat(long.title).hasLength(119)
    }

    @Test
    fun anEditIsUnchangedUntilSomethingDiffersBeyondSurroundingSpaces() {
        val form = editTimed(today.atTime(19, 30), 90)
        form.updateTitle("  Dinner with Jo & Priya  ")
        assertThat(form.unchanged).isTrue()
        form.chooseWho(alex)
        assertThat(form.unchanged).isTrue()
        form.chooseLength(Duration.ofHours(1))
        assertThat(form.unchanged).isFalse()
    }

    @Test
    fun aNewEventIsNeverUnchanged() {
        assertThat(new().unchanged).isFalse()
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventFormTest*"`
Expected: compilation FAILS: `EventForm`, `TimeSlot`, `TimeChoice`, `lengthLabel`, `LENGTH_CHOICES` and `MAX_TITLE_LENGTH` are unresolved.

- [ ] **Step 3: Share the day format and the midnight-end rule**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`:
1. Replace `private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)` with `internal val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)`.
2. Replace `whenLabel`, from its KDoc to its closing brace, with the shared rule and a `whenLabel` that uses it:
```kotlin
/** An event's first and last day in [zone]. An all-day end is exclusive; a timed end at exactly midnight belongs to the day before. */
internal fun daySpan(start: EventTime, end: EventTime, zone: ZoneId): Pair<LocalDate, LocalDate> =
    if (start is EventTime.AllDay && end is EventTime.AllDay) {
        start.date to maxOf(start.date, end.date.minusDays(1))
    } else {
        val from = start.instantIn(zone).atZone(zone)
        from.toLocalDate() to lastDayOf(from, end.instantIn(zone).atZone(zone))
    }

/** The detail sheet's "When": "Today · 19:30–21:00", "Sat 26 Sep · All day", or the span of a longer event. */
fun whenLabel(start: EventTime, end: EventTime, zone: ZoneId, today: LocalDate): String {
    val (first, last) = daySpan(start, end, zone)
    if (start is EventTime.AllDay && end is EventTime.AllDay) {
        return if (last == first) {
            "${dayLabel(first, today)} · $ALL_DAY_LABEL"
        } else {
            "${dayLabel(first, today)} – ${dayLabel(last, today)} · $ALL_DAY_LABEL"
        }
    }
    val from = start.instantIn(zone).atZone(zone)
    val to = end.instantIn(zone).atZone(zone)
    return if (last == first) {
        "${dayLabel(first, today)} · ${from.format(HOURS_MINUTES)}–${to.format(HOURS_MINUTES)}"
    } else {
        "${dayLabel(first, today)} ${from.format(HOURS_MINUTES)} – ${dayLabel(to.toLocalDate(), today)} ${to.format(HOURS_MINUTES)}"
    }
}
```
`WhenLabelTest` covers the rewrite: every label it checks stays the same.

- [ ] **Step 4: Write `EventForm`**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/EventForm.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.household.PersonId

/** Hand-off §7 Time chips. */
enum class TimeSlot(val label: String, val time: LocalTime) {
    Morning("Morning", LocalTime.of(9, 0)),
    Afternoon("Afternoon", LocalTime.of(14, 0)),
    Evening("Evening", LocalTime.of(18, 0)),
}

/** What the Time chips choose: all day, a slot, or a time from Pick time…. */
sealed interface TimeChoice {
    data object AllDay : TimeChoice
    data class Slot(val slot: TimeSlot) : TimeChoice
    data class Custom(val time: LocalTime) : TimeChoice
}

/** 2b-2 design D8: a title is at most 100 characters. */
const val MAX_TITLE_LENGTH = 100

/** Hand-off §7 Length chips: 30 min, 1 h, 2 h. */
val LENGTH_CHOICES: List<Duration> = listOf(Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2))

private val DEFAULT_LENGTH: Duration = Duration.ofHours(1)
private const val MINUTES_PER_HOUR = 60L
private const val DAYS_SHOWN = 7L
private val DAY_AND_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)

/** "30 min", "1 h", "1 h 30": whole hours read "{h} h", under an hour "{m} min", otherwise "{h} h {mm}". */
fun lengthLabel(length: Duration): String {
    val hours = length.toHours()
    val minutes = length.toMinutes() % MINUTES_PER_HOUR
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours h"
        else -> "$hours h ${minutes.toString().padStart(2, '0')}"
    }
}

/** On [today], the next slot still to come ([now] before its time), All day once Evening has begun; Morning on any other day. */
internal fun defaultTime(day: LocalDate, today: LocalDate, now: LocalTime): TimeChoice =
    if (day != today) {
        TimeChoice.Slot(TimeSlot.Morning)
    } else {
        TimeSlot.entries.firstOrNull { now < it.time }?.let { TimeChoice.Slot(it) } ?: TimeChoice.AllDay
    }

/**
 * The add/edit sheet's state (2b-2 design §3.1): plain Kotlin with Compose state, so the sheet recomposes as it
 * changes and tests drive it without a UI. [today] and [now] are the household zone's date and time when the sheet
 * opened. [day] is a date, not "today + n", so a sheet left open over midnight still saves the day its chip showed.
 */
class EventForm(
    val mode: Mode,
    val today: LocalDate,
    private val now: LocalTime,
    val zone: ZoneId,
    signedIn: PersonId?,
    preselectedDay: LocalDate?,
) {
    sealed interface Mode {
        data object New : Mode
        data class Edit(val original: EditableEvent) : Mode
    }

    private val original: EditableEvent? = (mode as? Mode.Edit)?.original

    /** Editing an event over several days: only the title and who can change (2b-2 design D3). */
    val datesLocked: Boolean = original?.let { spansDays(it.start, it.end, zone) } ?: false

    var title: String by mutableStateOf(original?.title ?: "")
        private set

    // An edit starts on the event's person (untagged reads as Family); a new event on whoever is signed in (D2).
    var who: PersonId by mutableStateOf(
        if (original != null) original.forPerson?.let(::PersonId) ?: PersonId.FAMILY else signedIn ?: PersonId.FAMILY,
    )
        private set

    var day: LocalDate by mutableStateOf(original?.let { startDate(it.start, zone) } ?: preselectedDay ?: today)
        private set

    var time: TimeChoice by mutableStateOf(original?.let { timeOf(it.start, zone) } ?: defaultTime(preselectedDay ?: today, today, now))
        private set

    var length: Duration by mutableStateOf(original?.let(::lengthOf) ?: DEFAULT_LENGTH)
        private set

    private var whoTouched = false

    // Until a Time chip is touched, changing the day re-applies the default (a new event only).
    private var timeTouched = original != null

    // Until Day, Time or Length is touched, an edit keeps the event's own start and end.
    private var whenTouched = false

    /** The Length chips: 30 min, 1 h, 2 h, and an edited event's own length when it is none of those (D3). */
    val lengths: List<Duration> = LENGTH_CHOICES + listOfNotNull(original?.let(::lengthOf)?.takeIf { it !in LENGTH_CHOICES })

    /** Today and the six days after it: the Day chips before Pick date…. */
    val dayChoices: List<LocalDate> = (0 until DAYS_SHOWN).map { today.plusDays(it) }

    val canSave: Boolean get() = title.trim().isNotEmpty()

    /** Typing past [MAX_TITLE_LENGTH] is ignored; shortening is always allowed, even an edited title already over it. */
    fun updateTitle(value: String) {
        if (value.length <= MAX_TITLE_LENGTH || value.length < title.length) title = value
    }

    /** Choosing the person already chosen changes nothing, so an untagged event stays untagged. */
    fun chooseWho(person: PersonId) {
        if (person == who) return
        who = person
        whoTouched = true
    }

    fun chooseDay(date: LocalDate) {
        day = date
        whenTouched = true
        if (!timeTouched) time = defaultTime(date, today, now)
    }

    /** A picked time that is a slot's time selects that slot's chip. */
    fun chooseTime(choice: TimeChoice) {
        time = if (choice is TimeChoice.Custom) {
            TimeSlot.entries.firstOrNull { it.time == choice.time }?.let { TimeChoice.Slot(it) } ?: choice
        } else {
            choice
        }
        timeTouched = true
        whenTouched = true
    }

    fun chooseLength(value: Duration) {
        length = value
        whenTouched = true
    }

    /** The start time a timed choice gives; null for All day. */
    val startTime: LocalTime?
        get() = when (val t = time) {
            TimeChoice.AllDay -> null
            is TimeChoice.Slot -> t.slot.time
            is TimeChoice.Custom -> t.time
        }

    /** Where the time picker opens: the chosen start, or the default slot's time (Evening's) when All day is chosen. */
    val pickerTime: LocalTime
        get() = startTime ?: (defaultTime(day, today, now) as? TimeChoice.Slot)?.slot?.time ?: TimeSlot.Evening.time

    /** Pick date…'s label: the chosen date, e.g. "Mon 5 Oct", when it isn't one of the seven Day chips. */
    val pickedDateLabel: String?
        get() = day.takeIf { it !in dayChoices }?.format(SHORT_DAY)

    /** A locked multi-day edit's one line: "Mon 22 – Wed 24 · change dates on your phone". */
    val lockedDatesLabel: String?
        get() = original?.takeIf { datesLocked }?.let { o ->
            val (first, last) = daySpan(o.start, o.end, zone)
            "${first.format(DAY_AND_DATE)} – ${last.format(DAY_AND_DATE)} · change dates on your phone"
        }

    /**
     * What Save sends. All day is one whole date. A timed event starts at the chosen local time in the household zone
     * (in a spring-forward gap it moves forward; an ambiguous autumn time takes the earlier offset) and lasts
     * [length] of real time, but ends at the next midnight at the latest, since new events can't span days (D3). A
     * locked edit, or one whose Day, Time and Length weren't touched, keeps its own start and end: rebuilt from the
     * chips, an event in the autumn's repeated hour would move. An edit whose Who wasn't touched keeps its tag
     * exactly (an untagged event stays untagged).
     */
    fun draft(createdBy: String?): EventDraft {
        val o = original
        val forPerson = if (o != null && !whoTouched) o.forPerson else who.value
        if (o != null && (datesLocked || !whenTouched)) return EventDraft(title.trim(), o.start, o.end, forPerson, createdBy)
        val start = startTime
        return if (start == null) {
            EventDraft(title.trim(), EventTime.AllDay(day), EventTime.AllDay(day.plusDays(1)), forPerson, createdBy)
        } else {
            val from = ZonedDateTime.of(day, start, zone).toInstant()
            val midnight = day.plusDays(1).atStartOfDay(zone).toInstant()
            EventDraft(title.trim(), EventTime.Timed(from), EventTime.Timed(minOf(from.plus(length), midnight)), forPerson, createdBy)
        }
    }

    /** An edit that changes nothing: the sheet just closes, with no PIN (2b-2 design §3.3). A new event never is. */
    val unchanged: Boolean
        get() {
            val o = original ?: return false
            val d = draft(createdBy = null)
            return d.title == o.title.trim() && d.start == o.start && d.end == o.end && d.forPerson == o.forPerson
        }

    /**
     * The header's live line, e.g. "Tomorrow · 14:00–15:00 · Mia": the When row's wording and the person's name. A tag
     * whose person has left the household has no name ([nameOf] gives null), so the line ends at the time.
     */
    fun summary(nameOf: (PersonId) -> String?): String {
        val d = draft(createdBy = null)
        val time = whenLabel(d.start, d.end, zone, today)
        return nameOf(who)?.let { "$time · $it" } ?: time
    }
}

private fun startDate(start: EventTime, zone: ZoneId): LocalDate = when (start) {
    is EventTime.AllDay -> start.date
    is EventTime.Timed -> start.instant.atZone(zone).toLocalDate()
}

private fun timeOf(start: EventTime, zone: ZoneId): TimeChoice = when (start) {
    is EventTime.AllDay -> TimeChoice.AllDay
    is EventTime.Timed -> {
        val t = start.instant.atZone(zone).toLocalTime()
        TimeSlot.entries.firstOrNull { it.time == t }?.let { TimeChoice.Slot(it) } ?: TimeChoice.Custom(t)
    }
}

/** A timed event's length in real time; null for an all-day event, or one whose end isn't after its start (bad provider data). */
private fun lengthOf(event: EditableEvent): Duration? {
    val start = event.start
    val end = event.end
    if (start !is EventTime.Timed || end !is EventTime.Timed) return null
    return Duration.between(start.instant, end.instant).takeIf { !it.isNegative && !it.isZero }
}

private fun spansDays(start: EventTime, end: EventTime, zone: ZoneId): Boolean {
    val (first, last) = daySpan(start, end, zone)
    return last.isAfter(first)
}
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventFormTest*"`
Expected: PASS.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add capability/calendar
git commit -m "Add the add/edit form: its defaults, summary line and household-zone drafts"
```

---

### Task 7: The date and time pickers

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Pickers.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/PickersTest.kt` (create)

**Interfaces:**
- Consumes: `SHORT_DAY` (Task 6); `ShellTokens.pinScrim`, `HhIcon`, `Culvery.colors` (`:core:ui`).
- Produces (in `uk.co.siland.culvery.capability.calendar.ui`):
  - `enum class EditorPicker { None, Date, Time }`
  - `@Composable internal fun PickerLayer(onDismiss: () -> Unit, content: @Composable () -> Unit)` — fills its parent (the sheet's box) with the `pinScrim`, which cancels on tap, and centres [content]
  - `@Composable internal fun DatePickerCard(today: LocalDate, selected: LocalDate, onPick: (LocalDate) -> Unit, onCancel: () -> Unit)`
  - `@Composable internal fun TimePickerCard(initial: LocalTime, onSet: (LocalTime) -> Unit, onCancel: () -> Unit)`
  - pure helpers: `firstPageStart(today)`, `pageOf(date, today)`, `pageStart(page, today)`, `pageRangeLabel(start)`, `dateCellLabel(date)`, `hourStep(hour, up)`, `minuteStep(minute, up)`
  - test tags: `picker_scrim`, `date_picker`, `date_range`, `date_prev`, `date_next`, `date_cell_<ISO date>`, `time_picker`, `hour_up`, `hour_down`, `hour_value`, `minute_up`, `minute_down`, `minute_value`, `picker_cancel`, `picker_set`

- [ ] **Step 1: Write the failing tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/PickersTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class PickersTest {
    @get:Rule val compose = createComposeRule()

    /** Wednesday 23 September 2026, the hand-off's day. */
    private val today = LocalDate.of(2026, 9, 23)
    private val picked = mutableListOf<LocalDate>()
    private val set = mutableListOf<LocalTime>()
    private var cancelled = 0

    private fun showDates(selected: LocalDate = today) = compose.setContent {
        CulveryTheme(dark = true) {
            PickerLayer(onDismiss = { cancelled++ }) {
                DatePickerCard(today, selected, onPick = { picked += it }, onCancel = { cancelled++ })
            }
        }
    }

    private fun showTime(initial: LocalTime) = compose.setContent {
        CulveryTheme(dark = true) {
            PickerLayer(onDismiss = { cancelled++ }) {
                TimePickerCard(initial, onSet = { set += it }, onCancel = { cancelled++ })
            }
        }
    }

    @Test
    fun pagesAreFiveWeeksFromThisWeeksMonday() {
        assertThat(firstPageStart(today)).isEqualTo(LocalDate.of(2026, 9, 21))
        assertThat(pageRangeLabel(pageStart(0, today))).isEqualTo("Mon 21 Sep – Sun 25 Oct")
        assertThat(pageOf(LocalDate.of(2026, 10, 25), today)).isEqualTo(0)
        assertThat(pageOf(LocalDate.of(2026, 10, 26), today)).isEqualTo(1)
        assertThat(pageOf(LocalDate.of(2026, 9, 20), today)).isEqualTo(-1)
        assertThat(dateCellLabel(LocalDate.of(2026, 10, 1))).isEqualTo("1 Oct")
        assertThat(dateCellLabel(LocalDate.of(2026, 10, 2))).isEqualTo("2")
    }

    @Test
    fun hoursWrapWithoutChangingTheDayAndMinutesStepByQuarters() {
        assertThat(hourStep(23, up = true)).isEqualTo(0)
        assertThat(hourStep(0, up = false)).isEqualTo(23)
        assertThat(minuteStep(45, up = true)).isEqualTo(0)
        assertThat(minuteStep(0, up = false)).isEqualTo(45)
        // An off-quarter start moves to the next or previous quarter first.
        assertThat(minuteStep(37, up = true)).isEqualTo(45)
        assertThat(minuteStep(37, up = false)).isEqualTo(30)
    }

    @Test
    fun aPastDayCanBePicked() {
        showDates()
        compose.onNodeWithTag("date_range").assertTextEquals("Mon 21 Sep – Sun 25 Oct")
        compose.onNodeWithTag("date_cell_2026-09-21").performClick()
        assertThat(picked).containsExactly(LocalDate.of(2026, 9, 21))
    }

    @Test
    fun theArrowsPageByFiveWeeks() {
        showDates()
        compose.onNodeWithTag("date_next").performClick()
        compose.onNodeWithTag("date_range").assertTextEquals("Mon 26 Oct – Sun 29 Nov")
        compose.onNodeWithTag("date_cell_2026-11-10").performClick()
        compose.onNodeWithTag("date_prev").performClick()
        compose.onNodeWithTag("date_prev").performClick()
        compose.onNodeWithTag("date_range").assertTextEquals("Mon 17 Aug – Sun 20 Sep")
        assertThat(picked).containsExactly(LocalDate.of(2026, 11, 10))
    }

    @Test
    fun thePickerOpensOnThePageHoldingTheSelectedDay() {
        showDates(selected = LocalDate.of(2026, 11, 10))
        compose.onNodeWithTag("date_range").assertTextEquals("Mon 26 Oct – Sun 29 Nov")
        compose.onNodeWithTag("date_cell_2026-11-10").assertExists()
    }

    @Test
    fun cancelAndTheScrimPickNothing() {
        showDates()
        compose.onNodeWithTag("picker_cancel").performClick()
        // The scrim's top-left corner is clear of the centred card.
        compose.onNodeWithTag("picker_scrim").performTouchInput { click(Offset(10f, 10f)) }
        assertThat(cancelled).isEqualTo(2)
        assertThat(picked).isEmpty()
    }

    @Test
    fun theTimeStepsWrapAndSetTime() {
        showTime(LocalTime.of(23, 45))
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_value").assertTextEquals("00")
        compose.onNodeWithTag("minute_up").performClick()
        compose.onNodeWithTag("minute_value").assertTextEquals("00")
        compose.onNodeWithTag("hour_value").assertTextEquals("00")
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(set).containsExactly(LocalTime.of(0, 0))
    }

    @Test
    fun anOffQuarterTimeShowsAsItIsThenStepsToAQuarter() {
        showTime(LocalTime.of(19, 37))
        compose.onNodeWithTag("minute_value").assertTextEquals("37")
        compose.onNodeWithTag("minute_down").performClick()
        compose.onNodeWithTag("minute_value").assertTextEquals("30")
        compose.onNodeWithTag("hour_down").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(set).containsExactly(LocalTime.of(18, 30))
    }

    @Test
    fun cancelSetsNoTime() {
        showTime(LocalTime.of(16, 15))
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("picker_cancel").performClick()
        assertThat(set).isEmpty()
        assertThat(cancelled).isEqualTo(1)
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*PickersTest*"`
Expected: compilation FAILS: `PickerLayer`, `DatePickerCard`, `TimePickerCard` and the page and step helpers are unresolved.

- [ ] **Step 3: Add the pickers' styles and dimensions**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`:

1. Add at the end of `CalendarType`, before its closing brace:
```kotlin

    /** 24 sp / 700: "Pick a date", "Pick a time". */
    val pickerTitle = HhType.dateNumber

    /** 15 sp / 400: the date picker's range, "Mon 21 Sep – Sun 25 Oct". */
    val pickerRange = subtitle

    /** 13 sp / 700: the date picker's weekday header. */
    val pickerWeekday = HhType.label.copy(fontWeight = FontWeight.W700)

    /** 16 sp / 600: date cells. */
    val pickerCell = personChip

    /** 16 sp / 700: Cancel and Set time. */
    val pickerButton = noteTitle

    /** 72 sp / 600, tabular, −2 tracking, line height 1: the time picker's hour and minute. */
    val timeValue = HhType.headerValue.copy(fontSize = 72.sp, letterSpacing = (-2).sp, lineHeight = 72.sp)

    /** 64 sp / 600: the ":" between them. */
    val timeColon = HhType.headerValue.copy(fontSize = 64.sp)
```
2. In `CalendarDimens`, before `/** Chip tint: …`, add:
```kotlin
    // Pickers (hand-off §7 and Culvery.dc.html): card `surf`, radius 30, padding 26. Date: 520 wide, 14 between blocks,
    // the range 4 below "Pick a date", ‹ › 48 dp circles 8 apart; weekday header padding 4; cells 56 dp, radius 16,
    // 6 apart, today's ring 2 dp; Cancel 52 dp, radius 26. Time: 440 wide, 18 between blocks; the columns 14 apart;
    // steppers 88×52, radius 18, a 32 dp icon, 8 from the value; ":" 10 above the baseline; Cancel and Set time
    // 56 dp, radius 28, 10 apart.
    val pickerRadius = 30.dp
    val pickerPadding = 26.dp
    val datePickerWidth = 520.dp
    val datePickerGap = 14.dp
    val pickerRangeTop = 4.dp
    val pageButton = 48.dp
    val pageButtonIcon = 24.dp
    val pageButtonGap = 8.dp
    val weekdayPaddingV = 4.dp
    val dateCellHeight = 56.dp
    val dateCellRadius = 16.dp
    val dateCellGap = 6.dp
    val dateRing = 2.dp
    val pickerCancelHeight = 52.dp
    val pickerCancelRadius = 26.dp
    val timePickerWidth = 440.dp
    val timePickerGap = 18.dp
    val timeColumnGap = 14.dp
    val stepperWidth = 88.dp
    val stepperHeight = 52.dp
    val stepperRadius = 18.dp
    val stepperIcon = 32.dp
    val stepperGap = 8.dp
    val colonBottom = 10.dp
    val timeButtonHeight = 56.dp
    val timeButtonRadius = 28.dp
    val timeButtonGap = 10.dp

    /** Hand-off §7: past days in the date picker at 30%. */
    const val PAST_DAY_ALPHA = 0.3f

```

- [ ] **Step 4: Write the pickers**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Pickers.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.SHORT_DAY
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.ShellTokens

/** Which picker is drawn over the add/edit sheet. */
enum class EditorPicker { None, Date, Time }

/** A date-picker page is 5 weeks (2b-2 design D10). */
internal const val PICKER_WEEKS = 5
private const val DAYS_PER_WEEK = 7
private const val DAYS_PER_PAGE = PICKER_WEEKS * DAYS_PER_WEEK
private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60

/** The time picker's minute step (hand-off §7). */
internal const val MINUTE_STEP = 15

// ENGLISH, as elsewhere in the calendar: the hand-off's "Mon", "1 Oct".
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

/** The first page starts on this week's Monday. */
internal fun firstPageStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** The page holding [date], counted from the first page; negative for earlier pages. */
internal fun pageOf(date: LocalDate, today: LocalDate): Int =
    Math.floorDiv(ChronoUnit.DAYS.between(firstPageStart(today), date), DAYS_PER_PAGE.toLong()).toInt()

internal fun pageStart(page: Int, today: LocalDate): LocalDate = firstPageStart(today).plusDays(DAYS_PER_PAGE.toLong() * page)

/** "Mon 21 Sep – Sun 25 Oct". */
internal fun pageRangeLabel(start: LocalDate): String =
    "${start.format(SHORT_DAY)} – ${start.plusDays(DAYS_PER_PAGE - 1L).format(SHORT_DAY)}"

/** A cell reads its day of the month; the 1st reads "1 Oct". */
internal fun dateCellLabel(date: LocalDate): String = if (date.dayOfMonth == 1) date.format(DAY_MONTH) else date.dayOfMonth.toString()

/** Hours step by 1 and wrap 23 ↔ 00 without changing the day. */
internal fun hourStep(hour: Int, up: Boolean): Int = Math.floorMod(hour + if (up) 1 else -1, HOURS_PER_DAY)

/** Minutes step through 00, 15, 30, 45 and wrap; an off-quarter minute moves to the next or previous quarter first. */
internal fun minuteStep(minute: Int, up: Boolean): Int = when {
    up -> (minute / MINUTE_STEP + 1) * MINUTE_STEP % MINUTES_PER_HOUR
    minute % MINUTE_STEP != 0 -> minute / MINUTE_STEP * MINUTE_STEP
    else -> Math.floorMod(minute - MINUTE_STEP, MINUTES_PER_HOUR)
}

/** Over the sheet only (2b-2 design D4): a scrim that cancels on tap, with [content] centred on it. */
@Composable
internal fun PickerLayer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("picker_scrim")
                .background(ShellTokens.pinScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Cancel",
                    onClick = onDismiss,
                ),
        )
        content()
    }
}

/**
 * Hand-off §7 date picker with 2b-2 design D10's changes: pages of 5 weeks, ‹ and › to move between them, past
 * days dimmed but selectable. It opens on the page holding [selected]; tapping a day picks it.
 */
@Composable
internal fun DatePickerCard(today: LocalDate, selected: LocalDate, onPick: (LocalDate) -> Unit, onCancel: () -> Unit) {
    val c = Culvery.colors
    var page by remember { mutableIntStateOf(pageOf(selected, today)) }
    val start = pageStart(page, today)
    PickerCard(CalendarDimens.datePickerWidth, CalendarDimens.datePickerGap, "date_picker") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.pageButtonGap)) {
            Column(Modifier.weight(1f)) {
                Text("Pick a date", style = CalendarType.pickerTitle, color = c.ink)
                Text(
                    pageRangeLabel(start),
                    style = CalendarType.pickerRange,
                    color = c.mute,
                    modifier = Modifier.padding(top = CalendarDimens.pickerRangeTop).testTag("date_range"),
                )
            }
            RoundButton("chevron_left", "Earlier weeks", "date_prev") { page-- }
            RoundButton("chevron_right", "Later weeks", "date_next") { page++ }
        }
        Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.dateCellGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.dateCellGap)) {
                (0 until DAYS_PER_WEEK).forEach { d ->
                    Text(
                        start.plusDays(d.toLong()).format(WEEKDAY),
                        style = CalendarType.pickerWeekday,
                        color = c.mute,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(vertical = CalendarDimens.weekdayPaddingV),
                    )
                }
            }
            (0 until PICKER_WEEKS).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.dateCellGap)) {
                    (0 until DAYS_PER_WEEK).forEach { d ->
                        val date = start.plusDays((week * DAYS_PER_WEEK + d).toLong())
                        DateCell(date, today, selected, Modifier.weight(1f)) { onPick(date) }
                    }
                }
            }
        }
        PickerButton(
            "Cancel", primary = false, tag = "picker_cancel",
            height = CalendarDimens.pickerCancelHeight, radius = CalendarDimens.pickerCancelRadius,
            modifier = Modifier.fillMaxWidth(), onClick = onCancel,
        )
    }
}

/** Hand-off §7 time picker: hour and minute steppers around the value, Cancel and Set time. */
@Composable
internal fun TimePickerCard(initial: LocalTime, onSet: (LocalTime) -> Unit, onCancel: () -> Unit) {
    val c = Culvery.colors
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    PickerCard(CalendarDimens.timePickerWidth, CalendarDimens.timePickerGap, "time_picker") {
        Text("Pick a time", style = CalendarType.pickerTitle, color = c.ink)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.timeColumnGap, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Stepper(hour, "hour", onUp = { hour = hourStep(hour, up = true) }, onDown = { hour = hourStep(hour, up = false) })
            Text(":", style = CalendarType.timeColon, color = c.ink, modifier = Modifier.padding(bottom = CalendarDimens.colonBottom))
            Stepper(minute, "minute", onUp = { minute = minuteStep(minute, up = true) }, onDown = { minute = minuteStep(minute, up = false) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.timeButtonGap)) {
            PickerButton(
                "Cancel", primary = false, tag = "picker_cancel",
                height = CalendarDimens.timeButtonHeight, radius = CalendarDimens.timeButtonRadius,
                modifier = Modifier.weight(1f), onClick = onCancel,
            )
            PickerButton(
                "Set time", primary = true, tag = "picker_set",
                height = CalendarDimens.timeButtonHeight, radius = CalendarDimens.timeButtonRadius,
                modifier = Modifier.weight(1f), onClick = { onSet(LocalTime.of(hour, minute)) },
            )
        }
    }
}

@Composable
private fun PickerCard(width: Dp, gap: Dp, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .testTag(tag)
            .width(width)
            .clip(RoundedCornerShape(CalendarDimens.pickerRadius))
            .background(Culvery.colors.surf)
            // Taps on the card's own space mustn't reach the scrim underneath, which cancels.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(CalendarDimens.pickerPadding),
        content = content,
    )
}

@Composable
private fun DateCell(date: LocalDate, today: LocalDate, selected: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    val c = Culvery.colors
    val isSelected = date == selected
    val shape = RoundedCornerShape(CalendarDimens.dateCellRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("date_cell_$date")
            .alpha(if (date.isBefore(today) && !isSelected) CalendarDimens.PAST_DAY_ALPHA else 1f)
            .height(CalendarDimens.dateCellHeight)
            .clip(shape)
            .background(if (isSelected) c.accent else c.surf2)
            .then(if (date == today) Modifier.border(CalendarDimens.dateRing, c.accent, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Text(dateCellLabel(date), style = CalendarType.pickerCell, color = if (isSelected) c.accentInk else c.ink, maxLines = 1)
    }
}

@Composable
private fun RoundButton(icon: String, label: String, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(CalendarDimens.pageButton)
            .clip(CircleShape)
            .background(c.surf2)
            .clickable(onClickLabel = label, onClick = onClick),
    ) {
        HhIcon(icon, size = CalendarDimens.pageButtonIcon, tint = c.ink, contentDescription = label)
    }
}

@Composable
private fun Stepper(value: Int, name: String, onUp: () -> Unit, onDown: () -> Unit) {
    val c = Culvery.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CalendarDimens.stepperGap)) {
        StepButton("expand_less", "${name}_up", "More", onUp)
        Text(value.toString().padStart(2, '0'), style = CalendarType.timeValue, color = c.ink, modifier = Modifier.testTag("${name}_value"))
        StepButton("expand_more", "${name}_down", "Less", onDown)
    }
}

@Composable
private fun StepButton(icon: String, tag: String, label: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(CalendarDimens.stepperWidth, CalendarDimens.stepperHeight)
            .clip(RoundedCornerShape(CalendarDimens.stepperRadius))
            .background(c.surf2)
            .clickable(onClickLabel = label, onClick = onClick),
    ) {
        HhIcon(icon, size = CalendarDimens.stepperIcon, tint = c.ink, contentDescription = label)
    }
}

@Composable
private fun PickerButton(
    text: String,
    primary: Boolean,
    tag: String,
    height: Dp,
    radius: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag(tag)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(if (primary) c.accent else c.surf2)
            .clickable(onClick = onClick),
    ) {
        Text(text, style = CalendarType.pickerButton, color = if (primary) c.accentInk else c.ink, maxLines = 1)
    }
}
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*PickersTest*"`
Expected: PASS.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. The pickers' screenshots come with the sheet in Task 8.

- [ ] **Step 7: Commit**

```bash
git add capability/calendar
git commit -m "Add the date and time pickers for the add/edit sheet"
```

---

### Task 8: The add/edit sheet — `EventEditorSheet`, stateless, with hand-off 10–16 and its place above the keyboard

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Components.kt` (rewrite)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheet.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorSheet.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorSheetTest.kt` (create)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EditorScreenshotTest.kt` (create)
- Create (recorded): `capability/calendar/src/test/screenshots/editor_{keyboard,empty,child,edit,own_length,failed,date_picker,time_picker,locked}_{dark,light}.png`

**Interfaces:**
- Consumes: `EventForm`, `TimeSlot`, `TimeChoice`, `lengthLabel` (Task 6); `EditorPicker`, `PickerLayer`, `DatePickerCard`, `TimePickerCard` (Task 7); `EditableEvent` (Task 4); `couldNotSave` (2b-1); `HhSheet`, `HhCloseButton`, `ShellTokens`, `DarkColors` (`:core:ui`).
- Produces:
  - ```kotlin
    @Composable
    fun EventEditorSheet(
        form: EventForm,
        people: List<Person>,          // the household in order; the sheet puts Family first
        childOnly: PersonId?,          // a signed-in Child's own id, new or edit: the other Who chips are disabled
        busy: Boolean,
        failure: String?,              // the failure card's title, or null
        picker: EditorPicker,
        onClose: () -> Unit,
        onSave: () -> Unit,
        onDelete: () -> Unit,
        onRefusedWho: () -> Unit,      // a disabled Who chip was tapped
        onPicker: (EditorPicker) -> Unit,
        modifier: Modifier = Modifier,
        keyboard: WindowInsets = WindowInsets.ime, // everything but the background sits above it
        focusTitleOnOpen: Boolean = form.mode == EventForm.Mode.New,
    )
    ```
  - `const val SAVE_FAILED_BODY` (the failure card's body)
  - `Components.kt`: `@Composable internal fun DeleteButton(enabled: Boolean, tag: String, onClick: () -> Unit)` and `@Composable internal fun PrimaryButton(text: String, icon: String, enabled: Boolean, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - test tags: `editor_sheet`, `editor_summary`, `editor_title`, `who_<name>`, `day_<ISO date>`, `day_pick`, `time_all_day`, `time_Morning`, `time_Afternoon`, `time_Evening`, `time_pick`, `length_<minutes>`, `editor_locked_dates`, `editor_failure`, `editor_delete`, `editor_save`; the close button keeps `sheet_close`

- [ ] **Step 1: Check that focus works under Robolectric in touch mode**

This task's focus tests (`aNewEventFocusesTheTitle`, `doneClosesTheKeyboardWithoutSaving`, `openingAPickerClosesTheKeyboardButAChipDoesNot`) and Task 9's depend on the tablet's touch mode, which Robolectric doesn't start in. Prove the setup before relying on it. Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/TouchModeSpikeTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.input.ImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TouchModeSpikeTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun touchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    }

    @Test
    fun aRequestedFocusHoldsAndDoneClearsIt() {
        compose.setContent {
            val focus = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current
            var text by remember { mutableStateOf("") }
            LaunchedEffect(Unit) { focus.requestFocus() }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.testTag("field").focusRequester(focus),
            )
        }
        compose.onNodeWithTag("field").assertIsFocused()
        compose.onNodeWithTag("field").performImeAction()
        compose.onNodeWithTag("field").assertIsNotFocused()
    }
}
```
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*TouchModeSpikeTest*"`
Expected: PASS. If it fails, stop and report: the focus tests below can't be trusted without it. Then delete `TouchModeSpikeTest.kt`; the sheet and host tests set touch mode the same way in their `@Before`.

- [ ] **Step 2: Write the failing sheet tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorSheetTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.EditableEvent
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.TimeSlot
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens

@RunWith(AndroidJUnit4::class)
class EventEditorSheetTest {
    @get:Rule val compose = createComposeRule()

    private val london = ZoneId.of("Europe/London")
    private val calls = mutableListOf<String>()
    private var picker by mutableStateOf(EditorPicker.None)
    private var busy by mutableStateOf(false)
    private var failure by mutableStateOf<String?>(null)

    /** Focus and the keyboard behave as on the tablet only in touch mode (Step 1). */
    @Before
    fun touchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    }

    /** A new event opened at 11:54 on the hand-off's Wednesday. */
    private fun newForm(signedIn: PersonId? = null) =
        EventForm(EventForm.Mode.New, SampleUi.TODAY, LocalTime.of(11, 54), london, signedIn, preselectedDay = null)

    private fun editForm(start: EventTime, end: EventTime, title: String = "Dinner with Jo & Priya", forPerson: String? = "alex") =
        EventForm(
            EventForm.Mode.Edit(EditableEvent(EventRef("c1", "s1", "e1"), title, start, end, forPerson)),
            SampleUi.TODAY, LocalTime.of(11, 54), london, signedIn = null, preselectedDay = null,
        )

    private fun dinner(): EventForm {
        val start = SampleUi.TODAY.atTime(19, 30).atZone(london).toInstant()
        return editForm(EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)))
    }

    private fun show(form: EventForm, childOnly: PersonId? = null, focus: Boolean = false) = compose.setContent {
        CulveryTheme(dark = true) {
            EventEditorSheet(
                form = form,
                people = SampleUi.household,
                childOnly = childOnly,
                busy = busy,
                failure = failure,
                picker = picker,
                onClose = { calls += "close" },
                onSave = { calls += "save" },
                onDelete = { calls += "delete" },
                onRefusedWho = { calls += "refused" },
                onPicker = {
                    picker = it
                    calls += "picker:$it"
                },
                focusTitleOnOpen = focus,
            )
        }
    }

    @Test
    fun aNewEventSaysNewEventAndOffersNoDelete() {
        show(newForm())
        compose.onNodeWithText("New event").assertExists()
        compose.onNodeWithTag("editor_summary").assertTextEquals("Today · 14:00–15:00 · Family")
        compose.onNodeWithText("Save event").assertExists()
        compose.onNodeWithTag("editor_delete").assertDoesNotExist()
        compose.onNodeWithText("What's happening?").assertExists()
    }

    @Test
    fun saveIsDisabledUntilThereIsATitle() {
        show(newForm())
        compose.onNodeWithTag("editor_save").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("editor_title").performTextInput("   ")
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_title").performTextReplacement("Parents evening")
        compose.onNodeWithTag("editor_save").assertIsEnabled().performClick()
        assertThat(calls).containsExactly("save")
    }

    @Test
    fun saveIsDisabledWhileBusy() {
        busy = true
        show(dinner())
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_delete").assertIsNotEnabled()
    }

    @Test
    fun anEditSaysEditEventAndOffersDelete() {
        show(dinner())
        compose.onNodeWithText("Edit event").assertExists()
        compose.onNodeWithText("Save changes").assertExists()
        compose.onNodeWithTag("time_pick").assertTextEquals("19:30")
        compose.onNodeWithTag("length_90").assertIsSelected()
        compose.onNodeWithTag("who_Alex").assertIsSelected()
        compose.onNodeWithTag("editor_delete").performClick()
        assertThat(calls).containsExactly("delete")
    }

    @Test
    fun chipsChangeTheFormAndTheSummary() {
        show(newForm())
        compose.onNodeWithTag("day_${SampleUi.TODAY.plusDays(1)}").performClick()
        compose.onNodeWithTag("time_Afternoon").performClick()
        compose.onNodeWithTag("who_Mia").performClick()
        compose.onNodeWithTag("editor_summary").assertTextEquals("Tomorrow · 14:00–15:00 · Mia")
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_summary").assertTextEquals("Tomorrow · 14:00–16:00 · Mia")
    }

    @Test
    fun lengthIsHiddenForAllDay() {
        show(newForm())
        compose.onNodeWithTag("length_60").assertExists()
        compose.onNodeWithTag("time_all_day").performClick()
        compose.onNodeWithTag("length_60").assertDoesNotExist()
    }

    @Test
    fun aSignedInChildsOtherWhoChipsAreDisabledAndATapExplains() {
        val form = newForm(signedIn = SampleUi.mia.id)
        show(form, childOnly = SampleUi.mia.id)
        compose.onNodeWithTag("who_Mia").assertIsSelected()
        compose.onNodeWithTag("who_Sam").performClick()
        compose.onNodeWithTag("who_Family").performClick()
        assertThat(calls).containsExactly("refused", "refused")
        assertThat(form.who).isEqualTo(SampleUi.mia.id)
    }

    @Test
    fun aLockedMultiDayEditShowsOneLineInPlaceOfDayTimeAndLength() {
        val halfTerm = editForm(EventTime.AllDay(LocalDate.of(2026, 9, 28)), EventTime.AllDay(LocalDate.of(2026, 10, 1)), "Half term", "family")
        show(halfTerm)
        compose.onNodeWithTag("editor_locked_dates").assertExists()
        compose.onNodeWithText("Mon 28 – Wed 30 · change dates on your phone").assertExists()
        compose.onNodeWithTag("day_pick").assertDoesNotExist()
        compose.onNodeWithTag("time_all_day").assertDoesNotExist()
        compose.onNodeWithTag("who_Family").assertIsSelected()
    }

    @Test
    fun aFailureShowsTheCardAndSaveSaysTryAgain() {
        failure = "Couldn't save to Sample calendar — Calendar is full"
        val form = newForm()
        form.updateTitle("Car MOT")
        show(form)
        compose.onNodeWithTag("editor_failure").assertExists()
        compose.onNodeWithText("Couldn't save to Sample calendar — Calendar is full").assertExists()
        compose.onNodeWithText(SAVE_FAILED_BODY).assertExists()
        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithText("Save event").assertDoesNotExist()
    }

    @Test
    fun pickDateOpensTheDatePickerAndAPickSetsTheDay() {
        val form = newForm()
        show(form)
        compose.onNodeWithTag("day_pick").performClick()
        compose.onNodeWithTag("date_picker").assertExists()
        compose.onNodeWithTag("date_cell_2026-10-05").performClick()
        assertThat(form.day).isEqualTo(LocalDate.of(2026, 10, 5))
        compose.onNodeWithTag("date_picker").assertDoesNotExist()
        compose.onNodeWithTag("day_pick").assertTextEquals("Mon 5 Oct").assertIsSelected()
        assertThat(calls).containsExactly("picker:Date", "picker:None").inOrder()
    }

    @Test
    fun pickTimeSetsACustomTime() {
        val form = newForm()
        show(form)
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("hour_value").assertTextEquals("14")
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("minute_up").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(15, 15)))
        compose.onNodeWithTag("time_pick").assertTextEquals("15:15").assertIsSelected()
    }

    @Test
    fun aPickedSlotTimeSelectsTheSlotChip() {
        val form = newForm()
        show(form)
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(form.time).isEqualTo(TimeChoice.Slot(TimeSlot.Evening))
        compose.onNodeWithTag("time_Evening").assertIsSelected()
    }

    @Test
    fun aNewEventFocusesTheTitle() {
        show(newForm(), focus = true)
        compose.onNodeWithTag("editor_title").assertIsFocused()
    }

    @Test
    fun anEditOpensWithNothingFocused() {
        show(dinner(), focus = false)
        compose.onNodeWithTag("editor_title").assertIsNotFocused()
    }

    @Test
    fun doneClosesTheKeyboardWithoutSaving() {
        val form = newForm()
        form.updateTitle("Parents evening")
        show(form, focus = true)
        compose.onNodeWithTag("editor_title").performImeAction()
        compose.onNodeWithTag("editor_title").assertIsNotFocused()
        assertThat(calls).doesNotContain("save")
    }

    @Test
    fun openingAPickerClosesTheKeyboardButAChipDoesNot() {
        show(newForm(), focus = true)
        compose.onNodeWithTag("time_Evening").performClick()
        compose.onNodeWithTag("editor_title").assertIsFocused()
        compose.onNodeWithTag("day_pick").performClick()
        compose.onNodeWithTag("editor_title").assertIsNotFocused()
    }

    @Test
    fun theCloseButtonCloses() {
        show(newForm())
        compose.onNodeWithTag("sheet_close").performClick()
        assertThat(calls).containsExactly("close")
    }

    /** `WindowInsets.ime` is zero under Robolectric, so the hand-off's 320 dp keyboard on its 800 dp screen is passed in. */
    @Test
    fun saveStaysAboveTheKeyboard() {
        val screen = 800.dp
        val keyboard = 320.dp
        val form = newForm()
        form.updateTitle("Parents evening")
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(ShellTokens.sheetWidth, screen)) {
                    EventEditorSheet(
                        form = form, people = SampleUi.household, childOnly = null, busy = false, failure = null,
                        picker = EditorPicker.None, onClose = {}, onSave = { calls += "save" }, onDelete = {},
                        onRefusedWho = {}, onPicker = {}, keyboard = WindowInsets(bottom = keyboard),
                    )
                }
            }
        }
        val save = compose.onNodeWithTag("editor_save").getUnclippedBoundsInRoot()
        assertThat(save.bottom).isAtMost(screen - keyboard)
        // Hand-off 10: the header, the Title and the first rows of chips stay in view; the rest scrolls.
        compose.onNodeWithText("New event").assertIsDisplayed()
        compose.onNodeWithTag("editor_title").assertIsDisplayed()
        compose.onNodeWithTag("who_Family").assertIsDisplayed()
        compose.onNodeWithTag("day_${SampleUi.TODAY}").assertIsDisplayed()
        compose.onNodeWithTag("editor_save").performClick()
        assertThat(calls).containsExactly("save")
    }
}
```

`aNewEventFocusesTheTitle` and `anEditOpensWithNothingFocused` pass `focus` explicitly; the default (`form.mode == EventForm.Mode.New`) is what the host uses, and Task 9's host tests open both kinds through it.

- [ ] **Step 3: Write the screenshot tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EditorScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditableEvent
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.TimeSlot
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens

/** The 800 dp screen less the hand-off's 320 dp keyboard (hand-off 10). */
private val ABOVE_THE_KEYBOARD = 480.dp

/**
 * Hand-off 10–16 as the shell shows the sheet: against the right edge over the scrim, on the 1280×800 canvas, at
 * 11:54 on Wednesday 23 September. Nothing is focused, so no cursor blinks into the images.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val london = ZoneId.of("Europe/London")

    private fun newForm(signedIn: PersonId? = null) =
        EventForm(EventForm.Mode.New, SampleUi.TODAY, LocalTime.of(11, 54), london, signedIn, preselectedDay = null)

    private fun snap(
        name: String,
        dark: Boolean,
        form: EventForm,
        height: Dp? = null,
        childOnly: PersonId? = null,
        failure: String? = null,
        picker: EditorPicker = EditorPicker.None,
    ) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
                    Box(Modifier.align(Alignment.TopEnd).then(if (height != null) Modifier.height(height) else Modifier.fillMaxHeight())) {
                        EventEditorSheet(
                            form = form,
                            people = SampleUi.household,
                            childOnly = childOnly,
                            busy = false,
                            failure = failure,
                            picker = picker,
                            onClose = {},
                            onSave = {},
                            onDelete = {},
                            onRefusedWho = {},
                            onPicker = {},
                            focusTitleOnOpen = false,
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /** Hand-off 10: "Parents evening" at 18:00, the sheet as tall as the space above the keyboard. */
    private fun keyboard() = newForm().apply {
        updateTitle("Parents evening")
        chooseTime(TimeChoice.Slot(TimeSlot.Evening))
    }

    /** Hand-off 11: no title yet, Saturday, All day. */
    private fun empty() = newForm().apply {
        chooseDay(SampleUi.TODAY.plusDays(3))
        chooseTime(TimeChoice.AllDay)
    }

    /** Hand-off 12: Mia signed in, her sleepover on Sunday evening. */
    private fun child() = newForm(signedIn = SampleUi.mia.id).apply {
        updateTitle("Sleepover at Ava's")
        chooseDay(SampleUi.TODAY.plusDays(4))
        chooseTime(TimeChoice.Slot(TimeSlot.Evening))
    }

    /** Hand-off 13: dinner at 19:30 for 2 h; the 19:30 start shows on Pick time… (2b-2 design D3). */
    private fun edit() = dinner(hours = 2)

    /** 2b-2 design D3: an event 1 h 30 long adds its own length as a fourth Length chip, selected. */
    private fun ownLength() = dinner(minutes = 90)

    private fun dinner(hours: Long = 0, minutes: Long = 0): EventForm {
        val start = SampleUi.TODAY.atTime(19, 30).atZone(london).toInstant()
        val end = start.plusSeconds((hours * 60 + minutes) * 60)
        val event = EditableEvent(EventRef("c1", "s1", "e1"), "Dinner with Jo & Priya", EventTime.Timed(start), EventTime.Timed(end), SampleUi.alex.id.value)
        return EventForm(EventForm.Mode.Edit(event), SampleUi.TODAY, LocalTime.of(11, 54), london, null, null)
    }

    /** Hand-off 14: the car's MOT on Friday morning, refused. */
    private fun failed() = newForm(signedIn = SampleUi.alex.id).apply {
        updateTitle("Car MOT")
        chooseDay(SampleUi.TODAY.plusDays(2))
        chooseTime(TimeChoice.Slot(TimeSlot.Morning))
    }

    /** Hand-off 15: a half-term trip on Monday 5 October, with the date picker open. */
    private fun datePicker() = newForm().apply {
        updateTitle("Half-term trip")
        chooseDay(LocalDate.of(2026, 10, 5))
        chooseTime(TimeChoice.Slot(TimeSlot.Evening))
    }

    /** Hand-off 16: a haircut at 16:15 today, with the time picker open. */
    private fun timePicker() = newForm().apply {
        updateTitle("Haircut")
        chooseTime(TimeChoice.Custom(LocalTime.of(16, 15)))
    }

    /** 2b-2 design D3: an all-day event from Monday 28 September to Wednesday 30, dates locked. */
    private fun locked(): EventForm {
        val visit = EditableEvent(
            EventRef("c1", "s1", "e2"), "Grandma visiting",
            EventTime.AllDay(LocalDate.of(2026, 9, 28)), EventTime.AllDay(LocalDate.of(2026, 10, 1)), "family",
        )
        return EventForm(EventForm.Mode.Edit(visit), SampleUi.TODAY, LocalTime.of(11, 54), london, null, null)
    }

    private val failure = couldNotSave("Sample calendar", "Calendar is full")

    @Test fun keyboardDark() = snap("editor_keyboard_dark", true, keyboard(), height = ABOVE_THE_KEYBOARD)
    @Test fun keyboardLight() = snap("editor_keyboard_light", false, keyboard(), height = ABOVE_THE_KEYBOARD)
    @Test fun emptyDark() = snap("editor_empty_dark", true, empty())
    @Test fun emptyLight() = snap("editor_empty_light", false, empty())
    @Test fun childDark() = snap("editor_child_dark", true, child(), childOnly = SampleUi.mia.id)
    @Test fun childLight() = snap("editor_child_light", false, child(), childOnly = SampleUi.mia.id)
    @Test fun editDark() = snap("editor_edit_dark", true, edit())
    @Test fun editLight() = snap("editor_edit_light", false, edit())
    @Test fun ownLengthDark() = snap("editor_own_length_dark", true, ownLength())
    @Test fun ownLengthLight() = snap("editor_own_length_light", false, ownLength())
    @Test fun failedDark() = snap("editor_failed_dark", true, failed(), failure = failure)
    @Test fun failedLight() = snap("editor_failed_light", false, failed(), failure = failure)
    @Test fun datePickerDark() = snap("editor_date_picker_dark", true, datePicker(), picker = EditorPicker.Date)
    @Test fun datePickerLight() = snap("editor_date_picker_light", false, datePicker(), picker = EditorPicker.Date)
    @Test fun timePickerDark() = snap("editor_time_picker_dark", true, timePicker(), picker = EditorPicker.Time)
    @Test fun timePickerLight() = snap("editor_time_picker_light", false, timePicker(), picker = EditorPicker.Time)
    @Test fun lockedDark() = snap("editor_locked_dark", true, locked())
    @Test fun lockedLight() = snap("editor_locked_light", false, locked())
}
```

- [ ] **Step 4: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventEditorSheetTest*" --tests "*EditorScreenshotTest*"`
Expected: compilation FAILS: `EventEditorSheet` and `SAVE_FAILED_BODY` are unresolved.

- [ ] **Step 5: Add the sheet's styles and dimensions**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`:

1. Add at the end of `CalendarType`, before its closing brace:
```kotlin

    /** 30 sp / 700, −0.5 tracking: "New event", "Edit event". */
    val editorTitle = HhType.screenTitle.copy(fontSize = 30.sp, letterSpacing = (-0.5).sp)

    /** 15 sp / 400: the add/edit sheet's live summary. */
    val editorSummary = subtitle

    /** 22 sp / 600: the Title field. */
    val titleField = HhType.sectionTitle.copy(fontWeight = FontWeight.W600)

    /** 13 sp / 700, 0.5 tracking: WHO, DAY, TIME, LENGTH. */
    val sectionLabel = HhType.label.copy(fontWeight = FontWeight.W700, letterSpacing = 0.5.sp)

    /** 16 sp / 600: choice chips. */
    val chip = personChip

    /** 16 sp / 500: a time chip's "09:00". */
    val chipSecondary = HhType.body.copy(fontWeight = FontWeight.W500)

    /** 18 sp / 700: Save, Save changes, Try again, Edit. */
    val primaryButton = HhType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.W700)

    /** 16 sp / 600: a locked multi-day edit's dates line. */
    val lockedDates = personChip
```
2. In `CalendarDimens`, before `/** Chip tint: …`, add:
```kotlin
    // Add/edit sheet (hand-off §7 Sheet 2 and Culvery.dc.html): padding 24×30×20; the summary 4 below the title and
    // ✕ 16 away; the body 4 below the Title field, 20 between sections, each label 10 above its chips.
    val editorPaddingTop = 24.dp
    val editorPaddingBottom = 20.dp
    val editorHeaderGap = 16.dp
    val editorSummaryTop = 4.dp
    val editorBodyTop = 4.dp
    val editorBodyGap = 20.dp
    val sectionGap = 10.dp

    // Title field: 64 dp, radius 18, `surf`, padding 0 20, a 2 dp `accent` border while focused.
    val titleHeight = 64.dp
    val titleRadius = 18.dp
    val titlePaddingH = 20.dp
    val titleBorder = 2.dp

    // Choice chips: 48 dp, padding 0 18, radius 24, 8 apart; a 12 dp dot or 20 dp icon 8 from the label.
    val choiceChipHeight = 48.dp
    val choiceChipPaddingH = 18.dp
    val choiceChipRadius = 24.dp
    val choiceChipGap = 8.dp
    val choiceChipIconGap = 8.dp
    val choiceChipDot = 12.dp
    val choiceChipIcon = 20.dp

    // The locked dates line and the failure card: radius 18, padding 14×16, the icon 12 from the text;
    // `date_range` 22 dp, `cloud_off` 24 dp, the failure body 2 below its title.
    val editorCardRadius = 18.dp
    val editorCardPaddingV = 14.dp
    val editorCardPaddingH = 16.dp
    val editorCardIconGap = 12.dp
    val lockedIcon = 22.dp
    val failureIcon = 24.dp
    val failureBodyTop = 2.dp

    // Footers: 10 between Delete and the main button; the main button's icon 10 from its label.
    val footerGap = 10.dp
    val primaryIconGap = 10.dp

    /** Hand-off §7: a disabled chip is drawn at 38% and stays tappable; a time chip's "09:00" is at 72%. */
    const val DISABLED_CHIP_ALPHA = 0.38f
    const val CHIP_SECONDARY_ALPHA = 0.72f

```

- [ ] **Step 6: Share the footer buttons**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Components.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon

/** Card-header pill ("Week"): hand-off §7, 44 dp tall, radius 22, `surf2` on the whole 44 dp box. */
@Composable
internal fun HeaderChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(CalendarDimens.touchTarget)
            .clip(RoundedCornerShape(CalendarDimens.pillRadius))
            .background(c.surf2)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.pillPaddingH),
    ) {
        Text(text, style = CalendarType.pill, color = c.ink, maxLines = 1)
    }
}

/** The person-colour bar at the start of an event row; fills the row's intrinsic height. */
@Composable
internal fun ColourBar(color: Color, width: Dp) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(width / 2))
            .background(color),
    )
}

/** The sheets' Delete (hand-off §7): 60 dp, padding 0 26, radius 30, `surf2`, `danger` 17 sp / 700 with `delete`. */
@Composable
internal fun DeleteButton(enabled: Boolean, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerIconGap),
        modifier = Modifier
            .testTag(tag)
            .height(CalendarDimens.footerButtonHeight)
            .clip(RoundedCornerShape(CalendarDimens.footerButtonRadius))
            .background(c.surf2)
            .clickable(enabled = enabled, onClickLabel = "Delete", onClick = onClick)
            .padding(horizontal = CalendarDimens.deleteButtonPaddingH),
    ) {
        HhIcon("delete", size = CalendarDimens.footerIcon, tint = c.danger)
        Text("Delete", style = CalendarType.footerButton, color = c.danger)
    }
}

/**
 * A sheet's main action (hand-off §7 Save, Edit): 60 dp, radius 30, `accent`, a 24 dp icon 10 from its 18 sp / 700
 * label. Disabled, it is `surf2` with `mute` text and does nothing.
 */
@Composable
internal fun PrimaryButton(
    text: String,
    icon: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val content = if (enabled) c.accentInk else c.mute
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.primaryIconGap, Alignment.CenterHorizontally),
        modifier = modifier
            .testTag(tag)
            .height(CalendarDimens.footerButtonHeight)
            .clip(RoundedCornerShape(CalendarDimens.footerButtonRadius))
            .background(if (enabled) c.accent else c.surf2)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        HhIcon(icon, size = CalendarDimens.footerIcon, tint = content)
        Text(text, style = CalendarType.primaryButton, color = content, maxLines = 1)
    }
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheet.kt`:
- replace `DeleteButton(enabled = !busy, onClick = onDelete)` with `DeleteButton(enabled = !busy, tag = "detail_delete", onClick = onDelete)`;
- delete the whole private `DeleteButton` composable (from `@Composable` above `private fun DeleteButton(` to its closing brace).

- [ ] **Step 7: Write the sheet**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorSheet.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.TimeSlot
import uk.co.siland.culvery.capability.calendar.lengthLabel
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.ShellTokens

/** Hand-off §7: the failure card's body. */
const val SAVE_FAILED_BODY = "Nothing was changed. Your details are still here — check the connection and try again."

/** A selected Who chip's ink on the person's colour: #0E1011 in both themes (hand-off §7), the dark theme's `bg`. */
private val PersonChipInk: Color = DarkColors.bg

private val HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm")

// ENGLISH, as elsewhere in the calendar: the hand-off's "Fri", "Sat".
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

/**
 * Hand-off §7 "Sheet 2 — Quick-add / edit" (2b-2 design §4.2). Stateless apart from the [form] it edits: the host
 * decides [busy], [failure] and which [picker] shows. [childOnly] is a signed-in Child's own id, in a new event or an
 * edit (§6): every other Who chip is drawn disabled and a tap on one calls [onRefusedWho]. The pickers draw inside
 * the sheet's box. Save, ✕, Done and opening a picker close the keyboard; tapping a chip doesn't. Everything but the
 * background sits above [keyboard].
 */
@Composable
fun EventEditorSheet(
    form: EventForm,
    people: List<Person>,
    childOnly: PersonId?,
    busy: Boolean,
    failure: String?,
    picker: EditorPicker,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRefusedWho: () -> Unit,
    onPicker: (EditorPicker) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: WindowInsets = WindowInsets.ime,
    focusTitleOnOpen: Boolean = form.mode == EventForm.Mode.New,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val titleFocus = remember { FocusRequester() }
    if (focusTitleOnOpen) {
        LaunchedEffect(Unit) {
            titleFocus.requestFocus()
            keyboard?.show()
        }
    }
    val closeKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val everyone = remember(people) { listOf(Person.Family) + people }
    Box(modifier.testTag("editor_sheet").width(ShellTokens.sheetWidth).fillMaxHeight()) {
        HhSheet(
            padding = PaddingValues(
                start = CalendarDimens.sheetPaddingH,
                end = CalendarDimens.sheetPaddingH,
                top = CalendarDimens.editorPaddingTop,
                bottom = CalendarDimens.editorPaddingBottom,
            ),
        ) {
            // With the keyboard up (about 320 dp) the sheet's content is about 480 dp tall: the header, Title and
            // footer stay put and the chips scroll (2b-2 design §4.4). The background still runs behind the keyboard.
            Column(
                verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
                modifier = Modifier.fillMaxSize().windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom)),
            ) {
                Header(form, everyone) {
                    closeKeyboard()
                    onClose()
                }
                TitleField(form, titleFocus, onDone = closeKeyboard)
                Column(
                    verticalArrangement = Arrangement.spacedBy(CalendarDimens.editorBodyGap),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = CalendarDimens.editorBodyTop),
                ) {
                    WhoSection(form, everyone, childOnly, onRefusedWho)
                    if (form.datesLocked) {
                        LockedDates(form.lockedDatesLabel.orEmpty())
                    } else {
                        DaySection(form) {
                            closeKeyboard()
                            onPicker(EditorPicker.Date)
                        }
                        TimeSection(form) {
                            closeKeyboard()
                            onPicker(EditorPicker.Time)
                        }
                        if (form.time != TimeChoice.AllDay) LengthSection(form)
                    }
                }
                if (failure != null) FailureCard(failure)
                Footer(
                    form = form,
                    busy = busy,
                    failed = failure != null,
                    onSave = {
                        closeKeyboard()
                        onSave()
                    },
                    onDelete = {
                        closeKeyboard()
                        onDelete()
                    },
                )
            }
        }
        when (picker) {
            EditorPicker.None -> Unit
            EditorPicker.Date -> PickerLayer(onDismiss = { onPicker(EditorPicker.None) }) {
                DatePickerCard(
                    today = form.today,
                    selected = form.day,
                    onPick = {
                        form.chooseDay(it)
                        onPicker(EditorPicker.None)
                    },
                    onCancel = { onPicker(EditorPicker.None) },
                )
            }
            EditorPicker.Time -> PickerLayer(onDismiss = { onPicker(EditorPicker.None) }) {
                TimePickerCard(
                    initial = form.pickerTime,
                    onSet = {
                        form.chooseTime(TimeChoice.Custom(it))
                        onPicker(EditorPicker.None)
                    },
                    onCancel = { onPicker(EditorPicker.None) },
                )
            }
        }
    }
}

@Composable
private fun Header(form: EventForm, everyone: List<Person>, onClose: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.editorHeaderGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (form.mode == EventForm.Mode.New) "New event" else "Edit event", style = CalendarType.editorTitle, color = c.ink)
            Spacer(Modifier.height(CalendarDimens.editorSummaryTop))
            Text(
                // A tag whose person has left has no name here, and the line ends at the time.
                form.summary { id -> everyone.firstOrNull { it.id == id }?.name },
                style = CalendarType.editorSummary,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("editor_summary"),
            )
        }
        HhCloseButton(onClick = onClose)
    }
}

/** 64 dp, `surf`, 22 sp / 600, "What's happening?", one line; Done closes the keyboard and never saves. */
@Composable
private fun TitleField(form: EventForm, focus: FocusRequester, onDone: () -> Unit) {
    val c = Culvery.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(CalendarDimens.titleRadius)
    BasicTextField(
        value = form.title,
        onValueChange = { form.updateTitle(it) },
        singleLine = true,
        textStyle = CalendarType.titleField.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .testTag("editor_title")
            .fillMaxWidth()
            .height(CalendarDimens.titleHeight)
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(c.surf)
                    .then(if (focused) Modifier.border(CalendarDimens.titleBorder, c.accent, shape) else Modifier)
                    .padding(horizontal = CalendarDimens.titlePaddingH),
            ) {
                if (form.title.isEmpty()) Text("What's happening?", style = CalendarType.titleField, color = c.mute, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
private fun Section(label: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.sectionGap)) {
        Text(label, style = CalendarType.sectionLabel, color = Culvery.colors.mute)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.choiceChipGap),
            verticalArrangement = Arrangement.spacedBy(CalendarDimens.choiceChipGap),
        ) {
            chips()
        }
    }
}

@Composable
private fun WhoSection(form: EventForm, everyone: List<Person>, childOnly: PersonId?, onRefusedWho: () -> Unit) {
    Section("WHO") {
        everyone.forEach { person ->
            val selected = form.who == person.id
            val enabled = childOnly == null || person.id == childOnly
            val colour = Color(person.color)
            ChoiceChip(
                label = person.name,
                selected = selected,
                tag = "who_${person.name}",
                enabled = enabled,
                selectedColor = colour,
                selectedInk = PersonChipInk,
                leading = { ink ->
                    if (selected) {
                        HhIcon("check", size = CalendarDimens.choiceChipIcon, tint = ink)
                    } else {
                        Box(Modifier.size(CalendarDimens.choiceChipDot).clip(CircleShape).background(colour))
                    }
                },
                // The chosen chip may be a disabled one (an adult tagged the child's event); a tap on it does nothing.
                onClick = { if (enabled || selected) form.chooseWho(person.id) else onRefusedWho() },
            )
        }
    }
}

@Composable
private fun DaySection(form: EventForm, onPickDate: () -> Unit) {
    Section("DAY") {
        form.dayChoices.forEachIndexed { i, date ->
            ChoiceChip(
                label = when (i) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> date.format(WEEKDAY)
                },
                selected = form.day == date,
                tag = "day_$date",
                onClick = { form.chooseDay(date) },
            )
        }
        val picked = form.pickedDateLabel
        ChoiceChip(
            label = picked ?: "Pick date…",
            selected = picked != null,
            tag = "day_pick",
            leading = { ink -> HhIcon("calendar_month", size = CalendarDimens.choiceChipIcon, tint = ink) },
            onClick = onPickDate,
        )
    }
}

@Composable
private fun TimeSection(form: EventForm, onPickTime: () -> Unit) {
    Section("TIME") {
        ChoiceChip("All day", form.time == TimeChoice.AllDay, "time_all_day", onClick = { form.chooseTime(TimeChoice.AllDay) })
        TimeSlot.entries.forEach { slot ->
            ChoiceChip(
                label = slot.label,
                selected = form.time == TimeChoice.Slot(slot),
                tag = "time_${slot.name}",
                secondary = slot.time.format(HOURS_MINUTES),
                onClick = { form.chooseTime(TimeChoice.Slot(slot)) },
            )
        }
        val custom = form.time as? TimeChoice.Custom
        ChoiceChip(
            label = custom?.time?.format(HOURS_MINUTES) ?: "Pick time…",
            selected = custom != null,
            tag = "time_pick",
            leading = { ink -> HhIcon("schedule", size = CalendarDimens.choiceChipIcon, tint = ink) },
            onClick = onPickTime,
        )
    }
}

@Composable
private fun LengthSection(form: EventForm) {
    Section("LENGTH") {
        form.lengths.forEach { length ->
            ChoiceChip(lengthLabel(length), form.length == length, "length_${length.toMinutes()}", onClick = { form.chooseLength(length) })
        }
    }
}

/**
 * Hand-off §7 chip: 48 dp, padding 0 18, radius 24, 16 sp / 600; `surf`/`ink`, or [selectedColor]/[selectedInk] when
 * selected. A disabled chip is drawn at 38% but stays tappable, so a tap can explain why.
 */
@Composable
private fun ChoiceChip(
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
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.choiceChipIconGap),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.selected = selected }
            .alpha(if (enabled) 1f else CalendarDimens.DISABLED_CHIP_ALPHA)
            .height(CalendarDimens.choiceChipHeight)
            .clip(RoundedCornerShape(CalendarDimens.choiceChipRadius))
            .background(if (selected) selectedColor else c.surf)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.choiceChipPaddingH),
    ) {
        leading?.invoke(ink)
        Text(label, style = CalendarType.chip, color = ink, maxLines = 1)
        if (secondary != null) {
            Text(secondary, style = CalendarType.chipSecondary, color = ink.copy(alpha = CalendarDimens.CHIP_SECONDARY_ALPHA), maxLines = 1)
        }
    }
}

/** 2b-2 design D3: a multi-day event's dates can't change here, so Day, Time and Length become this one line. */
@Composable
private fun LockedDates(label: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.editorCardIconGap),
        modifier = Modifier
            .testTag("editor_locked_dates")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.editorCardRadius))
            .background(c.surf)
            .padding(horizontal = CalendarDimens.editorCardPaddingH, vertical = CalendarDimens.editorCardPaddingV),
    ) {
        HhIcon("date_range", size = CalendarDimens.lockedIcon, tint = c.mute)
        Text(label, style = CalendarType.lockedDates, color = c.ink)
    }
}

/** Hand-off 14: the calendar refused the save; the details are still in the sheet. */
@Composable
private fun FailureCard(title: String) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.editorCardIconGap),
        modifier = Modifier
            .testTag("editor_failure")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.editorCardRadius))
            .background(c.dangerSoft)
            .padding(horizontal = CalendarDimens.editorCardPaddingH, vertical = CalendarDimens.editorCardPaddingV),
    ) {
        HhIcon("cloud_off", size = CalendarDimens.failureIcon, tint = c.danger)
        Column {
            // A long provider reason must not push Save off screen with the keyboard up; the body says what to do.
            Text(title, style = CalendarType.noteTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(CalendarDimens.failureBodyTop))
            Text(SAVE_FAILED_BODY, style = CalendarType.noteBody, color = c.mute)
        }
    }
}

@Composable
private fun Footer(form: EventForm, busy: Boolean, failed: Boolean, onSave: () -> Unit, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
        if (form.mode is EventForm.Mode.Edit) DeleteButton(enabled = !busy, tag = "editor_delete", onClick = onDelete)
        PrimaryButton(
            text = when {
                failed -> "Try again"
                form.mode == EventForm.Mode.New -> "Save event"
                else -> "Save changes"
            },
            icon = if (failed) "refresh" else "check",
            enabled = form.canSave && !busy,
            tag = "editor_save",
            onClick = onSave,
            modifier = Modifier.weight(1f),
        )
    }
}
```

- [ ] **Step 8: Run the sheet tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventEditorSheetTest*" --tests "*EventDetailSheetTest*"`
Expected: PASS. If the build shows a deprecation warning for `BasicTextField`, `KeyboardOptions` or `FlowRow`, or asks for an `@OptIn`, stop and ask (Global Constraints).

- [ ] **Step 9: Record the screenshots and look at every one**

Run: `./gradlew :capability:calendar:recordRoborazziDebug --tests "*EditorScreenshotTest*"`
Expected: `BUILD SUCCESSFUL` and 18 new `editor_*.png` files in `capability/calendar/src/test/screenshots/`.

Compare each with `docs/design/house_hub_handoff/screenshots/calendar-sheets/` (dark and light):
- `editor_keyboard_*` ↔ `10-add-keyboard-*`: 480 dp tall; header, Title, WHO and DAY's first row visible; Save pinned at the bottom; the chips scroll. (There is no focus border and no drawn keyboard: nothing is focused in screenshots, and the app uses the system keyboard.)
- `editor_empty_*` ↔ `11-…`: "Sat 26 Sep · All day · Family"; placeholder "What's happening?"; Save `surf2` with `mute` text; no LENGTH.
- `editor_child_*` ↔ `12-…`: Mia's chip in her pink with a `check`; Family, Alex and Sam at 38%.
- `editor_edit_*` ↔ `13-…`: the 19:30 chip selected on Pick time…; **2 h** selected, with no fourth Length chip; Delete on the left.
- `editor_own_length_*`: as `editor_edit_*`, with a fourth Length chip "1 h 30", selected (design D3; the hand-off has no such image).
- `editor_failed_*` ↔ `14-…`: the `dangerSoft` card above the footer; "Try again" with `refresh`.
- `editor_date_picker_*` ↔ `15-…`: the page "Mon 21 Sep – Sun 25 Oct" with ‹ › beside the title (design D10); today ringed; past days dimmed; "1 Oct"; Monday 5 October `accent`.
- `editor_time_picker_*` ↔ `16-…`: 16 : 15 between the steppers.
- `editor_locked_*`: "Mon 28 – Wed 30 · change dates on your phone" in place of DAY, TIME and LENGTH.

Fix any difference from the hand-off's values given in this task before going on, then re-record only what changed.

- [ ] **Step 10: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. The detail sheet's screenshots are unchanged: `DeleteButton` moved without changing how it draws.

- [ ] **Step 11: Commit**

```bash
git add capability/calendar
git commit -m "Add the add/edit sheet with its chips, pickers, failure card and screenshots"
```

---

### Task 9: `EventEditorHost`, the detail ↔ editor swaps, Edit in the detail footer, and one action at a time

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHost.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Sheets.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHost.kt` (rewrite)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheet.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHostTest.kt` (create)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheetTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/DetailScreenshotTest.kt` (modify)
- Modify (re-recorded): `capability/calendar/src/test/screenshots/detail_{editable,untagged,syncing,pin}_{dark,light}.png`

**Interfaces:**
- Consumes: `EventEditorSheet`, `DeleteButton`, `PrimaryButton` (Task 8); `EditorPicker` (Task 7); `EventForm` (Task 6); `CalendarEditor.create`, `update`, `mayDelete`, `session`, `openedAt`, `refuseOtherWho` (Task 5); `CalendarRepository.editable`, `masterLabel`, `people` (Task 4); `couldNotSave` (2b-1).
- Produces:
  - `sealed interface EditorRequest { data class New(val day: LocalDate?); data class Edit(val ref: EventRef) }`
  - `@Composable internal fun EventEditorHost(request: EditorRequest, repo: CalendarRepository, editor: CalendarEditor, onClose: () -> Unit, onDeleteAuthorised: (EventRef) -> Unit)`
  - `@Composable internal fun rememberEventAdder(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate): ((LocalDate?) -> Unit)?` — opens a new event on the given day, or today for null; null when there is nowhere to add (`masterLabel` is null)
  - `internal fun OverlayHost.showDetail(ref: EventRef, today: LocalDate, repo: CalendarRepository, editor: CalendarEditor, mode: DetailMode = DetailMode.Idle): Unit`
  - `internal fun OverlayHost.showEditor(request: EditorRequest, today: LocalDate, repo: CalendarRepository, editor: CalendarEditor): Unit`
  - `internal class SingleAction` (`busy`, `run(action)`) and `@Composable internal fun rememberSingleAction(key: Any?, onError: (Exception) -> Unit): SingleAction` (in `Sheets.kt`), shared by both hosts: an exception other than a cancellation goes to `onError`; the editor host shows `couldNotSave(label, TRY_AGAIN)` on the card, the detail host logs
  - the editor host passes `childOnly` for a signed-in Child in a new event and in an edit alike
  - `EventDetailHost(ref, today, repo, editor, onClose, initialMode: DetailMode = DetailMode.Idle, onEdit: () -> Unit = {})`
  - `EventDetailSheet(…, onAssign, onEdit: () -> Unit, modifier)`; test tag `detail_edit`

- [ ] **Step 1: Write the failing host and swap tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHostTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CHANGES_SAVED
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarPermissions
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.ChangeKind
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EVENT_ADDED
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.PendingChange
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.ScriptedWriter
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.TRY_AGAIN
import uk.co.siland.culvery.capability.calendar.TestAccess
import uk.co.siland.culvery.capability.calendar.WRITE_ATTEMPT_MS
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.testAccess
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

/**
 * The add/edit sheet wired to a real repository, editor and access rules, as EventDetailHostTest wires the detail
 * sheet; PIN pads are answered from a queue, and access and the editor share `access.toasts`.
 */
@RunWith(AndroidJUnit4::class)
class EventEditorHostTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository
    private lateinit var access: TestAccess
    private lateinit var repo: CalendarRepository
    private lateinit var editor: CalendarEditor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val london = ZoneId.of("Europe/London")
    private val day = LocalDate.of(2026, 9, 23)
    private val writer = ScriptedWriter("calendar.a")
    private val dinner = EventRef("c1", "s-family", "dinner")
    private val overlay = RecordingOverlay()
    private var closed = 0
    private val deleting = mutableListOf<EventRef>()

    /** The editor's "now", when a test moves it; otherwise the real time. */
    private var now: Long? = null

    @Before
    fun setUp() = runBlocking {
        // Focus and the keyboard behave as on the tablet only in touch mode (Task 8, Step 1).
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        access = testAccess(household, scope, WallClock { System.currentTimeMillis() }, scope)
        val zone = HouseholdZone(household)
        store.addConnection(
            Connection("c1", "calendar.a", "Sample calendar", emptyMap()),
            listOf(CalendarSource("s-family", "Family calendar", writable = true)),
            emptyMap(),
        )
        store.setMaster("c1", "s-family")
        val start = day.atTime(19, 30).atZone(london).toInstant()
        store.applySync(
            "c1", "s-family", DateRange(day.minusDays(1), day.plusDays(14), london),
            SyncResult(
                listOf(RemoteEvent("dinner", "Dinner with Jo & Priya", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), false, null, access.alex.id.value)),
                emptyList(), null, fullReplace = true,
            ),
        )
        repo = CalendarRepository(store, household, zone, emptySet(), setOf(writer))
        editor = CalendarEditor(
            store, setOf(writer), access.control, access.toasts, zone, WallClock { now ?: System.currentTimeMillis() },
            scope, {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        calendar.close()
        householdDb.close()
    }

    private fun showEditor(request: EditorRequest) = compose.setContent {
        CulveryTheme(dark = true) {
            EventEditorHost(request, repo, editor, onClose = { closed++ }, onDeleteAuthorised = { deleting += it })
        }
    }

    /** The shell's overlay as the app has it: whatever [open] shows, swaps included. */
    private fun showOverlay(open: OverlayHost.() -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay) {
                CulveryTheme(dark = true) { Box { overlay.content?.invoke() } }
            }
        }
        compose.runOnIdle { overlay.open() }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun millisAt(date: LocalDate, hour: Int, minute: Int) = date.atTime(hour, minute).atZone(london).toInstant().toEpochMilli()

    @Test
    fun aNewEventOpensWithItsTitleFocused() {
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").assertIsFocused()
        compose.onNodeWithTag("who_Family").assertIsSelected()
    }

    @Test
    fun anEditOpensOnTheEventWithNothingFocused() {
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("editor_title").assertTextEquals("Dinner with Jo & Priya").assertIsNotFocused()
        compose.onNodeWithTag("time_pick").assertTextEquals("19:30")
    }

    @Test
    fun savingANewEventAsksForThePinAddsItAndCloses() {
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("create:Parents evening")
        assertThat(writer.drafts.single().createdBy).isEqualTo(access.alex.id.value)
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Save)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
    }

    @Test
    fun tomorrowChosenAt2330IsStillThatDateWhenSavedAfterMidnight() {
        // Opened at 23:30 on Saturday 24 October; Save is tapped at 00:05, when "Tomorrow" would mean the 26th.
        now = millisAt(LocalDate.of(2026, 10, 24), 23, 30)
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Bonfire")
        compose.onNodeWithTag("day_2026-10-25").performClick()
        now = millisAt(LocalDate.of(2026, 10, 25), 0, 5)
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        // Morning on the 25th, the day the clocks go back: 09:00 GMT.
        assertThat(writer.drafts.single().start).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T09:00:00Z")))
    }

    @Test
    fun aRefusedSaveKeepsTheSheetAndItsInputAndOffersTryAgain() {
        writer.failWith = WriteRejectedException("Calendar is full")
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        waitForText("Try again")
        compose.onNodeWithText("Couldn't save to Sample calendar — Calendar is full").assertExists()
        compose.onNodeWithTag("editor_title").assertTextEquals("Parents evening")
        assertThat(closed).isEqualTo(0)
        assertThat(access.toasts.messages).isEmpty()
        // Try again clears the card at once.
        writer.failWith = null
        compose.onNodeWithTag("editor_save").performClick()
        compose.onNodeWithTag("editor_failure").assertDoesNotExist()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.created).hasSize(1)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
    }

    @Test
    fun aStoreFailureBeforeTheWriteShowsTheCardAndKeepsTheSheet() {
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        // An outbox row this version can't read, which reading the queue deletes, and a disk that refuses the delete:
        // the editor throws before it reaches the write, outside its own catch.
        runBlocking {
            calendar.useWriterConnection {
                it.execSQL(
                    "INSERT INTO outbox (connectionId, sourceId, remoteId, kind, draftJson, attempts, nextAttemptMillis, createdMillis) " +
                        "VALUES ('c1', 's-family', 'dinner', 'MOVE', NULL, 0, 0, 0)",
                )
                it.execSQL("CREATE TRIGGER fail_outbox BEFORE DELETE ON outbox BEGIN SELECT RAISE(ABORT, 'disk full'); END")
            }
        }
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        waitForText(couldNotSave("Sample calendar", TRY_AGAIN))
        compose.onNodeWithTag("editor_failure").assertExists()
        compose.onNodeWithTag("editor_save").assertIsEnabled()
        assertThat(closed).isEqualTo(0)
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun anUnchangedEditClosesWithoutAPin() {
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.requests).isEmpty()
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun savingAnEditSendsTheChange() {
        access.answer(TestAccess.SAM)
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("update:dinner")
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Edit)
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
    }

    @Test
    fun aChildSavingForFamilyIsToldAndTheSheetKeepsItsInput() {
        access.answer(TestAccess.MIA)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Pizza night")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        compose.onNodeWithTag("editor_title").assertTextEquals("Pizza night")
        assertThat(closed).isEqualTo(0)
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun aSignedInChildsOtherWhoChipsExplainByToast() {
        access.answer(TestAccess.MIA)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE_SELF) }
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("who_Mia").assertIsSelected()
        compose.onNodeWithTag("who_Sam").performClick()
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        compose.onNodeWithTag("who_Mia").assertIsSelected()
    }

    @Test
    fun aSignedInChildsEditDisablesTheOtherWhoChipsToo() {
        access.answer(TestAccess.MIA)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE_SELF) }
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        // The dinner is untagged, so Family is chosen; Mia can't move it to Sam (2b-2 design §6).
        compose.onNodeWithTag("who_Sam").performClick()
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        compose.onNodeWithTag("who_Family").assertIsSelected()
    }

    @Test
    fun saveIgnoresASecondTapWhileBusy() {
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        // A second answer is queued, so a second save that got through would reach the writer too.
        access.answer(TestAccess.ALEX, TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { writer.entered.isCompleted }
        compose.onNodeWithTag("editor_save").assertIsNotEnabled().performClick()
        gate.complete(Unit)
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("create:Parents evening")
    }

    @Test
    fun deleteAsksForThePinThenHandsOverToTheDetailSheet() {
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("editor_delete").performClick()
        compose.waitUntil(5_000) { deleting.isNotEmpty() }
        assertThat(deleting).containsExactly(dinner)
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Delete)
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun aRefusedDeleteKeepsTheEditorOpen() {
        access.answer(TestAccess.MIA)
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("editor_delete").performClick()
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(deleting).isEmpty()
        compose.onNodeWithText("Edit event").assertExists()
    }

    @Test
    fun theEditorClosesWhenItsEventHasGone() {
        showEditor(EditorRequest.Edit(EventRef("c1", "s-family", "nope")))
        compose.waitUntil(5_000) { closed > 0 }
    }

    @Test
    fun anEditWhoseEventHasGoneClosesOnSaveWithoutAPin() {
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        // Deleted on a phone, then synced, while the sheet was open.
        runBlocking { store.applyDeleted(dinner) }
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.requests).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun editInTheDetailSheetSwapsToTheEditorWithNoPin() {
        showOverlay { showDetail(dinner, day, repo, editor) }
        waitForText("Created by")
        compose.onNodeWithTag("detail_edit").performClick()
        waitForText("Edit event")
        compose.onNodeWithTag("detail_sheet").assertDoesNotExist()
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun deleteInTheEditorSwapsToTheDetailSheetAskingToConfirm() {
        access.answer(TestAccess.ALEX)
        showOverlay { showEditor(EditorRequest.Edit(dinner), day, repo, editor) }
        waitForText("Edit event")
        compose.onNodeWithTag("editor_delete").performClick()
        waitForText("Delete this event?")
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        assertThat(writer.calls).containsExactly("delete:dinner")
        assertThat(access.requests).hasSize(1)
    }

    @Test
    fun closeClosesTheEditor() {
        showOverlay { showEditor(EditorRequest.New(day = null), day, repo, editor) }
        waitForText("New event")
        compose.onNodeWithTag("sheet_close").performClick()
        compose.runOnIdle { assertThat(overlay.dismissed).isEqualTo(1) }
    }

    @Test
    fun aQueuedCreateOpensAsSyncingAndItsEditQueuesBehindIt() {
        val key = "0123456789abcdef0123456789abcdef"
        val created = EventRef("c1", "s-family", key)
        val start = day.atTime(18, 0).atZone(london).toInstant()
        runBlocking {
            store.enqueue(
                PendingChange(
                    0, "c1", "s-family", null, ChangeKind.CREATE,
                    EventDraft("Sleepover", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), access.mia.id.value, access.alex.id.value),
                    attempts = 1, nextAttemptMillis = 0, createdMillis = 0, clientKey = key,
                ),
            )
        }
        access.answer(TestAccess.ALEX)
        showOverlay { showDetail(created, day, repo, editor) }
        waitForText("Sleepover")
        compose.onNodeWithTag("detail_syncing").assertExists()
        compose.onNodeWithTag("detail_edit").performClick()
        waitForText("Edit event")
        compose.onNodeWithTag("editor_title").performTextReplacement("Sleepover at Ava's")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        // Queued behind its create, never sent ahead of it.
        assertThat(writer.calls).isEmpty()
        assertThat(runBlocking { store.pendingNow() }.map { it.kind to it.ref })
            .containsExactly(ChangeKind.CREATE to created, ChangeKind.UPDATE to created).inOrder()
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
    }
}
```

- [ ] **Step 2: Write the failing detail-sheet tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheetTest.kt`:
1. Add these imports:
```kotlin
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.unit.dp
```
2. In `show`, after `onAssign = { p: Person -> calls += "assign:${p.name}" },` add `onEdit = { calls += "edit" },`.
3. In `anotherCalendarIsReadOnlyWithNoFooter` and in `aRepeatingEventPointsToThePhoneAndShowsRepeats`, after `compose.onNodeWithTag("detail_delete").assertDoesNotExist()`, add:
```kotlin
        compose.onNodeWithTag("detail_edit").assertDoesNotExist()
```
4. In `theConfirmationReplacesTheFooterWithKeepWhereDeleteWas`, after `compose.onNodeWithTag("detail_delete").assertDoesNotExist()`, add:
```kotlin
        compose.onNodeWithTag("detail_edit").assertDoesNotExist()
```
5. Add at the end of the class:
```kotlin
    @Test
    fun editSitsRightOfDeleteAndOpensTheEditor() {
        show(SampleUi.detailEditable)
        val delete = compose.onNodeWithTag("detail_delete").getUnclippedBoundsInRoot()
        val edit = compose.onNodeWithTag("detail_edit").assertHeightIsEqualTo(60.dp).getUnclippedBoundsInRoot()
        assertThat(edit.left).isGreaterThan(delete.right)
        compose.onNodeWithTag("detail_edit").performClick()
        assertThat(calls).containsExactly("edit")
    }

    @Test
    fun anUntaggedEventCanBeEdited() {
        show(SampleUi.detailUntagged)
        compose.onNodeWithTag("detail_edit").assertExists()
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/DetailScreenshotTest.kt`, replace
```kotlin
                        EventDetailSheet(detail, SampleUi.household, mode, busy = false, {}, {}, {}, {}, {}, {})
```
with
```kotlin
                        EventDetailSheet(detail, SampleUi.household, mode, busy = false, {}, {}, {}, {}, {}, {}, {})
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventEditorHostTest*" --tests "*EventDetailSheetTest*"`
Expected: compilation FAILS: `EditorRequest`, `EventEditorHost`, `showDetail`, `showEditor` and `EventDetailSheet`'s `onEdit` are unresolved.

- [ ] **Step 4: Put Edit in the detail footer**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheet.kt`:

1. Replace the KDoc of `EventDetailSheet` with:
```kotlin
/**
 * Hand-off §7 "Sheet 1 — Event detail". Stateless: the host decides [mode] and [busy]. Buttons are never hidden
 * for permission reasons; the checks happen when they are tapped. Delete runs the guard ([onDelete]); only the
 * confirmation's "Delete event" deletes ([onConfirmDelete]). Edit ([onEdit]) shows exactly where Delete does and
 * opens the add/edit sheet without a PIN (2b-2 design D12).
 */
```
2. In the parameter list, after `onAssign: (Person) -> Unit,` add `onEdit: () -> Unit,`.
3. Replace
```kotlin
            if (mode == DetailMode.ConfirmingDelete) {
                DeleteConfirmation(e, busy, onKeep, onConfirmDelete)
            } else {
                DeleteButton(enabled = !busy, tag = "detail_delete", onClick = onDelete)
            }
```
with
```kotlin
            if (mode == DetailMode.ConfirmingDelete) {
                DeleteConfirmation(e, busy, onKeep, onConfirmDelete)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
                    DeleteButton(enabled = !busy, tag = "detail_delete", onClick = onDelete)
                    PrimaryButton("Edit", "edit", enabled = !busy, tag = "detail_edit", onClick = onEdit, modifier = Modifier.weight(1f))
                }
            }
```

- [ ] **Step 5: Write the host and the swaps**

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHost.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EditResult
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.TRY_AGAIN
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.LocalOverlayHost

private const val TAG = "EventEditorHost"

/** What the add/edit sheet was opened for. */
sealed interface EditorRequest {
    /** A new event on [day] (a week column's date), or on today when null. */
    data class New(val day: LocalDate?) : EditorRequest

    data class Edit(val ref: EventRef) : EditorRequest
}

/** The form once loaded. [form] is null when there is nothing to open: the event has gone, or there is no master. */
private class Opened(val form: EventForm?, val label: String)

private suspend fun open(request: EditorRequest, repo: CalendarRepository, editor: CalendarEditor): Opened {
    val label = repo.masterLabel.first() ?: return Opened(null, "")
    val at = editor.openedAt()
    val mode = when (request) {
        is EditorRequest.New -> EventForm.Mode.New
        is EditorRequest.Edit -> EventForm.Mode.Edit(repo.editable(request.ref).first() ?: return Opened(null, label))
    }
    val form = EventForm(
        mode = mode,
        today = at.toLocalDate(),
        now = at.toLocalTime(),
        zone = at.zone,
        signedIn = editor.session.value?.person?.id,
        preselectedDay = (request as? EditorRequest.New)?.day,
    )
    return Opened(form, label)
}

/**
 * The add/edit sheet (2b-2 design §3.2). It owns the form for this opening, runs Save and Delete one at a time (Save
 * is disabled while one runs), and closes itself when there is nothing to edit. An unchanged edit closes with no PIN.
 * A refusal keeps the sheet open with the failure card, and so does the tablet failing before the write; a queued or
 * saved change closes it. Delete authorises, then [onDeleteAuthorised] swaps to the detail sheet asking to confirm.
 * A signed-in Child's other Who chips are disabled in a new event and in an edit alike (§6).
 */
@Composable
internal fun EventEditorHost(
    request: EditorRequest,
    repo: CalendarRepository,
    editor: CalendarEditor,
    onClose: () -> Unit,
    onDeleteAuthorised: (EventRef) -> Unit,
) {
    val opened: Opened? by produceState<Opened?>(null, request) { value = open(request, repo, editor) }
    val people: List<Person>? by repo.people.collectAsState(initial = null)
    val session by editor.session.collectAsState()
    var failure by remember(request) { mutableStateOf<String?>(null) }
    var picker by remember(request) { mutableStateOf(EditorPicker.None) }
    val action = rememberSingleAction(request) { e ->
        Log.w(TAG, "Couldn't save", e)
        failure = couldNotSave(opened?.label.orEmpty(), TRY_AGAIN)
    }

    val loaded = opened ?: return
    val form = loaded.form
    if (form == null) {
        LaunchedEffect(request) { onClose() }
        return
    }
    val household = people ?: return

    fun save() {
        if (action.busy || !form.canSave) return
        if (form.unchanged) {
            onClose()
            return
        }
        failure = null
        action.run {
            val result = when (val mode = form.mode) {
                EventForm.Mode.New -> editor.create(form.draft(createdBy = null))
                is EventForm.Mode.Edit -> editor.update(mode.original.ref, form.draft(createdBy = null))
            }
            when (result) {
                EditResult.Done, EditResult.Queued, EditResult.NotEditable -> onClose()
                is EditResult.Rejected -> failure = couldNotSave(loaded.label, result.message)
                EditResult.Cancelled -> Unit
            }
        }
    }

    fun delete() {
        val mode = form.mode as? EventForm.Mode.Edit ?: return
        action.run { if (editor.mayDelete(mode.original.ref)) onDeleteAuthorised(mode.original.ref) }
    }

    EventEditorSheet(
        form = form,
        people = household,
        childOnly = session?.takeIf { it.role == Role.CHILD }?.person?.id,
        busy = action.busy,
        failure = failure,
        picker = picker,
        onClose = onClose,
        onSave = { save() },
        onDelete = { delete() },
        onRefusedWho = { session?.person?.name?.let(editor::refuseOtherWho) },
        onPicker = { picker = it },
    )
}

/**
 * Opens the add/edit sheet for a new event on a day (a week column's), or on today for null. Null when there is
 * nowhere to add to (no writable master calendar with a writer), which hides the add entry points (2b-2 design §4.1).
 */
@Composable
internal fun rememberEventAdder(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate): ((LocalDate?) -> Unit)? {
    val overlay = LocalOverlayHost.current
    val addTo: String? by repo.masterLabel.collectAsState(initial = null)
    val add: (LocalDate?) -> Unit = remember(overlay, repo, editor, today) {
        { day -> overlay.showEditor(EditorRequest.New(day), today, repo, editor) }
    }
    return add.takeIf { addTo != null }
}
```

Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Sheets.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.core.plugin.OverlayHost

/**
 * The calendar's two sheets swap through the one overlay (2b-2 design D4): the detail sheet's Edit shows the
 * editor, and the editor's Delete, once authorised, shows the detail sheet asking to confirm. ✕ or the scrim closes
 * whichever is showing. Both declare Unit: each calls the other, so neither type can be inferred.
 */
internal fun OverlayHost.showDetail(
    ref: EventRef,
    today: LocalDate,
    repo: CalendarRepository,
    editor: CalendarEditor,
    mode: DetailMode = DetailMode.Idle,
): Unit = show {
    EventDetailHost(
        ref = ref,
        today = today,
        repo = repo,
        editor = editor,
        onClose = { dismiss() },
        initialMode = mode,
        onEdit = { showEditor(EditorRequest.Edit(ref), today, repo, editor) },
    )
}

internal fun OverlayHost.showEditor(request: EditorRequest, today: LocalDate, repo: CalendarRepository, editor: CalendarEditor): Unit = show {
    EventEditorHost(
        request = request,
        repo = repo,
        editor = editor,
        onClose = { dismiss() },
        onDeleteAuthorised = { ref -> showDetail(ref, today, repo, editor, DetailMode.ConfirmingDelete) },
    )
}

/**
 * One action at a time for a sheet: while one runs, [busy] is true and further taps are ignored. An exception the
 * action lets through (the tablet's own store failing before the editor's write) goes to [onError] instead of
 * crashing the app; the sheet closing mid-action cancels it, which is not an error.
 */
internal class SingleAction(private val scope: CoroutineScope, private val onError: (Exception) -> Unit) {
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

/** A [SingleAction] for one opening of a sheet: a new [key] (another event or request) starts a fresh one. */
@Composable
internal fun rememberSingleAction(key: Any?, onError: (Exception) -> Unit): SingleAction {
    val scope = rememberCoroutineScope()
    val currentOnError by rememberUpdatedState(onError)
    return remember(key) { SingleAction(scope) { currentOnError(it) } }
}
```

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHost.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EditResult
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.LocalOverlayHost

private const val TAG = "EventDetailHost"

/** Wraps a loaded detail so "still loading" (null) differs from "gone" (Loaded(null)). */
private class Loaded(val detail: EventDetailUi?)

/**
 * The detail sheet for [ref], kept live from the repository: it closes itself when the event disappears.
 * Only one action runs at a time; while one runs, further taps are ignored, and one that fails before the editor's
 * write (the tablet's store) is logged rather than crashing the app. The editor toasts each outcome; the host only
 * decides whether the sheet stays open. [initialMode] is ConfirmingDelete when the editor's Delete handed over
 * (2b-2 design D4); [onEdit] swaps to the add/edit sheet.
 */
@Composable
internal fun EventDetailHost(
    ref: EventRef,
    today: LocalDate,
    repo: CalendarRepository,
    editor: CalendarEditor,
    onClose: () -> Unit,
    initialMode: DetailMode = DetailMode.Idle,
    onEdit: () -> Unit = {},
) {
    val loaded: Loaded? by remember(ref, today) { repo.event(ref, today).map { Loaded(it) } }.collectAsState(initial = null)
    val people: List<Person> by repo.people.collectAsState(initial = emptyList())
    var mode by remember(ref) { mutableStateOf(initialMode) }
    val action = rememberSingleAction(ref) { e -> Log.w(TAG, "Couldn't change an event", e) }

    val state = loaded ?: return
    val detail = state.detail
    if (detail == null) {
        LaunchedEffect(ref) { onClose() }
        return
    }

    EventDetailSheet(
        detail = detail,
        people = people,
        mode = mode,
        busy = action.busy,
        onClose = onClose,
        onDelete = { action.run { if (editor.mayDelete(ref)) mode = DetailMode.ConfirmingDelete } },
        onKeep = { mode = DetailMode.Idle },
        onConfirmDelete = {
            action.run {
                when (editor.delete(ref)) {
                    EditResult.Done, EditResult.Queued -> onClose()
                    is EditResult.Rejected -> mode = DetailMode.Idle
                    EditResult.Cancelled -> Unit
                    EditResult.NotEditable -> onClose()
                }
            }
        },
        onChoosePerson = { mode = DetailMode.ChoosingPerson },
        onAssign = { person ->
            action.run {
                when (editor.assign(ref, person.id)) {
                    EditResult.Done, EditResult.Queued -> mode = DetailMode.Idle
                    is EditResult.Rejected, EditResult.Cancelled -> Unit
                    EditResult.NotEditable -> onClose()
                }
            }
        },
        onEdit = onEdit,
    )
}

/** Opens an event's detail sheet through the shell's overlay host; its Edit swaps to the add/edit sheet. */
@Composable
internal fun rememberEventOpener(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate): (EventRef) -> Unit {
    val overlay = LocalOverlayHost.current
    return remember(overlay, repo, editor, today) {
        { ref -> overlay.showDetail(ref, today, repo, editor) }
    }
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventEditorHostTest*" --tests "*EventDetailSheetTest*" --tests "*EventDetailHostTest*" --tests "*OpenEventTest*"`
Expected: PASS. `EventDetailHostTest` (2b-1) passes unchanged: the shared single action behaves as its own `run` did.

- [ ] **Step 7: Re-record the detail sheet and look at it**

Run: `./gradlew :capability:calendar:recordRoborazziDebug --tests "*DetailScreenshotTest*"`
Expected: `detail_editable_*`, `detail_untagged_*`, `detail_syncing_*` and `detail_pin_*` change: the footer is now Delete (unchanged, on the left) and a green Edit filling the rest, 10 dp apart, both 60 dp tall, as `03-detail-editable-*` shows. `detail_delete_confirm_*`, `detail_readonly_feed_*` and `detail_recurring_*` are unchanged. Look at every changed image.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add capability/calendar
git commit -m "Open the add/edit sheet from the detail sheet's Edit, hand its Delete back, and run one action at a time in both"
```

---

### Task 10: The keyboard — `adjustResize` and toasts above the IME

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/OverlayLayersTest.kt` (modify)

**Interfaces:**
- Consumes: `EventEditorSheet` with its `keyboard` parameter, `EditorPicker` (Task 8); `EventForm` (Task 6); `EventEditorHost` (Task 9); `ShellLayers`, `OverlayLayer`, `ToastLayer` (2b-1).
- Produces:
  - `ToastLayer(toast: ToastMessage?, keyboard: WindowInsets = WindowInsets.ime, onHidden: (Long) -> Unit)` — the toast sits `ShellTokens.toastBottom` (28 dp) above [keyboard]; `onHidden` stays last, so a trailing lambda still binds to it
  - `MainActivity` runs with `android:windowSoftInputMode="adjustResize"`

`WindowInsets.ime` is zero under Robolectric, so the tests pass the keyboard's height in explicitly: the hand-off's 320 dp on its 800 dp screen. Whether the real keyboard's insets reach the sheet and the toast through `ShellLayers` is for the device: the emulator walkthrough (Task 12) and Plan 4's on-device pass.

- [ ] **Step 1: Write the failing tests**

In `app/src/test/java/uk/co/siland/culvery/shell/ui/OverlayLayersTest.kt`:
1. Add these imports:
```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.ui.EditorPicker
import uk.co.siland.culvery.capability.calendar.ui.EventEditorSheet
```
2. Add at the end of the class:
```kotlin
    @Test
    fun theToastSitsAboveTheKeyboard() {
        val keyboard = 320.dp
        compose.setContent {
            CulveryTheme(dark = true) {
                ToastLayer(ToastMessage(1, "Mia can only add events for themselves.", "info"), keyboard = WindowInsets(bottom = keyboard)) {}
            }
        }
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val toast = compose.onNodeWithTag("toast").getUnclippedBoundsInRoot()
        // Hand-off §7: 28 dp above the keyboard, which puts it about 340 dp from the bottom.
        assertThat(toast.bottom.value).isWithin(0.5f).of((screen.bottom - keyboard - 28.dp).value)
    }

    @Test
    fun tappingTheScrimClosesTheAddEditSheet() {
        showLayer()
        val form = EventForm(EventForm.Mode.New, LocalDate.of(2026, 9, 23), LocalTime.of(11, 54), ZoneId.of("Europe/London"), null, null)
        compose.runOnIdle {
            overlay.show {
                EventEditorSheet(
                    form = form, people = emptyList(), childOnly = null, busy = false, failure = null,
                    picker = EditorPicker.None, onClose = overlay::dismiss, onSave = {}, onDelete = {},
                    onRefusedWho = {}, onPicker = {}, focusTitleOnOpen = false,
                )
            }
        }
        compose.onNodeWithTag("editor_sheet").assertExists()
        // The scrim's top-left corner is clear of the sheet against the right edge.
        compose.onNodeWithTag("overlay_scrim").performTouchInput { click(Offset(10f, 10f)) }
        compose.onNodeWithTag("editor_sheet").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
    }
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*OverlayLayersTest*"`
Expected: compilation FAILS: `ToastLayer` has no `keyboard` parameter. (`tappingTheScrimClosesTheAddEditSheet` pins the shell's scrim over the new sheet; once it compiles it passes, as the scrim already dismisses whatever is shown.)

- [ ] **Step 3: Raise toasts above the keyboard**

In `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt`:
1. Add these imports:
```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
```
2. In `ShellLayers`, replace `ToastLayer(toast, onToastHidden)` with `ToastLayer(toast, onHidden = onToastHidden)`.
3. Replace `ToastLayer` and its KDoc with:
```kotlin
/**
 * Draws [toast] bottom-centre, 28 dp above the bottom or above the on-screen keyboard while it shows (hand-off §7),
 * and reports it hidden after 3.5 s; a new toast restarts the timer.
 */
@Composable
fun ToastLayer(toast: ToastMessage?, keyboard: WindowInsets = WindowInsets.ime, onHidden: (Long) -> Unit) {
    val t = toast ?: return
    LaunchedEffect(t.id) {
        delay(ShellTokens.TOAST_MILLIS)
        onHidden(t.id)
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom))
            .padding(bottom = ShellTokens.toastBottom),
        contentAlignment = Alignment.BottomCenter,
    ) {
        HhToast(t.message, t.icon)
    }
}
```

- [ ] **Step 4: Let the activity see the keyboard**

In `app/src/main/AndroidManifest.xml`, in the `<activity android:name=".MainActivity" …>` element, after `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode"`, add:
```xml
            android:windowSoftInputMode="adjustResize"
```
The activity is edge-to-edge (`enableEdgeToEdge()` in `MainActivity`), so `adjustResize` doesn't shrink the window: it makes the system deliver the keyboard's insets, which `WindowInsets.ime` reads. Without it the system may pan the whole window up instead, pushing the sheet's header off screen. Set it in the manifest only; its code constant `SOFT_INPUT_ADJUST_RESIZE` is deprecated (Global Constraints). If the build or lint flags the manifest value as deprecated, stop and ask.

- [ ] **Step 5: Check the app uses the real keyboard's insets**

The tests pass the keyboard in; the app must not. Check that neither host passes its own insets, so both default to `WindowInsets.ime`:
```bash
grep -n "keyboard =" capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorHost.kt app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt
```
Expected: no output.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*OverlayLayersTest*"`
Expected: PASS.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. No screenshot changes: the keyboard's insets are zero under Robolectric.

- [ ] **Step 8: Commit**

```bash
git add app
git commit -m "Let the app see the on-screen keyboard, and keep toasts above it"
```

---

### Task 11: Entry points — Today **+**, **Add event**, and taps on a week column

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/TodayCard.kt` (rewrite)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt` (rewrite)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CardHosts.kt` (rewrite)
- Modify: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekViewTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/OpenEventTest.kt` (rewrite)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardScreenshotTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekScreenshotTest.kt` (modify)
- Modify (re-recorded): `capability/calendar/src/test/screenshots/today_{dark,light}.png`, `week_{dark,light}.png`

**Interfaces:**
- Consumes: `rememberEventAdder`, null when there is nowhere to add (Task 9); `CalendarRepository.masterLabel` behind it (Task 4).
- Produces:
  - `fun TodayCard(events: List<EventUi>?, modifier: Modifier = Modifier, onOpen: (EventRef) -> Unit = {}, onAdd: (() -> Unit)? = null)` — the **+** shows only when `onAdd` is set; test tag `today_add`
  - `fun WeekView(state: WeekViewState, modifier: Modifier = Modifier, onOpen: (EventRef) -> Unit = {}, onAdd: ((LocalDate) -> Unit)? = null)` — with `onAdd` set: **Add event** (tag `week_add_event`, adds on `state.today`), a tap on a column's space below its chips (adds on its date), and each column's hint (tag `week_add_<ISO date>`, adds on its date); the column's header and the gaps between chips add nothing
  - `TodayCardHost` and `WeekViewHost` (signatures unchanged) pass the adder `rememberEventAdder` returns, so the entry points show only when it isn't null
  - test-only `stubEditor(store, zone, clock: WallClock = WallClock { 0L })`

- [ ] **Step 1: Write the failing card and week tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt`:
1. Add these imports:
```kotlin
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
```
2. Add at the end of the class:
```kotlin
    @Test
    fun thePlusOnTodayIsA44DpCircleThatAdds() {
        var added = 0
        show { TodayCard(SampleUi.today, onAdd = { added++ }) }
        compose.onNodeWithTag("today_add").assertHeightIsEqualTo(44.dp).assertWidthIsEqualTo(44.dp).performClick()
        assertThat(added).isEqualTo(1)
        compose.onNodeWithText("Week").assertExists()
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekViewTest.kt`:
1. Add these imports:
```kotlin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import java.time.LocalDate
```
2. Add at the end of the class:
```kotlin
    @Test
    fun addEventInTheHeaderAddsOnToday() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        compose.onNodeWithTag("week_add_event").assertHeightIsEqualTo(48.dp).performClick()
        assertThat(added).containsExactly(SampleUi.TODAY)
    }

    @Test
    fun aTapOnAColumnsEmptySpaceAddsOnThatDay() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        val friday = SampleUi.TODAY.plusDays(2)
        compose.onNodeWithTag("week_add_$friday", useUnmergedTree = true).performClick()
        assertThat(added).containsExactly(friday)
    }

    @Test
    fun aChipTapStillOpensItsEventAndAddsNothing() {
        val added = mutableListOf<LocalDate>()
        val opened = mutableListOf<EventRef>()
        show { WeekView(state(), onOpen = { opened += it }, onAdd = { added += it }) }
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        assertThat(opened).hasSize(1)
        assertThat(added).isEmpty()
    }

    @Test
    fun eachColumnEndsWithAnAddHintAtLeast40DpTall() {
        show { WeekView(state(), onAdd = {}) }
        (0L..6L).forEach {
            compose.onNodeWithTag("week_add_${SampleUi.TODAY.plusDays(it)}", useUnmergedTree = true).assertHeightIsAtLeast(40.dp)
        }
    }

    @Test
    fun aTapOnAColumnsHeaderAddsNothing() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        // A missed tap near the day's name is not a request for a new event: only the space below the chips adds.
        compose.onNodeWithTag("week_day_${SampleUi.TODAY}").performTouchInput { click(Offset(centerX, 10f)) }
        assertThat(added).isEmpty()
    }
```

- [ ] **Step 2: Write the failing host tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt`, replace `stubEditor` and its KDoc with:
```kotlin
/**
 * An editor that can't change anything, for tests that only need the hosts to compose and open sheets. [clock] is
 * "now" for an add sheet the test opens.
 */
internal fun stubEditor(store: CalendarStore, zone: HouseholdZone, clock: WallClock = WallClock { 0L }): CalendarEditor = CalendarEditor(
    store, emptySet(), NobodyMay, RecordingToaster(), zone, clock,
    CoroutineScope(Dispatchers.Unconfined), {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,
)
```

Replace `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/OpenEventTest.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.ScriptedWriter
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.stubEditor
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class OpenEventTest {
    @get:Rule val compose = createComposeRule()

    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val overlay = RecordingOverlay()
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository
    private lateinit var zone: HouseholdZone
    private lateinit var repo: CalendarRepository
    private lateinit var editor: CalendarEditor

    @Before
    fun setUp() = runBlocking {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        zone = HouseholdZone(household)
        store.addConnection(
            Connection("c1", "calendar.test", "Google", emptyMap()),
            listOf(CalendarSource("s-family", "Family calendar", writable = true)),
            emptyMap(),
        )
        val start = today.atTime(19, 30).atZone(london).toInstant()
        store.applySync(
            "c1", "s-family", DateRange(today.minusDays(1), today.plusDays(14), london),
            SyncResult(
                listOf(RemoteEvent("dinner", "Dinner with Jo & Priya", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), recurring = false)),
                emptyList(), null, fullReplace = true,
            ),
        )
        repo = CalendarRepository(store, household, zone, emptySet(), emptySet())
        editor = stubEditor(store, zone)
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    /** The Family calendar as a writable master whose provider has a writer, so the add entry points show. */
    private fun makeFamilyTheWritableMaster() {
        runBlocking { store.setMaster("c1", "s-family") }
        repo = CalendarRepository(store, household, zone, emptySet(), setOf(ScriptedWriter("calendar.test")))
        editor = stubEditor(store, zone, WallClock { SampleUi.NOW })
    }

    private fun show(host: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(
            LocalShellNavigator provides RecordingNavigator(),
            LocalOverlayHost provides overlay,
        ) {
            CulveryTheme(dark = true) {
                Box {
                    host()
                    overlay.content?.invoke()
                }
            }
        }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTag(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun tappingATodayRowOpensItsDetailSheetAndCloseDismissesIt() {
        show { TodayCardHost(repo, editor, today) }
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        waitForText("Created by")
        compose.onNodeWithText("Today · 19:30–21:00").assertExists()
        compose.onNodeWithTag("sheet_close").performClick()
        compose.onNodeWithText("Created by").assertDoesNotExist()
        assertThat(overlay.dismissed).isEqualTo(1)
    }

    @Test
    fun tappingAWeekChipOpensItsDetailSheet() {
        show { WeekViewHost(repo, editor, today, nowMillis = 0L) }
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        waitForText("Created by")
        compose.onNodeWithTag("detail_sheet").assertExists()
    }

    /** Both hosts at once, for the tests where neither may offer to add. */
    private fun showBothHosts() = show {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { TodayCardHost(repo, editor, today) }
            Box(Modifier.weight(1f)) { WeekViewHost(repo, editor, today, nowMillis = 0L) }
        }
    }

    private fun assertNowhereToAdd() {
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("today_add").assertDoesNotExist()
        compose.onNodeWithTag("week_add_event").assertDoesNotExist()
        compose.onNodeWithTag("week_add_$today", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun withNoMasterThereIsNowhereToAdd() {
        repo = CalendarRepository(store, household, zone, emptySet(), setOf(ScriptedWriter("calendar.test")))
        showBothHosts()
        assertNowhereToAdd()
    }

    @Test
    fun withAMasterWhoseProviderHasNoWriterThereIsNowhereToAdd() {
        runBlocking { store.setMaster("c1", "s-family") }
        showBothHosts()
        assertNowhereToAdd()
    }

    @Test
    fun thePlusOnTodayOpensANewEventForToday() {
        makeFamilyTheWritableMaster()
        show { TodayCardHost(repo, editor, today) }
        waitForTag("today_add")
        compose.onNodeWithTag("today_add").performClick()
        waitForText("New event")
        compose.onNodeWithTag("editor_summary").assertTextEquals("Today · 14:00–15:00 · Family")
    }

    @Test
    fun addEventInTheWeekOpensANewEventForToday() {
        makeFamilyTheWritableMaster()
        show { WeekViewHost(repo, editor, today, nowMillis = SampleUi.NOW) }
        waitForTag("week_add_event")
        compose.onNodeWithTag("week_add_event").performClick()
        waitForText("New event")
        compose.onNodeWithTag("editor_summary").assertTextEquals("Today · 14:00–15:00 · Family")
    }

    @Test
    fun aColumnTapPresetsItsDay() {
        makeFamilyTheWritableMaster()
        show { WeekViewHost(repo, editor, today, nowMillis = SampleUi.NOW) }
        val friday = today.plusDays(2)
        waitForTag("week_add_$friday")
        compose.onNodeWithTag("week_add_$friday", useUnmergedTree = true).performClick()
        waitForText("New event")
        // A later day starts on Morning.
        compose.onNodeWithTag("editor_summary").assertTextEquals("Fri 25 Sep · 09:00–10:00 · Family")
    }

    @Test
    fun aChipTapStillOpensTheEventNotTheEditor() {
        makeFamilyTheWritableMaster()
        show { WeekViewHost(repo, editor, today, nowMillis = SampleUi.NOW) }
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        waitForText("Created by")
        compose.onNodeWithText("New event").assertDoesNotExist()
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CardsTest*" --tests "*WeekViewTest*" --tests "*OpenEventTest*"`
Expected: compilation FAILS: `TodayCard` and `WeekView` have no `onAdd` parameter.

- [ ] **Step 4: Add the entry points' styles and dimensions**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`:
1. Add at the end of `CalendarType`, before its closing brace:
```kotlin

    /** 15 sp / 700: "Add event" in the Calendar header. */
    val addEventButton = HhType.buttonLabel
```
2. In `CalendarDimens`, before `/** Chip tint: …`, add:
```kotlin
    // Entry points (hand-off §7): Today's + is a 44 dp `accent` circle (touchTarget) with a 26 dp `add`, 8 from Week.
    // Add event is 48 dp, radius 24, padding 0 20 0 14, a 24 dp `add` 6 from its label. Each week column ends with a
    // 24 dp `add` hint at 50% in at least 40 dp.
    val todayAddIcon = 26.dp
    val todayHeaderButtonGap = 8.dp
    val addEventHeight = 48.dp
    val addEventRadius = 24.dp
    val addEventPaddingStart = 14.dp
    val addEventPaddingEnd = 20.dp
    val addEventIcon = 24.dp
    val addEventIconGap = 6.dp
    val addHintIcon = 24.dp
    val addHintMinHeight = 40.dp

    /** Hand-off §7: the week column's add hint at 50%. */
    const val ADD_HINT_ALPHA = 0.5f

```

- [ ] **Step 5: Add the Today card's +**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/TodayCard.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType

/**
 * Hand-off Home "Today" card. [events] null while loading: shows nothing rather than a false "Nothing on today".
 * Each row is a button (hand-off §7) that opens its event through [onOpen]. The **+** left of Week adds an event
 * today through [onAdd]; it is absent when there is nowhere to add to (no writable master calendar).
 */
@Composable
fun TodayCard(events: List<EventUi>?, modifier: Modifier = Modifier, onOpen: (EventRef) -> Unit = {}, onAdd: (() -> Unit)? = null) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(modifier = modifier.fillMaxSize().testTag("calendar_today"), radius = CalendarDimens.cardRadius) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.todayHeaderButtonGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Today", style = HhType.cardTitle, color = c.ink, modifier = Modifier.weight(1f))
            if (onAdd != null) AddCircle(onAdd)
            HeaderChip("Week", onClick = { navigator.openTab(CALENDAR_TAB_ID) })
        }
        Spacer(Modifier.height(CalendarDimens.todayHeaderGap))
        when {
            events == null -> {}
            events.isEmpty() -> Text("Nothing on today", style = HhType.body, color = c.mute)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CalendarDimens.todayRowGap),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(events, key = { it.ref.listKey }) { TodayRow(it, onOpen) }
            }
        }
    }
}

/** Hand-off §7: 44 dp, `accent`, a 26 dp `add` in `accentInk`. */
@Composable
private fun AddCircle(onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("today_add")
            .size(CalendarDimens.touchTarget)
            .clip(CircleShape)
            .background(c.accent)
            .clickable(onClickLabel = "Add event", onClick = onClick),
    ) {
        HhIcon("add", size = CalendarDimens.todayAddIcon, tint = c.accentInk, contentDescription = "Add event")
    }
}

@Composable
private fun TodayRow(event: EventUi, onOpen: (EventRef) -> Unit) {
    val c = Culvery.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(CalendarDimens.todayRowRadius))
            .background(c.surf2)
            .clickable(onClickLabel = "Open") { onOpen(event.ref) }
            .padding(horizontal = CalendarDimens.todayRowPaddingH, vertical = CalendarDimens.todayRowPaddingV),
    ) {
        ColourBar(Color(event.person.color), width = CalendarDimens.todayBarWidth)
        Spacer(Modifier.width(CalendarDimens.todayBarGap))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = HhType.rowTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(CalendarDimens.todayTimeTop))
            Text(
                "${event.timeLabel} · ${event.person.name}",
                style = HhType.secondary,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EventBadges(event, CalendarDimens.todayBadge, Modifier.align(Alignment.CenterVertically))
    }
}
```

- [ ] **Step 6: Add Add event and the column taps to the week**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.capability.calendar.isStaleAt
import uk.co.siland.culvery.capability.calendar.weekSubtitle
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType

private val SHORT_WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.UK)

data class WeekViewState(val week: WeekUi, val today: LocalDate, val sync: SyncStatusUi, val nowMillis: Long)

internal fun reconnectLabel(labels: List<String>): String =
    if (labels.size == 1) "${labels.single()} needs reconnecting" else "${labels.size} calendars need reconnecting"

/**
 * Hand-off §2 and §7: a rolling seven days from today, person-coloured chips and the sync state. There is no week
 * navigation, so the view never leaves the synced window. With [onAdd] (there is a writable master calendar), Add event
 * sits right of the legend and adds on today, and a tap on the space below a column's chips, or on its hint, adds on
 * that column's day; chip taps still open their event. Without it, the legend keeps a 24 dp gap to the right edge.
 */
@Composable
fun WeekView(
    state: WeekViewState,
    modifier: Modifier = Modifier,
    onOpen: (EventRef) -> Unit = {},
    onAdd: ((LocalDate) -> Unit)? = null,
) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.weekHeaderGap), modifier = modifier.fillMaxSize()) {
        Column {
            // The title+subtitle column and the legend share one bottom-aligned row, so the legend stays level
            // with the subtitle whether or not the reconnect chip below adds height to the title column.
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(CalendarDimens.headerTrailingGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("This week", style = CalendarType.weekTitle, color = c.ink)
                    Spacer(Modifier.height(CalendarDimens.subtitleTop))
                    Text(
                        weekSubtitle(state.sync, state.nowMillis),
                        style = CalendarType.subtitle,
                        color = if (state.sync.isStaleAt(state.nowMillis)) c.danger else c.mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("week_subtitle"),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CalendarDimens.headerTrailingGap),
                ) {
                    Legend(
                        state.week.people,
                        Modifier
                            .testTag("week_legend")
                            .then(if (onAdd == null) Modifier.padding(end = CalendarDimens.headerTrailingGap) else Modifier),
                    )
                    if (onAdd != null) AddEventButton { onAdd(state.today) }
                }
            }
            if (state.sync.needsSignIn.isNotEmpty()) {
                Spacer(Modifier.height(CalendarDimens.reconnectTop))
                ReconnectChip(reconnectLabel(state.sync.needsSignIn), onClick = navigator::openSettings)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.weekColumnGap), modifier = Modifier.fillMaxWidth().weight(1f)) {
            state.week.days.forEach { day ->
                DayColumn(
                    day,
                    isToday = day.date == state.today,
                    onOpen = onOpen,
                    onAdd = onAdd?.let { add -> { add(day.date) } },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(day: DayUi, isToday: Boolean, onOpen: (EventRef) -> Unit, onAdd: (() -> Unit)?, modifier: Modifier) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(CalendarDimens.weekColumnRadius)
    val inset = CalendarDimens.columnHeaderInset
    val chips = rememberLazyListState()
    HhCard(
        modifier = modifier
            .testTag("week_day_${day.date}")
            .then(if (isToday) Modifier.border(CalendarDimens.todayRingWidth, c.accent, shape) else Modifier),
        radius = CalendarDimens.weekColumnRadius,
        padding = PaddingValues(horizontal = CalendarDimens.weekColumnPaddingH, vertical = CalendarDimens.weekColumnPaddingV),
    ) {
        Row(Modifier.padding(start = inset, end = inset, bottom = inset)) {
            Text(
                if (isToday) "Today" else day.date.format(SHORT_WEEKDAY),
                style = CalendarType.strong14,
                color = if (isToday) c.accent else c.mute,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(CalendarDimens.weekDayDateGap))
            Text(day.date.dayOfMonth.toString(), style = HhType.dateNumber, color = c.ink, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(CalendarDimens.columnGap))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(
                    if (onAdd == null) {
                        Modifier
                    } else {
                        // The chips take their own taps. Only the space below the last one adds: a tap in the header or
                        // between two chips is a missed chip, not a new event (2b-2 design §4.1).
                        Modifier.pointerInput(onAdd) {
                            detectTapGestures { tap ->
                                val last = chips.layoutInfo.visibleItemsInfo.lastOrNull()
                                if (last == null || tap.y > last.offset + last.size) onAdd()
                            }
                        }
                    },
                ),
        ) {
            LazyColumn(
                state = chips,
                verticalArrangement = Arrangement.spacedBy(CalendarDimens.columnGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(day.events, key = { it.ref.listKey }) { EventChip(it, onOpen) }
            }
        }
        if (onAdd != null) AddHint(day.date, onAdd)
    }
}

/** Hand-off §7: a faint `add` at the foot of each column, in a tap area at least 40 dp tall. */
@Composable
private fun AddHint(date: LocalDate, onAdd: () -> Unit) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .testTag("week_add_$date")
            .fillMaxWidth()
            .heightIn(min = CalendarDimens.addHintMinHeight)
            .clickable(onClickLabel = "Add event", onClick = onAdd),
    ) {
        HhIcon("add", size = CalendarDimens.addHintIcon, tint = Culvery.colors.mute.copy(alpha = CalendarDimens.ADD_HINT_ALPHA))
    }
}

/** Hand-off §7: 48 dp, radius 24, `accent`, a 24 dp `add` and 15 sp / 700 in `accentInk`. */
@Composable
private fun AddEventButton(onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.addEventIconGap),
        modifier = Modifier
            .testTag("week_add_event")
            .height(CalendarDimens.addEventHeight)
            .clip(RoundedCornerShape(CalendarDimens.addEventRadius))
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(start = CalendarDimens.addEventPaddingStart, end = CalendarDimens.addEventPaddingEnd),
    ) {
        HhIcon("add", size = CalendarDimens.addEventIcon, tint = c.accentInk)
        Text("Add event", style = CalendarType.addEventButton, color = c.accentInk, maxLines = 1)
    }
}

@Composable
private fun EventChip(event: EventUi, onOpen: (EventRef) -> Unit) {
    val c = Culvery.colors
    val colour = Color(event.person.color)
    val tint = if (c.bg.luminance() < 0.5f) CalendarDimens.CHIP_ALPHA_DARK else CalendarDimens.CHIP_ALPHA_LIGHT
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.chipRadius))
            .background(colour.copy(alpha = tint))
            .clickable(onClickLabel = "Open") { onOpen(event.ref) }
            .padding(horizontal = CalendarDimens.chipPaddingH, vertical = CalendarDimens.chipPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(event.startLabel, style = CalendarType.chipTime, color = colour, maxLines = 1, modifier = Modifier.weight(1f))
            EventBadges(event, CalendarDimens.chipBadge)
        }
        Spacer(Modifier.height(CalendarDimens.chipTitleTop))
        Text(event.title, style = CalendarType.chipTitle, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Legend(people: List<Person>, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.legendGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        people.forEach { person ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(CalendarDimens.legendDotGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(CalendarDimens.legendDot).clip(CircleShape).background(Color(person.color)))
                Text(
                    person.name,
                    style = CalendarType.legend,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = CalendarDimens.legendNameMax),
                )
            }
        }
    }
}

/** One chip for every connection that needs signing in again: hand-off `dangerSoft` pill, 44 dp, radius 22. */
@Composable
private fun ReconnectChip(label: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reconnectIconGap),
        modifier = Modifier
            .height(CalendarDimens.touchTarget)
            .clip(RoundedCornerShape(CalendarDimens.pillRadius))
            .background(c.dangerSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.pillPaddingH),
    ) {
        HhIcon("sync_problem", size = CalendarDimens.reconnectIcon, tint = c.danger)
        Text(
            label,
            style = CalendarType.pill,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
```

- [ ] **Step 7: Show the entry points only where there is somewhere to add**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CardHosts.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WeekUi

/** Today's rows open their event; its + adds one today, when there is a writable master calendar to add to. */
@Composable
internal fun TodayCardHost(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate) {
    val open = rememberEventOpener(repo, editor, today)
    val add = rememberEventAdder(repo, editor, today)
    val events: List<EventUi>? by remember(today) { repo.day(today) }.collectAsState(initial = null)
    TodayCard(events, onOpen = open, onAdd = add?.let { a -> { a(null) } })
}

@Composable
internal fun ComingUpCardHost(repo: CalendarRepository, today: LocalDate) {
    val days: List<DayUi>? by remember(today) { repo.days(today.plusDays(1), 3) }.collectAsState(initial = null)
    ComingUpCard(days)
}

/**
 * Today plus six days; [today] moves at midnight, so the week rolls with it. Shows nothing until both flows load.
 * Add event and the column taps show only when there is a writable master calendar to add to.
 */
@Composable
internal fun WeekViewHost(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate, nowMillis: Long) {
    val open = rememberEventOpener(repo, editor, today)
    val add = rememberEventAdder(repo, editor, today)
    val week: WeekUi? by remember(today) { repo.week(today) }.collectAsState(initial = null)
    val sync: SyncStatusUi? by repo.syncStatus.collectAsState(initial = null)
    val w = week ?: return
    val s = sync ?: return
    WeekView(WeekViewState(w, today, s, nowMillis), onOpen = open, onAdd = add)
}
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CardsTest*" --tests "*WeekViewTest*" --tests "*OpenEventTest*" --tests "*CardHostsMidnightRolloverTest*"`
Expected: PASS.

- [ ] **Step 9: Show the entry points in the screenshots and re-record**

Only the four base images show the entry points; the variants keep testing what they tested.
1. In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardScreenshotTest.kt`, in `todayDark` and `todayLight` only, replace `TodayCard(SampleUi.today)` with `TodayCard(SampleUi.today, onAdd = {})`.
2. In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekScreenshotTest.kt`:
   - add `import java.time.LocalDate`;
   - after `private val now = SampleUi.NOW`, add:
```kotlin
    private val addNothing: (LocalDate) -> Unit = {}
```
   - replace `private fun snap(name: String, dark: Boolean, sync: SyncStatusUi, week: WeekUi = SampleUi.week) {` with `private fun snap(name: String, dark: Boolean, sync: SyncStatusUi, week: WeekUi = SampleUi.week, canAdd: Boolean = false) {`;
   - replace `WeekView(WeekViewState(week, SampleUi.TODAY, sync, now))` with `WeekView(WeekViewState(week, SampleUi.TODAY, sync, now), onAdd = addNothing.takeIf { canAdd })`;
   - replace `@Test fun weekDark() = snap("week_dark", true, fresh)` with `@Test fun weekDark() = snap("week_dark", true, fresh, canAdd = true)` and `@Test fun weekLight() = snap("week_light", false, fresh)` with `@Test fun weekLight() = snap("week_light", false, fresh, canAdd = true)`.

Run:
```bash
./gradlew :capability:calendar:recordRoborazziDebug --tests "*CardScreenshotTest*" --tests "*WeekScreenshotTest*"
```
Expected changes, compared with `docs/design/house_hub_handoff/screenshots/calendar-sheets/01-home-*` and `02-calendar-week-*`:
- `today_dark`, `today_light`: a green 44 dp **+** left of Week, 8 dp from it.
- `week_dark`, `week_light`: **Add event** (48 dp, green, `add` + label) at the right end of the header row, 24 dp right of the legend and vertically centred on it; a faint `add` at the foot of each column.
- Nothing else. `today_empty_dark`, `today_badges_dark`, the other `week_*` images, `coming_up_*`, `connect_*` and every `:app` image are unchanged.

Look at every changed image.

- [ ] **Step 10: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add capability/calendar
git commit -m "Add events from Today's +, the week's Add event, and a tap on a week column"
```

---

### Task 12: Offline on the emulator, the end-to-end offline test, the walkthrough, the user checkpoint, and the README

**Files:**
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`
- Test: `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt` (modify)
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `app/src/debug/java/uk/co/siland/culvery/DebugOfflineReceiver.kt`
- Test: `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt` (create)
- Modify (after the checkpoint): `README.md`
- Modify (after the checkpoint): `docs/superpowers/plans/2026-09-23-plan1-followups.md`

**Interfaces:**
- Consumes: `CalendarEditor`'s `@Inject` constructor, `create`, `update`, `delete`, `EVENT_ADDED`, `CHANGES_SAVED`, `EVENT_DELETED` (Task 5); `CalendarSync`'s and `CalendarSyncLoop`'s `@Inject` constructors (2b-1); `CalendarRepository.day`, `event` (Task 4); `seedDebugData` (2b-1); `OUTBOX_BACKOFF_MS` (2b-1).
- Produces:
  - `FakeCalendarProvider.setOffline(value: Boolean)` — while offline, every `sources`, `sync`, `create`, `update` and `delete` throws `UnreachableException`
  - `DebugOfflineReceiver` (debug builds only), reached with `adb shell am broadcast -n uk.co.siland.culvery/.DebugOfflineReceiver --ez offline true|false`; a broadcast without the extra changes nothing

- [ ] **Step 1: Write the failing tests**

In `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt`, add at the end of the class:
```kotlin
    @Test
    fun offlineFailsEveryReadAndWriteUntilItIsBackOnline() = runTest {
        val fake = providerOn(today)
        fake.setOffline(true)
        assertThat(runCatching { fake.sources(conn) }.exceptionOrNull()).isInstanceOf(UnreachableException::class.java)
        assertThat(runCatching { fake.familyEvents() }.exceptionOrNull()).isInstanceOf(UnreachableException::class.java)
        // Every write, not only the next one.
        repeat(2) {
            assertThat(runCatching { fake.create(conn, family, draft("Sleepover"), newClientKey()) }.exceptionOrNull())
                .isInstanceOf(UnreachableException::class.java)
        }
        fake.setOffline(false)
        assertThat(fake.familyEvents().map { it.title }).doesNotContain("Sleepover")
        fake.create(conn, family, draft("Sleepover"), newClientKey())
        assertThat(fake.familyEvents().map { it.title }).contains("Sleepover")
    }
```

Create `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`:
```kotlin
package uk.co.siland.culvery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CHANGES_SAVED
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarPermissionSource
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.CalendarSync
import uk.co.siland.culvery.capability.calendar.CalendarSyncLoop
import uk.co.siland.culvery.capability.calendar.EVENT_ADDED
import uk.co.siland.culvery.capability.calendar.EVENT_DELETED
import uk.co.siland.culvery.capability.calendar.EditResult
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.OUTBOX_BACKOFF_MS
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.DefaultAccessControl
import uk.co.siland.culvery.core.access.LockoutStore
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

/**
 * 2b-2 design §5 on the sample calendar, end to end: an event added while the calendar can't be reached shows at
 * once, can be changed and deleted before it syncs, and once the calendar is back nothing of it remains. The real
 * store, access rules, editor, sync engine, repository and fake provider; Alex answers every PIN pad.
 */
@RunWith(AndroidJUnit4::class)
class SampleAddTest {
    private class Toasts : Toaster {
        val messages = mutableListOf<String>()

        override fun show(message: String, icon: String) {
            messages += message
        }
    }

    private lateinit var householdDb: HouseholdDatabase
    private lateinit var calendarDb: CalendarDatabase
    private val fake = FakeCalendarProvider()
    private val toasts = Toasts()
    private val london = ZoneId.of("Europe/London")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        calendarDb = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        householdDb.close()
        calendarDb.close()
    }

    @Test
    fun anEventAddedOfflineCanBeChangedAndDeletedBeforeItSyncsAndNothingRemains() = runTest {
        val household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        val pins = PinManager(household, PinHasher())
        val store = CalendarStore(calendarDb)
        val zone = HouseholdZone(household)
        seedDebugData(household, pins, CalendarSetup(store, setOf(fake)), setOf(fake))
        var now = System.currentTimeMillis()
        val clock = WallClock { now }
        val prompt = PinPromptController()
        backgroundScope.launch { prompt.request.filterNotNull().collect { prompt.submit("1234") } }
        val access = DefaultAccessControl(
            PermissionRegistry(setOf(CorePermissionSource(), CalendarPermissionSource())),
            pins,
            LockoutStore(ApplicationProvider.getApplicationContext()),
            prompt,
            clock,
            toasts,
            backgroundScope,
        )
        val sync = CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, clock)
        val editor = CalendarEditor(store, setOf(fake), access, toasts, zone, clock, backgroundScope, CalendarSyncLoop(sync, store, clock, backgroundScope))
        val repo = CalendarRepository(store, household, zone, setOf(fake), setOf(fake))
        sync.syncAll()

        val today = LocalDate.now(london)
        val start = today.atTime(20, 0).atZone(london).toInstant()
        fun draft(title: String) = EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), PersonId.FAMILY.value, null)
        val titles = listOf("Parents evening", "Parents' evening at school")

        fake.setOffline(true)
        assertThat(editor.create(draft("Parents evening"))).isEqualTo(EditResult.Queued)
        val added = repo.day(today).first().single { it.title == "Parents evening" }
        assertThat(added.syncing).isTrue()

        assertThat(editor.update(added.ref, draft("Parents' evening at school"))).isEqualTo(EditResult.Queued)
        assertThat(repo.event(added.ref, today).first()?.event?.title).isEqualTo("Parents' evening at school")

        assertThat(editor.delete(added.ref)).isEqualTo(EditResult.Queued)
        assertThat(repo.day(today).first().map { it.title }).containsNoneIn(titles)

        fake.setOffline(false)
        now += OUTBOX_BACKOFF_MS.first()
        sync.syncAll()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(repo.day(today).first().map { it.title }).containsNoneIn(titles)
        assertThat(toasts.messages).containsExactly(EVENT_ADDED, CHANGES_SAVED, EVENT_DELETED).inOrder()
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :provider:calendar-fake:testDebugUnitTest :app:testDebugUnitTest --tests "*SampleAddTest*"`
Expected: compilation FAILS: `setOffline` is unresolved.

- [ ] **Step 3: Let the fake go offline**

In `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`:
1. After `@Volatile private var failNext: Throwable? = null`, add:
```kotlin
    @Volatile private var offline = false
```
2. After `fun failNextWith(error: Throwable) { … }`, add:
```kotlin

    /** While offline, every read and write throws UnreachableException (the walkthrough's DebugOfflineReceiver). */
    fun setOffline(value: Boolean) {
        offline = value
    }
```
3. In `write`, make the first line inside `synchronized(lock) {`:
```kotlin
        if (offline) throw UnreachableException(OFFLINE_MESSAGE)
```
4. Replace `throwIfFailing` with:
```kotlin
    private fun throwIfFailing() {
        if (offline) throw UnreachableException(OFFLINE_MESSAGE)
        failNext?.let {
            failNext = null
            throw it
        }
    }
```

- [ ] **Step 4: Add the debug-only offline switch**

Create `app/src/debug/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <!-- Debug walkthrough only: adb takes the sample calendar offline and back. The sender needs DUMP, which the
             adb shell holds and apps can't. -->
        <receiver
            android:name=".DebugOfflineReceiver"
            android:exported="true"
            android:permission="android.permission.DUMP" />
    </application>
</manifest>
```

Create `app/src/debug/java/uk/co/siland/culvery/DebugOfflineReceiver.kt`:
```kotlin
package uk.co.siland.culvery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

/**
 * Debug builds only: takes the sample calendar offline, or brings it back, so the walkthrough can add, change and
 * delete an event the calendar can't be reached for (2b-2 design §7):
 * `adb shell am broadcast -n uk.co.siland.culvery/.DebugOfflineReceiver --ez offline true`
 */
class DebugOfflineReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun fake(): FakeCalendarProvider

        fun calendarSetup(): CalendarSetup
    }

    override fun onReceive(context: Context, intent: Intent) {
        // A mistyped command must not take the calendar offline by default.
        if (!intent.hasExtra(EXTRA_OFFLINE)) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        deps.fake().setOffline(intent.getBooleanExtra(EXTRA_OFFLINE, false))
        // A pass now shows the new state; queued changes still wait out their backoff.
        deps.calendarSetup().syncSoon()
    }

    companion object {
        const val EXTRA_OFFLINE = "offline"
    }
}
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :provider:calendar-fake:testDebugUnitTest :app:testDebugUnitTest --tests "*SampleAddTest*"`
Expected: PASS.

- [ ] **Step 6: Build both variants and run the gate**

Run: `./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.
- The release build has no receiver and no fake: `app/src/debug` isn't in it.
- If Hilt reports that it can't provide `FakeCalendarProvider` to the entry point, the message names the missing binding: the fake is a `@Singleton` with an `@Inject` constructor, so it should resolve without a new module. Stop and report rather than adding a binding.
- If the build warns that `EntryPointAccessors.fromApplication` is deprecated, stop and ask (Global Constraints).

- [ ] **Step 7: Commit**

```bash
git add provider/calendar-fake app
git commit -m "Let the walkthrough take the sample calendar offline, and test adding, changing and deleting offline end to end"
```

- [ ] **Step 8: Walk through it on the emulator**

The 2b-2 design §7 asks for this walkthrough on `Culvery_Tablet_API_30` with the Google Play image if it is in place, otherwise API 35. Check which image it uses:
```bash
grep image.sysdir "$USERPROFILE/.android/avd/Culvery_Tablet_API_30.avd/config.ini"
```
- If it reads `google_apis_playstore`, use `Culvery_Tablet_API_30`.
- Otherwise use `Culvery_Tablet_API_35`, and say so in your report. Swapping the image is a user step in Android Studio's SDK Manager. Do not change the AVD yourself.

Debug builds never call `startLockTask` (`Kiosk.kt` skips it), so on the emulator "kiosk" means the debug build's own window: edge-to-edge with the system bars hidden. The keyboard in lock-task mode, and the Samsung keyboard, can only be checked with a signed release on the SM-T510; Step 12 adds them to the Plan 4 on-device pass.

1. Start the emulator **in the background** (in Git Bash the trailing `&` does it; with an agent's shell tool, also use its run-in-background option), then wait for it to boot:
   ```bash
   "$LOCALAPPDATA/Android/Sdk/emulator/emulator" -avd <AVD> -no-snapshot-save > /dev/null 2>&1 &
   adb wait-for-device && adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
   ```
   If `adb` is not on PATH, use `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb"`.
2. **The v2 → v3 migration on a real install.** Check whether an earlier Culvery is installed:
   ```bash
   adb shell pm list packages uk.co.siland.culvery
   ```
   - If it is, install over it **without** clearing data:
     ```bash
     ./gradlew :app:installDebug
     adb shell am start -n uk.co.siland.culvery/.MainActivity
     adb logcat -d | grep -iE "Migration|IllegalStateException|FATAL" | tail -20
     ```
     Expected: Home opens straight onto the cached events, and the excerpt shows no migration error or crash.
   - If not, say "migration not exercised on device; covered by `CalendarMigrationTest`" and run the two commands above without the `logcat` line.

Check each item below and report any that fail. Take `adb exec-out screencap -p > "$TMP/culvery-<step>.png"` at 3, 4, 5, 7 and 8.

3. **The entry points.** On Home, the Today card has a green **+** left of **Week**. Open the Calendar tab: **Add event** sits right of the legend, and each column ends with a faint **+**.
4. **Add, with the PIN.** Tap **+** on the Today card.
   1. A 600 dp sheet reads "New event", with a summary such as "Today · 18:00–19:00 · Family" (the next slot still to come). The Title field is focused and the keyboard is up.
   2. With the keyboard up: the header, the Title and **Save event** are all visible above it, and WHO and the first row of DAY show; the chips scroll.
   3. Type "Parents evening". Tap the keyboard's **Done**: the keyboard closes and nothing is saved.
   4. Tap **Tomorrow**, then **Evening**. The summary reads "Tomorrow · 18:00–19:00 · Family".
   5. Tap **Save event**. The PIN pad appears over the sheet: "Enter your PIN to save this event. It also records who made the change." Enter `1234` (Alex). The sheet closes, the toast reads "Event added", and the event shows tomorrow in Family's colour.
5. **Edit, then delete from the editor.** In the Calendar tab tap *Parents evening*.
   1. The detail footer has **Delete** and a green **Edit**. Tap **Edit**: no PIN; "Edit event", nothing focused, no keyboard; Evening, Tomorrow and **1 h** are selected.
   2. Tap **2 h**, then **Save changes**. No PIN (Alex is signed in); the toast reads "Changes saved"; the chip reads 18:00 and the event now ends at 20:00.
   3. Open it again, tap **Edit**, then **Delete** (bottom left). The detail sheet replaces the editor with "Delete this event?". Tap **Delete event**: the toast reads "Event deleted" and the event is gone.
6. **A pick outside the week, and a custom time.** Tap **Add event**. Tap **Pick date…**: the date picker covers the sheet only, with the 5-week page and ‹ ›. Tap **›**, then a day: the chip reads e.g. "Mon 2 Nov". Tap **Pick time…**, step to 16:15, **Set time**: the chip reads "16:15". Close with ✕: nothing is saved and no prompt appears.
7. **A child.** Tap **Sign out** in the status bar.
   1. Tap **Add event**, type "Pizza night", leave Who on **Family**, tap **Save event** and enter `1357` (Mia). The toast reads "Mia can only add events for themselves." The sheet stays open with "Pizza night" in it, and the status bar shows nobody signed in.
   2. Tap **Mia**, then **Save event**, and enter `1357`: "Event added".
   3. Tap **Add event** again: Who starts on **Mia**, and the other chips are dimmed. Tap **Sam**: the toast "Mia can only add events for themselves." shows. Close the sheet.
   4. Open Mia's *Pizza night*, tap **Edit**: the other Who chips are dimmed here too, and a tap on **Sam** shows the same toast (design §6).
8. **The keyboard and the toasts.** Still signed in as Mia, tap **Add event**. With the keyboard up (the title is focused), tap **Sam**: the toast shows **above** the keyboard, and **Save event** is still above the keyboard. Tap a Day chip: the keyboard stays up. Tap **Pick time…**: the keyboard closes. Cancel, close the sheet, and sign out.
9. **Offline: add, edit and delete before it syncs.**
   1. Take the sample calendar offline:
      ```bash
      adb shell am broadcast -n uk.co.siland.culvery/.DebugOfflineReceiver --ez offline true
      ```
   2. Tap **Add event**, type "Offline test", **Save event**, enter `1234`. The sheet closes at once, "Event added" shows, and the chip has the `cloud_upload` badge.
   3. Tap it: the detail sheet shows "Syncing to Sample calendar…". Tap **Edit**, change the title to "Offline test 2", **Save changes**: "Changes saved"; the chip reads "Offline test 2", still syncing.
   4. Tap it, **Delete**, **Delete event**: "Event deleted"; the chip is gone.
   5. Bring the calendar back:
      ```bash
      adb shell am broadcast -n uk.co.siland.culvery/.DebugOfflineReceiver --ez offline false
      ```
      Queued changes retry on their backoff (30 s, 1 min, 2 min, then every 5 min), so wait up to 5 minutes. The event never reappears, no failure toast shows, and afterwards:
      ```bash
      adb logcat -d | grep -iE "CalendarSync|CalendarEditor|FATAL" | tail -20
      ```
      shows no drop and no crash.
10. **DM Sans on this API level.** The sheet titles ("New event", "Pick a date") are clearly heavier than the chips; a long title wraps or ends with an ellipsis in the summary rather than clipping. Say which API level you checked. If it isn't API 30 with the Google Play image, list this as still to do on that image.

If the emulator can't start, say so and report Step 6 as the gate.

- [ ] **Step 9: STOP — the controller runs the USER CHECKPOINT**

The implementer stops here and reports Steps 6 and 8. **The controller**, not the implementer, then does the following, and does not start Step 10 until the user replies.

Send the user these images:
- The add/edit sheet (`capability/calendar/src/test/screenshots/`), dark and light:
  - `editor_keyboard_*`, `editor_empty_*`, `editor_child_*`, `editor_edit_*`, `editor_own_length_*`, `editor_failed_*`
  - `editor_date_picker_*`, `editor_time_picker_*`, `editor_locked_*`
- The detail sheet with Edit: `detail_editable_dark.png`, `detail_editable_light.png`, `detail_syncing_dark.png`
- The entry points: `today_dark.png`, `today_light.png`, `week_dark.png`, `week_light.png`
- The emulator screenshots from Step 8.

Send the hand-off references with them: `docs/design/house_hub_handoff/screenshots/calendar-sheets/10-…` to `16-…`, `01-home-*`, `02-calendar-week-*` and `03-detail-editable-*`.

Name the parts that are the controller's own design, which the hand-off does not specify:
- ‹ and › on the date picker, with past days dimmed but selectable (design D10);
- All day as the default from 18:00 (design D8);
- the fourth Length chip for an event's own length, e.g. "1 h 30" (design D3; `editor_own_length_*`);
- the locked multi-day line, "Mon 28 – Wed 30 · change dates on your phone" (design D3);
- the Title field staying put while the chips scroll (the prototype scrolls it);
- the failure card naming the connection ("Couldn't save to Sample calendar — Calendar is full"; the hand-off says "Google Calendar"; approved as U3 in 2b-1);
- the screenshots show no focus border on the Title: nothing is focused in screenshot tests. The emulator screenshots show it.

Show the offline path: run `./gradlew :app:testDebugUnitTest --tests "*SampleAddTest*"` and tell the user what it proves (an event added while the sample calendar is offline shows at once as syncing, can be renamed and deleted before it syncs, and once the calendar is back nothing of it remains, with the toasts "Event added", "Changes saved", "Event deleted"), alongside the Step 8.9 screenshots.

Ask one design question: the locked line drops the month ("Mon 28 – Wed 30"), as the design says. Across a month's end it reads "Wed 30 – Fri 2". Should it add the month then ("Wed 30 Sep – Fri 2 Oct")?

Also list anything you noted in Task 8 Step 9, Task 11 Step 9 or Step 8 above.

Ask: "Do these match what you want? Any changes before I update the README?"

- **If the user asks for changes:** make them, and re-record only the affected images with `--tests`. Look at them, run `./gradlew testDebugUnitTest verifyRoborazziDebug`, re-send the changed images, and commit with a message describing the change. Repeat until the user approves.
- **When approved:** continue to Step 10.

- [ ] **Step 10: Update the README**

Make these edits to `README.md`:

1. **Build and run.** In the paragraph starting "Debug builds seed a sample household", replace `so its events can be deleted and assigned on the tablet.` with `so events can be added there, and its events edited, deleted and assigned, on the tablet.` Then add this paragraph after it:
```markdown
To see what the tablet does while a calendar can't be reached, a debug build can take the sample calendar offline and bring it back: `adb shell am broadcast -n uk.co.siland.culvery/.DebugOfflineReceiver --ez offline true` (or `false`). Changes made meanwhile show as syncing and are sent once it is back.
```

2. **Modules.** Replace the `:capability:calendar` row's text with `Calendar contract (read and write), ``calendar.db`` cache and outbox, 5-minute sync, event editor, Home cards, Calendar tab, event detail and add/edit sheets`.

3. **Adding a calendar provider.** In step 3 ("Writing (optional)"):
   - after the bullet starting "`create`, `update` and `delete` write to one source.", add:
```markdown
   - `create` takes a client key: use it as the event's id, so the returned `remoteId` equals it, and when a create repeats a key already used on that source, return the event that key made instead of making a second (Google: `events.insert` with `id = clientKey`; a 409 means it exists, so fetch and return it). The app sends a create again with the same key when it never heard back, so this is what stops duplicates.
```
   - in the contract-test example, replace `// WRITE providers only; the six write checks fail if these are missing.` with `// WRITE providers only; the seven write checks fail if these are missing.`

4. **PINs.** Replace the bullet starting "Calendar changes follow the roles:" with:
```markdown
- Calendar changes follow the roles: Admins and Adults can add events for anyone, and edit, delete and assign any event on the master calendar; a Child can add events only for themselves, edit or delete only events they added (and can't move one to someone else), and can't assign. A refused change says why in a toast and signs the person out, so the next tap asks for a PIN. Events from other calendars, and repeating events, can't be changed on the tablet, and an event over several days can have only its title and who changed.
```

- [ ] **Step 11: Check the README reads sensibly**

Read `README.md` through once. Check that the provider steps still number 1 to 6, the code blocks are balanced, and nothing still mentions the six write checks or events that can only be "deleted and assigned":
```bash
grep -nE "six write checks|can be deleted and assigned" README.md
```
Expected: no output.

- [ ] **Step 12: Update the follow-ups**

In `docs/superpowers/plans/2026-09-23-plan1-followups.md`:
1. Under "## From Plan 2b-1 (deferred)", delete the **For Plan 2b-2** block (its heading and its four bullets): this plan did them.
2. Under "## From Plan 2b-1 (deferred)" → **For Plan 3**, delete the two bullets this plan also did:
   - "The editor's reads in `resolve()` (for `mayDelete`, `delete` and `assign`) are outside the write path's catch, …" (Task 9's shared single-action runner catches them in both hosts);
   - "When the provider accepts a change but saving it to the mirror fails, the editor toasts …" (Task 5: the change is `Done`, and the next sync mirrors it).
3. Under "**Next migration or Room upgrade**", delete the bullet "The driver-based `MigrationTestHelper` may not catch a dropped table. At v2 → v3, check it does, or assert the table list in the test." (Task 1 asserts the table list.)
4. The plan review already added "## From Plan 2b-2 (deferred)" at the end of the file. Under its **For Plan 3**, add:
```markdown
- The editor's Retry path queues a create with its key; if that queue insert then fails (`TRY_AGAIN`), the sheet's Try again chooses a new key, and a provider that did make the event ends up with two. Fold into R3 with the other store failures.
- The Google writer: `events.insert` with `id = clientKey`, and on 409 fetch and return the existing event.
```
   and under its **For Plan 4**, add:
```markdown
- The on-device pass: the add/edit sheet with the Samsung keyboard on the SM-T510, and with a signed release in lock-task mode (debug builds never call `startLockTask`, so the 2b-2 walkthrough checked the immersive window only).
```
5. Add under the right plan any item you noted during this plan that was deferred rather than fixed.

- [ ] **Step 13: Commit**

```bash
git add README.md docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Document adding and editing events, the client key, and the sample calendar's offline switch"
```

---

## Spec coverage (2b-2 design → tasks)

| Design | Where |
|---|---|
| §1 scope; §9 out of scope (no recurring, no multi-day create or date change, master only) | Tasks 5–11; the locked edit in Tasks 6, 8 |
| D1 save flow (direct attempt, refusal card and Try again, queued close, later rollback toast), success toasts | Tasks 3, 5, 9 |
| D2 Who defaults, a Child's disabled chips (in an edit too, §6), the refusal on Save | Tasks 5, 6, 8, 9 |
| D3 own start on Pick time…, a fourth Length chip, the multi-day lock, new events within one day | Tasks 6, 8 |
| D4 `EventForm`, stateless sheet, host, overlay swaps, `initialMode`, pickers inside the sheet | Tasks 6–9 |
| D5 `calendar.db` v3, `MIGRATION_2_3`, the migration test with the table list, `create(clientKey)`, the contract check and fixtures | Tasks 1, 2 |
| D6 queued creates opened, edited and deleted before they sync; in-order delivery | Tasks 3, 4, 5, 9, 12 |
| D7 editor `create`/`update`, rules (the Who rule on edits included), `createdBy` from the PIN, the re-read after authorising (update and delete) | Task 5 |
| D8 sheet UI (hand-off 10–14), 100-character cap, one save at a time, closing mid-save | Tasks 5, 6, 8, 9 |
| D9 keyboard: focus on open, Done, toasts above the IME, `adjustResize` | Tasks 8, 10, 12 |
| D10 pickers (hand-off 15, 16) with paging and wrapping | Tasks 7, 8 |
| D11 no "Discard changes?" | Task 8 (✕ closes), Task 12 step 8.6 |
| D12 entry points (+, Add event, column taps and hints, Edit), hidden without a writable master | Tasks 4, 9, 11 |
| D13 housekeeping (`SilentToaster`, no `CalendarSync` defaults) | Task 3 |
| §3.4 contract wording and the testkit's check; a write the provider accepted is done even if storing it fails | Tasks 2, 5; README in Task 12 |
| §3.6 a refused create drops what is queued behind it, one toast; the duplicate-create fix | Tasks 2, 3 |
| §3.7 wording: `cannotAddForOthers` (for adds and for a change of Who), `couldNotSave` with the connection label | Tasks 5, 9 |
| §4.4 the sheet above the keyboard; the kiosk keyboard risk | Tasks 8, 10; Task 12 step 8 and the follow-ups |
| §5 errors and offline (offline add/edit, refusal, timeout after create, child, gone event, store failure) | Tasks 3, 5, 9, 12 |
| §6 an edit that changes Who follows the add rule | Tasks 5, 9 |
| §7 testing list, Roborazzi list, emulator walkthrough | Tasks 1–12 |
| §8 review focus | Review Focus above |
