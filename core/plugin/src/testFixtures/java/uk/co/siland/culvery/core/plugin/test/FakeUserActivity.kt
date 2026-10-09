package uk.co.siland.culvery.core.plugin.test

import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.plugin.UserActivity

/** A [UserActivity] a test drives: [touch] for a finger or a TalkBack gesture, [exploring] for TalkBack, [timeouts] for "Time to take action". */
class FakeUserActivity(
    exploring: Boolean = false,
    var timeouts: (baseMillis: Long, hasControls: Boolean) -> Long = { base, _ -> base },
) : UserActivity {
    override val touches = MutableStateFlow(0L)
    override val exploring = MutableStateFlow(exploring)

    override fun timeout(baseMillis: Long, hasControls: Boolean): Long = timeouts(baseMillis, hasControls)

    fun touch() {
        touches.value += 1
    }
}
