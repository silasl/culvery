# Culvery — Plan 4d-1: Shared parts, contrast and layout bugs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks marked **Review: opus** get an opus reviewer.

**Goal:** One shared button, list row and section label in `:core:ui`; every text and meaningful icon passing WCAG contrast in both themes, with whose event it is shown by name as well as colour; and the clock box, header width, weather failure, detail sheet, PIN pad and fixed sizes fixed for larger font scales.

**Architecture:** Components first (`HhButton`, then its callers in two passes, then `HhListRow` and `HhSectionLabel`), so every later task draws with them. Then tokens, with one `ContrastTest` in `:core:ui` that reads the colours and alphas the code draws and grows with each task that adds a pair. Then the layout and state bugs, each with a Robolectric test at font scale 1.3 or 1.5 through a `WithFontScale` helper that scales as the platform does. Baselines are compared, re-recorded and looked at in the task that changes them.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00: ui/foundation 1.9.1, material3 1.3.2), Hilt 2.57.1, Coroutines 1.10.2, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. No new dependency; `:core:ui` gains the Roborazzi plugin the other UI modules already apply.

**Spec:** `docs/superpowers/specs/2026-10-06-culvery-4d1-shared-parts-contrast-design.md` (approved; decisions 1–10, triage items D1–D7 and D9–D14, audit numbers in its Appendix A). Parent spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md`.
**Previous plan (format, constraints):** `docs/superpowers/plans/2026-10-02-culvery-04c-release-fixes.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (its "From the 4d triage" section; Task 11 updates it).

**Plan series:** 1 Foundation · 2a · 2b-1 · 2b-2 · 3a · 4a · 4b · 4c (done) · **4d-1 Shared parts, contrast, layout bugs (this plan)** · 4d-2 TalkBack, timeouts, words · 4e the device · the end-of-v1 design walk-through.

**Builds on:** `main` at 84560bd (the spec's commit on top of b7cb40d).

| # | Task | Size | Review |
|---|---|---|---|
| 1 | `HhButton` (with `on` required), `buttonColors`, `quietFill`, `WithFontScale`, the button matrix, `ContrastTest` begins (§4.1) | M | **opus** (the shared API every task uses) |
| 2 | `HhPillButton`, `HhSheetButton` and the confirm card move to `HhButton` (§4.1, Decision 8) | L | sonnet |
| 3 | The calendar's own buttons move to `HhButton`; 44 and 40 dp targets become 48 (§4.1) | L | sonnet |
| 4 | `HhListRow` and `HhSectionLabel`; people, towns, connections, calendars and both sheets' labels (§4.2, §4.3) | M | sonnet |
| 5 | Tokens: light `accent`, `weather`, chip secondary text, `PersonChipInk`; `ContrastTest`'s token pairs (§5.2, §5.3, §5.5) | M | **opus** (every light baseline) |
| 6 | Names on week chips and Coming up rows; faint affordances (add hint, past days at 62 %, wizard dots); the chosen Settings page in bold (§5.1, §5.2, §5.4) | M | sonnet |
| 7 | The clock box and the header's width budget (§6.1, §6.2) | M | **opus** (Home's layout) |
| 8 | The weather's failed state; a place never prints its coordinates (§6.3) | M | **opus** (state across the loop and the view) |
| 9 | The detail sheet scrolls; the PIN pad at 1.5 (§6.4, §6.5) | M | sonnet |
| 10 | Fixed sizes become minimums; the status bar; the `*_fs15` baselines (§6.6) | M | sonnet |
| 11 | `release.md`, README, follow-ups; every changed baseline shown; the emulator walk-through (§6.7, §7) | M | sonnet + controller |

**Task order and why:** §8's order, with three changes the code argues for (rulings 1–3): §8's Task 2 is split in two (Tasks 2 and 3), §8's Tasks 5 and 6 are one task (6), whose alphas move into `:core:ui` there, and `ContrastTest` starts in Task 1 with the button pairs. Every task ends green, with its own baselines compared, re-recorded and looked at (ruling 4).

