package uk.co.siland.househub.core.household.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import uk.co.siland.househub.core.household.db.HouseholdDatabase

@Module
@InstallIn(SingletonComponent::class)
object HouseholdModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HouseholdDatabase =
        Room.databaseBuilder(context, HouseholdDatabase::class.java, "household.db").build()
}
