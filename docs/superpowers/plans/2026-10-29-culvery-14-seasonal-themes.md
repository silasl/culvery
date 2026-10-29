# Culvery — Plan 14: Seasonal themes, Halloween first Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–8 and 10–11 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 9 (stage 1 on the tablet) is the controller with the user and the son's SM-T510. Task 12 is the controller. Task 13 is the controller on the `culvery-5` AVD with the user. Task 14 needs the user and the SM-T510.

**Goal:** a seasonal theme dresses up the wall tablet by its dates (Halloween, 24–31 October by default): its own day and night colours on every screen, a pumpkin for the mark, a cobweb on the Today card, a moon and drifting bats behind Home, a Creepster clock, and a haunted organ for the doorbell, with a preview in Settings › Display and the doorbell sound chosen in Settings › Door.

**Architecture:**
- **`:core:ui`** holds the seam: `SeasonalTheme` (each with its default dates), `SeasonDates`, `ShowingTheme` (a `StateFlow<SeasonalTheme?>`, for code outside composition), `LocalSeasonalTheme` / `Culvery.seasonal` (for composition), the four colour sets picked in `CulveryTheme(dark, seasonal)`, Halloween's drawings and the Creepster clock face. Door and Calendar already depend on `:core:ui`, so no new module edge.
- **`:app`** decides which theme shows (`SeasonalThemes`: the date, the stored dates, a preview that ends at midnight), binds it as `ShowingTheme`, hands it to `CulveryTheme`, draws the pumpkin, moon and bats, and keeps the bats still when the screen is dark, Settings is open or animations are off. Settings › Display gets the preview (stage 1) and the dates (stage 2).
- **`:capability:door`** gets the organ WAV, `DoorSound`, the "Doorbell sound" choice, and plays the sound the choice and the showing theme name. **`:capability:calendar`** draws the cobweb on `TodayCard` when `Culvery.seasonal` is Halloween.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1; Python 3 standard library for the organ. **No new library.** One new asset: Creepster (SIL OFL 1.1), approved by the user on 2026-10-29.

**Spec:** `docs/superpowers/specs/2026-10-29-culvery-14-seasonal-themes-design.md`, all of it. Every "Call I made" there is decided; this plan doesn't reopen them.

**Not this plan** (spec non-goals): no house devices; no Christmas; no downloaded sound; no launcher icon change; no new readability rules; no Automatic / Off / "on now" setting (switching is by date only, the preview is the only manual way).

**Previous plans (format, shared parts):** `docs/superpowers/plans/2026-10-14-culvery-05-ring-doorbell.md` (Task 14: `door_chime.py`, `Chime`, `DoorAlerts`), `docs/superpowers/plans/2026-10-19-culvery-08-sleep-and-wake.md` (`DisplayState`, the night clock, Settings › Display), `docs/superpowers/plans/2026-10-29-culvery-13-party-mode.md` (house rules, the UI-task ending).

**Builds on:** branch `plan-14-seasonal-themes` in the one checkout `C:\Users\si\code\siland\culvery`, cut from `main` at 0cbc6890 (the spec is committed on it at 1712b67d). No worktrees. Never push.

**Deadline:** stage 1 (Tasks 1–9) must be on the SM-T510 by **30 October 2026**. Stage 2 (Tasks 10–11) and the end tasks follow.

## Global Constraints

**From the spec, verbatim:**
- §1 "It changes the tablet only: never lights, plugs, speakers or anything else in the house."
- §1 anti-goals: "Anything the family reads becomes harder to read"; "The night clock lights up the bedroom-dark room more than it does today."; "Warning red becomes easy to mistake for the Halloween orange."; "The drifting bats keep the screen or CPU busy while the tablet is dark or asleep, or make the tablet run warm."
- §2.1 "Halloween defaults to 24–31 October inclusive, with the normal look back on 1 November." "There is no *Off* or *on now* choice".
- §2.5 "**Night clock:** unchanged in every theme: dim grey, DM Sans, same size."
- §3 "Person colours don't change." "A preview ends when it's turned off or at the next local midnight, whichever comes first … It isn't stored across a restart."
- §3 "A new theme later (for example Christmas) is added as: a colour set for day and night, its drawn touches, an optional clock face, an optional doorbell sound, and default dates. Nothing else changes."
- §4.1 "A range may wrap the new year … If two ranges overlap, the first theme in the list wins." "A stored date that is missing or can't be read falls back to the theme's default."
- §4.3 "All drawings are our own vector paths in code. No images, no downloads." The pumpkin "stays a picture only, skipped by TalkBack".
- §4.4 "the clock's width must not change from minute to minute." "The date under the clock, the night clock, and every other text stay in DM Sans."
- §4.6 "Play once, not twice". "The Door module must not depend on the app module." "Unknown or missing stored values read as *Follow the theme*."

