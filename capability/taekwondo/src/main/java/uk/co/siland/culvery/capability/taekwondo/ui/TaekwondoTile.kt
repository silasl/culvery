package uk.co.siland.culvery.capability.taekwondo.ui

import androidx.compose.runtime.Composable
import javax.inject.Provider
import uk.co.siland.culvery.capability.taekwondo.Speaker
import uk.co.siland.culvery.capability.taekwondo.TaekwondoSettings
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HouseModeDimens
import uk.co.siland.culvery.core.ui.HouseModeThirdTile
import uk.co.siland.culvery.core.ui.Icons

/** Taekwondo's third-width tray button, its icon alone like the sound pads beside it; a tap opens the practice screen. [speaker] is built when the screen first opens. */
@Composable
fun TaekwondoTile(settings: TaekwondoSettings, speaker: Provider<Speaker>) {
    val overlay = LocalOverlayHost.current
    HouseModeThirdTile(
        tag = "taekwondo_tile",
        spoken = TaekwondoWords.TITLE,
        clickLabel = TaekwondoWords.OPEN,
        enabled = true,
        onTap = { overlay.showFullScreen { TaekwondoScreen(settings, speaker.get(), onClose = overlay::dismiss) } },
    ) {
        HhIcon(Icons.SPORTS_MARTIAL_ARTS, size = HouseModeDimens.thirdIcon, filled = true, tint = Culvery.colors.accent)
    }
}
