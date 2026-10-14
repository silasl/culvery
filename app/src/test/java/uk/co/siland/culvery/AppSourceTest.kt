package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** `:app` wires the weather and door capabilities through Hilt alone and never names them in its own code. */
class AppSourceTest {
    @Test
    fun theAppsOwnCodeNeverNamesTheWeatherOrDoorCapability() {
        // Unit tests run in the module's directory, as the screenshot paths rely on.
        val files = listOf("src/main", "src/debug", "src/release")
            .map(::File)
            .flatMap { root -> root.walk().filter { it.isFile && it.extension == "kt" }.toList() }
        assertThat(files).isNotEmpty()
        assertThat(files.filter { it.readText().contains("uk.co.siland.culvery.capability.weather") }.map { it.path }).isEmpty()
        assertThat(files.filter { it.readText().contains("uk.co.siland.culvery.capability.door") }.map { it.path }).isEmpty()
    }
}
