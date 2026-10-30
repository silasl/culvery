package uk.co.siland.culvery.display

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.ui.SeasonDates
import uk.co.siland.culvery.core.ui.SeasonalTheme
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

internal const val SEASONAL_THEMES = "Seasonal themes"

internal fun previewTitle(label: String): String = "Preview $label"

internal fun previewLine(label: String, on: Boolean): String =
    if (on) "Showing now. It ends at midnight." else "Shows $label on the wall and plays its doorbell until midnight."

/** Android's details screen for this app, where its permissions are; the first intent the tablet has opens. */
fun appDetailsIntents(packageName: String): List<Intent> =
    listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val DAY_LONG_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)

/** "24 Oct". */
fun dayMonth(d: MonthDay): String = d.atYear(2000).format(DAY_MONTH)

internal fun dayMonth(d: LocalDate): String = d.format(DAY_MONTH)

/** "24 Oct 2027", for a day in a year other than the one being picked in. */
internal fun dayMonthYear(d: LocalDate): String = d.format(DAY_MONTH_YEAR)

internal fun datesLine(dates: SeasonDates): String = "${dayMonth(dates.start)} – ${dayMonth(dates.end)}"

internal const val CHANGE_THE_DATES = "Change the dates"
internal const val TAP_FIRST_THEN_LAST = "Tap the first day, then the last."
internal const val SAVE_DATES = "Save dates"
internal const val CANCEL = "Cancel"

internal fun datesTitle(label: String): String = "$label dates"

internal fun putBack(dates: SeasonDates): String = "Put back ${datesLine(dates)}"

internal fun lastDayBeforeFirst(first: LocalDate): String = "The last day is before the first. Tap a last day on or after ${dayMonth(first)}."

internal fun longerThanAYear(limit: LocalDate): String = "A theme can show for a year at most. Tap a last day before ${dayMonthYear(limit)}."

internal fun nextLine(theme: SeasonalTheme, date: LocalDate): String = "Next: ${theme.label} from ${date.format(DAY_LONG_MONTH)}"
