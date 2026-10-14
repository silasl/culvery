package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.door.DoorDays
import uk.co.siland.culvery.capability.door.DoorEvent
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorStore
import uk.co.siland.culvery.capability.door.NOW
import uk.co.siland.culvery.capability.door.aPicture
import uk.co.siland.culvery.capability.door.doorDb
import uk.co.siland.culvery.capability.door.ring
import uk.co.siland.culvery.capability.door.startOfYesterday
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

@RunWith(AndroidJUnit4::class)
class DoorTabTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val london = ZoneId.of("Europe/London")
    private val october14 = LocalDate.of(2026, 10, 14)
    private val db = doorDb()
    private val store by lazy { DoorStore(db, File(folder.root, "door")) }
    private val opened = mutableListOf<DoorEvent>()

    @After
    fun tearDown() = db.close()

    /** Robolectric's main looper runs only when the test idles it, so each check lets the UI and its effects run first. */
    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(5_000) {
        compose.waitForIdle()
        condition()
    }

    private fun unseenRings() = runBlocking { store.unseen(startOfYesterday(october14, london)).first().rings }

    private fun event(id: Long, kind: DoorKind = DoorKind.RING, unrecognised: Boolean = false, snapshot: File? = null, at: Long = NOW - id * 60_000) =
        DoorEvent(id, at, kind, "Front Door", unrecognised, snapshot, seen = false)

    private fun show(days: DoorDays, status: String? = null) =
        compose.setContent { CulveryTheme(dark = true) { DoorTab(days, london, status, onOpenSnapshot = { opened += it }) } }

    /** A sheet host that draws what it is shown, as the shell's overlay layer does. */
    private class DrawingOverlay : OverlayHost {
        var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        override fun show(closesWhenIdle: Boolean, content: @Composable () -> Unit) {
            this.content = content
        }
        override fun markEdited(edited: Boolean) = Unit
        override fun dismiss() {
            content = null
        }
    }

    @Composable
    private fun host(today: Flow<LocalDate>, overlay: DrawingOverlay = DrawingOverlay()) {
        CompositionLocalProvider(LocalOverlayHost provides overlay) {
            Box {
                DoorTabHost(store, today, flowOf(london), flowOf(null))
                overlay.content?.invoke()
            }
        }
    }

    /** Fails if a row drops its time, its kind, or the Ring alert wording for an unplaced ring, or the tab shows a Yesterday label with no rows. */
    @Test
    fun eachRowSaysTheTimeWhatAndWhere() {
        show(DoorDays(listOf(event(1), event(2, DoorKind.MOTION), event(3, unrecognised = true)), emptyList()))
        compose.onNodeWithText("14:01 · Ring").assertExists()
        compose.onNodeWithText("14:00 · Motion").assertExists()
        compose.onNodeWithText("13:59 · Ring alert").assertExists()
        compose.onNodeWithTag("door_yesterday").assertDoesNotExist()
        compose.assertAccessible()
    }

    /** Fails if an empty Today shows no sentence, the sentence changes, or an empty Today hides Yesterday. */
    @Test
    fun anEmptyTodaySaysSo() {
        show(DoorDays(emptyList(), listOf(event(1))))
        compose.onNodeWithTag("door_empty").assertTextEquals("No rings or motion today.")
        compose.onNodeWithTag("door_yesterday").assertExists()
    }

    /** Fails if a status passed to the tab is not drawn. */
    @Test
    fun theStatusLineShowsOnlyWhenSomethingIsWrong() {
        show(DoorDays(emptyList(), emptyList()), status = "Culvery can’t see Ring’s notifications. Allow notification access in Settings › Door.")
        compose.onNodeWithTag("door_status").assertExists()
    }

    /** Fails if the status line is drawn when there is no status. */
    @Test
    fun noStatusNoLine() {
        show(DoorDays(emptyList(), emptyList()))
        compose.onNodeWithTag("door_status").assertDoesNotExist()
    }

    /** Fails if tapping a thumbnail does not hand its event to the opener. */
    @Test
    fun aThumbnailOpensTheSnapshot() {
        val withPicture = event(1, snapshot = File(folder.root, "1.jpg"))
        show(DoorDays(listOf(withPicture), emptyList()))
        compose.onNodeWithTag("door_thumb_1").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(opened).containsExactly(withPicture)
    }

    /** Fails if a row with a snapshot thumbnail has an unlabelled or unnamed control, or the thumbnail's tap area is under 48 dp high. */
    @Test
    fun aRowWithAThumbnailIsAccessible() {
        show(DoorDays(listOf(event(1, snapshot = File(folder.root, "1.jpg"))), emptyList()))
        compose.assertAccessible()
    }

    /** Through the host: fails if the thumbnail does not put the full-size sheet in the overlay host. */
    @Test
    fun theHostOpensTheSnapshotSheet() {
        runBlocking { store.record(ring(snapshot = aPicture)) }
        compose.setContent { CulveryTheme(dark = true) { host(flowOf(october14)) } }
        waitFor { compose.onAllNodesWithTag("door_row_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("door_thumb_1").performClick()
        waitFor { compose.onAllNodesWithTag("door_snapshot_sheet").fetchSemanticsNodes().isNotEmpty() }
    }

    /** 5 §4.7: fails if opening the tab leaves an event unseen. */
    @Test
    fun openingTheTabMarksEverythingSeen() {
        runBlocking { store.record(ring()) }
        compose.setContent { CulveryTheme(dark = true) { host(flowOf(october14)) } }
        waitFor { unseenRings() == 0 }
    }

    /** 5 §4.7: fails if a ring arriving while the tab is already open is marked seen at once, so it never badges. */
    @Test
    fun aRingArrivingWhileTheTabIsOpenStaysUnseen() {
        compose.setContent { CulveryTheme(dark = true) { host(flowOf(october14)) } }
        compose.waitForIdle()
        runBlocking { store.record(ring()) }
        waitFor { compose.onAllNodesWithTag("door_row_1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertThat(unseenRings()).isEqualTo(1)
    }

    /** Fails if a tab merely STARTED (behind another app) marks events seen, or if it never marks them once RESUMED. */
    @Test
    fun aTabLeftOpenBehindAnotherAppMarksNothingUntilItIsBack() {
        runBlocking { store.record(ring()) }
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { CulveryTheme(dark = true) { host(flowOf(october14)) } }
        }
        waitFor { compose.onAllNodesWithTag("door_row_1").fetchSemanticsNodes().isNotEmpty() }
        assertThat(unseenRings()).isEqualTo(1)
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        waitFor { unseenRings() == 0 }
    }

    /** Fails if the tab keeps the day it opened on after the household's date moves on. */
    @Test
    fun theTabFollowsTheHouseholdsDate() {
        runBlocking { store.record(ring(at = LocalDateTime.of(2026, 10, 14, 23, 59).atZone(london).toInstant().toEpochMilli())) }
        val today = MutableStateFlow(october14)
        compose.setContent { CulveryTheme(dark = true) { host(today) } }
        waitFor { compose.onAllNodesWithTag("door_row_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("door_yesterday").assertDoesNotExist()
        today.value = october14.plusDays(1)
        waitFor { compose.onAllNodesWithTag("door_yesterday").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("door_empty").assertExists()
    }
}
