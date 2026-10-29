# Culvery — Plan 14: Seasonal themes, Halloween first Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–4, 6–9 and 11–12 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 5 (an optional early tablet checkpoint) and Task 10 (stage 1 on the tablet) are the controller with the user and the son's SM-T510. Task 13 is the controller. Task 14 is the controller on the `culvery-5` AVD with the user. Task 15 needs the user and the SM-T510.

**Goal:** a seasonal theme dresses up the wall tablet by its dates (Halloween, 24–31 October by default): its own day and night colours on every screen, a pumpkin for the mark, a cobweb on the Today card, a moon and drifting bats behind Home, a Creepster clock, and a haunted organ for the doorbell, with a preview in Settings › Display and the doorbell sound chosen in Settings › Door.

**Architecture:**
- **`:core:ui`** holds the seam: `SeasonalTheme` (each with its default dates and its `look`), `SeasonDates`, `ShowingTheme` (a `StateFlow<SeasonalTheme?>`, for code outside composition), `LocalSeasonalTheme` / `Culvery.seasonal` / `Culvery.look` (for composition), and **`SeasonalLook`**: one bundle per theme of its day and night colours, clock face, mark, card corner, sky and doorbell sound. Call sites ask the look (`Culvery.look?.mark`), never `== HALLOWEEN`; adding Christmas is one more look and its dates. Halloween's look, drawings and Creepster live here. Door and Calendar already depend on `:core:ui`, so no new module edge.
- **`:app`** decides which theme shows (`SeasonalThemes`: the date, the stored dates, a preview that ends at midnight), binds it as `ShowingTheme`, hands it to `CulveryTheme`, puts the look's mark and sky on the shell, and keeps the sky still when the screen is dark, something is held over Home, or animations are off. Settings › Display gets the preview (stage 1) and the dates (stage 2).
- **`:capability:door`** gets the organ WAV, `DoorSound`, the "Doorbell sound" choice, and plays the sound the choice and the showing theme's look name, falling back to the classic chime. **`:capability:calendar`** draws the look's card corner on `TodayCard`.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1; Python 3 standard library for the organ. **No new library.** One new asset: Creepster (SIL OFL 1.1), approved by the user on 2026-10-29, from google/fonts at commit `6157dfaa640ca5738b4d1a7070fe389c3036d7fc`.

**Spec:** `docs/superpowers/specs/2026-10-29-culvery-14-seasonal-themes-design.md`, all of it. Every "Call I made" there is decided; this plan doesn't reopen them, except where the review rulings below say so (S4: the preview also changes the doorbell).

**Not this plan** (spec non-goals): no house devices; no Christmas; no downloaded sound; no launcher icon change; no new readability rules; no Automatic / Off / "on now" setting (switching is by date only, the preview is the only manual way).

**Previous plans (format, shared parts):** `docs/superpowers/plans/2026-10-14-culvery-05-ring-doorbell.md` (Task 14: `door_chime.py`, `Chime`, `DoorAlerts`), `docs/superpowers/plans/2026-10-19-culvery-08-sleep-and-wake.md` (`DisplayState`, the night clock, Settings › Display), `docs/superpowers/plans/2026-10-29-culvery-13-party-mode.md` (house rules, the UI-task ending).

**Builds on:** branch `plan-14-seasonal-themes` in the one checkout `C:\Users\si\code\siland\culvery`, cut from `main` at 0cbc6890 (the spec is committed on it at 1712b67d). No worktrees. Never push.

**Deadline:** stage 1 (Tasks 1–10) must be on the SM-T510 by **30 October 2026**. It is ordered by what matters most on the 31st: the colours and the theme seam (1, 2), the organ and its setting (3, 4), an optional early install (5), the clock (6), the pumpkin and cobweb (7), the preview (8), and the bats last (9). **If time runs short, Task 9 is the first cut**: Task 10 installs without it and it moves to stage 2. Stage 2 (Tasks 11–12) and the end tasks follow.

## Review rulings (2026-10-29)

The user approved the plan and ruled every review finding as recommended. Each is folded in where named.

| Id | Outcome |
|---|---|
| B1 | Stage 1 reordered: colours and seam (1, 2), organ and Door setting (3, 4), optional early tablet checkpoint (5), Creepster clock (6), pumpkin and cobweb (7), preview (8), bats last (9), the first cut. |
| B2 | With S2: `graphicsLayer()` on the sky, about 15 fps, a frame-time sample on the tablet (Tasks 9, 10). |
| B3 | By day the bats are a darker ink (`0xFFDDD0C0`); the moon stays out of the weather band, or the day moon is dropped if it can't; provisional, in the design cuts (ruling 8, Task 9). |
| B4 | A NATIVE pixel test that no `accent` pixel lies at or below `home_date`'s top (Task 6). |
| B5 | A wrapper `Box` tagged `culvery_mark` holds whichever mark draws; the pumpkin is tagged `pumpkin_mark` inside it (Task 7). |
| B6 | The cobweb test compares with and without the theme (the corner differs; a thread on the diagonal), not an exact pixel (Task 7). |
| B7 | The organ is 4.0 s with a short fade; the WAV test allows 3.4–4.1 s (ruling 10, Task 3). |
| B8 | "Doorbell sound" is its own `HhSectionLabel`, the page's first section, before "Chime" (Task 4). |
| B9 | `DoorCardScreenshotTest`'s local snap gets `seasonal`; `onPreview` gets a default (Tasks 1, 8). |
| B10 | `drawCobweb`'s size parameter is `span` (Task 7). |
| S1 | `MediaPlayerChime` falls back to `CLASSIC` when `ORGAN` won't load or start, logging the enum name only; a fake-player test; the tablet check rings with the organ (Tasks 3, 10). |
| S2 | As B2. |
| S3 | Creepster from the pinned commit above; `METADATA.pb`'s `license: "OFL"` checked as well as `OFL.txt`; the commit and the TTF's sha256 recorded beside the README licence line; no subsetting (Task 6). |
| S4 | A preview also changes the doorbell; the Settings line says "Shows Halloween on the wall and plays its doorbell until midnight." (Tasks 4, 8). |
| S5 | `animatorsOn` starts from `ValueAnimator.areAnimatorsEnabled()` at creation as well as on resume; the sky is still while `holding()` (sheets, PIN pad, door card, Settings) (ruling 9, Task 9). |
| S6 | Noted in the follow-ups: a ring just after a restart, before the clock is read, plays the classic chime, never silence (Task 10). |
| T1 | Bats may cross the clock (faint; the contrast test covers their colours); said in ruling 8. |
| T2 | With B6: the clock test asserts Creepster on the digits' style; the night clock pin runs through the real shell path, and the byte `cmp` stays (Tasks 2, 6). |
| T3 | Bad stored dates parameterised: {1332, 231, 0, -1, 1301, 100}, a wrong-typed key, a valid start with an invalid end (Task 11). |
| T4 | The wrap point pinned exactly; two frame steps advance about two `SKY_FRAME_MS` (Task 9). |
| T5 | `AnimatorsOn` is injectable and tested; one `StandardTestDispatcher` virtual-time test that `SeasonalThemes` follows the clock; the overlap test dropped for `aPreviewFromAnotherDayIsIgnored` (Tasks 2, 9). |
| T6 | Logic tests go red against a stub with the final signature returning a wrong value, not only a compile failure (Global Constraints). |
| T7 | A final look at `home_halloween_*` at the end of the bats task (Task 9). |
| T8 | `SeasonalLook` (above); `SlotClock` is a `Row` of fixed-width `Box`es measured once per style; Task 4 names `HhRadioRow` in a `selectableGroup`; the cobweb's geometry and the organ's voicing beyond its timing are the implementer's. |

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
- Settings › Display (stage 1): section "Seasonal themes"; per theme a switch row "Preview {label}" with the line "Shows {label} on the wall and plays its doorbell until midnight." when off and "Showing now. It ends at midnight." when on.
- Settings › Display (stage 2): per theme a row "{label}" with "{start} – {end}" as its line ("24 Oct – 31 Oct"), click label "Change the dates"; its sheet titled "{label} dates" with the line "Tap the first day, then the last.", buttons "Save dates", "Put back {default start} – {default end}", "Cancel"; refusals "The last day is before the first. Tap a last day on or after {start}." and "A theme can show for a year at most. Tap a last day before {start + 1 year}."; under the section when nothing shows: "Next: {label} from {d MMMM}" ("Next: Halloween from 24 October").
- Theme label: "Halloween".

