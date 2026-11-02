package uk.co.siland.culvery.capability.party.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.ModeIcons
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddModeSheetTest {
    @get:Rule val compose = createComposeRule()
    private val added = mutableListOf<Pair<String, String>>()

    private fun sheet() = compose.setContent {
        CulveryTheme(dark = true) {
            CompositionLocalProvider(LocalOverlayHost provides TestOverlay()) {
                AddModeSheet(listOf("Party mode"), { name, icon -> added += name to icon }, {})
            }
        }
    }

    private fun toTheIcons(name: String) {
        compose.onNodeWithTag("mode_name").performTextInput(name)
        compose.onNodeWithTag("mode_name_next").performClick()
    }

    /** Fails if: there aren't twelve, one isn't selected, one isn't a radio, or one is spoken by anything but its word. */
    @Test
    fun iconGridIsTwelveRadios() {
        sheet()
        toTheIcons("Film night")

        assertThat(ModeIcons.GRID).hasSize(12)
        for (icon in ModeIcons.GRID) {
            compose.onNodeWithTag("mode_icon_${icon.name}")
                .assertContentDescriptionEquals(icon.spoken)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        compose.onAllNodes(isSelectable() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(12)
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithTag("mode_icon_celebration").assert(isSelected())
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theTrimmedNameAndTheChosenIconAreAdded() {
        sheet()
        toTheIcons(" Film night ")
        compose.onNodeWithTag("mode_icon_pets").performClick()
        compose.onNodeWithTag("mode_icon_pets").assert(isSelected())
        compose.onAllNodes(isSelected()).assertCountEquals(1)

        compose.onNodeWithTag("mode_add_save").performClick()

        assertThat(added).containsExactly("Film night" to "pets")
    }

    @Test
    fun backKeepsTheTypedName() {
        sheet()
        toTheIcons("Film night")

        compose.onNodeWithTag("mode_icon_back").performClick()

        val typed = compose.onNodeWithTag("mode_name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertThat(typed).isEqualTo("Film night")
    }
}
