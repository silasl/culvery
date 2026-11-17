package uk.co.siland.culvery.core.plugin

import java.time.LocalTime

/** Whether the night clock's hours are now on the household's clock (8 §4.5). */
fun interface NightHours {
    /** Null while that isn't known yet. */
    fun isNight(): Boolean?

    /** When night hours begin each evening; null while that isn't known yet. */
    fun startsAt(): LocalTime? = null
}
