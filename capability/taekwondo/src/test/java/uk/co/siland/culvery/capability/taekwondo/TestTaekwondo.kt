package uk.co.siland.culvery.capability.taekwondo

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.plugin.test.FakeAlertHost

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

typealias FakeAlerts = FakeAlertHost

fun move(number: String): Move = CHOONG_MOO.moves.first { it.number == number }
