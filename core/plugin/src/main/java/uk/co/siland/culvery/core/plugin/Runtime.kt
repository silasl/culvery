package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Qualifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

fun interface WallClock {
    fun nowMillis(): Long
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

private const val MINUTE_MS = 60_000L
private const val NOW_TICK_MS = 30_000L

/**
 * The wall time in the latest zone from [zones]: now, then at the start of each minute, and at once when the zone
 * changes (4b design §3.8). Every zone in use is offset by whole minutes, so an epoch minute is a minute on its clock.
 */
fun wallTimeEachMinute(zones: Flow<ZoneId>, clock: WallClock): Flow<LocalDateTime> =
    combine(zones, minuteTicks(clock)) { zone, millis -> LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone) }

private fun minuteTicks(clock: WallClock): Flow<Long> = flow {
    while (true) {
        val now = clock.nowMillis()
        emit(now)
        delay(MINUTE_MS - Math.floorMod(now, MINUTE_MS))
    }
}

/** Every 30 s: how often [rememberNowMillis] reads the clock unless told otherwise. */
val nowTicks: Flow<Unit> = flow {
    while (true) {
        delay(NOW_TICK_MS)
        emit(Unit)
    }
}

/** Wall-clock time that refreshes on every [ticks] emission (every 30 s by default). */
@Composable
fun rememberNowMillis(clock: WallClock, ticks: Flow<Unit> = nowTicks): Long {
    val now by produceState(clock.nowMillis(), clock, ticks) {
        ticks.collect { value = clock.nowMillis() }
    }
    return now
}
