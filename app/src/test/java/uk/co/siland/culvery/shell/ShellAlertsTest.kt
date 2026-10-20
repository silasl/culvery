package uk.co.siland.culvery.shell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import uk.co.siland.culvery.core.plugin.AlertQuieter
import uk.co.siland.culvery.core.plugin.QUIET_CAP_MS

/** A quieter that writes what it was asked into [log]; [gate] holds its quiet() until completed. */
private class RecordingQuieter(private val name: String, private val log: MutableList<String>) : AlertQuieter {
    var gate: CompletableDeferred<Unit>? = null
    var fails = false
    var lowers = false

    override suspend fun quiet(): Boolean {
        log += "$name quiet"
        gate?.await()
        if (fails) throw IOException("the speaker went away")
        log += "$name quieted"
        return lowers
    }

    override suspend fun restore() {
        log += "$name restore"
    }
}

/** A quieter whose n-th quiet() waits for gates[n] and then answers answers[n]. */
private class ScriptedQuieter(private val gates: List<CompletableDeferred<Unit>?>, private val answers: List<Boolean>) : AlertQuieter {
    private var calls = 0

    override suspend fun quiet(): Boolean {
        val n = calls++
        gates[n]?.await()
        return answers[n]
    }

    override suspend fun restore() = Unit
}

@RunWith(AndroidJUnit4::class)
class ShellAlertsTest {
    private fun alerts(vararg quieters: AlertQuieter, scope: CoroutineScope = CoroutineScope(SupervisorJob())) = ShellAlerts(quieters.toSet(), scope)

    private val nothing: @Composable () -> Unit = {}

    /** Asks quieted() and runs what is due: the job is complete once quieted() has returned. */
    private fun TestScope.askQuieted(alerts: ShellAlerts): Job = launch { alerts.quieted() }.also { runCurrent() }

    /** Fails if show keeps the old content, or dismiss leaves one behind. */
    @Test
    fun anAlertReplacesTheLastAndDismissClearsIt() {
        val alerts = alerts()
        val first: @Composable () -> Unit = {}
        val second: @Composable () -> Unit = {}
        alerts.show(content = first)
        alerts.show(content = second)
        assertThat(alerts.content.value).isSameInstanceAs(second)
        alerts.dismiss()
        assertThat(alerts.content.value).isNull()
    }

