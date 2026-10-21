package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.isReadAsOne

@RunWith(AndroidJUnit4::class)
class PickersTest {
    @get:Rule val compose = createComposeRule()

    private val set = mutableListOf<LocalTime>()
    private var cancelled = 0
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun showTime(initial: LocalTime) = compose.setContent {
        CulveryTheme(dark = true) {
            PickerLayer(onDismiss = { cancelled++ }) {
                TimePickerCard(initial, onSet = { set += it }, onCancel = { cancelled++ })
            }
        }
    }

    @Test
    fun thePickersButtonsAre60DpButtons() {
        showTime(LocalTime.of(9, 0))
        listOf("picker_cancel", "picker_set").forEach {
            compose.onNodeWithTag(it).assert(isButton).assertHeightIsEqualTo(60.dp)
        }
    }

    @Test
    fun hoursWrapWithoutChangingTheDayAndMinutesStepByQuarters() {
        assertThat(hourStep(23, up = true)).isEqualTo(0)
        assertThat(hourStep(0, up = false)).isEqualTo(23)
        assertThat(minuteStep(45, up = true)).isEqualTo(0)
        assertThat(minuteStep(0, up = false)).isEqualTo(45)
        // An off-quarter start moves to the next or previous quarter first.
        assertThat(minuteStep(37, up = true)).isEqualTo(45)
        assertThat(minuteStep(37, up = false)).isEqualTo(30)
    }

    @Test
    fun cancelAndTheScrimSetNothing() {
        showTime(LocalTime.of(9, 0))
        compose.onNodeWithTag("picker_cancel").performClick()
        // The scrim's top-left corner is clear of the centred card.
        compose.onNodeWithTag("picker_scrim").performTouchInput { click(Offset(10f, 10f)) }
        assertThat(cancelled).isEqualTo(2)
        assertThat(set).isEmpty()
    }

    @Test
    fun theTimeStepsWrapAndSetTime() {
        showTime(LocalTime.of(23, 45))
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_value").assertTextEquals("00")
        compose.onNodeWithTag("minute_up").performClick()
        compose.onNodeWithTag("minute_value").assertTextEquals("00")
        compose.onNodeWithTag("hour_value").assertTextEquals("00")
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(set).containsExactly(LocalTime.of(0, 0))
    }

    @Test
    fun anOffQuarterTimeShowsAsItIsThenStepsToAQuarter() {
        showTime(LocalTime.of(19, 37))
        compose.onNodeWithTag("minute_value").assertTextEquals("37")
        compose.onNodeWithTag("minute_down").performClick()
        compose.onNodeWithTag("minute_value").assertTextEquals("30")
        compose.onNodeWithTag("hour_down").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(set).containsExactly(LocalTime.of(18, 30))
    }

    @Test
    fun cancelSetsNoTime() {
        showTime(LocalTime.of(16, 15))
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("picker_cancel").performClick()
        assertThat(set).isEmpty()
        assertThat(cancelled).isEqualTo(1)
    }

    @Test
    fun theScrimIsSilentAndEveryControlIsNamed() {
        showTime(LocalTime.of(9, 0))
        compose.onNodeWithTag("picker_scrim").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.assertAccessible()
    }

    @Test
    fun theStepButtonsSayWhichValueAndWhichWay() {
        showTime(LocalTime.of(9, 0))
        listOf("Hour up", "Hour down", "Minute up", "Minute down").forEach { compose.onNodeWithContentDescription(it).assert(isButton) }
        compose.assertAccessible()
    }

    /** TalkBack heard "Minute 0", "Hour 14" as the picker opened, a "Colon" stop, and both up buttons before either value. */
    @Test
    fun theTimePickerReadsEachColumnInTurnAndSpeaksAValueOnlyOnceItChanges() {
        showTime(LocalTime.of(9, 0))
        compose.onNodeWithText(":", useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        listOf("hour", "minute").forEach { column ->
            compose.onNodeWithTag("${column}_value").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
                .onParent().assert(isReadAsOne)
        }
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_value").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("minute_value").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun theTimePickersTitleIsAHeading() {
        showTime(LocalTime.of(9, 0))
        compose.onNodeWithText("Pick a time").assert(isHeading())
    }

    @Test
    fun theTimePickerIsAPane() {
        showTime(LocalTime.of(9, 0))
        compose.onNodeWithTag("time_picker").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Pick a time"))
    }

    @Test
    fun eachStepperValueIsSpokenWithItsNameAndNoLeadingZero() {
        showTime(LocalTime.of(7, 0))
        compose.onNodeWithTag("hour_value")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hour 7")))
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_value").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hour 8")))
        compose.onNodeWithTag("minute_value").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Minute 0")))
    }
}
