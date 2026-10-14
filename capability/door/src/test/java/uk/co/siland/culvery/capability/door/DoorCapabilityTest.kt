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
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.HouseholdClock
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
    private val settings by lazy { DoorSettings(doorSettingsStore(scope) { File(folder.root, "s.preferences_pb") }, scope) }
    private val clock by lazy { HouseholdClock(flowOf(ZoneId.of("Europe/London")), WallClock { NOW }, scope, flowOf(NOW)) }

    private fun capability(providers: Set<DoorProvider>) = DoorCapability(
        store, settings, providers, clock, householdZone(),
        DoorHealth(providers.firstOrNull(), settings, MutableStateFlow(true), MutableStateFlow(true), scope), FakeChime(), RecordingToaster(),
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

    /** 5 §5.4: Settings › Door, admin-only like the rest of Settings, only where a doorbell provider is installed. Fails if the page is missing, mislabelled, misordered, or offered with no provider. */
    @Test
    fun settingsHasADoorPage() {
        val page = capability(setOf(FakeDoorProvider())).settingsPages().single()
        assertThat(listOf(page.id, page.title, page.order)).containsExactly("door", "Door", 500).inOrder()
        assertThat(capability(emptySet()).settingsPages()).isEmpty()
    }
}
