package uk.co.siland.culvery.provider.door_ring

/**
 * BEST GUESSES at the Ring app's notifications, not captures (5 §2.12): the tablet's captures replace every one of these
 * (5 §8.3). The words follow what Ring is known to show; the channels and categories are guesses. "Front Door" is a
 * placeholder device; captures use [device] and [person].
 */
internal object RingFixtures {
    private const val POSTED = 1_791_982_920_000L
    private const val RING_WORDS = "There’s someone at your Front Door"

    private fun notice(
        id: Int,
        title: String?,
        text: String?,
        channel: String? = "ding",
        category: String? = null,
        bigText: String? = null,
        picture: Boolean = false,
        ongoing: Boolean = false,
        summary: Boolean = false,
        posted: Long = POSTED,
    ) = RingNotice(RING_PACKAGE, "0|$RING_PACKAGE|$id|null|10123", channel, category, title, text, bigText, picture, posted, posted, ongoing, summary)

    val ring = notice(101, "Front Door", RING_WORDS)

    /** The same notification (key and time) posted again with the snapshot. */
    val ringWithSnapshot = ring.copy(hasPicture = true, bigText = RING_WORDS, postTime = POSTED + 4_000)

    /** A second press: Ring reuses the key with a new time. */
    val ringAgain = notice(101, "Front Door", RING_WORDS, posted = POSTED + 30_000)
    val motion = notice(102, "Front Door", "There is motion at your Front Door", channel = "motion")
    val unknown = notice(103, "Front Door", "New event at your Front Door", channel = "alerts")

    // One per ignore rule, each with a ring's words, so the rule alone is what ignores it.
    val liveView = notice(104, "Front Door", RING_WORDS, channel = "live_view", ongoing = true)
    val bundle = notice(105, "Ring", RING_WORDS, summary = true)
    val promo = notice(106, "Ring", RING_WORDS, channel = "marketing", category = "promo")
    val appUpdate = notice(107, "Ring", "Update Ring to see when someone is at your Front Door", channel = "general")
}
