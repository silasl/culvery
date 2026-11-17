package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.reminders.BinsSave
import uk.co.siland.culvery.capability.reminders.COULD_NOT_SAVE
import uk.co.siland.culvery.capability.reminders.PICK_A_BINS_TIME
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BinsSheetTest {
    @get:Rule val compose = createComposeRule()

    private val overlay = TestOverlay()
    private val saved = mutableListOf<Pair<Boolean, LocalTime>>()
    private var closed = 0
    private var result = BinsSave.Saved
    private var failure: Exception? = null

    private fun show(on: Boolean = true, time: LocalTime = LocalTime.of(18, 0)) = compose.setContent {
        CulveryTheme(dark = false) {
            BinsSheetHost(
                on = on,
                time = time,
                save = { switch, at -> failure?.let { throw it }; saved += switch to at; result },
                onEdited = overlay::markEdited,
                onClose = { closed++ },
            )
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun theSheetOffersTheSwitchTheTimeAndSaveChanges() {
        show()
        compose.onNodeWithTag("bins_on").assertIsDisplayed()
        compose.onNodeWithTag("bins_time").assertIsDisplayed()
        compose.onNodeWithText("18:00").assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.assertAccessible()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun savingHandsOverTheSwitchAndTheTimeAndClosesTheSheet() {
        show()
        compose.onNodeWithTag("bins_on").performClick()
        compose.onNodeWithTag("bins_time_later").performClick()
        compose.onNodeWithTag("bins_save").performClick()
        compose.waitForIdle()
        assertThat(saved).containsExactly(false to LocalTime.of(18, 30))
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun aBadTimeShowsItsLineAndKeepsTheSheet() {
        result = BinsSave.BadTime
        show()
        compose.onNodeWithTag("bins_save").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(PICK_A_BINS_TIME).assertIsDisplayed()
        assertThat(closed).isEqualTo(0)
        compose.onNodeWithTag("bins_save").assertIsEnabled()
    }

    @Test
    fun aCancelledPinPadKeepsTheSheetWithNoLine() {
        result = BinsSave.Cancelled
        show()
        compose.onNodeWithTag("bins_save").performClick()
        compose.waitForIdle()
        assertThat(closed).isEqualTo(0)
        assertThat(count("bins_line")).isEqualTo(0)
    }

    @Test
    fun aFailedSaveLogsItsClassShowsItsLineAndKeepsTheSheet() {
        ShadowLog.clear()
        failure = IllegalStateException("secret")
        show()
        compose.onNodeWithTag("bins_save").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(COULD_NOT_SAVE).assertIsDisplayed()
        assertThat(closed).isEqualTo(0)
        compose.onNodeWithTag("bins_save").assertIsEnabled()
        assertThat(ShadowLog.getLogsForTag("Reminders").map { it.msg }).containsExactly("Couldn't save (IllegalStateException)")
    }
}
