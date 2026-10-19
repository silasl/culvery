package uk.co.siland.culvery.capability.door

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.settingsDataStore

/** 5 §5.5, §6: checked on each return to the front and whenever Android binds or unbinds the listener. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DoorHealthTest {
    @get:Rule val folder = TemporaryFolder()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { doorSettings(folder.root, io) }
    private val provider = FakeDoorProvider()
    private val bound = MutableStateFlow<Boolean?>(null)
    private val inFront = MutableStateFlow(true)

    @After
    fun tearDown() = io.cancel()

    private fun TestScope.health() = DoorHealth(provider, settings, bound, inFront, backgroundScope)

    private val accessOff = "Culvery can’t see Ring’s notifications. Allow notification access in Settings › Door."

    /** Fails if the status speaks before Ring is connected. */
    @Test
    fun nothingIsSaidBeforeRingIsConnected() = runTest {
        provider.installed = false
        provider.access = false
        val health = health()
        health.checks.first { it != null }
        assertThat(health.status.first()).isNull()
    }

    /** 5 §5.5: both steps done connects Ring, whether or not Settings › Door is open. Fails if the connection is never saved. */
    @Test
    fun bothStepsDoneConnectsRing() = runTest {
        // The health runs on a real dispatcher here: the save goes through DataStore's, which the test scheduler doesn't drive.
        DoorHealth(provider, settings, bound, inFront, io)
        withContext(Dispatchers.Default) { withTimeout(5_000) { settings.current.first { it.connected } } }
    }

    /** A save that failed is tried again on the next refresh, though the checks are unchanged. Fails if only a change in the checks retries it. */
    @Test
    fun aConnectionThatFailedToSaveIsSavedOnTheNextRefresh() = runTest {
        val real = settingsDataStore(io) { File(folder.root, "flaky.preferences_pb") }
        val attempts = AtomicInteger()
        val flaky = object : DataStore<Preferences> by real {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (attempts.getAndIncrement() == 0) throw IOException("disk full")
                return real.updateData(transform)
            }
        }
        val flakySettings = DoorSettings(flaky, io)
        val health = DoorHealth(provider, flakySettings, bound, MutableStateFlow(false), io)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (attempts.get() == 0) delay(10) }
            assertThat(flakySettings.current.value.connected).isFalse()
            health.refresh()
            withTimeout(5_000) { flakySettings.current.first { it.connected } }
        }
    }

    /** 5 §5.5: turning access off later leaves the connection; the status says so instead. Fails if access off clears the connection. */
    @Test
    fun turningAccessOffLeavesTheConnection() = runTest {
        settings.setConnected(true)
        val health = health()
        provider.access = false
        bound.value = false
        runCurrent()
        health.checks.first { it?.accessAllowed == false }
        assertThat(settings.prefs.first().connected).isTrue()
    }

    /** Fails if the status waits for a resume instead of following Android unbinding and binding the listener. */
    @Test
    fun accessTurnedOffWhileInFrontShowsAtOnceAndClearsWhenBack() = runTest {
        settings.setConnected(true)
        val health = health()
        health.checks.first { it?.accessAllowed == true }
        assertThat(health.status.first()).isNull()
        provider.access = false
        bound.value = false
        runCurrent()
        health.checks.first { it?.accessAllowed == false }
        assertThat(health.status.first()).isEqualTo(accessOff)
        assertThat(inFront.value).isTrue()
        provider.access = true
        bound.value = true
        runCurrent()
        health.checks.first { it?.accessAllowed == true }
        assertThat(health.status.first()).isNull()
    }

    /** Fails if coming back to the front doesn't check again. */
    @Test
    fun theRingAppRemovedShowsWhenCulveryComesBack() = runTest {
        settings.setConnected(true)
        inFront.value = false
        val health = health()
        health.checks.first { it != null }
        provider.installed = false
        inFront.value = true
        runCurrent()
        health.checks.first { it?.appInstalled == false }
        assertThat(health.status.first())
            .isEqualTo("The Ring app isn’t on this tablet. Install it and sign in, then check Settings › Door.")
    }

    /** Fails if the app-missing line outranks the access line. */
    @Test
    fun accessOffIsSaidBeforeTheAppMissing() = runTest {
        settings.setConnected(true)
        provider.installed = false
        provider.access = false
        val health = health()
        health.checks.first { it != null }
        assertThat(health.status.first()).isEqualTo(accessOff)
    }

    /** 5 §6: three unrecognised alerts in a row; a recognised one ends it. Fails if the limit isn't three or a recognised alert doesn't reset it. */
    @Test
    fun threeUnrecognisedAlertsSayRingsShowAsRingAlert() = runTest {
        settings.setConnected(true)
        val health = health()
        health.checks.first { it != null }
        repeat(2) { settings.countAlert(recognised = false) }
        assertThat(health.status.first()).isNull()
        settings.countAlert(recognised = false)
        assertThat(health.status.first()).isEqualTo("Culvery can’t fully read Ring’s notifications, so rings show as ‘Ring alert’.")
        settings.countAlert(recognised = true)
        assertThat(health.status.first()).isNull()
    }
}
