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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.SessionChip

/** Needs real text metrics: legacy Robolectric graphics fakes glyph widths and font ascents. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dateBaselineSits44dpBelowClockBaseline() {
        compose.setContent {
            CulveryTheme(dark = true) { HomeScreen(LocalDateTime.of(2026, 9, 23, 11, 54), emptyList()) }
        }
        val clock = compose.onNodeWithTag("home_clock")
        val date = compose.onNodeWithTag("home_date")
        val clockBaseline = clock.getUnclippedBoundsInRoot().top + clock.getAlignmentLinePosition(LastBaseline)
        val dateBaseline = date.getUnclippedBoundsInRoot().top + date.getAlignmentLinePosition(FirstBaseline)
        assertThat((dateBaseline - clockBaseline).value).isWithin(1f).of(44f)
    }

    @Test
    fun sessionChipShowsAdminInFull() = assertChipNotEllipsised("Admin")

    @Test
    fun sessionChipShowsAlexInFull() = assertChipNotEllipsised("Alex")

    @Test
    fun tappingTheSessionChipLocks() {
        var locked = false
        compose.setContent {
            CulveryTheme(dark = true) {
                NavRail(emptyList(), HOME_TAB_ID, SessionChip("Alex", 0xFF4CB387), {}, {}, { locked = true })
            }
        }
        compose.onNodeWithTag("rail_session").performClick()
        assertThat(locked).isTrue()
    }

    @Test
    fun sessionChipAnnouncesLockAsItsClickAction() {
        compose.setContent {
            CulveryTheme(dark = true) {
                NavRail(emptyList(), HOME_TAB_ID, SessionChip("Alex", 0xFF4CB387), {}, {}, {})
            }
        }
        val onClick = compose.onNodeWithTag("rail_session").fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertThat(onClick.label).isEqualTo("Lock")
    }

    private fun assertChipNotEllipsised(name: String) {
        compose.setContent {
            CulveryTheme(dark = true) {
                NavRail(emptyList(), HOME_TAB_ID, SessionChip(name, 0xFF4CB387), {}, {}, {})
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(name, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(layouts.single().isLineEllipsized(0)).isFalse()
    }
}
