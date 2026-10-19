package uk.co.siland.culvery.display

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.time.LocalTime
import uk.co.siland.culvery.core.ui.clockText

internal const val DISPLAY = "Display"
internal const val NIGHT_CLOCK = "Night clock"
internal const val BRIGHTNESS = "Brightness"
internal const val SLEEP = "Sleep"
internal const val NIGHT_START = "night clock start"
internal const val NIGHT_END = "night clock end"

/** The night clock's hours, and what the screen does while it shows (8 §2.3, §2.7). */
fun nightClockLine(from: LocalTime, until: LocalTime): String =
    "A dim clock shows from ${clockText(from)} until ${clockText(until)}. Only a tap wakes the screen, and rings wait in the Door tab."

internal const val DAYTIME = "Daytime"
internal const val EVENING = "Evening, from sunset"
internal const val DAYTIME_BRIGHTNESS = "Daytime brightness"
internal const val EVENING_BRIGHTNESS = "Evening brightness"
internal const val NO_SUN_TIMES = "Until the home location is set, the daytime level applies all day."

internal const val SLEEP_WHEN_ALONE = "Sleep when nobody’s around"

internal fun minutes(count: Int): String = "$count minutes"

internal fun sleepAfter(count: Int): String = "Sleep after ${minutes(count)}"

internal const val WAKE_BY_CAMERA = "Wake when someone walks up"
internal const val WAKE_BY_CAMERA_LINE = "The front camera looks for movement about once a second. Nothing is recorded or sent."
internal const val CAMERA_NOT_ASKED = "Walking up can wake the screen once Culvery may use the camera."
internal const val ALLOW_THE_CAMERA = "Allow the camera"
internal const val CAMERA_REFUSED = "The camera isn’t allowed, so only a tap wakes the screen. Allow it in Android’s settings for Culvery."
internal const val OPEN_ANDROIDS_SETTINGS = "Open Android’s settings for Culvery"
internal const val CANT_OPEN_ANDROIDS_SETTINGS = "Culvery can’t open Android’s settings for Culvery. Allow the camera in Android’s Settings › Apps › Culvery › Permissions."
internal const val NO_FRONT_CAMERA = "This tablet has no front camera, so a tap wakes the screen."

/** Android's details screen for this app, where its permissions are; the first intent the tablet has opens. */
fun appDetailsIntents(packageName: String): List<Intent> =
    listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
