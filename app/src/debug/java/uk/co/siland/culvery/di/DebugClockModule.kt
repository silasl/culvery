package uk.co.siland.culvery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
}

@Module
@InstallIn(SingletonComponent::class)
object DebugClockModule {
    @Provides
    fun wallClock(clock: DebugClock): WallClock = clock
}
