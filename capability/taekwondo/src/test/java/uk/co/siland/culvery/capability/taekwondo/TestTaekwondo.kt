package uk.co.siland.culvery.capability.taekwondo

import androidx.compose.runtime.Composable
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.plugin.AlertHost

/** A speaker that records `(text, korean)` and each time it was asked, and takes a second to say anything. */
class FakeSpeaker(hasKorean: Boolean? = true, private val now: () -> Long = { 0L }) : Speaker {
    override val korean = MutableStateFlow(hasKorean)
    var checks = 0
    val calls = CopyOnWriteArrayList<Pair<String, Boolean>>()
    val times = CopyOnWriteArrayList<Long>()
    var stops = 0

    override suspend fun say(text: String, korean: Boolean) {
        calls += text to korean
        times += now()
        delay(1_000)
    }

    override fun checkKorean() {
        checks++
    }

    override fun stop() {
        stops++
    }
}

/** An alert host a test drives: any alert, quiet or loud, makes [showing] true. */
class FakeAlerts : AlertHost {
    override val inFront = MutableStateFlow(true)
    override val showing: StateFlow<Boolean> = MutableStateFlow(false)
    override val lowered = MutableStateFlow(false)

    override fun show(loud: Boolean, content: @Composable () -> Unit) {
        (showing as MutableStateFlow).value = true
    }

    override suspend fun quieted() = Unit

    override fun dismiss() {
        (showing as MutableStateFlow).value = false
    }
}

fun move(number: String): Move = CHOONG_MOO.moves.first { it.number == number }
