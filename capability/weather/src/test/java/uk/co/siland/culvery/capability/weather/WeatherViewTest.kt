package uk.co.siland.culvery.capability.weather

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.SunTimes

class WeatherViewTest {
    private val thuMorning = THU.atTime(10, 30)

    @Test
    fun withoutALocationItAsksForOneWhateverIsStored() {
        assertThat(weatherView(null, stored(), thuMorning)).isEqualTo(WeatherView.NoLocation)
    }

    @Test
    fun aLocationWithNothingStoredIsWaiting() {
        assertThat(weatherView(LONDON, null, thuMorning)).isEqualTo(WeatherView.Waiting)
    }

    @Test
    fun theOldTownsWeatherIsWaitingAfterAMove() {
        assertThat(weatherView(LEEDS, stored(LONDON), thuMorning)).isEqualTo(WeatherView.Waiting)
        assertThat(weatherView(LONDON.copy(timeZoneId = "Europe/Dublin"), stored(LONDON), thuMorning)).isEqualTo(WeatherView.Waiting)
    }

    @Test
    fun aRenamedTownKeepsItsWeather() {
        assertThat(weatherView(LONDON.copy(name = "Westminster"), stored(LONDON), thuMorning)).isInstanceOf(WeatherView.Ready::class.java)
    }

    /** Review Focus 1: a fetch that lands after a move is stored under the place it asked for, so it doesn't match. */
    @Test
    fun matchingComparesTheCoordinatesAndTheZoneNotTheName() {
        val s = stored(LONDON)
        assertThat(s.matching(LONDON)).isNotNull()
        assertThat(s.matching(LONDON.copy(name = "Westminster"))).isNotNull()
        assertThat(s.matching(LEEDS)).isNull()
        assertThat(s.matching(LONDON.copy(latitude = 51.5075))).isNull()
        assertThat(s.matching(LONDON.copy(longitude = -0.1279))).isNull()
        assertThat(s.matching(LONDON.copy(timeZoneId = "Europe/Dublin"))).isNull()
        assertThat(s.matching(null)).isNull()
        assertThat((null as StoredWeather?).matching(LONDON)).isNull()
    }

    @Test
    fun readyStartsAtTodayWithTheNextTwoDaysAndTheHourContainingNow() {
        val view = weatherView(LONDON, stored(forecast = forecast(from = THU.minusDays(1))), thuMorning) as WeatherView.Ready
        assertThat(view.today.date).isEqualTo(THU)
        assertThat(view.days.map { it.date }).containsExactly(THU, FRI, SAT).inOrder()
        assertThat(view.now?.start).isEqualTo(THU.atTime(10, 0))
    }

    @Test
    fun theHourStartingNowIsTheOneShown() {
        val view = weatherView(LONDON, stored(), THU.atTime(11, 0)) as WeatherView.Ready
        assertThat(view.now?.start).isEqualTo(THU.atTime(11, 0))
    }

    @Test
    fun dataThatRunsOutBeforeTodayIsExpired() {
        assertThat(weatherView(LONDON, stored(forecast = forecast(from = THU.minusDays(7))), thuMorning)).isEqualTo(WeatherView.Expired)
    }

    @Test
    fun midnightMovesTodayOnAndDropsADay() {
        val data = stored(forecast = forecast(from = THU, days = 3))
        val beforeMidnight = weatherView(LONDON, data, THU.atTime(23, 59)) as WeatherView.Ready
        assertThat(beforeMidnight.days.map { it.date }).containsExactly(THU, FRI, SAT).inOrder()
        val afterMidnight = weatherView(LONDON, data, FRI.atStartOfDay()) as WeatherView.Ready
        assertThat(afterMidnight.today.date).isEqualTo(FRI)
        assertThat(afterMidnight.days.map { it.date }).containsExactly(FRI, SAT).inOrder()
        assertThat(afterMidnight.now?.start).isEqualTo(FRI.atStartOfDay())
    }

    @Test
    fun fewerThanThreeDaysLeftShowsThoseThatExist() {
        val view = weatherView(LONDON, stored(forecast = forecast(from = THU.minusDays(5))), thuMorning) as WeatherView.Ready
        assertThat(view.days.map { it.date }).containsExactly(THU, FRI).inOrder()
    }

