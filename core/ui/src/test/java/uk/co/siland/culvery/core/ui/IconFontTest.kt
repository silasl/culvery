package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Test

/** Every glyph `Icons` names is a ligature in the committed font, and tools/fonts/icons.txt lists exactly them (4c §3.5). */
class IconFontTest {
    // Unit tests run in the module's directory.
    private val repo = File("../..")
    private val font = OpenTypeLigatures(File("src/main/res/font/material_symbols_rounded.ttf"))

    private val named: List<String> = Icons::class.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .map { it.get(null) as String }

    @Test
    fun theReaderFindsARealLigatureAndNotAWord() {
        assertThat(font.has("home")).isTrue()
        assertThat(font.has("culvery")).isFalse()
    }

    @Test
    fun everyNamedIconIsALigatureInTheFont() {
        assertWithMessage("Icons with no ligature in the font").that(named.filterNot(font::has)).isEmpty()
    }

    /** HhIcon(filled = true) — the rail's selected tab — draws the FILL ≥ 0.99 alternate; the subset must keep it. */
    @Test
    fun everyNamedIconKeepsItsFilledForm() {
        assertWithMessage("Icons whose filled form isn't in the font").that(named.filterNot { it in NO_FILLED_FORM || font.hasFilledForm(it) }).isEmpty()
    }

    @Test
    fun theSubsetListMatchesIcons() {
        val listed = File(repo, "tools/fonts/icons.txt").readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        assertThat(listed).containsNoDuplicates()
        assertThat(listed).containsExactlyElementsIn(named)
    }

    private fun mainSources(): Sequence<File> {
        val main = "${File.separator}src${File.separator}main${File.separator}"
        return repo.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") }
            .filter { it.isFile && it.extension == "kt" && main in it.path }
    }

    @Test
    fun noHhIconCallNamesAGlyphInAString() {
        val callers = mainSources().filter { "HhIcon(\"" in it.readText() }.map { it.relativeTo(repo).path }.toList()
        assertWithMessage("Use Icons.* instead of a string in").that(callers).isEmpty()
    }

    /**
     * S3 was `icon = "smartphone"`, a name the font doesn't have. Every icon still named by a string (in modules
     * without `:core:ui`, such as `:core:plugin`'s toast icon and Open-Meteo's descriptor) must be one `Icons` names.
     */
    @Test
    fun everyIconNamedInAStringIsInIcons() {
        val unknown = mainSources().flatMap { file ->
            ICON_LITERAL.findAll(file.readText()).map { it.groupValues[1] }.filterNot { it in named }.map { "${file.relativeTo(repo).path}: $it" }
        }.toList()
        assertWithMessage("Icon names not in Icons").that(unknown).isEmpty()
    }

    private companion object {
        /** `icon = "…"`, `icon: String = "…"`, `TOAST_ICON_INFO = "…"`, and the like. */
        val ICON_LITERAL = Regex("""(?i)icon\w*(?:\s*:\s*String\??)?\s*[=:]\s*"([a-z0-9_]+)"""")

        /** Glyphs the full font has no filled alternate for, so FILL fills them through the variation alone. */
        val NO_FILLED_FORM: Set<String> = setOf(
            "add", "arrow_downward", "arrow_upward", "check", "chevron_left", "chevron_right", "close",
            "expand_less", "expand_more", "rainy_heavy", "rainy_light", "refresh", "repeat", "sync_problem",
        )
    }
}
