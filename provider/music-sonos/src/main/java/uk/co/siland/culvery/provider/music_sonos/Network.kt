package uk.co.siland.culvery.provider.music_sonos

import android.content.Context
import android.net.ConnectivityManager

/** Whether Android has any network: none means the tablet is offline, not the speakers (6 §6). */
internal class AndroidNetwork(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun online(): Boolean = connectivity?.activeNetwork != null
}
