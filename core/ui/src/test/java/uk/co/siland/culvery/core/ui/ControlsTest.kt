package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) =
        compose.setContent { CulveryTheme(dark = true) { content() } }

    @Test
    fun aTakenSwatchIsDisabledAndIgnoresATap() {
        var taps = 0
        show { HhSwatch(Color(0xFF5B9BE0), chosen = false, taken = true, tag = "swatch") { taps++ } }
        compose.onNodeWithTag("swatch").assertIsNotEnabled().performClick()
        assertThat(taps).isEqualTo(0)
    }

    @Test
    fun aChosenSwatchIsSelectedAndAFreeOneTakesATap() {
        var taps = 0
        show {
            Row {
                HhSwatch(Color(0xFF4CB387), chosen = true, taken = false, tag = "chosen") {}
                HhSwatch(Color(0xFF5B9BE0), chosen = false, taken = false, tag = "free") { taps++ }
            }
        }
        compose.onNodeWithTag("chosen").assertIsSelected()
        compose.onNodeWithTag("free").assertIsNotSelected().assertIsEnabled().performClick()
        assertThat(taps).isEqualTo(1)
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

    @Test
    fun aTextFieldShowsItsPlaceholderOnlyWhileEmpty() {
        var value by mutableStateOf("")
        show { HhTextField(value, { value = it }, placeholder = "Town or city", tag = "field") }
        compose.onNodeWithText("Town or city").assertExists()
        value = "Ca"
        compose.onNodeWithText("Town or city").assertDoesNotExist()
    }

    @Test
    fun aConfirmCardsButtonsAreLargeButtonsThatStopWhileBusy() {
        var keeps = 0
        show {
            HhConfirmCard(
                tag = "confirm", keep = "Keep", keepTag = "keep", confirm = "Remove", confirmTag = "remove",
                busy = true, onKeep = { keeps++ }, onConfirm = {},
            ) { Text("Remove Sam?") }
        }
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
}
