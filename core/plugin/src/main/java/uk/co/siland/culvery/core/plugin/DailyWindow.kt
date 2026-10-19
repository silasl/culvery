package uk.co.siland.culvery.core.plugin

import java.time.LocalTime

/** Inside from [from] until just before [until], across midnight when [until] is earlier; never when they're equal. */
fun inDailyWindow(now: LocalTime, from: LocalTime, until: LocalTime): Boolean = when {
    from == until -> false
    from < until -> now >= from && now < until
    else -> now >= from || now < until
}
