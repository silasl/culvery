package uk.co.siland.culvery.provider.door_ring

import android.util.Log
import javax.inject.Inject
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.DoorEventSink
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorNotice

/** The adb shell, whose `cmd notification post … culvery-fake-ring …` makes the debug build's fake rings (5 §7). */
const val SHELL_PACKAGE = "com.android.shell"
const val FAKE_RING_TAG = "culvery-fake-ring"

internal const val TAG_RING = "RingReader"

/** Between the listener and the Door capability (5 §4.1–4.2): Ring's notices only, placed and handed to the [sink]. */
class RingReader @Inject constructor(private val sink: DoorEventSink) {
    fun reads(packageName: String, tag: String?): Boolean =
        packageName == RING_PACKAGE || (FakeRings.ENABLED && packageName == SHELL_PACKAGE && tag == FAKE_RING_TAG)

    /** Another app's notice returns at once, unread; one that can't be read is dropped, so the listener stays bound. */
    fun onPosted(packageName: String, tag: String?, read: () -> RingPost) {
        if (!reads(packageName, tag)) return
        try {
            read(read())
        } catch (e: Exception) {
            dropped(e)
        } catch (e: OutOfMemoryError) {
            dropped(e)
        }
    }

    private fun dropped(e: Throwable) {
        Log.w(TAG_RING, "Couldn't read a Ring notice (${e::class.simpleName})")
    }

    fun read(post: RingPost) {
        val notice = post.notice
        sink.heard(notice.postTime)
        // The one line with Ring's words in it (5 §6, §8.3): on for 30 minutes at most, by an Admin. Log.i would be
        // stripped from the release build the tablet runs.
        if (sink.capturing) Log.println(Log.INFO, TAG_RING, "Ring notice: ${post.captureLine}")
        val reading = RingParser.read(notice)
        Log.println(Log.INFO, TAG_RING, "A Ring notice was read as ${reading.outcome}")
        val snapshot = post.picture?.let(::BitmapSnapshot)
        fun door(kind: DoorKind, device: String, unrecognised: Boolean = false) =
            DoorNotice(notice.key, notice.postTime, kind, device, unrecognised, snapshot, notice.whenMillis)
        val posted = when (reading) {
            is RingReading.Ring -> door(DoorKind.RING, reading.device)
            is RingReading.Motion -> door(DoorKind.MOTION, reading.device)
            is RingReading.UnknownRingAlert -> door(DoorKind.RING, reading.device, unrecognised = true)
            RingReading.Ignore -> null
        }
        posted?.let(sink::post)
    }

    fun listening(bound: Boolean) = sink.listening(bound)
}
