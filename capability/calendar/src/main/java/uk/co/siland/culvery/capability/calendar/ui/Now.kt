package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.time.LocalDate
import uk.co.siland.culvery.core.plugin.HouseholdClock

/**
 * Today in the household's zone, or null until that zone is read (never the device's, which can be a different day);
 * it changes only at midnight, so a card keyed on it rebuilds once a day (4c §4.3, C6).
 */
@Composable
internal fun rememberToday(clock: HouseholdClock): LocalDate? {
    val today by clock.today.collectAsState(initial = clock.now.value?.toLocalDate())
    return today
}
