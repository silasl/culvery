package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.house.HouseScene
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.RoomChoice

private val LOUNGE_RELAX = LightScene("lounge-relax-id", "Relax")

/** 13 §2.12, §2.13; 15b §4.2, §4.3: a mode's room choices, and putting back only the rooms still as the mode left them. */
@RunWith(AndroidJUnit4::class)
class LightsModesTest {
    private fun kitchen(active: String? = RELAX.id, scenes: List<LightScene> = listOf(BRIGHT, RELAX)) =
        room("Kitchen", on = true, brightness = 40, scenes = scenes, active = active)

    private fun lounge(active: String? = null) = room("Lounge", on = false, brightness = 0, scenes = listOf(LOUNGE_RELAX), active = active)

    private val system = TestLightsSystem(listOf(kitchen(), lounge()))
    private val modes = LightsModes({ system })
    private val down = LightsModes({ null })

    private val kitchenChoice = RoomChoice("Kitchen-id", "Kitchen", LightChoice.Scene(BRIGHT.id, "Bright"))
    private val loungeChoice = RoomChoice("Lounge-id", "Lounge", LightChoice.Scene(LOUNGE_RELAX.id, "Relax"))
    private val kitchenBefore = RoomBefore("Kitchen-id", "Kitchen", LightChoice.Scene(BRIGHT.id, "Bright"), true, 40, RELAX.id)
    private val loungeBefore = RoomBefore("Lounge-id", "Lounge", LightChoice.Scene(LOUNGE_RELAX.id, "Relax"), false, 0, null)

    private suspend fun thrownBy(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: LightsUnreachableException) {
        e
    }

    /** Fails if the rooms are taken from what was last cached, or a room's state is misread. */
    @Test
    fun beforeReadsTheChosenRoomsAfresh() = runTest {
        assertThat(modes.before(listOf(kitchenChoice, loungeChoice))).containsExactly(kitchenBefore, loungeBefore).inOrder()
        assertThat(system.calls.first()).isEqualTo("readAll")
    }

    /** Fails if a gone room, or a scene the room doesn't have, is kept or throws (13 §2.13). */
    @Test
    fun aGoneSceneIsLeftOut() = runTest {
        val gone = RoomChoice("Garden-id", "Garden", LightChoice.Scene(BRIGHT.id, "Bright"))
        val wrongScene = kitchenChoice.copy(choice = LightChoice.Scene(LOUNGE_RELAX.id, "Relax"))
        assertThat(modes.before(listOf(gone, wrongScene))).isEmpty()
    }

    /** Fails if a bridge that can't be read is swallowed: Party must say it couldn't start. */
    @Test
    fun beforeWithTheBridgeDownThrows() = runTest {
        system.failures["readAll"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        assertThat(thrownBy { modes.before(listOf(kitchenChoice)) }).isInstanceOf(LightsUnreachableException::class.java)
        assertThat(thrownBy { down.before(listOf(kitchenChoice)) }).isInstanceOf(LightsUnreachableException::class.java)
    }

    /** Fails if a room's scene isn't recalled, or a clean run names anyone. */
    @Test
    fun onRecallsEachScene() = runTest {
        assertThat(modes.on(listOf(kitchenBefore, loungeBefore))).isEmpty()
        assertThat(system.calls).containsExactly("recallScene Kitchen-id bright-id", "recallScene Lounge-id lounge-relax-id").inOrder()
    }

    /** Fails if one refusing room stops the others, or isn't named. */
    @Test
    fun aRoomThatRefusesIsNamed() = runTest {
        system.failures["recallScene Kitchen-id bright-id"] = LightsRefusedException()
        assertThat(modes.on(listOf(kitchenBefore, loungeBefore))).containsExactly("Kitchen")
        assertThat(system.calls).contains("recallScene Lounge-id lounge-relax-id")
    }

    /** Fails if the scene the room showed before Party isn't recalled when Party's is still active. */
    @Test
    fun backRecallsTheRememberedScene() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge())
        assertThat(modes.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll", "recallScene Kitchen-id relax-id").inOrder()
    }

