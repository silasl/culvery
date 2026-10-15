package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** 6 §3.2: plain HTTP for the speakers on the home network; never for the internet services Culvery uses. */
class CleartextTest {
    // Unit tests run in the module's directory, as NoBackupTest relies on.
    private fun root(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path)).documentElement

    private fun Element.children(): List<Element> = (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>()

    @Test
    fun theManifestNamesTheConfig() {
        val application = root("src/main/AndroidManifest.xml").children().single { it.tagName == "application" }
        assertThat(application.getAttributeNS("http://schemas.android.com/apk/res/android", "networkSecurityConfig"))
            .isEqualTo("@xml/network_security_config")
    }

    /** Fails if cleartext is allowed for Google or Open-Meteo, or not for an address the router chose. */
    @Test
    fun cleartextOnlyOffTheInternetServices() {
        val config = root("src/main/res/xml/network_security_config.xml").children()
        assertThat(config.single { it.tagName == "base-config" }.getAttribute("cleartextTrafficPermitted")).isEqualTo("true")
        val internet = config.single { it.tagName == "domain-config" }
        assertThat(internet.getAttribute("cleartextTrafficPermitted")).isEqualTo("false")
        val domains = internet.children().filter { it.tagName == "domain" }
        assertThat(domains.map { it.textContent.trim() }).containsExactly("googleapis.com", "google.com", "open-meteo.com")
        assertThat(domains.map { it.getAttribute("includeSubdomains") }.toSet()).containsExactly("true")
    }
}
