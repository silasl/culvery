package uk.co.siland.culvery.display

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Culvery sets only its own window's brightness, never Android's setting, which applies in other apps (8 §4.4). */
class BrightnessSourceTest {
    // Unit tests run in the module's directory, as AppSourceTest relies on.
    private val roots = listOf("src/main", "src/debug", "src/release").map(::File)

    /** Fails if any app source writes Android's brightness setting. */
    @Test
    fun noSourceWritesAndroidsBrightness() {
        val sources = roots.flatMap { root -> root.walk().filter { it.isFile && it.extension in setOf("kt", "java") }.toList() }
        assertThat(sources).isNotEmpty()
        assertThat(sources.filter { it.readText().contains("SCREEN_BRIGHTNESS") }.map { it.path }).isEmpty()
        assertThat(sources.filter { it.readText().contains("Settings.System.put") }.map { it.path }).isEmpty()
    }

    /** Fails if a manifest asks for the permission to change Android's settings. */
    @Test
    fun noManifestAsksToWriteSettings() {
        val manifests = roots.map { File(it, "AndroidManifest.xml") }.filter { it.isFile }
        assertThat(manifests).isNotEmpty()
        assertThat(manifests.filter { it.readText().contains("WRITE_SETTINGS") }.map { it.path }).isEmpty()
    }
}
