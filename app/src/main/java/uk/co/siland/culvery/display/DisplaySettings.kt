package uk.co.siland.culvery.display

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.ui.SeasonDates
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.defaultSeasonDates

private const val FILE = "display_settings"
private const val TAG = "DisplaySettings"
private const val SECONDS_PER_MINUTE = 60

/** The minutes of sleep delay Settings offers (8 §4.6). */
val SLEEP_CHOICES = listOf(2, 5, 10, 15)

/** The brightness a level can be set to; the screen is never fully dark (8 §4.4). */
val BRIGHTNESS_RANGE = 5..100

/** The camera switch counts as on until it is touched, once the camera is allowed (8 §4.6). */
const val WAKE_BY_CAMERA_DEFAULT = true

/** The display's settings (8 §4.6). [wakeByCamera] is null until the switch is touched. */
data class DisplayPrefs(
    val nightFrom: LocalTime = LocalTime.of(22, 0),
    val nightUntil: LocalTime = LocalTime.of(7, 0),
    val dayPercent: Int = 70,
    val eveningPercent: Int = 25,
    val sleepAfterMinutes: Int = 5,
    val wakeByCamera: Boolean? = null,
    /** Culvery has asked for the camera at least once, so a missing permission with no rationale is a refusal. */
    val cameraAsked: Boolean = false,
    val seasonDates: Map<SeasonalTheme, SeasonDates> = defaultSeasonDates(),
)

/** A small DataStore file under `files/datastore/`, which backup and transfer leave alone. Writes throw `IOException`. */
@Singleton
class DisplaySettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(settingsDataStore { context.preferencesDataStoreFile(FILE) }, scope)

    val prefs: Flow<DisplayPrefs> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the display's settings (${it::class.simpleName}); retrying") }
        .map { it.toPrefs() }
        .distinctUntilChanged()

    /** Null until the file is first read, for what must not act on defaults that may not be the saved choice. */
    val read: StateFlow<DisplayPrefs?> = prefs.stateIn(scope, SharingStarted.Eagerly, null)

    /** The defaults until the file is first read. */
    val current: StateFlow<DisplayPrefs> = read.map { it ?: DisplayPrefs() }.stateIn(scope, SharingStarted.Eagerly, DisplayPrefs())

    suspend fun setNight(from: LocalTime, until: LocalTime) {
        store.edit {
            it[NIGHT_FROM] = from.toSecondOfDay() / SECONDS_PER_MINUTE
            it[NIGHT_UNTIL] = until.toSecondOfDay() / SECONDS_PER_MINUTE
        }
    }

    suspend fun setDayPercent(percent: Int) {
        store.edit { it[DAY_PERCENT] = percent.coerceIn(BRIGHTNESS_RANGE) }
    }

    suspend fun setEveningPercent(percent: Int) {
        store.edit { it[EVENING_PERCENT] = percent.coerceIn(BRIGHTNESS_RANGE) }
    }

    suspend fun setSleepAfter(minutes: Int) {
        require(minutes in SLEEP_CHOICES) { "Sleep after $minutes minutes isn't offered" }
        store.edit { it[SLEEP_AFTER] = minutes }
    }

    suspend fun setWakeByCamera(on: Boolean) {
        store.edit { it[WAKE_BY_CAMERA] = on }
    }

    suspend fun setCameraAsked() {
        store.edit { it[CAMERA_ASKED] = true }
    }

    suspend fun setSeasonDates(theme: SeasonalTheme, dates: SeasonDates) {
        store.edit {
            it[intPreferencesKey(theme.startKey)] = dates.start.packed()
            it[intPreferencesKey(theme.endKey)] = dates.end.packed()
        }
    }

    suspend fun resetSeasonDates(theme: SeasonalTheme) {
        store.edit {
            it.remove(intPreferencesKey(theme.startKey))
            it.remove(intPreferencesKey(theme.endKey))
        }
    }

    private fun Preferences.toPrefs(): DisplayPrefs {
        val defaults = DisplayPrefs()
        return DisplayPrefs(
            nightFrom = this[NIGHT_FROM]?.let(::minuteOfDay) ?: defaults.nightFrom,
            nightUntil = this[NIGHT_UNTIL]?.let(::minuteOfDay) ?: defaults.nightUntil,
            dayPercent = this[DAY_PERCENT]?.coerceIn(BRIGHTNESS_RANGE) ?: defaults.dayPercent,
            eveningPercent = this[EVENING_PERCENT]?.coerceIn(BRIGHTNESS_RANGE) ?: defaults.eveningPercent,
            sleepAfterMinutes = this[SLEEP_AFTER]?.takeIf { it in SLEEP_CHOICES } ?: defaults.sleepAfterMinutes,
            wakeByCamera = this[WAKE_BY_CAMERA],
            cameraAsked = this[CAMERA_ASKED] ?: defaults.cameraAsked,
            seasonDates = SeasonalTheme.entries.associateWith { seasonDates(it) ?: it.defaultDates },
        )
    }

    /** Both ends, or null when either is missing, stored as another type, or not a real month and day. */
    private fun Preferences.seasonDates(theme: SeasonalTheme): SeasonDates? {
        val start = (asMap().entries.firstOrNull { it.key.name == theme.startKey }?.value as? Int)?.toMonthDay() ?: return null
        val end = (asMap().entries.firstOrNull { it.key.name == theme.endKey }?.value as? Int)?.toMonthDay() ?: return null
        return SeasonDates(start, end)
    }

    private fun MonthDay.packed(): Int = monthValue * 100 + dayOfMonth

    private fun Int.toMonthDay(): MonthDay? {
        val month = this / 100
        val day = this % 100
        return if (month in 1..12 && day in 1..Month.of(month).maxLength()) MonthDay.of(month, day) else null
    }

    private val SeasonalTheme.startKey get() = "season_${name.lowercase()}_start"
    private val SeasonalTheme.endKey get() = "season_${name.lowercase()}_end"

    private fun minuteOfDay(minutes: Int): LocalTime = LocalTime.ofSecondOfDay(minutes.coerceIn(0, MINUTES_PER_DAY - 1) * SECONDS_PER_MINUTE.toLong())

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val NIGHT_FROM = intPreferencesKey("night_from_minute")
        val NIGHT_UNTIL = intPreferencesKey("night_until_minute")
        val DAY_PERCENT = intPreferencesKey("day_percent")
        val EVENING_PERCENT = intPreferencesKey("evening_percent")
        val SLEEP_AFTER = intPreferencesKey("sleep_after_minutes")
        val WAKE_BY_CAMERA = booleanPreferencesKey("wake_by_camera")
        val CAMERA_ASKED = booleanPreferencesKey("camera_asked")
    }
}
