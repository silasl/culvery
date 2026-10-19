package uk.co.siland.culvery.core.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.pinReasonText
import uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS
import uk.co.siland.culvery.core.plugin.OVERLAY_IDLE_MS
import uk.co.siland.culvery.core.plugin.UserActivity
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.plural

/**
 * [overSheet]: a sheet is open, so the pad covers the sheet's area rather than the whole screen. Left alone, it cancels
 * after [OVERLAY_IDLE_MS]; [overSettings]: only once the longest Settings session would have ended, so the session
 * always ends first and the pad's close can't move TalkBack's focus and stretch it (4d-2 §6.3).
 */
@Composable
fun PinPadHost(
    controller: PinPromptController,
    activity: UserActivity,
    overSheet: Boolean = false,
    overSettings: Boolean = false,
    nowMillis: () -> Long = System::currentTimeMillis,
) {
    val request by controller.request.collectAsState()
    request?.let { r ->
        // Each touch starts the wait again; then it cancels as Cancel does, so authorise returns null. A lockout doesn't stop it.
        LaunchedEffect(r, overSettings) {
            activity.touches.collectLatest {
                delay(if (overSettings) activity.timeout(EDIT_HOLD_MS) + SETTINGS_MARGIN_MS else activity.timeout(OVERLAY_IDLE_MS))
                controller.cancel()
            }
        }
        PinPadSheet(
            label = r.label,
            reason = r.reason,
            error = r.error,
            lockedUntilMillis = r.lockedUntilMillis,
            onSubmit = controller::submit,
            onCancel = controller::cancel,
            overSheet = overSheet,
            nowMillis = nowMillis,
            clearOn = r,
        )
    }
}

/** How much longer than the longest Settings session the pad waits over it; Settings closing re-keys the pad to its own wait. */
private const val SETTINGS_MARGIN_MS = 1_000L

internal const val ENTER_YOUR_PIN = "Enter your PIN"

internal const val WRONG_PIN = "Wrong PIN — try again."

internal const val TRY_AGAIN = "You can try again."

internal fun tooManyTries(seconds: Int): String {
    val minutes = seconds / 60L
    val rest = seconds % 60L
    val wait = when {
        minutes == 0L -> plural(rest, "second")
        rest == 0L -> plural(minutes, "minute")
        else -> "${plural(minutes, "minute")} ${plural(rest, "second")}"
    }
    return "Too many tries — wait $wait."
}

/** "2 of 4 digits entered": the dots, spoken, never the digits themselves. */
internal fun digitsEntered(count: Int): String = "$count of ${PinHasher.PIN_LENGTH} digits entered"

/**
 * Hand-off §7 PIN pad asking who's there. The `rgba(0,0,0,.5)` scrim covers the sheet's 600 dp when [overSheet],
 * otherwise the whole screen; the card is centred in it.
 */
@Composable
fun PinPadSheet(
    label: String,
    reason: PinReason,
    error: PinError?,
    lockedUntilMillis: Long?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    overSheet: Boolean = false,
    nowMillis: () -> Long = System::currentTimeMillis,
    clearOn: Any? = null,
) {
    // Counts down from the lock's first value (deterministic under a virtual frame clock), reset in the same composition
    // as a lock that arrives on a retry, so it shows at once.
    val countdown = remember(lockedUntilMillis) { mutableIntStateOf(secondsUntil(lockedUntilMillis, nowMillis())) }
    val firstSeconds = remember(countdown) { countdown.intValue }
    LaunchedEffect(countdown) {
        while (countdown.intValue > 0) {
            delay(1_000)
            countdown.intValue -= 1
        }
    }
    val secondsLeft = countdown.intValue
    val locked = secondsLeft > 0
    // Nobody has typed since the lock began, so its end doesn't repeat the try before it.
    val lockEnded = firstSeconds > 0 && !locked
    val message = when {
        locked -> tooManyTries(secondsLeft)
        lockEnded -> TRY_AGAIN
        error is PinError.WrongPin -> WRONG_PIN
        error is PinError.NotAllowed -> error.message
        else -> ""
    }
    PinPadFrame(
        title = "Who’s this?",
        line = pinReasonText(reason, label),
        message = message,
        // Said once, from the lockout's first value: a line read every second would drown everything else.
        spokenMessage = if (locked) tooManyTries(firstSeconds) else message,
        wrongPin = error is PinError.WrongPin && !lockEnded,
        enabled = !locked,
        onSubmit = onSubmit,
        onCancel = onCancel,
        overSheet = overSheet,
        paneTitle = ENTER_YOUR_PIN,
        clearOn = clearOn,
    )
}

