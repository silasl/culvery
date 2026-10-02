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
import uk.co.siland.culvery.core.setup.EXIT_KIOSK
import uk.co.siland.culvery.core.setup.HomeAppPrompt
import uk.co.siland.culvery.core.setup.KIOSK
import uk.co.siland.culvery.core.setup.KIOSK_LINE
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.ui.HhPillButton

/**
 * Settings › Kiosk (4a design §4.7, 4c §5.1): the home app — Choose it while Culvery isn't, Change it (fresh PIN) while
 * it is — and Exit kiosk, which asks for a fresh PIN.
 */
@Singleton
class KioskPage @Inject constructor(private val homeApp: HomeApp) : SettingsPage {
    override val id = "kiosk"
    override val title = KIOSK
    override val order = 900

    @Composable
    override fun Content() {
        val navigator = LocalShellNavigator.current
        val isHome by homeApp.isDefault.collectAsState()
        StepTitle(KIOSK, KIOSK_LINE)
        if (isHome) {
            HhPillButton(CHANGE_HOME_APP, navigator::changeHomeApp, Modifier.testTag("settings_change_home_app"))
        } else {
            HomeAppPrompt(onChoose = navigator::chooseHomeApp)
        }
        HhPillButton(EXIT_KIOSK, navigator::exitKiosk, Modifier.testTag("settings_exit_kiosk"))
    }
}
