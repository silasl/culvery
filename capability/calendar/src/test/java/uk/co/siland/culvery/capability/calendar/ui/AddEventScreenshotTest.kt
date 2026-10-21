package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Duration
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.HeldEvent
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.lastShownDay
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.TouchModeRule

/** Handoff §1 as the shell shows the editor: full screen on the 1280 × 800 canvas, at 11:54 on Wednesday 23 September. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddEventScreenshotTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private fun show(
        form: EditorForm,
        dark: Boolean,
        keyboard: Dp = 0.dp,
        savesTo: String = "Family calendar",
        child: Person? = null,
        start: EditorStep? = null,
        held: Held? = null,
    ) {
        val flow = editorFlow(form, child?.id, start)
        compose.setContent {
            CulveryTheme(dark = dark) {
                EventEditorScreen(
                    form = form,
                    flow = flow,
                    members = SampleUi.members,
                    context = "From Calendar · ${child?.name ?: "Alex"} signed in",
                    savesTo = savesTo,
                    service = "Google Calendar",
                    busy = false,
                    failure = null,
                    picker = EditorPicker.None,
                    childOnly = child?.id,
                    childName = child?.name,
                    held = held,
                    onClose = {},
                    onSave = {},
                    onDelete = {},
                    onRefusedWho = {},
                    onPicker = {},
                    keyboard = WindowInsets(bottom = keyboard),
                )
            }
        }
    }

    private fun snap(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    /** Nothing filled in, the title focused and Who active. */
    @Test
    fun addEventStartDark() {
        show(editorForm(), dark = true)
        snap("add_event_start_dark")
    }

    @Test
    fun addEventStartLight() {
        show(editorForm(), dark = false)
        snap("add_event_start_light")
    }

    /** Handoff 6a: "Gui" typed with the 320 dp keyboard up: short rows with no labels, and no foot. */
    @Test
    fun addEventKeyboardDark() {
        show(editorForm(), dark = true, keyboard = 320.dp)
        compose.onNodeWithTag("editor_title").performTextInput("Gui")
        snap("add_event_keyboard_dark")
    }

    /** Handoff 6b: "Guitar lesson" for Sam and Mia, chosen as two tiles, with Everyone and the hint below. */
    @Test
    fun addEventWhoDark() {
        val form = editorForm().apply {
            updateTitle("Guitar lesson")
            toggleWho(SampleUi.sam.id)
            toggleWho(SampleUi.mia.id)
        }
        show(form, dark = true)
        snap("add_event_who_dark")
    }

    /** Handoff 6b with Mia signed in: Alex, Sam and Everyone locked, Mia chosen, and the line saying why. */
    @Test
    fun addEventWhoChildDark() {
        val mia = SampleUi.mia
        val form = editorForm(childOnly = mia.id).apply { updateTitle("Piano practice") }
        show(form, dark = true, child = mia)
        snap("add_event_who_child_dark")
    }

    /** The week's people as the handoff's 6c dots them: something on most days of this week. */
    private val sampleHeld: Held = run {
        val (alex, sam, mia) = SampleUi.household.map { it.id }
        val family = PersonId.FAMILY
        val days = mapOf(
            23 to listOf(sam, family),
            24 to listOf(alex, mia),
            25 to listOf(family, sam),
            26 to listOf(family, mia, sam),
            27 to listOf(family),
            28 to listOf(alex, mia),
            29 to listOf(family),
        )
        val events = days.map { (d, people) ->
            val date = LocalDate.of(2026, 9, d)
            HeldEvent(EventRef("c1", "s1", "e$d"), "Sample", people, EventTime.AllDay(date), EventTime.AllDay(date.plusDays(1)))
        }
        Held(events, lastShownDay(SampleUi.TODAY), emptySet(), firstDay = SampleUi.TODAY)
    }

    /** Handoff 6c: "Guitar lesson" for Mia on Sat 26 Sep, with the month's dots and today ringed. */
    @Test
    fun addEventDayDark() {
        val form = editorForm().apply {
            updateTitle("Guitar lesson")
            toggleWho(SampleUi.mia.id)
            tapDay(LocalDate.of(2026, 9, 26))
        }
        show(form, dark = true, start = EditorStep.Day, held = sampleHeld)
        snap("add_event_day_dark")
    }

    /** Handoff 6d: a trip for everyone, Thu 29 Oct to the trailing Sun 1 Nov, banded across October's last line. */
    @Test
    fun addEventDaysDark() {
        val form = editorForm().apply {
            updateTitle("Half-term trip to Devon")
            toggleWho(PersonId.FAMILY)
            setMultiDay(true)
            tapDay(LocalDate.of(2026, 10, 29))
            tapDay(LocalDate.of(2026, 11, 1))
        }
        show(form, dark = true, start = EditorStep.Day, held = sampleHeld)
        snap("add_event_days_dark")
    }

    /** The sample week with Mia's Piano, 15:30–16:15, on Sat 26 Sep: hours 15 and 16 are busy. */
    private val pianoHeld: Held = run {
        val sat = LocalDate.of(2026, 9, 26)
        val piano = HeldEvent(
            EventRef("c1", "s1", "piano"), "Piano", listOf(SampleUi.mia.id),
            EventTime.Timed(sat.atTime(15, 30).atZone(LONDON).toInstant()), EventTime.Timed(sat.atTime(16, 15).atZone(LONDON).toInstant()),
        )
        sampleHeld.copy(events = sampleHeld.events + piano)
    }

    private fun guitarAt(hour: Int) = editorForm().apply {
        updateTitle("Guitar lesson")
        toggleWho(SampleUi.mia.id)
        tapDay(LocalDate.of(2026, 9, 26))
        chooseHour(hour)
        chooseLength(Duration.ofMinutes(45))
    }

    /** Handoff 6e: Guitar lesson for Mia on Sat 26 Sep at 10:00–10:45, with 15 and 16 busy and "Mia is free then". */
    @Test
    fun addEventTimeDark() {
        show(guitarAt(10), dark = true, start = EditorStep.Time, held = pianoHeld)
        snap("add_event_time_dark")
    }

    /** Handoff 6i: the same at 15:00–15:45, over Piano: the warning, the free starts 14:45 and 16:15, and the Clash tag. */
    @Test
    fun addEventClashDark() {
        show(guitarAt(15), dark = true, start = EditorStep.Time, held = pianoHeld)
        snap("add_event_clash_dark")
    }

    /** Guitar lesson for Mia, Sat 26 Sep 10:00–10:45, every Saturday, 12 times. */
    private fun weeklyGuitar() = guitarAt(10).apply {
        chooseRepeat(RepeatChoice.Weekly)
        stopsAfter()
        repeat(2) { moreTimes() }
    }

    /** Handoff 6f: Every Saturday chosen, Stops after 12 with its last date, and where to skip one date. */
    @Test
    fun addEventRepeatsDark() {
        show(weeklyGuitar(), dark = true, start = EditorStep.Repeats)
        snap("add_event_repeats_dark")
    }

    /** Handoff 6g: the sentence, the first four Saturdays with Mia's dot and "+ 8 more", and Mia's week chip. */
    @Test
    fun addEventCheckDark() {
        show(weeklyGuitar(), dark = true, start = EditorStep.Check)
        snap("add_event_check_dark")
    }

    /** Cinema for Sam and Mia on Fri 25 Sep, 17:00–18:00: one date with both dots, and the neutral striped chip. */
    @Test
    fun addEventCheckSharedDark() {
        val form = editorForm().apply {
            updateTitle("Cinema")
            toggleWho(SampleUi.sam.id)
            toggleWho(SampleUi.mia.id)
            tapDay(LocalDate.of(2026, 9, 25))
            chooseHour(17)
        }
        show(form, dark = true, start = EditorStep.Check)
        snap("add_event_check_shared_dark")
    }

    /** Dinner tonight at 19:30 for 90 minutes, opened on Check. */
    @Test
    fun changeEventDark() {
        show(editorDinnerOf(Duration.ofMinutes(90)), dark = true, savesTo = "Family")
        snap("change_event_dark")
    }
}
