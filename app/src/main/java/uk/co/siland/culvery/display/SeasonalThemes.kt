package uk.co.siland.culvery.display

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.ui.SeasonDates
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.ShowingTheme
import uk.co.siland.culvery.core.ui.defaultSeasonDates

/** A theme shown by hand for [day] only (14 §3); it counts as off on any other day. */
data class ThemePreview(val theme: SeasonalTheme, val day: LocalDate)

/** The preview's theme when it is for [date], else the first theme whose dates hold [date] (14 §4.1). */
internal fun showingOn(date: LocalDate, dates: Map<SeasonalTheme, SeasonDates>, preview: ThemePreview?): SeasonalTheme? =
    preview?.takeIf { it.day == date }?.theme
        ?: SeasonalTheme.entries.firstOrNull { (dates[it] ?: it.defaultDates).contains(date) }

/** Which seasonal theme the wall shows, by the household's date; the clock is read from [now], never the device's own. */
@Singleton
class SeasonalThemes internal constructor(
    private val now: StateFlow<LocalDateTime?>,
    dates: Flow<Map<SeasonalTheme, SeasonDates>>,
    scope: CoroutineScope,
) : ShowingTheme {
    @Inject
    constructor(clock: HouseholdClock, @ApplicationScope scope: CoroutineScope) : this(clock.now, flowOf(defaultSeasonDates()), scope)

    private val preview = MutableStateFlow<ThemePreview?>(null)

    val dates: StateFlow<Map<SeasonalTheme, SeasonDates>> = dates.stateIn(scope, SharingStarted.Eagerly, defaultSeasonDates())

    override val showing: StateFlow<SeasonalTheme?> =
        combine(now, this.dates, preview) { now, dates, preview -> now?.let { showingOn(it.toLocalDate(), dates, preview) } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** The theme previewed today, else null. */
    val previewing: StateFlow<SeasonalTheme?> =
        combine(now, preview) { now, preview -> preview?.takeIf { it.day == now?.toLocalDate() }?.theme }
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            now.collect { time ->
                val today = time?.toLocalDate() ?: return@collect
                preview.update { it?.takeIf { p -> p.day == today } }
            }
        }
    }

    /** Turning one on replaces any other; nothing happens while the clock is unread. Off clears only [theme]'s own preview. */
    fun preview(theme: SeasonalTheme, on: Boolean) {
        if (on) {
            val today = now.value?.toLocalDate() ?: return
            preview.value = ThemePreview(theme, today)
        } else {
            preview.update { it?.takeUnless { p -> p.theme == theme } }
        }
    }
}
