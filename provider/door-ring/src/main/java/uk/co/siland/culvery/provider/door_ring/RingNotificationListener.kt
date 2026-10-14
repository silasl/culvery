package uk.co.siland.culvery.provider.door_ring

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Culvery's notification access (5 §3.2), allowed once in Android's Settings. Removals are ignored (5 §4.1). */
@AndroidEntryPoint
class RingNotificationListener : NotificationListenerService() {
    @Inject lateinit var reader: RingReader

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        reader.onPosted(sbn.packageName, sbn.tag) { ringPost(sbn.packageName, sbn.key, sbn.postTime, sbn.notification, this) }
    }

    override fun onListenerConnected() = reader.listening(true)

    /** Android unbinds a listener now and then; asking again costs nothing, and the status says if access is off (5 §6). */
    override fun onListenerDisconnected() {
        reader.listening(false)
        requestRebind(ComponentName(this, RingNotificationListener::class.java))
    }
}
