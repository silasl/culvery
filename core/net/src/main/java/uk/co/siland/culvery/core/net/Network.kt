package uk.co.siland.culvery.core.net

import android.content.Context
import android.net.ConnectivityManager

/** Whether Android has any network: none means the tablet is offline, not the devices on it. */
class AndroidNetwork(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun online(): Boolean = connectivity?.activeNetwork != null
}