    /** Fails if a room with no remembered scene is left lit or left off, instead of as it was. */
    @Test
    fun backWithNoRememberedSceneSetsBrightnessOrOff() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge(active = LOUNGE_RELAX.id))
        val lit = kitchenBefore.copy(activeSceneId = null)
        assertThat(modes.back(listOf(lit, loungeBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll", "setBrightness Kitchen-id 40", "setOn Lounge-id false").inOrder()
    }

    /** Fails if a room that was off is lit again because the bridge still reported its old scene as active. */
    @Test
    fun aRoomOffBeforeGoesBackOffEvenWithASceneRemembered() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge())
        val off = kitchenBefore.copy(on = false, brightness = 0)
        assertThat(modes.back(listOf(off))).isEmpty()
        assertThat(system.calls).containsExactly("readAll", "setOn Kitchen-id false").inOrder()
    }

    /** Fails if a room whose scene someone changed during the party is touched or named (13 §2.12). */
    @Test
    fun aSceneChangedByHandIsLeft() = runTest {
        assertThat(modes.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll")
    }

    /** Fails if a room with no active scene reported is put back (13 §2.12). */
    @Test
    fun noActiveSceneIsLeft() = runTest {
        system.rooms.value = listOf(kitchen(active = null), lounge())
        assertThat(modes.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll")
    }

    /** Fails if a remembered scene deleted since is recalled, or the room is left. */
    @Test
    fun aRememberedSceneSinceDeletedFallsBackToBrightness() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id, scenes = listOf(BRIGHT)), lounge())
        assertThat(modes.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll", "setBrightness Kitchen-id 40").inOrder()
    }

    /** Fails if the check uses the rooms as last cached rather than as the bridge shows now. */
    @Test
    fun backReadsAfresh() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge())
        system.onRead = { system.rooms.value = listOf(kitchen(active = RELAX.id), lounge()) }
        assertThat(modes.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll")
    }

    /** Fails if a room that has gone is named as one that didn't go back. */
    @Test
    fun aGoneRoomAtTheEndIsSkipped() = runTest {
        system.rooms.value = listOf(lounge())
        assertThat(modes.back(listOf(kitchenBefore))).isEmpty()
    }

    /** Fails if one room's refusal is silent, or stops the next room. */
    @Test
    fun aRoomThatRefusesToGoBackIsNamed() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge(active = LOUNGE_RELAX.id))
        system.failures["recallScene Kitchen-id relax-id"] = LightsRefusedException()
        assertThat(modes.back(listOf(kitchenBefore, loungeBefore))).containsExactly("Kitchen")
        assertThat(system.calls).contains("setOn Lounge-id false")
    }

    /** Fails if a bridge that can't be read leaves a room unnamed. */
    @Test
    fun backWithTheBridgeDownNamesEveryRoom() = runTest {
        system.failures["readAll"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        assertThat(modes.back(listOf(kitchenBefore, loungeBefore))).containsExactly("Kitchen", "Lounge").inOrder()
    }

    /** Fails if a room that has gone is kept, whatever its choice (15b §4.2). */
    @Test
    fun aGoneRoomIsLeftOutWhateverItsChoice() = runTest {
        val gone = listOf(LightChoice.Scene(BRIGHT.id, "Bright"), LightChoice.On(60), LightChoice.Off).map { RoomChoice("Garden-id", "Garden", it) }
        assertThat(modes.before(gone)).isEmpty()
    }

    /** Fails if On or Off needs a scene to be kept. */
    @Test
    fun onAndOffAreKeptForAnyExistingRoom() = runTest {
        val choices = listOf(RoomChoice("Kitchen-id", "Kitchen", LightChoice.On(60)), RoomChoice("Lounge-id", "Lounge", LightChoice.Off))
        assertThat(modes.before(choices).map { it.choice }).containsExactly(LightChoice.On(60), LightChoice.Off).inOrder()
    }

    /** Fails if On at a percent doesn't set the brightness (15b §4.2). */
    @Test
    fun onAtAPercentSetsTheBrightness() = runTest {
        assertThat(modes.on(listOf(kitchenBefore.copy(choice = LightChoice.On(60))))).isEmpty()
        assertThat(system.calls).containsExactly("setBrightness Kitchen-id 60")
    }

    /** Fails if Off leaves the room lit. */
    @Test
    fun offTurnsTheRoomOff() = runTest {
        assertThat(modes.on(listOf(kitchenBefore.copy(choice = LightChoice.Off)))).isEmpty()
        assertThat(system.calls).containsExactly("setOn Kitchen-id false")
    }

    private fun backOf(before: RoomBefore, now: LightRoom) {
        system.rooms.value = listOf(now)
        system.calls.clear()
        runBlocking { modes.back(listOf(before)) }
    }

    private fun wasPutBack() = system.calls.any { it.startsWith("set") || it.startsWith("recall") }

    private val onSixty = kitchenBefore.copy(choice = LightChoice.On(60), on = false, brightness = 0, activeSceneId = null)

    /** Fails if the 5-point tolerance is off by one either way (15b §4.3). */
    @Test
    fun onRoomsGoBackWithinFivePoints() {
        backOf(onSixty, room("Kitchen", on = true, brightness = 65))
        assertThat(wasPutBack()).isTrue()
        backOf(onSixty, room("Kitchen", on = true, brightness = 55))
        assertThat(wasPutBack()).isTrue()
        backOf(onSixty, room("Kitchen", on = true, brightness = 66))
        assertThat(wasPutBack()).isFalse()
        backOf(onSixty, room("Kitchen", on = false, brightness = 60))
        assertThat(wasPutBack()).isFalse()
    }

    /** Fails if a room turned on by hand after the mode turned it off is switched off again. */
    @Test
    fun anOffRoomTurnedOnByHandStays() {
        backOf(kitchenBefore.copy(choice = LightChoice.Off), room("Kitchen", on = true, brightness = 40))
        assertThat(wasPutBack()).isFalse()
    }

    /** Fails if a room the mode switched off isn't put back. */
    @Test
    fun anOffRoomStillOffGoesBack() {
        backOf(kitchenBefore.copy(choice = LightChoice.Off, activeSceneId = null), room("Kitchen", on = false, brightness = 0))
        assertThat(system.calls).containsExactly("readAll", "setBrightness Kitchen-id 40").inOrder()
    }

    /** Fails if a room that can't dim is judged by a brightness it doesn't have (S5). */
    @Test
    fun aNonDimmableOnRoomGoesBack() {
        backOf(onSixty.copy(choice = LightChoice.On(100)), room("Kitchen", on = true, brightness = 3, dimmable = false))
        assertThat(wasPutBack()).isTrue()
    }

    /** Fails if the rooms or their scenes are lost or reordered. */
    @Test
    fun optionsListRoomsWithTheirScenes() = runTest {
        val options = modes.options()
        assertThat(options.line).isNull()
        assertThat(options.rooms).containsExactly(
            LightRoomOption("Kitchen-id", "Kitchen", listOf(HouseScene(BRIGHT.id, "Bright"), HouseScene(RELAX.id, "Relax"))),
            LightRoomOption("Lounge-id", "Lounge", listOf(HouseScene(LOUNGE_RELAX.id, "Relax"))),
        ).inOrder()
    }

    /** Fails if a failed read gives rooms, or no line saying why. */
    @Test
    fun optionsWithTheBridgeDownGiveTheLine() = runTest {
        system.failures["readAll"] = LightsUnreachableException(LightsProblem.OFFLINE)
        assertThat(modes.options().line).isEqualTo(problemLine(LightsProblem.OFFLINE))
        assertThat(modes.options().rooms).isEmpty()
        system.failures["readAll"] = LightsRefusedException()
        assertThat(modes.options().line).isEqualTo(HUE_REFUSED)
    }

    /** Fails if no bridge is a throw or a silent success. */
    @Test
    fun noSystem() = runTest {
        assertThat(down.on(listOf(kitchenBefore, loungeBefore))).containsExactly("Kitchen", "Lounge").inOrder()
        assertThat(down.back(listOf(kitchenBefore, loungeBefore))).containsExactly("Kitchen", "Lounge").inOrder()
        assertThat(down.options().line).isEqualTo(problemLine(LightsProblem.UNREACHABLE))
    }

    /** Fails if a log line holds a room name or an id (13 §2.14). */
    @Test
    fun nothingPersonalReachesTheLog() = runTest {
        ShadowLog.clear()
        system.failures["recallScene Kitchen-id bright-id"] = LightsRefusedException()
        modes.on(listOf(kitchenBefore))
        system.failures["readAll"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        modes.back(listOf(kitchenBefore))
        modes.options()
        thrownBy { modes.before(listOf(kitchenChoice)) }
        val lines = ShadowLog.getLogs().filter { it.tag == "LightsModes" }.map { it.msg }
        assertThat(lines).hasSize(4)
        lines.forEach { line -> listOf("Kitchen", "Lounge", "-id", "Relax").forEach { assertThat(line).doesNotContain(it) } }
    }
}
