package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.house.HouseScene
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.SceneChoice

private val LOUNGE_RELAX = LightScene("lounge-relax-id", "Relax")

/** 13 §2.12, §2.13, §4.1.4, §4.2.2: Party's scenes, and putting back only the rooms still showing Party's scene. */
@RunWith(AndroidJUnit4::class)
class LightsPartyTest {
    private fun kitchen(active: String? = RELAX.id, scenes: List<LightScene> = listOf(BRIGHT, RELAX)) =
        room("Kitchen", on = true, brightness = 40, scenes = scenes, active = active)

    private fun lounge(active: String? = null) = room("Lounge", on = false, brightness = 0, scenes = listOf(LOUNGE_RELAX), active = active)

    private val system = TestLightsSystem(listOf(kitchen(), lounge()))
    private val party = LightsParty({ system })
    private val down = LightsParty({ null })

    private val kitchenChoice = SceneChoice("Kitchen-id", "Kitchen", BRIGHT.id, "Bright")
    private val loungeChoice = SceneChoice("Lounge-id", "Lounge", LOUNGE_RELAX.id, "Relax")
    private val kitchenBefore = RoomBefore("Kitchen-id", "Kitchen", BRIGHT.id, true, 40, RELAX.id)
    private val loungeBefore = RoomBefore("Lounge-id", "Lounge", LOUNGE_RELAX.id, false, 0, null)

    private suspend fun thrownBy(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: LightsUnreachableException) {
        e
    }

    /** Fails if the rooms are taken from what was last cached, or a room's state is misread. */
    @Test
    fun beforeReadsTheChosenRoomsAfresh() = runTest {
        assertThat(party.before(listOf(kitchenChoice, loungeChoice))).containsExactly(kitchenBefore, loungeBefore).inOrder()
        assertThat(system.calls.first()).isEqualTo("readAll")
    }

    /** Fails if a gone room, or a scene the room doesn't have, is kept or throws (13 §2.13). */
    @Test
    fun aGoneRoomOrSceneIsSkipped() = runTest {
        val gone = SceneChoice("Garden-id", "Garden", BRIGHT.id, "Bright")
        val wrongScene = kitchenChoice.copy(sceneId = LOUNGE_RELAX.id)
        assertThat(party.before(listOf(gone, wrongScene))).isEmpty()
    }

    /** Fails if a bridge that can't be read is swallowed: Party must say it couldn't start. */
    @Test
    fun beforeWithTheBridgeDownThrows() = runTest {
        system.failures["readAll"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        assertThat(thrownBy { party.before(listOf(kitchenChoice)) }).isInstanceOf(LightsUnreachableException::class.java)
        assertThat(thrownBy { down.before(listOf(kitchenChoice)) }).isInstanceOf(LightsUnreachableException::class.java)
    }

    /** Fails if a room's scene isn't recalled, or a clean run names anyone. */
    @Test
    fun onRecallsEachScene() = runTest {
        assertThat(party.on(listOf(kitchenBefore, loungeBefore))).isEmpty()
        assertThat(system.calls).containsExactly("recallScene Kitchen-id bright-id", "recallScene Lounge-id lounge-relax-id").inOrder()
    }

    /** Fails if one refusing room stops the others, or isn't named. */
    @Test
    fun aRoomThatRefusesIsNamed() = runTest {
        system.failures["recallScene Kitchen-id bright-id"] = LightsRefusedException()
        assertThat(party.on(listOf(kitchenBefore, loungeBefore))).containsExactly("Kitchen")
        assertThat(system.calls).contains("recallScene Lounge-id lounge-relax-id")
    }

    /** Fails if the scene the room showed before Party isn't recalled when Party's is still active. */
    @Test
    fun backRecallsTheRememberedScene() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge())
        assertThat(party.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll", "recallScene Kitchen-id relax-id").inOrder()
    }

