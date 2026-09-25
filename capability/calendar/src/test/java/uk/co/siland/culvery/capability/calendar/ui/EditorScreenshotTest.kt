package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditableEvent
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.TimeSlot
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens

/** The 800 dp screen less the hand-off's 320 dp keyboard (hand-off 10). */
private val ABOVE_THE_KEYBOARD = 480.dp

/**
 * Hand-off 10–16 as the shell shows the sheet: against the right edge over the scrim, on the 1280×800 canvas, at
 * 11:54 on Wednesday 23 September. Nothing is focused, so no cursor blinks into the images.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val london = ZoneId.of("Europe/London")

    private fun newForm(signedIn: PersonId? = null) =
        EventForm(EventForm.Mode.New, SampleUi.TODAY, LocalTime.of(11, 54), london, signedIn, preselectedDay = null)

    private fun snap(
        name: String,
        dark: Boolean,
        form: EventForm,
        height: Dp? = null,
        childOnly: PersonId? = null,
        failure: String? = null,
        picker: EditorPicker = EditorPicker.None,
    ) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
                    Box(Modifier.align(Alignment.TopEnd).then(if (height != null) Modifier.height(height) else Modifier.fillMaxHeight())) {
                        EventEditorSheet(
                            form = form,
                            people = SampleUi.household,
                            childOnly = childOnly,
                            busy = false,
                            failure = failure,
                            picker = picker,
                            onClose = {},
                            onSave = {},
                            onDelete = {},
                            onRefusedWho = {},
                            onPicker = {},
                            focusTitleOnOpen = false,
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /** Hand-off 10: "Parents evening" at 18:00, the sheet as tall as the space above the keyboard. */
    private fun keyboard() = newForm().apply {
        updateTitle("Parents evening")
        chooseTime(TimeChoice.Slot(TimeSlot.Evening))
    }

    /** Hand-off 11: no title yet, Saturday, All day. */
    private fun empty() = newForm().apply {
        chooseDay(SampleUi.TODAY.plusDays(3))
        chooseTime(TimeChoice.AllDay)
    }

    /** Hand-off 12: Mia signed in, her sleepover on Sunday evening. */
    private fun child() = newForm(signedIn = SampleUi.mia.id).apply {
        updateTitle("Sleepover at Ava's")
        chooseDay(SampleUi.TODAY.plusDays(4))
        chooseTime(TimeChoice.Slot(TimeSlot.Evening))
    }

    /** Hand-off 13: dinner at 19:30 for 2 h; the 19:30 start shows on Pick time… (2b-2 design D3). */
    private fun edit() = dinner(hours = 2)

    /** 2b-2 design D3: an event 1 h 30 long adds its own length as a fourth Length chip, selected. */
    private fun ownLength() = dinner(minutes = 90)

    private fun dinner(hours: Long = 0, minutes: Long = 0): EventForm {
        val start = SampleUi.TODAY.atTime(19, 30).atZone(london).toInstant()
        val end = start.plusSeconds((hours * 60 + minutes) * 60)
        val event = EditableEvent(EventRef("c1", "s1", "e1"), "Dinner with Jo & Priya", EventTime.Timed(start), EventTime.Timed(end), SampleUi.alex.id.value)
        return EventForm(EventForm.Mode.Edit(event), SampleUi.TODAY, LocalTime.of(11, 54), london, null, null)
    }

    /** Hand-off 14: the car's MOT on Friday morning, refused. */
    private fun failed() = newForm(signedIn = SampleUi.alex.id).apply {
        updateTitle("Car MOT")
        chooseDay(SampleUi.TODAY.plusDays(2))
        chooseTime(TimeChoice.Slot(TimeSlot.Morning))
    }

    /** Hand-off 15: a half-term trip on Monday 5 October, with the date picker open. */
    private fun datePicker() = newForm().apply {
        updateTitle("Half-term trip")
        chooseDay(LocalDate.of(2026, 10, 5))
        chooseTime(TimeChoice.Slot(TimeSlot.Evening))
    }

    /** Hand-off 16: a haircut at 16:15 today, with the time picker open. */
    private fun timePicker() = newForm().apply {
        updateTitle("Haircut")
        chooseTime(TimeChoice.Custom(LocalTime.of(16, 15)))
    }

    /** 2b-2 design D3: an all-day event from Monday 28 September to Wednesday 30, dates locked. */
    private fun locked(): EventForm {
        val visit = EditableEvent(
            EventRef("c1", "s1", "e2"), "Grandma visiting",
            EventTime.AllDay(LocalDate.of(2026, 9, 28)), EventTime.AllDay(LocalDate.of(2026, 10, 1)), "family",
        )
        return EventForm(EventForm.Mode.Edit(visit), SampleUi.TODAY, LocalTime.of(11, 54), london, null, null)
    }

    private val failure = couldNotSave("Sample calendar", "Calendar is full")

    @Test fun keyboardDark() = snap("editor_keyboard_dark", true, keyboard(), height = ABOVE_THE_KEYBOARD)
    @Test fun keyboardLight() = snap("editor_keyboard_light", false, keyboard(), height = ABOVE_THE_KEYBOARD)
    @Test fun emptyDark() = snap("editor_empty_dark", true, empty())
    @Test fun emptyLight() = snap("editor_empty_light", false, empty())
    @Test fun childDark() = snap("editor_child_dark", true, child(), childOnly = SampleUi.mia.id)
    @Test fun childLight() = snap("editor_child_light", false, child(), childOnly = SampleUi.mia.id)
    @Test fun editDark() = snap("editor_edit_dark", true, edit())
    @Test fun editLight() = snap("editor_edit_light", false, edit())
    @Test fun ownLengthDark() = snap("editor_own_length_dark", true, ownLength())
    @Test fun ownLengthLight() = snap("editor_own_length_light", false, ownLength())
    @Test fun failedDark() = snap("editor_failed_dark", true, failed(), failure = failure)
    @Test fun failedLight() = snap("editor_failed_light", false, failed(), failure = failure)
    @Test fun datePickerDark() = snap("editor_date_picker_dark", true, datePicker(), picker = EditorPicker.Date)
    @Test fun datePickerLight() = snap("editor_date_picker_light", false, datePicker(), picker = EditorPicker.Date)
    @Test fun timePickerDark() = snap("editor_time_picker_dark", true, timePicker(), picker = EditorPicker.Time)
    @Test fun timePickerLight() = snap("editor_time_picker_light", false, timePicker(), picker = EditorPicker.Time)
    @Test fun lockedDark() = snap("editor_locked_dark", true, locked())
    @Test fun lockedLight() = snap("editor_locked_light", false, locked())
}
