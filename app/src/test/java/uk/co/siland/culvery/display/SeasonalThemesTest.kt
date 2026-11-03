package uk.co.siland.culvery.display

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.shadows.ShadowLog
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.Birthday
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.SeasonDates
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.defaultSeasonDates

/** 14 §2.1, §3: which theme shows, and a preview that ends at the next midnight. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
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
        val preview = Preview.Theme(SeasonalTheme.HALLOWEEN, LocalDate.of(2026, 11, 4))
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

    private fun date(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    private fun halloweenOn(start: MonthDay, end: MonthDay) = mapOf(SeasonalTheme.HALLOWEEN to SeasonDates(start, end))

    /** Fails if edited dates don't decide what shows. */
    @Test
    fun editedDatesMoveTheTheme() {
        val edited = halloweenOn(MonthDay.of(11, 1), MonthDay.of(11, 5))
        assertThat(showingOn(date(2026, 11, 3), edited, null)).isEqualTo(SeasonalTheme.HALLOWEEN)
        assertThat(showingOn(date(2026, 10, 29), edited, null)).isNull()
    }

    /** Fails if the next theme isn't the soonest start, this year's or next year's. */
    @Test
    fun nextIsTheSoonestStart() {
        val defaults = defaultSeasonDates()
        assertThat(nextTheme(date(2026, 11, 5), defaults)).isEqualTo(SeasonalTheme.HALLOWEEN to date(2027, 10, 24))
        assertThat(nextTheme(date(2026, 10, 1), defaults)).isEqualTo(SeasonalTheme.HALLOWEEN to date(2026, 10, 24))
    }

    /** Fails if a start across the new year is placed in the wrong year. */
    @Test
    fun nextWrapsTheNewYear() {
        val christmas = halloweenOn(MonthDay.of(12, 20), MonthDay.of(1, 2))
        assertThat(nextTheme(date(2026, 12, 1), christmas)?.second).isEqualTo(date(2026, 12, 20))
        assertThat(nextTheme(date(2027, 1, 3), christmas)?.second).isEqualTo(date(2027, 12, 20))
    }

    /** Fails if `next` shows while a theme is in its dates or the clock is unread, or doesn't follow the clock. */
    @Test
    fun nextShowsOnlyWhileNothingShows() = runTest {
        val now = MutableStateFlow<LocalDateTime?>(null)
        val themes = themes(now)
        assertThat(themes.next.value).isNull()
        moveTo(now, at(10, 29, 12, 0))
        assertThat(themes.next.value).isNull()
        moveTo(now, at(11, 5, 12, 0))
        assertThat(themes.next.value).isEqualTo(SeasonalTheme.HALLOWEEN to date(2027, 10, 24))
    }

    /** Fails if `next` keeps the default dates after the household edits them. */
    @Test
    fun nextFollowsEditedDates() = runTest {
        val now = MutableStateFlow<LocalDateTime?>(at(10, 29, 12, 0))
        val edited = halloweenOn(MonthDay.of(11, 1), MonthDay.of(11, 5))
        val themes = SeasonalThemes(now, flowOf(edited), backgroundScope)
        runCurrent()
        assertThat(themes.next.value).isEqualTo(SeasonalTheme.HALLOWEEN to date(2026, 11, 1))
    }

    private val sam = Person(PersonId("sam"), "Sam", PersonPalette.colors[2])
    private val alex = Person(PersonId("alex"), "Alex", PersonPalette.colors[1])
    private val samDress = WallDress(birthday = Birthday(sam.color, listOf("Sam")))

    private fun TestScope.dressed(
        now: MutableStateFlow<LocalDateTime?>,
        birthdays: List<Person> = emptyList(),
        people: List<Person> = listOf(sam, alex),
    ): SeasonalThemes {
        val themes = SeasonalThemes(now, flowOf(defaultSeasonDates()), backgroundScope, flowOf(birthdays), flowOf(people))
        runCurrent()
        return themes
    }

    /** Fails unless a theme preview, a birthday preview, a real birthday and a theme's dates win in that order. */
    @Test
    fun dressOnFollowsTheOrder() {
        val day = date(2026, 3, 12)
        assertThat(dressOn(Preview.Theme(SeasonalTheme.HALLOWEEN, day), sam, listOf(alex), null))
            .isEqualTo(WallDress(seasonal = SeasonalTheme.HALLOWEEN))
        assertThat(dressOn(Preview.Birthday(day), sam, listOf(alex), SeasonalTheme.HALLOWEEN)).isEqualTo(samDress)
        assertThat(dressOn(null, sam, listOf(sam, alex), SeasonalTheme.HALLOWEEN))
            .isEqualTo(WallDress(birthday = Birthday(sam.color, listOf("Sam", "Alex"))))
        assertThat(dressOn(null, sam, listOf(alex), SeasonalTheme.HALLOWEEN)).isEqualTo(WallDress(birthday = Birthday(alex.color, listOf("Alex"))))
        assertThat(dressOn(null, sam, emptyList(), SeasonalTheme.HALLOWEEN)).isEqualTo(WallDress(seasonal = SeasonalTheme.HALLOWEEN))
    }

    /** Pin: nothing on, nothing worn. */
    @Test
    fun nothingOnDressesNothing() {
        assertThat(dressOn(null, sam, emptyList(), null)).isEqualTo(WallDress())
    }

    /** Fails if Halloween beats a birthday on 31 October, or a birthday moves the doorbell's theme. */
    @Test
    fun aBirthdayOn31OctoberBeatsHalloween() = runTest {
        val themes = dressed(MutableStateFlow(at(10, 31, 12, 0)), birthdays = listOf(sam))
        assertThat(themes.dress.value).isEqualTo(samDress)
        assertThat(themes.showing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
    }

    /** Fails if a real birthday beats a theme's preview. */
    @Test
    fun aHalloweenPreviewBeatsARealBirthday() = runTest {
        val themes = dressed(MutableStateFlow(at(3, 12, 12, 0)), birthdays = listOf(sam))
        assertThat(themes.dress.value).isEqualTo(samDress)
        themes.preview(SeasonalTheme.HALLOWEEN, true)
        runCurrent()
        assertThat(themes.dress.value).isEqualTo(WallDress(seasonal = SeasonalTheme.HALLOWEEN))
    }

    /** Fails if the two previews coexist, or the second doesn't replace the first. */
    @Test
    fun previewsReplaceEachOther() = runTest {
        val themes = dressed(MutableStateFlow(at(3, 12, 12, 0)))
        themes.preview(SeasonalTheme.HALLOWEEN, true)
        runCurrent()
        themes.previewBirthday(true)
        runCurrent()
        assertThat(themes.previewing.value).isNull()
        assertThat(themes.previewingBirthday.value).isTrue()
        assertThat(themes.dress.value).isEqualTo(samDress)
        themes.preview(SeasonalTheme.HALLOWEEN, true)
        runCurrent()
        assertThat(themes.previewingBirthday.value).isFalse()
        assertThat(themes.previewing.value).isEqualTo(SeasonalTheme.HALLOWEEN)
        themes.previewBirthday(true)
        runCurrent()
        themes.preview(SeasonalTheme.HALLOWEEN, false)
        runCurrent()
        assertThat(themes.previewingBirthday.value).isTrue()
        themes.previewBirthday(false)
        runCurrent()
        assertThat(themes.dress.value).isEqualTo(WallDress())
    }

    /** Fails if a birthday preview outlives the day. */
    @Test
    fun aBirthdayPreviewEndsAtMidnight() = runTest {
        val now = MutableStateFlow<LocalDateTime?>(at(3, 12, 23, 59))
        val themes = dressed(now)
        themes.previewBirthday(true)
        runCurrent()
        assertThat(themes.dress.value).isEqualTo(samDress)
        moveTo(now, at(3, 13, 0, 0))
        assertThat(themes.dress.value).isEqualTo(WallDress())
        assertThat(themes.previewingBirthday.value).isFalse()
    }

    /** Fails if a birthday preview reaches the doorbell's theme. */
    @Test
    fun theDoorbellFollowsOnlyTheSeason() = runTest {
        val themes = dressed(MutableStateFlow(at(3, 12, 12, 0)))
        themes.previewBirthday(true)
        runCurrent()
        assertThat(themes.dress.value).isEqualTo(samDress)
        assertThat(themes.showing.value).isNull()
    }

    /** Fails if the preview dresses the wall for nobody. */
    @Test
    fun noPreviewWithNobody() = runTest {
        val themes = dressed(MutableStateFlow(at(3, 12, 12, 0)), people = emptyList())
        assertThat(themes.previewPerson.value).isNull()
        themes.previewBirthday(true)
        runCurrent()
        assertThat(themes.dress.value).isEqualTo(WallDress())
    }

    /** Fails if the preview starts before the household's date is known. */
    @Test
    fun nothingPreviewsBeforeTheClockIsRead() = runTest {
        val themes = dressed(MutableStateFlow(null))
        themes.previewBirthday(true)
        runCurrent()
        assertThat(themes.previewingBirthday.value).isFalse()
        assertThat(themes.dress.value).isEqualTo(WallDress())
    }

    /** Fails if the look logs anything but its two lines, once per change, or any name. */
    @Test
    fun theBirthdayLookLogsOnlyChanges() = runTest {
        ShadowLog.clear()
        val now = MutableStateFlow<LocalDateTime?>(at(3, 12, 12, 0))
        val themes = dressed(now)
        themes.previewBirthday(true)
        runCurrent()
        themes.previewBirthday(true)
        runCurrent()
        moveTo(now, at(3, 13, 0, 0))
        val lines = ShadowLog.getLogsForTag("SeasonalThemes").map { it.msg }
        assertThat(lines).containsExactly("Birthday look on for 1 people", "Birthday look off").inOrder()
    }
}
