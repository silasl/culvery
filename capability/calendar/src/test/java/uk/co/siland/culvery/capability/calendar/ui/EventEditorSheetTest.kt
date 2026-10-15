package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.EditableEvent
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.capability.calendar.saveFailedBody
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertAccessible

@RunWith(AndroidJUnit4::class)
class EventEditorSheetTest {
    /** Focus and the keyboard behave as on the tablet only in touch mode, set before the activity launches. */
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val london = ZoneId.of("Europe/London")
    private val calls = mutableListOf<String>()
    private var picker by mutableStateOf(EditorPicker.None)
    private var busy by mutableStateOf(false)
    private var failure by mutableStateOf<SaveFailure?>(null)

    /** A new event opened at 11:54 on the hand-off's Wednesday. */
    private fun newForm(signedIn: PersonId? = null) =
        EventForm(EventForm.Mode.New, SampleUi.TODAY, LocalTime.of(11, 54), london, signedIn, preselectedDay = null)

    private fun editForm(start: EventTime, end: EventTime, title: String = "Dinner with Jo & Priya", forPerson: String? = "alex") =
        EventForm(
            EventForm.Mode.Edit(EditableEvent(EventRef("c1", "s1", "e1"), title, start, end, forPerson)),
            SampleUi.TODAY, LocalTime.of(11, 54), london, signedIn = null, preselectedDay = null,
        )

