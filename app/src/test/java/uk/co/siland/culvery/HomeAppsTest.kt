package uk.co.siland.culvery

import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowRoleManager

@RunWith(AndroidJUnit4::class)
class HomeAppsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val policies = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, CulveryDeviceAdmin::class.java)

    @After
    fun tearDown() = ShadowRoleManager.reset()

    /** §5.2: as device owner only Culvery may run in lock-task at start-up. */
    @Test
    fun asDeviceOwnerOnlyCulveryMayRunInLockTask() {
        shadowOf(policies).setDeviceOwner(admin)
        assertThat(allowLockTaskIfOwner(context)).isTrue()
        assertThat(policies.getLockTaskPackages(admin).toList()).containsExactly(context.packageName)
    }

    /** §5.2: Play services is allowed in lock-task only while Google's chooser is open. */
    @Test
    fun asDeviceOwnerPlayServicesIsAllowedOnlyWhileTheChooserIsOpen() {
        shadowOf(policies).setDeviceOwner(admin)
        allowLockTaskIfOwner(context)
        val before = policies.getLockTaskPackages(admin).toList()
        allowPlayServicesInLockTask(context, allowed = true)
        val during = policies.getLockTaskPackages(admin).toList()
        allowPlayServicesInLockTask(context, allowed = false)
        val after = policies.getLockTaskPackages(admin).toList()
        assertThat(before).containsExactly(context.packageName)
        assertThat(during).containsExactly(context.packageName, GMS_PACKAGE)
        assertThat(after).containsExactly(context.packageName)
    }

    @Test
    fun otherwiseScreenPinningIsLeftAsItIs() {
        assertThat(allowLockTaskIfOwner(context)).isFalse()
    }

    @Test
    fun theHomeRoleIsReadAgainOnRefresh() {
        val home = AndroidHomeApp(context)
        assertThat(home.isDefault.value).isFalse()
        ShadowRoleManager.addRoleHolder(RoleManager.ROLE_HOME, context.packageName, Process.myUserHandle())
        home.refresh()
        assertThat(home.isDefault.value).isTrue()
    }
}
