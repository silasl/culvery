package uk.co.siland.culvery.provider.door_ring

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat
import uk.co.siland.culvery.capability.door.MAX_SNAPSHOT_PX
import uk.co.siland.culvery.capability.door.fitScale

/** A notice, its picture if Ring attached one, and its extras' names for the capture (5 §8.3). */
class RingPost(val notice: RingNotice, val picture: Bitmap?, val extraKeys: Set<String>) {
    /** Check Ring's notifications' one line (5 §5.4): the whole notice, names and all, which a fixture is written from. */
    val captureLine: String get() = "$notice extras=${extraKeys.sorted()}"
}

/** Reads [notification] (5 §3.2): its words, channel, category and time, the picture, and the flags the parser needs. */
fun ringPost(packageName: String, key: String, postTime: Long, notification: Notification, context: Context): RingPost =
    ringPost(packageName, key, postTime, notification, context, ::readPicture)

/** [pictureOf] runs out of memory on a large image: the notice is then kept without it. */
internal fun ringPost(
    packageName: String,
    key: String,
    postTime: Long,
    notification: Notification,
    context: Context,
    pictureOf: (Bundle, Context) -> Bitmap?,
): RingPost {
    val extras = notification.extras
    val picture = try {
        pictureOf(extras, context)
    } catch (_: OutOfMemoryError) {
        null
    }
    val notice = RingNotice(
        packageName = packageName,
        key = key,
        channelId = notification.channelId,
        category = notification.category,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        hasPicture = picture != null,
        postTime = postTime,
        whenMillis = notification.`when`,
        ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
    )
    return RingPost(notice, picture, extras.keySet().toSet())
}

private fun readPicture(extras: Bundle, context: Context): Bitmap? =
    BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE, Bitmap::class.java) ?: pictureIcon(extras, context)

/** From Android 12 a picture that isn't a bitmap comes as an Icon; it is drawn no larger than a kept picture. */
internal fun pictureIcon(extras: Bundle, context: Context): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val drawable = BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE_ICON, Icon::class.java)?.loadDrawable(context) ?: return null
    val (w, h) = drawable.intrinsicWidth to drawable.intrinsicHeight
    if (w <= 0 || h <= 0) return drawable.toBitmap(MAX_SNAPSHOT_PX, MAX_SNAPSHOT_PX)
    val scale = fitScale(w, h)
    return drawable.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
}