**Plan review (2026-10-06).** Three reviews (buildability B1–B9, security and state P1–P6, tests and simplicity T1–T12, S1–S8, in `.superpowers/plan-review-4d1/`); the user ruled "all as recommended". Applied here: `on` is required (T1, ruling 25); `buttonColors` is the one tone-to-colour rule and `PersonChipInk` moves to `:core:ui` (T2, ruling 26); every re-record is preceded by Roborazzi's compare and its diffs are looked at (T3, ruling 33); the weather's failure is a `StateFlow<WeatherPlace?>` (S1, ruling 27) and a place prints no coordinates (P4, ruling 28); the former Tasks 6 and 7 (§8's 5 and 6) are merged into Task 6 (S6); the walk-through never clears data without asking (P1, ruling 32); the confirm card quotes at most three lines of a title (P2, ruling 29); Reconnect and Connect stop while busy (P6, ruling 30); `release.md` and the README say who can reach the unpinned moments (P5, ruling 31); a tap beside the PIN pad's overflowing Cancel is pinned (P3); the person sheet's PIN pad and the Task 10 `fs15` images are re-recorded where they change (B1, B2); the Cancel test pins `softWrap`/`maxLines` (B3); the greps, build script edit, `--continue` runs, imports and test widths are fixed (B4–B7, B9); the duplicate contrast checks go and the dots are checked by pixel (T4); the keypad, header, week-title, chip-name, confirm-height, +-role and temperature tests are tightened or added (T5–T10, T12); `ContrastTest` is plain JVM, written once and appended to (S2, S3); the list-row test tags go (S4); the screenshot helpers always wrap in `WithFontScale` (S5); `isLight` is internal and `HhAlpha`'s tint values private (S7). The user also accepted rulings 4 and 8 as spec deviations; the spec's §7, Decision 10 and §6.6 are amended to match.

## Rulings against the code

Where the spec is silent, ambiguous, or doesn't fit the code, this plan rules as follows.

1. **§8's Task 2 is two tasks (deviation).** Moving every caller in one task touches about 25 files in five modules, and a reviewer could approve the shared-component half and reject the calendar half. Task 2 moves the `:core:ui` buttons (`HhPillButton`, `HhSheetButton`, `HhConfirmCard`'s buttons) and all their callers; Task 3 moves the calendar's private ones (`PrimaryButton`, `DeleteButton`, `AddButton`, `HeaderChip`, `PickerButton`, Assign to…) and raises the 44 and 40 dp targets. Each deletes what it migrates and runs the whole gate. §8's Tasks 5 and 6 (names; faint affordances and the chosen page) are merged into one, Task 6 here, at the plan review (S6): both edit `HhAlpha`, `ContrastTest` and `WeekView` and re-record `week_*`. The plan has 11 tasks.
2. **The alphas move with their use (deviation from §8 Task 4).** The chip tint alphas and `PAST_DAY_ALPHA` move to `:core:ui` in Task 6, where the chip starts drawing `mute` on the tints and past days become 0.62. Moved earlier, `ContrastTest` would have to assert pairs the code doesn't draw yet (past days at 0.3 fail at 1.91).
3. **`ContrastTest` starts in Task 1, in its final shape.** The quiet-fill rule and `buttonColors` are Task 1's code, so their checks are Task 1's failing tests. Tasks 5 and 6 append their pairs to the same class; nothing rewrites it. It is a plain JVM test (Compose's `Color` is pure Kotlin).
4. **Baselines are re-recorded per task, not once at the end (a spec deviation the user accepted; §7 and Decision 10 are amended).** Every task ends with the full gate green, and the reviewer of each task sees that task's image changes beside its code. The spec's risk (a re-record hides regressions) is met by each task naming the images it expects to change and what each must show, a stop on any other change, Roborazzi's compare before every re-record (ruling 33), and Task 11 showing the user every baseline changed since 84560bd with one line on why. 4d-2 still starts from a fresh set.
5. **Sheets are `bg`, so sheet buttons pass `on = ButtonSurface.Bg` (spec §4.1 says "Surf (sheets…)").** `HhSheet` draws `c.bg` (`core/ui` Shell.kt:89). Decision 9's rule is "one step above the surface it sits on", so the detail, editor and person sheets' quiet and disabled buttons use `Bg` (light `surf`, dark `surf2`): the light Delete becomes white on `bg` instead of `surf2` at 1.04:1. Dark is unchanged. Cards, rows, pickers and the Settings column are `surf` and pass `Surf`. `on` has no default (ruling 25), so each call site in Tasks 2 and 3 names its surface.
6. **Light `danger` on `dangerSoft` is 4.497:1, not 4.50.** Nothing draws `danger` *text* on `dangerSoft`; it is the icon on the reconnect chip, the failure card and the confirm card. `ContrastTest` holds it at ≥ 3:1 as an icon and pins it at ≥ 4.49 so a change to either token shows. A later task that wants `danger` text there needs a darker `danger`; this is noted in the follow-ups (Task 11).
7. **Touch size is asserted on layout height.** In Compose 1.9.1 `SemanticsNode.touchBoundsInRoot` expands every node with an `OnClick` action to the 48 dp minimum touch target (checked in `ui-release.aar`: `SemanticsModifierNodeKt.getUseMinimumTouchTarget` is "has OnClick"; `NodeCoordinator.touchBoundsInRoot` pads by `minimumTouchTargetSize`), so `assertTouchHeightIsAtLeast(48.dp)` passes for any clickable and can't fail. The button tests assert `assertHeightIsEqualTo(48.dp)`/`(60.dp)`.
8. **The status bar gets no `minimumInteractiveComponentSize()` (a spec deviation the user accepted; §6.6 is amended).** That modifier reserves 48 dp of *layout*, so the 30 dp bar would become 48 and push Home down 18 dp, undoing most of Task 7's 21 dp. Compose's hit testing already takes a touch up to 48 dp around a smaller clickable (the same minimum touch target, `hitNear`), so Sign out and the theme toggle already have 48 dp touch. Task 10 pins that with a real touch 8 dp below Sign out's text, keeps the bar 30 dp tall at 1.0 (a test fails if anything grows it), and makes it `heightIn(min = 30.dp)` so it grows only when its text does.
9. **The week header's reconnect chip stays its own composable.** No `ButtonTone` draws `dangerSoft` with a `danger` icon and an ellipsised label. It becomes 48 dp through `CalendarDimens.touchTarget`, radius 24, and gains `Role.Button` (Task 3).
10. **Assign to… becomes `HhButton` (Primary, Regular)** rather than only growing to 48 dp: it was already an accent pill with a 15 sp / 700 label. Its padding goes from 18 to 20 and its radius from 22 to 24; `assignButtonHeight/Radius/PaddingH` go.
11. **`HhConfirmCard` loses `keepTone`.** Keep is always `Quiet` on `DangerSoft` (`surf` in both themes, the spec's DangerSoft row), so the person sheet's Keep goes from `surf2` to `surf`; the confirm button is `Destroy` on `DangerSoft`.
12. **`DeleteButton` stays as a one-line wrapper** in calendar ui/Components.kt: two sheets use it (§4.1 allows it).
13. **`HhButton` is `heightIn(min = …)`, not a fixed height, with a one-line, end-ellipsised label.** A label never wraps or clips; at a large font scale a button grows rather than cutting its text (§6.6's rule applied to the new part).
14. **`HhListRow` keeps the spec's signature;** a calendar row's Master line, read problem and person picker go in `below`, so the Master line moves from under the name (beside the chip and switch) to under the whole top line. Trailing items are 12 apart, so People's move buttons and chevron sit 12 apart instead of 16. Both are walk-through material (W31, W27).
15. **The PIN error line is `minLines = 2, maxLines = 2`** rather than a computed `heightIn`: Compose reserves two lines of `PinPadType.error` at the current font scale itself.
16. **The PIN Cancel key draws without a clip:** the circle clip goes, the ripple is `ripple(bounded = false, radius = 38.dp)`, and the label is `softWrap = false` with `wrapContentWidth(unbounded = true)`, so at 1.5 it overflows the 76 dp key instead of wrapping. Its tap area becomes the key's 76 dp square; a test pins that a tap at the edge of the 0 key beside it still types 0 (P3).
17. **Font scale in tests comes from `WithFontScale`,** a `:core:ui` composable that provides `Density(context)` for a configuration context at the given scale. `RuntimeEnvironment.setFontScale` (spec §7) reaches only activities started after it, and the compose rule's activity starts before the test body. `Density(context)` uses the platform's `FontScaleConverterFactory`, so SDK 35's non-linear scaling still applies (the clock is still capped, as §6.6 says).
18. **The weather's `Failed` is per place.** `WeatherSyncLoop.lastFailure` holds the place the last finished fetch failed for; `weatherView` is `Failed` only when that is the current location's place. A town chosen after a failure shows "Getting the forecast…" until its own first fetch finishes (Review Focus 2).
19. **`forecast_waiting_*` baselines already exist;** only `forecast_failed_*` is new (§6.3 says neither exists).
20. **Forecast temperatures are tested as their own part.** `ForecastRow` clears its children's semantics for TalkBack (4b), so no finder reaches the texts inside a row; the high and low become an `internal TemperaturePair`, which `TemperatureFontScaleTest` measures at 1.5, and the `forecast_fs15` baseline (with "-12°" lows) shows the whole card.
21. **The editor's section labels look the same after Task 4** (`CalendarType.sectionLabel` was already 13 sp / 700 with 0.5 tracking, typed in capitals); the person sheet's become capitals with 0.5 tracking.
22. **The walk-through goes offline with `svc wifi disable` / `svc data disable`, never airplane mode** (§7 says airplane mode; Play services crash-loops on the API 35 image after a network change, `docs/setup/google-calendar.md` §5, as 4c ruled).
23. **Appendix A's dark past-cell figure (5.80) is the 60 % value;** at 62 % it is 6.09. Light at 62 % is 4.83, as the spec says.
24. **Copy.** The one new string is "No forecast yet — check the tablet’s Wi-Fi." (curly ’). Strings a task only moves to a new component keep their text; the straight-apostrophe sweep is 4d-2's D33.
25. **`HhButton`'s `on` has no default (plan review T1; deviation from the spec's signature, which had `on: ButtonSurface = ButtonSurface.Surf`).** It stays the last parameter, so callers name it (`on = ButtonSurface.Bg`); a button left on `bg` with a `surf` fill (spec §9's named risk) can't compile.
26. **One tone-to-colour rule (T2).** `internal fun buttonColors(tone, enabled, on, colors): ButtonColors` returns the fill and label colour; `HhButton` draws it, `ButtonsTest` compares the drawn label with it, and `ContrastTest` checks every tone × state × surface through it. `PersonChipInk` (the label on a chip filled with a person's colour) moves from the calendar's `EventEditorSheet.kt` into `:core:ui` beside `PersonPalette`, and `ContrastTest` checks it on every palette colour and Family (Task 5).
27. **The weather's failure is a `StateFlow<WeatherPlace?>` (S1; spec §6.3 asks for "a `StateFlow` of succeeded or failed, starting as none yet").** `lastFailure` is the place the last finished fetch failed for, set on failure and cleared on success; null before any fetch. It keeps the per-place meaning of ruling 18 with no field nothing reads.
28. **A `WeatherPlace` prints no coordinates or zone (P4).** It overrides `toString()` to `"WeatherPlace(withheld)"`, so a later log line can't break the release logs' promise (`docs/setup/release.md`); `equals` is unchanged. A test checks a place and a `StoredWeather` print neither.
29. **The confirm card quotes at most three lines of the title (P2).** A title synced from a phone can be hundreds of characters; past three lines the pinned card would push Keep event and Delete event off the sheet. A test uses a thousand-character title.
30. **Reconnect and the Settings Connect buttons are disabled while busy (P6),** as Disconnect beside them already is.
31. **Who reaches the unpinned moments (P5).** `release.md` and the README say Choose home app needs an Admin signed in (no fresh PIN within the 2-minute session), Connect and Reconnect a fresh Admin PIN, and that a Home or Overview press leaves Culvery unpinned, so an Admin should sign out when done.
32. **The walk-through never clears data without asking (P1).** The wizard is checked from its baselines; clearing Culvery's data to see it live is an opt-in step that says exactly what it deletes and runs only on the user's yes. Items that add an event or change the home location ask first.
33. **Each re-record is preceded by Roborazzi's compare (T3).** `./gradlew <module>:compareRoborazziDebug --tests …` writes a `*_compare.png` (reference, new image and their difference) for each changed image; the implementer opens those before recording. In Task 5 every difference must be recoloured pixels only.

Nothing in this plan needs a deprecated API. Flagged and avoided: `rememberRipple` (use material3's `ripple`), `androidx.compose.ui.platform.LocalLifecycleOwner`, `assertTouchHeightIsAtLeast` as a check of anything (ruling 7).

## Global Constraints

- Package root `uk.co.siland.culvery`. Module rules unchanged (`build-logic`'s `ModuleBoundaries`). `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Pinned versions as in `gradle/libs.versions.toml`. **No new dependency.** Never change a version.
- **Deprecated APIs:** use none without asking. If the compiler shows a deprecation warning on a line you wrote, **stop and ask**.
- **Layout numbers are named, never inline**, in the module's tokens or dimens object (`ButtonTokens`, `ListRowTokens`, `CalendarDimens`, `SetupDimens`, `WeatherDimens`, `PinPadDimens`) or as a named `private val` beside the composable, as each file already does. Tests may use literal dp.
- **`HhButton` always names its surface:** `on = ButtonSurface.Bg` on a page, the wizard or a sheet; `.Surf` on a card, row, picker or the Settings column; `.DangerSoft` in a confirm card (ruling 25).
- **Values from the spec, exactly:**
  - Light `accent` = `#23704F`; white `accentInk` on it; `accentSoft` stays `#D3EDE1`. Dark tokens unchanged.
  - `HhColors.weather`: light `#8A6400`, dark `#E0B85B`; `SunAmber` goes; the header weather icon and the Forecast icons use it.
  - A selected chip's secondary text at full alpha (not 72 %).
  - `HhButton` Regular: 48 dp tall, radius 24, 15 sp / 700 label, padding 0 20, or 0 20 0 14 with a 24 dp icon 6 from the label. Large: 60 dp, radius 30, 17 sp / 700, padding 0 26, a 24 dp icon 8 from the label.
  - Tones: `Primary` `accent`/`accentInk`; `Quiet` quiet fill/`ink`; `Danger` quiet fill/`danger`; `Destroy` `danger`/`dangerInk`. Disabled, every tone: the quiet fill for its surface and a `mute` label and icon; does nothing.
  - Quiet fill: on `Bg` light `surf`, dark `surf2`; on `Surf` `surf2`; on `DangerSoft` `surf`. Each ≥ 1.1:1 against its surface.
  - `CalendarDimens.touchTarget` 44 → 48 (Today's +, the reconnect chip); the week column's add hint `addHintMinHeight` 40 → 48; Assign to… 48 (ruling 10).
  - `HhListRow`: `surf`, radius 18, padding 16 × 20; leading 16 from the text; title `HhType.rowTitle` (17 sp / 600, one line, ellipsis); subtitle `HhType.secondary` (14 sp / 400) 2 below; trailing items 12 apart; a row with `onClick` is `clickable(role = Role.Button)`; lists keep their 10 dp gap.
  - `HhSectionLabel`: `HhType.sectionLabel` (13 sp / 700, 0.5 tracking), draws `text.uppercase(Locale.UK)`, `semantics { heading(); contentDescription = text }`; callers pass "Who", "Day", "Time", "Length", "Name", "Colour", "Role", "PIN".
  - Contrast: normal text ≥ 4.5:1; large text (≥ 18.66 sp bold or 24 sp), icons and other non-text parts ≥ 3:1; WCAG 2.x relative luminance, alpha blended over the surface the code draws on, 8 bits a channel.
  - Week chip tints `0x2E` (dark) and `0x26` (light) of the person's colour over `surf`. Past date cells at **62 %** (0.62). The add hint at full `mute`. Wizard dots: the current step a filled `accent` dot, the others 1.5 dp `mute` outlines.
  - Week chip first line: `"${event.startLabel} · ${event.person.name}"` ("09:30 · Sam", "All day · Sam"), `mute`, `CalendarType.chipTime` (12 sp / 700), one line, end ellipsis. Coming up row: `"${event.timeLabel} · ${event.person.name}"`, `mute`, one line, end ellipsis.
  - The chosen Settings page's label W700 (others W600).
  - `HhType.clock` keeps `lineHeight = 93.6.sp`; the ~94 dp box comes from `Modifier.lineBox(height)` in core/ui `LineBox.kt` (Compose won't shrink one line below the font's ascent plus descent, so `Trim.None` alone doesn't; amended in Task 7); `home_clock` 94 dp ± 1 tall. Header items measured with `maxWidth = constraints.maxWidth − max(clock.width, date.width) − 28.dp`.
  - Weather `Failed` copy: "No forecast yet — check the tablet’s Wi-Fi."
  - Status bar `heightIn(min = 30.dp)`; Forecast rows `heightIn(min = 44.dp)`, temperatures `widthIn(min = 44.dp)`, one line, no soft wrap, 8 dp between high and low; the detail label column `widthIn(min = 104.dp)`, up to two lines; the week title, `HhChoiceChip`'s label and the Review health line end-ellipsised; `HhChoiceChip` `heightIn(min = 48.dp)`, up to two lines; the editor summary up to two lines.
- **Copy:** curly quotes and apostrophes (’ “ ”) in every string a task writes or edits; strings only moved keep their text (ruling 24). Spoken labels stay as they are (4d-2).
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test`, `check` or `build`. A task runs its module's tests while working; every task ends with the full gate before its commit.
- **Screenshots:** baselines in `<module>/src/test/screenshots/`, recorded and verified on Windows. Before re-recording, run `./gradlew <module>:compareRoborazziDebug --tests "<class pattern>"` for the classes the step names and open each `*_compare.png` it writes under `<module>/build/outputs/roborazzi/` (reference, new image and their difference side by side; ruling 33). Then re-record with `./gradlew <module>:recordRoborazziDebug --tests "<class pattern>"`; `git status --short -- "*.png"` lists what changed. **Look at every difference, and open every new image (the Read tool shows it), before committing**, and check it against the step's list of what it must show. An image that changes and isn't on the step's list, or that shows anything else moving, is a **stop and report**: don't commit it. `@GraphicsMode(GraphicsMode.Mode.NATIVE)` only on classes that capture screenshots or measure text.
- **Text layout in tests:** each module that measures text has a test-only `TextLayouts.kt` with `SemanticsNodeInteraction.textLayout()` (the same five lines; test sources aren't shared between modules). It reads `SemanticsActions.GetTextLayoutResult` from the node's config, so find text nodes with `useUnmergedTree = true` when a parent merges them.
- **Comments say what the code can't**, one line by default. No comment names this plan's task numbers.
- **Write every file as UTF-8** (copy contains "—", "›", "·", "…", "’", "°"). Use the Write/Edit tools, or `encoding="utf-8"` in Python.
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on the current branch; never push. **Never** `git checkout -- .` or `git restore .`; to undo your own change, restore only the files you changed, after `git diff --name-only`.

## Review Focus

The five inputs most likely to bite a household that the spec's own tests don't reach, each pinned by a test in its owning task:

1. **A long name on a week chip in a narrow column at a large font scale.** Expected: the chip's first line stays one line, the name is cut (with "…") before the time is, and the badge after it keeps its place.
   - Task 6 `FontScaleTest.atFontScale15ALongNameIsCutBeforeTheTimeAndTheBadgeStays` (capability/calendar).
2. **A new home town chosen just after a failed fetch.** Expected: the new town shows "Getting the forecast…" until its own first fetch finishes, not the old failure's "No forecast yet"; the same town renamed keeps the failure.
   - Task 8 `WeatherViewTest.aFailureForAnotherPlaceIsStillWaiting`, `aFailureForTheSameTownRenamedIsStillFailed`.
3. **Deleting an event with a long title at font scale 1.5.** Expected: the confirm card's Keep event and Delete event stay on screen with the title and info rows scrolled above them.
   - Task 9 `FontScaleTest.atFontScale15TheDeleteConfirmationsButtonsStayOnScreen`, `atFontScale15AThousandCharacterTitleStillLeavesTheConfirmationsButtons` (capability/calendar).
4. **A connection with a long Google account name that needs reconnecting, at font scale 1.5.** Expected: the account line is cut with "…" on one line, and Reconnect and Disconnect stay whole beside it.
   - Task 4 `FontScaleTest.atFontScale15ALongAccountKeepsReconnectAndDisconnectWhole` (capability/calendar).
5. **A button label longer than its button** (two Large buttons sharing a footer at 1.5). Expected: the label stays one line with "…", and the button keeps its 60 dp.
   - Task 1 `FontScaleTest.atFontScale15ALabelLongerThanItsButtonStaysOneLine` (core/ui).

The spec's own risks (§9), each pinned:
- Baseline churn: rulings 4, 33; each task's compare and re-record step; Task 11 Step 4.
- `HhButton` migration breadth (tags, heights): Tasks 2 and 3 keep every tag and run the whole gate before deleting the old functions; their new tests find each button by its old tag.
- The quiet fill's `on` passed wrongly: `on` has no default, so every caller names it (ruling 25); the button matrix baselines (Task 1) show each fill (ruling 5).
- Contrast edges: `ContrastTest.theEdgesStayWhereTheyAre`, `theTintEdgesStayWhereTheyAre`, `aPastDayReadsAtItsAlpha` (Tasks 5, 6).
- Compose's `LineHeightStyle` changing: `ShellLayoutTest.theClockBoxIsItsLineHeight` (Task 7).

---

## File Structure

```
core/ui/build.gradle.kts                                  (Task 1: Roborazzi)
core/ui/src/main/java/uk/co/siland/culvery/core/ui/
  Buttons.kt          (create, Task 1: HhButton, ButtonSize, ButtonSurface, ButtonTokens, quietFill, buttonColors; Task 2: ButtonTone moves here)
  FontScale.kt        (create, Task 1: WithFontScale)
  Colors.kt           (Task 1: isLight; Task 5: accent, weather, SunAmber out; Task 6: HhAlpha)
  PersonPalette.kt    (Task 5: PersonChipInk)
  Components.kt       (Task 2: HhPillButton out; Task 4: HhListRow, HhSectionLabel, ListRowTokens)
  Controls.kt         (Task 2: HhSheetButton and Plain out, HhConfirmCard; Task 5: secondary alpha; Task 10: chip minimum)
  Theme.kt (Task 5), Type.kt (Task 4: sectionLabel; Task 7: the clock)
core/ui/src/test/resources/robolectric.properties         (create, Task 1)
core/ui/src/test/java/uk/co/siland/culvery/core/ui/
  ButtonsTest.kt, ButtonScreenshotTest.kt, FontScaleTest.kt, ContrastTest.kt, Contrast.kt, TextLayouts.kt (create, Task 1)
  ListRowAndLabelTest.kt (create, Task 4); ControlsTest.kt (Tasks 2, 5); PersonPaletteTest.kt (Task 5)
core/ui/src/test/screenshots/button_matrix_{dark,light}.png  (new, Task 1)
core/setup/…/PeopleUi.kt, LocationPane.kt (Tasks 2, 4), PersonEditorSheet.kt (Tasks 2, 4), SettingsScreen.kt (Tasks 2, 6),
  SetupUi.kt, SetupWizard.kt (Tasks 2, 6), steps/WelcomeStep.kt, steps/YouStep.kt, pages/KioskPage.kt (Task 2),
  SetupDimens.kt (Tasks 4, 6)
core/setup/src/test/…/SetupButtonsTest.kt (create, Task 2), RowStylesTest.kt, TextLayouts.kt (create, Task 4), SettingsScreenTest.kt, WizardDotsTest.kt (Task 6)
core/access/…/ui/PinPad.kt (Task 9); core/access/src/test/…/ui/FontScaleTest.kt, TextLayouts.kt (create, Task 9)
capability/calendar/…/ui/Components.kt, CalendarType.kt, TodayCard.kt, WeekView.kt, EventDetailSheet.kt, EventEditorSheet.kt,
  Pickers.kt, CalendarConnectHost.kt, ReviewCalendars.kt, ConnectCalendarCard.kt, ComingUpCard.kt (Tasks 2–6, 9, 10)
capability/calendar/src/test/…/ui/TextLayouts.kt (create, Task 3), FontScaleTest.kt (create, Task 4; Tasks 6, 9, 10),
  CardsTest.kt, WeekViewTest.kt, EventDetailSheetTest.kt, PickersTest.kt, ReviewCalendarsTest.kt, SampleUi.kt, *ScreenshotTest.kt
capability/weather/…/WeatherView.kt, WeatherSyncLoop.kt, WeatherRepository.kt, WeatherWords.kt (Task 8),
  ui/ForecastCard.kt (Tasks 2, 5, 8, 10), ui/WeatherHeaderItem.kt (Tasks 5, 7), ui/WeatherDimens.kt (Task 10);
  src/test/…/ui/TemperatureFontScaleTest.kt (create, Task 10)
provider/calendar-fake/…/FakeCalendarProvider.kt (Task 2)
app/…/shell/ui/HomeScreen.kt (Task 7), StatusBar.kt (Task 10); app/src/test/…/shell/ui/ShellLayoutTest.kt, ShellScreenshotTest.kt,
  TextLayouts.kt (create, Task 7)
docs/setup/release.md, README.md, docs/superpowers/plans/2026-09-23-plan1-followups.md (Task 11)
docs/superpowers/specs/2026-10-06-culvery-4d1-shared-parts-contrast-design.md (§6.6, §7, Decision 10 amended at the plan review)
```

`…` stands for the module's package directory (`src/main/java/uk/co/siland/culvery/<module path>`); every step spells out the full path.

---
### Task 1: `HhButton`, `buttonColors`, `quietFill`, `WithFontScale`, the button matrix, `ContrastTest` begins (§4.1; rulings 3, 7, 13, 17, 25, 26)

**Review:** opus.

**Files:**
- Create: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Buttons.kt`, `core/ui/src/main/java/uk/co/siland/culvery/core/ui/FontScale.kt`
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt` (after `LightColors`, line 53), `core/ui/build.gradle.kts:3-11` (the plugins and dependencies blocks only)
- Create: `core/ui/src/test/resources/robolectric.properties`
- Test (create): `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ButtonsTest.kt`, `ButtonScreenshotTest.kt`, `FontScaleTest.kt`, `ContrastTest.kt`, `Contrast.kt`, `TextLayouts.kt`
- Baselines (new): `core/ui/src/test/screenshots/button_matrix_dark.png`, `button_matrix_light.png`

**Interfaces:**
- Consumes: `ButtonTone { Primary, Plain, Quiet, Danger, Destroy }` as it is in `Controls.kt:221` (Task 2 removes `Plain` and moves the enum to `Buttons.kt`); `ControlType.button` (17 sp / 700), `HhType.buttonLabel` (15 sp / 700), `HhIcon`, `Icons`.
- Produces (all in package `uk.co.siland.culvery.core.ui`):
  - `enum class ButtonSize { Regular, Large }`
  - `enum class ButtonSurface { Bg, Surf, DangerSoft }`
  - `object ButtonTokens { regularHeight, regularPaddingH, regularIconPaddingStart, regularIconGap, largeHeight, largePaddingH, largeIconGap, icon: Dp }`
  - `internal fun quietFill(on: ButtonSurface, colors: HhColors): Color`
  - `internal data class ButtonColors(val fill: Color, val ink: Color)` and `internal fun buttonColors(tone: ButtonTone, enabled: Boolean, on: ButtonSurface, colors: HhColors): ButtonColors` — the one place a tone becomes colours; `HhButton` draws it, `ButtonsTest` and `ContrastTest` read it.
  - `internal val HhColors.isLight: Boolean`
  - `@Composable fun HhButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tone: ButtonTone = ButtonTone.Quiet, size: ButtonSize = ButtonSize.Regular, icon: String? = null, enabled: Boolean = true, on: ButtonSurface)` — `on` has **no default** (ruling 25), so every caller names it: `on = ButtonSurface.Bg`, `.Surf` or `.DangerSoft`.
  - `@Composable fun WithFontScale(fontScale: Float, content: @Composable () -> Unit)`
  - Test-only (core/ui): `internal fun contrast(fg: Color, bg: Color): Double`, `internal fun Color.over(alpha: Float, surface: Color): Color`, `internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult`.

- [ ] **Step 1: Give `:core:ui` screenshots and the tablet's canvas**

`core/ui/build.gradle.kts`: edit the two blocks below only; keep line 1's `import org.gradle.api.tasks.PathSensitivity` and the `tasks.withType<Test>` block for `IconFontTest` (lines 13-18). The plugins block becomes:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    alias(libs.plugins.roborazzi)
}
```
and the dependencies block:
```kotlin
dependencies {
    // SingleAction takes a CoroutineScope in its public API.
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
```
Create `core/ui/src/test/resources/robolectric.properties`:
```
qualifiers=w1280dp-h800dp-land-hdpi
```

- [ ] **Step 2: Write the test helpers**

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/TextLayouts.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult

/** The layout Compose drew for this text node: its style, its lines and whether it overflowed. */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}
```
Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/Contrast.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt

/** WCAG 2.x contrast ratio of two opaque colours, 1 to 21. */
internal fun contrast(fg: Color, bg: Color): Double {
    val a = relativeLuminance(fg)
    val b = relativeLuminance(bg)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/** This colour at [alpha] over [surface], rounded to 8 bits a channel as the screen blends it. */
internal fun Color.over(alpha: Float, surface: Color): Color = Color(
    red = blend(red, surface.red, alpha),
    green = blend(green, surface.green, alpha),
    blue = blend(blue, surface.blue, alpha),
)

private fun blend(fg: Float, bg: Float, alpha: Float): Int = (fg * 255 * alpha + bg * 255 * (1 - alpha)).roundToInt()

private fun relativeLuminance(c: Color): Double = 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)

private fun linear(channel: Float): Double {
    val s = (channel * 255).roundToInt() / 255.0
    return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
}
```

- [ ] **Step 3: Write the failing tests**

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ContrastTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** WCAG 2.x normal text (4d-1 §5). Button labels are 15 and 17 sp bold, under the large-text size. */
private const val TEXT = 4.5

/** A quiet fill carries no WCAG ratio (the label names the button) but must not vanish into its surface (4d-1 Decision 9). */
private const val QUIET_FILL_MIN = 1.1

/**
 * Every text and meaningful non-text pair the screens draw, in both themes, with alphas blended as drawn. A ratio that
 * sits near its line is pinned at its value today, so a token change that lowers it fails here first.
 */
class ContrastTest {
    private val themes = listOf("dark" to DarkColors, "light" to LightColors)

    private fun surfaceOf(on: ButtonSurface, t: HhColors) = when (on) {
        ButtonSurface.Bg -> t.bg
        ButtonSurface.Surf -> t.surf
        ButtonSurface.DangerSoft -> t.dangerSoft
    }

    private fun check(what: String, fg: Color, bg: Color, min: Double) =
        assertWithMessage(what).that(contrast(fg, bg)).isAtLeast(min)

    @Test
    fun everyQuietFillStandsOffItsSurface() {
        themes.forEach { (name, t) ->
            ButtonSurface.entries.forEach { on -> check("$name quiet fill on $on", quietFill(on, t), surfaceOf(on, t), QUIET_FILL_MIN) }
        }
    }

    @Test
    fun everyButtonsLabelOnItsFill() {
        themes.forEach { (name, t) ->
            ButtonTone.entries.forEach { tone ->
                listOf(true, false).forEach { enabled ->
                    ButtonSurface.entries.forEach { on ->
                        val b = buttonColors(tone, enabled, on, t)
                        check("$name $tone ${if (enabled) "enabled" else "disabled"} on $on", b.ink, b.fill, TEXT)
                    }
                }
            }
        }
    }
}
```
A plain JVM test: Compose's `Color` and `luminance()` are pure Kotlin, as `PersonPaletteTest` beside it is. Tasks 5 and 6 append to this class; they don't rewrite it.
Fails if: `quietFill(Bg, light)` returns `surf2` (1.04:1, the S1 bug), any surface's fill is the surface itself, or `buttonColors` pairs a label with a fill under 4.5:1 for any tone, state or surface (it reads what `HhButton` draws, so a tone changed in `HhButton` is checked here).

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ButtonsTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ButtonsTest {
    @get:Rule val compose = createComposeRule()

    private val tones = listOf(ButtonTone.Primary, ButtonTone.Quiet, ButtonTone.Danger, ButtonTone.Destroy)
    private val enabledStates = listOf(true, false)

    private fun tag(tone: ButtonTone, size: ButtonSize, enabled: Boolean, on: ButtonSurface) = "${tone}_${size}_${enabled}_$on"

    @Test
    fun everyToneSizeAndSurfaceIsAButtonOfItsHeightAndADisabledOneIsMuteAndDoesNothing() {
        val taps = mutableListOf<String>()
        compose.setContent {
            CulveryTheme(dark = false) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tones.forEach { tone ->
                        ButtonSize.entries.forEach { size ->
                            enabledStates.forEach { enabled ->
                                ButtonSurface.entries.forEach { on ->
                                    val t = tag(tone, size, enabled, on)
                                    HhButton("Save", { taps += t }, Modifier.testTag(t), tone = tone, size = size, enabled = enabled, on = on)
                                }
                            }
                        }
                    }
                }
            }
        }
        tones.forEach { tone ->
            ButtonSize.entries.forEach { size ->
                enabledStates.forEach { enabled ->
                    ButtonSurface.entries.forEach { on ->
                        val t = tag(tone, size, enabled, on)
                        val node = compose.onNodeWithTag(t)
                            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                            .assertHeightIsEqualTo(if (size == ButtonSize.Large) 60.dp else 48.dp)
                        if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
                        node.performClick()
                        val label = compose.onNode(hasText("Save") and hasAnyAncestor(hasTestTag(t)), useUnmergedTree = true)
                            .textLayout().layoutInput.style.color
                        assertWithMessage("$t label").that(label).isEqualTo(buttonColors(tone, enabled, on, LightColors).ink)
                        if (!enabled) assertWithMessage("$t disabled label").that(label).isEqualTo(LightColors.mute)
                    }
                }
            }
        }
        assertWithMessage("taps").that(taps.toSet())
            .isEqualTo(taps.filter { it.contains("_true_") }.toSet())
        assertWithMessage("enabled buttons tapped").that(taps).hasSize(tones.size * ButtonSize.entries.size * ButtonSurface.entries.size)
    }
}
```
Fails if: the `Role.Button` goes, a size changes height (today's pills are about 46 dp), `HhButton` draws a label colour other than `buttonColors`' (so the two can't drift apart), a disabled button keeps its tone's colour (as `DeleteButton`'s red did), or a disabled button runs its click. The fill isn't sampled: `on` is a required parameter (ruling 25), so no caller can leave it at a wrong default, and the button matrix baselines show every fill.

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/FontScaleTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Real text metrics: these measure lines and ellipses. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleTest {
    @get:Rule val compose = createComposeRule()

    /** Two Large buttons sharing a narrow footer at 1.5: a long label mustn't wrap or shrink its button. */
    @Test
    fun atFontScale15ALabelLongerThanItsButtonStaysOneLine() {
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) {
                    Row(Modifier.width(280.dp)) {
                        HhButton(
                            "Delete", {}, Modifier.weight(1f),
                            tone = ButtonTone.Danger, size = ButtonSize.Large, icon = Icons.DELETE, on = ButtonSurface.Bg,
                        )
                        HhButton(
                            "Save changes", {}, Modifier.weight(1f).testTag("save"),
                            tone = ButtonTone.Primary, size = ButtonSize.Large, icon = Icons.CHECK, on = ButtonSurface.Bg,
                        )
                    }
                }
            }
        }
        val label = compose.onNode(hasText("Save changes") and hasAnyAncestor(hasTestTag("save")), useUnmergedTree = true).textLayout()
        assertThat(label.layoutInput.density.fontScale).isEqualTo(1.5f)
        assertThat(label.lineCount).isEqualTo(1)
        assertThat(label.isLineEllipsized(0)).isTrue()
        compose.onNodeWithTag("save").assertHeightIsEqualTo(60.dp)
    }
}
```
Fails if: `WithFontScale` doesn't reach the density (font scale 1.0), the label loses `maxLines = 1` (it wraps to two lines), or its overflow is `Clip` (not ellipsised).

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ButtonScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Tones down; Regular, Regular with an icon and Large across, each enabled then disabled; on `bg`, then on `surf`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ButtonScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val tones = listOf(ButtonTone.Primary, ButtonTone.Quiet, ButtonTone.Danger, ButtonTone.Destroy)

    @Composable
    private fun Panel(on: ButtonSurface, background: Color) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.background(background).padding(16.dp)) {
            tones.forEach { tone ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(true, false).forEach { enabled ->
                        HhButton("Next", {}, tone = tone, enabled = enabled, on = on)
                        HhButton("Add event", {}, tone = tone, icon = Icons.ADD, enabled = enabled, on = on)
                        HhButton("Save", {}, tone = tone, size = ButtonSize.Large, icon = Icons.CHECK, enabled = enabled, on = on)
                    }
                }
            }
        }
    }

    private fun snap(name: String, dark: Boolean) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                val c = Culvery.colors
                Column(Modifier.testTag("shot").background(c.bg)) {
                    Panel(ButtonSurface.Bg, c.bg)
                    Panel(ButtonSurface.Surf, c.surf)
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun matrixDark() = snap("button_matrix_dark", true)
    @Test fun matrixLight() = snap("button_matrix_light", false)
}
```

- [ ] **Step 4: Run them to see them fail**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*ContrastTest*" --tests "*ButtonsTest*" --tests "*FontScaleTest*"`
Expected: FAIL to compile with "Unresolved reference 'quietFill'", "'buttonColors'", "'HhButton'", "'ButtonSize'", "'ButtonSurface'", "'WithFontScale'".

- [ ] **Step 5: Write `isLight`, `quietFill`, `buttonColors`, `HhButton` and `WithFontScale`**

`core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt`: add `import androidx.compose.ui.graphics.luminance` and, after `LightColors`:
```kotlin

/** True for the day theme; while the theme fades, from whichever side `bg` has reached. */
internal val HhColors.isLight: Boolean get() = bg.luminance() > 0.5f
```
Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Buttons.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Button values (hand-off §7; 4d-1 §4.1). Both sizes are pills: the radius is half the height. */
object ButtonTokens {
    val regularHeight = 48.dp
    val regularPaddingH = 20.dp
    val regularIconPaddingStart = 14.dp
    val regularIconGap = 6.dp

    val largeHeight = 60.dp
    val largePaddingH = 26.dp
    val largeIconGap = 8.dp

    val icon = 24.dp
}

/** [Regular] 48 dp with a 15 sp / 700 label; [Large] 60 dp with a 17 sp / 700 label (sheet footers, pickers). */
enum class ButtonSize { Regular, Large }

/** What a button sits on; its quiet fill is one step above it (4d-1 Decision 9). Sheets are [Bg]. */
enum class ButtonSurface { Bg, Surf, DangerSoft }

/** The quiet and disabled fill on [on]. On light `bg` it is `surf`: `surf2` there is 1.04:1 and the button vanishes. */
internal fun quietFill(on: ButtonSurface, colors: HhColors): Color = when (on) {
    ButtonSurface.Bg -> if (colors.isLight) colors.surf else colors.surf2
    ButtonSurface.Surf -> colors.surf2
    ButtonSurface.DangerSoft -> colors.surf
}

internal data class ButtonColors(val fill: Color, val ink: Color)

/** A button's fill and label colour. Disabled, every tone is the quiet fill with a `mute` label (4d-1 Decision 8). */
internal fun buttonColors(tone: ButtonTone, enabled: Boolean, on: ButtonSurface, colors: HhColors): ButtonColors {
    val quiet = quietFill(on, colors)
    return when {
        !enabled -> ButtonColors(quiet, colors.mute)
        tone == ButtonTone.Primary -> ButtonColors(colors.accent, colors.accentInk)
        tone == ButtonTone.Danger -> ButtonColors(quiet, colors.danger)
        tone == ButtonTone.Destroy -> ButtonColors(colors.danger, colors.dangerInk)
        else -> ButtonColors(quiet, colors.ink)
    }
}

/**
 * The one button (4d-1 §4.1): a pill at least [size]'s height, a one-line label, an optional icon before it. Disabled,
 * every [tone] is the quiet fill with a `mute` label and does nothing. [on] is what the button sits on, with no default
 * so no caller can leave a button on `bg` with a `surf` fill.
 */
@Composable
fun HhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Quiet,
    size: ButtonSize = ButtonSize.Regular,
    icon: String? = null,
    enabled: Boolean = true,
    on: ButtonSurface,
) {
    val (fill, ink) = buttonColors(tone, enabled, on, Culvery.colors)
    val large = size == ButtonSize.Large
    val start = when {
        large -> ButtonTokens.largePaddingH
        icon != null -> ButtonTokens.regularIconPaddingStart
        else -> ButtonTokens.regularPaddingH
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (large) ButtonTokens.largeIconGap else ButtonTokens.regularIconGap,
            Alignment.CenterHorizontally,
        ),
        modifier = modifier
            .heightIn(min = if (large) ButtonTokens.largeHeight else ButtonTokens.regularHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = start, end = if (large) ButtonTokens.largePaddingH else ButtonTokens.regularPaddingH),
    ) {
        if (icon != null) HhIcon(icon, size = ButtonTokens.icon, tint = ink)
        Text(
            text,
            style = if (large) ControlType.button else HhType.buttonLabel,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
```
Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/FontScale.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Draws [content] at [fontScale], scaled as the platform scales it (non-linearly for large text from API 34), for tests
 * and screenshots: Robolectric's own font scale reaches only activities started after it is set.
 */
@Composable
fun WithFontScale(fontScale: Float, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val density = remember(context, fontScale) {
        val config = Configuration(context.resources.configuration).apply { this.fontScale = fontScale }
        Density(context.createConfigurationContext(config))
    }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*ContrastTest*" --tests "*ButtonsTest*" --tests "*FontScaleTest*"`
Expected: PASS. If `ButtonsTest` finds a height of 47.x or 48.x dp, the hdpi rounding shows: stop and report rather than loosen the assertion.

- [ ] **Step 7: Record the button matrix and look at it**

Run: `./gradlew :core:ui:recordRoborazziDebug --tests "*ButtonScreenshotTest*"`
Open `core/ui/src/test/screenshots/button_matrix_dark.png` and `button_matrix_light.png`. Each shows two panels, `bg` above `surf`, each four rows (Primary, Quiet, Danger, Destroy) of six pills: Next, + Add event, ✓ Save (60 dp, larger label) enabled, then the same three disabled. Check: every enabled Quiet and Danger pill, and every disabled pill, has a fill visibly different from its panel; in light, on `bg` those fills are white, on `surf` pale grey; disabled labels are grey in every row; Danger's label and icon are red; the Regular pills are visibly shorter than the Save pills. Anything else: stop and report.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`; no other module's baselines change (nothing calls `HhButton` yet).

- [ ] **Step 9: Commit**

```bash
git add core/ui
git commit -m "Add one shared button with a role, 48 and 60 dp sizes, one disabled look and a fill that shows on its surface"
```

---

### Task 2: `HhPillButton`, `HhSheetButton` and the confirm card move to `HhButton` (§4.1, Decision 8; rulings 1, 5, 11)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Components.kt:35-59` (delete `HhPillButton`), `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Controls.kt:68-73` (button tokens out), `:105-106` (`ControlType.button` stays), `:217-298` (`ButtonTone`, `HhSheetButton`, `HhConfirmCard`), `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Buttons.kt` (`ButtonTone` moves in)
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/PeopleUi.kt:52`, `PersonEditorSheet.kt:206-238`, `SettingsScreen.kt:69`, `SetupUi.kt:27`, `SetupWizard.kt:182-193, 238`, `steps/WelcomeStep.kt:81`, `steps/YouStep.kt:160`, `pages/KioskPage.kt:37, 41`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ConnectCalendarCard.kt:48-52`, `ReviewCalendars.kt:190-197, 233, 278, 327`, `EventDetailSheet.kt:127-132`
- Modify: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/ForecastCard.kt:72`
- Modify: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt:89-101`
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ControlsTest.kt` (two tests replaced), `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SetupButtonsTest.kt` (create), `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt`, `ReviewCalendarsTest.kt`, `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/ForecastCardTest.kt`, `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/ConnectScreenTest.kt`

**Interfaces:**
- Consumes: Task 1's `HhButton`, `ButtonSize`, `ButtonSurface`.
- Produces:
  - `enum class ButtonTone { Primary, Quiet, Danger, Destroy }` in `Buttons.kt` (`Plain` gone).
  - `@Composable fun HhConfirmCard(tag: String, keep: String, keepTag: String, confirm: String, confirmTag: String, busy: Boolean, onKeep: () -> Unit, onConfirm: () -> Unit, confirmIcon: String? = null, question: @Composable () -> Unit)` — `keepTone` gone.
  - `HhPillButton`, `HhSheetButton`, `ControlTokens.buttonHeight/buttonRadius/buttonPaddingH/buttonIcon/buttonIconGap` gone.
  - Every migrated button keeps its test tag.

- [ ] **Step 1: Write the failing tests**

`core/ui/src/test/java/uk/co/siland/culvery/core/ui/ControlsTest.kt`: delete `aDisabledSheetButtonIgnoresTaps` and `aDisabledPillIgnoresTaps` (Task 1's `ButtonsTest` covers both), and add:
```kotlin
    @Test
    fun aConfirmCardsButtonsAreLargeButtonsThatStopWhileBusy() {
        var keeps = 0
        show {
            HhConfirmCard(
                tag = "confirm", keep = "Keep", keepTag = "keep", confirm = "Remove", confirmTag = "remove",
                busy = true, onKeep = { keeps++ }, onConfirm = {},
            ) { Text("Remove Sam?") }
        }
        listOf("keep", "remove").forEach {
            compose.onNodeWithTag(it)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHeightIsEqualTo(60.dp)
                .assertIsNotEnabled()
        }
        compose.onNodeWithTag("keep").performClick()
        assertThat(keeps).isEqualTo(0)
    }
```
(imports: `androidx.compose.material3.Text`, `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`, `androidx.compose.ui.test.assertHeightIsEqualTo`, `androidx.compose.ui.unit.dp`.) Fails today: `keepTone` is a required parameter (compile error); afterwards it fails if Keep or the confirm button loses its role or 60 dp.

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SetupButtonsTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role as HouseholdRole
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.setup.steps.WelcomeContent
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

/** Every button in the wizard, Settings and the person sheet is the shared one: a button, 48 dp, or 60 in a footer. */
@RunWith(AndroidJUnit4::class)
class SetupButtonsTest {
    @get:Rule val compose = createComposeRule()

    private val sam = Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), HouseholdRole.ADULT, hasPin = false)

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CulveryTheme(dark = false) {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) { content() }
        }
    }

    private fun assertButton(tag: String, height: Dp) {
        compose.onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsEqualTo(height)
    }

    @Test
    fun theWizardsButtonsAreButtons() {
        show {
            Column {
                WizardFrame(6, 2, back = {}, forward = Forward.Next("Next", enabled = false), busy = false, onForward = {}, onSkip = {}) {
                    WelcomeContent(onSample = {}, busy = false)
                    PeopleList(listOf(sam), onEdit = {}, onAdd = {}, onMove = null)
                }
            }
        }
        listOf("wizard_back", "wizard_next", "welcome_sample", "people_add").forEach { assertButton(it, 48.dp) }
    }

    @Test
    fun skipIsAButton() {
        show { WizardFrame(6, 2, back = null, forward = Forward.Skip, busy = false, onForward = {}, onSkip = {}) {} }
        assertButton("wizard_skip", 48.dp)
    }

    @Test
    fun enterPinIsAButton() {
        show { PinGateContent(busy = false, onEnterPin = {}) }
        assertButton("wizard_enter_pin", 48.dp)
    }

    @Test
    fun settingsCloseAndTheKioskPageAreButtons() {
        show { SettingsScreen(listOf(KioskPage(FakeHomeApp(default = false))), onClose = {}) }
        listOf("settings_close", "choose_home_app", "settings_exit_kiosk").forEach { assertButton(it, 48.dp) }
    }

    @Test
    fun thePersonSheetsButtonsAreButtons() {
        show {
            PersonEditorSheet(
                form = PersonForm(sam, taken = setOf(PersonPalette.colors[0])),
                busy = false, canRemove = true, confirmingRemove = false, choosingPin = false,
                onChoosePin = {}, onPinChosen = {}, onPinCancelled = {}, onSave = {}, onRemove = {}, onKeep = {},
                onConfirmRemove = {}, onClose = {},
            )
        }
        assertButton("person_set_pin", 48.dp)
        assertButton("person_remove", 60.dp)
        assertButton("person_save", 60.dp)
    }
}
```
Fails today: `HhPillButton` has no role and is about 46 dp; `HhSheetButton` has no role.

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt`, add:
```kotlin
    @Test
    fun theConnectCardsButtonIsAButton() {
        show { ConnectCalendarCard(connectService = "Google Calendar", onConnect = {}) }
        compose.onNodeWithText("Connect Google Calendar")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsEqualTo(48.dp)
    }
```
(imports: `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`.)

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/ReviewCalendarsTest.kt`, add:
```kotlin
    @Test
    fun disconnectAndMakeMasterAreButtons() {
        show()
        listOf("review_disconnect_g1", "review_make_master_chores").forEach {
            compose.onNodeWithTag(it)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHeightIsEqualTo(48.dp)
        }
    }
```
(imports as above, plus `androidx.compose.ui.test.assertHeightIsEqualTo`, `androidx.compose.ui.unit.dp` if not there.)

`capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/ForecastCardTest.kt`, add:
```kotlin
    @Test
    fun withoutALocationOpenSettingsIsAButton() {
        show(WeatherView.NoLocation)
        compose.onNodeWithText("Open settings")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsEqualTo(48.dp)
    }
```
(imports: `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`, `androidx.compose.ui.test.assertHeightIsEqualTo`, `androidx.compose.ui.unit.dp`.)

`provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/ConnectScreenTest.kt`, add:
```kotlin
    @Test
    fun connectAndCancelAreButtons() {
        compose.setContent {
            CulveryTheme(dark = true) { FakeCalendarProvider().ConnectScreen(existing = null, onConnected = {}, onCancel = {}) }
        }
        listOf("Connect sample calendar", "Cancel").forEach {
            compose.onNodeWithText(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }
```
(imports: `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`.)

Each fails today on the missing `Role.Button` (and the height, about 46 dp).

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :core:ui:testDebugUnitTest --tests "*ControlsTest*" :core:setup:testDebugUnitTest --tests "*SetupButtonsTest*" :capability:calendar:testDebugUnitTest --tests "*CardsTest*" --tests "*ReviewCalendarsTest*" :capability:weather:testDebugUnitTest --tests "*ForecastCardTest*" :provider:calendar-fake:testDebugUnitTest --tests "*ConnectScreenTest*"`
Expected: `ControlsTest` fails to compile ("No value passed for parameter 'keepTone'"); the others FAIL on "Failed to assert the following: (Role = 'Button')".

- [ ] **Step 3: Move `ButtonTone`, rebuild the confirm card, delete the old buttons in `:core:ui`**

`Controls.kt`: delete `ButtonTone` and its KDoc (lines 217-221) and `HhSheetButton` (lines 223-260); delete the footer button tokens and their comment (lines 68-73: `buttonHeight`, `buttonRadius`, `buttonPaddingH`, `buttonIcon`, `buttonIconGap`). Keep `ControlType.button` (Task 1's Large label). Replace `HhConfirmCard` (lines 262-298) with:
```kotlin
/**
 * A confirmation card on `dangerSoft`: [question] above two buttons. Keep sits on the left where the action that opened
 * the card was, so a double tap on it is harmless; the confirming button is [ButtonTone.Destroy].
 */
@Composable
fun HhConfirmCard(
    tag: String,
    keep: String,
    keepTag: String,
    confirm: String,
    confirmTag: String,
    busy: Boolean,
    onKeep: () -> Unit,
    onConfirm: () -> Unit,
    confirmIcon: String? = null,
    question: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ControlTokens.confirmGap),
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .clip(RoundedCornerShape(ControlTokens.confirmRadius))
            .background(Culvery.colors.dangerSoft)
            .padding(ControlTokens.confirmPadding),
    ) {
        question()
        Row(horizontalArrangement = Arrangement.spacedBy(ControlTokens.confirmButtonGap), modifier = Modifier.fillMaxWidth()) {
            HhButton(
                keep, onKeep, Modifier.weight(1f).testTag(keepTag),
                size = ButtonSize.Large, enabled = !busy, on = ButtonSurface.DangerSoft,
            )
            HhButton(
                confirm, onConfirm, Modifier.weight(1f).testTag(confirmTag),
                tone = ButtonTone.Destroy, size = ButtonSize.Large, icon = confirmIcon, enabled = !busy, on = ButtonSurface.DangerSoft,
            )
        }
    }
}
```
`Buttons.kt`: add, after `ButtonTokens`:
```kotlin
/**
 * [Primary] `accent` with `accentInk`; [Quiet] the quiet fill with `ink`; [Danger] the quiet fill with `danger`
 * (Delete); [Destroy] `danger` with `dangerInk`, only a confirm card's confirming button.
 */
enum class ButtonTone { Primary, Quiet, Danger, Destroy }
```
`Components.kt`: delete `HhPillButton` and its KDoc (lines 35-59) and the imports only it used (`clickable`, `Text`, `Color` if unused — let the compiler's unused-import warnings guide you; HhCard still needs `Color`).

- [ ] **Step 4: Move the setup callers (all on `bg` except the Settings column, which is `surf`)**

Add `import uk.co.siland.culvery.core.ui.HhButton`, and `ButtonTone` / `ButtonSize` / `ButtonSurface` where used; remove `HhPillButton` / `HhSheetButton` imports.

- `core/setup/src/main/java/uk/co/siland/culvery/core/setup/PeopleUi.kt:52`:
```kotlin
        if (onAdd != null) HhButton(ADD_PERSON, onAdd, Modifier.testTag("people_add"), tone = ButtonTone.Primary, on = ButtonSurface.Bg)
```
- `PersonEditorSheet.kt:208-213`:
```kotlin
                        if (form.hasPin) {
                            HhButton(CHANGE_PIN, onChoosePin, Modifier.testTag("person_change_pin"), enabled = !busy, on = ButtonSurface.Bg)
                            HhButton(REMOVE_PIN, form::clearPin, Modifier.testTag("person_remove_pin"), enabled = !busy, on = ButtonSurface.Bg)
                        } else {
                            HhButton(SET_PIN, onChoosePin, Modifier.testTag("person_set_pin"), enabled = !busy, on = ButtonSurface.Bg)
                        }
```
  `PersonEditorSheet.kt:219-238` (the confirm card and the footer):
```kotlin
                HhConfirmCard(
                    tag = "person_confirm",
                    keep = KEEP, keepTag = "person_keep",
                    confirm = REMOVE_PERSON, confirmTag = "person_confirm_remove",
                    busy = busy, onKeep = onKeep, onConfirm = onConfirmRemove,
                ) {
                    Text(removeQuestion(existing.person.name), style = ControlType.confirmTitle, color = c.ink)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(SetupDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
                    if (canRemove) {
                        HhButton(
                            REMOVE_PERSON, onRemove, Modifier.testTag("person_remove"),
                            tone = ButtonTone.Danger, size = ButtonSize.Large, enabled = !busy, on = ButtonSurface.Bg,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    HhButton(
                        if (existing == null) SAVE_PERSON else SAVE_CHANGES,
                        onSave,
                        Modifier.testTag("person_save"),
                        tone = ButtonTone.Primary,
                        size = ButtonSize.Large,
                        enabled = form.canSave && !busy,
                        on = ButtonSurface.Bg,
                    )
                }
```
- `SettingsScreen.kt:69` (the page list column is `surf`): `HhButton(CLOSE, onClose, Modifier.testTag("settings_close"), on = ButtonSurface.Surf)`
- `SetupUi.kt:27`: `HhButton(CHOOSE_HOME_APP, onChoose, Modifier.testTag("choose_home_app"), tone = ButtonTone.Primary, on = ButtonSurface.Bg)`
- `SetupWizard.kt:182-193`:
```kotlin
            if (back != null) HhButton(BACK, back, Modifier.testTag("wizard_back"), enabled = !busy, on = ButtonSurface.Bg)
            Spacer(Modifier.weight(1f))
            when (forward) {
                Forward.Skip -> HhButton(SKIP_FOR_NOW, onSkip, Modifier.testTag("wizard_skip"), enabled = !busy, on = ButtonSurface.Bg)
                is Forward.Next -> HhButton(
                    forward.label,
                    onForward,
                    Modifier.testTag("wizard_next"),
                    tone = ButtonTone.Primary,
                    enabled = forward.enabled && !busy,
                    on = ButtonSurface.Bg,
                )
            }
```
  `SetupWizard.kt:238`: `HhButton(ENTER_PIN, onEnterPin, Modifier.testTag("wizard_enter_pin"), tone = ButtonTone.Primary, enabled = !busy, on = ButtonSurface.Bg)`
- `steps/WelcomeStep.kt:81`: `if (onSample != null) HhButton(USE_SAMPLE_HOUSEHOLD, onSample, Modifier.testTag("welcome_sample"), enabled = !busy, on = ButtonSurface.Bg)`
- `steps/YouStep.kt:160`: `HhButton(SET_YOUR_PIN, onSetPin, Modifier.testTag("you_set_pin"), on = ButtonSurface.Bg)`
- `pages/KioskPage.kt:37`: `HhButton(CHANGE_HOME_APP, navigator::changeHomeApp, Modifier.testTag("settings_change_home_app"), on = ButtonSurface.Bg)`; `:41`: `HhButton(EXIT_KIOSK, navigator::exitKiosk, Modifier.testTag("settings_exit_kiosk"), on = ButtonSurface.Bg)`

- [ ] **Step 5: Move the calendar, weather and fake-provider callers (all on `surf`)**

- `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ConnectCalendarCard.kt:48-52`:
```kotlin
        if (connectService != null) {
            HhButton("Connect $connectService", onConnect, tone = ButtonTone.Primary, on = ButtonSurface.Surf)
        } else {
            HhButton("Open settings", navigator::openSettings, tone = ButtonTone.Primary, on = ButtonSurface.Surf)
        }
```
- `ReviewCalendars.kt:190-197`: drop `keepTone = ButtonTone.Quiet,` from the `HhConfirmCard` call. `:233`: `HhButton(DISCONNECT, { onDisconnect(row) }, Modifier.testTag("review_disconnect_${row.connection.id}"), enabled = !busy, on = ButtonSurface.Surf)`. `:278`: `HhButton(MAKE_MASTER, { onMakeMaster(source) }, Modifier.testTag("review_make_master_$id"), enabled = !busy, on = ButtonSurface.Surf)`. `:327`: `HhButton(HIDE_THIS_CALENDAR, { onShown(source, false) }, Modifier.testTag("review_hide_$id"), enabled = !busy, on = ButtonSurface.Surf)`. Remove the `ButtonTone` import if nothing else uses it.
- `EventDetailSheet.kt:129`: `keep = "Keep event", keepTag = "detail_keep",` (drop `keepTone`); remove the `ButtonTone` import.
- `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/ForecastCard.kt:72`: `HhButton(OPEN_SETTINGS, navigator::openSettings, tone = ButtonTone.Primary, on = ButtonSurface.Surf)`
- `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt:91-99`:
```kotlin
            HhButton(
                "Connect sample calendar",
                onClick = {
                    val id = existing?.id ?: UUID.randomUUID().toString()
                    onConnected(Connection(id, ID, "Sample calendar", emptyMap()))
                },
                tone = ButtonTone.Primary,
                on = ButtonSurface.Surf,
            )
            HhButton("Cancel", onCancel, on = ButtonSurface.Surf)
```

Then check nothing is left:
```bash
grep -rn "HhPillButton\|HhSheetButton\|ButtonTone.Plain\|keepTone\|ControlTokens.button" --include=*.kt .
```
Expected: no output.

- [ ] **Step 6: Run the tests to see them pass**

Run the Step 2 command again. Expected: PASS. Then each touched module's whole suite:
`./gradlew :core:ui:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest :capability:weather:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest`
Expected: PASS. A test that finds a button by text or tag and fails means a tag was lost or a label changed: fix the call, never the test.

- [ ] **Step 7: Compare, re-record and look at the changed baselines**

First compare against the current baselines:
```bash
./gradlew :core:setup:compareRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
./gradlew :capability:calendar:compareRoborazziDebug --tests "*CardScreenshotTest*" --tests "*ReviewScreenshotTest*" --tests "*DetailScreenshotTest*"
./gradlew :capability:weather:compareRoborazziDebug --tests "*WeatherScreenshotTest*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Open every `*_compare.png` listed (reference, new image and their difference side by side) and check each difference against the list below before recording. Then record:
```bash
./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
./gradlew :capability:calendar:recordRoborazziDebug --tests "*CardScreenshotTest*" --tests "*ReviewScreenshotTest*" --tests "*DetailScreenshotTest*"
./gradlew :capability:weather:recordRoborazziDebug --tests "*WeatherScreenshotTest*"
git status --short -- "*.png"
```
Expected changes, and what each must show (open every one):
- core/setup — `wizard_frame_*`, `wizard_pin_gate_dark`, `welcome_*`, `welcome_sample_dark`, `you_*`, `done_*`, `people_list_*`, `location_*` (Back/Skip), `settings_*` (Close, Exit kiosk, Choose home app), `person_new_*`, `person_error_dark`, `person_pin_dark`, `person_remove_dark`: the pills are 48 dp (about 2 dp taller) with 20 dp side padding; in light, Back, Skip, Exit kiosk, Change home app and the person sheet's Set PIN are white on the grey page (they were invisible), while Close, on the white Settings column, stays pale grey; the disabled Next has a grey label on white; the person sheet's Remove person is white with a red label in light; in `person_remove_dark` Keep is `surf` (slightly darker than before). Nothing else moves.
- capability/calendar — `connect_*`, `connect_step_*`, `review_*`, `settings_calendars_page_*`: the accent and quiet pills 48 dp. `detail_delete_confirm_*` may be byte-identical (Keep was already `surf`).
- capability/weather — `forecast_no_location_*`: Open settings 48 dp.
Any other changed image: stop and report.

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 9: Commit**

```bash
git add core/ui core/setup capability/calendar capability/weather provider/calendar-fake
git commit -m "Draw the wizard, Settings, person sheet, confirm cards and Home prompts with the shared button"
```

---

### Task 3: The calendar's own buttons move to `HhButton`; 44 and 40 dp targets become 48 (§4.1; rulings 5, 9, 10, 12)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Components.kt:25-40` (`HeaderChip` out), `:54-128` (`DeleteButton` becomes a wrapper; `PrimaryButton`, `AddButton` out)
- Modify: `TodayCard.kt:56, 72-87`; `WeekView.kt:118, 141, 222-235, 286-328`; `EventDetailSheet.kt:145-150, 274-289`; `EventEditorSheet.kt:378-395`; `Pickers.kt:155-159, 180-191, 274-296`; `CalendarConnectHost.kt:101-105`; `ReviewCalendars.kt:203, 232`; `ComingUpCard.kt:35-41, 53-54`
- Modify: `CalendarType.kt:32-33, 53-54, 59-60, 74-75, 92-93, 98-99` and `CalendarDimens` (`:116-119, 235-247, 249-283, 305-324`)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/TextLayouts.kt` (create), `CardsTest.kt`, `WeekViewTest.kt`, `EventDetailSheetTest.kt`, `PickersTest.kt`

**Interfaces:**
- Consumes: `HhButton`, `ButtonTone`, `ButtonSize`, `ButtonSurface` (Tasks 1–2).
- Produces: `internal fun DeleteButton(enabled: Boolean, tag: String, onClick: () -> Unit)` as a wrapper; `CalendarDimens.touchTarget = 48.dp`, `pillRadius = 24.dp`, `addHintMinHeight = 48.dp`; `HeaderChip`, `PrimaryButton`, `AddButton`, `PickerButton`, `ThisWeekChip`, `AssignButton` gone. Tags kept: `week_add_event`, `week_this_week`, `detail_delete`, `detail_edit`, `detail_assign`, `editor_delete`, `editor_save`, `picker_cancel`, `picker_set`, `connecting_cancel`, `settings_connect_{id}`, `settings_reconnect`, `today_add`.
- Test-only (calendar): `internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult`.

- [ ] **Step 1: Write the test helper and the failing tests**

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/TextLayouts.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult

/** The layout Compose drew for this text node: its style, its lines and whether it overflowed. */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}
```
In each test file below add the imports `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert` where missing, and a private helper at the top of the class:
```kotlin
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
```
`CardsTest.kt`: replace `weekChipOnTodayIsAFullSizePill` and `thePlusOnTodayIsA44DpCircleThatAdds` with:
```kotlin
    @Test
    fun weekOnTodayIsA48DpButton() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Week").assert(isButton).assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun thePlusOnTodayIsA48DpCircleThatAdds() {
        var added = 0
        show { TodayCard(SampleUi.today, onAdd = { added++ }) }
        compose.onNodeWithTag("today_add").assert(isButton).assertHeightIsEqualTo(48.dp).assertWidthIsEqualTo(48.dp).performClick()
        assertThat(added).isEqualTo(1)
        compose.onNodeWithText("Week").assertExists()
    }
```
`WeekViewTest.kt`: in `theReconnectChipRunsTheReconnect` change `assertHeightIsAtLeast(44.dp)` to `.assert(isButton).assertHeightIsEqualTo(48.dp)`; replace `eachColumnEndsWithAnAddHintAtLeast40DpTall` with:
```kotlin
    @Test
    fun eachColumnEndsWithAnAddHintAtLeast48DpTall() {
        show { WeekView(state(), onAdd = {}) }
        (0L..6L).forEach {
            compose.onNodeWithTag("week_add_${SampleUi.TODAY.plusDays(it)}", useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun addEventAndThisWeekAre48DpButtons() {
        show { WeekView(state().copy(weeksAhead = 1), onAdd = {}) }
        listOf("week_add_event", "week_this_week").forEach { compose.onNodeWithTag(it).assert(isButton).assertHeightIsEqualTo(48.dp) }
    }
```
`EventDetailSheetTest.kt`, add:
```kotlin
    @Test
    fun deleteAndEditAreLargeButtonsAndADisabledDeleteIsGrey() {
        busy = true
        show(SampleUi.detailEditable)
        compose.onNodeWithTag("detail_edit").assert(isButton).assertHeightIsEqualTo(60.dp)
        compose.onNodeWithTag("detail_delete").assert(isButton).assertHeightIsEqualTo(60.dp).assertIsNotEnabled()
        val label = compose.onNode(hasText("Delete") and hasAnyAncestor(hasTestTag("detail_delete")), useUnmergedTree = true).textLayout()
        assertThat(label.layoutInput.style.color).isEqualTo(DarkColors.mute)
    }

    @Test
    fun assignToIsA48DpButton() {
        show(SampleUi.detailUntagged)
        compose.onNodeWithTag("detail_assign").assert(isButton).assertHeightIsEqualTo(48.dp)
    }
```
(imports: `androidx.compose.ui.test.hasAnyAncestor`, `androidx.compose.ui.test.hasTestTag`, `androidx.compose.ui.test.hasText`, `uk.co.siland.culvery.core.ui.DarkColors`.)
`PickersTest.kt`, add (its `showTime(initial)` helper draws `TimePickerCard`):
```kotlin
    @Test
    fun thePickersButtonsAre60DpButtons() {
        showTime(LocalTime.of(9, 0))
        listOf("picker_cancel", "picker_set").forEach {
            compose.onNodeWithTag(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertHeightIsEqualTo(60.dp)
        }
    }
```
(imports as needed: `androidx.compose.ui.test.assertHeightIsEqualTo`, `androidx.compose.ui.unit.dp`.)

Each fails today: Week, This week and the reconnect chip are 44 dp with no role; the + is 44; the add hint 40; Delete keeps red text while disabled and has no role; Assign is 44; the picker buttons 56.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CardsTest*" --tests "*WeekViewTest*" --tests "*EventDetailSheetTest*" --tests "*PickersTest*"`
Expected: FAIL on the role, the 48 or 60 dp heights, and Delete's colour.

- [ ] **Step 3: Replace the calendar's buttons**

`ui/Components.kt`: delete `HeaderChip` (lines 25-40), `PrimaryButton` (74-102) and `AddButton` (104-128); replace `DeleteButton` (54-72) with:
```kotlin
/** A sheet's Delete (hand-off §7): the danger tone, large, on the sheet's `bg`. Disabled, grey like every button. */
@Composable
internal fun DeleteButton(enabled: Boolean, tag: String, onClick: () -> Unit) = HhButton(
    "Delete", onClick, Modifier.testTag(tag),
    tone = ButtonTone.Danger, size = ButtonSize.Large, icon = Icons.DELETE, enabled = enabled, on = ButtonSurface.Bg,
)
```
`ui/TodayCard.kt:56`: `HhButton("Week", { navigator.openTab(CALENDAR_TAB_ID) }, on = ButtonSurface.Surf)` (the card is `surf`). `AddCircle` (72-87) keeps its code; its KDoc becomes "Hand-off §7: a 48 dp `accent` circle (raised from 44, 4d-1), a 26 dp `add` in `accentInk`." and its clickable gains the role: `.clickable(onClickLabel = "Add event", role = Role.Button, onClick = onClick)` (import `androidx.compose.ui.semantics.Role`).

`ui/WeekView.kt`:
- line 118: `HhButton("This week", { onWeeksAhead(0) }, Modifier.testTag("week_this_week"), on = ButtonSurface.Bg)`
- line 141: `if (onAdd != null) HhButton("Add event", { onAdd(state.week.start) }, Modifier.testTag("week_add_event"), tone = ButtonTone.Primary, icon = Icons.ADD, on = ButtonSurface.Bg)`
- `AddHint` (222-235): KDoc "Hand-off §7: a faint `add` at the foot of each column, in a tap area at least 48 dp tall." (the alpha changes in Task 6).
- `ReconnectChip` (286-310): KDoc "One chip for every connection that needs signing in again: a `dangerSoft` pill, 48 dp, radius 24." and `.clickable(role = Role.Button, onClick = onClick)`.
- Delete `ThisWeekChip` (312-328).

`ui/EventDetailSheet.kt:148`:
```kotlin
                    HhButton(
                        "Edit", onEdit, Modifier.weight(1f).testTag("detail_edit"),
                        tone = ButtonTone.Primary, size = ButtonSize.Large, icon = Icons.EDIT, enabled = !busy, on = ButtonSurface.Bg,
                    )
```
`:120`: `HhButton("Assign to…", onChoosePerson, Modifier.testTag("detail_assign"), tone = ButtonTone.Primary, enabled = !busy, on = ButtonSurface.Surf)` (the note card is `accentSoft`; `Surf`'s `surf2` fill shows there when the button is disabled); delete `AssignButton` (274-289).

`ui/EventEditorSheet.kt` `Footer` (378-395):
```kotlin
@Composable
private fun Footer(form: EventForm, busy: Boolean, failed: Boolean, onSave: () -> Unit, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
        if (form.mode is EventForm.Mode.Edit) DeleteButton(enabled = !busy, tag = "editor_delete", onClick = onDelete)
        HhButton(
            text = when {
                failed -> "Try again"
                form.mode == EventForm.Mode.New -> "Save event"
                else -> "Save changes"
            },
            onClick = onSave,
            modifier = Modifier.weight(1f).testTag("editor_save"),
            tone = ButtonTone.Primary,
            size = ButtonSize.Large,
            icon = if (failed) Icons.REFRESH else Icons.CHECK,
            enabled = form.canSave && !busy,
            on = ButtonSurface.Bg,
        )
    }
}
```
`ui/Pickers.kt`: lines 155-159 become `HhButton("Cancel", onCancel, Modifier.fillMaxWidth().testTag("picker_cancel"), size = ButtonSize.Large, on = ButtonSurface.Surf)` (the picker card is `surf`); lines 181-190 become
```kotlin
            HhButton("Cancel", onCancel, Modifier.weight(1f).testTag("picker_cancel"), size = ButtonSize.Large, on = ButtonSurface.Surf)
            HhButton(
                "Set time", { onSet(LocalTime.of(hour, minute)) }, Modifier.weight(1f).testTag("picker_set"),
                tone = ButtonTone.Primary, size = ButtonSize.Large, on = ButtonSurface.Surf,
            )
```
and delete `PickerButton` (274-296).
`ui/CalendarConnectHost.kt:101-105`: `HhButton("Cancel", onCancel, Modifier.fillMaxWidth().testTag("connecting_cancel"), size = ButtonSize.Large, on = ButtonSurface.Surf)`.
`ui/ReviewCalendars.kt:203` (on the page's `bg`): `connectable.forEach { d -> HhButton("Connect ${d.displayName}", { actions.onConnect(d) }, Modifier.testTag("settings_connect_${d.id}"), tone = ButtonTone.Primary, icon = Icons.ADD, enabled = !busy, on = ButtonSurface.Bg) }`; `:232` (in the row): `if (needsReconnect) HhButton("Reconnect", { onReconnect(row.connection) }, Modifier.testTag("settings_reconnect"), tone = ButtonTone.Primary, enabled = !busy, on = ButtonSurface.Surf)`. Both gain `enabled = !busy` (ruling 30): Disconnect beside them already stops while a change runs.
`ui/ComingUpCard.kt`: the header row keeps `heightIn(min = CalendarDimens.touchTarget)` so it stays level with Today's; its comment (lines 53-54) becomes "The row is Today's header height (its Week button), so the two cards' headers line up." and `MAX_ROWS`' KDoc arithmetic (lines 36-41) becomes "279 − 2 × 20 padding − 48 header − 6 gap = 185 dp … Three rows need 19 + 3 × 56 + 22 = 209 dp and do not fit; two need 19 + 2 × 56 + 22 = 153 dp, which leaves room for font rounding. So 2." (keep the rest of the sentence as it is).

- [ ] **Step 4: Tidy `CalendarType` and `CalendarDimens`**

`CalendarType`: delete `assignButton` (53-54), `footerButton` (59-60), `pickerButton` (74-75), `primaryButton` (92-93), `addEventButton` (98-99); `pill`'s KDoc (32-33) becomes "14 sp / 600: the reconnect chip."
`CalendarDimens`:
- lines 116-119: `// The reconnect chip and Today's +: 48 dp touch targets (raised from 44, 4d-1); the chip's radius half that.` then `val touchTarget = 48.dp`, `val pillRadius = 24.dp`, `val pillPaddingH = 16.dp`.
- lines 235-247: keep `assignTop` and `personChipGap` under `// Assign: the button 12 below the note's text; person chips 8 apart (ControlTokens has the chip).`; keep `footerIcon` under `// The confirm card's delete icon.`; delete `assignButtonHeight`, `assignButtonRadius`, `assignButtonPaddingH`, `footerButtonHeight`, `footerButtonRadius`, `deleteButtonPaddingH`, `footerIconGap`.
- lines 249-280: delete `pickerCancelHeight`, `pickerCancelRadius`, `timeButtonHeight`, `timeButtonRadius`; in the comment above, replace "Cancel 52 dp, radius 26" with "Cancel a Large button" and "Cancel and Set time 56 dp, radius 28, 10 apart" with "Cancel and Set time Large buttons 10 apart".
- lines 305-324: keep `footerGap` (`// Footers: 10 between Delete and the main button.`), `todayAddIcon`, `todayHeaderButtonGap`, `addHintIcon`, `addHintMinHeight = 48.dp` and `ADD_HINT_ALPHA`; delete `primaryIconGap` and every `addEvent*` value; the comment becomes `// Entry points (hand-off §7): Today's + (touchTarget) with a 26 dp add, 8 from Week. Each week column ends with a 24 dp add hint in at least 48 dp.`

Then:
```bash
grep -rn "HeaderChip\|PrimaryButton\|AddButton\|PickerButton\|ThisWeekChip\|AssignButton\|assignButton\|footerButton\|deleteButtonPaddingH\|footerIconGap\|pickerCancel\|timeButtonHeight\|timeButtonRadius\|primaryIconGap\|CalendarDimens\.addEvent\|CalendarType\.addEventButton\|pickerButton\|primaryButton" --include=*.kt capability/*/src/main app/src/main
```
Expected: no output (main sources only: test names such as `addEventInTheHeaderAddsOnToday` are fine).

- [ ] **Step 5: Run the tests to see them pass**

Run the Step 2 command, then `./gradlew :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`.
Expected: PASS.

- [ ] **Step 6: Compare, re-record and look at the changed baselines**

First compare against the current baselines:
```bash
./gradlew :capability:calendar:compareRoborazziDebug --tests "*CardScreenshotTest*" --tests "*WeekScreenshotTest*" --tests "*DetailScreenshotTest*" --tests "*EditorScreenshotTest*" --tests "*ConnectScreenshotTest*" --tests "*ReviewScreenshotTest*"
./gradlew :app:compareRoborazziDebug --tests "*ShellScreenshotTest*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Open every `*_compare.png` listed (reference, new image and their difference side by side) and check each difference against the list below before recording. Then record:
```bash
./gradlew :capability:calendar:recordRoborazziDebug --tests "*CardScreenshotTest*" --tests "*WeekScreenshotTest*" --tests "*DetailScreenshotTest*" --tests "*EditorScreenshotTest*" --tests "*ConnectScreenshotTest*" --tests "*ReviewScreenshotTest*"
./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest*"
git status --short -- "*.png"
```
Expected changes, and what each must show:
- `today_*` (`today_empty_dark`, `today_badges_dark` too): Week 48 dp with a 15 sp bold label; the + 48 dp; rows start 4 dp lower. `coming_up_*`: the header 4 dp taller, columns 4 dp lower, still two rows and "+2 more" in the busy one.
- `week_*`: the header's Add event unchanged in size; This week 48 dp, white in light; the reconnect chip (`week_needs_sign_in_dark`) 48 dp; columns start up to 4 dp lower.
- `detail_*`: Delete is white with red text in light (unchanged in dark); Edit's label 17 sp; `detail_untagged_*` (choosing a person) unchanged except anything below; Assign to… doesn't appear in these (ChoosingPerson) — if `detail_untagged_*` didn't change, that is right.
- `editor_*`: the footer's Save 17 sp; in light the disabled Save in `editor_empty_light` is white with a grey label; `editor_date_picker_*` Cancel 60 dp; `editor_time_picker_*` Cancel and Set time 60 dp.
- `connecting_*`: Cancel 60 dp.
- `review_*`, `settings_calendars_page_*`: Connect and Reconnect may be byte-identical (same size and colour).
- app `home_calendar_*`, `home_weather_*`: Today's Week 48 dp, its rows 4 dp lower; Coming up's header level with Today's.
Any other changed image: stop and report.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar app
git commit -m "Draw the calendar's buttons with the shared button and give Today's +, Week, This week, the reconnect chip and the add hints 48 dp targets"
```

---
### Task 4: `HhListRow` and `HhSectionLabel`; people, towns, connections, calendars and both sheets' labels (§4.2, §4.3, §6.6's health line; rulings 14, 21)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Components.kt` (append), `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Type.kt:37-57` (`sectionLabel`)
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/PeopleUi.kt:56-107`, `LocationPane.kt:141-159`, `PersonEditorSheet.kt:245-251`, `SetupDimens.kt:28-37, 81` (row values out; `SetupType.label` out)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ReviewCalendars.kt:207-306`, `EventEditorSheet.kt:230-241` (and the four `Section("WHO")`… calls at 245, 279, 305, 329), `CalendarType.kt:89-90, 104-105`, `CalendarType.kt` `CalendarDimens` 333-350 (row values out)
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ListRowAndLabelTest.kt` (create); `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TextLayouts.kt`, `RowStylesTest.kt` (create); `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/ReviewCalendarsTest.kt`, `FontScaleTest.kt` (create)

**Interfaces:**
- Consumes: `HhButton` (Tasks 1–2), `WithFontScale` (Task 1), the calendar's `textLayout()` (Task 3).
- Produces (package `uk.co.siland.culvery.core.ui`):
  - `object ListRowTokens { radius, paddingV, paddingH, leadingGap, lineGap, trailingGap, belowGap, icon: Dp }`
  - `@Composable fun HhListRow(title: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, subtitle: String? = null, subtitleColor: Color = Culvery.colors.mute, trailing: (@Composable RowScope.() -> Unit)? = null, onClick: (() -> Unit)? = null, below: (@Composable ColumnScope.() -> Unit)? = null)`
  - `@Composable fun HhSectionLabel(text: String, modifier: Modifier = Modifier)`
  - `HhType.sectionLabel` (13 sp / 700, 0.5 sp tracking)
  - Gone: `SetupType.label`, `CalendarType.sectionLabel`, `CalendarType.settingsRowTitle`, `SetupDimens.rowRadius/rowPaddingV/rowPaddingH/rowLineGap/rowIcon`, `CalendarDimens.settingsRowRadius/settingsRowPaddingV/settingsRowPaddingH/settingsIconGap/settingsStatusTop/reviewRowRadius/reviewRowPaddingV/reviewRowPaddingH`.
  - Test-only (core/setup): `internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult`; (capability/calendar): `class FontScaleTest` (NATIVE graphics), which Tasks 6, 9 and 10 add to.

- [ ] **Step 1: Write the failing tests**

Create `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ListRowAndLabelTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListRowAndLabelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aSectionLabelShowsCapitalsIsAHeadingAndSaysItsWordInSentenceCase() {
        compose.setContent { CulveryTheme(dark = true) { HhSectionLabel("Who") } }
        compose.onNodeWithText("WHO").assert(isHeading()).assertContentDescriptionEquals("Who")
    }

    @Test
    fun aRowWithAnActionIsAButtonAndOneWithoutTakesNoTap() {
        var taps = 0
        compose.setContent {
            CulveryTheme(dark = true) {
                Column {
                    HhListRow("Alex", Modifier.testTag("tappable"), subtitle = "Admin · PIN set", onClick = { taps++ })
                    HhListRow("Brighton", Modifier.testTag("still"))
                }
            }
        }
        compose.onNodeWithTag("tappable").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertThat(taps).isEqualTo(1)
        compose.onNodeWithTag("still").assert(!hasClickAction())
    }

    @Test
    fun aRowsTitleAndSubtitleAreOneLineInTheSharedStyles() {
        compose.setContent { CulveryTheme(dark = true) { HhListRow("Alex", subtitle = "Admin · PIN set") } }
        val title = compose.onNodeWithText("Alex", useUnmergedTree = true).textLayout()
        val subtitle = compose.onNodeWithText("Admin · PIN set", useUnmergedTree = true).textLayout()
        assertThat(title.layoutInput.style.fontSize).isEqualTo(17.sp)
        assertThat(title.layoutInput.style.fontWeight).isEqualTo(FontWeight.W600)
        assertThat(title.layoutInput.maxLines).isEqualTo(1)
        assertThat(subtitle.layoutInput.style.fontSize).isEqualTo(14.sp)
        assertThat(subtitle.layoutInput.maxLines).isEqualTo(1)
    }
}
```
Fails if: the label isn't upper-cased, isn't a heading, or speaks "WHO"; the row loses `Role.Button` or clicks without `onClick`; a row's title or subtitle leaves the spec's 17 sp / 600 and 14 sp or its one-line limit.

Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/TextLayouts.kt` (the same five lines as Task 1's, in package `uk.co.siland.culvery.core.setup`):
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult

/** The layout Compose drew for this text node: its style, its lines and whether it overflowed. */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}
```
Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/RowStylesTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

/** People's and towns' rows are the shared row, at the sizes Settings › Calendars uses too (4d-1 §4.2). */
@RunWith(AndroidJUnit4::class)
class RowStylesTest {
    @get:Rule val compose = createComposeRule()

    private val alex = Member(Person(PersonId("alex"), "Alex", PersonPalette.colors[0]), Role.ADMIN, hasPin = true)
    private val london = PlaceMatch("London", "England", "United Kingdom", 51.5074, -0.1278, "Europe/London")

    private fun style(text: String): TextStyle = compose.onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style

    private fun assertRowTitle(text: String) {
        assertWithMessage("$text size").that(style(text).fontSize).isEqualTo(17.sp)
        assertWithMessage("$text weight").that(style(text).fontWeight).isEqualTo(FontWeight.W600)
    }

    @Test
    fun aPersonsRowUsesTheSharedRowStyles() {
        compose.setContent { CulveryTheme(dark = true) { PeopleList(listOf(alex), onEdit = {}, onAdd = null, onMove = null) } }
        assertRowTitle("Alex")
        assertWithMessage("subtitle size").that(style("Admin · PIN set").fontSize).isEqualTo(14.sp)
    }

    @Test
    fun aTownsRowUsesTheSharedRowStyle() {
        compose.setContent {
            CulveryTheme(dark = true) {
                LocationContent("Lon", {}, TownResults.Found(listOf(london)), current = null, showCurrent = false, busy = false, onChoose = {})
            }
        }
        assertRowTitle("London")
    }
}
```
These pin the spec's sizes for the setup rows, which already use them, so they pass before the change; they fail if a caller draws its own text at another size. (If the person row's second line reads differently, use the text `PeopleUiTest` asserts for an Admin with a PIN.)

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/ReviewCalendarsTest.kt`, add:
```kotlin
    @Test
    fun connectionAndCalendarRowsUseTheSharedRowStyles() {
        show()
        fun style(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style
        listOf("Google Calendar · family@example.com", "Family").forEach {
            assertThat(style(it).fontSize).isEqualTo(17.sp)
            assertThat(style(it).fontWeight).isEqualTo(FontWeight.W600)
        }
        assertThat(style("Synced 2 min ago").fontSize).isEqualTo(14.sp)
    }
```
(imports: `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.unit.sp`.) Fails today: the calendar rows are 18 / 15 sp; afterwards if they leave the shared sizes.

Create `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/FontScaleTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale

/** The calendar's screens at large font scales (4d-1 §6). Real text metrics: these measure lines and ellipses. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L

    /** A long account that needs reconnecting, on the Settings page's 720 dp column. */
    @Test
    fun atFontScale15ALongAccountKeepsReconnectAndDisconnectWhole() {
        val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "the.whole.family.shared.calendar@example.co.uk"))
        val row = CalendarRow(google, "Google Calendar", "calendar_month", ConnectionHealth.NeedsSignIn, lastSyncMillis = now)
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) {
                    Box(Modifier.width(720.dp)) {
                        ReviewCalendars(
                            title = "Calendars",
                            connections = listOf(ReviewConnection(row, emptyList())),
                            people = listOf(Person.Family),
                            nowMillis = now,
                            busy = false,
                            picking = null,
                            confirming = null,
                            connectable = emptyList(),
                            actions = ReviewActions(),
                        )
                    }
                }
            }
        }
        val title = compose.onNodeWithText("Google Calendar · the.whole.family.shared.calendar@example.co.uk", useUnmergedTree = true).textLayout()
        assertThat(title.lineCount).isEqualTo(1)
        assertThat(title.isLineEllipsized(0)).isTrue()
        listOf("Reconnect", "Disconnect").forEach { label ->
            val text = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("settings_row_g1")), useUnmergedTree = true).textLayout()
            assertThat(text.isLineEllipsized(0)).isFalse()
        }
    }
}
```
Today's row already weights its title, so this passes before the change; it pins `HhListRow`'s measuring order and fails if the row measures its title before its trailing items (the buttons would be cut to "Reco…") or lets the title wrap.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :core:ui:testDebugUnitTest --tests "*ListRowAndLabelTest*" :core:setup:testDebugUnitTest --tests "*RowStylesTest*" :capability:calendar:testDebugUnitTest --tests "*ReviewCalendarsTest*" --tests "*FontScaleTest*"`
Expected: `:core:ui` fails to compile ("Unresolved reference 'HhSectionLabel'", "'HhListRow'"); `connectionAndCalendarRowsUseTheSharedRowStyles` fails on 18 sp; `RowStylesTest` and the font-scale test pass (they pin, see above).

- [ ] **Step 3: Write `HhListRow`, `HhSectionLabel` and the label style**

`Type.kt`, in `HhType` after `label`:
```kotlin
    val sectionLabel = style(13, FontWeight.W700).copy(letterSpacing = 0.5.sp)
```
`Components.kt`, append (imports: `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.Arrangement`, `Row`, `RowScope`, `Spacer`, `fillMaxWidth`, `width`, `androidx.compose.foundation.shape.RoundedCornerShape` (already), `androidx.compose.material3.Text`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.platform.testTag`, `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.heading`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.text.style.TextOverflow`, `java.util.Locale`):
```kotlin
/**
 * List rows (people, towns, connections, calendars; 4d-1 §4.2): `surf`, radius 18, padding 16 × 20, the leading item
 * 16 from the text, the second line 2 below, trailing items 12 apart, what goes below 12 under the top line. Lists put
 * 10 between rows.
 */
object ListRowTokens {
    val radius = 18.dp
    val paddingV = 16.dp
    val paddingH = 20.dp
    val leadingGap = 16.dp
    val lineGap = 2.dp
    val trailingGap = 12.dp
    val belowGap = 12.dp
    val icon = 24.dp
}

/**
 * One list row: [title] and [subtitle] one line each, cut with "…"; [trailing] keeps its size and the text takes what's
 * left. With [onClick] the whole row is a button. [below] holds anything under the top line (a calendar's Master line,
 * its read problem, its person picker).
 */
@Composable
fun HhListRow(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    subtitleColor: Color = Culvery.colors.mute,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(ListRowTokens.belowGap),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ListRowTokens.radius))
            .background(c.surf)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = ListRowTokens.paddingH, vertical = ListRowTokens.paddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(ListRowTokens.leadingGap))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ListRowTokens.lineGap)) {
                Text(
                    title, style = HhType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle, style = HhType.secondary, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(ListRowTokens.trailingGap))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ListRowTokens.trailingGap),
                    content = trailing,
                )
            }
        }
        below?.invoke(this)
    }
}

/** A section's label (4d-1 Decision 6): drawn in capitals, spoken as [text] is written ("Who"), and a heading. */
@Composable
fun HhSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.UK),
        style = HhType.sectionLabel,
        color = Culvery.colors.mute,
        modifier = modifier.semantics {
            heading()
            contentDescription = text
        },
    )
}
```

- [ ] **Step 4: Move the rows and labels**

`core/setup/src/main/java/uk/co/siland/culvery/core/setup/PeopleUi.kt`, `PersonRow` (56-88) becomes:
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
    HhListRow(
        title = name,
        modifier = Modifier.testTag("person_$name"),
        leading = { Box(Modifier.size(SetupDimens.rowDot).clip(CircleShape).background(Color(member.person.color))) },
        subtitle = "${roleName(member.role)} · ${if (member.hasPin) PIN_SET else NO_PIN}",
        trailing = {
            if (reorderable) {
                MoveButton(Icons.ARROW_UPWARD, moveUpLabel(name), "person_up_$name", onUp)
                MoveButton(Icons.ARROW_DOWNWARD, moveDownLabel(name), "person_down_$name", onDown)
            }
            HhIcon(Icons.CHEVRON_RIGHT, size = ListRowTokens.icon, tint = c.mute)
        },
        onClick = onClick,
    )
}
```
and in `MoveButton` (line 105) `size = ListRowTokens.icon` (imports for `PeopleUi.kt`: `uk.co.siland.culvery.core.ui.HhListRow`, `uk.co.siland.culvery.core.ui.ListRowTokens`; drop the ones the old row used alone).
`LocationPane.kt`, `PlaceRow` (141-159) becomes:
```kotlin
@Composable
private fun PlaceRow(label: String, ticked: Boolean, tag: String, onClick: (() -> Unit)?) {
    val tick: (@Composable RowScope.() -> Unit)? = if (ticked) ({ HhIcon(Icons.CHECK, size = ListRowTokens.icon, tint = Culvery.colors.accent) }) else null
    HhListRow(
        title = label,
        modifier = Modifier.testTag(tag).semantics(mergeDescendants = true) { selected = ticked },
        trailing = tick,
        onClick = onClick,
    )
}
```
(imports for `LocationPane.kt`: `androidx.compose.foundation.layout.RowScope`, `uk.co.siland.culvery.core.ui.HhListRow`, `uk.co.siland.culvery.core.ui.ListRowTokens`.)
`PersonEditorSheet.kt`, `Section` (245-251):
```kotlin
@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.labelGap)) {
        HhSectionLabel(label)
        content()
    }
}
```
`SetupDimens.kt`: the row block (lines 28-37) keeps only what isn't the shared row's:
```kotlin
    // List rows are ListRowTokens'. Not in the spec: 10 between rows, a 16 dp colour dot.
    val rowGap = 10.dp
    val rowDot = 16.dp
    val rowDotGap = 16.dp
