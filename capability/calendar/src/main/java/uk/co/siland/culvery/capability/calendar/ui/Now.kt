package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.core.plugin.WallClock

private const val TICK_MS = 30_000L

/** Wall-clock time that refreshes on every [ticks] emission (every 30 s by default). */
@Composable
internal fun rememberNowMillis(clock: WallClock, ticks: Flow<Unit> = everyTick): Long {
    val now by produceState(clock.nowMillis(), clock, ticks) {
        ticks.collect { value = clock.nowMillis() }
    }
    return now
}

private val everyTick: Flow<Unit> = flow {
    while (true) {
        delay(TICK_MS)
        emit(Unit)
    }
}

@Composable
internal fun rememberZoneId(zone: HouseholdZone): ZoneId {
    val z by zone.zone.collectAsState(initial = ZoneId.systemDefault())
    return z
}

internal fun todayIn(zone: ZoneId, nowMillis: Long): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

@Composable
internal fun rememberToday(zone: HouseholdZone, clock: WallClock, ticks: Flow<Unit> = everyTick): LocalDate =
    todayIn(rememberZoneId(zone), rememberNowMillis(clock, ticks))
