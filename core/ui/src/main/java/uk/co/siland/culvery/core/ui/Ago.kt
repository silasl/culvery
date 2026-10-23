package uk.co.siland.culvery.core.ui

/** "just now", "5 minutes ago", "3 hours ago", "2 days ago" (from 48 hours); a [thenMillis] in the future reads as just now. */
fun agoWords(thenMillis: Long, nowMillis: Long): String {
    val minutes = (nowMillis - thenMillis).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> plural(minutes, "minute") + " ago"
        hours < 48 -> plural(hours, "hour") + " ago"
        else -> plural(hours / 24, "day") + " ago"
    }
}
