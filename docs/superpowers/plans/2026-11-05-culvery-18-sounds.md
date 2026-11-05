# Culvery Plan 18: Doorbell sounds and sound buttons — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One store of sounds, built-in and on the tablet, feeds Settings › Door's doorbell list and up to three sound pads in Home's One-tap tray. Pads never play over a ring, at night, in quiet hours or on Holiday.

**Architecture:**
- A new `:core:sound` module holds the built-in sounds (moved from `capability/door`, plus three new generated ones), the folder reader, and one `SoundPlayer` that both the door and the pads use, so a ring can stop a pad.
- The door keeps its alert logic. It stores a sound id instead of an enum, and binds a new `DoorQuiet` seam (`core/house`) so the pads can see a ring card and quiet hours.
- The pads belong to `capability/party` (the Modes capability, which already owns the tray's modes and the Modes page). The tray grid gains a third-width button. Modes drop to row 1 and Taekwondo moves into the sound row.

**Tech Stack:** Kotlin, Compose, Hilt, DataStore, MediaPlayer, JUnit4, Robolectric, Roborazzi. Python standard library for generators. ffmpeg (already at `C:\Users\si\tools\ffmpeg-…\bin`) only for the private scripts in culvery-notes.

**Spec:** `docs/superpowers/specs/2026-11-05-culvery-18-sounds-design.md` (5b883082, with the tray change made after it). Read it before any task.

## Global Constraints

- **Nothing sourced in the repo.** No film clip, no file named after a real film tune, and no script that writes one out. Tests use made-up file names such as `Test Honk.wav`, `halloween/Test Howl.ogg`. The sourced clips and their scripts live in `C:\Users\si\code\siland\culvery-notes\sounds\` only (Task 9).
- **Logs never name a tablet file.** Say "a tablet sound". Never log a birthday name. The pad says "Happy Birthday" only.
- **Ids:** `builtin:<key>` and `file:<path inside sounds/, forward slashes>`. Keys: `classic`, `organ`, `sad_trombone`, `happy_birthday`, `christmas_bells`.
- **Folder:** `context.getExternalFilesDir(null)/sounds`. Extensions `.wav`, `.mp3`, `.ogg`, case-insensitive. Theme subfolder names are `SeasonalTheme.name.lowercase()` (`halloween`, later `christmas`).
- **Caps:** a tablet sound stops at 8 000 ms on a pad and 10 000 ms as the doorbell. Built-in sounds play to their end.
- **Copy (house style, curly apostrophes):** `Sound buttons`, `Pad 1`–`Pad 3`, `None`, `On this tablet`, `Not on this tablet any more`, `Shows when there’s room in the tray`, `On a birthday, pad 1 plays Happy Birthday.`, `Couldn’t play`, block reasons `Doorbell`, `Night`, `Holiday on`, `Quiet hours`. Follow the theme's line becomes `The season’s sound while a seasonal theme shows, the classic chime otherwise.`
- **Audio:** the chime's `CHIME_ATTRIBUTES` (notification event, sonification) for every sound, at Settings › Door's volume. Pads don't duck music.
- **Sounds, fonts and icons:** download none. If an icon the plan names isn't in the bundled icon font, use one that is and say so in the report. Don't add a font.
- No deprecated APIs without asking the user. Comments say only what the code can't. Files stay UTF-8 with their existing line endings.
- Commits have no trailers of any kind. Don't push or merge.
- Roborazzi re-records rewrite unrelated baselines. Restore every one this task didn't mean to change, file by file, then run `verifyRoborazziDebug`.

## Rulings against the spec (made while planning; the spec is the authority)

1. **Where the pads live:** in `capability/party` beside the modes, not in a capability of their own. The Modes page and the tray room both live there.
2. **Pad 3 waits by room, not by name:** a new `ModesShelf.soundRoom` is 3 less the other capabilities' third-width buttons. Pads 1..room show, so pad 3 waits while Taekwondo is there. Its Settings row says "Shows when there’s room in the tray", without naming Taekwondo.
3. **Taekwondo sits right of the pads:** the pads take orders 81–83 and Taekwondo stays at 90.
4. **The birthday reaches the pads through `ShowingTheme`:** it gains `birthdayShowing: StateFlow<Boolean>`, which reads the same `WallDress` Home wears, preview included. A pad 1 set to None still shows Happy Birthday on a birthday.
5. **A theme's sound moves out of its look:** `SeasonalSound` and `SeasonalLook.sound` go. `BuiltInSound.theme` says which built-in sound belongs to which theme. Christmas bells has no theme until a Christmas theme exists.
6. **Block reasons show as the sound row's line:** one line under the row, carried by the first pad shown. "Couldn’t play" shows on the failed pad's own line for 4 s.

## Review Focus

1. **A pad sound still playing when a ring's card appears:** it must stop before the 500 ms quieting and the chime (Task 5: `aNewRingHushesThePadsBeforeQuieting`).
2. **A file pushed while the app runs, or deleted after it was chosen:** the lists pick it up on the page's next open. A chosen file that's gone plays the classic chime at the door and hides its pad (Task 2: `refreshFindsANewFile`; Task 4: `aGoneFileRingsTheClassicChime`; Task 7: `aGoneFileHidesItsPad`).
3. **The stored `CLASSIC`, `ORGAN` and `FOLLOW_THEME` values on the tablet today:** they must keep their meaning (Task 4: `oldStoredChoicesKeepTheirMeaning`).
4. **An untrimmed 19 s clip:** it must stop at 8 s on a pad and 10 s at the door (Task 3: `aTabletSoundStopsAtItsCap`).
5. **Night not known yet just after a restart:** it counts as night, so the pads are greyed (Task 7: `unknownNightGreysThePads`).

---

### Task 1: `:core:sound` and the generated sounds

**Files:**
- Create: `core/sound/build.gradle.kts` (as `core/plugin`'s: library, Hilt, no Compose), `core/sound/src/main/AndroidManifest.xml` if the convention plugin needs one, and `core/sound/src/main/java/uk/co/siland/culvery/core/sound/BuiltInSound.kt`.
- Move: `capability/door/src/main/res/raw/door_chime.wav` and `door_organ.wav` to `core/sound/src/main/res/raw/`, using `git mv`.
- Create: `tools/sound/sad_trombone.py`, `tools/sound/happy_birthday.py`, `tools/sound/christmas_bells.py`, and their outputs `core/sound/src/main/res/raw/sad_trombone.wav`, `happy_birthday.wav`, `christmas_bells.wav`.
- Modify: `tools/sound/door_chime.py` and `door_organ.py` (their `OUT` points at core/sound); `settings.gradle.kts` (`include(":core:sound")`); `app/build.gradle.kts`.
- Test: `core/sound/src/test/java/uk/co/siland/culvery/core/sound/BuiltInSoundTest.kt`.

**Interfaces:**
- Produces: `enum class BuiltInSound(val key: String, val label: String, @RawRes val res: Int, val theme: SeasonalTheme?)` with, in this order, `CLASSIC("classic", "Classic chime", …, null)`, `ORGAN("organ", "Haunted organ", …, HALLOWEEN)`, `SAD_TROMBONE("sad_trombone", "Sad trombone", …, null)`, `HAPPY_BIRTHDAY("happy_birthday", "Happy Birthday", …, null)`, `CHRISTMAS_BELLS("christmas_bells", "Christmas bells", …, null)`. Plus `val BuiltInSound.id: String get() = "builtin:$key"`. `:core:sound` depends on `:core:ui` for `SeasonalTheme`.

Generators follow `door_organ.py`: standard library only, 22.05 kHz 16-bit mono, the docstring header, and `TARGET_RMS = 6970.0` over the first 3.5 s.
- **Sad trombone:** D4, C♯4, C4 at 0.45 s each, then B3 held 1.6 s with a 6 Hz vibrato of ±30 cents. A brass tone (odd and even harmonics, with the upper ones swelling over each note's first 0.1 s for the "wah"). About 3.2 s in all.
- **Happy Birthday:** in C at 3/4, about 100 bpm, once through (G G A G C B · G G A G D C · G G G′ E C B A · F F E C D C). Music-box tone: bright partials, fast decay. About 12 s, no more than 15.
- **Christmas bells:** "Jingle bells, jingle bells, jingle all the way" (E E E · E E E · E G C D E) on bright bells, as `door_chime.py`'s partials, over a sleigh-bell shimmer (short noise bursts band-passed high, on the beat). Under 8 s.

- [ ] **Step 1:** Write `BuiltInSoundTest`: `idsAreBuiltinKeys` (`CLASSIC.id == "builtin:classic"` and so on for all five), `onlyTheOrganHasATheme` (`ORGAN.theme == SeasonalTheme.HALLOWEEN`, the rest null), and a Robolectric `everySoundOpens` (each `res` opens with `context.resources.openRawResourceFd` and has a length > 0).
- [ ] **Step 2:** Run `./gradlew :core:sound:testDebugUnitTest`. Expect FAIL (no module).
- [ ] **Step 3:** Make the module, move the two WAVs, and write `BuiltInSound`. Point `DoorSound` at `uk.co.siland.culvery.core.sound.R.raw.*` for now, so the door still builds. Task 4 removes it.
- [ ] **Step 4:** Write the three generators and run each from the repo root. Check each output's length with `python -c "import wave,sys;w=wave.open(sys.argv[1]);print(w.getnframes()/w.getframerate())" <file>`: trombone 3–4 s, birthday 10–15 s, bells under 8 s.
- [ ] **Step 5:** Run `./gradlew :core:sound:testDebugUnitTest :capability:door:testDebugUnitTest`. Expect PASS.
- [ ] **Step 6:** Commit: `Add core:sound with the built-in sounds and three new generated ones`.

### Task 2: The sound library

**Files:**
- Create: `core/sound/src/main/java/uk/co/siland/culvery/core/sound/Sounds.kt`, `SoundLibrary.kt`.
- Test: `core/sound/src/test/java/uk/co/siland/culvery/core/sound/SoundsFolderTest.kt`, `SoundLibraryTest.kt`.

**Interfaces:**
- Consumes: `BuiltInSound` (Task 1).
- Produces:
  - `sealed interface SoundSource { data class BuiltIn(val sound: BuiltInSound) : SoundSource; data class OnTablet(val file: File) : SoundSource }`
  - `data class Sound(val id: String, val name: String, val source: SoundSource, val theme: SeasonalTheme?)`, whose `toString()` leaves out the name and the file.
  - `val Sound.onTablet: Boolean`
  - `fun readSoundsFolder(root: File?): List<Sound>`: tablet sounds only, sorted alphabetically by path (case-insensitive). A null or missing root gives an empty list.
  - `@Singleton class SoundLibrary internal constructor(root: () -> File?, io: CoroutineDispatcher)`, with an `@Inject` constructor taking `@ApplicationContext` that resolves `getExternalFilesDir(null)?.resolve("sounds")`. It has:
    - `val sounds: StateFlow<List<Sound>>`: built-ins in enum order, then tablet sounds. Read once at construction on `io`.
    - `suspend fun refresh()`
    - `fun byId(id: String?): Sound?`
    - `val choices: Flow<List<Sound>>`: `sounds` without Happy Birthday.
    - `fun forTheme(theme: SeasonalTheme?): Sound`: the theme's tablet subfolder first alphabetically, else the built-in whose `theme` matches, else Classic chime. Null gives Classic chime.

- [ ] **Step 1:** Write `SoundsFolderTest` (JUnit `TemporaryFolder`):
  - `aFileIsNamedWithoutItsExtension`: `Test Honk.wav` gives name `Test Honk`, id `file:Test Honk.wav`, theme null.
  - `aThemeFolderTagsItsFiles`: `halloween/Test Howl.ogg` gives theme `HALLOWEEN` and id `file:halloween/Test Howl.ogg`.
  - `otherFilesAndFoldersAreIgnored`: `.txt`, a hidden `.x.wav`, `other/Test.wav` and `halloween/deeper/Test.wav` are all skipped.
  - `extensionsIgnoreCase`: `TEST.MP3` is read.
  - `aSharedNameKeepsTheFirstPath`: `Test Honk.wav` and `halloween/Test Honk.mp3` give one sound with id `file:Test Honk.wav`.
  - `noFolderIsNoSounds`: a null root and a missing root both give `emptyList()`.
- [ ] **Step 2:** Write `SoundLibraryTest`:
  - `builtInsComeFirst`: the first five ids are the built-ins in enum order.
  - `refreshFindsANewFile`: a file added after construction is in `sounds` after `refresh()`.
  - `choicesLeaveOutHappyBirthday`
  - `aThemeFileBeatsTheBuiltIn`: with `halloween/Test Howl.ogg` present, `forTheme(HALLOWEEN)` is that file. Without it, it's `ORGAN`.
  - `noThemeIsTheClassicChime`
  - `byIdOfAGoneFileIsNull`
- [ ] **Step 3:** Run `./gradlew :core:sound:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 4:** Implement both files.
- [ ] **Step 5:** Run the same. Expect PASS.
- [ ] **Step 6:** Commit: `Read the tablet's sounds folder into one list with the built-in sounds`.

### Task 3: One player

**Files:**
- Create: `core/sound/src/main/java/uk/co/siland/culvery/core/sound/SoundPlayer.kt`.
- Move: `CHIME_ATTRIBUTES` from `capability/door/.../Chime.kt` into it, and `MediaPlayerChimeTest` into `core/sound/src/test/.../SoundPlayerTest.kt`, reworked.

**Interfaces:**
- Consumes: `Sound`, `SoundSource` (Task 2).
- Produces: `@Singleton class SoundPlayer internal constructor(open: (Sound) -> MediaPlayer?, scope: CoroutineScope)`, with an `@Inject` constructor (`@ApplicationContext`, `@ApplicationScope`) that opens `BuiltIn` through `MediaPlayer.create(context, res, CHIME_ATTRIBUTES, …)` and `OnTablet` through `setDataSource(file.path)` + `prepare()`. It has:
  - `fun playDoorbell(sound: Sound, volume: Float): Boolean`: stops any pad sound first. If `sound` won't open or start, it plays `BuiltInSound.CLASSIC`.
  - `fun playPad(pad: Int, sound: Sound, volume: Float): Boolean`: stops the current pad sound first. False when it won't open or start.
  - `fun stopPads()`
  - `val padPlaying: StateFlow<Int?>`: the pad whose sound is playing, cleared at its end, stop or error.
  - `const val PAD_CAP_MS = 8_000L` and `const val DOORBELL_CAP_MS = 10_000L`: applied to `OnTablet` sounds by a job on `scope` that stops and releases the player.
  - Settings' Play buttons call `playPad(PREVIEW_PAD, …)` with `const val PREVIEW_PAD = 0`.
- Logs: `Log.w(TAG, "A tablet sound couldn't play")` or `"The ${builtIn.key} sound couldn't play"`. Never a file name.

- [ ] **Step 1:** Write `SoundPlayerTest` (Robolectric, as `MediaPlayerChimeTest` is now, with `StandardTestDispatcher`). Keep the existing chime tests, adapted. Add:
  - `aPadStopsTheLastPad`
  - `aDoorbellStopsThePads` (`padPlaying` null, the pad's player released)
  - `aTabletSoundStopsAtItsCap` (advance 8 000 ms on a pad, 10 000 ms at the door)
  - `aBuiltInSoundHasNoCap` (still playing after 10 000 ms)
  - `aBrokenDoorbellSoundRingsTheClassicChime`
  - `aBrokenPadSoundIsFalse` (`padPlaying` null)
- [ ] **Step 2:** Run `./gradlew :core:sound:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. Keep `MediaPlayerChime`'s strong-reference set (its comment says why).
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `One sound player for the doorbell and the pads`.

### Task 4: The door uses the store

**Files:**
- Modify: `capability/door/.../Chime.kt` (only the `Chime` interface stays), `DoorSettings.kt`, `DoorAlerts.kt`, `DoorWords.kt`, `ui/DoorSettingsPage.kt`, `di/DoorModule.kt`, `capability/door/build.gradle.kts`. Remove `SeasonalSound` and `SeasonalLook.sound` from `core/ui/.../Seasonal.kt` and `HalloweenLook.kt`, and their check in `BirthdayLookTest`.
- Test: `DoorSettingsTest.kt`, `DoorAlertsTest.kt`, `ui/DoorSettingsPageTest.kt` (or whichever page test exists), `ChimeTest.kt`.

**Interfaces:**
- Consumes: `SoundLibrary`, `SoundPlayer`, `Sound` (Tasks 2–3).
- Produces:
  - `interface Chime { fun play(sound: Sound, volume: Float): Boolean; fun hushPads() }`, bound to `SoundPlayer`'s `playDoorbell` and `stopPads`.
  - `DoorPrefs.doorbell: String`: `FOLLOW_THEME_ID = "follow"` or a sound id. The stored key stays `doorbell_sound`. Reading maps the old values: `FOLLOW_THEME` to `follow`, `CLASSIC` to `builtin:classic`, `ORGAN` to `builtin:organ`.
  - `fun DoorPrefs.doorbellSound(library: SoundLibrary, showing: SeasonalTheme?): Sound`: `follow` gives `library.forTheme(showing)`. An id gives `library.byId(id)`, or Classic chime when the file has gone.
  - `DoorbellChoice` and `DoorSound` go.
- Settings › Door: under "Doorbell sound", Follow the theme comes first (its new line), then one `HhRadioRow` per `library.choices` sound, tagged `door_doorbell_<id>`, with the line "On this tablet" for tablet ones. A stored file id that has gone gets a selected row named from its file, with the line "Not on this tablet any more". The page calls `library.refresh()` once when it opens. Play chime plays `doorbellSound(…)`.

- [ ] **Step 1:** Write the failing tests:
  - `oldStoredChoicesKeepTheirMeaning` (preferences written as `FOLLOW_THEME`, `CLASSIC` and `ORGAN` read back as `follow`, `builtin:classic` and `builtin:organ`)
  - `followTheThemeAtHalloweenIsTheOrgan`
  - `aGoneFileRingsTheClassicChime`
  - on the page: `everyChoiceIsOffered` (the five built-ins less Happy Birthday, plus a fake `Test Honk.wav` with "On this tablet") and `aGoneChoiceSaysSo`
- [ ] **Step 2:** Run `./gradlew :capability:door:testDebugUnitTest :core:ui:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. Existing tests that build `DoorPrefs(doorbell = DoorbellChoice.X)` switch to ids.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Settings › Door offers every sound, built-in or on the tablet`.

### Task 5: A ring hushes the pads; the DoorQuiet seam

**Files:**
- Modify: `core/house/.../HouseSeams.kt` (add the seam), `core/house/.../di/*Module.kt` (`@BindsOptionalOf`), `capability/door/.../DoorAlerts.kt`, `capability/door/.../di/DoorModule.kt`.
- Create: `capability/door/.../DoorQuietness.kt`.
- Test: `DoorAlertsTest.kt`, `capability/door/src/test/.../DoorQuietnessTest.kt`.

**Interfaces:**
- Produces: `interface DoorQuiet { val ringShowing: Flow<Boolean>; suspend fun quietNow(): Boolean; val volume: Float }` in `core/house`, bound optionally. `DoorQuietness @Inject constructor(alerts: DoorAlerts, settings: DoorSettings, zone: HouseholdZone, clock: WallClock) : DoorQuiet`: `ringShowing` is `alerts.showing.map { it != null }`, `quietNow()` is `quietAt(clock.nowMillis(), zone.current(), settings.current.value)`, and `volume` is `settings.current.value.volume`.
- `DoorAlerts.onRecorded`: on a new ring that raises the card, call `chime.hushPads()` straight after `_showing` is set, before `host.quieted()` and whether or not the chime then plays.

- [ ] **Step 1:** Write the tests: `aNewRingHushesThePadsBeforeQuieting` (the fake `Chime` and `AlertHost` record order: `hushPads` comes before `quieted`), `aStoredRingDoesNotHush` (night, Holiday, away), `ringShowingFollowsTheCard`, `quietNowReadsQuietHours`, and `volumeIsTheDoorbells`.
- [ ] **Step 2:** Run `./gradlew :capability:door:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `A ring stops a pad sound, and the pads can see the door`.

### Task 6: The tray takes a sound row

**Files:**
- Modify: `core/plugin/.../TrayButton.kt` (`TrayWidth.THIRD`, and the KDoc on orders: pads 81–83, Taekwondo 90), `app/.../shell/ui/HomeTray.kt` (`trayRows`), `capability/party/.../ModesShelf.kt`, `capability/taekwondo/.../TaekwondoCapability.kt` (`TrayWidth.THIRD`), and `TaekwondoTile` if it doesn't fit a third.
- Modify: `core/ui/.../Seasonal.kt` (`ShowingTheme.birthdayShowing`), and `app/.../display/SeasonalThemes.kt` (`dress.map { it.birthday != null }`, `stateIn`).
- Test: `app/src/test/.../shell/ui/TrayRowsTest.kt`, `capability/party/src/test/.../ModesShelfTest.kt`, the `SeasonalThemes` test, and every `ShowingTheme` fake.

**Interfaces:**
- Produces:
  - `trayRows`: half-width buttons fill row 0 (two at most). Third-width buttons fill row 2 (three at most, in order). Full-width buttons fill row 1, then any empty row. Anything past those is left off.
  - `ModesShelf`: `HALF_SPOTS = 2`, its KDoc citing the redesign's row 1. `val soundRoom: Flow<Int>` is 3 less the third-width buttons of capabilities other than Holiday and Party, using the same wait as `room`. `room` and `soundRoom` share one reading of the other capabilities.
  - `ShowingTheme.birthdayShowing: StateFlow<Boolean>`

- [ ] **Step 1:** Write the tests:
  - `TrayRowsTest`: `thirdsFillTheLastRow`, `aFourthThirdIsLeftOff`, `halvesFillOnlyTheFirstRow`, `aFullButtonTakesTheMiddleRow`. Update the existing cases that expect halves in row 2.
  - `ModesShelfTest`: `modesHaveTwoSpots`, `aThirdOfAnotherCapabilityTakesASoundSpot` (Taekwondo-like: `soundRoom == 2`), `noOtherThirdsLeavesThreeSoundSpots`.
  - `SeasonalThemes`: `aBirthdayPreviewShowsABirthday`.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*TrayRows*' --tests '*SeasonalThemes*' :capability:party:testDebugUnitTest :capability:taekwondo:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS. Re-record the shell's Roborazzi tray baselines, check by eye that Taekwondo reads in a third, and restore any unrelated ones.
- [ ] **Step 5:** Commit: `Give the tray a row of three for sounds, and modes the first row`.

### Task 7: The sound pads

**Files:**
- Create: `capability/party/.../SoundPads.kt` (the store and which pads show), `PadGate.kt`, `ui/SoundPadTile.kt`.
- Modify: `ModesCapability.kt` (`trayButtons` adds the pads), `ModesWords.kt`, `capability/party/build.gradle.kts` (`:core:sound`), `di/*`.
- Test: `capability/party/src/test/.../SoundPadsTest.kt`, `PadGateTest.kt`, `ui/SoundPadTileTest.kt` (Roborazzi).

**Interfaces:**
- Consumes: `SoundLibrary`, `SoundPlayer`, `Optional<DoorQuiet>` (its `volume`, or 1.0 when the door isn't bound), `HouseMode`, `NightHours`, `ShowingTheme`, `ModesShelf.soundRoom`.
- Produces:
  - `class SoundPadsStore` (its own DataStore file, `sound_pads`). `val pads: Flow<List<String?>>` holds three entries, where null is None. The stored keys are `pad_1` to `pad_3`, and `"none"` stands for None. Defaults are `builtin:sad_trombone`, `builtin:organ`, null. `suspend fun setPad(pad: Int, id: String?)` takes pad 1–3.
  - `data class PadUi(val pad: Int, val sound: Sound)` and `fun shownPads(stored: List<String?>, room: Int, library: (String?) -> Sound?, birthday: Boolean): List<PadUi>`. It takes pads 1..room, and pad 1 is Happy Birthday when `birthday`. It then drops None and gone files.
  - `enum class PadBlock(val line: String) { DOORBELL("Doorbell"), NIGHT("Night"), HOLIDAY("Holiday on"), QUIET("Quiet hours") }` and `class PadGate @Inject constructor(...)` with `suspend fun blockedNow(): PadBlock?`, checked in that order. A night not known yet counts as night. Quiet hours count only when the door is bound.
  - Tray buttons: `TrayButton("sound.pad$pad", 80 + pad, TrayWidth.THIRD, line, …) { SoundPadTile(...) }`. The first pad shown carries the block line. A failed play shows "Couldn’t play" on that pad's line for 4 000 ms.
  - `SoundPadTile`: an `s2` tile with radius 26, the `volume_up` icon in accent over the name (19/700, one line, ellipsised). While playing, the icon is `stop`. While blocked, it's at 40% and the tap does nothing. It has a 150 ms press flash, as the tray's other tiles do. A tap calls `gate.blockedNow()`, then `player.playPad` or `player.stopPads()` when that pad is already playing. The block state refreshes each minute, and whenever `ringShowing` or `holiday` changes.

- [ ] **Step 1:** Write `SoundPadsTest`:
  - `defaultsAreTromboneOrganNone`
  - `noneIsStored`
  - `taekwondoKeepsPadThreeWaiting` (`room = 2`, so pads 1 and 2)
  - `aGoneFileHidesItsPad`
  - `aBirthdayMakesPadOneHappyBirthday` (also when pad 1 is None)
  - `otherPadsKeepTheirSoundsOnABirthday`
- [ ] **Step 2:** Write `PadGateTest`:
  - `aRingCardBlocksFirst`
  - `unknownNightGreysThePads`
  - `holidayBlocks`
  - `quietHoursBlockOnlyWithADoor`
  - `aClearDayBlocksNothing`
- [ ] **Step 3:** Write `SoundPadTileTest`: screenshots of the row with 2 and 3 pads, with one playing, blocked with the "Night" line, and on a birthday. Plus `tappingThePlayingPadStopsIt` and `aBlockedPadDoesNotPlay`, using a fake player.
- [ ] **Step 4:** Run `./gradlew :capability:party:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 5:** Implement.
- [ ] **Step 6:** Run the same. Expect PASS. Record the new baselines and restore any unrelated ones.
- [ ] **Step 7:** Commit: `Sound pads in the tray, quiet at night, on Holiday and while the doorbell rings`.

### Task 8: Settings › Modes: Sound buttons

**Files:**
- Create: `capability/party/.../ui/SoundButtonsSection.kt`.
- Modify: `ui/ModesPage.kt` (the section under `ModesList`), `ModesWords.kt`. Modify the Show on Home control only if it doesn't already stop at `room` with a "tray is full" line.
- Test: `capability/party/src/test/.../ui/SoundButtonsSectionTest.kt`, plus the Modes page test.

**Interfaces:**
- Consumes: `SoundPadsStore`, `SoundLibrary.choices`, `SoundPlayer.playPad(PREVIEW_PAD, …)`, `ModesShelf.soundRoom`.
- Produces: an `HhSectionLabel("Sound buttons")`, then three `HhListRow`s tagged `sound_pad_<n>`. Each row's title is `Pad n` and its subtitle is one of: the sound's name; `None`; `<name> · Not on this tablet any more`; or `Shows when there’s room in the tray`, for a pad past `soundRoom`. Each row has a trailing Play button (`sound_pad_<n>_play`, hidden for None). A tap opens an `HhSheet` with an `HhRadioRow` for None and one per choice, tablet ones lined "On this tablet". Choosing a sound closes the sheet and stores it. Under the rows, the note `On a birthday, pad 1 plays Happy Birthday.` The section calls `library.refresh()` once when it opens. Edits follow the page's existing access check.

- [ ] **Step 1:** Write the tests:
  - `threePadsWithTheirSounds`
  - `aWaitingPadSaysSo` (`soundRoom = 2`)
  - `choosingASoundStoresIt`
  - `choosingNoneStoresNone`
  - `aGoneFileSaysSo`
  - `playPlaysThePreview`
  - `showOnHomeStopsAtTwo`, if the existing tests don't already pin this at room 2
- [ ] **Step 2:** Run `./gradlew :capability:party:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Choose the sound pads on the Modes page`.

### Task 9: Private sounds (culvery-notes only)

Nothing in this task touches the repo.

**Files:**
- Create: `C:\Users\si\code\siland\culvery-notes\sounds\prepare.sh` and `push.sh`. Then `sources\` (copies of the user's `C:\Users\si\Desktop\sounds\*.mp3`) and `out\`.

**Steps:**
- [ ] **Step 1:** `prepare.sh` measures the door chime's loudness (`ffmpeg -i core/sound/src/main/res/raw/door_chime.wav -af ebur128 -f null -`, integrated LUFS). For each source it then writes `out/<Tidy Name>.mp3`: the first 8 s with a 0.3 s fade at the cut when the source is longer, leading silence trimmed, and `loudnorm` to the chime's LUFS. The names are `Imperial March`, `Indiana Jones`, `Superman`, `Jaws` and `Wilhelm scream`.
- [ ] **Step 2:** Run it, and check each output is 8 s or less with `ffprobe`.
- [ ] **Step 3:** `push.sh` runs `adb -s <tablet serial> shell mkdir -p /sdcard/Android/data/uk.co.siland.culvery/files/sounds`, then pushes each `out/*.mp3` there. Run it after Task 10's install.
- [ ] **Step 4:** Commit in culvery-notes, with no trailers: `Plan 18 private sounds: trim, level, push`.

### Task 10: Whole check and tablet

- [ ] **Step 1:** Run `./gradlew testDebugUnitTest verifyRoborazziDebug lintDebug`. Expect PASS.
- [ ] **Step 2:** Run `./gradlew :app:assembleDebug` and `adb -s <tablet serial> install -r app/build/outputs/apk/debug/app-debug.apk`. Never uninstall.
- [ ] **Step 3:** Run Task 9's `push.sh`. Open Settings › Modes and Settings › Door and check the five clips are listed.
- [ ] **Step 4:** Hand over for the walk-through. Collect faults and put them to the user before fixing any. Claude enters the debug PINs (sample Alex 1234).
