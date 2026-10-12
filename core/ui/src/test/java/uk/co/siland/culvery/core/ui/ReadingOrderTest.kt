package uk.co.siland.culvery.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.test.isReadAsOne

@RunWith(AndroidJUnit4::class)
class ReadingOrderTest {
    @get:Rule val compose = createComposeRule()

    /** By position, TalkBack read "Coming up" before Today's empty line and a week's day headings before their chips. */
    @Test
    fun aCardIsReadThroughBeforeItsNeighbours() {
        compose.setContent { CulveryTheme(dark = true) { HhCard(Modifier.testTag("card")) { Text("Today") } } }
        compose.onNodeWithTag("card").assert(isReadAsOne)
    }
}
