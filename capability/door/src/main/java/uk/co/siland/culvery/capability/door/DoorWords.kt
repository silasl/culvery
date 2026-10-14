package uk.co.siland.culvery.capability.door

import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.ui.plural

/** 5 §5.3: "Door, new motion". */
internal const val NEW_MOTION = "new motion"

/** Unseen rings as a count; unseen motion alone as a dot; else none (5 §4.8). */
fun badgeOf(unseen: Unseen): RailBadge? = when {
    unseen.rings > 0 -> RailBadge.Count(unseen.rings, plural(unseen.rings.toLong(), "unseen ring"))
    unseen.motion > 0 -> RailBadge.Dot(NEW_MOTION)
    else -> null
}
