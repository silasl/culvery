# Culvery — Plan 2b-1: Change events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** People can open any calendar event from Home or the week view and see its detail sheet. They can delete an editable event or assign a phone-added one, behind a personal PIN and the role rules. Changes go to the provider directly, or through an outbox when offline, and a later rejection is rolled back with a toast. All of this runs against the debug fake provider, with a redesigned PIN pad and a 2-minute status-bar session.

**Architecture:** `:core:plugin` gains two shell contracts, `OverlayHost` and `Toaster`. `:app` draws them as a sheet layer with a scrim, and a toast pill above the PIN pad. `:core:access` gains a 2-minute session and three new `authorise` parameters: `reason`, `allow` and `refusal`. The calendar gains a `CalendarWriter` contract that providers bind `@IntoSet`. `calendar.db` v2 adds a master flag and an `outbox` table. The `event` table stays a pure mirror of the provider. `CalendarEditor` authorises, tries the writer for up to 10 s, and queues the change when it can't reach the provider. The sync loop drains the outbox before each pass under a single-writer `Mutex`. The repository lays pending changes over the mirror, so a queued delete hides the event and a queued assign shows as syncing.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.x (KSP), Room 2.8.0 + room-testing, Coroutines/Flow, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1.

**Spec:** `docs/superpowers/specs/2026-09-24-culvery-2b1-change-events-design.md` (binding). Parent spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§5–§9).
**Design reference:** `docs/design/house_hub_handoff/README.md` §7 ("Calendar sheets", "Permissions and PIN", "Sheet 1 — Event detail") and the Theming table; `screenshots/calendar-sheets/03`–`09` (dark and light).
**Previous plan (format, constraints, review outcome):** `docs/superpowers/plans/2026-09-23-culvery-02a-calendar-read.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`.

**Plan series:** 1 Foundation (done) · 2a Calendar read path (done) · **2b-1 Change events (this plan)** · 2b-2 Add and edit (quick-add/edit sheet, pickers, Edit button, + / Add event / column hints, keyboard) · 3 Google and ICS providers · 4 Weather, setup, settings, release.

**Task order and why it differs from the suggested order:**
1. `calendar.db` v2 **and the whole store API the write path needs**: the master flag, the outbox, single-event reads and writes, the `MigrationTestHelper` test, and the unknown-health fix, which lives in `CalendarStore.healthOf`. Every later task only calls the store.
2. **Shell before access.** Access now shows a refusal as a toast, so it needs the `Toaster` contract that the shell task adds. The shell task also adds the overlay host and the status-bar sign-in, and removes the rail chip.
3. Access: the 2-minute session, `reason`, `allow`, `refusal`, and `NotAllowed` with a message.
4. PIN pad redesign, placed over the sheet when one is open.
5. `CalendarWriter` contract, the write checks in the contract suite, and the broken-writer self-test fixture.
6. Fake provider writes. The hand-off's week moves onto the writable Family source, tagged by person.
7. Sync loop and outbox drain: `requestSync`, `Mutex`, the shared `callWriter`, in-order delivery, backoff, the 48-hour cap, rejection toasts, and the deferred cancellation and mid-sync tests.
8. `CalendarEditor` and `CalendarPermissionSource`.
9. Repository: `EventRef`, the outbox overlay, the new `EventUi` fields, the "When" label, and the midnight-end test.
10. Event detail sheet, in every state, with screenshots.
11. Opening the sheet from Today rows and week chips, the badges, and removing the Coming up link, with screenshots.
12. Debug seed (master flag and tags), the rollback demo test, the emulator walkthrough, the **USER CHECKPOINT**, then the README.

**Deviations from the design and extensions (deliberate):**
- `authorise` gains a fourth parameter, `refusal: Refusal = Refusal.InPad`, as well as the design's `reason` and `allow`.
  - `Refusal.InPad` keeps today's behaviour: the pad shows "{name} can't do that" and asks again.
  - `Refusal.Toast { name -> "…" }` closes the pad, shows the caller's wording as a toast, and returns null. This also applies on the session shortcut, so a signed-in child gets the toast without seeing a pad (hand-off: "If a check fails → toast").
  - On the session shortcut, a `Refusal.Toast` refusal also **locks the session** (and doesn't extend it), so the next tap brings up the PIN pad and an adult can take over from a signed-in child. **Approved by the user (U2).**
  - `AccessControl` shows the toast itself through the injected `Toaster`. The calendar supplies the wording. With `Authorised?` as the return type, the caller can't tell a refusal from a cancel, so the toast has to come from `AccessControl`.
- **The session extends only on a successful `authorise`** (a PIN-gated action), never on a touch. `AccessControl.touch()`, `ShellViewModel.onUserActivity()` and `MainActivity`'s touch hook are removed, so a child tapping around can't keep an adult's session alive. **Approved by the user (U1)**; the design's D1 and §6 and the parent spec's §8 are amended to "2 minutes after the last authorised action".
- `Toaster` is an injectable singleton (`ShellToasts`, bound in `:app`). There is no `LocalToaster`: every toast comes from code that has the injected `Toaster` (access control, the editor, the outbox drain).
- The design's `WriteFailed` event is not built. The drain shows what it had to drop through the injected `Toaster`, one toast per connection per pass: "Couldn't save to {label} — {reason}" for one change, "Couldn't save {n} changes to {label}" for several, and "Couldn't save to {label}" when there is no reason (nothing can deliver it, or it waited too long).
- The editor shows its own outcome toasts ("Event deleted", "Couldn't save to {label} — {reason}") from the application scope, so closing the sheet mid-write can't lose them. The sheet only decides whether it stays open.
- **Assign is its own change kind, `ASSIGN`** (stored as TEXT in `outbox.kind`, so no schema change; the design lists CREATE, UPDATE and DELETE). Only its draft's `forPerson` counts: the editor and the drain send the event **as the mirror has it at send time** with the new person, so an assign never overwrites a title or time changed on a phone meanwhile. `CalendarWriter.update` changes only the title, the times and the tags and keeps everything else (Google: PATCH).
- The editor and the drain call writers through one classifier, `callWriter`, which sorts every outcome into `Accepted`, `Rejected` or `Retry`. Writes never set a connection's health: the pass that `requestSync()` starts straight after reports `NeedsSignIn` from the reads (design §5).
- The drain delivers the changes to one event **in the order they were made**: once an event's first change is waiting or has failed, its later changes wait too. Once a connection fails to answer (unreachable, timed out or needs sign-in), its other changes wait for their next attempt in that pass instead of each costing a timeout. A change still queued **48 hours** after it was made is dropped with a toast. One unreadable outbox row is logged and dropped rather than stopping the queue.
- The scrims (`rgba(0,0,0,.55)` behind sheets and `rgba(0,0,0,.5)` behind the PIN pad) are theme-independent constants in `:core:ui`'s `ShellTokens`, not `HhColors` tokens. This settles the Plan 1 follow-up "make the PIN pad scrim colour a token".
- Wording that names Google in the hand-off uses the **connection label** instead. So the fake reads "Syncing to Sample calendar…", "…will be removed from Sample calendar for everyone" and "Couldn't save to Sample calendar — {reason}", and a Google connection reads as the hand-off does. The 2a subtitle already does this ("synced with {label}"). **Approved by the user (U3).** Plan 3 moves the failure, repeating-event and delete wording to the service name ("Google Calendar") once a second label exists (in the follow-ups).
- `EventUi.createdBy` is a display `String` (a name, "Added from phone", or "Calendar feed"), not `Person?`. The editor checks ownership against the stored person id, never against the label.
- The detail sheet reads `EventDetailUi(event: EventUi, whenLabel: String)` from `CalendarRepository.event(ref, today)`. Rows don't need the "When" label ("Today · 19:30–21:00"), so it isn't in `EventUi`.
- A lazy-list key can't be a data class on Android. `EventRef.listKey` joins the three ids with NUL (`\u0000`), which no provider id contains, so it stays unambiguous when ids contain `/`.
- Delete asks for the PIN twice at most. `mayDelete(ref)` guards before the confirmation. `delete(ref)` authorises again, which normally passes silently on the session. If the session expired while the confirmation was showing, the pad appears again.
- The editor never writes directly behind a pending change for the same event: it queues the new change after the pending one, so the drain applies both in order. The editor's writes run one at a time under a `Mutex`, which re-reads the queue inside it, so two sheets can't race; the lock is held only around the write, never while the PIN pad is up. A second delete of an event already queued for delete is not queued again (checked under the same lock).
- The provider call and the mirror update run on `@ApplicationScope`, so closing the sheet mid-write can't lose a change. They don't take the sync's `passLock` (that could hold the 10 s attempt behind a 60 s pass), so a pass already in flight may briefly put back the old mirror; the `requestSync()` after every write corrects it.
- The loop no longer uses `collectLatest`. A trigger (a connection change or `requestSync`) that arrives mid-pass runs one more pass after it, rather than cancelling it. The loop also wakes early when an outbox entry falls due, so a 30 s backoff isn't stuck behind the 5-minute tick. The first pass starts when the first connection list arrives, so a connection added before the loop listened is never missed.
- A queued change for a connection flagged `NeedsSignIn` is **not** paused: it retries with the normal backoff, and the connection is skipped for the rest of the pass once it fails. This meets design §5 ("the queue resumes after reconnect") without a separate pause state.
- The contract adds one rule: **deleting an event that no longer exists succeeds.** Without it, a retried delete that had already reached the provider would be toasted as a failure. **Approved by the user (U3)**; the contract suite checks it.
- The fake provider now puts the hand-off's week on the writable **"Family calendar"** source. Events are tagged by person *name*, and the debug seed turns the names into household ids (`tagSamples`). INSET day stays on the read-only "School terms" source. Plumber quote call stays untagged ("Added from phone"). The fake forgets its writes when the app restarts, which is acceptable for debug.
- `CalendarStore.setMaster` also records the source as writable. `CalendarSetup.setMaster` checks the provider first, so a 2a install upgraded in place gets a writable master without clearing data.
- The spec lists the unknown-health fix under the repository, but it is done in Task 1 because the code lives in `CalendarStore.healthOf`.
- The detail sheet's "Repeats" row reads "Yes". The provider gives no recurrence rule to describe.

**Carried forward from the 2a review (still settled; do not reintroduce):** T4, S3, S5, S9, S10, S11, R11, R13, B1 stay rejected. R3 and R9 stay deferred to Plan 3, and R5 to Plan 4. The items deferred to 2b are **in scope here**:
- C1, the overlay host: Task 2.
- C2, `EventRef`: Tasks 1 and 9.
- C4, `CalendarWriter`: Task 5.
- C6 (`requestSync`) and R12 (the `Mutex`): Task 7.
- C8, the outbox table: Tasks 1 and 9.
- The `MigrationTestHelper` test: Task 1.

**Review outcome (settled; do not reintroduce):**
- Accepted: B1, `Locale.ENGLISH` for the short day label (JDK 17's CLDR prints "Sept" for `Locale.UK`).
- Accepted: B2, the drain delivers each event's changes in order (`blockedRefs`); no collapsing.
- Modified: B3, an `ASSIGN` change kind applied to the mirror's event at send time; KDoc on `CalendarWriter.update` (title, times and tags only).
- Deferred to 2b-2: S1, a `clientKey` on the outbox for idempotent creates.
- Rejected: S2, a `baseJson` column (B3 covers it).
- Accepted: R1, each outbox row decodes on its own (a bad row is logged and dropped), and a failing drain doesn't stop the sync.
- Modified: R2, a change still queued 48 hours after it was made is dropped with a toast; no attempts cap.
- Accepted: R3, a connection that fails to answer is skipped for the rest of the pass.
- Accepted: R4, a change nothing can deliver is toasted before it is dropped.
- Modified: R5, a `Mutex` inside the editor's write, re-reading the queue; never held across `authorise`.
- Accepted via Simp3: R6, no stale health snapshot, because there is no pause.
- Accepted: R7, documented only (a comment on the editor's write).
- Modified: R8, one rejection toast per connection per pass through the injected `Toaster`.
- Deferred to Plan 3: R8's mapping of raw provider text to fixed wording.
- Deferred to Plan 3: R9, `runInterruptible` in the Google writer and its contract check.
- Modified: R10, the editor toasts outcomes on the application scope through `Toaster`.
- Accepted: R11, KDoc on the exception classes (429, 403 rate limits and 5xx are Unreachable; a delete's 404/410 is success).
- Accepted: R12, `createdByLabel` keys on `isMaster`, with a test.
- Accepted: R13, no `drop(1)`; the first connection list starts the first pass.
- Accepted: T1, T2, T3, T4, T5, T7, T8, T11 (test fixes as listed in the tasks).
- Modified: T6 and T9, the host test checks that a rejection keeps the sheet open; the toast checks move to `CalendarEditorTest`.
- Modified: T10, an assign-rejected test, `detail_pin_light`, and a rollback demo test on the fake's existing switches (no debug trigger).
- Accepted: BM1, the schema assets are a `debug` source set (they ship in the debug APK).
- Moot: BM2, `WriteFailureToastsTest` no longer exists.
- Rejected: BM3 (the stop-and-ask covers it).
- Accepted: BM4, a note on the badge-count tests.
- Deferred to Plan 3: DL1, a recurrence label from Google's RRULE.
- Accepted: DL2, the note icons' colour is a checkpoint question.
- Accepted: Simp1, one `callWriter` returning `WriteOutcome`, shared by the editor and the drain (not always-enqueue).
- Accepted: Simp2, no `CalendarWriteEvents`, `WriteFailed` or `WriteFailureToasts`; `CalendarSync` takes the `Toaster`.
- Accepted: Simp3, no NeedsSignIn pause.
- Rejected: Simp4, keep `Refusal`.
- Accepted after R10: Simp5, no `LocalToaster`.
- Rejected: Simp6, `NotEditable` closes the sheet and `Cancelled` doesn't.
- Accepted: Simp7, the editor takes no `providers`.
- Rejected: Simp8, the queued-delete check stays, moved under R5's lock.
- Accepted: Simp9, `listKey` joins with NUL.
- Rejected: Simp10, Simp11.
- Modified: Simp12, the README is part of Task 12; Tasks 5 and 6 stay separate.
- Modified: Simp13, `detail_untagged_collapsed_dark` is dropped; the light variants stay.
- Approved by the user: U1 (the session extends only on an authorised action), U2 (a shortcut refusal toasts, then locks), U3 (delete-missing succeeds; the connection label in 2b-1).

## Global Constraints

- Package root `uk.co.siland.culvery`; app name "Culvery".
- `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Pinned versions: AGP 8.13.0, Gradle 8.13, Kotlin 2.2.20, KSP 2.2.20-2.0.3, Compose BOM 2025.09.00, Hilt 2.57.1, Room 2.8.0 (with `room-testing` 2.8.0, added in Task 1), Robolectric 4.16, Roborazzi 1.46.1. If a version fails to resolve, take the newest **patch** in the same minor line. Never move to a new major or minor version without asking.
- **Deprecated APIs:** use none without asking the user first. If any API this plan uses shows a deprecation warning in these versions, **stop and ask**. The steps name the APIs worth checking. `@OptIn` to an *experimental* API is allowed only where the plan says so; the plan's only new one is `ExperimentalCoroutinesApi` in tests, as 2a already does. Do not use `androidx.security:security-crypto` / `EncryptedSharedPreferences`.
- Design canvas 1280×800 dp. Hand-off §7 values are authoritative. Copy them exactly as the steps give them.
- No shadows; flat colours; no blur.
- No secrets, tokens or household data in source or build config. Sample people and events live only in `app/src/debug` and `:provider:calendar-fake`.
- PINs are exactly 4 ASCII digits.
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer.
- Module rules (enforced by `build-logic`'s `ModuleBoundaries`):
  - `:core:*` depends only on `:core:*`.
  - `:capability:X…` depends only on `:core:*` and its own family.
  - `:provider:X-…` depends only on `:core:*` and `:capability:X`, plus `:capability:X-testkit` in test configurations.
  - `:app` may depend on anything.
  - This plan adds `:capability:calendar` → `:core:access` (allowed).
  - Read `ProjectDependency.path`, never the deprecated `dependencyProject`.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test` or `check`. A task that touches one module runs that module's `testDebugUnitTest`, plus its `verifyRoborazziDebug` where it has screenshots; every task ends with the full gate before its commit.
- Screenshots:
  - Baselines live in `<module>/src/test/screenshots/`, recorded and verified on Windows.
  - Record with `./gradlew <module>:recordRoborazziDebug`, optionally with `--tests "<pattern>"` to record only new images.
  - Look at every new or changed image before committing.
  - `@GraphicsMode(GraphicsMode.Mode.NATIVE)` goes only on classes that capture screenshots or measure real text.
- Colours and type:
  - Colours come only from `Culvery.colors`, a person's own colour, or `ShellTokens`' two scrims.
  - Destructive and warning tones use the `danger`, `dangerSoft` and `dangerInk` tokens.
  - Text styles come from `HhType`, or are named `.copy()`s of it in `CalendarType` (calendar), `ShellType` (shell components in `:core:ui`) or `PinPadType` (`:core:access`).
  - **Layout numbers live in `CalendarDimens`, `ShellTokens` or `PinPadDimens`**, never inline.
- **Storage / migration policy:** `calendar.db` holds user configuration. Every schema version ships a Room `Migration` (hand-written here) with a `MigrationTestHelper` test. Never use destructive fallback. Schemas are exported to `capability/calendar/schemas/` and committed. **v2 must ship `MIGRATION_1_2` and `CalendarMigrationTest`.**
- Calendar providers and writers must be main-safe and cancellable. The engine runs every provider or writer call on `Dispatchers.IO`:
  - sync and drain calls under a 60 s timeout;
  - the editor's direct attempt under `WRITE_ATTEMPT_MS = 10_000`.
- Writers throw only `WriteRejectedException` (a permanent refusal), `NeedsSignInException` or `UnreachableException`.
- Viewing never needs a PIN. Every change calls `AccessControl.authorise` **when tapped**. Buttons are never hidden for permission reasons.

## Review Focus

1. **Deleting while offline, then the provider refuses it later.**
   - The event must vanish at once and stay hidden across syncs.
   - When the drain gets the rejection, the event must come back, with a toast saying why.
   - Tests:
     - Task 8 `offlineDeleteIsQueued`
     - Task 9 `aQueuedDeleteIsHidden`
     - Task 7 `drainRejectionDropsTheChangeAndToastsWhy`
     - Task 9 `aRejectedQueuedDeleteBringsTheEventBack`
     - Task 12 `aRefusedOfflineDeleteComesBackWithAToast` (the sample calendar end to end)
2. **The 5-minute sync does a full replace while changes are queued.**
   - Queued changes live only in `outbox`, so a full replace of `event` must not lose them or undo the overlay.
   - Tests:
     - Task 1 `fullReplaceSyncLeavesTheOutboxAlone`
     - Task 9 `pendingDeleteStaysHiddenAfterAFullReplaceSync`
     - Task 9 `pendingAssignShowsTheNewPersonAsSyncingAfterAFullReplaceSync`
3. **A child tries to delete someone else's event, or assign one.**
   - They must be refused with the hand-off's wording as a toast. They must still be able to delete an event they created.
   - Tests in Task 8:
     - `childCannotDeleteSomeoneElsesEventAndIsToldWhy`
     - `childCanDeleteTheirOwnEvent`
     - `adultCanDeleteAnyMasterEvent`
     - `childCannotAssignAndIsToldToAskAnAdult`
     - `signedInChildIsRefusedWithoutAPinPad` (the refusal also signs Mia out)
4. **The session runs out while the sheet is open**, between the guard and the confirmation, **or a child keeps an adult's session alive.**
   - The delete must ask for the PIN again rather than go through as nobody, or as a stale person.
   - Only an authorised action extends the session; touches don't. A refusal on the session shortcut signs the person out.
   - Tests:
     - Task 3 `sessionLastsTwoMinutesAfterTheLastAuthorisedAction`
     - Task 3 `onlyAnAuthorisedActionExtendsTheSession`
     - Task 3 `aRefusalOnTheSessionShortcutToastsThenLocks`
     - Task 8 `sessionExpiryBetweenGuardAndConfirmAsksForThePinAgain`
5. **The tablet already has 2a data when 2b-1 is installed.**
   - The v1 → v2 migration must keep connections, sources (mappings), events and cursors.
   - The app must open on cached events, with no crash and no resync from nothing.
   - Tests:
     - Task 1 `migrationFromV1KeepsConnectionsSourcesEventsAndCursors`
     - Task 12 walkthrough step "install over the 2a build"
6. **A double tap, or a sheet closed mid-write.**
   - A second tap on "Delete event" or a person chip must not send two writes.
   - An event already queued for delete must not be queued twice.
   - Closing the sheet during the provider call must not lose the change.
   - Tests:
     - Task 10 `deleteEventIgnoresASecondTapWhileBusy`
     - Task 8 `deletingAnEventAlreadyQueuedForDeleteDoesNotQueueItTwice`
     - Task 8 `closingTheSheetMidWriteStillFinishesTheWrite`
     - Task 8 `concurrentWritesToOneEventRunOneAtATime`
7. **Changes to one event arrive out of order, or an assign undoes an edit made on a phone.**
   - A change queued behind one in backoff must wait for it, and a connection that doesn't answer must not cost a timeout per change.
   - An assign must send the event as the mirror has it when it is sent, not as it was when it was queued.
   - Tests:
     - Task 7 `aLaterChangeWaitsForAnEarlierOneInBackoff`
     - Task 7 `anUnreachableConnectionIsNotTriedAgainInThePass`
     - Task 7 `aQueuedAssignIsAppliedToTheEventAsItIsNow`
     - Task 9 `aQueuedAssignChangesOnlyThePerson`

---

## File Structure

```
gradle/libs.versions.toml                                        (modify: room-testing)

core/plugin/src/main/java/.../core/plugin/Overlays.kt            (create: OverlayHost, LocalOverlayHost, Toaster)
core/ui/src/main/java/.../core/ui/Shell.kt                       (create: ShellTokens, ShellType, HhSheet, HhCloseButton, HhToast)
core/access/src/main/java/.../core/access/
  AccessControl.kt           (modify: 2-min session, no touch(), PinReason, pinReasonText, Refusal, new authorise)
  DefaultAccessControl.kt    (modify: reason/allow/refusal, Toaster, lock on a shortcut refusal)
  PinPromptController.kt     (modify: PinRequest.reason, NotAllowed.message)
  ui/PinPad.kt               (rewrite: hand-off §7 pad, over-sheet placement)
  ui/PinPadDimens.kt         (create: PinPadDimens, PinPadType)
core/access/src/test/java/.../core/access/RecordingToaster.kt    (create)

app/src/main/java/uk/co/siland/culvery/
  MainActivity.kt, di/AppModule.kt                               (modify: overlay/toast layers, Toaster binding, no touch hook)
  CulveryApp.kt                                                  (modify: providers into the seed)
  shell/ShellOverlays.kt                                         (create: OverlayState, ShellToasts, ToastMessage)
  shell/ShellUiState.kt, shell/ShellViewModel.kt                 (modify: SessionUi, no onUserActivity)
  shell/ui/OverlayLayers.kt                                      (create: OverlayLayer, ToastLayer)
  shell/ui/CulveryShell.kt, NavRail.kt, StatusBar.kt             (modify: status-bar sign-in, no rail chip)
app/src/debug|release/java/uk/co/siland/culvery/DebugSeed.kt     (modify: master + tags)
app/src/test/java/uk/co/siland/culvery/  shell/ShellToastsTest, shell/ui/OverlayLayersTest (create);
                                          shell/Fakes, shell/ShellViewModelTest, shell/ui/ShellLayoutTest,
                                          shell/ui/ShellScreenshotTest (modify)
app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt     (modify); SampleRollbackTest.kt (create)
app/src/test/screenshots/  home_session_dark, pin_pad_{dark,light}, home_calendar_{dark,light} (re-recorded);
                           home_toast_dark, pin_pad_wrong_dark (new)

capability/calendar/
  build.gradle.kts                   (modify: :core:access, room-testing, schemas as debug assets)
  schemas/…CalendarDatabase/2.json   (generated, committed)
  src/main/java/uk/co/siland/culvery/capability/calendar/
    CalendarContract.kt      (modify: EventDraft, CalendarWriter, WriteRejectedException)
    Stored.kt                (modify: EventRef, StoredSource.isMaster, ChangeKind, PendingChange)
    db/CalendarDatabase.kt   (modify: v2 — source.isMaster, outbox, DAO)
    db/Migrations.kt         (create: MIGRATION_1_2)
    CalendarStore.kt         (modify: master, outbox, single-event reads/writes, healthOf)
    Writes.kt                (create: backoff, OUTBOX_MAX_AGE_MS, WriteOutcome, callWriter, couldNotSave, assignDraft)
    CalendarSync.kt          (modify: Mutex, in-order outbox drain, writers, Toaster)
    CalendarSyncLoop.kt      (modify: @Singleton, requestSync, early wake)
    CalendarPermissions.kt   (create: CalendarPermissions, CalendarPermissionSource)
    Editability.kt           (create: ReadOnlyReason, readOnlyReason())
    CalendarEditor.kt        (create: EditResult, EVENT_DELETED, write Mutex, outcome toasts)
    PendingOverlay.kt        (create: ShownEvent, overlayPending())
    CalendarUi.kt            (modify: EventUi fields, EventDetailUi, whenLabel, createdByLabel, badges)
    CalendarRepository.kt    (modify: overlay, event(ref, today), people)
    CalendarSetup.kt         (modify: setMaster, requestSync)
    CalendarCapability.kt    (modify: editor → hosts)
    di/CalendarModule.kt     (modify: migration, writers, permissions)
    ui/CalendarType.kt       (modify: sheet styles and dimens)
    ui/EventDetailSheet.kt   (create: stateless sheet)
    ui/EventDetailHost.kt    (create: host + rememberEventOpener)
    ui/Badges.kt             (create: EventBadges)
    ui/TodayCard.kt, ui/WeekView.kt, ui/ComingUpCard.kt, ui/Components.kt, ui/CardHosts.kt (modify)
  src/test/java/uk/co/siland/culvery/capability/calendar/
    CalendarMigrationTest, CalendarStoreTest, CalendarSyncTest, CalendarSyncLoopTest,
    ScriptedProvider, ScriptedWriter, TestAccess, RecordingToaster, StubEditor, CalendarEditorTest,
    CalendarPermissionsTest, PendingOverlayTest, WhenLabelTest, CalendarRepositoryTest, CalendarSetupTest,
    CalendarCapabilityTest, ui/RecordingOverlay, ui/EventDetailSheetTest, ui/EventDetailHostTest,
    ui/DetailScreenshotTest, ui/OpenEventTest, ui/SampleUi, ui/CardsTest, ui/WeekViewTest,
    ui/CardScreenshotTest, ui/WeekScreenshotTest, ui/CardHostsMidnightRolloverTest (modify/create as each task says)
  src/test/screenshots/detail_*.png, today_badges_dark.png, week_syncing_dark.png (new); coming_up_*, week_* (re-recorded)

capability/calendar-testkit/   CalendarProviderContractTest (write checks), fixtures TinyProvider/TinyContracts, ContractSuiteSelfTest
provider/calendar-fake/        FakeCalendarProvider (writer, switches, tagSamples), SampleEvents (Family calendar), module, tests
README.md                      (modify, Task 12 after the checkpoint)
```

`...` stands for `uk/co/siland/culvery`; every step below spells out the full path.

---

### Task 1: `calendar.db` v2 — master flag, outbox, migration, and the store API for writes

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `capability/calendar/build.gradle.kts`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt` (add `EventDraft`)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Stored.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/Migrations.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`
- Create (generated): `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/2.json`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarMigrationTest.kt` (create)

**Interfaces:**
- Consumes (existing):
  - `CalendarStore(db: CalendarDatabase)` with `addConnection`, `visibleSourcesFor`, `setHealth`, `markSynced`, `cursor`, `applySync`, `eventsBetween`, `connections`, `connectionIds`, `connectionsNow`
  - `RemoteEvent`, `EventTime`, `DateRange`, `SyncCursor`, `SourceMapping`, `StoredConnection`, `StoredEvent`
  - `calendarDb()` test helper
- Produces:
  - `data class EventDraft(title: String, start: EventTime, end: EventTime, forPerson: String?, createdBy: String?)` in `CalendarContract.kt`
  - `data class EventRef(connectionId: String, sourceId: String, remoteId: String)` with `val listKey: String`
  - `StoredEvent.ref: EventRef`
  - `StoredSource(connectionId, source, mapping, isMaster: Boolean = false)`
  - `enum class ChangeKind { CREATE, UPDATE, DELETE, ASSIGN }` (an ASSIGN's draft counts only for its `forPerson`)
  - `data class PendingChange(id: Long, connectionId: String, sourceId: String, remoteId: String?, kind: ChangeKind, draft: EventDraft?, attempts: Int, nextAttemptMillis: Long, createdMillis: Long)` with `val ref: EventRef?`
  - `CalendarStore`:
    - Sources and the master:
      - `fun sources(): Flow<List<StoredSource>>`
      - `suspend fun source(connectionId: String, sourceId: String): StoredSource?`
      - `fun master(): Flow<StoredSource?>`
      - `suspend fun setMaster(connectionId: String, sourceId: String)`: throws `IllegalArgumentException` for an unknown source
    - Events:
      - `fun event(ref: EventRef): Flow<StoredEvent?>`
      - `suspend fun eventNow(ref: EventRef): StoredEvent?`
      - `suspend fun applyAccepted(connectionId: String, sourceId: String, event: RemoteEvent, zone: ZoneId, completing: Long? = null)`
      - `suspend fun applyDeleted(ref: EventRef, completing: Long? = null)`
    - The outbox:
      - `suspend fun enqueue(change: PendingChange): Long`
      - `fun pending(): Flow<List<PendingChange>>`: skips a row it can't read
      - `suspend fun pendingNow(): List<PendingChange>`: in queue order; logs and deletes a row it can't read
      - `suspend fun nextAttemptMillis(): Long?`
      - `suspend fun reschedule(id: Long, attempts: Int, nextAttemptMillis: Long)`
      - `suspend fun dropChange(id: Long)`
  - `val MIGRATION_1_2: Migration` in `uk.co.siland.culvery.capability.calendar.db`
  - Catalog alias `libs.room.testing`

- [ ] **Step 1: Add room-testing to the catalog and wire the test schemas**

In `gradle/libs.versions.toml`, under `[libraries]`, after the `room-compiler` line, add:
```toml
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

Replace `capability/calendar/build.gradle.kts` with:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
    alias(libs.plugins.roborazzi)
}

android {
    // MigrationTestHelper reads the exported schema JSON as assets. Robolectric reads the variant's assets, not
    // test assets, so the schemas are debug assets: a few KB in the debug APK, nothing in release.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
}

dependencies {
    api(project(":core:plugin"))
    // PersonId and Person appear in this module's public API (SourceMapping, EventUi).
    api(project(":core:household"))
    implementation(project(":core:ui"))
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.room.testing)
}
```

Check before continuing: `AndroidSourceDirectorySet.srcDir` must not be deprecated in AGP 8.13. Look for a deprecation warning when Gradle configures the project in Step 3. If it is deprecated, stop and ask.

- [ ] **Step 2: Write the failing store tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`, add these imports:
```kotlin
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertThrows
import kotlinx.coroutines.runBlocking
import uk.co.siland.culvery.capability.calendar.db.ConnectionEntity
import uk.co.siland.culvery.capability.calendar.db.OutboxEntity
```
(`first` is already imported; keep one copy.) Then add these members at the end of the class:
```kotlin
    private val alexId = "alex-id"

    private fun eventDraft(title: String, day: Int, hour: Int, forPerson: String? = alexId, createdBy: String? = alexId) =
        EventDraft(title, EventTime.Timed(at(day, hour)), EventTime.Timed(at(day, hour + 1)), forPerson, createdBy)

    private fun change(
        kind: ChangeKind,
        remoteId: String? = "e1",
        draft: EventDraft? = eventDraft("Swim", 23, 9),
        next: Long = 0L,
        attempts: Int = 0,
    ) = PendingChange(0, "c1", "s1", remoteId, kind, draft, attempts, next, createdMillis = 100L)

    @Test
    fun newSourcesAreNotTheMaster() = runTest {
        connect("s1", "s2")
        assertThat(store.sources().first().map { it.isMaster }).containsExactly(false, false)
        assertThat(store.master().first()).isNull()
    }

    @Test
    fun setMasterMarksOneSourceAsTheWritableMasterAndClearsTheOthers() = runTest {
        connect("s1", "s2")
        store.setMaster("c1", "s1")
        store.setMaster("c1", "s2")
        val master = store.master().first()!!
        assertThat(master.source.id).isEqualTo("s2")
        assertThat(master.source.writable).isTrue()
        assertThat(master.isMaster).isTrue()
        assertThat(store.sources().first().filter { it.isMaster }.map { it.source.id }).containsExactly("s2")
        assertThat(store.source("c1", "s1")!!.isMaster).isFalse()
    }

    @Test
    fun setMasterForAnUnknownSourceChangesNothing() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.setMaster("c1", "nope") } }
        assertThat(store.master().first()!!.source.id).isEqualTo("s1")
    }

    @Test
    fun outboxRoundTripsEveryKindAndDraftShape() = runTest {
        connect("s1")
        val allDay = EventDraft("Half term", EventTime.AllDay(LocalDate.of(2026, 10, 1)), EventTime.AllDay(LocalDate.of(2026, 10, 4)), null, null)
        val create = change(ChangeKind.CREATE, remoteId = null, draft = allDay)
        val update = change(ChangeKind.UPDATE, draft = eventDraft("Swim", 23, 9, forPerson = "family"))
        val delete = change(ChangeKind.DELETE, draft = null)
        val assign = change(ChangeKind.ASSIGN, draft = eventDraft("Swim", 23, 9, forPerson = "sam-id"))
        val ids = listOf(store.enqueue(create), store.enqueue(update), store.enqueue(delete), store.enqueue(assign))
        assertThat(store.pendingNow()).containsExactly(
            create.copy(id = ids[0]),
            update.copy(id = ids[1]),
            delete.copy(id = ids[2]),
            assign.copy(id = ids[3]),
        ).inOrder()
        assertThat(store.pending().first().map { it.ref }).containsExactly(
            null,
            EventRef("c1", "s1", "e1"),
            EventRef("c1", "s1", "e1"),
            EventRef("c1", "s1", "e1"),
        ).inOrder()
    }

    @Test
    fun nextAttemptIsTheEarliestQueuedTime() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null, next = 5_000))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "b", draft = null, next = 1_000))
        assertThat(store.nextAttemptMillis()).isEqualTo(1_000L)
    }

    @Test
    fun anUnreadableQueuedRowIsDroppedAndTheOthersStillRead() = runTest {
        connect("s1")
        val dao = db.calendarDao()
        dao.insertOutbox(OutboxEntity(connectionId = "c1", sourceId = "s1", remoteId = "x", kind = "BOGUS", draftJson = null, attempts = 0, nextAttemptMillis = 0, createdMillis = 0))
        dao.insertOutbox(OutboxEntity(connectionId = "c1", sourceId = "s1", remoteId = "y", kind = "UPDATE", draftJson = "{", attempts = 0, nextAttemptMillis = 0, createdMillis = 0))
        val good = store.enqueue(change(ChangeKind.DELETE, draft = null))
        assertThat(store.pending().first().map { it.id }).containsExactly(good)
        assertThat(store.pendingNow().map { it.id }).containsExactly(good)
        assertThat(dao.outboxNow().map { it.id }).containsExactly(good)
    }

    @Test
    fun rescheduleAndDropChange() = runTest {
        connect("s1")
        val id = store.enqueue(change(ChangeKind.DELETE, draft = null))
        store.reschedule(id, attempts = 3, nextAttemptMillis = 9_000)
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis }).isEqualTo(3 to 9_000L)
        store.dropChange(id)
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.nextAttemptMillis()).isNull()
    }

    @Test
    fun fullReplaceSyncLeavesTheOutboxAlone() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9)))
        val id = store.enqueue(change(ChangeKind.DELETE, draft = null))
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9), timed("e2", "Walk", 23, 10)))
        store.applySync("c1", "s1", window, full())
        assertThat(store.pendingNow().map { it.id }).containsExactly(id)
    }

    @Test
    fun eventByRefFollowsTheMirror() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9)))
        val ref = EventRef("c1", "s1", "e1")
        assertThat(store.event(ref).first()!!.title).isEqualTo("Swim")
        assertThat(store.eventNow(ref)!!.ref).isEqualTo(ref)
        assertThat(store.eventNow(EventRef("c1", "s1", "nope"))).isNull()
    }

    @Test
    fun applyAcceptedUpsertsTheEventAndCompletesTheChange() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9)))
        val id = store.enqueue(change(ChangeKind.UPDATE))
        store.applyAccepted("c1", "s1", timed("e1", "Swim", 23, 9).copy(forPerson = "sam-id"), zone, completing = id)
        assertThat(store.eventNow(EventRef("c1", "s1", "e1"))!!.forPerson).isEqualTo("sam-id")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun applyDeletedRemovesTheEventAndCompletesTheChange() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9), timed("e2", "Walk", 23, 10)))
        val id = store.enqueue(change(ChangeKind.DELETE, draft = null))
        store.applyDeleted(EventRef("c1", "s1", "e1"), completing = id)
        assertThat(titlesBetween(23, 24)).containsExactly("Walk")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun applyWithoutCompletingLeavesTheQueue() = runTest {
        connect("s1")
        val id = store.enqueue(change(ChangeKind.DELETE, remoteId = "other", draft = null))
        store.applyAccepted("c1", "s1", timed("e1", "Swim", 23, 9), zone)
        assertThat(store.pendingNow().map { it.id }).containsExactly(id)
    }

    @Test
    fun unknownHealthCodeIsAnError() = runTest {
        db.calendarDao().insertConnection(ConnectionEntity("c9", "calendar.test", "Odd", "{}", "BOGUS", null, null))
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Error("Unknown health code BOGUS"))
    }

    @Test
    fun okHealthCodeIsOk() = runTest {
        connect("s1")
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Ok)
    }

    @Test
    fun listKeysDifferWhenSlashesMoveBetweenIds() {
        assertThat(EventRef("a/b", "c", "d").listKey).isNotEqualTo(EventRef("a", "b/c", "d").listKey)
        assertThat(EventRef("a", "b", "c/d").listKey).isNotEqualTo(EventRef("a", "b/c", "d").listKey)
        assertThat(EventRef("ab", "c", "d").listKey).isNotEqualTo(EventRef("a", "bc", "d").listKey)
    }
```

- [ ] **Step 3: Write the failing migration test**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarMigrationTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
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
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

@RunWith(AndroidJUnit4::class)
class CalendarMigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CalendarDatabase::class.java)

    @Test
    fun migrationFromV1KeepsConnectionsSourcesEventsAndCursors() = runTest {
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO connection (id, providerId, label, configJson, health, healthMessage, lastSyncMillis) " +
                    "VALUES ('c1', 'calendar.test', 'Google', '{\"k\":\"v\"}', 'NEEDS_SIGN_IN', NULL, 1234)",
            )
            execSQL(
                "INSERT INTO source (connectionId, sourceId, name, writable, visible, personId) " +
                    "VALUES ('c1', 's1', 'Family', 1, 1, 'family'), ('c1', 's2', 'Alex', 0, 0, 'alex-id')",
            )
            execSQL(
                "INSERT INTO event (connectionId, sourceId, remoteId, title, startInstant, startDate, endInstant, endDate, " +
                    "recurring, forPerson, createdBy, startSort, endSort) " +
                    "VALUES ('c1', 's1', 'e1', 'Swim', 1000, NULL, 2000, NULL, 0, 'alex-id', 'sam-id', 1000, 2000)",
            )
            execSQL(
                "INSERT INTO sync_state (connectionId, sourceId, cursor, rangeStart) " +
                    "VALUES ('c1', 's1', 'k7', '2026-09-22|Europe/London')",
            )
            close()
        }

        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), CalendarDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val store = CalendarStore(db)
            val stored = store.connectionsNow().single()
            assertThat(stored.connection).isEqualTo(Connection("c1", "calendar.test", "Google", mapOf("k" to "v")))
            assertThat(stored.health).isEqualTo(ConnectionHealth.NeedsSignIn)
            assertThat(stored.lastSyncMillis).isEqualTo(1234L)

            assertThat(store.sources().first().map { Triple(it.source.id, it.mapping, it.isMaster) }).containsExactly(
                Triple("s1", SourceMapping(PersonId.FAMILY, visible = true), false),
                Triple("s2", SourceMapping(PersonId("alex-id"), visible = false), false),
            )
            assertThat(store.source("c1", "s1")!!.source.writable).isTrue()

            val event = store.eventNow(EventRef("c1", "s1", "e1"))!!
            assertThat(listOf(event.title, event.forPerson, event.createdBy)).containsExactly("Swim", "alex-id", "sam-id").inOrder()
            assertThat(event.start).isEqualTo(EventTime.Timed(Instant.ofEpochMilli(1000)))

            val range = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), ZoneId.of("Europe/London"))
            assertThat(store.cursor("c1", "s1", range)).isEqualTo(SyncCursor("k7"))

            assertThat(store.pendingNow()).isEmpty()
            assertThat(store.master().first()).isNull()
        } finally {
            db.close()
        }
    }
}
```

Check before continuing: in Room 2.8.0, the `MigrationTestHelper(Instrumentation, Class<out RoomDatabase>)` constructor, `createDatabase(String, Int)` and `runMigrationsAndValidate(String, Int, Boolean, vararg Migration)` must not be `@Deprecated`. Look for deprecation warnings in the next step's compile output. If any is deprecated, stop and ask.

- [ ] **Step 4: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarStoreTest*" --tests "*CalendarMigrationTest*"`
Expected: compilation FAILS with unresolved references `EventDraft`, `ChangeKind`, `PendingChange`, `EventRef`, `setMaster`, `MIGRATION_1_2` and the others.

- [ ] **Step 5: Add `EventDraft` to the contract**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`, insert after the `SyncResult` class:
```kotlin
/**
 * What the tablet asks a provider to write. [end] is exclusive, as in [RemoteEvent]. [forPerson] and [createdBy]
 * are household PersonId values ("family" allowed) that the provider stores with the event (Google:
 * extendedProperties.private). Names are never written.
 */
data class EventDraft(
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val forPerson: String?,
    val createdBy: String?,
)
```

- [ ] **Step 6: Extend the stored models**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Stored.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/** Who a source's events belong to when they carry no person tag, and whether the source is shown. */
data class SourceMapping(val person: PersonId, val visible: Boolean) {
    companion object {
        val Default = SourceMapping(PersonId.FAMILY, visible = true)
    }
}

data class StoredConnection(
    val connection: Connection,
    val health: ConnectionHealth,
    val lastSyncMillis: Long?,
)

/** [isMaster]: the household's master calendar, the only source the tablet writes to (spec §6). */
data class StoredSource(
    val connectionId: String,
    val source: CalendarSource,
    val mapping: SourceMapping,
    val isMaster: Boolean = false,
)

/** One mirrored event. Ids from providers may contain any character, including "/". */
data class EventRef(val connectionId: String, val sourceId: String, val remoteId: String) {
    /** A Bundle-safe lazy-list key. NUL never appears in a provider id, so it can't be mistaken for part of one. */
    val listKey: String get() = "$connectionId\u0000$sourceId\u0000$remoteId"
}

data class StoredEvent(
    val connectionId: String,
    val sourceId: String,
    val remoteId: String,
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val recurring: Boolean,
    val forPerson: String?,
    val createdBy: String?,
    val sourcePerson: PersonId,
    val startSort: Long,
    val endSort: Long,
) {
    val ref: EventRef get() = EventRef(connectionId, sourceId, remoteId)
}

/** ASSIGN changes only who an event is for: it is sent as the event is when it is sent, with the new person. */
enum class ChangeKind { CREATE, UPDATE, DELETE, ASSIGN }

/**
 * One queued write, kept until the provider accepts or refuses it. [remoteId] is null only for CREATE;
 * [draft] is null only for DELETE, and for ASSIGN only its forPerson counts. [id] is 0 until the store assigns one.
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
) {
    val ref: EventRef? get() = remoteId?.let { EventRef(connectionId, sourceId, it) }
}
```

- [ ] **Step 7: Bump the database to v2**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "connection")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val label: String,
    val configJson: String,
    /** OK, UNREACHABLE, NEEDS_SIGN_IN or ERROR. */
    val health: String,
    val healthMessage: String?,
    val lastSyncMillis: Long?,
)

@Entity(tableName = "source", primaryKeys = ["connectionId", "sourceId"])
data class SourceEntity(
    val connectionId: String,
    val sourceId: String,
    val name: String,
    val writable: Boolean,
    val visible: Boolean,
    /** A household PersonId value, or "family". */
    val personId: String,
    /** At most one row is 1 (CalendarStore.setMaster clears the others in the same transaction). */
    @ColumnInfo(defaultValue = "0") val isMaster: Boolean = false,
)

/** Timed events set the *Instant columns; all-day events set the *Date columns (ISO dates, end exclusive). */
@Entity(
    tableName = "event",
    primaryKeys = ["connectionId", "sourceId", "remoteId"],
    indices = [Index("startSort")],
)
data class EventEntity(
    val connectionId: String,
    val sourceId: String,
    val remoteId: String,
    val title: String,
    val startInstant: Long?,
    val startDate: String?,
    val endInstant: Long?,
    val endDate: String?,
    val recurring: Boolean,
    val forPerson: String?,
    val createdBy: String?,
    /** Epoch millis; for all-day events, midnight of the date in the household zone at sync time. */
    val startSort: Long,
    val endSort: Long,
)

@Entity(tableName = "sync_state", primaryKeys = ["connectionId", "sourceId"])
data class SyncStateEntity(
    val connectionId: String,
    val sourceId: String,
    val cursor: String?,
    /** The window the cursor belongs to, as "<ISO start date>|<zone id>", e.g. "2026-09-22|Europe/London". */
    val rangeStart: String,
)

/** Writes waiting for the provider. The event table stays a pure mirror of the provider. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val connectionId: String,
    val sourceId: String,
    /** Null for CREATE. */
    val remoteId: String?,
    /** CREATE, UPDATE, DELETE or ASSIGN. */
    val kind: String,
    /** The EventDraft as JSON; null for DELETE. */
    val draftJson: String?,
    val attempts: Int,
    val nextAttemptMillis: Long,
    val createdMillis: Long,
)

data class EventRow(
    @Embedded val event: EventEntity,
    val sourcePersonId: String,
)

@Dao
interface CalendarDao {
    @Query("SELECT * FROM connection ORDER BY label, id")
    fun connections(): Flow<List<ConnectionEntity>>

    @Query("SELECT id FROM connection ORDER BY id")
    fun connectionIds(): Flow<List<String>>

    @Query("SELECT * FROM connection ORDER BY label, id")
    suspend fun allConnections(): List<ConnectionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConnection(connection: ConnectionEntity)

    @Query("UPDATE connection SET health = :health, healthMessage = :message WHERE id = :id")
    suspend fun setHealth(id: String, health: String, message: String?)

    @Query("UPDATE connection SET health = 'OK', healthMessage = NULL, lastSyncMillis = :at WHERE id = :id")
    suspend fun markSynced(id: String, at: Long)

    @Query("SELECT * FROM source WHERE connectionId = :connectionId ORDER BY name")
    suspend fun sources(connectionId: String): List<SourceEntity>

    @Query("SELECT * FROM source ORDER BY connectionId, name")
    fun allSources(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM source WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun source(connectionId: String, sourceId: String): SourceEntity?

    @Query("SELECT * FROM source WHERE isMaster = 1 LIMIT 1")
    fun master(): Flow<SourceEntity?>

    @Query("UPDATE source SET isMaster = 0 WHERE isMaster = 1")
    suspend fun clearMaster()

    @Query("UPDATE source SET isMaster = 1, writable = 1 WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun markMaster(connectionId: String, sourceId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Query("DELETE FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun deleteEventsForSource(connectionId: String, sourceId: String)

    @Query("DELETE FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId AND remoteId IN (:ids)")
    suspend fun deleteEvents(connectionId: String, sourceId: String, ids: List<String>)

    @Upsert
    suspend fun upsertEvents(events: List<EventEntity>)

    @Query(
        """
        SELECT e.*, s.personId AS sourcePersonId FROM event e
        JOIN source s ON s.connectionId = e.connectionId AND s.sourceId = e.sourceId
        WHERE s.visible = 1 AND e.startSort < :end AND (e.endSort > :start OR e.startSort >= :start)
        ORDER BY e.startSort, e.title
        """,
    )
    fun eventsBetween(start: Long, end: Long): Flow<List<EventRow>>

    @Query(
        """
        SELECT e.*, s.personId AS sourcePersonId FROM event e
        JOIN source s ON s.connectionId = e.connectionId AND s.sourceId = e.sourceId
        WHERE e.connectionId = :connectionId AND e.sourceId = :sourceId AND e.remoteId = :remoteId
        """,
    )
    fun event(connectionId: String, sourceId: String, remoteId: String): Flow<EventRow?>

    @Query(
        """
        SELECT e.*, s.personId AS sourcePersonId FROM event e
        JOIN source s ON s.connectionId = e.connectionId AND s.sourceId = e.sourceId
        WHERE e.connectionId = :connectionId AND e.sourceId = :sourceId AND e.remoteId = :remoteId
        """,
    )
    suspend fun eventNow(connectionId: String, sourceId: String, remoteId: String): EventRow?

    @Query("SELECT * FROM sync_state WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun syncState(connectionId: String, sourceId: String): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Insert
    suspend fun insertOutbox(change: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY id")
    fun outbox(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox ORDER BY id")
    suspend fun outboxNow(): List<OutboxEntity>

    @Query("SELECT MIN(nextAttemptMillis) FROM outbox")
    suspend fun nextAttemptMillis(): Long?

    @Query("UPDATE outbox SET attempts = :attempts, nextAttemptMillis = :next WHERE id = :id")
    suspend fun rescheduleOutbox(id: Long, attempts: Int, next: Long)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteOutbox(id: Long)
}

/**
 * Holds user configuration (connections, source mappings, the master flag, queued writes), not just a cache:
 * every version bump ships a Migration (see Migrations.kt) with a MigrationTestHelper test, never a destructive
 * fallback. The schema JSON under schemas/ is committed.
 */
@Database(
    entities = [ConnectionEntity::class, SourceEntity::class, EventEntity::class, SyncStateEntity::class, OutboxEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
}
```

- [ ] **Step 8: Write the migration**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/Migrations.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2 (Plan 2b-1): the master-calendar flag and the outbox. The SQL must match schemas/…/2.json exactly. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `source` ADD COLUMN `isMaster` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`connectionId` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `remoteId` TEXT, `kind` TEXT NOT NULL, " +
                "`draftJson` TEXT, `attempts` INTEGER NOT NULL, `nextAttemptMillis` INTEGER NOT NULL, " +
                "`createdMillis` INTEGER NOT NULL)",
        )
    }
}
```

Check before continuing: `Migration.migrate(SupportSQLiteDatabase)` must not be `@Deprecated` in Room 2.8.0 (Room 2.7 added a `migrate(SQLiteConnection)` overload). If the compiler warns that it is deprecated, stop and ask.

- [ ] **Step 9: Add the store API**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.ConnectionEntity
import uk.co.siland.culvery.capability.calendar.db.EventEntity
import uk.co.siland.culvery.capability.calendar.db.EventRow
import uk.co.siland.culvery.capability.calendar.db.OutboxEntity
import uk.co.siland.culvery.capability.calendar.db.SourceEntity
import uk.co.siland.culvery.capability.calendar.db.SyncStateEntity
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/** The only writer of calendar.db. UI reads go through CalendarRepository. */
@Singleton
class CalendarStore @Inject constructor(private val db: CalendarDatabase) {
    private val dao = db.calendarDao()

    fun connections(): Flow<List<StoredConnection>> = dao.connections().map { rows -> rows.map { it.toStored() } }

    fun connectionIds(): Flow<List<String>> = dao.connectionIds().distinctUntilChanged()

    suspend fun connectionsNow(): List<StoredConnection> = dao.allConnections().map { it.toStored() }

    /**
     * One transaction, so the sync loop never sees a connection without its sources. A source missing from
     * [mapping] shows as Family. Source editing and pruning come with Plan 4's Connections settings.
     */
    suspend fun addConnection(connection: Connection, sources: List<CalendarSource>, mapping: Map<String, SourceMapping>) =
        db.withTransaction {
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
        }

    suspend fun visibleSourcesFor(connectionId: String): List<StoredSource> =
        dao.sources(connectionId).filter { it.visible }.map { it.toStored() }

    /** Every source of every connection, hidden ones included. */
    fun sources(): Flow<List<StoredSource>> = dao.allSources().map { rows -> rows.map { it.toStored() } }

    suspend fun source(connectionId: String, sourceId: String): StoredSource? = dao.source(connectionId, sourceId)?.toStored()

    fun master(): Flow<StoredSource?> = dao.master().map { it?.toStored() }

    /**
     * Makes this source the one master calendar, clearing any other, in one transaction. It also records the
     * source as writable: only CalendarSetup.setMaster calls this, after checking the provider says it is.
     */
    suspend fun setMaster(connectionId: String, sourceId: String) = db.withTransaction {
        dao.clearMaster()
        require(dao.markMaster(connectionId, sourceId) == 1) { "No source $sourceId in connection $connectionId" }
    }

    suspend fun setHealth(connectionId: String, health: ConnectionHealth) =
        dao.setHealth(connectionId, health.code(), (health as? ConnectionHealth.Error)?.message)

    suspend fun markSynced(connectionId: String, atMillis: Long) = dao.markSynced(connectionId, atMillis)

    /**
     * Null when nothing is stored or the cursor belongs to a different window or zone, forcing a full resync.
     * A zone change must resync: all-day events are stored at midnight in the zone they were synced in.
     */
    suspend fun cursor(connectionId: String, sourceId: String, range: DateRange): SyncCursor? =
        dao.syncState(connectionId, sourceId)
            ?.takeIf { it.rangeStart == range.cursorKey() }
            ?.cursor
            ?.let(::SyncCursor)

    /** Touches only the event mirror and the cursor; queued changes in the outbox are never affected. */
    suspend fun applySync(connectionId: String, sourceId: String, range: DateRange, result: SyncResult) =
        db.withTransaction {
            if (result.fullReplace) {
                dao.deleteEventsForSource(connectionId, sourceId)
            } else {
                result.removedIds.chunked(REMOVE_CHUNK).forEach { dao.deleteEvents(connectionId, sourceId, it) }
            }
            dao.upsertEvents(result.upserts.map { it.toEntity(connectionId, sourceId, range.zone) })
            dao.upsertSyncState(SyncStateEntity(connectionId, sourceId, result.cursor?.value, range.cursorKey()))
        }

    fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<StoredEvent>> =
        dao.eventsBetween(startMillis, endMillis).map { rows -> rows.map { it.toStored() } }

    fun event(ref: EventRef): Flow<StoredEvent?> =
        dao.event(ref.connectionId, ref.sourceId, ref.remoteId).map { it?.toStored() }

    suspend fun eventNow(ref: EventRef): StoredEvent? =
        dao.eventNow(ref.connectionId, ref.sourceId, ref.remoteId)?.toStored()

    /** Puts a write the provider accepted into the mirror and, if it came from the outbox, completes it. */
    suspend fun applyAccepted(connectionId: String, sourceId: String, event: RemoteEvent, zone: ZoneId, completing: Long? = null) =
        db.withTransaction {
            dao.upsertEvents(listOf(event.toEntity(connectionId, sourceId, zone)))
            completing?.let { dao.deleteOutbox(it) }
        }

    /** Removes an accepted delete from the mirror and, if it came from the outbox, completes it. */
    suspend fun applyDeleted(ref: EventRef, completing: Long? = null) = db.withTransaction {
        dao.deleteEvents(ref.connectionId, ref.sourceId, listOf(ref.remoteId))
        completing?.let { dao.deleteOutbox(it) }
    }

    suspend fun enqueue(change: PendingChange): Long = dao.insertOutbox(change.toEntity())

    /** Skips a row this version can't read; [pendingNow] deletes it. */
    fun pending(): Flow<List<PendingChange>> = dao.outbox().map { rows -> rows.mapNotNull { it.readOrNull() } }

    /** In queue order. A row this version can't read is logged and deleted, so one bad row can't stall the queue. */
    suspend fun pendingNow(): List<PendingChange> = dao.outboxNow().mapNotNull { row ->
        val change = row.readOrNull()
        if (change == null) dao.deleteOutbox(row.id)
        change
    }

    suspend fun nextAttemptMillis(): Long? = dao.nextAttemptMillis()

    suspend fun reschedule(id: Long, attempts: Int, nextAttemptMillis: Long) = dao.rescheduleOutbox(id, attempts, nextAttemptMillis)

    suspend fun dropChange(id: Long) = dao.deleteOutbox(id)

    private fun OutboxEntity.readOrNull(): PendingChange? =
        runCatching { toPending() }
            .onFailure { Log.w(TAG, "Dropping unreadable outbox row $id (kind $kind)", it) }
            .getOrNull()

    private companion object {
        const val TAG = "CalendarStore"

        // SQLite allows 999 bound variables per statement on older Android versions.
        const val REMOVE_CHUNK = 500
    }
}

private fun DateRange.cursorKey(): String = "$start|${zone.id}"

internal fun ConnectionHealth.code(): String = when (this) {
    ConnectionHealth.Ok -> "OK"
    ConnectionHealth.Unreachable -> "UNREACHABLE"
    ConnectionHealth.NeedsSignIn -> "NEEDS_SIGN_IN"
    is ConnectionHealth.Error -> "ERROR"
}

/** An unknown code (a newer app's value, or corruption) must not read as healthy. */
internal fun healthOf(code: String, message: String?): ConnectionHealth = when (code) {
    "OK" -> ConnectionHealth.Ok
    "UNREACHABLE" -> ConnectionHealth.Unreachable
    "NEEDS_SIGN_IN" -> ConnectionHealth.NeedsSignIn
    "ERROR" -> ConnectionHealth.Error(message ?: "Unknown error")
    else -> ConnectionHealth.Error("Unknown health code $code")
}

private fun encodeConfig(config: Map<String, String>): String = JSONObject(config).toString()

private fun decodeConfig(json: String): Map<String, String> {
    val o = JSONObject(json)
    return o.keys().asSequence().associateWith { o.getString(it) }
}

private fun encodeTime(t: EventTime): JSONObject = when (t) {
    is EventTime.Timed -> JSONObject().put("instant", t.instant.toEpochMilli())
    is EventTime.AllDay -> JSONObject().put("date", t.date.toString())
}

private fun decodeTime(o: JSONObject): EventTime =
    if (o.has("instant")) EventTime.Timed(Instant.ofEpochMilli(o.getLong("instant"))) else EventTime.AllDay(LocalDate.parse(o.getString("date")))

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

private fun encodeDraft(d: EventDraft): String = JSONObject()
    .put("title", d.title)
    .put("start", encodeTime(d.start))
    .put("end", encodeTime(d.end))
    .put("forPerson", d.forPerson ?: JSONObject.NULL)
    .put("createdBy", d.createdBy ?: JSONObject.NULL)
    .toString()

private fun decodeDraft(json: String): EventDraft {
    val o = JSONObject(json)
    return EventDraft(
        title = o.getString("title"),
        start = decodeTime(o.getJSONObject("start")),
        end = decodeTime(o.getJSONObject("end")),
        forPerson = o.stringOrNull("forPerson"),
        createdBy = o.stringOrNull("createdBy"),
    )
}

private fun ConnectionEntity.toStored() = StoredConnection(
    Connection(id, providerId, label, decodeConfig(configJson)),
    healthOf(health, healthMessage),
    lastSyncMillis,
)

private fun SourceEntity.toStored() = StoredSource(
    connectionId,
    CalendarSource(sourceId, name, writable),
    SourceMapping(PersonId(personId), visible),
    isMaster,
)

private fun RemoteEvent.toEntity(connectionId: String, sourceId: String, zone: ZoneId) = EventEntity(
    connectionId = connectionId,
    sourceId = sourceId,
    remoteId = remoteId,
    title = title,
    startInstant = (start as? EventTime.Timed)?.instant?.toEpochMilli(),
    startDate = (start as? EventTime.AllDay)?.date?.toString(),
    endInstant = (end as? EventTime.Timed)?.instant?.toEpochMilli(),
    endDate = (end as? EventTime.AllDay)?.date?.toString(),
    recurring = recurring,
    forPerson = forPerson,
    createdBy = createdBy,
    startSort = start.instantIn(zone).toEpochMilli(),
    endSort = end.instantIn(zone).toEpochMilli(),
)

private fun timeOf(instant: Long?, date: String?): EventTime =
    if (instant != null) EventTime.Timed(Instant.ofEpochMilli(instant)) else EventTime.AllDay(LocalDate.parse(requireNotNull(date)))

private fun EventRow.toStored() = StoredEvent(
    connectionId = event.connectionId,
    sourceId = event.sourceId,
    remoteId = event.remoteId,
    title = event.title,
    start = timeOf(event.startInstant, event.startDate),
    end = timeOf(event.endInstant, event.endDate),
    recurring = event.recurring,
    forPerson = event.forPerson,
    createdBy = event.createdBy,
    sourcePerson = PersonId(sourcePersonId),
    startSort = event.startSort,
    endSort = event.endSort,
)

private fun PendingChange.toEntity() = OutboxEntity(
    id = id,
    connectionId = connectionId,
    sourceId = sourceId,
    remoteId = remoteId,
    kind = kind.name,
    draftJson = draft?.let(::encodeDraft),
    attempts = attempts,
    nextAttemptMillis = nextAttemptMillis,
    createdMillis = createdMillis,
)

private fun OutboxEntity.toPending() = PendingChange(
    id = id,
    connectionId = connectionId,
    sourceId = sourceId,
    remoteId = remoteId,
    kind = ChangeKind.valueOf(kind),
    draft = draftJson?.let(::decodeDraft),
    attempts = attempts,
    nextAttemptMillis = nextAttemptMillis,
    createdMillis = createdMillis,
)
```

- [ ] **Step 10: Register the migration**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`:
- add `import uk.co.siland.culvery.capability.calendar.db.MIGRATION_1_2`;
- replace the `database` provider's body with:
```kotlin
        fun database(@ApplicationContext context: Context): CalendarDatabase =
            Room.databaseBuilder(context, CalendarDatabase::class.java, "calendar.db")
                .addMigrations(MIGRATION_1_2)
                .build()
```

- [ ] **Step 11: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarStoreTest*" --tests "*CalendarMigrationTest*"`
Expected: PASS. The build writes `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/2.json`.

**If `CalendarMigrationTest` fails with `FileNotFoundException` for `…CalendarDatabase/1.json`,** Robolectric isn't seeing the debug assets. Stop and ask; don't move the schemas into `src/main`.

**If `runMigrationsAndValidate` reports a schema mismatch,** open `2.json`. Copy the `createSql` for `outbox` (with `${TABLE_NAME}` replaced by `outbox`) and the `isMaster` field (`"defaultValue": "0"`) into `MIGRATION_1_2` exactly, then run again.

- [ ] **Step 12: Check the generated schema**

Open `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/2.json`. Confirm:
- `"version": 2`;
- the `source` table has an `isMaster` field with `"notNull": true` and `"defaultValue": "0"`;
- the `outbox` `createSql` matches the second statement in `MIGRATION_1_2` word for word.

`1.json` must be unchanged: `git diff --stat capability/calendar/schemas` shows only `2.json` as new.

- [ ] **Step 13: Run the module and the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. The new tests pass, and the existing store, sync, repository and screenshot tests still pass unchanged.

- [ ] **Step 14: Commit**

```bash
git add gradle/libs.versions.toml capability/calendar
git commit -m "Add calendar.db v2 with the master flag and outbox, and its migration test"
```

---

### Task 2: Shell — overlay host, toaster, status-bar sign-in, no rail chip

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Overlays.kt`
- Create: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Shell.kt`
- Create: `app/src/main/java/uk/co/siland/culvery/shell/ShellOverlays.kt`
- Create: `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellUiState.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/CulveryShell.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/NavRail.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/StatusBar.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/MainActivity.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ShellToastsTest.kt` (create)
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/OverlayLayersTest.kt` (create)
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt` (modify)
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt` (modify)
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt` (modify)
- Re-record: `app/src/test/screenshots/home_session_dark.png`; record `home_toast_dark.png`

**Interfaces:**
- Consumes (existing):
  - `AccessControl.session: StateFlow<Identified?>`, `Identified(person: Person, role: Role)`, `AccessControl.lock()`
  - `CulveryTheme`, `Culvery.colors`, `HhType`, `HhIcon(name, size, filled, tint, modifier, contentDescription)`
- Produces:
  - `:core:plugin`:
    - `interface OverlayHost { fun show(content: @Composable () -> Unit); fun dismiss() }` and `val LocalOverlayHost`
    - `interface Toaster { fun show(message: String, icon: String = TOAST_ICON_INFO) }` (injected only; no composition local), `const val TOAST_ICON_INFO = "info"`
  - `:core:ui`:
    - `object ShellTokens`: `sheetScrim`, `pinScrim`, `sheetWidth`, `sheetBorder`, `sheetGap`, `closeButton`, `closeIcon`, the toast and status values, `TOAST_MILLIS = 3_500L`
    - `object ShellType` (`toast`, `signOut`)
    - `@Composable fun HhSheet(padding: PaddingValues, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`
    - `@Composable fun HhCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier)`, tagged `sheet_close`
    - `@Composable fun HhToast(message: String, icon: String, modifier: Modifier = Modifier)`, tagged `toast`
  - `:app`:
    - `class OverlayState : OverlayHost`, with `val isShowing: Boolean` and `val content`
    - `@Singleton class ShellToasts @Inject constructor() : Toaster`, with `val current: StateFlow<ToastMessage?>` and `fun hide(id: Long)`
    - `data class ToastMessage(id: Long, message: String, icon: String)`
    - `@Composable fun OverlayLayer(state: OverlayState)`, with its scrim tagged `overlay_scrim`
    - `@Composable fun ToastLayer(toast: ToastMessage?, onHidden: (Long) -> Unit)`
    - `data class SessionUi(name: String, role: String)`, which replaces `SessionChip`; `ShellUiState.session: SessionUi?`
    - `ShellViewModel.signOut()`, which replaces `lockSession()`; `internal fun roleLabel(role: Role): String`
    - `CulveryShell(state, onSelectTab, onOpenSettings, onSignOut, onToggleThemePreview, tabContent)`
    - `NavRail(tabs, selectedId, onSelect, onOpenSettings)`
    - `StatusBar(now, dark, previewing, session, onSignOut, onToggleThemePreview)`, with tags `status_session` and `status_sign_out`
    - Hilt binding `Toaster` → `ShellToasts`

- [ ] **Step 1: Write the failing toaster test**

`app/src/test/java/uk/co/siland/culvery/shell/ShellToastsTest.kt`:
```kotlin
package uk.co.siland.culvery.shell

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShellToastsTest {
    private val toasts = ShellToasts()

    @Test
    fun aNewToastReplacesTheCurrentOne() {
        toasts.show("First")
        toasts.show("Second", "delete")
        assertThat(toasts.current.value?.let { it.message to it.icon }).isEqualTo("Second" to "delete")
    }

    @Test
    fun theDefaultIconIsInfo() {
        toasts.show("Event deleted")
        assertThat(toasts.current.value?.icon).isEqualTo("info")
    }

    @Test
    fun hidingAnOlderToastKeepsTheNewerOne() {
        toasts.show("First")
        val first = toasts.current.value!!.id
        toasts.show("Second")
        toasts.hide(first)
        assertThat(toasts.current.value?.message).isEqualTo("Second")
        toasts.hide(toasts.current.value!!.id)
        assertThat(toasts.current.value).isNull()
    }
}
```

- [ ] **Step 2: Write the failing overlay and toast layer tests**

`app/src/test/java/uk/co/siland/culvery/shell/ui/OverlayLayersTest.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage

@RunWith(AndroidJUnit4::class)
class OverlayLayersTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()

    private fun showLayer() = compose.setContent { CulveryTheme(dark = true) { OverlayLayer(overlay) } }

    @Test
    fun nothingIsDrawnUntilSomethingIsShown() {
        showLayer()
        compose.onNodeWithTag("overlay_scrim").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
    }

    @Test
    fun tappingTheScrimDismissesTheContent() {
        showLayer()
        compose.runOnIdle { overlay.show { Text("Sheet body") } }
        compose.onNodeWithText("Sheet body").assertExists()
        compose.onNodeWithTag("overlay_scrim").performClick()
        compose.onNodeWithText("Sheet body").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
    }

    @Test
    fun tapsOnTheSheetDoNotDismissIt() {
        showLayer()
        compose.runOnIdle { overlay.show { HhSheet(padding = PaddingValues(0.dp)) { Text("Sheet body") } } }
        compose.onNodeWithText("Sheet body").performClick()
        assertThat(overlay.isShowing).isTrue()
    }

    @Test
    fun showReplacesTheCurrentContentAndDismissHidesIt() {
        showLayer()
        compose.runOnIdle { overlay.show { Text("First") } }
        compose.runOnIdle { overlay.show { Text("Second") } }
        compose.onNodeWithText("First").assertDoesNotExist()
        compose.onNodeWithText("Second").assertExists()
        compose.runOnIdle { overlay.dismiss() }
        compose.onNodeWithText("Second").assertDoesNotExist()
    }

    @Test
    fun toastHidesAfterThreeAndAHalfSeconds() {
        var toast by mutableStateOf<ToastMessage?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) { ToastLayer(toast) { id -> if (toast?.id == id) toast = null } }
        }
        toast = ToastMessage(1, "Event deleted", "info")
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Event deleted").assertExists()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("Event deleted").assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Event deleted").assertDoesNotExist()
    }

    @Test
    fun aNewToastRestartsTheTimer() {
        var toast by mutableStateOf<ToastMessage?>(ToastMessage(1, "First", "info"))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) { ToastLayer(toast) { id -> if (toast?.id == id) toast = null } }
        }
        compose.mainClock.advanceTimeBy(3_000)
        toast = ToastMessage(2, "Second", "info")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Second").assertExists()
        compose.onNodeWithText("First").assertDoesNotExist()
        // The second toast's own 3.5 s runs from about 3.0 s: still showing at 6.3 s, gone by 6.6 s.
        compose.mainClock.advanceTimeBy(2_300)
        compose.onNodeWithText("Second").assertExists()
        compose.mainClock.advanceTimeBy(300)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Second").assertDoesNotExist()
    }
}
```

- [ ] **Step 3: Replace the rail-chip layout tests with status-bar tests**

Replace `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt` with:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.SessionUi

/** Needs real text metrics: legacy Robolectric graphics fakes glyph widths and font ascents. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val at = LocalDateTime.of(2026, 9, 23, 11, 54)

    @Test
    fun dateBaselineSits44dpBelowClockBaseline() {
        compose.setContent {
            CulveryTheme(dark = true) { HomeScreen(at, emptyList()) }
        }
        val clock = compose.onNodeWithTag("home_clock")
        val date = compose.onNodeWithTag("home_date")
        val clockBaseline = clock.getUnclippedBoundsInRoot().top + clock.getAlignmentLinePosition(LastBaseline)
        val dateBaseline = date.getUnclippedBoundsInRoot().top + date.getAlignmentLinePosition(FirstBaseline)
        assertThat((dateBaseline - clockBaseline).value).isWithin(1f).of(44f)
    }

    private fun statusBar(session: SessionUi?, onSignOut: () -> Unit = {}) = compose.setContent {
        CulveryTheme(dark = true) { StatusBar(at, dark = true, previewing = false, session = session, onSignOut = onSignOut, onToggleThemePreview = {}) }
    }

    @Test
    fun statusBarShowsWhoIsSignedInAndTheirRole() {
        statusBar(SessionUi("Alex", "Admin"))
        compose.onNodeWithText("Alex · Admin").assertExists()
        compose.onNodeWithText("Sign out").assertExists()
    }

    @Test
    fun signOutLocks() {
        var signedOut = false
        statusBar(SessionUi("Alex", "Admin")) { signedOut = true }
        compose.onNodeWithTag("status_sign_out").performClick()
        assertThat(signedOut).isTrue()
    }

    @Test
    fun signOutAnnouncesItsAction() {
        statusBar(SessionUi("Alex", "Admin"))
        val onClick = compose.onNodeWithTag("status_sign_out").fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertThat(onClick.label).isEqualTo("Sign out")
    }

    @Test
    fun nothingAboutSigningInShowsWhenLocked() {
        statusBar(null)
        compose.onNodeWithTag("status_session").assertDoesNotExist()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }

    @Test
    fun theRailHasNoSessionChip() {
        compose.setContent { CulveryTheme(dark = true) { NavRail(emptyList(), HOME_TAB_ID, {}, {}) } }
        compose.onNodeWithTag("rail_session").assertDoesNotExist()
        compose.onNodeWithTag("rail_home").assertExists()
    }
}
```

- [ ] **Step 4: Update the view-model tests for the status-bar session**

In `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt`:
- Replace the test `sessionShowsAsChip` with:
```kotlin
    @Test
    fun sessionShowsNameAndRoleForTheStatusBar() = runTest {
        val vm = vm()
        vm.uiState.test {
            access.session.value = Identified(alex, Role.ADMIN)
            assertThat(expectMostRecentItem().session).isEqualTo(SessionUi("Alex", "Admin"))
            vm.signOut()
            assertThat(expectMostRecentItem().session).isNull()
        }
    }

    @Test
    fun roleLabelsReadAsWords() {
        assertThat(Role.entries.map(::roleLabel)).containsExactly("Admin", "Adult", "Child").inOrder()
    }
```
- In `capabilityThatNeverEmitsDoesNotBlockShell`, replace `SessionChip("Alex", 0xFF4CB387)` with `SessionUi("Alex", "Admin")`.

- [ ] **Step 5: Run the app tests to see them fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: compilation FAILS with unresolved references: `ShellToasts`, `ToastMessage`, `OverlayState`, `OverlayLayer`, `ToastLayer`, `HhSheet`, `SessionUi`, `signOut` and `roleLabel`, and `StatusBar` called with the wrong parameters.

- [ ] **Step 6: Add the shell contracts to `:core:plugin`**

`core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Overlays.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets capability UI show a sheet above the rail and content without depending on :app. The shell draws a scrim
 * that dismisses on tap, and places [show]'s content against the right edge, full height.
 */
interface OverlayHost {
    /** Replaces anything already shown. */
    fun show(content: @Composable () -> Unit)

    fun dismiss()
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost> {
    error("LocalOverlayHost not provided: wrap the content in CompositionLocalProvider(LocalOverlayHost provides …)")
}

/** The hand-off's toast icon. */
const val TOAST_ICON_INFO = "info"

/**
 * A short bottom-centre message (hand-off §7). Injected wherever a toast is shown (access control, the calendar
 * editor, the outbox drain), so a toast never depends on a composable still being on screen. Safe from any
 * thread. A new toast replaces the current one.
 */
interface Toaster {
    fun show(message: String, icon: String = TOAST_ICON_INFO)
}
```

- [ ] **Step 7: Add the shell tokens and components to `:core:ui`**

`core/ui/src/main/java/uk/co/siland/culvery/core/ui/Shell.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shell component values from the hand-off (§6 Holiday sheet, §7 Calendar sheets and toast). */
object ShellTokens {
    /** rgba(0,0,0,.55) behind sheets. The same in both themes, so not an HhColors token. */
    val sheetScrim = Color(0x8C000000)

    /** rgba(0,0,0,.5) behind the PIN pad. The same in both themes. */
    val pinScrim = Color(0x80000000)

    // Right-side sheet: 600 dp, full height, bg, 1 dp left border `line`, 16 dp between blocks, 48 dp close button.
    val sheetWidth = 600.dp
    val sheetBorder = 1.dp
    val sheetGap = 16.dp
    val closeButton = 48.dp
    val closeIcon = 26.dp

    // Toast: bottom-centre pill 28 dp above the bottom, padding 14×22, radius 26, `info` icon, 3.5 s.
    val toastBottom = 28.dp
    val toastPaddingV = 14.dp
    val toastPaddingH = 22.dp
    val toastRadius = 26.dp
    val toastIcon = 22.dp
    val toastIconGap = 10.dp
    val toastMaxWidth = 720.dp
    const val TOAST_MILLIS = 3_500L

    // Status-bar sign-in: account_circle 16 dp, 6 dp between items, 16 dp before the theme indicator.
    val statusIcon = 16.dp
    val statusGap = 6.dp
    val statusGroupGap = 16.dp
    val statusSignOutPaddingH = 10.dp
    val statusSignOutPaddingV = 6.dp
}

/** Shell text styles, derived from HhType. */
object ShellType {
    /** 16 sp / 600: toast text. */
    val toast = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 12 sp / 700: the status bar's "Sign out". */
    val signOut = HhType.labelSmall
}

/**
 * The hand-off's right-side sheet: 600 dp, full height, `bg`, 1 dp `line` left border, 16 dp between blocks.
 * Taps on empty sheet space are swallowed so they don't reach the scrim underneath.
 */
@Composable
fun HhSheet(padding: PaddingValues, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
        modifier = modifier
            .fillMaxHeight()
            .width(ShellTokens.sheetWidth)
            .background(c.bg)
            .drawBehind {
                val x = ShellTokens.sheetBorder.toPx() / 2
                drawLine(c.line, Offset(x, 0f), Offset(x, size.height), ShellTokens.sheetBorder.toPx())
            }
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(padding),
        content = content,
    )
}

/** 48 dp round `surf2` close button with a 26 dp `close` icon. */
@Composable
fun HhCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("sheet_close")
            .size(ShellTokens.closeButton)
            .clip(CircleShape)
            .background(c.surf2)
            .clickable(onClickLabel = "Close", onClick = onClick),
    ) {
        HhIcon("close", size = ShellTokens.closeIcon, tint = c.ink, contentDescription = "Close")
    }
}

/** The toast pill: `ink` background, `bg` text, 16 sp / 600, padding 14×22, radius 26. */
@Composable
fun HhToast(message: String, icon: String, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShellTokens.toastIconGap),
        modifier = modifier
            .testTag("toast")
            .widthIn(max = ShellTokens.toastMaxWidth)
            .clip(RoundedCornerShape(ShellTokens.toastRadius))
            .background(c.ink)
            .padding(horizontal = ShellTokens.toastPaddingH, vertical = ShellTokens.toastPaddingV),
    ) {
        HhIcon(icon, size = ShellTokens.toastIcon, tint = c.bg)
        Text(message, style = ShellType.toast, color = c.bg)
    }
}
```

- [ ] **Step 8: Add the overlay and toast state to `:app`**

`app/src/main/java/uk/co/siland/culvery/shell/ShellOverlays.kt`:
```kotlin
package uk.co.siland.culvery.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.Toaster

/** The one sheet the shell shows above the rail and content; remembered by MainActivity. */
class OverlayState : OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    val isShowing: Boolean get() = content != null

    override fun show(content: @Composable () -> Unit) {
        this.content = content
    }

    override fun dismiss() {
        content = null
    }
}

data class ToastMessage(val id: Long, val message: String, val icon: String)

@Singleton
class ShellToasts @Inject constructor() : Toaster {
    private val ids = AtomicLong()
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    override fun show(message: String, icon: String) {
        _current.value = ToastMessage(ids.incrementAndGet(), message, icon)
    }

    /** Hides [id] if it is still the one showing, so an older toast's timer can't hide a newer toast. */
    fun hide(id: Long) {
        _current.update { if (it?.id == id) null else it }
    }
}
```

`app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import uk.co.siland.culvery.core.ui.HhToast
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage

/** The sheet layer: a scrim over the whole shell that dismisses on tap, and the content against the right edge. */
@Composable
fun OverlayLayer(state: OverlayState) {
    val content = state.content ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("overlay_scrim")
                .background(ShellTokens.sheetScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Close",
                    onClick = state::dismiss,
                ),
        )
        content()
    }
}

/** Draws [toast] bottom-centre and reports it hidden after 3.5 s; a new toast restarts the timer. */
@Composable
fun ToastLayer(toast: ToastMessage?, onHidden: (Long) -> Unit) {
    val t = toast ?: return
    LaunchedEffect(t.id) {
        delay(ShellTokens.TOAST_MILLIS)
        onHidden(t.id)
    }
    Box(Modifier.fillMaxSize().padding(bottom = ShellTokens.toastBottom), contentAlignment = Alignment.BottomCenter) {
        HhToast(t.message, t.icon)
    }
}
```

- [ ] **Step 9: Move the session to the status bar**

Replace `app/src/main/java/uk/co/siland/culvery/shell/ShellUiState.kt` with:
```kotlin
package uk.co.siland.culvery.shell

import java.time.LocalDateTime
import uk.co.siland.culvery.core.plugin.HomePlacement

const val HOME_TAB_ID = "home"

data class TabItem(val id: String, val label: String, val icon: String)

/** The status bar's "{name} · {role}". */
data class SessionUi(val name: String, val role: String)

data class ShellUiState(
    val tabs: List<TabItem> = emptyList(),
    val selectedTabId: String = HOME_TAB_ID,
    val session: SessionUi? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val dark: Boolean = true,
    val previewing: Boolean = false,
    val homeCards: List<HomePlacement> = emptyList(),
    val settingsOpen: Boolean = false,
)
```

In `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`:
- add `import uk.co.siland.culvery.core.household.Role`;
- replace `session = session?.let { SessionChip(it.person.name, it.person.color) },` with `session = session?.let { SessionUi(it.person.name, roleLabel(it.role)) },`;
- rename `fun lockSession() = access.lock()` to `fun signOut() = access.lock()`;
- add at the end of the file:
```kotlin
internal fun roleLabel(role: Role): String = when (role) {
    Role.ADMIN -> "Admin"
    Role.ADULT -> "Adult"
    Role.CHILD -> "Child"
}
```

Replace `app/src/main/java/uk/co/siland/culvery/shell/ui/StatusBar.kt` with:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.ShellType
import uk.co.siland.culvery.shell.SessionUi

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/** Hand-off status bar, plus §7's signed-in indicator: `account_circle` · "{name} · {role}" · "Sign out". */
@Composable
fun StatusBar(
    now: LocalDateTime,
    dark: Boolean,
    previewing: Boolean,
    session: SessionUi?,
    onSignOut: () -> Unit,
    onToggleThemePreview: () -> Unit,
) {
    val c = Culvery.colors
    val label = when {
        !previewing -> "Auto"
        dark -> "Night"
        else -> "Day"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 20.dp),
    ) {
        Text(now.format(TIME), style = HhType.status, color = c.mute)
        Spacer(Modifier.weight(1f))
        if (session != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShellTokens.statusGap),
                modifier = Modifier.testTag("status_session"),
            ) {
                HhIcon("account_circle", size = ShellTokens.statusIcon, tint = c.mute)
                Text("${session.name} · ${session.role}", style = HhType.status, color = c.mute, maxLines = 1)
                Text(
                    "Sign out",
                    style = ShellType.signOut,
                    color = c.accent,
                    modifier = Modifier
                        .testTag("status_sign_out")
                        .clickable(onClickLabel = "Sign out", onClick = onSignOut)
                        .padding(horizontal = ShellTokens.statusSignOutPaddingH, vertical = ShellTokens.statusSignOutPaddingV),
                )
            }
            Spacer(Modifier.width(ShellTokens.statusGroupGap))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("status_theme").clickable(onClick = onToggleThemePreview).padding(4.dp),
        ) {
            HhIcon(if (dark) "dark_mode" else "light_mode", size = 16.dp, tint = c.mute)
            Text(label, style = HhType.status.copy(fontSize = 12.sp), color = c.mute)
        }
    }
}
```
The Plan 1 status-bar numbers (30 dp, 20 dp, 4 dp, 16 dp, 12 sp) are left as they are. Only the new sign-in values go in `ShellTokens`.

Replace `app/src/main/java/uk/co/siland/culvery/shell/ui/NavRail.kt` with:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.TabItem

private val HomeTab = TabItem(HOME_TAB_ID, "Home", "home")

/** Who is signed in now shows in the status bar (hand-off §7), not here. */
@Composable
fun NavRail(
    tabs: List<TabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = Culvery.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxHeight()
            .width(108.dp)
            .drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(c.line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
            .padding(top = 16.dp, bottom = 20.dp),
    ) {
        (listOf(HomeTab) + tabs).forEach { tab ->
            RailItem(tab, selected = tab.id == selectedId) { onSelect(tab.id) }
        }
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            modifier = Modifier
                .testTag("rail_settings")
                .size(80.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.surf2)
                .clickable(onClick = onOpenSettings),
        ) {
            HhIcon("settings", size = 30.dp, tint = c.ink)
            Text("Settings", style = HhType.labelSmall, color = c.ink)
        }
    }
}

@Composable
private fun RailItem(tab: TabItem, selected: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("rail_${tab.id}")
            .width(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 64.dp, height = 38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(if (selected) c.accentSoft else Color.Transparent),
        ) {
            HhIcon(tab.icon, size = 26.dp, filled = selected, tint = if (selected) c.ink else c.mute)
        }
        Spacer(Modifier.height(6.dp))
        Text(tab.label, style = HhType.label, color = if (selected) c.ink else c.mute)
    }
}
```

Replace `app/src/main/java/uk/co/siland/culvery/shell/ui/CulveryShell.kt` with:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.ShellUiState

@Composable
fun CulveryShell(
    state: ShellUiState,
    onSelectTab: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onToggleThemePreview: () -> Unit,
    tabContent: @Composable (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
        StatusBar(state.now, state.dark, state.previewing, state.session, onSignOut, onToggleThemePreview)
        Row(Modifier.weight(1f)) {
            NavRail(state.tabs, state.selectedTabId, onSelectTab, onOpenSettings)
            Box(Modifier.weight(1f).padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 22.dp)) {
                if (state.selectedTabId == HOME_TAB_ID) {
                    HomeScreen(state.now, state.homeCards)
                } else {
                    tabContent(state.selectedTabId)
                }
            }
        }
    }
}
```

- [ ] **Step 10: Wire the layers and the toaster in the activity**

In `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`:
- add imports `dagger.Binds`, `uk.co.siland.culvery.core.plugin.Toaster`, `uk.co.siland.culvery.shell.ShellToasts`;
- add inside the abstract class, before `companion object`:
```kotlin
    @Binds
    abstract fun toaster(impl: ShellToasts): Toaster
```

Replace `app/src/main/java/uk/co/siland/culvery/MainActivity.kt` with:
```kotlin
package uk.co.siland.culvery

import android.os.Bundle
import android.view.MotionEvent
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
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.ui.PinPadHost
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ShellToasts
import uk.co.siland.culvery.shell.ShellViewModel
import uk.co.siland.culvery.shell.ui.CulveryShell
import uk.co.siland.culvery.shell.ui.OverlayLayer
import uk.co.siland.culvery.shell.ui.SettingsPlaceholder
import uk.co.siland.culvery.shell.ui.ToastLayer

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    @Inject lateinit var pinPrompt: PinPromptController
    @Inject lateinit var capabilities: Set<@JvmSuppressWildcards Capability>
    @Inject lateinit var toasts: ShellToasts

    // Set by Settings › Exit kiosk; cleared when the app comes back to the foreground.
    private var kioskExited = false

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
        setContent {
            val state by shell.uiState.collectAsStateWithLifecycle()
            val toast by toasts.current.collectAsStateWithLifecycle()
            val overlay = remember { OverlayState() }
            CompositionLocalProvider(
                LocalShellNavigator provides shell,
                LocalOverlayHost provides overlay,
            ) {
                CulveryTheme(dark = state.dark) {
                    CulveryShell(
                        state = state,
                        onSelectTab = shell::selectTab,
                        onOpenSettings = shell::openSettings,
                        onSignOut = shell::signOut,
                        onToggleThemePreview = shell::toggleThemePreview,
                        tabContent = { id -> capabilities.firstOrNull { it.id == id }?.TabContent() },
                    )
                    if (state.settingsOpen) {
                        SettingsPlaceholder(onExitKiosk = shell::exitKiosk, onClose = shell::closeSettings)
                    }
                    // Layer order: sheet, then the PIN pad over it, then toasts over everything.
                    OverlayLayer(overlay)
                    PinPadHost(pinPrompt)
                    ToastLayer(toast, toasts::hide)
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
        if (!kioskExited) {
            hideSystemBars()
            pinToScreen()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !kioskExited) hideSystemBars()
    }

    // Every touch-down anywhere (shell, sheet, PIN pad) keeps the PIN session alive.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) shell.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }
}
```

- [ ] **Step 11: Update the shell screenshot test**

In `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`:
- replace `import uk.co.siland.culvery.shell.SessionChip` with `import uk.co.siland.culvery.shell.SessionUi`, and add `import uk.co.siland.culvery.shell.ToastMessage`;
- in `snap`, rename the argument `onLockSession = {},` to `onSignOut = {},`;
- in `homeWithSessionDark`, replace `session = SessionChip("Admin", 0xFF4CB387)` with `session = SessionUi("Alex", "Admin")`;
- add after `homeWithSessionDark`:
```kotlin
    @Test
    fun toastDark() {
        // The toast hides itself after 3.5 s; hold the clock so it is still there for the capture.
        compose.mainClock.autoAdvance = false
        snap("home_toast_dark", dark = true) {
            ToastLayer(ToastMessage(1, "Mia can only change events they created.", "info")) {}
        }
    }
```

- [ ] **Step 12: Run the app tests to see them pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 13: Check which screenshots changed, then record**

Run: `./gradlew :app:verifyRoborazziDebug`
Expected: `home_session_dark` fails, because the session moved from the rail to the status bar, and `home_toast_dark` has no baseline. Every other image matches. If any other image differs, run `./gradlew :app:compareRoborazziDebug` and look at the `*_compare.png` under `app/build/outputs/roborazzi/` before going further. Do not re-record images you did not expect to change.

Run: `./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest.homeWithSessionDark" --tests "*ShellScreenshotTest.toastDark"`

Open both images:
- `home_session_dark.png`:
  - The rail has no chip; Home is the first rail item.
  - The status bar's right side reads, left to right: a muted `account_circle` icon, "Alex · Admin" at 13 sp muted, a green "Sign out" at 12 sp bold, a gap, then the theme indicator.
- `home_toast_dark.png`:
  - A light pill, bottom-centre, 28 dp above the bottom edge. It has a dark `info` icon and the text "Mia can only change events they created." at 16 sp semibold.

- [ ] **Step 14: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 15: Commit**

```bash
git add core/plugin core/ui app
git commit -m "Add the sheet overlay and toasts to the shell and show the session in the status bar"
```

---

### Task 3: Access — 2-minute session, reason, allow, refusal toasts

**Files:**
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/AccessControl.kt`
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/DefaultAccessControl.kt`
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/PinPromptController.kt`
- Create: `core/access/src/test/java/uk/co/siland/culvery/core/access/RecordingToaster.kt`
- Test: `core/access/src/test/java/uk/co/siland/culvery/core/access/DefaultAccessControlTest.kt` (modify)
- Modify: `app/src/main/java/uk/co/siland/culvery/MainActivity.kt` (remove the touch hook)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt` (remove `onUserActivity`)
- Modify: `app/src/test/java/uk/co/siland/culvery/shell/Fakes.kt` (`FakeAccessControl` signature, no `touch`)
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt` (remove `userActivityTouchesSession`)

**Interfaces:**
- Consumes:
  - `Toaster` (Task 2)
  - existing `PermissionRegistry`, `PinManager`, `LockoutStore`, `PinPromptController`, `WallClock`, `@ApplicationScope`
- Produces:
  - `const val SESSION_TIMEOUT_MS = 120_000L`, counted from the last successful `authorise` (a PIN-gated action), never from a touch
  - `AccessControl.touch()` is **removed**, with `ShellViewModel.onUserActivity()` and `MainActivity.dispatchTouchEvent`
  - A `Refusal.Toast` refusal on the session shortcut shows the toast, then `lock()`s
  - `enum class PinReason { Generic, Save, Edit, Delete, Assign }`
  - `fun pinReasonText(reason: PinReason, label: String): String`
  - `sealed interface Refusal { data object InPad; class Toast(val message: (name: String) -> String) }`
  - `AccessControl.authorise(vararg anyOf: String, reason: PinReason = PinReason.Generic, allow: (Identified, granted: Set<String>) -> Boolean = { _, g -> g.isNotEmpty() }, refusal: Refusal = Refusal.InPad): Authorised?`
  - `PinError.NotAllowed(name: String, message: String = "$name can't do that")`
  - `PinRequest.reason: PinReason`
  - `PinPromptController.open(label, error, lockedUntilMillis, reason: PinReason = PinReason.Generic)` (internal; tests)
  - `DefaultAccessControl(registry, pins, lockout, prompt, clock, toaster: Toaster, scope)`

- [ ] **Step 1: Add a recording toaster for the access tests**

`core/access/src/test/java/uk/co/siland/culvery/core/access/RecordingToaster.kt`:
```kotlin
package uk.co.siland.culvery.core.access

import uk.co.siland.culvery.core.plugin.Toaster

class RecordingToaster : Toaster {
    val messages = mutableListOf<String>()

    override fun show(message: String, icon: String) {
        messages += message
    }
}
```

- [ ] **Step 2: Write the failing access tests**

In `core/access/src/test/java/uk/co/siland/culvery/core/access/DefaultAccessControlTest.kt`:

1. Add a field after `private val seen = mutableListOf<PinRequest>()`:
```kotlin
    private val toasts = RecordingToaster()
```
2. In `access()`, add `toaster = toasts,` after `clock = WallClock { testScheduler.currentTime },`.
3. Replace `sessionExpiresAfterSixtySecondsIdle` and `touchExtendsSessionButItStillExpires` with the tests below. `AccessControl` no longer has `touch()`, so touching the screen can't extend a session: the first test is the "touches alone don't keep it alive" case, because nothing but an authorised action restarts the timer.
```kotlin
    @Test
    fun sessionLastsTwoMinutesAfterTheLastAuthorisedAction() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        advanceTimeBy(119_000); runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(2_000); runCurrent()
        assertThat(access.session.value).isNull()
        assertThat(firstPromptFor(access, CorePermissions.SETTINGS_MANAGE).error).isNull()
    }

    @Test
    fun onlyAnAuthorisedActionExtendsTheSession() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise("test.any")
        advanceTimeBy(100_000); runCurrent()
        // Passes on the session shortcut, without a pad, and restarts the two minutes.
        assertThat(access.authorise("test.any")).isNotNull()
        assertThat(seen).hasSize(1)
        advanceTimeBy(119_000); runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(2_000); runCurrent()
        assertThat(access.session.value).isNull()
    }
```
4. Add these tests at the end of the class:
```kotlin
    @Test
    fun reasonReachesThePinPad() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        val job = launch { access.authorise("test.any", reason = PinReason.Delete) }
        assertThat(prompt.request.filterNotNull().first().reason).isEqualTo(PinReason.Delete)
        job.cancel()
    }

    @Test
    fun theReasonIsGenericByDefault() = runTest {
        val access = access()
        assertThat(firstPromptFor(access, CorePermissions.SETTINGS_MANAGE).reason).isEqualTo(PinReason.Generic)
    }

    @Test
    fun aRefusalOnTheSessionShortcutToastsThenLocks() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234", "1234")
        access.authorise("test.any")
        val refused = access.authorise("test.any", allow = { _, _ -> false }, refusal = Refusal.Toast { "$it may not" })
        assertThat(refused).isNull()
        assertThat(seen).hasSize(1)
        assertThat(toasts.messages).containsExactly("Alex may not")
        assertThat(access.session.value).isNull()
        // The next tap brings up the PIN pad, so someone else can take over.
        assertThat(access.authorise("test.any")).isNotNull()
        assertThat(seen).hasSize(2)
    }

    @Test
    fun allowIsAppliedAfterAPinAndARefusalToastClosesThePad() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876")
        val result = access.authorise(
            "test.any",
            allow = { who, _ -> who.person.name != "Mia" },
            refusal = Refusal.Toast { "$it can only change events they created." },
        )
        assertThat(result).isNull()
        assertThat(toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(prompt.request.value).isNull()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun aRefusalInThePadCarriesItsMessageAndAsksAgain() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876", null)
        assertThat(access.authorise("test.any", allow = { _, _ -> false })).isNull()
        assertThat(seen.last().error).isEqualTo(PinError.NotAllowed("Mia", "Mia can't do that"))
        assertThat(toasts.messages).isEmpty()
    }

    @Test
    fun allowSeesOnlyTheGrantedPermissions() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876")
        var seenGrants: Set<String>? = null
        access.authorise(CorePermissions.SETTINGS_MANAGE, "test.any", allow = { _, g -> seenGrants = g; true })
        assertThat(seenGrants).containsExactly("test.any")
    }

    @Test
    fun aRefusedPinDoesNotResetTheLockoutCounter() = runTest {
        person("Alex", Role.ADMIN, "1234")
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "9876", "0000", null)
        access.authorise("test.any", allow = { who, _ -> who.person.name != "Mia" })
        assertThat(seen.last().lockedUntilMillis).isNotNull()
    }

    @Test
    fun pinReasonTextNamesTheActionOrThePermission() {
        assertThat(pinReasonText(PinReason.Delete, "Change any event"))
            .isEqualTo("Enter your PIN to delete this event. It also records who made the change.")
        assertThat(pinReasonText(PinReason.Assign, "Assign events"))
            .isEqualTo("Enter your PIN to assign this event. It also records who made the change.")
        assertThat(pinReasonText(PinReason.Generic, "Change settings")).isEqualTo("Enter your PIN to change settings.")
    }
```

- [ ] **Step 3: Run the access tests to see them fail**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: compilation FAILS: `toaster` is not a parameter of `DefaultAccessControl`, and `PinReason`, `Refusal`, `pinReasonText` and `PinRequest.reason` are unresolved.

- [ ] **Step 4: Extend the `AccessControl` contract**

Replace `core/access/src/main/java/uk/co/siland/culvery/core/access/AccessControl.kt` with:
```kotlin
package uk.co.siland.culvery.core.access

import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role

data class Authorised(val person: Person, val role: Role, val granted: Set<String>)

/** Hand-off §7: signed in for 2 minutes after the last authorised action. Touching the screen doesn't extend it. */
const val SESSION_TIMEOUT_MS = 120_000L

/** Why the PIN pad is asking; it picks the pad's reason line. */
enum class PinReason { Generic, Save, Edit, Delete, Assign }

/** The PIN pad's reason line. [label] is the permission's label, used for [PinReason.Generic]. */
fun pinReasonText(reason: PinReason, label: String): String = when (reason) {
    PinReason.Generic -> "Enter your PIN to ${label.replaceFirstChar { it.lowercase() }}."
    else -> "Enter your PIN to ${reason.name.lowercase()} this event. It also records who made the change."
}

/** What happens when the identified person may not do the thing. */
sealed interface Refusal {
    /** The PIN pad says "{name} can't do that" and asks again. */
    data object InPad : Refusal

    /** The PIN pad closes, [message] (given the person's name) shows as a toast, and authorise returns null. */
    class Toast(val message: (name: String) -> String) : Refusal
}

interface AccessControl {
    /** The person currently identified, or null once the session has timed out or been locked. */
    val session: StateFlow<Identified?>

    /**
     * Succeeds if the identified person (or whoever enters a PIN) holds at least one of [anyOf] and [allow]
     * agrees. Shows the PIN pad when needed, and always for fresh-PIN permissions. Returns null if cancelled
     * or refused.
     *
     * [allow] runs on the session shortcut and after each PIN, with the permissions the person holds.
     * [refusal] decides whether a "no" asks for another PIN in the pad, or closes the pad with a toast. With
     * [Refusal.Toast], a signed-in person who is refused gets the toast without a PIN pad and is signed out, so
     * the next tap asks for a PIN. Each success restarts the session's two minutes.
     */
    suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason = PinReason.Generic,
        allow: (Identified, granted: Set<String>) -> Boolean = { _, granted -> granted.isNotEmpty() },
        refusal: Refusal = Refusal.InPad,
    ): Authorised?

    fun lock()
}
```

- [ ] **Step 5: Carry the reason and the refusal message through the prompt**

In `core/access/src/main/java/uk/co/siland/culvery/core/access/PinPromptController.kt`, replace everything from `sealed interface PinError` down to the end of `class PinRequest` with:
```kotlin
sealed interface PinError {
    data object WrongPin : PinError
    data class NotAllowed(val name: String, val message: String = "$name can't do that") : PinError
}

/** One showing of the PIN pad. Identity equality: every retry is a new request. */
class PinRequest internal constructor(
    val label: String,
    val reason: PinReason,
    val error: PinError?,
    val lockedUntilMillis: Long?,
) {
    internal val answer = CompletableDeferred<String?>()
}
```
In the same file, replace `open` and `ask` with:
```kotlin
    internal fun open(label: String, error: PinError?, lockedUntilMillis: Long?, reason: PinReason = PinReason.Generic): PinRequest =
        PinRequest(label, reason, error, lockedUntilMillis).also { _request.value = it }

    internal suspend fun ask(label: String, reason: PinReason, error: PinError?, lockedUntilMillis: Long?): String? =
        open(label, error, lockedUntilMillis, reason).answer.await()
```

- [ ] **Step 6: Apply `allow` and `refusal` in `DefaultAccessControl`**

Replace `core/access/src/main/java/uk/co/siland/culvery/core/access/DefaultAccessControl.kt` with:
```kotlin
package uk.co.siland.culvery.core.access

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
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

@Singleton
class DefaultAccessControl @Inject constructor(
    private val registry: PermissionRegistry,
    private val pins: PinManager,
    private val lockout: LockoutStore,
    private val prompt: PinPromptController,
    private val clock: WallClock,
    private val toaster: Toaster,
    @ApplicationScope private val scope: CoroutineScope,
) : AccessControl {
    private val _session = MutableStateFlow<Identified?>(null)
    override val session: StateFlow<Identified?> = _session.asStateFlow()

    private var expiry: Job? = null
    // Serialises callers so repeated taps can't stack PIN pads, and a queued caller sees the session the first one started.
    private val authoriseLock = Mutex()

    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
    ): Authorised? {
        require(anyOf.isNotEmpty()) { "authorise needs at least one permission" }
        val defs = anyOf.map(registry::require)
        return authoriseLock.withLock {
            val current = _session.value
            if (current != null && defs.none { it.freshPin }) {
                val grants = grantedFor(current.role, anyOf)
                if (grants.isNotEmpty() && allow(current, grants)) {
                    restartExpiry()
                    return@withLock Authorised(current.person, current.role, grants)
                }
                if (refusal is Refusal.Toast) {
                    toaster.show(refusal.message(current.person.name))
                    // Signed out, so the next tap brings up the PIN pad: an adult can take over from a child.
                    lock()
                    return@withLock null
                }
            }
            try {
                promptUntilResolved(defs.first().label, reason, anyOf, allow, refusal)
            } finally {
                prompt.dismiss()
            }
        }
    }

    private suspend fun promptUntilResolved(
        label: String,
        reason: PinReason,
        anyOf: Array<out String>,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
    ): Authorised? {
        var error: PinError? = null
        while (true) {
            val pin = prompt.ask(label, reason, error, lockout.lockedUntil(clock.nowMillis())) ?: return null
            if (lockout.lockedUntil(clock.nowMillis()) != null) continue

            val identified = pins.identify(pin)
            if (identified == null) {
                lockout.recordFailure(clock.nowMillis())
                error = PinError.WrongPin
                continue
            }
            val granted = grantedFor(identified.role, anyOf)
            if (granted.isEmpty() || !allow(identified, granted)) {
                // A real but refused PIN neither resets nor counts, so a child's own PIN can't clear the counter.
                if (refusal is Refusal.Toast) {
                    toaster.show(refusal.message(identified.person.name))
                    return null
                }
                error = PinError.NotAllowed(identified.person.name)
                continue
            }
            lockout.reset()
            _session.value = identified
            restartExpiry()
            return Authorised(identified.person, identified.role, granted)
        }
    }

    override fun lock() {
        expiry?.cancel()
        _session.value = null
    }

    private fun grantedFor(role: Role, anyOf: Array<out String>): Set<String> =
        anyOf.filter { registry.isGranted(role, it) }.toSet()

    private fun restartExpiry() {
        expiry?.cancel()
        val guarded = _session.value
        expiry = scope.launch {
            delay(SESSION_TIMEOUT_MS)
            _session.compareAndSet(guarded, null)
        }
    }
}
```

- [ ] **Step 7: Update the app: its fake, and no touch hook**

In `app/src/test/java/uk/co/siland/culvery/shell/Fakes.kt`:
- add imports `uk.co.siland.culvery.core.access.PinReason` and `uk.co.siland.culvery.core.access.Refusal`;
- replace `override suspend fun authorise(vararg anyOf: String): Authorised? {` with:
```kotlin
    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
    ): Authorised? {
```
- in `FakeAccessControl`, delete the line `var touches = 0` and the line `override fun touch() { touches++ }`.

In `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt`, delete the test `userActivityTouchesSession`.

In `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`, delete the line `fun onUserActivity() = access.touch()` and the blank line before it.

In `app/src/main/java/uk/co/siland/culvery/MainActivity.kt`, delete `import android.view.MotionEvent` and the whole `dispatchTouchEvent` override with its comment:
```kotlin
    // Every touch-down anywhere (shell, sheet, PIN pad) keeps the PIN session alive.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) shell.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }
```
so the class ends after `onWindowFocusChanged`.

Then check nothing else still calls the removed API:
```bash
grep -rn "touch()\|onUserActivity\|dispatchTouchEvent" --include=*.kt app core
```
Expected: no output.

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :core:access:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS. Every existing access test still passes, with `PinError.NotAllowed("Mia")` now equal to `NotAllowed("Mia", "Mia can't do that")`.

- [ ] **Step 9: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. Hilt compiles `DefaultAccessControl` with the `Toaster` bound in Task 2, and no screenshot changes.

- [ ] **Step 10: Commit**

```bash
git add core/access app
git commit -m "Give access a 2-minute session that only authorised actions extend, PIN reasons, allow rules and refusal toasts"
```

---

### Task 4: PIN pad redesign, placed over the sheet

**Files:**
- Create: `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPadDimens.kt`
- Modify (rewrite): `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt`
- Test (rewrite): `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/PinPadTest.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/MainActivity.kt` (one line)
- Modify: `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`
- Re-record: `app/src/test/screenshots/pin_pad_dark.png`, `pin_pad_light.png`; record `pin_pad_wrong_dark.png`

**Interfaces:**
- Consumes:
  - `PinReason`, `pinReasonText`, `PinError.NotAllowed.message`, `PinRequest.reason` (Task 3)
  - `ShellTokens.sheetWidth`, `ShellTokens.pinScrim` (Task 2)
  - `OverlayState.isShowing` (Task 2)
- Produces:
  - `@Composable fun PinPadHost(controller: PinPromptController, overSheet: Boolean = false)`
  - `@Composable fun PinPadSheet(label: String, reason: PinReason, error: PinError?, lockedUntilMillis: Long?, onSubmit: (String) -> Unit, onCancel: () -> Unit, overSheet: Boolean = false)`
  - Test tags: `pin_scrim` (full screen, tap = cancel), `pin_area` (the scrimmed area), `pin_card`, `pin_key_0`…`pin_key_9`, `pin_cancel`, `pin_backspace`, `pin_dots`, `pin_error`
  - `internal object PinPadDimens`, `internal object PinPadType`

- [ ] **Step 1: Write the failing PIN pad tests**

Replace `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/PinPadTest.kt` with:
```kotlin
package uk.co.siland.culvery.core.access.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.ui.CulveryTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PinPadTest {
    @get:Rule val compose = createComposeRule()
    private val controller = PinPromptController()

    private fun show(overSheet: Boolean = false) =
        compose.setContent { CulveryTheme(dark = true) { PinPadHost(controller, overSheet) } }

    private fun tap(vararg keys: String) = keys.forEach { compose.onNodeWithTag("pin_key_$it").performClick() }

    @Test
    fun fourthDigitSubmitsAutomatically() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3", "4")
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isEqualTo("1234")
    }

    @Test
    fun threeDigitsDoNotSubmit() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3")
        compose.waitForIdle()
        assertThat(request.answer.isCompleted).isFalse()
    }

    @Test
    fun backspaceRemovesLastDigit() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3")
        compose.onNodeWithTag("pin_backspace").performClick()
        tap("4", "5")
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isEqualTo("1245")
    }

    @Test
    fun cancelKeyAnswersNull() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_cancel").performClick()
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isNull()
    }

    @Test
    fun tappingOutsideTheCardCancels() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_scrim").performTouchInput { click(Offset(10f, 10f)) }
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isNull()
    }

    @Test
    fun tappingTheCardItselfDoesNotCancel() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithText("Who's this?").performClick()
        compose.waitForIdle()
        assertThat(request.answer.isCompleted).isFalse()
    }

    @Test
    fun asksWhoIsThisWithTheEventReason() {
        controller.open("Change any event", null, null, PinReason.Delete)
        show()
        compose.onNodeWithText("Who's this?").assertExists()
        compose.onNodeWithText("Enter your PIN to delete this event. It also records who made the change.").assertExists()
    }

    @Test
    fun genericReasonUsesThePermissionLabel() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithText("Enter your PIN to change settings.").assertExists()
    }

    @Test
    fun wrongPinShowsTheErrorLineWithEmptyDigits() {
        controller.open("Change settings", PinError.WrongPin, null)
        show()
        compose.onNodeWithText("Wrong PIN — try again").assertExists()
    }

    @Test
    fun notAllowedShowsItsMessage() {
        controller.open("Change settings", PinError.NotAllowed("Mia"), null)
        show()
        compose.onNodeWithText("Mia can't do that").assertExists()
    }

    @Test
    fun keysAreDisabledWhileLocked() {
        // The countdown loops on delay(); stop the test clock racing through it.
        compose.mainClock.autoAdvance = false
        controller.open("Change settings", PinError.WrongPin, System.currentTimeMillis() + 60_000)
        show()
        compose.onNodeWithTag("pin_key_1").assertIsNotEnabled()
        compose.onNodeWithText("Too many tries", substring = true).assertExists()
    }

    @Test
    fun hiddenWhenNoRequest() {
        show()
        compose.onNodeWithTag("pin_scrim").assertDoesNotExist()
    }

    @Test
    fun backspaceIsLabelled() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithContentDescription("Delete last digit").assertExists()
    }

    @Test
    fun keysAre76dpCirclesInA400dpCard() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_key_5").assertWidthIsEqualTo(76.dp).assertHeightIsEqualTo(76.dp)
        compose.onNodeWithTag("pin_card").assertWidthIsEqualTo(400.dp)
    }

    @Test
    fun withoutASheetTheCardIsCentredOnTheScreen() {
        controller.open("Change settings", null, null)
        show()
        val card = compose.onNodeWithTag("pin_card").getUnclippedBoundsInRoot()
        assertThat(((card.left + card.right) / 2).value).isWithin(1f).of(640f)
    }

    @Test
    fun overASheetTheScrimCoversOnlyTheSheetAndTheCardIsCentredInIt() {
        controller.open("Change settings", null, null)
        show(overSheet = true)
        val area = compose.onNodeWithTag("pin_area").getUnclippedBoundsInRoot()
        assertThat(area.left.value).isWithin(1f).of(680f)
        assertThat((area.right - area.left).value).isWithin(1f).of(600f)
        val card = compose.onNodeWithTag("pin_card").getUnclippedBoundsInRoot()
        assertThat(((card.left + card.right) / 2).value).isWithin(1f).of(980f)
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :core:access:testDebugUnitTest --tests "*PinPadTest*"`
Expected: compilation FAILS: `PinPadHost` has no `overSheet` parameter. Once that compiles, the new text and tag checks fail.

- [ ] **Step 3: Name the pad's values**

`core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPadDimens.kt`:
```kotlin
package uk.co.siland.culvery.core.access.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** Hand-off §7 "PIN pad". */
internal object PinPadDimens {
    // Card: 400 dp, `surf`, radius 30, padding 26×28, 14 dp between blocks; 6 dp between title and reason.
    val cardWidth = 400.dp
    val cardRadius = 30.dp
    val cardPaddingV = 26.dp
    val cardPaddingH = 28.dp
    val gap = 14.dp
    val titleReasonGap = 6.dp

    // 56 dp `accentSoft` lock badge with a 30 dp `accent` lock.
    val badge = 56.dp
    val badgeIcon = 30.dp

    // Four 18 dp dots 16 apart; empty ones have a 2 dp border.
    val dot = 18.dp
    val dotGap = 16.dp
    val dotBorder = 2.dp

    // The error line is always 18 dp high, so the keypad never moves.
    val errorLine = 18.dp

    // Keypad: 76 dp circles, 12 dp between rows, 20 dp between columns.
    val key = 76.dp
    val keyRowGap = 12.dp
    val keyColumnGap = 20.dp
    val backspaceIcon = 30.dp
}

internal object PinPadType {
    /** 24 sp / 700: "Who's this?". */
    val title = HhType.dateNumber

    /** 15 sp / 400: the reason line. */
    val reason = HhType.secondary.copy(fontSize = 15.sp)

    /** 14 sp / 700: the error line. */
    val error = HhType.secondary.copy(fontWeight = FontWeight.W700)

    /** 16 sp / 600: the Cancel key. */
    val cancel = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 30 sp / 600, tabular: digits. */
    val digit = HhType.pinDigit
}
```

- [ ] **Step 4: Rewrite the pad**

Replace `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt` with:
```kotlin
package uk.co.siland.culvery.core.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.pinReasonText
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.ShellTokens

/** [overSheet]: a sheet is open, so the pad covers the sheet's area rather than the whole screen. */
@Composable
fun PinPadHost(controller: PinPromptController, overSheet: Boolean = false) {
    val request by controller.request.collectAsState()
    request?.let { r ->
        // Keyed on the request so each retry (e.g. after a wrong PIN) starts with empty digits.
        key(r) {
            PinPadSheet(
                label = r.label,
                reason = r.reason,
                error = r.error,
                lockedUntilMillis = r.lockedUntilMillis,
                onSubmit = controller::submit,
                onCancel = controller::cancel,
                overSheet = overSheet,
            )
        }
    }
}

/**
 * Hand-off §7 PIN pad. It catches every tap on the screen: a tap outside the card cancels. The `rgba(0,0,0,.5)`
 * scrim covers the sheet's 600 dp when [overSheet], otherwise the whole screen; the card is centred in it.
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
    val c = Culvery.colors
    var digits by remember { mutableStateOf("") }
    // Counts down from the initial value rather than re-reading the wall clock, so tests with a
    // virtual frame clock stay deterministic.
    val secondsLeft by produceState(secondsUntil(lockedUntilMillis), lockedUntilMillis) {
        while (value > 0) {
            delay(1_000)
            value -= 1
        }
    }
    val locked = secondsLeft > 0
    val canType = !locked && digits.length < PinHasher.PIN_LENGTH
    val message = when {
        locked -> "Too many tries — wait ${secondsLeft}s"
        error is PinError.WrongPin -> "Wrong PIN — try again"
        error is PinError.NotAllowed -> error.message
        else -> ""
    }
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
                .background(ShellTokens.pinScrim),
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
                    Text("Who's this?", style = PinPadType.title, color = c.ink)
                    Text(pinReasonText(reason, label), style = PinPadType.reason, color = c.mute, textAlign = TextAlign.Center)
                }
                Dots(filled = digits.length, error = error is PinError.WrongPin && digits.isEmpty())
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
                                .clickable(enabled = digits.isNotEmpty() && !locked) { digits = digits.dropLast(1) },
                        ) {
                            HhIcon("backspace", size = PinPadDimens.backspaceIcon, tint = c.ink, contentDescription = "Delete last digit")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dots(filled: Int, error: Boolean) {
    val c = Culvery.colors
    Row(horizontalArrangement = Arrangement.spacedBy(PinPadDimens.dotGap), modifier = Modifier.testTag("pin_dots")) {
        repeat(PinHasher.PIN_LENGTH) { i ->
            val dot = Modifier.size(PinPadDimens.dot).clip(CircleShape)
            Box(
                if (i < filled) {
                    dot.background(c.ink)
                } else {
                    dot.border(PinPadDimens.dotBorder, if (error) c.danger else c.mute, CircleShape)
                },
            )
        }
    }
}

@Composable
private fun DigitKey(digit: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("pin_key_$digit")
            .size(PinPadDimens.key)
            .clip(CircleShape)
            .background(c.surf2)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Text(digit, style = PinPadType.digit, color = c.ink)
    }
}

private fun secondsUntil(until: Long?): Int =
    until?.let { ((it - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0) } ?: 0
```

- [ ] **Step 5: Place the pad over the sheet in the app**

In `app/src/main/java/uk/co/siland/culvery/MainActivity.kt`, replace `PinPadHost(pinPrompt)` with:
```kotlin
                    PinPadHost(pinPrompt, overSheet = overlay.isShowing)
```

- [ ] **Step 6: Run the pad tests to see them pass**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: PASS (16 tests in `PinPadTest`).

- [ ] **Step 7: Update and extend the PIN pad screenshots**

In `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`:
- add imports `uk.co.siland.culvery.core.access.PinError` and `uk.co.siland.culvery.core.access.PinReason`;
- replace the two PIN pad tests with:
```kotlin
    @Test
    fun pinPadDark() = snap("pin_pad_dark", dark = true) {
        PinPadSheet("Change settings", PinReason.Generic, error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun pinPadLight() = snap("pin_pad_light", dark = false) {
        PinPadSheet("Change settings", PinReason.Generic, error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun pinPadWrongDark() = snap("pin_pad_wrong_dark", dark = true) {
        PinPadSheet("Change settings", PinReason.Generic, error = PinError.WrongPin, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }
```

Run: `./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest.pinPad*"`

Open `pin_pad_dark.png`, `pin_pad_light.png` and `pin_pad_wrong_dark.png`. Compare them with `docs/design/house_hub_handoff/screenshots/calendar-sheets/09-pin-pad-dark.png` and `09-pin-pad-light.png`. The hand-off draws the pad over a sheet; here it is centred over the whole screen with a darker scrim. Check the card:
- 400 dp wide, `surf`, radius 30, over a half-dark scrim;
- a green-tinted 56 dp circle with a green lock;
- "Who's this?" at 24 sp bold, with "Enter your PIN to change settings." centred below it in muted 15 sp;
- four 18 dp ring dots;
- a 3 × 4 keypad of 76 dp `surf2` circles, whose bottom row is "Cancel" (plain text), "0", then a backspace icon;
- no "Prototype PINs" hint.

In `pin_pad_wrong_dark.png`, the dots have coral (`danger`) rings, and "Wrong PIN — try again" shows in coral bold between the dots and the keypad. The keypad does not move between the two images.

Run: `./gradlew :app:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add core/access app
git commit -m "Redesign the PIN pad and place it over an open sheet"
```

---

### Task 5: `CalendarWriter` contract and the contract suite's write checks

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`
- Modify: `capability/calendar-testkit/src/main/java/uk/co/siland/culvery/capability/calendar_testkit/CalendarProviderContractTest.kt`
- Modify: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyProvider.kt`
- Modify: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyContracts.kt`
- Test: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/ContractSuiteSelfTest.kt` (modify)

**Interfaces:**
- Consumes:
  - `EventDraft` (Task 1)
  - existing `CalendarProvider`, `RemoteEvent`, `CalendarSource`, `SyncCursor`, `Feature`
- Produces:
  - `class WriteRejectedException(message: String, cause: Throwable? = null) : Exception`, with KDoc on it, `NeedsSignInException` and `UnreachableException` saying which provider errors each covers (429, 403 rate limits and 5xx are `UnreachableException`; a delete's 404/410 is success)
  - `interface CalendarWriter`:
    - `val providerId: String`
    - `suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEvent`
    - `suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft): RemoteEvent`: changes only the title, the times and the tags, and keeps every other field (Google: PATCH)
    - `suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String)`
  - `@Multibinds Set<CalendarWriter>` in `CalendarModule`
  - `CalendarProviderContractTest` hooks: `protected open fun writer(): CalendarWriter? = null` and `protected open fun writableSource(): CalendarSource? = null`
  - Six new checks, which run only when the provider declares `Feature.WRITE`:
    - `createdEventComesBackOnTheNextSyncWithItsTags`
    - `anAllDayEventRoundTrips`
    - `updatedFieldsRoundTrip`
    - `deletedEventIsRemovedOnTheNextSync`
    - `deletingAnEventThatIsAlreadyGoneSucceeds`
    - `aWriteToAnUnknownSourceIsRejected`
  - `TinyProvider.WRITABLE`; fixture contracts `DroppingTagsContract`, `StrictDeleteContract` and `ReadOnlyContract`

- [ ] **Step 1: Write the failing self-tests**

In `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/ContractSuiteSelfTest.kt`:
- add imports `uk.co.siland.culvery.capability.calendar_testkit.fixtures.DroppingTagsContract`, `uk.co.siland.culvery.capability.calendar_testkit.fixtures.ReadOnlyContract` and `uk.co.siland.culvery.capability.calendar_testkit.fixtures.StrictDeleteContract`;
- in `wellBehavedProviderPassesEveryCheck`, change `isEqualTo(10)` to `isEqualTo(16)`;
- add at the end of the class:
```kotlin
    @Test
    fun droppedTagsAreCaught() {
        assertThat(failuresOf(DroppingTagsContract::class.java)).containsExactly("createdEventComesBackOnTheNextSyncWithItsTags")
    }

    @Test
    fun refusingToDeleteAMissingEventIsCaught() {
        assertThat(failuresOf(StrictDeleteContract::class.java)).containsExactly("deletingAnEventThatIsAlreadyGoneSucceeds")
    }

    @Test
    fun aReadOnlyProviderSkipsOnlyTheWriteChecks() {
        val result = JUnitCore.runClasses(ReadOnlyContract::class.java)
        assertThat(result.failures.map { "${it.description.methodName}: ${it.message}" }).isEmpty()
        assertThat(result.runCount).isEqualTo(16)
        assertThat(result.assumptionFailureCount).isEqualTo(6)
    }
```

In `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyContracts.kt`, add to `TinyContract` after `override fun simulateUnreachable() = …`:
```kotlin
    override fun writer() = tiny
    override fun writableSource() = TinyProvider.WRITABLE
```
and add at the end of the file:
```kotlin
class DroppingTagsContract : TinyContract(TinyProvider(dropTagsOnCreate = true))
class StrictDeleteContract : TinyContract(TinyProvider(rejectMissingDelete = true))
class ReadOnlyContract : TinyContract(TinyProvider(canWrite = false))
```

- [ ] **Step 2: Run the self-test to see it fail**

Run: `./gradlew :capability:calendar-testkit:testDebugUnitTest`
Expected: compilation FAILS: `writer`, `writableSource`, `TinyProvider.WRITABLE`, and the `dropTagsOnCreate`, `rejectMissingDelete` and `canWrite` parameters are unresolved.

- [ ] **Step 3: Add the write contract**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`, replace the two lines
```kotlin
class NeedsSignInException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class UnreachableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
```
with:
```kotlin
/** The connection's sign-in has expired or been revoked (Google: 401, or a refused token refresh). */
class NeedsSignInException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * The provider couldn't be reached, or asked to be tried later: network errors, and for Google 429, 403
 * rate-limit reasons (rateLimitExceeded, userRateLimitExceeded) and every 5xx. The engine retries with backoff.
 */
class UnreachableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * A permanent refusal: retrying would not help (Google: a 4xx other than 401, 429 and the 403 rate limits).
 * A delete that finds the event already gone (404 or 410) is a success, not a refusal.
 */
class WriteRejectedException(message: String, cause: Throwable? = null) : Exception(message, cause)
```
and append at the end of the file:
```kotlin
/**
 * Writes for a provider that declares Feature.WRITE. Bound `@IntoSet` beside its CalendarProvider; the engine
 * matches them by [providerId] == descriptor.id. Behaviour is pinned by the write checks in
 * CalendarProviderContractTest.
 *
 * - Throws only [WriteRejectedException] (permanent), [NeedsSignInException] or [UnreachableException].
 * - Main-safe and cancellable. Called on Dispatchers.IO under a timeout: 10 s from the editor, 60 s from the
 *   outbox drain. A timeout counts as unreachable and the change is queued or retried.
 * - Stores [EventDraft.forPerson] and [EventDraft.createdBy] so that the next sync returns them unchanged.
 * - A write to a source the connection doesn't have, or can't write, throws [WriteRejectedException].
 * - [delete] of an event that no longer exists succeeds: a retried delete must not be reported as a failure.
 */
interface CalendarWriter {
    val providerId: String

    suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEvent

    /**
     * Changes only the title, the times and the two tags. Everything else the service holds (description,
     * location, attendees, reminders) is kept (Google: PATCH, never PUT).
     */
    suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft): RemoteEvent

    suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String)
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`, add `import uk.co.siland.culvery.capability.calendar.CalendarWriter` and, after the `providers()` multibinding:
```kotlin
    @Multibinds
    abstract fun writers(): Set<CalendarWriter>
```

- [ ] **Step 4: Add the write checks to the contract suite**

In `capability/calendar-testkit/src/main/java/uk/co/siland/culvery/capability/calendar_testkit/CalendarProviderContractTest.kt`:

1. Add imports:
```kotlin
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.core.plugin.Feature
```
2. Add after `protected open fun simulateUnreachable(): (() -> Unit)? = null`:
```kotlin
    /** The writer, for a provider that declares Feature.WRITE; the write checks fail if it is missing. */
    protected open fun writer(): CalendarWriter? = null
    /** A writable source the write checks may create, change and delete events in. */
    protected open fun writableSource(): CalendarSource? = null
```
3. Add after `private val window by lazy { range() }`:
```kotlin
    private val writes by lazy { writer() }
```
4. Add these members at the end of the class:
```kotlin
    /** Skips a provider without WRITE; fails one that declares WRITE but can't be exercised. */
    private fun requireWriting(): Pair<CalendarWriter, CalendarSource> {
        assumeTrue("provider does not declare WRITE", Feature.WRITE in subject.descriptor.features)
        val w = writes
        assertWithMessage("the provider declares WRITE, so writer() must return its writer").that(w).isNotNull()
        assertWithMessage("writer.providerId must equal the provider's descriptor.id")
            .that(w!!.providerId).isEqualTo(subject.descriptor.id)
        val source = writableSource()
        assertWithMessage("the provider declares WRITE, so writableSource() must name a writable source").that(source).isNotNull()
        return w to source!!
    }

    /** A one-hour event on day [dayOffset] after the window's second day, at 10:00 in the window's zone. */
    private fun draftIn(title: String, dayOffset: Long, forPerson: String? = "contract-for", createdBy: String? = "contract-by"): EventDraft {
        val start = window.start.plusDays(1 + dayOffset).atTime(10, 0).atZone(window.zone).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), forPerson, createdBy)
    }

    private suspend fun nextSyncReturns(source: CalendarSource, cursor: SyncCursor?, remoteId: String): RemoteEvent? =
        subject.sync(conn, source, window, cursor).upserts.firstOrNull { it.remoteId == remoteId }

    private fun assertMatches(what: String, event: RemoteEvent, draft: EventDraft) {
        assertWithMessage("$what: title").that(event.title).isEqualTo(draft.title)
        assertWithMessage("$what: start").that(event.start).isEqualTo(draft.start)
        assertWithMessage("$what: end").that(event.end).isEqualTo(draft.end)
        assertWithMessage("$what: the forPerson tag").that(event.forPerson).isEqualTo(draft.forPerson)
        assertWithMessage("$what: the createdBy tag").that(event.createdBy).isEqualTo(draft.createdBy)
        assertWithMessage("$what: a written event is never recurring").that(event.recurring).isFalse()
    }

    @Test
    fun createdEventComesBackOnTheNextSyncWithItsTags() = runTest {
        val (w, source) = requireWriting()
        val before = subject.sync(conn, source, window, null)
        val draft = draftIn("Contract check", 1)
        val created = w.create(conn, source, draft)
        assertMatches("create's result", created, draft)
        val synced = nextSyncReturns(source, before.cursor, created.remoteId)
        assertWithMessage("the next sync must return the created event").that(synced).isNotNull()
        assertMatches("the next sync", synced!!, draft)
    }

    @Test
    fun anAllDayEventRoundTrips() = runTest {
        val (w, source) = requireWriting()
        val before = subject.sync(conn, source, window, null)
        val first = window.start.plusDays(2)
        // Two days: the end date is exclusive, as in RemoteEvent.
        val draft = EventDraft("All-day check", EventTime.AllDay(first), EventTime.AllDay(first.plusDays(2)), "contract-for", "contract-by")
        val created = w.create(conn, source, draft)
        assertMatches("create's result", created, draft)
        val synced = nextSyncReturns(source, before.cursor, created.remoteId)
        assertWithMessage("the next sync must return the all-day event").that(synced).isNotNull()
        assertMatches("the next sync", synced!!, draft)
    }

    @Test
    fun updatedFieldsRoundTrip() = runTest {
        val (w, source) = requireWriting()
        val created = w.create(conn, source, draftIn("Before", 1))
        val cursor = subject.sync(conn, source, window, null).cursor
        val changed = draftIn("After", 2, forPerson = "contract-other")
        val updated = w.update(conn, source, created.remoteId, changed)
        assertWithMessage("an update keeps the remoteId").that(updated.remoteId).isEqualTo(created.remoteId)
        assertMatches("update's result", updated, changed)
        val synced = nextSyncReturns(source, cursor, created.remoteId)
        assertWithMessage("the next sync must return the updated event").that(synced).isNotNull()
        assertMatches("the next sync", synced!!, changed)
    }

    @Test
    fun deletedEventIsRemovedOnTheNextSync() = runTest {
        val (w, source) = requireWriting()
        val created = w.create(conn, source, draftIn("Doomed", 1))
        val cursor = subject.sync(conn, source, window, null).cursor
        w.delete(conn, source, created.remoteId)
        val next = subject.sync(conn, source, window, cursor)
        val reported = if (next.fullReplace) {
            next.upserts.none { it.remoteId == created.remoteId }
        } else {
            created.remoteId in next.removedIds
        }
        assertWithMessage("the next sync must report the delete: a removal, or absence from a full replace")
            .that(reported).isTrue()
        assertThat(subject.sync(conn, source, window, null).upserts.map { it.remoteId }).doesNotContain(created.remoteId)
    }

    @Test
    fun deletingAnEventThatIsAlreadyGoneSucceeds() = runTest {
        val (w, source) = requireWriting()
        val created = w.create(conn, source, draftIn("Twice", 1))
        w.delete(conn, source, created.remoteId)
        val error = try {
            w.delete(conn, source, created.remoteId)
            null
        } catch (e: Exception) {
            e
        }
        assertWithMessage("a second delete of the same event must succeed: a retried delete may already have landed")
            .that(error).isNull()
    }

    @Test
    fun aWriteToAnUnknownSourceIsRejected() = runTest {
        val (w, _) = requireWriting()
        val unknown = CalendarSource("contract-no-such-source", "Nowhere", writable = true)
        val error = try {
            w.create(conn, unknown, draftIn("Lost", 1))
            null
        } catch (e: Throwable) {
            e
        }
        assertThat(error).isInstanceOf(WriteRejectedException::class.java)
    }
```

- [ ] **Step 5: Give the tiny fixture a writer**

Replace `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyProvider.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar_testkit.fixtures

import androidx.compose.runtime.Composable
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventDraft
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

/** A minimal provider whose flags each break one rule of the contract. */
class TinyProvider(
    private val leakOutOfRange: Boolean = false,
    private val partialFirstSync: Boolean = false,
    private val repeatOnCursor: Boolean = false,
    private val rawNetworkErrors: Boolean = false,
    private val inclusiveAllDayEnd: Boolean = false,
    private val rawAuthErrors: Boolean = false,
    private val canWrite: Boolean = true,
    private val dropTagsOnCreate: Boolean = false,
    private val rejectMissingDelete: Boolean = false,
) : CalendarProvider, CalendarWriter {
    override val descriptor = ProviderDescriptor(
        "calendar.tiny",
        "Tiny",
        "event",
        if (canWrite) setOf(Feature.READ, Feature.WRITE) else setOf(Feature.READ),
    )
    override val providerId = "calendar.tiny"

    private var failNext: Throwable? = null
    private val written = linkedMapOf<String, RemoteEvent>()
    private var version = 0
    private var nextId = 0

    fun failNextWith(error: Throwable) {
        failNext = error
    }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
    }

    override suspend fun sources(conn: Connection) = if (canWrite) listOf(SOURCE, WRITABLE) else listOf(SOURCE)

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        failNext?.let { error ->
            failNext = null
            throw when {
                rawNetworkErrors && error is UnreachableException -> IOException("socket closed")
                rawAuthErrors && error is NeedsSignInException -> IllegalStateException("HTTP 401")
                else -> error
            }
        }
        if (source.id == WRITABLE.id) {
            // Every write bumps the version, so a cursor from before it gets a full replace.
            val current = SyncCursor("w$version")
            if (cursor == current) return SyncResult(emptyList(), emptyList(), current, fullReplace = false)
            return SyncResult(written.values.filter { range.overlaps(it.start, it.end) }, emptyList(), current, fullReplace = true)
        }
        if (cursor != null && !repeatOnCursor) return SyncResult(emptyList(), emptyList(), cursor, fullReplace = false)
        val events = all(range.zone).filter { leakOutOfRange || range.overlaps(it.start, it.end) }
        return SyncResult(events, emptyList(), SyncCursor("c1"), fullReplace = !partialFirstSync)
    }

    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEvent {
        checkWritable(source)
        val event = RemoteEvent(
            "w${++nextId}",
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

    override suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft): RemoteEvent {
        checkWritable(source)
        if (remoteId !in written) throw WriteRejectedException("No event $remoteId")
        val event = RemoteEvent(remoteId, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
        written[remoteId] = event
        version++
        return event
    }

    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) {
        checkWritable(source)
        when {
            written.remove(remoteId) != null -> version++
            rejectMissingDelete -> throw WriteRejectedException("No event $remoteId")
        }
    }

    private fun checkWritable(source: CalendarSource) {
        if (!canWrite || source.id != WRITABLE.id) throw WriteRejectedException("Tiny can't write to ${source.id}")
    }

    private fun all(zone: ZoneId): List<RemoteEvent> {
        fun at(date: LocalDate, hour: Int) = EventTime.Timed(date.atTime(hour, 0).atZone(zone).toInstant())
        val d = LocalDate.of(2026, 9, 24)
        val far = LocalDate.of(2026, 11, 7)
        return listOf(
            RemoteEvent("walk", "Walk", at(d, 9), at(d, 10), recurring = false),
            RemoteEvent("yoga-1", "Yoga", at(d, 18), at(d, 19), recurring = true),
            RemoteEvent("yoga-2", "Yoga", at(d.plusDays(7), 18), at(d.plusDays(7), 19), recurring = true),
            RemoteEvent("holiday", "Holiday", EventTime.AllDay(d), EventTime.AllDay(d.plusDays(2)), recurring = false),
            // An inclusive end on a one-day event equals its start date.
            RemoteEvent("bins", "Bins", EventTime.AllDay(d), EventTime.AllDay(if (inclusiveAllDayEnd) d else d.plusDays(1)), recurring = false),
            RemoteEvent("far", "Far away", at(far, 9), at(far, 10), recurring = false),
        )
    }

    companion object {
        val SOURCE = CalendarSource("tiny", "Tiny", writable = false)
        val WRITABLE = CalendarSource("tiny-w", "Tiny writable", writable = true)
    }
}
```

- [ ] **Step 6: Run the self-test to see it pass**

Run: `./gradlew :capability:calendar-testkit:testDebugUnitTest`
Expected: PASS (10 tests in `ContractSuiteSelfTest`).
- The good fixture runs 16 checks and skips none.
- The read-only fixture skips exactly the six write checks.
- Dropping tags fails only `createdEventComesBackOnTheNextSyncWithItsTags`.
- Refusing to delete a missing event fails only `deletingAnEventThatIsAlreadyGoneSucceeds`.
- Every older broken fixture still fails only its own check.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. `FakeCalendarProviderContractTest` still passes. Its provider does not declare WRITE yet, so the six write checks are skipped there until Task 6.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar capability/calendar-testkit
git commit -m "Add the calendar writer contract and write checks to the provider contract suite"
```

---

### Task 6: Fake provider writes, switches and tagged sample week

**Files:**
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/SampleEvents.kt`
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/di/FakeCalendarModule.kt`
- Test: `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderContractTest.kt` (modify)
- Test: `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt` (modify)

**Interfaces:**
- Consumes:
  - `CalendarWriter`, `WriteRejectedException`, `EventDraft` (Tasks 1 and 5)
  - the contract suite's `writer()` and `writableSource()` (Task 5)
- Produces:
  - `FakeCalendarProvider : CalendarProvider, CalendarWriter`, declaring `Feature.READ` and `Feature.WRITE`
  - `FakeCalendarProvider` methods:
    - `fun rejectNextWrite(message: String)`
    - `fun unreachableNextWrite()`
    - `fun tagSamples(idsByName: Map<String, String>)`
  - `FakeCalendarProvider.SOURCES`: `SOURCE_FAMILY` is named **"Family calendar"** and is `writable = true`; the rest stay read-only
  - The sample week lives on `SOURCE_FAMILY`, tagged by name ("Alex", "Sam", "Mia", "Family"). INSET day stays on `SOURCE_SCHOOL`. Plumber quote call is untagged.
  - Hilt: `@Binds @IntoSet CalendarWriter` → `FakeCalendarProvider` (the same singleton)

- [ ] **Step 1: Point the contract test at the writable Family calendar**

In `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderContractTest.kt`, replace `override fun sourceWithEvents() = …` with:
```kotlin
    private val familyCalendar = CalendarSource(FakeCalendarProvider.SOURCE_FAMILY, "Family calendar", writable = true)

    override fun sourceWithEvents() = familyCalendar
    override fun writer() = fake
    override fun writableSource() = familyCalendar
```

- [ ] **Step 2: Update and extend the fake's own tests**

In `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt`:

1. Add imports:
```kotlin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.core.plugin.Feature
```
2. Replace `sourcesAreThePeopleFamilyAndSchoolTermsAllReadOnly` with:
```kotlin
    @Test
    fun onlyTheFamilyCalendarIsWritable() = runTest {
        val sources = providerOn(today).sources(conn)
        assertThat(sources.map { it.name }).containsExactly("Alex", "Sam", "Mia", "Family calendar", "School terms").inOrder()
        assertThat(sources.filter { it.writable }.map { it.id }).containsExactly(FakeCalendarProvider.SOURCE_FAMILY)
        assertThat(providerOn(today).descriptor.features).containsExactly(Feature.READ, Feature.WRITE)
    }
```
3. In `todayMatchesTheHandOff`, replace the expected list with:
```kotlin
            "07:45–08:30 School run (fake-family)",
            "10:00–11:00 Boiler service (fake-family)",
            "13:00–13:30 Plumber quote call (fake-family)",
            "16:00–17:00 Swimming (fake-family)",
            "19:30–21:00 Dinner with Jo & Priya (fake-family)",
```
4. Rename `pianoRepeatsWeeklyOnMiasCalendar` to `pianoRepeatsWeeklyOnTheFamilyCalendar` and change `source(FakeCalendarProvider.SOURCE_MIA)` in it to `source(FakeCalendarProvider.SOURCE_FAMILY)`.
5. In `swimmingAndBinDayRepeatWeekly`, change `source(FakeCalendarProvider.SOURCE_MIA)` to `source(FakeCalendarProvider.SOURCE_FAMILY)`.
6. In `cursorFromYesterdayGivesAFullReplace`, change `val source = FakeCalendarProvider.SOURCES.first()` to `val source = source(FakeCalendarProvider.SOURCE_FAMILY)`.
7. Add at the end of the class:
```kotlin
    private val family get() = source(FakeCalendarProvider.SOURCE_FAMILY)

    private suspend fun FakeCalendarProvider.familyEvents(): List<RemoteEvent> =
        sync(conn, family, windowFrom(today), null).upserts

    private fun draft(title: String) = EventDraft(
        title,
        EventTime.Timed(today.plusDays(1).atTime(18, 0).atZone(zone).toInstant()),
        EventTime.Timed(today.plusDays(1).atTime(19, 0).atZone(zone).toInstant()),
        forPerson = "mia-id",
        createdBy = "sam-id",
    )

    @Test
    fun samplesAreUntaggedExceptFamilyUntilTheSeedGivesIds() = runTest {
        val byTitle = providerOn(today).familyEvents().associateBy { it.title }
        assertThat(byTitle.getValue("Dinner with Jo & Priya").let { it.forPerson to it.createdBy }).isEqualTo(null to null)
        assertThat(byTitle.getValue("Boiler service").forPerson).isEqualTo("family")
    }

    @Test
    fun tagSamplesTagsTheWeekWithTheHouseholdsIds() = runTest {
        val fake = providerOn(today)
        fake.tagSamples(mapOf("Alex" to "alex-id", "Sam" to "sam-id", "Mia" to "mia-id"))
        val byTitle = fake.familyEvents().associateBy { it.title }
        assertThat(byTitle.getValue("Dinner with Jo & Priya").let { it.forPerson to it.createdBy }).isEqualTo("alex-id" to "alex-id")
        assertThat(byTitle.getValue("Football").let { it.forPerson to it.createdBy }).isEqualTo("mia-id" to "mia-id")
        assertThat(byTitle.getValue("Boiler service").let { it.forPerson to it.createdBy }).isEqualTo("family" to "alex-id")
        assertThat(byTitle.getValue("Plumber quote call").let { it.forPerson to it.createdBy }).isEqualTo(null to null)
    }

    @Test
    fun tagSamplesForcesAFullReplaceOnlyWhenTheIdsChange() = runTest {
        val fake = providerOn(today)
        val first = fake.sync(conn, family, windowFrom(today), null)
        fake.tagSamples(mapOf("Alex" to "alex-id"))
        val second = fake.sync(conn, family, windowFrom(today), first.cursor)
        assertThat(second.fullReplace).isTrue()
        fake.tagSamples(mapOf("Alex" to "alex-id"))
        assertThat(fake.sync(conn, family, windowFrom(today), second.cursor).fullReplace).isFalse()
    }

    @Test
    fun deletingASampleHidesItAndDeletingItAgainSucceeds() = runTest {
        val fake = providerOn(today)
        val boiler = fake.familyEvents().single { it.title == "Boiler service" }
        fake.delete(conn, family, boiler.remoteId)
        fake.delete(conn, family, boiler.remoteId)
        assertThat(fake.familyEvents().map { it.title }).doesNotContain("Boiler service")
    }

    @Test
    fun updatingASampleReplacesItOnTheNextSync() = runTest {
        val fake = providerOn(today)
        val plumber = fake.familyEvents().single { it.title == "Plumber quote call" }
        fake.update(conn, family, plumber.remoteId, EventDraft(plumber.title, plumber.start, plumber.end, "sam-id", null))
        assertThat(fake.familyEvents().single { it.remoteId == plumber.remoteId }.forPerson).isEqualTo("sam-id")
    }

    @Test
    fun repeatingSamplesCannotBeChanged() = runTest {
        val fake = providerOn(today)
        val swim = fake.familyEvents().first { it.title == "Swimming" }
        assertThrows(WriteRejectedException::class.java) {
            runBlocking { fake.update(conn, family, swim.remoteId, EventDraft(swim.title, swim.start, swim.end, null, null)) }
        }
    }

    @Test
    fun readOnlySourcesCannotBeWritten() = runTest {
        val fake = providerOn(today)
        assertThrows(WriteRejectedException::class.java) {
            runBlocking { fake.create(conn, source(FakeCalendarProvider.SOURCE_SCHOOL), draft("Sports day")) }
        }
    }

    @Test
    fun rejectNextWriteRejectsOnlyTheNextWrite() = runTest {
        val fake = providerOn(today)
        fake.rejectNextWrite("Event is locked")
        val error = runCatching { fake.create(conn, family, draft("Sleepover")) }.exceptionOrNull()
        assertThat(error).isInstanceOf(WriteRejectedException::class.java)
        assertThat(error?.message).isEqualTo("Event is locked")
        assertThat(fake.create(conn, family, draft("Sleepover")).title).isEqualTo("Sleepover")
    }

    @Test
    fun unreachableNextWriteFailsOnlyTheNextWrite() = runTest {
        val fake = providerOn(today)
        fake.unreachableNextWrite()
        assertThat(runCatching { fake.create(conn, family, draft("Sleepover")) }.exceptionOrNull())
            .isInstanceOf(UnreachableException::class.java)
        assertThat(fake.familyEvents().map { it.title }).doesNotContain("Sleepover")
        fake.create(conn, family, draft("Sleepover"))
        assertThat(fake.familyEvents().map { it.title }).contains("Sleepover")
    }
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :provider:calendar-fake:testDebugUnitTest`
Expected: compilation FAILS: `writer()` returns a `FakeCalendarProvider`, which isn't a `CalendarWriter`, and `tagSamples`, `rejectNextWrite`, `unreachableNextWrite`, `create`, `update` and `delete` are unresolved.

- [ ] **Step 4: Move the sample week onto the Family calendar with name tags**

Replace `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/SampleEvents.kt` with:
```kotlin
package uk.co.siland.culvery.provider.calendar_fake

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_FAMILY
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_SCHOOL

/**
 * The hand-off's week (screenshots/calendar-sheets/02-calendar-week-dark.png, durations from Culvery.dc.html),
 * relative to today, on the one shared "Family calendar" as Culvery would write it: each event tagged with who it
 * is for and who added it, by person name. [forSource] turns names into household ids; "Family" is always
 * "family". Plumber quote call has no tags, as if added from a phone. INSET day comes from the read-only school feed.
 */
internal object SampleEvents {
    private class Timed(
        val id: String,
        val source: String,
        val title: String,
        val day: Long,
        val start: LocalTime,
        val minutes: Long,
        val weekly: Boolean = false,
        val forName: String? = null,
        val byName: String? = null,
    )

    private class AllDay(
        val id: String,
        val source: String,
        val title: String,
        val day: Long,
        val days: Long = 1,
        val weekly: Boolean = false,
        val forName: String? = null,
        val byName: String? = null,
    )

    private fun t(hour: Int, minute: Int) = LocalTime.of(hour, minute)

    private val timed = listOf(
        Timed("school-run", SOURCE_FAMILY, "School run", 0, t(7, 45), 45, forName = "Sam", byName = "Sam"),
        Timed("boiler", SOURCE_FAMILY, "Boiler service", 0, t(10, 0), 60, forName = "Family", byName = "Alex"),
        Timed("plumber", SOURCE_FAMILY, "Plumber quote call", 0, t(13, 0), 30),
        Timed("swimming", SOURCE_FAMILY, "Swimming", 0, t(16, 0), 60, weekly = true, forName = "Mia", byName = "Sam"),
        Timed("dinner", SOURCE_FAMILY, "Dinner with Jo & Priya", 0, t(19, 30), 90, forName = "Alex", byName = "Alex"),
        Timed("office", SOURCE_FAMILY, "Office day", 1, t(9, 0), 480, forName = "Alex", byName = "Alex"),
        // Mia added this herself, so she may delete it.
        Timed("football", SOURCE_FAMILY, "Football", 1, t(18, 0), 60, forName = "Mia", byName = "Mia"),
        Timed("dentist", SOURCE_FAMILY, "Dentist", 2, t(12, 30), 60, forName = "Sam", byName = "Sam"),
        Timed("piano", SOURCE_FAMILY, "Piano", 3, t(15, 30), 60, weekly = true, forName = "Mia", byName = "Alex"),
        Timed("book-club", SOURCE_FAMILY, "Book club", 3, t(20, 0), 120, forName = "Sam", byName = "Sam"),
        Timed("pizza", SOURCE_FAMILY, "Pizza night", 4, t(19, 0), 240, forName = "Family", byName = "Mia"),
        Timed("parkrun", SOURCE_FAMILY, "Parkrun", 5, t(9, 30), 90, forName = "Alex", byName = "Alex"),
        Timed("party", SOURCE_FAMILY, "Birthday party", 5, t(14, 0), 180, forName = "Mia", byName = "Sam"),
        Timed("lunch", SOURCE_FAMILY, "Sunday lunch at Gran's", 6, t(12, 0), 180, forName = "Family", byName = "Alex"),
        // Outside a 15-day window: the contract suite's out-of-range fixture.
        Timed("school-trip", SOURCE_FAMILY, "School trip", 20, t(8, 30), 420, forName = "Mia", byName = "Alex"),
    )

    private val allDay = listOf(
        AllDay("bins", SOURCE_FAMILY, "Bin day", 2, weekly = true, forName = "Family", byName = "Alex"),
        AllDay("inset", SOURCE_SCHOOL, "INSET day — no school", 3),
        // After the visible week, so the week view matches the hand-off.
        AllDay("half-term", SOURCE_FAMILY, "Half term", 8, days = 3, forName = "Family", byName = "Alex"),
    )

    /** Day offsets of each occurrence: a weekly event runs from last week to three weeks ahead. */
    private fun occurrences(day: Long, weekly: Boolean): List<Long> =
        if (weekly) (-1..3).map { week -> day + 7L * week } else listOf(day)

    private fun tag(name: String?, idsByName: Map<String, String>): String? = when (name) {
        null -> null
        "Family" -> "family"
        else -> idsByName[name]
    }

    fun forSource(sourceId: String, today: LocalDate, zone: ZoneId, idsByName: Map<String, String> = emptyMap()): List<RemoteEvent> =
        buildList {
            timed.filter { it.source == sourceId }.forEach { e ->
                occurrences(e.day, e.weekly).forEach { offset ->
                    val date = today.plusDays(offset)
                    val start = date.atTime(e.start).atZone(zone).toInstant()
                    add(
                        RemoteEvent(
                            "${e.id}-$date",
                            e.title,
                            EventTime.Timed(start),
                            EventTime.Timed(start.plusSeconds(e.minutes * 60)),
                            recurring = e.weekly,
                            forPerson = tag(e.forName, idsByName),
                            createdBy = tag(e.byName, idsByName),
                        ),
                    )
                }
            }
            allDay.filter { it.source == sourceId }.forEach { e ->
                occurrences(e.day, e.weekly).forEach { offset ->
                    val date = today.plusDays(offset)
                    add(
                        RemoteEvent(
                            "${e.id}-$date",
                            e.title,
                            EventTime.AllDay(date),
                            EventTime.AllDay(date.plusDays(e.days)),
                            recurring = e.weekly,
                            forPerson = tag(e.forName, idsByName),
                            createdBy = tag(e.byName, idsByName),
                        ),
                    )
                }
            }
        }

    /** Whether the Family calendar sample with [remoteId] repeats; null when it isn't a sample. */
    fun familySampleRepeats(remoteId: String): Boolean? =
        (timed.filter { it.source == SOURCE_FAMILY }.map { it.id to it.weekly } +
            allDay.filter { it.source == SOURCE_FAMILY }.map { it.id to it.weekly })
            .firstOrNull { (id, _) -> remoteId.startsWith("$id-") }
            ?.second
}
```

- [ ] **Step 5: Make the fake a writer**

Replace `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt` with:
```kotlin
package uk.co.siland.culvery.provider.calendar_fake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.HhPillButton

/**
 * Debug-only sample data matching the design hand-off, generated relative to today so it never goes stale.
 * Writes to the "Family calendar" are kept in memory, so the fake forgets them when the app restarts.
 */
@Singleton
class FakeCalendarProvider(private val clock: Clock) : CalendarProvider, CalendarWriter {
    @Inject constructor() : this(Clock.systemUTC())

    override val descriptor = ProviderDescriptor(ID, "Sample calendar (debug)", "event", setOf(Feature.READ, Feature.WRITE))
    override val providerId = ID

    @Volatile private var failNext: Throwable? = null

    private val lock = Any()
    // Everything below is guarded by lock. Every change bumps version, so the next sync is a full replace.
    private var version = 0
    private var nextId = 0
    private var idsByName: Map<String, String> = emptyMap()
    private val created = linkedMapOf<String, RemoteEvent>()
    private val changed = mutableMapOf<String, RemoteEvent>()
    private val deleted = mutableSetOf<String>()
    private var rejectNext: String? = null
    private var unreachableNext = false

    /** For contract tests: the next sources() or sync() call throws [error]. */
    fun failNextWith(error: Throwable) {
        failNext = error
    }

    /** The next create, update or delete throws WriteRejectedException([message]). */
    fun rejectNextWrite(message: String) = synchronized(lock) { rejectNext = message }

    /** The next create, update or delete throws UnreachableException, as if the tablet were offline. */
    fun unreachableNextWrite() = synchronized(lock) { unreachableNext = true }

    /** Debug seed only: the household's ids for the sample people, by name ("Alex", "Sam", "Mia"). */
    fun tagSamples(idsByName: Map<String, String>) = synchronized(lock) {
        if (idsByName != this.idsByName) {
            this.idsByName = idsByName
            version++
        }
    }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HhPillButton(
                "Connect sample calendar",
                onClick = {
                    val id = existing?.id ?: UUID.randomUUID().toString()
                    onConnected(Connection(id, ID, "Sample calendar", emptyMap()))
                },
                primary = true,
            )
            HhPillButton("Cancel", onClick = onCancel)
        }
    }

    override suspend fun sources(conn: Connection): List<CalendarSource> {
        throwIfFailing()
        return SOURCES
    }

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        throwIfFailing()
        val today = LocalDate.now(clock.withZone(range.zone))
        val (current, events) = synchronized(lock) {
            // The cursor carries the date and the write version: the sample data rolls over daily, and writes show.
            val current = SyncCursor("v2:$today:$version")
            if (cursor == current) return SyncResult(emptyList(), emptyList(), current, fullReplace = false)
            val samples = SampleEvents.forSource(source.id, today, range.zone, idsByName)
                .filterNot { it.remoteId in deleted }
                .map { changed[it.remoteId] ?: it }
            val written = if (source.id == SOURCE_FAMILY) created.values.toList() else emptyList()
            current to samples + written
        }
        return SyncResult(events.filter { range.overlaps(it.start, it.end) }, emptyList(), current, fullReplace = true)
    }

    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEvent = write(source) {
        val event = RemoteEvent("written-${++nextId}", draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
        created[event.remoteId] = event
        event
    }

    override suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft): RemoteEvent =
        write(source) {
            val repeats = when {
                remoteId in created -> false
                remoteId in deleted -> null
                else -> SampleEvents.familySampleRepeats(remoteId)
            } ?: throw WriteRejectedException("That event no longer exists")
            if (repeats) throw WriteRejectedException("Repeating events can't be changed here")
            val event = RemoteEvent(remoteId, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
            if (remoteId in created) created[remoteId] = event else changed[remoteId] = event
            event
        }

    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) = write(source) {
        // Deleting something already gone succeeds (CalendarWriter contract).
        if (created.remove(remoteId) == null) deleted += remoteId
        changed.remove(remoteId)
        Unit
    }

    private inline fun <T> write(source: CalendarSource, block: () -> T): T = synchronized(lock) {
        if (unreachableNext) {
            unreachableNext = false
            throw UnreachableException("Sample calendar is offline")
        }
        rejectNext?.let {
            rejectNext = null
            throw WriteRejectedException(it)
        }
        if (source.id != SOURCE_FAMILY) throw WriteRejectedException("${source.name} can't be changed here")
        block().also { version++ }
    }

    private fun throwIfFailing() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    companion object {
        const val ID = "calendar.fake"
        const val SOURCE_ALEX = "fake-alex"
        const val SOURCE_SAM = "fake-sam"
        const val SOURCE_MIA = "fake-mia"
        const val SOURCE_FAMILY = "fake-family"
        const val SOURCE_SCHOOL = "fake-school"

        val SOURCES = listOf(
            CalendarSource(SOURCE_ALEX, "Alex", writable = false),
            CalendarSource(SOURCE_SAM, "Sam", writable = false),
            CalendarSource(SOURCE_MIA, "Mia", writable = false),
            CalendarSource(SOURCE_FAMILY, "Family calendar", writable = true),
            CalendarSource(SOURCE_SCHOOL, "School terms", writable = false),
        )
    }
}
```

Replace `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/di/FakeCalendarModule.kt` with:
```kotlin
package uk.co.siland.culvery.provider.calendar_fake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class FakeCalendarModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: FakeCalendarProvider): CalendarProvider

    @Binds
    @IntoSet
    abstract fun writer(impl: FakeCalendarProvider): CalendarWriter
}
```

- [ ] **Step 6: Run the fake's tests to see them pass**

Run: `./gradlew :provider:calendar-fake:testDebugUnitTest`
Expected: PASS. `FakeCalendarProviderContractTest` now runs all 16 checks. None is skipped, because the fake declares WRITE and gives a writer and a writable source.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. `:app`'s `DebugSeedTest` still passes: the seed maps the same five source ids. The person sources are now empty; Task 12 tags the week and makes the Family calendar the master.

- [ ] **Step 8: Commit**

```bash
git add provider/calendar-fake
git commit -m "Let the sample calendar write, with reject and offline switches and a tagged week"
```

---

### Task 7: Sync loop and outbox drain — `requestSync`, one pass at a time, in-order delivery, backoff, rejection toasts

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoop.kt`
- Modify (replace): `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedProvider.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedWriter.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/RecordingToaster.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt` (modify)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoopTest.kt` (modify)

**Interfaces:**
- Consumes:
  - the store's outbox API, `eventNow`, `applyAccepted`/`applyDeleted` and `source` (Task 1)
  - `CalendarWriter`, `WriteRejectedException` (Task 5)
  - `Toaster` (Task 2)
- Produces (in `Writes.kt`, shared with the editor in Task 8):
  - `val OUTBOX_BACKOFF_MS: List<Long>` = 30 s, 1 min, 2 min, 5 min; `internal fun backoffMillis(attempts: Int): Long`
  - `const val OUTBOX_MAX_AGE_MS = 48 * 60 * 60_000L`
  - `internal const val EVENT_GONE = "The event no longer exists"`
  - `internal sealed interface WriteOutcome { Accepted(event: RemoteEvent?); Rejected(message: String); Retry(blocksConnection: Boolean) }`
  - `internal suspend fun callWriter(io: CoroutineContext, timeoutMillis: Long, call: suspend () -> RemoteEvent?): WriteOutcome`
  - `fun couldNotSave(label: String, reason: String?): String` ("Couldn't save to {label} — {reason}", or "Couldn't save to {label}") and `internal fun couldNotSaveAll(label: String, reasons: List<String?>): String` ("Couldn't save {n} changes to {label}" for more than one)
  - `internal fun assignDraft(event: StoredEvent, forPerson: String?): EventDraft`
  - `internal object SilentToaster : Toaster`
- Produces (the engine):
  - `CalendarSync`:
    - `@Inject constructor(store, providers, writers: Set<CalendarWriter>, toaster: Toaster, zone, clock)`
    - internal constructor `(store, providers, zone, clock, io, timeoutMillis, writers = emptySet(), toaster: Toaster = SilentToaster)`
    - `syncAll()` drains the outbox first (a drain failure is logged and the sync still runs), and runs one pass at a time
  - `@Singleton CalendarSyncLoop`:
    - `fun requestSync()`
    - internal constructor `(syncAll, connectionIds, scope, intervalMillis = SYNC_INTERVAL_MS, untilNextRetry: suspend () -> Long? = { null })`
    - `@Inject constructor(sync, store, clock: WallClock, @ApplicationScope scope)`
  - Test helpers:
    - `ScriptedProvider(id, sourceList, features: Set<Feature> = setOf(Feature.READ))`, with `var gate: CompletableDeferred<Unit>?`, `val entered: CompletableDeferred<Unit>` and `val maxInFlight: Int`
    - `ScriptedWriter(providerId)`, with `calls: MutableList<String>`, `drafts: MutableList<EventDraft>`, `failWith`, `gate` and `entered`
    - `RecordingToaster`

- [ ] **Step 1: Add the test helpers**

Replace `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedProvider.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

internal data class SyncCall(val connectionId: String, val sourceId: String, val range: DateRange, val cursor: SyncCursor?)

/** An in-test provider (the capability may not depend on :provider:calendar-fake, even in tests). */
internal class ScriptedProvider(
    id: String,
    var sourceList: List<CalendarSource> = emptyList(),
    features: Set<Feature> = setOf(Feature.READ),
) : CalendarProvider {
    override val descriptor = ProviderDescriptor(id, id, "event", features)
    val calls = mutableListOf<SyncCall>()
    var failWith: Throwable? = null
    /** Per-source failures, by source id; they win over [failWith]. */
    var failFor: Map<String, Throwable> = emptyMap()
    /** Never returns, like a stalled socket. */
    var hang = false
    /** When set, sync waits for it: a slow network the test releases. */
    var gate: CompletableDeferred<Unit>? = null
    /** Completes when the first sync call starts. */
    val entered = CompletableDeferred<Unit>()
    var events: (CalendarSource) -> List<RemoteEvent> = { emptyList() }

    private var inFlight = 0

    /** The most sync calls that were running at once. */
    var maxInFlight = 0
        private set

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
    }

    override suspend fun sources(conn: Connection): List<CalendarSource> = sourceList

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        // Some tests run the engine on Dispatchers.Default, so the bookkeeping is locked.
        synchronized(this) {
            calls += SyncCall(conn.id, source.id, range, cursor)
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
        }
        entered.complete(Unit)
        try {
            if (hang) awaitCancellation()
            gate?.await()
            (failFor[source.id] ?: failWith)?.let { throw it }
            return SyncResult(events(source), emptyList(), SyncCursor("k${calls.size}"), fullReplace = cursor == null)
        } finally {
            synchronized(this) { inFlight-- }
        }
    }
}

/** A real TimeoutCancellationException, as a provider's own internal withTimeout would throw. */
internal suspend fun timeoutCancellation(): TimeoutCancellationException =
    runCatching { withTimeout(1) { awaitCancellation() } }.exceptionOrNull() as TimeoutCancellationException
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedWriter.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import kotlinx.coroutines.CompletableDeferred
import uk.co.siland.culvery.core.plugin.Connection

/** An in-test writer. [calls] reads "create:<title>", "update:<remoteId>" or "delete:<remoteId>". */
internal class ScriptedWriter(override val providerId: String) : CalendarWriter {
    val calls = mutableListOf<String>()
    /** The drafts sent to create and update, in order. */
    val drafts = mutableListOf<EventDraft>()
    var failWith: Throwable? = null
    /** When set, each write waits for it: a slow network the test releases. */
    var gate: CompletableDeferred<Unit>? = null
    /** Completes when the first write starts. */
    val entered = CompletableDeferred<Unit>()
    private var next = 0

    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEvent {
        record("create:${draft.title}", draft)
        gate?.await()
        failWith?.let { throw it }
        return RemoteEvent("new-${++next}", draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
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

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/RecordingToaster.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.plugin.Toaster

class RecordingToaster : Toaster {
    val messages = mutableListOf<String>()

    override fun show(message: String, icon: String) {
        messages += message
    }
}
```

- [ ] **Step 2: Write the failing drain and cancellation tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`:

1. Add imports:
```kotlin
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.calendar.db.OutboxEntity
```
and put `@OptIn(ExperimentalCoroutinesApi::class)` on the class if it isn't there already.

2. Add these members at the end of the class:
```kotlin
    private val w = ScriptedWriter("calendar.a")
    private val toaster = RecordingToaster()

    private suspend fun writingEngine(io: CoroutineContext = EmptyCoroutineContext, timeoutMillis: Long = 1_000): CalendarSync {
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        return CalendarSync(
            store, setOf(a, b), HouseholdZone(household), clock, io, timeoutMillis,
            writers = setOf(w), toaster = toaster,
        )
    }

    private fun swimDraft(forPerson: String?) = EventDraft("Swim", swim().start, swim().end, forPerson, "alex-id")

    private suspend fun queue(
        kind: ChangeKind,
        remoteId: String? = "swim",
        draft: EventDraft? = swimDraft("sam-id"),
        attempts: Int = 1,
        sourceId: String = "s1",
        next: Instant = now,
        created: Instant = now,
    ): Long = store.enqueue(PendingChange(0, "c1", sourceId, remoteId, kind, draft, attempts, next.toEpochMilli(), created.toEpochMilli()))

    @Test
    fun drainDeliversADueDeleteAndCompletesIt() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = writingEngine()
        sync.syncAll()
        queue(ChangeKind.DELETE, draft = null)
        a.events = { emptyList() }
        sync.syncAll()
        assertThat(w.calls).containsExactly("delete:swim")
        assertThat(store.pendingNow()).isEmpty()
        assertThat(cachedTitles()).isEmpty()
    }

    @Test
    fun drainAppliesAnAcceptedUpdateEvenWhenTheReadFails() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = writingEngine()
        sync.syncAll()
        queue(ChangeKind.UPDATE)
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        assertThat(w.calls).containsExactly("update:swim")
        assertThat(store.eventNow(EventRef("c1", "s1", "swim"))!!.forPerson).isEqualTo("sam-id")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun drainDeliversACreateAndMirrorsIt() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"))
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        assertThat(w.calls).containsExactly("create:Swim")
        assertThat(store.eventNow(EventRef("c1", "s1", "new-1"))!!.forPerson).isEqualTo("mia-id")
    }

    @Test
    fun queuedChangesDrainInTheOrderTheyWereMade() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.UPDATE)
        queue(ChangeKind.DELETE, draft = null)
        sync.syncAll()
        assertThat(w.calls).containsExactly("update:swim", "delete:swim").inOrder()
    }

    @Test
    fun aLaterChangeWaitsForAnEarlierOneInBackoff() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.UPDATE, next = now.plusSeconds(30))
        queue(ChangeKind.DELETE, draft = null)
        sync.syncAll()
        assertThat(w.calls).isEmpty()
        assertThat(store.pendingNow()).hasSize(2)
        now = now.plusSeconds(30)
        sync.syncAll()
        assertThat(w.calls).containsExactly("update:swim", "delete:swim").inOrder()
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aQueuedAssignIsAppliedToTheEventAsItIsNow() = runTest {
        connect("c1", "calendar.a", s1)
        // Renamed on a phone after the assign was queued with the old title.
        a.events = { listOf(swim().copy(title = "Swim club")) }
        val sync = writingEngine()
        sync.syncAll()
        queue(ChangeKind.ASSIGN, draft = swimDraft("sam-id"))
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        val sent = w.drafts.single()
        assertThat(listOf(sent.title, sent.forPerson)).containsExactly("Swim club", "sam-id").inOrder()
        assertThat(store.eventNow(EventRef("c1", "s1", "swim"))!!.let { it.title to it.forPerson }).isEqualTo("Swim club" to "sam-id")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aQueuedAssignForAnEventThatIsGoneIsDroppedWithAToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.ASSIGN)
        sync.syncAll()
        assertThat(w.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1 — The event no longer exists")
    }

    @Test
    fun drainRejectionDropsTheChangeAndToastsWhy() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = writingEngine()
        sync.syncAll()
        queue(ChangeKind.DELETE, draft = null)
        w.failWith = WriteRejectedException("Event is locked")
        sync.syncAll()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(cachedTitles()).containsExactly("Swim")
        assertThat(toaster.messages).containsExactly("Couldn't save to C1 — Event is locked")
    }

    @Test
    fun severalRejectionsInOnePassMakeOneToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, remoteId = "swim", draft = null)
        queue(ChangeKind.DELETE, remoteId = "walk", draft = null)
        w.failWith = WriteRejectedException("Event is locked")
        sync.syncAll()
        assertThat(w.calls).containsExactly("delete:swim", "delete:walk").inOrder()
        assertThat(toaster.messages).containsExactly("Couldn't save 2 changes to C1")
    }

    @Test
    fun anUnreachableDrainRetriesWithBackoff() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null, attempts = 1)
        w.failWith = UnreachableException("offline")
        sync.syncAll()
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis })
            .isEqualTo(2 to now.toEpochMilli() + 60_000)
        sync.syncAll()
        assertThat(w.calls).hasSize(1)
        now = now.plusSeconds(60)
        sync.syncAll()
        assertThat(w.calls).hasSize(2)
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis })
            .isEqualTo(3 to now.toEpochMilli() + 120_000)
    }

    @Test
    fun anUnreachableConnectionIsNotTriedAgainInThePass() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, remoteId = "swim", draft = null, attempts = 1)
        queue(ChangeKind.DELETE, remoteId = "walk", draft = null, attempts = 1)
        w.failWith = UnreachableException("offline")
        sync.syncAll()
        assertThat(w.calls).containsExactly("delete:swim")
        assertThat(store.pendingNow().map { it.attempts to it.nextAttemptMillis })
            .containsExactly(2 to now.toEpochMilli() + 60_000, 2 to now.toEpochMilli() + 60_000)
    }

    @Test
    fun backoffIsThirtySecondsOneMinuteTwoMinutesThenFive() {
        assertThat((1..6).map(::backoffMillis))
            .containsExactly(30_000L, 60_000L, 120_000L, 300_000L, 300_000L, 300_000L).inOrder()
    }

    @Test
    fun needsSignInRetriesWithTheNormalBackoff() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null, attempts = 1)
        w.failWith = NeedsSignInException("expired")
        a.failWith = NeedsSignInException("expired")
        sync.syncAll()
        // The pass's own read flags the connection; the failed write only reschedules.
        assertThat(health("c1")).isEqualTo(ConnectionHealth.NeedsSignIn)
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis })
            .isEqualTo(2 to now.toEpochMilli() + 60_000)
        now = now.plusSeconds(60)
        sync.syncAll()
        assertThat(w.calls).hasSize(2)
    }

    @Test
    fun aChangeUnsentForTwoDaysIsDroppedWithAToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null, created = now.minusMillis(OUTBOX_MAX_AGE_MS + 1))
        sync.syncAll()
        assertThat(w.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1")
    }

    @Test
    fun aChangeWithNowhereToGoIsDroppedWithAToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null, sourceId = "gone")
        sync.syncAll()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(w.calls).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1")
    }

    @Test
    fun anUnreadableQueuedRowIsDroppedAndTheSyncStillRuns() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = writingEngine()
        calendar.calendarDao().insertOutbox(
            OutboxEntity(
                connectionId = "c1", sourceId = "s1", remoteId = "swim", kind = "BOGUS", draftJson = null,
                attempts = 0, nextAttemptMillis = 0, createdMillis = 0,
            ),
        )
        sync.syncAll()
        assertThat(cachedTitles()).containsExactly("Swim")
        assertThat(calendar.calendarDao().outboxNow()).isEmpty()
    }

    @Test
    fun aPassWaitsForTheOneBeforeIt() = runTest {
        connect("c1", "calendar.a", s1)
        // Real threads and a real timeout: runTest must not skip virtual time past the held-up pass.
        val sync = writingEngine(io = Dispatchers.Default, timeoutMillis = PROVIDER_TIMEOUT_MS)
        a.gate = CompletableDeferred()
        val first = launch { sync.syncAll() }
        a.entered.await()
        val second = launch { sync.syncAll() }
        // Real time for the second pass to reach the provider, if nothing held it back.
        withContext(Dispatchers.Default) { delay(200) }
        assertThat(a.calls).hasSize(1)
        a.gate?.complete(Unit)
        first.join()
        second.join()
        assertThat(a.calls).hasSize(2)
        assertThat(a.maxInFlight).isEqualTo(1)
    }

    @Test
    fun realCancellationPropagatesAndLeavesTheConnectionAlone() = runTest {
        connect("c1", "calendar.a", s1)
        a.hang = true
        val sync = engine()
        val job = launch { sync.syncAll() }
        a.entered.await()
        job.cancelAndJoin()
        assertThat(job.isCancelled).isTrue()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
        assertThat(store.connectionsNow().single().lastSyncMillis).isNull()
        a.hang = false
        sync.syncAll()
        assertThat(store.connectionsNow().single().lastSyncMillis).isEqualTo(now.toEpochMilli())
    }
```

- [ ] **Step 3: Write the failing loop tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoopTest.kt`, add imports:
```kotlin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
```
and add at the end of the class:
```kotlin
    @Test
    fun requestSyncRunsAPassNow() = runTest {
        var count = 0
        val loop = CalendarSyncLoop({ count++ }, MutableStateFlow(listOf("c1")), backgroundScope)
        loop.start()
        runCurrent()
        loop.requestSync()
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun requestsDuringAPassCoalesceIntoOneMorePass() = runTest {
        val gate = CompletableDeferred<Unit>()
        var count = 0
        val loop = CalendarSyncLoop({ count++; if (count == 1) gate.await() }, MutableStateFlow(listOf("c1")), backgroundScope)
        loop.start()
        runCurrent()
        repeat(3) { loop.requestSync() }
        runCurrent()
        assertThat(count).isEqualTo(1)
        gate.complete(Unit)
        runCurrent()
        assertThat(count).isEqualTo(2)
        advanceTimeBy(SYNC_INTERVAL_MS - 1)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aConnectionAddedMidPassGetsAPassOfItsOwnWithoutCancellingTheRunningOne() = runTest {
        val gate = CompletableDeferred<Unit>()
        var started = 0
        var finished = 0
        val ids = MutableStateFlow(listOf("c1"))
        val syncAll: suspend () -> Unit = {
            started++
            if (started == 1) gate.await()
            finished++
        }
        CalendarSyncLoop(syncAll, ids, backgroundScope).start()
        runCurrent()
        ids.value = listOf("c1", "c2")
        runCurrent()
        assertThat(started).isEqualTo(1)
        gate.complete(Unit)
        runCurrent()
        assertThat(started).isEqualTo(2)
        assertThat(finished).isEqualTo(2)
    }

    @Test
    fun aDueRetryWakesTheLoopBeforeTheFiveMinuteTick() = runTest {
        var count = 0
        CalendarSyncLoop({ count++ }, MutableStateFlow(listOf("c1")), backgroundScope, untilNextRetry = { 30_000L }).start()
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun anOverdueRetryStillWaitsASecond() = runTest {
        var count = 0
        CalendarSyncLoop({ count++ }, MutableStateFlow(listOf("c1")), backgroundScope, untilNextRetry = { -5_000L }).start()
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertThat(count).isEqualTo(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun cancellingTheScopeStopsTheLoopMidPass() = runTest {
        val loopScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val entered = CompletableDeferred<Unit>()
        var started = 0
        var retryReads = 0
        var sawCancellation = false
        val syncAll: suspend () -> Unit = {
            started++
            entered.complete(Unit)
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                sawCancellation = true
                throw e
            }
        }
        CalendarSyncLoop(syncAll, MutableStateFlow(listOf("c1")), loopScope, untilNextRetry = { retryReads++; null }).start()
        entered.await()
        loopScope.cancel()
        runCurrent()
        assertThat(sawCancellation).isTrue()
        advanceTimeBy(SYNC_INTERVAL_MS * 2)
        runCurrent()
        assertThat(started).isEqualTo(1)
        // A cancelled loop never goes on to read the outbox for its next wait.
        assertThat(retryReads).isEqualTo(0)
    }
```

- [ ] **Step 4: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSyncTest*" --tests "*CalendarSyncLoopTest*"`
Expected: compilation FAILS: `backoffMillis`, `OUTBOX_MAX_AGE_MS`, `ChangeKind.ASSIGN` in use, `requestSync`, and the `writers`, `toaster` and `untilNextRetry` parameters are unresolved.

- [ ] **Step 5: Add the shared write helpers**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Writes.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.Toaster

/** Waits after the nth failed attempt: 30 s, 1 min, 2 min, then 5 min. */
val OUTBOX_BACKOFF_MS: List<Long> = listOf(30_000L, 60_000L, 120_000L, 300_000L)

internal fun backoffMillis(attempts: Int): Long = OUTBOX_BACKOFF_MS[(attempts - 1).coerceIn(0, OUTBOX_BACKOFF_MS.lastIndex)]

/** A queued change still unsent this long after it was made is dropped, with a toast. */
const val OUTBOX_MAX_AGE_MS = 48 * 60 * 60_000L

internal const val EVENT_GONE = "The event no longer exists"

/** What one writer call came to. */
internal sealed interface WriteOutcome {
    /** The provider took the change. [event] is what it now holds; null for a delete. */
    data class Accepted(val event: RemoteEvent?) : WriteOutcome

    /** The provider refused the change for good. */
    data class Rejected(val message: String) : WriteOutcome

    /** Try again later. [blocksConnection]: the provider didn't answer, so its other changes should wait too. */
    data class Retry(val blocksConnection: Boolean) : WriteOutcome
}

/**
 * The one way the engine calls a writer, for the editor and the outbox drain alike: on [io], under
 * [timeoutMillis], with every failure sorted into a [WriteOutcome]. A real cancellation of the caller still
 * propagates.
 */
internal suspend fun callWriter(io: CoroutineContext, timeoutMillis: Long, call: suspend () -> RemoteEvent?): WriteOutcome =
    try {
        WriteOutcome.Accepted(withContext(io) { withTimeout(timeoutMillis) { call() } })
    } catch (e: WriteRejectedException) {
        WriteOutcome.Rejected(e.message ?: "the calendar refused the change")
    } catch (e: TimeoutCancellationException) {
        WriteOutcome.Retry(blocksConnection = true)
    } catch (e: CancellationException) {
        // Rethrows if the caller was really cancelled; otherwise the writer leaked a stray cancellation.
        currentCoroutineContext().ensureActive()
        WriteOutcome.Retry(blocksConnection = true)
    } catch (e: NeedsSignInException) {
        WriteOutcome.Retry(blocksConnection = true)
    } catch (e: UnreachableException) {
        WriteOutcome.Retry(blocksConnection = true)
    } catch (e: Exception) {
        Log.w(TAG, "A calendar write failed unexpectedly; it will be retried", e)
        WriteOutcome.Retry(blocksConnection = false)
    }

/** "Couldn't save to {label} — {reason}", or just "Couldn't save to {label}" when there is no reason. */
fun couldNotSave(label: String, reason: String?): String =
    if (reason == null) "Couldn't save to $label" else "Couldn't save to $label — $reason"

/** One toast for everything a drain pass dropped for [label]; [reasons] has one entry per change. */
internal fun couldNotSaveAll(label: String, reasons: List<String?>): String =
    if (reasons.size == 1) couldNotSave(label, reasons.single()) else "Couldn't save ${reasons.size} changes to $label"

/** An assign as it is sent: the event's current title, times and creator, with the new person. */
internal fun assignDraft(event: StoredEvent, forPerson: String?): EventDraft =
    EventDraft(event.title, event.start, event.end, forPerson, event.createdBy)

/** For an engine built without a toaster, in tests that don't look at toasts. */
internal object SilentToaster : Toaster {
    override fun show(message: String, icon: String) = Unit
}

private const val TAG = "CalendarWrites"
```

- [ ] **Step 6: Drain the outbox at the start of each pass, one pass at a time**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

const val SYNC_PAST_DAYS = 1L
const val SYNC_FUTURE_DAYS = 14L
const val PROVIDER_TIMEOUT_MS = 60_000L

@Singleton
class CalendarSync internal constructor(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    private val zone: HouseholdZone,
    private val clock: WallClock,
    private val io: CoroutineContext,
    private val timeoutMillis: Long,
    private val writers: Set<@JvmSuppressWildcards CalendarWriter> = emptySet(),
    private val toaster: Toaster = SilentToaster,
) {
    @Inject
    constructor(
        store: CalendarStore,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        writers: Set<@JvmSuppressWildcards CalendarWriter>,
        toaster: Toaster,
        zone: HouseholdZone,
        clock: WallClock,
    ) : this(store, providers, zone, clock, Dispatchers.IO, PROVIDER_TIMEOUT_MS, writers, toaster)

    // The loop's timer, a connection change and requestSync may all ask at once: one pass writes at a time.
    private val passLock = Mutex()

    /**
     * Delivers queued changes, then syncs each connection, and each source within it, independently. A
     * failure only flags that connection (with its worst source's health) and never clears its cache.
     */
    suspend fun syncAll() = passLock.withLock {
        val window = currentWindow()
        val connections = store.connectionsNow()
        try {
            drainOutbox(connections, window.zone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The changes stay queued for the next pass; the sync must still run.
            Log.w(TAG, "The outbox drain failed; syncing anyway", e)
        }
        connections.forEach { sync(it.connection, window) }
    }

    private suspend fun currentWindow(): DateRange {
        val z = zone.current()
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(z).toLocalDate()
        return DateRange(today.minusDays(SYNC_PAST_DAYS), today.plusDays(SYNC_FUTURE_DAYS + 1), z)
    }

    /**
     * Delivers queued changes in the order they were made. An event's later changes wait while an earlier one is
     * waiting or has just failed, so they can't land first and be undone. Once a connection fails to answer, its
     * other changes wait for their next attempt instead of each costing a timeout. Changes nothing can deliver,
     * changes the provider refuses, and changes older than [OUTBOX_MAX_AGE_MS] are dropped, with one toast per
     * connection.
     */
    private suspend fun drainOutbox(connections: List<StoredConnection>, zone: ZoneId) {
        val now = clock.nowMillis()
        val byId = connections.associateBy { it.connection.id }
        val blockedRefs = mutableSetOf<EventRef>()
        val blockedConnections = mutableSetOf<String>()
        val dropped = linkedMapOf<String, MutableList<String?>>()
        for (change in store.pendingNow()) {
            val ref = change.ref
            if (ref != null && ref in blockedRefs) continue
            val stored = byId[change.connectionId]
            val label = stored?.connection?.label ?: REMOVED_CALENDAR
            if (now - change.createdMillis > OUTBOX_MAX_AGE_MS) {
                Log.w(TAG, "Dropping a queued ${change.kind} for ${change.connectionId}: unsent for 48 hours")
                store.dropChange(change.id)
                dropped.getOrPut(label) { mutableListOf() }.add(null)
                continue
            }
            if (change.nextAttemptMillis > now) {
                ref?.let(blockedRefs::add)
                continue
            }
            if (change.connectionId in blockedConnections) {
                retryLater(change, now)
                ref?.let(blockedRefs::add)
                continue
            }
            val source = store.source(change.connectionId, change.sourceId)
            val writer = stored?.let { s -> writers.firstOrNull { it.providerId == s.connection.providerId } }
            if (stored == null || source == null || writer == null) {
                Log.w(TAG, "Dropping a queued ${change.kind} for ${change.connectionId}/${change.sourceId}: nothing can deliver it")
                store.dropChange(change.id)
                dropped.getOrPut(label) { mutableListOf() }.add(null)
                continue
            }
            when (val outcome = deliver(change, stored.connection, source.source, writer, zone)) {
                is WriteOutcome.Accepted -> Unit
                is WriteOutcome.Rejected -> {
                    store.dropChange(change.id)
                    dropped.getOrPut(label) { mutableListOf() }.add(outcome.message)
                }
                is WriteOutcome.Retry -> {
                    retryLater(change, now)
                    ref?.let(blockedRefs::add)
                    if (outcome.blocksConnection) blockedConnections += change.connectionId
                }
            }
        }
        dropped.forEach { (label, reasons) -> toaster.show(couldNotSaveAll(label, reasons)) }
    }

    /** Sends one change and, when the provider accepts it, applies the result to the mirror and completes it. */
    private suspend fun deliver(
        change: PendingChange,
        conn: Connection,
        source: CalendarSource,
        writer: CalendarWriter,
        zone: ZoneId,
    ): WriteOutcome {
        val ref = change.ref
        val outcome = when (change.kind) {
            ChangeKind.CREATE -> callWriter(io, timeoutMillis) { writer.create(conn, source, requireNotNull(change.draft)) }
            ChangeKind.UPDATE -> callWriter(io, timeoutMillis) {
                writer.update(conn, source, requireNotNull(ref).remoteId, requireNotNull(change.draft))
            }
            ChangeKind.ASSIGN -> {
                // Sent as the event is now, so a title or time changed elsewhere since it was queued is kept.
                val current = ref?.let { store.eventNow(it) } ?: return WriteOutcome.Rejected(EVENT_GONE)
                val draft = assignDraft(current, change.draft?.forPerson)
                callWriter(io, timeoutMillis) { writer.update(conn, source, current.remoteId, draft) }
            }
            ChangeKind.DELETE -> callWriter(io, timeoutMillis) {
                writer.delete(conn, source, requireNotNull(ref).remoteId)
                null
            }
        }
        if (outcome is WriteOutcome.Accepted) {
            val event = outcome.event
            if (event == null) {
                store.applyDeleted(requireNotNull(ref), completing = change.id)
            } else {
                store.applyAccepted(conn.id, source.id, event, zone, completing = change.id)
            }
        }
        return outcome
    }

    private suspend fun retryLater(change: PendingChange, now: Long) {
        val attempts = change.attempts + 1
        store.reschedule(change.id, attempts, now + backoffMillis(attempts))
    }

    private suspend fun sync(conn: Connection, window: DateRange) {
        val provider = providers.firstOrNull { it.descriptor.id == conn.providerId }
        if (provider == null) {
            store.setHealth(conn.id, ConnectionHealth.Error("Provider not installed"))
            return
        }
        var worst: ConnectionHealth = ConnectionHealth.Ok
        for (stored in store.visibleSourcesFor(conn.id)) {
            val health = syncSource(provider, conn, stored.source, window)
            if (health.severity() > worst.severity()) worst = health
        }
        if (worst == ConnectionHealth.Ok) {
            store.markSynced(conn.id, clock.nowMillis())
        } else {
            store.setHealth(conn.id, worst)
        }
    }

    private suspend fun syncSource(
        provider: CalendarProvider,
        conn: Connection,
        source: CalendarSource,
        window: DateRange,
    ): ConnectionHealth =
        try {
            val cursor = store.cursor(conn.id, source.id, window)
            val result = callProvider { provider.sync(conn, source, window, cursor) }
            store.applySync(conn.id, source.id, window, result)
            ConnectionHealth.Ok
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "${conn.label} / ${source.name}: timed out", e)
            ConnectionHealth.Unreachable
        } catch (e: CancellationException) {
            // Rethrows if this sync was really cancelled; otherwise the provider leaked a stray cancellation.
            currentCoroutineContext().ensureActive()
            ConnectionHealth.Unreachable
        } catch (e: NeedsSignInException) {
            ConnectionHealth.NeedsSignIn
        } catch (e: UnreachableException) {
            ConnectionHealth.Unreachable
        } catch (e: Exception) {
            ConnectionHealth.Error(e.message ?: e.javaClass.simpleName)
        }

    /** Every provider read goes through here; writes go through callWriter. */
    private suspend fun <T> callProvider(block: suspend () -> T): T =
        withContext(io) { withTimeout(timeoutMillis) { block() } }

    private fun ConnectionHealth.severity(): Int = when (this) {
        ConnectionHealth.NeedsSignIn -> 3
        ConnectionHealth.Unreachable -> 2
        is ConnectionHealth.Error -> 1
        ConnectionHealth.Ok -> 0
    }

    private companion object {
        const val TAG = "CalendarSync"

        /** The toast's label for a change whose connection has been removed. */
        const val REMOVED_CALENDAR = "a removed calendar"
    }
}
```

- [ ] **Step 7: Give the loop `requestSync` and an early wake for due retries**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoop.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.WallClock

const val SYNC_INTERVAL_MS = 5 * 60_000L

/** The shortest wait between passes, so an overdue queued change can't spin the loop. */
const val MIN_PASS_GAP_MS = 1_000L

/**
 * Runs a pass (the outbox drain, then a sync) as soon as the first connection list arrives, whenever a
 * connection is added or removed, on [requestSync], when a queued change falls due, and otherwise every
 * [intervalMillis]. A trigger that arrives mid-pass runs one more pass afterwards; it never cancels the running
 * one. [untilNextRetry] is the time until the earliest queued change is due, or null when nothing is queued.
 */
@Singleton
class CalendarSyncLoop internal constructor(
    private val syncAll: suspend () -> Unit,
    private val connectionIds: Flow<List<String>>,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = SYNC_INTERVAL_MS,
    private val untilNextRetry: suspend () -> Long? = { null },
) : Startable {
    @Inject
    constructor(sync: CalendarSync, store: CalendarStore, clock: WallClock, @ApplicationScope scope: CoroutineScope) :
        this(
            sync::syncAll,
            store.connectionIds(),
            scope,
            SYNC_INTERVAL_MS,
            { store.nextAttemptMillis()?.let { it - clock.nowMillis() } },
        )

    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Runs a pass as soon as possible. Requests made during a pass add up to one more pass. */
    fun requestSync() {
        wake.trySend(Unit)
    }

    override fun start() {
        scope.launch {
            // Every list, the first included, asks for a pass, so a connection added before this collector
            // started is never missed.
            launch { connectionIds.distinctUntilChanged().collect { wake.trySend(Unit) } }
            // The first pass waits only for the first connection list.
            var wait = intervalMillis
            while (true) {
                withTimeoutOrNull(wait) { wake.receive() }
                runPass()
                wait = nextWait()
            }
        }
    }

    private suspend fun runPass() {
        try {
            syncAll()
        } catch (e: CancellationException) {
            // Stops the loop only if it was really cancelled; a stray one (an internal timeout) must not.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Calendar sync was cancelled internally", e)
        } catch (e: Exception) {
            Log.w(TAG, "Calendar sync failed", e)
        }
    }

    private suspend fun nextWait(): Long {
        val retry = try {
            untilNextRetry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the outbox; waiting the full interval", e)
            null
        }
        return (retry ?: intervalMillis).coerceIn(MIN_PASS_GAP_MS, intervalMillis)
    }

    private companion object {
        const val TAG = "CalendarSync"
    }
}
```

`CalendarModule` needs no change: `CalendarSync` gets `Set<CalendarWriter>` from Task 5's `@Multibinds` and `Toaster` from `:app`.

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS. The five existing loop tests pass unchanged: the first connection list starts the first pass at once, and a new connection still triggers a pass. The existing sync tests pass unchanged too: `engine()` builds `CalendarSync` with no writers and the silent toaster.

- [ ] **Step 9: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. Hilt now builds `CalendarSync` with `Set<CalendarWriter>` (empty in release, the fake in debug) and the app's `Toaster`. It also injects `WallClock` into the singleton loop.

- [ ] **Step 10: Commit**

```bash
git add capability/calendar
git commit -m "Drain the calendar outbox before each sync, in order and one pass at a time, with requestSync, backoff and rejection toasts"
```

---

### Task 8: `CalendarEditor` and `CalendarPermissionSource`

**Files:**
- Modify: `capability/calendar/build.gradle.kts` (add `:core:access`)
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarPermissions.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Editability.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/TestAccess.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarPermissionsTest.kt` (create)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt` (create)

**Interfaces:**
- Consumes:
  - `AccessControl.authorise(…, reason, allow, refusal)`, `PinReason`, `Refusal`, `Identified` (Task 3)
  - `DefaultAccessControl`, `PermissionRegistry`, `PinManager`, `PinHasher`, `LockoutStore`, `PinPromptController`, `CorePermissionSource` (tests)
  - the store API and `ChangeKind.ASSIGN` (Task 1), `CalendarWriter` (Task 5), `Toaster` (Task 2)
  - `callWriter`, `WriteOutcome`, `backoffMillis`, `couldNotSave`, `assignDraft`, `CalendarSyncLoop.requestSync` (Task 7)
- Produces:
  - `object CalendarPermissions`, with the constants:
    - `CREATE = "calendar.event.create"`
    - `CREATE_SELF = "calendar.event.create.self"`
    - `EDIT = "calendar.event.edit"`
    - `EDIT_OWN = "calendar.event.edit.own"`
    - `ASSIGN = "calendar.event.assign"`
  - `class CalendarPermissionSource @Inject constructor() : PermissionSource`, bound `@IntoSet`
  - `internal fun mayChange(granted: Set<String>, who: Identified, createdBy: String?): Boolean`
  - `fun cannotChangeOthers(name: String): String` ("{name} can only change events they created.")
  - `const val ASK_AN_ADULT = "Ask an adult to assign this event."`
  - `enum class ReadOnlyReason { OtherCalendar, Recurring }` and `internal fun readOnlyReason(event: StoredEvent, source: StoredSource?, hasWriter: Boolean): ReadOnlyReason?`
  - `const val WRITE_ATTEMPT_MS = 10_000L`
  - `const val EVENT_DELETED = "Event deleted"`
  - `sealed interface EditResult`, with the cases:
    - `Done`, `Queued` and `Cancelled` (objects)
    - `Rejected(message: String)`
    - `NotEditable` (object)
  - `@Singleton class CalendarEditor`:
    - `suspend fun mayDelete(ref: EventRef): Boolean`
    - `suspend fun delete(ref: EventRef): EditResult`: toasts `EVENT_DELETED` when Done or Queued
    - `suspend fun assign(ref: EventRef, person: PersonId): EditResult`: queues or sends an `ASSIGN`
    - both toast `couldNotSave(label, message)` when Rejected; the toasts come from the application scope
    - internal constructor `(store, writers, access, toaster: Toaster, zone, clock, scope, requestSync: () -> Unit, io: CoroutineContext, attemptMillis: Long)`
    - `@Inject constructor(store, writers, access, toaster, zone, clock, @ApplicationScope scope, loop: CalendarSyncLoop)`
  - Test helpers:
    - `internal suspend fun testAccess(household, listenIn: CoroutineScope, clock: WallClock, sessionScope: CoroutineScope): TestAccess`
    - `internal suspend fun TestScope.testAccess(household: HouseholdRepository): TestAccess`
    - PINs: Alex (Admin, `TestAccess.ALEX = "1111"`), Sam (Adult, `"2222"`) and Mia (Child, `"3333"`)
    - `answer(vararg pins: String?)` answers PIN pads in order; null means Cancel
    - `requests` lists every PIN pad shown; `toasts` is a `RecordingToaster`

- [ ] **Step 1: Let the calendar use `:core:access`**

In `capability/calendar/build.gradle.kts`, add after `implementation(project(":core:ui"))`:
```kotlin
    implementation(project(":core:access"))
```
Run: `./gradlew :capability:calendar:dependencies --configuration debugCompileClasspath -q`
Expected: no `Module boundary` error. `:capability:*` may depend on `:core:*`.

- [ ] **Step 2: Add the access helper for tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/TestAccess.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import uk.co.siland.culvery.core.plugin.WallClock

/**
 * The real access rules over an in-memory household, with PIN pads answered from a queue.
 *
 * The session's 2-minute timer runs on its own scope, outside virtual time. Room does its work on its own
 * threads, and while a test waits for Room, runTest would otherwise skip virtual time ahead to the next delay,
 * which would end the session mid-test. Tests end a session with `control.lock()`, which is what the timer does.
 */
internal class TestAccess(
    val control: DefaultAccessControl,
    val prompt: PinPromptController,
    val toasts: RecordingToaster,
    val alex: Person,
    val sam: Person,
    val mia: Person,
) {
    val requests = mutableListOf<PinRequest>()
    private val answers = ArrayDeque<String?>()

    /** Answers the next PIN pads in order; null taps Cancel. A pad with no answer queued stays open. */
    fun answer(vararg pins: String?) {
        answers.addAll(pins)
    }

    fun listen(scope: CoroutineScope) {
        scope.launch {
            prompt.request.filterNotNull().collect { request ->
                requests += request
                if (answers.isEmpty()) return@collect
                val pin = answers.removeFirst()
                if (pin == null) prompt.cancel() else prompt.submit(pin)
            }
        }
    }

    companion object {
        const val ALEX = "1111"
        const val SAM = "2222"
        const val MIA = "3333"
    }
}

/** Alex, Sam and Mia with their PINs, and real access control. PIN pads are answered from [listenIn]. */
internal suspend fun testAccess(
    household: HouseholdRepository,
    listenIn: CoroutineScope,
    clock: WallClock,
    sessionScope: CoroutineScope,
): TestAccess {
    val pins = PinManager(household, PinHasher())
    suspend fun person(name: String, color: Long, role: Role, pin: String) =
        household.addPerson(name, color, role).also { pins.setPin(it.id, pin) }
    val alex = person("Alex", 0xFF4CB387, Role.ADMIN, TestAccess.ALEX)
    val sam = person("Sam", 0xFF5B9BE0, Role.ADULT, TestAccess.SAM)
    val mia = person("Mia", 0xFFE07BA8, Role.CHILD, TestAccess.MIA)
    val prompt = PinPromptController()
    val toasts = RecordingToaster()
    val control = DefaultAccessControl(
        registry = PermissionRegistry(setOf(CorePermissionSource(), CalendarPermissionSource())),
        pins = pins,
        lockout = LockoutStore(ApplicationProvider.getApplicationContext()),
        prompt = prompt,
        clock = clock,
        toaster = toasts,
        scope = sessionScope,
    )
    return TestAccess(control, prompt, toasts, alex, sam, mia).also { it.listen(listenIn) }
}

/** For runTest: PIN pads are answered on the test's background scope; the session timer runs outside it. */
internal suspend fun TestScope.testAccess(household: HouseholdRepository): TestAccess {
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    backgroundScope.coroutineContext.job.invokeOnCompletion { sessionScope.cancel() }
    return testAccess(household, backgroundScope, WallClock { testScheduler.currentTime }, sessionScope)
}
```

- [ ] **Step 3: Write the failing permission tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarPermissionsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

class CalendarPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), CalendarPermissionSource()))

    private fun rolesFor(permission: String) = Role.entries.filter { registry.isGranted(it, permission) }

    @Test
    fun theSpecTableOfRolesAndPermissions() {
        assertThat(rolesFor(CalendarPermissions.CREATE)).containsExactly(Role.ADMIN, Role.ADULT)
        assertThat(rolesFor(CalendarPermissions.CREATE_SELF)).containsExactly(Role.ADMIN, Role.ADULT, Role.CHILD)
        assertThat(rolesFor(CalendarPermissions.EDIT)).containsExactly(Role.ADMIN, Role.ADULT)
        assertThat(rolesFor(CalendarPermissions.EDIT_OWN)).containsExactly(Role.ADMIN, Role.ADULT, Role.CHILD)
        assertThat(rolesFor(CalendarPermissions.ASSIGN)).containsExactly(Role.ADMIN, Role.ADULT)
    }

    @Test
    fun noCalendarPermissionNeedsAFreshPin() {
        CalendarPermissionSource().permissions.forEach { assertThat(it.freshPin).isFalse() }
    }

    private val mia = Identified(Person(PersonId("mia"), "Mia", 0xFFE07BA8), Role.CHILD)

    @Test
    fun editOwnCoversOnlyEventsThePersonCreated() {
        assertThat(mayChange(setOf(CalendarPermissions.EDIT_OWN), mia, createdBy = "mia")).isTrue()
        assertThat(mayChange(setOf(CalendarPermissions.EDIT_OWN), mia, createdBy = "alex")).isFalse()
        assertThat(mayChange(setOf(CalendarPermissions.EDIT_OWN), mia, createdBy = null)).isFalse()
    }

    @Test
    fun editCoversEveryEvent() {
        assertThat(mayChange(setOf(CalendarPermissions.EDIT, CalendarPermissions.EDIT_OWN), mia, createdBy = null)).isTrue()
    }

    @Test
    fun refusalWordingMatchesTheHandOff() {
        assertThat(cannotChangeOthers("Mia")).isEqualTo("Mia can only change events they created.")
        assertThat(ASK_AN_ADULT).isEqualTo("Ask an adult to assign this event.")
    }
}
```

- [ ] **Step 4: Write the failing editor tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarEditorTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.WallClock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CalendarEditorTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository

    private val london = ZoneId.of("Europe/London")
    private val window = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), london)
    private val family = CalendarSource("s-family", "Family calendar", writable = true)
    private val school = CalendarSource("s-school", "School terms", writable = false)
    private val writer = ScriptedWriter("calendar.a")
    private var syncRequests = 0

    @Before
    fun setUp() = runTest {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        store.addConnection(Connection("c1", "calendar.a", "Sample calendar", emptyMap()), listOf(family, school), emptyMap())
        store.setMaster("c1", "s-family")
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    private fun event(id: String, createdBy: String?, forPerson: String? = null, recurring: Boolean = false): RemoteEvent {
        val start = LocalDate.of(2026, 9, 23).atTime(19, 30).atZone(london).toInstant()
        return RemoteEvent(id, id, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), recurring, forPerson, createdBy)
    }

    private suspend fun put(vararg events: RemoteEvent, source: String = "s-family") =
        store.applySync("c1", source, window, SyncResult(events.toList(), emptyList(), null, fullReplace = true))

    private fun ref(id: String, source: String = "s-family") = EventRef("c1", source, id)

    /**
     * The editor shares access's toaster, as the app shares one. [io] is the writer's context: tests that hold a
     * write open use Dispatchers.Default, so its 10 s timeout runs on real time and runTest can't skip past it
     * while the test waits for Room.
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
    )

    @Test
    fun adultCanDeleteAnyMasterEvent() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.SAM)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("delete:dinner")
        assertThat(store.eventNow(ref("dinner"))).isNull()
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Delete)
        assertThat(syncRequests).isEqualTo(1)
        assertThat(access.toasts.messages).containsExactly(EVENT_DELETED)
    }

    @Test
    fun childCanDeleteTheirOwnEvent() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).delete(ref("football"))).isEqualTo(EditResult.Done)
    }

    @Test
    fun childCannotDeleteSomeoneElsesEventAndIsToldWhy() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(writer.calls).isEmpty()
        assertThat(store.eventNow(ref("dinner"))).isNotNull()
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun childCannotDeleteAPhoneAddedEvent() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).delete(ref("plumber"))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
    }

    @Test
    fun signedInChildIsRefusedWithoutAPinPad() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value), event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.MIA)
        val editor = editor(access)
        assertThat(editor.mayDelete(ref("football"))).isTrue()
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Cancelled)
        assertThat(access.requests).hasSize(1)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        // Signed out by the refusal, so the next tap asks for a PIN.
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun adultCanAssignAPhoneAddedEventAndTheTagIsWritten() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("plumber"), access.sam.id)).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("update:plumber")
        val stored = store.eventNow(ref("plumber"))!!
        assertThat(stored.forPerson).isEqualTo(access.sam.id.value)
        assertThat(stored.createdBy).isNull()
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Assign)
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun aRejectedAssignChangesNothingAndSaysWhy() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("plumber"), access.sam.id)).isEqualTo(EditResult.Rejected("Event is locked"))
        assertThat(store.eventNow(ref("plumber"))!!.forPerson).isNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Event is locked")
    }

    @Test
    fun childCannotAssignAndIsToldToAskAnAdult() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).assign(ref("plumber"), access.mia.id)).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Ask an adult to assign this event.")
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun cancellingThePinChangesNothing() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(null)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Cancelled)
        assertThat(writer.calls).isEmpty()
        assertThat(syncRequests).isEqualTo(0)
    }

    @Test
    fun aRejectedDeleteChangesNothing() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Rejected("Event is locked"))
        assertThat(store.eventNow(ref("dinner"))).isNotNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Event is locked")
    }

    @Test
    fun offlineDeleteIsQueued() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        val queued = store.pendingNow().single()
        assertThat(listOf(queued.kind, queued.remoteId, queued.attempts)).containsExactly(ChangeKind.DELETE, "dinner", 1).inOrder()
        assertThat(queued.nextAttemptMillis).isEqualTo(testScheduler.currentTime + 30_000)
        assertThat(store.eventNow(ref("dinner"))).isNotNull()
        assertThat(syncRequests).isEqualTo(1)
        // The event hides at once (the repository's overlay), so the delete reads as done.
        assertThat(access.toasts.messages).containsExactly(EVENT_DELETED)
    }

    @Test
    fun aWriteSlowerThanTenSecondsIsQueued() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.gate = CompletableDeferred()
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        assertThat(testScheduler.currentTime).isAtLeast(WRITE_ATTEMPT_MS)
    }

    @Test
    fun needsSignInQueuesTheAssignAndAsksForASync() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        writer.failWith = NeedsSignInException("expired")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("plumber"), access.sam.id)).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow().single().let { it.kind to it.draft?.forPerson }).isEqualTo(ChangeKind.ASSIGN to access.sam.id.value)
        // The pass this asks for flags the connection from its own read.
        assertThat(syncRequests).isEqualTo(1)
    }

    @Test
    fun withinTheSessionTheConfirmationDoesNotAskAgain() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.mayDelete(ref("dinner"))).isTrue()
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Done)
        assertThat(access.requests).hasSize(1)
    }

    @Test
    fun sessionExpiryBetweenGuardAndConfirmAsksForThePinAgain() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.ALEX, TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.mayDelete(ref("dinner"))).isTrue()
        // What the 2-minute timer does when it fires (the timer itself is tested in DefaultAccessControlTest).
        access.control.lock()
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Done)
        assertThat(access.requests).hasSize(2)
    }

    @Test
    fun deletingAnEventAlreadyQueuedForDeleteDoesNotQueueItTwice() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow()).hasSize(1)
        assertThat(writer.calls).hasSize(1)
    }

    @Test
    fun aChangeBehindAPendingOneIsQueuedNotWrittenDirectly() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.assign(ref("plumber"), access.sam.id)).isEqualTo(EditResult.Queued)
        writer.failWith = null
        assertThat(editor.assign(ref("plumber"), access.mia.id)).isEqualTo(EditResult.Queued)
        assertThat(writer.calls).containsExactly("update:plumber")
        val second = store.pendingNow()[1]
        assertThat(second.kind).isEqualTo(ChangeKind.ASSIGN)
        assertThat(second.draft?.forPerson).isEqualTo(access.mia.id.value)
        assertThat(second.attempts).isEqualTo(0)
        assertThat(second.nextAttemptMillis).isEqualTo(testScheduler.currentTime)
    }

    @Test
    fun closingTheSheetMidWriteStillFinishesTheWrite() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val sheet = launch { editor.delete(ref("dinner")) }
        writer.entered.await()
        assertThat(writer.calls).containsExactly("delete:dinner")
        sheet.cancel()
        gate.complete(Unit)
        // Room applies the delete on its own thread; wait (in real time, bounded) for the mirror to show it.
        withContext(Dispatchers.Default) { withTimeout(5_000) { store.event(ref("dinner")).first { it == null } } }
        assertThat(store.pendingNow()).isEmpty()
        assertThat(writer.calls).containsExactly("delete:dinner")
    }

    @Test
    fun concurrentWritesToOneEventRunOneAtATime() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        writer.gate = CompletableDeferred()
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val first = async { editor.assign(ref("plumber"), access.sam.id) }
        writer.entered.await()
        // A second sheet on the same event, on the session the first one started.
        val second = async { editor.assign(ref("plumber"), access.mia.id) }
        // Real time for the second write to reach the writer, if the lock didn't hold it back.
        withContext(Dispatchers.Default) { delay(200) }
        assertThat(writer.calls).containsExactly("update:plumber")
        writer.gate?.complete(Unit)
        assertThat(first.await()).isEqualTo(EditResult.Done)
        assertThat(second.await()).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("update:plumber", "update:plumber")
        assertThat(store.eventNow(ref("plumber"))!!.forPerson).isEqualTo(access.mia.id.value)
    }

    @Test
    fun recurringAndOtherCalendarEventsAreNotEditableAndAskForNoPin() = runTest {
        val access = testAccess(household)
        put(event("swim", createdBy = access.alex.id.value, recurring = true))
        put(event("inset", createdBy = null), source = "s-school")
        val editor = editor(access)
        assertThat(editor.delete(ref("swim"))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.delete(ref("inset", "s-school"))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.assign(ref("inset", "s-school"), access.sam.id)).isEqualTo(EditResult.NotEditable)
        assertThat(editor.mayDelete(ref("swim"))).isFalse()
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun aMissingEventIsNotEditable() = runTest {
        val access = testAccess(household)
        assertThat(editor(access).delete(ref("nope"))).isEqualTo(EditResult.NotEditable)
    }

    @Test
    fun aMasterWithoutAWriterIsNotEditable() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        val noWriter = CalendarEditor(
            store, emptySet(), access.control, access.toasts, HouseholdZone(household),
            WallClock { testScheduler.currentTime }, backgroundScope, {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,
        )
        assertThat(noWriter.delete(ref("dinner"))).isEqualTo(EditResult.NotEditable)
    }
}
```

- [ ] **Step 5: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarPermissionsTest*" --tests "*CalendarEditorTest*"`
Expected: compilation FAILS with unresolved references: `CalendarPermissionSource`, `CalendarPermissions`, `mayChange`, `cannotChangeOthers`, `ASK_AN_ADULT`, `CalendarEditor`, `EditResult` and `WRITE_ATTEMPT_MS`.

- [ ] **Step 6: Declare the calendar permissions**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarPermissions.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.household.Role

object CalendarPermissions {
    const val CREATE = "calendar.event.create"
    const val CREATE_SELF = "calendar.event.create.self"
    const val EDIT = "calendar.event.edit"
    const val EDIT_OWN = "calendar.event.edit.own"
    const val ASSIGN = "calendar.event.assign"
}

/** Spec §8 as amended by the 2b-1 design §3.6. */
class CalendarPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(CalendarPermissions.CREATE, "Add events for anyone", setOf(Role.ADMIN, Role.ADULT)),
        PermissionDef(CalendarPermissions.CREATE_SELF, "Add your own events", Role.entries.toSet()),
        PermissionDef(CalendarPermissions.EDIT, "Change any event", setOf(Role.ADMIN, Role.ADULT)),
        PermissionDef(CalendarPermissions.EDIT_OWN, "Change your own events", Role.entries.toSet()),
        PermissionDef(CalendarPermissions.ASSIGN, "Assign events", setOf(Role.ADMIN, Role.ADULT)),
    )
}

/** Changing (deleting, and in 2b-2 editing) needs edit, or edit.own on an event this person created. */
internal fun mayChange(granted: Set<String>, who: Identified, createdBy: String?): Boolean =
    CalendarPermissions.EDIT in granted ||
        (CalendarPermissions.EDIT_OWN in granted && createdBy == who.person.id.value)

/** Hand-off §7 refusal wording. */
fun cannotChangeOthers(name: String): String = "$name can only change events they created."

const val ASK_AN_ADULT = "Ask an adult to assign this event."
```

- [ ] **Step 7: Decide what the tablet may change**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Editability.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

enum class ReadOnlyReason { OtherCalendar, Recurring }

/**
 * Null when the tablet may change [event] (spec §6): a non-recurring event on the writable master calendar whose
 * provider has a writer. A master with no writer (a misconfiguration) reads as another calendar. A recurring
 * event on another calendar is OtherCalendar.
 */
internal fun readOnlyReason(event: StoredEvent, source: StoredSource?, hasWriter: Boolean): ReadOnlyReason? = when {
    source == null || !source.isMaster || !source.source.writable || !hasWriter -> ReadOnlyReason.OtherCalendar
    event.recurring -> ReadOnlyReason.Recurring
    else -> null
}
```

- [ ] **Step 8: Write the editor**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarEditor.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** How long the editor tries the provider before queueing the change instead (2b-1 design D3). */
const val WRITE_ATTEMPT_MS = 10_000L

/** Hand-off §7: the toast after a delete. */
const val EVENT_DELETED = "Event deleted"

sealed interface EditResult {
    /** The provider accepted the change, and the mirror has it. */
    data object Done : EditResult

    /** The provider couldn't be reached in time: the change is queued and shows as syncing. */
    data object Queued : EditResult

    /** The provider refused the change for good; nothing changed, and the editor has toasted why. */
    data class Rejected(val message: String) : EditResult

    /** The PIN pad was cancelled, or the person was refused and told so by a toast. */
    data object Cancelled : EditResult

    /** The event is gone, queued for deletion, or can't be changed here (another calendar, or recurring). */
    data object NotEditable : EditResult
}

/**
 * Changes master-calendar events on the tablet: authorises, tries the provider for up to [attemptMillis], and
 * queues the change in the outbox when the provider can't be reached. It reports the outcome as a toast itself,
 * and every write nudges the sync loop.
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

    /** The delete guard, run before the confirmation appears: true when this person may delete [ref]. */
    suspend fun mayDelete(ref: EventRef): Boolean {
        val target = resolve(ref) ?: return false
        return authoriseChange(target) != null
    }

    /** Deletes [ref]. Authorises again, which passes silently while the session started by [mayDelete] lasts. */
    suspend fun delete(ref: EventRef): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        authoriseChange(target) ?: return EditResult.Cancelled
        return write(target, ChangeKind.DELETE, forPerson = null)
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
        return write(target, ChangeKind.ASSIGN, forPerson = person.value)
    }

    private class Target(
        val event: StoredEvent,
        val connection: Connection,
        val source: CalendarSource,
        val writer: CalendarWriter,
        val pending: List<PendingChange>,
    ) {
        /** Who made the event once its queued edits land; an assign never changes it. */
        val createdBy: String?
            get() {
                val edit = pending.lastOrNull { it.kind == ChangeKind.UPDATE }?.draft
                return if (edit != null) edit.createdBy else event.createdBy
            }
    }

    private suspend fun resolve(ref: EventRef): Target? {
        val event = store.eventNow(ref) ?: return null
        val source = store.source(ref.connectionId, ref.sourceId) ?: return null
        val connection = store.connectionsNow().firstOrNull { it.connection.id == ref.connectionId }?.connection ?: return null
        // A provider that declares WRITE without binding a writer fails the contract suite, and the repository logs it.
        val writer = writers.firstOrNull { it.providerId == connection.providerId } ?: return null
        if (readOnlyReason(event, source, hasWriter = true) != null) return null
        return Target(event, connection, source.source, writer, store.pendingNow().filter { it.ref == ref })
    }

    private suspend fun authoriseChange(target: Target): Authorised? {
        val createdBy = target.createdBy
        return access.authorise(
            CalendarPermissions.EDIT,
            CalendarPermissions.EDIT_OWN,
            reason = PinReason.Delete,
            allow = { who, granted -> mayChange(granted, who, createdBy) },
            refusal = Refusal.Toast(::cannotChangeOthers),
        )
    }

    /**
     * Runs on the application scope, so closing the sheet mid-write can't lose the change or its toast. Under
     * [writeLock] it reads the queue and the mirror afresh: a change behind a pending one for the same event is
     * queued after it rather than sent directly, where it could land first and be undone.
     */
    private suspend fun write(target: Target, kind: ChangeKind, forPerson: String?): EditResult {
        require(kind == ChangeKind.DELETE || kind == ChangeKind.ASSIGN) { "Creating and editing arrive with the quick-add sheet (2b-2)" }
        return scope.async {
            val result = writeLock.withLock {
                val ref = target.event.ref
                val pending = store.pendingNow().filter { it.ref == ref }
                val event = store.eventNow(ref)
                when {
                    pending.any { it.kind == ChangeKind.DELETE } ->
                        if (kind == ChangeKind.DELETE) EditResult.Queued else EditResult.NotEditable
                    event == null -> if (kind == ChangeKind.DELETE) EditResult.Done else EditResult.NotEditable
                    pending.isNotEmpty() -> queue(target, kind, draftFor(kind, event, forPerson), attempted = false)
                    else -> attempt(target, kind, event, forPerson)
                }
            }
            report(target, kind, result)
            // A sync pass already in flight may briefly put back the old mirror; the pass this asks for corrects it.
            requestSync()
            result
        }.await()
    }

    /** No draft for a delete; for an assign, the event as the mirror has it now, with the new person. */
    private fun draftFor(kind: ChangeKind, event: StoredEvent, forPerson: String?): EventDraft? =
        if (kind == ChangeKind.DELETE) null else assignDraft(event, forPerson)

    private suspend fun attempt(target: Target, kind: ChangeKind, event: StoredEvent, forPerson: String?): EditResult {
        val ref = event.ref
        val draft = draftFor(kind, event, forPerson)
        val outcome = callWriter(io, attemptMillis) {
            if (draft == null) {
                target.writer.delete(target.connection, target.source, ref.remoteId)
                null
            } else {
                target.writer.update(target.connection, target.source, ref.remoteId, draft)
            }
        }
        return when (outcome) {
            is WriteOutcome.Accepted -> {
                val accepted = outcome.event
                if (accepted == null) {
                    store.applyDeleted(ref)
                } else {
                    store.applyAccepted(ref.connectionId, ref.sourceId, accepted, zone.current())
                }
                EditResult.Done
            }
            is WriteOutcome.Rejected -> EditResult.Rejected(outcome.message)
            is WriteOutcome.Retry -> queue(target, kind, draft, attempted = true)
        }
    }

    /** An [attempted] change waits out the first backoff; one queued behind others is due at the next pass. */
    private suspend fun queue(target: Target, kind: ChangeKind, draft: EventDraft?, attempted: Boolean): EditResult {
        val now = clock.nowMillis()
        store.enqueue(
            PendingChange(
                id = 0,
                connectionId = target.connection.id,
                sourceId = target.source.id,
                remoteId = target.event.remoteId,
                kind = kind,
                draft = draft,
                attempts = if (attempted) 1 else 0,
                nextAttemptMillis = if (attempted) now + backoffMillis(1) else now,
                createdMillis = now,
            ),
        )
        return EditResult.Queued
    }

    /** A queued delete already hides the event, so it reads as deleted too. */
    private fun report(target: Target, kind: ChangeKind, result: EditResult) {
        when {
            result is EditResult.Rejected -> toaster.show(couldNotSave(target.connection.label, result.message))
            kind == ChangeKind.DELETE && (result == EditResult.Done || result == EditResult.Queued) -> toaster.show(EVENT_DELETED)
        }
    }
}
```

- [ ] **Step 9: Bind the permissions**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`, add imports `uk.co.siland.culvery.capability.calendar.CalendarPermissionSource` and `uk.co.siland.culvery.core.access.PermissionSource`, and add:
```kotlin
    @Binds
    @IntoSet
    abstract fun permissions(impl: CalendarPermissionSource): PermissionSource
```

- [ ] **Step 10: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarPermissionsTest*" --tests "*CalendarEditorTest*"`
Expected: PASS.

Check before continuing: `kotlinx.coroutines.async` on a `CoroutineScope`, then `await`, is stable API in coroutines 1.10.2. It must not show a deprecation warning.

- [ ] **Step 11: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. Hilt now builds `CalendarEditor`, although nothing injects it until Task 11. The `PermissionRegistry` has 8 permissions and no duplicate ids.

- [ ] **Step 12: Commit**

```bash
git add capability/calendar
git commit -m "Add the calendar editor for delete and assign, with the calendar permissions"
```

---

### Task 9: Repository — `EventRef`, the outbox overlay, editability, "When" and the midnight-end rule

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/PendingOverlay.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarRepository.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/TodayCard.kt` (list key)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt` (list key)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/WhenLabelTest.kt` (create)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/PendingOverlayTest.kt` (create)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt` (modify)
- Modify (constructor calls): `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarCapabilityTest.kt`, `…/ui/CardHostsMidnightRolloverTest.kt`, `…/ui/SampleUi.kt`, `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`

**Interfaces:**
- Consumes:
  - `EventRef`, `StoredEvent.ref`, `PendingChange`, `ChangeKind`, `store.pending()`, `store.sources()`, `store.event(ref)` (Task 1)
  - `CalendarWriter` (Task 5)
  - `ReadOnlyReason` (Task 8)
- Produces:
  - `EventUi(ref: EventRef, title, timeLabel, startLabel, person, allDay, recurring, startSort, …)`, with the new fields:
    - `sourceName: String = ""`
    - `connectionLabel: String = ""`
    - `readOnlyReason: ReadOnlyReason? = null`
    - `untagged: Boolean = false`
    - `syncing: Boolean = false`
    - `createdBy: String = ""`
    - and a computed `val editable: Boolean`
    - `key` is removed
  - `data class EventDetailUi(event: EventUi, whenLabel: String)`
  - `enum class Badge(icon: String, description: String) { Syncing, OtherCalendar, Repeats }` and `fun EventUi.badges(): List<Badge>`
  - `fun whenLabel(start: EventTime, end: EventTime, zone: ZoneId, today: LocalDate): String` and `internal fun dayLabel(date: LocalDate, today: LocalDate): String`
  - `const val ADDED_FROM_PHONE = "Added from phone"` and `const val CALENDAR_FEED = "Calendar feed"`
  - `internal data class ShownEvent(event: StoredEvent, syncing: Boolean)` and `internal fun overlayPending(events, pending, sources: (String, String) -> StoredSource?, zone, windowStart: Long, windowEnd: Long): List<ShownEvent>`
  - `CalendarRepository`:
    - `@Inject constructor(store, household, zone, providers: Set<CalendarProvider>, writers: Set<CalendarWriter>)`
    - `val people: Flow<List<Person>>`
    - `fun event(ref: EventRef, today: LocalDate): Flow<EventDetailUi?>`
    - `days`, `day` and `week` now lay the outbox over the mirror (a queued ASSIGN changes only the person shown)
  - `createdByLabel` reads "Calendar feed" for any source that isn't the master, writable or not

- [ ] **Step 1: Write the failing label and badge tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/WhenLabelTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class WhenLabelTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        EventTime.Timed(LocalDateTime.of(2026, 9, day, hour, minute).atZone(london).toInstant())

    private fun allDay(month: Int, day: Int) = EventTime.AllDay(LocalDate.of(2026, month, day))

    @Test
    fun dayLabelsAreRelativeNearTodayAndShortDatesOtherwise() {
        assertThat(dayLabel(today, today)).isEqualTo("Today")
        assertThat(dayLabel(today.plusDays(1), today)).isEqualTo("Tomorrow")
        assertThat(dayLabel(today.minusDays(1), today)).isEqualTo("Yesterday")
        assertThat(dayLabel(LocalDate.of(2026, 9, 26), today)).isEqualTo("Sat 26 Sep")
    }

    @Test
    fun aTimedEventOnOneDay() {
        assertThat(whenLabel(at(23, 19, 30), at(23, 21), london, today)).isEqualTo("Today · 19:30–21:00")
    }

    @Test
    fun aOneDayAllDayEvent() {
        assertThat(whenLabel(allDay(9, 26), allDay(9, 27), london, today)).isEqualTo("Sat 26 Sep · All day")
    }

    @Test
    fun aSeveralDayAllDayEventNamesItsLastDayNotItsExclusiveEnd() {
        assertThat(whenLabel(allDay(10, 1), allDay(10, 4), london, today)).isEqualTo("Thu 1 Oct – Sat 3 Oct · All day")
    }

    @Test
    fun anEventEndingAtMidnightStaysOnItsDay() {
        assertThat(whenLabel(at(23, 22), at(24, 0), london, today)).isEqualTo("Today · 22:00–00:00")
    }

    @Test
    fun anOvernightEventNamesBothDays() {
        assertThat(whenLabel(at(23, 22), at(24, 1), london, today)).isEqualTo("Today 22:00 – Tomorrow 01:00")
    }

    private fun ui(syncing: Boolean = false, readOnly: ReadOnlyReason? = null, recurring: Boolean = false) = EventUi(
        EventRef("c", "s", "e"), "Swim", "16:00–17:00", "16:00", Person.Family,
        allDay = false, recurring = recurring, startSort = 0, readOnlyReason = readOnly, syncing = syncing,
    )

    @Test
    fun badgesShowSyncingFirstThenLockOrRepeat() {
        assertThat(ui().badges()).isEmpty()
        assertThat(ui(recurring = true, readOnly = ReadOnlyReason.Recurring).badges()).containsExactly(Badge.Repeats)
        assertThat(ui(readOnly = ReadOnlyReason.OtherCalendar).badges()).containsExactly(Badge.OtherCalendar)
        assertThat(ui(recurring = true, readOnly = ReadOnlyReason.OtherCalendar).badges()).containsExactly(Badge.OtherCalendar)
        assertThat(ui(syncing = true, recurring = true, readOnly = ReadOnlyReason.Recurring).badges())
            .containsExactly(Badge.Syncing, Badge.Repeats).inOrder()
    }

    @Test
    fun badgeIconsMatchTheHandOff() {
        assertThat(Badge.entries.map { it.icon }).containsExactly("cloud_upload", "lock", "repeat").inOrder()
    }

    @Test
    fun editableMeansNoReadOnlyReason() {
        assertThat(ui().editable).isTrue()
        assertThat(ui(readOnly = ReadOnlyReason.Recurring).editable).isFalse()
    }

    @Test
    fun onlyTheMasterCalendarNamesWhoAddedAnEvent() {
        val master = StoredSource("c", CalendarSource("s1", "Family calendar", writable = true), SourceMapping.Default, isMaster = true)
        val writableOther = StoredSource("c", CalendarSource("s2", "Work", writable = true), SourceMapping.Default, isMaster = false)
        val alex = Person(PersonId("alex-id"), "Alex", 0xFF4CB387)
        val people = mapOf(alex.id to alex)
        assertThat(createdByLabel("alex-id", master, people)).isEqualTo("Alex")
        assertThat(createdByLabel(null, master, people)).isEqualTo(ADDED_FROM_PHONE)
        assertThat(createdByLabel("alex-id", writableOther, people)).isEqualTo(CALENDAR_FEED)
        assertThat(createdByLabel(null, null, people)).isEqualTo(CALENDAR_FEED)
    }
}
```

- [ ] **Step 2: Write the failing overlay tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/PendingOverlayTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.PersonId

class PendingOverlayTest {
    private val london = ZoneId.of("Europe/London")
    private val family = StoredSource("c1", CalendarSource("s1", "Family calendar", writable = true), SourceMapping.Default, isMaster = true)
    private val hidden = StoredSource("c1", CalendarSource("s2", "Hidden", writable = true), SourceMapping(PersonId.FAMILY, visible = false))
    private val sources = mapOf("s1" to family, "s2" to hidden)

    private fun at(hour: Int) = EventTime.Timed(LocalDateTime.of(2026, 9, 23, hour, 0).atZone(london).toInstant())

    private fun stored(id: String, hour: Int) = StoredEvent(
        "c1", "s1", id, id, at(hour), at(hour + 1), recurring = false, forPerson = null, createdBy = null,
        sourcePerson = PersonId.FAMILY, startSort = at(hour).instant.toEpochMilli(), endSort = at(hour + 1).instant.toEpochMilli(),
    )

    private fun change(id: Long, kind: ChangeKind, remoteId: String?, draft: EventDraft?, sourceId: String = "s1") =
        PendingChange(id, "c1", sourceId, remoteId, kind, draft, attempts = 0, nextAttemptMillis = 0, createdMillis = 0)

    private fun overlay(events: List<StoredEvent>, pending: List<PendingChange>) =
        overlayPending(events, pending, { _, s -> sources[s] }, london, Long.MIN_VALUE, Long.MAX_VALUE)

    @Test
    fun withNothingQueuedTheMirrorShowsAsItIs() {
        assertThat(overlay(listOf(stored("a", 9)), emptyList()).map { it.event.remoteId to it.syncing }).containsExactly("a" to false)
    }

    @Test
    fun aQueuedDeleteHidesTheEvent() {
        val shown = overlay(listOf(stored("a", 9), stored("b", 10)), listOf(change(1, ChangeKind.DELETE, "a", null)))
        assertThat(shown.map { it.event.remoteId }).containsExactly("b")
    }

    @Test
    fun aQueuedUpdateShowsTheDraftAsSyncingAndMovesItsSortTimes() {
        val draft = EventDraft("Moved", at(14), at(15), forPerson = "sam", createdBy = null)
        val shown = overlay(listOf(stored("a", 9)), listOf(change(1, ChangeKind.UPDATE, "a", draft))).single()
        assertThat(shown.syncing).isTrue()
        assertThat(listOf(shown.event.title, shown.event.forPerson)).containsExactly("Moved", "sam").inOrder()
        assertThat(shown.event.startSort).isEqualTo(at(14).instant.toEpochMilli())
    }

    @Test
    fun aQueuedAssignChangesOnlyThePerson() {
        // Queued before the event was renamed and moved on a phone; the mirror has the new title and time.
        val stale = EventDraft("Old title", at(7), at(8), forPerson = "sam", createdBy = null)
        val shown = overlay(listOf(stored("a", 9)), listOf(change(1, ChangeKind.ASSIGN, "a", stale))).single()
        assertThat(shown.syncing).isTrue()
        assertThat(listOf(shown.event.title, shown.event.forPerson)).containsExactly("a", "sam").inOrder()
        assertThat(shown.event.startSort).isEqualTo(at(9).instant.toEpochMilli())
    }

    @Test
    fun laterChangesWinInQueueOrder() {
        val first = EventDraft("a", at(9), at(10), forPerson = "sam", createdBy = null)
        val second = first.copy(forPerson = "mia")
        val shown = overlay(
            listOf(stored("a", 9)),
            listOf(change(1, ChangeKind.UPDATE, "a", first), change(2, ChangeKind.UPDATE, "a", second)),
        )
        assertThat(shown.single().event.forPerson).isEqualTo("mia")
        val deleted = overlay(listOf(stored("a", 9)), listOf(change(1, ChangeKind.UPDATE, "a", first), change(2, ChangeKind.DELETE, "a", null)))
        assertThat(deleted).isEmpty()
    }

    @Test
    fun aQueuedCreateAppearsAsSyncingUnlessItsSourceIsHidden() {
        val draft = EventDraft("Sleepover", at(18), at(19), forPerson = "mia", createdBy = "mia")
        val shown = overlay(emptyList(), listOf(change(7, ChangeKind.CREATE, null, draft), change(8, ChangeKind.CREATE, null, draft, sourceId = "s2")))
        assertThat(shown.map { it.event.remoteId to it.syncing }).containsExactly("pending-7" to true)
    }

    @Test
    fun anUpdateForAnEventNotShownIsIgnored() {
        val draft = EventDraft("Ghost", at(9), at(10), null, null)
        assertThat(overlay(emptyList(), listOf(change(1, ChangeKind.UPDATE, "gone", draft)))).isEmpty()
    }

    @Test
    fun theWindowLimitsWhatIsShown() {
        val shown = overlayPending(
            listOf(stored("a", 9), stored("b", 20)), emptyList(), { _, s -> sources[s] }, london,
            at(8).instant.toEpochMilli(), at(12).instant.toEpochMilli(),
        )
        assertThat(shown.map { it.event.remoteId }).containsExactly("a")
    }
}
```

- [ ] **Step 3: Write the failing repository tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt`:

1. In `setUp`, replace `repo = CalendarRepository(store, household, HouseholdZone(household))` with:
```kotlin
        repo = CalendarRepository(store, household, HouseholdZone(household), emptySet(), setOf(ScriptedWriter("calendar.test")))
```
   After the `store.addConnection(…)` call, add:
```kotlin
        store.setMaster("c1", "s-family")
```
2. In `hasConnectionsFollowsTheStore`, replace `CalendarRepository(emptyStore, household, HouseholdZone(household))` with `CalendarRepository(emptyStore, household, HouseholdZone(household), emptySet(), emptySet())`.
3. Add these members at the end of the class:
```kotlin
    private fun ref(id: String, source: String = "s-family") = EventRef("c1", source, id)

    private suspend fun queue(kind: ChangeKind, remoteId: String?, draft: EventDraft? = null, source: String = "s-family"): Long =
        store.enqueue(PendingChange(0, "c1", source, remoteId, kind, draft, attempts = 1, nextAttemptMillis = 0, createdMillis = 0))

    private suspend fun today() = repo.day(sept(23)).first()

    @Test
    fun eventsCarryTheirRefSourceAndConnection() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        val e = today().single()
        assertThat(e.ref).isEqualTo(ref("Boiler service"))
        assertThat(e.sourceName).isEqualTo("Family")
        assertThat(e.connectionLabel).isEqualTo("Google")
    }

    @Test
    fun masterEventsAreEditableAndOthersSayWhy() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60), timed("Swimming", 23, 16, 0, 60).copy(recurring = true))
        put("s-alex", timed("Dinner", 23, 19, 0, 60), timed("Gym", 23, 7, 0, 60).copy(recurring = true))
        val byTitle = today().associateBy { it.title }
        assertThat(byTitle.getValue("Boiler service").readOnlyReason).isNull()
        assertThat(byTitle.getValue("Boiler service").editable).isTrue()
        assertThat(byTitle.getValue("Swimming").readOnlyReason).isEqualTo(ReadOnlyReason.Recurring)
        assertThat(byTitle.getValue("Dinner").readOnlyReason).isEqualTo(ReadOnlyReason.OtherCalendar)
        assertThat(byTitle.getValue("Gym").readOnlyReason).isEqualTo(ReadOnlyReason.OtherCalendar)
    }

    @Test
    fun aMasterWhoseProviderHasNoWriterIsReadOnly() = runTest {
        val noWriter = CalendarRepository(store, household, HouseholdZone(household), emptySet(), emptySet())
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        assertThat(noWriter.day(sept(23)).first().single().readOnlyReason).isEqualTo(ReadOnlyReason.OtherCalendar)
    }

    @Test
    fun createdByNamesThePersonThePhoneOrTheFeed() = runTest {
        put(
            "s-family",
            timed("Dinner with Jo & Priya", 23, 19, 30, 90).copy(createdBy = alex.id.value),
            timed("Plumber quote call", 23, 13, 0, 30),
        )
        put("s-alex", timed("Gym", 23, 7, 0, 60))
        val byTitle = today().associateBy { it.title }
        assertThat(byTitle.getValue("Dinner with Jo & Priya").createdBy).isEqualTo("Alex")
        assertThat(byTitle.getValue("Plumber quote call").createdBy).isEqualTo(ADDED_FROM_PHONE)
        assertThat(byTitle.getValue("Gym").createdBy).isEqualTo(CALENDAR_FEED)
    }

    @Test
    fun untaggedMeansAMasterEventWithNoTagsAtAll() = runTest {
        put(
            "s-family",
            timed("Plumber quote call", 23, 13, 0, 30),
            timed("Assigned", 23, 14, 0, 30, forPerson = sam.id.value),
        )
        put("s-alex", timed("Gym", 23, 7, 0, 60))
        val byTitle = today().associateBy { it.title }
        assertThat(byTitle.getValue("Plumber quote call").untagged).isTrue()
        assertThat(byTitle.getValue("Assigned").untagged).isFalse()
        assertThat(byTitle.getValue("Gym").untagged).isFalse()
    }

    @Test
    fun aQueuedDeleteIsHidden() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        queue(ChangeKind.DELETE, "Boiler service")
        assertThat(today()).isEmpty()
        assertThat(store.eventNow(ref("Boiler service"))).isNotNull()
    }

    @Test
    fun aRejectedQueuedDeleteBringsTheEventBack() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        val id = queue(ChangeKind.DELETE, "Boiler service")
        assertThat(today()).isEmpty()
        // What the drain does when the provider refuses the delete.
        store.dropChange(id)
        assertThat(today().map { it.title to it.syncing }).containsExactly("Boiler service" to false)
    }

    @Test
    fun pendingDeleteStaysHiddenAfterAFullReplaceSync() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        queue(ChangeKind.DELETE, "Boiler service")
        put("s-family", timed("Boiler service", 23, 10, 0, 60), timed("Walk", 23, 12, 0, 30))
        assertThat(today().map { it.title }).containsExactly("Walk")
    }

    @Test
    fun pendingAssignShowsTheNewPersonAsSyncingAfterAFullReplaceSync() = runTest {
        val original = timed("Plumber quote call", 23, 13, 0, 30)
        put("s-family", original)
        queue(
            ChangeKind.ASSIGN,
            "Plumber quote call",
            EventDraft(original.title, original.start, original.end, forPerson = sam.id.value, createdBy = null),
        )
        assertThat(today().single().let { it.person.name to it.syncing }).isEqualTo("Sam" to true)
        put("s-family", original)
        assertThat(today().single().let { it.person.name to it.syncing }).isEqualTo("Sam" to true)
        assertThat(today().single().untagged).isFalse()
    }

    @Test
    fun aQueuedCreateShowsAsSyncing() = runTest {
        val slot = timed("Sleepover", 23, 18, 0, 60)
        val id = queue(ChangeKind.CREATE, null, EventDraft("Sleepover", slot.start, slot.end, forPerson = sam.id.value, createdBy = sam.id.value))
        val e = today().single()
        assertThat(listOf(e.title, e.person.name)).containsExactly("Sleepover", "Sam").inOrder()
        assertThat(e.syncing).isTrue()
        assertThat(e.ref).isEqualTo(ref("pending-$id"))
    }

    @Test
    fun detailHasTheWhenLabelAndFollowsTheOutbox() = runTest {
        put("s-family", timed("Dinner with Jo & Priya", 23, 19, 30, 90).copy(createdBy = alex.id.value))
        val detail = repo.event(ref("Dinner with Jo & Priya"), today = sept(23)).first()!!
        assertThat(detail.whenLabel).isEqualTo("Today · 19:30–21:00")
        assertThat(detail.event.createdBy).isEqualTo("Alex")
        assertThat(detail.event.editable).isTrue()
        queue(ChangeKind.DELETE, "Dinner with Jo & Priya")
        assertThat(repo.event(ref("Dinner with Jo & Priya"), today = sept(23)).first()).isNull()
    }

    @Test
    fun detailShowsAQueuedAssignAsSyncing() = runTest {
        val original = timed("Plumber quote call", 23, 13, 0, 30)
        put("s-family", original)
        queue(ChangeKind.ASSIGN, original.remoteId, EventDraft(original.title, original.start, original.end, sam.id.value, null))
        val detail = repo.event(ref(original.remoteId), today = sept(23)).first()!!
        assertThat(detail.event.syncing).isTrue()
        assertThat(detail.event.person.name).isEqualTo("Sam")
    }

    @Test
    fun detailOfAMissingEventIsNull() = runTest {
        assertThat(repo.event(ref("nope"), today = sept(23)).first()).isNull()
    }

    @Test
    fun anEventEndingAtMidnightStaysOnItsOwnDay() = runTest {
        put("s-alex", timed("Late film", 23, 22, 0, 120))
        assertThat(titlesAndLabels(sept(23), 2)).containsExactly(
            listOf("Late film 22:00–00:00"),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun anEventEndingAtMidnightTwoDaysOnReadsFromThenAllDay() = runTest {
        put("s-alex", timed("Festival", 23, 22, 0, 26 * 60L))
        assertThat(titlesAndLabels(sept(23), 3)).containsExactly(
            listOf("Festival 22:00–"),
            listOf("Festival All day"),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun peopleListsTheHousehold() = runTest {
        assertThat(repo.people.first().map { it.name }).containsExactly("Alex", "Sam").inOrder()
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarCapabilityTest.kt` and `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardHostsMidnightRolloverTest.kt`, replace `CalendarRepository(store, household, zone)` with `CalendarRepository(store, household, zone, emptySet(), emptySet())`.

- [ ] **Step 4: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: compilation FAILS with unresolved references: `whenLabel`, `dayLabel`, `Badge`, `badges`, `overlayPending`, `EventUi.ref`, `readOnlyReason`, `ADDED_FROM_PHONE`, `repo.event` and `repo.people`, and `CalendarRepository` is called with too many arguments.

- [ ] **Step 5: Extend the UI models**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

const val ALL_DAY_LABEL = "All day"
const val STALE_AFTER_MS = 30 * 60_000L
const val ADDED_FROM_PHONE = "Added from phone"
const val CALENDAR_FEED = "Calendar feed"

data class EventUi(
    /** Which mirrored event this is; the same on every day a multi-day event covers. */
    val ref: EventRef,
    val title: String,
    /** This day's slice: "07:45–08:30", "22:00–" (first day of several), "until 01:00" (last day), or "All day". */
    val timeLabel: String,
    /** The week chip's label: "07:45" for a one-day event, otherwise the same as [timeLabel]. */
    val startLabel: String,
    val person: Person,
    /** True for all-day events and for the middle days of a multi-day timed event; they sort first. */
    val allDay: Boolean,
    val recurring: Boolean,
    val startSort: Long,
    /** The source calendar's name, e.g. "Family calendar" or "School terms". */
    val sourceName: String = "",
    /** The connection's label ("Google", "Sample calendar"): where changes are sent. */
    val connectionLabel: String = "",
    /** Null when the tablet may change this event: a non-recurring event on the master calendar. */
    val readOnlyReason: ReadOnlyReason? = null,
    /** On the master calendar with no person tags at all: added from a phone (hand-off "untagged"). */
    val untagged: Boolean = false,
    /** A change to it is waiting in the outbox. */
    val syncing: Boolean = false,
    /** A person's name, [ADDED_FROM_PHONE] or [CALENDAR_FEED]. */
    val createdBy: String = "",
) {
    val editable: Boolean get() = readOnlyReason == null
}

/** The detail sheet's model: the event plus its "When" row. */
data class EventDetailUi(val event: EventUi, val whenLabel: String)

data class DayUi(val date: LocalDate, val events: List<EventUi>)

/** [people] is the legend: household members in order, then Family. */
data class WeekUi(val start: LocalDate, val days: List<DayUi>, val people: List<Person>)

/**
 * [lastSyncMillis] is the oldest successful sync across connections, so one failing calendar can't hide.
 * [connectionLabels] names every connection. [failingBeforeFirstSync] is true when a connection has never
 * synced and is not Ok, which makes the status stale however recent the others are.
 */
data class SyncStatusUi(
    val lastSyncMillis: Long?,
    val needsSignIn: List<String>,
    val connectionLabels: List<String>,
    val failingBeforeFirstSync: Boolean,
)

/** Hand-off §7 chip and row badges, in the order they show. */
enum class Badge(val icon: String, val description: String) {
    Syncing("cloud_upload", "Syncing"),
    OtherCalendar("lock", "Read-only calendar"),
    Repeats("repeat", "Repeats"),
}

/** `cloud_upload` first, then `lock` (another calendar) or `repeat` (recurring). */
fun EventUi.badges(): List<Badge> = listOfNotNull(
    Badge.Syncing.takeIf { syncing },
    when {
        readOnlyReason == ReadOnlyReason.OtherCalendar -> Badge.OtherCalendar
        recurring -> Badge.Repeats
        else -> null
    },
)

fun syncedLabel(lastSyncMillis: Long?, nowMillis: Long, connectionLabel: String? = null): String {
    if (lastSyncMillis == null) return "not synced yet"
    val synced = if (connectionLabel == null) "synced" else "synced with $connectionLabel"
    // A wall clock that jumped backwards gives a negative age; treat it as fresh.
    val minutes = (nowMillis - lastSyncMillis).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val ago = when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 48 -> "$hours h ago"
        else -> "${hours / 24} days ago"
    }
    return "$synced $ago"
}

fun isStale(lastSyncMillis: Long?, nowMillis: Long): Boolean =
    lastSyncMillis != null && nowMillis - lastSyncMillis > STALE_AFTER_MS

fun SyncStatusUi.isStaleAt(nowMillis: Long): Boolean =
    failingBeforeFirstSync || isStale(lastSyncMillis, nowMillis)

fun weekSubtitle(sync: SyncStatusUi, nowMillis: Long): String =
    "Family calendar · ${syncedLabel(sync.lastSyncMillis, nowMillis, sync.connectionLabels.singleOrNull())}"

/** A tag naming a real person (or Family) wins; otherwise the source's person; otherwise Family. */
fun resolvePerson(forPerson: String?, sourcePerson: PersonId, people: Map<PersonId, Person>): Person =
    forPerson?.let { people[PersonId(it)] } ?: people[sourcePerson] ?: Person.Family

private val HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm")
// ENGLISH, not UK: JDK 17's CLDR data gives "Sept" for Locale.UK, and Android versions differ.
private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** An event ending at exactly midnight ends on the day before. */
private fun lastDayOf(from: ZonedDateTime, to: ZonedDateTime): LocalDate {
    val endsAtMidnight = to.toLocalTime() == LocalTime.MIDNIGHT
    return if (endsAtMidnight && to.toLocalDate().isAfter(from.toLocalDate())) to.toLocalDate().minusDays(1) else to.toLocalDate()
}

internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(SHORT_DAY)
}

/** The detail sheet's "When": "Today · 19:30–21:00", "Sat 26 Sep · All day", or the span of a longer event. */
fun whenLabel(start: EventTime, end: EventTime, zone: ZoneId, today: LocalDate): String {
    if (start is EventTime.AllDay && end is EventTime.AllDay) {
        val last = maxOf(start.date, end.date.minusDays(1))
        return if (last == start.date) {
            "${dayLabel(start.date, today)} · $ALL_DAY_LABEL"
        } else {
            "${dayLabel(start.date, today)} – ${dayLabel(last, today)} · $ALL_DAY_LABEL"
        }
    }
    val from = start.instantIn(zone).atZone(zone)
    val to = end.instantIn(zone).atZone(zone)
    return if (lastDayOf(from, to) == from.toLocalDate()) {
        "${dayLabel(from.toLocalDate(), today)} · ${from.format(HOURS_MINUTES)}–${to.format(HOURS_MINUTES)}"
    } else {
        "${dayLabel(from.toLocalDate(), today)} ${from.format(HOURS_MINUTES)} – ${dayLabel(to.toLocalDate(), today)} ${to.format(HOURS_MINUTES)}"
    }
}

private class Slice(val timeLabel: String, val startLabel: String, val allDay: Boolean)

/** How the event reads on [date]. A timed event over several days reads "22:00–", then "All day", then "until 01:00". */
private fun StoredEvent.sliceOn(date: LocalDate, zone: ZoneId): Slice {
    if (start is EventTime.AllDay) return Slice(ALL_DAY_LABEL, ALL_DAY_LABEL, allDay = true)
    val from = start.instantIn(zone).atZone(zone)
    val to = end.instantIn(zone).atZone(zone)
    val endsAtMidnight = to.toLocalTime() == LocalTime.MIDNIGHT
    val lastDay = lastDayOf(from, to)
    val startClock = from.format(HOURS_MINUTES)
    val endClock = to.format(HOURS_MINUTES)
    return when {
        lastDay == from.toLocalDate() -> Slice("$startClock–$endClock", startClock, allDay = false)
        date == from.toLocalDate() -> Slice("$startClock–", "$startClock–", allDay = false)
        date == lastDay && !endsAtMidnight -> Slice("until $endClock", "until $endClock", allDay = false)
        else -> Slice(ALL_DAY_LABEL, ALL_DAY_LABEL, allDay = true)
    }
}

/** Sources, connection labels and which providers can write: what the UI needs beyond the event row. */
internal class SourceCatalog(sources: List<StoredSource>, connections: List<StoredConnection>, private val writerIds: Set<String>) {
    private val sources = sources.associateBy { it.connectionId to it.source.id }
    private val connections = connections.associate { it.connection.id to it.connection }

    fun source(connectionId: String, sourceId: String): StoredSource? = sources[connectionId to sourceId]

    fun label(connectionId: String): String = connections[connectionId]?.label.orEmpty()

    fun hasWriter(connectionId: String): Boolean = connections[connectionId]?.providerId in writerIds
}

/** Only the master calendar carries the tablet's tags; any other calendar's events come from its feed. */
internal fun createdByLabel(createdBy: String?, source: StoredSource?, people: Map<PersonId, Person>): String = when {
    source == null || !source.isMaster -> CALENDAR_FEED
    createdBy == null -> ADDED_FROM_PHONE
    else -> people[PersonId(createdBy)]?.name ?: ADDED_FROM_PHONE
}

internal fun StoredEvent.toUi(
    date: LocalDate,
    zone: ZoneId,
    people: Map<PersonId, Person>,
    catalog: SourceCatalog,
    syncing: Boolean = false,
): EventUi {
    val slice = sliceOn(date, zone)
    val source = catalog.source(connectionId, sourceId)
    return EventUi(
        ref = ref,
        title = title,
        timeLabel = slice.timeLabel,
        startLabel = slice.startLabel,
        person = resolvePerson(forPerson, sourcePerson, people),
        allDay = slice.allDay,
        recurring = recurring,
        startSort = startSort,
        sourceName = source?.source?.name.orEmpty(),
        connectionLabel = catalog.label(connectionId),
        readOnlyReason = readOnlyReason(this, source, catalog.hasWriter(connectionId)),
        untagged = source?.isMaster == true && source.source.writable && forPerson == null && createdBy == null,
        syncing = syncing,
        createdBy = createdByLabel(createdBy, source, people),
    )
}
```

A tag naming someone no longer in the household reads "Added from phone". That's the same as an untagged event, and it is only a label: the editor still checks the stored id.

- [ ] **Step 6: Lay pending changes over the mirror**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/PendingOverlay.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.ZoneId

/** A mirrored event as the UI shows it, with any queued change laid over it. */
internal data class ShownEvent(val event: StoredEvent, val syncing: Boolean)

/**
 * Lays [pending] changes over [events] in queue order. A delete hides the event. An update shows the draft's
 * values; an assign shows only its person. A create adds the draft as a new event on a visible source; it has no
 * remoteId yet, so its ref uses "pending-{id}". Only events overlapping [windowStart, windowEnd) are returned, in
 * start order.
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
                if (draft == null) continue
                val event = StoredEvent(
                    connectionId = change.connectionId,
                    sourceId = change.sourceId,
                    remoteId = "pending-${change.id}",
                    title = draft.title,
                    start = draft.start,
                    end = draft.end,
                    recurring = false,
                    forPerson = draft.forPerson,
                    createdBy = draft.createdBy,
                    sourcePerson = source.mapping.person,
                    startSort = draft.start.instantIn(zone).toEpochMilli(),
                    endSort = draft.end.instantIn(zone).toEpochMilli(),
                )
                shown[event.ref] = ShownEvent(event, syncing = true)
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

- [ ] **Step 7: Build the repository's flows on the overlay**

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

    /** The household's people, for the detail sheet's Assign chips. */
    val people: Flow<List<Person>> = household.people

    val syncStatus: Flow<SyncStatusUi> = store.connections().map { connections ->
        SyncStatusUi(
            lastSyncMillis = connections.mapNotNull { it.lastSyncMillis }.minOrNull(),
            needsSignIn = connections.filter { it.health == ConnectionHealth.NeedsSignIn }.map { it.connection.label },
            connectionLabels = connections.map { it.connection.label },
            failingBeforeFirstSync = connections.any { it.lastSyncMillis == null && it.health != ConnectionHealth.Ok },
        )
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

    /** One event for the detail sheet; null once it is gone or queued for deletion. [today] makes "Today · …". */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun event(ref: EventRef, today: LocalDate): Flow<EventDetailUi?> = zone.zone.flatMapLatest { z ->
        combine(store.event(ref), store.pending(), catalog, household.peopleWithFamily) { stored, pending, cat, people ->
            val mirrored = stored ?: return@combine null
            val shown = overlayPending(listOf(mirrored), pending.filter { it.ref == ref }, cat::source, z, Long.MIN_VALUE, Long.MAX_VALUE)
                .singleOrNull() ?: return@combine null
            val e = shown.event
            val day = e.start.instantIn(z).atZone(z).toLocalDate()
            EventDetailUi(e.toUi(day, z, people.associateBy { it.id }, cat, shown.syncing), whenLabel(e.start, e.end, z, today))
        }
    }

    private fun millis(date: LocalDate, z: ZoneId) = date.atStartOfDay(z).toInstant().toEpochMilli()

    private companion object {
        const val TAG = "CalendarRepository"
    }
}
```

- [ ] **Step 8: Key the lists by `EventRef`**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/TodayCard.kt`, replace `items(events, key = { it.key }) { TodayRow(it) }` with:
```kotlin
                items(events, key = { it.ref.listKey }) { TodayRow(it) }
```
In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt`, replace `items(day.events, key = { it.key }) { EventChip(it) }` with:
```kotlin
            items(day.events, key = { it.ref.listKey }) { EventChip(it) }
```

- [ ] **Step 9: Update the test UI models to the new `EventUi`**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/SampleUi.kt`:
- add `import uk.co.siland.culvery.capability.calendar.EventRef` and `import uk.co.siland.culvery.capability.calendar.ReadOnlyReason`;
- replace the `event` and `allDay` helpers with:
```kotlin
    fun event(title: String, time: String, person: Person, recurring: Boolean = false) = EventUi(
        EventRef("sample", "family", title), title, time, time.substringBefore('–'), person,
        allDay = false, recurring = recurring, startSort = 0,
        readOnlyReason = if (recurring) ReadOnlyReason.Recurring else null,
    )

    fun allDay(title: String, person: Person, recurring: Boolean = false) = EventUi(
        EventRef("sample", "family", title), title, ALL_DAY_LABEL, ALL_DAY_LABEL, person,
        allDay = true, recurring = recurring, startSort = 0,
        readOnlyReason = if (recurring) ReadOnlyReason.Recurring else null,
    )
```

In `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`:
- add `import uk.co.siland.culvery.capability.calendar.EventRef`;
- replace the `event` and `allDay` helpers with:
```kotlin
    private fun event(title: String, time: String, person: Person, recurring: Boolean = false) = EventUi(
        EventRef("sample", "family", title), title, time, time.substringBefore('–'), person,
        allDay = false, recurring = recurring, startSort = 0,
    )

    private fun allDay(title: String, person: Person, recurring: Boolean = false) =
        EventUi(EventRef("sample", "family", title), title, "All day", "All day", person, allDay = true, recurring = recurring, startSort = 0)
```

- [ ] **Step 10: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS. Every 2a repository test still passes: the labels and people are unchanged, and the master flag only adds fields.

- [ ] **Step 11: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, with no screenshot changes. The rows and chips don't draw the new fields yet (Task 11), and the sample recurring events still show only `repeat`.

- [ ] **Step 12: Commit**

```bash
git add capability/calendar app/src/test
git commit -m "Key calendar events by EventRef and show queued changes over the mirror"
```

---

### Task 10: Event detail sheet — every state, the delete confirmation, assign chips, the syncing pill

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheet.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHost.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/RecordingOverlay.kt`
- Modify: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/SampleUi.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheetTest.kt` (create)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHostTest.kt` (create)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/DetailScreenshotTest.kt` (create)
- Create (recorded): `capability/calendar/src/test/screenshots/detail_*.png` (14 images)

**Interfaces:**
- Consumes:
  - `EventDetailUi`, `EventUi` fields, `ReadOnlyReason`, `CalendarRepository.event(ref, today)`, `CalendarRepository.people` (Task 9)
  - `CalendarEditor.mayDelete/delete/assign`, `EditResult` (Task 8); the editor shows the outcome toasts itself
  - `HhSheet`, `HhCloseButton`, `ShellTokens`, `LocalOverlayHost` (Task 2)
  - `PinPadSheet(…, overSheet = true)` (Task 4)
  - `testAccess(household, listenIn, clock, sessionScope)` (Task 8)
- Produces:
  - `enum class DetailMode { Idle, ChoosingPerson, ConfirmingDelete }`
  - `@Composable fun EventDetailSheet(detail: EventDetailUi, people: List<Person>, mode: DetailMode, busy: Boolean, onClose: () -> Unit, onDelete: () -> Unit, onKeep: () -> Unit, onConfirmDelete: () -> Unit, onChoosePerson: () -> Unit, onAssign: (Person) -> Unit, modifier: Modifier = Modifier)`
  - Sheet test tags: `detail_sheet`, `detail_info`, `detail_note`, `detail_syncing`, `detail_assign`, `assign_<name>`, `detail_delete`, `detail_confirm`, `detail_keep`, `detail_confirm_delete`, and `sheet_close` from `HhCloseButton`
  - `@Composable internal fun EventDetailHost(ref: EventRef, today: LocalDate, repo: CalendarRepository, editor: CalendarEditor, onClose: () -> Unit)`
  - `@Composable internal fun rememberEventOpener(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate): (EventRef) -> Unit`
  - `internal fun calendarLabel(event: EventUi): String`
  - New `CalendarDimens` and `CalendarType` entries for the sheet (Step 3)
  - `SampleUi.detailEditable`, `detailReadOnlyFeed`, `detailRecurring`, `detailUntagged`, `detailSyncing`, `household`
  - Test helper `RecordingOverlay : OverlayHost`

- [ ] **Step 1: Add the sample details and a recording overlay**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/SampleUi.kt`:
- add imports `uk.co.siland.culvery.capability.calendar.ADDED_FROM_PHONE`, `uk.co.siland.culvery.capability.calendar.CALENDAR_FEED` and `uk.co.siland.culvery.capability.calendar.EventDetailUi`;
- add at the end of the object:
```kotlin
    /** The people who can be assigned (hand-off 06: Alex, Sam, Mia). */
    val household = listOf(alex, sam, mia)

    private fun EventUi.onFamilyCalendar(createdBy: String) =
        copy(sourceName = "Family calendar", connectionLabel = "Sample calendar", createdBy = createdBy)

    /** Hand-off 03. */
    val detailEditable = EventDetailUi(event("Dinner with Jo & Priya", "19:30–21:00", alex).onFamilyCalendar("Alex"), "Today · 19:30–21:00")

    /** Hand-off 04. */
    val detailReadOnlyFeed = EventDetailUi(
        allDay("INSET day — no school", family).copy(
            sourceName = "School terms",
            connectionLabel = "Sample calendar",
            readOnlyReason = ReadOnlyReason.OtherCalendar,
            createdBy = CALENDAR_FEED,
        ),
        "Sat 26 Sep · All day",
    )

    /** Hand-off 05. */
    val detailRecurring = EventDetailUi(event("Swimming", "16:00–17:00", mia, recurring = true).onFamilyCalendar("Sam"), "Today · 16:00–17:00")

    /** Hand-off 06. */
    val detailUntagged = EventDetailUi(
        event("Plumber quote call", "13:00–13:30", family).onFamilyCalendar(ADDED_FROM_PHONE).copy(untagged = true),
        "Today · 13:00–13:30",
    )

    /** Hand-off 07. */
    val detailSyncing = detailEditable.copy(event = detailEditable.event.copy(syncing = true))
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/RecordingOverlay.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.co.siland.culvery.core.plugin.OverlayHost

class RecordingOverlay : OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
    var dismissed = 0

    override fun show(content: @Composable () -> Unit) {
        this.content = content
    }

    override fun dismiss() {
        content = null
        dismissed++
    }
}
```

- [ ] **Step 2: Write the failing sheet tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheetTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class EventDetailSheetTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var mode by mutableStateOf(DetailMode.Idle)
    private var busy by mutableStateOf(false)

    private fun show(detail: EventDetailUi) = compose.setContent {
        CulveryTheme(dark = true) {
            EventDetailSheet(
                detail = detail,
                people = SampleUi.household,
                mode = mode,
                busy = busy,
                onClose = { calls += "close" },
                onDelete = { calls += "delete" },
                onKeep = { calls += "keep" },
                onConfirmDelete = { calls += "confirm" },
                onChoosePerson = { calls += "choose" },
                onAssign = { p: Person -> calls += "assign:${p.name}" },
            )
        }
    }

    @Test
    fun editableShowsTheInfoRowsAndADeleteButton() {
        show(SampleUi.detailEditable)
        listOf("When", "Today · 19:30–21:00", "For", "Created by", "Calendar", "Family calendar")
            .forEach { compose.onNodeWithText(it).assertExists() }
        // For and Created by both read "Alex".
        compose.onAllNodesWithText("Alex").assertCountEquals(2)
        compose.onNodeWithText("Repeats").assertDoesNotExist()
        compose.onNodeWithTag("detail_delete").assertExists()
        compose.onNodeWithTag("detail_note").assertDoesNotExist()
        compose.onNodeWithTag("detail_syncing").assertDoesNotExist()
    }

    @Test
    fun anotherCalendarIsReadOnlyWithNoFooter() {
        show(SampleUi.detailReadOnlyFeed)
        compose.onNodeWithText("From School terms (read-only)").assertExists()
        compose.onNodeWithText("This is a subscribed calendar, so it can't be changed here.").assertExists()
        compose.onNodeWithText("School terms · read-only").assertExists()
        compose.onNodeWithText("Calendar feed").assertExists()
        compose.onNodeWithTag("detail_delete").assertDoesNotExist()
    }

    @Test
    fun aRepeatingEventPointsToThePhoneAndShowsRepeats() {
        show(SampleUi.detailRecurring)
        compose.onNodeWithText("Repeating event").assertExists()
        compose.onNodeWithText("Edit repeating events in Sample calendar on your phone.").assertExists()
        compose.onNodeWithText("Repeats").assertExists()
        compose.onNodeWithTag("detail_delete").assertDoesNotExist()
    }

    @Test
    fun anUntaggedEventOffersAssignAndKeepsDelete() {
        show(SampleUi.detailUntagged)
        compose.onNodeWithText("Added from a phone").assertExists()
        compose.onNodeWithText("Showing as Family until someone assigns it.").assertExists()
        compose.onNodeWithText("Added from phone").assertExists()
        compose.onNodeWithTag("detail_delete").assertExists()
        compose.onNodeWithTag("assign_Sam").assertDoesNotExist()
        compose.onNodeWithText("Assign to…").performClick()
        assertThat(calls).containsExactly("choose")
    }

    @Test
    fun choosingAPersonAssignsThem() {
        mode = DetailMode.ChoosingPerson
        show(SampleUi.detailUntagged)
        compose.onNodeWithTag("detail_assign").assertDoesNotExist()
        listOf("Alex", "Sam", "Mia").forEach { compose.onNodeWithTag("assign_$it").assertExists() }
        compose.onNodeWithTag("assign_Sam").performClick()
        assertThat(calls).containsExactly("assign:Sam")
    }

    @Test
    fun aPendingChangeShowsTheSyncingPill() {
        show(SampleUi.detailSyncing)
        compose.onNodeWithTag("detail_syncing").assertExists()
        compose.onNodeWithText("Syncing to Sample calendar…").assertExists()
    }

    @Test
    fun deleteRunsTheGuardFirstRatherThanDeleting() {
        show(SampleUi.detailEditable)
        compose.onNodeWithTag("detail_delete").performClick()
        assertThat(calls).containsExactly("delete")
        compose.onNodeWithText("Delete this event?").assertDoesNotExist()
    }

    @Test
    fun theConfirmationReplacesTheFooterWithKeepWhereDeleteWas() {
        show(SampleUi.detailEditable)
        val delete = compose.onNodeWithTag("detail_delete").getUnclippedBoundsInRoot()
        mode = DetailMode.ConfirmingDelete
        compose.onNodeWithText("Delete this event?").assertExists()
        compose.onNodeWithText("“Dinner with Jo & Priya” will be removed from Sample calendar for everyone.").assertExists()
        compose.onNodeWithTag("detail_delete").assertDoesNotExist()
        val keep = compose.onNodeWithTag("detail_keep").getUnclippedBoundsInRoot()
        val confirm = compose.onNodeWithTag("detail_confirm_delete").getUnclippedBoundsInRoot()
        // A second tap where Delete was lands on Keep event, so a double tap is harmless.
        val deleteCentreX = (delete.left + delete.right) / 2
        val deleteCentreY = (delete.top + delete.bottom) / 2
        assertThat(deleteCentreX).isAtLeast(keep.left)
        assertThat(deleteCentreX).isAtMost(keep.right)
        assertThat(deleteCentreY).isAtLeast(keep.top)
        assertThat(deleteCentreY).isAtMost(keep.bottom)
        assertThat(keep.right).isLessThan(confirm.left)
        compose.onNodeWithTag("detail_keep").performClick()
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        assertThat(calls).containsExactly("keep", "confirm").inOrder()
    }

    @Test
    fun whileBusyTheButtonsAreDisabled() {
        mode = DetailMode.ConfirmingDelete
        busy = true
        show(SampleUi.detailEditable)
        compose.onNodeWithTag("detail_confirm_delete").assertIsNotEnabled()
        compose.onNodeWithTag("detail_keep").assertIsNotEnabled()
    }

    @Test
    fun theCloseButtonCloses() {
        show(SampleUi.detailEditable)
        compose.onNodeWithTag("sheet_close").performClick()
        assertThat(calls).containsExactly("close")
    }
}
```

- [ ] **Step 3: Name the sheet's values**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`:
- In `CalendarType`, delete nothing yet (Task 11 removes `link`) and add before its closing brace:
```kotlin

    /** 34 sp / 700, −0.5 tracking: the detail sheet's title. */
    val sheetTitle = weekTitle

    /** 15 sp / 400: info-row labels. */
    val infoLabel = subtitle

    /** 17 sp / 600: info-row values. */
    val infoValue = HhType.rowTitle

    /** 13 sp / 700: "Syncing to …". */
    val syncingPill = HhType.label.copy(fontWeight = FontWeight.W700)

    /** 16 sp / 700: explanation card titles. */
    val noteTitle = HhType.body.copy(fontWeight = FontWeight.W700)

    /** 14 sp / 400: explanation card text. */
    val noteBody = HhType.secondary

    /** 15 sp / 700: "Assign to…". */
    val assignButton = HhType.buttonLabel

    /** 16 sp / 600: person chips. */
    val personChip = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 17 sp / 700: Delete, Keep event, Delete event. */
    val footerButton = HhType.rowTitle.copy(fontWeight = FontWeight.W700)

    /** 18 sp / 700: "Delete this event?". */
    val confirmTitle = HhType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.W700)
```
- In `CalendarDimens`, add before `/** Chip tint: …`:
```kotlin
    // Event detail sheet (hand-off §7): padding 28×30×26; a 6 dp colour bar 14 dp from the title; close 12 dp away.
    val sheetPaddingTop = 28.dp
    val sheetPaddingH = 30.dp
    val sheetPaddingBottom = 26.dp
    val sheetTitleBar = 6.dp
    val sheetTitleBarGap = 14.dp
    val sheetHeaderGap = 12.dp

    // Syncing pill: 30 dp, radius 15, `surf2`, an 18 dp cloud_upload; 8 dp above the title.
    val syncingPillHeight = 30.dp
    val syncingPillRadius = 15.dp
    val syncingPillPaddingH = 12.dp
    val syncingPillIcon = 18.dp
    val syncingPillIconGap = 6.dp
    val syncingPillBottom = 8.dp

    // Info card: `surf`, radius 22, padding 2×18, rows at least 58 dp with 1 dp `line` dividers;
    // 22 dp icon, 104 dp label column, 12 dp person dot.
    val infoRadius = 22.dp
    val infoPaddingV = 2.dp
    val infoPaddingH = 18.dp
    val infoRowMin = 58.dp
    val infoDivider = 1.dp
    val infoIcon = 22.dp
    val infoIconGap = 12.dp
    val infoLabelWidth = 104.dp
    val infoDot = 12.dp
    val infoDotGap = 8.dp

    // Explanation cards (read-only, repeating, untagged): radius 22, padding 16×18, 26 dp icon.
    val noteRadius = 22.dp
    val notePaddingV = 16.dp
    val notePaddingH = 18.dp
    val noteIcon = 26.dp
    val noteIconGap = 14.dp
    val noteTextGap = 2.dp

    // Assign: "Assign to…" 44 dp, radius 22; person chips 48 dp, `surf`, radius 24, 12 dp dot, 8 apart.
    val assignTop = 12.dp
    val assignButtonHeight = 44.dp
    val assignButtonRadius = 22.dp
    val assignButtonPaddingH = 18.dp
    val personChipHeight = 48.dp
    val personChipRadius = 24.dp
    val personChipPaddingH = 18.dp
    val personChipGap = 8.dp
    val personChipDot = 12.dp
    val personChipDotGap = 8.dp

    // Footer: Delete 60 dp, padding 0 26, radius 30, `surf2`, `danger` text.
    val footerButtonHeight = 60.dp
    val footerButtonRadius = 30.dp
    val deleteButtonPaddingH = 26.dp
    val footerIcon = 24.dp
    val footerIconGap = 8.dp

    // Delete confirmation: `dangerSoft`, radius 24, padding 20, 16 between blocks; buttons 12 apart.
    val confirmRadius = 24.dp
    val confirmPadding = 20.dp
    val confirmGap = 16.dp
    val confirmButtonGap = 12.dp

```

- [ ] **Step 4: Write the sheet**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheet.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.ReadOnlyReason
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhSheet

enum class DetailMode { Idle, ChoosingPerson, ConfirmingDelete }

/** The Calendar row's value: the source's name, marked read-only when it isn't the master calendar. */
internal fun calendarLabel(event: EventUi): String =
    if (event.readOnlyReason == ReadOnlyReason.OtherCalendar) "${event.sourceName} · read-only" else event.sourceName

/**
 * Hand-off §7 "Sheet 1 — Event detail". Stateless: the host decides [mode] and [busy]. Buttons are never hidden
 * for permission reasons; the checks happen when they are tapped. Delete runs the guard ([onDelete]); only the
 * confirmation's "Delete event" deletes ([onConfirmDelete]).
 */
@Composable
fun EventDetailSheet(
    detail: EventDetailUi,
    people: List<Person>,
    mode: DetailMode,
    busy: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
    onConfirmDelete: () -> Unit,
    onChoosePerson: () -> Unit,
    onAssign: (Person) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val e = detail.event
    HhSheet(
        padding = PaddingValues(
            start = CalendarDimens.sheetPaddingH,
            end = CalendarDimens.sheetPaddingH,
            top = CalendarDimens.sheetPaddingTop,
            bottom = CalendarDimens.sheetPaddingBottom,
        ),
        modifier = modifier.testTag("detail_sheet"),
    ) {
        Header(e, onClose)
        InfoCard(detail)
        when {
            e.readOnlyReason == ReadOnlyReason.OtherCalendar -> Note(
                icon = "lock",
                title = "From ${e.sourceName} (read-only)",
                body = "This is a subscribed calendar, so it can't be changed here.",
            )
            e.readOnlyReason == ReadOnlyReason.Recurring -> Note(
                icon = "event_repeat",
                title = "Repeating event",
                body = "Edit repeating events in ${e.connectionLabel} on your phone.",
            )
            e.untagged -> Note(
                icon = "smartphone",
                title = "Added from a phone",
                body = "Showing as Family until someone assigns it.",
                background = c.accentSoft,
                iconTint = c.accent,
            ) {
                Spacer(Modifier.height(CalendarDimens.assignTop))
                if (mode == DetailMode.ChoosingPerson) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.personChipGap),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        people.forEach { person -> PersonChip(person, enabled = !busy) { onAssign(person) } }
                    }
                } else {
                    AssignButton(enabled = !busy, onClick = onChoosePerson)
                }
            }
        }
        if (e.editable) {
            Spacer(Modifier.weight(1f))
            if (mode == DetailMode.ConfirmingDelete) {
                DeleteConfirmation(e, busy, onKeep, onConfirmDelete)
            } else {
                DeleteButton(enabled = !busy, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun Header(e: EventUi, onClose: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.sheetHeaderGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
            ColourBar(Color(e.person.color), width = CalendarDimens.sheetTitleBar)
            Spacer(Modifier.width(CalendarDimens.sheetTitleBarGap))
            Column {
                if (e.syncing) {
                    SyncingPill(e.connectionLabel)
                    Spacer(Modifier.height(CalendarDimens.syncingPillBottom))
                }
                Text(e.title, style = CalendarType.sheetTitle, color = c.ink)
            }
        }
        HhCloseButton(onClick = onClose)
    }
}

@Composable
private fun SyncingPill(connectionLabel: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.syncingPillIconGap),
        modifier = Modifier
            .testTag("detail_syncing")
            .height(CalendarDimens.syncingPillHeight)
            .clip(RoundedCornerShape(CalendarDimens.syncingPillRadius))
            .background(c.surf2)
            .padding(horizontal = CalendarDimens.syncingPillPaddingH),
    ) {
        HhIcon("cloud_upload", size = CalendarDimens.syncingPillIcon, tint = c.mute)
        Text("Syncing to $connectionLabel…", style = CalendarType.syncingPill, color = c.mute, maxLines = 1)
    }
}

@Composable
private fun InfoCard(detail: EventDetailUi) {
    val e = detail.event
    HhCard(
        modifier = Modifier.fillMaxWidth().testTag("detail_info"),
        radius = CalendarDimens.infoRadius,
        padding = PaddingValues(horizontal = CalendarDimens.infoPaddingH, vertical = CalendarDimens.infoPaddingV),
    ) {
        InfoRow("schedule", "When") { Value(detail.whenLabel) }
        Divider()
        InfoRow("person", "For") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.infoDotGap)) {
                Box(Modifier.size(CalendarDimens.infoDot).clip(CircleShape).background(Color(e.person.color)))
                Value(e.person.name)
            }
        }
        Divider()
        InfoRow("edit_note", "Created by") { Value(e.createdBy) }
        Divider()
        InfoRow("calendar_month", "Calendar") { Value(calendarLabel(e)) }
        if (e.recurring) {
            Divider()
            InfoRow("repeat", "Repeats") { Value("Yes") }
        }
    }
}

@Composable
private fun InfoRow(icon: String, label: String, value: @Composable () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = CalendarDimens.infoRowMin),
    ) {
        HhIcon(icon, size = CalendarDimens.infoIcon, tint = c.mute)
        Spacer(Modifier.width(CalendarDimens.infoIconGap))
        Text(label, style = CalendarType.infoLabel, color = c.mute, maxLines = 1, modifier = Modifier.width(CalendarDimens.infoLabelWidth))
        value()
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = CalendarType.infoValue, color = Culvery.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(CalendarDimens.infoDivider).background(Culvery.colors.line))
}

@Composable
private fun Note(
    icon: String,
    title: String,
    body: String,
    background: Color = Culvery.colors.surf,
    iconTint: Color = Culvery.colors.mute,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Culvery.colors
    HhCard(
        modifier = Modifier.fillMaxWidth().testTag("detail_note"),
        radius = CalendarDimens.noteRadius,
        color = background,
        padding = PaddingValues(horizontal = CalendarDimens.notePaddingH, vertical = CalendarDimens.notePaddingV),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.noteIconGap)) {
            HhIcon(icon, size = CalendarDimens.noteIcon, tint = iconTint)
            Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.noteTextGap)) {
                Text(title, style = CalendarType.noteTitle, color = c.ink)
                Text(body, style = CalendarType.noteBody, color = c.mute)
            }
        }
        content()
    }
}

@Composable
private fun AssignButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("detail_assign")
            .height(CalendarDimens.assignButtonHeight)
            .clip(RoundedCornerShape(CalendarDimens.assignButtonRadius))
            .background(c.accent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CalendarDimens.assignButtonPaddingH),
    ) {
        Text("Assign to…", style = CalendarType.assignButton, color = c.accentInk, maxLines = 1)
    }
}

@Composable
private fun PersonChip(person: Person, enabled: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.personChipDotGap),
        modifier = Modifier
            .testTag("assign_${person.name}")
            .height(CalendarDimens.personChipHeight)
            .clip(RoundedCornerShape(CalendarDimens.personChipRadius))
            .background(c.surf)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CalendarDimens.personChipPaddingH),
    ) {
        Box(Modifier.size(CalendarDimens.personChipDot).clip(CircleShape).background(Color(person.color)))
        Text(person.name, style = CalendarType.personChip, color = c.ink, maxLines = 1)
    }
}

@Composable
private fun DeleteButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerIconGap),
        modifier = Modifier
            .testTag("detail_delete")
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

@Composable
private fun DeleteConfirmation(e: EventUi, busy: Boolean, onKeep: () -> Unit, onConfirmDelete: () -> Unit) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.confirmGap),
        modifier = Modifier
            .testTag("detail_confirm")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.confirmRadius))
            .background(c.dangerSoft)
            .padding(CalendarDimens.confirmPadding),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.noteIconGap)) {
            HhIcon("delete", size = CalendarDimens.footerIcon, tint = c.danger)
            Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.noteTextGap)) {
                Text("Delete this event?", style = CalendarType.confirmTitle, color = c.ink)
                Text(
                    "“${e.title}” will be removed from ${e.connectionLabel} for everyone.",
                    style = CalendarType.noteBody,
                    color = c.mute,
                )
            }
        }
        // Keep event sits on the left, where Delete was, so a double tap on Delete is harmless.
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.confirmButtonGap), modifier = Modifier.fillMaxWidth()) {
            ConfirmButton("Keep event", null, c.surf, c.ink, "detail_keep", !busy, onKeep, Modifier.weight(1f))
            ConfirmButton("Delete event", "delete_forever", c.danger, c.dangerInk, "detail_confirm_delete", !busy, onConfirmDelete, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConfirmButton(
    text: String,
    icon: String?,
    background: Color,
    content: Color,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerIconGap, Alignment.CenterHorizontally),
        modifier = modifier
            .testTag(tag)
            .height(CalendarDimens.footerButtonHeight)
            .clip(RoundedCornerShape(CalendarDimens.footerButtonRadius))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (icon != null) HhIcon(icon, size = CalendarDimens.footerIcon, tint = content)
        Text(text, style = CalendarType.footerButton, color = content, maxLines = 1)
    }
}
```

- [ ] **Step 5: Run the sheet tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventDetailSheetTest*"`
Expected: PASS (10 tests).

- [ ] **Step 6: Write the failing host tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHostTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.ScriptedWriter
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.TestAccess
import uk.co.siland.culvery.capability.calendar.WRITE_ATTEMPT_MS
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.testAccess
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

/**
 * The sheet wired to a real repository, editor and access rules; PIN pads are answered from a queue. Access and
 * the editor share one recording toaster, `access.toasts`, as the app shares one.
 */
@RunWith(AndroidJUnit4::class)
class EventDetailHostTest {
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
    private val today = LocalDate.of(2026, 9, 23)
    private val writer = ScriptedWriter("calendar.a")
    private var closed = 0

    @Before
    fun setUp() = runBlocking {
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
        store.applySync(
            "c1", "s-family", DateRange(today.minusDays(1), today.plusDays(14), london),
            SyncResult(
                listOf(event("dinner", "Dinner with Jo & Priya", access.alex.id.value), event("plumber", "Plumber quote call", null)),
                emptyList(), null, fullReplace = true,
            ),
        )
        repo = CalendarRepository(store, household, zone, emptySet(), setOf(writer))
        editor = CalendarEditor(
            store, setOf(writer), access.control, access.toasts, zone, WallClock { System.currentTimeMillis() },
            scope, {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        calendar.close()
        householdDb.close()
    }

    private fun event(id: String, title: String, createdBy: String?): RemoteEvent {
        val start = today.atTime(19, 30).atZone(london).toInstant()
        return RemoteEvent(id, title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), false, null, createdBy)
    }

    private fun show(id: String) = compose.setContent {
        CulveryTheme(dark = true) {
            EventDetailHost(EventRef("c1", "s-family", id), today, repo, editor, onClose = { closed++ })
        }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun deleteAsksForThePinBeforeTheConfirmationThenDeletesWithoutAskingAgain() {
        access.answer(TestAccess.ALEX)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        waitForText("Delete this event?")
        assertThat(access.requests).hasSize(1)
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.requests).hasSize(1)
        assertThat(writer.calls).containsExactly("delete:dinner")
    }

    @Test
    fun aRejectedDeleteKeepsTheSheetOpen() {
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        waitForText("Delete this event?")
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        // The editor toasts the refusal; the sheet goes back to its footer and stays open.
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Delete this event?").fetchSemanticsNodes().isEmpty() }
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Event is locked")
        compose.onNodeWithTag("detail_delete").assertExists()
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun deleteEventIgnoresASecondTapWhileBusy() {
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        access.answer(TestAccess.ALEX)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        waitForText("Delete this event?")
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        compose.waitUntil(5_000) { writer.calls.isNotEmpty() }
        gate.complete(Unit)
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("delete:dinner")
    }

    @Test
    fun aChildIsRefusedWithAToastAndNoConfirmation() {
        access.answer(TestAccess.MIA)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        // Access control shows refusals itself, through the toaster.
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        compose.onNodeWithTag("detail_confirm").assertDoesNotExist()
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun assigningAPhoneEventTagsItAndClosesTheChips() {
        access.answer(TestAccess.SAM)
        show("plumber")
        waitForText("Added from a phone")
        compose.onNodeWithTag("detail_assign").performClick()
        compose.onNodeWithTag("assign_Mia").performClick()
        compose.waitUntil(5_000) { writer.calls.isNotEmpty() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Added from a phone").fetchSemanticsNodes().isEmpty()
        }
        assertThat(writer.calls).containsExactly("update:plumber")
        assertThat(runBlocking { store.eventNow(EventRef("c1", "s-family", "plumber")) }?.forPerson).isEqualTo(access.mia.id.value)
    }

    @Test
    fun theSheetClosesWhenItsEventDisappears() {
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        runBlocking { store.applyDeleted(EventRef("c1", "s-family", "dinner")) }
        compose.waitUntil(5_000) { closed > 0 }
    }
}
```

- [ ] **Step 7: Write the host and the opener**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailHost.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EditResult
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.LocalOverlayHost

/** Wraps a loaded detail so "still loading" (null) differs from "gone" (Loaded(null)). */
private class Loaded(val detail: EventDetailUi?)

/**
 * The detail sheet for [ref], kept live from the repository: it closes itself when the event disappears.
 * Only one action runs at a time; while one runs, further taps are ignored. The editor toasts each outcome;
 * the host only decides whether the sheet stays open.
 */
@Composable
internal fun EventDetailHost(
    ref: EventRef,
    today: LocalDate,
    repo: CalendarRepository,
    editor: CalendarEditor,
    onClose: () -> Unit,
) {
    val loaded: Loaded? by remember(ref, today) { repo.event(ref, today).map { Loaded(it) } }.collectAsState(initial = null)
    val people: List<Person> by repo.people.collectAsState(initial = emptyList())
    var mode by remember(ref) { mutableStateOf(DetailMode.Idle) }
    var busy by remember(ref) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val state = loaded ?: return
    val detail = state.detail
    if (detail == null) {
        LaunchedEffect(ref) { onClose() }
        return
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } finally {
                busy = false
            }
        }
    }

    EventDetailSheet(
        detail = detail,
        people = people,
        mode = mode,
        busy = busy,
        onClose = onClose,
        onDelete = { run { if (editor.mayDelete(ref)) mode = DetailMode.ConfirmingDelete } },
        onKeep = { mode = DetailMode.Idle },
        onConfirmDelete = {
            run {
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
            run {
                when (editor.assign(ref, person.id)) {
                    EditResult.Done, EditResult.Queued -> mode = DetailMode.Idle
                    is EditResult.Rejected, EditResult.Cancelled -> Unit
                    EditResult.NotEditable -> onClose()
                }
            }
        },
    )
}

/** Opens an event's detail sheet through the shell's overlay host. */
@Composable
internal fun rememberEventOpener(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate): (EventRef) -> Unit {
    val overlay = LocalOverlayHost.current
    return remember(overlay, repo, editor, today) {
        { ref -> overlay.show { EventDetailHost(ref, today, repo, editor, onClose = overlay::dismiss) } }
    }
}
```

- [ ] **Step 8: Run the host tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventDetailHostTest*"`
Expected: PASS (6 tests).

- [ ] **Step 9: Write the screenshot test**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/DetailScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.ui.PinPadSheet
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens

/** The sheet as the shell shows it: against the right edge, over the scrim, on the 1280×800 canvas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(
        name: String,
        dark: Boolean,
        detail: EventDetailUi,
        mode: DetailMode = DetailMode.Idle,
        over: @Composable () -> Unit = {},
    ) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        EventDetailSheet(detail, SampleUi.household, mode, busy = false, {}, {}, {}, {}, {}, {})
                    }
                    over()
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun editableDark() = snap("detail_editable_dark", true, SampleUi.detailEditable)
    @Test fun editableLight() = snap("detail_editable_light", false, SampleUi.detailEditable)
    @Test fun readOnlyFeedDark() = snap("detail_readonly_feed_dark", true, SampleUi.detailReadOnlyFeed)
    @Test fun readOnlyFeedLight() = snap("detail_readonly_feed_light", false, SampleUi.detailReadOnlyFeed)
    @Test fun recurringDark() = snap("detail_recurring_dark", true, SampleUi.detailRecurring)
    @Test fun recurringLight() = snap("detail_recurring_light", false, SampleUi.detailRecurring)
    @Test fun untaggedDark() = snap("detail_untagged_dark", true, SampleUi.detailUntagged, DetailMode.ChoosingPerson)
    @Test fun untaggedLight() = snap("detail_untagged_light", false, SampleUi.detailUntagged, DetailMode.ChoosingPerson)
    @Test fun syncingDark() = snap("detail_syncing_dark", true, SampleUi.detailSyncing)
    @Test fun syncingLight() = snap("detail_syncing_light", false, SampleUi.detailSyncing)
    @Test fun deleteConfirmDark() = snap("detail_delete_confirm_dark", true, SampleUi.detailEditable, DetailMode.ConfirmingDelete)
    @Test fun deleteConfirmLight() = snap("detail_delete_confirm_light", false, SampleUi.detailEditable, DetailMode.ConfirmingDelete)

    @Test
    fun pinOverTheSheetDark() = snap("detail_pin_dark", true, SampleUi.detailEditable) {
        PinPadSheet("Change any event", PinReason.Delete, error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {}, overSheet = true)
    }

    @Test
    fun pinOverTheSheetLight() = snap("detail_pin_light", false, SampleUi.detailEditable) {
        PinPadSheet("Change any event", PinReason.Delete, error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {}, overSheet = true)
    }
}
```

- [ ] **Step 10: Record the sheet screenshots and look at every one**

Run: `./gradlew :capability:calendar:recordRoborazziDebug --tests "*DetailScreenshotTest*"`
Expected: `BUILD SUCCESSFUL`, and 14 PNGs `capability/calendar/src/test/screenshots/detail_*.png`.

Compare each image with its hand-off counterpart in `docs/design/house_hub_handoff/screenshots/calendar-sheets/`. In the hand-off the week view shows behind the scrim; here the background is plain.

Every sheet:
- 600 dp wide on the right, `bg`, with a 1 dp `line` left border;
- padding 28 top, 30 sides, 26 bottom;
- a 48 dp round `surf2` close button at the top right;
- a 6 dp person-colour bar left of a 34 sp bold title;
- the info card: `surf`, radius 22, and rows at least 58 dp tall with 1 dp dividers. Each row has a muted icon, a muted 15 sp label in a 104 dp column, and a 17 sp semibold value.

State by state:
- `detail_editable_*` (03):
  - When "Today · 19:30–21:00"; For "● Alex"; Created by "Alex"; Calendar "Family calendar".
  - A 60 dp `surf2` Delete pill with coral text and a trash icon sits at the bottom left.
  - There is no Edit button (2b-2).
- `detail_readonly_feed_*` (04):
  - "INSET day — no school"; When "Sat 26 Sep · All day"; Created by "Calendar feed"; Calendar "School terms · read-only".
  - A `surf` card with a lock icon reads "From School terms (read-only)" over the muted line.
  - No footer.
- `detail_recurring_*` (05):
  - A Repeats row reading "Yes".
  - An `event_repeat` card: "Repeating event" / "Edit repeating events in Sample calendar on your phone."
  - No footer.
- `detail_untagged_*` (06):
  - For "● Family"; Created by "Added from phone".
  - A green-tinted `accentSoft` card with a green phone icon: "Added from a phone" / "Showing as Family until someone assigns it."
  - Below the text, three 48 dp `surf` chips: ● Alex, ● Sam, ● Mia.
  - Delete at the bottom.
  - The collapsed state (a 44 dp green "Assign to…" button in place of the chips) has no image; `anUntaggedEventOffersAssignAndKeepsDelete` covers it, and the walkthrough shows it.
- `detail_syncing_*` (07): a 30 dp `surf2` pill above the title, reading "Syncing to Sample calendar…" with a `cloud_upload` icon. The colour bar spans both the pill and the title.
- `detail_delete_confirm_*` (08):
  - The Delete pill is replaced by a coral-tinted `dangerSoft` panel with radius 24. It holds a trash icon and "Delete this event?" at 18 sp bold, then "“Dinner with Jo & Priya” will be removed from Sample calendar for everyone."
  - Two 60 dp buttons: "Keep event" (`surf`) on the left and "Delete event" (coral with dark ink and `delete_forever`) on the right.
- `detail_pin_*` (09):
  - The PIN pad's half-dark scrim covers only the 600 dp sheet area.
  - Its 400 dp card is centred in that area, asking "Who's this?" / "Enter your PIN to delete this event. It also records who made the change."

Nothing may be clipped or overlapping. The titles and "Keep event" / "Delete event" must fit on one line. If something doesn't fit, note which image and which element.

Run: `./gradlew :capability:calendar:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Commit**

```bash
git add capability/calendar
git commit -m "Add the event detail sheet with delete confirmation, assign and syncing states"
```

---

### Task 11: Opening the sheet from Today rows and week chips, badges, no Coming up link

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Badges.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/TodayCard.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ComingUpCard.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Components.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CardHosts.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt`
- Modify: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/SampleUi.kt`
- Test (modify): `…/calendar/CalendarCapabilityTest.kt`, `…/calendar/ui/CardHostsMidnightRolloverTest.kt`, `…/calendar/ui/CardsTest.kt`, `…/calendar/ui/WeekViewTest.kt`, `…/calendar/ui/CardScreenshotTest.kt`, `…/calendar/ui/WeekScreenshotTest.kt`
- Test (create): `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/OpenEventTest.kt`
- Re-record:
  - `capability/calendar/src/test/screenshots/coming_up_{dark,light,busy_dark}.png`
  - `capability/calendar/src/test/screenshots/week_{dark,light,stale_dark,needs_sign_in_dark}.png`
  - `app/src/test/screenshots/home_calendar_{dark,light}.png`
- Record: `today_badges_dark.png`, `week_syncing_dark.png`

**Interfaces:**
- Consumes:
  - `EventUi.badges()`, `Badge`, `EventRef.listKey` (Task 9)
  - `rememberEventOpener`, `RecordingOverlay` (Task 10)
  - `CalendarEditor` (Task 8), `LocalOverlayHost` (Task 2)
- Produces:
  - `@Composable internal fun EventBadges(event: EventUi, size: Dp, modifier: Modifier = Modifier)`
  - `TodayCard(events: List<EventUi>?, modifier: Modifier = Modifier, onOpen: (EventRef) -> Unit = {})`. Rows are buttons, with the click label "Open".
  - `WeekView(state: WeekViewState, modifier: Modifier = Modifier, onOpen: (EventRef) -> Unit = {})`. Chips are buttons.
  - `ComingUpCard(days, modifier)` without the "Week ›" link; `HeaderLink` and `CalendarType.link` are removed.
  - `TodayCardHost(repo, editor, today)` and `WeekViewHost(repo, editor, today, nowMillis)`
  - `CalendarCapability @Inject constructor(repo, zone, clock, editor: CalendarEditor)`
  - Test helper `internal fun stubEditor(store: CalendarStore, zone: HouseholdZone): CalendarEditor`
  - `SampleUi.todayWithBadges`, `SampleUi.weekWithSyncing`; INSET day in `SampleUi.comingUp` is `ReadOnlyReason.OtherCalendar`

- [ ] **Step 1: Update the sample UI and add a stub editor**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/SampleUi.kt`:
- in `comingUp`, replace `allDay("INSET day — no school", family),` with:
```kotlin
            allDay("INSET day — no school", family).copy(readOnlyReason = ReadOnlyReason.OtherCalendar),
```
- add at the end of the object:
```kotlin
    /** Today rows with every badge: syncing, another calendar, repeating. */
    val todayWithBadges = listOf(
        event("Dinner with Jo & Priya", "19:30–21:00", alex).copy(syncing = true),
        allDay("INSET day — no school", family).copy(readOnlyReason = ReadOnlyReason.OtherCalendar),
        event("Swimming", "16:00–17:00", mia, recurring = true).copy(syncing = true),
    )

    /** Hand-off 07's week: Dinner with Jo & Priya is waiting to sync. */
    val weekWithSyncing = week.copy(
        days = week.days.mapIndexed { i, d ->
            if (i != 0) d else d.copy(events = d.events.map { if (it.title == "Dinner with Jo & Priya") it.copy(syncing = true) else it })
        },
    )
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/StubEditor.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.plugin.WallClock

private object NobodyMay : AccessControl {
    override val session: StateFlow<Identified?> = MutableStateFlow(null)

    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
    ): Authorised? = null

    override fun lock() = Unit
}

/** An editor that can't change anything, for tests that only need the hosts to compose and open sheets. */
internal fun stubEditor(store: CalendarStore, zone: HouseholdZone): CalendarEditor = CalendarEditor(
    store, emptySet(), NobodyMay, RecordingToaster(), zone, WallClock { 0L },
    CoroutineScope(Dispatchers.Unconfined), {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,
)
```

- [ ] **Step 2: Write the failing card and week tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt`:
- add imports `androidx.compose.ui.test.assert`, `androidx.compose.ui.test.hasClickAction` and `uk.co.siland.culvery.capability.calendar.EventRef`;
- delete the test `weekLinkOnComingUpOpensTheCalendarTab`;
- add at the end of the class:
```kotlin
    @Test
    fun comingUpHasNoWeekLink() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("Week ›").assertDoesNotExist()
    }

    @Test
    fun todayRowsAreButtonsThatOpenTheirEvent() {
        val opened = mutableListOf<EventRef>()
        show { TodayCard(SampleUi.today, onOpen = { opened += it }) }
        compose.onNodeWithText("School run").assert(hasClickAction())
        compose.onNodeWithText("School run").performClick()
        assertThat(opened).containsExactly(EventRef("sample", "family", "School run"))
    }

    // Counted on the merged tree, as a person hears them: each row merges its badges' descriptions, so a row
    // counts once per badge kind. Don't switch these to useUnmergedTree.
    @Test
    fun todayRowsShowEachOfTheirBadges() {
        show { TodayCard(SampleUi.todayWithBadges) }
        compose.onAllNodesWithContentDescription("Syncing").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Read-only calendar").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Repeats").assertCountEquals(1)
    }
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekViewTest.kt`:
- add import `uk.co.siland.culvery.capability.calendar.EventRef`;
- add at the end of the class:
```kotlin
    @Test
    fun insetDayFromTheSchoolFeedShowsTheLockBadge() {
        show { WeekView(state()) }
        compose.onAllNodesWithContentDescription("Read-only calendar").assertCountEquals(1)
    }

    @Test
    fun aSyncingChipShowsCloudUpload() {
        show { WeekView(WeekViewState(SampleUi.weekWithSyncing, SampleUi.TODAY, sync(), now)) }
        compose.onAllNodesWithContentDescription("Syncing").assertCountEquals(1)
    }

    @Test
    fun tappingAChipOpensItsEvent() {
        val opened = mutableListOf<EventRef>()
        show { WeekView(state(), onOpen = { opened += it }) }
        compose.onNodeWithText("Parkrun").performClick()
        assertThat(opened).containsExactly(EventRef("sample", "family", "Parkrun"))
    }
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/OpenEventTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
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
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class OpenEventTest {
    @get:Rule val compose = createComposeRule()

    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val overlay = RecordingOverlay()
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var repo: CalendarRepository
    private lateinit var editor: CalendarEditor

    @Before
    fun setUp() = runBlocking {
        calendar = calendarDb()
        householdDb = householdDb()
        val store = CalendarStore(calendar)
        val household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        val zone = HouseholdZone(household)
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
}
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarCapabilityTest.kt`, replace `capability = CalendarCapability(CalendarRepository(store, household, zone, emptySet(), emptySet()), zone, WallClock { 0L })` with:
```kotlin
        capability = CalendarCapability(
            CalendarRepository(store, household, zone, emptySet(), emptySet()), zone, WallClock { 0L }, stubEditor(store, zone),
        )
```

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardHostsMidnightRolloverTest.kt`:
- add imports `uk.co.siland.culvery.capability.calendar.CalendarEditor`, `uk.co.siland.culvery.capability.calendar.stubEditor` and `uk.co.siland.culvery.core.plugin.LocalOverlayHost`;
- add a field `private lateinit var editor: CalendarEditor`, and in `setUp`, after `repo = …`, add `editor = stubEditor(store, zone)`;
- in both tests, replace `CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator())` with `CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator(), LocalOverlayHost provides RecordingOverlay())`;
- replace `TodayCardHost(repo, rememberToday(zone, clock, ticks))` with `TodayCardHost(repo, editor, rememberToday(zone, clock, ticks))`;
- replace `WeekViewHost(repo, today, rememberNowMillis(clock, ticks))` with `WeekViewHost(repo, editor, today, rememberNowMillis(clock, ticks))`.

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: compilation FAILS: `TodayCard` and `WeekView` have no `onOpen`, `TodayCardHost` and `WeekViewHost` take no editor, and `CalendarCapability` has no fourth parameter.

- [ ] **Step 4: Add the badges**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Badges.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.badges
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon

/** Hand-off §7 badges, muted: 15 dp on week chips, 20 dp on Today rows. Nothing when there are none. */
@Composable
internal fun EventBadges(event: EventUi, size: Dp, modifier: Modifier = Modifier) {
    val badges = event.badges()
    if (badges.isEmpty()) return
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.badgeGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        badges.forEach { HhIcon(it.icon, size = size, tint = c.mute, contentDescription = it.description) }
    }
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`:
- in `CalendarType`, delete:
```kotlin

    /** 14 sp / 400: header text links ("Week ›"). */
    val link = HhType.secondary
```
- in `CalendarDimens`, add after `val chipBadge = 15.dp`:
```kotlin
    val badgeGap = 4.dp
```

- [ ] **Step 5: Make Today rows buttons with badges**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/TodayCard.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import uk.co.siland.culvery.core.ui.HhType

/**
 * Hand-off Home "Today" card. [events] null while loading: shows nothing rather than a false "Nothing on today".
 * Each row is a button (hand-off §7) that opens its event through [onOpen].
 */
@Composable
fun TodayCard(events: List<EventUi>?, modifier: Modifier = Modifier, onOpen: (EventRef) -> Unit = {}) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(modifier = modifier.fillMaxSize().testTag("calendar_today"), radius = CalendarDimens.cardRadius) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Today", style = HhType.cardTitle, color = c.ink, modifier = Modifier.weight(1f))
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

- [ ] **Step 6: Make week chips buttons with badges**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt`:
- add import `uk.co.siland.culvery.capability.calendar.EventRef`;
- change the `WeekView` signature to:
```kotlin
fun WeekView(state: WeekViewState, modifier: Modifier = Modifier, onOpen: (EventRef) -> Unit = {}) {
```
  and, inside it, replace `DayColumn(day, isToday = day.date == state.today, modifier = Modifier.weight(1f).fillMaxHeight())` with:
```kotlin
                DayColumn(day, isToday = day.date == state.today, onOpen = onOpen, modifier = Modifier.weight(1f).fillMaxHeight())
```
- change `private fun DayColumn(day: DayUi, isToday: Boolean, modifier: Modifier)` to `private fun DayColumn(day: DayUi, isToday: Boolean, onOpen: (EventRef) -> Unit, modifier: Modifier)`, and inside it replace `items(day.events, key = { it.ref.listKey }) { EventChip(it) }` with:
```kotlin
            items(day.events, key = { it.ref.listKey }) { EventChip(it, onOpen) }
```
- replace the whole `EventChip` function with:
```kotlin
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
```
- `HhIcon` is no longer used in `WeekView.kt` except by `ReconnectChip`, so keep its import.

- [ ] **Step 7: Remove the Coming up link**

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ComingUpCard.kt`:
- delete the imports `uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID` and `uk.co.siland.culvery.core.plugin.LocalShellNavigator`, and add `import androidx.compose.foundation.layout.heightIn`;
- delete the line `val navigator = LocalShellNavigator.current`;
- replace the header row
```kotlin
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Coming up", style = HhType.cardTitle, color = c.ink, modifier = Modifier.weight(1f))
            HeaderLink("Week ›", onClick = { navigator.openTab(CALENDAR_TAB_ID) })
        }
```
with
```kotlin
        // One way from Home to the week (2b-1 D6): Today's Week pill. The row keeps the old link's 44 dp height,
        // so the day columns stay where MAX_ROWS was measured.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = CalendarDimens.touchTarget),
        ) {
            Text("Coming up", style = HhType.cardTitle, color = c.ink)
        }
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Components.kt`, delete the `HeaderLink` function with its KDoc, and the now-unused import `androidx.compose.foundation.layout.heightIn`.

- [ ] **Step 8: Give the hosts the editor and open sheets**

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

@Composable
internal fun TodayCardHost(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate) {
    val open = rememberEventOpener(repo, editor, today)
    val events: List<EventUi>? by remember(today) { repo.day(today) }.collectAsState(initial = null)
    TodayCard(events, onOpen = open)
}

@Composable
internal fun ComingUpCardHost(repo: CalendarRepository, today: LocalDate) {
    val days: List<DayUi>? by remember(today) { repo.days(today.plusDays(1), 3) }.collectAsState(initial = null)
    ComingUpCard(days)
}

/** Today plus six days; [today] moves at midnight, so the week rolls with it. Shows nothing until both flows load. */
@Composable
internal fun WeekViewHost(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate, nowMillis: Long) {
    val open = rememberEventOpener(repo, editor, today)
    val week: WeekUi? by remember(today) { repo.week(today) }.collectAsState(initial = null)
    val sync: SyncStatusUi? by repo.syncStatus.collectAsState(initial = null)
    val w = week ?: return
    val s = sync ?: return
    WeekView(WeekViewState(w, today, s, nowMillis), onOpen = open)
}
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`:
- change the constructor to:
```kotlin
class CalendarCapability @Inject constructor(
    private val repo: CalendarRepository,
    private val zone: HouseholdZone,
    private val clock: WallClock,
    private val editor: CalendarEditor,
) : Capability {
```
- replace `TodayCardHost(repo, rememberToday(zone, clock))` with `TodayCardHost(repo, editor, rememberToday(zone, clock))`;
- replace `WeekViewHost(repo, today = todayIn(rememberZoneId(zone), now), nowMillis = now)` with `WeekViewHost(repo, editor, today = todayIn(rememberZoneId(zone), now), nowMillis = now)`.

- [ ] **Step 9: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS.
- `recurringTodayRowShowsTheRepeatBadge` (1) and `recurringChipsShowTheRepeatBadge` (3) still pass, now through `EventBadges`.
- `todayShowsTheFourthRowInTheTallCard` still passes: badges replaced the old `repeat` icon at the same 20 dp.

- [ ] **Step 10: Add the badge screenshots**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardScreenshotTest.kt`, add:
```kotlin
    @Test fun todayBadgesDark() = snap("today_badges_dark", true, TALL_W, TALL_H) { TodayCard(SampleUi.todayWithBadges) }
```
In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekScreenshotTest.kt`:
- change `snap` to take the week:
```kotlin
    private fun snap(name: String, dark: Boolean, sync: SyncStatusUi, week: WeekUi = SampleUi.week) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark) {
                    Box(Modifier.testTag("shot").background(Culvery.colors.bg).size(CONTENT_W, CONTENT_H)) {
                        WeekView(WeekViewState(week, SampleUi.TODAY, sync, now))
                    }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }
```
- add `import uk.co.siland.culvery.capability.calendar.WeekUi` and:
```kotlin
    @Test fun weekSyncingDark() = snap("week_syncing_dark", true, fresh, SampleUi.weekWithSyncing)
```

- [ ] **Step 11: Check which screenshots changed, then record**

Run: `./gradlew verifyRoborazziDebug`
Expected differences, and only these:
- the three `coming_up_*` images: the "Week ›" link is gone;
- the four existing `week_*` images: a `lock` badge now shows on INSET day;
- `app`'s `home_calendar_dark` and `home_calendar_light`: the Coming up link is gone;
- `today_badges_dark` and `week_syncing_dark`: new, no baseline yet.

`today_dark`, `today_light` and `today_empty_dark` must match: rows became buttons with no visual change. If any other image differs, run `./gradlew compareRoborazziDebug` and look at the diffs before continuing.

Run:
```bash
./gradlew :capability:calendar:recordRoborazziDebug --tests "*CardScreenshotTest.comingUp*" --tests "*CardScreenshotTest.todayBadgesDark" --tests "*WeekScreenshotTest*"
./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest.homeWithCalendar*"
```

Look at each re-recorded image:
- `coming_up_*` and `home_calendar_*`:
  - The Coming up header shows only "Coming up".
  - The day columns sit exactly where they did.
  - Nothing else changed. The hand-off gives Coming up's compact rows no badges, and `CompactRow` draws none.
- `week_*`: INSET day's "All day" line has a 15 dp muted `lock` at its right. Bin day and Piano keep `repeat`, and nothing else changed. Compare with `docs/design/house_hub_handoff/screenshots/calendar-sheets/02-calendar-week-dark.png`.
- `week_syncing_dark`: "Dinner with Jo & Priya" (Today column) shows a `cloud_upload` badge after its time, as in `07-detail-syncing-*`'s week behind the sheet.
- `today_badges_dark`:
  - Dinner shows `cloud_upload`.
  - INSET day shows `lock`.
  - Swimming shows `cloud_upload` then `repeat`, 4 dp apart, 20 dp each, vertically centred.

Run: `./gradlew verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. Hilt now injects `CalendarEditor` into `CalendarCapability`, so it builds the editor, the singleton loop and `AccessControl` into one graph.

- [ ] **Step 13: Commit**

```bash
git add capability/calendar app/src/test
git commit -m "Open the event detail sheet from Today rows and week chips, with badges; drop the Coming up week link"
```

---

### Task 12: Debug seed (master and tags), rollback demo, emulator walkthrough, the user checkpoint, and the README

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSetup.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSetupTest.kt` (modify)
- Modify: `app/src/debug/java/uk/co/siland/culvery/DebugSeed.kt`
- Modify: `app/src/release/java/uk/co/siland/culvery/DebugSeed.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`
- Test: `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt` (modify)
- Test: `app/src/testDebug/java/uk/co/siland/culvery/SampleRollbackTest.kt` (create)
- Modify (after the checkpoint): `README.md`

**Interfaces:**
- Consumes:
  - `CalendarStore.setMaster`, `CalendarStore.master()`, `CalendarStore.enqueue`, `PendingChange` (Task 1)
  - `CalendarSync`'s `@Inject` constructor, `CalendarSyncLoop.requestSync` (Task 7)
  - `FakeCalendarProvider.tagSamples`, `rejectNextWrite`, `SOURCE_FAMILY` (Task 6)
  - `CalendarRepository.day` (Task 9)
  - for the README: `CalendarWriter`, `WriteRejectedException`, `EventDraft`, `CalendarProviderContractTest.writer()` and `writableSource()`, `OverlayHost`, `Toaster`, `CalendarPermissions`, `MIGRATION_1_2`
- Produces:
  - `CalendarSetup(store, providers, requestSync: () -> Unit = {})` (public), plus `@Inject constructor(store, providers, loop: CalendarSyncLoop)`
  - `suspend fun CalendarSetup.master(): StoredSource?`
  - `suspend fun CalendarSetup.setMaster(connectionId: String, sourceId: String)`: throws `IllegalArgumentException` if the connection, provider or source is unknown, if the provider lacks WRITE, or if the source is read-only
  - `fun CalendarSetup.syncSoon()`
  - `suspend fun seedDebugData(household, pins, calendar: CalendarSetup, providers: Set<CalendarProvider>)`, in both debug and release

- [ ] **Step 1: Write the failing setup tests**

In `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSetupTest.kt`, add `import uk.co.siland.culvery.core.plugin.Feature` and these members at the end of the class:
```kotlin
    private val writable = ScriptedProvider(
        "calendar.w",
        sourceList = listOf(CalendarSource("s1", "Alex", writable = false), CalendarSource("s2", "Family calendar", writable = true)),
        features = setOf(Feature.READ, Feature.WRITE),
    )

    @Test
    fun theMasterIsNullUntilChosen() = runTest {
        val setup = CalendarSetup(store, setOf(writable))
        setup.connect(Connection("c1", "calendar.w", "W", emptyMap()), emptyMap())
        assertThat(setup.master()).isNull()
    }

    @Test
    fun setMasterMarksAWritableSourceAndAsksForASync() = runTest {
        var syncs = 0
        val setup = CalendarSetup(store, setOf(writable)) { syncs++ }
        setup.connect(Connection("c1", "calendar.w", "W", emptyMap()), emptyMap())
        setup.setMaster("c1", "s2")
        assertThat(setup.master()?.source?.id).isEqualTo("s2")
        assertThat(syncs).isEqualTo(1)
    }

    @Test
    fun setMasterRecordsTheSourceAsWritableEvenIfAnOlderInstallStoredItReadOnly() = runTest {
        store.addConnection(
            Connection("c1", "calendar.w", "W", emptyMap()),
            listOf(CalendarSource("s2", "Family calendar", writable = false)),
            emptyMap(),
        )
        CalendarSetup(store, setOf(writable)).setMaster("c1", "s2")
        assertThat(store.source("c1", "s2")!!.source.writable).isTrue()
    }

    @Test
    fun setMasterRefusesAReadOnlySourceOrAProviderThatCannotWrite() = runTest {
        val setup = CalendarSetup(store, setOf(writable, provider))
        setup.connect(Connection("c1", "calendar.w", "W", emptyMap()), emptyMap())
        setup.connect(Connection("c2", "calendar.a", "A", emptyMap()), emptyMap())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { setup.setMaster("c1", "s1") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { setup.setMaster("c2", "s2") } }
        assertThat(setup.master()).isNull()
    }
```

- [ ] **Step 2: Write the failing seed tests**

Replace `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt` with:
```kotlin
package uk.co.siland.culvery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

@RunWith(AndroidJUnit4::class)
class DebugSeedTest {
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var calendarDb: CalendarDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager
    private lateinit var store: CalendarStore
    private lateinit var setup: CalendarSetup
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
        setup = CalendarSetup(store, setOf(fake)) { syncs++ }
    }

    @After
    fun tearDown() {
        householdDb.close()
        calendarDb.close()
    }

    private suspend fun seed() = seedDebugData(household, pins, setup, setOf(fake))

    @Test
    fun seedsAlexSamAndMiaWithTheirRolesAndPins() = runTest {
        seed()
        assertThat(household.people.first().map { it.name }).containsExactly("Alex", "Sam", "Mia").inOrder()
        assertThat(pins.identify("1234")?.let { it.person.name to it.role }).isEqualTo("Alex" to Role.ADMIN)
        assertThat(pins.identify("2468")?.let { it.person.name to it.role }).isEqualTo("Sam" to Role.ADULT)
        assertThat(pins.identify("1357")?.let { it.person.name to it.role }).isEqualTo("Mia" to Role.CHILD)
    }

    private suspend fun seededSources() = store.visibleSourcesFor(store.connectionsNow().single().connection.id)

    @Test
    fun sampleSourcesAreMappedToThePeople() = runTest {
        seed()
        val byName = household.people.first().associate { it.name to it.id }
        val mapping = seededSources().associate { it.source.id to it.mapping.person }
        assertThat(mapping).containsExactly(
            FakeCalendarProvider.SOURCE_ALEX, byName.getValue("Alex"),
            FakeCalendarProvider.SOURCE_SAM, byName.getValue("Sam"),
            FakeCalendarProvider.SOURCE_MIA, byName.getValue("Mia"),
            FakeCalendarProvider.SOURCE_FAMILY, PersonId.FAMILY,
            FakeCalendarProvider.SOURCE_SCHOOL, PersonId.FAMILY,
        )
    }

    @Test
    fun theFamilyCalendarIsTheWritableMaster() = runTest {
        seed()
        val master = setup.master()!!
        assertThat(master.source.id).isEqualTo(FakeCalendarProvider.SOURCE_FAMILY)
        assertThat(master.source.writable).isTrue()
    }

    @Test
    fun theSampleWeekIsTaggedWithTheSeededPeopleAndASyncIsRequested() = runTest {
        seed()
        val alexId = household.people.first().single { it.name == "Alex" }.id.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val family = FakeCalendarProvider.SOURCES.single { it.id == FakeCalendarProvider.SOURCE_FAMILY }
        val events = fake.sync(
            Connection("debug-sample", FakeCalendarProvider.ID, "Sample calendar", emptyMap()),
            family,
            DateRange(today.minusDays(1), today.plusDays(15), zone),
            null,
        ).upserts
        assertThat(events.single { it.title == "Dinner with Jo & Priya" }.forPerson).isEqualTo(alexId)
        assertThat(syncs).isAtLeast(1)
    }

    @Test
    fun anUpgradedInstallGetsAWritableMasterWithoutClearingData() = runTest {
        store.addConnection(
            Connection("debug-sample", FakeCalendarProvider.ID, "Sample calendar", emptyMap()),
            FakeCalendarProvider.SOURCES.map { it.copy(writable = false) },
            emptyMap(),
        )
        seed()
        val master = setup.master()!!
        assertThat(master.source.id).isEqualTo(FakeCalendarProvider.SOURCE_FAMILY)
        assertThat(master.source.writable).isTrue()
    }

    @Test
    fun seedIsIdempotent() = runTest {
        seed()
        seed()
        assertThat(household.people.first()).hasSize(3)
        assertThat(store.connectionsNow()).hasSize(1)
        assertThat(store.sources().first().count { it.isMaster }).isEqualTo(1)
    }

    @Test
    fun existingActiveAdminIsLeftAlone() = runTest {
        val admin = household.addPerson("Admin", 0xFF4CB387, Role.ADMIN)
        pins.setPin(admin.id, "9999")
        seed()
        assertThat(household.people.first().map { it.name }).containsExactly("Admin")
        assertThat(seededSources().map { it.mapping.person }.toSet()).containsExactly(PersonId.FAMILY)
    }
}
```

`app/src/testDebug/java/uk/co/siland/culvery/SampleRollbackTest.kt`, the checkpoint's rollback demo on the sample calendar and its existing `rejectNextWrite` switch (no debug trigger in the app):
```kotlin
package uk.co.siland.culvery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.CalendarSync
import uk.co.siland.culvery.capability.calendar.ChangeKind
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.PendingChange
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

/**
 * A delete queued while offline hides the event; when the sample calendar later refuses it, the event comes back
 * and a toast says why. The real store, sync engine, repository and fake provider, end to end.
 */
@RunWith(AndroidJUnit4::class)
class SampleRollbackTest {
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
    fun aRefusedOfflineDeleteComesBackWithAToast() = runTest {
        val household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        val store = CalendarStore(calendarDb)
        val zone = HouseholdZone(household)
        seedDebugData(household, PinManager(household, PinHasher()), CalendarSetup(store, setOf(fake)), setOf(fake))
        val sync = CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, WallClock { System.currentTimeMillis() })
        val repo = CalendarRepository(store, household, zone, setOf(fake), setOf(fake))
        sync.syncAll()
        val today = LocalDate.now(london)
        val boiler = repo.day(today).first().single { it.title == "Boiler service" }

        // What the editor queues when the provider can't be reached (CalendarEditorTest covers that step).
        val now = System.currentTimeMillis()
        store.enqueue(
            PendingChange(
                0, boiler.ref.connectionId, boiler.ref.sourceId, boiler.ref.remoteId, ChangeKind.DELETE,
                draft = null, attempts = 1, nextAttemptMillis = now, createdMillis = now,
            ),
        )
        assertThat(repo.day(today).first().map { it.title }).doesNotContain("Boiler service")

        fake.rejectNextWrite("Boiler service is locked")
        sync.syncAll()
        assertThat(repo.day(today).first().map { it.title }).contains("Boiler service")
        assertThat(toasts.messages).containsExactly("Couldn't save to Sample calendar — Boiler service is locked")
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSetupTest*" :app:testDebugUnitTest --tests "*DebugSeedTest*" --tests "*SampleRollbackTest*"`
Expected: compilation FAILS: `master`, `setMaster` and the trailing `requestSync` lambda don't exist, and `seedDebugData` takes no providers.

- [ ] **Step 4: Let setup choose the master calendar**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSetup.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature

/**
 * Adds provider connections and chooses the master calendar. Used by the debug seed now and by setup and
 * settings in Plan 4.
 */
@Singleton
class CalendarSetup(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    private val requestSync: () -> Unit = {},
) {
    @Inject
    constructor(store: CalendarStore, providers: Set<@JvmSuppressWildcards CalendarProvider>, loop: CalendarSyncLoop) :
        this(store, providers, loop::requestSync)

    suspend fun hasConnections(): Boolean = store.connectionsNow().isNotEmpty()

    /** Sources missing from [mapping] show as Family. */
    suspend fun connect(connection: Connection, mapping: Map<String, SourceMapping>) {
        val provider = providers.firstOrNull { it.descriptor.id == connection.providerId }
            ?: throw IllegalArgumentException("No calendar provider ${connection.providerId}")
        store.addConnection(connection, provider.sources(connection), mapping)
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
        val provider = providers.firstOrNull { it.descriptor.id == connection.providerId }
            ?: throw IllegalArgumentException("No calendar provider ${connection.providerId}")
        require(Feature.WRITE in provider.descriptor.features) { "${provider.descriptor.displayName} can't write" }
        val source = provider.sources(connection).firstOrNull { it.id == sourceId }
            ?: throw IllegalArgumentException("No source $sourceId in ${connection.label}")
        require(source.writable) { "${source.name} is read-only" }
        store.setMaster(connectionId, sourceId)
        requestSync()
    }

    /** Asks the sync loop for a pass now. */
    fun syncSoon() = requestSync()
}
```

- [ ] **Step 5: Seed the master and the tags**

Replace `app/src/debug/java/uk/co/siland/culvery/DebugSeed.kt` with:
```kotlin
package uk.co.siland.culvery

import android.util.Log
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.SourceMapping
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

private class SeedPerson(val name: String, val color: Long, val role: Role, val pin: String, val source: String)

private val SEED_PEOPLE = listOf(
    SeedPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234", FakeCalendarProvider.SOURCE_ALEX),
    SeedPerson("Sam", 0xFF5B9BE0, Role.ADULT, "2468", FakeCalendarProvider.SOURCE_SAM),
    SeedPerson("Mia", 0xFFE07BA8, Role.CHILD, "1357", FakeCalendarProvider.SOURCE_MIA),
)

private const val DEBUG_CONNECTION_ID = "debug-sample"

/**
 * Debug builds only: the hand-off's family and a sample calendar, so the app is usable before the setup wizard
 * exists. People are added only when there is no active Admin, so a second set is never added. Every start
 * re-tags the sample week with the people's ids (the fake keeps them in memory) and, once, makes the sample
 * "Family calendar" the writable master (2b-1 design D5).
 */
suspend fun seedDebugData(
    household: HouseholdRepository,
    pins: PinManager,
    calendar: CalendarSetup,
    providers: Set<CalendarProvider>,
) {
    if (household.credentials().none { it.isActiveAdmin }) {
        SEED_PEOPLE.forEach { p ->
            val person = household.addPerson(p.name, p.color, p.role)
            pins.setPin(person.id, p.pin)
        }
    }
    val byName = household.people.first().associate { it.name to it.id }
    val fake = providers.filterIsInstance<FakeCalendarProvider>().singleOrNull()
    fake?.tagSamples(byName.mapValues { it.value.value })
    if (!calendar.hasConnections()) {
        val mapping = SEED_PEOPLE.associate { p ->
            p.source to SourceMapping(byName[p.name] ?: PersonId.FAMILY, visible = true)
        } + mapOf(
            FakeCalendarProvider.SOURCE_FAMILY to SourceMapping(PersonId.FAMILY, visible = true),
            FakeCalendarProvider.SOURCE_SCHOOL to SourceMapping(PersonId.FAMILY, visible = true),
        )
        calendar.connect(Connection(DEBUG_CONNECTION_ID, FakeCalendarProvider.ID, "Sample calendar", emptyMap()), mapping)
    }
    if (fake != null && calendar.master() == null) {
        try {
            calendar.setMaster(DEBUG_CONNECTION_ID, FakeCalendarProvider.SOURCE_FAMILY)
        } catch (e: IllegalArgumentException) {
            Log.w("Culvery", "Couldn't make the sample Family calendar the master", e)
        }
    }
    // The tags may have changed after the start-up sync ran.
    calendar.syncSoon()
}
```

Replace `app/src/release/java/uk/co/siland/culvery/DebugSeed.kt` with:
```kotlin
package uk.co.siland.culvery

import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository

@Suppress("UNUSED_PARAMETER")
suspend fun seedDebugData(
    household: HouseholdRepository,
    pins: PinManager,
    calendar: CalendarSetup,
    providers: Set<CalendarProvider>,
) = Unit
```

In `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`:
- add `import uk.co.siland.culvery.capability.calendar.CalendarProvider`;
- add a field after `@Inject lateinit var calendarSetup: CalendarSetup`:
```kotlin
    @Inject lateinit var calendarProviders: Set<@JvmSuppressWildcards CalendarProvider>
```
- replace `appScope.launch { seedDebugData(household, pins, calendarSetup) }` with:
```kotlin
        appScope.launch { seedDebugData(household, pins, calendarSetup, calendarProviders) }
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSetupTest*" :app:testDebugUnitTest --tests "*DebugSeedTest*" --tests "*SampleRollbackTest*"`
Expected: PASS.

- [ ] **Step 7: Build both variants and run the gate**

Run: `./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.
- The release build has empty `Set<CalendarProvider>` and `Set<CalendarWriter>` (both `@Multibinds`).
- The debug build binds the fake as both a provider and a writer.
- If Hilt reports a missing binding, the message names it. The new bindings are:
  - `Toaster` (`AppModule`), injected into `DefaultAccessControl`, `CalendarSync` and `CalendarEditor`;
  - `Set<CalendarWriter>` and the `PermissionSource` (`CalendarModule`);
  - the writer (`FakeCalendarModule`).

- [ ] **Step 8: Commit**

```bash
git add capability/calendar app
git commit -m "Seed the sample Family calendar as the writable master with a tagged week"
```

- [ ] **Step 9: Walk through it on the emulator**

The 2b-1 design §7 asks for this walkthrough on an API 30 **Google Play** image. Check which image `Culvery_Tablet_API_30` uses:
```bash
grep image.sysdir "$USERPROFILE/.android/avd/Culvery_Tablet_API_30.avd/config.ini"
```
- If it reads `google_apis_playstore`, use `Culvery_Tablet_API_30`.
- Otherwise use `Culvery_Tablet_API_35`, and say so in your report. Swapping the image is a user step in Android Studio's SDK Manager. Do not change the AVD yourself.

1. Start the emulator **in the background** (in Git Bash the trailing `&` does it; with an agent's shell tool, also use its run-in-background option), then wait for it to boot:
   ```bash
   "$LOCALAPPDATA/Android/Sdk/emulator/emulator" -avd <AVD> -no-snapshot-save > /dev/null 2>&1 &
   adb wait-for-device && adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
   ```
   If `adb` is not on PATH, use `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb"`.
2. **Migration on a real install.** Check whether an earlier Culvery is installed:
   ```bash
   adb shell pm list packages uk.co.siland.culvery
   ```
   - If it is, install over it **without** clearing data:
     ```bash
     ./gradlew :app:installDebug
     adb shell am start -n uk.co.siland.culvery/.MainActivity
     adb logcat -d | grep -iE "Migration|IllegalStateException|FATAL" | tail -20
     ```
     Expected: the app opens straight onto Home with the cached events (no empty Today card first), and the logcat excerpt shows no migration error or crash. Report what you saw.
   - If Culvery is not installed, say "migration not exercised on device; covered by `CalendarMigrationTest`" and run:
     ```bash
     ./gradlew :app:installDebug
     adb shell am start -n uk.co.siland.culvery/.MainActivity
     ```
3. Within a few seconds, the Today card shows the hand-off's day, now in person colours from the tags: School run (Sam), Boiler service (Family), Plumber quote call (Family), Swimming (Mia, `repeat`), Dinner with Jo & Priya (Alex). The Coming up header has no "Week ›".

Check each item below and report any that fail. Take `adb exec-out screencap -p > "$TMP/culvery-<step>.png"` at 4, 5, 7 and 9.

4. **Detail, editable.** Tap *Dinner with Jo & Priya*. A 600 dp sheet slides over the right with a dark scrim over the rest. It shows:
   - When "Today · 19:30–21:00"; For "● Alex"; Created by "Alex"; Calendar "Family calendar";
   - a Delete pill at the bottom left.

   Tap the scrim: the sheet closes.
5. **Assign, with a refusal first.** Tap *Plumber quote call*. The green "Added from a phone" card shows; Created by reads "Added from phone".
   1. Tap **Assign to…** to show the chips, then tap **Sam**.
   2. The PIN pad appears **over the sheet's 600 dp only**. It reads "Who's this?" / "Enter your PIN to assign this event. It also records who made the change."
   3. Enter `1357` (Mia). The pad closes and a toast reads "Ask an adult to assign this event."
   4. Tap **Sam** again and enter `2468` (Sam). The chips close and For reads "● Sam".
   5. The status bar shows "Sam · Adult" and a green "Sign out".
6. **Sign out.** Tap **Sign out** in the status bar. The session indicator disappears.
7. **A child deletes her own event.** Open the **Calendar** tab and tap *Football* (tomorrow).
   1. Tap **Delete**, then enter `1357` (Mia). The coral "Delete this event?" panel replaces the footer, with **Keep event** on the left.
   2. Tap **Keep event**: the Delete pill returns.
   3. Tap **Delete** again. No PIN is asked, because Mia is signed in.
   4. Tap **Delete event**. The sheet closes, "Event deleted" shows as a toast, and Football is gone from the week.
8. **A child is refused someone else's event.** Still within 2 minutes, tap *Dinner with Jo & Priya*, then **Delete**. No PIN pad appears, a toast reads "Mia can only change events they created.", and the status-bar indicator disappears: the refusal signed Mia out. Tap **Delete** again: the PIN pad appears. Cancel it.
9. **Read-only states.**
   - Tap *INSET day — no school*, which shows a `lock` badge: "From School terms (read-only)", Calendar "School terms · read-only", and no footer.
   - Tap *Swimming*: "Repeating event", a Repeats row reading "Yes", and no footer.
10. **The session ends by itself, and touches don't extend it.** Tap *Dinner with Jo & Priya*, tap **Delete**, enter `1234` (Alex), then tap **Keep event** and close the sheet. For the next 2 minutes 10 seconds keep tapping around (open and close week chips): the status-bar indicator still disappears 2 minutes after the PIN.
11. **PIN pad without a sheet.** On Home, tap **Settings** in the rail. The pad is centred over the whole screen and reads "Enter your PIN to change settings." Enter `0000`: the dots turn coral and "Wrong PIN — try again" shows. Cancel.
12. **DM Sans on this API level** (the 2a follow-up):
    - Bold titles ("This week", the sheet title, "Who's this?") must look clearly heavier than body text.
    - "Dinner with Jo & Priya" wraps in its week chip rather than truncating.
    - The sheet title wraps rather than clipping.

    Say which API level you checked. If it isn't API 30 with the Google Play image, list this check as still to do on that image.

The outbox's offline and rejection paths can't be triggered on the device in 2b-1: the fake's switches are only reachable from tests, and the app has no debug trigger for them. `SampleRollbackTest` runs the rollback end to end on the sample calendar with its `rejectNextWrite` switch; `CalendarEditorTest`, `CalendarSyncTest` and `CalendarRepositoryTest` cover the rest. Say so in the report.

If the emulator can't start, say so and report Step 7 as the gate.

- [ ] **Step 10: STOP — the controller runs the USER CHECKPOINT**

The implementer stops here and reports Steps 7 and 9. **The controller**, not the implementer, then does the following, and does not start Step 11 until the user replies.

Send the user these images:
- The sheet (`capability/calendar/src/test/screenshots/`):
  - `detail_editable_dark.png`, `detail_editable_light.png`
  - `detail_readonly_feed_dark.png`, `detail_readonly_feed_light.png`
  - `detail_recurring_dark.png`, `detail_recurring_light.png`
  - `detail_untagged_dark.png`, `detail_untagged_light.png`
  - `detail_syncing_dark.png`, `detail_syncing_light.png`
  - `detail_delete_confirm_dark.png`, `detail_delete_confirm_light.png`
  - `detail_pin_dark.png`, `detail_pin_light.png`
- Badges and the Coming up change:
  - `today_badges_dark.png`, `week_dark.png`, `week_syncing_dark.png`, `coming_up_dark.png`
- The shell (`app/src/test/screenshots/`):
  - `home_session_dark.png`, `home_toast_dark.png`
  - `pin_pad_dark.png`, `pin_pad_light.png`, `pin_pad_wrong_dark.png`
- The emulator screenshots from Step 9.

Send the hand-off references with them: `docs/design/house_hub_handoff/screenshots/calendar-sheets/03-…` to `09-…`, dark and light.

Name the parts that are the controller's own design, which the hand-off does not specify:
- where the status-bar sign-in sits (right side, before the theme indicator);
- the collapsed "Assign to…" button (the hand-off only shows the chips; seen in the walkthrough, step 5);
- "Repeats · Yes";
- the connection label in place of "Google" ("Syncing to Sample calendar…", "…removed from Sample calendar…", "Edit repeating events in Sample calendar on your phone."), already approved (U3);
- the `delete` icon in the confirmation panel;
- the 22 dp toast icon;
- the full-screen PIN pad when no sheet is open.

List the known differences that are **2b-2 work, not defects**:
- the Edit button in the sheet's footer;
- the **+** on Today, **Add event** in the Calendar header, and the column add hints;
- the quick-add sheet, the pickers and keyboard handling.

Show the rollback: run `./gradlew :app:testDebugUnitTest --tests "*SampleRollbackTest*"` and tell the user what it proves (an offline delete hides Boiler service; when the sample calendar refuses it, the event comes back and the toast reads "Couldn't save to Sample calendar — Boiler service is locked"), alongside `home_toast_dark.png` for how a toast looks.

Ask one design question: the lock and `event_repeat` icons in the read-only and repeating cards are drawn `mute`, as the other card icons are. Should they be `ink`?

Also list anything you noted in Task 10 Step 10, Task 11 Step 11 or Step 9 above.

Ask: "Do these match what you want? Any changes before I update the README?"

- **If the user asks for changes:** make them, and re-record only the affected images with `--tests`. Look at them, run `./gradlew testDebugUnitTest verifyRoborazziDebug`, re-send the changed images, and commit with a message describing the change. Repeat until the user approves.
- **When approved:** continue to Step 11.

- [ ] **Step 11: Update the README**

Make these edits to `README.md`:

1. **Build and run.** Replace the paragraph starting "Debug builds seed a sample household" with:
```markdown
Debug builds seed a sample household on first launch so the app is usable before the setup wizard exists: **Alex** (Admin, PIN 1234), **Sam** (Adult, PIN 2468) and **Mia** (Child, PIN 1357), plus a "Sample calendar" connection showing the design hand-off's week. Its "Family calendar" is the household's master calendar, so its events can be deleted and assigned on the tablet. The sample calendar keeps changes in memory and forgets them when the app restarts. People are only seeded when there is no Admin with a PIN, so after upgrading from a Plan 1 build clear the app's data (`adb shell pm clear uk.co.siland.culvery`). Release builds seed nothing and include no sample calendar.
```

2. **Modules.** In the table:
   - replace the `:core:plugin` row's text with ``Capability``, ``HomeCard``, ``ProviderDescriptor``, ``Connection``, ``ConnectionHealth``, ``ShellNavigator``, ``OverlayHost``, ``Toaster``, ``Startable``;
   - replace the `:core:access` row's text with `Permissions, PIN hashing, lockout, 2-minute session, PIN pad`;
   - replace the `:capability:calendar` row's text with `Calendar contract (read and write), ``calendar.db`` cache and outbox, 5-minute sync, event editor, Home cards, Calendar tab, event detail sheet`.

   Replace the paragraph starting "`calendar.db` stores user configuration" with:
```markdown
`calendar.db` stores user configuration (connections, mappings, the master calendar) and queued changes (the `outbox` table; the `event` table is only ever a copy of what the provider has). Every schema version bump ships a hand-written Room `Migration` in `db/Migrations.kt` with a `MigrationTestHelper` test in `CalendarMigrationTest`; never use destructive fallback. Room exports each schema version to `<module>/schemas/`, and those files are committed.
```

3. **Adding a capability.** In step 2, after "Card and tab UI move the shell through `LocalShellNavigator.current.openTab(id)` / `.openSettings()`.", add: "Sheets open through `LocalOverlayHost.current.show { … }` (draw them with `HhSheet`). Short messages go through the injected `Toaster`, from the code that knows the outcome (a view model or an `@ApplicationScope` job), so the toast still shows if the sheet has closed."

4. **Adding a calendar provider.**
   - In step 2, replace "and `features` (`READ`, plus `WRITE` once 2b adds writing)." with "and `features` (`READ`, plus `WRITE` if it implements `CalendarWriter`)."
   - Insert a new step after step 2:
````markdown
3. **Writing (optional).** If the service can write, declare `Feature.WRITE` and implement `CalendarWriter` (often on the same class):
   - `providerId` equals `descriptor.id`.
   - `create`, `update` and `delete` write to one source. `forPerson` and `createdBy` are household person ids to store with the event (Google: `extendedProperties.private`), and the next sync must return them unchanged. Never write names.
   - `update` changes only the title, the times and the tags, and keeps every other field (Google: PATCH, not PUT).
   - Throw `WriteRejectedException` for a permanent refusal (including a source that is unknown or read-only), `NeedsSignInException` for auth failures, and `UnreachableException` for network failures and for "try later" answers (Google: 429, 403 rate limits, 5xx). Nothing else.
   - Deleting an event that is already gone succeeds (Google: treat 404 and 410 on a delete as success).

   Bind it next to the provider: `@Binds @IntoSet abstract fun writer(impl: MyCalendarProvider): CalendarWriter`. The app writes only to the household's master calendar, and only non-recurring events. It tries the writer for 10 seconds, then queues the change and retries with backoff (30 s, 1 min, 2 min, then every 5 min), delivering each event's changes in order. A change still queued after 48 hours is dropped with a toast.
````
   - Renumber the old steps 3, 4 and 5 to 4, 5 and 6.
   - In the contract-test example, after `override fun simulateUnreachable() = { … }`, add:
```kotlin
       // WRITE providers only; the six write checks fail if these are missing.
       override fun writer() = …
       override fun writableSource() = …
```

5. **PINs.** Replace the bullet "A session lasts 60 seconds after the last touch. Tap your name in the rail to lock early. Settings closes when the session ends." with:
```markdown
- A session lasts 2 minutes after the last PIN-checked action; touching the screen doesn't extend it. While someone is signed in, the status bar shows their name and role and a **Sign out** link. Settings closes when the session ends.
- Calendar changes follow the roles: Admins and Adults can delete and assign any event on the master calendar; a Child can delete only events they added, and can't assign. A refused change says why in a toast and signs the person out, so the next tap asks for a PIN. Events from other calendars, and repeating events, can't be changed on the tablet.
```

- [ ] **Step 12: Check the README renders sensibly**

Read `README.md` through once. Check that:
- the provider steps number 1 to 6;
- the code blocks are balanced;
- nothing still mentions "60 seconds", "rail chip", "once 2b adds writing", `LocalToaster` or a touch extending the session:
  ```bash
  grep -nE "60 s|60 seconds|rail chip|once 2b|LocalToaster|last touch" README.md
  ```
  Expected: no output.

- [ ] **Step 13: Commit**

```bash
git add README.md
git commit -m "Document calendar changes, the writer contract and the 2-minute session"
```

---

## Spec coverage (2b-1 design → tasks)

| Design | Where |
|---|---|
| §2 D1 two-minute session (extended only by an authorised action), status-bar sign-in, no rail chip | Tasks 2, 3 |
| §2 D2 PIN pad redesign, over-sheet placement | Task 4 |
| §2 D3 save flow (10 s attempt, rejected, queued, later rollback) | Tasks 7, 8, 9, 10, 12 (delete and assign; save in 2b-2) |
| §2 D5 debug master calendar | Tasks 6, 12 |
| §2 D6 no Coming up week link | Task 11 |
| §3.1 overlay host, toaster, status bar | Task 2 |
| §3.2 `authorise` reason/allow, `NotAllowed` message, refusal toast (a shortcut refusal locks) | Task 3 |
| §3.3 `EventDraft`, `CalendarWriter`, `WriteRejectedException`, write checks (plus delete-missing and all-day), broken-writer fixtures | Tasks 1, 5 |
| §3.4 v2 migration, master flag, outbox, `MigrationTestHelper` | Task 1 |
| §3.5 editor, outbox drain (in order, 48-hour cap), backoff, rejection toasts in place of `WriteFailed`, overlay, `requestSync` + `Mutex` | Tasks 7, 8, 9 |
| §3.6 permissions table, allow rules, refusal wording | Task 8 |
| §3.7 `EventRef`, new `EventUi` fields | Tasks 1, 9 |
| §4.1–4.2 opening the sheet, every sheet state | Tasks 10, 11 |
| §4.3 badges | Tasks 9, 11 |
| §5 offline delete/assign, missing writer, sign-in expired during a write | Tasks 7, 8, 9 |
| §6 testing list, incl. deferred 2a tests (unknown health, midnight end, cancellation, mid-sync change) | Tasks 1, 7, 9 |
| §7 API 30 Google Play walkthrough | Task 12 |
