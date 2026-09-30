package uk.co.siland.culvery.core.setup

/**
 * Debug builds' **Use a sample household** on Welcome (4a design D11): makes the sample household and marks setup
 * complete. Bound only in `app/src/debug`, so a release build's Welcome doesn't offer it.
 */
fun interface SampleHousehold {
    suspend fun create()
}
