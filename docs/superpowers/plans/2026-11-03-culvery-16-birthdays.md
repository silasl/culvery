# Culvery — Plan 16: Birthdays Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–7 and 9–11 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 8 is the controller with the user and the son's SM-T510: **its Step 1 is done this week**, before the code is finished. Task 12 is the controller. Task 13 is the controller on the `culvery-5` AVD with the user. Task 14 needs the user and the SM-T510.

**Goal:** on a household member's birthday, found in the calendar, the wall greets them by name and dresses in their colour from midnight to midnight: their colour as the accent over a soft tint, a Cherry Bomb One clock, a cake in the rail, bunting and drifting balloons in Home's header, and confetti each time the screen wakes by day; Settings › Seasonal looks can preview it on any day.

**Architecture:**
- **`:core:ui`** gains the birthday look beside Plan 14's: `Birthday` (a colour and the names), six fixed `SeasonalLook`s (one per person colour, each with a night and a day set), `birthdayLook(colour)`, and `CulveryTheme(dark, seasonal, birthday)` with `Culvery.birthday`. The clock, rail mark and sky already read `Culvery.look`, so they follow a birthday with no new call sites. Cherry Bomb One, the cake, the bunting, the balloons and the confetti live here.
- **`:core:house`** holds the seam `TodaysBirthdays`, bound plainly (as `ShowingTheme` is) by **`:capability:calendar`**'s `CalendarBirthdays`, which reads only today's stored events on visible calendars.
- **`:app`**: `SeasonalThemes` keeps one preview (a theme's or a birthday's) and gains `dress`, what dresses the wall (a preview, then a real birthday, then a seasonal theme in its dates); `MainActivity` passes it to `CulveryTheme`. `ShowingTheme` stays `SeasonalThemes.showing`, so the doorbell follows the seasonal theme only (16 §3.2). `DisplayState` gains `dayWakes`; Home gets the greeting pill; Settings › Seasonal looks gets the preview row.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.** One new asset: Cherry Bomb One (SIL OFL 1.1, to be confirmed from its `OFL.txt`), approved by the user on 2026-11-03, from google/fonts at commit `3dd78844021e948ceb633d1dcee3f7885561b5d9` (2026-03-03; its folder there holds `CherryBombOne-Regular.ttf`, 214 940 bytes, `METADATA.pb`, `OFL.txt`).

**Spec:** `docs/superpowers/specs/2026-11-03-culvery-16-birthdays-design.md` as of 63d5c519, all of it. Its §2 decisions and §3 calls are settled; where the code forces a call, the rulings below make it. Its non-goals stand: no other occasions, ages, sound or house devices; Party, Holiday mode and the night clock unchanged.

