package uk.co.siland.culvery.capability.door

import android.content.Intent
import android.graphics.Bitmap
import java.io.File
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

enum class DoorKind { RING, MOTION }

/**
 * A ring or motion as the Door tab keeps it (5 §3.1). [unrecognised] is a Ring alert the parser couldn't place, shown and
 * alerted as a ring; [device] is as the doorbell's app names it, maybe empty.
 */
data class DoorEvent(
    val id: Long,
    val atMillis: Long,
    val kind: DoorKind,
    val device: String,
    val unrecognised: Boolean,
    val snapshot: File?,
    val seen: Boolean,
)

fun interface Snapshot {
    fun saveTo(file: File)
}

/** The longest side of a kept picture (5 §4.4 asks at most 640 px wide; a tall one is held to it too). */
const val MAX_SNAPSHOT_PX = 640
private const val JPEG_QUALITY = 85

/** The scale that fits a [width] × [height] picture in [MAX_SNAPSHOT_PX] square; never above 1. */
fun fitScale(width: Int, height: Int): Float = minOf(1f, MAX_SNAPSHOT_PX.toFloat() / maxOf(width, height))

/** A provider's picture, scaled down to fit [MAX_SNAPSHOT_PX] and written as JPEG (5 §4.4). */
class BitmapSnapshot(private val bitmap: Bitmap) : Snapshot {
    override fun saveTo(file: File) {
        val scale = fitScale(bitmap.width, bitmap.height)
        val scaled = if (scale == 1f) bitmap else Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        file.outputStream().use { out -> check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "The JPEG wasn't written" } }
    }
}

/**
 * What a provider heard (5 §4): [key] and [whenMillis] (the notification's own time) together name one notification,
 * which Ring posts again with its picture; a second press reuses the key with a new time.
 */
data class DoorNotice(
    val key: String,
    val atMillis: Long,
    val kind: DoorKind,
    val device: String,
    val unrecognised: Boolean = false,
    val snapshot: Snapshot? = null,
    val whenMillis: Long = atMillis,
)

/** What a provider calls with what it heard (5 §3.1). Safe from any thread; every call returns at once. */
interface DoorEventSink {
    /** Any notice from the doorbell's app, one that is then ignored included: "Last heard from Ring" (5 §4.2). */
    fun heard(atMillis: Long)

    fun post(notice: DoorNotice)

    /** Android bound (true) or unbound (false) the provider's listener. */
    fun listening(bound: Boolean)

    /** Check Ring's notifications is on: the provider writes each notice's shape to the log (5 §5.4). */
    val capturing: Boolean
}

/** A doorbell's app on the tablet, as the capability asks about it (5 §3.2, §5.5, §6). */
interface DoorProvider {
    val descriptor: ProviderDescriptor

    fun appInstalled(): Boolean

    /** Android lets Culvery read notifications. */
    fun accessAllowed(): Boolean

    /** Android's notification-access screens, best first: Culvery's own toggle, then the list. */
    fun accessScreens(): List<Intent>
}