```
(`rowDotGap` stays: the You step's PIN row uses it.) Delete `SetupType.label` (line 81 and its KDoc).

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ReviewCalendars.kt`, `ConnectionHeader` (207-235):
```kotlin
@Composable
private fun ConnectionHeader(row: CalendarRow, nowMillis: Long, busy: Boolean, onReconnect: (Connection) -> Unit, onDisconnect: (CalendarRow) -> Unit) {
    val c = Culvery.colors
    val needsReconnect = row.health == ConnectionHealth.NeedsSignIn
    HhListRow(
        title = serviceAndAccount(row),
        modifier = Modifier.testTag("settings_row_${row.connection.id}"),
        leading = { HhIcon(row.icon, size = CalendarDimens.settingsIcon, tint = c.ink) },
        subtitle = healthWords(row, nowMillis),
        subtitleColor = if (needsReconnect) c.danger else c.mute,
        trailing = {
            if (needsReconnect) {
                HhButton(
                    "Reconnect", { onReconnect(row.connection) }, Modifier.testTag("settings_reconnect"),
                    tone = ButtonTone.Primary, enabled = !busy, on = ButtonSurface.Surf,
                )
            }
            HhButton(DISCONNECT, { onDisconnect(row) }, Modifier.testTag("review_disconnect_${row.connection.id}"), enabled = !busy, on = ButtonSurface.Surf)
        },
    )
}
```
`SourceRow` (237-306):
```kotlin
@Composable
private fun SourceRow(
    source: StoredSource,
    people: List<Person>,
    busy: Boolean,
    picking: Boolean,
    onPick: (String) -> Unit,
    onPerson: (StoredSource, Person) -> Unit,
    onShown: (StoredSource, Boolean) -> Unit,
    onMakeMaster: (StoredSource) -> Unit,
) {
    val c = Culvery.colors
    val id = source.source.id
    // A person removed since is Family until HouseholdFollower catches up.
    val person = people.firstOrNull { it.id == source.mapping.person } ?: Person.Family
    HhListRow(
        title = source.source.name,
        modifier = Modifier.testTag("review_source_$id"),
        trailing = {
            HhChoiceChip(person.name, selected = false, tag = "review_person_$id", onClick = { onPick(sourceKey(source)) }, leading = { _ -> Dot(person) })
            Text(SHOW, style = CalendarType.subtitle, color = c.mute)
            // The master is always shown (4a design §3.9).
            HhSwitch(source.mapping.visible, { onShown(source, it) }, tag = "review_show_$id", enabled = !source.isMaster && !busy)
        },
        below = {
            if (source.isMaster) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reviewItemGap)) {
                    Text(
                        MASTER,
                        style = CalendarType.badge,
                        color = c.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(CalendarDimens.badgeRadius))
                            .background(c.accentSoft)
                            .padding(horizontal = CalendarDimens.badgePaddingH, vertical = CalendarDimens.badgePaddingV),
                    )
                    Text(NEW_EVENTS_GO_HERE, style = CalendarType.subtitle, color = c.mute)
                }
            } else if (source.source.writable) {
                HhButton(MAKE_MASTER, { onMakeMaster(source) }, Modifier.testTag("review_make_master_$id"), enabled = !busy, on = ButtonSurface.Surf)
            }
            // A calendar the service hid on its tick has no problem to show, and no Hide to offer.
            if (source.readProblem != null && source.mapping.visible) ReadProblem(source, busy, onShown)
            if (picking) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap), verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
                    people.forEach { p ->
                        val selected = p.id == source.mapping.person
                        HhChoiceChip(
                            p.name,
                            selected = selected,
                            tag = "review_pick_${p.name}",
                            onClick = { onPerson(source, p) },
                            enabled = !busy,
                            selectedColor = Color(p.color),
                            selectedInk = PersonChipInk,
                            leading = { ink -> if (selected) HhIcon(Icons.CHECK, size = ControlTokens.chipIcon, tint = ink) else Dot(p) },
                        )
                    }
                }
            }
        },
    )
}
```
`ui/EventEditorSheet.kt`, `Section` (230-241):
```kotlin
@Composable
private fun Section(label: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.sectionGap)) {
        HhSectionLabel(label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap),
            verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap),
        ) {
            chips()
        }
    }
}
```
and its callers pass `Section("Who")` (245), `Section("Day")` (279), `Section("Time")` (305), `Section("Length")` (329).
`CalendarType`: delete `sectionLabel` (89-90) and `settingsRowTitle` (104-105). `CalendarDimens` (333-350): keep `settingsIcon`, `reviewBlockGap`, `reviewItemGap`, `badgeRadius`, `badgePaddingV`, `badgePaddingH`, `connectStepWidth`, `connectStepHeight`; delete `settingsRowRadius`, `settingsRowPaddingV`, `settingsRowPaddingH`, `settingsIconGap`, `settingsStatusTop`, `reviewRowRadius`, `reviewRowPaddingV`, `reviewRowPaddingH`; the two comments become `// Settings' Calendars block (3a design §4.1): rows are ListRowTokens'; a connection's 26 dp icon leads it.` and `// Review calendars (4a design §4.5; not in the spec): blocks 12 apart; a calendar row's parts 12 apart; the Master badge \`accentSoft\`, radius 12, padding 4×10. The wizard's Connect card 380×460, about a Home card's size.`

