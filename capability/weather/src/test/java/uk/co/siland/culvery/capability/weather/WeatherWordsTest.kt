package uk.co.siland.culvery.capability.weather

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.ui.Icons

class WeatherWordsTest {
    private val hour = 3_600_000L

    @Test
    fun degreesAreWholeRoundedHalfUpAndNeverMinusZero() {
        assertThat(listOf(16.5, 16.49, 0.0, -0.4, -0.5, -1.5, -2.6).map(::degrees))
            .containsExactly("17°", "16°", "0°", "0°", "0°", "-1°", "-3°").inOrder()
    }

    @Test
    fun highAndLowReadAsTheHeaderLine() {
        assertThat(highLow(19.2, 10.6)).isEqualTo("High 19° · Low 11°")
    }

    @Test
    fun theAgeLineAppearsOnlyPastTwoHours() {
        val fetched = 1_000_000L
        fun ago(age: Long) = updatedAgo(fetched, fetched + age)
        assertThat(ago(2 * hour)).isNull()
        assertThat(ago(2 * hour + 1)).isEqualTo("Updated 2 h ago")
        assertThat(ago(3 * hour)).isEqualTo("Updated 3 h ago")
        assertThat(ago(24 * hour - 1)).isEqualTo("Updated 23 h ago")
        assertThat(ago(24 * hour)).isEqualTo("Updated 1 day ago")
        assertThat(ago(49 * hour)).isEqualTo("Updated 2 days ago")
    }

    @Test
    fun eachConditionHasItsIconAndOnlyClearAndPartlyCloudyChangeAtNight() {
        val day = Condition.entries.associateWith { weatherIcon(it, night = false) }
        assertThat(day).containsExactly(
            Condition.CLEAR, "sunny",
            Condition.PARTLY_CLOUDY, "partly_cloudy_day",
            Condition.CLOUDY, "cloud",
            Condition.FOG, "foggy",
            Condition.DRIZZLE, "rainy_light",
            Condition.RAIN, "rainy",
            Condition.SHOWERS, "rainy_heavy",
            Condition.SNOW, "weather_snowy",
            Condition.THUNDER, "thunderstorm",
        )
        val night = Condition.entries.associateWith { weatherIcon(it, night = true) }
        assertThat(night).isEqualTo(day + mapOf(Condition.CLEAR to Icons.BEDTIME, Condition.PARTLY_CLOUDY to "partly_cloudy_night"))
    }

    @Test
    fun rowsAreLabelledTodayThenShortDayNames() {
        assertThat(dayLabel(day(THU), isToday = true)).isEqualTo("Today")
        assertThat(dayLabel(day(FRI), isToday = false)).isEqualTo("Fri")
        assertThat(dayLabel(day(SAT), isToday = false)).isEqualTo("Sat")
    }

    @Test
    fun eachRowReadsAsOneSentence() {
        assertThat(rowDescription(day(FRI, Condition.PARTLY_CLOUDY, high = 17.2, low = 10.4), isToday = false))
            .isEqualTo("Friday, partly cloudy, high 17°, low 10°")
        assertThat(rowDescription(day(THU, Condition.THUNDER, high = 19.0, low = 11.0), isToday = true))
            .isEqualTo("Today, thunderstorms, high 19°, low 11°")
    }
}
