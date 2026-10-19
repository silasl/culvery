# Culvery — Plan 9a: The redesign, Home and the shared frame Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–14 are subagent tasks, except Task 1 Step 1 (the controller reads the tablet's density); Task 15 is the controller on the `culvery-5` AVD with the user; Tasks 16–17 need the user and the son's SM-T510.

> **Needs the user's OK: nothing yet; two possible stops.** No new library and no version change. No deprecated API is planned. Flagged and avoided: `SliderDefaults.Track(sliderPositions = …)` and `Slider(…, thumb, track)` overloads taking `SliderPositions` (deprecated); `Modifier.dropShadow` is not used (`Modifier.shadow` is). **Stop and ask** if (a) giving `HhPercentSlider` its 20 dp track needs `@OptIn(ExperimentalMaterial3Api::class)` (`SliderState`, `SliderDefaults.Track(sliderState = …)`): the fallback is Material's own track height with the theme's colours, and the user chooses; (b) a new icon is missing from the font at 608ce00: Google's current font is a download (name the file, source and size). If the compiler shows a deprecation or opt-in warning on a line you wrote, **stop and ask**.

**Goal:** Home and the frame around every tab look as the October redesign draws them: the rail and a top bar with a Watching pill, the forecast in Home's header, Today always five rows with Lights above Music beside it, the bigger door card over a scrim that closes after 60 seconds, the PIN pad's lockout countdown, the larger night clock, toasts that stay long enough to read, a countdown ring on the closing warning, and one outlined disabled button.

**Architecture:** Shared values land first in `:core:ui` (the `border` and `rain` tokens, the card edge, the outlined disabled `HhButton` with a trailing icon, toast timing and the countdown ring) so later tasks and Plans 9b/9c reuse them. `:core:plugin`'s `HomeCardPlacer` becomes a two-column `HomeLayout` (the TALL card left; up to two REGULAR cards right, Coming up only when there are none), and `AlertQuieter`/`AlertHost` learn whether something was lowered. Each capability redraws its own Home part (weather header, Today's fill rule, Lights, Music, the door card); `:core:access` redraws the PIN pad's lockout; `:app` redraws the rail, the top bar, the alert layer's scrim and the night clock.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-19-culvery-9-redesign-design.md` (§2 the user's rulings and §6 the reviewed calls are binding; where it and the handoff disagree, the spec wins), applying `docs/design/2026-10-redesign/handoff.md` and its notes `notes.txt`. Read all three with this plan.
**Previous plan (format, patterns):** `docs/superpowers/plans/2026-10-19-culvery-08-sleep-and-wake.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Tasks 15–17 update it).

**Plan series:** 1 · 2a · 2b-1 · 2b-2 · 3a · 4a · 4b · 4c · 4d-1 · 4d-2 · 4e · 5 Ring · 6 Sonos · 7 Philips Hue · 8 Sleep and wake · **9a Redesign: frame and Home (this plan)** · 9b Lights, Music, Door and Calendar tabs · 9c Add event, the wizard, Settings pages.

**Builds on:** branch `plan-9-redesign` in the one checkout `C:\Users\si\code\siland\culvery` (no worktrees; cut from `main` at 7d03651, spec at 9ab4218). Work only there. Never push. File references were read at 9ab4218.

| # | Task | Review |
|---|---|---|
| 1 | The density, and the six new icons | sonnet |
| 2 | Tokens: `border`, `rain`, the card edge, the card radius, the scrim | sonnet |
| 3 | One disabled button (the outline) and a trailing icon | sonnet |
| 4 | Toast timing and the closing warning's countdown ring | sonnet |
| 5 | The shared frame: rail, top bar, Watching pill, theme control | **opus** (the camera mark's privacy rule) |
| 6 | The weather in Home's header; the Forecast card goes | sonnet |
| 7 | Home's two columns: `HomeLayout`, the header's clock | sonnet |
| 8 | Today: five rows, tomorrow's top-up, the scroll, the pill, measured overflow | **opus** (the measuring layout) |
| 9 | The Lights card and the off slider | sonnet |
| 10 | The Music card's one control row and volume pill | sonnet |
| 11 | "Music lowered": quieters say whether they lowered anything | sonnet |
| 12 | The door card: scrim, size, Door history, 60 seconds | sonnet |
| 13 | The PIN pad's lockout countdown | sonnet |
| 14 | The night clock and the bare camera glyph | sonnet |
| 15 | The emulator walk-through on `culvery-5` | controller + user |
| 16 | On the tablet: the redesign at its density, the night clock in the dark | controller + user, **needs the SM-T510** |
| 17 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:** the density gates every number and the icon font can stop for a download, so Task 1 first. Shared `:core:ui` pieces next (2–4), so every screen after them is drawn once with them. The frame (5) before Home's parts; the weather (6) before the layout (7) because the Forecast card would otherwise fill Home's right column as a REGULAR card. Today (8), Lights (9), Music (10) on the new layout. The quieter's answer (11) before the door card that shows it (12). The PIN pad (13) and the night clock (14) stand alone. Emulator (15), tablet (16), merge (17).

## Shared components 9b and 9c reuse

Named here so their implementers don't build them again: `HhColors.border`, `HhColors.rain` and `HhCard`'s edge (Task 2); `ShellTokens.cardScrim` (Task 2); the outlined disabled `HhButton` and `HhButton(trailingIcon = …)` (Task 3: 9c's wizard Next arrow and Add event footer); `HhToast(countdownMs = …)` and `HhCountdownRing` (Task 4); `HhBottomFade` and the pinned-pill pattern (Task 8: 9b's Lights grid "N more rooms"); `HhPercentSlider(enabled = false, trackHeight = …)` with the off look (Task 9: 9b's room cards); `VolumePill` and `RoundButton(primary = …)` (Task 10: 9b's Music tab); `ButtonTokens.touchMin` (Task 3).

## Rulings against the code

Where the spec leaves a choice open, or doesn't fit the code at 9ab4218. Each is the plan's call unless it cites the spec.

1. **Units.** The handoff's px are converted once, at dp = px ÷ 1.5 (spec §3), rounding to whole dp and sp; the values are in **Units** below and each task uses them. Every 2 px edge becomes 1 dp (1.33 rounds down); Task 15/16 look at whether they read. The SM-T510's density is read first (Task 1 Step 1); the screenshot tests already draw at 1280 × 800 dp, `hdpi` (every module's `robolectric.properties`).
2. **Touch targets stay 48 dp** (4e: on the SM-T510 Compose didn't widen smaller targets). A control the handoff draws at 64–68 px (43–45 dp) is 48 dp; a pill drawn smaller than 48 dp (Sign out's 29 dp) keeps its look inside a 48 dp tap height. `ButtonTokens.touchMin = 48.dp`.
3. **`HomeLayout` replaces the 3 × 2 grid** (spec §4.2, §6.1): left, the highest-priority TALL card; right, the REGULAR cards by priority, at most 2, the first on top (Lights 35 above Music 30), splitting the column's height equally with a 15 dp gap; with none, the highest-priority WIDE card (Coming up) takes the right column; with one, it takes the full column. Ties go by id, as today.
4. **The Forecast card is deleted** with its tests and baselines. Weather's header shows: the current weather and the 3-day strip when ready with an hour for now; the "Add home location for weather" pill with no location (it opens Settings › Home location); and, because the card that said so is gone, the existing `NO_FORECAST` line in `mute` when the fetch failed with nothing stored. Waiting and Expired show nothing, as today. **Rain colours:** drizzle, rain, showers and thunder icons are `rain`; the rest `weather` (spec §4.2's "rain icons blue, sun amber").
5. **Today's fill** (spec §4.2): the counted rule picks the rows (n > 5 scroll; n = 5 those; n < 5 today's then 5 − n from tomorrow; n = 0 "Nothing on today" then 5 from tomorrow), then the card measures them: if they don't fit, tomorrow's rows go from the end, the TOMORROW divider with the last; if today's rows alone don't fit, the scroll treatment. In the scroll, the pill reads `"${n − min(k, 4)} more today · Open calendar"`, k being today's rows wholly above the pill at the top of the list: the handoff's n − 4 at its sizes, more when rows are taller. With n = 0 the TOMORROW divider shows under "Nothing on today", so outlined rows are always labelled. "See the week" hides while the pill shows. "Nothing on today. Tap + to add something." becomes the handoff's "Nothing on today".
6. **The pinned pill** is an `HhButton` (quiet, on `surf`, `calendar_month` before, `arrow_forward` after), 48 dp, 21 dp from the card's sides and 20 dp from its foot, `Modifier.shadow(8.dp, pill)`; the fade is 133 dp, transparent to `surf`; the list gets 73 dp of bottom padding.
7. **The Lights card's status** is `cardStatus(room)`: "On · 70% · Relax", "On · 70%" with no active scene, "On · Relax" for a room that can't dim, "Off". The middle slider shows only for a dimmable main room; off, it is disabled at the remembered level and reads "Off" (spec §2.5). The bottom row keeps Plan 7's All lights off and Holiday mode buttons (spec §6.2). Card padding is Today's (20 × 21 dp); a 15 dp gap above the bottom row; the rest of the height goes between the top row and the slider (spec §4.2).
8. **The Music card's row:** Play/Pause (primary, round), Next (quiet, round; hidden when nothing plays), then the volume pill filling the rest. **Shuffle everything** takes Next's place when nothing is playing, and is otherwise left to the Music tab, so the row keeps three items; paused with a queue, the row is Play, Next, volume. The pill shows its number (note 27).
9. **"Music lowered"** (spec §6.3): `AlertQuieter.quiet()` returns true when it turned something down; Music's is true when at least one room's volume command went through to a lower volume. `AlertHost.lowered` is true from the first quieter that said so until the loud alert goes; a ring replacing a ring keeps it.
10. **The door card's scrim** swallows taps and does nothing with them: the card goes by Got it, Door history or its 60 seconds (spec §2.4). The alert layer no longer sets the alert's width; the door card is 747 dp wide at most. **Door history** marks the ring seen (as Got it does) and opens the Door tab. Its "›" is the trailing `chevron_right` icon, so TalkBack reads "Door history". The subtitle stays "Front Door · 11:54" (the handoff's "· just now" is left out: the time already says it). The card's shadow is `Modifier.shadow(27.dp, shape)` (0 40 120 px). Without a snapshot the card is shorter; no placeholder.
11. **The Watching pill and the marks** (spec §6.7): the top bar's pill shows while `Presence.running`. The overlay mark (Plan 8's, top centre) shows only while the camera runs, the screen is awake and the top bar is covered or absent: Settings, the wizard, a sheet, the PIN pad or the door card. While asleep, the sleep layers draw the bare glyph (`#2E3331`) at the pill's spot, the top bar's right end. So the camera is marked exactly once at any moment.
12. **The theme control** reads `"${shown} · ${mode}"`: shown "Night" or "Day" (the theme on screen), mode "Auto" or "Preview". Spoken "Theme: Night · Auto"; its click labels stay Plan 4's.
13. **Toast timing** (spec §6.10): `toastMillis(message) = min(6_000 + 1_000 × (words / 10), 12_000)`, words being the message's whitespace-separated words (integer division). `urgent` stays for TalkBack's assertive reading only.
14. **The countdown ring** draws in place of the warning's `schedule` icon, full when the warning appears and empty at `CLOSING_WARNING_MS`, on both closing warnings (Settings' and an edited sheet's: one pill). Its words stay fixed.
15. **The PIN pad** takes the 4b card for both states (620 px wide, 2 px `surf3` edge, radius 44, the .64 scrim, 116 px keys) so nothing moves between them. Locked: the `dangerSoft` circle with a filled `lock_clock` in `danger`, "Too many tries", "Wait a moment, then try your PIN again.", the countdown `m:ss` and a bar draining from the lock's first value to 0 (fill `danger` on `surf3`), every digit, 0 and backspace at 30 %, Cancel at full strength. The dots and message and the countdown and bar are both laid out, one invisible and cleared from TalkBack, so the keys keep their place. TalkBack still hears `tooManyTries(firstSeconds)` once.
16. **Large buttons keep `ControlType.button` (17 sp)**; the handoff's 28 px footer labels (19 sp) are 9c's, with the footers. The door card's buttons are `ButtonSize.Large` (60 dp; 88 px is 59).
17. **The disabled outline** is 1 dp `surf3` around a transparent fill with a full-strength `mute` label and icon (spec §6.4, note 36); the reason line above it is 9c's. `DISABLED_INK_ALPHA` goes.
18. **The night clock** is 200 sp, −8 sp tracking, tabular, `#5A5A5A` (spec §4.10, provisional for the dark room). The glyph is the filled `photo_camera` at 20 dp, vertically centred on the top bar's 48 dp, 28 dp from the right edge.

