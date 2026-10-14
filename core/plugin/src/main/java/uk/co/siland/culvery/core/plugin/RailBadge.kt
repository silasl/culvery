package uk.co.siland.culvery.core.plugin

/** A mark on a capability's rail tab (5 §3.3): a count bubble or a dot, and the words TalkBack says after the tab's name. */
sealed interface RailBadge {
    /** Said after the tab's name: "Door, 2 unseen rings". */
    val spoken: String

    data class Count(val count: Int, override val spoken: String) : RailBadge

    data class Dot(override val spoken: String) : RailBadge
}