/** 4a design §4.2: choosing a new PIN. */
const val CHOOSE_PIN = "Choose a 4-digit PIN"
const val ENTER_IT_AGAIN = "Enter it again"
const val PINS_DIDNT_MATCH = "Those PINs didn’t match — try again."

/**
 * 4a design §4.2: the PIN pad twice, [CHOOSE_PIN] then [ENTER_IT_AGAIN]; a mismatch starts again with
 * [PINS_DIDNT_MATCH]. [onChosen] gets the PIN once both agree. [drawScrim] false leaves the scrim to whoever shows it
 * (the shell's overlay already draws one).
 */
@Composable
fun ChoosePinPad(onChosen: (String) -> Unit, onCancel: () -> Unit, overSheet: Boolean = false, drawScrim: Boolean = true) {
    var first by remember { mutableStateOf<String?>(null) }
    var mismatches by remember { mutableIntStateOf(0) }
    val retrying = first == null && mismatches > 0
    PinPadFrame(
        title = if (first == null) CHOOSE_PIN else ENTER_IT_AGAIN,
        line = null,
        message = if (retrying) PINS_DIDNT_MATCH else "",
        wrongPin = retrying,
        enabled = true,
        onSubmit = { pin ->
            val chosen = first
            when {
                chosen == null -> first = pin
                chosen == pin -> onChosen(pin)
                else -> {
                    first = null
                    mismatches++
                }
            }
        },
        onCancel = onCancel,
        overSheet = overSheet,
        drawScrim = drawScrim,
        clearOn = first,
    )
}

/**
 * The PIN pad's card and keypad (hand-off §7). It catches every tap on the screen: a tap outside the card cancels. The
 * pad submits on the fourth digit. [wrongPin] rings the empty dots in `danger`; [line] null leaves the reason line out.
 */
