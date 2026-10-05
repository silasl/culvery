package uk.co.siland.culvery

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAppTask

/** The kiosk's pinning over a real activity lifecycle (Robolectric drives it), with the window's effects recorded. */
@RunWith(AndroidJUnit4::class)
class KioskLifecycleTest {
    private class RecordingWindow : KioskWindow {
        val calls = mutableListOf<String>()
        var homeStarts = true
        override fun pin() { calls += "pin" }
        override fun unpin() { calls += "unpin" }
        override fun hideBars() { calls += "hideBars" }
        override fun showBars() { calls += "showBars" }
        override fun moveToBack() { calls += "moveToBack" }
        override fun allowPlayServices(allowed: Boolean) { calls += "allowPlayServices($allowed)" }
        override fun startHome() {
            if (!homeStarts) throw ActivityNotFoundException()
            calls += "startHome"
        }
        override fun finishTask() { calls += "finishTask" }
        override fun removeOtherTasks() { calls += "removeOtherTasks" }
    }

    private val window = RecordingWindow()
    private val front = FrontTracker()
    private var exited = false
    private var home = true
    private var owner = false
    private var changing = false
    private var inHomeTask = true
    private val said = mutableListOf<String>()
    private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
    private val kiosk = kioskFor()

    private fun kioskFor() = KioskLifecycle(
        window,
        setupComplete = { true },
        isHomeApp = { home },
        inHomeTask = { inHomeTask },
        isDeviceOwner = { owner },
        kioskExited = { exited },
        returnedToFront = { exited = false },
        front = { front },
        changingConfigurations = { changing },
        say = { said += it },
    )

    @Before
    fun setUp() {
        controller.get().lifecycle.addObserver(kiosk)
        controller.setup()
    }

    /** Review Focus 2: a fresh start (after a process death, say) remembers no "exited", so it pins. */
    @Test
    fun aFreshStartPins() {
        assertThat(window.calls).contains("pin")
    }

    /** Review Focus 5, D3: as the home app, Exit kiosk stays in front, and a Home press doesn't pin it again. */
    @Test
    fun exitKioskAsTheHomeAppStaysInFrontUnpinnedThroughAHomePress() {
        window.calls.clear()
        exited = true
        kiosk.exitKiosk()
        assertThat(window.calls).containsExactly("unpin", "showBars").inOrder()
        // Home pressed while Culvery is home and in front: paused and resumed, never stopped.
        controller.pause().resume()
        assertThat(window.calls).doesNotContain("pin")
    }

    @Test
    fun leavingAfterExitKioskAndComingBackPinsAgain() {
        exited = true
        kiosk.exitKiosk()
        window.calls.clear()
        controller.pause().stop().start().resume()
        assertThat(window.calls).contains("pin")
    }

    @Test
    fun withAnotherHomeAppExitKioskMovesToTheBack() {
        home = false
        exited = true
        window.calls.clear()
        kiosk.exitKiosk()
        assertThat(window.calls).containsExactly("unpin", "showBars", "moveToBack").inOrder()
    }

    /** A configuration change that waited for Culvery to return builds a new activity that never saw the stop. */
    @Test
    fun aRelaunchAfterBeingStoppedPinsAgain() {
        exited = true
        kiosk.exitKiosk()
        controller.pause().stop()
        window.calls.clear()
        val next = Robolectric.buildActivity(ComponentActivity::class.java)
        next.get().lifecycle.addObserver(kioskFor())
        next.setup()
        assertThat(window.calls).contains("pin")
    }

    @Test
    fun aConfigurationChangeWhileInFrontKeepsExited() {
        exited = true
        kiosk.exitKiosk()
        changing = true
        controller.pause().stop()
        window.calls.clear()
        val next = Robolectric.buildActivity(ComponentActivity::class.java)
        next.get().lifecycle.addObserver(kioskFor())
        next.setup()
        assertThat(exited).isTrue()
        assertThat(window.calls).doesNotContain("pin")
    }

    @Test
    fun whenNoHomeAppScreenOpensItPinsAgainAndSaysSo() {
        exited = true
        window.calls.clear()
        kiosk.openHomeAppScreen { throw ActivityNotFoundException() }
        assertThat(window.calls).containsExactly("unpin", "pin").inOrder()
        assertThat(exited).isFalse()
        assertThat(said).containsExactly(HOME_APP_NOT_SET)
    }

    @Test
    fun theHomeAppToastNamesWhatToDoNext() {
        assertThat(HOME_APP_NOT_SET)
            .isEqualTo("Culvery isn't the home app yet — exit kiosk, then set it in Android's Settings › Apps › Default apps.")
    }

