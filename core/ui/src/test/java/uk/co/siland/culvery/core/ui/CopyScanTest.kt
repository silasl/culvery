package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** 4d-2 Decision 8: no system word and no straight quote or apostrophe in any copy in the app's main sources. */
class CopyScanTest {
    // Unit tests run in the module's directory.
    private val repo = File("../..")

    /** As `IconFontTest.mainSources`, without the contract-test libraries (`*-testkit`), whose assertion messages are never shown. */
    private fun mainSources(): Sequence<File> {
        val main = "${File.separator}src${File.separator}main${File.separator}"
        return repo.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") && !it.name.endsWith("-testkit") }
            .filter { it.isFile && it.extension == "kt" && main in it.path }
    }

    /** "capability/calendar/CalendarPermissions.kt": the module's directory and the file's name, as the allowlist writes them. */
    private fun shortPath(file: File): String {
        val path = file.relativeTo(repo).invariantSeparatorsPath
        return path.substringBefore("/src/main/") + "/" + file.name
    }

    private fun allowed(): Set<Pair<String, String>> = File("src/test/resources/copy-scan-allow.txt").readLines(Charsets.UTF_8)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line -> line.split("|").map(String::trim).let { (path, literal) -> path to literal } }
        .toSet()

    @Test
    fun theScannerCatchesEachRuleAndPassesTheRest() {
        val fixture = File("src/test/resources/copy-scan-fixture.kt.txt").readText(Charsets.UTF_8)
        val found = CopyScan.scan("fixture", fixture).map { it.literal to it.why }
        assertThat(found).containsExactly(
            "Make master" to "says \"master\"",
            "It syncs every hour" to "says \"syncs\"",
            "Ask the master." to "says \"master\"",
            "That name's taken" to "a straight apostrophe",
            "Can't change this" to "a straight apostrophe",
            "No towns match \\\"Brigh\\\"." to "a straight quote",
            "He said \"go\" there" to "a straight quote",
            "That's it" to "a straight apostrophe",
            "kiosk" to "says \"kiosk\"",
        )
    }

    @Test
    fun noCopyNamesASystemWordOrUsesAStraightQuote() {
        val allow = allowed()
        val findings = mainSources().flatMap { file ->
            CopyScan.scan(shortPath(file), file.readText(Charsets.UTF_8)).asSequence()
        }.filterNot { f -> (f.path to f.literal) in allow }.toList()
        assertWithMessage("Copy with a system word or a straight quote (4d-2 §7.1):\n" + findings.joinToString("\n") { "${it.path}:${it.line}: ${it.literal} (${it.why})" })
            .that(findings).isEmpty()
    }

    @Test
    fun everyAllowlistEntryIsStillNeeded() {
        val found = mainSources().flatMap { file -> CopyScan.scan(shortPath(file), file.readText(Charsets.UTF_8)).asSequence() }
            .map { it.path to it.literal }.toSet()
        assertThat(found).containsAtLeastElementsIn(allowed())
    }
}
