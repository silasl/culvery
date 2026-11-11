# Culvery — Plan 21: Christmas, the second seasonal theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–8 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 9 is the controller. Task 10 is the controller on the `culvery-5` AVD with the user. Tasks 11 and 12 need the user and the SM-T510.

**Goal:** a Christmas theme dresses the wall from 1 December to 6 January: pine green and holly red by night, cream and deeper red by day, snow falling across Home's header behind the clock, fairy lights in the open band, a Mountains of Christmas clock, the Culvery house snowed in for the mark, holly on the Today card, and the Christmas music box for a doorbell that follows the theme.

**Architecture:**
- **`:core:ui`** gets Christmas as a second look on Plan 14's seam: `SeasonalTheme.CHRISTMAS` and its `SeasonalLook`, built from `ChristmasNight`/`ChristmasDay` (`ChristmasLook.kt`) and the drawings (`ChristmasArt.kt`). The sky seam is already in place (each sky gets Home's whole room and narrows itself); Christmas adds `headerArea`/`inHomeHeader` beside `skyBand`/`inSkyBand` in `SkyBand.kt`, and shares the bunting's hanging curve and its "how many fit" count. The house and dove paths move here from `:app` so the Christmas mark is drawn from the same paths as `CulveryMark`.
- **`:core:sound`** tags `CHRISTMAS_MUSIC_BOX` with the theme; Door's *Follow the theme*, the sound pads' list and the `christmas/` tablet folder pick it up with no new code.
- **`:app`** changes only where the code needs it: `CulveryMark` reads the moved paths; `nextTheme`, `shownRange` and the dates sheet stop rewriting a stored 29 February. Every theme list already loops over `SeasonalTheme.entries`.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth 1.4.4 + Turbine 1.2.1, Roborazzi 1.46.1. **No new library.** One new asset: Mountains of Christmas Bold (Apache License 2.0), approved by the user on 2026-11-11, from google/fonts at commit `85402ba9968a3ac7e32e4a5be29abc1298f52a3a` (2026-03-25; its `apache/mountainsofchristmas/` folder holds `MountainsofChristmas-Bold.ttf`, 123 172 bytes, git blob `81efb57588c4529190d8292bc457a24ba6fa63af`, `METADATA.pb` with `license: "APACHE2"`, and `LICENSE.txt`).

**Spec:** `docs/superpowers/specs/2026-11-11-culvery-21-christmas-design.md`, all of it. Its "*My call*" items are settled; this plan doesn't reopen them except where "Rulings against the code" says the code disagrees.

