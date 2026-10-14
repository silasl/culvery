package uk.co.siland.culvery.capability.door.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import uk.co.siland.culvery.capability.door.Chime
import uk.co.siland.culvery.capability.door.DoorCapability
import uk.co.siland.culvery.capability.door.DoorEventSink
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.capability.door.DoorRecorder
import uk.co.siland.culvery.capability.door.DoorPruneLoop
import uk.co.siland.culvery.capability.door.MediaPlayerChime
import uk.co.siland.culvery.capability.door.db.DoorDatabase
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class DoorModule {
    // With no provider bound there is no Door tab (5 §3.1).
    @Multibinds
    abstract fun providers(): Set<DoorProvider>

    @Binds
    @IntoSet
    abstract fun capability(impl: DoorCapability): Capability

    @Binds
    abstract fun sink(impl: DoorRecorder): DoorEventSink

    @Binds
    abstract fun chime(impl: MediaPlayerChime): Chime

    @Binds
    @IntoSet
    abstract fun pruneLoop(impl: DoorPruneLoop): Startable

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): DoorDatabase =
            Room.databaseBuilder(context, DoorDatabase::class.java, "door.db").build()
    }
}
