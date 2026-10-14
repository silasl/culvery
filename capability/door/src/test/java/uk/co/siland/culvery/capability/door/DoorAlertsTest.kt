package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.capability.door.ui.DoorCardUi
import uk.co.siland.culvery.core.plugin.WallClock

/** 5 §4.6–4.7: card and chime for a new ring in front; record only otherwise; 2 minutes, Got it, quiet hours, 10 seconds. */
class DoorAlertsTest {
    private val london = ZoneId.of("Europe/London")
    private val host = FakeAlertHost()
    private val chime = FakeChime()
    private val seen = mutableListOf<Long>()
    private var prefs = DoorPrefs()

    // NOW is 14:02 in London: not quiet.
    private var start = NOW

    private fun TestScope.alerts() =
        DoorAlerts(host, { seen += it }, { prefs }, chime, { london }, WallClock { start + testScheduler.currentTime }, backgroundScope)

    private fun TestScope.rung(id: Long, isNew: Boolean = true, device: String = "Front Door", unrecognised: Boolean = false, snapshot: File? = null) =
        Recorded(DoorEvent(id, start + testScheduler.currentTime, DoorKind.RING, device, unrecognised, snapshot, seen = false), isNew)

    @Test
    fun aNewRingInFrontShowsTheCardAndChimesAtTheVolume() = runTest {
        prefs = DoorPrefs(volume = 0.4f)
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        assertThat(host.shown).isEqualTo(1)
        assertThat(alerts.showing.value).isEqualTo(ShowingRing(1, DoorCardUi(false, "Front Door", "14:02", null)))
        assertThat(chime.volumes).containsExactly(0.4f)
    }

    @Test
    fun motionNeverAlerts() = runTest {
        val alerts = alerts()
        alerts.onRecorded(Recorded(DoorEvent(1, NOW, DoorKind.MOTION, "Front Door", false, null, false), isNew = true))
        assertThat(host.shown).isEqualTo(0)
        assertThat(chime.volumes).isEmpty()
    }

    /** 5 §4.6: the 10 seconds run from a chime that played. Fails if a chime that failed to start holds back the next ring's. */
    @Test
    fun aChimeThatFailedToStartDoesNotHoldBackTheNextOne() = runTest {
        val alerts = alerts()
        chime.starts = false
        alerts.onRecorded(rung(1))
        chime.starts = true
        advanceTimeBy(1)
        alerts.onRecorded(rung(2))
        assertThat(chime.volumes).hasSize(2)
        advanceTimeBy(CHIME_GAP_MS - 1)
        alerts.onRecorded(rung(3))
        assertThat(chime.volumes).hasSize(2)
    }

    @Test
    fun anUnrecognisedAlertShowsAsARingAlertAndChimes() = runTest {
        val alerts = alerts()
        alerts.onRecorded(rung(1, unrecognised = true))
        assertThat(alerts.showing.value?.card?.unrecognised).isTrue()
        assertThat(chime.volumes).hasSize(1)
    }

    /** 5 §2.9, §4.6: a second ring refreshes the card; the chime waits 10 seconds after the last. */
    @Test
    fun aSecondRingRefreshesTheCardAndChimesAgainOnlyAfterTenSeconds() = runTest {
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        advanceTimeBy(CHIME_GAP_MS - 1)
        alerts.onRecorded(rung(2, device = "Back Door"))
        assertThat(alerts.showing.value?.eventId).isEqualTo(2)
        assertThat(alerts.showing.value?.card?.device).isEqualTo("Back Door")
        assertThat(chime.volumes).hasSize(1)
        advanceTimeBy(1)
        alerts.onRecorded(rung(3))
        assertThat(chime.volumes).hasSize(2)
        assertThat(host.shown).isEqualTo(1)
    }

