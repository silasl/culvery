# Culvery Plan 21: Christmas, the second seasonal theme

Brainstormed with the user on 2026-11-11. This is follow-ups item 8. Christmas is the second theme on Plan 14's machinery (spec `2026-10-29-culvery-14-seasonal-themes-design.md`), so most of it is a second look bundle. It must be on the tablet before 1 December 2026. Visual choices stay provisional, but Christmas has to look finished on 1 December.

## 1. Intent

On a December evening the wall should read as Christmas from across the room: snow falling behind the clock, warm red and green, the Culvery house snowed in, and the doorbell playing the Christmas music box. Everything the family reads stays as readable as on any other day.

### Goals
- A Christmas theme that switches itself on and off by its dates (default 1 December – 6 January), set on the Seasonal looks page (App settings) like Halloween's, with the same per-theme Preview.
- A full-dress look, as Halloween has: its own colours by day and by night, a Christmas mark in the rail, a Christmas clock face, art in the Today card's corner, and moving art in Home's header.
- The doorbell plays the Christmas music box while Christmas shows and the doorbell is set to *Follow the theme*.
- Take on the Plan 14 leftovers that Christmas needs (§4.4, §4.7).

### Non-goals
- No house devices are touched (Hue, Sonos, plugs, Home Assistant). Party and Holiday mode are unchanged.
- No corner art on cards other than Today (a Plan 14 leftover, still left).
- No sleigh-bell recording (item 9 stays separate).
- No change to Halloween's look, except where shared code moves under it (§4.4).
- No launcher icon change.

### Anti-goals (a failure even if it works)
- Anything the family reads becomes harder to read, including the big clock with snow falling behind it.
- Warnings look like buttons. With red as the accent, warnings must not stay red.
- The night clock changes, or lights the room more.
- The snow or lights keep the screen or CPU busy while the tablet is dark or asleep, or cost more per frame than Halloween's bats after their fix (Plan 14: 90th percentile 11 ms on the SM-T510).

## 2. Rulings carried from Halloween (the user's, unchanged)

1. Switching is by dates only, set per theme on the Seasonal looks page. A per-theme **Preview** is the only manual way in. It ends when turned off or at midnight.
2. Themes change the tablet only, never lights, plugs or speakers.
3. The night clock stays as it is in every theme.
4. A birthday wins over a seasonal look (Plan 16). A birthday in December shows the birthday look for that day.

## 3. Decisions (the user's, 2026-11-11)

