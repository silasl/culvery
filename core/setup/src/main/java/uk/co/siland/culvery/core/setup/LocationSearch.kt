package uk.co.siland.culvery.core.setup

/** A town the search found (4a design §3.1). [region] and [country] are null when the service gives none. */
data class PlaceMatch(
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
) {
    /** "Brighton, England, United Kingdom"; a region that repeats the name is left out. */
    val label: String get() = listOfNotNull(name, region?.takeIf { it != name }, country).joinToString(", ")
}

/** The search couldn't be made or its answer read. Fixed words only: a cause could quote the query (ruling 4). */
class LocationSearchException(message: String) : Exception(message)

/** Home location by town (4a design D2). Bound by `:provider:weather-openmeteo`. */
interface LocationSearch {
    /** Up to five towns matching [query]. Main-safe; cancelling the caller cancels the request. */
    suspend fun search(query: String): List<PlaceMatch>
}
