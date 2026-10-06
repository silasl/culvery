# Plan 1 follow-ups

Items raised while Plan 1 (foundation) was built and reviewed. They were deferred on purpose and are for the plan that first touches the area.

## From Plan 1 (deferred)

**For Plan 4d (design, UX and accessibility)**
- `HhIcon` clears semantics and takes no content description. Icon-only buttons need an accessible label. *(Partly done: `HhIcon` now takes a `contentDescription`. Still unlabelled: the week view's add hints and the weather condition, 4d-2 D26.)*

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

**For Plan 4d (design, UX and accessibility)**
- Clickable event rows and week chips have no `Role.Button`. *(→ 4d-2 D15.)*

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

**For Plan 4d (design, UX and accessibility)**
- Disabled Who chips lack disabled semantics. *(→ 4d-2 D23.)*
- `AddEventButton` lacks an `onClickLabel`. *(→ 4d-2 D15.)*

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
- Settings page list items have no `Role.Tab` (accessibility), alongside the existing accessibility items. *(→ 4d-2 D16.)*
- The choose-a-PIN pad (and a sheet's Save) says "That PIN is taken — choose another." when someone else has the PIN, so an Admin can learn that a PIN is in use. Accepted: only an Admin (with a fresh PIN) gets there, and it follows from PINs being unique, since a PIN identifies its person. *(Kept as accepted, 4d triage X3.)*

## From Plan 4b (deferred)

**For Plan 4e (the device)**
- The weather on the SM-T510: the header and the Forecast card at its density, and the fetch over a whole day on the wall.
- On a cold start the clock, the date and the theme show the device's zone for a moment, until Room answers with the household's (`ShellViewModel.now` starts from `LocalDateTime.now()`). Accepted in 4b; check on the SM-T510 whether it shows.

**For Plan 4d (design, UX and accessibility)**
- All 4b layout, colour and copy choices are provisional: the Forecast card's rows, its title in every state, the card's icons in sun amber like the header's, the TalkBack condition words, the header items' spacing and divider. *(→ walk-through W3, W7; TalkBack condition words kept, X17; header spacing W34.)*
- The Forecast card has a lot of empty space below its three rows. *(→ walk-through W3.)*
- The header weather item has no merged TalkBack description: the temperature and High/Low read as two separate items. *(→ 4d-2 D26.)*
- The Forecast row semantics test probes only the day label; also assert that "17°" is absent (count 0). *(→ 4d-2 D26.)*

**Later**
- Open-Meteo answers with one UTC offset for the whole forecast, so data fetched before a clock change is an hour out for the days after it until the next fetch; offline across a clock change, the header's hour and the theme's sunset are an hour out. Known and untested in 4b (ruling 2). Fix: ask with `timeformat=unixtime` and convert each time in the household's zone (dates from the daily rows' own instants).

## From Plan 4c (deferred)

**For Plan 4d (design, UX and accessibility)**
- All 4c layout and copy choices are provisional: the home-app prompt (under Done's title; above Exit kiosk), the 48 dp Move up / Move down buttons, the read-problem line in danger colour with Hide at its right, `bedtime` for a clear night and `mobile` for a phone, the splash with no icon until the launcher icon exists, and the Calendar tab's ‹ › and This week pill. *(→ walk-through W7, W8, W13, W21, W27, W31; the splash icon has landed, X4.)*
- Move up / Move down have spoken labels but no other accessibility review yet. *(→ 4d-2 D28.)*
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
- The two home-app toasts in `KioskLifecycle.kt` (`HOME_APP_NOT_SET`, `HOME_APP_SCREEN_MISSING`) name Settings › Apps › Default apps; on One UI it's "Choose default apps". Check the wording on the SM-T510. *(Also the how-to line D41 adds to the Kiosk page; 4d triage E5.)*
- An optional short API 30 emulator pass (no Google): the splash compat path, Choose home app, pinning, DM Sans weights. Offered, not done.

**GitHub and Google (owner actions, not code)**
- Make the repository public, enable Pages (`main`, `/docs`), set the OAuth Branding homepage and privacy URLs and the authorised domain, then publish the OAuth app to production (it's in Testing, so grants expire every 7 days).

**Later**
- Google Calendar's sync token now lives about six weeks; check quota and data use over a month on the wall (Appendix A's economy estimate assumed a nightly full read).

## From Plan 4c (code minors)

**For Plan 4d (design, UX and accessibility)**
- T14: time spent in the add/edit overlay counts as idle, so the Calendar tab's 2-minute return to this week can fire under it (`CardHosts.kt`, `WeekView.kt`). *(→ 4d-2 D30.)*
- T16: the gap above the read-problem danger line is uneven (`ReviewCalendars.kt`). *(→ walk-through W31.)*

**Tests and hardening**
- T1: `tools/measure-release.sh` uses `$LOCALAPPDATA` under `set -u`, so it stops on macOS or Linux when adb isn't on PATH; use `${LOCALAPPDATA:-}`.
- T2: the icon literal guard misses ternary and positional literals (`icon = if (…) "x" else "y"`, `PrimaryButton("Edit", "edit")`); scan for any snake_case literal that is a full-font ligature but not in `Icons` (`IconFontTest.kt`).
- T2: `NO_FILLED_FORM` can go stale; assert none of its entries has a filled form and all are in `Icons` (`IconFontTest.kt`).
- T3: `LogHygieneTest` doesn't check the tag argument, and its call regex is whitespace-sensitive (`build-logic/convention/src/test/kotlin/LogHygieneTest.kt`).
- T8: test that a K3 expiry keeps the failure count, and that K4 reads the store once (`LockoutStoreTest.kt`).
- T9: the `isRoleAvailable` half of Choose home app's check is untested (`MainActivity.kt` `openHomeAppScreen`).
- T14: `WeekViewHost`'s `onEveryTouch` wiring is untested at host level (`CardHosts.kt`).
- T14: `addedToast` calls `zone.current()` outside `onAppScope`'s try, unlike `master()` and `authorise` (`CalendarEditor.kt`).
- T15: a read-only NeedsSignIn whose reads recover while queued writes keep failing for other reasons stays NeedsSignIn, and the 48-hour ageing freezes until a reconnect (bounded: the chip shows) (`CalendarStore.kt` `markSynced`, `Writes.kt`).
- T15: `writeAccepted` sits in the same try as `applyAcceptedWrite`, so its own failure logs misleadingly (`CalendarSync.kt`, `CalendarStore.kt`); `ZONE_MARGIN_MS` is untested (`CalendarRepository.kt`); `markSynced`'s fold after Unreachable following NeedsSignIn lost its test (`CalendarStoreTest.kt`).
- T16: a calendar deleted after it was flagged unreadable leaves only at the daily refresh (`SourceRefresher.kt`); the master-gone toast's count includes other removed calendars' changes (`CalendarStore.kt`).
- T17: `move`'s Cancelled and failure paths are untested (`PeopleEditorTest.kt`).
- T18: the town search's fill after a save overwrites text typed while the save ran (`LocationPane.kt`); `StepsUiTest`'s `after()` masks a `setUp` failure (`StepsUiTest.kt`); `restartExpiry` picks its timeout from a setup flag read before the lock (not reachable today) (`DefaultAccessControl.kt`).
- Add `android:dataExtractionRules` (`res/xml/data_extraction_rules.xml`) excluding every domain from cloud backup and device transfer: `allowBackup="false"` doesn't stop device-to-device transfer on Android 12+ for targetSdk 31+ (`AndroidManifest.xml`; `docs/privacy.html` now says some tablets may copy the data). Verify on the SM-T510.
- `MainActivity.onResume` refreshes the home role after `super.onResume()` and relies on API 29+ sending ON_RESUME after it returns, so `KioskLifecycle.onResume` sees the new role; add a one-line comment, or refresh inside the `isHomeApp` read (`MainActivity.kt`).

## From the 4d triage (2026-10-06)

The 4d audits (copy, screens, hand-off, accessibility, live emulator) were triaged into D1–D41, W1–W34, E1–E10 and X1–X17. 4d-1 is done (`docs/superpowers/plans/2026-10-06-culvery-04d1-shared-parts-contrast.md`). 4d is split (user, 2026-10-06): **4d-1** "shared parts, contrast, layout bugs" takes D1–D7 and D9–D14 (`docs/superpowers/specs/2026-10-06-culvery-4d1-shared-parts-contrast-design.md`); D8 is not done (palette swaps are W30); the rest is below. Paths are short (`core/ui` Controls.kt, calendar ui/WeekView.kt and so on); line numbers are as of b7cb40d.

**For Plan 4d-2 (TalkBack, timeouts, words)**

Decision 1, vocabulary, **still to decide** before 4d-2's copy is written. Each term is a visible product word; the recommendation comes first, then any alternative.
- a. "Master" calendar → **"Main"** badge, button "Use for new events", "Choose another main calendar first". Alt: no noun, only "New events go here". (C79, C85)
- b. Kiosk → page **"Tablet lock"**, button and PIN line **"Use other apps"**. Alt: "Lock to Culvery" / "Leave Culvery". (C70, C74–C77)
- c. Sync → **"Updated … ago" / "Saving to Google Calendar…" / "will be lost"**; the week subtitle drops "Family calendar" and reads "Updated from Google Calendar 5 minutes ago". (C17, C18, C24, C39, C84, C85)
- d. Google → **"Google Calendar" in every visible string** (`connectionLabel` becomes the service's display name). (C20, C39, C47)
- e. Add and edit: Add stays "Add event" → sheet "Add event" → button "Add event" → toast "Event added". Change: button **"Change"** → sheet "Change event" → "Save changes" → "Changes saved" (the permissions and notes already say "change"). Alt: "Edit" everywhere, renaming the permissions. (C30, C35, C40, C60)
- f. Assign → **"Choose who it's for"** (also the permission and "Ask a grown-up to choose who this is for."). (C32, C68)
- g. Read-only, subscribed, feed → **"can't change here"**; also right for the not-the-main-calendar case, which today wrongly says "Read-only calendar". (C24, C28, C37, C38)
- h. Minor: "Free" → "Nothing on"; the "Week" chip → "See the week"; "Added from a phone" in both places; "Keep" alone everywhere. (C2, C4, C31/C36, C99)

TalkBack plumbing:
- D15 Clickables announce no role; labels say "Open" or repeat the visible text; the theme toggle reads a bare "Auto" (new `Modifier.hhClickable` in core/ui; every raw `clickable` in a11y A6): a helper with `Role.Button` by default; labels say what happens ("open event details", "Add event", "preview night theme"); drop labels equal to the name; a shared `assertClickablesHaveRoleAndLabel()` called from each screen's test.
- D16 The nav rail and Settings page list have no Tab role or selected state (NavRail.kt:67-84, SettingsScreen.kt:91-108): `selectable(selected, role = Role.Tab)` in `selectableGroup()`.
- D17 Overlays aren't modal: TalkBack can activate the rail behind Settings or a sheet; no pane titles; nameless focusable scrims (OverlayLayers.kt:61-85, SetupWiring.kt:67-68, PinPad.kt:173-183, Pickers.kt:93-108, sheet roots): `clearAndSetSemantics {}` on whatever is behind an overlay; scrims cleared; `paneTitle` on each overlay.
- D18 No async result is announced: toasts, the editor failure card, person/You/location messages, PIN errors, the Syncing pill (Shell.kt:600-615, EventEditorSheet.kt:357-376, PersonEditorSheet.kt:217, YouStep.kt:163, LocationPane.kt:124-136, PinPad.kt:217, EventDetailSheet.kt:179): `liveRegion` Polite; Assertive for refusals and errors via `ToastMessage.urgent`; `error()` on the field concerned.
- D19 No headings anywhere (titles, card titles, sheet and picker titles, week column headers): `heading()`; merge each week column header into "Today, Tuesday 6 October". (4d-1 adds the section-label headings.)

Names and states:
- D20 Text fields lose their name once filled (Controls.kt:211-254 `HhTextField` and callers): a `label` on the field's semantics; `error(message)` on a refusal.
- D21 Colour swatches have no name; "taken" shows only as a strike-through (Controls.kt:360-390, PersonPalette.kt): a named palette; `selectable(role = RadioButton)` in `selectableGroup`; stateDescription "Used by Sam".
- D22 Calendars review: the Show switch is unnamed; the master's switch is disabled with no reason; the person chip reads as plain text; Disconnect, Make master and Hide repeat with no subject (ReviewCalendars.kt:233-330, Controls.kt:405-427): row `toggleable(Role.Switch)` named "Show {calendar}"; a master stateDescription; chip `Role.Button` "Change who {calendar} is for" with expanded/collapsed; per-row labels name the calendar or account.
- D23 Choice chips have no role or group; disabled Who chips sound normal (Controls.kt:192-200, EventEditorSheet.kt:243-275): `selectable(Role.RadioButton)` + `selectableGroup`; disabled-but-tappable chips get stateDescription "Not available".
- D24 Pickers: date cells read "21"; all four steppers are "More"/"Less"; changes are silent (Pickers.kt:93-256): cell "Tuesday 7 October, today" with selected state; "Hour up/down", "Minute up/down"; liveRegion on the stepper values and the range text.
- D25 PIN dots are silent; the lockout must not tick every second (PinPad.kt:84-99, 260-275): dots "2 of 4 digits entered" as a liveRegion; the lockout announced once.
- D26 Week and Home semantics: chips lack whose and which day; seven unlabelled add hints; the legend; detail rows split label from value; the weather header is two items with no condition (WeekView.kt:224-284, ComingUpCard.kt:97-113, EventDetailSheet.kt:224-235, WeatherHeaderItem.kt:21-34, ForecastCardTest.kt:62): `clearAndSetSemantics` descriptions ("Swimming, Sam, Tuesday 09:00", "Add event on Tue 7 October", "Now 17 degrees, partly cloudy. High 19, low 11"); merge InfoRow and the legend; tests assert "°" count 0 in forecast rows and the header.
- D27 Wizard dots are nameless "selected" nodes, and the total grows from 6 to 7 mid-flow (SetupWizard.kt:198-216): "Step n of m"; count Review from the start.
- D28 Move up/down: focus follows the position, not the person; the end of the list drops focus; no row actions (PeopleUi.kt:41-108): `key(person.id)`; a disabled button with stateDescription at the ends; `customActions` on the row.

Timeouts:
- D29 Settings closes 2 min after the last *pointer* touch, losing an open sheet's edits; TalkBack activity never counts; no warning (AccessControl.kt:10,13; DefaultAccessControl.kt:150-157; SetupWiring.kt:43-62; OverlayLayers.kt:54-58; MainActivity): `calculateRecommendedTimeoutMillis`; accessibility focus, click and scroll events count as touches; a 20 s live-region warning.
- D30 The week view snaps back under an open add/edit sheet and under TalkBack (WeekView.kt:330-373, CardHosts.kt:58; 4c code minor T14 and its untested host wiring): overlay touches count as activity in `WeekViewHost`; no snap-back while touch exploration is on; the recommended timeout; liveRegion on the title.
- D31 Toasts last a fixed 3.5 s, including errors and 100–140-character how-tos (OverlayLayers.kt:93-99, ShellTokens): the recommended timeout; about a 6 s base for errors and refusals; scale with length, capped at 8 s.
- D32 A stray tap leaves a sheet over the wall display indefinitely; no shell overlay closes by itself, only Settings closing dismisses them (ShellOverlays.kt, SetupWiring.kt:62, the editor and detail hosts). **Ruled 2026-10-06:** any overlay with no edits closes after 2 minutes idle; the full-column add target stays (W28). Test with a fake clock: an untouched overlay is dismissed and an edited one isn't.

Words (after Decision 1; curly quotes and apostrophes everywhere, ruled 2026-10-06):
- D33 Apply the Decision 1 glossary and curly quotes (SetupCopy.kt, CalendarUi.kt, ReviewCalendars.kt, EventDetailSheet.kt, EventEditorSheet.kt, CalendarEditor.kt, CalendarPermissions.kt, Permissions.kt, AccessControl.kt): map each PIN reason to explicit copy instead of the enum name (C60); `connectionLabel` = the display name; a source-scan test that fails on master/kiosk/sync/read-only/assign/feed or a straight quote in UI copy.
- D34 Errors don't give the cause or the fix; a delete failure says "Couldn't save"; `EVENT_GONE` and the fake provider's reasons are capitalised mid-sentence; the failure card says "check the connection" for refusals (Writes.kt:33,66,128-132; CalendarEditor.kt:36,46,133; EventEditorSheet.kt:61; ReviewCalendars.kt:57-77; plugin/Setup.kt:11; CalendarSetup.kt:25; PinPromptController.kt:12; FakeCalendarProvider.kt): per-cause copy (network vs refused); `COULD_NOT_DELETE`; reasons in lower case; "Something went wrong" → "Stopped updating — disconnect and connect again".
- D35 Abbreviations and end punctuation (WeatherWords.kt:33-35, CalendarUi.kt:100-112, EventForm.kt:45-47, PinPad.kt:95-111, AccessControl.kt:26): minutes, hours and seconds in full; a full stop on every sentence; "from 22:00" for TalkBack.
- D36 Copy that's wrong about who can act: roles promise abilities without a PIN; the button still says "Set your PIN" after one is set; the first person isn't told they're the Admin (SetupCopy.kt:17,72-74, PeopleUi.kt:80, YouStep.kt:148-163): "Without a PIN, Sam can only look." (row: "Adult · No PIN — can't add events"); "Change PIN" once set; the You step line.
- D37 The stale-sync subtitle is red alone (WeekView.kt:125, CalendarUi.kt:100-121): an icon or the words "Out of date", with D33's copy; plan step 1 reads the emulator's `connection` row (health, healthMessage) to confirm the cause. Investigated in the triage: expected on the emulator, where a pass with any failing calendar or unreachable Play services never stamps `lastSyncMillis` (CalendarSync.kt:303-309); see E10.
- D38 Dead-end buttons: Forecast's "Open settings" opens Settings at whatever page; the Connect card's fallback leads nowhere (ShellNavigator.kt:11, ShellViewModel.kt:157, ForecastCard.kt:72, ConnectCalendarCard.kt:51): `openSettings(page)`; "Add home location"; hide the fallback and say "No calendar services are available on this tablet."
- D39 Smaller copy and empty states (SetupCopy.kt, TodayCard.kt:61, ComingUpCard.kt:91, WeatherWords.kt:11, EventForm.kt:180, CalendarUi.kt:148, SourceRefresher.kt:23-26, CalendarConnectHost.kt:29, CalendarSetupSteps.kt:38): as the copy audit proposed (C1, C3, C10, C46, C50, C59, C75, C85, C87e, C90, C94, C98, S29); Settings › Calendars when not connected: "Connect your family's Google Calendar to see it on Home."; the Connect line "Choose the family's Google account" comes from the provider descriptor.
- D40 Location search: nothing on screen while searching; a failed search has no retry; the baseline shows a state the app never reaches (LocationPane.kt:85-95,124-136; SetupScreenshotTest.location()): `TownResults.Searching` "Searching…"; a plain "Try again" that re-runs the query; the fixture renders the real forward state.
- D41 Exit kiosk gives no feedback; the home-app how-tos are 100–140-character toasts (KioskLifecycle.kt:42-48,133-137, KioskPage.kt): a toast on exit; a short home-app toast with the how-to as a `mute` line on the Kiosk page (wording per Decision 1b; One UI's menu name per E5).

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

**For Plan 4d-2 (TalkBack, timeouts, words)**
- The Forecast row clears its children's semantics, so its day label and icon are checked only by baselines (the temperatures have `TemperatureFontScaleTest`). D26 rewrites those semantics (`ForecastCard.kt`).
- `WeatherRepositoryTest` feeds the failures in directly; that the `@Inject` constructor passes `WeatherSyncLoop.lastFailure` is checked only on the emulator (walk-through item 7).
- `HhCloseButton`, the week's ‹ ›, the date picker's ‹ › and the time steppers are `surf2` on `bg` or `surf` with no role of their own; on light `bg` the close button's fill is 1.04:1 (its `ink` icon carries it). Give them `HhButton`'s quiet fill and `Role.Button` in D15's pass (`Shell.kt`, `Pickers.kt`).
- The week header's reconnect chip stays its own composable (`dangerSoft`; no `HhButton` tone fits). It has `Role.Button`; its label is D15's.
- At font scale 1.5 the week view's Today column clips its last chip; the column doesn't grow. Let the column grow with its chips, or settle it at the design walk-through.

**For the end-of-v1 design walk-through (the user's own)**
- Calendars review: a calendar's Master line, Make master and read problem now sit under its top line (`HhListRow`'s `below`); People's move arrows and chevron are 12 dp apart (W31, W27).
- `HhButton` and `HhChoiceChip` grow with their text at large font scales but have no vertical padding of their own; a two-line chip at 1.5 runs close to its edges.
- Light `danger` on `dangerSoft` is 4.497:1: fine for the icons drawn there today; `danger` text on `dangerSoft` would need a darker `danger` (`ContrastTest` pins it).
- The PIN pad now keeps a blank two-line gap between the dots and the keypad when there's no message, so the keypad doesn't move when a message appears (spec §6.5 trade-off).

**For Plan 4e (the device)**
- The status bar's Sign out and theme toggle rely on Compose's 48 dp touch-target expansion rather than 48 dp of layout (4d-1 ruling 8); check on the SM-T510 that a touch just outside them lands.
- `HhCloseButton` is `surf2` on `bg` (1.04:1) in light; check on the SM-T510 that it reads as a button (see 4d-2 above).

## Feature ideas (2026-10-02, after v1)

**Each its own spec → plan cycle; not in 4c, 4d or 4e**
- Sleep and wake: dim the screen when nobody is there and wake on a tap. Start with a schedule or sunset (4b's sun times) and the ambient light sensor; camera presence (CameraX at about one frame a second, on-device motion or face detection, nothing stored or sent, and the app says when the camera is on, as Android 11 shows no indicator) is optional on top. Measure its power use against plain dimming on the SM-T510. v1 §14 had screensaver and night dimming out of scope.
- Bin collections from Mid Sussex District Council: no iCal feed, only a per-property tracker page (`mop.php?Track=…`). A read-only scraper provider fetching daily, mapped to Family, with "can't read" shown on the calendar's row (4c's per-calendar health), since the page can change without notice. The `Track` value identifies the property: store it as connection config and keep it out of logs. Check UKBinCollectionData's Mid Sussex scraper as a reference. Stopgap: repeating events in the family's Google calendar.
