package uk.co.siland.culvery.provider.hoover_homeassistant

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import uk.co.siland.culvery.capability.hoover.HooverActivity
import uk.co.siland.culvery.capability.hoover.HooverPrefs
import uk.co.siland.culvery.capability.hoover.HooverResult
import uk.co.siland.culvery.capability.hoover.HooverSettings
import uk.co.siland.culvery.capability.hoover.HooverState
import uk.co.siland.culvery.capability.hoover.HooverSystem
import uk.co.siland.culvery.capability.hoover.NO_HOOVER_CHOSEN
import uk.co.siland.culvery.capability.hoover.STARTABLE
import uk.co.siland.culvery.core.homeassistant.CONFIRM_FOR_MS
import uk.co.siland.culvery.core.homeassistant.EntityState
import uk.co.siland.culvery.core.homeassistant.HaProblem
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantException
import uk.co.siland.culvery.core.homeassistant.NOT_LINKED
import uk.co.siland.culvery.core.homeassistant.entityTrouble
import uk.co.siland.culvery.core.homeassistant.problemLine

private const val TAG = "HaHooverSystem"

internal fun activityOf(state: String): HooverActivity? = when (state) {
    "docked" -> HooverActivity.DOCKED
    "cleaning" -> HooverActivity.CLEANING
    "returning" -> HooverActivity.RETURNING
    "paused" -> HooverActivity.PAUSED
    "idle" -> HooverActivity.IDLE
    "error" -> HooverActivity.ERROR
    else -> null
}

/** The battery sensor is found by name, with no registry read (11 §4.2). */
internal fun batteryIdOf(vacuumId: String): String = "sensor." + vacuumId.substringAfter('.') + "_battery"

private fun batteryPercentOf(sensor: EntityState?): Int? =
    sensor?.state?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }?.roundToInt()

private fun problemState(line: String) = HooverState(null, null, line)

/** The row's state; null with no link or no hoover chosen, so there is no row (11 §4.4). */
internal fun hooverState(
    prefs: HooverPrefs,
    linked: Boolean,
    entities: Map<String, EntityState?>,
    problem: HaProblem?,
): HooverState? {
    val device = prefs.device?.takeIf { linked } ?: return null
    entityTrouble(device, entities, problem, GONE_HOOVER, HOOVER_UNREACHABLE)?.let { return problemState(it) }
    val activity = entities[device]?.let { activityOf(it.state) } ?: return HooverState(null, null, null)
    return HooverState(activity, batteryPercentOf(entities[batteryIdOf(device)]), null)
}

internal fun HomeAssistant.hooverStates(prefs: Flow<HooverPrefs>): Flow<HooverState?> =
    combine(prefs, link, entities, problem) { p, link, entities, problem -> hooverState(p, link != null, entities, problem) }
        .distinctUntilChanged()

/** The vacuum and its battery sensor. */
internal fun HooverPrefs.watched(): Set<String> = setOfNotNull(device, device?.let(::batteryIdOf))

/** The hoover through Home Assistant (11 §4.2–§4.4, §6). */
@Singleton
class HaHooverSystem internal constructor(
    private val ha: HomeAssistant,
    private val prefs: Flow<HooverPrefs>,
) : HooverSystem {
    @Inject
    constructor(ha: HomeAssistant, settings: HooverSettings) : this(ha, settings.prefs)

    private val starting = Mutex()

    override val state: Flow<HooverState?> = ha.hooverStates(prefs)

    override fun watch(): () -> Unit = ha.watch(prefs.map { it.watched() })

    /** One start at a time; the lock goes when the check ends or is cancelled (11 §6). */
    override suspend fun start(): HooverResult {
        if (!starting.tryLock()) return HooverResult.Busy
        try {
            return startOnce()
        } finally {
            starting.unlock()
        }
    }

    private suspend fun startOnce(): HooverResult {
        if (ha.link.value == null) return HooverResult.Failed(NOT_LINKED)
        val device = prefs.first().device ?: return HooverResult.Failed(NO_HOOVER_CHOSEN)
        try {
            val vacuum = ha.current(device)
            if (vacuum?.let { activityOf(it.state) } !in STARTABLE) return HooverResult.NotStarted
            try {
                ha.call("vacuum", "start", device)
            } catch (e: HomeAssistantException) {
                if (e.problem != HaProblem.REFUSED) throw e
                Log.w(TAG, "Home Assistant refused the start")
                return HooverResult.NotStarted
            }
            val left = ha.awaitEntity(device, CONFIRM_FOR_MS) { activityOf(it.state)?.let { a -> a !in STARTABLE } == true }
            if (left != null && activityOf(left.state) != HooverActivity.ERROR) return HooverResult.Started
            Log.w(TAG, "The hoover did not leave the dock")
            return HooverResult.NotStarted
        } catch (e: HomeAssistantException) {
            return HooverResult.Failed(problemLine(e.problem))
        }
    }
}
