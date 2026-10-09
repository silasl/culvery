package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.AppContent
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage

/** 4d-2 §4.3: whatever an overlay covers is out of TalkBack's reach; the toasts never are. */
@RunWith(AndroidJUnit4::class)
class ModalLayersTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()
    private var pinShowing by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var toast by mutableStateOf<ToastMessage?>(null)

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) {
            ShellLayers(
                overlay = overlay,
                toast = toast,
                onToastHidden = {},
                pinShowing = pinShowing,
                pinPad = { if (pinShowing) Box(Modifier.fillMaxSize().semantics { paneTitle = "Enter your PIN" }) { Text("Pad key") } },
                activity = FakeUserActivity(),
                onTouch = {},
            ) {
                AppContent(
                    complete = true,
                    settingsOpen = settingsOpen,
                    overlay = overlay,
                    wizard = {},
                    shell = { Text("Home", Modifier.testTag("rail_home").hhClickable({})) },
                    settings = { Box(Modifier.fillMaxSize().semantics { paneTitle = "Settings" }) { Text("Settings page") } },
                    onHomeDrawn = {},
                )
            }
        }
    }

    private fun showSheet() = compose.runOnIdle {
        overlay.show { HhSheet(PaddingValues(), Modifier.semantics { paneTitle = "Event details" }) { Text("Sheet body") } }
    }

    private fun pane(title: String) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title))

    @Test
    fun aSheetHidesTheShellUntilItCloses() {
        show()
        compose.onNodeWithTag("rail_home").assertExists()
        showSheet()
        compose.onNodeWithTag("rail_home").assertDoesNotExist()
        pane("Event details").assertExists()
        compose.runOnIdle { overlay.dismiss() }
        compose.onNodeWithTag("rail_home").assertExists()
    }

    @Test
    fun settingsHidesTheShell() {
        show()
        settingsOpen = true
        compose.onNodeWithTag("rail_home").assertDoesNotExist()
        pane("Settings").assertExists()
        compose.onNodeWithText("Settings page").assertExists()
    }

    @Test
    fun thePinPadHidesTheSheetAndTheShellButNeverTheToast() {
        show()
        showSheet()
        toast = ToastMessage(1, "Event added", "info")
        pinShowing = true
        compose.onNodeWithText("Sheet body").assertDoesNotExist()
        compose.onNodeWithTag("rail_home").assertDoesNotExist()
        pane("Enter your PIN").assertExists()
        compose.onNodeWithTag("toast").assertExists()
    }
}
