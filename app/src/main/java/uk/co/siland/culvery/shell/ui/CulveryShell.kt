package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.ShellUiState

private val MARK_TOP_MARGIN = 12.dp

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
    Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
        Column(Modifier.fillMaxSize()) {
            StatusBar(state.dark, state.previewing, state.session, onSignOut, onToggleThemePreview)
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
        // Over the rail's top-left, centred on the tab icons; it overlaps the status bar's empty left side and runs below it.
        Box(Modifier.align(Alignment.TopStart).width(RAIL_WIDTH).padding(top = MARK_TOP_MARGIN), contentAlignment = Alignment.TopCenter) {
            CulveryMark(MARK_WIDTH, Modifier.testTag("culvery_mark"))
        }
    }
}
