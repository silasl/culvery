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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.TouchModeRule

/** Handoff §1 as the shell shows the editor: full screen on the 1280 × 800 canvas, at 11:54 on Wednesday 23 September. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddEventScreenshotTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private fun show(form: EditorForm, dark: Boolean, keyboard: Dp = 0.dp, savesTo: String = "Family calendar", child: Person? = null) {
        val flow = editorFlow(form, child?.id)
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
                    held = null,
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
        val form = editorForm(signedIn = mia.id, childOnly = mia.id).apply { updateTitle("Piano practice") }
        show(form, dark = true, child = mia)
        snap("add_event_who_child_dark")
    }

    /** Dinner tonight at 19:30 for 90 minutes, opened on Check with its pane still empty. */
    @Test
    fun changeEventDark() {
        show(editorDinnerOf(Duration.ofMinutes(90)), dark = true, savesTo = "Family")
        snap("change_event_dark")
    }
}
