package uk.co.siland.culvery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.capability.shopping.SampleShoppingProvider

/**
 * Debug builds only: adds an item to the sample list as if from a phone, so the walkthrough can see the wall pick it up:
 * `adb shell am broadcast -n uk.co.siland.culvery/.DebugShoppingReceiver --es add Eggs`
 */
class DebugShoppingReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun sample(): SampleShoppingProvider
    }

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_ADD)?.takeIf { it.isNotBlank() } ?: return
        EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).sample().addFromAPhone(text)
    }

    companion object {
        const val EXTRA_ADD = "add"
    }
}
