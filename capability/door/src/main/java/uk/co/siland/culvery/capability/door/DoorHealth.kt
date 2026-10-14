package uk.co.siland.culvery.capability.door

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope

/** Three unrecognised alerts in a row mean Ring's words have changed (5 §6). */
private const val UNRECOGNISED_LIMIT = 3
private const val TAG = "DoorHealth"

/**
 * Whether the tablet can hear the doorbell (5 §5.5, §6), checked off the main thread on each return to the front and each
 * time Android binds or unbinds the listener (which it does when notification access is turned off), so the status
 * needn't wait for a resume. Both steps done connects Ring wherever they were done; access turned off later leaves the
 * connection and shows the status instead.
 */
@Singleton
class DoorHealth internal constructor(
    private val provider: DoorProvider?,
    private val settings: DoorSettings,
    bound: StateFlow<Boolean?>,
    inFront: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        providers: Set<@JvmSuppressWildcards DoorProvider>,
        settings: DoorSettings,
        recorder: DoorRecorder,
        host: AlertHost,
        @ApplicationScope scope: CoroutineScope,
    ) : this(providers.firstOrNull(), settings, recorder.bound, host.inFront, scope)

    data class Checks(val appInstalled: Boolean, val accessAllowed: Boolean)

    private val _checks = MutableStateFlow<Checks?>(null)
    val checks: StateFlow<Checks?> = _checks.asStateFlow()

    /** Counts every check, so a save that failed is tried again by the next one though [Checks] are equal and a StateFlow drops them. */
    private val checked = MutableStateFlow(0)

    init {
        scope.launch {
            check()
            merge(inFront.filter { it }, bound.filterNotNull()).collect { check() }
        }
        scope.launch {
            combine(checks.filterNotNull(), settings.prefs, checked) { c, prefs, _ -> c.appInstalled && c.accessAllowed && !prefs.connected }
                .filter { it }
                .collect { connect() }
        }
    }

    /** Settings › Door asks again as it opens: the household may have just installed Ring or allowed access. */
    fun refresh() {
        scope.launch { check() }
    }

    private fun check() {
        _checks.value = checkNow()
        checked.update { it + 1 }
    }

    private fun checkNow() = Checks(provider?.appInstalled() == true, provider?.accessAllowed() == true)

    private suspend fun connect() {
        try {
            settings.setConnected(true)
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't save the doorbell's connection (${e::class.simpleName})")
        }
    }

    /** The one line the Door tab and Settings › Door show when something is wrong; null while all is well or before Ring is connected. */
    val status: Flow<String?> = combine(checks, settings.prefs) { c, prefs ->
        val service = provider?.descriptor?.displayName
        when {
            c == null || service == null || !prefs.connected -> null
            !c.accessAllowed -> accessOff(service)
            !c.appInstalled -> appMissing(service)
            prefs.unrecognisedRun >= UNRECOGNISED_LIMIT -> cantFullyRead(service)
            else -> null
        }
    }.distinctUntilChanged()
}