**From the house rules** (as Plan 13's Global Constraints; binding):
- **Platform:** package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only; **no new library; never change a version**; no deprecated API, and any one met is flagged to the user before use. This plan's APIs were checked and are current: `ValueAnimator.areAnimatorsEnabled()` (API 26), `Modifier.graphicsLayer()`, `withFrameMillis`, `mutableLongStateOf`, `rememberTextMeasurer`, `MonthDay`, `ImageVector.Builder.addPath`, `MediaPlayer.create(Context, Int, AudioAttributes, Int)`. Never `Settings.System.ANIMATOR_DURATION_SCALE` (deprecated).
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash, or `gradlew.bat`), plus `./gradlew -p build-logic :convention:test` in any task that adds a log line or a `TAG`.
  - Never plain `test`, `check` or `build`, and never a Gradle `install…` task. No subagent touches a device.
  - **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`**: a long temp path breaks Robolectric past MAX_PATH.
  - Scratch goes in `$LISTS` = `C:/t9`, a plain shell variable, never exported.
- **Tests that can fail:** every new test fails before its change; one that passes before it is labelled **pin**. **A logic test goes red on its assertion, not only on compiling** (T6): Step 1 adds the production function or member with its final signature returning a wrong value (`null`, `false`, the normal colours, `CLASSIC`, an empty list), runs the tests and sees them fail on assertions, then Step 2 writes the body. Timer and frame tests run on `compose.mainClock` with `autoAdvance = false` or on virtual time (`StandardTestDispatcher`), asserted one step before the boundary and at it. A fake clock is a `MutableStateFlow<LocalDateTime?>`.
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
- **Code:** call sites read the look (`Culvery.look`, `SeasonalTheme.look`), never compare with `SeasonalTheme.HALLOWEEN`; only `Seasonal.kt` and `HalloweenLook.kt` name it in main sources (`SeasonalSourceTest`, Task 1). Comments say only what the code can't, one line by default, spec refs as `(14 §4.3)`, never a task or ruling number. Layout and drawing numbers go in a named object (`SeasonalDimens` in `:core:ui`, `DisplayDimens` in `:app`). Log lines interpolate only `e::class.simpleName` and enum names; Task 3 adds the one new log line.
- **Privacy:** no personal data in logs, test names, fixtures or committed files. Sample data only.
- **Devices:** the emulator is `culvery-5`; `emulator-5554` holds a signed release and the household: never install on it. The tablet (SM-T510) gets `adb install -r` of a signed release only; **never uninstall, never wipe**. No Hue, Sonos, plug or Home Assistant change, ever, in this plan.
- **Git:** a commit is a plain sentence with no prefix and no trailer of any kind; never push. **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **Midnight and the clock: the preview turned on at 23:59, the 31 October → 1 November change, the clock not yet read at start, the clock set back across midnight (Plan 11b's `DebugClock`).**
   - **Expected:** the preview ends at the first minute whose date isn't the day it was turned on, forward or back; showing follows the date with no restart; before the clock is read nothing is themed and nothing throws.
   - **Tests** (Task 2): `SeasonalThemesTest.aPreviewEndsAtMidnight`, `aPreviewEndsWhenTheClockGoesBackADay`, `halloweenEndsAsNovemberStarts`, `nothingShowsBeforeTheClockIsRead`, `followsTheHouseholdClockOnVirtualTime`.
2. **The bats while nobody should see them move: the night clock, the dark screen, Settings, a sheet, the PIN pad or the door card over Home, another tab, "Remove animations" on.**
   - **Expected:** the sky's elapsed time doesn't advance; the bats are drawn still at their last place; Home on another tab has no sky at all.
   - **Tests** (Task 9): `SkyDriftTest.theSkyDriftsOnlyAwakeUnheldWithAnimations`, `AnimatorsOnTest.readsAndroidsSetting`, `HalloweenSkyTest.stillMeansNoFrames`, `aStillSkyStillDrawsTheBats`, `CulveryShellSkyTest.noSkyOffHome`.
3. **Text over the new colours and drawings: every checked pair in both Halloween sets, the clock over a bat, warning red beside the orange.**
   - **Expected:** every pair passes the 4d thresholds; `danger` and `accent` are at least 35° apart in hue in every set.
   - **Tests** (Task 1): `ContrastTest` over all four sets, `dangerAndAccentStayApart`, `textReadsOverTheSky`.
4. **The doorbell: the stored choice unreadable, the theme or a preview changing between choosing and ringing, Play chime with Follow the theme, the organ file failing.**
   - **Expected:** an unknown or missing value reads as Follow the theme; a ring plays the sound for the theme showing at that moment, a preview included; Play chime plays what a ring would play now; an organ that won't play falls back to the chime, never silence.
   - **Tests** (Task 3): `MediaPlayerChimeTest.anOrganThatWontLoadPlaysTheChime`, `anOrganThatWontStartPlaysTheChime`; (Task 4) `DoorSettingsTest.anUnknownDoorbellValueReadsAsFollowTheTheme`, `DoorAlertsTest.aRingPlaysTheThemesSoundAsItIsNow`, `DoorSettingsPageTest.playChimePlaysWhatARingWould`.
5. **The header jumping: Creepster's line box and descenders, its proportional digits from 11:11 to 00:00, the theme switching on at midnight.**
   - **Expected:** the clock's box is the same height in DM Sans and Creepster, the same width for every time, nothing of it draws into the date, and the weather items don't move.
   - **Tests** (Task 6): `HalloweenClockTest.theClockKeepsItsWidthEveryMinute`, `theHeaderKeepsItsHeightInEitherFace`, `theClockDrawsNothingIntoTheDate`, `theHeaderItemsDontMoveWhenTheFaceChanges`.

Stage 2 adds one more, owned by Task 11: **dates across the new year, 29 February, and unreadable stored values** (`SeasonalTest.aWrappedRangeCoversBothEnds`, `aLeapDayEndInAShortYearEndsOnThe28th`; `DisplaySettingsTest.unreadableSeasonDatesFallBackToTheDefaults`).

---

| # | Task | Review |
|---|---|---|
| 1 | The theme seam, `SeasonalLook` and Halloween's colours in `:core:ui`; the contrast tests over four sets | **opus** (readability, warning red) |
| 2 | Which theme shows: `SeasonalThemes`, the preview's midnight, the app wired; the night clock pinned | **opus** (midnight edges) |
| 3 | The haunted organ: the script, the WAV, `DoorSound`, `Chime` plays a sound with the chime as fallback | sonnet |
| 4 | Settings › Door's "Doorbell sound"; a ring follows the theme | sonnet |
| 5 | *Optional* early tablet checkpoint | controller + user, **needs the SM-T510** |
| 6 | Creepster and the Halloween clock face | sonnet |
| 7 | The pumpkin mark and the Today card's cobweb | sonnet |
| 8 | Settings › Display: preview each theme until midnight | sonnet |
| 9 | The moon and the drifting bats, still when nobody should see them move (**first cut**) | **opus** (battery, heat) |
| 10 | **Stage 1 on the tablet** | controller + user, **needs the SM-T510** |
| 11 | Stage 2: the dates stored, read, and the next theme | sonnet |
| 12 | Stage 2: Settings › Display edits the dates and says what's next | sonnet |
| 13 | Simplify the whole branch | controller |
| 14 | The emulator walk-through on `culvery-5` | controller + user |
| 15 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why (B1):** the seam and colours first (1) since everything reads them, then what decides the theme and wires it into the app (2): from a build of Task 2 on, Halloween shows. The organ and its setting (3, 4) are what the family hears on the 31st and are independent of the drawings, so they come next, and Task 5 can put that much on the tablet if the evening allows. Then the clock (6), the pumpkin and web (7), and the preview (8). The bats (9) cost the most and matter least, so they come last and are cut first. Halloween's default dates contain 29–31 October, so stage 1 shows Halloween without stage 2. Each drawing task records the Home screenshots afresh.

## Rulings against the code

The spec leaves these to the builder, or they fall out of the code. Each is this plan's call.

1. **The seam** (`core/ui/.../Seasonal.kt`): `SeasonalTheme` is an enum, "no theme" is `null`. `ShowingTheme` sits in `:core:ui`, not `:core:plugin`, because `CulveryTheme` needs the enum and `:core:plugin` doesn't depend on `:core:ui`; Door and Calendar already depend on `:core:ui`. Composition reads `Culvery.seasonal` and `Culvery.look` (provided by `CulveryTheme`); Door's ring, outside composition, reads `ShowingTheme.showing`, bound in `:app` to `SeasonalThemes`.
2. **The look** (T8; `core/ui/.../Seasonal.kt`):
   ```kotlin
   enum class SeasonalSound { HAUNTED_ORGAN }

   /** What a theme changes (14 §3): its colours, and each optional touch; null leaves the normal look. */
   class SeasonalLook(
       val night: HhColors,
       val day: HhColors,
       /** The big Home clock's digits, in `accent`, each in a fixed slot. */
       val clockFace: TextStyle? = null,
       /** Drawn in the rail's mark spot, [width] wide; a picture only. */
       val mark: (@Composable (width: Dp) -> Unit)? = null,
       /** Drawn behind the Today card's content, over its surface, inside its clip. */
       val cardCorner: (DrawScope.(HhColors) -> Unit)? = null,
       /** Drawn behind Home's content; moves only while [drifting]. */
       val sky: (@Composable (drifting: Boolean, modifier: Modifier) -> Unit)? = null,
       val sound: SeasonalSound? = null,
   )
   ```
   `val SeasonalTheme.look: SeasonalLook` is an exhaustive `when` in `Seasonal.kt` (`HALLOWEEN -> HalloweenLook`), so a new theme must name its look. `HalloweenLook` (`core/ui/.../HalloweenLook.kt`) starts with its colours (Task 1) and gains its sound (4), face (6), mark and corner (7) and sky (9). `colorsFor(seasonal, dark)` reads the look's `night`/`day`, else `DarkColors`/`LightColors`.
3. **Halloween's colour sets** (`HalloweenLook.kt`). Starting values, checked against every `ContrastTest` pair with the spec's mockup values; the day set's `surf`, `surf2`, `ink` and `accentSoft` are moved from the mockup because the mockup's fail the clash tag, the outside-month alpha, `weather` on `surf2` and `accent` on `accentSoft`:

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
4. **Warning red apart from orange:** the hue gap in degrees (0–180) between `danger` and `accent`, at least **35°**, in all four sets. Today's normal `danger` (8°) would be 22° from the Halloween orange (30°), which is why Halloween's `danger` moves to a pink-red (344° / 343°).
5. **The drawings' colours** (`HalloweenInks`, in `HalloweenLook.kt`, so `ContrastTest` checks them): `moon(c) = c.accentSoft`; `bat(c)` = `c.surf2` at night and `DAY_BAT = Color(0xFFDDD0C0)` by day (B3: `surf2` is lighter than the day `bg` and vanishes); `web(c) = c.surf3`; the pumpkin's body `c.accent`, its stem `PUMPKIN_STEM = Color(0xFF5E8C31)`, its carved face `PUMPKIN_FACE = Color(0xFF1A0C02)` in both versions. What can sit over each is checked: over a bat, the clock's `accent` at 3.0 (large text) and `ink`, `mute` at 4.5; over the moon, nothing (ruling 8 keeps it clear of text). The day bat ink fails `weather` (4.02), which is why no bat lane crosses the header items (ruling 8).
6. **Which theme shows** (`app/.../display/SeasonalThemes.kt`): `showingOn(date, dates, preview)` = the preview's theme when `preview?.day == date`, else the first `SeasonalTheme.entries` whose dates contain `date`, else null. The preview is one `MutableStateFlow<ThemePreview?>`, in memory only; turning one on replaces any other; a preview whose `day` isn't today counts as off and is cleared on the next tick. Before the clock is read, `showing` is null. A preview changes everything the theme changes, the doorbell included (S4).
7. **The clock face:** Creepster at the clock's 100 sp, `lineBox(86.sp)` as today, no letter spacing, in `accent` (the mockup; 7.8:1 night, 4.9:1 day, over the 3:1 large-text line). `SlotClock` is a `Row` of `Box`es: each digit's `Box` is as wide as the widest of "0"–"9" in that style, measured once per style and density with `rememberTextMeasurer`; ":" takes its own width. This holds whether or not Creepster has tabular figures. Creepster is kept whole (S3: no subsetting).
8. **The sky** (layout fixed in dp; the implementer draws the bats and moon):
   - **Moon:** radius `SeasonalDimens.moonRadius` = 30 dp, centre `moonEnd` = 84 dp from Home's right and `moonTop` = 34 dp from its top, so it sits above the header items (whose top is about 79 dp down). If at the tablet's size it touches `home_header_items`, the day look drops the moon (B3) and the night keeps it only if it clears; the test decides.
   - **Bats:** 4, `batSpan` = 26 dp wing to wing, in lanes at 20 dp from Home's top (crossing the clock only, never the date or the header items; T1 accepts this, the colours are checked) and at the header's bottom + 40 dp, + 160 dp and + 280 dp, a quarter of the width apart. They drift left to right one screen width every `BAT_LAP_MS` = 90 000 ms, wrapping at `width + batSpan`.
   - **Cost (S2):** the drift updates at most every `SKY_FRAME_MS` = 66 ms (about 15 frames a second); the sky is its own layer (`Modifier.graphicsLayer()`) and reads the elapsed time only in its draw lambda, so a step redraws the sky layer and nothing else.
9. **When the bats move (S5):** `skyDrifts(screen, held, animatorsOn) = screen == Screen.AWAKE && !held && animatorsOn`, where `held` is `MainActivity`'s existing `holding(pinShowing, sheetShowing, alertShowing, settingsOpen, complete)`; off Home the sky isn't composed. `fun interface AnimatorsOn { fun now(): Boolean }`, bound to `ValueAnimator.areAnimatorsEnabled()`, read in `MainActivity.onCreate` and again in `onResume` (the SM-T510 is API 30, so the API 33 listener isn't used; changing "Remove animations" means leaving Culvery, which resumes it).
10. **The organ** (`tools/sound/door_organ.py`, 22 050 Hz 16-bit mono, **4.0 s**): the timing is fixed, the voicing is the implementer's. Notes (frequency Hz, start s, length s): `(880.00, 0.00, 0.14) (783.99, 0.14, 0.12) (880.00, 0.26, 0.86)`, then `(783.99, 1.30, 0.14) (698.46, 1.44, 0.14) (659.25, 1.58, 0.14) (587.33, 1.72, 0.30) (554.37, 2.02, 0.40) (587.33, 2.42, 1.20)`, then a fade to silence over the last 0.3 s ending at 4.0 s (B7). Additive organ partials leaning on the 2nd–4th harmonics for the small speaker (spec §4.6), peak-normalised to 0.95 as the chime is. Played once.
11. **Doorbell sound** (`:capability:door`): `enum class DoorbellChoice { FOLLOW_THEME, CLASSIC, ORGAN }`, stored by name under `doorbell_sound` in `door_settings`; `enum class DoorSound { CLASSIC, ORGAN }`; `fun DoorbellChoice.soundFor(showing: SeasonalTheme?): DoorSound`, where `FOLLOW_THEME` maps `showing?.look?.sound` with an exhaustive `when` (`HAUNTED_ORGAN -> ORGAN`, `null -> CLASSIC`). `Chime` becomes `fun interface Chime { fun play(sound: DoorSound, volume: Float): Boolean }`. `MediaPlayerChime` plays `CLASSIC` when any other sound won't load or start (S1), logging `"The door chime couldn't play ${sound.name}; playing the classic chime"`.
12. **Stored dates** (stage 2): in `display_settings`, `season_{name.lowercase()}_start` and `_end` as `month * 100 + day` ints. Both must read as a valid `MonthDay` (29 February is valid), else that theme's defaults apply. Putting back the defaults removes both keys.
13. **Choosing dates** (stage 2): the sheet shows `HhDateGrid` at the month of the range's next start; the first tap is the first day, the second the last. A last day before the first is refused, as is one a year or more after it; a range across the new year is picked as real dates (20 Dec 2026, then 2 Jan 2027), so it's simply "after the first". Saved as `MonthDay`s.
14. **"Next:"** shows under the section when `showingOn(today, dates, null)` is null: the theme whose next start (this year's, or next year's when it has passed) is soonest; ties go to list order.

## File Structure

```
core/ui/src/main/java/uk/co/siland/culvery/core/ui/
  Seasonal.kt (SeasonalTheme, look, SeasonDates, SeasonalLook, SeasonalSound, ShowingTheme,
    LocalSeasonalTheme, SeasonalDimens), HalloweenLook.kt (colours, inks; grows in 3, 6, 7, 9),
    Theme.kt (CulveryTheme(dark, seasonal), Culvery.seasonal, Culvery.look)                       (Task 1)
  ClockFace.kt (Creepster, CreepsterClock, SlotClock); res/font/creepster.ttf;
    licenses/OFL-Creepster.txt; README.md                                                          (Task 6)
  HalloweenArt.kt (PumpkinMark, drawCobweb), Components.kt (HhCard decoration)                     (Task 7)
  HalloweenArt.kt (HalloweenSky, batPositions)                                                      (Task 9)
