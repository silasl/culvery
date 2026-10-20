package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import kotlinx.coroutines.flow.MutableSharedFlow
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
import uk.co.siland.culvery.core.plugin.LocalTabReselected
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

    private fun event(
        id: Long,
        kind: DoorKind = DoorKind.RING,
        unrecognised: Boolean = false,
        snapshot: File? = null,
        at: Long = NOW - id * 60_000,
        device: String = "Front Door",
        seen: Boolean = false,
    ) = DoorEvent(id, at, kind, device, unrecognised, snapshot, seen)

    private fun show(days: DoorDays, status: String? = null, newIds: Set<Long> = emptySet()) =
        compose.setContent { CulveryTheme(dark = true) { DoorTab(days, london, status, onOpenSnapshot = { opened += it }, newIds = newIds) } }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun resumable() = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

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

    /** 9 §4.6: fails if the title block is missing, a device repeats, or motion counts as a new ring. */
    @Test
    fun theHeaderCountsNewRings() {
        show(DoorDays(listOf(event(1), event(2), event(3, DoorKind.MOTION, device = "Back Garden")), emptyList()))
        compose.onNodeWithText("Door").assertIsDisplayed()
        compose.onNodeWithText("Front Door · Back Garden · 2 new rings").assertIsDisplayed()
        compose.onNodeWithTag("door_row_3_new").assertDoesNotExist()
        compose.assertAccessible()
    }

    /** Fails if the status line moves above the title, or the block's one gap doubles under it. */
    @Test
    fun theStoreStatusShowsUnderTheNewTitle() {
        show(DoorDays(emptyList(), emptyList()), status = "The tablet is offline. Check the Wi-Fi.")
        val title = compose.onNodeWithText("Door").getUnclippedBoundsInRoot()
        val status = compose.onNodeWithTag("door_status").getUnclippedBoundsInRoot()
        assertThat(status.top.value - title.bottom.value).isWithin(1f).of(15f)
        compose.assertAccessible()
    }

    /** One 15 dp gap under the title block, as on Lights and Music. */
    @Test
    fun theDayLabelSitsOneGapBelowTheHeader() {
        show(DoorDays(listOf(event(1)), emptyList()))
        val subtitle = compose.onNodeWithText("Front Door · 1 new ring").getUnclippedBoundsInRoot()
        val today = compose.onNodeWithTag("door_today").getUnclippedBoundsInRoot()
        assertThat(today.top.value - subtitle.bottom.value).isWithin(1f).of(15f)
    }

    /** 9 §4.6: fails if a row drops its time or its kind, uses the old words, or an unplaced notice doesn't say so. */
    @Test
    fun eachRowSaysTheTimeAndWhatHappened() {
        show(DoorDays(listOf(event(1), event(2, DoorKind.MOTION), event(3, unrecognised = true)), emptyList()))
        fun row(id: Long, vararg texts: String) = texts.forEach { compose.onNode(hasTestTag("door_row_$id") and hasAnyDescendant(hasText(it))).assertExists() }
        row(1, "14:01 · Someone rang", "Front Door")
        row(2, "14:00 · Movement", "Front Door")
        row(3, "13:59 · Notice from Ring", "Front Door · Culvery couldn’t tell what this was")
        compose.onNodeWithTag("door_yesterday").assertDoesNotExist()
        compose.assertAccessible()
    }

    /** Pin: a ring already seen before the tab opened carries no NEW. */
    @Test
    fun aRingSeenBeforeOpeningHasNoTag() {
        show(DoorDays(listOf(event(1, seen = true)), emptyList()))
        compose.onNodeWithTag("door_row_1").assertExists()
        compose.onNodeWithTag("door_row_1_new").assertDoesNotExist()
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

    /** 9 §4.6: fails if the thumbnail isn't 107 × 60 dp, its tap height falls under 48 dp, or it stops opening the snapshot. */
    @Test
    fun theThumbnailStillOpensTheSnapshot() {
        val withPicture = event(1, snapshot = File(folder.root, "1.jpg"))
        show(DoorDays(listOf(withPicture), emptyList()))
        compose.onNodeWithTag("door_thumb_1")
            .assertWidthIsEqualTo(107.dp)
            .assertHeightIsEqualTo(60.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertThat(opened).containsExactly(withPicture)
        compose.assertAccessible()
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

    /** 9 §6.8: fails if the rings unseen on opening carry no NEW, or opening leaves them unseen so the badge stays. */
    @Test
    fun newRingsCarryATag() {
        runBlocking {
            store.record(ring(key = "a"))
            store.record(ring(key = "b", at = NOW - 60_000))
        }
        compose.setContent { CulveryTheme(dark = true) { host(flowOf(october14)) } }
        waitFor { unseenRings() == 0 && exists("door_row_1") && exists("door_row_2") }
        listOf(1, 2).forEach { compose.onNodeWithTag("door_row_${it}_new").assertTextEquals("NEW").assert(hasContentDescription("new")) }
        compose.assertAccessible()
    }

    /** 5 §4.7, 9 §6.8: fails if a ring arriving while the tab is open is marked seen at once (no badge), or shows no NEW (isNew from newIds alone). */
    @Test
    fun aRingArrivingWhileOpenIsNewAndKeepsItsBadge() {
        compose.setContent { CulveryTheme(dark = true) { host(flowOf(october14)) } }
        compose.waitForIdle()
        runBlocking { store.record(ring()) }
        waitFor { exists("door_row_1") }
        compose.waitForIdle()
        assertThat(unseenRings()).isEqualTo(1)
        compose.onNodeWithTag("door_row_1_new").assertExists()
        compose.assertAccessible()
    }

    /** 9 §6.8: fails if NEW outlasts the visit, so a ring seen on one visit still says NEW on the next. */
    @Test
    fun newLastsOneVisit() {
        runBlocking { store.record(ring()) }
        val owner = resumable()
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { CulveryTheme(dark = true) { host(flowOf(october14)) } }
        }
        waitFor { unseenRings() == 0 && exists("door_row_1_new") }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        waitFor { !exists("door_row_1_new") }
        compose.onNodeWithTag("door_row_1").assertExists()
    }

    /** 9 §6.8: fails if a reselect replaces the visit's NEW rings instead of adding the ones that arrived meanwhile. */
    @Test
    fun reselectingTheTabKeepsNewForThatVisit() {
        val reselected = MutableSharedFlow<Unit>()
        runBlocking { store.record(ring(key = "a", at = NOW - 60_000)) }
        compose.setContent { CulveryTheme(dark = true) { CompositionLocalProvider(LocalTabReselected provides reselected) { host(flowOf(october14)) } } }
        waitFor { unseenRings() == 0 && exists("door_row_1_new") }
        runBlocking { store.record(ring(key = "b")) }
        waitFor { exists("door_row_2") }
        runBlocking { reselected.emit(Unit) }
        waitFor { unseenRings() == 0 }
        compose.onNodeWithTag("door_row_1_new").assertExists()
        compose.onNodeWithTag("door_row_2_new").assertExists()
    }

    /** 9 §6.8: fails if a reselect landing with the resume can take the visit's rings first and the resume then drops their NEW. */
    @Test
    fun aResumeThenAReselectBackToBackKeepsTheFirstSetsNew() {
        val reselected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        runBlocking { store.record(ring()) }
        val owner = resumable()
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalTabReselected provides reselected) {
                CulveryTheme(dark = true) { host(flowOf(october14)) }
            }
        }
        waitFor { exists("door_row_1") }
        compose.runOnUiThread {
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            assertThat(reselected.tryEmit(Unit)).isTrue()
        }
        waitFor { unseenRings() == 0 }
        compose.waitForIdle()
        compose.onNodeWithTag("door_row_1_new").assertExists()
    }

    /** 5 §4.7: fails if tapping the Door tab again while it is open leaves a ring that arrived meanwhile unseen. */
    @Test
    fun tappingTheOpenTabAgainMarksEverythingSeen() {
        val reselected = MutableSharedFlow<Unit>()
        compose.setContent { CulveryTheme(dark = true) { CompositionLocalProvider(LocalTabReselected provides reselected) { host(flowOf(october14)) } } }
        compose.waitForIdle()
        runBlocking { store.record(ring()) }
        waitFor { compose.onAllNodesWithTag("door_row_1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertThat(unseenRings()).isEqualTo(1)
        runBlocking { reselected.emit(Unit) }
        waitFor { unseenRings() == 0 }
    }

    /** Fails if a tab merely STARTED (behind another app) marks events seen, or if it never marks them once RESUMED. */
    @Test
    fun aTabLeftOpenBehindAnotherAppMarksNothingUntilItIsBack() {
        runBlocking { store.record(ring()) }
        val owner = resumable()
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
