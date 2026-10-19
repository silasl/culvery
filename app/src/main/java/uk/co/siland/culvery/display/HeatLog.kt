package uk.co.siland.culvery.display

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

private const val TAG = "HeatLog"

const val HEAT_EVERY_MS = 900_000L

private const val PERCENT = 100

/** The tablet's thermal status with its battery temperature in tenths of a degree and charge in percent; null where Android gave none. */
data class HeatReading(val status: Int, val batteryTenths: Int?, val batteryPercent: Int?)

internal fun readingOf(status: Int, battery: Intent?): HeatReading {
    val tenths = battery?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }
        ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
    val level = battery?.takeIf { it.hasExtra(BatteryManager.EXTRA_LEVEL) }?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
    val scale = battery?.takeIf { it.hasExtra(BatteryManager.EXTRA_SCALE) }?.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
    val percent = if (level != null && scale != null && scale > 0) level * PERCENT / scale else null
    return HeatReading(status, tenths, percent)
}

interface TabletHeat {
    fun reading(): HeatReading

    /** Calls [listener] on each thermal status change, and once on registration, until the handle is closed. */
    fun onStatusChange(listener: () -> Unit): AutoCloseable
}

@Singleton
class AndroidTabletHeat @Inject constructor(@ApplicationContext private val context: Context) : TabletHeat {
    private val power = context.getSystemService(PowerManager::class.java)

    override fun reading(): HeatReading {
        // A null receiver returns the sticky battery intent without registering anything.
        val battery = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return readingOf(power.currentThermalStatus, battery)
    }

    override fun onStatusChange(listener: () -> Unit): AutoCloseable {
        val callback = PowerManager.OnThermalStatusChangedListener { listener() }
        power.addThermalStatusListener(ContextCompat.getMainExecutor(context), callback)
        return AutoCloseable { power.removeThermalStatusListener(callback) }
    }
}

/** Logs the thermal status when it changes and the battery every [HEAT_EVERY_MS], as numbers only (8 §4.7). */
@Singleton
class HeatLog @Inject internal constructor(
    private val tablet: TabletHeat,
    @ApplicationScope private val scope: CoroutineScope,
) : Startable {
    // The platform repeats the current status on registration; only a different one is a change.
    private var lastStatus: Int? = null
    private var registration: AutoCloseable? = null

    override fun start() {
        log(onlyIfChanged = false)
        registration = tablet.onStatusChange { log(onlyIfChanged = true) }
        scope.launch {
            while (true) {
                delay(HEAT_EVERY_MS)
                log(onlyIfChanged = false)
            }
        }
    }

    // Status changes arrive on the main thread and the timer on the scope's.
    @Synchronized
    private fun log(onlyIfChanged: Boolean) {
        val heat = tablet.reading()
        if (onlyIfChanged && heat.status == lastStatus) return
        lastStatus = heat.status
        Log.w(TAG, "Heat: thermal ${heat.status}, battery ${heat.batteryTenths} tenths of a degree, ${heat.batteryPercent} percent")
    }
}
