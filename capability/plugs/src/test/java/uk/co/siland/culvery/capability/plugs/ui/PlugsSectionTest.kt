package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.FAN
import uk.co.siland.culvery.capability.plugs.LAMP
import uk.co.siland.culvery.capability.plugs.PlugActions
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.PlugsRig
import uk.co.siland.culvery.capability.plugs.TestAccess
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

/** 15a §4.5: the plugs under the Lights tab's rooms: their label, the link's line, the tiles and the PIN. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugsSectionTest {
    @get:Rule val compose = createComposeRule()

    private inner class Host(vararg states: PlugState, role: Role? = Role.ADULT) {
        val rig = PlugsRig(*states)
        val access = TestAccess(role)
        val scope = CoroutineScope(Job())
        val actions = PlugActions(access, rig.settings, rig.system, RecordingToaster(), scope)
        val section = PlugsSection(setOf(rig.system), actions, rig.settings)

        fun show() = compose.setContent {
            CulveryTheme(dark = true) { Box(Modifier.width(1116.dp)) { section.Content(Modifier) } }
        }
    }

    private fun left(id: String) = compose.onNodeWithTag("plug_$id").fetchSemanticsNode().boundsInRoot

    private val five = arrayOf(
        PlugState("switch.a", "A", PlugPower.ON, null), PlugState("switch.b", "B", PlugPower.OFF, null),
        PlugState("switch.c", "C", PlugPower.OFF, null), PlugState("switch.d", "D", PlugPower.OFF, null),
        PlugState("switch.e", "E", PlugPower.OFF, null),
    )

    /** Fails if five plugs aren't four then one, or the first row's tiles differ in width. */
    @Test
    fun fourAcross() {
        Host(*five).show()
        val tiles = listOf("a", "b", "c", "d", "e").map { left("switch.$it") }
        assertThat(tiles.take(4).map { it.top }.distinct()).hasSize(1)
        assertThat(tiles[4].top).isGreaterThan(tiles[0].bottom)
        assertThat(tiles[4].left).isEqualTo(tiles[0].left)
        tiles.take(4).forEach { assertThat(it.width).isWithin(1f).of(tiles[0].width) }
        assertThat(tiles[0].width).isGreaterThan(0f)
        assertThat(tiles[4].width).isWithin(1f).of(tiles[0].width)
        compose.onNodeWithText("Plugs · Home Assistant".uppercase(), ignoreCase = true).assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the link's line isn't under the label and above the tiles, or a line shows with no problem. */
    @Test
    fun theProblemLineShowsUnderTheLabel() {
        val host = Host(LAMP_ON, FAN_OFF)
        host.rig.system.problem.value = "Home Assistant can’t be reached."
        host.show()
        compose.waitFor { compose.onAllNodesWithTag("plugs_line").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("plugs_line").assertTextEquals("Home Assistant can’t be reached.")
        val label = compose.onNodeWithText("Plugs · Home Assistant", ignoreCase = true).fetchSemanticsNode().boundsInRoot
        val line = compose.onNodeWithTag("plugs_line").fetchSemanticsNode().boundsInRoot
        assertThat(line.top).isAtLeast(label.bottom)
        assertThat(line.bottom).isAtMost(left(LAMP).top)

        host.rig.system.problem.value = null
        compose.waitFor { compose.onAllNodesWithTag("plugs_line").fetchSemanticsNodes().isEmpty() }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a tap on a PIN plug doesn't ask the Plugs switch PIN before switching. */
    @Test
    fun aPinPlugAsksForThePin() {
        val host = Host(LAMP_ON, FAN_OFF)
        runBlocking { host.rig.configure { it.copy(needsPin = setOf(FAN)) } }
        host.show()
        compose.waitFor { compose.onAllNodesWithTag("plug_${FAN}_lock", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("plug_${FAN}_switch").performClick()
        compose.waitFor { host.rig.system.switches.isNotEmpty() }
        assertThat(host.access.asked).containsExactly(listOf("plugs.switch"))
        assertThat(host.rig.system.switches).containsExactly(FAN to true)
        compose.onAllNodesWithTag("plug_${LAMP}_lock", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun switchLabel(id: String) = compose.onNodeWithTag("plug_${id}_switch").fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    /** Fails if a tap on a plain plug's pill doesn't switch that plug once without asking for a PIN. */
    @Test
    fun aPlainPlugSwitchesOnTap() {
        val host = Host(LAMP_ON, FAN_OFF)
        host.show()
        compose.onNodeWithTag("plug_${FAN}_switch").performClick()
        compose.waitFor { host.rig.system.switches.isNotEmpty() }
        assertThat(host.rig.system.switches).containsExactly(FAN to true)
        assertThat(host.access.asked).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the PIN plug says Switching… while the pad is up, doesn't once the PIN is in, or a tap during it reaches the system again. */
    @Test
    fun switchingShowsOnlyAfterThePin() {
        val host = Host(FAN_OFF)
        runBlocking { host.rig.configure { it.copy(needsPin = setOf(FAN)) } }
        val pad = CompletableDeferred<Unit>().also { host.access.padGate = it }
        val gate = CompletableDeferred<Unit>().also { host.rig.system.switchGates[FAN] = it }
        host.show()
        compose.waitFor { count("plug_${FAN}_lock") > 0 }
        compose.onNodeWithTag("plug_${FAN}_switch").assertContentDescriptionEquals("Switch on, needs a PIN").performClick()
        compose.waitFor { host.access.asked.size == 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("plug_${FAN}_switch").assertContentDescriptionEquals("Switch on, needs a PIN")

        pad.complete(Unit)
        compose.waitFor { host.rig.system.switches.size == 1 }
        compose.waitFor { switchLabel(FAN) == "Switching…" }
        compose.onNodeWithText("Switching…", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("plug_${FAN}_switch").performClick()
        gate.complete(Unit)
        compose.waitFor { switchLabel(FAN) != "Switching…" }
        assertThat(host.rig.system.switches).containsExactly(FAN to true)
        assertThat(host.access.asked).hasSize(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a Child's PIN switches a plug that needs one, or the tile stops saying it needs a PIN. */
    @Test
    fun aChildPinIsRefused() {
        val host = Host(FAN_OFF, role = Role.CHILD)
        runBlocking { host.rig.configure { it.copy(needsPin = setOf(FAN)) } }
        host.show()
        compose.waitFor { count("plug_${FAN}_lock") > 0 }
        compose.onNodeWithTag("plug_${FAN}_switch").performClick()
        compose.waitFor { host.access.asked.size == 1 }
        // Let the tap's action run to its end, so no switch can't pass by being early.
        runBlocking { host.scope.coroutineContext.job.children.toList().forEach { it.join() } }
        compose.waitForIdle()
        assertThat(host.rig.system.switches).isEmpty()
        compose.onNodeWithTag("plug_${FAN}_switch").assertContentDescriptionEquals("Switch on, needs a PIN")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the summary isn't "{on} of {n} plugs on" over every plug shown, or isn't null once there are none. */
    @Test
    fun summaryCounts() = runBlocking {
        val host = Host(LAMP_ON, FAN_OFF)
        assertThat(host.section.summary.first()).isEqualTo("1 of 2 plugs on")
        assertThat(host.section.shown.first()).isTrue()

        host.rig.system.added.value = emptyList()
        assertThat(host.section.summary.first()).isNull()
        assertThat(host.section.shown.first()).isFalse()

        host.rig.system.added.value = null
        assertThat(host.section.shown.first()).isFalse()
    }

    /** Fails if watch() doesn't start the system's watching once, or the result doesn't stop it. */
    @Test
    fun watchStartsAndStops() {
        val host = Host(LAMP_ON)
        assertThat(host.rig.system.watchers).isEqualTo(0)
        val stop = host.section.watch()
        assertThat(host.rig.system.watchers).isEqualTo(1)
        stop()
        assertThat(host.rig.system.watchers).isEqualTo(0)
    }

    /** Fails if a build with no plug system shows anything, has plugs or starts anything. */
    @Test
    fun noSystemNoSection() = runBlocking {
        val rig = PlugsRig()
        val section = PlugsSection(emptySet(), PlugActions(TestAccess(Role.ADULT), rig.settings, null, RecordingToaster(), CoroutineScope(Job())), rig.settings)
        assertThat(section.shown.first()).isFalse()
        assertThat(section.summary.first()).isNull()
        section.watch()()
    }
}
