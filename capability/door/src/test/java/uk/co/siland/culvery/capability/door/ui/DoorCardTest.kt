package uk.co.siland.culvery.capability.door.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.picture
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

/** 5 §5.1: what, where and when, spoken at once; the snapshot when there is one; Got it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoorCardTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private var gotIt = 0

    private fun show(card: DoorCardUi) = compose.setContent { CulveryTheme(dark = true) { DoorCard(card, onGotIt = { gotIt++ }) } }

    @Test
    fun theCardSaysWhatWhereAndWhenUrgently() {
        show(DoorCardUi(unrecognised = false, device = "Front Door", time = "14:02", snapshot = null))
        compose.onNodeWithTag("door_card_words")
            .assert(hasContentDescription("Someone’s at the door, Front Door, 14:02"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
        compose.assertAccessible()
    }

    @Test
    fun anUnrecognisedAlertSaysRingAlertAndAMissingDeviceIsLeftOut() {
        show(DoorCardUi(unrecognised = true, device = "", time = "14:02", snapshot = null))
        compose.onNodeWithTag("door_card_words").assert(hasContentDescription("Ring alert, 14:02"))
    }

    @Test
    fun gotItCallsBack() {
        show(DoorCardUi(false, "Front Door", "14:02", null))
        compose.onNodeWithTag("door_got_it").performClick()
        assertThat(gotIt).isEqualTo(1)
    }

    @Test
    fun theSnapshotShowsWhenThereIsOne() {
        val file = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(640, 360)).saveTo(it) }
        show(DoorCardUi(false, "Front Door", "14:02", file))
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("door_snapshot").fetchSemanticsNodes().isNotEmpty() }
    }
}