**Copy** (verbatim from the spec, or this plan's where the spec leaves a gap; curly apostrophes, "›", "–" in code; each owning task tests its strings):
- Settings › Door: section "Doorbell sound"; radio rows "Follow the theme" (line "The haunted organ while Halloween shows, the classic chime otherwise."), "Classic chime", "Haunted organ". The existing "Play chime" button keeps its name.
- Settings › Display (stage 1): section "Seasonal themes"; per theme a switch row "Preview {label}" with the line "Shows {label} on the wall until midnight." when off and "Showing now. It ends at midnight." when on.
- Settings › Display (stage 2): per theme a row "{label}" with "{start} – {end}" as its line ("24 Oct – 31 Oct"), click label "Change the dates"; its sheet titled "{label} dates" with the line "Tap the first day, then the last.", buttons "Save dates", "Put back {default start} – {default end}", "Cancel"; refusals "The last day is before the first. Tap a last day on or after {start}." and "A theme can show for a year at most. Tap a last day before {start + 1 year}."; under the section when nothing shows: "Next: {label} from {d MMMM}" ("Next: Halloween from 24 October").
- Theme label: "Halloween".

**From the house rules** (as Plan 13's Global Constraints; binding):
- **Platform:** package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only; **no new library; never change a version**; no deprecated API, and any one met is flagged to the user before use. This plan's APIs were checked and are current: `ValueAnimator.areAnimatorsEnabled()` (API 26), `withFrameMillis`, `mutableLongStateOf`, `rememberTextMeasurer`, `MonthDay`, `ImageVector.Builder.addPath`, `MediaPlayer.create(Context, Int, AudioAttributes, Int)`. Never `Settings.System.ANIMATOR_DURATION_SCALE` (deprecated; `Settings.Global`'s is not needed).
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash, or `gradlew.bat`), plus `./gradlew -p build-logic :convention:test` in any task that adds a log line or a `TAG`.
  - Never plain `test`, `check` or `build`, and never a Gradle `install…` task. No subagent touches a device.
  - **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`**: a long temp path breaks Robolectric past MAX_PATH.
  - Scratch goes in `$LISTS` = `C:/t9`, a plain shell variable, never exported.
- **Tests that can fail:** every new test fails before its change; one that passes before it is labelled **pin**. Timer and frame tests run on `compose.mainClock` with `autoAdvance = false` or on virtual time, asserted one step before the boundary and at it. A fake clock is a `MutableStateFlow<LocalDateTime?>`.
- **Every UI task ends as Plan 13's:**
  1. `compareRoborazziDebug` on the module; open each `*_compare.png`, and check each new image against what the task names;
  2. `./gradlew <module>:recordRoborazziDebug`;
  3. the baseline check: write `$LISTS/expected-baselines.txt` (the task's **New** and **Changes** names) and an empty `$LISTS/expected-prefixes.txt` afresh, then Plan 10's four lines:
```bash
git status --porcelain -- "*.png" | sed 's#.*/##; s#\.png$##' | sort -u > "$LISTS/changed.txt"
echo "changed but not listed:"; comm -23 "$LISTS/changed.txt" <(sort -u "$LISTS/expected-baselines.txt")
echo "listed but unchanged:"; comm -13 "$LISTS/changed.txt" <(sort -u "$LISTS/expected-baselines.txt")
echo "changed but not predicted:"; grep -v -f <(sed 's/^/^/' "$LISTS/expected-prefixes.txt") "$LISTS/changed.txt"
echo "predicted but unchanged:"; while read -r p; do grep -q "^$p" "$LISTS/changed.txt" || echo "$p"; done < "$LISTS/expected-prefixes.txt"
```
     The first two lists must be empty, or stop;
  4. the gate; 5. commit.

  Baselines live in `<module>/src/test/screenshots/`. Every new Halloween screen is recorded `_dark` and `_light`. `CopyScanTest` passes; every screen test ends with `compose.assertClickablesHaveRoleAndLabel()`.
- **Code:** comments say only what the code can't, one line by default, spec refs as `(14 §4.3)`, never a task or ruling number. Layout and drawing numbers go in a named object (`SeasonalDimens` in `:core:ui`, `DisplayDimens` in `:app`). Log lines interpolate only `e::class.simpleName` and enum names; this plan adds none unless a task says so.
- **Privacy:** no personal data in logs, test names, fixtures or committed files. Sample data only.
- **Devices:** the emulator is `culvery-5`; `emulator-5554` holds a signed release and the household: never install on it. The tablet (SM-T510) gets `adb install -r` of a signed release only; **never uninstall, never wipe**. No Hue, Sonos, plug or Home Assistant change, ever, in this plan.
- **Git:** a commit is a plain sentence with no prefix and no trailer of any kind; never push. **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **Midnight and the clock: the preview turned on at 23:59, the 31 October → 1 November change, the clock not yet read at start, the clock set back across midnight (Plan 11b's `DebugClock`).**
   - **Expected:** the preview ends at the first minute whose date isn't the day it was turned on, forward or back; showing follows the date with no restart; before the clock is read nothing is themed and nothing throws.
   - **Tests** (Task 2): `SeasonalThemesTest.aPreviewEndsAtMidnight`, `aPreviewEndsWhenTheClockGoesBackADay`, `halloweenEndsAsNovemberStarts`, `nothingShowsBeforeTheClockIsRead`.
2. **The bats while nobody should see them move: the night clock, the dark screen, Settings over Home, another tab, "Remove animations" on.**
   - **Expected:** the sky's elapsed time doesn't advance; the bats are drawn still at their last place; Home on another tab has no sky at all.
   - **Tests** (Task 5): `SkyDriftTest.theSkyDriftsOnlyAwakeOnHomeWithAnimations` (the table), `HalloweenSkyTest.stillMeansNoFrames`, `aStillSkyStillDrawsTheBats`, `CulveryShellSkyTest.noSkyOffHome`.
3. **Text over the new colours and drawings: every checked pair in both Halloween sets, header text over the moon and bats, warning red beside the orange.**
   - **Expected:** every pair passes the 4d thresholds; `danger` and `accent` are at least 35° apart in hue in every set.
   - **Tests** (Task 1): `ContrastTest` over all four sets, `dangerAndAccentStayApart`, `headerTextReadsOverTheSky`.
4. **The doorbell's stored choice unreadable, the theme changing between choosing and ringing, Play chime pressed with Follow the theme.**
   - **Expected:** an unknown or missing value reads as Follow the theme; a ring plays the sound for the theme showing at that moment; Play chime plays what a ring would play now.
   - **Tests** (Task 7): `DoorSettingsTest.anUnknownDoorbellValueReadsAsFollowTheTheme`, `DoorAlertsTest.aRingPlaysTheThemesSoundAsItIsNow`, `DoorSettingsPageTest.playChimePlaysWhatARingWould`.
5. **The header jumping: Creepster's line box, its proportional digits from 11:11 to 00:00, the theme switching on at midnight.**
   - **Expected:** the clock's box is the same height in DM Sans and Creepster, the same width for every time, and the weather items don't move.
   - **Tests** (Task 3): `HalloweenClockTest.theClockKeepsItsWidthEveryMinute`, `theHeaderKeepsItsHeightInEitherFace`, `theHeaderItemsDontMoveWhenTheFaceChanges`.

Stage 2 adds one more, owned by Task 10: **dates across the new year and 29 February** (`SeasonDatesTest.aWrappedRangeCoversBothEnds`, `aLeapDayEndInAShortYearEndsOnThe28th`; `DisplaySettingsTest.unreadableSeasonDatesFallBackToTheDefaults`).

---

| # | Task | Review |
|---|---|---|
| 1 | The theme seam and Halloween's colours in `:core:ui`; the contrast tests over four sets | **opus** (readability, warning red) |
| 2 | Which theme shows: `SeasonalThemes`, the preview's midnight, the app wired; the night clock pinned | **opus** (midnight edges) |
| 3 | Creepster and the Halloween clock face | sonnet |
| 4 | The pumpkin mark and the Today card's cobweb | sonnet |
| 5 | The moon and the drifting bats, still when nobody should see them move | **opus** (battery, heat) |
| 6 | The haunted organ: the script, the WAV, `DoorSound`, `Chime` plays a sound | sonnet |
| 7 | Settings › Door's "Doorbell sound"; a ring follows the theme | sonnet |
| 8 | Settings › Display: preview each theme until midnight | sonnet |
| 9 | **Stage 1 on the tablet** | controller + user, **needs the SM-T510** |
| 10 | Stage 2: the dates stored, read, and the next theme | sonnet |
| 11 | Stage 2: Settings › Display edits the dates and says what's next | sonnet |
| 12 | Simplify the whole branch | controller |
| 13 | The emulator walk-through on `culvery-5` | controller + user |
| 14 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:** the seam and colours first (1), since everything reads them; then what decides the theme and wires it into the app (2), so Halloween shows on a build from Task 2 on. The face (3) and the drawings (4, 5) are independent of the doorbell (6, 7) and each records the Home screenshots afresh. The preview (8) closes stage 1, which goes on the tablet (9) before the dates are editable (10, 11). Halloween's default dates contain 29–31 October, so stage 1 shows Halloween without stage 2.

## Rulings against the code

The spec leaves these to the builder, or they fall out of the code. Each is this plan's call.

1. **The seam** (`core/ui/.../Seasonal.kt`): `SeasonalTheme` is an enum, "no theme" is `null`. `ShowingTheme` sits in `:core:ui`, not `:core:plugin`, because `CulveryTheme` needs the enum and `:core:plugin` doesn't depend on `:core:ui`; Door and Calendar already depend on `:core:ui`. Composition reads `Culvery.seasonal` (provided by `CulveryTheme`); Door's ring, outside composition, reads `ShowingTheme.showing`, bound in `:app` to `SeasonalThemes`.
2. **Colour sets** (`core/ui/.../SeasonalColors.kt`), `fun colorsFor(seasonal: SeasonalTheme?, dark: Boolean): HhColors` with an exhaustive `when`, so a new theme must name its sets. Starting values (checked against every `ContrastTest` pair with the spec's mockup values; the day set's `surf`, `surf2`, `ink` and `accentSoft` are moved from the mockup because the mockup's fail the clash tag, the outside-month alpha, `weather` on `surf2` and `accent` on `accentSoft`):

   | token | `HalloweenNight` | `HalloweenDay` |
   |---|---|---|
   | bg | 0xFF120E15 | 0xFFF3ECE3 |
   | surf | 0xFF1D1722 | 0xFFFFFEFC |
   | surf2 | 0xFF29212F | 0xFFF6F0E8 |
   | surf3 | 0xFF352B3C | 0xFFE4D8CA |
   | line | 0xFF221B27 | 0xFFE3D7C9 |
   | ink | 0xFFF3EEF4 | 0xFF120C15 |
   | mute | 0xFFA69AAD | 0xFF5E5263 |
   | accent | 0xFFF28C28 | 0xFFA84B0A |
   | accentInk | 0xFF1A0C02 | 0xFFFFFFFF |
   | accentOnInk | 0xFFA84B0A | 0xFFF28C28 |
   | accentSoft | 0xFF3B2414 | 0xFFFAE9DA |
   | danger | 0xFFF27A9B | 0xFFB0284F |
   | dangerSoft | 0xFF3B1A26 | 0xFFF8DDE5 |
   | dangerInk | 0xFF1A0710 | 0xFFFFFFFF |
   | weather, rain | `DarkColors`' | `LightColors`' |
   | border | `Color.Transparent` | 0xFFDDCFBF |

   The implementer moves any value a test fails by the smallest step that passes, and says which in the report.
3. **Warning red apart from orange:** measured as the hue gap in degrees (0–180) between `danger` and `accent`, at least **35°**, in all four sets. Today's normal `danger` (8°) would be 22° from the Halloween orange (30°), which is why Halloween's `danger` moves to a pink-red (344° / 343°).
4. **The drawings' colours** (`HalloweenInks`, in `SeasonalColors.kt`, so `ContrastTest` checks them): `moon(c) = c.accentSoft`, `bat(c) = c.surf2`, `web(c) = c.surf3`; the pumpkin's body is `c.accent`, its stem `PUMPKIN_STEM = Color(0xFF5E8C31)`, its carved face `PUMPKIN_FACE = Color(0xFF1A0C02)` in both versions. Header text (`ink`, `mute`, `weather` at 4.5, `rain` at 3.0) is checked on the moon and bat colours, so the bats can cross the header band.
5. **Which theme shows** (`app/.../display/SeasonalThemes.kt`): `showingOn(date, dates, preview)` = the preview's theme when `preview?.day == date`, else the first `SeasonalTheme.entries` whose dates contain `date`, else null. The preview is one `MutableStateFlow<ThemePreview?>`, in memory only; turning one on replaces any other; a preview whose `day` isn't today counts as off and is cleared on the next tick. Before the clock is read, `showing` is null.
6. **The clock face:** Creepster at the clock's 100 sp, `lineBox(86.sp)` as today, no letter spacing, in `accent` (the mockup; 7.8:1 night, 4.9:1 day, over the 3:1 large-text line). Each digit sits in a slot as wide as the widest of "0"–"9" at that style, centred; ":" takes its own width. This holds whether or not Creepster has tabular figures, so the font isn't inspected for `tnum`. Creepster is kept whole (no subsetting): the clock's semantics still read "23:41".
7. **The cobweb** is drawn by `HhCard`'s new `decoration` (behind the content, inside the clip), so it needs no layout and never takes a touch; the "+" and "See the week" stay where they are.
8. **The sky:** 1 moon (radius `SeasonalDimens.moonRadius` = 34 dp, centre 84 dp from the right and 70 dp from the top of Home) and 4 bats (`SeasonalDimens.batSpan` = 26 dp wing to wing), at fixed heights 0.08, 0.22, 0.47, 0.71 of Home's height and spread a quarter of the width apart. They drift left to right one screen width every `BAT_LAP_MS` = 90 000 ms, wrapping. The drift updates at most every `SKY_FRAME_MS` = 50 ms (20 frames a second: about one pixel a frame on the tablet), and the elapsed time is read only in the draw phase, so a step redraws the sky layer and nothing else.
9. **When the bats move:** `skyDrifts(screen, settingsOpen, animatorsOn) = screen == Screen.AWAKE && !settingsOpen && animatorsOn`; off Home the sky isn't composed. `animatorsOn` is `ValueAnimator.areAnimatorsEnabled()`, read in `MainActivity.onResume` (the SM-T510 is API 30, so the API 33 listener isn't used; changing "Remove animations" means leaving Culvery, which resumes it).
10. **The organ** (`tools/sound/door_organ.py`, 22 050 Hz 16-bit mono, 3.6 s): notes (frequency Hz, start s, length s) `(880.00, 0.00, 0.14) (783.99, 0.14, 0.12) (880.00, 0.26, 0.86)`, then `(783.99, 1.30, 0.14) (698.46, 1.44, 0.14) (659.25, 1.58, 0.14) (587.33, 1.72, 0.30) (554.37, 2.02, 0.40) (587.33, 2.42, 1.10)`; partials (ratio, level) `(0.5, 0.12) (1, 0.7) (2, 1.0) (3, 0.8) (4, 0.55) (6, 0.25) (8, 0.12)`, the 2nd–4th leading for the small speaker; each note a 15 ms attack, held, 60 ms release; a 5.5 Hz tremulant of ±0.25 % in pitch; a 0.35 s reverb tail from three decaying echoes (60, 110, 170 ms at 0.35, 0.25, 0.15), all within 3.6 s; peak-normalised to 0.95 as the chime is.
11. **Doorbell sound** (`:capability:door`): `enum class DoorbellChoice { FOLLOW_THEME, CLASSIC, ORGAN }`, stored by name under `doorbell_sound` in `door_settings`; `enum class DoorSound { CLASSIC, ORGAN }`; `fun DoorbellChoice.soundFor(showing: SeasonalTheme?): DoorSound`, where `FOLLOW_THEME` maps with an exhaustive `when` (`HALLOWEEN → ORGAN`, `null → CLASSIC`). `Chime` becomes `fun interface Chime { fun play(sound: DoorSound, volume: Float): Boolean }`.
12. **Stored dates** (stage 2): in `display_settings`, `season_{name.lowercase()}_start` and `_end` as `month * 100 + day` ints. Both must read as a valid `MonthDay` (29 February is valid), else that theme's defaults apply. Putting back the defaults removes both keys.
13. **Choosing dates** (stage 2): the sheet shows `HhDateGrid` at the month of the range's next start; the first tap is the first day, the second the last. A last day before the first is refused, as is one a year or more after it; a range across the new year is picked as real dates (20 Dec 2026, then 2 Jan 2027), so it's simply "after the first". Saved as `MonthDay`s.
14. **"Next:"** shows under the section when `showingOn(today, dates, null)` is null: the theme whose next start (this year's, or next year's when it has passed) is soonest; ties go to list order.

## File Structure

```
core/ui/src/main/java/uk/co/siland/culvery/core/ui/
  Seasonal.kt (SeasonalTheme, SeasonDates, ShowingTheme, LocalSeasonalTheme), SeasonalColors.kt,
    Theme.kt (CulveryTheme(dark, seasonal), Culvery.seasonal)                                     (Task 1)
  ClockFace.kt (Creepster, SlotClock); res/font/creepster.ttf; licenses/OFL-Creepster.txt; README.md (Task 3)
  HalloweenArt.kt (PumpkinMark, Modifier/DrawScope cobweb, HalloweenSky, batPositions),
    SeasonalDimens in Seasonal.kt; Components.kt (HhCard decoration)                               (Tasks 4, 5)
