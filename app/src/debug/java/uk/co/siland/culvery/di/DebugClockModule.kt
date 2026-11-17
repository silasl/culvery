package uk.co.siland.culvery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.WallClock

private const val MS_PER_MINUTE = 60_000L

/** Debug builds only: the system clock plus [minutes], so the walkthrough can reach a Holiday mode step's time (11 §8.1). */
@Singleton
class DebugClock internal constructor(private val system: () -> Long) : WallClock {
    @Inject
    constructor() : this(System::currentTimeMillis)

    @Volatile
    var minutes: Long = 0L

    override fun nowMillis(): Long = system() + minutes * MS_PER_MINUTE

    /** Sets the offset so the wall reads [time] in [zone] at its next occurrence after what it reads now, to the minute. */
    fun moveTo(time: LocalTime, zone: ZoneId) {
        val real = nowMillis()
        val now = Instant.ofEpochMilli(real).atZone(zone)
        val today = now.toLocalDate().atTime(time).atZone(zone)
        val next = if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        minutes += (next.toInstant().toEpochMilli() - real + MS_PER_MINUTE - 1) / MS_PER_MINUTE
    }

    fun add(more: Long) {
        minutes += more
    }
}

private val CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm")

/** Null for anything but `HH:mm`, so a mistyped time moves nothing. */
fun parseClockTime(text: String): LocalTime? = try {
    LocalTime.parse(text, CLOCK_TIME)
} catch (_: DateTimeParseException) {
    null
}

@Module
@InstallIn(SingletonComponent::class)
object DebugClockModule {
    @Provides
    fun wallClock(clock: DebugClock): WallClock = clock
}
