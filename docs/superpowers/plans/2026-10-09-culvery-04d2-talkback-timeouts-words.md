# Culvery — Plan 4d-2: TalkBack, timeouts and words Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks marked **Review: opus** get an opus reviewer.

**Goal:** Every control in Culvery says what it is and what it does under TalkBack, overlays are modal and announce themselves, every timer counts TalkBack activity and warns before it loses typing, and the words are plain, consistent and curly-quoted.

**Architecture:** One `Modifier.hhClickable` and one shared semantics helper (`assertClickablesHaveRoleAndLabel`, in `:core:ui`'s test fixtures) carry the TalkBack basics into every screen, task by task, each task calling the helper from the screens it finishes. One `UserActivity` signal in `:core:plugin`, fed by the shell's pointer input and an `AccessibilityDelegate` on the content view, drives every timer: the Settings session (with a 10-minute edit hold and a 20-second warning), the overlay and PIN pad idle close, the week view's return to this week and toast durations. The words change last, behind a `CopyScanTest` that fails on system words and straight quotes in any copy literal, so no later change can bring them back.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00: ui/foundation 1.9.1, material3 1.3.2), Hilt 2.57.1, Coroutines 1.10.2, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1, AGP 8.13.0 (test fixtures). One possible new test dependency, BOM-managed: `androidx.compose.ui:ui-test-junit4-accessibility` (Task 1 decides).

**Spec:** `docs/superpowers/specs/2026-10-08-culvery-4d2-talkback-timeouts-words-design.md` (approved 2026-10-08; amended at planning 2026-10-09: a sheet with unsaved changes outside Settings gets the 20-second warning, Decision 4 and §6.3). Appendix A is the copy table, Appendix B the accessibility mapping. Parent spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md`.
**Previous plan (format, lessons):** `docs/superpowers/plans/2026-10-06-culvery-04d1-shared-parts-contrast.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Task 17 updates it).

**Plan series:** 1 · 2a · 2b-1 · 2b-2 · 3a · 4a · 4b · 4c · 4d-1 (done) · **4d-2 TalkBack, timeouts, words (this plan)** · 4e the device · the end-of-v1 design walk-through.

**Builds on:** `main` at eb40ec0 (the spec), whose code is 4d03211. Every file and line reference below was read at eb40ec0.

| # | Task | Size | Review |
|---|---|---|---|
| 1 | Groundwork: `enableAccessibilityChecks()` tried (expected: not adopted); `:core:ui` test fixtures with `assertClickablesHaveRoleAndLabel()`; `Modifier.hhClickable`; `HhButton.spokenName`; `HhListRow.clickLabel` (§4.1, §8.2) | M | **opus** (the shared API and the build change) |
| 2 | Roles and click labels on every clickable; the quiet fill and `Role.Button` for Close, ‹ › and the steppers (D15, 4d-1 follow-up) | L | sonnet |
| 3 | Tabs and headings (D16, D19) | M | sonnet |
| 4 | Modal overlays, silent scrims and pane titles (D17) | M | **opus** (hides nodes every later test relies on) |
| 5 | Live regions, always-present message lines, urgent toasts, field labels and errors (D18, D20) | L | sonnet |
| 6 | Swatches, choice chips and review rows (D21, D23, D22) | L | sonnet |
| 7 | Pickers, the PIN pad, wizard dots, Move up and Move down (D24, D25, D27, D28) | L | sonnet |
| 8 | Week and Home semantics; the Forecast row test (D26) | M | sonnet |
| 9 | `UserActivity`, the TalkBack hook and the recommended timeout (capped at the edit hold); the week snap-back (T14); toast durations (§6.1, D30, D31) | L | **opus** (timers and Android plumbing) |
| 10 | The Settings session: the edit hold, wall-clock expiry, `expiresAt` and the Settings warning; a refused PIN names no one and counts (D29, C64 as amended) | L | **opus** (privileged-session timing, the lockout) |
| 11 | Overlays and the PIN pad left alone; the sheet warning; the edits that hold Settings (D32, the amendment) | L | **opus** (overlay timing over a privileged session) |
| 12 | The vocabulary and quotes sweep (D33) | L | sonnet |
| 13 | `CopyScanTest` (Decision 8) | M | sonnet |
| 14 | Errors by cause; full words and full stops (D34, D35) | L | **opus** (the write path's result type) |
| 15 | Settings opens on the named page; location search shows it is working and can be tried again (D38, D40) | M | sonnet |
| 16 | People and PINs, "Out of date", Tablet lock and the smaller copy (D36, D37, D39, D41) | L | sonnet |
| 17 | Wrap-up: README, follow-ups, every changed baseline, the TalkBack check on the emulator and the wording walk-through | M | sonnet + controller |

**Task order and why:** §9's fourteen tasks in §9's order, three of them split in two (the user, at plan review: §9's 10 into 10 and 11, its 11 into 12 and 13, its 13 into 15 and 16; each half ends green), with the deviations in rulings 1–3: the helper is called from each screen in the task that finishes that screen's clickables, a few strings move into Task 12 because the scan bans their words, and the `:core:plugin` test fixtures arrive with `UserActivity` in Task 9. Every task ends green, with its own baselines compared, re-recorded, checked against its list by script and looked at.

## Rulings against the code

Where the spec is silent, ambiguous, or doesn't fit the code at eb40ec0, this plan rules as follows.

1. **The shared helper is called in the task that finishes a screen (deviation from §9 task 2's "the helper called from each screen test").** A screen's chips, swatches or tabs are converted in Tasks 3, 6 and 7, and the helper fails on them before then. So: Task 2 calls it for the Home cards, the week, the detail sheet, both pickers, the PIN pad, the status bar and the people list; Task 3 for the rail and Settings; Task 6 for the editor, the person sheet, the You step and the review rows; Task 7 for the wizard frame. Every screen named in §4.1 calls it by the end of Task 7.
2. **Strings the scan bans move into Task 12 (deviation from §9 tasks 12–13).** `CopyScanTest` lands in Task 13 and must pass at once (§8.4), so Task 12 writes the final text of every literal it flags: `syncedLabel` becomes `updatedLabel` with full words (D35's "minutes", "hours"), the permission labels, `masterGone` (C85), the two home-app toasts (C76, C77) and the reconnect label (C20). Strings Tasks 14 and 16 rewrite anyway only get their curly apostrophe in Task 12.
3. **`:core:plugin` gets test fixtures in Task 9**, by whatever mechanism Task 1 found works for `:core:ui` (ruling 12). They hold `FakeUserActivity`, which `:core:access`, `:capability:calendar` and `:app` tests use.
4. **`OverlayHost.show(closesWhenIdle: Boolean = true, content: @Composable () -> Unit)` and `markEdited(edited)`** (spec §6.3 has `content` first and `setEdited`). With `content` first, every `show { … }` caller stops compiling: a trailing lambda must be the last parameter. `setEdited` clashes on the JVM with the setter of an `edited` property (`setEdited(Z)V`), which `OverlayState` and the fakes have.
5. **`DefaultAccessControl` keeps one `@Inject` constructor and takes `userActivity: UserActivity` as its last parameter** (§6.2 asked for a `timeout` function so `:core:access` stays free of Android; `UserActivity` is an interface in `:core:plugin`, which `:core:access` already uses, so that holds). The five tests that build the class with named arguments (`DefaultAccessControlTest`, setup `TestHousehold`, calendar `TestAccess`, app `SampleAddTest`, `SampleWizardTest`) pass a `FakeUserActivity()` (plan review S1).
6. **`UserActivity` reaches the app's own composables as a parameter** (`ShellLayers`, `ToastLayer`, `PinPadHost`); `LocalUserActivity` (no default: it fails loudly, as `LocalOverlayHost` does) is for capability code, which can't see `:app` (the week view). Fewer tests then need the local.
7. **Scrims detect taps instead of being `clickable`.** A `pointerInput { detectTapGestures { … } }` has no semantics, so a scrim never reaches TalkBack (§4.3 "silent scrims") and keeps its pointer behaviour. `clearAndSetSemantics {}` can't be used on the PIN pad's scrim: the card is its child and would vanish with it.
8. **Messages that tick or that announce a count are spoken from a node of their own size, not a zero-size one.** Compose leaves nodes with empty bounds out of the accessibility tree in some versions, so: the PIN pad's message line sits in a `pin_message` box that carries the live region and a fixed description (the lockout read once, from its first value), and the visible line inside it is `hideFromAccessibility()` (stable in Compose 1.9.1; `invisibleToUser` is deprecated). The visible `pin_error` text keeps its tag and text layout, so 4d-1's font-scale tests still read it. The location pane's count ("3 towns found") is the description of its always-present status line (ruling 23).
9. **A spoken name with a subject keeps the visible text first, then a comma.** §5.3's "Use School terms for new events" and "Hide School terms" don't start with their visible text, which §4.1 requires and the helper checks. They become "Use for new events, School terms" and "Hide this calendar, School terms"; Reconnect and Disconnect read "Reconnect Google Calendar, alex@example.com" as §5.3 has them. Each is built from the button's own label constant, so Task 12's renames carry through.
10. **`quietFill` becomes public** (it is `internal` in `:core:ui`): the calendar's round buttons and steppers draw it (§4.1's 4d-1 follow-up).
11. **The PIN chooser's pane title is its stage title**: "Choose a 4-digit PIN", then "Enter it again" (§4.3 lists only the first). TalkBack announces a new pane when the stage changes; reading the old title there would contradict the screen.
12. **Test fixtures: `android { testFixtures { enable = true } }` with `android.experimental.enableTestFixturesKotlinSupport=true`** (§8.2). If Task 1 finds they don't build with the Compose convention plugin, it creates `:core:ui-testing` (Task 9 then puts `FakeUserActivity` there too): an Android library module under `core/`, which every module may depend on (`ModuleBoundaries`: core → core, capability → core, provider → core). Task 1 records which in this ruling before committing. **Fixtures work** (Task 1, 2026-10-09): AGP builds `:core:ui`'s test fixtures with the Compose convention plugin and the experimental flag.
13. **`enableAccessibilityChecks()` is adopted only if Task 1's probe shows it running and finding a real fault under Robolectric** (a 20 dp unlabelled clickable), and the three sample screens give no finding the plan can't fix or name. Then `assertAccessible()` (the fixtures' one entry point for screen tests) runs it too. Otherwise the dependency comes out again, and the helper and the emulator check are the guard. **Not adopted** (Task 1, 2026-10-09): the artifact resolved, but under Robolectric ATF reported nothing for a 20 dp unlabelled clickable (`expected: AccessibilityViewCheckException but was : null`); the dependency and probe are removed.
14. **The person sheet hides itself behind its own PIN chooser,** as the editor hides itself behind a picker (§4.3 names only the editor; the rule is "whatever an overlay covers").
15. **Copy-scan code contexts, extended (§7.1.3).** A literal's context is the calls it sits in, read back from the literal up to four levels, whatever line they start on (a multi-line `Log.w(` call is still a log). Besides the spec's list: `Regex(` or `toRegex(` (`DefaultMapping.namesPerson`'s `['’]s`), any `…Exception(` other than `WriteRejectedException` (the spec says "throws"; `Model.kt`'s `ColourInUseException` passes its message to `Exception(…)` in its class header and `OpenMeteoForecast` returns one), and Kotlin's `error(…)` only, never Compose's `semantics { error(…) }`, which TalkBack speaks. Words are matched with -s, -ed and -ing; literals inside `${…}` are read; a one-word identifier skips the quote checks but not the word check (real ids go in the allowlist). Modules named `*-testkit` are skipped: `:capability:calendar-testkit`'s `src/main` is a contract-test library whose assertion messages never reach a screen.
16. **`EventForm.lockedDatesLabel` becomes `lockedDatesLabel(service: String): String?`** (§7.9 C46 says "`EventForm` takes the service name"). A constructor parameter would touch every one of the ~40 `EventForm(…)` calls in the tests; the editor's host already holds the service (`repo.masterService`, `Opened.label`) and passes it to `EventEditorSheet`'s new `service` parameter.
17. **`SyncStatusUi.needsSignIn` and `connectionLabels` carry service names (`serviceNames`)** (Decision 12: visible copy never shows `Connection.label`).
18. **Existing baselines stand for two of §8.5's "new" ones:** `settings_kiosk_dark/light` become the Tablet lock page with its how-to line (names kept: renaming baselines hides their diff), and `week_stale_dark` is the stale subtitle (a `week_stale_light` is added). New: `location_searching_dark`, `settings_warning_dark`, `sheet_warning_dark`, `settings_calendars_empty_dark`, `week_stale_light`.
19. **The walk-through installs a signed release with `adb install -r`** (the user's instruction; §8.6 says the debug build). A release pins the screen, so leaving Culvery during the walk-through is Settings › Tablet lock › Use other apps with the user's PIN. A release can't be installed over a debug build: if `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and ask (uninstalling deletes the household's data).
20. **The sheet warning (spec amendment, user 2026-10-09):** "This closes in 20 seconds. Touch the screen to keep your changes." for an edited sheet outside Settings, 20 s before its 10-minute close; same pill, same rules as Settings' warning; while Settings is open only Settings' warning shows.
21. **Week chips, Today and Coming up rows, and the header weather merge their children and carry a description, rather than clearing them (§5.6 says `clearAndSetSemantics`).** Clearing removes the text nodes that 4d-1's font-scale tests measure (`FontScaleTest.atFontScale15ALongNameIsCutBeforeTheTimeAndTheBadgeStays`, `ShellLayoutTest.atFontScale13HeaderItemsKeepClearOfTheClockAndDate`). TalkBack reads a node's content description in place of its text, so it hears one item with the spoken words either way; the badge icons lose their own descriptions (their words are in the row's) and gain test tags (`badge_Repeats`, …). The header's "°" check becomes "exactly one node holds a '°': the merged header". The legend, the week's column headers and the Forecast rows still clear (nothing measures them). The emulator check (Task 17) listens for a row read twice.

22. **A sheet's first change counts as a touch for the Settings session, and so does its last** (Task 11). Typing goes to the keyboard's window, so no pointer event reaches Culvery's; without this a person typing a name for two minutes after their last tap would lose it. It restarts the session with the edit hold, no longer. When the edits go (undone, or the sheet closed by Close, Cancel or the scrim), the session is restarted at once without the hold, so an unattended Settings never keeps 10 minutes it no longer needs (plan review P1).
23. **In-page messages are always-present lines; the overlays' are not** (plan review T4). Compose speaks a live region only when a node it already has changes, and leaves out a node with no size, so `person_message`, `you_message` and the location pane's one `location_status` line are always composed and one line tall while empty (`HhMessageLine`, Task 5): a look change the spec's §2 now lists. The toast, the two warnings, the editor's failure card and the Saving pill stay new nodes: an always-present node in their layers would cover the shell under it, which Compose then leaves out of TalkBack's tree. The emulator check (Task 17 A.4 path 7) listens for each.
24. **The session ends by the wall clock, and keeps its hold through an authorise** (plan review P2, P6; Task 10). The expiry's `delay` stops while the tablet sleeps; a touch, an authorise, `endSetupSession` and `checkExpiry()` (start and resume) first end a session whose wall-clock `expiresAt` has passed. `restartExpiry` itself doesn't check, so `authorise` never returns a session it has just ended. The last touch's `holdForEdits` is remembered until a touch without it or sign-out.
25. **No overlay closes by itself inside Settings, or while it is saving; the pad over Settings waits a second longer** (plan review P4, P7; Task 11). An overlay or pad closing first would move TalkBack's focus, which counts as a touch and would stretch the session; a sheet's save in flight runs on its own scope and would be cut off.
26. **A refused real PIN names no one and counts** (plan review P8, user decision A; Task 10): "That PIN can’t do this. Ask an Admin.", or "… Ask a grown-up." when Adults hold the permission; `lockout.recordFailure` as for a wrong PIN, and still never a reset. The `Refusal.Toast` path counts too; its toast words (the calendar's refusals) are the caller's and still name the person, as today.
27. **The walk-through keeps the household out of the logs and the transcript** (plan review P3, P9; Task 17): speech capture paused for every PIN, a throwaway person (with the user's yes) for the PIN pad path, placeholders for names and titles, a grep before the commit, and the logs and screenshots deleted afterwards; TalkBack's settings and log level restored to what they were.
28. **The field name is checked on the emulator before Task 5 writes it** (plan review T5): a content description on an editable node can hide the typed value from TalkBack. Task 5 Step 0 records which mechanism is used.

Nothing in this plan needs a deprecated API. Flagged and avoided: `invisibleToUser()` (use `hideFromAccessibility()`), `AccessibilityEvent.obtain()` (use the `AccessibilityEvent(int)` constructor, API 30, tests only), `assertTouchHeightIsAtLeast` as a check of anything (4d-1 ruling 7), `captureToImage` under `GraphicsMode.NATIVE` (draw the root view to a `Bitmap`, as `WizardDotsTest` does).

## Global Constraints

- Package root `uk.co.siland.culvery`. Module rules unchanged (`build-logic`'s `ModuleBoundaries`). `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Pinned versions as in `gradle/libs.versions.toml`. **No new dependency** except, if Task 1 adopts it, `androidx.compose.ui:ui-test-junit4-accessibility` from the Compose BOM (test only). Never change a version.
- **Deprecated APIs:** use none without asking. If the compiler shows a deprecation warning on a line you wrote, **stop and ask**.
- **The child-in-the-house rule.** No change weakens a PIN gate, a confirm step or an `enabled` guard. A disabled control that §5 keeps tappable (a disabled Who chip) still runs only its explaining click. No timer keeps a privileged session open longer than: 2 minutes after the last touch (`SESSION_TIMEOUT_MS`), 10 minutes with unsaved edits in an open sheet (`EDIT_HOLD_MS`), 10 minutes in the wizard (`SETUP_IDLE_MS`), or what Android's "Time to take action" asks for when TalkBack is on, if longer, but never past `EDIT_HOLD_MS`. Nothing holds a session open without a touch: not a timer's own close moving TalkBack's focus (no overlay closes by itself inside Settings), and not the tablet sleeping (a session whose wall-clock end has passed ends at the next touch, authorise, start or resume). A real PIN that may not do the thing names no one and counts as a wrong attempt.
- **Timeouts, exactly (Decisions 3–6):**
  - `SESSION_TIMEOUT_MS = 120_000L` (2 min), `SETUP_IDLE_MS = 600_000L` (10 min), `EDIT_HOLD_MS = 600_000L` (10 min), `OVERLAY_IDLE_MS = 120_000L` (2 min), `CLOSING_WARNING_MS = 20_000L` (20 s), `BACK_TO_THIS_WEEK_MS = 120_000L` (2 min).
  - `UserActivity.timeout(base, hasControls)`: `base` unless touch exploration is on; then `min(max(base, getRecommendedTimeoutMillis(base, FLAG_CONTENT_TEXT or FLAG_CONTENT_ICONS or (FLAG_CONTENT_CONTROLS if hasControls))), max(base, EDIT_HOLD_MS))`. Never less than `base`, never more than the edit hold unless `base` is.
  - Toasts: `toastMillis(message, urgent) = minOf(base + 50 * (message.length - 40).coerceAtLeast(0), 8_000)`, `base` 4 s plain, 6 s urgent; waited through `timeout(…, hasControls = false)`. `TOAST_MILLIS` goes.
  - Settings: every touch restarts 2 min, or 10 min (`touch(holdForEdits = true)`) while the open sheet has edits; no longer hold. The hold is kept through an authorise on the session and dropped by the next touch without it; the shell sends one whenever the sheet's edits go (undone, closed). `expiresAt` is wall-clock time, checked before a touch or an authorise acts and on start and resume. The warning shows `CLOSING_WARNING_MS` before `expiresAt`, once per countdown, hidden by a touch.
  - Overlays: no edits, closed after `timeout(OVERLAY_IDLE_MS)` idle; edits (or a save in flight), after `timeout(EDIT_HOLD_MS)`; the connecting card never (`closesWhenIdle = false`); none inside Settings, whose own end takes them. The PIN pad cancels after `timeout(OVERLAY_IDLE_MS)` without a touch, a second later over Settings; digits are not edits.
  - The week view: no snap-back while `exploring`; otherwise after `timeout(BACK_TO_THIS_WEEK_MS)` without a touch anywhere (sheets included).
- **Live regions (Decision 10):** Assertive only on urgent toasts, `settings_warning` and `sheet_warning`. Polite on plain toasts, `editor_failure`, `person_message`, `you_message`, `location_status` (searching, no match, a failed search or save, and the town count over a list), `pin_message`, `pin_dots`, `detail_syncing`, `week_title`, `date_range`, `hour_value`, `minute_value`. `error(…)` on the person sheet's and You step's name fields for a duplicate name, and on `pin_dots` while the dots are ringed in `danger`. Compose speaks a live region only when a node it already has changes, so an in-page message is an always-present line (`HhMessageLine`, one line tall while empty); the toast, the warnings, the failure card and the Saving pill appear new, and the emulator check listens for them (Task 17).
- **Roles and labels (§4.1, Appendix A.2), verbatim:** click labels are lower case and say what happens: "open event details", "preview the night theme", "preview the day theme", "go back to the automatic theme", "change Sam’s details", "use as home", "show as Sam’s event", "reconnect", "find out why", "change who School terms is for". Names: "Add event", "Close", "Sign out", "Theme: Auto" / "Theme: Night" / "Theme: Day", "Earlier week", "Later week", "Earlier weeks", "Later weeks", "Move Sam up", "Move Sam down", "Delete last digit", "Cancel", "Hour up", "Hour down", "Minute up", "Minute down", "Settings". No click label equals its node's name. Pane titles: "Settings", "Event details", "Add event" / "Change event", "Add person" or the person's name, "Pick a date", "Pick a time", "Enter your PIN", "Choose a 4-digit PIN" (then "Enter it again", ruling 11), "Connecting to Google Calendar". Field names: "Event name", "Name", "Your name", "Town or city". Swatch names: Green, Blue, Pink, Violet, Lime, Red, Slate, Brown; Family "Amber"; taken state "used by Sam". Wizard dots "Step 3 of 7". PIN dots "2 of 4 digits entered".
- **Copy:** Appendix A's "New" column, verbatim, in the task that owns the row (ruling 2). Curly quotes and apostrophes (’ “ ”) in every string written or edited; "—", "›", "·", "…" as written there. Errors, toasts, body sentences and instructions end with a full stop; titles, labels, buttons, chips, badges and status lines don't (Decision 11). The service is "Google Calendar" in visible copy, from `serviceNameOf`, never `Connection.label` (Decision 12).
- **The two warnings, verbatim:** `settings_warning` "Settings closes in 20 seconds. Touch the screen to stay."; `sheet_warning` "This closes in 20 seconds. Touch the screen to keep your changes." Both a pill in the toast position, drawn over toasts, the `schedule` icon, Assertive, fixed text.
- **Layout numbers are named, never inline**, in the module's tokens or dimens object, as 4d-1 ruled. Tests may use literal dp. No look change beyond §2's list: the quiet fill on Close, ‹ › and the steppers; the disabled Move arrows; the warning pills; the "Searching…" and "Try again" lines; the "Out of date" icon; the Tablet lock how-to line; and the in-page message lines keeping one line while empty (plan review T4; the spec's §2 says so as amended).
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test`, `check` or `build`. A task runs its module's tests while working; every task ends with the full gate before its commit.
- **Tests that can fail:** every new test fails before its change, and each step says why ("Fails today: …"); where that reason doesn't show what the test guards afterwards, a "Fails if" line names the mutation that would break it. A test that passes before its change is labelled **pin** in its step, with what it pins. Semantics tests read real properties (`Role`, `ContentDescription`, `StateDescription`, `PaneTitle`, `LiveRegion`, `Heading`, `Selected`, `Error`, `CustomActions`, `OnClick` labels, `ToggleableState`). Timer tests use a fake clock (`TestScope`'s scheduler, or the Compose test clock with `mainClock.autoAdvance = false` and `advanceTimeBy`) and never sleep; each checks 1 ms (or one frame) before its boundary and at it.
- **Text layout in tests:** each module that measures text keeps its test-only `TextLayouts.kt` (`SemanticsNodeInteraction.textLayout()`). `hasVisualOverflow` is not used to show a line is whole (it is false for any text narrower than its box); use `lineCount` and `isLineEllipsized`. Font scale comes from `WithFontScale`. Pixels come from drawing the root view into a `Bitmap` (as `WizardDotsTest`), never `captureToImage`.
- **Screenshots:** baselines in `<module>/src/test/screenshots/`. Before re-recording, run `./gradlew <module>:compareRoborazziDebug --tests "<class pattern>"`, open each `*_compare.png` under `<module>/build/outputs/roborazzi/`, and check each difference against the step's list. Then `./gradlew <module>:recordRoborazziDebug --tests "<class pattern>"`. **An image that changes and isn't on the step's list is a stop and report.** Open every new image (the Read tool shows it).
- **The baseline check** (every step that records): the step's must-change and new names go one per line in `$TMP/expected-baselines.txt`, its may-change names in `$TMP/may-change.txt` (empty if none), then:
```bash
git status --porcelain -- "*.png" | sed 's#.*/##; s#\.png$##' | sort -u > "$TMP/changed.txt"
sort -u "$TMP/expected-baselines.txt" "$TMP/may-change.txt" > "$TMP/allowed.txt"
echo "changed but not listed:"; comm -23 "$TMP/changed.txt" "$TMP/allowed.txt"
echo "listed but unchanged:"; comm -13 "$TMP/changed.txt" <(sort -u "$TMP/expected-baselines.txt")
```
Both lists must be empty; anything in either is a stop and report. Put the output in the report.
- **Comments say what the code can't**, one line by default. No comment names this plan's task numbers.
- **Write every file as UTF-8.** Use the Write/Edit tools, or `encoding="utf-8"` in Python.
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on the current branch; never push. **Never** `git checkout -- .` or `git restore .`; to undo your own change, restore only the files you changed, after `git diff --name-only`.

## Review Focus

The five inputs most likely to bite a household that the spec's own tests don't reach, each pinned by a test in its owning task:

1. **An urgent toast followed at once by a plain one** ("Couldn’t save — try again." then "Event added."). Expected: the plain toast replaces it, reads Polite, and lasts its own 4 s from when it appeared; the urgent toast's 6 s timer neither hides it early nor keeps it longer.
   - Task 9 `OverlayLayersTest.aPlainToastAfterAnUrgentOneKeepsItsOwnTimeAndPoliteness` (app).
2. **An edit undone, or an edited sheet closed** (a title typed, then deleted; a chip chosen, then the first one again; Close, Cancel or the scrim). Expected: the sheet is no longer edited, so it closes after 2 minutes, with no warning, and the Settings session goes back to 2 minutes at once.
   - Task 11 `EventFormTest.anEditChangedBackIsNotEdited` (calendar), `ShellLayersTimeoutTest.aSheetWhoseEditsAreUndoneClosesAtTwoMinutesWithoutAWarning`, `AppContentTest.closingAnEditedSheetPutsSettingsBackOnTwoMinutes` and `undoingTheEditsPutsSettingsBackOnTwoMinutes` (app); Task 10 `DefaultAccessControlTest.aTouchWithoutTheHoldGoesBackToTwoMinutes` (core/access).
3. **TalkBack switched on or off while a later week shows.** Expected: switched on, the week stays; switched off, the 2-minute wait starts again from that moment.
   - Task 9 `WeekViewTest.turningTouchExplorationOffStartsTheWaitAgain` (calendar).
4. **The PIN pad over an edited sheet outside Settings, left alone.** Expected: the pad cancels at 2 minutes (its `authorise` returns null); the sheet under it stays until its own 10 minutes; a tap on the pad counts as a touch for the sheet.
   - Task 11 `PinPadTest.anIdlePadCancelsAtTwoMinutesAndATouchKeepsIt` (core/access) and `ShellLayersTimeoutTest.anEditedSheetStaysWhileAPadOverItComesAndGoes` (app, a real tap on the pad).
5. **Try again on a failed town search, then the query edited before it answers.** Expected: only the newest query's answer shows; the retried search for the old text is cancelled.
   - Task 15 `LocationPaneTest.editingTheQueryDuringARetryShowsOnlyTheNewQuerysTowns` (core/setup).

The spec's own risks (§10), each pinned:
- Live-region noise: Decision 10 in Global Constraints; `LiveRegion` asserted per tag in Tasks 5, 7, 8, 10, 11; the lockout's spoken text fixed while it ticks (Task 7 `PinPadTest.theLockoutsSpokenTextStaysFixedWhileItsSecondsTick`); how often each is spoken, and whether a new node is spoken at all, is heard on the emulator (Task 17, A.4 paths 4 and 7).
- The timeout and the PIN pad: Task 11's pad-over-sheet and pad-over-Settings tests; the pad's cancel goes through `PinPromptController.cancel()` (Task 11 `PinPadTest.anIdlePadCancelsAtTwoMinutesAndATouchKeepsIt`); over Settings the pad waits a second longer than the session (`overSettingsThePadWaitsASecondLonger`), and no overlay closes by itself inside Settings (`insideSettingsASheetDoesNotCloseByItselfAndOnlySettingsWarns`).
- Screenshot churn: every task's compare step names its baselines and checks them by script; Task 17 lists every baseline changed since eb40ec0.
- Test churn: each task that changes a string updates the tests that find it by text; the full gate runs before each commit.
- Hidden backgrounds hide nodes tests use: Task 4 Step 5 lists the tests that open a cover and what each reads (none reads a covered node at eb40ec0).
- The accessibility hook: Task 9 `ShellUserActivityTest` (the delegate's filter); Compose sending the events is heard on the emulator (Task 17, A.4 path 6).
- Waiting outside the app: the connecting card's `closesWhenIdle = false` (Task 11 `ShellLayersTimeoutTest.theConnectingCardIsStillOpenAtThirtyMinutes` and `CalendarConnectHostTest`).
- `enableAccessibilityChecks()` under Robolectric: ruling 13, Task 1.

The plan review's state findings, each pinned: the session after the tablet sleeps (Task 10 `aTouchAfterTheWallClockPassedTheEndSignsOut`, `checkExpiry…`, `anAuthoriseAfterTheWallClockPassedTheEndAsksForAPin`); "Time to take action" capped at the edit hold (Task 9 `aTimeToTakeActionPastTheEditHoldIsCappedThere`); the hold kept through an authorise (Task 10 `anAuthoriseOnTheShortcutKeepsTheHold`); a save never cut off (Task 11: saving counts as edited); a refused PIN names no one and counts (Task 10 `aRefusedRealPinCountsTowardTheLockoutAndNamesNoOne`).

---

## File Structure

```
gradle/libs.versions.toml, gradle.properties                (Task 1: fixtures flag; the a11y checks library only if adopted)
core/ui/build.gradle.kts                                     (Task 1: testFixtures)
core/ui/src/main/java/uk/co/siland/culvery/core/ui/
  Clickable.kt (create, Task 1: hhClickable)   Buttons.kt (Task 1: spokenName; Task 2: quietFill public)
  Components.kt (Task 1: clickLabel)   Shell.kt (Task 2: close button; Task 5: toast live region; Task 9: toastMillis)
  Controls.kt (Task 5: field label/error, HhMessageLine; Task 6: chips, swatches, switch)   PersonPalette.kt (Task 6: names)
core/ui/src/testFixtures/java/uk/co/siland/culvery/core/ui/test/Accessibility.kt (create, Task 1)
core/ui/src/test/java/uk/co/siland/culvery/core/ui/  ClickableTest.kt, AccessibilityHelperTest.kt (Task 1),
  ControlsTest.kt (Tasks 5, 6), ToastMillisTest.kt (Task 9), CopyScan.kt, CopyScanTest.kt (Task 13)
core/ui/src/test/resources/copy-scan-allow.txt, copy-scan-fixture.kt.txt (create, Task 13; Task 15 moves an entry)
core/plugin/build.gradle.kts (Task 9: testFixtures)
core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/
  UserActivity.kt (create, Task 9)  Overlays.kt (Task 5: urgent; Task 11: show/markEdited)
  ShellNavigator.kt (Task 15: openSettings(page), SettingsPageIds)  Setup.kt (Task 12: COULD_NOT_SAVE)  Connections.kt (Task 16: connectHint)
core/plugin/src/testFixtures/java/uk/co/siland/culvery/core/plugin/test/FakeUserActivity.kt (create, Task 9)
core/access/…/AccessControl.kt, DefaultAccessControl.kt (Tasks 10, 12, 14), Permissions.kt (Task 12),
  PinPromptController.kt (Task 10), ui/PinPad.kt (Tasks 2, 3, 4, 5, 7, 11, 12, 14)
core/setup/…/SetupCopy.kt (Tasks 12, 14–16), SettingsScreen.kt (Tasks 3, 4, 15), PeopleUi.kt (Tasks 2, 7, 16),
  PersonEditorSheet.kt (Tasks 3–7, 11, 14, 16), LocationPane.kt (Tasks 2, 5, 15), SetupUi.kt (Task 3),
  SetupWizard.kt (Task 7), WizardRules.kt (Task 7: dotCount, dotIndex go), PeopleEditor.kt (Tasks 5, 14), steps/YouStep.kt (Tasks 5, 6, 16),
  pages/KioskPage.kt (Tasks 12, 15, 16)
capability/calendar/…/CalendarUi.kt (Tasks 6, 8, 12, 16), Writes.kt, CalendarEditor.kt, CalendarSync.kt (Tasks 5, 12, 14),
  CalendarPermissions.kt, CalendarReview.kt, SourceRefresher.kt, CalendarSetup.kt (Tasks 5, 12, 14, 16), EventForm.kt (Tasks 11, 14, 16),
  CalendarRepository.kt (Task 12), CalendarSetupSteps.kt (Tasks 15, 16)
capability/calendar/…/ui/ WeekView.kt, CardHosts.kt (Tasks 2, 3, 5, 8, 9, 12, 16), TodayCard.kt, ComingUpCard.kt (Tasks 2, 3, 8, 12, 16),
  EventDetailSheet.kt (Tasks 2–5, 8, 12), EventEditorSheet.kt, EventEditorHost.kt, Sheets.kt (Tasks 3–6, 11, 12, 14, 16), Pickers.kt (Tasks 2–4, 7),
  ReviewCalendars.kt (Tasks 3, 6, 12, 14, 16), ConnectCalendarCard.kt (Tasks 3, 12, 15), CalendarConnectHost.kt (Tasks 3, 4, 11, 12, 16), Badges.kt (Task 8)
capability/weather/…/WeatherWords.kt (Tasks 8, 12, 14–16), ui/ForecastCard.kt (Tasks 3, 8, 15), ui/WeatherHeaderItem.kt (Task 8)
app/…/MainActivity.kt (Tasks 4, 9, 10, 11, 15), SetupWiring.kt (Tasks 4, 9, 11), shell/ShellOverlays.kt (Tasks 5, 11),
  shell/ShellUserActivity.kt (create, Task 9), shell/ShellViewModel.kt (Tasks 15, 16), shell/ui/OverlayLayers.kt (Tasks 4, 5, 9, 10, 11),
  NavRail.kt (Tasks 2, 3), StatusBar.kt (Task 2), KioskLifecycle.kt (Task 12), di/AppModule.kt (Task 9)
provider/calendar-google/…/GoogleHttp.kt, GoogleConnectFlow.kt, GoogleCalendarProvider.kt (Tasks 5, 12, 14, 16)
provider/calendar-fake/…/FakeCalendarProvider.kt, SampleEvents.kt (Tasks 12, 14)
README.md, docs/setup/release.md, docs/superpowers/plans/2026-09-23-plan1-followups.md (Task 17); docs/superpowers/plans/2026-10-09-4d2-walkthrough.md (create, Task 17)
```

`…` stands for the module's package directory (`src/main/java/uk/co/siland/culvery/<module path>`); every step spells out the full path the first time it names a file.

---

### Task 1: Groundwork — the accessibility checks tried, the test fixtures, `hhClickable`, `spokenName`, `clickLabel` (§4.1, §8.2; rulings 12, 13)

**Review:** opus.

**Files:**
- Modify: `gradle/libs.versions.toml` (the `[libraries]` block, after `androidx-compose-ui-test-junit4`, line 47), `gradle.properties` (append one line), `core/ui/build.gradle.kts:3-14`, and the `dependencies` blocks of `core/access/build.gradle.kts`, `core/setup/build.gradle.kts`, `capability/calendar/build.gradle.kts`, `capability/weather/build.gradle.kts`, `app/build.gradle.kts`
- Create: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Clickable.kt`
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Buttons.kt:67-112` (`HhButton`), `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Components.kt:60-112` (`HhListRow`)
- Create (fixtures): `core/ui/src/testFixtures/java/uk/co/siland/culvery/core/ui/test/Accessibility.kt`
- Test (create): `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ClickableTest.kt`, `AccessibilityHelperTest.kt`, `AccessibilityChecksProbeTest.kt` (deleted again unless adopted)

**Interfaces:**
- Consumes: `HhButton`, `HhListRow`, `ButtonSurface`, `CulveryTheme` as they are at eb40ec0.
- Produces:
  - `fun Modifier.hhClickable(onClick: () -> Unit, label: String? = null, role: Role = Role.Button, enabled: Boolean = true): Modifier` (package `uk.co.siland.culvery.core.ui`).
  - `HhButton(text, onClick, modifier, tone, size, icon, enabled, on, spokenName: String? = null)`: `spokenName`, when given, is the node's content description; it must start with `text`.
  - `val SpokenNameKey: SemanticsPropertyKey<String>` (`Buttons.kt`): set with `spokenName`; the helper checks only nodes that carry it.
  - `HhListRow(title, modifier, leading, subtitle, subtitleColor, trailing, onClick, clickLabel: String? = null, below)`.
  - Test fixtures, package `uk.co.siland.culvery.core.ui.test`: `fun SemanticsNodeInteractionsProvider.assertClickablesHaveRoleAndLabel()` and `fun ComposeContentTestRule.assertAccessible()` — the one call a screen test makes per state it renders. Every later task's screen tests call `compose.assertAccessible()`.
  - Each module's tests reach the fixtures through `testImplementation(testFixtures(project(":core:ui")))` (or `project(":core:ui-testing")`, ruling 12).

- [ ] **Step 1: Try `enableAccessibilityChecks()` under Robolectric**

`gradle/libs.versions.toml`, after line 47 (`androidx-compose-ui-test-junit4 = …`):
```toml
androidx-compose-ui-test-junit4-accessibility = { group = "androidx.compose.ui", name = "ui-test-junit4-accessibility" }
```
`core/ui/build.gradle.kts`, in `dependencies`, add `testImplementation(libs.androidx.compose.ui.test.junit4.accessibility)`.
Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/AccessibilityChecksProbeTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Whether Google's Accessibility Test Framework runs on Robolectric's views at all (4d-2 §8.2). */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksProbeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aTinyUnlabelledClickableFailsTheChecks() {
        compose.enableAccessibilityChecks()
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(20.dp).clickable {}) } }
        val failure = runCatching { compose.onRoot().tryPerformAccessibilityChecks() }.exceptionOrNull()
        // Only ATF's own finding counts: "checks not enabled", a missing class or an NPE inside ATF says nothing.
        assertThat(failure?.let { it::class.simpleName }).isEqualTo("AccessibilityViewCheckException")
        assertThat(failure?.message.orEmpty()).containsMatch("(?i)touch target|content ?label|speakable text")
    }

    @Test
    fun aLabelledButtonPassesTheChecks() {
        compose.enableAccessibilityChecks()
        compose.setContent { CulveryTheme(dark = true) { HhButton("Add event", {}, on = ButtonSurface.Bg) } }
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
```
Run: `./gradlew :core:ui:testDebugUnitTest --tests "*AccessibilityChecksProbeTest*"`
Expected: `aTinyUnlabelledClickableFailsTheChecks` FAILS (`expected: AccessibilityViewCheckException but was: null`) and `aLabelledButtonPassesTheChecks` passes. The plan review ran this probe at 9f80250: the artifact resolves from the BOM, but under Robolectric ATF reports nothing for a 20 dp unlabelled clickable. So the expected branch is **Not adopted**. Record the outcome by replacing ruling 13's last sentence in this plan file with one of:
- **Not adopted (expected):** the first test fails as above (or the artifact doesn't resolve, or the second test fails on a correct button). Write "Not adopted (Task 1, <date>): <what happened, one line>." Then delete the probe test, the catalog line and the `testImplementation` line again, and in Step 5 use variant B.
- **Adopted:** both tests pass, so ATF threw its own exception naming the touch target or the label. Write "Adopted (Task 1, <date>): ATF ran under Robolectric and said: <first line of the message>." Keep the probe test, and in Step 5 use variant A of `assertAccessible()`.

- [ ] **Step 2: Turn on `:core:ui`'s test fixtures and give every UI module's tests the dependency**

`gradle.properties`, append:
```properties
android.experimental.enableTestFixturesKotlinSupport=true
```
`core/ui/build.gradle.kts` lines 3-14 become (keep line 1's import and the `tasks.withType<Test>` block after it):
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    alias(libs.plugins.roborazzi)
}

android {
    // The shared semantics helper every module's UI tests call (4d-2 §8.2).
    testFixtures { enable = true }
}

dependencies {
    // SingleAction takes a CoroutineScope in its public API.
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui.test.junit4)
}
```
(If Step 1 adopted the checks, also keep `testImplementation(libs.androidx.compose.ui.test.junit4.accessibility)` and add `testFixturesImplementation(libs.androidx.compose.ui.test.junit4.accessibility)`.)
In the `dependencies` block of each of `core/access/build.gradle.kts`, `core/setup/build.gradle.kts`, `capability/calendar/build.gradle.kts`, `capability/weather/build.gradle.kts` and `app/build.gradle.kts`, add:
```kotlin
    testImplementation(testFixtures(project(":core:ui")))
```
`:core:ui`'s own tests see its fixtures without a line of their own.

- [ ] **Step 3: Write the failing tests**

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ClickableTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClickableTest {
    @get:Rule val compose = createComposeRule()

    private fun clickLabel(tag: String): String? = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.OnClick].label

    @Test
    fun aClickableIsAButtonWithItsLabelUnlessTold() {
        var taps = 0
        compose.setContent {
            CulveryTheme(dark = true) {
                // In a Column, so the boxes don't sit on top of each other and take each other's taps.
                Column {
                    Box(Modifier.size(48.dp).testTag("plain").hhClickable({ taps++ }))
                    Box(Modifier.size(48.dp).testTag("labelled").hhClickable({ taps++ }, label = "open event details", role = Role.Tab))
                    Box(Modifier.size(48.dp).testTag("off").hhClickable({ taps++ }, enabled = false))
                }
            }
        }
        compose.onNodeWithTag("plain").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertThat(clickLabel("plain")).isNull()
        compose.onNodeWithTag("labelled").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        assertThat(clickLabel("labelled")).isEqualTo("open event details")
        compose.onNodeWithTag("off").assertIsNotEnabled().performClick()
        assertThat(taps).isEqualTo(1)
    }

    @Test
    fun aButtonsSpokenNameIsItsDescription() {
        compose.setContent {
            CulveryTheme(dark = true) {
                HhButton("Disconnect", {}, Modifier.testTag("disconnect"), on = ButtonSurface.Surf, spokenName = "Disconnect Google Calendar, alex@example.com")
            }
        }
        compose.onNodeWithTag("disconnect")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Disconnect Google Calendar, alex@example.com")))
    }

    @Test
    fun aListRowsClickLabelSaysWhatHappens() {
        compose.setContent {
            CulveryTheme(dark = true) {
                HhListRow("Sam", Modifier.testTag("row"), subtitle = "Adult · PIN set", onClick = {}, clickLabel = "change Sam’s details")
            }
        }
        compose.onNodeWithTag("row").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertThat(clickLabel("row")).isEqualTo("change Sam’s details")
    }
}
```
Fails if: `hhClickable` loses its `Role.Button` default, drops `label` or `role`, or ignores `enabled`; `spokenName` isn't set as the description; `clickLabel` isn't passed to the row's `clickable`.

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/AccessibilityHelperTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** The shared helper fails on each fault it exists for, and passes the parts that are right, so it can't pass by default. */
@RunWith(AndroidJUnit4::class)
class AccessibilityHelperTest {
    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) = compose.setContent { CulveryTheme(dark = true) { Column { content() } } }

    private fun failure(): String = assertThrows(AssertionError::class.java) { compose.assertClickablesHaveRoleAndLabel() }.message.orEmpty()

    @Test
    fun aClickableWithNoRoleFails() {
        show { Text("Open", Modifier.clickable {}) }
        assertThat(failure()).contains("Open: no role")
    }

    @Test
    fun aClickableWithNoNameFails() {
        show { Box(Modifier.size(48.dp).hhClickable({})) }
        assertThat(failure()).contains("no name")
    }

    @Test
    fun aClickLabelThatRepeatsTheNameFails() {
        show { Text("Sign out", Modifier.hhClickable({}, label = "sign out")) }
        assertThat(failure()).contains("click label repeats its name")
    }

    @Test
    fun aClickLabelThatRepeatsTheVisibleTextUnderALongerNameFails() {
        show {
            Text(
                "Disconnect",
                Modifier.semantics { contentDescription = "Disconnect Google Calendar, alex@example.com" }
                    .hhClickable({}, label = "disconnect"),
            )
        }
        assertThat(failure()).contains("click label repeats its name")
    }

    @Test
    fun aSpokenNameThatDoesNotStartWithItsTextFails() {
        show { HhButton("Hide this calendar", {}, on = ButtonSurface.Surf, spokenName = "Hide School terms") }
        assertThat(failure()).contains("doesn't start with its text")
    }

    @Test
    fun namedButtonsRowsAndFieldsPass() {
        show {
            HhButton("Disconnect", {}, on = ButtonSurface.Surf, spokenName = "Disconnect Google Calendar, alex@example.com")
            HhListRow("Sam", subtitle = "Adult · PIN set", onClick = {}, clickLabel = "change Sam’s details")
            Box(Modifier.size(48.dp).hhClickable({}, role = Role.Tab)) { Text("Home") }
            HhTextField("", {}, placeholder = "Town or city", tag = "field")
        }
        compose.assertClickablesHaveRoleAndLabel()
    }
}
```
Fails if: the helper skips nodes without a role, accepts a blank name, compares labels with names case-sensitively (it must catch "sign out" on "Sign out"), compares the label only with the description (it must catch "disconnect" on visible "Disconnect" under a longer description), skips the spoken-name check, or fails a text field (which has a click action and no role).

- [ ] **Step 4: Run them to see them fail**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*ClickableTest*" --tests "*AccessibilityHelperTest*"`
Expected: FAIL to compile with "Unresolved reference 'hhClickable'", "'assertClickablesHaveRoleAndLabel'", "No parameter with name 'spokenName'", "No parameter with name 'clickLabel'".

- [ ] **Step 5: Write `hhClickable`, the fixtures, `spokenName` and `clickLabel`**

Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Clickable.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * A clickable TalkBack can name (4d-2 §4.1): a [role], `Role.Button` unless said, and [label], what follows "double-tap
 * to", lower case ("open event details"). Leave [label] null when the control's name already says what it does.
 */
fun Modifier.hhClickable(onClick: () -> Unit, label: String? = null, role: Role = Role.Button, enabled: Boolean = true): Modifier =
    clickable(enabled = enabled, onClickLabel = label, role = role, onClick = onClick)
```
`Buttons.kt`: add the parameter after `on` and set the description before the click (imports `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`); the KDoc gains "[spokenName] names a button whose label repeats along a list: it starts with [text] and adds the subject (\"Disconnect Google Calendar, alex@example.com\")." The signature and modifier chain become:
```kotlin
fun HhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Quiet,
    size: ButtonSize = ButtonSize.Regular,
    icon: String? = null,
    enabled: Boolean = true,
    on: ButtonSurface,
    spokenName: String? = null,
) {
```
```kotlin
        modifier = modifier
            .heightIn(min = if (large) ButtonTokens.largeHeight else ButtonTokens.regularHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .then(
                if (spokenName != null) {
                    Modifier.semantics {
                        contentDescription = spokenName
                        this[SpokenNameKey] = spokenName
                    }
                } else {
                    Modifier
                },
            )
            .hhClickable(onClick, enabled = enabled)
            .padding(start = start, end = if (large) ButtonTokens.largePaddingH else ButtonTokens.regularPaddingH),
```
and, above `HhButton`:
```kotlin
/** A button's spoken name, kept apart from other descriptions so the shared test helper can check it starts with the label. */
val SpokenNameKey = SemanticsPropertyKey<String>("SpokenName")
```
(import `androidx.compose.ui.semantics.SemanticsPropertyKey`; drop the now-unused `clickable` and `Role` imports.)
`Components.kt`, `HhListRow`: add `clickLabel: String? = null` after `onClick` (before `below`, so callers' trailing `below` lambdas still work), the KDoc gains "[clickLabel] says what a tap does (\"change Sam’s details\").", and line 83 becomes:
```kotlin
            .then(if (onClick != null) Modifier.hhClickable(onClick, label = clickLabel) else Modifier)
```
(drop the `clickable` import; keep `Role` if nothing else uses it, else drop it too.)

Create `core/ui/src/testFixtures/java/uk/co/siland/culvery/core/ui/test/Accessibility.kt`:
```kotlin
package uk.co.siland.culvery.core.ui.test

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import uk.co.siland.culvery.core.ui.SpokenNameKey

/**
 * 4d-2 §4.1: every clickable has a role and a name (its text or description), no click label that repeats the name or
 * the visible text, and a button's spoken name ([SpokenNameKey]) starts with the text a sighted helper reads out. Text
 * fields are left out: they have a click action but no role, and their label is checked where they are made.
 *
 * It sees only nodes with a click action, in the state the test has drawn: a `pointerInput` tap detector, a
 * long-click-only or custom-action-only node, and any state the test doesn't draw are invisible to it.
 */
fun SemanticsNodeInteractionsProvider.assertClickablesHaveRoleAndLabel() {
    val nodes = onAllNodes(hasClickAction()).fetchSemanticsNodes()
    val problems = nodes.mapNotNull { node ->
        val c = node.config
        if (c.getOrNull(SemanticsProperties.EditableText) != null) return@mapNotNull null
        val text = c.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        val description = c.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        val name = description.ifBlank { text }
        val label = c.getOrNull(SemanticsActions.OnClick)?.label
        val spoken = c.getOrNull(SpokenNameKey)
        val where = c.getOrNull(SemanticsProperties.TestTag) ?: name.ifBlank { "node ${node.id}" }
        when {
            c.getOrNull(SemanticsProperties.Role) == null -> "$where: no role"
            name.isBlank() -> "$where: no name"
            label != null && (label.equals(name, ignoreCase = true) || label.equals(text, ignoreCase = true)) ->
                "$where: click label repeats its name \"$label\""
            spoken != null && !spoken.startsWith(text) -> "$where: spoken name \"$spoken\" doesn't start with its text \"$text\""
            else -> null
        }
    }
    if (problems.isNotEmpty()) throw AssertionError("Clickables TalkBack can't name or place:\n" + problems.joinToString("\n"))
}

/** The one call a screen test makes for each state it renders. */
fun ComposeContentTestRule.assertAccessible() {
    assertClickablesHaveRoleAndLabel()
}
```
Variant A (Step 1 adopted the checks): `assertAccessible()` also runs Google's checks first —
```kotlin
fun ComposeContentTestRule.assertAccessible() {
    // createComposeRule() is an AndroidComposeTestRule on Android; the checks need one.
    (this as AndroidComposeTestRule<*, *>).enableAccessibilityChecks()
    onRoot().tryPerformAccessibilityChecks()
    assertClickablesHaveRoleAndLabel()
}
```
with imports `androidx.compose.ui.test.junit4.AndroidComposeTestRule`, `androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks`, `androidx.compose.ui.test.onRoot`, `androidx.compose.ui.test.tryPerformAccessibilityChecks`. Variant B (not adopted): as written above.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*ClickableTest*" --tests "*AccessibilityHelperTest*"`
Expected: PASS.
If the build fails on the test fixtures themselves (AGP rejects `testFixtures` with the Compose convention, or the fixtures can't see Compose's test APIs), take ruling 12's fallback, then re-run: delete the `android { testFixtures … }` block, the two `testFixturesImplementation` lines and the `gradle.properties` line; move `Accessibility.kt` to `core/ui-testing/src/main/java/uk/co/siland/culvery/core/ui/test/Accessibility.kt` unchanged; add `include(":core:ui-testing")` to `settings.gradle.kts` after `include(":core:ui")`; create `core/ui-testing/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
}

dependencies {
    // Test code only: the shared semantics helper (4d-2 §8.2), used as testImplementation by every UI module.
    implementation(project(":core:ui"))
    implementation(libs.androidx.compose.ui.test.junit4)
}
```
and replace each `testImplementation(testFixtures(project(":core:ui")))` from Step 2 with `testImplementation(project(":core:ui-testing"))`, adding the same line to `core/ui/build.gradle.kts`. Record which way it went in ruling 12 ("Fixtures work" or "Fallback taken: <the error, one line>").

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences (no drawing changed: `hhClickable` is `clickable`, and the description draws nothing).

- [ ] **Step 8: Commit**

```bash
git add gradle.properties gradle/libs.versions.toml settings.gradle.kts core/ui core/ui-testing core/access/build.gradle.kts core/setup/build.gradle.kts capability/calendar/build.gradle.kts capability/weather/build.gradle.kts app/build.gradle.kts docs/superpowers/plans/2026-10-09-culvery-04d2-talkback-timeouts-words.md
git commit -m "Add a clickable with a role and an action label, spoken names for buttons and rows, and a shared test helper that every clickable has a role and a name"
```
(`git add` of a path that doesn't exist is an error: leave out `settings.gradle.kts` and `core/ui-testing` unless the fallback was taken.)

---

### Task 2: Roles and click labels on every clickable; silent scrims; the quiet fill on Close, ‹ › and the steppers (D15, §4.3's scrims, 4d-1 follow-up; rulings 1, 7, 10)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Buttons.kt:47-51` (`quietFill` public), `Shell.kt:100-115` (`HhCloseButton`), `Controls.kt:191-209` (`HhPersonChip`)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt:128-132` (a `FULL_DATE` formatter)
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Spoken.kt`
- Modify: `capability/calendar/…/ui/TodayCard.kt:76-119`, `ui/WeekView.kt:108-117, 238-279, 308-332`, `ui/Pickers.kt:96-114, 136-137, 177-179, 227-268`, `ui/EventDetailSheet.kt:127` (the assign chips)
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt:175-185, 237-268, 293-308`
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupCopy.kt` (after line 63), `PeopleUi.kt:55-93`, `LocationPane.kt:135-144`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/NavRail.kt:60-72`, `OverlayLayers.kt:68-87`, `StatusBar.kt:36-85`
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ShellPartsTest.kt` (create), `ControlsTest.kt`; `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt`, `WeekViewTest.kt`, `EventDetailSheetTest.kt`, `PickersTest.kt`; `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/ForecastCardTest.kt`; `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/PinPadTest.kt`; `core/setup/src/test/java/uk/co/siland/culvery/core/setup/PeopleUiTest.kt`, `LocationPaneTest.kt`; `app/src/test/java/uk/co/siland/culvery/shell/ui/OverlayLayersTest.kt`, `ShellLayoutTest.kt`
- Baselines (changed, light only): the close button and the week's ‹ › (listed in Step 6)

**Interfaces:**
- Consumes: `Modifier.hhClickable`, `HhListRow(…, clickLabel)`, `compose.assertAccessible()` (Task 1).
- Produces:
  - `fun quietFill(on: ButtonSurface, colors: HhColors): Color` (now public).
  - `HhPersonChip(name: String, color: Color, tag: String, enabled: Boolean, clickLabel: String? = null, onClick: () -> Unit)`.
  - `internal val FULL_DATE: DateTimeFormatter` ("EEEE d MMMM", `Locale.ENGLISH`) in `CalendarUi.kt`; Tasks 3, 7 and 8 use it.
  - Calendar ui `Spoken.kt`: `internal const val OPEN_EVENT_DETAILS = "open event details"`, `internal const val RECONNECT_ACTION = "reconnect"`, `internal fun showAsEvent(name: String): String`, `internal fun addEventOn(date: LocalDate): String`. Later tasks add their spoken words here.
  - `internal fun RoundButton(icon: String, label: String, tag: String, on: ButtonSurface, enabled: Boolean = true, onClick: () -> Unit)` in `Pickers.kt`.
  - Setup copy: `internal fun changeDetailsLabel(name: String): String` ("change Sam’s details"), `internal const val USE_AS_HOME = "use as home"`.
  - The scrims (`overlay_scrim`, `picker_scrim`, `pin_scrim`) keep their tags and their tap-to-dismiss, with no semantics of their own.

- [ ] **Step 1: Write the failing tests**

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ShellPartsTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Real graphics: the close button's fill is read from pixels drawn on the sheet's `bg`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellPartsTest {
    @get:Rule val compose = createComposeRule()

    private fun closeButtonFill(dark: Boolean): Int {
        lateinit var window: View
        compose.setContent {
            window = LocalView.current.rootView
            CulveryTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) { HhCloseButton({}) } }
        }
        compose.waitForIdle()
        // captureToImage times out under Robolectric's paused looper, so draw the window.
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        window.draw(Canvas(bitmap))
        val bounds = compose.onNodeWithTag("sheet_close").fetchSemanticsNode().boundsInWindow
        // 6 dp in from the circle's left edge, level with its centre: inside the circle, outside the 26 dp icon.
        val sixDp = with(compose.density) { 6.dp.toPx() }
        return bitmap.getPixel((bounds.left + sixDp).toInt(), bounds.center.y.toInt())
    }

    @Test
    fun theCloseButtonIsAQuietButtonOnTheSheetInTheDay() {
        assertThat(closeButtonFill(dark = false)).isEqualTo(quietFill(ButtonSurface.Bg, LightColors).toArgb())
    }

    @Test
    fun theCloseButtonIsAQuietButtonOnTheSheetAtNight() {
        assertThat(closeButtonFill(dark = true)).isEqualTo(quietFill(ButtonSurface.Bg, DarkColors).toArgb())
    }

    @Test
    fun theCloseButtonIsAButtonNamedOnceByItsIcon() {
        compose.setContent { CulveryTheme(dark = true) { HhCloseButton({}) } }
        val node = compose.onNodeWithTag("sheet_close")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Close")))
        assertThat(node.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isNull()
    }
}
```
Fails today: the fill is `surf2` (in the day theme 1.04:1 on `bg`, so the first test fails; the night test is a **pin**: dark `quietFill(Bg)` is `surf2` already, and it fails if the button draws anything else); no role; the click label repeats "Close".

`ControlsTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.onNodeWithTag`):
```kotlin
    @Test
    fun aPersonChipSaysWhatATapDoes() {
        show { HhPersonChip("Sam", Color(0xFF5B9BE0), "assign_Sam", enabled = true, clickLabel = "show as Sam’s event") {} }
        val node = compose.onNodeWithTag("assign_Sam").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertThat(node.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("show as Sam’s event")
    }
```
Fails today: no `clickLabel` parameter; no role.

`CardsTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsActions`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    private fun clickLabel(node: androidx.compose.ui.test.SemanticsNodeInteraction): String? =
        node.fetchSemanticsNode().config[SemanticsActions.OnClick].label

    @Test
    fun todayRowsSayTheyOpenTheirEventAndThePlusIsNamedOnce() {
        show { TodayCard(SampleUi.todayWithBadges, onAdd = {}) }
        val row = compose.onNodeWithText("Dinner with Jo & Priya").assert(isButton)
        assertThat(clickLabel(row)).isEqualTo("open event details")
        assertThat(clickLabel(compose.onNodeWithTag("today_add").assert(isButton))).isNull()
        compose.assertAccessible()
    }

    @Test
    fun theOtherHomeCardsPassTheSharedCheck() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.assertAccessible()
    }

    @Test
    fun theConnectCardPassesTheSharedCheck() {
        show { ConnectCalendarCard(connectService = "Google Calendar", onConnect = {}) }
        compose.assertAccessible()
    }
```
Fails today: the row's label is "Open" with no role; the + has the label "Add event", its own name. The two card checks are **pins** (no raw clickable on either today; they fail if one is added without a role or name).

`WeekViewTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.onNodeWithContentDescription`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun everyClickableInTheWeekHasARoleAndAName() {
        show { WeekView(state(sync(needsSignIn = listOf("Google"))).copy(weeksAhead = 1), onAdd = {}) }
        compose.assertAccessible()
        compose.onNodeWithContentDescription("Add event on Wednesday 23 September", useUnmergedTree = true).assert(isButton)
        val chip = compose.onNodeWithText("Parkrun")
        assertThat(chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("open event details")
        val reconnect = compose.onNodeWithText("Google needs reconnecting")
        assertThat(reconnect.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("reconnect")
        assertThat(compose.onNodeWithTag("week_later").fetchSemanticsNode().config[SemanticsActions.OnClick].label).isNull()
    }
```
Fails today: the add hints have no name or role, chips say "Open", the reconnect chip has no label, ‹ › repeat their names as labels.

`EventDetailSheetTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsActions`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun everyModeOfTheSheetPassesTheSharedCheck() {
        show(SampleUi.detailEditable)
        compose.assertAccessible()
        mode = DetailMode.ConfirmingDelete
        compose.assertAccessible()
    }

    @Test
    fun theChipsToChooseWhoSayWhatATapDoes() {
        mode = DetailMode.ChoosingPerson
        show(SampleUi.detailUntagged)
        val chip = compose.onNodeWithTag("assign_Alex").assert(isButton)
        assertThat(chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("show as Alex’s event")
        compose.assertAccessible()
    }
```
Fails today: the close button repeats "Close"; the chips have no role or label.

`PickersTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.onNodeWithContentDescription`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun theDatePickersScrimIsSilentAndEveryControlIsNamed() {
        showDates()
        compose.onNodeWithTag("picker_scrim").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithContentDescription("Earlier weeks").assert(isButton)
        assertThat(compose.onNodeWithTag("date_prev").fetchSemanticsNode().config[SemanticsActions.OnClick].label).isNull()
        compose.assertAccessible()
    }

    @Test
    fun theStepButtonsSayWhichValueAndWhichWay() {
        showTime(LocalTime.of(9, 0))
        listOf("Hour up", "Hour down", "Minute up", "Minute down").forEach { compose.onNodeWithContentDescription(it).assert(isButton) }
        compose.assertAccessible()
    }
```
Fails today: the scrim has an `OnClick` labelled "Cancel" and no role; the steppers are "More" and "Less", twice each, with labels equal to their names.

`ForecastCardTest.kt`, add (import `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun theNoLocationCardPassesTheSharedCheck() {
        show(WeatherView.NoLocation)
        compose.assertAccessible()
    }
```
**Pin** (the card's one button is an `HhButton`; the other states have no clickable): fails if a clickable without a role or name is added to the no-location card.

`PinPadTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun theScrimIsSilentAndEveryKeyIsANamedButton() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_scrim").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        listOf("pin_key_5", "pin_cancel", "pin_backspace").forEach {
            compose.onNodeWithTag(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
        compose.assertAccessible()
    }
```
Fails today: the scrim's `OnClick` "Cancel"; keys without a role.

`PeopleUiTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsActions`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun aPersonsRowSaysItChangesTheirDetailsAndTheMoveButtonsAreNamedOnce() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        assertThat(compose.onNodeWithTag("person_Sam").fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("change Sam’s details")
        assertThat(compose.onNodeWithTag("person_up_Sam").fetchSemanticsNode().config[SemanticsActions.OnClick].label).isNull()
        compose.assertAccessible()
    }
```
Fails today: the row has no label; Move up's label repeats "Move Sam up".

`LocationPaneTest.kt`, add (import `androidx.compose.ui.semantics.SemanticsActions`):
```kotlin
    @Test
    fun aTownSaysATapMakesItHome() {
        search.answer = { listOf(brighton) }
        show()
        type("Can")
        advance(500)
        assertThat(compose.onNodeWithTag("place_0").fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("use as home")
    }
```
Fails today: no label.

`OverlayLayersTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`):
```kotlin
    @Test
    fun theScrimIsSilent() {
        showLayer()
        compose.runOnIdle { overlay.show { Text("Sheet body") } }
        compose.onNodeWithTag("overlay_scrim").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
```
Fails today: the scrim's `OnClick` "Close".

`ShellLayoutTest.kt`: replace `signOutAnnouncesItsAction` (lines 149-154) with the tests below (imports `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun signOutIsNamedOnce() {
        statusBar(SessionUi("Alex", "Admin"))
        val onClick = compose.onNodeWithTag("status_sign_out").fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertThat(onClick.label).isNull()
        compose.assertAccessible()
    }

    private fun themeToggle(dark: Boolean, previewing: Boolean): Pair<String, String?> {
        compose.setContent {
            CulveryTheme(dark = dark) { StatusBar({ at }, dark = dark, previewing = previewing, session = null, onSignOut = {}, onToggleThemePreview = {}) }
        }
        val node = compose.onNodeWithTag("status_theme").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val config = node.fetchSemanticsNode().config
        return config[SemanticsProperties.ContentDescription].single() to config[SemanticsActions.OnClick].label
    }

    @Test
    fun theThemeToggleOnAutoByDayOffersTheNightTheme() {
        assertThat(themeToggle(dark = false, previewing = false)).isEqualTo("Theme: Auto" to "preview the night theme")
    }

    @Test
    fun theThemeToggleOnAutoByNightOffersTheDayTheme() {
        assertThat(themeToggle(dark = true, previewing = false)).isEqualTo("Theme: Auto" to "preview the day theme")
    }

    @Test
    fun theThemeTogglePreviewingOffersTheWayBack() {
        assertThat(themeToggle(dark = true, previewing = true)).isEqualTo("Theme: Night" to "go back to the automatic theme")
    }

    /** TalkBack's double-tap is the semantics action, not a touch: it must reach the same toggle. */
    @Test
    fun theThemeToggleWorksFromTalkBack() {
        var toggles = 0
        compose.setContent {
            CulveryTheme(dark = false) { StatusBar({ at }, dark = false, previewing = false, session = null, onSignOut = {}, onToggleThemePreview = { toggles++ }) }
        }
        compose.onNodeWithTag("status_theme").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(toggles).isEqualTo(1)
        compose.onNodeWithTag("status_theme").performClick()
        assertThat(toggles).isEqualTo(2)
    }
```
(also import `androidx.compose.ui.test.performSemanticsAction`, `androidx.compose.ui.test.performClick`.)
Fail today: Sign out's label is "Sign out"; the toggle has no role, no description ("Auto" is its only text) and no label. `theThemeToggleWorksFromTalkBack` is a **pin** (today the toggle is a plain `clickable`, so both paths work): it fails if the semantics action and the touch stop reaching the same toggle.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :core:ui:testDebugUnitTest --tests "*ShellPartsTest*" --tests "*ControlsTest*" :capability:calendar:testDebugUnitTest --tests "*CardsTest*" --tests "*WeekViewTest*" --tests "*EventDetailSheetTest*" --tests "*PickersTest*" :capability:weather:testDebugUnitTest --tests "*ForecastCardTest*" :core:access:testDebugUnitTest --tests "*PinPadTest*" :core:setup:testDebugUnitTest --tests "*PeopleUiTest*" --tests "*LocationPaneTest*" :app:testDebugUnitTest --tests "*OverlayLayersTest*" --tests "*ShellLayoutTest*"`
Expected: FAIL as listed: `ControlsTest` to compile ("No parameter with name 'clickLabel'"), `ShellPartsTest` on the day theme's pixel and the close button's label, the rest on their assertions. The pins pass.

- [ ] **Step 3: The shared parts**

`Buttons.kt:47`: `internal fun quietFill(` becomes `fun quietFill(`.
`Shell.kt`, `HhCloseButton` (100-115) becomes (drop the `clickable` import if unused):
```kotlin
/** 48 dp round quiet button with a 26 dp `close` icon, named by its icon. Sheets are `bg`, so its fill is the quiet fill on `bg`. */
@Composable
fun HhCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("sheet_close")
            .size(ShellTokens.closeButton)
            .clip(CircleShape)
            .background(quietFill(ButtonSurface.Bg, c))
            .hhClickable(onClick),
    ) {
        HhIcon(Icons.CLOSE, size = ShellTokens.closeIcon, tint = c.ink, contentDescription = "Close")
    }
}
```
`Controls.kt`, `HhPersonChip` (191-209):
```kotlin
/** Hand-off §7 person chip: a chip's size and shape, `surf`, a 12 dp dot in the person's [color] 8 from their [name]. */
@Composable
fun HhPersonChip(name: String, color: Color, tag: String, enabled: Boolean, clickLabel: String? = null, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipIconGap),
        modifier = Modifier
            .testTag(tag)
            .height(ControlTokens.chipHeight)
            .clip(RoundedCornerShape(ControlTokens.chipRadius))
            .background(c.surf)
            .hhClickable(onClick, label = clickLabel, enabled = enabled)
            .padding(horizontal = ControlTokens.chipPaddingH),
    ) {
        Box(Modifier.size(ControlTokens.chipDot).clip(CircleShape).background(color))
        Text(name, style = ControlType.chip, color = c.ink, maxLines = 1)
    }
}
```

- [ ] **Step 4: The calendar's clickables**

`CalendarUi.kt`, after `DAY_AND_MONTH` (line 132):
```kotlin
/** "Tuesday 7 October": how TalkBack names a day (4d-2 §4.5, §5.5, §5.6). */
internal val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
```
Create `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Spoken.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.FULL_DATE

// What TalkBack says for the calendar's controls (4d-2 Appendix A.2). Click labels follow "double-tap to", in lower case.

internal const val OPEN_EVENT_DETAILS = "open event details"
internal const val RECONNECT_ACTION = "reconnect"

internal fun showAsEvent(name: String): String = "show as $name’s event"

internal fun addEventOn(date: LocalDate): String = "Add event on ${date.format(FULL_DATE)}"
```
`TodayCard.kt`: `AddCircle`'s line 86 becomes `.hhClickable(onClick),` and `TodayRow`'s line 101 becomes `.hhClickable({ onOpen(event.ref) }, label = OPEN_EVENT_DETAILS)` (imports `uk.co.siland.culvery.core.ui.hhClickable`; drop `clickable` and `Role`).
`WeekView.kt`:
- the ‹ › calls (111, 114) gain `on = ButtonSurface.Bg`:
```kotlin
                            RoundButton(Icons.CHEVRON_LEFT, "Earlier week", "week_earlier", on = ButtonSurface.Bg, enabled = state.weeksAhead > 0) {
                                onWeeksAhead(state.weeksAhead - 1)
                            }
                            RoundButton(Icons.CHEVRON_RIGHT, "Later week", "week_later", on = ButtonSurface.Bg, enabled = state.weeksAhead < MAX_WEEKS_AHEAD) {
                                onWeeksAhead(state.weeksAhead + 1)
                            }
```
- `AddHint` (238-251):
```kotlin
/** Hand-off §7: an `add` in `mute` at the foot of each column, in a tap area at least 48 dp tall, named with its day. */
@Composable
private fun AddHint(date: LocalDate, onAdd: () -> Unit) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .testTag("week_add_$date")
            .fillMaxWidth()
            .heightIn(min = CalendarDimens.addHintMinHeight)
            .semantics { contentDescription = addEventOn(date) }
            .hhClickable(onAdd),
    ) {
        HhIcon(Icons.ADD, size = CalendarDimens.addHintIcon, tint = Culvery.colors.mute)
    }
}
```
- `EventChip` line 261: `.hhClickable({ onOpen(event.ref) }, label = OPEN_EVENT_DETAILS)`.
- `ReconnectChip` line 319: `.hhClickable(onClick, label = RECONNECT_ACTION)`.
(imports `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`, `uk.co.siland.culvery.core.ui.hhClickable`; drop `clickable` and `Role`.)
`Pickers.kt`:
- `PickerLayer` (96-114):
```kotlin
/** Over the sheet only (2b-2 design D4): a scrim that cancels on a tap, with [content] centred on it. */
@Composable
internal fun PickerLayer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("picker_scrim")
                .background(ShellTokens.pinScrim)
                // A gesture, not a clickable: TalkBack never lands on the scrim; the card has Cancel.
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )
        content()
    }
}
```
- the date picker's ‹ › (136-137) gain `on = ButtonSurface.Surf`:
```kotlin
            RoundButton(Icons.CHEVRON_LEFT, "Earlier weeks", "date_prev", on = ButtonSurface.Surf) { page-- }
            RoundButton(Icons.CHEVRON_RIGHT, "Later weeks", "date_next", on = ButtonSurface.Surf) { page++ }
```
- the steppers (177, 179) name their value:
```kotlin
            Stepper(hour, "hour", "Hour", onUp = { hour = hourStep(hour, up = true) }, onDown = { hour = hourStep(hour, up = false) })
```
```kotlin
            Stepper(minute, "minute", "Minute", onUp = { minute = minuteStep(minute, up = true) }, onDown = { minute = minuteStep(minute, up = false) })
```
- `DateCell` line 221: `.hhClickable(onClick),`
- `RoundButton`, `Stepper`, `StepButton` (227-268):
```kotlin
/** A round quiet button named by its icon ([label]); its fill is the quiet fill on [on]. */
@Composable
internal fun RoundButton(icon: String, label: String, tag: String, on: ButtonSurface, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(CalendarDimens.pageButton)
            .alpha(if (enabled) 1f else CalendarDimens.DISABLED_ALPHA)
            .clip(CircleShape)
            .background(quietFill(on, c))
            .hhClickable(onClick, enabled = enabled),
    ) {
        HhIcon(icon, size = CalendarDimens.pageButtonIcon, tint = c.ink, contentDescription = label)
    }
}

@Composable
private fun Stepper(value: Int, tag: String, name: String, onUp: () -> Unit, onDown: () -> Unit) {
    val c = Culvery.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CalendarDimens.stepperGap)) {
        StepButton(Icons.EXPAND_LESS, "${tag}_up", "$name up", onUp)
        Text(value.toString().padStart(2, '0'), style = CalendarType.timeValue, color = c.ink, modifier = Modifier.testTag("${tag}_value"))
        StepButton(Icons.EXPAND_MORE, "${tag}_down", "$name down", onDown)
    }
}

/** On the picker card (`surf`), so the quiet fill on `surf`. */
@Composable
private fun StepButton(icon: String, tag: String, label: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(CalendarDimens.stepperWidth, CalendarDimens.stepperHeight)
            .clip(RoundedCornerShape(CalendarDimens.stepperRadius))
            .background(quietFill(ButtonSurface.Surf, c))
            .hhClickable(onClick),
    ) {
        HhIcon(icon, size = CalendarDimens.stepperIcon, tint = c.ink, contentDescription = label)
    }
}
```
(imports `androidx.compose.runtime.rememberUpdatedState`, `uk.co.siland.culvery.core.ui.hhClickable`, `uk.co.siland.culvery.core.ui.quietFill`; `detectTapGestures` and `pointerInput` are already imported; drop `clickable` and `MutableInteractionSource`.)
`EventDetailSheet.kt:127`, the assign chips:
```kotlin
                            people.forEach { person ->
                                HhPersonChip(person.name, Color(person.color), "assign_${person.name}", enabled = !busy, clickLabel = showAsEvent(person.name)) { onAssign(person) }
                            }
```

- [ ] **Step 5: The PIN pad, people, towns and the shell**

`PinPad.kt`, `PinPadFrame`'s outer `Box` (175-185):
```kotlin
    val cancel by rememberUpdatedState(onCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pin_scrim")
            // A gesture, not a clickable: TalkBack never lands on the scrim, which holds the card; the card has Cancel.
            .pointerInput(Unit) { detectTapGestures { cancel() } },
    ) {
```
the Cancel key's `clickable` (242-246) gains `role = Role.Button`:
```kotlin
                                .clickable(
                                    interactionSource = null,
                                    indication = ripple(bounded = false, radius = PinPadDimens.key / 2),
                                    role = Role.Button,
                                    onClick = onCancel,
                                ),
```
the backspace (265): `.hhClickable({ digits = digits.dropLast(1) }, enabled = digits.isNotEmpty() && enabled),`; `DigitKey` (303): `.hhClickable(onClick, enabled = enabled)`.
(imports `androidx.compose.runtime.rememberUpdatedState`, `androidx.compose.ui.semantics.Role`, `uk.co.siland.culvery.core.ui.hhClickable`; drop `MutableInteractionSource`.)
`SetupCopy.kt`, after `moveDownLabel` (line 59):
```kotlin

// What a tap does, for TalkBack (4d-2 §4.1): after "double-tap to", in lower case.
internal fun changeDetailsLabel(name: String): String = "change $name’s details"

internal const val USE_AS_HOME = "use as home"
```
`PeopleUi.kt`, `PersonRow`'s `HhListRow` call gains `clickLabel = changeDetailsLabel(name),` after `onClick = onClick,`; `MoveButton` line 93 becomes `.hhClickable(onClick),` (imports `uk.co.siland.culvery.core.ui.hhClickable`; drop `clickable` and `Role`).
`LocationPane.kt`, `PlaceRow`'s `HhListRow` call gains `clickLabel = USE_AS_HOME,` after `onClick = onClick,`.
`NavRail.kt:67`: `.hhClickable(onOpenSettings),` (the rail's tabs are Task 3's).
`OverlayLayers.kt`, `OverlayLayer` (68-87):
```kotlin
/** The sheet layer: a scrim over the whole shell that dismisses on a tap, and the content against the right edge. */
@Composable
fun OverlayLayer(state: OverlayState) {
    val content = state.content ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("overlay_scrim")
                .background(ShellTokens.sheetScrim)
                // A gesture, not a clickable: TalkBack never lands on the scrim; every sheet has its own Close.
                .pointerInput(state) { detectTapGestures { state.dismiss() } },
        )
        content()
    }
}
```
(imports `androidx.compose.foundation.gestures.detectTapGestures`; drop `clickable`, `MutableInteractionSource`, `remember` if unused.)
`StatusBar.kt`: above `StatusBar` add
```kotlin
// What the theme toggle's tap does, for TalkBack (4d-2 §4.1).
private const val PREVIEW_NIGHT = "preview the night theme"
private const val PREVIEW_DAY = "preview the day theme"
private const val BACK_TO_AUTOMATIC = "go back to the automatic theme"
```
after `val label = …` add
```kotlin
    val action = when {
        previewing -> BACK_TO_AUTOMATIC
        dark -> PREVIEW_DAY
        else -> PREVIEW_NIGHT
    }
```
Sign out's modifier (line 70): `.hhClickable(onSignOut)`; the toggle's `Row` modifier (line 79), with one click path for finger and TalkBack alike:
```kotlin
            modifier = Modifier
                .testTag("status_theme")
                .hhClickable(onToggleThemePreview, label = action)
                // After the click, so it clears only the label and icon inside; the click keeps its role and action.
                .clearAndSetSemantics { contentDescription = "Theme: $label" }
                .padding(4.dp),
```
(imports `androidx.compose.ui.semantics.clearAndSetSemantics`, `androidx.compose.ui.semantics.contentDescription`, `uk.co.siland.culvery.core.ui.hhClickable`; drop `clickable`.)

- [ ] **Step 6: Run the tests, then compare and re-record the baselines**

Run the Step 2 command. Expected: PASS. Then each changed module's whole tests: `./gradlew :core:ui:testDebugUnitTest :core:access:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest :capability:weather:testDebugUnitTest :app:testDebugUnitTest`. A test that tapped a scrim with `performClick()` still passes (it injects a real tap at the scrim's centre); a test reading a removed click label fails and is updated to the new one.
Compare:
```bash
./gradlew :capability:calendar:compareRoborazziDebug --tests "*DetailScreenshotTest*" --tests "*EditorScreenshotTest*" --tests "*WeekScreenshotTest*"
./gradlew :core:setup:compareRoborazziDebug --tests "*SetupScreenshotTest*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Open every `*_compare.png`. Expected differences, light theme only (dark `quietFill(Bg)` and `quietFill(Surf)` are `surf2`, as before):
- `detail_*_light` (editable, readonly_feed, recurring, syncing, untagged, delete_confirm, pin) and `detail_fs15`: the close button's fill from grey (`surf2`) to white (`surf`); nothing else.
- `editor_*_light` (child, date_picker, edit, empty, failed, keyboard, locked, own_length, time_picker): the same close button change; in `editor_date_picker_light` and `editor_time_picker_light` only that (the picker's ‹ › and steppers are on `surf`, already `surf2`).
- `person_new_light`: the close button.
- `week_light`, `week_next_light`, `week_furthest_light`, `week_fs15`: ‹ › from grey to white.
Any other change, or any dark image: stop and report. Then record:
```bash
./gradlew :capability:calendar:recordRoborazziDebug --tests "*DetailScreenshotTest*" --tests "*EditorScreenshotTest*" --tests "*WeekScreenshotTest*"
./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest*"
git status --short -- "*.png"
```

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add core/ui core/access core/setup capability/calendar capability/weather app
git commit -m "Give every clickable a role and a spoken action, silence the scrims, and draw Close, the week arrows and the steppers with the quiet fill"
```

---

### Task 3: Tabs and headings (D16, D19; ruling 1)

**Review:** sonnet.

**Files:**
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/NavRail.kt:36-99`
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SettingsScreen.kt:64, 66-68, 92-109`, `SetupUi.kt:19`, `PersonEditorSheet.kt:166-173`
- Modify: `capability/calendar/…/ui/Spoken.kt` (Task 2's), `ui/TodayCard.kt:57`, `ui/ComingUpCard.kt:59, 86-95` (the header and `DayColumn`), `ui/ConnectCalendarCard.kt:46`, `ui/WeekView.kt:119-126, 193-202`, `ui/ReviewCalendars.kt:187`, `ui/EventDetailSheet.kt:190`, `ui/EventEditorSheet.kt:202`, `ui/Pickers.kt:128, 171`
- Modify: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/ForecastCard.kt:62`
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt:215`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/NavRailTest.kt` (create); `core/setup/src/test/…/SettingsScreenTest.kt`, `PeopleUiTest.kt`; `capability/calendar/src/test/…/ui/CardsTest.kt`, `WeekViewTest.kt`, `EventDetailSheetTest.kt`, `EventEditorSheetTest.kt`, `PickersTest.kt`, `ReviewCalendarsTest.kt`; `capability/weather/src/test/…/ui/ForecastCardTest.kt`; `core/access/src/test/…/ui/PinPadTest.kt`
- Baselines: none change (semantics only; the rail and page items keep their look).

**Interfaces:**
- Consumes: `hhClickable`, `FULL_DATE`, `Spoken.kt`, `assertAccessible()` (Tasks 1–2).
- Produces: `internal fun dayHeading(date: LocalDate, prefix: String? = null): String` in calendar ui `Spoken.kt` ("Today, Tuesday 6 October", "Thursday 8 October"); rail items and Settings page items are `Role.Tab` in a `selectableGroup()`; the titles named in §4.5 are headings; the week's title is tagged `week_title` (Task 9 makes it a live region).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/uk/co/siland/culvery/shell/ui/NavRailTest.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.TabItem

@RunWith(AndroidJUnit4::class)
class NavRailTest {
    @get:Rule val compose = createComposeRule()
    private var selected by mutableStateOf(HOME_TAB_ID)
    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val inAGroup = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) {
            NavRail(listOf(TabItem("calendar", "Calendar", Icons.CALENDAR_MONTH)), selected, onSelect = { selected = it }, onOpenSettings = {})
        }
    }

    @Test
    fun theRailsItemsAreTabsInOneGroupAndSettingsIsAButtonOutsideIt() {
        show()
        compose.onNodeWithTag("rail_home").assert(tab).assertIsSelected()
        compose.onNodeWithTag("rail_home").onParent().assert(inAGroup)
        compose.onNodeWithTag("rail_calendar").assert(tab).assertIsNotSelected().performClick()
        compose.onNodeWithTag("rail_calendar").assertIsSelected()
        compose.onNodeWithTag("rail_home").assertIsNotSelected()
        compose.onNodeWithTag("rail_settings").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithTag("rail_settings").onParent().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.SelectableGroup))
        compose.assertAccessible()
    }
}
```
Fails today: rail items have no role and no selected state; no group.

`SettingsScreenTest.kt`, add (imports `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`, `androidx.compose.ui.test.isHeading`, `androidx.compose.ui.test.onParent`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun thePagesAreTabsInOneGroupUnderAHeading() {
        show(page("a", "Alpha", 0), KioskPage(FakeHomeApp()))
        compose.onNodeWithTag("settings_page_a").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertIsSelected()
        compose.onNodeWithTag("settings_page_a").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        compose.onNodeWithText("Settings").assert(isHeading())
        compose.onNodeWithTag("settings_page_kiosk").performClick()
        compose.onNodeWithText("Kiosk").assert(isHeading())
        compose.assertAccessible()
    }
```
(`onNodeWithText("Kiosk")` matches the page's title, a `StepTitle`; the list item reads "Kiosk" too: if the finder sees two nodes, use `onAllNodesWithText("Kiosk").filterToOne(isHeading())` instead.) Fails today: page items have `selected` but no role and no group; titles aren't headings.

`PeopleUiTest.kt`, add (import `androidx.compose.ui.test.isHeading`):
```kotlin
    @Test
    fun thePersonSheetsTitleIsAHeading() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithText("Add person", useUnmergedTree = true).assert(isHeading())
    }
```
(The Add person button above the sheet also says "Add person"; the sheet's title is the heading. If two nodes match, filter to the heading: `onAllNodesWithText("Add person", useUnmergedTree = true).filterToOne(isHeading())`.) Fails today: not a heading.

`CardsTest.kt`, add (imports `androidx.compose.ui.test.isHeading`, `androidx.compose.ui.test.onNodeWithContentDescription`):
```kotlin
    @Test
    fun todaysTitleIsAHeading() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Today").assert(isHeading())
    }

    @Test
    fun comingUpsDaysAreHeadingsThatSayTheDate() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("Coming up").assert(isHeading())
        compose.onNodeWithContentDescription("Tomorrow, Thursday 24 September").assert(isHeading())
        compose.onNodeWithContentDescription("Friday 25 September").assert(isHeading())
    }

    @Test
    fun theConnectCardsTitleIsAHeading() {
        show { ConnectCalendarCard(connectService = "Google Calendar", onConnect = {}) }
        compose.onNodeWithText("Connect a calendar").assert(isHeading())
    }
```
Fails today: no headings; the day labels read "Tomorrow" and "Friday" only.

`WeekViewTest.kt`, add (imports `androidx.compose.ui.test.isHeading`, `androidx.compose.ui.test.onChildren`, `androidx.compose.ui.test.onNodeWithContentDescription`):
```kotlin
    @Test
    fun eachColumnHeaderIsOneHeadingThatSaysItsDate() {
        show { WeekView(state()) }
        compose.onNodeWithContentDescription("Today, Wednesday 23 September").assert(isHeading()).onChildren().assertCountEquals(0)
        compose.onNodeWithContentDescription("Thursday 24 September").assert(isHeading())
        compose.onNodeWithText("This week").assert(isHeading())
    }
```
Fails today: the header is two text nodes ("Today", "23"), neither a heading; the title isn't one.

`EventDetailSheetTest.kt`, `EventEditorSheetTest.kt`, `PickersTest.kt`, `ReviewCalendarsTest.kt`, `ForecastCardTest.kt` and `PinPadTest.kt`, add one test each (import `androidx.compose.ui.test.isHeading`):
```kotlin
    // EventDetailSheetTest
    @Test
    fun theSheetsTitleIsAHeading() {
        show(SampleUi.detailEditable)
        compose.onNodeWithText("Dinner with Jo & Priya").assert(isHeading())
    }
```
```kotlin
    // EventEditorSheetTest
    @Test
    fun theSheetsTitleIsAHeading() {
        show(newForm())
        compose.onNodeWithText("New event").assert(isHeading())
    }
```
```kotlin
    // PickersTest
    @Test
    fun thePickersTitlesAreHeadings() {
        showDates()
        compose.onNodeWithText("Pick a date").assert(isHeading())
    }

    @Test
    fun theTimePickersTitleIsAHeading() {
        showTime(LocalTime.of(9, 0))
        compose.onNodeWithText("Pick a time").assert(isHeading())
    }
```
```kotlin
    // ReviewCalendarsTest
    @Test
    fun theTitleIsAHeading() {
        show()
        compose.onNodeWithText("Calendars").assert(isHeading())
    }
```
```kotlin
    // ForecastCardTest
    @Test
    fun theTitleIsAHeading() {
        show(READY)
        compose.onNodeWithText("Forecast").assert(isHeading())
    }
```
```kotlin
    // PinPadTest
    @Test
    fun theTitleIsAHeading() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithText("Who's this?").assert(isHeading())
    }
```
Each fails today: the title isn't a heading.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :app:testDebugUnitTest --tests "*NavRailTest*" :core:setup:testDebugUnitTest --tests "*SettingsScreenTest*" --tests "*PeopleUiTest*" :capability:calendar:testDebugUnitTest --tests "*CardsTest*" --tests "*WeekViewTest*" --tests "*EventDetailSheetTest*" --tests "*EventEditorSheetTest*" --tests "*PickersTest*" --tests "*ReviewCalendarsTest*" :capability:weather:testDebugUnitTest --tests "*ForecastCardTest*" :core:access:testDebugUnitTest --tests "*PinPadTest*"`
Expected: FAIL as listed.

- [ ] **Step 3: Tabs**

`NavRail.kt`, the rail items' loop (lines 55-57) becomes a group of its own, so Settings stays outside it:
```kotlin
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            (listOf(HomeTab) + tabs).forEach { tab ->
                RailItem(tab, selected = tab.id == selectedId) { onSelect(tab.id) }
            }
        }
```
and `RailItem`'s line 84 (`.clickable(onClick = onClick)`) becomes `.selectable(selected = selected, role = Role.Tab, onClick = onClick)`. (imports `androidx.compose.foundation.selection.selectable`, `androidx.compose.foundation.selection.selectableGroup`, `androidx.compose.ui.semantics.Role`; drop `clickable`.) TalkBack reads "Home, tab, selected, 1 of 2".
`SettingsScreen.kt`: the page list's `Column` (line 66) gains `modifier = Modifier.selectableGroup()`:
```kotlin
            Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.settingsItemGap), modifier = Modifier.selectableGroup()) {
```
and `PageItem`'s modifier (lines 98-104) becomes:
```kotlin
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .height(SetupDimens.settingsItemHeight)
            .clip(RoundedCornerShape(SetupDimens.settingsItemRadius))
            .then(if (chosen) Modifier.background(c.accentSoft) else Modifier)
            .selectable(selected = chosen, role = Role.Tab, onClick = onClick)
            .padding(horizontal = SetupDimens.settingsItemPaddingH),
```
(imports `androidx.compose.foundation.selection.selectable`, `androidx.compose.foundation.selection.selectableGroup`, `androidx.compose.ui.semantics.Role`; drop `clickable` and `selected`.)

- [ ] **Step 4: Headings**

Each of these `Text` calls gains `Modifier.semantics { heading() }` (merged into an existing `modifier =` where there is one; imports `androidx.compose.ui.semantics.heading`, `androidx.compose.ui.semantics.semantics`):
- `SetupUi.kt:19`: `Text(title, style = SetupType.title, color = c.ink, modifier = Modifier.semantics { heading() })`
- `SettingsScreen.kt:64`: `Text(SETTINGS, style = SetupType.title, color = c.ink, modifier = Modifier.semantics { heading() })`
- `PersonEditorSheet.kt:166-173`: the title `Text`'s `modifier = Modifier.weight(1f)` becomes `modifier = Modifier.weight(1f).semantics { heading() }`
- `TodayCard.kt:57`: `modifier = Modifier.weight(1f).semantics { heading() }`
- `ComingUpCard.kt:59`: `Text("Coming up", style = HhType.cardTitle, color = c.ink, modifier = Modifier.semantics { heading() })`
- `ConnectCalendarCard.kt:46`: `Text("Connect a calendar", style = HhType.cardTitle, color = c.ink, modifier = Modifier.semantics { heading() })`
- `ForecastCard.kt:62`: `Text(FORECAST_TITLE, style = HhType.cardTitle, color = c.ink, modifier = Modifier.semantics { heading() })`
- `WeekView.kt:119-126`, the title: `modifier = Modifier.weight(1f, fill = false).testTag("week_title").semantics { heading() },`
- `ReviewCalendars.kt:187`: `Text(title, style = CalendarType.reviewTitle, color = Culvery.colors.ink, modifier = Modifier.semantics { heading() })`
- `EventDetailSheet.kt:190`: `Text(e.title, style = CalendarType.sheetTitle, color = c.ink, modifier = Modifier.semantics { heading() })`
- `EventEditorSheet.kt:202`: add `modifier = Modifier.semantics { heading() }` to the title `Text`
- `Pickers.kt:128` ("Pick a date") and `:171` ("Pick a time"): add `modifier = Modifier.semantics { heading() }`
- `PinPad.kt:215`: `Text(title, style = PinPadType.title, color = c.ink, modifier = Modifier.semantics { heading() })`

`Spoken.kt`, add:
```kotlin
/** A day as a heading: "Today, Tuesday 6 October", "Tomorrow, Wednesday 7 October", "Thursday 8 October". */
internal fun dayHeading(date: LocalDate, prefix: String? = null): String = listOfNotNull(prefix, date.format(FULL_DATE)).joinToString(", ")
```
`WeekView.kt`, the column header `Row` (193-202) becomes one heading:
```kotlin
        Row(
            Modifier
                .padding(start = inset, end = inset, bottom = inset)
                .clearAndSetSemantics {
                    heading()
                    contentDescription = dayHeading(day.date, if (isToday) "Today" else null)
                },
        ) {
```
(imports `androidx.compose.ui.semantics.clearAndSetSemantics`, `androidx.compose.ui.semantics.heading`.)
`ComingUpCard.kt`: the caller (86-95) passes the spoken heading, and `DayColumn`'s label (line ~87 in `DayColumn`) carries it:
```kotlin
                days.forEachIndexed { i, day ->
                    DayColumn(
                        label = if (i == 0) "Tomorrow" else day.date.format(WEEKDAY),
                        spoken = dayHeading(day.date, if (i == 0) "Tomorrow" else null),
                        events = day.events,
                        modifier = Modifier.weight(1f),
                    )
                }
```
```kotlin
@Composable
private fun DayColumn(label: String, spoken: String, events: List<EventUi>, modifier: Modifier) {
```
```kotlin
        Text(label, style = CalendarType.strong14, color = c.ink, modifier = Modifier.semantics { heading(); contentDescription = spoken })
```
(imports `androidx.compose.ui.semantics.contentDescription`, `heading`, `semantics`.)

- [ ] **Step 5: Run the tests to see them pass**

Run the Step 2 command. Expected: PASS. Then `./gradlew :app:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest :capability:weather:testDebugUnitTest :core:access:testDebugUnitTest`. `CardsTest.comingUpLabelsTomorrowThenWeekdays` still finds "Tomorrow" and "Friday" by text (the `Text` keeps its text; the description is what TalkBack reads). A test finding the week's "Today" or day number by text inside a column header fails (the header is now one node with no text): find it by its description instead.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences (only semantics changed). A difference is a stop.

- [ ] **Step 7: Commit**

```bash
git add app core/setup core/access capability/calendar capability/weather
git commit -m "Make the rail and the Settings pages tabs, and give TalkBack headings for every title, card and day"
```

---

### Task 4: Modal overlays and pane titles (D17; rulings 7, 11, 14)

**Review:** opus.

**Files:**
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt:33-87` (`ShellLayers`, `OverlayLayer`), `app/src/main/java/uk/co/siland/culvery/SetupWiring.kt:52-76` (`AppContent`), `app/src/main/java/uk/co/siland/culvery/MainActivity.kt:154-174`
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SettingsScreen.kt:46-56` (the root `Row`), `PersonEditorSheet.kt:155-164, 250`
- Modify: `capability/calendar/…/ui/EventDetailSheet.kt:80-88`, `ui/EventEditorSheet.kt:107-115, 202`, `ui/Pickers.kt:125, 170, 191-205`, `ui/CalendarConnectHost.kt:95-108`
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt:74-107, 119-149, 155-185`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/ModalLayersTest.kt` (create), `AppContentTest.kt`, `ShellLayersRedrawTest.kt` (the new `pinShowing` argument); `capability/calendar/src/test/…/ui/EventDetailSheetTest.kt`, `EventEditorSheetTest.kt`, `PickersTest.kt`, `CalendarConnectHostTest.kt`; `core/access/src/test/…/ui/PinPadTest.kt`, `ChoosePinPadTest.kt`; `core/setup/src/test/…/SettingsScreenTest.kt`, `PeopleUiTest.kt`
- Baselines: none change.

**Interfaces:**
- Consumes: the silent scrims (Task 2).
- Produces:
  - `fun ShellLayers(overlay: OverlayState, toast: ToastMessage?, onToastHidden: (Long) -> Unit, pinShowing: Boolean, pinPad: @Composable () -> Unit, onTouch: (() -> Unit)? = null, shell: @Composable () -> Unit)` — `pinShowing` has no default; MainActivity passes `pinPrompt.request` ≠ null.
  - `fun OverlayLayer(state: OverlayState, hidden: Boolean = false)`.
  - `fun PinPadFrame(title: String, line: String?, message: String, wrongPin: Boolean, enabled: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit, overSheet: Boolean = false, drawScrim: Boolean = true, paneTitle: String = title)`.
  - `internal const val ENTER_YOUR_PIN = "Enter your PIN"` in `PinPad.kt`.
  - `internal fun PickerCard(width: Dp, gap: Dp, tag: String, paneTitle: String, content: @Composable ColumnScope.() -> Unit)`.
  - Spoken: `internal const val EVENT_DETAILS = "Event details"`, `internal fun connectingPane(service: String): String` ("Connecting to Google Calendar") in calendar ui `Spoken.kt`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/uk/co/siland/culvery/shell/ui/ModalLayersTest.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.AppContent
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage

/** 4d-2 §4.3: whatever an overlay covers is out of TalkBack's reach; the toasts never are. */
@RunWith(AndroidJUnit4::class)
class ModalLayersTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()
    private var pinShowing by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var toast by mutableStateOf<ToastMessage?>(null)

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) {
            ShellLayers(
                overlay = overlay,
                toast = toast,
                onToastHidden = {},
                pinShowing = pinShowing,
                pinPad = { if (pinShowing) Box(Modifier.fillMaxSize().semantics { paneTitle = "Enter your PIN" }) { Text("Pad key") } },
            ) {
                AppContent(
                    complete = true,
                    settingsOpen = settingsOpen,
                    overlay = overlay,
                    wizard = {},
                    shell = { Text("Home", Modifier.testTag("rail_home").hhClickable({})) },
                    settings = { Box(Modifier.fillMaxSize().semantics { paneTitle = "Settings" }) { Text("Settings page") } },
                    onHomeDrawn = {},
                )
            }
        }
    }

    private fun showSheet() = compose.runOnIdle {
        overlay.show { HhSheet(PaddingValues(), Modifier.semantics { paneTitle = "Event details" }) { Text("Sheet body") } }
    }

    private fun pane(title: String) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title))

    @Test
    fun aSheetHidesTheShellUntilItCloses() {
        show()
        compose.onNodeWithTag("rail_home").assertExists()
        showSheet()
        compose.onNodeWithTag("rail_home").assertDoesNotExist()
        pane("Event details").assertExists()
        compose.runOnIdle { overlay.dismiss() }
        compose.onNodeWithTag("rail_home").assertExists()
    }

    @Test
    fun settingsHidesTheShell() {
        show()
        settingsOpen = true
        compose.onNodeWithTag("rail_home").assertDoesNotExist()
        pane("Settings").assertExists()
        compose.onNodeWithText("Settings page").assertExists()
    }

    @Test
    fun thePinPadHidesTheSheetAndTheShellButNeverTheToast() {
        show()
        showSheet()
        toast = ToastMessage(1, "Event added", "info")
        pinShowing = true
        compose.onNodeWithText("Sheet body").assertDoesNotExist()
        compose.onNodeWithTag("rail_home").assertDoesNotExist()
        pane("Enter your PIN").assertExists()
        compose.onNodeWithTag("toast").assertExists()
    }
}
```
Fails today: no `pinShowing` parameter; with it, the shell and the sheet stay in the tree.

`AppContentTest.kt` and `ShellLayersRedrawTest.kt`: each `ShellLayers(` call gains `pinShowing = false,` after `onToastHidden = …,` (they compile again once it exists).

`EventDetailSheetTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsProperties` if missing, `androidx.compose.ui.test.SemanticsMatcher`):
```kotlin
    @Test
    fun theSheetIsAPaneTalkBackAnnounces() {
        show(SampleUi.detailEditable)
        compose.onNodeWithTag("detail_sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Event details"))
    }
```
`EventEditorSheetTest.kt`, add:
```kotlin
    @Test
    fun theSheetIsAPaneAndAPickerHidesIt() {
        show(newForm())
        compose.onNodeWithTag("editor_sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "New event"))
        compose.onNodeWithTag("day_pick").performClick()
        compose.onNodeWithTag("date_picker").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Pick a date"))
        compose.onNodeWithTag("editor_save").assertDoesNotExist()
        compose.onNodeWithTag("picker_cancel").performClick()
        compose.onNodeWithTag("editor_save").assertExists()
    }
```
`PickersTest.kt`, add:
```kotlin
    @Test
    fun theTimePickerIsAPane() {
        showTime(LocalTime.of(9, 0))
        compose.onNodeWithTag("time_picker").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Pick a time"))
    }
```
`CalendarConnectHostTest.kt`, add (imports as above, `uk.co.siland.culvery.core.ui.CulveryTheme` if missing):
```kotlin
    @Test
    fun theConnectingCardIsAPane() {
        compose.setContent { CulveryTheme(dark = true) { ConnectingCard("Google Calendar", onCancel = {}) } }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Connecting to Google Calendar")).assertExists()
    }
```
`PinPadTest.kt`, add:
```kotlin
    @Test
    fun thePadIsAPaneThatAsksForThePin() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_scrim").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Enter your PIN"))
    }
```
`ChoosePinPadTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`):
```kotlin
    @Test
    fun eachStageIsAPaneNamedByItsTitle() {
        show()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Choose a 4-digit PIN")).assertExists()
        tap("2468")
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Enter it again")).assertExists()
    }
```
`SettingsScreenTest.kt`, add (imports as above):
```kotlin
    @Test
    fun settingsIsAPane() {
        show(page("a", "Alpha", 0))
        compose.onNodeWithTag("settings").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Settings"))
    }
```
`PeopleUiTest.kt`, add (imports as above):
```kotlin
    @Test
    fun aPersonsSheetIsAPaneThatItsPinChooserHides() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Sam")).assertExists()
        compose.onNodeWithTag("person_set_pin").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Choose a 4-digit PIN")).assertExists()
        compose.onNodeWithTag("person_save").assertDoesNotExist()
    }
```
Each fails today: no pane titles; the editor's Save and the person sheet's Save stay in the tree under their pickers.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :app:testDebugUnitTest --tests "*ModalLayersTest*" :capability:calendar:testDebugUnitTest --tests "*EventDetailSheetTest*" --tests "*EventEditorSheetTest*" --tests "*PickersTest*" --tests "*CalendarConnectHostTest*" :core:access:testDebugUnitTest --tests "*PinPadTest*" --tests "*ChoosePinPadTest*" :core:setup:testDebugUnitTest --tests "*SettingsScreenTest*" --tests "*PeopleUiTest*"`
Expected: FAIL (`:app` to compile on `pinShowing`; the rest on their assertions).

- [ ] **Step 3: Hide what an overlay covers**

`OverlayLayers.kt`, `ShellLayers` gains `pinShowing` and wraps the two lower layers:
```kotlin
@Composable
fun ShellLayers(
    overlay: OverlayState,
    toast: ToastMessage?,
    onToastHidden: (Long) -> Unit,
    pinShowing: Boolean,
    pinPad: @Composable () -> Unit,
    onTouch: (() -> Unit)? = null,
    shell: @Composable () -> Unit,
) {
    val touched by rememberUpdatedState(onTouch)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {}
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touched?.invoke()
                }
            },
    ) {
        // A TalkBack double-tap is an action, not a touch, so a scrim can't stop it: what is covered leaves the tree.
        Box(Modifier.fillMaxSize().then(if (overlay.isShowing || pinShowing) Modifier.clearAndSetSemantics {} else Modifier)) { shell() }
        OverlayLayer(overlay, hidden = pinShowing)
        pinPad()
        ToastLayer(toast, onHidden = onToastHidden)
    }
}
```
The KDoc gains: "While a sheet or the PIN pad shows, what it covers is hidden from TalkBack; the toasts never are."
`OverlayLayer` gains `hidden: Boolean = false`, and its outer `Box` becomes:
```kotlin
    Box(
        Modifier.fillMaxSize().then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier),
        contentAlignment = Alignment.CenterEnd,
    ) {
```
(import `androidx.compose.ui.semantics.clearAndSetSemantics`.)
`SetupWiring.kt`, `AppContent`'s `true ->` branch:
```kotlin
        true -> {
            Box(Modifier.fillMaxSize().then(if (settingsOpen) Modifier.clearAndSetSemantics {} else Modifier)) { shell() }
            if (settingsOpen) settings()
```
(import `androidx.compose.ui.semantics.clearAndSetSemantics`.)
`MainActivity.kt`, in `setContent` after `val complete by …` (line 159):
```kotlin
            val pinRequest by pinPrompt.request.collectAsStateWithLifecycle()
```
and the `ShellLayers(` call gains `pinShowing = pinRequest != null,` after `onToastHidden = toasts::hide,`.
`EventEditorSheet.kt:108`, `HhSheet(` gains its modifier:
```kotlin
        HhSheet(
            modifier = if (picker != EditorPicker.None) Modifier.clearAndSetSemantics {} else Modifier,
            padding = PaddingValues(
```
`PersonEditorSheet.kt:163`, the sheet's modifier becomes `Modifier.testTag("person_sheet").then(if (choosingPin) Modifier.clearAndSetSemantics {} else Modifier),` (import `clearAndSetSemantics`).

- [ ] **Step 4: Pane titles**

`SettingsScreen.kt`, the root `Row`'s modifier gains `.semantics { paneTitle = SETTINGS }` after `.testTag("settings")` (imports `androidx.compose.ui.semantics.paneTitle`; `semantics` is imported since Task 3).
`PersonEditorSheet.kt:155`, the root `Box`: `Box(Modifier.fillMaxHeight().width(ShellTokens.sheetWidth).semantics { paneTitle = existing?.person?.name ?: ADD_PERSON }) {` (imports `paneTitle`, `semantics`).
`Spoken.kt`, add:
```kotlin
internal const val EVENT_DETAILS = "Event details"

internal fun connectingPane(service: String): String = "Connecting to $service"
```
`EventDetailSheet.kt:87`: `modifier = modifier.testTag("detail_sheet").semantics { paneTitle = EVENT_DETAILS },`.
`EventEditorSheet.kt`: before the root `Box` (line 107) add `val title = if (form.mode == EventForm.Mode.New) "New event" else "Edit event"`; the root becomes `Box(modifier.testTag("editor_sheet").semantics { paneTitle = title }.width(ShellTokens.sheetWidth).fillMaxHeight()) {`, and `Header` takes the title: `Header(title, form, everyone) { … }` with `private fun Header(title: String, form: EventForm, everyone: List<Person>, onClose: () -> Unit)` drawing `Text(title, …)` at line 202. (Task 12 changes the two words, once.)
`Pickers.kt`, `PickerCard` takes the pane title:
```kotlin
@Composable
internal fun PickerCard(width: Dp, gap: Dp, tag: String, paneTitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.paneTitle = paneTitle }
            .width(width)
```
(the rest unchanged); the callers: line 125 `PickerCard(CalendarDimens.datePickerWidth, CalendarDimens.datePickerGap, "date_picker", paneTitle = "Pick a date") {`, line 170 `PickerCard(CalendarDimens.timePickerWidth, CalendarDimens.timePickerGap, "time_picker", paneTitle = "Pick a time") {`, and `CalendarConnectHost.kt:98` `PickerCard(CalendarDimens.connectingWidth, CalendarDimens.connectingGap, "connecting_card", paneTitle = connectingPane(service)) {`.
`PinPad.kt`: add `internal const val ENTER_YOUR_PIN = "Enter your PIN"` above `PinPadSheet`; `PinPadFrame` gains `paneTitle: String = title` as its last parameter, and its root `Box` (the `pin_scrim`) gains `.semantics { this.paneTitle = paneTitle }` after `.testTag("pin_scrim")`; `PinPadSheet`'s `PinPadFrame(` call passes `paneTitle = ENTER_YOUR_PIN,`. `ChoosePinPad` passes nothing: its pane is its stage's title (ruling 11).

- [ ] **Step 5: Run the tests to see them pass**

Run the Step 2 command. Expected: PASS. Then `./gradlew :app:testDebugUnitTest :capability:calendar:testDebugUnitTest :core:access:testDebugUnitTest :core:setup:testDebugUnitTest`. Expected: PASS with no test moved. The tests that open a cover were read at eb40ec0, and none reads a covered node (§10 "Hidden backgrounds"):
- `AppContentTest`: `aCompleteSetupShowsTheShellAndSettings` reads "Shell" only before Settings opens; `aTapInsideAnOpenSheetCountsAsATouch`, `closingSettingsDismissesItsSheet` and `finishingSetupClosesAnOpenSheet` read the sheet, not the shell; `withSettingsClosedTouchesDoNothing` taps the shell with nothing open.
- `OverlayLayersTest` and `ShellLayersRedrawTest`: `OverlayLayer` alone, or the sheet's own nodes; the scrim taps are pointer input (`performClick` injects a touch at the scrim's centre, and `detectTapGestures` is not semantics).
- `EventEditorSheetTest`'s and `EventEditorHostTest`'s picker tests: they touch the picker's nodes while it is open, and the sheet's only after it closes.
- `PeopleUiTest.aPinChosenInTheSheetCanBeChangedOrRemoved`: the sheet's nodes after the chooser closes; `StepsUiTest`'s PIN chooser is the You step's, which hides nothing (ruling 14 is the person sheet only).
If a test fails here anyway, it reads a covered node: close the cover first, or tap by position when it tests pointer behaviour, and add it to this list.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 7: Commit**

```bash
git add app core/setup core/access capability/calendar
git commit -m "Hide what a sheet, Settings, a picker or the PIN pad covers from TalkBack, and announce each overlay by name"
```

---

### Task 5: Live regions, always-present message lines, urgent toasts, field labels and errors (D18, D20; Decision 10; rulings 8, 23, 28)

**Review:** sonnet.

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Overlays.kt:24-31` (`Toaster`)
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Shell.kt:117-134` (`HhToast`), `Controls.kt:145-189` (`HhTextField`) and a new `HhMessageLine` after it
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellOverlays.kt:33-49` (`ToastMessage`, `ShellToasts`), `shell/ui/OverlayLayers.kt` (`ToastLayer`, its `HhToast` call)
- Modify (urgent call sites): `capability/calendar/…/CalendarEditor.kt:127, 130, 133, 282, 311, 435`, `CalendarReview.kt:96`, `CalendarSetup.kt:104, 128`, `CalendarSync.kt:193`, `SourceRefresher.kt:83`; `core/access/…/DefaultAccessControl.kt:73, 109`; `core/setup/…/PeopleEditor.kt:98, 105`; `provider/calendar-google/…/GoogleConnectFlow.kt:49, 94, 107`
- Modify (live regions, field names): `capability/calendar/…/ui/EventEditorSheet.kt:218-230, 357-378`, `ui/EventDetailSheet.kt:197-213`; `core/setup/…/PersonEditorSheet.kt:53-131, 180-191, 219`, `PeopleEditor.kt:23-32, 118-135`, `steps/YouStep.kt:57-66, 96-99, 145-166`, `LocationPane.kt:103-133`, `SetupCopy.kt` (after `noTownsMatch`)
- Modify (`Toaster` implementers, signature only): `capability/calendar/src/test/…/RecordingToaster.kt`, `SilentToaster.kt`, `CalendarSyncTest.kt:652, 796`; `core/access/src/test/…/RecordingToaster.kt`; `core/setup/src/test/…/TestHousehold.kt:40-47`, `PeopleUiTest.kt:209-211`; `provider/calendar-google/src/test/…/TestSupport.kt:24-35`; `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt:52`, `SampleAddTest.kt:60`, `SampleRollbackTest.kt:40`
- Test: `core/ui/src/test/…/ControlsTest.kt`; `app/src/test/…/shell/ShellToastsTest.kt`, `shell/ui/OverlayLayersTest.kt`; `capability/calendar/src/test/…/CalendarEditorTest.kt`, `CalendarReviewTest.kt`, `CalendarSetupTest.kt`, `CalendarSyncTest.kt`, `SourceRefresherTest.kt`, `ui/EventEditorHostTest.kt`, `ui/EventEditorSheetTest.kt`, `ui/EventDetailSheetTest.kt`; `core/access/src/test/…/DefaultAccessControlTest.kt`; `core/setup/src/test/…/PeopleEditorTest.kt`, `PeopleUiTest.kt`, `StepsTest.kt`, `LocationPaneTest.kt`, `YouFormTest.kt` (create); `provider/calendar-google/src/test/…/GoogleConnectFlowTest.kt`
- Baselines: the in-page message lines keep one line while empty (§4.4, Step 7): `person_new_*`, `person_pin_dark`, `person_remove_dark`, `you_*`, `location_results_*`, `settings_location_*`.

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `interface Toaster { fun show(message: String, icon: String = TOAST_ICON_INFO, urgent: Boolean = false) }`.
  - `data class ToastMessage(val id: Long, val message: String, val icon: String, val urgent: Boolean = false)`.
  - `fun HhToast(message: String, icon: String, modifier: Modifier = Modifier, urgent: Boolean = false)` — merged, `LiveRegionMode.Assertive` when urgent, else `Polite`.
  - `fun HhTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, label: String, tag: String, modifier: Modifier = Modifier, readOnly: Boolean = false, capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences, error: String? = null, onDone: () -> Unit = {})`.
  - `fun HhMessageLine(message: String?, style: TextStyle, color: Color, tag: String, modifier: Modifier = Modifier, spoken: String? = message)` (`Controls.kt`): always composed, one line tall while empty, `LiveRegionMode.Polite`; [spoken] is its description when it differs from what shows.
  - `PeopleOutcome.Refused(val message: String, val nameTaken: Boolean = false)`; `PersonForm.nameError` and `YouForm.nameError: String?` (Compose state).
  - The location pane's one status line, tag `location_status`: "Searching…" (Task 15), no match, a failed search, a failed save, or blank; spoken "3 towns found" over a list.
  - `internal fun townsFound(count: Int): String` ("1 town found", "3 towns found") in `SetupCopy.kt`.
  - Test recorders: calendar, access and setup `RecordingToaster` and google `Toasts` keep `messages` and add `urgent: MutableList<String>` (the messages shown urgent).

- [ ] **Step 0 (the controller, before the implementer starts): check on the emulator how TalkBack names a filled field**

A content description on an editable node can replace the typed value in what TalkBack reads, so check it on `emulator-5554` before this task's field change is written (T5 of the plan review). Using a scratch branch, never `main`: `git switch -c probe-field-name`; in `EventEditorSheet.kt` add `.semantics { contentDescription = "Event name" }` (imports `androidx.compose.ui.semantics.contentDescription`, `semantics`) to the title field's modifier at line 229; `ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug`. Turn TalkBack on and capture its speech as `docs/superpowers/plans/2026-10-09-4d2-walkthrough.md` A.2 says (the walk-through file arrives in Task 17; until then use the commands in Task 17 Step 3's A.2 and A.6), open Calendar › Add event (ask the user first: nothing is saved; Close discards it), focus the title field, type `Swim`, move away and back.
- **Kept:** TalkBack says "Event name", the typed "Swim" and "edit box" when it comes back. Record "Field names: content description kept the value (<date>)" under this step and go on with Step 1 as written.
- **Lost:** it says "Event name, edit box" with no "Swim". Then Step 5 uses the fallback below instead of `contentDescription = label`, and this step's record says "Field names: fallback (<date>): <what TalkBack said>". Fallback: in `HhTextField`'s `decorationBox`, beside the placeholder, add `Box(Modifier.semantics { text = AnnotatedString(label) })` while `value` isn't empty (it merges the label into the field's text the way the placeholder did while empty), keep `error(error)`, and change the field-name test assertions in Step 2 from `ContentDescription` to `SemanticsMatcher("named $label") { label in it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }`. Check again on the emulator; if the value is still lost, stop and ask.
Then `git switch main`, `git branch -D probe-field-name`, restore TalkBack (A.6), and run `adb -s emulator-5554 logcat -c`.

- [ ] **Step 1: Give the toast its urgency, and every recorder the new signature**

`Overlays.kt`, `Toaster`:
```kotlin
/**
 * A short bottom-centre message (hand-off §7). Injected wherever a toast is shown (access control, the calendar
 * editor, the outbox drain), so a toast never depends on a composable still being on screen. Safe from any
 * thread. A new toast replaces the current one. [urgent] for errors and refusals: TalkBack interrupts to say it, and it
 * stays longer (4d-2 Decision 10, §6.5).
 */
interface Toaster {
    fun show(message: String, icon: String = TOAST_ICON_INFO, urgent: Boolean = false)
}
```
`ShellOverlays.kt`:
```kotlin
data class ToastMessage(val id: Long, val message: String, val icon: String, val urgent: Boolean = false)
```
```kotlin
    override fun show(message: String, icon: String, urgent: Boolean) {
        _current.value = ToastMessage(ids.incrementAndGet(), message, icon, urgent)
    }
```
Each test `Toaster` takes the third parameter. The recorders become:
```kotlin
// capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/RecordingToaster.kt
class RecordingToaster : Toaster {
    // Written from Room's and the app scope's threads, read on the test's.
    val messages: MutableList<String> = CopyOnWriteArrayList()

    /** The messages shown as urgent (errors and refusals). */
    val urgent: MutableList<String> = CopyOnWriteArrayList()

    override fun show(message: String, icon: String, urgent: Boolean) {
        messages += message
        if (urgent) this.urgent += message
    }
}
```
The same body in `core/setup/src/test/…/TestHousehold.kt`'s `RecordingToaster`, in `core/access/src/test/…/RecordingToaster.kt` (with `mutableListOf()` as it has today) and in `provider/calendar-google/src/test/…/TestSupport.kt`'s `Toasts`. The others only change their signature to `override fun show(message: String, icon: String, urgent: Boolean)`: `SilentToaster`, `CalendarSyncTest`'s two objects (lines 652, 796), `PeopleUiTest`'s object (line 210), google `NoToasts`, and `DebugSeedTest`'s `NoToasts`, `SampleAddTest`'s and `SampleRollbackTest`'s `Toasts`.

- [ ] **Step 2: Write the failing tests**

`ControlsTest.kt`, add (imports `androidx.compose.ui.Modifier`, `androidx.compose.ui.platform.testTag`, `androidx.compose.ui.semantics.LiveRegionMode`):
```kotlin
    @Test
    fun aToastIsPoliteAndAnUrgentOneIsAssertive() {
        show {
            Row {
                HhToast("Event added.", Icons.INFO, Modifier.testTag("plain"))
                HhToast("Couldn’t save — try again.", Icons.INFO, Modifier.testTag("urgent"), urgent = true)
            }
        }
        compose.onNodeWithTag("plain").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("urgent").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }

    @Test
    fun aFilledTextFieldKeepsItsNameAndCarriesItsError() {
        show { HhTextField("Alex", {}, placeholder = "Name", label = "Name", tag = "field", error = "Someone is already called Alex.") }
        compose.onNodeWithTag("field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Name")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Someone is already called Alex."))
    }
```
```kotlin
    /** Compose speaks a live region only when a node it already has changes, so the line must be there while empty. */
    @Test
    fun aMessageLineIsInTheTreeAndOneLineTallBeforeItHasAMessage() {
        var message by mutableStateOf<String?>(null)
        show { HhMessageLine(message, ControlType.chip, Color.Red, "line", spoken = message?.let { "said: $it" }) }
        val empty = compose.onNodeWithTag("line")
            .assertTextEquals("")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertThat(empty.fetchSemanticsNode().size.height).isGreaterThan(0)
        message = "Couldn’t save — try again."
        compose.onNodeWithTag("line")
            .assertTextEquals("Couldn’t save — try again.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("said: Couldn’t save — try again.")))
    }
```
(imports `androidx.compose.runtime.getValue`, `mutableStateOf`, `setValue`, `androidx.compose.ui.test.assertTextEquals`.) Fails if the line is composed only with a message, loses its height while empty, or drops its live region.
(The toast test's tags are outside `HhToast`'s own `testTag("toast")`, and the outermost tag is the one a node keeps.) `aTextFieldShowsItsPlaceholderOnlyWhileEmpty`'s call gains `label = "Town or city",`.
Fails today: no live region; no `urgent` or `label`/`error` parameter.

`ShellToastsTest.kt`, add:
```kotlin
    @Test
    fun anUrgentToastSaysSo() {
        toasts.show("Couldn’t save — try again.", urgent = true)
        assertThat(toasts.current.value?.urgent).isTrue()
        toasts.show("Event added.")
        assertThat(toasts.current.value?.urgent).isFalse()
    }
```
`OverlayLayersTest.kt`, add (imports `androidx.compose.ui.semantics.LiveRegionMode`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`):
```kotlin
    @Test
    fun theToastLayerSpeaksAnUrgentToastAssertively() {
        compose.setContent { CulveryTheme(dark = true) { ToastLayer(ToastMessage(1, "Couldn’t save — try again.", "info", urgent = true)) {} } }
        compose.onNodeWithTag("toast").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }
```
Each urgent call site, through a test that already reaches it — add the line after the test's existing toast assertion:
- `CalendarEditorTest.signedInChildIsRefusedWithoutAPinPad` (after line 192), `closingTheSheetBeforeARefusalToastsIt` (after 589), `aDatabaseFailureWhileQueueingIsToldNotThrown` (after 282): `assertThat(access.toasts.urgent).isEqualTo(access.toasts.messages)`
- `EventEditorHostTest`: the `COULD_NOT_OPEN` test (after line 524) and `aStoreFailureInTheEditorsDeleteToastsLikeTheDetailSheet` (after 543): `assertThat(access.toasts.urgent).isEqualTo(access.toasts.messages)`
- `SourceRefresherTest.aMasterDeletedWithOneChangeWaitingSaysItWasDroppedInTheSingular` (after its `toaster.messages` assertion, line 121-123), `CalendarSetupTest.aConnectThatFailsStoresNothingAndSaysSo` (after 152), `CalendarSyncTest.drainRejectionDropsTheChangeAndToastsWhy` (after 480), `CalendarReviewTest`'s two `COULD_NOT_SAVE` tests (after 107 and 164): `assertThat(toaster.urgent).isEqualTo(toaster.messages)`
- `CalendarReviewTest`, the first "now shows as" test (after line 77), a plain toast: `assertThat(toaster.urgent).isEmpty()`
- `DefaultAccessControlTest.aRefusalOnTheSessionShortcutToastsThenLocks` (after 293) and `allowIsAppliedAfterAPinAndARefusalToastClosesThePad`: `assertThat(toasts.urgent).isEqualTo(toasts.messages)`
- `GoogleConnectFlowTest.aReconnectWithADifferentAccountIsRefused` (after line 92) and `anyOtherPlayServicesFailureStopsAndSaysSo`: `assertThat(toasts.urgent).isEqualTo(toasts.messages)`
- `PeopleEditorTest.movingSomeoneTakesTheOpenSessionAndSaysSo` (after line 76), plain: `assertThat(access.toasts.urgent).isEmpty()`; and a new test for the move's failure:
```kotlin
    @Test
    fun aMoveThatCantFindThePersonSaysSoUrgently() = runTest {
        start()
        assertThat(editor.move(PersonId("gone"), up = true)).isEqualTo(PeopleOutcome.Refused(COULD_NOT_SAVE))
        assertThat(access.toasts.urgent).containsExactly(COULD_NOT_SAVE)
    }
```
(import `uk.co.siland.culvery.core.household.PersonId`.)
Each fails today: no call passes `urgent`.

Live regions and field names:
```kotlin
    // EventEditorSheetTest
    @Test
    fun theFailureCardIsSpokenPolitelyAndTheTitleFieldIsNamed() {
        failure = "Couldn’t save — try again."
        show(newForm())
        compose.onNodeWithTag("editor_failure").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("editor_title").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Event name")))
    }
```
```kotlin
    // EventDetailSheetTest
    @Test
    fun theSyncingPillIsSpokenPolitely() {
        show(SampleUi.detailSyncing)
        compose.onNodeWithTag("detail_syncing").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
```
`PeopleUiTest`: the line is always there now, so the three waits for it (lines 130, 200, 226) wait for its text instead: `compose.waitUntil(5_000) { compose.onNodeWithTag("person_message").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty().isNotEmpty() }` (imports `androidx.compose.ui.semantics.getOrNull`, `SemanticsProperties`).
`PeopleUiTest.aRefusalShowsInTheSheetAndKeepsWhatWasTyped`, after its `person_message` assertion (line 131):
```kotlin
        compose.onNodeWithTag("person_message").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("person_name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Someone is already called alex."))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Name")))
```
`PeopleEditorTest.aNameInUseIsRefusedAndNothingChanges` line 105 becomes `…isEqualTo(PeopleOutcome.Refused("Someone is already called ALEX.", nameTaken = true))`.
`StepsTest.nobodyCanBeCalledFamilyEvenFirst`, after line 211: `assertThat(you.form.nameError).isEqualTo("Someone is already called Family.")`.
Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/YouFormTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.setup.steps.YouForm
import uk.co.siland.culvery.core.setup.steps.YouFormContent
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class YouFormTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aTakenNameIsAnErrorOnTheNamedFieldAndSpokenPolitely() {
        val form = YouForm().apply {
            name = "Family"
            message = "Someone is already called Family."
            nameError = message
        }
        compose.setContent { CulveryTheme(dark = true) { YouFormContent(form, onSetPin = {}) } }
        compose.onNodeWithTag("you_name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Your name")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Someone is already called Family."))
        compose.onNodeWithTag("you_message").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
}
```
`LocationPaneTest.kt`, add (imports `androidx.compose.ui.semantics.LiveRegionMode`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`):
```kotlin
    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun theStatusLineIsThereBeforeAnySearchAndSpeaksTheCountOverAList() {
        search.answer = { listOf(brighton) }
        show()
        compose.onNodeWithTag("location_status").assertTextEquals("").assert(polite)
        type("Can")
        advance(500)
        compose.onNodeWithTag("location_status")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("1 town found")))
            .assert(polite)
        compose.onNodeWithTag("location_query").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Town or city")))
    }

    @Test
    fun noMatchIsSaidOnTheSameLine() {
        show()
        type("Xqz")
        advance(500)
        compose.onNodeWithTag("location_status").assertTextContains("Xqz", substring = true).assert(polite)
    }
```
(imports `androidx.compose.ui.test.assertTextContains`, `assertTextEquals`.) `aFailedSaveSaysSoAndKeepsTheResults` and the other text-based tests keep working: the line's text is the message, and blank once it clears.
Each fails today: no status line, no live regions, no field names, no `nameError`, no `nameTaken`.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew --continue :core:ui:testDebugUnitTest --tests "*ControlsTest*" :app:testDebugUnitTest --tests "*ShellToastsTest*" --tests "*OverlayLayersTest*" :capability:calendar:testDebugUnitTest --tests "*CalendarEditorTest*" --tests "*CalendarReviewTest*" --tests "*CalendarSetupTest*" --tests "*CalendarSyncTest*" --tests "*SourceRefresherTest*" --tests "*EventEditorHostTest*" --tests "*EventEditorSheetTest*" --tests "*EventDetailSheetTest*" :core:access:testDebugUnitTest --tests "*DefaultAccessControlTest*" :core:setup:testDebugUnitTest --tests "*PeopleEditorTest*" --tests "*PeopleUiTest*" --tests "*StepsTest*" --tests "*LocationPaneTest*" --tests "*YouFormTest*" :provider:calendar-google:testDebugUnitTest --tests "*GoogleConnectFlowTest*"`
Expected: FAIL as listed.

- [ ] **Step 4: Urgent call sites**

Each of these calls gains `urgent = true` (a named argument, after the message):
- `CalendarEditor.kt`: line 127 `toaster.show(cannotAddForOthers(name), urgent = true)`; 130 `toaster.show(COULD_NOT_OPEN, urgent = true)`; 133 `toaster.show(couldNotSave(label, TRY_AGAIN), urgent = true)`; 282 `toaster.show(couldNotSave(label, it.message), urgent = true)`; 311 `toaster.show(cannotChangeOthers(who.person.name), urgent = true)`; 435 `toaster.show(couldNotSave(label, result.message), urgent = true)`.
- `CalendarReview.kt:96` `toaster.show(COULD_NOT_SAVE, urgent = true)`.
- `CalendarSetup.kt:104` and `:128` `toaster.show(couldNotConnect(serviceNameOf(connection, providers::displayNameOf)), urgent = true)`.
- `CalendarSync.kt:193` `dropped.forEach { (label, reasons) -> toaster.show(couldNotSaveAll(label, reasons), urgent = true) }`.
- `SourceRefresher.kt:83` `… toaster.show(masterGone(provider.descriptor.displayName, outcome.droppedChanges), urgent = true)`.
- `DefaultAccessControl.kt:73` and `:109` `toaster.show(refusal.message(…), urgent = true)`.
- `PeopleEditor.kt:98` `toaster.show(COULD_NOT_SAVE, urgent = true)`; `:105` `outcome is PeopleOutcome.Refused -> toaster.show(outcome.message, urgent = true)`.
- `GoogleConnectFlow.kt:49` `toaster.show(UPDATE_PLAY_SERVICES, urgent = true)`; `:94` `toaster.show(differentAccount(stored), urgent = true)`; `:107` `toaster.show(couldNotConnect(GOOGLE_DISPLAY_NAME), urgent = true)`.
Everything else stays plain, including `KioskLifecycle`'s home-app toasts (they say what to do next, not that something failed).

- [ ] **Step 5: The toast, the text field and the in-place messages**

`Shell.kt`, `HhToast`:
```kotlin
/** The toast pill: `ink` background, `bg` text, 16 sp / 600, padding 14×22, radius 26. One spoken item, polite unless [urgent]. */
@Composable
fun HhToast(message: String, icon: String, modifier: Modifier = Modifier, urgent: Boolean = false) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShellTokens.toastIconGap),
        modifier = modifier
            .testTag("toast")
            .semantics(mergeDescendants = true) { liveRegion = if (urgent) LiveRegionMode.Assertive else LiveRegionMode.Polite }
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
(imports `androidx.compose.ui.semantics.LiveRegionMode`, `liveRegion`, `semantics`.)
`OverlayLayers.kt`, `ToastLayer`'s last line: `HhToast(t.message, t.icon, urgent = t.urgent)`.
`Controls.kt`, `HhTextField` gains `label` (after `placeholder`) and `error` (before `onDone`), and its modifier names it:
```kotlin
/**
 * 64 dp, `surf`, 22 sp / 600, one line; [placeholder] while empty; the keyboard's Done calls [onDone] and never saves.
 * [label] is its name for TalkBack once text hides the placeholder; [error] is read with it.
 */
@Composable
fun HhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    error: String? = null,
    onDone: () -> Unit = {},
) {
```
```kotlin
        modifier = modifier
            .testTag(tag)
            .semantics {
                contentDescription = label
                if (error != null) error(error)
            }
            .fillMaxWidth()
            .height(ControlTokens.fieldHeight)
            .onFocusChanged { focused = it.isFocused },
```
(imports `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.error`.) After `HhTextField`:
```kotlin
/**
 * An in-page message that TalkBack hears appear (4d-2 §4.4). Compose speaks a live region only when a node it already
 * has changes, and leaves a node with no size out of the tree, so the line is always composed and one line tall while
 * empty. [spoken] is its description when it differs from what shows ("3 towns found" under a list).
 */
@Composable
fun HhMessageLine(message: String?, style: TextStyle, color: Color, tag: String, modifier: Modifier = Modifier, spoken: String? = message) {
    Text(
        message.orEmpty(),
        style = style,
        color = color,
        modifier = modifier.testTag(tag).semantics {
            liveRegion = LiveRegionMode.Polite
            if (spoken != null && spoken != message) contentDescription = spoken
        },
    )
}
```
(imports `androidx.compose.ui.semantics.LiveRegionMode`, `liveRegion`, `androidx.compose.ui.text.TextStyle` where missing.) The callers:
- `EventEditorSheet.kt:221-229`: `placeholder = "What's happening?", label = "Event name", tag = "editor_title",`
- `PersonEditorSheet.kt:181-190`: `placeholder = NAME, label = NAME, tag = "person_name", capitalization = KeyboardCapitalization.Words, error = form.nameError,` and its `onValueChange` also sets `form.nameError = null`.
- `YouStep.kt:149-158`: `placeholder = NAME, label = YOUR_NAME, tag = "you_name", capitalization = KeyboardCapitalization.Words, error = form.nameError,` and `onValueChange` also sets `form.nameError = null`. Add `internal const val YOUR_NAME = "Your name"` to `SetupCopy.kt` after `NAME`.
- `LocationPane.kt:117`: `HhTextField(query, onQuery, TOWN_OR_CITY, TOWN_OR_CITY, "location_query", capitalization = KeyboardCapitalization.Words)`.

`PeopleEditor.kt`: `Refused` becomes `data class Refused(val message: String, val nameTaken: Boolean = false) : PeopleOutcome` (KDoc: "[nameTaken] marks the name field as well as saying it."); `saving`'s duplicate-name branch becomes `PeopleOutcome.Refused(someoneCalled(e.name), nameTaken = true)`.
`PersonEditorSheet.kt`: `PersonForm` gains `var nameError by mutableStateOf<String?>(null)` after `message`; `settle`'s refused branch:
```kotlin
            is PeopleOutcome.Refused -> {
                form.message = outcome.message
                form.nameError = outcome.message.takeIf { outcome.nameTaken }
                confirmingRemove = false
            }
```
and the message line (219), always composed:
```kotlin
            HhMessageLine(form.message, SetupType.message, c.danger, "person_message")
```
`YouStep.kt`: `YouForm` gains `var nameError by mutableStateOf<String?>(null)`; the duplicate-name branch (96-99):
```kotlin
        } catch (e: DuplicateNameException) {
            form.message = someoneCalled(e.name)
            form.nameError = form.message
            false
```
and `YouFormContent`'s message line (163) becomes `HhMessageLine(form.message, SetupType.message, c.danger, "you_message")`.
`SetupCopy.kt`, after `noTownsMatch`:
```kotlin
/** Spoken when the results change (4d-2 §4.4). */
internal fun townsFound(count: Int): String = if (count == 1) "1 town found" else "$count towns found"
```
`LocationPane.kt`, `LocationContent`'s `when` and the save-failure line become the list (when found) and one status line that is always there:
```kotlin
        if (results is TownResults.Found) {
            Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.rowGap)) {
                results.places.forEachIndexed { i, place ->
                    PlaceRow(place.label, ticked = current.isAt(place), tag = "place_$i", onClick = if (busy) null else ({ onChoose(place) }))
                }
            }
        }
        val status = statusLine(results, saveFailed)
        HhMessageLine(
            status.text,
            if (status.isSave) SetupType.message else SetupType.line,
            if (status.isError) c.danger else c.mute,
            "location_status",
            spoken = status.spoken,
        )
```
with, below `LocationContent`:
```kotlin
private class StatusLine(val text: String?, val spoken: String? = text, val isError: Boolean = false, val isSave: Boolean = false)

/** The one line under the field: a failed save first, then what the search found. */
private fun statusLine(results: TownResults, saveFailed: Boolean): StatusLine = when {
    saveFailed -> StatusLine(COULD_NOT_SAVE, isError = true, isSave = true)
    results is TownResults.NoMatch -> StatusLine(noTownsMatch(results.query))
    results is TownResults.Failed -> StatusLine(COULD_NOT_SEARCH, isError = true)
    results is TownResults.Found -> StatusLine(null, spoken = townsFound(results.places.size))
    else -> StatusLine(null)
}
```
(Each keeps today's style and colour: the search lines `SetupType.line`, the save failure `SetupType.message`.) The `location_none`, `location_failed`, `location_results` and `location_save_failed` tags go; nothing outside `LocationPaneTest` used them.
`EventEditorSheet.kt`, `FailureCard`'s `Row` modifier gains `.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }` after `.testTag("editor_failure")`.
`EventDetailSheet.kt`, `SyncingPill`'s `Row` modifier gains `.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }` after `.testTag("detail_syncing")`.

- [ ] **Step 6: Run the tests to see them pass**

Run the Step 3 command. Expected: PASS. Then every changed module's tests: `./gradlew :core:plugin:testDebugUnitTest :core:ui:testDebugUnitTest :core:access:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest :capability:weather:testDebugUnitTest :provider:calendar-google:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :app:testDebugUnitTest`.

- [ ] **Step 7: Compare, record and look at the message-line baselines**

```bash
./gradlew :core:setup:compareRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
find core/setup/build/outputs/roborazzi -name "*_compare.png" -mmin -30
```
Expected differences, each only a blank line (one line of the message style plus the column's gap) where a message would show: `person_new_dark`, `person_new_light`, `person_pin_dark`, `person_remove_dark` (above the footer or the confirm card); `you_dark`, `you_light` (under the PIN row); `location_results_dark`, `location_results_light` (under the list); `settings_location_dark`, `settings_location_light` (between the field and "Used for…"). `person_error_dark` has its message already, and `location_failed_dark` its failure line in the same place and style: neither changes. Then record and check the set with the plan's baseline check (Global Constraints):
```bash
./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
printf '%s\n' person_new_dark person_new_light person_pin_dark person_remove_dark you_dark you_light location_results_dark location_results_light settings_location_dark settings_location_light > "$TMP/expected-baselines.txt"
: > "$TMP/may-change.txt"
```
then run the check. Open every changed image.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add core app capability provider
git commit -m "Speak toasts, errors and results as they appear, assertively only for errors and refusals, and keep each text field's name once it has text"
```

---

### Task 6: Swatches, choice chips and review rows (D21, D23, D22; rulings 1, 9)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/PersonPalette.kt:7-25`, `Controls.kt:106-143` (`HhChoiceChip`), `:251-295` (`HhSwatch`, `HhSwatchGrid`), `:297-319` (`HhSwitch`)
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/PersonEditorSheet.kt:53-89, 192-207`, `steps/YouStep.kt:159`
- Modify: `capability/calendar/…/ui/EventEditorSheet.kt:232-327` (`Section`, `WhoSection`, `DaySection`, `TimeSection`), `ui/ReviewCalendars.kt:213-321`, `ui/Spoken.kt`
- Test: `core/ui/src/test/…/ControlsTest.kt`, `PersonPaletteTest.kt`; `core/setup/src/test/…/SetupButtonsTest.kt:83`, `SetupScreenshotTest.kt:104, 115, 122, 125` (the `taken` maps), `PeopleUiTest.kt`, `YouFormTest.kt`; `capability/calendar/src/test/…/ui/EventEditorSheetTest.kt`, `ReviewCalendarsTest.kt`
- Baselines: none expected to change (the Show text and switch keep their look; the review row's switch draws the same with no click of its own). Any change: stop.

**Interfaces:**
- Consumes: `hhClickable` (Task 1), `Spoken.kt` (Task 2), `assertAccessible()`.
- Produces:
  - `PersonPalette.names: List<String>` (Green, Blue, Pink, Violet, Lime, Red, Slate, Brown), `fun PersonPalette.nameOf(argb: Long): String?`, `const val FAMILY_COLOUR_NAME = "Amber"` (Task 8's legend uses them).
  - `fun HhSwatch(color: Color, name: String, chosen: Boolean, takenBy: String?, tag: String, onClick: () -> Unit)`; `fun HhSwatchGrid(chosen: Long?, taken: Map<Long, String>, tagPrefix: String, onPick: (Long) -> Unit)`.
  - `fun HhChoiceChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit, enabled: Boolean = true, selectedColor: Color = …, selectedInk: Color = …, secondary: String? = null, role: Role = Role.RadioButton, clickLabel: String? = null, stateDescription: String? = null, leading: …)` — a disabled chip stays tappable, with the state "not available" and the click label "find out why".
  - `fun HhSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, tag: String, enabled: Boolean = true)` — with `null`, it only draws.
  - `PersonForm(existing: Member?, taken: Map<Long, String>)`.
  - Calendar ui `Spoken.kt`: `ALWAYS_SHOWN`, `EXPANDED`, `COLLAPSED`, `fun changeWhoFor(calendar: String)`, `fun withSubject(action: String, subject: String)`, `fun forAccount(action: String, row: CalendarRow)`.

- [ ] **Step 1: Write the failing tests**

`PersonPaletteTest.kt`, add:
```kotlin
    @Test
    fun everyColourHasItsNameInOrderAndFamilyIsAmber() {
        assertThat(PersonPalette.colors.map { PersonPalette.nameOf(it) })
            .containsExactly("Green", "Blue", "Pink", "Violet", "Lime", "Red", "Slate", "Brown").inOrder()
        assertThat(PersonPalette.nameOf(0xFF123456)).isNull()
        assertThat(FAMILY_COLOUR_NAME).isEqualTo("Amber")
    }
```
`ControlsTest.kt`: the three swatch tests become (imports `androidx.compose.foundation.layout.FlowRow` not needed; `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.onParent`, `androidx.compose.ui.test.assertIsSelected` exist):
```kotlin
    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun aTakenSwatchIsNamedDisabledAndSaysWhoHasIt() {
        var taps = 0
        show { HhSwatch(Color(0xFF5B9BE0), "Blue", chosen = false, takenBy = "Sam", tag = "swatch") { taps++ } }
        compose.onNodeWithTag("swatch")
            .assert(radio)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Blue")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "used by Sam"))
            .assertIsNotEnabled()
            .performClick()
        assertThat(taps).isEqualTo(0)
    }

    @Test
    fun theSwatchesAreARadioGroupWithTheChosenOneSelected() {
        var picked: Long? = null
        show { HhSwatchGrid(PersonPalette.colors[0], taken = mapOf(PersonPalette.colors[2] to "Mia"), tagPrefix = "swatch_") { picked = it } }
        compose.onNodeWithTag("swatch_0").assert(radio).assertIsSelected()
        compose.onNodeWithTag("swatch_0").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        compose.onNodeWithTag("swatch_1").assertIsNotSelected().assertIsEnabled().performClick()
        assertThat(picked).isEqualTo(PersonPalette.colors[1])
        compose.assertAccessible()
    }
```
(delete `aTakenSwatchIsDisabledAndIgnoresATap` and `aChosenSwatchIsSelectedAndAFreeOneTakesATap`: the two above cover them with the names and states.) And for the chips (import `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun aChoiceChipIsARadioButtonAndADisabledOneStillExplainsItself() {
        var taps = 0
        show {
            Row {
                HhChoiceChip("Mia", selected = false, tag = "mia", onClick = { taps++ }, enabled = false)
                HhChoiceChip("Pick date…", selected = false, tag = "pick", onClick = {}, role = Role.Button)
            }
        }
        val mia = compose.onNodeWithTag("mia")
            .assert(radio)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
            .assertIsEnabled()
        assertThat(mia.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("find out why")
        mia.performClick()
        assertThat(taps).isEqualTo(1)
        compose.onNodeWithTag("pick").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
```
Fails today: swatches have no name, role, state or group; chips have no role, and a disabled one reads like any other.

`EventEditorSheetTest.kt`, add (imports `androidx.compose.ui.semantics.Role`, `SemanticsActions`, `SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.onParent`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun whoChipsAreARadioGroupAndAChildsOthersSayWhyNot() {
        show(newForm(signedIn = SampleUi.mia.id), childOnly = SampleUi.mia.id)
        compose.onNodeWithTag("who_Mia").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
        compose.onNodeWithTag("who_Mia").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        val sam = compose.onNodeWithTag("who_Sam").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
        sam.performClick()
        assertThat(calls).contains("refused")
        compose.onNodeWithTag("day_pick").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithTag("time_pick").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.assertAccessible()
    }
```
`PeopleUiTest.kt`, add:
```kotlin
    @Test
    fun thePersonSheetPassesTheSharedCheck() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("swatch_0").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "used by Alex"))
        compose.assertAccessible()
    }
```
`YouFormTest.kt`, add:
```kotlin
    @Test
    fun theYouStepPassesTheSharedCheck() {
        compose.setContent { CulveryTheme(dark = true) { YouFormContent(YouForm(), onSetPin = {}) } }
        compose.assertAccessible()
    }
```
(import `uk.co.siland.culvery.core.ui.test.assertAccessible`.)
`ReviewCalendarsTest.kt`, add (imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.state.ToggleableState`, `androidx.compose.ui.test.onNodeWithContentDescription`, `uk.co.siland.culvery.core.ui.test.assertAccessible`):
```kotlin
    @Test
    fun showIsOneNamedSwitchAndTheMainCalendarsSaysWhyItIsOff() {
        show()
        compose.onNodeWithTag("review_show_chores")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Show Chores")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        compose.onNodeWithTag("review_show_family")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "always shown: it’s the main calendar"))
    }

    @Test
    fun whoItIsForIsAButtonThatSaysWhetherItIsOpen() {
        var picking by mutableStateOf<String?>(null)
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    "Calendars", listOf(ReviewConnection(row(), listOf(swim))), listOf(Person.Family, mia), now, false, picking, null, emptyList(),
                    ReviewActions(onPick = { picking = if (picking == it) null else it }),
                )
            }
        }
        val chip = compose.onNodeWithTag("review_person_swim")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed"))
        assertThat(chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("change who Mia's swimming is for")
        chip.performClick()
        compose.onNodeWithTag("review_person_swim").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded"))
        compose.onNodeWithTag("review_pick_Mia").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    }

    @Test
    fun eachRepeatedActionNamesItsSubject() {
        show(ConnectionHealth.NeedsSignIn)
        compose.onNodeWithContentDescription("Disconnect Google Calendar, family@example.com").assertExists()
        compose.onNodeWithContentDescription("Reconnect Google Calendar, family@example.com").assertExists()
        compose.onNodeWithContentDescription("Make master, Chores").assertExists()
        compose.assertAccessible()
    }

    @Test
    fun hideNamesItsCalendar() {
        showSources(family, swim.copy(readProblem = READ_REFUSED))
        compose.onNodeWithContentDescription("Hide this calendar, Mia's swimming").assertExists()
    }
```
(imports `androidx.compose.runtime.getValue`, `mutableStateOf`, `setValue` and `androidx.compose.ui.test.onParent` if missing. "Make master" becomes "Use for new events" in Task 12, which updates this line.)
Fails today: the switch has no name; the master's has no reason; the chip has no role, label or state; the actions have no subject.

`SetupButtonsTest.kt:83` and `SetupScreenshotTest.kt:104, 115, 122, 125`: `taken = setOf(PersonPalette.colors[0])` becomes `taken = mapOf(PersonPalette.colors[0] to "Alex")`, and `setOf(PersonPalette.colors[0], PersonPalette.colors[1])` becomes `mapOf(PersonPalette.colors[0] to "Alex", PersonPalette.colors[1] to "Sam")`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :core:ui:testDebugUnitTest --tests "*ControlsTest*" --tests "*PersonPaletteTest*" :core:setup:testDebugUnitTest --tests "*PeopleUiTest*" --tests "*YouFormTest*" --tests "*SetupButtonsTest*" :capability:calendar:testDebugUnitTest --tests "*EventEditorSheetTest*" --tests "*ReviewCalendarsTest*"`
Expected: FAIL (to compile on `nameOf`, `takenBy`, the `Map`; then on the assertions).

- [ ] **Step 3: Named colours and swatches**

`PersonPalette.kt`, inside the object after `colors`:
```kotlin
    /** Each colour's name, in [colors]' order: what TalkBack says for a swatch and in the week's legend. */
    val names: List<String> = listOf("Green", "Blue", "Pink", "Violet", "Lime", "Red", "Slate", "Brown")

    /** [argb]'s name; null for a colour that isn't one of these (Family's amber is [FAMILY_COLOUR_NAME]). */
    fun nameOf(argb: Long): String? = colors.indexOf(argb).takeIf { it >= 0 }?.let(names::get)
```
and after the object:
```kotlin
/** Family's colour, by name. */
const val FAMILY_COLOUR_NAME = "Amber"
```
`Controls.kt`, `HhSwatch` and `HhSwatchGrid`:
```kotlin
/** A person colour to pick (4a design §4.4): ringed when [chosen]; [takenBy] someone else, struck through, disabled and said. */
@Composable
fun HhSwatch(color: Color, name: String, chosen: Boolean, takenBy: String?, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    val taken = takenBy != null
    val outside = ControlTokens.swatchRing + ControlTokens.swatchRingGap
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .semantics {
                contentDescription = name
                if (takenBy != null) stateDescription = usedBy(takenBy)
            }
            .size(ControlTokens.swatch + outside * 2)
            .then(if (chosen) Modifier.border(ControlTokens.swatchRing, c.ink, CircleShape) else Modifier)
            .clip(CircleShape)
            .selectable(selected = chosen, enabled = !taken, role = Role.RadioButton, onClick = onClick),
    ) {
```
(the inner `Box` is unchanged), and below it:
```kotlin
/** "used by Sam": a taken colour's state for TalkBack (4d-2 §5.2). */
private fun usedBy(name: String): String = "used by $name"

/** The person colours to pick from, a radio group, wrapping, 12 apart; swatch i is tagged [tagPrefix] + i. [taken] maps a colour to who has it. */
@Composable
fun HhSwatchGrid(chosen: Long?, taken: Map<Long, String>, tagPrefix: String, onPick: (Long) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
        verticalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
        modifier = Modifier.selectableGroup(),
    ) {
        PersonPalette.colors.forEachIndexed { i, colour ->
            HhSwatch(Color(colour), PersonPalette.names[i], chosen = chosen == colour, takenBy = taken[colour], tag = "$tagPrefix$i") { onPick(colour) }
        }
    }
}
```
(imports `androidx.compose.foundation.selection.selectable`, `androidx.compose.foundation.selection.selectableGroup`, `androidx.compose.ui.semantics.Role`, `contentDescription`, `stateDescription`; drop `selected` once unused.)
`PersonEditorSheet.kt`: `PersonForm(val existing: Member?, val taken: Map<Long, String>)`, its colour default `existing?.person?.color ?: PersonPalette.firstFree(taken.keys)`, and `showPersonEditor`'s first line:
```kotlin
    val taken = members.filter { it != existing }.associate { it.person.color to it.person.name }
```
with `PersonEditorHost(editor, existing, taken: Map<Long, String>, …)`. `YouStep.kt:159`: `taken = emptyMap()`.

- [ ] **Step 4: Choice chips**

`Controls.kt`, `HhChoiceChip`:
```kotlin
/**
 * Hand-off §7 chip: at least 48 dp; a long label takes two lines, then ends in …; padding 0 18, radius 24, 16 sp / 600;
 * `surf`/`ink`, or [selectedColor]/[selectedInk] when selected. One of a radio group by default ([role]); a chip that
 * opens something is `Role.Button`. A disabled chip is drawn at 38% but stays tappable, so a tap can explain why; TalkBack
 * hears "not available" and "double-tap to find out why".
 */
@Composable
fun HhChoiceChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selectedColor: Color = Culvery.colors.accent,
    selectedInk: Color = Culvery.colors.accentInk,
    secondary: String? = null,
    role: Role = Role.RadioButton,
    clickLabel: String? = null,
    stateDescription: String? = null,
    leading: (@Composable (ink: Color) -> Unit)? = null,
) {
    val c = Culvery.colors
    val ink = if (selected) selectedInk else c.ink
    val state = stateDescription ?: NOT_AVAILABLE.takeIf { !enabled }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipIconGap),
        modifier = Modifier
            .testTag(tag)
            .semantics {
                // A button chip that isn't holding a value says nothing about selection.
                if (role != Role.Button || selected) this.selected = selected
                if (state != null) this.stateDescription = state
            }
            .alpha(if (enabled) 1f else ControlTokens.DISABLED_ALPHA)
            .heightIn(min = ControlTokens.chipHeight)
            .clip(RoundedCornerShape(ControlTokens.chipRadius))
            .background(if (selected) selectedColor else c.surf)
            // Never `enabled = false`: that would stop the explaining tap (2b-2 design §4.2).
            .hhClickable(onClick, label = clickLabel ?: FIND_OUT_WHY.takeIf { !enabled }, role = role)
            .padding(horizontal = ControlTokens.chipPaddingH),
    ) {
        leading?.invoke(ink)
        Text(label, style = ControlType.chip, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (secondary != null) {
            Text(secondary, style = ControlType.chipSecondary, color = ink, maxLines = 1)
        }
    }
}

// What TalkBack says for a disabled chip that a tap explains (4d-2 §5.4).
private const val NOT_AVAILABLE = "not available"
private const val FIND_OUT_WHY = "find out why"
```
(import `androidx.compose.ui.semantics.stateDescription`; drop the `clickable` import when nothing else uses it.)
`EventEditorSheet.kt`: `Section`'s `FlowRow` gains `modifier = Modifier.selectableGroup(),`; the "Pick date…" chip (295-301) and "Pick time…" chip (319-325) gain `role = Role.Button,`. (imports `androidx.compose.foundation.selection.selectableGroup`, `androidx.compose.ui.semantics.Role`.)
`PersonEditorSheet.kt:199`, the role chips' `Column` gains `modifier = Modifier.selectableGroup()`.

- [ ] **Step 5: The switch and the review rows**

`Controls.kt`, `HhSwitch`'s parameter becomes `onCheckedChange: ((Boolean) -> Unit)?` and its KDoc gains: "With [onCheckedChange] null it only draws: the row around it is the switch TalkBack and the finger use."
`Spoken.kt`, add:
```kotlin
internal const val ALWAYS_SHOWN = "always shown: it’s the main calendar"
internal const val EXPANDED = "expanded"
internal const val COLLAPSED = "collapsed"

internal fun changeWhoFor(calendar: String): String = "change who $calendar is for"

/** A repeated action's spoken name: its visible words first, then what it acts on ("Hide this calendar, School terms"). */
internal fun withSubject(action: String, subject: String): String = "$action, $subject"

/** "Disconnect Google Calendar, alex@example.com", or "Disconnect Google Calendar" when the connection names no account. */
internal fun forAccount(action: String, row: CalendarRow): String =
    row.connection.config[CONFIG_ACCOUNT]?.let { "$action ${row.service}, $it" } ?: "$action ${row.service}"
```
(imports `uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT`, `uk.co.siland.culvery.capability.calendar.CalendarRow`.)
`ReviewCalendars.kt`, `ConnectionHeader`'s two buttons gain their spoken names:
```kotlin
            if (needsReconnect) {
                HhButton(
                    "Reconnect", { onReconnect(row.connection) }, Modifier.testTag("settings_reconnect"),
                    tone = ButtonTone.Primary, enabled = !busy, on = ButtonSurface.Surf, spokenName = forAccount("Reconnect", row),
                )
            }
            HhButton(
                DISCONNECT, { onDisconnect(row) }, Modifier.testTag("review_disconnect_${row.connection.id}"),
                enabled = !busy, on = ButtonSurface.Surf, spokenName = forAccount(DISCONNECT, row),
            )
```
`SourceRow`'s `trailing` becomes:
```kotlin
        trailing = {
            HhChoiceChip(
                person.name,
                selected = false,
                tag = "review_person_$id",
                onClick = { onPick(sourceKey(source)) },
                role = Role.Button,
                clickLabel = changeWhoFor(source.source.name),
                stateDescription = if (picking) EXPANDED else COLLAPSED,
                leading = { _ -> Dot(person) },
            )
            // Text and switch are one switch: named for the calendar, with why the main one can't be hidden (4a design §3.9).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reviewItemGap),
                modifier = Modifier
                    .testTag("review_show_$id")
                    .semantics {
                        contentDescription = "$SHOW ${source.source.name}"
                        if (source.isMaster) stateDescription = ALWAYS_SHOWN
                    }
                    .toggleable(
                        value = source.mapping.visible,
                        enabled = !source.isMaster && !busy,
                        role = Role.Switch,
                        onValueChange = { onShown(source, it) },
                    ),
            ) {
                Text(SHOW, style = CalendarType.subtitle, color = c.mute)
                HhSwitch(source.mapping.visible, onCheckedChange = null, tag = "review_show_switch_$id", enabled = !source.isMaster && !busy)
            }
        },
```
Make master (line 274): `HhButton(MAKE_MASTER, { onMakeMaster(source) }, Modifier.testTag("review_make_master_$id"), enabled = !busy, on = ButtonSurface.Surf, spokenName = withSubject(MAKE_MASTER, source.source.name))`.
`ReviewCalendars`'s `ReadProblem` Hide (318): `HhButton(HIDE_THIS_CALENDAR, { onShown(source, false) }, Modifier.testTag("review_hide_$id"), enabled = !busy, on = ButtonSurface.Surf, spokenName = withSubject(HIDE_THIS_CALENDAR, source.source.name))`.
The person picker's `FlowRow` (279) gains `modifier = Modifier.selectableGroup(),`.
(imports `androidx.compose.foundation.selection.selectableGroup`, `androidx.compose.foundation.selection.toggleable`, `androidx.compose.ui.semantics.Role`, `contentDescription`, `semantics`, `stateDescription`.) The Show text and switch were 12 dp apart as `HhListRow` trailing items; `CalendarDimens.reviewItemGap` is 12 dp too, so nothing moves.

- [ ] **Step 6: Run the tests, then compare**

Run the Step 2 command. Expected: PASS. Then `./gradlew :core:ui:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest`.
Compare (nothing should change):
```bash
./gradlew :capability:calendar:compareRoborazziDebug --tests "*ReviewScreenshotTest*" --tests "*EditorScreenshotTest*"
./gradlew :core:setup:compareRoborazziDebug --tests "*SetupScreenshotTest*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Expected: no `*_compare.png`. Any difference is a stop: find what moved rather than re-recording.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add core/ui core/setup capability/calendar
git commit -m "Name the colour swatches and say who has a colour, make choice chips radio groups that explain a disabled chip, and name the review rows' switch, chip and actions"
```

---

### Task 7: Pickers, the PIN pad, wizard dots, Move up and Move down (D24, D25, D27, D28; ruling 8)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/…/ui/Pickers.kt:129-134` (range line), `:207-225` (`DateCell`), `:244-251` (`Stepper`'s value), `ui/Spoken.kt`
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt:84-107` (`PinPadSheet`), `:156-230` (`PinPadFrame`'s message and dots), `:276-291` (`Dots`)
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/WizardRules.kt:35-38` (deleted), `SetupWizard.kt:135-136` and `:202-221` (`Dots`), `PeopleUi.kt:39-52, 55-93`, `SetupCopy.kt` (after `moveDownLabel`)
- Test: `capability/calendar/src/test/…/ui/PickersTest.kt`; `core/access/src/test/…/ui/PinPadTest.kt`, `ChoosePinPadTest.kt`; `core/setup/src/test/…/WizardRulesTest.kt:68-75` (deleted), `SetupWizardTest.kt:155-160`, `WizardDotsTest.kt`, `PeopleUiTest.kt:94-105`
- Baselines (changed): `settings_people_dark`, `settings_people_light` (the faint arrows at the list's ends)

**Interfaces:**
- Consumes: `FULL_DATE`, `Spoken.kt` (Tasks 2–4), `hhClickable`, `assertAccessible()`.
- Produces:
  - Calendar ui `Spoken.kt`: `internal fun dateCellName(date: LocalDate, today: LocalDate): String` ("Tuesday 7 October, today").
  - `PinPad.kt`: `internal const val WRONG_PIN = "Wrong PIN — try again"` and `internal fun tooManyTries(seconds: Int): String` (Task 14 gives both full words and a full stop); `internal fun digitsEntered(count: Int): String` ("2 of 4 digits entered"); `PinPadFrame(…, spokenMessage: String = message)`; the message sits in a `pin_message` box that is the live region; the visible `pin_error` text is hidden from TalkBack.
  - `WizardRules.dotCount` and `dotIndex` go: the frame gets `statuses.size` and the step's own index; `internal fun stepOf(index: Int, count: Int): String` ("Step 3 of 7") in `SetupCopy.kt`.
  - `internal const val ALREADY_FIRST = "already first"`, `ALREADY_LAST = "already last"` in `SetupCopy.kt`.

- [ ] **Step 1: Write the failing tests**

`PickersTest.kt`, add (imports `androidx.compose.ui.semantics.LiveRegionMode`, `SemanticsProperties`, `androidx.compose.ui.test.assertIsSelected`, `androidx.compose.ui.test.onNodeWithContentDescription`):
```kotlin
    @Test
    fun aDateCellSaysItsDayAndWhetherItIsToday() {
        showDates(selected = today.plusDays(1))
        compose.onNodeWithTag("date_cell_$today")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Wednesday 23 September, today")))
            .assert(isButton)
        compose.onNodeWithContentDescription("Thursday 24 September").assertIsSelected()
        compose.assertAccessible()
    }

    /** TalkBack's double-tap is the semantics action: it must pick the day as a touch does. */
    @Test
    fun aDateCellIsPickedFromTalkBack() {
        showDates()
        compose.onNodeWithTag("date_cell_${today.plusDays(2)}").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(picked).containsExactly(today.plusDays(2))
    }

    @Test
    fun theRangeLineIsSpokenWhenItChanges() {
        showDates()
        compose.onNodeWithTag("date_next").performClick()
        compose.onNodeWithTag("date_range")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextEquals("Mon 26 Oct – Sun 29 Nov")
    }

    @Test
    fun eachStepperValueIsSpokenWithItsNameAndNoLeadingZero() {
        showTime(LocalTime.of(7, 0))
        compose.onNodeWithTag("hour_value")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hour 7")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_value").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hour 8")))
        compose.onNodeWithTag("minute_value").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Minute 0")))
    }
```
(imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.performSemanticsAction`; `picked` is the test's list of dates `onPick` received.)
Fails today: cells read "23" with no weekday, today or role; the range and the values aren't live regions and the values read "07". `aDateCellIsPickedFromTalkBack` is a **pin** (the cell is a plain `clickable` today): it fails if the cell's semantics action stops picking the day.

`PinPadTest.kt`, add (imports `androidx.compose.ui.semantics.LiveRegionMode`, `SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`):
```kotlin
    private fun description(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

    @Test
    fun theDotsSayHowManyDigitsAreInAndSpeakEachOne() {
        controller.open("Change settings", null, null)
        show()
        assertThat(description("pin_dots")).isEqualTo("0 of 4 digits entered")
        compose.onNodeWithTag("pin_dots").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        tap("1")
        assertThat(description("pin_dots")).isEqualTo("1 of 4 digits entered")
    }

    @Test
    fun aWrongPinIsAnErrorOnTheDotsAndItsMessageIsALiveRegion() {
        controller.open("Change settings", PinError.WrongPin, null)
        show()
        compose.onNodeWithTag("pin_dots").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Wrong PIN — try again"))
        assertThat(description("pin_message")).isEqualTo("Wrong PIN — try again")
        compose.onNodeWithTag("pin_message").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    /** How many times TalkBack speaks it is the emulator's to hear (Task 17); here, its spoken text never ticks. */
    @Test
    fun theLockoutsSpokenTextStaysFixedWhileItsSecondsTick() {
        compose.mainClock.autoAdvance = false
        // The pad takes the lockout's first value from the wall clock, as the existing lockout tests do; the ticking runs on the test clock.
        controller.open("Change settings", PinError.WrongPin, System.currentTimeMillis() + 30_000)
        show()
        compose.mainClock.advanceTimeByFrame()
        val spoken = description("pin_message")
        val shown = compose.onNodeWithTag("pin_error", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        compose.mainClock.advanceTimeBy(3_000)
        assertThat(description("pin_message")).isEqualTo(spoken)
        assertThat(compose.onNodeWithTag("pin_error", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
            .isNotEqualTo(shown)
        assertThat(spoken).startsWith("Too many tries")
    }
```
`ChoosePinPadTest.aMismatchStartsAgainAndSaysSo`, after its `PINS_DIDNT_MATCH` assertion: `compose.onNodeWithTag("pin_dots").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Those PINs didn't match — try again."))` (imports as above).
Fails today: the dots are four nameless boxes; the message line is plain text whose countdown is its only spoken content.

`WizardRulesTest.kt`: delete `theDotsCountOnlyShownSteps` (68-75) with the two functions it tests (Step 5); `SetupWizardTest` below covers the count and the place.
`SetupWizardTest.kt:155-160` becomes:
```kotlin
    @Test
    fun itOpensAtTheFirstStepNotDoneWithADotForEveryStep() {
        show(FakeStep("a", 0, done = true), FakeStep("b", 1), FakeStep("c", 2, shown = false), FakeStep("d", 3))
        compose.awaitText("Step b")
        compose.onAllNodesWithTag("wizard_dot").assertCountEquals(4)
        compose.onNodeWithTag("wizard_dots").assertContentDescriptionEquals("Step 2 of 4")
    }
```
(import `androidx.compose.ui.test.assertContentDescriptionEquals`.)
`WizardDotsTest.kt`, add (imports `androidx.compose.ui.test.assertContentDescriptionEquals`, `androidx.compose.ui.test.onNodeWithTag`):
```kotlin
    @Test
    fun theDotsAreOneSpokenStep() {
        compose.setContent {
            CulveryTheme(dark = false) {
                WizardFrame(7, 0, back = null, forward = Forward.Next("Start", enabled = true), busy = false, onForward = {}, onSkip = {}) {}
            }
        }
        compose.onNodeWithTag("wizard_dots").assertContentDescriptionEquals("Step 1 of 7")
    }
```
Fails today: the dots count shown steps only and have no name.

`PeopleUiTest.kt`, `theMoveButtonsMoveSomeoneAndNoneGoesPastAnEnd` (94-105) becomes (imports `androidx.compose.ui.semantics.SemanticsActions`, `SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assertIsNotEnabled`):
```kotlin
    @Test
    fun theMoveButtonsMoveSomeoneAndTheEndsSayWhyTheyAreOff() {
        addSam()
        show()
        compose.awaitTag("person_up_Sam")
        compose.onNodeWithTag("person_up_Alex").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "already first"))
        compose.onNodeWithTag("person_down_Sam").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "already last"))
        compose.onNodeWithContentDescription("Move Sam up").performClick()
        compose.waitUntil(5_000) { runBlocking { household.people.first().map { it.name } } == listOf("Sam", "Alex") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_up_Sam").fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled) == true }
        compose.onNodeWithTag("person_down_Sam").assertIsEnabled()
    }

    /** 4d-2 §5.8: the row is keyed by the person, so TalkBack's focus moves with them, not with the position. */
    @Test
    fun movingSomeoneKeepsTheirRowAndItsActions() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        val samRow = compose.onNodeWithTag("person_Sam").fetchSemanticsNode()
        assertThat(samRow.config[SemanticsActions.CustomActions].map { it.label }).containsExactly("Move Sam up")
        compose.onNodeWithContentDescription("Move Sam up").performClick()
        // singleOrNull: a fetch mid-recomposition must not throw out of waitUntil.
        fun top(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.top
        compose.waitUntil(5_000) {
            val sam = top("person_Sam")
            val alex = top("person_Alex")
            sam != null && alex != null && sam < alex
        }
        assertThat(compose.onNodeWithTag("person_Sam").fetchSemanticsNode().id).isEqualTo(samRow.id)
        assertThat(compose.onNodeWithTag("person_Sam").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })
            .containsExactly("Move Sam down")
    }
```
Fails today: the ends are `Spacer`s (no node); rows aren't keyed (Sam's tag moves to the first position's node, so its id changes); no custom actions.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :capability:calendar:testDebugUnitTest --tests "*PickersTest*" :core:access:testDebugUnitTest --tests "*PinPadTest*" --tests "*ChoosePinPadTest*" :core:setup:testDebugUnitTest --tests "*WizardRulesTest*" --tests "*SetupWizardTest*" --tests "*WizardDotsTest*" --tests "*PeopleUiTest*"`
Expected: FAIL as listed.

- [ ] **Step 3: The pickers**

`Spoken.kt`, add:
```kotlin
/** "Tuesday 7 October, today": a date cell's name (its selected state is the tree's). */
internal fun dateCellName(date: LocalDate, today: LocalDate): String = dayHeading(date) + if (date == today) ", today" else ""
```
`Pickers.kt`, the range line (129-134) gains a polite live region:
```kotlin
                Text(
                    pageRangeLabel(start),
                    style = CalendarType.pickerRange,
                    color = c.mute,
                    modifier = Modifier
                        .padding(top = CalendarDimens.pickerRangeTop)
                        .testTag("date_range")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
```
`DateCell`:
```kotlin
@Composable
private fun DateCell(date: LocalDate, today: LocalDate, selected: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    val c = Culvery.colors
    val isSelected = date == selected
    val shape = RoundedCornerShape(CalendarDimens.dateCellRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("date_cell_$date")
            .alpha(if (date.isBefore(today) && !isSelected) HhAlpha.PAST_DAY else 1f)
            .height(CalendarDimens.dateCellHeight)
            .clip(shape)
            .background(if (isSelected) c.accent else c.surf2)
            .then(if (date == today) Modifier.border(CalendarDimens.dateRing, c.accent, shape) else Modifier)
            .hhClickable(onClick)
            // After the click, so it clears only the number inside; one click serves a touch and TalkBack alike.
            .clearAndSetSemantics {
                contentDescription = dateCellName(date, today)
                selected = isSelected
            },
    ) {
        Text(dateCellLabel(date), style = CalendarType.pickerCell, color = if (isSelected) c.accentInk else c.ink, maxLines = 1)
    }
}
```
`Stepper`'s value line:
```kotlin
        Text(
            value.toString().padStart(2, '0'),
            style = CalendarType.timeValue,
            color = c.ink,
            // "Hour 7", not "zero seven"; spoken as it changes.
            modifier = Modifier.testTag("${tag}_value").semantics {
                contentDescription = "$name $value"
                liveRegion = LiveRegionMode.Polite
            },
        )
```
(imports `androidx.compose.ui.semantics.LiveRegionMode`, `clearAndSetSemantics`, `contentDescription`, `liveRegion`, `selected`, `semantics`.)

- [ ] **Step 4: The PIN pad's dots and message**

`PinPad.kt`, above `PinPadSheet`:
```kotlin
internal const val WRONG_PIN = "Wrong PIN — try again"

internal fun tooManyTries(seconds: Int): String = "Too many tries — wait ${seconds}s"

/** "2 of 4 digits entered": the dots, spoken (4d-2 §5.5). */
internal fun digitsEntered(count: Int): String = "$count of ${PinHasher.PIN_LENGTH} digits entered"
```
`PinPadSheet` (84-107):
```kotlin
    // Counts down from the initial value rather than re-reading the wall clock, so tests with a
    // virtual frame clock stay deterministic.
    val firstSeconds = remember(lockedUntilMillis) { secondsUntil(lockedUntilMillis) }
    val secondsLeft by produceState(firstSeconds, lockedUntilMillis) {
        while (value > 0) {
            delay(1_000)
            value -= 1
        }
    }
    val locked = secondsLeft > 0
    val message = when {
        locked -> tooManyTries(secondsLeft)
        error is PinError.WrongPin -> WRONG_PIN
        error is PinError.NotAllowed -> error.message
        else -> ""
    }
    PinPadFrame(
        title = "Who's this?",
        line = pinReasonText(reason, label),
        message = message,
        // Said once, from the lockout's first value: a line read every second would drown everything else.
        spokenMessage = if (locked) tooManyTries(firstSeconds) else message,
        wrongPin = error is PinError.WrongPin,
        enabled = !locked,
        onSubmit = onSubmit,
        onCancel = onCancel,
        overSheet = overSheet,
        paneTitle = ENTER_YOUR_PIN,
    )
```
`PinPadFrame` gains `spokenMessage: String = message` (after `message`); the dots and message lines (218-229) become:
```kotlin
                Dots(filled = digits.length, error = if (wrongPin && digits.isEmpty()) message else null)
                Box(
                    Modifier
                        .testTag("pin_message")
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            if (spokenMessage.isNotEmpty()) contentDescription = spokenMessage
                        },
                ) {
                    Text(
                        message,
                        style = PinPadType.error,
                        color = c.danger,
                        // Two lines kept at every font scale, so the keypad doesn't jump when a message appears.
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        // The box above speaks it; this line's own text may tick.
                        modifier = Modifier.testTag("pin_error").semantics { hideFromAccessibility() },
                    )
                }
```
`Dots`:
```kotlin
/** [error], when set, rings the empty dots in `danger` and is read with them. */
@Composable
private fun Dots(filled: Int, error: String?) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(PinPadDimens.dotGap),
        modifier = Modifier.testTag("pin_dots").clearAndSetSemantics {
            contentDescription = digitsEntered(filled)
            liveRegion = LiveRegionMode.Polite
            if (error != null) error(error)
        },
    ) {
        repeat(PinHasher.PIN_LENGTH) { i ->
            val dot = Modifier.size(PinPadDimens.dot).clip(CircleShape)
            Box(
                if (i < filled) {
                    dot.background(c.ink)
                } else {
                    dot.border(PinPadDimens.dotBorder, if (error != null) c.danger else c.mute, CircleShape)
                },
            )
        }
    }
}
```
(imports `androidx.compose.ui.semantics.LiveRegionMode`, `clearAndSetSemantics`, `contentDescription`, `error`, `hideFromAccessibility`, `liveRegion`, `semantics`.)

- [ ] **Step 5: Wizard dots, Move up and Move down**

`WizardRules.kt`: delete `dotCount` and `dotIndex` (35-38) and the blank line before them. `SetupWizard.kt:135-136` become:
```kotlin
        // Every step, shown or not, so the total doesn't grow when Review calendars appears (4d-2 §5.7).
        dotCount = statuses.size,
        dotIndex = index,
```
`SetupCopy.kt`, after `moveDownLabel`:
```kotlin
internal const val ALREADY_FIRST = "already first"
internal const val ALREADY_LAST = "already last"

/** The wizard's dots, spoken: "Step 3 of 7". */
internal fun stepOf(index: Int, count: Int): String = "Step ${index + 1} of $count"
```
`SetupWizard.kt`, `Dots` (202-221): the `Row`'s modifier gains `.semantics { contentDescription = stepOf(index, count) }` after `.testTag("wizard_dots")`, and each dot's `.semantics { selected = current }` becomes `.semantics { hideFromAccessibility() }` (the row speaks for them; `clearAndSetSemantics` would remove the `wizard_dot` nodes that `WizardDotsTest` reads pixels through). Imports `contentDescription`, `hideFromAccessibility`; drop `selected`.
`PeopleUi.kt`, `PeopleList`'s loop (39-52):
```kotlin
        members.forEachIndexed { i, member ->
            // Keyed by the person, so a move moves this row's node, and TalkBack's focus with it.
            key(member.person.id) {
                PersonRow(
                    member,
                    reorderable = onMove != null,
                    onUp = if (onMove != null && i > 0) ({ onMove(member, true) }) else null,
                    onDown = if (onMove != null && i < members.lastIndex) ({ onMove(member, false) }) else null,
                    onClick = { onEdit(member) },
                )
            }
        }
```
`PersonRow` (55-78):
```kotlin
@Composable
internal fun PersonRow(
    member: Member,
    reorderable: Boolean = false,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = Culvery.colors
    val name = member.person.name
    val moves = listOfNotNull(
        onUp?.let { up -> CustomAccessibilityAction(moveUpLabel(name)) { up(); true } },
        onDown?.let { down -> CustomAccessibilityAction(moveDownLabel(name)) { down(); true } },
    )
    HhListRow(
        title = name,
        modifier = Modifier.testTag("person_$name").semantics { if (moves.isNotEmpty()) customActions = moves },
        leading = { Box(Modifier.size(SetupDimens.rowDot).clip(CircleShape).background(Color(member.person.color))) },
        subtitle = "${roleName(member.role)} · ${if (member.hasPin) PIN_SET else NO_PIN}",
        trailing = {
            if (reorderable) {
                MoveButton(Icons.ARROW_UPWARD, moveUpLabel(name), "person_up_$name", onUp, atEnd = ALREADY_FIRST)
                MoveButton(Icons.ARROW_DOWNWARD, moveDownLabel(name), "person_down_$name", onDown, atEnd = ALREADY_LAST)
            }
            HhIcon(Icons.CHEVRON_RIGHT, size = ListRowTokens.icon, tint = c.mute)
        },
        onClick = onClick,
        clickLabel = changeDetailsLabel(name),
    )
}

/** A Move up or Move down button; at the list's ends disabled and faint, saying why ([atEnd]), so focus has a place to stay. */
@Composable
private fun MoveButton(icon: String, label: String, tag: String, onClick: (() -> Unit)?, atEnd: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(SetupDimens.moveButton)
            .alpha(if (onClick == null) ControlTokens.DISABLED_ALPHA else 1f)
            .clip(CircleShape)
            .then(if (onClick == null) Modifier.semantics { stateDescription = atEnd } else Modifier)
            .hhClickable(onClick ?: {}, enabled = onClick != null),
    ) {
        HhIcon(icon, size = ListRowTokens.icon, tint = Culvery.colors.ink, contentDescription = label)
    }
}
```
(imports `androidx.compose.runtime.key`, `androidx.compose.ui.draw.alpha`, `androidx.compose.ui.semantics.CustomAccessibilityAction`, `customActions`, `semantics`, `stateDescription`, `uk.co.siland.culvery.core.ui.ControlTokens`; drop `Spacer`.)

- [ ] **Step 6: Run the tests, then compare and re-record**

Run the Step 2 command. Expected: PASS. Then `./gradlew :capability:calendar:testDebugUnitTest :core:access:testDebugUnitTest :core:setup:testDebugUnitTest :app:testDebugUnitTest`. Tests that find "Wrong PIN — try again" or "Those PINs didn't match — try again." by text still find the `pin_error` line.
Compare:
```bash
./gradlew :core:setup:compareRoborazziDebug --tests "*SettingsScreenshotTest*" --tests "*SetupScreenshotTest*"
./gradlew :capability:calendar:compareRoborazziDebug --tests "*EditorScreenshotTest*"
./gradlew :app:compareRoborazziDebug --tests "*ShellScreenshotTest*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Expected: `settings_people_dark` and `settings_people_light` only: a faint up arrow on the first row and a faint down arrow on the last, where there was a gap; nothing else moves. The wizard images, `editor_*_picker_*` and `pin_pad_*` don't change (fixed dot counts in the fixtures; semantics only). Anything else: stop. Then:
```bash
./gradlew :core:setup:recordRoborazziDebug --tests "*SettingsScreenshotTest*"
git status --short -- "*.png"
```

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar core/access core/setup
git commit -m "Speak date cells, stepper values and the PIN dots, say the lockout once, name the wizard's step, and let focus follow a moved person"
```

---

### Task 8: Week and Home semantics; the Forecast row test (D26; ruling 21)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt:83-98` (`Badge`), `:128-133` (a weekday formatter), after `sliceOn` (`:208`) a `spokenTime`
- Modify: `capability/calendar/…/ui/Badges.kt:15-27`, `ui/Spoken.kt`, `ui/WeekView.kt:226-232, 253-306` (`DayColumn`'s items, `EventChip`, `Legend`), `ui/TodayCard.kt:93-119`, `ui/ComingUpCard.kt:96-118` and its `DayColumn`, `ui/EventDetailSheet.kt:242-255` (`InfoRow`)
- Modify: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherWords.kt` (after `rowDescription`), `ui/WeatherHeaderItem.kt:21-35`
- Test: `capability/calendar/src/test/…/SpokenTimeTest.kt` (create), `ui/WeekViewTest.kt:92-97, 146-155`, `ui/CardsTest.kt:91-95, 152-160`, `ui/EventDetailSheetTest.kt`, `ui/FontScaleTest.kt:143`; `capability/weather/src/test/…/WeatherWordsTest.kt`, `ui/ForecastCardTest.kt`, `ui/WeatherHeaderItemTest.kt` (create)
- Baselines: none change.

**Interfaces:**
- Consumes: `FULL_DATE`, `dayHeading`, `OPEN_EVENT_DETAILS` (Tasks 2–3), `PersonPalette.nameOf`, `FAMILY_COLOUR_NAME` (Task 6).
- Produces:
  - `enum class Badge(val icon: String, val spoken: String)` — `Syncing("saving")`, `OtherCalendar("can’t change here")`, `Repeats("repeats")`; `description` goes (nothing shows it: the icons are named by their row now).
  - `internal fun spokenTime(label: String): String` and `internal val FULL_WEEKDAY: DateTimeFormatter` ("EEEE", `Locale.ENGLISH`) in `CalendarUi.kt`.
  - Calendar ui `Spoken.kt`: `internal fun eventOnDay(event: EventUi, day: LocalDate): String` ("Swimming, Sam, Tuesday 09:00, repeats"), `internal fun todayRowName(event: EventUi): String` ("Swimming, Sam, 09:00 to 10:30"), `internal fun legendName(people: List<Person>): String`.
  - Weather: `internal fun headerDescription(weather: HeaderWeather): String` ("Now 17°, partly cloudy, high 19°, low 11°").

- [ ] **Step 1: Write the failing tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/SpokenTimeTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpokenTimeTest {
    @Test
    fun aTimeIsReadAsAPersonWouldSayIt() {
        assertThat(spokenTime("09:00")).isEqualTo("09:00")
        assertThat(spokenTime("09:00–10:30")).isEqualTo("09:00 to 10:30")
        assertThat(spokenTime("22:00–")).isEqualTo("from 22:00")
        assertThat(spokenTime("until 01:00")).isEqualTo("until 01:00")
        assertThat(spokenTime(ALL_DAY_LABEL)).isEqualTo("all day")
    }
}
```
Fails today: no `spokenTime`.

`WeekViewTest.kt`, add (imports `androidx.compose.ui.test.onAllNodesWithContentDescription` exists; `androidx.compose.ui.test.onNodeWithContentDescription`):
```kotlin
    @Test
    fun aChipSaysWhatWhoWhenAndItsBadgesAsOneItem() {
        show { WeekView(state()) }
        compose.onNodeWithContentDescription("Swimming, Mia, Wednesday 16:00, repeats").assert(isButton)
        compose.onNodeWithContentDescription("Dinner with Jo & Priya, Alex, Wednesday 19:30").assertExists()
        compose.onNodeWithContentDescription("Colours: Alex, green; Sam, blue; Mia, pink; Family, amber").assertExists()
    }
```
and update the three badge tests and the legend test to the spoken words (the icons no longer carry their own):
- `legendNamesEveryoneAndFamily` (92-97): `compose.onNodeWithContentDescription("Colours: Alex, green; Sam, blue; Mia, pink; Family, amber").assertExists()`
- `recurringChipsShowTheRepeatBadge`: `compose.onAllNodesWithContentDescription(", repeats", substring = true).assertCountEquals(3)`
- `insetDayFromTheSchoolFeedShowsTheLockBadge`: `compose.onAllNodesWithContentDescription(", can’t change here", substring = true).assertCountEquals(1)`
- `aSyncingChipShowsCloudUpload`: `compose.onAllNodesWithContentDescription(", saving", substring = true).assertCountEquals(1)`
Fails today: chips read their two texts and badge icons apart; the legend is eight nodes.

`CardsTest.kt`, add (imports `androidx.compose.ui.test.onNodeWithContentDescription`):
```kotlin
    @Test
    fun todayAndComingUpRowsAreEachOneSpokenItem() {
        show { TodayCard(SampleUi.todayWithBadges) }
        compose.onNodeWithContentDescription("Dinner with Jo & Priya, Alex, 19:30 to 21:00, saving").assert(isButton)
    }

    @Test
    fun aComingUpRowSaysItsDayAndIsNotAButton() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithContentDescription("Office day, Alex, Thursday 09:00")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }
```
and update `recurringTodayRowShowsTheRepeatBadge` to `compose.onAllNodesWithContentDescription(", repeats", substring = true).assertCountEquals(1)` and `todayRowsShowEachOfTheirBadges` to:
```kotlin
        compose.onAllNodesWithContentDescription(", saving", substring = true).assertCountEquals(2)
        compose.onAllNodesWithContentDescription(", can’t change here", substring = true).assertCountEquals(1)
        compose.onAllNodesWithContentDescription(", repeats", substring = true).assertCountEquals(1)
```
`EventDetailSheetTest.kt`, add (imports `androidx.compose.ui.test.hasText`):
```kotlin
    @Test
    fun eachDetailRowReadsItsLabelAndValueTogether() {
        show(SampleUi.detailEditable)
        compose.onNode(hasText("When") and hasText("Today · 19:30–21:00")).assertExists()
    }
```
`FontScaleTest.kt:143` becomes `compose.onNodeWithTag("badge_Repeats", useUnmergedTree = true).assertIsDisplayed()`.
`WeatherWordsTest.kt`, add:
```kotlin
    @Test
    fun theHeaderIsOneSentence() {
        assertThat(headerDescription(HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.2, high = 19.0, low = 10.6)))
            .isEqualTo("Now 17°, partly cloudy, high 19°, low 11°")
    }
```
Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/WeatherHeaderItemTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class WeatherHeaderItemTest {
    @get:Rule val compose = createComposeRule()

    /** A9: one item that says the condition, not "17°" and "High 19° · Low 11°" apart. */
    @Test
    fun theHeaderIsOneItemThatSaysTheCondition() {
        compose.setContent { CulveryTheme(dark = true) { WeatherHeaderItem(HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0)) } }
        compose.onNodeWithTag("weather_header").assertContentDescriptionEquals("Now 17°, partly cloudy, high 19°, low 11°")
        compose.onAllNodesWithText("°", substring = true).assertCountEquals(1)
    }
}
```
`ForecastCardTest.kt`, add (imports `androidx.compose.ui.test.onChildren`):
```kotlin
    /** The 4d-1 follow-up and A10: a row is one item, with nothing of its own left to read. */
    @Test
    fun aForecastRowHasNoChildrenAndNoDegreesAreReadApart() {
        show(READY)
        compose.onAllNodesWithTag("forecast_row")[0].onChildren().assertCountEquals(0)
        compose.onAllNodesWithText("°", substring = true).assertCountEquals(0)
    }
```
That last test is a **pin**: the Forecast rows already clear their children; it fails if a row's day label, icon or temperatures come back as items.
The rest fail today: no merged descriptions, no `headerDescription`, the detail rows are two items each, and the badge icon has no tag.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :capability:calendar:testDebugUnitTest --tests "*SpokenTimeTest*" --tests "*WeekViewTest*" --tests "*CardsTest*" --tests "*EventDetailSheetTest*" --tests "*FontScaleTest*" :capability:weather:testDebugUnitTest --tests "*WeatherWordsTest*" --tests "*WeatherHeaderItemTest*" --tests "*ForecastCardTest*"`
Expected: FAIL as listed; `aForecastRowHasNoChildrenAndNoDegreesAreReadApart` passes.

- [ ] **Step 3: Spoken times and badge words**

`CalendarUi.kt`, `Badge` (83-88):
```kotlin
/** Hand-off §7 chip and row badges, in the order they show; [spoken] follows the row's words (", repeats"). */
enum class Badge(val icon: String, val spoken: String) {
    Syncing(Icons.CLOUD_UPLOAD, "saving"),
    OtherCalendar(Icons.LOCK, "can’t change here"),
    Repeats(Icons.REPEAT, "repeats"),
}
```
after `DAY_AND_MONTH` (beside Task 2's `FULL_DATE`):
```kotlin
internal val FULL_WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
```
after `sliceOn` (line 208):
```kotlin
/** How TalkBack reads a slice's time (4d-2 §5.6): "09:00 to 10:30", "from 22:00", "until 01:00", "all day"; a dash isn't read well. */
internal fun spokenTime(label: String): String = when {
    label == ALL_DAY_LABEL -> "all day"
    label.endsWith("–") -> "from ${label.dropLast(1)}"
    else -> label.replace("–", " to ")
}
```
`Badges.kt`, the icons lose their descriptions and gain tags:
```kotlin
/** Hand-off §7 badges, muted: 15 dp on week chips, 20 dp on Today rows. Nothing when there are none. The row speaks them. */
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
        badges.forEach { HhIcon(it.icon, size = size, tint = c.mute, modifier = Modifier.testTag("badge_${it.name}")) }
    }
}
```
(`HhIcon` takes a `modifier`; imports `androidx.compose.ui.platform.testTag`.)
`Spoken.kt`, add:
```kotlin
private fun EventUi.badgeWords(): String = badges().joinToString("") { ", ${it.spoken}" }

/** A week chip or a Coming up row: "Swimming, Sam, Tuesday 09:00", then its badges. */
internal fun eventOnDay(event: EventUi, day: LocalDate): String =
    "${event.title}, ${event.person.name}, ${day.format(FULL_WEEKDAY)} ${spokenTime(event.startLabel)}${event.badgeWords()}"

/** A Today row (the card is the day): "Swimming, Sam, 09:00 to 10:30", then its badges. */
internal fun todayRowName(event: EventUi): String = "${event.title}, ${event.person.name}, ${spokenTime(event.timeLabel)}${event.badgeWords()}"

/** "Colours: Alex, green; Sam, blue; Family, amber". */
internal fun legendName(people: List<Person>): String = people.joinToString("; ", prefix = "Colours: ") { person ->
    val colour = if (person.isFamily) FAMILY_COLOUR_NAME else PersonPalette.nameOf(person.color)
    if (colour == null) person.name else "${person.name}, ${colour.lowercase(Locale.ENGLISH)}"
}
```
(imports `java.util.Locale`, `uk.co.siland.culvery.capability.calendar.EventUi`, `FULL_WEEKDAY`, `badges`, `spokenTime`, `uk.co.siland.culvery.core.household.Person`, `uk.co.siland.culvery.core.ui.FAMILY_COLOUR_NAME`, `uk.co.siland.culvery.core.ui.PersonPalette`.)

- [ ] **Step 4: One item per chip, row, legend and detail row**

`WeekView.kt`: `DayColumn`'s `items(…)` line becomes `items(day.events, key = { it.ref.listKey }) { EventChip(it, day.date, onOpen) }`; `EventChip`:
```kotlin
@Composable
private fun EventChip(event: EventUi, day: LocalDate, onOpen: (EventRef) -> Unit) {
    val c = Culvery.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.chipRadius))
            .background(Color(event.person.color).copy(alpha = HhAlpha.tint(c)))
            // One item, read as a person would say it; merged, not cleared, so its texts stay in the tree for the font-scale tests.
            .semantics(mergeDescendants = true) { contentDescription = eventOnDay(event, day) }
            .hhClickable({ onOpen(event.ref) }, label = OPEN_EVENT_DETAILS)
            .padding(horizontal = CalendarDimens.chipPaddingH, vertical = CalendarDimens.chipPaddingV),
    ) {
```
(the body unchanged); `Legend`'s `Row` modifier: `modifier = modifier.clearAndSetSemantics { contentDescription = legendName(people) },`.
`TodayCard.kt`, `TodayRow`'s modifier gains `.semantics(mergeDescendants = true) { contentDescription = todayRowName(event) }` before `.hhClickable(…)`.
`ComingUpCard.kt`: `DayColumn` gains `date: LocalDate` (the caller passes `date = day.date`) and calls `CompactRow(it, date)`; `CompactRow(event: EventUi, day: LocalDate)`'s `Row` modifier gains `.semantics(mergeDescendants = true) { contentDescription = eventOnDay(event, day) }` after `.fillMaxWidth()`.
`EventDetailSheet.kt`, `InfoRow`'s `Row` modifier: `modifier = Modifier.fillMaxWidth().heightIn(min = CalendarDimens.infoRowMin).semantics(mergeDescendants = true) {},`.
(imports `androidx.compose.ui.semantics.clearAndSetSemantics`, `contentDescription`, `semantics`, `java.time.LocalDate` where missing.)

- [ ] **Step 5: The header weather**

`WeatherWords.kt`, after `rowDescription`:
```kotlin
/** The header's one TalkBack item: "Now 17°, partly cloudy, high 19°, low 11°" (TalkBack reads ° as "degrees"). */
internal fun headerDescription(weather: HeaderWeather): String =
    "Now ${degrees(weather.temperature)}, ${conditionWords(weather.condition)}, high ${degrees(weather.high)}, low ${degrees(weather.low)}"
```
`WeatherHeaderItem.kt`, the `Row` modifier: `modifier = modifier.testTag("weather_header").semantics(mergeDescendants = true) { contentDescription = headerDescription(weather) },` (imports `contentDescription`, `semantics`, `uk.co.siland.culvery.capability.weather.headerDescription`).

- [ ] **Step 6: Run the tests to see them pass**

Run the Step 2 command. Expected: PASS. Then `./gradlew :capability:calendar:testDebugUnitTest :capability:weather:testDebugUnitTest :app:testDebugUnitTest` (`ShellLayoutTest` still measures the header's texts in the unmerged tree).

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar capability/weather
git commit -m "Read each week chip, Today and Coming up row, detail row, the legend and the header weather as one item, as a person would say it"
```

---

### Task 9: `UserActivity`, the TalkBack hook and the recommended timeout; the week snap-back (T14); toast durations (§6.1, D30, D31; rulings 3, 6)

**Review:** opus.

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/UserActivity.kt`, `core/plugin/src/testFixtures/java/uk/co/siland/culvery/core/plugin/test/FakeUserActivity.kt`, `app/src/main/java/uk/co/siland/culvery/shell/ShellUserActivity.kt`
- Modify: `core/plugin/build.gradle.kts` (test fixtures), and the `dependencies` of `core/access/build.gradle.kts`, `core/setup/build.gradle.kts`, `capability/calendar/build.gradle.kts`, `app/build.gradle.kts` (`testImplementation(testFixtures(project(":core:plugin")))`)
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Shell.kt:47-55` (`TOAST_MILLIS` goes; `toastMillis` arrives)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt` (`ShellLayers`, `ToastLayer`), `app/src/main/java/uk/co/siland/culvery/SetupWiring.kt:43-45` (`touchTarget` → `countsTouches`, `SessionTouches`), `MainActivity.kt:63-77, 117-199`, `di/AppModule.kt:36-48`
- Modify: `capability/calendar/…/ui/WeekView.kt:119-126` (title), `:334-377` (`WeekShown`, `rememberWeekShown`, `onEveryTouch`), `ui/CardHosts.kt:39-64`
- Test (create): `core/ui/src/test/…/ToastMillisTest.kt`, `app/src/test/java/uk/co/siland/culvery/shell/ShellUserActivityTest.kt`
- Test (modify): `app/src/test/…/AppContentTest.kt`, `SetupWiringTest.kt:86-94`, `shell/ui/OverlayLayersTest.kt:78-130`, `ShellLayersRedrawTest.kt:40-48, 85`, `ModalLayersTest.kt`, `ShellScreenshotTest.kt:175`; `capability/calendar/src/test/…/ui/WeekViewTest.kt:288-338`, `CardHostsMidnightRolloverTest.kt:134, 164`, `OpenEventTest.kt:103-106`, `CalendarConnectHostTest.kt:314`
- Baselines: none change.

**Interfaces:**
- Consumes: the scrims and layers (Tasks 2, 4), `week_title` (Task 3), `ToastMessage.urgent` (Task 5).
- Produces:
  - In `uk.co.siland.culvery.core.plugin`: `interface UserActivity { val touches: StateFlow<Long>; val exploring: StateFlow<Boolean>; fun timeout(baseMillis: Long, hasControls: Boolean = true): Long }`, `val LocalUserActivity: ProvidableCompositionLocal<UserActivity>` (no default), `const val OVERLAY_IDLE_MS = 120_000L`, `const val EDIT_HOLD_MS = 600_000L`, `const val CLOSING_WARNING_MS = 20_000L`.
  - `:core:plugin` test fixtures, package `uk.co.siland.culvery.core.plugin.test`: `class FakeUserActivity(exploring: Boolean = false, var timeouts: (baseMillis: Long, hasControls: Boolean) -> Long = { base, _ -> base }) : UserActivity` with `override val touches: MutableStateFlow<Long>`, `override val exploring: MutableStateFlow<Boolean>` and `fun touch()`.
  - `:app`: `@Singleton class ShellUserActivity @Inject constructor(@ApplicationContext context: Context) : UserActivity` with `fun touched()` and `val accessibilityDelegate: View.AccessibilityDelegate`; bound as `UserActivity`.
  - `fun toastMillis(message: String, urgent: Boolean): Long` (`:core:ui`, `Shell.kt`).
  - `fun ShellLayers(overlay: OverlayState, toast: ToastMessage?, onToastHidden: (Long) -> Unit, pinShowing: Boolean, pinPad: @Composable () -> Unit, activity: UserActivity, onTouch: () -> Unit, shell: @Composable () -> Unit)` — `onTouch` runs on every finger down, always.
  - `fun ToastLayer(toast: ToastMessage?, activity: UserActivity, keyboard: WindowInsets = WindowInsets.ime, onHidden: (Long) -> Unit)`.
  - `SetupWiring.kt`: `internal fun countsTouches(complete: Boolean?, settingsOpen: Boolean): Boolean` and `@Composable internal fun SessionTouches(activity: UserActivity, counting: Boolean, onTouch: () -> Unit)` (Task 11 replaces it with a form that passes the edit hold).
  - `WeekShown` keeps `weeks` and `show(weeks)`; `touches`, `touched()` and `Modifier.onEveryTouch` go.

- [ ] **Step 1: `:core:plugin`'s test fixtures and the interface**

`core/plugin/build.gradle.kts` becomes (the same mechanism Task 1 recorded in ruling 12; with the fallback, put `FakeUserActivity` in `core/ui-testing` instead, adding `implementation(project(":core:plugin"))` there, and leave `core/plugin/build.gradle.kts` as it is):
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
}

android {
    // FakeUserActivity, for every module whose timers read UserActivity.
    testFixtures { enable = true }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:household"))
    // The Compose compiler runs on the fixtures too, so they need the runtime on their classpath.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
}
```
Check the fixtures build on their own before going on: `./gradlew :core:plugin:compileDebugTestFixturesKotlin`. Expected: `BUILD SUCCESSFUL` once `FakeUserActivity` below exists. If it fails with "The Compose Compiler requires the Compose Runtime to be on the class path", the two `testFixturesImplementation` lines are missing; any other fixtures error: take the fallback above and record it in ruling 12.
Add `testImplementation(testFixtures(project(":core:plugin")))` to the `dependencies` of `core/access`, `core/setup`, `capability/calendar` and `app`.
Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/UserActivity.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * The one activity signal every timer reads (4d-2 §6.1, Decision 9): the Settings session, an overlay left open, the
 * PIN pad, the week view's return to this week and the toasts. Fed by the shell's pointer input and by TalkBack.
 */
interface UserActivity {
    /** Grows by one on each finger down anywhere, and on each TalkBack focus move, double-tap or scroll. */
    val touches: StateFlow<Long>

    /** TalkBack (or another touch-exploration service) is on. */
    val exploring: StateFlow<Boolean>

    /** [baseMillis] for most people; with touch exploration on, what Android's "Time to take action" asks for, never less. */
    fun timeout(baseMillis: Long, hasControls: Boolean = true): Long
}

val LocalUserActivity = staticCompositionLocalOf<UserActivity> {
    error("LocalUserActivity not provided: wrap the content in CompositionLocalProvider(LocalUserActivity provides …)")
}

/** An overlay with nothing to lose closes after this long without a touch (4d-2 Decision 4). */
const val OVERLAY_IDLE_MS = 120_000L

/** A sheet with unsaved changes, and the Settings session over it, last this long after the last touch (Decision 3). */
const val EDIT_HOLD_MS = 600_000L

/** How long before a close the spoken warning shows. */
const val CLOSING_WARNING_MS = 20_000L
```
Create `core/plugin/src/testFixtures/java/uk/co/siland/culvery/core/plugin/test/FakeUserActivity.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin.test

import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.plugin.UserActivity

/** A [UserActivity] a test drives: [touch] for a finger or a TalkBack gesture, [exploring] for TalkBack, [timeouts] for "Time to take action". */
class FakeUserActivity(
    exploring: Boolean = false,
    var timeouts: (baseMillis: Long, hasControls: Boolean) -> Long = { base, _ -> base },
) : UserActivity {
    override val touches = MutableStateFlow(0L)
    override val exploring = MutableStateFlow(exploring)

    override fun timeout(baseMillis: Long, hasControls: Boolean): Long = timeouts(baseMillis, hasControls)

    fun touch() {
        touches.value += 1
    }
}
```

- [ ] **Step 2: Write the failing tests**

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ToastMillisTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToastMillisTest {
    @Test
    fun aToastLastsLongEnoughToReadAndNoLonger() {
        assertThat(toastMillis("Event added.", urgent = false)).isEqualTo(4_000L)
        assertThat(toastMillis("x".repeat(30), urgent = true)).isEqualTo(6_000L)
        assertThat(toastMillis("x".repeat(100), urgent = false)).isEqualTo(7_000L)
        assertThat(toastMillis("x".repeat(200), urgent = false)).isEqualTo(8_000L)
        assertThat(toastMillis("x".repeat(200), urgent = true)).isEqualTo(8_000L)
    }
}
```
Fails today: no `toastMillis`.

Create `app/src/test/java/uk/co/siland/culvery/shell/ShellUserActivityTest.kt`:
```kotlin
package uk.co.siland.culvery.shell

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ShellUserActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(AccessibilityManager::class.java)

    /** TalkBack's focus moves, double-taps and scrolls reach the content view as events, not touches (A38). */
    @Test
    fun focusClicksAndScrollsCountAsTouchesAndHoverDoesNot() {
        val activity = ShellUserActivity(context)
        // Robolectric's window refuses events while accessibility is off, as Android does.
        shadowOf(manager).setEnabled(true)
        val screen = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val content = screen.findViewById<ViewGroup>(android.R.id.content)
        val child = View(screen).also(content::addView)
        content.accessibilityDelegate = activity.accessibilityDelegate
        listOf(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED, AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_SCROLLED)
            .forEach { content.requestSendAccessibilityEvent(child, AccessibilityEvent(it)) }
        assertThat(activity.touches.value).isEqualTo(3)
        content.requestSendAccessibilityEvent(child, AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER))
        assertThat(activity.touches.value).isEqualTo(3)
    }

    @Test
    fun aFingerCountsToo() {
        val activity = ShellUserActivity(context)
        activity.touched()
        assertThat(activity.touches.value).isEqualTo(1)
    }

    @Test
    fun withoutTouchExplorationEveryTimeoutIsItsBase() {
        val activity = ShellUserActivity(context)
        shadowOf(manager).setInteractiveUiTimeout(300_000)
        assertThat(activity.exploring.value).isFalse()
        assertThat(activity.timeout(120_000)).isEqualTo(120_000)
    }

    @Test
    fun withTouchExplorationTimeoutsFollowTimeToTakeActionAndNeverShorten() {
        val activity = ShellUserActivity(context)
        shadowOf(manager).setInteractiveUiTimeout(300_000)
        shadowOf(manager).setNonInteractiveUiTimeout(20_000)
        shadowOf(manager).setTouchExplorationEnabled(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.exploring.value).isTrue()
        assertThat(activity.timeout(120_000)).isEqualTo(300_000)
        assertThat(activity.timeout(4_000, hasControls = false)).isEqualTo(20_000)
        assertThat(activity.timeout(600_000)).isEqualTo(600_000)
    }

    /** Android's own setting stops at 2 minutes; a larger value set another way never holds anything past the edit hold. */
    @Test
    fun aTimeToTakeActionPastTheEditHoldIsCappedThere() {
        val activity = ShellUserActivity(context)
        shadowOf(manager).setInteractiveUiTimeout(3_600_000)
        shadowOf(manager).setNonInteractiveUiTimeout(3_600_000)
        shadowOf(manager).setTouchExplorationEnabled(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.timeout(120_000)).isEqualTo(600_000)
        assertThat(activity.timeout(4_000, hasControls = false)).isEqualTo(600_000)
    }
}
```
Fails today: no `ShellUserActivity`. Afterwards: fails if the delegate counts a hover, misses a type, isn't called on the way up, if `exploring` doesn't follow the listener, or if `timeout` uses the recommended value with TalkBack off, lets it shorten a timeout, or lets it pass the edit hold. These tests build the events themselves: they pin the delegate's filter, not that Compose sends TalkBack's events up through the content view. That half is heard on the emulator (Task 17, A.4 path 6).

`WeekViewTest.kt`: delete `aTouchIsSeenWithoutBeingTaken` (290-298; `onEveryTouch` goes); `show` provides a fake (imports `uk.co.siland.culvery.core.plugin.LocalUserActivity`, `uk.co.siland.culvery.core.plugin.test.FakeUserActivity`, `androidx.compose.ui.semantics.LiveRegionMode`):
```kotlin
    private val activity = FakeUserActivity()

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator, LocalUserActivity provides activity) {
            CulveryTheme(dark = true) { content() }
        }
    }
```
and the snap-back tests (300-322) become:
```kotlin
    private fun showShown(): () -> WeekShown {
        lateinit var shown: WeekShown
        show {
            shown = rememberWeekShown(SampleUi.TODAY)
            Text(weekTitle(shown.weeks))
        }
        return { shown }
    }

    /** 4c §6.6, 4d-2 §6.4: a later week goes back after 2 minutes without a touch anywhere; a touch starts the wait again. */
    @Test
    fun aLaterWeekGoesBackToThisWeekAfterTwoMinutesWithoutATouch() {
        compose.mainClock.autoAdvance = false
        val shown = showShown()
        compose.runOnIdle { shown().show(2) }
        applyStateChanges()
        compose.mainClock.advanceTimeBy(BACK_TO_THIS_WEEK_MS - 100)
        compose.onNodeWithText("In 2 weeks").assertExists()
        activity.touch()
        applyStateChanges()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(BACK_TO_THIS_WEEK_MS - 100)
        compose.onNodeWithText("In 2 weeks").assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun withTalkBackOnALaterWeekStays() {
        compose.mainClock.autoAdvance = false
        activity.exploring.value = true
        val shown = showShown()
        compose.runOnIdle { shown().show(1) }
        applyStateChanges()
        compose.mainClock.advanceTimeBy(10 * 60_000L)
        compose.onNodeWithText("Next week").assertExists()
    }

    /** Review Focus 3: switched off mid-way, the 2-minute wait starts from that moment. */
    @Test
    fun turningTouchExplorationOffStartsTheWaitAgain() {
        compose.mainClock.autoAdvance = false
        activity.exploring.value = true
        val shown = showShown()
        compose.runOnIdle { shown().show(1) }
        applyStateChanges()
        compose.mainClock.advanceTimeBy(10 * 60_000L)
        activity.exploring.value = false
        applyStateChanges()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(BACK_TO_THIS_WEEK_MS - 100)
        compose.onNodeWithText("Next week").assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun aLongTimeToTakeActionHoldsALaterWeekLonger() {
        compose.mainClock.autoAdvance = false
        activity.timeouts = { base, _ -> if (base == BACK_TO_THIS_WEEK_MS) 300_000L else base }
        val shown = showShown()
        compose.runOnIdle { shown().show(1) }
        applyStateChanges()
        compose.mainClock.advanceTimeBy(299_900)
        compose.onNodeWithText("Next week").assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun theWeeksTitleIsAPoliteLiveRegion() {
        show { WeekView(state()) }
        compose.onNodeWithTag("week_title").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
```
(`aLaterWeekGoesBackToThisWeekAtMidnight` keeps its body: `show` now provides the fake. The margins are a few frames and a little, as the toast tests' are: the frame clock steps in 16 ms.)
Fail today: `rememberWeekShown` doesn't read `LocalUserActivity` (a fake's touch doesn't restart it; TalkBack doesn't hold it; the timeout isn't asked), and the title has no live region.

T14 (4c code minor: a sheet over the week didn't count as a touch) is pinned by two tests together: `AppContentTest.aTapInsideAnOpenSheetCountsAsATouch`, a real tap inside a sheet reaching `UserActivity` through `ShellLayers` (`:app`, which can see both), and the `WeekViewTest` snap-back tests above, a `UserActivity` touch restarting the week's wait. A host test in `:capability:calendar` can't draw `ShellLayers` (it lives in `:app`), so it could only call the fake's `touch()` itself and would prove nothing more; there is none.
`CardHostsMidnightRolloverTest.kt:134, 164`, `OpenEventTest.kt:103-106` and `CalendarConnectHostTest.kt:314`: each `CompositionLocalProvider(` gains `LocalUserActivity provides FakeUserActivity(),`.

`OverlayLayersTest.kt`: the toast tests take a fake and the new times (imports `androidx.compose.ui.semantics.LiveRegionMode`, `SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`, `uk.co.siland.culvery.core.plugin.test.FakeUserActivity`):
```kotlin
    private val activity = FakeUserActivity()
    private var toast by mutableStateOf<ToastMessage?>(null)

    private fun showToasts() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CulveryTheme(dark = true) { ToastLayer(toast, activity) { id -> if (toast?.id == id) toast = null } } }
    }

    private fun post(message: ToastMessage) {
        toast = message
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun shownAt(millis: Long, text: String): Boolean {
        compose.mainClock.advanceTimeBy(millis)
        compose.mainClock.advanceTimeByFrame()
        return compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aPlainToastLastsFourSeconds() {
        showToasts()
        post(ToastMessage(1, "Event deleted.", "info"))
        assertThat(shownAt(3_880, "Event deleted.")).isTrue()
        assertThat(shownAt(120, "Event deleted.")).isFalse()
    }

    @Test
    fun anUrgentToastLastsSixSeconds() {
        showToasts()
        post(ToastMessage(1, "Couldn’t save — try again.", "info", urgent = true))
        assertThat(shownAt(5_880, "Couldn’t save — try again.")).isTrue()
        assertThat(shownAt(120, "Couldn’t save — try again.")).isFalse()
    }

    @Test
    fun withTalkBacksLongerTimeAToastStaysForIt() {
        activity.timeouts = { base, hasControls -> if (hasControls) base else 20_000L }
        showToasts()
        post(ToastMessage(1, "Event deleted.", "info"))
        assertThat(shownAt(19_880, "Event deleted.")).isTrue()
        assertThat(shownAt(120, "Event deleted.")).isFalse()
    }

    /** Review Focus 1: the plain toast that replaces an urgent one lasts its own 4 s and reads politely. */
    @Test
    fun aPlainToastAfterAnUrgentOneKeepsItsOwnTimeAndPoliteness() {
        showToasts()
        post(ToastMessage(1, "Couldn’t save — try again.", "info", urgent = true))
        compose.mainClock.advanceTimeBy(1_000)
        post(ToastMessage(2, "Event added.", "info"))
        compose.onNodeWithTag("toast").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertThat(shownAt(3_880, "Event added.")).isTrue()
        assertThat(shownAt(120, "Event added.")).isFalse()
    }
```
Replace `toastHidesAfterThreeAndAHalfSeconds` and `aNewToastRestartsTheTimer` (78-116) with these four (the last covers the restart); `theToastSitsAboveTheKeyboard` (123) passes `activity` as `ToastLayer(ToastMessage(…), activity, keyboard = WindowInsets(bottom = keyboard)) {}`; `theToastLayerSpeaksAnUrgentToastAssertively` (Task 5) passes `activity`. The four margins are one frame and a little (the frame clock steps in 16 ms frames).
Fail today: toasts last 3.5 s whatever they say; no `activity` parameter.

`AppContentTest.kt`: the touch tests go through the signal (imports `uk.co.siland.culvery.core.plugin.test.FakeUserActivity`):
```kotlin
    private val activity = FakeUserActivity()

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) {
            SessionTouches(activity, countsTouches(complete, settingsOpen)) { access.touch() }
            ShellLayers(
                overlay = overlay,
                toast = null,
                onToastHidden = {},
                pinShowing = false,
                pinPad = {},
                activity = activity,
                onTouch = activity::touch,
            ) {
                AppContent(
                    complete = complete,
                    settingsOpen = settingsOpen,
                    overlay = overlay,
                    wizard = { Text("Wizard") },
                    shell = { Text("Shell") },
                    settings = { Box(Modifier.fillMaxSize().testTag("settings_stand_in")) },
                    onHomeDrawn = { homeDrawn++ },
                )
            }
        }
    }
```
`aTapInsideAnOpenSheetCountsAsATouch` gains `compose.waitForIdle()` before its assertion. Add:
```kotlin
    /** A38: whatever UserActivity counts (a TalkBack gesture on the device) reaches the session as a finger does. */
    @Test
    fun aUserActivityTouchWhileSettingsIsOpenReachesTheSession() {
        complete = true
        settingsOpen = true
        show()
        activity.touch()
        compose.waitForIdle()
        assertThat(access.touches).isEqualTo(1)
    }
```
`SetupWiringTest.touchesCountWhileSettingsIsOpenOrTheWizardShows` (86-94) becomes:
```kotlin
    @Test
    fun touchesCountWhileSettingsIsOpenOrTheWizardShows() {
        assertThat(countsTouches(complete = null, settingsOpen = false)).isFalse()
        assertThat(countsTouches(complete = true, settingsOpen = false)).isFalse()
        assertThat(countsTouches(complete = true, settingsOpen = true)).isTrue()
        assertThat(countsTouches(complete = false, settingsOpen = false)).isTrue()
    }
```
`ShellLayersRedrawTest.kt`: `ShellLayers(` gains `activity = FakeUserActivity(), onTouch = {},`; line 85 becomes `compose.mainClock.advanceTimeBy(toastMillis("Event deleted", urgent = false) + 100)` (import `uk.co.siland.culvery.core.ui.toastMillis`). `ModalLayersTest.kt`'s `ShellLayers(` gains the same two arguments. `ShellScreenshotTest.kt:175`: `ToastLayer(ToastMessage(1, "Mia can only change events they created.", "info"), FakeUserActivity()) {}`.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew --continue :core:ui:testDebugUnitTest --tests "*ToastMillisTest*" :app:testDebugUnitTest --tests "*ShellUserActivityTest*" --tests "*OverlayLayersTest*" --tests "*AppContentTest*" --tests "*SetupWiringTest*" :capability:calendar:testDebugUnitTest --tests "*WeekViewTest*"`
Expected: FAIL to compile on `toastMillis`, `ShellUserActivity`, `countsTouches`, `SessionTouches`, the `activity` parameters.

- [ ] **Step 4: The signal and the toast's time**

Create `app/src/main/java/uk/co/siland/culvery/shell/ShellUserActivity.kt`:
```kotlin
package uk.co.siland.culvery.shell

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS
import uk.co.siland.culvery.core.plugin.UserActivity

/**
 * The app's [UserActivity] (4d-2 §6.1): the shell reports fingers through [touched]; TalkBack's gestures arrive as
 * accessibility events through [accessibilityDelegate] on the content view, which Compose sends them up through.
 */
@Singleton
class ShellUserActivity @Inject constructor(@ApplicationContext context: Context) : UserActivity {
    private val manager: AccessibilityManager? = context.getSystemService(AccessibilityManager::class.java)

    private val _touches = MutableStateFlow(0L)
    override val touches: StateFlow<Long> = _touches.asStateFlow()

    private val _exploring = MutableStateFlow(manager?.isTouchExplorationEnabled == true)
    override val exploring: StateFlow<Boolean> = _exploring.asStateFlow()

    init {
        manager?.addTouchExplorationStateChangeListener { _exploring.value = it }
    }

    fun touched() {
        _touches.update { it + 1 }
    }

    override fun timeout(baseMillis: Long, hasControls: Boolean): Long {
        val m = manager ?: return baseMillis
        if (!_exploring.value) return baseMillis
        val controls = if (hasControls) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0
        val flags = AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS or controls
        val recommended = m.getRecommendedTimeoutMillis(baseMillis.toInt(), flags).toLong()
        // Android's own setting stops at 2 minutes; a larger value set another way never holds anything past the edit hold.
        return minOf(maxOf(baseMillis, recommended), maxOf(baseMillis, EDIT_HOLD_MS))
    }

    /** For `android.R.id.content`: a TalkBack focus move, double-tap or scroll is a touch. Events are only sent while a service listens. */
    val accessibilityDelegate: View.AccessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean {
            if (event.eventType in COUNTED) touched()
            return super.onRequestSendAccessibilityEvent(host, child, event)
        }
    }

    private companion object {
        val COUNTED = setOf(
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
        )
    }
}
```
`Shell.kt`: the toast comment (line 47) becomes "// Toast: bottom-centre pill 28 dp above the bottom, padding 14×22, radius 26, `info` icon; its time is [toastMillis]." and `TOAST_MILLIS` (55) goes; after `ShellType` add:
```kotlin
/** How long a toast shows (4d-2 §6.5): 4 s, 6 s for an error or refusal, longer for long text, at most 8 s. */
fun toastMillis(message: String, urgent: Boolean): Long {
    val base = if (urgent) 6_000L else 4_000L
    // 50 ms a character is a slow reader's pace (screens audit S35); 40 characters fit in the base time.
    return minOf(base + 50L * (message.length - 40).coerceAtLeast(0), 8_000L)
}
```

- [ ] **Step 5: The `:app` wiring**

`AppModule.kt`, after the `toaster` binding:
```kotlin
    @Binds
    abstract fun userActivity(impl: ShellUserActivity): UserActivity
```
(imports `uk.co.siland.culvery.core.plugin.UserActivity`, `uk.co.siland.culvery.shell.ShellUserActivity`.)
`SetupWiring.kt`, `touchTarget` (43-45) becomes:
```kotlin
/** 4a design D5, D9: touches restart the session while Settings is open or the wizard shows (the setup session's ten minutes). */
internal fun countsTouches(complete: Boolean?, settingsOpen: Boolean): Boolean = complete == false || (complete == true && settingsOpen)

/** While [counting], every touch [activity] reports, a TalkBack gesture's included, runs [onTouch] (4d-2 §6.1). */
@Composable
internal fun SessionTouches(activity: UserActivity, counting: Boolean, onTouch: () -> Unit) {
    val touch by rememberUpdatedState(onTouch)
    LaunchedEffect(activity, counting) {
        if (counting) activity.touches.drop(1).collect { touch() }
    }
}
```
(imports `androidx.compose.runtime.getValue`, `rememberUpdatedState`, `kotlinx.coroutines.flow.drop`, `uk.co.siland.culvery.core.plugin.UserActivity`; drop the `AccessControl` import if unused.)
`OverlayLayers.kt`: `ShellLayers` takes `activity: UserActivity` and `onTouch: () -> Unit` (after `pinPad`), drops `rememberUpdatedState(onTouch)`'s nullability (`touched()`), and passes `activity` to `ToastLayer(toast, activity, onHidden = onToastHidden)`; its KDoc's last sentence becomes "Every finger down anywhere in them, sheets and PIN pads included, goes to [onTouch] before anything handles it; the session hears of it from [UserActivity] a moment later, which is always before the finger lifts to click."; `ToastLayer`:
```kotlin
/**
 * Draws [toast] bottom-centre, 28 dp above the bottom or above the on-screen keyboard while it shows (hand-off §7), and
 * reports it hidden after its reading time ([toastMillis]), or TalkBack's longer one; a new toast restarts the timer.
 */
@Composable
fun ToastLayer(toast: ToastMessage?, activity: UserActivity, keyboard: WindowInsets = WindowInsets.ime, onHidden: (Long) -> Unit) {
    val t = toast ?: return
    LaunchedEffect(t.id) {
        delay(activity.timeout(toastMillis(t.message, t.urgent), hasControls = false))
        onHidden(t.id)
    }
```
(the rest unchanged; imports `uk.co.siland.culvery.core.plugin.UserActivity`, `uk.co.siland.culvery.core.ui.toastMillis`.)
`MainActivity.kt`: inject `@Inject lateinit var userActivity: ShellUserActivity` (beside `toasts`); in `onCreate`, after `lifecycle.addObserver(kiosk)`:
```kotlin
        // TalkBack's gestures reach the app as accessibility events, not touches: they keep Settings and the sheets open too.
        findViewById<ViewGroup>(android.R.id.content).accessibilityDelegate = userActivity.accessibilityDelegate
```
in `setContent`, `LocalUserActivity provides userActivity,` joins the `CompositionLocalProvider`; before `ShellLayers(`:
```kotlin
                        SessionTouches(userActivity, countsTouches(complete, state.settingsOpen)) { access.touch() }
```
and the `ShellLayers(` call's `onTouch = touchTarget(…)` becomes `activity = userActivity, onTouch = userActivity::touched,`. (imports `android.view.ViewGroup`, `uk.co.siland.culvery.core.plugin.LocalUserActivity`, `uk.co.siland.culvery.shell.ShellUserActivity`.)

- [ ] **Step 6: The week reads the signal**

`WeekView.kt`, the title's modifier (Task 3's) becomes `Modifier.weight(1f, fill = false).testTag("week_title").semantics { heading(); liveRegion = LiveRegionMode.Polite },` (imports `LiveRegionMode`, `liveRegion`). `WeekShown`, `rememberWeekShown` and `onEveryTouch` (334-377) become:
```kotlin
/** After this long without a touch, a later week goes back to this week (4c §6.6). */
internal const val BACK_TO_THIS_WEEK_MS = 120_000L

/** Which week the Calendar tab shows (4c D10): [weeks] after this one, 0 to [MAX_WEEKS_AHEAD]. */
@Stable
internal class WeekShown {
    var weeks by mutableIntStateOf(0)
        private set

    fun show(weeks: Int) {
        this.weeks = weeks.coerceIn(0, MAX_WEEKS_AHEAD)
    }
}

/**
 * A new [WeekShown] each [today], so the view is back on this week at midnight. A later week goes back after
 * [BACK_TO_THIS_WEEK_MS] (or TalkBack's longer time) without a touch anywhere, a sheet's included; never while TalkBack
 * is on, where it would move the page under a reader (4d-2 §6.4).
 */
@Composable
internal fun rememberWeekShown(today: LocalDate): WeekShown {
    val shown = remember(today) { WeekShown() }
    val activity = LocalUserActivity.current
    val touches by activity.touches.collectAsState()
    val exploring by activity.exploring.collectAsState()
    LaunchedEffect(shown, shown.weeks, touches, exploring) {
        if (shown.weeks > 0 && !exploring) {
            delay(activity.timeout(BACK_TO_THIS_WEEK_MS))
            shown.show(0)
        }
    }
    return shown
}
```
(imports `androidx.compose.runtime.collectAsState`, `uk.co.siland.culvery.core.plugin.LocalUserActivity`; drop `PointerEventPass`, `PointerEventType`, `pointerInput` if unused.)
`CardHosts.kt`, `WeekViewHost`'s `WeekView(` call drops `modifier = Modifier.onEveryTouch(shown::touched),` (and the `Modifier` import if unused); its KDoc gains "The return to this week counts every touch the shell sees (4d-2 §6.4)."

- [ ] **Step 7: Run the tests to see them pass**

Run the Step 3 command. Expected: PASS. If `focusClicksAndScrollsCountAsTouchesAndHoverDoesNot` throws "Accessibility off", the `setEnabled(true)` line didn't reach the window's manager: stop and report. Then `./gradlew :core:plugin:testDebugUnitTest :core:ui:testDebugUnitTest :core:access:testDebugUnitTest :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences (`home_toast_dark` holds its clock, so the longer toast doesn't change it).

- [ ] **Step 9: Commit**

```bash
git add core/plugin core/ui core/access/build.gradle.kts core/setup/build.gradle.kts capability/calendar app
git commit -m "Count TalkBack's gestures as touches, give TalkBack users Android's longer times, keep a later week while anyone is busy, and size toasts to their text"
```

---

### Task 10: The Settings session: the edit hold, wall-clock expiry, `expiresAt` and the Settings warning; a refused PIN that names no one (D29, C64 as amended; rulings 5, 24, 26)

**Review:** opus (privileged-session timing and the PIN pad's lockout).

**Files:**
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/AccessControl.kt:9-10` (KDoc), `:28-35` (after `Refusal`), `:37-76` (`AccessControl`), `DefaultAccessControl.kt:19-28` (header), `:37-41` (`set`), `:57-58` (`authorise`'s first lines), `:105-113` (a refused PIN), `:128-140` (`endSetupSession`, `touch`), `:150-157` (`restartExpiry`), `PinPromptController.kt:12` (`PinError.NotAllowed`)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt` (`ShellLayers`, `ToastLayer`; `ToastSlot`, `ClosingWarningLayer`, `rememberClosingSoon` added), `MainActivity.kt:67-77, 127, 168-172`
- Modify (fakes): `app/src/test/…/shell/Fakes.kt:54-78` (`FakeAccessControl`), `capability/calendar/src/test/…/StubEditor.kt:17-33` (`NobodyMay`)
- Modify (construction sites, one `@Inject` constructor): `core/access/src/test/…/DefaultAccessControlTest.kt:53-61`, `core/setup/src/test/…/TestHousehold.kt:60`, `capability/calendar/src/test/…/TestAccess.kt:82`, `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt:99`, `SampleWizardTest.kt:89`
- Test: `core/access/src/test/…/DefaultAccessControlTest.kt`, `ui/PinPadTest.kt:119`, `ui/FontScaleTest.kt:52`; `capability/calendar/src/test/…/ui/CalendarConnectHostTest.kt:257`; `app/src/test/…/shell/ui/ShellLayersTimeoutTest.kt` (create), `ShellScreenshotTest.kt`
- Baselines (new): `app/src/test/screenshots/settings_warning_dark.png`
- Spec: Appendix A's C64 row and §7.1.2 already say "That PIN can’t do this. Ask an Admin." (amended with this plan).

**Interfaces:**
- Consumes: `UserActivity`, `FakeUserActivity`, `EDIT_HOLD_MS`, `CLOSING_WARNING_MS`, `ShellLayers(…, activity, onTouch, …)`, `ToastLayer` (Task 9); `HhToast(…, urgent)` (Task 5).
- Produces:
  - `AccessControl`: `val expiresAt: StateFlow<Long?>` (the wall-clock millis the session ends at; null with nobody signed in), `fun touch(holdForEdits: Boolean = false)`, `fun checkExpiry()`.
  - `class DefaultAccessControl @Inject constructor(registry, pins, lockout, prompt, clock, toaster, @ApplicationScope scope, userActivity: UserActivity)` — one constructor; tests pass a `FakeUserActivity`.
  - `PinError.NotAllowed(val message: String)`; `fun refusedHere(openToAdults: Boolean): String` in `AccessControl.kt` ("That PIN can’t do this. Ask an Admin." / "… Ask a grown-up.").
  - `ShellLayers(…, activity: UserActivity, onTouch: () -> Unit, settingsExpiresAt: Long? = null, nowMillis: () -> Long = System::currentTimeMillis, shell: …)`; `internal const val SETTINGS_CLOSING`; `@Composable internal fun ClosingWarningLayer(text: String, tag: String, keyboard: WindowInsets = WindowInsets.ime)`; `@Composable private fun ToastSlot(keyboard: WindowInsets, content: @Composable () -> Unit)`, which `ToastLayer` and `ClosingWarningLayer` share.
  - Task 11 calls `touch(holdForEdits = true)` from the shell; until then nothing does.

The session's rules, all in `DefaultAccessControl`:
- A touch restarts 2 minutes, or 10 with `holdForEdits` (never more); the hold is remembered, so an `authorise` on the session shortcut (a Save the rules refuse, say) restarts 10 minutes too, until a touch without the hold clears it (P6).
- The timer is a coroutine `delay`, which stops while the tablet sleeps; `expiresAt` is wall-clock time. So a touch, an `authorise` and `checkExpiry()` (called by `MainActivity` on start and resume) first end a session whose `expiresAt` has passed by the wall clock, and only then do anything else (P2). A touch after a long sleep never revives a session.
- `restartExpiry` itself doesn't check: its callers do, so `authorise` never returns a session it has just ended.

- [ ] **Step 1: Write the failing tests**

`DefaultAccessControlTest.kt`, the `access()` helper (53-61) takes a `UserActivity` instead of building the timeout itself (imports `uk.co.siland.culvery.core.plugin.test.FakeUserActivity`, `uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS`):
```kotlin
    private fun TestScope.access(
        timers: CoroutineScope = backgroundScope,
        nowMillis: () -> Long = { testScheduler.currentTime },
        activity: FakeUserActivity = FakeUserActivity(),
    ) = DefaultAccessControl(
        registry = PermissionRegistry(setOf(CorePermissionSource(), everyone)),
        pins = pins,
        lockout = LockoutStore(context),
        prompt = prompt,
        clock = WallClock { nowMillis() },
        toaster = toasts,
        scope = timers,
        userActivity = activity,
    )
```
Add:
```kotlin
    private suspend fun TestScope.signedInAlex(access: DefaultAccessControl) {
        person("Alex", Role.ADMIN, "1234")
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
    }

    /** 4d-2 §6.2: ends at 2 minutes, not a moment before; a touch at 1:50 moves it to 3:50. */
    @Test
    fun theSessionEndsTwoMinutesAfterTheLastTouch() = runTest {
        val access = access()
        signedInAlex(access)
        advanceTimeBy(110_000)
        access.touch()
        advanceTimeBy(SESSION_TIMEOUT_MS - 1)
        runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun editsHoldTheSessionTenMinutesAndNoLonger() = runTest {
        val access = access()
        signedInAlex(access)
        access.touch(holdForEdits = true)
        advanceTimeBy(EDIT_HOLD_MS - 1_000)
        access.touch(holdForEdits = true)
        advanceTimeBy(EDIT_HOLD_MS - 1)
        runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    /** P1: once the edits go (a sheet closed or undone), the next touch puts the session back on 2 minutes. */
    @Test
    fun aTouchWithoutTheHoldGoesBackToTwoMinutes() = runTest {
        val access = access()
        signedInAlex(access)
        access.touch(holdForEdits = true)
        advanceTimeBy(30_000)
        access.touch(holdForEdits = false)
        advanceTimeBy(SESSION_TIMEOUT_MS - 1)
        runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    /** P6: a Save on the session shortcut keeps the hold, so a refused save's sheet isn't lost at 2 minutes. */
    @Test
    fun anAuthoriseOnTheShortcutKeepsTheHold() = runTest {
        val access = access()
        signedInAlex(access)
        access.touch(holdForEdits = true)
        advanceTimeBy(1_000)
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNotNull()
        advanceTimeBy(EDIT_HOLD_MS - 1)
        runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun talkBacksTimeToTakeActionStretchesTheSession() = runTest {
        val access = access(activity = FakeUserActivity(timeouts = { base, _ -> if (base == SESSION_TIMEOUT_MS) 300_000L else base }))
        signedInAlex(access)
        assertThat(access.expiresAt.value).isEqualTo(testScheduler.currentTime + 300_000L)
        advanceTimeBy(300_000L - 1)
        runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun expiresAtFollowsEachRestartAndClearsWhenTheSessionEnds() = runTest {
        val access = access()
        assertThat(access.expiresAt.value).isNull()
        signedInAlex(access)
        assertThat(access.expiresAt.value).isEqualTo(testScheduler.currentTime + SESSION_TIMEOUT_MS)
        advanceTimeBy(10_000)
        access.touch(holdForEdits = true)
        assertThat(access.expiresAt.value).isEqualTo(testScheduler.currentTime + EDIT_HOLD_MS)
        access.lock()
        assertThat(access.expiresAt.value).isNull()
        access.touch()
        assertThat(access.expiresAt.value).isNull()
    }

    /** P2: the timer stops while the tablet sleeps; the wall clock doesn't, so a touch after waking can't revive the session. */
    @Test
    fun aTouchAfterTheWallClockPassedTheEndSignsOut() = runTest {
        var wall = 0L
        val access = access(nowMillis = { wall })
        signedInAlex(access)
        wall = SESSION_TIMEOUT_MS - 1
        access.touch()
        assertThat(access.session.value).isNotNull()
        wall += SESSION_TIMEOUT_MS
        access.touch()
        assertThat(access.session.value).isNull()
        assertThat(access.expiresAt.value).isNull()
    }

    @Test
    fun checkExpiryEndsASessionOnlyOnceTheWallClockHasPassedItsEnd() = runTest {
        var wall = 0L
        val access = access(nowMillis = { wall })
        signedInAlex(access)
        wall = SESSION_TIMEOUT_MS - 1
        access.checkExpiry()
        assertThat(access.session.value).isNotNull()
        wall = SESSION_TIMEOUT_MS
        access.checkExpiry()
        assertThat(access.session.value).isNull()
    }

    /** P2: the session shortcut checks first, so it never authorises on a session that has already run out. */
    @Test
    fun anAuthoriseAfterTheWallClockPassedTheEndAsksForAPin() = runTest {
        var wall = 0L
        val access = access(nowMillis = { wall })
        signedInAlex(access)
        wall = SESSION_TIMEOUT_MS
        answerPins(null)
        // On the shortcut it would pass without a pad; null means the pad came up and was cancelled.
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(access.session.value).isNull()
    }
```
Each fails today: `touch` takes no hold, there is no `expiresAt` or `checkExpiry`, the constructor takes no `UserActivity`, and the wall clock is never read for the session.

A refused real PIN (P8, decision A): the pad names no one and the attempt counts. Replace `aChildIsRefusedConnecting`'s and `personWithoutPermissionIsToldAndNotSignedIn`'s `NotAllowed("Mia")` with `PinError.NotAllowed("That PIN can’t do this. Ask an Admin.")`; `aRefusalInThePadCarriesItsMessageAndAsksAgain`'s (322) with `PinError.NotAllowed("That PIN can’t do this. Ask a grown-up.")` (the test's `test.any` is open to Adults). Replace `notAllowedPinDoesNotCountTowardLockout` (251-259) with:
```kotlin
    /** P8: a sibling's PIN at an Admin's pad uses up a try like a wrong one, so it can't be found by guessing for free. */
    @Test
    fun aRefusedRealPinCountsTowardTheLockoutAndNamesNoOne() = runTest {
        person("Alex", Role.ADMIN, "1234")
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "9876", null)
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(seen.last().lockedUntilMillis).isNotNull()
        assertThat(seen.mapNotNull { (it.error as? PinError.NotAllowed)?.message }.joinToString()).doesNotContain("Mia")
    }

    @Test
    fun aRefusalSaysWhoToAsk() {
        assertThat(refusedHere(openToAdults = false)).isEqualTo("That PIN can’t do this. Ask an Admin.")
        assertThat(refusedHere(openToAdults = true)).isEqualTo("That PIN can’t do this. Ask a grown-up.")
    }
```
(`notAllowedPinDoesNotResetLockout` and `aRefusedPinDoesNotResetTheLockoutCounter` stay: a refused PIN still never resets the count.)
`PinPadTest.kt:119`: `controller.open("Change settings", PinError.NotAllowed("That PIN can’t do this. Ask an Admin."), null)` and its text assertion finds "That PIN can’t do this. Ask an Admin."; access `FontScaleTest.kt:52`: `show(PinError.NotAllowed("That PIN can’t do this. Ask an Admin."))` (the long name it measured goes: the message no longer holds one; the test still checks the message keeps to two lines at 1.5). `CalendarConnectHostTest.kt:257`: `PinError.NotAllowed("That PIN can’t do this. Ask an Admin.")`.
Fail today: the message names the person, and a refused PIN doesn't count.

Create `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayersTimeoutTest.kt` with the Settings warning (Task 11 adds the sheet's):
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.SESSION_TIMEOUT_MS
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.OverlayState

/** 4d-2 §6.2, §6.3 under the Compose test clock: what closes when, and the two warnings. */
@RunWith(AndroidJUnit4::class)
class ShellLayersTimeoutTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()
    private val activity = FakeUserActivity()
    private var expiresAt by mutableStateOf<Long?>(null)
    private var pinShowing by mutableStateOf(false)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                ShellLayers(
                    overlay = overlay,
                    toast = null,
                    onToastHidden = {},
                    pinShowing = pinShowing,
                    pinPad = { if (pinShowing) Box(Modifier.fillMaxSize().testTag("pad")) },
                    activity = activity,
                    onTouch = activity::touch,
                    settingsExpiresAt = expiresAt,
                    nowMillis = { compose.mainClock.currentTime },
                ) { Text("Home") }
            }
        }
    }

    /** Two frames: one recomposes on the new state, the next draws anything an effect wrote back. */
    private fun settle() {
        repeat(2) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    private fun after(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.mainClock.advanceTimeByFrame()
    }

    private fun open(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun settingsWarnsTwentySecondsBeforeItsSessionEndsAndATouchHidesIt() {
        show()
        expiresAt = compose.mainClock.currentTime + SESSION_TIMEOUT_MS
        settle()
        after(SESSION_TIMEOUT_MS - 20_000 - 100)
        assertThat(open("settings_warning")).isFalse()
        after(100)
        compose.onNodeWithTag("settings_warning")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assertTextEquals("Settings closes in 20 seconds. Touch the screen to stay.")
        // A touch moves the session on; the warning goes until the next countdown's last 20 seconds.
        expiresAt = compose.mainClock.currentTime + SESSION_TIMEOUT_MS
        settle()
        assertThat(open("settings_warning")).isFalse()
        after(SESSION_TIMEOUT_MS - 20_000 - 100)
        assertThat(open("settings_warning")).isFalse()
        after(100)
        assertThat(open("settings_warning")).isTrue()
    }

    @Test
    fun noSessionNoWarning() {
        show()
        after(30 * 60_000L)
        assertThat(open("settings_warning")).isFalse()
    }
}
```
Fails today: `ShellLayers` has no `settingsExpiresAt` or `nowMillis`; nothing warns. (`assertTextEquals` on a merged node reads the warning's one `Text`.)
`ShellScreenshotTest.kt`, add:
```kotlin
    @Test
    fun settingsWarningDark() = snap("settings_warning_dark", dark = true) { ClosingWarningLayer(SETTINGS_CLOSING, "settings_warning") }
```

- [ ] **Step 2: The fakes and the construction sites**

`Fakes.kt`, `FakeAccessControl` gains:
```kotlin
    override val expiresAt = MutableStateFlow<Long?>(null)
    val holds = mutableListOf<Boolean>()
    var expiryChecks = 0
    override fun checkExpiry() { expiryChecks++ }
```
and its `touch` becomes:
```kotlin
    override fun touch(holdForEdits: Boolean) {
        touches++
        holds += holdForEdits
    }
```
`StubEditor.kt`, `NobodyMay`: `override val expiresAt: StateFlow<Long?> = MutableStateFlow(null)`, `override fun touch(holdForEdits: Boolean) = Unit`, `override fun checkExpiry() = Unit`.
Each other `DefaultAccessControl(…)` built with named arguments gains `userActivity = FakeUserActivity(),` (import `uk.co.siland.culvery.core.plugin.test.FakeUserActivity`): `core/setup/src/test/…/TestHousehold.kt:60`, `capability/calendar/src/test/…/TestAccess.kt:82`, `app/src/testDebug/…/SampleAddTest.kt:99`, `SampleWizardTest.kt:89`. (`:core:setup`, `:capability:calendar` and `:app` have the fixtures since Task 9.)

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew --continue :core:access:testDebugUnitTest --tests "*DefaultAccessControlTest*" --tests "*PinPadTest*" --tests "*FontScaleTest*" :capability:calendar:testDebugUnitTest --tests "*CalendarConnectHostTest*" :app:testDebugUnitTest --tests "*ShellLayersTimeoutTest*"`
Expected: FAIL to compile on the missing members.

- [ ] **Step 4: The session's hold, wall-clock expiry and `expiresAt`**

`AccessControl.kt`: the `SESSION_TIMEOUT_MS` KDoc becomes "Hand-off §7: signed in for 2 minutes after the last authorised action or touch while Settings is open (4a design D5, 4d-2 §6.2)."; after `Refusal`:
```kotlin
/**
 * The PIN pad's line for a real PIN that may not do this (4d-2 C64, decision A): it names no one, so a PIN found by
 * guessing doesn't say whose it is, and says who to ask.
 */
fun refusedHere(openToAdults: Boolean): String =
    if (openToAdults) "That PIN can’t do this. Ask a grown-up." else "That PIN can’t do this. Ask an Admin."
```
in the interface, after `session`:
```kotlin
    /** When the session ends, in wall-clock millis, as of its last restart; null with nobody signed in (4d-2 §6.2, the warning). */
    val expiresAt: StateFlow<Long?>
```
and `touch` with `checkExpiry` after it:
```kotlin
    /**
     * A touch while Settings is open or the wizard shows (4a design D5, D9; 4d-2 §6.2): restarts the session's two minutes,
     * or the setup session's ten. [holdForEdits]: an open sheet has unsaved changes, so it restarts for [EDIT_HOLD_MS]
     * instead, and an authorise on the session keeps that until a touch without it. Nothing with nobody signed in, and
     * nothing for a session the wall clock has already ended.
     */
    fun touch(holdForEdits: Boolean = false)

    /** Ends the session if the wall clock has passed [expiresAt]: its timer stops while the tablet sleeps. On start and resume. */
    fun checkExpiry()
```
(import `uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS` for the KDoc link.)
`PinPromptController.kt:12`: `data class NotAllowed(val message: String) : PinError`.
`DefaultAccessControl.kt`, the header (19-28):
```kotlin
/** The access rules. [userActivity] gives each wait Android's "Time to take action" when TalkBack is on (4d-2 §6.1). */
@Singleton
class DefaultAccessControl @Inject constructor(
    private val registry: PermissionRegistry,
    private val pins: PinManager,
    private val lockout: LockoutStore,
    private val prompt: PinPromptController,
    private val clock: WallClock,
    private val toaster: Toaster,
    @ApplicationScope private val scope: CoroutineScope,
    private val userActivity: UserActivity,
) : AccessControl {
```
after `session` (34):
```kotlin
    private val _expiresAt = MutableStateFlow<Long?>(null)
    override val expiresAt: StateFlow<Long?> = _expiresAt.asStateFlow()

    // The last touch's holdForEdits, so an authorise on the session keeps it (P6). Cleared by a touch without it, or sign-out.
    @Volatile private var holding = false
```
`set` (37-41):
```kotlin
    /** The one place the session changes. Every caller but sign-out restarts the expiry next, which sets [expiresAt] again. */
    private fun set(next: SignedIn?) = synchronized(stateLock) {
        signedIn.value = next
        _session.value = next?.who
        _expiresAt.value = null
        if (next == null) holding = false
    }
```
`authorise`, inside `authoriseLock.withLock {`, before `val current = signedIn.value` (58):
```kotlin
            endIfPastByWallClock()
```
the refused PIN (105-113):
```kotlin
            val granted = grantedFor(identified.role, anyOf)
            if (granted.isEmpty() || !allow(identified, granted)) {
                // Counted as a wrong PIN and never resets the count, so a sibling's PIN can't be found for free or clear it.
                lockout.recordFailure(clock.nowMillis())
                if (refusal is Refusal.Toast) {
                    toaster.show(refusal.message(identified.person.name))
                    return null
                }
                error = PinError.NotAllowed(refusedHere(openToAdults = anyOf.any { registry.isGranted(Role.ADULT, it) }))
                continue
            }
```
`endSetupSession` (128-135) gains `if (endIfPastByWallClock()) return` as its first line. `touch` (137-140), with `checkExpiry` after it:
```kotlin
    override fun touch(holdForEdits: Boolean) {
        if (endIfPastByWallClock()) return
        val setup = synchronized(stateLock) { signedIn.value?.setup } ?: return
        holding = holdForEdits
        restartExpiry(if (setup) SETUP_IDLE_MS else SESSION_TIMEOUT_MS)
    }

    override fun checkExpiry() {
        endIfPastByWallClock()
    }

    /** True, with the session ended, once the wall clock has passed [expiresAt]; the expiry's delay stops while the tablet sleeps. */
    private fun endIfPastByWallClock(): Boolean {
        val ends = _expiresAt.value ?: return false
        if (clock.nowMillis() < ends) return false
        lock()
        return true
    }
```
`restartExpiry` (150-157):
```kotlin
    /** [baseMillis], or the edit hold while [holding]; stretched by TalkBack's time; [expiresAt] set to match. */
    private fun restartExpiry(baseMillis: Long = SESSION_TIMEOUT_MS) = synchronized(expiryLock) {
        expiry?.cancel()
        val guarded = signedIn.value
        val millis = userActivity.timeout(if (holding) maxOf(baseMillis, EDIT_HOLD_MS) else baseMillis)
        _expiresAt.value = guarded?.let { clock.nowMillis() + millis }
        expiry = scope.launch {
            delay(millis)
            synchronized(stateLock) { if (signedIn.value === guarded) set(null) }
        }
    }
```
(imports `uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS`, `uk.co.siland.culvery.core.plugin.UserActivity`.) The comment "A real but refused PIN neither resets nor counts…" (107) goes with the old lines.
The `Refusal.InPad` KDoc (`AccessControl.kt:31`) becomes "The PIN pad says the PIN can't do this and who to ask, counts it as a wrong one, and asks again."

- [ ] **Step 5: The Settings warning in the shell**

`OverlayLayers.kt`, above `ShellLayers`:
```kotlin
internal const val SETTINGS_CLOSING = "Settings closes in 20 seconds. Touch the screen to stay."
```
`ShellLayers` gains `settingsExpiresAt: Long? = null` and `nowMillis: () -> Long = System::currentTimeMillis` after `onTouch`; its KDoc gains "[settingsExpiresAt] is the session's end while Settings is open; 20 seconds before it a spoken warning shows over everything."; in its body, before the `Box`:
```kotlin
    val settingsClosingSoon = rememberClosingSoon(settingsExpiresAt, nowMillis)
```
and after `ToastLayer(…)`, last in the `Box`:
```kotlin
        if (settingsClosingSoon) ClosingWarningLayer(SETTINGS_CLOSING, "settings_warning")
```
Below `OverlayLayer`:
```kotlin
/** True from [CLOSING_WARNING_MS] before [expiresAt] until it moves; false without one. Once per countdown: keyed on it. */
@Composable
private fun rememberClosingSoon(expiresAt: Long?, nowMillis: () -> Long): Boolean {
    // Keyed, so a new end resets it in the same frame the touch lands in, not a frame later.
    var soon by remember(expiresAt) { mutableStateOf(false) }
    LaunchedEffect(expiresAt) {
        val at = expiresAt ?: return@LaunchedEffect
        // Measured once from the wall clock, then waited on the frame clock: a clock change afterwards can't move it.
        delay(at - CLOSING_WARNING_MS - nowMillis())
        soon = true
    }
    return soon
}

/** Where toasts and warnings sit: bottom-centre, 28 dp above the bottom or above the on-screen keyboard (hand-off §7). */
@Composable
private fun ToastSlot(keyboard: WindowInsets, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom))
            .padding(bottom = ShellTokens.toastBottom),
        contentAlignment = Alignment.BottomCenter,
    ) {
        content()
    }
}

/** A warning pill where toasts sit (hand-off §7's toast, `schedule` icon), spoken at once; its words are fixed, never a countdown. */
@Composable
internal fun ClosingWarningLayer(text: String, tag: String, keyboard: WindowInsets = WindowInsets.ime) {
    ToastSlot(keyboard) { HhToast(text, Icons.SCHEDULE, Modifier.testTag(tag), urgent = true) }
}
```
`ToastLayer`'s `Box(…) { HhToast(…) }` becomes `ToastSlot(keyboard) { HhToast(t.message, t.icon, urgent = t.urgent) }`.
(imports `androidx.compose.runtime.mutableStateOf`, `setValue`, `uk.co.siland.culvery.core.plugin.CLOSING_WARNING_MS`, `uk.co.siland.culvery.core.ui.Icons`.)
`MainActivity.kt`: inject `@Inject lateinit var wallClock: WallClock` (beside `access`, line 70); after `lifecycle.addObserver(kiosk)` (127):
```kotlin
        // The session's timer stops while the tablet sleeps; its wall-clock end is checked on the way back.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) access.checkExpiry()
        })
```
in `setContent` add `val sessionEnds by access.expiresAt.collectAsStateWithLifecycle()`; the `ShellLayers(` call (168) gains `settingsExpiresAt = if (state.settingsOpen) sessionEnds else null, nowMillis = wallClock::nowMillis,`. (imports `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.LifecycleEventObserver`, `uk.co.siland.culvery.core.plugin.WallClock`.)
`AppModule.kt` needs no change: Hilt now builds `DefaultAccessControl` through its one constructor, with the `UserActivity` binding Task 9 added.

- [ ] **Step 6: Run the tests to see them pass**

Run the Step 3 command. Expected: PASS. Then `./gradlew :core:plugin:testDebugUnitTest :core:access:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`.

- [ ] **Step 7: Record the Settings warning baseline and look at it**

```bash
./gradlew :app:compareRoborazziDebug --tests "*ShellScreenshotTest*"
find app/build/outputs/roborazzi -name "*_compare.png" -mmin -30
```
Expected: no difference in the existing images (`home_toast_dark` keeps its place: `ToastSlot` is the same layout). Then `./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest*"` and open `app/src/test/screenshots/settings_warning_dark.png`: Home with a toast-style pill bottom-centre, a clock (`schedule`) icon and "Settings closes in 20 seconds. Touch the screen to stay." on one or two lines, nothing clipped. Run the baseline check (Global Constraints) with `settings_warning_dark` as the one expected name. Anything else: stop.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add core/access core/setup capability/calendar app
git commit -m "Hold the Settings session ten minutes for unsaved changes, end it by the wall clock after the tablet sleeps, warn twenty seconds before Settings closes, and make a refused PIN name no one and count as a wrong one"
```

---

### Task 11: Overlays and the PIN pad left alone; the sheet warning; the edits that hold Settings (D32, the amendment; rulings 4, 20, 22, 25)

**Review:** opus (overlay timing over a privileged session).

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Overlays.kt:6-15` (`OverlayHost`)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellOverlays.kt:14-28` (`OverlayState`), `shell/ui/OverlayLayers.kt` (`ShellLayers`; `rememberSheetClosingSoon` added), `SetupWiring.kt` (`SessionTouches`), `MainActivity.kt:168-172`
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt:50-68` (`PinPadHost`)
- Modify: `capability/calendar/…/EventForm.kt:64-114` (`edited`), `ui/Sheets.kt:32-40` (`showEditor`), `ui/EventEditorHost.kt:69-75, 102-106`, `ui/CalendarConnectHost.kt:38-40` (`showConnect`); `core/setup/…/PersonEditorSheet.kt:55-79` (`PersonForm`), `:85-89` (`showPersonEditor`), `:91-131` (`PersonEditorHost`)
- Modify (fakes): `capability/calendar/src/test/…/ui/RecordingOverlay.kt`, `core/setup/src/test/…/RecordingOverlay.kt`
- Test: `core/access/src/test/…/ui/PinPadTest.kt` (its `show`); `capability/calendar/src/test/…/EventFormTest.kt`, `ui/EventEditorHostTest.kt`, `ui/CalendarConnectHostTest.kt`; `core/setup/src/test/…/PersonFormTest.kt` (create), `PeopleUiTest.kt`; `app/src/test/…/shell/ui/ShellLayersTimeoutTest.kt`, `AppContentTest.kt`, `ShellScreenshotTest.kt`
- Baselines (new): `app/src/test/screenshots/sheet_warning_dark.png`

**Interfaces:**
- Consumes: `touch(holdForEdits)`, `ShellLayers(…, settingsExpiresAt, nowMillis, …)`, `ClosingWarningLayer`, `SessionTouches` (Tasks 9, 10).
- Produces:
  - `interface OverlayHost { fun show(closesWhenIdle: Boolean = true, content: @Composable () -> Unit); fun markEdited(edited: Boolean); fun dismiss() }` — `markEdited`, not `setEdited`: a `var edited` property's setter is already `setEdited(Z)V` on the JVM.
  - `OverlayState.closesWhenIdle` and `OverlayState.edited` (Compose state, reset by `show` and `dismiss`).
  - `internal const val SHEET_CLOSING` in `OverlayLayers.kt`.
  - `@Composable internal fun SessionTouches(activity: UserActivity, counting: Boolean, edited: Boolean, onTouch: (holdForEdits: Boolean) -> Unit)` (replaces Task 9's three-parameter form).
  - `fun PinPadHost(controller: PinPromptController, activity: UserActivity, overSheet: Boolean = false, overSettings: Boolean = false)`.
  - `EventForm.edited: Boolean`, `PersonForm.edited: Boolean`; `EventEditorHost(…, onEdited: (Boolean) -> Unit = {})` and `PersonEditorHost(…, onEdited: (Boolean) -> Unit)`, called with "edited, or saving".

The rules (spec §6.3, the amendment, the plan review's P1, P4, P7):
- A sheet with nothing to lose closes after `timeout(OVERLAY_IDLE_MS)` without a touch; an edited one after `timeout(EDIT_HOLD_MS)`, with the sheet warning 20 s before; a sheet that is saving counts as edited, so a save in flight is never cut off; the connecting card never closes by itself.
- While Settings is open, no overlay closes by itself: Settings' own session ends first and takes its sheet with it (AppContent), and an overlay closing first would move TalkBack's focus, which counts as a touch and would stretch the session. The PIN pad over Settings waits a second longer than the session for the same reason.
- `SessionTouches` passes the sheet's state with every touch, and re-arms the session whenever that state changes while counting: the first change holds it (ruling 22: typing goes to the keyboard's window), and an edit undone, a sheet closed by Close, the scrim or Cancel, puts it back on 2 minutes at once (P1). Review Focus 2.

- [ ] **Step 1: Write the failing tests**

`EventFormTest.kt`, add:
```kotlin
    @Test
    fun aNewSheetWithItsDefaultsIsNotEditedAndATitleMakesItSo() {
        val form = EventForm(EventForm.Mode.New, today, LocalTime.of(11, 54), london, signedIn = null, preselectedDay = null)
        assertThat(form.edited).isFalse()
        form.updateTitle("Swim")
        assertThat(form.edited).isTrue()
    }

    /** Review Focus 2. */
    @Test
    fun anEditChangedBackIsNotEdited() {
        val form = EventForm(EventForm.Mode.New, today, LocalTime.of(11, 54), london, signedIn = null, preselectedDay = null)
        form.updateTitle("Swim")
        form.updateTitle("")
        form.chooseLength(java.time.Duration.ofHours(2))
        form.chooseLength(java.time.Duration.ofHours(1))
        assertThat(form.edited).isFalse()
    }
```
(`today` and `london` are the file's own fixtures; if they are named otherwise there, use those.) Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/PersonFormTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.ui.PersonPalette

class PersonFormTest {
    private val sam = Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), Role.ADULT, hasPin = false)

    @Test
    fun aSheetIsEditedOnlyWhileSomethingDiffersFromHowItOpened() {
        val form = PersonForm(sam, taken = emptySet())
        assertThat(form.edited).isFalse()
        form.name = "Samuel"
        assertThat(form.edited).isTrue()
        form.name = "Sam"
        assertThat(form.edited).isFalse()
        form.choosePin("2468")
        assertThat(form.edited).isTrue()
    }
}
```
Fails today: no `edited`.

The hosts report it (T6 of the plan review): both `RecordingOverlay`s now record `edited` and `closesWhenIdle` (Step 2). `EventEditorHostTest.kt`, add:
```kotlin
    @Test
    fun typingATitleMarksTheSheetEditedAndClearingItDoesNot() {
        showOverlay { showEditor(EditorRequest.New(day = null), day, repo, editor) }
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Swim")
        compose.waitUntil(5_000) { overlay.edited }
        compose.onNodeWithTag("editor_title").performTextReplacement("")
        compose.waitUntil(5_000) { !overlay.edited }
        assertThat(overlay.closesWhenIdle).isTrue()
    }
```
(Task 12 changes "New event" to "Add event": its sweep script updates this line with the others.) `PeopleUiTest.kt`, add:
```kotlin
    @Test
    fun typingANameMarksThePersonSheetEdited() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("person_name").performTextInput("Sam")
        compose.waitUntil(5_000) { overlay.edited }
        compose.onNodeWithTag("person_name").performTextReplacement("")
        compose.waitUntil(5_000) { !overlay.edited }
    }
```
`CalendarConnectHostTest.kt`, after line 118's `showConnect(…)` in its first test: `assertThat(overlay.closesWhenIdle).isFalse()`.
Each fails today: no `edited` or `closesWhenIdle` on the fakes; nothing reports them. Afterwards they fail if a host stops reporting, or `showConnect` loses `closesWhenIdle = false`.

`PinPadTest.kt`: `show` takes a fake (imports `uk.co.siland.culvery.core.plugin.test.FakeUserActivity`, `androidx.compose.runtime.getValue`, `mutableStateOf`, `setValue`):
```kotlin
    private val activity = FakeUserActivity()

    private fun show(overSheet: Boolean = false, overSettings: Boolean = false) =
        compose.setContent { CulveryTheme(dark = true) { PinPadHost(controller, activity, overSheet, overSettings) } }

    /** 4d-2 §6.3: a pad left alone cancels, so authorise returns null; a touch starts the wait again; a lockout doesn't stop it. */
    @Test
    fun anIdlePadCancelsAtTwoMinutesAndATouchKeepsIt() {
        compose.mainClock.autoAdvance = false
        val request = controller.open("Change settings", PinError.WrongPin, System.currentTimeMillis() + 300_000)
        show()
        compose.mainClock.advanceTimeBy(60_000)
        activity.touch()
        compose.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(119_900)
        assertThat(request.answer.isCompleted).isFalse()
        compose.mainClock.advanceTimeBy(200)
        assertThat(request.answer.getCompleted()).isNull()
    }

    /** P4: over Settings the pad outlasts the session by a second, so the session always ends first. */
    @Test
    fun overSettingsThePadWaitsASecondLonger() {
        compose.mainClock.autoAdvance = false
        val request = controller.open("Change settings", null, null)
        show(overSettings = true)
        compose.mainClock.advanceTimeBy(120_900)
        assertThat(request.answer.isCompleted).isFalse()
        compose.mainClock.advanceTimeBy(200)
        assertThat(request.answer.getCompleted()).isNull()
    }
```
Fails today: the pad never closes by itself.

`ShellLayersTimeoutTest.kt` (Task 10's), add (imports `androidx.compose.foundation.layout.PaddingValues`, `androidx.compose.ui.test.performClick`, `uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS`, `uk.co.siland.culvery.core.plugin.OVERLAY_IDLE_MS`, `uk.co.siland.culvery.core.ui.HhSheet`):
```kotlin
    private fun sheet(edited: Boolean = false, closesWhenIdle: Boolean = true) {
        compose.runOnIdle {
            overlay.show(closesWhenIdle) { HhSheet(PaddingValues(), Modifier.testTag("sheet")) { Text("Sheet body") } }
            if (edited) overlay.markEdited(true)
        }
        settle()
    }

    @Test
    fun anUntouchedSheetClosesAtTwoMinutesAndNotBefore() {
        show()
        sheet()
        after(OVERLAY_IDLE_MS - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    @Test
    fun aTouchInsideTheSheetStartsTheWaitAgain() {
        show()
        sheet()
        after(OVERLAY_IDLE_MS - 10_000)
        activity.touch()
        settle()
        after(OVERLAY_IDLE_MS - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    @Test
    fun anEditedSheetWarnsAt940AndClosesAtTenMinutes() {
        show()
        sheet(edited = true)
        after(EDIT_HOLD_MS - 20_000 - 100)
        assertThat(open("sheet_warning")).isFalse()
        after(100)
        compose.onNodeWithTag("sheet_warning")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assertTextEquals("This closes in 20 seconds. Touch the screen to keep your changes.")
        after(20_000 - 200)
        assertThat(open("sheet")).isTrue()
        after(200)
        assertThat(open("sheet")).isFalse()
        assertThat(open("sheet_warning")).isFalse()
    }

    @Test
    fun aTouchHidesTheSheetWarningAndStartsTheTenMinutesAgain() {
        show()
        sheet(edited = true)
        after(EDIT_HOLD_MS - 10_000)
        assertThat(open("sheet_warning")).isTrue()
        activity.touch()
        settle()
        assertThat(open("sheet_warning")).isFalse()
        after(EDIT_HOLD_MS - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    /** Review Focus 2: undone, the sheet has nothing to lose again: 2 minutes and no warning. */
    @Test
    fun aSheetWhoseEditsAreUndoneClosesAtTwoMinutesWithoutAWarning() {
        show()
        sheet(edited = true)
        compose.runOnIdle { overlay.markEdited(false) }
        settle()
        after(OVERLAY_IDLE_MS - 100)
        assertThat(open("sheet")).isTrue()
        assertThat(open("sheet_warning")).isFalse()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    @Test
    fun theConnectingCardIsStillOpenAtThirtyMinutes() {
        show()
        sheet(closesWhenIdle = false)
        after(30 * 60_000L)
        assertThat(open("sheet")).isTrue()
    }

    @Test
    fun withTalkBacksLongerTimeAnUntouchedSheetStaysFiveMinutes() {
        activity.timeouts = { base, _ -> if (base == OVERLAY_IDLE_MS) 300_000L else base }
        show()
        sheet()
        after(300_000L - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    /** Review Focus 4: the pad over the sheet comes and goes; a tap on it is a touch; the edited sheet keeps its ten minutes. */
    @Test
    fun anEditedSheetStaysWhileAPadOverItComesAndGoes() {
        show()
        sheet(edited = true)
        pinShowing = true
        settle()
        after(OVERLAY_IDLE_MS)
        compose.onNodeWithTag("pad").performClick()
        settle()
        pinShowing = false
        settle()
        after(EDIT_HOLD_MS - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    /** P4: inside Settings no overlay closes by itself; Settings' own end takes it. */
    @Test
    fun insideSettingsASheetDoesNotCloseByItselfAndOnlySettingsWarns() {
        show()
        expiresAt = compose.mainClock.currentTime + EDIT_HOLD_MS
        sheet(edited = true)
        after(EDIT_HOLD_MS - 10_000)
        assertThat(open("settings_warning")).isTrue()
        assertThat(open("sheet_warning")).isFalse()
        after(OVERLAY_IDLE_MS + EDIT_HOLD_MS)
        assertThat(open("sheet")).isTrue()
    }

    @Test
    fun insideSettingsAnUneditedSheetOutlastsTheOverlayIdleTime() {
        show()
        expiresAt = compose.mainClock.currentTime + 60 * 60_000L
        sheet()
        after(OVERLAY_IDLE_MS + 100)
        assertThat(open("sheet")).isTrue()
    }
```
(`insideSettings…` set `expiresAt` far ahead: the session itself is `DefaultAccessControl`'s, which these tests don't run; here it only marks Settings as open.) Fail today: no `closesWhenIdle` or `markEdited`; nothing closes or warns. `aTouchInsideTheSheetStartsTheWaitAgain` and `aTouchHidesTheSheetWarningAndStartsTheTenMinutesAgain` check the new boundary as well as the old, so a touch that stopped the timer without restarting it fails them.

`AppContentTest.kt` (imports as needed): the `show` content's `SessionTouches` call becomes `SessionTouches(activity, countsTouches(complete, settingsOpen), overlay.edited) { hold -> access.touch(hold) }`. `SessionTouches` now re-arms the session once when counting starts, so the two tests that count touches count from after the first frame: in `aTapInsideAnOpenSheetCountsAsATouch` and `aUserActivityTouchWhileSettingsIsOpenReachesTheSession`, add `val before = compose.runOnIdle { access.touches }` after the sheet shows (or after `show()`), and assert `isEqualTo(before + 1)`. Add:
```kotlin
    /** Typing goes to the keyboard's window, which Culvery's touch detector never sees, so the sheet's first change holds the session. */
    @Test
    fun anOpenSheetsFirstChangeHoldsTheSettingsSession() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.runOnIdle { overlay.markEdited(true) }
        compose.waitForIdle()
        assertThat(access.holds.last()).isTrue()
    }

    /** Review Focus 2 and P1: edits undone, or the sheet closed, put the session straight back on two minutes. */
    @Test
    fun closingAnEditedSheetPutsSettingsBackOnTwoMinutes() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.runOnIdle { overlay.markEdited(true) }
        compose.waitForIdle()
        compose.runOnIdle { overlay.dismiss() }
        compose.waitForIdle()
        assertThat(access.holds.takeLast(2)).containsExactly(true, false).inOrder()
    }

    @Test
    fun undoingTheEditsPutsSettingsBackOnTwoMinutes() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.runOnIdle { overlay.markEdited(true) }
        compose.waitForIdle()
        compose.runOnIdle { overlay.markEdited(false) }
        activity.touch()
        compose.waitForIdle()
        assertThat(access.holds.takeLast(3)).containsExactly(true, false, false).inOrder()
    }
```
and `closingSettingsDismissesItsSheet` (96-105) gains `compose.runOnIdle { overlay.markEdited(true) }` after its `overlay.show { … }` line and `assertThat(overlay.edited).isFalse()` at its end (the closing takes an edited sheet with it, and resets it).
`ShellScreenshotTest.kt`, add:
```kotlin
    @Test
    fun sheetWarningDark() = snap("sheet_warning_dark", dark = true) { ClosingWarningLayer(SHEET_CLOSING, "sheet_warning") }
```

- [ ] **Step 2: The fakes**

Both `RecordingOverlay`s:
```kotlin
    var edited = false
        private set
    var closesWhenIdle = true
        private set

    override fun show(closesWhenIdle: Boolean, content: @Composable () -> Unit) {
        this.content = content
        this.closesWhenIdle = closesWhenIdle
        edited = false
    }

    override fun markEdited(edited: Boolean) {
        this.edited = edited
    }
```
(the calendar one keeps `dismissed`; both `dismiss()` also set `edited = false`.) `edited` and `closesWhenIdle` are plain fields: the tests read them from `waitUntil`, which polls.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew --continue :core:access:testDebugUnitTest --tests "*PinPadTest*" :capability:calendar:testDebugUnitTest --tests "*EventFormTest*" --tests "*EventEditorHostTest*" --tests "*CalendarConnectHostTest*" :core:setup:testDebugUnitTest --tests "*PersonFormTest*" --tests "*PeopleUiTest*" :app:testDebugUnitTest --tests "*ShellLayersTimeoutTest*" --tests "*AppContentTest*"`
Expected: FAIL to compile on the missing members.

- [ ] **Step 4: Overlays that report edits and close when left**

`Overlays.kt`, `OverlayHost`:
```kotlin
/**
 * Lets capability UI show a sheet above the rail and content without depending on :app. The shell draws a scrim
 * that dismisses on tap, and places [show]'s content against the right edge, full height. An overlay left alone closes
 * after 2 minutes, or 10 while it has unsaved changes (4d-2 §6.3).
 */
interface OverlayHost {
    /** Replaces anything already shown. [closesWhenIdle] false for a card waiting on a screen outside the app, where no touch can reach Culvery. */
    fun show(closesWhenIdle: Boolean = true, content: @Composable () -> Unit)

    /** The open sheet has changes that closing it would lose, or is saving them; reset by each show and dismiss. */
    fun markEdited(edited: Boolean)

    fun dismiss()
}
```
`ShellOverlays.kt`, `OverlayState`:
```kotlin
/** The one sheet the shell shows above the rail and content; remembered by MainActivity. */
class OverlayState : OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    var closesWhenIdle by mutableStateOf(true)
        private set

    var edited by mutableStateOf(false)
        private set

    val isShowing: Boolean get() = content != null

    override fun show(closesWhenIdle: Boolean, content: @Composable () -> Unit) {
        this.closesWhenIdle = closesWhenIdle
        edited = false
        this.content = content
    }

    override fun markEdited(edited: Boolean) {
        this.edited = edited
    }

    override fun dismiss() {
        content = null
        edited = false
    }
}
```
`EventForm.kt`, after the `length` property (line 98):
```kotlin
    private data class Answers(val title: String, val who: PersonId, val day: LocalDate, val time: TimeChoice, val length: Duration)

    private val opening = Answers(title, who, day, time, length)

    /** Any field differs from how the sheet opened: closing it now would lose something (4d-2 §6.3). Changed back, it isn't. */
    val edited: Boolean get() = Answers(title, who, day, time, length) != opening
```
`PersonEditorSheet.kt`, `PersonForm` after `message`/`nameError`:
```kotlin
    private val opening = listOf(name, color, role)

    /** Anything differs from how the sheet opened (a PIN chosen or removed counts): closing it now would lose it. */
    val edited: Boolean get() = listOf(name, color, role) != opening || newPin != null || removePin
```
`showPersonEditor` (85-89): `show { PersonEditorHost(editor, existing, taken, lastAdmin, onClose = { dismiss() }, onEdited = ::markEdited) }`. `PersonEditorHost` (91) gains `onEdited: (Boolean) -> Unit` as its last parameter and, after `val action = …`:
```kotlin
    // Saving counts as edited, so the idle close never cuts a save off half done.
    val edited by rememberUpdatedState(onEdited)
    LaunchedEffect(form) { snapshotFlow { form.edited || action.busy }.collect { edited(it) } }
```
(imports `androidx.compose.runtime.LaunchedEffect`, `rememberUpdatedState`, `snapshotFlow`.)
`Sheets.kt:32-40`, `showEditor`'s `EventEditorHost(` call gains `onEdited = ::markEdited,`. `EventEditorHost` (69-75) gains `onEdited: (Boolean) -> Unit = {},` after `onDeleteAuthorised` (its tests leave it out), and after `val household = people ?: return` (106):
```kotlin
    // Saving counts as edited, so the idle close never cuts a save off half done.
    val edited by rememberUpdatedState(onEdited)
    LaunchedEffect(form) { snapshotFlow { form.edited || action.busy }.collect { edited(it) } }
```
(imports `androidx.compose.runtime.rememberUpdatedState`, `snapshotFlow`.)
`CalendarConnectHost.kt:38-40`:
```kotlin
// Google's chooser is a screen outside the app: no touch reaches Culvery while it is open, so the card mustn't close by itself.
internal fun OverlayHost.showConnect(request: ConnectRequest, connections: CalendarConnections): Unit = show(closesWhenIdle = false) {
    CalendarConnectHost(request, connections, onClose = { dismiss() })
}
```
`PinPad.kt`, `PinPadHost`:
```kotlin
/**
 * [overSheet]: a sheet is open, so the pad covers the sheet's area rather than the whole screen. Left alone, it cancels;
 * [overSettings]: a second later than the session, so Settings always ends first.
 */
@Composable
fun PinPadHost(controller: PinPromptController, activity: UserActivity, overSheet: Boolean = false, overSettings: Boolean = false) {
    val request by controller.request.collectAsState()
    val touches by activity.touches.collectAsState()
    request?.let { r ->
        // As Cancel does, so authorise returns null rather than waiting; a lockout doesn't stop it (4d-2 §6.3).
        LaunchedEffect(r, touches, overSettings) {
            delay(activity.timeout(OVERLAY_IDLE_MS) + if (overSettings) SETTINGS_MARGIN_MS else 0L)
            controller.cancel()
        }
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

/** How much longer the pad waits over Settings than the session does (the plan review's P4). */
private const val SETTINGS_MARGIN_MS = 1_000L
```
(imports `androidx.compose.runtime.LaunchedEffect`, `kotlinx.coroutines.delay`, `uk.co.siland.culvery.core.plugin.OVERLAY_IDLE_MS`, `uk.co.siland.culvery.core.plugin.UserActivity`.)

- [ ] **Step 5: The shell's idle close, the sheet warning and the session's hold**

`OverlayLayers.kt`: beside `SETTINGS_CLOSING` add
```kotlin
internal const val SHEET_CLOSING = "This closes in 20 seconds. Touch the screen to keep your changes."
```
In `ShellLayers`, after `val settingsClosingSoon = …`:
```kotlin
    // Inside Settings nothing closes by itself: Settings' own end takes its sheet (AppContent).
    val sheetClosingSoon = rememberSheetClosingSoon(overlay, activity, closes = settingsExpiresAt == null)
```
and the warning line at the end of the `Box` becomes:
```kotlin
        // While Settings is open only its warning shows: both run on the same touches.
        when {
            settingsClosingSoon -> ClosingWarningLayer(SETTINGS_CLOSING, "settings_warning")
            sheetClosingSoon -> ClosingWarningLayer(SHEET_CLOSING, "sheet_warning")
        }
```
Its KDoc's warning sentence becomes "[settingsExpiresAt] is the session's end while Settings is open; 20 seconds before it, or before an edited sheet outside Settings closes, a spoken warning shows over everything." Below `rememberClosingSoon`:
```kotlin
/**
 * Closes the overlay after [OVERLAY_IDLE_MS] without a touch, or [EDIT_HOLD_MS] while it has unsaved changes, and is true
 * from [CLOSING_WARNING_MS] before an edited one goes (4d-2 §6.3, the amendment). Any touch, a TalkBack gesture's
 * included, starts it again; a card that waits on a screen outside the app, or any overlay while not [closes], stays.
 */
@Composable
private fun rememberSheetClosingSoon(overlay: OverlayState, activity: UserActivity, closes: Boolean): Boolean {
    val content = overlay.content
    val edited = overlay.edited
    val touches by activity.touches.collectAsState()
    // Keyed like the effect, so a touch hides the warning in the frame it lands in.
    var soon by remember(content, edited, touches, closes) { mutableStateOf(false) }
    LaunchedEffect(content, edited, overlay.closesWhenIdle, touches, closes) {
        if (content == null || !overlay.closesWhenIdle || !closes) return@LaunchedEffect
        if (edited) {
            delay(activity.timeout(EDIT_HOLD_MS) - CLOSING_WARNING_MS)
            soon = true
            delay(CLOSING_WARNING_MS)
        } else {
            delay(activity.timeout(OVERLAY_IDLE_MS))
        }
        overlay.dismiss()
    }
    return soon
}
```
(imports `androidx.compose.runtime.collectAsState`, `uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS`, `uk.co.siland.culvery.core.plugin.OVERLAY_IDLE_MS`.)
`SetupWiring.kt`, `SessionTouches` becomes:
```kotlin
/**
 * While [counting], every touch [activity] reports, a TalkBack gesture's included, runs [onTouch] with whether the open
 * sheet has unsaved changes ([edited]). The session is re-armed whenever [edited] changes too: typing goes to the
 * keyboard's window, so a sheet's first change counts as a touch, and edits undone or a sheet closed go back to the
 * plain two minutes at once rather than at the next tap.
 */
@Composable
internal fun SessionTouches(activity: UserActivity, counting: Boolean, edited: Boolean, onTouch: (holdForEdits: Boolean) -> Unit) {
    val touch by rememberUpdatedState(onTouch)
    val hold by rememberUpdatedState(edited)
    LaunchedEffect(activity, counting) {
        if (counting) activity.touches.drop(1).collect { touch(hold) }
    }
    LaunchedEffect(counting, edited) {
        if (counting) touch(edited)
    }
}
```
`MainActivity.kt`: the `SessionTouches` line becomes `SessionTouches(userActivity, countsTouches(complete, state.settingsOpen), overlay.edited) { hold -> access.touch(holdForEdits = hold) }`, and `pinPad = { PinPadHost(pinPrompt, userActivity, overSheet = overlay.isShowing, overSettings = state.settingsOpen) }` (172).

- [ ] **Step 6: Run the tests to see them pass**

Run the Step 3 command. Expected: PASS. Then `./gradlew :core:plugin:testDebugUnitTest :core:access:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest :provider:calendar-google:testDebugUnitTest :app:testDebugUnitTest`. A test that leaves a sheet or a PIN pad open across a long virtual wait now sees it close: if so, it touches first or opens it after the wait.

- [ ] **Step 7: Record the sheet warning baseline and look at it**

```bash
./gradlew :app:compareRoborazziDebug --tests "*ShellScreenshotTest*"
find app/build/outputs/roborazzi -name "*_compare.png" -mmin -30
```
Expected: no difference in the existing images. Then `./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest*"` and open `app/src/test/screenshots/sheet_warning_dark.png`: the same pill as `settings_warning_dark` with "This closes in 20 seconds. Touch the screen to keep your changes.", nothing clipped. Run the baseline check with `sheet_warning_dark` as the one expected name. Anything else: stop.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add core/access core/plugin core/setup capability/calendar app
git commit -m "Close sheets and PIN pads left alone, never inside Settings or mid-save, warn twenty seconds before a sheet with changes closes, and let a sheet's changes hold the Settings session"
```

---

### Task 12: The vocabulary and quotes sweep (D33, Decisions 2, 12; rulings 2, 9, 17)

**Review:** sonnet.

**Files:**
- Modify (copy, per Appendix A rows owned by this task; see Step 3): `core/setup/…/SetupCopy.kt:15, 17, 24, 27, 29, 52, 85-86`, `pages/KioskPage.kt:13-43`; `core/access/…/AccessControl.kt:15-26`, `Permissions.kt:28-29`, `ui/PinPad.kt:94, 113`; `app/…/KioskLifecycle.kt:41-48`; `capability/calendar/…/CalendarUi.kt:14-17, 19-50, 69-81, 100-122, 231-236, 259, 264`, `CalendarRepository.kt:46-54`, `CalendarEditor.kt:46`, `CalendarPermissions.kt:24, 48`, `CalendarReview.kt:31-36`, `CalendarSetup.kt:25`, `SourceRefresher.kt:20-27`, `Writes.kt:126-132`, `ui/CalendarConnectHost.kt:33`, `ui/ConnectCalendarCard.kt:48`, `ui/EventDetailSheet.kt:53-55, 96-135, 139-145, 164-168, 186-212`, `ui/EventEditorSheet.kt` (the title, placeholder, Save label), `ui/ReviewCalendars.kt:57-81, 262-275, 314-318`, `ui/TodayCard.kt:59`, `ui/ComingUpCard.kt:88`, `ui/WeekView.kt:78-79`; `capability/weather/…/WeatherWords.kt:11`; `provider/calendar-google/…/GoogleHttp.kt:28`, `GoogleConnectFlow.kt:22`; `provider/calendar-fake/…/FakeCalendarProvider.kt:146, 193`, `SampleEvents.kt:61`; `core/plugin/…/Setup.kt:11`
- Test: the test sweep (Step 4) and the named manual edits; `core/setup/src/test/…/SetupScreenshotTest.kt:56, 141, 146` (titles from the constants)
- Baselines (changed): most screens' words (Step 6 lists them by name)

**Interfaces:**
- Consumes: everything before; `Badge.spoken` (Task 8); `refusedHere` and `PinError.NotAllowed(message)` (Task 10, already curly).
- Produces:
  - `fun updatedLabel(lastSyncMillis: Long?, nowMillis: Long, service: String? = null): String` ("Updated 5 minutes ago", "Updated from Google Calendar 5 minutes ago", "Not updated yet") replaces `syncedLabel`; `weekSubtitle(sync, nowMillis)` is it with the one service.
  - `SyncStatusUi(lastSyncMillis: Long?, needsSignIn: List<String>, serviceNames: List<String>, failingBeforeFirstSync: Boolean, reconnect: Connection? = null)` — both lists hold service names (ruling 17).
  - `EventUi` loses `connectionLabel`; `CALENDAR_FEED` goes; `createdByLabel(createdBy, reason, people, serviceName, sourceName)`.
  - Setup copy constants renamed: `TABLET_LOCK`, `USE_OTHER_APPS`; calendar: `MAIN`, `USE_FOR_NEW_EVENTS`, `CHOOSE_ANOTHER_MAIN`; `KEEP` (the plugin's) used in the detail sheet.
  - After this task no literal in a main source holds a system word or a straight quote: Task 13's `CopyScanTest` must pass on its first run.

- [ ] **Step 1: Write the failing copy tests**

Rewrite `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/SyncLabelsTest.kt`'s label tests (lines 10-45):
```kotlin
    @Test
    fun underAMinuteIsJustNow() = assertThat(updatedLabel(now - 59_000, now)).isEqualTo("Updated just now")

    @Test
    fun minutesInFullWordsSingularAtOne() {
        assertThat(updatedLabel(minutesAgo(1), now)).isEqualTo("Updated 1 minute ago")
        assertThat(updatedLabel(minutesAgo(59), now)).isEqualTo("Updated 59 minutes ago")
    }

    @Test
    fun hoursUnderFortyEightThenDays() {
        assertThat(updatedLabel(minutesAgo(60), now)).isEqualTo("Updated 1 hour ago")
        assertThat(updatedLabel(minutesAgo(150), now)).isEqualTo("Updated 2 hours ago")
        assertThat(updatedLabel(minutesAgo(48 * 60 - 1), now)).isEqualTo("Updated 47 hours ago")
        assertThat(updatedLabel(minutesAgo(48 * 60), now)).isEqualTo("Updated 2 days ago")
    }

    @Test
    fun neverUpdatedSaysSo() = assertThat(updatedLabel(null, now)).isEqualTo("Not updated yet")

    @Test
    fun aClockThatWentBackwardsReadsJustNow() = assertThat(updatedLabel(now + 3_600_000, now)).isEqualTo("Updated just now")

    @Test
    fun namesTheServiceWhenGivenOne() {
        assertThat(updatedLabel(minutesAgo(5), now, "Google Calendar")).isEqualTo("Updated from Google Calendar 5 minutes ago")
        assertThat(updatedLabel(null, now, "Google Calendar")).isEqualTo("Not updated yet")
    }

    @Test
    fun theSubtitleNamesTheServiceOnlyWhenEveryConnectionHasTheSameOne() {
        val one = SyncStatusUi(minutesAgo(5), emptyList(), listOf("Google Calendar"), failingBeforeFirstSync = false)
        assertThat(weekSubtitle(one, now)).isEqualTo("Updated from Google Calendar 5 minutes ago")
        assertThat(weekSubtitle(one.copy(serviceNames = listOf("Google Calendar", "Google Calendar")), now)).isEqualTo("Updated from Google Calendar 5 minutes ago")
        assertThat(weekSubtitle(one.copy(serviceNames = listOf("Google Calendar", "School")), now)).isEqualTo("Updated 5 minutes ago")
        assertThat(weekSubtitle(one.copy(lastSyncMillis = null), now)).isEqualTo("Not updated yet")
    }

    @Test
    fun theReconnectChipCountsAccounts() {
        assertThat(reconnectLabel(listOf("Google Calendar"))).isEqualTo("Google Calendar needs reconnecting")
        assertThat(reconnectLabel(listOf("Google Calendar", "Google Calendar"))).isEqualTo("2 accounts need reconnecting")
    }
```
(import `uk.co.siland.culvery.capability.calendar.ui.reconnectLabel`.)
`DefaultAccessControlTest.kt`: `pinReasonTextNamesTheActionOrThePermission` (347-353) becomes:
```kotlin
    @Test
    fun pinReasonTextIsExplicitCopyForEachReason() {
        val suffix = " It also records who made the change."
        assertThat(pinReasonText(PinReason.Save, "x")).isEqualTo("Enter your PIN to add this event.$suffix")
        assertThat(pinReasonText(PinReason.Edit, "x")).isEqualTo("Enter your PIN to change this event.$suffix")
        assertThat(pinReasonText(PinReason.Delete, "x")).isEqualTo("Enter your PIN to delete this event.$suffix")
        assertThat(pinReasonText(PinReason.Assign, "x")).isEqualTo("Enter your PIN to choose who this event is for.$suffix")
        assertThat(pinReasonText(PinReason.Generic, "Use other apps")).isEqualTo("Enter your PIN to use other apps.")
        assertThat(pinReasonText(PinReason.Generic, "Add and change people")).isEqualTo("Enter your PIN to add and change people.")
    }
```
`KioskLifecycleTest.kt:146-149` and `:153-156` become:
```kotlin
        assertThat(HOME_APP_SCREEN_MISSING).isEqualTo("Culvery can’t open the home app setting.")
```
```kotlin
    @Test
    fun theHomeAppToastsAreShort() {
        assertThat(HOME_APP_NOT_SET).isEqualTo("Culvery isn’t the home app yet.")
    }
```
`CalendarReviewTest.theDisconnectQuestionCountsWhatIsDropped` (182-188) becomes:
```kotlin
    @Test
    fun theDisconnectQuestionSaysWhatWillBeLost() {
        assertThat(disconnectQuestion("Google Calendar", 0)).isEqualTo("Disconnect Google Calendar? Its calendars stop showing here.")
        assertThat(disconnectQuestion("Google Calendar", 1))
            .isEqualTo("Disconnect Google Calendar? Its calendars stop showing here, and 1 change not yet saved to Google Calendar will be lost.")
        assertThat(disconnectQuestion("Google Calendar", 3))
            .isEqualTo("Disconnect Google Calendar? Its calendars stop showing here, and 3 changes not yet saved to Google Calendar will be lost.")
    }
```
`SourceRefresherTest.kt`: add
```kotlin
    @Test
    fun theMainCalendarGoneToastSaysWhoCanFixItAndWhatWasLost() {
        val gone = "Google Calendar’s main calendar has gone. An Admin can choose another in Settings › Calendars."
        assertThat(masterGone("Google Calendar", 0)).isEqualTo(gone)
        assertThat(masterGone("Google Calendar", 1)).isEqualTo("$gone 1 unsaved change was lost.")
        assertThat(masterGone("Google Calendar", 3)).isEqualTo("$gone 3 unsaved changes were lost.")
    }
```
and its three toast assertions (96, 110, 122, 133) compare with `masterGone("Google Calendar", n)` for the n each test sets up (0, 2, 1, 0).
Each fails today: the functions still say "synced", "min", "h", the enum name, "kiosk", "master", "sync" and "dropped".

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :capability:calendar:testDebugUnitTest --tests "*SyncLabelsTest*" --tests "*CalendarReviewTest*" --tests "*SourceRefresherTest*" :core:access:testDebugUnitTest --tests "*DefaultAccessControlTest*" :app:testDebugUnitTest --tests "*KioskLifecycleTest*"`
Expected: FAIL to compile on `updatedLabel`, `serviceNames` and `reconnectLabel`'s new form, and on the assertions.

- [ ] **Step 3: The sweep in the main sources**

Every edit below is Appendix A's "New" text, or (ruling 2) only the curly apostrophe for a string a later task rewrites. Copy each string exactly (curly ’ “ ”).

`core/setup/…/SetupCopy.kt`:
```kotlin
internal const val WELCOME_LINE = "Your home’s location, the people who live here, and your calendars."
internal const val WHOS_SETTING_UP = "Who’s setting this up?"
internal const val WHERES_HOME = "Where’s home?"
internal const val COULD_NOT_SEARCH = "Couldn’t search for towns — check the tablet’s Wi-Fi and try again."

internal fun noTownsMatch(query: String): String = "No towns match “$query”."

internal fun removeQuestion(name: String): String = "Remove $name? $name’s events and calendars show as Family."

internal const val TABLET_LOCK = "Tablet lock"
internal const val USE_OTHER_APPS = "Use other apps"
```
(`KIOSK` and `EXIT_KIOSK` go; `KIOSK_LINE` stays for Task 16.) `pages/KioskPage.kt`: `override val title = TABLET_LOCK`, `StepTitle(TABLET_LOCK, KIOSK_LINE)`, the exit button `HhButton(USE_OTHER_APPS, navigator::exitKiosk, Modifier.testTag("settings_exit_kiosk"), on = ButtonSurface.Bg)`; the KDoc: "Settings › Tablet lock (4a design §4.7, 4c §5.1; 4d-2 Decision 2b): the home app — Choose it while Culvery isn't, Change it (fresh PIN) while it is — and Use other apps, which asks for a fresh PIN." (the page id stays `"kiosk"`, §7.6).
`core/plugin/…/Setup.kt:11`: `const val COULD_NOT_SAVE = "Couldn’t save — try again."`
`core/access/…/Permissions.kt:28-29`:
```kotlin
        PermissionDef(CorePermissions.PEOPLE_MANAGE, "Add and change people", setOf(Role.ADMIN), freshPin = true),
        PermissionDef(CorePermissions.KIOSK_EXIT, "Use other apps", setOf(Role.ADMIN), freshPin = true),
```
`AccessControl.kt:18-26`:
```kotlin
/** The PIN pad's reason line, from explicit copy, never an enum's name (4d-2 §7.1.2, C60). [label] is the permission's, for [PinReason.Generic]. */
fun pinReasonText(reason: PinReason, label: String): String = when (reason) {
    PinReason.Generic -> "Enter your PIN to ${label.replaceFirstChar { it.lowercase() }}."
    PinReason.ContinueSetup -> CONTINUE_SETUP
    PinReason.Save -> eventReason("add this event")
    PinReason.Edit -> eventReason("change this event")
    PinReason.Delete -> eventReason("delete this event")
    PinReason.Assign -> eventReason("choose who this event is for")
}

private fun eventReason(what: String): String = "Enter your PIN to $what. It also records who made the change."
```
`ui/PinPad.kt`: `title = "Who’s this?"`; `const val PINS_DIDNT_MATCH = "Those PINs didn’t match — try again."`.
`app/…/KioskLifecycle.kt` (file lines 41-48):
```kotlin
/** Told when the home-app choice was declined or never shown (4c §5.1); Settings › Tablet lock says how to do it (4d-2 §7.8). */
internal const val HOME_APP_NOT_SET = "Culvery isn’t the home app yet."

/** Told when Change home app finds no screen to open; Settings › Tablet lock says where else to change it. */
internal const val HOME_APP_SCREEN_MISSING = "Culvery can’t open the home app setting."
```
`capability/calendar/…/CalendarUi.kt`:
- line 16 `const val ADDED_FROM_PHONE = "Added from a phone"`; line 17 (`CALENDAR_FEED`) goes.
- `EventUi`: delete `connectionLabel` and its KDoc (34-35); `createdBy`'s KDoc becomes "A person's name, [ADDED_FROM_PHONE], the service's name, or the calendar's own name for one nobody here can change."
- `SyncStatusUi`:
```kotlin
/**
 * [lastSyncMillis] is the oldest successful update across connections, so one failing calendar can't hide.
 * [serviceNames] names every connection's service (Decision 12). [failingBeforeFirstSync] is true when a connection has
 * never been updated and is not Ok, which makes the status stale however recent the others are.
 */
data class SyncStatusUi(
    val lastSyncMillis: Long?,
    val needsSignIn: List<String>,
    val serviceNames: List<String>,
    val failingBeforeFirstSync: Boolean,
    /** The first connection that needs signing in again: what the reconnect chip reconnects (3a design §4.3). */
    val reconnect: Connection? = null,
)
```
- `syncedLabel` (100-113) becomes:
```kotlin
/** "Updated 5 minutes ago", "Updated from Google Calendar 5 minutes ago", or "Not updated yet" (4d-2 §7.3: full words). */
fun updatedLabel(lastSyncMillis: Long?, nowMillis: Long, service: String? = null): String {
    if (lastSyncMillis == null) return "Not updated yet"
    // A wall clock that jumped backwards gives a negative age; treat it as fresh.
    val minutes = (nowMillis - lastSyncMillis).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val ago = when {
        minutes < 1 -> "just now"
        minutes < 60 -> plural(minutes, "minute") + " ago"
        hours < 48 -> plural(hours, "hour") + " ago"
        else -> plural(hours / 24, "day") + " ago"
    }
    return if (service == null) "Updated $ago" else "Updated from $service $ago"
}

/** "1 minute", "5 minutes". */
internal fun plural(count: Long, unit: String): String = if (count == 1L) "1 $unit" else "$count ${unit}s"
```
- `weekSubtitle` (121-122):
```kotlin
/** The week's line: the service named when every connection has the same one (Decision 12). */
fun weekSubtitle(sync: SyncStatusUi, nowMillis: Long): String =
    updatedLabel(sync.lastSyncMillis, nowMillis, sync.serviceNames.distinct().singleOrNull())
```
- `createdByLabel` (230-236):
```kotlin
/** Only the master calendar carries the tablet's tags; another writable calendar was filled in [serviceName]; one nobody here can change is named itself. */
internal fun createdByLabel(createdBy: String?, reason: ReadOnlyReason?, people: Map<PersonId, Person>, serviceName: String, sourceName: String): String =
    when (reason) {
        ReadOnlyReason.OtherCalendar -> sourceName
        ReadOnlyReason.NotMaster -> serviceName
        else -> createdBy?.let { people[PersonId(it)]?.name } ?: ADDED_FROM_PHONE
    }
```
- `toUi`: delete `connectionLabel = catalog.label(connectionId),` (259); line 264 becomes `createdBy = createdByLabel(createdBy, reason, people, serviceName, source?.source?.name.orEmpty()),`. Delete `SourceCatalog.label` (222) if nothing else uses it.
`CalendarRepository.kt:46-54`:
```kotlin
    val syncStatus: Flow<SyncStatusUi> = store.connections().map { connections ->
        val service = { c: StoredConnection -> serviceNameOf(c.connection, serviceNames::get) }
        SyncStatusUi(
            lastSyncMillis = connections.mapNotNull { it.lastSyncMillis }.minOrNull(),
            needsSignIn = connections.filter { it.health == ConnectionHealth.NeedsSignIn }.map(service),
            serviceNames = connections.map(service),
            failingBeforeFirstSync = connections.any { it.lastSyncMillis == null && it.health != ConnectionHealth.Ok },
            reconnect = connections.firstOrNull { it.health == ConnectionHealth.NeedsSignIn }?.connection,
        )
    }.distinctUntilChanged()
```
`CalendarEditor.kt:46`: `const val COULD_NOT_OPEN = "Couldn’t open the event — try again"`.
`CalendarPermissions.kt:24`: `PermissionDef(CalendarPermissions.ASSIGN, "Choose who events are for", setOf(Role.ADMIN, Role.ADULT)),`; line 48: `const val ASK_AN_ADULT = "Ask a grown-up to choose who this is for."`.
`CalendarReview.kt:31-36`:
```kotlin
/** One change reads in the singular; "will be lost", never "dropped" (4d-2 Decision 2c). */
fun disconnectQuestion(service: String, queued: Int): String = "Disconnect $service? Its calendars stop showing here" + when (queued) {
    0 -> "."
    1 -> ", and 1 change not yet saved to $service will be lost."
    else -> ", and $queued changes not yet saved to $service will be lost."
}
```
`CalendarSetup.kt:25`: `fun couldNotConnect(service: String): String = "Couldn’t connect to $service — try again"`.
`SourceRefresher.kt:20-27`:
```kotlin
/**
 * 3a design D7, 4a design §4.5: the main calendar went, or became read-only here, so the add buttons hide until another
 * is chosen; 4c C7 adds the queued changes that went with it. 4d-2 C85: who can fix it, in the household's words.
 */
fun masterGone(serviceName: String, droppedChanges: Int): String =
    "$serviceName’s main calendar has gone. An Admin can choose another in Settings › Calendars." + when (droppedChanges) {
        0 -> ""
        1 -> " 1 unsaved change was lost."
        else -> " $droppedChanges unsaved changes were lost."
    }
```
`Writes.kt:126-132`:
```kotlin
/** "Couldn’t save to {label} — {reason}", or just "Couldn’t save to {label}" when there is no reason. */
fun couldNotSave(label: String, reason: String?): String =
    if (reason == null) "Couldn’t save to $label" else "Couldn’t save to $label — $reason"

/** One toast for everything a drain pass dropped for [label]; [reasons] has one entry per change. */
internal fun couldNotSaveAll(label: String, reasons: List<String?>): String =
    if (reasons.size == 1) couldNotSave(label, reasons.single()) else "Couldn’t save ${reasons.size} changes to $label"
```
`ui/CalendarConnectHost.kt:33`: `internal const val CONNECTING_LINE = "Choose the family’s Google account and allow access."`.
`ui/ConnectCalendarCard.kt:48`: `Text("Connect your family’s calendar to see it here.", style = HhType.secondary, color = c.mute)`.
`ui/EventDetailSheet.kt`:
- `calendarLabel` (53-55):
```kotlin
/** The Calendar row's value: the source's name, said to be out of reach when the tablet can't change it (Decision 2g). */
internal fun calendarLabel(event: EventUi): String = when (event.readOnlyReason) {
    ReadOnlyReason.OtherCalendar, ReadOnlyReason.NotMaster -> "${event.sourceName} (can’t change here)"
    else -> event.sourceName
}
```
- the notes (96-135):
```kotlin
            when {
                e.readOnlyReason == ReadOnlyReason.OtherCalendar || e.readOnlyReason == ReadOnlyReason.NotMaster -> Note(
                    icon = Icons.LOCK,
                    title = CANT_CHANGE_HERE,
                    body = if (e.readOnlyReason == ReadOnlyReason.OtherCalendar) {
                        "This calendar can only be changed by whoever runs it."
                    } else {
                        "Change events on this calendar in ${e.serviceName} on your phone."
                    },
                )
                e.readOnlyReason == ReadOnlyReason.Recurring -> Note(
                    icon = Icons.EVENT_REPEAT,
                    title = "Repeating event",
                    body = "Change repeating events in ${e.serviceName} on your phone.",
                )
                e.untagged -> Note(
                    icon = Icons.MOBILE,
                    title = "Added from a phone",
                    body = "Showing as Family until someone chooses who it’s for.",
                    background = c.accentSoft,
                    iconTint = c.accent,
                ) {
                    Spacer(Modifier.height(CalendarDimens.assignTop))
                    if (mode == DetailMode.ChoosingPerson) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.personChipGap),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            people.forEach { person ->
                                HhPersonChip(person.name, Color(person.color), "assign_${person.name}", enabled = !busy, clickLabel = showAsEvent(person.name)) { onAssign(person) }
                            }
                        }
                    } else {
                        HhButton(
                            CHOOSE_WHO, onChoosePerson, Modifier.testTag("detail_assign"),
                            tone = ButtonTone.Primary, enabled = !busy, on = ButtonSurface.Surf,
                        )
                    }
                }
            }
```
with, above `calendarLabel`:
```kotlin
internal const val CANT_CHANGE_HERE = "Can’t change here"
internal const val CHOOSE_WHO = "Choose who it’s for"
```
- the confirm card's `keep = "Keep event"` becomes `keep = KEEP` (import `uk.co.siland.culvery.core.plugin.KEEP`); the footer's `"Edit"` becomes `"Change"`.
- `Header`: `SyncingPill(e.serviceName)`; `SyncingPill(service: String)` draws `Text("Saving to $service…", …)`.
`ui/EventEditorSheet.kt`: `val title = if (form.mode == EventForm.Mode.New) "Add event" else "Change event"`; the placeholder `"What’s happening?"`; the footer's label `form.mode == EventForm.Mode.New -> "Add event"`.
`ui/ReviewCalendars.kt`:
```kotlin
// 4a design §4.5; 4d-2 Decision 2a.
internal const val DISCONNECT = "Disconnect"
internal const val SHOW = "Show"
internal const val MAIN = "Main"
internal const val NEW_EVENTS_GO_HERE = "New events go here"
internal const val USE_FOR_NEW_EVENTS = "Use for new events"

// 4c design §6.4.
internal const val CANT_READ_CALENDAR = "Can’t read this calendar — check it’s still shared with this account."
internal const val HIDE_THIS_CALENDAR = "Hide this calendar"
internal const val CHOOSE_ANOTHER_MAIN = "Choose another main calendar first."
```
`healthWords`' Ok and Unreachable lines: `ConnectionHealth.Ok -> updatedLabel(row.lastSyncMillis, nowMillis)` and `ConnectionHealth.Unreachable -> "Can’t reach ${row.service}"` (its KDoc: "\"Updated 5 minutes ago\", \"Can’t reach Google Calendar\", …"); the uses of `MASTER`, `MAKE_MASTER`, `CHOOSE_ANOTHER_MASTER` become `MAIN`, `USE_FOR_NEW_EVENTS`, `CHOOSE_ANOTHER_MAIN` (the button's `spokenName` follows: "Use for new events, Chores"). Import `updatedLabel`; drop `syncedLabel`.
`ui/TodayCard.kt:59`: `HhButton("See the week", { navigator.openTab(CALENDAR_TAB_ID) }, on = ButtonSurface.Surf)`. `ui/ComingUpCard.kt:88`: `Text("Nothing on", …)`.
`ui/WeekView.kt:78-79`:
```kotlin
/** One chip for every connection needing sign-in: the service by name, or how many accounts (4d-2 C20). */
internal fun reconnectLabel(services: List<String>): String =
    if (services.size == 1) "${services.single()} needs reconnecting" else "${services.size} accounts need reconnecting"
```
`capability/weather/…/WeatherWords.kt:11`: `internal const val NO_FORECAST = "No forecast — check the tablet’s Wi-Fi."`.
`provider/calendar-google/…/GoogleHttp.kt:28`: `internal const val READ_ONLY_HERE = "this calendar can’t be changed from the tablet"`; `GoogleConnectFlow.kt:22`: `fun differentAccount(email: String): String = "That’s a different Google account. Reconnect with $email."`.
`provider/calendar-fake/…/FakeCalendarProvider.kt:146`: `"Repeating events can’t be changed here"`; `:193`: `"${source.name} can’t be changed here"`; `SampleEvents.kt:61`: `"Sunday lunch at Gran’s"`.

- [ ] **Step 4: The tests that find or assert the old words**

Save this script outside the repo (e.g. `$TMP/sweep_tests.py`; it isn't committed) and run it from the repo root, first without `--apply` to read what it will change, then with it:
```python
"""The vocabulary sweep's test half: the copy it changed, replaced in the tests that find or assert it. Log lines are left alone."""
import collections, pathlib, re, sys

PAIRS = [
    ("This is a subscribed calendar, so it can't be changed here.", "This calendar can only be changed by whoever runs it."),
    ('"From School terms (read-only)"', '"Can’t change here"'),
    ('"From Alex"', '"Can’t change here"'),
    ("School terms · read-only", "School terms (can’t change here)"),
    ("Exit kiosk mode", "Use other apps"),
    ('"Exit kiosk"', '"Use other apps"'),
    ('"Kiosk"', '"Tablet lock"'),
    ("Manage people", "Add and change people"),
    ('"Master"', '"Main"'),
    ("Make master", "Use for new events"),
    ('Choose another master calendar first"', 'Choose another main calendar first."'),
    ("Can't read this calendar — check it's still shared with this account\"", "Can’t read this calendar — check it’s still shared with this account.\""),
    ("Who's this?", "Who’s this?"),
    ("Those PINs didn't match", "Those PINs didn’t match"),
    ("What's happening?", "What’s happening?"),
    ('"New event"', '"Add event"'),
    ('"Edit event"', '"Change event"'),
    ('"Save event"', '"Add event"'),
    ('"Keep event"', '"Keep"'),
    ('"Edit"', '"Change"'),
    ('"Week"', '"See the week"'),
    ('"Free"', '"Nothing on"'),
    ("Added from phone", "Added from a phone"),
    ("Connect your family's calendar", "Connect your family’s calendar"),
    ("Choose the family's Google account", "Choose the family’s Google account"),
    ('"Couldn\'t save to ', '"Couldn’t save to '),
    ("Couldn't connect to ", "Couldn’t connect to "),
    ("Couldn't open the event", "Couldn’t open the event"),
    ("Couldn't save — try again.", "Couldn’t save — try again."),
    ("Couldn't search for towns — check the tablet's Wi-Fi and try again.", "Couldn’t search for towns — check the tablet’s Wi-Fi and try again."),
    ('No towns match \\"Xqz\\".', "No towns match “Xqz”."),
    ("Remove Sam? Sam's events", "Remove Sam? Sam’s events"),
    ("That's a different Google account", "That’s a different Google account"),
    ("can't be changed from the tablet", "can’t be changed from the tablet"),
    ("can't be changed here", "can’t be changed here"),
    ("No forecast — check the tablet's Wi-Fi.", "No forecast — check the tablet’s Wi-Fi."),
    ("Syncing to Sample calendar…", "Saving to Google Calendar…"),
    ("Edit repeating events", "Change repeating events"),
    ("until someone assigns it.", "until someone chooses who it’s for."),
    ('"Assign to…"', '"Choose who it’s for"'),
    ("Ask an adult to assign this event.", "Ask a grown-up to choose who this is for."),
    ("Synced 2 min ago", "Updated 2 minutes ago"),
    ("calendars need reconnecting", "accounts need reconnecting"),
]
REGEX_PAIRS = [(re.compile(r'"Couldn\'t save (\d+) changes to '), r'"Couldn’t save \1 changes to ')]
LOG_LINE = re.compile(r"\bLog\.|ShadowLog|\.msg\b|logs\b")

def main(root: str, apply: bool) -> None:
    files = [p for p in pathlib.Path(root).glob("**/src/test*/**/*.kt") if "build" not in p.parts and "build-logic" not in p.parts]
    hits = collections.Counter()
    for path in files:
        lines = path.read_text(encoding="utf-8").split("\n")
        changed = False
        for i, line in enumerate(lines):
            if LOG_LINE.search(line):
                continue
            new = line
            for old, rep in PAIRS:
                if old in new:
                    hits[old] += new.count(old)
                    new = new.replace(old, rep)
            for rx, rep in REGEX_PAIRS:
                if rx.search(new):
                    hits[rx.pattern] += 1
                    new = rx.sub(rep, new)
            if new != line:
                changed = True
                print(f"{path}:{i + 1}: {line.strip()[:90]}  ->  {new.strip()[:90]}")
                lines[i] = new
        if changed and apply:
            path.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    # A bare pair ("Edit", "Week") replaces any test literal with that text: a count far from the one expected is a stop.
    print("\nHits per pair:")
    for old, _ in PAIRS:
        print(f"{hits[old]:4}  {old}")
    for rx, _ in REGEX_PAIRS:
        print(f"{hits[rx.pattern]:4}  {rx.pattern}")
    print(f"{sum(hits.values()):4}  in all")

if __name__ == "__main__":
    main(sys.argv[1], apply="--apply" in sys.argv)
```
Run: `python -I "$TMP/sweep_tests.py" .` (about 118 lines at eb40ec0 plus the ones Tasks 1–11 added), read the list and the hits per pair, then `python -I "$TMP/sweep_tests.py" . --apply`. Each bare pair (`"Edit"`, `"Week"`, `"Free"`, `"New event"`, `"Kiosk"`, `"Master"`) must hit only lines that find or assert that copy: read each of their lines in the list, and a line that uses the word for anything else (a tag, a test name, a fixture's own data) is put back by hand after `--apply`. Put the per-pair counts in the report. Log assertions ("Couldn't read today's sun times…") are skipped: log messages keep their straight apostrophes, and the scan doesn't read them.
Then the edits the script can't make:
- `SampleUi.kt`: delete `connectionLabel = …` in `onFamilyCalendar` (97), `detailReadOnlyFeed` (106) and `detailNotMaster` (117); `detailReadOnlyFeed`'s `createdBy = CALENDAR_FEED` becomes `createdBy = "School terms"` (drop the `CALENDAR_FEED` import).
- `EventDetailSheetTest.kt:95`: `compose.onNodeWithText("Alex (can’t change here)").assertExists()` (a calendar that isn't the main one now says so too, Decision 2g); `:108`: `compose.onNodeWithText("School terms").assertExists()`.
- `CalendarRepositoryTest.kt:273`, `:276`: `status.needsSignIn` / `status.serviceNames` (the connections have no installed provider, so their services are their labels, "Google" and "School"); `:313`, `:520`: `e.serviceName` / `piano.serviceName`; `:346`: `.isEqualTo("Alex")` (the calendar's own name).
- `WhenLabelTest.kt:87-89`: `createdByLabel(null, null, people, "Google Calendar", "Family")` and `createdByLabel("alex-id", ReadOnlyReason.OtherCalendar, people, "Google Calendar", "School terms")` with `.isEqualTo("School terms")`.
- `WeekViewTest.kt`: `subtitleNamesTheOnlyConnection` expects `"Updated from Google 2 minutes ago"` and `staleSubtitleStillShowsTheAge` `"Updated from Google 45 minutes ago"`.
- `WeekScreenshotTest.kt` and calendar `FontScaleTest.kt`: each `SyncStatusUi(…, listOf("Google"), …)` becomes `listOf("Google Calendar")` and each `needsSignIn = listOf("Google")` `listOf("Google Calendar")`, so the baselines read as the household will see them.
- `SetupScreenshotTest.kt:56, 141, 146`: `StepTitle(WHOS_SETTING_UP, "The step's content goes here.")`, `StepTitle(WHERES_HOME)`, `StepTitle(WHOS_SETTING_UP)`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew testDebugUnitTest`. Expected: PASS. A failure on an old word the script didn't know: change that test line to Appendix A's new text, and tell the reviewer which.
Task 13's scanner doesn't exist yet, so look for anything left by hand: `grep -rnE "\b([Mm]aster|[Kk]iosk|[Ss]ync|[Rr]ead-only|[Aa]ssign|[Ff]eed)(s|ed|ing)?\b|[A-Za-z]'[a-z]" --include=*.kt */src/main */*/src/main | grep -v "Log\.\|testTag\|@Query\|//\|^\s*\*"` and read every line it prints: each is code (an identifier, a log, a query, a comment) or a miss to fix now.

- [ ] **Step 6: Compare and re-record the baselines, and look at them**

```bash
./gradlew --continue compareRoborazziDebug
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -60
```
Open every `*_compare.png`. Only words and the glyphs of apostrophes move. These must change:
- core/setup: `welcome_dark`, `welcome_light` (home’s), `you_dark`, `you_light`, `wizard_frame_dark`, `wizard_frame_light` (Who’s), `location_results_dark`, `location_results_light` (Where’s), `location_failed_dark` (Couldn’t, tablet’s), `person_remove_dark` (Sam’s), `settings_kiosk_dark`, `settings_kiosk_light` (title "Tablet lock", button "Use other apps"), `settings_location_dark`, `settings_location_light`, `settings_people_dark`, `settings_people_light` (the page list's "Tablet lock").
- capability/calendar: `week_dark`, `week_light`, `week_next_dark`, `week_next_light`, `week_furthest_dark`, `week_furthest_light`, `week_syncing_dark`, `week_fs15` ("Updated from Google Calendar 2 minutes ago"), `week_needs_sign_in_dark` ("Google Calendar needs reconnecting"), `week_stale_dark` (the same words in `danger`); `today_dark`, `today_light`, `today_badges_dark` ("See the week"); `coming_up_busy_dark` ("Nothing on"); `detail_editable_dark`, `detail_editable_light` ("Change"), `detail_delete_confirm_dark`, `detail_delete_confirm_light` ("Keep"), `detail_readonly_feed_dark`, `detail_readonly_feed_light`, `detail_recurring_dark`, `detail_recurring_light` (the notes, "School terms (can’t change here)"), `detail_syncing_dark`, `detail_syncing_light` ("Saving to Google Calendar…"), `detail_untagged_dark`, `detail_untagged_light` ("Added from a phone", "Choose who it’s for"), `detail_pin_dark`, `detail_pin_light` ("Who’s this?"), `detail_fs15`; every `editor_*` (`editor_child_*`, `editor_date_picker_*`, `editor_edit_*`, `editor_empty_*`, `editor_failed_*`, `editor_keyboard_*`, `editor_locked_*`, `editor_own_length_*`, `editor_time_picker_*`, dark and light: "Add event" / "Change event", "What’s happening?", the Add event button); `review_ok_dark`, `review_ok_light`, `review_read_problem_dark`, `review_read_problem_light`, `review_reconnect_dark`, `review_reconnect_light`, `review_disconnect_dark`, `review_person_picker_dark`, `settings_calendars_page_dark`, `settings_calendars_page_light` ("Main", "Use for new events", "Updated 2 minutes ago", "Can’t read this calendar — … account.", "Choose another main calendar first."); `connect_dark`, `connect_light`, `connecting_dark`, `connecting_light` (family’s).
- capability/weather: `forecast_expired_dark`, `forecast_expired_light` (tablet’s).
- app: `home_calendar_dark`, `home_calendar_light`, `home_weather_dark`, `home_weather_light`, `home_fs15` ("See the week"), `home_empty_dark`, `home_empty_light` (family’s), `pin_pad_dark`, `pin_pad_light`, `pin_pad_fs15`, `pin_pad_wrong_dark` (Who’s this?).
These may change, each only for the reason given: `welcome_sample_dark` (the welcome line), `wizard_pin_gate_dark` (Who’s this?), `today_empty_dark` ("See the week"), `coming_up_dark`, `coming_up_light` ("Nothing on" on an empty day), `connect_step_dark`, `connect_step_light`, `connected_step_dark`, `connected_step_light` (family’s, "Main"), `home_session_dark`, `home_toast_dark` ("See the week").
Any other change: stop. Then record and check the set:
```bash
./gradlew recordRoborazziDebug
```
Write the must-change names (every name above in "These must change", the `editor_*` ones spelled out as 18 names) to `$TMP/expected-baselines.txt` and the may-change ones to `$TMP/may-change.txt`, one per line, and run the baseline check (Global Constraints). Both of its lists must be empty.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add core app capability provider
git commit -m "Say main calendar, Tablet lock, updated and will be lost instead of the system's words, and curl every apostrophe and quote"
```

---

### Task 13: `CopyScanTest`: no system word or straight quote comes back (Decision 8, §7.1.3; ruling 15)

**Review:** sonnet.

**Files:**
- Create: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/CopyScan.kt`, `CopyScanTest.kt`; `core/ui/src/test/resources/copy-scan-allow.txt`, `copy-scan-fixture.kt.txt`

**Interfaces:**
- Consumes: Task 12's sweep (the main sources are clean).
- Produces (test only): `internal object CopyScan { fun literals(source: String): List<Literal>; fun scan(path: String, source: String): List<Finding> }` with `data class Literal(val start: Int, val line: Int, val raw: String, val triple: Boolean)` and `data class Finding(val path: String, val line: Int, val literal: String, val why: String)`. Later tasks keep it green; Task 15 moves the `"kiosk"` allowlist entry when the page id moves.

The rules (spec §7.1.3, ruling 15 and the plan review's T9):
- A finding is a literal that says one of master, kiosk, sync, read-only, assign or feed, with any of -s, -ed, -ing; or has a straight apostrophe; or a straight quote (`\"` in a string, or a bare `"` inside a `"""` one).
- Comments and KDoc are never read. The code inside `${…}` is read as code, and its own literals are read as literals ("Ask an Admin." inside a template is copy).
- A literal is code's, not the household's, when a call it sits in (up to four levels out, whatever line it is on) is a log, `testTag`, `require`/`check` (and their `NotNull` forms), `TODO`, `DateTimeFormatter.ofPattern`, `Regex`/`toRegex`, an annotation, or an exception's constructor other than `WriteRejectedException`; or Kotlin's own `error(…)` (at a statement's start, after `throw`, `?:`, `=` or `->`), never Compose's `semantics { error(…) }`, which TalkBack speaks; or its line is a `TAG =` or an annotation.
- A one-word literal that is lower case or holds `.`, `_` or `/` is an identifier: it skips the apostrophe and quote checks but not the word check. Real identifiers that hold a word ("kiosk.exit") go in the allowlist with a reason.

- [ ] **Step 1: Write the self-test, the fixture and the allowlist, against a scanner that finds nothing**

Create `core/ui/src/test/resources/copy-scan-fixture.kt.txt` (read as text, never compiled):
```kotlin
// CopyScanTest's self-test: one case of each rule. The master word in this comment is never read.
/** KDoc that says master and can't. */
fun banned() { Text("Make master") }
fun inflected() { Text("It syncs every hour") }
fun tagged() = Modifier.testTag("Make master now")
fun logged() = Log.w(TAG, "The master calendar can't be read")
fun loggedOverLines() = Log.w(
    TAG,
    "The master calendar can't be read",
)
fun templated(x: Int) = "Shown ${x.toString().padStart(2, '0')} times"
fun nested(admin: Boolean) = "That PIN can’t do this. ${if (admin) "Ask an Admin." else "Ask the master."}"
fun required(x: Int) = require(x > 0) { "x can't be zero" }
fun kotlinError(): Nothing = error("can't happen")
fun spokenError(bad: Boolean) = Modifier.semantics { if (bad) error("That name's taken") }
fun shownRefusal(): Nothing = throw WriteRejectedException("Can't change this")
fun hiddenFailure(): Nothing = throw IllegalStateException("can't happen")
fun quoted() = "No towns match \"Brigh\"."
fun raw() = """He said "go" there"""
fun apostrophe() = "That's it"
fun identifier() = "kiosk"
fun key() = "settings.page"
fun pattern() = Regex("(?:['’]s)?")
fun curly() = "That’s it — “quoted” — Settings › Apps."
```
Create `core/ui/src/test/resources/copy-scan-allow.txt`:
```text
# Copy-scan allowlist: module/File.kt | literal | reason. An entry needs a reason a reviewer accepts.
capability/calendar/CalendarPermissions.kt | calendar.event.assign | a permission id, stored and compared, never shown
core/access/Permissions.kt | kiosk.exit | a permission id, stored and compared, never shown
core/setup/KioskPage.kt | kiosk | the Tablet lock page's id, never shown (4d-2 §7.6)
```
(`CopyScanTest` names a file by its module's directory and its own name, as here.)
Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/CopyScanTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** 4d-2 Decision 8: no system word and no straight quote or apostrophe in any copy in the app's main sources. */
class CopyScanTest {
    // Unit tests run in the module's directory.
    private val repo = File("../..")

    /** As `IconFontTest.mainSources`, without the contract-test libraries (`*-testkit`), whose assertion messages are never shown. */
    private fun mainSources(): Sequence<File> {
        val main = "${File.separator}src${File.separator}main${File.separator}"
        return repo.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") && !it.name.endsWith("-testkit") }
            .filter { it.isFile && it.extension == "kt" && main in it.path }
    }

    /** "capability/calendar/CalendarPermissions.kt": the module's directory and the file's name, as the allowlist writes them. */
    private fun shortPath(file: File): String {
        val path = file.relativeTo(repo).invariantSeparatorsPath
        return path.substringBefore("/src/main/") + "/" + file.name
    }

    private fun allowed(): Set<Pair<String, String>> = File("src/test/resources/copy-scan-allow.txt").readLines(Charsets.UTF_8)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line -> line.split("|").map(String::trim).let { (path, literal) -> path to literal } }
        .toSet()

    @Test
    fun theScannerCatchesEachRuleAndPassesTheRest() {
        val fixture = File("src/test/resources/copy-scan-fixture.kt.txt").readText(Charsets.UTF_8)
        val found = CopyScan.scan("fixture", fixture).map { it.literal to it.why }
        assertThat(found).containsExactly(
            "Make master" to "says \"master\"",
            "It syncs every hour" to "says \"syncs\"",
            "Ask the master." to "says \"master\"",
            "That name's taken" to "a straight apostrophe",
            "Can't change this" to "a straight apostrophe",
            "No towns match \\\"Brigh\\\"." to "a straight quote",
            "He said \"go\" there" to "a straight quote",
            "That's it" to "a straight apostrophe",
            "kiosk" to "says \"kiosk\"",
        )
    }

    @Test
    fun noCopyNamesASystemWordOrUsesAStraightQuote() {
        val allow = allowed()
        val findings = mainSources().flatMap { file ->
            CopyScan.scan(shortPath(file), file.readText(Charsets.UTF_8)).asSequence()
        }.filterNot { f -> (f.path to f.literal) in allow }.toList()
        assertWithMessage("Copy with a system word or a straight quote (4d-2 §7.1):\n" + findings.joinToString("\n") { "${it.path}:${it.line}: ${it.literal} (${it.why})" })
            .that(findings).isEmpty()
    }

    @Test
    fun everyAllowlistEntryIsStillNeeded() {
        val found = mainSources().flatMap { file -> CopyScan.scan(shortPath(file), file.readText(Charsets.UTF_8)).asSequence() }
            .map { it.path to it.literal }.toSet()
        assertThat(found).containsAtLeastElementsIn(allowed())
    }
}
```
Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/CopyScan.kt` as a scanner that finds nothing, so the self-test is seen to fail first:
```kotlin
package uk.co.siland.culvery.core.ui

internal object CopyScan {
    data class Literal(val start: Int, val line: Int, val raw: String, val triple: Boolean)

    data class Finding(val path: String, val line: Int, val literal: String, val why: String)

    fun literals(source: String): List<Literal> = emptyList()

    fun scan(path: String, source: String): List<Finding> = emptyList()
}
```

- [ ] **Step 2: Run it to see the self-test fail**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*CopyScanTest*"`
Expected: `theScannerCatchesEachRuleAndPassesTheRest` FAILS ("missing (9)…"), `everyAllowlistEntryIsStillNeeded` FAILS (the three entries aren't found); `noCopyNamesASystemWordOrUsesAStraightQuote` passes, for now because the scanner finds nothing.

- [ ] **Step 3: Write the scanner**

`CopyScan.kt` becomes:
```kotlin
package uk.co.siland.culvery.core.ui

/**
 * 4d-2 §7.1.3: reads Kotlin source the way the copy rules need. Comments (KDoc included) go; string literals are
 * collected where they start; the code inside `${…}` is read as code again, with its own literals, while `$name` stays
 * as text. Whether a literal is code's is decided by the calls it sits in, whatever line they start on.
 */
internal object CopyScan {
    data class Literal(val start: Int, val line: Int, val raw: String, val triple: Boolean)

    data class Finding(val path: String, val line: Int, val literal: String, val why: String)

    private val BANNED = Regex("""(?i)\b(master|kiosk|sync|read-only|assign|feed)(s|ed|ing)?\b""")

    // The name before a call's "(": a literal inside one of these is code's, not the household's.
    private val CODE_CALL = Regex(
        """(?:\bLog\.\w+|\btestTag|\brequire|\brequireNotNull|\bcheck|\bcheckNotNull|\bTODO|""" +
            """DateTimeFormatter\.ofPattern|\bRegex|\btoRegex|@\w+|\b\w*Exception)\s*$""",
    )

    // Kotlin's error(…), where a statement or an expression starts; Compose's semantics error(…) follows an `if (…)`.
    private val KOTLIN_ERROR = Regex("""(?:^|[\n=>{;]|throw|\?:)\s*error\s*$""")

    private val TAG_LINE = Regex("""\bTAG\b\s*=|^\s*@""")

    private class Tokens(val literals: List<Literal>, val masked: String)

    fun literals(source: String): List<Literal> = tokens(source).literals

    fun scan(path: String, source: String): List<Finding> {
        val tokens = tokens(source)
        val lines = source.lines()
        return tokens.literals.mapNotNull { literal ->
            if (TAG_LINE.containsMatchIn(lines[literal.line - 1]) || inCodeCall(tokens.masked, literal.start)) return@mapNotNull null
            val text = withoutTemplates(literal.raw)
            val identifier = text.none(Char::isWhitespace) && (text == text.lowercase() || text.any { it in "._/" })
            val why = listOfNotNull(
                BANNED.find(text)?.let { "says \"${it.value}\"" },
                "a straight apostrophe".takeIf { !identifier && '\'' in text },
                "a straight quote".takeIf { !identifier && ("\\\"" in literal.raw || (literal.triple && '"' in literal.raw)) },
            )
            if (why.isEmpty()) null else Finding(path, literal.line, text, why.joinToString(", "))
        }
    }

    fun withoutTemplates(raw: String): String = buildString {
        var j = 0
        while (j < raw.length) {
            if (raw.startsWith("\${", j)) j = braceEnd(raw, j + 2) else append(raw[j++])
        }
    }

    /** The literals, and the source with comments and literal bodies blanked (newlines kept) for reading the calls around them. */
    private fun tokens(s: String): Tokens {
        val out = mutableListOf<Literal>()
        val masked = StringBuilder(s)
        fun blank(from: Int, to: Int) {
            for (k in from until to) if (masked[k] != '\n') masked.setCharAt(k, ' ')
        }
        fun lineAt(i: Int): Int = 1 + (0 until i).count { s[it] == '\n' }
        fun read(from: Int, to: Int) {
            var i = from
            while (i < to) {
                when {
                    s.startsWith("//", i) -> {
                        val end = s.indexOf('\n', i).let { if (it < 0 || it > to) to else it }
                        blank(i, end)
                        i = end
                    }
                    s.startsWith("/*", i) -> {
                        val end = s.indexOf("*/", i + 2) + 2
                        blank(i, end)
                        i = end
                    }
                    s[i] == '"' -> {
                        val quotes = if (s.startsWith("\"\"\"", i)) 3 else 1
                        val close = "\"".repeat(quotes)
                        val templates = mutableListOf<IntRange>()
                        var j = i + quotes
                        while (!s.startsWith(close, j)) {
                            j = when {
                                quotes == 1 && s[j] == '\\' -> j + 2
                                s.startsWith("\${", j) -> braceEnd(s, j + 2).also { end -> templates += (j + 2) until (end - 1) }
                                else -> j + 1
                            }
                        }
                        out += Literal(i, lineAt(i), s.substring(i + quotes, j), triple = quotes == 3)
                        blank(i + quotes, j)
                        // The code inside ${…} is code again, with literals of its own.
                        templates.forEach { t ->
                            for (k in t) masked.setCharAt(k, s[k])
                            read(t.first, t.last + 1)
                        }
                        i = j + quotes
                    }
                    s[i] == '\'' -> i = s.indexOf('\'', if (s[i + 1] == '\\') i + 3 else i + 2) + 1
                    else -> i++
                }
            }
        }
        read(0, s.length)
        return Tokens(out.sortedBy { it.start }, masked.toString())
    }

    /** Whether any of the four calls around [start] is code's: a log, a tag, a check, a pattern, an annotation, an exception. */
    private fun inCodeCall(masked: String, start: Int): Boolean {
        var j = start - 1
        repeat(4) {
            var depth = 0
            while (j >= 0) {
                val c = masked[j]
                if (c in ")]}") {
                    depth++
                } else if (c in "([{") {
                    if (depth == 0) break
                    depth--
                }
                j--
            }
            if (j < 0) return false
            var head = masked.substring(0, j).trimEnd()
            // `require(x) { "…" }`: the call is the one before the lambda.
            if (masked[j] == '{' && head.endsWith(")")) head = head.substring(0, openerOf(head)).trimEnd()
            val code = when {
                head.endsWith("WriteRejectedException") -> false
                KOTLIN_ERROR.containsMatchIn(head) -> true
                else -> CODE_CALL.containsMatchIn(head)
            }
            if (code) return true
            j--
        }
        return false
    }

    private fun openerOf(s: String): Int {
        var depth = 0
        for (k in s.indices.reversed()) {
            when (s[k]) {
                ')' -> depth++
                '(' -> if (--depth == 0) return k
            }
        }
        return 0
    }

    private fun braceEnd(s: String, from: Int): Int {
        var depth = 1
        var j = from
        while (depth > 0) {
            when (s[j]) {
                '{' -> depth++
                '}' -> depth--
            }
            j++
        }
        return j
    }
}
```
The self-test fails if: the tokenizer reads a comment or KDoc, keeps `${…}` code (the `'0'`) or skips a template's own literal ("Ask the master."), misses an inflection ("syncs"), skips a `WriteRejectedException` message or Compose's `error(…)`, reads a test tag, a log (on one line or several), a `require`, Kotlin's `error`, a `Regex` or an exception other than `WriteRejectedException`, lets an identifier skip the word check, flags an identifier's dot ("settings.page"), or misses a straight quote or apostrophe, escaped or in a raw string.

- [ ] **Step 4: Run it to see every test pass**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*CopyScanTest*"`
Expected: PASS, all three. `noCopyNamesASystemWordOrUsesAStraightQuote` finds nothing past the allowlist: these rules, run over eb40ec0 while this plan was revised, found 57: every string Task 12 rewrites (three of them, "until someone assigns it.", "Assign to…" and `syncedLabel`'s "synced", only these rules catch), the two badge descriptions Task 8 removed, the `PinPromptController` default Task 10 removed, and the three ids in the allowlist; nothing else. If it lists anything, it is a string Task 12 missed: fix it in the main source (Appendix A's text, curly), not in the allowlist, unless it is an identifier that is never shown; then stop and report which.
Then check the test can fail on real code: add `"Make master"` to any `Text(…)` in a main source, run it (expected: FAIL naming that file and line), and take the line out again (`git diff --name-only` must show only the four new test files).

- [ ] **Step 5: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add core/ui/src/test
git commit -m "Fail the build when copy says a system word or uses a straight apostrophe or quote"
```

---

### Task 14: Errors by cause; full words and full stops (D34, D35; Decision 11)

**Review:** opus.

**Files:**
- Modify: `capability/calendar/…/Writes.kt:32-33, 47-48, 62-66, 126-132`, `CalendarEditor.kt:35-67, 115-133, 258-286, 379-397, 430-440`, `CalendarSync.kt:110-128, 181, 193`, `CalendarSetup.kt:20-25, 77-130` (the toasts, `connectWithDefaults`, `reconnect`), `CalendarReview.kt:20-29`, `EventForm.kt:40-49`, `ui/EventEditorSheet.kt:65-66, 82, 150, 357-378`, `ui/EventEditorHost.kt:90-97, 120-125`, `ui/ReviewCalendars.kt:57-81`
- Modify: `provider/calendar-google/…/GoogleHttp.kt:26-28, 142`, `GoogleConnectFlow.kt:106-108` (and its `step` catch); `provider/calendar-fake/…/FakeCalendarProvider.kt:146`
- Modify: `core/plugin/…/Setup.kt:11`; `core/setup/…/SetupCopy.kt:45, 54, 61-63` and new copy, `PeopleEditor.kt:57-135`, `PersonEditorSheet.kt:96-99`, `steps/YouStep.kt:103-106`, `LocationPane.kt` (`saveFailed` line); `core/access/…/AccessControl.kt:26` (`CONTINUE_SETUP`), `ui/PinPad.kt` (`WRONG_PIN`, `tooManyTries`)
- Modify: `capability/weather/…/WeatherWords.kt:29-37`
- Test: `capability/calendar/src/test/…/WriteCopyTest.kt` (create) and the listed edits; `core/setup/src/test/…/SetupCopyTest.kt` (create); `core/access/src/test/…/ui/PinPadTest.kt`; `capability/weather/src/test/…/WeatherWordsTest.kt`, `ui/ForecastCardTest.kt`
- Baselines (changed): `editor_failed_*`, `editor_*` (the Length chips), `forecast_age_*`, `pin_pad_wrong_dark`, `pin_pad_fs15`, `wizard_pin_gate_dark` (CONTINUE_SETUP's full stop), `review_*` with an Error or Unreachable row (Step 7)

**Interfaces:**
- Consumes: `EditResult`, `WriteOutcome`, `couldNotSave`, `couldNotSaveAll` (as Task 12 left them).
- Produces:
  - In `Writes.kt` (package `…capability.calendar`): `sealed interface WriteProblem { data object Tablet : WriteProblem; data class Refused(val reason: String?) : WriteProblem }`; `const val EVENT_GONE = "the event has been deleted"`; `class WriteRejectedException(message: String? = null, cause: Throwable? = null)` (null: the provider can say no more; no sentinel string); `fun couldNotSave(service: String, problem: WriteProblem): String`; `fun couldNotDelete(service: String, problem: WriteProblem): String`; `fun saveFailedBody(service: String, problem: WriteProblem): String`; `internal fun changesLost(service: String, reasons: List<String?>): String` (replaces `couldNotSaveAll`).
  - `WriteOutcome.Rejected(val reason: String?)`; `EditResult.Rejected(val problem: WriteProblem)`; `TRY_AGAIN` goes.
  - `data class SaveFailure(val service: String, val problem: WriteProblem)` in `EventEditorSheet.kt`; `EventEditorSheet(…, failure: SaveFailure?, …)`.
  - `fun couldNotConnect(service: String, unreachable: Boolean): String`.
  - Setup copy: `internal fun couldNotSaveDetails(name: String?): String`, `internal fun couldNotMove(name: String): String`, `internal const val YOUR_DETAILS_NOT_SAVED`, `internal const val HOME_NOT_SAVED`; `PeopleEditor`'s failures name what wasn't saved.
  - `fun lengthLabel(length: Duration): String` in full words; `tooManyTries(seconds)` in full words.

- [ ] **Step 1: Write the failing copy tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/WriteCopyTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 4d-2 §7.2: each failure says what went wrong, by its cause, and what to do. */
class WriteCopyTest {
    private val gc = "Google Calendar"

    @Test
    fun aSaveSaysWhyByCause() {
        assertThat(couldNotSave(gc, WriteProblem.Tablet)).isEqualTo("Couldn’t save — try again.")
        assertThat(couldNotSave(gc, WriteProblem.Refused(null))).isEqualTo("Google Calendar wouldn’t accept this change.")
        assertThat(couldNotSave(gc, WriteProblem.Refused(EVENT_GONE))).isEqualTo("Couldn’t save to Google Calendar — the event has been deleted.")
    }

    @Test
    fun aDeleteNeverSaysSave() {
        assertThat(couldNotDelete(gc, WriteProblem.Tablet)).isEqualTo("Couldn’t delete — try again.")
        assertThat(couldNotDelete(gc, WriteProblem.Refused(null))).isEqualTo("Google Calendar wouldn’t accept this change.")
        assertThat(couldNotDelete(gc, WriteProblem.Refused("this calendar can’t be changed from the tablet")))
            .isEqualTo("Couldn’t delete — this calendar can’t be changed from the tablet.")
    }

    @Test
    fun theFailureCardsBodySaysWhatToDoNext() {
        assertThat(saveFailedBody(gc, WriteProblem.Tablet)).isEqualTo("Nothing was saved. Your details are still here — try again.")
        assertThat(saveFailedBody(gc, WriteProblem.Refused("x")))
            .isEqualTo("Nothing was saved. Your details are still here to add in Google Calendar on your phone.")
    }

    @Test
    fun theDrainSaysWhatWasLost() {
        assertThat(changesLost(gc, listOf(EVENT_GONE))).isEqualTo("A change couldn’t be saved to Google Calendar and was lost — the event has been deleted.")
        assertThat(changesLost(gc, listOf(null))).isEqualTo("A change couldn’t be saved to Google Calendar and was lost.")
        assertThat(changesLost(gc, listOf(null, "x", null))).isEqualTo("3 changes couldn’t be saved to Google Calendar and were lost.")
    }

    @Test
    fun connectingSaysWhetherTheServiceCouldBeReached() {
        assertThat(couldNotConnect(gc, unreachable = true)).isEqualTo("Couldn’t reach Google Calendar — check the tablet’s Wi-Fi, then try again.")
        assertThat(couldNotConnect(gc, unreachable = false)).isEqualTo("Couldn’t connect to Google Calendar — try again.")
    }

    @Test
    fun aRefusalWithNoReasonOfItsOwnHasNone() = kotlinx.coroutines.test.runTest {
        val outcome = callWriter(coroutineContext, 1_000) { throw WriteRejectedException() }
        assertThat(outcome).isEqualTo(WriteOutcome.Rejected(null))
    }

    @Test
    fun lengthsAreInFullWords() {
        assertThat(listOf(1L, 30L, 60L, 90L, 120L, 135L).map { lengthLabel(java.time.Duration.ofMinutes(it)) })
            .containsExactly("1 minute", "30 minutes", "1 hour", "1 hour 30 minutes", "2 hours", "2 hours 15 minutes").inOrder()
    }
}
```
Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SetupCopyTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE

/** 4d-2 §7.2 C96, D35: a failure names what wasn't saved; a toast ends with a full stop. */
class SetupCopyTest {
    @Test
    fun aFailureNamesWhatWasntSaved() {
        assertThat(couldNotSaveDetails("Sam")).isEqualTo("Couldn’t save Sam’s details — try again.")
        assertThat(couldNotSaveDetails(null)).isEqualTo("Couldn’t save this person — try again.")
        assertThat(couldNotMove("Sam")).isEqualTo("Couldn’t move Sam — try again.")
        assertThat(YOUR_DETAILS_NOT_SAVED).isEqualTo("Couldn’t save your details — try again.")
        assertThat(HOME_NOT_SAVED).isEqualTo("Couldn’t save the home location — try again.")
        assertThat(COULD_NOT_SAVE).isEqualTo("Couldn’t save that change — try again.")
        assertThat(COLOUR_TAKEN).isEqualTo("That colour is taken — choose another.")
    }

    @Test
    fun toastsEndWithAFullStop() {
        assertThat(removed("Sam")).isEqualTo("Sam removed.")
        assertThat(movedUp("Sam")).isEqualTo("Sam moved up.")
        assertThat(movedDown("Sam")).isEqualTo("Sam moved down.")
    }
}
```
`PinPadTest.kt`, add:
```kotlin
    @Test
    fun thePinPadsLinesAreSentencesInFullWords() {
        assertThat(tooManyTries(30)).isEqualTo("Too many tries — wait 30 seconds.")
        assertThat(tooManyTries(1)).isEqualTo("Too many tries — wait 1 second.")
        assertThat(WRONG_PIN).isEqualTo("Wrong PIN — try again.")
        assertThat(pinReasonText(PinReason.ContinueSetup, "x")).isEqualTo("Enter your PIN to carry on setting up.")
    }
```
`WeatherWordsTest.theAgeLineAppearsOnlyPastTwoHours` (26-28) expects `"Updated 2 hours ago"`, `"Updated 3 hours ago"`, `"Updated 23 hours ago"`; `ForecastCardTest.kt:118` waits for `"Updated 3 hours ago"`.
Each fails today: the functions and constants don't exist or say "min", "h", "s" and leave the full stop off.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :capability:calendar:testDebugUnitTest --tests "*WriteCopyTest*" :core:setup:testDebugUnitTest --tests "*SetupCopyTest*" :core:access:testDebugUnitTest --tests "*PinPadTest*" :capability:weather:testDebugUnitTest --tests "*WeatherWordsTest*"`
Expected: FAIL (to compile on `WriteProblem`, `couldNotDelete`, `changesLost`, `couldNotSaveDetails`, …; then on the words).

- [ ] **Step 3: A typed cause through the write path**

`Writes.kt`: line 32-33 becomes
```kotlin
/** Why a change to an event that is gone is refused; lower case, to follow a dash. Providers refuse with the same words. */
const val EVENT_GONE = "the event has been deleted"

/** Why a change didn't reach the calendar (4d-2 §7.2). The editor never meets an unreachable provider: it queues instead. */
sealed interface WriteProblem {
    /** The tablet failed before the provider answered (a store failure): trying again may work. */
    data object Tablet : WriteProblem

    /** The provider refused for good. [reason] is lower case, to follow a dash; null when it gave none. */
    data class Refused(val reason: String?) : WriteProblem
}
```
`WriteOutcome.Rejected` (47-48): `/** The provider refused the change for good; [reason] null when it gave none of its own. */ data class Rejected(val reason: String?) : WriteOutcome`; `callWriter`'s catch (65-66): `WriteOutcome.Rejected(e.message)`. `CalendarContract.kt:140`: `class WriteRejectedException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)`, its KDoc gaining "[message] is the reason in lower case, to follow a dash; null when the provider can say no more, shown as \"{service} wouldn’t accept this change.\" (4d-2 §7.2)." (Every existing `WriteRejectedException("…")` keeps compiling.) `couldNotSave` and `couldNotSaveAll` (126-132) become:
```kotlin
/** A save that didn't happen, by its cause (4d-2 §7.2, C47). */
fun couldNotSave(service: String, problem: WriteProblem): String = when (problem) {
    WriteProblem.Tablet -> "Couldn’t save — try again."
    is WriteProblem.Refused -> problem.reason?.let { "Couldn’t save to $service — $it." } ?: wouldNotAccept(service)
}

/** A delete that didn't happen: never "save" (C52). */
fun couldNotDelete(service: String, problem: WriteProblem): String = when (problem) {
    WriteProblem.Tablet -> "Couldn’t delete — try again."
    is WriteProblem.Refused -> problem.reason?.let { "Couldn’t delete — $it." } ?: wouldNotAccept(service)
}

private fun wouldNotAccept(service: String): String = "$service wouldn’t accept this change."

/** The editor's failure card's body (C48): trying again helps only when the tablet failed. */
fun saveFailedBody(service: String, problem: WriteProblem): String = when (problem) {
    WriteProblem.Tablet -> "Nothing was saved. Your details are still here — try again."
    is WriteProblem.Refused -> "Nothing was saved. Your details are still here to add in $service on your phone."
}

/** One toast for everything a drain pass lost for [service]; [reasons] has one entry per change (C53). */
internal fun changesLost(service: String, reasons: List<String?>): String = when {
    reasons.size > 1 -> "${reasons.size} changes couldn’t be saved to $service and were lost."
    reasons.single() == null -> "A change couldn’t be saved to $service and was lost."
    else -> "A change couldn’t be saved to $service and was lost — ${reasons.single()}."
}
```
`CalendarSync.kt`: `drop(change, label, outcome.reason)` (181) and `toaster.show(changesLost(label, reasons), urgent = true)` (193).
`CalendarEditor.kt`:
- delete `TRY_AGAIN` (35-36); `EVENT_DELETED = "Event deleted."`, `EVENT_ADDED = "Event added."`, `CHANGES_SAVED = "Changes saved."`, `COULD_NOT_OPEN = "Couldn’t open the event — try again."`;
- `EditResult.Rejected`'s KDoc keeps its text, and its declaration becomes `data class Rejected(val problem: WriteProblem) : EditResult`;
- `couldNotDelete(label: String)` (133): `internal fun couldNotDelete(label: String) = toaster.show(couldNotDelete(label, WriteProblem.Tablet), urgent = true)`;
- `onAppScope` (271): `EditResult.Rejected(WriteProblem.Tablet)`; its KDoc's "Rejected(TRY_AGAIN)" becomes "Rejected(Tablet)"; line 282: `scope.launch { (job.await() as? EditResult.Rejected)?.let { toaster.show(couldNotSave(label, it.problem), urgent = true) } }`;
- `attempt` (391): `is WriteOutcome.Rejected -> EditResult.Rejected(WriteProblem.Refused(outcome.reason))`;
- `report` (430-440):
```kotlin
    /** A queued change already shows (a delete hides the event), so it reads as done too. A failed delete says delete (C52). */
    private fun report(kind: ChangeKind, label: String, result: EditResult, added: String) {
        val saved = result == EditResult.Done || result == EditResult.Queued
        when {
            result is EditResult.Rejected && kind == ChangeKind.DELETE -> toaster.show(couldNotDelete(label, result.problem), urgent = true)
            result is EditResult.Rejected && kind == ChangeKind.ASSIGN -> toaster.show(couldNotSave(label, result.problem), urgent = true)
            saved && kind == ChangeKind.DELETE -> toaster.show(EVENT_DELETED)
            saved && kind == ChangeKind.CREATE -> toaster.show(added)
            saved && kind == ChangeKind.UPDATE -> toaster.show(CHANGES_SAVED)
        }
    }
```
`ui/EventEditorSheet.kt`: delete `SAVE_FAILED_BODY` (65-66) and add:
```kotlin
/** A save that failed: where it was going ([service]) and why ([problem]); the card says both (4d-2 §7.2). */
data class SaveFailure(val service: String, val problem: WriteProblem)
```
`failure: String?` (82) becomes `failure: SaveFailure?`; line 150 `if (failure != null) FailureCard(failure)`; `FailureCard`:
```kotlin
/** Hand-off 14: the save didn't happen; the details are still in the sheet, and the body says what to do next. */
@Composable
private fun FailureCard(failure: SaveFailure) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.editorCardIconGap),
        modifier = Modifier
            .testTag("editor_failure")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.editorCardRadius))
            .background(c.dangerSoft)
            .padding(horizontal = CalendarDimens.editorCardPaddingH, vertical = CalendarDimens.editorCardPaddingV),
    ) {
        HhIcon(Icons.CLOUD_OFF, size = CalendarDimens.failureIcon, tint = c.danger)
        Column {
            // A long provider reason must not push Save off screen with the keyboard up; the body says what to do.
            Text(couldNotSave(failure.service, failure.problem), style = CalendarType.noteTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(CalendarDimens.failureBodyTop))
            Text(saveFailedBody(failure.service, failure.problem), style = CalendarType.noteBody, color = c.mute)
        }
    }
}
```
`ui/EventEditorHost.kt`: `var failure by remember(request) { mutableStateOf<SaveFailure?>(null) }`; the action's error handler `failure = SaveFailure(opened?.label.orEmpty(), WriteProblem.Tablet)`; its comment (92) "Kept only after the tablet failed (WriteProblem.Tablet), so Try again can't make a second event (3a design §3.12)."; line 120 `if (result != EditResult.Rejected(WriteProblem.Tablet)) createKey = newClientKey()`; line 123 `is EditResult.Rejected -> failure = SaveFailure(loaded.label, result.problem)`. Imports: drop `TRY_AGAIN`, `couldNotSave`; add `WriteProblem`.
`GoogleHttp.kt`: delete `REFUSED` (27) and its KDoc line ("Follow-up R8's fixed wording…" moves onto `READ_ONLY_HERE`); line 142: `return WriteRejectedException(if (code == 403) READ_ONLY_HERE else null)`.
`FakeCalendarProvider.kt:146`: `if (current.recurring) throw WriteRejectedException("repeating events can’t be changed here")`.

- [ ] **Step 4: Connecting, the review rows and the setup failures**

`CalendarSetup.kt:24-25`:
```kotlin
/** C87g: a service that couldn't be reached says check the Wi-Fi; any other failure, try again. */
fun couldNotConnect(service: String, unreachable: Boolean): String =
    if (unreachable) "Couldn’t reach $service — check the tablet’s Wi-Fi, then try again." else "Couldn’t connect to $service — try again."
```
`connected` and `reconnected` end with "." (`"$service connected."`, `"$service reconnected."`). In `connectWithDefaults`, the `catch (e: Throwable)` keeps `e`: replace `val stored = try { … true } catch …` with a `var failure: Throwable? = null` set in the catch, and the failed toast `toaster.show(couldNotConnect(serviceNameOf(connection, providers::displayNameOf), unreachable = failure is UnreachableException), urgent = true)`; `reconnect`'s failed toast passes `unreachable = false` (a store failure).
`GoogleConnectFlow.kt`: `stopWithToast(unreachable: Boolean = false)` shows `couldNotConnect(GOOGLE_DISPLAY_NAME, unreachable)`; the `step` catch's non-cancel branch calls `stopWithToast(unreachable = e is UnreachableException)` (import `UnreachableException`).
`CalendarReview.kt:20-29`: each toast ends with ".": `"$calendar now shows as $person."`, `"$calendar hidden."`, `"$calendar shown."`, `"New events now go to $calendar."`, `"$service disconnected."`.
`ReviewCalendars.kt:57-81`:
```kotlin
/** 3a design §3.4; 4d-2 C81, C82: a status ("Needs reconnecting") or a sentence that says what to do. */
internal const val NEEDS_RECONNECTING = "Needs reconnecting"
internal const val STOPPED_UPDATING = "Stopped updating — disconnect and connect again."
```
```kotlin
internal fun healthWords(row: CalendarRow, nowMillis: Long): String = when (row.health) {
    ConnectionHealth.Ok -> updatedLabel(row.lastSyncMillis, nowMillis)
    ConnectionHealth.Unreachable -> "Can’t reach ${row.service} — check the tablet’s Wi-Fi."
    ConnectionHealth.NeedsSignIn -> NEEDS_RECONNECTING
    is ConnectionHealth.Error -> STOPPED_UPDATING
}
```
`core/plugin/…/Setup.kt:11`: `/** 4a design §5, 4d-2 C87j: a setting that didn't save; nothing changed. */ const val COULD_NOT_SAVE = "Couldn’t save that change — try again."`.
`core/setup/…/SetupCopy.kt`: `COLOUR_TAKEN = "That colour is taken — choose another."`; `removed(name) = "$name removed."`; `movedUp(name) = "$name moved up."`; `movedDown(name) = "$name moved down."`; and, after `someoneCalled`:
```kotlin
// 4d-2 C96: a failure names what wasn't saved.
internal fun couldNotSaveDetails(name: String?): String =
    if (name == null) "Couldn’t save this person — try again." else "Couldn’t save $name’s details — try again."

internal fun couldNotMove(name: String): String = "Couldn’t move $name — try again."

internal const val YOUR_DETAILS_NOT_SAVED = "Couldn’t save your details — try again."
internal const val HOME_NOT_SAVED = "Couldn’t save the home location — try again."
```
`PeopleEditor.kt`: `saving` takes what to say on failure: `private suspend fun saving(failure: String, block: suspend () -> Unit): PeopleOutcome` with its generic catch `PeopleOutcome.Refused(failure)`; the callers: `add` → `saving(couldNotSaveDetails(draft.name.trim().ifEmpty { null })) { … }`; `save`'s early `?: return PeopleOutcome.Refused(couldNotSaveDetails(null))` and `saving(couldNotSaveDetails(before.person.name)) { … }`; `remove`'s early `?: return PeopleOutcome.Refused(couldNotSaveDetails(null))` and `saving(couldNotSaveDetails(name)) { … }`; `move`: when `name == null`, `toaster.show(COULD_NOT_SAVE, urgent = true)` and `return PeopleOutcome.Refused(COULD_NOT_SAVE)` as today, else `saving(couldNotMove(name)) { moved = household.move(id, up) }`. Drop the `COULD_NOT_SAVE` import only if unused.
`PersonEditorSheet.kt:98`: `form.message = couldNotSaveDetails(existing?.person?.name)`. `YouStep.kt:105`: `form.message = YOUR_DETAILS_NOT_SAVED`. `LocationPane.kt`: the save-failed line shows `HOME_NOT_SAVED` (drop the `COULD_NOT_SAVE` import).

- [ ] **Step 5: Full words and full stops elsewhere**

`EventForm.kt:40-49`:
```kotlin
/** "30 minutes", "1 hour", "1 hour 30 minutes": full words, singular at one (4d-2 §7.3). */
fun lengthLabel(length: Duration): String {
    val hours = length.toHours()
    val minutes = length.toMinutes() % MINUTES_PER_HOUR
    return when {
        hours == 0L -> plural(minutes, "minute")
        minutes == 0L -> plural(hours, "hour")
        else -> "${plural(hours, "hour")} ${plural(minutes, "minute")}"
    }
}
```
(the KDoc on `LENGTH_CHOICES` becomes "Hand-off §7 Length chips: 30 minutes, 1 hour, 2 hours."; `plural` is Task 12's, in `CalendarUi.kt`.)
`AccessControl.kt:26`: `const val CONTINUE_SETUP = "Enter your PIN to carry on setting up."`. `PinPad.kt`: `internal const val WRONG_PIN = "Wrong PIN — try again."` and
```kotlin
internal fun tooManyTries(seconds: Int): String = "Too many tries — wait ${if (seconds == 1) "1 second" else "$seconds seconds"}."
```
`WeatherWords.kt:29-37`:
```kotlin
/** "Updated 3 hours ago" once the data is past [STALE_AFTER_MS] old, else null (4b §4.2; 4d-2 §7.3: full words). */
internal fun updatedAgo(fetchedAtMillis: Long, nowMillis: Long): String? {
    val age = nowMillis - fetchedAtMillis
    if (age <= STALE_AFTER_MS) return null
    val hours = age / HOUR_MS
    if (hours < HOURS_IN_DAY) return "Updated $hours hours ago"
    val days = hours / HOURS_IN_DAY
    return if (days == 1L) "Updated 1 day ago" else "Updated $days days ago"
}
```
(The age line starts past two hours, so "1 hour" never shows.)

- [ ] **Step 6: The tests that assert the old results and words**

Exact edits (line numbers as at eb40ec0; Task 12's sweep curled these strings):
- `CalendarEditorTest.kt`: 217 `EditResult.Rejected(WriteProblem.Refused("Event is locked"))`; 220 `containsExactly(couldNotSave("Sample calendar", WriteProblem.Refused("Event is locked")))`; 249 `EditResult.Rejected(WriteProblem.Refused("Event is locked"))`; 252 `containsExactly(couldNotDelete("Sample calendar", WriteProblem.Refused("Event is locked")))`; 280 `EditResult.Rejected(WriteProblem.Tablet)`; 282 `containsExactly(couldNotSave("Sample calendar", WriteProblem.Tablet))`; 505 `EditResult.Rejected(WriteProblem.Refused("Calendar is full"))`; 589 `containsExactly(couldNotSave("Sample calendar", WriteProblem.Refused("Calendar is full")))`; 663 `EditResult.Rejected(WriteProblem.Refused("Event is locked"))`; 681 `containsExactly(couldNotSave("Sample calendar", WriteProblem.Refused("Event is locked")))`. Drop the `TRY_AGAIN` import.
- `EventEditorHostTest.kt`: 228 `compose.onNodeWithText(couldNotSave("Sample calendar", WriteProblem.Refused("Calendar is full"))).assertExists()`; 258 and 501 `waitForText(couldNotSave("Sample calendar", WriteProblem.Tablet))`; 543 `containsExactly(couldNotDelete("Sample calendar", WriteProblem.Tablet))`. Imports: drop `TRY_AGAIN`; add `WriteProblem`, `couldNotDelete`.
- `EventDetailHostTest.kt:151`: `containsExactly(couldNotDelete("Sample calendar", WriteProblem.Refused("Event is locked")))`.
- `EventEditorSheetTest.kt`: `private var failure by mutableStateOf<SaveFailure?>(null)`; 178 `failure = SaveFailure("Sample calendar", WriteProblem.Refused("Calendar is full"))`; 183 `compose.onNodeWithText(couldNotSave("Sample calendar", WriteProblem.Refused("Calendar is full"))).assertExists()`; Task 5's `failure = "Couldn’t save — try again."` becomes `failure = SaveFailure("Google Calendar", WriteProblem.Tablet)`.
- `EditorScreenshotTest.kt:148`: `private val failure = SaveFailure("Sample calendar", WriteProblem.Refused("Calendar is full"))`.
- `CalendarSyncTest.kt`: 466 `containsExactly(changesLost("Service A", listOf(EVENT_GONE)))`; 480 `containsExactly(changesLost("Service A", listOf("Event is locked")))`; 492, 711, 839 `containsExactly(changesLost("Service A", listOf(null, null)))`; 697 `listOf(null, null, null)`; 557, 568, 671, 682, 955 `containsExactly(changesLost("Service A", listOf(null)))`; 1078 `containsExactly(changesLost("C1", listOf(null)))`.
- `app/src/testDebug/…/SampleRollbackTest.kt:101`: `containsExactly(changesLost("Sample calendar (debug)", listOf("Boiler service is locked")))` (an `internal` function of another module: write the string, "A change couldn’t be saved to Sample calendar (debug) and was lost — Boiler service is locked.").
- `CalendarSetupTest.kt`: 152, 161, 235 `containsExactly(couldNotConnect("Google Calendar", unreachable = true))` (an `UnreachableException`, a timeout, and the list without a primary, which the setup throws as one); 246 `couldNotConnect("Google", unreachable = false), couldNotConnect("Google", unreachable = false)`; 259 `containsExactly("Google Calendar connected.", couldNotConnect("Google Calendar", unreachable = false))`; 270 `containsExactly(couldNotConnect("Google Calendar", unreachable = false))`; 144, 172, 201, 227 "Google Calendar connected." / "Google Calendar reconnected.".
- `CalendarConnectHostTest.kt`: 295 `containsExactly(couldNotConnect("Google Calendar", unreachable = true))`; the "connected" and "reconnected" toasts (127, 142, 163, 175, 191, 283, 327) gain their full stop.
- `CalendarReviewTest.kt`: 77, 88, 98, 116, 137, 178: each toast gains its full stop ("Mia's swimming now shows as Mia.", …, "Google Calendar connected.", "Google Calendar reconnected.").
- `GoogleConnectFlowTest.kt:19`: `private val COULD_NOT_CONNECT = couldNotConnect(GOOGLE_DISPLAY_NAME, unreachable = false)` (import `uk.co.siland.culvery.capability.calendar.couldNotConnect`); a case there whose failure is an `UnreachableException` (the primary calendar's read) then fails on that line: it expects `couldNotConnect(GOOGLE_DISPLAY_NAME, unreachable = true)`. `GoogleConnectScreenTest.kt:177`: `containsExactly(couldNotConnect("Google Calendar", unreachable = false))`.
- `GoogleApiTest.kt:213`: `assertThat(invalid.refusal(GoogleCall.ADDING).message).isNull()`; `GoogleWriteTest.kt:232`: `assertThat(refused?.message).isNull()` (drop the `REFUSED` imports).
- `EventFormTest.kt:113`: `containsExactly("30 minutes", "1 hour", "2 hours", "1 hour 30 minutes")`; `:127-128`: `listOf(30L, 45L, 60L, 65L, 90L, 180L)` → `containsExactly("30 minutes", "45 minutes", "1 hour", "1 hour 5 minutes", "1 hour 30 minutes", "3 hours")`.
- `PeopleEditorTest.kt`: 74 `"Sam moved up."`; 76 `"Sam moved down."`; 113 `PeopleOutcome.Refused("That colour is taken — choose another.")`; 174 `"Sam removed."`; 201 `PeopleOutcome.Refused(couldNotSaveDetails(null))` (the table is gone, so the read fails first); 235 `PeopleOutcome.Refused(couldNotSaveDetails("Sam"))`.
- `PeopleUiTest.kt`: 166 `"Sam removed."`; 201 `hasText("Couldn’t save Sam’s details — try again.")` (the write fails after the read); 227 `hasText("Couldn’t save Sam’s details — try again.")` (the error escapes the editor: the sheet names its person).
- `LocationPaneTest.kt:178, 186`: `"Couldn’t save the home location — try again."`.
- `PinPadTest.kt:114`: `"Wrong PIN — try again."`; `:176`: `"Enter your PIN to carry on setting up."`; `SetupWizardTest.kt:400`: `compose.awaitText("Enter your PIN to carry on setting up.")`.

- [ ] **Step 7: Run the tests, then compare and re-record**

Run the Step 2 command, then `./gradlew testDebugUnitTest`. Expected: PASS (and `CopyScanTest` still passes: every new string is curly).
```bash
./gradlew --continue compareRoborazziDebug
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -60
```
Words only. These must change: `editor_failed_dark`, `editor_failed_light` (title "Couldn’t save to Sample calendar — Calendar is full.", body "Nothing was saved. Your details are still here to add in Sample calendar on your phone."), `editor_own_length_dark`, `editor_own_length_light`, `editor_empty_dark`, `editor_empty_light`, `editor_edit_dark`, `editor_edit_light` (Length chips "30 minutes", "1 hour", "2 hours"; a two-line chip where one doesn't fit, as 4d-1 allows), `forecast_age_dark`, `forecast_age_light` ("Updated 3 hours ago"), `pin_pad_wrong_dark`, `pin_pad_fs15` ("Wrong PIN — try again."), `wizard_pin_gate_dark` (the full stop).
These may change, only for the reason given: the other `editor_*` with Length chips showing (`editor_child_*`, `editor_date_picker_*`, `editor_keyboard_*`, `editor_time_picker_*`, dark and light); `review_read_problem_dark`, `review_read_problem_light`, `review_reconnect_dark`, `review_reconnect_light` (a row whose health words change).
Any other change: stop. Then `./gradlew recordRoborazziDebug`, write the two lists to `$TMP/expected-baselines.txt` and `$TMP/may-change.txt` (names spelled out, dark and light), and run the baseline check (Global Constraints).

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add core capability provider app
git commit -m "Say why a change failed and what to do, by cause, never save for a delete, and write minutes, hours and seconds in full with full stops on sentences"
```

---

### Task 15: Settings opens on the page a button names; location search shows it is working and can be tried again (D38, D40; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/ShellNavigator.kt:6-36` (`openSettings(page)`, `SettingsPageIds`)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt:51, 127-174, 205-209`, `shell/ShellUiState.kt:20-24`, `MainActivity.kt` (`SettingsScreen(…)`), `app/src/test/java/uk/co/siland/culvery/shell/ui/NoNavigation.kt`
- Modify: `core/setup/…/SetupCopy.kt:26-29` and new copy, `SettingsScreen.kt:46-56`, `LocationPane.kt:38-46, 60-133`, `pages/KioskPage.kt` (id), `pages/LocationPage.kt:26`, `pages/PeoplePage.kt:15`; `capability/calendar/…/CalendarSetupSteps.kt` (`CalendarsPage.id`), `ui/ConnectCalendarCard.kt:35-56`
- Modify: `capability/weather/…/WeatherWords.kt:13-14`, `ui/ForecastCard.kt:75-78`
- Modify: `core/ui/src/test/resources/copy-scan-allow.txt` (the `"kiosk"` id moves)
- Test: `core/setup/src/test/…/LocationPaneTest.kt`, `SetupScreenshotTest.kt:140-143` (and a `locationSearchingDark`), `SettingsScreenTest.kt`, `TestUi.kt:62-86` (`RecordingNavigator`); `app/src/test/…/shell/ShellViewModelTest.kt`; `capability/calendar/src/test/…/ui/CardsTest.kt`, `ui/RecordingNavigator.kt`; `capability/weather/src/test/…/ui/ForecastCardTest.kt`, `ui/RecordingNavigator.kt`; `provider/calendar-google/src/test/…/RecordingNavigator.kt`
- Baselines: new `location_searching_dark`; changed (Step 7)

**Interfaces:**
- Consumes: Tasks 1–14; `HhMessageLine` and the `location_status` line (Task 5).
- Produces:
  - `ShellNavigator.openSettings(page: String? = null)`; `object SettingsPageIds { const val LOCATION = "location"; const val PEOPLE = "people"; const val CALENDARS = "calendars"; const val TABLET_LOCK = "kiosk" }` in `:core:plugin`.
  - `ShellUiState.settingsPage: String?`; `fun SettingsScreen(pages: List<SettingsPage>, onClose: () -> Unit, initialPage: String? = null)`.
  - `TownResults.Searching`; `LocationContent(…, onRetry: () -> Unit = {})`; the status line says "Searching…" through the pause and the request.

- [ ] **Step 1: Write the failing tests**

`LocationPaneTest.kt`, add (`polite` is Task 5's matcher in this file; imports `androidx.compose.ui.test.performClick`):
```kotlin
    @Test
    fun aSearchSaysSoWhileItRuns() {
        search.answer = { awaitCancellation() }
        show()
        type("Can")
        advance(100)
        compose.onNodeWithTag("location_status").assertTextEquals("Searching…").assert(polite)
        advance(500)
        compose.onNodeWithTag("location_status").assertTextEquals("Searching…")
    }

    @Test
    fun tryAgainRunsTheSameSearchAgain() {
        var fail = true
        search.answer = { if (fail) throw LocationSearchException("offline") else listOf(brighton) }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("location_status").assertTextEquals("Couldn’t search for towns — check the tablet’s Wi-Fi, then try again.")
        fail = false
        compose.onNodeWithTag("location_retry").performClick()
        advance(500)
        assertThat(search.queries).containsExactly("Can", "Can").inOrder()
        compose.onNodeWithTag("place_0").assertExists()
        compose.onNodeWithTag("location_retry").assertDoesNotExist()
    }

    /** Review Focus 5: the newest query wins over a retry still waiting. */
    @Test
    fun editingTheQueryDuringARetryShowsOnlyTheNewQuerysTowns() {
        search.answer = { throw LocationSearchException("offline") }
        show()
        type("Can")
        advance(500)
        search.answer = { q -> if (q == "Can") awaitCancellation() else listOf(brighton) }
        compose.onNodeWithTag("location_retry").performClick()
        advance(500)
        type("x")
        advance(500)
        assertThat(search.queries).containsExactly("Can", "Can", "Canx").inOrder()
        assertThat(search.cancelled).isEqualTo(1)
        compose.onNodeWithTag("place_0").assertTextContains("Brighton, England, United Kingdom")
    }
```
and `noMatchNamesTheQuery` expects `"No towns match “Xqz” — check the spelling or try a nearby town."`; `aFailedSearchSaysToCheckTheWiFi` expects `"Couldn’t search for towns — check the tablet’s Wi-Fi, then try again."`.
`SettingsScreenTest.kt`, add:
```kotlin
    @Test
    fun settingsOpensOnTheAskedPageOrTheFirstForAnUnknownOne() {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalShellNavigator provides navigator) {
                    SettingsScreen(listOf(page("a", "Alpha", 0), page("location", "Home location", 1)), onClose = {}, initialPage = "location")
                }
            }
        }
        compose.onNodeWithTag("settings_page_location").assertIsSelected()
    }

    @Test
    fun anUnknownPageOpensTheFirst() {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalShellNavigator provides navigator) {
                    SettingsScreen(listOf(page("a", "Alpha", 0), page("b", "Beta", 1)), onClose = {}, initialPage = "nowhere")
                }
            }
        }
        compose.onNodeWithTag("settings_page_a").assertIsSelected()
    }
```
`ShellViewModelTest.kt`, add:
```kotlin
    @Test
    fun settingsOpensOnTheAskedPage() = runTest {
        access.result = admin
        val vm = vm()
        vm.uiState.test {
            vm.openSettings(SettingsPageIds.LOCATION)
            assertThat(expectMostRecentItem().settingsPage).isEqualTo("location")
        }
    }
```
(`admin` is the file's `Authorised` fixture for Alex, an Admin.)
`ForecastCardTest.kt`: `noLocationOpensSettings` becomes:
```kotlin
    @Test
    fun noLocationOffersToAddItAndOpensThatPage() {
        show(WeatherView.NoLocation)
        compose.onNodeWithText("Add home location").performClick()
        assertThat(navigator.pages).containsExactly("location")
    }
```
and `withoutALocationOpenSettingsIsAButton` finds `"Add home location"`.
`CardsTest.kt`: `connectCardOpensSettingsWhenNothingCanBeConnected` becomes:
```kotlin
    @Test
    fun withNothingToConnectTheCardSaysSoAndOffersNoButton() {
        show { ConnectCalendarCard(connectService = null, onConnect = {}) }
        compose.onNodeWithText("No calendar services are available on this tablet.").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
```
The three `RecordingNavigator`s (calendar, weather, google) and setup's `TestUi.kt` one, and `NoNavigation`: `override fun openSettings(page: String?)`; the calendar and weather ones record `val pages = mutableListOf<String?>()` and keep `settingsOpened` (incremented as before).
Each fails today: no "Searching…" or Try again, no page to open, the old Forecast button, a Connect card that offers a dead end.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :capability:calendar:testDebugUnitTest --tests "*CardsTest*" :core:setup:testDebugUnitTest --tests "*LocationPaneTest*" --tests "*SettingsScreenTest*" :app:testDebugUnitTest --tests "*ShellViewModelTest*" :capability:weather:testDebugUnitTest --tests "*ForecastCardTest*"`
Expected: FAIL as listed.

- [ ] **Step 3: Opening Settings on a page**

`ShellNavigator.kt`: `fun openSettings(page: String? = null)` with KDoc "Opens Settings, asking for a PIN if needed, on [page] (one of [SettingsPageIds]) or the first page."; the KDoc of `exitKiosk` becomes "Settings › Tablet lock (4a design §4.7): Use other apps, after a fresh Admin PIN."; and after the interface:
```kotlin
/** Settings' page ids, so a capability can open a page without depending on `:core:setup` (4d-2 §7.6). */
object SettingsPageIds {
    const val LOCATION = "location"
    const val PEOPLE = "people"
    const val CALENDARS = "calendars"

    /** Tablet lock's; stored nowhere, so the old name costs nothing. */
    const val TABLET_LOCK = "kiosk"
}
```
The pages use them: `LocationPage.id = SettingsPageIds.LOCATION`, `PeoplePage.id = SettingsPageIds.PEOPLE`, `CalendarsPage.id = SettingsPageIds.CALENDARS` (`CalendarSetupSteps.kt`), `KioskPage.id = SettingsPageIds.TABLET_LOCK`.
`ShellUiState.kt`: after `settingsOpen` add `/** The page Settings opens on; null for the first. */ val settingsPage: String? = null,`.
`ShellViewModel.kt`: `settingsOpen` (51) becomes
```kotlin
    /** Settings open, on a page or (null page) the first; null while closed. */
    private class OpenSettings(val page: String?)

    private val settings = MutableStateFlow<OpenSettings?>(null)
```
the first `combine` (129-137) reads `settings` (`settingsOpen = settings != null && session != null, settingsPage = settings?.page`); `init`'s second line, `closeSettings` and `leaveKiosk` set `settings.value = null`; `openSettings`:
```kotlin
    override fun openSettings(page: String?) {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.SETTINGS_MANAGE) != null) settings.value = OpenSettings(page)
        }
    }
```
(`openSettings` with no page still opens the first, as the rail's Settings does.)
`MainActivity.kt`: `settings = { SettingsScreen(remember { settingsPages(corePages.get(), capabilities) }, onClose = shell::closeSettings, initialPage = state.settingsPage) }`.
`SettingsScreen.kt`: `fun SettingsScreen(pages: List<SettingsPage>, onClose: () -> Unit, initialPage: String? = null)`, its KDoc's "the first chosen on open" becomes "[initialPage] chosen on open, or the first page for an unknown or no id", and line 47: `var chosen by rememberSaveable { mutableStateOf(pages.firstOrNull { it.id == initialPage }?.id ?: pages.firstOrNull()?.id) }`.

`copy-scan-allow.txt`: the line `core/setup/KioskPage.kt | kiosk | …` becomes `core/plugin/ShellNavigator.kt | kiosk | the Tablet lock page's id, never shown (4d-2 §7.6)` (the literal moved with the id; `CopyScanTest.everyAllowlistEntryIsStillNeeded` fails until it does).
`WeatherWords.kt:13-14`: `OPEN_SETTINGS` goes;
```kotlin
internal const val ADD_LOCATION = "Add your home location to see the weather."
internal const val ADD_HOME_LOCATION = "Add home location"
```
`ForecastCard.kt:75-78`: `HhButton(ADD_HOME_LOCATION, { navigator.openSettings(SettingsPageIds.LOCATION) }, tone = ButtonTone.Primary, on = ButtonSurface.Surf)` (import `uk.co.siland.culvery.core.plugin.SettingsPageIds`).
`ConnectCalendarCard.kt:35-56`:
```kotlin
/**
 * Takes the Today slot until a calendar is connected (spec §9.2), laid out like the hand-off's Holiday tile. With a
 * provider the household can connect, its button connects it (3a design §4.2); with none, it says so and offers nothing.
 */
@Composable
fun ConnectCalendarCard(connectService: String?, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    HhCard(
        modifier = modifier.fillMaxSize().testTag("calendar_connect"),
        radius = CalendarDimens.cardRadius,
        padding = PaddingValues(horizontal = CalendarDimens.connectPaddingH, vertical = CalendarDimens.connectPaddingV),
    ) {
        HhIcon(Icons.CALENDAR_ADD_ON, size = CalendarDimens.connectIconSize, tint = c.accent)
        Spacer(Modifier.weight(1f))
        Text("Connect a calendar", style = HhType.cardTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(CalendarDimens.connectSubtitleTop))
        if (connectService != null) {
            Text("Connect your family’s calendar to see it here.", style = HhType.secondary, color = c.mute)
            Spacer(Modifier.height(CalendarDimens.connectButtonTop))
            HhButton("Connect $connectService", onConnect, tone = ButtonTone.Primary, on = ButtonSurface.Surf)
        } else {
            Text("No calendar services are available on this tablet.", style = HhType.secondary, color = c.mute, modifier = Modifier.testTag("connect_none"))
        }
    }
}
```
(drop `LocalShellNavigator`.)

- [ ] **Step 4: Searching and Try again**

`SetupCopy.kt`:
```kotlin
internal const val COULD_NOT_SEARCH = "Couldn’t search for towns — check the tablet’s Wi-Fi, then try again."
internal const val SEARCHING = "Searching…"
internal const val TRY_AGAIN = "Try again"

internal fun noTownsMatch(query: String): String = "No towns match “$query” — check the spelling or try a nearby town."
```
`LocationPane.kt`: `TownResults` gains `data object Searching : TownResults`; `LocationPane`:
```kotlin
    var retries by rememberSaveable { mutableStateOf(0) }
    val results by produceState<TownResults>(TownResults.Idle, query, retries) {
        val q = query.trim()
        if (q.length < MIN_QUERY || query == chosen) {
            value = TownResults.Idle
            return@produceState
        }
        // Shown through the pause and the request: something is happening (D40).
        value = TownResults.Searching
        delay(SEARCH_PAUSE_MS)
        value = try {
            search.search(q).let { if (it.isEmpty()) TownResults.NoMatch(q) else TownResults.Found(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocationSearchException) {
            // The message can hold a status code: the copy is fixed and nothing is logged.
            TownResults.Failed
        } catch (e: Exception) {
            Log.w(TAG, "Town search failed (${e::class.simpleName})")
            TownResults.Failed
        }
    }
```
(a new query or a retry restarts it, which cancels the search before it: Review Focus 5) and the `LocationContent(…)` call gains `onRetry = { retries++ }` after `saveFailed`. `LocationContent` gains `onRetry: () -> Unit = {}` (after `saveFailed`); `statusLine` gains the searching case:
```kotlin
    results is TownResults.Searching -> StatusLine(SEARCHING)
```
(before the `Found` line), and after the `HhMessageLine(…)` call:
```kotlin
        if (results is TownResults.Failed && !saveFailed) {
            HhButton(TRY_AGAIN, onRetry, Modifier.testTag("location_retry"), on = ButtonSurface.Bg)
        }
```
(imports `uk.co.siland.culvery.core.ui.HhButton`, `ButtonSurface`.)

- [ ] **Step 5: The location fixture shows what the step would**

`SetupScreenshotTest.kt:140-143`:
```kotlin
    /** The forward button the real step shows (D40): Next once a home is saved, Skip for now without one. */
    private fun location(name: String, dark: Boolean, results: TownResults, current: HomeLocation? = brighton.toHome()) =
        step(name, dark, 1, WizardRules.forward(skippable = true, NEXT_LABEL, StepStatus(shown = true, done = current != null, canGoOn = current != null, skippable = true))) {
            StepTitle(WHERES_HOME)
            LocationContent("Brigh", {}, results, current = current, showCurrent = false, busy = false, onChoose = {})
        }
```
and add `@Test fun locationSearchingDark() = location("location_searching_dark", true, TownResults.Searching, current = null)` (imports `HomeLocation`, `uk.co.siland.culvery.core.plugin.NEXT_LABEL`).

- [ ] **Step 6: Run the tests to see them pass**

Run the Step 2 command, then `./gradlew testDebugUnitTest`. Expected: PASS, `CopyScanTest` included.

- [ ] **Step 7: Compare, record and look at the baselines**

```bash
./gradlew --continue compareRoborazziDebug
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -60
```
These must change: `location_results_dark`, `location_results_light` (the forward button "Next", enabled, instead of "Skip for now"), `location_failed_dark` ("…, then try again." and a Try again button under it), `forecast_no_location_dark`, `forecast_no_location_light` ("Add home location"). New: `location_searching_dark` ("Searching…" under the field, Skip for now). These may change: `home_empty_dark`, `home_empty_light` (only if Home's Forecast card has no location in the fixture: "Add home location"). Any other change: stop. Then `./gradlew recordRoborazziDebug`, open the new image, write the must-change and new names to `$TMP/expected-baselines.txt` and the may-change ones to `$TMP/may-change.txt`, and run the baseline check.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add core app capability provider
git commit -m "Open Settings on the page a button names, show a town search running, and let a failed search be tried again"
```

---

### Task 16: People and PINs, "Out of date", Tablet lock and the smaller copy (D36, D37, D39, D41; rulings 16, 18)

**Review:** sonnet.

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Connections.kt:3-12` (`ProviderDescriptor.connectHint`)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt:42-60` (the toaster), `:205-209` (`exitKiosk`)
- Modify: `core/setup/…/SetupCopy.kt` (lines 15, 17-18, 52, 87 and new copy), `PeopleUi.kt:68`, `PersonEditorSheet.kt:208-217`, `steps/YouStep.kt:119, 145-166`, `pages/KioskPage.kt`
- Modify: `capability/calendar/…/CalendarUi.kt:147-148` (`eventAddedFor`) and a `weekLine`, `EventForm.kt:172-181`, `CalendarSetupSteps.kt:33-46`, `ui/WeekView.kt:132-140`, `ui/CalendarType.kt:141` (stale icon sizes), `ui/TodayCard.kt:64`, `ui/ComingUpCard.kt:91`, `ui/EventEditorSheet.kt:76-92, 136-138`, `ui/EventEditorHost.kt:146-158`, `ui/CalendarConnectHost.kt:33, 80, 95-108` (`CONNECTING_LINE`, the call, `ConnectingCard`), `ui/ReviewCalendars.kt:122-211`; `provider/calendar-google/…/GoogleCalendarProvider.kt:72`
- Modify: `capability/weather/…/WeatherWords.kt:11-12`
- Test: `core/setup/src/test/…/SettingsScreenTest.kt`, `SettingsScreenshotTest.kt`, `KioskPageTest.kt`, `PeopleUiTest.kt`, `YouFormTest.kt`; `app/src/test/…/shell/ShellViewModelTest.kt`; `capability/calendar/src/test/…/SyncLabelsTest.kt`, `CalendarEditorTest.kt:128`, `EventFormTest.kt`, `ui/CardsTest.kt`, `ui/WeekViewTest.kt`, `ui/WeekScreenshotTest.kt`, `ui/ReviewCalendarsTest.kt`, `ui/ReviewScreenshotTest.kt`, `ui/ConnectScreenshotTest.kt:40`; `capability/weather/src/test/…/ui/ForecastCardTest.kt`
- Baselines: new `settings_calendars_empty_dark`, `week_stale_light`; changed (Step 9)

**Interfaces:**
- Consumes: Tasks 1–15.
- Produces:
  - `ProviderDescriptor(…, connectHint: String? = null)`; `internal fun ConnectingCard(service: String, onCancel: () -> Unit, hint: String? = null, content: …)`.
  - `fun weekLine(sync: SyncStatusUi, nowMillis: Long): String` ("Out of date — updated from Google Calendar 3 hours ago" when stale).
  - `EventForm.lockedDatesLabel(service: String): String?` (ruling 16) and `EventEditorSheet(…, service: String, …)`.
  - `ReviewCalendars(…, line: String? = null)`, `ReviewCalendarsHost(…, line: String? = null)`.
  - `ShellViewModel(…, toaster: Toaster)`.

- [ ] **Step 1: Read the emulator's connection health (D37, plan step 1)**

The 4d triage found the red "not synced yet" is expected on the emulator (a pass with any failing calendar, or unreachable Play services, never stamps `lastSyncMillis`). Confirm it from the installed app's database; this reads only:
```bash
adb -s emulator-5554 shell run-as uk.co.siland.culvery sqlite3 databases/calendar.db \
  "SELECT id, providerId, health, healthMessage, lastSyncMillis FROM connection;"
```

Notes: deferred to the final walk-through (Steps 1 and 8 read the emulator, which is off); read the connection health from Settings › Calendars there.
Record the rows (without `id`s or account emails) and the cause in one line under this step in the plan file ("Notes: …"). If `run-as` says the package isn't debuggable, a release build is installed: record "not readable: release build installed" and go on; don't reinstall anything.

- [ ] **Step 2: Write the failing tests**

`SyncLabelsTest.kt`, add:
```kotlin
    @Test
    fun aStaleWeekSaysOutOfDateFirst() {
        val stale = SyncStatusUi(minutesAgo(180), emptyList(), listOf("Google Calendar"), failingBeforeFirstSync = false)
        assertThat(weekLine(stale, now)).isEqualTo("Out of date — updated from Google Calendar 3 hours ago")
        assertThat(weekLine(stale.copy(serviceNames = listOf("Google Calendar", "School")), now)).isEqualTo("Out of date — updated 3 hours ago")
        assertThat(weekLine(stale.copy(lastSyncMillis = null, failingBeforeFirstSync = true), now)).isEqualTo("Out of date — not updated yet")
        assertThat(weekLine(stale.copy(lastSyncMillis = minutesAgo(5)), now)).isEqualTo("Updated from Google Calendar 5 minutes ago")
    }
```
`WeekViewTest.kt`, add (`sync()` builds with `connections`; give it `listOf("Google Calendar")` here):
```kotlin
    @Test
    fun aStaleWeekShowsTheIconAndTheWordsAsWellAsTheColour() {
        show { WeekView(state(sync(agoMinutes = 45, connections = listOf("Google Calendar")))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("Out of date — updated from Google Calendar 45 minutes ago")
        compose.onNodeWithTag("week_out_of_date", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aFreshWeekHasNoIcon() {
        show { WeekView(state(sync(agoMinutes = 2, connections = listOf("Google Calendar")))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("Updated from Google Calendar 2 minutes ago")
        compose.onNodeWithTag("week_out_of_date", useUnmergedTree = true).assertDoesNotExist()
    }
```
and `staleSubtitleStillShowsTheAge` (Task 12's) expects `"Out of date — updated from Google 45 minutes ago"`. (import `androidx.compose.ui.test.assertTextEquals`.)
`PeopleUiTest.kt`, `theListShowsEachPersonsRoleAndWhetherTheyHaveAPin`: `compose.awaitText("Adult · No PIN — can’t add events")`; add:
```kotlin
    @Test
    fun withoutAPinAnAdultOrChildCanOnlyLookAndTheSheetSaysSo() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithText("Without a PIN, Sam can only look.").assertExists()
        compose.onNodeWithTag("person_set_pin").performClick()
        repeat(2) { "2468".forEach { d -> compose.onNodeWithTag("pin_key_$d").performClick() } }
        compose.onNodeWithText("Without a PIN, Sam can only look.").assertDoesNotExist()
    }

    @Test
    fun anAdminIsNeverToldTheyCanOnlyLook() {
        show()
        compose.onNodeWithTag("person_Alex").performClick()
        compose.onNodeWithText("can only look", substring = true).assertDoesNotExist()
    }
```
`YouFormTest.kt`, add:
```kotlin
    @Test
    fun thePinButtonSaysChangeOnceAPinIsChosen() {
        val form = YouForm()
        compose.setContent { CulveryTheme(dark = true) { YouFormContent(form, onSetPin = {}) } }
        compose.onNodeWithTag("you_set_pin").assertTextEquals("Set your PIN")
        compose.runOnIdle { form.pin = "1234" }
        compose.onNodeWithTag("you_set_pin").assertTextEquals("Change PIN")
    }
```
(imports `androidx.compose.ui.test.assertTextEquals`.) The You step's title line is checked in `StepsUiTest` if it renders `YouStep.Content`; otherwise by the `you_*` baselines (Step 9).
`SettingsScreenTest.theKioskPageExitsThroughTheShell` expects the line `"Culvery keeps the tablet on this app. Tap Use other apps to leave it. It locks again when Culvery next opens."`.
`KioskPageTest.kt`, add:
```kotlin
    private val howTo = "If that doesn’t work: tap Use other apps, then choose the home app in Android’s Settings › Apps › Default apps."

    @Test
    fun theHowToLineShowsWhetherOrNotCulveryIsTheHomeApp() {
        show()
        compose.onNodeWithText(howTo).assertExists()
        home.isDefault.value = true
        compose.onNodeWithText(howTo).assertExists()
    }
```
`ShellViewModelTest.kt`: `vm()` and line 157 pass a `ShellToasts()` as the new last argument (keep it in a field, `private val toasts = ShellToasts()`); add:
```kotlin
    @Test
    fun usingOtherAppsSaysWhatHappensNext() = runTest {
        access.result = admin
        val vm = vm()
        vm.exitKiosk()
        runCurrent()
        assertThat(toasts.current.value?.message).isEqualTo("You can use other apps now. Culvery locks again when it next opens.")
    }
```
`ForecastCardTest.kt`: `expiredSaysToCheckTheWifi` expects `"No forecast — check the tablet’s Wi-Fi. Culvery will try again."`; `failedSaysThereIsNoForecastYetUnderItsTitle` `"No forecast yet — check the tablet’s Wi-Fi. Culvery will try again."`.
`CardsTest.kt`: `emptyTodaySaysNothingOnToday` becomes two:
```kotlin
    @Test
    fun anEmptyTodayWithAddSaysWhatToDo() {
        show { TodayCard(emptyList(), onAdd = {}) }
        compose.onNodeWithText("Nothing on today. Tap + to add something.").assertExists()
    }

    @Test
    fun anEmptyTodayWithoutAddJustSaysSo() {
        show { TodayCard(emptyList()) }
        compose.onNodeWithText("Nothing on today").assertExists()
    }
```
and `comingUpShowsTwoRowsAndAVisibleMoreInTheWideCard` expects `"+2 more in Calendar"`.
`ReviewCalendarsTest.kt`, add:
```kotlin
    @Test
    fun withNothingConnectedTheCalendarsPageSaysWhatToDo() {
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    title = "Calendars", connections = emptyList(), people = listOf(Person.Family), nowMillis = now, busy = false,
                    picking = null, confirming = null, connectable = emptyList(), actions = ReviewActions(),
                )
            }
        }
        compose.onNodeWithText("Connect your family’s Google Calendar to see it on Home.").assertExists()
    }

    @Test
    fun theWizardsStepHasItsLine() {
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    title = "Your calendars", connections = listOf(ReviewConnection(row(), listOf(family))), people = listOf(Person.Family),
                    nowMillis = now, busy = false, picking = null, confirming = null, connectable = emptyList(), actions = ReviewActions(),
                    line = "Choose who each calendar is for, and which to show. New events go to the main calendar.",
                )
            }
        }
        compose.onNodeWithText("Choose who each calendar is for, and which to show. New events go to the main calendar.").assertExists()
    }
```
`EventFormTest.kt`, the locked-dates test(s) call `form.lockedDatesLabel("Google Calendar")` and expect "… · change dates in Google Calendar on your phone". `CalendarEditorTest.kt:128`: `containsExactly(EVENT_ADDED, "Event added on Wed 21 October.")` (keep the date the test builds; only the words change).
Each fails today: no "Out of date", the old row, no look-only line, no Change PIN, no how-to, no exit toast, the old card words.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew --continue :capability:calendar:testDebugUnitTest --tests "*SyncLabelsTest*" --tests "*WeekViewTest*" --tests "*CardsTest*" --tests "*ReviewCalendarsTest*" --tests "*EventFormTest*" --tests "*CalendarEditorTest*" :core:setup:testDebugUnitTest --tests "*PeopleUiTest*" --tests "*YouFormTest*" --tests "*SettingsScreenTest*" --tests "*KioskPageTest*" :app:testDebugUnitTest --tests "*ShellViewModelTest*" :capability:weather:testDebugUnitTest --tests "*ForecastCardTest*"`
Expected: FAIL as listed.

- [ ] **Step 4: Tablet lock's toast**

`ShellViewModel.kt`: the constructor gains `private val toaster: Toaster` (last);
and `exitKiosk` toasts once it has left:
```kotlin
    override fun exitKiosk() {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.KIOSK_EXIT) != null) {
                leaveKiosk()
                kioskExitEvents.send(Unit)
                // As the home app Culvery stays in front, so nothing else shows that it worked (4d-2 §7.8).
                toaster.show(USING_OTHER_APPS)
            }
        }
    }
```
with, at the file's end, `internal const val USING_OTHER_APPS = "You can use other apps now. Culvery locks again when it next opens."`; `changeHomeApp`'s KDoc becomes "Asks for kiosk.exit's fresh PIN: its pad says \"Use other apps\", which is what this does." (imports `Toaster`; `MainActivity`'s `navigator` object delegates the new signature unchanged.)

- [ ] **Step 5: The words and parts of D36, D37, D39 and D41**

`SetupCopy.kt`:
```kotlin
internal const val WELCOME_LINE = "We’ll set up your home’s location, the people who live here, and your calendars."
internal const val WHOS_SETTING_UP = "Who’s setting this up?"
internal const val YOU_LINE = "You’ll be the Admin — you can change settings and people. Admins need a PIN."
internal const val SET_YOUR_PIN = "Set your PIN"
```
```kotlin
internal const val USED_FOR = "Used for the clock’s time zone and the weather."
```
```kotlin
internal const val NO_PIN_CANT_ADD = "No PIN — can’t add events"

/** C97: an Adult or Child without a PIN can't be told apart from anyone, so they can only look. */
internal fun canOnlyLook(name: String): String =
    if (name.isBlank()) "Without a PIN, they can only look." else "Without a PIN, ${name.trim()} can only look."

internal fun removeQuestion(name: String): String = "Remove $name? $name’s events and calendars will show as Family."
```
```kotlin
internal const val TABLET_LOCK_LINE = "Culvery keeps the tablet on this app. Tap Use other apps to leave it. It locks again when Culvery next opens."
internal const val HOME_APP_HOW_TO = "If that doesn’t work: tap Use other apps, then choose the home app in Android’s Settings › Apps › Default apps."
```
(`KIOSK_LINE` goes.)
`PeopleUi.kt:68`, the subtitle:
```kotlin
        subtitle = "${roleName(member.role)} · ${pinState(member)}",
```
with, below `PersonRow`:
```kotlin
/** An Admin's PIN state, or what an Adult or Child without one can't do (D36). */
private fun pinState(member: Member): String = when {
    member.hasPin -> PIN_SET
    member.role == Role.ADMIN -> NO_PIN
    else -> NO_PIN_CANT_ADD
}
```
(import `uk.co.siland.culvery.core.household.Role`.)
`PersonEditorSheet.kt`, the `PIN` section (208-217) gains, after the buttons' `Row`:
```kotlin
                    if (form.role != Role.ADMIN && !form.hasPin) {
                        Text(canOnlyLook(form.name), style = SetupType.secondary, color = c.mute, modifier = Modifier.testTag("person_look_only"))
                    }
```
(the `Section`'s `content` is a `Column` already; if it isn't, wrap the Row and the line in one.)
`YouStep.kt`: line 119 `StepTitle(WHOS_SETTING_UP, YOU_LINE)`; the PIN button: `HhButton(if (form.pin == null) SET_YOUR_PIN else CHANGE_PIN, onSetPin, Modifier.testTag("you_set_pin"), on = ButtonSurface.Bg)` (import `CHANGE_PIN`).
`KioskPage.kt`:
```kotlin
    @Composable
    override fun Content() {
        val navigator = LocalShellNavigator.current
        val isHome by homeApp.isDefault.collectAsState()
        StepTitle(TABLET_LOCK, TABLET_LOCK_LINE)
        if (isHome) {
            HhButton(CHANGE_HOME_APP, navigator::changeHomeApp, Modifier.testTag("settings_change_home_app"), on = ButtonSurface.Bg)
        } else {
            HomeAppPrompt(onChoose = navigator::chooseHomeApp)
        }
        // The how-to the toasts used to hold, where there's time to read it (D41). One UI's menu name is checked on the device (E5).
        Text(HOME_APP_HOW_TO, style = SetupType.secondary, color = Culvery.colors.mute, modifier = Modifier.testTag("kiosk_how_to"))
        HhButton(USE_OTHER_APPS, navigator::exitKiosk, Modifier.testTag("settings_exit_kiosk"), on = ButtonSurface.Bg)
    }
```
(imports `Text`, `Culvery`, `SetupType`.)
`CalendarUi.kt`: `eventAddedFor` (147-148) becomes `fun eventAddedFor(day: LocalDate): String = "Event added on ${day.format(DAY_AND_MONTH)}."` and after `weekSubtitle`:
```kotlin
/** The week's line with its state in words as well as colour (D37): "Out of date — updated from Google Calendar 3 hours ago". */
fun weekLine(sync: SyncStatusUi, nowMillis: Long): String {
    val line = weekSubtitle(sync, nowMillis)
    return if (sync.isStaleAt(nowMillis)) "Out of date — ${line.replaceFirstChar { it.lowercase() }}" else line
}
```
`CalendarType.kt`, after `subtitleTop` (141): `val staleIcon = 16.dp` and `val staleIconGap = 6.dp` (comment: "The stale week's sync_problem, 6 dp before its words (D37).").
`WeekView.kt:133-140`:
```kotlin
                    val stale = state.sync.isStaleAt(state.nowMillis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.staleIconGap)) {
                        if (stale) HhIcon(Icons.SYNC_PROBLEM, size = CalendarDimens.staleIcon, tint = c.danger, modifier = Modifier.testTag("week_out_of_date"))
                        Text(
                            weekLine(state.sync, state.nowMillis),
                            style = CalendarType.subtitle,
                            color = if (stale) c.danger else c.mute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("week_subtitle"),
                        )
                    }
```
`TodayCard.kt:64`: `events.isEmpty() -> Text(if (onAdd != null) "Nothing on today. Tap + to add something." else "Nothing on today", style = HhType.body, color = c.mute)`.
`ComingUpCard.kt:91`: `Text("+${events.size - MAX_ROWS} more in Calendar", …)` (the rail tab's name; the KDoc's "+N more" becomes "+N more in Calendar").
`EventForm.kt:172-181`:
```kotlin
    /**
     * A locked multi-day edit's one line: "Mon 22 – Wed 24 · change dates in Google Calendar on your phone", or, when the
     * span crosses a month, "Wed 30 Sep – Fri 2 Oct · …" on both ends (C46: [service] names where to change them).
     */
    fun lockedDatesLabel(service: String): String? = original?.takeIf { datesLocked }?.let { o ->
        val (first, last) = daySpan(o.start, o.end, zone)
        val formatter = if (first.month == last.month && first.year == last.year) DAY_AND_DATE else SHORT_DAY
        "${first.format(formatter)} – ${last.format(formatter)} · change dates in $service on your phone"
    }
```
`EventEditorSheet.kt`: the signature gains `service: String,` after `people` (ruling 16: the sheet names the service), line 137 `LockedDates(form.lockedDatesLabel(service).orEmpty())`; `EventEditorHost.kt:146` passes `service = loaded.label,`. The other callers pass `service = "Google Calendar"`: `OverlayLayersTest.kt:138`, calendar `FontScaleTest.kt` (the editor), `EditorScreenshotTest.kt`, `EventEditorSheetTest.kt`'s `show`.
`Connections.kt`, `ProviderDescriptor` gains, last: `/** The connecting card's second line, e.g. what to choose in the provider's screens; none when it has nothing to add (C87e). */ val connectHint: String? = null,`. `GoogleCalendarProvider.kt:72`: `ProviderDescriptor(GOOGLE_PROVIDER_ID, GOOGLE_DISPLAY_NAME, Icons.CALENDAR_MONTH, setOf(Feature.READ, Feature.WRITE), connectHint = "Choose the family’s Google account and allow access.")`.
`CalendarConnectHost.kt`: `CONNECTING_LINE` goes; `ConnectingCard(service: String, onCancel: () -> Unit, hint: String? = null, content: @Composable () -> Unit = {})` draws `if (hint != null) Text(hint, style = CalendarType.subtitle, color = c.mute)` under the title; `CalendarConnectHost` passes `hint = request.provider.descriptor.connectHint`. `ConnectScreenshotTest.kt:40`: `ConnectingCard("Google Calendar", onCancel = {}, hint = "Choose the family’s Google account and allow access.")` (the baseline keeps its line).
`ReviewCalendars.kt`: `ReviewCalendars` gains `line: String? = null` (last), and draws under its title:
```kotlin
        if (line != null) Text(line, style = CalendarType.subtitle, color = Culvery.colors.mute)
        if (connections.isEmpty()) {
            Text(
                "Connect your family’s Google Calendar to see it on Home.",
                style = CalendarType.subtitle,
                color = Culvery.colors.mute,
                modifier = Modifier.testTag("review_none"),
            )
        }
```
`ReviewCalendarsHost` gains `line: String? = null` and passes it; `CalendarSetupSteps.kt`: `ReviewCalendarsStep.Content` passes `line = YOUR_CALENDARS_LINE` with `const val YOUR_CALENDARS_LINE = "Choose who each calendar is for, and which to show. New events go to the main calendar."` beside `YOUR_CALENDARS`.
`WeatherWords.kt:11-12`:
```kotlin
internal const val NO_FORECAST = "No forecast — check the tablet’s Wi-Fi. Culvery will try again."
internal const val NO_FORECAST_YET = "No forecast yet — check the tablet’s Wi-Fi. Culvery will try again."
```

- [ ] **Step 6: The new baselines' fixtures**

`SettingsScreenshotTest.kt`: nothing changes in its code; `settings_kiosk_*` re-record with the new line and how-to (ruling 18). `ReviewScreenshotTest.kt`: add `@Test fun calendarsEmptyDark()` drawing `ReviewCalendars(title = "Calendars", connections = emptyList(), people = listOf(Person.Family), nowMillis = NOW, busy = false, picking = null, confirming = null, connectable = listOf(googleDescriptor), actions = ReviewActions())` inside its Settings frame as `settings_calendars_page_*` does, recorded as `settings_calendars_empty_dark` (use the file's own `snap` and descriptor fixtures). `WeekScreenshotTest.kt`: add `@Test fun weekStaleLight() = snap("week_stale_light", false, stale)` beside `week_stale_dark` (the file's `stale` status).

- [ ] **Step 7: Run the tests to see them pass**

Run the Step 3 command, then `./gradlew testDebugUnitTest`. Expected: PASS, `CopyScanTest` included.

- [ ] **Step 8: Write down the emulator's answer**

If Step 1's note is still empty, fill it now. It closes the 4d-1 follow-up "plan step 1 reads the emulator's connection".

- [ ] **Step 9: Compare, record and look at the baselines**

```bash
./gradlew --continue compareRoborazziDebug
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -60
```
These must change:
- calendar: `week_stale_dark` (the `sync_problem` icon before "Out of date — updated from Google Calendar …"); `coming_up_busy_dark` ("+2 more in Calendar"); `editor_locked_dark`, `editor_locked_light` ("· change dates in Google Calendar on your phone").
- setup: `welcome_dark`, `welcome_light` ("We’ll set up…"); `you_dark`, `you_light` (the title line and "Change PIN": the fixture has a PIN); `people_list_dark`, `people_list_light`, `settings_people_dark`, `settings_people_light` (an Adult or Child without a PIN reads "… · No PIN — can’t add events"); `person_new_dark`, `person_new_light` ("Without a PIN, they can only look." under PIN: a new person is an Adult with no PIN); `person_remove_dark` ("… will show as Family."); `settings_location_dark`, `settings_location_light`, `location_results_dark`, `location_results_light`, `location_failed_dark` ("Used for the clock’s time zone and the weather."); `settings_kiosk_dark`, `settings_kiosk_light` (the new line and the how-to line).
- weather: `forecast_expired_dark`, `forecast_expired_light`, `forecast_failed_dark`, `forecast_failed_light` ("… Culvery will try again.").
New: `settings_calendars_empty_dark` (the empty line above Connect Google Calendar), `week_stale_light`.
These may change, only for the reason given: `today_empty_dark` (the longer line, if its fixture passes `onAdd`); `welcome_sample_dark` (the welcome line); `wizard_frame_dark`, `wizard_frame_light` (if the frame's fixture is the You step: its line); `person_pin_dark`, `person_error_dark` (the look-only line, if the fixture's person has no PIN); `connected_step_dark`, `connected_step_light` (the wizard's calendars line); `location_searching_dark` ("Used for…"); `home_empty_dark`, `home_empty_light` (the Connect card unchanged; only if a Today card shows empty).
`connect_*` and `connecting_*` stay as they are (the fixture passes the same hint). Open each new image. Any other change: stop. Then `./gradlew recordRoborazziDebug`, write the lists, and run the baseline check.

- [ ] **Step 10: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add core app capability provider docs/superpowers/plans/2026-10-09-culvery-04d2-talkback-timeouts-words.md
git commit -m "Say who can only look without a PIN, mark an out-of-date week in words, explain Tablet lock where there is time to read, and tidy the smaller copy"
```

---

### Task 17: Wrap-up: README, follow-ups, every changed baseline, the TalkBack check and the wording walk-through (§8.5, §8.6, §9 item 14; rulings 19, 27)

**Review:** sonnet (Steps 1–6 by the implementer); Steps 7–10 by the controller with the user.

**Files:**
- Modify: `README.md:32, 57, 163-171, 178-180`, `docs/setup/release.md:53`
- Modify: `docs/superpowers/plans/2026-09-23-plan1-followups.md` (lines 8, 32, 53-54, 75-76, 88-89, 97-98, 111, 123, 143-192, 264-270, and a new section before "## Feature ideas")
- Create: `docs/superpowers/plans/2026-10-09-4d2-walkthrough.md`
- Modify (by the controller, after the walk-through): the walk-through file's transcript, the follow-ups file again

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only.

- [ ] **Step 1: The README and the release guide in the new words**

`README.md`:
1. Line 32: "it comes back to this week after 2 minutes untouched and at midnight." becomes "it comes back to this week after 2 minutes untouched (never while TalkBack is on, and later if Android's \"Time to take action\" asks for longer) and at midnight."
2. Line 57: "(Home location, People, Kiosk)" becomes "(Home location, People, Tablet lock)".
3. Line 163: `## Kiosk mode` becomes `## Tablet lock (kiosk mode)`. Line 165: "Settings › Kiosk, offer" becomes "Settings › Tablet lock, offer"; "**Settings › Kiosk › Exit kiosk** (Admin PIN, always asked) unpins it;" becomes "**Settings › Tablet lock › Use other apps** (Admin PIN, always asked) unpins it and says so in a toast;".
4. Line 178 becomes these two bullets:
```markdown
- A session lasts 2 minutes after the last PIN-checked action; while Settings is open, every touch in it (its sheets and PIN pads included) restarts the 2 minutes, or 10 minutes while an open sheet has unsaved changes. 20 seconds before Settings closes it shows "Settings closes in 20 seconds. Touch the screen to stay." With TalkBack on, moving between items counts as a touch, and Android's "Time to take action" (Settings › Accessibility) can make every wait longer, never shorter. During setup the first Admin stays signed in until Done, or until 10 minutes pass without a touch; the wizard then asks for their PIN to carry on. While someone is signed in, the status bar shows their name and role and a **Sign out** link. Settings closes when the session ends.
- A sheet or picker left open over Home or the Calendar tab closes after 2 minutes untouched, or after 10 minutes with unsaved changes ("This closes in 20 seconds. Touch the screen to keep your changes." 20 seconds before). A PIN pad cancels after 2 minutes untouched. Google's connecting card never closes by itself.
```
5. Line 179: "edit, delete and assign any event on the master calendar" becomes "change, delete and choose who any event on the main calendar is for"; "and can't assign." becomes "and can't choose who an event is for."
6. Line 180: "Exiting kiosk, changing the home app," becomes "Using other apps (Tablet lock), changing the home app,".

`docs/setup/release.md:53`: "the Done step (and Settings › Kiosk)" becomes "the Done step (and Settings › Tablet lock)"; "**Exit kiosk** unpins it" becomes "**Use other apps** unpins it"; "use **Settings › Kiosk › Change home app** (a fresh Admin PIN; the pad says \"Exit kiosk mode\")" becomes "use **Settings › Tablet lock › Change home app** (a fresh Admin PIN; the pad says \"Enter your PIN to use other apps.\")".
Then: `grep -n "Kiosk\b\|Exit kiosk\|master calendar\|assign" README.md docs/setup/*.md`. Expected: only line 73's "the master calendar" (the database's own word for the column, which stays) and line 131's "master calendar" in "Adding a calendar provider" (code-facing; it names `CalendarWriter`'s target). Change line 131's "the household's master calendar" to "the household's main calendar (the `master` flag in `calendar.db`)" and leave line 73.

- [ ] **Step 2: Update the follow-ups**

In `docs/superpowers/plans/2026-09-23-plan1-followups.md` delete these items (4d-2 did them):
- line 8, "`HhIcon` clears semantics …" (D26 labelled the add hints and the weather);
- line 32, "Clickable event rows and week chips have no `Role.Button`." (D15);
- lines 53-54, "Disabled Who chips lack disabled semantics." and "`AddEventButton` lacks an `onClickLabel`." (D23, D15);
- line 76, "Settings page list items have no `Role.Tab` …" (D16);
- lines 88-89, the header weather's merged description and the Forecast row's "17°" check (D26);
- line 98, "Move up / Move down have spoken labels but no other accessibility review yet." (D28);
- line 123, T14 (D30: `AppContentTest.aTapInsideAnOpenSheetCountsAsATouch` and the `WeekViewTest` snap-back tests);
- in "## From Plan 4d-1 (deferred)" › "For Plan 4d-2", the Forecast row item, the `HhCloseButton` item and the reconnect chip item (Tasks 2 and 8).
If a heading is left with no items, delete the heading too.
Line 111 (the two home-app toasts) becomes: "- One UI's name for Android's default-apps menu: the Tablet lock page's how-to line says \"Settings › Apps › Default apps\" (`HOME_APP_HOW_TO` in `SetupCopy.kt`); on One UI it may be \"Choose default apps\". Check on the SM-T510 (E5)."
In "## From the 4d triage (2026-10-06)": after "4d-1 is done (…)." add "4d-2 is done (`docs/superpowers/plans/2026-10-09-culvery-04d2-talkback-timeouts-words.md`): D15–D41 and Decision 1 are closed." Replace the whole "**For Plan 4d-2 (TalkBack, timeouts, words)**" block (from its heading, line 147, to the last D41 line, line 192) with nothing; the W, E and X lists stay.
Move the 4d-1 deferred item "`WeatherRepositoryTest` feeds the failures in directly; …" to that section's "For Plan 4e (the device)" list, and the item "At font scale 1.5 the week view's Today column clips its last chip; …" to its "For the end-of-v1 design walk-through" list; then delete the emptied "For Plan 4d-2" heading.
Before "## Feature ideas (2026-10-02, after v1)" add:
```markdown
## From Plan 4d-2 (deferred)

**For Plan 4e (the device)**
- E7 stays: a TalkBack walk of every screen on the SM-T510. The emulator check (`docs/superpowers/plans/2026-10-09-4d2-walkthrough.md`) covered Home, an event, adding an event, the PIN pad, Settings › People and the Settings warning.
- The Settings session counts no touch while Google's account chooser is open during a Reconnect from Settings › Calendars, so a slow chooser can close Settings behind it (4d-2 spec §10; E6).
- `ShellUserActivity` relies on Compose sending TalkBack's accessibility events up through `requestSendAccessibilityEvent`. `ShellUserActivityTest` pins only the delegate's filter (it sends the events itself); only the emulator check (walk-through A.4 path 6) shows Compose sending them. Re-run that check after a Compose upgrade.

**For the end-of-v1 design walk-through (the user's own)**
- Copy rows no 4d-2 item owned: C25, C43 ("Pick date…" or "Pick a date"), C49 ("Try again" or "Try saving again") (4d-2 spec Appendix A.3).
- The week legend now says each colour's name; keep it or drop it now that chips name their person (W33).
- The warning pills, "Searching…", "Try again", the "Out of date" icon, the Tablet lock how-to line and the message lines' blank line while empty are placed by the plan, not designed (4d-2 spec §2).

**Decisions for the user**
- The calendar's refusal toasts after a PIN (`Refusal.Toast`: "Mia can only change events they created.") still name the person whose PIN it was; the PIN pad's own refusal names no one (decision A). Should the toasts say "That PIN …" too?
```

- [ ] **Step 3: Write the walk-through file**

Create `docs/superpowers/plans/2026-10-09-4d2-walkthrough.md`:
````markdown
# Culvery 4d-2: the TalkBack check and the wording walk-through

Plan: `docs/superpowers/plans/2026-10-09-culvery-04d2-talkback-timeouts-words.md`, Task 17. Spec: §8.6, Decision 7.

Part A is run by the controller with TalkBack on, reading what TalkBack says from the emulator's logs. Part B is the user's own walk-through of the same screens, without TalkBack, for the wording.

## Rules for both parts

- The **already-running** API 35 Google Play emulator `emulator-5554`. If `adb devices` doesn't list it, ask the user to start it; don't start another.
- **Never clear Culvery's data or wipe the emulator** without the user's explicit yes to the exact question in A.1.
- **Ask before** adding an event or changing the home location, and undo it afterwards as the user prefers.
- **The user types every PIN.** The controller never types one, with or without TalkBack.
- **No PIN reaches the logs.** TalkBack speaks every key the user touches. Before any PIN entry the controller stops the background speech capture (A.2's `logcat`, or `uiautomator events`), and starts it again with `adb -s emulator-5554 logcat -c` first only once the pad has gone. The one exception is A.4 path 4, which uses a throwaway PIN (A.4).
- **The transcript names nothing of the household's.** Event titles, people's names, towns and account addresses are written as "[event]", "[person]", "[town]", "[account]"; a key's speech from the pad is never written down. Before the transcript is committed, the controller greps it for every name and title it saw (Task 17 Step 9).
- **Offline** only with `adb -s emulator-5554 shell svc wifi disable` and `svc data disable` (`enable` to come back); never airplane mode.
- Screenshots: `adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4d2-<item>.png"`.

## A. TalkBack (the controller)

### A.1 Install the signed release

```bash
adb devices
./gradlew :app:assembleRelease
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
```
If `assembleRelease` stops with "Release signing isn't set up", stop and ask the user (the four `culvery.release.*` lines go in their own `~/.gradle/gradle.properties`, `docs/setup/release.md` §1). If `install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a debug build is installed: stop and ask. Installing the release then needs `adb uninstall uk.co.siland.culvery`, which deletes the household (people, PINs, the Google connection, the home location); ask exactly: "The emulator has a debug build. Installing the release means uninstalling it, which deletes Culvery's people, PINs, Google connection and home location; you'd run setup and reconnect Google afterwards. Uninstall it?" Without a clear yes, run Part A on the debug build instead (`ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug`) and note that in the transcript.
A release pins the screen: to reach Android's own settings during the check, use Settings › Tablet lock › Use other apps (the user's PIN).

### A.2 Turn TalkBack on and its speech log

```bash
adb -s emulator-5554 shell pm list packages | grep -i talkback
adb -s emulator-5554 shell settings get secure enabled_accessibility_services > "$TMP/culvery-a11y-before.txt"
adb -s emulator-5554 shell settings get secure accessibility_enabled >> "$TMP/culvery-a11y-before.txt"
adb -s emulator-5554 shell settings get secure touch_exploration_enabled >> "$TMP/culvery-a11y-before.txt"
cat "$TMP/culvery-a11y-before.txt"
```
Expected: `package:com.google.android.marvin.talkback`. If it's missing, stop and ask the user (the image has no TalkBack). Keep the three saved values for A.6.
```bash
adb -s emulator-5554 shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb -s emulator-5554 shell settings put secure accessibility_enabled 1
adb -s emulator-5554 shell settings put secure touch_exploration_enabled 1
```
TalkBack speaks "TalkBack on". Its speech goes to the log only at the verbose level: in TalkBack's settings (Settings › Accessibility › TalkBack › Settings › Advanced settings › Developer settings, shown once Android's developer options are on, which they are on the emulator) note the **Log output level** it shows now (write it in A.5; A.6 puts it back), then set it to **Verbose**. Then start the log in the background:
```bash
adb -s emulator-5554 logcat -c
adb -s emulator-5554 logcat -v brief --pid="$(adb -s emulator-5554 shell pidof com.google.android.marvin.talkback | tr -d '\r')" > "$TMP/culvery-talkback.log"
```
and read what it said with:
```bash
grep -i -E "speak|utterance|tts" "$TMP/culvery-talkback.log" | tail -20
```
If nothing is logged after a swipe, use the accessibility-event dump instead: `adb -s emulator-5554 shell uiautomator events > "$TMP/culvery-a11y-events.log"` in the background, reading each event's `Text` and `ContentDescription`. Check right after starting it that TalkBack still speaks (`settings get secure enabled_accessibility_services` still names it); if the dump turned TalkBack off, stop the dump, turn TalkBack on again (the three `put` lines) and ask the user how to go on.

### A.3 Moving with TalkBack

`adb -s emulator-5554 shell wm size` gives the screen; take a screenshot to place swipes on the content, away from the status bar. Try, in this order, and keep the first that TalkBack accepts (record which in the transcript):
1. Keyboard: next `adb -s emulator-5554 shell input keycombination KEYCODE_ALT_LEFT KEYCODE_DPAD_RIGHT`, previous `… KEYCODE_ALT_LEFT KEYCODE_DPAD_LEFT`, activate `… KEYCODE_ALT_LEFT KEYCODE_ENTER`.
2. Gestures: next `adb -s emulator-5554 shell input swipe 400 400 900 400 120`, previous `… 900 400 400 400 120`, activate a double-tap in one shell call: `adb -s emulator-5554 shell "input tap 640 400; input tap 640 400"`.
To put TalkBack's focus on one item, a single `input tap` on it (touch exploration focuses what is touched).

### A.4 The paths (spec §8.6)

For each, note what TalkBack said, in order, in the transcript below; anything that reads badly, twice, or over another announcement is fixed (with a test, the gate and a commit) or listed with a reason.
1. **Home.** Header weather (one item: "17 degrees, sunny, high 19, low 11" in the plan's words), the clock and date, Today's rows ("09:30, Swimming, Sam's event…" one item each, with "open event details"), Today's +, Coming up rows and their day headings, the Forecast rows (one item per day), the rail tabs ("Home, tab, selected").
2. **An event.** Open one: the pane title "Event details"; the rows ("Time, …", "Who, …" merged); Change and Delete with their roles; Delete › the confirm card › "Keep" (never Delete).
3. **Adding an event** (ask first; it adds to the household's calendar; delete it afterwards as the user says). Add event: pane title "Add event"; the field read as "Event name"; the Who chips as radio buttons with "selected"; Pick date… (pane "Pick a date", a day cell "Tuesday 7 October, today"); Pick time… ("Hour up", the value spoken as it changes); Add event; the toast ("Event added on …"), read once.
4. **The PIN pad, with a throwaway PIN.** Ask: "May I have you add a person called Test, an Adult, with a PIN you don't use anywhere, so I can listen to the PIN pad? I'll ask you to remove Test afterwards." Without a yes, skip this path and the user listens to it instead (TalkBack speaks aloud on the emulator) and says what they heard; nothing is logged. With a yes, capture paused, the user adds Test (their own PIN asks first). Then, capture running: open Settings: pane "Enter your PIN"; the keys as buttons; the user types Test's first digit: "1 of 4 digits entered"; the user types a PIN no one has: "Wrong PIN — try again" (listen whether the dots' "0 of 4 digits entered" is spoken as well, and how many times); the user types Test's whole PIN: "That PIN can’t do this. Ask an Admin." (Test may not change settings; no name is said, decision A); if the user agrees to see the lockout (five wrong or refused PINs lock the pad for 30 seconds), the lockout is spoken once, not every second. Cancel; capture paused; the user removes Test (their own PIN).
5. **Settings › People.** The page list as tabs ("People, tab, selected"); a row ("Sam, Adult · PIN set" with "change Sam's details"); Move Sam down: TalkBack's focus stays on Sam's row in its new place; the row's custom actions (TalkBack's actions menu, or `adb … input keycombination KEYCODE_ALT_LEFT KEYCODE_SPACE` for the menu) list "Move Sam up" / "Move Sam down"; Sam's sheet: the swatches as radio buttons with names ("Blue, selected", "Green, used by Alex").
6. **The Settings warning.** In Settings, touch nothing for 1 minute 40 seconds (with a long "Time to take action", longer; TalkBack itself must stay quiet, so no swipes): TalkBack says "Settings closes in 20 seconds. Touch the screen to stay." once. Then one TalkBack swipe: the warning goes and Settings stays open past the 2 minutes (this is the only check that Compose sends TalkBack's events through the content view). Then leave it: Settings closes 2 minutes later.
7. **What appears, said once.** Compose speaks a live region when a node it already has changes; these appear new, so listen whether TalkBack says each as it appears, and how often: a toast (path 3's "Event added on …"); in Settings › Home location, typing "Xqz" (nothing is saved): "Searching…", then "No towns match “Xqz” — …" (the always-present status line); a person sheet's name the same as someone's, then Save (ask first; a refused save changes nothing): "Someone is already called …" (the always-present message line). Any that TalkBack doesn't say: listed in the follow-ups with what was heard (the toast, the two warnings, the editor's failure card and the Saving pill appear new and have no always-present slot: a slot in their layer would hide the shell under it from TalkBack).

### A.5 Transcript

(Filled in by the controller: the navigation method that worked, then for each path what TalkBack said, and what was fixed or listed.)

### A.6 Turn TalkBack off and restore

First set TalkBack's Log output level back to the value A.5 noted. Then:
```bash
adb -s emulator-5554 shell settings put secure enabled_accessibility_services "<first saved value>"
adb -s emulator-5554 shell settings put secure accessibility_enabled "<second saved value>"
adb -s emulator-5554 shell settings put secure touch_exploration_enabled "<third saved value>"
```
A saved value of `null` means the setting wasn't there: use `adb -s emulator-5554 shell settings delete secure <name>` instead of its `put`. Stop the background `logcat` (and `uiautomator events`). Then, once the transcript has been checked for names (Task 17 Step 9), delete what the check left behind:
```bash
rm -f "$TMP/culvery-talkback.log" "$TMP/culvery-a11y-events.log" "$TMP/culvery-a11y-before.txt" "$TMP"/culvery-4d2-*.png "$TMP/culvery-names.txt"
adb -s emulator-5554 logcat -c
```

## B. The wording walk-through (the user)

Without TalkBack. The user goes through these screens and says what reads wrong; the controller fixes it (test, gate, commit) or lists it in the follow-ups.
1. **Home:** the week line ("Updated from Google Calendar 5 minutes ago", or "Out of date — …" with its icon), "Nothing on today. Tap + to add something.", "+2 more in Calendar", the Forecast card's words; with the user's yes, the offline Forecast: go offline (`svc wifi disable`, `svc data disable`), wait for the next fetch, read "No forecast — check the tablet's Wi-Fi. Culvery will try again.", then `enable` both.
2. **An event:** "Created by", "Added from a phone", "Change", Delete's confirm and its "Keep".
3. **Add and change an event** (ask first): "Add event" sheet and button, "Choose who it's for" when a Child can't, the locked dates line on a multi-day event ("change dates in Google Calendar on your phone"), "Save changes", "Changes saved", and the 10-minute hold: type a title, leave it, read "This closes in 20 seconds. Touch the screen to keep your changes." at 9 minutes 40 seconds (only if the user wants to wait).
4. **The PIN pad:** "Enter your PIN to …" reasons, "Wrong PIN — try again", the lockout's words.
5. **Settings:** People (rows "Adult · No PIN — can't add events", the sheet's "Without a PIN, Sam can only look."), Home location ("Used for the clock's time zone and the weather."; type a town to see "Searching…"; with the user's yes to change the location, choose one and change it back), Calendars ("Main", "Use for new events", "Choose who each calendar is for…"), Tablet lock (the line, the how-to line, "Use other apps" and its toast "You can use other apps now. Culvery locks again when it next opens."; in the release build this unpins; the tablet pins again when Culvery next comes to the front).
6. **The Settings warning** without TalkBack: "Settings closes in 20 seconds. Touch the screen to stay."

Ask: "Do the words read right? Anything to change before 4d-2 is done?"
````

- [ ] **Step 4: Check every baseline is current, and list what changed**

```bash
./gradlew recordRoborazziDebug
git status --short -- "*.png"
```
Expected: no output (every task left its baselines current). Any changed image: stop and report; don't commit it.
```bash
git diff --name-status eb40ec0 -- "*.png" > "$TMP/culvery-4d2-baselines.txt"
wc -l "$TMP/culvery-4d2-baselines.txt"
grep "^A" "$TMP/culvery-4d2-baselines.txt"
```
Expected added (`A`): `location_searching_dark`, `settings_warning_dark`, `sheet_warning_dark`, `settings_calendars_empty_dark`, `week_stale_light` (ruling 18), and nothing else. Any `D` (deleted) line: stop and report. Keep the list for Step 8.

- [ ] **Step 5: Run the gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.
```bash
git add README.md docs/setup/release.md docs/superpowers/plans/2026-09-23-plan1-followups.md docs/superpowers/plans/2026-10-09-4d2-walkthrough.md
git commit -m "Describe Tablet lock, the timeouts and TalkBack in the README, close what 4d-2 took from the follow-ups, and add the TalkBack and wording walk-through"
```

- [ ] **Step 6: Stop and report**

Report Steps 1–5 with the baseline list. The implementer does not go on.

- [ ] **Step 7: The controller runs Part A (TalkBack)**

Follow `docs/superpowers/plans/2026-10-09-4d2-walkthrough.md` Part A. Fill in A.5, with placeholders for the household's names and titles. Each fix: a failing test first, the module's tests, the gate, a commit with a message describing the fix. Restore TalkBack's settings (A.6's first part) before Step 8, even if a path failed; keep the logs until Step 9's check.

- [ ] **Step 8: USER CHECKPOINT — Part B and the baselines**

Walk the user through Part B. Send the user these images, light and dark where both exist: `button_matrix_*` and the close button (core/ui); `settings_people_*`, `person_new_*`, `settings_kiosk_*`, `location_searching_dark`, `location_failed_dark` (core/setup); `week_*`, `week_stale_*`, `detail_editable_*`, `editor_empty_*`, `editor_locked_*`, `review_ok_*`, `settings_calendars_empty_dark`, `today_empty_dark`, `coming_up_busy_dark` (capability/calendar); `forecast_no_location_*`, `forecast_failed_*` (capability/weather); `settings_warning_dark`, `sheet_warning_dark`, `pin_pad_*`, `home_weather_*` (app); and the Step 4 list of every baseline changed since eb40ec0, grouped with one line on why each group changed:
- Task 2: the quiet fill on Close, the week's and the date picker's ‹ ›, and the time steppers.
- Task 5: the person sheet's, the You step's and the location pane's message lines keep one line while empty, so TalkBack hears a message appear.
- Task 7: the disabled Move up at the top of People and Move down at the bottom; the PIN pad's message line.
- Tasks 10 and 11: the two warning pills (new).
- Task 12: the vocabulary (Main, Use for new events, Tablet lock, Use other apps, Change, Choose who it's for, can't change here, Updated … ago) and curly apostrophes and quotes.
- Task 14: errors with their cause; full words ("minutes", "hours") and full stops.
- Task 15: "Searching…" and "Try again"; "Add home location"; the location fixture's Next.
- Task 16: "Out of date" and its icon; People's "No PIN — can't add events" and "can only look"; "Change PIN"; Tablet lock's line and how-to; the smaller copy and empty states.
Name this plan's own choices the spec doesn't give (all provisional, for the end-of-v1 walk-through): rulings 8, 9, 11, 18, 20, 21, 22, the always-present message lines (Task 5), the refused-PIN line "That PIN can’t do this. Ask an Admin." (decision A), and where the warnings and the how-to line sit.

Ask: "Do these match what you want? Anything to change before 4d-2 is done?"
- **If the user asks for changes:** make them, re-record only the affected images with `--tests`, open them, run `./gradlew testDebugUnitTest verifyRoborazziDebug`, re-send them, and commit with a message describing the change. Repeat until approved.
- **When approved:** go to Step 9.

- [ ] **Step 9: Record what the user deferred, and the audit folder**

Add under "## From Plan 4d-2 (deferred)" any item from Steps 7–8 that was deferred rather than fixed.
Check the transcript names nothing of the household's: write every person's name, event title, town and account address the logs hold, one per line, to `$TMP/culvery-names.txt`, then
```bash
grep -n -i -F -f "$TMP/culvery-names.txt" docs/superpowers/plans/2026-10-09-4d2-walkthrough.md
```
Expected: no output. Replace each hit with its placeholder and run it again. Then run A.6's last block (it deletes the logs, the screenshots and the list) before the commit below. The spec says the audit folder `.superpowers/audit-4d/` may go once 4d-2 is done; it isn't in git, so deleting it can't be undone. Ask the user: "The 4d audits in `.superpowers/audit-4d/` aren't committed. Everything 4d-2 needed from them is in its spec's appendices. Delete the folder?" Delete it only on a clear yes, and only that folder.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md docs/superpowers/plans/2026-10-09-4d2-walkthrough.md
git commit -m "Record the 4d-2 TalkBack transcript and what its walk-through left for later"
```

---

## Spec coverage (4d-2 design → tasks)

| Design | Where |
|---|---|
| §1 goal, §2 scope (D15–D41, the 4d-1 follow-ups for 4d-2) | Tasks 1–17 |
| Decision 1 (scope), §2 look changes | Global Constraints' "no look change" line (with the message lines, Task 5); ruling 18 |
| Decision 2 (vocabulary), Appendix A, §7.1 | Tasks 12, 14–16 (ruling 2) |
| Decisions 3–6 (timeouts), amendment (sheet warning) | Tasks 9–11 (rulings 4, 5, 20, 22); Global Constraints' timeouts |
| Decision 7, §8.6 TalkBack on the emulator, the wording walk-through | Task 17 Steps 3, 7, 8 (ruling 19); the field-name check before Task 5 (Task 5 Step 0) |
| Decision 8, §7.1.3, §8.4 copy-scan | Task 13 (ruling 15) |
| Decision 9, §6.1 one activity signal | Task 9 (rulings 3, 6) |
| Decision 10, §4.4 live regions (D18) | Task 5; Tasks 7, 8, 10, 11 for their own regions (ruling 8) |
| Decisions 11 (full stops), 12 (service names) | Task 14; Task 12 (ruling 17) |
| §4.1 roles and click labels (D15), the 4d-1 follow-ups | Tasks 1, 2 (rulings 1, 7, 9, 10) |
| §4.2 tabs (D16), §4.5 headings (D19) | Task 3 |
| §4.3 modal overlays, pane titles, silent scrims (D17) | Task 4 (rulings 7, 11, 14); scrims in Task 2 |
| §5.1 field names (D20) | Task 5 (after Step 0's emulator check) |
| §5.2 swatches (D21), §5.3 review rows (D22), §5.4 choice chips (D23) | Task 6 (ruling 9) |
| §5.5 pickers and PIN pad (D24, D25), §5.7 wizard dots (D27), §5.8 Move (D28) | Task 7 (ruling 8) |
| §5.6 week and Home (D26), the Forecast test follow-up | Task 8 (ruling 21) |
| §6.2 Settings (D29) | Task 10 (rulings 5, 22; Review Focus 2); the edits' hold in Task 11 |
| §6.3 overlays left open (D32) and the amendment | Task 11 (rulings 4, 20; Review Focus 2, 4) |
| §6.4 the week's return (D30, T14) | Task 9 (Review Focus 3) |
| §6.5 toast durations (D31) | Task 9 (Review Focus 1) |
| §7.1.2 the PIN pad refusal (C64, amended: decision A) | Task 10 |
| §7.2 errors by cause (D34), §7.3 full words and stops (D35) | Task 14 |
| §7.4 people and PINs (D36) | Task 16 |
| §7.5 "Out of date" (D37) and the emulator health read | Task 16 Steps 1, 8 |
| §7.6 dead ends (D38) | Task 15 |
| §7.7 location search (D40) | Task 15 (Review Focus 5) |
| §7.8 Tablet lock (D41) | Tasks 12 (toasts), 16 (line, how-to, exit toast) |
| §7.9 smaller copy (D39) | Task 16 (ruling 16) |
| §8.1–§8.3 tests, the helper, `enableAccessibilityChecks()`, fake clocks | Task 1 (rulings 12, 13); Global Constraints' test rules |
| §8.5 baselines | Each task's compare step and the baseline check; ruling 18; Task 17 Step 4 |
| §8.7 gate | Global Constraints; every task's last steps |
| §9 plan shape | The task table; the three splits and the deviations in rulings 1–3 |
| §10 risks | Review Focus, second list |
