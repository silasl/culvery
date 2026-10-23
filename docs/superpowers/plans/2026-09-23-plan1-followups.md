# Plan 1 follow-ups

Items raised while Plan 1 (foundation) was built and reviewed. They were deferred on purpose and are for the plan that first touches the area.

## From Plan 1 (deferred)

**Later**
- The shell composables (`NavRail`, `StatusBar`, `SettingsPlaceholder`) have no UI tests, although the test tags exist. Cover them with the Roborazzi setup.
- Set a module-wide Robolectric viewport default (`w1280dp-h800dp`) so each test class doesn't need its own `@Config`.

## From Plan 2a review (deferred)

**For the ICS provider, or the first JVM-only module** (3a design §8)
- Cover the ICS empty feed with `fullReplace` and zero-duration events.
- Extend the module guard to JVM-only modules.

**For Plan 4e (the device)**
- An on-device SM-T510 pass of the calendar UI.

**For Plan 4d (design, UX and accessibility)**
- `opsz` axis for large text. *(→ walk-through W1.)*

## From Plan 2b-1 (deferred)

**For Plan 4e (the device)**
- Check DM Sans weights and bold-text truncation on an API 30 AVD (Google Play image); the 2b-1 walkthrough ran on API 35.

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

**For Plan 4d (design, UX and accessibility)**
- All 4a layout, colour and copy choices are provisional (wizard padding, rows, swatches, role-chip lines, Settings column, the five extra person colours, the Connect step's connected card — its title wraps onto two lines). *(→ walk-through W6, W15–W27, W30, W34.)*
- The choose-a-PIN pad (and a sheet's Save) says "That PIN is taken — choose another." when someone else has the PIN, so an Admin can learn that a PIN is in use. Accepted: only an Admin (with a fresh PIN) gets there, and it follows from PINs being unique, since a PIN identifies its person. *(Kept as accepted, 4d triage X3.)*

## From Plan 4b (deferred)

**For Plan 4e (the device)**
- The weather on the SM-T510: the header and the Forecast card at its density, and the fetch over a whole day on the wall.
- On a cold start the clock, the date and the theme show the device's zone for a moment, until Room answers with the household's (`ShellViewModel.now` starts from `LocalDateTime.now()`). Accepted in 4b; check on the SM-T510 whether it shows.

**For Plan 4d (design, UX and accessibility)**
- All 4b layout, colour and copy choices are provisional: the Forecast card's rows, its title in every state, the card's icons in sun amber like the header's, the TalkBack condition words, the header items' spacing and divider. *(→ walk-through W3, W7; TalkBack condition words kept, X17; header spacing W34.)*
- The Forecast card has a lot of empty space below its three rows. *(→ walk-through W3.)*

**Later**
- Open-Meteo answers with one UTC offset for the whole forecast, so data fetched before a clock change is an hour out for the days after it until the next fetch; offline across a clock change, the header's hour and the theme's sunset are an hour out. Known and untested in 4b (ruling 2). Fix: ask with `timeformat=unixtime` and convert each time in the household's zone (dates from the daily rows' own instants).

## From Plan 4c (deferred)

**For Plan 4d (design, UX and accessibility)**
- All 4c layout and copy choices are provisional: the home-app prompt (under Done's title; above Exit kiosk), the 48 dp Move up / Move down buttons, the read-problem line in danger colour with Hide at its right, `bedtime` for a clear night and `mobile` for a phone, the splash with no icon until the launcher icon exists, and the Calendar tab's ‹ › and This week pill. *(→ walk-through W7, W8, W13, W21, W27, W31; the splash icon has landed, X4.)*
- The dovecote launcher icon and splash are provisional. *(→ walk-through W8, W9.)*

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
- One UI's name for Android's default-apps menu: the Tablet lock page's how-to line says "Settings › Apps › Default apps" (`HOME_APP_HOW_TO` in `SetupCopy.kt`); on One UI it may be "Choose default apps". Check on the SM-T510 (E5).
- An optional short API 30 emulator pass (no Google): the splash compat path, Choose home app, pinning, DM Sans weights. Offered, not done.

**GitHub and Google (owner actions, not code)**
- Make the repository public, enable Pages (`main`, `/docs`), set the OAuth Branding homepage and privacy URLs and the authorised domain, then publish the OAuth app to production (it's in Testing, so grants expire every 7 days).

**Later**
- Google Calendar's sync token now lives about six weeks; check quota and data use over a month on the wall (Appendix A's economy estimate assumed a nightly full read).

## From Plan 4c (code minors)

**For Plan 4d (design, UX and accessibility)**
- T16: the gap above the read-problem danger line is uneven (`ReviewCalendars.kt`). *(→ walk-through W31.)*

**Tests and hardening**
- T1: `tools/measure-release.sh` uses `$LOCALAPPDATA` under `set -u`, so it stops on macOS or Linux when adb isn't on PATH; use `${LOCALAPPDATA:-}`.
- T2: the icon literal guard misses ternary and positional literals (`icon = if (…) "x" else "y"`, `PrimaryButton("Edit", "edit")`); scan for any snake_case literal that is a full-font ligature but not in `Icons` (`IconFontTest.kt`).
- T2: `NO_FILLED_FORM` can go stale; assert none of its entries has a filled form and all are in `Icons` (`IconFontTest.kt`).
- T3: `LogHygieneTest` doesn't check the tag argument, and its call regex is whitespace-sensitive (`build-logic/convention/src/test/kotlin/LogHygieneTest.kt`).
- T8: test that a K3 expiry keeps the failure count, and that K4 reads the store once (`LockoutStoreTest.kt`).
- T9: the `isRoleAvailable` half of Choose home app's check is untested (`MainActivity.kt` `openHomeAppScreen`).
- T14: `WeekViewHost`'s `onEveryTouch` wiring is untested at host level (`CardHosts.kt`). (4d-2 pinned the behaviour with `AppContentTest` and `WeekViewTest` instead of a host test.)
- T14: `addedToast` calls `zone.current()` outside `onAppScope`'s try, unlike `master()` and `authorise` (`CalendarEditor.kt`).
- T15: a read-only NeedsSignIn whose reads recover while queued writes keep failing for other reasons stays NeedsSignIn, and the 48-hour ageing freezes until a reconnect (bounded: the chip shows) (`CalendarStore.kt` `markSynced`, `Writes.kt`).
- T15: `writeAccepted` sits in the same try as `applyAcceptedWrite`, so its own failure logs misleadingly (`CalendarSync.kt`, `CalendarStore.kt`); `ZONE_MARGIN_MS` is untested (`CalendarRepository.kt`); `markSynced`'s fold after Unreachable following NeedsSignIn lost its test (`CalendarStoreTest.kt`).
- T16: a calendar deleted after it was flagged unreadable leaves only at the daily refresh (`SourceRefresher.kt`); the master-gone toast's count includes other removed calendars' changes (`CalendarStore.kt`).
- T17: `move`'s Cancelled and failure paths are untested (`PeopleEditorTest.kt`).
- T18: the town search's fill after a save overwrites text typed while the save ran (`LocationPane.kt`); `StepsUiTest`'s `after()` masks a `setUp` failure (`StepsUiTest.kt`); `restartExpiry` picks its timeout from a setup flag read before the lock (not reachable today) (`DefaultAccessControl.kt`).
- Add `android:dataExtractionRules` (`res/xml/data_extraction_rules.xml`) excluding every domain from cloud backup and device transfer: `allowBackup="false"` doesn't stop device-to-device transfer on Android 12+ for targetSdk 31+ (`AndroidManifest.xml`; `docs/privacy.html` now says some tablets may copy the data). Verify on the SM-T510.
- `MainActivity.onResume` refreshes the home role after `super.onResume()` and relies on API 29+ sending ON_RESUME after it returns, so `KioskLifecycle.onResume` sees the new role; add a one-line comment, or refresh inside the `isHomeApp` read (`MainActivity.kt`).

## From the 4d triage (2026-10-06)

The 4d audits (copy, screens, hand-off, accessibility, live emulator) were triaged into D1–D41, W1–W34, E1–E10 and X1–X17. 4d-1 is done (`docs/superpowers/plans/2026-10-06-culvery-04d1-shared-parts-contrast.md`). 4d-2 is done (`docs/superpowers/plans/2026-10-09-culvery-04d2-talkback-timeouts-words.md`): D15–D41 and Decision 1 are closed. 4d is split (user, 2026-10-06): **4d-1** "shared parts, contrast, layout bugs" takes D1–D7 and D9–D14 (`docs/superpowers/specs/2026-10-06-culvery-4d1-shared-parts-contrast-design.md`); D8 is not done (palette swaps are W30); the rest is below. Paths are short (`core/ui` Controls.kt, calendar ui/WeekView.kt and so on); line numbers are as of b7cb40d.

**For the end-of-v1 design walk-through (the user's own)**

After 4e and before the v1 release. Taste calls only; each line names its audit sources.
- W1 DM Sans `opsz`: large titles are about 9% wider than the hand-off (the built clock matches opsz 9, the hand-off opsz 40); every baseline changes (hT1, F:2a).
- W2 The Home grid hole at row 2, col 3: Forecast WIDE with more days, or stretch the last card (H4).
- W3 The Forecast card underfilled and small from 2 m: spread the rows, size up the text (H5, S32, L5, F:4b).
- W4 Coming up empty state: "Nothing in the next 3 days" vs three columns (H7).
- W5 Today card's empty space below "Nothing on today" (L4; the copy part is D39).
- W6 Connect step rebuild: StepTitle, one 60 dp button, a connection row; drop the connected card (S10, S11, F:4a).
- W7 Condition-tinted forecast and header icons; `clear_night` vs `bedtime` (S33, F:4c, 4b amber).
- W8 Launcher icon: the bird's size, and the gap in the themed layer (S37, F:4c).
- W9 Splash dark flash in the day theme: crossfade, or keep it (S38, hL1; device check E3).
- W10 Status bar: "Auto" vs "Night"/"Day"; a preview cue (hS1).
- W11 A Wi-Fi-off icon in the status bar; battery left out (hS2).
- W12 Detail sheet value column 18 dp left of the hand-off (hD1).
- W13 Week header: subtitle alignment under ‹ ›; the faint disabled ‹; ‹ › and This week placement (hC1, F:4c).
- W14 An outlined disabled button style (S1, visual part; 4d-1 fixes the quiet fill on `bg`).
- W15 Wizard buttons inside the 720 dp column; Next as the 60 dp primary (S2).
- W16 Wizard dot size of 12 dp (S3, visual part).
- W17 Welcome screen rows and "Start setting up" (S4).
- W18 Location: the hint jumps under the results; how the saved home is shown (S5 layout, S21, T18 note).
- W19 Location failure card styling (S6, visual part).
- W20 Household: two accent buttons compete (S9).
- W21 Done step: the home-app card, summary rows, `ink` vs `mute` line (S12, F:4c).
- W22 PIN gate card for resumed setup (S13).
- W23 Role chips as full-width rows (S14, F:4a).
- W24 Errors under their field rather than above the footer (S15).
- W25 Person sheet Save as flex-1 (S16).
- W26 Settings: the left title size; page icons (S18, S19 visual, F:4a Settings column).
- W27 Move arrows grouped in one pill, plus a move animation (S20 visual, F:4c).
- W28 Week add target: the full column vs the + hint (L12, triage Decision 4 B).
- W29 Kiosk page status row "Kiosk is on/off" (S22 visual).
- W30 Person palette beyond the contrast rule: lime/Family, blue/violet, red vs `danger`, slate vs `mute`; a CVD ΔE floor in `PersonPaletteTest` (triage Decision 2 B/C, D8, S17, F:4a five extra colours).
- W31 Calendars review: the chip's edge on `surf` with `expand_more` (S23, S42); row heights and Make master placement (S25); "Always shown" text (S26); the read-problem strip (S27, F:4c, F:4c T16 uneven gap); `danger` for unhealthy states (S28 visual).
- W32 Connecting card progress indicator (S30).
- W33 Week legend: wrap it or drop it now chips carry names (A33, layout part).
- W34 Wizard and Settings padding, and the other 4a/4b/4c provisional choices not named above (F:4a, F:4b, F:4c).
- Dim the screen behind the door card while it shows (offered, undecided; Plan 5 walk-through).
- The Culvery mark's top padding: match the space above it to the space at its sides (the user, 2026-10-15).
- The door card's look and the chime's sound; the badge colours (Plan 5, provisional).
- The week's reconnect chip and the sign-in line both name the service ("Google Calendar needs reconnecting" and "… Reconnect in Settings › Calendars"); keep both or shorten the line (Plan 5 §A.3).

**For Plan 4e (the device)**

E5, One UI's home-app menu name, is already under Plan 4c's 4e list above.
- E1 Clock and Home grid at font scale 1.3 and 1.5 on Android 11 (linear scaling, a 156 sp clock; the API 35 emulator capped it); cap the clock if the grid overflows (A34, L2).
- E2 The gesture handle over the grid: does immersive mode hide it in the release build? (hS3)
- E3 The splash flash in the day theme on a cold start, to inform W9 (S38, hL1).
- E4 The dark theme on the device vs the dark baselines (the live audit couldn't switch the theme: it follows sun times, not the system night mode).
- E6 Google's account chooser speed, and how the Connecting card looks while waiting (S30).
- E7 A TalkBack walk of every screen after 4d-2: field labels, modal overlays, the accessibility-event session hook (A12, A1, A38).
- E8 The rail label "Calendar" at 1.5 (88 dp wide in an 88 dp tile on the emulator) (L9).
- E9 Week chip titles at 1.5 with real data (three lines, then an ellipsis) (L7).
- E10 The stored connection health after a clean pass on the real account clears the red "not synced yet" subtitle (L6, D37).

**Dropped in the 4d triage (don't re-raise)**
- X1 The copy audit's "keep" rows need no change: C9, C13, C15, C16, C21, C22, C26, C29 (aligned by D33), C33, C39a, C44, C54, C57, C58, C65, C66, C69, C73, C78, C80, C83, C87, C87c, C87d, C87f, C87h, C87i, C92, C95, C101–C103, C106, C108.
- X2 Plan 1's "HhIcon clears semantics": fixed in part (it takes `contentDescription`); the rest is D26.
- X3 Plan 4a's "That PIN is taken reveals a PIN": accepted as recorded.
- X4 Plan 4c's "splash with no icon": superseded; the icon landed in eb79f14.
- X5 Live L1 "~12 dp vs ~55 dp": a stale Plan 1 figure; the ink gap matches the hand-off (28 dp) and D9 fixes the real cause.
- X6 The screens audit's clock fix (`includeFontPadding = false`): the wrong mechanism; D9 uses `Trim.None` with `Alignment.Center`.
- X7 The hand-off audit's splash fix via `values-night`: the theme follows sun times (`ThemeSchedule`), not the system night mode, so a night resource would be wrong half the day.
- X8 L7 as a 4d item: chip titles ellipsize after three lines at 1.5, but the full title is one tap away; watched in E9.
- X9 L9: nothing clips at 1.5; watched in E8.
- X10 L3: merged into D10.
- X11 L2: the date grows with font scale and the clock doesn't; harmless, and D9 re-measures.
- X12 H3, H6 (geometry), hR1 and the matching rows: they match or are deliberate.
- X13 C104 "Use a sample household": debug build only.
- X14 The A17 note (TalkBack speaks each PIN digit): platform behaviour.
- X15 A41 motion: only the 400 ms theme fade; nothing to reduce.
- X16 A19, the pointer-only area below the chips: covered by the labelled add hint (D26).
- X17 The 4b "TalkBack condition words": kept; they read well.

## From Plan 4d-1 (deferred)


**Tests and hardening (from the 4d-1 final review)**
- `WeatherSyncLoopTest` has no case for a stray `CancellationException` (which should mark the place failed), or for a town change between a failure and the retry.
- `weight(1f, fill = false)` on the label in `HhButton` and `HhChoiceChip` shows no text under unbounded width (a `horizontalScroll`, `wrapContentWidth(unbounded = true)`). No caller does this today; the weight isn't needed, so drop it and re-run `FontScaleTest`.
- `Modifier.lineBox` doesn't harden its constraints, so a caller with a height cap would clip the text. Before a second caller, measure with `maxHeight = Infinity` and report `constraints.constrainHeight(boxHeight)`.

**For the end-of-v1 design walk-through (the user's own)**
- Calendars review: a calendar's Master line, Make master and read problem now sit under its top line (`HhListRow`'s `below`); People's move arrows and chevron are 12 dp apart (W31, W27).
- `HhButton` and `HhChoiceChip` grow with their text at large font scales but have no vertical padding of their own; a two-line chip at 1.5 runs close to its edges.
- Light `danger` on `dangerSoft` is 4.497:1: fine for the icons drawn there today; `danger` text on `dangerSoft` would need a darker `danger` (`ContrastTest` pins it).
- The PIN pad now keeps a blank two-line gap between the dots and the keypad when there's no message, so the keypad doesn't move when a message appears (spec §6.5 trade-off).
- At font scale 1.5 the week view's Today column clips its last chip; the column doesn't grow. Let the column grow with its chips, or settle it at the design walk-through.

**For Plan 4e (the device)**
- The status bar's Sign out and theme toggle rely on Compose's 48 dp touch-target expansion rather than 48 dp of layout (4d-1 ruling 8); check on the SM-T510 that a touch just outside them lands.
- The unpinned window behind Android's home-role dialog (Choose home app, Change home app) is the same with or without device owner: `openHomeAppScreen` unpins either way, so a Home press from the dialog leaves Culvery unpinned until it next comes to the front. Accepted 2026-10-06 with no re-pin timer; device owner only keeps Google's account chooser pinned (`KioskLifecycle.kt`).
- `WeatherRepositoryTest` feeds the failures in directly; that the `@Inject` constructor passes `WeatherSyncLoop.lastFailure` is checked only on the emulator (walk-through item 7).

## From Plan 4d-2 (deferred)

**For Plan 4e (the device)**
- E7 stays: a TalkBack walk of every screen on the SM-T510. The emulator check (`docs/superpowers/plans/2026-10-09-4d2-walkthrough.md`) covered Home, an event, adding an event, the PIN pad, Settings › People and the Settings warning.
- The Settings session counts no touch while Google's account chooser is open during a Reconnect from Settings › Calendars, so a slow chooser can close Settings behind it (4d-2 spec §10; E6).
- `ShellUserActivity` relies on Compose sending TalkBack's accessibility events up through `requestSendAccessibilityEvent`. `ShellUserActivityTest` pins only the delegate's filter (it sends the events itself); only the emulator check (walk-through A.4 path 6) shows Compose sending them. Re-run that check after a Compose upgrade.
- The clock set back during sleep: the session and the sheet timeouts compare wall-clock time, so a session can outlast a sleep if someone sets the clock back. Pre-existing and unreachable in lock-task; compare against `elapsedRealtime` (`DefaultAccessControl.kt`).
- Wizard PIN pad hardening: no wizard PIN pad shows over a live setup session today, because every wizard PIN goes through `SetupSessionGate.carryOn`. If the gate changes, a pad there cancels at 2 minutes; make `overSettings` mean "counting" so it follows the session (`SetupWiring.kt`).

**For the end-of-v1 design walk-through (the user's own)**
- Copy rows no 4d-2 item owned: C25, C43 ("Pick date…" or "Pick a date"), C49 ("Try again" or "Try saving again") (4d-2 spec Appendix A.3).
- The week legend now says each colour's name; keep it or drop it now that chips name their person (W33).
- The warning pills, "Searching…", "Try again", the "Out of date" icon, the Tablet lock how-to line and the message lines' blank line while empty are placed by the plan, not designed (4d-2 spec §2).
- The tap on a faint end Move arrow opens the row editor (pre-existing; W27).
- "Searching…" replaces a visible town list on every keystroke, so rows can vanish under a finger (spec §7.7).
- The Settings › Calendars empty-state line names Google Calendar even with no connectable provider (unreachable in a release build).

**Tests and hardening (from the 4d-2 task reviews)**
- FLAG: "find out why" and "not available" are also said on a selected disabled Who chip and on a busy review picker chip, and the custom `stateDescription` hides "selected"; add them only when `!enabled && !selected` (`EventEditorSheet.kt`, `Controls.kt`).
- FLAG: `PeopleUiTest` hit "connection pool has been closed" at teardown twice (once in `theMoveUpActionFromTalkBackMovesSomeone`, once in Task 10); it passes in isolation.
- `assertClickablesHaveRoleAndLabel`: its `EditableText` skip could skip a clickable wrapper around a text field (skip only when `Role` is null); its false-pass routes (a child `onClick` folded into a merging parent, `clearAndSetSemantics` removing `onClick`) are undocumented in the KDoc; no test for a label that repeats merged child text.
- `testImplementation(testFixtures(...))` sits first in three build files.
- `Pickers.kt:5` has an unused `foundation.clickable` import; `WeekViewTest` checks the add hints in the unmerged tree (TalkBack reads the merged one); `WeekViewTest.kt:291` `onNodeWithText("Today").assertDoesNotExist()` is dead.
- New imports are out of order in about ten test files, `CardsTest`, `WeekViewTest`, `WeatherHeaderItemTest` and `CalendarConnectHostTest`.
- No test pins the review switch having no `OnClick`; the disabled-Who test doesn't assert `who_Mia` stays selected; the Show row's ripple changed; `SemanticsActions` is imported unused.
- `MainActivity`'s `pinShowing` wiring and the Task 11 session wiring are tested only by the emulator check.
- Nested pane titles (person sheet over Settings, editor over picker) are confirmed only by the emulator check.
- Toaster KDoc says toasts "stay longer", true only since `toastMillis`.
- No test that typing clears `nameError`, or that a non-`nameTaken` refusal leaves no `Error` (`PersonSheet`); `PeopleUiTest`'s `waitUntil` uses `fetchSemanticsNode`, which throws instead of retrying.
- The §5.7 stable-total test became a hidden-step count test; its name ("…AndSpeakEachOne") misleads.
- Explicit `mergeDescendants` on the week chip and Today row is redundant (`hhClickable` merges) and no test can detect it.
- `WeekView.kt:389`: the `activity.timeout(BACK_TO_THIS_WEEK_MS)` call only runs when `!exploring`, where `timeout()` returns the base; a plain `delay(BACK_TO_THIS_WEEK_MS)` says the same.
- `SetupWiring.kt:50`: the `countsTouches` KDoc sits above `checksExpiryOnReturn`; move it down.
- `OverlayLayers.kt:90`'s comment gives the wrong reason; `Overlays.kt:8`'s KDoc omits the Settings, wizard and `closesWhenIdle` exceptions; `SetupWiring`'s `booleanArray` box; `touch(edited)` on activity recreation re-arms the session (pre-existing).
- The copy scan (`CopyScan.kt`): an `@label` head before a lambda is skipped; one-word apostrophe fragments skip the quote check; concatenation splits; `lineAt` is O(n) per literal; the `Log.`, `testTag`, `requireNotNull`, `checkNotNull`, `Regex` and `\w*Exception` exemptions have no fixtures.
- `TinyProvider.kt` (calendar-testkit) still has capitalised exception messages; a failed add names the person two ways.
- `LocationPane`'s KDoc has a line of about 230 characters.

**From the TalkBack check (walk-through A.5, 2026-10-12)**
- When a sheet or Settings closes, TalkBack's focus can fall to Android's status-bar clock ("13:16") instead of something in Culvery: after Settings timed out at 2 minutes and after "Event deleted." closed the event sheet (after the 10-minute close it landed on Settings, as it should). Compose has no call to place TalkBack's focus; try `FocusRequester` on the control that opened the overlay and check whether TalkBack follows input focus on the SM-T510.
- The first open of an event announced its title twice ("[event], Heading" ×2) and the first PIN pad of a session said "Enter your PIN", "Who’s this?, Heading" twice; later opens said them once. Not reproduced; listen again on the SM-T510.
- After a date is picked, focus goes to the Time chips instead of back to Pick date… (Cancel on the time picker does return to Pick time…).
- The confirm card's title and its message are two stops ("Delete this event?", "“Swim” will be removed …"); fine, but they could be one.
- Settings › Calendars: "Main" and "New events go here" are two stops.
- "Searching…" wasn't heard when typing a town (too brief, or not announced); "No towns match …" was, once. The town results appearing aren't announced.
- The toast "Event added on …" wasn't listened to with TalkBack on (the PIN was typed with TalkBack off); other toasts were said once.
- A.4 path 12 (sleep past the session) skipped: `adb root` is refused on the Google Play image.
- Stopped before the end: what TalkBack says when a lockout ends (now "You can try again.", tested in Robolectric only); the PIN pad over an edited person sheet outliving Settings by a second (A.4 path 13, second half); focus parked on a scrolled Settings list (path 10, second half). Run them on the SM-T510.

- A described row is two steps at each call site (a description on the parent, `shownNotSpoken()` on the children: Today and Coming up rows, week chips, the header weather), so the next one can forget the hiding; `assertNothingReadTwice` catches it only where a test draws it. A `DescribedRow(description) { … }` would own both (simplify review).
- `HhTextField` hides a placeholder only when it equals the label; a `placeholder: String? = null` that defaults to the label would say so instead of comparing strings (simplify review).

## Done in Plan 5 (2026-10-14)

Built on `plan-5-ring-doorbell`; the tablet step (Task 18) is below.
- Ring doorbell: the "someone's at the door" card, chime, Door tab with badge, and Settings › Door (Task 18 still has to confirm it against Ring's real notifications).
- Repeating events on the tablet: the add-event form's All day and Pick time chips, Repeats and Ends.
- Day one of the family week: the Culvery mark in the top corner over the rail (the small status-bar clock is gone), Home's clock smaller, larger events on the Today card.
- A failed Update now says why: an offline line and a "Google didn't answer" line under the week header, and a sign-in line.

## For Plan 5's tablet step (Task 18)

- Confirm the parser's words, keys and post times against Ring's real notifications: press twice within 2 minutes; does Ring re-post with the picture under the same time (the merge needs the same key and the same `when`)? Is a press posted as ongoing or call-style (the parser now reads an ongoing notice by its words)?
- The snapshot path: the thumbnail and the full-size sheet need a real picture.
- "The Ring app isn't on this tablet" is reachable only on a device.
- Settings › Door opens (no hang) after connecting Ring: the DataStore read in `DoorHealthTest` (`prefs.first`) stalled under the test's dispatchers; unexplained, not shown in production.
- The chime's loudness on the SM-T510 at its notification volume (default 100 %, two strikes).
- After reading captures, run `adb logcat -c` and check the capture switch is off.

## From Plan 5 (follow-ups)

From the final review's triage (51 follow-ups); the accepted ones and those fixed in the final fix wave are left out.

**Behaviour**
- A rethrow after unpin leaves the tablet unpinned, and two granted requests open the system screen twice (`KioskLifecycle.kt`, `MainActivity.kt`).
- Two writers to `_checks`: serialise `checkNow` (`DoorHealth.kt`).
- A second picture for the same event isn't redrawn, because `produceState` is keyed on the `File` (`Snapshots.kt:39`).
- With TalkBack on, focusing Got it counts as a touch for Settings or the sheet (`ShellUserActivity.kt:51-63`); Door rows are separate TalkBack stops (`DoorTab.kt`).
- `RailBadge.Count(0)` would draw a "0" bubble; add a `require` (`NavRail.kt`).
- A series made while offline shows as its first occurrence until the sync lands (ruled); the sample calendar's `assignSeries` on a created series still answers "gone" (calendar-fake).

**Tests**
- Door store and prune: surviving mutations `hasSnapshot = saved`, `part.delete()` and `drop(1)` (`DoorStoreTest.kt`, `DoorPruneLoopTest.kt`).
- Door alerts: the `eventId` guard on a re-post, the zero lower bound on the chime gap, `QuietHoursTest` passing on a London JVM, no "Fails if" lines (`DoorAlertsTest.kt`, `QuietHoursTest.kt`).
- Chime: the release-on-throw path in `MediaPlayerChime` is read-reviewed only.
- Parser: one entry per ignored list, the snapshot re-post fixture, Builder `Icon` tests, the `w <= 0` fallback (`RingParserTest.kt`, `RingReaderTest.kt`).
- Door card and tab: Got it's focus node, the OOM and deleted-file decode paths (`DoorCardTest.kt`), no host test of a ring recorded while the tab is open and resumed (`DoorTabTest.kt`).
- Settings › Door: no 1 ms boundary test for the capture's 30 minutes; the volume test doesn't show save-on-release timing (`DoorSettingsPageTest.kt`).
- Repeats: no test that a one-off insert has no `recurrence` key (`GoogleWriteTest.kt`); the Ends picker is tested only to its callback and the `editor_repeats_*` crop leaves Ends out (`EventEditorSheetTest.kt`).
- Rail: the 8 dp gap and the badge's hiding from TalkBack are unpinned (`NavRailTest.kt`); the `startFirst` test doesn't check the first intent or the empty list (`KioskLifecycleTest.kt`).

**Comments and style**
- Long lines (about 150 to 190 characters): `GoogleEvents.kt:535`, `CalendarEditor.kt:399`, `CalendarSync.kt:271`, `MainActivity.kt:157`; KDoc order at `GoogleEvents.kt:535` and `EventForm.kt:258`.
- History wording "since 5 §A.2" in `Type.kt:38` and `CalendarType.kt:199`; the `ShellLayoutTest` KDoc is vaguer than before; "Task 9's host test" in `EventFormTest.kt`.
- Test KDocs that lost their "why" or overclaim: `AlertLayerTest`, the stale-close test in `DoorAlertsTest`, `aRowWithAThumbnailIsAccessible` (claims a size check it doesn't make), the capture test in `DoorSettingsPageTest` (mentions the recorder).
- Imports out of order: `WeekViewTest`, `WeekScreenshotTest`, `NavRailTest`, `DoorRecorderTest`, `DoorTabTest`; `CalendarSyncTest` has a test after the companion; `IconFontTest.kt:77` isn't re-wrapped.
- `FakeCalendarProvider`'s top-level `offline()` shadows a private Boolean; `AlertLayer` could be private; `CulveryMark` bounds are hand-rounded.
- `RingFixtures`: dangling `[device]`/`[person]` KDoc links, an unused `picture` parameter on `notice()`, and the wording "Check Ring's notifications' one line"; the card's "device · time" line is built apart from `cardWords`.

## Plan 6 (Sonos): emulator walk-through (2026-10-15)

On `culvery-5` (debug build, sample household) against the household's real speakers, by typed address; the rest moved to the tablet at the user's call.
- Passed: Settings › Music between Calendars and Door; Find Sonos on the emulator says no speakers answered (its scan can't reach the home network); a mistyped address and one with `:1400` each say how to type it; the typed address connects and reads the household's rooms.
- Protocol check 1 passed: `GetZoneGroupState` and `GetHouseholdID` answer on the household's firmware (Sonos 86.10). Checks 2 (the group snapshot) and 3 (Shuffle everything's URI, empty metadata, one-item browse and order) move to the tablet; the tests' pins of ruling 12 stay marked as unconfirmed until then.
- Fault: checks failed now and then as "can't reach Sonos". The speakers answer every call in full from the PC; inside the emulator the larger answers (`GetPositionInfo`, `GetZoneGroupState`, about 1.6 KB) sometimes arrive cut short ("unexpected end of stream"), from the emulator's network layer with Sonos closing each connection straight after answering. Two commits harden Culvery for a busy network all the same: a fresh connection per call, and one retry for a read whose connection fails (commands are never sent twice). The flicker's real test is the tablet, which has no such layer.
- Moved to the tablet: Find Sonos by scan, the Home card and Music tab on live groups, Play/Pause/Next and both volume sliders against the Sonos app, the paused room staying on Home, the Rooms chip, a radio and a playlist Favourite, Shuffle everything from a paused station, the doorbell lowering (two-room group, Got it, a room dragged meanwhile, a second ring, 0 %), offline, TalkBack, and the captures for the fixtures.
- For the end-of-v1 design walk-through: the Music tab's unselected Rooms chip blends into its row; empty message lines leave gaps in Settings › Music and the address sheet; the Home card's buttons are tight to fit Shuffle everything; the tab keeps Next on a row with nothing playing while the Home card drops it.

## Plan 6 (Sonos): on the tablet (2026-10-16)

Release builds installed with `install -r` over Plan 5's (the household kept); Culvery pinned again once in front.
- Passed: Find Sonos by scan (the SSDP search under the multicast lock found the household within seconds); the usual room; no "can't reach Sonos" at all (the emulator's flicker was the emulator); the Home card and the Music tab on live groups; Play, Pause, Next and the volume, both ways with the Sonos app within about 2 seconds; the group slider keeps the rooms' balance (protocol check 2 passed: `SnapshotGroupVolume` then `SetGroupVolume`); the Rooms chip moves one room; the real bell with a two-room group: both rooms dropped before the chime, Got it brought both back; nothing in the log from the lowering or the restore.
- Fault: a regrouping in the Sonos app showed only at the 30-second layout check, and a slider on the old row was refused. The user's ruling: each 2-second check asks every room which group it's in (`GetZoneGroupAttributes`) and re-reads the layout at once on a change (0f967f8, reviewed). On the household's speakers that answer's group id matches the room list's exactly, so a still system isn't re-read every check. Grouping now shows straight away.
- Not tried on the tablet (the user will raise a bug if one shows in use): the paused room staying on Home, radio and playlist Favourites (they need Sonos Favourites set up in the Sonos app), Shuffle everything (protocol check 3, its library URI, empty metadata, one-item browse and order, stays unconfirmed and pinned as such in the tests), the "Nothing playing" card, offline, TalkBack, and the captures (the fixtures stay the documented examples; the release build has no capture).

## From Plan 6

- Move `MusicSettings` and `DoorSettings` onto `retryWithBackoff`, so one DataStore read failure doesn't end their flow.
- Throttle the "A music check failed" log line while Sonos stays unreachable.
- Move the weather and Google Calendar clients onto `:core:net`'s `Call.await` and `AndroidNetwork`.

## Plan 7 (Hue): emulator walk-through (2026-10-19)

On `culvery-5` (debug build) against the household's Hue Bridge by its typed address ([bridge address]); the PC's VPN paused.
- Passed: Settings › Lights after Music; the address sheet's typing lines; the 30 s pairing timeout and Try again; paired with the button; Lights on the rail; Find Hue Bridge says none answered (the emulator's search can't reach the home network). TLS against the bundled Philips root on Android 11 with nothing in the log (protocol check 3 from the wall).
- Passed: the tab's rooms, sliders and scenes; commands both ways within about a second; 20 quick toggles and a 10 s slider sweep with no refusal (the pacing); a slider on an off room turns it on; Home with every card shows Lights and drops the forecast; All lights off in one command (protocol check 2) and with a room left out.
- Passed: Holiday mode with Mimic presence and a schedule that was already on: a Child's PIN refused, a fresh PIN both ways, Turning on… then On, a second tap ignored, Mimic presence started then stopped from the wall (protocol check 1, seen in the capture), the schedule left on.
- Changes from the walk-through: All lights off is lit in the accent while any room it would turn off is on, and disabled otherwise (04c6b03, de4ce59); disabled buttons everywhere fade their label and icon to half (b92e24b); the capture also blanks room and light kinds (d0a7840).
- Not tried (the user's choice): offline, TalkBack.
- Captures: taken (names check clean after the kinds were blanked); the fixtures move to them in the tablet step.
- For the end-of-v1 design walk-through: with Holiday mode on and a room on, both card buttons are accent and look alike; the card's empty middle; Settings › Lights spacing and colour-only selection; the pairing sheet's wrapped title.

## Plan 7 (Hue): on the tablet (2026-10-19)

The release from a40fbd6 installed with `install -r` over Plan 6's (the household kept); Culvery pinned again once in front.
- Passed: paired by the typed address across the two home networks (the bridge on the router's, the tablet on the mesh's); the main room, rooms left out and Mimic presence chosen in Settings › Lights; Home's card (toggle, ›, All lights off, Holiday mode both ways with a fresh PIN, Mimic presence started and stopped); a wall change shown within about a second; nothing from Lights in the tablet's log.
- Find Hue Bridge on the tablet: "No Hue Bridge answered." The mDNS search doesn't cross from the mesh's network to the router's, as expected; the typed address is the way in for this household. If the bridge moves to the mesh's network (or the mesh runs in access-point mode), try the search again.
- Fixtures: reshaped on the household bridge's answers (a40fbd6); no parser change was needed. The capture held no bridge or config answers, so those keep their documented shape.

## From Plan 7

- `Bridges`, `LightsFollow` and `LightsConnect` mirror Music's `Speakers`, `MusicChecks` and `MusicConnect`: a generic later.
- A `HomeCardPriority` scale in `:core:plugin`, so a card states its rank without renumbering the others (and the hand-kept rail-order comment goes).
- Generic `AppSourceTest` and `ModuleBoundariesTest` rules instead of a line per capability.
- One `HueCapture` in main guarded by `BuildConfig.DEBUG` instead of debug and release source sets (Sonos uses the same split).
- `HueSystem` copies its map on every stream event and scans the scenes on every publish; `LightRoom`'s `List` is unstable for Compose.
- The `RecordingToaster` copies left in calendar, door, access and setup onto `:core:plugin`'s test fixture; Door's `VolumeRow` onto `HhPercentSlider`.

## Plan 8 (Sleep and wake): emulator walk-through (2026-10-19)

On `culvery-5` (debug build, an emulated front camera given on the command line).
- Passed: Settings › Display after Calendars; the camera prompt (deny, deny, the refused line, allowing it from Android's settings); the camera mark showing exactly while Android lists Culvery as the camera's client; asleep after 2 minutes with the mark; a tap only wakes, a quick second tap presses nothing; sheets, the PIN pad and Settings hold the wall awake and it sleeps 2 minutes after each closes; night set from 15:00: the night clock 1 minute after Settings closed (Settings holds it awake), the camera off and the window at its lowest at night; not in front: no mark, Android's brightness; the heat line (numbers only).
- Fault: a restart at night showed the night clock, then the bright Home screen 0.14 s later, because setup-not-read-yet counted as the wizard holding the wall awake; now only the wizard showing holds (90262f1, reviewed). A process restart at night now stays on the clock.
- Debug builds don't pin, so re-pinning after the camera prompt is checked on the tablet.
- Not tried here (moved to the tablet, the user testing in use): the doorbell by day and at night, camera wake-up with a real room, a tap on the night clock and its evening level, the night's end, the "no front camera" line, TalkBack, and the power and heat measurement.

## Plan 8 (Sleep and wake): on the tablet (2026-10-19)

The release from 25ee446 installed with `install -r` (the household kept); the user is testing it in use. Merged to main locally the same day at the user's request.
- Not done yet: the power and heat measurement (runs A, B, C; decision 9's bar: a thermal status rise, a 3 °C larger temperature rise, or over 2 %/h more drain by the charge counter, against camera wake-up off). Until it's run, camera wake-up ships on by default.
- Not tried yet on the tablet: re-pinning after the camera prompt, camera wake-up distance and speed, the night clock in the dark room, the doorbell by day and at night, TalkBack, whether Android repeats the thermal status at registration, whether BRIGHTNESS_OVERRIDE_OFF is minimum or off on this Samsung.

## From Plan 8

- Not done, and why: skipping `moved()` while awake would be wrong, since movement while awake must restart the sleep wait.
- One `capture()` a second instead of the camera's repeating request: a bigger change; the tablet's power runs will show whether it's needed.
- Not composing the shell while asleep: it would lose state and recompose everything on waking; measure first.
- `AlertHost` holding alerts back at night for every capability, not only Door; overlays exposing whether they hold the wall awake, in place of `SetupWiring`'s `holding(…)` of five booleans; `UserActivity`'s touches as an event flow.
- A `SettingsTokens.pageGap` shared by every settings page (Display's `settingsGap` is another 16 dp); one `HhSliderRow` for Display's brightness rows and Music's.
- `ShellLayers` taking one `screen` (or a list of layers) instead of `sleeping`, the sleep slot and the camera mark.
- `SetupState`'s `setupStore` onto `:core:plugin`'s `settingsDataStore`, as the four settings files now are.
- FLAG (pre-existing): `DoorTabTest` hit "connection pool has been closed" at teardown four times across Plans 7–8; it passes in isolation. Listed with the other flakes under Plan 9a.

## Plan 9a (Redesign: frame and Home): emulator walk-through (2026-10-20)

On `culvery-5` (debug build; the AVD restarted with an emulated front camera for item 2).
1. Frame: passed. "Preview" stays and ends at the next scheduled switch.
2. Watching pill: passed once the AVD had a front camera (the first start had none). The overlay mark shows over Settings, a sheet and the PIN pad.
3. Home location: skipped. Settings can't clear it; the screenshot tests cover it.
4. Today card: five rows pass; seven (two added, then deleted) scroll, with the pill and Calendar. Font scale 1.3 (the largest on API 30) fits with nothing cut. 0 and 2 events are covered by the screenshot tests and the tablet.
5. Lights above Music: passed.
6. Lights card: passed except the slider (fault 1 below).
7. Music card: passed.
8. Ring: passed. Scrim, card, "Music lowered" chip, overlay mark and badge; the card closes after 60 s (about 57 and 63 s seen); Door history opens the Door tab and clears the badge.
9. PIN lockout: passed.
10. Toasts: passed. The Settings timeout works; the user would like it shorter (design review).
11. Night clock: fault 2 below, then passed (the user: it looks good).
12. TalkBack: skipped by the user.

Faults found:
- Fault 1, the Lights slider bounced back to the old value after let-go. 37ef737 and b729ca8 hold the let-go value until the bridge reports it (5 s ceiling), on the card and the tab. The user still saw the card's slider jump back and forward again; the cause was the Hue provider, where the stream reports older levels about a second apart after a local update. 18a4b58 and 58f485d ignore a group's brightness reports for 5 s after a send unless they match, end the wait on a scene recall, and show the latest held-back level at the deadline. The card slider was confirmed on the tablet (see the tablet section below). Not an issue, noted: All lights off and switching on pause on the previous value, then move.
- Fault 2 (Plan 8), the night clock was woken by a home-activity relaunch. `DisplayState` used `AlertHost.inFront`, false on every pause, and a `singleTask` relaunch pauses and resumes in about 12 ms, which woke the wall for 60 s. e83dd7a and b3691c2 use `ShellAlerts.shown` (start to stop) instead. Changing the night times applies 60 s after Settings closes, by design.

Open tablet checks:
- A ring while music plays: on the emulator the Office speaker went from playing (lowered 24 to 5) to its Bluetooth source with nothing playing; the volume was restored to 24, and only Culvery talks to it. The emulator logged `MusicUnreachableException` every few seconds throughout. Likely explained by fault 3 on the tablet section below (the idle Bluetooth input reports PLAYING with no track, and the source flipped to it). Keep the ring-with-music re-test on the tablet open.
- The tablet never sleeping when nobody is around: fault 2 doesn't explain it. Read `logcat -s DisplayState` and `logcat -b events | grep wm_on_paused` on the tablet.
- The Hue connection lost after the evening sleep: explained (2026-10-20). The tablet still has the household's parental restrictions from its time as a child's device, which cut its network at night; the calendar drops at the same time. Not Culvery.

For the end-of-v1 design review:
- Home with the full column: the empty band in the Lights card between the top row and the slider (and above the Music card's row) is large, as spec §4.2 says, and larger when Lights takes the full column.
- The sliders are Material 3's, with a gap, a vertical thumb bar and an end-stop dot; the handoff draws a plain filled track.
- The row under the pinned pill shows faintly between the pill and the card's foot (the fade is linear to the surface colour).
- At font scale 1.5 the pinned pill ellipsises: "1 more today · Open cal…".
- "3 more today" while five rows are visible follows the handoff's formula; check it reads right.
- The Settings timeout could be shorter.
- The 1 dp disabled outline on light backgrounds is very faint (the handoff's is 2 px).
- The "Music lowered" chip: icon and words are `ink` on the quiet fill, with 12 dp side padding and a 6 dp icon gap, none of which the handoff gives.
- The Door card has no snapshot in the Home screenshot; "Got it" stays 17 sp against the handoff's 28 px.

Test flakes seen across the build (timing, not Plan 9a's code; each passed on a rerun):
- `LightsActionsTest` and `LightsCapabilityTest`: "Timed out waiting for 5000 ms" on DataStore and StateFlow reads.
- `DoorCapabilityTest.theTabAppearsOnceRingIsConnected`.
- `SonosSystemTest` after a clock jump.
- "Database connection closed" (a Room database closed in teardown while a flow still reads it, wrong thread) in `DoorTabTest`, `PeopleUiTest`, `DoorRecorderTest` and `EventDetailHostTest`.
- `CalendarMigrationTest`.
- A long `java.io.tmpdir` makes Robolectric database paths pass Windows MAX_PATH and fails with SQLITE_CANTOPEN; never set TMP, TEMP or `java.io.tmpdir` for Gradle.

## Plan 9a (Redesign: frame and Home): on the tablet (2026-10-20)

- Density: 240 dpi, 1920 × 1200 landscape, no override. The Units' 1.5 holds.
- Installed the release from the branch with `install -r`; the mapping is kept outside the repo.
- The user: the redesign looks good at the wall. The card slider stays put with the bridge reachable (fault 1b confirmed on a device).
- Covered-camera test: with the camera covered the wall went to sleep (sleep layer and glyph), with the display on, the camera open and Culvery in front. The user skipped the uncovered test and will watch it in use. If the wall stays awake in an empty room, look at the camera's movement check.
- The display was found off at Android level before the install; the user may have pressed the lock button.
- Fault 3 (Plan 6): Home's Music card always showed the Office, because its Bluetooth input reports PLAYING with no track. Fixed in 4f1bde2 and 234877b: an idle Bluetooth or line-in input doesn't count as playing for Home's pick and the tab order (TV audio still counts). Confirmed on the tablet. This likely also explains the emulator's "music stopped after a ring" (see the open item above).

Still open on the tablet:
- A real ring while music plays.
- The night clock in the dark at bedtime: readable from bed, not lighting the room, the glyph findable only on looking.
- The Hue connection lost after the evening sleep: explained, the parental restrictions cut the network at night (see above).

For the end-of-v1 design review:
- An Input group picked as Home's fallback says "Nothing playing" while its button shows Pause.

## Plan 9b (Redesign: the tabs): emulator walk-through (2026-10-20)

1. The four tabs line up: passed.
2. Lights: not checked on the emulator (the AVD couldn't reach the Hue bridge though its network validated); moved to the tablet.
3. Music: passed. Header counts, the card's control row and labelled volume, the rooms chip, and Play last on a quiet room.
4. Door: passed. A fake ring while the tab was open showed NEW and "1 new ring" and kept the rail badge; leaving and coming back cleared the badge and the NEW.
5. The week: passed. One arrow pill, This week past the current week, tinted two-line chips, no legend; pressing empty space shows "+ Add on …" and adds on that day.
6. Multi-day: the sample calendar's Half term shows as one bar across next week (passed). "Day 2 of 3" on Home was not seen: it needs the event's second day. Tests cover it; check on the tablet when a real multi-day event is on. Creating multi-day events is Plan 9c.

Faults found: none.

Test flakes seen during 9b's build, to add to the flakes list under Plan 9a (5 s timeouts and timing; each green on a rerun):
- `DoorRecorderTest.everyNoticeIsHeard`
- `DoorTabTest.tappingTheOpenTabAgainMarksEverythingSeen`
- `CameraAccessTest.aPermissionTakenAwayInAndroidsSettingsIsSeenOnResume`
- `SetupWizardTest.aRestoredWizardAsksForThePinAgain`
- `SpeakersTest.noConnectionOpensNothingUntilOneIsSaved`
- `HueSystemTest.theStreamEndsAtOnceWithAWaitPending` was a test race, not a flake; fixed in the cleanup pass (e37a1dc). It still polls real time with a 5 s cap, so it could fail on a very slow runner.

For the end-of-v1 design review:
- The light theme's week nav pill (surf2 on bg) is nearly invisible.
- A bar running past the week keeps its inset; the handoff stretches it.
- The bar's date_range icon in yellow or lime is faint in light.
- Column outlines and today's ring show through a bar.
- A lone Lights room fills half the width at full height.
- The 63 dp foot under the last Lights row after the pill goes.
- The Door Yesterday label's gaps.
- "1 room" and "1 more room" singulars were added.
- NEW is on rings only; motion isn't tagged.

## Plan 9b (Redesign: the tabs): on the tablet (2026-10-20)

- Installed the release from 470dee5 with `install -r`; the mapping is kept outside the repo.
- The user at the wall found four Lights faults, all fixed and re-installed (release 2220a04), then said "looks great":
  - A: the tray lacked the design's ONE-TAP label with its touch icon (84be81e).
  - B: the tray sat too high. It is now bottom-aligned with the "N of M rooms on" line, which moved every tab's right-hand header content and gave the week's reconnect chip a footer row (cde048a).
  - C: scene chips were cut at the card's edge. They now wrap, scrolling inside the card if they don't fit (7f9fe81).
  - D: off rooms read "1%". The Hue room reports brightness 0 while off; the provider now keeps the average of the room's lights' own levels (2220a04).
- The Lights checks moved here from the emulator walk-through (the AVD couldn't reach the bridge) are covered by the user's look.

Still open on the tablet:
- "Day 2 of 3" on Home: check when a real multi-day event is on.

For the end-of-v1 design review:
- The week's ‹ › pill follows the foot of its sync/Update now row and moves when that row appears.
- The ONE-TAP label uses the 13 sp section style; the handoff converts to 12 sp.
- Nothing shows that a card's chip area scrolls.
- An off room's remembered level can shift after a full read (it becomes the average of all its lights).

Test flakes seen during the fixes (each green on a rerun): `CameraAccessTest`, `DoorRecorderTest`, `BridgesTest`, plus the known `DoorTabTest` and `LightsActionsTest`.

## Plans 9c-1, 9c-1b and 9c-1c: emulator walk-through (2026-10-22)

On `culvery-5` (debug build), driven by Claude with the user watching.
1. Re-checks of what changed in 9c-1 and 9c-1b (the week, toasts, the grey frame, detail chips, the PIN pad, Starts and Ends, clashes, repeats with a span, Check, suggestions): passed, apart from faults 2, 6 and 7 below.
2. Idle close: an untouched editor closes at 2 minutes; an edited one warns at 9:40 and closes at 10.
3. "4th Saturday" and "last Thursday": land on Sat 24 Oct and Thu 29 Oct and nowhere else seen. The week view stops 3 weeks ahead, so the November dates (26 and 28 Nov) weren't seen.
4. Counted series: the detail reads "Every Saturday · 12 times · last on Sat 9 Jan"; Check says "…from 24 Oct to 9 Jan. 12 times in all."
5. Undo offline: the queued series was removed and nothing came back once the sample calendar was online. The emulator's wifi doesn't take the sample calendar offline; the debug offline broadcast does.
6. Undo by another person: refused, "Alex can only change events they created."
7. "Day 2 of 3" on Home: with the date set to Sat 31 Oct, "Half term · Day 2 of 3 · Family". The date was put back.
8. A timed span over midnight: Fri 23 22:00 to Sat 24 01:00 shows "22:00–" on Friday and "until 01:00" on Saturday.
9. Person and Favourites sheets: the wider sheet. Favourites lists the real Sonos favourites; none were tapped.

Faults found:
- Fault 1, some screens are blank for about a second before they fill (the Calendar tab after a restart, Settings › Calendars, Home location, People, Home's Today card). Left for the Home slow-fill check on the tablet below.
- Fault 2, the quick picks offered today after the copied start (16:00) had passed. ecea7c3 starts them tomorrow once a timed start is past the time the editor opened; all-day keeps today. Re-checked at 20:36: Swimming's picks start Thu 29 Oct.
- Fault 6, the keyboard stayed up after leaving the title, over the date grid and over the week once the editor closed. Compose's `hide()` did nothing once focus had cleared. c7279a9 hides it through the window, and a touch on the pane leaves the title before the pane acts on it; 3a73ffa and d91ec64 tidy it (the handler stays keyed, since removing it mid-press cancelled the tap underneath). Re-checked: one tap on a Who card chooses the person and puts the keyboard away.
- Fault 7, with "From before" showing and the keyboard up, the first ✕ only left the title. Gone with fault 6; a9e99fc pins one tap on close asking to discard.

For the end-of-v1 design review:
- Copying a suggestion from a series that is still running makes a duplicate that clashes with itself.
- The toast for an event over several days or past midnight gives no dates or times ("Added Late gig.").
- Undo's refusal reads oddly for an admin, beside the role line "Adult — can add and change any event".
- The clash wording; the lane bar showing dates only; the "Too long" tag staying after the event is shortened again; the Ends grid opening on the end's month.

## Plans 9c-1, 9c-1b and 9c-1c: on the tablet (2026-10-22, evening)

- Release from 145dc7e installed with `install -r` (household kept); mapping in `culvery-notes/mappings/mapping-145dc7e.txt`.
- Doorbell, 2026-10-21: logcat reaches back only to the afternoon of the 22nd and usage stats to 20:49 on the 21st, so the 13:10, 16:20 and 18:03 rings can't be read; the Door tab's history doesn't reach 13:10 either. Lead only: Android keeps the Ring app in standby bucket 40 ("rare") between notifications, lifted to 10 for ten minutes by each one. On Android 11 that shouldn't hold back Ring's high-priority pushes. The 22 Oct 18:07 ring and motion arrived as notifications. Next missed ring: note the minute and read logcat the same day.
- Home's speed, cold start (stop, start, screenshots about every 0.8 s), three runs: splash by 0.2 s; Music's card by 1.5 s; Today filled by 1.3–1.5 s (once still empty at 1.8 s); Lights read "Looking for the lights…" until 2.2, 2.5 and 2.9 s. Home filled by 2.2–2.9 s, the Hue bridge's first read the slowest part. Return from the Calendar tab, three runs: filled in the first frame, about 1 s after the tap command (most of it the command itself).
- Each relaunch shows Android's "App is pinned" dialog; expected without device owner (release.md §6). Stopping Culvery while the lock screen sits behind it lets the lock screen's 5 s timeout put the tablet to sleep; a test artefact, not a fault.
- At the wall (the user, a quick look, not the full Step 3 list): everything seemed fine. The full Step 3 checks are still to do.
- At the wall, 2026-10-23 (the user): the Step 3 checks all passed, done lightly rather than thoroughly; household use is the main testing from here, so faults may still show up there. Fault A didn't recur.

Fault found:
- Fault A, while adding an event (title typed), a tap on a start date closed the editor and an existing event's details opened. Not reproduced; no crash and no warning in the log. Nothing in the editor opens an event, so the editor closed first and a tap reached the week or Home behind it; the idle close (10 min once edited, warned at 9:40) and a session ending don't fit. Next time: note the minute, the entry point and where the opened event sat, and read logcat at once.
- Fault B (Plan 6, Sonos), Favourites lists "Discover Sonos Radio" and "Trending now", which do nothing when tapped. **Fixed 2026-10-23:** they are Sonos Radio shortcuts with no link to play, now left out.

## From Plans 9c-1b and 9c-1c
- **Choosing the destination calendar per event:** every new event still goes to the household's one destination calendar.
- **Skipping one date of a series** from the event's details.
- **An undone create whose drain delete is lost on restart:** `UndoneKeys` lives in memory, so after a restart the create can still land; if it does, its title is remembered too. Related (final review M2): an Undo that races a drain create landing in the same moment leaves the event on the week until the next sync, and the key stays in `UndoneKeys` until restart.
- **Home slow to fill:** cold start 2.2–2.9 s to Home filled on the tablet, the Hue bridge's first read the slowest part (Task 16, above); the return from the Calendar tab is quick. Decide whether Lights should show its last known rooms while it connects.
- **Hiding a calendar can hide a title another visible calendar still has** (final review M1): hiding folds nothing back, so a title supplied by the hidden calendar's event drops out of "From before" until the visible calendar's event next changes or the next full sync. Re-supply on hide, as `forget` does. **Fixed 2026-10-23.**
- **A phone's counted series is expanded in full** on every detail update (final review M3, `Repeats.kt:28`); cap it or work out the last date directly.
- **End-of-v1 design review:** 9c-1b's notes (the plan's "Notes for the end-of-v1 design review" and the ledger's "provisional for the design review" lines) and this plan's (the walk-through list above; the ledger's copy at `culvery-notes/sdd-ledger-plan-9c1c.md`).

## Left out of Plan 9c-1c on purpose (2026-10-22)
- **Choosing the destination calendar per event:** an event still saves to the household's one destination calendar. Picking another calendar for one event is a later change.
- **A year-long fetch of past events:** the tablet still keeps about a day of past events. Title suggestions use a remembered list that sync builds up over time, so "From before" starts sparse and fills in over the following weeks.

## Plan 9c-2 (Redesign: the wizard and Settings): emulator walk-through (2026-10-23)
- **The wizard** was walked from cleared app data and is fine. It was a light pass by the user; the duplicate-name and taken-colour checks were not reported one by one.
- **Settings** are fine: the list panel, the titles, People and its sheet, Calendars, the slider rows on Door, Display and Music, and the closing ring.
- **Home location** with Wi-Fi and data off: the reason shows under the field, with no red ring.
- **"Use for new events"** can't show with the sample calendars, because the only writable one is the main calendar. Check it on the tablet with real calendars.
- **Not tried with TalkBack:** focus moving to a refused field in the person sheet, and "N towns found" being spoken from the collapsed status line. Check both on the tablet.
- **No faults.**

## Plan 9c-2 and the Sonos and calendar fixes: on the tablet (2026-10-23)
- **The release from 85c49493** (9c-2 plus the fixes branch) is installed with `install -r`. The mapping is at `culvery-notes/mappings/mapping-85c4949.txt`.
- **The user found it all good:** Settings, Sonos Favourites without the two shortcuts, and the playlists showing and playing in a room. The logs show no Culvery errors; the one native crash was Google Play services' zygote.
- **Not tried (TalkBack was off):**
  - focus moving to a refused section of the person sheet, and whether its line is spoken twice;
  - TalkBack on a locked role row or a taken swatch;
  - "N towns found" spoken from Home location's status line.
  The user will raise them if they show in use.
- **Not tried either:** "Use for new events" with real calendars, and a new Sonos playlist appearing on the next open.

## Left out of Plan 9c-2 on purpose (2026-10-23)
- **Settings › Calendars keeps "Family"** for the household's calendars; "Family" or "Everyone" is for the design review.
- **The calendar editor's title field keeps its own ring:** folding it into the shared text field's error ring is deferred.
- **A colour the editor refuses names "someone else":** the sheet's list of taken colours is the one it opened with, so a colour taken since then has no owner's name. Building the list from the live household instead is a later change.
- **The Home location field never turns red** for a failed search, and the Hue address field never does for a failed connect: they show the reason under the field, because the text isn't wrong.

## From Plan 9c-2
- **End-of-v1 design review:**
  - people rows' names at 18 sp, not the handoff's 19;
  - 35 dp under a page title;
  - Show's group width and the person pill's gap, read from 5c;
  - the Set PIN pill clipped by a few px at the sheet's scroll edge on an 800 dp screen;
  - "When the doorbell rings, lower music to" wrapping to two lines;
  - the Repeats rows a few dp taller, taking the radio row's sizes;
  - the person pill on Settings › Calendars is capped at 140 dp, which leaves a calendar's name about 27 dp at font 1.3 in the wizard column.
- **Sonos playlists:** the sheet removes duplicate favourites using the favourites read just before. It relies on that order, which `MusicActions` keeps.

## To check when the tablet is next plugged in
- **Doorbell missed (2026-10-21, about 13:10):** the Ring doorbell was pressed but the tablet showed no ring and no motion. Pull logcat for Culvery's Ring listener around that time (notification received? card shown? chime?), check the Ring app's notification settings and the Plan 5 notification shapes, and whether the parental restrictions on the tablet were active then (they already cut Hue and the calendar in the evenings). A later ring the same day (about 16:20) came through fine, so it is intermittent, not a broken listener; compare the two in logcat. A third, unexplained ring event at about 18:03 the same day: read it in logcat too (what notification arrived, and what the tablet did with it).
- **Home slow to fill (9c walk-through, 2026-10-22):** on the emulator, Home's cards took a moment to fill after opening. Time it on the tablet (cold start and return from the Calendar tab) before deciding whether anything needs fixing. The same walk-through saw it on the Calendar tab after a restart, Settings › Calendars, Home location and People (fault 1); time those too.

## Feature ideas (2026-10-02, after v1)

**Each its own spec → plan cycle; not in 4c, 4d or 4e**
- Sleep and wake: Plan 8, spec `docs/superpowers/specs/2026-10-19-culvery-8-sleep-and-wake-design.md` (night clock, camera wake-up in the day, brightness by the time of day — the SM-T510 has no light sensor — and heat logging).
- Bin collections from Mid Sussex District Council: no iCal feed, only a per-property tracker page (`mop.php?Track=…`). A read-only scraper provider fetching daily, mapped to Family, with "can't read" shown on the calendar's row (4c's per-calendar health), since the page can change without notice. The `Track` value identifies the property: store it as connection config and keep it out of logs. Check UKBinCollectionData's Mid Sussex scraper as a reference. Stopgap: repeating events in the family's Google calendar.
- Sonos (the user, 2026-10-14): Plan 6, spec `docs/superpowers/specs/2026-10-15-culvery-6-sonos-design.md`.
- Philips Hue (the user, 2026-10-14): Plan 7, spec `docs/superpowers/specs/2026-10-16-culvery-7-hue-design.md`. Holiday mode drives it.
- Shopping list (the user, 2026-10-14): its own spec; no detail yet.
- Doorbell ring tones (the user, 2026-10-15): a list of chimes to choose from in Settings › Door, perhaps switching itself for Christmas and Halloween.
- Sound buttons (the user, 2026-10-15): buttons that play a short sound on the wall, such as the Imperial March or a sad trombone ("wah wah wahhh").
- Holiday mode (the user, 2026-10-14): saves power (puts the tablet to sleep) and drives other services while the family is away: Hue lights to their holiday setting, Arlo's settings, the thermostat to eco. Needs the Hue, Arlo and thermostat links first, and Sleep and wake for the tablet's part.
- Thermostat, Nest (the user, 2026-10-19): its own spec; so far: if the last day of a holiday is in the calendar, override Nest's away mode (so the house is warm for the return). Holiday mode sets it to eco/away.
- Reminders (the user, 2026-10-16): its own spec; no detail yet.
- Family slideshow (the user, 2026-10-16): its own spec; no detail yet.
- Event celebrations (the user, 2026-10-16): family birthdays and other occasions marked on the wall, with a sound or something like it. Its own spec; no detail yet.
- Voice control (the user, 2026-10-16, a question): its own spec; no detail yet.
- Party mode (the user, 2026-10-15): one switch that sets the house up for a party: music on (Sonos), chosen smart plugs on, and so on. Like Holiday mode, it drives other services, so it needs their links first (Sonos from Plan 6; smart plugs have no link yet). Its own spec; no detail yet.
- Eufy robot vacuum (the user, 2026-10-16): start the Eufy robovac from the wall. Its own spec; no detail yet.
- Multi-day events (the user, 2026-10-16, a calendar tweak): easier to create on the wall, and more recognisable on the week view and Home (for example one bar across the days rather than a chip per day). Builds on "Adding events, later" under Plan 2b-2 (the Until… chip). Folded into Plans 9b and 9c (the user, 2026-10-20; spec 9 §2.1).
- Seasonal themes (the user, 2026-10-19): its own spec; no detail yet. Doorbell ring tones already note switching for Christmas and Halloween.
- Sonos room grouping (the user, 2026-10-19): group and ungroup Sonos rooms from the wall (Plan 6 left grouping in the Sonos app). Its own spec; no detail yet.
- The October 2026 redesign's speculative designs (the user, 2026-10-19), in `docs/design/2026-10-redesign/handoff.md` and `notes.txt`, to use when each feature is built: the Shopping list card (note 12); the One-tap tray and Settings › Home buttons (notes 13, 17) with Party mode, house Holiday mode (lights, heating, door chime), Start hoover and sound pads; the family slideshow and idle screen (note 14); multi-day events as bars across the week and "Day 2 of 3" on Home (note 31); Door's Clear history (deleting stored pictures too, needs an Admin or Adult PIN) with a 30-day auto-clear, the latest-picture panel, Mark all seen and See full size (notes 30, 32); Music's Pause everywhere. Open decisions: the slideshow's place when every card shows, the night clock's grey and size, the placeholder sounds and actions.
- A home hub on the NAS (the user, 2026-10-15): running one (for example Home Assistant) is possible if it helps. It could bridge Sonos, Hue, Arlo, the thermostat and smart plugs, with Culvery's providers talking to the hub instead of each device. Weigh it in each service's brainstorm; not needed for Sonos (Plan 6 talks to the speakers directly).
- **Sonos playlists (2026-10-21):** show the household's Sonos playlists on the wall, either inside Favourites or behind their own button on the Music tab.
