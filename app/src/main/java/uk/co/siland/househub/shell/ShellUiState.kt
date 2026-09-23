package uk.co.siland.househub.shell

import java.time.LocalDateTime
import uk.co.siland.househub.core.plugin.HomePlacement

const val HOME_TAB_ID = "home"

data class TabItem(val id: String, val label: String, val icon: String)

data class SessionChip(val name: String, val color: Long)

data class ShellUiState(
    val tabs: List<TabItem> = emptyList(),
    val selectedTabId: String = HOME_TAB_ID,
    val session: SessionChip? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val dark: Boolean = true,
    val previewing: Boolean = false,
    val homeCards: List<HomePlacement> = emptyList(),
    val settingsOpen: Boolean = false,
)
