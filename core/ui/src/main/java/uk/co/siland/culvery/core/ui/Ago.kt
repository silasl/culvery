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

/** "Updated 5 minutes ago", "Updated from Google Calendar 5 minutes ago", or "Not updated yet" (4d-2 §7.3: full words). */
fun updatedLabel(lastMillis: Long?, nowMillis: Long, service: String? = null): String {
    if (lastMillis == null) return "Not updated yet"
    val ago = agoWords(lastMillis, nowMillis)
    return if (service == null) "Updated $ago" else "Updated from $service $ago"
}