Then:
```bash
grep -rn "SetupType.label\|CalendarType.sectionLabel\|settingsRowTitle\|settingsRow\|settingsStatusTop\|settingsIconGap\|reviewRow\|SetupDimens.row\(Radius\|PaddingV\|PaddingH\|LineGap\|Icon\)" --include=*.kt core capability
```
Expected: no output.

- [ ] **Step 5: Run the tests to see them pass**

Run the Step 2 command, then `./gradlew :core:ui:testDebugUnitTest :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest`.
Expected: PASS. (`PeopleUiTest`, `LocationPaneTest` and `ReviewCalendarsTest` find rows by their old tags; a failure there means a tag moved: fix the row.)

- [ ] **Step 6: Compare, re-record and look at the changed baselines**

First compare against the current baselines:
```bash
./gradlew :core:setup:compareRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
./gradlew :capability:calendar:compareRoborazziDebug --tests "*ReviewScreenshotTest*" --tests "*EditorScreenshotTest*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Open every `*_compare.png` listed (reference, new image and their difference side by side) and check each difference against the list below before recording. Then record:
```bash
./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
./gradlew :capability:calendar:recordRoborazziDebug --tests "*ReviewScreenshotTest*" --tests "*EditorScreenshotTest*"
git status --short -- "*.png"
```
Expected changes, and what each must show:
- `people_list_*`, `settings_people_*`: rows the same height and text sizes; the move arrows and chevron 12 apart (were 16), 12 after the text.
- `location_results_*`, `settings_location_*`: town rows unchanged in size; the tick 12 from the text's end.
- `person_new_*`, `person_error_dark`, `person_remove_dark`, `person_pin_dark`: the section labels read NAME, COLOUR, ROLE, PIN in capitals with slight tracking; nothing else moves.
- `review_*`, `settings_calendars_page_*`: the connection's line "Google Calendar · …" and calendar names a size smaller (17 sp), the health line 14 sp; calendar rows 2 dp more padding top and bottom; the Master badge line, Make master and the read-problem line now sit under the row's top line (the chip, Show and the switch alone on the top line).
- `editor_*`: unchanged (ruling 21). If an editor image changed, stop and report.
Any other changed image: stop and report.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 8: Commit**

```bash
git add core/ui core/setup capability/calendar
git commit -m "Share one list row for people, towns, connections and calendars, and one section label that reads in sentence case"
```

---

### Task 5: Tokens — light `accent`, `weather`, chip secondary text, `PersonChipInk`; `ContrastTest`'s token pairs (§5.2, §5.3, §5.5; rulings 6, 26)

**Review:** opus.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt:6-56`, `Theme.kt:34-48`, `Controls.kt:58-60` and `:146` (`SECONDARY_ALPHA`)
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/PersonPalette.kt` (`PersonChipInk` moves in)
- Modify: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/ForecastCard.kt:42, 91`, `ui/WeatherHeaderItem.kt:18, 20, 29`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventEditorSheet.kt:64` (`PersonChipInk` out), `ReviewCalendars.kt` (import)
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ContrastTest.kt` (appended), `PersonPaletteTest.kt:7-8`, `ControlsTest.kt`

