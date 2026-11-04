package uk.co.siland.culvery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Debug builds only: switches the sample council's collections page (17 §7):
 * `adb shell am broadcast -n uk.co.siland.culvery/.DebugCouncilReceiver -a uk.co.siland.culvery.debug.COUNCIL --es page missing`
 */
class DebugCouncilReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun council(): DebugCouncil
    }

    override fun onReceive(context: Context, intent: Intent) {
        // A mistyped command must not change the page.
        if (intent.action != ACTION) return
        val page = CouncilPage.of(intent.getStringExtra(EXTRA_PAGE)) ?: return
        EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).council().page = page
    }

    companion object {
        const val ACTION = "uk.co.siland.culvery.debug.COUNCIL"
        const val EXTRA_PAGE = "page"
    }
}
