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
 * As device owner (4c design §5.2), Culvery allowlists itself, so startLockTask() is true lock-task (no prompt, no exit
 * gesture), and Play services, so Google's chooser opens inside it (plan review 13). False when not device owner.
 */
internal fun allowLockTaskIfOwner(context: Context): Boolean {
    val policies = context.getSystemService(DevicePolicyManager::class.java)
    if (!policies.isDeviceOwnerApp(context.packageName)) return false
    policies.setLockTaskPackages(ComponentName(context, CulveryDeviceAdmin::class.java), arrayOf(context.packageName, GMS_PACKAGE))
    return true
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