**Interfaces:**
- Consumes: Task 1's `ContrastTest` (`TEXT`, `themes`, `check`, `everyButtonsLabelOnItsFill`), `contrast`, `textLayout`.
- Produces: `HhColors.weather: Color` (last field); `LightColors.accent = Color(0xFF23704F)`; `val PersonChipInk: Color` in `:core:ui` (`PersonPalette.kt`); `internal const val FAMILY_AMBER` in `PersonPaletteTest.kt`; `ContrastTest`'s `NON_TEXT = 3.0` (icon pairs). `SunAmber` and `ControlTokens.SECONDARY_ALPHA` gone.

- [ ] **Step 1: Write the failing tests**

`core/ui/src/test/java/uk/co/siland/culvery/core/ui/PersonPaletteTest.kt:7-8`: `private const val FAMILY_AMBER` becomes `internal const val FAMILY_AMBER` (ContrastTest reads it).
`ContrastTest.kt` (Task 1's class): add, below `TEXT`,
```kotlin
/** WCAG 2.x: large text (≥ 18.66 sp bold or 24 sp), icons and other meaningful parts. */
private const val NON_TEXT = 3.0
```
and append to the class:
```kotlin
    @Test
    fun inkAndMuteOnEverySurface() {
        themes.forEach { (name, t) ->
            listOf("bg" to t.bg, "surf" to t.surf, "surf2" to t.surf2, "surf3" to t.surf3, "accentSoft" to t.accentSoft, "dangerSoft" to t.dangerSoft)
                .forEach { (surface, bg) ->
                    check("$name ink on $surface", t.ink, bg, TEXT)
                    check("$name mute on $surface", t.mute, bg, TEXT)
                }
        }
    }

    @Test
    fun accentAsText() {
        themes.forEach { (name, t) ->
            listOf("bg" to t.bg, "surf" to t.surf, "surf2" to t.surf2, "accentSoft" to t.accentSoft)
                .forEach { (surface, bg) -> check("$name accent on $surface", t.accent, bg, TEXT) }
        }
    }

    @Test
    fun dangerTextIconsAndToasts() {
        themes.forEach { (name, t) ->
            listOf("bg" to t.bg, "surf" to t.surf, "surf2" to t.surf2).forEach { (surface, bg) -> check("$name danger on $surface", t.danger, bg, TEXT) }
            // Only icons draw danger on dangerSoft: the reconnect chip, the failure card, the confirm card.
            check("$name danger icon on dangerSoft", t.danger, t.dangerSoft, NON_TEXT)
            check("$name toast: bg on ink", t.bg, t.ink, TEXT)
        }
    }

    @Test
    fun theWeatherColourReadsAsAnIconAndAsText() {
        themes.forEach { (name, t) ->
            listOf("bg" to t.bg, "surf" to t.surf, "surf2" to t.surf2).forEach { (surface, bg) -> check("$name weather on $surface", t.weather, bg, TEXT) }
        }
    }

    @Test
    fun aChipFilledWithAPersonsColourReadsOnEveryColour() {
        (PersonPalette.colors + FAMILY_AMBER).forEach { colour ->
            check("chip ink on ${colour.toString(16)}", PersonChipInk, Color(colour), TEXT)
        }
    }

    @Test
    fun theEdgesStayWhereTheyAre() {
        check("light danger on dangerSoft (4.497)", LightColors.danger, LightColors.dangerSoft, 4.49)
        check("light mute on surf3 (4.705)", LightColors.mute, LightColors.surf3, 4.70)
    }
```
Button labels on their fills (`accentInk` on `accent`, `dangerInk` on `danger`, every quiet and disabled label) are Task 1's `everyButtonsLabelOnItsFill`, through `buttonColors`; they now run against the new tokens too.

`ControlsTest.kt`, add:
```kotlin
    @Test
    fun aSelectedChipsSecondaryTextIsAtFullStrength() {
        show { HhChoiceChip("Morning", selected = true, tag = "slot", onClick = {}, secondary = "09:00") }
        val colour = compose.onNodeWithText("09:00", useUnmergedTree = true).textLayout().layoutInput.style.color
        assertThat(colour).isEqualTo(DarkColors.accentInk)
    }
```
Fails today: `HhColors` has no `weather` and `PersonChipInk` isn't in `:core:ui` (compile errors); then light `accent` on `bg` is 3.71 (and on `accentSoft` 3.44; `everyButtonsLabelOnItsFill` finds `accentInk` on it 4.25), and the chip's secondary text is `accentInk` at 72 % (3.96 light, 4.28 dark with the new accent). Afterwards they fail if a token or the chip's alpha drifts back, or `PersonChipInk` changes to a colour some person's chip can't carry (the lowest today is brown, about 4.86).

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*ContrastTest*" --tests "*ControlsTest*"`
Expected: FAIL to compile ("Unresolved reference 'weather'", "'PersonChipInk'").

- [ ] **Step 3: Change the tokens**

`Colors.kt`: add `val weather: Color,` after `dangerInk` in `HhColors`; in `DarkColors` add `weather = Color(0xFFE0B85B),`; in `LightColors` set `accent = Color(0xFF23704F),` and add `weather = Color(0xFF8A6400),`; delete `SunAmber` and its KDoc (lines 55-56). Above `HhColors` add the KDoc line: `/** The theme's colours. [weather] draws the weather icons: amber at night, a darker gold that reads on the day theme's surfaces. */`
`Theme.kt`, in the `HhColors(...)` built from `target` (lines 34-48), after `dangerInk = animated(target.dangerInk),`:
```kotlin
        weather = animated(target.weather),
```
`Controls.kt`: delete `SECONDARY_ALPHA` (line 60) and shorten the KDoc above it (line 58) to "Hand-off §7: a disabled chip is drawn at 38% and stays tappable."; line 146 becomes:
```kotlin
            Text(secondary, style = ControlType.chipSecondary, color = ink, maxLines = 1)
```
`capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/ForecastCard.kt`: drop the `SunAmber` import (line 42); line 91's `tint = SunAmber` becomes `tint = c.weather`.
`ui/WeatherHeaderItem.kt`: drop the `SunAmber` import; the KDoc says "the condition in the weather colour"; line 29's `tint = SunAmber` becomes `tint = c.weather`.
`core/ui/src/main/java/uk/co/siland/culvery/core/ui/PersonPalette.kt`, after `PersonPalette` (import `androidx.compose.ui.graphics.Color`):
```kotlin

/** The label and tick on a chip filled with a person's colour: dark in both themes, as every person colour is a mid-tone. */
val PersonChipInk: Color = DarkColors.bg
```
`capability/calendar/…/ui/EventEditorSheet.kt:64`: delete `internal val PersonChipInk` (and its KDoc); `EventEditorSheet.kt` and `ReviewCalendars.kt` import `uk.co.siland.culvery.core.ui.PersonChipInk`. Nothing it draws changes.

```bash
grep -rn "SunAmber\|SECONDARY_ALPHA" --include=*.kt .
```
Expected: no output.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :core:ui:testDebugUnitTest :capability:calendar:testDebugUnitTest`
Expected: PASS (`ThemeTest`'s token equality includes `weather` through `DarkColors`/`LightColors`).

- [ ] **Step 5: Compare, re-record every module's baselines and look at them**

Every light image that shows `accent` changes, so compare and record every module. First compare against the current baselines:
```bash
./gradlew compareRoborazziDebug
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Open every `*_compare.png` listed (reference, new image and their difference side by side) and check each difference against the list below before recording. In this task each difference must be **recoloured pixels only**: green turned a deeper green, amber turned dark gold (light), a time chip's secondary text brighter; any shape, position or size difference, or any other colour changing, is a stop and report. Then record:
```bash
./gradlew recordRoborazziDebug
git status --short -- "*.png"
```
Expected changes, and what each must show:
- Every `*_light` image that shows green (primary buttons, the Today ring and "Today", Sign out, the chosen Settings page, the Master badge, toggles, chips, the PIN pad's lock badge, the connect cards' icons): the green is visibly deeper (`#23704F`); white labels on it unchanged.
- Every weather `*_light` image (`forecast_*_light`, `header_*_light`) and `home_weather_light`: the icons a darker gold, readable on white and on the grey page.
- `button_matrix_light`: the Primary row deeper green.
- Dark images: only those with a selected time chip (`editor_*_dark` that shows Morning, Afternoon or Evening selected) change, its time at full strength. Weather dark images unchanged (dark `weather` is the old amber).
Any dark image other than those, or anything that moves: stop and report.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 7: Commit**

```bash
git add core/ui capability/weather capability/calendar "*.png"
git commit -m "Deepen the light theme's green, give the weather icons a colour that reads on light surfaces, and check every colour pair's contrast"
```

---

### Task 6: Names on week chips and Coming up rows; faint affordances; the chosen Settings page in bold (§5.1, §5.2, §5.4, Decision 2; ruling 2)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt` (append `HhAlpha`)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt:222-235` (`AddHint`), `:237-257` (`EventChip`), `ComingUpCard.kt:96-114`, `Pickers.kt:220`, `CalendarType.kt` `CalendarDimens` 282-283, 323-324, 357-359 (the three alphas out)
- Modify: `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupWizard.kt:198-216` (`Dots`), `SetupDimens.kt:10-19` (`dotOutline`) and its `SetupType` (`chosenItem`), `SettingsScreen.kt:106` (`PageItem`'s label)
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ContrastTest.kt`; `core/setup/src/test/java/uk/co/siland/culvery/core/setup/SettingsScreenTest.kt`, `WizardDotsTest.kt` (create); `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekViewTest.kt:78-82`, `CardsTest.kt`, `FontScaleTest.kt`

**Interfaces:**
- Consumes: `isLight` (Task 1); `ContrastTest`'s `themes`, `check`, `TEXT`, `FAMILY_AMBER` (Tasks 1, 5); core/setup `textLayout()` (Task 4); the calendar `FontScaleTest` (Task 4).
- Produces: `object HhAlpha { const val PAST_DAY: Float = 0.62f; fun tint(colors: HhColors): Float }` (the two tint values private inside it); `SetupDimens.dotOutline = 1.5.dp`; `SetupType.chosenItem` (17 sp / 700); `CalendarDimens.CHIP_ALPHA_DARK/LIGHT`, `PAST_DAY_ALPHA` and `ADD_HINT_ALPHA` gone.

- [ ] **Step 1: Write the failing tests**

`ContrastTest.kt`, append to the class:
```kotlin
    @Test
    fun namesAndTitlesOnEveryPersonTint() {
        themes.forEach { (name, t) ->
            (PersonPalette.colors + FAMILY_AMBER).forEach { colour ->
                val tint = Color(colour).over(HhAlpha.tint(t), t.surf)
                check("$name mute on the ${colour.toString(16)} tint", t.mute, tint, TEXT)
                check("$name ink on the ${colour.toString(16)} tint", t.ink, tint, TEXT)
            }
        }
    }

    @Test
    fun theTintEdgesStayWhereTheyAre() {
        fun darkTint(colour: Long) = Color(colour).over(HhAlpha.tint(DarkColors), DarkColors.surf)
        check("dark mute on the lime tint (4.548)", DarkColors.mute, darkTint(0xFF9CC44E), 4.54)
        check("dark mute on the Family tint (4.602)", DarkColors.mute, darkTint(FAMILY_AMBER), 4.60)
    }

    @Test
    fun aPastDayReadsAtItsAlpha() {
        themes.forEach { (name, t) ->
            check("$name past day: the cell at its alpha over surf", t.ink.over(HhAlpha.PAST_DAY, t.surf), t.surf2.over(HhAlpha.PAST_DAY, t.surf), TEXT)
        }
        val light = LightColors
        check("light past day (4.830)", light.ink.over(HhAlpha.PAST_DAY, light.surf), light.surf2.over(HhAlpha.PAST_DAY, light.surf), 4.82)
    }
```
(The add hint at full `mute` and the dots' `accent` and `mute` on `bg` are pairs `inkAndMuteOnEverySurface` and `accentAsText` already hold, at 4.5; what this task changes about them is how the code draws them, which `WizardDotsTest` and the `week_*` baselines check.)

`core/setup/src/test/java/uk/co/siland/culvery/core/setup/SettingsScreenTest.kt`, add:
```kotlin
    @Test
    fun theChosenPageIsBoldAsWellAsTinted() {
        show(page("a", "Alpha", 0), page("b", "Beta", 1))
        assertThat(compose.onNodeWithText("Alpha", useUnmergedTree = true).textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W700)
        assertThat(compose.onNodeWithText("Beta", useUnmergedTree = true).textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W600)
    }
```
(import `androidx.compose.ui.text.font.FontWeight`.)
Create `core/setup/src/test/java/uk/co/siland/culvery/core/setup/WizardDotsTest.kt`:
```kotlin
package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.LightColors

/** Outline against fill tells the current step without colour (4d-1 §5.4). Real graphics: this reads pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WizardDotsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theCurrentStepIsAFilledDotAndTheOthersAreRings() {
        compose.setContent {
            CulveryTheme(dark = false) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    WizardFrame(6, 2, back = {}, forward = Forward.Next("Next", enabled = true), busy = false, onForward = {}, onSkip = {}) {}
                }
            }
        }
        fun centre(i: Int): Int {
            val pixels = compose.onAllNodesWithTag("wizard_dot")[i].captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2].toArgb()
        }
        assertThat(centre(2)).isEqualTo(LightColors.accent.toArgb())
        assertThat(centre(0)).isEqualTo(LightColors.bg.toArgb())
    }
}
```
`WeekViewTest.kt`: replace `allDayEventsSayAllDay` (78-82) with:
```kotlin
    @Test
    fun aChipNamesWhoseEventItIs() {
        show { WeekView(state()) }
        compose.onNodeWithText("09:30 · Alex").assertExists()
        compose.onAllNodesWithText("All day · Family").assertCountEquals(2)
    }
```
`CardsTest.kt`, add:
```kotlin
    @Test
    fun comingUpRowsNameWhoseEventItIs() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("09:00–17:00 · Alex").assertExists()
        compose.onNodeWithText("12:30–13:30 · Sam").assertExists()
    }
```
`FontScaleTest.kt` (calendar), add (imports: `androidx.compose.foundation.layout.size`, `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.ui.test.assertIsDisplayed`, `androidx.compose.ui.test.onNodeWithContentDescription`, `uk.co.siland.culvery.capability.calendar.SyncStatusUi`, `uk.co.siland.culvery.core.household.PersonId`, `uk.co.siland.culvery.core.plugin.LocalShellNavigator`):
```kotlin
    /** A long name on a chip in a 1116 dp week at 1.5. */
    @Test
    fun atFontScale15ALongNameIsCutBeforeTheTimeAndTheBadgeStays() {
        val max = Person(PersonId("max"), "Maximilian-Alexander", 0xFF9C7CE3)
        val swim = SampleUi.event("Swimming", "16:00–17:00", max, recurring = true)
        val week = SampleUi.week.copy(days = SampleUi.week.days.mapIndexed { i, d -> d.copy(events = if (i == 0) listOf(swim) else emptyList()) })
        val sync = SyncStatusUi(SampleUi.NOW, emptyList(), listOf("Google"), failingBeforeFirstSync = false)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = false) {
                    WithFontScale(1.5f) {
                        Box(Modifier.size(1116.dp, 724.dp)) { WeekView(WeekViewState(week, SampleUi.TODAY, sync, SampleUi.NOW)) }
                    }
                }
            }
        }
        val line = compose.onNodeWithText("16:00 · Maximilian-Alexander", useUnmergedTree = true).textLayout()
        assertThat(line.lineCount).isEqualTo(1)
        assertThat(line.isLineEllipsized(0)).isTrue()
        // An ellipsised line's visible end is where the "…" starts: after the whole time.
        assertThat(line.getLineEnd(0, visibleEnd = true)).isAtLeast("16:00".length)
        compose.onNodeWithContentDescription("Repeats", useUnmergedTree = true).assertIsDisplayed()
    }
```
Fail today: no `HhAlpha` (compile error); the chip shows only "09:30", the Coming up row only its time; the chosen page is W600; the other dots are filled `surf3` (their centre isn't `bg`). Afterwards: the tint pairs fail if a tint alpha grows (at `0x40` dark `mute` on lime is under 4.5); past days fail at 0.6 (4.499) or lower; the font-scale test fails if the chip's line can wrap, the time is cut, or the name gets its own unweighted text that pushes the badge out; the dots test fails if the outline goes back to a fill or the current dot loses `accent`; the Settings test fails if the chosen page loses its weight.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :core:ui:testDebugUnitTest --tests "*ContrastTest*" :core:setup:testDebugUnitTest --tests "*SettingsScreenTest*" --tests "*WizardDotsTest*" :capability:calendar:testDebugUnitTest --tests "*WeekViewTest*" --tests "*CardsTest*" --tests "*FontScaleTest*"`
Expected: `:core:ui` fails to compile ("Unresolved reference 'HhAlpha'"); the setup and calendar tests FAIL as above.

