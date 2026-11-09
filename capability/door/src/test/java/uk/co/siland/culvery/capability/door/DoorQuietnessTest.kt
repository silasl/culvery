package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.WallClock

/** 18 §7: the pads see the ring card, quiet hours and the doorbell's volume. */
@RunWith(AndroidJUnit4::class)
class DoorQuietnessTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val host = FakeAlertHost()
    private val settings by lazy { doorSettings(folder.root, scope) }
    private val alerts by lazy {
        DoorAlerts(host, {}, { DoorPrefs() }, FakeChime(), { ZoneId.of("Europe/London") }, WallClock { NOW }, scope, NightHours { false }, { null }, { null }, testSounds())
    }
    private val quiet by lazy { DoorQuietness(alerts, settings, householdZone(), WallClock { NOW }) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun ringShowingFollowsTheCard() = runTest(UnconfinedTestDispatcher()) {
        val seen = mutableListOf<Boolean>()
        val job = launch { quiet.ringShowing.toList(seen) }
        alerts.onRecorded(Recorded(DoorEvent(1, NOW, DoorKind.RING, "Front Door", false, null, false), isNew = true))
        alerts.dismissCard()
        job.cancel()
        assertThat(seen).containsExactly(false, true, false).inOrder()
    }

    private val local = Instant.ofEpochMilli(NOW).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)

    @Test
    fun quietNowReadsQuietHours() = runTest {
        settings.setConnected(true)
        settings.setQuietHours(on = true, from = local.minusHours(1), until = local.plusHours(1))
        awaiting("quiet hours to be on") { settings.current.first { it.quietOn && it.quietFrom == local.minusHours(1) } }
        assertThat(quiet.quietNow()).isTrue()
        settings.setQuietHours(on = false, from = local.minusHours(1), until = local.plusHours(1))
        awaiting("quiet hours to be off") { settings.current.first { !it.quietOn } }
        assertThat(quiet.quietNow()).isFalse()
    }

    @Test
    fun quietHoursNeedTheDoorSetUp() = runTest {
        settings.setQuietHours(on = true, from = local.minusHours(1), until = local.plusHours(1))
        awaiting("quiet hours to be on") { settings.current.first { it.quietOn && it.quietFrom == local.minusHours(1) } }
        assertThat(quiet.quietNow()).isFalse()
    }

    @Test
    fun volumeIsTheDoorbells() = runTest {
        assertThat(quiet.volume).isEqualTo(1.0f)
        settings.setVolume(0.3f)
        awaiting("the volume to be 0.3") { settings.current.first { it.volume == 0.3f } }
        assertThat(quiet.volume).isEqualTo(0.3f)
    }
}
