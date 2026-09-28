package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class EventDetailSheetTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var mode by mutableStateOf(DetailMode.Idle)
    private var busy by mutableStateOf(false)

    private fun show(detail: EventDetailUi) = compose.setContent {
        CulveryTheme(dark = true) {
            EventDetailSheet(
                detail = detail,
                people = SampleUi.household,
                mode = mode,
                busy = busy,
                onClose = { calls += "close" },
                onDelete = { calls += "delete" },
                onKeep = { calls += "keep" },
                onConfirmDelete = { calls += "confirm" },
                onChoosePerson = { calls += "choose" },
                onAssign = { p: Person -> calls += "assign:${p.name}" },
                onEdit = { calls += "edit" },
            )
        }
    }

    @Test
    fun editableShowsTheInfoRowsAndADeleteButton() {
        show(SampleUi.detailEditable)
        listOf("When", "Today · 19:30–21:00", "For", "Created by", "Calendar", "Family calendar")
            .forEach { compose.onNodeWithText(it).assertExists() }
        // For and Created by both read "Alex".
        compose.onAllNodesWithText("Alex").assertCountEquals(2)
        compose.onNodeWithText("Repeats").assertDoesNotExist()
        compose.onNodeWithTag("detail_delete").assertExists()
        compose.onNodeWithTag("detail_note").assertDoesNotExist()
        compose.onNodeWithTag("detail_syncing").assertDoesNotExist()
    }

    @Test
    fun anotherCalendarIsReadOnlyWithNoFooter() {
        show(SampleUi.detailReadOnlyFeed)
        compose.onNodeWithText("From School terms (read-only)").assertExists()
        compose.onNodeWithText("This is a subscribed calendar, so it can't be changed here.").assertExists()
        compose.onNodeWithText("School terms · read-only").assertExists()
        compose.onNodeWithText("Calendar feed").assertExists()
        compose.onNodeWithTag("detail_delete").assertDoesNotExist()
        compose.onNodeWithTag("detail_edit").assertDoesNotExist()
    }

    @Test
    fun aRepeatingEventPointsToThePhoneAndShowsRepeats() {
        show(SampleUi.detailRecurring)
        compose.onNodeWithText("Repeating event").assertExists()
        compose.onNodeWithText("Edit repeating events in Google Calendar on your phone.").assertExists()
        compose.onNodeWithText("Every week").assertExists()
        compose.onNodeWithText("Repeats").assertExists()
        compose.onNodeWithTag("detail_delete").assertDoesNotExist()
        compose.onNodeWithTag("detail_edit").assertDoesNotExist()
    }

    @Test
    fun anUntaggedEventOffersAssignAndKeepsDelete() {
        show(SampleUi.detailUntagged)
        compose.onNodeWithText("Added from a phone").assertExists()
        compose.onNodeWithText("Showing as Family until someone assigns it.").assertExists()
        compose.onNodeWithText("Added from phone").assertExists()
        compose.onNodeWithTag("detail_delete").assertExists()
        compose.onNodeWithTag("assign_Sam").assertDoesNotExist()
        compose.onNodeWithText("Assign to…").performClick()
        assertThat(calls).containsExactly("choose")
    }

    @Test
    fun choosingAPersonAssignsThem() {
        mode = DetailMode.ChoosingPerson
        show(SampleUi.detailUntagged)
        compose.onNodeWithTag("detail_assign").assertDoesNotExist()
        listOf("Alex", "Sam", "Mia").forEach { compose.onNodeWithTag("assign_$it").assertExists() }
        compose.onNodeWithTag("assign_Sam").performClick()
        assertThat(calls).containsExactly("assign:Sam")
    }

    @Test
    fun aPendingChangeShowsTheSyncingPill() {
        show(SampleUi.detailSyncing)
        compose.onNodeWithTag("detail_syncing").assertExists()
        compose.onNodeWithText("Syncing to Sample calendar…").assertExists()
    }

    @Test
    fun deleteRunsTheGuardFirstRatherThanDeleting() {
        show(SampleUi.detailEditable)
        compose.onNodeWithTag("detail_delete").performClick()
        assertThat(calls).containsExactly("delete")
        compose.onNodeWithText("Delete this event?").assertDoesNotExist()
    }

    @Test
    fun theConfirmationReplacesTheFooterWithKeepWhereDeleteWas() {
        show(SampleUi.detailEditable)
        val delete = compose.onNodeWithTag("detail_delete").getUnclippedBoundsInRoot()
        mode = DetailMode.ConfirmingDelete
        compose.onNodeWithText("Delete this event?").assertExists()
        compose.onNodeWithText("“Dinner with Jo & Priya” will be removed from Google Calendar for everyone.").assertExists()
        compose.onNodeWithTag("detail_delete").assertDoesNotExist()
        compose.onNodeWithTag("detail_edit").assertDoesNotExist()
        val keep = compose.onNodeWithTag("detail_keep").getUnclippedBoundsInRoot()
        val confirm = compose.onNodeWithTag("detail_confirm_delete").getUnclippedBoundsInRoot()
        // A second tap where Delete was lands on Keep event, so a double tap is harmless.
        val deleteCentreX = (delete.left + delete.right) / 2
        val deleteCentreY = (delete.top + delete.bottom) / 2
        assertThat(deleteCentreX).isAtLeast(keep.left)
        assertThat(deleteCentreX).isAtMost(keep.right)
        assertThat(deleteCentreY).isAtLeast(keep.top)
        assertThat(deleteCentreY).isAtMost(keep.bottom)
        assertThat(keep.right).isLessThan(confirm.left)
        compose.onNodeWithTag("detail_keep").performClick()
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        assertThat(calls).containsExactly("keep", "confirm").inOrder()
    }

    @Test
    fun whileBusyTheButtonsAreDisabled() {
        mode = DetailMode.ConfirmingDelete
        busy = true
        show(SampleUi.detailEditable)
        compose.onNodeWithTag("detail_confirm_delete").assertIsNotEnabled()
        compose.onNodeWithTag("detail_keep").assertIsNotEnabled()
    }

    @Test
    fun theCloseButtonCloses() {
        show(SampleUi.detailEditable)
        compose.onNodeWithTag("sheet_close").performClick()
        assertThat(calls).containsExactly("close")
    }

    @Test
    fun editSitsRightOfDeleteAndOpensTheEditor() {
        show(SampleUi.detailEditable)
        val delete = compose.onNodeWithTag("detail_delete").getUnclippedBoundsInRoot()
        val edit = compose.onNodeWithTag("detail_edit").assertHeightIsEqualTo(60.dp).getUnclippedBoundsInRoot()
        assertThat(edit.left).isGreaterThan(delete.right)
        compose.onNodeWithTag("detail_edit").performClick()
        assertThat(calls).containsExactly("edit")
    }

    @Test
    fun anUntaggedEventCanBeEdited() {
        show(SampleUi.detailUntagged)
        compose.onNodeWithTag("detail_edit").assertExists()
    }
}
