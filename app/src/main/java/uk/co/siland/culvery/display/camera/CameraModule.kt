package uk.co.siland.culvery.display.camera

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CameraModule {
    @Binds
    abstract fun frames(impl: Camera2Frames): FrameSource

    @Binds
    @IntoSet
    abstract fun presence(impl: Presence): Startable
}
