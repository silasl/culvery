package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.ShellUiState

@Composable
fun CulveryShell(
    state: ShellUiState,
    now: () -> LocalDateTime,
    onSelectTab: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onToggleThemePreview: () -> Unit,
    tabContent: @Composable (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
        StatusBar(now, state.dark, state.previewing, state.session, onSignOut, onToggleThemePreview)
        Row(Modifier.weight(1f)) {
            NavRail(state.tabs, state.selectedTabId, onSelectTab, onOpenSettings)
            Box(Modifier.weight(1f).padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 22.dp)) {
                if (state.selectedTabId == HOME_TAB_ID) {
                    HomeScreen(now, state.homeCards, state.headerItems)
                } else {
                    tabContent(state.selectedTabId)
                }
            }
        }
    }
}
