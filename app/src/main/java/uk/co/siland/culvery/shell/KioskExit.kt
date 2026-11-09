package uk.co.siland.culvery.shell

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the kiosk was left with Use other apps or Change home app, and Culvery not back in front since (4c K2). A
 * singleton, not saved state: a configuration change or a new activity keeps it, a process death forgets it, so the
 * kiosk pins again when next in front (ruling 6).
 */
@Singleton
class KioskExit @Inject constructor() {
    private val left = MutableStateFlow(false)

    val exited: StateFlow<Boolean> = left.asStateFlow()

    /** Culvery has been stopped since the exit; main thread only, read by the activity's lifecycle. */
    var stopped = false

    fun left() {
        left.value = true
    }

    fun returned() {
        left.value = false
        stopped = false
    }
}