    @Test
    fun aScreenThatOpensIsLeftToPinOnTheNextResume() {
        window.calls.clear()
        kiosk.openHomeAppScreen { }
        assertThat(window.calls).containsExactly("unpin")
        assertThat(said).isEmpty()
    }

    @Test
    fun aCancelledRoleDialogSaysSoUnlessCulveryIsNowHome() {
        home = true
        kiosk.roleAnswered(cancelled = true)
        kiosk.roleAnswered(cancelled = false)
        assertThat(said).isEmpty()
        home = false
        kiosk.roleAnswered(cancelled = false)
        assertThat(said).isEmpty()
        kiosk.roleAnswered(cancelled = true)
        assertThat(said).containsExactly(HOME_APP_NOT_SET)
    }

    /** 4c §5.3: not as device owner, Google's screens need the kiosk unpinned. */
    @Test
    fun leavingForGoogleUnpinsWhenNotDeviceOwner() {
        window.calls.clear()
        kiosk.leaveForGoogle()
        assertThat(window.calls).containsExactly("unpin")
    }

    /** 4c §5.2: as device owner Culvery stays in lock-task and Play services is allowed in it, nothing more. */
    @Test
    fun leavingForGoogleAsDeviceOwnerAllowsPlayServicesAndStaysPinned() {
        owner = true
        window.calls.clear()
        kiosk.leaveForGoogle()
        assertThat(window.calls).containsExactly("allowPlayServices(true)")
    }

    @Test
    fun resumingAsDeviceOwnerTakesPlayServicesOutOfLockTaskBeforePinning() {
        owner = true
        window.calls.clear()
        controller.pause().resume()
        assertThat(window.calls.filter { it.startsWith("allowPlayServices") || it == "pin" })
            .containsExactly("allowPlayServices(false)", "pin").inOrder()
    }

    @Test
    fun resumingWhenNotDeviceOwnerNeverTouchesTheAllowlist() {
        window.calls.clear()
        controller.pause().resume()
        assertThat(window.calls.filter { it.startsWith("allowPlayServices") }).isEmpty()
    }

    /** The chooser never opened, so Culvery never paused: an unpinned Culvery in front pins again at once. */
    @Test
    fun returningToPinningWhileInFrontPins() {
        kiosk.leaveForGoogle()
        window.calls.clear()
        kiosk.returnToPinning()
        assertThat(window.calls).containsExactly("pin")
    }

    /** The result arrives before onResume, which pins; pinning a paused activity isn't allowed. */
    @Test
    fun returningToPinningBeforeTheResumeLeavesItToTheResume() {
        controller.pause()
        window.calls.clear()
        kiosk.returnToPinning()
        assertThat(window.calls).isEmpty()
        controller.resume()
        assertThat(window.calls).contains("pin")
    }

    @Test
    fun returningToPinningAfterExitKioskDoesNotPin() {
        exited = true
        window.calls.clear()
        kiosk.returnToPinning()
        assertThat(window.calls).isEmpty()
    }

    @Test
    fun returningToPinningAsDeviceOwnerRemovesPlayServicesFromLockTask() {
        owner = true
        kiosk.leaveForGoogle()
        window.calls.clear()
        kiosk.returnToPinning()
        assertThat(window.calls).containsExactly("allowPlayServices(false)")
    }

