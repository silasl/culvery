package uk.co.siland.culvery.provider.weather_openmeteo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.DailyWeather
import uk.co.siland.culvery.capability.weather.Forecast
import uk.co.siland.culvery.capability.weather.HourlyWeather
import uk.co.siland.culvery.capability.weather.WeatherUnavailableException
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.weather_openmeteo.di.forecastClient

/** One day and one hour, as the members of `daily` and `hourly`. */
private const val DAILY_ONE =
    """ "time":["2026-10-01"],"weather_code":[3],"temperature_2m_max":[19.0],"temperature_2m_min":[11.0],"sunrise":["2026-10-01T07:01"],"sunset":["2026-10-01T18:38"]"""
private const val HOURLY_ONE = """ "time":["2026-10-01T00:00"],"temperature_2m":[17.0],"weather_code":[3]"""

// Robolectric for android.util.Log; the server is a real MockWebServer on localhost.
@RunWith(AndroidJUnit4::class)
class OpenMeteoForecastTest {
    private lateinit var server: MockWebServer
    private lateinit var forecast: OpenMeteoForecast
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()

    // 10:00 on Thursday 1 October 2026 in London: the week asked for is 1–7 October, the fixture's.
    private val clock = WallClock { Instant.parse("2026-10-01T09:00:00Z").toEpochMilli() }

