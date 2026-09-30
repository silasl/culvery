package uk.co.siland.culvery.core.setup.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.EXIT_KIOSK
import uk.co.siland.culvery.core.setup.KIOSK
import uk.co.siland.culvery.core.setup.KIOSK_LINE
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.ui.HhPillButton

/** Settings › Kiosk (4a design §4.7): Exit kiosk, moved here from the old Settings footer; it asks for a fresh PIN. */
@Singleton
class KioskPage @Inject constructor() : SettingsPage {
    override val id = "kiosk"
    override val title = KIOSK
    override val order = 900

    @Composable
    override fun Content() {
        val navigator = LocalShellNavigator.current
        StepTitle(KIOSK, KIOSK_LINE)
        HhPillButton(EXIT_KIOSK, navigator::exitKiosk, Modifier.testTag("settings_exit_kiosk"))
    }
}
