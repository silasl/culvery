package uk.co.siland.culvery

import android.app.ActivityManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun ComponentActivity.hideSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun ComponentActivity.showSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
}

/** Must be called while resumed. Debug builds skip pinning: it shows a system prompt on every start. */
fun ComponentActivity.pinToScreen() {
    val am = getSystemService(ActivityManager::class.java)
    if (!BuildConfig.DEBUG && am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask()
}

fun ComponentActivity.unpinFromScreen() {
    val am = getSystemService(ActivityManager::class.java)
    if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) stopLockTask()
}

/** The kiosk's effects on [activity]'s window and task. */
internal class ActivityKioskWindow(private val activity: ComponentActivity) : KioskWindow {
    override fun pin() = activity.pinToScreen()

    override fun unpin() = activity.unpinFromScreen()

    override fun hideBars() = activity.hideSystemBars()

    override fun showBars() = activity.showSystemBars()

    override fun allowPlayServices(allowed: Boolean) = allowPlayServicesInLockTask(activity, allowed)

    override fun moveToBack() {
        activity.moveTaskToBack(true)
    }
}
