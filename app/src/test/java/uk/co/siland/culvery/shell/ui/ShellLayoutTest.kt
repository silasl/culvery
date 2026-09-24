package uk.co.siland.culvery.shell.ui

import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.SessionUi

/** Needs real text metrics: legacy Robolectric graphics fakes glyph widths and font ascents. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val at = LocalDateTime.of(2026, 9, 23, 11, 54)

    @Test
    fun dateBaselineSits44dpBelowClockBaseline() {
        compose.setContent {
            CulveryTheme(dark = true) { HomeScreen(at, emptyList()) }
        }
        val clock = compose.onNodeWithTag("home_clock")
        val date = compose.onNodeWithTag("home_date")
        val clockBaseline = clock.getUnclippedBoundsInRoot().top + clock.getAlignmentLinePosition(LastBaseline)
        val dateBaseline = date.getUnclippedBoundsInRoot().top + date.getAlignmentLinePosition(FirstBaseline)
        assertThat((dateBaseline - clockBaseline).value).isWithin(1f).of(44f)
    }

    private fun statusBar(session: SessionUi?, onSignOut: () -> Unit = {}) = compose.setContent {
        CulveryTheme(dark = true) { StatusBar(at, dark = true, previewing = false, session = session, onSignOut = onSignOut, onToggleThemePreview = {}) }
    }

    @Test
    fun statusBarShowsWhoIsSignedInAndTheirRole() {
        statusBar(SessionUi("Alex", "Admin"))
        compose.onNodeWithText("Alex · Admin").assertExists()
        compose.onNodeWithText("Sign out").assertExists()
    }

    @Test
    fun signOutLocks() {
        var signedOut = false
        statusBar(SessionUi("Alex", "Admin")) { signedOut = true }
        compose.onNodeWithTag("status_sign_out").performClick()
        assertThat(signedOut).isTrue()
    }

    @Test
    fun signOutAnnouncesItsAction() {
        statusBar(SessionUi("Alex", "Admin"))
        val onClick = compose.onNodeWithTag("status_sign_out").fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertThat(onClick.label).isEqualTo("Sign out")
    }

    @Test
    fun nothingAboutSigningInShowsWhenLocked() {
        statusBar(null)
        compose.onNodeWithTag("status_session").assertDoesNotExist()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }
}
