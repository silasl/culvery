package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** A birthday on the wall: the first person's [colour] and every birthday person's name, in People order (16 §3.2). */
data class Birthday(val colour: Long, val names: List<String>) {
    override fun toString() = "Birthday(${names.size})"
}

val LocalBirthday = staticCompositionLocalOf<Birthday?> { null }

/** The look the wall wears: a birthday's, else the seasonal theme's, else none. */
val LocalLook = staticCompositionLocalOf<SeasonalLook?> { null }
