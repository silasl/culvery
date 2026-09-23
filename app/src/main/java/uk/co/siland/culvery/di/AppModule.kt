package uk.co.siland.culvery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.shell.MinuteTicker
import uk.co.siland.culvery.shell.SystemMinuteTicker

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Multibinds
    abstract fun capabilities(): Set<Capability>

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }

        @Provides
        fun minuteTicker(): MinuteTicker = SystemMinuteTicker
    }
}
