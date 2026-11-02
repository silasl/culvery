package uk.co.siland.culvery.core.house

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.robolectric.shadows.ShadowLog
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

/** 12 §5.2: what a mode's plugs answer becomes the tile's line, and a throw never escapes. */
@RunWith(AndroidJUnit4::class)
class ModePlugsTest {
    private class Plugs : HousePlugs {
        val calls = mutableListOf<String>()
        var entered: HouseStep? = HouseStep.Done
        var left: HouseStep = HouseStep.Done
        var throws = false

        override fun ready(modeId: String): Flow<Boolean> = flowOf(modeId == HouseModes.PARTY)

        override suspend fun preview(modeId: String) = listOf(PlugChange("Lamp", true))

        override suspend fun begin(modeId: String, grant: ModeGrant) = step("begin $modeId") { }

        override suspend fun enter(modeId: String): HouseStep? = step("enter $modeId") { entered }

        override suspend fun leave(modeId: String): HouseStep = step("leave $modeId") { left }

        override suspend fun options() = emptyList<PlugOption>()

        override fun added(modeId: String): Flow<List<AddedPlug>> = flowOf(emptyList())

        override suspend fun choose(modeId: String, plugId: String, on: Boolean?) = Unit

        override suspend fun forget(modeId: String) = Unit

        override suspend fun holdsPinPlug(modeId: String) = false

        private fun <T> step(call: String, answer: () -> T): T {
            calls += call
            if (throws) throw IOException("unreachable")
            return answer()
        }
    }

    private val plugs = Plugs()
    private val party = ModePlugs(plugs, HouseModes.PARTY, "a stored mode")
    private val by = ModeGrant.Person(Authorised(Person(PersonId("p"), "P", 0), Role.ADULT, setOf(HousePermissions.STORED_MODE)))

    /** Fails if the mode's id isn't what reaches the plugs. */
    @Test
    fun itAsksForItsOwnMode() = runTest {
        assertThat(party.ready()).isTrue()
        assertThat(ModePlugs(plugs, HouseModes.HOLIDAY, "Holiday").ready()).isFalse()
        party.begin(by)
        party.enter()
        party.leave()
        assertThat(plugs.calls).containsExactly("begin party", "enter party", "leave party").inOrder()
    }

    /** Fails if no snapshot isn't null, a failure loses its line, or a throw isn't the retry line. */
    @Test
    fun enterAnswersWhatToShow() = runTest {
        plugs.entered = null
        assertThat(party.enter()).isNull()
        plugs.entered = HouseStep.Failed("Lamp didn’t switch.")
        assertThat(party.enter()).isEqualTo(HouseStep.Failed("Lamp didn’t switch."))
        plugs.throws = true
        assertThat(party.enter()).isEqualTo(HouseStep.Failed(HouseLines.PLUGS_THREW))
    }

    /** Fails if a plug put back shows a line, a failure loses its own, or a throw escapes or shows the wrong one. */
    @Test
    fun leaveAnswersTheLine() = runTest {
        assertThat(party.leave()).isNull()
        plugs.left = HouseStep.Failed("Lamp didn’t go back.")
        assertThat(party.leave()).isEqualTo("Lamp didn’t go back.")
        plugs.throws = true
        assertThat(party.leave()).isEqualTo(HouseLines.PLUGS_LEAVE_THREW)
        party.begin(by)
        party.leaveQuietly()
    }

    /** 15b §5: fails if a plugs line logs a stored mode's id instead of "a stored mode". */
    @Test
    fun logsNameNoMode() = runTest {
        ShadowLog.clear()
        val stored = ModePlugs(plugs, "modeabcdef", "a stored mode")
        plugs.throws = true
        stored.begin(by)
        stored.enter()
        stored.leave()

        val logs = ShadowLog.getLogs().filter { it.tag == "ModePlugs" }.map { it.msg }
        assertThat(logs).hasSize(4)
        for (line in logs) {
            assertThat(line).contains("a stored mode")
            assertThat(line).doesNotContain("modeabcdef")
        }
    }
}
