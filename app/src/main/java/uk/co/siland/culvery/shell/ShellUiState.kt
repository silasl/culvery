package uk.co.siland.culvery.shell

import java.time.LocalDateTime
import uk.co.siland.culvery.core.plugin.HomePlacement

const val HOME_TAB_ID = "home"

data class TabItem(val id: String, val label: String, val icon: String)

/** The status bar's "{name} · {role}". */
data class SessionUi(val name: String, val role: String)

data class ShellUiState(
    val tabs: List<TabItem> = emptyList(),
    val selectedTabId: String = HOME_TAB_ID,
    val session: SessionUi? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val dark: Boolean = true,
    val previewing: Boolean = false,
    val homeCards: List<HomePlacement> = emptyList(),
    val settingsOpen: Boolean = false,
)
