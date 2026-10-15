package uk.co.siland.culvery.provider.door_ring

/** What a Ring notice is (5 §3.2). [device] is as Ring names it, empty when it doesn't say. */
sealed interface RingReading {
    data class Ring(val device: String) : RingReading

    data class Motion(val device: String) : RingReading

    data class UnknownRingAlert(val device: String) : RingReading

    data object Ignore : RingReading
}

/** What a log may say about a reading (5 §6): its outcome, never the device. */
val RingReading.outcome: String
    get() = when (this) {
        is RingReading.Ring -> "a ring"
        is RingReading.Motion -> "motion"
        is RingReading.UnknownRingAlert -> "an unknown alert"
        RingReading.Ignore -> "ignored"
    }

/**
 * Places a Ring notice (5 §3.2) from what the tablet captured on 2026-10-15. Live-view, group, app, account, offer and
 * battery notices, and ongoing ones that don't say a ring, are ignored. Otherwise the channel decides: Ring's
 * "ding_channel…" is a ring and "motion_channel…" is motion (the number after the prefix varies per install). On any
 * other channel the words decide, and anything else from Ring is an unknown alert, which still rings.
 */
object RingParser {
    // Android's Notification.CATEGORY_* values as text, so the parser runs without Android.
    private val IGNORED_CATEGORIES = setOf("promo", "recommendation", "status", "progress", "service", "sys", "transport", "err")
    private const val DING_CHANNEL = "ding_channel"
    private const val MOTION_CHANNEL = "motion_channel"
    private val IGNORED_WORDS = Regex("""(?i)\b(update|upgrade|subscription|protect|offer|sign in|signed out|password|account|battery|offline|firmware|live view)\b""")
    private val MOTION = Regex("""(?i)\bmotion\b|\b(?:a|an) (?:person|package|parcel|vehicle|car|animal|pet)\b""")
    private val RING = Regex("""(?i)\b(ding|rang|ringing|doorbell (?:press|ring)\w*|someone(?: is|['’]s)? at)\b""")
    private val AT_DEVICE = Regex("""(?i)\bat (?:your |the )?(.+?)[.!]?$""")

    fun read(notice: RingNotice): RingReading {
        val words = listOfNotNull(notice.title, notice.text, notice.bigText).joinToString(" ")
        // An ongoing notice is a ring if its words say so: Ring may post a press as a call-style alert.
        if ((notice.ongoing && !RING.containsMatchIn(words)) || notice.groupSummary || notice.category in IGNORED_CATEGORIES || IGNORED_WORDS.containsMatchIn(words)) {
            return RingReading.Ignore
        }
        val device = deviceOf(notice)
        val channel = notice.channelId.orEmpty()
        return when {
            channel.startsWith(DING_CHANNEL, ignoreCase = true) -> RingReading.Ring(device)
            channel.startsWith(MOTION_CHANNEL, ignoreCase = true) -> RingReading.Motion(device)
            MOTION.containsMatchIn(words) -> RingReading.Motion(device)
            RING.containsMatchIn(words) -> RingReading.Ring(device)
            else -> RingReading.UnknownRingAlert(device)
        }
    }

    /** The title, unless it is empty or the app's own name; then what follows "at your" in the text, less any leading emoji. */
    private fun deviceOf(notice: RingNotice): String =
        notice.title?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Ring", ignoreCase = true) }
            ?: notice.text?.dropWhile { !it.isLetterOrDigit() }?.trim()?.let { AT_DEVICE.find(it)?.groupValues?.get(1)?.trim() }
            ?: ""
}
