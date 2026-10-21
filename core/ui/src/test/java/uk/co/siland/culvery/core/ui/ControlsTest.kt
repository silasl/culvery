package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
class ControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) =
        compose.setContent { CulveryTheme(dark = true) { content() } }

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun aTakenSwatchIsNamedDisabledAndSaysWhoHasIt() {
        var taps = 0
        show { HhSwatch(Color(0xFF5B9BE0), "Blue", chosen = false, takenBy = "Sam", tag = "swatch") { taps++ } }
        compose.onNodeWithTag("swatch")
            .assert(radio)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Blue")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "used by Sam"))
            .assertIsNotEnabled()
            .performClick()
        assertThat(taps).isEqualTo(0)
    }

    @Test
    fun theSwatchesAreARadioGroupWithTheChosenOneSelected() {
        var picked: Long? = null
        show { HhSwatchGrid(PersonPalette.colors[0], taken = mapOf(PersonPalette.colors[2] to "Mia"), tagPrefix = "swatch_") { picked = it } }
        compose.onNodeWithTag("swatch_0").assert(radio).assertIsSelected()
        compose.onNodeWithTag("swatch_0").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        compose.onNodeWithTag("swatch_1").assertIsNotSelected().assertIsEnabled().performClick()
        assertThat(picked).isEqualTo(PersonPalette.colors[1])
        compose.assertAccessible()
    }

    @Test
    fun aChoiceChipIsARadioButtonAndADisabledOneStillExplainsItself() {
        var taps = 0
        show {
            Row {
                HhChoiceChip("Mia", selected = false, tag = "mia", onClick = { taps++ }, enabled = false)
                HhChoiceChip("Pick date…", selected = false, tag = "pick", onClick = {}, role = Role.Button)
            }
        }
        val mia = compose.onNodeWithTag("mia")
            .assert(radio)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
            .assertIsEnabled()
        assertThat(mia.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("find out why")
        mia.performClick()
        assertThat(taps).isEqualTo(1)
        compose.onNodeWithTag("pick").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun aChoiceChipReportsWhetherItIsSelected() {
        show {
            Row {
                HhChoiceChip("Admin", selected = true, tag = "admin", onClick = {})
                HhChoiceChip("Adult", selected = false, tag = "adult", onClick = {})
            }
        }
        compose.onNodeWithTag("admin").assertIsSelected()
        compose.onNodeWithTag("adult").assertIsNotSelected()
    }

    /** Fails if [spokenName] doesn't replace the chip's label for TalkBack, or costs the chip its selection. */
    @Test
    fun aChipCanBeSpokenOtherThanItsLabel() {
        show { HhChoiceChip("Relax", selected = true, tag = "c", onClick = {}, spokenName = "Relax scene in the Lounge") }
        compose.onNodeWithTag("c")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Relax scene in the Lounge")))
            .assertIsSelected()
    }

    /** Fails if a chip in a row with no width to share (a LazyRow) draws its label at zero width. */
    @Test
    fun aChipInASideScrollingRowShowsItsLabel() {
        show {
            LazyRow {
                item { HhChoiceChip("Relax", selected = false, tag = "c", onClick = {}, trailing = { HhIcon(Icons.EXPAND_MORE, size = 20.dp) }) }
            }
        }
        val label = compose.onNodeWithText("Relax", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val chip = compose.onNodeWithTag("c").getUnclippedBoundsInRoot()
        assertThat((label.right - label.left).value).isGreaterThan(0f)
        assertThat(compose.onNodeWithText("Relax", useUnmergedTree = true).textLayout().lineCount).isEqualTo(1)
        assertThat(chip.right.value - label.right.value).isAtLeast(20f)
    }

    /** Fails if the trailing slot isn't given the chip's ink, unselected and selected. */
    @Test
    fun aChipsTrailingIsGivenItsInk() {
        val inks = mutableListOf<Color>()
        show {
            HhChoiceChip("2 rooms", selected = false, tag = "a", onClick = {}, unselectedInk = Color.Blue, trailing = { inks += it })
            HhChoiceChip("3 rooms", selected = true, tag = "b", onClick = {}, selectedInk = Color.Red, trailing = { inks += it })
        }
        compose.waitForIdle()
        assertThat(inks.distinct()).containsExactly(Color.Blue, Color.Red)
    }

    @Test
    fun aSelectedChipsSecondaryTextIsAtFullStrength() {
        show { HhChoiceChip("Morning", selected = true, tag = "slot", onClick = {}, secondary = "09:00") }
        val colour = compose.onNodeWithText("09:00", useUnmergedTree = true).textLayout().layoutInput.style.color
        assertThat(colour).isEqualTo(DarkColors.accentInk)
    }

    @Test
    fun aTextFieldShowsItsPlaceholderOnlyWhileEmpty() {
        var value by mutableStateOf("")
        show { HhTextField(value, { value = it }, placeholder = "Town or city", label = "Town or city", tag = "field") }
        compose.onNodeWithText("Town or city").assertExists()
        value = "Ca"
        compose.onNodeWithText("Town or city").assertDoesNotExist()
    }

    @Test
    fun aToastIsPoliteAndAnUrgentOneIsAssertive() {
        show {
            Row {
                HhToast("Event added.", Icons.INFO, Modifier.testTag("plain"))
                HhToast("Couldn’t save — try again.", Icons.INFO, Modifier.testTag("urgent"), urgent = true)
            }
        }
        compose.onNodeWithTag("plain").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("urgent").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }

    @Test
    fun aFilledTextFieldKeepsItsNameAndCarriesItsError() {
        show { HhTextField("Alex", {}, placeholder = "Name", label = "Name", tag = "field", error = "Someone is already called Alex.") }
        compose.onNodeWithTag("field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Name")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Someone is already called Alex."))
    }

    /** Compose speaks a live region only when a node it already has changes, so the line must be there while empty. */
    @Test
    fun aMessageLineIsInTheTreeAndOneLineTallBeforeItHasAMessage() {
        var message by mutableStateOf<String?>(null)
        show { HhMessageLine(message, ControlType.chip, Color.Red, "line", spoken = message?.let { "said: $it" }) }
        val empty = compose.onNodeWithTag("line")
            .assertTextEquals("")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertThat(empty.fetchSemanticsNode().size.height).isGreaterThan(0)
        message = "Couldn’t save — try again."
        compose.onNodeWithTag("line")
            .assertTextEquals("Couldn’t save — try again.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("said: Couldn’t save — try again.")))
    }

    /** The same words as a field's error: TalkBack says the error as it appears, so the line stays quiet. */
    @Test
    fun aMessageLineShowingItsFieldsErrorHasNoLiveRegion() {
        show {
            HhMessageLine(
                "Someone is already called Alex.", ControlType.chip, Color.Red, "line",
                fieldError = "Someone is already called Alex.",
            )
        }
        compose.onNodeWithTag("line")
            .assertTextEquals("Someone is already called Alex.")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun aConfirmCardsButtonsAreLargeButtonsThatStopWhileBusy() {
        var keeps = 0
        show {
            HhConfirmCard(
                tag = "confirm", title = "Remove Sam?", keep = "Keep", keepTag = "keep", confirm = "Remove", confirmTag = "remove",
                busy = true, onKeep = { keeps++ }, onConfirm = {},
            ) { Text("Remove Sam?") }
        }
        compose.onNodeWithTag("confirm").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Remove Sam?"))
        listOf("keep", "remove").forEach {
            compose.onNodeWithTag(it)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHeightIsEqualTo(60.dp)
                .assertIsNotEnabled()
        }
        compose.onNodeWithTag("keep").performClick()
        assertThat(keeps).isEqualTo(0)
    }

    @Test
    fun singleActionRunsOneActionAtATimeAndReportsFailures() = runTest {
        val errors = mutableListOf<Exception>()
        val action = SingleAction(backgroundScope) { errors += it }
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        action.run { runs++; gate.await() }
        action.run { runs++ }
        testScheduler.runCurrent()
        assertThat(action.busy).isTrue()
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertThat(runs).isEqualTo(1)
        assertThat(action.busy).isFalse()
        action.run { throw IllegalStateException("boom") }
        testScheduler.runCurrent()
        assertThat(errors.single()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun aPersonChipSaysWhatATapDoes() {
        show { HhPersonChip("Sam", Color(0xFF5B9BE0), "assign_Sam", enabled = true, clickLabel = "show as Sam’s event") {} }
        val node = compose.onNodeWithTag("assign_Sam").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertThat(node.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("show as Sam’s event")
    }

    @Test
    fun aPersonChipCanBeAToggle() {
        var taps = 0
        var selected: Boolean? by mutableStateOf(true)
        show { HhPersonChip("Sam", Color(0xFF5B9BE0), "assign_Sam", enabled = true, selected = selected) { taps++ } }
        compose.onNodeWithTag("assign_Sam")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "chosen"))
            .performClick()
        assertThat(taps).isEqualTo(1)
        selected = false
        compose.onNodeWithTag("assign_Sam")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not chosen"))
        selected = null
        compose.onNodeWithTag("assign_Sam")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        compose.assertClickablesHaveRoleAndLabel()
    }
}
