package uk.co.siland.culvery.capability.shopping.di

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
import uk.co.siland.culvery.capability.shopping.ShoppingChecks
import uk.co.siland.culvery.capability.shopping.ShoppingDrainLoop
import uk.co.siland.culvery.capability.shopping.ShoppingProvider
import uk.co.siland.culvery.capability.shopping.db.ShoppingDatabase
import uk.co.siland.culvery.capability.shopping.ShoppingAccountUse
import uk.co.siland.culvery.capability.shopping.ShoppingCapability
import uk.co.siland.culvery.core.plugin.AccountUse
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class ShoppingModule {
    // With no provider bound there is no Shopping tab (10 §4.2).
    @Multibinds
    abstract fun providers(): Set<ShoppingProvider>

    @Binds
    @IntoSet
    abstract fun drainLoop(impl: ShoppingDrainLoop): Startable

    @Binds
    @IntoSet
    abstract fun capability(impl: ShoppingCapability): Capability

    @Binds
    @IntoSet
    abstract fun accountUse(impl: ShoppingAccountUse): AccountUse

    @Binds
    @IntoSet
    abstract fun checks(impl: ShoppingChecks): Startable

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): ShoppingDatabase =
            Room.databaseBuilder(context, ShoppingDatabase::class.java, "shopping.db").build()
    }
}
