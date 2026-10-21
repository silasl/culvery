package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.EditableEvent
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale

val LONDON: ZoneId = ZoneId.of("Europe/London")

/** A timed event from Monday 28 September 18:00 to Wednesday 30 at 10:00: its dates stay locked. */
val LOCKED_START = EventTime.Timed(LocalDateTime.of(2026, 9, 28, 18, 0).atZone(LONDON).toInstant())
val LOCKED_END = EventTime.Timed(LocalDateTime.of(2026, 9, 30, 10, 0).atZone(LONDON).toInstant())

/** A new event in the full-screen editor, opened at 11:54 on the sample Wednesday for the household Alex, Sam, Mia. */
fun editorForm(
    childOnly: PersonId? = null,
    preselectedDay: LocalDate? = null,
    repeats: Boolean = childOnly == null,
) = EditorForm(
    EditorForm.Mode.New, SampleUi.TODAY, LocalTime.of(11, 54), LONDON, childOnly, preselectedDay,
    order = SampleUi.household.map { it.id }, calendarPerson = null, repeats = repeats,
)

/** An edit in the full-screen editor of an event on Alex's calendar, by [childOnly] when a child is signed in. */
fun editorEditOf(
    start: EventTime,
    end: EventTime,
    title: String = "Dinner with Jo & Priya",
    forPerson: String? = "alex",
    childOnly: PersonId? = null,
) = EditorForm(
    EditorForm.Mode.Edit(EditableEvent(EventRef("c1", "s1", "e1"), title, start, end, forPerson)),
    SampleUi.TODAY, LocalTime.of(11, 54), LONDON, childOnly = childOnly, preselectedDay = null,
    order = SampleUi.household.map { it.id }, calendarPerson = SampleUi.alex.id,
)

/** Dinner tonight at 19:30 for [length], in the full-screen editor. */
fun editorDinnerOf(length: Duration): EditorForm {
    val start = SampleUi.TODAY.atTime(19, 30).atZone(LONDON).toInstant()
    return editorEditOf(EventTime.Timed(start), EventTime.Timed(start.plus(length)))
}

/** Grandma's visit over [LOCKED_START] to [LOCKED_END] in the full-screen editor: its dates can't change here. */
fun editorLockedVisit() = editorEditOf(LOCKED_START, LOCKED_END, "Grandma visiting", "family")

/**
 * The full-screen editor as the tests draw it: [form] on [flow] for the sample household, saving to the Family calendar,
 * in the dark theme with no keyboard, and nothing failing; a new [form] or [flow] is a new editor.
 */
@Composable
fun ShowEditor(
    form: EditorForm,
    flow: EditorFlow,
    held: Held? = null,
    picker: EditorPicker = EditorPicker.None,
    scale: Float = 1f,
    dark: Boolean = true,
    members: List<Member> = SampleUi.members,
    context: String = "From Calendar · Alex signed in",
    savesTo: String? = "Family calendar",
    busy: Boolean = false,
    failure: SaveFailure? = null,
    childOnly: PersonId? = null,
    childName: String? = childOnly?.let { id -> SampleUi.household.first { it.id == id }.name },
    keyboard: WindowInsets = WindowInsets(bottom = 0.dp),
    onClose: () -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRefusedWho: () -> Unit = {},
    onPicker: (EditorPicker) -> Unit = {},
) {
    CulveryTheme(dark = dark) {
        WithFontScale(scale) {
            key(form, flow) {
                EventEditorScreen(
                    form = form,
                    flow = flow,
                    members = members,
                    context = context,
                    savesTo = savesTo,
                    service = "Google Calendar",
                    busy = busy,
                    failure = failure,
                    picker = picker,
                    childOnly = childOnly,
                    childName = childName,
                    held = held,
                    onClose = onClose,
                    onSave = onSave,
                    onDelete = onDelete,
                    onRefusedWho = onRefusedWho,
                    onPicker = onPicker,
                    keyboard = keyboard,
                )
            }
        }
    }
}

/** Sets the content the first time; after that the content is on screen, reading state the test has swapped, so it waits. */
class ShownOnce(private val compose: ComposeContentTestRule) {
    private var shown = false

    fun show(content: @Composable () -> Unit) {
        if (shown) {
            compose.waitForIdle()
        } else {
            shown = true
            compose.setContent(content)
        }
    }
}
