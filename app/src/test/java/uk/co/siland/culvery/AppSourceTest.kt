package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * `:app` wires the weather, door, music and lights capabilities through Hilt alone and never names them in its own code.
 * Debug builds are the one exception for music and lights: their sample speakers and lights implement those contracts (13 §8).
 */
class AppSourceTest {
    @Test
    fun theAppsOwnCodeNeverNamesTheWeatherDoorMusicOrLightsCapability() {
        // Unit tests run in the module's directory, as the screenshot paths rely on.
        fun sources(vararg roots: String) = roots
            .map(::File)
            .flatMap { root -> root.walk().filter { it.isFile && it.extension == "kt" }.toList() }
        val files = sources("src/main", "src/debug", "src/release")
        val shipped = sources("src/main", "src/release")
        assertThat(files).isNotEmpty()
        assertThat(files.filter { it.readText().contains("uk.co.siland.culvery.capability.weather") }.map { it.path }).isEmpty()
        assertThat(files.filter { it.readText().contains("uk.co.siland.culvery.capability.door") }.map { it.path }).isEmpty()
        assertThat(shipped.filter { it.readText().contains("uk.co.siland.culvery.capability.music") }.map { it.path }).isEmpty()
        assertThat(shipped.filter { it.readText().contains("uk.co.siland.culvery.capability.lights") }.map { it.path }).isEmpty()
    }
}
