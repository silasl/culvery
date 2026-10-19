package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.LocalDateTime
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.ShellUiState

/**
 * The frame around every tab (9 §4.1): the rail at full height with the mark at its top, and beside it the top bar over the
 * page. [watching] shows the top bar's pill; [pillSpot] is told where its icon is.
 */
@Composable
fun CulveryShell(
    state: ShellUiState,
    now: () -> LocalDateTime,
    onSelectTab: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onToggleThemePreview: () -> Unit,
    watching: Boolean = false,
    pillSpot: CameraMarkSpot? = null,
    tabContent: @Composable (String) -> Unit,
) {
    Row(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
        NavRail(state.tabs, state.selectedTabId, onSelectTab, onOpenSettings)
        Column(
            Modifier.weight(1f).padding(start = ShellDimens.mainStart, end = ShellDimens.mainEnd, bottom = ShellDimens.mainBottom),
        ) {
            StatusBar(state.dark, state.previewing, state.session, onSignOut, onToggleThemePreview, watching, pillSpot)
            Box(Modifier.weight(1f)) {
                if (state.selectedTabId == HOME_TAB_ID) {
                    HomeScreen(now, state.homeCards, state.headerItems)
                } else {
                    tabContent(state.selectedTabId)
                }
            }
        }
    }
}
