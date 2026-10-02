package uk.co.siland.culvery.core.plugin

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** The longest the first background passes wait for Home's first frame (4c design §4.1). */
const val FIRST_DRAW_WAIT_MS = 3_000L

/**
 * A one-shot "Home has drawn" signal from the shell (4c design §4.1): the first calendar and weather passes wait for it,
 * or [FIRST_DRAW_WAIT_MS], so they don't compete with start-up's first frames.
 */
@Singleton
class FirstDraw @Inject constructor() {
    private val drawn = CompletableDeferred<Unit>()

    fun markDrawn() {
        drawn.complete(Unit)
    }

    suspend fun await(timeoutMillis: Long = FIRST_DRAW_WAIT_MS) {
        withTimeoutOrNull(timeoutMillis) { drawn.await() }
    }
}
