package uk.co.siland.culvery.shell

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ShellUserActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(AccessibilityManager::class.java)

    /** TalkBack's focus moves, double-taps and scrolls reach the content view as events, not touches (A38). */
    @Test
    fun focusClicksAndScrollsCountAsTouchesAndHoverDoesNot() {
        val activity = ShellUserActivity(context)
        // Robolectric's window refuses events while accessibility is off, as Android does.
        shadowOf(manager).setEnabled(true)
        val screen = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val content = screen.findViewById<ViewGroup>(android.R.id.content)
        val child = View(screen).also(content::addView)
        content.accessibilityDelegate = activity.accessibilityDelegate
        listOf(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED, AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_SCROLLED)
            .forEach { content.requestSendAccessibilityEvent(child, AccessibilityEvent(it)) }
        assertThat(activity.touches.value).isEqualTo(3)
        content.requestSendAccessibilityEvent(child, AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER))
        assertThat(activity.touches.value).isEqualTo(3)
    }

    @Test
    fun aFingerCountsToo() {
        val activity = ShellUserActivity(context)
        activity.touched()
        assertThat(activity.touches.value).isEqualTo(1)
    }

    @Test
    fun withoutTouchExplorationEveryTimeoutIsItsBase() {
        val activity = ShellUserActivity(context)
        shadowOf(manager).setInteractiveUiTimeout(300_000)
        assertThat(activity.exploring.value).isFalse()
        assertThat(activity.timeout(120_000)).isEqualTo(120_000)
    }

    @Test
    fun withTouchExplorationTimeoutsFollowTimeToTakeActionAndNeverShorten() {
        val activity = ShellUserActivity(context)
        shadowOf(manager).setInteractiveUiTimeout(300_000)
        shadowOf(manager).setNonInteractiveUiTimeout(20_000)
        shadowOf(manager).setTouchExplorationEnabled(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.exploring.value).isTrue()
        assertThat(activity.timeout(120_000)).isEqualTo(300_000)
        assertThat(activity.timeout(4_000, hasControls = false)).isEqualTo(20_000)
        assertThat(activity.timeout(600_000)).isEqualTo(600_000)
    }

    /** Android's own setting stops at 2 minutes; a larger value set another way never holds anything past the edit hold. */
    @Test
    fun aTimeToTakeActionPastTheEditHoldIsCappedThere() {
        val activity = ShellUserActivity(context)
        shadowOf(manager).setInteractiveUiTimeout(3_600_000)
        shadowOf(manager).setNonInteractiveUiTimeout(3_600_000)
        shadowOf(manager).setTouchExplorationEnabled(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.timeout(120_000)).isEqualTo(600_000)
        assertThat(activity.timeout(4_000, hasControls = false)).isEqualTo(600_000)
    }
}
