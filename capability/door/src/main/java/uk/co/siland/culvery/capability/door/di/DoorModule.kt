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
import uk.co.siland.culvery.capability.door.DoorPermissionSource
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.capability.door.DoorQuietness
import uk.co.siland.culvery.capability.door.DoorPruneLoop
import uk.co.siland.culvery.capability.door.DoorRecorder
import uk.co.siland.culvery.capability.door.db.DoorDatabase
import uk.co.siland.culvery.capability.door.db.SecureDelete
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.sound.PREVIEW_PAD
import uk.co.siland.culvery.core.sound.PadStart
import uk.co.siland.culvery.core.sound.Sound
import uk.co.siland.culvery.core.sound.SoundPlayer

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
    @IntoSet
    abstract fun permissions(impl: DoorPermissionSource): PermissionSource

    @Binds
    abstract fun quiet(impl: DoorQuietness): DoorQuiet

    @Binds
    abstract fun sink(impl: DoorRecorder): DoorEventSink

    @Binds
    @IntoSet
    abstract fun pruneLoop(impl: DoorPruneLoop): Startable

    companion object {
        @Provides
        fun chime(player: SoundPlayer): Chime = object : Chime {
            override suspend fun play(sound: Sound, volume: Float) = player.playDoorbell(sound, volume)

            override fun hushPads() = player.ringStarted()

            override val previewing = player.previewing

            override suspend fun preview(sound: Sound, volume: Float): PadStart = player.playPad(PREVIEW_PAD, sound, volume, SoundPlayer.DOORBELL_CAP_MS)

            override fun stopPreview() = player.stopPads()
        }

        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): DoorDatabase =
            Room.databaseBuilder(context, DoorDatabase::class.java, "door.db").addCallback(SecureDelete).build()
    }
}
