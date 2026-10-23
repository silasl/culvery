package uk.co.siland.culvery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Duration
import java.time.Instant
import javax.inject.Singleton
import uk.co.siland.culvery.capability.shopping.SampleShoppingProvider
import uk.co.siland.culvery.capability.shopping.ShoppingProvider
import uk.co.siland.culvery.core.plugin.WallClock

/** Debug builds only: a shopping list in memory, so the walkthrough needs no Google account (10 §4.10). */
@Module
@InstallIn(SingletonComponent::class)
object SampleShoppingModule {
    /** Ticked on different days, so Butter is learned three times and Bananas twice. */
    private val HISTORY = listOf("Butter" to 2L, "Bananas" to 4L, "Bin bags" to 6L, "Tea bags" to 8L, "Butter" to 9L, "Bananas" to 11L, "Butter" to 16L)

    @Provides
    @Singleton
    fun sample(clock: WallClock): SampleShoppingProvider {
        val provider = SampleShoppingProvider(open = listOf("Milk", "Bread", "Eggs"), clock = clock)
        val now = clock.nowMillis()
        HISTORY.forEach { (text, daysAgo) -> provider.tickedOn(text, Instant.ofEpochMilli(now).minus(Duration.ofDays(daysAgo))) }
        return provider
    }

    @Provides
    @IntoSet
    fun asProvider(sample: SampleShoppingProvider): ShoppingProvider = sample
}
