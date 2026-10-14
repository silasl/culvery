package uk.co.siland.culvery.provider.door_ring

/** The Ring app's package on Android (5 §4.1). */
const val RING_PACKAGE = "com.ringapp"

/**
 * One notification as the parser reads it (5 §3.2), with no Android types. [whenMillis] is the notification's own time,
 * which with [key] names one notification. [ongoing] and [groupSummary] let Ring's live-view and bundle notifications be
 * ignored rather than read as unknown rings.
 */
data class RingNotice(
    val packageName: String,
    val key: String,
    val channelId: String?,
    val category: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val hasPicture: Boolean,
    val postTime: Long,
    val whenMillis: Long = postTime,
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
)