1. **Dates:** 1 December – 6 January by default, inclusive, editable like Halloween's. The range runs over New Year, which `SeasonDates.contains` already handles.
2. **Colours (option A, "pine green, berry red"):** a dark pine-green background with holly red as the accent (buttons, switches, sliders, the active tab). Warnings can't stay red beside a red accent, so `danger` turns **amber** in both Christmas versions. The weather (icon and words on Home) is normally amber too, so Christmas's `weather` turns **ice blue**, leaving amber to warnings alone (ruled after the builder check).
3. **Day version:** a warm cream background, a deeper holly red accent, pale grey-green snow, and the house in dark ink, as the normal mark is by day. Approved from the mockup.
4. **Mark (option A, "the house, snowed in"):** Culvery's own house and dove, with snow along the roof, a wreath on the door and the dove in robin red. It replaces `CulveryMark` while Christmas shows, at the same size and place.
5. **Clock face:** **Mountains of Christmas** (bold) for the big Home clock's digits, in the accent red, by day and by night.
6. **Home's header:** snow drifts down across the **whole header, behind the clock** as well as the open band. A **string of fairy lights** hangs across the open band, where the birthday bunting hangs, and twinkles slowly. Halloween's bats stay in the band as they are.
7. **Corner art:** a sprig of holly (two leaves, three berries) in the Today card's top-right corner, where Halloween's cobweb is, **smaller than the first mockup** (about 40 × 25 dp in the mockup's scale). The user isn't sure of the holly yet: its size and look are to be tweaked on the tablet once built.
8. **Doorbell:** with *Follow the theme*, a ring plays the Christmas music box (Plan 18's `CHRISTMAS_MUSIC_BOX`) while Christmas shows. A birthday still plays Happy Birthday.

Mockups: `culvery/.superpowers/brainstorm/111-1794391406/content/` (gitignored): `colours.html`, `mark-v2.html`, `clock-face.html`, `card-art.html`, `lights.html`, `day.html`. Note that `day.html`'s day clock is wrongly in DM Sans; Christmas uses Mountains of Christmas by day too.

## 4. How it works

### 4.1 The theme
- `SeasonalTheme` gains `CHRISTMAS("Christmas", SeasonDates(MonthDay.of(12, 1), MonthDay.of(1, 6)))`, after `HALLOWEEN`. The ranges don't overlap; "first in the list wins" stays the rule.
- `SeasonalTheme.look` maps it to a new `ChristmasLook` (a new `ChristmasLook.kt` and `ChristmasArt.kt` in `core/ui`, beside Halloween's).
- Everything that lists themes already loops over `SeasonalTheme.entries` (checked against the code): the Seasonal looks page's rows (`DisplaySettingsPage.kt`), the dates, Preview, "Next:", and the sound folders (`christmas/` on the tablet). The builder checks each one picks Christmas up with no change, and that the dates sheet shows a range over New Year correctly (start in December, end in January) and the "Next:" line ("Next: Christmas from 1 December").
- Stored dates are per theme, keyed by the theme. A household with no stored Christmas dates gets the defaults.

### 4.2 Colours
`ChristmasNight` and `ChristmasDay`, both `HhColors`, starting values from the mockups (provisional; the builder adjusts any value that fails a check):

| token | Christmas night | Christmas day |
|---|---|---|
| bg | #0F1A14 | #F4EFE6 |
| surf | #16241C | #FFFEFB |
| surf2 | #203126 | #F1EBE1 |
| surf3 | a step above surf2 | a step below surf2 |
| line | #1B2A21 | #E3DACB |
| ink | #F1F4EF | #13201A |
| mute | #9FB0A4 | #4F5E55 |
| accent | #E0464B | #B3262C |
| accentInk | #1A0405 | #FFFFFF |
| accentSoft | #3A1A1B | #F8DEDE |
| accentOnInk | the other version's accent, as Halloween does | the other version's accent |
| danger / dangerSoft / dangerInk | amber, e.g. #F2A33A, checked | a deep amber, e.g. #8A5300, checked |
| weather | ice blue, e.g. #9FD3EA | a deep ice blue, e.g. #2A6A86 |
| rain | as normal night, unless it then clashes with the ice blue | as normal day, the same proviso |
| border | transparent, as normal night | as normal day's border rule (Halloween day uses #DDCFBF; pick the cream equivalent) |

- **Contrast:** `ContrastTest` already loops over `SeasonalTheme.entries`, so Christmas's two sets join it. Every pairing checked today is checked for both. The existing `danger`-against-`accent` hue check (35°) must pass with amber against red in both versions; the mockup values clear it only narrowly (about 36° by night, 39° by day), so keep an eye on it. Its list (`ContrastTest.kt:40`) also runs Halloween's moon, bat and cobweb ink checks; split it so each theme's ink checks run against its own colours.
- **Warnings against the weather:** add a check, for every theme set, that `danger` and `weather` differ clearly, by the same hue measure as `danger` against `accent`. The normal and Halloween sets must pass it as they are (record their numbers; if one fails, tell the user rather than change it). Check the ice blue's contrast wherever `weather` is checked today.
- **The night red will move.** The mockup's night accent #E0464B is about 4.4:1 on `bg` and 3.8:1 on `accentSoft`, under the 4.5 the accent-as-text check needs. *My call:* lighten it just enough to pass (towards #E85A5E), keeping it a clear holly red; the user sees it on the screenshot.
- **Christmas inks** (like `HalloweenInks`), kept in one object so the contrast tests can check what sits over them: snow (night: a low-contrast green-grey such as #3C5546; day: #D3DCD4), the light string's wire, the bulbs (red, gold, green, white; by day the white bulb needs an outline or a different colour to show on cream), the holly's leaves and berries, and the mark's snow, wreath and robin.
- Screenshot tests: Christmas day and night versions of Home, at least one sheet and the door card, next to Halloween's.

### 4.3 The mark
- `ChristmasMark(width)`: the launcher icon's house path (`MARK_HOUSE`) and dove path (`MARK_DOVE`) from `CulveryMark.kt`, plus a snow cap along the roof, a wreath ring with a small red bow on the house front above the door, the house in `ink` and the dove in robin red. The mockup's snow cap is in `mark-v2.html`. It is drawn in the mark's viewport so it takes the mark's place without moving anything. A picture only; TalkBack skips it, as today.
- `MARK_HOUSE` and `MARK_DOVE` are `internal` to `app` today; the Christmas mark lives in `core/ui` with the other looks. The builder moves the two path strings into `core/ui` (and `CulveryMark` reads them from there), or puts the Christmas mark in `app`, whichever keeps the look in one bundle. *My call:* move the paths.
- The snow cap must read at rail size (the first mockup's near-white snow on a white house vanished; the fix was a white cap sitting just above the roof with a thin line in `bg` between). Check at the real size on the screenshot.

### 4.4 Home's header: the sky choosing its own part (Plan 14 leftover)
- The sky composable already gets all of Home's room (`CulveryShell.kt:50`, `matchParentSize`) and narrows itself with `inSkyBand`, so the seam is small.
- Today every sky is placed in the open band (`inSkyBand`, `SkyBand.kt`): right of the clock and date, left of the header items. Christmas needs the snow across the **whole header**, behind the clock, and the lights in the band.
- Give the sky the whole header to draw in, with the band passed to it, so each theme draws where it wants: Halloween's bats and the birthday's balloons and bunting keep to the band; Christmas's snow fills the header and its lights hang in the band. The builder picks the seam (for example the sky gets the header's room and calls `skyBand(...)` for the band, rather than the shell sizing it to the band). Halloween and the birthday must draw exactly as now: their screenshot tests stay unchanged.
- **Snow:** small soft dots, a handful to a couple of dozen, falling slowly with a slight sideways drift, wrapping from the bottom of the header to its top. Low contrast against `bg`. It passes behind the clock, the date and the header items, so it must never lower any of that text's measured contrast below its check: the builder adds a contrast check of the clock's red, the date's `mute` and the header items' text against the snow ink, at the sizes they are, or keeps the flakes faint enough that the check is moot and records the numbers.
- **Lights:** a wire sagging across the band like the bunting's string (same top and sag as `BirthdayDimens.buntingTop`/`buntingSag`, or the builder's equivalent), with small bulbs in red, gold, green and white spaced along it. The bulbs twinkle slowly (each group fades between full and about half over a few seconds, out of step). With the band too narrow for a few bulbs, draw none, as the bunting does.
- **Motion rules, as Halloween's:** it moves only while `drifting` (Home showing, screen awake, animations on), stepping at `SKY_FRAME_MS` (~15 fps). Still, it draws once: snow where it is, lights at full. Asleep or dark, no frames.
- **Cost:** measure on the tablet as Plan 14 did (`gfxinfo`, 60 s on Home, awake). *My call on the bar:* no worse than Halloween after its fix, which was 90th percentile 11 ms, 99th 16 ms, janky frames 1.5%. Pass is a 90th percentile of 12 ms or under and janky frames under 5%. Snow over the whole header is a bigger area than the bats' band, so keep the sky on its own layer and Home in its cached layer, as now. If it misses the bar, fewer flakes come first, then drawing the flakes from one cached picture.

### 4.5 The clock face
- `MountainsOfChristmasClock`, a `TextStyle` like `CreepsterClock` and `CherryBombClock` (`ClockFace.kt`): the bold weight, in `accent` (the look's existing rule: the clock face is drawn in the accent), each digit in a fixed slot so the header's width doesn't change from minute to minute, and a line box that doesn't make the header jump when the theme changes. Check the colon reads at the tablet's size.
- **Font:** the user approved the download on 2026-11-11. Mountains of Christmas from Google Fonts' own repository (`github.com/google/fonts`), the bold file only (subsetting to digits and ":" allowed), with its licence file. **Its licence is the Apache License 2.0, not the OFL**: check the file in the repository says so before committing, and commit it as `core/ui/licenses/Apache-MountainsOfChristmas.txt` next to the others. Pin the commit and record the sha256, as for Creepster. Bundle as `core/ui/src/main/res/font/mountains_of_christmas_bold.ttf`.
- The date, the night clock and every other text stay in DM Sans.

### 4.6 Corner art
- The holly is Christmas's `cardCorner`, drawn behind the Today card's content inside its clip, as the cobweb is: two leaves and three berries in muted Christmas inks, faint enough to sit behind content. Its size is a named dimension (like `HalloweenDimens.webSpan`) so the user's tweak on the tablet is one number. It must not change the "+" or "See the week" buttons' touch areas. The builder decides whether the "+" moves; the mockup moved it left.

### 4.7 The doorbell
- Plan 18 already ties a sound to a theme (`BuiltInSound.theme`, `SoundLibrary.forTheme`), so the Plan 14 leftover "the doorbell sound as part of a theme's look" is already met. For Christmas: `CHRISTMAS_MUSIC_BOX`'s `theme` becomes `SeasonalTheme.CHRISTMAS`.
- *Follow the theme* then plays it while Christmas shows; a sound in a `christmas/` folder on the tablet overrides it, as `halloween/` does for the organ (the folder name comes from the enum, so no new code). A birthday still plays Happy Birthday. Choosing "Christmas music box" outright in Settings › Door works as today.
- Check that the music box still appears in the pad and doorbell lists once it has a theme. Halloween's organ, tagged the same way, does; the builder confirms nothing filters themed sounds out.
- Item 10 (whether a birthday overrides a doorbell set to one sound) stays open and separate.

### 4.8 The 29 February fault (Plan 14 leftover)
- A range starting 29 February starts on 1 March in common years, but "Next:" and the dates sheet say 28 February, and saving unchanged rewrites the start to 28 February. *My call* on the rule: a stored 29 February stays 29 February until the user picks another day; in a common year it means 1 March for switching, and the "Next:" line says the day it will really start ("from 1 March"). The dates sheet, opened in a common year, shows 1 March picked with a line saying the saved day is 29 February, and Save stays off until a day is changed, so opening and saving unchanged can't rewrite it. Unit-test a leap year and a common year for switching, "Next:" and the sheet.

## 5. Constraints and house rules
- Branch `plan-21-christmas` in the one `culvery` checkout (no worktrees). Plain-sentence commits, no trailers. Merge to main and push only on the user's say; check for personal data before any push.
- No personal data in logs or committed files. No deprecated APIs without asking the user.
- Test gate: `./gradlew testDebugUnitTest verifyRoborazziDebug`. Halloween's and the birthday's Home, sheet and door card baselines must not change. The Seasonal looks page's baselines (`settings_display_preview_*`, `settings_seasonal_birthday_dark`, `settings_display_next_*`, the dates-sheet shots) and the Display row's summary change because a Christmas row joins the page: re-record those, and look at each one before accepting it.
- Subagents use `C:/t9` for scratch.
- On the tablet (SM-T510): release build, `adb install -r` only, never uninstall or wipe. No Hue, Sonos or plug changes.
- **Time:** on the tablet by about 24 November 2026, so the user can preview it in daylight and at night before 1 December. One build, not staged: the date editing and preview already exist.

## 6. Left to the builder
- Final colour values, within §4.2's checks.
- The sky seam (§4.4), the snow's count, sizes and speed, the bulbs' spacing, size and twinkle.
- Whether to move the house paths or the mark (§4.3; my call is to move the paths).
- The holly's paths and starting size, and whether the "+" moves.
- Whether to subset the font.

## 7. Walk-through checks
- **Emulator** (themes are on App settings › Seasonal looks): Christmas by day and by night (status bar Day/Night preview); Preview on and off and ending at midnight; inside the dates (set the clock to 12 December) and over New Year (31 Dec → 1 Jan stays Christmas; 6 Jan → 7 Jan ends it); "Next: Christmas from 1 December" outside both themes' dates; editing Christmas's dates; 29 February (§4.8); Halloween unchanged; a birthday in December wins; the snow and lights stop with animations off, under Settings and the PIN pad, and when dark; Settings › Door's Play chime with *Follow the theme* plays the music box.
- **Tablet (release, `install -r`):** the user looks at it in daylight and at night from across the room, including the snow behind the clock; frame cost measured (§4.4); the holly's size tweaked to taste; the night clock unchanged.

## 8. Not in this build, on purpose
- Corner art on cards other than Today (Plan 14 leftover).
- A real sleigh-bell recording (item 9).
- Sound pads on Sonos (item 7). Birthdays overriding a single chosen doorbell sound (item 10).
