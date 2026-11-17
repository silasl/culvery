package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.robolectric.shadows.ShadowLog
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import uk.co.siland.culvery.capability.reminders.COULD_NOT_DELETE
import uk.co.siland.culvery.capability.reminders.COULD_NOT_SAVE
import uk.co.siland.culvery.capability.reminders.RemindersActions
import uk.co.siland.culvery.capability.reminders.RemindersStore
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase
import uk.co.siland.culvery.core.access.test.FakeAccess
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.reminders.LIST_FULL
import uk.co.siland.culvery.capability.reminders.PICK_A_LATER_TIME
import uk.co.siland.culvery.capability.reminders.ReminderDraft
import uk.co.siland.culvery.capability.reminders.ReminderForm
import uk.co.siland.culvery.capability.reminders.SaveResult
import uk.co.siland.culvery.capability.reminders.Schedule
import uk.co.siland.culvery.capability.reminders.WORDS_MAX
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role as HouseholdRole
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReminderSheetTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val overlay = TestOverlay()
    private val alex = Member(Person(PersonId("alex"), "Alex", 0xFF4CB387), HouseholdRole.ADMIN, hasPin = true)
    private val sam = Member(Person(PersonId("sam"), "Sam", 0xFF5B9BE0), HouseholdRole.ADULT, hasPin = true)
    private val mia = Member(Person(PersonId("mia"), "Mia", 0xFFE07BA8), HouseholdRole.CHILD, hasPin = true)
    private val household = listOf(alex, sam, mia)

    private val saved = mutableListOf<Pair<Long?, ReminderDraft>>()
    private val deleted = mutableListOf<Long>()
    private var closed = 0
    private var refused = 0
    private var result = SaveResult.Saved
    private var failure: Exception? = null

    private fun form(childOnly: PersonId? = null) = ReminderForm(null, today, LocalTime.of(10, 0), childOnly)

    private var editing by mutableStateOf<Long?>(null)

    private fun show(form: ReminderForm, existing: Long? = null, childName: String? = null) {
        editing = existing
        compose.setContent {
            CulveryTheme(dark = false) {
                ReminderSheetHost(
                    form = form,
                    editing = editing,
                    people = household,
                    childName = childName,
                    save = { id, draft -> failure?.let { throw it }; saved += id to draft; result },
                    delete = { id -> failure?.let { throw it }; deleted += id; true },
                    onRefusedWho = { refused++ },
                    onEdited = overlay::markEdited,
                    onClose = { closed++ },
                )
            }
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    @Test
    fun aNewOneSaysAddReminderAndAnExistingOneSaveChangesWithDelete() {
        show(form())
        compose.onNodeWithText("Add reminder").assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertDoesNotExist()
        assertThat(count("reminder_delete")).isEqualTo(0)

        editing = 7
        compose.onNodeWithText("Save changes").assertExists()
        compose.onNodeWithText("Delete reminder").assertExists()
        compose.onNodeWithText("Add reminder").assertDoesNotExist()
    }

    @Test
    fun repeatsSwapsTheDayForSevenChips() {
        val form = form()
        show(form)
        compose.onNodeWithTag("reminder_date").assertExists()
        assertThat(count("reminder_day_MONDAY")).isEqualTo(0)

        compose.onNodeWithTag("reminder_repeat_1").performClick()
        assertThat(count("reminder_date")).isEqualTo(0)
        DayOfWeek.entries.forEach { compose.onNodeWithTag("reminder_day_${it.name}").assert(isCheckbox) }
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { compose.onNodeWithText(it).assertExists() }

        compose.onNodeWithTag("reminder_day_MONDAY").performClick()
        compose.onNodeWithTag("reminder_day_FRIDAY").performClick()
        assertThat(form.days).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)

        compose.onNodeWithTag("reminder_repeat_0").performClick()
        compose.onNodeWithTag("reminder_date").assertExists()
        assertThat(count("reminder_day_MONDAY")).isEqualTo(0)
    }

    @Test
    fun addNeedsWordsAndARepeatNeedsADay() {
        val form = form()
        show(form)
        compose.onNodeWithTag("reminder_save").assertIsNotEnabled()
        compose.onNodeWithTag("reminder_words").performTextInput("PE kit")
        compose.onNodeWithTag("reminder_save").assertIsEnabled()
        compose.onNodeWithTag("reminder_repeat_1").performClick()
        compose.onNodeWithTag("reminder_save").assertIsNotEnabled()
        compose.onNodeWithTag("reminder_day_MONDAY").performClick()
        compose.onNodeWithTag("reminder_save").assertIsEnabled()
    }

    @Test
    fun saveHandsTheDraftToSaveAndClosesOnSaved() {
        show(form())
        compose.onNodeWithTag("reminder_words").performTextInput("PE kit")
        compose.onNodeWithTag("who_Mia").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_save").performClick()
        compose.waitForIdle()
        assertThat(saved).containsExactly(null to ReminderDraft("PE kit", listOf(mia.person.id), LocalTime.of(10, 0), Schedule.Once(today)))
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun aPastTimeShowsItsLineAndKeepsTheSheet() {
        result = SaveResult.Past
        show(form())
        compose.onNodeWithTag("reminder_words").performTextInput("PE kit")
        compose.onNodeWithTag("reminder_save").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("reminder_line").assertIsDisplayed()
        compose.onNodeWithText(PICK_A_LATER_TIME).assertIsDisplayed()
        assertThat(closed).isEqualTo(0)
        compose.onNodeWithTag("reminder_save").assertIsEnabled()
    }

    @Test
    fun aFullListShowsItsLineAndKeepsTheSheet() {
        result = SaveResult.Full
        show(form())
        compose.onNodeWithTag("reminder_words").performTextInput("PE kit")
        compose.onNodeWithTag("reminder_save").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(LIST_FULL).assertIsDisplayed()
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun aCancelledPadKeepsTheSheetWithNoLine() {
        result = SaveResult.Cancelled
        show(form())
        compose.onNodeWithTag("reminder_words").performTextInput("PE kit")
        compose.onNodeWithTag("reminder_save").performClick()
        compose.waitForIdle()
        assertThat(count("reminder_line")).isEqualTo(0)
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun aReminderThatHasGoneClosesTheSheet() {
        result = SaveResult.Gone
        show(form(), existing = 7)
        compose.onNodeWithTag("reminder_words").performTextInput("PE kit")
        compose.onNodeWithTag("reminder_save").performClick()
        compose.waitForIdle()
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun deleteAsksForTheReminderAndCloses() {
        show(form(), existing = 7)
        compose.onNodeWithTag("reminder_delete").performClick()
        compose.waitForIdle()
        assertThat(deleted).containsExactly(7L)
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun typingMarksTheSheetEditedAndWordsStopAtSixty() {
        val form = form()
        show(form)
        compose.waitForIdle()
        assertThat(overlay.edited).isFalse()
        compose.onNodeWithTag("reminder_words").performTextInput("x".repeat(WORDS_MAX + 10))
        compose.waitForIdle()
        assertThat(overlay.edited).isTrue()
        assertThat(form.words).hasLength(WORDS_MAX)
    }

    @Test
    fun aChildSeesTheirWhoLockedAndTheReasonWhyAndATapOnAnotherSaysSo() {
        show(form(childOnly = mia.person.id), childName = "Mia")
        compose.onNodeWithText("Mia can add reminders for Mia only.").assertExists()
        compose.onNodeWithTag("who_Alex").performScrollTo().performClick()
        assertThat(refused).isEqualTo(1)
    }

    @Test
    fun theSheetIsAccessible() {
        show(form())
        compose.assertClickablesHaveRoleAndLabel()
        // The date grid is core's; its day cells are checked there.
        compose.onNodeWithTag("reminder_repeat_1").performClick()
        compose.assertAccessible()
    }

    @Test
    fun aFailedSaveLogsItsClassShowsItsLineAndKeepsTheSheet() {
        ShadowLog.clear()
        failure = IllegalStateException("secret words")
        show(form())
        compose.onNodeWithTag("reminder_words").performTextInput("PE kit")
        compose.onNodeWithTag("reminder_save").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(COULD_NOT_SAVE).assertIsDisplayed()
        assertThat(closed).isEqualTo(0)
        compose.onNodeWithTag("reminder_save").assertIsEnabled()
        assertThat(ShadowLog.getLogsForTag("Reminders").map { it.msg }).containsExactly("Couldn't save (IllegalStateException)")
    }

    @Test
    fun aFailedDeleteLogsItsClassShowsItsLineAndKeepsTheSheet() {
        ShadowLog.clear()
        failure = IllegalStateException("secret words")
        show(form(), existing = 7)
        compose.onNodeWithTag("reminder_delete").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(COULD_NOT_DELETE).assertIsDisplayed()
        assertThat(closed).isEqualTo(0)
        assertThat(ShadowLog.getLogsForTag("Reminders").map { it.msg }).containsExactly("Couldn't delete (IllegalStateException)")
    }

    @Test
    fun aFailureToOpenLogsItsClassAndToasts() = runTest {
        ShadowLog.clear()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RemindersDatabase::class.java).allowMainThreadQueries().build()
        val householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        val toaster = RecordingToaster()
        val actions = RemindersActions(
            FakeAccess(null), RemindersStore(db), WallClock { throw IllegalStateException("secret words") }, HouseholdZone(HouseholdRepository(householdDb)), toaster, { null }, backgroundScope,
        )
        overlay.openReminder(null, actions, flowOf(emptyList()))
        assertThat(overlay.content).isNull()
        assertThat(toaster.shown).containsExactly("Couldn’t open the reminder — try again.")
        assertThat(ShadowLog.getLogsForTag("Reminders").map { it.msg }.single()).isEqualTo("Couldn't open (IllegalStateException)")
        db.close()
        householdDb.close()
    }
}
