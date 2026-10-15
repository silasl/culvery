package uk.co.siland.culvery.provider.music_sonos

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** 6 §8.2: the capture is a debug build's only; the release one logs nothing. */
class SonosCaptureSourceTest {
    @Test
    fun theReleaseCaptureLogsNothing() {
        // Unit tests run in the module's directory.
        val release = File("src/release/java/uk/co/siland/culvery/provider/music_sonos/SonosCapture.kt").readText(Charsets.UTF_8)
        assertThat(release).doesNotContain("Log.")
        assertThat(release).contains("fun log(action: String, answer: String) = Unit")
    }
}