    @Before
    fun setUp() {
        ShadowLog.clear()
        server = MockWebServer()
        server.start()
        forecast = OpenMeteoForecast(server.url("/v1/forecast"), client, clock)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun fixture(): String = checkNotNull(javaClass.classLoader?.getResource("forecast_london.json")) { "fixture missing" }.readText()

    private fun small(daily: String = DAILY_ONE, hourly: String = HOURLY_ONE) = """{"daily":{$daily},"hourly":{$hourly}}"""

    private suspend fun london(): Forecast = forecast.forecast(51.5074, -0.1278, ZoneId.of("Europe/London"))

    private suspend fun failure(): Throwable? =
        try {
            london()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }

    @Test
    fun itAsksForSevenDaysOfDailyAndHourlyWeatherInTheZone() = runTest {
        answer(fixture())
        london()
        val url = server.takeRequest().requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/v1/forecast")
        assertThat(listOf("latitude", "longitude", "timezone", "forecast_days", "daily", "hourly").map { url.queryParameter(it) })
            .containsExactly(
                "51.5074",
                "-0.1278",
                "Europe/London",
                "7",
                "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset",
                "temperature_2m,weather_code",
            ).inOrder()
    }

    @Test
    fun theRecordedAnswerReadsIntoDaysAndHours() = runTest {
        answer(fixture())
        val f = london()
        assertThat(f.days).hasSize(7)
        assertThat(f.days.first())
            .isEqualTo(DailyWeather(LocalDate.of(2026, 10, 1), Condition.DRIZZLE, 20.3, 14.0, LocalTime.of(7, 1), LocalTime.of(18, 38)))
        assertThat(f.days[3])
            .isEqualTo(DailyWeather(LocalDate.of(2026, 10, 4), Condition.FOG, 20.8, 13.8, LocalTime.of(7, 6), LocalTime.of(18, 31)))
        assertThat(f.days.last().date).isEqualTo(LocalDate.of(2026, 10, 7))
        assertThat(f.hours).hasSize(48)
        assertThat(f.hours[0]).isEqualTo(HourlyWeather(LocalDateTime.of(2026, 10, 1, 0, 0), Condition.PARTLY_CLOUDY, 18.3))
        assertThat(f.hours[1]).isEqualTo(HourlyWeather(LocalDateTime.of(2026, 10, 1, 1, 0), Condition.DRIZZLE, 16.7))
        assertThat(f.hours.last().start).isEqualTo(LocalDateTime.of(2026, 10, 2, 23, 0))
    }

    @Test
    fun wmoCodesMapOntoConditions() {
        val expected = mapOf(
            0 to Condition.CLEAR,
            1 to Condition.PARTLY_CLOUDY, 2 to Condition.PARTLY_CLOUDY,
            3 to Condition.CLOUDY,
            45 to Condition.FOG, 48 to Condition.FOG,
            51 to Condition.DRIZZLE, 53 to Condition.DRIZZLE, 55 to Condition.DRIZZLE, 56 to Condition.DRIZZLE, 57 to Condition.DRIZZLE,
            61 to Condition.RAIN, 63 to Condition.RAIN, 65 to Condition.RAIN, 66 to Condition.RAIN, 67 to Condition.RAIN,
            71 to Condition.SNOW, 73 to Condition.SNOW, 75 to Condition.SNOW, 77 to Condition.SNOW, 85 to Condition.SNOW, 86 to Condition.SNOW,
            80 to Condition.SHOWERS, 81 to Condition.SHOWERS, 82 to Condition.SHOWERS,
            95 to Condition.THUNDER, 96 to Condition.THUNDER, 99 to Condition.THUNDER,
            // Anything else is cloudy.
            4 to Condition.CLOUDY, 44 to Condition.CLOUDY, 100 to Condition.CLOUDY, -1 to Condition.CLOUDY,
        )
        assertThat(expected.keys.associateWith(::wmoCondition)).isEqualTo(expected)
    }

    /** Ruling 1: Open-Meteo gives a polar night as midnight to midnight, and a polar day as midnight to the next midnight. */
    @Test
    fun aPolarDayOrNightHasNoSunTimes() = runTest {
        answer(
            small(
                daily = """ "time":["2026-10-01","2026-10-02"],"weather_code":[71,0],"temperature_2m_max":[-0.5,9.0],"temperature_2m_min":[-6.0,3.0],"sunrise":["2026-10-01T00:00","2026-10-02T00:00"],"sunset":["2026-10-01T00:00","2026-10-03T00:00"]""",
            ),
        )
        val days = london().days
        assertThat(days.map { it.sunrise }).containsExactly(null, null)
        assertThat(days.map { it.sunset }).containsExactly(null, null)
    }

    /** Review Focus 4: a null entry drops that day or hour; a null sun time leaves the day without sun times. */
    @Test
    fun nullEntriesDropThatDayOrHourAndNothingElse() = runTest {
        answer(
            small(
                daily = """ "time":["2026-10-01","2026-10-02","2026-10-03","2026-10-04"],"weather_code":[3,null,61,2],"temperature_2m_max":[19.0,18.0,null,17.0],"temperature_2m_min":[11.0,10.0,9.0,8.0],"sunrise":["2026-10-01T07:01","2026-10-02T07:02","2026-10-03T07:04",null],"sunset":["2026-10-01T18:38","2026-10-02T18:36","2026-10-03T18:33","2026-10-04T18:31"]""",
                hourly = """ "time":["2026-10-01T00:00","2026-10-01T01:00","2026-10-01T02:00"],"temperature_2m":[17.0,null,15.0],"weather_code":[3,3,null]""",
            ),
        )
        val f = london()
        assertThat(f.days).containsExactly(
            DailyWeather(LocalDate.of(2026, 10, 1), Condition.CLOUDY, 19.0, 11.0, LocalTime.of(7, 1), LocalTime.of(18, 38)),
            DailyWeather(LocalDate.of(2026, 10, 4), Condition.PARTLY_CLOUDY, 17.0, 8.0, null, null),
        ).inOrder()
        assertThat(f.hours).containsExactly(HourlyWeather(LocalDateTime.of(2026, 10, 1, 0, 0), Condition.CLOUDY, 17.0))
    }

    @Test
    fun anErrorAnswerIsWeatherUnavailable() = runTest {
        answer("""{"reason":"Latitude must be in range of -90 to 90°.","error":true}""", code = 400)
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer("{}", code = 500)
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun aDroppedConnectionIsWeatherUnavailable() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun anUnreadableAnswerIsWeatherUnavailable() = runTest {
        answer("<html>Gateway</html>")
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer(small(daily = DAILY_ONE.replace("[19.0]", "[\"warm\"]")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer(small(hourly = HOURLY_ONE.replace("2026-10-01T00:00", "yesterday")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun aMissingArrayIsWeatherUnavailable() = runTest {
        answer("""{"daily":{$DAILY_ONE}}""")
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer(small(daily = DAILY_ONE.substringBefore(""","sunset"""")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun arraysOfUnequalLengthAreWeatherUnavailable() = runTest {
        answer(small(daily = DAILY_ONE.replace(""""time":["2026-10-01"]""", """"time":["2026-10-01","2026-10-02"]""")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer(small(hourly = HOURLY_ONE.replace(""""weather_code":[3]""", """"weather_code":[3,3]""")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun cancellingTheForecastCancelsTheCall() = runTest {
        // Its own client with a long read timeout, so only cancelling the call can end it inside the second.
        val patient = OkHttpClient.Builder().readTimeout(Duration.ofSeconds(30)).build()
        val slow = OpenMeteoForecast(server.url("/v1/forecast"), patient, clock)
        // One byte every 3 s: the headers arrive, the body stalls.
        server.enqueue(MockResponse().setBody(fixture()).throttleBody(1, 3, TimeUnit.SECONDS))
        val returned = withContext(Dispatchers.Default) {
            val call = launch { slow.forecast(51.5074, -0.1278, ZoneId.of("Europe/London")) }
            checkNotNull(runInterruptible(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }) { "the forecast never reached the server" }
            call.cancel()
            withTimeoutOrNull(1_000) { call.join() } != null
        }
        assertThat(returned).isTrue()
        withContext(Dispatchers.Default) { withTimeout(1_000) { while (patient.dispatcher.runningCallsCount() > 0) delay(10) } }
    }

    @Test
    fun theForecastClientGivesUpOnAWholeCallAfterSixtySeconds() {
        assertThat(forecastClient().callTimeoutMillis).isEqualTo(60_000)
    }

    /** Plan review 2: each byte arrives inside the read timeout, so only the whole-call limit can end it. */
    @Test
    fun aDrippingBodyEndsAsWeatherUnavailable() = runTest {
        // The module's client with its 60 s call limit cut to 1 s, so the test doesn't wait a minute.
        val hurried = forecastClient().newBuilder().callTimeout(Duration.ofSeconds(1)).build()
        val dripping = OpenMeteoForecast(server.url("/v1/forecast"), hurried, clock)
        server.enqueue(MockResponse().setBody(fixture()).throttleBody(1, 300, TimeUnit.MILLISECONDS))
        val thrown = withContext(Dispatchers.Default) {
            withTimeout(10_000) { runCatching { dripping.forecast(51.5074, -0.1278, ZoneId.of("Europe/London")) }.exceptionOrNull() }
        }
        assertThat(thrown).isInstanceOf(WeatherUnavailableException::class.java)
    }

    /** The recorded answer with an unknown key's string value padding it to exactly [bytes] bytes: valid, so only a cap can refuse it. */
    private fun padded(bytes: Long): String {
        val head = fixture().trimEnd().removeSuffix("}") + ""","pad":""""
        val tail = "\"}"
        val fill = bytes - (head + tail).toByteArray().size
        return head + "x".repeat(fill.toInt()) + tail
    }

    private fun loggedBodyTooLarge() = ShadowLog.getLogs().filter { it.tag == TAG }.any { "BodyTooLarge" in it.msg }

    /** Plan review 13. */
    @Test
    fun anAnswerOverOneMebibyteIsWeatherUnavailableWhetherOrNotItsLengthIsKnown() = runTest {
        val tooBig = padded(MAX_FORECAST_BYTES + 1)
        assertThat(tooBig.toByteArray().size.toLong()).isEqualTo(MAX_FORECAST_BYTES + 1)
        answer(tooBig)
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        assertThat(loggedBodyTooLarge()).isTrue()

        // Without a length up front, the read still stops one byte past the limit.
        ShadowLog.clear()
        server.enqueue(MockResponse().setChunkedBody(tooBig, 64 * 1024))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        assertThat(loggedBodyTooLarge()).isTrue()
    }

    /** The announced length alone refuses it: the body that follows is one byte, so only the early check can name BodyTooLarge. */
    @Test
    fun anAnnouncedLengthOverOneMebibyteIsRefusedBeforeReadingTheBody() = runTest {
        server.enqueue(MockResponse().setBody("x").setHeader("Content-Length", MAX_FORECAST_BYTES + 1))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        assertThat(loggedBodyTooLarge()).isTrue()
    }

    @Test
    fun anAnswerOfExactlyOneMebibyteIsRead() = runTest {
        answer(padded(MAX_FORECAST_BYTES))
        assertThat(london().days).hasSize(7)
        server.enqueue(MockResponse().setChunkedBody(padded(MAX_FORECAST_BYTES), 64 * 1024))
        assertThat(london().days).hasSize(7)
    }

    /** Plan review 13: only the seven dates from today in the zone asked for are kept. */
    @Test
    fun daysAndHoursOutsideTheSevenAskedForAreDropped() = runTest {
        answer(
            small(
                daily = """ "time":["2026-09-30","2026-10-01","2026-10-08"],"weather_code":[3,3,3],"temperature_2m_max":[19.0,19.0,19.0],"temperature_2m_min":[11.0,11.0,11.0],"sunrise":[null,null,null],"sunset":[null,null,null]""",
                hourly = """ "time":["2026-09-30T23:00","2026-10-01T00:00","2026-10-07T23:00","2026-10-08T00:00"],"temperature_2m":[17.0,17.0,17.0,17.0],"weather_code":[3,3,3,3]""",
            ),
        )
        val f = london()
        assertThat(f.days.map { it.date }).containsExactly(LocalDate.of(2026, 10, 1))
        assertThat(f.hours.map { it.start })
            .containsExactly(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 7, 23, 0))
            .inOrder()
    }

    @Test
    fun nothingLoggedHoldsTheCoordinatesOrTheZone() = runTest {
        answer("{}", code = 500)
        failure()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        failure()
        answer("""{"latitude":51.51147,"daily":{"time":"Europe/London"}}""")
        failure()
        assertNoSecretsLogged(
            TAG,
            listOf("51.5074", "51.51", "-0.1278", "0.1278", "Europe/London", "Europe%2FLondon", "London", "latitude"),
            minLines = 3,
        )
    }
}
