package uk.co.siland.culvery.display

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.defaultSeasonDates

/** 14 §2.1, §3: which theme shows, and a preview that ends at the next midnight. */
@OptIn(ExperimentalCoroutinesApi::class)
class SeasonalThemesTest {
    private fun at(month: Int, day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, month, day, hour, minute)

    private fun TestScope.themes(now: MutableStateFlow<LocalDateTime?>): SeasonalThemes {
        val themes = SeasonalThemes(now, flowOf(defaultSeasonDates()), backgroundScope)
        runCurrent()
        return themes
    }

    private fun TestScope.moveTo(now: MutableStateFlow<LocalDateTime?>, to: LocalDateTime?) {
        now.value = to
        runCurrent()
    }

    @Test
    fun halloweenShowsInItsDates() = runTest {
        val now = MutableStateFlow<LocalDateTime?>(at(10, 29, 12, 0))
        val themes = themes(now)
        assertThat(themes.showing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
        moveTo(now, at(10, 23, 23, 59))
        assertThat(themes.showing.value).isNull()
    }

    @Test
    fun halloweenEndsAsNovemberStarts() = runTest {
        val now = MutableStateFlow<LocalDateTime?>(at(10, 31, 23, 59))
        val themes = themes(now)
        assertThat(themes.showing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
        moveTo(now, at(11, 1, 0, 0))
        assertThat(themes.showing.value).isNull()
    }

    @Test
    fun aPreviewShowsOutsideTheDates() = runTest {
        val now = MutableStateFlow<LocalDateTime?>(at(11, 5, 10, 0))
        val themes = themes(now)
        assertThat(themes.showing.value).isNull()
        themes.preview(SeasonalTheme.HALLOWEEN, true)
        runCurrent()
        assertThat(themes.showing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
        assertThat(themes.previewing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
        themes.preview(SeasonalTheme.HALLOWEEN, false)
        runCurrent()
        assertThat(themes.showing.value).isNull()
        assertThat(themes.previewing.value).isNull()
    }

    @Test
    fun aPreviewEndsAtMidnight() = runTest {
        val now = MutableStateFlow<LocalDateTime?>(at(11, 5, 23, 59))
        val themes = themes(now)
        themes.preview(SeasonalTheme.HALLOWEEN, true)
        runCurrent()
        assertThat(themes.showing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
        moveTo(now, at(11, 6, 0, 0))
        assertThat(themes.showing.value).isNull()
        assertThat(themes.previewing.value).isNull()
    }

    @Test
    fun aPreviewEndsWhenTheClockGoesBackADay() = runTest {
        val now = MutableStateFlow<LocalDateTime?>(at(11, 5, 0, 1))
        val themes = themes(now)
        themes.preview(SeasonalTheme.HALLOWEEN, true)
        runCurrent()
        assertThat(themes.showing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
        moveTo(now, at(11, 4, 23, 59))
        assertThat(themes.showing.value).isNull()
        assertThat(themes.previewing.value).isNull()
    }

    @Test
    fun nothingShowsBeforeTheClockIsRead() = runTest {
        val now = MutableStateFlow<LocalDateTime?>(null)
        val themes = themes(now)
        assertThat(themes.showing.value).isNull()
        themes.preview(SeasonalTheme.HALLOWEEN, true)
        runCurrent()
        assertThat(themes.previewing.value).isNull()
    }

    @Test
    fun aPreviewFromAnotherDayIsIgnored() {
        val preview = ThemePreview(SeasonalTheme.HALLOWEEN, LocalDate.of(2026, 11, 4))
        assertThat(showingOn(LocalDate.of(2026, 11, 5), defaultSeasonDates(), preview)).isNull()
    }

    @Test
    fun followsTheHouseholdClockOnVirtualTime() = runTest {
        val start = Instant.parse("2026-10-31T23:58:00Z").toEpochMilli()
        val clock = HouseholdClock(flowOf(ZoneOffset.UTC), WallClock { start + testScheduler.currentTime }, backgroundScope)
        val themes = SeasonalThemes(clock.now, flowOf(defaultSeasonDates()), backgroundScope)
        runCurrent()
        assertThat(themes.showing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
        advanceTimeBy(60_000)
        runCurrent()
        assertThat(clock.now.value).isEqualTo(at(10, 31, 23, 59))
        assertThat(themes.showing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
        advanceTimeBy(60_000)
        runCurrent()
        assertThat(clock.now.value).isEqualTo(at(11, 1, 0, 0))
        assertThat(themes.showing.value).isNull()
    }
}
