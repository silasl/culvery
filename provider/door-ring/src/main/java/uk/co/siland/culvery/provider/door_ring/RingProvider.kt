package uk.co.siland.culvery.provider.door_ring

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.Icons

const val RING_PROVIDER_ID = "door.ring"

/** Connect Ring's first step's hint (5 §2.3, §2.11). */
internal const val RING_HINT = "Sign in as a Ring Shared User, and turn off the Ring app’s notification sound."

/** Ring through its app's notifications (5 §3.2): it never sees Ring credentials. */
@Singleton
class RingProvider internal constructor(private val context: Context, private val assumeInstalled: Boolean) : DoorProvider {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, FakeRings.ENABLED)

    override val descriptor = ProviderDescriptor(RING_PROVIDER_ID, "Ring", Icons.DOORBELL, setOf(Feature.READ), connectHint = RING_HINT)

    /** Ring's launcher entry, visible from Android 11 only through the manifest's `<queries>`. */
    override fun appInstalled(): Boolean = assumeInstalled || context.packageManager.getLaunchIntentForPackage(RING_PACKAGE) != null

    override fun accessAllowed(): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    override fun accessScreens(): List<Intent> {
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return listOf(list)
        val ours = ComponentName(context, RingNotificationListener::class.java).flattenToString()
        return listOf(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ours), list)
    }
}
