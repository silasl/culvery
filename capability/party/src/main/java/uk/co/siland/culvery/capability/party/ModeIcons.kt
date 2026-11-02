package uk.co.siland.culvery.capability.party

import uk.co.siland.culvery.core.ui.Icons

/** One icon a mode can have: its ligature [name] and the word TalkBack says for it. */
data class ModeIcon(val name: String, val spoken: String)

object ModeIcons {
    /** The grid in the order it is drawn, four to a row (15b §4.1). */
    val GRID: List<ModeIcon> = listOf(
        ModeIcon(Icons.CELEBRATION, "Party"),
        ModeIcon(Icons.MOVIE, "Film"),
        ModeIcon(Icons.SPORTS_ESPORTS, "Games"),
        ModeIcon(Icons.RESTAURANT, "Dinner"),
        ModeIcon(Icons.MENU_BOOK, "Reading"),
        ModeIcon(Icons.BEDTIME, "Bedtime"),
        ModeIcon(Icons.SELF_IMPROVEMENT, "Calm"),
        ModeIcon(Icons.FITNESS_CENTER, "Exercise"),
        ModeIcon(Icons.CAKE, "Birthday"),
        ModeIcon(Icons.PETS, "Pets"),
        ModeIcon(Icons.PARK, "Outdoors"),
        ModeIcon(Icons.MUSIC_NOTE, "Music"),
    )

    /** What to draw for a stored [icon]: itself when it is on the grid, else Party mode's. */
    fun drawn(icon: String): String = if (GRID.any { it.name == icon }) icon else PARTY_ICON
}
