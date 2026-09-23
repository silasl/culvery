package uk.co.siland.househub

import uk.co.siland.househub.core.access.PinManager
import uk.co.siland.househub.core.household.HouseholdRepository

@Suppress("UNUSED_PARAMETER")
suspend fun seedDebugData(household: HouseholdRepository, pins: PinManager) = Unit
