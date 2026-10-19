package uk.co.siland.culvery.core.plugin

/** Whether the night clock's hours are now on the household's clock (8 §4.5). */
fun interface NightHours {
    /** Null while that isn't known yet. */
    fun isNight(): Boolean?
}
