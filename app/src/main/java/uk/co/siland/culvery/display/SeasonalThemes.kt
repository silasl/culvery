package uk.co.siland.culvery.display

import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import uk.co.siland.culvery.core.house.TodaysBirthdays
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.ui.Birthday
import uk.co.siland.culvery.core.ui.SeasonDates
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.ShowingTheme
import uk.co.siland.culvery.core.ui.defaultSeasonDates
import uk.co.siland.culvery.core.ui.firstDayIn

/** One look shown by hand for [day] only (14 §3, 16 §3.2); it counts as off on any other day. */
sealed interface Preview {
    val day: LocalDate

    data class Theme(val theme: SeasonalTheme, override val day: LocalDate) : Preview

    data class Birthday(override val day: LocalDate) : Preview
}

/** What dresses the wall: a birthday's colours, else a seasonal theme's, else the normal ones. */
data class WallDress(val seasonal: SeasonalTheme? = null, val birthday: Birthday? = null)

private fun birthdayOf(people: List<Person>): Birthday? =
    people.firstOrNull()?.let { Birthday(it.color, people.map(Person::name)) }

/** A theme preview, then a birthday preview, then today's birthdays, then [seasonal] by its dates (16 §3.2). */
internal fun dressOn(preview: Preview?, previewPerson: Person?, birthdays: List<Person>, seasonal: SeasonalTheme?): WallDress =
    when {
        preview is Preview.Theme -> WallDress(seasonal = preview.theme)
        preview is Preview.Birthday && previewPerson != null -> WallDress(birthday = birthdayOf(listOf(previewPerson)))
        else -> birthdayOf(birthdays)?.let { WallDress(birthday = it) } ?: WallDress(seasonal = seasonal)
    }

/** The preview's theme when it is for [date], else the first theme whose dates hold [date] (14 §4.1). */
internal fun showingOn(date: LocalDate, dates: Map<SeasonalTheme, SeasonDates>, preview: Preview?): SeasonalTheme? =
    (preview as? Preview.Theme)?.takeIf { it.day == date }?.theme
        ?: SeasonalTheme.entries.firstOrNull { (dates[it] ?: it.defaultDates).contains(date) }

/** The theme whose next start is soonest, with that day (today counts); ties go to list order (14 §4.5). */
internal fun nextTheme(today: LocalDate, dates: Map<SeasonalTheme, SeasonDates>): Pair<SeasonalTheme, LocalDate>? =
    SeasonalTheme.entries
        .mapNotNull { theme ->
            val range = dates[theme] ?: theme.defaultDates
            (today.year..today.year + 4).firstNotNullOfOrNull { year ->
                range.start.firstDayIn(year).takeIf { !it.isBefore(today) && it in range }
            }?.let { theme to it }
        }
        .minByOrNull { it.second }

/** Which seasonal theme the wall shows, and what dresses it for a birthday, by the household's date; the clock is read from [now], never the device's own. */
@Singleton
class SeasonalThemes internal constructor(
    private val now: StateFlow<LocalDateTime?>,
    dates: Flow<Map<SeasonalTheme, SeasonDates>>,
    scope: CoroutineScope,
    birthdays: Flow<List<Person>> = flowOf(emptyList()),
    people: Flow<List<Person>> = flowOf(emptyList()),
) : ShowingTheme {
    @Inject
    constructor(
        clock: HouseholdClock,
        settings: DisplaySettings,
        @ApplicationScope scope: CoroutineScope,
        todays: TodaysBirthdays,
        household: HouseholdRepository,
    ) : this(clock.now, settings.read.filterNotNull().map { it.seasonDates }, scope, todays.today, household.people)

    private val preview = MutableStateFlow<Preview?>(null)

    val dates: StateFlow<Map<SeasonalTheme, SeasonDates>> = dates.stateIn(scope, SharingStarted.Eagerly, defaultSeasonDates())

    /** The household's date; null while the clock is unread. */
    val today: StateFlow<LocalDate?> =
        now.map { it?.toLocalDate() }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    /** The preview, only while it is for today. */
    private val livePreview: Flow<Preview?> = combine(today, preview) { today, preview -> preview?.takeIf { it.day == today } }

    /** The theme to look for next and its first day; null while a theme is in its dates or the clock is unread. */
    val next: StateFlow<Pair<SeasonalTheme, LocalDate>?> =
        combine(today, this.dates) { today, dates ->
            if (today == null || showingOn(today, dates, null) != null) null else nextTheme(today, dates)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override val showing: StateFlow<SeasonalTheme?> =
        combine(today, this.dates, preview) { today, dates, preview -> today?.let { showingOn(it, dates, preview) } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** The theme previewed today, else null. */
    val previewing: StateFlow<SeasonalTheme?> =
        livePreview.map { (it as? Preview.Theme)?.theme }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** The first person in People, whose birthday the preview shows; null with nobody in People. */
    val previewPerson: StateFlow<Person?> =
        people.map { it.firstOrNull() }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Whether the birthday preview is on today. */
    val previewingBirthday: StateFlow<Boolean> =
        livePreview.map { it is Preview.Birthday }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** What dresses the wall; the doorbell reads [showing], which a birthday never changes (16 §3.2). */
    val dress: StateFlow<WallDress> =
        combine(livePreview, previewPerson, birthdays, showing, ::dressOn)
            .distinctUntilChanged()
            .onEach { logBirthday(it.birthday) }
            .stateIn(scope, SharingStarted.Eagerly, WallDress())

    override val birthdayShowing: StateFlow<Boolean> =
        dress.map { it.birthday != null }.stateIn(scope, SharingStarted.Eagerly, false)

    private var logged = 0

    private fun logBirthday(birthday: Birthday?) {
        val names = birthday?.names.orEmpty()
        if (names.size == logged) return
        if (names.isEmpty()) Log.i(TAG, "Birthday look off") else Log.i(TAG, "Birthday look on, people: ${names.size}")
        logged = names.size
    }

    /** Turning one on replaces any other; nothing happens while the clock is unread. Off clears only [theme]'s own preview. */
    fun preview(theme: SeasonalTheme, on: Boolean) = setPreview(on, { Preview.Theme(theme, it) }) { it is Preview.Theme && it.theme == theme }

    /** On replaces a theme preview; nothing happens while the clock is unread. Off clears only a birthday preview. */
    fun previewBirthday(on: Boolean) = setPreview(on, Preview::Birthday) { it is Preview.Birthday }

    private fun setPreview(on: Boolean, make: (LocalDate) -> Preview, isOurs: (Preview) -> Boolean) {
        if (on) {
            val today = now.value?.toLocalDate() ?: return
            preview.value = make(today)
        } else {
            preview.update { it?.takeUnless(isOurs) }
        }
    }

    private companion object {
        const val TAG = "SeasonalThemes"
    }
}
