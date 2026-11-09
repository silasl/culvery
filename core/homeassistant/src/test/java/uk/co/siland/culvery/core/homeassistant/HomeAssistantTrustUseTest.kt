package uk.co.siland.culvery.core.homeassistant

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class HomeAssistantTrustUseTest {
    /** Fails if the probe's trust manager is built anywhere but the client's certificate lookup. */
    @Test
    fun theProbeTrustIsBuiltOnlyInTheCertificateLookup() {
        // Unit tests run in the module's directory.
        val calls = File("src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            file.readLines(Charsets.UTF_8).withIndex()
                .filter { (_, line) -> "capturing(" in line && "fun capturing(" !in line }
                .map { (index, _) -> file to index }
        }.toList()
        assertThat(calls.map { it.first.name }).containsExactly("HomeAssistantClient.kt")
        val lines = calls.single().first.readLines(Charsets.UTF_8)
        val lookup = lines.indexOfFirst { "override suspend fun certificate(" in it }
        assertThat(lookup).isAtLeast(0)
        val end = lines.drop(lookup + 1).indexOfFirst { it.startsWith("    }") } + lookup + 1
        assertThat(calls.single().second).isIn(lookup..end)
    }
}