    /** Fails if a room with no remembered scene is left lit or left off, instead of as it was. */
    @Test
    fun backWithNoRememberedSceneSetsBrightnessOrOff() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge(active = LOUNGE_RELAX.id))
        val lit = kitchenBefore.copy(activeSceneId = null)
        assertThat(party.back(listOf(lit, loungeBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll", "setBrightness Kitchen-id 40", "setOn Lounge-id false").inOrder()
    }

    /** Fails if a room that was off is lit again because the bridge still reported its old scene as active. */
    @Test
    fun aRoomOffBeforeGoesBackOffEvenWithASceneRemembered() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge())
        val off = kitchenBefore.copy(on = false, brightness = 0)
        assertThat(party.back(listOf(off))).isEmpty()
        assertThat(system.calls).containsExactly("readAll", "setOn Kitchen-id false").inOrder()
    }

    /** Fails if a room whose scene someone changed during the party is touched or named (13 §2.12). */
    @Test
    fun aSceneChangedByHandIsLeft() = runTest {
        assertThat(party.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll")
    }

    /** Fails if a room with no active scene reported is put back (13 §2.12). */
    @Test
    fun noActiveSceneIsLeft() = runTest {
        system.rooms.value = listOf(kitchen(active = null), lounge())
        assertThat(party.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll")
    }

    /** Fails if a remembered scene deleted since is recalled, or the room is left. */
    @Test
    fun aRememberedSceneSinceDeletedFallsBackToBrightness() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id, scenes = listOf(BRIGHT)), lounge())
        assertThat(party.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll", "setBrightness Kitchen-id 40").inOrder()
    }

    /** Fails if the check uses the rooms as last cached rather than as the bridge shows now. */
    @Test
    fun backReadsAfresh() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge())
        system.onRead = { system.rooms.value = listOf(kitchen(active = RELAX.id), lounge()) }
        assertThat(party.back(listOf(kitchenBefore))).isEmpty()
        assertThat(system.calls).containsExactly("readAll")
    }

    /** Fails if a room that has gone is named as one that didn't go back. */
    @Test
    fun aGoneRoomAtTheEndIsSkipped() = runTest {
        system.rooms.value = listOf(lounge())
        assertThat(party.back(listOf(kitchenBefore))).isEmpty()
    }

    /** Fails if one room's refusal is silent, or stops the next room. */
    @Test
    fun aRoomThatRefusesToGoBackIsNamed() = runTest {
        system.rooms.value = listOf(kitchen(active = BRIGHT.id), lounge(active = LOUNGE_RELAX.id))
        system.failures["recallScene Kitchen-id relax-id"] = LightsRefusedException()
        assertThat(party.back(listOf(kitchenBefore, loungeBefore))).containsExactly("Kitchen")
        assertThat(system.calls).contains("setOn Lounge-id false")
    }

    /** Fails if a bridge that can't be read leaves a room unnamed. */
    @Test
    fun backWithTheBridgeDownNamesEveryRoom() = runTest {
        system.failures["readAll"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        assertThat(party.back(listOf(kitchenBefore, loungeBefore))).containsExactly("Kitchen", "Lounge").inOrder()
    }

    /** Fails if the rooms or their scenes are lost or reordered. */
    @Test
    fun optionsListRoomsWithTheirScenes() = runTest {
        val options = party.options()
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
        assertThat(party.options().line).isEqualTo(problemLine(LightsProblem.OFFLINE))
        assertThat(party.options().rooms).isEmpty()
        system.failures["readAll"] = LightsRefusedException()
        assertThat(party.options().line).isEqualTo(HUE_REFUSED)
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
        party.on(listOf(kitchenBefore))
        system.failures["readAll"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        party.back(listOf(kitchenBefore))
        party.options()
        thrownBy { party.before(listOf(kitchenChoice)) }
        val lines = ShadowLog.getLogs().filter { it.tag == "LightsParty" }.map { it.msg }
        assertThat(lines).hasSize(4)
        lines.forEach { line -> listOf("Kitchen", "Lounge", "-id", "Relax").forEach { assertThat(line).doesNotContain(it) } }
    }
}
