package uk.co.siland.culvery.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HhIconTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun describedIconIsAnnouncedByItsDescriptionNotItsLigature() {
        compose.setContent { CulveryTheme(dark = true) { HhIcon("backspace", contentDescription = "Delete last digit") } }
        compose.onNodeWithContentDescription("Delete last digit").assertExists()
        compose.onNodeWithText("backspace").assertDoesNotExist()
    }

    @Test
    fun decorativeIconIsHidden() {
        compose.setContent { CulveryTheme(dark = true) { HhIcon("home") } }
        compose.onNodeWithText("home").assertDoesNotExist()
    }
}
