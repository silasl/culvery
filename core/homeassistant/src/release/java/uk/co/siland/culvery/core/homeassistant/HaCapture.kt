package uk.co.siland.culvery.core.homeassistant

/** Release builds capture nothing (11 §8.2). */
internal object HaCapture {
    @Volatile
    var keep: Set<String> = emptySet()

    @Suppress("UNUSED_PARAMETER")
    fun log(what: HaCall, text: String) = Unit
}
