package uk.co.siland.culvery.capability.plugs

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Toaster

private const val TAG = "PlugActions"

/**
 * What the Plugs tab does (12 §4.2): a switch by hand runs on the application scope, so its toast shows if the tab
 * closes, and asks a fresh Adult or Admin PIN only for a plug set to need one. Set-up sits behind `plugs.manage`.
 */
@Singleton
class PlugActions internal constructor(
    private val access: AccessControl,
    private val settings: PlugSettings,
    private val system: PlugSystem?,
    private val toaster: Toaster,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        access: AccessControl,
        settings: PlugSettings,
        systems: Set<@JvmSuppressWildcards PlugSystem>,
        toaster: Toaster,
        @ApplicationScope scope: CoroutineScope,
    ) : this(access, settings, systems.firstOrNull(), toaster, scope)

    private val _switching = MutableStateFlow<Set<String>>(emptySet())

    /** Plugs being switched, from the PIN being accepted (at once for a plug that needs none) until the switch ends. */
    val switching: StateFlow<Set<String>> = _switching.asStateFlow()

    /** Plugs tapped and not yet done, the PIN pad included: a tap on one of them does nothing. */
    private val asking: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Ignored unless the plug is On or Off and isn't already being switched. */
    fun switch(plug: PlugState) {
        val system = system ?: return
        val now = plug.isOn ?: return
        if (!asking.add(plug.entityId)) return
        scope.launch {
            try {
                if (needsPin(plug.entityId)) access.authorise(PlugsPermissions.SWITCH) ?: return@launch
                _switching.update { it + plug.entityId }
                when (val result = system.switch(plug.entityId, on = !now)) {
                    PlugResult.Unreachable -> toaster.show(PlugsWords.cantReach(plug.name), urgent = true)
                    PlugResult.NotSwitched -> toaster.show(PlugsWords.didntSwitch(plug.name), urgent = true)
                    is PlugResult.Failed -> toaster.show(result.line, urgent = true)
                    PlugResult.Done, PlugResult.Busy -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "A switch threw (${e::class.simpleName})")
                toaster.show(PlugsWords.didntSwitch(plug.name), urgent = true)
            } finally {
                _switching.update { it - plug.entityId }
                asking.remove(plug.entityId)
            }
        }
    }

    suspend fun mayManage(): Boolean = access.authorise(PlugsPermissions.MANAGE) != null

    suspend fun show(entityId: String, on: Boolean) = write { if (on) it.showing(entityId) else it.hiding(entityId) }

    /** Nothing is written for a plug that isn't shown. */
    suspend fun setNeedsPin(entityId: String, on: Boolean) = write {
        if (entityId in it.plugs) it.copy(needsPin = if (on) it.needsPin + entityId else it.needsPin - entityId) else it
    }

    /** Nothing is written for a plug that is no longer shown: a chip tap can land after it is hidden. */
    suspend fun setChoice(modeId: String, entityId: String, choice: ModeChoice) = write {
        if (entityId in it.plugs) it.with(modeId, entityId, choice) else it
    }

    private suspend fun needsPin(entityId: String): Boolean = try {
        entityId in settings.current().needsPin
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the PIN setting (${e::class.simpleName}); asking for the PIN")
        true
    }

    private suspend fun write(change: (PlugPrefs) -> PlugPrefs) {
        try {
            settings.update(change)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save the plugs (${e::class.simpleName})")
        }
    }
}