    @Test
    fun aMissingHourLeavesNowEmptyAndTheRestReady() {
        val f = forecast()
        val data = stored(forecast = f.copy(hours = f.hours.filterNot { it.start == THU.atTime(10, 0) }))
        val view = weatherView(LONDON, data, thuMorning) as WeatherView.Ready
        assertThat(view.now).isNull()
        assertThat(view.today.date).isEqualTo(THU)
    }

    /** A day whose 01:00 never comes (the clocks go forward) has 00:00, then 02:00: the lookup leaves the gap empty. */
    @Test
    fun theHourContainingNowIsFoundAcrossAGap() {
        val hours = listOf(HourlyWeather(THU.atTime(0, 0), Condition.CLEAR, 9.0), HourlyWeather(THU.atTime(2, 0), Condition.CLEAR, 8.0))
        val data = stored(forecast = Forecast(listOf(day(THU)), hours))
        assertThat((weatherView(LONDON, data, THU.atTime(2, 15)) as WeatherView.Ready).now?.temperature).isEqualTo(8.0)
        assertThat((weatherView(LONDON, data, THU.atTime(1, 30)) as WeatherView.Ready).now).isNull()
    }

    @Test
    fun theFetchTimeIsCarriedForTheAgeLine() {
        val view = weatherView(LONDON, stored(fetchedAtMillis = 1_234L), thuMorning) as WeatherView.Ready
        assertThat(view.fetchedAtMillis).isEqualTo(1_234L)
    }

    @Test
    fun oldMatchingDataStillGivesTodaysSunTimes() {
        val data = stored(forecast = forecast(from = THU.minusDays(5)), fetchedAtMillis = 0L)
        assertThat(sunTimesOn(LONDON, data, THU)).isEqualTo(SunTimes(SUNRISE, SUNSET))
    }

    @Test
    fun sunTimesNeedMatchingDataForToday() {
        assertThat(sunTimesOn(LEEDS, stored(LONDON), THU)).isNull()
        assertThat(sunTimesOn(LONDON, null, THU)).isNull()
        assertThat(sunTimesOn(null, stored(), THU)).isNull()
        assertThat(sunTimesOn(LONDON, stored(forecast = forecast(from = THU.minusDays(7))), THU)).isNull()
    }

    @Test
    fun aMissingSunriseOrSunsetGivesNoSunTimes() {
        val noSunrise = stored(forecast = Forecast(listOf(day(THU, sunrise = null)), emptyList()))
        val noSunset = stored(forecast = Forecast(listOf(day(THU, sunset = null)), emptyList()))
        assertThat(sunTimesOn(LONDON, noSunrise, THU)).isNull()
        assertThat(sunTimesOn(LONDON, noSunset, THU)).isNull()
    }

    @Test
    fun theHeaderShowsOnlyWhenReadyWithTheHourNow() {
        assertThat(headerWeather(WeatherView.Waiting, thuMorning)).isNull()
        assertThat(headerWeather(WeatherView.Expired, thuMorning)).isNull()
        assertThat(headerWeather(WeatherView.NoLocation, thuMorning)).isNull()
        assertThat(headerWeather(READY.copy(now = null), thuMorning)).isNull()
        assertThat(headerWeather(READY, thuMorning))
            .isEqualTo(HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0))
    }

    @Test
    fun itIsNightBeforeSunriseAndFromSunset() {
        fun nightAt(hour: Int, minute: Int) = headerWeather(READY, THU.atTime(hour, minute))!!.night
        assertThat(nightAt(7, 0)).isTrue()
        assertThat(nightAt(7, 1)).isFalse()
        assertThat(nightAt(18, 37)).isFalse()
        assertThat(nightAt(18, 38)).isTrue()
    }

    @Test
    fun withoutSunTimesTheHeaderUsesDayIcons() {
        val unknown = READY.copy(today = day(THU, sunrise = null, sunset = null))
        assertThat(headerWeather(unknown, THU.atTime(23, 0))!!.night).isFalse()
    }

    @Test
    fun anInvalidZoneIdAsksInTheDeviceZone() {
        assertThat(WeatherPlace(LONDON.copy(timeZoneId = "Not/AZone")).zone).isEqualTo(java.time.ZoneId.systemDefault())
        assertThat(WeatherPlace(WELLINGTON).zone).isEqualTo(java.time.ZoneId.of("Pacific/Auckland"))
    }
}
