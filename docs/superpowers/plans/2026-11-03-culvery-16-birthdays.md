# Culvery — Plan 16: Birthdays Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–7 and 9–11 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 8 (an optional early tablet checkpoint) is the controller with the user and the son's SM-T510. Task 12 is the controller. Task 13 is the controller on the `culvery-5` AVD with the user. Task 14 needs the user and the SM-T510.

**Goal:** on a household member's birthday, found in the calendar, the wall greets them by name and dresses in their colour from midnight to midnight: their colour as the accent over a soft tint, a Cherry Bomb One clock, a cake in the rail, bunting and drifting balloons over Home, and confetti each time the screen wakes; Settings › Seasonal looks can preview it on any day.

**Architecture:**
- **`:core:ui`** gains the birthday look beside Plan 14's: `Birthday` (a colour and the names), twelve fixed colour sets (six person colours × night and day) as six `SeasonalLook`s, `birthdayLook(argb)`, and `CulveryTheme(dark, seasonal, birthday)` with `Culvery.birthday`. The clock, rail mark and sky already read `Culvery.look`, so they follow a birthday with no new call sites. Cherry Bomb One, the cake, the bunting, the balloons and the confetti live here.
- **`:core:house`** holds the seam `TodaysBirthdays` (bound optionally, as `MultiDayEvents` is). **`:capability:calendar`** binds it: `HeldEvent` gains `tagged` (the tag's household members, no fallback), `Held.birthdaysOn` applies 16 §3.1, and `CalendarBirthdays` follows the household's date.
- **`:app`** decides what dresses the wall (`WallLooks`: a preview, then a real birthday, then a seasonal theme in its dates), passes it to `CulveryTheme`, keeps the birthday preview in `SeasonalThemes` beside the theme preview so one replaces the other, puts the greeting pill under Home's date, drops the confetti on each wake, and adds the preview row to Settings › Seasonal looks. `ShowingTheme` stays `SeasonalThemes.showing`, so the doorbell follows the seasonal theme only (16 §3.2).

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.** One new asset: Cherry Bomb One (SIL OFL 1.1, to be confirmed from its `OFL.txt`), approved by the user on 2026-11-03, from google/fonts at commit `3dd78844021e948ceb633d1dcee3f7885561b5d9` (2026-03-03; its folder there holds `CherryBombOne-Regular.ttf`, 214 940 bytes, `METADATA.pb`, `OFL.txt`).

**Spec:** `docs/superpowers/specs/2026-11-03-culvery-16-birthdays-design.md`, all of it. Its §2 decisions and §3 calls are settled; this plan doesn't reopen them. Where the code forces a call, the rulings below make it.

**Not this plan** (spec non-goals): no other occasions; no sound, the doorbell unchanged; no house devices; no ages; no birthdays stored in People; no change to Party or Holiday mode; no change to the night clock.

**Previous plans (format, shared parts):** `docs/superpowers/plans/2026-10-29-culvery-14-seasonal-themes.md` (the look, `SlotClock`, Creepster's download in its Task 6, the sky and `skyDrifts`, the preview, the UI-task ending), `docs/superpowers/plans/2026-10-19-culvery-08-sleep-and-wake.md` (`DisplayState`, `Screen`, the night clock).

**Builds on:** branch `plan-16-birthdays` in the one checkout `C:\Users\si\code\siland\culvery`, with the spec at 99a3fe4e. No worktrees. Never push.

**Deadline:** on the SM-T510 by the evening before the next family birthday, about two weeks from 2026-11-03. Tasks are ordered so a usable look lands first: colours (1), finding the birthday (2), the look wired with its precedence (3), the greeting (4), the clock (5), the preview (6), the cake (7); after Task 7 the look is whole enough to install (Task 8). The decorations follow and are the cuts, in this order: **Task 11 (balloons) is the first cut, then Task 10 (confetti), then Task 9 (bunting).** A cut task moves to the follow-ups; nothing before it depends on it.

---

## Global Constraints

**From the spec, verbatim:**
- §1 anti-goals: "Someone is greeted on a day that isn't their birthday, or under the wrong name."; "A person's name or birthday reaches a log, a committed file, a test or a screenshot fixture."; "Text the family reads becomes harder to read, in any of the six person colours, day or night."; "Confetti or balloons keep the screen or CPU busy while the tablet is dark or asleep, or make it run warm."; "The night clock lights the room more than it does today."
- §2.1 "only birthdays tagged for a family member. No tag, no theme. A birthday on the Family row gets nothing."
- §2.6 "two or more birthday events for the same person on the same day are one birthday."
- §3.1 "Only the tag counts. An untagged event never falls back to the calendar it came from" … "Timed events don't count".
- §3.2 "A birthday's look beats a seasonal theme on the same day" … "The doorbell's *Follow the theme* keeps following the seasonal theme alone. A birthday never changes the doorbell." … "The colours, clock and cake take the first of them in People order." … "The night clock is unchanged."
- §3.3 "Starting the app does not count." "Confetti doesn't fall when animations are off (`AnimatorsOn`), or while the door card, a PIN pad or any alert is showing. It falls over whatever screen is showing, never takes a touch, and is skipped by TalkBack." "About 60 pieces, gone by 3 seconds."
- §3.5 "a preview (seasonal or birthday) beats a real birthday, which beats a seasonal theme in its dates." "turning one preview on turns any other off."
- §3.6 "No name, title, date or person id in any log line." "Tests and screenshots use made-up names".
- §4.2 the colour sets are "worked out ahead in code, not computed at run time from arbitrary colours"; "`danger` must stay clearly apart from `accent` in every set".
- §4.2 "Drawings, our own vector paths in code, nothing downloaded".

**Copy** (curly apostrophes and "–" in code; each owning task tests its strings):
- Home's greeting: "Happy birthday {names}", where names are "Sam", "Sam and Alex", "Sam, Alex and Mia" (four: "Sam, Alex, Mia and Jo"), in People order, after a drawn cake. TalkBack reads "Happy birthday Sam".
- Settings › Seasonal looks, section "Seasonal themes", after the themes' rows: a switch row "Preview a birthday" (plan) with the line "Shows {name}’s birthday on the wall until midnight, with confetti each time the screen wakes." (plan) when off and "Showing now. It ends at midnight." when on.
- Log lines (plan; `Log.i`, `TAG = "WallLooks"`): "Birthday look on for {count} people" and "Birthday look off". Nothing else about birthdays is logged anywhere.

**From the house rules** (binding):
- **Platform:** package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only; **no new library; never change a version**; no deprecated API, and any one met is flagged to the user before use. This plan's APIs were checked and are current: `withFrameMillis`, `mutableLongStateOf`, `Modifier.graphicsLayer`, `drawWithCache`, `rememberTextMeasurer`, `ImageVector.Builder.addPath`, `Regex(…, RegexOption.IGNORE_CASE)`, `ValueAnimator.areAnimatorsEnabled()`, `Flow.runningFold`. Never `Settings.System.ANIMATOR_DURATION_SCALE`.
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash, or `gradlew.bat`), plus `./gradlew -p build-logic :convention:test` in any task that adds a log line, a `TAG` or a module edge.
  - Never plain `test`, `check` or `build`, and never a Gradle `install…` task. No subagent touches a device.
  - **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`**: a long temp path breaks Robolectric past MAX_PATH.
  - Subagents' scratch goes in `$LISTS` = `C:/t9`, a plain shell variable, never exported.
- **Tests that can fail:** every new test fails before its change; one that passes before it is labelled **pin**. A logic test goes red on its assertion, not only on compiling: Step 1 adds the production function with its final signature returning a wrong value (`null`, `false`, `emptyList()`, the normal colours), runs the tests and sees them fail on assertions; then the body is written. Timer and frame tests run on `compose.mainClock` with `autoAdvance = false` or on virtual time (`StandardTestDispatcher`), asserted one step before the boundary and at it. A fake clock is a `MutableStateFlow<LocalDateTime?>`.
- **Every UI task ends as Plan 14's:** `compareRoborazziDebug` on the module and a look at each `*_compare.png`; `recordRoborazziDebug`; the baseline check with `$LISTS/expected-baselines.txt` (the task's **New** and **Changes** names) and `$LISTS/expected-prefixes.txt` (the task's prefixes, e.g. `home_birthday_`), and Plan 14's four `comm`/`grep` lines, whose first two lists must be empty; the gate; commit. Baselines live in `<module>/src/test/screenshots/`. New birthday screens are recorded `_dark` and `_light`. `CopyScanTest` passes; every screen test ends with `compose.assertClickablesHaveRoleAndLabel()`.
- **Code:** call sites read the look (`Culvery.look`) or `Culvery.birthday`, never a colour by name. Comments say only what the code can't, one line by default, spec refs as `(16 §3.1)`, never a task or ruling number. Layout and drawing numbers go in a named object (`BirthdayDimens` in `:core:ui`, `ShellDimens` in `:app`).
- **Privacy:** no names, titles, dates or person ids in logs, test names or committed files. Tests and screenshots use "Sam", "Alex", "Mia" (and "Jo" for four) only; never a real family name. `Birthday.toString()` leaves out the names.
- **Devices:** the emulator is `culvery-5`; `emulator-5554` holds a signed release and the household: never install on it. The tablet (SM-T510) gets `adb install -r` of a signed release only; **never uninstall, never wipe**. No Hue, Sonos, plug or Home Assistant change.
- **Git:** a commit is one plain sentence with no prefix and **no trailer of any kind** (no Co-Authored-By, no Signed-off-by); never push. **Never** `git checkout -- .`, `git restore .` or `git stash`; one checkout, no worktrees.

## Review Focus

1. **The wrong person greeted, or someone greeted on the wrong day.**
   - **Inputs:** "Grandma’s birthday" untagged in Sam's own calendar; a birthday tagged Family; a tag naming an id no longer in People; a timed "Birthday party" tagged Sam; "bday", "Birthdayparty"; yesterday's or tomorrow's all-day birthday; a queued edit that moves the tag; the household's date turning at midnight with no restart.
   - **Expected:** only an all-day event over today whose tag names a current household member greets, and only that member; the greeting goes at the first minute of the next day.
   - **Tests** (Task 2): `BirthdaysOnTest.anUntaggedBirthdayInSamsOwnCalendarGreetsNobody`, `aFamilyBirthdayGreetsNobody`, `anUnknownIdIsIgnored`, `aTimedBirthdayPartyDoesntCount`, `onlyTheWholeWordCounts`, `onlyTodayCounts`; `CalendarBirthdaysTest.aBirthdayEndsAtMidnight`, `nobodyBeforeTheClockIsRead`.
2. **Two looks wanting the same day.**
   - **Inputs:** a birthday on 31 October; Halloween's preview on during a real birthday; the birthday preview on during Halloween's dates; the birthday preview turned on while Halloween's is on; a ring during each.
   - **Expected:** a preview beats a real birthday, which beats Halloween by date; one preview at a time; the doorbell plays what Halloween's dates or Halloween's preview say, never anything a birthday says.
   - **Tests** (Task 3): `WallLooksTest.aBirthdayBeatsHalloween`, `aThemePreviewBeatsABirthday`, `aBirthdayPreviewBeatsHalloweenByDate`, `previewsReplaceEachOther`, `theDoorbellFollowsOnlyTheSeason`.
3. **Text over the twelve new colour sets.**
   - **Inputs:** every pair `ContrastTest` checks, in each of the twelve sets; warning red beside pink and orange; the greeting pill on `bg`; a person whose stored colour isn't one of the six.
   - **Expected:** every pair passes the 4d thresholds; `danger` at least 35° from `accent`; the pill visible on `bg`; the accent stays the person's hue; an unknown colour takes the nearest of the six.
   - **Tests** (Task 1): `ContrastTest` over sixteen sets, `dangerAndAccentStayApart`, `theGreetingPillStandsOffTheBackground`, `eachBirthdayAccentKeepsThePersonsHue`; `BirthdayLookTest.aColourOutsideTheSixTakesTheNearest`.
4. **Confetti when nobody should see it, or in the way.**
   - **Inputs:** the app starting; waking with "Remove animations" on; a ring's card or the PIN pad up as the screen wakes; the screen going dark, or a ring arriving, mid-burst; a tap on a button under the confetti; TalkBack on.
   - **Expected:** no confetti on start, with animations off, or with a card or PIN pad up; a burst ends at once when either appears or the screen leaves awake; touches reach what is under it; TalkBack finds nothing.
   - **Tests** (Task 10): `ConfettiCueTest.onlyAWakeFromDarkOrTheNightClockCounts`, `confettiFallsOnlyWhenNothingIsUp`; `ConfettiLayerTest.aBurstStopsWhenTheScreenSleeps`, `aBurstStopsWhenARingShows`, `touchesPassThrough`, `talkBackFindsNothing`, `aBurstIsGoneByThreeSeconds`.
5. **The header moving or crowding on the day.**
   - **Inputs:** the clock in Cherry Bomb One from 11:11 to 00:00; the look switching on at midnight; three long names in the greeting ("Alexandra, Maximilian and Wilhelmina"); the bunting and balloons in the header's open band.
   - **Expected:** the clock's box keeps its width and height, nothing of it draws into the date; the greeting wraps within the clock-and-date column and never runs under the header items; the header items keep their place; the decorations stay inside the open band.
   - **Tests:** (Task 5) `BirthdayClockTest.theClockKeepsItsWidthEveryMinute`, `theHeaderKeepsItsHeightInEitherFace`, `theClockDrawsNothingIntoTheDate`; (Task 4) `GreetingTest.aLongGreetingWrapsClearOfTheItems`, `theHeaderItemsDontMove`; (Task 9) `BirthdaySkyTest.theBuntingStaysInTheBand`.

---

| # | Task | Review | Cut |
|---|---|---|---|
| 1 | The birthday look and its twelve colour sets in `:core:ui`; contrast over sixteen sets | **opus** (readability) | — |
| 2 | Finding today's birthdays: `tagged`, `birthdaysOn`, the `TodaysBirthdays` seam bound by the calendar | **opus** (wrong person) | — |
| 3 | What dresses the wall: `WallLooks`, the birthday preview, the app wired; night clock and doorbell pinned | **opus** (precedence, midnight) | — |
| 4 | The greeting pill under Home's date | sonnet | — |
| 5 | Cherry Bomb One and the birthday clock face | sonnet | — |
| 6 | Settings › Seasonal looks: "Preview a birthday" | sonnet | — |
| 7 | The cake in the rail | sonnet | — |
| 8 | *Optional* early tablet checkpoint | controller + user, **needs the SM-T510** | — |
| 9 | Bunting across Home's open header band | sonnet | third cut |
| 10 | Confetti on each wake | **opus** (battery, touches) | second cut |
| 11 | Balloons drifting up behind Home | **opus** (battery, heat) | **first cut** |
| 12 | Simplify the whole branch | controller | — |
| 13 | The emulator walk-through on `culvery-5` | controller + user | — |
| 14 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** | — |

**Order:** the colours and the look type (1) come first because everything reads them; finding the birthday (2) is the anti-goal that matters most; Task 3 makes a real birthday or the preview show on the wall, so from a build of Task 3 the look is visible. The greeting (4) and clock (5) are what is seen across the room; the preview (6) lets the user check it on any day; the cake (7) completes the "look". The decorations (9–11) are independent of each other except that balloons extend the sky Task 9 creates; if Task 9 is cut, Task 11 creates `BirthdaySky` itself.

## Rulings against the code

The spec leaves these to the builder (§6), or the code forces them. Each is this plan's call.

1. **The tag, not `HeldEvent.people`** (`Held.kt`). `HeldEvent.people` is already resolved with `resolveIds`' fallback to the source calendar's person, so it can't tell "tagged Sam" from "untagged in Sam's calendar". `HeldEvent` gains `val tagged: List<PersonId> = emptyList()` (last, defaulted, so the twelve existing constructions stand): `peopleIn(forPerson)` less Family and less ids not in People, in People order. `toHeld` fills it.
2. **The rule** (`capability/calendar/.../Birthdays.kt`): `BIRTHDAY_WORD = Regex("""\bbirthdays?\b""", RegexOption.IGNORE_CASE)`. An event counts when `start` and `end` are both `EventTime.AllDay`, `daySpan(start, end, zone)` holds the date, and the title matches. A multi-day all-day birthday counts on each of its days ("covers today"). An all-day "Sam’s birthday party" tagged Sam counts; a timed one doesn't. The result is the counting events' `tagged`, distinct, in People order.
3. **The seam** (`core/house/.../HouseSeams.kt`): `interface TodaysBirthdays { val today: StateFlow<List<Person>> }`, `@BindsOptionalOf` in `HouseModule` as `MultiDayEvents` is, bound by `CalendarBirthdays` in `CalendarModule`. It reads `CalendarRepository.held(today)` so hidden calendars and queued edits count as they do on screen; held events from the last sync still count when today's sync hasn't run (16 §4.1). `held()` becomes always collected while the app runs (one more collector over 12 weeks of events on each store change, off Main); the tablet task looks at `HeatLog`.
4. **The look type** (`core/ui/.../Birthday.kt`):
   ```kotlin
   /** A birthday on the wall: the first person's [colour] and every birthday person's name, in People order (16 §3.2). */
   data class Birthday(val colour: Long, val names: List<String>) {
       override fun toString() = "Birthday(${names.size})"
   }
   ```
   `fun birthdayLook(colour: Long): SeasonalLook` returns one of six fixed `SeasonalLook`s, one per `PersonPalette.colors` entry, by exact match, else the nearest by RGB distance. `colorsFor(look: SeasonalLook?, dark: Boolean)` replaces the body of `colorsFor(seasonal, dark)`, which delegates to it. `CulveryTheme(dark, seasonal = null, birthday = null, content)`: the look is `birthday?.let { birthdayLook(it.colour) } ?: seasonal?.look`; a new `LocalLook` carries it and `Culvery.look` reads `LocalLook`; `LocalBirthday` carries `birthday` and `Culvery.birthday` reads it. `LocalSeasonalTheme` stays as given. The app never passes both (ruling 7); if both arrive, the birthday wins. A birthday look is `SeasonalLook(night, day, clockFace = CherryBombClock /* Task 5 */, mark = CakeMark /* 7 */, sky = BirthdaySky /* 9, 11 */)` with no `cardCorner` and no `sound`.
5. **The twelve colour sets** (`core/ui/.../BirthdayLook.kt`), worked out by a script against the `ContrastTest` pairs and `hueGap`; the test decides, and the implementer moves any value a test fails by the smallest step that passes and says which. Each set copies `LightColors` (day) or `DarkColors` (night) and changes only these tokens; night `accentInk` is `0xFF0E1011` throughout, day `accentInk` stays `0xFFFFFFFF`:

   | colour (`PersonPalette`) | set | bg | surf2 | line | accent | accentOnInk | accentSoft | danger |
   |---|---|---|---|---|---|---|---|---|
   | Green 0xFF4CB387 | day | E7F0EC | E7EFEB | BBD6CA | 2F6F54 | 4CB387 | C9E8DB | — |
   | | night | 111817 | 263030 | 232F2C | 4CB387 | 2F6F54 | 22342E | — |
   | Blue 0xFF5B9BE0 | day | E8EEF4 | E8EDF2 | BED1DE | 2165AF | 5B9BE0 | D1E3F6 | — |
   | | night | 12171B | 272F35 | 242D33 | 5B9BE0 | 2165AF | 24303B | — |
   | Pink 0xFFE07BA8 | day | F4EBEF | F2EBEE | DBCAD1 | B42A68 | E07BA8 | F6D9E6 | B8461A |
   | | night | 181519 | 2F2D32 | 2E2A2F | E07BA8 | B42A68 | 382B33 | EE8A5A |
   | Lime 0xFF9CC44E | day | EEF2E7 | EDF0E7 | CCDABE | 546D24 | 9CC44E | DAE9BE | — |
   | | night | 151914 | 2B312C | 293027 | 9CC44E | 546D24 | 2E3625 | — |
   | Orange 0xFFE04A26 | day | F4EAE7 | F5ECEA | DBBFB5 | B3371A | E04A26 | F8D9D1 | A8236A |
   | | night | 181312 | 2F2A2A | 2E2624 | E66E51 | B3371A | 38241F | EE6FB0 |
   | Yellow 0xFFCEC018 | day | F2F1E2 | F0EFE4 | D7D9B2 | 6D660D | CEC018 | ECE6A5 | — |
   | | night | 181911 | 2E3129 | 2D3023 | CEC018 | 6D660D | 35351D | — |

   (All `0xFF`-prefixed; "—" keeps the base set's.) The spec's mockup pink accent `#C2457E` is 4.05:1 on its own `bg`, under 4.5, so the pink day accent is the darker `B42A68` of the same hue. Pink's `danger` moves toward orange-red and orange's toward a crimson-magenta, each at least 42° from its accent; the rest keep today's `danger`.
6. **Which person a look takes:** the colours, clock and cake take `birthdays.first()` in People order (`HouseholdRepository.people`' order); the greeting names them all.
7. **What dresses the wall** (`app/.../display/WallLooks.kt`):
   ```kotlin
   /** At most one of the two is set (16 §3.5). */
   data class WallDress(val seasonal: SeasonalTheme? = null, val birthday: Birthday? = null)
   ```
   `internal fun dressOn(themePreview: SeasonalTheme?, birthdayPreview: Person?, birthdays: List<Person>, seasonal: SeasonalTheme?): WallDress`: a theme preview → that theme; else a birthday preview → `Birthday(person.color, listOf(person.name))`; else birthdays → `Birthday(first.color, all names)`; else `seasonal` (the theme by its dates). The birthday preview lives in `SeasonalThemes` beside the theme preview (`MutableStateFlow<LocalDate?>`, in memory only, counted only on the day it was turned on), so turning either on clears the other; `SeasonalThemes.showing` (what `ShowingTheme` gives the doorbell) is unchanged by a birthday preview or a birthday.
8. **The greeting pill** (`HomeHeader`, a fourth child placed under the date): `ShellDimens.greetingGap = 12.dp` below the date; a `Row` with `accentSoft` fill, a fully rounded shape, `greetingPaddingH = 14.dp`, `greetingPaddingV = 6.dp`; a `CakeMark(greetingCake = 22.dp)` (Task 7; until then a `Spacer` of that width), `greetingCakeGap = 8.dp`, then the text in `HhType.rowTitle`, `ink`. **A drawn cake replaces the "🎂" emoji**: the emoji would draw from the system's emoji font (tofu in Robolectric's baselines) and TalkBack would read it; ours is the rail's cake, hidden from TalkBack. The `Row` carries `clearAndSetSemantics { text = AnnotatedString(greeting) }`, tag `home_greeting`, `traversalIndex = 1.5f`. Its width is at most the header width less the items' width and `HEADER_ITEM_GAP`; its text wraps (`maxLines = 2`). The header items keep their bottom at the date's; the header grows by the pill on a birthday, so Home's grid is that much shorter that day. `HomeSkyBand` gains `greetingWidth`, and `skyBand` takes the widest of clock, date and greeting.
9. **The clock face:** Cherry Bomb One at the clock's 100 sp, `lineBox(86.sp)`, no letter spacing, in `accent`, through Plan 14's `SlotClock`. If the date test fails, the implementer shrinks `CherryBombClock`'s `fontSize` by whole sp until it passes and says the size.
10. **The cake** (`CakeMark(width)`): an `ImageVector` in code with `CulveryMark`'s 54 × 49 viewport; two tiers in `accent`, icing drips and plate in `accentSoft`, one candle in `ink` with a flame in `weather`. Tag `cake_mark`; `contentDescription = null`; `hideFromAccessibility()`. Its paths are the implementer's.
11. **The sky** (`BirthdaySky(drifting, modifier)`, in Plan 14's open header band: `skyBand(LocalHomeSkyBand.current, …)`, which leaves out the clock, date, greeting and header items, so no text ever sits over a decoration; the spec's "behind Home's content" is met there, as the bats' sky was):
    - **Bunting** (static): a string in `mute`, 1.5 dp, from the band's left to right edge `buntingTop = 6.dp` down, sagging `buntingSag = 16.dp` at the middle (a quadratic); triangular flags `flagWidth = 18.dp` × `flagHeight = 22.dp` every `flagPitch = 28.dp`, hanging from the string, in `PersonPalette.colors` order, cycling. A band narrower than three pitches gets no bunting.
    - **Balloons:** `BALLOONS = 4`, each `balloonWidth = 30.dp` × `balloonHeight = 38.dp` with a `balloonString = 26.dp` string in `mute`; lane `i` centred at `band.left + band.width × (i + 0.5) / 4`, a quarter lap apart; each rises from just below the band's bottom to just above its top once every `BALLOON_LAP_MS = 40_000L`, swaying `balloonSway = 6.dp` (one sine period per lap); colours `PersonPalette.colors[i]` at `BirthdayInks.BALLOON_ALPHA = 0.85f`; drawn before the bunting. The drift reuses Plan 14's frame step (`SKY_FRAME_MS`, `graphicsLayer`, elapsed read only in the draw lambda) and `skyDrifts`.
    - **Inks** (`BirthdayInks`, so `ContrastTest` checks them): `balloon(argb, c)` = the colour at `BALLOON_ALPHA` over `c.bg`; `flag(argb)` = the colour; each stands off `bg` at `SKY_MIN` (1.2) in each birthday set.
12. **Confetti** (`core/ui/.../Confetti.kt`): `CONFETTI_PIECES = 60`; each a `confettiSize = 8.dp` × 12 dp rectangle, colour from `PersonPalette.colors + accent`; seeded start x across the width and y between `−height × 0.3` and 0; fall so every piece is below the bottom by `CONFETTI_MS = 2_500L`, with a spin and a sideways drift the implementer picks; the layer leaves at `CONFETTI_GONE_MS = 3_000L` at the latest. Drawn every frame (`withFrameMillis`) for its short life, in its own `graphicsLayer`, with no pointer input and `clearAndSetSemantics {}`.
13. **When confetti falls** (`app/.../display/ConfettiCue.kt`): `fun Flow<Screen>.wakes(): Flow<Unit>` emits when a value is `AWAKE` and the one before was `ASLEEP` or `NIGHT_CLOCK`; the first value never emits (starting the app). `internal fun confettiFalls(birthday: Boolean, animatorsOn: Boolean, pinShowing: Boolean, alertShowing: Boolean): Boolean` = `birthday && animatorsOn && !pinShowing && !alertShowing`. The door card is an alert (`DoorAlerts` shows it through `ShellAlerts`), so `alertShowing` covers it. A sheet or Settings doesn't stop it (the spec names only the card, the PIN pad and alerts). A burst runs only while `screen == AWAKE` and `confettiFalls(…)` stay true; either turning false ends it, and it doesn't come back until the next wake.
14. **The preview row:** on `SeasonalLooksScreen`, under "Seasonal themes", after the themes' rows. Plan 15b moved the spec's "Settings › Display, Seasonal themes" to App settings › Seasonal looks, section "Seasonal themes"; the row goes there. It names the first person in People order; with nobody in People the row isn't shown.

## File Structure

```
core/ui/src/main/java/uk/co/siland/culvery/core/ui/
  Birthday.kt (Birthday, birthdayGreeting, LocalBirthday, LocalLook), BirthdayLook.kt (twelve sets,
    six looks, birthdayLook, BirthdayInks), Seasonal.kt (colorsFor(look, dark)), Theme.kt              (Task 1)
  ClockFace.kt (CherryBombOne, CherryBombClock); res/font/cherry_bomb_one.ttf;
    licenses/OFL-CherryBombOne.txt                                                                  (Task 5)
  BirthdayArt.kt (CakeMark)                                                                         (Task 7)
  BirthdayArt.kt (BirthdaySky, drawBunting, BirthdayDimens), Seasonal.kt (HomeSkyBand)               (Tasks 4, 9)
  Confetti.kt (ConfettiBurst, confettiPieces)                                                       (Task 10)
  BirthdayArt.kt (balloonPositions)                                                                 (Task 11)
core/ui testFixtures Screenshots.kt (snap's birthday)                                               (Task 1)
core/house: HouseSeams.kt (TodaysBirthdays), di/HouseModule.kt                                      (Task 2)
capability/calendar: Held.kt (tagged), Birthdays.kt (birthdaysOn, CalendarBirthdays), di/CalendarModule.kt (Task 2)
app: display/WallLooks.kt, display/SeasonalThemes.kt (birthday preview), MainActivity.kt,
    display/di/DisplayModule.kt, src/debug di/HouseGraphProbe.kt                                    (Tasks 2, 3)
  shell/ui/HomeScreen.kt, shell/ui/ShellDimens.kt                                                   (Task 4)
  display/DisplayWords.kt, display/ui/DisplaySettingsPage.kt                                        (Task 6)
  display/ConfettiCue.kt, display/ui/ConfettiLayer.kt, MainActivity.kt                              (Task 10)
README.md (licence line)                                                                            (Task 5)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                                (Tasks 8, 13, 14)
```

---

### Task 1: The birthday look and its twelve colour sets (spec §3.2, §4.2; rulings 4–6; Review Focus 3)

**Review:** opus. Every pair in every set; warning red apart from pink and orange; the look as the one description.

**Files:**
- Create `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Birthday.kt`, `BirthdayLook.kt`.
- Modify `core/ui/.../Seasonal.kt` (`colorsFor(look, dark)`; the old signature delegates), `Theme.kt` (`CulveryTheme(dark, seasonal, birthday)`, `LocalLook`, `LocalBirthday`, `Culvery.birthday`; `Culvery.look` reads `LocalLook`).
- Modify `core/ui/src/testFixtures/.../test/Screenshots.kt` (`snap(…, seasonal, birthday: Birthday? = null, content)`).
- Test: `core/ui/src/test/.../ContrastTest.kt`, `ThemeTest.kt`, new `BirthdayLookTest.kt`; `capability/door/src/test/.../ui/DoorCardScreenshotTest.kt` (its local snap gains `birthday`).

**Interfaces:**
- Produces (public in `uk.co.siland.culvery.core.ui`):
  - `data class Birthday(val colour: Long, val names: List<String>)` (ruling 4); `fun birthdayGreeting(names: List<String>): String` ("Happy birthday Sam", "… Sam and Alex", "… Sam, Alex and Mia").
  - `val BirthdayNight: Map<Long, HhColors>`, `val BirthdayDay: Map<Long, HhColors>` keyed by `PersonPalette.colors`; `fun birthdayLook(colour: Long): SeasonalLook`.
  - `fun colorsFor(look: SeasonalLook?, dark: Boolean): HhColors`.
  - `@Composable fun CulveryTheme(dark: Boolean, seasonal: SeasonalTheme? = null, birthday: Birthday? = null, content: @Composable () -> Unit)`; `Culvery.birthday: Birthday?`.
  - `object BirthdayInks { const val BALLOON_ALPHA = 0.85f; fun balloon(argb: Long, c: HhColors): Color; fun flag(argb: Long): Color }` (used from Task 9; defined here so the contrast tests own it).

- [ ] **Step 1: The failing tests** (stubs: `birthdayLook` returns a look with `DarkColors`/`LightColors`, `birthdayGreeting` returns "").
  - `ContrastTest`: `themes` becomes `"normal"`, each seasonal theme, and each birthday look named `"{PersonPalette name} birthday"`, × night and day (sixteen sets); every existing loop covers them with no rule changed; the pinned edges stay on `LightColors`/`DarkColors`. Add:
    - `theGreetingPillStandsOffTheBackground`: each birthday set, `contrast(accentSoft, bg) >= QUIET_FILL_MIN`.
    - `eachBirthdayAccentKeepsThePersonsHue`: each birthday set, `hueGap(accent, Color(personColour)) <= 12f`.
    - `balloonsAndFlagsStandOffTheBackground`: each birthday set × each person colour, `BirthdayInks.balloon(argb, t)` and `flag(argb)` on `t.bg` at `SKY_MIN`.
  - `ThemeTest`:
    - `aBirthdayProvidesItsColourSets`: `CulveryTheme(dark = true, birthday = Birthday(0xFFE07BA8, listOf("Sam")))` → `Culvery.colors == BirthdayNight.getValue(0xFFE07BA8)`, `Culvery.look === birthdayLook(0xFFE07BA8)`, `Culvery.birthday?.names == listOf("Sam")`; `dark = false` → the day set.
    - `aBirthdayWinsOverATheme`: `seasonal = HALLOWEEN` and a birthday → the birthday's colours and look.
    - `noBirthdayIsAsBefore` (**pin**): with `birthday` absent, Halloween's and the normal sets are as today and `Culvery.birthday == null`.
  - `BirthdayLookTest`:
    - `everyPersonColourHasBothSets`: `BirthdayNight.keys == BirthdayDay.keys == PersonPalette.colors.toSet()`; each night set not light, each day set light; `weather`, `rain`, `ink`, `mute`, `surf`, `surf3`, `border` equal the base set's.
    - `aColourOutsideTheSixTakesTheNearest`: `birthdayLook(0xFFE07BA9) === birthdayLook(0xFFE07BA8)`; `birthdayLook(0xFF9B7BE0)` (Family's violet) is the look of whichever of the six is nearest by RGB distance (computed in the test).
    - `theLookHasNoCornerAndNoSound`: each look's `cardCorner` and `sound` are null.
    - `theGreetingNamesEveryone`: `birthdayGreeting(listOf("Sam")) == "Happy birthday Sam"`; two → "Happy birthday Sam and Alex"; three → "Happy birthday Sam, Alex and Mia"; four → "Happy birthday Sam, Alex, Mia and Jo".
    - `aBirthdayPrintsNoName`: `Birthday(0xFF5B9BE0, listOf("Sam")).toString()` doesn't contain "Sam".
  - Screenshots (each `_dark` and `_light`): `door_card_birthday_pink`, `door_card_birthday_lime` (the `door_card` sample; pink is the mockup, lime the hardest by day).

  Run `./gradlew :core:ui:testDebugUnitTest`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement** per rulings 4–6. The scheme's `remember` keys stay `(dark, colors)`.
- [ ] **Step 3: Images.** **New:** `door_card_birthday_pink_dark`, `door_card_birthday_pink_light`, `door_card_birthday_lime_dark`, `door_card_birthday_lime_light` (check: the person's colour on the buttons, a faint tint behind, text as clear as the normal images). **Changes:** none.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui capability/door/src/test
git commit -m "Add a birthday look for each of the six person colours, with day and night colour sets checked by the same contrast tests as every other look"
```

---

### Task 2: Finding today's birthdays (spec §2.1, §2.6, §3.1, §4.1; rulings 1–3; Review Focus 1)

**Review:** opus. Nobody greeted on a guess; midnight; nothing personal logged.

**Files:**
- Modify `core/house/.../HouseSeams.kt` (`TodaysBirthdays`), `core/house/.../di/HouseModule.kt` (`@BindsOptionalOf abstract fun todaysBirthdays(): TodaysBirthdays`).
- Modify `capability/calendar/.../Held.kt` (`HeldEvent.tagged`, `toHeld` fills it); create `capability/calendar/.../Birthdays.kt` (`BIRTHDAY_WORD`, `Held.birthdaysOn`, `CalendarBirthdays`); modify `di/CalendarModule.kt` (`@Binds abstract fun todaysBirthdays(impl: CalendarBirthdays): TodaysBirthdays`).
- Modify `app/src/debug/.../di/HouseGraphProbe.kt` (`todaysBirthdays(): Optional<TodaysBirthdays>`).
- Test: `capability/calendar/src/test/.../HeldTest.kt`, new `BirthdaysOnTest.kt`, new `CalendarBirthdaysTest.kt`; `app/src/test/.../HouseGraphTest.kt`.

**Interfaces:**
- Produces:
  - `interface TodaysBirthdays { /** Today's birthday people in People order; empty when none (16 §3.1). Holds names: never logged. */ val today: StateFlow<List<Person>> }`.
  - `HeldEvent(…, val tagged: List<PersonId> = emptyList())`.
  - `fun Held.birthdaysOn(date: LocalDate, order: List<PersonId>, zone: ZoneId): List<PersonId>` (ruling 2).
  - `@Singleton class CalendarBirthdays internal constructor(now: StateFlow<LocalDateTime?>, held: (LocalDate) -> Flow<Held>, people: Flow<List<Person>>, zone: Flow<ZoneId>, scope: CoroutineScope) : TodaysBirthdays`, `@Inject constructor(clock: HouseholdClock, repo: CalendarRepository, household: HouseholdRepository, zone: HouseholdZone, @ApplicationScope scope: CoroutineScope)`. `today` is `stateIn(scope, Eagerly, emptyList())`, re-read when the household's date, the held events, People or the zone change; empty while the clock is unread.

- [ ] **Step 1: The failing tests** (stubs: `birthdaysOn` returns `emptyList()`, `tagged` left empty).
  - `HeldTest.taggedIsTheTagsPeopleOnly`: `StoredEvent(forPerson = null, sourcePerson = sam).toHeld(…)` → `people == [sam]`, `tagged == []`; `forPerson = "family"` → `tagged == []`; `"ghost,sam"` → `[sam]`; `"sam,alex"` with People order Alex, Sam → `[alex, sam]`.
  - `BirthdaysOnTest` (2026-03-12, order Alex, Sam, Mia, `ZoneId.of("Europe/London")`; helper `allDay(title, tagged, first, last = first)`, `timed(title, tagged, at)`):
    - `anAllDayBirthdayTaggedForSamIsSams`: "Sam’s birthday", `[sam]` → `[sam]`.
    - `onlyTheWholeWordCounts`: "Birthday", "birthday!", "BIRTHDAYS", "Sam’s birthday" count; "bday", "Birthdayparty", "Rebirthdayed" don't.
    - `anUntaggedBirthdayInSamsOwnCalendarGreetsNobody`: `people = [sam]`, `tagged = []` → `[]`.
    - `aFamilyBirthdayGreetsNobody`: `people = [FAMILY]`, `tagged = []` → `[]`.
    - `anUnknownIdIsIgnored`: an event whose `tagged` was built from "ghost" → `[]` (through `toHeld`).
    - `aTimedBirthdayPartyDoesntCount`: timed "Sam’s birthday party" at 14:00, `[sam]` → `[]`.
    - `onlyTodayCounts`: the same event on 11 or 13 March → `[]`; a three-day all-day "Birthday weekend" 11–13 March → `[sam]`.
    - `twoEventsForSamAreOneBirthday`: two "Sam’s birthday" → `[sam]`.
    - `aSharedTagIsEachPersonsBirthday`: one event `[sam, alex]` plus one `[mia]` → `[alex, sam, mia]`.
  - `CalendarBirthdaysTest` (fake `now`, a `MutableStateFlow<Held>` per day from the `held` lambda, People Sam (pink), Alex (blue); `runTest` with `backgroundScope`):
    - `samsBirthdayIsSam`: 2026-03-12 09:00 with Sam's birthday held → `today == [Sam]` (the `Person`, name and colour).
    - `aBirthdayEndsAtMidnight`: 23:59 → `[Sam]`; 2026-03-13 00:00 → `[]`, with the 13th's `held` asked for.
    - `nobodyBeforeTheClockIsRead`: `now` null → `[]`, and `held` never asked.
    - `aRenameShowsAtOnce`: People changes Sam's name → `today`'s name follows (no restart).
  - `HouseGraphTest.theCalendarFindsTheBirthdays`: `todaysBirthdays()` is present and is a `CalendarBirthdays`.

  Run `./gradlew :capability:calendar:testDebugUnitTest :app:testDebugUnitTest --tests '*HouseGraph*'` and `./gradlew -p build-logic :convention:test`. Expected: FAIL on assertions.
- [ ] **Step 2: Implement** per rulings 1–3. No log line here.
- [ ] **Step 3: The gate, then commit.** No screenshot changes.
```bash
git add core/house capability/calendar app/src/debug app/src/test
git commit -m "Find today's birthdays in the calendar from all-day events tagged for a household member, never from the calendar an event came from"
```

---

### Task 3: What dresses the wall (spec §3.2, §3.5, §4.1; rulings 6, 7; Review Focus 2)

**Review:** opus. Precedence, midnight, the doorbell untouched, the night clock untouched.

**Files:**
- Create `app/src/main/java/uk/co/siland/culvery/display/WallLooks.kt`.
- Modify `app/.../display/SeasonalThemes.kt` (the birthday preview; `preview(theme, true)` clears it), `MainActivity.kt` (`@Inject lateinit var looks: WallLooks`; `CulveryTheme(dark, seasonal = dress.seasonal, birthday = dress.birthday)`), `app/src/debug/.../di/HouseGraphProbe.kt` (`wallLooks(): WallLooks`).
- Test: new `app/src/test/.../display/WallLooksTest.kt`; `display/SeasonalThemesTest.kt`; `display/ui/SleepLayersTest.kt`, `SleepLayersScreenshotTest.kt`; `shell/ui/ShellScreenshotTest.kt` (`snap` gains `birthday`).

**Interfaces:**
- Consumes: Task 1's `Birthday`, `CulveryTheme(dark, seasonal, birthday)`; Task 2's `TodaysBirthdays`.
- Produces (in `uk.co.siland.culvery.display`):
  - `data class WallDress` and `internal fun dressOn(…)` (ruling 7).
  - On `SeasonalThemes`: `val previewingBirthday: StateFlow<Boolean>` (true only on the day it was turned on); `fun previewBirthday(on: Boolean)` (on clears the theme preview; nothing while the clock is unread); `preview(theme, on = true)` clears the birthday preview. `showing` and `previewing` keep their meaning.
  - `@Singleton class WallLooks internal constructor(themes: SeasonalThemes, birthdays: Flow<List<Person>>, people: Flow<List<Person>>, scope: CoroutineScope)`, `@Inject constructor(themes: SeasonalThemes, todays: Optional<TodaysBirthdays>, household: HouseholdRepository, @ApplicationScope scope: CoroutineScope)` (no seam → `flowOf(emptyList())`):
    - `val dress: StateFlow<WallDress>` (Eagerly, `WallDress()` first);
    - `val previewPerson: StateFlow<Person?>`: the first in People, null with nobody.
    - Logs "Birthday look on for {count} people" when the dress's birthday name count changes from 0 to n or between counts, "Birthday look off" when it goes to none; `TAG = "WallLooks"`.

- [ ] **Step 1: The failing tests** (stubs: `dressOn` returns `WallDress()`, `previewBirthday` does nothing).
  - `WallLooksTest` (`dressOn` directly, then through `WallLooks` on a fake clock; Sam pink, Alex blue, People order Sam, Alex):
    - `aBirthdayBeatsHalloween`: `dressOn(null, null, [Sam], HALLOWEEN) == WallDress(birthday = Birthday(pink, ["Sam"]))`.
    - `aThemePreviewBeatsABirthday`: `dressOn(HALLOWEEN, null, [Sam], null) == WallDress(seasonal = HALLOWEEN)`.
    - `aBirthdayPreviewBeatsHalloweenByDate`: `dressOn(null, Sam, [], HALLOWEEN)` → Sam's birthday.
    - `aBirthdayPreviewBeatsARealBirthday`: `dressOn(null, Sam, [Alex], null)` → `Birthday(pink, ["Sam"])`.
    - `twoBirthdaysTakeTheFirstPersonsColour`: `[Sam, Alex]` → `Birthday(pink, ["Sam", "Alex"])`.
    - `nothingIsTheNormalLook`: all empty → `WallDress()`.
    - `previewsReplaceEachOther`: through `WallLooks` and `SeasonalThemes` on 2026-11-05: `preview(HALLOWEEN, true)` then `previewBirthday(true)` → `previewing == null`, `previewingBirthday`, dress Sam's; then `preview(HALLOWEEN, true)` → `previewingBirthday` false, dress Halloween.
    - `aBirthdayPreviewEndsAtMidnight`: on at 23:59 → Sam's; 00:00 next day → `WallDress()` and `previewingBirthday` false.
    - `theDoorbellFollowsOnlyTheSeason`: on 2026-10-31 with `[Sam]` → dress is Sam's and `themes.showing.value == HALLOWEEN`; on 2026-11-05 with `previewBirthday(true)` → `themes.showing.value == null`.
    - `noPreviewWithNobody`: People empty → `previewPerson` null, `previewBirthday(true)` leaves the dress `WallDress()`.
    - `noSeamIsNoBirthday`: the `@Inject` constructor with `Optional.empty()` → `WallDress()` on any day.
  - `SeasonalThemesTest.aThemePreviewClearsTheBirthdayPreview` and `.nothingPreviewsBeforeTheClockIsRead` (`previewBirthday(true)` with `now` null → `previewingBirthday` false).
  - `SleepLayersTest.theNightClockIsTheSameOnABirthday` (**pin**): as Plan 14's `theNightClockIsTheSameInEveryTheme`, under `CulveryTheme(dark, birthday = Birthday(pink, ["Sam"]))`, dark and light.
  - `SleepLayersScreenshotTest`: `night_clock_birthday` (dark, Sam pink).
  - `ShellScreenshotTest`: `home_birthday_{green|blue|pink|lime|orange|yellow}_{dark|light}`, the `fullHome` state with `birthday = Birthday(colour, listOf("Sam"))` (colours only at this task; the spec's six-colour screenshot check).

  Run `./gradlew :app:testDebugUnitTest --tests '*WallLooks*' --tests '*SeasonalThemes*' --tests '*SleepLayers*'` and `./gradlew -p build-logic :convention:test`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement** per ruling 7. `MainActivity` collects `looks.dress` with `collectAsStateWithLifecycle()` in place of `seasonal.showing`; `seasonal` stays injected for the doorbell's binding.
- [ ] **Step 3: Images.** **New:** `night_clock_birthday`, and the twelve `home_birthday_*` (prefix `home_birthday_`). Then `cmp app/src/test/screenshots/night_clock.png app/src/test/screenshots/night_clock_birthday.png` prints nothing (if it differs, stop: the night clock changed). **Changes:** none.
- [ ] **Step 4: The gate, then commit.**
```bash
git add app
git commit -m "Dress the wall for today's birthday, a preview first, then a birthday, then a seasonal theme in its dates, while the doorbell keeps following the season"
```

---

### Task 4: The greeting pill under Home's date (spec §2.2, §4.2; ruling 8; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify `app/.../shell/ui/HomeScreen.kt` (`HomeHeader` places the pill when `Culvery.birthday` is set), `ShellDimens.kt` (`greetingGap`, `greetingPaddingH`, `greetingPaddingV`, `greetingCake`, `greetingCakeGap`), `core/ui/.../Seasonal.kt` (`HomeSkyBand.greetingWidth`), `core/ui/.../HalloweenArt.kt` (`skyBand` takes the widest of clock, date and greeting).
- Test: new `app/src/test/.../shell/ui/GreetingTest.kt`; `ShellScreenshotTest`; `ShellLayoutTest` (**pins**).

**Interfaces:**
- Consumes: `Culvery.birthday`, `birthdayGreeting`.
- Produces: tag `home_greeting`; `HomeSkyBand.greetingWidth: Int`.

- [ ] **Step 1: The failing tests.**
  - `GreetingTest` (`HomeHeader` under `CulveryTheme(dark = true, birthday = …)`, one sample header item):
    - `theGreetingSitsUnderTheDate`: with `["Sam"]`, `home_greeting`'s top is `greetingGap` below `home_date`'s bottom, its left equals the date's.
    - `talkBackReadsTheGreeting`: `home_greeting`'s text is "Happy birthday Sam" with no emoji; reading order clock, date, greeting, items.
    - `twoAndThreeNames`: `["Sam", "Alex"]` → "Happy birthday Sam and Alex"; three → "Happy birthday Sam, Alex and Mia".
    - `aLongGreetingWrapsClearOfTheItems`: `["Alexandra", "Maximilian", "Wilhelmina"]` with a 1100 dp header → `home_greeting`'s right is left of `home_header_items`' left by at least `HEADER_ITEM_GAP`.
    - `theHeaderItemsDontMove`: `home_header_items`' bounds are the same with and without a birthday.
    - `noGreetingWithoutABirthday` (**pin**): no `home_greeting`; the header's height as today.
    - `thePillIsThePersonsSoftColour` (`@GraphicsMode(NATIVE)`): a pixel inside `home_greeting` clear of the text is `BirthdayNight.getValue(pink).accentSoft`.
  - Screenshots: new `home_birthday_two_dark` / `_light` (Sam and Alex, pink).

  Run `./gradlew :app:testDebugUnitTest --tests '*Greeting*' --tests '*ShellLayout*'`. Expected: FAIL on assertions; `ShellLayoutTest` passes.
- [ ] **Step 2: Implement** per ruling 8 (the cake slot is a `Spacer` until Task 7).
- [ ] **Step 3: Images.** **New:** `home_birthday_two_dark`, `home_birthday_two_light`. **Changes:** the twelve `home_birthday_*` (the pill under the date). Every other `home_*` unchanged.
- [ ] **Step 4: The gate, then commit.**
```bash
git add app core/ui
git commit -m "Greet the birthday people by name in a soft pill of their colour under Home's date"
```

---

### Task 5: Cherry Bomb One and the birthday clock face (spec §2.2, §4.2, §4.3; ruling 9; Review Focus 5)

**Review:** sonnet.

**Files:**
- Download (approved by the user 2026-11-03) into a new empty `$LISTS/cherrybombone/`, from `https://raw.githubusercontent.com/google/fonts/3dd78844021e948ceb633d1dcee3f7885561b5d9/ofl/cherrybombone/`: first `METADATA.pb`, whose `fonts { filename: … }` names the TTF (expected `CherryBombOne-Regular.ttf`), then that TTF and `OFL.txt`. Check before copying: `grep -c '^license: "OFL"' METADATA.pb` prints 1; `grep -c "SIL OPEN FONT LICENSE Version 1.1" OFL.txt` prints at least 1; `head -c 4 <ttf> | xxd -p` prints `00010000`; the TTF is 214 940 bytes. **Any failure, a 404, or an `OFL.txt` that isn't the SIL Open Font License → stop and tell the controller, who asks the user.** Record `sha256sum <ttf>`. Copy to `core/ui/src/main/res/font/cherry_bomb_one.ttf` and `core/ui/licenses/OFL-CherryBombOne.txt`; no subsetting. Nothing in `$LISTS/cherrybombone/` is run.
- Modify `README.md`'s licence paragraph (line 214), after Creepster's sentence: "Cherry Bomb One: SIL Open Font License (`core/ui/licenses/OFL-CherryBombOne.txt`), for the birthday clock; from google/fonts at commit 3dd78844021e948ceb633d1dcee3f7885561b5d9, `CherryBombOne-Regular.ttf` sha256 {hash}."
- Modify `core/ui/.../ClockFace.kt` (`CherryBombOne`, `CherryBombClock`), `BirthdayLook.kt` (`clockFace = CherryBombClock` on all six looks).
- Test: `core/ui` `ThemeTest.bundledFontsAreRealFiles`, `ClockFaceTest`; new `app/src/test/.../shell/ui/BirthdayClockTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Produces: `val CherryBombOne: FontFamily` (`Font(R.font.cherry_bomb_one)`); `val CherryBombClock: TextStyle` = `HhType.clock` with `fontFamily = CherryBombOne`, `fontWeight = FontWeight.Normal`, `fontFeatureSettings = null`, `letterSpacing = 0.sp`.

- [ ] **Step 1: The failing tests.**
  - `ThemeTest.bundledFontsAreRealFiles` adds `R.font.cherry_bomb_one` between 100 000 and 500 000 bytes.
  - `ClockFaceTest.everyDigitGetsTheSameSlot` gains a `CherryBombClock` row ("11:11", "00:00", "23:58" equal widths).
  - `BirthdayClockTest` (Home's header under `CulveryTheme(dark = true, birthday = Birthday(pink, ["Sam"]))`, as Plan 14's `HalloweenClockTest`):
    - `theClockIsInCherryBombOne`: the first `clock_slot`'s `fontFamily == CherryBombOne`.
    - `theClockKeepsItsWidthEveryMinute`: `home_clock`'s width equal at 11:11, 00:00, 08:47, 23:58.
    - `theHeaderKeepsItsHeightInEitherFace`: `home_clock`'s height equal with no look and with the birthday (the greeting aside: compare the clock's node only).
    - `theClockDrawsNothingIntoTheDate` (`@GraphicsMode(NATIVE)`): at 23:58 and 22:22, no pixel equal to the set's `accent` at or below `home_date`'s top across the clock's width.
    - `theClockIsThePersonsColour` (`@GraphicsMode(NATIVE)`): some pixel inside `home_clock` is exactly `BirthdayNight.getValue(pink).accent`.
    - `theDateStaysInDmSans` (**pin**).
  - Screenshots: the twelve `home_birthday_*` and `home_birthday_two_*` change.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*BirthdayClock*' --tests '*HalloweenClock*'`. Expected: FAIL on assertions, the pin aside; `HalloweenClockTest` passes.
- [ ] **Step 2: Implement** per ruling 9.
- [ ] **Step 3: Images.** **Changes:** the `home_birthday_` prefix (fourteen images; the clock in the person's colour in Cherry Bomb One; date and weather unchanged).
- [ ] **Step 4: The gate, then commit** (`git status` first: only the files above, nothing from `$LISTS`).
```bash
git add core/ui README.md app
git commit -m "Set Home's clock in Cherry Bomb One in the birthday person's colour, each digit in a fixed slot so the header never moves"
```

---

### Task 6: Settings › Seasonal looks: "Preview a birthday" (spec §3.5, §4.4; ruling 14)

**Review:** sonnet.

**Files:**
- Modify `app/.../display/DisplayWords.kt` (`PREVIEW_A_BIRTHDAY`, `fun birthdayPreviewLine(name: String, on: Boolean): String`), `display/ui/DisplaySettingsPage.kt` (`DisplaySections` and `DisplaySettingsHost` take `WallLooks`; the row on `SeasonalLooksScreen`).
- Test: `display/ui/DisplaySettingsPageTest.kt`, `display/ui/DisplaySettingsScreenshotTest.kt`.

**Interfaces:**
- Consumes: Task 3's `SeasonalThemes.previewingBirthday`, `previewBirthday(on)`, `WallLooks.previewPerson`.
- Produces: `DisplaySettingsUi.birthdayName: String? = null`, `.previewingBirthday: Boolean = false`; `DisplaySettingsActions.onPreviewBirthday: (Boolean) -> Unit = {}`; tag `display_preview_birthday`.

- [ ] **Step 1: The failing tests.**
  - `DisplaySettingsPageTest`:
    - `theBirthdayPreviewRow`: `birthdayName = "Sam"` → under "Seasonal themes", after Halloween's rows, a switch "Preview a birthday", off, line "Shows Sam’s birthday on the wall until midnight, with confetti each time the screen wakes.".
    - `theBirthdayPreviewSaysWhenItEnds`: `previewingBirthday = true` → on, line "Showing now. It ends at midnight.".
    - `theSwitchPreviewsABirthday`: tap → `onPreviewBirthday(true)`; on, tap → `false`.
    - `noRowWithNobody`: `birthdayName = null` → no `display_preview_birthday`.
    - `theHostPreviewsThroughSeasonalThemes`: the host over real `SeasonalThemes` and `WallLooks` (fake clock 2026-11-05, People Sam) → tapping sets `looks.dress` to Sam's birthday; Halloween's switch, turned on next, turns the birthday switch off.
    - `aBirthdayPreviewEndingAtMidnightTurnsTheSwitchOff`.
  - Screenshots: new `settings_seasonal_birthday_dark` / `_light` (the preview on, under Sam's pink).

  Run `./gradlew :app:testDebugUnitTest --tests '*DisplaySettings*'`. Expected: FAIL on assertions.
- [ ] **Step 2: Implement** with `HhSwitchRow`, per the Copy.
- [ ] **Step 3: Images.** **New:** `settings_seasonal_birthday_dark`, `settings_seasonal_birthday_light`. **Changes:** whichever existing Seasonal looks images show the section's foot (list what `compareRoborazziDebug` shows; check each changes only by the new row).
- [ ] **Step 4: The gate, then commit.**
```bash
git add app
git commit -m "Add a birthday preview to Settings › Seasonal looks, showing the first person's birthday until midnight or until turned off"
```

---

### Task 7: The cake in the rail (spec §2.2, §4.2; ruling 10)

**Review:** sonnet.

**Files:**
- Create `core/ui/.../BirthdayArt.kt` (`CakeMark`); modify `BirthdayLook.kt` (`mark = { w -> CakeMark(w) }`), `app/.../shell/ui/HomeScreen.kt` (the greeting's `Spacer` becomes `CakeMark(ShellDimens.greetingCake)`).
- Test: new `core/ui/src/test/.../BirthdayArtTest.kt`; `app/src/test/.../shell/ui/NavRailTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Produces: `@Composable fun CakeMark(width: Dp, modifier: Modifier = Modifier)` (ruling 10).

- [ ] **Step 1: The failing tests.**
  - `BirthdayArtTest.theCakeIsAPictureOnly` (`@GraphicsMode(NATIVE)`): under Sam's pink birthday, no text, no description, hidden from accessibility; some pixel is exactly the set's `accent` and some is `weather` (the flame).
  - `NavRailTest.theCakeTakesTheMarksPlace`: under a birthday, `cake_mark` inside `culvery_mark`, whose bounds equal those with no look; `thePumpkinStillShowsForHalloween` (**pin**).
  - Screenshots: the `home_birthday_` prefix changes.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*NavRail*' --tests '*Greeting*'`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Images.** **Changes:** the `home_birthday_` prefix (the cake in the rail and in the pill).
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Put a cake in the rail and in the greeting while a birthday shows"
```

---

### Task 8: *Optional* early tablet checkpoint (spec §5)

**Controller and user only, and only if the user wants it now; skip it otherwise.** The release as Plan 14's Task 10 Steps 2–3: `./gradlew :app:assembleRelease`; `grep -c DebugClock app/build/outputs/mapping/release/mapping.txt` prints 0; copy the mapping to `culvery-notes/mappings/` named for the commit; `adb -s <tablet> install -r app/build/outputs/apk/release/app-release.apk`; never uninstall. With the user: the birthday preview on, looked at from across the room by day and at night (the colours, the greeting, the clock, the cake); then off. Record one line under "Plan 16: on the tablet" in `docs/superpowers/plans/2026-09-23-plan1-followups.md`, with no names; commit only that file: `git commit -m "Record Plan 16's early install on the tablet" docs/superpowers/plans/2026-09-23-plan1-followups.md`.

---

### Task 9: Bunting across Home's open header band (spec §2.2, §4.2; ruling 11) — third cut

**Review:** sonnet.

**Files:**
- Modify `core/ui/.../BirthdayArt.kt` (`object BirthdayDimens` with ruling 11's bunting values; `fun DrawScope.drawBunting(band: Rect, string: Color, density: Density)`; `@Composable fun BirthdaySky(drifting: Boolean, modifier: Modifier = Modifier)`, drawing the bunting only at this task, placed in `skyBand` as `HalloweenSky` is), `BirthdayLook.kt` (`sky = { drifting, m -> BirthdaySky(drifting, m) }`).
- Test: new `core/ui/src/test/.../BirthdaySkyTest.kt`; `app/src/test/.../shell/ui/CulveryShellSkyTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `skyBand`, `LocalHomeSkyBand`, `BirthdayInks.flag`.
- Produces: `BirthdaySky`, tag `birthday_sky`, `clearAndSetSemantics {}`; `internal fun flagCount(bandWidthPx: Float, density: Density): Int` (0 under three pitches).

- [ ] **Step 1: The failing tests.**
  - `BirthdaySkyTest`:
    - `theBuntingStaysInTheBand`: with a measured `HomeSkyBand` (header 1100 × 220 px, clock 400, greeting 420, items 300), `birthday_sky`'s bounds lie inside `skyBand(…)`.
    - `flagsFillTheBand`: `flagCount` for 280 dp → 10; for 83 dp → 0.
    - `theFlagsAreThePersonColours` (`@GraphicsMode(NATIVE)`): pixels at the first six flags' centres are `PersonPalette.colors` in order.
    - `theSkyIsSilent`: no node under `birthday_sky` has text, a description or an action.
  - `CulveryShellSkyTest.theBirthdaySkyIsBehindHome`: a birthday on Home → `birthday_sky` exists and Home's header reads as before; `noBirthdaySkyOffHome`; `theBatsStillFlyForHalloween` (**pin**).
  - Screenshots: the `home_birthday_` prefix changes.

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*Sky*'`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement** per ruling 11.
- [ ] **Step 3: Images.** **Changes:** the `home_birthday_` prefix (bunting across the open band between the date and the weather).
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Hang bunting in the person colours across Home's header while a birthday shows"
```

---

### Task 10: Confetti on each wake (spec §2.3, §3.3; rulings 12, 13; Review Focus 4) — second cut

**Review:** opus. Nothing falls when it shouldn't; a burst ends when the screen or a card says so; touches and TalkBack pass through.

**Files:**
- Create `core/ui/.../Confetti.kt` (`ConfettiBurst`, `confettiPieces`), `app/.../display/ConfettiCue.kt` (`wakes`, `confettiFalls`), `app/.../display/ui/ConfettiLayer.kt`.
- Modify `app/.../MainActivity.kt` (a `ConfettiLayer` after `ShellLayers` inside the themed `Box`, fed `display.screen.wakes()`, `screen == AWAKE`, and `confettiFalls(dress.birthday != null, animatorsOn, pinRequest != null, alert != null)`).
- Test: new `core/ui/src/test/.../ConfettiTest.kt`, `app/src/test/.../display/ConfettiCueTest.kt`, `app/src/test/.../display/ui/ConfettiLayerTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Produces:
  - `data class ConfettiPiece(val centre: Offset, val angleDegrees: Float, val colour: Color)`; `fun confettiPieces(seed: Long, elapsedMillis: Long, size: Size, colours: List<Color>, density: Density): List<ConfettiPiece>`.
  - `@Composable fun ConfettiBurst(seed: Long, onDone: () -> Unit, modifier: Modifier = Modifier)`: tag `confetti`; colours `PersonPalette.colors + Culvery.colors.accent`; calls `onDone` once at `CONFETTI_MS`, and no later than `CONFETTI_GONE_MS`.
  - `fun Flow<Screen>.wakes(): Flow<Unit>`; `internal fun confettiFalls(…)` (ruling 13).
  - `@Composable internal fun ConfettiLayer(wakes: Flow<Unit>, awake: Boolean, falls: Boolean, seed: () -> Long)`.

- [ ] **Step 1: The failing tests** (stubs: `wakes` emits nothing, `confettiFalls` true, `confettiPieces` all at the origin).
  - `ConfettiTest`: `sixtyPieces`; `theyStartAboveTheTop` (elapsed 0: every `centre.y <= 0`); `theyAreBelowTheBottomBy2500` (elapsed 2 500: every `centre.y > size.height`); `theColoursAreThePeoplesAndTheAccent`; `theSameSeedFallsTheSameWay`.
  - `ConfettiCueTest`:
    - `onlyAWakeFromDarkOrTheNightClockCounts`: `[AWAKE]` → none (start); `[AWAKE, ASLEEP, AWAKE]` → one; `[AWAKE, NIGHT_CLOCK, AWAKE]` → one; `[AWAKE, NIGHT_CLOCK, ASLEEP, AWAKE]` → one; `[ASLEEP]` as the first value then `AWAKE` → one.
    - `confettiFallsOnlyWhenNothingIsUp`: the sixteen combinations; only `(true, true, false, false)` is true.
  - `ConfettiLayerTest` (`mainClock.autoAdvance = false`; a `MutableSharedFlow<Unit>` for wakes):
    - `aWakeDropsABurst`: emit → `confetti` exists; advance 2 400 → still; advance to 3 000 → gone.
    - `noBurstWhenItShouldntFall`: `falls = false`, emit → no `confetti`.
    - `aBurstStopsWhenTheScreenSleeps`: emit, advance 500, `awake = false` → gone; `awake = true` again with no wake → none.
    - `aBurstStopsWhenARingShows`: emit, advance 500, `falls = false` → gone.
    - `touchesPassThrough`: a button under the layer; mid-burst `performClick` reaches it, and a `performTouchInput { click() }` at its centre calls it.
    - `talkBackFindsNothing`: no node under `confetti` has text, a description or an action.
    - `aBurstIsGoneByThreeSeconds`: `onDone` called by 3 000 ms.
  - Screenshot: new `home_birthday_confetti_dark` (Sam pink, mainClock at 1 000 ms into a burst).

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*Confetti*'`. Expected: FAIL on assertions.
- [ ] **Step 2: Implement** per rulings 12 and 13. The seed is `wallClock.nowMillis()` at the wake.
- [ ] **Step 3: Images.** **New:** `home_birthday_confetti_dark` (check: pieces over Home, nothing behind them changed). **Changes:** none.
- [ ] **Step 4: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Drop confetti for two and a half seconds each time the screen wakes on a birthday, never with animations off or while a card or PIN pad shows"
```

---

### Task 11: Balloons drifting up behind Home (spec §2.2, §3.4; ruling 11) — first cut

**Review:** opus. Nothing moves while the screen is dark, something is held over Home, Home isn't showing or animations are off; a step redraws only the sky. **If time runs short, the controller skips this task and records it in the follow-ups.** If Task 9 was cut, this task creates `BirthdaySky` and its wiring as Task 9 describes, without the bunting.

**Files:**
- Modify `core/ui/.../BirthdayArt.kt` (`BirthdayDimens`' balloon values, `BALLOON_LAP_MS`; `balloonPositions`; `BirthdaySky` gains the balloons and the frame loop, reusing `HalloweenSky`'s step: lift the shared loop into one private helper in `HalloweenArt.kt` rather than copying it).
- Test: `core/ui/src/test/.../BirthdaySkyTest.kt`; `ShellScreenshotTest`.

**Interfaces:**
- Consumes: `skyDrifts` (unchanged), `SKY_FRAME_MS`, `BirthdayInks.balloon`.
- Produces: `fun balloonPositions(elapsedMillis: Long, band: Rect, density: Density): List<Offset>` (each balloon's top-left).

- [ ] **Step 1: The failing tests** (stub: `balloonPositions` returns fixed points).
  - `BirthdaySkyTest`:
    - `balloonsRiseTheBandEveryLap`: for a 400 × 200 px band, `balloonPositions(0)` vs `(20_000)`: each y moved by half of `band.height + balloonHeight + balloonString` (mod it); `(40_000)` equals `(0)`; x within each lane ± `balloonSway`.
    - `theSkyDriftsWhileAsked`, `stillMeansNoFrames`, `aStepIsAboutAFrameAndAWait`: as Plan 14's `HalloweenSkyTest` ones, on `birthday_sky`.
    - `aStillSkyStillDrawsTheBalloons` (`@GraphicsMode(NATIVE)`): `drifting = false` → the pixel at a balloon's centre is `BirthdayInks.balloon(colour, set)`.
  - `CulveryShellSkyTest.theBirthdaySkyStopsWhenHeld` (**pin**: `skyDrifts` unchanged): `skyDrifts = false` → elapsed doesn't advance.
  - Screenshots: the `home_birthday_` prefix changes (balloons drawn still at elapsed 0).

  Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest --tests '*Sky*'`. Expected: FAIL on assertions, the pin aside.
- [ ] **Step 2: Implement** per ruling 11.
- [ ] **Step 3: Images.** **Changes:** the `home_birthday_` prefix (four faint balloons in the open band, under the bunting).
- [ ] **Step 4: A final look** at `home_birthday_pink_dark` and `_light` and `home_birthday_lime_light`: clock, date, greeting, weather, cards, cake, bunting and balloons together; anything that competes with text is said in the report.
- [ ] **Step 5: The gate, then commit.**
```bash
git add core/ui app
git commit -m "Let balloons drift up through Home's header while a birthday shows, still while the screen is dark, anything is open over Home or animations are off"
```

---

### Task 12: Simplify the whole branch

- [ ] **Step 1:** Run the `simplify` skill over `git diff main...HEAD`. Look hardest at: `WallLooks` and `SeasonalThemes`' previews (one `combine` if two read alike); `birthdayGreeting` against `spokenNames` and `HouseLines.roomList` (one helper if a shared module can hold it without a new module edge); the two skies' shared frame loop; `CalendarBirthdays`' flows.
- [ ] **Step 2:** For each change, the module's tests, then the gate.
- [ ] **Step 3:** Commit when anything changed: `git commit -am "Simplify the birthday code"`.

---

### Task 13: The emulator walk-through on `culvery-5` (spec §7)

**Rules:** only `culvery-5` with a debug build and `install -r`; the household is made up (Sam, Alex, Mia); Claude enters the debug sample PINs; faults are collected and asked about before fixing; no house device touched.

- [ ] **Step 1: Walk** (one line each).
  1. Through the editor: an all-day "Sam’s birthday" today for Sam → pink look by night and by day (the status bar's Day/Night preview); every tab, a sheet, the PIN pad, a toast, Settings and the door card in the colours.
  2. A second "Sam’s birthday" → still one greeting. Then also one for Alex → "Happy birthday Sam and Alex", in Sam's colour if Sam is first in People.
  3. An all-day "Birthday" for Family, and the sample timed "Birthday party" → no look. (The untagged event in a person's own calendar is covered by `BirthdaysOnTest`; the editor always tags.)
  4. `DebugClockReceiver` to 31 October with Sam's birthday → the birthday shows; Settings › Door's Play chime with Follow the theme plays the organ.
  5. The preview on (no birthday today) → Sam's look and the line; off → gone; on, then the clock past midnight → it ends and the switch reads off; Halloween's preview on turns it off.
  6. The dark screen and the night clock (`DebugClockReceiver` into the night hours), then a tap → confetti each time; the night clock itself unchanged.
  7. "Remove animations" on → no confetti, the balloons still.
  8. A sample ring as the screen wakes → no confetti over the card.
- [ ] **Step 2: The fault loop**; put back every setting changed; delete the sample events; `DebugClockReceiver --el minutes 0`; `emu kill`.
- [ ] **Step 3: Record** under "Plan 16 (birthdays): emulator walk-through", no names. Commit: `git commit -am "Record Plan 16's emulator walk-through"`.

---

### Task 14: Final review, merge, install

- [ ] **Step 1:** Opus reviews `git diff main...HEAD` against the spec and this plan, looking first at: who counts (the tag only), midnight, readability in all twelve sets, the doorbell untouched, confetti and balloons stopping, nothing personal in logs or fixtures, the font's licence and hash. One fix wave, each fix with its failing test first, then the gate.
- [ ] **Step 2:** Finish with `superpowers:finishing-a-development-branch`: only on the user's say, `git merge --ff-only plan-16-birthdays` on `main`, the gate on `main`; never push. Move the design cuts and any cut task to the follow-ups.
- [ ] **Step 3:** The release from the merged `main` on the tablet with `install -r`, the mapping kept as in Task 8. With the user: the preview on from across the room by day and at night, then off; `adb -s <tablet> logcat -d -s WallLooks HeatLog` over the evening shows only the two birthday lines' shapes and no warmer evening than before. Record under "Plan 16: on the tablet", with no names, and commit only that file.

---

## Walk-through

1. On a household member's birthday, tagged for them in the calendar, the wall greets them by name under the date and wears their colour from midnight to midnight.
2. The clock is in Cherry Bomb One in their colour; a cake replaces the mark; bunting and slow balloons fill the open part of Home's header; confetti falls each time the screen wakes.
3. Two birthdays: both named, the first person's colour.
4. A birthday beats Halloween; the doorbell still follows Halloween.
5. Settings › Seasonal looks: "Preview a birthday" shows the first person's birthday until midnight.

**Design cuts to say again** (provisional, for the end-of-v1 review):
- the twelve sets' values, pink's darker accent than the mockup's, and the moved warning reds for pink and orange;
- the drawn cake in place of the 🎂 emoji, the pill's size and place, and Home's grid shorter on the day;
- Cherry Bomb One's size if Task 5 shrank it;
- the bunting and balloons kept to the header's open band, their sizes, number, speed and alpha;
- the confetti's count, fall and colours;
- "Preview a birthday" naming the first person only.

## Not in this build, on purpose

- Other occasions, ages, sound, house devices.
- A birthday preview for a chosen person (the first in People only).
- Keeping a preview across a restart.

---

## Spec coverage (16 → tasks)

| Spec | Task |
|---|---|
| §1 intent, goals, anti-goals | 1, 2, 3, 10, 11, 14 |
| §2.1 who counts | 2 |
| §2.2 full party: colours, bunting, cake, balloons, clock, greeting | 1, 4, 5, 7, 9, 11 |
| §2.3 confetti | 10 |
| §2.4 quiet, doorbell unchanged | 3 |
| §2.6 duplicates | 2 |
| §3.1 what counts | 2 |
| §3.2 which look wins, two people, midnight fade, night clock | 1, 3 |
| §3.3 confetti rules | 10 |
| §3.4 balloons | 11 |
| §3.5 preview, precedence | 3, 6 |
| §3.6 personal data | Global Constraints; 1, 2, 3 |
| §4.1 finding birthdays, the seam, stale sync | 2 |
| §4.2 the look, twelve sets, contrast, clock, drawings | 1, 4, 5, 7, 9, 11 |
| §4.3 the font | 5 |
| §4.4 Settings | 6 |
| §5 constraints | Global Constraints; 8, 13, 14 |
| §6 left to the builder | rulings 1–14 |
| §7 walk-through checks | 3 (six colours), 13, 14 |
