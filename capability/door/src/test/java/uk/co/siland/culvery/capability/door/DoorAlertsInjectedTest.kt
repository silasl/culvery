package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.WallClock

/** 8 §2.7, ruling 7: the injected DoorAlerts reads the night hours from core, and "not known yet" counts as night. */
@RunWith(AndroidJUnit4::class)
class DoorAlertsInjectedTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = doorDb()
    private val host = FakeAlertHost()
    private val chime = FakeChime()

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun alertsAt(night: Boolean?) = DoorAlerts(
        host,
        DoorStore(db, File(folder.root, "door")),
        doorSettings(folder.root, scope),
        chime,
        householdZone(),
        WallClock { NOW },
        scope,
        NightHours { night },
        object : HouseMode { override val holiday = MutableStateFlow<Boolean?>(null) },
        FakeShowing(),
    )

    /** Fails if a ring just after a restart, when the night hours aren't known yet, raises a card or a chime. */
    @Test
    fun aRingJustAfterARestartAtNightIsStored() = runTest {
        alertsAt(null).onRecorded(ring0())
        assertThat(host.shown).isEqualTo(0)
        assertThat(chime.volumes).isEmpty()
    }

    /** Fails if the injected constructor stops the day's ring from alerting. */
    @Test
    fun aRingByDayThroughTheInjectedConstructorAlerts() = runTest {
        alertsAt(false).onRecorded(ring0())
        assertThat(host.shown).isEqualTo(1)
    }

    private fun ring0() = Recorded(DoorEvent(1, NOW, DoorKind.RING, "Front Door", false, null, seen = false), isNew = true)
}
