package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        compose.onNodeWithTag("swatch_block", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("swatch_check", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aChosenSwatchShowsATickAndNoBlock() {
        show { HhSwatch(Color(0xFF5B9BE0), "Blue", chosen = true, takenBy = null, tag = "swatch") {} }
        compose.onNodeWithTag("swatch", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("swatch_check", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("swatch_block", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aRadioRowIsOneRadioNodeAndALockedOneDoesNotSelect() {
        var taps = 0
        var selected by mutableStateOf(false)
        show {
            Column {
                HhRadioRow("Adult", "Can add and change any event", selected = selected, tag = "row") { taps++ }
                HhRadioRow("Weekly", "Too long", selected = true, tag = "locked", locked = true) { taps++ }
            }
        }
        compose.onNodeWithTag("row").assert(radio).assertIsNotSelected().assertTextEquals("Adult", "Can add and change any event")
        selected = true
        compose.onNodeWithTag("row").assertIsSelected()
        compose.onNodeWithTag("row").performClick()
        assertThat(taps).isEqualTo(1)
        compose.onNodeWithTag("locked").assert(radio).assertIsSelected().assertIsNotEnabled().performClick()
        assertThat(taps).isEqualTo(1)
        compose.onNodeWithTag("row").assertHeightIsEqualTo(RadioRowTokens.minHeight)
        compose.assertClickablesHaveRoleAndLabel()
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

    /** Pin for the default; the second field fails without `clearsFocusOnDone`. */
    @Test
    fun aFieldCanKeepFocusAfterDone() {
        var keptDone = 0
        var clearedDone = 0
        show {
            Column {
                HhTextField("", {}, placeholder = "Kept", label = "Kept", tag = "kept", onDone = { keptDone++ }, clearsFocusOnDone = false)
                HhTextField("", {}, placeholder = "Cleared", label = "Cleared", tag = "cleared", onDone = { clearedDone++ })
            }
        }
        compose.onNodeWithTag("kept").performClick().performImeAction()
        compose.onNodeWithTag("kept").assertIsFocused()
        assertThat(keptDone).isEqualTo(1)
        compose.onNodeWithTag("cleared").performClick().performImeAction()
        compose.onNodeWithTag("cleared").assertIsNotFocused()
        assertThat(clearedDone).isEqualTo(1)
    }

    /** Fails without `onLongClick`; the second chip is the pin of today's chip, which has no long press. */
    @Test
    fun aChipCanBeLongPressed() {
        var taps = 0
        var holds = 0
        show {
            Row {
                HhChoiceChip("Eggs", selected = false, tag = "held", onClick = { taps++ }, role = Role.Button, onLongClick = { holds++ }, longClickLabel = "Forget Eggs")
                HhChoiceChip("Tea", selected = false, tag = "plain", onClick = {}, role = Role.Button)
            }
        }
        compose.onNodeWithTag("held").performTouchInput { longClick() }
        assertThat(holds).isEqualTo(1)
        assertThat(taps).isEqualTo(0)
        assertThat(compose.onNodeWithTag("held").fetchSemanticsNode().config[SemanticsActions.OnLongClick].label).isEqualTo("Forget Eggs")
        compose.onNodeWithTag("plain").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }

    /** Pin: a long label takes two lines by default; it takes one, ending in …, when asked. */
    @Test
    fun aChipCanBeKeptToOneLine() {
        show {
            Column {
                HhChoiceChip("W".repeat(40), selected = false, tag = "two", onClick = {}, role = Role.Button, modifier = Modifier.width(160.dp))
                HhChoiceChip("W".repeat(40), selected = false, tag = "one", onClick = {}, role = Role.Button, maxLines = 1, modifier = Modifier.width(160.dp))
            }
        }
        fun lines(tag: String): Int {
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
            return results.first().lineCount
        }
        assertThat(lines("two")).isEqualTo(2)
        assertThat(lines("one")).isEqualTo(1)
    }

    /** Fails if a field can't be told apart from the `surf` card it sits on. */
    @Test
    fun aTextFieldCanBeFilledOtherThanSurf() {
        lateinit var colors: HhColors
        show {
            colors = Culvery.colors
            Column {
                HhTextField("", {}, placeholder = "Add", label = "Add", tag = "plain")
                Spacer(Modifier.height(16.dp))
                HhTextField("", {}, placeholder = "Add", label = "Add", tag = "filled", fill = Culvery.colors.surf2)
            }
        }
        assertThat(pixelAt(compose.onNodeWithTag("plain"), 4.dp, 32.dp)).isEqualTo(colors.surf.toArgb())
        assertThat(pixelAt(compose.onNodeWithTag("filled"), 4.dp, 32.dp)).isEqualTo(colors.surf2.toArgb())
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

    /** pin: a toast with no action is the one merged, tagged row it was, with no button. */
    @Test
    fun aToastWithoutAnActionIsUnchanged() {
        show { HhToast("Event added.", Icons.INFO) }
        compose.onNodeWithTag("toast")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextEquals("Event added.")
        compose.onNodeWithTag("toast_action").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aToastsActionIsItsOwnButtonBesideTheMergedMessage() {
        var taps = 0
        show { HhToast("Added Piano.", Icons.INFO, actionLabel = "Undo", onAction = { taps++ }) }
        compose.onNodeWithTag("toast_action")
            .assertTextEquals("Undo")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        assertThat(taps).isEqualTo(1)
        compose.onNodeWithText("Added Piano.").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aFilledTextFieldKeepsItsNameAndCarriesItsError() {
        show { HhTextField("Alex", {}, placeholder = "Name", label = "Name", tag = "field", error = "Someone is already called Alex.") }
        compose.onNodeWithTag("field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Name")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Someone is already called Alex."))
    }

    @Test
    fun anErrorRingsTheFieldAndSitsUnderIt() {
        var error by mutableStateOf<String?>("Someone is already called Sam. Try “Sam B” or a nickname.")
        show { HhTextField("Sam", {}, placeholder = "Name", label = "Name", tag = "x", error = error) }
        val field = compose.onNodeWithTag("x")
        assertThat(pixelAt(field, 1.dp, 30.dp)).isEqualTo(DarkColors.danger.toArgb())
        val gap = compose.onNodeWithTag("x_error").assertIsDisplayed().getUnclippedBoundsInRoot().top -
            field.getUnclippedBoundsInRoot().bottom
        assertThat(gap.value).isWithin(0.5f).of(8f)
        error = null
        compose.onNodeWithTag("x_error").assertDoesNotExist()
        assertThat(pixelAt(field, 1.dp, 30.dp)).isNotEqualTo(DarkColors.danger.toArgb())
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anErrorScrollsItsFieldIntoView() {
        var error by mutableStateOf<String?>(null)
        show {
            Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(900.dp))
                HhTextField("Sam", {}, placeholder = "Name", label = "Name", tag = "x", error = error)
                Spacer(Modifier.height(900.dp))
            }
        }
        compose.onNodeWithTag("x").assertIsNotDisplayed()
        error = "Someone is already called Sam. Try “Sam B” or a nickname."
        compose.waitForIdle()
        compose.onNodeWithTag("x").assertIsDisplayed()
        compose.onNodeWithTag("x_error").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theErrorIsSaidOnce() {
        val sentence = "Someone is already called Sam. Try “Sam B” or a nickname."
        show { HhTextField("Sam", {}, placeholder = "Name", label = "Name", tag = "x", error = sentence) }
        compose.onNodeWithTag("x").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, sentence))
        compose.onNodeWithTag("x_error")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        val saysIt = SemanticsMatcher("says the sentence to TalkBack") { node ->
            val config = node.config
            val said = config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.contains(sentence) } ||
                config.getOrNull(SemanticsProperties.Error)?.contains(sentence) == true
            said && !config.contains(SemanticsProperties.HideFromAccessibility)
        }
        compose.onAllNodes(saysIt).assertCountEquals(1)
        compose.onNodeWithTag("x").assert(saysIt)
        compose.assertClickablesHaveRoleAndLabel()
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
    fun aPersonChipCanBeAToggle() {
        var taps = 0
        var selected by mutableStateOf(true)
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
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aBusyPersonChipSaysSaving() {
        val dot = Color(0xFF5B9BE0)
        var busy by mutableStateOf(false)
        show { HhPersonChip("Sam", dot, "assign_Sam", enabled = !busy, selected = true, busy = busy) {} }
        // Where the dot is: 18 dp of padding and half its 12 dp.
        assertThat(pixelAt(compose.onNodeWithTag("assign_Sam"), 24.dp, 24.dp)).isEqualTo(dot.toArgb())
        busy = true
        compose.waitForIdle()
        compose.onNodeWithTag("assign_Sam")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Saving"))
            .assertIsNotEnabled()
        val inked = (18..30).flatMap { x -> (18..30).map { y -> pixelAt(compose.onNodeWithTag("assign_Sam"), x.dp, y.dp) } }
        assertThat(inked).contains(DarkColors.accentInk.toArgb())
        assertThat(inked).doesNotContain(dot.toArgb())
    }
}