    @Test
    fun theCardClosesAfterTwoMinutesAndItsRingStaysUnseen() = runTest {
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        advanceTimeBy(CARD_MS - 1)
        runCurrent()
        assertThat(alerts.showing.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(alerts.showing.value).isNull()
        assertThat(host.dismissed).isEqualTo(1)
        assertThat(seen).isEmpty()
    }

    @Test
    fun aSecondRingRestartsTheTwoMinutes() = runTest {
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        advanceTimeBy(60_000)
        alerts.onRecorded(rung(2))
        advanceTimeBy(CARD_MS - 1)
        runCurrent()
        assertThat(alerts.showing.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(alerts.showing.value).isNull()
    }

    /** Ring 2 holds the lock across card 1's 2 minutes: the timer, queued behind it, must not close ring 2's card. */
    @Test
    fun aRingHoldingTheLockAsTheTwoMinutesEndKeepsItsCard() = runTest {
        var gate: CompletableDeferred<Unit>? = null
        val alerts = DoorAlerts(host, { seen += it }, { prefs }, chime, { gate?.await(); london }, WallClock { start + testScheduler.currentTime }, backgroundScope)
        alerts.onRecorded(rung(1))
        advanceTimeBy(CARD_MS - 1_000)
        gate = CompletableDeferred()
        backgroundScope.launch { alerts.onRecorded(rung(2)) }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertThat(alerts.showing.value?.eventId).isEqualTo(2)
        assertThat(host.dismissed).isEqualTo(0)
        assertThat(host.content).isNotNull()
    }

    @Test
    fun gotItClosesTheCardAndMarksItsRingSeen() = runTest {
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        alerts.gotIt(1)
        runCurrent()
        assertThat(alerts.showing.value).isNull()
        assertThat(host.dismissed).isEqualTo(1)
        assertThat(seen).containsExactly(1L)
    }

    /** A Got it meant for a card a new ring has just replaced marks that ring seen and leaves the new card up. */
    @Test
    fun aLateGotItLeavesTheNewCard() = runTest {
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        alerts.onRecorded(rung(2))
        alerts.gotIt(1)
        runCurrent()
        assertThat(alerts.showing.value?.eventId).isEqualTo(2)
        assertThat(seen).containsExactly(1L)
    }

    /** 5 §2.6: in quiet hours the card still shows; no chime. 22:00 BST. */
    @Test
    fun quietHoursKeepTheCardButNotTheChime() = runTest {
        start = NOW + 8 * 60 * 60_000L - 2 * 60_000L
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        assertThat(host.shown).isEqualTo(1)
        assertThat(chime.volumes).isEmpty()
    }

    /** 5 §2.7: stored while Culvery is behind another app; coming back raises nothing. */
    @Test
    fun aRingInTheBackgroundShowsNoCardWhenCulveryReturns() = runTest {
        host.inFront.value = false
        val alerts = alerts()
        alerts.onRecorded(rung(1))
        host.inFront.value = true
        runCurrent()
        assertThat(host.shown).isEqualTo(0)
        assertThat(chime.volumes).isEmpty()
        assertThat(seen).isEmpty()
    }

    /** 5 §4.3: Ring's text post, then the same notification with the snapshot: one card, its picture, one chime, the same 2 minutes. */
    @Test
    fun aSnapshotRepostUpdatesTheShowingCardWithoutAChime() = runTest {
        val alerts = alerts()
        val first = rung(1)
        alerts.onRecorded(first)
        advanceTimeBy(30_000)
        alerts.onRecorded(Recorded(first.event.copy(snapshot = File("1.jpg")), isNew = false))
        assertThat(alerts.showing.value?.card?.snapshot).isEqualTo(File("1.jpg"))
        assertThat(chime.volumes).hasSize(1)
        assertThat(host.shown).isEqualTo(1)
        advanceTimeBy(CARD_MS - 30_000 - 1)
        runCurrent()
        assertThat(alerts.showing.value).isNotNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(alerts.showing.value).isNull()
    }
}
