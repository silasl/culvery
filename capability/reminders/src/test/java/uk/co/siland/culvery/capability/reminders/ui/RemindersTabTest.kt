package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
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
import uk.co.siland.culvery.capability.reminders.BinsRowUi
import uk.co.siland.culvery.capability.reminders.CANT_READ_BINS
import uk.co.siland.culvery.capability.reminders.DueKey
import uk.co.siland.culvery.capability.reminders.RowUi
import uk.co.siland.culvery.capability.reminders.StripeUi
import uk.co.siland.culvery.capability.reminders.TabUi
import uk.co.siland.culvery.capability.reminders.binsRowTitle
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemindersTabTest {
    @get:Rule val compose = createComposeRule()

    private val mia = StripeUi(colour = 0xFFE07BA8)
    private val waiting = RowUi(DueKey.Mine(1), 1, listOf(mia), "07:30", "PE kit", "Mia", null)
    private val today = RowUi(null, 2, listOf(mia), "16:30", "Swimming bag", "Mia", "Every Friday")
    private val bins = BinsRowUi(on = true, time = LocalTime.of(18, 0), cantRead = false)
    private val opened = mutableListOf<Long>()
    private val done = mutableListOf<DueKey>()
    private var added = 0
    private var binsTapped = 0

    private fun show(ui: TabUi) = compose.setContent {
        CulveryTheme(dark = false) { RemindersTab(ui, onAdd = { added++ }, onOpen = { opened += it }, onDone = { done += it }, onBins = { binsTapped++ }) }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun aGroupWithNoRowsIsLeftOut() {
        show(TabUi(listOf(waiting), emptyList(), listOf(today), null))
        compose.onNodeWithTag("reminders_waiting").assertIsDisplayed()
        compose.onNodeWithTag("reminders_coming_up").assertIsDisplayed()
        assertThat(count("reminders_today")).isEqualTo(0)
        compose.assertAccessible()
    }

    @Test
    fun doneOnAWaitingRowNeedsNoPin() {
        show(TabUi(listOf(waiting), listOf(today), emptyList(), null))
        compose.onNodeWithTag("reminders_waiting_0_done").performClick()
        assertThat(done).containsExactly(DueKey.Mine(1))
        assertThat(count("reminders_today_0_done")).isEqualTo(0)
    }

    @Test
    fun tappingARowOpensIt() {
        show(TabUi(emptyList(), listOf(today), emptyList(), null))
        compose.onNodeWithTag("reminders_today_0").performClick()
        assertThat(opened).containsExactly(2L)
    }

    @Test
    fun addReminderIsInTheHeader() {
        show(TabUi(emptyList(), listOf(today), emptyList(), null))
        compose.onNodeWithTag("reminders_add").performClick()
        assertThat(added).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theEmptyLineShowsWithNoReminders() {
        show(TabUi(emptyList(), emptyList(), emptyList(), null))
        compose.onNodeWithTag("reminders_empty").assertIsDisplayed()
        compose.onNodeWithText(NO_REMINDERS).assertIsDisplayed()
        assertThat(NO_REMINDERS).isEqualTo("No reminders yet. Tap Add reminder to make one.")
    }

    @Test
    fun theBinsRowShowsOnlyWhenGiven() {
        show(TabUi(emptyList(), emptyList(), emptyList(), null))
        assertThat(count("reminders_bins")).isEqualTo(0)
    }

    @Test
    fun theBinsRowOpensItsSheet() {
        show(TabUi(emptyList(), listOf(today), emptyList(), bins))
        compose.onNodeWithText(binsRowTitle(LocalTime.of(18, 0))).assertIsDisplayed()
        compose.onNodeWithTag("reminders_bins").performClick()
        assertThat(binsTapped).isEqualTo(1)
        assertThat(count("reminders_empty")).isEqualTo(0)
    }

    @Test
    fun theCantReadLineShowsUnderTheBinsRow() {
        show(TabUi(emptyList(), emptyList(), emptyList(), bins.copy(cantRead = true)))
        compose.onNodeWithText(CANT_READ_BINS).assertIsDisplayed()
    }

    @Test
    fun theBinsRowSpeaksItsSwitch() {
        show(TabUi(emptyList(), emptyList(), emptyList(), bins))
        compose.onNodeWithTag("reminders_bins").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
    }

    @Test
    fun aSwitchedOffBinsRowSaysOff() {
        show(TabUi(emptyList(), emptyList(), emptyList(), bins.copy(on = false)))
        compose.onNodeWithTag("reminders_bins").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
    }
}
