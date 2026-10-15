package uk.co.siland.culvery.provider.door_ring

/**
 * Captured from the Ring app on the tablet on 2026-10-15 (5 §8.3): channels, nulls and re-posts are as Ring sent them.
 * The number ending each channel id varies per install. "Front Door" is a generic device name. The ignore fixtures keep
 * a capture's shape with only the field their rule reads changed.
 */
internal object RingFixtures {
    private const val POSTED = 1_791_982_920_000L
    private const val DING_CHANNEL = "ding_channel_notification333206520"
    private const val MOTION_CHANNEL = "motion_channel_notification333206520"
    private const val PRESS_WORDS = "🔔 Someone is at your Front Door"
    private const val PERSON_WORDS = "There is a person at your Front Door"

    private fun notice(
        id: Int,
        text: String?,
        channel: String? = DING_CHANNEL,
        title: String? = null,
        category: String? = null,
        ongoing: Boolean = false,
        summary: Boolean = false,
        picture: Boolean = false,
        posted: Long = POSTED,
    ) = RingNotice(
        RING_PACKAGE, "0|$RING_PACKAGE|$id|null|10123", channel, category, title, text, bigText = null,
        hasPicture = picture, postTime = posted, ongoing = ongoing, groupSummary = summary,
    )

    /** A doorbell press: posted without a picture, then twice with one, all with the same key and time. */
    val ring = notice(333, PRESS_WORDS)
    val ringWithPicture = ring.copy(hasPicture = true, postTime = POSTED + 1_500)
    val ringWithPictureAgain = ring.copy(hasPicture = true, postTime = POSTED + 3_000)

    /** Motion with person detection, posted the same three ways. */
    val motion = notice(334, PERSON_WORDS, channel = MOTION_CHANNEL)
    val motionWithPicture = motion.copy(hasPicture = true, postTime = POSTED + 1_500)

    /** The group summary on Ring's channels: no title, no text. */
    val groupSummary = notice(-726026163, text = null, summary = true)

    // Channels and words the parser has not seen, to pin each fallback step.
    val unfamiliarMotionChannel = notice(335, "Something happened", channel = MOTION_CHANNEL)
    val unfamiliarDingChannel = notice(336, "Something happened", channel = DING_CHANNEL)
    val pressWordsOnAnotherChannel = notice(337, PRESS_WORDS, channel = "alerts")
    val unknown = notice(338, "New event at your Front Door", channel = "alerts")

    // One per ignore rule; all but the live-view and ongoing ones carry a ring's words, so the rule alone is what ignores it.
    val liveView = notice(339, "Live view: someone is at your Front Door", channel = "live_view", ongoing = true)
    val ongoingStatus = notice(340, "Streaming from your Front Door", title = "Ring", channel = "general", ongoing = true)

    /** A ring Ring posts as an ongoing, call-style alert. */
    val ongoingRing = notice(341, "Someone is at your Front Door", ongoing = true)
    val bundle = notice(342, PRESS_WORDS, summary = true)
    val promo = notice(343, PRESS_WORDS, title = "Ring", channel = "marketing", category = "promo")
    val appUpdate = notice(344, "Update Ring to see when someone is at your Front Door", title = "Ring", channel = "general")
}
