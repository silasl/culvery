package uk.co.siland.culvery.shell

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS
import uk.co.siland.culvery.core.plugin.UserActivity

/**
 * The app's [UserActivity] (4d-2 §6.1): the shell reports fingers through [touched]; TalkBack's gestures arrive as
 * accessibility events through [accessibilityDelegate] on the content view, which Compose sends them up through.
 */
@Singleton
class ShellUserActivity @Inject constructor(@ApplicationContext context: Context) : UserActivity {
    private val manager: AccessibilityManager? = context.getSystemService(AccessibilityManager::class.java)

    private val _touches = MutableStateFlow(0L)
    override val touches: StateFlow<Long> = _touches.asStateFlow()

    private val _exploring = MutableStateFlow(manager?.isTouchExplorationEnabled == true)
    override val exploring: StateFlow<Boolean> = _exploring.asStateFlow()

    init {
        manager?.addTouchExplorationStateChangeListener { _exploring.value = it }
    }

    fun touched() {
        _touches.update { it + 1 }
    }

    override fun timeout(baseMillis: Long, hasControls: Boolean): Long {
        val m = manager ?: return baseMillis
        if (!_exploring.value) return baseMillis
        val controls = if (hasControls) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0
        val flags = AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS or controls
        val recommended = m.getRecommendedTimeoutMillis(baseMillis.toInt(), flags).toLong()
        // Android's own setting stops at 2 minutes; a larger value set another way never holds anything past the edit hold.
        return maxOf(baseMillis, minOf(recommended, EDIT_HOLD_MS))
    }

    /** For `android.R.id.content`: a TalkBack focus move, double-tap or scroll is a touch. Events are only sent while a service listens. */
    val accessibilityDelegate: View.AccessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean {
            if (event.eventType in COUNTED) touched()
            return super.onRequestSendAccessibilityEvent(host, child, event)
        }
    }

    private companion object {
        val COUNTED = setOf(
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
        )
    }
}
