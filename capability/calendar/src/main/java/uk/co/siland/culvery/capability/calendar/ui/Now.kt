package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.nowTicks
import uk.co.siland.culvery.core.plugin.rememberNowMillis

@Composable
internal fun rememberZoneId(zone: HouseholdZone): ZoneId {
    val z by zone.zone.collectAsState(initial = ZoneId.systemDefault())
    return z
}

internal fun todayIn(zone: ZoneId, nowMillis: Long): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

@Composable
internal fun rememberToday(zone: HouseholdZone, clock: WallClock, ticks: Flow<Unit> = nowTicks): LocalDate =
    todayIn(rememberZoneId(zone), rememberNowMillis(clock, ticks))
