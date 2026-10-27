package uk.co.siland.culvery.capability.door.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.DOOR_ID
import uk.co.siland.culvery.capability.door.DoorAlerts
import uk.co.siland.culvery.capability.door.DoorEvent
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorPrefs
import uk.co.siland.culvery.capability.door.FakeAlertHost
import uk.co.siland.culvery.capability.door.FakeChime
import uk.co.siland.culvery.capability.door.NOW
import uk.co.siland.culvery.capability.door.Recorded
import uk.co.siland.culvery.capability.door.cardLine
import uk.co.siland.culvery.capability.door.picture
import uk.co.siland.culvery.core.plugin.LocalAlertHost
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 5 §5.1, 9 §4.3: what, where and when, spoken at once; the snapshot when there is one; Got it and Door history; Music lowered. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoorCardTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private var gotIt = 0

    private fun show(card: DoorCardUi, lowered: Boolean = false) =
        compose.setContent { CulveryTheme(dark = true) { DoorCard(card, onGotIt = { gotIt++ }, lowered = lowered) } }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun theCardSaysWhatWhereAndWhenUrgently() {
        show(DoorCardUi(unrecognised = false, device = "Front Door", time = "14:02", snapshot = null))
        compose.onNodeWithTag("door_card_words")
            .assert(hasContentDescription("Someone’s at the door, Front Door, 14:02, just now"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
        compose.assertAccessible()
    }

    @Test
    fun anUnrecognisedAlertSaysNoticeFromRingAndAMissingDeviceIsLeftOut() {
        show(DoorCardUi(unrecognised = true, device = "", time = "14:02", snapshot = null))
        compose.onNodeWithTag("door_card_words").assert(hasContentDescription("Notice from Ring, 14:02, just now"))
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

    /** 9 §6.3: Fails if the chip shows while nothing was turned down, or is missing when music was. */
    @Test
    fun musicLoweredShowsOnlyWhenLowered() {
        var lowered by mutableStateOf(false)
        compose.setContent { CulveryTheme(dark = true) { DoorCard(DoorCardUi(false, "Front Door", "11:54", null), onGotIt = {}, lowered = lowered) } }
        compose.onNodeWithText("Music lowered").assertDoesNotExist()
        lowered = true
        compose.onNodeWithText("Music lowered").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Handoff 2a: Fails if the line drops "just now", or TalkBack doesn't say it. */
    @Test
    fun theLineSaysJustNow() {
        assertThat(cardLine("Front Door", "11:54")).isEqualTo("Front Door · 11:54 · just now")
        assertThat(cardLine("", "11:54")).isEqualTo("11:54 · just now")
        show(DoorCardUi(false, "Front Door", "11:54", null))
        compose.onNodeWithTag("door_card_words").assert(hasContentDescription("Someone’s at the door, Front Door, 11:54, just now"))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Handoff 2a: Fails if either button isn't Large, Got it isn't twice Door history's width, or the › is read out. */
    @Test
    fun doorHistoryIsQuietAndGotItPrimary() {
        show(DoorCardUi(false, "Front Door", "11:54", null))
        val gotIt = bounds("door_got_it")
        val history = bounds("door_history")
        assertThat((gotIt.bottom - gotIt.top).value).isWithin(0.5f).of(60f)
        assertThat((history.bottom - history.top).value).isWithin(0.5f).of(60f)
        assertThat((gotIt.right - gotIt.left).value).isWithin(2f).of(2 * (history.right - history.left).value)
        assertThat(gotIt.left).isLessThan(history.left)
        compose.onNodeWithTag("door_history").assertTextEquals("Door history")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Handoff 2a: Fails if the card is wider than 747 dp or the picture doesn't fill its width at 16:9. */
    @Test
    fun theSnapshotFillsTheCardsWidth() {
        val file = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(640, 360)).saveTo(it) }
        show(DoorCardUi(false, "Front Door", "11:54", file))
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("door_snapshot").fetchSemanticsNodes().isNotEmpty() }
        val card = bounds("door_card")
        val snapshot = bounds("door_snapshot")
        assertThat((card.right - card.left).value).isWithin(0.5f).of(747f)
        assertThat((snapshot.right - snapshot.left).value).isWithin(0.5f).of(747f - 2 * 24f)
        assertThat((snapshot.bottom - snapshot.top).value).isWithin(0.5f).of((747f - 2 * 24f) * 9f / 16f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §4.3: Fails if Door history leaves the sheet open, marks the ring seen, leaves the card up, or doesn't open the Door tab. */
    @Test
    fun doorHistoryClosesTheCardAndOpensTheTab() {
        val scope = TestScope()
        val host = FakeAlertHost()
        val seen = mutableListOf<Long>()
        val alerts = DoorAlerts(
            host, { seen += it }, { DoorPrefs() }, FakeChime(), { ZoneId.of("Europe/London") }, WallClock { NOW }, scope.backgroundScope, NightHours { false }, { null },
        )
        scope.launch { alerts.onRecorded(Recorded(DoorEvent(1, NOW, DoorKind.RING, "Front Door", false, null, seen = false), isNew = true)) }
        scope.runCurrent()
        val navigator = RecordingNavigator()
        val overlay = TestOverlay().apply { show {} }
        compose.setContent {
            CompositionLocalProvider(LocalAlertHost provides host, LocalOverlayHost provides overlay, LocalShellNavigator provides navigator) {
                CulveryTheme(dark = true) { DoorCardHost(alerts) }
            }
        }
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("door_history").performClick()
        scope.runCurrent()
        assertThat(overlay.content).isNull()
        assertThat(alerts.showing.value).isNull()
        assertThat(host.dismissed).isEqualTo(1)
        assertThat(seen).isEmpty()
        assertThat(navigator.tabs).containsExactly(DOOR_ID)
    }
}
