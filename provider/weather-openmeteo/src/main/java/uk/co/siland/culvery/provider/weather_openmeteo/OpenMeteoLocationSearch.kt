package uk.co.siland.culvery.provider.weather_openmeteo

import android.util.Log
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.core.setup.LocationSearchException
import uk.co.siland.culvery.core.setup.PlaceMatch

/** Open-Meteo's geocoding API: no key (4a design D2). */
const val OPEN_METEO_GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"

/** The module's one log tag. */
internal const val TAG = "OpenMeteo"

private const val RESULTS = "5"

@Serializable
internal data class GeocodingAnswer(val results: List<GeocodingPlace> = emptyList())

@Serializable
internal data class GeocodingPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    val admin1: String? = null,
    val country: String? = null,
)

private val GeocodingJson = Json { ignoreUnknownKeys = true }

/**
 * Town search over Open-Meteo's geocoding (4a design §3.8): up to five towns, English names; one without a time zone is
 * left out, as a home needs one. Nothing logged names the query, a town or its coordinates.
 */
class OpenMeteoLocationSearch(private val url: HttpUrl, private val client: OkHttpClient) : LocationSearch {
    override suspend fun search(query: String): List<PlaceMatch> {
        val request = Request.Builder()
            .url(
                url.newBuilder()
                    .addQueryParameter("name", query)
                    .addQueryParameter("count", RESULTS)
                    .addQueryParameter("language", "en")
                    .addQueryParameter("format", "json")
                    .build(),
            )
            .build()
        val answer = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            // A cancelled caller gets its cancellation, never "couldn't search".
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Town search failed (${e::class.simpleName})")
            throw LocationSearchException("Couldn't reach the town search")
        }
        if (answer.code !in 200..299) {
            Log.w(TAG, "Town search answered ${answer.code}")
            throw LocationSearchException("The town search answered ${answer.code}")
        }
        val parsed = try {
            GeocodingJson.decodeFromString(GeocodingAnswer.serializer(), answer.body)
        } catch (e: IllegalArgumentException) {
            // Not the exception itself: kotlinx.serialization quotes the body, which holds the towns.
            Log.w(TAG, "Town search sent an answer the tablet can't read (${e::class.simpleName})")
            throw LocationSearchException("The town search sent an answer the tablet can't read")
        }
        return parsed.results.mapNotNull { place ->
            place.timezone?.let { zone -> PlaceMatch(place.name, place.admin1, place.country, place.latitude, place.longitude, zone) }
        }
    }
}

private class Answer(val code: Int, val body: String)

/**
 * Enqueues the call and suspends until its whole body is in, reading it on OkHttp's thread (3a's `Call.await`):
 * cancelling the coroutine cancels the call, which ends a stalled read at once.
 */
private suspend fun Call.await(): Answer = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = try {
                    response.use { Answer(it.code, it.body?.string().orEmpty()) }
                } catch (e: Throwable) {
                    // After a cancel this is the closed socket and the continuation is already cancelled; anything else
                    // must reach the caller, or it would hang.
                    cont.resumeWithException(e)
                    return
                }
                cont.resume(answer)
            }
        },
    )
}