core/ui testFixtures Screenshots.kt (snap's seasonal)                                               (Task 1)
app: display/SeasonalThemes.kt, display/di/DisplayModule.kt, MainActivity.kt,
    src/debug di/HouseGraphProbe.kt                                                                 (Task 2)
  shell/ui/HomeScreen.kt                                                                            (Task 6)
  shell/ui/NavRail.kt                                                                               (Task 7)
  display/DisplayWords.kt, display/ui/DisplaySettingsPage.kt                                        (Tasks 8, 12)
  shell/ui/CulveryShell.kt, SetupWiring.kt (skyDrifts), display/AnimatorsOn.kt, MainActivity.kt     (Task 9)
  display/DisplaySettings.kt, display/SeasonalThemes.kt                                             (Task 11)
capability/door: tools/sound/door_organ.py, res/raw/door_organ.wav, Chime.kt, DoorAlerts.kt (call),
    ui/DoorSettingsPage.kt (call), test TestDoor.kt                                                 (Task 3)
  DoorSettings.kt, DoorWords.kt, DoorAlerts.kt, DoorCapability.kt, ui/DoorSettingsPage.kt            (Task 4)
capability/calendar: ui/TodayCard.kt                                                                (Task 7)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                                (Tasks 5, 10, 14, 15)
```

---

### Task 1: The theme seam, `SeasonalLook` and Halloween's colours in `:core:ui` (spec §3, §4.1, §4.2; rulings 1–5; Review Focus 3)

**Review:** opus. Every pair the family reads, in both Halloween sets; warning red apart from orange; the look as the one place a theme is described.

**Files:**
- Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Seasonal.kt`, `HalloweenLook.kt`.
- Modify `core/ui/.../Theme.kt` (`CulveryTheme(dark, seasonal = null)`, `LocalSeasonalTheme` provided, `Culvery.seasonal`, `Culvery.look`).
- Modify `core/ui/src/testFixtures/.../test/Screenshots.kt` (`snap(name, dark, modifier, seasonal: SeasonalTheme? = null, content)`).
- Test: `core/ui/src/test/.../ContrastTest.kt`, `Contrast.kt` (`hueGap`), `ThemeTest.kt`, new `SeasonalTest.kt`, new `SeasonalSourceTest.kt`; `capability/door/src/test/.../ui/DoorCardScreenshotTest.kt` (its local snap helper gains `seasonal`, B9); `capability/party/src/test/.../ui/PartyScreenshotTest.kt`.

**Interfaces:**
- Produces (all public in `uk.co.siland.culvery.core.ui`):
  - `enum class SeasonalTheme(val label: String, val defaultDates: SeasonDates) { HALLOWEEN("Halloween", SeasonDates(MonthDay.of(10, 24), MonthDay.of(10, 31))) }`; `val SeasonalTheme.look: SeasonalLook`.
  - `data class SeasonDates(val start: MonthDay, val end: MonthDay) { operator fun contains(date: LocalDate): Boolean }`: inclusive; `end < start` wraps the new year.
  - `fun defaultSeasonDates(): Map<SeasonalTheme, SeasonDates>`.
  - `class SeasonalLook(…)` and `enum class SeasonalSound` as ruling 2; `val HalloweenLook: SeasonalLook` (colours only now).
  - `interface ShowingTheme { /** Null while no theme shows. */ val showing: StateFlow<SeasonalTheme?> }`
  - `val LocalSeasonalTheme: ProvidableCompositionLocal<SeasonalTheme?>` (`staticCompositionLocalOf { null }`); `Culvery.seasonal: SeasonalTheme?`, `Culvery.look: SeasonalLook?` (`@Composable get`).
  - `val HalloweenNight: HhColors`, `val HalloweenDay: HhColors`, `fun colorsFor(seasonal: SeasonalTheme?, dark: Boolean): HhColors`.
  - `object HalloweenInks { fun moon(c: HhColors): Color; fun bat(c: HhColors): Color; fun web(c: HhColors): Color; val DAY_BAT: Color; val PUMPKIN_STEM: Color; val PUMPKIN_FACE: Color }`.
  - `@Composable fun CulveryTheme(dark: Boolean, seasonal: SeasonalTheme? = null, content: @Composable () -> Unit)`: the target is `colorsFor(seasonal, dark)`, animated as today (400 ms).
  - Test helper `internal fun hueGap(a: Color, b: Color): Float` in `Contrast.kt` (degrees, 0–180).

- [ ] **Step 1: The failing tests** (with the stubs of the Global Constraints: `colorsFor` returning the normal sets, `contains` returning false).
  - `ContrastTest`: `themes` becomes every set `colorsFor` gives, named `"{label or normal} {night|day}"` (`(listOf(null) + SeasonalTheme.entries)` × dark, light); every existing loop then covers four sets with no rule changed. The pinned edges (`theEdgesStayWhereTheyAre`, `theTintEdgesStayWhereTheyAre`, the light pins in `aPastDayReadsAtItsAlpha` and `theClashTagReadsOnTheRow`) stay on `LightColors` / `DarkColors`. Add:
    - `dangerAndAccentStayApart`: every set, `hueGap(t.danger, t.accent) >= 35f`, the message naming the set.
    - `textReadsOverTheSky`: both Halloween sets, on `HalloweenInks.bat(t)`: `accent` at `NON_TEXT`, `ink` and `mute` at `TEXT`.
    - `theWebReadsUnderCardText`: both Halloween sets, `ink` and `mute` on `HalloweenInks.web(t)` at `TEXT`.
  - `ThemeTest`:
    - `halloweenProvidesItsSets`: `CulveryTheme(dark = true, seasonal = HALLOWEEN)` → `Culvery.colors == HalloweenNight`, `Culvery.seasonal == HALLOWEEN`, `Culvery.look === HalloweenLook`; `dark = false` → `HalloweenDay`.
    - `noThemeIsTheNormalLook` (**pin**): `seasonal = null` → `DarkColors` / `LightColors`, `Culvery.look == null`.
    - `halloweenKeepsTheLightOrDarkReading`: `HalloweenDay.isLight` true, `HalloweenNight.isLight` false; `HalloweenNight.border == Color.Transparent`.
    - `personColoursDontChange` (**pin**): `PersonPalette.colors` and `PersonChipInk` equal literals copied from `PersonPalette.kt` today.
    - `halloweenKeepsWeatherAndRain`: night's `weather` and `rain` are `DarkColors`'; day's `LightColors`'.
  - `SeasonalTest`:
    - `halloweenRunsFrom24To31October`: 23 Oct false, 24 Oct true, 31 Oct true, 1 Nov false (2026).
    - `aWrappedRangeCoversBothEnds`: `SeasonDates(MonthDay.of(12, 20), MonthDay.of(1, 2))` → 19 Dec false, 20 Dec true, 1 Jan true, 2 Jan true, 3 Jan false.
    - `aOneDayRange`: start == end → only that day.
    - `theDefaultsAreEveryThemesOwn`: `defaultSeasonDates()` has every entry, Halloween's 24 Oct – 31 Oct.
    - `everyThemeHasALookWithBothSets`: each entry's `look.night` isn't light and `look.day` is.
  - `SeasonalSourceTest.onlyTheLookNamesATheme`: in every `*/src/main/**/*.kt` and `*/*/src/main/**/*.kt` (as `IconFontTest` reads them), `HALLOWEEN` appears only in `Seasonal.kt` and `HalloweenLook.kt`.
  - Screenshots (each `_dark` and `_light`, with `seasonal = SeasonalTheme.HALLOWEEN`): `door_card_halloween` (the `door_card` sample), `party_sheet_on_halloween` (the `party_sheet_on` sample).

  Run `./gradlew :core:ui:testDebugUnitTest`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** per the Interfaces and rulings 1–5. The scheme's `remember` keys stay `(dark, colors)`.
- [ ] **Step 3: Images.** **New:** `door_card_halloween_dark`, `door_card_halloween_light`, `party_sheet_on_halloween_dark`, `party_sheet_on_halloween_light` (check: orange buttons, plum or warm cream surfaces, text as clear as the normal images). **Changes:** none.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui capability/door/src/test capability/party/src/test
git commit -m "Add seasonal themes to the shared theme, each described by one look, with Halloween's day and night colours checked by the same contrast tests as the normal look"
```

---

### Task 2: Which theme shows, the app wired, the night clock pinned (spec §2.1, §2.5, §3, §4.1; ruling 6; Review Focus 1)

**Review:** opus. Midnight in both directions, the clock unread, the night clock untouched.

**Files:**
- Create `app/src/main/java/uk/co/siland/culvery/display/SeasonalThemes.kt`.
- Modify `app/.../display/di/DisplayModule.kt` (`@Binds abstract fun showingTheme(impl: SeasonalThemes): ShowingTheme`), `app/.../MainActivity.kt` (`@Inject lateinit var seasonal: SeasonalThemes`; `CulveryTheme(dark = state.dark, seasonal = showing)`), `app/src/debug/java/uk/co/siland/culvery/di/HouseGraphProbe.kt` (`showingTheme(): ShowingTheme`, `seasonalThemes(): SeasonalThemes`).
- Test: `app/src/test/.../display/SeasonalThemesTest.kt`; `display/ui/SleepLayersTest.kt`, `display/ui/SleepLayersScreenshotTest.kt`; `shell/ui/ShellScreenshotTest.kt` (`snap` gains `seasonal`); `HouseGraphTest.kt`.

**Interfaces:**
- Consumes: Task 1's `SeasonalTheme`, `SeasonDates`, `ShowingTheme`, `defaultSeasonDates()`, `CulveryTheme(dark, seasonal)`.
- Produces (in `uk.co.siland.culvery.display`):
  - `data class ThemePreview(val theme: SeasonalTheme, val day: LocalDate)`
  - `internal fun showingOn(date: LocalDate, dates: Map<SeasonalTheme, SeasonDates>, preview: ThemePreview?): SeasonalTheme?`
  - `@Singleton class SeasonalThemes internal constructor(now: StateFlow<LocalDateTime?>, dates: Flow<Map<SeasonalTheme, SeasonDates>>, scope: CoroutineScope) : ShowingTheme`, `@Inject constructor(clock: HouseholdClock, @ApplicationScope scope: CoroutineScope)` passing `clock.now` and `flowOf(defaultSeasonDates())` (Task 11 replaces the dates):
    - `override val showing: StateFlow<SeasonalTheme?>` (Eagerly, null until the clock is read);
    - `val previewing: StateFlow<SeasonalTheme?>`: the theme previewed today, else null;
    - `val dates: StateFlow<Map<SeasonalTheme, SeasonDates>>` (Eagerly, `defaultSeasonDates()` until read);
    - `fun preview(theme: SeasonalTheme, on: Boolean)`: on → `ThemePreview(theme, today)` (nothing while the clock is unread); off → cleared only when `theme` is the one previewed.

- [ ] **Step 1: The failing tests** (stubs: `showingOn` returns null, `showing` stays null).
  - `SeasonalThemesTest` (fake `now`, `runTest` with `backgroundScope`):
    - `halloweenShowsInItsDates`: 2026-10-29 12:00 → `HALLOWEEN`; 2026-10-23 23:59 → null.
    - `halloweenEndsAsNovemberStarts`: 2026-10-31 23:59 → `HALLOWEEN`; then 2026-11-01 00:00 → null, with no new instance.
    - `aPreviewShowsOutsideTheDates`: 2026-11-05 10:00, `preview(HALLOWEEN, true)` → `showing` and `previewing` `HALLOWEEN`; `preview(HALLOWEEN, false)` → both null.
    - `aPreviewEndsAtMidnight`: preview on at 2026-11-05 23:59 → `HALLOWEEN`; 2026-11-06 00:00 → `showing` and `previewing` null.
    - `aPreviewEndsWhenTheClockGoesBackADay`: preview on at 2026-11-05 00:01; 2026-11-04 23:59 → both null.
    - `nothingShowsBeforeTheClockIsRead`: `now` null → `showing` null; `preview(HALLOWEEN, true)` → `previewing` still null, no throw.
    - `aPreviewFromAnotherDayIsIgnored` (T5): `showingOn(2026-11-05, defaults, ThemePreview(HALLOWEEN, 2026-11-04)) == null`.
    - `followsTheHouseholdClockOnVirtualTime` (T5): a real `HouseholdClock` over a fixed zone, a `WallClock` reading `testScheduler.currentTime` from 2026-10-31 23:58 and its `minuteTicks` on a `StandardTestDispatcher`; `SeasonalThemes` over `clock.now` reads `HALLOWEEN`; `advanceTimeBy(60_000)` → still `HALLOWEEN` at 23:59; another 60 000 → null at 00:00.
  - `SleepLayersTest.theNightClockIsTheSameInEveryTheme` (**pin**, spec §2.5, T2): through the real shell path (`ShellLayers` with `SleepLayer` over `CulveryShell`, as `SleepLayersScreenshotTest.snap` builds it) under `CulveryTheme(dark, seasonal = HALLOWEEN)`, dark and light: "23:41"'s style has `fontFamily == HhType.clock.fontFamily`, `fontSize == 200.sp`, `letterSpacing == (-8).sp`, `fontFeatureSettings == "tnum"`, `color == Color(0xFF5A5A5A)`; the pixel at the screen's corner is `Color.Black` (`pixelAt`, `@GraphicsMode(NATIVE)`).
  - `SleepLayersScreenshotTest`: `night_clock_halloween` (dark, `HALLOWEEN`); byte-identical to `night_clock` (Step 3).
  - `ShellScreenshotTest`: `home_halloween_dark` / `home_halloween_light`, the `fullHome` state with `seasonal = HALLOWEEN` (colours only at this task).
  - `HouseGraphTest.theShowingThemeIsSeasonalThemes`: `showingTheme()` is the same instance as `seasonalThemes()`.

  Run `./gradlew :app:testDebugUnitTest --tests '*SeasonalThemes*' --tests '*SleepLayers*' --tests '*HouseGraph*'`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement** per ruling 6 and the Interfaces. `MainActivity` collects `seasonal.showing` with `collectAsStateWithLifecycle()` beside `state`.
- [ ] **Step 3: Images.** **New:** `night_clock_halloween`, `home_halloween_dark`, `home_halloween_light`. Then `cmp app/src/test/screenshots/night_clock.png app/src/test/screenshots/night_clock_halloween.png` prints nothing (spec §2.5; if it differs, stop: the night clock changed). **Changes:** none.
- [ ] **Step 4: The gate, then commit.**
```bash
git add app
git commit -m "Show Halloween by its dates from midnight to midnight, with a preview that ends at midnight, and keep the night clock as it is in every theme"
```

---

### Task 3: The haunted organ (spec §2.6, §4.6; rulings 10, 11; Review Focus 4)

**Review:** sonnet.

**Files:**
- Create `tools/sound/door_organ.py` in `door_chime.py`'s style (docstring with usage and output, standard library only, the note table at the top) and run it: `python tools/sound/door_organ.py` writes `capability/door/src/main/res/raw/door_organ.wav`.
- Modify `capability/door/.../Chime.kt` (`DoorSound`, `Chime.play(sound, volume)`, `MediaPlayerChime` with an internal opener seam and the fallback), `DoorAlerts.kt` and `ui/DoorSettingsPage.kt` (call `play(DoorSound.CLASSIC, …)`, so behaviour is unchanged until Task 4), `core/ui/.../HalloweenLook.kt` (`sound = SeasonalSound.HAUNTED_ORGAN`).
- Test: `capability/door/src/test/.../ChimeTest.kt`, new `MediaPlayerChimeTest.kt`, `TestDoor.kt` (`FakeChime`).

**Interfaces:**
- Produces:
  - `enum class DoorSound(@RawRes internal val res: Int) { CLASSIC(R.raw.door_chime), ORGAN(R.raw.door_organ) }`; `fun interface Chime { fun play(sound: DoorSound, volume: Float): Boolean }`.
  - `class MediaPlayerChime internal constructor(private val open: (DoorSound) -> MediaPlayer?) : Chime`, `@Inject constructor(@ApplicationContext context: Context)` opening `MediaPlayer.create(context, sound.res, CHIME_ATTRIBUTES, AUDIO_SESSION_ID_GENERATE)`. A sound other than `CLASSIC` that opens null or throws `IllegalStateException` on start → logged per ruling 11, then `CLASSIC` played; true when either started.
  - `FakeChime.plays: List<Pair<DoorSound, Float>>`, with `volumes` kept as `plays.map { it.second }` so existing tests stand.

- [ ] **Step 1: The failing tests** (stub: `MediaPlayerChime` plays only what it's asked).
  - `ChimeTest.bothSoundsAreBundledWavs`: for each `DoorSound`, `openRawResource(res)` starts `RIFF`…`WAVE`, PCM, 1 channel, 22 050 Hz, 16 bits; `ORGAN`'s length (data bytes / 44 100) is between 3.4 and 4.1 s (B7); `CLASSIC`'s stays 3.2 s (**pin**).
  - `ChimeTest.theOrganIsLoudEnough`: `ORGAN`'s peak is at least 0.9 × 32 767; its RMS over 0–3.5 s is at least 0.8 × the chime's RMS over its first 3.0 s.
  - `ChimeTest.theOrganPlaysOnceAndFades`: no 0.5 s run of |sample| < 0.01 × 32 767 before 3.4 s (not repeated); the last 50 ms are all under 0.02 × 32 767 (faded, no click).
  - `MediaPlayerChimeTest` (Robolectric; the opener a fake recording each `DoorSound` it's asked for, handing back a Robolectric `MediaPlayer` or null):
    - `anOrganThatWontLoadPlaysTheChime` (S1): opener null for `ORGAN` → asked `[ORGAN, CLASSIC]`, `play` true.
    - `anOrganThatWontStartPlaysTheChime`: the `ORGAN` player's start throws (a shadowed player in the error state) → `CLASSIC` asked next.
    - `theChimeFailingIsFalse`: null for both → `play(ORGAN, 1f)` false, asked `[ORGAN, CLASSIC]`; `play(CLASSIC, 1f)` asks once.
  - `DoorAlertsTest.aNewRingInFrontShowsTheCardAndChimesAtTheVolume` (**pin**): add `chime.plays.single().first == DoorSound.CLASSIC`; every other existing test passes unchanged.
  - `LogHygieneTest` already lists `Chime.kt` (`TAG = "MediaPlayerChime"`); the new line interpolates only `sound.name`.

  Run `./gradlew :capability:door:testDebugUnitTest` and `./gradlew -p build-logic :convention:test`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** per rulings 10 and 11, then run the script. Listen once on the PC and say in the report whether it reads as an organ playing the Toccata's opening. The WAV is about 175 KB.
- [ ] **Step 3: The gate, then commit.** No screenshot changes.
```bash
git add tools/sound/door_organ.py capability/door core/ui
git commit -m "Add a haunted organ doorbell sound, generated by our own script, and play the classic chime instead whenever the organ won't play"
```

---

### Task 4: Settings › Door's "Doorbell sound"; a ring follows the theme (spec §2.7, §4.6; rulings 6, 11; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify `capability/door/.../DoorSettings.kt` (`DoorPrefs.doorbell`, `setDoorbell`, key `doorbell_sound`), `Chime.kt` (`DoorbellChoice`, `soundFor`), `DoorWords.kt` (the Copy), `DoorAlerts.kt` (plays `prefs().doorbell.soundFor(theme())`), `DoorCapability.kt` (takes `ShowingTheme`, passes it to the page), `ui/DoorSettingsPage.kt` (the section and rows; Play chime plays the chosen sound).
- Test: `DoorSettingsTest`, `DoorAlertsTest`, `DoorAlertsInjectedTest`, `ui/DoorSettingsPageTest.kt`, `ui/DoorSettingsScreenshotTest.kt`, `DoorCapabilityTest`.

**Interfaces:**
- Consumes: Task 3's `DoorSound`, `Chime`; Task 1's `ShowingTheme`, `SeasonalTheme.look`, `SeasonalSound`.
- Produces:
  - `enum class DoorbellChoice { FOLLOW_THEME, CLASSIC, ORGAN }`; `fun DoorbellChoice.soundFor(showing: SeasonalTheme?): DoorSound`.
  - `DoorPrefs.doorbell: DoorbellChoice = FOLLOW_THEME`; `suspend fun DoorSettings.setDoorbell(choice: DoorbellChoice)`.
  - `DoorAlerts` internal constructor gains `theme: () -> SeasonalTheme?` (last); the `@Inject` one gains `showing: ShowingTheme` and passes `{ showing.showing.value }`.
  - `DoorSettingsUi.doorbell: DoorbellChoice`; `DoorSettingsActions.onDoorbell: (DoorbellChoice) -> Unit`; tags `door_doorbell_follow`, `door_doorbell_classic`, `door_doorbell_organ`.
  - `DoorSettingsHost(…, showing: StateFlow<SeasonalTheme?>)`; Play chime calls `chime.play(p.doorbell.soundFor(showing.value), p.volume)`.
  - The page (B8): `HhSectionLabel("Doorbell sound")` is the page's first section, right after `HhPageTitle`, holding three `HhRadioRow`s in a `Column(Modifier.selectableGroup())`; then the existing "Chime" section (volume and Play chime) as today.

- [ ] **Step 1: The failing tests** (stubs: `soundFor` returns `CLASSIC`, the stored value ignored).
  - `DoorSettingsTest`:
    - `theDoorbellFollowsTheThemeByDefault`: a fresh store → `FOLLOW_THEME`.
    - `theDoorbellChoiceIsKept`: `setDoorbell(ORGAN)` → reread (new instance over the same file) `ORGAN`.
    - `anUnknownDoorbellValueReadsAsFollowTheTheme`: the key holding `"KAZOO"` → `FOLLOW_THEME`, no throw.
    - `soundForFollowsTheLook`: `FOLLOW_THEME` with `HALLOWEEN` → `ORGAN`, with null → `CLASSIC`; `CLASSIC` with `HALLOWEEN` → `CLASSIC`; `ORGAN` with null → `ORGAN`.
  - `DoorAlertsTest`:
    - `aRingDuringHalloweenPlaysTheOrgan`: `FOLLOW_THEME`, theme `HALLOWEEN` → `plays == [(ORGAN, volume)]`, once.
    - `aRingPlaysTheThemesSoundAsItIsNow`: the theme flips from `HALLOWEEN` (a preview, S4) to null between two rings 11 s apart → `ORGAN` then `CLASSIC`.
    - `quietHoursStillSilenceTheOrgan` (**pin**, as `quietHoursKeepTheCardButNotTheChime`): `ORGAN` chosen, inside quiet hours → no play.
  - `DoorAlertsInjectedTest` builds with a `ShowingTheme` whose `showing` is `MutableStateFlow(null)`.
  - `DoorSettingsPageTest`:
    - `theDoorbellSoundRows`: the section "Doorbell sound" above "Chime"; three radio rows in a selectable group: "Follow the theme" with its line verbatim, "Classic chime", "Haunted organ"; `FOLLOW_THEME` selected by default.
    - `choosingASoundSavesIt`: tapping "Haunted organ" → `onDoorbell(ORGAN)`; the host saves it.
    - `playChimePlaysWhatARingWould`: host with `FOLLOW_THEME` and `HALLOWEEN` → `plays == [(ORGAN, 1.0f)]`; with `CLASSIC` chosen → `CLASSIC`.
  - Screenshots: `settings_door_dark` / `_light` change; new `settings_door_halloween_dark` / `_light` (`ORGAN` chosen, under `HALLOWEEN`).

  Run `./gradlew :capability:door:testDebugUnitTest`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement** per ruling 11 and the Copy.
- [ ] **Step 3: Images.** **New:** `settings_door_halloween_dark`, `settings_door_halloween_light`. **Changes:** `settings_door_dark`, `settings_door_light`, `settings_door_steps_dark` (everything below the new section moves down). No other `door_*` image.
- [ ] **Step 4: The gate, then commit.**
```bash
git add capability/door
git commit -m "Let Settings › Door choose the doorbell sound, following the theme by default, so a ring plays the haunted organ while Halloween shows"
```

---

### Task 5: *Optional* early tablet checkpoint (spec §2.8)

**Controller and user only, and only if the evening allows; skip it otherwise.** Same rules and steps as Task 10 Steps 2–3 (release, mapping kept, `adb -s <tablet> install -r`, never uninstall). With the user: the Halloween colours by day and night, and Settings › Door's Play chime with Follow the theme plays the organ. Record one line under "Plan 14 stage 1: on the tablet" (no names); commit only that file: `git commit -m "Record Plan 14's early install on the tablet" docs/superpowers/plans/2026-09-23-plan1-followups.md`.

---

### Task 6: Creepster and the Halloween clock face (spec §2.3, §4.4; ruling 7; Review Focus 5)

**Review:** sonnet.

**Files:**
- Download (approved 2026-10-29; S3) from the pinned commit into a new empty `$LISTS/creepster/`, from `https://raw.githubusercontent.com/google/fonts/6157dfaa640ca5738b4d1a7070fe389c3036d7fc/ofl/creepster/`: first `METADATA.pb`, whose `fonts { filename: … }` names the TTF (expected `Creepster-Regular.ttf`), then that TTF and `OFL.txt`. Check before copying: `grep -c '^license: "OFL"' METADATA.pb` prints 1; `grep -c "SIL OPEN FONT LICENSE Version 1.1" OFL.txt` prints at least 1; `head -c 4 <ttf> | xxd -p` prints `00010000`. Any failure (or a 404: the folder isn't at that commit) → stop and tell the controller. Record `sha256sum <ttf>`. Copy to `core/ui/src/main/res/font/creepster.ttf` and `core/ui/licenses/OFL-Creepster.txt`; no subsetting. Nothing in `$LISTS/creepster/` is run.
- Modify `README.md`'s licence paragraph (line 214): add "Creepster: SIL Open Font License (`core/ui/licenses/OFL-Creepster.txt`), for the Halloween clock; from google/fonts at commit 6157dfaa640ca5738b4d1a7070fe389c3036d7fc, `Creepster-Regular.ttf` sha256 {hash}."
- Create `core/ui/.../ClockFace.kt`; modify `core/ui/.../HalloweenLook.kt` (`clockFace = CreepsterClock`), `app/.../shell/ui/HomeScreen.kt` (`HomeHeader`'s clock reads `Culvery.look?.clockFace`).
- Test: `core/ui` `ThemeTest.bundledFontsAreRealFiles`; new `core/ui/src/test/.../ClockFaceTest.kt`; new `app/src/test/.../shell/ui/HalloweenClockTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `Culvery.look`, `HhType.clock`, `lineBox`.
- Produces:
  - `val Creepster: FontFamily` (`Font(R.font.creepster)`); `val CreepsterClock: TextStyle` = `HhType.clock` with `fontFamily = Creepster`, `fontWeight = FontWeight.Normal`, `fontFeatureSettings = null`, `letterSpacing = 0.sp`.
  - `@Composable fun SlotClock(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier)` per ruling 7. Each character is a `Text` tagged `clock_slot` and hidden from accessibility (`hideFromAccessibility()`); the `Row` carries the time as its own semantics text (`semantics { this.text = AnnotatedString(text) }`), so `home_clock` reads "23:41" and TalkBack reads one time.
  - `HomeHeader` draws `SlotClock(time, face, c.accent, <the same testTag, lineBox and traversalIndex>)` when `Culvery.look?.clockFace` is non-null, else today's `Text`.

- [ ] **Step 1: The failing tests.**
  - `ThemeTest.bundledFontsAreRealFiles` adds: `R.font.creepster` is over 10 000 bytes and under 500 000.
  - `ClockFaceTest`:
    - `everyDigitGetsTheSameSlot`: `SlotClock("11:11", CreepsterClock, …)` and `SlotClock("00:00", …)` have equal widths.
    - `theClockReadsAsOneTime`: `onNodeWithTag(…)` text is "23:41"; no unhidden child text.
  - `HalloweenClockTest` (the shell's `HomeHeader` under `CulveryTheme(dark = true, seasonal = …)`, `now` a state):
    - `theClockIsInCreepster` (T2): under `HALLOWEEN`, `onAllNodesWithTag("clock_slot", useUnmergedTree = true)[0].textLayout().layoutInput.style.fontFamily == Creepster`.
    - `theClockKeepsItsWidthEveryMinute`: under `HALLOWEEN`, `home_clock`'s width is the same at 11:11, 00:00, 08:47 and 23:58.
    - `theHeaderKeepsItsHeightInEitherFace`: the header's height and `home_clock`'s height are the same with `seasonal` null and `HALLOWEEN`.
    - `theClockDrawsNothingIntoTheDate` (B4, `@GraphicsMode(NATIVE)`): under `HALLOWEEN` at 23:58 (and 22:22, Creepster's lowest drips), no pixel equal to `HalloweenNight.accent` lies at or below `home_date`'s top, across the clock's width.
    - `theHeaderItemsDontMoveWhenTheFaceChanges`: with one sample header item, `home_header_items`' bounds are the same with `seasonal` null and `HALLOWEEN`, at 23:58.
    - `theDateStaysInDmSans` (**pin**): under `HALLOWEEN`, `home_date`'s style `fontFamily == DmSans`.
    - `theClockIsTheThemesOrange` (`@GraphicsMode(NATIVE)`): under `HALLOWEEN`, some pixel inside `home_clock`'s bounds is exactly `HalloweenNight.accent` and none is `HalloweenNight.ink`.
  - Screenshots: `home_halloween_dark` / `_light` change.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*HalloweenClock*' --tests '*ShellLayout*'`. Expected: FAIL on assertions, the pin aside; `ShellLayoutTest` (**pins**: the normal clock) passes.
- [ ] **Step 2: Implement** per ruling 7. If `theClockDrawsNothingIntoTheDate` fails, shrink Creepster's `fontSize` in `CreepsterClock` by whole sp until it passes, and say the size in the report.
- [ ] **Step 3: Images.** **Changes:** `home_halloween_dark`, `home_halloween_light` (the Creepster clock in orange; the date and weather unchanged). Every other `home_*` image must not change.
- [ ] **Step 4: The gate, then commit** (`git status` first: only the files above, nothing from `$LISTS`).
```bash
git add core/ui README.md app
git commit -m "Set Home's clock in Creepster while Halloween shows, each digit in a fixed slot so the header never moves"
```

---

### Task 7: The pumpkin mark and the Today card's cobweb (spec §2.2, §4.3; rulings 2, 5)

**Review:** sonnet.

**Files:**
- Create `core/ui/.../HalloweenArt.kt` (the pumpkin and the web; the sky in Task 9); add to `Seasonal.kt`'s `object SeasonalDimens` `webSpan = 88.dp`, `webStroke = 1.dp`. Modify `HalloweenLook.kt` (`mark = { w -> PumpkinMark(w) }`, `cardCorner = { c -> drawCobweb(HalloweenInks.web(c), SeasonalDimens.webSpan) }`).
- Modify `core/ui/.../Components.kt`: `HhCard(…, decoration: (DrawScope.() -> Unit)? = null, content)`, drawn after the background and before the padding, so it needs no layout and never takes a touch.
- Modify `app/.../shell/ui/NavRail.kt` (B5: the mark's modifiers and `culvery_mark` move onto a wrapper `Box`; inside it `Culvery.look?.mark?.invoke(MARK_WIDTH) ?: CulveryMark(MARK_WIDTH)`), `capability/calendar/.../ui/TodayCard.kt` (`HhCard(decoration = Culvery.look?.cardCorner?.let { corner -> { corner(colors) } })`).
- Test: new `core/ui/src/test/.../HalloweenArtTest.kt`; `app/src/test/.../shell/ui/NavRailTest.kt`, `CulveryMarkTest.kt`; `capability/calendar/src/test/.../ui/CardsTest.kt`, `ui/CardScreenshotTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `HalloweenInks`, `Culvery.look`.
- Produces:
  - `@Composable fun PumpkinMark(width: Dp, modifier: Modifier = Modifier)`: an `ImageVector` in code, `CulveryMark`'s aspect (54 × 49), body in `accent`, stem `PUMPKIN_STEM`, carved face `PUMPKIN_FACE`; tag `pumpkin_mark`; `contentDescription = null` and `hideFromAccessibility()`. Its paths are the implementer's.
  - `fun DrawScope.drawCobweb(color: Color, span: Dp)` (B10): threads from the top-right corner, `SeasonalDimens.webStroke` wide, within `span` of the corner. Its geometry is the implementer's.

- [ ] **Step 1: The failing tests.**
  - `HalloweenArtTest.thePumpkinIsAPictureOnly`: `PumpkinMark` under `HALLOWEEN` has no text, no content description, and is hidden from accessibility; some pixel in it is exactly `HalloweenNight.accent` and some is `PUMPKIN_STEM` (`@GraphicsMode(NATIVE)`).
  - `NavRailTest`:
    - `thePumpkinTakesTheMarksPlace`: under `HALLOWEEN`, `pumpkin_mark` shows inside `culvery_mark`, whose bounds equal its bounds with `seasonal = null`.
    - `theMarkIsBackWithoutATheme` (**pin**): `seasonal = null` → no `pumpkin_mark`; `CulveryMarkTest` passes unchanged.
  - Calendar (`TodayCard` under `CulveryTheme(dark = true, seasonal = …)`, `@GraphicsMode(NATIVE)`, B6):
    - `theWebSitsInTheTopRightCorner`: under `HALLOWEEN`, the `webSpan` square at the card's top-right corner holds pixels that aren't `HalloweenNight.surf` outside the buttons' bounds (the corner differs from the same card without the theme, where it's all `surf`); at least one pixel on the corner's diagonal (inward 8–60 dp) is a web thread (not `surf`); and the card's bottom-left `webSpan` square has none.
    - `noWebWithoutATheme` (**pin**): with `seasonal = null`, every pixel in the corner square clear of buttons is `DarkColors.surf`.
    - `theButtonsStayWhereTheyAre`: `today_add` and "See the week" have the same bounds with and without `HALLOWEEN`, and a tap on each still calls `onAdd` / opens the tab.
  - Screenshots: `today_halloween_dark` / `_light` (the `today` sample, `HALLOWEEN`).

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*NavRail*' --tests '*CulveryMark*' :capability:calendar:testDebugUnitTest`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: Images.** **New:** `today_halloween_dark`, `today_halloween_light` (a faint web in the corner behind the header; the "+" and "See the week" untouched). **Changes:** `home_halloween_dark`, `home_halloween_light` (the pumpkin in the rail, the web on Today). No other image changes.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app capability/calendar
git commit -m "Put a pumpkin in the rail and a cobweb on the Today card while Halloween shows"
```

---

### Task 8: Settings › Display: preview each theme until midnight (spec §2.1, §3, §4.5 stage 1; S4)

**Review:** sonnet.

**Files:**
- Modify `app/.../display/DisplayWords.kt` (the Copy's stage 1 strings), `display/ui/DisplaySettingsPage.kt` (`DisplayPage` takes `SeasonalThemes`; the section after Sleep and the camera row; `DisplaySettingsUi.previewing`, `DisplaySettingsActions.onPreview`).
- Test: `display/ui/DisplaySettingsPageTest.kt`, `display/ui/DisplaySettingsScreenshotTest.kt`.

**Interfaces:**
- Consumes: Task 2's `SeasonalThemes.previewing`, `preview(theme, on)`.
- Produces: `DisplaySettingsUi.previewing: SeasonalTheme? = null`; `DisplaySettingsActions.onPreview: (SeasonalTheme, Boolean) -> Unit = { _, _ -> }` (B9: existing callers unchanged); tag `display_preview_{name.lowercase()}` (`display_preview_halloween`).

- [ ] **Step 1: The failing tests.**
  - `DisplaySettingsPageTest`:
    - `eachThemeHasAPreview`: the section "Seasonal themes", one switch row per `SeasonalTheme` named "Preview Halloween", off, line "Shows Halloween on the wall and plays its doorbell until midnight.".
    - `turningThePreviewOnSaysWhenItEnds`: `previewing = HALLOWEEN` → on, line "Showing now. It ends at midnight.".
    - `theSwitchPreviews`: tap → `onPreview(HALLOWEEN, true)`; on, tap → `(HALLOWEEN, false)`.
    - `theHostPreviewsThroughSeasonalThemes`: the host over a `SeasonalThemes` with a fake clock at 2026-11-05 → tapping turns `showing` to `HALLOWEEN`; tapping again turns it off.
    - `aPreviewEndingAtMidnightTurnsTheSwitchOff`: the host, preview on, the clock to 00:00 the next day → the switch reads off.
  - Screenshots: new `settings_display_preview_dark` / `_light` (preview on, under `HALLOWEEN`).

  Run `./gradlew :app:testDebugUnitTest --tests '*DisplaySettings*'`. Expected: FAIL on assertions.
- [ ] **Step 2: Implement** per the Copy, with `HhSectionLabel` and `HhSwitchRow`.
- [ ] **Step 3: Images.** **New:** `settings_display_preview_dark`, `settings_display_preview_light`. **Changes:** whichever of `settings_display_allowed_dark`, `settings_display_allowed_light`, `settings_display_not_asked_dark`, `settings_display_refused_dark` show the page's foot (list the ones `compareRoborazziDebug` shows changed, and check each is only the new section).
- [ ] **Step 4: The gate, then commit.**
```bash
git add app
git commit -m "Add a preview for each seasonal theme to Settings › Display, ending at midnight or when turned off"
```

---

### Task 9: The moon and the drifting bats (spec §3, §4.3; rulings 5, 8, 9; Review Focus 2) — the first cut

**Review:** opus. Nothing animates while the screen is dark, something is held over Home, Home isn't showing or animations are off; a step redraws only the sky. **If stage 1 is short of time, the controller skips this task, runs Task 10 without it, and does it after Task 10 as stage 2's first task.**

**Files:**
- Modify `core/ui/.../HalloweenArt.kt` (`HalloweenSky`, `batPositions`), `Seasonal.kt` (`SeasonalDimens.moonRadius = 30.dp`, `moonEnd = 84.dp`, `moonTop = 34.dp`, `batSpan = 26.dp`, `batTopLane = 20.dp`, `batLanesBelowHeader = listOf(40.dp, 160.dp, 280.dp)`; `BAT_LAP_MS = 90_000L`, `SKY_FRAME_MS = 66L`), `HalloweenLook.kt` (`sky = { drifting, m -> HalloweenSky(drifting, m) }`).
- Create `app/.../display/AnimatorsOn.kt` (`fun interface AnimatorsOn`, `AndroidAnimatorsOn`); bind it in `display/di/DisplayModule.kt`.
- Modify `app/.../SetupWiring.kt` (`skyDrifts`), `app/.../shell/ui/CulveryShell.kt` (`skyDrifts: Boolean = false`; the look's sky first in Home's `Box`, `Modifier.matchParentSize()`), `app/.../MainActivity.kt` (`@Inject lateinit var animators: AnimatorsOn`; `var animatorsOn by mutableStateOf(true)` set from `animators.now()` in `onCreate` before `setContent` and in `onResume`; passes `skyDrifts(screen, held, animatorsOn)`, where `held` is the value it already computes).
- Test: `core/ui` new `HalloweenSkyTest.kt`; `app` new `SkyDriftTest.kt` (beside `CameraMarksTest`), new `display/AnimatorsOnTest.kt`, new `shell/ui/CulveryShellSkyTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `HalloweenInks.moon`, `.bat`, `Culvery.look`, `Screen`, `holding`.
- Produces:
  - `internal fun batPositions(elapsedMillis: Long, size: Size, headerBottomPx: Float, density: Density): List<Offset>` (ruling 8; x wraps at `size.width + batSpan`).
  - `@Composable fun HalloweenSky(drifting: Boolean, modifier: Modifier = Modifier, elapsed: MutableLongState = remember { mutableLongStateOf(0L) })`: tag `halloween_sky`, `clearAndSetSemantics {}`, `graphicsLayer()`; while `drifting`, a `LaunchedEffect(drifting)` loop of `withFrameMillis` (advancing `elapsed` from where it stopped) then `delay(SKY_FRAME_MS)`; `elapsed` read only inside the `Canvas` draw lambda. The header's bottom comes from Home's layout (the implementer passes it, e.g. measured once from `HomeHeader`).
  - `fun interface AnimatorsOn { fun now(): Boolean }`; `class AndroidAnimatorsOn @Inject constructor() : AnimatorsOn` (`ValueAnimator.areAnimatorsEnabled()`).
  - `internal fun skyDrifts(screen: Screen, held: Boolean, animatorsOn: Boolean): Boolean`.
  - `CulveryShell(…, skyDrifts: Boolean = false, …)`.

- [ ] **Step 1: The failing tests** (stubs: `batPositions` returns fixed points, `skyDrifts` true).
  - `HalloweenSkyTest` (`mainClock.autoAdvance = false`):
    - `batsDriftAScreenWidthEveryLap` (T4): for a 1000 × 600 px sky, `batPositions(0, …)` vs `(45_000, …)`: each x moved by exactly half of `1000 + batSpanPx` (mod it); `(90_000, …)` equals `(0, …)`; a bat whose x at 0 is `w + span − 1` px is at `−1` px one px-step later (the wrap point exactly); y unchanged.
    - `theSkyDriftsWhileAsked`: `drifting = true`, advance 1 000 ms → `elapsed` between 900 and 1 000.
    - `aStepIsAboutAFrameAndAWait` (T4): after the first frame, two steps (`advanceTimeBy(2 * SKY_FRAME_MS)` plus a frame each) advance `elapsed` by `2 * SKY_FRAME_MS` ± one frame.
    - `stillMeansNoFrames`: `drifting = false`, advance 10 000 ms → `elapsed` unchanged; then `true`, advance 500 → it goes on from where it stood, not from 0 nor jumping by 10 000.
    - `aStillSkyStillDrawsTheBats`: `drifting = false` → the pixel at a bat's body (from `batPositions(0, …)`) is `HalloweenInks.bat(HalloweenNight)`; at the moon's centre `HalloweenInks.moon(HalloweenNight)`.
    - `theSkyIsSilent`: no node under `halloween_sky` has text, a description or an action.
  - `SkyDriftTest.theSkyDriftsOnlyAwakeUnheldWithAnimations`: the twelve combinations of `Screen` × held × animatorsOn; only `(AWAKE, false, true)` is true.
  - `AnimatorsOnTest.readsAndroidsSetting` (T5): `ShadowValueAnimator.setDurationScale(0f)` → `AndroidAnimatorsOn().now()` false; `1f` → true.
  - `CulveryShellSkyTest`:
    - `theSkyIsBehindHomeWhileHalloweenShows`: `HALLOWEEN` on Home → `halloween_sky` exists, drawn before the header (its bounds hold `home_clock`'s), and the header still reads clock, date, items in order.
    - `theMoonAndBatsKeepClearOfTheHeaderItems` (B3): with the `fullHome` sample, the moon's bounds and every bat lane's band (from `batPositions` and `batSpan`) miss `home_header_items` and `home_date`, by day and night; if the moon can't, the day look has no moon (then this test asserts that).
    - `noSkyOffHome`: selected tab `door` → no `halloween_sky`.
    - `noSkyWithoutATheme` (**pin**).
  - Screenshots: `home_halloween_dark` / `_light` change (drawn still at elapsed 0).

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*Sky*' --tests '*AnimatorsOn*'`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement** per rulings 8 and 9.
- [ ] **Step 3: Images.** **Changes:** `home_halloween_dark`, `home_halloween_light` (a faint moon top right above the weather, four faint bats; the day bats visibly darker than the background). Every normal `home_*` image unchanged.
- [ ] **Step 4: A final look (T7)** at `home_halloween_dark` and `_light` as stage 1 leaves them: clock, date, weather, cards, pumpkin, web, moon and bats together; anything that competes with text is said in the report.
- [ ] **Step 5: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Draw a moon and drifting bats behind Home while Halloween shows, still while the screen is dark, anything is open over Home or animations are off"
```

---

### Task 10: Stage 1 on the tablet (spec §2.8, §5, §7)

**Controller and user only. Needs the SM-T510 on the cable.** 4e's tablet rules apply: a signed release with `install -r`, never uninstall, never wipe; save and restore every setting changed. No subagent.

- [ ] **Step 1: The gate on the branch**, then a short look on `culvery-5` with a debug build (`install -r`): Halloween at night and by day (the status bar's Day/Night preview); the pumpkin, web, moon, bats drifting; the clock in Creepster with the header still; the night clock unchanged; Settings › Display's preview; Settings › Door's three sounds with Play chime. Faults are collected and asked about before any fix.
- [ ] **Step 2: The release.** `./gradlew :app:assembleRelease`; `grep -c DebugClock app/build/outputs/mapping/release/mapping.txt` prints 0; copy the mapping to `culvery-notes/mappings/` named for the commit.
- [ ] **Step 3: Install.** `adb -s <tablet> install -r app/build/outputs/apk/release/app-release.apk`. Never uninstall.
- [ ] **Step 4: With the user:**
  - Halloween shows (29–31 October are in its dates); the user looks across the room by day and at night.
  - The organ (S1): a real ring, or, if none is to hand, Settings › Door's Play chime with Follow the theme; it plays the organ at the tablet's notification volume, once.
  - The bats' cost (S2): `adb -s <tablet> shell dumpsys gfxinfo uk.co.siland.culvery reset`, a minute on Home awake, then `dumpsys gfxinfo uk.co.siland.culvery` → about 15 frames a second and the 90th-percentile frame time under 16 ms; then the same with the screen asleep → no new frames.
  - `HeatLog`'s lines over the evening are no warmer than the evening before (`adb -s <tablet> logcat -d -s HeatLog`).
- [ ] **Step 5: Record** under "Plan 14 stage 1: on the tablet" in `docs/superpowers/plans/2026-09-23-plan1-followups.md`, with no names: what was checked, the frame numbers, and (S6) "A ring just after a restart, before the household clock is read, plays the classic chime even during Halloween; never silence." If Task 9 was cut, say so there. Commit: `git commit -m "Record Plan 14's first stage on the tablet" docs/superpowers/plans/2026-09-23-plan1-followups.md`.

---

### Task 11: Stage 2: the dates stored, read, and the next theme (spec §4.1, §4.5; rulings 12, 14)

**Review:** sonnet.

**Files:**
- Modify `app/.../display/DisplaySettings.kt` (`DisplayPrefs.seasonDates`, `setSeasonDates`, `resetSeasonDates`), `display/SeasonalThemes.kt` (the `@Inject` constructor takes `DisplaySettings` and passes `settings.read.filterNotNull().map { it.seasonDates }`; `nextTheme`).
- Test: `display/DisplaySettingsTest.kt`, `display/SeasonalThemesTest.kt`; `core/ui` `SeasonalTest.kt`.

**Interfaces:**
- Produces:
  - `DisplayPrefs.seasonDates: Map<SeasonalTheme, SeasonDates> = defaultSeasonDates()`.
  - `suspend fun DisplaySettings.setSeasonDates(theme: SeasonalTheme, dates: SeasonDates)`; `suspend fun DisplaySettings.resetSeasonDates(theme: SeasonalTheme)`.
  - `internal fun nextTheme(today: LocalDate, dates: Map<SeasonalTheme, SeasonDates>): Pair<SeasonalTheme, LocalDate>?` (ruling 14); `SeasonalThemes.next: StateFlow<Pair<SeasonalTheme, LocalDate>?>`, null while a theme is in its dates or the clock is unread.

- [ ] **Step 1: The failing tests** (stubs: `seasonDates` always the defaults, `nextTheme` null).
  - `DisplaySettingsTest`:
    - `seasonDatesDefaultToEachThemes`: fresh → Halloween 24 Oct – 31 Oct.
    - `editedDatesAreKept`: `setSeasonDates(HALLOWEEN, 20 Oct – 2 Nov)` → reread the same.
    - `puttingBackTheDefaultsRemovesTheKeys`: after `resetSeasonDates` → defaults, and the file holds neither key.
    - `unreadableSeasonDatesFallBackToTheDefaults` (T3), parameterised: `season_halloween_start` each of {1332, 231, 0, -1, 1301, 100} with a valid `_end` → both ends the defaults; the key written as a string (`stringPreferencesKey`) → defaults, no throw; a valid start (1020) with `_end` = 1332 → defaults; only `_end` stored → defaults.
    - `theLeapDayIsAValidDate`: `229` reads as 29 February.
  - `SeasonalTest.aLeapDayEndInAShortYearEndsOnThe28th`: `SeasonDates(20 Feb, 29 Feb)` contains 2027-02-28 and not 2027-03-01.
  - `SeasonalThemesTest`:
    - `editedDatesMoveTheTheme`: dates 1 Nov – 5 Nov, 2026-11-03 → `HALLOWEEN`; 2026-10-29 → null.
    - `nextIsTheSoonestStart`: 2026-11-05, defaults → `(HALLOWEEN, 2027-10-24)`; 2026-10-01 → `(HALLOWEEN, 2026-10-24)`; on 2026-10-29 → null.
    - `nextWrapsTheNewYear`: dates 20 Dec – 2 Jan, on 2026-12-01 → `2026-12-20`; on 2027-01-03 → `2027-12-20`.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*DisplaySettings*' --tests '*SeasonalThemes*'`. Expected: FAIL on assertions.
- [ ] **Step 2: Implement** per rulings 12 and 14.
- [ ] **Step 3: The gate, then commit.** No screenshot changes.
```bash
git add app core/ui
git commit -m "Store each seasonal theme's dates with the display's settings, falling back to its defaults, and work out which theme comes next"
```

---

### Task 12: Stage 2: Settings › Display edits the dates and says what's next (spec §4.5; ruling 13)

**Review:** sonnet.

**Files:**
- Modify `app/.../display/DisplayWords.kt` (the stage 2 Copy; `fun dayMonth(d: MonthDay): String` = "24 Oct"; `fun nextLine(theme: SeasonalTheme, date: LocalDate): String`), `display/ui/DisplaySettingsPage.kt` (a dates row per theme; the dates sheet as an overlay through `LocalOverlayHost`, as Settings' other sheets; the "Next:" line).
- Test: `display/ui/DisplaySettingsPageTest.kt`, `display/ui/DisplaySettingsScreenshotTest.kt`.

**Interfaces:**
- Consumes: Task 11's `seasonDates`, `setSeasonDates`, `resetSeasonDates`, `SeasonalThemes.next`; `HhListRow`, `HhSheet`, `HhDateGrid`, `HhFooterButton`, `HhButton`, `HhMessageLine`.
- Produces: `DisplaySettingsUi.dates: Map<SeasonalTheme, SeasonDates> = defaultSeasonDates()`, `.next: Pair<SeasonalTheme, LocalDate>? = null`, `.today: LocalDate`; `DisplaySettingsActions.onDates: (SeasonalTheme, SeasonDates?) -> Unit = { _, _ -> }` (null puts back the defaults); `internal fun checkRange(first: LocalDate, last: LocalDate): String?` (the refusal, or null); tags `display_dates_halloween`, `display_dates_sheet`, `display_dates_save`, `display_dates_reset`, `display_dates_refused`, `display_next`.

- [ ] **Step 1: The failing tests** (stub: `checkRange` null).
  - `checkRange`: 24 Oct → 31 Oct null; 31 Oct → 24 Oct "The last day is before the first. Tap a last day on or after 31 Oct."; 20 Dec 2026 → 2 Jan 2027 null; 24 Oct 2026 → 24 Oct 2027 "A theme can show for a year at most. Tap a last day before 24 Oct 2027." (the year shown only when it differs from the first day's).
  - `DisplaySettingsPageTest`:
    - `eachThemeShowsItsDates`: `display_dates_halloween` reads "Halloween" and "24 Oct – 31 Oct", click label "Change the dates".
    - `pickingTwoDaysSavesThem`: open, tap 20 Oct then 2 Nov, "Save dates" → `onDates(HALLOWEEN, SeasonDates(20 Oct, 2 Nov))`, the sheet closes.
    - `aLastDayBeforeTheFirstIsRefused`: tap 31 Oct then 24 Oct → `display_dates_refused` reads the line; "Save dates" is disabled; nothing saved.
    - `puttingBackTheDefaults`: with 20 Oct – 2 Nov saved, "Put back 24 Oct – 31 Oct" → `onDates(HALLOWEEN, null)`.
    - `cancelSavesNothing`.
    - `theNextLineShowsOutsideTheDates`: `next = (HALLOWEEN, 2027-10-24)` → `display_next` "Next: Halloween from 24 October"; `next = null` → absent.
  - Screenshots: new `settings_display_dates_sheet_dark` / `_light` (20 Oct – 2 Nov picked), `settings_display_next_dark` / `_light`.

  Run `./gradlew :app:testDebugUnitTest --tests '*DisplaySettings*'`. Expected: FAIL on assertions.
- [ ] **Step 2: Implement** per ruling 13 and the Copy.
- [ ] **Step 3: Images.** **New:** the four above. **Changes:** `settings_display_preview_dark`, `settings_display_preview_light`, and any `settings_display_*` image whose foot shows the section.
- [ ] **Step 4: The gate, then commit.**
```bash
git add app
git commit -m "Let Settings › Display change each seasonal theme's dates or put back the defaults, and say which theme comes next"
```

---

### Task 13: Simplify the whole branch

- [ ] **Step 1:** Run the `simplify` skill over `git diff main...HEAD`. Look hardest at: `SeasonalLook`'s call sites (each one line); `SlotClock` against a plain `Text` with a fixed width; `SeasonalThemes`' flows (one `combine` if two read alike); `HalloweenArt`'s paths (shared builders); `MediaPlayerChime`'s fallback.
- [ ] **Step 2:** For each change, the module's tests, then the gate.
- [ ] **Step 3:** Commit when anything changed: `git commit -am "Simplify the seasonal theme code"`.

---

### Task 14: The emulator walk-through on `culvery-5` (spec §7)

**Rules:** only `culvery-5` with a debug build and `install -r`; the user types real PINs, Claude enters the debug sample PINs; faults are collected and asked about before fixing; no house device touched.

- [ ] **Step 1: Walk** (one line each).
  1. In the dates: Halloween by night and by day via the status bar's Day/Night preview; every tab, a sheet, the PIN pad, a toast, Settings and the door card in Halloween's colours.
  2. The pumpkin, the web, the moon and bats; the bats drift; the clock in Creepster, the header still across a minute's change.
  3. The night clock (`DebugClockReceiver` into the night hours): unchanged; the bats stop (`dumpsys gfxinfo` shows no new frames).
  4. A sheet, the PIN pad, Settings over Home, and "Remove animations" on: the bats still.
  5. Outside the dates (`DebugClockReceiver` forward past 1 November 00:00): the colours fade back at midnight with no restart.
  6. Preview on outside the dates: Halloween shows and Play chime plays the organ; off: both go; on again, then the clock past midnight: it ends and the switch reads off.
  7. Settings › Display's dates: change to 20 Oct – 2 Nov and back; a last day before the first is refused; "Put back"; the "Next:" line outside the dates.
  8. Settings › Door: each sound with Play chime; a sample ring with each choice, during and outside Halloween.
- [ ] **Step 2: The fault loop**; put back every setting changed; `DebugClockReceiver --el minutes 0`; `emu kill`.
- [ ] **Step 3: Record** under "Plan 14 (seasonal themes): emulator walk-through", no names. Commit: `git commit -am "Record Plan 14's emulator walk-through"`.

---

### Task 15: Final review, merge, install

- [ ] **Step 1:** Opus reviews `git diff main...HEAD` against the spec and this plan, looking first at readability in both Halloween sets, the night clock, the bats' stopping and cost, midnight, the Door seam and fallback, the look as the only place a theme is named, and the font's licence and hash. One fix wave, each fix with its failing test first, then the gate.
- [ ] **Step 2:** Finish with `superpowers:finishing-a-development-branch`: only on the user's say, `git merge --ff-only plan-14-seasonal-themes` on `main`, the gate on `main`; never push. Move the design cuts and the S6 note to the follow-ups.
- [ ] **Step 3:** The release from the merged `main` on the tablet with `install -r`, the mapping kept as in Task 10.

---

## Walk-through

1. 24–31 October: the wall is Halloween by day and by night, with the normal look back on 1 November at midnight.
2. Halloween: orange on plum by night, burnt orange on cream by day; a pumpkin for the mark; a cobweb on Today; a moon and slow bats behind Home; the big clock in Creepster.
3. The night clock is exactly as it was.
4. Settings › Display: "Preview Halloween" (colours and doorbell) until midnight; each theme's dates, with "Put back"; "Next: Halloween from 24 October" outside the dates.
5. Settings › Door: "Doorbell sound" (Follow the theme, Classic chime, Haunted organ); Play chime plays the one chosen.

**Design cuts to say again** (provisional, for the end-of-v1 review):
- the day set's moved values (`surf`, `surf2`, `ink`, `accentSoft`) and the pink-red warning colour;
- the clock in orange, Creepster's digits in fixed slots, and its size if Task 6 shrank it;
- the day bats' darker ink (`0xFFDDD0C0`), the moon kept above the weather (or dropped by day), the bats' size, number, lanes, speed and 15 fps;
- the web's size and colour; the pumpkin's drawing;
- the organ's arrangement, length and loudness on the tablet;
- the radio rows for the doorbell, the switch for the preview, the date sheet's two taps.

## Not in this build, on purpose

- Christmas or any other theme; a "never show" switch (change the dates instead).
- A theme for the night clock, the launcher icon or the house's devices.
- Keeping a preview across a restart.
- The themed doorbell for a ring in the first moments after a restart, before the clock is read (S6: it plays the classic chime).

---

## Spec coverage (14 → tasks)

| Spec | Task |
|---|---|
| §1 intent, goals, anti-goals | 1, 2, 9, 10 |
| §2.1 switching by date, preview only | 2, 8, 11, 12 |
| §2.2 full dress: colours, pumpkin, cobweb, moon and bats, Creepster | 1, 6, 7, 9 |
| §2.3 Creepster from Google Fonts, licence checked | 6 |
| §2.4 day version, Day/Night preview | 1, 2, 10 |
| §2.5 night clock unchanged | 2 |
| §2.6 the organ, generated | 3 |
| §2.7 Settings › Door's doorbell sound | 4 |
| §2.8 staged delivery | 5, 10 (stage 1), 11–12 (stage 2) |
| §3 every screen, bats stopping, pinker red, person colours, preview to midnight, PINs, adding a theme | 1, 2, 4, 8, 9 |
| §4.1 which theme, wrap, overlap, midnight, stored dates | 1, 2, 11 |
| §4.2 colours, contrast over four sets, danger vs accent, screenshots | 1, 2, 6, 7, 9 |
| §4.3 drawn touches | 7, 9 |
| §4.4 clock face | 6 |
| §4.5 Settings › Display | 8, 12 |
| §4.6 doorbell | 3, 4 |
| §5 constraints | all; 5, 10, 14, 15 |
| §6 left to the builder | rulings 1–14 |
| §7 walk-through checks | 10, 14 |
