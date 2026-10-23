package uk.co.siland.culvery.capability.shopping.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import uk.co.siland.culvery.capability.shopping.ShoppingProvider
import uk.co.siland.culvery.capability.shopping.db.ShoppingDatabase

@Module
@InstallIn(SingletonComponent::class)
abstract class ShoppingModule {
    // With no provider bound there is no Shopping tab (10 §4.2).
    @Multibinds
    abstract fun providers(): Set<ShoppingProvider>

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): ShoppingDatabase =
            Room.databaseBuilder(context, ShoppingDatabase::class.java, "shopping.db").build()
    }
}
