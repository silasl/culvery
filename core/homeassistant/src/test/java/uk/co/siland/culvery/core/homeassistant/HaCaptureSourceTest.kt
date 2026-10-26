package uk.co.siland.culvery.core.homeassistant

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** 11 §8.2: the capture is a debug build's only; the release one logs nothing. */
class HaCaptureSourceTest {
    /** Fails if the release capture logs, or stops being the empty function the client calls. */
    @Test
    fun theReleaseCaptureLogsNothing() {
        // Unit tests run in the module's directory.
        val release = File("src/release/java/uk/co/siland/culvery/core/homeassistant/HaCapture.kt").readText(Charsets.UTF_8)
        assertThat(release).doesNotContain("Log.")
        assertThat(release).contains("fun log(what: HaCall, text: String) = Unit")
    }
}
