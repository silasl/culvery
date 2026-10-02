package uk.co.siland.culvery

import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.siland.culvery.core.plugin.HomeApp

/** Google Play services: its account chooser and consent screens. */
internal const val GMS_PACKAGE = "com.google.android.gms"

/**
 * As device owner (4c design §5.2), Culvery allowlists only itself, so startLockTask() is true lock-task (no prompt, no
 * exit gesture). False when not device owner.
 */
internal fun allowLockTaskIfOwner(context: Context): Boolean {
    val policies = context.getSystemService(DevicePolicyManager::class.java)
    if (!policies.isDeviceOwnerApp(context.packageName)) return false
    setLockTaskPackages(context, policies, withPlayServices = false)
    return true
}

/**
 * As device owner, Play services may run in lock-task only while Google's chooser is open (4c design §5.2, §5.3):
 * allowed just before it opens, and back to Culvery alone once Culvery is in front again, however the chooser ended.
 */
internal fun allowPlayServicesInLockTask(context: Context, allowed: Boolean) =
    setLockTaskPackages(context, context.getSystemService(DevicePolicyManager::class.java), withPlayServices = allowed)

private fun setLockTaskPackages(context: Context, policies: DevicePolicyManager, withPlayServices: Boolean) {
    val packages = if (withPlayServices) arrayOf(context.packageName, GMS_PACKAGE) else arrayOf(context.packageName)
    policies.setLockTaskPackages(ComponentName(context, CulveryDeviceAdmin::class.java), packages)
}

/** [HomeApp] through RoleManager (API 29+). The choice is made outside Culvery, so [refresh] runs on every resume. */
@Singleton
class AndroidHomeApp @Inject constructor(@param:ApplicationContext private val context: Context) : HomeApp {
    private val held = MutableStateFlow(check())
    override val isDefault: StateFlow<Boolean> = held.asStateFlow()

    fun refresh() {
        held.value = check()
    }

    private fun check(): Boolean {
        val roles = context.getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME)
    }
}