**Previous plans:** `docs/superpowers/plans/2026-10-29-culvery-14-seasonal-themes.md` (the look, `SlotClock`, Creepster's download in its Task 6, the sky and `skyDrifts`, the preview, the UI-task ending), `docs/superpowers/plans/2026-10-19-culvery-08-sleep-and-wake.md` (`DisplayState`, `Screen`).

**Builds on:** branch `plan-16-birthdays` in the one checkout `C:\Users\si\code\siland\culvery`. No worktrees. Never push.

**Deadline:** on the SM-T510 by the evening before the next family birthday; the date is in culvery-notes, never in this repository. Tasks are ordered so a usable look lands first: colours (1), finding the birthday (2), the look wired with its precedence (3), the greeting (4), the clock (5), the preview (6), the cake (7). Task 8 Step 1 (the real event checked on the tablet) runs this week, needing no build. The decorations follow and are the cuts, in this order: **Task 11 (balloons) first, then Task 10 (confetti), then Task 9 (bunting).** A cut task moves to the follow-ups; nothing before it depends on it.

---

## Global Constraints

- **Spec anti-goals (§1), each a failure even if it works:** someone greeted on a day that isn't their birthday or under the wrong name; a name or birthday in a log, committed file, test or screenshot fixture; text harder to read in any of the six person colours, day or night; confetti or balloons keeping the screen or CPU busy while dark or asleep; the night clock lighting the room more.
- **Copy** (curly apostrophes in code; each owning task tests its strings):
  - Home's greeting: "Happy birthday " + `HouseLines.roomList(names)` ("Sam", "Sam and Alex", "Sam, Alex and Mia"), in People order, after a drawn cake. TalkBack reads "Happy birthday Sam".
  - Settings › Seasonal looks, section "Seasonal themes", after the themes' rows: switch "Preview a birthday" (plan); line "Shows {name}’s birthday on the wall until midnight, with confetti each time the screen wakes." (plan) when off, "Showing now. It ends at midnight." when on.
  - Log lines (plan; `Log.i`, which the release build strips, so debug builds only; `TAG = "SeasonalThemes"`): "Birthday look on for {count} people", "Birthday look off". Nothing else about birthdays is logged.
- **Personal data:** tests and screenshots use Sam, Alex, Mia, Jo (and the spec's Leo) and made-up dates (March); never a real name or the real birthday date, here, in tests or in the follow-ups. `Birthday.toString()` leaves out the names. No exception, `require` or `check` message interpolates a `Person`, `Birthday`, `StoredEvent` or `DisplaySettingsUi`.
- **Platform:** package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only; **no new library; never change a version**; no deprecated API, and any one met is flagged to the user before use. Checked and current: `withFrameMillis`, `mutableLongStateOf`, `Modifier.graphicsLayer`, `drawWithCache`, `rememberTextMeasurer`, `ImageVector.Builder.addPath`, `Regex(…, RegexOption.IGNORE_CASE)`, `ValueAnimator.areAnimatorsEnabled()`, `MutableSharedFlow`. Never `Settings.System.ANIMATOR_DURATION_SCALE`.
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash, or `gradlew.bat`), plus `./gradlew -p build-logic :convention:test` in any task that adds a log line, a `TAG` or a module edge.
  - Never plain `test`, `check` or `build`, and never a Gradle `install…` task. No subagent touches a device.
  - **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`**: a long temp path breaks Robolectric past MAX_PATH. Subagents' scratch goes in `$LISTS` = `C:/t9`, a plain shell variable, never exported.
- **Tests that can fail:** every new test fails before its change; one that passes before it is labelled **pin**. A logic test goes red on its assertion, not only on compiling: Step 1 adds the function with its final signature and a stub body, sees the tests fail on assertions, then writes the body. Where one stub can't turn both the positive and the negative tests red, the step names two stubs and runs each. Timer and frame tests run on `compose.mainClock` with `autoAdvance = false` or on virtual time. A fake clock is a `MutableStateFlow<LocalDateTime?>`.
- **Every UI task ends as Plan 14's:** `compareRoborazziDebug` on the module and a look at each `*_compare.png`; `recordRoborazziDebug`; the baseline check with `$LISTS/expected-baselines.txt` (the task's **New** and **Changes** names), an empty `$LISTS/expected-prefixes.txt`, and Plan 14's four `comm`/`grep` lines, whose first two lists must be empty; the gate; commit. `CopyScanTest` passes; every screen test ends with `compose.assertClickablesHaveRoleAndLabel()`. The plan records **seven** new birthday images in all: `birthday_colours_dark`/`_light` (Task 1), `home_birthday_pink_dark`/`_light` (Task 3, changed by each later Home task), `home_birthday_two_dark` (4), `settings_seasonal_birthday_dark` (6), `home_birthday_confetti_dark` (10).
- **Code:** call sites read `Culvery.look` or `Culvery.birthday`, never a colour by name. Comments say only what the code can't, one line by default, spec refs as `(16 §3.1)`, never a task or ruling number. Layout and drawing numbers go in a named object (`BirthdayDimens` in `:core:ui`, `ShellDimens` in `:app`).
- **Devices:** the emulator is `culvery-5`; `emulator-5554` holds a signed release and the household: never install on it. The tablet gets `adb install -r` of a signed release only; **never uninstall, never wipe**. No Hue, Sonos, plug or Home Assistant change.
- **Git:** a commit is one plain sentence with no prefix and **no trailer of any kind**; never push. **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **The wrong person, or the wrong day.** A one-off, a multi-day or a weekly "birthday"; an untagged one in Sam's own calendar; one tagged Family or a stale id; a timed "Birthday party"; a queued create not yet synced; a hidden calendar; a stale sync; midnight with no restart. Expected: only a yearly, one-day, all-day event on today, tagged for a current member, greets, and only them; the look goes at the first minute of the next day. Tests: Task 2's `BirthdaysInTest` and `CalendarBirthdaysTest`.
2. **Two looks on one day.** A birthday on 31 October; Halloween's preview during a real birthday; the birthday preview during Halloween's dates; a ring during each. Expected: preview > real birthday > Halloween by date; one preview at a time; the doorbell follows only Halloween's dates or its preview. Tests: Task 3's `SeasonalThemesTest` precedence tests.
3. **Text over twelve new colour sets.** Every `ContrastTest` pair in each set; warning red beside pink and orange; the greeting pill on `bg`; a stored colour that isn't one of the six. Expected: every pair passes; `danger` ≥ 35° from `accent`; the pill visible; an unknown colour keeps the normal colours and still greets. Tests: Task 1.
4. **Confetti when nobody should see it, or in the way.** App start; leaving and returning to the front; a tap on the night clock at night; animations off; a card or PIN pad up; sleep or a ring mid-burst; a tap under it; TalkBack. Expected: none of those drop confetti; a burst ends at once when the screen sleeps or a card or PIN pad shows; touches pass through; TalkBack finds nothing. Tests: Task 10's `DisplayStateTest` wake tests and `ConfettiLayerTest`.
5. **The header moving or crowding.** Cherry Bomb One from 11:11 to 00:00; four names in a narrow header; the bunting in the band. Expected: the clock's box keeps its size and draws nothing into the date; the greeting wraps clear of the header items, which don't move; flags stay inside the band. Tests: Task 5's `ThemedClockTest`, Task 4's `GreetingTest`, Task 9's `BirthdaySkyTest`.

---

| # | Task | Review | Cut |
|---|---|---|---|
| 1 | The birthday look and its twelve colour sets; contrast over sixteen sets | **opus** (readability) | — |
| 2 | Today's birthdays from today's stored events; the `TodaysBirthdays` seam | **opus** (wrong person) | — |
| 3 | One preview, the wall's dress and its precedence in `SeasonalThemes`; the app wired | **opus** (precedence, midnight) | — |
| 4 | The greeting pill under Home's date | sonnet | — |
| 5 | Cherry Bomb One and the birthday clock face | sonnet | — |
| 6 | Settings › Seasonal looks: "Preview a birthday" | sonnet | — |
| 7 | The cake in the rail and the pill | sonnet | — |
| 8 | The real birthday event checked on the tablet (**Step 1 this week**); optional early install | controller + user, **needs the SM-T510** | — |
| 9 | Bunting across Home's open header band | sonnet | third |
| 10 | Confetti on each daytime wake | **opus** (battery, touches) | second |
| 11 | Balloons drifting up in the band | **opus** (battery, heat) | **first** |
| 12 | Simplify the whole branch | controller | — |
| 13 | The emulator walk-through on `culvery-5` | controller + user | — |
| 14 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** | — |

## Rulings against the code

1. **What counts** (`capability/calendar/.../Birthdays.kt`), from `StoredEvent`s:
   - `start` and `end` both `EventTime.AllDay`, and `daySpan(start, end, zone) == date to date` (one day, today);
   - **yearly:** `recurring` is true, and `recurrenceRule` is either null or holds `FREQ=YEARLY` (matched on the uppercased rule, which may be preceded by a `DTSTART` line). The store's `recurring` comes from Google's `recurringEventId`, so it is always set for a series occurrence; `recurrenceRule` is null when the provider couldn't read the series (`GoogleCalendarProvider.ruleOf` logs and stores none). A known non-yearly rule (weekly, monthly, daily) doesn't count; an unread rule does, so a failed series read can't drop a real birthday.
   - the title matches `Regex("""\bbirthdays?\b""", RegexOption.IGNORE_CASE)`; titles are never matched against names (16 §3.1 accepted risk);
   - the people are `peopleIn(forPerson)` kept only when in People (Family and unknown ids drop out), never `sourcePerson`; distinct, in People order.
2. **Where it reads** (`CalendarBirthdays`): `CalendarStore.eventsBetween(from, to)` for today ± `ZONE_MARGIN_MS`, as `CalendarMultiDayEvents` does; its query joins visible sources only. No pending overlay, so a queued create counts after its sync (16 §4.1); no connection health, so a stale sync still counts. The query is re-made only when the household's date or zone changes (`distinctUntilChanged`), never each minute.
3. **The seam** (`core/house/.../HouseSeams.kt`): `interface TodaysBirthdays { val today: StateFlow<List<Person>> }`, bound with `@Binds` in `CalendarModule` (no `@BindsOptionalOf`; the calendar is always installed). People's rename or recolour shows at once.
4. **The look type** (`core/ui/.../Birthday.kt`):
   ```kotlin
   /** A birthday on the wall: the first person's [colour] and every birthday person's name, in People order (16 §3.2). */
   data class Birthday(val colour: Long, val names: List<String>) {
       override fun toString() = "Birthday(${names.size})"
   }
   ```
   `fun birthdayLook(colour: Long): SeasonalLook?` is `PersonPalette.colors.indexOf(colour)`'s look, null for any other colour. `CulveryTheme(dark, seasonal = null, birthday = null, content)`: the look is `if (birthday != null) birthdayLook(birthday.colour) else seasonal?.look`, so a birthday in an unknown colour keeps the normal colours and still greets. A new `LocalLook` carries the look and `Culvery.look` reads it; `LocalBirthday` carries `birthday` for `Culvery.birthday`. `LocalSeasonalTheme` then has no reader and is removed (`ThemeTest` lines 107 and 123 read `Culvery.look` instead). The new colour helper is `colorsOf(look: SeasonalLook?, dark: Boolean)`; `colorsFor(seasonal, dark)` delegates to it, so `colorsFor(null, …)` (ThemeTest line 128) stays unambiguous. A birthday look is `SeasonalLook(night, day, clockFace = CherryBombClock /* 5 */, mark = CakeMark /* 7 */, sky = BirthdaySky /* 9, 11 */)`, no `cardCorner`, no `sound`.
5. **The twelve colour sets** (`core/ui/.../BirthdayLook.kt`), worked out by a script against `ContrastTest`'s pairs and `hueGap`; the test decides, and the implementer moves any value a test fails by the smallest step that passes and says which. Each colour stores the values below; a private builder makes each set with `LightColors.copy(…)` / `DarkColors.copy(…)`. Day: `accentInk` white, `accentOnInk` the person colour. Night: `accentInk` `0xFF0E1011`, `accentOnInk` the day accent. "—" keeps the base `danger`.

   | colour | set | bg | surf2 | line | accent | accentSoft | danger |
   |---|---|---|---|---|---|---|---|
   | Green 0xFF4CB387 | day | E7F0EC | E7EFEB | BBD6CA | 2F6F54 | C9E8DB | — |
   | | night | 111817 | 263030 | 232F2C | 4CB387 | 22342E | — |
   | Blue 0xFF5B9BE0 | day | E8EEF4 | E8EDF2 | BED1DE | 2165AF | D1E3F6 | — |
   | | night | 12171B | 272F35 | 242D33 | 5B9BE0 | 24303B | — |
   | Pink 0xFFE07BA8 | day | F4EBEF | F2EBEE | DBCAD1 | B42A68 | F6D9E6 | B8461A |
   | | night | 181519 | 2F2D32 | 2E2A2F | E07BA8 | 382B33 | EE8A5A |
   | Lime 0xFF9CC44E | day | EEF2E7 | EDF0E7 | CCDABE | 546D24 | DAE9BE | — |
   | | night | 151914 | 2B312C | 293027 | 9CC44E | 2E3625 | — |
   | Orange 0xFFE04A26 | day | F4EAE7 | F5ECEA | DBBFB5 | B3371A | F8D9D1 | A8236A |
   | | night | 181312 | 2F2A2A | 2E2624 | E66E51 | 38241F | EE6FB0 |
   | Yellow 0xFFCEC018 | day | F2F1E2 | F0EFE4 | D7D9B2 | 6D660D | ECE6A5 | — |
   | | night | 181911 | 2E3129 | 2D3023 | CEC018 | 35351D | — |

   (All `0xFF`-prefixed.) The mockup's pink accent `#C2457E` is 4.05:1 on its own `bg`, so the pink day accent is the darker `B42A68` of the same hue. Pink's `danger` moves toward orange-red and orange's toward crimson-magenta, each at least 42° from its accent.
6. **One preview** (`SeasonalThemes`): `ThemePreview` becomes
   ```kotlin
   sealed interface Preview {
       val day: LocalDate
       data class Theme(val theme: SeasonalTheme, override val day: LocalDate) : Preview
       data class Birthday(override val day: LocalDate) : Preview
   }
   ```
   in the existing single `MutableStateFlow`, so turning one on replaces the other with no extra code; a preview whose `day` isn't today counts as off. `showingOn` reads only `Preview.Theme`, so `showing` (the doorbell's) never sees a birthday.
