package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shell component values from the hand-off (§6 Holiday sheet, §7 Calendar sheets and toast). */
object ShellTokens {
    /** rgba(0,0,0,.6) behind sheets. The same in both themes, so not an HhColors token. */
    val sheetScrim = Color(0x99000000)

    /** rgba(0,0,0,.64) behind the door card and the PIN pad (9 §4.3, §4.9). The same in both themes. */
    val cardScrim = Color(0xA3000000)

    // Right-side sheet: 613 dp, full height, bg, 1 dp left border `surf3`, 16 dp between blocks, 48 dp close button.
    val sheetWidth = 613.dp
    val sheetBorder = 1.dp
    val sheetGap = 16.dp
    val closeButton = 48.dp
    val closeIcon = 26.dp

    // Toast: bottom-centre pill 28 dp above the bottom, padding 14×22, radius 26, `info` icon; its time is [toastMillis].
    val toastBottom = 28.dp
    val toastPaddingV = 14.dp
    val toastPaddingH = 22.dp
    val toastRadius = 26.dp
    val toastIcon = 22.dp
    val ringSize = 22.dp
    val ringStroke = 3.dp
    val toastIconGap = 10.dp
    val toastMaxWidth = 720.dp
    val toastActionGap = 16.dp
    val toastActionHeight = 48.dp

    /** The least gap between a toast and the foot of a sheet or editor it sits above (9c §5). */
    val toastAboveFoot = 12.dp

    /** Home's cards (9 §4.2). */
    val homeCardRadius = 24.dp
}

/**
 * Where the feet that keep toasts above them (see [KeepsToastsAbove]) have their tops, in window px, each under its own
 * owner key so a foot that goes takes away only its own.
 */
class ToastClearance {
    private val tops = mutableStateMapOf<Any, Float>()

    /** The highest reported top (the smallest y), or null when no foot is shown. */
    val top: Float? get() = tops.values.minOrNull()

    internal fun report(owner: Any, top: Float) {
        if (tops[owner] != top) tops[owner] = top
    }

    internal fun clear(owner: Any) {
        tops.remove(owner)
    }
}

val LocalToastClearance = staticCompositionLocalOf<ToastClearance?> { null }

/** Draws [content] and tells the toast layer where its top is, so a toast sits above it instead of over its buttons. */
@Composable
fun KeepsToastsAbove(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val clearance = LocalToastClearance.current
    val owner = remember { Any() }
    DisposableEffect(clearance, owner) { onDispose { clearance?.clear(owner) } }
    Box(modifier.onGloballyPositioned { clearance?.report(owner, it.positionInWindow().y) }) { content() }
}

/** Shell text styles, derived from HhType. */
object ShellType {
    /** 16 sp / 600: toast text. */
    val toast = HhType.body.copy(fontWeight = FontWeight.W600)
}

const val TOAST_MIN_MS = 6_000L
const val TOAST_MAX_MS = 12_000L
const val TOAST_MS_PER_TEN_WORDS = 1_000L

/** How long a toast shows (9 §6.10): 6 s plus 1 s for every ten words, at most 12 s. */
fun toastMillis(message: String): Long {
    val words = message.split(Regex("\\s+")).count { it.isNotEmpty() }
    return minOf(TOAST_MIN_MS + TOAST_MS_PER_TEN_WORDS * (words / 10), TOAST_MAX_MS)
}

/** A countdown that ends at [endsAt] on [nowMillis]'s clock, [totalMs] long from its start. */
class Countdown(val endsAt: Long, val totalMs: Long, val nowMillis: () -> Long)

/** The share of the countdown still to run, 0..1. */
internal fun ringLeft(remainingMs: Long, totalMs: Long): Float = (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)

/**
 * The hand-off's right-side sheet: 613 dp, full height, `bg`, 1 dp `surf3` left border, 16 dp between blocks.
 * Taps on empty sheet space are swallowed so they don't reach the scrim underneath.
 */
