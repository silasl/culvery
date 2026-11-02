package uk.co.siland.culvery.capability.taekwondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.taekwondo.PATTERNS
import uk.co.siland.culvery.capability.taekwondo.Pattern
import uk.co.siland.culvery.capability.taekwondo.RunState
import uk.co.siland.culvery.capability.taekwondo.Speaker
import uk.co.siland.culvery.capability.taekwondo.TaekwondoRun
import uk.co.siland.culvery.capability.taekwondo.TaekwondoSettings
import uk.co.siland.culvery.core.plugin.LocalAlertHost
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.hhClickable

/** The words on the screen. */
object TaekwondoWords {
    const val TITLE = "Taekwondo"
    const val DETAILED = "Detailed mode"
    const val CANCEL = "Cancel"
    const val CLOSE = "Close"

    fun again(seconds: Int) = "Again in $seconds"
}

private val CountStyle = HhType.clock.copy(fontSize = 240.sp, lineHeight = 260.sp, letterSpacing = 0.sp)
private val AgainStyle = CountStyle.copy(fontSize = 96.sp, lineHeight = 112.sp)
private val HangulStyle = HhType.screenTitle.copy(fontSize = 96.sp, lineHeight = 112.sp)

/**
 * The practice screen: pick a pattern, then after 10 s it reads the moves aloud until Cancel. It keeps the overlay open
 * while a run goes on, and any door alert stops the run (15b Ruling 18). Asks for no PIN.
 */
@Composable
fun TaekwondoScreen(settings: TaekwondoSettings, speaker: Speaker, onClose: () -> Unit) {
    val overlay = LocalOverlayHost.current
    val alerts = LocalAlertHost.current
    val scope = rememberCoroutineScope()
    val run = remember(speaker) { TaekwondoRun(speaker, scope) }
    val state by run.state.collectAsState()
    val detailed by settings.detailed.collectAsState(initial = false)
    val running = state != RunState.Idle
    var chosen by remember { mutableStateOf(PATTERNS.first()) }

    LaunchedEffect(running) { overlay.keepOpen(running) }
    LaunchedEffect(run) { alerts.showing.collect { if (it) run.stop() } }
    DisposableEffect(run) { onDispose { run.stop() } }

    TaekwondoPanel(
        state = state, pattern = chosen, detailed = detailed,
        onDetailed = { on -> scope.launch { settings.setDetailed(on) } },
        onPattern = { chosen = it; run.start(it, detailed) },
        onCancel = run::stop,
        onClose = { run.stop(); onClose() },
    )
}

/** What the screen draws for [state]; [pattern] is the one chosen. */
@Composable
internal fun TaekwondoPanel(
    state: RunState,
    pattern: Pattern,
    detailed: Boolean,
    onDetailed: (Boolean) -> Unit,
    onPattern: (Pattern) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val running = state != RunState.Idle
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize().background(Culvery.colors.bg).padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (running) pattern.name else TaekwondoWords.TITLE,
                style = HhType.screenTitle, color = Culvery.colors.ink, modifier = Modifier.weight(1f).testTag("taekwondo_title"),
            )
            HhButton(TaekwondoWords.CLOSE, onClick = onClose, on = ButtonSurface.Bg, modifier = Modifier.testTag("taekwondo_close"))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (state) {
                RunState.Idle -> Grid(detailed, onDetailed, onPattern)
                is RunState.Counting -> Big(state.seconds.toString(), "count", CountStyle)
                is RunState.Again -> Big(TaekwondoWords.again(state.seconds), "again", AgainStyle)
                is RunState.Moving -> MoveView(state, detailed)
            }
        }
        if (running) {
            HhButton(
                TaekwondoWords.CANCEL, onClick = onCancel, on = ButtonSurface.Bg,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("taekwondo_cancel"),
            )
        }
    }
}

@Composable
private fun Grid(detailed: Boolean, onDetailed: (Boolean) -> Unit, onPattern: (Pattern) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
        HhSwitchRow(TaekwondoWords.DETAILED, null, detailed, "taekwondo_detailed", onDetailed)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
            PATTERNS.forEachIndexed { i, pattern ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Culvery.colors.surf)
                        .hhClickable({ onPattern(pattern) })
                        .padding(48.dp)
                        .testTag("pattern_$i"),
                ) {
                    Text(pattern.name, style = HhType.sectionTitle, color = Culvery.colors.ink)
                }
            }
        }
    }
}

@Composable
private fun Big(text: String, tag: String, style: TextStyle) {
    Text(
        text,
        style = style,
        color = Culvery.colors.accent, textAlign = TextAlign.Center, modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun MoveView(s: RunState.Moving, detailed: Boolean) {
    val move = s.move
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(move.number, style = HhType.headerValue.copy(fontSize = 48.sp), color = Culvery.colors.accent, modifier = Modifier.testTag("move_number"))
        Text(
            move.hangul ?: move.romanised, style = HangulStyle, color = Culvery.colors.ink, textAlign = TextAlign.Center,
            modifier = Modifier.testTag("move_korean"),
        )
        if (move.hangul != null) {
            Text(move.romanised, style = HhType.sectionTitle, color = Culvery.colors.mute, textAlign = TextAlign.Center, modifier = Modifier.testTag("move_romanised"))
        }
        Text(move.english, style = HhType.screenTitle, color = Culvery.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.testTag("move_english"))
        if (detailed) {
            Text(move.description, style = HhType.body.copy(fontSize = 24.sp), color = Culvery.colors.mute, textAlign = TextAlign.Center, modifier = Modifier.testTag("move_description"))
        }
    }
}
