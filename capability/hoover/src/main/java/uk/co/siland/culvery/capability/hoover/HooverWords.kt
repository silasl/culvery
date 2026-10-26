package uk.co.siland.culvery.capability.hoover

import uk.co.siland.culvery.core.plugin.CHECKING

const val HOOVER = "Hoover"
const val START = "Start"
const val START_SPOKEN = "Start cleaning"
const val STARTING = "Starting…"
const val CLEANING_WORD = "Cleaning"
const val NOT_STARTED = "The hoover didn’t start. Check the Eufy app."
const val STUCK = "Stuck. Check the Eufy app."
const val NO_HOOVER_CHOSEN = "No hoover is chosen. Choose one in Settings › Other integrations."

private fun HooverActivity.word() = when (this) {
    HooverActivity.DOCKED -> "Docked"
    HooverActivity.CLEANING -> CLEANING_WORD
    HooverActivity.RETURNING -> "Going home"
    HooverActivity.PAUSED -> "Paused"
    HooverActivity.IDLE -> "Idle"
    HooverActivity.ERROR -> STUCK
}

/** The row's status (11 §4.4): a problem or a stuck hoover is a line of its own, otherwise the word and the battery when known. */
fun hooverStatus(state: HooverState): String {
    state.problem?.let { return it }
    val activity = state.activity ?: return CHECKING
    if (activity == HooverActivity.ERROR) return STUCK
    return state.batteryPercent?.let { "${activity.word()} · $it%" } ?: activity.word()
}

/** What TalkBack reads for the row (11 §4.4). */
fun hooverSpoken(state: HooverState): String {
    state.problem?.let { return "$HOOVER. $it" }
    val activity = state.activity ?: return "$HOOVER, checking."
    if (activity == HooverActivity.ERROR) return "$HOOVER. $STUCK"
    val battery = state.batteryPercent?.let { ", battery $it percent" }.orEmpty()
    return "$HOOVER, ${activity.word().lowercase()}$battery."
}

/** The line a start leaves on the row; null when it started or was already under way. */
fun resultLine(result: HooverResult): String? = when (result) {
    HooverResult.NotStarted -> NOT_STARTED
    is HooverResult.Failed -> result.line
    HooverResult.Started, HooverResult.Busy -> null
}
