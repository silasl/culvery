package uk.co.siland.culvery.capability.weather

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.LightColors

class WeatherWordsTest {
    @Test
    fun degreesAreWholeRoundedHalfUpAndNeverMinusZero() {
        assertThat(listOf(16.5, 16.49, 0.0, -0.4, -0.5, -1.5, -2.6).map(::degrees))
            .containsExactly("17°", "16°", "0°", "0°", "0°", "-1°", "-3°").inOrder()
    }

    @Test
    fun theHeaderIsOneSentence() {
        assertThat(headerDescription(HeaderWeather.Now(Condition.PARTLY_CLOUDY, night = false, temperature = 17.2, high = 19.0, low = 10.6, days = emptyList())))
            .isEqualTo("Now 17°, partly cloudy, high 19°, low 11°")
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

    @Test
    fun rainIsBlueAndSunIsAmber() {
        for (colors in listOf(DarkColors, LightColors)) {
            val wet = listOf(Condition.DRIZZLE, Condition.RAIN, Condition.SHOWERS, Condition.THUNDER)
            val dry = listOf(Condition.CLEAR, Condition.PARTLY_CLOUDY, Condition.CLOUDY, Condition.FOG, Condition.SNOW)
            assertThat(wet.map { weatherTint(it, colors) }).containsExactly(colors.rain, colors.rain, colors.rain, colors.rain)
            assertThat(dry.map { weatherTint(it, colors) }).containsExactly(colors.weather, colors.weather, colors.weather, colors.weather, colors.weather)
        }
    }
}
