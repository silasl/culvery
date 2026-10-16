package uk.co.siland.culvery.provider.lights_hue

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Hue's guidance (7 §4.3): about one group command a second, about ten light commands a second. */
internal const val GROUP_GAP_MS = 1_000L
internal const val LIGHT_GAP_MS = 100L

/** `grouped_light` and `scene` commands are GROUP; `light` and `behavior_instance` ones LIGHT. */
internal enum class PaceKind { GROUP, LIGHT }

/**
 * Spaces each kind's commands at least its gap apart, from one start to the next. A command waiting its turn is
 * replaced by a newer one for the same target, and [dropWaiting] drops every waiting command; either way its caller
 * hears false at once and its block never runs.
 */
internal class HuePacer(
    private val now: () -> Long,
    groupGapMs: Long = GROUP_GAP_MS,
    lightGapMs: Long = LIGHT_GAP_MS,
) {
    private class Lane(val gapMs: Long) {
        var lastStart: Long? = null
    }

    private val lock = Any()
    private val lanes = mapOf(PaceKind.GROUP to Lane(groupGapMs), PaceKind.LIGHT to Lane(lightGapMs))
    private val waiting = HashMap<String, CompletableDeferred<Unit>>()

    /** Runs [block] at [target]'s turn: true once it has run, false when it was replaced or dropped first. */
    suspend fun send(target: String, kind: PaceKind, block: suspend () -> Unit): Boolean {
        val gone = CompletableDeferred<Unit>()
        synchronized(lock) { waiting.put(target, gone)?.complete(Unit) }
        val lane = lanes.getValue(kind)
        try {
            while (true) {
                val wait = synchronized(lock) {
                    if (gone.isCompleted) return false
                    val wait = lane.lastStart?.let { it + lane.gapMs - now() } ?: 0L
                    if (wait <= 0) lane.lastStart = now()
                    wait
                }
                if (wait <= 0) break
                // Two waiting for one turn both wake; the first to claim it goes and the other waits again.
                if (withTimeoutOrNull(wait) { gone.await() } != null) return false
            }
        } finally {
            synchronized(lock) { waiting.remove(target, gone) }
        }
        block()
        return true
    }

    fun dropWaiting() {
        synchronized(lock) {
            waiting.values.forEach { it.complete(Unit) }
            waiting.clear()
        }
    }
}