**Previous plans (format, shared parts):** `docs/superpowers/plans/2026-10-29-culvery-14-seasonal-themes.md` (the seam, the house rules, the UI-task ending, the bats' cost), `2026-11-03-culvery-16-birthdays.md` (the bunting, the birthday look), `2026-11-05-culvery-18-sounds.md` (`BuiltInSound.theme`, `SoundLibrary.forTheme`).

**Builds on:** branch `plan-21-christmas` in the one checkout `C:\Users\si\code\siland\culvery`, cut from `main` at baa37ead (the spec is committed on it at 760432db). No worktrees. Never push.

**Deadline:** on the SM-T510 by about **24 November 2026**, so the user can see it by day and at night before 1 December. Tasks are in the order the user ruled (P2): the essentials first (1 colours and contrast, 2 which theme shows and the Christmas screenshots, 3 the music box), then the snow and lights (4), the clock (5), the mark (6), the holly (7) and the 29 February fault (8). **If time runs short, the controller asks the user before cutting anything, and cuts in this order: 8 (29 February) first, then 7 (holly), 6 (mark), 5 (clock).** Tasks 1–4 are never cut.

## Plan review rulings (2026-11-11)

The user ruled every plan-review finding as recommended. Each is folded in where named.

| Id | Ruling |
|---|---|
| P1 | The snow cap is drawn **over** the roof inside the existing `MarkViewport` (TOP 29, HEIGHT 49), with a thin `bg` stroke between cap and roof, as `mark-v2.html` does. The house keeps its full size and `NavRailTest`'s equal-bounds check holds (Task 6). |
| P2 | Order: 1 colours, 2 which theme shows, 3 music box, 4 snow and lights, 5 clock and font, 6 mark, 7 holly, 8 29 February. Cut order if short (asking the user first): 29 February, holly, mark, clock. |
| P3 | `ChristmasDay.ink` is `#0B130F`: at `#13201A` it fails `aPastDayReadsAtItsAlpha` (4.29) and `outsideMonthDaysKeepTheirContrast` (4.18); `#0B130F` gives 4.78 / 4.59 (Task 1, ruling 2). |
| P4 | Warnings against weather: the normal and birthday gaps stay pinned at today's values, unchanged; Task 12 puts the gap in the follow-ups for the end-of-v1 design review (ruling 3). |
| F1 | Pin literals round down: normal day 35.9°, Sam's pink birthday night 22.4° (Task 1). |
| F2 | The snow's maths stated exactly; `elapsedMillis % SNOW_FALL_MS` and `% TWINKLE_MS` taken on the `Long` before any `Float` (as `HalloweenArt.kt:153`, `BirthdayArt.kt:84`); 40-day tests for both (Task 4). |
| F3 | `aFlakeWrapsFromTheBottomToTheTop` checks one flake just before and just after its own wrap time; `theBulbsTwinkleOutOfStep` compares at `TWINKLE_MS / 4`; `aStillSkyDrawsTheSnowAndTheLightsAtFull` uses the largest flake clear of the lights (Task 4). |
| F4 | `theLightsKeepToTheOpenBand` is a pixel check like `skyStaysInTheBand` (`CulveryShellSkyTest.kt:109-145`); `theSnowFallsBehindTheWholeHeader` takes the header's bounds from the clock, date and items, with no `HomeScreen.kt` change (Task 4). |
| F5 | `Flake`, `snowFlakes`, `bulbCentres` and `bulbAlpha` are all public (`:app`'s test uses `bulbCentres`) (Task 4). |
| F6 | One shared `fitCount(widthPx, pitchPx, min)` replaces `flagCount` and the planned `bulbCount`; a shared `hangingPath` beside `hangingY` in `SkyBand.kt` draws both the bunting's string and the lights' wire. The birthday baselines must not change (Task 4). |
| F7 | `theSkyDriftsWhileAsked` stays; `aStepIsAboutAFrameAndAWait` is dropped (`SkyFrames` is already tested); `stillMeansNoFrames` stays (Task 4). |
| F8 | `ChristmasArtTest` draws the mark at 216 dp over `Box(Modifier.background(bg))`, as `HalloweenArtTest` does, matching colours by nearest colour within a tolerance; Step 1 names the `ChristmasMark` stub (Task 6). |
| F9 | The font is downloaded before the clock task's failing tests (`R.font` must exist); `git hash-object` of the TTF must equal the blob sha above before the sha256 is recorded (Task 5). |
| F10 | `ThemedClockTest` runs **seven** cases (Task 5, Review Focus 4). |
| F11 | Task 1's baseline list is exact, `settings_app_dark` and `settings_app_light` included; the implementer edits the list to what `compareRoborazziDebug` shows before the "listed but unchanged" check. |
| F12 | `DisplaySettingsScreenshotTest`'s `snap` and `datesSheet` gain `today` and `theme` parameters for the Christmas sheet shot (Task 2). |
| F13 | 29 February: `nextTheme` and `shownRange` skip a range empty in a year (29 Feb – 29 Feb in a common year); with both ends 29 February nothing moves, so no line shows; the line names `shown.year`, not "this year"; a pin for 28 Feb – 10 Mar; the line keys off the range the sheet opened on (Task 8). |
| F14 | Accepted, as for Halloween: a ring right after a cold start, before the clock is read, plays the classic chime; edited Christmas dates may show the defaults for a moment after a restart. A pin that the music box is in the sound pads' list (Task 3). |
| F15 | `rainAndWeatherStayApart`, `theThemesDontOverlap` and `christmasRunsFrom1DecemberTo6January` are **pins**; `rainAndWeatherStayApart` gets a mutation proof (Task 1). |

---

| # | Task | Review |
|---|---|---|
| 1 | Christmas's entry, colours and contrast; the existing tests and Seasonal looks baselines that move | **opus** (readability, warnings) |
| 2 | Which theme shows over New Year; Christmas screenshots; the night clock pinned | sonnet |
| 3 | The music box follows Christmas | sonnet |
| 4 | Snow across the header, lights in the band | **opus** (cost, text over snow) |
| 5 | Mountains of Christmas and the Christmas clock face | sonnet |
| 6 | The house, snowed in (paths move to `:core:ui`) | sonnet |
| 7 | Holly on the Today card | sonnet |
| 8 | The 29 February fault | sonnet |
| 9 | Simplify and whole-branch review | controller + opus reviewer |
| 10 | The emulator walk-through on `culvery-5` | controller + user |
| 11 | The release on the tablet, frame cost, the holly's size | controller + user, **needs the SM-T510** |
| 12 | The follow-ups | controller |

## Global Constraints

**From the spec, verbatim:**
- §1 non-goals: "No house devices are touched (Hue, Sonos, plugs, Home Assistant). Party and Holiday mode are unchanged." "No change to Halloween's look, except where shared code moves under it (§4.4)." "No launcher icon change."
- §1 anti-goals: "Anything the family reads becomes harder to read, including the big clock with snow falling behind it." "Warnings look like buttons. With red as the accent, warnings must not stay red." "The night clock changes, or lights the room more." "The snow or lights keep the screen or CPU busy while the tablet is dark or asleep, or cost more per frame than Halloween's bats after their fix".
- §2: "Switching is by dates only … A per-theme **Preview** is the only manual way in. It ends when turned off or at midnight." "A birthday wins over a seasonal look (Plan 16)."
- §3.1 "1 December – 6 January by default, inclusive, editable like Halloween's."
- §4.4 motion: "it moves only while `drifting` (Home showing, screen awake, animations on), stepping at `SKY_FRAME_MS` (~15 fps). Still, it draws once: snow where it is, lights at full. Asleep or dark, no frames."
- §4.4 cost: "Pass is a 90th percentile of 12 ms or under and janky frames under 5%."
- §4.5 "The date, the night clock and every other text stay in DM Sans."
- §5 "Halloween's and the birthday's Home, sheet and door card baselines must not change."

**Copy** (curly apostrophes, "›", "–" in code; each owning task tests its strings):
- Theme label "Christmas". Seasonal looks rows: "Christmas" with "1 Dec – 6 Jan"; "Preview Christmas" with "Shows Christmas on the wall and plays its doorbell until midnight." / "Showing now. It ends at midnight."; sheet "Christmas dates", "Put back 1 Dec – 6 Jan".
- App settings › Display row summary: "Halloween 24–31 Oct · Christmas 1 Dec–6 Jan".
- "Next: Christmas from 1 December".
- This plan's (spec §4.8 gives the meaning, not the words), `{yyyy}` the year the sheet shows: "The saved first day is 29 February, which in {yyyy} is 1 March." and "The saved last day is 29 February, which in {yyyy} is 28 February."
- A file in the tablet's `christmas/` folder: "On this tablet · Christmas".

**House rules** (binding):
- **Platform:** package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only; **no new library; never change a version**; **no deprecated API without asking the user first** (flag it to the controller, who asks). `Font(resId, FontWeight)`, `drawWithCache`, `withFrameMillis`, `graphicsLayer`, `addPathNodes` are current.
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash, or `gradlew.bat`), plus `./gradlew -p build-logic :convention:test` in any task that adds a log line (none is planned). Never plain `test`, `check` or `build`, never a Gradle `install…` task. No subagent touches a device.
- **Scratch:** `$LISTS` = `C:/t9`, a plain shell variable, never exported. **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`**: a long temp path breaks Robolectric past MAX_PATH.
- **Tests that can fail:** every new test fails before its change; one that passes before it is labelled **pin** in its KDoc. A logic test goes red on its assertion: Step 1 adds the production member with its final signature returning a wrong value (`null`, `false`, an empty list, the normal colours), runs the tests and sees assertion failures, then the body is written. Frame tests run on `compose.mainClock` with `autoAdvance = false`.
- **Every UI task ends as Plan 14's:**
  1. `./gradlew <module>:compareRoborazziDebug`; open each `*_compare.png` and check it against what the task names;
  2. `./gradlew <module>:recordRoborazziDebug` (Windows only);
  3. the baseline check: write `$LISTS/expected-baselines.txt` (the task's **New** and **Changes** names) and an empty `$LISTS/expected-prefixes.txt` afresh, then
```bash
git status --porcelain -- "*.png" | sed 's#.*/##; s#\.png$##' | sort -u > "$LISTS/changed.txt"
echo "changed but not listed:"; comm -23 "$LISTS/changed.txt" <(sort -u "$LISTS/expected-baselines.txt")
echo "listed but unchanged:"; comm -13 "$LISTS/changed.txt" <(sort -u "$LISTS/expected-baselines.txt")
echo "changed but not predicted:"; grep -v -f <(sed 's/^/^/' "$LISTS/expected-prefixes.txt") "$LISTS/changed.txt"
echo "predicted but unchanged:"; while read -r p; do grep -q "^$p" "$LISTS/changed.txt" || echo "$p"; done < "$LISTS/expected-prefixes.txt"
```
     The first two lists must be empty, or stop. A baseline that moved and isn't listed is restored file by file (`git restore -- <that file>`), never with a blanket restore;
  4. the gate; 5. commit.
- **Baselines that never change in this plan:** every `*halloween*` and `*birthday*` image except `settings_seasonal_birthday_dark` (Task 1), every normal `home_*`, `today_*`, `door_card_*` and `night_clock*` image. New Christmas images are recorded `_dark` and `_light`. `CopyScanTest` passes; every screen test ends with `compose.assertClickablesHaveRoleAndLabel()`.
- **Code:** call sites read the look (`Culvery.look`, `SeasonalTheme.look`), never compare with a theme; only `Seasonal.kt`, `<Theme>Look.kt` and `BuiltInSound.kt` name `HALLOWEEN` or `CHRISTMAS` in main sources (`SeasonalSourceTest`). Layout and drawing numbers go in `ChristmasDimens`; drawing colours in `ChristmasInks`. A time fed into a phase is reduced with `%` on the `Long` before it becomes a `Float`. Comments say only what the code can't, one line by default, spec refs as `(21 §4.4)`, never a task number.
- **Privacy:** no personal data in logs, test names, fixtures or committed files. Sample data only (Sam, Alex).
- **Devices:** the emulator is `culvery-5`; `emulator-5554` holds a signed release and the household: never install on it. The tablet (SM-T510) gets `adb -s <tablet serial> install -r` of a signed release only; **never uninstall, never wipe**; write `<tablet serial>`, never the real one. No Hue, Sonos, plug or Home Assistant change.
- **Git:** a commit is one plain sentence with no prefix and **no Co-Authored-By or any other trailer**; never push; merge only on the user's say. **Never** `git checkout -- .`, `git restore .` or `git stash`.
- **The font download** (Task 5) is approved: the bold file, its `LICENSE.txt` checked to be the Apache License 2.0, committed as `core/ui/licenses/Apache-MountainsOfChristmas.txt`, the commit, blob and sha256 recorded. Nothing downloaded is run.

## Review Focus

1. **The snow under text: a flake passing behind the clock's digits, the date, the weather icon and its words, by day and by night.**
   - **Expected:** every pair still passes its check (clock 3:1 as large text, date and words 4.5:1, icons 3:1); the snow is faint against `bg`.
   - **Tests** (Task 4): `ContrastTest.textReadsOverTheSnow`; `CulveryShellSkyTest.theSnowFallsBehindTheWholeHeader`.
2. **New Year and the edges of the dates: 30 Nov → 1 Dec at midnight, 31 Dec → 1 Jan, 6 Jan → 7 Jan, the "Next:" line in the gap, a birthday in December.**
   - **Expected:** Christmas starts at 00:00 on 1 December, holds over New Year, ends at 00:00 on 7 January with no restart; "Next: Christmas from 1 December" in November; a birthday dresses the wall while the doorbell keeps Christmas's showing theme.
   - **Tests** (Task 2): `SeasonalThemesTest.christmasStartsAsDecemberStarts`, `christmasRunsOverNewYear`, `nextIsChristmasInNovember`, `aBirthdayInDecemberWins`.
3. **A stored 29 February opened in a common year and saved untouched.**
   - **Expected:** the store still holds 229; switching starts on 1 March; "Next:" says 1 March; the sheet says why it shows 1 March and Save stays off until a day is tapped.
   - **Tests** (Task 8): `DisplaySettingsPageTest.aSavedLeapDayIsntRewrittenByOpeningTheSheet`, `SeasonalThemesTest.nextSaysTheDayALeapDayStartReallyStarts`.
4. **The header jumping: Mountains of Christmas's line box, its proportional digits from 11:11 to 00:00, the theme switching on at midnight.**
   - **Expected:** the clock's box is the same height in DM Sans and Mountains of Christmas, the same width every minute, nothing draws into the date, the header items stay put.
   - **Tests** (Task 5): `ThemedClockTest[christmas]`'s seven cases.
5. **The doorbell: a ring with *Follow the theme* during Christmas, on a December birthday, with a file in `christmas/`, with the music box chosen outright.**
   - **Expected:** the music box; Happy Birthday on the birthday; the tablet file over the built-in; the music box all year when chosen.
   - **Tests** (Task 3): `DoorSettingsTest.followTheThemeAtChristmasIsTheMusicBox`, `SoundLibraryTest.aChristmasFileBeatsTheMusicBox`, `DoorSettingsPageTest.playChimeAtChristmasPlaysTheMusicBox`.

## Rulings against the code

The spec leaves these to the builder, or the code argues with it. Each is this plan's call.

1. **The sky seam needs no shell change.** `CulveryShell` already hands every sky Home's whole room (`Modifier.matchParentSize()`) under `LocalHomeSkyBand`, and the header is placed at that room's top-left. Halloween's and the birthday's skies narrow themselves with `inSkyBand` and stay untouched. Christmas adds, in `SkyBand.kt`, `headerArea(measured, size)` (`Rect(0, 0, headerWidth, headerHeight)`, or all of `size` while unmeasured) and `Modifier.inHomeHeader(measured)`; its one node covers the header and finds the band inside it with the existing `skyBand(measured, …)`, whose coordinates share the header's origin.
2. **Christmas's colours** (`ChristmasLook.kt`). Starting values, worked out against the main pairs (text on surfaces, accent as text, danger, weather, rain, buttons, toast) and the past-day and outside-month alphas (P3); the full `ContrastTest` run is the judge, and the implementer moves any value a test still fails by the smallest step that passes and says which in the report:

   | token | `ChristmasNight` | `ChristmasDay` |
   |---|---|---|
   | bg | 0xFF0F1A14 | 0xFFF4EFE6 |
   | surf | 0xFF16241C | 0xFFFFFEFB |
   | surf2 | 0xFF203126 | 0xFFF1EBE1 |
   | surf3 | 0xFF2B3E31 | 0xFFE4DCCD |
   | line | 0xFF1B2A21 | 0xFFE3DACB |
   | ink | 0xFFF1F4EF | **0xFF0B130F** |
   | mute | 0xFF9FB0A4 | 0xFF4F5E55 |
   | accent | **0xFFEE6A6E** | 0xFFB3262C |
   | accentInk | 0xFF1A0405 | 0xFFFFFFFF |
   | accentOnInk | 0xFFB3262C | 0xFFEE6A6E |
   | accentSoft | 0xFF3A1A1B | 0xFFF8DEDE |
   | danger | 0xFFF2A33A | 0xFF8A5300 |
   | dangerSoft | 0xFF3A2A14 | 0xFFFAEBD3 |
   | dangerInk | 0xFF1A0F02 | 0xFFFFFFFF |
   | weather | 0xFF9FD3EA | 0xFF2A6A86 |
   | rain | **0xFF9C9EF2** | **0xFF4A4FB8** |
   | border | `Color.Transparent` | 0xFFDCD2C2 |

   - **The day ink is darker than the mockup's (P3):** #13201A gives 4.29 on the past-day check and 4.18 on the outside-month check; #0B130F gives 4.78 and 4.59.
   - **The night red moves further than the spec's #E85A5E:** that value is 5.1:1 on `bg` but 3.96:1 on `surf2` (`accentAsText` checks `surf2` too). #EE6A6E is the smallest step on the same hue that passes (4.53 on `surf2`, 5.15 on `accentSoft`), still a clear holly red. Danger against it: 36.1° (passes 35°).
   - **Rain clashes with the ice blue, so it moves:** normal rain #7FB2E5 is 11.6° from #9FD3EA (day 11.5°), the same blue. Christmas's rain is periwinkle (40.2° night, 39.0° day from the weather), checked by `rainAndWeatherStayApart` at 35° in every set (normal, Halloween and birthday sets are about 168° apart, so they pass as they are).
3. **Warnings against the weather** (`dangerAndWeatherStayApart`; P4): the spec asks for 35° in every set, and for the normal and Halloween sets to pass as they are. **They don't all:** the normal night set is 34.2° (#EE7B6A against #E0B85B), the normal day 35.9°, Halloween about 58° / 60°; the birthday nights that keep the normal danger are 34.2°, and Sam's pink birthday (index 2) is 22.4° by night and 26.8° by day (figures rounded down). Per the spec and the user's ruling, nothing is changed: the test asserts 35° for every seasonal theme's two sets, and pins the normal and birthday sets at the values they measure today (rounded down to 0.1°). Task 12 puts the gap in the follow-ups for the end-of-v1 design review. Christmas: 164.2° / 162.2°.
4. **`weather` has two other users, which follow the ice blue under Christmas:** the editor's Clash tag (`EditorRows.kt:64`) and the free-check "busy" line (`ClashLine.kt:94`). Moving them to `danger` would change the normal look's clash tag, which the spec doesn't ask for. They stay on `weather`; `theClashTagReadsOnTheRow` already checks the Christmas sets; it's a design cut to say again (Task 10).
5. **The snow ink is fainter than the spec's example.** #3C5546 on night `bg` puts the clock's red at 2.35:1 and the date's `mute` at 3.57:1 over a flake. Starting values: night `NIGHT_SNOW = 0xFF2C4034` (clock 3.66, date 4.88, ink about 10, weather icon 6.9, `bg` 1.60), day `DAY_SNOW = 0xFFD3DCD4` (clock 4.63, date 4.88, weather icon 4.27, `bg` 1.23). The header items' text is `ink` and `mute`; only their icons are `weather`/`rain`, so those are checked at 3:1.
6. **The Seasonal looks baselines are re-recorded in Task 1, not in a late task.** The page loops `SeasonalTheme.entries`, so the Christmas row appears the moment the entry exists, and the gate runs on every commit. Task 1 also updates the existing tests the entry moves (listed there). Task 8 changes no baseline (the screenshot tests open Halloween's default dates on 15 September 2026).
7. **The house and dove paths move** to `core/ui/.../MarkPaths.kt` as public `const val MARK_HOUSE`, `MARK_DOVE` and `object MarkViewport { LEFT = 26f; TOP = 29f; WIDTH = 54f; HEIGHT = 49f }`; `CulveryMark` and `CulveryMarkTest` import them, so the launcher-icon pin still holds. The snow cap fits inside this viewport, over the roof (P1).
8. **The "+" stays where it is.** The holly is small and drawn behind the content inside the card's clip, as the cobweb is; moving the "+" would change the Today card in every look. If the user wants it moved after the tablet, it's a follow-up.
9. **The font is bundled whole** (123 KB, as Creepster), not subset: no subsetting tool in the build, and the gain is small.
10. **The 29 February rule** (spec §4.8; F13) applies to both ends. `fun MonthDay.firstDayIn(year: Int): LocalDate` (in `Seasonal.kt`) gives 1 March for a 29 February start in a common year, else `atYear(year)`; ends keep `atYear` (28 February, which `SeasonDates.contains` already uses). `nextTheme` and `shownRange` use `firstDayIn` for starts and skip a year in which the range holds no day (29 Feb – 29 Feb in a common year), going on to the next year that holds it. The sheet's Save is off while the days it opened on differ from the saved ones and nothing has been tapped; in every other case Save behaves as today.
11. **The lights hang as the bunting does** (F6): `stringY` and the bunting's string path move from `BirthdayArt.kt` to `SkyBand.kt` as `hangingY` and `hangingPath`; `flagCount` becomes the shared `fitCount`. The lights' wire is `mute`, as the bunting's string. The birthday baselines must not change.
12. **"At least one sheet"** (spec §4.2) is Christmas's own dates sheet under Christmas, which also shows the range over New Year.
13. **Accepted, as for Halloween** (F14): a ring right after a cold start, before the household clock is read, plays the classic chime even in December; edited Christmas dates may show the defaults for a moment after a restart until the settings are read.

## File Structure

```
core/ui/src/main/java/uk/co/siland/culvery/core/ui/
  Seasonal.kt (CHRISTMAS, look)                                                        (Task 1)
  ChristmasLook.kt (ChristmasNight, ChristmasDay, ChristmasLook; ChristmasInks from 4) (Tasks 1, 4–7)
  SkyBand.kt (headerArea, inHomeHeader, hangingY, hangingPath, fitCount),
    BirthdayArt.kt (uses them), ChristmasArt.kt (snow, lights, ChristmasSky, ChristmasDimens) (Task 4)
  ClockFace.kt; res/font/mountains_of_christmas_bold.ttf;
    licenses/Apache-MountainsOfChristmas.txt; README.md                                (Task 5)
  MarkPaths.kt (MARK_HOUSE, MARK_DOVE, MarkViewport); ChristmasArt.kt (ChristmasMark)  (Task 6)
  ChristmasArt.kt (drawHolly)                                                          (Task 7)
  Seasonal.kt (firstDayIn)                                                             (Task 8)
core/sound: BuiltInSound.kt                                                            (Task 3)
app: shell/ui/CulveryMark.kt                                                           (Task 6)
  display/SeasonalThemes.kt (nextTheme), display/DisplayWords.kt,
    display/ui/DisplaySettingsPage.kt (shownRange, DatesSheet)                         (Task 8)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                   (Tasks 10–12)
```

---

### Task 1: Christmas's entry, colours and contrast (spec §3.1–3.3, §4.1, §4.2; rulings 2–6; P3, P4, F1, F11, F15)

**Review:** opus. Every pair the family reads in both Christmas sets; warnings apart from the red accent and from the weather; Halloween's ink checks on Halloween's colours only.

**Files:**
- Modify `core/ui/.../Seasonal.kt` (the entry after `HALLOWEEN`; `CHRISTMAS -> ChristmasLook` in `look`).
- Create `core/ui/.../ChristmasLook.kt` (`ChristmasNight`, `ChristmasDay`, `ChristmasLook` with colours only).
- Test: `core/ui` `ContrastTest.kt`, `ThemeTest.kt`, `SeasonalTest.kt`, `SeasonalSourceTest.kt`; `app` `display/SeasonalThemesTest.kt`, `display/DisplaySettingsTest.kt`, `display/ui/DisplaySettingsPageTest.kt`, `settings/AppSettingsPageTest.kt`.

**Interfaces:**
- Produces: `SeasonalTheme.CHRISTMAS("Christmas", SeasonDates(MonthDay.of(12, 1), MonthDay.of(1, 6)))`; `val ChristmasNight: HhColors`, `val ChristmasDay: HhColors`, `val ChristmasLook: SeasonalLook` (ruling 2; later tasks add `sky`, `clockFace`, `mark`, `cardCorner`).
- In `ContrastTest`: `private fun setsOf(theme: SeasonalTheme): List<Pair<String, HhColors>>` (`"{label} night"` to `look.night`, `"{label} day"` to `look.day`); `halloween = setsOf(HALLOWEEN)`, `christmas = setsOf(CHRISTMAS)`; later tasks' Christmas checks loop `christmas`.

- [ ] **Step 1: The failing tests** (stub: `ChristmasLook` built from `DarkColors` / `LightColors`).
  - `ContrastTest`: the `halloween` list (line ~40) becomes `setsOf(HALLOWEEN)`, so `theMoonAndBatsStandOffTheBackground` and `theWebReadsUnderCardText` run on Halloween's two sets only (**pins**). `themes` already takes every entry, so every existing loop covers Christmas. Add:
    - `dangerAndWeatherStayApart`: every `SeasonalTheme.entries` set, `hueGap(t.danger, t.weather) >= 35f`, message naming the set.
    - `theNormalAndBirthdayWarningGapsStayWhereTheyAre` (**pin**, ruling 3): a literal map from set name to its gap today, rounded **down** to 0.1° (normal night 34.2, normal day 35.9, Sam's pink birthday night 22.4, day 26.8, the other birthday sets as measured); each set's gap is at least its value.
    - `rainAndWeatherStayApart` (**pin**: it passes against the stub): every set in `themes`, `hueGap(t.rain, t.weather) >= 35f`. Mutation proof, once, not committed: set `ChristmasNight`'s `weather` to 0xFF9FD3EA with `rain = DarkColors.rain` and see it fail on "Christmas night"; then put the real values in.
  - `ThemeTest`:
    - `christmasProvidesItsSets`: `CulveryTheme(dark = true, seasonal = CHRISTMAS)` → `Culvery.colors == ChristmasNight`, `Culvery.look === ChristmasLook`; `dark = false` → `ChristmasDay`.
    - `christmasWarningsAreAmberAndItsWeatherIceBlue`: in both sets, `hueGap(danger, Color(0xFFF2A33A)) <= 10f` and `hueGap(weather, Color(0xFF9FD3EA)) <= 10f`, so a contrast step can move a value without leaving amber or ice blue; `ChristmasNight.border == Color.Transparent`.
    - `aBirthdayStillBeatsChristmas` (**pin**): `CulveryTheme(dark = true, seasonal = CHRISTMAS, birthday = Birthday(PersonPalette.colors[0], listOf("Sam")))` → `Culvery.look === birthdayLook(PersonPalette.colors[0])`.
  - `SeasonalTest`:
    - `christmasRunsFrom1DecemberTo6January` (**pin**: the entry's dates exist with the stub): 30 Nov 2026 false, 1 Dec true, 31 Dec true, 1 Jan 2027 true, 6 Jan true, 7 Jan false.
    - `theThemesDontOverlap` (**pin**): no day of 2026 or 2028 is in both themes' defaults.
    - `theDefaultsAreEveryThemesOwn` gains Christmas's 1 Dec – 6 Jan.
  - `SeasonalSourceTest.onlyTheLookNamesATheme`: for each `SeasonalTheme` name, the main sources naming it are a subset of `Seasonal.kt`, `{Label}Look.kt` and `BuiltInSound.kt`.
  - Existing tests the entry moves, updated to say Christmas (each a plain update, not new behaviour):
    - `SeasonalThemesTest.nextIsTheSoonestStart`: on 2026-11-05 → `CHRISTMAS to 2026-12-01`; on 2026-10-01 → `HALLOWEEN to 2026-10-24`; on 2027-01-07 → `HALLOWEEN to 2027-10-24`.
    - `nextShowsOnlyWhileNothingShows`: at 11-05 → `CHRISTMAS to 2026-12-01`.
    - `nextWrapsTheNewYear`: its map becomes `CHRISTMAS to SeasonDates(12-20, 1-2)` and `HALLOWEEN to SeasonDates(12-22, 12-23)` (so Halloween is never the sooner start), and asserts the pairs: on 2026-12-01 → `CHRISTMAS to 2026-12-20`; on 2027-01-03 → `CHRISTMAS to 2027-12-20`.
    - `DisplaySettingsPageTest` summary: `seasonalSummary(defaultSeasonDates()) == "Halloween 24–31 Oct · Christmas 1 Dec–6 Jan"`; the 28 Oct–2 Nov case keeps Christmas's default after it.
    - `AppSettingsPageTest.displayRowsOpenTheirSubPages`: the seasonal row reads "Seasonal looks", "Halloween 24–31 Oct · Christmas 1 Dec–6 Jan".
    - Any other test the whole `:app` run shows failing because its clock sits in 1 Dec – 6 Jan or counts themes: update it to expect Christmas, and name each in the report.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** the entry, the look and the two sets per ruling 2.
- [ ] **Step 3: Images** (`:app`). **New:** none. **Changes** (each only the Christmas row added, or the summary's longer line): `settings_display_preview_dark`, `settings_display_preview_light`, `settings_seasonal_birthday_dark`, `settings_display_next_dark`, `settings_display_next_light`, `settings_display_dates_sheet_dark`, `settings_display_dates_sheet_light`, `settings_seasonal_dark`, `settings_app_dark`, `settings_app_light`. Before the baseline check, edit `$LISTS/expected-baselines.txt` to the names `compareRoborazziDebug` actually showed changed, after looking at each (for example, if the Display row is out of view in `settings_app_*`, drop those two), and say any edit in the report; nothing outside this list may move.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Add Christmas as a seasonal theme from 1 December to 6 January, with pine green and holly red colours whose warnings are amber and weather ice blue, checked by the same contrast tests"
```
- [ ] **Step 5: Report** the measured gap table from `theNormalAndBirthdayWarningGapsStayWhereTheyAre` (Task 12 copies it to the follow-ups), and every colour moved from ruling 2.

---

### Task 2: Which theme shows over New Year; Christmas screenshots; the night clock pinned (spec §2, §4.1, §4.2; F12; Review Focus 2)

**Review:** sonnet. Most tests are **pins** (the machinery exists); they pin Christmas's own edges.

**Files:**
- Test: `app` `display/SeasonalThemesTest.kt`, `display/DisplaySettingsTest.kt`, `display/ui/DisplaySettingsPageTest.kt`, `display/ui/DisplaySettingsScreenshotTest.kt`, `display/ui/SleepLayersScreenshotTest.kt`, `shell/ui/ShellScreenshotTest.kt`; `capability/door` `ui/DoorCardScreenshotTest.kt`. No main source changes unless a test finds a fault (then stop and report it to the controller before fixing).

**Interfaces:**
- Consumes: Task 1's `CHRISTMAS`; the existing `SeasonalThemes`, `showingOn`, `nextTheme`, `dressOn`, `shownRange`, `nextLine`, `DisplaySettings.setSeasonDates`.
- `DisplaySettingsScreenshotTest` (F12): `snap(…, today: LocalDate = TODAY, …)` passes `today` to `DisplaySettingsUi`; `datesSheet(name: String, dark: Boolean, theme: SeasonalTheme = SeasonalTheme.HALLOWEEN, today: LocalDate = TODAY)` previews `theme` when it isn't Halloween and opens `display_dates_${theme.name.lowercase()}`. Existing shots keep their defaults, so their images don't move.

- [ ] **Step 1: The tests.**
  - `SeasonalThemesTest` (fake `now`, as the Halloween ones):
    - `christmasStartsAsDecemberStarts` (**pin**): 2026-11-30 23:59 → null; 2026-12-01 00:00 → `CHRISTMAS`, same instance; 2026-12-12 12:00 → `CHRISTMAS`.
    - `christmasRunsOverNewYear` (**pin**): 2026-12-31 23:59 → `CHRISTMAS`; 2027-01-01 00:00 → `CHRISTMAS`; 2027-01-06 23:59 → `CHRISTMAS`; 2027-01-07 00:00 → null.
    - `nextIsChristmasInNovember` (**pin**): `nextTheme(2026-11-11, defaults) == CHRISTMAS to 2026-12-01`, and `nextLine(CHRISTMAS, 2026-12-01) == "Next: Christmas from 1 December"`.
    - `aBirthdayInDecemberWins` (**pin**): `dressOn(null, null, listOf(sam), CHRISTMAS) == samDress`; with the household clock on 2026-12-12 and Sam's birthday today, `showing` stays `CHRISTMAS` and `birthdayShowing` is true.
    - `previewingChristmasReplacesHalloween` (**pin**): on 2026-11-05, `preview(HALLOWEEN, true)` then `preview(CHRISTMAS, true)` → `showing` and `previewing` `CHRISTMAS`; `preview(HALLOWEEN, false)` leaves it; 2026-11-06 00:00 → both null.
  - `DisplaySettingsTest`:
    - `christmasDatesDefault` (**pin**): fresh → `CHRISTMAS` 1 Dec – 6 Jan.
    - `christmasDatesAreKeptApart` (**pin**): `setSeasonDates(CHRISTMAS, 20 Dec – 2 Jan)` → reread the same; Halloween still 24 Oct – 31 Oct; the file holds `season_christmas_start` = 1220 and `season_christmas_end` = 102.
  - `DisplaySettingsPageTest`:
    - `christmasHasItsRowAndPreview` (**pin**): `display_dates_christmas` reads "Christmas", "1 Dec – 6 Jan", click label "Change the dates"; `display_preview_christmas` reads "Preview Christmas" and "Shows Christmas on the wall and plays its doorbell until midnight.".
    - `christmasDatesOpenOverNewYear` (**pin**): `shownRange(2026-11-11, defaults[CHRISTMAS]) == (2026-12-01 to 2027-01-06)`; `shownRange(2027-01-03, …)` is the same pair; `shownRange(2026-12-12, …)` the same.
    - `pickingAChristmasRangeOverNewYearSavesIt` (**pin**): today 2026-11-11, open Christmas's sheet (December 2026 shown, 1 Dec chosen), tap 20 Dec, page to January 2027, tap 2 Jan, "Save dates" → `onDates(CHRISTMAS, SeasonDates(12-20, 1-2))`.
  - Screenshots (each `_dark` and `_light` unless named):
    - `ShellScreenshotTest`: `home_christmas_dark` / `_light`, `fullHome` with `seasonal = CHRISTMAS`.
    - `DoorCardScreenshotTest`: `door_card_christmas_dark` / `_light` (the `ring` sample).
    - `DisplaySettingsScreenshotTest`: `settings_display_dates_sheet_christmas_dark` / `_light` = `datesSheet(name, dark, theme = CHRISTMAS, today = LocalDate.of(2026, 11, 11))` (ruling 12).
    - `SleepLayersScreenshotTest`: `night_clock_christmas` (dark, `CHRISTMAS`).

  Run `./gradlew :app:testDebugUnitTest --tests '*SeasonalThemes*' --tests '*DisplaySettings*'`. Expected: PASS (pins). A failure is a fault: report it before any fix.
- [ ] **Step 2: Images.** **New:** `home_christmas_dark`, `home_christmas_light` (pine green or cream, red buttons and tab, amber nowhere but warnings, ice-blue weather), `door_card_christmas_dark`, `door_card_christmas_light`, `settings_display_dates_sheet_christmas_dark`, `settings_display_dates_sheet_christmas_light` (1 Dec chosen in December), `night_clock_christmas`. Then `cmp app/src/test/screenshots/night_clock.png app/src/test/screenshots/night_clock_christmas.png` prints nothing (if it differs, stop: the night clock changed). **Changes:** none.
- [ ] **Step 3: The gate, then commit.**
```bash
git add app/src/test capability/door/src/test
git commit -m "Pin Christmas's dates over New Year, its place in the dates sheet and the next-theme line, and add Christmas screenshots of Home, the door card and its dates sheet"
```

---

### Task 3: The music box follows Christmas (spec §3.8, §4.7; ruling 13; F14; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify `core/sound/.../BuiltInSound.kt` (`CHRISTMAS_MUSIC_BOX`'s `theme = SeasonalTheme.CHRISTMAS`).
- Test: `core/sound` `BuiltInSoundTest.kt`, `SoundLibraryTest.kt`, `SoundWordsTest.kt`; `capability/door` `DoorSettingsTest.kt`, `ui/DoorSettingsPageTest.kt`; `capability/party` `ui/SoundButtonsSectionTest.kt`.

**Interfaces:**
- Consumes: Task 1's `CHRISTMAS`; `SoundLibrary.forTheme`, `choices` (which both Settings › Door's list and the sound pads' list read: `DoorSettingsPage.kt:242`, `SoundButtonsSection.kt:77`), `DoorPrefs.doorbellSound(library, showing, birthday)`, `Sound.tabletLine`.

- [ ] **Step 1: The failing tests.**
  - `BuiltInSoundTest.onlyTheOrganHasATheme` becomes `theOrganAndTheMusicBoxHaveTheirThemes`: `ORGAN.theme == HALLOWEEN`, `CHRISTMAS_MUSIC_BOX.theme == CHRISTMAS`, `CLASSIC` and `HAPPY_BIRTHDAY` null.
  - `SoundLibraryTest`:
    - `aChristmasFileBeatsTheMusicBox`: `forTheme(CHRISTMAS).id == "builtin:christmas_music_box"`; after `touch("christmas/Test Bells.ogg")` → `"file:christmas/Test Bells.ogg"`.
    - `themedSoundsStayChoices` (**pin**): `choices.first()` ids contain `builtin:organ` and `builtin:christmas_music_box`.
  - `SoundWordsTest.aChristmasFileSaysSo` (**pin**): a tablet sound with `theme = CHRISTMAS` → `tabletLine == "On this tablet · Christmas"`.
  - `DoorSettingsTest.followTheThemeAtChristmasIsTheMusicBox`: `DoorPrefs().doorbellSound(library, CHRISTMAS, birthday = false).id == "builtin:christmas_music_box"`; `birthday = true` → `builtin:happy_birthday`; `DoorPrefs(doorbell = CHRISTMAS_MUSIC_BOX.id).doorbellSound(library, null, false)` → the music box (**pin**).
  - `DoorSettingsPageTest`:
    - `playChimeAtChristmasPlaysTheMusicBox`: `showHost(CHRISTMAS)`, `door_play_chime` → `chime.plays == [("builtin:christmas_music_box", 1.0f)]`.
    - `theMusicBoxIsStillADoorbellChoice` (**pin**): `door_doorbell_builtin:christmas_music_box_choose` exists and selects.
  - `SoundButtonsSectionTest.theMusicBoxIsAPadChoice` (**pin**): open pad 1's sheet → a radio row "Christmas music box" is there, and choosing it stores `builtin:christmas_music_box`.

  Run `./gradlew :core:sound:testDebugUnitTest :capability:door:testDebugUnitTest :capability:party:testDebugUnitTest`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** the one tag.
- [ ] **Step 3: The gate, then commit.** No screenshot changes.
```bash
git add core/sound capability/door/src/test capability/party/src/test
git commit -m "Play the Christmas music box for a doorbell that follows the theme while Christmas shows, or a sound from the tablet's christmas folder"
```
- [ ] **Step 4:** The report repeats ruling 13's two accepted cases, for Task 12 to record.

---

### Task 4: Snow across the header, lights in the band (spec §3.6, §4.4; rulings 1, 5, 11; F2–F7; Review Focus 1)

**Review:** opus. Text over snow; nothing moves while nobody should see it; a step redraws only the sky's layer; Halloween and the birthday draw exactly as before.

**Files:**
- Modify `core/ui/.../SkyBand.kt` (`headerArea`, `inHomeHeader`, `hangingY`, `hangingPath`, `fitCount`), `BirthdayArt.kt` (`stringY`, the string path in `buntingIn` and `flagCount` replaced by the shared ones; nothing else), `ChristmasLook.kt` (`sky = { drifting, m -> ChristmasSky(drifting, m) }`; `object ChristmasInks` begins with the snow and bulb inks).
- Create `core/ui/.../ChristmasArt.kt` (snow, lights, `ChristmasSky`, `ChristmasDimens`).
- Test: new `core/ui/src/test/.../ChristmasSkyTest.kt`; `ContrastTest.kt`; `BirthdaySkyTest.kt` (`flagCount` calls become `fitCount` with the flag pitch and 3; otherwise **pins**); `app` `shell/ui/CulveryShellSkyTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `HomeSkyBand`, `LocalHomeSkyBand`, `skyBand`, `SkyFrames`, `SKY_FRAME_MS`, `BirthdayDimens.buntingTop`/`buntingSag`/`flagPitch`.
- Produces:
  - `fun headerArea(measured: HomeSkyBand?, size: Size): Rect`; `internal fun Modifier.inHomeHeader(measured: HomeSkyBand?): Modifier` (sizes and places a node as `headerArea`, as `inSkyBand` does for the band).
  - `internal fun hangingY(band: Rect, x: Float, top: Dp, sag: Dp, density: Density): Float` (the parabola `stringY` draws today); `internal fun hangingPath(band: Rect, top: Dp, sag: Dp, density: Density): Path` (the quadratic `buntingIn` builds today).
  - `internal fun fitCount(widthPx: Float, pitchPx: Float, min: Int): Int`: `floor(widthPx / pitchPx)`, or 0 when that's under `min`. The bunting calls it with `flagPitch` and 3, so `fitCount(280f, …)` = 10, `(83f, …)` = 0, `(84f, …)` = 3 at the test's density, as `flagCount` gave.
  - `object ChristmasDimens { val flakeRadiusMin = 1.5.dp; val flakeRadiusMax = 3.dp; val snowSway = 10.dp; val bulbPitch = 26.dp; val bulbRadius = 4.dp; val wireStroke = 1.5.dp }`; `internal const val SNOW_FLAKES = 18`; `internal const val SNOW_FALL_MS = 24_000L`; `internal const val TWINKLE_MS = 4_000L`; `internal const val BULBS_MIN = 3`.
  - `class Flake(val centre: Offset, val radius: Float)`; `fun snowFlakes(elapsedMillis: Long, area: Rect, density: Density): List<Flake>`. For flake `i` of `SNOW_FLAKES`, with `phase_i` its own fixed fraction in [0, 1) and `t = (elapsedMillis % SNOW_FALL_MS).toFloat() / SNOW_FALL_MS`:
    - `y = area.top + ((phase_i + t) mod 1) × area.height`: every flake falls at the same speed, the header's height every `SNOW_FALL_MS`, wrapping from the bottom to the top at its own time `(1 − phase_i) × SNOW_FALL_MS`;
    - `x = column_i + snowSway × sin(2π × ((phase_i + t) mod 1))`, the sway's period also `SNOW_FALL_MS`, `column_i` spreading the flakes across the width and kept at least `snowSway` inside `area`;
    - radius between `flakeRadiusMin` and `flakeRadiusMax`, fixed per flake. The phases, columns and radii are the implementer's.
  - `fun bulbCentres(band: Rect, density: Density): List<Offset>`: `fitCount(band.width, bulbPitch, BULBS_MIN)` bulbs, centred as the flags are, each `bulbRadius` below the wire at its x.
  - `fun bulbAlpha(elapsedMillis: Long, index: Int): Float`: `t = (elapsedMillis % TWINKLE_MS).toFloat() / TWINKLE_MS`; between `ChristmasInks.BULB_DIM` (0.5) and 1 on a cosine of the phase `(t − g / 4) mod 1`, where `g = index % 4`, so each group runs a quarter period behind the one before; `bulbAlpha(0, 0) == 1f`.
  - `object ChristmasInks { val NIGHT_SNOW = Color(0xFF2C4034); val DAY_SNOW = Color(0xFFD3DCD4); fun snow(c: HhColors): Color; fun wire(c: HhColors): Color = c.mute; val NIGHT_BULBS = listOf(Color(0xFFEE6A6E), Color(0xFFF2C14E), Color(0xFF5FBF7A), Color(0xFFF4F1E8)); val DAY_BULBS = listOf(Color(0xFFB3262C), Color(0xFFB8860B), Color(0xFF2E7D46), Color(0xFFFFFFFF)); fun bulbs(c: HhColors): List<Color>; fun bulbOutline(c: HhColors): Color?; const val BULB_DIM = 0.5f }` (red, gold, green, white; by day every bulb gets a `c.mute` outline so the white one shows on cream; by night none).
  - `@Composable fun ChristmasSky(drifting: Boolean, modifier: Modifier = Modifier, elapsed: MutableLongState = remember { mutableLongStateOf(0L) })`: as `HalloweenSky`'s shape: `SkyFrames(drifting, elapsed)`, a `Box(modifier)` holding one `Spacer` with `.inHomeHeader(measured).testTag("christmas_sky").clearAndSetSemantics {}.graphicsLayer { clip = true }.drawWithCache { … }`; the cache builds the band (`skyBand(measured, size, density)`), the wire (`hangingPath(band, buntingTop, buntingSag, …)`) and the bulb centres once per size; `onDrawBehind` reads `elapsed` and draws the flakes in `snow`, then the wire and bulbs, each bulb at `bulbAlpha` while `drifting` and at full when still.

- [ ] **Step 1: The failing tests** (stubs: `snowFlakes` a fixed list, `bulbAlpha` 1f, `bulbCentres` empty, `headerArea` the whole size).
  - `ChristmasSkyTest` (`mainClock.autoAdvance = false` for the frame tests):
    - `snowFallsTheHeaderEveryLap`: for a 1000 × 300 px area, `snowFlakes(SNOW_FALL_MS, …)` equals `snowFlakes(0, …)`; at `SNOW_FALL_MS / 4` each flake's y has moved down by a quarter of the height, mod the height (within 0.5 px).
    - `aFlakeWrapsFromTheBottomToTheTop`: pick one flake, work out its wrap time `w` from its y at 0 (`w = (area.bottom − y0) / area.height × SNOW_FALL_MS`); at `w − 10` its y is within 1.5 px of `area.bottom`, at `w + 10` within 1.5 px of `area.top`.
    - `theSnowStaysInTheHeader`: every 500 ms over a fall, every centre is inside `area`.
    - `theSnowRepeatsOverLongRuns` (F2): for `t` in {0, 7_000, 23_999}, `snowFlakes(40.days.inWholeMilliseconds + t, …)` equals `snowFlakes(t, …)` and `bulbAlpha(40.days.inWholeMilliseconds + t, i) == bulbAlpha(t, i)` for i in 0..3.
    - `theLightsHangInTheBand`: `bulbCentres` inside the band; the first and last at least half a pitch from its ends; the wire's ends at `band.top + buntingTop`.
    - `aNarrowBandHasNoLights`: a band 2.9 pitches wide → no centres; 3 pitches → 3.
    - `theBulbsTwinkleOutOfStep`: for each index, `bulbAlpha` sampled every 50 ms over a period stays in [0.5, 1] and comes within 0.05 of both ends; at `TWINKLE_MS / 4`, `bulbAlpha(…, 0)`, `(…, 1)`, `(…, 2)`, `(…, 3)` aren't all equal, and `bulbAlpha(TWINKLE_MS / 4, 1) == bulbAlpha(0, 0)` (one group a quarter behind the next); `bulbAlpha(TWINKLE_MS, i) == bulbAlpha(0, i)`.
    - `theSkyDriftsWhileAsked` and `stillMeansNoFrames` (as `HalloweenSkyTest`'s, on `ChristmasSky`).
    - `aStillSkyDrawsTheSnowAndTheLightsAtFull` (`@GraphicsMode(NATIVE)`): `drifting = false`, `elapsed` set to `TWINKLE_MS / 2` → the pixel at each bulb's centre is exactly its `bulbs(ChristmasNight)` colour (at full, not dimmed); for the largest flake whose box is clear of every bulb and of the wire, some pixel within its radius equals `snow(ChristmasNight)`.
    - `theSkyIsSilent`: no node under `christmas_sky` has text, a description or an action.
  - `ContrastTest` over `christmas`:
    - `textReadsOverTheSnow` (spec §4.4): on `snow(t)`, `t.accent` at `NON_TEXT` (the clock, large text), `t.ink` and `t.mute` at `TEXT` (the date and the header items' words), `t.weather` and `t.rain` at `NON_TEXT` (the header items' icons). Report the numbers.
    - `theSnowAndLightsStandOffTheBackground`: `snow(t)` and `wire(t)` on `t.bg` at `SKY_MIN`; each bulb, at full and at `BULB_DIM` over `t.bg`, at `SKY_MIN`, except where `bulbOutline(t)` is set, where the outline on `t.bg` is checked instead.
  - `CulveryShellSkyTest`:
    - `theSnowFallsBehindTheWholeHeader` (F4): under `CHRISTMAS` on Home with `fullHome`, the header is `Rect(clock.left, clock.top, items.right, max(date.bottom, items.bottom))` from `home_clock`, `home_date` and `home_header_items`' bounds (as `skyStaysInTheBand` builds its band); `christmas_sky`'s bounds equal it within 1 px; the header still reads clock, date, items in order.
    - `theLightsKeepToTheOpenBand` (F4), by night and by day: the band as `skyStaysInTheBand` builds it; scanning the header's rows and columns of a snapshot, every pixel exactly one of `bulbs(t)` and outside the clock, date and items bounds lies inside `band.inflate(1f)`; at least one such pixel is drawn.
    - `noChristmasSkyOffHome`: tab `door` → no `christmas_sky`.
    - `theSkyStaysInTheOpenBandAtNight` / `ByDay`, `theBirthdaySkyIsBehindHome`, `theBatsStillFlyForHalloween` (**pins**, unchanged).
  - `BirthdaySkyTest` (**pins**): every test passes after `fitCount`, `hangingY` and `hangingPath` replace the bunting's own.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*Sky*'`. Expected: FAIL on assertions, the pins aside. If `textReadsOverTheSnow` fails on the starting values, darken the night snow (lighten the day snow) by the smallest step that passes; never lighten text.
- [ ] **Step 2: Implement** per the Interfaces and ruling 1. No change to `CulveryShell`, `HalloweenArt.kt` or `HomeScreen.kt`.
- [ ] **Step 3: Images.** **Changes:** `home_christmas_dark`, `home_christmas_light` (drawn still at elapsed 0: faint flakes across the header and behind the clock, a sagging string of red, gold, green and white bulbs in the open band, the day bulbs outlined). `home_halloween_*`, `home_birthday_*` and every normal `home_*` unchanged; if a birthday image moved, the shared curve or count changed the bunting: stop.
- [ ] **Step 4: A final look** at `home_christmas_dark` and `_light` as a whole (clock, date, weather, cards, snow, lights): anything that competes with text goes in the report.
- [ ] **Step 5: The gate, then commit.**
```bash
git add core/ui app/src/test
git commit -m "Let snow fall across Home's header behind the clock and hang twinkling lights in the open band while Christmas shows, still while the screen is dark, anything is open over Home or animations are off"
```

---

### Task 5: Mountains of Christmas and the Christmas clock face (spec §3.5, §4.5; ruling 9; F9, F10; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify `README.md`'s licence paragraph (line ~222), after Cherry Bomb One: "Mountains of Christmas: Apache License 2.0 (`core/ui/licenses/Apache-MountainsOfChristmas.txt`), for the Christmas clock; from google/fonts at commit 85402ba9968a3ac7e32e4a5be29abc1298f52a3a, `MountainsofChristmas-Bold.ttf` sha256 {hash}."
- Create `core/ui/src/main/res/font/mountains_of_christmas_bold.ttf`, `core/ui/licenses/Apache-MountainsOfChristmas.txt`.
- Modify `core/ui/.../ClockFace.kt`, `ChristmasLook.kt` (`clockFace = MountainsOfChristmasClock`).
- Test: `core/ui` `ThemeTest.bundledFontsAreRealFiles`, `ClockFaceTest.kt`; `app` `shell/ui/ThemedClockTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `SlotClock`, `HhType.clock`, `HomeHeader`'s existing `Culvery.look?.clockFace` path (no `:app` main change).
- Produces: `val MountainsOfChristmas: FontFamily = FontFamily(Font(R.font.mountains_of_christmas_bold, FontWeight.Bold))`; `val MountainsOfChristmasClock: TextStyle = HhType.clock.copy(fontFamily = MountainsOfChristmas, fontWeight = FontWeight.Bold, fontFeatureSettings = null, letterSpacing = 0.sp)`, in `accent` through `SlotClock` as the other faces.

- [ ] **Step 1: The font** (before any test, so `R.font.mountains_of_christmas_bold` exists). Download into a new empty `$LISTS/mountains/`, from `https://raw.githubusercontent.com/google/fonts/85402ba9968a3ac7e32e4a5be29abc1298f52a3a/apache/mountainsofchristmas/`: `METADATA.pb`, `MountainsofChristmas-Bold.ttf`, `LICENSE.txt`. Check, every line must hold, else stop and tell the controller:
  - `git hash-object "$LISTS/mountains/MountainsofChristmas-Bold.ttf"` prints `81efb57588c4529190d8292bc457a24ba6fa63af` (the blob at the pinned commit in GitHub's tree API);
  - `grep -c '^license: "APACHE2"' METADATA.pb` prints 1; `grep -c 'filename: "MountainsofChristmas-Bold.ttf"' METADATA.pb` prints 1;
  - `grep -c "Apache License" LICENSE.txt` and `grep -c "Version 2.0" LICENSE.txt` each print at least 1;
  - `head -c 4 MountainsofChristmas-Bold.ttf | xxd -p` prints `00010000`; the file is 123 172 bytes.

  Then record `sha256sum` of the TTF, and copy it to `core/ui/src/main/res/font/mountains_of_christmas_bold.ttf` and `LICENSE.txt` to `core/ui/licenses/Apache-MountainsOfChristmas.txt`. Nothing in `$LISTS/mountains/` is run.
- [ ] **Step 2: The failing tests** (stub: `MountainsOfChristmasClock = HhType.clock`).
  - `ThemeTest.bundledFontsAreRealFiles` adds: `R.font.mountains_of_christmas_bold` is over 10 000 bytes and under 500 000.
  - `ClockFaceTest.everyDigitGetsTheSameSlot` adds `MountainsOfChristmasClock`: "11:11" and "00:00" equal widths.
  - `ThemedClockTest.looks()` adds `Look("christmas", SeasonalTheme.CHRISTMAS, null, MountainsOfChristmas, ChristmasNight)`, so its **seven** cases run for Christmas: `theClockIsInTheLooksFace`, `theClockKeepsItsWidthEveryMinute` (11:11, 00:00, 08:47, 23:58), `theClockKeepsItsHeightInEitherFace`, `theClockDrawsNothingIntoTheDate` (at 23:58 and 22:22), `theHeaderItemsDontMoveWhenTheFaceChanges`, `theDateStaysInDmSans` (**pin**), `theClockIsTheLooksAccent`.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*ThemedClock*' --tests '*ShellLayout*'`. Expected: FAIL on assertions, the pin aside; `ShellLayoutTest` (**pins**: the normal clock) passes.
- [ ] **Step 3: Implement.** If `theClockDrawsNothingIntoTheDate` fails, shrink `fontSize` in `MountainsOfChristmasClock` by whole sp until it passes, and give the size in the report.
- [ ] **Step 4: Images.** **Changes:** `home_christmas_dark`, `home_christmas_light` (the clock in Mountains of Christmas in red over the snow; look at the colon at full size and say in the report whether it reads; the date and weather unchanged). `home_halloween_*` and `home_birthday_*` unchanged.
- [ ] **Step 5: The gate, then commit** (`git status` first: only the files above, nothing from `$LISTS`).
```bash
git add core/ui README.md app/src/test
git commit -m "Set Home's clock in Mountains of Christmas while Christmas shows, each digit in a fixed slot so the header never moves"
```

---

### Task 6: The house, snowed in (spec §3.4, §4.3; ruling 7; P1, F8)

**Review:** sonnet.

**Files:**
- Create `core/ui/.../MarkPaths.kt` (moved from `app/.../shell/ui/CulveryMark.kt`).
- Modify `core/ui/.../ChristmasArt.kt` (`ChristmasMark`), `app/.../shell/ui/CulveryMark.kt` (reads `MARK_HOUSE`, `MARK_DOVE`, `MarkViewport` from `:core:ui`), `core/ui/.../ChristmasLook.kt` (`mark = { w -> ChristmasMark(w) }`; the mark's inks).
- Test: new `core/ui/src/test/.../ChristmasArtTest.kt`; `ContrastTest.kt`; `app` `shell/ui/NavRailTest.kt`, `CulveryMarkTest.kt` (imports only); `ShellScreenshotTest`.

**Interfaces:**
- Produces:
  - `const val MARK_HOUSE: String`, `const val MARK_DOVE: String`, `object MarkViewport { const val LEFT = 26f; const val TOP = 29f; const val WIDTH = 54f; const val HEIGHT = 49f }` (public, `uk.co.siland.culvery.core.ui`).
  - `ChristmasInks` gains `val MARK_SNOW_NIGHT = Color(0xFFFFFFFF)`, `val MARK_SNOW_DAY = Color(0xFFC9D4CB)`, `val WREATH_NIGHT = Color(0xFF3E8E55)`, `val WREATH_DAY = Color(0xFF5FBF7A)`, `fun markSnow(c: HhColors): Color`, `fun wreath(c: HhColors): Color`, `fun robin(c: HhColors): Color = c.accent`. The wreath is chosen against the house it sits on (`ink`: near-white at night, near-black by day).
  - `@Composable fun ChristmasMark(width: Dp, modifier: Modifier = Modifier)`: an `ImageVector` in `MarkViewport` (translated by `-LEFT, -TOP` as `CulveryMark`), drawn in this order: the house in `ink`; the snow cap in `markSnow`, **over** the roof's top edge, inside the viewport (P1), with a thin `bg` stroke round the cap so a line of `bg` separates it from the roof; the dove in `robin`; a wreath ring in `wreath` with a small bow in `robin` on the house front above the door (the door arch is x 45.06–57.94, top about 62.5 in the 108-unit square). Tag `christmas_mark`, `contentDescription = null`, `hideFromAccessibility()`. Its paths are the implementer's, the cap's shape from `mark-v2.html`.

- [ ] **Step 1: The failing tests** (stub: `ChristmasMark` draws the house in `ink` and the dove in `weather` only, as `CulveryMark` does, so the cap, robin and wreath assertions fail).
  - `ChristmasArtTest` (`@GraphicsMode(NATIVE)`, as `HalloweenArtTest`: `CulveryTheme(dark, seasonal = CHRISTMAS) { Box(Modifier.background(Culvery.colors.bg)) { ChristmasMark(216.dp) } }`; a pixel "is" a colour when each channel is within 8 of it and no other named colour is nearer):
    - `theChristmasMarkIsAPictureOnly`: no text, no content description, hidden from accessibility; some pixel is `ChristmasNight.ink`, some `ChristmasInks.markSnow(ChristmasNight)`, some `ChristmasInks.robin(ChristmasNight)`, some `ChristmasInks.wreath(ChristmasNight)`.
    - `theSnowCapSitsOnTheRoof` (P1): down the pixel column through the roof's apex (x 51.5 of 108, mapped into the 216 dp image), the first run that isn't `bg` is the cap's colour, then a run of `bg` (the stroke) at least one pixel long, then `ink` (the roof), and the cap's top is at or below the image's top edge (inside the viewport); by night and by day.
  - `ContrastTest.theChristmasMarkStandsOffTheBackground` over `christmas`: `markSnow(t)` on `t.bg` at `SKY_MIN`; `wreath(t)` on `t.ink` at `NON_TEXT`; `robin(t)` on `t.bg` at `NON_TEXT`.
  - `NavRailTest`:
    - `theChristmasMarkTakesTheMarksPlace`: under `CHRISTMAS`, `christmas_mark` shows inside `culvery_mark`, whose bounds equal its bounds with `seasonal = null`.
    - `thePumpkinTakesTheMarksPlace`, `theMarkIsBackWithoutATheme` (**pins**, unchanged).
  - `CulveryMarkTest.theMarkDrawsTheLauncherIconsTwoPaths` (**pin**) passes with the moved constants.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*NavRail*' --tests '*CulveryMark*'`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: Images.** **Changes:** `home_christmas_dark`, `home_christmas_light` (the snowed-in house in the rail; look at it at full size: the cap must read on the house at night, and on cream by day). Every other `home_*` image unchanged.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Put the Culvery house in the rail snowed in, with a wreath on the door and the dove in robin red, while Christmas shows"
```

---

### Task 7: Holly on the Today card (spec §3.7, §4.6; ruling 8)

**Review:** sonnet.

**Files:**
- Modify `core/ui/.../ChristmasArt.kt` (`drawHolly`; `ChristmasDimens.hollySpan`), `ChristmasLook.kt` (`cardCorner = { c -> drawHolly(ChristmasInks.hollyLeaf(c), ChristmasInks.hollyBerry(c), ChristmasDimens.hollySpan) }`; the holly inks). No change to `TodayCard.kt` (it already draws `Culvery.look?.cardCorner`).
- Test: `ContrastTest.kt`; `capability/calendar` `ui/CardsTest.kt`, `ui/CardScreenshotTest.kt`; `app` `ShellScreenshotTest`.

**Interfaces:**
- Produces:
  - `ChristmasDimens.hollySpan = 40.dp`: the holly's width from the card's top-right corner; its height is `hollySpan * 25 / 40`. The one number the user tweaks on the tablet.
  - `fun DrawScope.drawHolly(leaf: Color, berry: Color, span: Dp)`: two leaves and three berries within `span` × `span * 25 / 40` of the top-right corner. Its paths are the implementer's.
  - `ChristmasInks` gains `val HOLLY_LEAF_NIGHT = Color(0xFF24382B)`, `val HOLLY_BERRY_NIGHT = Color(0xFF4A2426)`, `val HOLLY_LEAF_DAY = Color(0xFFDCE5DC)`, `val HOLLY_BERRY_DAY = Color(0xFFF0D2D2)`, `fun hollyLeaf(c: HhColors): Color`, `fun hollyBerry(c: HhColors): Color`.

- [ ] **Step 1: The failing tests.**
  - `ContrastTest.theHollyReadsUnderCardText` over `christmas`: `t.ink` and `t.mute` on `hollyLeaf(t)` and on `hollyBerry(t)` at `TEXT`; each on `t.surf` at `SKY_MIN`.
  - `CardsTest` (`TodayCard` under `CulveryTheme(dark = true, seasonal = …)`, `@GraphicsMode(NATIVE)`, as the cobweb's):
    - `theHollySitsInTheTopRightCorner`: under `CHRISTMAS`, the `hollySpan` × `hollySpan * 25 / 40` box at the card's top-right holds pixels that aren't `ChristmasNight.surf` outside the buttons' bounds, at least one exactly `hollyBerry(ChristmasNight)`; the card's bottom-left box of that size has none.
    - `theButtonsStayWhereTheyAreUnderTheHolly`: `today_add` and "See the week" have the same bounds with and without `CHRISTMAS`, and a tap on each still calls `onAdd` / opens the tab.
    - `theWebSitsInTheTopRightCorner`, `noWebWithoutATheme` (**pins**, unchanged).
  - Screenshots: `today_christmas_dark` / `_light` (the `today` sample, `CHRISTMAS`).

  Run `./gradlew :core:ui:testDebugUnitTest :capability:calendar:testDebugUnitTest`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: Images.** **New:** `today_christmas_dark`, `today_christmas_light` (a faint sprig in the corner behind the header; the "+" and "See the week" untouched; say in the report how much of it the "+" covers). **Changes:** `home_christmas_dark`, `home_christmas_light`. `today_halloween_*` unchanged.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui capability/calendar/src/test app/src/test
git commit -m "Put a sprig of holly on the Today card while Christmas shows"
```

---

### Task 8: The 29 February fault (spec §4.8; ruling 10; F13; Review Focus 3)

**Review:** sonnet.

**Files:**
- Modify `core/ui/.../Seasonal.kt` (`firstDayIn`), `app/.../display/SeasonalThemes.kt` (`nextTheme`), `display/DisplayWords.kt` (the two lines), `display/ui/DisplaySettingsPage.kt` (`shownRange`; `DatesSheet` shows the line and holds Save).
- Test: `core/ui` `SeasonalTest.kt`; `app` `display/SeasonalThemesTest.kt`, `display/DisplaySettingsTest.kt`, `display/ui/DisplaySettingsPageTest.kt`.

**Interfaces:**
- Produces:
  - `fun MonthDay.firstDayIn(year: Int): LocalDate`: 29 February in a common year → 1 March of that year; else `atYear(year)`.
  - `nextTheme` and `shownRange` take a theme's start as `start.firstDayIn(year)`, and pass over a year in which the range holds no day (the candidate start isn't `in` the dates: only 29 Feb – 29 Feb in a common year), trying the next year, at most four.
  - `internal fun leapDayLine(first: Boolean, shown: LocalDate): String`: "The saved first day is 29 February, which in {shown.year} is 1 March." / "The saved last day is 29 February, which in {shown.year} is 28 February.".
  - `DatesSheet`: from the range it opened on (`shown`, computed once with `remember`, never from later picks): when `MonthDay.from(shown.first) != saved.start` the first-day line shows, else when `MonthDay.from(shown.second) != saved.end` the last-day line; one `HhMessageLine` tagged `display_dates_leap` under the grid. When both ends are 29 February, `shownRange` opens on a leap year, so nothing moved and no line shows. `display_dates_save` is enabled only when `range != null && (tapped || nothing moved)`, where `tapped` turns true on the first `pick`.

- [ ] **Step 1: The failing tests** (stub: `firstDayIn` returns `atYear(year)`).
  - `SeasonalTest`:
    - `firstDayInPutsALeapDayOn1March`: `MonthDay.of(2, 29).firstDayIn(2027) == 2027-03-01`; `firstDayIn(2028) == 2028-02-29`; `MonthDay.of(12, 1).firstDayIn(2026) == 2026-12-01`.
    - `aLeapDayStartsOn1MarchInACommonYear` (**pin**): `SeasonDates(2-29, 3-10)` contains 2027-02-28 false, 2027-03-01 true, 2028-02-28 false, 2028-02-29 true.
  - `SeasonalThemesTest`:
    - `nextSaysTheDayALeapDayStartReallyStarts`: Halloween 29 Feb – 10 Mar, Christmas default: `nextTheme(2027-01-10, …) == HALLOWEEN to 2027-03-01`; `nextTheme(2028-01-10, …) == HALLOWEEN to 2028-02-29`; `nextLine(HALLOWEEN, 2027-03-01) == "Next: Halloween from 1 March"`.
    - `aLeapDayOnlyRangeWaitsForALeapYear`: Halloween 29 Feb – 29 Feb, Christmas 30 Dec – 31 Dec: `nextTheme(2027-01-10, …) == HALLOWEEN to 2028-02-29`.
  - `DisplaySettingsTest.aStoredLeapDayStaysALeapDay` (**pin**): `setSeasonDates(HALLOWEEN, 29 Feb – 10 Mar)` → the file holds `season_halloween_start` = 229; reread → `MonthDay.of(2, 29)`.
  - `DisplaySettingsPageTest`:
    - `shownRangeOfALeapDay`: `shownRange(2027-01-10, 2-29..3-10) == (2027-03-01 to 2027-03-10)`; `shownRange(2028-01-10, …) == (2028-02-29 to 2028-03-10)`; `shownRange(2027-01-10, 2-20..2-29) == (2027-02-20 to 2027-02-28)`; `shownRange(2027-01-10, 2-29..2-29) == (2028-02-29 to 2028-02-29)`.
    - `aSavedLeapDayIsntRewrittenByOpeningTheSheet`: today 2027-01-10, Halloween saved 29 Feb – 10 Mar, open the sheet → 1 Mar chosen, `display_dates_leap` reads "The saved first day is 29 February, which in 2027 is 1 March.", `display_dates_save` disabled; a click on it saves nothing; Cancel → `dated` empty. Reopen, tap 2 Mar and 9 Mar → Save enabled; Save → `onDates(HALLOWEEN, SeasonDates(3-2, 3-9))`.
    - `aSavedLeapDayEndSaysSo`: today 2027-01-10, 20 Feb – 29 Feb saved → the line reads "The saved last day is 29 February, which in 2027 is 28 February."; Save disabled.
    - `aLeapDayOnlyRangeOpensOnALeapYear`: today 2027-01-10, 29 Feb – 29 Feb saved → February 2028 shown with 29 Feb chosen, no `display_dates_leap`, Save enabled.
    - `the28thOfFebruaryIsOrdinary` (**pin**): today 2027-01-10, 28 Feb – 10 Mar saved → no `display_dates_leap`; Save enabled.
    - `inALeapYearTheSheetIsAsBefore` (**pin**): today 2028-01-10, 29 Feb – 10 Mar → no `display_dates_leap`; Save enabled.
    - `anOrdinaryRangeSavesAsBefore` (**pin**): `pickingTwoDaysSavesThem` passes unchanged, and opening Halloween's default dates shows no line and Save enabled.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*SeasonalThemes*' --tests '*DisplaySettings*'`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** per ruling 10 and the Copy.
- [ ] **Step 3: Images.** `compareRoborazziDebug` on `:app`: **New:** none. **Changes:** none (the screenshot tests' dates hold no 29 February).
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Keep a stored 29 February as it is: in other years the theme starts on 1 March, the next-theme line says so, and the dates sheet explains it and won't save until a day is changed"
```

---

### Task 9: Simplify and whole-branch review

- [ ] **Step 1:** Run the `simplify` skill over `git diff main...HEAD`. Look hardest at: `ChristmasSky` against `HalloweenSky` and `BirthdaySky` (a shared shape if two read alike, without moving their pixels); `ChristmasInks`' day/night pairs; the dates sheet's Save rule (one condition).
- [ ] **Step 2:** For each change, the module's tests, then the gate. Commit when anything changed: `git commit -am "Simplify the Christmas theme code"`.
- [ ] **Step 3:** An opus reviewer reads `git diff main...HEAD` against the spec and this plan, first for: text over the snow and the moved colours; the hue gaps; Halloween's and the birthday's baselines untouched; the sky's stopping; the font's licence, blob and hash; the 29 February rule; no theme named outside the look files. One fix wave, each fix with its failing test first, then the gate.

---

### Task 10: The emulator walk-through on `culvery-5` (spec §7)

**Rules:** only `culvery-5` with a debug build and `install -r`; Claude enters the debug sample PINs; faults are collected and put to the user before any fix; no house device touched. Themes are on App settings › Seasonal looks.

- [ ] **Step 1: Walk** (one line each in the record).
  1. Preview Christmas: by night and by day (the status bar's Day/Night preview); every tab, a sheet, the PIN pad, a toast, Settings and the door card in Christmas's colours; a warning reads amber, not red; the weather ice blue.
  2. The snow behind the clock and the date, the lights twinkling, the clock (the header still across a minute's change, the colon reads), the mark, the holly.
  3. Preview off: all goes; on again, then the clock past midnight (`DebugClockReceiver`, as in Plan 14's walk-through): it ends and the switch reads off.
  4. Inside the dates: the clock to 12 December, Christmas shows with no preview; 31 Dec 23:59 → 1 Jan 00:00 stays Christmas; 6 Jan 23:59 → 7 Jan 00:00 ends it with no restart.
  5. Outside both themes' dates (for example 11 November): "Next: Christmas from 1 December".
  6. Christmas's dates sheet: the range over New Year shown right (1 Dec in December, 6 Jan in January); change to 20 Dec – 2 Jan and back with "Put back 1 Dec – 6 Jan".
  7. 29 February (Task 8): save Halloween as 29 Feb – 10 Mar in a common year's clock; reopen: 1 Mar with the line, Save off; "Next:" says 1 March; put the dates back.
  8. Halloween unchanged (preview it); a birthday in December wins (the birthday preview on 12 December).
  9. The snow and lights stop with "Remove animations" on, under Settings and the PIN pad, and when dark (`dumpsys gfxinfo` shows no new frames).
  10. Settings › Door's Play chime with *Follow the theme* plays the music box during Christmas, the classic chime outside it; the music box is in the sound pads' list (Settings › Modes).
- [ ] **Step 2:** Say the design cuts again (provisional, for the end-of-v1 review): the night red at #EE6A6E and the day ink at #0B130F (or as moved); the periwinkle rain; the Clash tag and "busy" line in ice blue under Christmas (ruling 4); the snow's faintness, count, size and speed; the bulbs' spacing, size, colours and twinkle, the day bulbs' outline; the holly's size and look, the "+" not moved; the mark's cap over the roof, wreath and robin; the clock face's size if Task 5 shrank it.
- [ ] **Step 3: The fault loop**; put back every setting changed; `DebugClockReceiver --el minutes 0`; `emu kill`.
- [ ] **Step 4: Record** under "Plan 21 (Christmas): emulator walk-through (date)" in `docs/superpowers/plans/2026-09-23-plan1-followups.md`, no names. Commit only that file: `git commit -m "Record Plan 21's emulator walk-through" docs/superpowers/plans/2026-09-23-plan1-followups.md`.

---

### Task 11: The release on the tablet, frame cost, the holly's size (spec §4.4, §7)

**Controller and user only. Needs the SM-T510 on the cable.** A signed release with `install -r`, never uninstall, never wipe; save and restore every setting changed; no Hue, Sonos or plug change.

- [ ] **Step 1: The release.** The gate on the branch, then `./gradlew :app:assembleRelease`; `grep -c DebugClock app/build/outputs/mapping/release/mapping.txt` prints 0; copy the mapping to `culvery-notes/mappings/mapping-<short sha>.txt`.
- [ ] **Step 2: Install.** `adb -s <tablet serial> install -r app/build/outputs/apk/release/app-release.apk`. Never uninstall.
- [ ] **Step 3: With the user,** Preview Christmas on: the wall from across the room by day and at night, the snow behind the clock included; the night clock unchanged (the night hours, or the user's evening); Play chime with *Follow the theme* plays the music box.
- [ ] **Step 4: The frame cost** (Plan 14's method): awake on Home, `adb -s <tablet serial> shell dumpsys gfxinfo uk.co.siland.culvery reset`, 60 s, then `adb -s <tablet serial> shell dumpsys gfxinfo uk.co.siland.culvery`. **Pass: 90th percentile ≤ 12 ms and janky frames < 5%** (Halloween after its fix: 11 ms, 99th 16 ms, 1.5%). Then 30 s with the display off: no new frames. On a miss, put it to the user; with their go, halve `SNOW_FLAKES` first and measure again; drawing the flakes from one cached picture comes second. Each change is its own commit with the gate, and a new release installed with `-r`.
- [ ] **Step 5: The holly's size,** to the user's taste: change only `ChristmasDimens.hollySpan`, re-record `today_christmas_*` and `home_christmas_*` (`./gradlew :capability:calendar:recordRoborazziDebug :app:recordRoborazziDebug`, then the baseline check with those four names), the gate, commit `git commit -m "Size the Christmas holly as chosen on the tablet"`, install the new release with `-r`. Any other look change the user asks for is collected, not made here.
- [ ] **Step 6:** Preview off; `HeatLog`'s lines no warmer than a normal evening (`adb -s <tablet serial> logcat -d -s HeatLog`).

---

### Task 12: The follow-ups

- [ ] **Step 1:** In `docs/superpowers/plans/2026-09-23-plan1-followups.md`, no names:
  - take "8. A Christmas seasonal theme." off "Open items, numbered" (the other numbers stay as they are; numbers never change);
  - add "## Plan 21 (Christmas): on the tablet (date)": what was checked, the gfxinfo numbers (frames, 50th/90th/99th percentile, janky %, asleep), the holly's size chosen, the mapping file's name;
  - add "## From Plan 21":
    - **for the end-of-v1 design review (P4):** warnings against the weather are under 35° apart in the normal night set (34.2°), the birthday nights that keep the normal warning colour (34.2°) and Sam's pink birthday (22.4° night, 26.8° day), with Task 1's full table; left as they are on the user's ruling;
    - the Clash tag and "busy" line following the weather colour, ice blue under Christmas (ruling 4), for the same review;
    - accepted (ruling 13): a ring right after a cold start, before the clock is read, plays the classic chime even in December; edited Christmas dates may show the defaults for a moment after a restart;
    - any fault collected and not fixed;
  - add "## Plan 21 not in this build, on purpose": corner art on cards other than Today (a Plan 14 leftover); a real sleigh-bell recording (item 9); sound pads on Sonos (item 7); birthdays overriding a single chosen doorbell sound (item 10).
- [ ] **Step 2:** Commit only that file: `git commit -m "Follow-ups: item 8 done, Plan 21 on the tablet" docs/superpowers/plans/2026-09-23-plan1-followups.md`.
- [ ] **Step 3:** Hand over with `superpowers:finishing-a-development-branch`: merge to `main` and push only on the user's say, after a check for personal data in `git diff main...HEAD`.

---

## Spec coverage (21 → tasks)

| Spec | Task |
|---|---|
| §1 intent, goals, non-goals, anti-goals | 1, 2, 4, 11 |
| §2 rulings carried (dates only, tablet only, night clock, birthday wins) | 1, 2, 10 |
| §3.1 dates | 1, 2 |
| §3.2–3.3 colours by night and day, amber warnings, ice-blue weather | 1 |
| §3.6 / §4.4 snow, lights, seam, motion, cost | 4, 11 |
| §3.5 / §4.5 the clock face, font, licence | 5 |
| §3.4 / §4.3 the mark, paths moved | 6 |
| §3.7 / §4.6 holly | 7, 11 |
| §3.8 / §4.7 doorbell | 3 |
| §4.1 the entry, lists over entries, New Year, stored dates | 1, 2 |
| §4.2 contrast, ContrastTest split, danger vs weather, night red, inks, screenshots | 1, 2, 4, 6, 7 |
| §4.8 29 February | 8 |
| §5 constraints, baselines | Global Constraints; 1, 2 |
| §6 left to the builder | rulings 1–13 |
| §7 walk-through checks | 10, 11 |
| §8 not in this build | 12 |
