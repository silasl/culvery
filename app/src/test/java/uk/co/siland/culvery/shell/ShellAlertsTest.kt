package uk.co.siland.culvery.shell

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class ShellAlertsTest {
    /** Fails if show keeps the old content, or dismiss leaves one behind. */
    @Test
    fun anAlertReplacesTheLastAndDismissClearsIt() {
        val alerts = ShellAlerts()
        val first: @androidx.compose.runtime.Composable () -> Unit = {}
        val second: @androidx.compose.runtime.Composable () -> Unit = {}
        alerts.show(first)
        alerts.show(second)
        assertThat(alerts.content.value).isSameInstanceAs(second)
        alerts.dismiss()
        assertThat(alerts.content.value).isNull()
    }

    /** Fails if inFront is true before the screen resumes or stays true after it pauses (5 §2.7). */
    @Test
    fun inFrontFollowsTheScreenItObserves() {
        val alerts = ShellAlerts()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().lifecycle.addObserver(alerts)
        assertThat(alerts.inFront.value).isFalse()
        controller.setup()
        assertThat(alerts.inFront.value).isTrue()
        controller.pause()
        assertThat(alerts.inFront.value).isFalse()
    }
}
