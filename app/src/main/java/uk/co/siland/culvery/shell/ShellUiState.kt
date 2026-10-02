package uk.co.siland.culvery.shell

import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomePlacement

const val HOME_TAB_ID = "home"

data class TabItem(val id: String, val label: String, val icon: String)

/** The status bar's "{name} · {role}". */
data class SessionUi(val name: String, val role: String)

data class ShellUiState(
    val tabs: List<TabItem> = emptyList(),
    val selectedTabId: String = HOME_TAB_ID,
    val session: SessionUi? = null,
    val dark: Boolean = true,
    val previewing: Boolean = false,
    val homeCards: List<HomePlacement> = emptyList(),
    /** Home's header items, by order (4b design §3.8). */
    val headerItems: List<HeaderItem> = emptyList(),
    /** Every capability's Home cards have answered once: the splash can go (4c §4.1). */
    val cardsLoaded: Boolean = false,
    val settingsOpen: Boolean = false,
)
