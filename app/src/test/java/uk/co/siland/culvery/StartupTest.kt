package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.Startable

class StartupTest {
    @Test
    fun everyStartableIsStartedOnce() {
        var a = 0
        var b = 0
        startAll(listOf(Startable { a++ }, Startable { b++ })) { _, _ -> }
        assertThat(listOf(a, b)).containsExactly(1, 1)
    }

    @Test
    fun aFailingStartableDoesNotStopTheOthers() {
        val failures = mutableListOf<String>()
        var laterStarted = false
        startAll(listOf(Startable { error("boom") }, Startable { laterStarted = true })) { _, e ->
            failures += e.message.orEmpty()
        }
        assertThat(laterStarted).isTrue()
        assertThat(failures).containsExactly("boom")
    }
}
