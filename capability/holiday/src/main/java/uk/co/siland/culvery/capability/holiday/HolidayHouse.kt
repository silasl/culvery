package uk.co.siland.culvery.capability.holiday

import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.capability.holiday.HolidayStep.ECO_OFF
import uk.co.siland.culvery.capability.holiday.HolidayStep.ECO_ON
import uk.co.siland.culvery.capability.holiday.HolidayStep.HOOVER
import uk.co.siland.culvery.capability.holiday.HolidayStep.LIGHTS_ON
import uk.co.siland.culvery.capability.holiday.HolidayStep.PLUGS_ON
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseHeating
import uk.co.siland.culvery.core.house.HouseHoover
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.attempt

private const val TAG = "HolidayHouse"

enum class HolidayRun { TURNING_ON, TURNING_OFF }

/**
 * The house's Holiday mode (11 §5.3, §5.4): turning on and off, and the steps due while it is on, run by [tick]. Times
 * are local, from [now]; retries and the off line's ten minutes are wall millis, which don't repeat when the clocks go
 * back.
 */
@Singleton
class HolidayHouse internal constructor(
    private val settings: HolidaySettings,
    private val lights: HouseLights?,
    private val heating: HouseHeating?,
    private val hoover: HouseHoover?,
    private val plugs: HousePlugs?,
    private val wall: WallClock,
    private val now: () -> LocalDateTime?,
) : HouseMode, HouseModeSwitch {
    @Inject
    constructor(
        settings: HolidaySettings,
        lights: Optional<HouseLights>,
        heating: Optional<HouseHeating>,
        hoover: Optional<HouseHoover>,
        plugs: Optional<HousePlugs>,
        wall: WallClock,
        clock: HouseholdClock,
    ) : this(settings, lights.orElse(null), heating.orElse(null), hoover.orElse(null), plugs.orElse(null), wall, { clock.now.value })

    private val current = MutableStateFlow<HolidayRecord?>(null)
    private val on = MutableStateFlow<Boolean?>(null)
    private val run = MutableStateFlow<HolidayRun?>(null)
    private val shown = MutableStateFlow<String?>(null)

    /** Null until the store has answered. */
    val record: StateFlow<HolidayRecord?> = current.asStateFlow()
    override val holiday: StateFlow<Boolean?> = on.asStateFlow()
    override val isOn: StateFlow<Boolean?> = holiday
    override val modeId = HouseModes.HOLIDAY
    override val modeName = HolidayWords.LABEL
    val running: StateFlow<HolidayRun?> = run.asStateFlow()

    /** The tile's line: what didn't happen. */
    val line: StateFlow<String?> = shown.asStateFlow()

    private val mutex = Mutex()

    // The rest is in memory and changed under the mutex: a restart forgets the grant, the failures and the last tries.
    private var grant: Authorised? = null
    private val lastTry = mutableMapOf<HolidayStep, Long>()
    private val failed = mutableMapOf<HolidayStep, String>()
    private var offLine: String? = null
    private var offAt = 0L

    // Memory is the truth; true while the store holds an older record than it.
    private var unsaved = false

    /** The steps turning on now would take, for the sheet. */
    suspend fun plan(back: LocalDate): List<HolidayStep> {
        val eco = ready(heating?.ready)
        val plugsReady = ready(plugs?.ready(HouseModes.HOLIDAY))
        val hooverInTime = now()?.let { it < hooverFrom(back) } == true
        return buildList {
            if (ready(lights?.ready)) add(LIGHTS_ON)
            if (eco) add(ECO_ON)
            if (plugsReady) add(PLUGS_ON)
            if (hooverInTime && ready(hoover?.ready)) add(HOOVER)
            if (eco) add(ECO_OFF)
        }
    }

    /** What the plugs step would switch, for the sheet. */
    suspend fun plugChanges(): List<PlugChange> = plugs?.preview(HouseModes.HOLIDAY).orEmpty()

    suspend fun turnOn(back: LocalDate, by: Authorised) {
        require(HousePermissions.HOLIDAY_MODE in by.granted) { "Turning Holiday mode on needs its own grant" }
        exclusive(HolidayRun.TURNING_ON) {
            if (load().on) return@exclusive
            val steps = plan(back)
            grant = by
            resetRun()
            offLine = null
            if (plugs != null && PLUGS_ON in steps) {
                attempt<Unit>({ plugs.begin(HouseModes.HOLIDAY, by) }) { e ->
                    Log.w(TAG, "The plugs didn't begin for Holiday mode (${e::class.simpleName})")
                }
            }
            set(HolidayRecord(on = true, back = back, since = wall.nowMillis(), steps = steps.associateWith { StepState.PENDING }))
            runDue()
        }
    }

    /** Every part is tried and Holiday mode ends whatever they answer (11 §5.4). */
    suspend fun turnOff(by: Authorised) {
        require(HousePermissions.HOLIDAY_MODE in by.granted) { "Turning Holiday mode off needs its own grant" }
        exclusive(HolidayRun.TURNING_OFF) {
            val record = load()
            if (!record.on) return@exclusive
            val lightsLeft = lights != null && answer { lights.setHoliday(false, by) } !is HouseStep.Done
            if (lightsLeft) Log.w(TAG, "Holiday lights didn't turn off with Holiday mode")
            // Eco already off at 00:01 is left as it is (11 §5.7).
            val ecoLeft = heating != null && record.steps[ECO_OFF] == StepState.PENDING && answer { heating.setEco(false) } !is HouseStep.Done
            if (ecoLeft) Log.w(TAG, "The heating didn't come off Eco with Holiday mode")
            val plugsLine = plugs?.let { puttingBack(it) }
            grant = null
            resetRun()
            // Heating, then plugs, then lights, one per line (12 §5.5).
            offLine = listOfNotNull(
                HolidayWords.STILL_ON_ECO.takeIf { ecoLeft },
                plugsLine,
                HolidayWords.LIGHTS_STILL_ON.takeIf { lightsLeft },
            ).joinToString("\n").ifEmpty { null }
            offAt = wall.nowMillis()
            set(HolidayRecord())
        }
    }

    /** The line for plugs that didn't all go back, or null when they did. */
    private suspend fun puttingBack(plugs: HousePlugs): String? {
        val line = when (val left = answer { plugs.leave(HouseModes.HOLIDAY) }) {
            is HouseStep.Done -> null
            is HouseStep.Failed -> left.line
            null -> HouseLines.PLUGS_LEAVE_THREW
        }
        if (line != null) Log.w(TAG, "The plugs didn't all go back with Holiday mode")
        return line
    }

    /** Runs what is due; a tick that finds another change running leaves it to the next minute. */
    suspend fun tick() {
        if (!mutex.tryLock()) return
        try {
            val away = load().on
            if (unsaved) persist()
            if (away) runDue() else if (offLine != null && !within(offAt, OFF_LINE_MS)) {
                offLine = null
                showLine()
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun exclusive(change: HolidayRun, block: suspend () -> Unit) {
        if (!run.compareAndSet(null, change)) return
        try {
            mutex.withLock { block() }
        } finally {
            run.value = null
        }
    }

    /** The first read that finds Holiday mode off puts back plugs a cut-short turn-on left begun (12 §5.2). */
    private suspend fun load(): HolidayRecord = current.value ?: settings.read.filterNotNull().first().also {
        publish(it)
        if (!it.on && plugs != null) answer { plugs.leave(HouseModes.HOLIDAY) }
    }

    private suspend fun runDue() {
        val time = now() ?: return
        for (step in HolidayStep.entries) {
            val record = current.value ?: return
            val back = record.back ?: return
            if (record.steps[step] != StepState.PENDING) continue
            when (window(step, back, time)) {
                Window.NOT_YET -> Unit
                Window.PASSED -> mark(step, StepState.MISSED)
                Window.OPEN -> when {
                    // The grant lives only in memory, so after a restart the lights step can't be run (11 §5.3).
                    step == LIGHTS_ON && grant == null -> mark(step, StepState.MISSED)
                    !within(lastTry[step], RETRY_MS) -> runStep(step)
                }
            }
        }
    }

    private suspend fun runStep(step: HolidayStep) {
        lastTry[step] = wall.nowMillis()
        if (step == PLUGS_ON) return runPlugs()
        settle(step, answer { perform(step) })
    }

    /** No snapshot means the begin never landed, so there is nothing to retry (12 §5.2); a throw is retried. */
    private suspend fun runPlugs() {
        val result = attempt<HouseStep?>({
            plugs?.enter(HouseModes.HOLIDAY) ?: run {
                failed.remove(PLUGS_ON)
                mark(PLUGS_ON, StepState.MISSED)
                return
            }
        }) { e ->
            Log.w(TAG, "Holiday mode's plugs step threw ${e::class.simpleName}")
            null
        }
        settle(PLUGS_ON, result)
    }

    private suspend fun settle(step: HolidayStep, result: HouseStep?) {
        if (result is HouseStep.Done) {
            failed.remove(step)
            mark(step, StepState.DONE)
        } else {
            Log.w(TAG, "Holiday mode's ${step.name} step didn't work; trying again in five minutes")
            failed[step] = (result as? HouseStep.Failed)?.line ?: HolidayWords.failedLine(step)
            showLine()
        }
    }

    private suspend fun perform(step: HolidayStep): HouseStep {
        val gone = HouseStep.Failed(HolidayWords.failedLine(step))
        return when (step) {
            LIGHTS_ON -> grant?.let { lights?.setHoliday(true, it) } ?: gone
            ECO_ON -> heating?.setEco(true) ?: gone
            PLUGS_ON -> error("The plugs step runs through runPlugs")
            HOOVER -> hoover?.start() ?: gone
            ECO_OFF -> heating?.setEco(false) ?: gone
        }
    }

    /** [block]'s answer, or null when it threw, which is logged. */
    private inline fun answer(block: () -> HouseStep): HouseStep? = attempt(block) { e ->
        Log.w(TAG, "A Holiday mode step threw ${e::class.simpleName}")
        null
    }

    private suspend fun mark(step: HolidayStep, state: StepState) {
        if (state == StepState.MISSED) Log.w(TAG, "Holiday mode's ${step.name} step was missed")
        val record = current.value ?: return
        set(record.copy(steps = record.steps + (step to state)))
    }

    private fun resetRun() {
        lastTry.clear()
        failed.clear()
    }

    /** Shows [record] and saves it; one that can't be saved still runs, and is saved again at each tick until it lands. */
    private suspend fun set(record: HolidayRecord) {
        publish(record)
        persist()
    }

    private suspend fun persist() {
        val record = current.value ?: return
        unsaved = attempt({
            if (record.on) settings.save(record) else settings.clear()
            false
        }) { e ->
            Log.w(TAG, "Couldn't save the Holiday mode record (${e::class.simpleName}); trying again at the next tick")
            true
        }
    }

    private fun publish(record: HolidayRecord) {
        current.value = record
        on.value = record.on
        showLine()
    }

    private fun showLine() {
        val record = current.value
        shown.value = if (record?.on != true) {
            offLine
        } else {
            HolidayStep.entries.reversed().firstNotNullOfOrNull { step ->
                when (record.steps[step]) {
                    StepState.PENDING -> failed[step]
                    StepState.MISSED -> HolidayWords.missedLine(step)
                    else -> null
                }
            }
        }
    }

    /** Whether less than [span] has passed on the wall since [since]; a wall that went back counts as passed. */
    private fun within(since: Long?, span: Long): Boolean = since != null && wall.nowMillis() - since in 0 until span

    private suspend fun ready(flag: Flow<Boolean>?): Boolean = flag?.first() == true

    private enum class Window { NOT_YET, OPEN, PASSED }

    private fun window(step: HolidayStep, back: LocalDate, time: LocalDateTime): Window = when (step) {
        LIGHTS_ON, PLUGS_ON -> Window.OPEN
        ECO_ON -> if (time < ecoOff(back)) Window.OPEN else Window.PASSED
        HOOVER -> when {
            time < hooverFrom(back) -> Window.NOT_YET
            time < hooverUntil(back) -> Window.OPEN
            else -> Window.PASSED
        }
        ECO_OFF -> if (time < ecoOff(back)) Window.NOT_YET else Window.OPEN
    }

    private fun hooverFrom(back: LocalDate) = back.minusDays(1).atTime(HOOVER_FROM_HOUR, 0)

    private fun hooverUntil(back: LocalDate) = back.minusDays(1).atTime(HOOVER_UNTIL_HOUR, 0)

    private fun ecoOff(back: LocalDate) = back.atTime(0, 1)

    companion object {
        const val RETRY_MS = 300_000L
        const val OFF_LINE_MS = 600_000L
        private const val HOOVER_FROM_HOUR = 10
        private const val HOOVER_UNTIL_HOUR = 22
    }
}
