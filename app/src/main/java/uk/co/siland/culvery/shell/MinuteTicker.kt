package uk.co.siland.culvery.shell

import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun interface MinuteTicker {
    fun ticks(): Flow<LocalDateTime>
}

val SystemMinuteTicker = MinuteTicker {
    flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            delay(60_000L - (now.second * 1_000L + now.nano / 1_000_000L))
        }
    }
}
