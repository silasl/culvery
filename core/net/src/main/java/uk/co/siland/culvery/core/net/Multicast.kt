package uk.co.siland.culvery.core.net

import android.content.Context
import android.net.wifi.WifiManager

/** Takes the lock when called and gives it back when the result is closed: without it some Wi-Fi drivers drop multicast traffic to save power. */
fun multicastLock(context: Context, tag: String): () -> AutoCloseable = {
    val lock = context.applicationContext.getSystemService(WifiManager::class.java)?.createMulticastLock(tag)?.apply {
        setReferenceCounted(false)
        acquire()
    }
    AutoCloseable { lock?.release() }
}
