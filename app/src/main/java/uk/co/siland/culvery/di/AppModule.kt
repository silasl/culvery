package uk.co.siland.culvery.di

import android.util.Log
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.shell.ShellToasts

/**
 * An application job's uncaught failure is logged and the process lives on; with the SupervisorJob its siblings keep
 * running (3a design §3.12).
 */
internal val LoggingExceptionHandler = CoroutineExceptionHandler { _, e -> Log.e("Culvery", "An application job failed (${e::class.simpleName})") }

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Multibinds
    abstract fun capabilities(): Set<Capability>

    @Multibinds
    abstract fun startables(): Set<Startable>

    @Binds
    abstract fun toaster(impl: ShellToasts): Toaster

    /** Bound by the weather capability; without it the theme keeps 07:00 / 19:00 (4b design §3.8). */
    @BindsOptionalOf
    abstract fun daylight(): Daylight

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + LoggingExceptionHandler)

        @Provides
        fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }
    }
}
