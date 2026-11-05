package uk.co.siland.culvery.core.sound

import androidx.annotation.RawRes
import uk.co.siland.culvery.core.ui.SeasonalTheme

/** The sounds that ship in the app. [theme] is the seasonal theme a sound belongs to, if any. */
enum class BuiltInSound(val key: String, val label: String, @RawRes val res: Int, val theme: SeasonalTheme?) {
    CLASSIC("classic", "Classic chime", R.raw.door_chime, null),
    ORGAN("organ", "Haunted organ", R.raw.door_organ, SeasonalTheme.HALLOWEEN),
    SAD_TROMBONE("sad_trombone", "Sad trombone", R.raw.sad_trombone, null),
    HAPPY_BIRTHDAY("happy_birthday", "Happy Birthday", R.raw.happy_birthday, null),
    CHRISTMAS_BELLS("christmas_bells", "Christmas bells", R.raw.christmas_bells, null),
}

val BuiltInSound.id: String get() = "builtin:$key"
