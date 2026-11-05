package uk.co.siland.culvery.capability.door

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.plugin.WallClock

/** A provider whose answers a test sets. */
internal class FakeDoorProvider(var installed: Boolean = true, var access: Boolean = true) : DoorProvider {
    override val descriptor = ProviderDescriptor("door.ring", "Ring", "doorbell", setOf(Feature.READ), connectHint = "Sign in as a Ring Shared User.")
    override fun appInstalled() = installed
    override fun accessAllowed() = access
    override fun accessScreens() = listOf(Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS"), Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
}

@RunWith(AndroidJUnit4::class)
class DoorCapabilityTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = doorDb()
    private val store by lazy { DoorStore(db, File(folder.root, "door")) }
    private val settings by lazy { doorSettings(folder.root, scope) }
    private val clock by lazy { HouseholdClock(flowOf(ZoneId.of("Europe/London")), WallClock { NOW }, scope, flowOf(NOW)) }

    private fun alerts() = DoorAlerts(
        FakeAlertHost(), {}, { DoorPrefs() }, FakeChime(), { ZoneId.of("Europe/London") }, WallClock { NOW }, scope, NightHours { false }, { null }, { null }, testSounds(),
    )

    private fun capability(providers: Set<DoorProvider>) = DoorCapability(
        store, settings, providers, clock, householdZone(),
        DoorHealth(providers.firstOrNull(), settings, MutableStateFlow(true), MutableStateFlow(true), scope), FakeChime(), testSounds(), RecordingToaster(), FakeShowing(),
        TestAccess(Role.ADMIN), alerts(), scope,
    )

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    /** 5 §3.1: Door, slot 50, the doorbell; a tab once Ring is connected, and never without a provider. */
    @Test
    fun theTabAppearsOnceRingIsConnected() = runTest {
        val door = capability(setOf(FakeDoorProvider()))
        assertThat(listOf(door.id, door.label, door.icon, door.order)).containsExactly("door", "Door", "doorbell", 50).inOrder()
        assertThat(door.hasTab.first()).isFalse()
        settings.setConnected(true)
        door.hasTab.first { it }
        assertThat(capability(emptySet()).hasTab.first()).isFalse()
    }

    @Test
    fun theBadgeFollowsWhatIsUnseenToday() = runTest {
        val door = capability(setOf(FakeDoorProvider()))
        assertThat(door.badge.first()).isNull()
        store.record(ring())
        door.badge.first { it != null }
        assertThat(door.badge.first()).isEqualTo(RailBadge.Count(1, "1 unseen ring"))
    }

    /** 15a §3.1: with 30 days kept the tab shows them all, but the badge still counts today and yesterday. */
    @Test
    fun theBadgeIgnoresOlderUnseenRings() = runTest {
        val thirty = DoorStore(db, File(folder.root, "thirty"), keepFrom = { keepFrom(dateIn(it, ZoneId.of("Europe/London")), ZoneId.of("Europe/London"), KeepDays.THIRTY) })
        val threeDaysAgo = NOW - 3 * 24 * 60 * 60_000L
        thirty.record(ring(key = "old", at = threeDaysAgo, whenMillis = threeDaysAgo))
        val door = DoorCapability(
            thirty, settings, setOf(FakeDoorProvider()), clock, householdZone(),
            DoorHealth(FakeDoorProvider(), settings, MutableStateFlow(true), MutableStateFlow(true), scope), FakeChime(), testSounds(), RecordingToaster(), FakeShowing(),
            TestAccess(Role.ADMIN), alerts(), scope,
        )
        assertThat(door.badge.first()).isNull()
        thirty.record(ring(key = "today"))
        door.badge.first { it != null }
        assertThat(door.badge.first()).isEqualTo(RailBadge.Count(1, "1 unseen ring"))
        assertThat(thirty.events.first()).hasSize(2)
    }

    /** 5 §5.4: Settings › Door, admin-only like the rest of Settings, only where a doorbell provider is installed. Fails if the page is missing, mislabelled, misordered, or offered with no provider. */
    @Test
    fun settingsHasADoorPage() {
        val page = capability(setOf(FakeDoorProvider())).settingsPages().single()
        assertThat(listOf(page.id, page.title, page.order)).containsExactly("door", "Door", 500).inOrder()
        assertThat(capability(emptySet()).settingsPages()).isEmpty()
    }
}
