package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton

/**
 * Runs [act] when [count] of [owner]'s refusals has risen since this entered composition, so a refusal identical to the
 * last still counts. One already held when it appears does not.
 */
@Composable
internal fun OnNewRefusal(owner: Any, count: Int, act: suspend () -> Unit) {
    val seen = remember(owner) { count }
    LaunchedEffect(owner, count) { if (count > seen) act() }
}

/** 4c design §5.1: shown while Culvery isn't the home app, in the wizard's Done step and Settings › Tablet lock. */
@Composable
internal fun HomeAppPrompt(onChoose: () -> Unit, tone: ButtonTone) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.homeAppPromptGap)) {
        Text(MAKE_HOME_APP, style = SetupType.line, color = Culvery.colors.ink)
        HhButton(CHOOSE_HOME_APP, onChoose, Modifier.testTag("choose_home_app"), tone = tone, on = ButtonSurface.Bg)
    }
}
