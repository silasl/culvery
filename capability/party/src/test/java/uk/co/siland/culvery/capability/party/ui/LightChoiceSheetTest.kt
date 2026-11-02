package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
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
import uk.co.siland.culvery.core.house.HouseScene
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 15b §4.2: what a mode does to a room, as the sheet offers it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightChoiceSheetTest {
    @get:Rule val compose = createComposeRule()
    private val saved = mutableListOf<LightChoice>()
    private val kitchen = LightRoomOption("r1", "Kitchen", listOf(HouseScene("s1", "Bright"), HouseScene("s2", "Relax")))
    private val hall = LightRoomOption("r3", "Hall", emptyList(), dimmable = false)

    private fun sheet(room: LightRoomOption, current: LightChoice? = null) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.size(960.dp, 900.dp)) { LightChoiceSheet(room, current, { saved += it }, {}) }
        }
    }

    private fun press(tag: String, times: Int) = repeat(times) { compose.onNodeWithTag(tag).performClick() }

    /** Fails if the percent moves by anything but ten, or runs past 10 or 100. */
    @Test
    fun percentStepsByTen() {
        sheet(kitchen, LightChoice.On(20))
        compose.onNodeWithTag("light_percent").assertTextEquals("20%")

        press("light_minus", 1)
        compose.onNodeWithTag("light_percent").assertTextEquals("10%")
        compose.onNodeWithTag("light_minus").assertIsNotEnabled()
        press("light_plus", 9)
        compose.onNodeWithTag("light_percent").assertTextEquals("100%")
        compose.onNodeWithTag("light_plus").assertIsNotEnabled()
        compose.onNodeWithTag("light_minus").assertIsEnabled()
        press("light_minus", 3)
        compose.onNodeWithTag("light_save").performClick()

        assertThat(saved).containsExactly(LightChoice.On(70))
        assertThat(PERCENTS).containsExactly(10, 20, 30, 40, 50, 60, 70, 80, 90, 100).inOrder()
    }

    /** S5: fails if a room that can't dim is offered a percent, or On saves as anything but 100. */
    @Test
    fun aNonDimmableRoomOffersOnWithoutAPercent() {
        sheet(hall)

        compose.onNodeWithTag("light_on").performClick()

        compose.onAllNodesWithTag("light_minus").assertCountEquals(0)
        compose.onAllNodesWithTag("light_plus").assertCountEquals(0)
        compose.onAllNodesWithTag("light_percent").assertCountEquals(0)
        compose.onNodeWithTag("light_save").performClick()
        assertThat(saved).containsExactly(LightChoice.On(100))
        assertThat(lightChip(saved.single(), dimmable = false)).isEqualTo("On")
    }

    /** Fails if the scenes aren't radio rows of which one is chosen at a time, or Save is open before a choice. */
    @Test
    fun scenesAreRadios() {
        sheet(kitchen)
        compose.onNodeWithTag("light_save").assertIsNotEnabled()
        compose.onNodeWithTag("light_scene_0").assertTextEquals("Bright").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithTag("light_scene_1").assertTextEquals("Relax").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

        compose.onNodeWithTag("light_scene_1").performClick()
        compose.onNodeWithTag("light_scene_1").assertIsSelected()
        compose.onNodeWithTag("light_scene_0").assertIsNotSelected()
        compose.onNodeWithTag("light_scene_0").performClick()
        compose.onNodeWithTag("light_scene_1").assertIsNotSelected()
        compose.onNodeWithTag("light_save").assertIsEnabled().performClick()

        assertThat(saved).containsExactly(LightChoice.Scene("s1", "Bright"))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if Off isn't a radio row that replaces a scene. */
    @Test
    fun offIsARadio() {
        sheet(kitchen, LightChoice.Scene("s1", "Bright"))
        compose.onNodeWithTag("light_scene_0").assertIsSelected()
        compose.onNodeWithTag("light_off").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

        compose.onNodeWithTag("light_off").performClick()

        compose.onNodeWithTag("light_off").assertIsSelected()
        compose.onNodeWithTag("light_scene_0").assertIsNotSelected()
        compose.onNodeWithTag("light_save").performClick()
        assertThat(saved).containsExactly(LightChoice.Off)
    }

    /** Fails if nothing is saved until Save light, or On starts at anything but full. */
    @Test
    fun nothingSavesUntilSaveLight() {
        sheet(kitchen)

        compose.onNodeWithTag("light_on").performClick()
        compose.onNodeWithTag("light_percent").assertTextEquals("100%")

        assertThat(saved).isEmpty()
    }

    @Test
    fun theChipsRead() {
        assertThat(lightChip(LightChoice.Scene("s2", "Relax"))).isEqualTo("Scene: Relax")
        assertThat(lightChip(LightChoice.On(60))).isEqualTo("On · 60%")
        assertThat(lightChip(LightChoice.Off)).isEqualTo("Off")
    }
}
