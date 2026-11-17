package uk.co.siland.culvery.capability.reminders

/** [ready]: setup complete and Culvery in front. [quiet]: door quiet hours. */
internal data class Wall(val ready: Boolean, val holiday: Boolean?, val night: Boolean?, val lit: Boolean, val quiet: Boolean)

internal enum class Held(val word: String) { AWAY("away"), HOLIDAY("holiday"), NIGHT("night"), RING("ring") }

/**
 * Why the card is down, checked in that order (never RING: that is the layer answering null); null when it may show.
 * Holiday not known holds as Holiday. Night holds only while the wall isn't lit; night not known holds whatever the wall.
 */
internal fun heldBy(wall: Wall): Held? = when {
    !wall.ready -> Held.AWAY
    wall.holiday != false -> Held.HOLIDAY
    wall.night == null || (wall.night && !wall.lit) -> Held.NIGHT
    else -> null
}

/** Day for certain, not quiet hours, and the wall lit. */
internal fun soundAllowed(wall: Wall): Boolean = wall.night == false && !wall.quiet && wall.lit

/** [of]: how many wait unsnoozed; the card shown is always the first. */
data class Showing(val due: Due, val of: Int)