- [ ] **Step 3: Move the alphas and draw the names, the hint, past days, dots and the chosen page**

`Colors.kt`, append:
```kotlin

/** Alphas drawn over a surface, kept here so ContrastTest checks the values the screens draw (4d-1 §5). */
object HhAlpha {
    private const val TINT_DARK = 0x2E / 255f
    private const val TINT_LIGHT = 0x26 / 255f

    /** A past day in the date picker, the whole cell over the picker's `surf`; 60 % would be 4.499:1 in light. */
    const val PAST_DAY = 0.62f

    /** A week chip's tint: the person's colour at this alpha over `surf`. */
    fun tint(colors: HhColors): Float = if (colors.isLight) TINT_LIGHT else TINT_DARK
}
```
`CalendarDimens`: delete `PAST_DAY_ALPHA` and its KDoc (282-283), `ADD_HINT_ALPHA` and its KDoc (323-324), `CHIP_ALPHA_DARK`, `CHIP_ALPHA_LIGHT` and their KDoc (357-359).
`ui/Pickers.kt:220`: `CalendarDimens.PAST_DAY_ALPHA` becomes `HhAlpha.PAST_DAY` (import `uk.co.siland.culvery.core.ui.HhAlpha`).
`ui/WeekView.kt`, `AddHint`: KDoc "Hand-off §7: an `add` in `mute` at the foot of each column, in a tap area at least 48 dp tall."; the icon's `tint = Culvery.colors.mute`.
`ui/WeekView.kt`, `EventChip` (237-257):
```kotlin
@Composable
private fun EventChip(event: EventUi, onOpen: (EventRef) -> Unit) {
    val c = Culvery.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.chipRadius))
            .background(Color(event.person.color).copy(alpha = HhAlpha.tint(c)))
            .clickable(onClickLabel = "Open") { onOpen(event.ref) }
            .padding(horizontal = CalendarDimens.chipPaddingH, vertical = CalendarDimens.chipPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // One line, so a long name is cut before the time (4d-1 Decision 2).
            Text(
                "${event.startLabel} · ${event.person.name}",
                style = CalendarType.chipTime,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            EventBadges(event, CalendarDimens.chipBadge)
        }
        Spacer(Modifier.height(CalendarDimens.chipTitleTop))
        Text(event.title, style = CalendarType.chipTitle, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
```
(imports: `uk.co.siland.culvery.core.ui.HhAlpha`; remove `androidx.compose.ui.graphics.luminance` if unused.)
`ui/ComingUpCard.kt`, `CompactRow`'s second line (line 111):
```kotlin
            Text(
                "${event.timeLabel} · ${event.person.name}",
                style = CalendarType.small12,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
```
`core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupDimens.kt`, after `dotGap`:
```kotlin
    // The other steps' dots are 1.5 dp `mute` rings: outline against fill tells the current step without colour.
    val dotOutline = 1.5.dp
```
and in `SetupType`, after `rowTitle`:
```kotlin
    /** 17 sp / 700: the chosen Settings page, bold as well as tinted (4d-1 §5.2). */
    val chosenItem = HhType.rowTitle.copy(fontWeight = FontWeight.W700)
```
`SetupWizard.kt`, `Dots` (198-216):
```kotlin
@Composable
private fun Dots(count: Int, index: Int) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SetupDimens.dotGap, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().testTag("wizard_dots"),
    ) {
        repeat(count) { i ->
            val current = i == index
            Box(
                Modifier
                    .testTag("wizard_dot")
                    .semantics { selected = current }
                    .size(SetupDimens.dot)
                    .clip(CircleShape)
                    .then(if (current) Modifier.background(c.accent) else Modifier.border(SetupDimens.dotOutline, c.mute, CircleShape)),
            )
        }
    }
}
```
(import `androidx.compose.foundation.border`.)
`SettingsScreen.kt:106`:
```kotlin
        Text(title, style = if (chosen) SetupType.chosenItem else SetupType.rowTitle, color = if (chosen) c.accent else c.ink, maxLines = 1)
```

- [ ] **Step 4: Run the tests to see them pass**

Run the Step 2 command, then `./gradlew :core:setup:testDebugUnitTest :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`.
Expected: PASS.

- [ ] **Step 5: Compare, re-record and look at the changed baselines**

First compare against the current baselines:
```bash
./gradlew :capability:calendar:compareRoborazziDebug --tests "*WeekScreenshotTest*" --tests "*CardScreenshotTest*" --tests "*EditorScreenshotTest*" --tests "*ReviewScreenshotTest*"
./gradlew :core:setup:compareRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
./gradlew :app:compareRoborazziDebug --tests "*ShellScreenshotTest*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Open every `*_compare.png` listed (reference, new image and their difference side by side) and check each difference against the list below before recording. Then record:
```bash
./gradlew :capability:calendar:recordRoborazziDebug --tests "*WeekScreenshotTest*" --tests "*CardScreenshotTest*" --tests "*EditorScreenshotTest*" --tests "*ReviewScreenshotTest*"
./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest*"
git status --short -- "*.png"
```
Expected changes, and what each must show:
- `week_*`: each chip's first line reads "07:45 · Sam", "All day · Family" and so on, in grey, cut with "…" where the column is narrow; the tints and titles unchanged; badges still at the right. In the ones with Add event (`week_dark`, `week_light`, `week_next_*`, `week_furthest_*`) the + at each column's foot solid grey.
- `coming_up_*`, `home_calendar_*`, `home_weather_*`: each Coming up row's second line reads "09:00–17:00 · Alex" in grey, cut with "…" where needed.
- `editor_date_picker_*`: past days readable (dimmed, not ghosted); today's ring and the selected day unchanged.
- Every image with the wizard frame (`wizard_frame_*`, `welcome_*`, `location_*`, `you_*`, `people_list_*`, `done_*`, …): the current step a filled green dot, the others grey rings; nothing else moves.
- `settings_*` (core/setup) and `settings_calendars_page_*` (calendar): the chosen page's title bold.
Any other changed image: stop and report.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 7: Commit**

```bash
git add core/ui core/setup capability/calendar app
git commit -m "Name whose event it is on week chips and Coming up rows, show add hints, past dates and wizard dots clearly, and the chosen Settings page in bold"
```

---
### Task 7: The clock box and the header's width budget (§6.1, §6.2)

**Review:** opus.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Type.kt:38-42` (`HhType.clock`)
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/HomeScreen.kt:51-80` (KDoc, measure)
- Modify: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/WeatherHeaderItem.kt:31-32`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/TextLayouts.kt` (create), `ShellLayoutTest.kt`, `ShellScreenshotTest.kt:90-99` (`calendarHome` with `onAdd`)

**Interfaces:**
- Consumes: `WithFontScale` (Task 1); `WeatherHeaderItem`, `HeaderWeather`, `Condition` from `:capability:weather` (as `ShellScreenshotTest` already does).
- Produces: `home_clock` ≈ 94 dp tall; `HomeHeader` measures its items in `constraints.maxWidth − max(clock.width, date.width) − HEADER_ITEM_GAP`; `WeatherHeaderItem`'s two lines one line each with an end ellipsis. Test-only (app): `internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/uk/co/siland/culvery/shell/ui/TextLayouts.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult

/** The layout Compose drew for this text node: its style, its lines and whether it overflowed. */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}
```
Superseded: see Global Constraints and spec §6.1 (`Modifier.lineBox`).

`ShellLayoutTest.kt`, add (imports: `uk.co.siland.culvery.capability.weather.Condition`, `uk.co.siland.culvery.capability.weather.HeaderWeather`, `uk.co.siland.culvery.capability.weather.ui.WeatherHeaderItem`, `uk.co.siland.culvery.core.ui.WithFontScale`):
```kotlin
    /** Hand-off: the clock's line box is 0.9 × 104 sp; Trim.Both had made it the font's whole 135 dp. */
    @Test
    fun theClockBoxIsItsLineHeight() {
        compose.setContent { CulveryTheme(dark = true) { HomeScreen({ at }, emptyList(), emptyList()) } }
        val clock = compose.onNodeWithTag("home_clock").getUnclippedBoundsInRoot()
        assertThat(clock.height.value).isWithin(1f).of(94f)
    }

    /** The weather, then a wide item after it (a future Climate): together they'd run onto the date without a budget. */
    @Test
    fun atFontScale13HeaderItemsKeepClearOfTheClockAndDate() {
        val weather = HeaderItem("weather", 10) {
            WeatherHeaderItem(HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0))
        }
        val wide = HeaderItem("climate", 20) { Box(Modifier.size(800.dp, 60.dp)) }
        compose.setContent { CulveryTheme(dark = true) { WithFontScale(1.3f) { HomeScreen({ at }, emptyList(), listOf(weather, wide)) } } }
        val clock = compose.onNodeWithTag("home_clock").getUnclippedBoundsInRoot()
        val date = compose.onNodeWithTag("home_date").getUnclippedBoundsInRoot()
        val items = compose.onNodeWithTag("home_header_items").getUnclippedBoundsInRoot()
        assertThat(items.left.value).isAtLeast(maxOf(clock.right, date.right).value + 28f - 1f)
        val shown = compose.onNodeWithTag("home_header_weather").getUnclippedBoundsInRoot()
        assertThat(shown.width.value).isGreaterThan(0f)
        assertThat(compose.onNodeWithText("17°", useUnmergedTree = true).textLayout().hasVisualOverflow).isFalse()
        assertThat(compose.onNodeWithText("High 19° · Low 11°", useUnmergedTree = true).textLayout().hasVisualOverflow).isFalse()
    }
```
`ShellScreenshotTest.kt`, in `calendarHome` (lines 90-99) the Today card becomes `TodayCard(today, onAdd = {})`, so a Home baseline shows Today's + (hand-off H6).
Fails today: the clock box is 135 dp; the items are measured at the full width and placed over the date. Afterwards: the clock test fails if `Trim` goes back to `Both` (or Compose changes `LineHeightStyle`); the header test fails if the items get the full width again, or the weather item, measured first, is squeezed or cut. (The weather item's one-line `maxLines` is a guard for an item that does get squeezed; the `home_fs15` baseline in Task 10 shows it.)

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*ShellLayoutTest*"`
Expected: FAIL: "expected 94.0 ± 1 but was 135.x"; "items.left … expected at least …".

- [ ] **Step 3: Fix the clock box and give the items a budget**

Superseded: see Global Constraints and spec §6.1 (`Modifier.lineBox`).

`Type.kt:38-42`:
```kotlin
    val clock = style(104, FontWeight.W600, tabular = true).copy(
        letterSpacing = (-4).sp,
        lineHeight = 93.6.sp,
        // The line height is under the font's own; Trim.Both would give the ascent and descent back (a 135 dp box).
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
```
`HomeScreen.kt`: the KDoc (51-54) becomes
```kotlin
/**
 * Clock and date, the date's baseline [CLOCK_TO_DATE_BASELINES] below the clock's, as the hand-off's CSS places it; the
 * capabilities' items at the right, in the width the clock and date leave less [HEADER_ITEM_GAP], their bottom
 * [HEADER_ITEMS_BOTTOM] above the date's (4b design §4.1, 4d-1 §6.2).
 */
```
and the measure block (65-69):
```kotlin
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val clock = measurables[0].measure(loose)
        val date = measurables[1].measure(loose)
        val budget = constraints.maxWidth - maxOf(clock.width, date.width) - HEADER_ITEM_GAP.roundToPx()
        val right = measurables[2].measure(loose.copy(maxWidth = budget.coerceAtLeast(0)))
```
(the rest of the block is unchanged.)
`capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/WeatherHeaderItem.kt:31-32`:
```kotlin
            Text(degrees(weather.temperature), style = HhType.headerValue, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(highLow(weather.high, weather.low), style = HhType.secondary, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
```
(import `androidx.compose.ui.text.style.TextOverflow`.)

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :app:testDebugUnitTest :core:ui:testDebugUnitTest :capability:weather:testDebugUnitTest`
Expected: PASS, `dateBaselineSits44dpBelowClockBaseline` and `headerItemsSitAtTheRightFourAboveTheDatesBottom` included.

- [ ] **Step 5: Compare, re-record and look at the changed baselines**

First compare against the current baselines:
```bash
./gradlew :app:compareRoborazziDebug --tests "*ShellScreenshotTest*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Open every `*_compare.png` listed (reference, new image and their difference side by side) and check each difference against the list below before recording. Then record:
```bash
./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest*"
git status --short -- "*.png"
```
Expected changes, and what each must show: every `home_*` and `pin_pad_*` image (Home sits behind the pad), `home_toast_dark`: the clock's top about 21 dp higher, the date 44 dp below its baseline as before, the header's weather item still bottom-aligned 4 dp above the date's bottom and at the right edge, the grid starting about 21 dp higher with each row about 10 dp taller. `home_calendar_*` also show Today's round + left of Week. Nothing else moves; the `launcher_icon_*` images unchanged. Any other change: stop and report.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 7: Commit**

```bash
git add core/ui app capability/weather
git commit -m "Make the Home clock's box its 94 dp line height, so Home sits where the hand-off puts it, and keep header items clear of the date"
```

---

### Task 8: The weather's failed state (§6.3; rulings 18, 27, 28)

**Review:** opus.

**Files:**
- Modify: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherView.kt:13-19` (`WeatherPlace.toString`), `:33-62`, `WeatherSyncLoop.kt:33-93`, `WeatherRepository.kt:22-33`, `WeatherWords.kt:10-12`, `ui/ForecastCard.kt:59-74`
- Test: `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherViewTest.kt`, `WeatherSyncLoopTest.kt`, `WeatherRepositoryTest.kt`, `ui/ForecastCardTest.kt`, `ui/WeatherScreenshotTest.kt`
- Baselines (new): `capability/weather/src/test/screenshots/forecast_failed_dark.png`, `forecast_failed_light.png`

**Interfaces:**
- Consumes: nothing new.
- Produces (package `uk.co.siland.culvery.capability.weather`):
  - `WeatherView.Failed` (a `data object`)
  - `fun weatherView(location: HomeLocation?, stored: StoredWeather?, now: LocalDateTime, failedFor: WeatherPlace? = null): WeatherView`
  - `WeatherSyncLoop.lastFailure: StateFlow<WeatherPlace?>` — the place the last finished fetch failed for, or null once one succeeds (ruling 27).
  - `WeatherPlace.toString()` without coordinates or zone (ruling 28).
  - `WeatherRepository` internal constructor `(location: Flow<HomeLocation?>, stored: Flow<StoredWeather?>, now: Flow<LocalDateTime>, failures: Flow<WeatherPlace?> = flowOf(null))`; its `@Inject` constructor takes `syncLoop: WeatherSyncLoop` too.
  - `internal const val NO_FORECAST_YET = "No forecast yet — check the tablet’s Wi-Fi."`

- [ ] **Step 1: Write the failing tests**

`WeatherViewTest.kt`, add:
```kotlin
    private val london = WeatherPlace(LONDON)

    @Test
    fun aLocationWithNothingStoredAndAFailedFetchHasFailed() {
        assertThat(weatherView(LONDON, null, thuMorning, failedFor = london)).isEqualTo(WeatherView.Failed)
    }

    @Test
    fun aLocationWithNothingStoredAndNoFailureIsWaiting() {
        assertThat(weatherView(LONDON, null, thuMorning, failedFor = null)).isEqualTo(WeatherView.Waiting)
    }

    @Test
    fun storedWeatherShowsWhateverTheLastFetchDid() {
        assertThat(weatherView(LONDON, stored(), thuMorning, failedFor = london)).isInstanceOf(WeatherView.Ready::class.java)
        assertThat(weatherView(LONDON, stored(forecast = forecast(from = THU.minusDays(7))), thuMorning, failedFor = london))
            .isEqualTo(WeatherView.Expired)
    }

    /** A town chosen after a failure waits for its own first fetch. */
    @Test
    fun aFailureForAnotherPlaceIsStillWaiting() {
        assertThat(weatherView(LEEDS, null, thuMorning, failedFor = london)).isEqualTo(WeatherView.Waiting)
    }

    @Test
    fun aFailureForTheSameTownRenamedIsStillFailed() {
        assertThat(weatherView(LONDON.copy(name = "Westminster"), null, thuMorning, failedFor = london)).isEqualTo(WeatherView.Failed)
    }

    @Test
    fun withoutALocationAFailureStillAsksForOne() {
        assertThat(weatherView(null, null, thuMorning, failedFor = london)).isEqualTo(WeatherView.NoLocation)
    }

    /** The release logs promise no coordinates or zone (docs/setup/release.md): a place printed by mistake carries none. */
    @Test
    fun aPrintedPlaceHoldsNoCoordinatesOrZone() {
        listOf(london.toString(), stored().toString()).forEach { printed ->
            listOf("51.5", "-0.12", "Europe/London").forEach { assertThat(printed).doesNotContain(it) }
        }
    }
```
`WeatherSyncLoopTest.kt`, add:
```kotlin
    @Test
    fun theLastFailureNamesThePlaceAndASuccessClearsIt() = runTest {
        var calls = 0
        val loop = WeatherSyncLoop({
            calls++
            if (calls == 1) throw WeatherUnavailableException("offline")
        }, MutableStateFlow(london), backgroundScope)
        assertThat(loop.lastFailure.value).isNull()
        loop.start()
        runCurrent()
        assertThat(loop.lastFailure.value).isEqualTo(london)
        after(WEATHER_RETRY_MS)
        assertThat(loop.lastFailure.value).isNull()
    }

    @Test
    fun aFetchStillRunningLeavesTheLastFailureShowing() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val loop = WeatherSyncLoop({
            calls++
            if (calls == 1) throw WeatherUnavailableException("offline") else gate.await()
        }, MutableStateFlow(london), backgroundScope)
        loop.start()
        runCurrent()
        after(WEATHER_RETRY_MS)
        assertThat(calls).isEqualTo(2)
        assertThat(loop.lastFailure.value).isEqualTo(london)
        gate.complete(Unit)
        runCurrent()
        assertThat(loop.lastFailure.value).isNull()
    }
