package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.FAN
import uk.co.siland.culvery.capability.plugs.HOLIDAY
import uk.co.siland.culvery.capability.plugs.LAMP
import uk.co.siland.culvery.capability.plugs.ModeChoice
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 12 §2.4, §4.3: a mode's section lists each plug with its Leave, On or Off. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModeSectionTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()
    private val prefs = PlugPrefs().adding(LAMP).adding(FAN)

    private fun show(
        modes: List<HouseModeInfo> = listOf(HOLIDAY_MODE),
        editing: Boolean = false,
        prefs: PlugPrefs = this.prefs,
    ) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.size(380.dp, 700.dp)) {
                ModeSections(modes, listOf(LAMP_OFF, FAN_OFF), prefs, editing, onChoice = { m, id, c -> calls += "$m $id $c" })
            }
        }
    }

    private fun selected(on: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, on)

    /** Fails if a row at rest has anything to tap, or doesn't show the plug's name and its word. */
    @Test
    fun atRestARowIsReadOnly() {
        show(prefs = prefs.with(HOLIDAY, LAMP, ModeChoice.OFF))
        compose.onNodeWithTag("mode_holiday_0").assert(hasAnyDescendant(hasText("Lamp"))).assert(hasAnyDescendant(hasText("Off")))
        compose.onNodeWithTag("mode_holiday_1").assert(hasAnyDescendant(hasText("Fan"))).assert(hasAnyDescendant(hasText("Leave")))
        compose.onNodeWithTag("mode_holiday").assert(!hasAnyDescendant(hasClickAction()))
        compose.onAllNodesWithTag("mode_holiday_0_on").assertCountEquals(0)
    }

    /** Fails if Edit doesn't offer three chips with the chosen one selected, spoken as the row, or a tap doesn't report the choice. */
    @Test
    fun editOffersLeaveOnOff() {
        show(editing = true, prefs = prefs.with(HOLIDAY, LAMP, ModeChoice.OFF))
        compose.onNodeWithTag("mode_holiday_0_off").assert(selected(true))
        compose.onNodeWithTag("mode_holiday_0_on").assert(selected(false))
        compose.onNodeWithTag("mode_holiday_0_leave").assert(selected(false))
        compose.onNodeWithTag("mode_holiday_0_on").assertContentDescriptionEquals("Holiday mode, Lamp, on").performClick()
        assertThat(calls).containsExactly("holiday switch.lamp ON")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the chips of a row aren't one radio group. */
    @Test
    fun theChipsAreARadioGroup() {
        show(editing = true)
        compose.onNodeWithTag("mode_holiday_1").assert(hasAnyDescendant(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
    }

    /** Fails if two modes aren't two sections in order of `order`, or a second mode changes the first. */
    @Test
    fun aSecondModeIsASecondSection() {
        show(modes = listOf(PARTY_MODE, HOLIDAY_MODE), editing = true)
        val holiday = compose.onNodeWithTag("mode_holiday").fetchSemanticsNode().boundsInRoot.top
        val party = compose.onNodeWithTag("mode_party").fetchSemanticsNode().boundsInRoot.top
        assertThat(holiday).isLessThan(party)
        compose.onNodeWithTag("mode_party_1_on").performClick()
        assertThat(calls).containsExactly("party switch.fan ON")
        compose.onNodeWithTag("mode_holiday_0_leave").assert(selected(true))
        compose.onNodeWithTag("mode_party_0_leave").assert(selected(true))
    }
}
