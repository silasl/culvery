# Plan 1 follow-ups

Items raised while Plan 1 (foundation) was built and reviewed. They were deferred on purpose and are for the plan that first touches the area.

## For Plan 2 (Calendar capability)
- `HhIcon` clears semantics and takes no content description. Icon-only buttons need an accessible label.
- `HomeCardPlacer` has no tests with mixed sizes or a full grid. Add them once real cards exist.
- The shell composables (`NavRail`, `StatusBar`, `SettingsPlaceholder`) have no UI tests, although the test tags exist. Cover them with the Roborazzi setup.
- Set a module-wide Robolectric viewport default (`w1280dp-h800dp`) so each test class doesn't need its own `@Config`.
- Make the PIN pad scrim colour (`0x8C000000`) a token, and give the repeated `80.dp` key size a name.
- Home header: the gap between the clock and the date is about 55 dp on the emulator, against about 12 dp in the hand-off, so the grid sits roughly 40 dp too low. The line-height trim on `HhType.clock` isn't taking effect. Found on the emulator; both AVDs show it.
- The rail session chip cuts "Admin" to "Adm…". The padding, dot and lock icon leave too little width inside the 108 dp rail. Tighten the padding or drop the lock icon.

## For Plan 4 (weather, setup, settings, release)
- Guard the lockout against a backwards jump of the wall clock: treat a stored `lockedUntil` more than 16 minutes in the future as expired.
- Run the theme schedule in the household's timezone (`HomeLocation.timeZoneId`) and feed it sunrise/sunset.
- Subset the 15 MB Material Symbols font to the glyphs used, and measure memory on the SM-T510.
- After Exit kiosk, the system bars overlap the content (edge-to-edge, no insets). Pad the root with `WindowInsets.systemBars` when not in kiosk mode.
- `LockoutStore` uses `commit()`, which is synchronous disk I/O and may be on Main. Switch to `apply()` with an in-memory mirror if StrictMode complains.
- `kioskExited` is lost when the Activity is recreated by a config change that isn't in the manifest list.
- The session keeps a snapshot of the person, so a role change or deletion takes up to 60 s to apply. The people editor should call `AccessControl.lock()`.
- Read `addPerson`'s `sortOrder` inside a transaction. Add tests that `setRole`, `setPinHash` and `clearPin` reject Family.
- Make the debug seed check for an active Admin rather than an empty household. Remove the debug Admin when the wizard creates the first real one.
- Before shipping to the wall, run the Task 10 Step 7 checks on the device and do a signed release build (`startLockTask` has only run in release, and never on a device).
- Calendar sources (deferred from Plan 2a, where a connection's source list is fixed when it is added):
  - Add source editing with pruning to `CalendarStore`: re-map and hide sources, and drop the events and sync state of sources that are gone.
  - Add foreign keys with `ON DELETE CASCADE` (event and sync_state → source → connection) in a `calendar.db` migration, and make `applySync` return early if its source row is gone, so a removal racing an in-flight sync leaves no orphan rows or stale cursor.
  - Refresh each connection's sources from `provider.sources()` (for example daily), keeping the existing mappings.

## From Plan 2a review (deferred)

**For Plan 2b**
- Remove the "Week ›" link from the Coming up card header. The user decided that Today's Week pill and the rail's Calendar tab are enough, so there is one way from Home to the week view. Re-record the Coming up screenshots.
- Map an unknown stored health code to `Error`, not `Ok` (`CalendarStore.healthOf`).
- Test that real cancellation propagates through `CalendarSync` and the loop.
- Test that an id change mid-sync behaves correctly.
- Test the midnight-end label rule.
- `EventRef` must replace the `/`-joined `EventUi.key` (ICS UIDs can contain `/`).
- Check DM Sans weights and bold-text truncation on an API 30 AVD (Google Play image) at the checkpoint.
- The overlay host, "sync now" (`requestSync` + `Mutex`), `CalendarWriter`, the outbox as a separate table, and the v2 migration with a `MigrationTestHelper` test.

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

## From Plan 2b-1 review (deferred)

**For Plan 2b-2**
- S1, idempotent creates: add a nullable `clientKey TEXT` (a UUID) to `outbox` (a one-line Room AutoMigration, v2 → v3) and an optional client id on `CalendarWriter.create`. The contract says a repeated create with the same key returns the existing event. A queued create's `EventRef` uses the client id, so it can be opened, edited and deleted before it syncs. Needed before quick-add ships: nothing in 2b-1 creates events.

**For Plan 3**
- R8: map raw provider error text to fixed, friendly wording in the Google writer before it reaches a toast ("Couldn't save to … — {reason}").
- R9: the Google writer must wrap its blocking HTTP calls in `runInterruptible` (or an equivalent cancellable call), so the editor's 10 s and the drain's 60 s timeouts hold. Add the contract check (a gated write returns when its caller is cancelled) with it; on the cooperative fake it proves nothing.
- DL1: describe the recurrence from Google's RRULE (e.g. "Every week") in the detail sheet's Repeats row, in place of "Yes".
- U3 follow-up: once a second connection label exists, use the service name ("Google Calendar") in the failure, repeating-event and delete-confirmation wording, and keep the short connection label for the syncing pill.
