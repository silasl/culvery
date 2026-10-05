# Plan 1 follow-ups

Items raised while Plan 1 (foundation) was built and reviewed. They were deferred on purpose and are for the plan that first touches the area.

## For Plan 2 (Calendar capability)
- `HhIcon` clears semantics and takes no content description. Icon-only buttons need an accessible label.
- The shell composables (`NavRail`, `StatusBar`, `SettingsPlaceholder`) have no UI tests, although the test tags exist. Cover them with the Roborazzi setup.
- Set a module-wide Robolectric viewport default (`w1280dp-h800dp`) so each test class doesn't need its own `@Config`.
- Home header: the gap between the clock and the date is about 55 dp on the emulator, against about 12 dp in the hand-off, so the grid sits roughly 40 dp too low. The line-height trim on `HhType.clock` isn't taking effect. Found on the emulator; both AVDs show it.

## From Plan 2a review (deferred)

**For the ICS provider, or the first JVM-only module** (3a design §8)
- Cover the ICS empty feed with `fullReplace` and zero-duration events.
- Extend the module guard to JVM-only modules.

**For Plan 4e (the device)**
- An on-device SM-T510 pass of the calendar UI.

**For the end-of-v1 design and UX review**
- `opsz` axis for large text.

## From Plan 2b-1 (deferred)

**For Plan 4e (the device)**
- Check DM Sans weights and bold-text truncation on an API 30 AVD (Google Play image); the 2b-1 walkthrough ran on API 35.

**Accessibility pass (with the `HhIcon` item)**
- Clickable event rows and week chips have no `Role.Button`.
- The event detail sheet doesn't scroll. It fits today; longer titles and 2b-2's fields won't.

**Next migration or Room upgrade**
- `androidxSqlite` 2.6.2 is pinned apart from Room's transitive version. Re-check it on each Room bump.

## From Plan 2b-2 (deferred)

**For Plan 4e (the device)**
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

**Later**
- L9: keep a Google access token in memory until a 401 instead of asking Play services for one on every call (it caches them itself; each ask costs an IPC).

## From Plan 3a (deferred)

