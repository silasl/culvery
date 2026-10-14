package uk.co.siland.culvery.provider.door_ring

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class RingProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun itIsRingAndNeverHoldsCredentials() {
        val provider = RingProvider(context, assumeInstalled = false)
        assertThat(provider.descriptor.id).isEqualTo("door.ring")
        assertThat(provider.descriptor.displayName).isEqualTo("Ring")
        assertThat(provider.descriptor.connectHint).isEqualTo("Sign in as a Ring Shared User, and turn off the Ring app’s notification sound.")
    }

    /** The real check: Ring's launcher entry, which the manifest's `<queries>` makes visible from Android 11. */
    @Test
    fun theAppIsInstalledWhenRingCanBeLaunched() {
        val provider = RingProvider(context, assumeInstalled = false)
        assertThat(provider.appInstalled()).isFalse()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(RING_PACKAGE)
        shadowOf(context.packageManager).addResolveInfoForIntent(
            launcher,
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = RING_PACKAGE; name = "com.ringapp.MainActivity" } },
        )
        assertThat(provider.appInstalled()).isTrue()
    }

    /** Debug builds count the Ring app as there: the emulator has none (5 §8.1). */
    @Test
    fun debugBuildsAssumeIt() = assertThat(RingProvider(context, assumeInstalled = true).appInstalled()).isTrue()

    @Test
    fun accessIsAllowedWhenAndroidListsOurListener() {
        val provider = RingProvider(context, assumeInstalled = false)
        val ours = ComponentName(context, RingNotificationListener::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")
        assertThat(provider.accessAllowed()).isFalse()
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", ours)
        assertThat(provider.accessAllowed()).isTrue()
    }

    /** Android 11: Culvery's own toggle first, then the list in case One UI lacks it. */
    @Config(sdk = [30])
    @Test
    fun onAndroid11TheScreensAreCulverysToggleThenTheList() {
        val screens = RingProvider(context, assumeInstalled = false).accessScreens()
        assertThat(screens.map { it.action })
            .containsExactly(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).inOrder()
        assertThat(screens.first().getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME))
            .isEqualTo(ComponentName(context, RingNotificationListener::class.java).flattenToString())
    }

    @Config(sdk = [29])
    @Test
    fun onAndroid10ItIsTheList() {
        assertThat(RingProvider(context, assumeInstalled = false).accessScreens().map { it.action })
            .containsExactly(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
