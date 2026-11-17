package uk.co.siland.culvery.capability.reminders.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase

@Module
@InstallIn(SingletonComponent::class)
object RemindersModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RemindersDatabase =
        Room.databaseBuilder(context, RemindersDatabase::class.java, "reminders.db").build()
}
