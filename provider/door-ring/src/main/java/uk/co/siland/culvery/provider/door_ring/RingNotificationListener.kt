package uk.co.siland.culvery.provider.door_ring

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Culvery's notification access (5 §3.2), allowed once in Android's Settings. Removals are ignored (5 §4.1).
 * Ring's notices are cleared once heard: Android holds 25 per app and drops every new one past that, rings included.
 * Ring reposts a notice with its picture under the same key and time, which the Door store merges as before.
 */
@AndroidEntryPoint
class RingNotificationListener : NotificationListenerService() {
    @Inject lateinit var reader: RingReader

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!reader.reads(sbn.packageName, sbn.tag)) return
        reader.onPosted(sbn.packageName, sbn.tag) { ringPost(sbn.packageName, sbn.key, sbn.postTime, sbn.notification, this) }
        cancelNotification(sbn.key)
    }

    /** Leftovers from before count towards the cap too. */
    override fun onListenerConnected() {
        reader.listening(true)
        val leftovers = activeNotifications.orEmpty().filter { reader.reads(it.packageName, it.tag) }.map { it.key }
        if (leftovers.isNotEmpty()) cancelNotifications(leftovers.toTypedArray())
    }

    /** Android unbinds a listener now and then; asking again costs nothing, and the status says if access is off (5 §6). */
    override fun onListenerDisconnected() {
        reader.listening(false)
        requestRebind(ComponentName(this, RingNotificationListener::class.java))
    }
}