**For Plan 4e (the device)**
- The on-device pass: in a signed release in lock-task mode, check Play services' account chooser and consent screens appear when connecting and reconnecting (3a design §9); if not, exit kiosk around them.
- Emulator Play services 26.34 crash-loops on API 35 after a network change (see the setup doc's §5 note); recheck on the SM-T510 before shipping.

## From Plan 4a (deferred)

**For Plan 4e (the device)**
- The PIN pad for choosing a PIN has no reason line; check on the SM-T510 that its two stages read clearly.
- The wizard's steps and Settings' pages on the SM-T510 with the Samsung keyboard up (the town search, the person sheet's name field).
- Play services "NetworkCapability 37" crash-loops recur on API 35 emulator images whenever Play Store re-updates Play services; Culvery copes (calls fail and retry), but connecting needs a working Play services.

**For the end-of-v1 design and UX review**
- All 4a layout, colour and copy choices are provisional (wizard padding, rows, swatches, role-chip lines, Settings column, the five extra person colours, the Connect step's connected card — its title wraps onto two lines).
- Settings page list items have no `Role.Tab` (accessibility), alongside the existing accessibility items.
- The calendar's DeleteButton keeps its danger text while disabled; the shared HhSheetButton greys out — pick one.
- The choose-a-PIN pad (and a sheet's Save) says "That PIN is taken — choose another." when someone else has the PIN, so an Admin can learn that a PIN is in use. Accepted: only an Admin (with a fresh PIN) gets there, and it follows from PINs being unique, since a PIN identifies its person.

## From Plan 4b (deferred)

**For Plan 4e (the device)**
- The weather on the SM-T510: the header and the Forecast card at its density, and the fetch over a whole day on the wall.
- On a cold start the clock, the date and the theme show the device's zone for a moment, until Room answers with the household's (`ShellViewModel.now` starts from `LocalDateTime.now()`). Accepted in 4b; check on the SM-T510 whether it shows.

**For the end-of-v1 design and UX review**
- All 4b layout, colour and copy choices are provisional: the Forecast card's rows, its title in every state, the card's icons in sun amber like the header's, the TalkBack condition words, the header items' spacing and divider.
- The Forecast card has a lot of empty space below its three rows.
- Header items could overlap the clock or date if they grow wide (design review, before Climate).

**Accessibility pass (with the `HhIcon` item)**
- The header weather item has no merged TalkBack description: the temperature and High/Low read as two separate items.
- The Forecast row semantics test probes only the day label; also assert that "17°" is absent (count 0).

**Later**
- Open-Meteo answers with one UTC offset for the whole forecast, so data fetched before a clock change is an hour out for the days after it until the next fetch; offline across a clock change, the header's hour and the theme's sunset are an hour out. Known and untested in 4b (ruling 2). Fix: ask with `timeformat=unixtime` and convert each time in the household's zone (dates from the daily rows' own instants).

## From Plan 4c (deferred)

**For Plan 4d (design, UX and accessibility)**
- All 4c layout and copy choices are provisional: the home-app prompt (under Done's title; above Exit kiosk), the 48 dp Move up / Move down buttons, the read-problem line in danger colour with Hide at its right, `bedtime` for a clear night and `mobile` for a phone, the splash with no icon until the launcher icon exists, and the Calendar tab's ‹ › and This week pill.
- Move up / Move down have spoken labels but no other accessibility review yet.
- The dovecote launcher icon and splash are provisional.

**For Plan 4e (the device)**
- Measure Appendix B's numbers on the SM-T510 (start-up, skipped frames, gfxinfo, memory), and the subset font's memory saving there. The emulator's numbers are noisy, and first starts after an install are slow on it.
- A week-long run on the SM-T510 with no slowdown: Java heap, native heap, PSS, threads, GC and frame jank sampled over time (a soak script was offered and deferred to 4e).
- Connect and Reconnect in lock-task on the SM-T510, with screen pinning and as device owner: the account chooser opens outside pinning and Culvery pins again. Pinning dropping while the chooser shows wasn't observed in the emulator walkthrough; it is unit-tested.
- Screen pinning on Android 11: does it prompt after a reboot as Android 15 does? Decide on device owner for the wall.
- The home app after a real power cut; device-owner provisioning on a freshly reset tablet (`docs/setup/release.md` §6).
- Confirm on a real account that a calendar unshared from it is refused (403/404) while still listed, and how long Google keeps listing it; check the row and **Hide this calendar** (unshare a calendar shared in from another account).
- "Delete this and following" on a phone: Google ends the series with UNTIL rather than cancelling it; check the trailing instances leave the tablet.
- The `onCreate` hand-over (a standard-task Culvery created while Culvery is already home) couldn't be triggered on API 35; unit-tested only.
- The R8 build's sign-in, sync, writes and weather on the tablet.
- An optional short API 30 emulator pass (no Google): the splash compat path, Choose home app, pinning, DM Sans weights. Offered, not done.

**GitHub and Google (owner actions, not code)**
- Make the repository public, enable Pages (`main`, `/docs`), set the OAuth Branding homepage and privacy URLs and the authorised domain, then publish the OAuth app to production (it's in Testing, so grants expire every 7 days).

**Later**
- Google Calendar's sync token now lives about six weeks; check quota and data use over a month on the wall (Appendix A's economy estimate assumed a nightly full read).

## Feature ideas (2026-10-02, after v1)

**Each its own spec → plan cycle; not in 4c, 4d or 4e**
- Sleep and wake: dim the screen when nobody is there and wake on a tap. Start with a schedule or sunset (4b's sun times) and the ambient light sensor; camera presence (CameraX at about one frame a second, on-device motion or face detection, nothing stored or sent, and the app says when the camera is on, as Android 11 shows no indicator) is optional on top. Measure its power use against plain dimming on the SM-T510. v1 §14 had screensaver and night dimming out of scope.
- Bin collections from Mid Sussex District Council: no iCal feed, only a per-property tracker page (`mop.php?Track=…`). A read-only scraper provider fetching daily, mapped to Family, with "can't read" shown on the calendar's row (4c's per-calendar health), since the page can change without notice. The `Track` value identifies the property: store it as connection config and keep it out of logs. Check UKBinCollectionData's Mid Sussex scraper as a reference. Stopgap: repeating events in the family's Google calendar.
