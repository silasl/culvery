package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.ClockField
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.isReadAsOne

@RunWith(AndroidJUnit4::class)
class TimeSteppersTest {
    @get:Rule val compose = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val stepped = mutableListOf<Pair<ClockField, Boolean>>()
    private var time by mutableStateOf(LocalTime.of(9, 0))

    private fun show(
        initial: LocalTime,
        prefix: String = "",
        enabled: (ClockField, Boolean) -> Boolean = { _, _ -> true },
    ) {
        time = initial
        compose.setContent {
            CulveryTheme(dark = true) {
                TimeSteppers(
                    time, prefix,
                    onStep = { field, up ->
                        stepped += field to up
                        time = when (field) {
                            ClockField.Hour -> time.withHour(hourStep(time.hour, up))
                            ClockField.Minute -> time.withMinute(minuteStep(time.minute, up))
                        }
                    },
                    enabled = enabled,
                )
            }
        }
    }

    @Test
    fun aDisabledStepButtonIsNotClickableAndSaysSo() {
        show(LocalTime.of(9, 0), enabled = { field, up -> !(field == ClockField.Hour && up) })
        compose.onNodeWithTag("hour_up").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
        compose.onNodeWithTag("hour_up").performClick()
        assertThat(stepped).isEmpty()
        compose.onNodeWithTag("hour_down").assertIsEnabled().performClick()
        assertThat(stepped).containsExactly(ClockField.Hour to false)
    }

    @Test
    fun aPrefixTagsEveryPartAndLeavesTheOthersAlone() {
        show(LocalTime.of(9, 30), prefix = "end_")
        listOf("hour_up", "hour_down", "hour_value", "minute_up", "minute_down", "minute_value").forEach {
            compose.onNodeWithTag("end_$it").assertExists()
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        compose.onNodeWithTag("end_minute_up").performClick()
        compose.onNodeWithTag("end_minute_value").assertTextEquals("45")
        assertThat(stepped).containsExactly(ClockField.Minute to true)
    }

    @Test
    fun theStepButtonsSayWhichValueAndWhichWay() {
        show(LocalTime.of(9, 0))
        listOf("Hour up", "Hour down", "Minute up", "Minute down").forEach { compose.onNodeWithContentDescription(it).assert(isButton) }
        compose.assertAccessible()
    }

    /** TalkBack heard "Minute 0", "Hour 14" as the picker opened, a "Colon" stop, and both up buttons before either value. */
    @Test
    fun theStepperReadsEachColumnInTurnAndSpeaksAValueOnlyOnceItChanges() {
        show(LocalTime.of(9, 0))
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
    fun eachStepperValueIsSpokenWithItsNameAndNoLeadingZero() {
        show(LocalTime.of(7, 0))
        compose.onNodeWithTag("hour_value")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hour 7")))
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_value").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hour 8")))
        compose.onNodeWithTag("minute_value").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Minute 0")))
    }
}