7. **The dress** lives on `SeasonalThemes` (no new singleton: the preview, the dates and the precedence are already there): `data class WallDress(val seasonal: SeasonalTheme? = null, val birthday: Birthday? = null)` and `internal fun dressOn(preview: Preview?, previewPerson: Person?, birthdays: List<Person>, seasonal: SeasonalTheme?): WallDress` — a theme preview → that theme; else a birthday preview with someone in People → `Birthday(person.color, listOf(person.name))`; else birthdays → `Birthday(first.color, all names)` (16 §3.2); else `seasonal` by its dates. The birthday preview takes the first person in `HouseholdRepository.people`. Because `SeasonalThemes` then logs, `SeasonalThemesTest` runs under Robolectric (`@RunWith(AndroidJUnit4::class)`, as `HeatLogTest`).
8. **The greeting pill** (`HomeHeader`, a fourth child placed under the date): `ShellDimens.greetingGap = 12.dp` below the date; a `Row` with `accentSoft` fill, fully rounded, `greetingPaddingH = 14.dp`, `greetingPaddingV = 6.dp`; a cake `greetingCake = 22.dp` wide (a `Spacer` until Task 7), `greetingCakeGap = 8.dp`, then the text in `HhType.rowTitle`, `ink`, `maxLines = 2`. **A drawn cake replaces the "🎂" emoji**: the emoji would draw from the system font (missing glyphs in Robolectric) and TalkBack would read it. The `Row` has `clearAndSetSemantics { text = AnnotatedString(greeting) }`, tag `home_greeting`, `traversalIndex = 1.5f`. Its width is at most the header width less the items' width and `HEADER_ITEM_GAP`. The header items keep their bottom at the date's; the header grows by the pill, so Home's grid is that much shorter on the day. `HomeSkyBand` gains `greetingWidth`, set from the pill and **reset to 0 when the pill leaves**; `skyBand` takes the widest of clock, date and greeting.
9. **The clock face:** Cherry Bomb One at the clock's 100 sp, `lineBox(86.sp)`, no letter spacing, in `accent`, through `SlotClock`. If the date test fails, shrink `CherryBombClock`'s `fontSize` by whole sp until it passes and say the size.
10. **The cake** (`CakeMark(width)`): an `ImageVector` with `CulveryMark`'s 54 × 49 viewport: two tiers in `accent`, icing drips in `BirthdayInks.ICING = Color(0xFFFFF4E8)` (cream, visible on the tiers in both themes and on the pill, where an `accentSoft` icing would vanish), a plate in `accentSoft`, one candle in `ink`, a flame in `weather`. Tag `cake_mark`; no description; `hideFromAccessibility()`.
11. **The sky** (`BirthdaySky(drifting, modifier)`), in Plan 14's open header band (`skyBand`), which leaves out the clock, date, greeting and items, so no text sits over a decoration; clipped to the band (`graphicsLayer { clip = true }`, as `HalloweenSky`):
    - **Bunting** (static): a `mute` string, 1.5 dp, across the band `buntingTop = 6.dp` down, sagging `buntingSag = 16.dp` at the middle; triangular flags `flagWidth = 18.dp` × `flagHeight = 22.dp` every `flagPitch = 28.dp`, in `PersonPalette.colors` order, cycling. A band narrower than three pitches gets none.
    - **Balloons:** 4, `balloonWidth = 30.dp` × `balloonHeight = 38.dp`, `balloonString = 26.dp` in `mute`; lane `i` centred at `band.left + band.width × (i + 0.5) / 4`, a quarter lap apart; each rises from below the band to above it once every `BALLOON_LAP_MS = 40_000L`, swaying `balloonSway = 6.dp` (one sine period per lap); `PersonPalette.colors[i]` at `BirthdayInks.BALLOON_ALPHA = 0.85f`; drawn under the bunting. The drift reuses Plan 14's frame step (`SKY_FRAME_MS`, elapsed read only when drawing) and `skyDrifts`.
