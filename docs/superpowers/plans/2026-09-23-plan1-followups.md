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

## From Plan 2a review (deferred)

**For the ICS provider, or the first JVM-only module** (3a design §8)
- Cover the ICS empty feed with `fullReplace` and zero-duration events.
- Extend the module guard to JVM-only modules.

**For Plan 4**
- Test that `addConnection` is atomic.
- All-day events straddle two days after a household zone change until the next sync: filter all-day events by date.
- `opsz` axis for large text.
- An on-device SM-T510 pass of the calendar UI.

## From Plan 2b-1 (deferred)

**For Plan 4**
- m5: Settings closes when the session ends, 2 minutes after the PIN, however busy the adult is. Extend the session on each saved settings action, or give Settings its own session while open.
- Check DM Sans weights and bold-text truncation on an API 30 AVD (Google Play image); the 2b-1 walkthrough ran on API 35.

**Accessibility pass (with the `HhIcon` item)**
- Clickable event rows and week chips have no `Role.Button`.
- The event detail sheet doesn't scroll. It fits today; longer titles and 2b-2's fields won't.

**Next migration or Room upgrade**
- `androidxSqlite` 2.6.2 is pinned apart from Room's transitive version. Re-check it on each Room bump.

## From Plan 2b-2 (deferred)

**For Plan 4**
- I1, T2, T7: in the on-device pass, check the add/edit sheet with Samsung's floating and split keyboards, that the real IME inset reaches the sheet and the toast through `ShellLayers`, and that a tap on the scrim hides the keyboard.
- The on-device pass: the add/edit sheet with the Samsung keyboard on the SM-T510, and with a signed release in lock-task mode (debug builds never call `startLockTask`, so the 2b-2 walkthrough checked the immersive window only).
- With the full keyboard up, the add/edit sheet's first Day row is half-hidden and the chips scroll to reach it; check whether that is acceptable on the SM-T510's keyboard.
- A toast covers the sheet's footer for about 3.5 s while it is up; check whether that is acceptable on-device.
- For up to 30 s after midnight a week-column tap adds on the old "today" until the tick moves it; check on-device.
- "No explicit keyboard insets in the hosts" is a manual grep in the plan, not a test.

**Adding events, later (the user chose to leave these for now)**
- Lengths beyond 2 h: a 3 h chip and **Pick end…** (an end time on the same day).
- 5-minute steps on the time picker's minutes (15-minute steps today).
- Multi-day all-day events: an **Until…** chip when All day is chosen, picking the end date. Multi-day timed events stay on the phone.

**Accessibility pass (with the `HhIcon` item)**
- Disabled Who chips lack disabled semantics.
- `AddEventButton` lacks an `onClickLabel`.

## From Plan 3a review (deferred)

**For Plan 4**
- L3: decide in the on-device kiosk pass whether connecting and reconnecting Google need a fresh PIN; 3a rides the 2-minute Admin session, and the account chooser's "Add another account" can lead a child out of the app.
- M4: before connecting, check Play services is available (`isGooglePlayServicesAvailable`), and when the chooser comes back cancelled with no data in lock-task mode, hint to exit kiosk; part of the on-device lock-task pass.
- H2: when a connection has no master (cleared by D7), make its writable primary the master again, with Settings' master editing; and count the queued changes a source removal dropped in the D7 toast.
- M7: a source still listed after a refresh but always 403/404 on events.list is flagged for a refresh every pass and keeps its connection "Can't reach": stop re-flagging a kept source, and show per-source health in Settings.
- L4: release logging policy: keep the account email (in request paths) and calendar names out of release logs.
- L6: on the device, delete a whole series on a phone and check its instances leave the tablet; the next daily full sync bounds it today (a full sync when a cancelled id is a known series would close it).

**Later**
- L9: keep a Google access token in memory until a 401 instead of asking Play services for one on every call (it caches them itself; each ask costs an IPC).

## From Plan 3a (deferred)

**For Plan 4**
- The on-device pass: in a signed release in lock-task mode, check Play services' account chooser and consent screens appear when connecting and reconnecting (3a design §9); if not, exit kiosk around them.
- The release OAuth client (the release key's SHA-1) with release signing. Until then a release build offers Connect Google Calendar, and it fails with "Couldn't connect" (the README says so).
- The Connect-a-calendar card's Google button was checked by tests only: a debug build always has the sample calendar, so the card never shows on the emulator.
- Settings: disconnecting a connection, and editing mappings, visibility and the master (3a design §10).
- Bound recurring series in the mirror: Google's sync (`singleEvents`, no `timeMax`) stores every instance, e.g. 730 rows up to 2040 for one weekly event. Measure on the SM-T510 and cap stored instances (for example, drop rows past the sync window after each pass).
- `TodayCardHost` recomposes every 30 s on the clock tick with nothing changed; key the day on the date alone.
- Emulator Play services 26.34 crash-loops on API 35 after a network change (see the setup doc's §5 note); recheck on the SM-T510 before shipping.