## Units (converted once, px ÷ 1.5)

Names are the plan's; each task puts its values in its module's dimens object.

| Where | Handoff px | dp / sp |
|---|---|---|
| Rail | 160 wide, 1 px right line | `RAIL_WIDTH` 107, line 1 |
| Mark | 84 × 76; 76 below | `MARK_WIDTH` 56; top and sides `(RAIL_WIDTH − MARK_WIDTH) / 2`; 51 below |
| Nav item | pill 96 × 56 (r 28), label 20, 22 apart | pill 64 × 37 (r 50 %), label 13 sp (selected W600, else W500), 15 apart |
| Door badge | min 28 high, 17/700, 3 px `bg` ring | min 19, 11 sp W700 `dangerInk` on `danger`, ring 2 |
| Settings tile | 120 × 120, r 30, `s1` | 80 × 80, r 20, `surf` |
| Main area | padding 0 42 34 44 | top 0, end 28, bottom 23, start 29 |
| Top bar | 72 high, 28 gaps | 48, gaps 19 |
| Watching pill | 44 high, 10 dot, 19/700 | 29, dot 7, icon 18 (filled), 13 sp W700 `accent` on `accentSoft`, padding 12 × 0 |
| Signed-in | Sign out 44 pill 19/700; divider 1 × 28 | icon 20, "Alex · Admin" 14 sp `ink`; pill 29 in a 48 tap height, 13 sp W700, quiet fill on `bg`; divider 1 × 19 `surf3` |
| Theme control | 21 muted | icon 20, 14 sp `mute` |
| Home clock | 150/600, lh .86, ls −5 | 100 sp W600, lh 86 sp, ls −3.3 sp, tabular |
| Home date | 34 muted, 22 gap | 23 sp, 15 below the clock's line box |
| Weather now | icon 84, 64/700, condition 22 | icon 56, 43 sp W700, 15 sp `mute` |
| Strip | divider 1 × 100 `s3`; days 34 apart; label 20/600, icon 42, high 22/700, low 22 | divider 1 × 67; 23 apart; 13 sp W600, icon 28, 15 sp W700, 15 sp |
| Grid | gaps 22; left column 600 | gap 15; left 400 |
| Cards | r 36, 1 px `border` (light), Today padding 30/32 | r 24, edge 1, padding 20 × 21 |
| Today header | 34/700; 68 + and pill | 23 sp W700; 48 (ruling 2) |
| Today row | r 22, padding 20/22, bar 7, title 31/600 (2 lines), meta 24; lists 14 apart | r 15, padding 13 × 15, bar 5, 21 sp W600, 16 sp, 9 apart |
| Tomorrow | divider 22/700 ls 1.5, 1 px `s3` rule; rows 2 px `s2` edge, padding 16/20, bar 75 %, 27/600 (1 line), meta 22 | 15 sp W700 ls 1 sp, rule 1; edge 1, padding 11 × 13, bar alpha .75, 18 sp W600, 15 sp |
| Today scroll | fade 200; pill 72, 32 sides, 30 foot; 110 padding | fade 133; pill 48, 21 sides, 20 foot; 73 bottom padding |
| "Nothing on today" | 28 muted | 19 sp |
| Lights card | bulb 46, name 30/700, status 22, › 64; track 30; buttons 68 | 31, 20 sp W700, 15 sp, 48; track 20, icons 24; 48 |
| Music card | art 132 r 20; room 30/700, track 25, artist 21; row 68; volume track 14 | 88 r 13; 20 sp W700, 17 sp, 14 sp; 48; track 9 |
| Door card | 1120 wide, 2 px `s3`, r 44, padding 36, gap 26; circle 88, bell 50; 46/700, 25; chip 52; snapshot r 28; buttons 88 | 747, 1, r 29, 24, 17; 59, 33; 31 sp W700, 17 sp; chip 35 (icon 20, 15 sp W600); r 19; Large 60 |
| Scrims | rgba(0,0,0,.64) | `Color(0xA3000000)` |
| PIN card | 620 wide, 2 px `s3`, r 44; circle 88; 40/700; 23; countdown 44/700; bar 260 × 8; keys 116 at 30 % | 413, 1, r 29; 59 (icon 33); 27 sp W700; 15 sp; 29 sp W700 tabular; 173 × 5; 77, alpha .3 |
| Night clock | 300/600, ls −12 | 200 sp W600, ls −8 sp, tabular |
| Countdown ring | (the toast's 22 icon) | 22, stroke 3 |

## Global Constraints

**From the spec, verbatim:**

- §2.2 "**Coming up goes now.** Home's Coming up card is removed. Today always fills 5 rows, topping up from tomorrow or scrolling (note 9), and the week is one tap away. Lights and Music take the right column until Shopping and the tray arrive."
- §2.3 "Until the One-tap tray exists, the Home Lights card keeps a Holiday mode button."
- §2.4 "**The door card closes after 60 seconds** (was 2 minutes, Plan 5). The ring stays in the Door tab with its badge."
- §2.5 "**An off room looks off** (note 18). Its slider is disabled, keeping the remembered level and reading "Off"".
- §3 "The handoff gives every size in device pixels on a 1920 × 1200 artboard, and says dp = px ÷ 1.5 (sp likewise for type). The builder confirms the SM-T510's density (expected 1.5, 240 dpi …) before converting, and converts once, in the theme and the screens' dimens, rounding to whole dp."
- §4.1 "the theme control reads "Night · Auto" / "Day · Auto", and "Day · Preview" / "Night · Preview" while previewing, tapping it returns to Auto". "New tokens `border` (light only) and `rain` (#7FB2E5 dark, #2F6FAE light)".
- §4.2 "**Lights on top, Music below, each about half the column's height** … their parts keep the handoff's sizes and the extra height goes to space between the top row and the controls." "the placer's priority rules still decide which cards show." "Hide "See the week" while the pinned pill shows. **Not** "Day 2 of 3"".
- §4.3 ""Got it" primary and "Door history ›" quiet (opens the Door tab), and a "Music lowered" chip — shown only when a quieter actually lowered something. … The chime, quiet hours and night rules are unchanged."
- §4.13 "Toasts and warning pills stay at least 6 s plus 1 s per 10 words, capped at 12 s". "The "Settings closes in 20 s" pill shows its countdown as a ring that runs down." "Today's scroll view hides its scrollbar; the pill and "See the week" open Calendar on today."
- §6.1 "the right column shows **Coming up** … until the slideshow or Shopping exists … with only one of Lights or Music, that card takes the full right column. Each of the three layouts gets a screenshot test."
- §6.4 "**One disabled button style** (the outline) everywhere, replacing Plan 8's half-faded label. The reason line above it is **optional**".
- §6.7 "Plan 8's privacy rule stands: a mark shows whenever the camera is running. The top bar's pill shows while the camera runs (`Presence.running`); on screens without the top bar (Settings, sheets, the wizard) the small overlay mark stays".
- §7 "Every changed screen's Roborazzi baselines are re-recorded and each new image looked at against the handoff". "Copy changes go through CopyScanTest; TalkBack labels follow the new copy."

**Copy** (the handoff's, verbatim, curly apostrophes in code): "Watching", "Sign out", "Night · Auto", "Day · Auto", "Night · Preview", "Day · Preview", "Add home location for weather", "Today", "See the week", "Nothing on today", "Tomorrow" (drawn TOMORROW), "{n} more today · Open calendar", "All lights off", "Holiday mode", "Got it", "Door history", "Music lowered", "Too many tries", "Wait a moment, then try your PIN again.". **The plan's, for the end-of-v1 design walk-through:** the pill and glyph's TalkBack name stays "The camera is on"; status "On · 70% · Relax".

**From the house rules:**

- Package root `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. **No new library; never change a version.**
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (or `.\gradlew.bat …`). Never plain `test`, `check` or `build`. A task runs its module's tests while working; every task ends with the full gate before its commit. **Never run a Gradle `install…` task**, and no subagent touches a device.
- **Tests that can fail:** every new test fails before its change, and its step says why; a "Fails if" line names the mutation it catches where it isn't plain. A test that passes before its change is labelled **pin**. Timer tests run on virtual time (`TestScope`'s scheduler, or `compose.mainClock` with `autoAdvance = false`) and never sleep; each checks 1 ms before its boundary and at it. Code reaching `android.util.Log` runs under Robolectric.
- **Screenshots:** baselines in `<module>/src/test/screenshots/`. Before re-recording, `./gradlew <module>:compareRoborazziDebug --tests "<pattern>"` and open each `*_compare.png` under `<module>/build/outputs/roborazzi/`; then `recordRoborazziDebug`. **Open every new image and look at it against the handoff's section for that screen** (sizes, colours, copy) and the rulings above. **The baseline check** (Plan 6): the step's expected names, one per line, in `$TMP/expected-baselines.txt`, then
```bash
git status --porcelain -- "*.png" | sed 's#.*/##; s#\.png$##' | sort -u > "$TMP/changed.txt"
echo "changed but not listed:"; comm -23 "$TMP/changed.txt" <(sort -u "$TMP/expected-baselines.txt")
echo "listed but unchanged:"; comm -13 "$TMP/changed.txt" <(sort -u "$TMP/expected-baselines.txt")
```
Both lists empty, or stop and report. Where a shared value changes many screens (Tasks 2, 3, 5, 7), the expected list is every image `compareRoborazziDebug` reports changed across all modules, each looked at and named in the commit's report.
- **Copy** names what the user does; an action keeps its name through its flow; errors say what went wrong and how to fix it; curly quotes and apostrophes; "·", "›", "…", "—" as written. `CopyScanTest` must pass; a protocol word never shown goes in `core/ui/src/test/resources/copy-scan-allow.txt`. Sentences end with a full stop; labels don't.
- **Accessibility:** every changed screen's test runs `compose.assertClickablesHaveRoleAndLabel()`; TalkBack names follow the new copy.
- **Icons** come from `Icons`; a new one also goes in `tools/fonts/icons.txt` and the subset font rebuilt from the full font at 608ce00 (`tools/fonts/README.md`).
- **Layout numbers are named**, in the module's dimens object; tests may use literal dp.
- **Comments say what the code can't**, one line by default; spec references as `(9 §4.2)`. **No comment names this plan's task or ruling numbers.**
- **No personal data in files:** sample names only as the tests already use them ("Alex", "Sam", "Mia", "Lounge", "Kitchen"); from the tablet, only figures.
- **Write every file as UTF-8.**
- **Commit messages are a plain sentence in the repo's style and contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Never push. **Never** `git checkout -- .` or `git restore .`.

## Review Focus

The five inputs the spec implies, most likely to bite the household, that its own test list doesn't cover; each is pinned in its owning task:

1. **A large font or long titles on a five-event day** (font scale 1.5, two-line titles). Expected: the scroll and pinned pill, nothing cut mid-row, no tomorrow rows behind the pill.
   - Task 8 `TodayCardTest.atFontScale15FiveRowsScrollWithThePill`, `TodayFillTest.aLongTitleDropsTomorrowRowsFirst`.
2. **Midnight while Home shows tomorrow's top-ups.** Expected: tomorrow's events become today's rows, the divider and outlined rows go, and the new tomorrow tops up.
   - Task 8 `CardHostsMidnightRolloverTest.tomorrowsTopUpsBecomeTodayAtMidnight`.
3. **The camera running behind Settings, a sheet, the PIN pad, the door card, the wizard or the dark screen.** Expected: exactly one mark visible at every moment (pill, overlay mark or glyph), never none.
   - Task 5 `DisplayWiringTest.theCameraIsMarkedExactlyOnce`; Task 14 `SleepLayersTest.theBareGlyphShowsWhileTheCameraRuns`.
4. **The forecast failing now the Forecast card is gone** (no Wi-Fi at first start). Expected: the header says there's no forecast and why, not an empty header.
   - Task 6 `WeatherViewTest.aFailedFetchSaysSoInTheHeader`, `WeatherHeaderItemTest.noForecastSaysWhy`.
5. **The lockout ending with TalkBack on.** Expected: the countdown isn't read every second, the keys come back at 0:00 without moving, and "You can try again." is said.
   - Task 13 `PinPadTest.theCountdownIsSpokenOnce`, `theKeypadDoesNotMoveWhenTheLockEnds`.

The spec's own tests (§7) each have their task: Today's fill at 0, 1–4, 5, 6+ and measured overflow (8); the off-room slider (9; the scene chip turning a room on is 9b's tab); the door card at 60 s (12); the PIN countdown and keys at 0:00 (13); toast duration by word count (4); the theme control's preview copy and the Watching pill (5); the three Home layouts (7). The Lights grid at 4, 6 and 9 rooms is 9b's.

---

## File Structure

```
core/ui/.../Icons.kt, tools/fonts/icons.txt, core/ui/src/main/res/font/material_symbols_rounded.ttf (Task 1)
core/ui/.../Colors.kt, Theme.kt, Components.kt (HhCard edge), Shell.kt (homeCardRadius, cardScrim) (Task 2)
core/ui/.../Buttons.kt (Task 3)   Shell.kt (toastMillis, HhToast, HhCountdownRing) (Task 4)
core/ui/.../Fades.kt (create, Task 8)   Controls.kt (HhPercentSlider, Task 9)   Type.kt (clock, date, Task 7)
core/plugin/.../HomeCardPlacer.kt, HomeCard.kt (Task 7)   Alerts.kt (Task 11)
core/access/.../ui/PinPad.kt, ui/PinPadDimens.kt (Task 13)
capability/weather/.../WeatherView.kt, WeatherWords.kt, WeatherCapability.kt, ui/WeatherHeaderItem.kt, ui/WeatherDimens.kt; ui/ForecastCard.kt deleted (Task 6)
capability/calendar/.../ui/TodayFill.kt (create), ui/TodayCard.kt, ui/CardHosts.kt, ui/CalendarType.kt (Task 8)
capability/lights/.../ui/LightsCard.kt, ui/LightsParts.kt, ui/LightsDimens.kt, LightsWords.kt (Task 9)
capability/music/.../ui/MusicCard.kt, ui/MusicParts.kt, ui/MusicDimens.kt (Task 10)   MusicQuieter.kt (Task 11)
capability/door/.../DoorAlerts.kt, DoorWords.kt, ui/DoorCard.kt, ui/DoorCardHost.kt, ui/DoorDimens.kt (Task 12)
app/src/main/java/uk/co/siland/culvery/
  shell/ui/ShellDimens.kt (create), NavRail.kt, StatusBar.kt, CulveryShell.kt, SetupWiring.kt (overlayCameraMark), MainActivity.kt (Task 5)
  shell/ui/HomeScreen.kt, HomeGrid.kt, shell/ShellUiState.kt, shell/ShellViewModel.kt (Task 7)
  shell/ui/OverlayLayers.kt (Task 4: toasts, ring; Task 12: the alert's scrim)   shell/ShellOverlays.kt (Task 11)
  display/ui/SleepLayers.kt, display/ui/DisplayDimens.kt, MainActivity.kt (Task 14)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 15–17)
```

`…` stands for the module's package directory.

---

### Task 1: The density, and the six new icons (spec §3; ruling 1)

**Review:** sonnet (Step 1 is the controller's).

**Files:**
- Modify: `core/ui/.../Icons.kt`, `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (regenerated); maybe `core/ui/src/test/.../IconFontTest.kt` (`NO_FILLED_FORM`)

**Interfaces:**
- Produces: `Icons.ARROW_FORWARD = "arrow_forward"`, `Icons.BRIGHTNESS_HIGH = "brightness_high"`, `Icons.BRIGHTNESS_LOW = "brightness_low"`, `Icons.LOCK_CLOCK = "lock_clock"`, `Icons.VOLUME_DOWN = "volume_down"`, `Icons.VOLUME_UP = "volume_up"`.

- [ ] **Step 1: The density (the controller, the SM-T510 on the cable; 4e's rules, serial in `$TMP/culvery-4e-serial`)** — `adb -s "$S" shell wm density` and `adb -s "$S" shell wm size`. Expected: "Physical density: 240" and "Physical size: 1920x1200", no "Override" line. Anything else: **stop and ask** (the Units table assumes 1.5). Read only; change nothing.
- [ ] **Step 2: The failing test** — add the six constants to `Icons` and the names to `icons.txt`, each in alphabetical order. Run: `./gradlew :core:ui:testDebugUnitTest --tests '*IconFontTest*'` — Expected: FAIL (the font lacks them).
- [ ] **Step 3: The subset** — `tools/fonts/README.md` steps 3–4: `git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$TMP/material-symbols-full.ttf"`, then `python tools/fonts/subset.py --source "$TMP/material-symbols-full.ttf"` (fontTools via `python -m pip install --user fonttools` if missing). If the script names one the source lacks: **a download — stop and ask the user**. If `everyNamedIconKeepsItsFilledForm` names one, add it to `NO_FILLED_FORM`.
- [ ] **Step 4: The gate and commit** — Expected: PASS, **no image changed**.
```bash
git add core/ui tools/fonts/icons.txt
git commit -m "Add the icons the redesign draws: arrows, brightness, the lockout clock and volume"
```

---

### Task 2: Tokens: `border`, `rain`, the card edge, the card radius, the scrim (spec §4.1; Units)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Colors.kt`, `Theme.kt` (animate both new tokens), `Components.kt` (`HhCard`), `Shell.kt` (`homeCardRadius`, `cardScrim`); every user of `ShellTokens.pinScrim` (`core/access/.../ui/PinPad.kt`, `capability/calendar/.../ui/Pickers.kt`)
- Test: `core/ui/src/test/.../ThemeTest.kt`, `ContrastTest.kt`

**Interfaces:**
- Produces: `HhColors.border: Color` (dark `Color.Transparent`, light `Color(0xFFD5DCD8)`), `HhColors.rain: Color` (dark `Color(0xFF7FB2E5)`, light `Color(0xFF2F6FAE)`); `HhCard` draws a 1 dp `border` edge inside its clip (`CardTokens.edge = 1.dp`); `ShellTokens.homeCardRadius = 24.dp`; `ShellTokens.cardScrim = Color(0xA3000000)` replacing `pinScrim`.

- [ ] **Step 1: The failing tests** — `ThemeTest.theCardEdgeIsLightOnly` (`DarkColors.border == Color.Transparent`, `LightColors.border == Color(0xFFD5DCD8)`); `ThemeTest.rainIsBlueInBothThemes` (the two values); `ContrastTest.rainIconsReadOnTheirSurfaces` (`rain` against `bg` and `surf`, both themes, ≥ 3:1, as the weather icons' check). Run: `./gradlew :core:ui:testDebugUnitTest --tests '*ThemeTest*' --tests '*ContrastTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — the two fields after `weather`; `HhCard` adds `.border(CardTokens.edge, Culvery.colors.border, shape)` after the clip; `cardScrim`'s KDoc: "rgba(0,0,0,.64) behind the door card and the PIN pad (9 §4.3, §4.9). The same in both themes."
- [ ] **Step 3: Run them, then compare** — `./gradlew compareRoborazziDebug`: every light image with a card gains the edge; every Home card's corner and every PIN pad or picker scrim changes. **Re-record** (`recordRoborazziDebug`), **open each new image and look at it against the handoff** (light theme, note 8: a hairline edge, no shadow; dark: no edge). The expected list is the compare report; nothing else may change.
- [ ] **Step 4: The gate and commit**
```bash
git add core capability app
git commit -m "Add the light theme's card edge and the rain colour, and round cards and scrims to the redesign"
```

---

### Task 3: One disabled button (the outline) and a trailing icon (spec §6.4; ruling 17)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Buttons.kt`
- Test: `core/ui/src/test/.../ButtonsTest.kt`, `ContrastTest.kt`, `ButtonScreenshotTest.kt`; `capability/calendar/src/test/.../ui/EventDetailSheetTest.kt` (its `DISABLED_INK_ALPHA` assertion becomes `mute`)

**Interfaces:**
- Produces: `internal data class ButtonColors(val fill: Color, val ink: Color, val outline: Color?)`; disabled: `ButtonColors(Color.Transparent, colors.mute, colors.surf3)`; `ButtonTokens.disabledOutline = 1.dp`, `ButtonTokens.touchMin = 48.dp`; `fun surfaceOf(on: ButtonSurface, colors: HhColors): Color` (`bg`, `surf`, `dangerSoft`); `HhButton(…, trailingIcon: String? = null)` drawn after the label at `ButtonTokens.icon` in the label's colour; with a trailing icon the end padding is `regularIconPaddingStart` (Regular). `DISABLED_INK_ALPHA` removed.

- [ ] **Step 1: The failing tests**
- `ButtonsTest.aDisabledButtonIsAnOutlineWithAMutedLabel`: for every tone and surface, both themes: `buttonColors(tone, enabled = false, on, c) == ButtonColors(Color.Transparent, c.mute, c.surf3)`; a disabled `HhButton` click does nothing and is `assertIsNotEnabled()`. Fails if any tone keeps a fill.
- `ButtonsTest.aTrailingIconSitsAfterTheLabel`: `HhButton("Door history", …, trailingIcon = Icons.CHEVRON_RIGHT, on = ButtonSurface.Surf)`: the icon's left ≥ the label's right; the node's name is "Door history" (the icon is unspoken).
- `ContrastTest`'s disabled check: `t.mute` against `surfaceOf(on, t)` ≥ `DISABLED_TEXT_MIN`, every surface, both themes.
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*ButtonsTest*' --tests '*ContrastTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — `HhButton` adds `.border(ButtonTokens.disabledOutline, outline, pill)` when `outline != null`, before the click; the KDoc's "Disabled, every tone is the quiet fill with a faded `mute` label" becomes "Disabled, every tone is an outline with a `mute` label (9 §6.4)".
- [ ] **Step 3: Run them; compare; re-record** — `./gradlew compareRoborazziDebug`: `button_matrix_*` and every image with a disabled `HhButton` (All lights off with nothing on, sheet footers before a title, …). **Re-record, open each new image and look at it against the handoff** (note 36: a thin outline, a muted label, no fill). The expected list is the compare report.
- [ ] **Step 4: The gate and commit**
```bash
git add core capability
git commit -m "Draw every disabled button as an outline with a muted label, and let a button end with an icon"
```

---

### Task 4: Toast timing and the closing warning's countdown ring (spec §4.13, §6.10; rulings 13, 14)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Shell.kt`; `app/.../shell/ui/OverlayLayers.kt` (`ToastLayer`, `ClosingWarningLayer`)
- Test: `core/ui/src/test/.../ToastMillisTest.kt`, `ShellPartsTest.kt`; `app/src/test/.../shell/ui/ShellLayersTimeoutTest.kt`, `ShellLayersRedrawTest.kt`, `ShellScreenshotTest.kt`

**Interfaces:**
- Produces (`core.ui`): `const val TOAST_MIN_MS = 6_000L`, `TOAST_MAX_MS = 12_000L`, `TOAST_MS_PER_TEN_WORDS = 1_000L`; `fun toastMillis(message: String): Long`; `internal fun ringLeft(elapsedMs: Long, totalMs: Long): Float` (1 at 0, linear, 0 at and after `totalMs`); `@Composable fun HhCountdownRing(totalMs: Long, color: Color, modifier: Modifier = Modifier)` (`ShellTokens.ringSize = 22.dp`, `ringStroke = 3.dp`; the drained part in `color` at 30 %; starts full on first composition, drains on the frame clock); `HhToast(message, icon, modifier, urgent, countdownMs: Long? = null)` (a ring in `bg` in place of the icon when set).

- [ ] **Step 1: The failing tests**
```kotlin
@Test
fun aToastStaysSixSecondsPlusOneForEveryTenWords() {
    fun words(n: Int) = List(n) { "word" }.joinToString(" ")
    assertThat(toastMillis("")).isEqualTo(6_000L)
    assertThat(toastMillis(words(9))).isEqualTo(6_000L)
    assertThat(toastMillis(words(10))).isEqualTo(7_000L)
    assertThat(toastMillis(words(59))).isEqualTo(11_000L)
    assertThat(toastMillis(words(60))).isEqualTo(12_000L)
    assertThat(toastMillis(words(200))).isEqualTo(12_000L)
    assertThat(toastMillis("  two   words  ")).isEqualTo(6_000L)
}
```
- `ShellPartsTest.theRingRunsDownOverItsTime`: `ringLeft(0, 20_000) == 1f`, `(10_000, …) == 0.5f`, `(20_000, …) == 0f`, `(25_000, …) == 0f`.
- `ShellLayersTimeoutTest.aToastShowsForItsReadingTime` (`mainClock.autoAdvance = false`): a 12-word toast is on screen at 6 999 ms and gone at 7 000 ms. Fails today: the old rule hides it at 4 000.
`ShellLayersRedrawTest`'s `toastMillis("Event deleted", urgent = false)` becomes `toastMillis("Event deleted")`.
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*ToastMillisTest*' --tests '*ShellPartsTest*'` and `./gradlew :app:testDebugUnitTest --tests '*ShellLayers*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — words by `message.split(Regex("\\s+")).count { it.isNotEmpty() }`; `ToastLayer` calls `toastMillis(t.message)`; `ClosingWarningLayer` passes `countdownMs = CLOSING_WARNING_MS`; the ring is drawn with `drawArc` from 12 o'clock, clockwise, from an `Animatable(1f)` animated to 0 with `tween(totalMs.toInt(), easing = LinearEasing)`. The toast KDoc's "4 s, 6 s for an error…" becomes the new rule (9 §6.10).
- [ ] **Step 3: Screenshots** — `settings_warning_dark` and `sheet_warning_dark` re-recorded (a full ring); a new `settings_warning_half_dark` (`autoAdvance = false`, advanced 10 000 ms: half a ring). Re-record, **open each new image and look at it against the handoff** ("Interactions": a ring that runs down). Expected list: those three.
- [ ] **Step 4: The gate and commit**
```bash
git add core/ui app
git commit -m "Keep toasts up six seconds plus one for every ten words, and show the closing warning's countdown as a ring"
```

---

### Task 5: The shared frame: rail, top bar, Watching pill, theme control (spec §4.1, §6.7; rulings 2, 11, 12; Review Focus 3)

**Review:** opus.

**Files:**
- Create: `app/.../shell/ui/ShellDimens.kt`
- Modify: `shell/ui/CulveryShell.kt` (the rail full height with the mark inside it; the top bar and the page in the main area), `NavRail.kt`, `StatusBar.kt` (the top bar), `CulveryMark.kt` (none expected), `SetupWiring.kt` (`overlayCameraMark`), `MainActivity.kt`
- Test: `app/src/test/.../shell/ui/ShellLayoutTest.kt`, `NavRailTest.kt`, `ShellScreenshotTest.kt`; `display/DisplayWiringTest.kt`; `display/ui/SleepLayersScreenshotTest.kt` (`camera_mark_dark`/`_light` now show the pill)

**Interfaces:**
- Consumes: `ButtonTokens.touchMin` (Task 3); `Presence.running` (Plan 8).
- Produces: `object ShellDimens` (the frame rows of Units, incl. `topBarHeight = 48.dp`, `mainPaddingEnd = 28.dp`); `RAIL_WIDTH = 107.dp`, `MARK_WIDTH = 56.dp`; `internal fun themeLabel(dark: Boolean, previewing: Boolean): String`; `internal const val WATCHING = "Watching"`; `StatusBar(dark, previewing, session, watching: Boolean, onSignOut, onToggleThemePreview)`; `CulveryShell(state, now, watching: Boolean, onSelectTab, onOpenSettings, onSignOut, onToggleThemePreview, tabContent)`; `internal fun overlayCameraMark(running: Boolean, sleeping: Boolean, complete: Boolean?, settingsOpen: Boolean, sheetShowing: Boolean, pinShowing: Boolean, alertShowing: Boolean): Boolean` = `running && !sleeping && !(complete == true && !settingsOpen && !sheetShowing && !pinShowing && !alertShowing)`.

- [ ] **Step 1: The failing tests** (each UI test ends with `assertClickablesHaveRoleAndLabel()`)
- `ShellLayoutTest.theThemeControlSaysWhatItIs` (§4.1): `themeLabel(dark = true, previewing = false) == "Night · Auto"`, `(false, false) == "Day · Auto"`, `(true, true) == "Night · Preview"`, `(false, true) == "Day · Preview"`; on screen the control's name is "Theme: Day · Preview" and its click label "go back to the automatic theme".
- `ShellLayoutTest.theWatchingPillShowsOnlyWhileTheCameraRuns` (§6.7): `watching = true` → "Watching" shown, named "The camera is on"; false → no such node.
- `ShellLayoutTest.theMarkHasEqualSpaceAboveAndBeside` (note 6): the mark's top inside the rail equals `(RAIL_WIDTH − MARK_WIDTH) / 2` within 0.5 dp, and equals its left inset. Replaces `theMarkSitsInTheTopCornerOnTheTabIconsCentreLine` and `theMarkIsAtLeast48DpClearOfTheFirstTab`; `theHomeTabIsWhereItWasBeforeTheMarkMoved` goes (the rail moved).
- `ShellLayoutTest.theTopBarSitsInTheMainArea`: its left ≥ 107 dp, its height 48 dp; the page's top is at the bar's bottom; the page's right edge is 28 dp from the screen's.
- `ShellLayoutTest.signOutIsAQuietPill`: drawn 29 dp tall, tapped anywhere in its 48 dp height it locks (the existing `inTheShellSignOutAndTheThemeTakeATouchJustBelowThem` keeps passing).
- `NavRailTest.theDoorBadgeIsAPillWithABackgroundRing`: `rail_door_badge` at least 19 dp tall and 23 dp wide (19 + the 2 dp ring each side), text "2".
- `DisplayWiringTest.theCameraIsMarkedExactlyOnce` (Review Focus 3): over every combination of the six other inputs, with `running = true` exactly one of these is true, and with `running = false` none: the pill in plain view (`!sleeping && complete == true && !settingsOpen && !sheetShowing && !pinShowing && !alertShowing`), the overlay mark (`overlayCameraMark(…)`), the glyph (`sleeping`). Fails if a sheet, the PIN pad or the wizard leaves the camera unmarked, or the mark doubles over the pill.
Run: `./gradlew :app:testDebugUnitTest --tests '*ShellLayoutTest*' --tests '*NavRailTest*' --tests '*DisplayWiringTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — the Units' frame rows. `CulveryShell`: `Row { NavRail (full height, the mark at its top) ; Column(main padding) { StatusBar ; page } }`. The top bar: right-aligned, 19 dp gaps, in order the Watching pill (a 7 dp `accent` dot, filled `photo_camera`, "Watching"; `contentDescription = THE_CAMERA_IS_ON`), the signed-in block (`account_circle`, "Alex · Admin" in `ink`, the Sign out pill, the 1 × 19 dp `surf3` divider), the theme control. The badge: `danger` pill with a 2 dp `bg` ring (`background(c.bg, pill).padding(2.dp)` then the pill). `MainActivity`: `CulveryShell(…, watching = running, …)` and `ShellLayers(…, cameraMark = overlayCameraMark(running, screen != Screen.AWAKE, complete, state.settingsOpen, overlay.isShowing, pinRequest != null, alert != null))`. Task 14 removes the overlay mark from over the sleep layer; until then it shows there as today.
- [ ] **Step 3: Run them; compare; re-record** — every `ShellScreenshotTest` image and `camera_mark_dark`/`_light` change; new `home_watching_dark`, `home_watching_light` (pill, signed in) and `home_preview_light` ("Day · Preview"). **Re-record, open each new image and look at it against the handoff's "Shared frame"** (the mark's equal padding, the badge's ring, the bar's order, one line). The expected list is the compare report plus the three new names.
- [ ] **Step 4: The gate and commit**
```bash
git add app
git commit -m "Redraw the rail and the top bar: the mark evenly placed, the Door badge ringed, a Watching pill, a Sign out pill and a theme control that says what it is"
```

---

### Task 6: The weather in Home's header; the Forecast card goes (spec §4.2; ruling 4; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify: `capability/weather/.../WeatherView.kt`, `WeatherWords.kt`, `WeatherCapability.kt`, `WeatherRepository.kt` (its `header` flow's type), `ui/WeatherHeaderItem.kt`, `ui/WeatherDimens.kt`
- Delete: `ui/ForecastCard.kt` (and `ForecastCardHost`), its tests, the `forecast_*` baselines; words only it used
- Test: `WeatherViewTest.kt`, `WeatherWordsTest.kt`, `WeatherCapabilityTest.kt`, `ui/WeatherHeaderItemTest.kt`, `ui/WeatherScreenshotTest.kt`; `app/.../shell/ui/ShellScreenshotTest.kt` (`weatherHome` loses the Forecast card)

**Interfaces:**
- Consumes: `HhColors.rain` (Task 2).
- Produces: `sealed interface HeaderWeather { data class Now(val condition: Condition, val night: Boolean, val temperature: Double, val high: Double, val low: Double, val days: List<DailyWeather>) : HeaderWeather; data object AddLocation : HeaderWeather; data object NoForecast : HeaderWeather }`; `fun headerWeather(view: WeatherView, now: LocalDateTime): HeaderWeather?`; `internal fun weatherTint(condition: Condition, colors: HhColors): Color`; `internal fun conditionLabel(condition: Condition): String` ("Partly cloudy"); `internal const val ADD_HOME_LOCATION_FOR_WEATHER = "Add home location for weather"`; `WeatherCapability.cards()` emits an empty list.

- [ ] **Step 1: The failing tests**
- `WeatherViewTest.noLocationAsksForOne` (`NoLocation` → `AddLocation`); `aFailedFetchSaysSoInTheHeader` (Review Focus 4: `Failed` → `NoForecast`); `readyCarriesTheThreeDays` (`Now.days` = today and the next two); `waitingAndExpiredShowNothing` (**pin**, null).
- `WeatherWordsTest.rainIsBlueAndSunIsAmber`: DRIZZLE, RAIN, SHOWERS, THUNDER → `colors.rain`; CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, SNOW → `colors.weather`; both themes. `conditionLabel(PARTLY_CLOUDY) == "Partly cloudy"`.
- `WeatherHeaderItemTest` (Robolectric, a fake `ShellNavigator`): `theCurrentWeatherIsOneItem` (named by `headerDescription`; the icon 56 dp, the temperature 43 sp); `theStripReadsTodayThenTwoDays` (Wednesday 2026-10-21: "Today", "Thu", "Fri", each one item named by `rowDescription`); `theNoLocationPillOpensHomeLocation` ("Add home location for weather" → `openSettings(SettingsPageIds.LOCATION)`); `noForecastSaysWhy` (the `NO_FORECAST` sentence shows). Each ends with `assertClickablesHaveRoleAndLabel()`.
- `WeatherCapabilityTest.weatherHasNoHomeCard` (`cards().first()` empty); `theHeaderItemShowsWithoutALocation`. The old card tests go with the card.
Run: `./gradlew :capability:weather:testDebugUnitTest` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — `headerItems()` maps every non-null `HeaderWeather`; the block: icon, then a `Column` of temperature over `conditionLabel`, the divider, then the strip (`Row` of three: label, icon at 28 dp in `weatherTint`, high and low as one line). The pill is an `HhButton` (quiet, `ButtonSurface.Bg`). Delete what nothing uses after the card goes (`FORECAST_TITLE`, `GETTING_FORECAST`, `NO_FORECAST_YET`, `ADD_LOCATION`, `ADD_HOME_LOCATION`, `updatedAgo`, `STALE_AFTER_MS` if unused; the compiler and the tests say which).
- [ ] **Step 3: Screenshots** — re-recorded: `header_day_*`, `header_night_*`, `header_clear_night_*`, `home_weather_*`, `home_full_*`, `home_fs15`; new: `header_rain_dark`, `header_rain_light`, `header_no_location_dark`, `header_no_location_light`, `header_no_forecast_dark`; deleted: every `forecast_*`. **Open each new image and look at it against the handoff's Home header** (note 1: current weather, a divider, three days; blue rain, amber sun). The baseline check with those names (deletions show as changed).
- [ ] **Step 4: The gate and commit**
```bash
git add capability/weather app
git commit -m "Move the forecast into Home's header with a three-day strip, ask for the home location there, and drop the Forecast card"
```

---

### Task 7: Home's two columns: `HomeLayout`, the header's clock (spec §2.2, §4.2, §6.1; ruling 3)

**Review:** sonnet.

**Files:**
- Modify: `core/plugin/.../HomeCardPlacer.kt` (`HomePlacement` gone), `HomeCard.kt` (the sizes' KDoc); `core/ui/.../Type.kt` (`clock`, `date`); `app/.../shell/ShellUiState.kt`, `ShellViewModel.kt`, `shell/ui/HomeGrid.kt`, `HomeScreen.kt`
- Test: `core/plugin/src/test/.../HomeCardPlacerTest.kt` (rewritten); `capability/lights/src/test/.../LightsCapabilityTest.kt` (its placement case); `app/src/test/.../shell/ShellViewModelTest.kt`; create `app/src/test/.../shell/ui/HomeGridTest.kt`; `ShellLayoutTest.kt`; `ShellScreenshotTest.kt`; `core/ui/src/test/.../TypeTest.kt`

**Interfaces:**
- Produces (`core.plugin`): `data class HomeLayout(val left: HomeCard?, val right: List<HomeCard>) { companion object { val Empty = HomeLayout(null, emptyList()) } }`; `HomeCardPlacer.RIGHT_CARDS = 2`; `HomeCardPlacer.place(cards: List<HomeCard>): HomeLayout`. KDoc on `HomeCardSize`: TALL is Home's left column; REGULAR shares the right column, two at most; WIDE fills the right column only when no REGULAR card shows (9 §6.1).
- Produces (`app`): `ShellUiState.homeCards: HomeLayout = HomeLayout.Empty`; `HomeGrid(layout: HomeLayout, modifier)`; `HomeScreen(now, layout: HomeLayout, headerItems)`; `ShellDimens.gridGap = 15.dp`, `leftColumn = 400.dp`, `clockToDate = 15.dp`; `HhType.clock` 100 sp / W600 / tabular / −3.3 sp / line height 86 sp; `HhType.date` 23 sp.

- [ ] **Step 1: The failing tests**
- `HomeCardPlacerTest`: `emptyIsEmpty`; `theHighestTallCardTakesTheLeft`; `lightsSitAboveMusic` (REGULAR 35 and 30 → `right == [lights, music]`); `atMostTwoOnTheRight` (three REGULAR → the two highest, ties by id); `comingUpOnlyWithoutLightsOrMusic` (TALL + WIDE + one REGULAR → `right == [regular]`; TALL + WIDE → `right == [wide]`); `aSecondWideCardIsDropped`.
- `HomeGridTest` (Robolectric, 1280 × 800 minus the frame): `theLeftColumnIs400Dp`; `twoRightCardsShareTheColumn` (each `(h − 15) / 2` within 1 dp, Lights on top); `oneRightCardTakesTheColumn`; `comingUpTakesTheColumnAlone`. It pins the left card's size and a right half's as literals, `todayCardSize` and `rightHalfSize` (about 400 × 582 and 701 × 283 dp: whatever it measures), and Tasks 8–10's card tests use the same literals, so a frame change that moves them fails here first.
- `ShellLayoutTest.theClockIs100spAndTheDate23` (replaces `theClockIs88spAndTheDate21`); `theDateSits15dpBelowTheClock` (replaces `dateBaselineSits42dpBelowClockBaseline`).
- `LightsCapabilityTest`'s placement case: with Today, Coming up, Music and Lights, `right` is Lights then Music. `ShellViewModelTest`'s placement cases read `homeCards.left`/`right`.
Run: `./gradlew :core:plugin:testDebugUnitTest :app:testDebugUnitTest --tests '*HomeGridTest*' --tests '*ShellLayoutTest*' --tests '*ShellViewModelTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — `HomeGrid`: a `Row` with 15 dp between the 400 dp left and the rest; the right a `Column` with 15 dp gaps, each card `weight(1f)`. The header places the date 15 dp below the clock's line box; the header items keep their place relative to the date. Coming up keeps its parts, restyled by Task 2's tokens.
- [ ] **Step 3: Screenshots** — re-recorded: every `home_*`, `tab_*`, `pin_pad_*` and `camera_mark_*` image in `ShellScreenshotTest`/`SleepLayersScreenshotTest` (the header's clock moves); new `home_coming_up_dark` (Today and Coming up), `home_lights_only_dark` (Today and Lights, full column), `home_lights_music_dark` (spec §6.1's three layouts). **Open each and look at it against the handoff's 1a/1c** (a 400 dp Today column; Lights above Music, half each; the clock's size). The expected list is the compare report plus the three new names.
- [ ] **Step 4: The gate and commit**
```bash
git add core app capability/lights
git commit -m "Lay Home out in two columns: Today on the left, Lights above Music on the right, Coming up only without them"
```

---

### Task 8: Today: five rows, tomorrow's top-up, the scroll, the pill, measured overflow (spec §2.2, §4.2, §4.13; rulings 5, 6; Review Focus 1, 2)

**Review:** opus.

**Files:**
- Create: `capability/calendar/.../ui/TodayFill.kt`; `core/ui/.../Fades.kt`
- Modify: `capability/calendar/.../ui/TodayCard.kt`, `ui/CardHosts.kt` (`TodayCardHost` reads `repo.days(today, 2)`), `ui/CalendarType.kt` (the Today rows of Units)
- Test: create `capability/calendar/src/test/.../ui/TodayFillTest.kt`; `ui/CardsTest.kt`, `ui/CardHostsMidnightRolloverTest.kt`, `ui/CardScreenshotTest.kt`

**Interfaces:**
- Consumes: `HhButton(trailingIcon = …)` (Task 3); `Icons.ARROW_FORWARD` (Task 1); `todayCardSize` (Task 7's `HomeGridTest`).
- Produces (`core.ui`): `@Composable fun HhBottomFade(color: Color, height: Dp, modifier: Modifier = Modifier)` (a vertical gradient, transparent to `color`, ignoring touches).
- Produces (`capability.calendar.ui`):
  - `const val TODAY_ROWS = 5`; `const val PILL_ROWS_SHOWN = 4`
  - `data class TodayRows(val today: List<EventUi>, val tomorrow: List<EventUi>)`; `fun todayRows(today: List<EventUi>, tomorrow: List<EventUi>): TodayRows` (tomorrow is `tomorrow.take(TODAY_ROWS − today.size)` when `today.size < TODAY_ROWS`, else empty)
  - `data class TodayFit(val tomorrowShown: Int, val scroll: Boolean, val more: Int)`; `internal fun fitToday(today: List<Int>, tomorrow: List<Int>, nothing: Int, divider: Int, gap: Int, available: Int, pillSpace: Int): TodayFit` (heights in px: each of today's rows, each candidate tomorrow row, the "Nothing on today" line, the TOMORROW divider)
  - `fun moreToday(count: Int): String = "$count more today · Open calendar"`; `const val NOTHING_ON_TODAY = "Nothing on today"`, `const val TOMORROW = "Tomorrow"`
  - `TodayCard(events: List<EventUi>?, tomorrow: List<EventUi>?, modifier: Modifier = Modifier, onOpen: (EventRef) -> Unit = {}, onAdd: (() -> Unit)? = null)`

`fitToday`'s rule (ruling 5), the one algorithm the signature doesn't decide:
```kotlin
// Stack height of today's rows (or the nothing line), then the divider and t tomorrow rows, gaps between items.
// today.size > TODAY_ROWS -> scroll. Else the largest t <= tomorrow.size whose stack fits available -> no scroll.
// If none fits and today is not empty -> scroll; if today is empty -> t = the largest that fits, at least 0.
// Scrolling: k = today's rows whose bottom (stacked from 0 with gaps) <= available - pillSpace;
// more = today.size - min(k, PILL_ROWS_SHOWN); tomorrowShown = 0.
```

- [ ] **Step 1: The failing tests**
`TodayFillTest.kt` (plain JVM; rows of 80 px, gap 9, divider 30, nothing line 30, available 480, pillSpace 68 unless said):
- `moreThanFiveScrollsWithNMinusFour` (7 rows → `TodayFit(0, true, 3)`); `tallRowsCountMoreThanNMinusFour` (7 rows of 120 → k = 3 → more 4).
- `exactlyFiveIsJustThose` (5 rows → `TodayFit(0, false, 0)`).
- `fewerThanFiveTopUpFromTomorrow`: for n in 1..4, `todayRows` gives `5 − n` tomorrow candidates, and `fitToday` shows them all at 60 px each.
- `nothingTodayShowsFiveFromTomorrow` (n = 0, five 60 px candidates → 5, no scroll).
- `aLongTitleDropsTomorrowRowsFirst` (Review Focus 1: today 110, 110, 110; two tomorrow of 60 → `tomorrowShown == 1`, no scroll).
- `todayAloneOverflowingScrolls` (four of 140 → scroll, k = 2, more 2).
- `aShortTomorrowListTopsUpWhatItHas` (n = 2, tomorrow has 1 → 1).
`CardsTest.kt` (Robolectric, the card at `todayCardSize`, a fake `ShellNavigator`; each ends with `assertClickablesHaveRoleAndLabel()`):
- `sevenTodayScrollsBehindThePill`: "3 more today · Open calendar" shown; no "See the week"; a click → `openTab("calendar")`.
- `fiveTodayShowsFiveAndNoPill`: no "Tomorrow"; "See the week" shown, a click → `openTab("calendar")`.
- `twoTodayAddsThreeFromTomorrow`: the "Tomorrow" heading (drawn TOMORROW, spoken "Tomorrow"), three tomorrow rows named by `eventOnDay(event, tomorrow)`, their titles one line.
- `nothingTodaySaysSoAndShowsFiveFromTomorrow`: "Nothing on today", then the heading and five rows.
- `atFontScale15FiveRowsScrollWithThePill` (Review Focus 1): `WithFontScale(1.5f)`, five events with two-line titles → the pill shows, "See the week" hides.
- `todayTitlesWrapToTwoLines` (**pin**: `maxLines == 2`, ellipsis); `tomorrowRowsOpenTheirEvent`.
`CardHostsMidnightRolloverTest.tomorrowsTopUpsBecomeTodayAtMidnight` (Review Focus 2): one event tomorrow → before midnight it shows under "Tomorrow"; the clock passes midnight → it is a today row and "Tomorrow" is gone unless the new tomorrow has events. Fails today: the host reads only today.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*TodayFillTest*' --tests '*CardsTest*' --tests '*CardHostsMidnightRolloverTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — `TodayCard` lists through a `SubcomposeLayout`: subcompose each candidate row, the nothing line and the divider at the list's width to measure them, call `fitToday`, then subcompose the result: the rows in a `Column`, or a `LazyColumn` (no scrollbar) of today's rows with `contentPadding(bottom = 73.dp)`, `HhBottomFade(c.surf, 133.dp)` at its foot and the pill (ruling 6) pinned. Today rows: `surf2`, 2-line titles; tomorrow rows: transparent with a 1 dp `surf2` edge, the bar at alpha 0.75, one-line titles. The divider: `TOMORROW.uppercase()` with a 1 dp `surf3` rule to its right, a heading spoken "Tomorrow". The header hides "See the week" while scrolling. `TodayCardHost`: `repo.days(today, 2)` → `TodayCard(days[0].events, days[1].events, …)`.
- [ ] **Step 3: Screenshots** — re-recorded: `today_dark`, `today_light`, `today_empty_dark`, `today_badges_dark`, and the app's `home_*`; new: `today_scroll_dark`, `today_scroll_light` (the fade into white), `today_topped_up_dark` (2 + 3), `today_nothing_today_dark` (0 + 5), `today_fs15`. **Open each and look at it against the handoff's Today card** (notes 9, 21: outlined smaller tomorrow rows, the fade into the pill, nothing cut mid-row). The expected list is those names plus the app's compare report.
- [ ] **Step 4: The gate and commit**
```bash
git add core/ui capability/calendar app
git commit -m "Fill Today to five rows: scroll behind an Open calendar pill when there are more, top up from tomorrow when there are fewer, and measure long titles"
```

---

### Task 9: The Lights card and the off slider (spec §2.3, §2.5, §4.2, §6.2; ruling 7)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Controls.kt` (`HhPercentSlider`); `capability/lights/.../ui/LightsCard.kt`, `ui/LightsParts.kt` (`MainRoomActions`), `ui/LightsDimens.kt`, `LightsWords.kt`
- Test: `core/ui/src/test/.../PercentSliderTest.kt`; `capability/lights/src/test/.../ui/LightsCardTest.kt`, `ui/LightsCardScreenshotTest.kt`, `LightsWordsTest.kt`

**Interfaces:**
- Consumes: `Icons.BRIGHTNESS_LOW`, `BRIGHTNESS_HIGH` (Task 1); `rightHalfSize` (Task 7).
- Produces (`core.ui`): `HhPercentSlider(…, enabled: Boolean = true, trackHeight: Dp? = null, …)`; disabled, it ignores drags and taps, keeps `percent`, and draws an `surf2` track with an `surf3` fill and thumb (note 18); `spokenState` is the caller's ("Off").
- Produces (`capability.lights`): `internal fun cardStatus(room: LightRoom): String`; `class MainRoomActions(val onToggle: () -> Unit, val onOpen: () -> Unit, val onDrag: (Int) -> Unit, val onLetGo: (Int) -> Unit)`; `LightsDimens` card rows of Units (`trackHeight = 20.dp`).

- [ ] **Step 1: The failing tests**
- `PercentSliderTest.aDisabledSliderIgnoresDragsAndKeepsItsLevel`: `enabled = false`, `percent = 40` → `assertIsNotEnabled()`, a swipe calls neither `onDrag` nor `onLetGo`, the state reads the caller's `spokenState`.
- `LightsWordsTest.theCardStatusNamesTheScene`: on 70 with Relax active → "On · 70% · Relax"; on 70, no scene → "On · 70%"; on, can't dim, Relax → "On · Relax"; off → "Off".
- `LightsCardTest` (at `rightHalfSize`; each ends with `assertClickablesHaveRoleAndLabel()`): `theSliderIsDisabledWhileTheRoomIsOff` (§2.5: `lights_card_brightness` not enabled, spoken "Lounge brightness, Off"); `movingTheSliderSendsTheBrightness` (on → a swipe → `onLetGo` with a value in 0..100); `aRoomThatCantDimHasNoSlider`; `theBottomRowIsAllLightsOffAndHolidayMode` (**pin**, §6.2).
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*PercentSliderTest*'` and `./gradlew :capability:lights:testDebugUnitTest --tests '*LightsCardTest*' --tests '*LightsWordsTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — the card: the top row (bulb 31 dp, filled `accent` on, outline `mute` off; name; `cardStatus`; the 48 dp ›), `Spacer(Modifier.weight(1f))`, the slider row (`brightness_low`, `HhPercentSlider(enabled = room.on, trackHeight = 20.dp)`, `brightness_high`), 15 dp, the bottom row. The host wires `onDrag`/`onLetGo` to `actions.dragBrightness(room, it)`/`letGoBrightness(room, it)`. If the 20 dp track needs an opt-in: **stop and ask** (header). The tab's sliders are unchanged until 9b.
- [ ] **Step 3: Screenshots** — re-recorded at `rightHalfSize`: every `lights_card_*` and the app's `home_full_*`, `home_lights_*`. **Open each and look at it against the handoff's Lights card** (note 3: the slider fills the middle; the off look of note 18). The expected list: those names.
- [ ] **Step 4: The gate and commit**
```bash
git add core/ui capability/lights app
git commit -m "Give Home's Lights card the main room's brightness slider, off while the room is off, and the scene in its status"
```

---

### Task 10: The Music card's one control row and volume pill (spec §4.2; ruling 8)

**Review:** sonnet.

**Files:**
- Modify: `capability/music/.../ui/MusicCard.kt`, `ui/MusicParts.kt`, `ui/MusicDimens.kt`
- Test: `capability/music/src/test/.../ui/MusicCardTest.kt`, `ui/MusicCardScreenshotTest.kt`

**Interfaces:**
- Consumes: `Icons.VOLUME_UP` (Task 1); `HhPercentSlider(trackHeight = …)` (Task 9); `rightHalfSize` (Task 7).
- Produces: `RoundButton(icon, spoken, tag, enabled = true, primary: Boolean = false, onClick)` (primary: `accent` fill, `accentInk` icon); `@Composable internal fun VolumePill(group: SpeakerGroup, tag: String, onLetGo: (Int) -> Unit, modifier: Modifier = Modifier)` (a 48 dp `surf2` pill: `volume_up`, a 9 dp track, the number at 15 sp W600; spoken `volumeName(group.name)`, `percentWords`); `MusicDimens` card rows of Units.

- [ ] **Step 1: The failing tests** (`MusicCardTest`, at `rightHalfSize`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `oneControlRow`: playing → `music_card_pause`, `music_card_next`, `music_card_volume` share a vertical centre within 1 dp.
- `theVolumePillHasTheSpeakerAndTheNumber`: "30" shown; spoken "Kitchen volume, 30 percent"; letting go calls `onGroupVolume`.
- `nothingPlayingHidesNextAndOffersShuffleInItsPlace`; `pausedShowsPlayAndNextAndNoShuffle`.
- `theArtIs88Dp`.
Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicCardTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — the card: `NowPlayingBlock` at 88 dp art (the name 20 sp W700, the track 17 sp, the artist 14 sp `mute`), `Spacer(weight(1f))`, the row (Play/Pause primary, Next or Shuffle, `VolumePill` `weight(1f)`).
- [ ] **Step 3: Screenshots** — re-recorded: every `music_card_*` and the app's `home_full_*`, `home_lights_music_dark`. **Open each and look at it against the handoff's Music card** (notes 4, 27: bigger art, one row, a labelled volume). The expected list: those names.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/music app
git commit -m "Put the Music card's controls on one row with a labelled volume pill under bigger art"
```

---

### Task 11: "Music lowered": quieters say whether they lowered anything (spec §4.3, §6.3; ruling 9)

**Review:** sonnet.

**Files:**
- Modify: `core/plugin/.../Alerts.kt`; `app/.../shell/ShellOverlays.kt` (`ShellAlerts`); `capability/music/.../MusicQuieter.kt`; every fake `AlertHost`/`AlertQuieter` in tests (door's, app's)
- Test: `app/src/test/.../shell/ShellAlertsTest.kt`; `capability/music/src/test/.../MusicQuieterTest.kt`

**Interfaces:**
- Produces: `interface AlertQuieter { suspend fun quiet(): Boolean; suspend fun restore() }` (KDoc: true when it turned something down); `AlertHost.lowered: StateFlow<Boolean>` (true from the first quieter that answered true until the loud alert goes).

- [ ] **Step 1: The failing tests**
- `ShellAlertsTest.loweredOnlyWhenAQuieterTurnedSomethingDown`: two quieters answering false → `lowered` false after `quieted()`; one answering true → true; `dismiss()` → false.
- `ShellAlertsTest.aFailingQuieterIsNotLowered` (throws → false, the other's true still counts).
- `ShellAlertsTest.aRingReplacingARingKeepsLowered` (a loud `show` over a loud one: stays true, no second quiet).
- `MusicQuieterTest.quietSaysWhetherItLoweredARoom`: nothing playing → false; a playing room at 40 with share 30 → true; the set command failing → false; share 100 → false (40 stays 40).
Run: `./gradlew :app:testDebugUnitTest --tests '*ShellAlertsTest*'` and `./gradlew :capability:music:testDebugUnitTest --tests '*MusicQuieterTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — `ShellAlerts` sets a `MutableStateFlow` from inside its `runAll` (a quieter's `true` sets it); `startRestore` and a non-loud replacement clear it. `MusicQuieter` counts rooms whose `setRoomVolume` returned and whose set is below was.
- [ ] **Step 3: The gate and commit** — Expected: PASS, **no image changed**.
```bash
git add core/plugin app capability
git commit -m "Let the doorbell know whether music was actually turned down"
```

---

### Task 12: The door card: scrim, size, Door history, 60 seconds (spec §2.4, §4.3; rulings 10, 16)

**Review:** sonnet.

**Files:**
- Modify: `capability/door/.../DoorAlerts.kt` (`CARD_MS`, KDoc), `DoorWords.kt`, `ui/DoorCard.kt`, `ui/DoorCardHost.kt`, `ui/DoorDimens.kt`; `app/.../shell/ui/OverlayLayers.kt` (`AlertLayer`)
- Test: `capability/door/src/test/.../DoorAlertsTest.kt`, `ui/DoorCardTest.kt`, `ui/DoorCardScreenshotTest.kt`; `app/src/test/.../shell/ui/AlertLayerTest.kt`, `DoorCardAboveKeyboardTest.kt`, `ShellScreenshotTest.kt`

**Interfaces:**
- Consumes: `AlertHost.lowered` (Task 11); `ShellTokens.cardScrim` (Task 2); `HhButton(trailingIcon = …)` (Task 3); `Icons.VOLUME_DOWN` (Task 1).
- Produces: `const val CARD_MS = 60_000L`; `internal const val DOOR_HISTORY = "Door history"`, `internal const val MUSIC_LOWERED = "Music lowered"`; `DoorCard(card: DoorCardUi, lowered: Boolean, onGotIt: () -> Unit, onHistory: () -> Unit, modifier: Modifier = Modifier)`; `DoorDimens` card rows of Units (`cardWidth = 747.dp`); `AlertLayer(content, hidden, keyboard)` draws the scrim and centres the alert at its own width.

- [ ] **Step 1: The failing tests**
- `DoorAlertsTest.theCardClosesAfterSixtySecondsAndItsRingStaysUnseen` (§2.4, renamed): showing at `CARD_MS − 1` (59 999 ms), gone at 60 000. Its two neighbours' `CARD_MS` arithmetic is unchanged.
- `DoorCardTest`: `musicLoweredShowsOnlyWhenLowered` (§6.3); `doorHistoryIsQuietAndGotItPrimary` (both 60 dp tall, Got it twice Door history's width within 2 dp); `doorHistoryOpensTheDoorTabAndMarksTheRingSeen` (the host with a fake navigator and `DoorAlerts`: `openTab("door")`, the ring seen, the card gone); `theSnapshotFillsTheCardsWidth` (16:9). Each ends with `assertClickablesHaveRoleAndLabel()`.
- `AlertLayerTest.aTapOnTheScrimPressesNothingBeneath`: a button under the scrim's empty part is not pressed, and the tap isn't a touch; `thePinPadDrawsAboveTheAlert` (**pin**) still passes; `theCardCentresOnTheScreenWithoutAKeyboard` and `theCardStaysAboveTheKeyboard…` read the card's own width.
Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorAlertsTest*' --tests '*DoorCardTest*'` and `./gradlew :app:testDebugUnitTest --tests '*AlertLayerTest*' --tests '*DoorCardAboveKeyboardTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — the card: `widthIn(max = 747.dp)`, `surf`, a 1 dp `surf3` edge, radius 29, padding 24, 17 dp gaps, `Modifier.shadow(27.dp, shape)`; the header (the 59 dp `accentSoft` circle with a filled 33 dp `doorbell`, the title 31 sp W700, the line 17 sp `mute`, then the "Music lowered" chip while `lowered`); the snapshot; the buttons row (Got it primary `weight(2f)`, Door history quiet with `trailingIcon = Icons.CHEVRON_RIGHT`, `weight(1f)`, both Large). The host: `lowered` from `LocalAlertHost.current.lowered`; `onHistory = { alerts.gotIt(ring.eventId); navigator.openTab(DOOR_ID) }`. `AlertLayer`: a full-screen `cardScrim` box taking every tap (`detectTapGestures {}`), the alert centred above the keyboard; `ALERT_WIDTH` goes. The DoorAlerts KDoc's "2 minutes" becomes "60 seconds (9 §2.4)".
- [ ] **Step 3: Screenshots** — re-recorded: `door_card_dark`, `door_card_light`, `door_card_snapshot_dark`, `door_card_unrecognised_dark`, `home_door_card_dark`; new `door_card_lowered_dark`. **Open each and look at it against the handoff's 2a** (note 16: everything behind dimmed, the edge and shadow, the picture full width, Got it the big one). The baseline check with those names.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/door app
git commit -m "Show the door card larger over a dimmed screen, with Door history and a Music lowered chip, and close it after 60 seconds"
```

---

### Task 13: The PIN pad's lockout countdown (spec §4.9; ruling 15; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify: `core/access/.../ui/PinPad.kt`, `ui/PinPadDimens.kt`
- Test: `core/access/src/test/.../ui/PinPadTest.kt`, `ChoosePinPadTest.kt` (sizes); `app/src/test/.../shell/ui/ShellScreenshotTest.kt` (`pin_pad_*`); `capability/calendar`'s `detail_pin_*`, `calendar-sheets/09-pin-pad-*` where they draw the pad

**Interfaces:**
- Consumes: `Icons.LOCK_CLOCK` (Task 1); `ShellTokens.cardScrim` (Task 2).
- Produces: `internal const val TOO_MANY_TRIES = "Too many tries"`, `internal const val WAIT_A_MOMENT = "Wait a moment, then try your PIN again."`; `internal fun countdownText(seconds: Int): String` (`m:ss`); `PinPadFrame(…, lock: PinLock? = null, …)` with `internal data class PinLock(val secondsLeft: Int, val firstSeconds: Int)`; `PinPadDimens` rows of Units (`cardWidth = 413.dp`, `key = 77.dp`, `DISABLED_KEY_ALPHA = 0.3f`).

- [ ] **Step 1: The failing tests** (`PinPadTest`, `compose.mainClock.autoAdvance = false`, `nowMillis = { 0L }`, locked until 27 000; each ends with `assertClickablesHaveRoleAndLabel()`)
- `aLockShowsTheCountdownAndTheBar`: "Too many tries" (a heading), "Wait a moment, then try your PIN again.", "0:27"; after 1 000 ms, "0:26".
- `everyKeyIsOffWhileLockedButCancel`: `pin_key_0`…`pin_key_9` and `pin_backspace` not enabled; `pin_cancel` enabled and answers null.
- `theKeysComeBackAtZero`: at 26 999 ms `pin_key_1` not enabled; at 27 000 enabled, "You can try again." shown.
- `theKeypadDoesNotMoveWhenTheLockEnds` (Review Focus 5): `pin_key_1`'s top at 0 ms equals its top at 27 000 ms.
- `theCountdownIsSpokenOnce` (Review Focus 5): `pin_message`'s description is `tooManyTries(27)` at 0 and at 5 000 ms.
- `countdownText(27) == "0:27"`, `(65) == "1:05"`, `(600) == "10:00"`.
- `keysAre76dpCirclesInA400dpCard` becomes `keysAre77dpCirclesInA413dpCard`; `aLockThatArrivesOnARetryDisablesTheKeys` and `keysAreDisabledWhileLocked` stay (**pin**).
Run: `./gradlew :core:access:testDebugUnitTest --tests '*PinPadTest*' --tests '*ChoosePinPadTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — `PinPadSheet` passes `PinLock(secondsLeft, firstSeconds)` while locked. The frame: the 4b card (413 dp, 1 dp `surf3` edge, radius 29) over `cardScrim`; the badge `dangerSoft`/`lock_clock` filled `danger` while locked; the dots-and-message block and the countdown-and-bar block in one `Box`, the unused one `alpha(0f)` and `clearAndSetSemantics {}`; keys and backspace `alpha(0.3f)` while locked; the bar's fill `secondsLeft / firstSeconds` of 173 dp.
- [ ] **Step 3: Screenshots** — re-recorded: `pin_pad_dark`, `pin_pad_light`, `pin_pad_wrong_dark`, `pin_pad_fs15`, `pin_pad_locked_dark`, and every other image drawing the pad (`detail_pin_*`, `calendar-sheets/09-pin-pad-*`, the compare report); new `pin_pad_locked_light`. **Open each and look at it against the handoff's 4b** (note 37: the red lock, the countdown and bar, every key dimmed but Cancel, nothing moved). The expected list is the compare report plus the new name.
- [ ] **Step 4: The gate and commit**
```bash
git add core/access app capability
git commit -m "Show a locked PIN pad's wait as a countdown with a draining bar, every key dimmed but Cancel"
```

---

### Task 14: The night clock and the bare camera glyph (spec §4.10, §6.7; rulings 11, 18; Review Focus 3)

**Review:** sonnet.

**Files:**
- Modify: `app/.../display/ui/SleepLayers.kt`, `display/ui/DisplayDimens.kt`, `MainActivity.kt`
- Test: `app/src/test/.../display/ui/SleepLayersTest.kt`, `SleepLayersScreenshotTest.kt`

**Interfaces:**
- Consumes: `ShellDimens.topBarHeight`, `mainPaddingEnd`, `overlayCameraMark` (Task 5).
- Produces: `NightClockTokens.size = 200.sp`, `NightClockTokens.tracking = (-8).sp`, `NightClockTokens.glyph = Color(0xFF2E3331)`; `SleepLayer(screen: Screen, time: () -> LocalDateTime, cameraRunning: Boolean, onWake: () -> Unit)`.

- [ ] **Step 1: The failing tests** (`SleepLayersTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `theNightClockIs200spTabularAndTight`: the time's layout input has `fontSize == 200.sp`, `letterSpacing == (-8).sp`, `fontFeatureSettings == "tnum"`, colour `#5A5A5A`.
- `theBareGlyphShowsWhileTheCameraRuns` (Review Focus 3): ASLEEP with `cameraRunning = true` → one "The camera is on" node, top-right (its right edge 28 dp from the screen's, its centre 24 dp from the top); NIGHT_CLOCK the same; `cameraRunning = false` → none; it isn't clickable.
- `theNightClockShowsOnlyTheTime` (**pin**) and the wake tests keep passing with the new parameter.
Run: `./gradlew :app:testDebugUnitTest --tests '*SleepLayersTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — the glyph inside both waking layers, outside their merged node's text so the clock's name stays the time; `MainActivity` passes `cameraRunning = running`; `overlayCameraMark` already leaves the sleep layers to the glyph.
- [ ] **Step 3: Screenshots** — re-recorded: `night_clock`, `night_clock_fs15`, `camera_mark_asleep`; new `night_clock_camera`. **Open each and look at it against the handoff's 4c** (notes 38, 39: a bigger, steadier clock; a glyph findable only if you look). The baseline check with those names.
- [ ] **Step 4: The gate and commit**
```bash
git add app
git commit -m "Draw the night clock larger in steady figures, with a bare camera glyph where the Watching pill sits"
```

---

### Task 15: The emulator walk-through on `culvery-5` (spec §1, §7)

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch first, then the gate.)

**Files:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Step 6); any file a fault fix needs.

**Rules.** As Plan 8's Task 13: `emulator-5554` holds a signed release and the household — **never install on it, never uninstall anything, never run a Gradle `install…` task while any device is connected**. Only the `culvery-5` AVD; every `adb` command names `-s "$S"`. The user types every PIN. Put back every Culvery setting changed.

- [ ] **Step 1: The AVD** — `-list-avds` shows `culvery-5` (else stop and ask); start it with `-no-snapshot-save -camera-front emulated`; its serial to `$TMP/culvery-5-serial`; `S=$(cat "$TMP/culvery-5-serial")`.
- [ ] **Step 2: Install the debug build there only** — `./gradlew :app:assembleDebug`; `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 3: Walk the checklist** (tick each with one line)
1. The frame: the mark's equal padding; the Door badge with its ring (a fake ring); the top bar on one line, signed out and signed in; "Night · Auto"/"Day · Auto"; a tap previews ("Day · Preview"), another returns.
2. The Watching pill shows exactly while `adb -s "$S" shell dumpsys media.camera | grep -i -A2 "active camera clients"` names Culvery; over Settings, a sheet and the PIN pad the small mark shows instead; asleep, the bare glyph.
3. Home: the header's weather and strip; with the home location cleared (then put back), the pill opens Settings › Home location; Wi-Fi off before a first fetch on a fresh location: the no-forecast line.
4. Today with 0, 2, 5 and 7 events on the fake calendar (seeded through the debug seed): the top-up under TOMORROW, the scroll with the fade and the pill, "See the week" hidden, the pill opening Calendar on this week; Android's font size at its largest: the scroll treatment, nothing cut.
5. Right column: Lights above Music, half each; Lights only, Music only, neither (Coming up) where the AVD's fakes allow.
6. Lights card: the slider moves the main room; off, it greys and reads Off and won't move; the status names the scene.
7. Music card: one row; the volume pill's number follows a drag; nothing playing shows Shuffle everything in Next's place.
8. A fake ring: the scrim, the card, the chime; with music playing on a fake group the "Music lowered" chip, without it no chip; Door history opens the Door tab and the badge clears; the card goes by itself at 60 s (a stopwatch).
9. Five wrong PINs: "Too many tries", the countdown ticking, the bar draining, every key dimmed but Cancel; at 0:00 the keys come back and nothing moves.
10. Toasts: a short one stays 6 s, a long one longer (a stopwatch); Settings left idle shows the warning with its ring running down.
11. The night clock (night times set to start 3 minutes from now, then put back): its size and figures; the time changing doesn't shimmer.
12. TalkBack (short, with the user's yes): the pill "The camera is on"; "Theme: Day · Preview"; Today's pill; "Door history"; the locked pad's message said once.
- [ ] **Step 4: The fault loop** — each fault a failing test, the fix, the module's tests and the gate, a commit naming the fault, `install -r`, the item re-checked.
- [ ] **Step 5: Put back** every setting changed; `adb -s "$S" emu kill`.
- [ ] **Step 6: Record and close** — under "Plan 9a (Redesign: frame and Home): emulator walk-through": the outcome, faults and commits, taste notes for the design review.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9a's emulator walk-through"
```

---

### Task 16: On the tablet: the redesign at its density, the night clock in the dark (spec §3, §4.10, §7)

**Review:** the controller with the user. **Needs the son's SM-T510 on the cable and the household's yes.**

**Files:** maybe `display/ui/DisplayDimens.kt` (`NightClockTokens`), a dimens value a fault shows, with its test; the follow-ups file.

**Rules.** 4e's tablet rules (`docs/superpowers/plans/2026-10-12-4e-walkthrough.md`, "Rules"): save and restore every setting changed, never touch other apps' data, signed releases with `adb install -r`, **never uninstall Culvery**, the serial in `$TMP/culvery-4e-serial`, device paths in one quoted `shell` string, the names check before any commit.

- [ ] **Step 1: Install the release** — signed per `docs/setup/release.md`; `adb -s "$S" install -r app/build/outputs/apk/release/app-release.apk`; Culvery pinned.
- [ ] **Step 2: At arm's length and across the room** (the user): Home's header, Today's rows and the pill, Lights and Music, the door card (the user presses the doorbell), the locked PIN pad (the user enters wrong PINs), a toast. Note what reads and what doesn't; the 1 dp edges (ruling 1) in daylight.
- [ ] **Step 3: The night clock in the dark** (the user, at the household's bedtime): the room dark, the clock readable from bed and at arm's length, the room not lit by it, the glyph only findable if you look (it shows only while the camera is running, so check it on the day's dark screen too). If the clock lights the room: a dimmer `NightClockTokens.ink` first, its screenshot re-recorded and looked at, the gate, a commit, `install -r`; if it is too big or small, `NightClockTokens.size` the same way.
- [ ] **Step 4: Put back and read the logs** — every setting back; `adb -s "$S" logcat -d | grep -E "ShellAlerts|MusicQuieter|DoorAlerts"`: exception classes only.
- [ ] **Step 5: Record** — "Plan 9a (Redesign: frame and Home): on the tablet": the density read in Task 1, what read and what didn't, the night clock's verdict and any tuned value; no names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9a on the tablet: the redesign at arm's length and the night clock in the dark"
```

---

### Task 17: Final review, merge, install

**Review:** an opus final review of the whole branch against the spec and this plan; the controller. **Needs the SM-T510 for the install.**

- [ ] **Step 1: The review** — opus, the whole branch against spec §2, §4.1–4.3, §4.9, §4.10, §4.13, §6 and this plan; one fix round, each fix with its failing test first and the gate.
- [ ] **Step 2: Merge** — `superpowers:finishing-a-development-branch`: merge `plan-9-redesign` to `main` locally; never push. 9b and 9c continue from the merged `main`.
- [ ] **Step 3: Install** — the release from the merged `main`, signed, `adb -s "$S" install -r app/build/outputs/apk/release/app-release.apk` (4e's rules); Home, a ring and the night clock looked at once more; Culvery pinned.

---

## Spec coverage (9 design → tasks)

| Spec | Task |
|---|---|
| §2.1 speculative features not built | none (none built) |
| §2.2 Coming up goes; Today fills 5; Lights and Music on the right | 7, 8 |
| §2.3 Holiday mode stays on Home's Lights card | 9 |
| §2.4 the door card closes after 60 s | 12 |
| §2.5 an off room looks off (Home's slider) | 9 (the tab: 9b) |
| §3 units, the density | 1, Units, every task |
| §4.1 rail, top bar, Watching pill, sign out, theme control; `border`, `rain` | 2, 5 |
| §4.2 header weather and strip, no Forecast card, the pill, rain tint | 6 |
| §4.2 grid, Lights over Music at half height | 7, 9, 10 |
| §4.2 Today's fill, measured overflow, 2-line titles, See the week hidden | 8 |
| §4.2 Lights card; Music card | 9; 10 |
| §4.3 door card, Door history, Music lowered, 60 s | 11, 12 |
| §4.9 PIN lockout | 13 |
| §4.10 night clock and glyph | 14, 16 |
| §4.13 toasts, the ring, Today's scroll and pill | 4, 8 |
| §6.1 the three layouts | 7 |
| §6.2, §6.3, §6.4, §6.6, §6.7, §6.10 | 9; 11, 12; 3; 1; 5, 14; 4 |
| §4.4–4.8, §4.11, §4.12, §6.5, §6.8, §6.9 | 9b, 9c |
| §7 tests; emulator; tablet | every task; 15; 16 |
