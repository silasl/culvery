package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** 4e design §5: the household's data is never copied off the tablet by Android's backup or device transfer. */
class NoBackupTest {
    // Unit tests run in the module's directory, as AppSourceTest relies on.
    private fun root(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(path)).documentElement

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>()

    private val application: Element
        get() = root("src/main/AndroidManifest.xml").children().single { it.tagName == "application" }

    @Test
    fun theManifestTurnsBackupOffAndNamesTheRules() {
        // allowBackup is what Android 11 and earlier honour for both backup and transfer; the rules cover Android 12+.
        assertThat(application.getAttributeNS(ANDROID, "allowBackup")).isEqualTo("false")
        assertThat(application.getAttributeNS(ANDROID, "dataExtractionRules")).isEqualTo("@xml/data_extraction_rules")
    }

    @Test
    fun theRulesExcludeEveryDomainFromCloudBackupAndDeviceTransfer() {
        val rules = root("src/main/res/xml/data_extraction_rules.xml")
        assertThat(rules.tagName).isEqualTo("data-extraction-rules")
        assertThat(rules.children().map { it.tagName }).containsExactly("cloud-backup", "device-transfer")
        for (section in rules.children()) {
            val entries = section.children()
            assertThat(entries.map { it.tagName }.toSet()).containsExactly("exclude")
            assertThat(entries.map { it.getAttribute("path") }.toSet()).containsExactly(".")
            assertThat(entries.map { it.getAttribute("domain") }).containsExactlyElementsIn(DOMAINS)
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val DOMAINS = listOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