core/ui testFixtures Screenshots.kt (snap's seasonal)                                              (Task 1)
app: display/SeasonalThemes.kt, display/di/DisplayModule.kt, MainActivity.kt                        (Task 2)
  shell/ui/HomeScreen.kt                                                                          (Task 3)
  shell/ui/NavRail.kt                                                                             (Task 4)
  shell/ui/CulveryShell.kt, SetupWiring.kt (skyDrifts), MainActivity.kt                           (Task 5)
  display/DisplayWords.kt, display/ui/DisplaySettingsPage.kt                                      (Tasks 8, 11)
  display/DisplaySettings.kt, display/SeasonalThemes.kt                                           (Task 10)
capability/calendar: ui/TodayCard.kt                                                              (Task 4)
capability/door: tools/sound/door_organ.py, res/raw/door_organ.wav, Chime.kt, DoorAlerts.kt (call),
    ui/DoorSettingsPage.kt (call), test TestDoor.kt                                               (Task 6)
  DoorSettings.kt, DoorWords.kt, DoorAlerts.kt, DoorCapability.kt, ui/DoorSettingsPage.kt          (Task 7)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                              (Tasks 9, 13, 14)
```

---

### Task 1: The theme seam and Halloween's colours in `:core:ui` (spec §3, §4.1, §4.2; rulings 1–4; Review Focus 3)

**Review:** opus. Every pair the family reads, in both Halloween sets; warning red apart from orange.

**Files:**
- Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Seasonal.kt`, `SeasonalColors.kt`.
- Modify `core/ui/.../Theme.kt` (`CulveryTheme(dark, seasonal = null)`, `LocalSeasonalTheme` provided, `Culvery.seasonal`).
- Modify `core/ui/src/testFixtures/.../test/Screenshots.kt` (`snap(name, dark, modifier, seasonal: SeasonalTheme? = null, content)`).
- Test: `core/ui/src/test/.../ContrastTest.kt`, `Contrast.kt` (`hueGap`), `ThemeTest.kt`, new `SeasonalTest.kt`; `capability/door/src/test/.../ui/DoorCardScreenshotTest.kt`; `capability/party/src/test/.../ui/PartyScreenshotTest.kt`.

**Interfaces:**
- Produces (all public in `uk.co.siland.culvery.core.ui`):
  - `enum class SeasonalTheme(val label: String, val defaultDates: SeasonDates) { HALLOWEEN("Halloween", SeasonDates(MonthDay.of(10, 24), MonthDay.of(10, 31))) }`
  - `data class SeasonDates(val start: MonthDay, val end: MonthDay) { operator fun contains(date: LocalDate): Boolean }`: inclusive; `end < start` wraps the new year.
  - `fun defaultSeasonDates(): Map<SeasonalTheme, SeasonDates>` (every entry's default).
  - `interface ShowingTheme { /** Null while no theme shows. */ val showing: StateFlow<SeasonalTheme?> }`
  - `val LocalSeasonalTheme: ProvidableCompositionLocal<SeasonalTheme?>` (`staticCompositionLocalOf { null }`); `Culvery.seasonal: SeasonalTheme?` (`@Composable get`).
  - `val HalloweenNight: HhColors`, `val HalloweenDay: HhColors`, `fun colorsFor(seasonal: SeasonalTheme?, dark: Boolean): HhColors`.
  - `object HalloweenInks { fun moon(c: HhColors): Color; fun bat(c: HhColors): Color; fun web(c: HhColors): Color; val PUMPKIN_STEM: Color; val PUMPKIN_FACE: Color }`.
  - `@Composable fun CulveryTheme(dark: Boolean, seasonal: SeasonalTheme? = null, content: @Composable () -> Unit)`: the target is `colorsFor(seasonal, dark)`, animated as today (400 ms).
  - Test helper `internal fun hueGap(a: Color, b: Color): Float` in `Contrast.kt` (degrees, 0–180).

- [ ] **Step 1: The failing tests.**
  - `ContrastTest`: `themes` becomes every set `colorsFor` gives, named `"{label or normal} {night|day}"` (`(listOf(null) + SeasonalTheme.entries)` × dark, light); every existing loop then covers four sets with no rule changed. The pinned edges (`theEdgesStayWhereTheyAre`, `theTintEdgesStayWhereTheyAre`, the light pins in `aPastDayReadsAtItsAlpha` and `theClashTagReadsOnTheRow`) stay on `LightColors` / `DarkColors`. Add:
    - `dangerAndAccentStayApart`: every set, `hueGap(t.danger, t.accent) >= 35f`, the message naming the set.
    - `headerTextReadsOverTheSky`: both Halloween sets, `ink`, `mute`, `weather` at `TEXT` and `rain` at `NON_TEXT` on `HalloweenInks.moon(t)` and `HalloweenInks.bat(t)`.
    - `theWebReadsUnderCardText`: both Halloween sets, `ink` and `mute` on `HalloweenInks.web(t)` at `TEXT`.
  - `ThemeTest`:
    - `halloweenProvidesItsSets`: `CulveryTheme(dark = true, seasonal = HALLOWEEN)` → `Culvery.colors == HalloweenNight` and `Culvery.seasonal == HALLOWEEN`; `dark = false` → `HalloweenDay`.
    - `noThemeIsTheNormalLook` (**pin**): `seasonal = null` → `DarkColors` / `LightColors`, `Culvery.seasonal == null`.
    - `halloweenKeepsTheLightOrDarkReading`: `HalloweenDay.isLight` true, `HalloweenNight.isLight` false; `HalloweenNight.border == Color.Transparent`.
    - `personColoursDontChange` (**pin**): `PersonPalette.colors` and `PersonChipInk` are what they were (compare with literals copied from `PersonPalette.kt` today).
    - `halloweenKeepsWeatherAndRain`: `HalloweenNight.weather == DarkColors.weather`, `.rain == DarkColors.rain`; day with `LightColors`.
  - `SeasonalTest`:
    - `halloweenRunsFrom24To31October`: 23 Oct false, 24 Oct true, 31 Oct true, 1 Nov false (2026).
    - `aWrappedRangeCoversBothEnds`: `SeasonDates(MonthDay.of(12, 20), MonthDay.of(1, 2))` → 19 Dec false, 20 Dec true, 1 Jan true, 2 Jan true, 3 Jan false.
    - `aOneDayRange`: start == end → only that day.
    - `theDefaultsAreEveryThemesOwn`: `defaultSeasonDates()` has every entry, Halloween's 24 Oct – 31 Oct.
  - Screenshots (each `_dark` and `_light`, via `snap(…, seasonal = SeasonalTheme.HALLOWEEN)`): `door_card_halloween` (the `door_card` sample), `party_sheet_on_halloween` (the `party_sheet_on` sample).

  Run `./gradlew :core:ui:testDebugUnitTest`. Expected: FAIL (compile), the pins aside.
- [ ] **Step 2: Implement** per the Interfaces and rulings 1–4. The scheme's `remember` keys stay `(dark, colors)`.
- [ ] **Step 3: Images.** **New:** `door_card_halloween_dark`, `door_card_halloween_light`, `party_sheet_on_halloween_dark`, `party_sheet_on_halloween_light` (check: orange buttons, dark plum or warm cream surfaces, text as clear as the normal images). **Changes:** none; every existing image stays (default `seasonal = null`).
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui capability/door/src/test capability/party/src/test
git commit -m "Add seasonal themes to the shared theme with Halloween's day and night colours, checked by the same contrast tests as the normal look"
```

---

### Task 2: Which theme shows, the app wired, the night clock pinned (spec §2.1, §2.5, §3, §4.1; rulings 5; Review Focus 1)

**Review:** opus. Midnight in both directions, the clock unread, the night clock untouched.

**Files:**
- Create `app/src/main/java/uk/co/siland/culvery/display/SeasonalThemes.kt`.
- Modify `app/.../display/di/DisplayModule.kt` (`@Binds abstract fun showingTheme(impl: SeasonalThemes): ShowingTheme`), `app/.../MainActivity.kt` (`@Inject lateinit var seasonal: SeasonalThemes`; `CulveryTheme(dark = state.dark, seasonal = showing)`).
- Test: `app/src/test/.../display/SeasonalThemesTest.kt`; `display/ui/SleepLayersTest.kt`, `display/ui/SleepLayersScreenshotTest.kt`; `shell/ui/ShellScreenshotTest.kt` (`snap` gains `seasonal`); `HouseGraphTest.kt` with `app/src/debug/.../di/HouseGraphProbe.kt` (`showingTheme(): ShowingTheme`, `seasonalThemes(): SeasonalThemes`).

**Interfaces:**
- Consumes: Task 1's `SeasonalTheme`, `SeasonDates`, `ShowingTheme`, `defaultSeasonDates()`, `CulveryTheme(dark, seasonal)`.
- Produces (in `uk.co.siland.culvery.display`):
  - `data class ThemePreview(val theme: SeasonalTheme, val day: LocalDate)`
  - `internal fun showingOn(date: LocalDate, dates: Map<SeasonalTheme, SeasonDates>, preview: ThemePreview?): SeasonalTheme?`
  - `@Singleton class SeasonalThemes internal constructor(now: StateFlow<LocalDateTime?>, dates: Flow<Map<SeasonalTheme, SeasonDates>>, scope: CoroutineScope) : ShowingTheme`, `@Inject constructor(clock: HouseholdClock, @ApplicationScope scope: CoroutineScope)` passing `clock.now` and `flowOf(defaultSeasonDates())` (Task 10 replaces the dates):
    - `override val showing: StateFlow<SeasonalTheme?>` (Eagerly, null until the clock is read);
    - `val previewing: StateFlow<SeasonalTheme?>`: the theme previewed today, else null;
    - `val dates: StateFlow<Map<SeasonalTheme, SeasonDates>>` (Eagerly, `defaultSeasonDates()` until read);
    - `fun preview(theme: SeasonalTheme, on: Boolean)`: on → `ThemePreview(theme, today)` (nothing while the clock is unread); off → cleared only when `theme` is the one previewed.

- [ ] **Step 1: The failing tests.**
  - `SeasonalThemesTest` (fake `now`, `runTest` with `backgroundScope`):
    - `halloweenShowsInItsDates`: 2026-10-29 12:00 → `HALLOWEEN`; 2026-10-23 23:59 → null.
    - `halloweenEndsAsNovemberStarts`: 2026-10-31 23:59 → `HALLOWEEN`; then 2026-11-01 00:00 → null, with no new instance.
    - `aPreviewShowsOutsideTheDates`: 2026-11-05 10:00, `preview(HALLOWEEN, true)` → `showing` and `previewing` `HALLOWEEN`; `preview(HALLOWEEN, false)` → both null.
    - `aPreviewEndsAtMidnight`: preview on at 2026-11-05 23:59 → `HALLOWEEN`; 2026-11-06 00:00 → `showing` null and `previewing` null.
    - `aPreviewEndsWhenTheClockGoesBackADay`: preview on at 2026-11-05 00:01; 2026-11-04 23:59 → both null.
    - `nothingShowsBeforeTheClockIsRead`: `now` null → `showing` null; `preview(HALLOWEEN, true)` → `previewing` still null, no throw.
    - `theFirstThemeWinsAnOverlap`: `showingOn` with two entries can't be built from one enum, so it's tested on the function: `showingOn(29 Oct, mapOf(HALLOWEEN to SeasonDates(1 Oct, 31 Dec)), null) == HALLOWEEN`, and a `preview` for another day is ignored (`ThemePreview(HALLOWEEN, 28 Oct)` on 29 Oct outside the dates → null).
  - `SleepLayersTest.theNightClockIsTheSameInEveryTheme` (**pin**, spec §2.5): `show` takes `seasonal`; under `HALLOWEEN` dark and light, "23:41"'s style has `fontFamily == HhType.clock.fontFamily`, `fontSize == 200.sp`, `letterSpacing == (-8).sp`, `fontFeatureSettings == "tnum"`, `color == Color(0xFF5A5A5A)`; and the layer's background pixel at the screen's corner is `Color.Black` (`pixelAt`, `@GraphicsMode(NATIVE)`).
  - `SleepLayersScreenshotTest`: `night_clock_halloween` (dark, `HALLOWEEN`). It must be byte-identical to `night_clock` (Step 3).
  - `ShellScreenshotTest`: `home_halloween_dark` / `home_halloween_light`, the `fullHome` state with `seasonal = HALLOWEEN` (colours only at this task).
  - `HouseGraphTest.theShowingThemeIsSeasonalThemes`: `showingTheme()` is the same instance as `seasonalThemes()`.

  Run `./gradlew :app:testDebugUnitTest --tests '*SeasonalThemes*' --tests '*SleepLayers*' --tests '*HouseGraph*'`. Expected: FAIL, the pin aside.
- [ ] **Step 2: Implement** per ruling 5 and the Interfaces. `MainActivity` collects `seasonal.showing` with `collectAsStateWithLifecycle()` beside `state`.
- [ ] **Step 3: Images.** **New:** `night_clock_halloween`, `home_halloween_dark`, `home_halloween_light`. Then `cmp app/src/test/screenshots/night_clock.png app/src/test/screenshots/night_clock_halloween.png` prints nothing (spec §2.5; if it differs, stop: the night clock changed). **Changes:** none.
- [ ] **Step 4: The gate, then commit.**
```bash
git add app
git commit -m "Show Halloween by its dates from midnight to midnight, with a preview that ends at midnight, and keep the night clock as it is in every theme"
```

---

### Task 3: Creepster and the Halloween clock face (spec §2.3, §4.4; ruling 6; Review Focus 5)

**Review:** sonnet.

**Files:**
- Download (the user approved it on 2026-10-29) into a new empty `$LISTS/creepster/`: list `https://github.com/google/fonts/tree/main/ofl/creepster` first to get the TTF's exact name (expected `Creepster-Regular.ttf`), then fetch it and `OFL.txt` from `https://raw.githubusercontent.com/google/fonts/main/ofl/creepster/`. Check before copying: `head -c 4 Creepster-Regular.ttf | xxd` is `0001 0000` (a TrueType file); `grep -c "SIL OPEN FONT LICENSE Version 1.1" OFL.txt` prints at least 1. Either fails → stop and tell the controller. Then copy to `core/ui/src/main/res/font/creepster.ttf` and `core/ui/licenses/OFL-Creepster.txt`. Nothing in `$LISTS/creepster/` is run.
- Modify `README.md` line 214's licence sentence: add "Creepster: SIL Open Font License (`core/ui/licenses/OFL-Creepster.txt`), for the Halloween clock."
- Create `core/ui/.../ClockFace.kt`; modify `app/.../shell/ui/HomeScreen.kt` (`HomeHeader`'s clock).
- Test: `core/ui` `ThemeTest.bundledFontsAreRealFiles`; new `core/ui/src/test/.../ClockFaceTest.kt`; new `app/src/test/.../shell/ui/HalloweenClockTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `Culvery.seasonal`, `HhType.clock`, `lineBox`.
- Produces:
  - `val Creepster: FontFamily` (`Font(R.font.creepster)`); `val CreepsterClock: TextStyle` = `HhType.clock` with `fontFamily = Creepster`, `fontWeight = FontWeight.Normal`, `fontFeatureSettings = null`, `letterSpacing = 0.sp`.
  - `@Composable fun SlotClock(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier)`: one `Layout`; each digit in a slot the width of the widest of "0"–"9" (`rememberTextMeasurer`), centred; other characters at their own width; semantics `clearAndSetSemantics { this.text = AnnotatedString(text) }`, so `assertTextEquals` and TalkBack read the time as one.
  - `HomeHeader` draws `SlotClock(time, CreepsterClock, c.accent, <the same testTag, lineBox and traversalIndex>)` when `Culvery.seasonal == HALLOWEEN`, else today's `Text`.

- [ ] **Step 1: The failing tests.**
  - `ThemeTest.bundledFontsAreRealFiles` adds: `R.font.creepster` is over 10 000 bytes and under 500 000.
  - `ClockFaceTest`:
    - `everyDigitGetsTheSameSlot`: `SlotClock("11:11", CreepsterClock, …)` and `SlotClock("00:00", …)` have equal widths.
    - `theClockReadsAsOneTime`: the node's text is "23:41"; one node, no child texts.
  - `HalloweenClockTest` (the shell's `HomeHeader` under `CulveryTheme(dark = true, seasonal = …)`, `now` a state):
    - `theClockKeepsItsWidthEveryMinute`: under `HALLOWEEN`, `home_clock`'s width is the same at 11:11, 00:00, 08:47 and 23:58.
    - `theHeaderKeepsItsHeightInEitherFace`: the header's height and `home_clock`'s height (86 sp's pixels) are the same with `seasonal` null and `HALLOWEEN`.
    - `theHeaderItemsDontMoveWhenTheFaceChanges`: with one sample header item, `home_header_items`' bounds are the same with `seasonal` null and `HALLOWEEN`, at 23:58 (the items are right-aligned in the full width, so only a clock too wide for the budget could move them).
    - `theDateStaysInDmSans` (**pin**): under `HALLOWEEN`, `home_date`'s style `fontFamily == DmSans`.
    - `theClockIsTheThemesOrange` (`@GraphicsMode(NATIVE)`): under `HALLOWEEN`, some pixel inside `home_clock`'s bounds is exactly `HalloweenNight.accent` and none is `HalloweenNight.ink`.
  - Screenshots: `home_halloween_dark` / `_light` change.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*HalloweenClock*' --tests '*ShellLayout*'`. Expected: FAIL, the pin aside; `ShellLayoutTest` (**pins**: the normal clock) passes.
- [ ] **Step 2: Implement** per ruling 6. Check the colon and digits on `home_halloween_*` read clearly at the tablet's size; say in the report if Creepster's ":" needed a nudge.
- [ ] **Step 3: Images.** **New:** none. **Changes:** `home_halloween_dark`, `home_halloween_light` (the Creepster clock in orange; the date and weather unchanged). Every other `home_*` image must not change.
- [ ] **Step 4: The gate, then commit** (`git status` first: only the files above, nothing from `$LISTS`).
```bash
git add core/ui README.md app
git commit -m "Set Home's clock in Creepster while Halloween shows, each digit in a fixed slot so the header never moves"
```

---

### Task 4: The pumpkin mark and the Today card's cobweb (spec §2.2, §4.3; rulings 4, 7)

**Review:** sonnet.

**Files:**
- Create `core/ui/.../HalloweenArt.kt` (pumpkin and cobweb now; the sky in Task 5); add `object SeasonalDimens` to `Seasonal.kt` (`webSize = 88.dp`, `webStroke = 1.dp`, plus Task 5's).
- Modify `core/ui/.../Components.kt`: `HhCard(…, decoration: (DrawScope.() -> Unit)? = null, content)`, drawn after the background and before the padding.
- Modify `app/.../shell/ui/NavRail.kt` (the pumpkin in the mark's place), `capability/calendar/.../ui/TodayCard.kt` (the web when `Culvery.seasonal == HALLOWEEN`).
- Test: new `core/ui/src/test/.../HalloweenArtTest.kt`; `app/src/test/.../shell/ui/NavRailTest.kt`; `capability/calendar/src/test/.../ui/CardsTest.kt` (or the file holding Today's tests), `ui/CardScreenshotTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `HalloweenInks`, `Culvery.seasonal`.
- Produces:
  - `@Composable fun PumpkinMark(width: Dp, modifier: Modifier = Modifier)`: an `ImageVector` in code (a round body of three overlapping lobes in `accent`, a stem in `PUMPKIN_STEM`, two triangle eyes and a jagged mouth in `PUMPKIN_FACE`), `CulveryMark`'s aspect (54 × 49), `contentDescription = null` and `hideFromAccessibility()`.
  - `fun DrawScope.drawCobweb(color: Color, size: Dp)`: radial threads from the top-right corner and four spiral rings, `SeasonalDimens.webStroke` wide.
  - `NavRail` keeps the mark's modifiers and tag `culvery_mark` for whichever is drawn, and adds `pumpkin_mark` to the pumpkin.

- [ ] **Step 1: The failing tests.**
  - `HalloweenArtTest.thePumpkinIsAPictureOnly`: under `HALLOWEEN`, `PumpkinMark` has no text, no content description, and is hidden from accessibility; its pixel at the body's centre is `HalloweenNight.accent` (`pixelAt`, `@GraphicsMode(NATIVE)`).
  - `NavRailTest`:
    - `thePumpkinTakesTheMarksPlace`: under `HALLOWEEN`, `pumpkin_mark` shows; `culvery_mark`'s bounds equal its bounds under `seasonal = null`.
    - `theMarkIsBackWithoutATheme` (**pin**): `seasonal = null` → no `pumpkin_mark`.
  - Calendar (`TodayCard` under `CulveryTheme(dark = true, seasonal = …)`):
    - `theWebSitsInTheTopRightCorner`: under `HALLOWEEN` the pixel 12 dp in from the card's top-right corner (both ways) is `HalloweenInks.web(HalloweenNight)` or the card's `surf` (a thread or a gap), and the same spot without a theme is `DarkColors.surf`; the pixel at a thread's known point (on the diagonal, 30 dp in) is the web colour.
    - `theButtonsStayWhereTheyAre`: `today_add` and "See the week" have the same bounds with and without `HALLOWEEN`, and a tap on each still calls `onAdd` / opens the tab.
  - Screenshots: `today_halloween_dark` / `_light` (the `today` sample, `HALLOWEEN`).

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*NavRail*' :capability:calendar:testDebugUnitTest`. Expected: FAIL, the pin aside.
- [ ] **Step 2: Implement** per the Interfaces and rulings 4 and 7.
- [ ] **Step 3: Images.** **New:** `today_halloween_dark`, `today_halloween_light` (a faint web in the corner behind the header; the "+" and "See the week" untouched). **Changes:** `home_halloween_dark`, `home_halloween_light` (the pumpkin in the rail, the web on Today). No other image changes.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app capability/calendar
git commit -m "Put a pumpkin in the rail and a cobweb on the Today card while Halloween shows"
```

---

### Task 5: The moon and the drifting bats (spec §3, §4.3; rulings 8, 9; Review Focus 2)

**Review:** opus. Nothing animates while the screen is dark, Settings is open, Home isn't showing or animations are off; a step redraws only the sky.

**Files:**
- Modify `core/ui/.../HalloweenArt.kt` (`HalloweenSky`, `batPositions`), `Seasonal.kt` (`SeasonalDimens.moonRadius = 34.dp`, `moonEnd = 84.dp`, `moonTop = 70.dp`, `batSpan = 26.dp`; `BAT_LAP_MS = 90_000L`, `SKY_FRAME_MS = 50L`).
- Modify `app/.../SetupWiring.kt` (`skyDrifts`), `app/.../shell/ui/CulveryShell.kt` (`skyDrifts: Boolean = false`; the sky first in Home's `Box`, `Modifier.matchParentSize()`, only when `Culvery.seasonal == HALLOWEEN`), `app/.../MainActivity.kt` (`animatorsOn` state set in `onResume`; passes `skyDrifts(screen, state.settingsOpen, animatorsOn)`).
- Test: `core/ui` `HalloweenSkyTest.kt` (new); `app` `SkyDriftTest.kt` (new, beside `CameraMarksTest`), `shell/ui/CulveryShellSkyTest.kt` (new); `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `HalloweenInks.moon`, `.bat`, `Screen`.
- Produces:
  - `internal fun batPositions(elapsedMillis: Long, size: Size, batSpanPx: Float): List<Offset>`: ruling 8's four bats; x wraps at `size.width + batSpanPx`.
  - `@Composable fun HalloweenSky(drifting: Boolean, modifier: Modifier = Modifier, elapsed: MutableLongState = remember { mutableLongStateOf(0L) })`: tag `halloween_sky`, `clearAndSetSemantics {}`; while `drifting`, a `LaunchedEffect(drifting)` loop of `withFrameMillis` (advancing `elapsed` from where it stopped) then `delay(SKY_FRAME_MS)`; `elapsed` read only inside the `Canvas` draw lambda.
  - `internal fun skyDrifts(screen: Screen, settingsOpen: Boolean, animatorsOn: Boolean): Boolean`.
  - `CulveryShell(…, skyDrifts: Boolean = false, …)`.

- [ ] **Step 1: The failing tests.**
  - `HalloweenSkyTest` (`mainClock.autoAdvance = false`):
    - `batsDriftAScreenWidthEveryLap`: `batPositions(0, 1000×600, 26)` vs `(45_000, …)`: each x moved by half of 1026 (mod 1026); at 90 000 equal to 0; y unchanged.
    - `theSkyDriftsWhileAsked`: `drifting = true`, advance 1 000 ms → `elapsed` between 900 and 1 000.
    - `stillMeansNoFrames`: `drifting = false`, advance 10 000 ms → `elapsed` unchanged; then `true`, advance 500 → it goes on from where it stood, not from 0 nor jumping by 10 000.
    - `aStillSkyStillDrawsTheBats`: `drifting = false` → the pixel at a bat's body (from `batPositions(0, …)`) is `HalloweenInks.bat(HalloweenNight)`; at the moon's centre `HalloweenInks.moon(HalloweenNight)`.
    - `theSkyIsSilent`: no node under `halloween_sky` has text, a description or an action.
  - `SkyDriftTest.theSkyDriftsOnlyAwakeOnHomeWithAnimations`: the eight combinations; only `(AWAKE, false, true)` is true; `ASLEEP` and `NIGHT_CLOCK` false.
  - `CulveryShellSkyTest`:
    - `theSkyIsBehindHomeWhileHalloweenShows`: `HALLOWEEN` on Home → `halloween_sky` exists and is drawn before the header (its bounds hold `home_clock`'s; `home_clock` still clickable-free and readable).
    - `noSkyOffHome`: selected tab `door` → no `halloween_sky`.
    - `noSkyWithoutATheme` (**pin**).
  - Screenshots: `home_halloween_dark` / `_light` change (drawn still at elapsed 0).

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*Sky*'`. Expected: FAIL, the pin aside.
- [ ] **Step 2: Implement** per rulings 8 and 9. Check on `home_halloween_*` that no bat sits under the clock's or date's glyphs at elapsed 0; if one does, move its height and say so (the contrast test already allows it).
- [ ] **Step 3: Images.** **Changes:** `home_halloween_dark`, `home_halloween_light` (a faint moon behind the weather, four faint bats). Every normal `home_*` image unchanged.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Draw a moon and drifting bats behind Home while Halloween shows, still while the screen is dark, Settings is open or animations are off"
```

---

### Task 6: The haunted organ (spec §2.6, §4.6; rulings 10, 11)

**Review:** sonnet.

**Files:**
- Create `tools/sound/door_organ.py` in `door_chime.py`'s style (docstring with usage and output, standard library only, `RATE`, the note and partial tables at the top) and run it: `python tools/sound/door_organ.py` writes `capability/door/src/main/res/raw/door_organ.wav`.
- Modify `capability/door/.../Chime.kt` (`DoorSound`, `Chime.play(sound, volume)`, `MediaPlayerChime` picks the resource), `DoorAlerts.kt` and `ui/DoorSettingsPage.kt` (call `play(DoorSound.CLASSIC, …)`, so behaviour is unchanged until Task 7).
- Test: `capability/door/src/test/.../ChimeTest.kt`, `TestDoor.kt` (`FakeChime`).

**Interfaces:**
- Produces: `enum class DoorSound(@RawRes internal val res: Int) { CLASSIC(R.raw.door_chime), ORGAN(R.raw.door_organ) }`; `fun interface Chime { fun play(sound: DoorSound, volume: Float): Boolean }`; `FakeChime.plays: List<Pair<DoorSound, Float>>`, with `volumes` kept as `plays.map { it.second }` so existing tests stand.

- [ ] **Step 1: The failing tests.**
  - `ChimeTest.bothSoundsAreBundledWavs`: for each `DoorSound`, `openRawResource(res)` starts `RIFF`…`WAVE`, PCM, 1 channel, 22 050 Hz, 16 bits; `ORGAN`'s length (data bytes / 44 100) is between 3.4 and 3.8 s; `CLASSIC`'s is unchanged at 3.2 s (**pin** for the chime).
  - `ChimeTest.theOrganIsLoudEnough`: `ORGAN`'s peak sample is at least 0.9 × 32 767 and its RMS over the notes (0–3.5 s) at least 0.12 × 32 767, so it isn't quieter than the chime at the same volume (compute the chime's RMS in the test and assert the organ's ≥ 0.8 × it).
  - `ChimeTest.theOrganPlaysOnce`: no gap of silence (|sample| < 0.01 × 32 767 for 0.5 s) before 3.0 s, so the phrase isn't repeated.
  - `DoorAlertsTest.aNewRingInFrontShowsTheCardAndChimesAtTheVolume` (**pin**): add `chime.plays.single().first == DoorSound.CLASSIC`; every other existing test passes unchanged.

  Run `./gradlew :capability:door:testDebugUnitTest`. Expected: FAIL (compile, then the organ file missing), the pins aside.
- [ ] **Step 2: Implement** per rulings 10 and 11, then run the script. Listen once on the PC and say in the report whether it reads as an organ playing the Toccata's opening. The WAV is about 160 KB.
- [ ] **Step 3: The gate, then commit.** No screenshot changes.
```bash
git add tools/sound/door_organ.py capability/door
git commit -m "Add a haunted organ doorbell sound, generated by our own script, and let the chime play either sound"
```

---

### Task 7: Settings › Door's "Doorbell sound"; a ring follows the theme (spec §2.7, §4.6; ruling 11; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify `capability/door/.../DoorSettings.kt` (`DoorPrefs.doorbell`, `setDoorbell`, key `doorbell_sound`), `Chime.kt` (`DoorbellChoice`, `soundFor`), `DoorWords.kt` (the Copy), `DoorAlerts.kt` (plays `prefs().doorbell.soundFor(theme())`), `DoorCapability.kt` (takes `ShowingTheme`, passes it to the page), `ui/DoorSettingsPage.kt` (the section and rows; Play chime plays the chosen sound).
- Test: `DoorSettingsTest`, `DoorAlertsTest`, `DoorAlertsInjectedTest`, `ui/DoorSettingsPageTest.kt`, `ui/DoorSettingsScreenshotTest.kt`, `DoorCapabilityTest`.

**Interfaces:**
- Consumes: Task 6's `DoorSound`, `Chime`; Task 1's `ShowingTheme`, `SeasonalTheme`.
- Produces:
  - `enum class DoorbellChoice { FOLLOW_THEME, CLASSIC, ORGAN }`; `fun DoorbellChoice.soundFor(showing: SeasonalTheme?): DoorSound`.
  - `DoorPrefs.doorbell: DoorbellChoice = FOLLOW_THEME`; `suspend fun DoorSettings.setDoorbell(choice: DoorbellChoice)`.
  - `DoorAlerts` internal constructor gains `theme: () -> SeasonalTheme?` (last); the `@Inject` one gains `showing: ShowingTheme` and passes `{ showing.showing.value }`.
  - `DoorSettingsUi.doorbell: DoorbellChoice`; `DoorSettingsActions.onDoorbell: (DoorbellChoice) -> Unit`; tags `door_doorbell_follow`, `door_doorbell_classic`, `door_doorbell_organ`.
  - `DoorSettingsHost(…, showing: StateFlow<SeasonalTheme?>)`; Play chime calls `chime.play(p.doorbell.soundFor(showing.value), p.volume)`.

- [ ] **Step 1: The failing tests.**
  - `DoorSettingsTest`:
    - `theDoorbellFollowsTheThemeByDefault`: a fresh store → `FOLLOW_THEME`.
    - `theDoorbellChoiceIsKept`: `setDoorbell(ORGAN)` → reread (new instance over the same file) `ORGAN`.
    - `anUnknownDoorbellValueReadsAsFollowTheTheme`: the key holding `"KAZOO"` → `FOLLOW_THEME`, no throw.
  - `soundFor` (in `DoorSettingsTest` or a new `DoorbellChoiceTest`): `FOLLOW_THEME` with `HALLOWEEN` → `ORGAN`, with null → `CLASSIC`; `CLASSIC` with `HALLOWEEN` → `CLASSIC`; `ORGAN` with null → `ORGAN`.
  - `DoorAlertsTest`:
    - `aRingDuringHalloweenPlaysTheOrgan`: `FOLLOW_THEME`, theme `HALLOWEEN` → `plays == [(ORGAN, volume)]`, once.
    - `aRingPlaysTheThemesSoundAsItIsNow`: theme flips from `HALLOWEEN` to null between two rings 11 s apart → `ORGAN` then `CLASSIC`.
    - `quietHoursStillSilenceTheOrgan` (**pin**, as `quietHoursKeepTheCardButNotTheChime`): `ORGAN` chosen, inside quiet hours → no play.
  - `DoorAlertsInjectedTest` builds with a `ShowingTheme` whose `showing` is `MutableStateFlow(null)`.
  - `DoorSettingsPageTest`:
    - `theDoorbellSoundRows`: the section "Doorbell sound" and three radio rows in a selectable group, "Follow the theme" with its line verbatim, "Classic chime", "Haunted organ"; `FOLLOW_THEME` selected by default.
    - `choosingASoundSavesIt`: tapping "Haunted organ" → `onDoorbell(ORGAN)`; the host saves it.
    - `playChimePlaysWhatARingWould`: host with `FOLLOW_THEME` and `HALLOWEEN` → `plays == [(ORGAN, 1.0f)]`; with `CLASSIC` chosen → `CLASSIC`.
  - Screenshots: `settings_door_dark` / `_light` change; new `settings_door_halloween_dark` / `_light` (`ORGAN` chosen, under `HALLOWEEN`).

  Run `./gradlew :capability:door:testDebugUnitTest`. Expected: FAIL, the pin aside.
- [ ] **Step 2: Implement** per ruling 11 and the Copy. The rows sit after the existing "Chime" section's title, before the volume slider and "Play chime".
- [ ] **Step 3: Images.** **New:** `settings_door_halloween_dark`, `settings_door_halloween_light`. **Changes:** `settings_door_dark`, `settings_door_light` (the three rows added). `settings_door_steps_dark` changes only if the rows are on screen in it; list it if so. No other `door_*` image.
- [ ] **Step 4: The gate, then commit.**
```bash
git add capability/door
git commit -m "Let Settings › Door choose the doorbell sound, following the theme by default, so a ring plays the haunted organ while Halloween shows"
```

---

### Task 8: Settings › Display: preview each theme until midnight (spec §2.1, §3, §4.5 stage 1)

**Review:** sonnet.

**Files:**
- Modify `app/.../display/DisplayWords.kt` (the Copy's stage 1 strings), `display/ui/DisplaySettingsPage.kt` (`DisplayPage` takes `SeasonalThemes`; the section after Sleep and the camera row; `DisplaySettingsUi.previewing`, `DisplaySettingsActions.onPreview`).
- Test: `display/ui/DisplaySettingsPageTest.kt`, `display/ui/DisplaySettingsScreenshotTest.kt`.

**Interfaces:**
- Consumes: Task 2's `SeasonalThemes.previewing`, `preview(theme, on)`.
- Produces: `DisplaySettingsUi.previewing: SeasonalTheme?` (default null); `DisplaySettingsActions.onPreview: (SeasonalTheme, Boolean) -> Unit`; tag `display_preview_{name.lowercase()}` (`display_preview_halloween`).

- [ ] **Step 1: The failing tests.**
  - `DisplaySettingsPageTest`:
    - `eachThemeHasAPreview`: the section "Seasonal themes", one switch row per `SeasonalTheme` named "Preview Halloween", off, line "Shows Halloween on the wall until midnight.".
    - `turningThePreviewOnSaysWhenItEnds`: `previewing = HALLOWEEN` → on, line "Showing now. It ends at midnight.".
    - `theSwitchPreviews`: tap → `onPreview(HALLOWEEN, true)`; on, tap → `(HALLOWEEN, false)`.
    - `theHostPreviewsThroughSeasonalThemes`: the host over a `SeasonalThemes` with a fake clock at 2026-11-05 → tapping turns `showing` to `HALLOWEEN`; tapping again turns it off.
    - `aPreviewEndingAtMidnightTurnsTheSwitchOff`: the host, preview on, the clock to 00:00 the next day → the switch reads off.
  - Screenshots: new `settings_display_preview_dark` / `_light` (preview on, under `HALLOWEEN`).

  Run `./gradlew :app:testDebugUnitTest --tests '*DisplaySettings*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Copy, with `HhSectionLabel` and `HhSwitchRow`.
- [ ] **Step 3: Images.** **New:** `settings_display_preview_dark`, `settings_display_preview_light`. **Changes:** `settings_display_allowed_dark`, `settings_display_allowed_light`, `settings_display_not_asked_dark`, `settings_display_refused_dark` (the section at the foot; if the foot is off screen in one, it doesn't change: drop it from the list).
- [ ] **Step 4: The gate, then commit.**
```bash
git add app
git commit -m "Add a preview for each seasonal theme to Settings › Display, ending at midnight or when turned off"
```

---

### Task 9: Stage 1 on the tablet (spec §2.8, §5, §7)

**Controller and user only. Needs the SM-T510 on the cable.** 4e's tablet rules apply: a signed release with `install -r`, never uninstall, never wipe; save and restore every setting changed. No subagent.

- [ ] **Step 1: The gate on the branch**, then a short look on `culvery-5` with a debug build (`install -r`): Halloween at night and by day (the status bar's Day/Night preview); the pumpkin, web, moon, bats drifting; the clock in Creepster with the header still; the night clock unchanged; Settings › Display's preview; Settings › Door's three sounds with Play chime. Faults are collected and asked about before any fix.
- [ ] **Step 2: The release.** `./gradlew :app:assembleRelease`; `grep -c DebugClock app/build/outputs/mapping/release/mapping.txt` prints 0; copy the mapping to `culvery-notes/mappings/` named for the commit.
- [ ] **Step 3: Install.** `adb -s <tablet> install -r app/build/outputs/apk/release/app-release.apk`. Never uninstall.
- [ ] **Step 4: With the user:** Halloween shows (29–31 October are in its dates); the user looks across the room by day and at night; Play chime with Follow the theme plays the organ, at the tablet's notification volume; the screen going dark stops the bats (a minute's `adb -s <tablet> shell dumpsys gfxinfo uk.co.siland.culvery` with the screen asleep shows no new frames); `HeatLog`'s lines over the evening are no warmer than the evening before (`adb -s <tablet> logcat -d -s HeatLog`).
- [ ] **Step 5: Record** under "Plan 14 stage 1: on the tablet" in `docs/superpowers/plans/2026-09-23-plan1-followups.md`, with no names; commit: `git commit -am "Record Plan 14's first stage on the tablet"`.

---

### Task 10: Stage 2: the dates stored, read, and the next theme (spec §4.1, §4.5; rulings 12, 14)

**Review:** sonnet.

**Files:**
- Modify `app/.../display/DisplaySettings.kt` (`DisplayPrefs.seasonDates`, `setSeasonDates`, `resetSeasonDates`), `display/SeasonalThemes.kt` (the `@Inject` constructor takes `DisplaySettings` and passes `settings.read.filterNotNull().map { it.seasonDates }`; `nextTheme`).
- Test: `display/DisplaySettingsTest.kt`, `display/SeasonalThemesTest.kt`; `core/ui` `SeasonalTest.kt`.

**Interfaces:**
- Produces:
  - `DisplayPrefs.seasonDates: Map<SeasonalTheme, SeasonDates> = defaultSeasonDates()`.
  - `suspend fun DisplaySettings.setSeasonDates(theme: SeasonalTheme, dates: SeasonDates)`; `suspend fun DisplaySettings.resetSeasonDates(theme: SeasonalTheme)`.
  - `internal fun nextTheme(today: LocalDate, dates: Map<SeasonalTheme, SeasonDates>): Pair<SeasonalTheme, LocalDate>?` (ruling 14); `SeasonalThemes.next: StateFlow<Pair<SeasonalTheme, LocalDate>?>`, null while a theme is in its dates or the clock is unread.

- [ ] **Step 1: The failing tests.**
  - `DisplaySettingsTest`:
    - `seasonDatesDefaultToEachThemes`: fresh → Halloween 24 Oct – 31 Oct.
    - `editedDatesAreKept`: `setSeasonDates(HALLOWEEN, 20 Oct – 2 Nov)` → reread the same.
    - `puttingBackTheDefaultsRemovesTheKeys`: after `resetSeasonDates` → defaults, and the file holds neither key.
    - `unreadableSeasonDatesFallBackToTheDefaults`: `season_halloween_start = 1332` (13th month) → defaults for both ends; only `_end` stored → defaults.
    - `theLeapDayIsAValidDate`: `229` reads as 29 February.
  - `SeasonalTest.aLeapDayEndInAShortYearEndsOnThe28th`: `SeasonDates(20 Feb, 29 Feb)` contains 2027-02-28 and not 2027-03-01.
  - `SeasonalThemesTest`:
    - `editedDatesMoveTheTheme`: dates 1 Nov – 5 Nov, 2026-11-03 → `HALLOWEEN`; 2026-10-29 → null.
    - `nextIsTheSoonestStart`: 2026-11-05, defaults → `(HALLOWEEN, 2027-10-24)`; 2026-10-01 → `(HALLOWEEN, 2026-10-24)`; on 2026-10-29 → null.
    - `nextWrapsTheNewYear`: dates 20 Dec – 2 Jan, on 2026-12-01 → `2026-12-20`; on 2027-01-03 → `2027-12-20`.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*DisplaySettings*' --tests '*SeasonalThemes*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 12 and 14.
- [ ] **Step 3: The gate, then commit.** No screenshot changes.
```bash
git add app core/ui
git commit -m "Store each seasonal theme's dates with the display's settings, falling back to its defaults, and work out which theme comes next"
```

---

### Task 11: Stage 2: Settings › Display edits the dates and says what's next (spec §4.5; ruling 13)

**Review:** sonnet.

**Files:**
- Modify `app/.../display/DisplayWords.kt` (the stage 2 Copy; `fun dayMonth(d: MonthDay): String` = "24 Oct"; `fun nextLine(theme, date): String`), `display/ui/DisplaySettingsPage.kt` (a dates row per theme; the dates sheet as an overlay through `LocalOverlayHost`, as Settings' other sheets; the "Next:" line).
- Test: `display/ui/DisplaySettingsPageTest.kt`, `display/ui/DisplaySettingsScreenshotTest.kt`.

**Interfaces:**
- Consumes: Task 10's `seasonDates`, `setSeasonDates`, `resetSeasonDates`, `SeasonalThemes.next`; `HhListRow`, `HhSheet`, `HhDateGrid`, `HhFooterButton`, `HhButton`, `HhMessageLine`.
- Produces: `DisplaySettingsUi.dates: Map<SeasonalTheme, SeasonDates>`, `.next: Pair<SeasonalTheme, LocalDate>?`, `.today: LocalDate`; `DisplaySettingsActions.onDates: (SeasonalTheme, SeasonDates?) -> Unit` (null puts back the defaults); `internal fun checkRange(first: LocalDate, last: LocalDate): String?` (the refusal, or null); tags `display_dates_halloween`, `display_dates_sheet`, `display_dates_save`, `display_dates_reset`, `display_dates_refused`, `display_next`.

- [ ] **Step 1: The failing tests.**
  - `checkRange`: 24 Oct → 31 Oct null; 31 Oct → 24 Oct "The last day is before the first. Tap a last day on or after 31 Oct."; 20 Dec 2026 → 2 Jan 2027 null; 24 Oct 2026 → 24 Oct 2027 "A theme can show for a year at most. Tap a last day before 24 Oct 2027." (the year shown only when it differs from the first day's).
  - `DisplaySettingsPageTest`:
    - `eachThemeShowsItsDates`: `display_dates_halloween` reads "Halloween" and "24 Oct – 31 Oct", click label "Change the dates".
    - `pickingTwoDaysSavesThem`: open, tap 20 Oct then 2 Nov, "Save dates" → `onDates(HALLOWEEN, SeasonDates(20 Oct, 2 Nov))`, the sheet closes.
    - `aLastDayBeforeTheFirstIsRefused`: tap 31 Oct then 24 Oct → `display_dates_refused` reads the line; "Save dates" is disabled; nothing saved.
    - `puttingBackTheDefaults`: with 20 Oct – 2 Nov saved, "Put back 24 Oct – 31 Oct" → `onDates(HALLOWEEN, null)`.
    - `cancelSavesNothing`.
    - `theNextLineShowsOutsideTheDates`: `next = (HALLOWEEN, 2027-10-24)` → `display_next` "Next: Halloween from 24 October"; `next = null` → absent.
  - Screenshots: new `settings_display_dates_sheet_dark` / `_light` (20 Oct – 2 Nov picked), `settings_display_next_dark` / `_light`.

  Run `./gradlew :app:testDebugUnitTest --tests '*DisplaySettings*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 13 and the Copy.
- [ ] **Step 3: Images.** **New:** the four above. **Changes:** `settings_display_preview_dark`, `settings_display_preview_light`, and any of the four `settings_display_*` images whose foot shows the section.
- [ ] **Step 4: The gate, then commit.**
```bash
git add app
git commit -m "Let Settings › Display change each seasonal theme's dates or put back the defaults, and say which theme comes next"
```

---

### Task 12: Simplify the whole branch

- [ ] **Step 1:** Run the `simplify` skill over `git diff main...HEAD`. Look hardest at: `SlotClock` against a plain `Text` with a fixed width; `SeasonalThemes`' flows (one `combine` if two read alike); `HalloweenArt`'s paths (shared builders); `DoorSettingsPage`'s rows beside Party's radio sheets.
- [ ] **Step 2:** For each change, the module's tests, then the gate.
- [ ] **Step 3:** Commit when anything changed: `git commit -am "Simplify the seasonal theme code"`.

---

### Task 13: The emulator walk-through on `culvery-5` (spec §7)

**Rules:** only `culvery-5` with a debug build and `install -r`; the user types real PINs, Claude enters the debug sample PINs; faults are collected and asked about before fixing; no house device touched.

- [ ] **Step 1: Walk** (one line each).
  1. In the dates (the emulator's date today): Halloween by night and by day via the status bar's Day/Night preview; every tab, a sheet, the PIN pad, a toast, Settings and the door card in Halloween's colours.
  2. The pumpkin, the web, the moon and bats; the bats drift; the clock in Creepster, the header still across a minute's change.
  3. The night clock (`DebugClockReceiver` into the night hours): unchanged; the bats stop (Layout Inspector or `dumpsys gfxinfo` shows no frames).
  4. Settings over Home, and "Remove animations" on in Android's settings: the bats still.
  5. Outside the dates (`DebugClockReceiver` forward past 1 November 00:00): the colours fade back at midnight with no restart.
  6. Preview on outside the dates: Halloween shows; off: it goes; on again, then the clock past midnight: it ends and the switch reads off.
  7. Settings › Display's dates: change to 20 Oct – 2 Nov and back; a last day before the first is refused; "Put back"; the "Next:" line outside the dates.
  8. Settings › Door: each sound with Play chime; a sample ring with each choice, during and outside Halloween.
- [ ] **Step 2: The fault loop**; put back every setting changed; `DebugClockReceiver --el minutes 0`; `emu kill`.
- [ ] **Step 3: Record** under "Plan 14 (seasonal themes): emulator walk-through", no names. Commit: `git commit -am "Record Plan 14's emulator walk-through"`.

---

### Task 14: Final review, merge, install

- [ ] **Step 1:** Opus reviews `git diff main...HEAD` against the spec and this plan, looking first at readability in both Halloween sets, the night clock, the bats' stopping, midnight, the Door seam and the font's licence. One fix wave, each fix with its failing test first, then the gate.
- [ ] **Step 2:** Finish with `superpowers:finishing-a-development-branch`: only on the user's say, `git merge --ff-only plan-14-seasonal-themes` on `main`, the gate on `main`; never push. Move the design cuts to the follow-ups.
- [ ] **Step 3:** The release from the merged `main` on the tablet with `install -r`, the mapping kept as in Task 9.

---

## Walk-through

1. 24–31 October: the wall is Halloween by day and by night, with the normal look back on 1 November at midnight.
2. Halloween: orange on plum by night, burnt orange on cream by day; a pumpkin for the mark; a cobweb on Today; a moon and slow bats behind Home; the big clock in Creepster.
3. The night clock is exactly as it was.
4. Settings › Display: "Preview Halloween" until midnight; each theme's dates, with "Put back"; "Next: Halloween from 24 October" outside the dates.
5. Settings › Door: "Doorbell sound" (Follow the theme, Classic chime, Haunted organ); Play chime plays the one chosen.

**Design cuts to say again:**
- the day set's moved values (`surf`, `surf2`, `ink`, `accentSoft`) and the pink-red warning colour;
- the clock in orange, Creepster's digits in fixed slots;
- the moon and bats' size, number, speed and faintness; the web's size and colour;
- the pumpkin's drawing;
- the organ's arrangement and loudness on the tablet;
- the radio rows for the doorbell, the switch for the preview, the date sheet's two taps.

## Not in this build, on purpose

- Christmas or any other theme; a "never show" switch (change the dates instead).
- A theme for the night clock, the launcher icon or the house's devices.
- Keeping a preview across a restart.

---

## Spec coverage (14 → tasks)

| Spec | Task |
|---|---|
| §1 intent, goals, anti-goals | 1, 2, 5, 9 |
| §2.1 switching by date, preview only | 2, 8, 10, 11 |
| §2.2 full dress: colours, pumpkin, cobweb, moon and bats, Creepster | 1, 3, 4, 5 |
| §2.3 Creepster from Google Fonts, licence checked | 3 |
| §2.4 day version, Day/Night preview | 1, 2, 9 |
| §2.5 night clock unchanged | 2 |
| §2.6 the organ, generated | 6 |
| §2.7 Settings › Door's doorbell sound | 7 |
| §2.8 staged delivery | 9 (stage 1), 10–11 (stage 2) |
| §3 every screen, bats stopping, pinker red, person colours, preview to midnight, PINs, adding a theme | 1, 2, 5, 7, 8 |
| §4.1 which theme, wrap, overlap, midnight, stored dates | 1, 2, 10 |
| §4.2 colours, contrast over four sets, danger vs accent, screenshots | 1–5 |
| §4.3 drawn touches | 4, 5 |
| §4.4 clock face | 3 |
| §4.5 Settings › Display | 8, 11 |
| §4.6 doorbell | 6, 7 |
| §5 constraints | all; 9, 13, 14 |
| §6 left to the builder | rulings 1–14 |
| §7 walk-through checks | 9, 13 |
