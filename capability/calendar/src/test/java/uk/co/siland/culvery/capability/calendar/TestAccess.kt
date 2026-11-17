package uk.co.siland.culvery.capability.calendar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.access.test.testAccess as sharedTestAccess

internal typealias TestAccess = uk.co.siland.culvery.core.access.test.TestAccess

/** The shared test access with the calendar's permissions, or [calendarPermissions] in their place. */
internal suspend fun testAccess(
    household: HouseholdRepository,
    listenIn: CoroutineScope,
    clock: WallClock,
    sessionScope: CoroutineScope,
    calendarPermissions: PermissionSource = CalendarPermissionSource(),
): TestAccess = sharedTestAccess(household, listenIn, clock, sessionScope, calendarPermissions)

internal suspend fun TestScope.testAccess(
    household: HouseholdRepository,
    calendarPermissions: PermissionSource = CalendarPermissionSource(),
): TestAccess = sharedTestAccess(household, calendarPermissions)
