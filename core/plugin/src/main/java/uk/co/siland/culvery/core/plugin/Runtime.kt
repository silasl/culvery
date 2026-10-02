package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import javax.inject.Qualifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun interface WallClock {
    fun nowMillis(): Long
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

private const val MINUTE_MS = 60_000L

/** Now, then each minute's start, as epoch millis. */
internal fun minuteTicks(clock: WallClock): Flow<Long> = flow {
    while (true) {
        val now = clock.nowMillis()
        emit(now)
        delay(MINUTE_MS - Math.floorMod(now, MINUTE_MS))
    }
}

/** Wall-clock millis, read again at each of the household clock's minutes (4c design §4.3). */
@Composable
fun rememberNowMillis(clock: HouseholdClock): Long {
    val now by produceState(clock.nowMillis(), clock) {
        clock.minutes.collect { value = clock.nowMillis() }
    }
    return now
}