@Composable
fun HhSheet(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    keyboard: WindowInsets = WindowInsets.ime,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
        modifier = modifier
            .fillMaxHeight()
            .width(ShellTokens.sheetWidth)
            .background(c.bg)
            .drawBehind {
                val x = ShellTokens.sheetBorder.toPx() / 2
                drawLine(c.surf3, Offset(x, 0f), Offset(x, size.height), ShellTokens.sheetBorder.toPx())
            }
            .pointerInput(Unit) { detectTapGestures { } }
            // The background runs behind the keyboard; the content stays above it.
            .windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom))
            .padding(padding),
        content = content,
    )
}

/** 48 dp round quiet button with a 26 dp `close` icon, named by its icon. Sheets are `bg`, so its fill is the quiet fill on `bg`. */
@Composable
fun HhCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("sheet_close")
            .size(ShellTokens.closeButton)
            .clip(CircleShape)
            .background(quietFill(ButtonSurface.Bg, c))
            .hhClickable(onClick),
    ) {
        HhIcon(Icons.CLOSE, size = ShellTokens.closeIcon, tint = c.ink, contentDescription = "Close")
    }
}

/**
 * The toast pill: `ink` background, `bg` text, 16 sp / 600, padding 14×22, radius 26. One spoken item, polite unless [urgent].
 * With a [countdown], a ring of the time left replaces the icon (9 §4.13). With an [actionLabel], a 48 dp text button in
 * `accentOnInk` follows the message; it sits outside the merged message group so TalkBack and tests can reach it.
 */
@Composable
fun HhToast(
    message: String,
    icon: String,
    modifier: Modifier = Modifier,
    urgent: Boolean = false,
    countdown: Countdown? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val c = Culvery.colors
    val spoken = Modifier.semantics(mergeDescendants = true) { liveRegion = if (urgent) LiveRegionMode.Assertive else LiveRegionMode.Polite }
    // The merged group is the tagged node itself when there is no action, so its tag, live region and text are one node.
    fun pill(group: Modifier) = modifier
        .testTag("toast")
        .then(group)
        .widthIn(max = ShellTokens.toastMaxWidth)
        .clip(RoundedCornerShape(ShellTokens.toastRadius))
        .background(c.ink)
        .padding(horizontal = ShellTokens.toastPaddingH, vertical = ShellTokens.toastPaddingV)
    val body: @Composable RowScope.() -> Unit = {
        if (countdown != null) CountdownRing(countdown, c.bg) else HhIcon(icon, size = ShellTokens.toastIcon, tint = c.bg)
        Text(message, style = ShellType.toast, color = c.bg)
    }
    if (actionLabel == null) {
        Row(pill(spoken), Arrangement.spacedBy(ShellTokens.toastIconGap), Alignment.CenterVertically, body)
    } else {
        Row(pill(Modifier), Arrangement.spacedBy(ShellTokens.toastActionGap), Alignment.CenterVertically) {
            Row(spoken, Arrangement.spacedBy(ShellTokens.toastIconGap), Alignment.CenterVertically, body)
            Box(
                Modifier.testTag("toast_action").defaultMinSize(minWidth = ShellTokens.toastActionHeight, minHeight = ShellTokens.toastActionHeight).hhClickable(onAction),
                contentAlignment = Alignment.Center,
            ) { Text(actionLabel, style = ShellType.toast, color = c.accentOnInk) }
        }
    }
}

/** Redrawn every frame from the clock, so an advanced clock or a screenshot shows the time really left; the loop ends at 0. */
@Composable
private fun CountdownRing(countdown: Countdown, color: Color) {
    var frame by remember { mutableLongStateOf(0L) }
    // A Countdown is rebuilt on each recomposition; its end and length are what restart the loop.
    LaunchedEffect(countdown.endsAt, countdown.totalMs) {
        while (countdown.endsAt - countdown.nowMillis() > 0) withFrameMillis { frame = it }
        frame++
    }
    Canvas(Modifier.size(ShellTokens.ringSize)) {
        // Read so each frame redraws the ring.
        frame
        val stroke = ShellTokens.ringStroke.toPx()
        val arc = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(color.copy(alpha = RING_SPENT_ALPHA), 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
        val left = ringLeft(countdown.endsAt - countdown.nowMillis(), countdown.totalMs)
        drawArc(color, -90f, 360f * left, false, topLeft, arc, style = Stroke(stroke))
    }
}

private const val RING_SPENT_ALPHA = 0.3f
