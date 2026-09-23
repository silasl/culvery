package uk.co.siland.househub

import kotlinx.coroutines.flow.first
import uk.co.siland.househub.core.access.PinManager
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.household.Role

/** Debug builds only: an Admin with PIN 1234 so Settings is reachable before the setup wizard exists. */
suspend fun seedDebugData(household: HouseholdRepository, pins: PinManager) {
    if (household.people.first().isNotEmpty()) return
    val admin = household.addPerson("Admin", 0xFF4CB387, Role.ADMIN)
    pins.setPin(admin.id, "1234")
}
