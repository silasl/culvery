package uk.co.siland.culvery.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.DebugSampleHousehold
import uk.co.siland.culvery.core.setup.SampleHousehold

/** Debug builds only: Welcome offers Use a sample household (4a design D11). */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugSetupModule {
    @Binds
    abstract fun sampleHousehold(impl: DebugSampleHousehold): SampleHousehold
}
