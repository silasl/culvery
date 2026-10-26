package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * 6 §3.2: the network security config allows plain HTTP in general (for speakers at any home address), so every URL
 * written in the app's main sources must be HTTPS: a future internet client can't fall under the base config unseen.
 * A speaker's address is built at run time (`http://$address…`); XML names are never fetched.
 */
class InternetHttpsTest {
    // Unit tests run in the module's directory; the repo is one up.
    private val repo = File("..")

    private val plainUrl = Regex(""""http://([A-Za-z0-9.-]+)""")

    /** XML namespace and feature names: identifiers that look like URLs. */
    private val names = setOf("schemas.xmlsoap.org", "apache.org", "schemas.android.com", "purl.org")

    /** The base config deliberately allows cleartext on the home network; this is the example address Settings shows the household. */
    private val homeNetworkExamples = setOf("192.168.1.20")

    @Test
    fun everyUrlInMainSourcesIsHttps() {
        val main = "${File.separator}src${File.separator}main${File.separator}"
        val found = repo.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") }
            .filter { it.isFile && it.extension == "kt" && main in it.path }
            .flatMap { file -> plainUrl.findAll(file.readText(Charsets.UTF_8)).map { file.name to it.groupValues[1] } }
            .filterNot { (_, host) -> host in names || host in homeNetworkExamples }
            .toList()
        assertWithMessage("plain-HTTP URLs in main sources").that(found).isEmpty()
    }

    @Test
    fun aPlainUrlOutsideTheAllowancesStillFails() {
        assertThat(plainUrl.findAll("\"http://api.example.com/x\"").map { it.groupValues[1] }.toList()).containsExactly("api.example.com")
        assertThat("api.example.com" in names + homeNetworkExamples).isFalse()
    }

    @Test
    fun theScanSeesTheInternetClients() {
        val sources = repo.walkTopDown().onEnter { it.name !in setOf("build", ".gradle", ".git") }.filter { it.isFile && it.extension == "kt" }
        assertWithMessage("the forecast's base URL").that(sources.any { "\"https://api.open-meteo.com" in it.readText(Charsets.UTF_8) }).isTrue()
    }
}
