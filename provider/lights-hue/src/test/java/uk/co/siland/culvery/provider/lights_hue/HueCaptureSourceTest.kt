package uk.co.siland.culvery.provider.lights_hue

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** 7 §8.2: the capture is a debug build's only; the release one logs nothing. */
class HueCaptureSourceTest {
    /** Fails if the release capture logs, or stops being the empty function the client calls. */
    @Test
    fun theReleaseCaptureLogsNothing() {
        // Unit tests run in the module's directory.
        val release = File("src/release/java/uk/co/siland/culvery/provider/lights_hue/HueCapture.kt").readText(Charsets.UTF_8)
        assertThat(release).doesNotContain("Log.")
        assertThat(release).contains("fun log(what: String, answer: String) = Unit")
    }
}