    @Test
    fun theActivityWindowMovesTheTaskToTheBack() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        ActivityKioskWindow(activity).moveToBack()
        assertThat(shadowOf(activity).isTaskMovedToBack).isTrue()
    }

    /** The emulator walkthrough: Choose home app said yes from the launcher's task, so Android made a second Culvery. */
    @Test
    fun aGrantedRoleOutsideTheHomeTaskHandsOverToHome() {
        inHomeTask = false
        controller.pause()
        window.calls.clear()
        kiosk.roleAnswered(cancelled = false)
        assertThat(window.calls).containsExactly("startHome", "finishTask").inOrder()
        assertThat(said).isEmpty()
    }

    /** The instance handing over neither pins nor unpins on its way out: the home task's Culvery does that. */
    @Test
    fun anInstanceThatHandedOverNeverPinsAgain() {
        inHomeTask = false
        controller.pause()
        kiosk.roleAnswered(cancelled = false)
        window.calls.clear()
        controller.resume()
        kiosk.returnToPinning()
        kiosk.openHomeAppScreen { throw ActivityNotFoundException() }
        assertThat(window.calls).isEmpty()
    }

    /** A launcher or Settings "Open" while Culvery is already home: onCreate hands over before anything shows. */
    @Test
    fun aStartOutsideTheHomeTaskWhileHomeHandsOver() {
        inHomeTask = false
        val next = Robolectric.buildActivity(ComponentActivity::class.java)
        val other = kioskFor()
        window.calls.clear()
        assertThat(other.handOverToHome()).isTrue()
        assertThat(window.calls).containsExactly("startHome", "finishTask").inOrder()
        next.get().lifecycle.addObserver(other)
        window.calls.clear()
        next.setup()
        assertThat(window.calls).isEmpty()
    }

    @Test
    fun aStartInTheHomeTaskStays() {
        inHomeTask = true
        window.calls.clear()
        assertThat(kioskFor().handOverToHome()).isFalse()
        kiosk.roleAnswered(cancelled = false)
        assertThat(window.calls).isEmpty()
    }

    @Test
    fun aStartOutsideTheHomeTaskWhileAnotherAppIsHomeStays() {
        inHomeTask = false
        home = false
        window.calls.clear()
        assertThat(kioskFor().handOverToHome()).isFalse()
        assertThat(window.calls).isEmpty()
    }

    /**
     * Review: Culvery made home in Settings while an old standard-task Culvery lives on; that task brought back from
     * recents (or by Settings' Open, with no onCreate) hands over rather than pinning a second Culvery.
     */
    @Test
    fun aResumeOutsideTheHomeTaskOnceCulveryIsHomeHandsOver() {
        inHomeTask = false
        home = false
        controller.pause().stop()
        home = true
        window.calls.clear()
        controller.start().resume()
        assertThat(window.calls).containsExactly("startHome", "finishTask").inOrder()
    }

    /** The instance that hands over in onCreate starts no shell work: neither the front tracker nor the view model. */
    @Test
    fun aHandOverReadsNothingOfTheShell() {
        inHomeTask = false
        val kiosk = KioskLifecycle(
            window,
            setupComplete = { error("setup read") },
            isHomeApp = { true },
            inHomeTask = { false },
            isDeviceOwner = { error("owner read") },
            kioskExited = { error("shell read") },
            returnedToFront = { error("shell read") },
            front = { error("shell read") },
            changingConfigurations = { false },
            say = { error("toast") },
        )
        assertThat(kiosk.handOverToHome()).isTrue()
    }

    /** Review: no home screen to start (it can't, but if it did) leaves this instance as it was, not half handed over. */
    @Test
    fun aHomeThatWontStartLeavesThisInstanceInCharge() {
        inHomeTask = false
        window.homeStarts = false
        window.calls.clear()
        assertThat(kiosk.handOverToHome()).isFalse()
        assertThat(kiosk.handedOver).isFalse()
        assertThat(window.calls).isEmpty()
    }

    @Test
    fun theActivityWindowStartsHomeInItsOwnTaskAndRemovesThisOne() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val window = ActivityKioskWindow(activity)
        window.startHome()
        window.finishTask()
        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_MAIN)
        assertThat(started.categories).containsExactly(Intent.CATEGORY_HOME)
        assertThat(started.`package`).isEqualTo(activity.packageName)
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun onlyAHomeIntentIsTheHomeTask() {
        assertThat(isHomeTask(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))).isTrue()
        assertThat(isHomeTask(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))).isFalse()
        assertThat(isHomeTask(null)).isFalse()
    }

    /**
     * The emulator walkthrough: saying yes to the role makes Android start the home task's Culvery itself, and the
     * launcher-task Culvery that asked is never resumed again, so the home task's Culvery removes it.
     */
    @Test
    fun aResumeInTheHomeTaskWhileHomeRemovesCulverysOtherTasksBeforePinning() {
        assertThat(window.calls).contains("removeOtherTasks")
        assertThat(window.calls.indexOf("removeOtherTasks")).isLessThan(window.calls.indexOf("pin"))
    }

    @Test
    fun aResumeInTheHomeTaskWhileAnotherAppIsHomeRemovesNothing() {
        home = false
        window.calls.clear()
        controller.pause().resume()
        assertThat(window.calls).doesNotContain("removeOtherTasks")
    }

    @Test
    fun aResumeOutsideTheHomeTaskRemovesNothing() {
        inHomeTask = false
        home = false
        window.calls.clear()
        controller.pause().resume()
        assertThat(window.calls).doesNotContain("removeOtherTasks")
    }

    @Test
    fun theActivityWindowRemovesEveryCulveryTaskButItsOwn() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val own = appTask(activity.taskId)
        val other = appTask(activity.taskId + 1)
        shadowOf(activity.getSystemService(ActivityManager::class.java)).setAppTasks(listOf(own, other))
        ActivityKioskWindow(activity).removeOtherTasks()
        assertThat(shadowOf(other).isFinishedAndRemoved).isTrue()
        assertThat(shadowOf(own).isFinishedAndRemoved).isFalse()
    }

    private fun appTask(id: Int): ActivityManager.AppTask = ShadowAppTask.newInstance().also {
        shadowOf(it).setTaskInfo(ActivityManager.RecentTaskInfo().apply { taskId = id })
    }
}