@Composable
fun PinPadFrame(
    title: String,
    line: String?,
    message: String,
    spokenMessage: String = message,
    wrongPin: Boolean,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    overSheet: Boolean = false,
    drawScrim: Boolean = true,
    paneTitle: String = title,
    clearOn: Any? = null,
) {
    val c = Culvery.colors
    // Emptied for each try by [clearOn], not by rebuilding the pad: new nodes move TalkBack's focus, and its reading of
    // the focused key flushes the try's message before it is said.
    var digits by remember(clearOn) { mutableStateOf("") }
    // A pad opened from a field (a name, then Set PIN) would sit under the keyboard; leaving the field closes it.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusManager.clearFocus() }
    // The keys stay enabled at the fourth digit: disabling them made TalkBack read the focused key again, which drowned
    // the message the PIN brought. A fifth digit is ignored instead.
    val type: (String) -> Unit = { d ->
        if (enabled && digits.length < PinHasher.PIN_LENGTH) {
            digits += d
            if (digits.length == PinHasher.PIN_LENGTH) onSubmit(digits)
        }
    }

    val cancel by rememberUpdatedState(onCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pin_scrim")
            .semantics { this.paneTitle = paneTitle }
            // A gesture, not a clickable: TalkBack never lands on the scrim, which holds the card; the card has Cancel.
            .pointerInput(Unit) { detectTapGestures { cancel() } },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(if (overSheet) Alignment.CenterEnd else Alignment.Center)
                .then(if (overSheet) Modifier.fillMaxHeight().width(ShellTokens.sheetWidth) else Modifier.fillMaxSize())
                .testTag("pin_area")
                .then(if (drawScrim) Modifier.background(ShellTokens.cardScrim) else Modifier),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PinPadDimens.gap),
                modifier = Modifier
                    .testTag("pin_card")
                    .width(PinPadDimens.cardWidth)
                    .clip(RoundedCornerShape(PinPadDimens.cardRadius))
                    .background(c.surf)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = PinPadDimens.cardPaddingH, vertical = PinPadDimens.cardPaddingV),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(PinPadDimens.badge).clip(CircleShape).background(c.accentSoft),
                ) {
                    HhIcon(Icons.LOCK, size = PinPadDimens.badgeIcon, tint = c.accent)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PinPadDimens.titleReasonGap),
                ) {
                    Text(title, style = PinPadType.title, color = c.ink, modifier = Modifier.semantics { heading() })
                    if (line != null) Text(line, style = PinPadType.reason, color = c.mute, textAlign = TextAlign.Center)
                }
                Dots(filled = digits.length, error = if (wrongPin && digits.isEmpty()) spokenMessage else null)
                Box(
                    Modifier
                        .testTag("pin_message")
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            if (spokenMessage.isNotEmpty()) contentDescription = spokenMessage
                        },
                ) {
                    Text(
                        message,
                        style = PinPadType.error,
                        color = c.danger,
                        // Two lines kept at every font scale, so the keypad doesn't jump when a message appears.
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        // The box above speaks it; this line's own text may tick.
                        modifier = Modifier.testTag("pin_error").semantics { hideFromAccessibility() },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(PinPadDimens.keyRowGap)) {
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(PinPadDimens.keyColumnGap)) {
                            row.forEach { d -> DigitKey(d, enabled = enabled) { type(d) } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(PinPadDimens.keyColumnGap)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .testTag("pin_cancel")
                                .size(PinPadDimens.key)
                                .clickable(
                                    interactionSource = null,
                                    indication = ripple(bounded = false, radius = PinPadDimens.key / 2),
                                    role = Role.Button,
                                    onClick = onCancel,
                                ),
                        ) {
                            // Not clipped to the key: at a large font scale the word runs past the circle rather than breaking.
                            Text(
                                "Cancel",
                                style = PinPadType.cancel,
                                color = c.ink,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                            )
                        }
                        DigitKey("0", enabled = enabled) { type("0") }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .testTag("pin_backspace")
                                .size(PinPadDimens.key)
                                .clip(CircleShape)
                                // Off once four are in: that PIN is being checked.
                                .hhClickable({ digits = digits.dropLast(1) }, enabled = digits.length in 1 until PinHasher.PIN_LENGTH && enabled),
                        ) {
                            HhIcon(Icons.BACKSPACE, size = PinPadDimens.backspaceIcon, tint = c.ink, contentDescription = "Delete last digit")
                        }
                    }
                }
            }
        }
    }
}

/** [error], when set, rings the empty dots in `danger` and is read with them. */
@Composable
private fun Dots(filled: Int, error: String?) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(PinPadDimens.dotGap),
        modifier = Modifier.testTag("pin_dots").clearAndSetSemantics {
            contentDescription = digitsEntered(filled)
            liveRegion = LiveRegionMode.Polite
            if (error != null) error(error)
        },
    ) {
        repeat(PinHasher.PIN_LENGTH) { i ->
            val dot = Modifier.size(PinPadDimens.dot).clip(CircleShape)
            Box(
                if (i < filled) {
                    dot.background(c.ink)
                } else {
                    dot.border(PinPadDimens.dotBorder, if (error != null) c.danger else c.mute, CircleShape)
                },
            )
        }
    }
}

@Composable
private fun DigitKey(digit: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("pin_key_$digit")
            .size(PinPadDimens.key)
            .clip(CircleShape)
            .background(c.surf2)
            .hhClickable(onClick, enabled = enabled)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Text(digit, style = PinPadType.digit, color = c.ink)
    }
}

private fun secondsUntil(until: Long?, now: Long): Int =
    until?.let { ((it - now + 999) / 1000).toInt().coerceAtLeast(0) } ?: 0