    /** 6 §3.3 "at once". Fails if a quieter is skipped, waits for the one before it, or quieted() returns before the slowest finished. */
    @Test
    fun aLoudAlertRunsEveryQuieterAtOnceAndQuietedWaitsForThem() = runTest {
        val log = mutableListOf<String>()
        val first = RecordingQuieter("first", log).apply { gate = CompletableDeferred() }
        val second = RecordingQuieter("second", log)
        val alerts = alerts(first, second, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        val quieted = askQuieted(alerts)
        // The second ran while the first was still held.
        assertThat(log).containsExactly("first quiet", "second quiet", "second quieted").inOrder()
        assertThat(quieted.isCompleted).isFalse()
        first.gate!!.complete(Unit)
        runCurrent()
        assertThat(quieted.isCompleted).isTrue()
    }

    /** 6 §3.3: a hung quieter holds the chime back 500 ms at most. Fails at 499 if the cap is shorter, at 500 if longer. */
    @Test
    fun quietedReturnsAt500MsWhenAQuieterHangs() = runTest {
        val hung = RecordingQuieter("hung", mutableListOf()).apply { gate = CompletableDeferred() }
        val alerts = alerts(hung, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        var returnedAt: Long? = null
        launch {
            alerts.quieted()
            returnedAt = testScheduler.currentTime
        }
        advanceTimeBy(QUIET_CAP_MS - 1)
        runCurrent()
        assertThat(returnedAt).isNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(returnedAt).isEqualTo(QUIET_CAP_MS)
    }

    /** 6 §3.3: the 500 ms run from the show, not from each call. Fails if a quieted() asked 300 ms in waits until 800. */
    @Test
    fun theCapRunsFromTheShowNotFromTheCall() = runTest {
        val hung = RecordingQuieter("hung", mutableListOf()).apply { gate = CompletableDeferred() }
        val alerts = alerts(hung, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        advanceTimeBy(300)
        runCurrent()
        var returnedAt: Long? = null
        launch {
            alerts.quieted()
            returnedAt = testScheduler.currentTime
        }
        advanceTimeBy(QUIET_CAP_MS - 300 - 1)
        runCurrent()
        assertThat(returnedAt).isNull()
        advanceTimeBy(1)
        runCurrent()
        assertThat(returnedAt).isEqualTo(QUIET_CAP_MS)
    }

    /** Fails if dismiss forgets a restore. */
    @Test
    fun dismissRunsEveryRestore() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("a", log), RecordingQuieter("b", log), scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        log.clear()
        alerts.dismiss()
        runCurrent()
        assertThat(log).containsExactly("a restore", "b restore")
    }

    /** 6 §3.3: a second ring while the card shows quietens nothing again. Fails if replacing a loud alert re-runs quiet(). */
    @Test
    fun aLoudAlertOverALoudOneDoesNotQuietAgain() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("music", log), scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        alerts.show(loud = true, content = nothing)
        runCurrent()
        val quieted = askQuieted(alerts)
        assertThat(quieted.isCompleted).isTrue()
        alerts.dismiss()
        runCurrent()
        assertThat(log).containsExactly("music quiet", "music quieted", "music restore").inOrder()
    }

    /** Fails if a quiet alert lowers anything, or quieted() waits when nothing is loud. */
    @Test
    fun aQuietAlertNeverQuietens() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("music", log), scope = backgroundScope)
        alerts.show(content = nothing)
        val quieted = askQuieted(alerts)
        alerts.dismiss()
        runCurrent()
        assertThat(quieted.isCompleted).isTrue()
        assertThat(log).isEmpty()
    }

    /** 6 §3.3: a quiet alert replacing a loud one ends the loud one, so the music comes back. */
    @Test
    fun aQuietAlertReplacingALoudOneRestores() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("music", log), scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        alerts.show(content = nothing)
        runCurrent()
        assertThat(log).containsExactly("music quiet", "music quieted", "music restore").inOrder()
    }

    /** 6 §3.3, §4.6: fails if restore runs while the slow quiet() is still lowering: the music would stay low. */
    @Test
    fun aDismissDuringASlowQuietRestoresAfterIt() = runTest {
        val log = mutableListOf<String>()
        val slow = RecordingQuieter("music", log).apply { gate = CompletableDeferred() }
        val alerts = alerts(slow, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        alerts.dismiss()
        runCurrent()
        assertThat(log).containsExactly("music quiet")
        slow.gate!!.complete(Unit)
        runCurrent()
        assertThat(log).containsExactly("music quiet", "music quieted", "music restore").inOrder()
    }

    /** One failing quieter (a speaker gone) neither stops the others nor holds quieted() past their finish. */
    @Test
    fun aFailingQuieterDoesNotStopTheOthers() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("gone", log).apply { fails = true }, RecordingQuieter("music", log), scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        val quieted = askQuieted(alerts)
        assertThat(quieted.isCompleted).isTrue()
        assertThat(log).containsExactly("gone quiet", "music quiet", "music quieted")
        alerts.dismiss()
        runCurrent()
        assertThat(log).containsAtLeast("gone restore", "music restore")
    }

    @Test
    fun loweredOnlyWhenAQuieterTurnedSomethingDown() = runTest {
        val some = RecordingQuieter("some", mutableListOf())
        val alerts = alerts(RecordingQuieter("none", mutableListOf()), some, scope = backgroundScope)
        assertThat(alerts.lowered.value).isFalse()
        alerts.show(loud = true, content = nothing)
        askQuieted(alerts)
        assertThat(alerts.lowered.value).isFalse()
        alerts.dismiss()
        runCurrent()
        some.lowers = true
        alerts.show(loud = true, content = nothing)
        askQuieted(alerts)
        assertThat(alerts.lowered.value).isTrue()
        alerts.dismiss()
        assertThat(alerts.lowered.value).isFalse()
    }

    @Test
    fun aFailingQuieterIsNotLowered() = runTest {
        val gone = RecordingQuieter("gone", mutableListOf()).apply { fails = true }
        val alone = alerts(gone, scope = backgroundScope)
        alone.show(loud = true, content = nothing)
        askQuieted(alone)
        assertThat(alone.lowered.value).isFalse()
        val withMusic = alerts(gone, RecordingQuieter("music", mutableListOf()).apply { lowers = true }, scope = backgroundScope)
        withMusic.show(loud = true, content = nothing)
        askQuieted(withMusic)
        assertThat(withMusic.lowered.value).isTrue()
    }

    @Test
    fun aRingReplacingARingKeepsLowered() = runTest {
        val log = mutableListOf<String>()
        val alerts = alerts(RecordingQuieter("music", log).apply { lowers = true }, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        askQuieted(alerts)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        assertThat(alerts.lowered.value).isTrue()
        assertThat(log.count { it == "music quiet" }).isEqualTo(1)
    }

    @Test
    fun aQuietAlertReplacingARingClearsLowered() = runTest {
        val alerts = alerts(RecordingQuieter("music", mutableListOf()).apply { lowers = true }, scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        askQuieted(alerts)
        alerts.show(content = nothing)
        assertThat(alerts.lowered.value).isFalse()
    }

    /** Fails if an answer is accepted without checking its pass is still the current one. */
    @Test
    fun aLateAnswerFromAnEarlierRingIsIgnored() = runTest {
        val late = CompletableDeferred<Unit>()
        val alerts = alerts(ScriptedQuieter(listOf(late, null), listOf(true, false)), scope = backgroundScope)
        alerts.show(loud = true, content = nothing)
        runCurrent()
        alerts.dismiss()
        alerts.show(loud = true, content = nothing)
        runCurrent()
        late.complete(Unit)
        runCurrent()
        assertThat(alerts.lowered.value).isFalse()
    }

    /** Fails if inFront is true before the screen resumes or stays true after it pauses (5 §2.7). */
    @Test
    fun inFrontFollowsTheScreenItObserves() {
        val alerts = alerts()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().lifecycle.addObserver(alerts)
        assertThat(alerts.inFront.value).isFalse()
        controller.setup()
        assertThat(alerts.inFront.value).isTrue()
        controller.pause()
        assertThat(alerts.inFront.value).isFalse()
    }

    /** Fails if an instance that hands over to the home task in onResume still writes inFront. */
    @Test
    fun anInstanceThatHandsOverInOnResumeWritesNothing() {
        val alerts = alerts()
        var handedOver = false
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val lifecycle = controller.get().lifecycle
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                handedOver = true
            }
        })
        lifecycle.addObserver(alerts.unlessHandedOver(handedOver = { handedOver }, changingConfigurations = { false }))
        controller.setup()
        assertThat(alerts.inFront.value).isFalse()
        controller.pause()
        assertThat(alerts.inFront.value).isFalse()
    }

    /** Fails if shown is true before the screen starts, false while it is only paused, or true after it stops (8 §4.1). */
    @Test
    fun shownFollowsTheScreenFromStartToStop() {
        val alerts = alerts()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().lifecycle.addObserver(alerts.unlessHandedOver(handedOver = { false }, changingConfigurations = { false }))
        controller.create()
        assertThat(alerts.shown.value).isFalse()
        controller.start()
        assertThat(alerts.shown.value).isTrue()
        controller.resume().pause()
        assertThat(alerts.shown.value).isTrue()
        controller.stop()
        assertThat(alerts.shown.value).isFalse()
    }

    /** Fails if the stop of an instance that handed over to the home task clears shown while the home task's is on screen. */
    @Test
    fun anInstanceThatHandsOverStopsWithoutWritingShown() {
        val alerts = alerts()
        val home = Robolectric.buildActivity(ComponentActivity::class.java)
        home.get().lifecycle.addObserver(alerts.unlessHandedOver(handedOver = { false }, changingConfigurations = { false }))
        home.setup()
        var handedOver = false
        val other = Robolectric.buildActivity(ComponentActivity::class.java)
        other.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                handedOver = true
            }
        })
        other.get().lifecycle.addObserver(alerts.unlessHandedOver(handedOver = { handedOver }, changingConfigurations = { false }))
        other.setup().pause().stop()
        assertThat(alerts.shown.value).isTrue()
    }

    /** Fails if a stop for a configuration change, the same screen coming straight back, counts as leaving. */
    @Test
    fun aConfigurationChangeIsNotLeaving() {
        val alerts = alerts()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().lifecycle.addObserver(alerts.unlessHandedOver(handedOver = { false }, changingConfigurations = { true }))
        controller.setup().pause().stop()
        assertThat(alerts.shown.value).isTrue()
    }
}