```
`WeatherRepositoryTest.kt`, add (import `kotlinx.coroutines.flow.MutableStateFlow` if missing):
```kotlin
    @Test
    fun aFailedFirstFetchShowsFailedUntilOneSucceeds() = runTest {
        stored.value = null
        val failures = MutableStateFlow<WeatherPlace?>(null)
        val withFailures = WeatherRepository(location, stored, now, failures)
        withFailures.view.test {
            assertThat(awaitItem()).isEqualTo(WeatherView.Waiting)
            failures.value = WeatherPlace(LONDON)
            assertThat(awaitItem()).isEqualTo(WeatherView.Failed)
            stored.value = stored()
            assertThat(awaitItem()).isInstanceOf(WeatherView.Ready::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }
```
This feeds the failures in directly; that the `@Inject` constructor passes `syncLoop.lastFailure` (not a constant) is checked on the emulator, Task 11 Step 6 item 7.
`ui/ForecastCardTest.kt`, add:
```kotlin
    @Test
    fun failedSaysThereIsNoForecastYetUnderItsTitle() {
        show(WeatherView.Failed)
        compose.onNodeWithText("Forecast").assertExists()
        compose.onNodeWithText("No forecast yet — check the tablet’s Wi-Fi.").assertExists()
        compose.onNodeWithText("Getting the forecast…").assertDoesNotExist()
    }
```
`ui/WeatherScreenshotTest.kt`, add:
```kotlin
    @Test fun forecastFailedDark() = card("forecast_failed_dark", true, WeatherView.Failed)
    @Test fun forecastFailedLight() = card("forecast_failed_light", false, WeatherView.Failed)
```
Fail today: `failedFor`, `WeatherView.Failed` and `lastFailure` don't exist (compile errors), and a printed `WeatherPlace` shows its coordinates and zone. Afterwards: the view tests fail if `Failed` ignores the place (Review Focus 2) or overrides stored data; the loop tests fail if the failure is recorded at the start of a fetch rather than its end, isn't cleared by a success, or isn't recorded; the repository test fails if the view isn't fed the failures; the print test fails if `toString` goes back to the data class's.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :capability:weather:testDebugUnitTest`
Expected: FAIL to compile ("Unresolved reference 'failedFor'", "'Failed'", "'lastFailure'").

- [ ] **Step 3: Record the failure and show it**

`WeatherView.kt`, in `WeatherPlace` (13-19), after `zone`:
```kotlin

    // Printed by mistake (a log, a crash message), a place gives away where the household lives.
    override fun toString(): String = "WeatherPlace(withheld)"
```
in `WeatherView`, after `Waiting`:
```kotlin

    /** A location is set, nothing stored matches it, and the last fetch for it failed. */
    data object Failed : WeatherView
```
and `weatherView` (53-62):
```kotlin
/** [failedFor]: the place the last finished fetch failed for, null once one succeeded. */
fun weatherView(location: HomeLocation?, stored: StoredWeather?, now: LocalDateTime, failedFor: WeatherPlace? = null): WeatherView {
    if (location == null) return WeatherView.NoLocation
    val data = stored.matching(location)
        ?: return if (failedFor == WeatherPlace(location)) WeatherView.Failed else WeatherView.Waiting
    val date = now.toLocalDate()
    val ahead = data.days.filter { !it.date.isBefore(date) }.sortedBy { it.date }
    val today = ahead.firstOrNull()?.takeIf { it.date == date } ?: return WeatherView.Expired
    // The latest start at or before now within the hour: a skipped hour leaves a gap, not a wrong hour.
    val hour = data.hours.filter { !it.start.isAfter(now) && now.isBefore(it.start.plusHours(1)) }.maxByOrNull { it.start }
    return WeatherView.Ready(hour, today, ahead.take(FORECAST_DAYS), data.fetchedAtMillis)
}
```
`WeatherSyncLoop.kt`: add imports `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.flow.asStateFlow`; in the class body, before `start()`:
```kotlin
    private val failure = MutableStateFlow<WeatherPlace?>(null)

    /** The place the last finished fetch failed for, null once one succeeds; a fetch still running changes nothing. */
    val lastFailure: StateFlow<WeatherPlace?> = failure.asStateFlow()
```
and `runFetch` (80-93):
```kotlin
    private suspend fun runFetch(place: WeatherPlace): Boolean {
        val ok = try {
            fetch(place)
            true
        } catch (e: CancellationException) {
            // Stops the loop only if it was really cancelled; a stray one (an internal timeout) must not.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "A weather fetch was cancelled internally")
            false
        } catch (e: Throwable) {
            // An Error too, as the calendar's loop. The type only: a message could hold the place.
            Log.w(TAG, "Weather fetch failed (${e::class.simpleName})")
            false
        }
        failure.value = if (ok) null else place
        return ok
    }
```
The class KDoc gains a last sentence: "[lastFailure] says where the last fetch failed, so the card can say a first fetch didn't get through."
`WeatherRepository.kt` (22-33):
```kotlin
@Singleton
class WeatherRepository internal constructor(
    location: Flow<HomeLocation?>,
    stored: Flow<StoredWeather?>,
    now: Flow<LocalDateTime>,
    failures: Flow<WeatherPlace?> = flowOf(null),
) : Daylight {
    @Inject
    constructor(household: HouseholdRepository, store: WeatherStore, clock: HouseholdClock, syncLoop: WeatherSyncLoop) :
        this(household.location, store.stored, clock.minutes, syncLoop.lastFailure)

    val view: Flow<WeatherView> = combine(location, stored, now, failures) { l, s, n, f -> weatherView(l, s, n, f) }
        .distinctUntilChanged()
        .retryWithBackoff { Log.w(TAG, "Couldn't read the weather (${it::class.simpleName}); retrying") }
```
(import `kotlinx.coroutines.flow.flowOf`; the KDoc's first sentence adds "and where the last fetch failed".)
`WeatherWords.kt`, after `NO_FORECAST` (line 11):
```kotlin
internal const val NO_FORECAST_YET = "No forecast yet — check the tablet’s Wi-Fi."
```
`ui/ForecastCard.kt`, in the `when (view)` (59-74), after `WeatherView.Waiting -> …`:
```kotlin
            WeatherView.Failed -> Text(NO_FORECAST_YET, style = HhType.body, color = c.mute, modifier = Modifier.testTag("weather_failed"))
```
(import `uk.co.siland.culvery.capability.weather.NO_FORECAST_YET`.) The header item needs nothing: `headerWeather` shows only `Ready`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :capability:weather:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS (`:app` builds the Hilt graph with the new constructor parameter; `WeatherStoreTest`'s equality checks on `WeatherPlace` are unaffected, as `equals` is still the data class's).

- [ ] **Step 5: Record the new baselines and look at them**

Run:
```bash
./gradlew :capability:weather:recordRoborazziDebug --tests "*WeatherScreenshotTest.forecastFailed*"
git status --short -- "*.png"
```
Expected: only the two new `forecast_failed_*` images. Each shows the Forecast card with its title and "No forecast yet — check the tablet’s Wi-Fi." in grey under it (with a curly apostrophe), laid out as `forecast_waiting_*`. Any existing image changed: stop and report.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 7: Commit**

```bash
git add capability/weather
git commit -m "Say when the first forecast for the home town couldn't be fetched, until one gets through, and never print a place's coordinates"
```

---

### Task 9: The detail sheet scrolls; the PIN pad at 1.5 (§6.4, §6.5; rulings 15, 16, 29)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/EventDetailSheet.kt:75-153` (and the confirm card's quoted title, line 137-141)
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt:217-241`, `PinPadDimens.kt:27-28` (`errorLine` out)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/SampleUi.kt` (`detailLongTitle`), `FontScaleTest.kt`, `DetailScreenshotTest.kt:30-49`; `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/TextLayouts.kt`, `FontScaleTest.kt` (create); `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt:126-151`; `core/setup` `person_pin_dark` baseline (the person sheet's PIN pad shares `PinPadFrame`)
- Baselines (new): `capability/calendar/src/test/screenshots/detail_fs15.png`, `app/src/test/screenshots/pin_pad_fs15.png`

**Interfaces:**
- Consumes: `WithFontScale` (Task 1), the calendar `FontScaleTest` and `textLayout()` (Tasks 3–4), the app `textLayout()` (Task 7).
- Produces: `SampleUi.detailLongTitle: EventDetailUi`; `DetailScreenshotTest.snap(…, fontScale: Float = 1f, …)` and `ShellScreenshotTest.snap(…, fontScale: Float = 1f, …)`, which Task 10 uses; `FontScaleTest.showDetail(mode, detail)` (calendar), which Task 10 uses; `PinPadDimens.errorLine` gone. Test-only (core/access): `internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult`.

- [ ] **Step 1: Write the failing tests**

`SampleUi.kt`, after `detailUntagged`:
```kotlin
    /** An editable event whose title runs to several lines at a large font scale. */
    val detailLongTitle = detailUntagged.copy(
        event = detailUntagged.event.copy(title = "Plumber quote call about the boiler, the upstairs radiators and the dripping tap in the downstairs loo"),
    )
```
`FontScaleTest.kt` (calendar), add (imports: `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.test.assertHeightIsEqualTo`, `androidx.compose.ui.test.onNodeWithTag`, `androidx.compose.ui.test.performScrollTo`, `uk.co.siland.culvery.capability.calendar.EventDetailUi`):
```kotlin
    private fun showDetail(mode: DetailMode, detail: EventDetailUi = SampleUi.detailLongTitle) = compose.setContent {
        CulveryTheme(dark = false) {
            WithFontScale(1.5f) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    EventDetailSheet(detail, SampleUi.household, mode, busy = false, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
    }

    private fun showDetail(mode: DetailMode, title: String) =
        showDetail(mode, SampleUi.detailLongTitle.copy(event = SampleUi.detailLongTitle.event.copy(title = title)))

    @Test
    fun atFontScale15EditStaysOnScreenAndTheBodyScrollsToItsLastRow() {
        showDetail(DetailMode.Idle)
        compose.onNodeWithTag("detail_edit").assertIsDisplayed()
        compose.onNodeWithText("Family calendar").performScrollTo().assertIsDisplayed()
    }

    /** A long title mustn't push the confirm card's buttons off the sheet. */
    @Test
    fun atFontScale15TheDeleteConfirmationsButtonsStayOnScreen() {
        showDetail(DetailMode.ConfirmingDelete)
        compose.onNodeWithTag("detail_keep").assertIsDisplayed().assertHeightIsEqualTo(60.dp)
        compose.onNodeWithTag("detail_confirm_delete").assertIsDisplayed().assertHeightIsEqualTo(60.dp)
    }

    /** A title synced from a phone can be any length; the confirm card quotes at most three lines of it. */
    @Test
    fun atFontScale15AThousandCharacterTitleStillLeavesTheConfirmationsButtons() {
        showDetail(DetailMode.ConfirmingDelete, "Boiler service ".repeat(67))
        compose.onNodeWithTag("detail_keep").assertIsDisplayed().assertHeightIsEqualTo(60.dp)
        compose.onNodeWithTag("detail_confirm_delete").assertIsDisplayed().assertHeightIsEqualTo(60.dp)
    }
```
Create `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/TextLayouts.kt`:
```kotlin
package uk.co.siland.culvery.core.access.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult

/** The layout Compose drew for this text node: its style, its lines and whether it overflowed. */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}
```
Create `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/FontScaleTest.kt`:
```kotlin
package uk.co.siland.culvery.core.access.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale

/** The PIN pad at font scale 1.5 (4d-1 §6.5). Real text metrics: these measure lines and overflow. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleTest {
    @get:Rule val compose = createComposeRule()

    private fun show(error: PinError?) = compose.setContent {
        CulveryTheme(dark = false) {
            WithFontScale(1.5f) {
                PinPadSheet("Change settings", PinReason.Generic, error = error, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
            }
        }
    }

    @Test
    fun atFontScale15CancelStaysOneWord() {
        show(null)
        val cancel = compose.onNodeWithText("Cancel", useUnmergedTree = true).textLayout()
        // Pins how it can't break, whatever Robolectric's metrics: one line that never wraps.
        assertThat(cancel.layoutInput.softWrap).isFalse()
        assertThat(cancel.layoutInput.maxLines).isEqualTo(1)
        assertThat(cancel.lineCount).isEqualTo(1)
        assertThat(cancel.hasVisualOverflow).isFalse()
    }

    @Test
    fun atFontScale15ARefusalForALongNameIsShownWhole() {
        show(PinError.NotAllowed("Maximilian-Alexander Fotheringham-Smythe"))
        assertThat(compose.onNodeWithTag("pin_error", useUnmergedTree = true).textLayout().hasVisualOverflow).isFalse()
    }

    @Test
    fun atFontScale15AMessageAppearingDoesNotMoveTheKeypad() {
        var message by mutableStateOf("")
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) { PinPadFrame("Who's this?", null, message, wrongPin = false, enabled = true, onSubmit = {}, onCancel = {}) }
            }
        }
        val before = compose.onNodeWithTag("pin_key_1").getUnclippedBoundsInRoot().top
        message = "Maximilian-Alexander Fotheringham-Smythe can't do that"
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("pin_key_1").getUnclippedBoundsInRoot().top).isEqualTo(before)
    }

    /** Cancel's label may run past its key at 1.5; a tap on the next key must still be that key. */
    @Test
    fun atFontScale15ATapAtTheZeroKeysLeftEdgeTypesZero() {
        var cancels = 0
        var submitted: String? = null
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) {
                    PinPadFrame("Who's this?", null, "", wrongPin = false, enabled = true, onSubmit = { submitted = it }, onCancel = { cancels++ })
                }
            }
        }
        compose.onNodeWithTag("pin_key_0").performTouchInput { click(Offset(2.dp.toPx(), centerY)) }
        listOf("1", "2", "3").forEach { compose.onNodeWithTag("pin_key_$it").performClick() }
        assertThat(cancels).isEqualTo(0)
        assertThat(submitted).isEqualTo("0123")
    }
}
```
`DetailScreenshotTest.kt`, `snap` (30-49) gains `fontScale: Float = 1f` after `mode`, and its `CulveryTheme` content becomes:
```kotlin
            CulveryTheme(dark = dark) {
                val sheet: @Composable () -> Unit = {
                    Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                        Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
                        Box(Modifier.align(Alignment.CenterEnd)) {
                            EventDetailSheet(detail, SampleUi.household, mode, busy = false, {}, {}, {}, {}, {}, {}, {})
                        }
                        over()
                    }
                }
                WithFontScale(fontScale, sheet)
            }
```
and add `@Test fun fontScale15() = snap("detail_fs15", false, SampleUi.detailLongTitle, fontScale = 1.5f)` (import `uk.co.siland.culvery.core.ui.WithFontScale`).
`app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`, `snap` (126-151) gains `fontScale: Float = 1f` before `overlay`, and always wraps the `Box` in `WithFontScale(fontScale) { … }` (at 1.0 it is the density the view already has: an existing image that changes is a finding about `WithFontScale`, so stop and report); add:
```kotlin
    @Test
    fun pinPadFontScale15() = snap("pin_pad_fs15", dark = false, fontScale = 1.5f) {
        PinPadSheet("Change settings", PinReason.Generic, error = PinError.WrongPin, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }
```
`DetailScreenshotTest`'s wrap is unconditional too, for the same reason.
Fail today: Edit and the confirm card's buttons are squeezed to nothing under the long title (the sheet doesn't scroll) and `performScrollTo` finds no scrolling parent; Cancel's label wraps by default (`softWrap` true, no line limit); the error line is a fixed 18 dp, so a refusal overflows it. Afterwards: the scroll and confirm tests fail if the footer goes back inside the scrolling column or the quoted title loses its three-line limit (the thousand-character title would push the buttons off); the keypad test fails if `minLines = 2` goes (a two-line message would push the keys down); the key-0 test pins that the overflowing Cancel label takes no touch from the key beside it (it passes before the change too).

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :capability:calendar:testDebugUnitTest --tests "*FontScaleTest*" :core:access:testDebugUnitTest --tests "*FontScaleTest*"`
Expected: FAIL as above.

- [ ] **Step 3: Scroll the detail sheet's body; pin its footer**

`ui/EventDetailSheet.kt`, the `HhSheet` content (lines 84-152) becomes:
```kotlin
    ) {
        // The title has no line limit, so the header scrolls with the body; only the footer stays put (4d-1 §6.4).
        Column(
            verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Header(e, onClose)
            InfoCard(detail)
            when {
                e.readOnlyReason == ReadOnlyReason.OtherCalendar || e.readOnlyReason == ReadOnlyReason.NotMaster -> {
                    val subscribed = e.readOnlyReason == ReadOnlyReason.OtherCalendar
                    Note(
                        icon = Icons.LOCK,
                        title = if (subscribed) "From ${e.sourceName} (read-only)" else "From ${e.sourceName}",
                        body = if (subscribed) {
                            "This is a subscribed calendar, so it can't be changed here."
                        } else {
                            "Change events on this calendar in ${e.serviceName} on your phone."
                        },
                    )
                }
                e.readOnlyReason == ReadOnlyReason.Recurring -> Note(
                    icon = Icons.EVENT_REPEAT,
                    title = "Repeating event",
                    body = "Edit repeating events in ${e.serviceName} on your phone.",
                )
                e.untagged -> Note(
                    icon = Icons.MOBILE,
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
                            people.forEach { person -> HhPersonChip(person.name, Color(person.color), "assign_${person.name}", enabled = !busy) { onAssign(person) } }
                        }
                    } else {
                        HhButton("Assign to…", onChoosePerson, Modifier.testTag("detail_assign"), tone = ButtonTone.Primary, enabled = !busy, on = ButtonSurface.Surf)
                    }
                }
            }
        }
        if (e.editable) {
            if (mode == DetailMode.ConfirmingDelete) {
                HhConfirmCard(
                    tag = "detail_confirm",
                    keep = "Keep event", keepTag = "detail_keep",
                    confirm = "Delete event", confirmTag = "detail_confirm_delete", confirmIcon = Icons.DELETE_FOREVER,
                    busy = busy, onKeep = onKeep, onConfirm = onConfirmDelete,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.noteIconGap)) {
                        HhIcon(Icons.DELETE, size = CalendarDimens.footerIcon, tint = c.danger)
                        Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.noteTextGap)) {
                            Text("Delete this event?", style = ControlType.confirmTitle, color = c.ink)
                            Text(
                                "“${e.title}” will be removed from ${e.serviceName} for everyone.",
                                style = CalendarType.noteBody,
                                color = c.mute,
                                // A title synced from a phone can be any length; past three lines it would push Keep off the sheet.
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
                    DeleteButton(enabled = !busy, tag = "detail_delete", onClick = onDelete)
                    HhButton(
                        "Edit", onEdit, Modifier.weight(1f).testTag("detail_edit"),
                        tone = ButtonTone.Primary, size = ButtonSize.Large, icon = Icons.EDIT, enabled = !busy, on = ButtonSurface.Bg,
                    )
                }
            }
        }
    }
```
(This is the code as Tasks 2–3 left it, moved into the scrolling column; the `Spacer(Modifier.weight(1f))` before the footer is gone, and the confirm card's quoted title is three lines at most (ruling 29). Imports: `androidx.compose.foundation.verticalScroll`, `uk.co.siland.culvery.core.ui.ShellTokens`.) The KDoc gains: "The header, info card and note scroll; the footer stays at the bottom."

- [ ] **Step 4: Let the PIN pad's Cancel overflow and its message take two lines**

`core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt:217-224`:
```kotlin
                Text(
                    message,
                    style = PinPadType.error,
                    color = c.danger,
                    // Two lines kept at every font scale, so the keypad doesn't jump when a message appears.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("pin_error"),
                )
```
`PinPad.kt:232-241` (the Cancel key):
```kotlin
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .testTag("pin_cancel")
                                .size(PinPadDimens.key)
                                .clickable(
                                    interactionSource = null,
                                    indication = ripple(bounded = false, radius = PinPadDimens.key / 2),
                                    onClick = onCancel,
                                ),
                        ) {
                            // Not clipped to the key: at a large font scale the word runs past the circle rather than breaking.
                            Text(
                                "Cancel",
                                style = PinPadType.cancel,
                                color = c.ink,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                            )
                        }