12. **Confetti** (`core/ui/.../Confetti.kt`): 60 pieces, each 8 × 12 dp, colours `PersonPalette.colors + accent`; seeded start x across the width and y from `−0.3 × height` to 0; all below the bottom by `CONFETTI_MS = 2_500L`; spin and sideways drift the implementer's; gone by `CONFETTI_GONE_MS = 3_000L`. Drawn each frame for its short life, its own `graphicsLayer`, no pointer input, `clearAndSetSemantics {}`.
13. **When confetti falls:** `DisplayState.dayWakes: SharedFlow<Unit>` (hot, `extraBufferCapacity = 1`, `DROP_OLDEST`), emitted inside `change` when `internal fun dayWake(before: Display, after: Display): Boolean` = `before.screen != AWAKE && after.screen == AWAKE && before.inFront && after.inFront && after.night == false`. That leaves out the app's start (no change from `START`'s `AWAKE`), leaving or returning to the front (`inFront` changes), and any wake in the night hours, a tap on the night clock included (16 §3.3); the night clock ending at its morning time is a wake. `MainActivity` drops a burst on each `dayWakes` while `dress.birthday != null && animatorsOn && pinRequest == null && alert == null` (the door card is an alert); a sheet or Settings doesn't stop it. A burst shows only while `screen == AWAKE` and that condition hold; either turning false ends it for good.
14. **The preview row:** Plan 15b moved the spec's "Settings › Display, Seasonal themes" to App settings › Seasonal looks, section "Seasonal themes"; the row goes there, after the themes. With nobody in People it isn't shown.
15. **Log hygiene:** `LogHygiene.CALL` in `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` becomes `\bLog\.(w|e|i|wtf|println)\(`; `ALLOWED` gains `after.screen.name` (`DisplayState`'s existing line) and the one expression the birthday line uses for its count.

## File Structure

```
core/ui/src/main/java/uk/co/siland/culvery/core/ui/
  Birthday.kt (Birthday, LocalBirthday, LocalLook), BirthdayLook.kt (six looks, birthdayLook,
    BirthdayInks), Seasonal.kt (colorsOf; LocalSeasonalTheme removed), Theme.kt                     (Task 1)
  ClockFace.kt (CherryBombOne, CherryBombClock); res/font/cherry_bomb_one.ttf;
    licenses/OFL-CherryBombOne.txt                                                                  (Task 5)
  BirthdayArt.kt (CakeMark; BirthdaySky, drawBunting, BirthdayDimens; balloonPositions)             (Tasks 7, 9, 11)
  Seasonal.kt, HalloweenArt.kt (HomeSkyBand.greetingWidth, skyBand)                                 (Task 4)
  Confetti.kt (ConfettiBurst, confettiPieces)                                                       (Task 10)
core/ui testFixtures Screenshots.kt (snap's birthday)                                               (Task 1)
core/house: HouseSeams.kt (TodaysBirthdays)                                                         (Task 2)
capability/calendar: Birthdays.kt (birthdaysIn, CalendarBirthdays), di/CalendarModule.kt            (Task 2)
app: display/SeasonalThemes.kt (Preview, WallDress, dress), MainActivity.kt, HouseGraphProbe.kt    (Tasks 2, 3)
  shell/ui/HomeScreen.kt, shell/ui/ShellDimens.kt                                                   (Task 4)
  display/DisplayWords.kt, display/ui/DisplaySettingsPage.kt                                        (Task 6)
  display/DisplayState.kt (dayWakes), display/ui/ConfettiLayer.kt, MainActivity.kt                  (Task 10)
build-logic/convention/src/test/kotlin/LogHygieneTest.kt                                            (Task 3)
README.md (licence line)                                                                            (Task 5)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                                (Tasks 8, 13, 14)
```

---

### Task 1: The birthday look and its twelve colour sets (spec §3.2, §4.2; rulings 4, 5)

**Review:** opus.

**Files:**
- Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Birthday.kt`, `BirthdayLook.kt`.
- Modify `core/ui/.../Seasonal.kt` (`colorsOf`; `colorsFor` delegates; `LocalSeasonalTheme` removed), `Theme.kt` (`CulveryTheme(dark, seasonal, birthday)`, `LocalLook`, `LocalBirthday`, `Culvery.birthday`).
- Modify `core/ui/src/testFixtures/.../test/Screenshots.kt` (`snap(…, seasonal, birthday: Birthday? = null, content)`).
- Test: `core/ui/src/test/.../ContrastTest.kt`, `ThemeTest.kt`, new `BirthdayLookTest.kt`, `ButtonScreenshotTest.kt`.

**Interfaces:**
- Produces (public in `uk.co.siland.culvery.core.ui`): `Birthday` (ruling 4); `fun birthdayLook(colour: Long): SeasonalLook?`; `fun colorsOf(look: SeasonalLook?, dark: Boolean): HhColors`; `CulveryTheme(dark: Boolean, seasonal: SeasonalTheme? = null, birthday: Birthday? = null, content)`; `Culvery.birthday: Birthday?`; `object BirthdayInks { val ICING: Color; const val BALLOON_ALPHA = 0.85f; fun balloon(argb: Long, c: HhColors): Color; fun flag(argb: Long): Color }`.

- [ ] **Step 1: The failing tests** (stub: `birthdayLook` returns a look of `DarkColors`/`LightColors` for any colour).
  - `ContrastTest`: `themes` becomes "normal", each seasonal theme, and each `birthdayLook(c)` named "{`PersonPalette.nameOf(c)`} birthday", × night and day (sixteen sets); every existing loop covers them unchanged. Add `theGreetingPillStandsOffTheBackground` (each birthday set: `contrast(accentSoft, bg) >= QUIET_FILL_MIN`), `eachBirthdayAccentKeepsThePersonsHue` (`hueGap(accent, Color(c)) <= 12f`), `balloonsAndFlagsStandOffTheBackground` (each birthday set × each person colour, `balloon` and `flag` on `bg` at `SKY_MIN`).
  - `ThemeTest`: `aBirthdayProvidesItsColourSets` (`birthday = Birthday(pink, ["Sam"])`, dark → `Culvery.colors == birthdayLook(pink)!!.night`, `Culvery.look === birthdayLook(pink)`, `Culvery.birthday?.names == ["Sam"]`; light → `.day`); `aBirthdayWinsOverATheme` (with `seasonal = HALLOWEEN`); `aColourOutsideTheSixKeepsTheNormalLook` (`Birthday(0xFF123456, ["Sam"])` → `DarkColors`, `Culvery.look == null`, `Culvery.birthday` set); `noBirthdayIsAsBefore` (**pin**).
  - `BirthdayLookTest`: `everyPersonColourHasALook` (each of the six non-null, `night` dark, `day` light, `weather`, `rain`, `ink`, `mute`, `surf`, `surf3`, `border` the base set's); `theLookHasNoCornerAndNoSound`; `aBirthdayPrintsNoName` (`toString()` lacks "Sam").
  - `ButtonScreenshotTest`: `birthday_colours_dark` / `_light`: a strip per person colour, each under its own `CulveryTheme(birthday = …)`: `bg`, a Primary and a Quiet button, ink and mute text on `surf`, a pill of `accentSoft` with ink text.

  Run `./gradlew :core:ui:testDebugUnitTest`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement** per rulings 4 and 5.
- [ ] **Step 3: Images.** **New:** `birthday_colours_dark`, `birthday_colours_light` (each strip in its person's colour; text as clear as the normal images). **Changes:** none.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui
git commit -m "Add a birthday look for each of the six person colours, with day and night colour sets checked by the same contrast tests as every other look"
```

---

### Task 2: Today's birthdays from today's stored events (spec §3.1, §4.1; rulings 1–3; Review Focus 1)

**Review:** opus.

**Files:**
- Modify `core/house/.../HouseSeams.kt` (`TodaysBirthdays`).
- Create `capability/calendar/.../Birthdays.kt`; modify `di/CalendarModule.kt` (`@Binds abstract fun todaysBirthdays(impl: CalendarBirthdays): TodaysBirthdays`).
- Modify `app/src/debug/.../di/HouseGraphProbe.kt` (`todaysBirthdays(): TodaysBirthdays`).
- Test: new `capability/calendar/src/test/.../BirthdaysInTest.kt`, `CalendarBirthdaysTest.kt` (Robolectric, Room in memory as `CalendarMultiDayEventsTest`); `app/src/test/.../HouseGraphTest.kt`.

**Interfaces:**
- Produces:
  - `interface TodaysBirthdays { /** Today's birthday people in People order (16 §3.1); holds names, never logged. */ val today: StateFlow<List<Person>> }`.
  - `internal fun birthdaysIn(events: List<StoredEvent>, date: LocalDate, order: List<PersonId>, zone: ZoneId): List<PersonId>` (ruling 1).
  - `@Singleton class CalendarBirthdays internal constructor(now: StateFlow<LocalDateTime?>, events: (fromMillis: Long, toMillis: Long) -> Flow<List<StoredEvent>>, people: Flow<List<Person>>, zone: Flow<ZoneId>, scope: CoroutineScope) : TodaysBirthdays`; `@Inject constructor(clock: HouseholdClock, store: CalendarStore, household: HouseholdRepository, zone: HouseholdZone, @ApplicationScope scope: CoroutineScope)` passing `store::eventsBetween`. `today`: `stateIn(scope, Eagerly, emptyList())`; empty while the clock is unread.

- [ ] **Step 1: The failing tests.** Run them against two stubs in turn: `emptyList()` (the positive tests go red), then "every household member any event's tag names" (the negative tests go red).
  - `BirthdaysInTest` (2026-03-12, People order Alex, Sam, Mia; `StoredEvent` helpers `yearly(title, forPerson, sourcePerson = family, first = date, days = 1, rule = "RRULE:FREQ=YEARLY")`, `oneOff(…)`, `timed(…)`):
    - `aYearlyBirthdayTaggedForSamIsSams`: "Sam’s birthday", "sam" → `[sam]`.
    - `aOneOffBirthdayGreetsNobody`: `recurring = false` → `[]`.
    - `aMultiDayBirthdayGreetsNobody`: yearly, two days from the 12th, and two days to the 12th → `[]`.
    - `onlyAYearlyRuleCounts`: `"RRULE:FREQ=WEEKLY"` → `[]`; `"DTSTART:20200312\nRRULE:FREQ=YEARLY"` → `[sam]`; `recurring = true`, rule null → `[sam]`.
    - `onlyTheWholeWordCounts`: "Birthday", "birthday!", "BIRTHDAYS" count; "bday", "Birthdayparty" don't.
    - `theTagDecides`: untagged with `sourcePerson = sam` → `[]`; "family" → `[]`; "ghost,sam" → `[sam]`; "sam,alex" → `[alex, sam]`.
    - `aTimedBirthdayPartyDoesntCount`: timed at 14:00 → `[]`.
    - `onlyTodayCounts`: yearly on the 11th or 13th → `[]`.
    - `twoEventsForSamAreOneBirthday` → `[sam]`.
    - `aBirthdayTitledForSomeoneElseGreetsTheTag` (**pin**, 16 §3.1 accepted risk): "Leo’s birthday", "sam" → `[sam]`.
  - `CalendarBirthdaysTest` (a real `CalendarStore` over Room; People Sam (pink), Alex (blue); fake `now`):
    - `samsBirthdayIsSam`: synced yearly "Sam’s birthday" for the 12th, 09:00 → `today == [Sam]` (name and colour).
    - `aBirthdayEndsAtMidnight`: 23:59 → `[Sam]`; the 13th 00:00 → `[]`.
    - `nobodyBeforeTheClockIsRead`: `now` null → `[]`, the events lambda never called.
    - `theQueryFollowsTheDateNotTheMinute`: a counting `events` lambda; 09:00 then 09:01 → called once; the 13th 00:00 → twice.
    - `aHiddenCalendarGreetsNobody`: the source's mapping not visible → `[]`.
    - `aQueuedCreateWaitsForItsSync`: the birthday only as a queued create in the outbox (as `CalendarRepositoryTest` queues one) → `[]`.
    - `aStaleSyncStillGreets`: the connection's last sync a month before → `[Sam]`.
    - `aRenameShowsAtOnce`: People renames Sam → `today` follows.
  - `HouseGraphTest.theCalendarFindsTheBirthdays`: `todaysBirthdays()` is a `CalendarBirthdays`.

  Run `./gradlew :capability:calendar:testDebugUnitTest :app:testDebugUnitTest --tests '*HouseGraph*'` and `./gradlew -p build-logic :convention:test`. Expected: FAIL on assertions (each stub), the pin aside.
- [ ] **Step 2: Implement** per rulings 1–3. No log line.
- [ ] **Step 3: The gate, then commit.** No screenshot changes.
```bash
git add core/house capability/calendar app/src/debug app/src/test
git commit -m "Find today's birthdays in today's stored events, counting only yearly one-day all-day events tagged for a household member"
```

---

### Task 3: One preview, the wall's dress and its precedence (spec §3.2, §3.5; rulings 6, 7, 15; Review Focus 2)

**Review:** opus.

**Files:**
- Modify `app/.../display/SeasonalThemes.kt` (`Preview` replaces `ThemePreview`; `showingOn(date, dates, preview: Preview?)`; `WallDress`, `dressOn`, `dress`, `previewPerson`, `previewingBirthday`, `previewBirthday`; the two log lines), `MainActivity.kt` (`CulveryTheme(dark, seasonal = dress.seasonal, birthday = dress.birthday)` from `seasonal.dress`), `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` (ruling 15).
- Test: `app/src/test/.../display/SeasonalThemesTest.kt` (now Robolectric; its `ThemePreview(…)` uses become `Preview.Theme(…)`); `display/ui/SleepLayersTest.kt`; `shell/ui/ShellScreenshotTest.kt` (`snap` gains `birthday`); `LogHygieneTest`.

**Interfaces:**
- Consumes: Task 1's `Birthday`, `CulveryTheme(…, birthday)`; Task 2's `TodaysBirthdays`.
- Produces (in `uk.co.siland.culvery.display`), on `SeasonalThemes`:
  - internal constructor `(now, dates, scope, birthdays: Flow<List<Person>> = flowOf(emptyList()), people: Flow<List<Person>> = flowOf(emptyList()))`; `@Inject` constructor gains `todays: TodaysBirthdays, household: HouseholdRepository`.
  - `val dress: StateFlow<WallDress>` (Eagerly, `WallDress()` first); `val previewPerson: StateFlow<Person?>`; `val previewingBirthday: StateFlow<Boolean>`; `fun previewBirthday(on: Boolean)` (nothing while the clock is unread; off clears only a birthday preview). `showing`, `previewing` and `preview(theme, on)` keep their meaning.

- [ ] **Step 1: The failing tests** (stub: `dressOn` returns `WallDress(seasonal = seasonal)`, `previewBirthday` does nothing).
  - `SeasonalThemesTest` (People Sam (pink), Alex (blue), in that order; dates in March unless Halloween's):
    - `dressOnFollowsTheOrder`: `dressOn(Theme(HALLOWEEN), Sam, [Alex], null)` → Halloween; `dressOn(Birthday, Sam, [Alex], HALLOWEEN)` → `Birthday(pink, ["Sam"])`; `dressOn(null, Sam, [Sam, Alex], HALLOWEEN)` → `Birthday(pink, ["Sam", "Alex"])`; `dressOn(null, Sam, [], null)` → `WallDress()` (**pin** for that case).
    - `aBirthdayOn31OctoberBeatsHalloween`: through `SeasonalThemes` on 2026-10-31 with `[Sam]` → Sam's dress, and `showing == HALLOWEEN`.
    - `aHalloweenPreviewBeatsARealBirthday`: 2026-03-12 with `[Sam]`, `preview(HALLOWEEN, true)` → Halloween's dress.
    - `previewsReplaceEachOther`: `preview(HALLOWEEN, true)`, then `previewBirthday(true)` → `previewing == null`, `previewingBirthday`, Sam's dress; then `preview(HALLOWEEN, true)` → `previewingBirthday` false.
    - `aBirthdayPreviewEndsAtMidnight`: on at 23:59 → Sam's; 00:00 → `WallDress()`, `previewingBirthday` false.
    - `theDoorbellFollowsOnlyTheSeason`: 2026-03-12, `previewBirthday(true)` → `showing == null`.
    - `noPreviewWithNobody`: People empty → `previewPerson` null; `previewBirthday(true)` leaves `WallDress()`.
    - `nothingPreviewsBeforeTheClockIsRead`: `now` null → `previewBirthday(true)` leaves `previewingBirthday` false.
  - `SleepLayersTest.theNightClockIsTheSameOnABirthday` (**pin**): as `theNightClockIsTheSameInEveryTheme`, under a pink birthday, dark and light.
  - `LogHygieneTest`: `anInfoLineIsChecked` (`Log.i(TAG, "x $name")` → one problem); the existing debug-line case keeps `Log.d` unchecked.
  - `ShellScreenshotTest`: `home_birthday_pink_dark` / `_light` (the `fullHome` state, `Birthday(pink, ["Sam"])`; colours only now).

  Run `./gradlew :app:testDebugUnitTest --tests '*SeasonalThemes*' --tests '*SleepLayers*'` and `./gradlew -p build-logic :convention:test`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** per rulings 6, 7 and 15. `MainActivity` collects `seasonal.dress` in place of `seasonal.showing`.
- [ ] **Step 3: Images.** **New:** `home_birthday_pink_dark`, `home_birthday_pink_light`. **Changes:** none.
- [ ] **Step 4: The gate, then commit.**
```bash
git add app build-logic
git commit -m "Dress the wall for today's birthday, a preview first, then a birthday, then a seasonal theme in its dates, while the doorbell keeps following the season"
```

---

### Task 4: The greeting pill under Home's date (spec §2.2, §4.2; ruling 8; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify `app/.../shell/ui/HomeScreen.kt` (`HomeHeader` places the pill when `Culvery.birthday` is set, its text `"Happy birthday " + HouseLines.roomList(names)`), `ShellDimens.kt`, `core/ui/.../Seasonal.kt` (`HomeSkyBand.greetingWidth`), `core/ui/.../HalloweenArt.kt` (`skyBand`).
- Test: new `app/src/test/.../shell/ui/GreetingTest.kt`; `ShellScreenshotTest`; `ShellLayoutTest` (**pins**).

**Interfaces:**
- Consumes: `Culvery.birthday`, `HouseLines.roomList`.
- Produces: tag `home_greeting`; `HomeSkyBand.greetingWidth: Int`.

- [ ] **Step 1: The failing tests** (`HomeHeader` under `CulveryTheme(dark = true, birthday = …)`, one sample header item).
  - `theGreetingSitsUnderTheDate`: `["Sam"]` → `home_greeting`'s top `greetingGap` below `home_date`'s bottom, left edges equal.
  - `talkBackReadsTheGreeting`: text "Happy birthday Sam", no emoji; reading order clock, date, greeting, items.
  - `twoAndThreeNames`: "Happy birthday Sam and Alex"; "Happy birthday Sam, Alex and Mia".
  - `aLongGreetingWrapsClearOfTheItems`: `["Sam", "Alex", "Mia", "Jo"]` in a 700 dp header → two lines, and `home_greeting`'s right is left of `home_header_items`' left by at least `HEADER_ITEM_GAP`.
  - `theHeaderItemsDontMove` (**pin**): the items' bounds equal with and without a birthday.
  - `noGreetingWithoutABirthday` (**pin**).
  - `theBandForgetsTheGreeting`: a provided `HomeSkyBand`; with the birthday `greetingWidth > 0`; birthday removed → 0.
  - `thePillIsThePersonsSoftColour` (`@GraphicsMode(NATIVE)`): a pixel inside the pill clear of text and cake is the pink night `accentSoft`.
  - Screenshot: new `home_birthday_two_dark` (Sam and Alex, pink).

  Run `./gradlew :app:testDebugUnitTest --tests '*Greeting*' --tests '*ShellLayout*'`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** per ruling 8.
- [ ] **Step 3: Images.** **New:** `home_birthday_two_dark`. **Changes:** `home_birthday_pink_dark`, `home_birthday_pink_light`.
- [ ] **Step 4: The gate, then commit.**
```bash
git add app core/ui
git commit -m "Greet the birthday people by name in a soft pill of their colour under Home's date"
```

---

### Task 5: Cherry Bomb One and the birthday clock face (spec §4.2, §4.3; ruling 9; Review Focus 5)

**Review:** sonnet.

**Files:**
- Download (approved by the user 2026-11-03) into a new empty `$LISTS/cherrybombone/`, from `https://raw.githubusercontent.com/google/fonts/3dd78844021e948ceb633d1dcee3f7885561b5d9/ofl/cherrybombone/`: first `METADATA.pb`, whose `fonts { filename: … }` names the TTF (expected `CherryBombOne-Regular.ttf`), then that TTF and `OFL.txt`. Check before copying: `grep -c '^license: "OFL"' METADATA.pb` prints 1; `grep -c "SIL OPEN FONT LICENSE Version 1.1" OFL.txt` prints at least 1; `head -c 4 <ttf> | xxd -p` prints `00010000`; the TTF is 214 940 bytes. **Any failure, a 404, or an `OFL.txt` that isn't the SIL Open Font License → stop and tell the controller, who asks the user.** Record `sha256sum <ttf>`. Copy to `core/ui/src/main/res/font/cherry_bomb_one.ttf` and `core/ui/licenses/OFL-CherryBombOne.txt`; no subsetting. Nothing in `$LISTS/cherrybombone/` is run.
- Modify `README.md`'s licence paragraph (line 214), after Creepster's sentence: "Cherry Bomb One: SIL Open Font License (`core/ui/licenses/OFL-CherryBombOne.txt`), for the birthday clock; from google/fonts at commit 3dd78844021e948ceb633d1dcee3f7885561b5d9, `CherryBombOne-Regular.ttf` sha256 {hash}."
- Modify `core/ui/.../ClockFace.kt` (`CherryBombOne`, `CherryBombClock`), `BirthdayLook.kt` (`clockFace = CherryBombClock`).
- Test: `core/ui` `ThemeTest.bundledFontsAreRealFiles`, `ClockFaceTest`; `app/src/test/.../shell/ui/HalloweenClockTest.kt` renamed `ThemedClockTest.kt` and parameterised (`ParameterizedRobolectricTestRunner`) over Halloween and a pink birthday; `ShellScreenshotTest`.

**Interfaces:**
- Produces: `val CherryBombOne: FontFamily`; `val CherryBombClock: TextStyle` = `HhType.clock` with `fontFamily = CherryBombOne`, `fontWeight = FontWeight.Normal`, `fontFeatureSettings = null`, `letterSpacing = 0.sp`.

- [ ] **Step 1: The failing tests.**
  - `ThemeTest.bundledFontsAreRealFiles`: `R.font.cherry_bomb_one` between 100 000 and 500 000 bytes.
  - `ClockFaceTest.everyDigitGetsTheSameSlot` gains a `CherryBombClock` row.
  - `ThemedClockTest`, each case per look (its face, its night `accent`): `theClockIsInTheLooksFace`; `theClockKeepsItsWidthEveryMinute` (11:11, 00:00, 08:47, 23:58; **pin** for Halloween); `theHeaderKeepsItsHeightInEitherFace` (`home_clock`'s height with no look and with the look; **pin** for Halloween); `theClockDrawsNothingIntoTheDate` (`@GraphicsMode(NATIVE)`, at 23:58 and 22:22: no `accent` pixel in the rows from `home_date`'s top to its bottom, across the clock's width; the pill's cake below is outside the scan); `theClockIsTheLooksAccent`; `theDateStaysInDmSans` (**pin**).
  - Screenshots: `home_birthday_pink_*`, `home_birthday_two_dark` change.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*ThemedClock*'`. Expected: FAIL on the birthday cases, the pins aside.
- [ ] **Step 2: Implement** per ruling 9.
- [ ] **Step 3: Images.** **Changes:** `home_birthday_pink_dark`, `home_birthday_pink_light`, `home_birthday_two_dark` (the clock in pink Cherry Bomb One; date and weather unchanged).
- [ ] **Step 4: The gate, then commit** (`git status` first: nothing from `$LISTS`).
```bash
git add core/ui README.md app
git commit -m "Set Home's clock in Cherry Bomb One in the birthday person's colour, each digit in a fixed slot so the header never moves"
```

---

### Task 6: Settings › Seasonal looks: "Preview a birthday" (spec §3.5, §4.4; ruling 14)

**Review:** sonnet.

**Files:**
- Modify `app/.../display/DisplayWords.kt` (`PREVIEW_A_BIRTHDAY`, `fun birthdayPreviewLine(name: String, on: Boolean): String`), `display/ui/DisplaySettingsPage.kt` (the row on `SeasonalLooksScreen`; the host reads `themes.previewPerson`, `previewingBirthday`).
- Modify `app/src/test/.../settings/AppSettingsRig.kt` if its `SeasonalThemes(…)` needs the new parameters.
- Test: `display/ui/DisplaySettingsPageTest.kt`, `display/ui/DisplaySettingsScreenshotTest.kt`.

**Interfaces:**
- Consumes: Task 3's `previewPerson`, `previewingBirthday`, `previewBirthday(on)`, `dress`.
- Produces: `DisplaySettingsUi.birthdayName: String? = null`, `.previewingBirthday: Boolean = false`; `DisplaySettingsActions.onPreviewBirthday: (Boolean) -> Unit = {}`; tag `display_preview_birthday`.

- [ ] **Step 1: The failing tests** (`DisplaySettingsPageTest`).
  - `theBirthdayPreviewRow`: `birthdayName = "Sam"` → after Halloween's rows, switch "Preview a birthday", off, line "Shows Sam’s birthday on the wall until midnight, with confetti each time the screen wakes.".
  - `theBirthdayPreviewSaysWhenItEnds`: on → "Showing now. It ends at midnight.".
  - `theSwitchPreviewsABirthday`: tap → `onPreviewBirthday(true)`; on, tap → `false`.
  - `noRowWithNobody`.
  - `theHostPreviewsABirthdayThroughSeasonalThemes`: the host over a real `SeasonalThemes` (fake clock 2026-03-05, People Sam) → tap sets `dress` to Sam's birthday; Halloween's switch then turns the birthday switch off.
  - `aBirthdayPreviewEndingAtMidnightTurnsTheSwitchOff`.
  - Screenshot: new `settings_seasonal_birthday_dark` (the preview on, under Sam's pink).

  Run `./gradlew :app:testDebugUnitTest --tests '*DisplaySettings*' --tests '*AppSettings*'`. Expected: FAIL on assertions.
- [ ] **Step 2: Implement** with `HhSwitchRow`, per the Copy.
- [ ] **Step 3: Images.** **New:** `settings_seasonal_birthday_dark`. **Changes:** whichever existing Seasonal looks images show the section's foot (each changes only by the new row).
- [ ] **Step 4: The gate, then commit.**
```bash
git add app
git commit -m "Add a birthday preview to Settings › Seasonal looks, showing the first person's birthday until midnight or until turned off"
```

---

### Task 7: The cake in the rail and the pill (spec §2.2, §4.2; ruling 10)

**Review:** sonnet.

**Files:**
- Create `core/ui/.../BirthdayArt.kt` (`CakeMark`); modify `BirthdayLook.kt` (`mark = { w -> CakeMark(w) }`), `app/.../shell/ui/HomeScreen.kt` (the pill's `Spacer` becomes `CakeMark(ShellDimens.greetingCake)`).
- Test: new `core/ui/src/test/.../BirthdayArtTest.kt`; `app/src/test/.../shell/ui/NavRailTest.kt`, `GreetingTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Produces: `@Composable fun CakeMark(width: Dp, modifier: Modifier = Modifier)` (ruling 10).

- [ ] **Step 1: The failing tests.**
  - `BirthdayArtTest.theCakeIsAPictureOnly` (`@GraphicsMode(NATIVE)`, pink birthday): no text, no description, hidden from accessibility; pixels exactly `accent`, `weather` and `ICING` present.
  - `NavRailTest.theCakeTakesTheMarksPlace`: `cake_mark` inside `culvery_mark`, whose bounds equal those with no look; `thePumpkinStillShowsForHalloween` (**pin**).
  - `GreetingTest.theIcingShowsOnThePill` (`@GraphicsMode(NATIVE)`): some pixel inside the pill's cake is `ICING`.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*NavRail*' --tests '*Greeting*' --tests '*ThemedClock*'`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Images.** **Changes:** `home_birthday_pink_dark`, `home_birthday_pink_light`, `home_birthday_two_dark`.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Put a cake in the rail and in the greeting while a birthday shows"
```

---

### Task 8: The real birthday event on the tablet (spec §4.1, §5)

**Controller and user only.** Nothing from the tablet is copied, photographed or written down beyond the one line below; no names, no dates.

- [ ] **Step 1 — do this week, needs no build.** With the user, on the tablet's Calendar tab, open the next family birthday's event: its "When" says all day, one day; its Repeats row says every year; it is for the birthday person. If it isn't tagged, the user tags the series there ("Choose who it’s for" on a repeating event changes the series). If it isn't yearly or all-day, the user fixes it in Google Calendar. Use no `adb` dump, `logcat` or screenshot. Record under "Plan 16: on the tablet" in `docs/superpowers/plans/2026-09-23-plan1-followups.md`: "The next family birthday's event was checked as yearly, all-day and tagged." (or what was changed), with no name and no date. Commit only that file: `git commit -m "Record that the next family birthday's event was checked on the tablet" docs/superpowers/plans/2026-09-23-plan1-followups.md`.
- [ ] **Step 2 — optional, after Task 7, if the user wants it.** The release as Plan 14's Task 10 Steps 2–3 (`./gradlew :app:assembleRelease`; `grep -c DebugClock app/build/outputs/mapping/release/mapping.txt` prints 0; the mapping into `culvery-notes/mappings/` named for the commit; `adb -s <tablet> install -r app/build/outputs/apk/release/app-release.apk`; never uninstall). The birthday preview on, seen across the room by day and at night, then off. One line under the same heading, no names, no dates; commit only that file.

---

### Task 9: Bunting across Home's open header band (spec §2.2, §4.2; ruling 11) — third cut

**Review:** sonnet.

**Files:**
- Modify `core/ui/.../BirthdayArt.kt` (`object BirthdayDimens` with the bunting values; `fun DrawScope.drawBunting(band: Rect, string: Color, density: Density)`; `@Composable fun BirthdaySky(drifting: Boolean, modifier: Modifier = Modifier)`, bunting only for now, placed and clipped in `skyBand` as `HalloweenSky`), `BirthdayLook.kt` (`sky = { drifting, m -> BirthdaySky(drifting, m) }`).
- Test: new `core/ui/src/test/.../BirthdaySkyTest.kt`; `app/src/test/.../shell/ui/CulveryShellSkyTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Produces: `BirthdaySky`, tag `birthday_sky`, `clearAndSetSemantics {}`; `internal fun flagCount(bandWidthPx: Float, density: Density): Int`; `internal fun lowestFlagTip(band: Rect, density: Density): Float`.

- [ ] **Step 1: The failing tests.**
  - `BirthdaySkyTest`: `theBuntingStaysInTheBand` (header 1100 × 220 px, clock 400, greeting 420, items 300 → `birthday_sky`'s bounds inside `skyBand(…)`, and `lowestFlagTip(band) <= band.bottom`); `flagsFillTheBand` (280 dp → 10; 83 dp → 0); `theFlagsAreThePersonColours` (`@GraphicsMode(NATIVE)`: the first six flags' centres are `PersonPalette.colors` in order); `theSkyIsSilent`.
  - `CulveryShellSkyTest`: `theBirthdaySkyIsBehindHome`; `noBirthdaySkyOffHome`; `theBatsStillFlyForHalloween` (**pin**).

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*Sky*'`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement** per ruling 11.
- [ ] **Step 3: Images.** **Changes:** `home_birthday_pink_dark`, `home_birthday_pink_light`, `home_birthday_two_dark` (bunting between the date and the weather).
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Hang bunting in the person colours across Home's header while a birthday shows"
```

---

### Task 10: Confetti on each daytime wake (spec §2.3, §3.3; rulings 12, 13; Review Focus 4) — second cut

**Review:** opus.

**Files:**
- Create `core/ui/.../Confetti.kt`, `app/.../display/ui/ConfettiLayer.kt`.
- Modify `app/.../display/DisplayState.kt` (`dayWakes`, `dayWake`), `MainActivity.kt` (a `ConfettiLayer` after `ShellLayers` in the themed `Box`, per ruling 13).
- Test: new `core/ui/src/test/.../ConfettiTest.kt`, `app/src/test/.../display/ui/ConfettiLayerTest.kt`; `app/src/test/.../display/DisplayStateTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Produces:
  - `data class ConfettiPiece(val centre: Offset, val angleDegrees: Float, val colour: Color)`; `fun confettiPieces(seed: Long, elapsedMillis: Long, size: Size, colours: List<Color>, density: Density): List<ConfettiPiece>`.
  - `@Composable fun ConfettiBurst(seed: Long, onDone: () -> Unit, modifier: Modifier = Modifier)`: tag `confetti`; calls `onDone` once at `CONFETTI_MS`.
  - `DisplayState.dayWakes: SharedFlow<Unit>`; `internal fun dayWake(before: Display, after: Display): Boolean`.
  - `@Composable internal fun ConfettiLayer(wakes: Flow<Unit>, awake: Boolean, falls: Boolean, seed: () -> Long)`.

- [ ] **Step 1: The failing tests** (stubs: `dayWake` true on any change to `AWAKE`, `confettiPieces` all at the origin).
  - `ConfettiTest`: `sixtyPieces`; `theyStartAboveTheTop`; `theyAreBelowTheBottomBy2500`; `theColoursAreThePeoplesAndTheAccent`; `theSameSeedFallsTheSameWay`.
  - `DisplayStateTest` (its own harness in `TestDisplay.kt`, driving the real transitions, collecting `dayWakes`):
    - `aTapOnTheDarkScreenByDayIsAWake`: in front by day, idle to `ASLEEP`, `tapped()` → one wake.
    - `theNightClockEndingIsAWake`: the clock past the night's end → one wake.
    - `startingIsNoWake`: a fresh `DisplayState` reaching `AWAKE` in front → none.
    - `leavingAndReturningIsNoWake`: `ASLEEP`, then not in front (→ `AWAKE`), then in front again → none.
    - `aTapOnTheNightClockAtNightIsNoWake`: `NIGHT_CLOCK` at 23:30, `tapped()` → `AWAKE` and no wake.
  - `ConfettiLayerTest` (`mainClock.autoAdvance = false`, a `MutableSharedFlow<Unit>` as wakes): `aWakeDropsABurst` (present at 2 400, gone at 3 000); `noBurstWhenItShouldntFall`; `aBurstStopsWhenTheScreenSleeps` (and doesn't come back on `awake` again); `aBurstStopsWhenARingShows` (`falls` false mid-burst); `aRecompositionKeepsListening` (a host recomposing `falls` and `awake` between wakes still catches the next wake, and a recomposition alone drops nothing); `touchesPassThrough` (a button under it is clicked by a `performTouchInput { click() }` mid-burst); `talkBackFindsNothing`.
  - Screenshot: new `home_birthday_confetti_dark` (pink, 1 000 ms into a burst).

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*Confetti*' --tests '*DisplayState*'`. Expected: FAIL on assertions.
- [ ] **Step 2: Implement** per rulings 12 and 13; the seed is `wallClock.nowMillis()` at the wake.
- [ ] **Step 3: Images.** **New:** `home_birthday_confetti_dark`. **Changes:** none.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Drop confetti for two and a half seconds each time the screen wakes by day on a birthday, never at night, with animations off or while a card or PIN pad shows"
```

---

### Task 11: Balloons drifting up in the band (spec §2.2, §3.4; ruling 11) — first cut

**Review:** opus. **If time runs short, the controller skips this task and records it in the follow-ups.** If Task 9 was cut, this task creates `BirthdaySky` and its wiring as Task 9 describes, without the bunting.

**Files:**
- Modify `core/ui/.../BirthdayArt.kt` (balloon values, `BALLOON_LAP_MS`, `balloonPositions`; `BirthdaySky` gains the balloons and the frame loop), `HalloweenArt.kt` (the frame loop lifted into one `internal` helper both skies call).
- Test: `core/ui/src/test/.../BirthdaySkyTest.kt`, `HalloweenSkyTest.kt` (**pins**); `ShellScreenshotTest`.

**Interfaces:**
- Produces: `fun balloonPositions(elapsedMillis: Long, band: Rect, density: Density): List<Offset>`; the shared `internal` frame-loop helper.

- [ ] **Step 1: The failing tests** (stub: `balloonPositions` returns fixed points).
  - `BirthdaySkyTest`: `balloonsRiseTheBandEveryLap` (a 400 × 200 px band: at 20 000 each y moved by half of `band.height + balloonHeight + balloonString`, mod it; 40 000 equals 0; x within its lane ± `balloonSway`); `theSkyDriftsWhileAsked`, `stillMeansNoFrames`, `aStepIsAboutAFrameAndAWait` (as `HalloweenSkyTest`'s); `aStillSkyStillDrawsTheBalloons` (`@GraphicsMode(NATIVE)`).
  - `HalloweenSkyTest` passes unchanged (**pins**).

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*Sky*'`. Expected: FAIL on assertions, the pins aside.
- [ ] **Step 2: Implement** per ruling 11.
- [ ] **Step 3: Images.** **Changes:** `home_birthday_pink_dark`, `home_birthday_pink_light`, `home_birthday_two_dark` (four faint balloons under the bunting, drawn still).
- [ ] **Step 4: A final look** at the three Home images and `birthday_colours_light` together: anything competing with text is said in the report.
- [ ] **Step 5: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Let balloons drift up through Home's header while a birthday shows, still while the screen is dark, anything is open over Home or animations are off"
```

---

### Task 12: Simplify the whole branch

- [ ] **Step 1:** Run the `simplify` skill over `git diff main...HEAD`. Look hardest at `SeasonalThemes`' flows (one `combine` where two read alike), `CalendarBirthdays` against `CalendarMultiDayEvents`, and the two skies' shared loop. If Tasks 9 and 11 were both cut, remove `BirthdayInks.BALLOON_ALPHA`, `balloon`, `flag`, their contrast test, and `HomeSkyBand.greetingWidth`.
- [ ] **Step 2:** For each change, the module's tests, then the gate.
- [ ] **Step 3:** Commit when anything changed: `git commit -am "Simplify the birthday code"`.

---

### Task 13: The emulator walk-through on `culvery-5` (spec §7)

**Rules:** `culvery-5` only, a debug build, `install -r`; the debug sample household (Alex green and first in People, Sam blue, Mia pink); Claude enters the debug sample PINs; faults are collected and asked about before fixing; no house device touched. Sample events are made in the editor as yearly all-day events and deleted afterwards.

- [ ] **Step 1: Walk** (one line each).
  1. "Sam’s birthday", yearly, all-day today, for Sam → blue look by night and by day (the status bar's Day/Night preview); every tab, a sheet, the PIN pad, a toast, Settings and the door card in the colours.
  2. A second such event for Sam → one greeting. One for Alex too → "Happy birthday Alex and Sam" in Alex's green.
  3. A one-off all-day "Birthday" for Mia, one for Family, and the sample timed "Birthday party" → no look for them.
  4. `DebugClockReceiver` to 31 October with Sam's birthday → the birthday shows; Settings › Door's Play chime with Follow the theme plays the organ.
  5. The preview on with no birthday → Alex's look and the line; off → gone; on, then past midnight → it ends and the switch reads off; Halloween's preview on turns it off.
  6. By day: the dark screen, then a tap → confetti; leaving Culvery and coming back → none. At night: a tap on the night clock → none.
  7. "Remove animations" on → no confetti; the balloons still.
  8. A sample ring as the screen wakes → no confetti over the card.
- [ ] **Step 2: The fault loop**; put back every setting; delete the sample events; `DebugClockReceiver --el minutes 0`; `emu kill`.
- [ ] **Step 3: Record** under "Plan 16 (birthdays): emulator walk-through", no names, no dates. Commit: `git commit -am "Record Plan 16's emulator walk-through"`.

---

### Task 14: Final review, merge, install

- [ ] **Step 1:** Opus reviews `git diff main...HEAD` against the spec and this plan, first at: who counts (yearly, one day, the tag only), midnight, readability in the twelve sets, the doorbell untouched, confetti and balloons stopping, nothing personal in logs or fixtures, the font's licence and hash. One fix wave, each fix with its failing test first, then the gate.
- [ ] **Step 2:** `superpowers:finishing-a-development-branch`: only on the user's say, `git merge --ff-only plan-16-birthdays` on `main`, the gate on `main`; never push. Move the design cuts and any cut task to the follow-ups.
- [ ] **Step 3:** The release from the merged `main` on the tablet with `install -r`, the mapping kept as in Task 8. With the user: the preview on across the room by day and at night, then off; `adb -s <tablet> logcat -d -s HeatLog` over the evening no warmer than before. Record under "Plan 16: on the tablet" with no names and no date; the real day's check is recorded afterwards the same way, saying only that it showed. Commit only that file.

---

## Walk-through

1. On a household member's birthday, kept as a yearly all-day event tagged for them, the wall greets them by name under the date and wears their colour from midnight to midnight.
2. The clock is in Cherry Bomb One in their colour; a cake replaces the mark; bunting and slow balloons fill the open part of Home's header; confetti falls each time the screen wakes by day.
3. Two birthdays: both named, the first person's colour. A birthday beats Halloween; the doorbell still follows Halloween.
4. Settings › Seasonal looks: "Preview a birthday" shows the first person's birthday until midnight.

**Design cuts to say again** (provisional, for the end-of-v1 review):
- the twelve sets' values, pink's darker accent than the mockup's, and the moved warning reds for pink and orange;
- the drawn cake in place of the 🎂 emoji, its cream icing, the pill's size and place, and Home's grid shorter on the day;
- Cherry Bomb One's size if Task 5 shrank it;
- **the bunting and balloons confined to the header's open band** (between the date and the weather), not across all of Home, and their sizes, number, speed and alpha;
- the confetti's count, fall and colours, and that the night clock ending in the morning counts as a wake;
- "Preview a birthday" naming the first person only.
