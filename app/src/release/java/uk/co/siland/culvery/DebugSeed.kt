package uk.co.siland.culvery

import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.core.household.HouseholdRepository

@Suppress("UNUSED_PARAMETER")
suspend fun seedDebugData(household: HouseholdRepository, calendar: CalendarSetup, providers: Set<CalendarProvider>) = Unit

@Suppress("UNUSED_PARAMETER")
suspend fun removeSampleWhenReplaced(calendar: CalendarSetup) = Unit
