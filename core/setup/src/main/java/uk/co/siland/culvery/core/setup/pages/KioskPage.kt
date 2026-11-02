package uk.co.siland.culvery.core.setup.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.core.plugin.HomeApp
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.setup.CHANGE_HOME_APP
import uk.co.siland.culvery.core.setup.CHOOSE_HOME_APP
import uk.co.siland.culvery.core.setup.HOME_APP_HOW_TO
import uk.co.siland.culvery.core.setup.MAKE_HOME_APP
import uk.co.siland.culvery.core.setup.SetupType
import uk.co.siland.culvery.core.setup.TABLET_LOCK
import uk.co.siland.culvery.core.setup.TABLET_LOCK_LINE
import uk.co.siland.culvery.core.setup.USE_OTHER_APPS
import uk.co.siland.culvery.core.setup.tabletLockStatus
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.SettingsTokens

/**
 * App settings › TABLET LOCK (4a design §4.7, 4c §5.1; 4d-2 Decision 2b; 15b §6 W29): a status row for the home app —
 * Choose it while Culvery isn't, Change it (fresh PIN) while it is — and Use other apps, which asks for a fresh PIN.
 */
@Composable
fun TabletLockSection(homeApp: HomeApp) {
    val navigator = LocalShellNavigator.current
    val c = Culvery.colors
    val isHome by homeApp.isDefault.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(SettingsTokens.rowGap)) {
        HhSectionLabel(TABLET_LOCK)
        HhListRow(
            tabletLockStatus(isHome),
            Modifier.testTag("tablet_lock_status"),
            leading = { HhIcon(Icons.LOCK, size = ListRowTokens.icon, tint = if (isHome) c.accent else c.mute) },
            below = {
                Text(if (isHome) TABLET_LOCK_LINE else MAKE_HOME_APP, style = SetupType.line, color = c.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap), verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
                    if (isHome) {
                        HhButton(CHANGE_HOME_APP, navigator::changeHomeApp, Modifier.testTag("settings_change_home_app"), on = ButtonSurface.Surf)
                    } else {
                        HhButton(CHOOSE_HOME_APP, navigator::chooseHomeApp, Modifier.testTag("choose_home_app"), tone = ButtonTone.Primary, on = ButtonSurface.Surf)
                    }
                    HhButton(USE_OTHER_APPS, navigator::exitKiosk, Modifier.testTag("settings_exit_kiosk"), on = ButtonSurface.Surf)
                }
                // Shown whether or not Culvery is the home app: the page is where there is time to read it.
                Text(HOME_APP_HOW_TO, style = SetupType.secondary, color = c.mute, modifier = Modifier.testTag("kiosk_how_to"))
            },
        )
    }
}
