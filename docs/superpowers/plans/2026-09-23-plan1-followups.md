# Plan 1 follow-ups

Items raised while Plan 1 (foundation) was built and reviewed. They were deferred on purpose and are for the plan that first touches the area.

## For Plan 2 (Calendar capability)
- `HhIcon` clears semantics and takes no content description. Icon-only buttons need an accessible label.
- `HomeCardPlacer` has no tests with mixed sizes or a full grid. Add them once real cards exist.
- The shell composables (`NavRail`, `StatusBar`, `SettingsPlaceholder`) have no UI tests, although the test tags exist. Cover them with the Roborazzi setup.
- Set a module-wide Robolectric viewport default (`w1280dp-h800dp`) so each test class doesn't need its own `@Config`.
- Home header: the gap between the clock and the date is about 55 dp on the emulator, against about 12 dp in the hand-off, so the grid sits roughly 40 dp too low. The line-height trim on `HhType.clock` isn't taking effect. Found on the emulator; both AVDs show it.

## For Plan 4 (weather, setup, settings, release)
- Guard the lockout against a backwards jump of the wall clock: treat a stored `lockedUntil` more than 16 minutes in the future as expired.
- Run the theme schedule in the household's timezone (`HomeLocation.timeZoneId`) and feed it sunrise/sunset.
- Subset the 15 MB Material Symbols font to the glyphs used, and measure memory on the SM-T510.
- After Exit kiosk, the system bars overlap the content (edge-to-edge, no insets). Pad the root with `WindowInsets.systemBars` when not in kiosk mode.
- `LockoutStore` uses `commit()`, which is synchronous disk I/O and may be on Main. Switch to `apply()` with an in-memory mirror if StrictMode complains.
- `kioskExited` is lost when the Activity is recreated by a config change that isn't in the manifest list.
- The session keeps a snapshot of the person, so a role change or deletion lasts until the session ends, 2 minutes after the last authorised action. The people editor should call `AccessControl.lock()`.
- Read `addPerson`'s `sortOrder` inside a transaction. Add tests that `setRole`, `setPinHash` and `clearPin` reject Family.
- Make the debug seed check for an active Admin rather than an empty household. Remove the debug Admin when the wizard creates the first real one.
- Before shipping to the wall, run the Task 10 Step 7 checks on the device and do a signed release build (`startLockTask` has only run in release, and never on a device).
- Calendar sources (deferred from Plan 2a, where a connection's source list is fixed when it is added):
  - Add source editing with pruning to `CalendarStore`: re-map and hide sources, and drop the events and sync state of sources that are gone.
  - Add foreign keys with `ON DELETE CASCADE` (event and sync_state → source → connection) in a `calendar.db` migration, and make `applySync` return early if its source row is gone, so a removal racing an in-flight sync leaves no orphan rows or stale cursor.
  - Refresh each connection's sources from `provider.sources()` (for example daily), keeping the existing mappings.

## From Plan 2a review (deferred)

**For Plan 3**
- Widen R3:
  - add a logging `CoroutineExceptionHandler` on `@ApplicationScope`;
  - add `retryWhen` with backoff on `connectionIds()` and on the capability flows in `ShellViewModel`, where `catch` currently completes the flow;
  - catch `Throwable` minus `CancellationException` per source, because an `Error` from a provider currently crashes the process.
- Log NeedsSignIn, Unreachable and Error with their cause.
- Put the store calls outside the per-source try.
- Replace the chunking test with a counting DAO fake: API 30 SQLite has the 999-variable limit.
- More contract-suite self-test fixtures.
- Cover the ICS empty feed with `fullReplace` and zero-duration events.
- Extend the module guard to JVM-only modules.

**For Plan 4**
- `CalendarSetup.connect` must go through the IO + timeout wrapper.
- Test that `addConnection` is atomic.
- All-day events straddle two days after a household zone change until the next sync: filter all-day events by date.
- `opsz` axis for large text.
- An on-device SM-T510 pass of the calendar UI.

## From Plan 2b-1 (deferred)

**For Plan 3**
- R8: map raw provider error text to fixed, friendly wording in the Google writer before it reaches a toast ("Couldn't save to … — {reason}").
- R9: the Google writer must wrap its blocking HTTP calls in `runInterruptible` (or an equivalent cancellable call), so the editor's 10 s and the drain's 60 s timeouts hold. Add the contract check (a gated write returns when its caller is cancelled) with it; on the cooperative fake it proves nothing.
- DL1: describe the recurrence from Google's RRULE (e.g. "Every week") in the detail sheet's Repeats row, in place of "Yes".
- U3 follow-up: once a second connection label exists, use the service name ("Google Calendar") in the failure, repeating-event and delete-confirmation wording, and keep the short connection label for the syncing pill.
- m2: a drain that keeps throwing makes the loop run a full sync every second (`nextWait()` sees the change overdue). Back off after a failed drain; fold into R3.
- m3: a queued ASSIGN for an event that left the mirror's window is dropped as "The event no longer exists". Fetch the event from the provider, or send a tags-only patch.
- m4: a reconnect keeps the connection id, so queued changes wait out their backoff (up to 5 minutes). Reconnect should reset that connection's `nextAttemptMillis` and call `requestSync()`.

**For Plan 4**
- `CalendarSetup.setMaster` calls `provider.sources()` without the IO + timeout wrapper. Fix with `CalendarSetup.connect`.
- m5: Settings closes when the session ends, 2 minutes after the PIN, however busy the adult is. Extend the session on each saved settings action, or give Settings its own session while open.
- Check DM Sans weights and bold-text truncation on an API 30 AVD (Google Play image); the 2b-1 walkthrough ran on API 35.

**Accessibility pass (with the `HhIcon` item)**
- Clickable event rows and week chips have no `Role.Button`.
- The event detail sheet doesn't scroll. It fits today; longer titles and 2b-2's fields won't.

**Next migration or Room upgrade**
- `androidxSqlite` 2.6.0 is pinned apart from Room's transitive version. Re-check it on each Room bump.

## From Plan 2b-2 (deferred)

**For Plan 3**
- C2: when the drain's accepted create can't be stored, the row stays due and the loop resends it every second (2b-1 m2). Fold into R3's drain backoff, and update `CalendarSyncTest.aCreateWhoseMirrorWriteFailsIsRetriedAndMakesOneEvent`, which relies on the row staying due.
- C3: an edit sends every field from the sheet's snapshot, so a phone change made meanwhile to a field the user didn't touch is undone. With the Google writer's PATCH, send only the touched fields, applied to the event as re-read under the write lock.
- C9: a create whose reply was lost, then unsent for 48 hours, is dropped with the DELETE queued behind it, so the event it did make comes back. Decide what an aged create means for Google (fetch by key first, or send the delete).
- C10: the fake recreates a deleted event when a create repeats its key; Google returns 409 for a cancelled event's id. Pin the rule in the contract suite with the Google writer.
- The editor's Retry path queues a create with its key; if that queue insert then fails (`TRY_AGAIN`), the sheet's Try again chooses a new key, and a provider that did make the event ends up with two. Fold into R3 with the other store failures.
- The Google writer: `events.insert` with `id = clientKey`, and on 409 fetch and return the existing event.
- The add/edit sheet crashes if the store fails while loading: `produceState` has no `catch`. Fold into R3 with the other store failures.

**For Plan 4**
- I1, T2, T7: in the on-device pass, check the add/edit sheet with Samsung's floating and split keyboards, that the real IME inset reaches the sheet and the toast through `ShellLayers`, and that a tap on the scrim hides the keyboard.
- The on-device pass: the add/edit sheet with the Samsung keyboard on the SM-T510, and with a signed release in lock-task mode (debug builds never call `startLockTask`, so the 2b-2 walkthrough checked the immersive window only).
- With the full keyboard up, the add/edit sheet's first Day row is half-hidden and the chips scroll to reach it; check whether that is acceptable on the SM-T510's keyboard.
- A toast covers the sheet's footer for about 3.5 s while it is up; check whether that is acceptable on-device.

**Accessibility pass (with the `HhIcon` item)**
- Disabled Who chips lack disabled semantics.
- `AddEventButton` lacks an `onClickLabel`.
