package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** A theme is described in one place, so adding one touches nothing else (14 §3). */
class SeasonalSourceTest {
    private val repo = File("../..")

    @Test
    fun onlyTheLookNamesATheme() {
        val main = "${File.separator}src${File.separator}main${File.separator}"
        val sources = repo.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") }
            .filter { it.isFile && it.extension == "kt" && main in it.path }
            .toList()
        assertThat(sources).isNotEmpty()
        val naming = sources.filter { "HALLOWEEN" in it.readText() }.map { it.name }.sorted()
        assertWithMessage("Files naming HALLOWEEN").that(naming - setOf("HalloweenLook.kt", "Seasonal.kt")).isEmpty()
    }
}
