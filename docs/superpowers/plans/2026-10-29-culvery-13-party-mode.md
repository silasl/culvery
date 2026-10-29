# Culvery — Plan 13: Party mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–8 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 9 is the controller. Task 10 is the controller on the `culvery-5` AVD with the user. Tasks 11–12 need the user and the son's SM-T510.

**Goal:** one tap with an Adult's or Admin's PIN sets the house up for a party (the Party plugs, a chosen Sonos favourite or playlist on the chosen speakers grouped together, chosen Hue scenes) and one tap puts it all back, leaving anything changed by hand, with Holiday and Party never on together.

**Architecture:**
- **`:core:house`** gains Party's names (`HouseModes.PARTY`, `HousePermissions.PARTY_MODE`), `HouseModeSwitch` (a mode's on/off, multibound, for the exclusion), `HouseMusic` (new seam) and Party's methods on `HouseLights`, with their data types and the shared lines.
- **`:capability:music`** gains grouping in `MusicSystem` (`group`, `regroup`), implemented by `:provider:music-sonos` over UPnP and by `TestMusicSystem`, and `MusicHouseSeam`, which implements `HouseMusic` (choose the coordinator, group, play, judge "still Party's", pause, put the groups back).
- **`:capability:lights`** gains `LightsParty` (read the chosen rooms, recall the scenes, put back rooms whose active scene is still Party's), reached through `LightsHouseSeam`. `LightsSystem` and Hue are unchanged.
- **`:capability:party`** (new, depends only on `:core:house`): the store (`party_settings`: the choices, and the record with its snapshot), `PartyHouse` (turn on, turn off, the plugs retry, restarts, the lines), `PartyActions` (PIN, exclusion), the tile, the sheet, Settings › Party mode, and the `HouseModeInfo` that gives the Plugs tab its Party column.
- **Holiday mode** binds its `HouseModeSwitch` and refuses to turn on while Party is on.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2, OkHttp (MockWebServer in the Sonos tests, as now); JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-29-culvery-13-party-mode-design.md`, all of it, decisions 1–14. Every "My call" there is decided; this plan doesn't reopen them.

**Not this plan** (spec non-goals): a timer or automatic end; a volume setting; a playlist picker at tap time; resuming what played before the party.

**Previous plans (format, shared parts):**
- `docs/superpowers/plans/2026-10-27-culvery-11b-holiday-mode.md`: `HolidayHouse`, its record and retries, the tile and sheet, `MemoryStore`.
- `docs/superpowers/plans/2026-10-27-culvery-12-smart-plugs.md`: `HousePlugs` (`begin`, `enter`, `leave`), `HouseModeInfo`, the Plugs tab's mode columns, and the house rules copied below.

**Builds on:** branch `plan-13-party-mode` in the one checkout `C:\Users\si\code\siland\culvery`, cut from `main` at 788f6f1a (the spec is committed on it at 978f904f). No worktrees. Never push.

## Global Constraints

**From the spec, verbatim:**
- §1 "Party must never leave the house half-set: if a step fails it says so on the tile, and the other steps still run."
- §1 "Party must never change a plug, light or speaker the family changed by hand during the party when it ends".
- §1 "The children must not be able to start or end it (PIN, as Holiday)."
- §2.6 "Party can't be turned on while Holiday mode is on, and the tile says why ("Turn Holiday mode off first."). Holiday can't be turned on while Party is on, with the matching line."
- §2.7 "it needs its own permission, `party.mode`, granted to Adults and Admins like `holiday.mode`."
- §2.9 "Party keeps nothing running while it's on: no retries every 5 minutes for music or lights … Plugs keep Plan 12's own retry rule unchanged."
- §2.11 "the group Party made still has the same rooms and is playing. If the song changed but the group didn't, it's still Party's and is paused. If anyone regrouped or paused it, it's left."
- §2.12 "a light room counts as unchanged when the bridge's active scene is still Party's scene. If the bridge reports no active scene for it, the room is left as it is".
- §2.13 "a saved speaker room or light room that no longer exists is skipped quietly; if no chosen speaker room is left, the music step fails with its line."
- §2.14 "Names show on the tile and are never logged."
- §4.3 "The record and snapshot are in a DataStore, so a restart mid-party still knows what to put back."
- §6 "Hue and Sonos stay read-only in tests on real devices without the user's go; a real party run is the user's."
- §6a "`:capability:party` depends only on `:core:house`". "The "still Party's" check reads the groups afresh, not from the cached `groups` flow."

**Copy** (verbatim from the spec; curly apostrophes and "›", "…" in code):
- Tile: "Party mode"; "Off" / "On".
- Sheet buttons: "Turn Party mode on" / "Turn Party mode off"; music "Play {playlist} on every speaker"; lights "{scene} in {room}".
- Nothing configured: "Party mode has nothing to do yet. Choose plugs, music and lights in Settings › Party mode."
- Lines: "The music didn’t start. Check the Sonos app." / "The music didn’t stop. Stop it in the Sonos app."; "Some lights didn’t change: {rooms}." / "Some lights didn’t go back: {rooms}."; "Turn Holiday mode off first." / "Turn Party mode off first."; plugs: Plan 12's lines.
- Plugs tab column: "Party mode".
- **This plan's copy** (spec gaps; the owning task tests each):
  - sheet lines end with a full stop, as Holiday's do: "Play Party mix on every speaker.", "Bright in Kitchen.";
  - speakers not all chosen: "Play {playlist} on {rooms}.";
  - `{rooms}`: one name; two joined by " and "; more by ", " with " and " before the last ("Kitchen, Lounge and Dining");
  - the plug lines on the on sheet, as Holiday's: "Switch {name} on." / "Switch {name} off.", then "Plugs go back as they were when you turn it off.";
  - sheet titles: on "Party mode will:", off "When Party mode ends:"; "Cancel";
  - the off sheet: "Stop the music and put the speakers back in their groups." (a party group was made), "Put the plugs back as they were." (`plugs.ready(PARTY)` is true), "Put the lights back in {rooms}." (rooms were set), then "Anything changed by hand during the party stays as it is."; with none of the three, only "Party mode changed nothing, so there’s nothing to put back.";
  - the groups not put back after the music stopped: "The speakers didn’t go back to their groups. Regroup them in the Sonos app.";
  - the tile spoken: "Party mode, off." / "Party mode, on." / "Party mode, turning on." / "Party mode, turning off."; click labels "Turn on" / "Turn off"; while a change runs the second line is "Turning on…" / "Turning off…";
  - Settings › Party mode: title "Party mode"; sections "Music", "Lights", "Plugs"; the music row "Music" with "None" or the pick's name, its sheet titled "Music for Party mode"; "Speakers" with a switch row per room (all on by default); a light row per room with "Leave alone" or the scene's name, its sheet titled with the room's name; the plugs line "Choose what each plug does in Party mode on the Plugs tab."; with no Sonos "Set up Sonos in Settings › Music to choose the music."; with no Hue "Set up Hue in Settings › Lights to choose scenes."; a read failure shows the system's own line (Music's `problemLine` / `CANT_REACH_SONOS` / `SONOS_REFUSED`, Lights' `problemLine` / `HUE_REFUSED`).

**From the house rules** (as Plan 12's Global Constraints; binding):
- **Platform:** package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only; **no new library; never change a version**; no deprecated API, and any one met is flagged to the user before use. None is needed here: grouping uses the speakers' own `SetAVTransportURI` and `BecomeCoordinatorOfStandaloneGroup`, and the UI uses the existing `Hh*` components.
- **New module:** `:capability:party`, as `capability/holiday/build.gradle.kts` (`api(project(":core:plugin"))`, `api(project(":core:house"))`, `implementation` of `:core:ui`, `:core:access`, DataStore; Roborazzi). It goes into `settings.gradle.kts` in Task 1; `app/build.gradle.kts` and the README change once, in Task 8. `:capability:music` gains `api(project(":core:house"))` in Task 3.
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash, or `gradlew.bat`), plus `./gradlew -p build-logic :convention:test` in any task that adds a log line or a module.
  - Never plain `test`, `check` or `build`, and never a Gradle `install…` task. No subagent touches a device.
  - **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`**: a long temp path breaks Robolectric's SQLite.
  - Scratch goes in `$LISTS` = `C:/t9`, a plain shell variable, never exported.
- **Tests that can fail:**
  - Every new test fails before its change; one that passes before it is labelled **pin**.
  - Nothing reaches a real address: Sonos tests use `FakeSpeaker` (MockWebServer on loopback), the rest use fakes. Timer tests run on virtual time and assert one tick before the boundary and at it.
  - A "restart" in a test is a new `PartyHouse` over the **same** `MemoryStore` instance, after the old one's job is cancelled. A file-backed store test reopens the file only after cancelling the first DataStore's scope.
  - `:capability:party` copies Holiday's `TestAccess(role)` with `PartyPermissionSource`.
- **Every UI task ends as Plan 11a's:**
  1. `compareRoborazziDebug` on the module; open each `*_compare.png`, and check each new image against the drawing named;
  2. `recordRoborazziDebug`;
  3. the baseline check (`$LISTS/expected-baselines.txt`, `$LISTS/expected-prefixes.txt`, then Plan 10's four `comm`/`grep` lines);
  4. the gate;
  5. commit.

  Every new screen is recorded `_dark` and `_light`. `CopyScanTest` passes; every screen test ends with `compose.assertClickablesHaveRoleAndLabel()`.
- **Code:**
  - Icons come from `Icons`. `celebration` is added in Task 6 (`tools/fonts/icons.txt`, the subset rebuilt with `python tools/fonts/subset.py --source "$LISTS/material-symbols-full.ttf"`, the full font from `git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf`); a missing glyph means a download, so stop and ask.
  - Layout numbers go in `PartyDimens` (the tile's copy `HolidayDimens`' values).
  - Comments say only what the code can't, in one line by default, with spec refs as `(13 §4.2)`, never a task or ruling number.
  - Each new file with a `TAG` is named in `LogHygieneTest.theScanReachesTheShippedCode`. Log lines interpolate only `e::class.simpleName` and enum names; no counts.
- **Privacy:**
  - No room id, room name, scene id or name, speaker id, favourite id or name, plug name or address goes into a log, an exception message, a `toString`, a test name or a test tag. Tags use an index.
  - Every new data class below overrides `toString` without them. The store holds ids and the names the lines and the sheet need; it is under `files/datastore/`, which backup and transfer leave alone.
  - Sample data only: speakers Kitchen, Lounge, Dining (`SonosFixtures.KITCHEN`, `LOUNGE`, `DINING`; `Kitchen-id` etc. in the capability tests); picks "Party mix" (`FV:2/3`) and "Sunday morning" (`FV:2/2`); Hue rooms Kitchen (`r1`, scenes Bright `s1`, Relax `s2`) and Lounge (`r2`, Relax `s3`); plugs Lamp and Desk. Real names stay in `$LISTS`.
- **Devices:**
  - The emulator is `culvery-5`. `emulator-5554` holds a signed release and the household: never install on it.
  - The tablet (SM-T510) gets `adb install -r` of a signed release only; never uninstall, never wipe.
  - **No real speaker, light or plug is changed, and Party mode is never turned on against real devices, except on the user's go each time** (Tasks 10–11).
- **Git:**
  - A commit is a plain sentence with no prefix and no trailer of any kind; never push.
  - **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **The party group changed during the party: regrouped, paused, or playing another song.**
   - **Expected:** regrouped or paused → nothing sent, no line; a new song in the same group → still paused and regrouped (decision 11). The judgement reads the layout and the playing groups afresh.
   - **Tests** (Task 3): `MusicHouseSeamTest.aRegroupedPartyIsLeft`, `aPausedPartyIsLeft`, `aNewSongStillCountsAsParty`, `stillPartyReadsAfresh`.
2. **Sonos half-answering: a room refusing to join, a play that fails, the speakers down at either end.**
   - **Expected:** the rooms that joined still play and the music line shows; a failed play puts the groups back at once and leaves no party group; at turn-off the stop line shows; the plugs and lights steps run regardless.
   - **Tests** (Task 2): `SonosGroupingTest.aRoomThatRefusesIsLeftOutNotThrown`; (Task 3) `MusicHouseSeamTest.aRoomThatWontJoinStillPlaysTheRestAndSaysSo`, `aPlayThatFailsPutsTheGroupsBack`, `stopWithTheSpeakersDownSaysSo`; (Task 5) `PartyHouseTest.aFailingStepDoesntStopTheOthers`.
3. **The tablet restarts mid-party or mid-turn-on, with the snapshot partly saved.**
   - **Expected:** turning off puts back what the record holds and nothing else; a music snapshot with no party group leaves the music; the plugs retry resumes with no grant; a turn-on whose record never landed has its plugs put back when Party loads as off.
   - **Tests** (Task 5): `PartyHouseTest.aRestartMidPartyStillPutsBack`, `aRestartBeforeTheGroupWasMadeLeavesTheMusic`, `aPlugsRetryAfterARestartTakesNoGrant`, `anOrphanRunIsPutBackWhenPartyLoadsAsOff`, `eachSnapshotIsSavedBeforeItsChange`.
4. **What Settings saved is gone by the tap: a speaker room, a Hue room or scene deleted, a favourite removed, or the bridge reporting no active scene at the end.**
   - **Expected:** gone rooms and scenes are skipped with no line; no chosen speaker room left, or the pick gone, fails the music with its line; a room with no active scene is left.
   - **Tests** (Task 3): `MusicHouseSeamTest.noChosenRoomLeftFailsTheMusic`, `aPickNoLongerThereFails`; (Task 4) `LightsPartyTest.aGoneRoomOrSceneIsSkipped`, `noActiveSceneIsLeft`, `aRememberedSceneSinceDeletedFallsBackToBrightness`.
5. **Taps that cross: Holiday tapped while Party is on (and the reverse), the other mode turned on while a sheet is open, a tap while a change runs.**
   - **Expected:** no PIN pad; the tile's line says which mode to turn off, and goes when that mode is off; a confirm finds the other mode on and turns nothing on; a tap while running does nothing.
   - **Tests** (Task 6): `PartyActionsTest.holidayOnRefusesBeforeThePin`, `theConfirmChecksAgain`, `aTapWhileRunningDoesNothing`; `HolidayActionsTest.partyOnRefusesBeforeThePin`, `theConfirmChecksAgain`.

---

| # | Task | Review |
|---|---|---|
| 1 | `:core:house` Party seams and types; `:capability:party` 1: the store, the permission, the words | sonnet |
| 2 | `MusicSystem` grouping: the contract, Sonos over UPnP, `TestMusicSystem` | **opus** (grouping, put-back, partial failure) |
| 3 | `MusicHouseSeam`: Party's music and "still Party's" | **opus** (hand-change rule, put-back) |
| 4 | `LightsParty`: Party's scenes and put-back through `HouseLights` | **opus** (hand-change rule, put-back) |
| 5 | `PartyHouse`: turn on, turn off, the plugs retry, restarts, the lines; the mode's registration | **opus** (put-back, restarts) |
| 6 | `PartyActions`, the exclusion (Holiday too), the capability, the tile and the sheet | sonnet |
| 7 | Settings › Party mode; the Plugs tab's Party column | sonnet |
| 8 | The app wired in; the debug sample speakers and lights; the README | sonnet |
| 9 | Simplify the whole branch | controller |
| 10 | The emulator walk-through on `culvery-5` | controller + user |
| 11 | Fixtures from captures; on the tablet | controller + user, **needs the SM-T510** |
| 12 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:** the seams, types and store first (1), so the music (2, 3) and lights (4) sides each implement a fixed seam and `PartyHouse` (5) meets tested seams and store; the actions and screens (6, 7) once the house is real; the wiring (8) last, because the graph test checks every binding at once.

## Rulings against the code

The spec leaves these to the builder, or they fall out of the code. Each is this plan's call.

1. **Seam types** (`:core:house`, `HouseSeams.kt`), each with a `toString` that names no id or name:
   - `HouseModes.PARTY = "party"`; `HousePermissions.PARTY_MODE = "party.mode"`.
   - `interface HouseModeSwitch { val modeId: String; val modeName: String; val isOn: StateFlow<Boolean?> }`, multibound; `fun Set<HouseModeSwitch>.blockedBy(modeId: String): HouseModeSwitch?` is the first other mode whose `isOn.value == true`. `HouseMode` (Holiday's, for the door and the screen) is unchanged.
   - `data class HouseRoom(val id: String, val name: String)`; `data class MusicPick(val id: String, val name: String)`; `data class MusicOptions(val picks: List<MusicPick>, val rooms: List<HouseRoom>, val line: String?)`.
   - `data class GroupBefore(val coordinatorId: String, val roomIds: List<String>)`; `data class PartyGroup(val coordinatorId: String, val roomIds: Set<String>)`; `data class MusicStart(val made: PartyGroup?, val step: HouseStep)`.
   - ```kotlin
     /** What Party reaches in the speakers (13 §4.1, §4.2); absent with no music capability. */
     interface HouseMusic {
         /** True while a music system is open. */
         val ready: Flow<Boolean>
         /** Favourites then playlists, and the rooms, read afresh; [MusicOptions.line] says why they couldn't be. */
         suspend fun options(): MusicOptions
         /** Every group as it is now, read afresh; throws when the speakers can't be read. */
         suspend fun before(): List<GroupBefore>
         /** Groups [roomIds] (null: every room) and plays [pickId]; a failed play puts [before] back. Never throws. */
         suspend fun start(pickId: String, roomIds: Set<String>?, before: List<GroupBefore>): MusicStart
         /** Pauses [made] and puts [before] back when [made] is still Party's (13 §2.11), else sends nothing. Never throws. */
         suspend fun stop(made: PartyGroup, before: List<GroupBefore>): HouseStep
     }
     ```
   - `data class HouseScene(val id: String, val name: String)`; `data class LightRoomOption(val id: String, val name: String, val scenes: List<HouseScene>)`; `data class LightOptions(val rooms: List<LightRoomOption>, val line: String?)`.
   - `data class SceneChoice(val roomId: String, val roomName: String, val sceneId: String, val sceneName: String)`; `data class RoomBefore(val roomId: String, val name: String, val sceneId: String, val on: Boolean, val brightness: Int, val activeSceneId: String?)` (`sceneId` is Party's).
   - `HouseLines` gains `MUSIC_DIDNT_START`, `MUSIC_DIDNT_STOP`, `SPEAKERS_NOT_BACK`, and, moved from `HolidayWords` unchanged, `PLUGS_THREW`, `PLUGS_MISSED`, `PLUGS_LEAVE_THREW`, `PLUGS_BACK`; and `fun roomList(names: List<String>): String`, `fun lightsDidntChange(names: List<String>): String`, `fun lightsDidntGoBack(names: List<String>): String`, `fun turnOffFirst(modeName: String): String`.
   - `HouseModule` gains `@BindsOptionalOf HouseMusic` and `@Multibinds Set<HouseModeSwitch>`.
2. **`HouseLights` gains Party's four** (Task 4, so `LightsHouseSeam` and Holiday's `FakeLights` change with it):
   ```kotlin
   suspend fun partyOptions(): LightOptions
   /** The chosen rooms read afresh; a room or scene the bridge no longer has is left out (13 §2.13). Throws when the bridge can't be read. */
   suspend fun partyBefore(choices: List<SceneChoice>): List<RoomBefore>
   /** Recalls each room's scene; the names of the rooms that didn't change. Never throws. */
   suspend fun partyOn(rooms: List<RoomBefore>): List<String>
   /** Puts back each room whose active scene, read afresh, is still Party's (13 §2.12); the names not put back. Never throws. */
   suspend fun partyBack(rooms: List<RoomBefore>): List<String>
   ```
   No grant: scenes are open to everyone on the Lights tab. `LightsParty` doesn't take `LightsHouse`'s run lock; Holiday lights can't run during a party, and an All lights off meeting a turn-on is the family's own doing.
3. **Grouping in `MusicSystem`** (`:capability:music`):
   - `data class RoomGroup(val coordinatorId: String, val roomIds: List<String>)`.
   - `suspend fun group(coordinatorId: String, roomIds: Set<String>): SpeakerGroup` reads the layout afresh, then:
     1. the coordinator, when it is a member of another group, stands alone first;
     2. each member of its group not in `roomIds` stands alone;
     3. each room in `roomIds` not yet in its group joins it;
     4. the layout is read afresh, and the group the coordinator leads is returned.

     A room that refuses or doesn't answer is left out, not thrown (the caller compares); the coordinator not answering throws as other commands do.
   - `suspend fun regroup(groups: List<RoomGroup>)` reads the layout afresh, skips a group already as remembered and any room no longer in the household, then: each remembered coordinator not leading a group stands alone; each remembered member not under its coordinator joins it. Every room is tried; any that refused throws `MusicRefusedException` at the end. The layout is read afresh after.
   - **Sonos:** `SonosClient.join(address, coordinatorUuid)` = `SetAVTransportURI` with `CurrentURI` `x-rincon:{coordinatorUuid}` and empty metadata; `SonosClient.standAlone(address)` = `BecomeCoordinatorOfStandaloneGroup` (AVTransport, `InstanceID` 0). Both are commands, so `SonosClient.send` never retries them. Both go to the room's own address, through `toRooms`.
4. **The coordinator and "still Party's"** (pure, `capability/music/.../PartyMusic.kt`):
   - `partyCoordinator(groups, chosen)`: among groups whose coordinator is chosen, the one holding the most chosen rooms (ties: layout order); else the first chosen room in layout order; null when no chosen room is in the layout. This moves the fewest rooms.
   - `stillParty(playing, made)`: a group in `playing` whose `coordinatorId == made.coordinatorId` and whose room ids equal `made.roomIds`. `playing` comes from `checkLayout()` then `playingNow()`, never `groups.value`.
5. **`MusicHouseSeam.start`**: no system → `MusicStart(null, Failed(MUSIC_DIDNT_START))`. Else `checkLayout()`; chosen = `roomIds ∩ layout rooms` (or all); none → failed, nothing sent; the pick looked up by id in `favourites()` then `playlists()`, none → failed; `group(partyCoordinator(...), chosen)`; `playFavourite`. A throwing play → `regroup(before)` quietly, `made = null`, failed. A group missing chosen rooms → `made` is the group as returned, `step` failed. Otherwise `Done`.
6. **`MusicHouseSeam.stop`**: no system or a read throwing → `Failed(MUSIC_DIDNT_STOP)`. Not `stillParty` → `Done`, nothing sent. `pause` throwing → `Failed(MUSIC_DIDNT_STOP)`, no regroup. `regroup` throwing → `Failed(SPEAKERS_NOT_BACK)`. Otherwise `Done`. Music isn't resumed (decision 10).
7. **`LightsParty`** (`capability/lights/.../LightsParty.kt`, system from `Bridges`):
   - `partyBefore`: `readAll()`, then a `RoomBefore` per choice whose room exists and has that scene, with the room's name, on, brightness and `activeSceneId` as read.
   - `partyOn`: `recallScene` per room, each caught; the names of those that threw. No system → every name.
   - `partyBack`: `readAll()` (throwing → every name), then per room: gone → skipped; `activeSceneId != sceneId` (null included) → left, not named; else put back: the remembered `activeSceneId` when the room still has it → `recallScene`; else off → `setOn(false)`; else `setBrightness(max(brightness, 1))`. A throw names the room.
   - `partyOptions`: `readAll()`, rooms with their scenes; a failure → empty with `problemLine(e.problem)` or `HUE_REFUSED`.
8. **The store** (`party_settings`, `PartySettings`), one DataStore Preferences file. Multi-field values join their fields with U+001F, which no name holds; a malformed value is skipped on read.
   - Choices: `music.pick` (id), `music.pick_name`, `music.rooms` (a string set of `id␟name`; absent means every speaker), `scenes` (a string set of `roomId␟roomName␟sceneId␟sceneName`).
   - Record: `on`, `since` (wall millis), `music.before` (a set of `coordinatorId␟id,id,…`), `music.made` (`coordinatorId␟id,id,…`), `lights.before` (a set of `roomId␟name␟sceneId␟on␟brightness␟activeSceneId-or-empty`).
   - `update` transforms the choices in one `edit`, leaving the record's keys; `save(record)` replaces only the record's keys; `clearRecord()` removes only them.
9. **`PartyHouse`** (as `HolidayHouse`: a `Mutex`, `exclusive` with `PartyRun`, memory as the truth with `unsaved` retried at each tick):
   - **`turnOn(by)`**: requires `PARTY_MODE` in `by.granted`. Already on → nothing. Then, each step attempt-wrapped and independent (13 §4.1):
     1. save `PartyRecord(on = true, since = now)`;
     2. plugs, when `plugs?.ready(PARTY)`: `begin(PARTY, by)`, then `enter(PARTY)`;
     3. music, when a pick is saved and `music?.ready`: `before()` → save it in the record → `start(pick, rooms, before)` → save `made`;
     4. lights, when scenes are saved and `lights?.ready`: `partyBefore(scenes)` → save it → `partyOn(rooms)`. `partyBefore` throwing names every chosen room.
   - **Lines while on**, one per kind, music then plugs then lights, joined by `\n`: the music step's line; the plugs step's `Failed` line, or `PLUGS_THREW` when `enter` threw, or `PLUGS_MISSED` when it returned null; `lightsDidntChange(names)`.
   - **The plugs retry** (Plan 12's rule, kept): while on and the plugs step isn't `Done`, `tick()` calls `enter(PARTY)` once `RETRY_MS` (300 000) has passed since the last try, with no grant. `null` stops the retries. Music and lights are never retried (decision 9). After a restart with the record on and plugs bound, the step counts as pending, so the first tick calls `enter` (which answers `Done` at once when nothing is pending).
   - **`turnOff(by)`**: requires `PARTY_MODE`. Not on → nothing. In order (13 §4.2): `made?.let { music.stop(it, musicBefore) }` (music absent with a `made` → `MUSIC_DIDNT_STOP`); `lights?.partyBack(lights)` when the record holds rooms (lights absent → every name); `plugs?.leave(PARTY)` whenever plugs are bound (threw → `PLUGS_LEAVE_THREW`). Then clear the record. The off line is music, plugs, lights, shown until the next turn-on or `OFF_LINE_MS` (600 000).
   - **The first `load()` that finds the record off** calls `plugs.leave(PARTY)` once, attempt-wrapped, no line (12 §5.2).
   - **`plan()`**: `PartyPlan(plugs = preview(PARTY) when ready, music = the saved choice when music is ready, scenes = the saved scenes when lights are ready)`; `nothing` when all three are empty (decision 8).
10. **The exclusion** (decision 6): `PartyActions` and `HolidayActions` take `Set<HouseModeSwitch>`. A tap while off with `blockedBy(own id)` non-null asks no PIN and sets the refusal to that mode; the refusal shows as the tile's line, `turnOffFirst(modeName)`, while that mode stays on, and clears at the next tap. The sheet's confirm checks again and turns nothing on when blocked. The houses don't check: `HolidayHouse` and `PartyHouse` are themselves in the set, so taking it there would make a cycle.
11. **The tile and the sheet:** Party's tile is the One-tap tray's `TrayButton("party.mode", 20)`, beside Holiday's 10. A tap asks the PIN (on and off alike), then opens the sheet: on, `willLines(house.plan())` and "Turn Party mode on"; off, `house.backLines()` and "Turn Party mode off". The turn-off runs under `NonCancellable`, as Holiday's.
12. **Settings › Party mode** (`PartyPage`, id `"party"`, order 475, between Lights 470 and Other integrations 480): the page is always listed. A pick or room saved but no longer offered still shows its saved name. Picking a scene saves the names shown; switching every speaker back on saves "every speaker" (no `music.rooms`).
13. **Debug samples** (Task 8, for Task 10): `SampleMusicProvider` (`sample.music`) and `SampleLightsProvider` (`sample.lights`) in `app/src/debug`, in-memory systems with the sample rooms, picks and scenes. `DebugSampleHouseReceiver`:
    - `--ez sample true`: keeps the saved music and lights connections in `debug_sample_house`, then connects the samples;
    - `--ez sample false`: reconnects the kept ones;
    - `--es hand regroup|pause|scene`: makes a hand change on the samples (moves Dining out of the party group / pauses it / clears Kitchen's active scene);
    - `--ez down true|false`: every sample read and command throws the unreachable exception (`MusicUnreachableException(UNREACHABLE)`, `LightsUnreachableException(UNREACHABLE)`) until set false.

    The samples' `find()` and `check()` answer null.

## Units

| Where | dp / sp |
|---|---|
| Tile | `HolidayTile`'s shape and sizes, from `PartyDimens` (copied from `HolidayDimens`); icon `celebration` |
| Sheet | `HolidaySheet`'s layout: `HhSheet`, the title, the lines as Holiday's list, `HhButton` primary and Cancel |
| Settings › Party mode | `HhPageTitle`, `HhSectionLabel` per section, `HhListRow` for Music and each light room (the value on the right), `HhSwitchRow` per speaker room, `HhRadioRow` lists in `HhSheet`s, the plugs line in `HhType.body` `mute`; gaps as `MusicDimens.settingsGap` |

## File Structure

```
core/house: HouseSeams.kt, di/HouseModule.kt                                                         (Tasks 1, 4)
capability/party/ (new): build.gradle.kts; PartySettings.kt, PartyPermissions.kt, PartyWords.kt     (Task 1)
  PartyHouse.kt, PartyScheduler.kt, di/PartyModule.kt                                               (Task 5)
  PartyActions.kt, PartyCapability.kt, ui/PartyTile.kt, ui/PartySheet.kt, ui/PartyDimens.kt;
    core/ui Icons.kt, tools/fonts/icons.txt, the font (celebration)                                 (Task 6)
  ui/PartySettingsPage.kt                                                                           (Task 7)
capability/music: MusicContract.kt (RoomGroup, group, regroup); test TestMusic.kt                   (Task 2)
  build.gradle.kts, PartyMusic.kt, MusicHouseSeam.kt, di/MusicModule.kt                             (Task 3)
provider/music-sonos: SonosClient.kt, SonosSystem.kt; test SonosFixtures.kt, SonosClientTest.kt,
  SonosGroupingTest.kt                                                                              (Task 2)
capability/lights: LightsParty.kt, LightsHouseSeam.kt; capability/holiday test TestHoliday.kt       (Task 4)
capability/holiday: HolidayWords.kt (Task 1); HolidayHouse.kt, HolidayActions.kt, ui/HolidayTile.kt,
  ui/HolidaySheet.kt, di/HolidayModule.kt                                                           (Task 6)
capability/plugs test: ui/PlugsScreenshotTest.kt, ui/PlugsTabTest.kt                                (Task 7)
app: build.gradle.kts, src/debug di/HouseGraphProbe.kt, src/debug sample/SampleHouse.kt,
  src/debug di/SampleHouseModule.kt, src/debug DebugSampleHouseReceiver.kt, src/debug AndroidManifest.xml;
  test HouseGraphTest, NoBackupTest, SampleHouseTest; README.md                                     (Task 8)
settings.gradle.kts (Task 1); build-logic tests (Tasks 1–6, 8)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                                (Tasks 10–12)
```

---

### Task 1: Party's seams and types in `:core:house`; `:capability:party` 1: the store, the permission, the words (spec §2.6–§2.8, §4.3, §5, §6a; rulings 1, 8)

**Review:** sonnet.

**Files:**
- Modify `core/house/src/main/java/uk/co/siland/culvery/core/house/HouseSeams.kt`, `core/house/.../di/HouseModule.kt`.
- Modify `capability/holiday/.../HolidayWords.kt`: `PLUGS_LEAVE_THREW`, `PLUGS_MISSED`, `PLUGS_THREW` and `PLUGS_BACK` now read `HouseLines`' (the text is unchanged).
- Create `capability/party/build.gradle.kts`, register `:capability:party` in `settings.gradle.kts`, and in `capability/party/src/main/java/uk/co/siland/culvery/capability/party/`: `PartySettings.kt`, `PartyPermissions.kt`, `PartyWords.kt`; `di/PartyModule.kt` with `@IntoSet PermissionSource` only.
- Modify `build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt`, `LogHygieneTest.kt` (expects `PartySettings.kt`).
- Test: `core/house` `HouseSeamsTest`; `capability/party/src/test/.../PartySettingsTest.kt`, `PartyPermissionsTest.kt`, `PartyWordsTest.kt`; helpers `TestParty.kt` (`MemoryStore` copied from Holiday's with `failOnWrite: Int?`, `TestAccess` with `PartyPermissionSource`, the sample choices). Holiday's existing tests are the pins for the moved lines.

**Interfaces:**
- Produces: ruling 1's types, constants and functions.
- Produces in `:capability:party`:
  - `data class MusicChoice(val pickId: String, val pickName: String, val rooms: List<HouseRoom>?)` (null: every speaker).
  - `data class PartyPrefs(val music: MusicChoice? = null, val scenes: List<SceneChoice> = emptyList())`, with `fun withScene(choice: SceneChoice): PartyPrefs` (replacing that room's), `fun withoutScene(roomId: String): PartyPrefs`, `fun withMusic(choice: MusicChoice?): PartyPrefs`.
  - `data class PartyRecord(val on: Boolean = false, val since: Long? = null, val musicBefore: List<GroupBefore>? = null, val made: PartyGroup? = null, val lights: List<RoomBefore>? = null)`.
  - `@Singleton class PartySettings(store: DataStore<Preferences>, scope: CoroutineScope)`, `@Inject constructor(@ApplicationContext context, @ApplicationScope scope)` over `party_settings`: `val prefs: Flow<PartyPrefs>`; `val record: StateFlow<PartyRecord?>` (null until read); `suspend fun update(change: (PartyPrefs) -> PartyPrefs)`; `suspend fun save(record: PartyRecord)`; `suspend fun clearRecord()`. Writes throw `IOException`.
  - `class PartyPermissionSource @Inject constructor() : PermissionSource`: `party.mode`, "Turn Party mode on and off", Admin and Adult, `freshPin = true`.
  - `object PartyWords`: the Copy's Party strings, plus `fun willLines(plan: PartyPlan): List<String>`, `fun backLines(record: PartyRecord, plugsListed: Boolean): List<String>`, `fun tileSpoken(on: Boolean): String`, `fun runningSpoken(run: PartyRun): String`, `fun musicLine(choice: MusicChoice): String`. `PartyPlan` and `PartyRun` are declared here so the words compile: `data class PartyPlan(val plugs: List<PlugChange>, val music: MusicChoice?, val scenes: List<SceneChoice>) { val nothing: Boolean }`, `enum class PartyRun { TURNING_ON, TURNING_OFF }`.

- [ ] **Step 1: The failing tests.**
  - `HouseSeamsTest`:
    - `theNewTypesHideTheirIdsAndNames`: `toString` of `HouseRoom`, `MusicPick`, `GroupBefore`, `PartyGroup`, `HouseScene`, `LightRoomOption`, `SceneChoice`, `RoomBefore` holds none of "Kitchen", "Kitchen-id", "Party mix", "FV:2/3", "r1", "s1", "Bright".
    - `roomListJoinsAsTheCopySays`: `["Kitchen"]` → "Kitchen"; two → "Kitchen and Lounge"; three → "Kitchen, Lounge and Dining".
    - `theLightsLinesNameTheRooms`: `lightsDidntChange(["Kitchen", "Lounge"])` = "Some lights didn’t change: Kitchen and Lounge."; `lightsDidntGoBack(["Kitchen"])` = "Some lights didn’t go back: Kitchen.".
    - `turnOffFirstNamesTheMode`: "Turn Holiday mode off first." and "Turn Party mode off first.".
    - `theMusicLinesAreTheSpecs`: the three music constants verbatim.
    - `blockedByFindsAnotherModeOn`: holiday on, party off → `blockedBy("party")` is holiday; `blockedBy("holiday")` null; holiday `null` (unread) → null.
  - `PartySettingsTest`:
    - `everythingSurvivesARestart` (file-backed): choices `MusicChoice("FV:2/3", "Party mix", [Kitchen, Lounge])`, scenes `[SceneChoice(r1, "Kitchen", s1, "Bright"), SceneChoice(r2, "Lounge", s3, "Relax")]`; record on, since 1 000, `musicBefore [GroupBefore(Kitchen-id, [Kitchen-id]), GroupBefore(Lounge-id, [Lounge-id, Dining-id])]`, `made PartyGroup(Lounge-id, {all three})`, `lights [RoomBefore(r1, "Kitchen", s1, true, 40, s2), RoomBefore(r2, "Lounge", s3, false, 0, null)]` → the reopened store reads the same, member order kept.
    - `everySpeakerIsNoRoomList`: `rooms = null` → no `music.rooms` key; reads back null.
    - `namesWithSeparatorsReadBack`: a room named "Kitchen = Diner; 2, upstairs" survives in choices and the record.
    - `clearingTheRecordKeepsTheChoices`; `updatingTheChoicesKeepsTheRecord`.
    - `withSceneReplacesThatRoomsChoice`; `withoutSceneRemovesIt`.
    - `malformedValuesAreSkipped`: a `scenes` entry with three fields, `lights.before` with "maybe" for on, `music.made` with no separator → skipped, no throw.
    - `toStringHasNoIdsOrNames`: `PartyPrefs`, `MusicChoice`, `PartyRecord`.
  - `PartyPermissionsTest`: `party.mode` with its label verbatim, Admin and Adult, `freshPin`; a `PermissionRegistry` doesn't grant it to `Role.CHILD`.
  - `PartyWordsTest`:
    - `willLinesInOrder`: plan with Lamp off and Desk on, `MusicChoice("FV:2/3", "Party mix", null)`, Bright in Kitchen → "Switch Lamp off.", "Switch Desk on.", "Plugs go back as they were when you turn it off.", "Play Party mix on every speaker.", "Bright in Kitchen.".
    - `someSpeakersAreNamed`: rooms Kitchen and Lounge → "Play Party mix on Kitchen and Lounge.".
    - `nothingToDo`: an empty plan → only the decision 8 line.
    - `backLines`: a record with `made` and lights Kitchen and Lounge, `plugsListed = true` → "Stop the music and put the speakers back in their groups.", "Put the plugs back as they were.", "Put the lights back in Kitchen and Lounge.", "Anything changed by hand during the party stays as it is."; an empty record with `plugsListed = false` → "Party mode changed nothing, so there’s nothing to put back." alone.
    - `spoken`: "Party mode, off.", "Party mode, on.", "Party mode, turning on.", "Party mode, turning off.".
  - `ModuleBoundariesTest.thePartyModuleKeepsToItsFamily` (**pin**): allowed `:capability:party` → `:core:house` (`api`); banned `:capability:party` → `:capability:music`, `:capability:lights`, `:capability:plugs`, `:capability:holiday`; banned `:capability:holiday` → `:capability:party`.

  Run `./gradlew :core:house:testDebugUnitTest :capability:party:testDebugUnitTest :capability:holiday:testDebugUnitTest` and `./gradlew -p build-logic :convention:test`. Expected: FAIL (the pins aside).
- [ ] **Step 2: Implement** per the Interfaces and rulings 1 and 8.
- [ ] **Step 3: The gate and the convention tests.** No screenshot changes.
- [ ] **Step 4: Commit.**
```bash
git add core/house capability/party capability/holiday settings.gradle.kts build-logic
git commit -m "Add Party mode's seams to the house, its store of choices and the record it puts back from, its permission and its words"
```

---

### Task 2: `MusicSystem` grouping: the contract, Sonos over UPnP, `TestMusicSystem` (spec §4.1.3, §4.2.1, §6a, §7; rulings 3; Review Focus 2)

**Review:** opus. Which room stands alone when, a refused join, the put-back, nothing retried that could double.

**Files:**
- Modify `capability/music/.../MusicContract.kt`: `RoomGroup`, `group`, `regroup`.
- Modify `provider/music-sonos/.../SonosClient.kt` (`join`, `standAlone`), `SonosSystem.kt` (`group`, `regroup`). No log line: `SonosSystem` logs nothing today, and `MusicHouseSeam` logs the outcome.
- Modify `capability/music/src/test/.../TestMusic.kt`: `TestMusicSystem.group` records `"group {coordinatorId} {roomIds sorted}"` and rearranges `groups` (the coordinator leads exactly those rooms; rooms taken out stand alone, `STOPPED`); `TestMusicSystem.regroup` records `"regroup {coordinatorId}:{ids},…"` and rearranges; both honour `failures`, `failOnce`, `gates`; `groupLeavesOut: Set<String>` drops rooms from the returned group, as a refused join would.
- Modify `provider/music-sonos/src/test/.../SonosFixtures.kt`: `standAlone()` = `soap("BecomeCoordinatorOfStandaloneGroup", "DelegatedGroupCoordinatorID" to "", "NewGroupID" to "")`, `transportUriSet()` = `soap("SetAVTransportURI")` (shapes from Sonos's UPnP docs; Task 11 replaces them from a capture if the user allows a real run).
- Test: `SonosClientTest`, new `SonosGroupingTest.kt` (three `FakeSpeaker`s: Kitchen, Lounge, Dining at their own ports; the topology from Kitchen's, set afresh by each test for the read after).

**Interfaces:**
- Consumes: `SonosClient`, `FakeSpeaker`, `SonosFixtures.topology/grouped/apart`.
- Produces: ruling 3's `RoomGroup`, `MusicSystem.group(coordinatorId: String, roomIds: Set<String>): SpeakerGroup`, `MusicSystem.regroup(groups: List<RoomGroup>)`; `internal suspend fun SonosClient.join(address: String, coordinatorUuid: String)`, `internal suspend fun SonosClient.standAlone(address: String)`.

- [ ] **Step 1: The failing tests.**
  - `SonosClientTest`:
    - `joinSendsTheCoordinatorsRinconUri`: to the AVTransport path, SOAPACTION `…AVTransport:1#SetAVTransportURI`, body `<InstanceID>0</InstanceID><CurrentURI>x-rincon:RINCON_000E58AAAAAA01400</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>`.
    - `standAloneSendsBecomeCoordinatorOfStandaloneGroup`: AVTransport, `<InstanceID>0</InstanceID>`, no other argument.
    - `joinAndStandAloneAreNotSentTwice`: `dropOnce` on each → one request each, and an `IOException`.
  - `SonosGroupingTest`:
    - `groupJoinsEachChosenRoomToTheCoordinator`: apart; `group(KITCHEN, {KITCHEN, LOUNGE, DINING})` → Lounge's and Dining's speakers each get one join naming Kitchen; Kitchen gets none; a `GetZoneGroupState` follows the last join; the returned group's `coordinatorId == KITCHEN` with all three rooms.
    - `aRoomAlreadyInTheGroupIsNotSentAJoin`: Kitchen and Lounge grouped, Dining apart → only Dining joins.
    - `aRoomNotChosenLeavesTheCoordinatorsGroup`: grouped (all under Kitchen), chosen `{KITCHEN, LOUNGE}` → Dining gets `BecomeCoordinatorOfStandaloneGroup`; no joins.
    - `aCoordinatorInAnotherGroupStandsAloneFirst`: all under Kitchen; `group(LOUNGE, {LOUNGE, DINING})` → Lounge's stand-alone is received before Dining's join naming Lounge; Kitchen gets nothing.
    - `aRoomThatRefusesIsLeftOutNotThrown` (Review Focus 2): Dining answers fault 701 to the join → no throw; the returned group (from the topology read after) lacks Dining.
    - `theCoordinatorUnreachableThrows`: Kitchen silent → `MusicUnreachableException`.
    - `regroupPutsEachRoomBackUnderItsCoordinator`: all under Kitchen; `regroup([RoomGroup(KITCHEN, [KITCHEN]), RoomGroup(LOUNGE, [LOUNGE, DINING])])` → Lounge stands alone, then Dining joins Lounge; Kitchen gets nothing.
    - `aGroupAlreadyAsRememberedIsLeft`: the layout already as remembered → no command sent.
    - `aRoomGoneFromTheHouseholdIsSkipped`: a remembered `RINCON_000E58EEEEEE01400` not in the layout → no request for it, no throw.
    - `aRefusedRoomInRegroupThrowsAfterTryingTheRest`: Lounge refuses its stand-alone → Dining's join is still sent; then `MusicRefusedException`.
    - `anotherHouseholdIsNeverGrouped`: `OTHER_HOUSEHOLD` → `MusicUnreachableException`, nothing sent (both go through `toRooms`).
  - `TestMusicSystem` gets no test of its own; Task 3's tests drive it.

  Run `./gradlew :provider:music-sonos:testDebugUnitTest :capability:music:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 3. The per-room steps run one after another, not in parallel: a join sent while its coordinator is still standing alone could be refused.
- [ ] **Step 3: The gate and the convention tests**, then commit.
```bash
git add capability/music provider/music-sonos build-logic
git commit -m "Let the speakers be grouped under one room and put back into the groups they were in, over the speakers' own UPnP"
```

---

### Task 3: `MusicHouseSeam`: Party's music and "still Party's" (spec §2.4, §2.10, §2.11, §2.13, §4.1.3, §4.2.1; rulings 1, 4–6; Review Focus 1, 2, 4)

**Review:** opus. The hand-change rule, the put-back after a failed play, partial joins.

**Files:**
- Modify `capability/music/build.gradle.kts`: `api(project(":core:house"))`, with the house comment "HouseMusic appears in MusicHouseSeam's public API."
- Create `capability/music/.../PartyMusic.kt` (`partyCoordinator`, `stillParty`), `MusicHouseSeam.kt`; modify `di/MusicModule.kt` (`@Binds HouseMusic`); `LogHygieneTest` expects `MusicHouseSeam.kt`.
- Modify `ModuleBoundariesTest.theMusicModulesFollowTheRules`: allowed `:capability:music` → `:core:house` (`api`).
- Test: `PartyMusicTest.kt`, `MusicHouseSeamTest.kt` (`TestMusicSystem`; groups built with `TestMusic.kt`'s group helper; `before` = the seam's own `before()`).

**Interfaces:**
- Consumes: Task 1's `HouseMusic` and types; Task 2's `group`, `regroup`, `RoomGroup`; `Speakers`, `problemLine`, `CANT_REACH_SONOS`, `SONOS_REFUSED`.
- Produces:
  - `internal fun partyCoordinator(groups: List<SpeakerGroup>, chosen: Set<String>): String?`; `internal fun stillParty(playing: List<SpeakerGroup>, made: PartyGroup): Boolean`.
  - `class MusicHouseSeam internal constructor(system: () -> MusicSystem?, ready: Flow<Boolean>) : HouseMusic`, `@Inject constructor(speakers: Speakers)` (`ready` = `speakers.system.map { it != null }`). Log lines are "Party music: $step failed (Type)", `step` from a private enum `BEFORE, GROUP, PLAY, PAUSE, REGROUP, OPTIONS`; a left group logs "Party music: left as changed by hand".

- [ ] **Step 1: The failing tests.**
  - `PartyMusicTest`:
    - `theCoordinatorLeadsTheMostChosenRooms`: groups [Kitchen], [Lounge + Dining led by Lounge]; chosen all → `Lounge-id`; chosen `{Kitchen-id}` → `Kitchen-id`; chosen `{Dining-id}` (a member) → `Dining-id`; chosen `{Kitchen-id, Lounge-id}` (a tie of one each) → `Kitchen-id`; chosen `{Gone-id}` → null.
    - `stillPartyNeedsTheSameRoomsAndLeader`: same leader and rooms → true; a room more or less → false; another leader → false; absent from `playing` → false.
  - `MusicHouseSeamTest`:
    - `beforeReadsTheLayoutAfresh`: `calls` starts with `checkLayout`; the answer is each group's leader and rooms in order.
    - `startGroupsTheChosenRoomsAndPlays`: apart Kitchen, Lounge, Dining; `start("FV:2/3", null, before)` → `calls` holds `checkLayout`, `favourites`, `playlists`, `group Kitchen-id [Dining-id, Kitchen-id, Lounge-id]`, `playFavourite {party group id} FV:2/3` in that order; `MusicStart(PartyGroup(Kitchen-id, all three), Done)`.
    - `aPlaylistIsFoundToo`: the pick only among `playlistList` → played.
    - `onlyChosenRoomsJoin`: `roomIds {Kitchen-id, Lounge-id, Gone-id}` → `group Kitchen-id [Kitchen-id, Lounge-id]`.
    - `noChosenRoomLeftFailsTheMusic` (Review Focus 4): `{Gone-id}` → `MusicStart(null, Failed("The music didn’t start. Check the Sonos app."))`; no `group` or `playFavourite` call.
    - `aPickNoLongerThereFails` (Review Focus 4): no `group` call.
    - `aRoomThatWontJoinStillPlaysTheRestAndSaysSo` (Review Focus 2): `groupLeavesOut {Dining-id}` → `playFavourite` called; `made.roomIds == {Kitchen-id, Lounge-id}`; step `Failed` with the start line.
    - `aPlayThatFailsPutsTheGroupsBack` (Review Focus 2): `playFavourite` throws `MusicRefusedException` → a `regroup` call naming `before`'s groups; `made == null`; the start line.
    - `theSpeakersDownFailTheStartWithoutThrowing`: `checkLayout` throws `MusicUnreachableException` → the start line.
    - `stopPausesAndPutsTheGroupsBack`: `playingNowAnswer` = the party group playing → `checkLayout`, `playingNow`, `pause {id}`, `regroup …` in order; `Done`.
    - `aNewSongStillCountsAsParty` (Review Focus 1): the party group playing another track → paused and regrouped.
    - `aRegroupedPartyIsLeft` (Review Focus 1): the leader's group now lacks Dining → no `pause`, no `regroup`; `Done`.
    - `aPausedPartyIsLeft` (Review Focus 1): not in `playingNowAnswer` → nothing sent; `Done`.
    - `stillPartyReadsAfresh` (Review Focus 1): `groups` shows the party group playing but `playingNowAnswer` is empty → left; and `checkLayout` was called before `playingNow`.
    - `stopWithTheSpeakersDownSaysSo` (Review Focus 2): `playingNow` throws → `Failed("The music didn’t stop. Stop it in the Sonos app.")`.
    - `aPauseThatFailsLeavesTheGroups`: the stop line; no `regroup`.
    - `aRegroupThatFailsSaysSo`: `Failed("The speakers didn’t go back to their groups. Regroup them in the Sonos app.")`.
    - `optionsListFavouritesThenPlaylistsAndTheRooms`: picks "Sunday morning", "Party mix", then the playlist; rooms Kitchen, Lounge, Dining in layout order; `line` null.
    - `optionsWithSonosDownGiveTheLine`: `UNREACHABLE` → `CANT_REACH_SONOS`; a refusal → `SONOS_REFUSED`.
    - `noSystem`: `ready` false; `start` → the start line; `stop` → the stop line; `options().line == CANT_REACH_SONOS`.
    - `nothingPersonalReachesTheLog` (`ShadowLog`): no "Kitchen", "-id", "Party mix", "FV:".

  Run `./gradlew :capability:music:testDebugUnitTest --tests '*PartyMusic*' --tests '*MusicHouseSeam*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 4–6.
- [ ] **Step 3: The gate and the convention tests**, then commit.
```bash
git add capability/music build-logic
git commit -m "Let Party mode group the chosen speakers and play its pick on them, and at the end pause and regroup them only if the group is still the one it made and still playing"
```

---

### Task 4: `LightsParty`: Party's scenes and put-back through `HouseLights` (spec §2.5, §2.12, §2.13, §4.1.4, §4.2.2; rulings 2, 7; Review Focus 4)

**Review:** opus. The active-scene rule, the put-back choice, gone rooms and scenes.

**Files:**
- Modify `core/house/.../HouseSeams.kt`: ruling 2's four methods on `HouseLights`.
- Create `capability/lights/.../LightsParty.kt`; modify `LightsHouseSeam.kt` (delegates the four to `LightsParty`); `LightsHouseSeam`'s `@Inject` constructor gains `LightsParty`. `LogHygieneTest` expects `LightsParty.kt`.
- Modify `capability/holiday/src/test/.../TestHoliday.kt`: `FakeLights` answers the four with empty results and records nothing (Holiday never calls them).
- Test: `LightsPartyTest.kt` (`TestLightsSystem`, rooms from `room(...)`), `LightsHouseSeamTest`.

**Interfaces:**
- Consumes: Task 1's types; `Bridges`, `LightsSystem.readAll/rooms/recallScene/setOn/setBrightness`, `problemLine`, `HUE_REFUSED`.
- Produces: `class LightsParty internal constructor(system: () -> LightsSystem?)`, `@Inject constructor(bridges: Bridges)`, with ruling 2's four methods (same names and types). Log lines "Party lights: {BEFORE|RECALL|BACK|OPTIONS} failed (Type)".

- [ ] **Step 1: The failing tests.** Rooms: Kitchen `r1` on at 40 with scenes Bright `s1`, Relax `s2`, active `s2`; Lounge `r2` off with Relax `s3`, no active scene.
  - `LightsPartyTest`:
    - `beforeReadsTheChosenRoomsAfresh`: `calls` starts with `readAll`; → `[RoomBefore(r1, "Kitchen", s1, true, 40, s2), RoomBefore(r2, "Lounge", s3, false, 0, null)]`.
    - `aGoneRoomOrSceneIsSkipped` (Review Focus 4): choices `r9` (gone) and `r1` with `s3` (Lounge's scene) → empty; no throw.
    - `beforeWithTheBridgeDownThrows`: `readAll` throws `LightsUnreachableException` → it escapes.
    - `onRecallsEachScene`: `recallScene r1 s1`, `recallScene r2 s3`; → empty.
    - `aRoomThatRefusesIsNamed`: `recallScene r1 s1` throws `LightsRefusedException` → `["Kitchen"]`, and Lounge is still recalled.
    - `backRecallsTheRememberedScene`: after a read showing `r1` active `s1` → `recallScene r1 s2`; → empty.
    - `backWithNoRememberedSceneSetsBrightnessOrOff`: `RoomBefore(r1, …, s1, true, 40, null)` with `s1` active → `setBrightness r1 40`; `RoomBefore(r2, …, s3, false, 0, null)` with `s3` active → `setOn r2 false`.
    - `aSceneChangedByHandIsLeft`: `r1` active `s2` at the end → nothing sent, not named.
    - `noActiveSceneIsLeft` (Review Focus 4): `r1` with no active scene → nothing sent, not named.
    - `aRememberedSceneSinceDeletedFallsBackToBrightness` (Review Focus 4): `s2` gone from `r1` → `setBrightness r1 40`.
    - `backReadsAfresh`: `rooms` shows `s1` active before the call but `readAll` makes it `s2` → left.
    - `aGoneRoomAtTheEndIsSkipped`: `r1` gone → not named.
    - `backWithTheBridgeDownNamesEveryRoom`: → `["Kitchen", "Lounge"]`.
    - `optionsListRoomsWithTheirScenes`; `optionsWithTheBridgeDownGiveTheLine` (`UNREACHABLE` → its `problemLine`; refused → `HUE_REFUSED`).
    - `noSystem`: `partyOn` and `partyBack` name every room; `options().line` is `problemLine(UNREACHABLE)`.
    - `nothingPersonalReachesTheLog` (`ShadowLog`): no "Kitchen", "r1", "s1".
  - `LightsHouseSeamTest.thePartyCallsReachLightsParty`: each of the four on the seam gives `LightsParty`'s answer over the same `TestLightsSystem`.

  Run `./gradlew :capability:lights:testDebugUnitTest :capability:holiday:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 2 and 7.
- [ ] **Step 3: The gate and the convention tests**, then commit.
```bash
git add core/house capability/lights capability/holiday build-logic
git commit -m "Let Party mode recall a scene in each chosen room and at the end put back only the rooms still showing its scene"
```

---

### Task 5: `PartyHouse`: turn on, turn off, the plugs retry, restarts, the lines; the mode's registration (spec §2.2–§2.9, §4, §5; ruling 9; Review Focus 2, 3)

**Review:** opus. The order and independence of the steps, what's saved before each change, the put-back from the record, restarts.

**Files:**
- Create `capability/party/.../PartyHouse.kt`, `PartyScheduler.kt` (a tick at start and each minute, as `HolidayScheduler`).
- Modify `di/PartyModule.kt`: `@IntoSet Startable` (the scheduler), `@Binds @IntoSet HouseModeSwitch` (`PartyHouse`), and a companion `@Provides @IntoSet HouseModeInfo(HouseModes.PARTY, PartyWords.LABEL, 20, HousePermissions.PARTY_MODE)`.
- `LogHygieneTest` expects `PartyHouse.kt`.
- Test: `PartyHouseTest.kt`; `TestParty.kt` gains `FakePlugs` (as Holiday's: scriptable `ready`, `preview`, `begins`, queues of `enter` and `leave`, `throwOnLeave`, `calls`), `FakeMusic : HouseMusic` and `FakeLights : HouseLights` (scriptable answers, a shared ordered `calls` list across the three, a gate per call, `throws` per call), and the `WallClock` fake `HolidayHouseTest` uses.

**Interfaces:**
- Consumes: Task 1; `HousePlugs`, `HouseMusic`, `HouseLights` (with Task 4's methods), `WallClock`, `attempt`.
- Produces: `@Singleton class PartyHouse internal constructor(settings: PartySettings, plugs: HousePlugs?, music: HouseMusic?, lights: HouseLights?, wall: WallClock) : HouseModeSwitch`, `@Inject` over `Optional`s:
  - `val record: StateFlow<PartyRecord?>`; `override val isOn: StateFlow<Boolean?>`; `val running: StateFlow<PartyRun?>`; `val line: StateFlow<String?>`; `override val modeId = HouseModes.PARTY`; `override val modeName = PartyWords.LABEL`;
  - `suspend fun plan(): PartyPlan`; `suspend fun backLines(): List<String>` (`PartyWords.backLines(record, plugs?.ready(PARTY) == true)`); `suspend fun turnOn(by: Authorised)`; `suspend fun turnOff(by: Authorised)`; `suspend fun tick()`;
  - `companion object { const val RETRY_MS = 300_000L; const val OFF_LINE_MS = 600_000L }`.
  - Log lines name only the step (`PLUGS`, `MUSIC`, `LIGHTS`) and the exception type.

- [ ] **Step 1: The failing tests.** `by` = `grant("party.mode")` unless the test says otherwise. Choices: Party mix on every speaker; Bright in Kitchen, Relax in Lounge. Plugs ready with Lamp off.
  - `turnOnNeedsItsOwnGrant`: `grant("holiday.mode")` → `IllegalArgumentException`; nothing called, nothing saved.
  - `turnOnRunsEachStepInOrder`: `calls` = `plugs.begin party`, `plugs.enter party`, `music.before`, `music.start FV:2/3 null`, `lights.partyBefore`, `lights.partyOn`; `begins == [by]`; the store's record holds `on`, `musicBefore`, `made` and `lights`.
  - `theRecordIsSavedBeforeAnyDeviceIsTouched`: with `plugs.begin` gated, the store already reads `on = true`.
  - `eachSnapshotIsSavedBeforeItsChange` (Review Focus 3): with `music.start` gated, the store holds `musicBefore`; with `lights.partyOn` gated, it holds `lights`.
  - `nothingConfiguredStillTurnsOn`: no choices, plugs not ready → record on; no seam called but `ready`; `line` null; `plan().nothing`.
  - `aSystemNotReadyIsNoStep`: music not ready → no music call, no music line, even with a pick saved.
  - `aFailingStepDoesntStopTheOthers` (Review Focus 2): `music.start` → `Failed(start line)`; `lights.partyBefore` throws → lights still tried after music, plugs entered first; `line` = "The music didn’t start. Check the Sonos app.\nSome lights didn’t change: Kitchen and Lounge.".
  - `theLinesAreOnePerKindInOrder`: music failed, `enter` → `Failed("Lamp didn’t switch off. It’ll keep trying.")`, `partyOn` → `["Kitchen"]` → those three lines, music then plugs then lights.
  - `plugsAreRetriedEveryFiveMinutes`: the first `enter` failed → no `enter` at `RETRY_MS − 60_000`, one at `RETRY_MS`; `Done` → the plugs line goes; `music.start` and `lights.partyOn` were each called once in all.
  - `aPlugsStepThatThrowsShowsItsLine`: "The plugs didn’t switch. They’ll keep trying.", retried.
  - `aPlugsStepWithNoRunIsMissed`: `begin` throws, `enter` → null → "The plugs didn’t switch. Switch them on the Plugs tab."; no further `enter`.
  - `turnOffPutsBackInOrder`: `calls` after on = `music.stop`, `lights.partyBack`, `plugs.leave party`; `stop` got the saved `made` and `musicBefore`; `partyBack` the saved rooms; the record cleared.
  - `turnOffLinesAreMusicPlugsLights`: stop → stop line; leave → `Failed("Lamp didn’t switch back on. Switch it on the Plugs tab.")`; partyBack → `["Kitchen"]` → "The music didn’t stop. Stop it in the Sonos app.\nLamp didn’t switch back on. Switch it on the Plugs tab.\nSome lights didn’t go back: Kitchen.". It shows at `OFF_LINE_MS − 60_000` and is gone at `OFF_LINE_MS`; a turn-on before then clears it.
  - `turnOffWithEverythingThrowingStillEnds`: `stop`, `partyBack` and `leave` throw → the record cleared; lines the stop line, "Some plugs may not have gone back. Check them on the Plugs tab.", "Some lights didn’t go back: Kitchen and Lounge.".
  - `noGroupMadeNoMusicStop`: the record has no `made` → `stop` not called, no music line.
  - `aRestartMidPartyStillPutsBack` (Review Focus 3): a new house over the same store → `turnOff` calls `stop` with the saved `made` and `musicBefore` and `partyBack` with the saved rooms.
  - `aRestartBeforeTheGroupWasMadeLeavesTheMusic` (Review Focus 3): the store holds `musicBefore` only → no `stop`, no music line.
  - `aPlugsRetryAfterARestartTakesNoGrant` (Review Focus 3): a new house; the first tick → `enter(party)`, no `begin`.
  - `anOrphanRunIsPutBackWhenPartyLoadsAsOff` (Review Focus 3): no record; the first tick → `leave(party)` once; a second tick doesn't call it.
  - `aRecordThatWontSaveIsSavedAtTheNextTick`: `failOnWrite` on the first save → the house still on in memory, and the next tick saves it.
  - `turnOnWhileOnDoesNothing`; `aTurnOffMeetingATurnOnIsDropped`: `running == TURNING_ON` → `turnOff` returns at once.
  - `planListsWhatWillHappen`: plugs from `preview(party)`, music and scenes from the store; music not ready → `music == null`.
  - `isOnFollowsTheRecord`: null before the store answers, then true or false.
  - `nothingPersonalReachesTheLog` (`ShadowLog`): no "Kitchen", "Lounge", "Party mix", "Lamp", "FV:", "-id".

  Run `./gradlew :capability:party:testDebugUnitTest --tests '*PartyHouse*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 9.
- [ ] **Step 3: The gate and the convention tests**, then commit.
```bash
git add capability/party build-logic
git commit -m "Turn Party mode on and off: plugs, music and lights each set up in turn and put back from a saved record, plugs retried as Holiday's are, one line per kind that didn't work"
```

---

### Task 6: `PartyActions`, the exclusion (Holiday too), the capability, the tile and the sheet (spec §2.6–§2.8, §3, §5; rulings 10, 11; Review Focus 5)

**Review:** sonnet.

**Files:**
- Create `capability/party/.../PartyActions.kt`, `PartyCapability.kt`, `ui/PartyTile.kt` (`PartyTile` stateless, `PartyTileHost`), `ui/PartySheet.kt` (`PartySheet` stateless, `PartySheetHost`), `ui/PartyDimens.kt`; modify `di/PartyModule.kt` (`@IntoSet Capability`).
- Modify `capability/holiday/.../HolidayHouse.kt` (implements `HouseModeSwitch`: `modeId = HOLIDAY`, `modeName = HolidayWords.LABEL`, `isOn = holiday`), `di/HolidayModule.kt` (`@Binds @IntoSet HouseModeSwitch`), `HolidayActions.kt` (takes `Set<HouseModeSwitch>`; `refusal`; `suspend fun turnOn(back: LocalDate, by: Authorised): Boolean`), `ui/HolidayTile.kt` (the refusal as the line), `ui/HolidaySheet.kt` (confirms through `actions.turnOn`).
- Modify `core/ui/.../Icons.kt` (`CELEBRATION = "celebration"`), `tools/fonts/icons.txt`, the font.
- `LogHygieneTest` expects `PartyActions.kt`, `PartyTile.kt`, `PartySheet.kt`.
- Test: `PartyActionsTest.kt`, `PartyCapabilityTest.kt`, `ui/PartyTileTest.kt`, `ui/PartySheetTest.kt`, `ui/PartyScreenshotTest.kt`; `HolidayActionsTest`, `ui/HolidayTileTest`, `ui/HolidayScreenshotTest`; `core/ui` `IconFontTest`. A test `HouseModeSwitch` is `TestSwitch(modeId, modeName, isOn: MutableStateFlow<Boolean?>)` in each module's test helpers.

**Interfaces:**
- Consumes: Task 5's `PartyHouse`; Task 1's words, `HouseModeSwitch.blockedBy`, `HouseLines.turnOffFirst`; `AccessControl`, `LocalOverlayHost`, `rememberSingleAction`, `HhSheet`, `HhButton`, `HhIcon`, `hhClickable`, `shownNotSpoken`.
- Produces:
  - `@Singleton class PartyActions internal constructor(access: AccessControl, house: PartyHouse, switches: Set<HouseModeSwitch>)`, `@Inject` over `Set<@JvmSuppressWildcards HouseModeSwitch>`:
    - `val refusal: StateFlow<String?>`;
    - `suspend fun toggle(open: (Authorised, Boolean) -> Unit)` (the Boolean is "turning on"): nothing while `running` or before `isOn` is read; off and blocked → the refusal, no PIN; else `access.authorise(PARTY_MODE)`, null → nothing; then `open(by, !on)`;
    - `suspend fun turnOn(by: Authorised): Boolean` (blocked → refusal, false); `suspend fun turnOff(by: Authorised)` under `NonCancellable`.
  - `HolidayActions` likewise: `val refusal: StateFlow<String?>`, the check in `toggle` before `ask()`, and `turnOn(back, by): Boolean`.
  - `@Singleton class PartyCapability @Inject constructor(actions: PartyActions, house: PartyHouse, settings: PartySettings, music: Optional<HouseMusic>, lights: Optional<HouseLights>, plugs: Optional<HousePlugs>) : Capability`: `id = "party"`, `label = "Party mode"`, `icon = Icons.CELEBRATION`, `order = 76`, `hasTab` false, no cards, `trayButtons()` = `TrayButton("party.mode", 20) { PartyTileHost(actions, house) }`; `settingsPages()` empty until Task 7.
  - `@Composable internal fun PartyTile(on: Boolean, line: String?, running: PartyRun?, onTap: () -> Unit)`; `@Composable internal fun PartySheet(turningOn: Boolean, lines: List<String>, onConfirm: () -> Unit, onCancel: () -> Unit)`.
  - Tags: `party_tile`, `party_tile_state`, `party_tile_line`; `party_sheet`, `party_sheet_lines`, `party_sheet_confirm`, `party_sheet_cancel`.

- [ ] **Step 1: The failing tests.** Screen tests end with `assertClickablesHaveRoleAndLabel()`.
  - `PartyActionsTest`:
    - `aTapAsksForAFreshPartyPin`: off → `asked == [["party.mode"]]`, then `open(by, true)`; on → `open(by, false)`.
    - `aChildsPinOpensNothing`; `aCancelledPadOpensNothing`.
    - `holidayOnRefusesBeforeThePin` (Review Focus 5): holiday's switch on → no ask, `refusal == "Turn Holiday mode off first."`; holiday off → `refusal` null; a tap clears it too.
    - `turningOffIsNeverRefused`: Party on and Holiday (somehow) on → the PIN is asked.
    - `theConfirmChecksAgain` (Review Focus 5): holiday turns on after `open` → `turnOn(by)` is false, `house.turnOn` never runs, `refusal` set.
    - `aTapWhileRunningDoesNothing` (Review Focus 5); `aTapBeforeTheRecordIsReadDoesNothing`.
    - `aTurnOffSurvivesTheCallerBeingCancelled`.
  - `HolidayActionsTest`: `partyOnRefusesBeforeThePin` (Review Focus 5) → "Turn Party mode off first.", no ask; `theConfirmChecksAgain` (Review Focus 5) → `turnOn(back, by)` false and the house still off.
  - `PartyCapabilityTest.theTileSitsBesideHolidays`: one tray button, id `party.mode`, order 20; `label` "Party mode"; icon `celebration`; no tab, no cards.
  - `PartyTileTest`:
    - `offReadsOff`: "Party mode", `party_tile_state` "Off"; spoken "Party mode, off."; click label "Turn on".
    - `onReadsOn`: "On"; spoken "Party mode, on."; click label "Turn off".
    - `runningShowsAndIgnoresTaps`: "Turning on…"; spoken "Party mode, turning on."; a tap calls nothing.
    - `theLinesShowUnderIt`: two lines in `party_tile_line`.
  - `PartySheetTest`:
    - `theOnSheetListsWhatWillHappen`: `PartyWords.willLines`' sample in order under "Party mode will:"; "Turn Party mode on" confirms once.
    - `nothingToDoStillTurnsOn`: the decision 8 line; the confirm is there.
    - `theOffSheetListsWhatGoesBack`: "When Party mode ends:" and the back lines; "Turn Party mode off".
    - `cancelClosesWithoutTurning`.
    - `theHostTurnsOnThroughTheActions` (host, fakes): confirm → `house.turnOn` with the sheet's `by`; with Holiday turned on meanwhile → not called, and the tile's line reads "Turn Holiday mode off first.".
  - `HolidayTileTest.aRefusalShowsAsTheLine`: the host with Party on, tapped → `holiday_tile_line` "Turn Party mode off first.", no PIN asked.
  - `PartyScreenshotTest` (each `_dark` and `_light`): `party_tile_off`, `party_tile_on_lines` (on, with the music and lights lines), `party_sheet_on`, `party_sheet_nothing`, `party_sheet_off`.
  - `HolidayScreenshotTest`: `holiday_tile_refused`, `_dark` and `_light`.
  - `IconFontTest` covers `celebration`.

  Run `./gradlew :capability:party:testDebugUnitTest :capability:holiday:testDebugUnitTest :core:ui:testDebugUnitTest --tests '*IconFont*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces, rulings 10–11, the Copy and Units.
- [ ] **Step 3: Images.**
  - **New:** the 12 above. **Check against** Holiday's tile and sheet images (`holiday_tile_*`, `holiday_sheet_*`): same shapes and type, Party's icon and words.
  - **Must not change:** every other `holiday_*` image, and every `ShellScreenshotTest` image (if one changes, stop and ask).

  Then the gate and the convention tests, and commit.
```bash
git add capability/party capability/holiday core/ui tools/fonts build-logic
git commit -m "Add the Party mode tile beside Holiday's, with a fresh Adult or Admin PIN and a sheet to turn it on or off, and keep Party and Holiday from being on together"
```

---

### Task 7: Settings › Party mode; the Plugs tab's Party column (spec §3; ruling 12)

**Review:** sonnet.

**Files:**
- Create `capability/party/.../ui/PartySettingsPage.kt`: `PartyPage` (`SettingsPage`, id `"party"`, title "Party mode", order 475), `PartySettingsScreen` (stateless), `PartySettingsHost`, the music and scene sheets. Modify `PartyCapability.kt` (`settingsPages()` = `[PartyPage]`); `core/plugin/.../Setup.kt` (the `SettingsPage` KDoc's order list adds "Party mode 475").
- Modify `capability/plugs/src/test/.../ui/PlugsScreenshotTest.kt` (`plugs_tab_party_*`), `ui/PlugsTabTest.kt`.
- `LogHygieneTest` expects `PartySettingsPage.kt` if it logs.
- Test: `ui/PartySettingsPageTest.kt`, `ui/PartySettingsScreenshotTest.kt`.

**Interfaces:**
- Consumes: Task 1's store and types, `HouseMusic.options`, `HouseLights.partyOptions`, `HhPageTitle`, `HhSectionLabel`, `HhListRow`, `HhSwitchRow`, `HhRadioRow`, `HhSheet`, `LocalOverlayHost`.
- Produces:
  - `data class PartySettingsUi(val prefs: PartyPrefs, val music: MusicOptions?, val lights: LightOptions?)`, where null means that system isn't set up (seam absent or not `ready`); `toString` without names.
  - `@Composable fun PartySettingsScreen(ui: PartySettingsUi, onMusic: (MusicPick?) -> Unit, onSpeaker: (HouseRoom, Boolean) -> Unit, onScene: (LightRoomOption, HouseScene?) -> Unit)`.
  - `@Composable fun PartySettingsHost(settings: PartySettings, music: HouseMusic?, lights: HouseLights?)`: reads both option lists once when shown, collects `prefs`, writes through `settings.update`; a write that throws is logged.
  - Tags: `settings_party`, `party_music`, `party_speaker_{i}`, `party_light_{i}`, `party_plugs_line`, `party_music_line`, `party_lights_line`; sheets `party_music_sheet`, `party_music_{i}` (0 is "None"), `party_scene_sheet`, `party_scene_{i}` (0 is "Leave alone").

- [ ] **Step 1: The failing tests.** Screen tests end with `assertClickablesHaveRoleAndLabel()`.
  - `PartySettingsPageTest`:
    - `musicDefaultsToNone`: `party_music` reads "Music" and "None".
    - `pickingMusicSavesIt`: the sheet "Music for Party mode" lists "None", "Sunday morning", "Party mix"; picking "Party mix" → `onMusic(MusicPick("FV:2/3", "Party mix"))`; the host saves `MusicChoice("FV:2/3", "Party mix", null)`.
    - `noneClearsTheMusic`: the host saves `music = null`.
    - `everySpeakerByDefault`: three switch rows, all on.
    - `switchingARoomOffSavesTheRest`: Dining off → saved rooms `[Kitchen, Lounge]`; switching it on again → `rooms == null`.
    - `aLightRoomDefaultsToLeaveAlone`: `party_light_0` "Kitchen", "Leave alone".
    - `pickingASceneSavesIt`: the sheet titled "Kitchen" lists "Leave alone", "Bright", "Relax"; "Bright" → saved `SceneChoice(r1, "Kitchen", s1, "Bright")`; "Leave alone" → removed.
    - `aSavedChoiceNoLongerOfferedStillShows`: a saved pick absent from the options → the row shows its saved name.
    - `unlinkedSystemsSaySetUp`: `music = null` → "Set up Sonos in Settings › Music to choose the music."; `lights = null` → "Set up Hue in Settings › Lights to choose scenes."
    - `aReadFailureShowsTheLine`: `MusicOptions(empty, empty, CANT_REACH_SONOS text)` → that line in `party_music_line`.
    - `thePlugsLinePointsToThePlugsTab`: "Choose what each plug does in Party mode on the Plugs tab."
    - `theRowsSpeakTheirValue`: `party_music` spoken "Music, Party mix"; `party_light_0` "Kitchen, Bright"; `party_speaker_2` a switch named "Dining".
  - `PartyCapabilityTest.theSettingsPageSitsAfterLights`: one page, id `party`, title "Party mode", order 475.
  - `PlugsTabTest.aSecondModeIsASecondColumn` (**pin**: Plan 12 §5.1): modes holiday and party at rest → headings "Holiday mode" then "Party mode", and each row holds both cells.
  - `PartySettingsScreenshotTest` (each `_dark` and `_light`): `party_settings` (music chosen, Dining off, Bright in Kitchen, Lounge left alone), `party_settings_unlinked`, `party_settings_music_sheet`.
  - `PlugsScreenshotTest`: `plugs_tab_party_dark` / `_light` (the four sample rows, Holiday and Party columns, at rest).

  Run `./gradlew :capability:party:testDebugUnitTest :capability:plugs:testDebugUnitTest`. Expected: FAIL (the pin aside).
- [ ] **Step 2: Implement** per ruling 12, the Copy and Units.
- [ ] **Step 3: Images.** **New:** the 8 above. **Check against** Settings › Music and › Lights (`music_settings_*`, `lights_settings_*`) for rows and sheets, and `plugs_tab_long_name_edit_*` for the two-column header. **Must not change:** every other `plugs_*` image.
```bash
git add capability/party capability/plugs core/plugin build-logic
git commit -m "Add Settings › Party mode to choose the music, the speakers and a scene per room, and show Party mode's column on the Plugs tab"
```

---

### Task 8: The app wired in; the debug sample speakers and lights; the README (spec §6a, §8; ruling 13)

**Review:** sonnet.

**Files:**
- Modify `app/build.gradle.kts` (`:capability:party`), `app/src/debug/.../di/HouseGraphProbe.kt` (`partyHouse(): PartyHouse`, `music(): Optional<HouseMusic>`, `switches(): Set<HouseModeSwitch>`).
- Create in `app/src/debug/java/uk/co/siland/culvery/`: `sample/SampleHouse.kt` (`SampleMusicProvider`, `SampleLightsProvider` and their in-memory systems), `di/SampleHouseModule.kt` (`@IntoSet` each), `DebugSampleHouseReceiver.kt`; register the receiver in `app/src/debug/AndroidManifest.xml` as the others are.
- Modify `README.md`: the `:capability:party` row; `:core:house`'s row names `HouseMusic` and `HouseModeSwitch` and the `party.mode` permission; `:capability:music`'s row names grouping and `HouseMusic`; `:capability:lights`' row names Party's scenes.
- `LogHygieneTest` expects `DebugSampleHouseReceiver.kt` if it logs.
- Test: `app` `HouseGraphTest`, `NoBackupTest`, new `SampleHouseTest.kt`.

**Interfaces:**
- Consumes: Tasks 1–7; `MusicSettings.connect`, `LightsSettings.connect`, `MusicLink`, `LightsLink`.
- Produces: ruling 13's providers (descriptor ids `sample.music`, `sample.lights`) and receiver extras `sample` (Boolean), `hand` (`regroup` | `pause` | `scene`) and `down` (Boolean).

- [ ] **Step 1: The failing tests.**
  - `HouseGraphTest.partyIsInTheHouse`: `modes()` holds `party` with permission `party.mode` and order 20; `switches()` holds `holiday` and `party`; `music()` is present; `partyHouse()` resolves; `startables()` holds a `PartyScheduler`.
  - `NoBackupTest` (**pin**: `party_settings` is under `files/datastore/`).
  - `SampleHouseTest`:
    - `theSampleSpeakersGroupAndRegroup`: `group(Kitchen, all)` → one group of three; `regroup` back → three alone.
    - `theSampleSpeakersPlayAPick`: `playFavourite` → that group `PLAYING`, and `playingNow()` lists it.
    - `theSampleLightsShowTheSceneRecalled`: `recallScene(Kitchen, Bright)` → Kitchen's `activeSceneId` is Bright's.
    - `aHandChangeIsWhatTheReceiverSays`: `hand=regroup` moves Dining out of the party group; `hand=pause` pauses it; `hand=scene` clears Kitchen's active scene.
    - `downMakesEveryCallUnreachable`: with `down` on, `checkLayout()` and `readAll()` throw the unreachable exceptions; off again, they answer.
    - `theSamplesFindNothing`: `find()` and `check(any)` → null.

  Run `./gradlew :app:testDebugUnitTest --tests '*HouseGraph*' --tests '*NoBackup*' --tests '*SampleHouse*'`. Expected: FAIL (the pin aside).
- [ ] **Step 2: Implement** per ruling 13.
- [ ] **Step 3: The gate and the convention tests.** **Must not change:** any screenshot. Then commit.
```bash
git add app README.md build-logic
git commit -m "Wire Party mode into the app, and add sample speakers and lights to debug builds for walking it through on the emulator"
```

---

### Task 9: Simplify the whole branch

- [ ] **Step 1:** Run the `simplify` skill over `git diff main...HEAD`. Look hardest at:
  - `PartyHouse` beside `HolidayHouse` (`exclusive`, `persist`, `showLine`, the plugs retry, `RETRY_MS` / `OFF_LINE_MS`): a shared piece in `:core:house` only if it shortens both;
  - `PartyTile` / `PartySheet` beside Holiday's;
  - `MusicHouseSeam`'s failure-to-line mapping beside `MusicActions.sheetLine`;
  - `SonosSystem.group` and `regroup` (one per-room loop if they read alike).
- [ ] **Step 2:** For each change, run the affected module's tests, then the gate.
- [ ] **Step 3:** Commit when anything changed: `git commit -am "Simplify the Party mode code"`.

---

### Task 10: The emulator walk-through on `culvery-5` (spec §3, §8)

**Rules** (as Plan 12's Task 8):
- Use only the `culvery-5` AVD and a debug build with `install -r`.
- The user types the real PINs; Claude enters the debug sample PINs.
- Collect faults and ask before fixing.
- **No real speaker, light or plug is touched.** Before the samples are connected, check Settings › Music and › Lights: if a real connection is saved, the walk-through runs on the samples only (`--ez sample true`). A real plug on On or Off in the Party column is set to Ignore first, or the AVD is in airplane mode from before turning on until after turning off, as Plan 12's walk-through was.
- Names, ids and addresses never go into a file or commit.

- [ ] **Step 0:** Note in `$LISTS` whether `culvery-5` has saved Sonos and Hue connections. `adb -s "$S" shell am broadcast -n uk.co.siland.culvery/.DebugSampleHouseReceiver --ez sample true`. If it had none, tell the user the sample connections stay in the emulator's debug data afterwards (there's no "forget" in the app), and go on only on their say.
- [ ] **Step 1: Walk** (one line each).
  1. The One-tap tray shows Holiday's tile then Party's, "Party mode" / "Off"; TalkBack on Party's tile.
  2. Settings › Party mode: "None"; pick Party mix; Dining off and on again; Bright in Kitchen, Lounge left alone; the plugs line.
  3. The Plugs tab shows a "Party mode" column; Edit (PIN) offers Ignore / On / Off in it; every real plug is left on Ignore, per the rules.
  4. Tap Party's tile: a Child's PIN is refused; an Adult's opens the sheet with "Play Party mix on every speaker." and "Bright in Kitchen."; Cancel changes nothing.
  5. Turn on: the sample speakers form one group playing Party mix; Kitchen shows Bright; the tile reads "On".
  6. Holiday's tile while Party is on: "Turn Party mode off first.", no PIN pad; it goes when Party is off.
  7. Turn off (PIN, the off sheet): the group breaks back up and stops; Kitchen goes back; no line.
  8. Again with hand changes: `--es hand regroup` → at turn-off the music is left; `--es hand scene` → Kitchen is left.
  9. Again with the samples down (`--ez down true` before turning on, `false` after turning off): turn on → "The music didn’t start. Check the Sonos app." and "Some lights didn’t change: Kitchen."; turn off → the off lines, gone after 10 minutes.
  10. With nothing chosen: the sheet's "Party mode has nothing to do yet. …"; it turns on and off.
  11. A restart mid-party (`adb shell am force-stop`, reopen): the tile still reads "On"; turning off puts back.
  12. Holiday on, then Party's tile: "Turn Holiday mode off first.".
- [ ] **Step 2: The fault loop**; `--ez sample false`; put back every setting changed; `emu kill`.
- [ ] **Step 3: Record** under "Plan 13 (Party mode): emulator walk-through", with no names or ids. Commit: `git commit -am "Record Plan 13's emulator walk-through"`.

---

### Task 11: Fixtures from captures; on the tablet (spec §6, §8)

**Needs the SM-T510 on the cable.** 4e's tablet rules apply: a signed release with `install -r`, never uninstall; save and restore every setting changed; the names check before any commit.

- [ ] **Step 1: Install the release** (mapping kept in `culvery-notes/mappings/`).
- [ ] **Step 2: With the user, read-only:** Settings › Party mode lists the household's favourites, playlists, speaker rooms, Hue rooms and scenes; choose what the user says; the sheet's lines match. Nothing is turned on.
- [ ] **Step 3: Only on the user's go, a real party run**, with the capture on: `adb -s <tablet> logcat -c`; `setprop log.tag.SonosCapture DEBUG`; turn Party on, check the speakers grouped and the scenes; then a hand change if the user wants one; turn off, check the put-back; `adb -s <tablet> logcat -d -s SonosCapture:D > "$LISTS/party-capture.txt"`; `setprop log.tag.SonosCapture INFO`. **The names check:** grep the file for the speakers' addresses, `RINCON_` ids, room and favourite names on the user's list: no match, else widen `SonosXml.redact` (failing case first) and recapture. Without the user's go, skip to Step 5 and say Party's first real use is the check.
- [ ] **Step 4: Fixtures (failing first).** Replace `standAlone()` and `transportUriSet()` in `SonosFixtures.kt` with the redacted answers; run `./gradlew :provider:music-sonos:testDebugUnitTest`. Expected: FAIL where the shapes differ (else say so). Fix with the user's go, run the gate and the names check, commit: `git commit -am "Replace the grouping fixtures with redacted answers from the household's speakers"`.
- [ ] **Step 5: The log scan.** `adb -s <tablet> logcat -d -s PartyHouse PartyActions PartySettings MusicHouseSeam LightsParty HolidayActions HolidayHouse > "$LISTS/plan13-log.txt"`, then grep it for the names, ids and addresses: no match.
- [ ] **Step 6: Record** under "Plan 13: on the tablet". Commit: `git commit -am "Record Plan 13 on the tablet"`.

---

### Task 12: Final review, merge, install

- [ ] **Step 1:** Opus reviews `git diff main...HEAD` against the spec and this plan, looking first at the music grouping and put-back, the hand-change rules, what the record holds before each change, the exclusion and PINs, and the logs. Then one fix wave, each fix with its failing test first, and the gate.
- [ ] **Step 2:** Finish with `superpowers:finishing-a-development-branch`:
  - only on the user's say, `git merge --ff-only plan-13-party-mode` on `main`, then the gate on `main`; never push;
  - move the design cuts to the follow-ups.
- [ ] **Step 3:** Install the release from the merged `main` on the tablet with `install -r`.

---

## Walk-through

1. The One-tap tray holds Holiday's tile, then "Party mode" / "Off".
2. Settings › Party mode chooses the music ("None" by default), the speakers (all by default) and a scene or "Leave alone" per Hue room, and points to the Plugs tab for plugs.
3. The Plugs tab has a "Party mode" column beside Holiday's.
4. The tile asks an Adult's or Admin's PIN; a Child's is refused; the sheet lists what will happen, or that there's nothing to do yet.
5. On: one speaker group plays the pick, the scenes show, the plugs switch; a step that fails says so on the tile in one line per kind.
6. Off (PIN, the sheet of what goes back): the music stops, the groups come back, the lights and plugs go back; anything changed by hand is left.
7. Holiday and Party refuse each other's turn-on with "Turn … off first." and no PIN pad.
8. A restart mid-party still puts back.

**Design cuts to say again:**
- the tile's look copied from Holiday's, and the `celebration` icon;
- the sheet's full stops and the off sheet's wording;
- "Play {playlist} on {rooms}." when not every speaker is chosen;
- switch rows (not checkboxes) for the speakers, and the pickers as sheets;
- the speakers-not-regrouped line.

## Not in this build, on purpose

- A timer or automatic end; a volume setting; a pick at tap time; resuming the music from before.
- Retrying music or lights while on (decision 9).
- Colour per light: rooms go back by scene, or on/off and brightness (spec §7).
- A "forget" for the sample connections in debug builds.

---

## Spec coverage (13 → tasks)

| Spec | Task |
|---|---|
| §1 goals, anti-goals | 3, 4, 5, 6 |
| §2.1 configurable, §2.8 nothing configured | 1, 5, 6, 7 |
| §2.2 put back, §2.4 groups after the music stops, §2.10 no resume, §2.11 still Party's | 2, 3, 5 |
| §2.3 no automatic end, §2.9 no music or lights retries, plugs' kept | 5 |
| §2.5 hand changes left, §2.12 the active scene | 3, 4 |
| §2.6 Holiday/Party exclusion | 1, 6 |
| §2.7 the tile beside Holiday's, `party.mode` | 1, 6 |
| §2.13 gone rooms skipped | 3, 4 |
| §2.14 one line per kind, names never logged | 1, 5, 6 |
| §3 tile, sheet, turning off, Settings › Party mode, the Plugs column | 5, 6, 7 |
| §4.1–§4.2 the steps in order | 2–5 |
| §4.3 restarts | 1, 5 |
| §5 lines | 1, 3, 4, 5, 6 |
| §6 constraints, privacy, real devices only on the user's go | all; 10, 11 |
| §6a structure: seams, grouping in `MusicSystem` and every fake, Holiday changes | 1, 2, 3, 4, 6, 8 |
| §7 left to the builder | rulings 3, 4, 7, 12 |
| §8 unit, Roborazzi, emulator walk-through with fakes, real run on the user's go | 1–8, 10, 11 |
