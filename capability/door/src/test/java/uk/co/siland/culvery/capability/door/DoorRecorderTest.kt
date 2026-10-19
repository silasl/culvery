package uk.co.siland.culvery.capability.door

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.settingsDataStore

/** 5 §3.1, §4.2: the sink records in the order notices arrive, keeps last heard and the unrecognised run. */
@RunWith(AndroidJUnit4::class)
class DoorRecorderTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = doorDb()
    private val store by lazy { DoorStore(db, File(folder.root, "door")) }
    private val settings by lazy { doorSettings(folder.root, scope) }
    private var now = NOW

    // Each record reaches the alert decision last, after the store and the count: the tests wait on it.
    private val alerted = Channel<Recorded>(Channel.UNLIMITED)
    private val recorder by lazy { DoorRecorder(store, settings, scope, WallClock { now }) { alerted.send(it) } }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    /** The text post and its snapshot re-post must not race: one event, the later one's device and picture. */
    @Test
    fun noticesAreRecordedInTheOrderTheyArrive() = runTest {
        recorder.post(ring(device = "Front Door"))
        recorder.post(ring(at = NOW + 1_000, device = "Back Door", snapshot = aPicture))
        assertThat(listOf(alerted.receive().isNew, alerted.receive().isNew)).containsExactly(true, false).inOrder()
        val event = store.events.first().single()
        assertThat(event.device).isEqualTo("Back Door")
        assertThat(event.snapshot).isNotNull()
    }

    @Test
    fun everyNoticeIsHeard() = runTest {
        recorder.heard(NOW)
        settings.prefs.first { it.lastHeardMillis != null }
        assertThat(settings.prefs.first().lastHeardMillis).isEqualTo(NOW)
    }

    @Test
    fun theRunCountsNewUnrecognisedAlertsAndARecognisedOneEndsIt() = runTest {
        recorder.post(ring(key = "a", unrecognised = true))
        recorder.post(ring(key = "a", at = NOW + 1_000, unrecognised = true))
        recorder.post(ring(key = "b", unrecognised = true))
        repeat(3) { alerted.receive() }
        assertThat(settings.prefs.first().unrecognisedRun).isEqualTo(2)
        recorder.post(motion())
        alerted.receive()
        assertThat(settings.prefs.first().unrecognisedRun).isEqualTo(0)
    }

    @Test
    fun eachRecordGoesToTheAlertDecision() = runTest {
        recorder.post(ring())
        assertThat(alerted.receive().isNew).isTrue()
    }

    /** 5 §5.4: on until its end, and off if the clock was set back past its 30 minutes. */
    @Test
    fun capturingIsOnUntilItsEndAndOffAfterTheClockWentBack() = runTest {
        assertThat(recorder.capturing).isFalse()
        settings.setCapture(untilMillis = NOW + CAPTURE_MS)
        settings.current.first { it.captureUntilMillis != 0L }
        assertThat(recorder.capturing).isTrue()
        now = NOW + CAPTURE_MS
        assertThat(recorder.capturing).isFalse()
        now = NOW - 1
        assertThat(recorder.capturing).isFalse()
    }

    /** Fails if a non-IO failure while counting ends the queue, so later notices are never recorded. */
    @Test
    fun aFailureWhileCountingDoesNotStopLaterNotices() = runTest {
        val real = settingsDataStore(scope) { File(folder.root, "f.preferences_pb") }
        var failures = 1
        val flaky = object : DataStore<Preferences> by real {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (failures-- > 0) throw IllegalStateException("once")
                return real.updateData(transform)
            }
        }
        val flakyRecorder = DoorRecorder(store, DoorSettings(flaky, scope), scope, WallClock { now }) { alerted.send(it) }
        flakyRecorder.post(ring(key = "a"))
        flakyRecorder.post(ring(key = "b"))
        withContext(Dispatchers.Default) { withTimeout(5_000) { repeat(2) { alerted.receive() } } }
        assertThat(store.events.first().map { it.device }).hasSize(2)
    }

    @Test
    fun bindingIsKept() {
        assertThat(recorder.bound.value).isNull()
        recorder.listening(false)
        assertThat(recorder.bound.value).isFalse()
    }
}
