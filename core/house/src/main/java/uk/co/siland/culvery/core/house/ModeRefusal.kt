package uk.co.siland.culvery.core.house

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The line a mode's tile shows when another mode being on kept it from turning on (13 §2.6): "Turn {mode} off first."
 * from a [refuse] until the next [refuse] or [clear], and only while that mode is still on, so it goes once the mode
 * is turned off. It is read from [switches] as they change, so no scope is needed to keep it current.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class ModeRefusal(private val ownId: String, private val switches: Set<HouseModeSwitch>) : StateFlow<String?> {
    private val refusedBy = MutableStateFlow<HouseModeSwitch?>(null)

    private val changes: Flow<String?> = refusedBy
        .flatMapLatest { mode -> if (mode == null) flowOf(null) else mode.isOn.map { line(mode, it) } }
        .distinctUntilChanged()

    private fun line(mode: HouseModeSwitch, on: Boolean?) = if (on == true) HouseLines.turnOffFirst(mode.modeName) else null

    override val value: String? get() = refusedBy.value?.let { line(it, it.isOn.value) }

    override val replayCache: List<String?> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<String?>): Nothing {
        changes.collect(collector)
        awaitCancellation()
    }

    /** True when another mode is on, which the line then names; clears the line when none is. */
    fun refuse(): Boolean {
        val other = switches.blockedBy(ownId)
        refusedBy.value = other
        return other != null
    }

    fun clear() {
        refusedBy.value = null
    }
}
