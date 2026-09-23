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
