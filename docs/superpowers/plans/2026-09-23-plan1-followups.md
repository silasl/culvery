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
- Branding inside the app (the user, 2026-10-13, 4e): the Culvery name or mark shows only on the splash; place it somewhere on Home or the rail.
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

## Feature ideas (2026-10-02, after v1)

**Each its own spec → plan cycle; not in 4c, 4d or 4e**
- Sleep and wake: dim the screen when nobody is there and wake on a tap. Start with a schedule or sunset (4b's sun times) and the ambient light sensor; camera presence (CameraX at about one frame a second, on-device motion or face detection, nothing stored or sent, and the app says when the camera is on, as Android 11 shows no indicator) is optional on top. Measure its power use against plain dimming on the SM-T510. v1 §14 had screensaver and night dimming out of scope.
- Bin collections from Mid Sussex District Council: no iCal feed, only a per-property tracker page (`mop.php?Track=…`). A read-only scraper provider fetching daily, mapped to Family, with "can't read" shown on the calendar's row (4c's per-calendar health), since the page can change without notice. The `Track` value identifies the property: store it as connection config and keep it out of logs. Check UKBinCollectionData's Mid Sussex scraper as a reference. Stopgap: repeating events in the family's Google calendar.
- Ring doorbell (2026-10-13): show "someone’s at the door" over the calendar. Ring has no official third-party API; the likely route is the Ring app on the tablet with Culvery reading its notifications (notification access, no Ring credentials in Culvery), versus an unofficial Ring login (fragile, more credentials on the wall). Its own spec; the tablet is shared, so the Ring app’s sign-in is the household’s call.
