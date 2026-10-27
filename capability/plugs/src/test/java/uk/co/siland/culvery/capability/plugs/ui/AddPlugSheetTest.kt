package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.LAMP
import uk.co.siland.culvery.capability.plugs.PlugCandidate
import uk.co.siland.culvery.capability.plugs.PlugCandidates
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 12 §4.2, §4.3: Add a plug lists the switches Home Assistant has that aren't on the tab yet. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddPlugSheetTest {
    @get:Rule val compose = createComposeRule()
    private val picked = mutableListOf<PlugCandidate>()
    private var closed = 0

    private fun show(candidates: PlugCandidates?, added: List<String> = emptyList()) = compose.setContent {
        CulveryTheme(dark = true) { AddPlugSheet(candidates, added, onPick = { picked += it }, onClose = { closed++ }) }
    }

    private fun none(tag: String) = compose.onAllNodesWithTag(tag).assertCountEquals(0)

    /** Fails if the list shows a plug already added, or leaves out one that isn't. */
    @Test
    fun listsOnlySwitchesNotYetAdded() {
        show(PlugCandidates.Found(CANDIDATES), added = listOf(LAMP))
        compose.onNodeWithTag("add_plug_0").assertTextEquals("Fan")
        compose.onNodeWithTag("add_plug_1").assertTextEquals("Desk")
        compose.onNodeWithTag("add_plug_2").assertTextEquals("Lamp LED")
        none("add_plug_3")
        none("add_plug_none")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a pick isn't handed on once, or the sheet stays open. */
    @Test
    fun pickingAddsAndCloses() {
        show(PlugCandidates.Found(CANDIDATES), added = listOf(LAMP))
        compose.onNodeWithTag("add_plug_1").performClick()
        assertThat(picked).containsExactly(CANDIDATES[2])
        assertThat(closed).isEqualTo(1)
    }

    /** Fails if a Home Assistant with nothing to add doesn't say so. */
    @Test
    fun nothingToAddSaysSo() {
        show(PlugCandidates.Found(CANDIDATES.take(1)), added = listOf(LAMP))
        compose.onNodeWithTag("add_plug_none").assertTextEquals("Home Assistant has no other switches.")
    }

    /** Fails if a failed read doesn't show its line, or offers a plug. */
    @Test
    fun aFailureShowsItsLine() {
        show(PlugCandidates.Failed("Home Assistant can’t be reached."))
        compose.onNodeWithTag("add_plug_line").assertTextEquals("Home Assistant can’t be reached.")
        none("add_plug_0")
    }

    /** Fails if the sheet shows a list or "nothing to add" while the switches are still being read. */
    @Test
    fun whileReadingItShowsOnlyTheTitle() {
        show(null)
        compose.onNodeWithTag("add_plug_sheet").assertIsDisplayed()
        none("add_plug_none")
        none("add_plug_0")
    }

    /** Fails if the close button doesn't close. */
    @Test
    fun closeCloses() {
        show(null)
        compose.onNodeWithTag("sheet_close").performClick()
        assertThat(closed).isEqualTo(1)
    }
}
