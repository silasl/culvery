package uk.co.siland.culvery.capability.taekwondo.ui

import androidx.compose.runtime.Composable
import javax.inject.Provider
import uk.co.siland.culvery.capability.taekwondo.Speaker
import uk.co.siland.culvery.capability.taekwondo.TaekwondoSettings
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.HouseModeTile
import uk.co.siland.culvery.core.ui.Icons

/** Taekwondo's half-width tray button: it has no on state, and a tap opens the practice screen. [speaker] is built when the screen first opens. */
@Composable
fun TaekwondoTile(settings: TaekwondoSettings, speaker: Provider<Speaker>) {
    val overlay = LocalOverlayHost.current
    HouseModeTile(
        tag = "taekwondo_tile",
        icon = Icons.SPORTS_MARTIAL_ARTS,
        label = TaekwondoWords.TITLE,
        on = false,
        second = null,
        secondTag = "taekwondo_tile_state",
        spoken = TaekwondoWords.TITLE,
        enabled = true,
        clickLabel = TaekwondoWords.OPEN,
        onTap = { overlay.showFullScreen { TaekwondoScreen(settings, speaker.get(), onClose = overlay::dismiss) } },
    )
}
