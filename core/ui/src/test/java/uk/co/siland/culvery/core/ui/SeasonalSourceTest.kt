package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** A theme is described in one place, so adding one touches nothing else (14 §3). */
class SeasonalSourceTest {
    private val repo = File("../..")

    /** A theme's built-in sound is named beside the sound (18, ruling 5). */
    @Test
    fun onlyTheLookNamesATheme() {
        val main = "${File.separator}src${File.separator}main${File.separator}"
        val sources = repo.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") }
            .filter { it.isFile && it.extension == "kt" && main in it.path }
            .toList()
        assertThat(sources).isNotEmpty()
        SeasonalTheme.entries.forEach { theme ->
            val naming = sources.filter { theme.name in it.readText() }.map { it.name }.sorted()
            assertWithMessage("Files naming ${theme.name}").that(naming - setOf("${theme.label}Look.kt", "Seasonal.kt", "BuiltInSound.kt")).isEmpty()
        }
    }
}
