package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.core.household.FAMILY_COLOR
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** Handoff 6b in the editor on the 1280 × 800 dp canvas, at 11:54 on Wednesday 23 September, in the dark theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhoPaneTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val c = DarkColors
    private val alex = SampleUi.alex
    private val sam = SampleUi.sam
    private val mia = SampleUi.mia
    private val refusals = mutableListOf<Unit>()
    private var busy by mutableStateOf(false)
    private lateinit var form: EditorForm
    private lateinit var flow: EditorFlow

    private fun show(editor: EditorForm = editorForm(), child: PersonId? = null) {
        form = editor
        flow = editorFlow(editor, child, EditorStep.Who)
        compose.setContent {
            CulveryTheme(dark = true) {
                key(form) {
                    EventEditorScreen(
                        form = form,
                        flow = flow,
                        members = SampleUi.members,
                        context = "From Calendar · Alex signed in",
                        savesTo = "Family calendar",
                        service = "Google Calendar",
                        busy = busy,
                        failure = null,
                        picker = EditorPicker.None,
                        childOnly = child,
                        childName = child?.let { id -> SampleUi.household.first { it.id == id }.name },
                        held = null,
                        onClose = {},
                        onSave = {},
                        onDelete = {},
                        onRefusedWho = { refusals += Unit },
                        onPicker = {},
                        keyboard = WindowInsets(bottom = 0.dp),
                    )
                }
            }
        }
    }

    /** Mia, signed in, changing her Piano that Alex retagged to Sam + Mia. */
    private fun miasSharedPiano(): EditorForm {
        val start = LocalDateTime.of(2026, 9, 26, 15, 30).atZone(LONDON).toInstant()
        return editorEditOf(EventTime.Timed(start), EventTime.Timed(start.plusSeconds(45 * 60)), "Piano", "sam,mia", childOnly = mia.id)
    }

    private fun tile(p: Person) = compose.onNodeWithTag("who_${p.name}")

    private fun part(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun bounds(tag: String): DpRect = part(tag).getUnclippedBoundsInRoot()

    /** A tile's fill, read left of its circle and below its corner marks; Everyone's, left of its circle. */
    private fun fillOf(tag: String): Int = pixelAt(compose.onNodeWithTag(tag), if (tag == "who_everyone") 8.dp else 12.dp, if (tag == "who_everyone") 34.dp else 50.dp)

    private fun tinted(color: Long): Int = Color(color).copy(alpha = 0x33 / 255f).compositeOver(c.surf2).toArgb()

    private fun assertClose(what: String, actual: Int, expected: Int) {
        val channels = listOf(16, 8, 0).map { shift -> (actual shr shift and 0xFF) - (expected shr shift and 0xFF) }
        assertWithMessage("$what: #%08X vs #%08X".format(actual, expected)).that(channels.all { it in -2..2 }).isTrue()
    }

    private fun circleColour(tag: String): Int = pixelAt(part("${tag}_dot"), 5.dp, bounds("${tag}_dot").let { (it.bottom - it.top) / 2 })

    @Test
    fun tilesAreThreeAcrossAsDrawn() {
        show()
        val a = bounds("who_Alex")
        val s = bounds("who_Sam")
        val m = bounds("who_Mia")
        listOf(a, s, m).forEach { assertThat((it.bottom - it.top).value).isWithin(0.5f).of(160f) }
        assertThat(s.top).isEqualTo(a.top)
        assertThat(m.top).isEqualTo(a.top)
        assertThat((s.left - a.right).value).isWithin(1f).of(13f)
        assertThat((m.left - s.right).value).isWithin(1f).of(13f)
        assertThat((s.right - s.left).value).isWithin(1f).of((a.right - a.left).value)
        listOf(alex to "Admin", sam to "Adult", mia to "Child").forEach { (p, role) ->
            val circle = bounds("who_${p.name}_dot")
            assertThat((circle.right - circle.left).value).isWithin(0.5f).of(64f)
            assertThat((circle.bottom - circle.top).value).isWithin(0.5f).of(64f)
            assertClose("${p.name}'s circle", circleColour("who_${p.name}"), Color(p.color).toArgb())
            tile(p).assertTextEquals(p.name, role)
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aTileTogglesAndSeveralCanBeChosen() {
        show()
        tile(sam).performClick()
        tile(mia).performClick()
        compose.runOnIdle { assertThat(form.who).containsExactly(sam.id, mia.id).inOrder() }
        listOf(sam, mia).forEach { p ->
            tile(p).assertIsToggleable().assertIsOn().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "chosen"))
            assertClose("${p.name}'s fill", fillOf("who_${p.name}"), tinted(p.color))
            assertClose("${p.name}'s ring", pixelAt(tile(p), 1.dp, 80.dp), Color(p.color).toArgb())
            val badge = bounds("who_${p.name}_check")
            assertThat((badge.right - badge.left).value).isWithin(0.5f).of(32f)
            assertClose("${p.name}'s badge", pixelAt(part("who_${p.name}_check"), 2.dp, 16.dp), Color(p.color).toArgb())
        }
        tile(alex).assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not chosen"))
        assertThat(fillOf("who_Alex")).isEqualTo(c.surf2.toArgb())
        part("who_Alex_check").assertDoesNotExist()
        compose.onNodeWithTag("editor_row_who_value", useUnmergedTree = true).assertTextEquals("Sam + Mia")
        tile(sam).performClick()
        compose.runOnIdle { assertThat(form.who).containsExactly(mia.id) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun everyoneClearsThePicks() {
        show()
        tile(sam).performClick()
        tile(mia).performClick()
        compose.onNodeWithTag("who_everyone").performClick()
        compose.runOnIdle { assertThat(form.who).containsExactly(PersonId.FAMILY) }
        compose.onNodeWithTag("who_everyone").assertIsOn()
        assertClose("Everyone's fill", fillOf("who_everyone"), tinted(FAMILY_COLOR))
        listOf(alex, sam, mia).forEach { tile(it).assertIsOff() }
        compose.onNodeWithTag("editor_row_who_value", useUnmergedTree = true).assertTextEquals("Everyone")
        compose.onNodeWithTag("who_everyone").assertTextEquals("Everyone", "Shows as a Family event")
        tile(sam).performClick()
        compose.runOnIdle { assertThat(form.who).containsExactly(sam.id) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Handoff Behaviour: you can pick more than one, so Who never moves on by itself. */
    @Test
    fun whoDoesNotMoveOnByItself() {
        show()
        compose.mainClock.autoAdvance = false
        tile(sam).performClick()
        compose.mainClock.advanceTimeBy(500)
        assertThat(flow.active).isEqualTo(EditorStep.Who)
        compose.mainClock.autoAdvance = true
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theHintSaysYouCanPickMore() {
        show()
        compose.onNodeWithTag("editor_who_hint").assertTextEquals("Pick more than one person for a shared event.")
        compose.onNodeWithTag("editor_who_only").assertDoesNotExist()
        part("who_Sam_lock").assertDoesNotExist()
        part("who_everyone_lock").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aChildsOtherTilesAndEveryoneAreLocked() {
        show(editorForm(childOnly = mia.id), child = mia.id)
        listOf("Alex" to alex.color, "Sam" to sam.color, "everyone" to FAMILY_COLOR).forEach { (name, colour) ->
            val tag = "who_$name"
            compose.onNodeWithTag(tag).assertIsOff()
            assertWithMessage("$name has no fill").that(fillOf(tag)).isEqualTo(c.surf.toArgb())
            part("${tag}_lock").assertExists()
            assertClose("$name's dot", circleColour(tag), Color(colour).toArgb())
        }
        tile(mia).assertIsOn().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "chosen, not available"))
        part("who_Mia_lock").assertExists()
        compose.onNodeWithTag("editor_who_only").assertTextEquals("Mia can add events for Mia only.")
        compose.onNodeWithTag("editor_who_hint").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aTapOnALockedTileExplainsAndChangesNothing() {
        show(editorForm(childOnly = mia.id), child = mia.id)
        tile(alex).performClick()
        compose.runOnIdle {
            assertThat(refusals).hasSize(1)
            assertThat(form.who).containsExactly(mia.id)
        }
        compose.onNodeWithTag("who_everyone").performClick()
        compose.runOnIdle {
            assertThat(refusals).hasSize(2)
            assertThat(form.who).containsExactly(mia.id)
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** A child is only ever themselves: unticking would leave nobody, which Save can't take from them either. */
    @Test
    fun aTapOnAChildsOwnTileChangesNothing() {
        show(editorForm(childOnly = mia.id), child = mia.id)
        tile(mia).performClick()
        compose.runOnIdle {
            assertThat(refusals).isEmpty()
            assertThat(form.who).containsExactly(mia.id)
        }
        tile(mia).assertIsOn()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aChosenLockedTileStaysChosen() {
        show(miasSharedPiano(), child = mia.id)
        tile(sam).assertIsOn().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "chosen, not available"))
        part("who_Sam_lock").assertExists()
        tile(sam).performClick()
        compose.runOnIdle {
            assertThat(refusals).isEmpty()
            assertThat(form.who).containsExactly(sam.id, mia.id).inOrder()
        }
        tile(sam).assertIsOn()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Unticking herself would leave [sam], a set Save refuses a child (9 §3.3). */
    @Test
    fun aChildsOwnTileInASharedEditIsLocked() {
        show(miasSharedPiano(), child = mia.id)
        tile(mia).assertIsOn()
        part("who_Mia_lock").assertExists()
        tile(mia).performClick()
        compose.runOnIdle {
            assertThat(refusals).isEmpty()
            assertThat(form.who).containsExactly(sam.id, mia.id).inOrder()
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin of the sheet's rule: a save in flight fades no tile, calls none unavailable and takes no tap. */
    @Test
    fun busyFadesNoTile() {
        show()
        val tags = listOf("who_Alex", "who_Sam", "who_Mia", "who_everyone")
        val idle = tags.map { fillOf(it) }
        busy = true
        compose.waitForIdle()
        tags.forEachIndexed { i, tag ->
            assertThat(fillOf(tag)).isEqualTo(idle[i])
            compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not chosen"))
        }
        tile(sam).performClick()
        compose.runOnIdle { assertThat(form.who).isEmpty() }
        compose.assertClickablesHaveRoleAndLabel()
    }
}
