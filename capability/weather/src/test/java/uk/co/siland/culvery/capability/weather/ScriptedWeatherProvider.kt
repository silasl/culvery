package uk.co.siland.culvery.capability.weather

import java.time.ZoneId
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

/** Answers with [answer] (which may throw) and records each request. */
internal class ScriptedWeatherProvider(id: String = "weather.test", private val answer: () -> Forecast = { forecast() }) : WeatherProvider {
    override val descriptor = ProviderDescriptor(id, id, "cloud", setOf(Feature.READ))
    val asked = mutableListOf<Triple<Double, Double, ZoneId>>()

    override suspend fun forecast(latitude: Double, longitude: Double, zone: ZoneId): Forecast {
        asked += Triple(latitude, longitude, zone)
        return answer()
    }
}