    private fun dinner(): EventForm {
        val start = SampleUi.TODAY.atTime(19, 30).atZone(london).toInstant()
        return editForm(EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)))
    }

    private fun show(form: EventForm, childOnly: PersonId? = null, focus: Boolean = false) = compose.setContent {
        CulveryTheme(dark = true) {
            EventEditorSheet(
                form = form,
                people = SampleUi.household,
                service = "Google Calendar",
                childOnly = childOnly,
                busy = busy,
                failure = failure,
                picker = picker,
                onClose = { calls += "close" },
                onSave = { calls += "save" },
                onDelete = { calls += "delete" },
                onRefusedWho = { calls += "refused" },
                onPicker = {
                    picker = it
                    calls += "picker:$it"
                },
                focusTitleOnOpen = focus,
            )
        }
    }

    @Test
    fun aNewEventSaysNewEventAndOffersNoDelete() {
        show(newForm())
        compose.onAllNodesWithText("Add event").filterToOne(isHeading()).assertExists()
        compose.onNodeWithTag("editor_summary").assertTextEquals("Today · All day · Family")
        compose.onNodeWithTag("editor_save").assertTextEquals("Add event")
        compose.onNodeWithTag("editor_delete").assertDoesNotExist()
        compose.onNodeWithText("What’s happening?").assertExists()
    }

    @Test
    fun saveIsDisabledUntilThereIsATitle() {
        show(newForm())
        compose.onNodeWithTag("editor_save").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("editor_title").performTextInput("   ")
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_title").performTextReplacement("Parents evening")
        compose.onNodeWithTag("editor_save").assertIsEnabled().performClick()
        assertThat(calls).containsExactly("save")
    }

    @Test
    fun saveIsDisabledWhileBusy() {
        busy = true
        show(dinner())
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_delete").assertIsNotEnabled()
    }

    @Test
    fun anEditSaysEditEventAndOffersDelete() {
        show(dinner())
        compose.onNodeWithText("Change event").assertExists()
        compose.onNodeWithText("Save changes").assertExists()
        compose.onNodeWithTag("time_pick").assertTextEquals("19:30")
        compose.onNodeWithTag("length_90").assertIsSelected()
        compose.onNodeWithTag("who_Alex").assertIsSelected()
        compose.onNodeWithTag("editor_delete").performClick()
        assertThat(calls).containsExactly("delete")
    }

    @Test
    fun chipsChangeTheFormAndTheSummary() {
        show(newForm())
        // Today, the picker opens on 12:00 for a sheet opened at 11:54; two hours up is 14:00.
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        compose.onNodeWithTag("day_${SampleUi.TODAY.plusDays(1)}").performClick()
        compose.onNodeWithTag("who_Mia").performClick()
        compose.onNodeWithTag("editor_summary").assertTextEquals("Tomorrow · 14:00–15:00 · Mia")
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_summary").assertTextEquals("Tomorrow · 14:00–16:00 · Mia")
    }

    @Test
    fun lengthShowsOnlyForAPickedTime() {
        show(newForm())
        compose.onNodeWithTag("length_60").assertDoesNotExist()
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        compose.onNodeWithTag("length_60").assertExists()
        compose.onNodeWithTag("time_all_day").performClick()
        compose.onNodeWithTag("length_60").assertDoesNotExist()
    }

    @Test
    fun aSignedInChildsOtherWhoChipsAreDisabledAndATapExplains() {
        val form = newForm(signedIn = SampleUi.mia.id)
        show(form, childOnly = SampleUi.mia.id)
        compose.onNodeWithTag("who_Mia").assertIsSelected()
        compose.onNodeWithTag("who_Sam").performClick()
        compose.onNodeWithTag("who_Family").performClick()
        assertThat(calls).containsExactly("refused", "refused")
        assertThat(form.who).isEqualTo(SampleUi.mia.id)
    }

    @Test
    fun aLockedMultiDayEditShowsOneLineInPlaceOfDayTimeAndLength() {
        val halfTerm = editForm(EventTime.AllDay(LocalDate.of(2026, 9, 28)), EventTime.AllDay(LocalDate.of(2026, 10, 1)), "Half term", "family")
        show(halfTerm)
        compose.onNodeWithTag("editor_locked_dates").assertExists()
        compose.onNodeWithText("Mon 28 – Wed 30 · change dates in Google Calendar on your phone").assertExists()
        compose.onNodeWithTag("day_pick").assertDoesNotExist()
        compose.onNodeWithTag("time_all_day").assertDoesNotExist()
        compose.onNodeWithTag("who_Family").assertIsSelected()
    }

    @Test
    fun theFailureCardIsSpokenPolitelyAndTheTitleFieldIsNamed() {
        failure = SaveFailure("Google Calendar", WriteProblem.Tablet)
        show(newForm())
        compose.onNodeWithTag("editor_failure").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("editor_title").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Event name")))
    }

    @Test
    fun aFailureShowsTheCardAndSaveSaysTryAgain() {
        failure = SaveFailure("Sample calendar", WriteProblem.Refused("calendar is full"))
        val form = newForm()
        form.updateTitle("Car MOT")
        show(form)
        compose.onNodeWithTag("editor_failure").assertExists()
        compose.onNodeWithText(couldNotSave("Sample calendar", WriteProblem.Refused("calendar is full"))).assertExists()
        compose.onNodeWithText(saveFailedBody("Sample calendar", WriteProblem.Refused("calendar is full"))).assertExists()
        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithTag("editor_save").assertTextEquals("Try again")
    }

    @Test
    fun pickDateOpensTheDatePickerAndAPickSetsTheDay() {
        val form = newForm()
        show(form)
        compose.onNodeWithTag("day_pick").performClick()
        compose.onNodeWithTag("date_picker").assertExists()
        compose.onNodeWithTag("date_cell_2026-10-05").performClick()
        assertThat(form.day).isEqualTo(LocalDate.of(2026, 10, 5))
        compose.onNodeWithTag("date_picker").assertDoesNotExist()
        compose.onNodeWithTag("day_pick").assertTextEquals("Mon 5 Oct").assertIsSelected()
        assertThat(calls).containsExactly("picker:Date", "picker:None").inOrder()
    }

    @Test
    fun pickTimeSetsACustomTime() {
        val form = newForm()
        show(form)
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("hour_value").assertTextEquals("12")
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("minute_up").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(13, 15)))
        compose.onNodeWithTag("time_pick").assertTextEquals("13:15").assertIsSelected()
    }

    /** 5 §A.4: All day and Pick time… only; Repeats on a new event, Ends only once it repeats. */
    @Test
    fun aNewEventOffersRepeatsAndEndsOnlyWhenItRepeats() {
        show(newForm())
        compose.onNodeWithTag("time_all_day").assertIsSelected()
        compose.onNodeWithTag("time_Morning").assertDoesNotExist()
        compose.onNodeWithTag("repeat_Never").assertIsSelected()
        compose.onNodeWithTag("ends_never").assertDoesNotExist()
        compose.onNodeWithTag("repeat_Weekly").performClick()
        compose.onNodeWithTag("ends_never").assertIsSelected()
        compose.onNodeWithTag("ends_pick").performClick()
        assertThat(calls).contains("picker:EndDate")
    }

    /** A sheet short enough that the Ends row starts below the fold. */
    private fun showShort(form: EventForm) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.size(ShellTokens.sheetWidth, 520.dp)) {
                EventEditorSheet(
                    form = form, people = SampleUi.household, service = "Google Calendar", childOnly = null, busy = false,
                    failure = null, picker = EditorPicker.None, onClose = {}, onSave = {}, onDelete = {}, onRefusedWho = {},
                    onPicker = {}, focusTitleOnOpen = false,
                )
            }
        }
    }

    @Test
    fun choosingARepeatScrollsEndsIntoView() {
        showShort(newForm())
        compose.onNodeWithTag("repeat_Weekly").performScrollTo().performClick()
        compose.onNodeWithTag("ends_never").assertIsDisplayed()
    }

    @Test
    fun anEndBeforeTheDayScrollsItsLineIntoView() {
        val form = newForm()
        form.chooseRepeat(RepeatChoice.Weekly)
        showShort(form)
        compose.onNodeWithTag("ends_never").performScrollTo()
        compose.runOnIdle { form.chooseEnd(SampleUi.TODAY.minusDays(1)) }
        compose.onNodeWithTag("ends_error").assertIsDisplayed()
    }

    @Test
    fun anEditHasNoRepeats() {
        show(dinner())
        compose.onNodeWithTag("repeat_Never").assertDoesNotExist()
    }

    @Test
    fun anEndBeforeTheDaySaysSoAndSaveWaits() {
        val form = newForm()
        form.updateTitle("Swimming")
        form.chooseRepeat(RepeatChoice.Weekly)
        form.chooseEnd(SampleUi.TODAY.minusDays(1))
        show(form)
        compose.onNodeWithTag("ends_error").assertTextEquals("The end date is before the event’s day. Pick a later one.")
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
    }

    @Test
    fun aNewEventFocusesTheTitle() {
        show(newForm(), focus = true)
        compose.onNodeWithTag("editor_title").assertIsFocused()
    }

    @Test
    fun anEditOpensWithNothingFocused() {
        show(dinner(), focus = false)
        compose.onNodeWithTag("editor_title").assertIsNotFocused()
    }

    @Test
    fun doneClosesTheKeyboardWithoutSaving() {
        val form = newForm()
        form.updateTitle("Parents evening")
        show(form, focus = true)
        compose.onNodeWithTag("editor_title").performImeAction()
        compose.onNodeWithTag("editor_title").assertIsNotFocused()
        assertThat(calls).doesNotContain("save")
    }

    @Test
    fun openingAPickerClosesTheKeyboardButAChipDoesNot() {
        show(newForm(), focus = true)
        compose.onNodeWithTag("day_${SampleUi.TODAY.plusDays(1)}").performClick()
        compose.onNodeWithTag("editor_title").assertIsFocused()
        compose.onNodeWithTag("day_pick").performClick()
        // The picker hides the sheet from TalkBack, so the focus is read from the unmerged tree.
        compose.onNodeWithTag("editor_title", useUnmergedTree = true).assertIsNotFocused()
    }

    @Test
    fun theCloseButtonCloses() {
        show(newForm())
        compose.onNodeWithTag("sheet_close").performClick()
        assertThat(calls).containsExactly("close")
    }

    /** `WindowInsets.ime` is zero under Robolectric, so the hand-off's 320 dp keyboard on its 800 dp screen is passed in. */
    @Test
    fun saveStaysAboveTheKeyboard() {
        val screen = 800.dp
        val keyboard = 320.dp
        val form = newForm()
        form.updateTitle("Parents evening")
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(ShellTokens.sheetWidth, screen)) {
                    EventEditorSheet(
                        form = form, people = SampleUi.household, service = "Google Calendar", childOnly = null, busy = false, failure = null,
                        picker = EditorPicker.None, onClose = {}, onSave = { calls += "save" }, onDelete = {},
                        onRefusedWho = {}, onPicker = {}, keyboard = WindowInsets(bottom = keyboard),
                    )
                }
            }
        }
        val save = compose.onNodeWithTag("editor_save").getUnclippedBoundsInRoot()
        assertThat(save.bottom).isAtMost(screen - keyboard)
        // Hand-off 10: the header, the Title and the first rows of chips stay in view; the rest scrolls.
        compose.onAllNodesWithText("Add event").filterToOne(isHeading()).assertIsDisplayed()
        compose.onNodeWithTag("editor_title").assertIsDisplayed()
        compose.onNodeWithTag("who_Family").assertIsDisplayed()
        compose.onNodeWithTag("day_${SampleUi.TODAY}").assertIsDisplayed()
        compose.onNodeWithTag("editor_save").performClick()
        assertThat(calls).containsExactly("save")
    }

    @Test
    fun theSheetsTitleIsAHeading() {
        show(newForm())
        compose.onAllNodesWithText("Add event").filterToOne(isHeading()).assertExists()
    }

    @Test
    fun theSheetIsAPaneAndAPickerHidesIt() {
        show(newForm())
        compose.onNodeWithTag("editor_sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Add event"))
        compose.onNodeWithTag("day_pick").performClick()
        compose.onNodeWithTag("date_picker").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Pick a date"))
        compose.onNodeWithTag("editor_save").assertDoesNotExist()
        compose.onNodeWithTag("picker_cancel").performClick()
        compose.onNodeWithTag("editor_save").assertExists()
    }

    @Test
    fun whoChipsAreARadioGroupAndAChildsOthersSayWhyNot() {
        show(newForm(signedIn = SampleUi.mia.id), childOnly = SampleUi.mia.id)
        compose.onNodeWithTag("who_Mia").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
        compose.onNodeWithTag("who_Mia").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        val sam = compose.onNodeWithTag("who_Sam").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
        sam.performClick()
        assertThat(calls).contains("refused")
        compose.onNodeWithTag("day_pick").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithTag("time_pick").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.assertAccessible()
    }
}