```
(imports: `androidx.compose.foundation.layout.wrapContentWidth`, `androidx.compose.material3.ripple`, `androidx.compose.ui.text.style.TextOverflow`.) `PinPadDimens.kt`: delete `errorLine` and its comment (27-28).

- [ ] **Step 5: Run the tests to see them pass**

Run the Step 2 command, then `./gradlew :capability:calendar:testDebugUnitTest :core:access:testDebugUnitTest :app:testDebugUnitTest`.
Expected: PASS (`PinPadTest` still taps `pin_cancel` and reads `pin_error`).

- [ ] **Step 6: Compare, re-record and look at the changed baselines**

First compare against the current baselines:
```bash
./gradlew :capability:calendar:compareRoborazziDebug --tests "*DetailScreenshotTest*"
./gradlew :app:compareRoborazziDebug --tests "*ShellScreenshotTest*"
./gradlew :core:setup:compareRoborazziDebug --tests "*SetupScreenshotTest.personPin*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Open every `*_compare.png` listed (reference, new image and their difference side by side) and check each difference against the list below before recording. Then record:
```bash
./gradlew :capability:calendar:recordRoborazziDebug --tests "*DetailScreenshotTest*"
./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest*"
./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest.personPin*"
git status --short -- "*.png"
```
Expected changes, and what each must show:
- New `detail_fs15`: the light sheet at 1.5, the long title over several lines at the top, the footer's Delete and Edit whole at the bottom.
- New `pin_pad_fs15`: Cancel on one line (it may run past its circle), "Wrong PIN — try again" whole above the keypad.
- `pin_pad_*`, `detail_pin_*`, and core/setup's `person_pin_dark` (the choose-a-PIN pad shares the frame): the error line now two lines tall, so the card is taller and the keypad sits about 19 dp lower in it; in `pin_pad_wrong_dark` the message whole.
- The other `detail_*` images: unchanged (the sheet's content is shorter than the screen, so nothing scrolls and the footer is where it was). If one changed, stop and report.
Any other change: stop and report.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar core/access core/setup app
git commit -m "Scroll the event sheet's details above a fixed footer, and keep the PIN pad's Cancel and messages whole at large font sizes"
```

---

### Task 10: Fixed sizes become minimums; the status bar; the `*_fs15` baselines (§6.6; rulings 7, 8, 20)

**Review:** sonnet.

**Files:**
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/StatusBar.kt:29-49`
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Controls.kt:112-149` (`HhChoiceChip`)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt:115`, `EventDetailSheet.kt` (`InfoRow`'s label, line 232 before Task 9), `EventEditorSheet.kt:202-210` (summary)
- Modify: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/ForecastCard.kt:78-96`, `ui/WeatherDimens.kt:11-17`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt`, `ShellScreenshotTest.kt`; `core/ui/src/test/java/uk/co/siland/culvery/core/ui/FontScaleTest.kt`; `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/FontScaleTest.kt`, `WeekScreenshotTest.kt:36-50`; `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/WeatherScreenshotTest.kt`, `TemperatureFontScaleTest.kt` (create)
- Baselines (new): `app/src/test/screenshots/home_fs15.png`, `capability/calendar/src/test/screenshots/week_fs15.png`, `capability/weather/src/test/screenshots/forecast_fs15.png`

**Interfaces:**
- Consumes: `WithFontScale` (Task 1), each module's `textLayout()`, `showDetail` (Task 9), `ShellScreenshotTest.snap(fontScale = …)` (Task 9).
- Produces: `StatusBar` tagged `status_bar`, `heightIn(min = 30.dp)`; `WeatherDimens.temperatureGap = 8.dp`; `@Composable internal fun TemperaturePair(high: String, low: String)` in `ForecastCard.kt`; `HhChoiceChip` `heightIn(min = ControlTokens.chipHeight)`, label up to two lines with an ellipsis.

- [ ] **Step 1: Write the failing tests**

`ShellLayoutTest.kt`, add (imports: `androidx.compose.ui.geometry.Offset`, `androidx.compose.ui.test.assertHeightIsEqualTo`, `androidx.compose.ui.test.click`, `androidx.compose.ui.test.performTouchInput`, `com.google.common.truth.Truth.assertWithMessage`):
```kotlin
    /** A 48 dp touch target mustn't grow the bar: that would push Home down. */
    @Test
    fun theStatusBarIs30DpTallAtFontScale1() {
        statusBar(SessionUi("Alex", "Admin"))
        compose.onNodeWithTag("status_bar").assertHeightIsEqualTo(30.dp)
    }

    @Test
    fun atFontScale15TheStatusBarGrowsToHoldItsText() {
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(1.5f) {
                    StatusBar({ at }, dark = true, previewing = false, session = SessionUi("Alex", "Admin"), onSignOut = {}, onToggleThemePreview = {})
                }
            }
        }
        listOf("Sign out", "Auto", "Alex · Admin").forEach {
            assertWithMessage(it).that(compose.onNodeWithText(it, useUnmergedTree = true).textLayout().hasVisualOverflow).isFalse()
        }
    }

    /** Compose takes a touch up to 48 dp around a smaller clickable; Sign out's text is about 28 dp tall. */
    @Test
    fun signOutTakesATouchJustBelowItsText() {
        var signedOut = false
        statusBar(SessionUi("Alex", "Admin")) { signedOut = true }
        compose.onNodeWithTag("status_sign_out").performTouchInput { click(Offset(centerX, height + 8.dp.toPx())) }
        assertThat(signedOut).isTrue()
    }
```
`core/ui/src/test/java/uk/co/siland/culvery/core/ui/FontScaleTest.kt`, add (imports: `androidx.compose.ui.test.getUnclippedBoundsInRoot`, `androidx.compose.ui.test.onNodeWithText`):
```kotlin
    @Test
    fun atFontScale15AChipsLongLabelTakesTwoLinesAndTheChipGrowsToHoldThem() {
        val label = "Saturday morning at the leisure centre pool"
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) { Box(Modifier.width(220.dp)) { HhChoiceChip(label, selected = false, tag = "chip", onClick = {}) } }
            }
        }
        val layout = compose.onNodeWithText(label, useUnmergedTree = true).textLayout()
        assertThat(layout.lineCount).isEqualTo(2)
        assertThat(layout.isLineEllipsized(1)).isTrue()
        val chip = compose.onNodeWithTag("chip").getUnclippedBoundsInRoot()
        val text = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(text.bottom.value).isAtMost(chip.bottom.value)
    }
```
`capability/calendar/…/ui/FontScaleTest.kt`, add (imports: `androidx.compose.ui.test.hasAnyAncestor`, `androidx.compose.ui.test.hasTestTag`, `androidx.compose.ui.test.hasText` if missing, `com.google.common.truth.Truth.assertWithMessage`, `java.time.LocalTime`, `java.time.ZoneId`, `uk.co.siland.culvery.capability.calendar.EventForm`):
```kotlin
    @Test
    fun atFontScale15ALaterWeeksTitleGivesWayToThisWeek() {
        val sync = SyncStatusUi(SampleUi.NOW, emptyList(), listOf("Google"), failingBeforeFirstSync = false)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = false) {
                    WithFontScale(1.5f) {
                        Box(Modifier.size(800.dp, 724.dp)) {
                            WeekView(WeekViewState(SampleUi.weekAhead(3), SampleUi.TODAY, sync, SampleUi.NOW, weeksAhead = 3), onAdd = {})
                        }
                    }
                }
            }
        }
        assertThat(compose.onNodeWithText("In 3 weeks", useUnmergedTree = true).textLayout().lineCount).isEqualTo(1)
        val thisWeek = compose.onNode(hasText("This week") and hasAnyAncestor(hasTestTag("week_this_week")), useUnmergedTree = true).textLayout()
        assertThat(thisWeek.isLineEllipsized(0)).isFalse()
    }

    @Test
    fun atFontScale15TheDetailsLabelsAreWhole() {
        showDetail(DetailMode.Idle)
        listOf("When", "For", "Created by", "Calendar").forEach {
            assertWithMessage(it).that(compose.onNodeWithText(it, useUnmergedTree = true).textLayout().hasVisualOverflow).isFalse()
        }
    }

    @Test
    fun atFontScale15TheEditorSummaryTakesTwoLinesBeforeCuttingTheName() {
        val max = Person(PersonId("max"), "Maximilian-Alexander Fotheringham-Smythe", 0xFF9C7CE3)
        val form = EventForm(EventForm.Mode.New, SampleUi.TODAY, LocalTime.of(11, 54), ZoneId.of("Europe/London"), signedIn = null, preselectedDay = null)
        form.chooseWho(max.id)
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                        EventEditorSheet(
                            form = form, people = SampleUi.household + max, childOnly = null, busy = false, failure = null,
                            picker = EditorPicker.None, onClose = {}, onSave = {}, onDelete = {}, onRefusedWho = {}, onPicker = {},
                            focusTitleOnOpen = false,
                        )
                    }
                }
            }
        }
        val summary = compose.onNodeWithTag("editor_summary").textLayout()
        assertThat(summary.lineCount).isEqualTo(2)
        assertThat(summary.isLineEllipsized(1)).isFalse()
    }
```
Fail today: no `status_bar` tag; at 1.5 Sign out overflows the fixed 30 dp; the chip is one line, clipped, in a fixed 48 dp; the week title takes the room first and squeezes This week until its label ends in "…"; "Created by" overflows its fixed 104 dp, one line; the summary is one line, ellipsised. Afterwards: the 30 dp test fails if anything grows the bar at 1.0 (as `minimumInteractiveComponentSize()` would); the touch test fails if Sign out's click moves to a node Compose doesn't expand (one inside a clipping parent smaller than the touch), and passes today by design: it pins ruling 8.

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/TemperatureFontScaleTest.kt` (the forecast row clears its children's semantics for TalkBack, so the temperatures are tested as their own part):
```kotlin
package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale

/** Real text metrics: this measures lines and positions. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TemperatureFontScaleTest {
    @get:Rule val compose = createComposeRule()

    private fun lines(node: SemanticsNodeInteraction): Int {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().lineCount
    }

    @Test
    fun atFontScale15ColdTemperaturesStayOneLineAndApart() {
        compose.setContent { CulveryTheme(dark = false) { WithFontScale(1.5f) { TemperaturePair("-12°", "-23°") } } }
        val high = compose.onNodeWithText("-12°")
        val low = compose.onNodeWithText("-23°")
        assertThat(lines(high)).isEqualTo(1)
        assertThat(lines(low)).isEqualTo(1)
        assertThat(high.getUnclippedBoundsInRoot().right.value + 8f).isAtMost(low.getUnclippedBoundsInRoot().left.value + 0.5f)
    }
}
```
Fails today: `TemperaturePair` doesn't exist; afterwards if a temperature can wrap (a fixed 44 dp with soft wrap breaks "-12°" at 1.5) or the 8 dp gap goes (high and low run together).

`WeatherScreenshotTest.kt`, add (imports: `uk.co.siland.culvery.core.ui.WithFontScale`):
```kotlin
    private val cold = READY.copy(days = READY.days.map { it.copy(high = it.high - 25, low = it.low - 23) })

    @Test
    fun forecastFontScale15() = snap("forecast_fs15", false) {
        WithFontScale(1.5f) { Box(Modifier.size(REGULAR_W, REGULAR_H)) { ForecastCard(cold, cold.fetchedAtMillis) } }
    }
```
`WeekScreenshotTest.kt`: `snap` (36-50) gains `fontScale: Float = 1f` after `weeksAhead` and always wraps its `Box` in `WithFontScale(fontScale) { … }`, as `DetailScreenshotTest` does (Task 9; an existing image that changes is a stop); add `@Test fun weekFontScale15() = snap("week_fs15", false, fresh, canAdd = true, fontScale = 1.5f)`.
`ShellScreenshotTest.kt`, add `@Test fun homeFontScale15() = snap("home_fs15", dark = false, state = weatherHome(dark = false), fontScale = 1.5f)`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew --continue :app:testDebugUnitTest --tests "*ShellLayoutTest*" :capability:weather:testDebugUnitTest --tests "*TemperatureFontScaleTest*" :core:ui:testDebugUnitTest --tests "*FontScaleTest*" :capability:calendar:testDebugUnitTest --tests "*FontScaleTest*"`
Expected: FAIL as above (`signOutTakesATouchJustBelowItsText` passes: see above).

- [ ] **Step 3: Make the fixed sizes minimums**

`app/…/shell/ui/StatusBar.kt`: add `private val STATUS_BAR_MIN_HEIGHT = 30.dp` above `StatusBar`; its KDoc gains "Its controls take a touch up to 48 dp around them (Compose's minimum touch target), so the bar stays 30 dp unless its text grows."; the Row's modifier (line 48):
```kotlin
        modifier = Modifier.testTag("status_bar").fillMaxWidth().heightIn(min = STATUS_BAR_MIN_HEIGHT).padding(horizontal = 20.dp),
```
(import `androidx.compose.foundation.layout.heightIn`; drop `height` if unused.)
`core/ui/…/Controls.kt`, `HhChoiceChip`: `.height(ControlTokens.chipHeight)` (line 137) becomes `.heightIn(min = ControlTokens.chipHeight)`, the KDoc's "48 dp" becomes "at least 48 dp; a long label takes two lines, then ends in …", and the label (line 144):
```kotlin
        Text(label, style = ControlType.chip, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
```
(imports `androidx.compose.foundation.layout.heightIn`, `androidx.compose.ui.text.style.TextOverflow`.)
`capability/calendar/…/ui/WeekView.kt:115`:
```kotlin
                        Text(
                            weekTitle(state.weeksAhead),
                            style = CalendarType.weekTitle,
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
```
`ui/EventDetailSheet.kt`, `InfoRow`'s label:
```kotlin
        Text(label, style = CalendarType.infoLabel, color = c.mute, maxLines = 2, modifier = Modifier.widthIn(min = CalendarDimens.infoLabelWidth))
```
(import `androidx.compose.foundation.layout.widthIn`; `CalendarDimens`' info-card comment says "a label column at least 104 dp".)
`ui/EventEditorSheet.kt`, the summary (202-210): `maxLines = 2,` (the comment above it stays).
`capability/weather/…/ui/WeatherDimens.kt`, the row block becomes:
```kotlin
    // Title → first row 10; rows at least 44 tall, so three and the age line fit the 279 dp REGULAR cell; a 30 dp icon
    // (§4.2) 12 from the temperatures, each at least 44 wide, right-aligned, 8 apart so they never run together.
    val titleGap = 10.dp
    val rowHeight = 44.dp
    val rowIcon = 30.dp
    val rowIconGap = 12.dp
    val temperatureWidth = 44.dp
    val temperatureGap = 8.dp
```
`ui/ForecastCard.kt`, `ForecastRow` (78-96):
```kotlin
@Composable
private fun ForecastRow(day: DailyWeather, isToday: Boolean) {
    val c = Culvery.colors
    val description = rowDescription(day, isToday)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeatherDimens.rowHeight)
            .testTag("forecast_row")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(dayLabel(day, isToday), style = HhType.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
        HhIcon(weatherIcon(day.condition, night = false), size = WeatherDimens.rowIcon, tint = c.weather)
        Spacer(Modifier.width(WeatherDimens.rowIconGap))
        TemperaturePair(degrees(day.high), degrees(day.low))
    }
}

/** A day's high and low, each on one line, at least 44 dp wide and right-aligned, 8 dp apart so they never run together. */
@Composable
internal fun TemperaturePair(high: String, low: String) {
    val c = Culvery.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Temperature(high, c.ink)
        Spacer(Modifier.width(WeatherDimens.temperatureGap))
        Temperature(low, c.mute)
    }
}

@Composable
private fun Temperature(text: String, color: Color) {
    Text(
        text,
        style = HhType.rowTitle,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.widthIn(min = WeatherDimens.temperatureWidth),
    )
}
```
(imports: `androidx.compose.foundation.layout.heightIn`, `widthIn`, `androidx.compose.ui.graphics.Color`; drop `height` if unused.)

- [ ] **Step 4: Run the tests to see them pass**

Run the Step 2 command, then `./gradlew :core:ui:testDebugUnitTest :capability:calendar:testDebugUnitTest :capability:weather:testDebugUnitTest :app:testDebugUnitTest`.
Expected: PASS.

- [ ] **Step 5: Compare, record and re-record the baselines and look at them**

First compare against the current baselines:
```bash
./gradlew :app:compareRoborazziDebug --tests "*ShellScreenshotTest*"
./gradlew :capability:calendar:compareRoborazziDebug --tests "*WeekScreenshotTest*" --tests "*EditorScreenshotTest*" --tests "*ReviewScreenshotTest*" --tests "*DetailScreenshotTest*"
./gradlew :capability:weather:compareRoborazziDebug --tests "*WeatherScreenshotTest*"
./gradlew :core:setup:compareRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
find . -path "*/build/outputs/roborazzi/*_compare.png" -mmin -30
```
Open every `*_compare.png` listed (reference, new image and their difference side by side) and check each difference against the list below before recording. Then record:
```bash
./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest*"
./gradlew :capability:calendar:recordRoborazziDebug --tests "*WeekScreenshotTest*" --tests "*EditorScreenshotTest*" --tests "*ReviewScreenshotTest*" --tests "*DetailScreenshotTest*"
./gradlew :capability:weather:recordRoborazziDebug --tests "*WeatherScreenshotTest*"
./gradlew :core:setup:recordRoborazziDebug --tests "*SetupScreenshotTest*" --tests "*SettingsScreenshotTest*"
git status --short -- "*.png"
```
Expected changes, and what each must show:
- New `home_fs15`: light Home at 1.5: the status bar's text whole (the bar a little taller), the clock the same size as at 1.0 (SDK 35 caps it), the date larger, the header's weather clear of the date, the Forecast rows' temperatures apart.
- New `week_fs15`: light week at 1.5: "This week" whole, chips' first lines cut with "…", nothing overlapping.
- New `forecast_fs15`: rows with "-6°" and "-12°" (and the others), each on one line, high and low 8 dp apart.
- `forecast_ready_*`, `forecast_age_*`, `home_weather_*`: the high temperature 8 dp further left; nothing else moves.
- `detail_fs15` (Task 9's): the label column wider where "Created by" needs it, the label whole, the values moved right by as much.
- `pin_pad_fs15` (Task 9's): the status bar behind the pad a little taller ("Auto" no longer fits 30 dp at 1.5), Home behind it a little lower; the pad itself unchanged.
- Chip images (`editor_*`, `review_person_picker_dark`, the person sheet's role chips `person_*`): unchanged at 1.0 (`heightIn` gives the same 48 dp). If one changed, stop and report.
Any other change: stop and report.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 7: Commit**

```bash
git add app core/ui capability/calendar capability/weather core/setup
git commit -m "Let the status bar, forecast rows, detail labels, chips, the week title and the editor summary grow or shorten at large font sizes instead of clipping"
```

---

### Task 11: `release.md`, README, follow-ups; every changed baseline shown; the emulator walk-through (§6.7, §7; rulings 4, 22)

**Review:** sonnet (Steps 1–5 by the implementer; Steps 6–7 by the controller with the user).

**Files:**
- Modify: `docs/setup/release.md:55-65` (§6), `README.md:26, 78, 171`, `docs/superpowers/plans/2026-09-23-plan1-followups.md:9, 34, 79, 91, 129, 150` and a new section before "## Feature ideas"
- Modify (by the controller, after the walk-through): the follow-ups file again, if the user defers anything

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only.

- [ ] **Step 1: Make device owner the wall setup in `docs/setup/release.md`**

Replace §6's heading and first sentence (lines 55-57) with:
```markdown
## 6. Device owner (the wall setup)

This is how the wall tablet should be set up. As device owner, Culvery allowlists itself for lock-task, so pinning needs no confirmation and can't be undone with Back + Overview. Google Play services is allowed in too, but only while Google's account chooser is open during Connect or Reconnect. It needs a **freshly reset tablet with no accounts on it yet**: set the tablet up without signing in to Google, install the release, then:
```
(the code block and the next paragraph stay). Replace the section's last paragraph ("Add the family's Google account afterwards … Without it, screen pinning works as described in the README's Kiosk section.") with:
```markdown
Add the family's Google account afterwards (Settings › Accounts). Undoing device owner needs a factory reset.

Without device owner, Culvery runs in screen pinning, and two moments leave it unpinned: Android's "Choose home app" dialog, and Google's account chooser during Connect or Reconnect. Both need an Admin: Choose home app while an Admin is signed in, Connect or Reconnect with a fresh Admin PIN. A Home or Overview press there leaves Culvery unpinned until it next comes to the front, so sign out when done. This is accepted for screen pinning; there is no re-pin timer.
```
Then `grep -n "(optional)" docs/setup/release.md README.md` — expected: no line about device owner.

- [ ] **Step 2: Update the README**

1. Line 26 (the weather paragraph), after "…the old town's weather is never shown." add: "If the first forecast for the home can't be fetched, the card says \"No forecast yet — check the tablet’s Wi-Fi.\" until a fetch gets through."
2. Line 78 (adding a capability), after "(draw them with `HhSheet`)." add: "Draw buttons with `HhButton` (say what it sits on: `on = ButtonSurface.Bg` on a page or a sheet), list rows with `HhListRow` and section labels with `HhSectionLabel`, all in `:core:ui`, so the role, the 48 dp target and the contrast come with them."
3. Line 171 (Kiosk mode's last paragraph) becomes:
```markdown
The wall tablet should be set up with Culvery as device owner, on a freshly reset tablet (`docs/setup/release.md` §6): it then pins with no confirmation and no exit gesture, and stays in lock-task while Google's account chooser is open. Undoing that needs a factory reset. With screen pinning instead, Culvery is unpinned while Android's Choose home app dialog (reached while an Admin is signed in) or Google's account chooser (a fresh Admin PIN) shows, and a Home or Overview press there leaves it unpinned until it next comes to the front; that gap is accepted, with no re-pin timer, so an Admin should sign out when done.
```
Read the Kiosk section through once.

- [ ] **Step 3: Update the follow-ups**

In `docs/superpowers/plans/2026-09-23-plan1-followups.md`, delete these items (4d-1 did them or settled them):
- line 9, "Home header: the gap between the clock and the date is about 55 dp…" (the stale clock-gap item; X5 records why);
- line 34, "The event detail sheet doesn't scroll…";
- line 79, "The calendar's DeleteButton keeps its danger text while disabled…";
- line 91, "Header items could overlap the clock or date if they grow wide…";
- line 129, "The unpinned window behind Android's home-role dialog…".
If a heading is left with no items, delete the heading too. In "## From the 4d triage (2026-10-06)" (line 150), after its first sentence add: "4d-1 is done (`docs/superpowers/plans/2026-10-06-culvery-04d1-shared-parts-contrast.md`)."
Before "## Feature ideas (2026-10-02, after v1)" add:
```markdown
## From Plan 4d-1 (deferred)

**For Plan 4d-2 (TalkBack, timeouts, words)**
- The Forecast row clears its children's semantics, so its day label and icon are checked only by baselines (the temperatures have `TemperatureFontScaleTest`). D26 rewrites those semantics (`ForecastCard.kt`).
- `WeatherRepositoryTest` feeds the failures in directly; that the `@Inject` constructor passes `WeatherSyncLoop.lastFailure` is checked only on the emulator (walk-through item 7).
- `HhCloseButton`, the week's ‹ ›, the date picker's ‹ › and the time steppers are `surf2` on `bg` or `surf` with no role of their own; on light `bg` the close button's fill is 1.04:1 (its `ink` icon carries it). Give them `HhButton`'s quiet fill and `Role.Button` in D15's pass (`Shell.kt`, `Pickers.kt`).
- The week header's reconnect chip stays its own composable (`dangerSoft`; no `HhButton` tone fits). It has `Role.Button`; its label is D15's.

**For the end-of-v1 design walk-through (the user's own)**
- Calendars review: a calendar's Master line, Make master and read problem now sit under its top line (`HhListRow`'s `below`); People's move arrows and chevron are 12 dp apart (W31, W27).
- `HhButton` and `HhChoiceChip` grow with their text at large font scales but have no vertical padding of their own; a two-line chip at 1.5 runs close to its edges.
- Light `danger` on `dangerSoft` is 4.497:1: fine for the icons drawn there today; `danger` text on `dangerSoft` would need a darker `danger` (`ContrastTest` pins it).

**For Plan 4e (the device)**
- The status bar's Sign out and theme toggle rely on Compose's 48 dp touch-target expansion rather than 48 dp of layout (4d-1 ruling 8); check on the SM-T510 that a touch just outside them lands.
```

- [ ] **Step 4: Check every baseline is current, and list what changed**

```bash
./gradlew recordRoborazziDebug
git status --short -- "*.png"
```
Expected: no output (every task left its baselines current). Any changed image: stop and report; don't commit it.
```bash
git diff --name-status 84560bd -- "*.png" > "$TMP/culvery-4d1-baselines.txt"
wc -l "$TMP/culvery-4d1-baselines.txt"
```
Keep the list for Step 6.

- [ ] **Step 5: Run the gate and commit**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.
```bash
git add docs/setup/release.md README.md docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Document device owner as the wall setup, the shared button, row and label, and what 4d-1 took from the follow-ups"
```
**Stop and report** Steps 1–5 with the baseline list; the implementer does not go on.

- [ ] **Step 6: STOP — the controller runs the walk-through and the USER CHECKPOINT with the user**

The controller drives the **already-running API 35 Google Play emulator `emulator-5554`** with `adb` and screenshots; don't start another. **Never clear Culvery's data or wipe the emulator without the user's explicit yes** (see item 1a): the walk-through runs on the household already there. Debug build, light theme, as the live audit. To go offline use `adb -s emulator-5554 shell svc wifi disable` and `svc data disable` (`enable` to come back), **never airplane mode** (ruling 22). The user types every PIN; the controller never types one. Ask the user before each item that changes the household's data (adding an event, changing the home location), and undo it afterwards as the user prefers. At each numbered item take `adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4d1-<n>.png"` and send the images with the report.

Before starting:
```bash
adb devices
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug
adb -s emulator-5554 shell settings put system font_scale 1.0
adb -s emulator-5554 shell am start -n uk.co.siland.culvery/.MainActivity
```
Expected: `emulator-5554 device`; the install succeeds; Culvery opens. Tap the status bar's theme label until it reads "Day" (the light theme). Fonts: `adb -s emulator-5554 shell settings put system font_scale 1.3` (or `1.5`), and back to `1.0` at the end.

1. **Buttons (Tasks 1–3).** The wizard isn't reached without clearing the household, so check it from the baselines: send `wizard_frame_light`, `welcome_light`, `people_list_light` and `done_light` (Back, Skip and Next 48 dp pills; Back white on the grey page; a disabled Next white with a grey label). On the household as it is, Settings (the user's PIN): Close at the left column's foot; Kiosk's Exit kiosk and Choose/Change home app white pills; People › Add person; a person's sheet: Set PIN white, Remove person white with a red label, Save person green; Remove person › the confirm card's Keep white, Remove person red. Calendar tab: Add event, ‹ ›, a later week's This week (white); each column's + solid grey. Home: Today's + and Week the same 48 dp height. An event's sheet: Delete white with red text, Edit green; with the user's PIN, Delete › Keep event / Delete event (then Keep event). The add sheet: Pick date… › the picker's Cancel 60 dp; Pick time… › Cancel and Set time 60 dp.
   1a. **Only if the user asks to see the wizard live, and says yes to this exact question:** "This clears Culvery's data on the emulator: its people and PINs, the Google connection, the calendars' settings, the home location and the stored weather. Setup starts again and you'd reconnect Google afterwards. Clear it?" Then `adb -s emulator-5554 shell pm clear uk.co.siland.culvery`, start it, and walk the wizard as above. Without a clear yes, skip 1a.
2. **Rows and labels (Task 4).** Settings › People, Home location and Calendars: rows the same text sizes; a calendar's Master badge line under its top line. The person sheet's labels NAME, COLOUR, ROLE, PIN; the add sheet's WHO, DAY, TIME, LENGTH. `adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml && adb -s emulator-5554 shell cat /sdcard/ui.xml | grep -o 'content-desc="Who"'` with the add sheet open: prints `content-desc="Who"`.
3. **Colours (Task 5).** The green is deeper (Next, Add event, Sign out, the Master badge, the chosen Settings page); the weather icons a dark gold on Home and in the header; a selected time chip's time at full white.
4. **Names (Task 6).** Week chips read "09:30 · Alex", "All day · Family"; Coming up rows "… · Sam"; long names end in "…".
5. **Faint parts (Task 6).** Week add hints solid grey; the date picker's past days readable; the wizard's dots (from item 1's baselines): the current one filled green, the others grey rings; Settings' chosen page bold.
6. **Clock and header (Task 7).** Home at 1.0: compare with a screenshot from before this plan (`app/src/test/screenshots/home_weather_light.png` at 84560bd, `git show 84560bd:app/src/test/screenshots/home_weather_light.png > "$TMP/before.png"`): the clock and the grid about 21 dp higher. At 1.3 and 1.5: the header's weather never touches the date.
7. **Weather failure (Task 8; also the only check that the app wires `WeatherSyncLoop.lastFailure` into the card).** Ask first: it changes the home location. Online, Settings › Home location, search "Edinburgh" and wait for the results; then go offline (`svc wifi disable; svc data disable`) and tap Edinburgh. Close Settings: the Forecast card says "No forecast yet — check the tablet’s Wi-Fi." and the header shows no weather. Online again (`enable`): within 5 minutes the card shows Edinburgh's forecast. Set Home location back to what it was.
8. **Detail and PIN pad (Task 9).** At 1.5, with the user's yes (it adds an event to the household's calendar; delete it afterwards): add an event with a long title (`adb -s emulator-5554 shell input text "Plumber%squote%scall%sabout%sthe%sboiler%sand%sthe%sradiators%supstairs"`), open it: the body scrolls, Delete and Edit stay at the bottom; Delete › the confirm card's buttons on screen (then Keep event). The PIN pad (Settings): Cancel on one line; a wrong PIN shows "Wrong PIN — try again" whole and the keypad doesn't move when it appears.
9. **Minimums (Task 10).** At 1.5: the status bar shows Sign out and the theme label whole; the Forecast rows' temperatures apart; an event's sheet labels "Created by" and "Calendar" whole; a later week's This week stays whole (the title gives way); the add sheet's summary on two lines with a long name. Back to 1.0.

Send the user these images, light and dark where both exist: `button_matrix_*` (core/ui), `wizard_frame_*`, `settings_people_*`, `person_new_*` (core/setup), `week_*`, `detail_editable_*`, `editor_empty_*`, `review_ok_*` (capability/calendar), `forecast_ready_*`, `forecast_failed_*` (capability/weather), `home_weather_*`, `pin_pad_*` (app), the four `*_fs15` and `forecast_fs15`, and the walk-through screenshots; and the Step 4 list of every baseline changed since 84560bd, grouped by task with one line on why each group changed (Tasks 2–3: button heights and fills; 4: rows and labels; 5: the green and the weather colour; 6: names and faint parts; 7: Home 21 dp higher; 9: the PIN pad's message line; 10: the temperature gap and the `*_fs15` changes). Name this plan's own choices the spec doesn't give (all provisional, for the walk-through): rulings 5, 9, 10, 11, 14, 16 and the new `*_fs15` layouts.

Ask: "Do these match what you want? Anything to change before 4d-1 is done?"
- **If the user asks for changes:** make them, re-record only the affected images with `--tests`, look at them, run `./gradlew testDebugUnitTest verifyRoborazziDebug`, re-send them, and commit with a message describing the change. Repeat until approved.
- **When approved:** go to Step 7.

- [ ] **Step 7: Record what the user deferred**

Add under "## From Plan 4d-1 (deferred)" in the follow-ups file any item the user or the controller noted during the walk-through that was deferred rather than fixed, then:
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Follow-ups: what the 4d-1 walk-through left for later"
```
(Skip the commit if nothing was added.)

---

## Spec coverage (4d-1 design → tasks)

| Design | Where |
|---|---|
| §1 goal, §2 scope (D1–D7, D9–D14, the docs change) | Tasks 1–11 |
| Decision 1 (the split), 7 (vocabulary: nothing new) | No new visible terms from the list; "Assign to…", "Master" kept as they are |
| Decision 2, §5.1 names on chips and Coming up (D4) | Task 6 (Review Focus 1) |
| Decision 3, §5.2 light accent, chip secondary; §5.3 weather (D5, D6) | Task 5 |
| Decision 4 (overlay idle close) | 4d-2 (not here) |
| Decision 5, §6.7 device owner | Task 11 Steps 1–3 (ruling 31) |
| Decision 6, §4.3 `HhSectionLabel`, curly quotes in new strings | Task 4; Global Constraints; Task 8's copy |
| Decision 8 one disabled look | Task 1 (`ButtonsTest`), Task 3 (Delete greys out) |
| Decision 9 the quiet fill | Task 1 (`quietFill`, `buttonColors`, `ContrastTest`; `on` required), rulings 5, 25 |
| Decision 10 layout changes, baselines (amended: per task) | Rulings 4, 14; each task's compare and re-record step; Task 11 Step 4 |
| §4.1 `HhButton` (D1), sizes, tones, callers, the 48 dp raises | Tasks 1, 2, 3 (rulings 1, 9, 10, 11, 12, 13) |
| §4.2 `HhListRow` (D2) | Task 4 (Review Focus 4; ruling 14) |
| §5.4 faint affordances (D7), the chosen page (D5) | Task 6 |
| §5.5 `ContrastTest` | Tasks 1, 5, 6 (ruling 3; ruling 6 for `danger` on `dangerSoft`; ruling 26 for `PersonChipInk`) |
| §6.1 the clock box (D9), the Plan 1 follow-up | Task 7; Task 11 Step 3 |
| §6.2 the header budget (D10) | Task 7 |
| §6.3 the weather's failed state (D11) | Task 8 (rulings 18, 27, 28; Review Focus 2) |
| §6.4 the detail sheet scrolls (D12) | Task 9 (Review Focus 3; ruling 29) |
| §6.5 the PIN pad (D13) | Task 9 (rulings 15, 16) |
| §6.6 fixed sizes to minimums (D14), `*_fs15`; the status bar (amended) | Task 10 (rulings 7, 8, 20); Review health line ellipsis: Task 4 (`HhListRow`'s subtitle); PIN pad and detail `fs15`: Task 9 |
| §7 testing strategy (amended: per-task baselines), gate, walk-through | Global Constraints; Task 11 Step 6 (rulings 22, 32) |
| §9 risks | Review Focus, second list |

