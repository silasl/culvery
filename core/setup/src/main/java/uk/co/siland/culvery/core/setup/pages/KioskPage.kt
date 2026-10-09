package uk.co.siland.culvery.core.setup.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.HomeApp
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.CHANGE_HOME_APP
import uk.co.siland.culvery.core.setup.HomeAppPrompt
import uk.co.siland.culvery.core.setup.TABLET_LOCK
import uk.co.siland.culvery.core.setup.USE_OTHER_APPS
import uk.co.siland.culvery.core.setup.KIOSK_LINE
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.HhButton

/**
 * Settings › Tablet lock (4a design §4.7, 4c §5.1; 4d-2 Decision 2b): the home app — Choose it while Culvery isn't,
 * Change it (fresh PIN) while it is — and Use other apps, which asks for a fresh PIN.
 */
@Singleton
class KioskPage @Inject constructor(private val homeApp: HomeApp) : SettingsPage {
    override val id = "kiosk"
    override val title = TABLET_LOCK
    override val order = 900

    @Composable
    override fun Content() {
        val navigator = LocalShellNavigator.current
        val isHome by homeApp.isDefault.collectAsState()
        StepTitle(TABLET_LOCK, KIOSK_LINE)
        if (isHome) {
            HhButton(CHANGE_HOME_APP, navigator::changeHomeApp, Modifier.testTag("settings_change_home_app"), on = ButtonSurface.Bg)
        } else {
            HomeAppPrompt(onChoose = navigator::chooseHomeApp)
        }
        HhButton(USE_OTHER_APPS, navigator::exitKiosk, Modifier.testTag("settings_exit_kiosk"), on = ButtonSurface.Bg)
    }
}
