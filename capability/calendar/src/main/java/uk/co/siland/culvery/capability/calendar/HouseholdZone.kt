package uk.co.siland.culvery.capability.calendar

import java.time.DateTimeException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository

/** The household's time zone; the device zone until setup has set a location, or if the stored id is invalid. */
@Singleton
class HouseholdZone @Inject constructor(household: HouseholdRepository) {
    val zone: Flow<ZoneId> = household.location
        .map { location -> location?.timeZoneId?.let(::parse) ?: ZoneId.systemDefault() }
        .distinctUntilChanged()

    suspend fun current(): ZoneId = zone.first()

    private fun parse(id: String): ZoneId? =
        try {
            ZoneId.of(id)
        } catch (e: DateTimeException) {
            null
        }
}
